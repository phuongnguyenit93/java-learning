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

Ngoài General Agent Rules, Step 4 **bắt buộc** phải đọc và nắm context của:

```text
./temp/*_CURRICULUM_MAP.md tương ứng với target module
+ approved Step 2 ROADMAP
+ approved Step 3 Menu structure
```

Curriculum Map cung cấp area/module ownership và boundary; ROADMAP cung cấp module learning journey; Step 3 Menu cung cấp chapter/path/title/section structure mà Knowledge phải triển khai.

Nếu Curriculum Map thiếu/ambiguous, report **CURRICULUM CONTEXT GAP**. Nếu Knowledge cần một milestone chưa có, report **ROADMAP GAP**. Nếu Knowledge chỉ có thể đúng bằng cách redesign chapter/path/title structure đáng kể, report **MENU GAP** và quay lại Step 3 thay vì tự tái cấu trúc Menu trong Step 4.

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

For an existing/refactor module, Step 3 has already reconciled the structural Menu with Curriculum + ROADMAP. Step 4 therefore focuses on the learner-facing Knowledge body and metadata.

Audit the old Knowledge against the approved Curriculum, ROADMAP and Step 3 structure. Existing knowledge must be preserved while being migrated, rewritten, regrouped, split, merged or expanded as needed.

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
→ names the concept or learner question directly
→ matches the opening focus of the section
```

Do not make the visible title carry the whole explanation, conclusion, motivation, or contrast. Put those in the body instead.

Prefer:

```text
OOP là gì?
Đóng gói là gì?
Rủi ro của kế thừa
Dynamic Dispatch
What Is OOP?
Inheritance Risks
```

over sentence-like titles such as:

```text
Đối tượng là nơi gắn trạng thái với hành vi
Đóng gói không chỉ là dùng field private
Objects as collaborating state + behavior
Encapsulation is more than private fields
```

Visible titles may be refined during curriculum editing without changing the stable anchor id. VI/EN titles should be naturally localized and conceptually equivalent, not literal copies of each other.

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

At the end of the Knowledge authoring/refactor step, the module should have a coherent Knowledge path before Video/Quiz/Interview are authored and before API relations are finalized. Video later mirrors one Knowledge Menu file into one Video source and treats the finalized H2 order as its section order.

"Coherent" here means more than all planned anchors existing. A learner entering at chapter 1 should be able to follow the motivation and chapter transitions without already knowing the module's vocabulary.

---

---

## Pedagogy and localization rules

### Pedagogical coherence is a first-class requirement

Technical coverage is not the same thing as a good curriculum.

A module can be technically correct and still be a poor learning experience when it only presents a catalog of definitions:

```text
term A
→ definition

term B
→ definition

term C
→ definition
```

That structure may produce valid Knowledge anchors, API relations, Quiz questions and Interview questions while still leaving the learner asking:

```text
What is this module actually about?
Why do these concepts belong together?
What problem caused this concept to exist?
How does this term connect to the previous one?
Where does Java code enter the picture?
```

Therefore **coverage correctness and pedagogical correctness are separate acceptance dimensions**.

The preferred learning chain is:

```text
WHAT
→ what is this domain/concept?

WHY
→ what problem or limitation makes it useful?

RELATION
→ how does it connect to the other concepts in this module?

HOW
→ how does Java represent or implement the idea?

EVIDENCE
→ what code/runtime behavior makes the idea observable?

PRACTICE / PRESENTATION
→ how do Video / Quiz / Interview / API experiments reinforce or present it?
```

Do not treat a complete H2 inventory as proof that the module teaches well.

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

The exact visible heading may vary by chapter, but the underlying learning role must remain equivalent to `WHAT → WHY → RELATION → HOW → EVIDENCE → PRACTICE`.

### Mandatory three-layer curriculum model

Every mature learning module should be understandable at **three nested layers**.

#### Layer 1 — Approved Module Roadmap

The learner needs an explicit orientation layer before deep terminology. This layer is now a first-class upstream curriculum artifact, not merely a diagram embedded after the README has already been authored.

At minimum, the first chapter or equivalent entry chapter must explain in beginner-friendly language:

```text
What is this thing?
Why does this domain/topic exist?
What broad problem does it help solve?
What are the major terms the learner will encounter?
How do those terms relate to each other?
What order should they be learned in?
What should the learner understand by the end of the module?
```

The chapter does not have to be literally named `MentalModel`, but it must perform this function.

The first chapter should **reflect and explain the approved roadmap**, for example:

```text
core idea
    ↓
concept A — solves one problem
    ↓
concept B — introduces another capability
    ↓
concept C — explains runtime behavior
    ↓
concept D — alternative / trade-off
```

The goal is that a learner can answer:

```text
"I know what I am about to learn and why these chapters belong in one module."
```

For a module whose module/topic name is itself a concept the learner is expected to understand — for example `collection`, `generics`, `reflection`, `annotation`, `classloader`, `exception`, or `OOP` — do not rely on an unanchored introductory paragraph to establish that concept. The entry chapter should normally contain an early stable Knowledge section that explicitly answers:

```text
What is <topic> in beginner language?
        ↓
What concrete problem exists without it?
        ↓
What simpler/older mechanism could solve part of the problem, and where does it stop being enough?
        ↓
Why would application code choose this concept/mechanism?
        ↓
Only then: what taxonomy, hierarchy, subtypes or advanced mechanics exist?
```

For example, a Collections module should not begin conceptually with `Collection → List → Set → Queue`. It should first make the learner understand why grouping dynamic data is a problem at all, what arrays already solve, what the Collections Framework adds, and only then use the hierarchy as a map of different behavioral contracts.

The same rule applies elsewhere: do not start Generics with type-parameter syntax before establishing the duplication/type-safety problem; do not start Reflection with `Class` APIs before explaining why runtime inspection exists; do not start ClassLoader with delegation mechanics before explaining why class bytes must be located and defined.

Do not open a module with advanced definitions while the learner still lacks the vocabulary required to understand why the definitions matter.

The entry chapter is downstream from the roadmap: if it needs a milestone that the roadmap does not contain, fix/review the roadmap first rather than letting README structure diverge.

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
