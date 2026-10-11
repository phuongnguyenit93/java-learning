#!/usr/bin/env python3
"""Validate and package the static Java learning data produced by Gradle.

Only the generated files inside build/learning-data/java are exported.  This
script neither modifies authored Java content nor calls GitHub/Cloudflare.
"""

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath
from urllib.parse import unquote, urlsplit

import yaml


EXPOSED_FIELDS = (
    "overview", "knowledge", "roadmap", "video", "quiz",
    "interview", "api", "reference",
)
ALLOWED_EXTENSIONS = frozenset({".json", ".yml", ".yaml", ".md"})
SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fail(message):
    raise ValueError(message)


def resolve_asset(directory, value):
    """Resolve only local public asset paths; never escape the data directory."""
    if not isinstance(value, str) or not value.startswith(("/module/", "/search/")):
        fail(f"Invalid public asset reference: {value!r}")
    split = urlsplit(value)
    path = unquote(split.path)
    parts = PurePosixPath(path.lstrip("/")).parts
    if not parts or any(part in (".", "..") for part in parts):
        fail(f"Unsafe public asset path: {value}")
    target = directory.joinpath(*parts)
    if not target.exists():
        fail(f"Missing referenced asset: {value}")
    return target


def validate(directory):
    if not directory.is_dir():
        fail(f"Generated data directory not found: {directory}")
    catalog_file = directory / "module-catalog.json"
    if not catalog_file.is_file():
        fail("Missing module-catalog.json; run ./gradlew generatePortalData first")
    if not (directory / "module").is_dir() or not (directory / "search").is_dir():
        fail("Expected module/ and search/ directories in generated data")

    files = sorted(directory.rglob("*"))
    regular = []
    casefolded = set()
    counts = {extension: 0 for extension in ALLOWED_EXTENSIONS}
    for file in files:
        if file.is_symlink():
            fail(f"Symbolic links are forbidden in the artifact: {file}")
        if file.is_dir():
            continue
        if not file.is_file():
            fail(f"Non-file generated artifact: {file}")
        relative = file.relative_to(directory).as_posix()
        folded = relative.casefold()
        if folded in casefolded:
            fail(f"Case-insensitive duplicate generated path: {relative}")
        casefolded.add(folded)
        suffix = file.suffix.lower()
        if suffix not in ALLOWED_EXTENSIONS:
            fail(
                f"Forbidden generated file type: {relative} "
                f"(allowed: {', '.join(sorted(ALLOWED_EXTENSIONS))})"
            )
        if file.stat().st_size == 0:
            fail(f"Empty generated file: {relative}")
        counts[suffix] += 1
        if suffix == ".json":
            try:
                with file.open(encoding="utf-8-sig") as stream:
                    json.load(stream)
            except (ValueError, UnicodeError) as exc:
                fail(f"Malformed JSON {relative}: {exc}")
        elif suffix in (".yml", ".yaml"):
            try:
                with file.open(encoding="utf-8-sig") as stream:
                    for _ in yaml.safe_load_all(stream):
                        pass
            except (yaml.YAMLError, UnicodeError, ValueError) as exc:
                fail(f"Malformed YAML {relative}: {exc}")
        regular.append(file)

    with catalog_file.open(encoding="utf-8-sig") as stream:
        catalog = json.load(stream)
    if not isinstance(catalog, dict) or catalog.get("version") != 1:
        fail("Unexpected module-catalog.json schema version")
    root = catalog.get("root")
    if not isinstance(root, dict) or not isinstance(root.get("children"), list):
        fail("Invalid module-catalog.json root")

    seen_modules = set()
    checked_refs = 0

    def walk(node):
        nonlocal checked_refs
        if not isinstance(node, dict):
            fail(f"Invalid catalog node: {node!r}")
        if node.get("kind") == "MODULE":
            module_id = node.get("routeId")
            if not isinstance(module_id, str) or not module_id:
                fail(f"MODULE without routeId: {node.get('id')}")
            if module_id in seen_modules:
                fail(f"Duplicate routeId in catalog: {module_id}")
            seen_modules.add(module_id)
            for field in EXPOSED_FIELDS:
                value = node.get(field)
                if value is None:
                    continue
                refs = value.values() if isinstance(value, dict) else [value]
                for ref in refs:
                    resolve_asset(directory, ref)
                    checked_refs += 1
        children = node.get("children", [])
        if not isinstance(children, list):
            fail(f"Invalid children in catalog node: {node.get('id')}")
        for child in children:
            walk(child)

    walk(root)
    if not seen_modules or checked_refs == 0:
        fail("No module data references were validated")

    # Generated Knowledge/Video indexes may refer to lazy-loaded static files.
    # Check those references too, not only the module's index.json.
    for file in regular:
        relative = file.relative_to(directory).as_posix()
        if not (
            relative.endswith("/knowledge/vi/index.json")
            or relative.endswith("/knowledge/en/index.json")
            or relative.endswith("/video/vi/index.json")
            or relative.endswith("/video/en/index.json")
        ):
            continue
        with file.open(encoding="utf-8-sig") as stream:
            document = json.load(stream)

        def check_refs(value):
            nonlocal checked_refs
            if isinstance(value, dict):
                for item in value.values():
                    check_refs(item)
            elif isinstance(value, list):
                for item in value:
                    check_refs(item)
            elif isinstance(value, str) and value.startswith(("/module/", "/search/")):
                resolve_asset(directory, value)
                checked_refs += 1

        check_refs(document)

    if counts[".json"] < 3 or not (counts[".yml"] or counts[".yaml"]):
        fail(f"Generated data looks incomplete: {counts}")
    for language in ("vi", "en"):
        search = directory / "search" / f"knowledge-search.{language}.json"
        if not search.is_file():
            fail(f"Missing localized search index: {search.relative_to(directory)}")
    total_bytes = sum(file.stat().st_size for file in regular)
    print(
        f"VALIDATED: {len(regular)} files, {total_bytes} bytes, "
        f"{len(seen_modules)} modules, {checked_refs} asset references, "
        f"JSON={counts['.json']}, YAML={counts['.yml'] + counts['.yaml']}"
    )
    return regular, len(seen_modules), checked_refs, total_bytes


def package(directory, output, commit, commit_time):
    if not SHA_RE.fullmatch(commit):
        fail("source-commit must be a full 40-character lowercase git SHA")
    files, modules, checked_refs, total_bytes = validate(directory)
    output.mkdir(parents=True, exist_ok=True)
    archive = output / "java-learning-data.zip"
    checksum_file = output / "java-learning-data.zip.sha256"
    manifest = {
        "schemaVersion": 1,
        "producer": "java-learning",
        "sourceCommit": commit,
        "sourceCommitTime": commit_time,
        "fileCount": len(files),
        "moduleCount": modules,
        "referenceCount": checked_refs,
        "totalBytes": total_bytes,
        "catalogSha256": sha256(directory / "module-catalog.json"),
    }

    # Stable entry order/timestamps make a package reproducible for a commit.
    with zipfile.ZipFile(
        archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6,
        allowZip64=True,
    ) as z:
        entries = [("manifest.json", None)] + [
            (file.relative_to(directory).as_posix(), file) for file in files
        ]
        for name, file in entries:
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            if file is None:
                body = (json.dumps(manifest, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
                z.writestr(info, body, compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
            else:
                with file.open("rb") as source, z.open(info, "w", force_zip64=True) as target:
                    for chunk in iter(lambda: source.read(1024 * 1024), b""):
                        target.write(chunk)

    digest = sha256(archive)
    checksum_file.write_text(f"{digest}  {archive.name}\n", encoding="ascii")
    with zipfile.ZipFile(archive) as z:
        names = z.namelist()
        if len(names) != len(files) + 1 or len(set(names)) != len(names):
            fail("ZIP entry count mismatch or duplicate archive paths")
        if json.loads(z.read("manifest.json")) != manifest:
            fail("Manifest mismatch inside ZIP")
    print(f"PACKAGED: {archive} ({archive.stat().st_size} bytes)")
    print(f"SHA256: {digest}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["validate", "package"])
    parser.add_argument("--data-dir", type=Path, default=Path("build/learning-data/java"))
    parser.add_argument("--output-dir", type=Path, default=Path("build/java-data-release"))
    parser.add_argument("--source-commit", default="")
    parser.add_argument("--source-commit-time", default="")
    args = parser.parse_args()
    try:
        if args.action == "validate":
            validate(args.data_dir)
        else:
            if not args.source_commit_time:
                fail("Missing --source-commit-time")
            package(
                args.data_dir, args.output_dir,
                args.source_commit, args.source_commit_time,
            )
    except (ValueError, OSError, zipfile.BadZipFile) as exc:
        print(f"PUBLISHER ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
