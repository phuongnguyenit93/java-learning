# GENERAL_AGENT_RULES.md

## 1. Purpose

This file defines the standard workflow for AI agents that build, complete, or review the **learning content of one module** in `java-learning`.

It is intentionally different from the repository-wide documentation:

```text
AGENTS.md
→ how an AI agent must work safely inside the repository

ARCHITECTURE.md
→ why the repository/build/runtime boundaries exist

GENERAL_AGENT_RULES.md
→ orchestrates the canonical module-generation steps

STEP_1_CURRICULUM_RULES.md
→ area/module scope, ownership, boundary and Step 1 handoff

STEP_2_ROADMAP_REFERENCE.md
→ canonical roadmap architecture, contract, workflow and approval gate

STEP_3_MENU.md
→ Menu + title skeleton/review for both new and existing modules

STEP_4_KNOWLEDGE.md
→ Knowledge authoring/refactor against the approved Step 3 Menu

STEP_5_API.md / STEP_6_VIDEO.md / STEP_7_QUIZ.md / STEP_8_INTERVIEW.md
→ downstream learning surfaces

STEP_9_VALIDATION.md
→ integrated validation + Coverage Review

STEP_10_COMMIT.md
→ final commit / push / Merge Request workflow
```

Read `../AGENTS.md`, `../ARCHITECTURE.md` and this orchestrator first. Then read the canonical `STEP_X_*.md` file for the step actually being executed, together with any upstream output that step explicitly depends on. Do not load unrelated step rules as competing authorities.

In this document, **build a module** means building its learning content and learning relationships. It does **not** mean changing the Gradle build architecture unless the task explicitly requires that.

The target learning surfaces are:

```text
Curriculum / scope
Roadmap
Menu
Knowledge
API Docs / guided API experiments when applicable
Video Script / presentation plan
Quiz
Interview
Integrated validation / Coverage Review
```

The Portal is only a consumer/presentation layer. Do not write module learning content directly into `project-portal` generated data.

---

## 2. Core learning model

The module should read as one curriculum, not as several unrelated features.

Preferred relationship:

```text
Module topic
    ↓
Roadmap Skeleton
    ↓
Module Roadmap
    ↓
Roadmap Review / approval
    ↓
README / Knowledge Menu structure
    ↓
Knowledge curriculum
    ↓
API experiments when the module exposes learning APIs
    ↓
Video presentation grounded in Knowledge + available evidence
    ↓
Quiz reinforcement
    ↓
Interview / explanation practice
    ↓
Coverage Review
```

The content surfaces have different roles:

```text
Roadmap
→ canonical learning milestones, ordering, dependencies and module-level mental journey

Knowledge
→ canonical explanation of the concepts

API Docs
→ executable/demonstrable experiments that prove or illustrate Knowledge concepts

Video
→ one presentation artifact per Knowledge Menu; turns finalized Knowledge/evidence into scenes, visuals, narration and transitions

Quiz
→ checks recognition, reasoning, misconceptions, behavior and practical understanding

Interview
→ checks explanation, comparison, trade-offs, pitfalls, mental models and production reasoning

Coverage Review
→ checks whether the whole module forms a coherent curriculum without artificial filler
```

Do not optimize for raw counts. A smaller coherent module is better than a large module filled with duplicate or weak material.

### Step 1 / Step 2 navigation

Area Curriculum/scope rules live in [`STEP_1_CURRICULUM_RULES.md`](./STEP_1_CURRICULUM_RULES.md). ROADMAP + module-level Reference design/review rules live in [`STEP_2_ROADMAP_REFERENCE.md`](./STEP_2_ROADMAP_REFERENCE.md). This orchestrator does not duplicate either contract.

This file only consumes the approved ROADMAP as the upstream source for Menu, Knowledge, API Docs, Video, Quiz and Interview. If downstream work reveals a missing module-level prerequisite or milestone, report a **ROADMAP GAP** and return to Step 2 instead of silently changing the learning journey here.

This module-level playbook consumes these Step 1 decisions:

```text
area scope
→ canonical module inventory
→ primary concept ownership
→ module boundaries
→ inter-module dependencies
→ recommended area learning path / waves
```

Do not redefine those decisions locally while authoring one module. If module work reveals a missing module, wrong primary owner, unclear boundary, broken inter-module dependency or another area-level inconsistency, report a **CURRICULUM GAP** and return to Step 1 Curriculum review.

Once the area-level boundary is stable, continue through the canonical numbered step files.

---
## 3. Canonical module-owned sources

Agents must work on canonical module source, not generated Portal projections.

Typical learning sources are:

```text
Module Reference
module/.../reference/references.yml

Knowledge
module/.../readme/{lang}/menu/**/*.md
module/.../src/main/resources/readme/{lang}/knowledge-metadata.yml

API Docs
module/.../src/main/resources/swagger/{lang}/controller-description.yml
module/.../src/main/resources/swagger/{lang}/api-descriptions.yml
module/.../src/main/resources/swagger/{lang}/api-execution.yml
module/.../src/main/resources/swagger/{lang}/api-params.yml

Video
module/.../video/{lang}/menu/**/*.md

Quiz
module/.../src/main/resources/quiz/{lang}/question.yml

Interview
module/.../src/main/resources/interview/{lang}/question.yml
```

Actual paths and enabled capabilities must be confirmed from the module and current repository configuration before editing.

`MODULE_LANGUAGE` is the language source of truth for localized learning artifacts. Do not invent a separate feature-specific language list.

Module-level Reference is the intentional exception because it is a **shared non-localized catalog**:

```text
module/.../reference/references.yml
→ one shared file for all module languages
→ owned/curated in Step 2
```

Section-level references are not a second Reference subsystem. They remain ordinary localized Markdown inside Step 4 Knowledge sections.

Generated or mixed-ownership fields must follow the preservation rules from `../AGENTS.md` and `../ARCHITECTURE.md`. Never regenerate human-owned content from scratch merely because an AI agent is rebuilding learning content.

---

## 4. Module generation orchestration

`GENERAL_AGENT_RULES.md` is the **orchestrator**. Detailed authoring rules belong to the canonical step files below; do not duplicate them here.

### Canonical step numbering

```text
Step 1 — Curriculum / scope / ownership / boundary
        ↓
Step 2 — ROADMAP + Module Reference
        ↓
Step 3 — Menu + title only
        ↓
Step 4 — Knowledge
        ↓
Step 5 — API learning documentation when applicable
        ↓
Step 6 — Video Script / Presentation Plan
        ↓
Step 7 — Quiz
        ↓
Step 8 — Interview
        ↓
Step 9 — Integrated validation + Coverage Review
        ↓
Step 10 — Commit / Push / Merge Request
```

The step number and execution order are stable across new and refactor workflows. Existing content may change what must be audited, but it does not remove Step 3 from the canonical sequence.

### New module workflow

```text
Step 1 → STEP_1_CURRICULUM_RULES.md
Step 2 → STEP_2_ROADMAP_REFERENCE.md
Step 3 → STEP_3_MENU.md
Step 4 → STEP_4_KNOWLEDGE.md
Step 5 → STEP_5_API.md
Step 6 → STEP_6_VIDEO.md
Step 7 → STEP_7_QUIZ.md
Step 8 → STEP_8_INTERVIEW.md
Step 9 → STEP_9_VALIDATION.md
Step 10 → STEP_10_COMMIT.md
```

### Existing / refactor module workflow

```text
Step 1 → STEP_1_CURRICULUM_RULES.md
Step 2 → STEP_2_ROADMAP_REFERENCE.md
Step 3 → STEP_3_MENU.md
         audit/refactor existing Menu against Step 1 + Step 2
Step 4 → STEP_4_KNOWLEDGE.md
         refactor Knowledge against the approved Step 3 Menu
Step 5 → STEP_5_API.md
Step 6 → STEP_6_VIDEO.md
         reconcile Video path/H1/H2 mapping against the latest Knowledge before authoring/refactoring script
Step 7 → STEP_7_QUIZ.md
Step 8 → STEP_8_INTERVIEW.md
Step 9 → STEP_9_VALIDATION.md
Step 10 → STEP_10_COMMIT.md
```

Step 3 is **mandatory for both new and existing modules**.

The difference is only the evidence being inspected:

```text
NEW MODULE
→ derive Menu/chapter/title skeleton from approved Curriculum + ROADMAP

EXISTING / REFACTOR MODULE
→ derive the same target Menu/chapter/title skeleton from approved Curriculum + ROADMAP
→ additionally inspect the old Menu/content structure as migration evidence
→ preserve/reuse compatible structure where appropriate
→ move/reorder/rename/split/merge structural items when needed
```

Existing Menu structure is never a reason to skip Step 3 and is never the curriculum authority. Step 3 must explicitly reconcile it with Step 1 and Step 2 before Step 4 authors/refactors full Knowledge.

### Mandatory Curriculum Map context for Steps 2, 3, 4 and 5

Steps 2, 3, 4 and 5 must load and understand the relevant Step 1 Curriculum Map from:

```text
module-generate-agent/temp/*_CURRICULUM_MAP.md
```

The target module path determines which area Curriculum Map is relevant. The agent must not select a Curriculum Map only because its filename looks similar.

For each of Steps 2, 3, 4 and 5:

```text
GENERAL_AGENT_RULES.md
        +
target module/repository context
        +
relevant *_CURRICULUM_MAP.md from module-generate-agent/temp
        +
the requested STEP_X rule file
        +
required upstream output from earlier steps
```

must be present in working context before editing.

The Curriculum Map supplies:

```text
area scope
module responsibility
primary ownership
neighboring-module boundaries
inter-module dependencies
recommended area learning order
cross-module terminology ownership
known duplication/gap risks
```

If the required Curriculum Map is missing, stale relative to an explicitly updated Step 1, or ambiguous for the target module, do not silently infer the missing Step 1 decisions. Report the prerequisite as a **CURRICULUM CONTEXT GAP** and resolve Step 1 context first.

### Execute only the requested step

When the user provides a module + step, perform **only that step**. A later step may inspect upstream outputs, but it must not silently execute or redesign another step.

### Mandatory authoritative-source rule

Before every step that inspects, designs, authors, refactors or validates learning content, consult authoritative documentation for the relevant technology.

```text
Java / Java Core
→ Oracle Java documentation / Java API documentation
→ Java Language Specification when language semantics are involved
→ OpenJDK / JEP when version-sensitive behavior matters

Spring
→ spring.io / official Spring Reference Documentation

Other technologies
→ official specification / official vendor or project documentation
```

Repository implementation is important evidence, but it is not the only curriculum authority.

### Reference ownership rule

Keep the two Reference scopes separate:

```text
MODULE-LEVEL REFERENCE
→ Step 2 ownership
→ module/.../reference/references.yml
→ shared across VI / EN
→ curated learner-facing bibliography
→ not a source of learning order

SECTION-LEVEL REFERENCE
→ Step 4 ownership
→ authored directly inside Knowledge Markdown
→ localized naturally with that Knowledge file
→ use H3 such as "### References" / "### Tài liệu tham khảo"
→ do not create a new H2 only for references
```

External research may use more sources than are ultimately published. Do not automatically dump browsing/research history into Module Reference or every Knowledge section.

### Refactor preservation rule

```text
MOVE
+ REORGANIZE
+ SPLIT / MERGE
+ RENAME when needed
+ ADD missing curriculum content

DO NOT silently DELETE existing learning knowledge
```

Incorrect, stale or out-of-scope content must be surfaced as an explicit review finding rather than silently discarded.

### Step routing

```text
Step 1 → STEP_1_CURRICULUM_RULES.md
Step 2 → STEP_2_ROADMAP_REFERENCE.md
Step 3 → STEP_3_MENU.md
Step 4 → STEP_4_KNOWLEDGE.md
Step 5 → STEP_5_API.md
Step 6 → STEP_6_VIDEO.md
Step 7 → STEP_7_QUIZ.md
Step 8 → STEP_8_INTERVIEW.md
Step 9 → STEP_9_VALIDATION.md
Step 10 → STEP_10_COMMIT.md
```

### Gap routing

```text
CURRICULUM GAP
→ area/module inventory, ownership, boundary or inter-module dependency problem
→ return to Step 1

ROADMAP GAP
→ missing/wrong milestone, prerequisite or module-internal learning order
→ return to Step 2

Menu / Knowledge issue
→ structural Menu/chapter/title/path issue: resolve in Step 3 for both new and existing modules
→ full explanation/body/pedagogy issue inside approved structure: resolve in Step 4
```

---

## 5. Multi-agent workflow without losing one unified learning flow

Large modules may be split across workers to reduce context size and workload, but workers must not independently invent separate curricula.

The dependency chain remains:

```text
Prime / Lead
    ↓
Step 1 scope / Curriculum + approved Step 2 ROADMAP
    ↓
Menu worker for both new and existing modules
    ↓ stable chapter/title skeleton
Knowledge worker(s)
    ↓ approved/stable Knowledge structure
API worker(s), when applicable
    ↓ exact Knowledge relations established
Video worker(s)
    ↓ one Knowledge Menu → one presentation artifact
Quiz worker(s) + Interview worker(s)
    ↓
Validation worker
    ↓
Independent Coverage Review
    ↓
Prime integration
    ↓
Step 10 final delivery
```

### Recommended responsibilities

```text
Prime / Lead agent
→ coordinates the canonical step outputs
→ consumes Step 1 scope/ownership instead of redefining it
→ consumes the approved Step 2 ROADMAP instead of inventing another learning order
→ resolves conflicts between workers
→ ensures all phases use the same learning model

Menu worker
→ executes Step 3 Menu/title scaffolding
→ for existing modules, audits and restructures the old Menu against Step 1 + Step 2
→ consults authoritative technical sources to verify concept grouping and terminology
→ does not write full Knowledge

Knowledge worker
→ executes Step 4
→ writes/reviews README Knowledge + metadata
→ consumes the approved Step 3 Menu for both new and existing modules
→ migrates/refactors existing Knowledge into that approved structure when needed
→ establishes stable file/anchor identities
→ preserves the agreed three-layer flow and chapter-to-chapter narrative
→ adds optional section-level References directly in Markdown when they materially help the learner
→ keeps those References inside the owning H2 using H3/local content; does not invent a separate section-reference schema

API worker
→ starts only after Knowledge structure is sufficiently stable
→ decides API applicability from Curriculum/Roadmap/Menu/Knowledge first
→ treats current MODULE_TYPE/BUILD_SWAGGER/controllers as implementation evidence, never curriculum authority
→ reconciles module config/runtime capability when the approved learning design requires or rejects an API surface
→ then inspects/creates/refactors actual controllers/methods/source
→ maps learning APIs to exact Knowledge
→ writes API descriptions/execution/evidence

Video worker
→ executes Step 6 after Knowledge and applicable Step 5 evidence are stable
→ preserves the one-Knowledge-Menu → one-Video mapping
→ reconciles Video path/H1/H2 structure against the latest Knowledge before authoring
→ turns Knowledge/evidence into spoken narration, visuals, transitions and scenes
→ never treats Video as an alternate technical source of truth

Quiz worker
→ executes Step 7
→ consumes Knowledge + finalized API mapping; may inspect Video for presentation context but does not derive technical truth from it
→ creates non-duplicative assessment

Interview worker
→ executes Step 8
→ consumes the same Knowledge + API map; Video may provide presentation context but not replace canonical Knowledge
→ creates explanation/reasoning practice, not Quiz clones

Validation worker
→ checks schemas, relations, parity, generators and diffs

Coverage reviewer
→ reviews the integrated module from curriculum perspective
→ should preferably be independent from the workers that authored the content
```

### What may run in parallel

After a dependency is stable, bounded work may run in parallel.

Examples:

```text
VI and EN wording work
→ may run in parallel after the canonical topic/question structure is fixed

Quiz and Interview authoring
→ may run in parallel only when Step 6 Video is already complete or the Prime explicitly treats the two authoring tasks as bounded work after the canonical Step 6 handoff

VI and EN Video narration
→ may run in parallel after Knowledge H1/H2 mapping and presentation plan are fixed

multiple chapter workers
→ may run in parallel only when the Prime has already fixed chapter ownership,
  terminology, learning narrative, cross-chapter boundaries and required transitions
```

### What must not run independently

Avoid this pattern:

```text
Worker A invents Knowledge curriculum
Worker B independently invents API curriculum
Worker C independently invents Video curriculum
Worker D independently invents Quiz curriculum
Worker E independently invents Interview curriculum
```

Even if every file is individually valid, the result will usually be inconsistent.

Instead use handoff artifacts between phases:

```text
Step 1 output
→ module boundary / capability inventory / neighboring ownership

Step 2 output
→ approved milestone order + prerequisites + module mental journey
→ curated shared module-level `reference/references.yml` when useful sources exist

Step 3 output — both new and existing modules
→ stable Menu/chapter/title skeleton

Step 4 output
→ canonical chapter/topic map
→ major terminology + concept relationships
→ chapter-order rationale / important transitions
→ running-example plan when useful
→ exact Knowledge files + anchors when full Knowledge is authored
→ optional local section References embedded directly in the relevant Knowledge Markdown

Step 5 output
→ controller/method ↔ Knowledge mapping + API learning intent

Step 6 output
→ one Video source per target Knowledge Menu
→ Knowledge H2 → Video section mapping
→ scene/transition presentation plan grounded in Knowledge + available evidence

Step 7 output
→ Quiz tied to the established curriculum

Step 8 output
→ Interview tied to the established curriculum

Step 9 output
→ validated integrated module
→ prioritized gaps/fixes, not a competing curriculum

Step 10 output
→ task-scoped commit on the current module branch
→ module branch pushed to remote
→ Merge Request created from the module branch into `main`
→ no automatic merge unless the user explicitly requests/authorizes it
→ after the Merge Request is actually merged, return the session to the original main worktree
```

Workers must inspect the latest upstream files before editing. Do not rely only on a stale task description if an upstream worker has changed the canonical Knowledge/API/Video structure.

---

## 6. Cross-step quality rules

### 6.1 Do not change concepts just to make relations convenient

Never rewrite curriculum semantics merely to make a relation easier to fill.

```text
No exact relation
→ blank relation is acceptable

Real missing curriculum concept
→ add/fix Knowledge because the concept belongs there

Artificial missing relation
→ do not manufacture Knowledge/API content
```

### 6.2 Do not create content solely to increase counts

Avoid:

```text
duplicate Quiz questions
near-identical Interview questions
trivial APIs with no learning value
tiny Knowledge sections that exist only as relation targets
fake relation mappings
```

### 6.3 Preserve exact technical meaning across languages

Localized wording may differ naturally, but these must remain equivalent:

```text
concept
behavior
correct Quiz answer
question intent
API relationship
Knowledge relationship
technical conclusion
```

Natural localization is preferred over literal translation. In particular, VI should read as Vietnamese technical writing, not English prose with Vietnamese connectors. EN should read as natural English. Both languages must preserve the same technical meaning, learning intent, anchor identity, and downstream relationships even when sentence structure and visible pedagogical headings differ.

### 6.4 Source code is evidence, not the curriculum itself

The learning content must remain understandable without forcing the learner to reverse-engineer Java source first.

Use source to prove/explain behavior, especially in API execution documentation.

### 6.5 Respect module boundaries

Do not expand one module into a generic textbook covering adjacent technologies unless the extra material is necessary to understand the module itself.

Cross-topic context is useful; curriculum scope creep is not.

### 6.6 Do not write an encyclopedia of isolated definitions

Avoid authoring a module as:

```text
chapter A → definition
chapter B → definition
chapter C → definition
```

even when every definition is technically correct.

The learner must be able to understand the causal/learning relationship:

```text
problem
→ concept
→ why it helps
→ relation to the domain
→ Java mechanism
→ evidence/code
```

Definitions are reference material. A curriculum needs a learning path around them.

### 6.7 Do not confuse coverage with pedagogy

These can all be true:

```text
all H2 anchors exist
all metadata is valid
all API relations resolve
Quiz/Interview coverage is high
build/projection passes
```

while the module is still pedagogically incomplete.

Treat the following as separate review dimensions:

```text
coverage correctness
→ did we include the important knowledge?

technical correctness
→ are explanations/code/runtime claims accurate?

pedagogical coherence
→ can a learner understand what, why, relation and learning order?
```

Do not declare a module complete from counts alone.

---
