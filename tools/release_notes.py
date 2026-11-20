#!/usr/bin/env python3
"""
Generate release notes from commits since the last tag of the channel.

Usage:
  python tools/release_notes.py --channel beta|stable [--out PATH]
                                [--base-ref TAG] [--notes-override PATH]
                                [--require-confident]

Rules
-----
* Commits since the last `{channel}-build-*` tag (for stable, falling back to the
  last `beta-build-*` tag when no stable tag exists yet) are grouped into
  balanced, emoji-headed sections. Breaking changes go to Highlights.
* "Confident" means at least one user-facing change (anything except
  "Other changes") was found in the range. With --require-confident (stable),
  an unsure range exits 2 and asks; supply --notes-override with the notes to
  publish instead.
* Without --require-confident (beta), an unsure range still publishes, but the
  body is marked "Unreviewed" and lists the raw commits.
* Output is written to --out (default: dist/RELEASE_NOTES_<channel>.md) and also
  printed. Exit codes: 0 ok, 2 unsure (stable only).
"""

import argparse
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

REPO_URL = subprocess.run(
    ["git", "remote", "get-url", "origin"], capture_output=True, text=True, check=False,
).stdout.strip()
if REPO_URL.endswith(".git"):
    REPO_URL = REPO_URL[:-4]
if REPO_URL.startswith("git@github.com:"):
    REPO_URL = "https://github.com/" + REPO_URL[len("git@github.com:"):]

# Balanced categories, shared verbatim by beta and stable.
CATEGORIES = (
    ("Highlights", "🚨"),
    ("Features", "✨"),
    ("Fixes", "🐛"),
    ("Performance", "⚡"),
    ("Appearance & UI", "🎨"),
    ("Settings & Preferences", "⚙️"),
    ("Extensions", "🧩"),
    ("Privacy & Security", "🔐"),
    ("Browser & Navigation", "📱"),
    ("Other changes", "📝"),
)
USER_FACING = {name for name, _ in CATEGORIES} - {"Other changes", "Highlights"}

AREA_RULES = (
    ("Appearance & UI", (
        r"\b(blur|frost|glass|accent|theme|dark|light|oled|colour|color|ui|glyph|icon|"
        r"capsule|pill|box|boxes|opacity|underglow|haptic)\b",
    )),
    ("Settings & Preferences", (
        r"\b(setting|settings|preference|preferences|toggle|search settings|appearance)\b",
    )),
    ("Extensions", (r"\b(extension|extensions|add-?on|addons|xpi)\b",)),
    ("Privacy & Security", (
        r"\b(privacy|tracking|tracker|cookie|cookies|permission|permissions|password|"
        r"passwords|secure|security|https)\b",
    )),
    ("Browser & Navigation", (
        r"\b(tab|tabs|pin|pins|pinned|navigat\w*|back|forward|history|download|downloads|"
        r"find|translate|immersive|ota|update|search|menu|address|url|zoom)\b",
    )),
)


def git(*args):
    return subprocess.run(
        ["git", *args], capture_output=True, text=True, check=False,
    ).stdout.strip()


def commits_since(base):
    rng = f"{base}..HEAD" if base else "HEAD"
    out = git("log", rng, "--no-merges", "--pretty=format:%s%x1f%h")
    rows = []
    for line in out.split("\n"):
        if not line.strip():
            continue
        parts = line.rsplit("\x1f", 1)
        if len(parts) != 2:
            continue
        rows.append((parts[0].strip(), parts[1].strip()))
    return rows


def section_of(subject):
    """Return (category, display text, breaking) for one commit subject."""
    s = subject.strip()
    breaking = bool(re.match(r"^\w+(\([^)]*\))?!:\s", s)) or "BREAKING CHANGE" in s.upper()
    conventional = re.match(
        r"^(feat|feature|fix|bugfix|perf|refactor|chore|ci|docs|style|test|build)"
        r"(!?\([^)]*\))?!?:\s*(.+)$",
        s, re.I,
    )
    if conventional:
        kind = conventional.group(1).lower()
        rest = conventional.group(3).strip()
        if kind in ("feat", "feature"):
            return "Features", rest, breaking
        if kind in ("fix", "bugfix"):
            return "Fixes", rest, breaking
        if kind == "perf":
            return "Performance", rest, breaking
        if breaking:
            return "Highlights", rest, True
        return "Other changes", rest, breaking

    sl = s.lower()
    if breaking:
        return "Highlights", s, True
    # Intent first: a fix is a fix whichever area it touches.
    if re.search(r"\b(revert|fix(?:es|ed)?|repair|correct|restore|mojibake|corrupt|unresolved|missing|compile error)\b", sl):
        return "Fixes", s, breaking
    if re.search(r"\b(perf|faster|speed|lag|laggy|taxing|frame|fps|optimis|optimiz)\b", sl):
        return "Performance", s, breaking
    if re.search(r"\b(add|adds|added|implement|enable|introduce|support|revamp|redesign|new)\b", sl):
        return "Features", s, breaking
    # Then area, for the freeform "Area: thing" subjects this repo uses a lot.
    for category, patterns in AREA_RULES:
        if any(re.search(p, sl) for p in patterns):
            return category, s, breaking
    return "Other changes", s, breaking


def tidy(text):
    """Sentence-case a commit fragment without mangling identifiers."""
    text = text.strip().rstrip(".")
    if not text:
        return text
    return text[0].upper() + text[1:]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--channel", choices=["beta", "stable"], required=True)
    ap.add_argument("--out", default=None)
    ap.add_argument("--base-ref", default=None)
    ap.add_argument("--notes-override", default=None)
    ap.add_argument("--require-confident", action="store_true")
    args = ap.parse_args()

    channel = args.channel
    out_path = Path(args.out or f"dist/RELEASE_NOTES_{channel}.md")

    override_text = None
    if args.notes_override:
        p = Path(args.notes_override)
        if p.exists():
            override_text = p.read_text("utf-8").strip()
        else:
            override_text = args.notes_override.strip()

    base = args.base_ref or git("tag", "--list", f"{channel}-build-*", "--sort=-v:refname", "-n0", "--format=%(refname:short)", "--").split("\n")[0].strip()
    if not base and channel == "stable":
        base = git("tag", "--list", "beta-build-*", "--sort=-v:refname", "-n0", "--format=%(refname:short)").split("\n")[0].strip()
    rows = commits_since(base if base else None)

    if override_text:
        body = build_body(channel, base, rows, override_text)
        confident = True
    else:
        body, confident = build_generated(channel, base, rows)
        if args.require_confident and not confident:
            sys.stderr.write(
                "unsure release notes: no user-facing changes found "
                f"since {base or 'the first commit'}; refusing to guess.\n"
                "Re-run with --notes-override to publish with notes you wrote.\n"
            )
            return 2

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(body + "\n", encoding="utf-8")
    print(body)
    return 0


def build_body(channel, base, rows, override_text):
    header = header_of(channel, len(rows), base)
    compare = f"\n\n📚 **Full changelog:** {REPO_URL}/compare/{base}...HEAD" if base else ""
    return f"{header}\n\n{override_text}{compare}"


def header_of(channel, commit_count, base):
    date = datetime.now(timezone.utc).strftime("%d-%m-%Y")
    emoji, kind = ("🧪", "Beta") if channel == "beta" else ("🛡️", "Stable")
    base_line = f"since `{base}`" if base else "from the first commit"
    return f"## {emoji} {kind} - {date}\n\n_{commit_count} commits {base_line} | auto-generated {date}_"


def build_generated(channel, base, rows):
    if not rows:
        body = (
            f"## {'🧪 Beta' if channel == 'beta' else '🛡️ Stable'} - "
            f"{datetime.now(timezone.utc).strftime('%d-%m-%Y')}\n\n"
            f"📝 No commits found."
        )
        return body, False

    grouped = {name: [] for name, _ in CATEGORIES}
    for subject, sha in rows:
        category, text, breaking = section_of(subject)
        line = f"- {tidy(text)}  `_({sha})_`"
        if breaking:
            grouped["Highlights"].append(line)
        grouped[category].append(line)

    confident = any(grouped[name] for name in USER_FACING)
    header = header_of(channel, len(rows), base)

    def block(name):
        items = grouped[name]
        if not items:
            return ""
        emoji = next(e for n, e in CATEGORIES if n == name)
        return f"### {emoji} {name}\n" + "\n".join(items) + "\n\n"

    sections = "".join(block(name) for name, _ in CATEGORIES)
    compare = f"\n📚 **Full changelog:** {REPO_URL}/compare/{base}...HEAD\n" if base else "\n"
    if not confident:
        note = (
            "> ⚠️ **Unreviewed** — no user-facing changes were detected; the raw "
            "commit list is reproduced above.\n\n"
        )
        return header + "\n\n" + note + sections + compare, False
    return header + "\n\n" + sections + compare, True


if __name__ == "__main__":
    sys.exit(main())
