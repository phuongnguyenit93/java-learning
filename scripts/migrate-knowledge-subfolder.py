#!/usr/bin/env python3
"""Move chapter Markdown under knowledge/ and update Knowledge path references.

Read-only by default. Run with --apply after reviewing the printed counts.
Does not touch video files, directories, or content/video trees.

Legacy Markdown directly in menu/ has no unambiguous chapter identity and is
reported, not moved into an invented chapter.
"""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import re
import sys

CHAPTER = re.compile(r"^[0-9]+\.[^/\\]+$")
RELATION = re.compile(
    rb"(?<![A-Za-z0-9_./-])([0-9]+\.[^/\r\n]+/)"
    rb"([A-Za-z][A-Za-z0-9_]*\.md)(?![A-Za-z0-9_./-])"
)
DOCS = re.compile(
    rb"(content/readme/docs/(?:vi|en)/menu/[0-9]+\.[^/\r\n]+/)"
    rb"([A-Za-z][A-Za-z0-9_]*\.md)(?![A-Za-z0-9_./-])"
)


def under_menu(path: Path) -> bool:
    parts = path.parts
    return any(
        parts[i:i + 4] == ("content", "readme", "docs", lang)
        and len(parts) > i + 4
        and parts[i + 4] == "menu"
        for i in range(len(parts) - 4) for lang in ("vi", "en")
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Perform checked migration")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    modules = root / "module"
    moves: list[tuple[Path, Path, str]] = []
    legacy_root: list[Path] = []

    for path in sorted(modules.rglob("*.md")):
        if path.is_symlink() or not path.is_file() or not under_menu(path):
            continue
        parent = path.parent
        if CHAPTER.fullmatch(parent.name) and parent.parent.name == "menu":
            target = parent / "knowledge" / path.name
            if target.exists():
                raise RuntimeError(f"Destination already exists: {target}")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            moves.append((path, target, digest))
        elif parent.name == "menu":
            legacy_root.append(path)

    # Only rewrite references to chapter Markdown, never 'content/video'.
    changes: list[tuple[Path, bytes, bytes]] = []
    for path in sorted(modules.rglob("*")):
        if path.is_symlink() or not path.is_file() or path.suffix not in (
            ".yml", ".yaml", ".json", ".md", ".groovy", ".gradle"
        ):
            continue
        if "video" in path.parts or path.name == "video-script.yml":
            continue
        if path in {source for source, _, _ in moves}:
            # Source content stays byte-for-byte identical during relocation.
            continue
        original = path.read_bytes()
        updated = DOCS.sub(rb"\1knowledge/\2", original)
        # Normalize bare chapter/file.md references, e.g. metadata YAML keys
        # and readmeRelated.file, while leaving already migrated refs untouched.
        updated = RELATION.sub(rb"\1knowledge/\2", updated)
        if updated != original:
            changes.append((path, original, updated))

    print(f"Knowledge Markdown files to move: {len(moves)}")
    print(f"Non-Knowledge reference files to rewrite: {len(changes)}")
    print(f"Legacy root-level menu Markdown untouched: {len(legacy_root)}")
    for path in legacy_root[:12]:
        print("  LEGACY:", path.relative_to(root))

    if not args.apply:
        print("DRY_RUN: no files modified; use --apply after review.")
        return 0

    for source, target, digest in moves:
        if not source.is_file() or target.exists():
            raise RuntimeError(f"Changed since preflight: {source} -> {target}")
        if hashlib.sha256(source.read_bytes()).hexdigest() != digest:
            raise RuntimeError(f"Modified since preflight: {source}")
    for path, original, _ in changes:
        if path.read_bytes() != original:
            raise RuntimeError(f"Reference changed since preflight: {path}")

    # No bulk string rewriting of Knowledge content. Git detects byte-exact
    # moves as renames. This keeps code fences and authored text unchanged.
    for source, target, digest in moves:
        target.parent.mkdir(parents=True, exist_ok=True)
        source.rename(target)
        if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
            raise RuntimeError(f"Checksum mismatch after move: {target}")
    for path, _, updated in changes:
        path.write_bytes(updated)
    print(f"APPLIED: moved {len(moves)}, updated references {len(changes)}.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print(f"MIGRATION BLOCKED: {error}", file=sys.stderr)
        sys.exit(1)
