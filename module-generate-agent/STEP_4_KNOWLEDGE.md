# Step 4 - Knowledge Rules

Step 4 là canonical authoring/refactor step cho **Knowledge** sau khi Step 3 đã ổn định Menu structure.

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

Curriculum Map cung cấp area/module ownership và boundary; ROADMAP cung cấp module learning journey; Step 3 Menu cung cấp chapter/path/title/section structure mà Knowledge phải triển khai.

Nếu Curriculum Map không tồn tại và Step 4 được gọi như một **isolated Knowledge review/refactor**, không block chỉ vì thiếu file. Dùng current Roadmap/Menu/Knowledge/source/reference/metadata, neighboring ownership evidence và authoritative external documentation/real-world behavior để validate/refactor Knowledge theo scope thực tế. Existing Knowledge phải được preserve khi valid nhưng không được giữ technical claim sai chỉ vì historical content đã tồn tại.

Nếu đang chạy canonical full flow bắt đầu từ Step 1 thì không dùng fallback này để skip Step 1. Nếu isolated fallback không resolve được ownership/boundary, report **CURRICULUM CONTEXT GAP**. Nếu Knowledge cần một milestone chưa có trong Roadmap hiện hữu, report **ROADMAP GAP**. Nếu Knowledge chỉ có thể đúng bằng cách redesign chapter/path/title structure đáng kể, report **MENU GAP** và quay lại Step 3 thay vì tự tái cấu trúc Menu trong Step 4.

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

```text
NEW MODULE
→ consume Step 3 Menu skeleton
→ write full Knowledge

REFACTOR MODULE
→ consume the reviewed/refactored Step 3 Menu skeleton
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
```

Do not make the visible title carry the whole explanation, conclusion, motivation, or contrast. Put those in the body instead.

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
