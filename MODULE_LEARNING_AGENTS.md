# MODULE_LEARNING_AGENTS.md

## 1. Purpose

This file defines the standard workflow for AI agents that build, complete, or review the **learning content of one module** in `java-learning`.

It is intentionally different from the repository-wide documentation:

```text
AGENTS.md
→ how an AI agent must work safely inside the repository

ARCHITECTURE.md
→ why the repository/build/runtime boundaries exist

MODULE_LEARNING_AGENTS.md
→ how an AI agent builds one module into a coherent learning experience
```

Read `AGENTS.md` and `ARCHITECTURE.md` before using this playbook. Their ownership, lifecycle, source-of-truth, localization, and preservation rules remain authoritative.

In this document, **build a module** means building its learning content and learning relationships. It does **not** mean changing the Gradle build architecture unless the task explicitly requires that.

The target learning surfaces are:

```text
Knowledge
API Docs / guided API experiments when applicable
Quiz
Interview
Coverage Review
```

The Portal is only a consumer/presentation layer. Do not write module learning content directly into `project-portal` generated data.

---

## 2. Core learning model

The module should read as one curriculum, not as several unrelated features.

Preferred relationship:

```text
Module topic
    ↓
Knowledge curriculum
    ↓
API experiments when the module exposes learning APIs
    ↓
Quiz reinforcement
    ↓
Interview / explanation practice
    ↓
Coverage Review
```

The content surfaces have different roles:

```text
Knowledge
→ canonical explanation of the concepts

API Docs
→ executable/demonstrable experiments that prove or illustrate Knowledge concepts

Quiz
→ checks recognition, reasoning, misconceptions, behavior and practical understanding

Interview
→ checks explanation, comparison, trade-offs, pitfalls, mental models and production reasoning

Coverage Review
→ checks whether the whole module forms a coherent curriculum without artificial filler
```

Do not optimize for raw counts. A smaller coherent module is better than a large module filled with duplicate or weak material.

### Pedagogical coherence is a first-class requirement

Technical coverage is not the same thing as a good curriculum.

A module can be technically correct and still be a poor learning experience when it only presents a catalog of definitions:

```text
term A
→ definition

term B
→ definition

term C
→ definition
```

That structure may produce valid Knowledge anchors, API relations, Quiz questions and Interview questions while still leaving the learner asking:

```text
What is this module actually about?
Why do these concepts belong together?
What problem caused this concept to exist?
How does this term connect to the previous one?
Where does Java code enter the picture?
```

Therefore **coverage correctness and pedagogical correctness are separate acceptance dimensions**.

The preferred learning chain is:

```text
WHAT
→ what is this domain/concept?

WHY
→ what problem or limitation makes it useful?

RELATION
→ how does it connect to the other concepts in this module?

HOW
→ how does Java represent or implement the idea?

EVIDENCE
→ what code/runtime behavior makes the idea observable?

PRACTICE
→ how do Quiz / Interview / API experiments reinforce it?
```

Do not treat a complete H2 inventory as proof that the module teaches well.

### Mandatory three-layer curriculum model

Every mature learning module should be understandable at **three nested layers**.

#### Layer 1 — Module mental model / roadmap

The learner needs an orientation layer before deep terminology.

At minimum, the first chapter or equivalent entry chapter must explain in beginner-friendly language:

```text
What is this thing?
Why does this domain/topic exist?
What broad problem does it help solve?
What are the major terms the learner will encounter?
How do those terms relate to each other?
What order should they be learned in?
What should the learner understand by the end of the module?
```

The chapter does not have to be literally named `MentalModel`, but it must perform this function.

The first chapter should include a **terminology map / roadmap**, for example:

```text
core idea
    ↓
concept A — solves one problem
    ↓
concept B — introduces another capability
    ↓
concept C — explains runtime behavior
    ↓
concept D — alternative / trade-off
```

The goal is that a learner can answer:

```text
"I know what I am about to learn and why these chapters belong in one module."
```

Do not open a module with advanced definitions while the learner still lacks the vocabulary required to understand why the definitions matter.

#### Layer 2 — Concept / chapter story

Each important chapter should explain the concept as part of a problem-solving story, not as an isolated dictionary entry.

Preferred progression:

```text
Where are we in the module roadmap?
        ↓
What problem exists before this concept?
        ↓
Why was this idea introduced / why is it useful?
        ↓
What mental model solves the problem?
        ↓
How does it relate to concepts before and after it?
        ↓
Which Java language feature / type / API represents it?
        ↓
What code demonstrates the idea?
```

For example, do not teach encapsulation as only:

```text
encapsulation = private fields
```

Prefer the reasoning chain:

```text
uncontrolled mutable state
        ↓
invalid object state becomes possible
        ↓
the object needs ownership of its invariants
        ↓
encapsulation creates that boundary
        ↓
Java uses access control + behavior-oriented methods to express it
```

This layer is where chapter-to-chapter transitions matter. A chapter should make clear why the learner is moving to the next concept.

#### Layer 3 — Technical depth

Only after the learner understands the purpose and relationship should the chapter go deep into mechanics.

Typical technical-depth flow:

```text
syntax / API surface
→ compile-time behavior
→ runtime behavior
→ focused code examples
→ important rules
→ edge cases
→ pitfalls
→ trade-offs
→ debugging / production implications when relevant
```

Not every concept needs every item, but deep rules must be attached to an already-established mental model.

If a learner sees rules such as method hiding, exception narrowing, generic variance, Unicode normalization or class-loading identity before understanding the problem/domain they belong to, the content is too bottom-up.

### Distinguish domain concepts from Java mechanisms

Do not accidentally present every language mechanism as a foundational concept of the module.

For each major term, classify its role internally:

```text
domain / design concept
→ part of the module's main mental model

Java mechanism
→ language/runtime feature used to express or implement the concept

comparison mechanism
→ included mainly to distinguish it from another important concept

boundary concept
→ mentioned only enough to hand off to another module
```

Example for an OOP curriculum:

```text
encapsulation / abstraction / polymorphism
→ OOP design concepts

overriding + dynamic dispatch
→ Java mechanisms that support subtype polymorphism

overloading
→ compile-time Java mechanism taught partly because learners confuse it with overriding
```

The Knowledge text must make that relationship explicit. Do not let file placement imply a false conceptual hierarchy.

### Running examples and code continuity

When practical, reuse one or a small number of simple running examples across related chapters so the learner sees the concept evolve instead of repeatedly resetting context.

Preferred shape:

```text
simple object / problem
    ↓ chapter 1
add a constraint
    ↓ chapter 2
introduce a concept to solve it
    ↓ chapter 3
extend the design
    ↓ chapter 4
observe runtime behavior
```

Do not force one example across unrelated concepts, but prefer continuity when it reduces cognitive load.

Code should answer a learning question. Avoid code snippets that merely restate syntax without demonstrating why the feature matters.

### Module granularity: a module is a learning domain, not one tiny concept

Do not automatically create one real module for every small Java concept, keyword, API, annotation, or language rule.

For learning-authoring purposes, prefer this model:

```text
real learning module
→ one coherent topic/domain large enough to have its own curriculum

README chapter / Knowledge section
→ a smaller concept that belongs inside that curriculum
```

For example, concepts such as:

```text
boxing
casting
pass-by-value
constructor
access modifier
```

are usually better treated as chapters/sections of broader modules such as `language-basics` or `class-object` rather than five independent real modules.

By contrast, a topic such as `generics`, `collection`, `exception`, or `reflection` can justify its own module when it contains several connected concepts, mental models, pitfalls and practical behaviors that form a meaningful curriculum.

A useful heuristic is to draft the topic outline before creating/splitting the module:

```text
too small
→ definition
→ one example
→ one pitfall

likely large enough
→ mental model
→ core mechanics
→ important variants
→ runtime/practical behavior
→ edge cases
→ comparisons
→ pitfalls
→ trade-offs / usage guidance
→ advanced implications when relevant
```

This is a learning-design heuristic, not a physical module-discovery rule. The repository still identifies a physical real module by its local `gradle.properties` according to `AGENTS.md` and `ARCHITECTURE.md`.

There is no mandatory numeric minimum, but as a rough quality signal a mature topic often supports several meaningful Knowledge sections and enough non-duplicative Quiz/Interview material to justify an independent curriculum. Do not create filler merely to reach an arbitrary count.

API presence is **not** a requirement for a topic to deserve its own module:

```text
Knowledge + Quiz + Interview
→ may form a complete learning module without API

API
→ optional practical/experiment layer when the module genuinely exposes useful runnable behavior
```

Do not convert a naturally library/concept-oriented topic into a `SERVLET`/`REACTIVE` application merely to manufacture REST endpoints. Conversely, when a `SERVLET`/`REACTIVE` module does expose meaningful learning APIs, Step 3 requires those APIs to connect back to real Knowledge concepts.

### Cross-area ownership: one primary home per concept

Before adding a topic to a module, inspect neighboring learning areas and choose one primary canonical home. Do not duplicate full curricula merely because a concept is related to several areas.

Typical boundary examples:

```text
java/core
→ stable language/standard-library foundations used broadly across versions

paradigm/functional
→ functional programming mental model, functional interfaces, lambdas, method references, Optional

java/version/java8/stream-api
→ Stream API mechanics and pipeline behavior

java/version/java9/module-system
→ JPMS / module-info / module path / strong encapsulation

java/advance/jvm
→ JVM internals, bytecode, runtime memory, GC, JIT, Java Memory Model

java/advance/networking
→ Java networking APIs such as Socket/ServerSocket, NIO channels/selectors, URI/URL and HttpClient

java/advance/security-cryptography
→ Java security/cryptography APIs such as providers, digests, MAC, cipher, signatures, keys, KeyStore and certificates
```

Related modules may later link to or reference the canonical topic, but they should not independently recreate the same full explanation. This keeps Knowledge ownership clear and makes cross-module relations meaningful instead of duplicative.

Keep these Java API curricula separate from neighboring system/framework areas. For example, `infrastructure/system/network` owns protocol, gateway, proxy, service-mesh and system-level TLS concerns; Spring Security owns framework authentication/authorization/resource-server behavior. Java networking/security modules should teach the JDK/runtime APIs and their semantics, not duplicate those infrastructure/framework curricula.

### Curriculum planning and lock protocol for large learning areas

When the task covers many modules or a large learning domain, do not jump directly from taxonomy to content authoring. Use an explicit curriculum-planning phase first so chapter paths, concept ownership and downstream relations do not churn after Knowledge/API/Quiz/Interview work has started.

Recommended planning flow:

```text
1. Inventory the whole learning area
        ↓
2. Define module boundaries and one primary home per concept
        ↓
3. Establish chapter/H1 skeletons
        ↓
4. Review only for large missing knowledge domains
        ↓
5. Build a detailed curriculum map
        ↓
6. Review the map again for large conceptual gaps
        ↓
7. Define the learning narrative / module roadmap
        ↓
8. Define chapter transitions + running examples where useful
        ↓
9. Lock taxonomy + chapter paths + planned Knowledge identities + pedagogical flow
        ↓
10. Implement Knowledge
        ↓
11. Implement API / Quiz / Interview from the locked curriculum
```

The repeated review in steps 4 and 6 is intentional. The first pass asks whether the module/chapter taxonomy is missing a major area. The second asks whether the detailed concept map is still missing a foundational mental model after individual sections and relations have been proposed.

Steps 7 and 8 answer a different question:

```text
Even if every important concept exists, will a learner understand why the concepts appear in this order and how they connect?
```

Do not lock a large curriculum only because the concept inventory is complete.

#### Large-gap review rule

During curriculum review, distinguish **foundational gaps** from small API/details that can be added later.

Ask:

```text
If this topic is missing, would the learner's mental model of the domain be materially incomplete?
```

Examples of large gaps:

```text
missing type-system model
missing runtime dispatch model
missing object identity/equality contract
missing Unicode/code-point model in a String curriculum
missing overflow/precision model in a numeric curriculum
```

Examples that normally should not block curriculum lock by themselves:

```text
one utility class
one convenience method
one rarely-used annotation
one small formatting helper
one minor JDK API variant
```

Do not endlessly expand a curriculum searching for every API in the JDK. Once no major conceptual gap remains and smaller omissions would not break the learning model, the curriculum may be locked and implementation should proceed.

#### Detailed curriculum map

For a large module set, create a planning artifact before implementation. The artifact may be repository-scoped, for example:

```text
<AREA>_CURRICULUM_MAP.md
```

It should be a planning document, not a generated runtime artifact and not the canonical Knowledge source itself.

For every module, record at least:

```text
existing chapter path
    ↓
planned stable H2 Knowledge identities
    ↓
API applicability
    ↓
meaningful API experiments when useful
    ↓
primary Knowledge anchor for each experiment
    ↓
Quiz coverage themes
    ↓
Interview coverage themes
    ↓
cross-module ownership/boundaries
```

For modules with several related concepts, the planning artifact should also capture the intended **learning narrative** at a high level:

```text
module entry question
→ major terminology map
→ why each major concept exists
→ concept dependency/order
→ important transitions between chapters
→ running example(s), when useful
```

The map should be detailed enough that later workers do not independently reinvent the curriculum, but it should not force artificial one-to-one coverage such as:

```text
1 H2 = 1 API = 1 Quiz = 1 Interview question
```

That relationship is intentionally many-to-many and sometimes absent. Knowledge completeness is primary; APIs and assessments sample or prove the curriculum where they add value.

#### Curriculum lock

A curriculum is considered ready to lock when:

```text
module taxonomy is stable
chapter paths/order are stable
primary concept ownership is clear
major missing domains have been reviewed at least twice
planned H2 identities are coherent and non-duplicative
the module has a beginner-readable entry mental model / roadmap
major terminology is introduced before deep mechanics depend on it
chapter order forms an explainable learning narrative
important chapters answer problem → why → relation → Java mechanism before deep rules
running examples / transitions are planned where they materially improve continuity
API experiments have real observable learning value
Quiz/Interview coverage themes are broad enough to exercise the important concepts
neighboring modules have explicit boundaries so full explanations are not duplicated
```

After lock, treat taxonomy/chapter/anchor changes as migrations rather than casual edits. A genuine correctness issue may still require change, but ordinary implementation should consume the locked curriculum instead of redesigning it.

#### Wave planning for very large scopes

When many modules are involved, group implementation into dependency-aware waves rather than attempting all modules simultaneously.

Example shape:

```text
Wave 1 — language/object foundations
        ↓
Wave 2 — mechanisms built on those foundations
        ↓
Wave 3 — standard-library/domain APIs
```

Waves are learning dependencies, not necessarily Gradle dependencies. A later wave may technically build without an earlier one, but its curriculum can still depend on the earlier mental model.

Before starting a wave, perform one final **large-gap-only review**. Ignore minor enrichment unless it changes the domain's core mental model. Once that review finds no major gap, freeze the wave plan and begin implementation rather than continuing taxonomy exploration indefinitely.

That final review must include both:

```text
conceptual completeness
→ are any foundational ideas missing?

pedagogical coherence
→ can a learner see the roadmap, motivations and relationships between the ideas?
```

---

## 3. Canonical module-owned sources

Agents must work on canonical module source, not generated Portal projections.

Typical learning sources are:

```text
Knowledge
module/.../readme/{lang}/menu/**/*.md
module/.../src/main/resources/readme/{lang}/knowledge-metadata.yml

API Docs
module/.../src/main/resources/swagger/{lang}/controller-description.yml
module/.../src/main/resources/swagger/{lang}/api-descriptions.yml
module/.../src/main/resources/swagger/{lang}/api-execution.yml
module/.../src/main/resources/swagger/{lang}/api-params.yml

Quiz
module/.../src/main/resources/quiz/{lang}/question.yml

Interview
module/.../src/main/resources/interview/{lang}/question.yml
```

Actual paths and enabled capabilities must be confirmed from the module and current repository configuration before editing.

`MODULE_LANGUAGE` is the language source of truth. Do not invent a separate feature-specific language list.

Generated or mixed-ownership fields must follow the preservation rules from `AGENTS.md` and `ARCHITECTURE.md`. Never regenerate human-owned content from scratch merely because an AI agent is rebuilding learning content.

### Bootstrapping a new learning module

For a new concept-oriented learning module, prefer the repository's real lifecycle instead of manually fabricating all support files:

```text
create module directory
    ↓
add local gradle.properties
    ↓
run Gradle/settings synchronization
    ↓
master.json + properties.json are synchronized from canonical schemas
    ↓
set module-owned VALUE fields
    ↓
enable BUILD_README=TRUE
    ↓
run Gradle configuration again
    ↓
README.md + readme/{lang}/BASE.md + LIST.md + menu/ are initialized
    ↓
add human-owned chapter files under readme/{lang}/menu/**
```

Do not hand-create generated registry/structure outputs such as `ModuleListEnum`, `module-structure.txt`, or `STRUCTURE.md`; let the existing generators synchronize them.

For an early curriculum-only phase, it is valid for a module to start as:

```text
MODULE_TYPE=LIBRARY
BUILD_README=TRUE
BUILD_SWAGGER=FALSE
BUILD_QUIZ=FALSE
BUILD_INTERVIEW=FALSE
```

and have no Java source or module-local `build.gradle` yet. Add runtime/source capabilities only when the learning design actually requires them.

#### Current SERVICE_NAME bootstrap caveat

When `SERVICE_NAME` is blank, current settings generation temporarily falls back to the module directory name and validates that value as a generated enum constant (`[A-Za-z_][A-Za-z0-9_]*`). A new directory containing `-`, such as `language-basics`, therefore cannot safely complete its very first metadata synchronization while `SERVICE_NAME` is still blank.

For this current implementation constraint, use an enum-safe temporary directory name for the first sync (for example `language_basics`), set a valid stable `SERVICE_NAME` such as `JAVA_LANGUAGE_BASICS` in the generated `master.json`, then rename the directory to its intended final path and run Gradle again. Do not keep the temporary underscore path as part of the learning taxonomy merely to satisfy this bootstrap detail.

### H1-only chapter scaffolds vs Knowledge sections

It is valid to establish the curriculum outline first with chapter Markdown files containing only their localized H1:

```markdown
# Generics
```

The H1 defines the chapter title/outline, but it is **not yet a Knowledge section** for governance/relation purposes. Canonical Knowledge identity starts only when anchored H2 sections exist:

```markdown
## <a id="type-erasure">Type Erasure</a>
```

Therefore, during an H1-only scaffold phase, do not run `syncMetadataReadme` merely to create empty/useless Knowledge metadata. Add the real anchored H2 sections first, then synchronize `knowledge-metadata.yml`.

The H1-only scaffold phase is also the preferred time to finalize chapter numbering, directory names and Markdown file paths. Before anchored Knowledge sections and cross-surface relations exist, reordering `1.X`, `2.Y`, or renaming a chapter file is still a low-cost curriculum edit. After `readmeRelated`, Quiz/Interview relations, Knowledge metadata or API ordering depend on those paths/anchors, the same rename becomes a reference migration and must update/validate every exact relation. Stabilize the learning path early whenever possible.

---

## 4. Six-step module learning workflow

The six steps below are sequential. A later step may inspect an earlier one, but it must not redefine the earlier step independently.

```text
1. Inspect and define scope
        ↓
2. Build Knowledge
        ↓
3. Build API learning documentation when applicable
        ↓
4. Build Quiz
        ↓
5. Build Interview
        ↓
6. Integrated validation
        ↓
Coverage Review
```

### Step 1 — Inspect and define the module learning scope

Do not start by generating content from the module name alone.

Inspect the actual module first. At minimum, determine:

```text
module path
gradle.properties / real-module identity
master.json
MODULE_TYPE
MODULE_LANGUAGE
enabled learning capabilities
build.gradle when relevant
existing README / Knowledge files
existing Java source relevant to the learning topic
existing controllers/endpoints for SERVLET/REACTIVE modules
existing Swagger metadata
existing Quiz / Interview files
existing tests/examples when they materially demonstrate behavior
```

Build an internal topic inventory before editing:

```text
What is the module actually teaching?
Which concepts exist in source or examples?
Which concepts already have Knowledge content?
Which concepts have executable experiments?
Which important concepts are missing?
Which existing content is duplicate, stale or misleading?
```

Use repository/module implementation as evidence, supplemented by technically authoritative knowledge when explanation requires broader context.

Never use this shortcut:

```text
module name
    ↓
generic AI course generated from memory
```

Preferred model:

```text
module implementation + repository intent + authoritative technical knowledge
                              ↓
                     module curriculum
```

At the end of Step 1, the agent should have a proposed chapter/topic map and know which capabilities are relevant.

For a nontrivial module, Step 1 is incomplete until the agent can also state the learning story in plain language:

```text
This module is about ...
It exists because ...
The major terms are ...
They connect like ...
The learner should read them in this order because ...
```

---

### Step 2 — Build Knowledge first

Knowledge is the canonical curriculum foundation for the module.

Write or improve localized Markdown under the module README learning structure. A Knowledge section must use the repository's stable anchored H2 identity:

```markdown
## <a id="stable-section-id">Section title</a>
```

Section ids must be unique within the module/language and stable enough to serve as references from API, Quiz and Interview metadata.

Knowledge should normally progress from mental model to practical behavior:

```text
problem / motivation
→ beginner mental model
→ terminology + relationship to the module
→ connection to previous/next concept
→ Java mechanism / representation
→ lifecycle or flow
→ examples
→ important rules
→ edge cases
→ pitfalls
→ trade-offs / when to use
→ practical observations
```

Not every section needs all layers. The content should fit the actual topic rather than follow a rigid template mechanically.

However, a mature module must collectively satisfy the three-layer contract:

```text
Layer 1 — module roadmap
Layer 2 — concept/chapter story
Layer 3 — technical depth
```

Do not begin every H2 with a formal definition. Use definitions after enough context exists for the definition to mean something.

For important terminology, explicitly answer **why this concept belongs in this module**. If a concept is present mainly as a comparison mechanism or boundary to another module, say so.

Knowledge governance belongs in:

```text
src/main/resources/readme/{lang}/knowledge-metadata.yml
```

Current governance includes:

```yaml
difficulty: BASIC | INTERMEDIATE | ADVANCED
aiGenerated: true
reviewed: false
```

Preserve existing human-owned values. Use the repository's explicit synchronization task when metadata skeleton synchronization is required; do not silently rewrite metadata during ordinary analysis.

Knowledge is allowed to contain concepts without an API. Pure conceptual material, constraints, comparisons and mental models may be valid Knowledge even when no useful executable endpoint exists.

At the end of Step 2, the module should have a coherent Knowledge path before Quiz/Interview are authored and before API relations are finalized.

"Coherent" here means more than all planned anchors existing. A learner entering at chapter 1 should be able to follow the motivation and chapter transitions without already knowing the module's vocabulary.

---

### Step 3 — Build API learning documentation and connect API to Knowledge

This step is conditional on the module exposing learning APIs.

For current repository conventions:

```text
MODULE_TYPE = SERVLET
or
MODULE_TYPE = REACTIVE
```

means the agent must explicitly inspect controllers/endpoints and determine whether the module contains learning APIs that should participate in Swagger/API Docs.

Do not assume every endpoint is a learning experiment. Infrastructure, health, support, internal, generated or framework-only endpoints must not force artificial curriculum mappings.

#### API ↔ Knowledge invariant

For a learning API, the relationship is:

```text
Knowledge chapter
      ↓
Knowledge anchored section
      ↓
Controller / method experiment
```

Every meaningful learning API in a SERVLET/REACTIVE learning module should illustrate, prove, observe or exercise a real Knowledge concept.

The agent must therefore ask:

```text
What concept does this API teach?
        ↓
Does that concept already exist in Knowledge?
        ├── YES
        │   → map the API to the exact Knowledge file/anchor
        │
        └── NO
            ↓
        Is this concept genuinely part of the module curriculum?
            ├── YES
            │   → treat it as a Knowledge coverage gap
            │   → fix Knowledge first
            │   → then map the API
            │
            └── NO
                → do not manufacture a Knowledge section merely to obtain a mapping
```

Never solve a missing relation by selecting the nearest-sounding README section.

Controller-level relationship belongs in `controller-description.yml` and identifies the chapter/file. Method-level relationship belongs in `api-descriptions.yml` and identifies the exact anchored section, with optional file override only when the method truly belongs to another chapter.

Use exact relationships only:

```text
controller → exact README file
method     → exact methodSignature + exact README anchor
```

Do not auto-map from:

```text
controller name
method name
package name
summary text
semantic similarity
```

If no exact relation exists and the concept should not be added to Knowledge, leave the relation blank rather than inventing one.

#### API description responsibilities

Keep these roles distinct:

```text
summary
→ what topic/API is this?

description
→ what does this experiment demonstrate or teach?

execution
→ how does the experiment unfold, why does it behave that way,
  what evidence proves it, and what should the learner conclude?
```

For nontrivial APIs, `api-execution.yml` should normally cover, when applicable:

```text
Concept
Flow
Meaning
Code evidence
Observation
Conclusion
```

Code evidence must support the learning explanation rather than replace it. Prefer small snippets colocated with the runtime step they prove. Avoid copying whole classes/methods unnecessarily.

Preserve human-owned Swagger metadata and stale historical entries according to repository rules. Method identity is the exact method signature, not method name alone.

At the end of Step 3, API Docs should read as the **practical experiment layer of Knowledge**, not as an unrelated generated Swagger reference.

---

### Step 4 — Build Quiz from the established curriculum

Quiz is authored after Knowledge and API relationships are stable enough to serve as references.

Quiz should test understanding rather than repeat sentences from the README.

Aim for a useful mix such as:

```text
core concept recognition
runtime behavior
cause/effect reasoning
common misconception
comparison
edge case
pitfall
practical choice
API experiment observation when relevant
```

Avoid multiple questions that test the same distinction with superficial wording changes.

Follow the canonical Quiz schema. Current important rules include:

```text
single-choice
exactly four stable internal answers A/B/C/D
correctAnswerId stored separately
every answer has answer + explanation
aiGenerated / reviewed governance
optional exact readmeRelated relation
optional exact apiRelated relation
```

Relations are optional, not quotas.

Use:

```yaml
readmeRelated:
  file: ''
  anchor: ''

apiRelated:
  controller: ''
  methodSignature: ''
```

when there is no exact target.

Do not create a new API solely so a Quiz question can have `apiRelated`. Do not create a meaningless Knowledge section solely so a Quiz question can have `readmeRelated`.

VI/EN variants should preserve conceptual parity and question order unless the task explicitly defines a different localization policy. Natural wording is preferred over literal translation, but the tested concept and correct answer must remain equivalent.

---

### Step 5 — Build Interview as explanation and reasoning practice

Interview is not a prose copy of Quiz.

It should exercise the learner's ability to explain and reason about the module:

```text
mental models
why / when
comparison
trade-offs
failure modes
pitfalls
framework behavior
design implications
production considerations
common interview questions
```

Example distinction:

```text
Quiz
→ Which behavior occurs when X happens?

Interview
→ Explain why X behaves that way, how it differs from Y,
  and when the distinction matters.
```

Follow the canonical Interview schema and preserve human-owned content. Interview items may use the same optional exact `readmeRelated` and `apiRelated` shapes as Quiz.

Do not invent relations for the sake of completeness. If a question spans several concepts or has no single exact API experiment, a blank relation is valid.

VI/EN content should preserve topic/order parity where the module maintains bilingual content.

---

### Step 6 — Integrated validation

After Knowledge, API, Quiz and Interview are authored, validate the module as one integrated unit.

Validation must include both structural correctness and learning coherence.

#### Structural validation

Check applicable items such as:

```text
YAML parses successfully
canonical Quiz/Interview schema passes
README anchors are unique and exact
controller README files exist
API method anchors exist
apiRelated controller + methodSignature exist and are active when required
localized files exist for configured MODULE_LANGUAGE values
human-owned values were preserved
generated/stale ownership rules were respected
repository-specific Gradle generation/projection task passes
git diff contains only intended changes
```

Use the repository's real generator/tasks to validate when possible. Do not rely only on visual inspection of YAML.

#### Learning validation

Ask:

```text
Can a beginner explain what this module is about after the entry chapter?
Does the entry chapter introduce the major terminology and roadmap before deep mechanics?
Can the learner see why the major chapters belong to the same module?
Does each important concept explain the problem/motivation before technical rules?
Are concept-to-concept transitions explicit enough to explain why the next chapter exists?
Are Java mechanisms clearly connected to the domain/design concept they support?
Does code demonstrate a learning problem/behavior rather than merely restate syntax?
Does Knowledge explain the concepts before other surfaces test them?
Do learning APIs demonstrate concepts that exist in Knowledge?
Does execution explain meaning/evidence, not merely source order?
Does Quiz test understanding rather than memorize wording?
Does Interview add explanation/reasoning depth rather than duplicate Quiz?
Are relations exact and useful?
Is important content missing?
Is any content present only to inflate counts?
```

Do not mark `reviewed=true` merely because the same AI agent generated and re-read the content. Preserve the repository's governance meaning for human/independent review.

---

## 5. Coverage Review

Coverage Review happens after the six-step build flow. It is an independent curriculum-quality pass over the completed module.

The goal is **not** to force one-to-one coverage across all surfaces.

Build a conceptual matrix such as:

```text
Topic / section                 Knowledge   API   Quiz   Interview
------------------------------------------------------------------
Core mental model                 ✓          ✓     ✓        ✓
Lifecycle                         ✓          ✓     ✓        ✓
Configuration                     ✓          -     ✓        ✓
Pure conceptual limitation        ✓          -     ✓        ✓
Hands-on API observation          ✓          ✓     ✓        -
```

This is valid. Not every Knowledge topic needs an API, Quiz and Interview simultaneously.

Coverage Review should identify:

```text
missing beginner-facing module orientation / roadmap
major terminology introduced without explaining why it belongs in the module
definition-first chapters with no problem/motivation or learning context
chapter ordering that is technically valid but pedagogically unexplained
missing bridge between a domain/design concept and the Java mechanism that implements it
code examples that demonstrate syntax but not the reason the concept matters
unnecessary context resets where a running example would materially improve continuity
critical Knowledge concept with no reinforcement anywhere
learning API with no real Knowledge concept
important API experiment with weak/no execution explanation
Quiz cluster that repeats the same distinction
Interview questions that duplicate Quiz mechanically
important practical behavior missing from all assessments
orphan README relation
orphan API relation
VI/EN conceptual mismatch
stale or contradictory content
unnecessary filler added only to satisfy a count
```

Classify findings by importance instead of blindly adding content:

```text
MUST FIX
→ incorrect, contradictory, orphaned, missing critical concept, invalid relation,
  or a nontrivial module with no usable beginner orientation/roadmap

SHOULD IMPROVE
→ meaningful curriculum gap, weak motivation, poor chapter transition,
  terminology without relationship context, disconnected examples, repetitive assessment

OPTIONAL
→ enrichment that is useful but not required for module coherence
```

Coverage percentages are diagnostic only. They must never become a requirement such as "every Knowledge section must have an API".

---

## 6. Multi-agent workflow without losing one unified learning flow

Large modules may be split across workers to reduce context size and workload, but workers must not independently invent separate curricula.

The dependency chain remains:

```text
Prime / Lead
    ↓
Step 1: scope + canonical topic map
    ↓
Knowledge worker(s)
    ↓ approved/stable Knowledge structure
API worker(s), when applicable
    ↓ exact Knowledge relations established
Quiz worker(s) + Interview worker(s)
    ↓
Validation worker
    ↓
Independent Coverage Review
    ↓
Prime integration
```

### Recommended responsibilities

```text
Prime / Lead agent
→ owns module scope and final curriculum
→ defines chapter/topic map
→ defines the module-level learning narrative / terminology roadmap
→ decides important chapter transitions and running-example strategy
→ resolves conflicts between workers
→ ensures all phases use the same learning model

Knowledge worker
→ writes/reviews README Knowledge + metadata
→ establishes stable file/anchor identities
→ preserves the agreed three-layer flow and chapter-to-chapter narrative

API worker
→ starts only after Knowledge structure is sufficiently stable
→ inspects actual controllers/methods/source
→ maps learning APIs to exact Knowledge
→ writes API descriptions/execution/evidence

Quiz worker
→ consumes Knowledge + finalized API mapping
→ creates non-duplicative assessment

Interview worker
→ consumes the same Knowledge + API map
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
→ may run in parallel after Knowledge and required API mappings are stable

multiple chapter workers
→ may run in parallel only when the Prime has already fixed chapter ownership,
  terminology, learning narrative, cross-chapter boundaries and required transitions
```

### What must not run independently

Avoid this pattern:

```text
Worker A invents Knowledge curriculum
Worker B independently invents API curriculum
Worker C independently invents Quiz curriculum
Worker D independently invents Interview curriculum
```

Even if every file is individually valid, the result will usually be inconsistent.

Instead use handoff artifacts between phases:

```text
Step 1 output
→ canonical topic/chapter map
→ module mental model / beginner roadmap
→ major terminology + concept relationships
→ chapter-order rationale / important transitions
→ running-example plan when useful

Step 2 output
→ exact Knowledge files + anchors

Step 3 output
→ controller/method ↔ Knowledge mapping + API learning intent

Step 4/5 output
→ assessments tied to the established curriculum

Step 6 output
→ validated integrated module

Coverage Review output
→ prioritized gaps/fixes, not a competing curriculum
```

Workers must inspect the latest upstream files before editing. Do not rely only on a stale task description if an upstream worker has changed the canonical Knowledge/API structure.

---

## 7. Quality rules

### 7.1 Do not change concepts just to make relations convenient

Never rewrite curriculum semantics merely to make a relation easier to fill.

```text
No exact relation
→ blank relation is acceptable

Real missing curriculum concept
→ add/fix Knowledge because the concept belongs there

Artificial missing relation
→ do not manufacture Knowledge/API content
```

### 7.2 Do not create content solely to increase counts

Avoid:

```text
duplicate Quiz questions
near-identical Interview questions
trivial APIs with no learning value
tiny Knowledge sections that exist only as relation targets
fake relation mappings
```

### 7.3 Preserve exact technical meaning across languages

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

### 7.4 Source code is evidence, not the curriculum itself

The learning content must remain understandable without forcing the learner to reverse-engineer Java source first.

Use source to prove/explain behavior, especially in API execution documentation.

### 7.5 Respect module boundaries

Do not expand one module into a generic textbook covering adjacent technologies unless the extra material is necessary to understand the module itself.

Cross-topic context is useful; curriculum scope creep is not.

### 7.6 Do not write an encyclopedia of isolated definitions

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

### 7.7 Do not confuse coverage with pedagogy

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

## 8. Completion checklist

Before declaring a module learning build complete, confirm:

```text
[ ] AGENTS.md and ARCHITECTURE.md were followed
[ ] module identity/type/languages/capabilities were inspected
[ ] module topic map reflects actual module intent/source
[ ] the first/equivalent entry chapter explains what the module is, why it exists and what the learner will encounter
[ ] the entry chapter provides a major terminology map / learning roadmap
[ ] the chapter order has an explainable learning narrative rather than only a numeric sequence
[ ] important concepts explain problem/motivation → relation → Java mechanism before deep rules
[ ] domain/design concepts are distinguished from implementation/comparison/boundary mechanisms
[ ] chapter transitions explain why the next important concept follows
[ ] running examples are reused where continuity materially reduces cognitive load
[ ] Knowledge is coherent and uses stable unique anchors
[ ] Knowledge governance metadata is correct/preserved
[ ] SERVLET/REACTIVE learning APIs were inspected
[ ] every meaningful learning API has a real Knowledge concept or an explicitly justified blank relation
[ ] controller/method README relations are exact, not fuzzy guesses
[ ] API execution explains concept/flow/meaning/evidence/observation/conclusion where applicable
[ ] Quiz follows canonical schema and avoids duplicate coverage
[ ] Interview adds explanation/reasoning depth beyond Quiz
[ ] optional Knowledge/API relations are exact or intentionally blank
[ ] configured languages preserve conceptual parity
[ ] generators/schema/relation validation were run when available
[ ] only intended source files changed
[ ] Coverage Review was performed
[ ] MUST FIX findings are resolved
[ ] no content was added solely to inflate counts or relation coverage
```

The final result should feel like one module taught through several complementary surfaces, not four separate datasets that happen to share a module directory.
