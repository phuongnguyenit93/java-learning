# Step 8 - Validation Rules

Step 8 là integrated validation + Coverage Review cho cả **new module** và **refactor module**. Đây là step cuối trước khi coi learning build hoàn tất.

### General Agent Rules context guard

Trước khi thực hiện Step 8:

```text
Nếu context của GENARAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENARAL_AGENT_RULES.md trước khi tiếp tục
```

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

---

## Step 8 — Integrated validation

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

## Coverage Review

Coverage Review happens after the applicable new-module or refactor-module workflow. It is an independent curriculum-quality pass over the completed module.

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

## Completion checklist

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
