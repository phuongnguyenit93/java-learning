# Step 5 - API Rules

Step 5 xây hoặc refactor API learning documentation khi module thực sự có learning API. Step number này **giống nhau cho new và refactor module**.

### General Agent Rules context guard

Trước khi thực hiện Step 5:

```text
Nếu context của GENARAL_AGENT_RULES.md vẫn còn rõ ràng trong working context
→ không cần đọc lại

Nếu context đã bị loại khỏi cửa sổ làm việc, bị quên, bị truncate,
hoặc agent không chắc mình còn nhớ đầy đủ các global rules
→ đọc lại ./GENARAL_AGENT_RULES.md trước khi tiếp tục
```

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

This step is conditional on the module exposing learning APIs.

For current repository conventions:

```text
MODULE_TYPE = SERVLET
or
MODULE_TYPE = REACTIVE
```

means the agent must explicitly inspect controllers/endpoints and determine whether the module contains learning APIs that should participate in Swagger/API Docs.

Do not assume every endpoint is a learning experiment. Infrastructure, health, support, internal, generated or framework-only endpoints must not force artificial curriculum mappings.

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

Every meaningful learning API in a SERVLET/REACTIVE learning module should illustrate, prove, observe or exercise a real Knowledge concept.

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
```

Downstream:

```text
Knowledge + finalized API mappings
        ↓
STEP_6_QUIZ.md / STEP_7_INTERVIEW.md
```
