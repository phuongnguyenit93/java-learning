# Step 3 - Menu Rules

Step 3 dùng cho **cả new module và existing/refactor module**. Mục tiêu là ổn định Menu/chapter/title skeleton từ Step 1 Curriculum + Step 2 ROADMAP đã approve trước khi viết/refactor full Knowledge.

**Mandatory shared pedagogy:** áp dụng `rules/MODULE_CONTENT_RULES.md` cho learner baseline, foundational progression, descriptive H1/H2 và cross-module plain-text bridges. **STEP 3 sở hữu cách materialize, phân bổ và review các learning focus đó qua H1/H2**. Menu đúng schema/anchors nhưng không dạy được nền tảng hoặc có title kiểu Quiz/Interview vẫn là **MUST FIX**. Áp dụng cho cả VI/EN, new và refactor modules.

### General Agent Rules + Curriculum Map context guard

Trước khi thực hiện Step 3:

```text
Nếu context của GENERAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENERAL_AGENT_RULES.md trước khi tiếp tục
```

Ngoài General Agent Rules, Step 3 phải đọc và nắm context của Curriculum Map liên quan trong đường dẫn sau **khi file đó tồn tại**:

```text
./temp/*_CURRICULUM_MAP.md
```

và ROADMAP Step 2 đã được review/approve của target module khi artifact đó tồn tại/đã được thực hiện cho workflow hiện tại.

Resolve Curriculum Map theo module path/area thực tế. Không chọn file chỉ theo tên gần giống.

Step 3 phải hiểu ít nhất:

```text
Step 1 Curriculum
→ module responsibility / ownership / boundary / area dependency

Step 2 ROADMAP
→ milestone order / prerequisite / module learning journey

Existing Menu khi có
→ historical structure / reusable content identity / migration evidence
```

Nếu Curriculum Map không tồn tại và Step 3 được gọi như một **isolated Menu review/refactor**, không block chỉ vì thiếu file. Dùng current Roadmap/Menu/Knowledge/source/reference/metadata, neighboring-module ownership evidence và authoritative external documentation/real-world behavior để re-evaluate Menu từ first principles. Current content là migration evidence, không phải curriculum authority tuyệt đối.

Nếu đang chạy canonical full flow bắt đầu từ Step 1 thì không dùng fallback này để skip Step 1/Curriculum. Nếu isolated fallback vẫn để lại ownership/boundary conflict thật sự không thể resolve, report **CURRICULUM CONTEXT GAP**. Nếu ROADMAP đã tồn tại nhưng Menu thực sự cần một **learning milestone mới** mà ROADMAP không có, report **ROADMAP GAP** thay vì silently redefining the Roadmap.

Không được nhầm `ROADMAP milestone` với `Knowledge H2`. Một approved ROADMAP milestone có thể cần được Step 3 phân rã thành nhiều anchored H2 để learner có đủ vocabulary, mental model và learning sequence trước khi Step 4 viết body. Việc phân rã một milestone đã tồn tại thành các learner-facing Knowledge identities **không phải ROADMAP GAP** nếu không làm thay đổi learning goal, order, ownership hoặc boundary đã được ROADMAP approve.

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

```text
NEW MODULE
→ execute Step 3

REFACTOR MODULE
→ execute Step 3 exactly like a new module
→ additionally inspect the existing Menu/Knowledge structure
→ preserve/reuse compatible structure only when real body content or downstream semantic dependencies justify it
→ restructure incompatible Menu before Step 4
```

### Existing Menu content-presence rule

For Step 3, distinguish **real lesson body content** from mere structural scaffold.

Counts as real body content:

```text
explanatory prose inside a lesson/section
code/example blocks
tables/lists that teach substantive behavior
failure/trade-off/evidence discussion
other learner-facing explanation below the section identity
```

Does **not** count as body content:

```text
H1 chapter title
H2/H3 section title
anchor id
folder/file name
generated internal Menu links
<details> / back-to-top / navigation boilerplate
empty section scaffold
TODO/placeholder text
section title alone, even when it sounds descriptive
```

If an existing Menu file/tree contains only titles/anchors/scaffold and **no real body content inside its sections**, treat that existing Menu as **reference only**. Its current chapter count, paths, titles, anchors and ordering are **not fixed constraints**.

In that case Step 3 must re-evaluate the Menu from first principles against the Step 3 pedagogical criteria and may freely:

```text
REMOVE unnecessary chapter/section identities
ADD missing chapter/section identities
REORDER learning progression
RENAME titles/anchors/paths
SPLIT or MERGE chapters/sections
REPLACE a weak historical decomposition with a better one
```

Do **not** keep a title-only Menu rigid merely to preserve historical shape. Existing empty scaffold is cheaper to replace now than to force Step 4 Knowledge into a bad structure later.

If real body content **does exist**, its valid semantic content creates a migration/no-loss obligation, but it still does not make the old Menu structure immutable. Step 3 may still move/rename/split/merge/reorder structure when needed; it must simply map and preserve valid body meaning so Step 4 can migrate it safely.

Canonical source chủ yếu: `module/.../readme/{lang}/menu/**/*.md`.

---

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

### Materialized chapter + lesson-title scaffold

Step 3 không dừng ở H1-only planning.

Sau khi chapter structure đã được xác định từ Curriculum + ROADMAP, mỗi Markdown file trong:

```text
readme/{lang}/menu/**/*.md
```

phải materialize trước ít nhất:

```text
localized H1 chapter title
        +
toàn bộ planned anchored H2 lesson titles
```

Ví dụ:

```markdown
# Generics

## <a id="generic-purpose">Generics: Khái niệm và mục đích</a>

## <a id="generic-types">Generic Types</a>

## <a id="generic-methods">Generic Methods</a>

## <a id="type-erasure">Type Erasure</a>
```

Ở Step 3:

```text
H1
→ phải tồn tại

anchored H2 titles
→ phải tồn tại thật trong Markdown
→ không chỉ tồn tại dưới dạng planning note

full lesson body
→ chưa viết
→ thuộc Step 4
```

Mỗi anchored H2 **sau khi đã được Step 3 hiện tại review/approve** mới trở thành stable Knowledge identity chuẩn bị cho Step 4 và downstream relations.

Một anchored H2 cũ chỉ tồn tại dưới dạng title/anchor/scaffold, chưa có real body content và chưa được current Step 3 re-approve **không tự động là stable identity**. Step 3 được phép remove/rename/reorder/split/merge identity đó khi pedagogical review cho thấy structure khác tốt hơn.

Không tạo placeholder body kiểu:

```text
TODO
Coming soon
Generated later
```

Nếu body chưa tồn tại thì để section rỗng để repository task tạo scaffold chính xác.

Trước khi các relation downstream phụ thuộc vào path/anchor, Step 3 là thời điểm ưu tiên để finalize:

```text
chapter numbering
directory name
Markdown filename
H1 title
anchored H2 id
anchored H2 visible title
```

Sau khi Video path mapping, `readmeRelated`, Quiz/Interview relations, Knowledge metadata hoặc API ordering đã phụ thuộc vào path/anchor, rename/reorder sẽ trở thành reference migration và phải update/validate mọi exact relation liên quan. Video mirror dùng cùng relative path của Knowledge; đổi path ở Step 3 sẽ tạo Video path mới và có thể để lại Video cũ như orphan cần migrate thủ công.

Không chạy `syncMetadataReadme` chỉ để tạo metadata rỗng trước khi anchored H2 identities thực sự được chốt.

---

## Build Menu + title only

This step applies to **both new and existing/refactor modules**.

Derive the detailed Knowledge skeleton from the approved Curriculum + ROADMAP when those artifacts exist. In isolated fallback mode without a Curriculum Map, use the reconstructed scope/boundary obtained from current repository evidence + authoritative real-world knowledge:

```text
approved Curriculum boundary when present
or isolated-fallback reconstructed boundary
        ↓
approved roadmap milestone when present
or current Roadmap evidence reviewed in this isolated step
        ↓
Knowledge category / chapter
        ↓
localized chapter title
        ↓
planned section titles / stable identities
```

Create the Menu/chapter structure and titles, but **do not write the full official Knowledge content yet**.

The purpose is to stabilize:

```text
chapter order
folder/file names
localized H1 titles
planned anchored H2 identities
roadmap → Knowledge structure
```

This is the lowest-cost point to correct chapter numbering, file placement and title structure before downstream metadata and relations depend on them.

**Handoff to Teaching Blueprint:** approved chapter/H2 identities, their learner-facing order and assigned learning focuses are the fixed inputs for the mandatory **Step 4 pre-authoring phase** defined in `STEP_3_5_TEACHING_BLUEPRINT.md`. Step 3 does **not** write the blueprint's worked examples or full Knowledge prose. If the blueprint reveals that a required teaching focus cannot fit any approved H2, route the structural defect back to Step 3 instead of inventing new H2s in Step 4. This handoff is **not** an extra canonical Step or parent STEP barrier.

---

## Pedagogical decomposition từ ROADMAP sang Knowledge H2

Step 3 không chỉ kiểm tra structural correctness của Menu. Step 3 phải bảo đảm **pedagogical structural correctness** trước khi Step 4 bắt đầu viết Knowledge body.

Không áp dụng máy móc:

```text
1 roadmap milestone
→ 1 chapter
→ 1 H2
```

ROADMAP milestone là learning goal cấp cao. Step 3 chịu trách nhiệm phân rã learning goal đó thành số lượng Knowledge section vừa đủ để learner có thể học theo một chuỗi rõ ràng.

Ví dụ:

```text
ROADMAP milestone
→ "Understand why the Spring IoC Container exists"

Step 3 có thể materialize thành:
→ Spring IoC Container: Concept and Role
→ The Problem Solved by a Managed Container
→ Spring Beans and Their Lifecycle Context
→ Managed Beans and Plain Objects
→ Inversion of Control (IoC): Core Model
→ Dependency Injection as an IoC Mechanism
```

Các H2 trên vẫn cùng phục vụ một milestone nếu chúng không tạo learning goal mới ngoài ROADMAP.

### Canonical Concept Architecture Gate — BEFORE choosing chapter/H2 titles

**Knowledge architecture precedes menu wording.** Within the **approved** Step 1 module ownership and Step 2 milestone outcomes/order, build a compact **concept-to-teaching coverage map** in the existing Step 3 working/review reasoning, **before** inventing H1/H2 visible names or grouping lessons by similar-looking titles. For each material module-owned concept or concept family, resolve:

1. **Canonical identity and aliases:** its **verified, recognized technical term** (when one exists), natural localized display name, relevant synonyms/aliases and meaning; do not invent an official standard term or assume English translation is canonical in every locale. This is a semantic identity for planning, **not** a newly persisted `conceptId` or required schema.
2. **Parent/family and type of relationship:** which real conceptual family or chapter topic it belongs to, and whether links to neighbors mean **part-of / kind-of / uses / contrasted-with / prerequisite / application**. These relationships are not interchangeable; similarity of words does **not** establish parentage or learning order. Classify by educational/technical relationship, not by the phrase the AI happened to generate for a title.
3. **First intelligible teaching home and depth:** identify the **primary place within this module** where essential WHAT and relevant mechanism/relationship are actually introduced, versus optional bridges, later deeper treatment or a different module's canonical owner. A single concept may legitimately recur for a different outcome; repeated **full definitions** in unrelated H2s are not the desired structure. Do not mistake one concept = one H2, or first brief mention = mastery.
4. **Real prerequisite and progression:** distinguish prior concepts strictly needed to understand this item from useful background; prove that essential vocabulary and mental models arrive **before** the H2 that depends on them. Identify where sibling concepts can branch after common foundation without inventing an artificial single-file dependency chain.
5. **Learning objective and section role:** what unique learner capability the concept contributes, whether the planned H2 teaches **WHAT, WHY, HOW, RELATION, APPLICATION or a deliberate combination**, and why adjacent H2s will not duplicate each other's teaching. A chapter title is a meaningful family; H2 titles are discoverable lessons with a specific focus, **not** an unrelated glossary or interchangeable task prompts.
6. **Milestone-to-H2 traceability:** for every important Step 2 outcome, locate one or more prospective chapter/H2 teaching homes; for every proposed H2, point **back** to an approved milestone outcome and ownership. Expose missing owned concepts, orphan H2s, hidden prerequisites and misleading grouping **before** materializing scaffold or approving stable anchors.

**Role-aware learning relation test:** for each major milestone **outcome** (not merely each roadmap node), explicitly distinguish in the existing Step 3 design/review result: **PRIMARY TEACHING** — the actual one or more H1/anchored H2 teaching homes that introduce or develop the *new capability*; **PREREQUISITE KNOWLEDGE** — previously taught essential concepts/H2s whose understanding is required before the primary content; **RELATED / EXTENSION** — useful comparison, optional enrichment or separate deeper lesson not required to claim milestone completion. The concept's first principal home and the milestone's direct teaching contributors are different questions; the same chapter/H2 may legitimately be PRIMARY for more than one distinct objective if its real teaching covers each. A chapter can include several H2s with different roles. Do not automatically classify every H2 of a linked chapter as PRIMARY or require a single PRIMARY H2 per milestone/concept.

**Prerequisite evidence test:** a prerequisite must be established **earlier in the learner's valid path**, by exact existing prior H2 teaching or a verified prerequisite module, not just named in another title, mentioned in a related list or placed nearby. If a needed foundation exists only later, repair first-use placement inside Step 3 scope or route genuine milestone/ownership gaps to Step 2/1. An optional related concept must not be silently treated as a completion requirement. Preserve legitimate revisits at different learning depths without repeating whole lessons.

**Planning illustration, not a new repository format:** milestone about maintaining valid object state → conceptual family `Encapsulation` → focused teaching for `Encapsulation` / `Data Hiding` / `Object Invariants` **only when each needs distinct learning depth**. If Java `private` semantics are owned by another module, introduce only enough context for this paradigm lesson and defer Java language details. Prefer `Data Hiding — Che giấu dữ liệu` to a vague title that hides the standard concept; do not split a focused, coherent explanation solely to make the inventory longer.

**Gate:** Step 3 must fix any **Step 3-owned** grouping/identity/teaching-home/first-use gap before H2 approval; a genuine missing milestone/dependency routes to `ROADMAP GAP` (Step 2), and a cross-module ownership conflict routes to `CURRICULUM GAP` (Step 1). Do not create or write new persistent `conceptId`, `canonicalName`, `parentTopic`, `prerequisites`, `knowledgeRefs` or UI data models as a workaround; the current stable chapter path + anchored H2 identities, Roadmap schema and relation contracts remain authoritative. This audit can be concise and recorded within the **existing Step 3 result**, not as a new tracked planning artifact, mandatory extra review round or generator stage.

**Concept-first H2 materialization — separate SUBJECT from EXPLANATION:** every proposed H2 must have one **defensible primary teaching unit**: (A) a recognized technical concept/family with canonical discoverable naming, or (B) a clearly distinct substantive comparison, historical explanation, problem analysis, process, practice/decision or synthesis outcome. A unit in category B is not a loophole to turn `WHY`, `purpose`, `concept map`, `where to start`, `relation to Java`, or each Roadmap slogan into standalone H2s merely because they are important to mention. Unless there is an independently teachable and approved learning outcome, place such orientation **inside** the relevant concept-focused H2 (or a concise appropriate existing entry section); then verify its presence in Step 4. An existing H2 focused on `OOP` can legitimately introduce why OOP matters, its object mental model and the next learning steps **in one coherent lesson**. Do not create an additional H2 that simply rephrases the same motivation. Conversely, do not compress a genuine technical topic into a one-line glossary entry or omit a separate HOW/comparison that is substantive and necessary.

**H2 necessity / merge-split challenge:** before creating or keeping each anchored H2, ask: **what would a learner specifically fail to understand or do if this H2 were merged into its nearest concept lesson?** If the answer is only “we need a WHY paragraph / title for learning journey / outline of later chapters”, the H2 is not independently warranted. Compare neighboring lessons' **actual promised learner abilities**, not merely whether their titles differ. If two lessons compare the same previous approach to the same OOP benefit, merge or re-scope unless they provide provably different analyses. A module with real scope and prerequisite needs may require many H2s; neither a fixed maximum nor an artificial quota of canonical-name H2s is permitted.

### Reviewable Integrated Roadmap → Menu Design Preview (Step 3 evidence)

**Do not leave the concept architecture only in the author's head.** In the **existing Step 3 author/reviewer result**, provide a compact **learner-order preview** that makes the Stage/Progression → approved Roadmap milestone → concept family/chapter → focused H2 lessons relationship inspectable **before accepting the Menu scaffold**. A major module can use a grouped table or indented outline; smaller modules need only a concise readable mapping. This is **not** a new tracked blueprint, a new hierarchy in the Roadmap schema, an extra canonical Step, or a Portal UI requirement.

For each approved milestone and its material learning concepts, the preview must make the following independently checkable:

- **Roadmap anchor:** actual approved milestone ID/name and its measurable learner outcome; group into broad stages/waves **only when such grouping helps explain progression**, not because an external example used four stages. Stage names in the preview are explanatory and do not create new curriculum nodes.
- **Topic/concept structure:** one or more meaningful proposed chapter/concept families and the **canonical recognized terms** to introduce within them, including their **primary teaching home versus short bridge/later deep-dive elsewhere**. Do not treat `Class`, `Encapsulation`, `Composition` and `Dynamic Dispatch` as interchangeable component types.
- **Lesson decomposition:** proposed or current H1 + anchored H2 identities/titles and **one distinguishing learner outcome per meaningful H2**. A concept may be taught coherently in one H2 or through several H2s with different intents; do not force a one-concept/one-H2 mapping or invent filler lessons.
- **Prerequisite and reason for order:** essential already-taught knowledge and the explanatory reason why major groups follow one another; clearly label a **strict prerequisite** versus a sensible default presentation order or optional parallel branch.
- **Boundary and depth:** concepts owned deeply by this module, first-contact contextual bridges, and advanced topics owned elsewhere; mark material omissions, excessive repetition, unapproved scope or orphan H2s as findings rather than silently adding content.
- **Practice/readiness preview:** for significant chapter/milestone transitions, a compact indication of **what a learner can do or explain afterward and how to check it**; detailed exercises belong to Step 4 and later assessments.
- **Three learning roles for each important outcome:** identify the **PRIMARY teaching H2(s)** that actually build the approved skill, **PREREQUISITE** concepts with their verified earlier teaching homes, and useful **RELATED/EXTENSION** chapters/H2s (if any). Give a short teaching-evidence rationale so the roles cannot be inferred from word similarity or chapter co-location; no role needs a new persisted relation field.

**Illustration only — partial OOP row, NOT a mandated OOP Roadmap:**

```text
Approved milestone: state-behavior-and-invariants
  Goal: explain how a valid object state is protected by its published behavior
  Family / chapter candidate: Encapsulation (object-state boundary)
  Canonical concepts to teach: Encapsulation; Data Hiding; Object Invariants
  Prerequisite: basic Object + State meaning already introduced
  Proposed H2 teaching homes:
    PRIMARY: Encapsulation — Why external changes need a boundary [CONCEPT / WHY]
    PRIMARY: Object Invariants — Conditions that must remain valid [WHAT / APPLICATION]
    PREREQUISITE: earlier Object + State introduction (verify actual H2/source)
    RELATED: later Java access-modifier mechanism (different owner; optional deep dive)
  Demonstration: trace one valid update and one rejected invalid update
  Handoff: detailed Java access modifiers belong to the verified Java owner
```

**Two review moments within the existing Step 3 author/review work (not new rounds):** (1) compare the proposed map **against the real approved Roadmap and references** before creating H2 scaffolds; (2) after materializing/refining the actual Menu, reconcile the preview with **every current H1 + anchored H2 ID/title** in learner order, not just the examples or changed lines. **Both directions** must hold: each important approved outcome has a real teaching home, and each actual H2 is justified by an approved outcome. A reviewer must inspect current source and record concrete gaps; copying an author's preview without checking current files is NOT review. For VI/EN, verify semantic concept/learning-order parity without assuming identical wording, IDs or counts where the existing language contracts do not require them.

**Concept vs narrative balance check:** mark the H2s that **teach the first usable meaning of a core concept** and the H2s that merely **discuss the course, roadmap or benefits of concepts taught elsewhere**. The latter cannot be accepted as full conceptual coverage; require a compelling distinct outcome for each such H2. In particular, a first chapter consisting predominantly of broad motivation, boundaries, concept lists and orientation while actual named building blocks only appear in later chapters must be checked for **unnecessary narrative fragmentation** and delayed first-contact teaching. This is a qualitative gate, not a rule that every first chapter must teach all later detailed topics or reach a fixed concept-to-narrative ratio.

**Current Roadmap binding boundary:** `relatedKnowledge` maps milestones to **same-module Knowledge categories**, not individual H2s and not a typed PRIMARY/PREREQUISITE/RELATED relationship. A shared category link may be useful at several milestones while different H2s inside it are the actual direct teachers. Review the exact current H2 homes separately; reject a milestone with **only navigational/related** support and no content capable of teaching its unique objective. Conversely, do not delete a legitimate repeated `relatedKnowledge` entry just because categories overlap. Never invent `primaryKnowledge`/`prerequisiteKnowledge` YAML keys or pretend the present Portal shows role-specific badges; implementing a typed UI would require a separately authorized schema/Portal change.

**Pass condition:** a novice looking at just the ordered chapter/H2 titles should recognize the broad concept families and important standard terms, see meaningful progression rather than a flat collection, and reasonably anticipate what each lesson teaches. This **title-only usability test complements**, but does not replace, the concept/ownership/dependency checks or actual Knowledge teaching in Step 4. A richly formatted or collapsible Menu does not compensate for incorrect underlying concept architecture.

**Multi-level conceptual overview vs focused H2:** when a learner asks for the “components” of a broad topic, check whether the approved Roadmap actually contains **distinct conceptual levels** (e.g., entity model, design principles, relationships and implementation mechanisms). Step 3 assigns each level to a coherent chapter/H2 focus and first-use order, with a compact overview/bridge where useful; do not cram an entire multi-chapter taxonomy into an H2 approved only to introduce one core model, or silently leave a genuinely required level untaught. Conversely, do not split every glossary term into its own H2 or treat language-specific syntax as part of a language-independent paradigm without ownership evidence. For an overview H2, ensure its title/focus clearly promises **orientation** rather than full deep teaching; for a focused H2, require enough room for the meaningful relationship among its named concepts.

**Breadth-vs-depth Menu gate for overlapping subjects:** apply Step 2's broad-syllabus ownership/depth decisions when converting an extensive external checklist to H2s. Materialize in-depth H2 identities for **module-owned** outcomes; give legitimately needed outside terms a concise first-use bridge inside a relevant H2, not their own duplicate chapter; merely orient/signpost optional related subjects where genuinely helpful; exclude unrelated advanced features. Do not copy an entire sibling module's design-pattern, language-mechanism or testing syllabus merely to appear complete. Nor may this rule excuse omitting a module-owned foundation necessary to understand approved later H2s. When two related H2s seem repetitive, confirm they teach **different capabilities, viewpoints or levels of depth** before retaining both. Preserve the existing approved Roadmap and stable-anchor migration contracts.

### Step 3 learner baseline and foundation-focus gate

Follow `rules/MODULE_CONTENT_RULES.md` as the **sole source of shared learning-content quality**: assume no prior understanding of module-owned core concepts unless a verified prerequisite establishes it; teach WHAT/WHY/RELATION before depending on HOW; do not treat the title of a module/H2 as proof that its meaning was taught.

**Step 3's own deliverable is structural**, not full Knowledge prose. Using Step 1 ownership, approved Step 2 Roadmap and real prerequisite evidence, allocate each important term/learning focus to appropriate **existing or newly planned anchored H2 identities**, in learner-facing order:

- Record mentally/in the review result which H2 establishes **WHAT**, which explains **WHY**, and which establishes **RELATION**, before the first H2 using the concept for advanced HOW.
- One H2 **may carry both WHAT and WHY** when its focused scope allows; neither a distinct H2 per label nor literal WHAT/WHY words in the title are required. But missing foundational focus is a **MUST FIX**, not an excuse to hide it in an unrelated technical section's future body.
- Distinguish terms already taught by real prerequisites from terms only named in an upstream module. Provide an entry/bridge focus where needed, or report the proper Curriculum/Roadmap gap.
- Before approving Menu, ask whether Step 4 can fully realize each focus **without adding/splitting/reordering H2**. If not, fix the Step 3 skeleton before handoff.

For example, the Spring Boot application-runtime entry flow should introduce the meaning of application runtime and why its lifecycle matters **before** Boot-specific hooks. The *motivation for understanding runtime* can be taught **inside** an `Application Runtime — Khái niệm và phạm vi` H2; actual next H2s should identify distinct subjects such as `SpringApplication — Vai trò trong khởi động ứng dụng` or `SpringApplication — Vòng đời và các giai đoạn chính` **only when each adds its own approved capability**. Do not automatically create an additional standalone `Vai trò của runtime trong quá trình thực thi ứng dụng` merely to give WHY a separate H2.

For a foundational term **owned by another module**, Step 3 identifies the first suitable H2 where Step 4 can author a short **plain-text contextual bridge** under the shared rule. Do **not** add a special navigation H2, module link, route, `relatedModules`, `readmeRelated` or metadata solely to refer to the other module. This optional prose bridge never replaces a genuine missing prerequisite.

### Chapter 1 mandatory minimum — extensible foundation, NOT a fixed H2 template

**Mỗi new hoặc refactor module bắt buộc có một Chapter 1 / first learner-facing chapter đủ làm điểm vào cho người mới học chủ đề đó.** Chapter 1 là vị trí mở đầu **thực tế theo generated Menu order**, không nhất thiết tên file là `1.Introduction` hay H1 phải viết `Tổng quan`. Đây là **minimum learning coverage**, không phải maximum content, giới hạn số H2 hay template đặt tên.

Khi Step 3 materialize các anchored H2 của Chapter 1, phải bảo đảm người học có thể tiếp cận **tối thiểu năm nhóm nội dung sau** trong Chapter 1:

1. **WHAT — Khái niệm và phạm vi:** chủ đề/module này thực chất là gì, không phải là gì, phạm vi module sở hữu. Không được dùng tên chủ đề như một thuật ngữ hiển nhiên người học đã biết.
2. **WHY — Mục đích, vai trò và động cơ học:** vì sao concept tồn tại hoặc vì sao quan trọng phải hiểu nó; vấn đề/hạn chế/tình huống thực tế mà nó giúp giải thích hoặc xử lý. Không sáng tác một `problem solved` giả cho những chủ đề mang tính mô tả tự nhiên như application runtime.
3. **CORE MODEL — Các thành tố/khái niệm chính và mối quan hệ:** giới thiệu mental model ban đầu; chỉ nêu thành phần, trạng thái, giai đoạn, abstraction hoặc quan hệ **thật sự tồn tại**. Không ép mọi concept phải có `components` hay `lifecycle` riêng khi bản chất không có.
4. **PREREQUISITE ORIENTATION — Điểm xuất phát của learner:** chỉ rõ tối thiểu vốn từ/kiến thức nào cần để đọc tiếp, giải thích hoặc dành một short bridge cho term thiết yếu chưa được học, phân biệt nội dung module tự sở hữu với module khác. Nếu không cần prerequisite bổ sung, không tạo H2 rỗng mang tên `Prerequisites`; vẫn phải xác nhận entry không có hidden prerequisite.
5. **LEARNING JOURNEY — Bản đồ đường học tiếp:** cho learner biết các nhóm kiến thức/chương tiếp theo sẽ đi theo hướng nào, mỗi chặng phát triển năng lực gì và liên hệ ra sao với foundation vừa học. Bảo đảm có H2 scope phù hợp để Step 4 giới thiệu những bài thực hành/minh chứng năng lực quan trọng và một ví dụ phát triển xuyên suốt **khi được approved Roadmap hỗ trợ**, không buộc mọi chặng có H2 bài tập riêng. Không viết lại toàn bộ ROADMAP, tạo bản ROADMAP khác hoặc bắt buộc một H2 `Lộ trình học` nếu có thể giới thiệu tự nhiên trong focused section.

**Năm nhóm nội dung trên là sàn tối thiểu về NỘI DUNG, không phải năm đơn vị H2.** Step 3 chỉ **bổ sung H2 nhập môn khi có teaching unit thực sự độc lập** cần cho vocabulary, cơ chế, so sánh đáng học, lịch sử có scope riêng hoặc prerequisite; còn phần bối cảnh, vì sao hữu ích, bản đồ nội dung và learning journey ngắn phải được tích hợp tự nhiên vào body của H2 có concept/focus thích hợp. Không được dừng ở đúng năm mục vì đã tick checklist; cũng không được sinh thêm bài mang tiêu đề văn vẻ để đủ từng mục trong năm nhóm.

**Historical WHY vs conceptual WHY focus:** khi approved scope thật sự cần giải thích **nguồn gốc/quá trình hình thành**, dành H2 hoặc một phần focus phù hợp cho câu hỏi lịch sử khác với H2 giải thích **vai trò/lợi ích hiện tại**. Tiêu đề phải phản ánh trọng tâm thật (ví dụ, nguồn gốc và sự phát triển khác với vai trò và mục đích), không ép mọi module thêm chapter lịch sử. Bảo đảm có chỗ để Step 4 giải thích bối cảnh → biến đổi được kiểm chứng → ranh giới khái niệm → ý nghĩa hiện tại, không bắt Step 3 tự viết sử liệu. Nếu việc bổ sung cần milestone mới hay thay đổi ownership, route về Step 2/1 theo contract.

**Materialization contract:**

- Mỗi nhóm phải có **một hoặc nhiều H2 phù hợp về scope** trong Chapter 1 để Step 4 có thể hiện thực hóa nội dung; **một H2 có thể đáp ứng nhiều nhóm** nếu vẫn có một learning focus rõ ràng. Năm nhóm **không đồng nghĩa** phải có năm H2.
- WHAT/WHY và mental model phải có learner-facing **anchored foundation identity**, không được giấu toàn bộ trong BASE.md, một lời mở đầu không anchor, hoặc đoạn prose của H2 chuyên sâu đang giả định người học đã biết chúng.
- PREREQUISITE ORIENTATION và LEARNING JOURNEY có thể được diễn đạt tự nhiên **bên trong body của một H2 nhập môn phù hợp**, không bắt buộc H2 riêng; Step 3 vẫn phải kiểm tra rằng structure có đúng chỗ để Step 4 viết đủ chúng.
- **WHY và CORE MODEL cũng không mặc định cần H2 riêng** nếu H2 nhập môn đã giải thích đầy đủ vấn đề, động cơ, khái niệm và quan hệ cốt lõi. Một `concept map` chỉ liệt kê thuật ngữ, hay bài `learning path` chỉ giới thiệu chương sau, là **supporting exposition**, không phải bằng chứng có một teaching unit độc lập. H2 chuyên về lịch sử hoặc comparison vẫn phù hợp khi có mục tiêu riêng thực chất.
- Dùng **statement/topic-style H1/H2 tự nhiên cho từng module**, không copy các nhãn `WHAT`, `WHY`, `CORE MODEL`, `PREREQUISITES`, `LEARNING JOURNEY` thành title literal của mọi module.
- Nếu một khái niệm quan trọng đã được prerequisite thực sự dạy, Chapter 1 có thể dùng bridge ngắn và đi thẳng vào **góc nhìn mới thuộc module hiện tại**; không dạy lại toàn bộ owner module. Không vì thế mà bỏ qua phần nhập môn của core concept do module hiện tại sở hữu.
- Nếu năm nhóm không thể được phân bổ vào Chapter 1 từ approved Roadmap mà không thay đổi milestone/ownership, report upstream `ROADMAP GAP` / `CURRICULUM GAP` theo đúng owner. Nếu chỉ cần mở rộng, split, merge hoặc reorder H2 trong existing approved milestone, đó là công việc Step 3 bình thường.

**Ví dụ, không phải universal template:**

```text
Git — first chapter
→ Git: Khái niệm và mục đích
→ Repository, Working Tree, Staging Area và Commit
→ Bối cảnh trước Git, động cơ học và hướng sang thao tác Git
  are SHORT explanations inside the relevant H2 body unless they each have
  a distinct, substantial approved comparison or practice objective.

Spring Boot Application Runtime — first chapter
→ Application Runtime: Khái niệm và phạm vi
→ SpringApplication: Vai trò trong khởi động ứng dụng (if owned here)
→ SpringApplication: Vòng đời và các giai đoạn chính (if distinct/approved)
→ WHY, conceptual map and learning-path preview stay in the relevant lesson
  bodies rather than being mechanically promoted into extra H2 identities.
```

Trong ví dụ, prerequisite orientation và preview các chương sau có thể nằm **bên trong H2 nhập môn tương ứng**; ví dụ không có ý nói bốn H2 luôn đủ cho mọi module. Module khó hơn có thể cần sáu, tám hoặc nhiều H2 nhập môn nếu từng H2 có focus riêng và không vượt boundary. Step 4 vẫn là owner viết full body.

**Chapter 1 review gate — mandatory cho mỗi language:** reviewer đọc full Chapter 1 H1/H2 theo thứ tự render, ánh xạ **cả năm nhóm** vào H2 thích hợp, kiểm tra core WHAT/WHY xuất hiện trước API/lifecycle/deep HOW, kiểm tra không có hidden prerequisite, và xác nhận mỗi section đủ scope để Step 4 triển khai. Nếu thiếu bất kỳ nhóm nào mà không có lý do hợp lệ về cách thực hiện trong H2 hiện có thì **MUST FIX trong Step 3**; title-only scaffold hay lời hứa `Step 4 sẽ tự thêm` không phải bằng chứng đạt gate.

**Chapter 1 anti-fragmentation MUST FIX gate:** before approving the first chapter, temporarily remove each standalone `WHY / origin / purpose / concept map / scope boundary / starting point / next journey` title and ask whether its distinct approved learning outcome would be lost if the explanation moved into a real foundational concept H2. If not, merge/rehome the prose focus instead of preserving the standalone H2. Read the **full first-chapter title cluster** and identify needless overlaps (e.g., two H2s both explain why dispersed data/rules motivate OOP); a reviewer must not PASS the chapter solely for satisfying all five coverage groups. Also verify that early concept vocabulary (`Object`, `Identity`, `State`, `Behavior`, or the topic's actual foundational concepts) has understandable first-contact homes in valid learner order, here or in genuinely appropriate early downstream H2s. Never force Java Class/Constructor detail into a language-independent paradigm merely to increase the number of technical headings.

### Step 3 descriptive-title gate

Apply the `MODULE_CONTENT_RULES.md` **descriptive topic/statement title** contract to **every actual H1/anchored H2 in VI and EN**. Choose clear topic identities such as `Vòng đời của SpringApplication` instead of question prompts such as `Có những vòng đời nào?`. Rewrite semantically, not by deleting question marks. Keep each title consistent with the H2's approved WHAT/WHY/RELATION/HOW focus and distinct from neighboring H2s.

**Canonical concept + learning-focus identity gate:** do NOT approve a technically themed H2 merely because its title is short, descriptive and easily paraphrased. For **each actual H2 in the learner-facing sequence**, resolve (without new tracked schema): (a) its actual primary named concept **if one exists**, using reliable terminology rather than invention; (b) the distinct learner question/intent it answers about that concept; (c) the observable new ability the learner gains; (d) the essential first-use prerequisite/neighboring H2 dependency; and (e) its module-owner/depth boundary. If the H2 is primarily a known concept such as `Cohesion` or `Coupling`, its **visible title must make that concept discoverable**, ideally through the established technical label paired with a natural explanatory phrase when needed. If it is truly a workflow/application/comparison, use its real focus instead of manufacturing a named concept. A title like `Tính gắn kết giữa các hành vi cùng trách nhiệm` may describe Cohesion correctly yet remain an inadequate technical-learning index if its actual canonical concept is hidden. Verify VI/EN names are semantically aligned, not machine-translated; preserve stable anchors when only wording changes.

**Menu is the projection of the approved concept architecture, not a second Curriculum/Roadmap:** a Knowledge category/H1 can represent a useful conceptual family and multiple H2s its focused lessons. The learner-facing list should reveal **which broader topic contains this lesson**, what recognized concept/angle it teaches, and its intended order, without requiring a separate Portal UI feature. Step 1 still owns **area-level Curriculum**; Step 2 still owns **module milestones/dependencies**; Step 3 owns **chapter/H2 grouping**. No `Roadmap → arbitrary new Topics → conceptId → H2` schema is implied. The CURRENT Roadmap `relatedKnowledge` links to **same-module Knowledge category ids**, not H2/concept ids, and is a secondary navigation mapping refined after the Menu structure is established. Do not reverse-engineer milestones from that mapping, create one relation per term or assume a new collapsible tree/progress UI exists. Any Portal layout redesign must be separately scoped and implemented by its owner.

**Adjacent H2 learning-identity test:** compare overlapping titles **as a group**, not merely one by one. In particular, `Object Responsibility` (WHAT), `Responsibility Assignment` (HOW/DECISION), and `Cohesion` (RELATION/QUALITY) can merit separate H2s when each has unique taught content and progression, whereas three near-synonymous titles about assigning responsibility without distinct learner outcomes do not. Conversely, do not collapse clearly different named concepts solely because their names share `object`, `responsibility` or `behavior`. Repair unclear scope/split/merge within Step 3 ownership; escalate a genuine milestone/ownership change through the existing Roadmap/Curriculum gap contracts. Do not invent one-H2-per-term quotas or require the WHAT→WHY→HOW→EXAMPLE chain as separate H2 headings for every concept.

When the current Menu has real downstream consumers, preserve an anchor/path if its concept identity is unchanged. Otherwise handle explicit migration and validate dependent Video, API, Quiz, Interview, Roadmap/Knowledge relations under the applicable owner; do not silently break them.

### Khi nào là ROADMAP GAP thật sự?

Step 3 chỉ report **ROADMAP GAP** khi việc tạo Menu hợp lý đòi hỏi thay đổi learning journey ở cấp milestone, ví dụ:

```text
cần thêm một learning goal lớn chưa tồn tại
milestone order hiện tại làm learner phải dùng prerequisite chưa được học
approved milestone bỏ sót một domain/concept lớn
Menu cần một branch/handoff làm thay đổi scope hoặc module boundary
```

Không report ROADMAP GAP chỉ vì một milestone cần nhiều H2 để dạy đầy đủ:

```text
milestone đã có đúng learning goal
        +
H2 mới chỉ định nghĩa vocabulary / prerequisite / relation
        +
không thay đổi ownership / boundary / milestone order
        ↓
đây là trách nhiệm bình thường của Step 3
```

### Step 4 compatibility gate

Trước khi chốt Menu, Step 3 phải tự hỏi:

```text
Nếu Step 4 chỉ được phép viết/refactor body
mà không redesign Menu,
structure hiện tại có đủ để Step 4 thỏa:

WHAT
→ WHY
→ RELATION
→ HOW
→ EVIDENCE
```

Nếu câu trả lời là không, phải sửa Menu/H2 ngay trong Step 3 hoặc report upstream gap phù hợp. Không được chuyển một structural pedagogy gap xuống Step 4 rồi kỳ vọng Step 4 tự thêm section ngoài approved skeleton.

### Mandatory beginner-first / title-style review procedure

Trong mọi authoring pass, independent review và recheck/fix của Step 3, ngoài structural/file validation phải thực hiện **một lần full visible-title audit cho từng language**:

**Title preview / novice comprehension gate:** apply the `rules/MODULE_CONTENT_RULES.md` beginner-first **standalone title clarity test AND canonical-concept discoverability test**, independently of the existing rule against interview-style titles. Reviewers must actually read the current **visible H1/H2 titles as Portal/index cards in learner order**, temporarily without their bodies, and judge whether the declared beginner can infer the lesson's concrete subject/angle and recognize a named technical concept when one is the primary focus. A title does **not** PASS merely because it is grammatically descriptive, short, technically valid, or lacks a `?`. Reject clusters of abstract nominalizations that only make sense after an expert reads the body; check that adjacent labels distinguish real learning focuses rather than repeat synonyms. Preserve accurate technical terms and add a short explanatory angle when it meaningfully improves first contact. If body and approved focus match, fix **visible wording** rather than inventing a new Roadmap milestone/H2 or copying the lesson into the title. Title changes with downstream references follow the existing anchor/migration contract.

```text
1. Read Curriculum ownership + approved ROADMAP and declared prerequisites;
   verify the canonical concept-family coverage and milestone→H2 teaching-home map.
2. Read ALL current H1 + anchored H2 in learner-facing order for that language.
3. Mark each new essential term's first appearance and its introduction/bridge location.
4. Walk through the first chapters as the declared target learner, not as a Spring/Java expert.
4a. Audit the actual first learner-facing Chapter 1 against all FIVE mandatory minimum content groups; allow and require extra foundational H2 when the topic needs them.
5. Check WHAT / WHY / core relations BEFORE lifecycle, internals, configuration or APIs.
6. Evaluate EVERY H1/H2 for BOTH descriptive (non-interview) style AND beginner-first
   standalone clarity: subject + distinguishing angle, useful first-use wording,
   honest approved scope, and distinguishability from adjacent titles.
6a. For named-concept H2s, verify the recognized term is visible and findable;
    for EVERY adjacent cluster, contrast canonical concept (if applicable),
    learning intent, new learner ability, prerequisite order and depth/owner.
7. Identify H2 ownership of the WHAT/WHY/RELATION focus, including any needed plain-text cross-module bridge inside an existing lesson.
8. Verify VI/EN semantic scope/order parity without assuming literal translations.
9. Confirm Step 4 can teach within the approved titles/anchors; no structural rescue needed.
9a. Audit BOTH directions: each approved milestone outcome has real H2 teaching coverage,
    and each H2 can justify its milestone/family/prerequisites and distinct learner outcome.
9b. Independently reconcile the reviewable integrated design preview with CURRENT
    Roadmap + every actual H1/H2; record any mismatch/omission/duplicate/dependency gap.
9c. For each milestone outcome, separate PRIMARY direct teaching H2s from VERIFIED
    prerequisite homes and RELATED context; never derive those roles from an untyped
    `relatedKnowledge` category link or the number of linked categories.
9d. First-chapter anti-fragmentation: try merging each orientation/WHY/meta H2 into
    its nearest concept H2; retain it only if a distinct substantial teaching
    objective would otherwise be lost. Check duplicate motivation comparisons.
10. Classify real defects as MUST FIX, repair Step 3-owned issues, and re-audit the current titles.
```

Không đạt khi review chỉ chạy generator, chỉ so YAML/Markdown schema, chỉ tìm dấu `?`, hoặc chỉ đọc diff mà không đọc current learning journey. **Review #2 phải bắt đầu từ source hiện tại sau fixes**, không chấp nhận checklist/report của Review #1 làm evidence thay cho full title audit. Số vòng review và reviewer eligibility tuân thủ active orchestration; mục này bổ sung nội dung bắt buộc cần review chứ không thay đổi phase barriers.

Nếu title đổi mà anchored H2 id vẫn biểu thị **đúng cùng concept**, giữ id và cập nhật generated internal navigation/language parity. Nếu cần thêm/split/reorder H2 để sửa prerequisite gap, cũng phải kiểm tra exact relations trong `relatedKnowledge`, Video mirror/scene mapping, `readmeRelated`, Quiz/Interview references hoặc metadata nếu chúng đã tồn tại. Không rewrite body theo kiểu Step 4 trong Step 3; lập migration mapping và route downstream repairs tới canonical owner tương ứng.

---

## Generate internal menu scaffold

Sau khi một chapter Markdown file đã có:

```text
H1
+ anchored H2 lesson titles
```

Step 3 phải chạy repository task module-wide:

```text
generateInternalReadmeMenuForModule
```

Task này **không cần location riêng**. Nó derive language từ:

```text
MODULE_LANGUAGE
```

rồi với từng language:

```text
readme/{lang}/menu/
        ↓
recursive scan **/*.md
        ↓
sort deterministic
        ↓
GenerateInternalReadmeMenuService.generate(file)
cho từng Markdown file
```

Vì vậy Step 3 chỉ cần materialize đúng H1 + anchored H2 cho toàn bộ language/menu tree rồi chạy **một task ở cấp module**.

Task module-wide phải fail rõ language + path nếu một Markdown file không có internal section anchor hợp lệ. Không silently skip một chapter malformed vì điều đó có thể che mất Step 3 scaffold gap.

### Single-file task

Repository vẫn cung cấp task:

```text
generateInternalReadmeMenuForFile
```

cho trường hợp cần regenerate/debug một file riêng lẻ.

Task single-file được cấu hình qua:

```groovy
generateInternalReadmeMenuForFile {
    location = 'readme/vi/menu/.../Chapter.md'
}
```

Đây là utility task cho một file cụ thể; **canonical Step 3 module workflow dùng `generateInternalReadmeMenuForModule`** để tránh bỏ sót chapter/language.

Task nhận các section dạng:

```markdown
## <a id="stable-id">Lesson Title</a>
```

và generate/normalize:

```text
<a id="back-to-top"></a>

## Menu
→ links tới các anchored H2

<details>
<summary>Click for details</summary>
...
</details>

Quay lại đầu trang
separators
```

Body rỗng là hợp lệ ở Step 3. Sau khi generator chạy, scaffold có thể có dạng:

```markdown
## <a id="type-erasure">Type Erasure</a>

<details>
<summary>Click for details</summary>

</details>
```

Step 4 mới điền full Knowledge body vào section đã được Step 3 chốt.

### Internal menu generation ownership

```text
Human / AI Step 3
→ quyết định H1
→ quyết định anchored H2 id/title/order

generateInternalReadmeMenuForModule
→ materialize internal navigation/rendering scaffold

Step 4
→ điền Knowledge body
```

Không manually duplicate generated `## Menu`, back-to-top links hoặc `<details>` boilerplate nếu task có thể generate chúng.

Nếu chạy task module-wide lần nữa trên các file đã generate, task phải được coi là normalization/regeneration step; Step 3 vẫn phải kiểm tra diff để bảo đảm human-owned H1/H2 identities và existing compatible body không bị thay đổi ngoài ý muốn.

---

## Build module BASE.md

Sau khi Menu structure và anchored lesson titles đã ổn định, Step 3 phải author/review:

```text
readme/{lang}/BASE.md
```

cho mọi language được khai báo trong `MODULE_LANGUAGE`.

`BASE.md` là **human-owned module orientation content**, không phải generated Menu và không phải nơi chứa full lesson Knowledge.

Nội dung BASE phải được derive từ:

```text
Step 1 Curriculum
+ approved Step 2 ROADMAP
+ finalized Step 3 Menu structure
```

BASE nên giúp learner trả lời ở mức module:

```text
Module này là gì?
Tại sao domain/topic này tồn tại?
Learner cần prerequisite nào?
Các nhóm/chapter lớn là gì?
Chúng liên hệ với nhau như thế nào?
Nên học theo flow nào?
Module kết thúc ở boundary nào?
Concept nào được handoff sang module khác?
```

BASE không được:

```text
copy toàn bộ lesson body
duplicate chi tiết từng H2
thay thế ROADMAP
invent learning flow khác với Menu/ROADMAP
```

VI/EN có thể diễn đạt tự nhiên khác nhau nhưng phải giữ cùng module mental model và learning journey.

---

## Generate LIST.md and final README

Sau khi:

```text
Menu files đã ổn định
generateInternalReadmeMenuForModule đã chạy thành công cho toàn bộ MODULE_LANGUAGE
BASE.md đã được author/review cho từng language
```

Step 3 phải chạy:

```text
generateFinalReadme
```

Task hiện tại:

```text
đọc MODULE_LANGUAGE
        ↓
scan readme/{lang}/menu/**/*.md
        ↓
sort folder/file theo numeric prefix rồi fallback theo tên
        ↓
generate/overwrite readme/{lang}/LIST.md
        ↓
combine:
LIST.md
+
BASE.md
        ↓
generate final README
```

Output hiện tại:

```text
en
→ README.md

vi
→ README.vi.md
```

Điểm quan trọng:

```text
generateFinalReadme
không concatenate full chapter Knowledge vào README cuối

final README
= LIST.md + BASE.md
```

`LIST.md` và final README là generated outputs. Không author trực tiếp generated content khi source Menu/BASE mới là nơi sở hữu nội dung.

Sau khi chạy task phải inspect:

```text
LIST ordering
language parity
expected files xuất hiện
BASE content được ghép đúng
final README output đúng language naming
unexpected diff
```

### Existing/refactor module behavior

Existing modules follow the same target-design process as new modules. The old Menu is additional evidence, not the source-of-truth:

```text
Step 1 Curriculum khi tồn tại
hoặc isolated-fallback reconstructed scope/boundary
        +
Step 2 approved ROADMAP khi tồn tại
hoặc reviewed current Roadmap evidence trong isolated fallback
        +
authoritative technical sources
        ↓
target Menu skeleton
        ↑
existing Menu / existing Knowledge
→ inspect for reusable structure and migration impact
```

Before classifying any existing chapter/section as reusable, first apply the **Existing Menu content-presence rule** above. Title-only/anchor-only/generated-scaffold structure has no preservation privilege and must not be treated as a stable identity merely because it already exists on disk.

For an existing module, audit current chapter/files/section identities as:

```text
aligned
→ keep when compatible

misordered
→ reorder

misplaced
→ move to the correct chapter

overloaded
→ split structure when needed

fragmented
→ merge structure when needed

poorly named
→ rename title/path when migration cost is justified

missing
→ add planned chapter/section identity

out of scope / stale / incorrect
→ nếu có real body content: surface as an explicit finding and preserve/migrate valid meaning before removal
→ nếu chỉ là title/anchor/generated scaffold không có real body content: có thể remove trong Step 3 sau review; không giữ cứng chỉ vì identity đã tồn tại
```

Step 3 may inspect old Knowledge bodies to understand what existing structural identities contain, but **must not perform full Knowledge rewriting**. Full explanation/body refactor belongs to Step 4 after the Menu structure is approved.

### Mandatory authoritative-source rule for Step 3

Step 3 must consult authoritative technical sources just as Step 2 does. Menu design is a curriculum decision, not a file-renaming exercise.

Use official sources to validate at least:

```text
canonical terminology
major conceptual groupings
prerequisite relationships
technology/version boundaries
whether two concepts deserve separate chapters or one coherent chapter
whether an existing chapter is obsolete, misplaced or missing
```

Examples:

```text
Java
→ Oracle Java documentation / Java API documentation
→ JLS / JVM Specification when semantics require it
→ OpenJDK / JEP for version-sensitive behavior

Spring
→ official Spring Reference Documentation

Other technologies
→ official specification / vendor or project-maintained documentation
```

Repository content remains important migration evidence, but it is not the only curriculum authority.

## Completion gate

Step 3 hoàn thành khi, cho cả new và existing module:

```text
Menu structure tương thích với Step 1 Curriculum khi Curriculum Map tồn tại; nếu isolated fallback thì tương thích với reconstructed scope/boundary đã được kiểm chứng từ current content + authoritative real-world knowledge
ROADMAP milestone/order được phản ánh hợp lý
ROADMAP milestone đã được phân rã thành đủ learner-facing Knowledge identities; không áp dụng máy móc 1 milestone = 1 H2
trước khi chốt H1/H2 titles đã kiểm tra canonical concept-family architecture từ approved milestone, ownership, prerequisites và meaningful conceptual grouping; không nhóm bài từ các title do AI tự nghĩ
concept coverage được trace hai chiều từ milestone outcomes → H2 teaching homes và H2 → approved outcome; không còn orphan H2 hoặc concept cốt lõi chỉ được nêu tên mà chưa có nơi dạy
existing Step 3 result chứa learner-order integrated design preview đủ để reviewer kiểm tra milestone → family → canonical concepts → actual H1/H2 focus, prerequisites, owner/depth và năng lực đầu ra
independent reviewer đã đối chiếu preview với CURRENT Roadmap/H1/H2 thực tế (cả VI/EN), không chấp nhận plan đẹp nhưng scaffold/learning progression sai
major milestone outcomes có PRIMARY learning H2s đủ khả năng dạy mục tiêu; prerequisites có bằng chứng đã dạy trước, related content chỉ đóng vai trò bổ trợ; untyped relatedKnowledge không bị nhầm là role/coverage proof
learner baseline + prerequisites được xác định từ evidence, không mặc định core term trong tên module đã được biết
full visible-title audit chứng minh core concept có intro/bridge rõ ràng trước khi dùng ở lifecycle/mechanics/API/decision
entry flow có WHAT + WHY + concrete motivation + foundational relation đủ cho người mới theo đúng module prerequisite
Chapter 1 (first learner-facing chapter) có đủ 5 minimum content groups WHAT, WHY, CORE MODEL, PREREQUISITE ORIENTATION và LEARNING JOURNEY; được bổ sung foundation sections khi cần, không ép fixed H2 count/title
Chapter 1 vượt anti-fragmentation gate: five groups được dạy như nội dung trong concept-led lesson, không nâng từng WHY/purpose/map/learning-path thành H2 vô nghĩa; mọi narrative H2 giữ lại có objective và evidence riêng
mọi nhóm minimum được map vào H2 thực tế có learning focus phù hợp và có thể triển khai body ở Step 4, không chỉ dựa vào BASE/placeholder
WHAT/WHY/RELATION learning focus được phân bổ rõ cho các H2 hợp lệ để Step 4 triển khai; không mặc định một H2 riêng cho từng vai trò
cross-module foundational terms có body-level bridge ngắn tại vị trí thích hợp khi cần; không phát sinh navigation/link/route/metadata chỉ cho lời nhắc văn bản
không còn beginner-critical undefined term hoặc hidden prerequisite trong H1/H2 learning progression
mọi H1/anchored H2 sử dụng descriptive topic/statement titles thay vì question-style Quiz/Interview headings
mọi visible H1/H2 vượt qua novice title-preview check ở đúng learner order, không chỉ technically correct nhưng khó hiểu hoặc lặp lại các cụm danh từ trừu tượng
H2 dạy named technical concept thể hiện thuật ngữ được công nhận đủ để nhận diện/tra cứu, có bridge ngắn khi cần; không ép mọi H2 song ngữ/English hoặc invent canonical names
các H2 gần nhau đã được đối chiếu identity + intent + learner outcome + prerequisite/ownership, không split theo từ khóa hay chỉ khác cách diễn đạt
title naming/semantic granularity/VI-EN semantic parity được review trên toàn bộ current H1/H2, không chỉ generator/diff
chapter order ổn định
paths ổn định
localized H1 titles ổn định
toàn bộ planned anchored H2 identities đã được materialize vào Markdown file
anchored H2 order/title/id ổn định
entry chapter có stable H2 foundation đủ để learner hiểu module/core concept trước khi gặp vocabulary nâng cao
không có foundational term quan trọng bị downstream section sử dụng trước khi được giới thiệu hợp lý
Curriculum terminology ownership đã được phản ánh trong Menu/H2 flow khi module là primary owner; nếu không có Curriculum Map thì ownership không được invent và phải dựa trên repository boundary + authoritative evidence đã review
Step 4 có thể thỏa WHAT → WHY → RELATION → HOW → EVIDENCE mà không cần redesign Menu
generateInternalReadmeMenuForModule đã xử lý toàn bộ chapter files của MODULE_LANGUAGE
internal Menu/details/back-to-top scaffold hợp lệ
BASE.md đã được author/review cho mọi MODULE_LANGUAGE
BASE.md phản ánh đúng Curriculum + ROADMAP + Menu structure khi các artifact đó tồn tại; isolated fallback phải phản ánh reconstructed scope + reviewed Roadmap/Menu hiện hành
generateFinalReadme đã chạy thành công
LIST.md và final README phản ánh đúng Menu/BASE source
existing structural content đã được audit/migrated khi có
không còn structural gap buộc Step 4 phải redesign Menu
final independent reviewer đã đọc lại toàn bộ current learning journey và xác nhận không còn MUST FIX về entry foundation, learning sequence hoặc title quality
```

Downstream:

```text
approved Menu + anchored lesson-title scaffold
+ internal-menu generated scaffold
+ approved BASE.md
+ generated LIST/final README
        ↓
Step 4 Teaching Blueprint Gate (STEP_3_5_TEACHING_BLUEPRINT.md)
        ↓
STEP_4_KNOWLEDGE.md
```
