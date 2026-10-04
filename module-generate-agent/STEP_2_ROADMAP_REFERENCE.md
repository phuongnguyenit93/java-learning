# Step 2 - Roadmap + Reference Rules

Tài liệu này là **canonical source-of-truth duy nhất cho Step 2 — Build / Review / Refactor Module ROADMAP** sau khi scope, ownership và boundary cấp area đã được xác định ở [`STEP_1_CURRICULUM_RULES.md`](./STEP_1_CURRICULUM_RULES.md).

### General Agent Rules + Curriculum Map context guard

Trước khi thực hiện Step 2:

```text
Nếu context của GENERAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENERAL_AGENT_RULES.md trước khi tiếp tục
```

Ngoài General Agent Rules, Step 2 **bắt buộc** phải đọc và nắm context của Step 1 Curriculum Map liên quan trong:

```text
./temp/*_CURRICULUM_MAP.md
```

Resolve Curriculum Map theo target module/area thực tế trong repository. Curriculum Map phải cung cấp ít nhất:

```text
module responsibility
primary ownership
scope boundary
neighboring-module ownership
inter-module dependency
recommended area learning order
duplication / curriculum-gap risks
```

Không coi Curriculum Map là optional chỉ vì module đã tồn tại hoặc đã có ROADMAP cũ.

Nếu không xác định được Curriculum Map tương ứng, file bị thiếu, hoặc có nhiều candidate mâu thuẫn, phải report **CURRICULUM CONTEXT GAP** thay vì tự suy ownership/boundary.

Không suy đoán các orchestration, preservation, gap-routing, Curriculum hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

### Module branch + worktree isolation

Trước khi bắt đầu chỉnh sửa Step 2, target module phải được tách sang **branch riêng theo tên module** và branch đó phải được materialize thành **worktree riêng** cho session hiện tại.

Quy ước branch:

```text
module/<module-name>
```

Trong đó `<module-name>` dùng tên module dạng lowercase kebab-case.

Ví dụ:

```text
Language Basics
→ module/language-basics

Native Interoperability
→ module/native-interoperability
```

Workflow bắt buộc:

```text
1. Kiểm tra current branch và git status.
2. Không mang unrelated working-tree changes vào task module mới.
3. Từ baseline `main` phù hợp, create branch `module/<module-name>`.
4. Tạo worktree riêng cho branch đó.
5. Session hiện tại chuyển toàn bộ thao tác sang worktree vừa tạo.
6. Step 2 và mọi Step tiếp theo của module tiếp tục làm trong worktree riêng này cho tới hết Step 10.
7. Không quay lại sửa module từ main worktree trong lúc module worktree còn là workspace active của task.
```

Ví dụ logical layout:

```text
main worktree
→ /java-learning
→ branch main

module worktree
→ /java-learning-worktrees/language-basics
→ branch module/language-basics
```

Mục tiêu của worktree isolation là cho phép nhiều session chạy song song mà không giẫm lên current branch, index hoặc uncommitted working state của nhau.

```text
Session A
→ worktree A
→ branch module/language-basics

Session B
→ worktree B
→ branch module/networking
```

Không thực hiện module-generation trực tiếp trên `main`.

### Session workspace lifecycle

Sau khi module worktree đã được tạo ở Step 2:

```text
Step 2
↓
Step 3
↓
...
↓
Step 10
```

đều phải được thực hiện **trong cùng module worktree đó**.

Sau khi Step 10 đã:

```text
commit
→ push module branch
→ create Merge Request vào main
```

thì session quay lại **main worktree ban đầu** để kết thúc lifecycle của task hoặc chuẩn bị cho module/task tiếp theo.

Việc quay lại main worktree sau khi tạo Merge Request **không có nghĩa auto-merge MR** và cũng không xóa module worktree/branch. Cleanup worktree/branch là thao tác riêng, chỉ thực hiện khi phù hợp với trạng thái MR và user workflow.

File này sở hữu đồng thời:

```text
Roadmap architecture
+ build / on-disk contract
+ Roadmap Skeleton
+ milestone / relation contract
+ authoring workflow
+ review / approval gate
+ downstream dependency contract
+ Portal semantic contract
+ module-level Reference curation contract
```

```text
STEP 1 — CURRICULUM
→ area scope / module inventory / ownership / boundary / dependency
        ↓
STEP 2 — ROADMAP
→ learning journey bên trong một module
→ curate shared module-level Reference catalog
        ↓
Menu / Knowledge
        ↓
API Docs / Video / Quiz / Interview
```

---

## 1. ROADMAP là upstream source-of-truth của module

Trước khi tạo Knowledge Menu hoặc lesson content mới, module phải có **ROADMAP đã được review/approve** theo contract trong chính file này.

Dependency bắt buộc:

```text
approved area Curriculum
        ↓
module identity / scope / neighboring-module boundaries
        ↓
Roadmap Skeleton
        ↓
Module Roadmap
        ↓
Roadmap Review / approval
        ↓
Menu / Knowledge
        ↓
API Docs / Video / Quiz / Interview
```

Nếu ROADMAP sai, downstream content không được tự bù bằng cách tạo một learning flow khác.

---

## 2. Build và on-disk contract

Roadmap capability đi theo hai phase logic:

```text
Phase A — Roadmap Skeleton
→ cung cấp planning questions và quality dimensions dùng chung

Phase B — Module Roadmap
→ tạo ordered learning journey cho một module cụ thể
```

Roadmap files nằm trong từng module:

```text
<module>/
└── roadmap/
    ├── vi/
    │   └── roadmap.yml
    └── en/
        └── roadmap.yml
```

Build contract:

```text
automation/master.json
→ BUILD_ROADMAP (default TRUE)
→ MODULE_LANGUAGE
→ ModuleOrchestrationPlugin
→ RoadmapSetupPlugin
→ roadmap/<language>/roadmap.yml
```

`MODULE_LANGUAGE` quyết định language skeleton nào tồn tại. Mỗi language sở hữu ROADMAP độc lập; build system không yêu cầu VI/EN phải có cùng node ids, số milestone, ordering hoặc dependencies.

Ownership:

```text
Gradle generator
→ generated schema/comment block

human / AI author
→ roadmap: content
```

Không overwrite human/AI-owned `roadmap:` content trong normal synchronization.

### Module-level Reference companion artifact

Step 2 đồng thời sở hữu việc **curate Reference chung của module** từ các authoritative sources đã được dùng để research/review ROADMAP.

Vì vậy Step 2 không được coi là hoàn tất chỉ bằng việc approve ROADMAP; agent cũng phải review/create `reference/references.yml` cho module. Nếu thật sự không có learner-facing source phù hợp để publish, có thể giữ skeleton comment-only, nhưng không được bỏ qua việc đánh giá Reference.

Reference chung không phải một ROADMAP khác và không quyết định learning order. Nó là learner-facing bibliography / further-reading catalog của toàn module.

Canonical source:

```text
<module>/
└── reference/
    └── references.yml
```

Reference chung là **shared artifact**, không tách theo `MODULE_LANGUAGE`:

```text
reference/references.yml
→ dùng chung cho VI / EN
→ không tạo reference/vi hoặc reference/en
```

Build contract:

```text
automation/master.json
→ BUILD_REFERENCE (default TRUE)
→ ModuleOrchestrationPlugin
→ ReferenceSetupPlugin
→ reference/references.yml
```

Generation / preservation contract:

```text
file missing
→ create comment-only skeleton

file exists but empty / whitespace-only
→ initialize comment-only skeleton

file contains any authored content
→ preserve file exactly
→ do not overwrite / resynchronize authored Reference content
```

Reference skeleton chỉ mô tả schema bằng comment. Authored content có dạng tối thiểu:

```yaml
references:
  - title: "Java Language Specification"
    url: "https://docs.oracle.com/javase/specs/"
    description: "Official Java language specification."
```

Authoring policy:

```text
title
→ giữ tên chính thức / tên gốc của tài liệu khi có thể

url
→ trỏ tới authoritative or high-value source thực tế

description
→ mô tả ngắn lý do source hữu ích cho module
→ mặc định có thể dùng English chung vì catalog không localized
```

Không dump mọi URL agent từng mở vào `references.yml`. Research evidence có thể rộng, nhưng published Module Reference phải được **curate** và chỉ giữ các nguồn thực sự có giá trị học tập hoặc kiểm chứng module.

Portal chỉ nên expose Reference khi file có authored `references:` entries. Comment-only skeleton không phải learner-facing Reference content.

---

## 3. Roadmap Skeleton

Roadmap Skeleton là **planning framework**, không phải danh sách chapter bắt buộc. Một module không cần một milestone cho mỗi dimension dưới đây.

Khi thiết kế ROADMAP, phải cân nhắc ít nhất:

```text
PURPOSE
→ Module/topic này là gì?
→ Tại sao nó tồn tại?
→ Nếu không có nó thì learner/code gặp vấn đề gì?

FOUNDATION
→ Mental model hoặc prerequisite nào phải có trước?

CORE MODEL
→ Các abstraction / building block chính là gì?

MECHANICS
→ Language/runtime/API làm concept hoạt động như thế nào?

USAGE
→ Concept được dùng trong code thực tế ra sao?

DECISION / TRADE-OFF
→ Khi nào chọn mechanism này thay vì mechanism khác?

FAILURE / PITFALL
→ Compile-time/runtime failure hoặc lỗi thường gặp nào quan trọng?

INTEGRATION
→ Topic liên hệ với neighboring concepts/modules thế nào?

SYNTHESIS
→ End-to-end mental model cuối cùng learner cần giữ lại là gì?
```

Đây là **design questions**, không phải visible headings bắt buộc trong ROADMAP hay Knowledge.

---

## 4. Trách nhiệm của Roadmap Designer và Reviewer

Giữ responsibility tách biệt:

```text
Roadmap Designer
→ chọn milestone-level learning journey
→ xác định learning order bên trong module
→ xác định dependency giữa các milestone khi cần

Roadmap Reviewer
→ kiểm tra prerequisite order
→ kiểm tra module scope / boundary
→ kiểm tra practical completeness
→ kiểm tra milestone granularity
→ kiểm tra beginner-friendliness

Knowledge Architect
→ consume ROADMAP đã approve
→ mở rộng milestone thành Menu / Knowledge structure
```

ROADMAP không phải Knowledge Menu và không thay thế Knowledge.

---

## 5. Granularity và contract của milestone

Roadmap node là **learning milestone cấp cao**, không phải từng fact, method, keyword, syntax item hoặc API nhỏ.

```text
ROADMAP milestone
→ một chặng học có mục đích rõ ràng
→ có thể được triển khai bởi nhiều Knowledge sections

Knowledge section
→ giải thích chi tiết concept / mechanism / API / edge case
```

Một method, keyword, một API nhỏ hoặc một isolated fact thông thường không nên trở thành milestone riêng.

Không biến ROADMAP thành API inventory.

Mỗi milestone nên xác định đủ các ý nghĩa sau:

```text
stable identity
title
learning purpose
why this milestone exists here
dependency on earlier milestones when relevant
what learner should understand before moving on
which conceptual area downstream Knowledge may expand
```

Ví dụ granularity hợp lý:

```text
Purpose
→ Type Model
→ Control Flow
→ Methods & Data Flow
→ Scope & Lifetime
→ Arrays
→ Synthesis
```

Quá nhỏ cho ROADMAP:

```text
add()
remove()
get()
size()
for
while
byte
short
```

Những nội dung nhỏ này thuộc Menu / Knowledge.

---

## 6. ROADMAP relation ownership

Roadmap relation metadata không được trở thành source-of-truth thứ hai.

```text
Roadmap Designer / Reviewer
→ milestone order
→ dependencies
→ optional relatedModules cho cross-module context/navigation

Knowledge Architect
→ mở rộng milestone thành Knowledge categories/sections
→ sau đó mới add/refine relatedKnowledge ids khi Knowledge structure đã rõ
```

### `relatedKnowledge`

`relatedKnowledge` chỉ trỏ tới Knowledge category ids trong **cùng module** để hỗ trợ navigation.

Nó:

```text
KHÔNG quyết định milestone order
KHÔNG được dùng để reverse-engineer ROADMAP
KHÔNG biến Knowledge category thành child roadmap node
```

Với legacy migration, provisional mapping tới category cũ có thể được dùng để migrate navigation, nhưng mapping đó không phải bằng chứng rằng historical Menu order là learning order đúng.

Ví dụ:

```yaml
roadmap:
  - id: type-system-basics
    title: Type System Basics
    relatedKnowledge:
      - PrimitiveReference
      - WrapperBoxing
      - Casting
```

Lifecycle đúng:

```text
design + approve milestone/order
        ↓
derive/refine Knowledge structure
        ↓
add/refine relatedKnowledge navigation mapping
```

### `relatedModules`

`relatedModules` chỉ là cross-module navigation/support metadata.

Nó:

```text
KHÔNG tạo nested roadmap graph
KHÔNG biến module khác thành child milestone
KHÔNG thay thế dependency/ownership ở area Curriculum
```

Ownership, boundary và dependency giữa các module thuộc [`STEP_1_CURRICULUM_RULES.md`](./STEP_1_CURRICULUM_RULES.md).

Ví dụ:

```yaml
roadmap:
  - id: type-system-basics
    title: Type System Basics
    purpose: ...
    relatedModules:
      - routeId: JAVA_OOP
        label: Object-Oriented Programming
        note: Type hierarchy, inheritance and polymorphism extend this model.
```

`routeId` là stable Portal/module identity dùng cho navigation. `label` và `note` là localized presentation text của roadmap language hiện tại.

---

## 7. New module — Build ROADMAP

Với **module mới**, thiết kế ROADMAP từ:

```text
approved area Curriculum Map — mandatory
+ module scope
+ prerequisites
+ neighboring-module boundaries
+ authoritative technical sources
+ canonical Roadmap Skeleton
```

Không generate course chỉ từ module name.

ROADMAP phải kiểm tra ít nhất:

```text
Module này thực sự dạy điều gì?
Learner cần mental model/prerequisite nào trước?
Các milestone lớn là gì?
Milestone nào phụ thuộc milestone nào?
Thứ tự nào hợp lý cho newbie?
Điểm kết thúc của module nằm ở đâu?
Concept nào phải handoff sang module khác?
```

---

## 8. Existing module — Review / Refactor ROADMAP

Với **existing/refactor module**, không build ROADMAP bằng cách copy Menu/Knowledge order hiện tại.

Flow đúng:

```text
approved area Curriculum / module boundary
        ↓
authoritative documentation
        ↓
design/review ROADMAP độc lập
        ↓
approve ROADMAP
        ↓
sau đó mới audit Knowledge cũ
```

Historical content có thể là evidence để kiểm tra coverage, nhưng không được mặc định trở thành curriculum source-of-truth.

Sau khi ROADMAP được approve mới audit Knowledge cũ theo các trạng thái:

```text
aligned
missing
misplaced
too deep
duplicate
out of scope
```

Roadmap review phải kiểm tra ít nhất:

```text
Có major milestone nào thiếu không?
Có milestone nào thừa, trùng hoặc quá nhỏ không?
Có milestone nào quá implementation/API-specific không?
Prerequisite order đã đúng chưa?
Learning order có phù hợp với beginner không?
Module boundary có khớp area Curriculum không?
Có scope leakage sang module owner khác không?
ROADMAP có practical concern quan trọng nào bị thiếu không?
VI wording có tự nhiên và tránh English filler không cần thiết không?
```

Nếu area-level ownership/boundary/dependency sai hoặc thiếu, đó là **CURRICULUM GAP** và phải quay lại Step 1 thay vì sửa cục bộ trong ROADMAP.

---

## 9. Mandatory authoritative-source rule cho Step 2

Trước khi build/review/refactor ROADMAP, phải tham khảo tài liệu chính thống của technology tương ứng.

Ví dụ:

```text
Java / Java Core
→ Oracle Java documentation / Java API documentation
→ Java Language Specification khi liên quan language semantics
→ OpenJDK / JEP khi behavior phụ thuộc Java version

Spring
→ spring.io / official Spring Reference Documentation

Technology khác
→ official specification / vendor documentation / project-maintained reference documentation
```

Official sources được dùng để kiểm chứng:

```text
terminology
scope
prerequisites
learning order
runtime/language semantics
version-sensitive behavior
important conceptual gaps
```

Repository implementation là evidence quan trọng, nhưng không phải curriculum authority duy nhất.

Trong quá trình research/review Step 2, đồng thời đánh giá source nào xứng đáng được publish vào `reference/references.yml`.

Phân biệt rõ:

```text
Research sources
→ có thể rộng
→ dùng để kiểm chứng ROADMAP / scope / semantics

Published Module Reference
→ curated subset
→ shared cho toàn module
→ learner-facing
```

Không bắt buộc mọi source đã consult phải xuất hiện trong Module Reference.

---

## 10. Roadmap review / approval gate

Không tạo Knowledge Menu hoặc lesson content mới trước khi ROADMAP vượt qua review gate.

Reviewer phải kiểm tra:

```text
missing prerequisite?
wrong learning order?
scope leakage into another module?
missing practical concern?
milestone too implementation-specific?
milestone too granular?
duplicate responsibility?
important concept isolated from its motivation?
learner can explain the module end-to-end after following the path?
```

Approval không có nghĩa ROADMAP bất biến tuyệt đối. Correctness issue thật sự vẫn có thể buộc phải sửa, nhưng downstream không được tự redesign ROADMAP mà không quay lại Step 2.

---

## 11. ROADMAP GAP

Nếu downstream Menu/Knowledge/API/Video/Quiz/Interview authoring phát hiện một prerequisite hoặc milestone quan trọng mà ROADMAP chưa có:

```text
phát hiện missing module-level milestone/prerequisite
        ↓
ROADMAP GAP
        ↓
quay lại Step 2 ROADMAP review
        ↓
approve thay đổi
        ↓
mới tiếp tục downstream
```

Không silently add một unrelated chapter hoặc đổi learning order downstream rồi coi ROADMAP cũ vẫn đúng.

Phân biệt:

```text
CURRICULUM GAP
→ vấn đề area/module ownership/boundary/dependency
→ quay lại Step 1

ROADMAP GAP
→ vấn đề learning journey bên trong một module đã có scope đúng
→ quay lại Step 2
```

---

## 12. Downstream contract

Sau khi ROADMAP được approve:

```text
Roadmap milestone
        ↓
Menu / Knowledge sections cần thiết để hoàn thành milestone
        ↓
Knowledge lesson content
        ↓
API Docs / Video / Quiz / Interview reinforcement
```

Downstream authoring phải hỏi:

> Learner cần những Knowledge sections nào để hoàn thành milestone này?

Không được đổi thành:

> Có những facts/API nào về topic này mà mình có thể liệt kê?

API Docs, Video, Quiz và Interview không được trở thành alternate curriculum designers:

```text
Knowledge
├── API Docs / experiments → prove observable behavior
├── Video                  → present one Knowledge Menu through scenes/visuals/narration
├── Quiz                   → test objectives / misconceptions
└── Interview              → test explanation / trade-offs / practical reasoning
```

Raw count không phải success metric. Traceability và learning coherence quan trọng hơn số lượng artifact.

---

## 13. Portal semantic contract

Portal là projection/consumer, không phải source-of-truth của ROADMAP.

Roadmap và Menu có hai vai trò khác nhau:

```text
Roadmap
→ learner đang ở đâu?
→ milestone lớn nào tiếp theo?
→ các major concepts liên hệ ra sao?
→ Knowledge category nào hỗ trợ milestone?
→ neighboring module nào có thể đi tiếp?

Menu
→ exact Knowledge chapter/section nào có thể mở?
```

Current semantic contract:

```text
milestone
→ Level 1 learning order

relatedKnowledge
→ same-module Knowledge navigation
→ luôn là supporting relation

relatedModules
→ cross-module navigation
→ secondary/satellite relation
→ không tạo nested curriculum graph
```

UI có thể thay đổi layout theo viewport, nhưng semantic distinction giữa milestone, `relatedKnowledge` và `relatedModules` phải được giữ.

---

## 14. Source-of-truth hierarchy

```text
STEP 1 Curriculum / module scope
        ↓
approved Module ROADMAP
        ↓
Menu / Knowledge structure
        ↓
Knowledge lessons
        ↓
API Docs / Video / Quiz / Interview
        ↓
Portal projection
```

Khi legacy module mâu thuẫn với ROADMAP mới đã approve, coi đó là migration/audit work. Không redefine ROADMAP chỉ để giữ accidental historical ordering.

---

## 15. Step 2 completion gate

Step 2 chỉ hoàn thành khi ROADMAP đủ ổn định để drive Menu/Knowledge.

Checklist:

```text
[ ] Area Curriculum/boundary đã được consume khi applicable
[ ] Official documentation đã được kiểm tra
[ ] ROADMAP không được reverse-engineer từ Menu cũ
[ ] Milestone ở đúng granularity
[ ] Major prerequisite đã có
[ ] Learning order có rationale rõ
[ ] Beginner có thể theo được journey
[ ] Scope không leak sang module khác
[ ] relatedKnowledge không quyết định curriculum
[ ] relatedModules chỉ là navigation/support
[ ] VI wording tự nhiên khi applicable
[ ] Không còn ROADMAP GAP đã biết nhưng chưa xử lý
```

Sau gate này mới chuyển sang Menu / Knowledge step.
