#!/usr/bin/env python3
"""
horiZen precompile gate.

Fast, dependency-free static checks over the Android app's sources. Catches the
failure classes that have historically broken our CI before the real compile
ever runs. Everything prints as `path:line: [RULE] message` so it doubles as a
machine-greppable log; with --annotations it additionally prints GitHub Actions
inline error annotations.

Usage:
    python tools/precompile_check.py [--annotations] [--emit-report <path>]

Exit code: 0 = clean, 1 = at least one ERROR finding.
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "gecko-browser" / "app" / "src" / "main"
KTDIR = SRC / "java" / "com" / "example" / "geckobrowser"
RESDIR = SRC / "res"
MANIFEST = SRC / "AndroidManifest.xml"
GRADLE_APP = ROOT / "gecko-browser" / "app" / "build.gradle"

findings = []  # list[(level, relpath, line, rule, msg)]


def add(level, path, line, rule, msg):
    rel = path.relative_to(ROOT).as_posix() if isinstance(path, Path) else path
    findings.append((level, rel, line, rule, msg))


def err(path, line, rule, msg):
    add("ERROR", path, line, rule, msg)


def warn(path, line, rule, msg):
    add("WARN", path, line, rule, msg)


# ------------------------------------------------------------ source loading

KT_FILES = sorted(KTDIR.rglob("*.kt"))

def read_text(p):
    return p.read_bytes().decode("utf-8")  # raises UnicodeDecodeError -> fail in R5


def raw_lines(p):
    return read_text(p).splitlines()


def _strip_line_comments_and_strings(text):
    """
    Remove // line comments and string literal contents ("..." with \\ escapes,
    single-line raw spans) from `text`. Returns the stripped text.
    Multi-line block/raw comments need the scanner below.
    """
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            if text[i:i + 3] == '"""':
                end = text.find('"""', i + 3)
                i = n if end == -1 else end + 3
                out.append('""""""')
                continue
            j = i + 1
            while j < n:
                if text[j] == '\\':
                    j += 2
                    continue
                if text[j] == '"':
                    break
                if text[j] == '\n':
                    break
                j += 1
            i = j + 1
            out.append('""')
            continue
        if text[i:i + 2] == '//':
            break  # rest of line is a comment
        out.append(c)
        i += 1
    return "".join(out)


def code_of_file(p, text=None):
    """
    Single left-to-right pass; emits a placeholder for every removed token so the output
    stays line-aligned with the raw lines. Handles: line and block comments, double-quoted
    strings, triple-quoted raw strings, single-quoted char literals (with escapes).
    """
    if text is None:
        text = read_text(p)
    lines = []
    state = 'normal'
    block_depth = 0
    out = []

    def flush_line():
        line = "".join(out)
        lines.append(line)
        out.clear()

    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ''
        nxt3 = text[i:i + 3]
        if ch == '\n':
            flush_line()
            if state == 'line_comment':
                state = 'normal'
            i += 1
            continue
        if state == 'line_comment':
            i += 1
            continue
        if state == 'block_comment':
            if text[i:i + 2] == '/*':
                block_depth += 1
                i += 2
                continue
            if text[i:i + 2] == '*/':
                block_depth -= 1
                if block_depth == 0:
                    state = 'normal'
                i += 2
                continue
            i += 1
            continue
        if state == 'string':
            if ch == '\\':
                i += 2
                continue
            if ch == '"':
                out.append('""')
                state = 'normal'
                i += 1
                continue
            i += 1
            continue
        if state == 'raw_string':
            if nxt3 == '"""':
                out.append('""""""')
                state = 'normal'
                i += 3
                continue
            i += 1
            continue
        if state == 'char':
            if ch == '\\':
                i += 2
                continue
            if ch == "'":
                out.append("''")
                state = 'normal'
                i += 1
                continue
            i += 1
            continue
        # normal state
        if text[i:i + 2] == '//':
            state = 'line_comment'
            i += 2
            continue
        if text[i:i + 2] == '/*':
            state = 'block_comment'
            block_depth = 1
            i += 2
            continue
        if nxt3 == '"""':
            state = 'raw_string'
            i += 3
            continue
        if ch == '"':
            state = 'string'
            i += 1
            continue
        if ch == "'":
            state = 'char'
            i += 1
            continue
        out.append(ch)
        i += 1
    flush_line()
    return lines


# ---------------------------------------------------------------- rules

CORRUPT_CHARS = ['\ufffd', '\u01fd', '\u00c2', '\u00ef\u00bf\xbd', '\u00c3', '\u00e2\u20ac']


def rule5_structure(files):
    for p in files:
        text = read_text(p)  # raises -> caught below in main
        for ch in CORRUPT_CHARS:
            idx = text.find(ch)
            while idx != -1:
                line = text.count('\n', 0, idx) + 1
                err(p, line, "R5-mojibake", f"corrupt glyph {ch!r} in source (file was previously mangled)")
                idx = text.find(ch, idx + 1)
        code = code_of_file(p, text)
        depth = {'{': 0, '(': 0, '[': 0}
        for ln, line in enumerate(code, 1):
            for ch in line:
                if ch in '{([':
                    depth[ch] += 1
                elif ch in '})]':
                    key = {'}': '{', ')': '(', ']': '['}[ch]
                    depth[key] -= 1
                    if depth[key] < 0:
                        err(p, ln, "R5-balance", f"unmatched closing {ch!r}")
        for k, v in depth.items():
            if v != 0:
                err(p, len(code), "R5-balance", f"{v:+d} unbalanced {k!r} across file")


def rule6_xml():
    candidates = [MANIFEST, *sorted(RESDIR.rglob("*.xml"))]
    for p in candidates:
        try:
            ET.parse(p)
        except ET.ParseError as e:
            err(p, None, "R6-xml", f"XML parse error: {e}")


ANDROID_ATTR_REF = re.compile(r"@(?!android:)([a-z][a-z0-9]*)/([A-Za-z0-9_.]+)")


def _res_index():
    idx = set()
    for p in RESDIR.rglob("*"):
        if not p.is_file():
            continue
        typ = p.parent.name
        if typ.startswith("mipmap"):
            typ = "mipmap"
        elif typ.startswith("drawable"):
            typ = "drawable"
        name = p.stem
        idx.add((typ, name))
    # values/*.xml declare string/color/style/... as <type name="..."> elements.
    element_types = {
        "string", "color", "style", "drawable", "bool", "integer", "plurals",
        "array", "string-array", "integer-array", "dimen", "font", "bools",
    }
    for p in (RESDIR / "values").rglob("*.xml"):
        try:
            tree = ET.parse(p)
        except ET.ParseError:
            continue
        for el in tree.getroot():
            name = el.attrib.get("name")
            if not name:
                continue
            tag = el.tag.split("}")[-1]
            typ = tag if tag in element_types else None
            if typ == "bools":
                typ = "bool"
            if typ:
                idx.add((typ, name))
    return idx


def rule7_resources(files):
    res = _res_index()
    ref_re = re.compile(r"@(?!android:)([a-z][a-z0-9]*)/([A-Za-z0-9_.]+)")
    r_re = re.compile(r"(?<!android\.)(?<![\w.])R\.([a-z][a-z0-9]*)\.([A-Za-z0-9_]+)")
    # XML references
    for p in sorted(RESDIR.rglob("*.xml")) + [MANIFEST]:
        text = p.read_text("utf-8", errors="replace")
        for m in ref_re.finditer(text):
            typ, name = m.group(1), m.group(2)
            if (typ, name) not in res:
                line = text.count('\n', 0, m.start()) + 1
                err(p, line, "R7-resref", f"@{typ}/{name} does not resolve to a file in res/")
    # Kotlin R.* references
    for p in files:
        code_lines = code_of_file(p)
        for ln, line in enumerate(code_lines, 1):
            for m in r_re.finditer(line):
                typ, name = m.group(1), m.group(2)
                if (typ, name) not in res:
                    err(p, ln, "R7-resref", f"R.{typ}.{name} does not resolve to a file in res/")


def rule1_setter_clash(files):
    for p in files:
        code = code_of_file(p)
        state_vars = set()
        fun_setters = {}
        var_re = re.compile(r"\bvar\s+([A-Za-z_]\w*)\s+by\s+(mutableStateOf|mutableIntStateOf|mutableFloatStateOf|mutableDoubleStateOf|mutableStateListOf|mutableStateMapOf)")
        fun_re = re.compile(r"\bfun\s+set([A-Z]\w*)")
        for ln, line in enumerate(code, 1):
            for m in var_re.finditer(line):
                state_vars.add(m.group(1))
            for m in fun_re.finditer(line):
                fun_setters.setdefault(m.group(1), (ln, m.group(0)))
        for name, (ln, decl) in fun_setters.items():
            prop = name[0].lower() + name[1:]
            if prop in state_vars:
                err(p, ln, "R1-setter-clash",
                    f"{decl} clashes with `var {prop} by mutableStateOf`; rename to update{name} "
                    f"(setters are synthesized for delegated Compose state)")


def rule3_icon_symbols():
    comp = KTDIR / "ui" / "Components.kt"
    if not comp.exists():
        return
    text = read_text(comp)
    start = text.find("object Ico")
    if start == -1:
        return
    depth, i = 0, text.find("{", start)
    j = i
    while j < len(text):
        if text[j] == '{':
            depth += 1
        elif text[j] == '}':
            depth -= 1
            if depth == 0:
                break
        j += 1
    body = text[i:j]
    names = set(re.findall(r"\bval\s+(\w+)\s*=\s*Ico[A-Za-z0-9_]*", body))
    for p in KT_FILES:
        for ln, line in enumerate(code_of_file(p), 1):
            for m in re.finditer(r"\bIco\.(\w+)", line):
                name = m.group(1)
                if name not in names:
                    err(p, ln, "R3-icon", f"Ico.{name} is not declared in Components.kt object Ico")


def _member_index():
    text = read_text(KTDIR / "BrowserViewModel.kt")
    code = code_of_file(KTDIR / "BrowserViewModel.kt", text)
    combined = "\n".join(code)
    members = set()
    for m in re.finditer(r"\bfun\s+([a-zA-Z_]\w*)", combined):
        members.add(m.group(1))
    for m in re.finditer(r"\b(?:var|val)\s+([A-Za-z_]\w*)", combined):
        members.add(m.group(1))
    return members


def rule2_vm_members():
    members = _member_index()
    for p in KT_FILES:
        for ln, line in enumerate(code_of_file(p), 1):
            for m in re.finditer(r"\bvm\.([A-Za-z_]\w*)", line):
                name = m.group(1)
                if name not in members:
                    err(p, ln, "R2-vm-member", f"vm.{name} is not declared in BrowserViewModel.kt")


def rule4_same_module_imports():
    declared = set()
    for p in KT_FILES:
        code_combined = "\n".join(code_of_file(p))
        patterns = [
            r"\b(?:data\s+class|sealed\s+class|enum\s+class|annotation\s+class|class|object|interface)\s+([A-Za-z_]\w*)",
            r"\bfun\s+([a-zA-Z_]\w*)",
            r"\b(?:val|var|const\s+val)\s+([A-Za-z_]\w*)",
        ]
        for pat in patterns:
            declared.update(re.findall(pat, code_combined))
    for p in KT_FILES:
        for ln, line in enumerate(raw_lines(p), 1):
            m = re.match(r"\s*import\s+(?:(?:static)\s+)?com\.example\.geckobrowser\.(?:[\w.]+\.)?(\w+)", line)
            if not m:
                continue
            name = m.group(1)
            if name == "*" or name in ("R", "BuildConfig"):
                continue
            if name not in declared:
                err(p, ln, "R4-import", f"import of com.example.geckobrowser.{name} does not resolve in the module")


def rule8_buildconfig():
    gradle_text = GRADLE_APP.read_text("utf-8") if GRADLE_APP.exists() else ""
    gradle_has = re.search(r"buildConfig\s*true", gradle_text) is not None
    for p in KT_FILES:
        code_combined = "\n".join(code_of_file(p))
        if "BuildConfig." in code_combined and not gradle_has:
            err(p, None, "R8-buildconfig",
                "BuildConfig referenced but buildFeatures.buildConfig is not enabled in app/build.gradle")
            return


def rule9_haptics_scope():
    """Every haptics.performHapticFeedback needs `val haptics` in the enclosing function."""
    for p in KT_FILES:
        has_haptics = False
        for ln, line in enumerate(code_of_file(p), 1):
            if re.match(r"^\s*(?:(?:private|internal|protected|public)\s+)*(?:suspend\s+)?fun\s+", line):
                has_haptics = False
            elif re.match(r"^\s*(?:companion\s+object\b|object\s|data\s+class\s|class\s)", line):
                has_haptics = False
            if re.search(r"\bval\s+haptics\s*=", line):
                has_haptics = True
            if "haptics.performHapticFeedback" in line and not has_haptics:
                err(p, ln, "R9-haptics-scope",
                    "haptics.performHapticFeedback without `val haptics = LocalHapticFeedback.current` in this function")


# ------------------------------------------------------------------ main

def main():
    annotate = "--annotations" in sys.argv
    emit = None
    if "--emit-report" in sys.argv:
        emit = Path(sys.argv[sys.argv.index("--emit-report") + 1])

    try:
        rule5_structure(KT_FILES)
    except UnicodeDecodeError as e:
        for p in KT_FILES:
            try:
                read_text(p)
            except UnicodeDecodeError:
                err(p, None, "R5-encoding", f"file is not valid UTF-8: {e}")

    rule6_xml()
    rule7_resources(KT_FILES)
    rule1_setter_clash(KT_FILES)
    rule3_icon_symbols()
    rule2_vm_members()
    rule4_same_module_imports()
    rule8_buildconfig()
    rule9_haptics_scope()

    errors = sum(1 for f in findings if f[0] == "ERROR")
    warns = sum(1 for f in findings if f[0] == "WARN")

    # ---- report
    lines = [
        "# precompile-report",
        f"commit: {ROOT.name}",
        "",
    ]
    for rule_name in ["R5-mojibake", "R5-balance", "R5-encoding", "R6-xml", "R7-resref",
                      "R1-setter-clash", "R3-icon", "R2-vm-member", "R4-import", "R8-buildconfig",
                      "R9-haptics-scope"]:
        hits = [f for f in findings if f[3] == rule_name]
        status = "PASS" if not hits else f"FAIL ({len(hits)})"
        lines.append(f"## {rule_name}  {status}")
        for level, rel, line, _rule, msg in hits:
            ln = line if line is not None else "?"
            lines.append(f"   {rel}:{ln}: {msg}")
    lines.append("")
    result = "PASS" if errors == 0 else "FAIL"
    lines.append(f"RESULT: {result} ({errors} errors, {warns} warnings)")
    report = "\n".join(lines) + "\n"

    print(report)
    for level, rel, line, rule, msg in findings:
        ln = line if line is not None else 1
        print(f"{rel}:{ln}: [{rule}] {msg}")
        if annotate and level == "ERROR":
            print(f"::error file={rel},line={ln}::{rule}: {msg}")

    if emit:
        emit.parent.mkdir(parents=True, exist_ok=True)
        emit.write_text(report, encoding="utf-8")
    return 0 if errors == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
