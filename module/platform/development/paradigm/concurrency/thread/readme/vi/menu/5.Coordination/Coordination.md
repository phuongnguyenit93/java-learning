<a id="back-to-top"></a>

# Thread Coordination trong Java

## Menu
- [1. Thread.join(): chờ một Thread kết thúc](#join)
- [2. wait(), notify(), notifyAll() và condition predicate](#wait-notify)
- [3. Condition với ReentrantLock](#condition)
- [4. LockSupport: park/unpark và permit](#lock-support)
- [5. Producer–Consumer và BlockingQueue](#producer-consumer)
- [6. Chọn BlockingQueue theo semantics](#blocking-queue-variants)
- [7. PriorityBlockingQueue: priority không đồng nghĩa fairness](#priority-queue-semantics)
- [8. SynchronousQueue: direct handoff](#synchronous-queue)
- [9. Synchronizers: phối hợp nhiều Thread ở mức cao hơn](#synchronizers)
- [10. Semaphore permit accounting và over-release](#semaphore-permit-accounting)
- [11. Timeout và failure semantics của Synchronizer](#synchronizer-timeout-failure)
- [12. Chọn primitive theo dependency](#coordination-choice)
- [13. Các experiment của phần Coordination](#coordination-experiments)

Synchronization và Coordination có liên quan nhưng không phải cùng một vấn đề.

```text
Synchronization
→ bảo vệ shared state khỏi concurrent access sai

Coordination
→ quyết định Thread nào chờ, khi nào tiếp tục, và các phase phối hợp với nhau ra sao
```

## <a id="join">1. Thread.join(): chờ một Thread kết thúc</a>

<details>
<summary>Click for details</summary>

`join()` là coordination theo lifecycle.

```text
Thread A start worker
        ↓
Thread A gọi worker.join()
        ↓
Thread A chờ
        ↓
worker TERMINATED
        ↓
Thread A tiếp tục
```

Ngoài việc chờ kết thúc, `join()` còn có happens-before guarantee đã học ở phần JMM.

Tham khảo:

```text
CoordinationController#join()
GET /coordination/join
```

**Kết luận:** `join()` phù hợp khi dependency là "tôi chỉ tiếp tục sau khi Thread này hoàn thành".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="wait-notify">2. wait(), notify(), notifyAll() và condition predicate</a>

<details>
<summary>Click for details</summary>

`Object.wait()` chỉ được gọi khi Thread đang giữ monitor tương ứng.

Khi gọi `wait()`:

```text
Thread giữ monitor
→ wait()
→ release monitor
→ vào wait set
```

`notify()` đánh thức một waiter; `notifyAll()` đánh thức tất cả waiter để chúng cạnh tranh lấy lại monitor.

Ba rule dễ nhầm:

1. `wait()`, `notify()` và `notifyAll()` phải thao tác khi caller đang sở hữu monitor tương ứng; nếu không có thể nhận `IllegalMonitorStateException`.
2. `wait()` release monitor trong thời gian chờ, nhưng `Thread.sleep()` **không release monitor** chỉ vì Thread đang ngủ.
3. `notify()`/`notifyAll()` không chuyển monitor ngay cho waiter. Waiter được đánh thức vẫn phải chờ notifier rời `synchronized`/release monitor rồi mới có thể reacquire và tiếp tục.

### Luôn chờ bằng predicate loop

Pattern đúng:

```java
synchronized (monitor) {
    while (!condition) {
        monitor.wait();
    }
    // condition đã đúng
}
```

Không dùng `if` chỉ để kiểm tra một lần. Thread có thể tỉnh dậy nhưng condition chưa phù hợp, và Java cho phép spurious wakeup.

Tham khảo:

```text
CoordinationController#waitNotify()
GET /coordination/wait-notify
```

Experiment đảm bảo waiter thực sự vào wait set trước khi notifier cập nhật predicate và gọi `notifyAll()`.

Demo dùng ba waiter để quan sát riêng hai bước:

```text
notify()
→ chỉ một waiter được đánh thức để kiểm tra lại predicate
→ predicate vẫn false nên waiter quay lại chờ

ready = true + notifyAll()
→ toàn bộ waiter được đánh thức
→ từng Thread cạnh tranh acquire lại monitor
→ tất cả hoàn thành vì predicate đã true
```

**Kết luận:** notification chỉ là tín hiệu "hãy kiểm tra lại"; predicate mới quyết định có được tiếp tục hay không.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="condition">3. Condition với ReentrantLock</a>

<details>
<summary>Click for details</summary>

`Condition` cung cấp condition queue gắn với explicit `Lock`.

```java
lock.lock();
try {
    while (!ready) {
        condition.await();
    }
} finally {
    lock.unlock();
}
```

Một `ReentrantLock` có thể tạo nhiều `Condition`, hữu ích khi nhiều nhóm waiter chờ các predicate khác nhau.

Giống `wait/notify` yêu cầu ownership của monitor, các operation như `await()`, `signal()` và `signalAll()` phải được dùng khi Thread đang giữ **associated Lock**. Nếu không, implementation có thể ném `IllegalMonitorStateException`.

`Condition.await()` có mental model tương tự condition wait của monitor:

```text
caller đang giữ associated Lock
→ await()
→ release Lock trong lúc chờ
→ được signal / interrupt / timeout
→ phải reacquire Lock trước khi await() return/throw theo contract
```

Vì vậy code sau `await()` vẫn chạy trong critical section cho tới khi `finally` gọi `unlock()`.

Tham khảo:

```text
CoordinationController#condition()
GET /coordination/condition
```

Experiment tạo hai wait-set độc lập trên cùng một lock:

```text
dataAvailable      → đánh thức data waiter
shutdownRequested  → đánh thức shutdown waiter
```

Sau `dataAvailable.signal()`, shutdown waiter vẫn tiếp tục chờ cho tới khi chính `shutdownRequested.signal()` được gọi.

**Kết luận:** `Condition` là phiên bản explicit-lock của condition waiting; vẫn cần predicate loop.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lock-support">4. LockSupport: park/unpark và permit</a>

<details>
<summary>Click for details</summary>

`LockSupport.park()` tạm dừng Thread và `unpark(thread)` cấp permit cho Thread đích danh.

Điểm khác biệt hữu ích so với `notify()`:

- không cần giữ intrinsic monitor;
- `unpark` chỉ định chính xác Thread;
- permit có thể được cấp trước khi Thread gọi `park()`.

Permit của `LockSupport` không phải counter cộng dồn vô hạn. Mental model gần với một trạng thái "có permit / chưa có permit": nhiều lần `unpark(thread)` trước một lần `park()` không tích lũy thành nhiều lượt park trong tương lai đều tự đi qua.

`park()` cũng có interruption semantics khác các API như `sleep()`/`wait()`:

```text
park()
→ có thể return vì permit / unpark
→ có thể return vì Thread bị interrupt
→ có thể spurious return

park()
→ không ném InterruptedException
→ caller phải tự kiểm tra predicate và/hoặc interrupt status
```

Vì vậy pattern thực tế vẫn là một loop quanh condition/cancellation state, không phải gọi `park()` một lần rồi mặc định rằng nguyên nhân return chắc chắn là `unpark()`.

Một nuance khác: permit có thể được cấp **sau khi Thread đã start nhưng trước lần `park()` tương ứng**. Không nên mở rộng câu đó thành guarantee rằng gọi `unpark(thread)` trên một Thread **chưa start** sẽ lưu permit cho lần chạy tương lai.

Khi xây primitive/framework thấp hơn, overload `park(blocker)` hữu ích cho diagnostics. JVM có thể expose blocker object qua `LockSupport.getBlocker(thread)`, giúp thread dump/debugging cho biết Thread đang park thay mặt cho abstraction nào thay vì chỉ thấy một điểm park mơ hồ.

Tham khảo:

```text
CoordinationController#lockSupport()
GET /coordination/lock-support
```

Demo park ba worker. Hai worker đầu dùng predicate loop để chứng minh targeted `unpark`; worker thứ ba được interrupt trong lúc park để quan sát interruption semantics. Kết quả chính:

```text
targetedWorkerResumed = true
otherWorkerStillParked = true
parkReturnedWhenInterrupted = true
interruptStatusObservedAfterPark = true
parkThrowsInterruptedException = false
blockerVisibleWhileParked = true
```

Sau đó experiment `unpark(first)` và cleanup toàn bộ worker trước khi return.

Điều này chứng minh trực tiếp rằng `unpark(thread)` target một Thread cụ thể, còn interrupt là một reason khác làm `park()` return. Permit pre-issue (`unpark` sau start nhưng trước `park`) vẫn là semantics hợp lệ của `LockSupport`, nhưng không dùng làm bằng chứng duy nhất vì `park()` cũng cho phép spurious return.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="producer-consumer">5. Producer–Consumer và BlockingQueue</a>

<details>
<summary>Click for details</summary>

Producer–Consumer tách tốc độ tạo dữ liệu khỏi tốc độ xử lý dữ liệu bằng một buffer trung gian.

```text
Producer
   ↓ put
BlockingQueue
   ↓ take
Consumer
```

Với bounded queue, producer có thể bị backpressure khi buffer đầy.

Các nhóm method thường gặp:

| Hành vi | Insert | Remove |
| --- | --- | --- |
| Exception | `add` | `remove` |
| Special value | `offer` | `poll` |
| Block | `put` | `take` |
| Timed wait | `offer(timeout)` | `poll(timeout)` |

Tham khảo:

```text
CoordinationController#blockingQueue()
GET /coordination/blocking-queue
```

Demo sản xuất và tiêu thụ số item hữu hạn rồi join cả hai Thread. Không để consumer `while(true)` tồn tại sau request.

Các operation `put()`, `take()`, `offer(timeout)` và `poll(timeout)` có thể phản ứng với interruption. Vì vậy producer/consumer loop vẫn phải preserve cancellation semantics thay vì swallow `InterruptedException`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="blocking-queue-variants">6. Chọn BlockingQueue theo semantics</a>

<details>
<summary>Click for details</summary>

Không phải mọi `BlockingQueue` chỉ khác implementation detail.

| Queue | Capacity / ordering | Điểm cần nhớ |
| --- | --- | --- |
| `ArrayBlockingQueue` | bounded, array-backed | capacity cố định; constructor có fairness option |
| `LinkedBlockingQueue` | bounded nếu truyền capacity; mặc định capacity rất lớn | phù hợp backlog FIFO nhưng vẫn cần chủ động giới hạn trong production |
| `SynchronousQueue` | không lưu phần tử | direct handoff giữa producer và consumer |
| `PriorityBlockingQueue` | priority-ordered, effectively unbounded | ưu tiên theo comparator/natural order, không phải FIFO thông thường |

`PriorityBlockingQueue` không cung cấp backpressure bằng capacity như `ArrayBlockingQueue`. Nếu producer nhanh hơn consumer, backlog vẫn có thể tăng.

Ngoài ra, nếu hai phần tử có cùng priority thì không nên dựa vào FIFO như một contract mặc định. Nếu business cần stable ordering, comparator/data model phải encode thêm tie-breaker rõ ràng, ví dụ sequence number.

`drainTo(...)` hữu ích khi cần lấy một batch phần tử khỏi `BlockingQueue` mà không lặp `poll()` từng item.

Tham khảo:

```text
CoordinationController#blockingQueueVariants()
GET /coordination/blocking-queue-variants
```

Experiment cho thấy:

```text
ArrayBlockingQueue đầy → offer() trả false
LinkedBlockingQueue → drainTo lấy batch hữu hạn
PriorityBlockingQueue → otp trước delivery trước promotion theo priority
```

**Kết luận:** chọn queue dựa trên capacity, ordering và handoff semantics; không chọn chỉ dựa trên benchmark tổng quát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="priority-queue-semantics">7. PriorityBlockingQueue: priority không đồng nghĩa fairness</a>

<details>
<summary>Click for details</summary>

Priority queue trả phần tử theo ordering của comparator/natural order, nhưng có hai footgun:

```text
equal priority
→ không nên mặc định FIFO

high-priority item tới liên tục
→ low-priority item có thể phải chờ rất lâu
```

Đây là **business scheduling semantics**, không chỉ là implementation detail.

Tham khảo:

```text
CoordinationController#priorityQueueSemantics()
GET /coordination/priority-queue-semantics
```

Experiment tạo một task priority cao và hai task cùng priority. Output trả observed order nhưng đồng thời ghi rõ:

```text
higherPriorityComesFirst = true
equalPriorityFifoIsContract = false
boundedCapacityBackpressure = false
```

**Kết luận:** nếu cần FIFO trong cùng priority, hãy đưa sequence vào ordering rule thay vì dựa vào insertion order ngầm định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronous-queue">8. SynchronousQueue: direct handoff</a>

<details>
<summary>Click for details</summary>

`SynchronousQueue` không lưu phần tử như buffer thông thường.

```text
producer put(item)
        ↕ rendezvous
consumer take()
```

Mỗi handoff cần một operation phía đối diện tương ứng.

Tham khảo:

```text
CoordinationController#synchronousQueue()
GET /coordination/synchronous-queue
```

**Kết luận:** đây là coordination point trực tiếp, không phải queue dùng để tích trữ backlog.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizers">9. Synchronizers: phối hợp nhiều Thread ở mức cao hơn</a>

<details>
<summary>Click for details</summary>

### CountDownLatch

Một hoặc nhiều Thread chờ counter về 0.

```text
new CountDownLatch(N)
workers countDown()
coordinator await()
```

Latch là one-shot: counter đã về 0 thì không reset để dùng vòng mới.

`await(timeout, unit)` trả `false` khi hết thời gian mà counter chưa về 0. Timeout của Thread đang chờ **không tự cancel** các worker đang chạy.

### CyclicBarrier

Một nhóm Thread cùng chờ nhau tại barrier. Khi đủ parties, cả nhóm tiếp tục. Barrier có thể tái sử dụng qua nhiều generation nếu không bị broken.

Nếu một participant timeout/interrupted trong lúc chờ, barrier có thể chuyển sang trạng thái **broken**. Các participant khác có thể nhận `BrokenBarrierException`. `reset()` tạo generation mới nhưng chỉ nên dùng khi application đã hiểu rõ state của các participant cũ.

Barrier còn có thể nhận một `barrierAction`, chạy khi party cuối cùng tới checkpoint trước khi các party được release.

### Semaphore

Semaphore quản lý số permit, phù hợp để giới hạn concurrent access tới một resource có capacity hữu hạn.

Rule cleanup:

```java
boolean acquired = false;
try {
    semaphore.acquire();
    acquired = true;
    // use resource
} finally {
    if (acquired) {
        semaphore.release();
    }
}
```

Không `release()` nếu chưa acquire thành công.

### Fair và non-fair Semaphore

`new Semaphore(n)` mặc định non-fair. `new Semaphore(n, true)` chọn fairness cho các acquisition có áp dụng policy này, theo thứ tự tới điểm xếp hàng nội bộ. Nó không bảo đảm thứ tự `Thread.start()` hay thứ tự log.

Fairness giúp hạn chế barging/starvation khi tranh chấp permit, nhưng có thể giảm throughput. Đặc biệt, `tryAcquire()` **không timeout** không tuân theo fairness: nó có thể lấy permit ngay dù đã có Thread khác chờ. Không suy luận policy chỉ từ tên constructor.

Experiment `synchronizers()` bên dưới tập trung vào giới hạn concurrent access và hoàn trả permit; nó không phải phép đo fairness. Đây là tiêu chí chọn policy, không phải kết luận từ thứ tự vài dòng output.

Tham khảo: [Semaphore — Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html).

Điểm rất quan trọng: `Semaphore` không enforce ownership giống lock. Code có thể gọi `release()` dù Thread hiện tại không phải Thread đã acquire permit. Vì vậy unmatched `release()` có thể làm permit count tăng sai và phá giới hạn concurrency.

Ngoài `acquire()`, các API đáng nhớ gồm:

```text
acquire(n)
tryAcquire()
tryAcquire(timeout)
```

Chúng giúp biểu diễn resource cần nhiều permit hoặc admission có timeout.

### Phaser

`Phaser` phù hợp cho coordination nhiều phase và số participant có thể register/deregister động.

Các operation quan trọng:

```text
register()
arrive()
arriveAndAwaitAdvance()
arriveAndDeregister()
```

Khác `CountDownLatch`, Phaser có thể tiến qua nhiều phase và participant có thể thay đổi theo lifecycle của workflow.

Các API chờ phase không có cùng interruption semantics:

```text
awaitAdvance(...)
→ chờ phase advance
→ không phải interruptible wait API

awaitAdvanceInterruptibly(...)
→ phản ứng với interrupt

awaitAdvanceInterruptibly(..., timeout, unit)
→ phản ứng với interrupt + có bounded timeout
```

Vì vậy nếu coordinator phải hỗ trợ cancellation/timeout, hãy chọn interruptible/timed API thay vì chỉ nhìn tên `await`.

### Exchanger

Hai Thread gặp nhau tại một exchange point và trao đổi object cho nhau.

Nếu partner không tới, `exchange(value)` có thể chờ. Khi workflow cần bounded waiting, dùng timed overload:

```java
exchange(value, timeout, unit)
```

Semantics quan trọng:

```text
partner tới
→ exchange thành công

partner không tới trước deadline
→ TimeoutException

Thread đang chờ bị interrupt
→ InterruptedException
```

Tham khảo:

```text
CoordinationController#synchronizers()
GET /coordination/synchronizers
```

Endpoint chạy các example nhỏ, bounded và trả về một result map để thấy semantics của từng synchronizer.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="semaphore-permit-accounting">10. Semaphore permit accounting và over-release</a>

<details>
<summary>Click for details</summary>

Tham khảo:

```text
CoordinationController#semaphorePermitAccounting()
GET /coordination/semaphore-permit-accounting
```

Experiment bắt đầu với một permit rồi cố tình gọi `release()` mà chưa acquire:

```text
initialPermits = 1
permitsAfterUnmatchedRelease = 2
semaphoreEnforcesOwner = false
```

Sau over-release, code thậm chí có thể acquire hai permit cùng lúc dù resource ban đầu chỉ có capacity 1.

**Kết luận:** với Semaphore, permit accounting là trách nhiệm của application. Pattern `boolean acquired` + `finally` giúp tránh release khi acquisition thất bại/interrupted.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronizer-timeout-failure">11. Timeout và failure semantics của Synchronizer</a>

<details>
<summary>Click for details</summary>

Happy-path demo chưa đủ để sử dụng synchronizer an toàn. Cần hiểu điều gì xảy ra khi một participant không đến đúng hạn.

Tham khảo:

```text
CoordinationController#synchronizersTimeout()
GET /coordination/synchronizers-timeout
```

Endpoint minh họa bounded:

```text
CountDownLatch.await(timeout)
→ false khi caller hết thời gian chờ
→ worker thật vẫn tiếp tục chạy và hoàn thành bình thường

CyclicBarrier participant timeout
→ barrier broken
→ reset() đưa barrier về generation mới
→ generation kế tiếp có thể chạy thành công với participant mới

Semaphore acquire(2) + tryAcquire(timeout)
→ timeout nếu không còn permit

Phaser arrive / arriveAndAwaitAdvance / deregister
→ một participant rời sau phase 0
→ participant còn lại tiếp tục phase 1
→ thể hiện dynamic registration/deregistration

Phaser coordinator
→ dùng awaitAdvanceInterruptibly(..., timeout, ...)
→ bounded wait và phản ứng được với interruption

Exchanger không có partner
→ timed exchange ném TimeoutException

Exchanger đang chờ partner
→ interrupt làm blocking exchange kết thúc bằng InterruptedException
```

Riêng latch demo, worker được giữ bằng một latch khác và chỉ được release **sau khi caller đã quan sát timeout**. Không dùng chênh lệch hai khoảng `sleep()` để giả định timeout chắc chắn xảy ra.

Các trường cần quan sát trong `countDownLatch`:

```text
completedBeforeTimeout = false
workerContinuedAfterCallerTimeout = true
workerTerminatedNormally = true
```

Nếu caller bị interrupt hoặc bước quan sát thất bại, service vẫn giải phóng gate và cleanup worker trong `finally`. Với Phaser, coordinator dùng timed interruptible wait; đường cleanup force-terminate Phaser để worker không kẹt ở phase chưa đủ participant. Với Exchanger, experiment chạy cả timed wait và interrupt path để không để participant chờ partner vô hạn.

**Kết luận:** timeout không chỉ là "đợi ít hơn"; mỗi primitive có failure state và recovery semantics riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="coordination-choice">12. Chọn primitive theo dependency</a>

<details>
<summary>Click for details</summary>

```text
đợi Thread kết thúc
→ join

đợi predicate trên monitor
→ wait/notifyAll

đợi predicate với explicit Lock
→ Condition

park/unpark Thread cụ thể
→ LockSupport

buffer producer-consumer
→ BlockingQueue

đợi N việc hoàn thành
→ CountDownLatch

N Thread gặp nhau tại checkpoint lặp lại
→ CyclicBarrier

giới hạn concurrent permits
→ Semaphore

workflow nhiều phase/dynamic parties
→ Phaser

hai Thread trao đổi object
→ Exchanger
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="coordination-experiments">13. Các experiment của phần Coordination</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#join` | `CoordinationController#join()` | `GET /coordination/join` |
| `#wait-notify` | `CoordinationController#waitNotify()` | `GET /coordination/wait-notify` |
| `#condition` | `CoordinationController#condition()` | `GET /coordination/condition` |
| `#lock-support` | `CoordinationController#lockSupport()` | `GET /coordination/lock-support` |
| `#producer-consumer` | `CoordinationController#blockingQueue()` | `GET /coordination/blocking-queue` |
| `#blocking-queue-variants` | `CoordinationController#blockingQueueVariants()` | `GET /coordination/blocking-queue-variants` |
| `#priority-queue-semantics` | `CoordinationController#priorityQueueSemantics()` | `GET /coordination/priority-queue-semantics` |
| `#synchronous-queue` | `CoordinationController#synchronousQueue()` | `GET /coordination/synchronous-queue` |
| `#synchronizers` | `CoordinationController#synchronizers()` | `GET /coordination/synchronizers` |
| `#semaphore-permit-accounting` | `CoordinationController#semaphorePermitAccounting()` | `GET /coordination/semaphore-permit-accounting` |
| `#synchronizer-timeout-failure` | `CoordinationController#synchronizersTimeout()` | `GET /coordination/synchronizers-timeout` |

</details>

- [Quay lại đầu trang](#back-to-top)
