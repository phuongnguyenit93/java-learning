# MODULE CONTENT RULES — Learner-First Pedagogical Contract

## 1. Purpose and authority

This file is the **canonical cross-cutting pedagogical contract** for learning content across all module-generation Steps, both new and refactor modules, all configured languages and independent reviews. It owns **what makes the learning journey effective**, not the mechanics of worktree/worker orchestration, Step ordering, Gradle, Markdown/schema generation or Git.

`GENERAL_AGENT_RULES.md` already requires reading **every current file** in `module-generate-agent/rules/` before module-generation execution; this file is part of that mandatory rule set. Individual STEP files retain authority over their **Step-specific outputs, authoring, validation and gap routing**:

- Step 1 / Curriculum: target learner, area/module ownership, boundaries and learning dependencies.
- Step 2 / Roadmap: milestone-level learning journey, prerequisite and milestone order.
- Step 3 / Menu: chapter/H1/H2 identities, learning-focus assignment and section order.
- Step 4 / Knowledge: actual teaching, prose, examples and evidence within approved H2 identities.
- Steps 5–8: present/reinforce current Knowledge without inventing a different curriculum.
- Step 9: independently verify the integrated learning journey.

Do not use a general principle here to bypass the approved Curriculum/Roadmap/Menu or to edit a different Step's owned content. This file defines **quality to achieve**, not a new Step, scheduling policy, artifact or navigation system.

## 2. Learner baseline: zero knowledge of the new topic, not zero knowledge of everything

**Default assumption:** the learner does **not yet understand the core concepts owned by the module they are entering**. A module/chapter name, a Roadmap title, a technical term's popularity or the agent's own expertise is not evidence that the learner already understands it.

**Universal learner-first requirement across EVERY applicable STEP:** author and review each artifact for someone encountering **this module's owned topic for the first time**, not for an expert who already knows its vocabulary. This applies to the Roadmap, Menu, Knowledge, guided APIs, Video, Quiz, Interview and integrated validation; downstream surfaces must not silently assume a foundation that upstream teaching has not established. The learner may still know **explicit, verified prerequisites** from earlier modules. This shared rule defines the learner baseline; **the minimum composition of Chapter 1 belongs only to `STEP_3_MENU.md`**, not to this file.

Do not erase genuinely established prerequisites. Determine the assumed baseline from the approved learner profile, explicit prerequisite modules, actual learning order and concept ownership. A concept owned by another module is assumed known **only if** prior learning really covers it; otherwise provide a short bridge or route a genuine prerequisite gap. A one-paragraph bridge **cannot replace a required deep prerequisite**.

Review each important concept:

1. Who owns its definition and depth?
2. Where does it first appear in the learner-facing sequence?
3. Is it already taught in an actual prerequisite? What is the evidence?
4. Which H2 introduces its meaning before advanced use?
5. Where does its motivation/relationship become understandable?

Merely writing a term in a title, BASE.md or module metadata does **not** teach it.

Example: a learner who has launched a Spring Boot app may still not understand *application runtime*. Introduce what happens while an application executes and why lifecycle understanding matters before discussing Boot-specific runtime coordination, events, availability or `SpringApplication.run()` internals. Do not pretend that "Application Runtime" is a separate installable component.

## 3. Required conceptual learning progression

For each important **module-owned** concept, the learner should be able to follow this logical chain:

1. **WHAT / SCOPE:** its beginner-readable meaning, boundaries and what it is *not*.
2. **WHY / MOTIVATION:** the concrete problem, limitation or purpose that makes it worth understanding.
3. **RELATION / CORE MODEL:** terms, components, dependencies and links to earlier concepts.
4. **HOW / MECHANISM:** behavior, lifecycle, code/API, configuration and relevant trade-offs.
5. **EVIDENCE / OBSERVATION:** focused example, code/runtime behavior, failure or trustworthy conceptual evidence appropriate to the module.
6. **SYNTHESIS / HANDOFF:** coherent end-to-end model and the boundary with neighboring modules.

This is a **logical prerequisite sequence, not a mechanical heading template**. WHAT and WHY may share one H2; a complex foundation may require multiple H2s. Not every H2 needs to repeat all layers, and motivating examples can precede formal definitions when they help the learner without requiring unexplained vocabulary.

But advanced HOW cannot assume the learner already knows essential WHAT/WHY. The learning journey must establish enough foundation for a learner to understand **what this topic means, why it matters, which core terms relate, and what comes next** before deeper technical sections. A distant section or a BASE overview does not automatically repair missing learner-facing foundations. The **Chapter 1 structural minimum and its H2 mapping/review gate** are Step 3 responsibilities.

**STEP 3 → STEP 4 handoff:** Step 3 identifies **which approved H2** owns each essential WHAT, WHY and RELATION learning focus, in a prerequisite-safe order. A single H2 may own more than one focus; the title need not contain literal "WHAT" or "WHY". Step 4 must **fulfill those focuses in the section body**, not assume they were taught just because its title names a concept. Step 4 must not redesign approved H2 structure to conceal a Menu gap.

## 4. Learner comprehension and grounded teaching evidence

**Content completeness is not teaching effectiveness.** A definition, WHY sentence, diagram, example mention or populated H2 inventory can satisfy a surface checklist while still leaving a newcomer unable to explain the concept. Evaluate the learner's ability to reconstruct the **module-owned core mental model** from the actual teaching, at the point in the approved learning order where that understanding is needed.

For an important concept, the Knowledge read up to that point (plus genuinely verified prerequisites) must give a newcomer enough to:

1. Explain the concept in plain language and distinguish it from a common misconception or neighboring concept when relevant.
2. Describe the concrete motivation/purpose and the core terms/relationships without silently importing unstated expertise.
3. Walk through a suitable **worked explanation**: start with a problem/input or initial state, follow the relevant actions/decisions/relationships, identify an observable outcome or justified conclusion, and connect it back to the concept.
4. Understand what new idea a following section adds, rather than encountering repeated definitions disguised as progression.

**Worked explanation is topic-appropriate, not a mandatory code block.** A stateful/runtime topic often benefits from traced code, expected output, state-before/after or a failure case; a pure design/conceptual topic may use a concrete scenario, contrast, diagram or reasoned counterexample. Include enough intermediate reasoning for a beginner to connect cause and result. Prefer a small running example that evolves when it lowers cognitive load. Do not require Java code, a fixed example template, one example per H2, or repeated WHAT/WHY/HOW labels.

**First-contact H2 local intelligibility:** when an anchored H2 is the approved first WHAT introduction for a **module-owned core concept**, a newcomer reading that H2 (with actually verified earlier prerequisites) must be able to explain the concept's basic meaning and form an initial concrete mental picture **before opening later H2s or chapters**. Supply a plain-language explanation and, when the term remains abstract without one, a small immediately understandable illustration inside that H2. Its deeper WHY, mechanism, full worked trace, trade-offs and comparisons may still live in later approved H2s. This does **not** require each H2 to be independently complete or repeat later lessons, and does not allow Step 4 to alter Step 3's approved learning-focus allocation. If local intelligibility is impossible without changing H2 focus/order, route a `MENU GAP` rather than hiding the foundation elsewhere.

**Code evidence must be honest and verifiable:** when Java code is presented as a **runnable** example or used to substantiate claimed Java behavior, keep the example syntactically/semantically valid for its stated Java version and stated context, and make expected output, state changes or exceptions consistent with it. Compile/run relevant minimal examples using an available matching toolchain when practical; otherwise verify by careful reasoning and **do not claim execution occurred**. An intentionally partial snippet must be labeled/contextualized as a fragment (or pseudocode if non-Java); it must not be passed off as standalone executable Java. Language-independent modules may teach through pseudocode, diagrams and scenarios without compulsory Java samples. The Step 4 author owns example correctness; Step 9 reviewers verify significant executable/behavioral claims as part of the existing validation.

**Teach distinctions where misunderstanding matters:** if learners could reasonably confuse the owned concept with a neighboring paradigm, mechanism or idea, explain the **actual distinguishing dimension** and show a short contrast or shared scenario when useful (for example, describing explicit operations versus specifying a desired result). State meaningful overlap and limits; avoid absolute or technically false either/or claims. Keep the comparison within this module's ownership and place it at the approved first helpful learning point, with later sections free to expand. Never force a comparison into an unrelated H2 or require every concept to name other paradigms.

Introduce essential terminology at or before its first consequential use; a later chapter's definition does not by itself repair an unexplained term in the entry path. Prior verified learning may support an earlier use, and a concise cross-module bridge may support a non-owned term under Section 6. Avoid repeating nearly equivalent definitions across H2s without a distinct teaching purpose; preserving stable anchors does not require artificial prose fragmentation.

**Grounded learner-comprehension check:** select representative beginner questions about the entry concept and important mechanisms/limits. Attempt a short, understandable teach-back answer **using only current Knowledge encountered in the valid learning order and verified prerequisites**. Every essential explanation/example step must be traceable to real Markdown section content; normal paraphrasing and synthesis are allowed, but inventing missing definitions, causal bridges, worked steps or evidence to make the answer sound good is not. If the reviewer can answer only by importing its own expertise, the source has a teaching gap even if the reviewer personally knows the correct answer. An external/generic AI explanation can be a diagnostic comparison, **not an authority or a mandatory length/style target**; validate its technical correctness and module scope first.

This is a quality test within existing Step 4 authoring and Step 9 independent review, **not** a new Step, dataset, required learner trial, per-H2 numeric quota, schema, artifact, Portal feature or extra reviewer round. The owning Steps decide how to author, cite review evidence and route fixes.

## 5. Descriptive learning titles, not interview questions

Knowledge H1 and anchored H2 are **navigation identities of learnable topics**. Prefer accurate, concise descriptive statements/noun phrases naming the specific concept, mechanism, lifecycle, behavior, comparison, risk or trade-off rather than a Quiz/Interview-style question.

Examples of semantic rewriting (not string/regex replacements):

| Avoid question-style H2 | Prefer descriptive topic title |
| --- | --- |
| X là gì? | X: Khái niệm và phạm vi |
| Vì sao cần X? | Mục đích và vấn đề X giải quyết |
| Có những vòng đời nào? | Vòng đời của đối tượng cụ thể |
| X hoạt động như thế nào? | Cơ chế hoạt động của X |
| Khi nào sử dụng X? | Trường hợp sử dụng và giới hạn của X |
| What is X? | X: Concept and Scope |

A title must stand alone in the Menu/Portal index, match its actual body focus and distinguish adjacent sections. Avoid empty generic names like "Introduction" without the specific subject, misleading assertions or multiple unrelated topics squeezed into a heading. Preserve natural VI/EN wording with equivalent concept scope/order and stable anchor identity.

**Beginner-first title clarity is separate from terminology correctness.** A grammatically correct, technically precise noun phrase can still be a poor lesson title when it stacks abstract terms that a learner has not yet met: e.g., `Xác định nơi sở hữu hành vi và quyết định`, `Tính gắn kết giữa các hành vi cùng trách nhiệm`, or `Mức độ phụ thuộc và tác động của thay đổi thiết kế`. Evaluate a visible title **as a standalone Portal card in its real learning order**, with only genuinely taught prerequisites available. It should help the learner anticipate **which concrete concept, action, relationship, problem or outcome** the lesson will clarify, without first opening the body or knowing the author's intended terminology. Do **not** require full conceptual mastery from a title: a new technical term may appear when paired with a short, intelligible explanatory phrase, e.g., `Tính gắn kết: Các hành vi phục vụ cùng một mục đích` or `Hợp đồng giữa các đối tượng: Điều kiện và kết quả cam kết`.

**Concrete title self-test:** (1) Can an entrant at this point roughly say what they will learn from the title alone? (2) Does the title identify a meaningful subject plus **a specific distinguishing angle** (purpose, action, consequence or relation), rather than merely combine broad nouns (`trách nhiệm`, `hành vi`, `quyết định`, `phụ thuộc`)? (3) Are any technical words new to this learner paired with just enough meaning, or clearly preceded by a real first-use introduction? (4) Are neighboring titles **meaningfully distinguishable**, without saying the same abstract thing in seven ways? (5) Does the title honestly describe its approved H2 body and avoid claiming broader coverage? If not, refine the visible wording in the owning Step while preserving the actual learning scope and stable identities when appropriate. This is **semantic editing**, not a keyword replacement, forbidden-word list, rigid `X: Y` template, mandate for long sentence-like headlines, or permission to add new H2 topics.

**Canonical concept discoverability is a THIRD, independent title quality test.** If an H2's **primary approved focus** is a recognized, named technical concept, identify its real standard name from reliable terminology sources and make that concept **recognizable in its visible title**. Prefer an established localized name and, **when useful to disambiguate or search**, its widely used English term/acronym alongside a short learner-friendly meaning, e.g., `Cohesion — Tính gắn kết trong một đối tượng`, `Coupling — Mức độ phụ thuộc giữa các đối tượng`, `Composition — Quan hệ hợp thành đối tượng`. Do not replace a known concept with only a vague consequence/action paraphrase such as `Mức độ phụ thuộc và tác động của thay đổi thiết kế` when the lesson's actual focus is Coupling. The learner should be able to connect the title to documentation, interviews and later lessons **without guessing the hidden terminology**.

**Not every H2 is a named-concept lesson.** A comparison, workflow, design decision, historical development or application may require a descriptive topic/angle rather than an invented English label. Never coin a supposedly canonical name, mistake a common practice for a formally standardized term, or force English into natural VI titles when the established Vietnamese term is already unambiguous; in EN, prefer the standard English term. Conversely, a bare term with no useful first-use meaning can still fail the **beginner comprehension** test. A title may introduce new vocabulary with a short gloss; the H2 body must then teach it at first contact, not require the learner to know it in advance.

**Distinct-concept / distinct-learning-focus check:** in a cluster of close H2s, distinguish the **concept identity** from the **learning task** (definition/model, assignment/decision, relationships, mechanism, application, trade-off). Each H2 must have a non-redundant outcome, prerequisite position and scope, not just different wording for the same conclusion. Related concepts may be taught in one coherent H2 where justified; one technical word does not automatically deserve its own lesson. A defensible example is `Object Responsibility` (what an object is responsible for) → `Responsibility Assignment` (how to decide which object owns a behavior) → `Cohesion` (whether behaviors within that object belong together). An apparent duplicate must be checked for genuinely different capability before merging or adding a new H2. If the concept is owned by a different module, use an appropriate short orientation and respect module ownership rather than duplicating a whole advanced lesson.

**Do not promote teaching-support prose into lesson identities:** `WHY`, motivation, mental-model overview, prerequisite orientation, a journey preview, a bridge to a neighboring module and a recap are **things a lesson may need to explain**, not automatically separate concepts or H2s. The default home for a short introductory WHY/comparison/roadmap preview is the body of an approved **concept-centered** H2. A dedicated non-concept H2 is justified only when its **own distinct, substantive** historical, comparative, practical, decision or mechanism objective cannot be taught coherently inside an existing H2. Require the reviewer to name that objective and concrete teaching evidence; a polished abstract phrase or an extra paragraph of motivation does not qualify. Distinct concept H2s may naturally cover WHAT + WHY + HOW and need not spawn separate essay-like H2s. The first chapter is especially vulnerable: do not substitute seven meta headings about *why, purpose, map, scope, Java relation, learning path* for recognizable foundational concepts. Nor should all overview, comparison or application lessons be banned; decide by actual pedagogical necessity and approved ownership.

**Illustrative OOP title rewrite (not a mandatory module rename):** `Xác định nơi sở hữu hành vi và quyết định` → `Responsibility Assignment — Phân chia trách nhiệm giữa các đối tượng`; `Luồng phối hợp nhiều đối tượng cho một yêu cầu` → `Object Collaboration — Cách các đối tượng phối hợp xử lý yêu cầu`. These titles must still pass the novice-clarity test and match their actual H2s. Localized wording may vary; these examples do **not** impose bilingual headings on every lesson or change stable anchors by themselves.

**Guiding questions belong inside the Step 4 Knowledge body** or downstream Quiz/Interview, not as the default H1/H2 identity. A Menu where essentially every H2 is an interrogative prompt **does not pass**. Inspect every title, not just a ratio. For existing material, title/path/anchor revisions follow downstream migration and relation rules in the owning Steps.

## 6. Plain-text cross-module foundation bridge — not navigation

When the current lesson needs a basic concept whose **deep teaching is owned by a different module**, include a **short, technically correct WHAT-level bridge in prose** at the first useful occurrence:

- Explain what the outside concept is *just enough* for this lesson, and its real relationship to the current concept.
- When helpful, mention a **verified real module** by name in ordinary text, e.g. "Để tìm hiểu kỹ hơn về X, bạn có thể học module Y."
- **Do not** create a Markdown hyperlink, URL, route, button, new anchor, `routeId`, `relatedModules`, `readmeRelated` or other navigation metadata **solely for this explanatory bridge**. Existing independently required Roadmap/Knowledge navigation contracts remain in force.
- Prefer the body of an existing relevant H2, not a new H2/H3/chapter solely to direct learners elsewhere. Do not repeat the same cross-module notice every time a term appears.
- Do not copy the other module's full teaching depth, invent an owner or misrepresent the dependency. If substantial prior understanding is genuinely required, resolve the prerequisite gap rather than treating this note as a substitute.

Example of **plain text inside a Spring Boot Knowledge section**:

> Spring Boot được xây dựng trên Spring Framework. Những cơ chế như IoC và Dependency Injection là nền tảng của Spring Framework mà Spring Boot sử dụng khi cấu hình và khởi động ứng dụng. Để tìm hiểu sâu hơn, bạn có thể học module Spring Framework Core Container (spring-framework/core-container).

Step 3 identifies where the bridge is needed; **Step 4 authors the actual prose**. The relationship must remain technically accurate: Spring Boot builds on Spring Framework, rather than being described as "a part of Spring Core". Localize the explanation naturally in VI/EN without changing ownership meaning.

## 7. Review quality and gaps

**Technical validation ≠ pedagogical validation.** Passing Gradle/generators, valid anchors and a full content inventory do not prove that learners can follow the course.

For each applicable Step's authoring/review, check the current source for: target baseline and real prerequisites; first-contact H2 intelligibility and each essential term's first use vs introduction; WHY and mental model before difficult HOW; worked teaching evidence, code-claim validity, relevant conceptual contrasts and meaningful progression rather than repeated fragments; chapter transitions; descriptive H1/H2; natural VI/EN parity; cross-module ownership/short bridges; and meaningful learning outcomes. Independent reviewers must assess **current full source**, not merely a previous report or diff. The grounded comprehension check in Section 4 must test what the learner-facing source teaches, not how fluently the reviewer can answer from memory.

**MUST FIX:** missing foundational meaning (including an unintelligible first-contact core WHAT H2), hidden mandatory prerequisite, unexplained core vocabulary before advanced material, missing explanation/evidence essential to reconstruct a module-owned core concept, incorrectly represented runnable Java or a materially false example/contrast, systematically question-like lesson titles, false cross-module relation, or a Menu structure that cannot accommodate a coherent Step 4. Weaker-than-ideal narrative, an optional extra example or comparison, or harmless style differences are not automatically MUST FIX; classify by the actual consequence for comprehension.

Route the defect to its real owner:

- **CURRICULUM GAP (Step 1):** wrong module ownership, baseline, boundary or inter-module dependency.
- **ROADMAP GAP (Step 2):** missing milestone or wrong milestone-level learning order/prerequisite.
- **MENU GAP (Step 3):** wrong or missing chapter/H2 identity, focus, title or order.
- **KNOWLEDGE GAP (Step 4):** approved H2 exists but its explanation, examples or evidence are absent/inadequate.

Fix/recheck/revalidate under the active Step/orchestration rules. This file does **not** change required review counts, reviewer eligibility or parent-level barriers.
