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
  last `beta-build-*` tag when no stable tag exists yet) are grouped into sections:
  Features, Fixes, Performance, Other. A `BREAKING` subject goes to Highlights.
* "Confident" means at least one user-facing change (feat/fix/perf/breaking) was
  found in the range. With --require-confident (stable), an unsure range exits 2
  and asks; supply --notes-override with the notes to publish instead.
* Without --require-confident (beta), an unsure range still publishes, but the
  body is marked "Auto-generated -- unreviewed" and lists the raw commits.
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
    s = subject.strip()
    breaking = bool(re.match(r"^\w+(\([^)]*\))?!:\s", s)) or "BREAKING CHANGE" in s.upper()
    m = re.match(r"^(feat|feature|fix|bugfix|perf|refactor|chore|ci|docs|style|test|build)(!?\([^)]*\))?!?:\s*(.+)$", s, re.I)
    if m:
        kind = m.group(1).lower()
        rest = m.group(3).strip()
        if kind in ("feat", "feature"):
            return ("Features", rest, breaking)
        if kind in ("fix", "bugfix"):
            return ("Fixes", rest, breaking)
        if kind == "perf":
            return ("Performance", rest, breaking)
        return ("Other", rest, breaking)
    sl = s.lower()
    if re.search(r"\b(revert|fix|repair|correct|restore|mojibake|corrupt|unresolved|missing|bump)\b", sl):
        return ("Fixes", s, breaking)
    if re.search(r"\b(add|implement|enable|introduce|support|revamp|redesign|new)\b", sl):
        return ("Features", s, breaking)
    return ("Other", s, breaking)
    s = subject.strip()
    breaking = bool(re.match(r"^\w+(\([^)]*\))?!:\s", s)) or "BREAKING CHANGE" in s.upper()
    m = re.match(r"^(feat|feature|fix|bugfix|perf|refactor|chore|ci|docs|style|test|build)(!?\([^)]*\))?!?:\s*(.+)$", s, re.I)
    if m:
        kind = m.group(1).lower()
        rest = m.group(3).strip()
        if kind in ("feat", "feature"):
            return ("Features", rest, breaking)
        if kind in ("fix", "bugfix"):
            return ("Fixes", rest, breaking)
        if kind == "perf":
            return ("Performance", rest, breaking)
        return ("Other", rest, breaking)
    # Second chance: freeform subjects. Keyword-score them so the common
    # "Area: thing" style of this repo lands somewhere useful.
    sl = s.lower()
    if re.search(r"\b(revert|fix|repair|correct|restore|mojibake|corrupt|unresolved|missing|bump)\b", sl):
        return ("Fixes", s, breaking)
    if re.search(r"\b(add|implement|enable|introduce|support|revamp|redesign|new)\b", sl):
        return ("Features", s, breaking)
    return ("Other", s, breaking)


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
                "unsure release notes: no user-facing (feat/fix/perf/breaking) commits found "
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
    compare = f"\n\n**Full changelog:** {REPO_URL}/compare/{base}...HEAD" if base else ""
    return f"{header}\n\n{override_text}{compare}"


def header_of(channel, commit_count, base):
    date = datetime.now(timezone.utc).strftime("%d-%m-%Y")
    kind = "Beta" if channel == "beta" else "Stable"
    base_line = f"since `{base}`" if base else "from the first commit"
    return f"## {kind} - {date}\n\n_{commit_count} commits {base_line} | auto-generated {date}_"


def build_generated(channel, base, rows):
    if not rows:
        body = (
            f"## {'Beta' if channel == 'beta' else 'Stable'} - {datetime.now(timezone.utc).strftime('%d-%m-%Y')}\n\n"
            f"No commits found."
        )
        return body, False

    highlights, features, fixes, perf, other = [], [], [], [], []
    for subject, sha in rows:
        section, text, breaking = section_of(subject)
        if breaking:
            highlights.append(f"- {subject} ({sha})")
        elif section == "Features":
            features.append(f"- {text} ({sha})")
        elif section == "Fixes":
            fixes.append(f"- {text} ({sha})")
        elif section == "Performance":
            perf.append(f"- {text} ({sha})")
        else:
            other.append(f"- {subject} ({sha})")

    user_facing = bool(highlights or features or fixes or perf)
    header = header_of(channel, len(rows), base)

    def block(title, items):
        if not items:
            return ""
        return f"### {title}\n" + "\n".join(items) + "\n\n"

    compare = f"\n**Full changelog:** {REPO_URL}/compare/{base}...HEAD\n" if base else "\n"
    sections = (
        block("Highlights", highlights)
        + block("Features", features)
        + block("Fixes", fixes)
        + block("Performance", perf)
        + block("Other changes", other)
    )
    if not user_facing:
        note = (
            "> [!] Auto-generated, unreviewed - no user-facing (feat/fix/perf/breaking) commits found; "
            "the raw commit list is reproduced above.\n\n"
        )
        return header + "\n\n" + note + sections + compare, False
    return header + "\n\n" + sections + compare, True


if __name__ == "__main__":
    sys.exit(main())
