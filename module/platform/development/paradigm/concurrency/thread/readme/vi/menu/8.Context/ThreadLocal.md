<a id="back-to-top"></a>

# ThreadLocal & Context Propagation

## Menu
- [1. ThreadLocal isolation](#thread-local-isolation)
- [2. remove() và lifecycle của worker Thread](#thread-local-cleanup)
- [3. InheritableThreadLocal](#inheritable-thread-local)
- [4. Vì sao InheritableThreadLocal thất bại với Thread Pool?](#thread-pool-problem)
- [5. Context propagation: capture → restore → cleanup](#context-propagation)
- [6. Spring singleton bean và thread safety](#spring-bean-thread-safety)
- [7. Stack/Heap và context isolation](#object-memory)
- [8. Khi nào nên dùng ThreadLocal?](#thread-local-usage)
- [9. Các experiment của phần Context](#context-experiments)

`ThreadLocal<T>` cung cấp một cách gắn một value với **Thread hiện tại**.

Mental model nên dùng:

```text
Thread
→ có ThreadLocal-associated storage
→ ThreadLocal key dùng để truy cập value tương ứng của Thread đó
```

Không nên học thành:

```text
ThreadLocal = copy một biến shared cho từng Thread
```

và cũng không phải:

```text
ThreadLocal = công cụ giải quyết visibility của shared state
```

ThreadLocal thường **tránh chia sẻ** một loại context, thay vì đồng bộ truy cập vào cùng state.

## <a id="thread-local-isolation">1. ThreadLocal isolation</a>

<details>
<summary>Click for details</summary>

Ví dụ:

```java
ThreadLocal<String> requestId = new ThreadLocal<>();

requestId.set("REQ-123");
try {
    // code cùng Thread có thể requestId.get()
} finally {
    requestId.remove();
}
```

Hai Thread sử dụng cùng một `ThreadLocal` object có thể thấy hai value khác nhau.

Tham khảo:

```text
ThreadLocalController#isolation()
GET /context/isolation
```

Experiment tạo hai Thread, mỗi Thread set một request id khác nhau rồi đọc lại value của chính nó.

**Kết luận:** isolation đến từ Thread-local association, không phải từ lock hay visibility synchronization trên một shared value.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-local-cleanup">2. remove() và lifecycle của worker Thread</a>

<details>
<summary>Click for details</summary>

Worker trong pool có thể sống rất lâu và chạy nhiều task.

Nếu task làm:

```java
context.set(value);
```

nhưng không cleanup, task sau chạy trên cùng worker có thể gặp stale context hoặc giữ reference lâu hơn cần thiết.

Pattern an toàn:

```java
context.set(value);
try {
    task.run();
} finally {
    context.remove();
}
```

Đây là lý do context propagation wrapper / Spring `TaskDecorator` luôn cần bước restore + cleanup.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="inheritable-thread-local">3. InheritableThreadLocal</a>

<details>
<summary>Click for details</summary>

`InheritableThreadLocal` cho phép child Thread nhận initial value từ parent tại thời điểm child Thread được tạo.

Tham khảo:

```text
ThreadLocalController#inheritance()
GET /context/inheritance
```

Demo cố tình:

```text
parent = A
new Thread(...)
parent = B
child.start()
```

Child vẫn quan sát initial inherited value A.

**Kết luận:** inheritance gắn với **Thread creation**, không phải "mỗi khi submit task thì copy context mới".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-problem">4. Vì sao InheritableThreadLocal thất bại với Thread Pool?</a>

<details>
<summary>Click for details</summary>

Worker pool được tạo một lần rồi reuse:

```text
parent context = USER_A
submit task 1
        ↓
worker-1 được tạo → inherit USER_A

parent context = USER_B
submit task 2
        ↓
vẫn worker-1 cũ
→ không có child Thread mới để inherit USER_B
```

Tham khảo:

```text
ThreadLocalController#poolReuseProblem()
GET /context/pool-reuse-problem
```

Kết quả kỳ vọng:

```text
task1 = USER_A
task2 = USER_A
parent = USER_B
```

Đây là stale context do worker reuse.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-propagation">5. Context propagation: capture → restore → cleanup</a>

<details>
<summary>Click for details</summary>

Khi task được submit sang executor, context propagation thường cần ba bước:

```text
caller Thread
   ↓ capture context
wrap Runnable/Callable
   ↓
worker Thread
   ↓ restore context
run task
   ↓ finally
cleanup / restore previous context
```

Tham khảo:

```text
ThreadLocalController#explicitPropagation()
GET /context/explicit-propagation
```

Demo tạo worker trước khi parent set context, sau đó wrap task để worker vẫn nhận đúng context ở thời điểm submit.

Trong Spring, `TaskDecorator` là một nơi phù hợp để đóng gói pattern này cho `ThreadPoolTaskExecutor`; phần Spring Task Executor sẽ thực hành trực tiếp.

Ngoài việc tự wrap task hoặc dùng `TaskDecorator`, hệ sinh thái Java còn có các giải pháp propagation chuyên dụng. Ví dụ `TransmittableThreadLocal` của Alibaba mở rộng ý tưởng capture/replay context cho executor reuse. Tuy nhiên đây là **library-specific solution**, không thay đổi mental model cốt lõi:

```text
capture context tại submission boundary
→ restore trước khi task chạy
→ cleanup/restore previous state sau task
```

Vì vậy chapter này ưu tiên explicit propagation và Spring `TaskDecorator`; library bên thứ ba chỉ nên được chọn khi semantics và integration của nó phù hợp với application thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-bean-thread-safety">6. Spring singleton bean và thread safety</a>

<details>
<summary>Click for details</summary>

Spring singleton chỉ nói rằng container thường dùng chung một bean instance.

Điều đó không tự động làm bean thread-safe hay thread-unsafe.

Một stateless service kiểu:

```java
@Service
class PriceService {
    BigDecimal calculate(Order order) {
        // chỉ dùng local variables và dependency thread-safe phù hợp
    }
}
```

tránh được một nhóm lớn race condition vì không giữ mutable request-specific state trong field.

Nhưng không nên nói "stateless bean an toàn tuyệt đối". Service vẫn có thể thao tác shared database row, mutable argument, external resource hoặc dependency không thread-safe.

### Bean scope không phải Thread scope

Spring còn có các scope như singleton, request và session, nhưng scope mô tả **lifecycle/lookup boundary của bean**, không phải một concurrency guarantee.

Mental model:

```text
singleton
→ một bean instance được dùng rộng trong application context

request scope
→ bean instance gắn với lifecycle của một HTTP request

session scope
→ bean instance gắn với HTTP session
```

Không nên biến thành:

```text
request scope = mỗi Thread một object
session scope = mỗi Thread một object
```

Request và Thread là hai lifecycle khác nhau. Async processing có thể chuyển execution sang Thread khác; framework dùng context/proxy để resolve scoped object theo request semantics chứ không phải vì bean "thuộc" một worker thread.

Đặc biệt, session-scoped mutable state không tự động thread-safe: cùng một user/session có thể có nhiều request concurrent. Bean scope không thay thế synchronization hoặc thiết kế tránh shared mutable state.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-memory">7. Stack/Heap và context isolation</a>

<details>
<summary>Click for details</summary>

Object Java như entity, DTO, collection vẫn là object trên heap.

Thread có stack riêng chứa frame/local reference, nhưng việc một reference là local variable không tạo ra một "local heap" riêng cho object.

Thread safety phụ thuộc object có bị shared/reachable từ nhiều Thread và có mutable state hay không.

JPA persistence context / transaction isolation là framework/database semantics riêng; không nên giải thích chúng bằng khái niệm "mỗi Thread có heap riêng".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-local-usage">8. Khi nào nên dùng ThreadLocal?</a>

<details>
<summary>Click for details</summary>

Các use case phổ biến:

- request/correlation id;
- logging MDC (bản thân MDC thường dựa trên thread-bound context);
- security/request context trong framework;
- transaction/session context do framework quản lý.

Không nên dùng ThreadLocal để biến mọi parameter thành implicit global state. Explicit parameter thường dễ test và reasoning hơn nếu context không thực sự cần thread-bound semantics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="context-experiments">9. Các experiment của phần Context</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#thread-local-isolation` | `ThreadLocalController#isolation()` | `GET /context/isolation` |
| `#inheritable-thread-local` | `ThreadLocalController#inheritance()` | `GET /context/inheritance` |
| `#thread-pool-problem` | `ThreadLocalController#poolReuseProblem()` | `GET /context/pool-reuse-problem` |
| `#context-propagation` | `ThreadLocalController#explicitPropagation()` | `GET /context/explicit-propagation` |

</details>

- [Quay lại đầu trang](#back-to-top)
