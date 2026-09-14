<a id="back-to-top"></a>

# Virtual Thread trong Java 21

## Menu
- [1. Platform Thread và Virtual Thread](#virtual-thread-basic)
- [2. Tạo Virtual Thread trực tiếp](#virtual-thread-creation)
- [3. Blocking không biến thành Non-blocking](#mount-unmount)
- [4. Virtual thread per task executor](#virtual-executor)
- [5. Throughput không phải Latency](#throughput-latency)
- [6. Virtual Thread không làm downstream vô hạn](#resource-limits)
- [7. ThreadLocal với Virtual Thread](#virtual-thread-thread-local)
- [8. Pinning trong Java 21](#pinning-java21)
- [9. Locking với Virtual Thread](#virtual-thread-locking)
- [10. Spring Boot và Virtual Thread](#spring-virtual-thread)
- [11. ExecutorService và try-with-resources trong Java 21](#executor-autoclose)
- [12. Khi nào cân nhắc Virtual Thread?](#virtual-thread-choice)
- [13. Các experiment của phần Virtual Thread](#virtual-thread-experiments)

Virtual Thread là lightweight `Thread` implementation được JVM quản lý để giúp mô hình thread-per-task scale tốt hơn cho workload có nhiều thời gian chờ/blocking phù hợp.

Nó không thay đổi các quy tắc correctness đã học:

```text
race condition vẫn tồn tại
volatile/JMM vẫn tồn tại
lock vẫn cần khi shared mutable state cần bảo vệ
deadlock vẫn có thể xảy ra
```

## <a id="virtual-thread-basic">1. Platform Thread và Virtual Thread</a>

<details>
<summary>Click for details</summary>

Platform Thread gắn chặt hơn với OS thread trong lifecycle thực thi.

Virtual Thread là Java `Thread` được scheduler của JVM mount lên carrier/platform thread khi cần chạy.

Mental model:

```text
Virtual Thread
      ↓ mounted
Carrier / Platform Thread
      ↓
CPU thực thi code
```

Khi virtual thread gặp một supported blocking operation, runtime có thể unmount virtual thread để carrier thực thi virtual thread khác.

Điều này giúp số lượng task đang chờ lớn hơn mà không cần một OS thread riêng cho mỗi task.

### Daemon và priority trong Java 21

Virtual thread vẫn là `Thread`, nhưng không nên mang toàn bộ assumption của platform thread sang nó.

Trong Java 21:

```text
virtual thread
→ luôn là daemon thread
→ priority cố định ở Thread.NORM_PRIORITY
```

Vì virtual thread là daemon, chỉ còn virtual threads sống **không đủ để giữ JVM tiếp tục chạy**. Đây là liên hệ trực tiếp với daemon lifecycle đã học ở phần Basic.

Priority cũng không phải tuning knob để điều khiển scheduling của virtual thread. Nếu cần giới hạn concurrency/resource, dùng abstraction phù hợp như semaphore, executor/resource pool hoặc rate limiter thay vì cố tăng/giảm thread priority.

Tham khảo:

```text
VirtualThreadController#basic()
GET /virtual-thread/basic
```

Kết quả kỳ vọng:

```text
isVirtual = true
daemon = true
priority = 5
normalPriority = 5
stateAfterJoin = TERMINATED
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-creation">2. Tạo Virtual Thread trực tiếp</a>

<details>
<summary>Click for details</summary>

Virtual thread không bắt buộc phải đi qua `ExecutorService`. Java 21 có hai API trực tiếp đáng biết:

```java
Thread.startVirtualThread(task);

Thread.ofVirtual()
        .name("worker")
        .start(task);
```

Builder hữu ích khi cần cấu hình name/uncaught-exception handler trước khi start.

Tham khảo:

```text
VirtualThreadController#creationApi()
GET /virtual-thread/creation-api
```

Experiment tạo một Thread bằng mỗi API và xác nhận:

```text
startVirtualThreadIsVirtual = true
builderIsVirtual = true
bothTerminated = true
```

**Kết luận:** `newVirtualThreadPerTaskExecutor()` là abstraction thuận tiện cho task submission/lifecycle; bản thân virtual thread vẫn là một `Thread` và có thể được tạo trực tiếp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mount-unmount">3. Blocking không biến thành Non-blocking</a>

<details>
<summary>Click for details</summary>

Một virtual thread gọi blocking API vẫn **semantically blocked**.

Khác biệt là JVM có thể giải phóng carrier trong lúc virtual thread chờ nếu operation hỗ trợ unmount.

Vì vậy không nên nói:

```text
Virtual Thread = non-blocking programming
```

Đúng hơn:

```text
blocking style code
        +
cheap virtual-thread suspension
        ↓
high concurrency với programming model tuần tự quen thuộc
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-executor">4. Virtual thread per task executor</a>

<details>
<summary>Click for details</summary>

Java 21 cung cấp:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(task);
}
```

Mỗi task được thực thi trong một virtual thread mới.

Không cần pool virtual thread theo cách pool platform thread truyền thống, vì virtual thread được thiết kế để tạo nhiều và không reuse như worker pool để tiết kiệm OS thread.

Tham khảo:

```text
VirtualThreadController#blockingScale()
GET /virtual-thread/blocking-scale
```

Demo so sánh một lượng nhỏ sleep-based blocking task:

- fixed platform pool có 10 workers;
- virtual-thread-per-task executor.

Timing chỉ mang tính minh họa, **không phải benchmark chuẩn**. `sleep` được dùng để làm rõ concurrency shape, không để kết luận performance production.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="throughput-latency">5. Throughput không phải Latency</a>

<details>
<summary>Click for details</summary>

Virtual threads thường giúp **throughput/scalability** khi hệ thống cần giữ rất nhiều concurrent blocking task.

Chúng không tự làm một database query 500 ms trở thành 50 ms.

```text
latency một operation
→ phụ thuộc operation/downstream

concurrent capacity
→ virtual thread có thể giúp tăng mạnh trong workload phù hợp
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resource-limits">6. Virtual Thread không làm downstream vô hạn</a>

<details>
<summary>Click for details</summary>

Có thể tạo rất nhiều virtual threads, nhưng database connection pool, API downstream, file descriptor và rate limit vẫn hữu hạn.

Do đó thường vẫn cần concurrency limiter như `Semaphore`.

Tham khảo:

```text
VirtualThreadController#limitedResource()
GET /virtual-thread/limited-resource
```

Demo tạo nhiều virtual task nhưng chỉ cho tối đa 5 task vào critical external-resource section cùng lúc.

Implementation đặc biệt giữ rule:

```text
chỉ release permit nếu acquire thực sự thành công
```

tránh bug làm tăng permit khi Thread bị interrupt trước lúc acquire.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-thread-local">7. ThreadLocal với Virtual Thread</a>

<details>
<summary>Click for details</summary>

Virtual thread vẫn có `ThreadLocal` semantics theo Thread. Điều khác biệt về scalability là application có thể tạo số lượng Thread lớn hơn rất nhiều, nên việc đặt object lớn vào ThreadLocal cho từng virtual thread có thể trở thành heap pressure đáng kể.

Rule vẫn giống phần Context:

```text
set
→ try
→ work
→ finally remove/restore
```

Không nên dùng ThreadLocal như global cache chỉ vì virtual thread rẻ.

Tham khảo:

```text
VirtualThreadController#threadLocal()
GET /virtual-thread/thread-local
```

Experiment dùng plain `ThreadLocal` trên hai virtual threads:

```text
firstThreadContext = request-A
firstThreadCleaned = true
secondThreadInheritedPlainThreadLocal = null
plainThreadLocalIsPerThread = true
```

**Kết luận:** virtual thread không thay đổi rule isolation/cleanup của ThreadLocal; scale lớn khiến memory discipline càng quan trọng.

### InheritableThreadLocal với Virtual Thread

`InheritableThreadLocal` vẫn có creation-time inheritance semantics. Virtual thread tạo mới có thể nhận inherited value từ Thread tạo nó.

Nhưng Java 21 virtual-thread builder cho phép tắt behavior này:

```java
Thread.ofVirtual()
        .inheritInheritableThreadLocals(false)
        .start(task);
```

Experiment `/thread-local` quan sát thêm:

```text
inheritableThreadLocalDefault = parent-context
inheritableThreadLocalWhenDisabled = null
virtualBuilderCanDisableInheritance = true
```

Điểm quan trọng vẫn là: inheritance không thay thế explicit context propagation. Context có lifecycle/request semantics riêng vẫn nên capture/restore/cleanup có chủ đích thay vì phụ thuộc ngầm vào việc Thread mới được tạo từ Thread nào.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pinning-java21">8. Pinning trong Java 21</a>

<details>
<summary>Click for details</summary>

Phần này phải gắn với version vì implementation virtual thread thay đổi qua các JDK.

Trong **Java 21**, virtual thread có thể bị pinned vào carrier trong một số trường hợp, đáng chú ý khi blocking trong lúc giữ monitor `synchronized`, hoặc qua một số native/foreign operation.

Pinning ngắn không đồng nghĩa correctness bug, nhưng blocking lâu/thường xuyên trong trạng thái pinned có thể làm giảm scalability vì carrier không được giải phóng.

Không nên biến rule này thành chân lý vĩnh viễn cho mọi JDK mới hơn; luôn kiểm tra behavior/version đang deploy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-locking">9. Locking với Virtual Thread</a>

<details>
<summary>Click for details</summary>

Virtual thread không loại bỏ nhu cầu synchronization.

Nếu nhiều virtual threads update cùng shared mutable state:

```text
100000 virtual threads
        +
unsafe count++
        ↓
vẫn có race condition
```

`ReentrantLock`, concurrent collections, Atomic classes và các design tránh sharing vẫn có vai trò như với platform thread.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-virtual-thread">10. Spring Boot và Virtual Thread</a>

<details>
<summary>Click for details</summary>

Spring Boot hỗ trợ virtual-thread-oriented task execution khi bật property phù hợp, ví dụ trong Boot version hỗ trợ:

```properties
spring.threads.virtual.enabled=true
```

Nhưng không nên diễn giải thành:

> bật property này thì mọi Thread trong application đều trở thành virtual thread.

Custom executor, library riêng, scheduler riêng hoặc code tự tạo platform Thread vẫn có lifecycle/execution strategy của chúng.

Khi cần chắc chắn, quan sát executor/thread thực tế thay vì suy ra từ property một cách tuyệt đối.

Do virtual threads là daemon threads, application chỉ còn daemon threads có thể làm JVM exit dù vẫn còn background work dự kiến. Với Spring Boot, khi application cần được giữ sống độc lập với non-daemon worker, có thể dùng `spring.main.keep-alive=true` để Boot giữ JVM sống. Nuance này đặc biệt đáng chú ý với scheduler/background component khi virtual threads được bật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-autoclose">11. ExecutorService và try-with-resources trong Java 21</a>

<details>
<summary>Click for details</summary>

Trong Java hiện đại, `ExecutorService` hỗ trợ lifecycle phù hợp với try-with-resources.

Ví dụ:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(...);
}
```

Scope lexical giúp ownership của executor rõ hơn: code tạo executor cũng chịu trách nhiệm kết thúc scope của nó.

`ExecutorService` hỗ trợ `AutoCloseable` từ Java 19, nên cú pháp này dùng được với Java 21 nhưng không dùng được với kiểu `ExecutorService` của Java 17.

Quan trọng hơn cú pháp là semantics của `close()`:

```text
kết thúc thân try
→ close(): bắt đầu orderly shutdown
→ chờ task đã nhận hoàn thành và executor terminate
→ mới đi tiếp sau khối try
```

Khác `shutdown()`, `close()` có chờ termination. Nó không có timeout mặc định để bảo đảm thoát nhanh nếu task bị kẹt.
Khi Thread đang chờ trong `close()` bị interrupt, implementation mặc định cố dừng task như `shutdownNow()`, vẫn chờ termination và khôi phục interrupt status trước khi trả về. Task vẫn phải hợp tác với cancellation.

Liên hệ experiment `VirtualThreadController#blockingScale()`: thời gian được đo **sau khối try**, nên bao gồm thời gian hoàn thành các task, không chỉ thời gian submit. Hai executor đều được đóng trước khi response trả về. Nếu chuyển phép đo vào cuối thân `try` thì ý nghĩa phép đo sẽ khác.

Tham khảo: [ExecutorService.close() — Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html#close()).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-choice">12. Khi nào cân nhắc Virtual Thread?</a>

<details>
<summary>Click for details</summary>

Phù hợp để thử khi:

- rất nhiều concurrent request/task;
- task chủ yếu chờ I/O;
- muốn giữ imperative/thread-per-task style;
- downstream capacity đã được giới hạn hợp lý.

Không mặc định là lựa chọn tốt nhất khi:

- workload CPU-bound thuần túy;
- concurrency thực sự thấp;
- bottleneck nằm hoàn toàn ở downstream có capacity nhỏ;
- code giữ monitor/pinned blocking lâu trên Java version đang dùng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="virtual-thread-experiments">13. Các experiment của phần Virtual Thread</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#virtual-thread-basic` | `VirtualThreadController#basic()` | `GET /virtual-thread/basic` |
| `#virtual-thread-creation` | `VirtualThreadController#creationApi()` | `GET /virtual-thread/creation-api` |
| `#virtual-executor` | `VirtualThreadController#blockingScale()` | `GET /virtual-thread/blocking-scale` |
| `#resource-limits` | `VirtualThreadController#limitedResource()` | `GET /virtual-thread/limited-resource` |
| `#virtual-thread-thread-local` | `VirtualThreadController#threadLocal()` | `GET /virtual-thread/thread-local` |

</details>

- [Quay lại đầu trang](#back-to-top)
