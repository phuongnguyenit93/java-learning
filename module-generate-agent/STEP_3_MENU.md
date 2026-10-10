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

### Step 3 learner baseline and foundation-focus gate

Follow `rules/MODULE_CONTENT_RULES.md` as the **sole source of shared learning-content quality**: assume no prior understanding of module-owned core concepts unless a verified prerequisite establishes it; teach WHAT/WHY/RELATION before depending on HOW; do not treat the title of a module/H2 as proof that its meaning was taught.

**Step 3's own deliverable is structural**, not full Knowledge prose. Using Step 1 ownership, approved Step 2 Roadmap and real prerequisite evidence, allocate each important term/learning focus to appropriate **existing or newly planned anchored H2 identities**, in learner-facing order:

- Record mentally/in the review result which H2 establishes **WHAT**, which explains **WHY**, and which establishes **RELATION**, before the first H2 using the concept for advanced HOW.
- One H2 **may carry both WHAT and WHY** when its focused scope allows; neither a distinct H2 per label nor literal WHAT/WHY words in the title are required. But missing foundational focus is a **MUST FIX**, not an excuse to hide it in an unrelated technical section's future body.
- Distinguish terms already taught by real prerequisites from terms only named in an upstream module. Provide an entry/bridge focus where needed, or report the proper Curriculum/Roadmap gap.
- Before approving Menu, ask whether Step 4 can fully realize each focus **without adding/splitting/reordering H2**. If not, fix the Step 3 skeleton before handoff.

For example, the Spring Boot application-runtime entry flow should introduce the meaning of application runtime and why its lifecycle matters **before** Boot-specific hooks. A suitable descriptive sequence is `Application Runtime: Khái niệm và phạm vi` → `Vai trò của runtime trong quá trình thực thi ứng dụng` → `Spring Boot trong quá trình điều phối Application Runtime` → `Vòng đời của SpringApplication`. These are illustrative learning focuses, not mandatory exact H2 identities.

For a foundational term **owned by another module**, Step 3 identifies the first suitable H2 where Step 4 can author a short **plain-text contextual bridge** under the shared rule. Do **not** add a special navigation H2, module link, route, `relatedModules`, `readmeRelated` or metadata solely to refer to the other module. This optional prose bridge never replaces a genuine missing prerequisite.

### Chapter 1 mandatory minimum — extensible foundation, NOT a fixed H2 template

**Mỗi new hoặc refactor module bắt buộc có một Chapter 1 / first learner-facing chapter đủ làm điểm vào cho người mới học chủ đề đó.** Chapter 1 là vị trí mở đầu **thực tế theo generated Menu order**, không nhất thiết tên file là `1.Introduction` hay H1 phải viết `Tổng quan`. Đây là **minimum learning coverage**, không phải maximum content, giới hạn số H2 hay template đặt tên.

Khi Step 3 materialize các anchored H2 của Chapter 1, phải bảo đảm người học có thể tiếp cận **tối thiểu năm nhóm nội dung sau** trong Chapter 1:

1. **WHAT — Khái niệm và phạm vi:** chủ đề/module này thực chất là gì, không phải là gì, phạm vi module sở hữu. Không được dùng tên chủ đề như một thuật ngữ hiển nhiên người học đã biết.
2. **WHY — Mục đích, vai trò và động cơ học:** vì sao concept tồn tại hoặc vì sao quan trọng phải hiểu nó; vấn đề/hạn chế/tình huống thực tế mà nó giúp giải thích hoặc xử lý. Không sáng tác một `problem solved` giả cho những chủ đề mang tính mô tả tự nhiên như application runtime.
3. **CORE MODEL — Các thành tố/khái niệm chính và mối quan hệ:** giới thiệu mental model ban đầu; chỉ nêu thành phần, trạng thái, giai đoạn, abstraction hoặc quan hệ **thật sự tồn tại**. Không ép mọi concept phải có `components` hay `lifecycle` riêng khi bản chất không có.
4. **PREREQUISITE ORIENTATION — Điểm xuất phát của learner:** chỉ rõ tối thiểu vốn từ/kiến thức nào cần để đọc tiếp, giải thích hoặc dành một short bridge cho term thiết yếu chưa được học, phân biệt nội dung module tự sở hữu với module khác. Nếu không cần prerequisite bổ sung, không tạo H2 rỗng mang tên `Prerequisites`; vẫn phải xác nhận entry không có hidden prerequisite.
5. **LEARNING JOURNEY — Bản đồ đường học tiếp:** cho learner biết các nhóm kiến thức/chương tiếp theo sẽ đi theo hướng nào và liên hệ ra sao với foundation vừa học. Không viết lại toàn bộ ROADMAP, tạo bản ROADMAP khác hoặc bắt buộc một H2 `Lộ trình học` nếu có thể giới thiệu tự nhiên trong focused section.

**Năm nhóm nội dung trên là sàn tối thiểu, không phải danh sách đóng.** Step 3 **phải bổ sung H2/focus nhập môn khác khi cần** để làm rõ vocabulary, vấn đề thực tế, so sánh cơ chế cũ/mới, ví dụ nền, bối cảnh lịch sử có ích, ranh giới ownership hoặc cầu nối đến chapter sau. Không được dừng ở đúng năm mục vì đã tick checklist; không được thêm mục hình thức chỉ để tăng số lượng.

**Materialization contract:**

- Mỗi nhóm phải có **một hoặc nhiều H2 phù hợp về scope** trong Chapter 1 để Step 4 có thể hiện thực hóa nội dung; **một H2 có thể đáp ứng nhiều nhóm** nếu vẫn có một learning focus rõ ràng. Năm nhóm **không đồng nghĩa** phải có năm H2.
- WHAT/WHY và mental model phải có learner-facing **anchored foundation identity**, không được giấu toàn bộ trong BASE.md, một lời mở đầu không anchor, hoặc đoạn prose của H2 chuyên sâu đang giả định người học đã biết chúng.
- PREREQUISITE ORIENTATION và LEARNING JOURNEY có thể được diễn đạt tự nhiên **bên trong body của một H2 nhập môn phù hợp**, không bắt buộc H2 riêng; Step 3 vẫn phải kiểm tra rằng structure có đúng chỗ để Step 4 viết đủ chúng.
- Dùng **statement/topic-style H1/H2 tự nhiên cho từng module**, không copy các nhãn `WHAT`, `WHY`, `CORE MODEL`, `PREREQUISITES`, `LEARNING JOURNEY` thành title literal của mọi module.
- Nếu một khái niệm quan trọng đã được prerequisite thực sự dạy, Chapter 1 có thể dùng bridge ngắn và đi thẳng vào **góc nhìn mới thuộc module hiện tại**; không dạy lại toàn bộ owner module. Không vì thế mà bỏ qua phần nhập môn của core concept do module hiện tại sở hữu.
- Nếu năm nhóm không thể được phân bổ vào Chapter 1 từ approved Roadmap mà không thay đổi milestone/ownership, report upstream `ROADMAP GAP` / `CURRICULUM GAP` theo đúng owner. Nếu chỉ cần mở rộng, split, merge hoặc reorder H2 trong existing approved milestone, đó là công việc Step 3 bình thường.

**Ví dụ, không phải universal template:**

```text
Git — first chapter
→ Git: Khái niệm và mục đích
→ Bài toán quản lý phiên bản mã nguồn
→ Repository, Working Tree, Staging Area và Commit
→ Bối cảnh học và mối liên hệ với các chương thao tác Git

Spring Boot Application Runtime — first chapter
→ Application Runtime: Khái niệm và phạm vi
→ Vai trò của việc hiểu Application Runtime
→ Các trạng thái và thành tố chính trong quá trình thực thi ứng dụng
→ Spring Boot trong mô hình Application Runtime
```

Trong ví dụ, prerequisite orientation và preview các chương sau có thể nằm **bên trong H2 nhập môn tương ứng**; ví dụ không có ý nói bốn H2 luôn đủ cho mọi module. Module khó hơn có thể cần sáu, tám hoặc nhiều H2 nhập môn nếu từng H2 có focus riêng và không vượt boundary. Step 4 vẫn là owner viết full body.

**Chapter 1 review gate — mandatory cho mỗi language:** reviewer đọc full Chapter 1 H1/H2 theo thứ tự render, ánh xạ **cả năm nhóm** vào H2 thích hợp, kiểm tra core WHAT/WHY xuất hiện trước API/lifecycle/deep HOW, kiểm tra không có hidden prerequisite, và xác nhận mỗi section đủ scope để Step 4 triển khai. Nếu thiếu bất kỳ nhóm nào mà không có lý do hợp lệ về cách thực hiện trong H2 hiện có thì **MUST FIX trong Step 3**; title-only scaffold hay lời hứa `Step 4 sẽ tự thêm` không phải bằng chứng đạt gate.

### Step 3 descriptive-title gate

Apply the `MODULE_CONTENT_RULES.md` **descriptive topic/statement title** contract to **every actual H1/anchored H2 in VI and EN**. Choose clear topic identities such as `Vòng đời của SpringApplication` instead of question prompts such as `Có những vòng đời nào?`. Rewrite semantically, not by deleting question marks. Keep each title consistent with the H2's approved WHAT/WHY/RELATION/HOW focus and distinct from neighboring H2s.

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

```text
1. Read Curriculum ownership + approved ROADMAP and declared prerequisites.
2. Read ALL current H1 + anchored H2 in learner-facing order for that language.
3. Mark each new essential term's first appearance and its introduction/bridge location.
4. Walk through the first chapters as the declared target learner, not as a Spring/Java expert.
4a. Audit the actual first learner-facing Chapter 1 against all FIVE mandatory minimum content groups; allow and require extra foundational H2 when the topic needs them.
5. Check WHAT / WHY / core relations BEFORE lifecycle, internals, configuration or APIs.
6. Evaluate EVERY H1/H2 as a descriptive topic statement, not an interview question.
7. Identify H2 ownership of the WHAT/WHY/RELATION focus, including any needed plain-text cross-module bridge inside an existing lesson.
8. Verify VI/EN semantic scope/order parity without assuming literal translations.
9. Confirm Step 4 can teach within the approved titles/anchors; no structural rescue needed.
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
learner baseline + prerequisites được xác định từ evidence, không mặc định core term trong tên module đã được biết
full visible-title audit chứng minh core concept có intro/bridge rõ ràng trước khi dùng ở lifecycle/mechanics/API/decision
entry flow có WHAT + WHY + concrete motivation + foundational relation đủ cho người mới theo đúng module prerequisite
Chapter 1 (first learner-facing chapter) có đủ 5 minimum content groups WHAT, WHY, CORE MODEL, PREREQUISITE ORIENTATION và LEARNING JOURNEY; được bổ sung foundation sections khi cần, không ép fixed H2 count/title
mọi nhóm minimum được map vào H2 thực tế có learning focus phù hợp và có thể triển khai body ở Step 4, không chỉ dựa vào BASE/placeholder
WHAT/WHY/RELATION learning focus được phân bổ rõ cho các H2 hợp lệ để Step 4 triển khai; không mặc định một H2 riêng cho từng vai trò
cross-module foundational terms có body-level bridge ngắn tại vị trí thích hợp khi cần; không phát sinh navigation/link/route/metadata chỉ cho lời nhắc văn bản
không còn beginner-critical undefined term hoặc hidden prerequisite trong H1/H2 learning progression
mọi H1/anchored H2 sử dụng descriptive topic/statement titles thay vì question-style Quiz/Interview headings
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
STEP_4_KNOWLEDGE.md
```
