# Step 4 - Knowledge Rules

Step 4 là canonical authoring/refactor step cho **Knowledge** sau khi Step 3 đã ổn định Menu structure. **Trước khi viết/refactor full Knowledge body, bắt buộc thực hiện Teaching Blueprint Gate theo `STEP_3_5_TEACHING_BLUEPRINT.md` như Phase A của Step 4**, không phải canonical STEP mới.

### General Agent Rules + Curriculum Map context guard

Trước khi thực hiện Step 4:

```text
Nếu context của GENERAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENERAL_AGENT_RULES.md trước khi tiếp tục
```

Ngoài General Agent Rules, Step 4 phải đọc và nắm context của các artifact sau **khi chúng tồn tại trong workflow hiện tại**:

```text
./temp/*_CURRICULUM_MAP.md tương ứng với target module
+ approved Step 2 ROADMAP
+ approved Step 3 Menu structure
```

`STEP_3_5_TEACHING_BLUEPRINT.md` là **mandatory companion rule** phải đọc cho mọi Step 4 new/refactor/isolated authoring. Plan cho từng approved anchored H2, xác định **primary/secondary teaching intent** (khái niệm, WHY, HOW, quan hệ, learning journey, comparison, application, correctness hoặc intent khác phù hợp), áp dụng **intent-specific blueprint** cùng mental model, ví dụ và evidence thích hợp, sau đó **PASS Blueprint Gate trước khi triển khai full prose**. Đừng ép mọi H2 vào cùng template hay dùng riêng tiêu đề để chọn kiểu; đặc biệt H2 Learning Journey phải giải thích điểm xuất phát, sự tiến bộ giữa các milestone và đích đến, không chỉ liệt kê chủ đề. Với isolated review/validation chỉ đọc existing content, áp dụng các tiêu chí comprehension tương ứng mà không yêu cầu tạo lại toàn bộ planning note hay chỉnh sửa file nếu chưa được giao authoring.

Curriculum Map cung cấp area/module ownership và boundary; ROADMAP cung cấp module learning journey; Step 3 Menu cung cấp chapter/path/title/section structure mà Knowledge phải triển khai.

Nếu Curriculum Map không tồn tại và Step 4 được gọi như một **isolated Knowledge review/refactor**, không block chỉ vì thiếu file. Dùng current Roadmap/Menu/Knowledge/source/reference/metadata, neighboring ownership evidence và authoritative external documentation/real-world behavior để validate/refactor Knowledge theo scope thực tế. Existing Knowledge phải được preserve khi valid nhưng không được giữ technical claim sai chỉ vì historical content đã tồn tại.

Nếu đang chạy canonical full flow bắt đầu từ Step 1 thì không dùng fallback này để skip Step 1. Nếu isolated fallback không resolve được ownership/boundary, report **CURRICULUM CONTEXT GAP**. Nếu Knowledge cần một milestone chưa có trong Roadmap hiện hữu, report **ROADMAP GAP**. Nếu Knowledge chỉ có thể đúng bằng cách redesign chapter/path/title structure đáng kể, report **MENU GAP** và quay lại Step 3 thay vì tự tái cấu trúc Menu trong Step 4.

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

```text
NEW MODULE
→ consume Step 3 Menu skeleton
→ Step 3.5 Teaching Blueprint + Gate (inside Step 4)
→ write full Knowledge

REFACTOR MODULE
→ consume the reviewed/refactored Step 3 Menu skeleton
→ Step 3.5 Teaching Blueprint + Gate (inside Step 4)
→ migrate/refactor existing Knowledge into that approved structure
```

Canonical sources:

```text
module/.../readme/{lang}/menu/**/*.md
module/.../src/main/resources/readme/{lang}/knowledge-metadata.yml
```

---

## Refactor behavior

### Existing / refactor module — Refactor Knowledge against approved Menu

For an existing/refactor module, Step 3 has already reconciled the structural Menu with Curriculum + ROADMAP, or with the isolated-step fallback evidence when the Curriculum Map was absent. Step 4 therefore focuses on the learner-facing Knowledge body and metadata.

Audit the old Knowledge against the approved Curriculum when present, the reviewed Roadmap/current scope evidence, and the Step 3 structure. Existing valid knowledge must be preserved while being migrated, rewritten, regrouped, split, merged or expanded as needed.

Allowed operations include:

```text
move existing body content into the Step 3-approved chapter/section
split an overloaded explanation across already-approved section identities
merge fragmented explanations into the approved target section
refine learner-facing wording while preserving stable structural identities
add missing prerequisites, explanations or concepts
reorder explanation flow inside the approved structure
```

Do not silently delete an existing concept merely because Step 3 changed the chapter structure. Preserve the knowledge somewhere appropriate or explicitly classify it as incorrect/stale/out-of-scope for review.

Do not perform a second independent Menu redesign in Step 4. Small mechanical corrections required to make the approved structure valid are acceptable; structural curriculum changes belong to Step 3.

Toàn bộ Knowledge authoring và pedagogy rules trong file này áp dụng như nhau cho new module và refactor module.

## Knowledge authoring

### Build / Refactor Knowledge

This step applies after the Menu/title skeleton is stable in Step 3, regardless of whether the module is new or existing.

Knowledge is the canonical explanation layer for the module.

Write or improve localized Markdown under the module README learning structure. A Knowledge section must use the repository's stable anchored H2 identity:

```markdown
## <a id="stable-section-id">Section title</a>
```

Section ids must be unique within the module/language and stable enough to serve as references from API, Quiz and Interview metadata.

The H2 visible title should behave like a compact learning/navigation label:

```text
short
→ easy to scan in the Portal card/index
→ names the concept, mechanism, lifecycle or other concrete learning topic directly
→ matches the opening focus of the section
→ descriptive statement/topic title rather than a Quiz/Interview-style question
→ intelligible at this point to a learner with only verified earlier knowledge;
   a new technical term can pair with a short plain-language angle
```

Do not make the visible title carry the whole explanation, conclusion, motivation, or contrast. Put those in the body instead.

**Title clarity check during authoring/refactor:** `rules/MODULE_CONTENT_RULES.md` also requires judging **beginner-facing meaning**, not only a short descriptive/no-question style. Before relying on an H2 title as orientation, see whether a learner reading its Portal card could predict the actual lesson focus without consulting the body. If an approved title is abstract, vague, misleading or indistinguishable from neighboring H2s, report its specific `MENU GAP` to Step 3; use the established same-focus/same-anchor wording-refinement process when authorized, preserving VI/EN and downstream relations. A technically excellent body does not make an opaque title usable, but a title need not define its entire technical subject.

**Named-concept title/body consistency:** when an approved H2 owns a recognized technical concept, check that the learner can identify its established term from the title and that the body actually **introduces, explains and grounds that same concept**, including a localized plain-language bridge where necessary. Do not hide all standard terminology behind polished design-language paraphrases or assume a heading alone teaches the concept. Check adjacent H2s contribute distinct capabilities (e.g., understanding a responsibility vs assigning it vs evaluating cohesion). Route real Menu identity/title gaps back to Step 3; do not silently expand the body into a duplicate concept syllabus.

**Approved concept-home delivery:** author each H2 as the actual learning role assigned by Step 3's canonical concept-family map. When it is the **primary first-teaching home**, define and illustrate the recognized concept locally at declared learner depth before relying on it; when it is a later comparison/application, build on its **already taught** meaning and teach the genuinely new skill instead of repeating the original full definition. If a neighboring owner has the deep implementation, give only the necessary first-use conceptual bridge and preserve that ownership. Keep the source's existing chapter path + anchored H2 identity and actual approved milestone; do not invent `conceptId`-based links or treat a generated Menu label as proof of learning.

**Consume the reviewed integrated design preview, not a new syllabus:** when available from Step 3's existing author/reviewer result, verify it still matches the **current** Roadmap and approved actual chapter/H2 identities before writing. Use its concept homes, progression and abilities to select appropriate teaching evidence; do not copy its topic list as Knowledge prose or make it an additional tracked source of truth. A mismatch in grouping/focus/prerequisites is an upstream `MENU GAP` / `ROADMAP GAP`, not permission for Step 4 to silently redesign the curriculum.

**Narrative-support content is not automatically a lesson:** teach WHAT, WHY, core model, prerequisite orientation and learning journey **inside appropriately scoped concept-centered H2 bodies** rather than adding separate H2s to satisfy each instructional concern. When an already-approved H2 is an abstract purpose/overview/boundary/learning-path essay with no independently substantial outcome, or two H2s only restate the same motivation, report a **Step 3 MENU GAP** for consolidation and downstream anchor migration; do not mask the issue by making each body longer or renaming only the title. Preserve a distinct approved origin, comparison or practice H2 if its actual teaching evidence justifies its identity. Step 4 must not silently restructure approved H2s itself.

**Milestone-specific Knowledge-role delivery:** for each approved milestone learning outcome, check that its **PRIMARY** designated H2 contributor(s) actually teach enough to achieve it, using explanations/examples appropriate to their intents; **PREREQUISITE** learning was truly taught earlier; **RELATED/EXTENSION** material is not silently essential for completion. A shared chapter can support multiple milestones with different H2s, and a revisited H2 may build a genuinely different ability. Do not consider a category's mere link in `relatedKnowledge`, a shared heading, or an optional related concept as evidence of a fully taught milestone. Flag missing or incorrectly assigned primary teaching as `MENU GAP`/`ROADMAP GAP` according to ownership; don't inflate Step 4 with an invented curriculum.

Prefer:

```text
OOP: Khái niệm và mục đích
Đóng gói: Nguyên lý và vai trò
Rủi ro của kế thừa
Dynamic Dispatch
OOP: Concept and Purpose
Inheritance Risks
```

over sentence-like titles such as:

```text
Đối tượng là nơi gắn trạng thái với hành vi
Đóng gói không chỉ là dùng field private
Objects as collaborating state + behavior
Encapsulation is more than private fields
```

Title wording may be refined within the **same approved Step 3 focus/identity** without changing the stable anchor id; any real concept/section redesign must be routed to Step 3. VI/EN titles should be naturally localized and conceptually equivalent, not literal copies of each other. The canonical title-style rule belongs to `rules/MODULE_CONTENT_RULES.md`.

### Fulfill the Step 3 learning-focus contract

Apply the shared `rules/MODULE_CONTENT_RULES.md` baseline/WHAT→WHY→RELATION→HOW→EVIDENCE and plain-text cross-module bridge rules **when writing actual Knowledge bodies**. Step 4 consumes each H2's approved learning focus: if one H2 owns WHAT and WHY, **write both**; a title alone is not an explanation. A later lifecycle/API H2 does not have to repeat foundations already taught by an earlier H2 or verified prerequisite.

Inside an existing relevant H2, briefly explain an external module's foundational concept, its relationship to this lesson and, when useful, name the verified owner module **in plain text**. Do not fabricate links, routes, `relatedModules`, `readmeRelated`, extra headings or metadata solely for this bridge. Do not absorb its full curriculum.

**STEP 4 accountability:** missing explanation/evidence within an appropriately approved H2 is a Step 4 content defect and must be fixed here. If an essential WHAT/WHY/RELATION focus has **no suitable H2** or cannot fit the approved order/identity, report `MENU GAP` and return to Step 3 rather than adding an H2 or hiding a prerequisite in an unrelated section. Do not force every H2 to repeat every learning layer.

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

### Author a teachable explanation, not only complete content slots

Apply the **learner comprehension and grounded teaching evidence** contract in `rules/MODULE_CONTENT_RULES.md` Section 4 while writing **actual H2 bodies**. A correct definition plus an isolated WHY sentence or example mention is not automatically a sufficiently taught concept. Check that someone entering with the approved learner baseline can follow the essential meaning, motivation and causal/relational steps **without the author silently supplying missing knowledge**.

For each important module-owned concept, author within its approved H2 focus and valid earlier lessons:

```text
first useful encounter
→ introduce essential terms in plain language before relying on them
→ show a concrete problem/scenario and why the concept matters
→ work through the key actions, decisions or conceptual relationships
→ make the outcome/observation/conclusion explicit
→ connect that evidence back to the concept and the next learning focus
```

This is a **teaching logic**, not a literal H2/H3 template or a requirement to repeat every stage in every section. When an important mechanism has observable behavior, prefer a focused worked example with intermediate steps (such as initial state → operation/decision → changed or unchanged state → observed result). When code is useful, make its relevant outcome intelligible; when code is inappropriate, use a concrete scenario, contrast, diagram or reasoned counterexample. Reuse and progressively extend a small example across related sections where that adds understanding; do not merely restate the same example or definition under multiple H2s.

**Bridge representations rather than stacking them:** when a Knowledge H2 combines an everyday scenario, process description, Java/pseudocode, a trace and/or printed output, explicitly connect the critical real-world actions to the corresponding conditions/operations, explain which branch actually runs for the stated input, and reconcile the final state and output with the teaching point. Do not put a plausible story above unrelated code or show expected output without enough information to derive it. When explaining several related core terms (such as state, command and control flow), point to their **specific roles in the same example** rather than defining them only in a detached glossary. Keep first-contact explanations concise and defer the detailed mechanism to the approved later focus; Java is not required when another representation is clearer.

**First-contact core WHAT H2:** author the approved introductory H2 so its *own body* explains the concept in plain language and offers a small concrete mental picture when a definition alone remains abstract. A learner should understand that basic meaning **without needing to read a later WHY, HOW or comparison section**. Later H2s deepen the idea rather than retroactively supply its first intelligible meaning. This is local intelligibility, **not** a requirement for every H2 to repeat the whole WHAT→WHY→HOW chain or contain standalone executable code. Respect Step 3's approved focus, and report `MENU GAP` if that focus cannot support a viable entry explanation.

**Java example integrity:** if an authored Java block is claimed runnable or is used as proof of actual Java behavior, ensure its syntax, declared dependencies/Java version, initial conditions and resulting output/state/exception agree; prefer a small complete compilation/execution context that a learner can reproduce. Compile/run meaningful examples with an available compatible toolchain when feasible, and report actual validation honestly. Mark conceptual pseudocode and intentionally partial snippets explicitly; do not portray them as standalone runnable code. Do not require Java code merely because the larger project teaches Java: a paradigm or purely conceptual lesson can use an appropriately explained scenario/trace instead.

**Relevant concept contrasts:** where novices may conflate an important concept with another paradigm/mechanism, make the difference observable with a focused comparison or one shared scenario. Explain *what dimension differs*, *where they overlap* and *what the contrast does not imply*; e.g., an imperative-versus-declarative explanation must not imply all declarative implementations have no execution order or state effects. Teach only the comparison needed for this module and expand it in the approved later comparison H2 if one exists; do not silently move that H2's full lesson into an introductory WHAT section or invent an unrelated cross-module lesson.

**Entry-chapter responsibility:** after the introductory Knowledge has been read in its actual order, a beginner should be able to explain the module's core topic in ordinary language and illustrate it with a meaningful example. If a core term first appears there but its definition is deferred to a later chapter, provide the necessary short foundation **at first use** unless a real prerequisite already taught it. The later deep-dive section remains valid and can expand on the earlier introduction. `BASE.md`, a title, Menu link or metadata cannot substitute for this teaching.

**Learning Journey H2 delivery (when owned by the approved Menu):** write more than a list of upcoming concepts. For the important upcoming milestones, explain the specific learner capability gained, **why** the next stage builds on that capability, and a short task/observation that can indicate readiness. Give a realistic destination and, where justified, a small set of completion self-checks; keep the detailed teaching and exercises in their owning later H2s. If a running example/project is appropriate, preview **how it grows with the milestones**, then actually reuse/extend it where the later approved content permits. Do not impose the same project or amount of code on every conceptual module, claim a fixed completion duration, reproduce the Roadmap verbatim, or promise later topics that are missing from the approved scope. Optional reading should use verified authoritative/curated references under the existing Step 2 module Reference and Step 4 section-reference contracts rather than invented links.

**Historical-origin H2 delivery (when owned by approved Menu):** follow the `ORIGIN / HISTORY` intent in `STEP_3_5_TEACHING_BLUEPRINT.md`, not a generic modern “WHY useful” paragraph. Answer the historically supportable origin question, situate the earlier limitation, explain the pivotal change(s) with accurate chronology and attribution, distinguish **historical causation** from illustrative comparison, and reconnect to the topic without losing its scope. In particular, do not equate **stored-program architecture**, **machine/assembly/high-level language evolution** and a **programming paradigm** or claim a single inventor/date without evidence. Verify substantial names/dates/firsts and priority disputes from reputable historical sources; use current section References where helpful. Clearly identify modern pseudocode/Java as pedagogical analogy rather than a period artifact, and avoid inventing historical quotes or details to make the narrative vivid. A historical subsection is optional for topics where history would not improve the approved learning outcome.

**Taxonomy / multi-level concept overview (when owned by approved Menu):** implement the `TAXONOMY / MAP` intent in `STEP_3_5_TEACHING_BLUEPRINT.md` by explaining **why the chosen categories differ**, what the representative concepts in each mean, and how important categories connect in a shared situation. A table/list may orient the learner but must not substitute for the explanation of levels and interactions. Distinguish **entity/property**, **design principle**, **object/type relationship** and **language implementation mechanism** where relevant; do not present a popular “four pillars” list as a complete mandatory definition of OOP. Keep the overview within the approved H2 focus, refer deeper concepts to real later H2s or their actual owner, and ensure conceptual examples/diagrams do not claim objects or states that the displayed code never constructs/produces. A focused core-model H2 needs to teach its **named elements and their meaningful cooperation**, not list the entire broad topic.

**Intentional reinforcement vs duplicate lessons:** when a concept appears in several modules/chapters, introduce only the meaning required by this H2's learning purpose and verified prerequisites; show **the new viewpoint, capability or decision** being taught here. Do not repeat full Java language mechanics, testing frameworks, SOLID or design-pattern inventories inside a paradigm lesson to maximize vocabulary coverage. Conversely, do not omit the short explanation needed to make first-use core concepts understandable merely because a sibling module owns the deep dive.

Before closing Step 4 authoring/refactor, perform a brief source-grounded self-check with representative learner questions (normally an entry WHAT/WHY question, a mechanism/trace question and an application/boundary question when applicable). For each, verify that the necessary explanation can be reconstructed from **current authored Markdown and verified prerequisites**, identify the H2(s) supplying the evidence, and revise any absent or fragmentary teaching. For core introductory WHAT H2s, also attempt a basic explain-it-back answer using **that H2 and verified earlier prerequisites only**; audit meaningful Java code claims and comparisons against the shared rule. Do this for configured VI/EN paths with equivalent conceptual outcomes, without adding report YAML, assessment items or artificial per-H2 content quotas. This author self-check does not replace either independent review or Step 9's integrated validation.

If all needed teaching fits existing approved H2 identities, fix it in Step 4. If meaningful first-contact teaching or progression **requires new/split/reordered H2 identities**, report `MENU GAP` to Step 3; do not silently redesign Menu. Route missing milestone or curriculum prerequisites to their established owners. Preserve valid prior content and downstream stable identities according to the existing refactor contract.

### Section-level References

Reference gắn trực tiếp với một Knowledge section thuộc ownership của Step 4 và được author **thuần trong chính Markdown Knowledge**, không tạo artifact/schema/relation system riêng.

Khi một H2 section có tài liệu tham khảo thực sự hữu ích, đặt reference ở bên trong section đó bằng H3 localized:

```markdown
## <a id="polymorphism">Đa hình</a>

Nội dung Knowledge...

### Tài liệu tham khảo

- Java Language Specification §...
- Dev.java — ...
```

EN tương ứng:

```markdown
### References
```

Contract bắt buộc:

```text
Section Reference
→ optional; không bắt buộc mọi H2 phải có
→ phải liên quan trực tiếp tới section hiện tại
→ ưu tiên authoritative source
→ có thể trùng với một entry trong module-level Reference nếu đó là source phù hợp nhất
→ không dump toàn bộ Module Reference xuống mỗi section
→ giữ official document title theo tên gốc khi phù hợp
```

Không tạo:

```text
## References
## Tài liệu tham khảo
```

chỉ để chứa citation/reference, vì H2 là stable Knowledge section identity và còn được dùng cho Portal navigation, Video section mapping và downstream relations. Reference local phải là H3 hoặc content thấp hơn nằm trong H2 hiện tại.

Section-level Reference không cần stable reference id, YAML riêng, relation metadata, generator riêng hoặc Portal projection riêng. Nó là một phần tự nhiên của learner-facing Knowledge Markdown.

### Knowledge → Roadmap `relatedKnowledge` binding

Sau khi Step 4 đã author/refactor đủ Knowledge để structure và stable Knowledge identities trở nên rõ ràng, Step 4 phải **audit và materialize/refine navigation binding ngược vào approved Roadmap** theo contract `relatedKnowledge` do Step 2 sở hữu.

Lifecycle bắt buộc:

```text
approved Roadmap milestones
        ↓
approved Step 3 Menu structure
        ↓
finalized/current Step 4 Knowledge identities
        ↓
audit each Roadmap milestone
        ↓
add / refine / remove stale relatedKnowledge mappings
        ↓
validate every relatedKnowledge target against CURRENT Knowledge
```

Mục tiêu của binding này là navigation/traceability:

```text
Roadmap milestone
→ learner đang học chặng nào?

relatedKnowledge
→ Knowledge nào trong cùng module giúp learner hoàn thành milestone đó?
```

**Keep navigation and teaching-role evidence separate:** the current `relatedKnowledge` is an **untyped same-module category link**, not a `PRIMARY`/`PREREQUISITE`/`RELATED` classification and not an H2-level learning coverage ledger. When auditing links, compare each milestone's unique learning objectives to **actual primary teaching H2s** identified in Step 3's existing preview and confirmed in written Knowledge. A reused category ID is acceptable when it legitimately helps more than one milestone; different H2s within it may serve different teaching roles. Neither presence nor absence of a category link alone proves that content is complete/missing. Do not add unsupported role-specific YAML keys, silently change Roadmap order, or remove useful shared navigation simply because its title is the same for multiple milestones.

Contract bắt buộc:

```text
relatedKnowledge
→ chỉ map tới CURRENT Knowledge identity hợp lệ trong cùng module
→ dùng identity/schema mà CURRENT Step 2 Roadmap contract yêu cầu
→ phải phản ánh Knowledge structure thực tế sau Step 4
→ remove/refine stale mappings khi Knowledge đã move/rename/merge/split
→ một milestone có thể map tới nhiều Knowledge identities khi hợp lý
→ một Knowledge identity có thể support nhiều milestones khi semantics thực sự overlap
```

Step 4 **không được** dùng `relatedKnowledge` để:

```text
đổi milestone order
đổi milestone meaning/purpose
reverse-engineer lại Roadmap từ Knowledge hiện tại
biến Knowledge section/category thành child Roadmap node
tạo milestone mới một cách silent
```

Nếu việc binding đúng đòi hỏi một milestone/prerequisite quan trọng mà approved Roadmap chưa có:

```text
ROADMAP GAP
→ quay lại Step 2
→ review/approve Roadmap change
→ sau đó quay lại Step 4 để hoàn tất Knowledge + relatedKnowledge binding
```

Nếu Knowledge identity thay đổi chỉ do Step 4 refactor nhưng Roadmap semantics vẫn đúng, Step 4 được phép update `relatedKnowledge` trực tiếp như một downstream navigation-binding maintenance task; không cần coi đó là Roadmap redesign.

Step 4 review phải kiểm tra ít nhất:

```text
[ ] every existing relatedKnowledge target still exists
[ ] no stale Knowledge identity remains in Roadmap mapping
[ ] important Roadmap milestones that should navigate to Knowledge are mapped when useful
[ ] every material milestone learning outcome has sufficient actual PRIMARY teaching H2s;
    genuine prerequisites are earlier and related-only links do not mask missing teaching
[ ] mapping reflects CURRENT Knowledge, not historical Menu/Knowledge layout
[ ] relatedKnowledge remains supporting navigation metadata, not curriculum authority
```

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

At the end of the Knowledge authoring/refactor step, the module should have a coherent Knowledge path **and its applicable Roadmap `relatedKnowledge` bindings must reflect the CURRENT finalized Knowledge structure** before Video/Quiz/Interview are authored and before API relations are finalized. Video later mirrors one Knowledge Menu file into one Video source and treats the finalized H2 order as its section order.

"Coherent" here means more than all planned anchors existing. A learner entering at chapter 1 should be able to follow the motivation and chapter transitions without already knowing the module's vocabulary.

---

---

## Pedagogy and localization rules

### Pedagogical coherence is a first-class requirement

The canonical criteria for beginner-first, prerequisites, WHAT → WHY → RELATION → HOW → EVIDENCE, descriptive titles and optional plain-text cross-module bridges live in `rules/MODULE_CONTENT_RULES.md`. Do not redefine their semantics here.

**Step 4 authors actual teaching**, not an isolated glossary or a collection of completed H2 placeholders. Give each section its assigned motivation/mental model, connect it to previous and next concepts where appropriate, and substantiate technical rules with real examples or observations. A mechanically complete anchor inventory is not evidence of pedagogical coherence.

### Vietnamese authoring policy

For `vi` Knowledge, Vietnamese is the primary explanatory language. The learner should not have to translate ordinary prose mentally while learning a new Java concept.

Translate ordinary explanatory vocabulary when a clear Vietnamese equivalent exists, for example:

```text
responsibility   → trách nhiệm
behavior         → hành vi
caller           → bên gọi / đoạn mã sử dụng
rule             → quy tắc
state            → trạng thái
implementation   → cách triển khai / phần triển khai
collaborator      → đối tượng cộng tác
hierarchy         → cây kế thừa / hệ phân cấp (tùy context)
procedural code   → mã theo phong cách thủ tục
localize change   → khoanh vùng ảnh hưởng của thay đổi
```

Keep English when it is one of these:

```text
Java keyword or syntax token
→ class, interface, extends, implements, private, static, final...

Java/API/type/member name
→ Object, String, List, @Override, PaymentMethod...

technical term the learner should recognize/search
→ polymorphism, encapsulation, dynamic dispatch, overloading, overriding, subtype...
```

For the last category, prefer Vietnamese first with English in parentheses on first meaningful introduction, then use the Vietnamese term where natural:

```text
đa hình (polymorphism)
đóng gói (encapsulation)
kiểu con (subtype)
trạng thái (state)
trách nhiệm (responsibility)
```

Do not force literal translation when the Vietnamese wording would become less precise than the established Java term, but also do not leave English filler merely because the source draft was written in English.

Repository-specific wording exclusion: do not use the filler word `nuance` / `nuances` in learner-facing content. It is too vague in English and reads unnaturally when embedded in Vietnamese prose. Replace it with the exact meaning required by the sentence, for example `điểm cần lưu ý`, `điểm khác biệt`, `đặc điểm`, `hành vi`, or `chi tiết` in VI, and `detail`, `distinction`, `behavior`, or `caveat` in EN. The excluded token may appear in this governance rule only so agents can recognize and remove it from authored content.

The pedagogical chain is conceptual, not a requirement to expose English labels in localized content. VI headings may use natural equivalents such as:

```text
KHÁI NIỆM
VÌ SAO
MỐI LIÊN HỆ
CƠ CHẾ
MINH CHỨNG
THỰC HÀNH
ĐÁNH ĐỔI
GIỚI HẠN
```

These labels describe **learning roles inside the explanation**, not a directive to create generic H2 titles named only `KHÁI NIỆM` or `VÌ SAO`. The actual anchored H1/H2 must follow the shared descriptive-title contract, while body progression remains consistent with `WHAT → WHY → RELATION → HOW → EVIDENCE → PRACTICE`.

### Mandatory three-layer curriculum model

Every mature learning module should be understandable at **three nested layers**.

#### Layer 1 — Approved Module Roadmap

The first chapter/equivalent entry section must **write the beginner-friendly foundation and reasons for learning the module's owned concept** required by `MODULE_CONTENT_RULES.md`, within the **actual anchored H2 focus approved at Step 3**. Reflect the approved Roadmap and declared learner baseline; do not substitute a detached overview or an assumed prior definition. A learner must be able to understand what the module teaches and why its chapters connect **before** deep terminology or mechanisms.

For example, a Collections lesson should establish the problem of grouping/changing sets of data, what arrays can already do and what the Collections Framework adds **before** relying on List/Set/Queue taxonomy. In Generics, Reflection or ClassLoader, teach the respective motivation before advanced syntax/API/behaviors. If the necessary foundational focus cannot fit any approved H2, report `MENU GAP` rather than redesigning Step 3 here.

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
