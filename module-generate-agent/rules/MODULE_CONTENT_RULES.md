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

## 4. Descriptive learning titles, not interview questions

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

**Guiding questions belong inside the Step 4 Knowledge body** or downstream Quiz/Interview, not as the default H1/H2 identity. A Menu where essentially every H2 is an interrogative prompt **does not pass**. Inspect every title, not just a ratio. For existing material, title/path/anchor revisions follow downstream migration and relation rules in the owning Steps.

## 5. Plain-text cross-module foundation bridge — not navigation

When the current lesson needs a basic concept whose **deep teaching is owned by a different module**, include a **short, technically correct WHAT-level bridge in prose** at the first useful occurrence:

- Explain what the outside concept is *just enough* for this lesson, and its real relationship to the current concept.
- When helpful, mention a **verified real module** by name in ordinary text, e.g. "Để tìm hiểu kỹ hơn về X, bạn có thể học module Y."
- **Do not** create a Markdown hyperlink, URL, route, button, new anchor, `routeId`, `relatedModules`, `readmeRelated` or other navigation metadata **solely for this explanatory bridge**. Existing independently required Roadmap/Knowledge navigation contracts remain in force.
- Prefer the body of an existing relevant H2, not a new H2/H3/chapter solely to direct learners elsewhere. Do not repeat the same cross-module notice every time a term appears.
- Do not copy the other module's full teaching depth, invent an owner or misrepresent the dependency. If substantial prior understanding is genuinely required, resolve the prerequisite gap rather than treating this note as a substitute.

Example of **plain text inside a Spring Boot Knowledge section**:

> Spring Boot được xây dựng trên Spring Framework. Những cơ chế như IoC và Dependency Injection là nền tảng của Spring Framework mà Spring Boot sử dụng khi cấu hình và khởi động ứng dụng. Để tìm hiểu sâu hơn, bạn có thể học module Spring Framework Core Container (spring-framework/core-container).

Step 3 identifies where the bridge is needed; **Step 4 authors the actual prose**. The relationship must remain technically accurate: Spring Boot builds on Spring Framework, rather than being described as "a part of Spring Core". Localize the explanation naturally in VI/EN without changing ownership meaning.

## 6. Review quality and gaps

**Technical validation ≠ pedagogical validation.** Passing Gradle/generators, valid anchors and a full content inventory do not prove that learners can follow the course.

For each applicable Step's authoring/review, check the current source for: target baseline and real prerequisites; each essential term's first use vs introduction; WHY and mental model before difficult HOW; chapter transitions; descriptive H1/H2; appropriate evidence; natural VI/EN parity; cross-module ownership/short bridges; and meaningful learning outcomes. Independent reviewers must assess **current full source**, not merely a previous report or diff.

**MUST FIX:** missing foundational meaning, hidden mandatory prerequisite, unexplained core vocabulary before advanced material, systematically question-like lesson titles, false cross-module relation, or a Menu structure that cannot accommodate a coherent Step 4. Harmless style differences are not by themselves MUST FIX.

Route the defect to its real owner:

- **CURRICULUM GAP (Step 1):** wrong module ownership, baseline, boundary or inter-module dependency.
- **ROADMAP GAP (Step 2):** missing milestone or wrong milestone-level learning order/prerequisite.
- **MENU GAP (Step 3):** wrong or missing chapter/H2 identity, focus, title or order.
- **KNOWLEDGE GAP (Step 4):** approved H2 exists but its explanation, examples or evidence are absent/inadequate.

Fix/recheck/revalidate under the active Step/orchestration rules. This file does **not** change required review counts, reviewer eligibility or parent-level barriers.
