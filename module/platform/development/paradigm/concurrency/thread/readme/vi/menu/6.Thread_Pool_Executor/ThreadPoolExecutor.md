<a id="back-to-top"></a>

# Thread Pool & Executor trong Java

## Menu
- [1. Executor và ExecutorService](#executor-abstraction)
- [2. Quy trình xử lý của ThreadPoolExecutor](#thread-pool-flow)
- [3. corePoolSize, maximumPoolSize, queue và keepAlive](#pool-properties)
- [4. Pool sizing: heuristic không phải công thức tuyệt đối](#pool-sizing)
- [5. Các factory trong Executors](#executors-factories)
- [6. Delayed và periodic scheduling](#scheduled-execution)
- [7. RejectedExecutionHandler và overload](#rejection-policy)
- [8. Custom RejectedExecutionHandler: overload là observable event](#custom-rejection-handler)
- [9. Lifecycle và graceful shutdown](#graceful-shutdown)
- [10. ThreadFactory và observability](#thread-factory-observability)
- [11. Các experiment của phần Thread Pool](#thread-pool-experiments)

Sau khi hiểu Thread trực tiếp, coordination và synchronization, phần này chuyển sang abstraction quản lý **nhiều task trên một tập worker Thread**.

```text
Task
→ Executor
→ worker threads + queue + lifecycle + rejection policy
```

## <a id="executor-abstraction">1. Executor và ExecutorService</a>

<details>
<summary>Click for details</summary>

`Executor` tách việc **submit task** khỏi cách task được thực thi.

```java
executor.execute(task);
```

Caller không cần tự `new Thread(...)` cho từng task.

`ExecutorService` bổ sung lifecycle và result-oriented APIs như:

- `submit()`;
- `shutdown()`;
- `shutdownNow()`;
- `awaitTermination()`.

Điểm quan trọng:

```text
Runnable / Callable = công việc
Executor            = chiến lược thực thi công việc
ThreadPoolExecutor  = một implementation quản lý worker pool
```

### <a id="execute-vs-submit">execute() và submit() khác nhau ở đâu?</a>

Hai API đều có thể đưa `Runnable` vào executor, nhưng completion/result channel khác nhau:

| API | Return | Result/exception observation |
| --- | --- | --- |
| `execute(Runnable)` | `void` | không có `Future`; exception thoát khỏi task đi theo uncaught-exception path của worker |
| `submit(Runnable/Callable)` | `Future<?>` / `Future<T>` | result/exception được giữ trong `Future`; caller quan sát khi `get()` |

Đây là footgun thực tế:

```java
executor.submit(() -> {
    throw new IllegalStateException("boom");
});
```

nếu code bỏ luôn `Future`, exception có thể không xuất hiện theo cách developer kỳ vọng từ `execute()`.

Tham khảo:

```text
ThreadPoolController#executeVsSubmit()
GET /thread-pool/execute-vs-submit
```

Experiment dùng `UncaughtExceptionHandler` riêng để quan sát `execute()` và dùng `Future.get()` để quan sát `submit()`:

```text
execute(task throws)
→ uncaught exception path

submit(task throws)
→ Future complete exceptionally
→ get() ném ExecutionException với cause gốc
```

**Kết luận:** nếu chọn `submit()` cho task quan trọng, phải có strategy quan sát completion/failure thay vì submit rồi bỏ quên handle.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-flow">2. Quy trình xử lý của ThreadPoolExecutor</a>

<details>
<summary>Click for details</summary>

Với `ThreadPoolExecutor`, khi gọi `execute(task)`, mental model cốt lõi là:

```text
worker count < corePoolSize ?
        ├─ yes → tạo core worker chạy task
        └─ no
            ↓
        thử enqueue task
            ├─ thành công → task chờ trong queue
            └─ queue full
                ↓
        worker count < maximumPoolSize ?
            ├─ yes → tạo worker bổ sung
            └─ no  → rejection policy
```

Điều này khác với câu:

> queue đầy thì `execute()` tự block caller cho đến khi có chỗ.

`ThreadPoolExecutor.execute()` không có contract đó. Nếu muốn backpressure ở caller, cần chọn policy/architecture phù hợp.

### Demo flow

Tham khảo:

```text
ThreadPoolController#executorFlow()
GET /thread-pool/executor-flow
```

Pool demo dùng cấu hình nhỏ:

```text
core = 1
max = 2
queue = 1
AbortPolicy
```

Các task được giữ lại bằng latch để quan sát lần lượt:

```text
task 1 → core worker
task 2 → queue
task 3 → worker thứ 2 vì queue đầy
task 4 → rejected
```

**Kết luận:** `maximumPoolSize` chỉ có cơ hội phát huy khi queue không còn nhận task mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pool-properties">3. corePoolSize, maximumPoolSize, queue và keepAlive</a>

<details>
<summary>Click for details</summary>

### corePoolSize

Số worker mà pool cố duy trì theo core policy. Mặc định core worker thường được tạo khi có task, trừ khi chủ động prestart.

### maximumPoolSize

Giới hạn worker tối đa khi pool cần mở rộng sau khi queue không nhận thêm task.

### workQueue

Queue quyết định cách backlog được giữ.

Một queue rất lớn có thể làm `maximumPoolSize` gần như không được dùng vì task cứ tiếp tục được enqueue.

Một bounded queue giúp giới hạn backlog nhưng buộc hệ thống phải có chiến lược khi capacity cạn.

### keepAliveTime

Thời gian worker vượt core được phép idle trước khi bị loại bỏ. Nếu bật `allowCoreThreadTimeOut`, rule có thể áp dụng cho core worker nữa.

Mặc định core worker được giữ lại kể cả khi idle. Khi gọi:

```java
executor.allowCoreThreadTimeOut(true);
```

core worker cũng có thể bị thu hồi sau `keepAliveTime`. Trade-off là giảm resource khi workload thưa nhưng request sau có thể phải trả chi phí tạo worker lại. Nếu workload lên/xuống liên tục, timeout quá ngắn có thể gây thread churn thay vì tiết kiệm hữu ích.

### prestartAllCoreThreads

Tạo sẵn core worker trước khi task tới. Hữu ích khi warm-up latency quan trọng, nhưng đổi lại tạo resource sớm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pool-sizing">4. Pool sizing: heuristic không phải công thức tuyệt đối</a>

<details>
<summary>Click for details</summary>

Các công thức kiểu:

```text
CPU-bound → khoảng Ncpu
I/O-bound → lớn hơn Ncpu
```

chỉ là điểm khởi đầu.

Sizing production nên dựa vào:

- arrival rate;
- service time;
- latency SLO;
- downstream capacity;
- memory cho queued task;
- contention;
- behavior khi overload.

Không có `queueCapacity = 500` hay `maxPoolSize = Ncpu * 2` đúng cho mọi hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executors-factories">5. Các factory trong Executors</a>

<details>
<summary>Click for details</summary>

### newFixedThreadPool(n)

Số worker cố định. Factory này dùng unbounded `LinkedBlockingQueue`, vì vậy backlog có thể tăng lớn nếu producer nhanh hơn consumer lâu dài.

### newCachedThreadPool()

Pool có thể tăng worker mạnh và dùng `SynchronousQueue`. Phù hợp cho task ngắn trong một số workload nhưng cần hiểu risk tạo nhiều platform threads.

### newSingleThreadExecutor()

Một worker, giúp serialize task theo queue của executor.

Factory này cũng dùng queue không bounded theo một application capacity nhỏ do caller cấu hình. Nếu producer submit nhanh hơn single worker xử lý trong thời gian dài, backlog/memory/latency vẫn có thể tăng mạnh. "Một worker" không đồng nghĩa "tự có backpressure".

### newScheduledThreadPool(n)

Hỗ trợ delayed/periodic execution.

Không chọn factory chỉ vì tên tiện; trong production thường cần hiểu rõ queue, worker limit và rejection behavior thật sự.

Tham khảo:

```text
ThreadPoolController#fixedPoolReuse()
GET /thread-pool/fixed-pool-reuse

ThreadPoolController#executorFactories()
GET /thread-pool/executor-factories
```

`fixed-pool-reuse` gửi nhiều task nhưng chỉ quan sát tối đa hai worker name, cho thấy worker được reuse thay vì tạo Thread cho từng task.

`executor-factories` chạy bốn experiment bounded để quan sát semantics đại diện:

```text
newFixedThreadPool(2)
→ hai worker giữ task đang chạy
→ task dư chờ queue

newCachedThreadPool()
→ khi chưa có idle worker, nhiều task đồng thời có thể làm pool tăng worker

newSingleThreadExecutor()
→ task được serialize theo submission order của queue

newScheduledThreadPool(1)
→ task được chạy sau delay mà không cần tự viết sleep-loop
```

Các executor đều được shutdown/awaitTermination trong chính experiment để request không để lại worker sống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-execution">6. Delayed và periodic scheduling</a>

<details>
<summary>Click for details</summary>

`ScheduledExecutorService` hỗ trợ ba semantics khác nhau cần phân biệt:

```text
schedule()
→ chạy một lần sau delay

scheduleAtFixedRate()
→ cố duy trì cadence theo mốc start dự kiến

scheduleWithFixedDelay()
→ chờ một delay sau khi lần chạy trước hoàn thành rồi mới lên lịch lần tiếp theo
```

`scheduleAtFixedRate()` không có contract cho phép cùng **một periodic task** tự overlap với chính nó. Nếu một lần chạy kéo dài hơn period, lần kế tiếp có thể bị trễ; scheduler không tự clone cùng task để chạy song song chỉ vì missed cadence.

Tham khảo:

```text
ThreadPoolController#scheduledExecution()
GET /thread-pool/scheduled-execution
```

Experiment chỉ chạy vài iteration rồi cancel cả hai periodic task và shutdown executor. Mục tiêu là học scheduling semantics mà không để background task sống sau request.

Method chạy mỗi mode riêng với `configuredIntervalMillis = 30`, task mô phỏng làm việc ít nhất khoảng `60 ms`, và ghi lại ba lần thực thi đầu tiên.
Trong `fixedRate.timeline` và `fixedDelay.timeline`, quan sát:

- `startMillis`, `endMillis`: thời điểm thực tế tính từ lúc chuẩn bị schedule.
- `workMillis`: thời gian thực thi đo được.
- `gapAfterPreviousEndMillis`: khoảng nghỉ từ lúc lần trước kết thúc đến lúc lần này bắt đầu; lần đầu là `null`.

Khi task lâu hơn period, fixed-rate thường chạy lần tiếp theo gần ngay sau khi lần trước xong để xử lý lịch đã trễ. Fixed-delay vẫn đợi khoảng delay đã cấu hình sau completion.
Scheduler/OS có thể gây trễ thêm, nên không assert một con số timing chính xác hoặc kết luận fixed-rate luôn nhanh hơn trong mọi lần chạy.
`noOverlapObserved` được tính từ các mốc thời gian. Vì demo dùng một worker, trường này chỉ xác nhận các lượt quan sát không overlap; guarantee không overlap của cùng periodic task đến từ Java API, không phải benchmark này.

### <a id="scheduled-failure">Exception trong periodic task</a>

Một semantics production rất quan trọng của `ScheduledExecutorService`: nếu một execution của periodic task ném exception ra ngoài, các execution tiếp theo của **cùng periodic task** bị suppress.

Điều này khác với suy nghĩ:

```text
run #1 lỗi
→ scheduler tự log rồi vẫn chạy #2, #3, #4...
```

Không nên dựa vào assumption đó.

Tham khảo:

```text
ThreadPoolController#scheduledFailure()
GET /thread-pool/scheduled-failure
```

Experiment tạo `scheduleAtFixedRate()` mà lần đầu luôn throw:

```text
run #1
→ IllegalStateException
→ ScheduledFuture complete exceptionally
→ runsObserved = 1
→ laterExecutionsSuppressed = true
```

`ScheduledFuture.get()` cho phép caller quan sát failure qua `ExecutionException`, nhưng production periodic job thường không có caller ngồi `get()`. Vì vậy task định kỳ cần logging/metrics/alerting và exception boundary phù hợp; nếu business muốn job tiếp tục sau một failure đã xử lý, task phải catch/handle failure theo policy rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rejection-policy">7. RejectedExecutionHandler và overload</a>

<details>
<summary>Click for details</summary>

Task bị reject khi executor vẫn RUNNING nhưng không còn worker/queue capacity phù hợp, hoặc khi executor đã shutdown.

Các policy mặc định:

| Policy | Hành vi |
| --- | --- |
| `AbortPolicy` | ném `RejectedExecutionException` |
| `CallerRunsPolicy` | caller tự chạy task nếu executor chưa shutdown |
| `DiscardPolicy` | bỏ task im lặng |
| `DiscardOldestPolicy` | loại bỏ task ở **head của work queue** rồi thử submit task mới lại |

### CallerRunsPolicy như một dạng feedback/backpressure

Tham khảo:

```text
ThreadPoolController#callerRuns()
GET /thread-pool/caller-runs
```

Pool được làm đầy có chủ đích. Task tiếp theo chạy ngay trên caller thread.

Điểm cần quan sát:

```text
fallbackThread = HTTP/caller thread
```

Caller bị bận tự xử lý task, vì vậy tốc độ submit giảm. Đây là feedback pressure, không phải queue tự block `execute()`.

`DiscardPolicy` và `DiscardOldestPolicy` chỉ nên dùng khi semantics mất task được hiểu và chấp nhận rõ ràng.

### DiscardPolicy và DiscardOldestPolicy: mất task là business semantics

`DiscardPolicy` bỏ task mới mà không ném exception. `DiscardOldestPolicy` gọi theo semantics của **head work queue** rồi thử submit task mới lại.

Với FIFO queue như `LinkedBlockingQueue` trong experiment này, head cũng chính là task đã nằm trong queue lâu nhất. Nhưng không nên biến điều đó thành contract tổng quát: nếu work queue có ordering khác, ví dụ priority ordering, head phụ thuộc semantics của queue chứ không nhất thiết là task "cũ nhất theo thời gian".

Điều này có nghĩa chúng không chỉ là "cách xử lý kỹ thuật khi pool đầy". Chúng thay đổi **task nào được phép biến mất**.

Tham khảo:

```text
ThreadPoolController#discardPolicies()
GET /thread-pool/discard-policies
```

Experiment làm đầy pool/queue bằng latch rồi quan sát:

```text
DiscardPolicy
→ task mới không chạy và không có exception

DiscardOldestPolicy
→ head của LinkedBlockingQueue trong demo không chạy
→ task mới thay thế và được thực thi
```

**Kết luận:** chỉ chọn discard policy khi application có rule rõ ràng về việc bỏ dữ liệu/công việc; nếu mất task là không chấp nhận được, policy này không phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="custom-rejection-handler">8. Custom RejectedExecutionHandler: overload là observable event</a>

<details>
<summary>Click for details</summary>

Bốn policy mặc định không phải lựa chọn duy nhất. `RejectedExecutionHandler` là extension point để application tự quyết định semantics khi admission thất bại.

Ví dụ production thường muốn:

- tăng metric `executor_rejected_total`;
- log pool/queue saturation;
- phát alert;
- reject rõ ràng để upstream retry/rate-limit;
- fallback sang durable queue **chỉ khi business contract cho phép và đã thiết kế idempotency/delivery semantics**.

Không nên tự động "đẩy sang Kafka" trong handler chỉ vì task bị reject; hành vi đó thay đổi delivery contract của hệ thống.

Tham khảo:

```text
ThreadPoolController#customRejectionHandler()
GET /thread-pool/custom-rejection-handler
```

Experiment làm đầy worker + queue rồi custom handler:

```text
rejectionCount = 1
exceptionObserved = true
customHandlerCanRecordOverload = true
```

**Kết luận:** rejection nên được coi là tín hiệu overload có thể quan sát và xử lý theo business semantics, không chỉ là exception kỹ thuật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="graceful-shutdown">9. Lifecycle và graceful shutdown</a>

<details>
<summary>Click for details</summary>

Executor do code tạo ra phải có owner chịu trách nhiệm shutdown.

Pattern Java Core:

```java
executor.shutdown();

if (!executor.awaitTermination(timeout, unit)) {
    executor.shutdownNow();
}
```

`shutdown()`:

- không nhận task mới;
- cho queued/running task tiếp tục.

`shutdownNow()`:

- cố interrupt running task;
- trả về task chưa bắt đầu;
- không thể cưỡng bức code không hợp tác với interruption phải dừng ngay.

Tham khảo:

```text
ThreadPoolController#gracefulShutdown()
GET /thread-pool/graceful-shutdown
```

Demo tạo executor cục bộ, hoàn thành task, gọi shutdown và xác nhận pool terminated trước khi request kết thúc.

### Experiment forced shutdown

Tham khảo:

```text
ThreadPoolController#forcedShutdown()
GET /thread-pool/forced-shutdown
```

Một worker giữ task đang chạy ở latch; task thứ hai nằm trong queue. Sau `shutdown()`, worker chưa được release nên `awaitTermination(50 ms)` trả `false` một cách có kiểm soát.
Sau đó `shutdownNow()` interrupt running task và trả lại task chưa bắt đầu:

```text
terminatedBeforeForce = false
interruptedWorkers = 1
queuedTaskReturned = true
neverStartedCount = 1
queuedTaskRuns = 0
terminatedAfterForce = true
```

Task đang chạy dừng được vì `await()` hỗ trợ interruption và worker kết thúc sau catch. Điều này không chứng minh `shutdownNow()` có thể kill một task không hợp tác. Service dùng executor cục bộ và cleanup cả khi caller bị interrupt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-factory-observability">10. ThreadFactory và observability</a>

<details>
<summary>Click for details</summary>

`ThreadFactory` cho phép kiểm soát cách worker Thread được tạo, đặc biệt hữu ích cho:

- thread name prefix;
- daemon policy khi thật sự cần;
- uncaught exception handling;
- observability/thread dump.

Tên như:

```text
payment-worker-1
payment-worker-2
```

hữu ích hơn nhiều so với `pool-7-thread-3` khi debug production.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-experiments">11. Các experiment của phần Thread Pool</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#execute-vs-submit` | `ThreadPoolController#executeVsSubmit()` | `GET /thread-pool/execute-vs-submit` |
| `#thread-pool-flow` | `ThreadPoolController#executorFlow()` | `GET /thread-pool/executor-flow` |
| `#executors-factories` | `ThreadPoolController#fixedPoolReuse()` | `GET /thread-pool/fixed-pool-reuse` |
| `#executors-factories` | `ThreadPoolController#executorFactories()` | `GET /thread-pool/executor-factories` |
| `#scheduled-execution` | `ThreadPoolController#scheduledExecution()` | `GET /thread-pool/scheduled-execution` |
| `#scheduled-failure` | `ThreadPoolController#scheduledFailure()` | `GET /thread-pool/scheduled-failure` |
| `#rejection-policy` | `ThreadPoolController#callerRuns()` | `GET /thread-pool/caller-runs` |
| `#rejection-policy` | `ThreadPoolController#discardPolicies()` | `GET /thread-pool/discard-policies` |
| `#custom-rejection-handler` | `ThreadPoolController#customRejectionHandler()` | `GET /thread-pool/custom-rejection-handler` |
| `#graceful-shutdown` | `ThreadPoolController#gracefulShutdown()` | `GET /thread-pool/graceful-shutdown` |
| `#graceful-shutdown` | `ThreadPoolController#forcedShutdown()` | `GET /thread-pool/forced-shutdown` |

</details>

- [Quay lại đầu trang](#back-to-top)
