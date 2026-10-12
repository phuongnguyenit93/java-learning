# Step 3.5 — Teaching Blueprint (Step 4 Pre-Authoring Gate)

## 1. Status, authority and boundaries

**Step 3.5 is a mandatory DESIGN PHASE inside canonical Step 4, placed after the Step 3 Menu approval and before substantive Knowledge body authoring.** This file owns the **lesson delivery design** (HOW TO TEACH), not a new canonical workflow Step or source-of-truth layer.

```text
Step 3 — approved chapter / anchored H2 Menu and learning-focus ownership
       ↓
Step 4, Phase A — STEP 3.5 Teaching Blueprint + design gate (this file)
       ↓
Step 4, Phase B — author/refactor Knowledge Markdown using that blueprint
       ↓
Step 4 — existing author validation and independent review phases
       ↓
Step 5 — next CURRENT canonical Step when applicable
```

- **Canonical numbering stays Step 1 → Step 10.** There is no separately scheduled `3.5` unit for parent-horizontal orchestration, no new cross-child barrier, worker lane, review round, Git branch, commit, generator, schema, Portal feature or mandatory tracked blueprint artifact.
- Read `AGENTS.md`, `ARCHITECTURE.md`, `GENERAL_AGENT_RULES.md`, **all current** `module-generate-agent/rules/*`, the current canonical `STEP_4_KNOWLEDGE.md`, and the appropriate Step 1/2/3 upstream inputs according to the active workflow. This file supplements **Step 4's authoring responsibility**; it does not override the orchestration or Step 3 structural authority.
- Step 3 decides **WHAT/WHY/RELATION learning focus, chapter/H2 identity and order**. Step 3.5 decides **which teaching experience and evidence will make that approved focus intelligible**. Step 4 writes the actual full Markdown, examples and technical explanations. Step 9 independently verifies actual finished Knowledge, not promises in a blueprint.
- Applies to **new and refactor** modules, all configured VI/EN languages, including isolated Step 4 calls using the existing approved/current Menu and verified fallback context when Curriculum is unavailable. Do not invent missing Curriculum, change an H2 title/anchor, or bypass any upstream gap rule in this phase.

## 2. Purpose: design the explanation before generating prose

A structurally correct H2 can still produce an ineffective lesson: a technical definition, an asserted WHY, and an example **mentioned** without showing how or why the result follows. The blueprint must plan the learner's **reasoning path**, not merely list content to mention.

For each approved anchored H2 in actual learner-facing order, choose a **proportionate** teaching strategy. A difficult first-contact concept or causal WHY/HOW mechanism needs an explicit proof path; a short transitional/boundary H2 may need only a compact bridge and handoff. Do not force all H2s to have code, business stories, comparisons, diagrams, or a fixed number of paragraphs.

Before planning an H2, inspect the current Roadmap, approved Menu focus, **Step 3 canonical concept-family/teaching-home architecture**, verified earlier prerequisites and earlier H2 learning. For refactor, also inspect the **current valid Knowledge body** to preserve accurate teaching and examples rather than redesigning or duplicating them automatically. Confirm essential technical claims against authoritative specification/docs or actual reproducible behavior when necessary. The blueprint cannot manufacture a new curriculum, technical fact or learner prerequisite.

**Integrated preview handoff:** the reviewable Step 3 Roadmap → concept-family → actual H1/H2 preview is **structural evidence for the approved Menu**. Reuse its real concept home, objective and prerequisite order to choose the correct intent-specific teaching plan, but **verify against the current approved source**. The preview alone cannot prove an H2 is taught, replace the per-H2 worked explanation, change a stable identity or excuse an unapproved topic. If the preview and current Menu disagree, route a concrete Step 3 `MENU GAP` rather than quietly adapting the lesson to the wrong structure.

**Anti-fragmentation handoff:** the `WHY / ORIGIN / TAXONOMY / LEARNING JOURNEY` teaching playbooks describe **how to teach an approved H2 with that genuine primary intent**, not an instruction to create one anchored H2 per playbook. Check whether a narrative/orientation H2 could be explained naturally **inside** the adjacent foundation/concept H2 without losing a distinct learner capability. If so, record a Step 3 `MENU GAP`; do not protect an unnecessary title merely by writing an elaborate blueprint for it. For example, an OOP chapter with separate H2s both saying “procedural rules are scattered” and “OOP puts rules in responsible objects” may need one substantive comparison/motivation rather than two overlapping teaching units. Do not silently merge approved anchors in Step 4; route the structural change upstream.

## 3. Required per-H2 blueprint decisions

Keep a compact record keyed to the **real chapter path + anchored H2 ID**; never rely only on a generic title. The author must be able to answer all of the following for each H2 (use `not applicable + reason` where genuinely irrelevant):

| Design decision | Concrete question to resolve BEFORE writing |
| --- | --- |
| **Focus / learner outcome** | What is this H2's approved learning focus, and what could a newcomer explain, predict or trace **immediately after this H2**? |
| **Concept-home / prerequisite handoff (read-only Step 3 check)** | Which approved canonical concept/family and Roadmap outcome does this H2 realize? Is this the actual first full teaching home, a deeper treatment, a bridge or a cross-concept application? Have real essential prerequisites appeared BEFORE this H2? Report a `MENU GAP` when Step 3's approved decomposition cannot sustain the lesson rather than filling it with unrelated glossary concepts. |
| **Milestone learning-role handoff (read-only Step 3 check)** | For the specific milestone outcome, is this H2 a **PRIMARY direct-teaching contributor**, a genuinely **PREREQUISITE** foundation already taught before dependent use, or a **RELATED** comparison/extension? One H2 can serve multiple different outcomes for different reasons; a category listed in `relatedKnowledge` alone proves none of these roles. If no approved H2 can teach a claimed milestone outcome, raise `MENU GAP` instead of treating navigation as teaching evidence. |
| **Title-to-outcome intelligibility (read-only Step 3 handoff check)** | At this point in the learning order, could a beginner infer the H2's actual subject/angle from its visible title **before reading the body**, using only already taught concepts? If the title is technically valid but too abstract, misleading or indistinguishable from adjacent titles, record a `MENU GAP` for Step 3 **rather than silently renaming** an approved anchored H2 in this pre-authoring phase. |
| **Named concept / unique teaching focus (read-only Step 3 handoff check)** | When this H2 primarily teaches a recognized named technical concept, can a learner **recognize and later look it up** from the actual visible title? Which distinct intent/outcome does this H2 teach compared with its adjacent lessons? If the technical name was paraphrased away, or adjacent H2s only repeat the same teaching in different wording, flag a Step 3 `MENU GAP` rather than fabricating a canonical term or silently changing the approved Menu. |
| **Starting knowledge / vocabulary** | Which terms have truly been introduced in previous H2s/prerequisite modules? Which new essential term needs a plain-language, first-use bridge **here**? |
| **Initial mental picture / motivation** | Which short situation, observation, problem or comparison makes the concept intelligible? If this is first-contact core WHAT, can the H2 give a usable basic meaning **on its own**? |
| **Teaching sequence and causal bridge** | What are the actual intermediate reasons, dependencies or steps between the example's initial conditions and its result? Why does each important decision/state/relationship matter? |
| **Evidence and verification** | What makes the explanation credible: worked trace, observable Java output/exception, valid pseudocode, diagram, counterexample or authoritative conceptual evidence? How will significant claims be checked? |
| **Evidence-to-explanation mapping (when using multiple representations)** | If a real-world story, pseudocode/Java, state trace, diagram and/or printed output describe the **same** example, which important elements correspond, which path executes for the stated input, and how does the observed result substantiate the learning point? Avoid separate illustrations that merely sit beside each other. |
| **Misconception / boundary** | What likely wrong inference needs correction? Is a neighboring mechanism/paradigm comparison useful, and where is its deeper treatment owned? |
| **Synthesis / transferable takeaway** | What essential understanding should the learner retain **after** the explanation or example? When useful, how will the H2 reconnect concrete evidence to a concise, technically accurate general principle, without merely repeating the initial definition? |
| **Continuity / next handoff** | How does this H2 build on previous teaching, avoid duplicating other H2s and prepare the next approved learning focus? Should an existing example evolve rather than restart? |
| **Comprehension proof** | What short beginner question would reveal whether this H2 actually taught its assigned focus? Can the **planned explanation itself** answer without importing hidden knowledge or borrowing later H2 content? |

**Blueprint depth is risk-based:** all H2 IDs must be accounted for, but this is not a requirement to reproduce every decision as a heading or full paragraph in each planned record. Expand the plan for essential WHAT, causal WHY, tricky HOW, state transitions, concurrency, runtime behavior, or known misconceptions. For a straightforward already-founded topic, a concise plan is sufficient **if** the author and reviewer can still see its specific outcome and evidence.

## 4. Intent-specific teaching blueprints — WHAT question is this H2 answering?

The universal decisions in Section 3 are **not enough**: a definition, an explanation of *why*, a walkthrough of *how*, and a learning roadmap demand **different forms of teaching evidence**. Before authoring each H2, select the **primary learner question / teaching intent** from the playbooks below. Add only genuinely needed **secondary intents** when Step 3 has already approved their focus. Decide from the real learning outcome and scope, **not keyword matching, a question mark, the section title alone, or an automatic WHAT/WHY/HOW label**. Descriptive H2 titles remain the Step 3 contract.

Use these playbooks as **minimum design decisions for that intent**, not mandatory visible H3 headings, identical prose templates, fixed paragraph counts or new artifacts. A section may combine intents; plan one coherent explanation without duplicating earlier/later lessons. If an important learner question cannot fit the approved H2 focus or learner order, report a `MENU GAP` rather than forcing it into an unrelated H2. If a genuinely new milestone is needed, route a `ROADMAP GAP` instead.

### 4.1 Concept / definition — “X là gì?” (WHAT / SCOPE)

**Plan the answer:**

1. **Plain-language meaning:** a precise definition followed by an explanation understandable at the declared beginner baseline; no circular definition using X itself.
2. **Essential properties and boundaries:** what makes X this concept, what is not necessarily part of X; distinguish a nearby misconception only when useful.
3. **Small concrete mental picture:** one situation, value, diagram, snippet or analogy that actually illustrates the defining property, with its interpretation; no decorative example label.
4. **Progressive example interpretation, when behavior makes the concept visible:** plan the **specific important steps or observations** of the chosen small example, including before/after values where relevant, and say **which observed feature demonstrates the definition**. A code snippet plus a final number or an unexplained arrow is not enough when the key feature is a transition. Keep this at introductory depth; reserve full mechanism/implementation for an approved HOW H2.
5. **Real-world-to-code bridge, when both are used:** align the meaningful everyday actions and constraints with the corresponding **specific** code condition, command and observable result. For one selected input, tell the reader which branch actually executes and which does not; show how the printed output or final value follows. Code **without** this interpretation, or a story whose steps do not match its code, is not sufficient teaching evidence. A non-code conceptual illustration is equally valid when it communicates the concept better.
6. **Concept-revealing variation, when a contrast is genuinely instructive:** change **one meaningful variable, order, condition or assumption** in the same example; predict both outcomes and explain **which concept property the difference reveals**. Use this selectively for state-, sequence-, condition- or dependency-sensitive concepts. Do not force counterfactuals for descriptive/taxonomic concepts, or move a full WHY/comparison lesson into WHAT.
7. **First-use vocabulary:** define prerequisite terms locally if they have not really been taught; defer advanced mechanisms to their approved H2s.
8. **Concept synthesis / key takeaway:** plan a brief, technically accurate return from the concrete illustration to the **general mental model**, including a useful boundary or misconception when necessary. The takeaway must add conceptual clarity beyond copying the opening definition. It can be integrated naturally into prose; do not mandate a `Conclusion` heading or repetitive summary for every short H2.
9. **Comprehension evidence:** a newcomer can paraphrase X, point to the observation that demonstrates its essence, and — **where a variation is used** — explain why that one change affects the outcome, **without borrowing explanations from later H2s**.

**NOT READY:** a glossary sentence plus an unexplained arrow diagram; a real-life process and Java block with no mapped connection between their steps and claimed output; runnable-looking code with no interpretation of the behavior it was selected to teach; a variation shown as two outputs with no explanation of the distinguishing property; or a closing sentence that simply repeats the opening definition.

**Illustration (not a fixed Java-code requirement):** with `x = 10`, applying `x = x + 5` and then `x = x * 2` changes the stored value `10 → 15 → 30`. A short first-contact explanation identifies **commands operating on the current value in sequence**. Swapping those two updates instead produces `10 → 20 → 25`; the difference makes the role of command order visible. A concise synthesis connects the example back to imperative programming as **describing an execution process through commands that may change state**, without teaching all assignment/control-flow internals or implying every command must mutate state.

### 4.2 Explanation / motivation — “Tại sao X quan trọng / cần X?” (WHY / CAUSE)

**Plan the answer:**

1. **Question and situation:** specify the real consequence, purpose, limitation or practical question being explained; do not fabricate a historical “problem X was invented to solve” for a naturally occurring mechanism.
2. **Contrasting conditions:** choose two meaningful situations, actions or counterfactuals **where the distinction affects the claimed outcome**.
3. **Causal steps:** spell out the dependency or intermediate event connecting the changed condition to the changed outcome, including unchanged state when relevant.
4. **Consequence and principle:** state what differs, **why** it differs, and which general rule the example supports.
5. **Boundary / exception:** identify when the claimed dependency does **not** apply so the reader does not learn an absolute false rule.
6. **Teach-back:** learner can predict and justify the result of a small variation, not merely repeat “X is important.”

**NOT READY:** “X matters because it is useful” or “checking first avoids errors” with no contrasting trace. **Illustration:** checking withdrawal `130` against balance `100` before subtraction leaves `100`; subtracting before validation produces `-30`. Explain *why* discovering the error afterward does not automatically reverse the earlier update. This is one sequential example, not a concurrency guarantee.

### 4.3 Mechanism / process — “X hoạt động như thế nào?” (HOW / FLOW)

**Plan the answer:**

1. **Starting conditions:** required input, configuration, state and entry event; define what must already be true.
2. **Ordered mechanism:** identify meaningful operations, decisions/branches, iterations and ownership/participants as applicable; do not list API names in place of explanation.
3. **Intermediate trace:** track the value, state, request, control handoff or other observable change at crucial steps; explain why each step follows.
4. **Output and observation:** show what the user/program can observe and distinguish the output from persistent state when relevant.
5. **Important alternatives and limits:** failure, skipped path, nontermination, edge condition, or behavior the example does not prove, when relevant.
6. **Verification:** check technical evidence through trustworthy sources or executable behavior; runnable Java claims must match actual context, version and expected output.

**NOT READY:** a lifecycle/API list or `A → B → C` without explaining what flows between stages, the decision conditions and the outcome.

### 4.4 Components / relationships — “X gồm gì và chúng liên hệ thế nào?” (RELATION / CORE MODEL)

**Plan the answer:**

1. **Relevant components or concepts:** only real parts/roles required for this learning focus; do not invent a universal “three components” model for unrelated subjects.
2. **Each component's responsibility:** what each one contributes, holds, decides or changes.
3. **Interaction and dependencies:** who produces/consumes information, what can affect what, and which relations are essential versus incidental.
4. **One joined-up example or diagram:** walk through how the pieces cooperate to obtain an outcome, not independent dictionary entries.
5. **Ground each central role in that example:** identify the **particular** value, operation, condition, event or code fragment that realizes each role; distinguish a value being read, a value being mutated, and a branch being selected when relevant. Do not misleadingly label every expression as a state-changing command or claim every command changes state.
6. **Boundaries / handoff:** distinguish this model from language-specific implementation or a neighboring module's owned detail.

**NOT READY:** a vocabulary list that leaves the reader unable to explain how the parts work together, or a worked example in which none of the named components is tied to a specific observable role.

### 4.5 Starting point / learning journey — “Bắt đầu từ đâu, học gì tiếp, tại sao theo thứ tự đó?” (PREREQUISITE / LEARNING JOURNEY)

**Plan the answer:**

1. **Starting point:** state concretely what the learner must already know or be able to do, **what is not required**, and where missing essentials are actually introduced. Critical prerequisites must be available **before first dependent use**; do not first disclose them at the end of an introductory chapter.
2. **Destination:** a concrete end capability, not “understand the module” or “have a good foundation.”
3. **Meaningful milestones:** summarize the **actual approved upcoming chapters/focuses in real learner-facing order**, at the right granularity; do not invent a competing Roadmap or promise a topic that the current curriculum never teaches.
4. **Why each transition exists:** for significant `A → B`, identify what ability/insight A establishes that B builds on, or what new complexity makes B necessary; “then / next / finally” alone is not an explanation.
5. **Progressive illustration where useful:** show one small task/example evolving across stages (e.g., change a balance → validate a withdrawal → handle many requests → organize methods → verify behavior). Each stage must add a distinct capability, not just name a Java keyword.
6. **Stage-level outcome + demonstration:** for each significant milestone, plan an **observable learner ability** (explain, trace, implement, predict, debug or justify) and a suitably small practice task or observation that could demonstrate it. Distinguish this preview from the full exercise instructions belonging in the appropriate later Knowledge/assessment; do not merely enumerate syntax or lesson titles.
7. **Progressive project / integration when worthwhile:** if a small running project is genuinely useful, map `starting version → incremental capability at each relevant milestone → integrated final behavior`. Make each addition reuse previously learned concepts and respect what has not yet been taught. This is **conditional**, not a required Java console project, database, API, repository artifact or capstone for every module.
8. **Completion / readiness:** plan a small set of learner-observable exit checks that align with **actual approved outcomes**, including an explanation/trace and an application/verification when material. The learner should know **how to judge understanding**, rather than merely counting finished chapters. Do not promise an auto-scored checkbox feature or new Quiz/metadata schema.
9. **Depth / pacing / learning resources:** differentiate essential foundations from optional advanced extensions (and verified external-module prerequisites); avoid making an illustrative architecture, library, tool or framework mandatory without curriculum evidence. Quote study time only if useful and **clearly estimated from declared assumptions**, never as a universal duration. Curated authoritative further reading may be signposted using the existing module Reference / relevant section references **when available and appropriate**, not fabricated or copied into a second bibliography.
10. **Learner orientation:** make clear whether this H2 is an **overview**, not the full teaching of every future topic; show the next practical starting action and what learners can explain/do after key milestones.
11. **Teach-back:** learner can say where to start, what comes next, **why** the progression makes sense, what practice demonstrates progress and how to know the approved destination has been reached; the answer must be possible from this H2 and already verified prerequisites.

**NOT READY:** a paragraph that only names `variables → branches → loops → procedures` without prerequisites, progression logic, demonstrated learner outcomes or destination; a list of exercises disconnected from taught stages; a capstone that secretly depends on unapproved advanced material; or an asserted fixed-duration course unsupported by learner assumptions. **Illustration:** understand how one value changes and demonstrate it with a short trace **before** reasoning about conditions that read it; next implement repeated decisions, organize growing logic into procedures, and finally verify combined behavior against expected properties. An incrementally developed account simulator may make these connections visible, but only if suitable for the curriculum. A prerequisite briefing that comes after its first dependent use is a structural/first-use issue, not something this final H2 can retroactively repair.

### 4.6 Comparison / distinction — “X khác Y ở đâu, chọn khi nào?” (COMPARISON / TRADE-OFF)

**Plan the answer:**

1. **Purpose and dimension:** say exactly *what* is being compared (e.g., level of control, data mutation, responsibility, abstraction), avoiding an apples-to-oranges comparison.
2. **Shared task/context:** compare X and Y under equivalent conditions, using a paired example or compact table when useful.
3. **Specific behavior/benefit/cost:** explain why their approaches or outcomes differ, not merely attach “better/faster/simpler” labels.
4. **Overlap and limitations:** identify where they coexist or neither implies the other's absence; avoid portraying paradigms as mutually exclusive by default.
5. **Decision guide / understanding check:** learner can justify a suitable choice for one scenario and explain a case where the contrast is not decisive.

**NOT READY:** “Imperative = HOW, declarative = WHAT” alone, or a false claim that declarative programs cannot have ordered execution or stateful implementations.

### 4.7 Application / practice — “Khi nào và làm thế nào áp dụng X?” (USE / PRACTICE)

**Plan the answer:**

1. **Appropriate conditions:** prerequisites and signs the concept/technique fits this situation.
2. **Task and target result:** a small, feasible problem with explicit input/output or acceptance condition.
3. **Decision/actions:** an intelligible example the learner can follow or attempt, based only on concepts already taught.
4. **Observation and feedback:** how to tell whether it worked; show meaningful result or failure signal.
5. **Limits / alternatives:** a case where another choice is better or extra safety is needed.

**NOT READY:** code copied without a problem statement, expected behavior or interpretation; exercises that require unintroduced advanced concepts.

### 4.8 Correctness / risks / debugging — “Làm sao biết đúng, sai ở đâu?” (VALIDATION / LIMIT)

**Plan the answer:**

1. **Expected property:** describe a testable condition, invariant, precondition, postcondition or risk in the concept's actual scope.
2. **Normal and breaking cases:** choose an ordinary case plus a boundary/counterexample that can reveal a defect.
3. **Trace and root cause:** show where the assumption, step, state or dependency leads to an incorrect result.
4. **Repair or mitigation:** explain which change addresses the actual cause, and what it cannot guarantee.
5. **Recheck:** use a test, assertion, trace or justified proof suited to the lesson, without claiming an unexecuted test passed.

**NOT READY:** a list of pitfalls with no failure mechanism, or “add a test” without stating what should be verified.

### 4.9 Historical origin / evolution — “X xuất hiện từ đâu, vì sao hình thành và phát triển thế nào?” (ORIGIN / HISTORY)

**Use when historical emergence or evolution is an APPROVED learning focus**, not automatically for every WHAT or WHY. Distinguish the historical question **“why/how did it emerge?”** from the conceptual question **“why is it useful/important?”**. A concept, architectural mechanism and programming language may develop alongside one another without being the same invention or having one shared inventor/date.

**Plan the answer:**

1. **Direct, historically qualified answer:** open with what can actually be asserted about the origin: a documented invention with identifiable contributors, a gradual evolution, or a later-recognized conceptual category. Explicitly reject a false single inventor/date/predecessor narrative when applicable.
2. **Earlier context and concrete friction:** show the relevant earlier practice, technical/social constraints or learner-accessible historical situation. State **which limitation existed then**, rather than projecting modern software engineering concerns backward without evidence.
3. **Relevant changes and evidence-backed chronology:** choose only decisive events/periods with verified dates, names, inventions or documents. Explain **what specifically changed at each milestone**, and what remained unchanged. Do not list historical names/years as a substitute for explanation.
4. **Causal links without post-hoc invention:** make clear whether an earlier limitation **motivated** a documented development, merely provided context, or is the writer's explanatory analogy. Chronological succession alone is not evidence that A caused B.
5. **Category and terminology boundaries:** explicitly distinguish adjacent but different histories such as hardware reconfiguration vs stored-program architecture, machine instructions vs high-level languages, and those technologies vs the **imperative paradigm** itself. Do not assign an architecture's first demonstration date to the birth of a programming style.
6. **Concrete before/after illustration when helpful:** contrast how a representative task could be expressed or programmed in two historically appropriate contexts; explain the meaningful difference. Modern Java/pseudocode can **illustrate the underlying idea**, but must be labeled a **modern teaching analogy**, never presented as period-authentic source or evidence for an early machine's capabilities.
7. **What persisted / present relevance:** reconnect the historical change to the concept's current essence and uses only as far as this H2's approved scope permits. Avoid a separate full mechanism, language-history or application lesson inside a short origin section.
8. **Source and uncertainty discipline:** verify nontrivial firsts, dates, named contributors, technical capabilities and disputed priority using reliable historical/primary or institutional sources. Handle competing definitions of “first”, later revisions and ambiguity explicitly. Use the existing section/module Reference contracts where appropriate; a source label, invented quote or unverified URL is not evidence.
9. **Teach-back:** learner can explain **what preceded X, what demonstrably changed, why the change mattered and what the milestones do NOT prove**, without equating neighboring concepts or imagining a one-day invention.

**NOT READY:** “X was invented to replace paradigm Y” with no historical evidence; a timeline that conflates enabling architecture, particular languages and a programming paradigm; a named first/inventor treated as uncontested despite conflicting source definitions; or modern code described as literal historical implementation.

**Illustrative distinction (not a required timeline):** original ENIAC programming relied substantially on cable/switch configuration; 1945 EDVAC design documents discussed storing instructions as data; Manchester Baby executed a program stored in electronic memory in 1948; later languages such as Fortran made instruction-oriented programming more expressive at a higher level. These are **related changes at different layers**, not proof that the imperative paradigm itself was invented in 1945, 1948 or with Fortran. An author must verify the precise historical claims and qualifications against trustworthy sources before publishing; omit distracting chronology when the approved teaching focus is only a short conceptual WHY.

**Source examples for fact-checking the illustration (not a required bibliography in every lesson):** [Computer History Museum — Programming ENIAC](https://computerhistory.org/blog/programming-the-eniac-an-example-of-why-computer-history-is-hard/), [Computer History Museum — 1945 EDVAC draft](https://www.computerhistory.org/timeline/1945/), [University of Manchester — Manchester Baby](https://www.curation.cs.manchester.ac.uk/computer50/www.computer50.org/mark1/new.baby.html), and [IBM — Fortran history](https://www.ibm.com/history/fortran). In particular, distinguish ENIAC's original cabling from its **later changes**, and distinguish claims about the **first stored-program demonstration** by the precise meaning of “stored” rather than erasing qualified/contested early work.

### 4.10 Concept taxonomy / knowledge landscape — “X có những nhóm khái niệm nào, khác cấp độ ra sao?” (TAXONOMY / MAP)

**Use when the approved focus is an overview or classification of several concept families**, not merely one component interaction or one cause-and-effect mechanism. This differs from **4.4 RELATION / CORE MODEL**: a relationship lesson explains **how the parts work together**; a taxonomy lesson explains **why ideas belong to distinct groups/levels, where they overlap, and how the learner should navigate them**. An H2 may legitimately need both, but must not become a second, unapproved course outline.

**Plan the answer:**

1. **Scope and classification question:** define what is being mapped and **which criterion** creates each category (role, level of abstraction, relationship type, design goal or implementation mechanism). Distinguish “parts of an instance” from “principles of the paradigm” and “relations between instances/types”; a shared topic does not make all items equivalent parts.
2. **A small, justified group structure:** choose a **manageable set of genuinely different groups** supported by the approved Roadmap and verified sources. Avoid arbitrary grouping simply to mimic “three groups”, “four pillars” or a listicle; state whether the map is introductory/representative rather than an exhaustive canon.
3. **Group-level meaning before item inventory:** explain what question **each group as a whole answers**, then introduce its essential members at novice depth with accurate, non-circular descriptions. Distinguish foundational concepts from later mechanisms, and define unavoidable new vocabulary at first use.
4. **Cross-level relationships and overlap:** show how items/levels relate, complement each other or appear in multiple views. Do not portray **inheritance as simultaneously a design slogan and a class/type relation without explaining the difference**, or imply every OOP program must use inheritance, mutable state or all familiar “pillars”.
5. **Ground the map in one coherent example where useful:** point from each **central group or key term** to the same situation, object/behavior or accurately labeled diagram/code; include at least one meaningful interaction or distinction so the map is more than independently defined rows. A type/class, a runtime object, an abstract principle and a relation must not be illustrated as though they were interchangeable kinds of entities.
6. **Representation integrity:** distinguish an **illustrative possible object** or conceptual diagram from objects that the provided code **actually creates**. If two instances are drawn to show distinct identities/states, either construct both in the code or label one as hypothetical; ensure shown state matches the time and operations demonstrated. Do not claim a partial class plus free-floating statements is standalone runnable Java.
7. **Depth / ownership handoff:** mark which concepts receive only a first-use orientation, which approved H2/chapter deepens them, and which belong to another module (e.g., a Java-language mechanism versus a language-independent paradigm). Avoid copying deep syntax, design-pattern or implementation lessons merely to make an overview feel comprehensive.
   When a long syllabus mixes many owners, distinguish **deep teaching here / brief prerequisite bridge / optional related direction / out-of-scope omission** for the material groups. A keyword named in this overview does not imply this module owns its full implementation, and a concept may reappear later for a genuinely different purpose without requiring an identical explanation.
8. **Synthesis / teach-back:** learner can name the **basis of the groups**, classify representative concepts correctly, explain how two levels connect, and recognize a category mistake without relying on later specialized chapters.

**NOT READY:** a flat list mixing `class`, `object`, `encapsulation`, `composition` and `dynamic dispatch` as though all were the same kind of “component”; a table with correct definitions but no reason for grouping or any relationship among categories; or an overview that appears complete while quietly omitting a material approved category. Conversely, a focused **object identity/state/behavior/collaboration** H2 is NOT required to reproduce every OOP principle or Java feature: it should make **those approved core elements understandable and related**, then hand off deeper groups to their actual owners.

**Illustration (classification, not a required OOP template):** one OOP learning landscape may distinguish **object model** (identity, state if any, behavior/responsibility), **design principles** (encapsulation, abstraction), **collaboration/structural/type relations** (composition, subtyping, inheritance where appropriate), and **language mechanisms** (Java classes, constructors, interfaces and dispatch). These are different analytical lenses, not a claim that all languages have classes or that four groups form an exhaustive OOP definition. In a `BankAccount` example, two accounts with the same balance can retain distinct identities; an account decides the validity of its own withdrawal; a payment object requests this behavior under a contract. Deeper Java syntax or subtype mechanisms belong to the relevant approved later lesson/owner.

**Intent selection rule:** for each H2, record its **primary intent**, only the necessary secondary intents, the **specific required evidence/teaching moves from the matching playbook(s)**, and a short learner question to verify them. When an H2 has a genuinely distinct role not covered here (e.g., a brief chapter transition), derive a fit-for-purpose blueprint from Section 3 and explain the outcome; **do not misclassify it merely to fill a playbook**. Shared prerequisite, technical accuracy, source-grounding, localization and continuity obligations remain in force for every intent.

## 5. The worked-explanation contract

For any H2 whose learning outcome involves an important cause, process or outcome, the blueprint must explicitly plan a **reasoning chain**. Select a form suitable to the topic:

```text
starting situation / input / current knowledge
    ↓
decision, operation or core conceptual relationship
    ↓
intermediate change / reason / dependency (show the missing bridge)
    ↓
observable result or justified conclusion
    ↓
why this result demonstrates the H2's exact learning focus
```

A blueprint that only says `example: bank withdrawal; explain ordering; add Java code` is **NOT READY**. It names teaching ingredients without specifying the mechanism the learner should observe.

**Cross-representation bridge when useful:** a plan that combines everyday steps, actual code and output should be traceable as `real situation → meaningful actions/constraints → matching code decisions and updates → selected execution path → result → concept`. For example, starting balance `100`, withdrawal `30`: “check whether amount is positive and affordable” corresponds to `amount > 0 && balance >= amount`, the successful path updates `balance` to `70`, and the displayed balance must be `70`. If the condition were false, the subtraction would be skipped. Keep the explanation proportional to the H2: an introductory WHAT only needs a short bridge, whereas a dedicated HOW can develop the complete trace. This mapping is **conditional on the representations actually used**, not a requirement to add Java to every lesson.

Use the **appropriate Section 4 playbook** to decide what counts as evidence. This trace pattern is especially useful for causal WHY, HOW and correctness/debugging. A WHAT H2 may need only a small meaning-bearing example; a RELATION H2 may need an interaction model; a COMPARISON H2 may need matched scenarios; a LEARNING JOURNEY H2 needs a **progression of learner capabilities and justified prerequisite transitions**, not necessarily a runtime state trace.

Do not require a code block in a language-independent paradigm lesson, a bank example in unrelated modules, one illustration per H2, or a rigid WHAT→WHY→HOW template repeated everywhere. Evidence is substantive only when it enables the learner to **see or reason why** the claim holds.

## 6. Worked blueprint example — Imperative Programming (illustrative only)

Approved H2: **`Vai trò của việc kiểm soát từng bước thực thi`** (WHY). This is a **design plan**, not the final learner-facing Knowledge body, a mandated topic, or a change to the approved Menu.

| Blueprint field | Specific lesson plan |
| --- | --- |
| Learner outcome | Explain why changing the order of dependent commands can change program behavior, and identify when order may not matter. |
| Earlier baseline | Learner has a simple understanding of an instruction and a variable; if `state` has not yet been taught, explain it locally as the value held **at that moment**, not as an assumed advanced term. |
| Opening situation | Simplified sequential balance check: initial `balance = 100`, withdrawal request `amount = 130`. |
| Case A (check first) | Test `amount > 0 && balance >= amount` → false → refuse withdrawal → **balance remains 100**. |
| Case B (subtract first) | Execute `balance -= 130` without prior validation → balance becomes **-30**; discovering invalid balance afterward does not automatically undo the update. |
| Causal explanation | The subtract command mutates the value that later code observes. Changing the order of a validation and its dependent action changes **which state is allowed to occur**, not just the printed message. |
| Nuance / counterexample | Independent assignments to unrelated variables may be reordered without changing final values; imperative does not mean every command must be globally ordered by the author or executed manually. |
| Evidence | Side-by-side traced values `100 → 100` versus `100 → -30`, plus small clear pseudocode or validated Java **only if it helps**. |
| Important scope limit | This models one sequential in-memory calculation. It is **not** a sufficient implementation of a real bank transfer; concurrent withdrawals require additional transaction/consistency protection. Do not claim this simple check alone prevents race conditions. |
| Handoff | This WHY section prepares deeper discussion of commands, state and control flow; it should not duplicate their complete later chapters. |
| Teach-back question | “With 100 available and 130 requested, what happens in each order and why does a later error message not reverse an earlier subtraction?” |

The final H2 may present this in paragraphs, a table, a small code snippet or an annotated flow, whichever teaches the causal relationship best. **It must not collapse the explicit trace back into** “check before subtract, otherwise the balance can be wrong.” That is a conclusion without the evidence path.

## 7. Mandatory Teaching Blueprint Gate — BEFORE full Knowledge prose

The **current Step 4 author** must check the completed plan **before substantive body generation/refactor**. Do not pass simply because every H2 has a row or every design-field name appears.

```text
[ ] Every approved chapter/H2 identity is accounted for in the actual learner order.
[ ] Every material planned H2 has a legitimate canonical concept-family/topic home,
    an approved milestone learning outcome and a clear first-teaching / deeper-treatment
    / bridge role; no missing prerequisite is hidden by the blueprint prose.
[ ] Visible H2 titles allow the declared learner to anticipate the teaching focus before
    reading the body; expert-only vague/abstract naming is routed to Step 3, not hidden
    behind a good Step 4 explanation or repaired by silently renaming an anchor.
[ ] Named-concept H2 titles preserve discoverable standard terminology with first-use
    clarity as needed; adjacent H2s have distinguishable learning outcomes, not merely
    differently worded descriptions of the same technical idea.
[ ] Each H2 has an intent selected from its actual approved learning outcome, not its title alone;
    the corresponding intent-specific blueprint decisions are concrete, or N/A is justified.
[ ] Important first-contact WHAT H2s contain a planned locally intelligible meaning/mental picture.
[ ] For a core WHAT example relying on observable behavior, the plan interprets the key steps
    and connects the observation to the defining property; a selective one-factor variation
    is justified where it materially reveals the concept, not required by default.
[ ] When one illustration uses scenario, code and/or output, the intended actions map to
    actual expressions/commands, the chosen path and result agree with the stated input,
    and the learner can explain the connection rather than reading detached examples.
[ ] Where synthesis improves retention, the plan reconnects the example/evidence to an
    accurate generalizable mental model without a redundant closing heading or paragraph.
[ ] Important WHY/HOW outcomes have specific steps/causal bridges and verifiable evidence;
    not only generic example labels, definitions, diagrams, or asserted conclusions.
[ ] RELATION H2s anchor each key component's role in the shared example and explain
    interactions; COMPARISON H2s show real dimensions and overlap;
    APPLICATION / CORRECTNESS H2s specify an outcome and how it is checked when relevant.
[ ] ORIGIN / HISTORY H2s separate historical context, verified changes, causal claims and
    present-day interpretation; claims about "first", dates, inventors and enabling technologies
    are source-grounded and do not conflate different conceptual/technical layers.
[ ] TAXONOMY / MAP H2s separate concept families by justified classification criteria,
    explain representative items and cross-level links, preserve actual module/depth ownership,
    and do not turn a scoped overview into a misleading exhaustive or flat feature list.
[ ] For cross-module topic inventories, the planned depth/hand-off is clear: local-owned
    teaching versus essential short bridge versus optional orientation versus exclusion;
    no deep duplication solely to inflate apparent coverage.
[ ] LEARNING JOURNEY / PREREQUISITE H2s establish the real starting point BEFORE dependent use,
    actual approved milestones, meaningful transitions, concrete destination and learner orientation;
    they do not merely list future topics or create a competing Roadmap.
[ ] For material LEARNING JOURNEY milestones, the plan connects the approved progression to
    observable abilities and proportionate practice/readiness checks; any incremental project,
    deeper extension, study-time estimate or curated further reading is grounded and optional
    rather than a new compulsory curriculum, schema or fixed number of stages.
[ ] Essential vocabulary is introduced before consequential use, with verified prerequisites.
[ ] The illustration demonstrates its H2 learning outcome; no example chosen only for decoration.
[ ] Hard distinctions, counterexamples, boundaries and accuracy risks are identified where relevant.
[ ] VI/EN plans have the same learning outcomes, concept order and evidence meaning;
    language-specific wording and pedagogy can remain natural.
[ ] Chapter narrative and reusable examples progress; adjacent H2s do not simply restate each other.
[ ] Every narrative/orientation H2 has a distinct substantial learner outcome that would be
    lost if folded into a concept lesson; five chapter-entry coverage groups or ten
    teaching playbooks have NOT been mechanically transformed into extra H2 titles.
[ ] The plan fits the APPROVED Menu/H2 scopes without changing anchors, adding content owners or
    moving deep later lessons into first-contact sections.
[ ] At least the critical entry WHAT/WHY, LEARNING JOURNEY and high-risk mechanism plans can pass a short
    beginner teach-back using **planned** evidence, not the author's unexplained expertise.
```

If an item fails, **revise the teaching design and recheck** before full authoring. This is an **internal Step 4 pre-authoring readiness check**, not an additional independent review or new parent-level phase/barrier. Existing Step 4 initial authoring and the independent review rounds **required by the active orchestration** remain intact. The independent reviewers later inspect the **current complete Knowledge**, including every critical core first-contact H2, rather than declaring success because a blueprint exists.

**Gap routing:**

```text
approved H2 can support the lesson, but its teaching strategy/example/causal bridge is weak
→ improve Step 3.5 design; realize and validate in Step 4 Knowledge

cannot teach correctly without new/split/reordered chapter/H2 identity or changed focus
→ MENU GAP → return to canonical Step 3; re-approve structure, then redo affected blueprint

missing Roadmap milestone or essential milestone-level prerequisite order
→ ROADMAP GAP → Step 2

wrong learner baseline, module ownership, deep external dependency or curriculum boundary
→ CURRICULUM GAP → Step 1
```

Do **not** silently rewrite Step 3 Menu during blueprint work, treat the blueprint as permission to change canonical sources, or defer a known structural defect until Step 9.

## 8. Working-note lifecycle and review integrity

- The blueprint is a **pre-authoring working plan**, keyed to current H2 IDs. It may be held in the active Step 4 work notes/report or an explicitly approved **untracked/temporary** task note when needed for context handoff. This rule requires the **decisions and gate**, not a particular persistent file path, YAML schema, generated artifact or learner-facing section.
- Do not put planning rows, private teaching prompts, gate markers or duplicated draft prose inside final Knowledge solely to satisfy this contract. The canonical product remains `readme/{lang}/menu/**/*.md` and the existing applicable metadata/relations.
- A resumed Step 4 author must verify the plan against **CURRENT** approved Menu and current already-authored body; reuse a still-valid plan, repair drift, and avoid unnecessary reauthoring of valid Knowledge. If a structural upstream change occurs, redo only the affected blueprint decisions after proper approval.
- Step 4 reviews must use the actual complete Markdown and authoritative evidence as the decisive source. A polished plan **does not prove** that the final lesson teaches the intended content; conversely a good existing lesson need not be rewritten just to mirror a particular example in the plan.
- Follow current sole-writer ownership, worktree isolation, reviewer independence, validation and final commit authority. Step 3.5 itself does **not** change worker eligibility, cross-child barriers or the authorized commit workflow.
