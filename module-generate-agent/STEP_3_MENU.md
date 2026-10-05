# Step 3 - Menu Rules

Step 3 dùng cho **cả new module và existing/refactor module**. Mục tiêu là ổn định Menu/chapter/title skeleton từ Step 1 Curriculum + Step 2 ROADMAP đã approve trước khi viết/refactor full Knowledge.

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

## <a id="generic-purpose">Generics là gì và vì sao cần?</a>

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
→ What Is the Spring IoC Container?
→ Why Is a Container Needed?
→ What Is a Spring Bean?
→ Managed Bean vs Plain Object
→ What Is IoC?
→ How Dependency Injection Implements IoC
```

Các H2 trên vẫn cùng phục vụ một milestone nếu chúng không tạo learning goal mới ngoài ROADMAP.

### Entry-chapter foundation gate

Trước khi finalize chapter đầu tiên hoặc entry chapter tương đương, Step 3 phải kiểm tra learner có stable H2 identities để trả lời những câu hỏi nền tảng sau hay chưa:

```text
What is this module/core concept?
        ↓
Why does it exist?
        ↓
What concrete problem exists without it?
        ↓
What foundational terms will later sections assume?
        ↓
Have those terms been introduced before they are used?
        ↓
How do the foundational concepts relate to each other?
        ↓
What should the learner study next?
```

Không bắt buộc mỗi câu hỏi phải là một H2 riêng. Có thể gộp khi một section vẫn có một learning focus rõ ràng, ví dụ `What Is Generics and Why Is It Needed?` hoặc `What Is the Spring IoC Container and Why Is It Needed?`.

Nhưng không được chỉ nhét định nghĩa quan trọng vào một đoạn prose không có stable identity nếu term đó là foundation mà nhiều chapter sau sẽ giả định learner đã hiểu.

### Foundational terminology rule

Step 3 phải lấy terminology ownership từ Curriculum + ROADMAP và hỏi:

```text
term này có phải learner sẽ gặp lại nhiều lần trong module không?
term này có phải prerequisite để hiểu chapter sau không?
module hiện tại có phải primary owner chịu trách nhiệm giới thiệu term này không?
```

Nếu câu trả lời là có, term đó phải được giới thiệu ở một vị trí learner-facing rõ ràng trước khi downstream section sử dụng nó như vocabulary đã biết.

Ví dụ với `core-container`, nếu Curriculum xác định module này own IoC Container / Bean / Dependency Injection / ApplicationContext thì Step 3 phải bảo đảm các foundation đó được materialize đủ rõ trong Menu/H2 flow; không được để Step 4 lần đầu tiên "cứu" chúng bằng body prose.

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
```

Downstream:

```text
approved Menu + anchored lesson-title scaffold
+ internal-menu generated scaffold
+ approved BASE.md
+ generated LIST/final README
        ↓
STEP_4_KNOWLEDGE.md
```
