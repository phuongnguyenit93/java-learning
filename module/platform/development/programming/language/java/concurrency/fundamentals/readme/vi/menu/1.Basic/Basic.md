<a id="back-to-top"></a>

# Thread cơ bản trong Java

## Menu
- [1. Process và Thread](#process-and-thread)
- [2. Phân biệt Thread, Runnable, Callable và Executor](#thread-task)
- [3. start() và run() khác nhau như thế nào?](#start-vs-run)
- [4. Tạo và chạy Thread trực tiếp](#create-thread)
- [5. Thread lifecycle và Thread.State](#thread-state)
- [6. Daemon Thread và JVM lifecycle](#daemon-thread)
- [7. Các experiment của phần Basic](#basic-endpoints)

Phần này xây dựng mental model nền tảng trước khi đi vào interruption, Java Memory Model, synchronization, coordination hay thread pool.

Mục tiêu sau khi học xong:

- Phân biệt được Process, Thread và Task.
- Hiểu `start()` khác `run()` ở điểm nào.
- Biết hai cách trực tiếp để tạo Platform Thread bằng `Thread` và `Runnable`.
- Hiểu `Callable` không tự tạo Thread; nó chỉ mô tả một task có kết quả.
- Đọc được các trạng thái trong `Thread.State`.
- Hiểu chính xác Daemon Thread ảnh hưởng tới thời điểm JVM kết thúc như thế nào.

## <a id="process-and-thread">1. Process và Thread</a>

<details>
<summary>Click for details</summary>

### Process là gì?

Process là một chương trình đang thực thi và có không gian tài nguyên riêng do hệ điều hành quản lý.

Ví dụ:

- IntelliJ IDEA đang chạy là một process.
- Một JVM chạy Spring Boot application là một process.
- Chrome có thể sử dụng nhiều process cho các tab hoặc thành phần khác nhau.

Các process được cách ly với nhau. Nếu muốn trao đổi dữ liệu, chúng thường phải sử dụng một cơ chế IPC như socket, pipe, file hoặc shared memory.

### Thread là gì?

Thread là một luồng thực thi bên trong process.

Một JVM process thường có nhiều thread cùng tồn tại:

```text
JVM Process
│
├── main thread
├── HTTP worker thread
├── GC thread
├── scheduler thread
└── các thread khác
```

Các thread trong cùng process có thể cùng truy cập những object nằm trong heap của process. Mỗi thread đồng thời có execution stack riêng để lưu call stack và dữ liệu local của lần thực thi đó.

Điều quan trọng là:

```text
Nhiều Thread
    ↓
có thể cùng truy cập Shared Mutable State
    ↓
Concurrency Problem có thể xuất hiện
```

Race condition, visibility, ordering và synchronization sẽ được học ở các phần sau. Ở phần Basic chỉ cần ghi nhớ rằng việc nhiều thread cùng nhìn thấy một object không đồng nghĩa việc truy cập object đó luôn an toàn.

### Process và Thread khác nhau ở đâu?

| Đặc điểm | Process | Thread |
| --- | --- | --- |
| Phạm vi | Một chương trình đang chạy | Một luồng thực thi bên trong process |
| Không gian bộ nhớ | Có không gian địa chỉ riêng | Cùng process nên có thể cùng truy cập heap |
| Stack | Process có thể chứa nhiều thread, mỗi thread có stack riêng | Mỗi thread có stack riêng |
| Giao tiếp | Thường cần IPC | Có thể giao tiếp thông qua shared object |
| Chi phí tạo/chuyển đổi | Thường lớn hơn thread | Thường nhẹ hơn process |
| Rủi ro concurrency | Cách ly tốt hơn giữa các process | Dễ phát sinh lỗi khi chia sẻ mutable state |

### Demo trong module

Tham khảo controller:

```text
BasicThreadController#processAndThread()
```

Endpoint:

```text
GET /basic/process-thread
```

Khi chạy endpoint này, response trả về:

- PID của JVM process hiện tại.
- Tên thread đang xử lý HTTP request.
- ID của thread đó.
- Thread đó có phải daemon hay không.
- Trạng thái hiện tại của thread.

Điểm cần quan sát là **một HTTP request không tự tồn tại độc lập**. Nó đang được thực thi bởi một thread cụ thể bên trong JVM process.

**Kết luận:** Process là phạm vi chứa tài nguyên và nhiều thread; Thread là execution flow đang thực sự chạy code bên trong process đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-task">2. Phân biệt Thread, Runnable, Callable và Executor</a>

<details>
<summary>Click for details</summary>

Đây là điểm rất dễ học sai nếu chỉ nhớ các API riêng lẻ.

### Thread

`Thread` đại diện cho một luồng thực thi.

Ví dụ:

```java
Thread worker = new Thread(() -> {
    System.out.println(Thread.currentThread().getName());
});

worker.start();
```

### Runnable

`Runnable` mô tả một công việc:

```java
Runnable task = () -> {
    System.out.println("Doing work");
};
```

`Runnable` không phải Thread và tự nó không tạo ra thread mới.

Muốn task chạy trên một Thread mới:

```java
Thread worker = new Thread(task);
worker.start();
```

Mental model:

```text
Runnable
   = WHAT to run

Thread
   = WHERE execution happens
```

### Callable

`Callable<V>` cũng mô tả một task nhưng có thể:

- trả về kết quả;
- ném checked exception.

```java
Callable<String> task = () -> "result";
```

`Callable` cũng không tự tạo Thread.

Ở phần Basic, module dùng `FutureTask` để kết nối `Callable` với một Thread trực tiếp:

```java
Callable<String> task = () -> "result";
FutureTask<String> futureTask = new FutureTask<>(task);

Thread worker = new Thread(futureTask, "callable-worker");
worker.start();

String result = futureTask.get();
```

`Future`, `ExecutorService` và `CompletableFuture` sẽ được học kỹ ở các chapter sau.

### Executor

Executor là abstraction dùng để nhận task và quyết định cách task được thực thi.

Trong phần Basic chỉ cần ghi nhớ:

```text
Task abstraction
    Runnable / Callable
            ↓
Execution mechanism
    Thread / Executor
```

Không nên gọi `Callable` hoặc Thread Pool là "cách tạo Thread" vì chúng đảm nhận vai trò khác nhau.

### Liên hệ với code trong module

Tham khảo:

```text
CreateThreadController#createByExtends()
CreateThreadController#createByRunnable()
CreateThreadController#createByCallable()
```

Ba method này được đặt cạnh nhau để nhìn rõ ba vai trò khác nhau:

```text
MyWorker extends Thread
    → task và execution mechanism bị gắn vào cùng một class

Runnable + Thread
    → task và Thread được tách rời

Callable + FutureTask + Thread
    → task có kết quả vẫn cần một execution mechanism để chạy
```

**Kết luận:** `Runnable` và `Callable` mô tả **công việc**; `Thread` hoặc `Executor` chịu trách nhiệm **thực thi công việc**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="start-vs-run">3. start() và run() khác nhau như thế nào?</a>

<details>
<summary>Click for details</summary>

Đây là kiến thức nền tảng nhất khi làm việc trực tiếp với `Thread`.

Giả sử có:

```java
Thread worker = new Thread(task, "worker-thread");
```

### Gọi run()

```java
worker.run();
```

Đây chỉ là một lời gọi method Java thông thường.

Không có thread mới được start.

`task` chạy ngay trên thread đang gọi `run()`.

```text
HTTP request thread
      ↓
worker.run()
      ↓
task vẫn chạy trên HTTP request thread
```

### Gọi start()

```java
worker.start();
```

`start()` yêu cầu JVM khởi động execution của Thread đó. Sau đó JVM sẽ gọi `run()` trên thread mới.

```text
HTTP request thread
      │
      └── worker.start()
              ↓
        worker-thread
              ↓
            run()
```

Vì vậy:

```text
run()   → method call bình thường
start() → bắt đầu lifecycle của một Thread mới
```

Một object `Thread` chỉ được `start()` một lần. Nếu gọi `start()` lần thứ hai sau khi nó đã được start, JVM ném `IllegalThreadStateException`.

### Demo trong module

Tham khảo controller:

```text
BasicThreadController#startVsRun()
```

Endpoint:

```text
GET /basic/start-vs-run
```

Demo chạy cùng một `Runnable` theo hai cách:

1. gọi trực tiếp `run()`;
2. tạo Thread và gọi `start()`.

Khi chạy, hãy so sánh tên thread:

```text
run() trực tiếp
→ task chạy trên caller/request thread

start()
→ task chạy trên basic-start-worker
```

Điểm cần quan sát không phải thứ tự log, mà là **thread nào đang thực thi `run()`**.

**Kết luận:** gọi `run()` chỉ là gọi method bình thường; gọi `start()` mới bắt đầu lifecycle của một Thread riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="create-thread">4. Tạo và chạy Thread trực tiếp</a>

<details>
<summary>Click for details</summary>

Trong phần Basic, mục tiêu là hiểu API mức thấp trước khi học Executor.

### Cách 1: extends Thread

```java
public class MyWorker extends Thread {

    public MyWorker(String name) {
        super(name);
    }

    @Override
    public void run() {
        System.out.println(
                "Running on " + Thread.currentThread().getName()
        );
    }
}
```

Sử dụng:

```java
Thread worker = new MyWorker("extends-thread-worker");
worker.start();
```

Cách này giúp nhìn rõ quan hệ kế thừa với `Thread`, nhưng nó làm class công việc bị gắn chặt với execution mechanism.

### Cách 2: Runnable + Thread

```java
Runnable task = () -> {
    System.out.println(
            "Running on " + Thread.currentThread().getName()
    );
};

Thread worker = new Thread(task, "runnable-worker");
worker.start();
```

Cách này tách rõ:

```text
Runnable = task
Thread   = execution
```

Đây là mental model quan trọng để sau này hiểu Executor.

### Callable + FutureTask + Thread

`Callable` không thể truyền trực tiếp vào constructor `Thread` vì `Thread` nhận `Runnable`.

`FutureTask` có thể bọc `Callable` và đồng thời implements `Runnable`:

```java
Callable<String> task = () -> "Kết quả";
FutureTask<String> futureTask = new FutureTask<>(task);

Thread worker = new Thread(futureTask, "callable-worker");
worker.start();

String result = futureTask.get();
```

Điểm cần hiểu:

```text
Callable
    ↓ được bọc bởi
FutureTask
    ↓ được chạy bởi
Thread
```

### Demo trong module

Tham khảo controller:

```text
CreateThreadController#createByExtends()
CreateThreadController#createByRunnable()
CreateThreadController#createByCallable()
```

Các endpoint:

```text
GET /create/extends
GET /create/runnable
GET /create/callable
```

Khi chạy từng endpoint, hãy quan sát tên worker trong response:

```text
/extends
→ extends-thread-worker

/runnable
→ runnable-thread-worker

/callable
→ callable-thread-worker
```

`/extends` và `/runnable` đều tạo execution bằng `Thread`, nhưng cách tổ chức task khác nhau. `/callable` cho thấy `Callable` không tự chạy: nó được `FutureTask` bọc lại rồi mới được một `Thread` thực thi.

Mỗi demo đều `join()` hoặc chờ kết quả trước khi kết thúc experiment, nên không để lại worker thread ngoài ý muốn.

**Kết luận:** phần này học cách nối **task** với **execution mechanism**, không phải học ba API tương đương nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-state">5. Thread lifecycle và Thread.State</a>

<details>
<summary>Click for details</summary>

Java định nghĩa sáu trạng thái trong `Thread.State`:

```text
NEW
RUNNABLE
BLOCKED
WAITING
TIMED_WAITING
TERMINATED
```

Không có enum state tên là `RUNNING`.

Một thread đang thực sự dùng CPU vẫn được JVM biểu diễn bằng `RUNNABLE`.

### NEW

Thread object đã được tạo nhưng chưa `start()`:

```java
Thread worker = new Thread(task);
System.out.println(worker.getState()); // NEW
```

### RUNNABLE

Thread đã được start và đang ở trạng thái có thể thực thi hoặc đang thực thi.

Java không tách riêng "ready" và "running" thành hai giá trị `Thread.State`.

### BLOCKED

Thread đang chờ lấy intrinsic monitor để đi vào một block hoặc method `synchronized`.

Ví dụ:

```text
Thread A giữ monitor X
        ↓
Thread B cố synchronized(X)
        ↓
Thread B = BLOCKED
```

`BLOCKED` có ý nghĩa rất cụ thể: chờ monitor lock của `synchronized`.

Không nên gọi mọi trường hợp "đang chờ" là `BLOCKED`.

### WAITING

Thread đang chờ vô thời hạn cho đến khi một điều kiện hoặc sự kiện khác làm nó có thể tiếp tục.

Một số API có thể đưa thread vào `WAITING`:

- `Object.wait()` không timeout;
- `Thread.join()` không timeout;
- `LockSupport.park()`.

Ví dụ với `wait()`:

```java
synchronized (monitor) {
    monitor.wait();
}
```

Thread đang gọi `wait()` phải được đánh thức bằng `notify()`, `notifyAll()`, interrupt hoặc một cơ chế phù hợp với API đang dùng.

Riêng `join()` có semantics khác: thread đang gọi `join()` chờ thread mục tiêu kết thúc.

### TIMED_WAITING

Thread đang chờ với một giới hạn thời gian.

Ví dụ:

- `Thread.sleep(...)`;
- `Object.wait(timeout)`;
- `Thread.join(timeout)`;
- `LockSupport.parkNanos(...)`.

### TERMINATED

Method `run()` đã hoàn thành hoặc kết thúc bởi exception không được xử lý.

Thread object vẫn tồn tại như một object Java, nhưng execution của thread đó đã kết thúc và không thể `start()` lại.

### Sơ đồ mental model

```text
new Thread(...)
      ↓
     NEW
      ↓ start()
   RUNNABLE
      ↓
 ┌────┼───────────────┐
 ↓    ↓               ↓
BLOCKED          WAITING / TIMED_WAITING
 └────┴──────┬────────┘
             ↓
          RUNNABLE
             ↓ run() kết thúc
         TERMINATED
```

Đây là mô hình khái niệm. Một thread có thể chuyển giữa `RUNNABLE` và các trạng thái chờ nhiều lần trong suốt vòng đời.

### Demo trong module

Tham khảo controller:

```text
BasicThreadController#threadLifeCycle()
```

Endpoint:

```text
GET /basic/thread-life-cycle
```

Demo mới chủ động điều khiển một worker đi qua lần lượt:

```text
NEW
→ RUNNABLE
→ TIMED_WAITING
→ BLOCKED
→ WAITING
→ TERMINATED
```

Service sử dụng các tín hiệu nội bộ để quan sát state thay vì chỉ dựa vào những khoảng `sleep()` ngẫu nhiên của request thread. Riêng `TIMED_WAITING` được tạo bằng một latch có timeout dài; caller quan sát state rồi chủ động release latch để worker tiếp tục sang bước `BLOCKED`. Vì vậy demo không phụ thuộc vào việc scheduler phải "bắt kịp" một cửa sổ sleep ngắn.

Khi chạy endpoint, response nên thể hiện đủ chuỗi:

```text
NEW -> NEW
RUNNABLE -> RUNNABLE
TIMED_WAITING -> TIMED_WAITING
BLOCKED -> BLOCKED
WAITING -> WAITING
TERMINATED -> TERMINATED
```

Điểm cần quan sát là mỗi state xuất hiện vì **một nguyên nhân cụ thể**:

- `TIMED_WAITING` do chờ có timeout;
- `BLOCKED` do chờ intrinsic monitor của `synchronized`;
- `WAITING` do `wait()` không timeout;
- `TERMINATED` sau khi `run()` kết thúc.

**Kết luận:** không nên gom mọi trạng thái "đang chờ" thành một khái niệm chung. `Thread.State` mô tả các loại chờ khác nhau với semantics khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="daemon-thread">6. Daemon Thread và JVM lifecycle</a>

<details>
<summary>Click for details</summary>

Java thread có thuộc tính daemon.

Điểm quan trọng nhất không phải daemon "chạy nền", mà là cách JVM quyết định thời điểm kết thúc.

### User thread và daemon thread

JVM tiếp tục tồn tại khi vẫn còn user thread còn sống.

Khi không còn user thread nào sống, JVM có thể kết thúc dù daemon thread vẫn chưa hoàn thành.

```text
User Thread còn sống
    → JVM tiếp tục chạy

Chỉ còn Daemon Thread
    → JVM không bắt buộc phải chờ daemon hoàn thành
```

Vì vậy daemon thread không phù hợp cho công việc bắt buộc phải hoàn tất trước khi process kết thúc, ví dụ ghi dữ liệu quan trọng mà không có cơ chế đảm bảo khác.

Daemon cũng **không đồng nghĩa priority thấp**. `daemon` là property liên quan JVM liveness/lifecycle; thread scheduling priority là khái niệm khác. Không suy ra một daemon thread sẽ tự động được CPU ưu tiên thấp hơn chỉ vì `isDaemon() == true`.

### Thread mới kế thừa daemon status

Khi tạo một Thread mới, daemon status mặc định được kế thừa từ thread tạo ra nó.

Có thể thay đổi trước khi start:

```java
Thread worker = new Thread(task);
worker.setDaemon(true);
worker.start();
```

Sau khi Thread đã được start, không thể thay đổi daemon flag bằng `setDaemon(...)`.

### Vì sao không dùng HTTP endpoint để chứng minh JVM exit?

Trong Spring Boot, kết thúc một HTTP request không đồng nghĩa JVM kết thúc.

Server vẫn có nhiều user thread khác đang sống.

Do đó kiểu demo:

```text
request kết thúc
→ kết luận daemon thread phải chết
```

là sai.

Controller chỉ phù hợp để quan sát daemon property, không phù hợp để chứng minh quy tắc shutdown của JVM.

### Demo trong Spring Boot

Tham khảo controller:

```text
DaemonThreadController#inspectDaemonRules()
```

Endpoint:

```text
GET /daemon/inspect
```

Demo cho thấy:

- daemon status của HTTP request thread;
- daemon status mà child thread kế thừa mặc định;
- giá trị sau khi chủ động gọi `setDaemon(true)` trước `start()`.

Khi chạy endpoint này, không kết luận gì về thời điểm JVM shutdown. Endpoint chỉ dùng để quan sát **daemon flag và inheritance rule**.

### Demo JVM lifecycle độc lập

File:

```text
src/main/java/com/example/learning/module/basic/thread/DaemonJvmExitDemo.java
```

Class này có `main()` riêng và phải chạy như một Java application độc lập.

Chạy với:

```text
true  → worker là daemon thread
false → worker là user thread
```

Khi `true`, `main` kết thúc trước worker và JVM không chờ worker hoàn thành.

Khi `false`, worker là user thread nên JVM tiếp tục sống cho đến khi worker hoàn thành.

**Kết luận:** HTTP demo giúp quan sát daemon property; `DaemonJvmExitDemo.main()` mới là experiment phù hợp để chứng minh JVM exit semantics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="basic-endpoints">7. Các experiment của phần Basic</a>

<details>
<summary>Click for details</summary>

| README section | Controller method / Demo | Endpoint | Mục đích |
| --- | --- | --- | --- |
| `#process-and-thread` | `BasicThreadController#processAndThread()` | `GET /basic/process-thread` | Quan sát JVM process và HTTP request thread |
| `#start-vs-run` | `BasicThreadController#startVsRun()` | `GET /basic/start-vs-run` | Chứng minh `run()` không tạo thread mới còn `start()` thì có |
| `#thread-state` | `BasicThreadController#threadLifeCycle()` | `GET /basic/thread-life-cycle` | Quan sát sáu giá trị `Thread.State` |
| `#create-thread` | `CreateThreadController#createByExtends()` | `GET /create/extends` | Tạo Thread bằng subclass của `Thread` |
| `#create-thread` | `CreateThreadController#createByRunnable()` | `GET /create/runnable` | Tách task `Runnable` khỏi `Thread` |
| `#create-thread` | `CreateThreadController#createByCallable()` | `GET /create/callable` | Chạy `Callable` bằng `FutureTask` và một Thread trực tiếp |
| `#daemon-thread` | `DaemonThreadController#inspectDaemonRules()` | `GET /daemon/inspect` | Quan sát daemon flag và inheritance |
| `#daemon-thread` | `DaemonJvmExitDemo.main()` | Java application độc lập | Chứng minh JVM không đợi daemon thread |

Sau phần này, cần tự trả lời được các câu hỏi:

1. `Thread`, `Runnable` và `Callable` khác vai trò ở đâu?
2. Vì sao `thread.run()` không tương đương `thread.start()`?
3. `Thread.State.BLOCKED` khác `WAITING` như thế nào?
4. Vì sao một Thread đã `TERMINATED` không thể restart?
5. Vì sao kết thúc HTTP request trong Spring Boot không chứng minh được daemon thread sẽ chết?
6. Vì sao Executor chưa nên được coi đơn giản là "một cách tạo Thread"?

</details>

- [Quay lại đầu trang](#back-to-top)
