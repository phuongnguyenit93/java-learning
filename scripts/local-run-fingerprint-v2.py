#!/usr/bin/env python3
"""Match the Gradle LocalRunFingerprintService V2 Git tree input algorithm.

Never use working-tree hashes: both the portal catalog and Actions must agree
on the committed snapshot even if their checkouts are at different points in time.
"""

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INPUTS = ROOT / "project-build/gradle-runtime/src/main/resources/local-run/fingerprint-v2-roots.txt"
HEADER = "local-run-fingerprint-v2\n"


def git_object(rev: str, path: str) -> str:
    process = subprocess.run(
        ["git", "-C", str(ROOT), "rev-parse", f"{rev}:{path}"],
        capture_output=True,
        text=True,
        check=False,
    )
    oid = process.stdout.strip().lower()
    return oid if process.returncode == 0 and re.fullmatch(r"[0-9a-f]{40,64}", oid) else "MISSING"


def git_file(rev: str, path: str) -> str | None:
    result = subprocess.run(
        ["git", "-C", str(ROOT), "show", f"{rev}:{path}"],
        capture_output=True, text=True, check=False, encoding="utf-8",
    )
    return result.stdout if result.returncode == 0 else None


def index_services(revision: str) -> dict[str, str]:
    result = {}
    listed = subprocess.run(
        ["git", "-C", str(ROOT), "ls-tree", "-r", "--name-only", "-z", revision,
         "module", "internal", "project-build/springboot-runtime"],
        capture_output=True, check=True,
    )
    paths = {item.decode("utf-8") for item in listed.stdout.split(b"\0") if item}
    for master_path in sorted(path for path in paths if path.endswith("/master.json")):
        parent = master_path.rsplit("/", 1)[0]
        if f"{parent}/gradle.properties" not in paths:
            continue
        master_source = git_file(revision, master_path)
        if master_source is None:
            raise ValueError(f"Missing committed metadata: {master_path}")
        field = json.loads(master_source).get("SERVICE_NAME")
        name = field.get("VALUE") if isinstance(field, dict) else field
        if name:
            key = str(name).strip().upper()
            if key in result and result[key] != parent:
                raise ValueError(f"Ambiguous Local Run dependency SERVICE_NAME: {key}")
            result[key] = parent
    return result


def dependent_paths(module_path: str, paths: set[str], visited: set[str],
                    services: dict[str, str], revision: str) -> None:
    if module_path in visited:
        return
    visited.add(module_path)
    paths.add(module_path)
    if git_object(revision, module_path) == "MISSING":
        raise ValueError(f"Local Run dependency missing at {revision}: {module_path}")

    prop_source = git_file(revision, f"{module_path}/properties.json")
    if prop_source is not None:
        props = json.loads(prop_source)
        for group, key in (
            ("MODULE_DEPEND", "MODULE_DEPEND_LIST"),
            ("DATABASE", "DATABASE_LIST"),
            ("YML", "BUILD_YML_MODULE_DEPEND"),
        ):
            entry = props.get(group, {}).get(key, {})
            raw = entry.get("VALUE", "") if isinstance(entry, dict) else ""
            csv = ",".join(map(str, raw)) if isinstance(raw, list) else str(raw or "")
            for service in (item.strip().upper() for item in csv.split(",")):
                if service:
                    if service not in services:
                        raise ValueError(f"Unresolved Local Run dependency {service} in {module_path}")
                    dependent_paths(services[service], paths, visited, services, revision)

    gradle_source = git_file(revision, f"{module_path}/build.gradle")
    if gradle_source is not None:
        for project in re.findall(r"project\s*\(\s*['\"](:[A-Za-z0-9_:-]+)['\"]\s*\)",
                                  gradle_source):
            dependent_paths(project[1:].replace(":", "/"), paths, visited, services, revision)


def fingerprint(module_dir: str, revision: str) -> str:
    if not re.fullmatch(r"module/[A-Za-z0-9_./-]+", module_dir) or ".." in module_dir.split("/"):
        raise ValueError("Invalid module path")
    shared = {line.strip() for line in INPUTS.read_text(encoding="utf-8").splitlines()
              if line.strip() and not line.strip().startswith("#")}
    dependencies = set()
    dependent_paths(module_dir, dependencies, set(), index_services(revision), revision)
    for dependency in dependencies:
        if git_object(revision, dependency) == "MISSING":
            raise ValueError(f"Local Run V2 module is not committed at {revision}: {dependency}")
    shared.update(dependencies)
    content = HEADER + "".join(f"{path}\t{git_object(revision, path)}\n" for path in sorted(shared))
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--module-dir", required=True)
    parser.add_argument("--revision", default="HEAD")
    args = parser.parse_args()
    print(fingerprint(args.module_dir, args.revision))
