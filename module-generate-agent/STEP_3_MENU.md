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

Ngoài General Agent Rules, Step 3 **bắt buộc** phải đọc và nắm context của Curriculum Map liên quan trong:

```text
./temp/*_CURRICULUM_MAP.md
```

và ROADMAP Step 2 đã được review/approve của target module.

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

Nếu Curriculum Map thiếu hoặc ambiguous, report **CURRICULUM CONTEXT GAP**. Nếu ROADMAP chưa approved hoặc Menu cần milestone mà ROADMAP không có, report **ROADMAP GAP**. Không tự bù upstream gap bằng Menu structure.

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

```text
NEW MODULE
→ execute Step 3

REFACTOR MODULE
→ execute Step 3 exactly like a new module
→ additionally inspect the existing Menu/Knowledge structure
→ preserve/reuse compatible structure where appropriate
→ restructure incompatible Menu before Step 4
```

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

Mỗi anchored H2 là một stable Knowledge identity chuẩn bị cho Step 4 và downstream relations.

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

Sau khi `readmeRelated`, Quiz/Interview relations, Knowledge metadata hoặc API ordering đã phụ thuộc vào path/anchor, rename/reorder sẽ trở thành reference migration và phải update/validate mọi exact relation liên quan.

Không chạy `syncMetadataReadme` chỉ để tạo metadata rỗng trước khi anchored H2 identities thực sự được chốt.

---

## Build Menu + title only

This step applies to **both new and existing/refactor modules**.

Derive the detailed Knowledge skeleton from the approved Curriculum + ROADMAP:

```text
approved Curriculum boundary
        ↓
approved roadmap milestone
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
Step 1 Curriculum
        +
Step 2 approved ROADMAP
        +
authoritative technical sources
        ↓
target Menu skeleton
        ↑
existing Menu / existing Knowledge
→ inspect for reusable structure and migration impact
```

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
→ surface as an explicit finding; do not silently delete
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
Menu structure tương thích với Step 1 Curriculum
ROADMAP milestone/order được phản ánh hợp lý
chapter order ổn định
paths ổn định
localized H1 titles ổn định
toàn bộ planned anchored H2 identities đã được materialize vào Markdown file
anchored H2 order/title/id ổn định
generateInternalReadmeMenuForModule đã xử lý toàn bộ chapter files của MODULE_LANGUAGE
internal Menu/details/back-to-top scaffold hợp lệ
BASE.md đã được author/review cho mọi MODULE_LANGUAGE
BASE.md phản ánh đúng Curriculum + ROADMAP + Menu structure
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
