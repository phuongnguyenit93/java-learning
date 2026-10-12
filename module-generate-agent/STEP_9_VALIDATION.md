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
Are runnable Java examples valid for their stated context, and are partial snippets/pseudocode identified honestly?
Can the first WHAT H2 of each core entry concept be understood without borrowing explanations from later sections?
Are easy-to-confuse paradigms/mechanisms distinguished accurately at the useful learning point, including overlap?
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
When their bodies are hidden, can the declared beginner understand the concrete lesson
angle from each visible H1/H2 Portal title in learning order, without expert assumptions?
Do neighboring titles meaningfully distinguish their subject/angle instead of repeating
abstract jargon that becomes clear only after reading the full body?
When an H2 owns a recognized technical concept, can the learner identify its standard
name from the visible title and use it to locate follow-up documentation?
For adjacent H2s, are named concepts (when applicable), taught abilities and prerequisite
positions genuinely distinct, rather than just differently worded descriptions?
Are outside-module foundational concepts correctly introduced with concise prose when needed, without inventing navigation artifacts?
Is important content missing?
Is any content present only to inflate counts?
```

Do not mark `reviewed=true` merely because the same AI agent generated and re-read the content. Preserve the repository's governance meaning for human/independent review.

#### Source-grounded learner comprehension gate

Apply `rules/MODULE_CONTENT_RULES.md` Section 4 as a **separate evidence-based pedagogical test**, not as another structural checklist or a demand for a particular writing style. For a representative sample (normally 2–3 beginner questions spanning entry WHAT/WHY, a core mechanism/trace, and an application/limit when relevant), an independent reviewer must try to teach back an intelligible answer from the **current full Knowledge source** and verified earlier prerequisites, in actual learner-facing order. For broad modules, expand the sample to cover other high-risk foundations or after any failed probe; the suggested count is **not** a coverage ceiling or mandatory per-H2 quota.

**Separately inspect every approved first-contact H2 for a module-owned core WHAT concept**, not just the sampled questions: read its actual H2 body with verified earlier prerequisites and confirm that a novice can explain the concept's basic meaning and picture a concrete illustration before visiting later H2s. Record any failing H2 identity and the missing explanation in the current review result. This focused gate does not demand a complete standalone lesson or duplicate the full later WHY/HOW content.

For each probe, include concise evidence in the **existing review result** (no new tracked artifact or schema):

```text
learner question / expected understanding
→ specific current Markdown chapter + anchored H2(s) that teach each essential point
→ first-use/prerequisite order check for required terminology
→ concrete example/trace/observation or justified conceptual evidence where needed
→ for entry core WHAT: can its H2 alone plus verified earlier prerequisites explain basic meaning?
→ for material Java/contrast claims: context/correctness and evidence of validation, if executed
→ PASS or finding with MUST FIX / SHOULD IMPROVE / OPTIONAL rationale
```

Do not treat a section title, `BASE.md`, Quiz/Interview answer, video narration, generator PASS or the reviewer's background knowledge as a substitute for **actual prior Knowledge teaching**. It is legitimate to paraphrase and combine what is present, but the reviewer must not fabricate a definition, reasoning link, example step or observation to complete an otherwise deficient answer. If a normal AI answer seems clearer, use it only to locate possible gaps after checking that it is technically correct and in scope; greater length or fluency alone is not a failure.

The optional Step 4 working plan governed by `STEP_3_5_TEACHING_BLUEPRINT.md` may explain authoring intent but **cannot substitute** for a causal worked explanation, example, prerequisite introduction or contrast actually present in learner-facing Markdown. Judge the lesson as written, not as planned.

Apply **intent-aware comprehension** when reviewing the actual finished Knowledge: a WHAT H2 must convey an intelligible meaning, a WHY H2 must substantiate the important reason or consequence, a HOW H2 must reveal the mechanism, a RELATION H2 must connect components, and a LEARNING JOURNEY H2 must tell the learner their real starting point, why major milestones follow each other and what capability they will gain. Comparison/application/correctness H2s need correspondingly meaningful distinctions, outcomes and verification. Do not demand identical code/example templates across all H2s; do not PASS merely because each H2 contains a paragraph, an example keyword or a list of upcoming topics. Assess each H2 against its **approved real learning purpose**, not a title-based category guess.

**Independent visible-title usability review:** in addition to reading finished Knowledge, inspect **H1/H2 titles by themselves in learner order** as a Portal visitor would. Judge clarity for the declared novice using actual earlier teaching; a precise title whose meaning can only be guessed after reading the body is not automatically usable navigation. Verify it communicates the approved lesson's meaningful focus, remains distinct from neighboring labels and uses natural VI/EN wording; do not replace it with an interview question or pack the whole explanation into the heading. Route genuinely unclear labels to **Step 3** for title-level repair and downstream migration check; do not treat correct body prose as a substitute for the failed title.

**Canonical term and adjacent-focus check:** for a named technical concept owned by an H2, verify the learner can **recognize the actual standard term** in the index and the body teaches it at the promised depth. A vague paraphrase alone may fail discoverability even when it sounds more accessible; a bare unexplained foreign term may fail novice clarity. For neighboring H2s, verify their *new learning outcomes* are different, not simply varied surface wording. Do not enforce English words on every title, one H2 per term, or a universal numbered WHAT/WHY/HOW template; route structurally ambiguous concept identities to Step 3 and real curriculum boundary issues upstream.

**Canonical concept architecture / reverse-trace review:** validate the current **approved Step 2 milestone → Step 3 concept-family and focused H1/H2 → Step 4 actual lesson** chain in both directions, using source as evidence (not merely a planning note): each essential milestone capability has sufficient real Knowledge teaching; each H2 belongs to an approved outcome and makes a distinct contribution; first teaching happens before dependent use; recognized terminology and group membership are consistent with the verified module/neighbor ownership. A concept may be briefly introduced in one H2 and revisited for different depth later, but a full repeated lesson with no new learner ability or a core concept mentioned without being taught is a real finding. Distinguish concept relationship from prerequisite order; a non-linear branching dependency must not become a fabricated single mandatory chain. Do **not** require `conceptId`, a new persisted taxonomy, one-H2-per-concept, a new Quiz relation or a Portal feature to perform this content review. Route structural ownership/ordering defects back to their canonical Step 1–3 owners.

**Milestone-to-Knowledge role audit:** for every significant milestone **objective**, independently identify in the current Markdown the **PRIMARY** H2(s) that actually teach the new ability, any **PREREQUISITE** H2/module content that must have been taught earlier, and any **RELATED/EXTENSION** material that offers context but is not itself required. Audit reused Knowledge category links by their *specific H2 contributions*: a `relatedKnowledge` item is **untyped navigation** and does not assert that every H2 in the category is a primary teacher or that all prerequisite content is complete. Do not flag valid shared-category links as duplicates merely because two milestones use them; do flag an objective whose only apparent support is a related navigation target or an H2 that does not teach the promised skill. Base findings on exact current content and approved ownership, not a count of `relatedKnowledge` targets, title similarity or a review preview alone. This adds no new schema, Portal badge, learner-progress feature or review round.

**Integrated preview is an audit aid, never proof by itself:** Step 2/3 may show a concise staged/grouped concept design in their existing review results. Independently reconstruct the critical milestone → current H1/H2 → **actual taught concept and learner ability** path from the present Roadmap and Markdown, and note any divergence from the intended preview. A beautiful four-stage/twelve-topic outline does not PASS if actual lesson titles hide canonical concepts, prerequisites arrive late or the approved abilities are not taught; equally, a different number of stages/topics does not FAIL when its real concept structure, evidence and progression are sound. No extra tracked map, UI layer, schema or review round is implied.

**Chapter-1 over-fragmentation check (Step 3 structural owner):** look beyond individual technically valid titles. Inspect the complete first chapter for an unnecessary succession of `definition → context → purpose → map → language boundary → learning path` H2s with only one or two recognizable concept teaching units. For each narrative H2, demand its own distinct approved learner outcome and evidence that would not fit coherently in the adjacent foundational concept H2. If merely a support paragraph, group it into the appropriate H2 and route structural consolidation through Step 3; if two H2s both explain the same old-vs-new motivation, test whether they are actually duplicates. A course-introduction/teaching-intent checklist satisfied as seven separate H2s does **not** prove a concept-first Menu. Preserve legitimate substantial historical/comparative/practice chapters; do not enforce a universal H2 count or require every H2 to have an English term. A structural identity/sequence defect that blocks an intelligible concept-led beginner path is **MUST FIX**; merely verbose but otherwise effective prose is **SHOULD IMPROVE**.

For an approved **ORIGIN / HISTORY** H2, inspect the actual written account for a historically warranted origin, grounded predecessor/context, supported milestones and their real effects, and a clear separation of **documented causation versus temporal association**. Check impactful dates, attributions, “first” claims and uncertainty against reliable sources; reject falsely equating a hardware architecture, language and abstract paradigm or portraying modern sample Java as a historically authentic program. A correct historical story must **teach the distinction**, not merely decorate a conceptual WHY with a timeline. Do not demand historical depth for every module or insist that every historical example contain code.

For an approved **TAXONOMY / MAP** or multi-level conceptual overview, check the **reason for grouping**, whether the listed items truly share that criterion, the meaningful **cross-level links**, and whether the promised breadth and depth match the actual approved Roadmap/Menu. Do not accept a flat glossary, a misleading universal “pillars” inventory, or the accidental transfer of language-specific details into a language-independent paradigm. A focused **RELATION** H2 may not need the broad taxonomy, but the actual components named in its title must be explained and related rather than left as an arrow of abstract nouns. Where a conceptual diagram shows two instances while code creates only one, check that the other is labeled as hypothetical instead of claiming execution proved its existence.

**Cross-module overlap and learning-depth review:** when a broad syllabus includes material owned by other modules, verify that the Knowledge here teaches its **approved own outcome**, gives essential prerequisite bridges at first use, and treats neighboring advanced concepts as context rather than copying entire sibling lessons. An absent keyword is not automatically a coverage defect if the concept is outside scope; a duplicated concept is not automatically wrong if this module establishes a **different learner capability**. Require a concrete missing-owned-outcome or unnecessary repeated-teaching rationale before issuing findings; route genuine ownership conflicts upstream instead of increasing content volume.

For a substantial LEARNING JOURNEY or course-entry overview, also test whether the **current written source** tells learners how to demonstrate progress at major approved milestones, why one stage enables the next and which attainable end abilities count as readiness/completion. Where the lesson promises an evolving example or project, spot-check that its stages correspond to real downstream Knowledge and that no required stage silently depends on a missing prerequisite. Verify any suggested resources are real/appropriate and any duration is qualified as an estimate rather than guaranteed. This is a content/review test, not a mandate for eight stages, weekly schedules, capstone software, a completion checklist UI or extra tracked artifacts.

For high-value examples that combine a real-world scenario, code and observed result, verify the **mapping in the current learner-facing source**: can a beginner point from each important scenario action to its actual condition/command, trace the branch taken and obtain the stated result? Where core components are named, can the reader identify their concrete roles **within the same illustration**? A missing bridge is a real teaching defect even if the scenario, code, terminology table and output each look correct in isolation. Do not require this particular representation or a long mapping table for purely conceptual topics.

**MUST FIX** when a novice cannot reconstruct a module-owned core concept because essential meaning, prerequisite, reasoning or observable evidence is absent or arrives after dependent advanced material; when the first core WHAT H2 is unintelligible on its own terms; or when an example advertised as runnable Java is invalid or a central contrast materially misrepresents a concept. **SHOULD IMPROVE** for a coherent but weak narrative, unnecessarily fragmented/repetitive teaching, or an optional example/contrast that would materially improve clarity without blocking foundational understanding. **OPTIONAL** for extra enrichment/style. Route valid findings to Step 4 for teaching defects, Step 3 for structural/focus/order gaps, Step 2 for milestone/prerequisite order gaps, or Step 1 for curriculum ownership/baseline gaps. Recheck repaired source under the active review barriers; this gate adds **no new STEP, review round, worker or learner trial**.

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
introductory core WHAT H2 that only becomes intelligible after reading a later section
core term introduced in an entry chapter but not intelligibly explained until a later chapter
core mental model that a beginner could only explain by importing the reviewer's outside knowledge
chapter ordering that is technically valid but pedagogically unexplained
missing bridge between a domain/design concept and the Java mechanism that implements it
code examples that demonstrate syntax but not the reason the concept matters
runnable Java examples with missing context, invalid syntax or incorrect claimed output; unlabeled pseudocode presented as Java
misleading contrasts that erase significant overlap or imply false exclusivity between paradigms
example mentions that never trace the relevant decision/relationship to an outcome
unnecessary context resets where a running example would materially improve continuity
near-duplicate H2 explanations that add no distinct learner understanding
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
  missing teaching/evidence needed to reconstruct a module-owned core mental model,
  unintelligible first-contact core WHAT H2, materially invalid runnable Java example,
  materially false distinction/overlap between important concepts,
  systematically interview-question-style Knowledge H1/H2,
  or an incorrect cross-module concept/ownership claim

SHOULD IMPROVE
→ meaningful curriculum gap, weak motivation, poor chapter transition,
  terminology without relationship context, disconnected/repetitive examples,
  coherent but unnecessarily fragmented teaching, repetitive assessment

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
[ ] core introductory WHAT H2s explain basic meaning locally without depending on later deep-dive sections
[ ] runnable Java example claims and expected observations are sound for the stated context, with snippets labeled appropriately
[ ] high-risk concept/paradigm distinctions explain the real difference and overlap without false exclusivity
[ ] where historical origin/evolution is an approved H2 focus, consequential dates, attribution, causation and technology/concept boundaries are independently checked
[ ] when an overview promises a multi-level concept map, categories and examples clarify classification, relationships and actual depth ownership rather than mixing incompatible terms into a flat inventory
[ ] source-grounded comprehension probes include cited current H2 evidence, valid first-use order and justified findings
[ ] a newcomer can reconstruct central meaning and a suitable worked explanation without reviewer-invented bridges
[ ] Knowledge H1/H2 titles are descriptive concept/topic labels, not Quiz/Interview prompts
[ ] visible H1/H2 Portal titles pass novice-alone title clarity and adjacent-distinction checks without requiring the learner to open the lesson to learn what the label means
[ ] H2s whose primary focus is a recognized named technical concept expose a findable term in the visible title; adjacent H2 identities add distinct learning outcomes rather than duplicate prose under new labels
[ ] approved milestone outcomes have real H2/Knowledge teaching homes, and every H2 traces back to an approved milestone/concept family without an unowned/untaught essential concept or invented prerequisite
[ ] each material milestone objective has a genuine PRIMARY teaching explanation, its strict PREREQUISITE learning predates use, and RELATED navigation does not substitute for learning evidence
[ ] necessary cross-module concept introductions use accurate, concise plain text without invented navigation
[ ] the entry chapter provides a major terminology map / learning roadmap
[ ] first chapter does not inflate pedagogical support (WHY, map, boundaries,
    learning journey) into multiple H2s without distinct substantial concepts/outcomes;
    redundant narrative H2 identities are routed to Step 3 for consolidation
[ ] the chapter order has an explainable learning narrative rather than only a numeric sequence
[ ] substantial Learning Journey sections connect meaningful approved stages to learner-observable outcomes and feasible readiness/practice checks, without invented scope or fixed schedules
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
