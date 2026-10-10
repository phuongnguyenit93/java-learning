# Step 9 - Validation Rules

Step 9 là integrated validation + Coverage Review cho cả **new module** và **refactor module**. Đây là step cuối trước khi coi learning build hoàn tất.

**Cross-cutting quality authority:** kiểm tra learning coherence theo `rules/MODULE_CONTENT_RULES.md` trên source hiện tại. Step 9 là integration/review gate; **không tự định nghĩa lại** learner baseline, WHAT/WHY progression, title policy hoặc plain-text cross-module bridge contract.

### General Agent Rules context guard

Trước khi thực hiện Step 9:

```text
Nếu context của GENERAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENERAL_AGENT_RULES.md trước khi tiếp tục
```

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

---

## Step 9 — Integrated validation

After Knowledge, API, Video, Quiz and Interview are authored, validate the module as one integrated unit.

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
BUILD_VIDEO modules mirror Video relative paths from Knowledge Menu paths
authored Video sections preserve current Knowledge H1/H2 structure/order
authored Scene/Transition fields pass Portal Video validation
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
Were module-owned core concepts introduced at WHAT/WHY level before dependent HOW/API/lifecycle sections?
Are assumed prerequisite concepts actually taught in the declared earlier learning path?
Does the entry chapter introduce the major terminology and roadmap before deep mechanics?
Can the learner see why the major chapters belong to the same module?
Does each important concept explain the problem/motivation before technical rules?
Are concept-to-concept transitions explicit enough to explain why the next chapter exists?
Are Java mechanisms clearly connected to the domain/design concept they support?
Does code demonstrate a learning problem/behavior rather than merely restate syntax?
Does Knowledge explain the concepts before other surfaces test them?
Do learning APIs demonstrate concepts that exist in Knowledge?
Does execution explain meaning/evidence, not merely source order?
Does each authored Video map one-to-one to its Knowledge Menu and preserve the Knowledge H2 learning order?
Does Video add useful visual/narrative value instead of copying Knowledge prose?
Are Video Scene/Transition visuals, narration and evidence technically consistent with Knowledge/API evidence?
Does Quiz test understanding rather than memorize wording?
Does Interview add explanation/reasoning depth rather than duplicate Quiz?
Are relations exact and useful?
Do H1/H2 use descriptive learning-topic labels rather than interview-style questions?
Are outside-module foundational concepts correctly introduced with concise prose when needed, without inventing navigation artifacts?
Is important content missing?
Is any content present only to inflate counts?
```

Do not mark `reviewed=true` merely because the same AI agent generated and re-read the content. Preserve the repository's governance meaning for human/independent review.

---

## Coverage Review

Coverage Review happens after the applicable new-module or refactor-module workflow. It is an independent curriculum-quality pass over the completed module.

The goal is **not** to force one-to-one coverage across all surfaces.

Build a conceptual matrix such as:

```text
Topic / section                 Knowledge   API   Video   Quiz   Interview
-------------------------------------------------------------------------
Core mental model                 ✓          ✓      ✓       ✓        ✓
Lifecycle                         ✓          ✓      ✓       ✓        ✓
Configuration                     ✓          -      ✓       ✓        ✓
Pure conceptual limitation        ✓          -      ✓       ✓        ✓
Hands-on API observation          ✓          ✓      ✓       ✓        -
```

This is valid. Not every Knowledge topic needs an API, Quiz and Interview simultaneously. Video follows a different unit rule: one authored Video per Knowledge Menu, while individual H2 sections may use different numbers/types of scenes.

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
Knowledge Menu with missing/stale Video mapping when BUILD_VIDEO is enabled for the completed module
Video script that copies Knowledge prose without a useful visual/presentation plan
Video that introduces claims or runtime behavior not supported by Knowledge/evidence
Video section order that drifts from the mapped Knowledge H2 order
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
  a nontrivial module with no usable beginner orientation/roadmap,
  missing essential WHAT/WHY or required prerequisite before advanced use,
  systematically interview-question-style Knowledge H1/H2,
  or an incorrect cross-module concept/ownership claim

SHOULD IMPROVE
→ meaningful curriculum gap, weak motivation, poor chapter transition,
  terminology without relationship context, disconnected examples, repetitive assessment

OPTIONAL
→ enrichment that is useful but not required for module coherence
```

Coverage percentages are diagnostic only. They must never become a requirement such as "every Knowledge section must have an API".

---

## Completion checklist

Before declaring a module learning build complete, confirm:

```text
[ ] AGENTS.md and ARCHITECTURE.md were followed
[ ] module identity/type/languages/capabilities were inspected
[ ] module topic map reflects actual module intent/source
[ ] the first/equivalent entry chapter explains what the module is, why it exists and what the learner will encounter
[ ] core terms have an evidenced prerequisite or an intelligible early WHAT/WHY foundation before technical detail
[ ] approved Menu WHAT/WHY/RELATION learning focuses are fulfilled by actual Step 4 Knowledge bodies
[ ] Knowledge H1/H2 titles are descriptive concept/topic labels, not Quiz/Interview prompts
[ ] necessary cross-module concept introductions use accurate, concise plain text without invented navigation
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
[ ] BUILD_VIDEO modules have one Video source per target Knowledge Menu
[ ] Video path/H1/H2 mapping matches current Knowledge structure
[ ] authored Video Scene/Transition fields validate and projection succeeds
[ ] Video narration/visuals remain technically aligned with Knowledge/API evidence
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

The final result should feel like one module taught through several complementary surfaces, not separate datasets that happen to share a module directory.
