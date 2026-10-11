# Java Learning Data Publisher

This is the **producer-only** half of the split Learning Platform pipeline.
The `java-learning` repository owns the Gradle generators; the standalone
`learning-platform` repository owns the React app and Cloudflare deployment.

## Current entry point

- GitHub Actions: `.github/workflows/publish-java-data.yml`
- Trigger: **workflow_dispatch only**, from `main`
- Generator: `./gradlew generatePortalData --no-daemon --console=plain` using JDK 21
- Output: `build/learning-data/java/` (ignored by Git)
- Validation and archive: `scripts/publish-java-data.py` (Python 3 + PyYAML 6.0.2)
- Regression tests: `scripts/tests/test_java_data_publisher.py`
- Publication: one **rolling** GitHub Release with fixed tag `java-data`

The workflow uses `GITHUB_TOKEN` with `contents: write`. It must not replace
the independent rolling GitHub Release tagged `local-run`.

## How to test

1. Commit and push the publisher files to `main`.
2. Open **Actions → Publish Java Learning Data → Run workflow**; choose `main`.
3. Wait for the Gradle task, validation, packaging, and release steps to pass.
4. Open **Releases**, find the fixed `java-data` Release, and download
   `java-learning-data.zip` and `java-learning-data.zip.sha256`.
5. Verify the checksum and inspect `manifest.json` and `module-catalog.json`.
6. Rerunning with identical files does not re-upload anything. A new commit
   replaces both assets (`java-learning-data.zip` first, then its checksum).
   It also updates the Release notes with the new commit and ZIP SHA-256.

The ZIP contains `manifest.json`, `module-catalog.json`, `module/**`, and
`search/**` at the archive root; it contains **no Gradle cache, Java build
output, backend secrets, Spring Boot application or React frontend**.

The manifest includes `schemaVersion`, `producer`, `sourceCommit`,
`sourceCommitTime`, `fileCount`, `moduleCount`, `referenceCount`,
`totalBytes`, and `catalogSha256`. The `.sha256` asset provides an
integrity check for the entire ZIP. Using the commit timestamp plus stable ZIP
entry metadata means the same committed sources can produce the same package.

The `java-data` tag is created once and **is not force-moved** with each
publication. The real source revision is `manifest.json:sourceCommit` inside
the ZIP; the rolling Release notes repeat that SHA for convenient inspection.
Workflow-level concurrency is shared by all Java data publish runs, and a
stale run is rejected if its source commit is no longer the HEAD of `main`.
The existing `local-run` Release remains independent.

Replacing two GitHub Release assets is not atomic. A consumer must download
the ZIP plus `.sha256` and retry after a checksum mismatch during replacement;
it must never deploy a mismatched package. The rolling Release does not retain
old packages for rollback; archive historical packages elsewhere if needed.

## Manual validation from a generated output

```bash
python -m pip install 'PyYAML==6.0.2'
python -m unittest discover -s scripts/tests -p 'test_java_data_publisher.py' -v
./gradlew generatePortalData
python scripts/publish-java-data.py validate
python scripts/publish-java-data.py package \
  --source-commit "$(git rev-parse HEAD)" \
  --source-commit-time "$(git show -s --format=%cI HEAD)"
cd build/java-data-release && sha256sum -c java-learning-data.zip.sha256
```

Validation fails on missing catalog/search data, invalid JSON/YAML, symlinks,
empty generated files, **any file whose extension is not `.json`, `.yml`,
`.yaml`, or `.md`** (including `.jar`, `.class`, `.gradle`, and extensionless
files), duplicate case-insensitive output paths, unsafe paths, duplicate module
route IDs, missing catalog capability paths, and missing lazy-loaded
Knowledge/Video assets. File extensions are checked before packaging, so an
unexpected file blocks Release publication rather than being silently skipped.
This is an **export/integrity validation**,
not a substitute for each module generator's full learning-content schema
validation.

## Not yet enabled

Automatic `push`/merge triggers, Portal data download, and Cloudflare Deploy
Hooks are **out of scope** until publisher and consumer tests are complete.
Do not enable any of them just to test this publisher workflow.
