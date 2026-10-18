<a id="back-to-top"></a>

# Interruption & Cooperative Cancellation trong Java

## Menu
- [1. Interrupt là tín hiệu, không phải lệnh kill](#interrupt-mental-model)
- [2. interrupt(), isInterrupted() và Thread.interrupted()](#interrupt-flag)
- [3. InterruptedException và blocking operation](#interrupted-exception)
- [4. Cleanup khi task bị cancel](#interrupt-cleanup)
- [5. interrupt() không làm mọi trạng thái chờ trở nên interruptible](#interrupt-limitations)
- [6. interrupt() và volatile stop flag khác nhau thế nào?](#interrupt-vs-stop-flag)
- [7. Quy tắc thực hành](#interrupt-rules)
- [8. Các experiment của phần Interruption](#interruption-experiments)

Phần này học cách **yêu cầu một Thread dừng lại có kiểm soát**.

Mental model quan trọng nhất:

```text
interrupt()
    ≠ kill thread

interrupt()
    = gửi tín hiệu interruption
      để code đang chạy tự hợp tác xử lý
```

Sau phần này cần trả lời được:

- `interrupt()` thực sự làm gì.
- Vì sao busy loop phải chủ động kiểm tra interrupt flag.
- `isInterrupted()` khác `Thread.interrupted()` ở đâu.
- Vì sao `sleep()`, `wait()` và `join()` có thể ném `InterruptedException`.
- Vì sao interrupt flag thường trở về `false` khi `InterruptedException` được ném.
- Khi nào cần restore interrupt status.
- Vì sao cleanup nên nằm trong `finally`.
- Những trạng thái nào không thể thoát ra chỉ bằng `interrupt()`.
- `volatile boolean stop` khác `interrupt()` ở điểm nào khi worker đang block.

## <a id="interrupt-mental-model">1. Interrupt là tín hiệu, không phải lệnh kill</a>

<details>
<summary>Click for details</summary>

Java không thiết kế `interrupt()` như một lệnh cưỡng bức kết thúc Thread.

Khi gọi:

```java
worker.interrupt();
```

caller đang gửi một **interruption request** tới `worker`.

Điều gì xảy ra tiếp theo phụ thuộc vào trạng thái và code của worker.

Nếu worker đang chạy code bình thường:

```text
interrupt()
    ↓
interrupt status = true
    ↓
worker phải tự kiểm tra tín hiệu
    ↓
worker quyết định cleanup và kết thúc
```

Nếu worker đang ở một operation có hỗ trợ interruption như `sleep()`, `wait()` hoặc `join()`, operation đó có thể kết thúc sớm bằng `InterruptedException`.

Vì vậy khái niệm đúng là:

```text
Cooperative Cancellation

caller
  → yêu cầu dừng

worker
  → quan sát yêu cầu
  → cleanup
  → kết thúc an toàn
```

### Vì sao không dùng Thread.stop()?

`Thread.stop()` là API deprecated vì có thể dừng Thread tại một thời điểm không an toàn và làm invariant của shared state bị phá vỡ.

Code hiện đại nên thiết kế task có khả năng phản ứng với cancellation thay vì cưỡng bức kill Thread.

### Demo trong module

Tham khảo:

```text
InterruptionController#interruptBusyWorker()
```

Endpoint:

```text
GET /interruption/busy-loop
```

Experiment tạo một worker chạy CPU loop:

```java
while (!Thread.currentThread().isInterrupted()) {
    // work
}
```

Controller gọi service, service start worker rồi gửi `interrupt()`.

Khi chạy, cần quan sát:

```text
worker bắt đầu
→ interrupt được gửi
→ worker quan sát interrupt flag
→ loop kết thúc
→ Thread TERMINATED
```

**Kết luận:** với code không block ở API interruptible, `interrupt()` chỉ đặt tín hiệu; worker phải chủ động kiểm tra tín hiệu đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interrupt-flag">2. interrupt(), isInterrupted() và Thread.interrupted()</a>

<details>
<summary>Click for details</summary>

Ba API này có vai trò khác nhau.

| API | Loại | Kiểm tra Thread nào? | Có clear flag không? |
| --- | --- | --- | --- |
| `thread.interrupt()` | instance | target thread | gửi interruption request |
| `thread.isInterrupted()` | instance | thread được gọi | Không |
| `Thread.interrupted()` | static | current thread | Có |

Điểm dễ nhầm nhất là `Thread.interrupted()`.

Ví dụ worker đã có interrupt status = `true`:

```java
Thread.currentThread().isInterrupted();
// true

Thread.currentThread().isInterrupted();
// vẫn true

Thread.interrupted();
// trả true và clear status

Thread.interrupted();
// false
```

Mental model:

```text
isInterrupted()
→ observe
→ không consume signal

Thread.interrupted()
→ observe current thread
→ consume/clear signal
```

Không nên clear interrupt status một cách tùy tiện. Việc clear tín hiệu có thể khiến code ở tầng trên không còn biết cancellation đã được yêu cầu.

### Demo trong module

Tham khảo:

```text
InterruptionController#inspectInterruptFlag()
```

Endpoint:

```text
GET /interruption/flag
```

Worker được interrupt trong lúc đang spin, sau đó lần lượt gọi:

```text
isInterrupted()
isInterrupted()
Thread.interrupted()
Thread.interrupted()
isInterrupted()
```

Kết quả cần quan sát:

```text
true
true
true
false
false
```

**Kết luận:** `isInterrupted()` chỉ đọc flag; `Thread.interrupted()` đọc rồi clear interrupt status của current thread.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interrupted-exception">3. InterruptedException và blocking operation</a>

<details>
<summary>Click for details</summary>

Một số operation hỗ trợ interruption trực tiếp, ví dụ:

- `Thread.sleep(...)`;
- `Object.wait(...)`;
- `Thread.join(...)`;
- nhiều API blocking trong `java.util.concurrent`.

Ví dụ:

```java
try {
    Thread.sleep(30_000);
} catch (InterruptedException e) {
    // Thread đã nhận interruption request
}
```

Khi `InterruptedException` được ném bởi các API kiểu này, interrupt status thường đã được **clear**.

Do đó trong `catch`:

```java
Thread.currentThread().isInterrupted()
```

thường trả về `false`.

Nếu method không thể `throw InterruptedException` tiếp nhưng cần bảo toàn tín hiệu cancellation cho code phía trên, pattern phổ biến là:

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
```

Đây gọi là **restore interrupt status**.

Không phải mọi `catch (InterruptedException)` đều bắt buộc restore. Nếu method propagate exception ra ngoài thì caller đã nhận được cancellation signal qua exception. Restore đặc biệt hữu ích khi exception bị bắt lại và không thể/reasonably không nên throw tiếp.

### Demo trong module

Tham khảo:

```text
InterruptionController#interruptSleepingWorker()
```

Endpoint:

```text
GET /interruption/sleep
```

Worker bắt đầu `sleep()` dài, sau đó service gọi `interrupt()`.

Response cần thể hiện:

```text
InterruptedException xảy ra
flag trong catch trước restore = false
flag sau Thread.currentThread().interrupt() = true
worker kết thúc
```

**Kết luận:** interruption của blocking operation thường đi qua `InterruptedException`; exception này và interrupt flag là hai cách biểu diễn cùng cancellation signal ở các thời điểm khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interrupt-cleanup">4. Cleanup khi task bị cancel</a>

<details>
<summary>Click for details</summary>

Cancellation không chỉ có nghĩa là thoát loop.

Task có thể đang giữ:

- file;
- socket;
- temporary resource;
- transaction/resource scope;
- trạng thái cần reset.

Vì vậy lifecycle an toàn thường có dạng:

```java
try {
    while (!Thread.currentThread().isInterrupted()) {
        // work
    }
} finally {
    // cleanup
}
```

Hoặc với blocking operation:

```java
try {
    doBlockingWork();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
} finally {
    cleanup();
}
```

`finally` quan trọng vì cleanup không nên phụ thuộc vào worker kết thúc theo đường "happy path" hay cancellation path.

### Demo trong module

Tham khảo:

```text
InterruptionController#cancelWithCleanup()
```

Endpoint:

```text
GET /interruption/cleanup
```

Experiment mô phỏng một resource được mở trước khi worker bắt đầu làm việc.

Sau khi gửi `interrupt()`, hãy quan sát:

```text
interruptionObserved = true
cleanupCalled = true
resourceOpen = false
workerAlive = false
```

Worker có thể nhận interrupt trước lần kiểm tra điều kiện loop hoặc trong lúc `sleep()`.
Ở trường hợp đầu, nó thoát loop mà không đi qua `catch`; ở trường hợp sau, `sleep()` ném `InterruptedException`.
`interruptionObserved` ghi nhận tín hiệu cancellation ở cả hai đường, không dùng việc có exception hay không để kết luận Thread đã nhận interrupt.

**Kết luận:** cancellation đúng không chỉ dừng execution mà còn phải đưa resource về trạng thái hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interrupt-limitations">5. interrupt() không làm mọi trạng thái chờ trở nên interruptible</a>

<details>
<summary>Click for details</summary>

Không nên hiểu:

```text
interrupt()
→ mọi blocking operation lập tức bị phá vỡ
```

Ví dụ quan trọng là thread đang chờ intrinsic monitor của `synchronized`.

```java
synchronized (monitor) {
    // Thread khác đang giữ monitor
}
```

Nếu worker đang ở `Thread.State.BLOCKED` vì chờ monitor này, gọi `interrupt()`:

- đặt interrupt status;
- **không** làm worker thoát khỏi việc chờ monitor;
- worker vẫn phải đợi monitor được release.

Sau khi lấy được monitor, code có thể kiểm tra interrupt status và xử lý cancellation.

Nếu cần lock acquisition có khả năng phản ứng với interruption, chapter Synchronization sẽ giới thiệu `ReentrantLock.lockInterruptibly()`.

Blocking I/O cũng không nên được gom thành một rule chung. Khả năng interruption phụ thuộc vào API cụ thể; một số NIO channel có semantics interruption riêng trong khi nhiều blocking API khác cần cơ chế đóng resource hoặc cancellation khác.

### Demo trong module

Tham khảo:

```text
InterruptionController#interruptSynchronizedWaiter()
```

Endpoint:

```text
GET /interruption/synchronized-blocked
```

Service giữ một monitor, start worker để worker rơi vào `BLOCKED`, sau đó gọi `interrupt()` khi worker vẫn chưa lấy được monitor.

Điểm cần quan sát:

```text
state before interrupt = BLOCKED
state immediately after interrupt = BLOCKED
interrupt flag = true
```

Sau khi service release monitor:

```text
worker lấy được monitor
→ vẫn quan sát interrupt flag = true
→ worker kết thúc
```

**Kết luận:** interrupt signal có thể tồn tại trong khi Thread vẫn tiếp tục bị `BLOCKED`; khả năng "đánh thức" phụ thuộc vào operation mà Thread đang chờ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interrupt-vs-stop-flag">6. interrupt() và volatile stop flag khác nhau thế nào?</a>

<details>
<summary>Click for details</summary>

Một custom stop flag vẫn là một kỹ thuật cooperative cancellation hợp lệ trong một số loop:

```java
while (!stopRequested) {
    doWork();
}
```

Nếu `stopRequested` được chia sẻ giữa nhiều Thread, visibility phải được bảo đảm, ví dụ bằng `volatile` hoặc một primitive tương đương.

Điểm khác biệt quan trọng là **stop flag chỉ là application state**. Nó không tích hợp với interruption protocol của Java và không tự đánh thức một Thread đang ở operation interruptible như `sleep()`, `wait()` hoặc `join()`.

Ví dụ:

```java
volatile boolean stopRequested;

while (!stopRequested) {
    Thread.sleep(30_000);
}
```

Nếu Thread đang ở giữa `sleep(30_000)` và Thread khác chỉ làm:

```java
stopRequested = true;
```

worker chưa có cơ hội đọc lại flag cho tới khi `sleep()` kết thúc tự nhiên.

Trong khi:

```java
worker.interrupt();
```

có thể làm `sleep()` kết thúc sớm bằng `InterruptedException`.

So sánh mental model:

| Cơ chế | Visibility signal | Đánh thức `sleep/wait/join` | Có interrupt status | Tích hợp với nhiều API concurrency |
| --- | --- | --- | --- | --- |
| `volatile boolean stop` | Có | Không | Không | Không |
| `interrupt()` | Có interruption signal | Có với API hỗ trợ interruption | Có | Có |

Điều này không có nghĩa lúc nào cũng phải dùng `interrupt()` thay cho mọi flag. Một state flag có thể biểu diễn business lifecycle rõ hơn, ví dụ `RUNNING`, `PAUSED`, `STOPPING`. Nhưng nếu task có thể block và cần cancellation responsive, interruption thường là protocol phù hợp hơn.

### Demo trong module

Tham khảo:

```text
InterruptionController#stopFlagVsInterrupt()
GET /interruption/stop-flag-vs-interrupt
```

Experiment cho worker đi vào `sleep(30s)`, sau đó:

```text
set volatile stop flag = true
→ worker vẫn còn alive
→ interrupt worker
→ sleep ném InterruptedException
→ worker kết thúc
```

Response quan trọng:

```text
stopFlagVisible = true
aliveAfterOnlyStopFlag = true
stateAfterOnlyStopFlag = TIMED_WAITING
interruptWokeBlockingOperation = true
workerAliveAfterCleanup = false
```

**Kết luận:** stop flag giúp worker biết *nên dừng*, còn `interrupt()` ngoài việc mang cancellation signal còn có khả năng làm nhiều blocking API kết thúc sớm để worker có cơ hội phản ứng ngay.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interrupt-rules">7. Quy tắc thực hành</a>

<details>
<summary>Click for details</summary>

### Không swallow InterruptedException

Code như sau làm mất cancellation signal:

```java
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    // bỏ qua
}
```

Thay vào đó, thường chọn một trong hai hướng:

```text
1. propagate InterruptedException

hoặc

2. restore interrupt status rồi kết thúc/return
```

### Task CPU-bound phải có cancellation point

Nếu task chạy loop dài mà không gọi API interruptible, cần chủ động kiểm tra:

```java
Thread.currentThread().isInterrupted()
```

Không nhất thiết kiểm tra sau từng instruction; cancellation point nên được đặt ở vị trí hợp lý để cân bằng responsiveness và chi phí kiểm tra.

### Cleanup phải deterministic

Normal learning demo không được để lại Thread sống sau khi endpoint hoàn tất.

Service trong chapter này luôn `join()` worker với timeout và có cleanup fallback nếu cần.

### Không trộn Thread Pool shutdown vào mental model cơ bản

`ExecutorService.shutdown()`, `shutdownNow()`, `awaitTermination()` và Spring executor lifecycle có liên quan tới interruption nhưng thuộc abstraction cao hơn.

Chúng sẽ được học ở phần Thread Pool / Executor và Lifecycle thay vì dùng để giải thích interrupt cơ bản ở đây.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interruption-experiments">8. Các experiment của phần Interruption</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint | Mục đích |
| --- | --- | --- | --- |
| `#interrupt-mental-model` | `InterruptionController#interruptBusyWorker()` | `GET /interruption/busy-loop` | Chứng minh cooperative cancellation của CPU loop |
| `#interrupt-flag` | `InterruptionController#inspectInterruptFlag()` | `GET /interruption/flag` | Phân biệt `isInterrupted()` và `Thread.interrupted()` |
| `#interrupted-exception` | `InterruptionController#interruptSleepingWorker()` | `GET /interruption/sleep` | Quan sát `InterruptedException`, flag clear và restore |
| `#interrupt-cleanup` | `InterruptionController#cancelWithCleanup()` | `GET /interruption/cleanup` | Chứng minh cleanup trong cancellation path |
| `#interrupt-limitations` | `InterruptionController#interruptSynchronizedWaiter()` | `GET /interruption/synchronized-blocked` | Chứng minh `synchronized` monitor acquisition không interruptible |
| `#interrupt-vs-stop-flag` | `InterruptionController#stopFlagVsInterrupt()` | `GET /interruption/stop-flag-vs-interrupt` | So sánh stop flag với interruption khi worker đang block |

Sau phần này cần tự trả lời được:

1. Vì sao `interrupt()` không đồng nghĩa với kill Thread?
2. Busy loop phải làm gì để phản ứng với cancellation?
3. `isInterrupted()` và `Thread.interrupted()` khác nhau ở đâu?
4. Vì sao interrupt flag có thể là `false` bên trong `catch (InterruptedException)`?
5. Khi nào nên restore interrupt status?
6. Vì sao cleanup nên nằm trong `finally`?
7. Vì sao một Thread đang `BLOCKED` chờ `synchronized` vẫn không thoát ngay sau `interrupt()`?
8. Vì sao `volatile boolean stop = true` không thể tự đánh thức một Thread đang `sleep()`?

</details>

- [Quay lại đầu trang](#back-to-top)
