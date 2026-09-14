<a id="back-to-top"></a>

# Thread & Concurrency - Q&A / Tổng hợp

## Menu
- [1. Bản đồ kiến thức tổng thể](#mental-map)
- [2. Thread, Runnable, Callable và Executor khác nhau thế nào?](#thread-vs-task)
- [3. run() và start() khác nhau thế nào?](#run-vs-start)
- [4. interrupt() có kill Thread không?](#interrupt-question)
- [5. Stack, Heap và thread safety có quan hệ thế nào?](#stack-heap-thread-safety)
- [6. Vì sao local variable capture trong lambda phải final/effectively final?](#effectively-final)
- [7. volatile, Atomic và synchronized chọn thế nào?](#volatile-atomic-sync)
- [8. Race condition và visibility bug khác nhau thế nào?](#race-vs-visibility)
- [9. Happens-before có nghĩa A chạy trước B theo đồng hồ không?](#happens-before-question)
- [10. Synchronization và Coordination khác nhau thế nào?](#synchronization-vs-coordination)
- [11. Vì sao wait() phải nằm trong while?](#wait-notify-question)
- [12. BlockingQueue khác ConcurrentLinkedQueue thế nào?](#blocking-queue-question)
- [13. Queue của ThreadPoolExecutor đầy thì caller có tự block không?](#thread-pool-question)
- [14. Async có phải Non-blocking không?](#async-question)
- [15. Future và CompletableFuture khác nhau ở đâu?](#future-question)
- [16. ThreadLocal có làm code thread-safe không?](#thread-local-question)
- [17. @Async có tự tạo Thread mới mỗi lần không?](#spring-async-question)
- [18. Khi nào một Thread/Pool được coi là leak?](#leak-question)
- [19. Virtual Thread có thay Thread Pool không?](#virtual-thread-question)
- [20. Schedule có quan hệ gì với Thread?](#schedule-thread)
- [21. Schedule wake-up có phải interrupt không?](#schedule-interrupt)
- [22. Batch và Schedule khác nhau thế nào?](#batch-schedule)
- [23. Checklist trước khi viết code concurrency](#final-checklist)
- [24. Kết luận module](#module-conclusion)

Phần cuối dùng để kiểm tra lại mental model của toàn module, không giới thiệu thêm một framework concurrency mới.

## <a id="mental-map">1. Bản đồ kiến thức tổng thể</a>

<details>
<summary>Click for details</summary>

```text
Thread lifecycle
    ↓
Interruption / Cancellation
    ↓
Shared mutable state
    ↓
Atomicity / Visibility / Ordering / JMM
    ↓
Synchronization
    ↓
Coordination
    ↓
Executor / Thread Pool
    ↓
Future / CompletableFuture
    ↓
ThreadLocal / Context propagation
    ↓
Spring TaskExecutor / @Async
    ↓
Lifecycle / Leak
    ↓
Virtual Thread
```

Nếu một vấn đề concurrency xuất hiện, trước tiên hãy xác định nó thuộc nhóm nào thay vì chọn tool theo tên API.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-vs-task">2. Thread, Runnable, Callable và Executor khác nhau thế nào?</a>

<details>
<summary>Click for details</summary>

```text
Runnable / Callable
→ mô tả task

Thread
→ một execution flow

Executor
→ abstraction quyết định cách task được thực thi

Future / CompletableFuture
→ abstraction của completion/result
```

`Callable` không phải một "loại Thread".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="run-vs-start">3. run() và start() khác nhau thế nào?</a>

<details>
<summary>Click for details</summary>

```text
thread.run()
→ method call bình thường trên caller thread

thread.start()
→ bắt đầu lifecycle của Thread
→ JVM gọi run() trên execution mới
```

Một `Thread` object chỉ start một lần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interrupt-question">4. interrupt() có kill Thread không?</a>

<details>
<summary>Click for details</summary>

Không.

`interrupt()` gửi cooperative cancellation signal.

Code đang chạy phải:

- kiểm tra interrupt status; hoặc
- đang ở interruptible blocking operation; hoặc
- propagate/restore interruption đúng cách.

Một tight loop không kiểm tra interrupt sẽ không tự dừng chỉ vì caller gọi `interrupt()`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stack-heap-thread-safety">5. Stack, Heap và thread safety có quan hệ thế nào?</a>

<details>
<summary>Click for details</summary>

Mỗi Thread có stack riêng.

Local variable nằm trong stack frame, nhưng local variable có thể chứa reference tới object trên heap.

Ví dụ:

```java
List<String> list = sharedList;
```

`list` là local reference, nhưng object mà nó trỏ tới vẫn có thể được nhiều Thread cùng truy cập.

Vì vậy:

```text
local variable
≠ tự động thread-safe
```

Điều cần hỏi là object/state có bị shared mutable access hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="effectively-final">6. Vì sao local variable capture trong lambda phải final/effectively final?</a>

<details>
<summary>Click for details</summary>

Đây trước hết là **Java language/closure semantics**, không phải vì JVM "bắt local variable phải thread-safe".

Ví dụ hợp lệ:

```java
int taskId = i;
executor.execute(() -> use(taskId));
```

nếu `taskId` không bị gán lại sau khi khởi tạo.

Lambda capture value/reference của local variable theo rule effectively-final để tránh semantics mơ hồ của mutable local stack variable sau khi enclosing method tiếp tục/thoát.

Điều đó không làm object được capture trở thành immutable:

```java
final List<String> list = new ArrayList<>();
```

reference không đổi nhưng nội dung list vẫn mutable.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="volatile-atomic-sync">7. volatile, Atomic và synchronized chọn thế nào?</a>

<details>
<summary>Click for details</summary>

### volatile

Phù hợp cho visibility/order của một state variable khi update không cần compound atomic invariant.

```text
stop flag
ready flag
published configuration reference
```

### Atomic

Phù hợp cho atomic state transition đơn giản:

```text
counter
CAS reference update
```

CAS chỉ kiểm tra expected value/reference hiện tại. Với reference có thể xảy ra ABA (`A → B → A`): plain CAS có thể thấy state cuối lại là A và không biết đã có thay đổi ở giữa. Khi version của state là một phần correctness, cân nhắc abstraction như `AtomicStampedReference` thay vì mặc định plain `AtomicReference` đủ cho mọi protocol.

### synchronized / Lock

Phù hợp khi cần bảo vệ critical section hoặc invariant nhiều bước.

Không dùng `volatile int count` rồi kỳ vọng `count++` atomic.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-vs-visibility">8. Race condition và visibility bug khác nhau thế nào?</a>

<details>
<summary>Click for details</summary>

```text
Atomicity problem
→ operation bị interleave
→ lost update

Visibility problem
→ Thread không có guarantee thấy write của Thread khác

Ordering problem
→ cross-thread observation không có ordering guarantee phù hợp
```

Ba nhóm có thể xuất hiện cùng nhau, nhưng không nên trộn thành một khái niệm "CPU cache issue".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="happens-before-question">9. Happens-before có nghĩa A chạy trước B theo đồng hồ không?</a>

<details>
<summary>Click for details</summary>

Không chỉ như vậy.

Happens-before là relation của Java Memory Model mang visibility/ordering guarantee.

Các edge quan trọng đã học:

- program order;
- monitor unlock → later lock;
- volatile write → subsequent volatile read;
- `Thread.start()`;
- successful `Thread.join()`;
- submission vào `Executor` → task execution;
- asynchronous computation → code sau successful `Future.get()`;
- release/acquire edge của các synchronizer như latch/semaphore/lock;
- transitivity.

### Safe publication và final field liên quan gì tới happens-before/JMM?

Việc `new` xong một object không tự trả lời câu hỏi Thread khác nhận reference đó qua memory-consistency boundary nào. Publication nên đi qua một cơ chế có contract rõ ràng như volatile, lock/monitor, concurrent collection, `Thread.start()`, executor submission hoặc boundary tương đương.

`final` field có initialization-safety đặc biệt trong JMM nếu object được construct đúng cách và `this` không escape trước khi constructor hoàn tất. Điều đó làm immutable object với final fields dễ reasoning hơn trong concurrent code, nhưng không biến các mutable field hay mutation xảy ra sau constructor thành thread-safe.

Rule cần nhớ:

```text
construct hoàn chỉnh
→ không để this escape sớm
→ publish qua boundary rõ ràng
→ mới share object giữa các Thread
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-vs-coordination">10. Synchronization và Coordination khác nhau thế nào?</a>

<details>
<summary>Click for details</summary>

```text
Synchronization
→ ai được chạm shared state tại thời điểm nào?

Coordination
→ Thread nào phải chờ event/phase/Thread khác?
```

`synchronized` bảo vệ counter là synchronization.

`CountDownLatch` đợi hai worker hoàn thành là coordination.

Một chương trình thực tế thường dùng cả hai.

### Fair lock có nghĩa mọi cách acquire đều fair không?

Không. Ví dụ `new ReentrantLock(true)` cấu hình fair lock, nhưng untimed `tryLock()` vẫn là opportunistic acquisition và có thể barge nếu lock đang available. Timed `tryLock(timeout, unit)` mới tham gia fair ordering policy khi phải chờ. Với `ReentrantReadWriteLock`, non-blocking `ReadLock.tryLock()` / `WriteLock.tryLock()` cũng không nên được coi là fairness-preserving admission.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="wait-notify-question">11. Vì sao wait() phải nằm trong while?</a>

<details>
<summary>Click for details</summary>

Notification không phải proof rằng condition đã đúng.

Pattern:

```java
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
}
```

giúp xử lý:

- spurious wakeup;
- nhiều waiter cạnh tranh;
- state có thể thay đổi trước khi waiter reacquire monitor.

Với `LockSupport.park()` cũng cần condition/cancellation loop. `park()` có thể return vì permit, interrupt hoặc spurious return và không ném `InterruptedException`; caller phải tự kiểm tra interrupt status/predicate.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="blocking-queue-question">12. BlockingQueue khác ConcurrentLinkedQueue thế nào?</a>

<details>
<summary>Click for details</summary>

`ConcurrentLinkedQueue` là concurrent non-blocking queue API; `poll()` khi rỗng trả `null`.

`BlockingQueue` có condition-waiting semantics như `put/take` và timed operations.

Nếu Producer–Consumer cần backpressure/waiting rõ ràng, `BlockingQueue` thường phù hợp hơn việc tự busy-poll `ConcurrentLinkedQueue`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-question">13. Queue của ThreadPoolExecutor đầy thì caller có tự block không?</a>

<details>
<summary>Click for details</summary>

Không theo contract của `execute()`.

Flow:

```text
core
→ queue
→ max workers
→ rejection
```

Backpressure phải đến từ policy/architecture cụ thể, ví dụ `CallerRunsPolicy`, bounded admission control hoặc upstream rate limiting.

### execute() và submit() có giống nhau về failure observation không?

Không.

```text
execute(Runnable)
→ void
→ uncaught failure đi theo worker uncaught-exception path

submit(Runnable/Callable)
→ Future
→ result/failure được giữ trong Future
→ get() expose failure qua ExecutionException
```

Submit rồi bỏ luôn `Future` có thể làm application mất một failure-observation channel quan trọng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="async-question">14. Async có phải Non-blocking không?</a>

<details>
<summary>Click for details</summary>

Không.

```java
CompletableFuture.supplyAsync(() -> jdbcCall());
```

có thể giải phóng caller nhưng worker vẫn block trong JDBC.

Async nói về completion/caller relationship; non-blocking nói về cách operation chờ external event/resource.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="future-question">15. Future và CompletableFuture khác nhau ở đâu?</a>

<details>
<summary>Click for details</summary>

`Future` chủ yếu là handle để:

- kiểm tra completion;
- cancel;
- blocking `get()` result.

`CompletableFuture` thêm completion pipeline/composition:

- transform;
- compose;
- combine;
- race;
- error recovery.

Hai nuance cần nhớ:

```text
Future do ExecutorService.submit(...) trả về
→ future.cancel(true)
→ có thể request interrupt running task

CompletableFuture.cancel(true)
→ mayInterruptIfRunning không điều khiển processing của CompletableFuture implementation
```

`allOf()` / `anyOf()` cũng chỉ aggregate completion. Completion có thể exceptional, và aggregate operation không mặc định là policy tự cancel các sibling future còn lại. Riêng `allOf()` không nên được hiểu như fail-fast barrier: một child fail sớm không có nghĩa aggregate lập tức complete khi các child khác vẫn chưa complete.

Và `join()` không phải non-blocking variant của `get()`: nó vẫn có thể chờ completion; khác biệt nổi bật nằm ở exception API (`CompletionException` unchecked so với `ExecutionException` checked của `get()`).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-local-question">16. ThreadLocal có làm code thread-safe không?</a>

<details>
<summary>Click for details</summary>

ThreadLocal giúp tránh sharing một context value giữa Thread.

Nó không làm object shared khác tự thread-safe và không phải replacement cho lock/Atomic trên shared state.

Đặc biệt với pool, luôn nghĩ tới worker reuse và cleanup.

Bean scope cũng không phải Thread scope. `@RequestScope` không có nghĩa "mỗi worker Thread một bean", còn session-scoped mutable state vẫn có thể bị nhiều request cùng session truy cập concurrent.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-async-question">17. @Async có tự tạo Thread mới mỗi lần không?</a>

<details>
<summary>Click for details</summary>

Không nên suy luận như vậy.

`@Async` dispatch invocation qua Spring async infrastructure và executor được resolve cho method/application.

Application phải bật async method execution infrastructure, ví dụ bằng `@EnableAsync` trong configuration tương ứng. Với `void @Async`, caller không có Future để nhận failure; cần `AsyncUncaughtExceptionHandler`/logging/metrics hoặc channel riêng nếu failure quan trọng.

Nếu dùng `ThreadPoolTaskExecutor`, worker được pool quản lý/reuse.

Nếu execution strategy dùng virtual-thread-per-task, behavior lại khác.

Hãy xem executor thực tế thay vì coi annotation là execution mechanism duy nhất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="leak-question">18. Khi nào một Thread/Pool được coi là leak?</a>

<details>
<summary>Click for details</summary>

Khi lifecycle resource kéo dài hơn ownership/intention của hệ thống và không còn được quản lý đúng.

Ví dụ:

- request tạo pool nhưng quên shutdown;
- Thread infinite loop không cancellation path;
- scheduler được đăng ký lặp ngoài ý muốn;
- worker ThreadLocal giữ stale context;
- queue backlog tăng vô hạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-question">19. Virtual Thread có thay Thread Pool không?</a>

<details>
<summary>Click for details</summary>

Với task concurrency, virtual-thread-per-task giảm nhu cầu pool virtual threads để giới hạn số OS worker như platform-thread pool truyền thống.

Nhưng hệ thống vẫn cần giới hạn **resource downstream**:

- DB connections;
- API rate limit;
- memory;
- file descriptors;
- critical resource capacity.

Semaphore/rate limiter/pool của resource vẫn có vai trò.

Trong Java 21, virtual thread là daemon thread và dùng `Thread.NORM_PRIORITY`; không dùng thread priority để điều khiển scalability. `InheritableThreadLocal` vẫn có thể được inherit khi virtual thread được tạo, nhưng `Thread.ofVirtual().inheritInheritableThreadLocals(false)` cho phép tắt inheritance. Vì vậy explicit context propagation vẫn là mental model an toàn hơn cho request/security/logging context.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="schedule-thread">20. Schedule có quan hệ gì với Thread?</a>

<details>
<summary>Click for details</summary>

Scheduler quyết định **khi nào task được trigger**; executor/thread quyết định **task chạy ở đâu**.

```text
Schedule / Timer
→ trigger time
→ submit/run task
→ execution thread
```

Không nên thay scheduler bằng:

```java
while (true) {
    Thread.sleep(...);
    runJob();
}
```

vì lifecycle, drift, cancellation và exception handling khó quản lý hơn.

### fixedRate và overlap

Không nên kết luận chung rằng một periodic task chưa chạy xong thì scheduler chắc chắn tạo hàng trăm Thread chạy chồng chính task đó.

Với `ScheduledThreadPoolExecutor`, các lần thực thi liên tiếp của **cùng một periodic task** không chạy đồng thời với chính nó.

Nếu một execution của periodic task để exception thoát ra ngoài, các execution tiếp theo của cùng periodic task bị suppress theo contract của scheduled executor. Vì vậy periodic job production cần error-observation/handling policy rõ ràng, không chỉ scheduling policy.

Trong Spring, overlap thực tế còn phụ thuộc scheduler, số scheduled registration và cách task được dispatch sang executor khác. Phải phân tích configuration cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="schedule-interrupt">21. Schedule wake-up có phải interrupt không?</a>

<details>
<summary>Click for details</summary>

Không.

Scheduler chờ tới deadline bằng cơ chế scheduling/condition nội bộ rồi đưa task tới trạng thái có thể thực thi.

`interrupt()` là cancellation/interruption signal dành cho Thread/task đang chạy hoặc đang ở interruptible wait.

Hai khái niệm đều liên quan "đánh thức/chờ" nhưng semantics khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="batch-schedule">22. Batch và Schedule khác nhau thế nào?</a>

<details>
<summary>Click for details</summary>

```text
Schedule
→ WHEN: khi nào bắt đầu?

Batch
→ WHAT/HOW: workload lớn được chia và xử lý như thế nào?
```

Batch có thể dùng Thread Pool, partitioning, queue, transaction… nhưng không bắt buộc phải được trigger bằng scheduler.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="final-checklist">23. Checklist trước khi viết code concurrency</a>

<details>
<summary>Click for details</summary>

1. State nào thực sự được share?
2. State đó có mutable không?
3. Correctness cần atomicity, visibility hay ordering guarantee nào?
4. Object/reference được publish sang Thread khác bằng boundary nào? Có `this` escape quá sớm không?
5. Có thể tránh sharing bằng immutability/message passing không?
6. Nếu dùng lock, lock owner/scope/order là gì?
7. Nếu Thread phải chờ, primitive coordination nào phù hợp?
8. Executor có bounded capacity không?
9. Overload/rejection strategy là gì?
10. Task có cancellation/timeout không?
11. Ai sở hữu lifecycle và shutdown resource?
12. ThreadLocal/context có cleanup không?
13. Demo/benchmark có thực sự chứng minh điều mình kết luận không?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-conclusion">24. Kết luận module</a>

<details>
<summary>Click for details</summary>

Mục tiêu cuối cùng không phải nhớ càng nhiều class càng tốt.

Mental model cần giữ là:

```text
Correctness
→ JMM + synchronization

Progress
→ coordination + liveness design

Resource control
→ executor + queue + rejection + lifecycle

Result composition
→ Future / CompletableFuture

Context
→ explicit propagation + cleanup

Scalability
→ chọn execution model phù hợp, bao gồm Virtual Thread
```

Khi đã phân loại đúng vấn đề, việc chọn API sẽ dễ hơn nhiều.

</details>

- [Quay lại đầu trang](#back-to-top)
