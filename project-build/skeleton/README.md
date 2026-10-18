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

**Scope:** This plugin creates centralized samples only, and never edits module
contents. Module Quiz and Interview generators and validators use the **same
pinned Quiz/Interview/common JSON Schemas**, bundled into `gradle-runtime` at
build time. Their legacy `question-schema.yml` resources were removed. The
module-local `content/{quiz,interview}/{language}/question.yml` filenames and
BUILD feature flags remain unchanged. On Gradle Sync, existing generated
question comments are kept when their field descriptors and version are
equivalent; cosmetic description/order changes alone do not rewrite authored
module files. Required-field synchronization and structural schema changes
still apply. Roadmap still uses its legacy authoring
schema resource; converting that generator is a separate migration.

For backwards compatibility, module question validators still accept a
file-only Knowledge relation (`readmeRelated.file` set and `anchor` empty), as
emitted by the current Interview Sheet importer. The contract's stricter
relation `anyOf` will require a separate content migration before enforcement.
