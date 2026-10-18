# Contract-generated authoring YAML skeletons

This directory is generated from the **pinned** `learning-contract` Git submodule.
It is maintained by `ContractSkeletonSetupPlugin` from `gradle-runtime`,
installed by the root `RootOrchestrationPlugin`.

On **every root Gradle configuration / IDE Gradle Sync**, the plugin converts
only the explicitly supported *authored YAML* document contracts into local
samples. There is **no Gradle task to invoke**, no module discovery, and no
`BUILD_QUIZ` / `BUILD_INTERVIEW` / `BUILD_ROADMAP` condition.

| Input JSON Schema | Generated outputs |
| --- | --- |
| `learning-contract/schemas/quiz.schema.json` | `quiz/quiz.yml` |
| `learning-contract/schemas/interview.schema.json` | `interview/interview.yml` |
| `learning-contract/schemas/roadmap.schema.json` | `roadmap/roadmap.yml` |
| `learning-contract/schemas/reference.schema.json` | `reference/references.yml` (shared) |

**Video authoring example (not contract-generated):**

- `video/video-script.yml` — English authoring skeleton for Video scripts.

This example is deliberately outside the plugin allowlist: its authored
YAML format is validated by the Video importer/projector, while the existing
`video-script.schema.json` describes *published JSON*, not the YAML source.

Every sample uses **English** descriptions and guidance from the JSON Schema;
there are no `vi/` or `en/` output directories. The generator derives
field descriptions, required flags, defaults, examples, and Roadmap guidance
from the schema itself. Every file contains a parseable
empty list as its active YAML payload; commented examples may contain draft
placeholders that must be completed before publication.

Files are updated **only when the contract-derived output differs**. Manually
created files without the generated header are not overwritten. An uninitialized
contract submodule or an incompatible schema causes an explicit failure.
The plugin also removes the old generated `quiz/{vi,en}/`,
`interview/{vi,en}/`, and `roadmap/{vi,en}/` output directories on sync,
as well as legacy `quiz/question.yml` and `interview/question.yml`,
but refuses to delete non-generated files.

Reference uses its English JSON Schema descriptions because localized authoring
annotations have not yet been defined.

**Not generated here:** Knowledge, Knowledge Search,
Video Index/Script (published JSON projections, not the source YAML),
API Docs, Module Catalog, Manifest, or `common.schema.json`.

**Scope:** This plugin creates centralized samples only and never edits module
contents. Quiz and Interview validators use the **same pinned
Quiz/Interview/common JSON Schemas**, bundled into `gradle-runtime` at build
time. Their legacy `question-schema.yml` resources were removed.

When `BUILD_QUIZ`, `BUILD_INTERVIEW`, or `BUILD_ROADMAP` is enabled, Gradle
setup only ensures `content/quiz/`, `content/interview/`, or `content/roadmap/`
exists. It does **not** create language folders, generate module `question.yml`
or `roadmap.yml` skeletons, synchronize comments, or insert default fields in
existing authored files. Sheet import/content authoring owns
`content/{quiz,interview}/{language}/question.yml` and
`content/roadmap/{language}/roadmap.yml`; Portal projection validates existing
files and skips languages without one. BUILD flags and output filenames are
unchanged. Roadmap importer comments (including localized thinking guidance)
now come from `learning-contract/schemas/roadmap.schema.json` (`x-guidance` and
`x-description`). The old `roadmap/roadmap-schema.yml` resource was removed.

For backwards compatibility, module question validators still accept a
file-only Knowledge relation (`readmeRelated.file` set and `anchor` empty), as
emitted by the current Interview Sheet importer. The contract's stricter
relation `anyOf` will require a separate content migration before enforcement.

**Shared validation:** `utils/schema/ContractSchemaLoaderUtils` reads bundled
JSON Schemas; `utils/schema/validate/ContractSchemaValidateUtils` checks both
Draft 2020-12 schema definitions and authored/published data, and
`SchemaExtensionValidateUtils` enforces the nonstandard `x-uniqueBy` keyword.
Quiz/Interview use the explicit `LEGACY_QUESTION_LINKS` validation mode while
the existing Sheet importer permits file-only Knowledge references; other
documents use `STRICT`. Video uses separate `videoAuthoring` (YAML source)
and `videoScript`/`videoIndex` (Portal JSON) contracts. Source-specific video
timeline and chapter-order checks remain in `VideoYamlContentUtils`; they
cannot be expressed solely through JSON Schema.
