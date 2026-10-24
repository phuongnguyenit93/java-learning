# Code Explorer Material V1 (code-only, no AI)

`java-learning` builds a portable static source bundle for a read-only
`learning-platform` Monaco viewer. No backend runs Java, no Spring context,
and no LLM or worker is involved in generating the material.

## Authored configuration

An individual Gradle module opts in using `content/code-explorer/material.yml`:

```yaml
version: 1
type: CODE_EXPLORER
id: oop-abstraction
language: java
sourceRoot: src/main/java
include:
  - com/example/learning/oop/abstraction/**/*.java
apiMapping:
  source: SWAGGER
```

Include globs are a publish allowlist; only Java source in `src/main/java`
can be selected. Do not include credentials, proprietary source or secrets.
The generator rejects symlinks, path traversal, malformed Java/YAML,
non-UTF-8 text and oversized files.

## Module-local Gradle tasks

Run from the root of `java-learning`:

```powershell
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:generateModuleCodeExplorer -PcodeExplorerMode=DRY_RUN
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:generateModuleCodeExplorer -PcodeExplorerMode=APPLY
.\gradlew.bat :module:platform:development:programming:paradigm:object-oriented:publishModuleCodeExplorer -PcodeExplorerMode=APPLY
```

- Generate writes to `<module>/build/code-explorer/`.
- Publish also writes to
  `<repo>/build/learning-data/java/module/<SERVICE_NAME>/code-explorer/`.
- DRY_RUN performs all parse/index work **without writing**.
- No Java application execution, no Sheets, no commit or push.
- Repeated builds with unchanged input produce the same JSON and Java bytes.

The standard Java data release pipeline now runs
`./gradlew generatePortalData`, which includes `generatePortalCodeExplorer`
for **every opted-in module** (the same allowlist/symbol engine).
`scripts/publish-java-data.py` validates the generated Java files against the
manifest's SHA-256 values before packaging them in `java-learning-data.zip`.
Only `module/<ROUTE_ID>/code-explorer/files/**/*.java` is authorized:
arbitrary Java source elsewhere in the dataset remains forbidden.
The downstream `learning-platform` release consumer verifies these checksums
again before syncing the bundle.

## Bundle contract

| Asset | Purpose |
| --- | --- |
| `index.json` | Version, material ID, source file paths, SHA-256 hashes and index paths |
| `symbols.json` | Type and method declarations with ID, source path, line and column |
| `references.json` | Direct lexical calls and type uses with status and target IDs |
| `api-map.json` | Swagger HTTP verb + path + method signature → source method |
| `files/**/*.java` | Original allowlisted source, unchanged and read-only |

The producer catalog declares
`/module/<SERVICE_NAME>/code-explorer/index.json`. The frontend maps this
through the existing Java asset prefix and requests
`/language/java/module/<SERVICE_NAME>/code-explorer/index.json`, loading the other JSONs
on demand and lazily fetch individual `files/<path>` from the bundle.
All source line and column positions are **1-based** for Monaco.

Navigation statuses: `RESOLVED` (one target), `MULTIPLE_TARGETS` (multiple
equally plausible targets; show a picker), `UNRESOLVED` (no clickable target).
The frontend must not guess a target for non-resolved references.

V1 handles only lexical class/method references where the receiver type
and overload are unambiguous. Bean injection, interface implementation
dispatch, reflection, external jars and full Language Server semantics
are deliberately deferred. Swagger mapping only includes API methods
whose Controller Java file is in this material's allowlist.

This is only the material generator and a portable consumer contract;
`learning-platform` Monaco UI implementation is a separate task.
