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

---

## 3. Canonical module-owned sources

Agents must work on canonical module source, not generated Portal projections.

Typical learning sources are:

```text
Knowledge
module/.../src/main/resources/readme/{lang}/menu/**/*.md
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
definition / purpose
→ mental model
→ lifecycle or flow
→ examples
→ important rules
→ edge cases
→ pitfalls
→ trade-offs / when to use
→ practical observations
```

Not every section needs all layers. The content should fit the actual topic rather than follow a rigid template mechanically.

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
→ incorrect, contradictory, orphaned, missing critical concept, invalid relation

SHOULD IMPROVE
→ meaningful curriculum gap, weak explanation, repetitive assessment

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
→ resolves conflicts between workers
→ ensures all phases use the same learning model

Knowledge worker
→ writes/reviews README Knowledge + metadata
→ establishes stable file/anchor identities

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
  terminology and cross-chapter boundaries
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

---

## 8. Completion checklist

Before declaring a module learning build complete, confirm:

```text
[ ] AGENTS.md and ARCHITECTURE.md were followed
[ ] module identity/type/languages/capabilities were inspected
[ ] module topic map reflects actual module intent/source
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
