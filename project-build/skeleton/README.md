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
| `learning-contract/schemas/quiz.schema.json` | `quiz/{vi,en}/question.yml` |
| `learning-contract/schemas/interview.schema.json` | `interview/{vi,en}/question.yml` |
| `learning-contract/schemas/roadmap.schema.json` | `roadmap/{vi,en}/roadmap.yml` |
| `learning-contract/schemas/reference.schema.json` | `reference/references.yml` (shared) |

The generator derives field descriptions, required flags, defaults, examples,
and Roadmap guidance from the schema itself. Every file contains a parseable
empty list as its active YAML payload; commented examples may contain draft
placeholders that must be completed before publication.

Files are updated **only when the contract-derived output differs**. Manually
created files without the generated header are not overwritten. An uninitialized
contract submodule or an incompatible schema causes an explicit failure.

Reference uses its English JSON Schema descriptions because localized authoring
annotations have not yet been defined.

**Not generated here:** Knowledge, Knowledge Search,
Video Index/Script (JSON projections, not the source Markdown),
API Docs, Module Catalog, Manifest, or `common.schema.json`.

**Scope:** This plugin creates centralized samples only. The existing module
Quiz/Interview/Roadmap skeleton generators still consume their legacy YAML
schema resources. Switching those module generators to these samples is a
separate, later migration; the plugin does not touch any module contents.
