<a id="back-to-top"></a>

# Thread / Pool Lifecycle & Leak

## Menu
- [1. Thread leak](#thread-leak)
- [2. Pool leak](#pool-leak)
- [3. Queue growth cũng là resource pressure](#queue-growth)
- [4. Cached pool và native thread pressure](#cached-pool-risk)
- [5. Scheduled task duplication](#scheduled-leak)
- [6. ThreadLocal và worker reuse](#thread-local-leak)
- [7. Quan sát leak trong production](#observability)
- [8. Ownership rule](#lifecycle-rule)
- [9. Các experiment của phần Leak](#leak-experiments)

Leak trong concurrency thường không phải Java object leak đơn thuần. Một resource có lifecycle dài hơn ý định của application có thể giữ theo Thread, native stack, queue, task hoặc context.

Rule của learning module:

```text
unsafe pattern
→ quan sát trong phạm vi bounded
→ cleanup ngay trong cùng experiment
```

Không tạo endpoint mà mỗi lần gọi để lại một Thread/pool sống vô hạn.

## <a id="thread-leak">1. Thread leak</a>

<details>
<summary>Click for details</summary>

Thread leak xảy ra khi Thread được tạo nhưng không có termination/lifecycle strategy phù hợp và tiếp tục sống lâu hơn owner mong muốn.

Ví dụ nguy hiểm:

```java
new Thread(() -> {
    while (true) {
        doSomething();
    }
}).start();
```

nếu không có cancellation path và owner không còn kiểm soát reference/lifecycle.

Thread sống có thể giữ:

- native thread resources;
- stack;
- object reachable từ stack/thread-local;
- classloader/context reference;
- CPU nếu loop hoạt động.

### Demo bounded

Tham khảo:

```text
LeakController#threadLeakPattern()
GET /leak/thread-pattern
```

Worker được cố tình giữ ở trạng thái chờ để quan sát:

```text
aliveBeforeCleanup = true
```

sau đó experiment release worker và join:

```text
aliveAfterCleanup = false
```

**Kết luận:** demo cho thấy pattern leak mà không thực sự để lại leaked Thread.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pool-leak">2. Pool leak</a>

<details>
<summary>Click for details</summary>

Một `ExecutorService` tạo ra các worker Thread và thường cần explicit shutdown nếu nó không được framework/container quản lý.

Pattern nguy hiểm:

```java
void handleRequest() {
    ExecutorService pool = Executors.newFixedThreadPool(10);
    pool.submit(...);
    // quên shutdown
}
```

Mỗi request có thể tạo thêm pool/worker mới.

Tham khảo:

```text
LeakController#poolLeakPattern()
GET /leak/pool-pattern
```

Demo ghi nhận executor chưa shutdown ở giữa experiment, sau đó cleanup bằng `shutdown/awaitTermination`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="queue-growth">3. Queue growth cũng là resource pressure</a>

<details>
<summary>Click for details</summary>

Thread count ổn định không có nghĩa hệ thống an toàn.

Nếu producer nhanh hơn consumer và queue không được giới hạn hợp lý:

```text
arrival rate > service rate
        ↓
backlog tăng
        ↓
queued task giữ object/context
        ↓
memory + latency tăng
```

Đây là lý do pool sizing phải xem cả queue, không chỉ worker count.

Tham khảo:

```text
LeakController#queuePressure()
GET /leak/queue-pressure
```

Demo dùng bounded queue nhỏ, cố tình làm đầy rồi quan sát rejection; không tạo unbounded backlog thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cached-pool-risk">4. Cached pool và native thread pressure</a>

<details>
<summary>Click for details</summary>

`newCachedThreadPool()` có khả năng mở rộng số platform worker lớn khi task tới nhanh và chưa có worker rảnh.

Nếu workload block lâu và submission rate cao, số native Thread có thể tăng mạnh.

Hệ quả có thể gồm:

- memory tăng;
- context switching tăng;
- scheduler overhead;
- cuối cùng có thể gặp lỗi tạo native Thread mới.

Không nên coi cached pool là "pool tự tối ưu nên luôn an toàn".

### Platform Thread: memory và context-switching cost

Platform Thread không miễn phí. Một Thread sống có thể đi kèm:

- native/JVM bookkeeping;
- stack reservation/commit tùy JVM/OS/configuration;
- scheduling state;
- references reachable từ stack hoặc ThreadLocal.

Vì vậy không nên reasoning kiểu:

```text
1 Thread khá nhẹ
→ 100.000 platform threads cũng chỉ là 100.000 lần "khá nhẹ"
→ chắc chắn ổn
```

CPU core cũng hữu hạn. Khi số **runnable** platform threads lớn hơn rất nhiều số core:

```text
nhiều runnable threads
        ↓
OS/JVM scheduler chia CPU time
        ↓
execution context được chuyển qua lại
        ↓
thêm scheduling/context-switch overhead
+ cache locality có thể xấu đi
        ↓
throughput / tail latency có thể xấu hơn
```

Context switch bản thân không phải bug và không phải lúc nào cũng đắt như nhau. Thread đang block cũng khác Thread liên tục runnable. Vì vậy không dùng một con số "N threads là quá nhiều" cho mọi machine/workload.

Khi nghi ngờ thread pressure, quan sát bằng thread count/state, CPU, scheduler/OS metrics và JFR/profiler thay vì kết luận chỉ từ cấu hình `maxPoolSize`.

Đây cũng là lý do Virtual Thread ở chapter sau tập trung giảm chi phí của mô hình thread-per-task cho workload blocking phù hợp; nó không làm CPU core hay downstream resource trở thành vô hạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduled-leak">5. Scheduled task duplication</a>

<details>
<summary>Click for details</summary>

Leak/lifecycle bug cũng có thể xuất hiện khi application vô tình tạo nhiều scheduler hoặc đăng ký cùng periodic job nhiều lần.

Triệu chứng:

```text
một job logic
→ chạy 2, 3, 4 lần mỗi chu kỳ
```

Phải kiểm soát ownership của scheduler và registration lifecycle.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-local-leak">6. ThreadLocal và worker reuse</a>

<details>
<summary>Click for details</summary>

ThreadLocal value gắn với worker có thể sống lâu nếu worker sống lâu.

Do đó:

```text
set context
→ try
→ work
→ finally remove/restore
```

là lifecycle rule, không chỉ coding style.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="observability">7. Quan sát leak trong production</a>

<details>
<summary>Click for details</summary>

Dấu hiệu nên theo dõi:

- live thread count tăng theo thời gian;
- pool count/worker count tăng không quay xuống;
- queue depth tăng liên tục;
- memory tăng cùng backlog;
- CPU tăng do busy loop/context switch;
- shutdown/deploy kéo dài;
- duplicate scheduled execution.

Các công cụ hữu ích:

- thread dump;
- JFR/JDK tooling;
- executor metrics;
- queue depth metrics;
- application logs với thread name;
- OS/container metrics.

### Workflow chẩn đoán bằng thread dump

Thread dump hữu ích nhất khi được dùng để trả lời câu hỏi cụ thể, không phải chỉ chụp một file rồi đếm số dòng.

Một workflow thực tế:

```text
1. phát hiện live thread count tăng bất thường
        ↓
2. chụp nhiều thread dump cách nhau một khoảng thời gian
        ↓
3. group theo thread name / pool prefix
        ↓
4. xem Thread.State và stack trace lặp lại
        ↓
5. xác định owner tạo Thread/pool
        ↓
6. đối chiếu queue depth, executor metrics, request rate
        ↓
7. kiểm tra lifecycle: shutdown/cancel/timeout/cleanup
```

Một dump đơn lẻ chỉ là snapshot. Nhiều snapshot giúp phân biệt:

```text
Thread tạm thời đang bận
vs
Thread bị giữ sống lâu bất thường
```

### Đọc Thread.State đúng cách

Một số pattern thường gặp:

| State/pattern | Câu hỏi nên đặt ra |
| --- | --- |
| Nhiều `RUNNABLE` cùng stack | Có busy loop, CPU hot path hay contention ngoài JVM không? |
| Nhiều `BLOCKED` cùng monitor | Ai đang giữ monitor? Critical section có quá lớn không? |
| Nhiều `WAITING/TIMED_WAITING` trong executor | Đây là worker idle bình thường hay pool không còn owner/lifecycle? |
| Thread name prefix tăng liên tục | Có đang tạo pool mới theo request/job không? |
| Stack giữ cùng application object/context | ThreadLocal/task/reference có lifecycle dài hơn intended không? |

`WAITING` không tự động có nghĩa leak. Một worker của pool sống lâu và chờ queue có thể hoàn toàn bình thường nếu pool đó là resource application-owned và có lifecycle rõ ràng.

Ngược lại, Thread count tăng đều dù request rate quay về bình thường là tín hiệu mạnh hơn nhiều.

### Kết hợp thread dump với executor metrics

Nếu có executor metrics, nên đọc cùng nhau:

```text
poolSize
activeCount
queueSize
completedTaskCount
rejectionCount
```

Ví dụ:

```text
poolSize ổn định
activeCount luôn max
queueSize tăng liên tục
```

thường gợi ý saturation/backlog hơn là thread leak.

Trong khi:

```text
request rate ổn định
nhưng số pool/thread name prefix mới cứ tăng
```

gợi ý ownership/lifecycle bug ở việc tạo executor.

### Tooling thực tế

Tùy môi trường có thể dùng:

- `jcmd <pid> Thread.print`;
- `jstack <pid>`;
- Java Flight Recorder / JDK Mission Control;
- Spring Boot Actuator metrics nếu executor được instrument;
- container/OS process metrics.

Mục tiêu cuối cùng không phải tìm "Thread nào trông lạ", mà phải nối được chuỗi:

```text
symptom
→ resource
→ owner
→ missing/incorrect lifecycle rule
→ fix
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-rule">8. Ownership rule</a>

<details>
<summary>Click for details</summary>

Mỗi concurrency resource cần trả lời được:

```text
Ai tạo nó?
Ai dừng nó?
Khi nào dừng?
Nếu shutdown timeout thì làm gì?
Task có cooperate với interruption không?
```

Nếu không trả lời được owner/lifecycle, resource leak rất dễ xuất hiện.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="leak-experiments">9. Các experiment của phần Leak</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#thread-leak` | `LeakController#threadLeakPattern()` | `GET /leak/thread-pattern` |
| `#pool-leak` | `LeakController#poolLeakPattern()` | `GET /leak/pool-pattern` |
| `#queue-growth` | `LeakController#queuePressure()` | `GET /leak/queue-pressure` |

</details>

- [Quay lại đầu trang](#back-to-top)
