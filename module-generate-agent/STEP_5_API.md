# Step 5 - API Rules

Step 5 đánh giá, xây hoặc refactor API learning surface dựa trên **learning design đã được approve**. Step number này **giống nhau cho new và refactor module**.

### General Agent Rules + upstream learning context guard

Trước khi thực hiện Step 5:

```text
Nếu context của GENERAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENERAL_AGENT_RULES.md trước khi tiếp tục
```

Step 5 phải nắm context của các upstream artifact sau **khi chúng tồn tại trong workflow hiện tại**:

```text
relevant ./temp/*_CURRICULUM_MAP.md
+ approved Step 2 ROADMAP
+ approved Step 3 Menu
+ completed/reviewed Step 4 Knowledge
```

Step 5 applicability phải được quyết định từ các upstream learning artifacts này trước khi nhìn config/runtime implementation hiện tại.

Nếu Curriculum Map không tồn tại và Step 5 được gọi như một **isolated API applicability/review/refactor step**, không block chỉ vì thiếu file. Quyết định applicability từ current Roadmap/Menu/Knowledge/source/runtime evidence cộng authoritative external documentation và real-world behavior. Current config (`MODULE_TYPE`, `BUILD_SWAGGER`, controller hiện có) vẫn chỉ là implementation evidence, không được biến thành curriculum authority.

Nếu đang chạy canonical full flow bắt đầu từ Step 1 thì không dùng fallback này để skip Step 1. Nếu isolated fallback không resolve được ownership/boundary, report **CURRICULUM CONTEXT GAP**. Nếu API experiment cần concept/milestone chưa tồn tại, route về đúng upstream gap thay vì tự mở rộng curriculum trong Step 5.

Không suy đoán các orchestration, preservation, gap-routing hoặc cross-step rules từ trí nhớ khi context không còn chắc chắn.

Canonical sources:

```text
module/.../src/main/resources/swagger/{lang}/controller-description.yml
module/.../src/main/resources/swagger/{lang}/api-descriptions.yml
module/.../src/main/resources/swagger/{lang}/api-execution.yml
module/.../src/main/resources/swagger/{lang}/api-params.yml
```

---

## Step 5 — Build API learning documentation and connect API to Knowledge

Step 5 là conditional learning surface, nhưng quyết định **có cần API hay không phải đến từ learning value**, không phải trạng thái repository hiện tại.

### Applicability source of truth

`Curriculum` below means the real Step 1 Curriculum artifact when present. In isolated fallback mode without that file, use the reconstructed scope/boundary established from current repository evidence + authoritative real-world knowledge; do not invent a fake Curriculum artifact.

```text
Curriculum
        ↓
ROADMAP
        ↓
Menu
        ↓
Knowledge
        ↓
API learning-value evaluation
        ↓
STEP 5 REQUIRED / NOT REQUIRED
```

Tuyệt đối **không** dùng các trạng thái hiện tại sau làm authority để quyết định Step 5 có cần hay không:

```text
MODULE_TYPE
BUILD_SWAGGER
current build.gradle
existing dependencies
existing controller/service/source layout
presence/absence of @RestController / @Controller
presence/absence of @RequestMapping / @GetMapping / @PostMapping / ...
presence/absence of src/main/resources/swagger
presence/absence of existing API metadata
neighboring modules currently using LIBRARY/SERVLET/REACTIVE
```

Các trạng thái trên chỉ mô tả **current implementation state**. Chúng có thể:

```text
đúng
incomplete
stale
được cấu hình từ curriculum cũ
hoặc đã bị hiểu/sửa sai trước đó
```

Do đó:

```text
NEVER:
current config
→ infer curriculum
→ decide Step 5

ALWAYS:
approved learning design
→ decide Step 5
→ reconcile implementation/config
```

Ví dụ, đây **không phải** lý do hợp lệ để skip Step 5:

```text
MODULE_TYPE = LIBRARY
BUILD_SWAGGER = FALSE
src/main/java is empty
no controller exists
no swagger resources exist
```

Nếu Knowledge cho thấy một executable API experiment có learning value cao, các trạng thái trên được hiểu là **implementation chưa support curriculum**, không phải bằng chứng API không cần thiết.

Ngược lại, đây cũng không phải lý do bắt buộc phải giữ Step 5:

```text
BUILD_SWAGGER = TRUE
controller already exists
swagger metadata already exists
```

Nếu API không còn tạo learning value theo Curriculum/Roadmap/Knowledge hiện tại, implementation cũ có thể là stale và cần được cleanup/reconciled.

Do not assume every endpoint is a learning experiment. Infrastructure, health, support, internal, generated or framework-only endpoints must not force artificial curriculum mappings.

---

## Step 5 applicability decision

Trước khi inspect config hiện tại, review toàn bộ Knowledge surface và hỏi:

```text
Có concept nào mà learner sẽ hiểu tốt hơn đáng kể
nếu được trigger/observe qua một bounded executable API experiment không?
```

Decision:

```text
NO
→ STEP 5 learning API is not required
→ do not create Swagger/API solely because this step exists
→ inspect current implementation only to find stale API/config that contradicts this decision

YES
→ STEP 5 learning API is required
→ define experiment surface from Knowledge
→ only then inspect current repository implementation
→ repair/enable required capability
→ build/refactor API + documentation
```

Step 5 applicability phải được explain bằng **Knowledge concept + expected observable evidence**, ví dụ:

```text
Concept
→ what learner needs to observe
→ why static Knowledge/code example is insufficient
→ why an API-triggered experiment adds value
```

Không dùng câu trả lời kiểu:

```text
"module is LIBRARY"
"Swagger is disabled"
"there is no controller"
"other modules do not expose APIs"
```

để kết luận Step 5 không cần.

---

## Step 5 conditional independent-review count (API applicability exception)

**This Step-specific exception overrides the generic two-review minimum ONLY when the initial Step 5 implementation/applicability pass concludes that no learning API should be built.** It applies per module/child, not to an entire parent batch based on one child's result.

```text
INITIAL STEP 5 IMPLEMENTATION/APPLICABILITY PASS
        ↓
NOT REQUIRED — no learning API to build
        ↓
ONE fresh independent FULL applicability review is required
        ├─ confirms NOT REQUIRED
        │    → fix/reconcile any valid stale config/API findings
        │    → revalidate as required
        │    → STEP 5 CLEAN with ONE independent review
        │
        └─ finds learning API IS REQUIRED
             → initial NOT REQUIRED decision is invalidated
             → build/refactor the justified API + docs from Knowledge
             → complete required config/setup/build/projection validation
             → TWO NEW sequential independent FULL reviews of POST-BUILD state
             → fix/revalidate between reviews under normal governance
             → STEP 5 CLEAN only after both post-build reviews pass

INITIAL STEP 5 PASS = REQUIRED
        → build/refactor API + validate
        → normal TWO independent full reviews
```

Rules:

- The single no-API review **must still be independent of the initial Step 5 decision/author**, inspect current approved Curriculum/Roadmap/Menu/Knowledge and API learning value, then check current runtime/config for stale contradictions. A self-check or `BUILD_SWAGGER=FALSE` is **not** this review.
- If the independent reviewer confirms `NOT REQUIRED`, the reviewer may correct valid no-API cleanup/config findings with exclusive write ownership and revalidate. Do not impose an automatic second review merely because no-API cleanup was required; route substantial changed-scope/ownership concerns through normal governance.
- If **any later review/recheck** overturns the no-API result and establishes `REQUIRED`, the exception **immediately ends**: implement the API and restart the **full two-review sequence on the completed post-build source**. The earlier no-API applicability review **does not count** as Review #1 or Review #2 of the built API.
- Both post-build reviews must be fresh, full, sequential source-state reviews with an eligible independent reviewer **different from the actual API builder** (and satisfying the active workflow's original-author independence rule), fixes and revalidation in between as necessary; they must not run in parallel on one pre-fix snapshot. If the initial no-API reviewer becomes the API builder, they cannot review their own built API.
- A false-negative `NOT REQUIRED` decision must not be kept merely to save one review. The decision continues to depend on learning value, not current module config.
- Preserve the active parent-wide implementation/review eligibility barriers, per-child worktree/write isolation and subsequent STEP transition gate. This changes **only the per-child minimum independent-review count for confirmed no-API Step 5**.

---

## Reconcile module configuration with the learning decision

Sau khi Step 5 applicability đã được quyết định từ learning design, agent phải audit implementation hiện tại.

### If Step 5 is required

Repository configuration phải được chỉnh để support learning API thực tế.

Review ít nhất:

```text
module/master.json
→ MODULE_TYPE
→ BUILD_SWAGGER
→ other required BUILD_* capabilities

module runtime/source structure
→ controller
→ service/experiment implementation
→ resources
→ dependencies derived by repository setup

Swagger source
→ src/main/resources/swagger/{lang}/...
```

Canonical rule:

```text
learning design requires API
        ↓
derive required runtime architecture
        ↓
fix canonical module config
        ↓
run repository setup/sync
        ↓
let generated/derived files follow canonical config
        ↓
author/refactor API experiment
```

Do **not** blindly set every API module to `SERVLET`.

Choose runtime architecture from the experiment:

```text
Spring MVC / Servlet learning endpoint
→ MODULE_TYPE = SERVLET

Spring WebFlux / reactive endpoint
→ MODULE_TYPE = REACTIVE

No executable HTTP learning surface justified
→ do not force a web runtime merely to satisfy Step 5
```

For the current repository implementation, Swagger adapters are selected from `MODULE_TYPE` only when `BUILD_SWAGGER=TRUE`. Therefore, if an in-module Swagger learning API is required, these values must be made mutually consistent rather than leaving a stale combination such as:

```text
MODULE_TYPE = LIBRARY
BUILD_SWAGGER = TRUE
```

when the intended experiment actually requires a runnable Servlet/Reactive web stack.

Prefer editing **canonical human-owned config values** such as `master.json`, then run the repository's normal synchronization/setup so derived configuration/resources are regenerated correctly. Do not hand-patch generated projections merely to make the build pass.

If enabling Step 5 reveals missing dependencies/resources/source folders that should be generated by repository setup, fix the canonical capability/config first and let repository automation materialize them.

### If Step 5 is not required

Current implementation must still be checked for stale contradictions.

Examples:

```text
BUILD_SWAGGER = TRUE but no learning API is justified
legacy learning controller no longer maps to approved Knowledge
swagger metadata refers to removed/out-of-scope concepts
module runtime type was changed only to support obsolete API demos
```

Do not silently keep stale capability just because it already exists.

Reconcile it safely:

```text
approved learning design
→ no API learning surface
→ identify stale API/config
→ remove/disable only when ownership and downstream impact are understood
→ validate build/projection afterward
```

Preservation rules still apply: historical human-owned content must not be silently deleted. Classify stale/out-of-scope artifacts explicitly before removal/refactor.

### Configuration is downstream, not curriculum authority

```text
Curriculum / Roadmap / Menu / Knowledge
        ↓
Step 5 applicability
        ↓
required API architecture
        ↓
MODULE_TYPE / BUILD_SWAGGER / source / dependencies / swagger resources
```

Never reverse this arrow.

## API learning value và experiment suitability

Không phải Knowledge concept nào cũng nên được biến thành API. Step 5 phải ưu tiên **learning value của experiment**, không ưu tiên số lượng endpoint hoặc relation coverage.

Trước khi tạo hoặc giữ một learning API, hỏi câu này trước:

```text
API này có tạo ra một observation
mà learner khó thấy chỉ bằng cách đọc Knowledge không?
```

Nếu câu trả lời là **NO**:

```text
Knowledge may be enough
→ do not create / preserve an API solely for coverage
```

Nếu câu trả lời là **YES**:

```text
design a bounded executable experiment
→ make the behavior observable
→ explain the evidence
→ connect the result back to Knowledge
```

### Phân loại concept theo API value

Khi review một module, có thể phân loại concept như sau:

```text
HIGH API VALUE
→ có action / state transition / runtime behavior rõ
→ response, exception, trace, timing hoặc state có thể chứng minh concept
→ API experiment thường có giá trị cao

CONDITIONAL API VALUE
→ một phần concept có thể quan sát ở runtime
→ nhưng phần quan trọng khác nằm ở compile-time semantics / type rules / source structure
→ chỉ giữ API nếu experiment bổ sung observation thật sự

KNOWLEDGE-FIRST
→ concept chủ yếu là syntax, grammar, compile-time-invalid construct,
  formal type relationship hoặc terminology
→ Knowledge / code example thường phù hợp hơn executable API
```

Không biến classification này thành quota. Nó là decision framework để tránh ép mọi concept thành endpoint.

### Runtime-observable vs compile-time semantics

Executable API tự nhiên phù hợp nhất với behavior có thể xảy ra và quan sát ở runtime, ví dụ:

```text
state before / after
exception
thread / worker identity
queue size / rejection
timing / ordering
mutation
shared state
runtime type
resource lifecycle
observable side effect
```

Ngược lại, các rule như:

```text
compile-time error
overload ambiguity chỉ tồn tại khi compile
generic invariance
definite assignment rule
illegal syntax / grammar
formal type compatibility
```

không nên bị "giả runtime" chỉ để tạo API. Nếu phần cốt lõi của concept không thể thực thi trong một chương trình hợp lệ, ưu tiên Knowledge + focused code example. API chỉ nên tồn tại nếu nó còn chứng minh được một phần runtime consequence quan trọng mà learner thực sự cần quan sát.

### Experiment-first design

Một API Docs trực quan nên được thiết kế giống một thí nghiệm hơn là một trang lý thuyết chạy qua HTTP.

Ưu tiên flow:

```text
Setup
→ Trigger
→ State / behavior changes
→ Observable evidence
→ Conclusion
```

Ví dụ pattern tốt:

```text
prepare bounded state
→ perform one meaningful action
→ expose before/after, failure, trace, timing or identity
→ explain why the observation proves the concept
```

Tránh pattern:

```text
repeat theory in description
→ execute code that adds no new observation
→ return a value that merely restates the expected rule
```

Nếu endpoint chỉ biến một Knowledge sentence thành JSON mà không tạo evidence mới, đó thường là dấu hiệu API có learning value thấp.

### Scenario và state transition

Khi concept cho phép, ưu tiên experiment có một scenario cụ thể và state transition rõ ràng:

```text
before
→ action
→ after
```

hoặc:

```text
actor A / actor B / resource
→ interaction
→ observable result
```

Điều này đặc biệt hữu ích cho concurrency, lifecycle, collections, mutable state, resource management, exceptions và các topic có behavior theo thời gian.

Với concept ít "chuyển động" hơn, không cố tạo timeline giả. Hãy chọn observation phù hợp với bản chất concept hoặc để concept ở Knowledge-first.

### API count is not a success metric

Module có ít API nhưng mỗi API tạo được observation rõ ràng tốt hơn module có nhiều endpoint mà phần lớn chỉ minh họa syntax hoặc lặp lại Knowledge.

```text
fewer high-value experiments
>
many low-value executable explanations
```

#### API ↔ Knowledge invariant

For a learning API, the relationship is:

```text
Knowledge chapter
      ↓
Knowledge anchored section
      ↓
Controller / method experiment
```

Every meaningful learning API should illustrate, prove, observe or exercise a real Knowledge concept. If the experiment requires a Servlet/Reactive runtime, Step 5 must ensure the module configuration actually provides that runtime.

For an executable learning experiment, keep the runtime evidence intentionally bounded and observable:

```text
one primary concept per experiment when practical
→ behavior must actually prove or illustrate the Knowledge claim
→ response / state / trace / timing / exception should make the result observable
→ ordinary demos must clean up threads, executors, streams and other resources they create
→ intentionally unsafe demos such as race/deadlock/leak examples must be clearly labeled and bounded/controllable when possible
→ repeated execution should remain meaningful and must not silently depend on stale state from a previous run
```

Controller, service and documentation roles should remain distinct:

```text
Knowledge
→ explains the concept and expected mental model

Controller / endpoint
→ triggers the experiment

Service / implementation code
→ implements the behavior being demonstrated

Response / console / state / timing
→ provides observable evidence
```

Do not copy the full theory into Java source comments. Source code is evidence for the learning claim, not a second canonical explanation.

The agent must therefore ask:

```text
What concept does this API teach?
        ↓
Does that concept already exist in Knowledge?
        ├── YES
        │   → map the API to the exact Knowledge file/anchor
        │
        └── NO
            ↓
        Is this concept genuinely part of the module curriculum?
            ├── YES
            │   → treat it as a Knowledge coverage gap
            │   → fix Knowledge first
            │   → then map the API
            │
            └── NO
                → do not manufacture a Knowledge section merely to obtain a mapping
```

Never solve a missing relation by selecting the nearest-sounding README section.

Controller-level relationship belongs in `controller-description.yml` and identifies the chapter/file. Method-level relationship belongs in `api-descriptions.yml` and identifies the exact anchored section, with optional file override only when the method truly belongs to another chapter.

Use exact relationships only:

```text
controller → exact README file
method     → exact methodSignature + exact README anchor
```

Do not auto-map from:

```text
controller name
method name
package name
summary text
semantic similarity
```

If no exact relation exists and the concept should not be added to Knowledge, leave the relation blank rather than inventing one.

#### API description responsibilities

Keep these roles distinct:

```text
summary
→ what topic/API is this?

description
→ what does this experiment demonstrate or teach?

execution
→ how does the experiment unfold, why does it behave that way,
  what evidence proves it, and what should the learner conclude?
```

For nontrivial APIs, `api-execution.yml` should normally cover, when applicable:

```text
Concept
Flow
Meaning
Code evidence
Observation
Conclusion
```

Code evidence must support the learning explanation rather than replace it. Prefer small snippets colocated with the runtime step they prove. Avoid copying whole classes/methods unnecessarily.

Preserve human-owned Swagger metadata and stale historical entries according to repository rules. Method identity is the exact method signature, not method name alone.

At the end of the API step, API Docs should read as the **practical experiment layer of Knowledge**, not as an unrelated generated Swagger reference.

---

## Completion gate

Step 5 hoàn thành khi:

```text
[ ] Applicability được quyết định từ Curriculum/Roadmap/Menu/Knowledge; khi Curriculum Map không tồn tại trong isolated fallback thì dùng reconstructed scope + Roadmap/Menu/Knowledge/current evidence, không từ config hiện tại
[ ] Có rationale rõ vì sao Step 5 REQUIRED hoặc NOT REQUIRED
[ ] MODULE_TYPE / BUILD_SWAGGER / source hiện tại chỉ được dùng như implementation evidence
[ ] Nếu Step 5 REQUIRED, canonical module config đã được sửa để support runtime/API capability cần thiết
[ ] Nếu Step 5 NOT REQUIRED, stale API/config contradiction đã được review/reconcile
[ ] Review count đúng nhánh: initial NOT REQUIRED + independently confirmed NOT REQUIRED → one full independent review; REQUIRED ngay từ đầu hoặc discovered later → two independent POST-BUILD full reviews (earlier no-API review not counted)
[ ] Repository setup/sync đã được chạy khi canonical config thay đổi
[ ] Generated/derived config không bị hand-edit thay cho source-of-truth config
[ ] Mỗi learning API có learning intent rõ
[ ] API tạo observation/evidence thật sự thay vì chỉ lặp lại Knowledge
[ ] Concept được đánh giá đúng mức API value thay vì ép mọi Knowledge section thành endpoint
[ ] Compile-time/semantic concept không bị "giả runtime" chỉ để tăng coverage
[ ] Experiment có setup → trigger → observation → conclusion khi bản chất topic phù hợp
[ ] Response / state / trace / timing / exception làm behavior đủ observable
[ ] Relation tới Knowledge là exact hoặc intentionally blank
[ ] Execution giải thích vì sao evidence chứng minh concept
[ ] Không có API giả được tạo chỉ để tăng count hoặc relation coverage
[ ] Resource / unsafe experiment được bounded và cleanup đúng cách
[ ] Build/runtime/API projection được validate sau khi capability/config thay đổi
```

Downstream:

```text
Knowledge + finalized API mappings
        ↓
STEP_6_VIDEO.md
        ↓
STEP_7_QUIZ.md / STEP_8_INTERVIEW.md
        ↓
STEP_9_VALIDATION.md
```
