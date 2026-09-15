# 📂 README MODULE STRUCTURE (VI)

* **1.Basic**
    * [Basic](readme/vi/menu/1.Basic/Basic.md)
* **2.Interruption**
    * [Interruption](readme/vi/menu/2.Interruption/Interruption.md)
* **3.Concurrency_Problems**
    * [ConcurrencyProblem](readme/vi/menu/3.Concurrency_Problems/ConcurrencyProblem.md)
* **4.Synchronization**
    * [Synchronization](readme/vi/menu/4.Synchronization/Synchronization.md)
* **5.Coordination**
    * [Coordination](readme/vi/menu/5.Coordination/Coordination.md)
* **6.Thread_Pool_Executor**
    * [ThreadPoolExecutor](readme/vi/menu/6.Thread_Pool_Executor/ThreadPoolExecutor.md)
* **7.Async**
    * [Async](readme/vi/menu/7.Async/Async.md)
* **8.Context**
    * [ThreadLocal](readme/vi/menu/8.Context/ThreadLocal.md)
* **9.Spring_Task_Executor**
    * [SpringExecutor](readme/vi/menu/9.Spring_Task_Executor/SpringExecutor.md)
* **10.Leak**
    * [Leak](readme/vi/menu/10.Leak/Leak.md)
* **11.Virtual_Thread**
    * [VirtualThread](readme/vi/menu/11.Virtual_Thread/VirtualThread.md)
* **12.Q&A**
    * [Q&A](readme/vi/menu/12.Q&A/Q&A.md)

# Thread & Concurrency

Module này được xây dựng để học Thread và Concurrency theo hướng **từ nền tảng → vấn đề thực tế → công cụ xử lý → cách áp dụng trong Spring Boot**.

Mục tiêu không phải chỉ nhớ API, mà phải hiểu được:

- Thread được tạo và vận hành như thế nào.
- Điều gì xảy ra khi nhiều thread cùng truy cập dữ liệu.
- Vì sao race condition, visibility, ordering và deadlock xuất hiện.
- Khi nào nên dùng synchronization, lock, atomic, concurrent collection hoặc coordination primitive.
- Thread Pool và Executor quản lý tài nguyên như thế nào.
- Async khác gì với parallel và non-blocking.
- Context được truyền giữa các thread ra sao.
- Cách Spring Boot quản lý task executor.
- Những lỗi lifecycle như thread leak, pool leak và cách nhận biết chúng.
- Virtual Thread giải quyết bài toán scalability như thế nào và giới hạn của nó.

---

## Learning Flow

```text
1. Basic Thread
        ↓
2. Interruption & Cancellation
        ↓
3. Concurrency Problems & Java Memory Model
        ↓
4. Synchronization
        ↓
5. Thread Coordination
        ↓
6. Thread Pool & Executor
        ↓
7. Future / CompletableFuture / Async
        ↓
8. ThreadLocal & Context Propagation
        ↓
9. Spring Task Executor
        ↓
10. Thread / Pool Lifecycle & Leak
        ↓
11. Virtual Thread
        ↓
12. Q&A / Tổng hợp
```

---

## 1. Basic Thread

Bắt đầu từ bản chất của Thread trước khi học các công cụ concurrency cấp cao hơn.

Nội dung chính:

- Process và Thread.
- Tạo Thread bằng `Thread`, `Runnable`, `Callable`.
- `start()` và `run()` khác nhau như thế nào.
- Thread lifecycle và `Thread.State`.
- Daemon Thread.
- Quan sát thread name, state và execution flow.

Sau phần này cần trả lời được:

> Một Thread được tạo, bắt đầu, chạy và kết thúc như thế nào?

---

## 2. Interruption & Cancellation

Thread trong Java không nên bị "kill" cưỡng bức. Việc dừng thread chủ yếu dựa trên cơ chế cooperative cancellation.

Nội dung chính:

- `interrupt()` thực sự làm gì.
- Interrupt flag.
- `InterruptedException`.
- Blocking operation và interruption.
- Cách viết loop có thể dừng an toàn.
- Cách cleanup resource khi task bị hủy.

Sau phần này cần trả lời được:

> Làm thế nào để yêu cầu một Thread dừng đúng cách?

---

## 3. Concurrency Problems & Java Memory Model

Trước khi học cách đồng bộ, cần hiểu rõ các vấn đề mà synchronization đang giải quyết.

Nội dung chính:

- Shared mutable state.
- Race condition.
- Atomicity.
- Visibility.
- Ordering.
- Java Memory Model.
- Happens-before.
- Safe publication, `final` field initialization safety và `this` escape.
- `volatile`.
- Deadlock, livelock và starvation.

Sau phần này cần trả lời được:

> Vì sao code chạy đúng với một Thread nhưng có thể sai khi nhiều Thread cùng chạy?

---

## 4. Synchronization

Sau khi hiểu nguyên nhân của concurrency bug, bắt đầu học các cơ chế bảo vệ shared state.

Nội dung chính:

- `synchronized` và monitor.
- `ReentrantLock`.
- Fair lock, `tryLock()` và `lockInterruptibly()`.
- `ReadWriteLock`.
- `StampedLock`.
- Atomic classes và CAS.
- `LongAdder`, `LongAccumulator`.
- Concurrent collections.

Mục tiêu của phần này không phải tìm ra một công cụ "tốt nhất", mà hiểu trade-off của từng cơ chế.

---

## 5. Thread Coordination

Synchronization bảo vệ dữ liệu; coordination giúp nhiều Thread phối hợp thứ tự thực thi và trao đổi tín hiệu.

Nội dung chính:

- `join()`.
- `wait()`, `notify()`, `notifyAll()`.
- Condition predicate và spurious wakeup.
- `Condition`.
- `LockSupport`.
- Producer - Consumer.
- `BlockingQueue`.
- `CountDownLatch`.
- `CyclicBarrier`.
- `Semaphore`.
- `Phaser`.
- `Exchanger`.

Sau phần này cần phân biệt rõ:

```text
Synchronization → bảo vệ shared state
Coordination    → phối hợp execution giữa các thread
```

---

## 6. Thread Pool & Executor

Sau khi hiểu Thread trực tiếp, chuyển sang cách ứng dụng thực tế quản lý số lượng lớn task.

Nội dung chính:

- Executor abstraction.
- `ExecutorService`.
- `ThreadPoolExecutor`.
- Core pool size, maximum pool size.
- Work queue.
- Keep alive time.
- ThreadFactory.
- Rejection policy.
- Graceful shutdown.
- Bounded queue và backpressure.

Sau phần này cần trả lời được:

> Vì sao application thực tế thường submit task vào Executor thay vì tự tạo Thread cho từng công việc?

---

## 7. Future / CompletableFuture / Async

Khi task được chạy bởi Executor, vấn đề tiếp theo là lấy kết quả và tổ chức nhiều task bất đồng bộ.

Nội dung chính:

- `Future`.
- `CompletableFuture`.
- Chaining và composition.
- Exception handling.
- `allOf()` / `anyOf()`.
- Executor của từng stage.
- Phân biệt synchronous, asynchronous, parallel và non-blocking.

Điểm cần ghi nhớ:

> Async không đồng nghĩa với non-blocking.

---

## 8. ThreadLocal & Context Propagation

Sau khi đã hiểu Executor và thread reuse, mới đi vào dữ liệu gắn với execution context của từng Thread.

Nội dung chính:

- `ThreadLocal`.
- `InheritableThreadLocal`.
- Thread reuse và nguy cơ stale context.
- Cleanup bằng `remove()`.
- Context propagation.
- Task decorator.

Sau phần này cần hiểu rằng ThreadLocal chủ yếu giúp **tránh chia sẻ một loại state giữa các thread**, không phải là cơ chế thay thế synchronization cho shared state.

---

## 9. Spring Task Executor

Áp dụng kiến thức Executor vào Spring Boot.

Nội dung chính:

- `TaskExecutor`.
- `ThreadPoolTaskExecutor`.
- `@Async`.
- Custom executor.
- Context propagation trong Spring.
- Shutdown và lifecycle do Spring quản lý.

Mục tiêu là hiểu Spring đang bọc các khái niệm Java concurrency nào, thay vì coi `@Async` như một cơ chế độc lập.

---

## 10. Thread / Pool Lifecycle & Leak

Một concurrency demo chỉ đúng khi tài nguyên của nó cũng có lifecycle đúng.

Nội dung chính:

- Thread leak.
- Executor leak.
- Queue growth.
- Native thread exhaustion.
- Context switching overhead.
- Scheduled task duplication.
- Thread dump và cách quan sát hệ thống.
- Shutdown đúng cách.

Nguyên tắc của module:

```text
Demo lỗi có chủ đích
→ phải được ghi rõ và giới hạn phạm vi

Demo bình thường
→ phải cleanup toàn bộ thread/executor/resource đã tạo
```

---

## 11. Virtual Thread

Cuối cùng mới học Virtual Thread, sau khi đã hiểu chi phí và giới hạn của Platform Thread.

Nội dung chính:

- Platform Thread và Virtual Thread.
- Carrier Thread.
- Mount / unmount.
- Blocking operation với Virtual Thread.
- Pinning trong Java 21.
- Virtual Thread và Executor.
- Virtual Thread trong Spring Boot.
- Throughput và latency.
- Khi nào Virtual Thread phù hợp và khi nào không.

Virtual Thread không thay đổi các nguyên tắc về race condition, synchronization hay shared mutable state đã học ở các phần trước.

---

## 12. Q&A / Tổng hợp

Phần cuối dùng để kết nối các khái niệm đã học và xử lý những câu hỏi dễ gây nhầm lẫn.

Ví dụ:

- Stack và Heap liên quan gì tới Thread Safety?
- Biến local có luôn Thread Safe không?
- `volatile` khác `Atomic` và `synchronized` ở đâu?
- Thread Pool khác Virtual Thread như thế nào?
- Async có đồng nghĩa với tạo Thread mới không?
- Khi nào dùng BlockingQueue thay vì tự `wait/notify`?
- Khi nào nên dùng lock và khi nào nên tránh shared mutable state hoàn toàn?

---

## Cách học trong module

Mỗi chủ đề nên được tiếp cận theo cùng một flow:

```text
Kiến thức / Mental Model
        ↓
Vấn đề cần giải quyết
        ↓
Controller kích hoạt demo
        ↓
Service thực hiện experiment
        ↓
Quan sát thread / state / timing / output
        ↓
Giải thích kết quả
```

Controller trong module này đóng vai trò như **nút kích hoạt thí nghiệm**, không phải business API.

Service là nơi triển khai experiment để chứng minh kiến thức trong README.

Mỗi experiment cần hướng tới các tiêu chí:

1. Mental model chính xác.
2. Demo thực sự chứng minh đúng điều đang giải thích.
3. Kết quả đủ rõ để quan sát và lặp lại.
4. Thread, executor và resource được cleanup đúng lifecycle.
5. README, endpoint và code có cùng một ý nghĩa.
