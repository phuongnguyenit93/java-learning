<a id="back-to-top"></a>

# Executor Framework

## Menu
- [Executor, ExecutorService và ThreadPoolExecutor](#executor-framework-001)
- [execute() so với submit() và xử lý lỗi](#executor-framework-002)
- [Fixed, cached và single-thread executors](#executor-framework-003)
- [Đánh giá rủi ro của queue không giới hạn](#executor-framework-004)
- [corePoolSize, maximumPoolSize và keepAliveTime](#executor-framework-005)
- [Work queue: ArrayBlockingQueue, LinkedBlockingQueue](#executor-framework-006)
- [RejectedExecutionHandler và bốn chính sách từ chối](#executor-framework-007)
- [ThreadFactory và UncaughtExceptionHandler trong executor](#executor-framework-008)
- [Lựa chọn pool size cho CPU-bound và I/O-bound](#executor-framework-009)
- [submit task độc lập và tránh thread starvation](#executor-framework-010)
- [Khả năng quan sát active count, queue size, completed tasks](#executor-framework-011)
- [Lifecycle: đóng executor và tránh thread leak](#executor-framework-012)
- [Kiểm soát số task đang chờ và backpressure](#executor-framework-013)
- [Phân biệt user task concurrency với thread pool concurrency](#executor-framework-014)
- [Thứ tự tiếp nhận việc của ThreadPoolExecutor: core threads, queue, maximum và rejection](#executor-framework-015)
- [AbortPolicy, CallerRunsPolicy, DiscardPolicy và DiscardOldestPolicy: hệ quả khi quá tải](#executor-framework-016)
- [prestartAllCoreThreads() và allowCoreThreadTimeOut(): quản lý luồng lõi](#executor-framework-017)
- [beforeExecute(), afterExecute() và cách nhận biết ngoại lệ của tác vụ submit()](#executor-framework-018)

## <a id="executor-framework-001">Executor, ExecutorService và ThreadPoolExecutor</a>

<details>
<summary>Xem chi tiết</summary>

# Executor, ExecutorService và ThreadPoolExecutor trong Java Concurrency

> **Phạm vi:** Executor Framework của Java SE, giải thích ba cấp abstraction `Executor`, `ExecutorService` và `ThreadPoolExecutor`; cách giao task, lấy kết quả, quản lý vòng đời và cấu hình thread pool, kiểm soát queue/rejection, quan hệ Java Memory Model và những tình huống dễ gây deadlock. Bài tập trung vào **Java concurrency nói chung**, không sử dụng cơ chế dành riêng cho Spring.

## 1. Tại sao Java có Executor Framework?

Cách cơ bản để thực thi một công việc đồng thời là tạo thread:

```java
Thread thread = new Thread(() -> doWork());
thread.start();
thread.join();
```

Đây là **đoạn code minh họa trong một method**: `doWork()` phải được khai báo và method cần khai báo/xử lý `InterruptedException` từ `join()`.

Đối với vài tác vụ, cách này đơn giản. Nhưng khi ứng dụng liên tục nhận hàng nghìn tác vụ, việc tạo một platform thread cho mỗi tác vụ có thể làm tăng overhead, số lượng stack/thread của OS, contention và khó kiểm soát shutdown. Nếu nhiều tác vụ cần dùng một số lượng worker hữu hạn, application còn cần:

- **Nơi nhận task** thay vì trực tiếp quản lý từng thread.
- **Chính sách thực thi:** chạy ngay trong caller, tạo worker, đưa vào queue, giới hạn concurrency.
- **Kết quả/ngoại lệ:** nhận được giá trị của `Callable` và biết task thành công hay thất bại.
- **Quản lý vòng đời:** ngừng nhận task mới, đợi task cũ hoàn tất, hủy khi cần.
- **Backpressure/rejection:** nếu worker và queue đều bận thì xử lý task mới thế nào?
- **Quan sát/tuning:** queue length, active threads, completed tasks, latency và failures.

Executor Framework **tách “cái gì cần làm” khỏi “task được chạy ở đâu và bằng cách nào”**. Thay vì `new Thread` khắp ứng dụng, caller mô tả công việc thành `Runnable` hoặc `Callable<T>` rồi giao cho một executor.

**Bảo đảm quan trọng:** một executor **không nhất thiết chạy task trên thread mới**. `Executor` chỉ quy định một giao diện thực thi. Implementation có thể chạy đồng bộ trong caller, trên một worker dùng lại, trên pool, hoặc bằng chiến lược khác. Đừng mặc định gọi `execute()` đồng nghĩa bắt đầu thread mới.

## 2. Ba cấp API cần phân biệt

```text
Executor
   |
   +-- execute(Runnable)
   |
   +-- ExecutorService extends Executor
          |
          +-- submit(...), invokeAll(...), invokeAny(...)
          +-- shutdown(), shutdownNow(), awaitTermination(...)
          +-- Future<T> cho kết quả/hủy
          |
          +-- ThreadPoolExecutor implements ExecutorService
                 |
                 +-- corePoolSize, maximumPoolSize, keepAliveTime
                 +-- BlockingQueue<Runnable>, ThreadFactory
                 +-- RejectedExecutionHandler, metrics/hook
```

### 2.1. `Executor`: “hãy thực thi Runnable này”

`Executor` là functional interface với một phương thức:

```java
void execute(Runnable command);
```

Đây là **chữ ký API minh họa**, không phải method cần tự cài cho mọi ứng dụng. Một executor thực tế quyết định task chạy khi nào/ở đâu.

Ví dụ **direct executor** không tạo worker:

```java
import java.util.concurrent.Executor;

Executor direct = Runnable::run;
direct.execute(() -> System.out.println("Executed by caller"));
```

Đây là đoạn code đặt trong method (có import). `Runnable::run` gọi `run()` trực tiếp ở thread gọi `execute()`, nên **không có bất đồng bộ**. Ví dụ cho thấy không nên suy từ tên `Executor` rằng nó luôn có thread pool.

`Executor.execute()` nhận `Runnable` và không trả `Future`. Nếu muốn nhận một kết quả/ngoại lệ dưới dạng đối tượng có thể đợi, dùng `ExecutorService.submit()` hoặc tự kết hợp một cơ chế completion.

### 2.2. `ExecutorService`: giao task, lấy kết quả, quản lý lifecycle

`ExecutorService` **mở rộng** `Executor`, thêm những phương thức cốt lõi:

- `submit(Runnable)` trả `Future<?>`; với task hoàn tất bình thường, `get()` trả `null`.
- `submit(Callable<T>)` trả `Future<T>`; `get()` trả kết quả kiểu T hoặc ném `ExecutionException` nếu task thất bại.
- `submit(Runnable, T result)` trả Future với giá trị result khi runnable hoàn tất thành công.
- `invokeAll(Collection<? extends Callable<T>>)` nhận nhiều task và trả danh sách Future theo thứ tự **đầu vào**, không phải theo thứ tự task hoàn thành.
- `invokeAny(...)` trả một kết quả thành công từ tập task, không cam kết task nào thắng; các task còn lại có thể bị hủy theo contract.
- `shutdown()` ngừng nhận task mới và tiếp tục xử lý những task đã giao.
- `shutdownNow()` cố gắng dừng task đang chạy và trả danh sách task đã được xếp hàng nhưng chưa khởi chạy.
- `awaitTermination(timeout, unit)` đợi pool đi tới trạng thái terminated hoặc hết thời hạn/interrupt.
- `isShutdown()` và `isTerminated()` giúp quan sát lifecycle.

`ExecutorService` là một **interface**. Không thể kết luận core threads, queue hay rejection policy của mọi instance chỉ từ kiểu `ExecutorService`: cách thực thi thực tế phụ thuộc implementation.

### 2.3. `ThreadPoolExecutor`: implementation có cấu hình cụ thể

`ThreadPoolExecutor` là một class trong `java.util.concurrent` cho phép điều khiển:

1. Số worker lõi: `corePoolSize`.
2. Số worker tối đa: `maximumPoolSize`.
3. Thời gian worker vượt core được giữ khi idle: `keepAliveTime`.
4. Queue chứa task chưa chạy: `BlockingQueue<Runnable>`.
5. Cách tạo worker: `ThreadFactory`.
6. Cách xử lý task không thể nhận: `RejectedExecutionHandler`.

Constructor đại diện:

```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    2, 4,
    30L, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(100),
    Executors.defaultThreadFactory(),
    new ThreadPoolExecutor.AbortPolicy()
);
```

Đây là đoạn code đặt trong method; cần import `ThreadPoolExecutor`, `TimeUnit`, `ArrayBlockingQueue` và `Executors`. Khi không còn dùng pool, phải thực hiện lifecycle shutdown; trong ví dụ này chưa có code đó vì chỉ minh họa constructor.

**Đừng hiểu `maximumPoolSize = 4` nghĩa là cứ có 4 task thì pool tạo 4 threads.** Sự tương tác giữa `corePoolSize` và loại queue quyết định lúc nào pool tạo worker mới.

## 3. `Runnable`, `Callable` và `Future`

### Runnable: không có giá trị trả về được khai báo

```java
Runnable task = () -> processEvent();
executor.execute(task);
```

Đây là minh họa với `processEvent()`/`executor` do caller cung cấp. `Runnable.run()` có chữ ký `void run()` và không khai báo checked exception. Nếu task thất bại, đường truyền exception phụ thuộc cách giao task và implementation, không mặc định có `Future.get()` để truy xuất.

### Callable: trả kết quả và có thể ném Exception

```java
Callable<Integer> task = () -> 21 * 2;
Future<Integer> result = service.submit(task);
Integer value = result.get();
```

Đây là đoạn code trong method có `service` là `ExecutorService`, cần import `java.util.concurrent.Callable` và `Future`; method phải xử lý/khai báo `InterruptedException`/`ExecutionException`.

`Callable<V>.call()` trả V và khai báo `throws Exception`. `ExecutorService.submit` đóng gói việc hoàn tất vào `Future`.

### `Future.get()` không phải thao tác “thăm dò không chờ”

- `get()` có thể **block** tới khi task hoàn tất.
- `get(timeout, unit)` có thể ném `TimeoutException`; **timeout ở `get` không tự hủy task**.
- Nếu task ném exception, `get()` ném `ExecutionException`, với `getCause()` chứa nguyên nhân.
- Nếu Future đã bị cancel, `get()` ném `CancellationException`.
- Nếu thread gọi `get` bị interrupt trong khi chờ, `get` ném `InterruptedException`.
- `isDone()` true khi task hoàn tất **bình thường, thất bại hoặc bị hủy**; không phải bằng chứng thành công.
- `isCancelled()` cho biết trạng thái hủy; `cancel(true)` yêu cầu interrupt nếu task đang chạy, **không cam kết code bỏ qua interrupt sẽ ngừng ngay**.

**Điểm quan trọng:** nếu chỉ gọi `submit` mà không quan sát Future hoặc kênh báo lỗi khác, exception trong task có thể trở thành lỗi “im lặng” ở tầng gọi. Với task quan trọng, phải kiểm tra/ghi nhận failure theo một giao thức rõ.

## 4. Ví dụ Java 17 chạy được: cả ba cấp API

Chương trình minh họa ba hành vi tách biệt và **không phụ thuộc vào may rủi của scheduler**:

1. `Executor` chạy task trực tiếp, xác nhận đã gọi Runnable.
2. `ExecutorService` nhận `Callable` và trả 42 qua `Future.get()`.
3. `ThreadPoolExecutor` có **một worker và queue capacity = 1**. Ta dùng latch để giữ worker trong task thứ nhất, xếp task thứ hai vào queue rồi nộp task thứ ba; với `AbortPolicy` task thứ ba **bị từ chối**. Sau đó thả latch, đọc hai kết quả, shutdown và đợi pool terminated.

Tạo file **`ExecutorFrameworkDemo.java`**:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExecutorFrameworkDemo {
    public static void main(String[] args) throws Exception {
        AtomicBoolean ran = new AtomicBoolean(false);
        Executor direct = Runnable::run;
        direct.execute(() -> ran.set(true));
        System.out.println("executorRan=" + ran.get());

        ExecutorService service = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> answer = service.submit(() -> 6 * 7);
            System.out.println("submitResult=" + answer.get());
        } finally {
            service.shutdown();
        }

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> first = pool.submit(() -> {
                started.countDown();
                release.await();
                return "first";
            });
            started.await();
            Future<String> second = pool.submit(() -> "second");

            boolean rejected = false;
            try {
                pool.execute(() -> { });
            } catch (RejectedExecutionException expected) {
                rejected = true;
            }
            System.out.println("rejectedWhenSaturated=" + rejected);

            release.countDown();
            System.out.println("taskResults=" + first.get() + "," + second.get());
        } finally {
            release.countDown();
            pool.shutdown();
        }
        System.out.println("poolTerminated="
                + pool.awaitTermination(5, TimeUnit.SECONDS));
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac --release 17 ExecutorFrameworkDemo.java
java ExecutorFrameworkDemo
```

**Stdout đã kiểm chứng:**

```text
executorRan=true
submitResult=42
rejectedWhenSaturated=true
taskResults=first,second
poolTerminated=true
```

### Phân tích từng dòng

**`executorRan=true`:** `Runnable::run` chạy trực tiếp nên giá trị AtomicBoolean đã đổi trước khi `execute()` trở về. Không có một platform thread mới nào được tạo chỉ vì gọi `Executor.execute()` trong trường hợp này.

**`submitResult=42`:** `Executors.newSingleThreadExecutor()` cung cấp `ExecutorService` với một worker thực thi các task tuần tự. `submit(Callable<Integer>)` trả Future; `get()` chờ phép tính hoàn thành và lấy kết quả.

**`rejectedWhenSaturated=true`:**

- `ThreadPoolExecutor(1,1,..., new ArrayBlockingQueue<>(1), AbortPolicy)` chỉ có tối đa **một active worker**.
- Task thứ nhất được worker chạy và báo `started.countDown()` rồi **chờ `release.await()`**, nên worker không rảnh.
- `started.await()` ở main xác nhận task thứ nhất đang ở checkpoint trước khi nộp task tiếp theo.
- Task thứ hai không thể nhận thêm worker (max = 1), vì vậy được enqueue vào **slot duy nhất**.
- Task thứ ba đến khi worker đã bận và queue đầy. `AbortPolicy` ném `RejectedExecutionException`, code catch và in true.

**`taskResults=first,second`:** sau `release.countDown()`, worker tiếp tục task thứ nhất, rồi chạy task thứ hai từ queue. Đọc `Future.get()` bảo đảm cả hai hoàn thành; biểu thức kết quả được kết hợp theo thứ tự main gọi get, không phải vì scheduler hứa thứ tự gọi `submit` luôn phản ánh thứ tự completion của các thread trong mọi loại pool.

**`poolTerminated=true`:** sau khi đã lấy hai Future và gọi `pool.shutdown()`, `awaitTermination` thành công trong lần chạy kiểm chứng. `awaitTermination` có timeout 5 giây nên trong môi trường bị kẹt bất thường nó có thể trả false; chương trình in đúng trạng thái được quan sát, không suy từ timeout rằng mọi thread chắc chắn đã dừng.

### Giới hạn mẫu thực hành

- Latch chỉ kiểm soát **lịch thử nghiệm**, không phải mô hình xử lý công việc production.
- Code dùng `shutdown()` sau các task hoàn tất; production nên có cancellation/timeout hoặc shutdown policy khi Future không bao giờ kết thúc.
- Lệnh `release.countDown()` trong `finally` tránh để worker tiếp tục kẹt ở latch nếu main gặp exception giữa chừng.
- Các class executor chuẩn Java SE không tự mang lại transaction cho database, external API hoặc file system.
- Với task thất bại trong `submit`, có thể lấy nguyên nhân từ `ExecutionException.getCause()`. `Future.get` là ranh giới completion/visibility, không phải bảo đảm “mọi side effect bên ngoài JVM đã commit”.

## 5. ThreadPoolExecutor xử lý task mới như thế nào?

Với một `ThreadPoolExecutor` đang ở trạng thái chạy và cấu hình thông thường, nguyên lý admission chính:

1. Nếu số worker đang có **nhỏ hơn `corePoolSize`**, ưu tiên tạo worker để chạy task mới.
2. Nếu đã đạt core, thử **đưa task vào work queue**.
3. Nếu queue không nhận được task (thường vì đã đầy), thử **tạo thêm worker** nếu tổng số worker còn dưới `maximumPoolSize`.
4. Nếu không thể enqueue hoặc tạo thêm worker, executor **reject** task theo `RejectedExecutionHandler`.

```text
                  execute(task)
                       |
             workerCount < core?
              /               \
            yes               no
             |                 |
        tạo worker       offer vào workQueue
                              /      \
                        thành công    thất bại
                            |            |
                         xếp hàng   workerCount < max?
                                      /       \
                                     yes      no
                                      |        |
                                  tạo worker  REJECT
```

Sơ đồ giản lược này giải thích chiến lược phổ biến; bên trong class còn có kiểm tra trạng thái pool và xử lý race khi shutdown. **Task có thể bị reject khi pool đang shutdown**, ngay cả khi queue chưa đầy.

### Ví dụ `core=2`, `max=4`, queue capacity 3

- Task 1 và 2 có thể tạo hai core workers.
- Khi hai core workers đang bận, task 3–5 có thể nằm trong queue.
- Task 6–7 đến khi queue đầy có thể kích hoạt worker thứ 3 và thứ 4.
- Task 8 đến khi 4 worker đều bận và queue vẫn đầy → rejection theo policy.

Đây là một **kịch bản minh họa khi các task đầu chưa kết thúc**. Nếu task hoàn tất nhanh, thread pool có thể tái sử dụng worker và queue không thực sự đầy; đừng coi số thứ tự task như lịch cố định của mọi execution.

### Những cấu hình làm `maximumPoolSize` ít tác dụng

Nếu work queue là `LinkedBlockingQueue` **gần như không giới hạn**, task có thể liên tục được enqueue sau khi đạt core, nên pool thường **không tăng worker vượt core** chỉ vì queue còn rất nhiều task. Lúc đó `maximumPoolSize` có thể không phát huy chức năng mở rộng như người cấu hình tưởng.

Nếu dùng `SynchronousQueue`, nó không lưu item nội bộ: producer giao task trực tiếp cho worker nhận. Khi không có worker rảnh và số worker chưa đạt max, pool có thể tăng worker mạnh hơn; cấu hình max quá lớn có nguy cơ tạo rất nhiều threads dưới burst.

**Chọn queue có chủ đích**, vì nó ảnh hưởng không chỉ memory/backpressure mà còn ảnh hưởng trực tiếp đến cách thread pool tăng số worker.

## 6. Các tham số chính và đánh đổi

| Tham số | Ý nghĩa | Lỗi hiểu nhầm hay gặp |
|---|---|---|
| `corePoolSize` | Số worker mục tiêu được duy trì theo chính sách; worker có thể được tạo khi task đến, không nhất thiết được tạo hết từ constructor | “Core threads luôn được prestart ngay” |
| `maximumPoolSize` | Trần số worker trong executor (tùy điều kiện queue và lifecycle) | “Pool sẽ luôn tăng tới max khi task đến nhiều” |
| `keepAliveTime` | Idle timeout với worker vượt core theo mặc định | “Tất cả core threads tự dừng sau keepAlive” |
| `workQueue` | Lưu task đang chờ worker | “Queue thread-safe nghĩa là backlog không thể tăng vô hạn” |
| `ThreadFactory` | Cách tạo thread: tên, daemon flag, ưu tiên, `UncaughtExceptionHandler`, v.v. | “Đặt daemon=true là một shutdown strategy đúng” |
| `RejectedExecutionHandler` | Chính sách khi task không thể được nhận | “Reject luôn ném exception” |
| `allowCoreThreadTimeOut(true)` | Cho phép core workers cũng timeout khi idle nếu điều kiện API được đáp ứng | “Có thể bật với keepAlive=0 một cách mặc nhiên” |

### Số worker và loại workload

**CPU-bound:** nếu task chủ yếu tính toán và không block, số platform workers gần số CPU thực sự khả dụng thường là một điểm bắt đầu để đo lường. Tăng quá nhiều workers tạo context switching, cache contention và giảm throughput.

**I/O-bound:** task đợi network/disk có thể có lợi khi số concurrent tasks lớn hơn số CPU. Nhưng cần kiểm tra giới hạn downstream: connection pool, API rate limit, file descriptors, bộ nhớ và latency. Số worker lớn không giải quyết được tài nguyên bên ngoài bị quá tải.

**Hỗn hợp:** tách các nhóm workload có đặc tính khác nhau hoặc dùng deadline/capacity riêng. Ví dụ task đọc file rất lâu không nên ngăn các tác vụ cập nhật UI/health-check quan trọng được cấp worker.

Đây là hướng dẫn **điểm bắt đầu để đo**, không phải công thức cố định; workload cần benchmark, profiling và thử nghiệm quá tải với SLA thực.

### `prestartCoreThread(s)`

`ThreadPoolExecutor` có `prestartCoreThread()` và `prestartAllCoreThreads()` để tạo worker lõi trước khi task được nhận, khi cần giảm cold-start latency. Không bắt buộc dùng; tạo nhiều threads rảnh sớm cũng tốn tài nguyên.

### `allowCoreThreadTimeOut`

Nếu muốn core workers cũng được thu hồi khi idle, `allowCoreThreadTimeOut(true)` cho phép cơ chế đó với cấu hình keep-alive hợp lệ. Khi task mới đến, worker có thể được tạo lại. Điều này đánh đổi tài nguyên lúc nhàn rỗi với thời gian khởi tạo lại worker.

## 7. Work queue và backpressure

`ThreadPoolExecutor` thường dùng một `BlockingQueue<Runnable>` để chứa task chưa được cấp worker. Đây là **queue task của executor**, không nhất thiết cùng một queue dữ liệu mà business logic tự dùng để chuyển message.

Một số lựa chọn:

- `ArrayBlockingQueue<>(capacity)`: capacity rõ, FIFO, tránh backlog vô hạn trong queue của pool.
- `LinkedBlockingQueue<>(capacity)`: bounded linked queue nếu truyền capacity cụ thể.
- `new LinkedBlockingQueue<>()`: mặc định capacity rất lớn (`Integer.MAX_VALUE`), **không phải giới hạn bộ nhớ thực dụng**.
- `SynchronousQueue<>()`: handoff trực tiếp, không giữ backlog trong queue, khiến admission phụ thuộc worker sẵn sàng hoặc việc tạo worker mới.
- `PriorityBlockingQueue`: thứ tự priority thay vì FIFO và không có hard bound capacity như `ArrayBlockingQueue`; không mặc định thích hợp để giới hạn tài nguyên.

**Capacity của work queue không đồng nghĩa tổng công việc trong hệ thống.** Tổng số task được executor nhận có thể gồm các task đang chạy và các task đang nằm trong queue. Ngoài ra còn có task đang ở upstream queues, HTTP buffers, message brokers hoặc sources khác.

### Chọn backpressure một cách rõ ràng

Nếu tất cả worker đang bận và work queue đầy, executor phải quyết định **không thể nhận thêm task**. Policy mặc định của `ThreadPoolExecutor` khi dùng `AbortPolicy` là ném `RejectedExecutionException`.

Caller nên xử lý rejection theo contract:

- Trả “quá tải” cho upstream.
- Retry với giới hạn, backoff và idempotency.
- Chuyển công việc sang tầng lưu trữ bền vững nếu không được phép mất.
- Giảm tốc upstream/rate limit.
- Dùng caller-runs có cân nhắc để truyền áp lực ngược vào thread gửi task.

**Không được im lặng bỏ qua rejection** nếu tác vụ là đơn hàng, thanh toán hoặc event bắt buộc xử lý. Mất task vì “đầy queue” là hành vi nghiệp vụ cần được khai báo và giám sát.

## 8. Rejection policies của ThreadPoolExecutor

| Handler | Hành vi tổng quát | Khi dùng phải lưu ý |
|---|---|---|
| `AbortPolicy` | Ném `RejectedExecutionException` | Caller phải catch/propagate/report |
| `CallerRunsPolicy` | Chạy task trong **thread gọi execute** nếu pool chưa shutdown | Làm caller chậm (backpressure), phá giả định “task luôn chạy ở worker”; khi pool shutdown thì task bị bỏ |
| `DiscardPolicy` | Im lặng bỏ task | Có thể mất dữ liệu; không phù hợp việc bắt buộc hoàn tất |
| `DiscardOldestPolicy` | Bỏ task ở đầu queue rồi thử submit lại task mới khi còn chạy | Có thể loại công việc quan trọng, tương tác không mong muốn với queue priority và concurrency |

### `CallerRunsPolicy` không phải lúc nào cũng là lựa chọn vô hại

Nếu task thực thi I/O nặng và caller là request thread, caller-runs khiến request latency tăng đột biến. Nếu task thực thi mã dựa trên identity/ThreadLocal của worker, việc task chạy trong caller có thể gây lỗi ngữ nghĩa. Với `submit()`, task thường được bọc thành một `FutureTask`: caller-runs có thể thực hiện ngay trong caller, làm lời gọi `submit` tốn thời gian trước khi trả Future.

**Đặc biệt:** khi executor đã shutdown, `CallerRunsPolicy` **không chạy** task được reject mà có thể bỏ qua. Vì thế nếu cần bảo đảm “task luôn được thực thi hoặc trả lỗi”, phải thiết kế rejection contract riêng, không mặc định caller-runs là an toàn tuyệt đối.

### `DiscardOldestPolicy` và FIFO queue

“Oldest” ở đây là task ở đầu queue theo hành vi `poll()` của queue, **không nhất thiết là task có timestamp nghiệp vụ cũ nhất**. Với priority queue, “head” còn có thể là task ưu tiên cao nhất—discarding nó có thể trái kỳ vọng.

Cả hai discard handlers có thể gây mất việc. Chỉ dùng khi product contract thực sự cho phép bỏ task và có metrics để phát hiện.

### Custom handler

`RejectedExecutionHandler.rejectedExecution(Runnable r, ThreadPoolExecutor executor)` cho phép đưa ra chính sách riêng. Một handler production có thể báo metric và ném exception có nội dung rõ, nhưng phải tránh **block vô hạn hoặc deadlock** trong cùng executor đang quá tải. Handler chạy trên đường gửi task và phải được audit về concurrency.

## 9. `execute` và `submit`: exception đi đâu?

Đây là một trong các khác biệt vận hành hay bị bỏ qua.

```java
// Minh họa trong method, executor là ThreadPoolExecutor:
executor.execute(() -> {
    throw new IllegalStateException("failed in execute");
});

Future<?> future = executor.submit(() -> {
    throw new IllegalStateException("failed in submit");
});
```

Đây là **đoạn minh họa** với imports phù hợp. Trong `submit`, executor bọc task qua `FutureTask` và lưu kết quả/exception vào Future; việc đọc `future.get()` ném `ExecutionException` mang cause `IllegalStateException`.

Với `execute`, unchecked exception có thể thoát khỏi method `run()` của worker và được xử lý theo cơ chế uncaught exception của thread/implementation; không có Future trả về cho caller để truy vấn nguyên nhân. Không được suy rằng chỉ nhìn `System.err` là một chiến lược error monitoring đầy đủ.

`ThreadPoolExecutor.afterExecute(Runnable r, Throwable t)` có thể được override để thu thập thông tin task completion, nhưng lưu ý: **khi task do `submit` bọc bằng `FutureTask`, exception thường nằm trong Future, và tham số `t` của hook có thể là null**. Nếu cần lấy failure, phải kiểm tra Future một cách cẩn thận (và tránh block vô hạn trong hook).

**Khuyến nghị vận hành:** dùng một cơ chế rõ ràng để **ghi nhận task thất bại**, bao gồm cả business exceptions và infrastructure exceptions, thay vì chỉ dựa vào logs mặc định của worker.

## 10. Vòng đời ExecutorService và shutdown an toàn

### Các trạng thái logic

Một executor có thể:

1. **Running:** nhận task mới.
2. **Shutdown:** không nhận task mới, nhưng có thể tiếp tục các task đã nhận.
3. **Terminated:** không còn task đang thực thi; executor đã kết thúc.
4. **Stop attempt:** `shutdownNow()` cố interrupt task đang chạy, không bảo đảm mọi task dừng tức thì.

`shutdown()` không đợi. `awaitTermination()` **chỉ đợi** và trả boolean; nó không tự gọi `shutdown()`. Nếu không gọi shutdown, một pool còn worker có thể làm JVM tiếp tục chạy (đặc biệt khi threads không phải daemon). Do đó resource ownership và lifecycle phải rõ ràng.

### Mẫu graceful shutdown

```java
static void stopGracefully(ExecutorService service)
        throws InterruptedException {
    service.shutdown(); // không nhận task mới
    try {
        if (!service.awaitTermination(10, TimeUnit.SECONDS)) {
            service.shutdownNow(); // yêu cầu interrupt task đang chạy
            if (!service.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("Executor did not terminate");
            }
        }
    } catch (InterruptedException e) {
        service.shutdownNow();
        Thread.currentThread().interrupt();
        throw e;
    }
}
```

Đây là **static method minh họa** đặt trong class có import `ExecutorService`, `TimeUnit`; ngưỡng 10 giây chỉ là ví dụ, cần chọn theo workload/SLA. Khi cần propagate `InterruptedException`, khôi phục interrupt flag trước khi rethrow có thể phù hợp với contract caller; nếu dừng theo phương án khác thì chính sách đó phải được định nghĩa rõ.

`shutdownNow()` thường sử dụng `Thread.interrupt()` như một tín hiệu. Nếu task bỏ qua interrupt, đang block ở thao tác không interruptible, hoặc giữ vòng lặp vô hạn, nó vẫn có thể tiếp tục. Không được tin rằng một lần shutdownNow là “kill task” tuyệt đối.

### Cancellation trong task

Task hợp tác với interrupt:

```java
Runnable task = () -> {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            performOneUnitOfWork();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        cleanupOwnedResources();
    }
};
```

Đây là **mã minh họa**: `performOneUnitOfWork()` có thể ném `InterruptedException`, `cleanupOwnedResources()` phải được định nghĩa. Task không nên catch `InterruptedException` rồi tiếp tục mãi và làm shutdown bị treo. Với `Future.cancel(true)`, người gọi yêu cầu interrupt; việc task dừng nhanh tới đâu còn tùy code và nơi đang block.

**Lưu ý:** `Future.get(timeout)` có timeout chỉ cho **caller đang đợi**; nó **không tự gọi `cancel(true)`**. Nếu muốn hủy sau deadline, caller phải thiết kế thao tác hủy và xử lý kết quả.

## 11. Deadlock thường gặp khi task đợi task khác cùng pool

Một executor có thể deadlock mà không có hai lock lồng nhau. Ví dụ một worker đang chạy task A, A submit task B vào **cùng single-thread executor**, rồi A gọi `Future.get()` để chờ B.

```java
// Minh họa sai: chạy đoạn này với pool chỉ có một worker.
ExecutorService pool = Executors.newSingleThreadExecutor();
pool.submit(() -> {
    Future<Integer> other = pool.submit(() -> 42);
    return other.get(); // A chờ B; B chưa có worker để chạy.
});
```

Đoạn code minh họa cần imports `java.util.concurrent`, và nếu được chạy nguyên vẹn sẽ có nguy cơ **treo**; không dùng nó làm chương trình thực hành chạy. Task A giữ worker duy nhất rồi đợi B; B đang xếp hàng và cần worker đó, tạo **thread starvation deadlock**.

Tăng pool size đôi khi tránh tình huống cụ thể nhưng không phải lời giải đủ nếu graph phụ thuộc có thể sâu hơn hoặc mọi worker cùng block. Giải pháp đúng có thể là:

- Không chờ Future của task phụ thuộc trên **cùng executor có số worker hữu hạn**.
- Tổ chức computation thành một task lớn hơn khi không cần concurrency.
- Dùng completion orchestration phù hợp, hoặc pool tách biệt với capacity được thiết kế.
- Hạn chế blocking call bên trong worker khi chúng cần chính pool đó để tiến triển.
- Đặt timeout/cancellation cho các điểm chờ và theo dõi active/queued tasks.

Một mẫu khác: N worker trong pool đồng loạt gọi `BlockingQueue.put` trên queue đang đầy, nhưng task consumer cần được chạy trên **chính pool đã hết worker**. Worker không nhả chỗ, consumer không được cấp CPU/worker → treo. Khi thiết kế pipeline, phải xét cả executor work queue lẫn business data queue.

## 12. Tác vụ dài hạn và một pool chung

Nếu trộn task rất ngắn với tác vụ block network nhiều phút trong một pool nhỏ, short tasks có thể bị starvation hoặc latency cao dù chúng chỉ cần ít CPU.

### Cách tách workload

- **CPU executor:** số worker vừa phải theo CPU khả dụng, tránh blocking I/O kéo dài.
- **I/O executor:** concurrency/queue có giới hạn phù hợp với connection pool và downstream capacity.
- **Scheduled executor:** nhiệm vụ delay/periodic dùng `ScheduledExecutorService`/`ScheduledThreadPoolExecutor` thay vì tự `Thread.sleep()` trong worker thường.
- **Single-thread owner:** các command cần thực thi tuần tự vì invariant có thể dùng `newSingleThreadExecutor()`, nhưng backlog và shutdown vẫn phải được kiểm soát.

Không phải lúc nào cũng cần nhiều pool; chia quá nhiều pools gây phức tạp lifecycle và dùng tài nguyên kém. Hãy tách khi SLA, loại chờ, khả năng scale hoặc owner của state thật sự khác nhau.

### `Executors` factory methods và rủi ro mặc định

| Factory | Hành vi nổi bật | Cần cảnh giác |
|---|---|---|
| `newSingleThreadExecutor()` | Một worker xử lý task tuần tự; worker được thay khi thất bại theo contract | Queue có thể phát triển lớn; không phải bounded admission |
| `newFixedThreadPool(n)` | Số worker cố định n, tái sử dụng | Work queue mặc định **không có hard bound nhỏ**, có thể backlog |
| `newCachedThreadPool()` | Tái sử dụng worker rảnh và tạo thêm theo nhu cầu | Có thể tạo rất nhiều platform threads dưới burst, phải cân nhắc giới hạn |
| `newScheduledThreadPool(n)` | Delay và periodic tasks | Chính sách queue/timer khác; task periodic lỗi có thể ngừng các lần chạy kế tiếp |
| `newWorkStealingPool()` | Dựa trên `ForkJoinPool`, work-stealing | Không bảo đảm thứ tự thực thi; không tương đương pool FIFO truyền thống |
| `newSingleThreadScheduledExecutor()` | Một worker cho lịch chạy tuần tự | Task dài làm những lần chạy sau bị trễ |

**Quan trọng:** `newFixedThreadPool(n)` chỉ giới hạn worker count, **không giới hạn số task chờ thực tế** theo một capacity nhỏ. Khi producer nhanh hơn consumer, work queue có thể tích tụ rất nhiều task và ăn RAM. Nếu cần bounded queue + rejection policy cụ thể, dùng `ThreadPoolExecutor` với constructor rõ ràng.

## 13. ThreadFactory và quan sát thread

`ThreadFactory` quyết định cách tạo worker mới. Có thể dùng để:

- Đặt tên thread dễ chẩn đoán như `image-worker-1`, `image-worker-2`.
- Đặt `UncaughtExceptionHandler` cho các lỗi uncaught (không thay thế đọc exception trong Future).
- Quyết định daemon/thread priority theo policy có kiểm chứng.
- Tạo worker thuộc context/class loader/thread group phù hợp với môi trường.

Ví dụ Java 17:

```java
AtomicInteger sequence = new AtomicInteger();
ThreadFactory factory = runnable -> {
    Thread t = new Thread(runnable,
            "image-worker-" + sequence.incrementAndGet());
    t.setDaemon(false);
    return t;
};
```

Đây là đoạn code đặt trong method với imports `java.util.concurrent.ThreadFactory` và `java.util.concurrent.atomic.AtomicInteger`.

**Không chọn daemon=true chỉ để JVM tự tắt** nếu có công việc quan trọng chưa hoàn tất. Daemon threads có thể bị dừng khi JVM kết thúc mà không bảo đảm flush dữ liệu/commit state. Resource owner vẫn phải shutdown và đợi completion theo contract.

ThreadFactory có thể từ chối tạo thread (ví dụ trả null theo một số tình huống); class pool xử lý việc tạo worker theo hành vi API. Với production, custom factory cần được kiểm thử khi hệ thống thiếu tài nguyên hoặc cấu hình không hợp lệ.

## 14. Memory visibility và Java Memory Model

Executor Framework không chỉ là hàng đợi task; các API concurrency của Java có quy tắc memory consistency để dữ liệu được công bố an toàn giữa threads.

Các quan hệ thường gặp:

1. Hành động trong thread trước khi **submit** một `Runnable/Callable` cho `ExecutorService` *happen-before* các hành động bên trong task đó.
2. Hành động bên trong task *happen-before* những hành động của thread khác **sau khi `Future.get()` lấy kết quả thành công** theo contract của API.
3. Với `Executor.execute`, tài liệu API mô tả memory consistency cho các hành động trước khi submit `Runnable` và bắt đầu thực thi nó.
4. Những quan hệ từ `Thread.start`, `join`, volatile, locks và queues vẫn áp dụng nếu chương trình có thêm cơ chế phối hợp.

Ví dụ:

```java
// Minh họa trong một method, có sẵn ExecutorService service:
int[] data = {10, 20};
Future<Integer> task = service.submit(() -> data[0] + data[1]);
System.out.println(task.get()); // 30 nếu không có mutation cạnh tranh
```

Đây là code minh họa, method cần khai báo/xử lý checked exceptions. Nếu main tạo `data` trước submit và **không sửa nó đồng thời**, worker có thể thấy dữ liệu đã chuẩn bị theo memory consistency contract; sau `get()` main nhận kết quả đúng.

**Nhưng:** submission không khiến mọi mutation sau submit tự an toàn. Nếu main đổi `data[0]` trong lúc worker đọc, vẫn phải có synchronization/ownership riêng. Task return không tự làm các object mutable mà nó trả về trở thành immutable.

### Future.get và visibility không sửa atomicity của work

Nếu hai task cùng làm `counter++` trên một `int` thường thiếu synchronization, main đợi cả hai bằng `Future.get()` **không cứu lost update**. `get()` chỉ là ranh giới completion/visibility giữa task và caller; cập nhật giữa hai workers đã có thể race trước đó.

Sửa bằng:

- `AtomicInteger.incrementAndGet()` cho counter độc lập.
- `synchronized`/`Lock` cho invariant đa bước.
- Task-local accumulators rồi merge sau `Future.get()`.
- Immutable message/ownership transfer khi muốn tránh shared mutable state.

## 15. Metrics: quan sát pool theo đúng semantics

`ThreadPoolExecutor` có API quan sát như:

```java
int core = pool.getCorePoolSize();
int max = pool.getMaximumPoolSize();
int current = pool.getPoolSize();
int active = pool.getActiveCount();
int queued = pool.getQueue().size();
long completed = pool.getCompletedTaskCount();
```

Đây là **đoạn minh họa trong method** với `pool` là `ThreadPoolExecutor`.

Các số liệu được dùng để theo dõi:

- **Active count:** số worker đang thực thi task vào thời điểm quan sát (một số count có tính ước lượng).
- **Pool size:** tổng số worker hiện có.
- **Queue depth:** số task còn chờ, không bao gồm task đang thực thi.
- **Completed tasks:** số task đã hoàn thành ở mức executor, **không có nghĩa business operation thành công**.
- **Rejection count:** thường cần tự theo dõi trong rejection handler vì không có “rejected task counter” tiêu chuẩn đầy đủ trên mọi executor.
- **Task wait time:** thời gian từ submit đến start (nếu instrument), khác execution time.
- **Task execution time, queue age và latency end-to-end:** giúp biết bottleneck nằm ở cấp nhận task, worker hay downstream.

Một số getter dùng snapshot/ước lượng, có thể thay đổi ngay sau khi đọc. **Không dùng `getQueue().size()` rồi quyết định chắc chắn task tiếp theo sẽ được nhận**, vì concurrent submissions/completions có thể thay đổi queue. Đừng gọi `getQueue().clear()` như một cách “điều tiết bình thường”: tác động đến các task đã được chấp nhận và có thể làm Future không bao giờ hoàn thành như caller kỳ vọng.

### Thread dump và JFR

Khi nghi pool đang nghẽn:

```bash
jcmd <PID> Thread.print -l
```

Thay `<PID>` bằng process ID thật. Xem worker đang chạy, chờ queue, blocked trên monitor hay đang đợi network/DB. `WAITING (parking)` của idle worker có thể là **bình thường**, không đồng nghĩa deadlock.

Với workload đủ dài, JFR giúp xem sự kiện thread park, contention và hot spots tùy JVM/recording settings. Quan sát thread state chỉ là một snapshot; phải kết hợp queue depth, task latency, retry/rejection và các SLA nghiệp vụ.

## 16. Những quyết định quan trọng trước khi tuning pool

Khi chuẩn bị một executor cho production, nên trả lời rõ:

1. **Task là gì?** CPU-bound, I/O-bound, long-running, periodic hay có external side effect?
2. **Tải đến như thế nào?** Trung bình, burst, peak concurrency và khả năng giảm tốc upstream?
3. **SLO latency/throughput nào phải giữ?** Thời gian chờ queue có bao gồm trong deadline không?
4. **Giới hạn bộ nhớ?** Một queued task giữ bao nhiêu reference/payload; capacity cần bao nhiêu?
5. **Downstream có capacity nào?** DB connections, HTTP pool, API quota, disk throughput?
6. **Task được phép reject/drop không?** Nếu không, cần cơ chế bền vững/retry/idempotency?
7. **Quy tắc hủy ra sao?** Deadline, interrupt, retry và rollback/compensation?
8. **Exception được quan sát ở đâu?** Future, CompletionService, logging/metrics, error channel?
9. **Ai sở hữu executor?** Ai được shutdown và lúc nào? Không để một service bất kỳ tắt pool dùng chung.
10. **Có phụ thuộc task-task?** Một task có gọi `Future.get` của task khác cùng pool không?
11. **Có mutable state chia sẻ?** Task submission không thay synchronization của chính state.
12. **Có metrics và bài test quá tải không?** Chỉ test với hai task nhẹ không cho biết behavior khi queue full.

Tuning có ý nghĩa khi đã xác định chính xác acceptance semantics và lifecycle. Tăng thread count để giảm queue depth có thể làm downstream quá tải và tăng lỗi; giảm threads để tiết kiệm CPU có thể gây starvation cho tác vụ phụ thuộc lẫn nhau.

## 17. Khi nào cần CompletionService, ScheduledExecutorService hoặc ForkJoinPool?

Ba loại API này liên quan Executor Framework, nhưng **không thay đổi ý nghĩa của ba cấp chính**:

### `ExecutorCompletionService<V>`

Nếu submit nhiều `Callable` và muốn lấy kết quả **theo thứ tự hoàn tất**, `ExecutorCompletionService` bọc một `Executor`/`ExecutorService`, cung cấp completion queue của Future. Điều này khác việc gọi `future1.get()` rồi `future2.get()` theo thứ tự đầu vào: một Future chậm ở đầu có thể làm caller đợi dù Future khác đã hoàn thành.

### `ScheduledExecutorService`

Dùng để chạy task sau delay hoặc định kỳ:

- `schedule`: chạy một lần sau delay.
- `scheduleAtFixedRate`: lịch dựa trên khoảng bắt đầu định kỳ.
- `scheduleWithFixedDelay`: chờ một khoảng sau khi lần chạy trước kết thúc.

Các periodic executions có thể bị trễ khi worker bận; không có bảo đảm đúng mốc thời gian thực tuyệt đối. Exception từ một lần periodic execution có thể khiến các lần chạy tiếp theo bị ngừng theo contract, cần giám sát Future/result.

### `ForkJoinPool`

Phù hợp với tác vụ chia nhỏ và work-stealing, đặc biệt khi bài toán có cấu trúc fork/join. Nó có queue/scheduling semantics riêng; không được suy rằng nó đảm bảo FIFO như một fixed thread pool dùng `LinkedBlockingQueue`. Blocking I/O kéo dài trong worker của fork/join có thể cần thiết kế và cơ chế quản lý blocking đặc biệt.

Bài này tập trung vào `Executor`, `ExecutorService` và `ThreadPoolExecutor`; các API trên là lựa chọn bổ trợ khi yêu cầu phù hợp.

## 18. Java 21+ virtual threads và sự khác biệt cần biết

Từ Java 21, virtual threads cho phép tạo nhiều thread Java nhẹ hơn platform threads trong nhiều workload blocking I/O. API `Executors.newVirtualThreadPerTaskExecutor()` tạo **một virtual thread mới cho mỗi task**, không phải một fixed pool của virtual threads theo `corePoolSize/maximumPoolSize`.

- Java 17 **chưa hỗ trợ ổn định virtual threads** như Java 21; ví dụ chính của bài dùng platform threads và chạy trên JDK 17.
- Không lấy tư duy “giới hạn platform thread pool” áp dụng máy móc cho virtual-thread-per-task executor. Muốn giới hạn số concurrent DB requests hoặc API calls, dùng Semaphore/rate limit/connection pool phù hợp.
- Virtual threads **không** loại bỏ data races, shared mutable state, timeout hay backpressure.
- Với các tác vụ CPU-bound, tạo thêm vô số virtual threads không giúp vượt số CPU vật lý; vẫn phải đo workload.

Phân biệt API theo **phiên bản JDK** trước khi sao chép code: `newVirtualThreadPerTaskExecutor()` không biên dịch với `--release 17`.

## 19. Lỗi phổ biến và cách sửa

1. **“Executor.execute luôn tạo thread mới.”** Sai: implementation có thể chạy đồng bộ trong caller như direct executor.
2. **“Executor và ExecutorService giống hệt nhau.”** Sai: `ExecutorService` có `submit`, `Future`, lifecycle/coordination APIs.
3. **“newFixedThreadPool(n) tự giới hạn mọi backlog ở n.”** Sai: n là số worker, queue có thể tăng rất lớn.
4. **“maximumPoolSize là số worker luôn tạo khi có nhiều task.”** Sai: pool thường ưu tiên queue sau khi đạt core; queue unbounded làm max ít được dùng.
5. **“shutdown chờ mọi task kết thúc.”** Sai: shutdown ngừng nhận task mới; đợi bằng Future/awaitTermination.
6. **“shutdownNow kill mọi task ngay lập tức.”** Sai: thường chỉ yêu cầu interrupt; task phải hợp tác.
7. **“Future.isDone nghĩa là task thành công.”** Sai: task có thể lỗi hoặc bị hủy.
8. **“future.get(timeout) tự động cancel nếu timeout.”** Sai: timeout của get không hủy task.
9. **“submit exception sẽ tự in stack trace như execute.”** Không thể dựa vào điều đó; task failure được giữ trong Future.
10. **“CallerRunsPolicy luôn ném lỗi khi từ chối.”** Sai: nó thường chạy trong caller nếu pool chưa shutdown; khi đã shutdown có thể bỏ task.
11. **“DiscardPolicy vẫn xử lý task theo cách khác.”** Sai: nó bỏ task; chỉ dùng khi được phép mất việc.
12. **“Đặt workQueue unbounded sẽ không hết bộ nhớ.”** Sai: backlog có thể tăng tới OOM dưới tải quá mức.
13. **“Các task đều độc lập vì chạy trong pool.”** Sai: shared mutable fields vẫn có thể race.
14. **“Future.get sau hai task sửa cùng counter bảo đảm đúng tổng.”** Sai: get hoàn tất/visibility, không sửa lost update giữa workers.
15. **“Thread pool một worker không thể deadlock.”** Sai: task đang chạy có thể đợi task khác chỉ có thể được chạy bởi worker duy nhất đó.
16. **“getQueue().size() == 0 nghĩa là mọi task hoàn thành.”** Sai: task có thể đang chạy.
17. **“Pool activeCount cao nghĩa là CPU luôn quá tải.”** Sai: workers có thể block I/O; cần CPU/stack/latency.
18. **“Đặt daemon=true đủ thay thế shutdown.”** Sai: công việc quan trọng có thể mất khi JVM thoát.
19. **“Tăng max threads luôn tăng throughput.”** Sai: downstream bottleneck, context switching và GC có thể xấu hơn.
20. **“ThreadFactory thay thế error handling.”** Sai: failures trong `submit` thường được lưu trong Future.
21. **“Mỗi method có thể tự shutdown executor được inject/shared.”** Sai về ownership: caller sở hữu lifecycle phải được định nghĩa để không làm hỏng users khác.
22. **“Có thread pool thì task tự có timeout.”** Không: deadline phải được thiết kế ở Future, I/O operations, cancellation và queue admission.

## 20. Câu hỏi ôn tập và đáp án

**Câu 1.** `Executor` có bắt buộc chạy task trên thread khác không?  
**Đáp án:** Không. Nó chỉ định giao diện `execute(Runnable)`; implementation có thể chạy trực tiếp trong caller.

**Câu 2.** Tại sao cần `ExecutorService` thay vì `Executor` nếu muốn nhận kết quả phép tính?  
**Đáp án:** `ExecutorService.submit(Callable<T>)` trả `Future<T>`, có thể `get()` để chờ kết quả và bắt exception/hủy.

**Câu 3.** `ExecutorService.shutdown()` có chờ task đang chạy xong không?  
**Đáp án:** Không. Nó ngừng nhận task mới; muốn đợi phải dùng Future/`awaitTermination`.

**Câu 4.** Pool core=2, max=8, queue rất lớn: tại sao có thể chỉ dùng 2 workers dù còn nhiều task?  
**Đáp án:** Khi đạt core, ThreadPoolExecutor ưu tiên enqueue trước khi tăng workers; queue lớn còn nhận task thì thường không cần tạo worker vượt core.

**Câu 5.** Pool core=max=1, queue capacity=1. Worker đang bận, queue chứa một task, task thứ ba đến: điều gì xảy ra với AbortPolicy?  
**Đáp án:** Task mới bị reject bằng `RejectedExecutionException`. Đây là tình huống trong ví dụ chính.

**Câu 6.** `CallerRunsPolicy` giúp backpressure thế nào?  
**Đáp án:** Khi pool còn hoạt động nhưng bão hòa, task có thể chạy ngay trong thread gọi `execute`, làm caller chậm và giảm tốc submit. Cần lưu ý thread-affinity/context và behavior khi shutdown.

**Câu 7.** `submit` task ném `IllegalStateException`, caller thấy gì ở `future.get()`?  
**Đáp án:** `ExecutionException` với cause là exception của task (trừ khi có cancellation/interrupt khác theo contract).

**Câu 8.** `Future.get(100, MILLISECONDS)` ném `TimeoutException` có chắc task đã ngừng không?  
**Đáp án:** Không. Timeout chỉ kết thúc lời gọi đợi; task có thể vẫn đang chạy.

**Câu 9.** Một task trong single-thread pool submit task thứ hai cùng pool rồi gọi `get`, tại sao nguy hiểm?  
**Đáp án:** Task đầu giữ worker duy nhất và đợi task hai; task hai đang queue, không được chạy nên có thể deadlock.

**Câu 10.** Có thể gọi `Future.get` sau hai task làm `counter++` không synchronization rồi suy tổng sẽ chính xác?  
**Đáp án:** Không. Future.get bảo đảm completion/visibility ở phía caller nhưng không làm phép cập nhật counter giữa workers atomic.

**Câu 11.** `getQueue().size() == 0` chứng minh executor đã xử lý xong tất cả task chưa?  
**Đáp án:** Không; các task đang chạy không nằm trong queue. Dùng Future, termination status và application-level completion.

**Câu 12.** Tại sao không nên dùng `newCachedThreadPool()` không kiểm soát cho nguồn có burst lớn?  
**Đáp án:** Nó có thể tạo nhiều platform threads khi không có worker nhàn rỗi; cần admission/rate limit theo tài nguyên.

**Câu 13.** Khi task có lỗi business, `getCompletedTaskCount()` có phân biệt thành công không?  
**Đáp án:** Không nên coi nó là success counter. Nó chỉ đo hoàn tất ở mức executor; cần business result/error metrics riêng.

**Câu 14.** Khi nhiều tasks độc lập CPU-bound, tăng workers gấp 10 lần số CPU có chắc nhanh hơn không?  
**Đáp án:** Không. Overhead scheduling, cache contention và resource bottleneck có thể giảm hiệu năng; đo thực tế với workload đại diện.

**Câu 15.** Để có bounded task backlog mà task bị reject rõ ràng khi quá tải, nên cấu hình gì?  
**Đáp án:** Một `ThreadPoolExecutor` với giới hạn core/max, work queue có capacity cụ thể và handler như `AbortPolicy`, đồng thời caller xử lý rejection.

## 21. Checklist áp dụng vào code thật

Trước khi đưa một executor vào ứng dụng, xác nhận:

- **Ownership:** component nào tạo pool, ai có quyền submit và ai phải shutdown?
- **Semantics:** task có cần thứ tự, kết quả, deadline, retry hoặc idempotency không?
- **Worker limits:** core/max có hợp lý với CPU và downstream resource không?
- **Queue capacity:** có hard bound thực dụng cho burst và ngân sách bộ nhớ không?
- **Rejection:** task bị reject sẽ báo lỗi, retry, lưu bền hay bỏ theo contract?
- **Completion:** caller có nhận exceptions từ `Future` hoặc kênh lỗi rõ ràng không?
- **Cancellation:** interrupt có được tôn trọng, I/O có timeout và task có cleanup không?
- **Deadlock:** task có chờ task khác trong cùng pool? Producer/consumer có bị kẹt do thiếu worker?
- **Visibility:** dữ liệu được chuẩn bị trước submit đúng, mutable state chia sẻ trong task có synchronization?
- **Monitoring:** queue age, active workers, completed/failures, rejections, latency và CPU có được thu thập?
- **Shutdown:** test khi service kết thúc với task đang chờ/đang chạy và khi nhận interrupt?
- **Version:** class/API mình dùng có sẵn trong JDK triển khai không (đặc biệt các APIs mới từ Java 21+)?

Khi câu trả lời chưa rõ, cấu hình đơn giản nhưng **có bound và báo lỗi rõ** thường dễ điều hành hơn một pool rất lớn không có rejection/timeout.

## 22. Tài liệu tham khảo chính thức

- [Java SE 17 — Executor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html): `execute` và memory-consistency properties.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): `submit`, `invokeAll`, `invokeAny`, lifecycle.
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): cấu hình worker, queue, handler, hooks và metrics.
- [Java SE 17 — Executors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html): các factory methods.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get`, `cancel`, exceptions và memory consistency.
- [Java SE 17 — Callable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Callable.html): hàm trả kết quả.
- [Java SE 17 — BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): queue, blocking, capacity và publication.
- [Java SE 17 — RejectedExecutionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html): rejection contract.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model và happens-before.

**Tổng kết:** `Executor` là hợp đồng “chạy Runnable”; `ExecutorService` thêm kết quả `Future` và lifecycle; `ThreadPoolExecutor` hiện thực pool với **worker limits + work queue + rejection policy**. Để ứng dụng đúng, không chỉ chọn số threads: cần kiểm soát **backlog, loại workload, lỗi, hủy, shutdown, shared state và quan hệ memory visibility**. Một thread pool tốt không phải pool lớn nhất, mà là pool có hành vi rõ ràng khi tải bình thường lẫn khi quá tải.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-002">execute() so với submit() và xử lý lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# execute() so với submit() và xử lý lỗi trong Java Executor Framework

## 1. Hai cách giao việc cho executor, hai cách quan sát lỗi

Khi sử dụng **Executor Framework** trong Java, ta thường đưa một công việc vào executor bằng một trong hai phương thức:

- `execute(Runnable)`, được định nghĩa bởi interface `Executor`, **không trả đối tượng kết quả** (kiểu trả về `void`).
- `submit(...)`, được định nghĩa bởi `ExecutorService`, **trả về `Future`** để quan sát hoàn tất, lấy kết quả, nhận lỗi và gửi yêu cầu hủy.

Cả hai đều giao tác vụ cho executor theo chính sách thực thi của implementation. Với một `ThreadPoolExecutor` thông thường, task được chạy trên worker thread; nhưng không được tổng quát hóa rằng **mọi executor đều luôn chạy bất đồng bộ**: có executor chạy trong caller, và một số `RejectedExecutionHandler` cũng có thể chạy công việc trong thread gọi.

Khác biệt quan trọng nhất đối với lỗi:

- `execute(() -> { throw ...; })` với một `Runnable` trực tiếp trong `ThreadPoolExecutor`: lỗi không được callback gọi `execute` trả về như exception bất đồng bộ. Nó thường thoát khỏi `run()` của worker, được `ThreadPoolExecutor.afterExecute(Runnable, Throwable)` nhận dưới dạng tham số `Throwable` và tới cơ chế uncaught exception của thread; worker bị kết thúc bất thường và pool có thể tạo worker thay thế theo trạng thái.
- `submit(() -> { throw ...; })`: implementation thông thường bọc task trong một `FutureTask` hoặc đối tượng `RunnableFuture`. Lỗi được **ghi lại như trạng thái thất bại của Future**; `Future.get()` ném `ExecutionException` chứa nguyên nhân. Lỗi đó thường **không thoát khỏi `FutureTask.run()`** để đến `UncaughtExceptionHandler`. Khi `afterExecute` được gọi cho FutureTask, tham số `Throwable` thường là `null` dù task bên trong đã thất bại.

**Đừng nhầm:** một lời gọi `execute(...)` hoặc `submit(...)` **có thể ném `RejectedExecutionException` ngay trong thread gọi** khi executor từ chối nhận task. Đây là lỗi **giao việc**, khác với exception ném **khi task chạy**.

Bài này dùng Java 17+ và API JDK tiêu chuẩn; nội dung áp dụng cho Java concurrency nói chung.

## 2. Bảng so sánh nhanh

| Tiêu chí | `execute(Runnable)` | `submit(...)` |
| --- | --- | --- |
| Interface | `Executor` | `ExecutorService` |
| Kiểu task | `Runnable` | `Runnable` hoặc `Callable<T>` |
| Giá trị trả từ lời gọi | `void` | `Future<?>` / `Future<T>` |
| Lấy kết quả tính toán | Không có `Future` được trả | `Future.get()` lấy kết quả |
| Quan sát task hoàn tất | Cần cơ chế khác: latch, callback, queue, trạng thái riêng | `Future.isDone()`, `get()` |
| Lỗi trong task với `ThreadPoolExecutor` thông thường | Có thể thoát khỏi worker và tới uncaught handler | Thường được giữ bên trong Future; `get()` ném `ExecutionException` |
| Lỗi khi executor từ chối | Có thể ném `RejectedExecutionException` ngay | Cũng có thể ném `RejectedExecutionException` ngay |
| Hủy công việc qua kết quả trả về | Không có Future để gọi `cancel` | `Future.cancel(...)` |
| Bảo đảm thứ tự task | Không tự bảo đảm | Không tự bảo đảm |
| Có bắt buộc task chạy ở thread khác? | Không, tùy implementation/policy | Không, tùy implementation/policy |

Việc một interface có `Future` **không đồng nghĩa** bạn đã thiết kế xong xử lý lỗi. Nếu chỉ gọi `submit(task)` rồi **bỏ đối tượng Future**, lỗi trong task có thể không được ứng dụng quan sát hoặc ghi nhận như mong đợi.

## 3. Hiểu `Runnable`, `Callable` và ba overload `submit`

`Runnable.run()` không có giá trị trả về và không khai báo checked exception trong chữ ký:

```java
@FunctionalInterface
public interface Runnable {
    void run();
}
```

Đây là trích đoạn chữ ký API để giải thích, không phải định nghĩa lại interface trong dự án.

`Callable<V>.call()` trả về giá trị kiểu `V` và được khai báo `throws Exception`:

```java
@FunctionalInterface
public interface Callable<V> {
    V call() throws Exception;
}
```

`ExecutorService` có ba overload thường dùng:

1. `Future<?> submit(Runnable task)`: nếu hoàn thành bình thường, `future.get()` trả `null`.
2. `<T> Future<T> submit(Runnable task, T result)`: nếu hoàn thành bình thường, `get()` trả đúng **đối tượng `result` được cung cấp**, không phải kết quả return của `Runnable.run()`.
3. `<T> Future<T> submit(Callable<T> task)`: nếu hoàn thành bình thường, `get()` trả kết quả do `call()` sinh ra.

`Future.get()` **chờ cho đến khi task hoàn tất** hoặc gặp một sự kiện hủy/interrupt/lỗi được phản ánh qua API. `isDone()` có thể là true khi task đã hoàn tất **thành công, thất bại hoặc bị hủy**, chứ không đồng nghĩa thành công.

### Ví dụ Java 17+: ba kiểu submit và giá trị get

Tạo file `SubmitResultsDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SubmitResultsDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> runnable = executor.submit(() -> {
                int local = 1 + 1;
                if (local != 2) {
                    throw new AssertionError("Math error");
                }
            });

            Future<String> runnableWithResult =
                    executor.submit(() -> {}, "finished");

            Future<Integer> callable = executor.submit(() -> 40 + 2);

            System.out.println("Runnable result: " + runnable.get());
            System.out.println("Provided result: "
                    + runnableWithResult.get());
            System.out.println("Callable result: " + callable.get());
        } finally {
            executor.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac SubmitResultsDemo.java
java SubmitResultsDemo
```

Đầu ra:

```text
Runnable result: null
Provided result: finished
Callable result: 42
```

`Future.get()` giúp main chờ task và có quan hệ memory consistency thích hợp: các hành động trong task xảy ra-before các hành động sau `get()` thành công trên Future tương ứng theo hợp đồng `java.util.concurrent`. Ví dụ không dựa vào thứ tự bất định của các worker vì main in kết quả sau khi gọi `get()` lần lượt.

## 4. Exception từ `execute` không truyền ngược qua lời gọi đã trả về

Xét snippet sau:

```java
try {
    executor.execute(() -> {
        throw new IllegalStateException("worker failed");
    });
} catch (IllegalStateException e) {
    System.out.println("Caught in caller?");
}
```

Đây là **snippet**, `executor` là biến có sẵn. Với `ThreadPoolExecutor` thông thường đang nhận task để chạy trên worker, `execute` có thể trả về trước khi task chạy, nên `catch` ở **caller** không bắt được exception phát sinh trên worker. Lỗi được xử lý trên chính đường thực thi của worker.

**Ngoại lệ quan trọng:** nếu sử dụng một executor **chạy task ngay trên thread gọi**, hoặc rejection policy kiểu `CallerRunsPolicy` khiến task chạy trong caller, exception của task **có thể** xuất hiện đồng bộ trên caller. Vì vậy không thể đưa ra quy tắc tuyệt đối rằng “mọi exception từ execute đều không thể bắt ở caller”; phải phân biệt **chính sách executor**.

### Java 17+: quan sát lỗi qua `UncaughtExceptionHandler` và `Future.get`

Tạo file `ExecuteVsSubmitFailure.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ExecuteVsSubmitFailure {
    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> uncaught = new AtomicReference<>();
        CountDownLatch handlerCalled = new CountDownLatch(1);

        ExecutorService pool = Executors.newSingleThreadExecutor(task -> {
            Thread t = new Thread(task, "example-worker");
            t.setUncaughtExceptionHandler((thread, error) -> {
                uncaught.set(error);
                handlerCalled.countDown();
            });
            return t;
        });

        try {
            pool.execute(() -> {
                throw new IllegalStateException("execute failure");
            });

            if (!handlerCalled.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Uncaught handler not called");
            }
            System.out.println("execute handler: "
                    + uncaught.get().getClass().getSimpleName());

            Future<?> submitted = pool.submit(() -> {
                throw new IllegalArgumentException("submit failure");
            });

            try {
                submitted.get();
            } catch (ExecutionException e) {
                System.out.println("submit get cause: "
                        + e.getCause().getClass().getSimpleName());
            }
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
```

Chạy:

```bash
javac ExecuteVsSubmitFailure.java
java ExecuteVsSubmitFailure
```

Khi pool hoạt động bình thường, không có interrupt ngoài và không vượt thời hạn phòng vệ 5 giây:

```text
execute handler: IllegalStateException
submit get cause: IllegalArgumentException
```

**Phân tích:**

1. Thread factory đặt `UncaughtExceptionHandler` tùy chỉnh cho worker, tránh phụ thuộc vào stack trace mặc định in ra stderr.
2. `execute` nhận một `Runnable` ném `IllegalStateException`. Với pool này, lỗi không được FutureTask bắt; worker gặp uncaught exception và handler ghi lại lỗi.
3. `handlerCalled.await(...)` buộc main đợi đến khi handler đã nhận lỗi trước khi in. Latch giúp đầu ra không phụ thuộc scheduling; không chèn `sleep` để “canh” thread.
4. Pool kiểu single-thread executor duy trì khả năng chạy công việc tiếp theo, tạo worker thay thế khi cần sau khi worker trước chết bất thường.
5. `submit` bọc `Runnable` trong Future. `IllegalArgumentException` được lưu trong Future; `submitted.get()` ném `ExecutionException`, và `getCause()` trả ngoại lệ gốc.
6. Uncaught handler **không phải kênh thông thường** để thu lỗi của task do `submit` tạo, vì wrapper bắt lỗi.

Đoạn demo sử dụng một `Runnable` trực tiếp với `execute`. Nếu chính đối tượng truyền vào `execute` là `FutureTask` hoặc một wrapper tự bắt `Throwable`, cơ chế báo lỗi có thể khác: phải hiểu **đối tượng Runnable được chạy thực sự là gì**, không chỉ tên hàm giao việc.

## 5. Tại sao exception trong `submit` có thể bị “ẩn”?

Với một executor dựa trên `AbstractExecutorService`, `submit` thường chuyển task thành `RunnableFuture` bằng `newTaskFor`, sau đó gọi `execute` với wrapper ấy. `FutureTask.run()` bắt lỗi từ `Runnable`/`Callable` và lưu kết quả thất bại. Thread worker có thể **tiếp tục chạy các task sau** thay vì chết vì exception của task.

Luồng xử lý khái niệm:

```text
execute(runnable):
    caller -> executor queue -> worker.run()
                             -> lỗi thoát ra từ Runnable
                             -> afterExecute(..., throwable)
                             -> uncaught exception handling

submit(callable/runnable):
    caller -> tạo RunnableFuture/FutureTask
           -> executor queue -> worker.run(FutureTask)
                             -> FutureTask lưu kết quả/lỗi
                             -> afterExecute(..., null) thường gặp
    caller -> future.get() -> giá trị hoặc ExecutionException
```

Đây là **sơ đồ** cho việc thực thi điển hình bằng `ThreadPoolExecutor`, không phải đặc tả rằng mọi implementation `ExecutorService` phải có cùng nội bộ.

**Sai lầm phổ biến:**

```java
// Lỗi thiết kế: không giữ Future và không quan sát ngoại lệ của task.
executor.submit(() -> processOrder());
```

Nếu `processOrder()` ném exception, caller có thể không biết. Trong ứng dụng cần audit, retry, alert hoặc ghi nhận lỗi, hãy **giữ Future để quan sát** hoặc dùng lớp quản lý task có cơ chế báo cáo lỗi tập trung.

## 6. Xử lý `Future.get` đúng cách

`Future.get()` có thể có những kết quả quan trọng:

| Kết quả | API phản ánh | Cách hiểu |
| --- | --- | --- |
| Hoàn tất bình thường | Trả kết quả; với Runnable không có result là `null` | Task đã hoàn tất thành công theo hợp đồng |
| Task ném lỗi | `ExecutionException` | `getCause()` là lỗi gốc trong task |
| Future bị hủy | `CancellationException` (unchecked) | Future đang ở trạng thái canceled |
| Thread **đang chờ** bị interrupt | `InterruptedException` | Việc chờ bị ngắt; không nhất thiết task đã hủy |
| `get(timeout, unit)` hết thời gian | `TimeoutException` | Việc **chờ** hết hạn; task có thể vẫn chạy |

Khi xử lý `ExecutionException`, không được bỏ qua nguyên nhân:

```java
try {
    Result value = future.get();
    use(value);
} catch (ExecutionException e) {
    Throwable original = e.getCause();
    recordFailure(original);
}
```

Đây là snippet, `Result`, `future`, `use` và `recordFailure` thuộc ứng dụng giả định; cần xử lý thêm `InterruptedException` và `CancellationException` tại tầng thích hợp.

**Không nên làm mất interrupt:** nếu thread gọi `get` bị interrupt và tầng hiện tại không thể `throws InterruptedException`, thường gọi `Thread.currentThread().interrupt()` rồi thoát/đưa ra kết quả hủy hợp lệ. Không bắt `InterruptedException` rồi tiếp tục chờ vô hạn mà không có chính sách.

**Không nhầm thời gian chờ với cancellation:** `future.get(100, MILLISECONDS)` ném `TimeoutException` không làm task tự dừng. Nếu muốn yêu cầu hủy, caller có thể `future.cancel(true)`, nhưng đó là **yêu cầu hợp tác**, không bảo đảm worker lập tức ngừng hoặc đã rollback các side effect.

### Có cần gọi `get` trong chính worker chạy task đó không?

Nếu một task trong pool **một worker** gửi task khác vào cùng pool rồi gọi `future.get()` đợi task mới, worker có thể tự kẹt: task mới đang nằm trong queue và không có worker khác để chạy. Đây là **thread-starvation deadlock**, không phải lỗi riêng của `submit`. Không thiết kế workflow chờ lồng nhau trên pool thiếu tài nguyên.

## 7. `ThreadPoolExecutor.afterExecute` và vì sao `Throwable` có thể bằng null

`ThreadPoolExecutor` có hook:

```java
protected void afterExecute(Runnable r, Throwable t)
```

Hook chạy khi task kết thúc trên worker của pool. Với `execute` truyền một `Runnable` ném exception trực tiếp, `t` thường chứa exception đó. Nhưng với task `submit` được bọc `FutureTask`, lỗi đã bị wrapper lưu lại, nên `t` thường là `null`.

Nếu muốn ghi nhận lỗi của **cả hai cách giao việc** ở một `ThreadPoolExecutor` tùy chỉnh, cần kiểm tra trường hợp `r instanceof Future<?>` rồi lấy lỗi từ Future **nếu task đã done**. Việc gọi `get()` khi task chưa done có thể làm block worker của hook; cần guard đúng.

### Ví dụ Java 17+: thu lỗi tập trung trong afterExecute

Tạo file `AfterExecuteFailures.java`:

```java
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AfterExecuteFailures {
    static final class ObservedPool extends ThreadPoolExecutor {
        final ConcurrentLinkedQueue<String> failures =
                new ConcurrentLinkedQueue<>();

        ObservedPool() {
            super(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(), task -> {
                        Thread t = new Thread(task, "observed-worker");
                        t.setUncaughtExceptionHandler((thread, error) -> {
                            // Hook này chỉ tránh in stack trace mặc định
                            // trong ví dụ; không thay thế logging thực tế.
                        });
                        return t;
                    });
        }

        @Override
        protected void afterExecute(Runnable task, Throwable error) {
            super.afterExecute(task, error);
            Throwable failure = error;

            if (failure == null
                    && task instanceof Future<?> future
                    && future.isDone()) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    failure = e.getCause();
                } catch (CancellationException e) {
                    failure = e;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (failure != null) {
                failures.add(failure.getClass().getSimpleName());
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ObservedPool pool = new ObservedPool();

        try {
            pool.submit(() -> {
                throw new IllegalArgumentException("submitted");
            });
            pool.execute(() -> {
                throw new IllegalStateException("executed");
            });

            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Pool did not terminate");
            }
            System.out.println("Observed: " + pool.failures);
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Chạy:

```bash
javac AfterExecuteFailures.java
java AfterExecuteFailures
```

Khi pool hoàn tất bình thường trong thời hạn 5 giây:

```text
Observed: [IllegalArgumentException, IllegalStateException]
```

**Giải thích:**

- Pool có đúng **một worker** và hàng đợi FIFO `LinkedBlockingQueue`, nên task submit được xử lý trước task execute.
- `submit` tạo FutureTask lưu `IllegalArgumentException`; trong hook, `error` là `null` nhưng `future.get()` ném `ExecutionException`, từ đó lấy cause.
- `execute` truyền Runnable ném `IllegalStateException`; hook nhận `error` khác null và ghi vào hàng đợi lỗi.
- Với lỗi của `execute`, worker có thể chết; ThreadPoolExecutor xử lý trạng thái worker phù hợp. Chờ `awaitTermination` giúp main chỉ in danh sách lỗi sau khi hai task đều đã được xử lý.
- `future.isDone()` là guard trước `get()` để tránh blocking không cần thiết. Tuy nhiên nếu subclass hoặc custom RunnableFuture có hợp đồng lạ, hoặc hook được thiết kế để chạy trong môi trường phức tạp, vẫn cần thận trọng.

**Giới hạn của hook:** `afterExecute` được gọi cho các task **đã thực sự chạy trên worker**. Task bị từ chối trước khi chạy sẽ không tự đi qua hook; task nằm trong queue nhưng bị `shutdownNow()` lấy ra cũng không nhất thiết được chạy. Ngoài ra, `afterExecute` không phải nơi thích hợp cho tác vụ logging nặng hoặc blocking lâu, vì nó đang chạy trên worker và có thể làm giảm throughput.

Không nên coi ví dụ như một thư viện observability đầy đủ: trong production, logging cần tránh ném exception trong hook, ghi cả context/correlation ID và tách riêng cancellation khỏi lỗi nghiệp vụ nếu cần.

## 8. Lỗi bị từ chối khi giao việc: `RejectedExecutionException`

Cả `execute` và `submit` có thể bị từ chối **ngay khi gọi**, ví dụ executor đã shutdown hoặc thread pool và hàng đợi bounded đều đầy. Với `ThreadPoolExecutor.AbortPolicy`, phương thức ném `RejectedExecutionException` trong thread gọi.

Ngược lại, `CallerRunsPolicy` có thể chạy task trong caller khi pool còn đang chạy nhưng không nhận thêm task; điều này có thể tạo backpressure nhưng cũng thay đổi luồng chạy và cách exception xuất hiện. `DiscardPolicy` hoặc `DiscardOldestPolicy` có rủi ro đánh rơi task theo chính sách; với `submit`, nếu future của task bị bỏ mà không thực thi/hoàn tất, caller đang đợi kết quả có thể bị kẹt. Chỉ sử dụng rejection policy khi hiểu rõ tác động đến business logic và hợp đồng Future.

### Java 17+: tái hiện rejection với bounded queue

Tạo file `RejectionDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RejectionDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            workerStarted.await();     // Worker đang chờ trong task đầu.
            pool.execute(() -> {});   // Chiếm slot duy nhất trong queue.

            boolean rejected = false;
            try {
                pool.submit(() -> 42);
            } catch (RejectedExecutionException e) {
                rejected = true;
            }

            System.out.println("Submission rejected: " + rejected);
        } finally {
            releaseWorker.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac RejectionDemo.java
java RejectionDemo
```

Đầu ra khi pool hoàn tất bình thường:

```text
Submission rejected: true
```

Một worker đang bị giữ trong task đầu bởi `releaseWorker`; hàng đợi capacity 1 đã chứa task thứ hai. Lần gọi `submit` thứ ba không còn worker hay slot nhận nên `AbortPolicy` ném `RejectedExecutionException` **ở caller**. Không có `Future` được trả thành công từ lần gọi bị từ chối ấy.

**Giao việc thất bại khác task thất bại:** `RejectedExecutionException` cần được xử lý quanh câu lệnh `submit`/`execute`, còn lỗi của task `submit` cần quan sát qua `Future.get` hoặc cơ chế riêng.

`Executors.newFixedThreadPool(n)` thường dùng hàng đợi không giới hạn về mặt cấu hình, nên không phù hợp nếu bạn cần bounded backpressure rõ ràng. Số lượng task chờ vẫn có thể tiêu thụ nhiều bộ nhớ; cân nhắc `ThreadPoolExecutor` với queue capacity và rejection policy được chọn cẩn thận.

## 9. Shutdown, interruption và cancellation

`ExecutorService` có hai phương thức vòng đời quan trọng:

- `shutdown()`: không nhận task mới; những task đã được nhận trước đó tiếp tục được xử lý theo hợp đồng của executor. **Không phải dừng ngay mọi worker.**
- `shutdownNow()`: cố gắng dừng các task đang chạy—thông thường bằng interrupt—và trả lại danh sách task đang chờ nhưng chưa được chạy. **Không bảo đảm mọi task đang chạy sẽ dừng**, đặc biệt nếu code không hợp tác với interrupt hoặc đang ở blocking point không interruptible.

`Future.cancel(false)` có thể đánh dấu Future bị hủy khi phù hợp mà không yêu cầu interrupt thread đang chạy. `Future.cancel(true)` cố gửi interrupt cho task đang chạy nếu có thread được gắn với nó, nhưng không phải cơ chế cưỡng ép rollback. `Future.get()` trên Future đã cancelled ném `CancellationException`.

Nếu task có side effect, cần xác định:

1. Hủy trước khi task chạy thì có cần trả lại quyền sở hữu công việc không?
2. Hủy khi đang ghi dở thì cleanup/compensation thế nào?
3. Interrupt đến khi đang chờ queue, lock hoặc I/O thì phản ứng ra sao?
4. Chờ kết quả quá hạn có đồng nghĩa phải yêu cầu hủy task không?
5. Có cần log trạng thái **đã nhận**, **đang chạy**, **thành công**, **lỗi**, **bị hủy** riêng biệt không?

Một cách shutdown có trách nhiệm cho ứng dụng sử dụng executor riêng là:

```java
// Snippet: executor là ExecutorService do ứng dụng sở hữu.
executor.shutdown();
try {
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            // Báo cáo worker chưa dừng, không tuyên bố shutdown thành công.
        }
    }
} catch (InterruptedException e) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

Đây là snippet về shutdown policy, không phải file độc lập. Không nên tùy tiện shutdown một executor **thuộc sở hữu của thành phần khác**, vì như vậy có thể cắt ngang task của họ.

### Task code cần hợp tác với interrupt

`submit` và `cancel(true)` sẽ không tự sửa một task chạy CPU vô hạn:

```java
// Snippet về vòng tính toán có điều kiện dừng hợp tác.
while (!Thread.currentThread().isInterrupted()) {
    processNextChunk();
}
```

Ở đây `processNextChunk()` do ứng dụng định nghĩa. Đối với thao tác blocking như `BlockingQueue.take()`, `CountDownLatch.await()`, thread có thể ném `InterruptedException`; code thường cần cleanup và restore/propagate interrupt phù hợp. Không bắt rồi nuốt exception, khiến công việc tiếp tục dù caller đã yêu cầu hủy.

## 10. Khi nào chọn `execute`, khi nào chọn `submit`?

### Nên cân nhắc `execute` khi

- Task thực sự **không có kết quả** cho caller và được quản lý bằng một hệ thống giám sát/lifecycle riêng.
- Bạn muốn dùng `Executor` interface đơn giản, không cần hợp đồng `Future`.
- Hệ thống có kênh rõ ràng để ghi nhận lỗi của Runnable (ví dụ bắt/ghi lỗi **bên trong** task, custom executor hook hoặc uncaught handler phù hợp với implementation).
- Công việc “fire-and-forget” là **đúng nghiệp vụ**, đã có đường xử lý lỗi và không chỉ bị quên sau khi giao.

Ví dụ wrapper để ghi lỗi:

```java
// Snippet: executor là Executor, task là Runnable,
// recordFailure được định nghĩa bởi ứng dụng.
executor.execute(() -> {
    try {
        task.run();
    } catch (RuntimeException e) {
        recordFailure(e);
        throw e; // Tùy chính sách: giữ cho worker biết lỗi.
    }
});
```

Đây là snippet, không phải chương trình độc lập. Việc **rethrow** có thể kích hoạt uncaught handler hoặc khiến worker kết thúc trong ThreadPoolExecutor; với một `Executor` khác phản ứng có thể khác. Không ghi lỗi hai lần nếu đã có hệ thống observability bắt từ nơi khác.

### Nên cân nhắc `submit` khi

- Cần nhận kết quả của `Callable` hoặc quan sát khi `Runnable` hoàn tất.
- Caller cần chờ task qua `get`, xử lý `ExecutionException`, dùng timeout hoặc hủy qua `Future.cancel`.
- Cần quản lý một tập task và thu thập trạng thái **thành công / thất bại / hủy** rõ ràng.
- Có một service chịu trách nhiệm **theo dõi Future** thay vì vứt Future rồi mặc định các lỗi sẽ được log.

Ví dụ quản lý nhiều task:

```java
// Snippet: executor là ExecutorService, tasks là danh sách Callable<Result>.
List<Future<Result>> futures = new ArrayList<>();
for (Callable<Result> task : tasks) {
    futures.add(executor.submit(task));
}
for (Future<Result> future : futures) {
    try {
        Result result = future.get();
        saveResult(result);
    } catch (ExecutionException e) {
        recordFailure(e.getCause());
    }
}
```

Đây là snippet, chưa có imports và khai báo `Result`/`tasks`/`saveResult`/`recordFailure`; đồng thời cần xử lý `InterruptedException`, `CancellationException`, deadline và chính sách có tiếp tục chờ sau một task thất bại hay không. Nếu cần gom kết quả theo thứ tự **hoàn thành**, `ExecutorCompletionService` có thể giúp tránh việc đợi Future đầu tiên lâu dù các task sau đã xong.

### Một quy tắc thiết kế đơn giản

**Nếu task thất bại, ai chịu trách nhiệm biết và phản ứng?**

- Nếu answer là **caller**, `submit` + `Future.get`/quản lý Future thường là thiết kế rõ nhất.
- Nếu answer là **hạ tầng hoặc task tự ghi nhận**, `execute` có thể đủ.
- Nếu **không ai** chịu trách nhiệm, cả hai cách đều thiếu chính sách xử lý lỗi.

Không có lựa chọn luôn tốt hơn. Khác biệt cốt lõi là **hợp đồng giao việc, quan sát kết quả, lỗi và lifecycle**, chứ không chỉ “hàm nào chạy nhanh hơn”.

## 11. Các sai lầm cần tránh

1. **“`execute` chạy đồng bộ, `submit` chạy bất đồng bộ.”** Sai; implementation và rejection policy quyết định thread chạy thực tế.
2. **“`submit` không bao giờ ném exception ở caller.”** Sai: `RejectedExecutionException` có thể ném **ngay lúc giao việc**; còn exception **trong task** được Future ghi lại theo implementation thông thường.
3. **“`try/catch` quanh `execute` luôn bắt lỗi task.”** Sai nếu task chạy trên worker khác; có ngoại lệ với direct/caller-runs execution.
4. **“`submit` tự log exception ra stderr.”** Sai; wrapper Future thường bắt lỗi; nếu không quan sát Future, lỗi có thể bị bỏ qua.
5. **“`Future.isDone()` true nghĩa là thành công.”** Sai: cancelled và failed đều có thể done.
6. **“`Future.get()` trả exception gốc trực tiếp.”** Thường sai: task exception được bọc trong `ExecutionException`; dùng `getCause()`.
7. **“`afterExecute` nhận Throwable khác null cho mọi task lỗi.”** Sai đối với `FutureTask` của `submit`; có thể cần kiểm tra Future.
8. **“`afterExecute` bắt cả task bị từ chối.”** Sai: task bị reject trước khi chạy không đi qua hook worker.
9. **“`Future.get(timeout)` hết hạn đồng nghĩa task đã cancel.”** Sai: timeout là trạng thái **ngừng chờ** của caller.
10. **“`cancel(true)` bảo đảm tác vụ ngừng tức thì.”** Sai: interrupt là hợp tác; task/I/O có thể không đáp ứng ngay.
11. **“`shutdownNow` dừng mọi tác vụ ngay.”** Sai: chỉ cố dừng và trả các task chưa chạy; cần kiểm tra thực tế.
12. **“Có một worker thì task gửi nested submit và get luôn chạy được.”** Sai: có thể deadlock do thiếu worker.
13. **“Dùng `DiscardPolicy` với submit là vô hại.”** Sai: Future của một task bị bỏ có thể không bao giờ hoàn thành và caller chờ mãi.
14. **“Chỉ cần bắt `Exception` trong task là xử lý được mọi lỗi.”** Không hoàn toàn: `Error` là `Throwable` nhưng không phải `Exception`; chính sách logging/lifecycle cần phân loại lỗi phù hợp, không cố nuốt lỗi hệ thống nghiêm trọng.
15. **“UncaughtExceptionHandler là cơ chế tổng quát cho mọi kiểu submit.”** Sai: task wrapper có thể lưu lỗi mà không để exception thoát ra worker.
16. **“Dùng Future đồng nghĩa đã có observability.”** Sai nếu không giữ, đọc hoặc xử lý Future ở đâu cả.
17. **“ExecutorService cần shutdown ở mọi nơi gọi submit.”** Không đúng nếu executor thuộc quyền quản lý của service khác; phải tuân thủ ownership.

## 12. Checklist triển khai ExecutorService có xử lý lỗi

1. **Task có cần kết quả không?** Chọn `Runnable` hay `Callable<T>` phù hợp.
2. **Ai sở hữu executor?** Ai chịu trách nhiệm `shutdown` và giám sát vòng đời?
3. **Chọn `execute` hay `submit` theo nghĩa vụ quan sát:** Future có được lưu và chờ/kiểm tra không?
4. **Phân biệt exception khi nhận việc và khi chạy:** catch `RejectedExecutionException` đúng ở caller; xử lý task failure qua Future/hook/logging.
5. **Đối với submit, quan sát `ExecutionException` và cause;** phân biệt cancellation, interrupt và timeout.
6. **Đối với execute, xác định cơ chế báo lỗi:** task tự xử lý, uncaught handler hay `afterExecute` tùy implementation.
7. **Nếu override `afterExecute`, xử lý `Future` an toàn:** chỉ `get` khi done; tránh làm hook blocking và không để hook tự ném lỗi.
8. **Xem rejection policy và queue capacity:** pool bounded không có slot phải có đường backpressure/retry/từ chối rõ ràng.
9. **Không chờ nested Future trên pool không đủ thread:** kiểm tra phụ thuộc task, worker count và các lock đang giữ.
10. **Thiết kế timeout, cancel, shutdown:** có chính sách khi caller ngừng chờ nhưng task còn chạy.
11. **Bảo đảm xử lý lỗi không tạo side effect trùng:** retry cần idempotency hoặc giao thức phù hợp.
12. **Kiểm thử đường thành công, thất bại, hủy, từ chối:** dùng `CountDownLatch`/bounded queue để tạo lịch có kiểm soát, không đoán timing bằng `sleep`.
13. **Đo và giám sát:** số task được nhận/chạy/xong, lỗi task, cancellation, rejection, queue backlog và thời gian chờ; ghi context để tìm được task liên quan.

**Ghi nhớ:** `execute(Runnable)` giao việc **không trả Future**; với task trực tiếp trên `ThreadPoolExecutor`, exception có thể đi qua hook/uncaught handler của worker. `submit(...)` trả **Future**, nơi chứa kết quả và lỗi để caller quan sát qua `get()`; lỗi của task thường không xuất hiện ở uncaught handler. Cả hai có thể bị từ chối **ngay khi giao việc**. Thiết kế đúng phải có người chịu trách nhiệm nhận lỗi, xử lý timeout/interrupt/cancellation và quản lý executor theo vòng đời rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-003">Fixed, cached và single-thread executors</a>

<details>
<summary>Xem chi tiết</summary>

# Fixed, cached và single-thread executors trong Java

## 1. Mục tiêu và phạm vi

Sau bài học, bạn có thể:

- Phân biệt ba factory method **Executors.newFixedThreadPool(n)**, **Executors.newCachedThreadPool()** và **Executors.newSingleThreadExecutor()** theo số worker, cơ chế tạo/tái sử dụng thread, loại hàng đợi, độ tuần tự và giới hạn tài nguyên.
- Hiểu mối liên hệ giữa **Executor**, **ExecutorService**, **ThreadPoolExecutor**, **Runnable**, **Callable** và **Future**.
- Giải thích vì sao một **fixed pool không có hàng đợi giới hạn mặc định**, vì sao **cached pool không có giới hạn thread thực tế do người dùng chỉ định**, và vì sao **single-thread executor không đồng nghĩa Thread object cố định mãi mãi**.
- Chọn executor cho công việc độc lập, CPU-bound, blocking I/O, pipeline cần tuần tự hoặc tài nguyên cần concurrency cố định; không nhầm “tối đa N worker” với “tối đa N công việc được nhận”.
- Dùng **submit()**, **execute()**, **Future.get()**, **shutdown()**, **shutdownNow()**, **awaitTermination()** và xử lý lỗi, hủy, timeout, interrupt đúng hợp đồng.
- Tự tạo **ThreadPoolExecutor với bounded queue và rejection policy** khi cần kiểm soát tải thay vì dùng factory mặc định không giới hạn.
- Chạy ví dụ Java 17+ có stdout xác định, biết cách kiểm chứng tính chất concurrency mà không dựa vào Thread.sleep() hay thứ tự log ngẫu nhiên.

Phạm vi là **Java concurrency nói chung trong JDK 17+**, không đề cập cấu hình executor, lifecycle hay annotation riêng của Spring. Ví dụ dùng platform threads của Java 17. Các API virtual thread xuất hiện từ phiên bản JDK mới hơn không phải một trong ba factory được so sánh ở đây.

## 2. Trước tiên: executor là gì?

Thay vì tạo một Thread riêng cho mọi đơn vị công việc, ứng dụng có thể **giao task** cho một executor quản lý việc thực thi.

~~~java
// Đoạn minh họa, không phải chương trình độc lập:
ExecutorService pool = Executors.newFixedThreadPool(2);
try {
    Future<Integer> result = pool.submit(() -> 6 * 7);
    System.out.println(result.get());
} finally {
    pool.shutdown();
}
~~~

Trong một file Java thật cần import các kiểu trong java.util.concurrent và xử lý/khai báo checked exceptions từ get(). Ý nghĩa của từng thành phần:

- **Runnable**: nhiệm vụ không trả kết quả qua phương thức run(), nhưng có thể có side effect.
- **Callable<T>**: nhiệm vụ trả T hoặc ném exception từ call().
- **Executor.execute(Runnable)**: yêu cầu executor thực thi Runnable.
- **ExecutorService**: bổ sung submit(), Future, shutdown, invokeAll, invokeAny và điều khiển vòng đời.
- **Future<T>**: đại diện cho kết quả/cancel/completion, có phương thức get() để chờ.
- **ThreadPoolExecutor**: một triển khai executor quản lý các worker platform thread, queue và chính sách nhận/từ chối task.

**Executor** phân tách **gửi công việc** khỏi **cách công việc được chạy**. Việc dùng một executor không tự khiến các task thread-safe: nếu hai task cùng sửa một HashMap mutable mà thiếu đồng bộ hóa, data race vẫn có thể xảy ra.

### Task, worker và queue là ba đại lượng khác nhau

~~~text
Producer gọi submit(task)
           |
           v
     ExecutorService
        |        |
        |        +-- Work queue: task đang chờ
        |
        +-- Worker threads: task đang được chạy
                  |
                  +-- Future trả kết quả/lỗi/hủy
~~~

Một **task được chấp nhận** chưa chắc **đang chạy ngay**: nó có thể còn trong queue. Một **thread pool có 4 worker** không đồng nghĩa queue chỉ nhận 4 task. Nếu task chờ nhiều hơn tốc độ xử lý, queue hoặc số worker có thể lớn dần tùy factory.

## 3. Bảng so sánh ba loại executor

| Tiêu chí | newFixedThreadPool(n) | newCachedThreadPool() | newSingleThreadExecutor() |
| --- | --- | --- | --- |
| Số thread làm task cùng lúc | Tối đa **n** | Có thể tăng theo nhu cầu; giới hạn tối đa nội bộ rất lớn | **1** |
| Kích thước pool thường trực | Có thể tạo worker khi cần đến n; mặc định core không tự hết hạn | Thread rảnh có thể bị thu hồi sau thời gian idle | Một worker nếu cần; có thể thay worker đã chết |
| Queue mặc định | **LinkedBlockingQueue không giới hạn thực dụng** | **SynchronousQueue**, handoff trực tiếp, không giữ backlog theo hàng đợi thông thường | **LinkedBlockingQueue không giới hạn thực dụng** |
| Khi task đến mà mọi worker bận | Đưa vào queue | Thường tạo thêm worker (nếu chưa tới giới hạn và tài nguyên cho phép) | Đưa vào queue, không chạy song song |
| Thread rảnh được tái sử dụng | Có | Có; đặc trưng cached | Có; worker được thay thế nếu chết |
| Giữ thứ tự thực thi task | Không bảo đảm hoàn tất theo thứ tự gửi | Không bảo đảm | Task được thực hiện tuần tự theo thứ tự đưa vào queue |
| Có thể gây vấn đề do tải không giới hạn | Queue tăng/bộ nhớ | Thread tăng/bộ nhớ/thread exhaustion | Queue tăng/độ trễ |
| Mục đích điển hình | Giới hạn concurrency của nhóm task | Nhiều task ngắn, đến từng đợt, có kiểm soát tải bên ngoài | Tuần tự hóa một dòng công việc/state |

**Ghi chú “không giới hạn”:** Java không hứa bộ nhớ vô hạn. Mặc định fixed và single dùng queue có capacity ở mức rất lớn (thực dụng xem như unbounded), nên overload có thể gây cạn heap trước khi chạm giới hạn số phần tử. Cached pool có ngưỡng worker tối đa rất lớn, nhưng hệ điều hành/JVM có thể hết tài nguyên tạo thread sớm hơn rất nhiều. Vì vậy “không giới hạn thực dụng” là **rủi ro**, không phải lời hứa khả năng mở rộng vô hạn.

## 4. Fixed thread pool: giới hạn số worker, không giới hạn backlog mặc định

Tạo pool:

~~~java
// Đoạn minh họa; cần import java.util.concurrent.Executors.
ExecutorService pool = Executors.newFixedThreadPool(4);
~~~

Những đặc điểm cốt lõi của **newFixedThreadPool(nThreads)**:

1. Khi cần, executor dùng tối đa **nThreads** worker để xử lý các task.
2. Các task đến khi mọi worker bận được đặt vào queue, thay vì tạo thread thứ n+1.
3. Worker rảnh được tái sử dụng cho task sau.
4. Nếu một worker chấm dứt do lỗi trước shutdown, executor có thể tạo worker thay thế để tiếp tục phục vụ các task.
5. Worker platform thread của pool mặc định **không tự hết hạn vì nhàn rỗi** như cached pool khi pool còn chạy.
6. Factory dùng một queue không có giới hạn hữu ích để tạo **backpressure mặc định**; số task chờ có thể tăng đến khi ảnh hưởng memory/latency.

**Điều được cố định là mức concurrency tối đa**, không phải tổng số task được submit, thời gian hoàn tất hay số thread tuyệt đối mãi mãi trong mọi hoàn cảnh.

### Khi nào chọn fixed?

- Tác vụ độc lập và muốn **giới hạn số tác vụ chạy đồng thời**, ví dụ tính toán xử lý dữ liệu.
- Có thể điều tiết tốc độ submit hoặc workload đã được giới hạn từ bên ngoài.
- Có nhu cầu một pool phục vụ nhiều task vừa/nhỏ, giảm chi phí tạo Thread mới từng lần.
- Có kế hoạch shutdown và kiểm soát lỗi.

**Không dùng factory này mà mặc định đã giải quyết overload.** Nếu producer gửi 100.000 task nhưng 4 worker chỉ xử lý được vài trăm task/giây, backlog tích lũy và tail latency tăng. Một executor giới hạn worker nhưng queue không giới hạn **không giới hạn lượng công việc đã nhận**.

### Ví dụ 1: fixed pool không chạy quá hai task đồng thời

Ta dùng CountDownLatch để buộc hai task đầu giữ worker trong một khoảng logic, rồi kiểm tra có đúng hai task đang hoạt động. Không cần dùng sleep để “may mắn” bắt gặp concurrency.

Tệp **FixedPoolDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class FixedPoolDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch twoStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        Callable<Integer> task = () -> {
            int now = active.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            twoStarted.countDown();
            try {
                release.await();
                return 1;
            } finally {
                active.decrementAndGet();
            }
        };

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(task));
            }

            twoStarted.await();
            System.out.println("first wave active = " + active.get());
            release.countDown();

            int sum = 0;
            for (Future<Integer> f : futures) {
                sum += f.get();
            }
            System.out.println("completed = " + sum);
            System.out.println("peak <= 2 = " + (peak.get() <= 2));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Lệnh chạy:

~~~bash
javac -encoding UTF-8 FixedPoolDemo.java
java FixedPoolDemo
~~~

Stdout trong điều kiện chạy bình thường, không có interrupt bên ngoài:

~~~text
first wave active = 2
completed = 4
peak <= 2 = true
~~~

**Giải thích:**

- Hai worker vào task, tăng active rồi chờ release. Vì pool có tối đa 2 worker, hai task còn lại phải chờ trong queue.
- Khi main thấy latch twoStarted về 0, cả hai task đầu vẫn chờ release nên active bằng 2.
- Sau release, bốn task cùng hoàn thành theo lịch khác nhau, nhưng mỗi task trả 1 nên tổng là 4.
- Peak không vượt 2 dù đã submit 4 task.
- finally giải phóng latch nếu đường chờ gặp exception và yêu cầu pool dừng. Trong sản phẩm thật, shutdown hợp tác và chờ kết thúc theo policy thường tốt hơn shutdownNow vô điều kiện.

**Không được suy ra** fixed pool luôn làm hai task hoạt động. Nếu workload không có đủ task hoặc JVM chưa tạo xong worker, số task đang chạy có thể ít hơn n. Ví dụ trên cố ý dùng latch để quan sát được hai worker cùng hoạt động.

## 5. Cached thread pool: tái sử dụng worker rảnh, tăng worker khi không có người nhận

~~~java
// Đoạn minh họa, không phải chương trình độc lập:
ExecutorService pool = Executors.newCachedThreadPool();
~~~

Mẫu cached pool phổ biến sử dụng các đặc điểm của ThreadPoolExecutor:

- **Core pool size = 0**.
- **Maximum pool size** rất lớn (tối đa được cấu hình bằng Integer.MAX_VALUE trong factory JDK).
- **SynchronousQueue**: hàng đợi kiểu **direct handoff**; nó không tích lũy một danh sách các task chờ theo cách LinkedBlockingQueue làm.
- **Keep-alive mặc định 60 giây**: worker nhàn rỗi có thể bị thu hồi nếu không còn nhận việc.
- Task đến sẽ được trao cho worker rảnh phù hợp; nếu không có worker sẵn sàng nhận, executor có thể tạo worker mới.

Cached pool **không giới hạn concurrency bằng một số nhỏ cố định**. Nó phù hợp khi task ngắn, các burst có giới hạn kiểm soát được, và số thread tạo mới không đe dọa tài nguyên.

### Rủi ro với blocking I/O kéo dài

Giả sử có 2.000 request, mỗi task bị chặn chờ dịch vụ bên ngoài rất lâu. Nếu không có worker rảnh tại thời điểm task được giao, cached pool có thể tìm cách tạo rất nhiều thread. Mỗi platform thread dùng tài nguyên bộ nhớ, stack và scheduling. Hệ thống có thể gặp **thread exhaustion**, tăng context switching, giảm throughput hoặc thất bại khi tạo thread.

SynchronousQueue **không tự tạo backpressure** ở giới hạn nghiệp vụ mong muốn. Nếu cần ràng buộc tối đa số request đang hoạt động, dùng Semaphore, bounded executor hoặc hàng đợi giới hạn phù hợp, đồng thời xác định chính sách khi quá tải.

### Ví dụ 2: cached pool tạo đủ worker khi các task cùng chờ

Tệp **CachedPoolDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class CachedPoolDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        CountDownLatch threeStarted = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();

        Callable<Integer> task = () -> {
            active.incrementAndGet();
            threeStarted.countDown();
            try {
                release.await();
                return 1;
            } finally {
                active.decrementAndGet();
            }
        };

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                futures.add(pool.submit(task));
            }

            threeStarted.await();
            System.out.println("concurrent active = " + active.get());

            release.countDown();

            int sum = 0;
            for (Future<Integer> f : futures) {
                sum += f.get();
            }
            System.out.println("completed = " + sum);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CachedPoolDemo.java
java CachedPoolDemo
~~~

Stdout:

~~~text
concurrent active = 3
completed = 3
~~~

Ba task cùng chờ trên release; không có worker rảnh để nhận task thứ hai và thứ ba, nên cached pool có thể mở rộng đủ để cả ba task bắt đầu. Cách dùng latch giúp kết quả quan sát có tính xác định trong ví dụ. **Điều này không có nghĩa cached pool luôn tạo thread mới cho mỗi task**: sau khi task kết thúc, worker có thể tái sử dụng cho task khác; khi rảnh lâu, worker có thể bị loại bỏ.

Nếu thiết kế chỉ được phép **2 request đồng thời**, cached pool không tự giữ giới hạn đó. Cần thêm control (ví dụ Semaphore) hoặc chọn executor giới hạn worker và chính sách backlog hợp lý.

## 6. Single-thread executor: một task chạy tại một thời điểm

~~~java
// Đoạn minh họa:
ExecutorService pool = Executors.newSingleThreadExecutor();
~~~

**newSingleThreadExecutor()** có hợp đồng:

1. Chỉ có **một worker hoạt động thực thi task** tại một thời điểm.
2. Task được **thực hiện tuần tự**; không có hai task trong cùng executor chạy chồng lên nhau.
3. Các task đi vào queue chờ lượt. Với factory mặc định, queue có tính chất **không giới hạn thực dụng**.
4. Nếu worker thất bại và chấm dứt trước shutdown, executor tạo worker thay thế khi cần; **không nên dựa vào cùng một Thread object sống mãi**.
5. Executor trả về là một lớp bao không cung cấp cách public đơn giản để chỉnh pool lên nhiều worker, trái với việc tự tạo ThreadPoolExecutor có core/max bằng 1.
6. Khi nhiều thread cùng submit, thứ tự chính xác giữa các lời gọi đồng thời do lịch submit quyết định; **không có thứ tự nghiệp vụ tự nhiên** nếu ứng dụng chưa định nghĩa nó.

Single-thread executor là cách dễ hiểu để xây dựng **mô hình một owner cập nhật shared mutable state**. Nhưng các thread khác không được trực tiếp sửa state ấy cùng lúc, nếu không confinement bị phá.

### Ví dụ 3: xử lý tuần tự ba command

Tệp **SingleThreadPoolDemo.java**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SingleThreadPoolDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        StringBuilder state = new StringBuilder();

        try {
            pool.submit(() -> state.append("A"));
            pool.submit(() -> state.append("B"));
            pool.submit(() -> state.append("C"));

            Future<String> result = pool.submit(state::toString);
            System.out.println("state = " + result.get());
        } finally {
            pool.shutdown();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 SingleThreadPoolDemo.java
java SingleThreadPoolDemo
~~~

Stdout:

~~~text
state = ABC
~~~

**Vì sao có thể dùng StringBuilder?** Các task thay đổi StringBuilder và task snapshot đều chạy tuần tự trên một executor. Main **không** đọc hoặc sửa StringBuilder trực tiếp khi worker đang xử lý; main chỉ nhận String qua Future.get(). Trong mô hình ví dụ này không có hai thread cùng truy cập mutable state.

**Lưu ý:** Không nên suy luận “dùng một executor tên single” sẽ khiến mọi lời gọi vào object tự đi qua executor. Nếu thread khác gọi trực tiếp state.append("X") trong lúc worker xử lý, vẫn có race. Và nếu task có side effect bên ngoài, việc chạy tuần tự trong một JVM không tự cung cấp transaction hay exactly-once.

### Không giữ một ThreadLocal như biến trạng thái nghiệp vụ bất biến theo worker

Single-thread executor **thường tái sử dụng một thread**, nhưng có thể thay thế worker khi thread trước chết. **ThreadLocal** gắn với thread cụ thể, không gắn với executor. Vì vậy nếu worker bị thay, giá trị ThreadLocal có thể biến mất; khi reuse worker, ngữ cảnh task cũ có thể còn nếu không remove(). Dữ liệu nghiệp vụ nên nằm trong một owner object có lifecycle rõ ràng thay vì dựa vào ThreadLocal như kho lưu trữ lâu dài.

## 7. Bảng chọn theo nhu cầu và kiểu task

| Bài toán | Lựa chọn khởi đầu | Điều cần kiểm soát thêm |
| --- | --- | --- |
| Tính toán độc lập, muốn tối đa N tác vụ hoạt động | Fixed pool | Kích thước theo CPU/workload, backlog, overload |
| Task ngắn, số task đến từng đợt nhưng concurrency được giới hạn bên ngoài | Cached pool có thể phù hợp | Số thread tối đa thực tế, blocking duration, tài nguyên OS |
| Command tuần tự lên cùng mutable state | Single-thread executor | Queue growth, head-of-line blocking, task failure |
| Hàng triệu request I/O chờ lâu | Không mặc định dùng cached platform-thread pool | Bounded resource model, backpressure, JDK/platform phù hợp |
| Xử lý request cần timeout và bounded backlog | ThreadPoolExecutor cấu hình giới hạn | Queue capacity, rejection, cancellation, shutdown |
| Muốn task hoàn thành theo thứ tự gửi | Single-thread executor hoặc giao thức sắp xếp kết quả | Thứ tự submit giữa nhiều producer, failure/slow task |
| Muốn giữ thứ tự output nhưng vẫn chạy song song | Fixed/cached + lưu Future theo index và kết hợp đúng | Completion order khác submission order |
| Một tác vụ submit task khác rồi chờ Future trên cùng một worker | Tránh mô hình phụ thuộc vòng | Thread-starvation deadlock |

**Đừng chọn chỉ theo con số thread.** Hãy xét thời gian chạy của task, thời gian block, tần suất gửi, RAM, nguy cơ queue tăng và mục tiêu latency. Với tác vụ CPU-bound, số worker gần mức CPU hiệu dụng có thể là điểm thử nghiệm ban đầu, nhưng không phải công thức cố định cho mọi workload.

## 8. Những gì xảy ra khi submit vượt khả năng phục vụ

Giả sử mỗi giây ứng dụng nhận 1.000 task nhưng pool xử lý được 100 task/giây. Nếu không có backpressure, backlog tăng khoảng 900 task/giây trong thời gian tải như vậy kéo dài.

### Fixed và single: queue tăng

~~~text
Incoming -> [Worker 1][Worker 2] + [Queue: task task task task ...]
                                          |
                                      backlog tăng
~~~

Nếu queue không bị giới hạn ở mức nghiệp vụ phù hợp, có thể tăng độ trễ rất lớn hoặc cạn bộ nhớ. **Không có giới hạn worker** khác với **không có giới hạn task chờ**.

### Cached: worker tăng

~~~text
Incoming -> [Worker 1][Worker 2][Worker 3][Worker 4] ...
                mỗi worker bị block lâu
                -> số platform threads tăng
~~~

Cached pool đặt trọng tâm vào tái sử dụng worker và mở rộng nhanh khi không có worker rảnh; dưới tải blocking không kiểm soát, có thể dùng cạn tài nguyên tạo thread.

### Single: head-of-line blocking

Một task đầu tiên chậm hoặc chờ I/O làm tất cả task sau **phải đợi lượt**. Nếu task đầu kẹt mãi, queue có thể tích lũy mà không công việc phía sau nào được chạy. Đó là trade-off của mô hình tuần tự.

**Backpressure** nghĩa là hệ thống chủ động giới hạn lượng việc chưa xử lý, để producer phải chờ, bị từ chối có kiểm soát, hoặc áp dụng một chính sách mất dữ liệu được phép theo đặc tả. Mặc định của ba factory không phải là một giải pháp backpressure tổng quát.

## 9. Cấu hình ThreadPoolExecutor có bounded queue

Khi cần giới hạn đồng thời cả số worker và queue, có thể tạo ThreadPoolExecutor tường minh.

Tệp **BoundedExecutorDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BoundedExecutorDemo {
    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy()
        );

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<Integer> first = pool.submit(() -> {
                workerStarted.countDown();
                release.await();
                return 1;
            });

            workerStarted.await();   // Worker đang chạy task 1.
            Future<Integer> second = pool.submit(() -> 2); // Queue 1 slot.

            boolean rejected = false;
            try {
                pool.submit(() -> 3); // Worker bận, queue đầy.
            } catch (RejectedExecutionException ex) {
                rejected = true;
            }

            System.out.println("rejected = " + rejected);
            release.countDown();

            System.out.println("sum = " + (first.get() + second.get()));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 BoundedExecutorDemo.java
java BoundedExecutorDemo
~~~

Stdout:

~~~text
rejected = true
sum = 3
~~~

**Giải thích chính xác:**

1. Core=max=1 nên pool có **một worker**.
2. ArrayBlockingQueue capacity=1 chỉ có **một task chờ**.
3. Task 1 đã báo started và đang đợi release, vì vậy task 2 nằm trong queue.
4. Task 3 không thể chạy ngay, queue đã đầy; AbortPolicy ném RejectedExecutionException ngay khi submit.
5. Main giải phóng task 1 rồi lấy kết quả của hai Future đã được nhận: 1 + 2 = 3.
6. finally bảo đảm release latch ngay cả khi lỗi và yêu cầu dừng pool.

Đây là ví dụ minh họa **giới hạn rõ ràng** chứ không đề xuất luôn bỏ task khi queue đầy. Cần quy định phản hồi thực tế khi rejection xảy ra: retry có giới hạn, thông báo bận, giảm tốc producer, hoặc đưa công việc sang cơ chế lưu trữ có độ bền phù hợp.

### Rejection policies và ý nghĩa

| Handler | Hành vi chủ yếu | Rủi ro |
| --- | --- | --- |
| **AbortPolicy** | Ném RejectedExecutionException | Caller phải xử lý; thích hợp khi không được âm thầm mất việc |
| **CallerRunsPolicy** | Khi chưa shutdown, thực thi task trong thread gọi execute | Có thể block producer và phá giả định “task luôn chạy trên worker chuyên dụng” |
| **DiscardPolicy** | Bỏ task bị từ chối mà không ném | Dễ mất việc âm thầm, khó xác định trạng thái |
| **DiscardOldestPolicy** | Loại task đầu queue rồi thử lại execute | Task cũ có thể bị mất, không phù hợp job bắt buộc xử lý |

**Đặc biệt với thread confinement:** Nếu dùng CallerRunsPolicy, một task bị từ chối có thể chạy **ngay trên thread submit**. Vì vậy một kiến trúc dựa vào “chỉ một worker thực thi mọi thao tác trên state” có thể bị phá nếu caller cũng thực thi task. Chính sách overload phải phù hợp với **ownership** và bất biến, không chỉ với throughput.

### Queue cũng ảnh hưởng cách pool tăng worker

Với ThreadPoolExecutor nói chung:

- Nếu số worker hiện có chưa đạt corePoolSize, pool thường tạo worker mới để xử lý task.
- Khi đã đủ core worker, executor thường **thử enqueue**.
- Nếu enqueue không được và số worker còn dưới maximumPoolSize, executor có thể tạo thêm worker.
- Nếu không thể enqueue và cũng không tạo worker, task sẽ bị từ chối.

Điều này giải thích vì sao **maximumPoolSize không có tác dụng mở rộng lên trên core** đối với một queue không giới hạn mà vẫn nhận enqueue. Muốn điều chỉnh số worker linh hoạt phải hiểu cả **core/max và loại queue**. Tránh đặt max rất lớn nhưng giữ queue unbounded rồi tin pool sẽ tự scale worker khi queue dồn.

## 10. submit(), execute() và cách xử lý exception

### execute(Runnable)

**execute()** giao Runnable cho executor. Nếu Runnable ném unchecked exception, việc xử lý exception phụ thuộc đường thực thi, thread factory/handler và ThreadPoolExecutor; lỗi có thể tới uncaught exception handler của worker thread, và worker bị thay. **Không được giả định caller của execute() trực tiếp bắt được exception bên trong task.**

### submit(Runnable/Callable)

**submit()** trả Future. Exception xảy ra trong task thường được lưu trong Future; caller gọi **Future.get()** sẽ nhận **ExecutionException** và xem cause. Nếu bỏ qua Future, lỗi task có thể không được người gửi nhìn thấy qua luồng điều khiển bình thường.

### Ví dụ 5: bắt lỗi từ Future.get()

Tệp **FutureFailureDemo.java**:

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureFailureDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);

        try {
            Future<Integer> result = pool.submit(() -> {
                throw new IllegalArgumentException("invalid input");
            });

            try {
                result.get();
            } catch (ExecutionException ex) {
                System.out.println(
                    "cause = " + ex.getCause().getClass().getSimpleName()
                );
            }
        } finally {
            pool.shutdown();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 FutureFailureDemo.java
java FutureFailureDemo
~~~

Stdout:

~~~text
cause = IllegalArgumentException
~~~

**Future.get()** còn có thể ném InterruptedException nếu caller đang đợi bị ngắt; Future bị cancel có thể ném CancellationException. Không coi isDone() là thành công: **done bao gồm thành công, thất bại và hủy**.

### Thứ tự gửi không đồng nghĩa thứ tự hoàn tất

Fixed/cached executor có thể chạy task song song, nên task được submit sau vẫn có thể hoàn tất trước. Muốn lấy kết quả theo thứ tự đã lưu, main có thể giữ danh sách Future và gọi get() theo index; đó là **thứ tự thu kết quả**, không chứng minh task chạy tuần tự. Nếu muốn phản hồi theo **thứ tự hoàn thành thực tế**, xem **ExecutorCompletionService** hoặc cơ chế composition phù hợp.

Single-thread executor chạy tuần tự các task đã được xếp theo thứ tự vào executor, nhưng không thể suy ra thứ tự của các lời submit diễn ra đồng thời giữa nhiều producer chỉ từ tên executor.

## 11. Vòng đời: shutdown, shutdownNow và awaitTermination

ExecutorService cần có vòng đời rõ ràng. Trên Java 17, nên dùng try/finally để đảm bảo gọi phương thức shutdown khi không còn dùng pool.

| API | Hợp đồng cơ bản |
| --- | --- |
| **shutdown()** | Ngừng nhận task mới, tiếp tục xử lý task đã được chấp nhận trước shutdown |
| **shutdownNow()** | Cố dừng các task đang chạy (thường bằng interrupt), trả danh sách task chưa bắt đầu thực thi |
| **isShutdown()** | Đã được yêu cầu shutdown chưa |
| **isTerminated()** | Tất cả task đã kết thúc sau shutdown chưa |
| **awaitTermination(timeout, unit)** | Chờ tối đa thời gian chỉ định cho executor kết thúc; trả boolean |
| **submit sau shutdown** | Thường bị từ chối theo rejection policy |

**shutdown() không block** cho đến khi mọi task xong; nó chỉ bắt đầu quá trình đóng. **shutdownNow() không đảm bảo dừng được task đang chạy**, vì tác vụ có thể không xử lý interrupt. Danh sách trả về của shutdownNow() có thể chứa task đã chờ trong queue; policy gọi lại/ghi nhận những task này phải do ứng dụng tự quyết định.

Một ví dụ shutdown hợp tác:

~~~java
// Đoạn minh họa trong phương thức throws InterruptedException:
pool.shutdown();

if (!pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
    java.util.List<Runnable> pending = pool.shutdownNow();
    // Ghi nhận/sắp xếp xử lý pending theo yêu cầu nghiệp vụ.
    if (!pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
        // Ghi nhận rằng vẫn còn task không kết thúc.
    }
}
~~~

Không có main() trong đoạn này; biến pool cần là ExecutorService đã khởi tạo. Với hệ thống thật, nếu thread đang thực hiện I/O không interruptible, việc gọi shutdownNow không chắc làm nó kết thúc. **Dùng timeout không có nghĩa task đã bị hủy và side effect đã rollback**.

### Không tự chặn mình khi shutdown

Nếu một task chạy trong single-thread executor lại submit một task phụ **vào chính executor ấy** rồi gọi Future.get() để đợi task phụ, worker duy nhất đang bị task hiện tại chiếm nên task phụ không được chạy. Đây là **thread-starvation deadlock**.

Tương tự, cố chờ executor chấm dứt ngay từ một worker của chính executor, trong khi nó còn phải chờ worker ấy kết thúc, có thể dẫn tới vòng chờ. Phân tích **dependency graph**, không chỉ số thread.

## 12. Hủy tác vụ và interrupt

**Future.cancel(true)** yêu cầu hủy và có thể interrupt thread đang chạy tác vụ. Nó **không** cưỡng chế dừng ngay, không xóa thao tác I/O đã thực hiện và không rollback transaction. **cancel(false)** không yêu cầu ngắt task đang chạy nhưng vẫn có thể đánh dấu Future bị hủy theo hợp đồng.

Một task muốn hỗ trợ hủy nên:

- Có các điểm kiểm tra **Thread.currentThread().isInterrupted()** khi thực hiện vòng lặp lâu.
- Xử lý **InterruptedException** từ blocking APIs: propagate hoặc khôi phục interrupt flag và thoát theo policy.
- Giải phóng lock/permit/stream/resource đúng đường finally.
- Không gọi thao tác bên ngoài không idempotent vô kiểm soát trong đường retry.
- Phân biệt **request timeout** với **task thực sự đã ngừng** và **side effect đã commit**.

### Ví dụ 6: cancel task đang chờ latch

Tệp **ExecutorCancelDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExecutorCancelDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean();

        try {
            Future<?> task = pool.submit(() -> {
                entered.countDown();
                try {
                    neverReleased.await();
                } catch (InterruptedException ex) {
                    sawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                }
            });

            entered.await();
            boolean cancelled = task.cancel(true);

            pool.shutdown();
            boolean terminated = pool.awaitTermination(
                    5, TimeUnit.SECONDS
            );

            System.out.println("cancel accepted = " + cancelled);
            System.out.println("worker interrupted = " + sawInterrupt.get());
            System.out.println("pool terminated = " + terminated);
        } finally {
            pool.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ExecutorCancelDemo.java
java ExecutorCancelDemo
~~~

Stdout khi worker nhận interrupt và kết thúc theo giao thức (môi trường chạy bình thường):

~~~text
cancel accepted = true
worker interrupted = true
pool terminated = true
~~~

**Giải thích:**

- entered.await() đảm bảo task đã bắt đầu trước khi main gọi cancel.
- Worker chờ CountDownLatch không được phát tín hiệu, nên cancel(true) là yêu cầu interrupt phù hợp cho blocking wait đó.
- Worker bắt InterruptedException, ghi cờ quan sát và kết thúc.
- awaitTermination kiểm tra việc executor đã chấm dứt; caller chỉ in trạng thái sau khi đã yêu cầu shutdown và chờ.
- Future được đánh dấu canceled **không đồng nghĩa một tác vụ bất kỳ luôn nhận interrupt thành công**. Ví dụ này cố ý chọn CountDownLatch.await() để minh họa đường hủy hợp tác.

**Thực tiễn:** Nếu timeout 5 giây xảy ra do môi trường rất bận hoặc worker không phản hồi, pool terminated có thể là false; không dùng một khoảng chờ số học cố định để chứng minh JVM luôn kết thúc đúng deadline. Thông điệp ở đây là phải **kiểm tra kết quả awaitTermination**, chứ không mặc định thành công.

## 13. Java Memory Model: gửi task và lấy Future kết quả

ExecutorService có quy tắc memory consistency quan trọng:

- Các hành động trong thread **trước khi gửi Runnable/Callable tới ExecutorService** happens-before các hành động của task đó.
- Các hành động trong task **happens-before** hành động của thread khác **sau khi lấy kết quả bằng Future.get()**.

Nhờ đó, việc truyền input đã chuẩn bị trước submit và đọc output sau Future.get có cơ sở JMM.

~~~text
Producer thread                        Worker thread
----------------                       -------------
prepare input
submit(task)        --- happens-before -> task đọc input
                                         task tạo result
future.get()         <- happens-before -- task kết thúc
consume result
~~~

Tuy nhiên, **không được sửa input mutable đồng thời với task đang đọc** nếu không có đồng bộ hóa bổ sung. Happens-before của submit không cho phép producer tiếp tục viết không đồng bộ vào cùng object sau khi đã submit rồi mong worker thấy snapshot nhất quán.

Single-thread executor cũng không tự bảo vệ code bên ngoài executor truy cập state của worker. Trạng thái business chỉ thực sự confined nếu chỉ owner worker sửa nó và giao tiếp với thread khác qua kênh có hợp đồng rõ ràng.

## 14. Sử dụng task độc lập và state được chia sẻ

Một pool cho phép nhiều task **cùng chạy**, nhưng không đảm bảo safe access đến shared mutable state:

~~~java
// Anti-pattern minh họa khi pool có nhiều worker:
java.util.HashMap<String, Integer> totals = new java.util.HashMap<>();

pool.submit(() -> totals.put("a", 1));
pool.submit(() -> totals.put("b", 2));
~~~

Hai put vào HashMap có thể diễn ra đồng thời; HashMap không thread-safe theo hợp đồng. Không sửa bằng cách thêm Thread.sleep trong một task. Lựa chọn tùy bất biến:

- Nếu chỉ cần map hỗ trợ các operation concurrent, cân nhắc **ConcurrentHashMap** và các operation có ngữ nghĩa nguyên tử tương ứng.
- Nếu phải cập nhật nhiều field cùng nhau, dùng **lock** bảo vệ toàn giao dịch trong JVM.
- Nếu có thể, chuyển state về **single-thread owner** và gửi command tới owner.
- Nếu task độc lập, trả về immutable result rồi gộp kết quả ở một thread sau Future.get.

**Không suy ra mọi chuỗi operation trên ConcurrentHashMap cũng nguyên tử**: containsKey rồi put vẫn là check–then–act nếu không dùng operation có điều kiện như putIfAbsent/computeIfAbsent đúng hợp đồng.

### Khi nào single-thread giúp tránh lock?

Nếu một object mutable chỉ được chạm tới bởi các task chạy trên cùng single-thread executor, các thao tác trên object đó không có truy cập đồng thời từ các task của executor. Ta có thể giảm nhu cầu lock bên trong object. Nhưng nếu producer cầm tham chiếu tới object ấy và sửa trực tiếp, hoặc dùng CallerRunsPolicy khiến task chạy trên thread gọi, mô hình ownership bị phá.

### Khi nào fixed/cached cần atomic hoặc lock?

Khi task trong pool chia sẻ trạng thái mutable, mọi truy cập liên quan cần giao thức phù hợp: atomic increment cho counter, CAS cho trạng thái một biến, lock cho bất biến nhiều bước, hoặc message passing để tránh chia sẻ.

## 15. Không nhầm tên pool với thread identity và ThreadLocal

Cả fixed, cached và single-thread executor **tái sử dụng thread** theo chính sách của mình. Những lỗi thường gặp:

- Task A lưu ngữ cảnh vào ThreadLocal mà quên **remove()**; task B chạy sau trên cùng worker có thể thấy ngữ cảnh còn sót.
- Tên thread hoặc id không phải ID request: một worker có thể chạy rất nhiều task.
- Single-thread executor có thể thay worker nếu worker cũ chết; không nên lưu trạng thái nghiệp vụ chỉ trong ThreadLocal và mong không bao giờ mất.
- Cached pool có thể tạo thread mới cho task đến khi không còn worker rảnh, nên dữ liệu ThreadLocal của task trước không đảm bảo xuất hiện.
- Fixed pool có N thread nhưng không có cam kết task nào luôn thuộc cùng một worker cụ thể.

Với ngữ cảnh task, cách an toàn là **truyền dữ liệu cần thiết trong task object** hoặc đặt/clear ThreadLocal theo scope trong try/finally, nếu thật sự cần ThreadLocal. Mọi context được share qua reference mutable vẫn cần tính đến safe publication và ownership.

## 16. Sizing pool và nguyên tắc đo đạc

### CPU-bound

Với tác vụ tiêu tốn CPU liên tục, số worker gần với số CPU thực sự được cấp là **điểm bắt đầu để đo**. Quá nhiều worker CPU-bound có thể tăng context switching, cache contention và giảm hiệu năng. Không lấy Runtime.availableProcessors() làm công thức tuyệt đối: JVM có thể chịu quota container, hyper-threading, tác vụ hỗn hợp và ràng buộc của ứng dụng.

### Blocking I/O

Task có thể dành nhiều thời gian chờ socket/file/database. Có thể cần nhiều worker hơn số CPU, nhưng vẫn phải giới hạn **tài nguyên thật** như số connection, memory, remote rate limits và queue backlog. Cached pool không phải câu trả lời tự động cho mọi bài toán I/O.

### Single-thread

Một worker tốt khi correctness cần tuần tự và tải phù hợp với năng lực xử lý của một thread. Nhưng nó sẽ có head-of-line blocking khi task kéo dài. Nên đo **queue latency**, thời gian xử lý task và backlog, không chỉ CPU.

### Đo metric nào?

| Metric | Tại sao hữu ích |
| --- | --- |
| Throughput (task/giây) | Năng lực xử lý thực tế |
| Active worker count | Mức concurrency và contention |
| Queue size/age | Tải tồn đọng và nguy cơ latency |
| Rejection count | Quá tải đã vượt chính sách chấp nhận |
| Task execution latency | Thời gian chạy của task |
| Queue waiting latency | Thời gian từ submit tới bắt đầu chạy |
| Tail latency p95/p99 | Người dùng cuối bị ảnh hưởng khi tải dồn |
| Heap/thread count | Rủi ro memory/thread exhaustion |
| Error/cancel/timeout count | Task kết thúc vì gì, không chỉ isDone |

Không kết luận fixed nhanh hơn cached hoặc ngược lại chỉ từ một benchmark đơn. Hãy kiểm tra nhiều cỡ task, CPU, thời gian block, tốc độ producer, warmup/JIT và mức giới hạn tài nguyên. Có thể kết hợp **bounded concurrency** với cơ chế khác thay vì chọn chỉ một factory.

## 17. Deadlock và chờ vòng trong executor

### Single-thread task đợi task khác trên cùng pool

~~~java
// Anti-pattern minh họa; nếu đặt trong task chạy trên single pool:
Future<?> child = sameSinglePool.submit(() -> doChildWork());
child.get(); // Worker duy nhất đang đợi chính task chưa thể chạy.
~~~

Ví dụ không độc lập: sameSinglePool và doChildWork() là thành phần giả định; child.get() cần checked-exception handling. Nếu chỉ có một worker, task con bị queue phía sau task cha nhưng task cha lại đợi con; đó là **thread-starvation deadlock**.

### Fixed pool bị chiếm toàn bộ bởi các task chờ phụ thuộc

Không chỉ single-thread mới có vấn đề. Một fixed pool 4 worker có thể bị 4 task cha chiếm hết, mỗi cha lại submit task con vào cùng pool rồi chờ Future.get. Tất cả task con chờ queue, không còn worker để xử lý. Tăng pool lên 8 có thể chỉ đẩy ngưỡng lỗi nếu thiết kế phụ thuộc còn vòng.

### Chờ Future trong lúc giữ lock

Thread A giữ lock L rồi gọi Future.get chờ worker; worker muốn lấy lock L mới hoàn tất. Vòng chờ không giải quyết được bởi việc đổi cached thành fixed. Cần phân tích dependency graph, bỏ chờ bên trong critical section khi có thể hoặc thiết kế workflow bất đồng bộ phù hợp.

### Khi nào nên tách executor?

Một task parent cần chờ child thuộc một loại tài nguyên/chờ khác có thể được tổ chức ở executor khác, hoặc nối kết quả bất đồng bộ để không chiếm worker chờ vòng. Nhưng tạo thêm executor không tự sửa logic dependency sai, không thay thế timeout/cleanup và không có nghĩa cứ càng nhiều pool càng tốt.

## 18. Những nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| Fixed pool chỉ nhận tối đa N task | N giới hạn số worker hoạt động; queue có thể nhận nhiều task hơn |
| Fixed pool luôn có sẵn đúng N Thread object từ lúc tạo | Worker có thể được tạo theo nhu cầu; có thể được thay khi lỗi |
| Cached pool giới hạn thread bằng số CPU | Không; cached có thể tạo rất nhiều platform thread |
| Cached pool giữ backlog trong LinkedBlockingQueue | Mẫu mặc định dùng SynchronousQueue handoff |
| Single-thread executor có một Thread object bất biến mãi mãi | Worker có thể được thay, nhưng contract tuần tự còn được giữ |
| Single-thread nghĩa nhiều thread submit sẽ có thứ tự nghiệp vụ xác định | Concurrent submitters cần quy tắc ordering riêng |
| Một pool có worker nghĩa task luôn được chạy ngay | Task có thể ở queue hoặc bị từ chối |
| Future.isDone() đồng nghĩa task thành công | Done cũng có thể là failure hoặc cancellation |
| submit() tự in lỗi khi task ném exception | Exception thường được lưu trong Future và thấy qua get() |
| shutdown() đợi tất cả task kết thúc trước khi trả về | Nó khởi động shutdown, không tự block |
| shutdownNow() chắc chắn dừng task ngay | Thường chỉ gửi interrupt/cố dừng; task cần hợp tác |
| Future.get(timeout) timeout nghĩa worker đã bị hủy | Nó chỉ giới hạn thời gian chờ của caller |
| Factory mặc định đã có backpressure | Fixed/single có queue không giới hạn thực dụng; cached tăng worker |
| Đặt maximumPoolSize cao hơn core luôn làm pool tự scale lên max | Unbounded queue có thể nhận hết backlog khiến pool không vượt core |
| CallerRunsPolicy chỉ làm ứng dụng chậm lại | Nó có thể chuyển nơi thực thi task, phá giả định owner thread |
| Một pool làm mọi shared state thread-safe | Phải dùng atomic/lock/confinement cho dữ liệu |
| Dùng ThreadLocal là dữ liệu thuộc executor | ThreadLocal thuộc thread cụ thể, không thuộc executor hoặc request |

## 19. Checklist khi chọn executor

**Đúng đắn:**

- [ ] Các task có độc lập hay cùng sửa mutable state?
- [ ] Có cần **tuần tự hóa** hay cho phép hoàn thành không theo thứ tự?
- [ ] Caller có thể đồng thời submit không, và quy tắc thứ tự task là gì?
- [ ] Có task nào submit task con rồi chờ kết quả trong cùng pool?
- [ ] Có callback hoặc side effect nào bị gọi khi đang giữ lock?
- [ ] ThreadLocal có được đặt và remove đúng scope từng task không?

**Tải và tài nguyên:**

- [ ] Loại task là CPU-bound hay blocking, và thời gian chờ điển hình là bao lâu?
- [ ] Biết concurrency mục tiêu, queue capacity và giới hạn connection/thread?
- [ ] Có backpressure hoặc rejection policy rõ khi quá tải?
- [ ] Có metric queue age, active count, rejection, timeout và error?
- [ ] Đã kiểm tra tail latency dưới burst/overload chưa?

**Vòng đời và lỗi:**

- [ ] Executor được shutdown khi hết vòng đời?
- [ ] Có awaitTermination hoặc cơ chế theo dõi shutdown phù hợp?
- [ ] Có chính sách khi task không phản hồi interrupt?
- [ ] Có bắt lỗi qua Future.get, CompletionService hoặc kênh báo lỗi khác?
- [ ] Timeout/cancel không bị nhầm với completion của side effect?
- [ ] Task được submit khi executor đã shutdown sẽ được xử lý thế nào?

## 20. Câu hỏi tự kiểm tra

**1. newFixedThreadPool(4) có chặn submit task thứ năm không?**  
Không theo nghĩa luôn chặn hoặc từ chối khi đã có 4 task. Thường task thứ năm được queue nếu bốn worker đang bận; default queue rất lớn. Muốn bounded backlog, dùng cấu hình/cơ chế khác.

**2. Cached pool có tự giới hạn số thread ở số core CPU không?**  
Không. Khi không có worker rảnh để nhận handoff, cached pool có thể tạo thêm platform thread rất nhiều, đến khi gặp giới hạn cấu hình hoặc tài nguyên.

**3. Single-thread executor có đảm bảo mọi task chạy trên đúng cùng một Thread object suốt đời không?**  
Không. Nó đảm bảo chỉ một task đang thực thi mỗi lúc và tuân theo thứ tự queue; thread thực tế có thể bị thay nếu worker chết.

**4. Nếu submit Callable ném exception, caller có thể lấy thông tin lỗi qua đâu?**  
Future.get() có thể ném ExecutionException chứa cause; cần kiểm tra/ghi nhận thay vì bỏ Future.

**5. ThreadPoolExecutor core=2, max=8 và queue không giới hạn có thường tăng lên 8 worker khi queue dài không?**  
Không nhất thiết. Queue tiếp tục nhận task sau khi đủ core, vì thế maximumPoolSize có thể không được dùng để mở rộng trong điều kiện bình thường.

**6. Khi nào CallerRunsPolicy nguy hiểm cho state chỉ một worker được phép sửa?**  
Khi queue đầy, task có thể chạy trên thread submit, không phải worker owner; giả định confinement bị phá.

**7. shutdown() có giống shutdownNow() không?**  
Không. shutdown không nhận task mới nhưng cho task đã nhận tiếp tục; shutdownNow cố dừng task đang chạy và trả lại task chưa bắt đầu, không bảo đảm dừng thành công.

**8. Future.get(timeout) ném TimeoutException có chứng minh task không còn chạy không?**  
Không. Chỉ caller ngừng chờ theo hạn; task có thể vẫn đang chạy.

**9. Nếu task đầu chạy chậm trong single-thread executor, task sau có chạy trước để giảm trễ không?**  
Không trong cùng executor theo thiết kế tuần tự; chúng đợi task trước, tạo head-of-line blocking.

**10. Có nên dùng cached pool cho mọi thao tác I/O không?**  
Không. Nếu blocking lâu hoặc số request không giới hạn, thread count có thể tăng quá mức. Phải đánh giá tải, tài nguyên và backpressure.

## 21. Tổng kết

**Fixed pool** phù hợp khi cần **giới hạn concurrency** ở mức N worker, nhưng phải quản lý backlog vì queue mặc định không giới hạn thực dụng. **Cached pool** phù hợp với workload task ngắn, từng đợt và có kiểm soát tải ngoài, nhưng có thể tăng số platform thread rất lớn khi tất cả worker đều bận. **Single-thread executor** phù hợp khi cần **tuần tự hóa task và một owner xử lý mutable state**, đổi lại có nguy cơ queue tăng và head-of-line blocking.

Cả ba đều là những cách triển khai của **ExecutorService**: hãy xét **task, worker, queue, rejection, cancellation, Future error và shutdown** cùng nhau. Khi yêu cầu tài nguyên hoặc backpressure chặt chẽ, có thể dùng **ThreadPoolExecutor cấu hình rõ bounded queue và rejection policy**. Executor không thay thế việc chứng minh **happens-before, atomicity và bất biến dữ liệu** của các task được thực thi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-004">Đánh giá rủi ro của queue không giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá rủi ro của queue không giới hạn trong Java Executor Framework

> **Luận điểm:** Một executor có worker hữu hạn nhưng chấp nhận gần như mọi task vào queue không giới hạn **không tự mở rộng năng lực xử lý**. Khi tốc độ nhận việc lớn hơn tốc độ hoàn thành, backlog tăng, task giữ thêm bộ nhớ, thời gian chờ tăng, request có thể hết deadline và cuối cùng JVM có thể gặp `OutOfMemoryError`. Queue lớn cũng che giấu quá tải vì `submit()` vẫn có thể trả về nhanh. Cần đánh giá **capacity, throughput, memory, latency, rejection, cancellation và shutdown** như một hệ thống.

Bài thuộc nhóm **Executor Framework** của **Java concurrency nói chung**. Các ví dụ dùng Java SE 17+ (`ThreadPoolExecutor`, `Executors`, `BlockingQueue`, `Future`); không dùng Spring.

## 1. Queue không giới hạn là gì?

Một queue được gọi là *unbounded* khi ứng dụng không đặt một dung lượng hữu hạn đủ nhỏ để kiểm soát workload. Chẳng hạn `new LinkedBlockingQueue<>()` có capacity mặc định `Integer.MAX_VALUE`: về kỹ thuật nó có giới hạn số phần tử, nhưng heap thường cạn trước khi đạt giới hạn này. Vì vậy **unbounded không có nghĩa JVM có tài nguyên vô hạn**.

Mô hình cơ bản:

~~~text
Producer threads
       |
       | execute(task) / submit(task)
       v
[ Waiting task queue ] ---> worker-1 ---> complete
                         -> worker-2 ---> complete
                         -> worker-N ---> complete

Nếu task đến > task hoàn thành trong một khoảng dài:
     queue tăng -> chờ lâu -> heap retention -> GC/timeout tăng
~~~

`execute` hoặc `submit` thành công có nghĩa task **đã được executor chấp nhận theo hợp đồng**, không phải đã bắt đầu, đã xử lý thành công hay đã trả kết quả đúng deadline. Một số task còn có thể nằm trong queue cho đến khi pool shutdown, bị hủy, hoặc task trước đó kết thúc.

### Unbounded queue không phải unbounded thread pool

| Thiết kế | Hành vi chính | Rủi ro khi quá tải |
| --- | --- | --- |
| `Executors.newFixedThreadPool(n)` | Cố định số worker, unbounded work queue | Backlog và heap tăng, latency kéo dài |
| `Executors.newSingleThreadExecutor()` | Một worker, unbounded queue, xử lý tuần tự | Head-of-line blocking, chờ rất lâu |
| `Executors.newCachedThreadPool()` | Sử dụng `SynchronousQueue` để handoff; có thể tạo nhiều worker | Bùng nổ số thread và tài nguyên native/CPU |
| `ThreadPoolExecutor` với `ArrayBlockingQueue(cap)` | Queue hữu hạn, worker min/max | Cần xử lý reject/backpressure rõ ràng |
| `ScheduledThreadPoolExecutor` | Hàng đợi nhiệm vụ theo lịch/delay và contract riêng | Retention của scheduled/cancelled tasks, backlog theo thời gian |
| `ForkJoinPool` | Work-stealing và cơ chế riêng | Không áp đặt quy tắc mở worker của `ThreadPoolExecutor` cho nó |

Không nên nhìn thấy từ “Executor” rồi mặc định mọi implementation dùng cùng một kiểu queue.

## 2. Factory method nào dễ dẫn đến backlog?

~~~java
// Snippet API, không phải một chương trình độc lập.
ExecutorService fixed = Executors.newFixedThreadPool(8);
ExecutorService single = Executors.newSingleThreadExecutor();
~~~

`newFixedThreadPool(8)` cung cấp tối đa 8 worker tái sử dụng và **shared unbounded queue** cho task chờ. `newSingleThreadExecutor` có một worker và unbounded queue. Chúng tiện lợi cho ví dụ nhỏ hoặc một số workload được giới hạn đầu vào, nhưng **không cung cấp backpressure tự động** trước khi heap chạm giới hạn.

Một cấu hình dễ gây hiểu lầm:

~~~java
// Snippet mô tả cấu hình ThreadPoolExecutor.
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        4, 16, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>()
);
~~~

Lập trình viên đặt `maximumPoolSize=16` rồi kỳ vọng khi tải tăng, pool tạo tới 16 worker. Trên thực tế, chiến lược `ThreadPoolExecutor` thường:

1. Khi worker count chưa đạt `corePoolSize`, cố gắng tạo core worker cho task.
2. Sau khi đạt core, ưu tiên `workQueue.offer(task)` nếu queue nhận được.
3. Chỉ khi không thể queue task mới mới xem xét tạo non-core worker tới `maximumPoolSize`.
4. Nếu vẫn không thể nhận, áp dụng `RejectedExecutionHandler`.

Với `LinkedBlockingQueue<>()` có capacity cực lớn, bước 2 thường thành công, nên worker thường chỉ ở gần **core=4**, còn task mới nối dài queue. Không nên gọi `maximumPoolSize` là hoàn toàn vô tác dụng trong mọi ngữ cảnh; nó chỉ **hiếm được kích hoạt vì lựa chọn queue**.

### Capacity là một phần của thuật toán pool

~~~text
new task
    |
    v
workerCount < corePoolSize ?
    | yes -> tạo core worker nếu có thể
    | no
    v
Executor còn RUNNING và queue.offer(task) được?
    | yes -> queue chờ worker
    | no
    v
Thử tạo worker thêm tới maximumPoolSize
    | success -> chạy task
    | failure -> rejection policy
~~~

Đây là mô hình ra quyết định tổng quát; implementation còn kiểm tra lại trạng thái executor sau enqueue để xử lý race với shutdown. Dù vậy nó đủ để lý giải **vì sao một unbounded queue làm `maximumPoolSize` khó phát huy tác dụng**.

## 3. Rủi ro 1 — tốc độ đến vượt tốc độ xử lý

Đặt:

- `λ`: số task được gửi/được chấp nhận trung bình mỗi giây.
- `μ`: số task pool hoàn thành trung bình mỗi giây ở tải đang xét.
- `Q`: số task đang chờ.

Nếu `λ > μ` trong thời gian dài, backlog sẽ tăng theo phần chênh lệch, trừ khi có cancellation, deduplication, rate limit hoặc thay đổi workload. Nếu `λ < μ` sau một burst, pool có thể drain queue, nhưng task vẫn đã phải đợi trước đó.

Ví dụ: 1.000 task đến mỗi giây, pool xử lý được 800 task mỗi giây. Backlog tăng khoảng **200 task/giây** khi điều kiện này được duy trì. Sau mười phút có thể tồn đọng khoảng **120.000 task**, trước khi tính retry và các thay đổi tải.

Đây chỉ là phép tính minh họa, không phải công thức dự báo cứng cho workload có burst, varying task duration, GC pauses và downstream saturation. Nhưng nó phản ánh giới hạn căn bản: queue **lưu công việc chứ không tự xử lý thêm công việc**.

### Vì sao overload có thể không xuất hiện ở chỉ số tiếp nhận?

Khi queue nhận task nhanh, `execute()` không block. Một API handler có thể trả về “đã nhận” trong vài micro-giây trong khi task thực sự phải chờ lâu trước khi worker bắt đầu.

Hãy đo riêng:

| Metric | Câu hỏi |
| --- | --- |
| Accepted/sec | Có bao nhiêu task đã nhận? |
| Completed/sec | Có bao nhiêu task thực sự hoàn tất? |
| Rejected/sec | Có bao nhiêu task bị từ chối? |
| Queue length / slope | Backlog đang giảm sau burst hay tăng đều? |
| Queue waiting time | Task đợi bao lâu từ submit đến start? |
| Task execution time | Khi đã bắt đầu, task mất bao lâu? |
| Failure/cancel/timeout | Task có còn giá trị hay đã thất bại? |

Chỉ nhìn **accepted** hoặc “submit không throw” là không đủ để đánh giá sức khỏe executor.

## 4. Rủi ro 2 — task giữ lại bộ nhớ và gây GC pressure

Queue chứa `Runnable` hoặc `FutureTask`; một task có thể giữ reference tới:

- Payload request, `byte[]`, JSON tree, buffer và object được parse.
- Lambda capture `this`, context, collection hoặc thông tin nghiệp vụ.
- `Callable` và cấu trúc kết quả/exception của `Future`.
- Object graph lớn vốn chỉ nên sống ngắn nếu task xử lý ngay.
- Dữ liệu của request đã timeout ở caller nhưng task còn tồn tại trong queue.

Nếu executor còn sống, nó giữ queue; queue giữ task; task giữ các object reachable theo đường reference đó. Garbage collector **không thể thu hồi** object vẫn reachable chỉ vì phía client không còn chờ kết quả.

Hệ quả:

1. Used heap tăng theo backlog, đặc biệt retained heap của captured objects.
2. Tần suất GC hoặc pause có thể tăng, làm giảm công suất xử lý thực tế.
3. Công suất giảm lại khiến backlog tăng thêm — một vòng phản hồi xấu.
4. JVM có thể gặp `OutOfMemoryError: Java heap space` trước khi queue đạt capacity lý thuyết.
5. Outage có thể đến muộn: queue đã tích dữ liệu từ lâu nhưng chỉ biểu hiện ở đợt tải tiếp theo.

**Đừng dùng số entry nhân một kích thước lambda giả định** để kết luận memory an toàn. Một lambda nhỏ có thể giữ cả một object graph lớn. Dùng heap profiler/dump và dữ liệu task thật để ước lượng retained bytes trên mỗi queued task.

`LinkedBlockingQueue` default capacity `Integer.MAX_VALUE` là giới hạn **số phần tử**, không phải memory limit cho ứng dụng.

## 5. Rủi ro 3 — độ trễ end-to-end tăng

Một task có thời gian thực thi 10 ms nhưng chờ trong queue 5 s sẽ có latency nhiều giây. Nói gần đúng:

~~~text
Total time = queue waiting time + running time + scheduling/other overhead
~~~

Nếu task phải hoàn tất trong 300 ms, một queue chứa hàng nghìn task có thể đã làm SLA **không khả thi trước khi task được bắt đầu**.

Theo Little's Law ở steady state dưới các giả thiết phù hợp: `L = λ × W`, trong đó `L` là số công việc trung bình trong hệ thống, `λ` là throughput/arrival rate tương thích và `W` là thời gian trung bình nằm trong hệ thống. Khi xét riêng queue thì phải dùng **queue length** và **queue waiting time** tương ứng, không trộn với số task active. Khi backlog đang tăng mãi, hệ thống chưa ở steady state nên không thể suy ra kết quả dài hạn đơn giản từ công thức đó.

### Head-of-line blocking

Trong FIFO queue, một task chậm hoặc blocking chiếm worker có thể làm các task phía sau chờ rất lâu:

~~~text
worker-1: blocking remote call ...
worker-2: blocking remote call ...
queue:    [fast1, fast2, fast3, ...]
~~~

Thêm dung lượng queue không làm hai worker bớt block. Cần tìm nguyên nhân giảm service rate: I/O không có timeout, locks, downstream DB/API quá tải, pool dùng chung cho task quá khác nhau, hoặc dependency cycle.

### Thread-pool starvation deadlock

Một task trong single-thread executor submit thêm một task **vào chính pool** rồi chờ `Future.get()` có thể tự kẹt: worker duy nhất bị chiếm bởi task ngoài, task trong queue không có worker để chạy.

~~~java
// Phản ví dụ minh họa; đừng chạy trực tiếp vì có thể kẹt.
ExecutorService pool = Executors.newSingleThreadExecutor();

pool.submit(() -> {
    Future<Integer> nested = pool.submit(() -> 42);
    return nested.get(); // Worker đợi task mà pool chưa thể chạy.
});
~~~

Đây là dependency deadlock, không phải riêng queue không giới hạn. Nhưng queue cho phép nhận thêm task mà không thể tiến triển, che giấu tình trạng nghẽn.

## 6. Ví dụ 1 — queue tăng dù maximumPoolSize lớn

Ví dụ được **điều phối bằng latch**, không tạo hàng triệu object để cố tình OOM. Một worker phải chờ; 5 task tiếp theo được enqueue; dù maximumPoolSize là 4, executor vẫn chỉ có một worker vì queue nhận task.

Tạo `UnboundedQueueDemo.java`, Java 17+:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UnboundedQueueDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            started.await();

            for (int i = 0; i < 5; i++) {
                pool.execute(() -> {});
            }

            System.out.println("max = " + pool.getMaximumPoolSize());
            System.out.println("workers = " + pool.getPoolSize());
            System.out.println("queued = " + pool.getQueue().size());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
        System.out.println("completed = " + pool.getCompletedTaskCount());
    }
}
~~~

Chạy:

~~~bash
javac UnboundedQueueDemo.java
java UnboundedQueueDemo
~~~

Stdout khi thực hiện bình thường:

~~~text
max = 4
workers = 1
queued = 5
completed = 6
~~~

`started.await()` xác nhận worker đang chạy và kẹt tại `release.await()`. Năm task tiếp theo được queue nhận; pool không tăng worker trên core vì queue còn chỗ. `release.countDown()` cho worker hoàn tất rồi chạy tất cả task đã nhận; `awaitTermination()` đợi trước khi in completed count.

Không phải cứ tăng `maximumPoolSize` là giải quyết được backlog khi dùng queue không giới hạn.

## 7. Queue hữu hạn và các điều kiện admission

Một phương án phổ biến là dùng `ArrayBlockingQueue` hoặc `LinkedBlockingQueue` với capacity được đặt rõ:

~~~java
// Snippet cấu hình; không phải chương trình độc lập.
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        2, 4,
        30L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(100),
        new ThreadPoolExecutor.AbortPolicy()
);
~~~

Khi queue đầy và pool chưa đạt max, executor có thể tạo worker bổ sung. Khi queue không nhận được và không thể mở worker thêm, **task bị reject** theo `RejectedExecutionHandler`.

**Capacity=100 chỉ là ví dụ**, không phải con số chuẩn. Dung lượng cần phụ thuộc kích thước retained task, heap budget, throughput, waiting-time SLO, burst size và số worker/downstream concurrency limit. Một queue cực nhỏ có thể reject quá sớm dưới burst hợp lệ; một queue quá lớn có thể hấp thụ request đã hết giá trị.

Lưu ý capacity nói về **task chờ**, không phải tất cả task đang chạy. Tổng concurrency/in-flight bao gồm task active, task queued và có thể những task ở các tầng pipeline khác.

## 8. Ví dụ 2 — bounded queue hiện rõ overload qua rejection

Tạo `BoundedQueueDemo.java`, Java 17+:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BoundedQueueDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            started.await();
            pool.execute(() -> {});
            pool.execute(() -> {});

            boolean rejected = false;
            try {
                pool.execute(() -> {});
            } catch (RejectedExecutionException expected) {
                rejected = true;
            }

            System.out.println("queued = " + pool.getQueue().size());
            System.out.println("rejected = " + rejected);
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
        System.out.println("completed = " + pool.getCompletedTaskCount());
    }
}
~~~

Chạy:

~~~bash
javac BoundedQueueDemo.java
java BoundedQueueDemo
~~~

Stdout bình thường:

~~~text
queued = 2
rejected = true
completed = 3
~~~

Một worker đang block, hai task nằm trong queue dung lượng 2; task thứ tư không còn chỗ và không thể tạo thêm worker vì max=1. `AbortPolicy` ném exception thay vì nhận backlog không giới hạn. Sau khi nhả latch, ba task được chấp nhận hoàn tất.

**Bounded queue không tạo thêm năng lực xử lý**, nhưng cho phép ứng dụng **phát hiện quá tải có kiểm soát**. Cần thiết kế caller xử lý `RejectedExecutionException`: từ chối, retry có backoff, thông báo client, ghi dead-letter khi cần, hoặc làm việc tại tầng phù hợp.

## 9. Chính sách rejection và tradeoff

`ThreadPoolExecutor` có các `RejectedExecutionHandler` built-in:

| Policy | Hành vi khi pool không nhận task | Cần cảnh giác |
| --- | --- | --- |
| `AbortPolicy` | Ném `RejectedExecutionException` | Caller cần xử lý, thích hợp để không mất việc âm thầm |
| `CallerRunsPolicy` | Nếu pool **chưa shutdown**, chạy task trên thread gọi `execute` | Có backpressure, nhưng tăng latency của caller và thay đổi execution context |
| `DiscardPolicy` | Bỏ task im lặng | Có thể mất việc không biết, chỉ hợp khi nghiệp vụ cho phép |
| `DiscardOldestPolicy` | Cố bỏ task ở đầu queue rồi submit lại | Mất work cũ, không phù hợp nếu thứ tự/ưu tiên quan trọng |

**Chi tiết quan trọng:** `CallerRunsPolicy` **bỏ task** khi executor đã shutdown. Vì vậy nó không bảo đảm “task nào cũng sẽ được thực hiện”. Nó chỉ là một chiến lược tạo feedback khi pool còn nhận task.

`DiscardOldestPolicy` không có nghĩa bỏ task “kém ưu tiên nhất” trong mọi queue. Với priority queue, phần tử đầu queue có ý nghĩa tùy comparator; với FIFO, đó thường là phần tử chờ lâu nhất.

### CallerRunsPolicy có thể không hợp trong request thread

Nếu task chậm vài giây, chính request/event-loop thread có thể phải thực thi và block theo. Context như thread-local, transaction, security principal hoặc lock đang giữ có thể khác worker. Chỉ dùng policy này khi **chạy inline** là hành vi hợp lệ và được chấp nhận về latency.

## 10. Ví dụ 3 — CallerRunsPolicy thực sự chạy trên submitter

Tạo `CallerRunsDemo.java`, Java 17+:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CallerRunsDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread submitter = Thread.currentThread();
        AtomicBoolean ranInCaller = new AtomicBoolean();

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy());

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            started.await();

            pool.execute(() -> {}); // Slot queue đã được chiếm.
            pool.execute(() -> ranInCaller.set(
                    Thread.currentThread() == submitter));

            System.out.println("caller ran = " + ranInCaller.get());
            System.out.println("queued = " + pool.getQueue().size());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac CallerRunsDemo.java
java CallerRunsDemo
~~~

Stdout bình thường:

~~~text
caller ran = true
queued = 1
~~~

Vì worker đang bận và queue capacity 1 đã đầy, task thứ ba được chạy **đồng bộ trong main thread**. `execute` chỉ trả về sau khi task này xong, nên submitter chịu feedback. Kết quả được kiểm tra bằng **Thread identity**, không đoán tên thread hoặc lịch OS.

Khi chọn policy, hãy định nghĩa rõ liệu client có quyền chờ lâu hơn, callback có thể chạy ở caller, và exception từ task có được truyền về caller hay không.

## 11. Rủi ro deadline, timeout và cancellation

Một request chờ `future.get(timeout, unit)` rồi gặp `TimeoutException` không có nghĩa task đã bị hủy. Task có thể:

- Vẫn nằm trong queue trong khi client đã bỏ cuộc.
- Được worker bắt đầu sau deadline, tiêu tốn tài nguyên cho kết quả không còn ai nhận.
- Đã chạy và tạo side effect, nhưng caller retry và tạo một task thứ hai.
- Chưa xử lý cancellation và tiếp tục sau `Future.cancel(true)` nếu nó không hợp tác với interruption.

Muốn quản lý deadline đúng phải có quy tắc: ai sở hữu Future, ai gọi cancel, lúc bắt đầu task có kiểm tra deadline không, task đang thực hiện side effect có idempotency không, và trạng thái thành công/thất bại được ghi nhận ở đâu.

**Timeout việc chờ không phải timeout việc thực thi.** `Future.get(100, MILLISECONDS)` dừng đợi sau thời gian đó, không tự ngắt worker hay xóa queue entry.

### Cancelled Future có thể vẫn nằm trong work queue

Task được gửi bằng `submit` thường có wrapper `FutureTask`. `Future.cancel()` thay đổi trạng thái Future; nó không nhất thiết xóa wrapper khỏi queue ngay. `ThreadPoolExecutor.purge()` được cung cấp để loại các cancelled Future tasks còn trong work queue, nhưng có chi phí và không thay thế bounded admission.

Đối với `ScheduledThreadPoolExecutor` có phương thức `setRemoveOnCancelPolicy(true)` riêng; không nhầm method đó là API của `ThreadPoolExecutor` thường.

## 12. Ví dụ 4 — cancellation khác removal khỏi queue

Tạo `CancelledQueueDemo.java`, Java 17+:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CancelledQueueDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2));

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            started.await();

            Future<?> pending = pool.submit(() -> {});
            boolean cancelled = pending.cancel(false);

            System.out.println("cancelled = " + cancelled);
            System.out.println("queue before purge = "
                    + pool.getQueue().size());

            pool.purge();
            System.out.println("queue after purge = "
                    + pool.getQueue().size());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac CancelledQueueDemo.java
java CancelledQueueDemo
~~~

Stdout bình thường:

~~~text
cancelled = true
queue before purge = 1
queue after purge = 0
~~~

Worker đang bị giữ, nên Future chưa thể bắt đầu; cancel đánh dấu Future đã hủy nhưng wrapper còn trong queue. `purge` loại bỏ nó. Đừng suy rộng hành vi implementation này cho mọi executor tùy chỉnh; hãy xem contract và instrumentation của executor đang sử dụng.

## 13. Admission control và backpressure ngoài queue bound

Bounded queue vẫn có thể nhận nhiều task hơn mức SLA cho phép. Những phương án bổ sung:

- **Rate limiting** ở producer/API layer: tránh gửi quá tốc độ xử lý bền vững.
- **Semaphore cho in-flight budget**: giới hạn tổng số task đã nhận gồm running và queued, không chỉ queue length.
- **Rejection rõ ràng**: trả tín hiệu overload và retry-after/backoff khi nghiệp vụ cho phép.
- **Caller-runs** nếu chạy inline là hợp lệ.
- **Tách pool theo workload**: CPU-bound, blocking I/O, priority cao/thấp có thể dùng tài nguyên riêng.
- **Coalescing/deduplication**: bỏ bớt thông báo trạng thái cũ không còn giá trị, theo quy tắc nghiệp vụ.
- **Deadline-aware task**: task bị expired khi bắt đầu có thể trả kết quả hủy thay vì thực hiện side effect vô ích.
- **Durable queue ngoài JVM** khi phải chịu tải vượt bộ nhớ và cần persistence, kèm quota/retention/broker backpressure; không phải cách “làm vô hạn công suất”.

### Cẩn trọng khi tự dùng Semaphore

Một pattern ngây thơ lấy permit trước `submit` rồi release trong `finally` của worker có thể **rò permit** nếu task bị cancel **trước khi thực thi**: phần `finally` của worker không bao giờ chạy. Khi submit bị reject, permit cũng cần trả; khi handler chạy task ngay trên caller, phải tránh **trả hai lần** khi exception vượt qua call stack. Một solution đúng cần một ownership/release-once protocol cho mọi trạng thái, không phải chỉ thêm `Semaphore` tùy tiện.

Nếu nhu cầu đơn giản là giới hạn queue, `ArrayBlockingQueue` với rejection rõ có thể ít lỗi hơn.

## 14. Chọn capacity từ memory budget và latency budget

Không có capacity mặc định đúng cho mọi hệ thống. Hãy xét hai giới hạn chính:

**Giới hạn bộ nhớ:** giả sử mỗi queued task giữ trung bình R byte retained heap theo đo đạc và ngân sách heap dành cho queue là M byte; capacity lý thuyết không được vượt tỷ lệ M/R, và cần headroom cho GC, task có payload lớn bất thường, worker và các hệ thống khác.

**Giới hạn độ trễ:** tốc độ hoàn thành ổn định khoảng C task/s, thời gian cho phép chờ queue khoảng W giây; một queue quá nhiều phần tử so với C×W sẽ khiến task phía cuối khó đạt deadline. Đây chỉ là ước lượng sơ bộ cho FIFO và workload tương đối đồng nhất, không phải cam kết cứng khi task duration thay đổi.

Nếu hai giới hạn cho các con số khác nhau, **giới hạn chặt hơn** là gợi ý để bắt đầu thử nghiệm. Không đặt queue capacity chỉ bằng “số lớn mà JVM cho phép”.

| Câu hỏi | Vì sao cần |
| --- | --- |
| Peak arrival rate và burst dài bao lâu? | Xác định lượng backlog thật sự cần hấp thụ |
| Completion rate khi downstream chậm? | Đo năng lực trong tình huống xấu |
| Retained heap của mỗi queued task? | Ngăn OOM trước khi capacity đạt ngưỡng |
| P95/p99 queue wait target? | Bảo vệ SLA chứ không chỉ heap |
| Timeout/cancel rate? | Tránh giữ task đã không còn giá trị |
| Rejection có chấp nhận được? | Quyết định fail-fast hay durable buffer |
| Task có idempotent không? | Tránh retry storm/double side effect |
| Shutdown deadline bao nhiêu? | Queue rất dài làm drain lâu |

## 15. Shutdown với queue lớn

`ExecutorService.shutdown()` ngừng nhận task mới và cho task đã được nhận tiếp tục theo hợp đồng, bao gồm những task còn chờ. Với queue khổng lồ, graceful shutdown có thể mất rất lâu.

`shutdownNow()` là yêu cầu hủy best-effort: cố interrupt task đang chạy và trả danh sách task chưa bắt đầu. Nó **không** đảm bảo thread running dừng tức thì và không tự bền vững hóa các task còn chờ. Chủ sở hữu phải có policy ghi nhận/requeue/cancel phù hợp.

`awaitTermination(timeout, unit)` chỉ đợi trạng thái terminated, không tự sửa backlog hoặc hủy task. Nếu task đang kẹt ở I/O không phản hồi interrupt, executor có thể không kết thúc trong timeout.

~~~java
// Snippet shutdown điều phối, cần xử lý InterruptedException ở caller.
pool.shutdown();
if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
    java.util.List<Runnable> neverStarted = pool.shutdownNow();
    // Theo nghiệp vụ: ghi nhận, hủy hoặc chuyển những task chưa chạy.
    if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
        // Còn task không phản hồi interrupt: alert/escalation.
    }
}
~~~

Không được biến bài toán overload thành “shutdown nhanh bằng cách bỏ hết backlog” nếu work phải được xử lý chính xác. Phải thiết kế rõ acceptable loss và retry/durability.

## 16. Observability: metric nào cần theo dõi?

`ThreadPoolExecutor` cung cấp các getter như:

- `getQueue().size()`: số task đang chờ trong queue **tại thời điểm đo**.
- `getActiveCount()`: số thread đang tích cực thực thi task, là **ước lượng** cho monitoring.
- `getPoolSize()`, `getLargestPoolSize()`: worker hiện tại và cực đại đã từng có.
- `getTaskCount()`, `getCompletedTaskCount()`: số task tổng và đã hoàn tất, giá trị dùng monitoring.
- `getRejectedExecutionHandler()`: policy đang cấu hình, nhưng muốn thống kê số reject thường cần tự instrument.
- **Queue waiting time**: thời gian từ enqueue/accept đến task bắt đầu; rất quan trọng cho SLA.
- **Execution time**: thời gian task chạy; tránh nhầm với queue wait.
- **Heap/GC**, downstream response time, timeouts, cancellations và retry.

Các getter snapshot/ước lượng **không phải primitive đồng bộ**: check `getQueue().size() < capacity` rồi `execute()` không đảm bảo còn chỗ, vì thread khác có thể submit trong khoảng trống. Dùng chính queue bounded/rejection hoặc một admission protocol.

### Dấu hiệu overload rõ ràng

- Queue size tăng liên tục qua nhiều khoảng đo, không drain sau burst.
- Completed/sec thấp hơn accepted/sec kéo dài.
- Active workers đạt mức worker hữu ích nhưng queue vẫn tăng.
- P99 queue wait tăng, nhiều task bắt đầu sau client deadline.
- Heap sau GC tăng cùng queue.
- Retry rate, timeout rate và lượng task bị cancel cùng tăng.
- Executor shutdown mất nhiều thời gian vì backlog.

Nếu queue luôn bằng 0 nhưng request vẫn chậm, hãy tìm bottleneck khác: task đang chạy, downstream, `SynchronousQueue`, thread count, lock contention hoặc CPU, thay vì quy mọi lỗi về unbounded queue.

## 17. Kiểm thử load và rủi ro thực tế

Không cần cố tình đẩy JVM tới `OutOfMemoryError` để chứng minh nguy cơ. Dùng latch như các ví dụ trên, queue capacity nhỏ, deadline và metric rõ ràng. Với load test riêng, cần kiểm tra:

1. **Steady state:** accepted/completed cân bằng và queue ổn định?
2. **Burst:** queue có drain trong thời gian cho phép sau khi burst qua?
3. **Overload kéo dài:** rejection/backpressure kích hoạt đúng?
4. **Task chậm:** worker bị block thì queue wait thay đổi ra sao?
5. **Timeout/cancel:** task đã hết giá trị có còn chạy hay giữ memory?
6. **Failure:** task ném exception có được ghi nhận, retry đúng và idempotent không?
7. **Shutdown:** queue còn backlog, `shutdown`/`shutdownNow` hoạt động theo policy?
8. **Caller-runs:** submitter có bị block lâu hay chạy task dưới context không phù hợp?
9. **Memory:** heap sau GC và retained task graph có phù hợp budget?
10. **Tail latency:** p95/p99 của queue wait và end-to-end vẫn nằm trong yêu cầu?

Dùng JMH cho microbenchmark phù hợp, nhưng để đánh giá queue overload phải có **load test nhiều producer, task durations đại diện, giới hạn downstream và cancellation/shutdown scenarios**. Một vòng `System.nanoTime()` chạy trong main không phản ánh áp lực thực.

### Một lưu ý riêng về `submit`

Khi `submit(Callable/Runnable)`, exception của task thường được lưu trong `Future`; nếu bỏ qua Future mà không có kênh quan sát lỗi, task có thể **thất bại âm thầm đối với logic nghiệp vụ**. `execute(Runnable)` có xử lý exception khác. Vì vậy monitor “submit thành công” không chứng minh task đã chạy đúng, nhất là khi queue quá lớn.

## 18. Quy trình quyết định khi nhận thấy queue không giới hạn

**Bước 1 — Xác định loại executor thật.** `newFixedThreadPool`, `newSingleThreadExecutor` hay custom `ThreadPoolExecutor`? Work queue loại gì, capacity bao nhiêu, core/max và rejection handler gì?

**Bước 2 — Đo arrival vs completion.** Task gửi vào và hoàn thành trên cùng cửa sổ thời gian; backlog tăng có bền vững không?

**Bước 3 — Đánh giá memory retention.** Task giữ object graph lớn nào, cancelled Future có nằm trong queue, memory budget cho queue còn bao nhiêu?

**Bước 4 — Đánh giá deadline.** Task chờ tới lúc chạy còn có giá trị hay chỉ làm việc vô ích? Có nên reject/cancel/expire sớm?

**Bước 5 — Chọn capacity.** Dựa vào cả memory và latency; không mặc định dùng `Integer.MAX_VALUE`.

**Bước 6 — Chọn overload policy.** Abort, caller-runs nếu đúng semantics, rate limiting, semaphore admission có release-once, hoặc durable queue với quota. Ghi nhận reject và tác động lên client.

**Bước 7 — Kiểm tra số worker/downstream.** CPU-bound khác blocking I/O; tăng worker không tự tăng DB capacity hoặc external API rate limits.

**Bước 8 — Kiểm tra failure và shutdown.** Phải thu lỗi từ `Future`, có cancellation policy và giới hạn thời gian drain; không mất task không chủ đích.

**Bước 9 — Benchmark/load test.** So sánh p95/p99 wait, completed/sec, heap, GC, reject, timeouts và correctness trên tải đại diện.

## 19. Các hiểu nhầm thường gặp

| Hiểu nhầm | Thực tế |
| --- | --- |
| “Queue không giới hạn là vô hạn tài nguyên.” | Heap hữu hạn; `LinkedBlockingQueue` cũng có bound `Integer.MAX_VALUE` theo số phần tử |
| “Chỉ cần tăng `maximumPoolSize`.” | Unbounded queue thường nhận task trước khi tạo worker vượt core |
| “`submit` trả nhanh là task chạy nhanh.” | Có thể vẫn đang chờ rất lâu |
| “Không có reject nghĩa là hệ thống khỏe.” | Queue có thể đang tích backlog nguy hiểm |
| “Task nhỏ nên memory footprint nhỏ.” | Closure và `FutureTask` có thể giữ object graph lớn |
| “Timeout ở `Future.get` tự hủy task.” | Timeout của caller không phải cancellation |
| “`cancel` tức khắc xóa task khỏi queue.” | Có executor cần dequeue hoặc `purge` |
| “Bounded queue tự xử lý overload.” | Nó làm overload lộ rõ, caller vẫn phải có policy |
| “`CallerRunsPolicy` luôn thực hiện task.” | Sau shutdown nó có thể bỏ task; trước shutdown có thể block caller |
| “Queue size bằng 0 nghĩa là mọi task xong.” | Task running không nằm trong queue |
| “`shutdown()` xóa backlog.” | Thường drain những task đã nhận |
| “`shutdownNow()` chắc chắn terminate ngay.” | Best effort interruption, có task không hợp tác |
| “Một benchmark ngắn đủ chứng minh tối ưu.” | Kết quả phụ thuộc contention, GC, payload, scheduling và downstream |

## 20. Câu hỏi tự kiểm tra

**Câu 1.** `ThreadPoolExecutor` có core=2, max=20 và `new LinkedBlockingQueue<>()`. Nếu core worker bận, task mới có chắc làm pool tăng lên 20 worker không?  
**Đáp án:** Không. Queue rất lớn thường nhận task, nên executor không cần tạo worker vượt core.

**Câu 2.** Vì sao queue chưa đạt `Integer.MAX_VALUE` vẫn có thể gặp OOM?  
**Đáp án:** Số task và captured object graph có thể tiêu thụ hết heap từ sớm.

**Câu 3.** `Future.get(100, MILLISECONDS)` timeout thì task đã dừng chưa?  
**Đáp án:** Chưa chắc. Deadline chờ của caller tách biệt với execution/cancellation.

**Câu 4.** Một worker đang block, bounded queue capacity 2 có 2 task chờ, maxPoolSize=1. Task tiếp theo với `AbortPolicy` ra sao?  
**Đáp án:** Bị reject bằng `RejectedExecutionException`.

**Câu 5.** `CallerRunsPolicy` có bảo đảm task sẽ chạy ngay cả khi executor đã shutdown không?  
**Đáp án:** Không. Policy bỏ task khi shutdown; khi chưa shutdown và overload, task có thể chạy inline ở caller.

**Câu 6.** Nếu `getQueue().size() == 0` có thể kết luận executor không có việc không?  
**Đáp án:** Không. Worker vẫn có thể đang chạy task.

**Câu 7.** Vì sao `shutdown()` mất rất lâu khi queue unbounded lớn?  
**Đáp án:** Nó không tự bỏ task đã nhận; executor phải hoàn tất backlog trước khi terminated, trừ khi thay policy/cancel.

**Câu 8.** `cancel(false)` đối với Future đang queued có nhất thiết xóa wrapper ngay không?  
**Đáp án:** Không. `ThreadPoolExecutor.purge()` có thể được dùng để loại cancelled Future còn ở queue.

**Câu 9.** Đo mỗi accepted/sec có đủ để phát hiện overload không?  
**Đáp án:** Không. Cần completed/sec, queue slope, wait latency, heap/GC, timeout, failure/rejection.

**Câu 10.** Tiêu chí chọn queue capacity là “lớn nhất JVM chịu được” phải không?  
**Đáp án:** Không. Cần dựa vào budget heap, queue waiting-time deadline, workload burst và policy quá tải.

## Tài liệu chính thức Java SE

- [`ThreadPoolExecutor` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): core/max/queue strategy, rejection, `purge`, monitoring.
- [`Executors` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html): `newFixedThreadPool`, `newSingleThreadExecutor`, `newCachedThreadPool`.
- [`LinkedBlockingQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html): default capacity và queue behavior.
- [`ArrayBlockingQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html): bounded FIFO queue.
- [`RejectedExecutionHandler` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html): xử lý task không được nhận.
- [`Future` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): timeout, cancellation và completion.
- [`ExecutorService` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): shutdown, termination, task lifecycle.
- [`Semaphore` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html): admission/resource permits.

**Ghi nhớ:** Queue không giới hạn **trì hoãn thời điểm phải đối mặt với overload**, không làm executor có throughput vô hạn. Thiết kế an toàn cần **bounded admission, memory và latency budget, rejection/backpressure policy, deadline/cancellation, đo backlog và shutdown semantics**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-005">corePoolSize, maximumPoolSize và keepAliveTime</a>

<details>
<summary>Xem chi tiết</summary>

# corePoolSize, maximumPoolSize và keepAliveTime trong Java ThreadPoolExecutor

## 1. Mục tiêu bài học

Một Java application xử lý nhiều công việc đồng thời thường không muốn tạo một `new Thread(...)` vô hạn cho mỗi yêu cầu. `ThreadPoolExecutor` quản lý các **worker threads**, **hàng đợi công việc (work queue)** và chính sách **từ chối task (rejection)** để điều tiết việc thực thi.

Ba tham số `corePoolSize`, `maximumPoolSize` và `keepAliveTime` là nền tảng, nhưng **không thể hiểu tách rời queue**. Người mới thường nghĩ: “core=2, max=10, vậy khi có 10 request pool sẽ chạy 10 thread”. Đây là nhận định **không đúng trong nhiều cấu hình**: với queue có thể nhận thêm task, pool ưu tiên **xếp hàng sau khi đủ core**, chứ không lập tức tăng lên max.

Sau bài học, bạn có thể:

- Diễn giải chính xác `corePoolSize` (ngưỡng worker lõi), `maximumPoolSize` (trần worker) và `keepAliveTime` (thời gian idle của worker đủ điều kiện được thu hồi).
- Mô tả thứ tự ra quyết định khi `execute(task)`: **tạo core worker → ưu tiên queue → thêm non-core worker khi không queue được → reject khi không còn khả năng nhận**.
- Phân biệt queue **không giới hạn**, queue **có dung lượng giới hạn** và `SynchronousQueue` (handoff trực tiếp) và hiểu vì sao chúng thay đổi hẳn ý nghĩa thực tế của maximumPoolSize.
- Hiểu việc worker được **tạo lười** (lazily), `prestartCoreThread()`, `prestartAllCoreThreads()`, `allowCoreThreadTimeOut(true)` và các giới hạn cấu hình.
- Viết code Java 17+ tạo, quan sát, shutdown một `ThreadPoolExecutor` có giới hạn; tái hiện queue, mở rộng tới max và rejection **không dựa vào sleep để đoán lịch chạy**.
- Biết tác động tới latency, throughput, memory, backpressure, blocking I/O, CPU-bound work và cancellation.
- Tránh lỗi đặt `maximumPoolSize` rất lớn nhưng dùng unbounded queue, giữ worker không cần thiết, bỏ qua `RejectedExecutionException` hoặc để task/blocking chiếm hết pool.

**Phạm vi:** Java SE, `java.util.concurrent` và Executor Framework; không phụ thuộc thư viện hoặc framework bên ngoài. Ví dụ chạy với Java 17+.

## 2. Cấu trúc tổng quan của ThreadPoolExecutor

Một constructor thường gặp:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

ThreadPoolExecutor executor = new ThreadPoolExecutor(
        2,                         // corePoolSize
        4,                         // maximumPoolSize
        30,                        // keepAliveTime
        TimeUnit.SECONDS,          // đơn vị
        new ArrayBlockingQueue<>(8) // workQueue, tối đa 8 task đang đợi
);
~~~

Đây là **đoạn trích dùng trong một method**. Sau khi hoàn tất sử dụng, cần gọi `shutdown()`/`awaitTermination()` phù hợp. Bản thân constructor không tự khởi chạy toàn bộ 2 thread lõi nếu chưa có công việc hoặc chưa prestart.

Hình dung:

~~~text
                      ThreadPoolExecutor
                   ┌──────────────────────────┐
execute(task) ────►│ admission / quyết định   │
                   │                          │
                   │  Worker đang chạy        │
                   │  ┌────────────────────┐  │
                   │  │ core (<= 2)        │  │
                   │  │ extra (<= tổng 4)  │  │
                   │  └────────────────────┘  │
                   │                          │
                   │  Work queue (capacity 8) │
                   │  ┌────────────────────┐  │
                   │  │ task đợi          │  │
                   │  └────────────────────┘  │
                   │                          │
                   │  Reject nếu không nhận   │
                   └──────────────────────────┘
~~~

**Số thread tổng cộng không vượt maximumPoolSize** theo hợp đồng cấu hình (trừ chi tiết vận hành thay thế worker đã chết trong quá trình quản lý, không nên dựa vào snapshot metrics để khẳng định một mốc tức thời tuyệt đối). Task queue và worker pool là **hai thành phần riêng**: queue chứa task chưa chạy; worker đang giữ task có thể đang chạy, block hoặc đợi tài nguyên ngoài.

Để tính đúng capacity và latency, không chỉ nhìn số worker; phải nhìn cả **thời gian thực thi task**, queue capacity, tốc độ task đến, throughput và rejection policy.

## 3. corePoolSize: ngưỡng worker lõi

`corePoolSize` là số lượng worker mà executor cố duy trì khi có công việc, trừ khi chính sách core timeout cho phép chúng chết do idle.

Các quy tắc:

1. Khi `execute(task)` và số worker **ít hơn corePoolSize**, executor ưu tiên tạo thêm một worker để chạy task **ngay cả khi có worker hiện tại đang idle** và chưa vượt ngưỡng core. Đây là mô tả chính sách từ JDK; trên một thread-pool cụ thể, trạng thái workers có thể biến đổi đồng thời.
2. Worker lõi thường được tạo **lazily khi task được submit**, không có nghĩa gọi constructor là đã có đủ core threads.
3. Khi worker count đã đạt corePoolSize, executor thường **thử queue task** trước khi tạo thêm non-core worker.
4. Theo mặc định, core workers không chết chỉ vì idle; nhưng chúng có thể được tạo/thay thế khi bị lỗi, khi pool shutdown hoặc khi bật `allowCoreThreadTimeOut(true)`.
5. `corePoolSize` không phải trần số thread; có thể tăng đến `maximumPoolSize` nếu queue không nhận được task và điều kiện cho phép.

Đừng nhầm `corePoolSize=2` với “luôn tồn tại đúng 2 thread ngay từ lúc khởi tạo” hoặc “chỉ được chạy 2 task”. Pool có thể có 0 worker lúc mới tạo, 2 worker khi tải thấp, và tới max trong điều kiện queue đầy.

### Prestart: khởi chạy core workers trước khi có request

~~~java
executor.prestartCoreThread();     // cố khởi chạy một core thread
executor.prestartAllCoreThreads(); // khởi chạy những core threads còn thiếu
~~~

`prestartCoreThread()` trả boolean tùy có tạo được worker mới hay không, `prestartAllCoreThreads()` trả số worker được khởi chạy thêm. Có thể hữu ích nếu muốn giảm chi phí khởi tạo worker khi request đầu đến, hoặc queue đã có task trước. Tuy nhiên, prestart có chi phí thread/stack và không tự làm các task nhanh hơn về mặt thuật toán.

### corePoolSize bằng 0 có hợp lệ không?

Có, `corePoolSize` được phép là 0 nếu các điều kiện constructor khác hợp lệ (`maximumPoolSize > 0` và không nhỏ hơn core). Khi đó cơ chế nhận công việc và bảo đảm có worker xử lý queue có các chi tiết riêng; không nên suy ra “queue có task nhưng pool sẽ không bao giờ chạy”. Với các bài toán thông thường, chọn một cấu hình core dương giúp dễ dự đoán và theo dõi hơn.

## 4. maximumPoolSize: trần worker, không phải số worker mong muốn

`maximumPoolSize` là **giới hạn trên** của số worker mà pool có thể tạo để phục vụ task trong điều kiện admission phù hợp. Điều kiện cấu hình:

~~~text
0 <= corePoolSize <= maximumPoolSize
maximumPoolSize > 0
~~~

Khi pool đã có core workers và queue vẫn nhận task, nó thường **không tạo thêm worker** dù max lớn. Chỉ khi queue **không nhận thêm task** (ví dụ đã đầy với bounded queue hoặc direct handoff không thể hoàn thành) executor mới thử tạo thêm worker đến trần max.

Do đó:

~~~java
new ThreadPoolExecutor(
        2, 100, 60, TimeUnit.SECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>()
);
~~~

Với `LinkedBlockingQueue` không truyền capacity, giới hạn danh nghĩa rất lớn (`Integer.MAX_VALUE`). Trong hoạt động bình thường, queue tiếp tục nhận task; pool thường chỉ có khoảng core workers, và cấu hình `maximumPoolSize=100` **gần như không được sử dụng để giải quyết backlog**. Queue có thể tăng lớn, tiêu thụ heap và làm request chờ lâu.

Đây là một trong những lỗi cấu hình phổ biến nhất: tăng maximumPoolSize nhưng **quên queue capacity**.

### Khi nào maximumPoolSize thực sự có tác dụng?

Ví dụ `core=2, max=4` và queue capacity 3:
- Hai task đầu được giao cho core workers.
- Các task tiếp theo xếp hàng đến khi đủ 3 task đang đợi.
- Nếu cả hai worker vẫn bận và queue đã đầy, task đến tiếp theo có thể tạo worker thứ 3 rồi thứ 4.
- Khi tổng worker đã bằng 4 và queue vẫn đầy, task đến tiếp theo không thể nhận theo chính sách thường và phải **reject** hoặc xử lý qua một `RejectedExecutionHandler`.

Không nên biến thứ tự trên thành lời hứa “task số 6 luôn chạy ngay trước task số 3”: lịch chạy, thời gian hoàn tất task, và thời điểm worker lấy task từ queue có thể thay đổi. Đây là **quy tắc admission**, không phải trình tự completion/FIFO tuyệt đối cho toàn hệ thống.

## 5. keepAliveTime: giữ worker idle trong bao lâu?

`keepAliveTime` là thời gian một worker **không nhận thêm task** trong khi đang idle có thể được giữ lại trước khi bị thu hồi, với những điều kiện của pool.

Mặc định:
- Khi pool có **nhiều hơn corePoolSize workers**, các worker **vượt core** có thể bị kết thúc sau khi idle quá keepAliveTime.
- Các core workers **không bị thu hồi chỉ vì idle**, trừ khi bật `allowCoreThreadTimeOut(true)`.
- Keep-alive áp dụng cho **thời gian worker chờ công việc mới**, **không** giới hạn thời gian một task đang chạy.
- Khi worker đang làm I/O 1 phút, `keepAliveTime=30s` không khiến task bị ngắt sau 30 giây.
- Khi worker xong việc và tiếp tục lấy được task trong thời hạn, nó có thể tiếp tục được dùng; idle timer không phải TTL tuyệt đối của Thread object.

Ví dụ:

~~~java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        2, 5,
        30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(10)
);
~~~

Nếu pool tạm có 5 workers, khi tải giảm về 0, các worker dư trên core có thể kết thúc sau khi idle đủ 30 giây. Theo chính sách mặc định, các core workers vẫn tồn tại để chờ công việc mới cho tới khi shutdown hoặc thay đổi cấu hình. **Không suy đoán số worker tại đúng mốc 30.000ms** vì scheduler và các thread đang thực thi công việc/đọc queue không có deadline chính xác đến từng mili giây.

### Bật core-thread timeout

~~~java
pool.allowCoreThreadTimeOut(true);
~~~

Khi true, **cả core workers** có thể bị thu hồi sau thời gian idle. Điều kiện: `keepAliveTime` phải **lớn hơn 0**; nếu không, `allowCoreThreadTimeOut(true)` ném `IllegalArgumentException`. Đặt chính sách này **trước khi pool chịu tải** thường dễ hiểu và ít biến động.

Lưu ý `keepAliveTime=0` có thể là cấu hình hợp lệ khi **không** bật core timeout: extra workers được phép kết thúc khi không có việc chờ. Nhưng không thể đặt keepAlive=0 trong lúc core-thread timeout đang bật.

## 6. Thứ tự quyết định quan trọng nhất: execute và workQueue

Tài liệu `ThreadPoolExecutor` mô tả **ba quy tắc tương tác giữa worker size và queue**:

1. **Nếu đang có ít hơn `corePoolSize` workers:** ưu tiên tạo worker mới để chạy task vừa submit, thay vì đưa ngay vào queue.
2. **Nếu đã có ít nhất core workers:** ưu tiên thử đưa task vào `workQueue`, thay vì tạo thêm worker.
3. **Nếu queue không nhận được task:** thử tạo worker mới, miễn tổng worker chưa đạt `maximumPoolSize`; nếu cả điều kiện này cũng thất bại thì áp dụng **rejection handler**.

Sơ đồ admission đơn giản hóa:

~~~text
                   execute(task)
                        |
             workerCount < corePoolSize ?
                 /               \
               có                không
               |                   |
        tạo core worker        queue.offer(task)
        để chạy task             /      \
                              được     không được
                               |          |
                             queued    workerCount < max ?
                                          /       \
                                        có        không
                                        |           |
                               tạo extra worker  reject / handler
                               để chạy task

* Thực tế phải xử lý shutdown, race giữa workers và queue,
  lỗi tạo worker, và việc worker đã biến mất.
~~~

Đây là **mô hình học tập** cho luồng admission. Implementation thực tế có kiểm tra lại trạng thái RUNNING/SHUTDOWN sau queue offer và kiểm soát worker count qua CAS; không nên viết lại sơ đồ trên như một implementation thread-safe đầy đủ.

### Bảng trạng thái với core=2, max=4, queue capacity=3

Giả sử mọi worker xử lý task lâu, không task nào hoàn thành trong lúc submit:

| Task mới | Hành vi điển hình | Worker count | Số task đang chờ |
|---|---|---:|---:|
| #1 | Tạo core worker thứ 1 | 1 | 0 |
| #2 | Tạo core worker thứ 2 | 2 | 0 |
| #3 | Queue | 2 | 1 |
| #4 | Queue | 2 | 2 |
| #5 | Queue | 2 | 3 |
| #6 | Queue đã đầy → tạo extra worker thứ 3 | 3 | 3 |
| #7 | Queue đầy → tạo extra worker thứ 4 | 4 | 3 |
| #8 | Worker đạt max + queue đầy → reject | 4 | 3 |

Các con số này **phụ thuộc giả thiết tất cả workers đang bận**. Nếu worker hoàn tất sớm, nó có thể lấy task từ queue, giải phóng capacity và thay đổi quyết định cho những submit kế tiếp.

## 7. Queue làm thay đổi ý nghĩa ba tham số ra sao?

### 7.1. Unbounded queue: core đóng vai trò chính, max ít được dùng

~~~java
new java.util.concurrent.LinkedBlockingQueue<Runnable>()
~~~

Hàng đợi này có giới hạn danh nghĩa rất lớn, nên `offer(task)` thường thành công. Hệ quả:
- Sau khi có đủ core workers, task đến tiếp tục xếp hàng.
- `maximumPoolSize` thường không tác động tới số worker trong điều kiện bình thường.
- Backlog có thể tăng và tiêu thụ heap khi arrival rate cao hơn processing rate.
- Tail latency có thể rất lớn dù hệ thống không ném RejectedExecutionException.

`Executors.newFixedThreadPool(n)` dùng kiểu queue không giới hạn thực tế và số worker cố định n: phù hợp khi bạn **đã kiểm soát lượng submit**, nhưng không tự tạo backpressure khi producer quá nhanh.

### 7.2. Bounded queue: có trần backlog, max có vai trò khi queue đầy

~~~java
new java.util.concurrent.ArrayBlockingQueue<Runnable>(100)
~~~

Hàng đợi giới hạn 100 task chờ. Khi đã đạt core workers, tasks được queue trước. Chỉ khi queue không còn chỗ, executor mới thử tạo thêm workers tới maximumPoolSize. Nếu đã hết cả hai nguồn capacity, rejection policy bắt đầu phát huy.

Bounded queue giúp **cắt đà tăng memory backlog**, nhưng cần định nghĩa hệ thống phản ứng ra sao với overload. Nếu rejection bị nuốt hoặc được chuyển thành retry không giới hạn, tải vẫn không được điều tiết an toàn.

### 7.3. SynchronousQueue: handoff, không giữ backlog trong queue

~~~java
new java.util.concurrent.SynchronousQueue<Runnable>()
~~~

`SynchronousQueue` không có dung lượng chứa task; nó chỉ **handoff trực tiếp** task cho một worker đang chờ nhận. Nếu không có worker sẵn sàng nhận, `offer` không lưu task để chờ, executor có thể tạo thêm worker đến maximumPoolSize. Khi đạt max mà không có worker nhận, task mới có thể bị reject.

Cấu hình này thường khiến max có tác động rõ hơn; đồng thời có thể làm tăng số threads nhanh nếu tải burst lớn. Cần chọn max và rejection policy phù hợp. `Executors.newCachedThreadPool()` về mặt triển khai dùng mô hình direct handoff với max rất lớn, nên nếu producer không có giới hạn, có thể tạo quá nhiều platform threads.

### So sánh nhanh

| Queue | Đặc điểm | Hệ quả với core/max/keepAlive | Nguy cơ |
|---|---|---|---|
| `LinkedBlockingQueue<>` không truyền capacity | Gần như unbounded | Queue ưu tiên nhận task, max ít phát huy | Backlog/heap, tail latency |
| `ArrayBlockingQueue<>(capacity)` | Bounded | Khi đầy mới tạo extra tới max | Rejection nếu overload, cần policy |
| `SynchronousQueue<>` | Không lưu backlog | Tạo extra sớm hơn khi không handoff được | Bùng nổ số worker nếu max quá cao |
| `LinkedBlockingQueue<>(capacity)` | Bounded linked queue | Tương tự bounded queue, đặc điểm allocation khác | Chi phí memory/queue contention |

Không có queue “tốt nhất” trong mọi workload. Hàng đợi là một **phần bắt buộc của quyết định sizing**, không thể chọn core/max/keepAlive trước rồi bỏ qua queue.

## 8. Ví dụ Java 17+ xác định: core → queue → extra → reject

Chương trình dùng `CountDownLatch` để giữ worker 1 và worker 2 đang bận. Lúc đó queue capacity 1 chứa đúng một task. Submit thêm task thứ 4 sẽ bị reject theo `AbortPolicy`.

Lưu tệp **ThreadPoolSizingDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolSizingDemo {
    private static void awaitGate(CountDownLatch gate) {
        try {
            gate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 2,
                10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy()
        );

        CountDownLatch coreStarted = new CountDownLatch(1);
        CountDownLatch extraStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        boolean rejected = false;
        try {
            // Task 1: tạo core worker, giữ nó bận.
            pool.execute(() -> {
                coreStarted.countDown();
                awaitGate(release);
            });
            coreStarted.await();

            // Task 2: core đã bận -> vào queue capacity 1.
            pool.execute(() -> { });

            // Task 3: queue đầy -> tạo worker thứ 2 (extra).
            pool.execute(() -> {
                extraStarted.countDown();
                awaitGate(release);
            });
            extraStarted.await();

            // Task 4: core/extra đều bận, queue vẫn đầy -> reject.
            try {
                pool.execute(() -> { });
            } catch (RejectedExecutionException e) {
                rejected = true;
            }

            System.out.println("poolSize=" + pool.getPoolSize());
            System.out.println("queued=" + pool.getQueue().size());
            System.out.println("rejected=" + rejected);
        } finally {
            // Giải phóng cả hai workers trên mọi đường đi.
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }

        System.out.println("completed=" + pool.getCompletedTaskCount());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ThreadPoolSizingDemo.java
java ThreadPoolSizingDemo
~~~

Output trong điều kiện các task không bị interrupt ngoài và executor kết thúc bình thường:

~~~text
poolSize=2
queued=1
rejected=true
completed=3
~~~

**Giải thích tính xác định:**

1. Main đợi `coreStarted.await()`: task 1 đang ở worker lõi và chưa thể kết thúc vì `release` còn đóng.
2. Task 2 được submit khi core worker đang bận, nên được đưa vào queue có capacity 1; chưa có worker nào được giải phóng để lấy task này.
3. Task 3 đến khi queue đã đầy. Pool còn dưới maximumPoolSize=2 nên tạo worker thứ hai. Main đợi `extraStarted` để chắc chắn worker thứ hai đã bắt đầu và đang chờ release.
4. Task 4 không thể đưa vào queue và cũng không thể tạo worker thứ ba, nên `AbortPolicy` ném `RejectedExecutionException`; main bắt và ghi rejected=true.
5. Trước khi mở latch, pool có đúng 2 workers bận và 1 task chờ; hai metrics `poolSize` và `queued` phản ánh điều kiện cố định trong ví dụ.
6. Trong `finally`, main mở gate, shutdown graceful, đợi pool kết thúc. Ba task được nhận (1, 2, 3) hoàn thành; task 4 bị từ chối không chạy. Vì thế completed=3.

**Điểm thực hành:** Latch trong ví dụ này **được dùng để dựng lịch chạy và chứng minh admission policy**, không phải cấu trúc chung phải đưa vào mọi production pool. Trong thực tế task có thể hoàn tất trước khi task kế tiếp được submit nên không nên suy số workers/queue từ **số thứ tự request**.

### Tại sao không dùng sleep để ép queue đầy?

`Thread.sleep(100)` chỉ khiến thread hiện tại nghỉ một khoảng, không bảo đảm task nào đã bắt đầu hoặc còn đang bận trên máy khác. `CountDownLatch` tạo thứ tự rõ ràng, cho phép chương trình minh họa chính sách sizing chứ không dựa vào may rủi scheduler.

## 9. Ví dụ đối chứng: unbounded queue không tự mở rộng tới maximum

Với `LinkedBlockingQueue<>` mặc định, khi core worker đang bận, các task khác vào queue thay vì kích hoạt extra workers. Ta có thể kiểm chứng bằng ví dụ ngắn.

Lưu tệp **UnboundedQueueDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UnboundedQueueDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 5,
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            started.await();

            // Worker lõi đang bận. Hai task này được xếp hàng.
            pool.execute(() -> { });
            pool.execute(() -> { });

            System.out.println("poolSize=" + pool.getPoolSize());
            System.out.println("queued=" + pool.getQueue().size());
            System.out.println("maximum=" + pool.getMaximumPoolSize());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac UnboundedQueueDemo.java
java UnboundedQueueDemo
~~~

Output trong trường hợp bình thường:

~~~text
poolSize=1
queued=2
maximum=5
~~~

Dù maximum=5, pool **không cần tạo thêm** workers khi queue vẫn có thể tiếp nhận task. Đây là lý do cần kiểm tra `workQueue` trước khi tăng maximumPoolSize trong một ứng dụng có backlog lớn.

## 10. Ví dụ cấu hình keepAliveTime và core timeout

Ví dụ dưới đây minh họa API cấu hình, tránh dùng `sleep` rồi đo chính xác worker count tại một thời điểm dễ bị ảnh hưởng bởi scheduler.

Lưu tệp **CoreTimeoutConfigDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CoreTimeoutConfigDemo {
    public static void main(String[] args) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 15, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8)
        );

        try {
            pool.allowCoreThreadTimeOut(true);

            System.out.println("core=" + pool.getCorePoolSize());
            System.out.println("max=" + pool.getMaximumPoolSize());
            System.out.println("keepAliveSeconds=" +
                    pool.getKeepAliveTime(TimeUnit.SECONDS));
            System.out.println("coreCanTimeOut=" +
                    pool.allowsCoreThreadTimeOut());
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CoreTimeoutConfigDemo.java
java CoreTimeoutConfigDemo
~~~

Output:

~~~text
core=2
max=4
keepAliveSeconds=15
coreCanTimeOut=true
~~~

Phần cấu hình trên **không** yêu cầu pool đã tạo đủ hai core workers. Vì không submit task và không gọi prestart, số worker có thể vẫn bằng 0. `allowCoreThreadTimeOut(true)` chỉ thay **chính sách tồn tại của core workers sau khi idle**, không ra lệnh hủy workers đang chạy.

### Khi nào bật core timeout?

Có thể hữu ích khi:
- Tải đến theo các đợt cách xa nhau, mỗi đợt sử dụng nhiều platform threads.
- Muốn giảm tài nguyên khi idle lâu, chấp nhận chi phí tái tạo worker khi traffic quay lại.
- Không cần duy trì threads warm để tối thiểu hóa thời gian khởi động task kế tiếp.

Không nên bật chỉ vì “tiết kiệm thread” mà bỏ qua độ trễ cold-start hoặc burst latency. Cần đo workload thực tế và cân nhắc `ThreadFactory`, công việc đầu tiên cần làm, tài nguyên hệ điều hành và memory.

## 11. Rejection policy là phần thiết kế capacity, không phải chi tiết phụ

Nếu pool vẫn chạy nhưng **queue đã không nhận thêm task** và **worker count đã đạt max**, một task mới được xử lý qua `RejectedExecutionHandler`. Executor cũng có thể reject task khi đã shutdown.

Các handler tích hợp:

| Handler | Hành vi điển hình khi reject | Cần lưu ý |
|---|---|---|
| `AbortPolicy` (mặc định) | Ném `RejectedExecutionException` | Caller phải xử lý quá tải / lỗi rõ ràng. |
| `CallerRunsPolicy` | Caller chạy task **trong thread gọi submit**, nếu executor chưa shutdown | Tạo backpressure gián tiếp nhưng có thể kéo dài latency và làm code chạy ở thread bất ngờ. Nếu shutdown, task bị bỏ. |
| `DiscardPolicy` | Bỏ task mà không ném | Nguy hiểm nếu task là công việc bắt buộc; dễ mất dữ liệu im lặng. |
| `DiscardOldestPolicy` | Bỏ task đầu queue rồi thử gửi task mới | Có thể mất task cũ; với priority queue và một số cấu hình, hành vi có thể đặc biệt nguy hiểm. Cần xem shutdown và retry. |

Không có handler nào tự đảm bảo mọi yêu cầu nghiệp vụ được xử lý “exactly once”. Nếu task là đơn hàng, giao dịch hoặc công việc phải được lưu bền vững, cần đường tiếp nhận và lưu queue đáng tin cậy ở đúng tầng hệ thống. Trong một JVM, rejected task chưa được nhận/commit không nên bị log thành thành công.

### CallerRunsPolicy có ý nghĩa backpressure như thế nào?

Khi pool quá tải, thread submit phải **trực tiếp chạy task**, nên nó không thể tiếp tục submit thật nhanh. Điều này có thể giúp cân bằng tốc độ producer/consumer. Nhưng nếu thread submit là event loop hoặc thread cần phản hồi nhanh, tác vụ chạy tại đó sẽ **chặn đường xử lý chính**. Không dùng CallerRunsPolicy theo thói quen mà không xác định caller thread có được phép chạy công việc đó hay không.

### AbortPolicy là lựa chọn minh bạch

Một ứng dụng có thể bắt `RejectedExecutionException` và báo “quá tải / thử lại sau” hoặc chuyển công việc sang kênh xử lý khác. Không retry vô hạn ngay tại thread đã submit: retry lập tức tạo thêm contention và không giảm áp lực hệ thống.

## 12. keepAliveTime không phải timeout của task

Đây là ba giới hạn thời gian hoàn toàn khác nhau:

| Cấu hình/API | Điều gì bị giới hạn? | Có hủy task đang chạy? |
|---|---|---|
| `keepAliveTime` | Thời gian worker **idle** có thể đợi task mới trước khi được thu hồi theo policy | **Không** |
| `Future.get(timeout, unit)` | Thời gian **caller chờ kết quả** | **Không** tự hủy task |
| `Future.cancel(true)` | Yêu cầu hủy Future và cố interrupt worker khi phù hợp | Chỉ **nỗ lực** hủy; cần task hợp tác |
| Timeout tại I/O API | Thời gian đợi của **hoạt động I/O tương ứng** | Tùy API/loại tài nguyên |
| `awaitTermination(timeout, unit)` | Thời gian đợi **executor kết thúc** | Không tự kill worker |

Ví dụ một task giữ worker để chờ network 90 giây không bị `keepAliveTime=10s` cưỡng bức dừng giữa chừng. Vì task **không idle**, clock keep-alive không giải quyết long-running tasks.

Nếu muốn deadline nghiệp vụ, cần thiết kế timeout ở nơi task đang block, cancellation và cleanup phù hợp. Không dùng việc tăng/giảm keepAlive để thay thế quản lý timeout của request.

## 13. corePoolSize và CPU-bound vs blocking workload

### CPU-bound work

Các task tính toán liên tục sử dụng CPU:
- Không nên mở quá nhiều platform worker threads so với khả năng CPU chỉ để tăng số “threads”.
- Khi số runnable threads vượt số core CPU quá nhiều, overhead context switching có thể tăng và throughput không nhất thiết tốt hơn.
- Một pool gần với số CPU cores hữu ích là **điểm bắt đầu để benchmark**, không phải công thức cứng.
- Bounded queue có thể giúp giữ memory ổn định khi nguồn task đến nhanh hơn tốc độ tính toán.

Ví dụ:

~~~java
int cores = Runtime.getRuntime().availableProcessors();
int workers = Math.max(1, cores);
~~~

Đây là đoạn trích minh họa; `availableProcessors()` phụ thuộc môi trường/container và không tự biết workload thật. Việc chọn size nên xét CPU quota, parallelism của các module khác, GC, contention và yêu cầu latency.

### Blocking I/O workload

Nếu worker thường **chờ I/O** hơn là tính toán, việc có nhiều workers hơn số CPU cores đôi khi cải thiện concurrency:
- Tuy nhiên, mỗi platform thread vẫn có stack, scheduler và resource overhead.
- Quá nhiều worker có thể làm quá tải connection pool, disk, remote service hoặc memory.
- Với I/O blocking, giới hạn **downstream resource concurrency** và queue capacity thường quan trọng không kém thread count.
- Đo active threads, queue depth, thời gian I/O, rejection, external service saturation và tail latency.

Trong Java 21+, **virtual threads** cung cấp mô hình khác cho nhiều nhiệm vụ blocking: `Executors.newVirtualThreadPerTaskExecutor()` tạo virtual thread cho mỗi task thay vì giữ một pool platform workers cố định. Nhưng đó **không** phải cách tự điều tiết độ lớn connection pool hoặc quota của tài nguyên ngoài. Khi cần giới hạn số yêu cầu đồng thời, sử dụng semaphore, bounded work admission hoặc quota tương ứng. `corePoolSize`/`maximumPoolSize`/`keepAliveTime` vẫn là các tham số trực tiếp của `ThreadPoolExecutor` cho workers theo mô hình pool.

## 14. Quan sát pool: giá trị cấu hình vs metrics tại một thời điểm

Các API thường dùng:

~~~java
pool.getCorePoolSize();
pool.getMaximumPoolSize();
pool.getKeepAliveTime(TimeUnit.SECONDS);
pool.getPoolSize();
pool.getActiveCount();
pool.getQueue().size();
pool.getCompletedTaskCount();
pool.getLargestPoolSize();
pool.getTaskCount();
~~~

Các số có thể chia thành hai nhóm:

**Thông số cấu hình:** corePoolSize, maximumPoolSize, keepAliveTime, loại queue, policy cho core timeout, RejectedExecutionHandler.

**Metrics biến động:** poolSize (workers hiện tại), activeCount (ước lượng workers đang chạy task), completedTaskCount (ước lượng số task đã xong), taskCount (ước lượng tổng task), queue size (số task đang đợi tại thời điểm quan sát).

**Đừng coi các API thống kê là snapshot transaction đồng thời.** Pool hoạt động liên tục; trong lúc bạn đọc activeCount rồi queue size, task có thể được hoàn thành/submit. Các hàm thống kê của `ThreadPoolExecutor` cung cấp dữ liệu để monitoring/tuning, không phải primitive đồng bộ cho quy tắc admission.

Một số metrics giúp chẩn đoán:

| Triệu chứng | Quan sát cần kiểm tra | Hướng suy luận |
|---|---|---|
| Queue dài, worker không tăng tới max | Queue unbounded hoặc vẫn còn chỗ | Executor ưu tiên queue sau core |
| ActiveCount luôn gần poolSize, queue tăng | Task chậm, downstream block, arrival cao | Đo service time, workload và bounded capacity |
| Pool tăng max rồi reject nhiều | Tải vượt capacity | Admission/backpressure, timeouts, rate limit |
| Pool thường chỉ còn core sau idle | Non-core workers bị thu hồi theo keepAlive | Hành vi thường đúng policy |
| Pool về 0 sau idle dù core > 0 | Đã bật core-thread timeout? | Kiểm tra `allowsCoreThreadTimeOut()` |
| Latency rất cao mà ít rejection | Unbounded queue backlog | Tăng max chưa chắc giải quyết nếu queue vẫn nhận |
| “Task biến mất” khi đầy | DiscardPolicy/DiscardOldest/CallerRuns khi shutdown | Kiểm tra handler và ghi nhận accepted/rejected |

## 15. Thay đổi cấu hình khi pool đang chạy

`ThreadPoolExecutor` có setter:

~~~java
pool.setCorePoolSize(newCore);
pool.setMaximumPoolSize(newMax);
pool.setKeepAliveTime(20, TimeUnit.SECONDS);
pool.allowCoreThreadTimeOut(true);
~~~

Đoạn này là minh họa trong một method có `pool` và các biến cấu hình. Những method setter có thể được gọi khi pool hoạt động nhưng **không thay đổi mọi worker cùng một lúc theo kiểu transaction**. Các worker có thể được bổ sung, bị thu hồi khi idle hoặc có công việc chờ tùy chính sách.

### Ràng buộc cấu hình

- `corePoolSize` phải >= 0.
- `maximumPoolSize` phải > 0 và >= corePoolSize.
- `keepAliveTime` phải >= 0; nếu core timeout đang bật thì không được đặt keepAlive=0.
- `allowCoreThreadTimeOut(true)` đòi keepAlive > 0.
- Các phép thay đổi thứ tự setter cần tôn trọng ràng buộc **tại từng bước**.

Ví dụ hiện tại `core=4, max=8` và muốn đổi thành `core=10, max=12`. Không thể gọi `setCorePoolSize(10)` **trước** khi tăng maximum lên ít nhất 10. Ngược lại, khi muốn thu max dưới core hiện tại, phải giảm core trước. Nếu nhiều thread cấu hình executor đồng thời, cần một giao thức quản trị rõ ràng để tránh các trạng thái trung gian không hợp lệ.

### Không dựa vào tuning để sửa bug correctness

Điều chỉnh core/max/queue chỉ ảnh hưởng cách sắp lịch và giới hạn công việc. Nó **không làm một shared counter++ tự atomic**, không sửa race trong task, không biến state mutable thành thread-safe và không giảm tính phức tạp của transaction bên ngoài. Task logic vẫn cần synchronization/ownership phù hợp.

## 16. shutdown đúng cách và vòng đời worker

Một pool cần được quản lý vòng đời để tránh thread leak hoặc khiến JVM không kết thúc.

~~~java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        2, 4, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(50)
);

try {
    pool.execute(() -> System.out.println("work"));
} finally {
    pool.shutdown();
    try {
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Some tasks did not terminate");
            }
        }
    } catch (InterruptedException e) {
        pool.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
~~~

Đây là đoạn trong một method Java hợp lệ. `shutdown()` ngừng nhận tasks mới nhưng các tasks đã nhận tiếp tục được xử lý theo policy. `shutdownNow()` **chỉ nỗ lực** dừng workers đang chạy (thường thông qua interrupt) và trả về các tasks chưa khởi động mà nó lấy khỏi queue; nó không bảo đảm mọi tác vụ bất kỳ lập tức chấm dứt.

Khi task đang block ở API không interruptible, shutdownNow có thể không giải phóng được nó. Cần dùng timeout, đóng tài nguyên hoặc cancellation hook theo hợp đồng của tác vụ. Không nhầm `keepAliveTime` với thời gian đợi shutdown, cũng không nhầm timeout của `awaitTermination` với lệnh cưỡng bức kill.

### Trách nhiệm của task

- Phản ứng `InterruptedException` phù hợp, không nuốt interrupt rồi chạy mãi.
- Cleanup trong `finally`/`try-with-resources`.
- Giữ shared state nhất quán nếu bị cancel.
- Tránh giữ lock trong khi blocking lâu nếu không cần.
- Không tạo task phụ vô hạn trong cùng pool nhỏ khiến task cha chờ task con mà pool không còn worker rảnh (thread-starvation deadlock).

## 17. Sai lầm thường gặp: task chờ Future khác trong cùng pool

Ví dụ:
- Pool có core=max=1, một worker duy nhất.
- Task A chạy trên worker đó và submit task B vào **cùng pool**.
- Task A gọi `futureB.get()`, đợi B kết thúc.
- B nằm trong queue, nhưng worker duy nhất đang bị A giữ và chờ B.

Nếu không có worker khác để thực thi B, ứng dụng có thể **bị treo**. Tăng maximumPoolSize sẽ không cứu được nếu queue unbounded nhận B nhưng executor không tạo thêm worker. Đây là ví dụ điển hình cho việc queue semantics tác động tới liveness, không chỉ throughput.

Giải pháp cần cân nhắc:
- Tránh chờ đồng bộ task con trong cùng fixed-size pool.
- Thiết kế composition bất đồng bộ hoặc phân tách pools có trách nhiệm rõ ràng.
- Điều chỉnh capacity/queue đúng mô hình dependency, nhưng không phụ thuộc vào việc “max lớn là đủ”.
- Nếu cần blocking để đợi kết quả, đảm bảo pool có khả năng tiến triển mà không vòng phụ thuộc chờ nhau.

## 18. Cấu hình tối ưu có công thức cố định không?

Không có một bộ ba core/max/keepAlive tối ưu cho mọi workload. Cần xét:

1. **Task duration:** CPU time vs thời gian chờ.
2. **Arrival rate:** số task đến mỗi giây, burstiness.
3. **SLO:** p95/p99 latency và tỷ lệ lỗi/rejection chấp nhận.
4. **Queue budget:** memory cho backlog, độ trễ tối đa trước khi task bắt đầu.
5. **Downstream limits:** DB connections, remote API quotas, file handles.
6. **CPU/GC:** số cores thực tế, heap, thread stack, allocation rate.
7. **Timeout/cancellation:** request quá hạn có được hủy khỏi queue hay vẫn chiếm memory?
8. **Rejection handling:** có đủ điều kiện chấp nhận việc mất/thử lại task?
9. **Thay đổi theo tải:** traffic đều hay theo burst, cần worker warm hay thu hồi nhanh?
10. **Quan sát thực tế:** queue length, activeCount, task service time, completed count, timeout/rejection và tail latency.

Một cách thử có trách nhiệm:
- Xác định tối đa số task được phép **đồng thời xử lý** và tối đa số task được phép **đợi**.
- Chọn bounded queue và rejection/backpressure theo ý nghĩa nghiệp vụ.
- Bắt đầu từ core/max phù hợp workload; đo workload thật hoặc load test có tính đại diện.
- Đánh giá keepAliveTime theo burst spacing và chi phí khởi tạo worker, không dùng keepAlive để điều khiển runtime task.
- Kiểm tra shutdown và overload behavior, không chỉ kịch bản tải vừa phải.

## 19. Các lỗi phổ biến và cách sửa

**Lỗi 1 — “max=50 nên pool sẽ tạo 50 worker khi có 50 task.”** Không nếu queue tiếp tục nhận task. Sau core, queue được ưu tiên.

**Lỗi 2 — Đặt unbounded queue rồi ngạc nhiên vì poolSize gần core.** Đó là hệ quả admission policy. Dùng bounded queue/queue khác nếu cần max phát huy đúng với contract của workload.

**Lỗi 3 — Dùng keepAliveTime để giới hạn thời gian thực thi một task.** Sai. Keep-alive là idle-worker policy; task timeout phải xử lý riêng.

**Lỗi 4 — Coi corePoolSize là số workers được tạo ngay ở constructor.** Core workers mặc định lazily created; dùng prestart nếu cần.

**Lỗi 5 — Đặt keepAlive=0 rồi bật allowCoreThreadTimeOut(true).** Không hợp lệ; phải có keepAlive > 0 cho core timeout.

**Lỗi 6 — Chọn max vô cùng lớn với SynchronousQueue rồi coi pool tự bảo vệ tài nguyên.** Pool có thể tạo quá nhiều threads, gây quá tải hệ thống. Cần ràng buộc thực.

**Lỗi 7 — Chọn AbortPolicy nhưng không bắt RejectedExecutionException.** Task có thể không được nhận; caller phải hiểu và báo outcome.

**Lỗi 8 — Dùng DiscardPolicy cho công việc bắt buộc.** Có nguy cơ mất task im lặng. Phải có giao thức không mất dữ liệu phù hợp.

**Lỗi 9 — Dùng CallerRunsPolicy mà không kiểm tra caller context.** Caller có thể chạy task blocking và kéo dài latency của request/event loop.

**Lỗi 10 — Dùng queue vô hạn để “tránh reject”.** Thường chỉ chuyển lỗi overload thành memory pressure và latency kéo dài.

**Lỗi 11 — Không shutdown pool.** Workers có thể giữ resource và vòng đời JVM; cần ownership rõ ràng.

**Lỗi 12 — Cho rằng shutdownNow kill mọi task.** Nó nỗ lực interrupt, nhưng task phải hợp tác. Cần cleanup/await/deadline.

**Lỗi 13 — Tăng max để sửa thread-starvation deadlock nhưng queue vẫn unbounded.** Queue vẫn nhận task con trong khi worker cha chờ; phải xem mô hình dependency.

**Lỗi 14 — Tin rằng activeCount/queueSize là snapshot bất biến.** Metrics đổi liên tục; dùng cho monitoring, không cho check-then-act correctness.

**Lỗi 15 — Đổi core/max theo thứ tự khiến core > max tạm thời.** Setter có validation; phải lên kế hoạch thứ tự phù hợp.

**Lỗi 16 — Cho rằng CPU-bound và I/O-bound dùng cùng bộ tham số.** Đặc điểm block và downstream resource làm lựa chọn khác nhau.

**Lỗi 17 — Dùng thread pool để che race condition trong task.** Executor quản lý lịch chạy, không tự đồng bộ mutable state mà tasks cùng sửa.

**Lỗi 18 — Nhầm queue size với tổng task đang hoạt động.** Queue chỉ có tasks **chưa chạy**; active workers đang xử lý tasks ngoài queue.

## 20. Checklist review một ThreadPoolExecutor

- Xác định rõ `corePoolSize`, `maximumPoolSize`, `keepAliveTime` và `TimeUnit`.
- Có biết loại `BlockingQueue` và **capacity thật sự** không?
- Có hiểu quy tắc **core → queue → extra → reject** trong tải giả định không?
- Với unbounded queue, đã biết maximumPoolSize thường không giúp backlog chưa?
- Với bounded queue, đã mô tả behavior khi queue đầy và worker đạt max chưa?
- Đã xác định policy cho rejected tasks, tránh mất việc im lặng chưa?
- `allowCoreThreadTimeOut` có bật không, keepAlive có >0 khi bật không?
- Có cần prestart core workers hay chấp nhận lazy startup?
- Workers làm CPU-bound, blocking I/O hay hỗn hợp? Có downstream concurrency limit?
- Có task cha chờ task con trong cùng pool? Có nguy cơ starvation deadlock?
- Có policy timeout/cancellation riêng cho task, không dùng keepAlive thay thế?
- Có metrics về active workers, queue depth, rejection, task duration và tail latency?
- Đã thiết kế `shutdown`, `awaitTermination`, `shutdownNow` và cleanup an toàn?
- Đã load-test cả burst và overload, không chỉ happy path?

## 21. Câu hỏi tự luyện và đáp án

**Câu 1.** Pool có core=2, max=16, queue `LinkedBlockingQueue<>` mặc định. Sau khi có hai worker, submit thêm 100 task lâu. Pool có tự tăng lên 16 worker không?

**Đáp án:** Thông thường không. Queue tiếp tục nhận task, nên executor ưu tiên queue sau core. MaximumPoolSize có rất ít tác dụng nếu queue hầu như không đầy.

**Câu 2.** Pool có core=2, max=4, bounded queue capacity=3. Hai worker đang bận, queue đang có 3 task, thêm 1 task thì sao?

**Đáp án:** Executor có thể tạo worker thứ 3 để chạy task đến, vì queue không nhận thêm và tổng worker còn dưới max.

**Câu 3.** Nếu pool đã có 4 workers đều bận, queue capacity=3 đã đầy, một task mới đến thì sao?

**Đáp án:** Executor áp dụng `RejectedExecutionHandler`, hoặc ném `RejectedExecutionException` nếu dùng AbortPolicy.

**Câu 4.** keepAlive=5 giây, một task đang chạy 30 giây. Task có bị tự ngắt ở giây thứ 5 không?

**Đáp án:** Không. Keep-alive áp dụng thời gian worker **idle**, không giới hạn thời gian task đang chạy.

**Câu 5.** core=3 nhưng gọi constructor xong chưa submit task. Có chắc poolSize=3 không?

**Đáp án:** Không. Workers được tạo lazily; có thể poolSize=0 nếu chưa prestart.

**Câu 6.** Bật allowCoreThreadTimeOut(true) khi keepAliveTime=0 được không?

**Đáp án:** Không. API yêu cầu keepAliveTime > 0 cho core-thread timeout.

**Câu 7.** Vì sao CallerRunsPolicy có thể tạo backpressure nhưng lại nguy hiểm cho một event-loop thread?

**Đáp án:** Khi pool quá tải, caller trực tiếp chạy task, làm nó không submit tiếp được nhanh; nhưng event-loop sẽ bị chiếm bởi task, ảnh hưởng latency và khả năng phục vụ các sự kiện khác.

**Câu 8.** `Future.get(100, MILLISECONDS)` timeout có khiến worker pool thu hồi thread hay hủy task ngay không?

**Đáp án:** Không. Đây chỉ là timeout của caller khi chờ Future, độc lập với keepAlive/worker lifecycle và cancellation.

**Câu 9.** Nếu dùng `SynchronousQueue`, queue có thể giữ 100 task chờ không?

**Đáp án:** Không. Đây là direct-handoff queue, không lưu backlog; khi không handoff được, pool có thể tăng workers tới max hoặc reject tùy cấu hình.

**Câu 10.** Pool có một worker đang chạy task A; A submit B vào cùng pool rồi gọi Future.get(), trong khi B chờ trong queue. Nguy cơ gì?

**Đáp án:** Thread-starvation deadlock: worker duy nhất đợi B, nhưng B không có worker để chạy. Phải thay thiết kế dependency hoặc capacity/admission phù hợp.

## 22. Tài liệu tham khảo chính thức

- **ThreadPoolExecutor — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- **ExecutorService — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html
- **BlockingQueue — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/BlockingQueue.html
- **ArrayBlockingQueue — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html
- **LinkedBlockingQueue — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html
- **SynchronousQueue — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SynchronousQueue.html
- **ThreadPoolExecutor.RejectedExecutionHandler — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html
- **TimeUnit — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/TimeUnit.html

**Kết luận:** Ba tham số **`corePoolSize`**, **`maximumPoolSize`** và **`keepAliveTime`** chỉ có ý nghĩa đúng khi đặt cạnh **workQueue** và **rejection policy**. Pool thường tạo tới core, sau đó queue, khi queue không nhận nữa mới mở rộng tới max, và cuối cùng reject khi cả workers lẫn queue đã hết capacity. Keep-alive thu hồi **worker idle**, không hủy task dài. Cấu hình đúng phải bảo vệ đồng thời **throughput, độ trễ, memory, tài nguyên ngoài và hành vi khi quá tải**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-006">Work queue: ArrayBlockingQueue, LinkedBlockingQueue</a>

<details>
<summary>Xem chi tiết</summary>

# Work queue: ArrayBlockingQueue và LinkedBlockingQueue trong Executor Framework

Khi gọi `execute()` hoặc `submit()` trên một `ThreadPoolExecutor`, không phải mọi tác vụ đều lập tức có thread chạy. Nếu số worker hiện có đang bận, executor có thể đưa những `Runnable` chưa bắt đầu vào **work queue**. Hai loại queue quan trọng của Java là `ArrayBlockingQueue` và `LinkedBlockingQueue`. Sự khác biệt về **dung lượng**, **cách lưu phần tử**, cùng thuật toán tiếp nhận tác vụ của `ThreadPoolExecutor` ảnh hưởng đến số worker được tạo, độ trễ hàng chờ, bộ nhớ và quyết định từ chối task khi quá tải.

- `ArrayBlockingQueue<E>`: queue FIFO dựa trên **mảng có dung lượng cố định** ngay từ constructor.
- `LinkedBlockingQueue<E>`: queue FIFO sử dụng cấu trúc liên kết; có constructor **bounded** và constructor mặc định có capacity **`Integer.MAX_VALUE`**, thường được mô tả là *effectively unbounded*.
- Cả hai triển khai `BlockingQueue<E>`, có các phương thức chờ/không chờ khi thêm hoặc lấy phần tử.
- Trong `ThreadPoolExecutor`, **work queue đầy không có nghĩa thread nộp task sẽ tự chờ**. Executor thường dùng `offer()`: nếu thất bại, nó thử tạo thêm worker tới `maximumPoolSize`; nếu không thể, áp dụng `RejectedExecutionHandler`.

Đây là bài về Java concurrency và Executor Framework của JDK, không dùng framework khác. Các ví dụ độc lập dùng JDK 17+.

## 1. Work queue làm gì trong executor?

Một cách nhìn đơn giản:

```text
execute(task) / submit(task)
           |
           v
    ThreadPoolExecutor
       /        \
      v          v
   Workers    BlockingQueue<Runnable>
   chạy task  giữ các task chưa chạy
      ^          |
      +----------+
           |
           v
  Rejection nếu không thể nhận việc
```

**Task đang chạy** thuộc worker; **task chờ** nằm trong queue. Queue có thể chứa `Runnable` do caller gửi hoặc wrapper task do `submit()` tạo ra. Một task xếp hàng lâu vẫn giữ tham chiếu tới dữ liệu/đối tượng mà nó capture; vì vậy queue tăng liên tục có thể gây **GC pressure**, tăng thời gian chờ và dẫn đến cạn heap.

Tham số thiết lập pool thường gồm:

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        2, 4, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(10),
        new ThreadPoolExecutor.AbortPolicy()
);
```

Đây là **trích đoạn minh họa**, cần import `java.util.concurrent.*`. Pool có tối thiểu `corePoolSize = 2` worker theo mục tiêu khi có tác vụ, tối đa `maximumPoolSize = 4` worker, và queue chứa tối đa 10 task chưa được worker lấy. Chính sách `AbortPolicy` ném `RejectedExecutionException` nếu không tiếp nhận được task.

**Quan trọng:** constructor không tự khởi động đủ core thread. Worker thường được tạo theo nhu cầu; có API `prestartCoreThread()` hoặc `prestartAllCoreThreads()` nếu cần tạo trước.

## 2. API quan trọng của BlockingQueue

`BlockingQueue<E>` phân biệt rõ cách ứng xử khi queue **đầy** hoặc **rỗng**:

| Thao tác | Khi có thể thực hiện | Khi chưa thể thực hiện |
|---|---|---|
| `add(e)` | Thêm phần tử và trả `true`. | Ném `IllegalStateException` nếu đầy. |
| `offer(e)` | Thêm và trả `true`. | Trả `false` ngay nếu đầy. |
| `put(e)` | Thêm phần tử. | **Chờ** tới khi có chỗ, có thể bị interrupt. |
| `offer(e, timeout, unit)` | Chờ có giới hạn rồi thêm. | Trả `false` khi hết hạn, hoặc ném `InterruptedException`. |
| `remove()` | Lấy và xóa phần tử đầu. | Ném `NoSuchElementException` nếu rỗng. |
| `poll()` | Lấy và xóa phần tử đầu. | Trả `null` ngay nếu rỗng. |
| `take()` | Lấy phần tử. | **Chờ** tới khi có dữ liệu, có thể bị interrupt. |
| `poll(timeout, unit)` | Chờ có giới hạn rồi lấy. | Trả `null` khi hết hạn, hoặc bị interrupt. |
| `peek()` | Xem phần tử đầu, không xóa. | Trả `null` nếu rỗng. |

Hai queue này đều cấm phần tử `null`; kết quả `null` từ `poll()` vì vậy có thể biểu thị không có item. `size()` và `remainingCapacity()` chỉ mô tả trạng thái ở **thời điểm đọc**. Không viết `if (remainingCapacity() > 0) add(item)` rồi cho rằng thao tác chắc chắn thành công: producer khác có thể chen vào. Hãy gọi trực tiếp `offer()` hoặc `put()` theo hợp đồng.

Trong producer–consumer, `put()` và `take()` thường có ích; trong đường tiếp nhận tác vụ bình thường của `ThreadPoolExecutor`, executor dùng `offer()` để không chặn caller tại queue.

## 3. ArrayBlockingQueue: mảng cố định và capacity rõ ràng

Tạo một queue có giới hạn:

```java
BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
```

Đoạn này là trích đoạn, cần import `BlockingQueue` và `ArrayBlockingQueue`. Dung lượng 100 được xác định khi tạo, không tự động tăng. Khi đủ 100 phần tử: `offer()` trả false; `put()` đợi; `offer(timeout,...)` có thể đợi tới hạn.

Đặc tính:

- **FIFO:** phần tử trong queue được lấy theo thứ tự enqueue; không bảo đảm task được nhiều worker thực thi và **hoàn thành** theo đúng thứ tự.
- **Array được cấp phát theo capacity:** không tạo một node liên kết mới cho mỗi phần tử như cấu trúc linked. Tổng hiệu năng phụ thuộc workload và contention, không nên kết luận “array luôn nhanh hơn”.
- **Fairness tùy chọn:** `new ArrayBlockingQueue<>(capacity, true)` yêu cầu chính sách công bằng khi các thread tranh quyền truy cập queue, thường đánh đổi throughput. Nó **không** hứa sự công bằng toàn bộ của `ThreadPoolExecutor`.
- **Bounded backlog:** giúp giới hạn số task **đang chờ** và làm quá tải biểu hiện rõ qua queue full/rejection.

Khi dùng queue bounded, phải quyết định cách xử lý nếu pool đạt `maximumPoolSize` và queue hết chỗ. Bounded queue **không** đồng nghĩa mọi task đều được chấp nhận.

## 4. LinkedBlockingQueue: giới hạn rõ hoặc capacity rất lớn

Hai constructor:

```java
BlockingQueue<Runnable> defaultCapacity =
        new LinkedBlockingQueue<>();

BlockingQueue<Runnable> bounded =
        new LinkedBlockingQueue<>(100);
```

Đây là trích đoạn, cần import `java.util.concurrent.*`.

- Không truyền capacity: `LinkedBlockingQueue` mặc định có capacity `Integer.MAX_VALUE`. “Unbounded” là cách gọi tắt; không có nghĩa RAM vô hạn. Nếu task được nộp liên tục nhanh hơn xử lý, queue có thể chiếm rất nhiều heap trước khi đạt giới hạn số nguyên.
- Có truyền capacity: queue giữ tối đa số phần tử đó. Với `LinkedBlockingQueue<>(100)`, `offer()` trả `false` khi đã có 100 phần tử chưa bị lấy khỏi queue.
- Dùng linked nodes nên có chi phí bộ nhớ/cấp phát tương ứng các phần tử và cách implementation. Không có constructor `fair` giống `ArrayBlockingQueue`.
- FIFO theo thứ tự enqueue. Với nhiều thread cùng submit, thứ tự được chấp nhận phụ thuộc lịch chạy, không phải luôn thứ tự các dòng log của caller.

**Điểm khác biệt ở executor:** `LinkedBlockingQueue<>()` mặc định thường **tiếp nhận gần như mọi task đến sau core workers vào queue**, khiến executor không có lý do tăng worker tới `maximumPoolSize` trong đường admission bình thường. Một `LinkedBlockingQueue<>(boundedCapacity)` khi đầy **cũng có thể khiến pool tạo thêm worker**, giống ArrayBlockingQueue bounded.

## 5. Ví dụ 1 — Quan sát bounded capacity qua API queue

Tạo tệp **`BlockingQueueBasics.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockingQueueBasics {
    public static void main(String[] args) throws InterruptedException {
        ArrayBlockingQueue<String> array =
                new ArrayBlockingQueue<>(1);
        System.out.println("Array offer A: " + array.offer("A"));
        System.out.println("Array offer B: " + array.offer("B"));
        System.out.println("Array size: " + array.size());
        System.out.println("Array take: " + array.take());

        LinkedBlockingQueue<String> linked =
                new LinkedBlockingQueue<>(2);
        System.out.println("Linked offer X: " + linked.offer("X"));
        System.out.println("Linked offer Y: " + linked.offer("Y"));
        System.out.println("Linked offer Z: " + linked.offer("Z"));
        System.out.println("Linked remaining: "
                + linked.remainingCapacity());

        List<String> removed = new ArrayList<>();
        linked.drainTo(removed);
        System.out.println("Drained: " + removed);
        System.out.println("Linked size: " + linked.size());
    }
}
```

```bash
javac BlockingQueueBasics.java
java BlockingQueueBasics
```

Kết quả:

```text
Array offer A: true
Array offer B: false
Array size: 1
Array take: A
Linked offer X: true
Linked offer Y: true
Linked offer Z: false
Linked remaining: 0
Drained: [X, Y]
Linked size: 0
```

Ví dụ đơn luồng cố ý dùng `offer()` ở thời điểm queue đầy để **không treo chương trình** như `put()`. `drainTo()` chuyển các item hiện có sang danh sách, **không chờ** dữ liệu mới. `ArrayBlockingQueue` và `LinkedBlockingQueue` đều hỗ trợ các thao tác `BlockingQueue`; điểm cần hiểu ở đây là việc capacity làm `offer()` thất bại.

## 6. Quy tắc admission: core → queue → maximum → rejection

Khi caller gọi `ThreadPoolExecutor.execute(command)`, mô hình hoạt động thông thường là:

1. Nếu số worker **nhỏ hơn `corePoolSize`**, pool cố tạo worker mới để chạy task.
2. Nếu đã đạt core, pool thử **`workQueue.offer(command)`**.
3. Nếu offer thành công, task nằm trong queue chờ worker. Pool cũng **kiểm tra lại trạng thái chạy/shutdown** và điều kiện có worker nhằm xử lý các race đồng thời.
4. Nếu queue không nhận task, pool thử tạo thêm worker tới **`maximumPoolSize`**.
5. Nếu không thể thêm worker hoặc executor đã shutdown, dùng `RejectedExecutionHandler`.

```text
                    execute(task)
                         |
                 workers < core ?
                    /         \
                  yes         no
                   |           |
              create worker   queue.offer(task)?
                                 /           \
                               yes           no
                                |             |
                            queue waits   workers < max?
                                            /       \
                                          yes       no
                                           |         |
                                      create worker  reject
```

Đây là mô hình đơn giản hóa; các quyết định thực tế cần đối phó với concurrent shutdown và thay đổi worker count.

**Hệ quả hay bị bỏ qua:** `maximumPoolSize` thường **chỉ có tác dụng khi queue không nhận thêm được**. Nếu queue có capacity rất lớn, nhiều task sẽ chờ trong queue trong khi số worker vẫn gần `corePoolSize`. Tăng maximum từ 4 lên 100 **không tự rút ngắn** queue đang tăng nếu LinkedBlockingQueue mặc định luôn nhận thêm task.

Ví dụ với `core=1`, `max=2`, queue capacity 1:

- Task thứ nhất → tạo core worker.
- Task thứ hai → vào queue.
- Task thứ ba → queue đầy, tạo worker thứ hai.
- Task thứ tư → nếu hai worker đều bận và queue vẫn đầy, **reject**.

**Không nhầm** `newFixedThreadPool(n)` với queue bounded: implementation thông thường sử dụng một `LinkedBlockingQueue` không giới hạn nhỏ theo ứng dụng, nên tải vượt năng lực xử lý có thể tạo backlog lớn; chọn factory/config phù hợp theo mục tiêu.

## 7. Ví dụ 2 — Bounded ArrayBlockingQueue làm pool mở thêm worker

Dùng `CountDownLatch` để giữ task #1 và #3 đang chạy, trong khi task #2 nằm trong queue. Nhờ đó ta kiểm tra chính xác pool đã mở thêm worker ở bước nào mà không phụ thuộc tình cờ vào thread scheduler.

Tạo tệp **`ExecutorBoundedAdmission.java`**:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorBoundedAdmission {
    static void awaitRelease(CountDownLatch release) {
        try {
            release.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy()
        );
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch thirdStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            executor.execute(() -> {
                firstStarted.countDown();
                awaitRelease(release);
            });
            firstStarted.await();

            executor.execute(() -> { }); // Task thứ hai đợi trong queue.

            executor.execute(() -> {
                thirdStarted.countDown();
                awaitRelease(release);
            });
            thirdStarted.await();

            System.out.println("Pool size: " + executor.getPoolSize());
            System.out.println("Active workers: " + executor.getActiveCount());
            System.out.println("Queued tasks: " + executor.getQueue().size());
        } finally {
            release.countDown();
            executor.shutdown();
        }

        boolean terminated = executor.awaitTermination(
                5, TimeUnit.SECONDS);
        System.out.println("Terminated: " + terminated);
        System.out.println("Completed tasks: "
                + executor.getCompletedTaskCount());
    }
}
```

```bash
javac ExecutorBoundedAdmission.java
java ExecutorBoundedAdmission
```

Kết quả trong lần chạy bình thường, không bị interrupt từ bên ngoài:

```text
Pool size: 2
Active workers: 2
Queued tasks: 1
Terminated: true
Completed tasks: 3
```

Task #1 chiếm core worker và đợi latch; task #2 xếp hàng vì queue còn chỗ; task #3 làm `offer()` thất bại do queue đầy, vì vậy pool tạo worker thứ hai (không vượt max). Main đợi cả task #1 và #3 đạt trạng thái đang chạy trước khi đọc metrics, nên hàng đợi vẫn chứa task #2. Khi `release.countDown()`, executor hoàn tất các task được chấp nhận, rồi shutdown đúng hợp đồng.

Trong ứng dụng thực tế, `getActiveCount()` và queue size là các giá trị chẩn đoán có thể thay đổi tức thì; ở ví dụ này chúng ổn định vì latch giữ task lại.

## 8. Ví dụ 3 — LinkedBlockingQueue mặc định giữ task ở queue

Dùng pool `corePoolSize=1`, `maximumPoolSize=3` nhưng `LinkedBlockingQueue<>()` capacity mặc định. Khi core worker đang bận, hai task tiếp theo **được enqueue**; pool thường không mở thêm worker.

Tạo tệp **`ExecutorUnboundedQueue.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecutorUnboundedQueue {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 3, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            started.await();

            executor.execute(() -> { });
            executor.execute(() -> { });

            System.out.println("Pool size: " + executor.getPoolSize());
            System.out.println("Active workers: " + executor.getActiveCount());
            System.out.println("Queued tasks: " + executor.getQueue().size());
        } finally {
            release.countDown();
            executor.shutdown();
        }

        boolean terminated = executor.awaitTermination(
                5, TimeUnit.SECONDS);
        System.out.println("Terminated: " + terminated);
        System.out.println("Completed tasks: "
                + executor.getCompletedTaskCount());
    }
}
```

```bash
javac ExecutorUnboundedQueue.java
java ExecutorUnboundedQueue
```

Kết quả:

```text
Pool size: 1
Active workers: 1
Queued tasks: 2
Terminated: true
Completed tasks: 3
```

Chỉ core worker được tạo vì queue nhận các task mới thành công. `maximumPoolSize=3` là trần, **không phải mục tiêu tự mở tới ba worker**. Nếu đổi thành `new LinkedBlockingQueue<Runnable>(1)`, queue có thể đầy và khi đó pool có cơ hội mở non-core worker giống ví dụ ArrayBlockingQueue.

## 9. Saturation và RejectedExecutionHandler

Khi worker đạt max và queue đầy, executor dùng rejection policy:

| Policy | Ứng xử | Chú ý |
|---|---|---|
| `AbortPolicy` | Ném `RejectedExecutionException`. | Mặc định; caller cần xử lý lỗi hoặc giới hạn retry. |
| `CallerRunsPolicy` | Nếu executor chưa shutdown, chạy task ngay trong **thread gọi `execute()`**. | Có thể tạo backpressure nhưng làm chậm caller; khi executor đã shutdown, task có thể bị bỏ. |
| `DiscardPolicy` | Bỏ task mà không ném. | Chỉ dùng khi có thể chấp nhận mất việc. |
| `DiscardOldestPolicy` | Thử bỏ task ở đầu queue rồi thử execute lại. | Có thể mất công việc đã chờ; không phù hợp với mọi policy/queue. |

Queue bounded giúp giới hạn backlog nhưng **không tự cung cấp backpressure mong muốn**. Cần chọn policy có chủ đích: báo lỗi có thể quan sát, caller-runs khi thích hợp, retry có giới hạn hoặc giảm tốc nhà sản xuất task.

### Ví dụ 4 — CallerRunsPolicy chạy task trên thread nộp

Tạo tệp **`CallerRunsBackpressure.java`**:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CallerRunsBackpressure {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        Thread caller = Thread.currentThread();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean ranOnCaller = new AtomicBoolean();

        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            started.await();

            executor.execute(() -> { }); // Một task ở queue.
            executor.execute(() -> {
                ranOnCaller.set(Thread.currentThread() == caller);
            });

            System.out.println("Third ran on caller: "
                    + ranOnCaller.get());
        } finally {
            release.countDown();
            executor.shutdown();
        }

        boolean terminated = executor.awaitTermination(
                5, TimeUnit.SECONDS);
        System.out.println("Terminated: " + terminated);
        System.out.println("Pool completed: "
                + executor.getCompletedTaskCount());
    }
}
```

```bash
javac CallerRunsBackpressure.java
java CallerRunsBackpressure
```

Kết quả trong lần chạy bình thường:

```text
Third ran on caller: true
Terminated: true
Pool completed: 2
```

Task thứ ba chạy **đồng bộ trong caller**, không phải thread của pool. Vì vậy `getCompletedTaskCount()` chỉ đếm hai task worker đã hoàn tất, không đếm task chạy ngoài pool. Nếu caller là thread xử lý yêu cầu nhạy cảm latency hoặc event loop, `CallerRunsPolicy` có thể làm nghẽn đường nhận yêu cầu. Cân nhắc bối cảnh trước khi chọn.

## 10. FIFO queue khác FIFO hoàn thành

Hai queue có ngữ nghĩa FIFO cho các phần tử **đã nằm trong queue**. Nhưng `ThreadPoolExecutor` không hứa “task submit trước luôn hoàn tất trước”:

- Có nhiều worker, task bắt đầu trước có thể mất thời gian lâu hơn task bắt đầu sau.
- Nếu bounded queue đầy, task mới có thể được giao **thẳng cho non-core worker** dù task cũ còn trong queue. Trong ví dụ pool có core=1,max=2, task #3 có thể bắt đầu trước task #2.
- Các caller đồng thời submit tạo ra thứ tự enqueue phụ thuộc lịch chạy.
- `CallerRunsPolicy` chạy task tại submitter, không phải qua hàng đợi chờ worker.

Nếu yêu cầu nghiệp vụ có thứ tự nghiêm ngặt, cần thiết kế serialization/order riêng. Một single-thread executor loại bỏ xử lý song song trên worker nhưng rejection policy, cancellation và các callback ngoài executor vẫn cần được xét; không suy ordering chỉ từ tên FIFO của BlockingQueue.

## 11. Khi nào chọn ArrayBlockingQueue hoặc LinkedBlockingQueue?

| Tiêu chí | `ArrayBlockingQueue` | `LinkedBlockingQueue` |
|---|---|---|
| Lưu trữ | Mảng dung lượng cố định. | Node liên kết theo phần tử. |
| Capacity mặc định | Phải truyền vào. | `Integer.MAX_VALUE` khi không truyền. |
| Bounded | Luôn bounded. | Bounded nếu chỉ định capacity. |
| FIFO | Có. | Có. |
| `put/take/offer/poll` | Có. | Có. |
| Fairness trong constructor | Có option `fair`. | Không có option tương đương. |
| Hậu quả queue đầy với executor | Có thể tạo thêm worker tới max rồi reject. | Cũng vậy **nếu bounded**; bản mặc định khó đầy trước khi bộ nhớ trở thành vấn đề. |
| Bảo trì/hiệu năng | Dễ dự toán bộ nhớ slots; ít overhead cấp phát node. | Có overhead node và khả năng tăng queue khi không bounded; đo trong workload thật. |

**Chọn capacity trước khi chọn implementation.** Nếu mục tiêu là memory/latency budget rõ ràng, cả `ArrayBlockingQueue<>(n)` và `LinkedBlockingQueue<>(n)` đều cung cấp giới hạn. Khác biệt hiệu năng/locking của implementation phải được đo đạc với workload thực tế; không có quy tắc “Linked luôn nhanh hơn Array”.

## 12. Thiết kế capacity, throughput và backpressure

Giả sử hệ thống nhận **500 task/giây** nhưng các worker ổn định chỉ xử lý **300 task/giây**. Trong thời gian quá tải, backlog tăng khoảng **200 task/giây**. Tăng queue capacity chỉ **trì hoãn** lúc đầy, không tạo thêm năng lực xử lý; task xếp hàng lâu có thể hết thời hạn nghiệp vụ trước khi chạy.

Một queue quá lớn thường gây:

- Độ trễ trước khi task bắt đầu tăng.
- Giữ hàng loạt object được task tham chiếu, tăng bộ nhớ và GC.
- Che giấu overload thay vì buộc caller giảm tốc.
- Tác vụ bị hủy/không còn ý nghĩa vẫn có thể nằm trong queue chờ xử lý.

Queue quá ngắn cùng maxPoolSize nhỏ có thể reject sớm; điều này không nhất thiết sai nếu policy bên gửi biết giảm tải. Queue càng bounded và max càng cao không đồng nghĩa throughput tốt hơn: quá nhiều worker tranh CPU, kết nối database hoặc hệ thống ngoài có thể làm chậm hơn.

Khi đặt thông số, hãy quan sát **arrival rate**, **service rate**, thời gian task chờ, thời gian chạy, active workers, queue depth, rejection count, heap và latency p95/p99. Với task CPU-bound, số worker thường phải phù hợp tài nguyên CPU; với task I/O-bound, số worker có thể khác nhưng vẫn bị giới hạn bởi downstream và bộ nhớ.

## 13. Không thao tác trực tiếp work queue như một API submit

`executor.getQueue()` cho phép quan sát queue. Nhưng không nên tự gọi `executor.getQueue().put(task)` để nộp công việc vì việc này có thể **bỏ qua cơ chế** `execute()`: kiểm tra shutdown, tạo worker, chính sách reject và một số logic quản lý task.

Dùng `execute()` hoặc `submit()` để gửi nhiệm vụ. Các metric như:

```java
int queued = executor.getQueue().size();
int active = executor.getActiveCount();
long completed = executor.getCompletedTaskCount();
int capacity = executor.getQueue().remainingCapacity();
```

là trích đoạn cần `executor` đã khai báo, và là **quan sát tức thời/xấp xỉ**, không phải giao thức ra quyết định nguyên tử. Không xây code `if (getQueue().size() < limit) executor.execute(...)` rồi giả định chắc chắn còn chỗ: state có thể đổi trước lời `execute()`.

`drainTo()` không phải “chờ executor chạy hết task”; nó chuyển các phần tử còn trong queue sang collection đích. Việc can thiệp queue trực tiếp để xóa/hủy cần thiết kế rất cẩn thận vì có thể phá hợp đồng hoàn tất/quan sát task của caller.

## 14. Shutdown, cancellation và task chờ

Executor không “đóng” work queue bằng một lời `BlockingQueue.close()` tổng quát. Chủ sở hữu quản lý vòng đời qua:

- `shutdown()`: ngừng nhận task mới, các task đã được chấp nhận có thể chạy cho tới hoàn tất.
- `shutdownNow()`: cố interrupt các task đang chạy, ngừng xử lý task còn chờ và trả những task chưa bắt đầu; không bảo đảm thread sẽ ngừng ngay.
- `awaitTermination(timeout, unit)`: đợi pool thật sự terminated, trả boolean cho biết đã hoàn tất trước hạn không.
- `Future.cancel(true)`: yêu cầu hủy task; với task đang chạy có thể cố interrupt nhưng không bảo đảm cleanup đã hoàn tất ngay khi method trả về.
- `getQueue().size()` chỉ cho biết backlog hiện tại, không phản ánh task đang thực thi.

Task đã hủy có thể còn được queue giữ cho tới khi được lấy hoặc dọn bằng cơ chế phù hợp. `ThreadPoolExecutor.purge()` có thể loại bỏ một số task Future đã cancel khỏi queue, nhưng không phải công cụ thay cho capacity và lifecycle đúng. Task giữ object lớn trong lúc queued là yếu tố phải dự toán memory.

Với `submit()`, ngoại lệ của tác vụ được đưa vào `Future` và thường được phát hiện qua `get()`; với `execute()`, cách exception ảnh hưởng worker và nơi xử lý lỗi có khác. Không thiết kế hệ thống coi một queue chưa đầy là bằng chứng nhiệm vụ chắc chắn thành công.

## 15. Java Memory Model và chuyển giao dữ liệu qua BlockingQueue

Hợp đồng memory consistency của `BlockingQueue` cung cấp quan hệ **happens-before**: hành động trong producer **trước khi đưa một object vào queue** happens-before hành động của consumer **sau khi truy cập/lấy object đó ra**. Điều này giúp chuyển giao dữ liệu được xây dựng trước enqueue.

Ví dụ ý tưởng:

```java
// Producer:
Message message = new Message();
message.value = 42;
queue.put(message);

// Consumer trong thread khác:
Message result = queue.take();
System.out.println(result.value);
```

Đây là **trích đoạn** cần class `Message` và queue phù hợp, không phải chương trình độc lập. Không cần `volatile` trên mọi field của payload chỉ để chuyển giao **một chiều đúng cách** qua queue. Nhưng nếu producer tiếp tục sửa cùng object sau enqueue trong khi consumer đọc, quan hệ queue không bảo vệ các mutation cạnh tranh đó; ưu tiên payload immutable hoặc quy tắc chuyển quyền sở hữu rõ ràng.

Đối với executor, tác vụ được submit thường mang tham chiếu tới dữ liệu mà nó cần. An toàn dữ liệu trong các worker vẫn dựa trên Java Memory Model và giao thức đồng bộ hóa thích hợp; queue không biến mọi object mutable được capture thành thread-safe.

## 16. Checklist và tự kiểm tra

Checklist trước khi đưa executor vào hệ thống:

1. Xác định `corePoolSize`, `maximumPoolSize`, tốc độ đến và thời gian chạy task.
2. Chọn queue bounded hay effectively unbounded **có chủ đích**, không để mặc định vì tiện.
3. Ước lượng memory và latency chờ dựa trên số queued task và object graph chúng giữ.
4. Hiểu admission **core → offer queue → maximum → rejection**.
5. Chọn rõ `RejectedExecutionHandler`; xử lý overload theo hợp đồng nghiệp vụ.
6. Không dùng queue FIFO như lời hứa thứ tự thực thi/hoàn thành.
7. Không submit tác vụ bằng `getQueue().add()`/`put()`.
8. Quản lý shutdown, cancellation, timeout và cleanup.
9. Theo dõi queue depth, active workers, rejection, task duration và GC.
10. Stress-test khi task bị block, queue đầy và executor shutdown đồng thời.

Câu hỏi kiểm tra:

1. **ArrayBlockingQueue có constructor không truyền capacity không?** Không, capacity phải xác định khi tạo.
2. **LinkedBlockingQueue không tham số có capacity gì?** `Integer.MAX_VALUE` — không phải RAM vô hạn.
3. **LinkedBlockingQueue có thể bounded không?** Có, dùng constructor nhận capacity.
4. **Tại sao `maximumPoolSize=20` vẫn có thể chỉ dùng 2 worker?** Vì queue còn nhận thêm task sau khi core có 2 worker, nên không có lý do mở non-core trong đường admission bình thường.
5. **Executor có gọi `queue.put(task)` và chặn caller khi queue đầy không?** Đường admission thông thường dùng `offer()`; queue đầy khiến pool thử tạo thêm worker hoặc reject.
6. **Queue FIFO có bảo đảm thứ tự task hoàn tất không?** Không, vì nhiều worker, thời lượng task khác nhau và task mới có thể được giao trực tiếp cho worker.
7. **CallerRunsPolicy có thể tạo backpressure ra sao?** Task bị reject có thể chạy ngay trên caller, làm caller không thể tiếp tục submit cho tới khi task kết thúc.
8. **Khi shutdown(), những task đã ở queue có tự bị bỏ hết không?** Không. `shutdown()` cho phép task đã nhận được chạy cho tới khi executor kết thúc; `shutdownNow()` là cơ chế cố dừng khác.
9. **Đọc `remainingCapacity()` rồi `add()` có an toàn trong cạnh tranh không?** Không; dùng `offer()` hoặc `put()` theo contract.
10. **Vì sao queue rất lớn có thể làm hệ thống chậm và hết heap?** Backlog giữ object trong RAM và tạo queue wait time, không làm service rate tự tăng.

**Ghi nhớ:** `ArrayBlockingQueue` luôn bounded; `LinkedBlockingQueue` có thể bounded nhưng mặc định có capacity rất lớn. Trong `ThreadPoolExecutor`, hiểu chính xác **core → queue.offer → maximum → rejection** để chọn queue, capacity và policy theo ngân sách tài nguyên và độ trễ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-007">RejectedExecutionHandler và bốn chính sách từ chối</a>

<details>
<summary>Xem chi tiết</summary>

# RejectedExecutionHandler và bốn chính sách từ chối trong Java

## 1. Vì sao ThreadPoolExecutor phải từ chối công việc?

`ThreadPoolExecutor` quản lý số luồng làm việc và hàng đợi `BlockingQueue<Runnable>`. Khi lượng công việc đến nhanh hơn khả năng xử lý, executor phải quyết định điều gì xảy ra với một tác vụ mới thay vì nhận vô hạn và khiến bộ nhớ, độ trễ hoặc tài nguyên tăng mất kiểm soát.

`RejectedExecutionHandler` là giao diện định nghĩa hành động **khi một tác vụ không được `ThreadPoolExecutor` chấp nhận**. Đây là cơ chế của **Java Concurrency trong `java.util.concurrent`**, áp dụng độc lập với bất kỳ framework nào.

Có hai trường hợp chính kích hoạt từ chối:

1. **Quá tải (saturation):** số worker đã đạt `maximumPoolSize` và hàng đợi không thể nhận thêm tác vụ. Một cấu hình dùng hàng đợi có dung lượng giới hạn giúp sự kiện này xảy ra rõ ràng.
2. **Executor đang đóng/đã đóng:** sau `shutdown()` hoặc `shutdownNow()`, tác vụ mới không còn được nhận, ngay cả khi hàng đợi vẫn còn chỗ. Những thay đổi trạng thái đồng thời với thao tác nhận việc cũng có thể khiến tác vụ vừa đưa vào hàng đợi bị gỡ ra rồi chuyển sang handler.

Ví dụ: `corePoolSize = 1`, `maximumPoolSize = 1`, hàng đợi chứa tối đa 1 tác vụ. Khi A đang chạy, B đang đợi và C được gửi tới, C sẽ bị từ chối; **chính sách từ chối** quyết định số phận C. Ngược lại, nếu executor đã đóng, chính sách cũng được gọi khi gửi C, không phụ thuộc độ đầy của hàng đợi.

> “Rejected” không đồng nghĩa “task đã chạy và thất bại”. Nó có nghĩa executor **không chấp nhận giao tác vụ đó để thực hiện** theo cơ chế thông thường. Một handler có thể ném lỗi, thực thi bằng luồng gọi, bỏ tác vụ hoặc tự định tuyến lại.

## 2. Thứ tự nhận việc và điểm kích hoạt handler

Đơn giản hóa luồng `execute(command)` của `ThreadPoolExecutor`:

1. Nếu số worker đang tồn tại thấp hơn `corePoolSize`, cố tạo worker mới để xử lý `command`.
2. Nếu không tạo worker ở bước 1 và executor còn ở trạng thái nhận việc, thử đưa `command` vào `workQueue`.
3. Sau khi xếp hàng thành công, **kiểm tra lại trạng thái executor** để xử lý cuộc đua với `shutdown`; nếu đã đóng và gỡ tác vụ khỏi queue thành công, tác vụ bị từ chối. Executor cũng bảo đảm có worker khi cần.
4. Nếu queue không nhận `command`, thử tạo thêm worker đến `maximumPoolSize`.
5. Nếu vẫn không được (đã đóng, hoặc đã đạt giới hạn worker và queue), gọi `RejectedExecutionHandler.rejectedExecution(command, executor)`.

Đây là trình tự khái niệm để hiểu các ngưỡng, không phải cam kết rằng mọi phép kiểm tra trong triển khai nội bộ đều là một thao tác nguyên tử.

`RejectedExecutionHandler` là một functional interface:

```java
@FunctionalInterface
public interface RejectedExecutionHandler {
    void rejectedExecution(Runnable r, ThreadPoolExecutor executor);
}
```

- `r` là tác vụ đang bị từ chối; nếu lời gọi ban đầu là `submit(...)`, nó thường là một `RunnableFuture` do `submit` tạo ra.
- `executor` là pool liên quan, cho phép xem trạng thái, hàng đợi và các chỉ số.
- Handler có thể ném `RejectedExecutionException`. Nếu handler tự trả về bình thường, `execute` có thể trả về bình thường **dù công việc đã bị bỏ**. Không được coi việc “không thấy ngoại lệ” là bằng chứng tác vụ đã hoàn thành.
- Mặc định, `ThreadPoolExecutor` sử dụng `AbortPolicy`. Có thể truyền handler vào constructor hoặc thay bằng `setRejectedExecutionHandler(...)` khi cần; cấu hình trước khi nhận tải là dễ kiểm soát nhất.

### Vai trò của loại queue

| Queue | Hệ quả thực tế với ngưỡng từ chối |
| --- | --- |
| `ArrayBlockingQueue<>(N)` | Queue có dung lượng N; khi đầy và không thể thêm worker, handler có cơ hội được gọi. |
| `LinkedBlockingQueue<>(N)` | Hàng đợi có giới hạn N; hành vi quá tải tương tự nhưng chi phí cấu trúc dữ liệu khác. |
| `LinkedBlockingQueue<>()` | Gần như không bị chặn bởi ngưỡng nhỏ do ứng dụng đặt; sau khi đạt core worker, việc thường đi vào queue và `maximumPoolSize` ít có tác dụng mở rộng khi queue vẫn nhận được. Độ trễ/bộ nhớ có thể tăng trước khi có từ chối. |
| `SynchronousQueue<>` | Không chứa phần tử chờ: một tác vụ cần được chuyển giao trực tiếp cho worker. Khi không thể chuyển giao hoặc tạo worker mới, có thể bị từ chối; `DiscardOldestPolicy` đặc biệt không phù hợp vì không có phần tử nào để `poll()`. |

`corePoolSize` là ngưỡng ưu tiên tạo worker ban đầu, không có nghĩa executor luôn duy trì bấy nhiêu luồng; pool có thể khởi tạo worker khi nhận việc. `maximumPoolSize` là giới hạn worker, không phải tổng số tác vụ đang chạy cộng số tác vụ trong queue.

## 3. So sánh bốn chính sách tích hợp

Tất cả đều là lớp lồng trong `ThreadPoolExecutor`, triển khai `RejectedExecutionHandler`.

| Chính sách | Khi pool còn hoạt động nhưng quá tải | Khi pool đã shutdown | Đánh đổi chính |
| --- | --- | --- | --- |
| `AbortPolicy` | Ném `RejectedExecutionException` cho luồng gửi việc | Ném `RejectedExecutionException` | Thất bại tường minh; caller phải xử lý. |
| `CallerRunsPolicy` | Chạy tác vụ ngay trên **luồng gọi `execute`**, đồng bộ trước khi lời gọi trả về | Bỏ tác vụ | Tạo backpressure tự nhiên, nhưng chặn caller và thay đổi ngữ cảnh thực thi. |
| `DiscardPolicy` | Im lặng bỏ tác vụ mới | Im lặng bỏ tác vụ | Tác vụ có thể mất mà người gọi không biết. |
| `DiscardOldestPolicy` | Bỏ phần tử ở **đầu hàng đợi chờ**, rồi thử `execute` lại với tác vụ mới | Bỏ tác vụ mới | Mất tác vụ đã xếp hàng; retry có thể lại bị từ chối. |

### 3.1. AbortPolicy: thất bại rõ ràng

```java
RejectedExecutionHandler policy = new ThreadPoolExecutor.AbortPolicy();
```

Handler luôn ném `RejectedExecutionException`, kể cả khi executor đã shutdown. Đây là lựa chọn mặc định và thường là điểm khởi đầu an toàn khi mỗi tác vụ có giá trị: bên gửi có thể ghi log, tăng metric, phát tín hiệu bận, thực hiện retry **có giới hạn** hoặc chuyển tác vụ sang cơ chế bền vững.

Ví dụ xử lý tại ranh giới gửi công việc (đoạn mã minh họa, biến `pool` và `task` được tạo ở nơi khác):

```java
try {
    pool.execute(task);
} catch (RejectedExecutionException rejected) {
    // Ghi nhận và chuyển quyết định cho tầng gọi thích hợp.
    // Không retry vô hạn ngay trên cùng pool đang quá tải.
    System.err.println("Task was not accepted: " + rejected.getMessage());
}
```

**Lỗi phổ biến:** chỉ bắt rồi bỏ qua ngoại lệ, khiến `AbortPolicy` trên thực tế trở thành mất dữ liệu âm thầm; hoặc retry tức thì, không giới hạn, làm tình trạng quá tải trầm trọng hơn.

### 3.2. CallerRunsPolicy: giảm tốc luồng gửi việc

```java
RejectedExecutionHandler policy = new ThreadPoolExecutor.CallerRunsPolicy();
```

Nếu executor chưa đóng, handler gọi `r.run()` trên **chính luồng đang gửi tác vụ**. Ví dụ `main` gửi việc và pool đã bão hòa thì `main` trực tiếp chạy tác vụ được từ chối; sau khi hoàn tất mới trở về từ `execute`. Đây là một dạng backpressure: producer tốn thời gian tự xử lý, vì vậy không thể liên tục bơm thêm việc với tốc độ cũ.

Điểm cần đánh giá kỹ:

- Tác vụ được chạy **đồng bộ**; caller có thể là luồng xử lý giao diện, vòng sự kiện, luồng giữ lock hoặc một worker khác, dẫn đến độ trễ, deadlock hay thiếu tài nguyên nếu chọn sai.
- Không còn bảo đảm tác vụ chạy trên worker được cấu hình (tên luồng, `ThreadLocal`, security context, request context, cách quản lý MDC...). Tác vụ không nên dựa vào identity của thread pool.
- Exception unchecked từ `Runnable.run()` thực thi trực tiếp có thể lan tới caller, khác với lỗi tác vụ chạy trên worker. Với tác vụ được đóng gói bởi `submit`, `FutureTask` lại ghi nhận lỗi bên trong `Future`.
- Khi executor đã shutdown, chính sách này **bỏ tác vụ**, không tự chuyển sang chạy trên caller.

Nên dùng khi producer có thể bị chặn, tác vụ không lệ thuộc worker thread và người thiết kế chấp nhận việc chuyển chi phí thực thi sang caller.

### 3.3. DiscardPolicy: bỏ tác vụ mới không báo lỗi

```java
RejectedExecutionHandler policy = new ThreadPoolExecutor.DiscardPolicy();
```

Handler không ném lỗi và không chạy tác vụ. Nó chỉ phù hợp khi bỏ một số sự kiện là **chủ đích nghiệp vụ**, ví dụ cập nhật trạng thái gần nhất có thể tái tạo, phép lấy mẫu best-effort, hoặc công việc định kỳ mà vòng sau sẽ thay thế vòng trước. Dù vậy, thường vẫn cần đếm số lần bị từ chối để phát hiện mất tải.

Không thích hợp cho giao dịch, ghi dữ liệu, thanh toán, xử lý job phải hoàn tất, thao tác cần hồi đáp, hoặc các tác vụ trả `Future` được hệ khác chờ. Sau `submit`, một `FutureTask` bị bỏ có thể mãi ở trạng thái chưa hoàn thành, khiến `get()` chờ vô hạn nếu không có timeout hoặc hủy riêng.

### 3.4. DiscardOldestPolicy: bỏ tác vụ đang chờ ở đầu queue

```java
RejectedExecutionHandler policy = new ThreadPoolExecutor.DiscardOldestPolicy();
```

Khi pool chưa đóng, handler gọi `executor.getQueue().poll()` để loại phần tử chờ ở đầu hàng đợi và gửi lại tác vụ mới qua `execute`. Nó **không** hủy tác vụ đang chạy. Với queue FIFO, đó thường là công việc được xếp hàng lâu nhất; với queue ưu tiên, `poll()` chọn đầu queue theo thứ tự ưu tiên, **không nhất thiết là tác vụ vào sớm nhất**.

Những hệ quả dễ bị bỏ sót:

- Tác vụ cũ bị loại không nhận ngoại lệ trực tiếp tại luồng đã gửi nó; nếu là `FutureTask`, người đang đợi `Future.get()` có thể bị treo vì Future chưa được hủy.
- Retry **không bảo đảm thành công**: có thể lại bị từ chối do cạnh tranh với producer khác hoặc tình trạng shutdown. Với queue không chứa phần tử như `SynchronousQueue`, `poll()` không tạo được chỗ trống; việc thử lại có thể lặp quá sâu. Không sử dụng chính sách này cho cấu hình đó.
- Thay “việc cũ” bằng “việc mới” chỉ hợp lý nếu dữ liệu cũ thật sự có thể mất. Bỏ phần tử ở đầu `PriorityBlockingQueue` có thể phá hỏng mức ưu tiên mong đợi.
- Khi executor đã shutdown, chính sách bỏ ngay tác vụ mới, không retry.

Tài liệu JDK cũng cảnh báo đây là chính sách hiếm khi phù hợp nếu có bên chờ hoàn thành tác vụ; trường hợp cần “thay thế công việc cũ” thường nên thiết kế hàng đợi gộp/cập nhật trạng thái và cơ chế hủy, thông báo thất bại rõ ràng.

## 4. Ví dụ Java 17+ chạy độc lập: cùng tải, bốn kết quả khác nhau

Chương trình cố định trạng thái **A đang chạy, B đang chờ, C là tác vụ đến lúc đầy tải** bằng `CountDownLatch`, tránh phụ thuộc vào `sleep()` hoặc tốc độ máy. Mỗi chính sách dùng một pool mới, giữ A chưa hoàn tất cho tới khi xử lý C, rồi mới giải phóng.

Lưu thành `RejectionPoliciesDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RejectionPoliciesDemo {
    public static void main(String[] args) throws InterruptedException {
        runCase("AbortPolicy", new ThreadPoolExecutor.AbortPolicy());
        runCase("CallerRunsPolicy", new ThreadPoolExecutor.CallerRunsPolicy());
        runCase("DiscardPolicy", new ThreadPoolExecutor.DiscardPolicy());
        runCase("DiscardOldestPolicy",
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    private static void runCase(
            String name, RejectedExecutionHandler policy)
            throws InterruptedException {

        List<String> events =
                Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                policy);

        try {
            pool.execute(() -> {
                events.add("A-start");
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                events.add("A-end");
            });

            started.await(); // A đã chiếm worker
            pool.execute(() -> events.add("B")); // B đã vào queue

            try {
                pool.execute(() -> events.add("C")); // C bị từ chối
            } catch (RejectedExecutionException ex) {
                events.add("REJECTED-C");
            }
        } finally {
            release.countDown();
            pool.shutdown();
        }

        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Pool did not terminate: " + name);
        }
        System.out.println(name + " -> " + String.join(", ", events));
    }
}
```

Biên dịch và chạy:

```bash
javac RejectionPoliciesDemo.java
java RejectionPoliciesDemo
```

Kết quả xác định trong ví dụ này:

```text
AbortPolicy -> A-start, REJECTED-C, A-end, B
CallerRunsPolicy -> A-start, C, A-end, B
DiscardPolicy -> A-start, A-end, B
DiscardOldestPolicy -> A-start, A-end, C
```

Giải thích:

- `AbortPolicy`: C bị từ chối bằng exception; A rồi B vẫn thực hiện.
- `CallerRunsPolicy`: C chạy ngay trên thread `main` trước khi A được mở khóa; B tiếp tục đợi worker.
- `DiscardPolicy`: C biến mất; queue vẫn còn B.
- `DiscardOldestPolicy`: phần tử B trong queue bị loại, C thay vào vị trí chờ; sau khi A xong, C chạy.

Ví dụ này cố ý dùng `execute` để thấy trực tiếp cơ chế handler. **Thứ tự log ở các chương trình chạy đồng thời nói chung không tự nhiên cố định**; ở đây latch buộc thứ tự cần kiểm chứng. Trong thực tế không nên dùng latch để cố tạo quá tải; hãy đo thông lượng, độ trễ và tỷ lệ từ chối dưới tải thật.

## 5. `execute` và `submit`: hai cách gọi, một rủi ro lớn

Cả `execute(Runnable)` và `submit(...)` đều có thể đi qua cơ chế từ chối của `ThreadPoolExecutor`, nhưng `submit` đóng gói tác vụ trong `RunnableFuture` và trả về `Future` khi lời gọi trở về bình thường.

| Tình huống | Với `execute` | Với `submit` |
| --- | --- | --- |
| `AbortPolicy` từ chối | Exception được ném tại lệnh gửi | Exception cũng được ném trong thao tác gửi; không có Future được trả về thành công |
| `CallerRunsPolicy` khi chưa shutdown | Runnable chạy trong caller | `FutureTask` chạy trong caller, Future sau đó đã hoàn tất; vẫn có thể tốn nhiều thời gian |
| `DiscardPolicy` | Trả về bình thường dù tác vụ không chạy | Có thể trả về Future **không bao giờ completed** |
| `DiscardOldestPolicy` | Tác vụ mới có thể được nhận, tác vụ chờ bị thay thế | Future ứng với tác vụ bị loại khỏi queue có thể không bao giờ completed |

Các lời gọi `get(timeout, unit)` giúp tránh chờ vô hạn tại phía consumer, **nhưng không tự sửa sự mất tác vụ**. `isDone()` không chuyển sang `true` chỉ vì executor đã bỏ phần tử `FutureTask` khỏi hàng đợi. Nếu ứng dụng thực sự cho phép loại tác vụ, cần thiết kế cách `cancel(false)` Future đã bị bỏ và phát tín hiệu/metric cho nơi quan sát, hoặc chọn chính sách thể hiện thất bại tường minh.

Mẫu dễ gây lỗi: dùng `Executors.newFixedThreadPool(n)`, tưởng rằng luôn phát sinh rejection khi quá tải. Factory này sử dụng queue không giới hạn nhỏ theo cấu hình ứng dụng, nên queue có thể tăng rất lớn thay vì sớm kích hoạt handler. Nếu cần giới hạn mức chờ và kiểm soát từ chối, tạo `ThreadPoolExecutor` với queue có giới hạn phù hợp.

## 6. Tùy biến RejectedExecutionHandler một cách có trách nhiệm

Một handler tùy chỉnh hữu ích khi ứng dụng cần **đo được** tác vụ bị từ chối và đưa ra quyết định nhất quán. Mẫu “đếm rồi ném lỗi” giữ được hợp đồng thất bại tường minh:

```java
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

// Fragment minh họa; có thể đặt trong method cấu hình pool.
AtomicLong rejectedCount = new AtomicLong();
RejectedExecutionHandler handler = (task, executor) -> {
    rejectedCount.incrementAndGet();
    throw new RejectedExecutionException(
            "Executor rejected task; shutdown=" + executor.isShutdown());
};
```

Truyền `handler` vào constructor `ThreadPoolExecutor` như ví dụ trước. `AtomicLong` giúp thống kê số lần từ chối đồng thời, nhưng bộ đếm là **số lần gọi handler**, không nhất thiết bằng số công việc cuối cùng đã thất lạc nếu handler retry hoặc chuyển giao thành công. Dữ liệu theo thời gian nên có trong monitoring, cùng với tải đang chạy, độ sâu queue và thời gian chờ.

Khi viết một chính sách phức tạp hơn, cần đặt ra:

1. Tác vụ nào được phép bỏ? Có cần đảm bảo thứ tự, at-most-once, khả năng retry hoặc giao nhận bền vững?
2. Producer có thể bị chặn trong bao lâu? Có timeout, khả năng interrupt và đường thoát khi shutdown không?
3. Nếu phải loại `FutureTask` đang nằm trong queue, ai sẽ hủy Future và thông báo thất bại cho người đợi?
4. Handler có thể được gọi đồng thời từ nhiều luồng. Mọi cập nhật trạng thái phải thread-safe; không giữ khóa hoặc callback có nguy cơ gây deadlock.
5. Cần báo rõ trạng thái “không chấp nhận” cho tầng gửi việc; không giả vờ chấp nhận thành công nếu không thể bảo đảm xử lý.
6. Không gọi `execute` đệ quy mù quáng trên cùng pool đang đầy và không block vô hạn trong chính handler. Nếu có fallback queue, phải đặt capacity và chính sách shutdown riêng.

Một số hệ thống sẽ phù hợp hơn với admission control trước pool (ví dụ giới hạn số tác vụ đang lưu hành bằng `Semaphore`), đẩy sự kiện sang hàng đợi có độ bền, hoặc trả tín hiệu “busy” cho producer. Rejected handler chỉ là **điểm cuối** sau khi `ThreadPoolExecutor` đã từ chối, không thay thế thiết kế giới hạn tải toàn hệ thống.

## 7. Chọn chính sách theo đặc điểm tác vụ

| Yêu cầu | Lựa chọn khởi đầu | Cần kiểm tra trước khi đưa vào thực tế |
| --- | --- | --- |
| Không được mất tác vụ mà không biết | `AbortPolicy` | Bắt và xử lý rejection; retry có giới hạn hoặc lưu bền vững khi cần |
| Producer chấp nhận chạy chậm để giảm tải; công việc có thể chạy trên caller | `CallerRunsPolicy` | Không gọi từ event loop/luồng cần phản hồi nhanh hoặc đang giữ lock; shutdown vẫn làm rơi tác vụ |
| Sự kiện best-effort có thể bỏ và không ai chờ Future | `DiscardPolicy` | Mất tác vụ đúng với yêu cầu sản phẩm; theo dõi lượng bỏ |
| Dữ liệu cũ có thể bị thay thế bởi dữ liệu mới hơn | Cân nhắc thiết kế queue coalescing; chỉ cân nhắc `DiscardOldestPolicy` nếu hiểu rõ rủi ro | Queue FIFO, xử lý Future bị bỏ, tránh `SynchronousQueue` và retry không giới hạn |

**Không có “policy tốt nhất” cho mọi ứng dụng.** Điều quan trọng là tính chất của tác vụ, quy ước báo lỗi và khả năng chịu tải của producer/consumer. Với tác vụ bắt buộc hoàn tất, `AbortPolicy` cộng cơ chế tiếp nhận lại/ghi bền vững thường rõ ràng hơn một handler bỏ âm thầm.

### Checklist cấu hình và vận hành

- Chọn `corePoolSize` và `maximumPoolSize` dựa trên tác vụ CPU-bound hay I/O-bound, tài nguyên thực và benchmark; không đặt số luồng thật lớn chỉ để né rejection.
- Dùng queue **có capacity hữu hạn** nếu mục tiêu là giới hạn lượng việc đang chờ, chấp nhận trade-off giữa khả năng hấp thụ đột biến và độ trễ.
- Theo dõi ít nhất rejection count/rate, `getActiveCount()`, `getPoolSize()`, `getQueue().size()`, `getCompletedTaskCount()`, thời gian xử lý và độ trễ queue. Các getter pool/queue là **ảnh chụp gần đúng**, không phải phép kiểm tra capacity nguyên tử để ra quyết định chính xác.
- Phân biệt từ chối do overload và do shutdown bằng trạng thái executor/telemetry; đừng tự gộp hai tình huống thành “thiếu thread”.
- Khi cần shutdown có kiểm soát, dừng gửi việc, gọi `shutdown()`, `awaitTermination(...)` và xác định cách xử lý tác vụ chưa hoàn tất; `shutdownNow()` cố gắng ngắt worker và trả về tác vụ còn trong queue, nhưng không bảo đảm mọi tác vụ dừng ngay.
- Kiểm tra tác động của `submit`/`Future`, exception, timeout, cancellation; viết bài test có điều khiển bằng latch giống ví dụ thay vì trông chờ timing ngẫu nhiên.
- Thử tải tăng đột biến, caller chậm, queue đầy, nhiều producer cạnh tranh và cuộc đua shutdown. Chứng minh handler được gọi đúng và công việc mất/chuyển giao đúng theo hợp đồng.

## 8. Câu hỏi tự kiểm tra

1. Pool có `corePoolSize=2`, `maximumPoolSize=4`, queue hữu hạn 10 chỗ: khi đã có 2 worker bận và còn chỗ queue, executor sẽ xếp hàng hay tăng lên 3 worker? **Xếp hàng trước** trong trình tự thông thường; chỉ khi queue không nhận được mới thử tăng worker đến maximum.
2. Vì sao `CallerRunsPolicy` có thể giảm tốc producer? **Vì producer trực tiếp trả chi phí chạy task bị từ chối**, lời gọi `execute` bị giữ tới khi hoàn thành; nhưng không bảo đảm cơ chế này khi pool đã shutdown.
3. `DiscardOldestPolicy` có hủy task đang chạy không? **Không**; nó `poll()` từ queue chờ, rồi retry; tác vụ đang chạy không phải phần tử đầu queue.
4. Nếu `submit` bị `DiscardPolicy` bỏ, `Future.get()` có tự phát sinh `RejectedExecutionException` không? **Không**; handler đã im lặng và Future có thể vẫn chờ mãi nếu không bị hủy.
5. Gửi tác vụ vào pool đã `shutdown()` có thể được `CallerRunsPolicy` thực thi trên luồng gửi không? **Không**; policy bỏ tác vụ trong trường hợp executor đã shutdown.

## Tài liệu tham khảo

- Java SE 17 API: [ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- Java SE 17 API: [RejectedExecutionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html)
- Java SE 17 API: [AbortPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.AbortPolicy.html), [CallerRunsPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.CallerRunsPolicy.html), [DiscardPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.DiscardPolicy.html), [DiscardOldestPolicy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.DiscardOldestPolicy.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-008">ThreadFactory và UncaughtExceptionHandler trong executor</a>

<details>
<summary>Xem chi tiết</summary>

# ThreadFactory và UncaughtExceptionHandler trong Java Executor Framework

## 1. Tại sao cần quan tâm đến cách tạo thread và cách báo lỗi?

Một `ExecutorService` nhận các task và quyết định khi nào, ở worker nào chúng được chạy. Người viết ứng dụng thường chỉ gọi `execute(...)` hoặc `submit(...)`, nhưng trong vận hành cần trả lời thêm:

- Worker thread được **đặt tên** ra sao để đọc thread dump và log?
- Worker được tạo dưới **ThreadGroup**, daemon flag, priority, context class loader và handler nào?
- Nếu task ném `RuntimeException`/`Error` không được xử lý, **ai nhận biết**?
- Nếu task chạy qua `submit(...)` bị lỗi, vì sao `UncaughtExceptionHandler` có thể không được gọi?
- Khi một worker kết thúc bất thường, executor có tạo worker khác không? Lỗi của task cũ có tự được chạy lại không?
- Làm thế nào ghi nhận lỗi nhưng **không làm hỏng chính sách shutdown và quản lý tài nguyên**?

Hai API ở trung tâm:

1. **`java.util.concurrent.ThreadFactory`**: chiến lược tạo đối tượng `Thread` cho executor. Factory không quản lý queue, không tự chạy task và không tự quyết định lỗi task được biểu diễn ra sao.
2. **`Thread.UncaughtExceptionHandler`**: callback của một thread khi thread **đang kết thúc do một ngoại lệ không được bắt**. Nó không phải callback chung cho mọi task thất bại trong executor.

**Điểm phân biệt cốt lõi:** `ThreadPoolExecutor.execute(Runnable)` với một `Runnable` ném exception có thể khiến exception thoát khỏi worker và đến uncaught handler. Ngược lại, `submit(...)` thường bao task trong một `FutureTask`, nên exception được **lưu vào Future**, không thoát thành một uncaught exception từ `Thread.run()`.

Bài học này nằm trong phạm vi **Executor Framework và Java concurrency nói chung**. Các ví dụ sử dụng API chuẩn của JDK 17+ và không dựa vào framework khác.

### Mục tiêu học tập

Sau bài, bạn có thể:

1. Viết `ThreadFactory` đặt tên worker dễ đọc và gắn handler khi cần.
2. Hiểu vì sao factory phải trả về **thread chưa được start**, và vì sao một factory có thể được gọi nhiều lần trong suốt vòng đời pool.
3. Phân biệt exception đi qua `execute()`, `submit()`, `Future.get()` và `Thread.UncaughtExceptionHandler`.
4. Biết cách mở rộng `ThreadPoolExecutor.afterExecute(...)` để giám sát task đúng cách, kể cả lỗi bị Future giữ lại.
5. Quản lý worker failure, replacement, shutdown, retry và tránh hiểu nhầm handler là cơ chế khôi phục task.
6. Kiểm tra lỗi có kênh quan sát rõ ràng, không dựa vào thứ tự log của nhiều thread.

## 2. ThreadFactory: trách nhiệm và hợp đồng

### 2.1. Giao diện đơn giản nhưng tác động lớn

`ThreadFactory` là một functional interface:

~~~java
// Chữ ký API, không phải chương trình độc lập.
public interface ThreadFactory {
    Thread newThread(Runnable r);
}
~~~

Executor truyền một `Runnable` nội bộ cần được thực thi vào `newThread(r)`. Factory tạo và **trả về một Thread chưa được start**. Executor chịu trách nhiệm gọi `start()` khi thích hợp.

Một factory có thể thiết lập:

- **Name** có tiền tố chức năng cùng số tăng: `orders-worker-1`, `orders-worker-2`.
- **Daemon** khi có lý do lifecycle rõ ràng; thường tránh daemon cho công việc quan trọng phải được shutdown có kiểm soát.
- **Priority** nếu thật sự hiểu các giới hạn scheduler; không dùng thread priority làm bảo đảm thứ tự nghiệp vụ.
- **UncaughtExceptionHandler** để ghi nhận một số lỗi chưa được bắt ở cấp thread.
- ThreadGroup hoặc context class loader khi môi trường và ownership yêu cầu, không tùy tiện dùng để giải quyết lỗi ứng dụng.

Factory **không** nên tự `start()` thread, gọi `r.run()` ngay trong factory hoặc trả một thread đã được start. Những hành động đó phá giả định của executor về thời điểm tạo worker và có thể làm phát sinh `IllegalThreadStateException` hoặc hành vi không phù hợp.

### 2.2. Không tạo một thread cho mỗi task theo giả định

`newFixedThreadPool(2, factory)` có thể dùng cùng hai worker để chạy rất nhiều task. `ThreadFactory.newThread(...)` được gọi **khi executor cần tạo worker**, không phải mỗi lần gọi `submit()`.

Một số tình huống factory có thể được gọi thêm:

- Pool chưa có đủ worker và cần worker mới.
- Một worker **kết thúc bất thường** và pool cần duy trì số worker theo cấu hình/trạng thái.
- Pool co giãn hoặc thay thế worker dựa trên chính sách `ThreadPoolExecutor`.

Ngược lại, factory có thể **không được gọi** khi task mới được xếp queue và worker hiện có sẽ thực thi nó. Do đó không nên đặt initialization **cho mỗi task** vào factory. Dữ liệu ngữ cảnh của request/task nên được truyền qua task hoặc wrapper và dọn đúng vòng đời task.

### 2.3. Ví dụ tối giản về factory

~~~java
// Mã minh họa; cần imports và dùng bên trong một method.
AtomicInteger nextId = new AtomicInteger();

ThreadFactory factory = task -> {
    Thread thread = new Thread(
        task, "compute-worker-" + nextId.incrementAndGet()
    );
    thread.setDaemon(false);
    thread.setUncaughtExceptionHandler((t, error) -> {
        System.err.println(
            "Uncaught in " + t.getName() + ": " + error
        );
    });
    return thread; // Chưa start.
};

ExecutorService pool = Executors.newFixedThreadPool(2, factory);
~~~

Đặt `AtomicInteger` cho counter tên giúp factory an toàn khi nhiều caller cùng lúc yêu cầu tạo worker. Không mặc định rằng factory luôn được gọi từ đúng một thread.

**Giới hạn:** `UncaughtExceptionHandler` bên trên chỉ chạy khi có uncaught throwable tới tận ranh giới thread theo hợp đồng. Nó không quan sát tất cả lỗi từ `FutureTask`, và không phải nơi chạy xử lý retry nghiệp vụ.

## 3. Ví dụ 1 — đặt tên worker với ThreadFactory (Java 17+)

Để minh họa rằng hai thread của fixed pool có tên riêng mà không dựa vào thứ tự scheduling, ta cho cả hai task chờ tại một latch rồi in **tập tên đã sắp xếp**.

Tạo tệp `NamedWorkersDemo.java`:

~~~java
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadFactory;

public class NamedWorkersDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger ids = new AtomicInteger();

        ThreadFactory factory = command -> {
            Thread worker = new Thread(
                command, "lesson-worker-" + ids.incrementAndGet()
            );
            worker.setDaemon(false);
            return worker;
        };

        ExecutorService pool =
            Executors.newFixedThreadPool(2, factory);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> first = pool.submit(() -> {
                entered.countDown();
                release.await();
                return Thread.currentThread().getName();
            });

            Future<String> second = pool.submit(() -> {
                entered.countDown();
                release.await();
                return Thread.currentThread().getName();
            });

            entered.await();
            release.countDown();

            TreeSet<String> names =
                new TreeSet<>(List.of(first.get(), second.get()));
            System.out.println("workers = " + names);
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac NamedWorkersDemo.java
java NamedWorkersDemo
~~~

Stdout khi hai task hoàn tất bình thường:

~~~text
workers = [lesson-worker-1, lesson-worker-2]
~~~

Giải thích:

1. `newFixedThreadPool(2, factory)` có đủ năng lực chạy hai task đồng thời.
2. `entered` bảo đảm cả hai task đã được worker nhận và tới điểm chờ; nếu chỉ một worker tồn tại, cả hai không thể cùng đếm latch trước khi một trong hai rời latch, nên executor phải tạo worker thứ hai để tiến triển.
3. `release` giải phóng cả hai task sau khi chúng đều đã tới điểm chờ.
4. `Future.get()` đợi kết quả của từng task và truyền lỗi task qua `ExecutionException` khi có lỗi.
5. `TreeSet` sắp tên trước khi in, tránh phụ thuộc task nào hoàn thành trước.
6. `shutdown()` kết hợp `awaitTermination()` thể hiện việc quản lý pool có kiểm soát. `shutdownNow()` trong đường timeout là yêu cầu hủy tốt nhất có thể (*best effort*), không phải phép ép tất cả task kết thúc.

**Không suy ra từ ví dụ rằng factory được gọi mỗi lần submit.** Lần chạy này chỉ chứng minh hai worker được tạo vì hai task cần chạy cùng lúc. Khi những task tiếp theo được gửi vào pool, chúng có thể được thực thi bởi chính hai worker đã tạo.

## 4. UncaughtExceptionHandler làm gì?

### 4.1. Callback cấp thread

`Thread.UncaughtExceptionHandler` có method:

~~~java
// Chữ ký giao diện, không phải chương trình độc lập.
void uncaughtException(Thread thread, Throwable error);
~~~

Khi một thread chuẩn bị kết thúc vì throwable không được xử lý, Java chuyển thông tin tới handler phù hợp. Handler có thể ghi lại tên thread, loại throwable, stack trace và số lần xảy ra lỗi.

Các cách gắn handler:

- `thread.setUncaughtExceptionHandler(handler)`: handler riêng cho thread đó.
- `Thread.setDefaultUncaughtExceptionHandler(handler)`: handler mặc định ở phạm vi JVM cho những thread không có handler rõ ràng, tùy cơ chế fallback/ThreadGroup.
- Gắn vào **mọi thread được tạo qua factory** để có phạm vi giám sát nhất quán đối với worker do factory tạo.

Với executor, thường nên gắn handler **khi factory khởi tạo thread**, thay vì cố tìm worker đang chạy để thay đổi handler.

### 4.2. Handler không thay thế exception handling trong task

Cần phân biệt ba ranh giới:

~~~text
Task business code
      |
      +--> tự try/catch lỗi: task đã xử lý, handler không thấy
      |
      +--> execute(Runnable) -> Runnable.run() throws
      |                       -> worker có thể chết bất thường
      |                       -> UncaughtExceptionHandler nhận Throwable
      |
      +--> submit(Runnable/Callable)
                              -> FutureTask.run() bắt Throwable
                              -> Future ghi nhận lỗi
                              -> Future.get() ném ExecutionException
                              -> Không có uncaught throwable từ task
~~~

`UncaughtExceptionHandler` không bắt lỗi checked exception theo nghĩa thay caller giải quyết mọi lỗi; `Runnable.run()` không khai báo checked exception. Nếu bạn chuyển checked exception thành `RuntimeException` và throwable đó thật sự thoát khỏi worker, handler mới có cơ hội nhận.

**Điểm bảo đảm cần dựa vào:** Lỗi task qua `submit` được đưa vào Future và phải quan sát qua Future hoặc một hook/cơ chế được thiết kế. Việc chỉ gắn handler cho thread pool **không** bảo đảm bạn sẽ thấy các lỗi của mọi task submit.

### 4.3. Giới hạn của chính handler

Một handler nên:

- **Ngắn gọn và an toàn**: ghi nhận lỗi, metric hoặc gửi tín hiệu cảnh báo bằng đường ít phụ thuộc.
- Tránh ném thêm exception; uncaught exception phát sinh từ chính handler thường bị cơ chế runtime bỏ qua ở ranh giới đó.
- Tránh chờ vô hạn, không lấy những lock có thể gây deadlock trong quá trình logging.
- Tránh gọi lại chính executor đang hỏng để gửi một task thông báo quan trọng mà không có fallback.
- Không gọi `Thread.stop()`, không “cứu sống” thread đang kết thúc hoặc giả vờ task thất bại đã được retry thành công.

Nếu ứng dụng muốn retry task lỗi, cần hệ thống retry theo idempotency, giới hạn lần thử, deadline và báo kết quả. **Handler chỉ là điểm quan sát thất bại của thread**, không phải retry policy.

## 5. execute() và submit(): khác biệt quan trọng nhất của bài

### 5.1. `execute(Runnable)`

`Executor.execute(Runnable)` chỉ yêu cầu executor thực thi task. Nó **không trả `Future`** cho caller để lấy kết quả hoặc checked exception.

Với `ThreadPoolExecutor` chuẩn khi một `Runnable` được truyền trực tiếp vào `execute` và ném `RuntimeException`/`Error` không được bắt, throwable có thể thoát khỏi `run()` của worker. `ThreadPoolExecutor` gọi `afterExecute` với throwable và worker có thể kết thúc bất thường; cơ chế uncaught của thread có thể nhận throwable đó.

**Không đánh đồng:** điều này đúng cho mô hình worker của `ThreadPoolExecutor` chuẩn, không phải lời hứa rằng bất kỳ `Executor` custom nào cũng có cùng chính sách. Một executor tự viết có thể bắt mọi throwable hoặc thực thi task ngay trên caller.

### 5.2. `submit(Runnable/Callable)`

`ExecutorService.submit(...)` trả về `Future<?>` hoặc `Future<V>`. Với triển khai `ThreadPoolExecutor` thông thường, task được chuyển thành `RunnableFuture` như `FutureTask`. `FutureTask.run()` bắt exception và lưu outcome.

Nếu task thất bại:

~~~java
// Mã minh họa; executor đã được tạo.
Future<?> result = executor.submit(
    () -> { throw new IllegalStateException("task failed"); }
);

try {
    result.get();
} catch (ExecutionException failure) {
    Throwable root = failure.getCause();
    // Báo lỗi, chuyển thành lỗi tầng caller hoặc xử lý có kiểm soát.
}
~~~

`get()` là cách tiêu chuẩn để caller biết lỗi task. Nếu caller **không lưu Future hoặc không gọi get/quan sát bằng cách khác**, lỗi có thể bị bỏ qua trong luồng điều phối mà không xuất hiện tại uncaught handler.

### 5.3. Lưu ý: `execute(FutureTask)` vẫn có thể giấu exception

Bí quyết không chỉ là “tên phương thức execute hay submit”, mà còn là **wrapper task có bắt throwable hay không**. Nếu bạn truyền một `FutureTask` trực tiếp cho `execute()`, chính `FutureTask.run()` có thể bắt lỗi và giữ nó trong FutureTask, nên uncaught handler vẫn không thấy lỗi đó.

Vì thế quy tắc vận hành là: **xem cách task được đóng gói và nơi kết quả/exception được lưu**, không chỉ đếm số lần handler được gọi.

## 6. Ví dụ 2 — lỗi từ execute() tới UncaughtExceptionHandler (Java 17+)

Ví dụ này gắn handler ngay trong factory và dùng latch để bảo đảm main đã thấy handler xử lý lỗi trước khi in. Để stdout ổn định, handler lưu **tên loại ngoại lệ** thay vì in stack trace ngẫu nhiên giữa các thread.

Tạo tệp `ExecuteUncaughtDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ThreadFactory;

public class ExecuteUncaughtDemo {
    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch handled = new CountDownLatch(1);
        AtomicReference<String> observed =
            new AtomicReference<>("none");

        ThreadFactory factory = command -> {
            Thread worker = new Thread(command, "execute-worker");
            worker.setUncaughtExceptionHandler((thread, error) -> {
                observed.set(error.getClass().getSimpleName());
                handled.countDown();
            });
            return worker;
        };

        ExecutorService pool =
            Executors.newSingleThreadExecutor(factory);

        try {
            pool.execute(() -> {
                throw new IllegalStateException("from execute");
            });

            handled.await();
            System.out.println("handler = " + observed.get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ExecuteUncaughtDemo.java
java ExecuteUncaughtDemo
~~~

Stdout trong mô hình `ThreadPoolExecutor` chuẩn của ví dụ:

~~~text
handler = IllegalStateException
~~~

Lần `execute` gửi một `Runnable` **không được FutureTask bọc**. `IllegalStateException` thoát khỏi `run()`, đi qua worker executor và cuối cùng tới handler của worker. Handler ghi loại throwable và đếm latch. Main chỉ in sau `handled.await()` nên không phụ thuộc vào thứ tự log của hai thread.

**Worker hỏng không đồng nghĩa toàn pool chết:** `ThreadPoolExecutor` thường có thể tạo worker thay thế theo chính sách pool khi worker cũ kết thúc bất thường. Nhưng **task vừa ném lỗi không tự chạy lại**. Dù có worker thay thế, lỗi task và kết quả nghiệp vụ vẫn phải được giám sát riêng.

Không đặt kỳ vọng tên worker luôn là `execute-worker` duy nhất trên mọi lần tạo; nếu factory dùng một tên cố định như ví dụ, worker thay thế có thể trùng tên. Trong sản phẩm thực, nên có counter tăng cho từng worker, như `NamedWorkersDemo`.

## 7. Ví dụ 3 — submit() giữ exception trong Future (Java 17+)

Ví dụ tương phản dùng cùng kỹ thuật handler nhưng gửi task bằng `submit`. Handler **không nhận lỗi task**, trong khi `Future.get()` trả lỗi rõ ràng.

Tạo tệp `SubmitCapturedDemo.java`:

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadFactory;

public class SubmitCapturedDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger handlerCalls = new AtomicInteger();

        ThreadFactory factory = command -> {
            Thread thread = new Thread(command, "submit-worker");
            thread.setUncaughtExceptionHandler((t, error) -> {
                handlerCalls.incrementAndGet();
            });
            return thread;
        };

        ExecutorService pool =
            Executors.newSingleThreadExecutor(factory);

        try {
            Future<?> future = pool.submit(() -> {
                throw new IllegalArgumentException("from submit");
            });

            try {
                future.get();
            } catch (ExecutionException failure) {
                System.out.println("future cause = "
                    + failure.getCause().getClass().getSimpleName());
            }

            System.out.println("handler calls = "
                + handlerCalls.get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SubmitCapturedDemo.java
java SubmitCapturedDemo
~~~

Stdout:

~~~text
future cause = IllegalArgumentException
handler calls = 0
~~~

Giải thích:

- `submit` trả `Future` và triển khai pool chuẩn lưu `IllegalArgumentException` làm nguyên nhân lỗi.
- `future.get()` chờ task hoàn thành rồi ném `ExecutionException`; `getCause()` là exception gốc của task.
- Vì exception không thoát khỏi `FutureTask.run()` để làm worker kết thúc bất thường, handler không nhận lỗi này.
- Nếu không gọi `get()` hoặc một cơ chế quan sát phù hợp, lỗi có thể bị bỏ sót dù chương trình vẫn chạy tiếp.

**Không suy luận rằng uncaught handler không hữu ích.** Nó vẫn có giá trị giám sát một số failure cấp thread/execute; chỉ không thay thế được kết quả của Future.

## 8. Một bảng quyết định chẩn đoán lỗi

| Task chạy như thế nào? | Exception đi đâu? | Có nhất thiết tới UncaughtExceptionHandler? | Cách quan sát chính |
| --- | --- | --- | --- |
| `new Thread(r).start()`, `r.run()` ném không bắt | Ranh giới thread | Có theo cơ chế uncaught của Thread | Handler + log |
| `ThreadPoolExecutor.execute(plainRunnable)`, `run()` ném | Qua worker, có thể làm worker kết thúc đột ngột | Thường có với pool chuẩn | Handler hoặc `afterExecute` |
| `ExecutorService.submit(Runnable)` ném | `FutureTask`/`Future` | **Không**, lỗi bị giữ lại | `Future.get()` |
| `ExecutorService.submit(Callable)` ném | `Future` | **Không**, lỗi bị giữ lại | `Future.get()` |
| `execute(FutureTask)` ném trong delegate | `FutureTask` | Không mặc định | `FutureTask.get()` |
| Task tự `try/catch` và xử lý lỗi | Code task | Không, nếu không rethrow | Kênh kết quả/log của task |
| `CompletableFuture.supplyAsync(...)` ném | Completion stage thất bại | Không cần tới handler | `join()`, `get()`, `whenComplete` |
| Task bị hủy qua `Future.cancel(true)` | Trạng thái Future và yêu cầu interrupt | Không mặc định | `CancellationException`/state + xác nhận cleanup riêng |

Không coi bảng này như cam kết về mọi `Executor` tự viết: chính cách triển khai executor/wrapper quyết định ranh giới bắt lỗi. Với `ExecutorService` thuộc JDK, tập trung vào hợp đồng Future và các hook chuyên biệt.

## 9. ThreadPoolExecutor.afterExecute(): giám sát cả execute và submit

### 9.1. Hook này giải quyết vấn đề gì?

`ThreadPoolExecutor` cung cấp hook:

~~~java
// Chữ ký method để override, không phải chương trình độc lập.
protected void afterExecute(Runnable task, Throwable error)
~~~

Nó được gọi khi một task trong executor vừa chạy xong. Nếu một `plain Runnable` ném exception trực tiếp khi `execute`, đối số `error` có thể là throwable. Nhưng với `submit`, task thông thường là một `FutureTask` bắt exception vào nội bộ; vì vậy **`error` thường là null ngay cả khi task thất bại**.

Nếu muốn quan sát lỗi từ `submit` ở hook này, cần xem `task instanceof Future<?>` và lấy outcome từ Future bằng cách thích hợp.

### 9.2. Mẫu hook an toàn và giới hạn

~~~java
// Mẫu trong subclass ThreadPoolExecutor, không phải chương trình độc lập.
@Override
protected void afterExecute(Runnable task, Throwable failure) {
    super.afterExecute(task, failure);

    Throwable observed = failure;
    if (observed == null && task instanceof Future<?> future) {
        try {
            future.get();
        } catch (CancellationException canceled) {
            // Cancellation là một trạng thái riêng, không mặc định lỗi task.
        } catch (ExecutionException executedWithFailure) {
            observed = executedWithFailure.getCause();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    if (observed != null) {
        reportTaskFailure(observed);
    }
}
~~~

Đoạn trên cần import các loại `Future`, `CancellationException`, `ExecutionException` và `InterruptedException`. Đây là mẫu JDK phổ biến; **không nên gọi `get()` mù quáng trên bất kỳ Future chưa hoàn thành** ở nơi khác. Trong `afterExecute` của `ThreadPoolExecutor`, task đã chạy xong theo cơ chế thông thường nên FutureTask do `submit` tạo đã hoàn tất và `get()` không cần chờ tác vụ đó chạy tiếp.

Cũng cần xem xét các wrapper khác: nếu một `Runnable` chứa Future của task khác chưa hoàn thành, hoặc một implementation tùy chỉnh trả Future không gắn với task vừa kết thúc, `get()` có thể block. Hook production không nên block vô hạn. Nếu có `Future.isDone()`, có thể kiểm tra trước khi lấy kết quả, hoặc chỉ unwrap những wrapper mình kiểm soát.

**Không ném exception ngoài ý muốn từ `afterExecute`.** Theo mô hình `ThreadPoolExecutor`, exception thoát từ hook có thể khiến worker kết thúc đột ngột, gây thêm thay thế worker và che mất nguyên nhân nghiệp vụ. Hãy ghi nhận lỗi bằng đường bền vững, ít phụ thuộc và tránh gọi I/O lâu trong hook nếu cần throughput tốt.

### 9.3. Ví dụ 4 — quan sát submit exception trong afterExecute (Java 17+)

Tạo tệp `AfterExecuteDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class AfterExecuteDemo {
    static final class ObservingPool extends ThreadPoolExecutor {
        final CountDownLatch observed = new CountDownLatch(1);
        final AtomicReference<String> cause = new AtomicReference<>("none");

        ObservingPool() {
            super(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
            );
        }

        @Override
        protected void afterExecute(Runnable task, Throwable failure) {
            super.afterExecute(task, failure);

            Throwable found = failure;
            if (found == null && task instanceof Future<?> future) {
                try {
                    future.get();
                } catch (ExecutionException ex) {
                    found = ex.getCause();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            if (found != null) {
                cause.set(found.getClass().getSimpleName());
            }
            observed.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        ObservingPool pool = new ObservingPool();
        try {
            Future<?> future = pool.submit(() -> {
                throw new IllegalArgumentException("sample");
            });

            try {
                future.get();
            } catch (ExecutionException expected) {
                // Caller đã nhận exception, hook cũng sẽ quan sát.
            }

            pool.observed.await();
            System.out.println("afterExecute = " + pool.cause.get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AfterExecuteDemo.java
java AfterExecuteDemo
~~~

Stdout:

~~~text
afterExecute = IllegalArgumentException
~~~

`future.get()` bên ngoài quan sát lỗi ở caller. `afterExecute` chạy sau task, thấy `failure == null` do wrapper FutureTask đã giữ exception, sau đó lấy `getCause()` từ Future. `observed` giúp main chờ hook đã ghi nhận xong trước khi in, nên không phụ thuộc thứ tự scheduling.

Ví dụ chỉ gửi một task thất bại và không hủy nó, nên không cần nhánh riêng cho `CancellationException`. **Trong ứng dụng thật**, hook phải xử lý cả Future bị hủy và mọi loại wrapper, cũng như giữ hook ngắn và không block. Mẫu này thể hiện cách nhìn rõ failure của task chứ không thay thế quản lý retry ở tầng nghiệp vụ.

### 9.4. Dùng hook hay quan sát Future ở caller?

- Khi caller **cần kết quả để ra quyết định**, dùng `Future.get()` hoặc stage composition phù hợp; exception thuộc contract xử lý kết quả.
- Khi cần **giám sát tập trung** mọi task của một `ThreadPoolExecutor` tự quản, `afterExecute` hoặc task wrapper có thể ghi nhận metric và lỗi.
- Khi chỉ cần log **uncaught thread failure**, handler là lớp bảo vệ bổ sung, không phải hệ thống xử lý mọi Future.

Có thể dùng kết hợp cả ba, nhưng cần tránh ghi lỗi trùng nhiều lần và nhớ rằng **log lỗi không phải nghiệp vụ đã xử lý lỗi**.

## 10. Worker chết bất thường và khả năng thay thế

### 10.1. Một task throw có nghĩa gì đối với pool?

Trong `ThreadPoolExecutor`, khi một `Runnable` thoát bằng throwable không bắt, worker đang chạy task đó có thể kết thúc bất thường. Pool có cơ chế quản lý worker count và **có thể tạo worker thay thế** tùy cấu hình và trạng thái executor.

Nhưng những điều sau **không tự xảy ra**:

- Task thất bại được tự chạy lại như một transaction.
- Side effect đã làm một nửa được rollback.
- Người submit qua `execute` tự có một Future để nhận exception.
- Future của task submit bị lỗi sẽ gửi exception qua handler.
- Mọi tài nguyên phụ thuộc ThreadLocal được dọn sạch trước khi worker chết.
- Shutdown của executor trở nên an toàn dù task đang giữ resource hoặc lock khác.

Nếu cần retry, phải thiết kế cơ chế xác định task có idempotent hay không, số lần thử, backoff/deadline và kênh trả kết quả. Với tác vụ gửi email/chuyển tiền, retry vô điều kiện có thể lặp side effect.

### 10.2. ThreadFactory không kiểm soát lịch task

Một factory được gọi khi pool cần worker. Nó **không** được gọi mỗi lần task bắt đầu nên:

- Không đặt request ID hoặc user context cho từng task bằng cấu hình thread cố định.
- Không gắn thông tin task hiện hành vào tên thread trong factory rồi giả định nó thay đổi theo task.
- Không dùng `UncaughtExceptionHandler` để xử lý business-level rollback của từng request.
- Với `ThreadLocal`/MDC, hãy đặt và xóa trong task wrapper hoặc hook được thiết kế, bằng `try/finally`.

Một worker có thể chạy hàng nghìn task; context còn sót của task trước có thể ảnh hưởng task sau, kể cả khi không có exception. Nếu bạn sửa tên thread cho từng task, hãy restore tên gốc trong finally để tránh rò rỉ metadata giữa task.

## 11. Daemon, priority, context class loader và security

### 11.1. Daemon workers

`Thread.setDaemon(true)` khiến thread là daemon. JVM có thể kết thúc khi chỉ còn daemon threads, vì vậy những công việc cần ghi dữ liệu, flush queue hay cleanup lúc shutdown **không nên chỉ dựa vào daemon status**.

Một pool có daemon worker **không** có nghĩa rằng task sẽ hoàn thành trước khi JVM thoát. Trái lại, non-daemon worker có thể giữ JVM sống nếu quên shutdown pool. Vì thế lựa chọn daemon phải đi kèm lifecycle, không phải thủ thuật chữa pool “không chịu thoát”.

Trong `ThreadFactory`, gọi `setDaemon(...)` trước khi thread start; không cố thay đổi daemon status của thread đã chạy. Tên và priority cũng nên được cấu hình trước khi return thread.

### 11.2. Thread priority

`setPriority(...)` phụ thuộc JVM và scheduler, không bảo đảm task priority, FIFO hoặc throughput theo công thức đơn giản. Không dùng thread priority thay cho queue có thứ tự hoặc cơ chế SLA.

### 11.3. Context class loader

Worker có thể kế thừa context class loader của thread tạo nó, tùy cách constructor/triển khai được sử dụng. Với môi trường plugin, module hoặc class loading phức tạp, việc giữ context class loader quá lâu có thể giữ reference ngoài ý muốn. Cần chính sách rõ về class loader, nhưng không tự đổi nó thành null hay system loader nếu chưa biết nhu cầu của thư viện gọi từ task.

### 11.4. ThreadGroup và handler mặc định

Khi không có handler riêng trên thread, cơ chế fallback có thể đi qua `ThreadGroup` và default handler. Điều này hữu ích cho chẩn đoán, nhưng handler ở factory là lựa chọn rõ ràng hơn nếu bạn muốn ghi nhận lỗi của **một nhóm worker cụ thể** mà không thay đổi hành vi của các thread khác trong JVM.

Không nên cấu hình `Thread.setDefaultUncaughtExceptionHandler(...)` như một thao tác cục bộ vô hại: nó có tác động ở cấp JVM và có thể ghi đè chính sách global của thư viện hoặc ứng dụng khác.

## 12. Lỗi task, cancellation và interrupt không giống nhau

### 12.1. `Future.cancel(true)` là yêu cầu hủy, không phải exception từ task

Một task đang chờ `BlockingQueue.take()` hay `Thread.sleep()` có thể phản ứng với interrupt bằng `InterruptedException`. Nếu task được chạy qua `submit`, việc task kết thúc do hủy hoặc exception thường được phản ánh qua **Future state** và contract riêng của executor.

Đừng chờ `UncaughtExceptionHandler` nhận mọi `InterruptedException`: đây là checked exception mà nhiều API task xử lý hoặc đưa vào Future; nó không mặc nhiên thoát khỏi `Thread.run()`. Với `Callable`, exception được ném vào boundary `FutureTask` và quan sát qua `get()`.

### 12.2. Future.cancel không xác nhận worker đã cleanup

`Future.cancel(true)` có thể đánh dấu future hủy dù code task vẫn đang chạy một thời gian, ví dụ vì nó không kiểm tra interrupt hoặc đang dọn tài nguyên. Nếu cần biết **cleanup đã hoàn tất**, dùng một kênh xác nhận riêng như latch, trạng thái task hoặc cấu trúc phối hợp thích hợp.

`CompletableFuture.cancel(true)` không dùng tham số `mayInterruptIfRunning` để điều khiển execution theo hợp đồng API. Vì vậy không suy ra CompletableFuture đang chạy sẽ bị interrupt giống một task qua `FutureTask`.

### 12.3. Một số lỗi có thể làm hỏng JVM hoặc process

`UncaughtExceptionHandler` là callback quan sát lỗi, không phải ranh giới phục hồi tin cậy cho mọi `Error`. Ví dụ tình trạng thiếu bộ nhớ, stack overflow hoặc lỗi hệ thống nghiêm trọng có thể khiến logging/metric trong handler cũng thất bại. Đừng cố đảm bảo mọi failure đều được handler ghi nhận hoặc có thể tiếp tục bình thường sau một `Error`.

## 13. Làm sao kiểm thử exception reporting?

### 13.1. Kiểm thử bằng latch và Future, không bằng ngủ đoán thời gian

Với handler, test có thể dùng `CountDownLatch` đặt ngay trong callback để xác nhận nó đã được gọi. Với `submit`, sử dụng `Future.get()` để quan sát nguyên nhân `ExecutionException`. Với `afterExecute`, dùng latch hoặc kênh kết quả để xác nhận hook đã hoàn thành trước khi assert.

Đừng viết:

~~~java
// Sai về cách kiểm chứng, không phải chương trình độc lập.
executor.execute(() -> { throw new RuntimeException("boom"); });
Thread.sleep(100);
assert handlerCalled;
~~~

Scheduler có thể khiến task/handler chưa chạy sau 100ms dù code đúng. Dùng latch với timeout trong bài test giúp phản ánh lỗi mà không để suite treo vĩnh viễn.

### 13.2. Những case nên kiểm tra

| Test | Kết quả cần xác nhận |
| --- | --- |
| Factory đặt tên worker | Tên có tiền tố, ID đủ phân biệt, thread chưa start ở factory |
| Pool tái sử dụng worker | Nhiều task có thể chạy chung worker, không dùng factory per-task |
| `execute(plainRunnable)` ném unchecked | Handler/hook nhận lỗi theo triển khai pool chuẩn |
| `submit(Callable)` ném unchecked | `Future.get()` có `ExecutionException` với cause đúng |
| `submit(Runnable)` ném unchecked | Handler không phải kênh nhận lỗi mặc định |
| `execute(FutureTask)` ném trong delegate | Lỗi được giữ trong FutureTask; không suy ra handler chạy |
| Task tự catch exception | Không có uncaught throwable nếu không rethrow |
| `afterExecute` với Future lỗi | Hook unwrap được cause sau khi Future hoàn tất |
| Future bị cancel | Phân biệt cancellation với failure thông thường |
| Worker kết thúc bất thường | Pool có thể tạo worker khác; task cũ không tự retry |
| Shutdown | Không rò rỉ worker, không chờ vô hạn; xử lý timeout |
| Handler/hook tự throw | Không để reporting path phá hỏng sự ổn định của pool |

### 13.3. Kênh kết quả và quan sát lỗi

Một thiết kế tốt nên trả lời: **ai sở hữu trách nhiệm xem lỗi task?**

- Caller của `submit` giữ Future và gọi `get()`, hoặc chuyển nó cho một tầng quản lý có trách nhiệm theo dõi.
- Một hệ thống fire-and-forget có thể dùng task wrapper: bắt `Throwable` ở ranh giới phù hợp, ghi nhận rồi **rethrow** nếu muốn giữ hành vi lỗi của worker, hoặc tạo kết quả lỗi có cấu trúc.
- Một `ThreadPoolExecutor` custom có thể dùng `afterExecute` để thống kê lỗi tập trung, miễn hook không block và kiểm soát cancellation.
- Uncaught handler là lớp bảo vệ cuối cho các lỗi **thật sự thoát khỏi thread**, chứ không thay thế observability của Future.

Nếu bắt `Throwable` trong task wrapper, hãy hết sức thận trọng với `Error`; thường không nên âm thầm nuốt các lỗi nghiêm trọng. Logging phải an toàn, side effect chỉ được chạy một lần khi retry được thiết kế rõ.

## 14. Chọn ThreadPoolExecutor hay Executors factory method?

### 14.1. Executors.newFixedThreadPool

`Executors.newFixedThreadPool(n, factory)` tiện khi cần số worker cố định và đặt tên/handler chung. Nhưng factory method này dùng một hàng đợi có khả năng tăng không giới hạn theo thiết kế chuẩn; nếu producer gửi task nhanh hơn khả năng xử lý lâu dài, queue có thể lớn và gây áp lực bộ nhớ.

Khi cần **bounded queue**, rejection/backpressure và giám sát rõ, có thể tạo `ThreadPoolExecutor` trực tiếp với capacity, handler từ chối và lifecycle phù hợp.

### 14.2. Ví dụ cấu hình có giới hạn

~~~java
// Mẫu cấu hình, không phải chương trình độc lập.
// factory đã được tạo theo chính sách đặt tên/handler.
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    2, 4,
    30, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(100),
    factory,
    new ThreadPoolExecutor.AbortPolicy()
);
~~~

Mẫu này cần import `ThreadPoolExecutor`, `ArrayBlockingQueue` và `TimeUnit`. `AbortPolicy` ném `RejectedExecutionException` khi executor không thể nhận task theo chính sách; exception này có thể xảy ra **trong thread gọi execute/submit**, không phải uncaught failure của worker.

Đừng dựa vào `UncaughtExceptionHandler` để bắt task bị reject, vì task có thể **chưa được worker thực thi**. Caller phải xử lý `RejectedExecutionException`, retry/backpressure hoặc trả lỗi upstream.

### 14.3. Không lẫn trách nhiệm

| Thành phần | Trách nhiệm chính |
| --- | --- |
| `ThreadFactory` | Tạo worker thread với thuộc tính phù hợp |
| `RejectedExecutionHandler` | Quyết định cách xử lý task khi executor không thể nhận |
| `UncaughtExceptionHandler` | Nhận throwable thực sự không được bắt ở boundary của thread |
| `Future.get()` | Lấy kết quả/exception của task được biểu diễn bằng Future |
| `ThreadPoolExecutor.beforeExecute/afterExecute` | Hook trước/sau execution để đo/quan sát theo contract |
| `ExecutorService.shutdown/awaitTermination` | Quản lý lifecycle pool |
| Business retry policy | Quyết định task nào được thử lại, backoff, idempotency, deadline |

Càng tách rõ trách nhiệm, càng dễ tránh lỗi “đã có handler mà sao Future thất bại không ai biết”.

## 15. Các lỗi phổ biến cần tránh

**Lỗi 1 — Nghĩ `ThreadFactory` được gọi cho từng `submit`.** Worker có thể được tái sử dụng. Muốn context theo task, phải bọc task hoặc truyền dữ liệu trong task.

**Lỗi 2 — Factory gọi `thread.start()` trước khi trả.** Executor sở hữu thời điểm khởi động; trả thread chưa start.

**Lỗi 3 — Chỉ gắn `UncaughtExceptionHandler` rồi coi đã bắt mọi task exception.** `submit` thường lưu lỗi trong `Future`; người sở hữu Future phải quan sát.

**Lỗi 4 — Gửi `FutureTask` qua `execute` rồi chờ handler.** `FutureTask.run()` tự bắt throwable, do đó lỗi có thể không thoát ra worker.

**Lỗi 5 — Không bao giờ gọi `Future.get()` trên task quan trọng.** Lỗi bị lưu nhưng không ai nhìn; cần protocol kết quả hoặc giám sát tập trung.

**Lỗi 6 — `afterExecute` chỉ kiểm tra `Throwable error != null`.** Với `submit`, tham số đó có thể null ngay cả khi task thất bại. Phải unwrap Future khi phù hợp.

**Lỗi 7 — Gọi `get()` tùy tiện trong hook trên Future chưa done.** Có thể block worker/đặt ra dependency vô hạn; chỉ unwrap task hoàn tất thuộc executor và có guard thích hợp.

**Lỗi 8 — Ném exception từ handler hoặc hook rồi coi như đã xử lý lỗi.** Reporting code nên an toàn và không gây failure thứ cấp.

**Lỗi 9 — Cứ handler được gọi thì executor tự retry task.** Worker có thể được thay thế, nhưng task lỗi không tự được làm lại.

**Lỗi 10 — Dùng daemon workers để chữa việc quên shutdown.** JVM có thể thoát trước khi task quan trọng hoàn tất. Quản lý lifecycle đúng cách thay vì trông chờ daemon.

**Lỗi 11 — Không reset ThreadLocal/MDC giữa các task.** Worker tồn tại lâu hơn một task; dữ liệu request trước có thể rò sang task sau.

**Lỗi 12 — Xem `cancel(true)` là đã cleanup xong.** Tín hiệu hủy khác hoàn tất thực thi và side effect; cần xác nhận riêng.

**Lỗi 13 — Dùng một ThreadFactory cho nhiều executor với tên không phân biệt.** Thread dump khó đọc; nên có prefix theo pool và số ID.

**Lỗi 14 — Bỏ qua rejection ở caller.** Task bị từ chối chưa chắc chạy trên worker; handler uncaught của worker không xử lý chuyện này.

**Lỗi 15 — Chạy I/O lâu trong handler.** Thread đang xử lý lỗi có thể bị giữ quá lâu, làm chậm thay thế worker hoặc che giấu sự cố; nên report tối giản và có fallback.

## 16. Checklist review và bài tập

### Checklist triển khai

- `ThreadFactory.newThread` có luôn trả **Thread mới, chưa start** theo contract?
- Tên worker có prefix dễ nhận biết pool và ID tăng ổn định?
- Daemon/priority/context class loader có được quyết định chủ ý, không chỉ sao chép cấu hình tùy tiện?
- Handler có ghi nhận throwable đủ thông tin và tránh ném lỗi phụ, deadlock hoặc I/O dài?
- Task gửi qua `execute` và `submit` có **kênh nhận exception** khác nhau được tài liệu hóa?
- Caller có trách nhiệm quan sát từng Future quan trọng không?
- Nếu dùng `afterExecute`, có xử lý `Future.get()`/`ExecutionException`/`CancellationException`/interrupt đúng không?
- Có tránh block trên Future khác chưa hoàn tất trong hook?
- Có tách trạng thái task thất bại, bị hủy, bị reject và pool shutdown?
- Có hiểu rằng worker replacement **không** tự retry task thất bại?
- Có tránh rò rỉ `ThreadLocal` của task cũ qua task mới không?
- Có đo queue length, thời gian xử lý, số lỗi và latency, đồng thời quản lý pool shutdown?
- Có tránh gắn default handler toàn JVM ngoài phạm vi ownership?
- Có test exception reporting bằng latch/Future thay vì ngủ đoán scheduling?

### Câu hỏi 1

**Tại sao `ExecutorService.submit(...)` có thể thất bại mà `UncaughtExceptionHandler` không được gọi?**

Vì với `ThreadPoolExecutor` chuẩn, submit thường đóng gói task trong `FutureTask`. Wrapper đó bắt throwable và lưu vào Future; throwable không thoát khỏi worker như một uncaught exception. Caller cần gọi `get()` hoặc dùng kênh quan sát khác.

### Câu hỏi 2

**Nếu truyền `FutureTask` vào `execute(...)` thay vì gọi `submit(...)` thì handler chắc chắn thấy lỗi không?**

Không. `FutureTask.run()` vẫn giữ exception nội bộ; tên method `execute` không thay đổi hành vi wrapper.

### Câu hỏi 3

**Factory có nên start thread ngay để giảm độ trễ không?**

Không. `ThreadFactory.newThread` tạo và trả thread chưa start; executor quản lý `start()`. Start sớm có thể phá lifecycle và tính đúng đắn.

### Câu hỏi 4

**Khi `execute(plainRunnable)` ném exception, pool có tự chạy lại đúng task ấy không?**

Không. Worker có thể kết thúc và được thay thế, nhưng retry task là trách nhiệm riêng cần thiết kế điều kiện, giới hạn và idempotency.

### Câu hỏi 5

**`afterExecute(Runnable, Throwable)` nhận tham số Throwable là null có chứng minh submit task đã thành công không?**

Không. `FutureTask` có thể bắt exception nên tham số throwable null dù Future ở trạng thái lỗi. Có thể unwrap kết quả từ Future đã hoàn tất.

### Câu hỏi 6

**Tại sao đặt request ID vào tên thread trong factory không đủ để ghi nhận request hiện tại?**

Vì worker có thể thực thi nhiều task liên tiếp. Factory chỉ chạy lúc tạo worker, không chạy theo mỗi request. Metadata từng request cần được đặt/xóa theo scope task.

### Câu hỏi 7

**`Future.cancel(true)` hay `future.isCancelled()` có chứng minh worker đã release tài nguyên chưa?**

Không. Future cancellation state có thể thay đổi trước khi worker thực sự hoàn thành cleanup; dùng kênh xác nhận riêng khi cần.

### Câu hỏi 8

**Khi pool dùng queue có giới hạn và task bị reject, nơi nào nên xử lý?**

Chính thread gọi `execute/submit` và chính sách `RejectedExecutionHandler`. Task có thể chưa chạy trên worker, nên uncaught handler của worker không phải kênh nhận lỗi này.

**Tóm lại:** `ThreadFactory` xác định **worker được tạo như thế nào**, `UncaughtExceptionHandler` quan sát **throwable thật sự thoát khỏi thread**, còn `Future` và `afterExecute` giúp quan sát **kết quả hoặc lỗi của task** trong executor. Muốn vận hành đúng cần phân biệt worker lifecycle và task lifecycle, tránh nuốt lỗi submit, quản lý shutdown, cleanup và không coi handler là cơ chế retry hay phục hồi mọi exception.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-009">Lựa chọn pool size cho CPU-bound và I/O-bound</a>

<details>
<summary>Xem chi tiết</summary>

# Lựa chọn pool size cho CPU-bound và I/O-bound trong Java

## 1. Vì sao pool size là một quyết định về năng lực hệ thống?

Trong Executor Framework, thread pool tái sử dụng các worker thread để xử lý nhiều tác vụ. Số worker ảnh hưởng trực tiếp đến **throughput** (số tác vụ hoàn thành mỗi giây), **latency** (thời gian đáp ứng), mức sử dụng CPU, bộ nhớ, chuyển ngữ cảnh và hàng đợi chờ.

- **Quá ít thread:** CPU có thể nhàn rỗi khi worker đang chờ I/O; tác vụ phải xếp hàng dù hệ thống còn khả năng xử lý.
- **Quá nhiều thread:** tác vụ CPU tranh nhau cùng lõi xử lý, tăng context switching, bộ nhớ stack, GC và tranh chấp lock. Với I/O, quá nhiều request đồng thời còn có thể làm nghẽn connection pool, database hoặc dịch vụ phía sau.
- **Đúng kích thước:** giữ tài nguyên có ích luôn bận ở mức mục tiêu, đồng thời khống chế hàng đợi và giới hạn năng lực của các phụ thuộc.

**Không có một con số pool size tốt cho mọi ứng dụng.** Công thức chỉ là điểm khởi đầu. Giá trị cuối cùng phải được kiểm chứng bằng tải đại diện, mức CPU được cấp, hành vi blocking, độ trễ và giới hạn downstream.

## 2. Nhận diện bản chất tác vụ

| Đặc điểm | CPU-bound | I/O-bound |
|---|---|---|
| Phần lớn thời gian | Đang thực thi tính toán trên CPU | Chờ đọc/ghi file, network, JDBC, remote service... |
| Nút thắt thông thường | Số lõi CPU và chi phí điều phối | Độ trễ I/O và giới hạn tài nguyên bên ngoài |
| Tăng thread vượt ngưỡng | Ít tăng throughput, có thể chậm hơn | Có thể che giấu thời gian chờ, đến khi chạm giới hạn khác |
| Ví dụ | Nén dữ liệu, tính toán hash, biến đổi ảnh, tính toán số học | Đọc file blocking, gọi HTTP blocking, truy vấn JDBC, chờ socket |

Một tác vụ có thể **mixed workload**: parse JSON bằng CPU, gọi dịch vụ qua mạng, rồi xử lý dữ liệu tiếp. Hãy đo phần **thực thi CPU** và phần **đợi** trong cùng vòng đời tác vụ, thay vì gắn nhãn theo tên phương thức. Một lệnh truy vấn database vừa tốn CPU tại server vừa khiến thread Java phía client chờ I/O; hai nguồn lực đó phải được đánh giá riêng.

Phân biệt thêm:

- **Blocking:** worker thread bị chiếm trong lúc đợi, ví dụ Thread.sleep, blocking socket read hoặc Future.get. Thread đó không chạy tác vụ khác.
- **Non-blocking/asynchronous I/O:** thread có thể phục vụ công việc khác khi I/O chưa hoàn thành. Không được áp nguyên công thức của blocking thread pool cho event loop.
- **Lock contention:** thread chờ monitor/lock không dùng CPU hữu ích nhưng tăng số worker chưa chắc giúp ích; trước hết kiểm tra nguyên nhân tranh chấp.

## 3. CPU-bound: bắt đầu gần số CPU thực sự có

Gọi **N** là số logical processor hiệu dụng mà tiến trình Java được phép sử dụng, **U** là mục tiêu sử dụng CPU (0–1), và **P** là số worker chạy tác vụ tính toán độc lập. Một điểm xuất phát thường dùng là:

**P ≈ N × U**, sau đó đo và điều chỉnh, thường kiểm thử quanh **N** worker.

Ví dụ tiến trình thực sự có thể dùng 8 logical CPU, mục tiêu CPU khoảng 80–90% thì thử khoảng 6–8 worker tính toán. Đây là **phạm vi thử nghiệm**, không phải quy tắc làm tròn bắt buộc; CPU còn phục vụ GC, runtime, các luồng khác và dịch vụ cùng máy.

Nếu toàn bộ tác vụ luôn runnable và tiêu thụ CPU, tăng từ 8 lên 80 worker trên máy chỉ có năng lực 8 logical CPU không tạo thêm tài nguyên CPU. Nó thường làm tăng run queue, context switching và cạnh tranh cache, trong khi throughput bão hòa.

**Cẩn thận khi xác định N:**

1. Runtime.getRuntime().availableProcessors() là tín hiệu tiện dùng, nhưng cần đối chiếu với CPU quota/cpuset của container, cấu hình JVM và số CPU thực sự được phân bổ. Giới hạn CPU thực tế có thể khác CPU vật lý của host; phiên bản JVM và cấu hình container awareness cũng ảnh hưởng số trả về.
2. Logical CPU từ SMT/Hyper-Threading không đồng nghĩa từng CPU có năng lực của một core vật lý độc lập. Hãy benchmark tác vụ thật.
3. Nếu nhiều pool CPU-bound cùng dùng chung JVM, không phân bổ **N worker chạy kín CPU cho từng pool** như thể mỗi pool có N lõi riêng.
4. Công việc có synchronized, JNI, cấp phát/GC nặng hoặc truy cập bộ nhớ thường không scale tuyến tính với số thread; kiểm tra profile để tìm giới hạn thực.

## 4. I/O-bound dạng blocking: tính cả thời gian chờ

Đặt:

- **N:** năng lực CPU hiệu dụng.
- **U:** mức sử dụng CPU mục tiêu, ví dụ 0,8.
- **C:** thời gian CPU mà một tác vụ tiêu thụ trung bình.
- **W:** tổng thời gian tác vụ **không tiêu thụ CPU hữu ích** trong khoảng xử lý, chủ yếu do blocking/waiting, với cùng đơn vị đo như C.
- **P:** số worker của pool.

Công thức ước lượng thường gặp:

**P ≈ N × U × (1 + W / C)**

Diễn giải: nếu tác vụ chỉ chạy CPU 1 phần trong tổng (C + W) phần thời gian, cần nhiều worker hơn số CPU để lúc một worker đợi, worker khác có thể chạy. Công thức giả định tải đều, các tác vụ tương đối độc lập, chờ có thể chồng lấp và CPU là ràng buộc đang mô hình hóa. **Nó không bảo đảm pool có kích thước đó sẽ an toàn cho database, API hoặc bộ nhớ.**

**Ví dụ:** N = 8, U = 0,8, mỗi tác vụ tốn C = 5 ms CPU và W = 45 ms chờ I/O.

~~~text
W / C = 45 / 5 = 9
P ≈ 8 × 0,8 × (1 + 9) = 64 worker
~~~

Có thể thử 32, 48, 64 worker dưới tải thực tế rồi so sánh throughput, p95/p99 latency, CPU, queue wait và lỗi downstream. Nếu database chỉ cho phép 20 kết nối hữu dụng, tăng lên 64 worker không tạo được 64 truy vấn database song song: phần dư sẽ đợi kết nối, làm queue và tail latency tệ hơn. **Giới hạn downstream có thể quyết định mức đồng thời nhỏ hơn công thức.**

Một cách viết tương đương, khi đặt tỷ lệ thời gian đợi là b = W/(C+W), là **P ≈ N × U / (1-b)** với 0 ≤ b < 1. Khi C rất nhỏ, tỷ lệ W/C cực lớn và công thức có thể cho kết quả phi thực tế; lúc đó cần đặt giới hạn concurrency/queue/memory trước, không tạo hàng nghìn platform thread theo kết quả máy tính.

### Không nhầm throughput với concurrency

Giả sử hệ thống nhận trung bình λ tác vụ/giây và mỗi tác vụ ở trong hệ thống trung bình T giây. Theo **Little's Law**, lượng tác vụ đồng thời trung bình **L = λ × T** trong trạng thái ổn định. Ví dụ 200 tác vụ/giây với thời gian xử lý trung bình 0,2 giây tương ứng khoảng 40 tác vụ đang xử lý/đợi trong hệ thống.

L ở đây có thể gồm cả tác vụ **trong queue**, nên không được tự động coi L là số worker cần tạo. Để sizing, cần quan sát riêng queue wait, thời gian đang xử lý và tài nguyên mà mỗi giai đoạn sử dụng. Nếu tốc độ đến vượt năng lực xử lý bền vững, queue vẫn tăng dù có tăng nhẹ pool size.

## 5. Đo C, W và khả năng chịu tải như thế nào?

**Bước 1 — phân loại từng loại công việc.** Ghi lại đường đi của request: phần tính toán, blocking I/O, lock, chờ connection, retry và giới hạn bên ngoài. Pool chuyên xử lý request gồm nhiều giai đoạn cần đo trên đúng kiểu request; không lấy tỷ lệ của một tác vụ nhỏ rồi áp cho mọi tác vụ.

**Bước 2 — đo dưới tải đại diện.** Thu thập thời gian từ lúc submit đến khi bắt đầu chạy (**queue wait**), thời gian chạy từ start đến finish (**task wall time**), CPU time thực thi trong worker, độ trễ gọi I/O, timeout/rejection, số kết nối đang sử dụng, CPU utilization và throughput. JFR, thread dump, profiler, metrics của executor, ThreadMXBean và metric của downstream có thể hỗ trợ.

Với ThreadMXBean, getCurrentThreadCpuTime() đo CPU time của thread hiện hành khi JVM hỗ trợ và bật chức năng; không phải cứ lấy tổng wall time trừ CPU time là thu được chính xác riêng **thời gian chờ I/O**. Phần chênh còn chứa scheduling, lock waits và các khoảng không được đo khác. Hãy đo từng đoạn I/O bằng timer và kiểm tra ở cấp hệ thống. Với tác vụ rất ngắn, overhead đo có thể đáng kể.

**Bước 3 — thử dải giá trị.** Giữ workload, input, thời gian warm-up, CPU quota và dependency tương đương, sau đó thay **một tham số**: ví dụ CPU pool = 4/6/8/12 hoặc blocking I/O pool = 16/32/48/64. Đánh giá throughput, p50/p95/p99, error/timeout, CPU, memory, thread count, queue depth và GC. Đừng chọn cấu hình chỉ vì throughput cao nhất nếu p99 hoặc số lỗi tăng mạnh.

**Bước 4 — tải đột biến và quá tải.** Kiểm tra cả tải ổn định, burst, dependency chậm, connection pool cạn và shutdown. Sizing thành công phải có hành vi backpressure/rejection rõ ràng khi vượt công suất.

## 6. Ví dụ Java 17 chạy được: tách CPU pool và blocking I/O pool

Chương trình sau cố ý mô phỏng hai lớp công việc: vòng lặp tính toán chiếm CPU và Thread.sleep mô phỏng **thời gian một platform thread phải chờ**. Chương trình minh họa cách tạo pool riêng, submit Callable, chờ Future và shutdown; **đây không phải benchmark** để suy ra pool size tối ưu. Trong ứng dụng thật, Thread.sleep không thay thế cho đo latency I/O.

Tạo tệp **PoolSizingDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PoolSizingDemo {
    public static void main(String[] args)
            throws InterruptedException, ExecutionException {
        int cpus = Math.max(1, Runtime.getRuntime().availableProcessors());

        // Giá trị khởi đầu để minh họa; phải đo để cấu hình thực tế.
        int cpuPoolSize = cpus;
        int blockingIoPoolSize = Math.min(32, Math.max(4, cpus * 4));

        List<Callable<Long>> cpuTasks = new ArrayList<>();
        for (int task = 0; task < 20; task++) {
            cpuTasks.add(() -> {
                long sum = 0;
                for (int number = 1; number <= 100_000; number++) {
                    sum += number;
                }
                return sum;
            });
        }

        List<Callable<Integer>> ioTasks = new ArrayList<>();
        for (int task = 0; task < 20; task++) {
            ioTasks.add(() -> {
                Thread.sleep(30); // Mô phỏng chờ I/O blocking.
                return 1;
            });
        }

        long checksum = 0;
        for (long value : execute(cpuPoolSize, cpuTasks)) {
            checksum += value;
        }

        int completed = 0;
        for (int value : execute(blockingIoPoolSize, ioTasks)) {
            completed += value;
        }

        System.out.println("CPU checksum = " + checksum);
        System.out.println("I/O completed = " + completed);
    }

    private static <T> List<T> execute(
            int poolSize, List<Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            List<Future<T>> futures = executor.invokeAll(tasks);
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac PoolSizingDemo.java
java PoolSizingDemo
~~~

Kết quả chuẩn (không phụ thuộc thứ tự thread):

~~~text
CPU checksum = 100001000000
I/O completed = 20
~~~

Mỗi tác vụ CPU tính 1 + 2 + ... + 100000 = 5.000.050.000; 20 tác vụ cộng lại thành 100.001.000.000. Hai kích thước pool chỉ là cấu hình **minh họa**. Không lấy thời gian chạy ngẫu nhiên của chương trình làm kết luận benchmark vì JIT, warm-up và scheduling có thể thay đổi.

Ở ví dụ này invokeAll đợi toàn bộ công việc hoàn tất trước khi trả về; shutdown nằm trong finally để không tiếp nhận task mới. Với lifecycle thực tế, cần quản lý shutdown có thời hạn, xử lý InterruptedException đúng cách và cân nhắc shutdownNow khi không thể chờ thêm.

## 7. Pool size chỉ là một nửa: hàng đợi, giới hạn và rejection

**Executors.newFixedThreadPool(n)** sử dụng n worker và hàng đợi LinkedBlockingQueue không giới hạn dung lượng một cách tường minh. Tăng tốc độ submit cao hơn tốc độ hoàn thành khiến queue có thể phình lớn, ngốn heap và tạo latency rất cao, dù số thread cố định.

Với dịch vụ cần giới hạn tải, có thể dùng ThreadPoolExecutor cùng **bounded queue** và chính sách rejection có chủ đích:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BoundedPoolDemo {
    public static void main(String[] args) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4, 4,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            executor.execute(() -> System.out.println("Task accepted"));
        } catch (RejectedExecutionException rejected) {
            System.err.println("Pool overloaded or shutting down");
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Lưu ở **BoundedPoolDemo.java**, chạy:

~~~bash
javac BoundedPoolDemo.java
java BoundedPoolDemo
~~~

Với chương trình đơn nhiệm vụ ở trên, stdout là:

~~~text
Task accepted
~~~

Ở tải thật, AbortPolicy báo RejectedExecutionException khi không thể nhận task, giúp phía gọi phản ứng rõ ràng (trả lỗi quá tải, ghi metric, retry **có giới hạn và backoff** hoặc điều tiết producer). CallerRunsPolicy có thể làm thread submit tự chạy task để giảm tốc độ đưa việc vào, nhưng có thể kéo dài latency của caller; cần xem kỹ hành vi lúc pool shutdown. Không dùng DiscardPolicy nếu không chấp nhận mất việc thầm lặng.

**Ý nghĩa corePoolSize / maximumPoolSize:** ThreadPoolExecutor thường ưu tiên tạo thread tới corePoolSize, sau đó **đưa vào queue**, rồi chỉ tạo thêm thread tới maximumPoolSize nếu queue không nhận thêm. Với queue không giới hạn, maximumPoolSize hầu như không tăng thread quá corePoolSize. Khi lựa chọn size, cần thiết kế **pool + queue + rejection + timeout** cùng lúc.

Tách thêm hai giới hạn:

- **Số worker:** số Java thread đang chạy/chờ.
- **Số request tới dependency:** số kết nối, RPC hay transaction đồng thời mà tài nguyên ngoài chịu được.

Ví dụ pool có 64 worker nhưng database pool tối đa 16 connection thì có thể dùng Semaphore hoặc cơ chế giới hạn kết nối để giữ tối đa 16 truy vấn đồng thời; lúc đó vẫn phải thiết kế queue/wait timeout cho phần còn lại. Đừng tăng cả hai giới hạn chỉ để làm biến mất queue khi database đã bão hòa.

## 8. Khi nào tách pool, khi nào dùng virtual threads?

**Tách pool khi tính chất công việc khác nhau.** Nếu 8 tác vụ tính toán dài dùng kín 8 worker và tác vụ blocking I/O cũng nằm trong cùng pool 8 thread, công việc I/O có thể bị starve. Một CPU pool nhỏ và một blocking I/O pool lớn hơn, có giới hạn riêng, làm chính sách tài nguyên dễ quan sát hơn. Nhưng không nên tạo hàng loạt pool cho từng phương thức; tổng worker của toàn JVM vẫn chịu giới hạn chung.

**ForkJoinPool / work-stealing** phù hợp với một số tác vụ CPU nhỏ chia để trị và các tác vụ có quan hệ fork/join. Nó không phải lựa chọn mặc định cho I/O blocking tùy ý; blocking thiếu khai báo có thể khiến worker bị chiếm và giảm song song. Với logic cụ thể, xem cơ chế ManagedBlocker và đặc tính các API dùng common pool.

**Virtual threads (Java 21+):** với nhiều tác vụ I/O blocking theo mô hình thread-per-request, virtual threads cho phép số lượng tác vụ đồng thời lớn mà không cần một pool platform thread tương ứng. Khi đó thường **không sizing pool virtual thread theo N × (1 + W/C)**; tạo virtual thread theo task và giới hạn concurrency tại tài nguyên khan hiếm như connection, API hoặc rate limit. Virtual threads **không làm CPU nhanh hơn**; code CPU-bound vẫn tranh chấp số CPU thực tế. Một số blocking/native/monitor scenarios có thể ảnh hưởng carrier threads tùy phiên bản JDK; cần quan sát thực tế. Code Java 17 phía trên vẫn dùng platform threads, không giả định Java 21.

Ví dụ cú pháp Java 21+ (độc lập với chương trình Java 17 ở trên):

~~~java
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class VirtualThreadIoDemo {
    public static void main(String[] args) throws Exception {
        Semaphore downstreamLimit = new Semaphore(10);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                executor.submit(() -> {
                    downstreamLimit.acquire();
                    try {
                        // Thay bằng I/O blocking thực tế tới dependency có giới hạn.
                        Thread.sleep(20);
                    } finally {
                        downstreamLimit.release();
                    }
                    return null;
                });
            }
        }
        System.out.println("100 tasks submitted and completed");
    }
}
~~~

Chạy bằng JDK 21+ sau khi lưu **VirtualThreadIoDemo.java**; khối try-with-resources chờ executor kết thúc các task khi close. Semaphore giới hạn số task **đi vào đoạn I/O** đồng thời là 10; 100 virtual thread được tạo không có nghĩa là 100 kết nối tới downstream. Với tình huống production, cần thêm timeout, cancellation và xử lý interruption phù hợp.

## 9. Quy trình chọn cấu hình cho một hệ thống cụ thể

1. **Xác định tải và mục tiêu:** số tác vụ/giây, concurrency, p95/p99 mục tiêu, SLA, giới hạn CPU/RAM, connection pool và rate limit.
2. **Phân loại từng loại tác vụ:** CPU-bound, blocking I/O, non-blocking I/O hay hỗn hợp; nêu rõ phụ thuộc nào bị gọi.
3. **Chọn baseline:** CPU pool thử quanh N; blocking I/O pool thử từ N × U × (1 + W/C) sau khi đo, rồi **chặn bởi giới hạn downstream, bộ nhớ và số platform thread khả dụng**.
4. **Chọn queue có hạn:** sức chứa phù hợp với burst và thời gian chờ chấp nhận được; xác định rejection, timeout và fallback.
5. **Load test:** tăng dần số worker và concurrency; đo throughput, CPU, độ trễ, queue wait, saturation, contention, timeout, GC và lỗi của dependency.
6. **Chọn cấu hình ổn định:** ưu tiên vùng throughput đã gần bão hòa nhưng tail latency, lỗi và tài nguyên còn an toàn; thử lại với burst và downstream chậm.

### Những dấu hiệu thực tế

| Quan sát | Gợi ý kiểm tra | Hành động thử |
|---|---|---|
| CPU gần 100%, queue tăng, thêm worker không tăng throughput | CPU saturation | Tối ưu thuật toán, giảm CPU/task hoặc mở rộng CPU; tránh tăng thread vô hạn |
| CPU thấp, nhiều worker TIMED_WAITING/WAITING ở I/O, queue dài | Blocking wait, connection starvation | Đo thời gian chờ; thử tăng worker có kiểm soát và kiểm tra giới hạn I/O |
| CPU thấp nhưng nhiều thread BLOCKED trên cùng lock | Contention | Giảm tranh chấp/critical section trước khi tăng pool |
| Độ trễ p99 tăng và queue phình khi burst | Chưa có backpressure, queue quá lớn | Bound queue, admission control, timeout và xử lý rejection |
| Timeout downstream tăng khi tăng pool | Dependency quá tải | Hạ concurrency downstream, giảm retry storm, đặt bulkhead/rate limit |
| Throughput ổn nhưng RAM/thread count tăng mạnh | Platform thread/queue quá nhiều | Giảm giới hạn, cân nhắc virtual threads cho blocking I/O Java 21+ |

### Lỗi thường gặp cần tránh

- Dùng một con số cố định như “pool = 2 × số core” cho mọi loại tác vụ.
- Áp công thức I/O cho CPU-bound hoặc coi thread count càng lớn thì nhanh hơn.
- Lấy Runtime.availableProcessors() làm bằng chứng đầy đủ về CPU quota, bỏ qua tài nguyên dùng chung.
- Đo wall time và gọi toàn bộ nó là CPU time; quên queue wait, lock contention và latency của dependency.
- Chọn maximumPoolSize rất cao trong khi dùng queue không giới hạn, rồi tưởng ThreadPoolExecutor sẽ tự tăng tối đa thread.
- Chỉ đo throughput trung bình, không theo dõi p99, rejection, timeout, RAM, GC hoặc overload.
- Tăng pool size để che giấu truy vấn chậm, khóa tranh chấp, connection pool nhỏ hoặc retry không giới hạn.
- Chạy CPU-heavy task trong pool dành cho blocking I/O rồi khiến các tác vụ I/O bị starvation.

## 10. Checklist khi hoàn thành

Một lựa chọn pool size được xem là có cơ sở khi bạn trả lời được: **N là bao nhiêu trong môi trường triển khai thật? C và W được đo thế nào? Giới hạn concurrency của dependency là bao nhiêu? Queue tối đa bao nhiêu task? Khi quá tải sẽ reject/backpressure ra sao? p95/p99 và throughput có còn đạt mục tiêu sau thử tải hay không?**

**Ghi nhớ:** CPU-bound thường bắt đầu gần năng lực CPU thực sự; blocking I/O có thể cần nhiều worker hơn để che thời gian chờ, nhưng mức đồng thời chỉ hợp lý khi nằm trong sức chịu đựng của hệ thống. Cấu hình chính xác đến từ đo đạc, thử tải và kiểm soát queue/downstream, không đến từ một công thức đơn lẻ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-010">submit task độc lập và tránh thread starvation</a>

<details>
<summary>Xem chi tiết</summary>

# Submit tác vụ độc lập và tránh thread starvation trong Java Executor Framework

## Mục tiêu

Sau bài này, bạn có thể:

- Phân biệt việc **submit nhiều tác vụ độc lập** với việc một tác vụ đang chạy lại chờ kết quả của tác vụ khác.
- Hiểu `ExecutorService`, `submit`, `Future`, hàng đợi và số worker ảnh hưởng tới tiến độ như thế nào.
- Nhận ra tình huống **thread starvation**, đặc biệt là *thread-starvation deadlock* khi worker chờ công việc đang mắc kẹt trong chính executor của nó.
- Chọn cách phối hợp tác vụ không chặn worker một cách không cần thiết; dùng timeout, cancellation, chia pool và backpressure đúng mục đích.
- Quan sát, tái hiện và kiểm thử lỗi bằng các công cụ Java SE.

Phạm vi: Java concurrency và Executor Framework nói chung; các ví dụ không phụ thuộc framework ứng dụng.

## 1. Mô hình vận hành: task, worker và hàng đợi

`ExecutorService` nhận công việc (`Runnable` hoặc `Callable<T>`) và quản lý việc thực thi thông qua các thread. Với thread pool cố định:

~~~text
Thread gọi submit()
       |
       v
  ExecutorService
       |
       +--> Worker 1: chạy task A
       +--> Worker 2: chạy task B
       +--> ... (tối đa số worker của pool)
       |
       +--> Queue: task C, task D, ... đợi worker rảnh
~~~

**Submit** có nghĩa là chuyển công việc cho executor và nhận một đối tượng theo dõi; nó **không bảo đảm** task được chạy ngay hoặc các task chạy song song. Tiến độ thực tế phụ thuộc số worker, chính sách tạo thread, hàng đợi, tài nguyên bên ngoài và việc worker có bị chặn hay không.

Các API chính:

| API | Ý nghĩa cần nhớ |
| --- | --- |
| `execute(Runnable)` | Gửi công việc không trả `Future`; lỗi không được lấy về bằng `Future.get()`. |
| `submit(Runnable/Callable)` | Trả `Future`; ngoại lệ của task được ghi nhận và xuất hiện qua `get()` dưới dạng `ExecutionException`. |
| `Future.get()` | Chặn **thread đang gọi** cho tới khi có kết quả, lỗi hoặc bị ngắt; bản không có timeout có thể đợi vô hạn. |
| `Future.get(timeout, unit)` | Giới hạn thời gian chờ của **caller**; hết hạn ném `TimeoutException`, **không tự hủy task**. |
| `Future.cancel(true)` | Yêu cầu hủy và, nếu task đang chạy, thử interrupt thread; task phải hợp tác mới dừng kịp. |
| `shutdown()` | Ngừng nhận task mới và cố chạy xong task đã nhận. |
| `shutdownNow()` | Thử interrupt task đang chạy, trả về task chưa bắt đầu; không bảo đảm mọi task dừng ngay. |

### Thế nào là tác vụ độc lập?

Hai task độc lập khi task này **không cần kết quả hoặc tín hiệu hoàn thành của task kia để chạy xong**. Chẳng hạn xử lý ba đầu vào độc lập rồi tổng hợp kết quả ở thread điều phối. Nếu cả ba cùng sửa một biến toàn cục hoặc tranh cùng tài nguyên, bạn vẫn phải xử lý an toàn dữ liệu và giới hạn tài nguyên; “độc lập về đầu vào” không có nghĩa “không cần đồng bộ”.

Mẫu hợp lý là: **gửi tất cả task trước, rồi mới chờ kết quả ở nơi điều phối**. Chờ `Future.get()` tuần tự ở thread gọi không khiến ba task chạy tuần tự, vì chúng đã được submit trước đó. Tuy nhiên thứ tự lấy kết quả có thể làm việc hiển thị chậm hơn nếu future đầu tiên chưa xong.

## 2. Ví dụ chạy được: submit ba task độc lập

Tạo tệp `IndependentTasksDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class IndependentTasksDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);

        try {
            List<Callable<Integer>> tasks = List.of(
                    () -> 10 * 10,
                    () -> 20 * 20,
                    () -> 30 * 30
            );

            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(task)); // Gửi hết trước
            }

            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get(2, TimeUnit.SECONDS); // Chờ ở main
            }

            System.out.println("Tổng = " + total);
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy trên JDK 17 trở lên:

~~~bash
javac IndependentTasksDemo.java
java IndependentTasksDemo
~~~

Đầu ra xác định:

~~~text
Tổng = 1400
~~~

Cả ba `Callable` không chờ nhau, nên pool có thể phân công chúng cho các worker khác nhau. Vòng lặp `get` ở `main` chỉ tổng hợp kết quả. Trong chương trình thực tế, phải quyết định chính sách khi một future timeout hoặc thất bại: log/thu thập lỗi, hủy việc dư thừa, hoặc trả kết quả một phần. `shutdown` trong `finally` bảo đảm không tiếp tục nhận task mới khi đoạn điều phối kết thúc.

**Sai khác thường gặp:** submit một task rồi gọi `get` ngay trong từng vòng lặp sẽ làm người gọi đợi task đó hoàn tất trước khi gửi task kế tiếp. Cách đó làm mất cơ hội chạy đồng thời, dù chưa chắc gây starvation. Mẫu nên dùng là:

~~~java
// Tốt khi các task thật sự độc lập:
for (Callable<Integer> task : tasks) {
    futures.add(pool.submit(task));
}
for (Future<Integer> future : futures) {
    consume(future.get());
}
~~~

Đoạn trên là mã minh họa: `tasks`, `futures`, `pool` và `consume` cần được khai báo trong chương trình thực tế.

### Nếu cần xử lý theo thứ tự hoàn thành

`ExecutorCompletionService<T>` bọc một executor và đưa các future đã hoàn thành vào hàng đợi kết quả. `take()` chờ **một kết quả bất kỳ hoàn thành tiếp theo**, thay vì chờ future đầu tiên trong danh sách. Đây vẫn là thao tác chặn caller; hãy để thread điều phối chờ, tránh dùng worker trong chính pool để chờ các task cùng pool.

~~~java
// Đoạn minh họa khi pool và tasks đã được tạo:
java.util.concurrent.ExecutorCompletionService<Integer> completed =
        new java.util.concurrent.ExecutorCompletionService<>(pool);

for (Callable<Integer> task : tasks) {
    completed.submit(task);
}
for (int i = 0; i < tasks.size(); i++) {
    Integer result = completed.take().get();
    // Xử lý theo thứ tự hoàn thành, không giả định thứ tự submit.
}
~~~

## 3. Thread starvation xảy ra như thế nào?

**Thread starvation** là tình trạng một task không có cơ hội tiến triển trong khoảng thời gian đáng kể vì tài nguyên thực thi bị chiếm dụng: chẳng hạn mọi worker đều bận, bị block hoặc các task ưu tiên khác liên tục giành lượt. Một executor có hàng đợi vẫn có thể nhận thêm task trong khi không task nào trong hàng đợi được cấp worker.

Trường hợp đặc biệt là **thread-starvation deadlock**: các worker đang chạy task cha, nhưng task cha lại chờ task con đã được submit vào **chính pool không còn worker rảnh**. Quan hệ chờ tự khóa tiến độ:

~~~text
Pool có 1 worker
Worker-1: chạy Parent
  Parent: submit Child vào chính pool
  Parent: gọi child.get() -> WAITING
Queue: [Child]
Child cần Worker-1 -> Worker-1 lại đang chờ Child
=> Không task nào tự giải phóng được worker.
~~~

Điều này không nhất thiết có hai mutex khóa chéo nhau. Đây là vòng phụ thuộc giữa **lịch cấp phát worker** và **Future completion**. Chỉ tăng kích thước pool từ 1 lên 2 không phải cách sửa bền vững: hai parent cùng chạy có thể đồng thời chờ hai child đang nằm trong hàng đợi.

Ngoài dependency nói trên, starvation cũng có thể do task I/O/blocking kéo dài chiếm hết pool; hàng đợi quá dài so với tốc độ xử lý; hoặc các tác vụ cần lock/semaphore chung. “CPU thấp mà queue đầy” thường là dấu hiệu đáng điều tra, nhưng riêng CPU thấp chưa đủ để kết luận.

## 4. Tái hiện an toàn: parent chờ child trong single-thread executor

Tạo `StarvationDemo.java`:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class StarvationDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        Future<Integer> parent = pool.submit(() -> {
            Future<Integer> child = pool.submit(() -> 42);
            return child.get(); // Worker duy nhất chờ task đang xếp hàng
        });

        try {
            System.out.println(parent.get(200, TimeUnit.MILLISECONDS));
        } catch (TimeoutException expected) {
            System.out.println("Timeout: task con không có worker để chạy");
        } finally {
            parent.cancel(true);  // Interrupt worker đang chờ Future.get()
            pool.shutdownNow();   // Không để chương trình demo treo
        }
    }
}
~~~

Chạy:

~~~bash
javac StarvationDemo.java
java StarvationDemo
~~~

Đầu ra kỳ vọng:

~~~text
Timeout: task con không có worker để chạy
~~~

`parent.get(200, ...)` được gọi ở `main` và giới hạn thời gian **main chờ**. Nó không làm `child` chạy được, cũng không sửa dependency bên trong worker. Trong `finally`, `cancel(true)` yêu cầu interrupt parent đang bị chặn tại `child.get()`; `shutdownNow()` cố dọn các tác vụ còn chờ. Mục tiêu là demo kết thúc được, không phải dùng timeout như giải pháp kiến trúc.

Trong tình huống có hai worker, lỗi tương tự vẫn xuất hiện khi cả hai worker đều chạy parent đang chờ child từ cùng pool. Kết luận cốt lõi: **đừng giữ worker của một executor chỉ để đợi một task đang cần chính worker của executor đó**.

## 5. Cách sửa chính: mô hình hóa dependency thay vì chặn worker

Nếu việc sau chỉ là biến đổi kết quả của việc trước, hãy dùng một pipeline: task tiếp theo chỉ được kích hoạt **sau khi** kết quả trước có sẵn. Với Java SE, `CompletableFuture` hỗ trợ `thenApply`, `thenCompose` và `thenCombine` để biểu diễn quan hệ đó.

Tạo `CompositionDemo.java`:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CompositionDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            CompletableFuture<Integer> result =
                    CompletableFuture.supplyAsync(() -> 21, pool)
                            .thenApply(value -> value * 2);

            System.out.println("Kết quả = " + result.get(2, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac CompositionDemo.java
java CompositionDemo
~~~

Đầu ra xác định:

~~~text
Kết quả = 42
~~~

`thenApply` không tạo ra một task con rồi chặn worker để đợi nó; hàm biến đổi được chạy khi giai đoạn trước hoàn thành. Với biến thể không có hậu tố `Async`, hành động có thể chạy trong thread hoàn thành giai đoạn trước hoặc thread đăng ký continuation nếu giai đoạn đã hoàn tất. Vì vậy **không giả định** mọi continuation có thread riêng.

Chọn phép phối hợp theo ý nghĩa:

| Nhu cầu | Cách biểu diễn |
| --- | --- |
| Biến đổi kết quả A thành B | `future.thenApply(transform)` |
| B là một bước bất đồng bộ trả về `CompletionStage<B>` | `future.thenCompose(startB)`, làm phẳng nested future |
| A và B độc lập, chỉ cần kết hợp khi cả hai xong | `a.thenCombine(b, combine)` |
| Chỉ cần tín hiệu hoàn tất của nhiều future | `CompletableFuture.allOf(...)` |
| Muốn xử lý kết quả của các `Callable` theo lúc hoàn tất | `ExecutorCompletionService` |

Ví dụ minh họa cho một bước phụ thuộc phải thực thi tiếp trong chính executor:

~~~java
// Dùng biến pool đã tồn tại, không gọi get()/join() trong worker.
CompletableFuture<String> chain =
        CompletableFuture.supplyAsync(() -> "java", pool)
                .thenCompose(text ->
                        CompletableFuture.supplyAsync(
                                () -> text.toUpperCase(java.util.Locale.ROOT), pool));
~~~

Ở đây, bước thứ hai **chỉ được submit khi bước thứ nhất đã hoàn thành**; một worker cũng có thể chạy xong chuỗi này vì không có worker nào bị giữ để đợi child. Nếu continuation có blocking I/O, cần cân nhắc executor riêng cho hoạt động blocking; tự động dùng `thenApplyAsync` không bảo đảm hết starvation nếu vẫn trỏ vào cùng pool đang nghẽn.

### Trường hợp không cần tạo child task

Nếu phép tính con ngắn và là phần tự nhiên của công việc cha, thực hiện trực tiếp trong task cha có thể là cách đơn giản nhất. Gửi thêm một task rồi `get` kết quả của nó không đem lại lợi ích nếu parent không có việc độc lập nào để làm trong lúc chờ.

## 6. Phân tách tải CPU và tải blocking

Một task CPU-bound thường chiếm CPU đến khi tính xong. Một task blocking (đọc file/network, đợi khóa, đợi dịch vụ khác) có thể giữ platform thread trong lúc CPU hầu như rảnh. Khi đặt cả hai loại trong pool nhỏ, blocking task có thể chiếm hết worker và trì hoãn công việc tính toán ngắn.

Các nguyên tắc thiết kế:

1. **Pool CPU-bound:** đặt mức đồng thời phù hợp số lõi CPU và phép đo thực tế; không mặc định “càng nhiều thread càng nhanh”.
2. **Pool blocking:** cân nhắc executor riêng và giới hạn tài nguyên phía sau (connection, rate limit, file descriptor); việc tăng thread quá mức có thể chuyển tắc nghẽn sang tài nguyên khác.
3. **Không dùng pool chung cho luồng orchestration phải chờ các task cùng pool:** khi buộc phải chờ, hãy chờ ở thread điều phối nằm ngoài pool phụ thuộc, hoặc thiết kế pipeline bất đồng bộ.
4. **Đo theo workload thực tế:** độ trễ, throughput, số task đang chạy, độ dài queue và thời gian chờ; pool size chỉ là một phần của bài toán.

`Executors.newFixedThreadPool(n)` thường dùng **hàng đợi không giới hạn dung lượng theo cấu hình** (`LinkedBlockingQueue`), nên task có thể tích tụ nhiều khi tốc độ submit vượt tốc độ xử lý; điều này dễ dẫn đến tăng bộ nhớ và độ trễ. `newCachedThreadPool()` có thể tăng số thread mạnh khi nhiều task bị block, cũng không thay thế kiểm soát tải.

### Hàng đợi hữu hạn và xử lý overload

Ví dụ cấu hình `ThreadPoolExecutor` (đoạn cấu hình minh họa, không phải chương trình độc lập):

~~~java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        4, 4,
        0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(100),
        new ThreadPoolExecutor.AbortPolicy()
);
~~~

Hàng đợi chỉ chứa tối đa 100 task **đang chờ**, tách biệt tối đa 4 task đang chạy. Khi pool đang chạy đủ worker và queue đầy, `AbortPolicy` ném `RejectedExecutionException`; caller phải có chính sách cụ thể (giảm tốc độ gửi, báo quá tải, retry có giới hạn hoặc từ chối yêu cầu). Cần `import java.util.concurrent.*;` cho ví dụ này.

`CallerRunsPolicy` có thể tạo backpressure bằng cách cho caller chạy task khi pool quá tải, nhưng nó làm caller bị chặn và có hành vi khác khi executor đã shutdown. Không chọn rejection policy chỉ để che mất dấu hiệu overload; phải hiểu ngữ cảnh caller và điều kiện dừng.

Với dịch vụ ngoài có hạn mức, `Semaphore` hoặc cơ chế giới hạn request có thể cần thiết dù executor còn nhiều worker. **Giới hạn queue và giới hạn số request đồng thời giải quyết hai vấn đề khác nhau.**

## 7. Timeout, cancellation, interrupt và shutdown

Timeout là **ranh giới thời gian chờ**, không phải bằng chứng task đã bị hủy. Đoạn sau chỉ là mẫu điều khiển, giả định `future` đã được khởi tạo:

~~~java
try {
    Result value = future.get(1, TimeUnit.SECONDS);
    use(value);
} catch (TimeoutException timeout) {
    future.cancel(true);
    // Ghi nhận timeout và quyết định phản hồi cho caller.
}
~~~

`Result` và `use` là ký hiệu minh họa. Trong mã thật, cần xử lý `InterruptedException` (thường khôi phục cờ interrupt ở lớp điều phối phù hợp), `ExecutionException` và `CancellationException`; không được nuốt lỗi rồi coi như thành công.

Nếu task đang chặn ở API có thể bị interrupt, `cancel(true)` có thể khiến nó dừng; nếu task phớt lờ interrupt, đang dùng I/O không đáp ứng interrupt hoặc có code CPU chạy liên tục không kiểm tra cờ interrupt, việc hủy không bảo đảm hoàn tất ngay. Task nên có điểm kiểm tra trạng thái, timeout I/O riêng và `finally` để giải phóng tài nguyên.

Khi đóng executor, thông thường: ngừng nhận việc mới qua `shutdown()`, đợi có giới hạn qua `awaitTermination`, rồi mới `shutdownNow()` nếu cần. Không để pool sống vô hạn sau demo/tests; cũng không tắt pool dùng chung tùy tiện từ từng task.

**Một lưu ý quan trọng:** đặt `get(timeout)` ngay trong parent đang chạy ở chính pool nhỏ vẫn giữ worker cho đến timeout. Nó chỉ giới hạn tác hại theo thời gian, không loại bỏ cấu trúc chờ gây starvation.

## 8. Nhận diện và chẩn đoán trong ứng dụng Java

Dấu hiệu nghi vấn có thể gồm:

- Queue tiếp tục tăng hoặc chờ rất lâu; throughput giảm dù vẫn nhận task.
- Các worker của pool đều đang bận nhưng ít task hoàn thành; CPU có thể thấp nếu phần lớn thread đang chờ.
- Thread dump cho thấy nhiều worker ở trạng thái `WAITING`/`TIMED_WAITING` tại `FutureTask.get`, `CompletableFuture.join`, lock hoặc I/O; đồng thời queue chứa công việc mà các worker ấy đang đợi.
- Timeout tăng khi workload tăng; thêm worker chỉ làm hiện tượng xuất hiện muộn hơn.

Quy trình xác minh thay vì đoán:

1. Xác định **executor nào** nhận task cha/con và số worker thực tế; vẽ đồ thị “task A chờ task B” với nơi B được xếp hàng.
2. Lấy thread dump lúc nghẽn (ví dụ `jcmd <pid> Thread.print` với JDK phù hợp) và đối chiếu stack của worker đang chờ.
3. Nếu dùng `ThreadPoolExecutor`, quan sát `getPoolSize()`, `getActiveCount()`, `getQueue().size()` và `getCompletedTaskCount()` theo thời gian; các chỉ số chỉ là ảnh chụp gần đúng, không dùng riêng lẻ làm chứng cứ.
4. Đối chiếu thời gian xử lý CPU/I/O, thời gian đợi lock và độ dài queue; thử với tải có kiểm soát để xem bottleneck nằm ở đâu.
5. Sửa dependency/chia tải/giới hạn queue, rồi đo lại throughput, timeout và tài nguyên. Không dùng tăng pool size làm bằng chứng lỗi đã được xử lý triệt để.

**Phân biệt thuật ngữ:** deadlock khóa truyền thống thường là vòng chờ giữa các lock; thread-starvation deadlock ở đây là vòng phụ thuộc vào worker và task queue. “Starvation” rộng hơn, có thể chỉ là trì hoãn kéo dài do lịch chạy hoặc tranh tài nguyên, chưa chắc có vòng chờ tuyệt đối.

## 9. Tự kiểm thử những tình huống dễ bỏ sót

Các test có giá trị nên ép tiến độ bằng primitive đồng bộ có thời hạn (chẳng hạn `CountDownLatch`), tránh dựa vào `Thread.sleep` để “đoán” thứ tự. Những trường hợp nên tái hiện:

| Kịch bản | Điều cần kiểm tra |
| --- | --- |
| Ba task độc lập; pool đủ worker | Tất cả được submit trước khi caller chờ; kết quả không phụ thuộc thứ tự chạy. |
| Pool 1 worker, parent submit child rồi `get` | Phát hiện timeout, tránh để test treo và cleanup pool sau test. |
| Hai worker, hai parent cùng chờ hai child trong cùng pool | Hiểu rằng tăng từ 1 lên 2 worker không loại bỏ vấn đề kiến trúc. |
| Một task lỗi hoặc chạy lâu | Caller nhận đúng `ExecutionException`/`TimeoutException`, có chính sách hủy rõ ràng. |
| Queue đầy | Rejection được xử lý dự đoán được; không mất task âm thầm. |
| Task bị cancel/interrupt | Tài nguyên được đóng; task có thể phản hồi interrupt hoặc timeout I/O theo thiết kế. |

Khi viết test, **không dựa vào thứ tự thread chạy** hoặc thời lượng máy nhanh/chậm để xác nhận correctness. Kiểm tra kết quả, trạng thái timeout và quan hệ chờ có thể quan sát được. Một test có timeout ở tầng test runner vẫn cần cleanup executor trong `finally` để không làm kẹt cả bộ test.

## 10. Nguyên tắc quyết định nhanh

- **Task thật sự độc lập:** submit tất cả, rồi tập hợp kết quả ở caller; dùng `CompletionService` nếu cần xử lý theo thứ tự hoàn thành.
- **Task B phụ thuộc A:** dùng phép biến đổi/chaining như `thenApply` hoặc `thenCompose`; đừng chiếm worker của A để đợi B được xếp hàng trên cùng pool.
- **Task bị block vì I/O:** tách loại tải và đo giới hạn tài nguyên; cân nhắc executor phù hợp và timeout I/O thực.
- **Hàng đợi tăng mãi:** giới hạn queue, đưa ra backpressure/rejection policy và kiểm soát tốc độ producer.
- **Pool nhỏ thường “đứng im”:** kiểm tra thread dump, chuỗi `Future.get()/join()` và xem liệu công việc đang chờ có cần chính các worker bị giữ hay không.
- **Java 21+ có virtual threads:** `Executors.newVirtualThreadPerTaskExecutor()` hữu ích cho nhiều tác vụ blocking kiểu thread-per-task, nhưng không tự giới hạn tải lên dịch vụ ngoài, không chữa vòng phụ thuộc logic và không thay thế timeout/cancellation. Ví dụ chính của bài dùng API chạy được trên Java 17+.

Điểm cốt lõi: **gửi công việc độc lập để executor có cơ hội chạy chúng; biểu diễn công việc phụ thuộc thành chuỗi phối hợp; đừng để toàn bộ worker bị khóa trong lúc chờ chính hàng đợi mà chúng phải phục vụ.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-011">Khả năng quan sát active count, queue size, completed tasks</a>

<details>
<summary>Xem chi tiết</summary>

# Khả năng quan sát active count, queue size, completed tasks trong Java Executor Framework

## 1. Tại sao cần quan sát thread pool?

Khi sử dụng `ExecutorService`, chương trình giao task cho executor thay vì tự tạo từng `Thread`. Nhưng một hệ thống đang chạy cần biết: có bao nhiêu worker đang thực hiện task, bao nhiêu task đang đợi, bao nhiêu task đã kết thúc, pool đã chạm giới hạn hay chưa, và việc xử lý có đang chậm dần?

Với `java.util.concurrent.ThreadPoolExecutor`, ba phép quan sát thường gặp là:

- **`getActiveCount()`:** ước lượng số thread trong pool hiện đang **thực thi task**.
- **`getQueue().size()`:** số phần tử hiện có trong **work queue** của pool, thường là task đang chờ worker lấy ra, **không gồm task đang chạy**.
- **`getCompletedTaskCount()`:** ước lượng tổng số task đã **hoàn tất thực thi trong các worker của pool**, tính từ khi executor được tạo.

Những chỉ số này giúp phát hiện backlog, theo dõi tải và hỗ trợ điều chỉnh dung lượng. Tuy nhiên, chúng không phải một **snapshot nguyên tử của toàn bộ executor**, không phải kết quả nghiệp vụ “thành công” và **không thể tự dùng để quyết định chắc chắn** liệu một lần `execute()`/`submit()` tiếp theo có được chấp nhận hay không.

Bài học này thuộc chủ đề **Executor Framework** của Java concurrency nói chung, minh họa bằng Java 17+ và API JDK, không gắn với framework hoặc cấu hình ứng dụng cụ thể.

## 2. Nắm vòng đời task để đọc đúng số liệu

Một task trong `ThreadPoolExecutor` có thể đi qua các giai đoạn:

```text
Ứng dụng gửi task
       |
       v
Executor quyết định
       |
       +--> Worker nhận và thực thi ngay
       |
       +--> Work queue chờ worker trống
       |
       +--> Rejection handler (khi không thể nhận theo chính sách)
                    |
                    +--> Ném ngoại lệ / bỏ task / chạy ở caller / cách khác
                            
Task chạy trong worker --> Kết thúc run/call --> Completed-task accounting
```

**Không đồng nhất ba số đo:**

| Chỉ số | Đo điều gì | Không đo điều gì |
| --- | --- | --- |
| `getActiveCount()` | Ước lượng worker đang thực thi task | Không nhất thiết là số thread đang sử dụng CPU; task chờ I/O vẫn có thể được tính là active |
| `getQueue().size()` | Số task còn nằm trong work queue tại thời điểm lấy mẫu | Không bao gồm task đã được worker lấy khỏi queue; không phải độ trễ chờ |
| `getCompletedTaskCount()` | Ước lượng số task đã hoàn tất xử lý trong pool | Không cho biết bao nhiêu task thành công, bao nhiêu thất bại hay bao nhiêu kết quả đã được caller nhận |
| `getPoolSize()` | Số worker thread hiện có trong pool | Không phải số worker thực sự active |
| `getLargestPoolSize()` | Số worker lớn nhất từng tồn tại đồng thời trong pool | Không phải ngưỡng cấu hình tối đa hoặc mức tải hiện tại |
| `getTaskCount()` | Ước lượng số task từng được lập lịch cho pool | Không phải số request người dùng hoàn thành hoặc số task thực sự thành công |

Đặc biệt, `getCompletedTaskCount()` không nên đặt tên dashboard là “successful jobs”. Một task có thể kết thúc bằng exception, bị hủy, hoặc hoàn tất execution nhưng nghiệp vụ thất bại. `submit()` thường bọc task trong `FutureTask`: exception được lưu trong `Future` để caller gọi `get()` nhận `ExecutionException`; task vẫn có thể được tính đã hoàn tất bởi executor.

### “Active” có tính cả worker đang đợi bên trong task không?

Có thể có. Nếu worker **đang chạy `Runnable.run()`** nhưng bị chặn trong `queue.take()` khác, truy vấn I/O hoặc chờ lock, nó vẫn đang thực thi task của executor ở nghĩa quản lý worker. `getActiveCount()` không phải CPU utilization. Thread của pool **rảnh chờ lấy task kế tiếp** thì không tính active theo ý nghĩa của metric này.

Do đó nên đối chiếu active count với queue length, thời gian chạy task, độ trễ queue và sử dụng CPU, thay vì chỉ nhìn một số.

## 3. Đọc số liệu từ đâu?

`ExecutorService` là interface tổng quát, **không có** các phương thức `getActiveCount()`, `getCompletedTaskCount()` hay `getQueue()`. Những phép quan sát này là API của **`ThreadPoolExecutor`**.

Khởi tạo trực tiếp một pool cấu hình rõ ràng:

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        2,                         // corePoolSize
        4,                         // maximumPoolSize
        30, TimeUnit.SECONDS,      // keepAliveTime
        new ArrayBlockingQueue<>(20)
);

int active = executor.getActiveCount();
int queued = executor.getQueue().size();
long completed = executor.getCompletedTaskCount();
```

Đây là **snippet**, cần import `ThreadPoolExecutor`, `ArrayBlockingQueue` và `TimeUnit`, và phải shutdown executor khi xong. Không ép một `ExecutorService` bất kỳ thành `ThreadPoolExecutor` nếu không biết implementation: nó có thể là wrapper, một executor khác hoặc implementation không hỗ trợ các metric trên.

`Executors.newFixedThreadPool(n)` trong JDK thường tạo `ThreadPoolExecutor` phía dưới, nhưng API khai báo trả `ExecutorService`; nếu code cần metric triển khai cụ thể, tốt nhất **nắm trực tiếp tham chiếu `ThreadPoolExecutor` lúc khởi tạo** hoặc cung cấp một interface monitoring riêng, thay vì downcast tùy tiện.

`Executors.newWorkStealingPool()` dựa trên mô hình khác; `ForkJoinPool` có tập metric riêng như `getActiveThreadCount()`, `getQueuedTaskCount()`. Không áp nguyên xi cách hiểu queue và active của `ThreadPoolExecutor` cho mọi executor.

## 4. Java 17+: ví dụ quan sát pool với lịch chạy được kiểm soát

Để hiểu các số đo, ta tạo một pool **2 worker** và work queue chứa tối đa **3 task**. Năm task cùng có cổng chờ. Hai worker vào cổng, ba task còn lại nằm trong queue; main lấy số liệu rồi mới mở cổng cho chúng chạy hết.

Tạo file `ThreadPoolMetricsDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolMetricsDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(3)
        );

        CountDownLatch twoStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        Runnable task = () -> {
            twoStarted.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            for (int i = 0; i < 5; i++) {
                pool.execute(task);
            }

            twoStarted.await();

            System.out.println("Submitted: 5");
            System.out.println("Queue size before release: "
                    + pool.getQueue().size());
            System.out.println("Active estimate before release: "
                    + pool.getActiveCount());
            System.out.println("Completed estimate before release: "
                    + pool.getCompletedTaskCount());
        } finally {
            release.countDown();
            pool.shutdown();
        }

        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Pool did not terminate");
        }

        System.out.println("Final queue size: "
                + pool.getQueue().size());
        System.out.println("Final active estimate: "
                + pool.getActiveCount());
        System.out.println("Final completed estimate: "
                + pool.getCompletedTaskCount());
    }
}
```

Biên dịch và chạy:

```bash
javac ThreadPoolMetricsDemo.java
java ThreadPoolMetricsDemo
```

Đầu ra minh họa trong điều kiện thực thi thông thường:

```text
Submitted: 5
Queue size before release: 3
Active estimate before release: 2
Completed estimate before release: 0
Final queue size: 0
Final active estimate: 0
Final completed estimate: 5
```

**Phân tích và giới hạn bảo đảm:**

1. Pool có `corePoolSize = maximumPoolSize = 2`. Hai task đầu tiên được worker thực thi và chờ `release`; ba task còn lại có thể nằm trong queue dung lượng 3. Không có task thứ sáu nên không cần rejection.
2. `twoStarted.await()` xác nhận đã có **hai task bước vào `run()`**; vì cổng chưa mở, hai worker này chưa thể kết thúc bình thường. Ba task còn lại ở queue. Đây là lý do snapshot trước khi mở cổng thường có active ≈ 2, queued = 3, completed ≈ 0.
3. `getActiveCount()` và `getCompletedTaskCount()` được tài liệu hóa là **giá trị ước lượng**. Không dùng output mẫu này để tạo assertion rằng mọi implementation và mọi lần lấy mẫu đồng thời phải cho số chính xác tuyệt đối.
4. Khi `release.countDown()`, hai worker hoàn thành task đang chạy rồi lấy các task còn chờ. `shutdown()` từ chối task mới nhưng **tiếp tục chạy task đã nhận** theo cơ chế shutdown bình thường.
5. Sau `awaitTermination()` trả true, mọi worker đã dừng, queue được xử lý xong trong ví dụ. Vì năm task đều hoàn tất bình thường, snapshot cuối thông thường là active 0, queue 0, completed 5.
6. `finally` bảo đảm mở cổng và bắt đầu shutdown ngay cả khi main bị interrupt khi chờ cổng, tránh cố ý để worker mắc kẹt vì code đo metric.

Mấu chốt của ví dụ là **tách thời điểm lấy mẫu trước và sau khi task hoàn tất**. Trong ứng dụng thật, việc gọi ba getter không chụp một transaction thống nhất; những giá trị có thể đến từ các thời điểm hơi khác nhau khi pool đang thay đổi.

## 5. Độ tin cậy: các metric của ThreadPoolExecutor là snapshot/ước lượng

Javadoc mô tả `getActiveCount()`, `getCompletedTaskCount()` và `getTaskCount()` theo ngữ nghĩa **approximate**. `getQueue().size()` phản ánh số phần tử đang thấy trong queue tại thời điểm đọc, nhưng task có thể được lấy ra hoặc thêm vào ngay sau đó; cách `size()` hoạt động còn phụ thuộc implementation của queue.

Điều này dẫn đến ba quy tắc:

- **Không lấy snapshot để làm invariant concurrency:** ví dụ `if (pool.getQueue().size() < 100) pool.execute(task)` vẫn có race giữa lúc đọc size và lúc submit. Các thread khác có thể làm queue đầy ở khoảng giữa.
- **Không suy ra kết quả chính xác bằng cộng/trừ các metric đo khác lúc:** `getTaskCount() - getCompletedTaskCount() - getActiveCount()` không được đảm bảo bằng `queue.size()` do sampling không nguyên tử và cách đếm cụ thể.
- **Không chờ trạng thái bằng polling quá sát nếu API đã có chờ đúng:** thay vì lặp `while (getActiveCount() > 0)`, dùng `shutdown()` + `awaitTermination(timeout, unit)` để đợi pool kết thúc; thay vì tự chờ completed tăng, dùng `Future.get()`, `CountDownLatch` hoặc `invokeAll()` khi phù hợp.

Các metric vẫn rất hữu ích cho dashboard, xu hướng, SLO và phát hiện bất thường. “Ước lượng” không phải “vô dụng”; nghĩa là chúng thích hợp để **quan sát**, không để đồng bộ các luồng hoặc quyết định nhận task một cách atomic.

### Metric nào phản ánh “đang nghẽn”?

Ví dụ:

- **Active gần mức số worker hiện tại, queue tăng liên tục:** tốc độ nhận task có thể cao hơn năng lực xử lý, hoặc nhiều task bị kẹt trong I/O/lock.
- **Active thấp, queue gần như trống:** pool có thể đang rảnh, nhưng cũng có thể upstream không gửi được việc; cần đối chiếu lượng yêu cầu tới.
- **Completed tăng chậm, queue tăng:** có thể throughput xử lý suy giảm. Nếu active vẫn cao, xem thời gian chạy task, lock contention, downstream I/O.
- **Queue luôn trống nhưng latency cao:** task có thể đang bị chặn ở bước ngoài executor, trong bản thân task hoặc tại chỗ nhận yêu cầu; chỉ số queue không bao phủ cả pipeline.

Không kết luận “deadlock” hoặc “thiếu thread” chỉ từ một snapshot. Dùng thêm thread dump, JFR, thời gian chạy task, loại queue và trạng thái dependency.

## 6. Hiểu đúng queue size: bounded, unbounded và handoff

`ThreadPoolExecutor` có thể dùng các loại work queue khác nhau; ngữ nghĩa `size()` thay đổi theo loại:

| Loại queue | Đặc điểm | Cách diễn giải |
| --- | --- | --- |
| `ArrayBlockingQueue<>(capacity)` | Dung lượng cố định, phần tử được lưu trong queue | `size()` là backlog đang lưu, có thể đối chiếu với capacity |
| `LinkedBlockingQueue<>(capacity)` | Có giới hạn nếu truyền capacity rõ ràng | Theo dõi backlog, lưu ý cost/memory |
| `LinkedBlockingQueue<>()` mặc định | Dung lượng rất lớn (gần như unbounded theo mục đích sử dụng) | Queue có thể tăng đáng kể và tiêu tốn bộ nhớ trước khi có rejection do đầy |
| `SynchronousQueue` | Handoff trực tiếp, **không có dung lượng lưu phần tử** | `size()` thường luôn 0, **không đồng nghĩa không có áp lực tải** |
| `PriorityBlockingQueue` | Sắp xếp theo độ ưu tiên, không giới hạn cố định theo cách mặc định | `size()` không cho biết mức ưu tiên, tuổi task hoặc deadline |
| Queue của `ScheduledThreadPoolExecutor` | Có thể chứa task trì hoãn/chưa tới hạn và task định kỳ | `size()` không đơn giản là số request thường đang chờ worker |

### Lưu ý khi pool có hàng đợi rất lớn

Trong `ThreadPoolExecutor`, sau khi đạt `corePoolSize`, nếu queue vẫn nhận được task thì executor có thể **ưu tiên đưa task vào queue** thay vì tạo thêm worker cho tới `maximumPoolSize`. Với `LinkedBlockingQueue` không giới hạn thực tế, `maximumPoolSize` thường **không phát huy** như người mới nghĩ. Queue tăng là cảnh báo về backlog và nguy cơ bộ nhớ, không nhất thiết phản ánh pool đã mở rộng tối đa worker.

Với `SynchronousQueue`, vì không lưu task trong queue, `getQueue().size()` không mô tả backlog. Khi không có worker để nhận handoff và không thể thêm worker, submission có thể bị từ chối theo chính sách rejection. Cần quan sát cả rejection, latency và nguồn yêu cầu.

### Không tùy tiện sửa `getQueue()`

`getQueue()` trả về queue mà executor đang dùng. Javadoc lưu ý truy cập này chủ yếu phục vụ **monitoring và debugging**; sử dụng nó để thêm/xóa phần tử tùy tiện sẽ can thiệp cơ chế quản lý task của executor. Đừng gọi `pool.getQueue().clear()` như cách “xả tải” hoặc `queue.add(runnable)` để thay `execute()`.

Nếu cần chủ động loại một task đã được xếp hàng, xem các API được hỗ trợ như `remove(Runnable)`/`purge()` theo đúng hợp đồng, hoặc quản lý cancellation và ownership ở lớp bao quanh. Việc hủy task chưa chắc đồng nghĩa queue giảm ngay tức thì với mọi executor.

## 7. Completed task khác “successful task” như thế nào?

`getCompletedTaskCount()` theo dõi task **đã hoàn tất việc thực thi trong pool**, không phải giá trị nghiệp vụ thành công. Một `Runnable` có thể ném exception; một `Callable` được `submit()` có thể kết thúc bằng lỗi; một task có thể bị cancel hoặc kết thúc sớm.

### Java 17+: một task thất bại vẫn được tính completed

Tạo file `CompletedIsNotSuccess.java`:

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;

public class CompletedIsNotSuccess {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
        );

        boolean businessSuccess = true;
        try {
            Future<?> result = pool.submit(() -> {
                throw new IllegalStateException("business failed");
            });

            try {
                result.get();
            } catch (ExecutionException e) {
                businessSuccess = false;
                System.out.println("Failure observed via Future: "
                        + e.getCause().getClass().getSimpleName());
            }
        } finally {
            pool.shutdown();
        }

        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Pool did not terminate");
        }

        System.out.println("Business success: " + businessSuccess);
        System.out.println("Completed tasks: "
                + pool.getCompletedTaskCount());
    }
}
```

Biên dịch và chạy:

```bash
javac CompletedIsNotSuccess.java
java CompletedIsNotSuccess
```

Đầu ra:

```text
Failure observed via Future: IllegalStateException
Business success: false
Completed tasks: 1
```

Lời gọi `submit` bọc công việc trong `Future`. `result.get()` ném `ExecutionException` với nguyên nhân là lỗi nghiệp vụ; executor vẫn đã thực thi và kết thúc một task nên completed count tăng. Nếu chỉ có completed count mà không xem kết quả `Future` hoặc metric riêng, dashboard có thể hiển thị “1 completed” và **không hề cho biết 1 thất bại**.

### Mối quan hệ giữa `execute()`, `submit()` và ngoại lệ

- `execute(Runnable)` thường chuyển `Runnable` trực tiếp cho worker. Một exception không bắt có thể đi qua cơ chế `afterExecute(Runnable, Throwable)` dưới dạng `Throwable` và làm worker kết thúc bất thường.
- `submit(Runnable/Callable)` thường sử dụng `FutureTask`, giữ exception **bên trong Future** để `get()` báo `ExecutionException`. Trong hook `afterExecute`, đối số `Throwable` có thể là null **dù tác vụ thất bại**.
- `Future.cancel(...)` ảnh hưởng trạng thái hủy của Future, nhưng không phải mọi cancellation đều đồng nghĩa worker đã kết thúc tức thì. Không dùng completed count để suy ra cancellation đã hoàn tất hoặc side effect đã rollback.

Muốn đếm thành công/thất bại, hãy xác định nghĩa **thành công của nghiệp vụ** rồi đo trực tiếp, ví dụ wrapper `Callable` ghi nhận outcome sau khi tác vụ trả kết quả hoặc ném exception. Không gán nhãn business success cho metric quản trị worker của `ThreadPoolExecutor`.

## 8. Rejection policy làm metric “submitted” và “completed” lệch nhau

Khi pool và queue không nhận thêm task, `RejectedExecutionHandler` quyết định cách xử lý. Một số policy chuẩn:

- `AbortPolicy`: ném `RejectedExecutionException` cho caller.
- `CallerRunsPolicy`: nếu executor chưa shutdown, chạy task **ngay trên thread gọi `execute`**, tạo backpressure; nếu shutdown có thể bỏ task.
- `DiscardPolicy`: bỏ task mà không báo lỗi.
- `DiscardOldestPolicy`: loại task cũ nhất đang chờ rồi thử submit lại; cần đặc biệt thận trọng với deadline/priority/retry.

**Hệ quả:** một task có thể được thực thi ở **caller thread** chứ không phải worker của pool. Task đó không phải hoàn tất “bởi pool worker”, nên không nên mong `getCompletedTaskCount()` phản ánh mọi công việc được xử lý bởi cả caller và worker.

### Java 17+: CallerRunsPolicy làm chênh lệch bộ đếm

Tạo file `CallerRunsMetrics.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CallerRunsMetrics {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger callerExecuted = new AtomicInteger();
        Thread submitter = Thread.currentThread();

        try {
            pool.execute(() -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            entered.await();           // Worker số 1 đang giữ slot chạy.
            pool.execute(() -> { });   // Task số 2 nằm trong queue đầy.
            pool.execute(() -> {       // Task số 3 chạy trong caller.
                if (Thread.currentThread() == submitter) {
                    callerExecuted.incrementAndGet();
                }
            });

            System.out.println("Executed in caller: "
                    + callerExecuted.get());
        } finally {
            release.countDown();
            pool.shutdown();
        }

        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Pool did not terminate");
        }

        System.out.println("Completed by pool workers: "
                + pool.getCompletedTaskCount());
    }
}
```

Chạy:

```bash
javac CallerRunsMetrics.java
java CallerRunsMetrics
```

Đầu ra:

```text
Executed in caller: 1
Completed by pool workers: 2
```

Vì pool chỉ có một worker và queue chứa một phần tử, khi task thứ ba được gửi, cả worker và queue đều bận. `CallerRunsPolicy` thực thi task này ngay trên caller. Hai task còn lại chạy trong worker và hoàn tất sau khi `release` mở. Từ góc nhìn nghiệp vụ, **ba task đã được thực thi**; từ metric của pool, chỉ **hai task đã hoàn tất bởi worker**.

**Điểm cần đưa lên dashboard:** số yêu cầu **được gửi**, **được chấp nhận**, **chạy trong caller**, **bị từ chối/bỏ**, **thất bại nghiệp vụ** và **hoàn tất nghiệp vụ** có thể khác nhau. Hãy có các counter riêng nếu yêu cầu đo toàn bộ pipeline, không suy hết từ completed count.

## 9. Thiết kế một dashboard quan sát pool thực tế

Một bảng theo dõi hữu ích thường có hai nhóm:

### A. Các metric trạng thái của executor (gauge/snapshot)

| Metric | Loại | Câu hỏi trả lời |
| --- | --- | --- |
| `getActiveCount()` | Gauge ước lượng | Bao nhiêu worker hiện đang bận thực thi task? |
| `getPoolSize()` | Gauge | Pool hiện có bao nhiêu worker? |
| `getCorePoolSize()`/`getMaximumPoolSize()` | Cấu hình | Ngưỡng thiết kế là gì? |
| `getQueue().size()` | Gauge | Bao nhiêu phần tử còn đang chờ trong queue? |
| Queue remaining capacity, nếu queue hỗ trợ | Gauge | Còn bao nhiêu slot trong queue bounded? |
| `isShutdown()`, `isTerminating()`, `isTerminated()` | Trạng thái lifecycle | Pool đang nhận việc hay đang ngừng? |

Gauge thay đổi theo thời gian. Lấy mẫu quá thưa có thể bỏ lỡ spike ngắn; lấy mẫu quá dày có overhead và cũng không làm dữ liệu trở nên nguyên tử.

### B. Các metric tích lũy hoặc nghiệp vụ (counter/histogram)

| Metric | Nguồn | Tại sao cần |
| --- | --- | --- |
| `getCompletedTaskCount()` | Executor, **ước lượng tích lũy** | Xu hướng throughput thực thi của worker |
| `getTaskCount()` | Executor, **ước lượng tích lũy** | Tổng task được pool quản lý theo cách đếm của implementation |
| Submitted/accepted/rejected | Instrumentation tại điểm submit/rejection | Phát hiện quá tải và policy |
| Caller-runs count | Instrumentation ở `CallerRunsPolicy` hoặc wrapper | Thấy backpressure làm caller mất thời gian |
| Business success/failure/cancel | Wrapper task hoặc kết quả Future | Phân biệt outcome nghiệp vụ với “đã thực thi” |
| Queue wait duration | Thời điểm enqueue và bắt đầu chạy | Phát hiện backlog qua latency chứ không chỉ size |
| Execution duration | Thời điểm bắt đầu và kết thúc task | Xác định task chậm, I/O và contention |
| End-to-end latency | Tại ranh giới request/workflow | Thấy cả chờ queue, chạy task, retry và phản hồi |

**Công thức ước lượng throughput** trong một cửa sổ đo:

```text
completed_rate_per_second
    = (completed_at_t2 - completed_at_t1) / elapsed_seconds
```

Dùng thời gian trôi qua từ đồng hồ đơn điệu (`System.nanoTime()`) để đo khoảng lấy mẫu, không lấy wall-clock để tính elapsed nếu không cần. Vì completed count là estimate và snapshot không nguyên tử, tốc độ tính bằng hiệu số nên được xem là **ước lượng**, đặc biệt với khoảng lấy mẫu ngắn hoặc pool thay đổi/shutdown.

Không so sánh trực tiếp counter từ **hai instance executor khác nhau** hoặc sau khi pool đã được tạo lại: bộ đếm khởi động lại từ 0 nên delta có thể sai nếu không nhận biết vòng đời.

## 10. Lấy mẫu định kỳ mà không cản trở worker

Một hướng triển khai là dùng một scheduler/monitor **tách khỏi pool làm việc**, đọc metrics định kỳ rồi xuất ra log hoặc hệ thống metrics. Không để chính pool đang bão hòa làm nhiệm vụ duy nhất đo trạng thái của nó, vì khi hàng đợi đầy, task monitor cũng có thể bị chậm.

Ví dụ cấu trúc:

```java
// Snippet minh họa: pool là ThreadPoolExecutor, sampler
// là ScheduledExecutorService đã khởi tạo.
ScheduledFuture<?> monitor = sampler.scheduleAtFixedRate(
        () -> {
            int active = pool.getActiveCount();
            int queued = pool.getQueue().size();
            long completed = pool.getCompletedTaskCount();

            recordMetrics(active, queued, completed);
        },
        0, 1, TimeUnit.SECONDS
);

// Khi dừng monitoring:
monitor.cancel(false);
sampler.shutdown();
```

Đây là **snippet**, không phải file Java độc lập: `recordMetrics` và các biến được giả định đã định nghĩa. Đừng làm lệnh ghi log/network chậm hoặc bị chặn vô hạn trong callback lấy mẫu. Nếu một lượt sampler chạy quá lâu hoặc ném exception không được xử lý, lịch lấy mẫu có thể bị ảnh hưởng. `scheduleAtFixedRate` không bảo đảm lấy mẫu đúng mỗi giây dưới mọi lịch chạy.

Một bản monitoring production nên:

1. Gắn nhãn pool theo chức năng (ví dụ nhóm IO, CPU, background), không nhúng thông tin cá nhân vào metric tags.
2. Ghi trạng thái active, pool size, queue length, completed, rejected và latency cùng timestamp.
3. Tránh tag theo task ID/request ID có cardinality cực lớn.
4. Xử lý lỗi exporter/metrics backend độc lập với việc chạy task.
5. Khi pool đang shutdown, tiếp tục lấy mẫu đủ để thấy drain, rồi dừng sampler đúng vòng đời.
6. Cảnh báo theo **xu hướng có thời gian duy trì**, không theo một snapshot nhiễu duy nhất.

### Hook `beforeExecute`/`afterExecute`

Có thể subclass `ThreadPoolExecutor` và override `beforeExecute(Thread,Runnable)`/`afterExecute(Runnable,Throwable)` để đo vòng đời worker. Nhưng hook này chạy trên đường thực thi task, nên code đo cần nhẹ, thread-safe và không làm hỏng logic của executor.

- `beforeExecute` có thể ghi thời điểm bắt đầu, nhưng nếu dùng shared map phải quản lý ID và cleanup.
- `afterExecute` được gọi khi `run()` của task kết thúc trong worker. `Throwable` có thể null với `FutureTask` ngay cả khi user code lỗi: cần xem kết quả `Future` hoặc instrument trực tiếp wrapper nếu muốn success/failure chính xác.
- Nếu hook tự ném exception, worker có thể kết thúc bất thường và việc xử lý bị ảnh hưởng.
- `CallerRunsPolicy` có thể thực thi task ở caller mà **không qua worker hooks**, nên metrics dựa riêng trên hook cũng không bao quát tất cả task thực thi.

Với nhu cầu thông thường, bọc `Runnable`/`Callable` ngay ở biên submit để đo thời gian, status và error rõ ràng hơn là cố diễn giải completed count như business success.

## 11. Mẫu instrument outcome tại ranh giới task

Dưới đây là một ví dụ **chạy độc lập** để theo dõi thành công/thất bại nghiệp vụ bằng counter riêng, đồng thời đối chiếu với completed count. Tạo file `ExecutorOutcomeDemo.java`:

```java
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.LongAdder;

public class ExecutorOutcomeDemo {
    private static final LongAdder succeeded = new LongAdder();
    private static final LongAdder failed = new LongAdder();

    static Runnable tracked(Runnable work) {
        return () -> {
            try {
                work.run();
                succeeded.increment();
            } catch (RuntimeException | Error e) {
                failed.increment();
                throw e;
            }
        };
    }

    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
        );

        try {
            Future<?> ok = pool.submit(tracked(() -> { }));
            Future<?> bad = pool.submit(tracked(() -> {
                throw new IllegalArgumentException("invalid input");
            }));

            for (Future<?> future : new Future<?>[] {ok, bad}) {
                try {
                    future.get();
                } catch (ExecutionException ignored) {
                    // Lỗi đã được đếm trong wrapper; caller biết task lỗi.
                }
            }
        } catch (ExecutionException e) {
            throw new AssertionError(e);
        } finally {
            pool.shutdown();
        }

        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Pool did not terminate");
        }

        System.out.println("Succeeded: " + succeeded.sum());
        System.out.println("Failed: " + failed.sum());
        System.out.println("Completed by pool: "
                + pool.getCompletedTaskCount());
    }
}
```

Chạy:

```bash
javac ExecutorOutcomeDemo.java
java ExecutorOutcomeDemo
```

Kết quả mong đợi:

```text
Succeeded: 1
Failed: 1
Completed by pool: 2
```

**Lưu ý về ví dụ:** `LongAdder.sum()` rất phù hợp để quan sát counter khi nhiều thread cập nhật, nhưng trong lúc cập nhật đồng thời, kết quả tổng không phải một snapshot nguyên tử. Ở đây ta gọi `sum()` **sau khi pool đã kết thúc**, nên kết quả cuối ổn định. Trong ứng dụng cần theo dõi nhiều loại lỗi, nên phân nhóm exception ở lớp nghiệp vụ thay vì chỉ phân biệt success/failure bằng một `RuntimeException`.

*Chi tiết về checked exception:* `Runnable.run()` không khai báo checked exception. Với `Callable` có checked exception, wrapper cần khai báo/propagate thích hợp và đếm lỗi tương ứng. Trong ví dụ trên, các task chỉ ném unchecked exception.

## 12. Khi nào méo số liệu hoặc thiếu số liệu?

### Task bị cancel trước khi worker chạy

Nếu một `Future` bị hủy lúc đang trong queue, wrapper của user code có thể chưa chạy lần nào. `FutureTask` hoặc cấu trúc queue có thể vẫn hiện diện cho tới lúc được dọn/worker lấy ra theo policy. `getQueue().size()` và completed count **không** tự cung cấp số task đã hủy trước khi chạy.

### Task chạy lâu hoặc bị treo

`getActiveCount()` có thể cao liên tục, trong khi completed count gần như không tăng. Đây có thể là task dài hợp lệ hoặc đang chặn I/O/lock. Đo execution duration và xem thread dump/JFR; không suy ra luôn deadlock.

### Rejected task

Task bị `AbortPolicy` từ chối có thể chưa từng vào queue hoặc worker. Nó không có nghĩa completed count phải tăng. Cần counter riêng ngay ở ranh giới submit và rejection handler, cùng chính sách retry.

### Task chạy trong caller

`CallerRunsPolicy` có thể làm throughput bị chi phối bởi thời gian caller bị chặn; metric active của pool không phản ánh caller đang chạy task. Theo dõi cả caller-run count và submit latency.

### Periodic task

`ScheduledThreadPoolExecutor` có task định kỳ có thể chạy nhiều lần từ cùng một đăng ký, và queue có thể chứa task trì hoãn. Không hiểu `getCompletedTaskCount()` của periodic executor như số bản ghi nghiệp vụ đã xử lý, cũng không so sánh trực tiếp queue size với pool thông thường mà không xét scheduler semantics.

### Pool bị shutdown

`shutdown()` cho phép hoàn tất các task đã được nhận theo cách bình thường nhưng không nhận task mới. `shutdownNow()` cố ngắt task đang chạy và trả danh sách task chờ chưa bắt đầu, **không bảo đảm** thread/task đang chạy dừng ngay. Sau shutdown, metric active/queue/completed vẫn có thể thay đổi trong giai đoạn drain. Dùng `awaitTermination()` để chờ pool kết thúc theo thời hạn thay vì chỉ nhìn active count.

## 13. Dùng metric để điều chỉnh pool: điều nên và không nên làm

**Nên làm:**

- Theo dõi active, queue, task latency, rejection và throughput trên cùng trục thời gian.
- Sử dụng bounded queue khi cần giới hạn backlog và memory; tính cả dung lượng worker và chiến lược rejection.
- Phân biệt tác vụ CPU-bound, I/O-bound và tác vụ chờ resource; cấu hình pool theo workload.
- Cảnh báo khi queue tăng lâu, thời gian chờ tăng hoặc rejection xuất hiện liên tục.
- So sánh kết quả trước/sau khi đổi `corePoolSize`, `maximumPoolSize`, queue và rejection policy qua benchmark/tải thật.

**Không nên làm:**

- Không dùng `if (getActiveCount() < max) execute(...)` để điều phối nguyên tử; trạng thái đổi ngay sau lúc đọc.
- Không dùng `getQueue().size() == 0` để kết luận tất cả task đã xong; worker có thể vẫn đang chạy.
- Không dùng `completed == submitted` nếu có rejection, task chạy ở caller, cancellation hoặc task được gửi trong nhiều thời điểm.
- Không dùng `getLargestPoolSize()` như số worker active hiện tại.
- Không nhầm `getPoolSize()` với `getActiveCount()`; worker idle vẫn nằm trong pool.
- Không gọi `getQueue().clear()` hay tự enqueue để “tối ưu” executor dựa trên một snapshot.
- Không cho metrics exporter blocking I/O làm giảm throughput của chính pool.
- Không dùng một điểm đo duy nhất để kết luận tối ưu số thread; cần xem CPU, I/O, độ dài task, memory, latency và contention.

## 14. Checklist triển khai khả năng quan sát Executor Framework

1. **Xác nhận executor implementation:** có thật là `ThreadPoolExecutor` không, hay một `ExecutorService` wrapper/`ForkJoinPool`/scheduled executor?
2. **Định nghĩa ý nghĩa metric:** active = worker đang thực thi task, queued = phần tử đang chờ, completed = task đã kết thúc thực thi trong pool, không phải business success.
3. **Phân biệt gauge và counter:** active/queue là snapshot; completed/task count là số liệu tích lũy ước lượng.
4. **Ghi rõ queue type/capacity:** queue bounded, unbounded, handoff hay delayed sẽ có ý nghĩa size khác nhau.
5. **Không dùng getter để quyết định atomic:** chính `execute`/`submit` và rejection policy mới xác định việc nhận task.
6. **Instrument submitted/accepted/rejected/caller-runs** khi hệ thống cần thống kê chính xác đường đi của task.
7. **Đếm outcome nghiệp vụ độc lập:** success, failure, retry, cancel phải được xác định ở layer quản lý task hoặc Future.
8. **Đo latency:** thời gian chờ queue, execution duration, end-to-end response và tail latency giúp giải thích queue length.
9. **Đo bằng sampler nhẹ, tách pool khi có thể:** tránh exporter gây áp lực ngược cho hệ thống.
10. **Đánh giá shutdown/restart:** counter reset khi đổi instance; snapshot còn thay đổi trong lúc drain.
11. **Kiểm thử các biên:** pool rảnh, bão hòa, task thất bại, caller-runs, rejection, cancellation, shutdown.
12. **Không tin vào một snapshot duy nhất:** xác minh xu hướng với JFR/thread dump và workload thực tế.

**Kết luận:** `ThreadPoolExecutor` cung cấp các thước đo hữu ích như `getActiveCount()`, `getQueue().size()` và `getCompletedTaskCount()` để hiểu mức tải và tiến độ worker. Nhưng chúng là **quan sát trạng thái đang thay đổi**, không phải thao tác đồng bộ hay số thành công nghiệp vụ. Hãy kết hợp chúng với rejection metrics, thời gian chờ/chạy task và thống kê outcome riêng để có bức tranh chính xác về Executor Framework.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-012">Lifecycle: đóng executor và tránh thread leak</a>

<details>
<summary>Xem chi tiết</summary>

# Lifecycle: đóng Executor và tránh thread leak trong Java

## 1. Vì sao Executor cần một vòng đời rõ ràng?

`ExecutorService` cho phép nộp các `Runnable` hoặc `Callable` để một nhóm worker thread thực thi. Tạo một executor là **tạo ra một tài nguyên có vòng đời**, không đơn thuần gọi một method để chạy code.

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
executor.submit(() -> performTask());
```

Đây là **mã minh họa**, cần import `java.util.concurrent.*` và định nghĩa `performTask()`. Nếu không bao giờ đóng executor, các worker có thể tiếp tục tồn tại và tiêu tốn thread/stack/tài nguyên; chương trình Java có thể không kết thúc nếu còn các platform thread non-daemon sống. Nếu ứng dụng tạo executor mới theo từng yêu cầu rồi không đóng, số lượng thread có thể tăng không kiểm soát.

**Thread leak** trong ngữ cảnh này không nhất thiết là một vùng nhớ không còn tham chiếu mà GC không thu hồi. Nó thường là tình trạng **thread tiếp tục tồn tại hoặc tài nguyên gắn với thread không được giải phóng dù công việc logic đã kết thúc**. Các nguyên nhân phổ biến:

- Tạo `ExecutorService` mới nhiều lần nhưng không `shutdown()`.
- Worker bị kẹt ở `BlockingQueue.take()`, `Future.get()`, `Lock.lock()` hoặc I/O không có cơ chế đánh thức/hủy.
- Task có vòng lặp vô hạn không phản ứng `interrupt()`.
- Tạo quá nhiều pool với số worker cố định, tổng thread vượt khả năng hệ thống.
- Quên dọn `ThreadLocal` trong pooled worker, khiến dữ liệu gắn với thread tồn tại xuyên nhiều task.
- Task chặn lẫn nhau, deadlock hoặc giữ lock trong lúc chờ công việc khác cùng pool.
- Scheduled task hoặc cơ chế producer tiếp tục nộp việc sau khi một thành phần đáng lẽ đã đóng.

Đích đến của bài là một contract có thể kiểm chứng:

> **Ai tạo executor thì phải xác định rõ ai sở hữu, ai được nộp việc, khi nào ngừng nhận, task còn chạy được xử lý thế nào, và làm sao xác nhận executor đã terminated.**

Bài này thuộc **Executor Framework** của **Java concurrency nói chung**, không phụ thuộc framework.

## 2. Lifecycle gồm những pha nào?

Một executor không chỉ có hai trạng thái “đang chạy” và “đã đóng”. Mô hình sử dụng thường là:

```text
       tạo ExecutorService
               |
               v
        RUNNING / ACCEPTING
        - nhận execute/submit
        - worker chạy và lấy task từ queue
               |
          shutdown()
               |
               v
        SHUTDOWN / DRAINING
        - không nhận tác vụ mới
        - những task đã nhận vẫn được phép hoàn tất
               |
        worker và queue kết thúc
               |
               v
          TERMINATED
        - không còn worker thực thi task
        - awaitTermination có thể trả true
```

Một nhánh khác là:

```text
RUNNING hoặc SHUTDOWN
      |
 shutdownNow()
      |
 yêu cầu dừng ngay / interrupt worker
 lấy pending tasks chưa bắt đầu
      |
 nếu task hợp tác và worker kết thúc
      v
  TERMINATED
```

Với `ThreadPoolExecutor`, JVM có các trạng thái thực thi nội bộ chi tiết hơn, nhưng không nên viết code dựa trên chi tiết implementation. API public cho ta các điểm quan sát:

- `isShutdown()`: executor đã nhận yêu cầu đóng; **không** có nghĩa worker đã xong.
- `isTerminated()`: executor đã kết thúc sau shutdown.
- `awaitTermination(timeout, unit)`: đợi trạng thái terminated hoặc timeout/interrupt.
- `shutdown()`: đóng có trật tự, không chặn caller đợi hoàn tất.
- `shutdownNow()`: cố gắng dừng/cancel, nhưng không phải lệnh cưỡng bức kill thread.

Nếu chỉ gọi `shutdown()` rồi trả về, bạn mới **bắt đầu** lifecycle shutdown. Nếu cần xác nhận cleanup, phải đợi và xử lý trường hợp đợi thất bại.

## 3. Phân biệt shutdown, termination và task completion

Ba khái niệm này không tương đương:

| Sự kiện | Ý nghĩa | Chưa chứng minh điều gì? |
| --- | --- | --- |
| `shutdown()` đã được gọi | Executor từ chối công việc mới; công việc đã nhận có thể tiếp tục | Không chứng minh task đã xong |
| `isShutdown() == true` | Lifecycle đã bước vào shutdown | Không chứng minh worker đã kết thúc |
| `isTerminated() == true` | Executor đã kết thúc các worker theo hợp đồng | Không chứng minh mọi task thành công về nghiệp vụ |
| `awaitTermination(...) == true` | Executor đã terminated trong thời gian đợi | Không trả kết quả hoặc exception từng task |
| `Future.get()` trả thành công | Task tương ứng trả kết quả; có quan hệ bộ nhớ khi nhận kết quả | Không chứng minh các task khác đã hoàn tất |
| `Future.cancel(true)` thành công | Future chuyển sang trạng thái canceled và yêu cầu interrupt nếu thích hợp | Không chứng minh worker đã cleanup xong |

Ví dụ một `Callable` có thể ném exception. Worker vẫn kết thúc, executor vẫn terminated; nhưng business outcome là **FAILED**. Vì vậy, với bài toán cần bảo đảm kết quả, phải giữ `Future`, chờ `get()` hoặc có cơ chế báo lỗi riêng. **Chờ cả pool dừng** và **đọc outcome task** là hai phép kiểm tra khác nhau.

## 4. `shutdown()`: đóng có trật tự

Gọi:

```java
executor.shutdown();
```

sẽ ngăn nhận task mới và cho những task đã được nhận vào executor tiếp tục được xử lý theo hợp đồng. Nó **không chờ** các task đã nộp hoàn thành trước khi trả về.

Những tác vụ bị nộp sau khi shutdown thường bị từ chối thông qua `RejectedExecutionException` khi dùng các executor tiêu chuẩn với rejection policy mặc định. Nếu ứng dụng cấu hình `ThreadPoolExecutor` với một `RejectedExecutionHandler` khác, hành vi có thể khác; không giả định mọi handler đều ném exception.

### Ví dụ 1: graceful shutdown với kết quả xác định

Tạo tệp `GracefulLifecycleDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class GracefulLifecycleDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        Future<Integer> first = executor.submit(() -> {
            bothStarted.countDown();
            release.await();
            return 10;
        });
        Future<Integer> second = executor.submit(() -> {
            bothStarted.countDown();
            release.await();
            return 20;
        });

        bothStarted.await(); // Cả hai task đang đợi release.
        executor.shutdown();

        System.out.println("shutdown=" + executor.isShutdown());
        System.out.println("terminated before release="
                + executor.isTerminated());

        release.countDown();
        boolean stopped = executor.awaitTermination(
                10, TimeUnit.SECONDS);

        System.out.println("terminated=" + stopped);
        System.out.println("sum=" + (first.get() + second.get()));
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac GracefulLifecycleDemo.java
java GracefulLifecycleDemo
```

Kết quả khi worker được lập lịch hoàn thành trong thời hạn mười giây:

```text
shutdown=true
terminated before release=false
terminated=true
sum=30
```

**Vì sao kết quả đáng tin:**

1. Main đợi `bothStarted` trước khi shutdown. Hai worker đã vào task và đang chờ `release`.
2. `shutdown()` cấm submit mới, nhưng không interrupt các task này.
3. Vì chưa mở `release`, các task chưa thể hoàn tất; `isTerminated()` tại thời điểm đó là `false`.
4. Main mở latch, các task trả kết quả `10` và `20`.
5. `awaitTermination` chờ executor dừng; `Future.get()` xác nhận kết quả từng task và cung cấp bảo đảm bộ nhớ khi đọc outcome.

Time budget là minh họa: trong môi trường bị trì hoãn bất thường, `awaitTermination` vẫn có thể trả `false`. Code production phải kiểm tra nhánh đó thay vì mặc định mọi executor chắc chắn dừng trong mười giây.

### Lưu ý khi dùng Future

`Future.get()` có thể ném `ExecutionException` khi task thất bại, `InterruptedException` nếu caller bị ngắt, hoặc `CancellationException` nếu task bị hủy. Đừng chỉ gọi `awaitTermination` rồi xem mọi tác vụ đều thành công.

## 5. `shutdownNow()` không phải kill -9 cho Java thread

Gọi:

```java
List<Runnable> notStarted = executor.shutdownNow();
```

yêu cầu executor:

1. Dừng nhận task mới.
2. Cố dừng những task đang chạy (thường bằng `Thread.interrupt()`).
3. Không tiếp tục bắt đầu thực thi những task còn ở queue.
4. Trả về danh sách các `Runnable` chưa bắt đầu được trả lại theo hợp đồng.

**Điểm then chốt:** interrupt là **tín hiệu hợp tác**. Nếu một task không kiểm tra interrupt, không gọi API chờ interruptible và tiếp tục vòng lặp CPU, `shutdownNow()` không cưỡng chế nó kết thúc. Các tác vụ đang chạy cũng **không nằm hết** trong danh sách pending được trả về.

Với `submit(Callable)`, executor thường bọc task bằng một `RunnableFuture`, nên phần tử trong danh sách pending có thể **không phải object ban đầu** caller nộp. Muốn retry, phải kiểm tra tính hợp lệ, idempotency và tránh xử lý lặp.

### Ví dụ 2: phân biệt task đang chạy và task chưa bắt đầu

Tạo tệp `ShutdownNowLifecycleDemo.java`:

```java
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShutdownNowLifecycleDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1));

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        executor.execute(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            } finally {
                exited.countDown();
            }
        });

        started.await(); // Worker duy nhất đang chờ không thời hạn.
        executor.execute(() ->
                System.out.println("pending should not run"));

        List<Runnable> pending = executor.shutdownNow();
        exited.await();

        boolean terminated = executor.awaitTermination(
                10, TimeUnit.SECONDS);
        System.out.println("pending=" + pending.size());
        System.out.println("saw interrupt=" + interrupted.get());
        System.out.println("terminated=" + terminated);
    }
}
```

```bash
javac ShutdownNowLifecycleDemo.java
java ShutdownNowLifecycleDemo
```

Kết quả khi không có interrupt bất thường dành cho main và executor dừng trong thời hạn:

```text
pending=1
saw interrupt=true
terminated=true
```

Giải thích: pool có **một** worker, task đầu đã chạy và đợi ở latch không mở. Task thứ hai vào queue capacity 1, không thể bắt đầu. `shutdownNow()` cố interrupt task đầu; `await()` phản hồi bằng `InterruptedException` và task thoát. Task thứ hai được trả về dưới dạng pending. `exited.await()` giúp main đợi nhánh cleanup của task đầu trước khi in.

Chương trình dùng latch không bao giờ mở chỉ để **mô phỏng task bị chặn**. Không nên dùng cấu trúc chờ vô thời hạn như vậy trong sản xuất nếu không có đường timeout, cancel hoặc tín hiệu dừng rõ.

## 6. `awaitTermination()`: xác nhận đã kết thúc thật hay chưa

Chữ ký:

```java
boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;
```

Đây là phần chữ ký API rút gọn. `awaitTermination`:

- **Trả `true`** nếu executor đã terminated trước khi hoặc trong lúc đợi.
- **Trả `false`** nếu đến hạn mà executor vẫn chưa terminated.
- **Ném `InterruptedException`** nếu thread đang chờ bị interrupt.

Nó **không tự gọi `shutdown()`**. Nếu executor còn RUNNING và không có thread khác đóng nó, `awaitTermination` có thể chỉ chờ hết timeout rồi trả `false`.

Chờ có giới hạn phải luôn có nhánh xử lý `false`. Đừng coi việc log “shutdown requested” như bằng chứng worker đã dọn dẹp xong.

### Ví dụ 3: timeout trước, thành công sau

Tạo tệp `TerminationTimeoutDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TerminationTimeoutDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        executor.execute(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        started.await();
        executor.shutdown();

        boolean before = executor.awaitTermination(
                50, TimeUnit.MILLISECONDS);
        System.out.println("before release=" + before);

        release.countDown();
        boolean after = executor.awaitTermination(
                10, TimeUnit.SECONDS);
        System.out.println("after release=" + after);
    }
}
```

```bash
javac TerminationTimeoutDemo.java
java TerminationTimeoutDemo
```

Kết quả khi không bị interrupt từ ngoài và worker được lên lịch kết thúc trong thời hạn sau:

```text
before release=false
after release=true
```

Task chưa được mở latch ở lần chờ đầu, nên executor chắc chắn chưa terminated tại thời điểm đó. Sau khi main mở `release`, worker có thể kết thúc và executor đi tới terminated. Nếu tài nguyên có yêu cầu thời hạn nghiêm ngặt, code phải kiểm tra `after` và có fallback hợp lý.

## 7. Mẫu đóng hai giai đoạn được khuyến khích

Quy trình thường dùng cho pool do chính ứng dụng sở hữu:

1. `shutdown()` để ngừng nhận công việc mới và ưu tiên graceful completion.
2. `awaitTermination(graceTimeout)` để chờ có giới hạn.
3. Nếu chưa dừng, gọi `shutdownNow()` để yêu cầu cancellation.
4. `awaitTermination(forceTimeout)` thêm một lần để xác nhận worker thực sự đã thoát.
5. Nếu vẫn chưa dừng, **báo lỗi shutdown không hoàn tất**; không tuyên bố thành công.
6. Nếu caller bị interrupt khi chờ, `shutdownNow()` rồi khôi phục cờ interrupt hoặc truyền exception theo hợp đồng.

Một helper Java 17+:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecutorStopper {
    private ExecutorStopper() {}

    public static boolean stop(ExecutorService executor) {
        executor.shutdown();
        try {
            if (executor.awaitTermination(5, TimeUnit.SECONDS)) {
                return true;
            }
            executor.shutdownNow();
            return executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
```

Lớp này **biên dịch được nhưng không có `main`**; đặt trong `ExecutorStopper.java` và dùng từ code sở hữu executor. Hai timeout `5` giây chỉ là ví dụ. Helper trả `false` nếu không thể xác nhận termination hoặc caller bị interrupt. Trong hệ thống thật, caller còn nên biết danh sách pending từ `shutdownNow()` nếu các job đó cần retry/persist.

**Không nhầm shutdownNow với “force stop”:** nếu task không hợp tác, bước thứ hai vẫn có thể timeout. Không dùng `Thread.stop()` để cưỡng chế cắt ngang worker đang giữ khóa; API đó không an toàn.

## 8. Ownership: ai tạo thì ai đóng?

Đây là nguyên tắc phòng thread leak mạnh nhất:

- Một thành phần **tạo executor** cần có trách nhiệm quản lý lifecycle của nó.
- Thành phần **mượn executor** từ bên ngoài không nên tự ý gọi `shutdown()` lên tài nguyên dùng chung trừ khi hợp đồng cho phép.
- Tạo pool riêng cho mỗi request rất dễ gây quá nhiều thread và khó đóng đúng thời điểm.
- Executor dùng chung nhiều tác vụ thường sống theo vòng đời của service/application, không phải từng lời gọi.
- Nếu một method tạo pool tạm thời cho tác vụ cục bộ, phải có đường `finally` đóng pool ngay cả khi tính toán ném exception.

Ví dụ sở hữu đúng:

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
try {
    submitKnownTasks(executor);
    collectResults();
} finally {
    stopExecutor(executor);
}
```

Đây là **pseudocode Java minh họa**, các phương thức do ứng dụng định nghĩa; `stopExecutor` triển khai shutdown hai giai đoạn. Nếu `collectResults` ném exception, `finally` vẫn kích hoạt dọn tài nguyên. Tuy nhiên, `finally` không có nghĩa việc dừng chắc chắn thành công: helper phải báo tình trạng khi không thể terminate.

Ví dụ quyền sở hữu sai:

```java
void handleRequest() {
    ExecutorService executor = Executors.newFixedThreadPool(8);
    executor.submit(this::process);
    // Không giữ handle để shutdown.
}
```

Đây là đoạn minh họa. Nếu gọi nhiều lần, số pool và worker có thể tăng lên. GC không phải cơ chế lifecycle đáng tin để đóng executor. Phải quy định một owner quản lý số lượng, thời điểm dừng và các tác vụ đang chờ.

### Trường hợp caller chỉ được gửi việc

Một API tốt có thể chỉ đưa `Executor` thay vì toàn `ExecutorService` cho thành phần cần gọi `execute`. `Executor` không công bố phương thức `shutdown`, giúp tránh caller tùy tiện đóng pool của thành phần khác. Đây không phải ranh giới bảo mật tuyệt đối; nó là một cách **diễn đạt ownership trong thiết kế API**.

## 9. Vì sao pooled thread khác Thread tạo theo từng task?

Với `new Thread(task).start()`, một thread thường kết thúc khi `run()` trả về. Với thread pool, worker có thể tiếp tục sống sau khi một task kết thúc để lấy task mới. Điều đó giúp tái sử dụng thread, nhưng nếu không shutdown, thread có thể chờ công việc mới lâu dài.

`Executors.newFixedThreadPool(n)` tạo pool có khả năng duy trì số worker khi có công việc, thường sử dụng một queue không giới hạn thực tế theo nhu cầu nộp task. Nếu task producer nhanh hơn consumer, backlog có thể tăng đáng kể — **không phải thread leak nhưng vẫn là resource leak/bùng bộ nhớ tiềm tàng**.

`Executors.newCachedThreadPool()` có thể tạo nhiều worker khi lượng công việc tăng, và giữ worker nhàn rỗi một khoảng thời gian trước khi thu hồi. Nó không có nghĩa số worker luôn bị giới hạn thấp. Nếu request đến nhanh và task chặn lâu, số thread có thể tăng rất lớn.

`Executors.newSingleThreadExecutor()` thường giữ worker để tái sử dụng, nên quên shutdown một single-thread executor trong mỗi request vẫn có thể tạo nhiều thread chờ.

### Đừng dùng daemon thread để che việc thiếu shutdown

Một custom `ThreadFactory` có thể tạo daemon thread, nhưng daemon thread **không phải giải pháp dọn dẹp tài nguyên**. JVM có thể thoát khi chỉ còn daemon thread mà không đợi những task đó hoàn tất hoặc flush dữ liệu. Điều cần làm là **quản lý lifecycle và hoàn thành/hủy có kiểm soát**.

Một `ThreadFactory` đặt tên thread rõ ràng rất hữu ích khi chẩn đoán:

```java
ThreadFactory factory = runnable -> {
    Thread thread = new Thread(runnable);
    thread.setName("order-worker-" + thread.getId());
    return thread;
};
ExecutorService executor = Executors.newFixedThreadPool(4, factory);
```

Đoạn này cần import `ExecutorService`, `Executors`, `ThreadFactory`. Thread được tạo theo tên dễ nhận diện trong thread dump. Khi ứng dụng dùng nhiều pool, có thể kết hợp số pool/worker để tránh tên trùng. Không nên cấu hình daemon tùy tiện chỉ để làm process thoát.

## 10. Task không hợp tác interrupt có thể ngăn termination

`shutdownNow()` thông thường gọi interrupt các worker, nhưng `Thread.interrupt()` là **yêu cầu hợp tác**. Task CPU-bound không kiểm tra cờ hoặc task kẹt trong I/O không hỗ trợ interrupt có thể tiếp tục chạy.

Tạo tệp `InterruptIgnoringTaskDemo.java` để minh họa một task cố tình không quan tâm interrupt, nhưng có **cờ dừng riêng** cho phép chương trình hoàn tất:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptIgnoringTaskDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch started = new CountDownLatch(1);

        executor.execute(() -> {
            started.countDown();
            while (running.get()) {
                Thread.onSpinWait(); // Cố tình bỏ qua interrupt.
            }
        });

        started.await();
        executor.shutdownNow();

        boolean before = executor.awaitTermination(
                50, TimeUnit.MILLISECONDS);
        System.out.println("terminated while ignoring interrupt=" + before);

        running.set(false);
        boolean after = executor.awaitTermination(
                10, TimeUnit.SECONDS);
        System.out.println("terminated after stop signal=" + after);
    }
}
```

```bash
javac InterruptIgnoringTaskDemo.java
java InterruptIgnoringTaskDemo
```

Kết quả khi task thoát và worker được lập lịch trong thời hạn thứ hai:

```text
terminated while ignoring interrupt=false
terminated after stop signal=true
```

Task không đọc interrupt, nên `shutdownNow()` không làm nó thoát. Phải đặt `running=false`, lúc đó vòng lặp mới kết thúc. `Thread.onSpinWait()` chỉ là gợi ý tối ưu cho spin loop, **không phải cơ chế cancel hay chờ tài nguyên**. Ví dụ chủ đích chứng minh giới hạn của shutdownNow; không tạo busy-loop tương tự trong code thật.

## 11. Task hợp tác với interrupt như thế nào?

Hai trường hợp cơ bản:

**Task đang chờ bằng API interruptible:**

```java
try {
    while (!Thread.currentThread().isInterrupted()) {
        Job job = queue.take();
        process(job);
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
} finally {
    cleanup();
}
```

Đây là đoạn minh họa, `Job`, `queue`, `process`, `cleanup` là code của ứng dụng. `BlockingQueue.take()` có thể ném `InterruptedException` khi worker bị ngắt. Thread cần rời task hoặc propagate thay vì bắt exception rồi tiếp tục chờ vô hạn.

**Task CPU-bound hoặc chia batch:**

```java
for (Job job : jobs) {
    if (Thread.currentThread().isInterrupted()) {
        return;
    }
    processSafely(job);
}
```

Đây là đoạn minh họa. Phản hồi hủy ở **điểm nhất quán**: nếu xử lý một job có nhiều bước, cần hoàn thành/rollback một đơn vị trạng thái để không để lại invariant sai. Interrupt không tự rollback các thay đổi đã thực hiện.

Đừng mặc định `Thread.sleep()` trong vùng `synchronized` sẽ nhả monitor; sleep **không nhả lock**. Task giữ lock rồi ngủ chờ thành phần khác cũng cần lock có thể gây kẹt. Khi shutdown, cần xem cả lock ownership và các phương thức chờ.

### InterruptedException và cờ interrupted

Nhiều API chờ ném `InterruptedException` và thường xóa cờ interrupt của thread trước khi ném. Nếu method không thể truyền tiếp checked exception (như `Runnable.run()`), cách thông dụng là **khôi phục cờ** bằng `Thread.currentThread().interrupt()` và kết thúc công việc.

`Thread.currentThread().isInterrupted()` chỉ đọc trạng thái, **không xóa cờ**. `Thread.interrupted()` là static method đọc và **xóa cờ hiện tại**. Không vô ý tiêu thụ tín hiệu ngắt rồi lặp lại vô hạn.

## 12. `Future.cancel(true)` khác shutdown toàn executor

Nếu một task riêng không còn cần, `Future.cancel(true)` có thể gửi yêu cầu interrupt tới task tương ứng mà không đóng cả pool. Ngược lại:

- `shutdown()`: đóng admission của **toàn executor**, không chủ động hủy các task đã nhận.
- `shutdownNow()`: yêu cầu dừng **toàn executor** và trả về pending tasks.
- `Future.cancel(true)`: nỗ lực hủy **một task** theo contract Future/executor.

Một `Future` bị đánh dấu cancelled **chưa chứng minh cleanup của worker đã hoàn tất**. Nếu thread đang chạy bị cancel, Future có thể chuyển trạng thái trước khi code `finally` của task chạy xong; `Future.get()` có thể ném `CancellationException` mà không chờ riêng bước cleanup.

Vì vậy, nếu lifecycle của tài nguyên yêu cầu biết cleanup đã xong, có thể dùng `CountDownLatch` trong `finally`, tín hiệu trạng thái hoặc chờ executor termination. Đừng chỉ dựa vào `isCancelled()` để đóng tài nguyên mà task có thể còn dùng.

### Pending tasks sau shutdownNow

`List<Runnable>` trả từ `shutdownNow()` không phải danh sách tất cả công việc thất bại; nó là các tác vụ chưa bắt đầu được executor trả về. Nó có thể chứa wrapper `FutureTask` khi công việc được nộp bằng `submit`. Nếu cần retry, phải xét các task có thể đã bắt đầu, idempotency, deduplication và yêu cầu lưu bền vững. Một pool trong RAM **không thay durable job queue**.

## 13. Tránh thread leak bằng cleanup trong từng task

Một worker của thread pool có thể chạy **nhiều task liên tiếp trên cùng thread**. Vì vậy dữ liệu dạng `ThreadLocal` có thể tồn tại sau khi task kết thúc và xuất hiện trong task tiếp theo nếu không được xóa.

Ví dụ nguy cơ:

```java
static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

void process(String id) {
    REQUEST_ID.set(id);
    // Nếu quên remove(), thread worker có thể mang id cũ sang task khác.
}
```

Đây là đoạn minh họa. Không chỉ là sai dữ liệu: nếu `ThreadLocal` trỏ tới object lớn, session hoặc resource, pooled worker tồn tại lâu có thể giữ object đó lâu hơn mong muốn, gây **memory retention**.

Dùng `try/finally`:

```java
REQUEST_ID.set(id);
try {
    processRequest();
} finally {
    REQUEST_ID.remove();
}
```

### Ví dụ 4: xác nhận ThreadLocal được dọn

Tạo tệp `ThreadLocalCleanupDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ThreadLocalCleanupDemo {
    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<?> first = executor.submit(() -> {
            CONTEXT.set("request-123");
            try {
                System.out.println("first=" + CONTEXT.get());
            } finally {
                CONTEXT.remove();
            }
        });
        first.get();

        Future<?> second = executor.submit(() ->
                System.out.println("second=" + CONTEXT.get()));
        second.get();

        executor.shutdown();
        boolean terminated = executor.awaitTermination(
                10, TimeUnit.SECONDS);
        System.out.println("terminated=" + terminated);
    }
}
```

```bash
javac ThreadLocalCleanupDemo.java
java ThreadLocalCleanupDemo
```

Kết quả nếu executor kết thúc trong thời hạn:

```text
first=request-123
second=null
terminated=true
```

`newSingleThreadExecutor` chạy task theo thứ tự, tái sử dụng worker để cho thấy context đã được remove; `Future.get()` của task thứ nhất hoàn thành trước khi nộp task thứ hai. Nếu quên `CONTEXT.remove()`, task thứ hai có thể thấy `request-123` thay vì `null`. Điều này có thể rò rỉ thông tin giữa requests.

**Phạm vi:** `ThreadLocal.remove()` dọn phần context do task thiết lập; không thay thế việc đóng socket, stream, connection hoặc executor. Với resource trong task, vẫn cần `try-with-resources`/`finally` tương ứng.

## 14. Shutdown an toàn với exception và try/finally

Một số path thường bỏ sót cleanup:

- Constructor/khởi tạo pool thành công nhưng code sau đó ném exception trước khi gọi `shutdown()`.
- Caller return sớm, hoặc bị cancel khi đang chờ `Future.get()`.
- Lệnh shutdown nằm sau một thao tác có thể ném unchecked exception.
- Worker kết thúc task nhưng quên `close()` resource đã mở.

Mẫu sở hữu tài nguyên:

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
try {
    runWorkflow(executor);
} finally {
    boolean stopped = ExecutorStopper.stop(executor);
    if (!stopped) {
        System.err.println("executor still not confirmed terminated");
    }
}
```

Đây là đoạn minh họa dùng helper `ExecutorStopper` ở mục 7 và hàm `runWorkflow` do ứng dụng định nghĩa. `finally` chạy khi rời scope bình thường hoặc bởi exception; nhưng không có phép màu ép thread không hợp tác phải thoát. Chỉ nên coi cleanup thành công khi có tín hiệu terminated.

### Không vô tình ghi đè exception ban đầu

Nếu code cleanup trong `finally` tự ném exception, nó có thể che mất exception chính đã khiến workflow thất bại. Trong code production, cần quyết định cách ghi nhận lỗi shutdown (log, suppressed exception, kết quả tổng hợp) mà không làm mất nguyên nhân gốc. `try-with-resources` có cơ chế suppressed exceptions cho tài nguyên `AutoCloseable`, nhưng trên **JDK 17** việc sở hữu `ExecutorService` thông thường được quản lý bằng shutdown rõ ràng như các ví dụ ở đây.

### Về `ExecutorService.close()` trên JDK mới

Ở các JDK mới hơn, `ExecutorService` có thể được dùng dưới dạng `AutoCloseable` nhờ phương thức `close()` được bổ sung cho giao diện (từ Java 19). Không suy diễn rằng mẫu `try (var executor = ...)` sẽ biên dịch nguyên trạng với **JDK 17**. `close()` trong JDK mới có ngữ nghĩa đóng/đợi riêng; khi yêu cầu deadline và chính sách hủy cụ thể, vẫn phải hiểu hợp đồng API và quản lý interruption/timeout có chủ đích.

## 15. Thread leak và resource leak khác nhau thế nào?

Một số khái niệm gần nhau cần phân biệt:

| Hiện tượng | Dấu hiệu | Nguyên nhân thường gặp |
| --- | --- | --- |
| **Thread leak** | Số thread sống tăng, nhiều worker chờ mãi, JVM không thoát | Tạo pool không shutdown, task bị treo |
| **Thread retention có chủ đích** | Worker thread sống dù chưa có task | Pool được chủ sở hữu giữ để tái sử dụng; không nhất thiết là leak |
| **Queue backlog** | Queue dài, heap tăng, response chậm | Producer nhanh hơn consumer, queue không bị giới hạn |
| **ThreadLocal memory retention** | Heap giữ object theo vòng đời worker | Không gọi `ThreadLocal.remove()` |
| **Resource leak trong task** | Connection/file descriptor không trả lại | Thiếu try-with-resources/finally |
| **Deadlock hoặc starvation** | Worker sống nhưng không tiến triển | Lấy khóa sai thứ tự, tự chờ Future trong pool quá nhỏ |
| **Shutdown chưa hoàn tất** | `isShutdown()==true`, `isTerminated()==false` lâu dài | Task không hợp tác hủy, blocked I/O, vòng chờ |

**Không phải mọi thread đang chờ đều là leak.** Một pool sống suốt lifecycle của dịch vụ có thể hợp lệ. Vấn đề là sự khác biệt giữa **số thread và tài nguyên mà thiết kế dự kiến** với **số thread/resource còn giữ khi owner không còn cần chúng**.

Một worker chờ queue vì sẵn sàng xử lý thêm task là bình thường khi ứng dụng còn RUNNING. Nó trở thành dấu hiệu lỗi nếu thành phần sở hữu đã “đóng” logic nhưng quên dừng executor.

## 16. Khóa và chờ Future có thể làm pool tự kẹt

Một lỗi không hẳn do quên shutdown là **thread-starvation deadlock**: mọi worker trong pool đều chờ một task khác **cũng được nộp vào chính pool đó**, nhưng không còn worker rảnh để thực thi task mới.

Ví dụ ý tưởng (không nên chạy để tạo kẹt):

```java
ExecutorService pool = Executors.newSingleThreadExecutor();

pool.submit(() -> {
    Future<Integer> child = pool.submit(() -> 42);
    return child.get(); // Worker duy nhất đợi child chưa thể chạy.
});
```

Đây là mã minh họa, cần import các lớp executor/Future và xử lý checked exception trong lambda theo kiểu `Callable`. Worker duy nhất đang đợi `child.get()`; child chờ đến lượt được chạy bởi worker đó. `shutdown()` không tự giải quyết vòng chờ; `shutdownNow()` có thể gửi interrupt tới worker, nhưng cần task hợp tác và code xử lý lỗi.

Một deadlock monitor cũng có thể làm termination không đến. Khi debug, hãy xét **thread dump**, stack trace, số worker, queue và phụ thuộc giữa Future/task/lock.

Biện pháp phòng tránh gồm: tránh một task block chờ task con trong cùng pool không đủ worker, tách workload blocking/CPU khi phù hợp, dùng tổ chức task không tạo vòng phụ thuộc, và thiết kế timeout/cancel thay vì đợi vô hạn.

## 17. Monitoring: biết pool thực sự có leak hay chỉ đang bận

Khi cần chẩn đoán `ThreadPoolExecutor`, có thể quan sát các chỉ số như:

- `getPoolSize()`: số worker hiện tại trong pool.
- `getActiveCount()`: số worker **ước tính** đang thực thi task.
- `getQueue().size()`: backlog hiện tại của queue (snapshot, có thể đổi ngay).
- `getCompletedTaskCount()`: số task đã hoàn tất theo thống kê của executor.
- `getLargestPoolSize()`: mức pool size cao nhất ghi nhận.
- `isShutdown()`, `isTerminated()`: mốc lifecycle.

Các giá trị này **không phải transaction snapshot nhiều trường**. Một lần đọc từng metric có thể từ các thời điểm khác nhau, nên dùng để quan sát xu hướng, không làm điều kiện quyết định tính đúng đắn của workflow.

### Công cụ JVM hỗ trợ

- `jcmd <pid> Thread.print`: lấy thread dump để xem worker đang RUNNABLE, WAITING, TIMED_WAITING hay BLOCKED ở đâu.
- Java Flight Recorder (JFR): ghi nhận thread, blocking, allocation và những sự kiện liên quan khi cần.
- `jstack` hoặc công cụ tương đương: hỗ trợ phân tích thread dump trên môi trường phù hợp.
- `ThreadMXBean`: quan sát thread count, deadlock detection và thông tin JVM thread.

Đây là **các công cụ chẩn đoán**, không phải phần code chạy của bài. Tên thread rõ ràng từ `ThreadFactory` giúp nhận biết pool nào chưa dừng. Số thread theo thời gian, trạng thái shutdown và stack trace thường quan trọng hơn một snapshot duy nhất.

### Dấu hiệu cần điều tra

```text
Sau khi một test / request / service lifecycle kết thúc:
  số thread tên "worker-..." vẫn tăng qua mỗi lượt;
  nhiều thread đợi queue.take() dù owner đã đóng;
  isShutdown() == false trên pool đáng lẽ được giải phóng;
  hoặc isShutdown() == true nhưng isTerminated() không trở thành true.
```

Không gọi toàn bộ các trường hợp đó là cùng một lỗi. Pool còn RUNNING là thiếu shutdown; pool đã shutdown nhưng còn task kẹt là vấn đề cancellation/liveness; queue tăng là vấn đề backpressure.

## 18. Rejected execution: race giữa submit và shutdown

Hai thread có thể tranh chấp:

```text
Thread A                              Thread B
--------                              --------
if (!executor.isShutdown()) ...
                                      executor.shutdown()
executor.submit(task) -> REJECTED
```

`isShutdown()` chỉ là quan sát tại một thời điểm, không tạo giao dịch với `submit()`. Vì vậy, code nộp việc phải xử lý `RejectedExecutionException` hoặc hợp đồng của rejection policy.

Ví dụ:

```java
try {
    executor.execute(task);
} catch (RejectedExecutionException e) {
    reportNotAccepted(task);
}
```

Đây là đoạn minh họa, `executor`, `task` và `reportNotAccepted` do ứng dụng định nghĩa. Nếu task cần đảm bảo không mất, đường rejection có thể phải ghi bền vững, retry trên executor khác có kiểm soát hoặc báo lỗi cho caller.

Tránh thiết kế một “cờ custom accepting” tách rời executor mà không có đồng bộ; nó có thể làm nảy sinh race mới. Một giao thức shutdown tốt thường có điểm ngừng nhận ở tầng API/service, và mọi đường nộp task vẫn xử lý rejection như trường hợp hợp lệ.

## 19. ScheduledExecutorService cũng cần shutdown

`ScheduledExecutorService` nhận tác vụ trễ hoặc định kỳ. Nếu tạo `Executors.newSingleThreadScheduledExecutor()` cho mỗi đối tượng/request rồi không shutdown, worker và task đã lên lịch có thể tồn tại lâu hơn cần thiết.

Một số triển khai như `ScheduledThreadPoolExecutor` có chính sách cụ thể về **delayed tasks** và **periodic tasks** khi shutdown. Đừng mặc định rằng mọi scheduled task đã đặt lịch đều chạy hoặc đều bị hủy giống nhau trong mọi implementation.

Những điểm cần kiểm tra:

- Có task định kỳ nào tiếp tục hoặc bị dừng khi gọi `shutdown()`?
- Các delayed tasks đã nộp nhưng chưa đến hạn sẽ được xử lý theo policy nào?
- Khi cancel một scheduled future, queue có giữ wrapper pending cho đến thời điểm chạy dự kiến không?
- Với `ScheduledThreadPoolExecutor`, `setRemoveOnCancelPolicy(true)` có thể hữu ích để các task bị cancel được loại khỏi queue nhanh hơn trong các tình huống phù hợp.
- Chỉ sau khi policy và task kết thúc thì executor mới terminated.

Cũng cần theo dõi resource mà scheduled task dùng: việc cancel timer không tự đóng connection hoặc xóa dữ liệu `ThreadLocal` mà job đã tạo.

## 20. Bounded queue, pool size và tổng tài nguyên

Tránh thread leak không chỉ là đóng pool. Một pool được tạo đúng lifecycle vẫn có thể gặp **tải vượt khả năng xử lý**, dẫn tới nhiều task queued và giữ bộ nhớ.

Ví dụ `Executors.newFixedThreadPool(n)` thường có hàng đợi không giới hạn thực tế. Nếu producer nộp 100.000 task mỗi giây nhưng worker chỉ xử lý 10.000, backlog sẽ tăng. Dù số thread ổn định, heap và độ trễ có thể bị đẩy tới giới hạn.

Khi muốn kiểm soát tải, có thể cấu hình `ThreadPoolExecutor` với bounded queue và rejection policy rõ:

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        4, 4, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(100));
```

Đây là đoạn minh họa, cần import `ThreadPoolExecutor`, `TimeUnit` và `ArrayBlockingQueue`. Khi bốn worker đều bận và queue đã đầy, yêu cầu mới có thể bị từ chối theo rejection policy đã chọn. Phải xử lý **backpressure** thay vì tạo thêm vô hạn task chờ.

Chọn queue capacity, pool size và thời hạn task theo thực tế. Một queue bounded không tự giải quyết task chết kẹt: nếu mọi worker đứng chờ vĩnh viễn, queue sẽ đầy và nộp việc bị từ chối. Cần timeout/cancel và thiết kế liveness đúng.

## 21. Test lifecycle như thế nào?

Một test tốt không dựa vào `Thread.sleep(100)` rồi đoán pool đã dừng. Nên tạo precondition có kiểm soát bằng `CountDownLatch`, `Future` hoặc các primitive đồng bộ thích hợp.

**Những điều nên kiểm tra:**

1. Sau `shutdown()`, việc nộp task mới được xử lý theo rejection policy.
2. Tác vụ đã nộp được hoàn thành trong graceful path nếu task hợp tác.
3. `awaitTermination` trả `false` khi có task cố ý chưa thể hoàn tất.
4. Khi task được mở latch hoặc xử lý interrupt, executor chuyển terminated.
5. `shutdownNow()` gửi interrupt tới task interruptible và trả về pending task chưa chạy (trong tình huống được điều phối).
6. Code cleanup chạy trong `finally`, kể cả khi có exception/cancellation.
7. Sau mỗi lượt test tạo pool, owner **phải đóng** pool, tránh làm bộ test tích lũy worker.

### Mini test bằng assert thông thường

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
try {
    Future<Integer> value = executor.submit(() -> 42);
    if (value.get() != 42) {
        throw new AssertionError("wrong result");
    }
} finally {
    executor.shutdownNow();
    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        throw new AssertionError("pool did not stop");
    }
}
```

Đây là đoạn minh họa nằm trong method khai báo `throws InterruptedException, ExecutionException` (hoặc `throws Exception`), với các import `ExecutorService`, `Executors`, `Future` và `TimeUnit`. Điều quan trọng là kiểm tra được **outcome task** và **termination** tách biệt.

Trong sản phẩm thật, thay vì `shutdownNow()` ngay trong finally, thường chọn graceful trước rồi fallback theo yêu cầu dữ liệu. Khi test bị interrupt, phải xử lý cờ interrupt và cleanup phù hợp để không gây rò rỉ pool sang test khác.

## 22. Một quy trình đóng service thực tế

Giả sử service có một executor nhận background jobs và một nguồn gửi yêu cầu. Shutdown an toàn cần có thứ tự:

1. **Ngừng nhận yêu cầu mới** ở cổng giao tiếp của service.
2. **Ngừng producer** nộp task mới vào executor; có giao thức báo rejected nếu còn cạnh tranh.
3. **Gọi `shutdown()`** để drain các task đã nhận.
4. **Đợi có deadline** bằng `awaitTermination()`. Chọn thời hạn theo nghiệp vụ, không tùy tiện chờ vô hạn.
5. Nếu deadline graceful hết, **`shutdownNow()`** để yêu cầu cancel phần còn lại và xem xét pending tasks.
6. **Đợi lại có giới hạn**; nếu chưa terminated, ghi nhận shutdown chưa hoàn tất, metric/log và có phương án xử lý của hệ thống điều phối process.
7. **Đóng resource phụ thuộc** (database pool, file, socket...) theo thứ tự owner/task không còn cần chúng, trừ resource cần đóng sớm để phá blocking I/O.
8. Đối chiếu **outcome nghiệp vụ** qua Future, kênh kết quả, idempotency hoặc trạng thái lưu bền vững; termination không thay thế việc xác nhận job đã commit.

**Mấu chốt:** thứ tự đóng phải theo **đồ thị phụ thuộc tài nguyên**. Nếu executor còn task đang dùng database mà đóng database trước, tác vụ có thể thất bại. Nếu task kẹt đọc socket, có thể cần đóng socket để đánh thức nó; không có thứ tự duy nhất cho mọi hệ thống.

### Deadline tổng thể

Nếu gọi `awaitTermination(5s)` rồi thêm `awaitTermination(5s)`, thời gian chờ có thể lên tới khoảng **10 giây** cộng overhead. Nếu yêu cầu đóng toàn service trong một deadline tổng thể, nên phân bổ ngân sách hoặc tính thời gian còn lại bằng `System.nanoTime()`; không cộng vô hạn các timeout độc lập.

Một timeout hết hạn **không phải là lệnh kill**, chỉ là thông tin caller đã chờ đủ lâu. Hãy báo tình trạng chưa hoàn tất thay vì bịa rằng worker đã dừng.

## 23. Checklist chống thread leak

Trước khi đưa một thành phần sử dụng executor vào production, tự kiểm tra:

- **Owner:** Ai tạo pool? Ai được gọi `shutdown()`? Có đóng đúng một lần khi lifecycle kết thúc không?
- **Số lượng:** Có pool mới trong mỗi request, mỗi vòng lặp hoặc mỗi retry không? Giới hạn số thread/queue ở đâu?
- **Admission:** Khi bắt đầu shutdown, nộp task mới có bị từ chối đúng cách không?
- **Task:** Mỗi task có điểm thoát, timeout, hợp đồng interrupt và cleanup hay không?
- **Blocking:** Có task giữ lock chờ task khác trong cùng pool không?
- **Future:** Có kiểm tra task outcome qua `get()` hoặc kênh lỗi không?
- **Queue:** Có backlog vô hạn và thiếu backpressure không?
- **ThreadLocal:** Có `remove()` trong `finally` cho dữ liệu theo request không?
- **Resources:** Có đóng file, socket, stream, connection không?
- **Termination:** Có `awaitTermination()` và xử lý nhánh `false` không?
- **Diagnostics:** Pool có tên thread và metric để truy ra worker bị treo không?
- **Testing:** Có test chắc chắn dọn pool kể cả test thất bại không?

Đó là những kiểm tra theo **lifecycle**; không cần đợi đến khi JVM tăng hàng nghìn thread mới bắt đầu xem xét.

## 24. Tự kiểm tra kiến thức

**Câu 1.** Sau `shutdown()`, executor có chắc chắn đã hết thread chưa?

**Trả lời:** Không. `shutdown()` bắt đầu đóng có trật tự, không đợi. Những task đã nhận có thể tiếp tục chạy.

**Câu 2.** `isShutdown()==true` và `isTerminated()==false` có mâu thuẫn không?

**Trả lời:** Không. Executor đã bước vào shutdown nhưng vẫn có task/worker chưa kết thúc.

**Câu 3.** `shutdownNow()` có “giết” một task CPU-bound không kiểm tra interrupt không?

**Trả lời:** Không bảo đảm. Nó gửi yêu cầu hủy hợp tác; task có thể tiếp tục chạy.

**Câu 4.** `awaitTermination(2, SECONDS)` trả `false` nghĩa là gì?

**Trả lời:** Executor chưa đạt terminated trong hai giây. Không tự suy ra mọi task thất bại, hay mọi worker đã dừng.

**Câu 5.** Một method tạo `newSingleThreadExecutor()` mỗi request mà không shutdown. Vì sao nguy hiểm?

**Trả lời:** Mỗi request có thể để lại worker nhàn rỗi nhưng còn sống; tổng số thread và tài nguyên tăng theo số request.

**Câu 6.** Nếu task đã hoàn thành `Future.get()` với kết quả đúng, có thể bỏ qua đóng executor không?

**Trả lời:** Không. Future task hoàn thành không có nghĩa worker pool đã kết thúc vòng đời. Owner vẫn cần shutdown khi không dùng nữa.

**Câu 7.** Vì sao cần `ThreadLocal.remove()` trong `finally`?

**Trả lời:** Pooled thread có thể chạy nhiều task; dữ liệu request cũ có thể tiếp tục được giữ và bị task sau nhìn thấy, gây memory retention hoặc rò rỉ context.

**Câu 8.** `Future.cancel(true)` có chứng minh worker đã cleanup xong không?

**Trả lời:** Không. Trạng thái Future canceled có thể đến trước khi task hoàn tất cleanup; cần tín hiệu riêng hoặc termination phù hợp.

**Câu 9.** Một task trong single-thread executor nộp task con vào chính pool rồi `get()` chờ task con. Nguy cơ gì?

**Trả lời:** Thread-starvation deadlock: worker duy nhất chờ một task chưa có worker để chạy.

**Câu 10.** Sau `shutdown()`, thread A kiểm tra `!isShutdown()` rồi submit trong khi thread B shutdown. Có chắc submit thành công không?

**Trả lời:** Không. Có race giữa check và submit; caller phải xử lý rejection.

**Câu 11.** `ThreadFactory` tạo daemon thread có thay được shutdown đúng cách không?

**Trả lời:** Không. Daemon có thể khiến process thoát mà không chờ task flush/cleanup; không phải cách quản lý tài nguyên an toàn.

**Câu 12.** Một pool đã terminated, liệu mọi job đã commit database thành công?

**Trả lời:** Không nhất thiết. Task có thể fail, cancel hoặc kết thúc trước commit. Phải kiểm tra outcome và có transaction/recovery ở tầng nghiệp vụ.

## 25. Tổng kết

**ExecutorService là tài nguyên có vòng đời**, không chỉ là một hàm chạy task. Muốn tránh thread leak cần **owner rõ ràng, ngừng nhận việc đúng lúc, shutdown có giới hạn thời gian, task hợp tác cancel, cleanup tài nguyên và xác nhận termination**.

Một mẫu an toàn là **`shutdown()` → `awaitTermination()` → nếu cần `shutdownNow()` → `awaitTermination()` lần nữa**. Nhưng shutdownNow chỉ cố interrupt, không cưỡng chế task dừng; timed wait chỉ báo đã hoặc chưa terminated trong khoảng đợi; và trạng thái terminated không thay thế việc kiểm tra kết quả nghiệp vụ.

Thiết kế tốt bắt đầu từ lúc tạo pool: tái sử dụng ở scope phù hợp, không tạo bừa trong mỗi request, đặt tên worker, giới hạn backlog, dọn `ThreadLocal`/resources, tránh task tự chờ nhau và có test lifecycle. Với Java concurrency nói chung, **đóng executor cũng quan trọng như nộp tác vụ vào executor**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-013">Kiểm soát số task đang chờ và backpressure</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm soát số task đang chờ và backpressure trong Java Executor Framework

## 1. Vấn đề: producer có thể nhanh hơn consumer

Trong mô hình Executor Framework của Java, **producer** gọi `execute(...)` hoặc `submit(...)` để giao công việc, còn các **worker thread** thực thi tác vụ. Khi tốc độ công việc đi vào lớn hơn năng lực xử lý trong một khoảng thời gian đủ dài, tác vụ sẽ tích lại trong hàng đợi.

Ví dụ: hệ thống nhận trung bình 200 tác vụ/giây nhưng các worker chỉ hoàn thành trung bình 120 tác vụ/giây. Khi tốc độ này duy trì, hàng đợi tăng khoảng 80 tác vụ/giây (chưa xét thay đổi tốc độ và các giới hạn khác). Sau một phút, đã có thể có hàng nghìn tác vụ chờ. Tăng dung lượng queue chỉ trì hoãn thời điểm gặp giới hạn, **không làm tăng năng lực xử lý thực sự**.

Hệ quả:
- **Độ trễ tăng:** task đã được nhận nhưng phải đợi lâu mới chạy; timeout ở phía yêu cầu có thể xảy ra trước khi worker bắt đầu.
- **Bộ nhớ tăng:** mỗi `Runnable`, `Callable`, closure và dữ liệu mà nó tham chiếu còn được giữ khi nằm trong queue; nguy cơ `OutOfMemoryError`.
- **Công việc lỗi thời:** dữ liệu hoặc kết quả không còn giá trị khi được xử lý quá muộn.
- **Hiệu ứng dây chuyền:** producer vẫn nạp thêm việc, tạo áp lực đến downstream, disk, network hoặc dịch vụ khác.

**Backpressure** là cách chuyển tín hiệu “consumer không theo kịp” về phía producer, để producer **chậm lại, chờ, bị từ chối hoặc chủ động giảm tải**. Nó là một chính sách kiểm soát lưu lượng và số công việc tồn đọng; không đồng nghĩa với việc chỉ cấu hình `maximumPoolSize` lớn hơn.

Bài này tập trung vào **Java Concurrency nói chung**, đặc biệt là `ThreadPoolExecutor`, `BlockingQueue`, `Semaphore` và cơ chế admission control, không phụ thuộc framework.

## 2. Phân biệt các số lượng cần kiểm soát

| Đại lượng | Ý nghĩa | Cách giới hạn thường dùng |
| --- | --- | --- |
| Task đang chạy (running) | Công việc worker hiện đang thực thi | Kích thước pool, giới hạn tài nguyên hoặc semaphore |
| Task đang xếp hàng (queued) | Công việc đã được executor chấp nhận nhưng chưa bắt đầu | `BlockingQueue` có capacity hữu hạn |
| Task đang tồn tại trong hệ (in-flight/outstanding) | Tùy hợp đồng: thường bao gồm task đã nhận nhưng chưa hoàn tất, tức running + queued | Semaphore hoặc cơ chế cấp phép trước khi enqueue |
| Producer đang chờ nộp task | Lời gọi bị block trước admission; task chưa vào pool | `Semaphore.acquire()`, `tryAcquire(timeout, unit)`, bounded input queue |
| Task bị từ chối hoặc bỏ | Công việc không được pool nhận hoặc bị chính sách loại | Handler, metric, timeout, fallback và hợp đồng lỗi |

Không nên đánh đồng **giới hạn độ dài queue** với **giới hạn tổng số tác vụ**. Ví dụ pool có 4 worker và queue 100 phần tử có thể giữ khoảng 104 tác vụ đang chạy/chờ cùng lúc trong trạng thái đầy thông thường. Trong lúc hệ thống đồng thời hoạt động, số đếm và thời điểm chuyển giữa running/queued là biến động; con số “104” là mô hình capacity, không phải giao dịch nhận việc nguyên tử cho mọi lời gọi.

`getQueue().size()` và `getActiveCount()` chủ yếu phục vụ quan sát; dùng chúng để tự kiểm tra “nếu size nhỏ thì submit” là **check-then-act race**: producer khác có thể chiếm chỗ ngay giữa hai thao tác.

## 3. ThreadPoolExecutor quyết định xếp hàng như thế nào?

Với `ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler)`, luồng điều khiển tổng quát của `execute(command)` là:

1. Nếu số worker nhỏ hơn `corePoolSize`, ưu tiên tạo worker mới để chạy task.
2. Nếu đã đạt ngưỡng core và executor còn nhận việc, thử `offer` task vào `workQueue`.
3. Nếu queue không nhận, thử mở thêm worker đến `maximumPoolSize`.
4. Nếu không nhận được hoặc executor đã shutdown, chuyển cho `RejectedExecutionHandler`.

Sau thao tác enqueue còn có bước kiểm tra lại trạng thái executor để xử lý trường hợp shutdown xảy ra đồng thời. Vì vậy, việc “queue có chỗ” chưa phải lời hứa chắc chắn tác vụ sẽ được chấp nhận.

### Tác động của loại queue

| Loại queue | Hành vi dưới tải | Lưu ý |
| --- | --- | --- |
| `ArrayBlockingQueue<>(capacity)` | Queue hữu hạn, producer có thể gặp rejection khi worker và queue đều đầy | Dễ đặt giới hạn rõ ràng, không tự block caller trong `execute` |
| `LinkedBlockingQueue<>(capacity)` | Cũng có giới hạn khi truyền capacity | Có cách quản lý nút và chi phí bộ nhớ khác `ArrayBlockingQueue` |
| `LinkedBlockingQueue<>()` | Giới hạn mặc định rất lớn (`Integer.MAX_VALUE`), thường tích tụ task trước khi đạt limit | `Executors.newFixedThreadPool(n)` dùng mô hình này; `maximumPoolSize` gần như không giúp nở pool khi queue tiếp tục nhận |
| `SynchronousQueue<>` | Không lưu task chờ, phải chuyển giao trực tiếp tới worker | Dễ tạo nhiều worker tới maximum; khi không thể chuyển giao hoặc mở worker thì rejection |
| `PriorityBlockingQueue<>`, `DelayQueue<>` | Thường không có capacity hữu hạn do ứng dụng đặt qua constructor chuẩn | Độ ưu tiên/độ trễ không thay thế kiểm soát lượng task |

**Điểm rất dễ nhầm:** `ThreadPoolExecutor.execute` dùng đường nhận việc không block lâu để chờ chỗ trong queue. Dùng `ArrayBlockingQueue<>(100)` **không tự biến lời gọi `execute` thành `put()`**; lúc quá tải nó sẽ thử nở pool rồi gọi rejection handler. Muốn chờ có kiểm soát, producer phải thực hiện admission control riêng, hoặc chấp nhận một handler như `CallerRunsPolicy`.

Không nên tự gọi `executor.getQueue().put(task)` để “ép” công việc vào pool: thao tác này đi vòng qua các kiểm tra trạng thái và quy tắc tạo worker của `execute`, có thể tạo task tồn đọng khi pool đóng hoặc không có worker nhận việc.

## 4. Các chiến lược backpressure chính

### 4.1. Bounded queue + AbortPolicy: từ chối có thể quan sát

`ArrayBlockingQueue` hoặc `LinkedBlockingQueue` với dung lượng hữu hạn đặt trần cho công việc đang chờ. `AbortPolicy` (mặc định của `ThreadPoolExecutor`) ném `RejectedExecutionException` khi executor không thể nhận task.

```java
// Fragment cấu hình: executor cần được shutdown khi không còn dùng.
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        2, 4,
        30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(100),
        new ThreadPoolExecutor.AbortPolicy());
```

Khi đã có 2 worker bận, task mới thường đi vào queue trước. Khi queue đầy, pool có thể tăng lên tối đa 4 worker. Sau đó, task mới bị từ chối. Với pool vừa tạo, thứ tự này giải thích vì sao đặt `maximumPoolSize=4` **không có nghĩa** pool tự mở 4 worker trước khi dùng queue.

Phù hợp khi producer hoặc tầng gọi có thể phản ứng với lỗi: trả “busy”, retry với giới hạn/thời gian lùi, chuyển sang kênh lưu bền vững hoặc ghi nhận tác vụ không được nhận. Đừng bắt exception rồi im lặng bỏ qua, vì như vậy đánh mất ưu điểm của chính sách này.

### 4.2. Bounded queue + CallerRunsPolicy: producer tự chịu chi phí

Khi quá tải **và executor vẫn đang chạy**, `CallerRunsPolicy` thực thi `Runnable.run()` ngay trên **thread đang gọi `execute`**. Producer phải chờ tác vụ đó hoàn tất trước khi nạp tiếp, nên tốc độ đầu vào bị kéo xuống một cách tự nhiên.

Phù hợp nếu caller được phép chạy tác vụ và chịu thời gian block. Cần thận trọng nếu caller là event loop, UI thread, một worker đang giữ lock hoặc thread đòi hỏi độ trễ phản hồi nghiêm ngặt: nó có thể bị chặn lâu hoặc phát sinh deadlock do phụ thuộc lẫn nhau.

Những giới hạn quan trọng:
- Khi executor **đã shutdown**, `CallerRunsPolicy` bỏ task; nó không thực thi fallback.
- Task chạy trên thread gọi, nên môi trường `ThreadLocal`, context, tên luồng và quản lý lỗi có thể khác worker.
- Nếu task chạy trực tiếp ném unchecked exception, đường truyền exception của `execute` có thể khác task chạy trên worker; không mặc định rằng mọi exception đều bị executor lưu lại.
- Khi producer là thread chính nhưng lại phải phục vụ nhiệm vụ ưu tiên cao, việc tự chạy task có thể tạo chậm trễ dây chuyền.

### 4.3. Semaphore: giới hạn số task outstanding trước khi gửi

`Semaphore` giữ một số **permit** hữu hạn. Producer phải lấy permit **trước khi gửi** task; permit được trả khi task hoàn tất. Nếu có 20 permit, thiết kế đang hướng tới tối đa 20 task đã được nhận nhưng chưa hoàn tất (bao gồm task đang chạy và đang chờ). Permit không tăng số worker; nó giới hạn lượng công việc mà ứng dụng cho phép tiếp nhận.

Ba cách phổ biến:

- `acquire()`: đợi cho tới khi có permit hoặc bị interrupt; cung cấp backpressure bằng blocking.
- `tryAcquire()`: trả `false` ngay khi không có permit; cung cấp fast-fail/admission rejection.
- `tryAcquire(timeout, unit)`: đợi tối đa một khoảng thời gian, rồi trả `false`; thường có ích khi có deadline.

Quy tắc quan trọng: **lấy permit → gửi task → trả permit khi task kết thúc; nếu gửi bị từ chối thì trả permit ngay**. Đưa `release()` vào `finally` của phần thực thi để tránh rò permit khi task ném exception. Với `AbortPolicy`, có thể xử lý `RejectedExecutionException` từ lời gọi `execute` để trả lại permit trước khi báo lỗi cho producer.

```java
// Fragment: dùng ThreadPoolExecutor với AbortPolicy;
// truyền permits và work vào từ nơi cấu hình.
static boolean trySubmit(ThreadPoolExecutor executor,
                         Semaphore permits,
                         Runnable work) {
    if (!permits.tryAcquire()) {
        return false; // Chưa gửi vào executor.
    }

    try {
        executor.execute(() -> {
            try {
                work.run();
            } finally {
                permits.release();
            }
        });
        return true;
    } catch (RejectedExecutionException rejected) {
        permits.release(); // Task không được executor nhận.
        return false;
    }
}
```

Ở ví dụ này, `false` có nghĩa hệ thống **không nhận task**: hoặc không có permit, hoặc executor từ chối. Khi cần báo rõ nguyên nhân, hãy trả về loại kết quả cụ thể hơn thay vì chỉ dùng boolean.

**Phân biệt hai giới hạn:** số permit nên được chọn theo khả năng chấp nhận task của hệ thống, không nhất thiết phải bằng `maximumPoolSize + queueCapacity`. Ngay cả khi hai giới hạn bằng nhau, semaphore không bảo đảm 100% rằng `execute` không thể bị từ chối: shutdown, producer không dùng cùng semaphore, hoặc chuyển giao permit sát thời điểm worker kết thúc đều có thể tạo race. Vẫn cần handler và hoàn permit khi rejection.

### 4.4. Giới hạn tổng tải trước khi tạo executor task

Có thể đặt `Semaphore` ở đầu pipeline thay vì ngay trước pool. Producer nhận tín hiệu không còn công suất **trước khi tạo một lượng lớn object/task**, đọc dữ liệu hoặc phát lời gọi phụ. Nếu dữ liệu đầu vào phải được lưu bền vững, áp dụng admission tại ranh giới thích hợp và thiết kế nơi nhận lại các công việc chưa xử lý. Đừng tạo vô hạn producer thread chỉ để chúng block trên `acquire()`: như vậy chuyển sự tích tụ từ queue sang tập thread đang chờ.

### 4.5. Giảm tải có chủ đích (load shedding)

Nếu công việc không bắt buộc thực hiện đầy đủ, producer có thể:
- Bỏ mẫu/sự kiện không thiết yếu và tăng metric dropped.
- Gộp các cập nhật trùng khóa, chỉ giữ trạng thái mới nhất (coalescing).
- Áp dụng deadline: không nhận task đã hết giá trị trước khi bắt đầu.
- Ưu tiên công việc thiết yếu; tách pool/capacity giữa các loại workload để giảm ảnh hưởng lẫn nhau.

`DiscardPolicy` âm thầm bỏ task mới, còn `DiscardOldestPolicy` bỏ phần tử đầu queue rồi thử nộp task mới. Hai chính sách này không phải backpressure đáng tin cậy cho công việc cần phản hồi: `submit()` có thể trả một `Future` chưa bao giờ hoàn tất khi task bị bỏ. `DiscardOldestPolicy` cũng có rủi ro retry lặp lại với queue không lưu phần tử như `SynchronousQueue`. Thường nên thiết kế việc “thay task cũ” và phản hồi/hủy `Future` một cách tường minh.

## 5. Ví dụ Java 17 chạy độc lập: hai cách kiểm soát tải

Chương trình minh họa:

1. **`CallerRunsPolicy`** với 1 worker và queue 1 chỗ: A đang chạy, B đang chờ, C sẽ phải chạy trên `main`.
2. **`Semaphore(2)` + `AbortPolicy`**: chỉ chấp nhận 2 task outstanding. Khi A đang chạy và B đã nhận, task C bị chặn ở bước admission (`tryAcquire() == false`), chưa đưa vào executor.

`CountDownLatch` giữ A đang chạy để tạo trạng thái quá tải **có kiểm soát**, tránh phụ thuộc `Thread.sleep` và tốc độ máy. Lưu tệp `BackpressureDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BackpressureDemo {
    public static void main(String[] args) throws Exception {
        callerRunsExample();
        semaphoreExample();
    }

    private static void callerRunsExample() throws Exception {
        List<String> events =
                Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy());

        try {
            pool.execute(() -> {
                events.add("A running");
                started.countDown();
                awaitUninterruptiblyForDemo(releaseA);
                events.add("A done");
            });

            started.await(); // A chiếm worker.
            pool.execute(() -> events.add("B done")); // B trong queue.
            events.add("before C");
            pool.execute(() ->
                    events.add("C on " + Thread.currentThread().getName()));
            events.add("after C");
        } finally {
            releaseA.countDown();
            pool.shutdown();
        }

        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new AssertionError("CallerRuns pool did not terminate");
        }
        System.out.println("CallerRuns: " + String.join(" | ", events));
    }

    private static void semaphoreExample() throws Exception {
        List<String> events =
                Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        Semaphore permits = new Semaphore(2);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        try {
            boolean a = trySubmit(pool, permits, () -> {
                events.add("A running");
                started.countDown();
                awaitUninterruptiblyForDemo(releaseA);
                events.add("A done");
            });
            started.await(); // A chạy, chiếm permit đầu tiên.

            boolean b = trySubmit(
                    pool, permits, () -> events.add("B done"));
            boolean c = trySubmit(
                    pool, permits, () -> events.add("C done"));

            events.add("accepted=" + a + "," + b + "," + c);
        } finally {
            releaseA.countDown();
            pool.shutdown();
        }

        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Semaphore pool did not terminate");
        }
        System.out.println("Semaphore: " + String.join(" | ", events));
    }

    // Dùng với AbortPolicy: rejection tại execute() nghĩa là không nhận task.
    private static boolean trySubmit(ThreadPoolExecutor pool,
                                     Semaphore permits,
                                     Runnable work) {
        if (!permits.tryAcquire()) {
            return false;
        }
        try {
            pool.execute(() -> {
                try {
                    work.run();
                } finally {
                    permits.release();
                }
            });
            return true;
        } catch (RejectedExecutionException rejected) {
            permits.release();
            return false;
        }
    }

    // Chỉ phục vụ demo: giữ trạng thái A cho tới khi main mở latch.
    private static void awaitUninterruptiblyForDemo(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac BackpressureDemo.java
java BackpressureDemo
```

Stdout:

```text
CallerRuns: A running | before C | C on main | after C | A done | B done
Semaphore: A running | accepted=true,true,false | A done | B done
```

Trong nhánh đầu, `execute(C)` không xếp được vào queue và `CallerRunsPolicy` thực thi C trên `main` trước khi lệnh gửi việc kết thúc. Trong nhánh sau, C không có permit nên `trySubmit` trả `false` mà không đưa C vào queue. Hai nhánh chứng minh hai vị trí khác nhau của backpressure: **ngay trong handler sau saturation** và **trước lúc gửi task vào pool**.

Hàm đợi latch trong demo cố tình khôi phục interrupt flag sau khi hoàn tất chờ để chứng minh thứ tự sự kiện. Code sản phẩm thường phải có đường xử lý interrupt/shutdown phù hợp, không nên sao chép cơ chế đợi không ngắt này cho một worker thực tế.

## 6. Blocking admission có timeout và interruption

Đối với tác vụ yêu cầu chờ chỗ một thời gian nhất định, có thể thay `permits.tryAcquire()` bằng:

```java
// Fragment trong một method có throws InterruptedException:
boolean admitted = permits.tryAcquire(200, TimeUnit.MILLISECONDS);
if (!admitted) {
    // Quá hạn chờ nhận việc: báo busy/deadline exceeded.
    return;
}
try {
    executor.execute(() -> {
        try {
            work.run();
        } finally {
            permits.release();
        }
    });
} catch (RejectedExecutionException rejected) {
    permits.release();
    throw rejected;
}
```

Đoạn này giả định `executor` được cấu hình `AbortPolicy`, `permits` và `work` đã khởi tạo. Nếu bị interrupt **trong khi chờ permit**, `tryAcquire(timeout, unit)` ném `InterruptedException` và **không có permit nào cần hoàn lại**. Phương thức gọi thường nên truyền tiếp interrupt hoặc xử lý theo hợp đồng cancellation của ứng dụng; không âm thầm nuốt interrupt rồi chờ mãi.

Một producer có thể đặt deadline tổng thể và trừ đi thời gian đã tiêu tốn để tránh thời gian chờ admission + thời gian chạy vượt quá hạn định. Nếu producer timeout sau khi task đã được executor nhận, cần có chiến lược hủy/đối chiếu trạng thái riêng: timeout ở phía producer **không tự dừng worker**.

`Semaphore` mặc định không bảo đảm fairness. `new Semaphore(n, true)` ưu tiên thứ tự chờ theo cơ chế fairness của semaphore, đổi lấy chi phí/throughput có thể khác; các lệnh `tryAcquire()` không timeout có thể giành permit ngay cả khi đã có luồng chờ trong semaphore fair. Nếu thực sự cần tuân thủ hàng chờ công bằng, chọn API và kiểm thử semantics tương ứng; fairness không thay thế hạn chờ.

## 7. Thiết kế permit không bị rò rỉ

Mẫu `trySubmit` ở trên thích hợp để dạy về đường **nhận–chạy–hoàn tất** khi dùng `AbortPolicy` và `execute`. Trong hệ thống thật, cần xem xét vòng đời task đầy đủ:

- **Exception từ task:** `finally` trong wrapper vẫn trả permit khi `Runnable.run()` ném lỗi.
- **Rejection trước khi nhận:** phải trả permit tại luồng nộp task, nếu không capacity sẽ giảm vĩnh viễn.
- **`shutdown()`:** từ chối task mới nhưng các task đã nhận được chạy tiếp theo trình tự shutdown có kiểm soát; permit được trả khi chúng chạy xong.
- **`shutdownNow()`:** có thể trả về danh sách task **chưa bắt đầu** trong queue. Wrapper của chúng chưa chạy, nên `finally` của wrapper **không được gọi** và permit vẫn bị chiếm. Cần đối chiếu các task bị lấy ra và hoàn lại permit/hủy trạng thái một lần; đồng thời phối hợp interrupt cho task đang chạy.
- **`remove(runnable)`, `purge()`, hủy Future/đường hủy tùy biến:** nếu task không bao giờ chạy, cơ chế chỉ trả permit khi task chạy sẽ rò permit. Nếu dùng `submit`, executor có thể bọc task thành `FutureTask`; việc gắn ownership và cleanup cần được thiết kế nhất quán.
- **Đường chuyển sang CallerRuns:** nếu đổi handler thành `CallerRunsPolicy`, task có thể chạy ngay trên luồng gọi; việc ném `RejectedExecutionException` từ **bên trong task** có thể khiến đoạn `catch` của producer hiểu nhầm là executor từ chối và release permit **hai lần**. Không dùng máy móc wrapper dành cho `AbortPolicy` khi thay handler.

Để production hóa vòng đời nhiều trường hợp, dùng token/handle trạng thái có thao tác hoàn permit **đúng một lần** (ví dụ `AtomicBoolean.compareAndSet(false, true)`), cơ chế đăng ký task trước khi enqueue và đường cleanup cho queued task bị hủy. Nếu không thể chứng minh được toàn bộ đường kết thúc, đừng tuyên bố rằng semaphore bao được mọi trường hợp.

## 8. Backpressure khác rate limiting như thế nào?

**Rate limiting** giới hạn số request hoặc task đến trong một khoảng thời gian (ví dụ token bucket 100/s). **Concurrency limiting** giới hạn lượng công việc đồng thời. **Queue bounding** giới hạn số task đang đợi. **Backpressure** là cơ chế để phản ứng khi tài nguyên phía sau không theo kịp: block, trả tín hiệu chậm lại, timeout hoặc reject.

Một hệ thống có thể cần cả ba loại giới hạn: rate limit để kiềm chế đỉnh đến, semaphore để tránh quá nhiều tác vụ đang tồn tại, queue hữu hạn để tránh tích tụ bộ nhớ. Có rate limit nhưng tác vụ xử lý chậm bất ngờ thì vẫn có thể backlog. Có queue hữu hạn nhưng caller tiếp tục retry không giới hạn thì có thể tạo áp lực ngược lên hệ thống bên ngoài.

Theo định luật Little ở **trạng thái ổn định**, quan hệ trung bình `L = λ × W` liên hệ số công việc trong hệ (`L`), thông lượng (`λ`) và thời gian lưu trong hệ (`W`). Ví dụ throughput 100 task/giây và thời gian tồn tại trung bình 0,5 giây tương ứng khoảng 50 task đang tồn tại trung bình. Đây là **quan hệ quan sát tại trạng thái ổn định**, không phải công thức đặt capacity tuyệt đối cho burst, timeout, tài nguyên hạn chế hay overload kéo dài.

Đối với workload CPU-bound, tăng worker quá số lõi khả dụng có thể làm tranh CPU và context switch nhiều hơn. Với I/O-bound, số tác vụ chờ I/O có thể lớn hơn, nhưng phải xét connection pool, memory, giới hạn bên ngoài và deadline. Dung lượng queue phụ thuộc mức burst muốn hấp thụ và ngân sách độ trễ, không nên chọn tùy ý quá lớn.

## 9. Quan sát, kiểm thử và vận hành

### Chỉ số nên theo dõi

- **Admission:** số task gửi, nhận, từ chối, timeout chờ permit, bị drop; phân loại rejection do shutdown và saturation khi có thể.
- **Độ sâu queue:** `getQueue().size()`, queue capacity, xu hướng tăng và thời gian chờ task trước khi bắt đầu.
- **Pool:** `getActiveCount()`, `getPoolSize()`, `getCompletedTaskCount()`, thời gian xử lý và bão hòa thực tế.
- **Producer:** thời gian bị chặn do semaphore/`CallerRunsPolicy`, số producer đang chờ, số timeout phía caller.
- **End-to-end:** độ trễ p95/p99, lỗi, request bị hủy, độ tuổi task khi được thực thi và số công việc còn tồn đọng trong pipeline.

Các getter của `ThreadPoolExecutor` là số liệu tức thời hoặc gần đúng khi chạy concurrent, hữu ích cho monitoring nhưng không dùng làm điều kiện kiểm soát nhận việc kiểu đọc rồi gửi. Mọi nhánh drop/rejection cần metric hoặc tín hiệu rõ ràng; nếu chỉ đo queue size có thể bỏ sót công việc đã bị loại.

### Tình huống kiểm thử cần có

1. Một worker bận + queue đầy: xác nhận pool kích hoạt handler mong đợi.
2. Producer nộp nhanh trong thời gian dài: xác nhận queue và số task outstanding không tăng vượt mục tiêu; độ trễ còn chấp nhận.
3. Task ném exception: permit phải quay lại.
4. Chờ permit bị interrupt/timeout: không nộp task, không release nhầm.
5. Shutdown và shutdownNow: xác nhận nơi nào trả task chưa chạy, task nào được hủy, permit và Future nào cần cleanup.
6. Nhiều producer cạnh tranh: kiểm tra race admission, retry có giới hạn, không tạo duplicate hoặc lost task ngoài hợp đồng.
7. Task phụ thuộc kết quả của task khác cùng pool: kiểm tra deadlock, nhất là khi `CallerRunsPolicy` hoặc producer đang giữ khóa.

Có thể dùng `CountDownLatch` như ví dụ để tạo trạng thái queue đầy một cách xác định; `Thread.sleep` đơn lẻ không bảo đảm task nào đang chạy hoặc đang chờ. Load test thực tế cần có burst, tốc độ tăng giảm, service time biến động và downstream chậm.

## 10. Lỗi thiết kế thường gặp

| Sai lầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| Chỉ tăng `LinkedBlockingQueue` rất lớn để né rejection | Backlog và độ trễ/bộ nhớ tăng âm thầm | Đặt capacity theo ngân sách độ trễ và giới hạn tổng tải |
| Cho rằng queue hữu hạn tự block `execute` | Khi đầy pool có thể ném `RejectedExecutionException` hoặc gọi handler | Đặt cơ chế backpressure cụ thể: abort/CallerRuns/semaphore |
| Chỉ kiểm tra `queue.size() < limit` trước khi gửi | Check-then-act race dưới nhiều producer | Dùng `Semaphore.tryAcquire`/`acquire` hoặc cơ chế admission nguyên tử |
| Dùng `CallerRunsPolicy` cho luồng không được block | Luồng gọi bị chiếm, tăng latency hoặc deadlock | Chọn fast-fail, timeout hoặc chuyển admission sang boundary phù hợp |
| Nuốt `RejectedExecutionException` | Mất việc nhưng không có tín hiệu | Ghi metric và phản hồi/retry có giới hạn/lưu bền vững |
| Dùng `DiscardPolicy` với `submit` rồi chờ `Future.get()` | Future có thể không hoàn tất bao giờ | Dùng chính sách rõ lỗi hoặc quản lý hủy Future |
| `release()` chỉ nằm trong task, bỏ qua reject/cancel queued task | Rò permit, hệ thống ngừng nhận việc sau một thời gian | Bảo đảm cleanup đúng một lần trên mọi đường kết thúc |
| Retry liên tục trên cùng pool đang bão hòa | Tăng áp lực CPU, tạo vòng lặp và khiến overload kéo dài | Backoff có jitter, budget retry, deadline hoặc báo busy |
| Tăng tối đa số thread mà không xét downstream | Tranh tài nguyên, latency cao, lỗi hàng loạt | Đo bottleneck, giới hạn concurrency theo tài nguyên thật |

## 11. Câu hỏi tự kiểm tra

**1. Một pool 2 core, 4 maximum, queue 10 chỗ. Khi có 2 worker bận và queue còn trống, executor ưu tiên mở worker thứ 3 không?**  
Thông thường không; `execute` ưu tiên enqueue sau khi đã đạt core, rồi mới thêm worker khi queue không nhận task.

**2. Queue hữu hạn có tự làm producer chờ không?**  
Không. `ThreadPoolExecutor.execute` thử nhận bằng queue rồi có thể tăng worker hoặc gọi rejection handler; cần `CallerRunsPolicy`, semaphore hoặc admission khác nếu muốn làm chậm producer.

**3. Semaphore(20) giới hạn chính xác điều gì?**  
Giới hạn số permit được cấp đồng thời. Nếu mọi producer đều lấy permit trước khi gửi và release đúng một lần khi task kết thúc, nó biểu diễn tối đa 20 task outstanding theo hợp đồng đó. Nó không tự giới hạn những task bỏ qua semaphore và không bảo đảm pool luôn nhận.

**4. Vì sao shutdownNow có thể làm rò permit?**  
Task chưa chạy bị lấy khỏi queue sẽ không đi vào wrapper có `finally { permits.release(); }`. Caller cần xử lý danh sách task chưa bắt đầu và cơ chế cleanup.

**5. Backpressure có nhất thiết là chặn producer không?**  
Không. Nó có thể là chạy công việc trên caller, trả lỗi bận, chờ có timeout, giảm tải hoặc chuyển sang kênh có khả năng tiếp nhận riêng. Chọn hành vi dựa trên giá trị công việc, deadline, tính bền vững và tác động tới producer.

## Tài liệu tham khảo

- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java SE 17 — BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)
- [Java SE 17 — Semaphore](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Semaphore.html)
- [Java SE 17 — RejectedExecutionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-014">Phân biệt user task concurrency với thread pool concurrency</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt user task concurrency với thread pool concurrency

Trong Java, **số yêu cầu người dùng còn đang xử lý** (*user task/request concurrency*) và **số tác vụ đang được worker trong một thread pool thực thi** (*thread pool concurrency*) là hai đại lượng **khác nhau**. Một hệ thống có thể có 200 yêu cầu người dùng in-flight nhưng một `ExecutorService` chỉ có 8 worker threads. Ngược lại, một yêu cầu có thể tách thành 20 subtasks rồi submit vào một executor, khiến số task lớn hơn số requests.

Sai lầm “100 concurrent users thì cần pool 100 threads” có thể dẫn tới quá nhiều threads, queue tích tụ, áp lực CPU/memory, tăng latency hoặc nghẽn ở database. Hiểu đúng cần phân biệt **business requests**, **submitted/accepted tasks**, **queued tasks**, **executing tasks**, **worker threads** và **CPU parallelism**. Các khái niệm này liên quan nhưng **không thể thay thế**.

Bài sử dụng Java concurrency nói chung với Java 17+, không phụ thuộc framework hay thành phần concurrency riêng của Spring.

## 1. Định nghĩa user task concurrency

“User task concurrency” không phải tên một thuộc tính chính thức trong `ExecutorService` API. Ở đây nó biểu diễn **số công việc theo nghĩa nghiệp vụ đang tồn tại cùng lúc**, do chương trình quyết định cách đếm.

Một **request/workflow** có thể được tính là in-flight từ khi được chấp nhận tới khi đã phản hồi hoặc hoàn tất công việc cuối cùng, tùy contract. Trong khoảng đó request có thể:

- Đang chạy CPU code.
- Đang đợi queue có worker trống.
- Đang chờ I/O, database, network hoặc một asynchronous result.
- Đang chia ra nhiều tác vụ con rồi chờ tập hợp kết quả.
- Đã timeout ở caller nhưng background job vẫn chạy, nếu ứng dụng chưa hủy được job.
- Trả response sớm và giao việc nền riêng: request đã xong nhưng job nền vẫn in-flight.

**User đang online** không phải **request đang hoạt động**. Một user có thể gửi nhiều requests song song hoặc không gửi request nào. Nhiều users cũng có thể dùng chung một batch job. Nếu hệ thống cần theo dõi “user concurrency”, phải nói rõ **đếm sessions, requests, workflows hay background jobs**.

### Ranh giới in-flight cần nhất quán

Ví dụ một request bắt đầu lúc nhận input, kết thúc khi đã trả response:

```text
Request received
    -> validate
    -> submit task A
    -> queue wait
    -> task A runs
    -> wait result
    -> response sent
Request completed
```

Nếu business workflow tiếp tục sau response, cần metric **background jobs in-flight** riêng. Nếu request tạo subtasks song song, không cộng số subtasks vào số users rồi gọi tất cả là “concurrent users”.

## 2. Định nghĩa thread pool concurrency

`ThreadPoolExecutor` quản lý một tập workers, thực thi `Runnable` hoặc `Callable` đã được chấp nhận. Tại một thời điểm, các tasks có thể:

1. **Accepted**: executor đã nhận, chưa chắc được chạy ngay.
2. **Queued**: đang đợi trong `BlockingQueue<Runnable>` của pool.
3. **Executing**: worker đang chạy `run()`/`call()`.
4. **Completed/failed/cancelled**: trạng thái lifecycle theo contract cụ thể.

Đây là **nhãn để lý giải**, không phải một enum task-state của JDK. Một task cancelled có thể chưa bao giờ bắt đầu; cancellation của `Future` cũng có thể được ghi nhận trước khi worker đã ra khỏi `finally`. Vì vậy phải xác định **thời điểm hoàn thành** rõ ràng khi đo.

Trong fixed pool có 4 platform workers, **tối đa 4 task bodies** thường được thực thi cùng lúc **bởi chính những worker ấy**. Nhưng số task accepted có thể là 1.000 vì 996 tasks đang đợi trong queue. Nếu một worker đang chờ API từ xa, nó vẫn **bị chiếm bởi task** và chưa thể chạy task khác, dù CPU có thể nhàn.

**Cẩn trọng:** `CallerRunsPolicy` hoặc code tự tạo thread ngoài pool có thể khiến task body được thực thi **ngoài worker pool**. Do đó phải phân biệt “tối đa số task bodies trên pool workers” với “tổng số task bodies cùng chạy trên mọi threads trong ứng dụng”.

## 3. Phân biệt concurrency và parallelism

| Khái niệm | Câu hỏi | Điều không nên suy ra |
| --- | --- | --- |
| **Concurrent business requests** | Có bao nhiêu workflow người dùng chưa kết thúc? | Không bằng số Java threads |
| **Outstanding executor tasks** | Có bao nhiêu task đã accepted nhưng chưa hoàn tất? | Không bằng số active workers |
| **Queued tasks** | Bao nhiêu task chưa có worker? | Không phải CPU work đang chạy |
| **Active pool workers** | Worker nào đang thực thi task body? | Không bảo đảm worker đang sử dụng CPU |
| **CPU parallelism** | Bao nhiêu phép tính đang thực sự diễn ra trên các cores? | Không bằng tổng threads hay futures |
| **Asynchrony** | Caller có thể tiếp tục mà chưa đợi kết quả? | Không có nghĩa supplier bên trong không blocking |

Một task có thể đang **executing** nhưng `Thread.sleep`, `Future.get`, `CountDownLatch.await` hoặc chờ I/O. Worker bị giữ nhưng không làm CPU computation. Một `CompletableFuture.supplyAsync` trả Future ngay cho caller, nhưng supplier có thể thực thi synchronous blocking I/O trên worker.

## 4. Bốn con số điển hình trong một pool

Giả sử người gọi submit 6 tasks vào `ThreadPoolExecutor` có đúng 2 workers và queue đủ 4 slots. Hai tasks chạy/chờ trên workers; 4 tasks xếp hàng:

```text
                  Executor (pool of 2)
                       |
submit 6 tasks --------+
                       |
                 +-----+------+
                 |            |
              Worker 1     Worker 2
               Task A       Task B
                 |            |
                 +-----+------+
                       |
             queue: [C, D, E, F]

Accepted tasks  = 6
Executing tasks = 2
Queued tasks    = 4
Workers in pool = 2
```

Nếu **mỗi task đến từ một request riêng**, có thể có 6 requests in-flight. Nếu **một request nộp cả 6 tasks**, chỉ có 1 request, 6 executor tasks và 2 executing workers.

Một phép tính kế toán theo định nghĩa tự thiết kế:

```text
outstanding accepted tasks = accepted tasks - actually completed tasks
roughly: outstanding ≈ queued + executing
```

Trong production, các metric thường là snapshot khác thời điểm, còn cancellation, rejection, task stealing, transfer và caller-runs làm cách đếm thay đổi. Vì vậy đây là **mô hình lý giải**, không phải invariant luôn áp dụng chính xác cho mọi executor getter.

## 5. Java 17: đo 6 accepted, 2 executing, 4 queued

Thay vì dùng `Thread.sleep(100)` để đoán “hai tasks đang chạy”, chương trình dưới dùng `CountDownLatch` chặn hai workers tại checkpoint xác định. Main chụp số liệu **trước** khi mở cổng.

Tạo tệp **`TaskVsPoolConcurrencyDemo.java`**:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskVsPoolConcurrencyDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch twoStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executing = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(4));

        int accepted = 6;
        try {
            for (int i = 0; i < accepted; i++) {
                pool.execute(() -> {
                    executing.incrementAndGet();
                    twoStarted.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        executing.decrementAndGet();
                        completed.incrementAndGet();
                    }
                });
            }

            if (!twoStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers not ready");
            }

            System.out.println("accepted=" + accepted);
            System.out.println("executing=" + executing.get());
            System.out.println("queued=" + pool.getQueue().size());
            System.out.println(
                    "inFlight=" + (accepted - completed.get()));
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new IllegalStateException("Pool did not stop");
            }
        }
        System.out.println("completed=" + completed.get());
    }
}
```

Biên dịch/chạy JDK 17+:

```bash
javac TaskVsPoolConcurrencyDemo.java
java TaskVsPoolConcurrencyDemo
```

Stdout **khi chương trình hoàn thành bình thường trước timeout**:

```text
accepted=6
executing=2
queued=4
inFlight=6
completed=6
```

**Vì sao có thể dự đoán kết quả?** Hai task đầu làm `executing++`, báo `twoStarted`, rồi chờ `release`. Vì cả hai workers đang chờ latch, bốn task còn lại **không thể được lấy từ queue** trước khi main in số liệu. Main sau đó mở `release` trong `finally`, gọi `shutdown` rồi `awaitTermination`. Chỉ sau khi tất cả sáu tasks hoàn thành, nó in `completed=6`.

Lưu ý rằng `executing=2` ở đây là “hai task bodies đã bắt đầu và chưa kết thúc”; thực tế cả hai đang **WAITING** trên latch, không dùng CPU để xử lý business logic. Các getter giám sát như `getActiveCount()` và `getQueue().size()` có thể là snapshot đổi ngay trong production, nhưng test này cố ý giữ state ổn định để giải thích khái niệm.

Đây là thí nghiệm điều phối, **không phải benchmark throughput**.

## 6. Fan-out: một request có thể sinh nhiều executor tasks

Ví dụ ba requests cùng cần hai tác vụ con:

```text
User request A --> subtask A1 + subtask A2
User request B --> subtask B1 + subtask B2
User request C --> subtask C1 + subtask C2

Business requests = 3
Submitted subtasks = 6
Fixed pool workers = 2
```

Tùy scheduler và thời gian thực thi, chỉ một phần subtasks được worker xử lý ở cùng thời điểm. Nếu requests tạo 10 subtasks mỗi lần và có 500 requests đang in-flight, workload có thể tạo tới 5.000 subtasks outstanding, **không có nghĩa pool có 5.000 threads**.

### Java 17: CompletableFuture fan-out, fan-in

Tạo `FanOutExample.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FanOutExample {
    public static void main(String[] args) throws Exception {
        int requests = 3;
        int subtasksPerRequest = 2;
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<CompletableFuture<Integer>> futures =
                    new ArrayList<>();

            for (int r = 0; r < requests; r++) {
                for (int part = 0; part < subtasksPerRequest; part++) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> 1, pool));
                }
            }

            CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture<?>[0])
            ).join();

            int sum = futures.stream()
                    .mapToInt(CompletableFuture::join)
                    .sum();

            System.out.println("requests=" + requests);
            System.out.println("subtasks=" + futures.size());
            System.out.println("sum=" + sum);
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new IllegalStateException("Pool did not stop");
            }
        }
    }
}
```

Chạy:

```bash
javac FanOutExample.java
java FanOutExample
```

Stdout:

```text
requests=3
subtasks=6
sum=6
```

Mỗi subtask trả một `int` bằng 1, và main tổng hợp sau khi `allOf` hoàn thành. `join()` ở đây nằm ở **main**, không ở worker của chính pool đó; các workers vẫn rảnh để chạy các subtasks còn lại. `requests=3` là **số request mô phỏng**, không phải chỉ số đo từ server thật hay khẳng định cả ba cùng chiếm worker.

**Điểm cần nhớ:** `CompletableFuture` mang tính composition/asynchrony, không mặc định supplier không blocking. Nếu supplier gọi HTTP đồng bộ hay `Thread.sleep`, nó vẫn chiếm một worker trong khoảng chờ.

## 7. Cấu hình `ThreadPoolExecutor` quyết định task chạy hay queue

Constructor phổ biến:

```java
new ThreadPoolExecutor(
    corePoolSize,
    maximumPoolSize,
    keepAliveTime,
    TimeUnit.MILLISECONDS,
    workQueue,
    rejectionHandler
);
```

Đây là snippet chưa khai báo biến/nhập số liệu. Với `ThreadPoolExecutor` thông thường:

1. Nếu số worker ít hơn `corePoolSize`, pool có thể tạo worker để chạy task mới.
2. Khi đã có đủ core workers, pool thử **đưa task vào queue**.
3. Nếu queue không nhận thêm và số workers chưa đạt `maximumPoolSize`, pool có thể tạo thêm worker.
4. Nếu cả worker/queue đều không thể nhận, rejection handler được gọi.

Điều này rất quan trọng: **maximumPoolSize không phải số threads chắc chắn sẽ được tạo ngay**. Với **unbounded queue**, sau khi core workers đã có, pool thường xếp hàng, không cần mở rộng tới maximum.

### Factory methods thường gặp

| API | Mô hình | Nguy cơ/giới hạn |
| --- | --- | --- |
| `Executors.newFixedThreadPool(n)` | n platform workers, các task dư thường chờ trong unbounded queue | Queue có thể phình và gây long latency/memory pressure |
| `Executors.newSingleThreadExecutor()` | Chạy tasks tuần tự | Một task chậm làm tất cả đợi |
| `Executors.newCachedThreadPool()` | Tạo thêm workers khi demand tăng, có tái sử dụng | Số threads có thể tăng mạnh nếu không giới hạn admission |
| `Executors.newScheduledThreadPool(n)` | Chạy delayed/periodic tasks | Nhiều scheduled items không đồng nghĩa nhiều workers |
| `ThreadPoolExecutor` custom | Chọn core/max, bounded queue, rejection | Phải quản lý saturation/backpressure rõ |
| `newVirtualThreadPerTaskExecutor()` (Java 21+) | Một virtual thread cho mỗi task | Không có trên Java 17; tài nguyên downstream vẫn hữu hạn |

**Không nên chọn factory chỉ từ tên API**. Cần biết số lượng tasks, thời gian CPU/blocking, mức burst, queue semantics và giới hạn nghiệp vụ.

## 8. Vì sao tăng `maximumPoolSize` có thể không cải thiện latency?

Ví dụ `corePoolSize=4`, `maximumPoolSize=32`, queue capacity rất lớn. Khi có bốn workers, task tiếp theo thường **được queue**. Chỉ khi queue đầy mới có thể kích hoạt tạo workers tới maximum. Vì vậy tăng maximum nhưng để queue không bao giờ đầy có thể không tăng concurrency thực tế.

Ngược lại, cấu hình pool với queue rất nhỏ có thể tạo thêm workers nhanh hơn tới maximum nhưng **tăng tải đồng thời vào downstream**. Nếu DB chỉ cho 10 connections, tạo 100 workers I/O cũng không làm DB xử lý 100 query cùng lúc; các workers còn có thể xếp hàng chờ connection, tốn memory/context switching.

**Pool sizing không phải bài toán một tham số.** Phải xét:

- CPU cores và thời gian compute.
- Thời gian chờ I/O/lock.
- Queue waiting latency.
- Tốc độ submit và burst.
- Connection pool, network, DB rate limit.
- Memory/stack và chi phí quản lý threads.
- Timeout, cancellation, SLA.
- Chính sách khi full: reject, caller-runs, load shedding.

## 9. Khi queue đầy: rejection và backpressure

Một fixed pool hữu hạn không thể nhận vô hạn tasks mà không trả giá. **Backpressure** buộc producer giảm tốc độ, trì hoãn hoặc từ chối công việc khi downstream không đủ khả năng phục vụ.

Một số chiến lược:

- **`AbortPolicy`**: ném `RejectedExecutionException` khi saturation, caller tự xử lý.
- **`CallerRunsPolicy`**: khi phù hợp, task chạy trên thread **đang gọi submit/execute** thay vì pool worker, làm caller chậm lại.
- **`DiscardPolicy`**: bỏ task; nguy hiểm nếu job bắt buộc hoàn tất hoặc có dữ liệu quan trọng.
- **`DiscardOldestPolicy`**: loại task đang chờ lâu nhất rồi thử nhận task mới, có thể phá fairness hoặc mất dữ liệu.
- **Semaphore admission**: giới hạn số requests/jobs in-flight **trước** khi submit vào pool.
- **Bounded queue**: bảo vệ heap khỏi backlog vô hạn và tạo điểm áp dụng policy rõ ràng.

**Quan trọng với CallerRuns:** pool có thể chỉ có 4 workers, nhưng caller **cũng đang chạy một task body**, nên toàn ứng dụng có thể có hơn 4 tasks đang thực thi. Điều này không làm pool tự có worker thứ năm. Nếu caller là event-loop thread không được block, policy này có thể làm hỏng latency toàn hệ thống. Khi executor shutdown, CallerRunsPolicy không có lời hứa luôn thực thi task.

## 10. Java 17: một worker, một queue slot, một task bị từ chối

Tạo **`PoolSaturationExample.java`**:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PoolSaturationExample {
    public static void main(String[] args) throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        Runnable task = () -> {
            firstStarted.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                completed.incrementAndGet();
            }
        };

        int accepted = 0;
        int rejected = 0;
        try {
            pool.execute(task);
            accepted++;

            if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker not started");
            }

            pool.execute(task);
            accepted++;

            try {
                pool.execute(task);
                accepted++;
            } catch (RejectedExecutionException expected) {
                rejected++;
            }

            System.out.println("accepted=" + accepted);
            System.out.println("queued=" + pool.getQueue().size());
            System.out.println("rejected=" + rejected);
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new IllegalStateException("Pool did not stop");
            }
        }
        System.out.println("completed=" + completed.get());
    }
}
```

```bash
javac PoolSaturationExample.java
java PoolSaturationExample
```

Stdout khi chương trình hoàn tất bình thường:

```text
accepted=2
queued=1
rejected=1
completed=2
```

Giải thích: một worker chạy task đầu rồi chờ `release`; task thứ hai nằm trong queue capacity 1. Task thứ ba không còn chỗ nên `AbortPolicy` ném exception. Main chỉ đo queue khi latch chưa mở; sau đó cho hai accepted tasks hoàn tất và đợi executor terminate.

Không có `Thread.sleep` để hy vọng tạo ra saturation; thứ tự được bảo đảm bởi latch. Đây là **thí nghiệm API**, không chứng minh hiệu năng pool trong tải thực.

## 11. User concurrency vượt pool concurrency không nhất thiết là lỗi

Có thể chấp nhận hàng trăm requests outstanding khi:

- Một phần lớn requests chờ kết quả async mà không giữ platform worker.
- Có queue bounded và hạn SLA rõ.
- Downstream đủ sức xử lý ở tốc độ đến trung bình.
- Backpressure/rejection được thiết kế để tránh backlog vô hạn.
- Mỗi request giới hạn fan-out và total in-flight tasks.

Ngược lại, **ít concurrent requests vẫn có thể gây overload** nếu mỗi request tạo hàng trăm subtasks, I/O chậm, retry không giới hạn hoặc tasks giữ nhiều memory. Chỉ đếm số requests không đủ; cần đếm workloads được sinh ra và tài nguyên chúng đang chờ.

### Đơn vị đo phải rõ

“Active=100” là **100 requests** hay **100 executor tasks**, **100 threads**, **100 DB connections**, **100 subscriptions**? Cùng số 100 nhưng thiết kế capacity hoàn toàn khác.

## 12. CPU-bound và I/O-bound

**CPU-bound:** task hầu hết dùng CPU. Thêm quá nhiều workers hơn số core có ích có thể tăng context switches và cache misses, làm throughput giảm. Một pool compute nên được sizing dựa trên core và mức contention, rồi benchmark thực tế.

**I/O-bound:** task chủ yếu chờ network/database/file. Có thể sử dụng nhiều workers hơn số cores vì threads waiting không dùng CPU liên tục. Tuy nhiên giới hạn connection pool, bandwidth và remote API vẫn quyết định concurrency hữu ích. Thêm workers không sửa được downstream đã quá tải.

**Mixed workload:** parsing CPU và gọi I/O dài trong cùng pool có thể gây interference. Nếu cần, tách các stages hoặc executor theo workload, nhưng tổng thread/process và queue vẫn phải được quản lý; không tạo nhiều pool lớn tùy ý.

**Không đồng nhất `Thread.State.RUNNABLE` với “đang dùng CPU”.** Thread có thể runnable mà chưa được scheduler chọn; các trạng thái Java và OS cũng không giống hệt nhau. Dùng profiler/JFR để phân biệt CPU, lock contention và blocking I/O.

## 13. Nested submission: thread-starvation deadlock

Một lỗi đặc biệt khó phát hiện là task A đang chiếm worker của pool, submit task B **vào cùng pool**, rồi chặn bằng `Future.get()` để đợi B. Nếu tất cả workers đều làm như vậy, không còn ai chạy B.

**Snippet sai — không chạy nếu không có timeout:**

```java
ExecutorService pool = Executors.newSingleThreadExecutor();

Future<Integer> outer = pool.submit(() -> {
    Future<Integer> inner = pool.submit(() -> 42);
    return inner.get(); // Worker duy nhất chờ task ở queue!
});

System.out.println(outer.get()); // Có thể đợi mãi.
```

Đây là snippet (cần imports `ExecutorService`, `Executors`, `Future`). Pool một worker chạy outer; inner đã queue nhưng worker đang chờ inner, nên inner không thể chạy. Đây là **thread-starvation deadlock**, không nhất thiết có vòng sở hữu intrinsic monitor.

Cách tránh:

- Nếu công việc nhỏ, gọi trực tiếp logic con thay vì submit và blocking wait.
- Với fan-out, tạo subtasks rồi **join ở coordinator không chiếm worker của chính pool**.
- Dùng asynchronous composition `thenCompose`/`allOf` đúng cách thay cho blocking `get` trong pool worker.
- Nếu sử dụng executor riêng cho phụ thuộc, cần tính toán capacity/lifecycle rõ ràng, không đơn giản nhân đôi pool.
- Thêm timeout và cancellation, nhưng timeout chỉ giúp thoát một tình huống chờ, không tự giải quyết sai cấu trúc.

**`CompletableFuture.supplyAsync(..., samePool).join()` trong worker vẫn có thể gây starvation.** Tên method có chữ Async không khiến `join` thành non-blocking.

## 14. CompletableFuture không có nghĩa non-blocking

`CompletableFuture.supplyAsync(supplier, executor)` làm caller nhận future mà không đợi supplier xong. Nhưng supplier có thể:

- Tính toán CPU nặng.
- Gọi `Thread.sleep`.
- Gọi JDBC hoặc HTTP client đồng bộ.
- Chờ mutex hoặc `Future.get` khác.

Nếu supplier blocking, nó vẫn chiếm executor worker. Khi dùng `thenApply`, `thenApplyAsync`, `thenCompose` và `thenComposeAsync`, cần biết **executor nào chạy stage tiếp theo** và stage nào có thể block. Nếu không chỉ định executor cho một số async method, JDK có thể dùng common pool; vì thế theo dõi resource thực tế không thể chỉ nhìn một pool do ứng dụng tạo.

**Request concurrency** có thể tiếp tục tồn tại trong thời gian future chưa hoàn tất dù caller thread đã trả về. Do đó đếm requests theo lifecycle business và đếm executor tasks theo lifecycle task, không dùng số futures như số users.

## 15. Giới hạn business concurrency riêng với pool

Giả sử business yêu cầu tối đa 100 user requests đang hoạt động và pool chỉ có 12 workers. Có thể dùng **`Semaphore` admission** ở ranh giới request để không nhận nhiều hơn 100, còn pool 12 workers xử lý từng stage.

Snippet synchronous:

```java
if (!requestPermits.tryAcquire()) {
    rejectBusy();
    return;
}

try {
    handleWholeRequest();
} finally {
    requestPermits.release();
}
```

Ở đây `requestPermits` là `Semaphore` và các methods là logic ứng dụng. Nếu `tryAcquire` thất bại, **không release**. Nếu request thực thi qua **asynchronous stages**, phải giữ permit tới **khi workflow cuối cùng hoàn thành**, không release ngay sau `submit` hoặc ngay khi callback khởi động trả về.

Một limiter cho business work **không thay** giới hạn connections tới DB. Có thể cần:

1. Max user requests in-flight.
2. Max outstanding tasks / bounded executor queue.
3. Max concurrent downstream I/O calls.
4. Per-user/tenant quota để tránh một nguồn chiếm toàn bộ tài nguyên.

Đây là bốn lớp admission khác nhau, không phải bốn cách đặt cùng một biến `threadPoolSize`.

## 16. Đo đúng metrics cho requests và executor

| Metric | Tầng | Lưu ý |
| --- | --- | --- |
| `activeRequests` | Business | Đếm từ accept tới response/workflow completion |
| `inFlightJobs` | Business/background | Đếm jobs vẫn chạy sau response nếu có |
| `taskSubmitRate` | Executor | Tasks/s nộp vào pool |
| `acceptedTasks` | Executor | Đã nhận, không chắc đã chạy |
| `rejectedTasks` | Executor | Bị policy từ chối, cần handling |
| `queuedTasks` | Queue | Snapshot backlog chờ worker |
| `activeWorkers` | Executor | Worker đang thực thi task body |
| `poolSize` | Executor | Số workers hiện pool quản lý |
| `largestPoolSize` | Executor | Mức pool size cao nhất đã quan sát |
| `completedTaskCount` | Executor | Số task hoàn thành theo metric pool |
| `queueWaitMs` | Latency | Submit → task body start |
| `taskRunMs` | Latency | Task start → task body complete |
| `requestLatencyMs` | End-to-end | Yêu cầu bắt đầu → phản hồi |
| `blockedTime` | Runtime | I/O/lock/condition wait |
| `CPU load` | Host | CPU có phải nghẽn không |
| `downstreamInFlight` | Resource | Connections/API calls đang sử dụng |

`ThreadPoolExecutor.getActiveCount()`, `getCompletedTaskCount()`, `getTaskCount()` là các số liệu giám sát **ước lượng**, không một transaction snapshot nhất quán. Không ghép chúng trong một phép kiểm soát invariant nghiêm ngặt. Với request accounting, instrument **start/end** theo business ownership và xử lý cả rejection, exception, timeout, cancellation.

### Little's Law hỗ trợ capacity thinking, không cho ra số threads

Trong tải ổn định tương đối:

```text
Average in-flight requests ≈ completion rate × average request duration

Ví dụ:
100 requests/second × 0.2 seconds = 20 requests in-flight (average)
```

Đây là tương quan **số requests trong hệ thống**, không phải khuyến nghị tạo 20 worker threads. Request có thể chờ I/O, tạo subtasks, hoặc không giữ worker trong phần lớn thời gian. Công thức cũng không mô tả chính xác một snapshot khi burst/queue đang tăng không ổn định.

## 17. Timeout, cancellation và task lifecycle

Khi request timeout sau 2 giây, thread đang gọi remote API có thể tiếp tục chạy lâu nếu API không có timeout/cancellation phù hợp. Khi đó:

- Request metric có thể xem request kết thúc.
- Background task vẫn giữ worker và connection.
- Queue có thể tăng mặc dù caller đã từ bỏ kết quả.
- `future.cancel(true)` là **yêu cầu hủy**, không phải cam kết worker dừng ngay.
- Future cancelled có thể được báo trước khi worker ra khỏi `finally` và hoàn tất resource cleanup.

Do đó cần truyền deadline qua các stages, dùng I/O API có timeout, viết cooperative cancellation, finally/try-with-resources và **quan sát task termination** thật. Không dùng `Thread.stop()` để ép dừng; nó có thể phá invariant và gây resource corruption.

Trong metrics, phân biệt **request completed**, **task cancelled**, **worker stopped** và **resource closed** khi điều đó ảnh hưởng correctness.

## 18. Virtual threads thay đổi loại thread, không xóa demand

Từ Java 21 có `Executors.newVirtualThreadPerTaskExecutor()`: thường tạo virtual thread cho mỗi task, **không phải fixed-size platform worker pool**. Nó giúp mô hình nhiều blocking tasks dễ scale hơn trong nhiều trường hợp, nhưng:

- Mỗi request vẫn có thể tạo nhiều virtual tasks.
- Nhiều virtual threads không bằng cùng số OS/carrier threads hoặc cores.
- CPU, heap, file descriptors, DB connections, remote API quotas vẫn hữu hạn.
- Vẫn cần admission limiter/backpressure cho downstream.
- Shared mutable state vẫn phải tuân theo Java Memory Model.
- Không suy ra mọi blocking call đều có chi phí bằng 0; runtime/version JDK khác nhau có thể có hành vi scheduling/pinning riêng.

API virtual threads không có trên Java 17; toàn bộ ví dụ executable trước đó tương thích JDK 17+.

## 19. Tối ưu và kiểm thử tải

Không tăng pool size chỉ vì thấy nhiều requests. Một quy trình có căn cứ:

1. **Định nghĩa** user/request in-flight bắt đầu và kết thúc ở đâu.
2. **Vẽ fan-out**: mỗi request tạo bao nhiêu tasks, có retry/nested waits không.
3. **Phân loại workload**: CPU, I/O, mixed, lock contention.
4. **Đo** arrival rate, request latency, task queue wait, task runtime, rejection.
5. **Quan sát** poolSize, activeCount, queued tasks, completedTaskCount, CPU, heap.
6. **Quan sát downstream**: DB connection pool, API quota, network throughput.
7. **Chọn queue bounded/admission** và rõ cách reject/busy/timeout.
8. **Điều chỉnh** core/max phù hợp hành vi queue và deadline; đừng quên maximum không luôn có tác dụng với unbounded queue.
9. **Load test** nhiều mức concurrent requests, xem p50/p95/p99, queue growth, throughput, starvation/fairness.
10. **Thử shutdown/cancellation**: tasks queued, tasks running và tasks không phản hồi interrupt.
11. **Review nested blocking** để tránh pool starvation.
12. **Kiểm tra memory/resource limits** khi fan-out tăng hoặc chuyển sang virtual threads.

Không nên đặt mục tiêu pool `activeCount` luôn bằng maximum. Pool rảnh khi tải thấp là bình thường. Mục tiêu thực là **latency, correctness, throughput, khả năng phục hồi và giới hạn tài nguyên**.

## 20. Các ngộ nhận phổ biến

| Ngộ nhận | Điều thực tế |
| --- | --- |
| “100 concurrent users cần 100 threads” | User sessions ≠ active requests ≠ worker tasks |
| “Pool 10 threads chỉ nhận được 10 tasks” | Có thể còn nhiều tasks trong queue |
| “`submit` xong nghĩa task đã chạy” | Accepted task có thể queued |
| “Active worker = đang dùng CPU” | Task có thể chờ I/O, lock, latch |
| “Một request luôn tương ứng một task” | Fan-out và retries sinh nhiều subtasks |
| “Nhiều workers luôn tăng throughput” | CPU/downstream có thể bão hòa, thêm threads gây overhead |
| “Tăng `maximumPoolSize` chắc chắn thêm workers” | Pool có thể queue sau khi đạt core |
| “Unbounded queue không reject nên an toàn” | Queue/memory/latency có thể tăng không giới hạn |
| “Async future luôn non-blocking” | Supplier có thể block, `join()` block caller |
| “Nested `Future.get` trong same pool luôn ổn” | Dễ thread-starvation deadlock |
| “CallerRuns không thay đổi execution concurrency” | Task chạy trên caller ngoài pool workers |
| “`isCancelled()` chứng minh worker đã dừng” | Worker có thể vẫn cleanup hoặc block |
| “Virtual threads bỏ giới hạn hệ thống” | Downstream/CPU/memory vẫn hữu hạn |
| “Queue depth snapshot là accounting chính xác tuyệt đối” | Metrics có race và snapshot độc lập |
| “Một user nhiều requests sẽ tự được chia công bằng” | Có thể cần per-user quota/fair queue |

## 21. Câu hỏi tự kiểm tra

**1. Có 500 requests in-flight và pool 8 workers, có mâu thuẫn không?** Không. Requests có thể chờ queue hoặc I/O, một request có thể sinh nhiều tasks.

**2. Một request submit 20 subtasks, có phải 20 users?** Không. Đây là một request tạo 20 executor tasks.

**3. `newFixedThreadPool(4)` nhận 100 tasks thì có 100 tasks chạy body đồng thời trong pool không?** Không. Thường nhiều nhất bốn tasks trên pool workers, các tasks khác đợi queue.

**4. Worker đang ở `CountDownLatch.await` có tính đang executing không?** Nó vẫn chiếm vị trí worker, dù không sử dụng CPU để tính toán.

**5. Tăng `maximumPoolSize` mà queue không đầy có tạo thêm workers ngay không?** Thường không. Sau core, pool ưu tiên queue.

**6. Tại sao `CompletableFuture.supplyAsync` không có nghĩa non-blocking toàn bộ?** Code supplier vẫn có thể gọi blocking API trên executor thread.

**7. Vì sao nested `Future.get` trong pool một worker có thể treo?** Worker chờ subtask ở queue của chính pool, nhưng không có worker rảnh để chạy subtask.

**8. CallerRunsPolicy tạo ra task body ngoài pool worker được không?** Có, trên thread gọi `execute` khi policy áp dụng.

**9. Little's Law ra 20 requests in-flight có buộc pool 20 threads không?** Không. Nó đo requests trung bình trong hệ thống, không phải worker count.

**10. Khi user response timeout, task chạy nền có thể vẫn chiếm worker không?** Có, nếu cancellation không khiến task thực sự dừng.

**11. Virtual-thread-per-task tự giới hạn số DB queries song song không?** Không. Cần giới hạn connection/rate/quota riêng.

**12. Đo chỉ `activeCount` có đủ sizing pool không?** Không. Cần queue waits, request latency, CPU, downstream, fan-out, rejection và demand theo thời gian.

## 22. Checklist cuối cùng

- **Đã xác định user task concurrency là requests, sessions hay jobs chưa?**
- Có đếm request in-flight riêng khỏi executor tasks outstanding không?
- Biết một request tạo bao nhiêu subtasks, futures và retries không?
- Đã phân biệt accepted, queued, executing, completed, rejected chưa?
- Pool đang dùng queue **bounded hay unbounded**? Core/max/rejection hoạt động thế nào?
- Worker đang CPU-bound hay chủ yếu bị blocking?
- Có nested submission tới same pool rồi blocking `get/join` không?
- Có admission limiter cho workflow và limiter cho downstream riêng không?
- Có deadline, cancellation và finally cleanup thực tế không?
- Metrics có ghi đơn vị requests/tasks/threads/cores và đo theo time series không?
- Khi chuyển sang virtual threads, vẫn quản lý resource budgets chứ?
- Load test đã kiểm tra p95/p99, memory, queue growth, rejection và shutdown chưa?

**Kết luận:** **User task concurrency** thể hiện số lượng công việc người dùng đang có nhu cầu xử lý; **thread pool concurrency** thể hiện số task bodies được các workers trong một executor thực thi tại một thời điểm; **CPU parallelism** là mức thực sự chạy trên hardware. Ba đại lượng có quan hệ nhưng không bằng nhau. Muốn hệ thống ổn định, phải đếm đúng business requests và subtasks, hiểu queue/rejection, giới hạn in-flight demand, tránh worker starvation và tuning theo bottleneck thực tế thay vì suy ra số thread từ số users.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-015">Thứ tự tiếp nhận việc của ThreadPoolExecutor: core threads, queue, maximum và rejection</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự tiếp nhận việc của ThreadPoolExecutor: core threads, queue, maximum và rejection

## 1. Vấn đề cần hiểu

Trong Java, khi gọi `ThreadPoolExecutor.execute(Runnable)`, executor phải quyết định một task mới sẽ được thực thi theo cách nào: giao cho **worker vừa tạo**, đưa vào **work queue**, tạo thêm worker khi queue không nhận được, hoặc gọi **RejectedExecutionHandler**.

Nhiều người nghĩ `corePoolSize=2, maximumPoolSize=8` nghĩa là executor sẽ chạy đồng thời 8 task ngay khi có 8 request. Điều đó thường **sai**: với work queue có khả năng nhận việc, sau khi đạt core pool size, executor **ưu tiên queue**, không ưu tiên mở rộng ngay đến maximum.

**Quy tắc ghi nhớ:**

~~~text
              execute(task)
                   │
       workerCount < corePoolSize?
            ┌──────┴──────┐
           CÓ           KHÔNG
            │              │
     tạo core worker   thử queue.offer(task)
     chạy task mới       ┌───┴─────┐
                       thành công  thất bại
                           │          │
                    kiểm tra lại     thử tạo worker
                    trạng thái pool  đến maximumPoolSize
                           │          ┌───┴────┐
                       queued      thành công  thất bại
                                      │         │
                                 chạy task     REJECT
~~~

Sơ đồ mô tả **đường quyết định phổ biến** và lược bỏ một số kiểm tra trạng thái đồng thời quan trọng. Thực tế, sau khi queue nhận task, executor **kiểm tra lại** xem pool có bị shutdown hay số worker đã trở về 0 không; nó có thể thu hồi task vừa queue để reject hoặc khởi tạo worker để bảo đảm tiến triển.

Sau bài này, bạn sẽ:

- Mô tả chính xác ba quy tắc **core → queue → maximum → rejection** của `ThreadPoolExecutor`.
- Phân biệt **worker count**, **active count**, **queue size**, **accepted**, **started** và **completed**; không nhầm tiến độ thực thi với quyết định tiếp nhận.
- Hiểu ảnh hưởng của `ArrayBlockingQueue`, `LinkedBlockingQueue`, `SynchronousQueue`, queue có/không giới hạn và các executor factories.
- Phân tích vì sao `maximumPoolSize` gần như không phát huy với unbounded queue nhưng lại quan trọng khi queue bounded hoặc direct handoff.
- Biết tại sao shutdown, racing updates và thất bại tạo worker có thể khiến task bị từ chối dù chỉ nhìn số thread và queue thấy còn chỗ.
- Chọn `AbortPolicy`, `CallerRunsPolicy`, `DiscardPolicy`, `DiscardOldestPolicy` theo yêu cầu không mất task và backpressure.
- Chạy các ví dụ Java 17+ có `CountDownLatch` kiểm soát trình tự, đưa ra stdout chính xác thay vì suy đoán theo `Thread.sleep`.
- Rà soát backlog, tail latency, shutdown, cancellation và nguy cơ starvation/deadlock.

**Phạm vi:** Java SE và Executor Framework, không bàn đến tích hợp framework. Các chương trình trong bài biên dịch bằng JDK 17+.

## 2. Cấu trúc tối thiểu của ThreadPoolExecutor

Constructor hay dùng:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

ThreadPoolExecutor pool = new ThreadPoolExecutor(
        2,                             // corePoolSize
        4,                             // maximumPoolSize
        30,                            // keepAliveTime
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(3),   // tối đa 3 task đang đợi
        new ThreadPoolExecutor.AbortPolicy()
);
~~~

Đây là **đoạn mã minh họa trong method**, chưa phải chương trình độc lập. Caller phải quản lý vòng đời `pool` qua `shutdown()` và phương án chờ/hủy phù hợp.

Các thành phần quan trọng:

| Thành phần | Chức năng |
|---|---|
| `corePoolSize` | Ngưỡng worker mà executor **ưu tiên tạo** trước khi queue các task mới. |
| `maximumPoolSize` | Giới hạn tổng số workers khi queue không nhận thêm task. |
| `workQueue` | Nơi đợi của các task đã được nhận nhưng chưa bắt đầu chạy bởi worker. |
| `keepAliveTime` | Thời gian worker đủ điều kiện được giữ khi idle trước khi có thể bị thu hồi. |
| `ThreadFactory` | Tạo các Thread; có thể đặt tên, daemon, handler, v.v. |
| `RejectedExecutionHandler` | Xử lý task không được tiếp nhận do saturation, shutdown hoặc không thể tạo worker. |
| `execute(Runnable)` | Gửi task để thực hiện về sau, có thể tạo worker mới, enqueue hoặc reject. |

`corePoolSize` và `maximumPoolSize` không phải hai queue và không phải số request độc lập. `maximumPoolSize` là giới hạn **tổng** workers chứ không phải số workers cộng thêm vào core. Ví dụ core=2, max=4 nghĩa là tối đa 4 workers tổng cộng theo cấu hình, không phải 2+4=6.

### Điều kiện cấu hình

~~~text
0 <= corePoolSize <= maximumPoolSize
maximumPoolSize > 0
keepAliveTime >= 0
workQueue != null
~~~

Các tham số không hợp lệ có thể gây `IllegalArgumentException` hoặc `NullPointerException` khi tạo/cấu hình executor. Riêng bật `allowCoreThreadTimeOut(true)` đòi `keepAliveTime > 0`.

## 3. Quy tắc thứ nhất: worker count dưới corePoolSize

Khi executor đang ở trạng thái nhận việc và số workers thấp hơn corePoolSize, nó **ưu tiên thử tạo thêm worker** để chạy task vừa gửi, thay vì xếp task vào queue.

Giả sử cấu hình:

~~~text
corePoolSize = 2
maximumPoolSize = 4
queue capacity = 3
workerCount lúc đầu = 0
~~~

Khi lần lượt gọi `execute(task1)` và `execute(task2)`, nếu chưa có worker nào kết thúc hoặc thay đổi cấu hình:

- Task 1: tạo worker đầu tiên; worker này nhận task 1 làm công việc đầu tiên.
- Task 2: tạo worker thứ hai; worker nhận task 2.
- Queue vẫn trống ở hai lần submit đó vì pool còn dưới core.

**Điểm dễ nhầm:** quy tắc dựa trên **số worker hiện có**, không đơn thuần dựa trên `getActiveCount()`. Javadoc nêu rằng nếu còn dưới corePoolSize, executor ưu tiên thêm worker ngay cả khi có worker hiện tại đang idle. Do vậy không thể suy luận “có một worker idle thì chắc chắn task mới được đưa vào queue” khi tổng workers vẫn dưới core.

### Core workers được tạo lazily

Constructor thường **chưa tạo đủ** core workers ngay lập tức. Nếu cần có workers sẵn trước khi request đến:

~~~java
int started = pool.prestartAllCoreThreads();
~~~

`prestartAllCoreThreads()` trả số core workers mới được khởi chạy. `prestartCoreThread()` thử tạo một core worker còn thiếu. Đây là lựa chọn về khởi động nóng (warm start), không thay đổi quy tắc cơ bản của queue và maximum khi tải tới.

### CorePoolSize = 0

`corePoolSize=0` hợp lệ khi `maximumPoolSize>0`. Executor vẫn có cơ chế tạo worker xử lý công việc, kể cả trường hợp một task đã được queue nhưng chưa có worker. Không được đọc sơ đồ đơn giản theo kiểu “core=0 nên task chỉ queue mãi mà chẳng ai chạy”. Implementation có bước tái kiểm tra sau queue và tạo worker khi cần.

## 4. Quy tắc thứ hai: đạt core thì ưu tiên queue

Khi số workers đã đạt ít nhất corePoolSize, executor **thử đưa task vào work queue trước**, không tự tạo worker mới chỉ vì `workerCount < maximumPoolSize`.

Với core=2, max=4, bounded queue capacity=3 và **hai core workers đều đang xử lý công việc dài**, những task tiếp theo đến như sau:

~~~text
workers đang chạy: [task1, task2]
workQueue:         []

execute(task3) → workQueue.offer(task3) = true
workQueue:         [task3]

execute(task4) → workQueue.offer(task4) = true
workQueue:         [task3, task4]

execute(task5) → workQueue.offer(task5) = true
workQueue:         [task3, task4, task5]

workerCount vẫn có thể = 2 dù max = 4.
~~~

Đây chính là lý do **queue quyết định cách mở rộng**. Nếu queue còn nhận được task, pool thường không tăng worker count lên max.

**Queue chứa việc đang đợi, không chứa task đang chạy.** Khi worker lấy task3 ra khỏi queue, queue size giảm và task3 thành active. Không được coi queue size=0 là hệ thống không còn việc đang thực thi.

### Queue không phải cam kết thứ tự hoàn thành

Task3 có thể được submit và queue trước task6, nhưng task6 có thể được giao trực tiếp cho một extra worker khi queue đầy. Nếu task6 chạy nhanh, nó **có thể hoàn tất trước task3** đang chờ. Dù queue là FIFO, **toàn bộ pool không cung cấp FIFO completion order**. Các task đang chạy trên các worker khác nhau có thể kết thúc theo thứ tự bất kỳ.

Điều này ảnh hưởng rõ ràng tới ứng dụng đòi thứ tự xử lý theo một key, như xử lý update trạng thái một tài khoản. Nếu thứ tự là invariant nghiệp vụ, cần serialization hoặc ordering protocol riêng, không chỉ tin vào FIFO queue.

## 5. Quy tắc thứ ba: queue không nhận được mới thử tăng tới maximumPoolSize

Khi queue `offer(task)` thất bại, executor sẽ thử tạo worker mới chạy task đó với giới hạn `maximumPoolSize`.

Với ví dụ core=2, max=4, queue capacity=3 và các worker đang bận:

~~~text
Sau 5 task:
- worker 1: task1 (đang chạy)
- worker 2: task2 (đang chạy)
- queue: [task3, task4, task5] (đầy)

execute(task6):
- queue.offer(task6) → false
- workerCount=2 < max=4
- tạo extra worker 3, giao task6 làm task đầu tiên

execute(task7):
- queue vẫn đầy
- workerCount=3 < max=4
- tạo extra worker 4, giao task7 làm task đầu tiên
~~~

**Task6 và task7 có thể bắt đầu trước task3–5**, tùy scheduler và thời gian chạy. Extra worker được tạo vì queue không nhận được task; việc task được nhận trực tiếp không tự đảm bảo order với queued tasks cũ.

### MaximumPoolSize không quyết định có tạo worker nếu queue vẫn nhận

Nếu `maximumPoolSize=100` nhưng queue `offer` vẫn thành công, logic ưu tiên queue khiến pool không nhất thiết tạo worker thứ 3 khi core=2. Ngược lại, với `SynchronousQueue` không có chỗ giữ task, offer có thể thất bại ngay khi không có worker chờ handoff, khiến pool tạo thêm workers nhanh hơn.

**Không có một con số maximum “tối ưu” nếu chưa biết queue strategy và workload.**

## 6. Quy tắc thứ tư: không queue được và không thêm worker được thì reject

Khi cả hai điều kiện sau đồng thời đúng:

1. Work queue không nhận thêm task.
2. Không thể tạo thêm worker, chẳng hạn `workerCount >= maximumPoolSize`.

Executor sẽ chuyển task tới `RejectedExecutionHandler`.

Một task cũng có thể bị reject **khi executor đã shutdown**, dù queue còn chỗ hoặc worker count chưa max. Ngoài ra, việc `ThreadFactory` không tạo được Thread, hoặc resource exhaustion, có thể làm không thể tạo worker; không nên coi mọi rejection đều do queue đầy.

Ví dụ core=2, max=4, queue capacity=3:

| Task submit | Hai core và queue vẫn giữ việc | Quyết định thường gặp | Tổng worker | Queue size |
|---|---|---|---:|---:|
| 1 | Chưa có worker | Tạo core worker 1 | 1 | 0 |
| 2 | Task 1 còn chạy | Tạo core worker 2 | 2 | 0 |
| 3 | Hai workers bận | Queue | 2 | 1 |
| 4 | Hai workers bận | Queue | 2 | 2 |
| 5 | Hai workers bận | Queue đầy sau khi nhận task5 | 2 | 3 |
| 6 | Queue đã đầy | Tạo extra worker 3 | 3 | 3 |
| 7 | Queue đã đầy | Tạo extra worker 4 | 4 | 3 |
| 8 | Workers tối đa, queue đầy | Reject/handler | 4 | 3 |

Bảng này **chỉ chính xác dưới giả thiết** chưa task nào kết thúc hoặc được worker lấy ra khỏi queue trong lúc submit. Trong ứng dụng thật, một worker có thể hoàn tất task và lấy task đang chờ trước khi submit tiếp theo, làm thay đổi mọi giá trị. Vì vậy không suy quyết định thực tế **chỉ theo số thứ tự request**.

## 7. Sơ đồ ra quyết định đầy đủ hơn

~~~text
                          execute(command)
                                 |
                       command có null?
                         /             \
                        có             không
                        |                |
                NullPointerException   trạng thái/workerCount
                                         |
                           workerCount < core?
                           /                 \
                         có                  không
                         |                     |
                 addWorker(task, core=true)  pool RUNNING?
                         |                  /           \
                       thành công          có          không
                         |                 |             |
                       return           offer(queue)   không nhận task
                                         /    \
                                       được  không được
                                        |        |
                                RECHECK pool     addWorker(task,
                                  state/count    core=false, <=max)
                                     |               /     \
                           shutdown? 0 workers     được   lỗi
                             |          |           |      |
                        remove/reject  khởi tạo     return  reject
                                     worker nếu
                                     cần
                                        |
                                     return
~~~

Sơ đồ là **mô hình diễn giải** của contract, không phải implementation copy-paste dùng để tự viết executor. OpenJDK sử dụng các thao tác nguyên tử và bước kiểm tra lại để đối phó với thay đổi trạng thái ngay trong lúc quyết định.

### Vì sao cần recheck sau queue offer thành công?

Giả sử thread A kiểm tra pool vẫn RUNNING, sau đó queue.offer(task) thành công. Cùng lúc thread B gọi shutdown. Nếu A cứ trả về “đã nhận” mà không xét thay đổi trạng thái, có thể task nằm trong queue của pool không còn được phép nhận việc mới. Đó là lý do implementation tái kiểm tra `runState`: trong một số interleaving nó gỡ task khỏi queue và reject.

Ví dụ khác: queue nhận task nhưng số workers trở về 0 do timeout/lỗi. Executor cần bảo đảm một worker có thể tiếp tục lấy việc, thay vì để queue có task mà không có worker xử lý. Vì vậy có bước kiểm tra và tạo worker khi cần.

### “Đã được queue” không có nghĩa sẽ được chạy chính xác một lần trong mọi tình huống

Một task có thể:
- được nhận và bắt đầu;
- được queue nhưng sau đó bị `shutdownNow` thu hồi khi chưa chạy;
- bị xóa khỏi queue bởi caller có quyền quản lý tác vụ;
- bị hủy bằng `Future.cancel(...)` trước khi thực thi;
- chạy rồi ném exception (vẫn là lần thực thi đã được bắt đầu).

Đừng đồng nhất `execute` trả bình thường, `submit` trả `Future`, `Future.isDone()`, `Future.isCancelled()` và side effects hoàn tất. Cần một completion/acknowledgment protocol phù hợp nếu đây là công việc nghiệp vụ bắt buộc.

## 8. Ví dụ chạy độc lập 1: kiểm chứng core → queue → max → reject

Chương trình sau **không phụ thuộc vào Thread.sleep()**. Dùng `CountDownLatch` để giữ các workers đang bận cho tới khi main quan sát xong. Qua đó thứ tự admission có thể được xác nhận bằng số worker và kích cỡ queue.

Lưu tệp **AdmissionOrderDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AdmissionOrderDemo {
    private static Runnable blockingTask(
            CountDownLatch started,
            CountDownLatch release) {
        return () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.AbortPolicy()
        );

        CountDownLatch coreStarted = new CountDownLatch(2);
        CountDownLatch extraStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        boolean rejected = false;

        try {
            pool.execute(blockingTask(coreStarted, release)); // #1
            pool.execute(blockingTask(coreStarted, release)); // #2
            coreStarted.await();

            pool.execute(() -> { }); // #3: queue slot 1
            pool.execute(() -> { }); // #4: queue slot 2

            pool.execute(blockingTask(extraStarted, release)); // #5
            pool.execute(blockingTask(extraStarted, release)); // #6
            extraStarted.await();

            try {
                pool.execute(() -> { }); // #7: queue + workers full
            } catch (RejectedExecutionException expected) {
                rejected = true;
            }

            System.out.println("workers=" + pool.getPoolSize());
            System.out.println("queued=" + pool.getQueue().size());
            System.out.println("rejected=" + rejected);
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }

        System.out.println("completed=" + pool.getCompletedTaskCount());
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac AdmissionOrderDemo.java
java AdmissionOrderDemo
~~~

**Stdout khi không có interrupt từ bên ngoài và các worker hoàn thành:**

~~~text
workers=4
queued=2
rejected=true
completed=6
~~~

Giải thích từng bước:

1. Task #1 và #2 khởi tạo hai core workers, rồi hai workers cùng chờ ở `release.await()`.
2. Main chỉ tiếp tục khi `coreStarted.await()` hoàn tất, bảo đảm hai workers đã bắt đầu và **chưa thể kết thúc** trước khi mở release.
3. Task #3 và #4 vào queue capacity 2. Vì cả hai core workers đang chờ release, chưa worker nào lấy hai task này.
4. Task #5 và #6 đến khi queue đầy. Pool tạo workers thứ ba và thứ tư, mỗi worker nhận task mới ngay rồi chờ release.
5. Main đợi cả hai extra workers báo đã bắt đầu, sau đó gửi task #7. Vì queue vẫn chứa #3/#4, bốn workers vẫn bị giữ bận, `AbortPolicy` ném `RejectedExecutionException`; main bắt và ghi true.
6. Trước khi release, các snapshot số lượng chắc chắn là **4 workers, 2 queued**, trong điều kiện ví dụ. Không giả định thứ tự worker name hoặc task finish.
7. Cuối cùng release cả bốn workers; #3 và #4 được xử lý rồi executor shutdown. Sáu task được chấp nhận và hoàn tất; #7 bị reject nên `getCompletedTaskCount()` là 6.

**Chú ý:** `getCompletedTaskCount()` được Javadoc mô tả là một giá trị **ước lượng** khi pool đang hoạt động, nhưng sau khi tất cả tasks chấp nhận hoàn tất và pool terminated, số lượng ở ví dụ này ổn định. Nếu `awaitTermination` không thành công trong 5 giây hoặc task bị interrupt ngoài dự kiến, không được hứa stdout y hệt.

### Vấn đề điều kiện khi demo có lỗi

Chương trình đã đặt `release.countDown()` trong `finally` để tránh giữ workers vĩnh viễn nếu một phép submit/rejection ngoài dự kiến làm main rời block. Trong code thực, còn phải log/propagate error và xử lý shutdown sao cho không làm mất dữ liệu cần hoàn thành.

## 9. Ví dụ chạy độc lập 2: unbounded queue làm maximum ít phát huy

Đối với `LinkedBlockingQueue<>` mặc định, hàng đợi có capacity danh nghĩa rất lớn. Sau khi đã có core worker và worker đó đang bận, task mới được queue thay vì tạo thêm workers tới max.

Lưu tệp **QueueFirstDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class QueueFirstDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 10, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );

        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            running.await();

            pool.execute(() -> { });
            pool.execute(() -> { });
            pool.execute(() -> { });

            System.out.println("core=" + pool.getCorePoolSize());
            System.out.println("max=" + pool.getMaximumPoolSize());
            System.out.println("workers=" + pool.getPoolSize());
            System.out.println("queued=" + pool.getQueue().size());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac QueueFirstDemo.java
java QueueFirstDemo
~~~

Stdout:

~~~text
core=1
max=10
workers=1
queued=3
~~~

Một worker lõi bị giữ bận trong khi ba task chờ. `max=10` **không buộc executor tạo thêm chín workers**, vì queue vẫn nhận task. Đây là nguyên nhân một pool có maximum lớn nhưng queue depth tăng, tail latency tăng và CPU có thể còn nhàn rỗi.

**Không coi unbounded queue là không có giới hạn tài nguyên.** `LinkedBlockingQueue<>` mặc định có capacity `Integer.MAX_VALUE`, nhưng task objects và queue nodes tiêu tốn heap. Trước khi đạt giới hạn danh nghĩa, ứng dụng có thể gặp memory pressure hoặc `OutOfMemoryError` nếu producer nhanh hơn consumer trong thời gian dài.

## 10. Ví dụ chạy độc lập 3: SynchronousQueue khiến pool mở rộng sớm

`SynchronousQueue` **không giữ các phần tử** như một queue thông thường. Một lần handoff thành công đòi một bên nhận đang chờ tương ứng. Khi pool có core worker bận và không worker nào chờ nhận task, `SynchronousQueue.offer(task)` không có chỗ chứa, khiến executor thử tạo thêm workers tới max.

Lưu **DirectHandoffDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DirectHandoffDemo {
    private static Runnable blocked(
            CountDownLatch started,
            CountDownLatch release) {
        return () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 2, 20, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        );

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        boolean thirdRejected = false;

        try {
            pool.execute(blocked(firstStarted, release));
            firstStarted.await();

            pool.execute(blocked(secondStarted, release));
            secondStarted.await();

            try {
                pool.execute(() -> { });
            } catch (RejectedExecutionException e) {
                thirdRejected = true;
            }

            System.out.println("workers=" + pool.getPoolSize());
            System.out.println("queued=" + pool.getQueue().size());
            System.out.println("thirdRejected=" + thirdRejected);
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Lệnh:

~~~bash
javac DirectHandoffDemo.java
java DirectHandoffDemo
~~~

Stdout:

~~~text
workers=2
queued=0
thirdRejected=true
~~~

Vì `SynchronousQueue` không chứa backlog:
- Task đầu dùng core worker.
- Khi core bận, task thứ hai không handoff ngay được nên executor tạo extra worker đến max=2.
- Cả hai workers bận, task thứ ba không queue được và không thể tăng worker nữa: reject.
- `queue.size()` vẫn bằng **0**, không có nghĩa pool không bận. Nó là đặc điểm riêng của handoff queue.

**Không tùy tiện đặt maximumPoolSize cực lớn** để tránh rejection với SynchronousQueue: khi nhiều tác vụ blocking liên tiếp đến, pool có thể tạo quá nhiều platform threads và tiêu hao tài nguyên hệ thống.

## 11. Quy tắc queue thay đổi thế nào theo từng cấu trúc?

| Work queue | Có giữ backlog? | Khi đã có core workers và tất cả đều bận | Vai trò maximumPoolSize | Rủi ro |
|---|---|---|---|---|
| `LinkedBlockingQueue<>` không truyền capacity | Có, capacity danh nghĩa rất lớn | Task vào queue | Thường khó chạm tới max | Heap/backlog tăng không kiểm soát |
| `ArrayBlockingQueue<>(n)` | Có, tối đa n task chờ | Task vào queue cho tới khi đầy | Khi queue đầy mới mở rộng | Khi quá tải: rejection |
| `LinkedBlockingQueue<>(n)` | Có, tối đa n task chờ | Tương tự bounded queue | Khi queue đầy mới mở rộng | Cần sizing/backpressure |
| `SynchronousQueue<>` | Không chứa task chờ | Thử giao trực tiếp cho worker chờ; nếu không được thì tạo thêm worker | Tác động sớm | Worker growth, rejection nếu max nhỏ |
| `PriorityBlockingQueue<>` | Có, gần như unbounded mặc định | Queue theo priority policy | Thường không kích hoạt tăng tới max | Priority inversion/starvation, backlog |

Để hiểu executor, **luôn hỏi queue là gì và capacity bao nhiêu trước khi nhìn maximumPoolSize**.

### FIFO queue không đồng nghĩa FIFO toàn executor

Ngay cả với `ArrayBlockingQueue` hoặc `LinkedBlockingQueue` tuân theo thứ tự queue của chúng, task được giao trực tiếp cho extra worker khi queue đầy có thể bắt đầu trước những task đang chờ. Sự phối hợp worker scheduling và thời gian xử lý khiến **completion order** độc lập với **submission order**. Không dùng pool chung làm FIFO transaction engine nếu nghiệp vụ đòi thứ tự tuyệt đối.

### Executors.newFixedThreadPool và newCachedThreadPool

- `Executors.newFixedThreadPool(n)` có cấu hình fixed worker count và queue không giới hạn thực tế: worker count có giới hạn, backlog có thể tăng.
- `Executors.newCachedThreadPool()` sử dụng direct handoff và có thể mở rộng số platform workers mạnh, thu hồi workers idle theo policy: không cung cấp một bounded concurrency limit phù hợp mọi workload.
- `Executors.newSingleThreadExecutor()` chạy một task mỗi lần theo thiết kế, nhưng vẫn cần quản lý hàng đợi và shutdown, và cũng không phải transaction log bền vững.
- `Executors.newWorkStealingPool(...)` thuộc mô hình khác, không nên giả định tuân đúng sơ đồ admission của một `ThreadPoolExecutor` tự cấu hình ở đây.
- Với Java 21+, `Executors.newVirtualThreadPerTaskExecutor()` dùng một virtual thread cho mỗi task; **không** điều tiết downstream concurrency chỉ nhờ core/max/queue như ThreadPoolExecutor platform worker pool.

## 12. Rejection là một hành động theo policy, không nhất thiết là exception

Khi không nhận được task, `ThreadPoolExecutor` gọi `RejectedExecutionHandler.rejectedExecution(task, executor)`. Các policy chuẩn:

| Policy | Khi pool đã saturate và còn RUNNING | Điều cần hiểu |
|---|---|---|
| `AbortPolicy` | Ném `RejectedExecutionException` | Mặc định; caller phải xử lý exception, retry có giới hạn hoặc thông báo quá tải. |
| `CallerRunsPolicy` | Caller của `execute` chạy task trực tiếp | Backpressure tự nhiên, nhưng có thể block caller; nếu executor đã shutdown, policy bỏ task. |
| `DiscardPolicy` | Bỏ task không báo lỗi | Nguy hiểm nếu task bắt buộc phải chạy; không có evidence hoàn thành. |
| `DiscardOldestPolicy` | Bỏ head của queue rồi thử gửi task mới | Có thể mất một task cũ; tương tác với priority queue rất dễ sai kỳ vọng. |

**Quy tắc đặc biệt:** `RejectedExecutionHandler` là extension point, nên một handler tùy biến có thể block, đưa vào kênh khác, ghi metric, throw hoặc làm nhiều việc khác. Vì thế không thể nói “mọi rejection chắc chắn throw exception”. Với `AbortPolicy` mặc định thì có exception; với `DiscardPolicy`, `execute` có thể return bình thường **dù task không được chạy**.

### Rejection cũng xuất hiện sau shutdown

~~~java
pool.shutdown();
pool.execute(() -> System.out.println("will not be accepted"));
~~~

Với AbortPolicy, `execute` ném `RejectedExecutionException` vì pool không còn nhận công việc mới, **không cần queue đã đầy**. Với CallerRunsPolicy, nếu executor đã shutdown, policy không chạy task trên caller. Vì vậy cần kiểm tra trạng thái lifecycle và policy, không suy tất cả rejection là saturation.

## 13. Ví dụ chạy độc lập 4: CallerRunsPolicy tạo backpressure

Giả sử pool chỉ có **một worker**, một slot queue, và task đầu đang giữ worker. Task thứ hai chiếm queue; task thứ ba khi submit bị reject theo nghĩa admission thông thường, nhưng CallerRunsPolicy **tự chạy ngay trong main**.

Lưu tệp **CallerRunsDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CallerRunsDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> thirdThread = new AtomicReference<>();

        try {
            pool.execute(() -> {
                workerStarted.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            workerStarted.await();

            pool.execute(() -> { }); // task 2 xếp hàng
            pool.execute(() ->
                    thirdThread.set(Thread.currentThread().getName()));

            System.out.println("thirdThread=" + thirdThread.get());
            System.out.println("queued=" + pool.getQueue().size());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac CallerRunsDemo.java
java CallerRunsDemo
~~~

Stdout khi chạy trực tiếp qua `java CallerRunsDemo` từ main thread mặc định:

~~~text
thirdThread=main
queued=1
~~~

Giải thích:
- Worker vẫn chờ `release`, task #2 đang queue.
- Task #3 đến khi không còn capacity; CallerRunsPolicy gọi `run()` của task ngay trên thread thực hiện `execute`, tức main.
- `thirdThread` được gán "main" trước khi `execute` trả về; không chờ worker.
- Queue vẫn chứa task #2, còn worker đầu chưa được release. Vì vậy số queued là 1.
- Nếu chạy main bằng một harness đổi tên thread, tên có thể khác; ngữ nghĩa ổn định là **thread thực hiện submit**.

**Lợi ích:** producer bị chậm lại vì phải tự xử lý task, tạo dạng backpressure. **Chi phí:** nếu caller là event-loop hoặc thread phục vụ request nhạy latency, nó có thể bị block rất lâu, gây xấu tail latency.

## 14. execute và submit khác nhau ở đâu trong tình huống này?

`ThreadPoolExecutor.execute(Runnable)` trực tiếp áp dụng chính sách admission/rejection.

Khi dùng `ExecutorService.submit(Callable)` hoặc `submit(Runnable)`:
- `submit` thường bọc công việc vào một `FutureTask`, rồi gửi thông qua `execute`.
- Nếu task được chấp nhận, `submit` trả `Future` để lấy kết quả/hủy.
- Nếu `AbortPolicy` reject, exception có thể được ném trong lúc submit và caller **không** có Future của task đã được nhận.
- Nếu handler **silently discard**, caller có thể nhận một Future không bao giờ được thực thi/xong theo đường bình thường; `Future.get()` có thể chờ mãi nếu không có cancellation/timeout.
- Với CallerRunsPolicy, tác vụ có thể chạy trên **thread gọi submit** trước khi `submit` trả.
- `Future.cancel(true)` chỉ yêu cầu hủy theo API; không cưỡng bức task đang làm side effect phải dừng.

**Không dùng `submit` làm bằng chứng task chắc chắn sẽ chạy.** Hãy xét cả handler, shutdown, queue removal và lifecycle. Nếu cần workload có tính bền vững, cần một lớp admission/acknowledgment rõ ràng hơn một queue chỉ nằm trong RAM.

### Exception của task

Với `execute`, exception chưa xử lý từ `Runnable.run()` có thể đi qua cơ chế uncaught-exception/worker lifecycle. Với `submit`, exception của `Callable/Runnable` thường được giữ trong `Future` và thể hiện khi `Future.get()` ném `ExecutionException`. Điều này **khác** rejection lúc submit. Đừng nhầm “task chạy rồi thất bại” với “task không được nhận vào executor”.

## 15. Những race thực tế mà sơ đồ đơn giản không vẽ hết

### 15.1. Shutdown xảy ra sau khi queue.offer thành công

Thread A nhận được true khi queue.offer. Thread B có thể gọi `shutdown()` gần như cùng lúc. Executor tái kiểm tra trạng thái và khi cần có thể **remove task vừa enqueue rồi reject**, thay vì để một task mới lọt qua thời điểm shutdown không hợp lệ.

Đó là lý do state check và admission phải dùng một giao thức đồng bộ/atomic ở mức implementation. Người viết ứng dụng không nên tự thay thế `execute` bằng:

~~~java
if (!pool.isShutdown()) {
    pool.getQueue().add(task); // KHÔNG PHẢI cách submit task chuẩn
}
~~~

Đoạn trên là minh họa **sai**. Giữa check và add, pool có thể shutdown; việc thao tác queue trực tiếp bỏ qua quá trình tạo worker và retry/rejection. Javadoc cũng không khuyến khích dùng `getQueue()` để điều khiển công việc thông thường, ngoại trừ monitoring/debugging hoặc một số thao tác quản trị có chủ đích.

### 15.2. Queue nhận task nhưng không còn worker

Nếu workers đã kết thúc do lỗi, timeout hoặc tình huống cạnh tranh, một lần queue.offer thành công không tự bảo đảm có ai đang tiêu thụ queue. Executor kiểm tra worker count và có thể tạo worker mới (kể cả khi corePoolSize=0) để tránh queue bị bỏ mặc.

### 15.3. ThreadFactory tạo worker thất bại

Nếu `ThreadFactory.newThread(...)` trả null hoặc không thể tạo thread do tài nguyên, việc addWorker có thể thất bại. Kết quả cuối có thể là rejection; **worker count còn dưới maximumPoolSize** không bảo đảm thread mới chắc chắn được tạo.

### 15.4. Metrics thay đổi giữa hai lần đọc

`getPoolSize()`, `getActiveCount()`, `getQueue().size()` và `getCompletedTaskCount()` không phải một snapshot transaction của executor. Trong khi bạn đo lần lượt, worker có thể vừa rời task, dequeued task mới hoặc bị thu hồi. Chúng hữu ích để monitoring, nhưng không nên dùng để tự viết một thuật toán check-then-act thay thế `execute`.

## 16. KeepAlive và core timeout tác động gì tới admission?

`keepAliveTime` không thay quy tắc cơ bản core → queue → max. Nó ảnh hưởng tới **số workers đang tồn tại** khi pool idle, từ đó có thể làm những lần execute sau thấy worker count thấp hơn core.

- Khi worker count lớn hơn corePoolSize, workers dư có thể bị thu hồi sau thời gian idle theo keepAlive policy.
- Mặc định core workers được giữ khi idle, trừ trường hợp shutdown, lỗi và lifecycle cần thay thế.
- `allowCoreThreadTimeOut(true)` cho phép ngay cả core workers bị thu hồi khi idle, với điều kiện keepAliveTime > 0.
- `prestartAllCoreThreads()` tạo sẵn core workers, làm thay đổi trạng thái ban đầu trước khi request đến.
- keepAlive **không** giới hạn thời gian một task chạy, **không** ép task đang block phải kết thúc khi quá hạn.

Do đó, “lần submit tiếp theo sẽ queue hay tạo core worker?” phụ thuộc worker count hiện thời, mà worker count chịu ảnh hưởng bởi prestart, timeout, lỗi worker và kích thước core được thay đổi động.

## 17. Vì sao ưu tiên queue trước extra workers có tradeoff?

Đây là chính sách thiết kế quan trọng của ThreadPoolExecutor, không phải chi tiết nhỏ.

**Ưu điểm khi queue vừa phải:**
- Hạn chế tạo platform threads quá nhanh.
- Tái sử dụng workers hiện có, có thể giảm overhead scheduling và memory stacks.
- Hấp thụ một burst ngắn khi tốc độ nhận việc tạm vượt tốc độ xử lý.

**Nhược điểm khi queue quá lớn:**
- Tasks phải chờ lâu trước khi bắt đầu dù maximumPoolSize còn cao.
- Memory giữ nhiều Runnable/FutureTask và dữ liệu capture; dễ tăng heap usage.
- Deadline đã hết khi task còn trong queue; task có thể trở thành việc vô ích.
- Độ trễ đuôi (p95/p99) xấu nhưng hệ thống không reject, khó phát hiện overload nếu chỉ nhìn error rate.

**Ưu điểm của queue nhỏ/direct handoff:**
- Khi workers bận, pool có thể thêm workers sớm hơn tới maximum.
- Capacity giới hạn và rejection dễ thấy hơn.

**Nhược điểm:**
- Dễ tạo nhiều threads, context switching, tăng tải downstream (DB, remote API, disk).
- Nếu max nhỏ và traffic burst, nhiều task có thể bị từ chối mặc dù burst chỉ diễn ra ngắn.
- Caller phải có protocol backpressure/retry/fallback chính xác.

Không có chiến lược nào tốt nhất mọi nơi. Chọn queue dựa trên throughput của consumer, deadline, memory budget, số tác vụ đến cùng lúc và ý nghĩa của rejection.

## 18. Thread-starvation deadlock khi task con bị queue

Một trường hợp nguy hiểm:

~~~text
core = maximum = 1
workQueue là unbounded

Worker duy nhất chạy task A
  A submit task B vào cùng pool → B vào queue
  A gọi futureB.get() → chờ B hoàn tất
  B đang queue, cần worker đang bị A giữ
=> không có worker rảnh để chạy B
=> A và B có thể chờ vô hạn
~~~

Tăng `maximumPoolSize` **không cứu được** nếu queue unbounded tiếp tục nhận task B, khiến pool không tạo extra worker. Vấn đề nằm ở **cấu trúc phụ thuộc công việc** và admission policy.

Giải pháp có thể là:
- Tránh task cha blocking đợi task con trong cùng fixed pool.
- Tách các loại việc có dependency khác nhau sang executors thích hợp.
- Thiết kế completion/composition không chặn worker đang giữ capacity.
- Đảm bảo không có vòng chờ tài nguyên, không dựa vào queue growth để giải quyết liveness.
- Sử dụng timeout ở chỗ thực sự chờ nếu có nhu cầu, nhưng timeout chỉ phát hiện/chấm dứt việc chờ, không tự làm hoàn thành task con.

## 19. Quan sát và giám sát admission trong production

Các metrics nên theo dõi:

| Metric | API/cách quan sát | Gợi ý |
|---|---|---|
| core size cấu hình | `getCorePoolSize()` | Ngưỡng trước queue. |
| max size cấu hình | `getMaximumPoolSize()` | Trần worker khi không queue được. |
| worker count hiện tại | `getPoolSize()` | Có thể khác core và max. |
| số workers đang chạy task | `getActiveCount()` | Ước lượng tại thời điểm đọc. |
| tasks đang queue | `getQueue().size()` | Không tính tác vụ đã được worker lấy. |
| task hoàn tất | `getCompletedTaskCount()` | Giá trị ước lượng khi còn hoạt động. |
| tổng số task | `getTaskCount()` | Ước lượng tasks đã submit/được xử lý theo metrics. |
| số workers lớn nhất từng có | `getLargestPoolSize()` | Giúp biết đã bao giờ chạm vùng extra chưa. |
| rejection count | Custom rejection handler/metric riêng | Cần tách saturation vs shutdown. |
| thời gian chờ queue | Timestamp admission/start có protocol đúng | Không lấy latency task bằng mỗi queue size. |

Các counters/metrics của ThreadPoolExecutor giúp **quan sát**, không làm cơ chế điều tiết đồng thời tự đúng. Một custom handler có thể ghi lại rejection, nhưng không được nuốt task bắt buộc mà caller vẫn tưởng nó đã được nhận.

### Ví dụ custom handler có ghi nhận và từ chối

~~~java
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

final class CountingAbortHandler implements RejectedExecutionHandler {
    private final AtomicLong rejected = new AtomicLong();

    @Override
    public void rejectedExecution(Runnable task, ThreadPoolExecutor pool) {
        rejected.incrementAndGet();
        throw new RejectedExecutionException(
                "executor rejected task; shutdown=" + pool.isShutdown());
    }

    long rejectedCount() {
        return rejected.get();
    }
}
~~~

Đây là một **class Java hợp lệ nhưng chưa có main**. Khi đặt vào constructor executor, handler ghi nhận số lần rejection rồi ném exception để caller biết task **không được nhận**. Nếu cần phân loại nguyên nhân, đừng chỉ dựa vào snapshot `isShutdown()` vì trạng thái có thể đổi giữa quyết định reject và callback; kết hợp lifecycle logs và metrics có contract rõ hơn.

## 20. Đọc code nguồn OpenJDK: ba bước của execute

JDK thực tế diễn giải ý tưởng ở mức implementation với ba giai đoạn:

1. Nếu worker count dưới core, thử `addWorker(command, true)` và return khi thành công.
2. Nếu pool đang RUNNING, thử `workQueue.offer(command)`. Khi thành công, **tái kiểm tra trạng thái**: nếu pool không RUNNING nữa và xóa task được, reject; nếu vẫn thiếu worker, tạo worker để đảm bảo có consumer.
3. Nếu không enqueue được, thử `addWorker(command, false)` trong giới hạn max. Nếu không tạo được, gọi rejection.

**Đây là mô tả logic**, không phải bản sao mã nguồn hay triển khai để người học tự xây executor. Những kiểm tra CAS và runState của OpenJDK rất quan trọng để không có race giữa shutdown, queue, worker creation và thread failure.

Điểm tinh tế nhất là **return từ bước 2 không chỉ dựa trên một phép queue.offer()**. Bước double-check ngăn task bị bỏ mặc hoặc lọt vào trạng thái shutdown sai. Khi debug một trường hợp executor reject “bất thường”, cần nhớ trạng thái pool có thể đã thay đổi giữa các lần kiểm tra.

## 21. Checklist review cấu hình và logic tiếp nhận

1. **corePoolSize, maximumPoolSize hiện tại là bao nhiêu?** Đã xác minh maximum là tổng worker cap, không phải số extra riêng?
2. **Queue loại gì, capacity bao nhiêu?** `LinkedBlockingQueue<>` mặc định gần như unbounded, `ArrayBlockingQueue` bounded, `SynchronousQueue` không giữ backlog.
3. **Khi dưới core, có hiểu worker được tạo ưu tiên không?** Constructor mặc định không nhất thiết prestart.
4. **Sau khi đạt core, có hiểu queue được ưu tiên trước mở rộng không?** Đừng hứa max được dùng ngay khi task count tăng.
5. **Khi queue đầy, có tính max tổng cộng không?** Sau đó phải tính rejection.
6. **Rejection policy là gì?** Abort, CallerRuns, Discard, DiscardOldest hay custom? Caller có được báo rõ khi việc bị bỏ?
7. **Có shutdown đồng thời với submit không?** Nhớ executor có double-check sau enqueue và reject khi không còn RUNNING.
8. **Task có thể kết thúc trước khi submit hết batch không?** Đừng dự đoán states theo số thứ tự task nếu không có điều kiện điều khiển.
9. **Có task cha đợi task con trên cùng pool không?** Tránh thread-starvation deadlock.
10. **Queue retention bao lâu?** Deadline của task có thể đã hết trước khi bắt đầu.
11. **Có backpressure hoặc giới hạn producer không?** Không chọn unbounded queue chỉ để tránh rejection trước mắt.
12. **Có monitoring queue depth, active count, completed, rejection và tail latency?** Đã phân biệt metrics ước lượng với synchronization primitives?
13. **Timeout/cancel thuộc layer nào?** keepAlive không phải task timeout, Future.get(timeout) không tự hủy.
14. **Có shutdown/awaitTermination/cleanup đúng?** Sau shutdown, việc mới không được chấp nhận theo admission.
15. **Đã test overload với queue cố định chưa?** Dùng latch/handoff kiểm soát trình tự, không dựa sleep và đoán scheduler.

## 22. Bài tập tự kiểm tra

**Câu 1.** Pool core=2, max=8 và unbounded LinkedBlockingQueue. Hai core workers đang bận, 20 task đến thêm. Có chắc pool sẽ tạo đủ 8 workers không?

**Đáp án:** Không. Sau core, queue nhận được task; maximumPoolSize thường không có tác dụng khi queue còn capacity. Backlog tăng trong queue.

**Câu 2.** Pool core=2, max=4, queue capacity=2. Hai core đang bận, queue đã chứa hai tasks. Task tiếp theo được xử lý thế nào?

**Đáp án:** Queue offer thất bại; nếu pool còn RUNNING và tạo worker được, executor tạo worker thứ ba (extra) để chạy task mới.

**Câu 3.** Nếu pool đã có 4 workers đều bận và queue vẫn đầy, task mới sẽ làm gì?

**Đáp án:** Chuyển sang RejectedExecutionHandler. Với AbortPolicy, `RejectedExecutionException`; với CallerRunsPolicy khi pool chưa shutdown, caller chạy task.

**Câu 4.** Vì sao task được submit sau có thể chạy trước một task đang queue trước đó?

**Đáp án:** Khi queue đầy, executor có thể tạo extra worker chạy **task mới trực tiếp**. Task cũ tiếp tục đợi trong queue, nên không có bảo đảm FIFO cho toàn bộ start/completion order.

**Câu 5.** `SynchronousQueue` có chứa nhiều tasks đang đợi khi pool quá tải không?

**Đáp án:** Không. Đây là handoff queue không có storage capacity. Khi không handoff được, executor thử tạo worker đến max hoặc reject.

**Câu 6.** `ThreadPoolExecutor.execute` đã queue task, sao vẫn có thể reject?

**Đáp án:** Executor tái kiểm tra sau offer. Nếu pool shutdown trong lúc submit và task có thể bị remove, nó sẽ reject thay vì để task mới được giữ trái policy.

**Câu 7.** `getQueue().size() == 0` có chứng minh executor hoàn toàn idle không?

**Đáp án:** Không. Workers có thể đang xử lý tasks, đặc biệt với SynchronousQueue luôn không giữ backlog.

**Câu 8.** `submit` trả Future dưới DiscardPolicy có bảo đảm Future sẽ hoàn tất không?

**Đáp án:** Không. Handler có thể im lặng bỏ task; Future có thể không nhận trạng thái completion từ việc thực thi và caller chờ mãi nếu không có timeout/cancel.

**Câu 9.** Pool fixed một worker, task A submit B vào cùng pool rồi `Future.get()`; vì sao có thể deadlock?

**Đáp án:** A giữ worker duy nhất nhưng chờ B; B ở queue và không thể chạy vì không có worker rảnh. Đây là thread-starvation deadlock.

**Câu 10.** Một task bị reject do shutdown nhưng queue còn chỗ; có trái hợp đồng không?

**Đáp án:** Không. Executor không nhận công việc mới sau shutdown; rejection không chỉ xảy ra do saturation.

## 23. Tài liệu chính thức và đọc sâu

- **ThreadPoolExecutor — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- **ThreadPoolExecutor (mã nguồn OpenJDK 21):** https://github.com/openjdk/jdk21/blob/master/src/java.base/share/classes/java/util/concurrent/ThreadPoolExecutor.java
- **ExecutorService — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html
- **Executors — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html
- **ArrayBlockingQueue — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html
- **LinkedBlockingQueue — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html
- **SynchronousQueue — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SynchronousQueue.html
- **RejectedExecutionHandler — Java SE 21:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/RejectedExecutionHandler.html

**Kết luận cốt lõi:** `ThreadPoolExecutor` không đi thẳng từ core lên maximum khi có task mới. Đường thông thường là **ưu tiên tạo workers khi dưới core, ưu tiên queue khi đã đạt core, thử tạo extra workers khi queue không nhận được, và cuối cùng giao cho rejection handler**. Để hiểu chính xác, phải tính cả **queue type/capacity, shutdown races, worker lifecycle và policy xử lý overload**. Task được nhận, task đã bắt đầu và task đã hoàn tất là ba mốc khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-016">AbortPolicy, CallerRunsPolicy, DiscardPolicy và DiscardOldestPolicy: hệ quả khi quá tải</a>

<details>
<summary>Xem chi tiết</summary>

# AbortPolicy, CallerRunsPolicy, DiscardPolicy và DiscardOldestPolicy: hệ quả khi quá tải

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

- Giải thích **ThreadPoolExecutor.RejectedExecutionHandler** và thời điểm một tác vụ bị executor từ chối, đặc biệt khi **mọi worker đang bận, hàng đợi đầy hoặc executor đã shutdown**.
- Phân biệt chính xác bốn handler mặc định của JDK: **AbortPolicy**, **CallerRunsPolicy**, **DiscardPolicy**, **DiscardOldestPolicy**.
- Dự đoán **thread thực thi**, **tác vụ bị mất**, **exception tại nơi submit**, **thay đổi thứ tự công việc**, **backpressure** và **trạng thái Future** dưới từng policy.
- Thiết kế **ThreadPoolExecutor có queue giới hạn**, kiểm thử bốn policy bằng những lịch chạy được điều khiển để không phụ thuộc Thread.sleep().
- Nhận biết vì sao **submit() trả Future không bảo đảm Future sẽ hoàn tất** nếu handler âm thầm bỏ một RunnableFuture khỏi queue hoặc bỏ task mới.
- Chọn chính sách từ chối theo **mức độ được phép mất dữ liệu**, **idempotency**, **thread confinement**, **latency**, **retries**, **shutdown** và hợp đồng với producer.
- Ghi nhận metrics, kiểm soát lỗi, xử lý graceful shutdown và giải thích những giới hạn của mỗi cơ chế trong phạm vi Java concurrency.

Phạm vi là **Executor Framework của Java 17+**, chỉ nói các API thuộc JDK. Các cơ chế executor riêng của Spring được trình bày trong chương khác.

## 2. Rejection không chỉ là lỗi kỹ thuật: đó là quyết định về dữ liệu

Trong một hệ thống có tài nguyên hữu hạn, đôi khi **không thể nhận thêm công việc** đúng thời hạn. Một executor có thể chỉ có 2 worker và hàng đợi cho 10 task. Nếu cả worker đều bận và 10 task đã ở queue, task thứ 13 sẽ đi tới **rejection handler** nếu không thể bố trí thêm worker.

~~~text
Producer gửi task
        |
        v
ThreadPoolExecutor.execute(command)
        |
        +-- Có thể tạo worker theo corePoolSize? ----> Thực thi
        |
        +-- Queue nhận được command? ----------------> Đợi trong queue
        |
        +-- Có thể tăng worker tới maximumPoolSize? -> Thực thi
        |
        +-- Không thể nhận / đã shutdown ------------> reject(command)
                                                         |
                                               RejectedExecutionHandler
~~~

Đây là sơ đồ khái niệm: ThreadPoolExecutor còn có kiểm tra trạng thái và race khi shutdown, số worker thay đổi và queue thay đổi; các quyết định thực tế theo hợp đồng JDK. Điểm chính: **rejection handler được gọi khi execute không thể nhận task theo trạng thái và cấu hình hiện tại**.

Với application, mỗi hành vi mang hậu quả khác nhau:

- **Ném lỗi** cho producer để producer biết chưa được nhận.
- **Chạy task ngay trên thread producer**, chuyển áp lực lên phía gửi.
- **Lặng lẽ bỏ task vừa gửi**.
- **Bỏ task đang đợi lâu nhất trong queue**, thử đưa task mới vào.

Một nhiệm vụ có thể là log debug được phép mất hoặc lệnh ghi thanh toán không được mất. **Không thể chọn cùng một policy một cách máy móc cho mọi loại task.**

## 3. Điều kiện để ThreadPoolExecutor thực sự bão hòa

Một constructor thường dùng:

~~~java
// Đoạn minh họa trong một phương thức Java; không phải file độc lập.
ThreadPoolExecutor pool = new ThreadPoolExecutor(
    2, 2,
    0L, TimeUnit.MILLISECONDS,
    new ArrayBlockingQueue<>(10),
    new ThreadPoolExecutor.AbortPolicy()
);
~~~

Cần import các lớp tương ứng từ java.util.concurrent.

Ý nghĩa:

| Thành phần | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| corePoolSize | 2 | Mốc worker cốt lõi, thường được tạo theo nhu cầu |
| maximumPoolSize | 2 | Số worker tối đa |
| keepAliveTime | 0 | Thời gian giữ worker dư so với core; với core=max không có worker dư |
| workQueue | ArrayBlockingQueue<>(10) | Tối đa 10 task đang chờ trong queue |
| rejectedExecutionHandler | AbortPolicy | Xử lý khi executor không thể nhận thêm task |

**Worker đang chạy không nằm trong queue.** Với pool 2 worker, queue capacity 10, số task đang hoạt động/chờ tối đa trong cấu hình này là **2 đang chạy + 10 đang đợi**, trước khi tính những task đã hoàn thành; không phải “10 task tổng cộng”.

Nếu dùng **Executors.newFixedThreadPool(n)** hoặc **newSingleThreadExecutor()** mặc định, queue kiểu LinkedBlockingQueue có giới hạn rất lớn, nên những executor này **không có backpressure hữu ích chỉ nhờ giới hạn worker**. Trong thực tế queue có thể lớn đến mức gây cạn heap. Nếu dùng **newCachedThreadPool()**, pool thường có SynchronousQueue và maximumPoolSize rất lớn: khi không có worker rảnh, executor có thể tạo nhiều platform thread, làm cạn tài nguyên trước khi có tín hiệu overload hữu ích.

Muốn quan sát rejection một cách chủ động, ví dụ trong bài dùng **một worker + queue một chỗ + handler chỉ định**.

### Thuật toán nhận task giản lược

1. Nếu số worker ít hơn **corePoolSize**, executor cố tạo worker mới cho command.
2. Nếu đã đủ core, nó thường thử **enqueue** command khi đang running.
3. Nếu queue không nhận được, executor cố tạo worker khác nếu vẫn dưới **maximumPoolSize**.
4. Nếu không thể enqueue và không thể thêm worker, hoặc executor không nhận việc do shutdown, command được **reject**.

Với queue không giới hạn thực dụng, bước 2 gần như luôn nhận thêm task cho đến lúc hết tài nguyên, nên tăng maximumPoolSize cao hơn core **không nhất thiết khiến pool mở thêm worker**. Đây là lý do phải xem **worker, queue và handler cùng một lúc**.

## 4. Bảng so sánh bốn chính sách mặc định

| Handler | Khi pool đang chạy nhưng bão hòa | Producer có được báo không? | Hệ quả quan trọng |
| --- | --- | --- | --- |
| **AbortPolicy** | Ném RejectedExecutionException | **Có**, qua exception đồng bộ tại execute/submit | Không nhận task; caller phải xử lý thất bại |
| **CallerRunsPolicy** | Thread gọi execute chạy task trực tiếp nếu executor chưa shutdown | Không ném vì riêng trạng thái bão hòa này | Producer có thể bị block; task chạy sai thread dự kiến |
| **DiscardPolicy** | Bỏ task mới mà không báo | **Không** | Mất task im lặng; Future có thể không bao giờ xong |
| **DiscardOldestPolicy** | Bỏ head của work queue rồi thử execute task mới nếu chưa shutdown | Thường không, nhưng lần retry có thể lại bị từ chối tùy cấu hình | Task đã đợi bị mất; phá thứ tự và Future của task bị bỏ có thể treo |

**Khi executor đã shutdown**, hành vi cũng quan trọng:

- **AbortPolicy** vẫn ném RejectedExecutionException.
- **CallerRunsPolicy** **không** chạy task trong caller khi executor đã shutdown; nó bỏ task.
- **DiscardPolicy** bỏ task mà không báo.
- **DiscardOldestPolicy** không thử xóa/đưa lại task khi executor đã shutdown, và bỏ task.

Đây là hành vi của các **implementation handler chuẩn của JDK**. Không suy luận rằng caller thấy execute() trả về bình thường thì task đã được enqueue, sẽ được chạy, hoặc đã hoàn thành.

## 5. AbortPolicy: fail-fast để producer chủ động quyết định

**ThreadPoolExecutor.AbortPolicy** là handler mặc định khi tạo ThreadPoolExecutor mà không cung cấp handler khác. Khi được gọi, nó ném **RejectedExecutionException**.

~~~java
// Đoạn minh họa trong method Java:
try {
    pool.execute(task);
} catch (RejectedExecutionException ex) {
    // Task chưa được nhận; xử lý theo hợp đồng nghiệp vụ.
}
~~~

**Rejection xảy ra tại lời gọi gửi task**, không phải exception từ code bên trong task. Đây là hai lớp lỗi khác nhau:

- Task chưa được nhận: **RejectedExecutionException** ngay khi gọi execute/submit.
- Task đã được nhận nhưng bản thân chạy thất bại: exception trong worker; với submit() thường được lưu trong Future và xuất hiện qua **Future.get() / ExecutionException**.

### Khi nào AbortPolicy phù hợp?

- Công việc **không được phép tự biến mất**, ví dụ cập nhật dữ liệu cần được ghi nhận.
- Producer có khả năng **thông báo bận**, giảm tốc, chuyển sang đường xử lý khác hoặc ghi một durable queue.
- Cần metrics/rejection rõ ràng để biết khi nào hệ thống quá tải.
- Có hợp đồng rõ về retry, timeout và idempotency của tác vụ.

**AbortPolicy không tự retry.** Nó chỉ đưa quyết định lại cho caller. Nếu caller lập tức retry vô hạn trong vòng lặp sát nhau, hệ thống có thể càng quá tải. Retry cần backoff, giới hạn số lần, tổng deadline và đánh giá lại nhu cầu.

**Không nên catch exception rồi giả vờ thành công**:

~~~java
// Anti-pattern: caller lờ đi trạng thái không nhận công việc.
try {
    pool.execute(saveOrder);
} catch (RejectedExecutionException ignored) {
    // Người gọi vẫn nhận phản hồi "đã lưu" là sai.
}
~~~

Đây là mã minh họa; pool và saveOrder cần được khai báo. Với task quan trọng, thất bại nhận việc phải hiện diện trong trạng thái/response hoặc được chuyển sang cơ chế lưu trữ đáng tin cậy theo thiết kế.

## 6. CallerRunsPolicy: tạo backpressure bằng chính thread gửi

**CallerRunsPolicy** có logic cốt lõi: nếu executor **chưa shutdown**, thread gọi execute(command) chạy **command.run()** trực tiếp.

Nghĩa là trong overload, producer không chỉ enqueue nữa mà **phải làm công việc vừa gửi ngay lúc đó**, làm chậm khả năng tiếp tục submit thêm. Đây là một dạng **backpressure thô**, có thể hữu ích khi producer và worker đều được phép thực thi cùng task.

~~~text
Bình thường:
Producer -> execute(task) -> Queue/worker -> thực thi

Pool bão hòa, CallerRunsPolicy:
Producer -> execute(task) -> task.run() ngay trên Producer thread
                            |
                            +-- Producer bị giữ tới khi task.run() kết thúc
~~~

Điều cần lưu ý:

1. **Caller thread có thể là main thread, request thread, event-loop thread hoặc thread đang giữ lock**; không phải worker do executor quản lý.
2. Nếu task block lâu, caller bị block lâu; request latency và responsiveness có thể bị ảnh hưởng.
3. Nếu task dựa vào ThreadLocal, thread name, security context, thread affinity hoặc ownership của worker, hành vi có thể thay đổi.
4. **Không bảo đảm thứ tự chạy giữa task được caller thực thi và task đang chờ queue**. Task mới có thể hoàn thành trước task cũ đang queue.
5. Nếu caller là task đang chạy trong **cùng executor**, CallerRunsPolicy có thể gây **thực thi lồng nhau** trên worker thread, tăng call depth và làm thay đổi giả định ordering.
6. **Nếu executor đã shutdown, handler không chạy task ở caller**, nên có thể âm thầm bỏ task.

### Khi nào nên dùng?

- Task **không phụ thuộc thread cụ thể** và an toàn khi chạy trên mọi thread producer.
- Producer chấp nhận bị chặn để hệ thống không nhận vô hạn việc mới.
- Có contract về latency, timeout và thread affinity rõ ràng.
- Đã đánh giá việc caller đang giữ lock, tham gia transaction hoặc xử lý network event loop.

### Trường hợp nguy hiểm: phá thread confinement

Giả sử state mutable chỉ được phép một worker trong single-thread executor sửa. Nếu thay executor bằng một ThreadPoolExecutor một worker dùng **CallerRunsPolicy**, khi queue đầy, một producer có thể chạy task cập nhật state **trực tiếp trên thread producer**. Như vậy **một tác vụ trên worker và một tác vụ trên producer có thể cùng truy cập state**, gây race hoặc phá thứ tự.

Không chỉ là vấn đề hiệu năng: **CallerRunsPolicy có thể thay đổi mô hình đúng đắn**. Không được chọn nó chỉ vì tên gọi “backpressure” mà quên rằng task không còn chắc chắn chạy trên owner worker.

## 7. DiscardPolicy: bỏ task mới một cách im lặng

**DiscardPolicy** thực hiện **không hành động** khi reject. Lời gọi execute có thể trở về bình thường nhưng command **không được chạy**.

Đối với task kiểu log debug được phép mất, cách làm này đôi khi là lựa chọn có chủ đích để giữ hệ thống chính hoạt động. Nhưng nó nguy hiểm cho các tác vụ:

- Ghi dữ liệu tài chính hoặc nghiệp vụ bắt buộc.
- Gửi thông báo cần bảo đảm giao nhận.
- Giải phóng/đóng tài nguyên thuộc bước sau.
- Báo completion cho thread khác.
- Ghi log kiểm toán bắt buộc.
- Các task mà caller trả phản hồi “đã chấp nhận” chỉ vì execute() không ném lỗi.

**Silent loss** còn ảnh hưởng debugging: producer có thể không có dấu hiệu trực tiếp cho biết yêu cầu chưa chạy, và không có metric nếu không thêm handler/telemetry ngoài.

### Điểm tinh vi khi dùng submit()

**ExecutorService.submit()** thường đóng gói command thành một **RunnableFuture** rồi chuyển tới execute(). Nếu DiscardPolicy bỏ RunnableFuture, Future trả về caller có thể **không bao giờ chuyển sang trạng thái done**, vì task chưa được chạy và không được cancel.

Hậu quả:

- **future.get()** có thể chờ vô hạn.
- **future.get(timeout)** có thể hết thời gian chờ, nhưng điều này không chứng minh task đã từng được nhận.
- **future.isDone()** có thể vẫn là false ngay cả sau khi executor đã terminated.
- **shutdown()** không tự biến Future bị bỏ thành canceled.

Do đó, nếu cho phép silent discard, phải thiết kế cách thông báo/kiểm tra kết quả độc lập hoặc **không cung cấp Future với kỳ vọng chắc chắn hoàn tất**.

## 8. DiscardOldestPolicy: bỏ task cũ đang chờ, thử nhận task mới

Khi executor còn running và không nhận được command mới, **DiscardOldestPolicy** thực hiện gần tương đương:

~~~java
// Giả mã minh họa hành vi chính của handler mặc định:
if (!executor.isShutdown()) {
    executor.getQueue().poll(); // Bỏ head của hàng đợi.
    executor.execute(newTask);  // Thử nhận task mới lần nữa.
}
~~~

Đây là đoạn mô tả cần biến executor/newTask đã được khai báo; không phải chương trình độc lập.

**“Oldest” ở đây có nghĩa phần tử đầu work queue theo cơ chế của queue**, không phải task đang chạy lâu nhất, cũng không phải “task có timestamp nhỏ nhất toàn hệ thống”.

Với **FIFO ArrayBlockingQueue/LinkedBlockingQueue**, head thường là task đang chờ lâu nhất. Tuy nhiên:

- **Task đang chạy không bị lấy ra và dừng**.
- **Task trong queue bị bỏ không tự được cancel**.
- Nếu queue không có phần tử để poll, retry có thể vẫn reject; trong một số cấu hình không có capacity hữu ích (như SynchronousQueue), một vòng retry qua handler có thể lặp không tiến triển. Không dùng DiscardOldestPolicy một cách mù quáng với mọi loại queue.
- Với queue có thứ tự ưu tiên, head có nghĩa theo **ordering của queue**, không nhất thiết oldest theo thời gian submit; mất head có thể phá chính sách ưu tiên.
- Khi executor đã shutdown, handler không thực hiện chuỗi poll/re-execute theo logic chuẩn, và task mới bị bỏ.

### Hậu quả nghiệp vụ

Giả sử các command đến theo thứ tự:

~~~text
A đang thực thi trên worker
B chờ trong queue (capacity=1)
C được submit khi worker vẫn bận

DiscardOldestPolicy:
  B bị lấy khỏi queue và bỏ
  C được thử đưa vào queue
  Khi A hoàn thành -> C được chạy
  Kết quả: A, C chạy; B bị mất
~~~

Với tác vụ **cập nhật trạng thái có phụ thuộc thứ tự**, kết quả có thể sai: C có thể giả định B đã thực hiện. Nếu B là lệnh debit và C là lệnh xác nhận, việc bỏ B là lỗi dữ liệu nghiêm trọng. Nếu chỉ cập nhật **ảnh trạng thái mới nhất** và các bản cũ có thể bị bỏ, logic coalescing có chủ đích có thể phù hợp hơn, nhưng vẫn cần xử lý Future/metrics và tính đúng đắn của thứ tự.

## 9. Ví dụ 1: so sánh trực tiếp cả bốn policy bằng lịch xác định

Ví dụ tạo **1 worker, queue capacity=1**. Task A giữ worker cho đến khi main cho phép, task B nằm trong queue, task C gây rejection. Kết quả được sắp chữ sau completion để không phụ thuộc thứ tự log giữa thread.

Tệp **RejectionPolicyMatrixDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RejectionPolicyMatrixDemo {
    static String runCase(RejectedExecutionHandler policy)
            throws InterruptedException {

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), policy
        );

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> ran = Collections.synchronizedList(new ArrayList<>());
        boolean rejected = false;

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                    ran.add("A");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            started.await();
            pool.execute(() -> ran.add("B"));

            try {
                pool.execute(() -> ran.add("C"));
            } catch (RejectedExecutionException ex) {
                rejected = true;
            }

            release.countDown();
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }

            List<String> finished = new ArrayList<>(ran);
            Collections.sort(finished);
            return "rejected=" + rejected
                    + ", ran=" + String.join("", finished);
        } finally {
            release.countDown();
            if (!pool.isTerminated()) {
                pool.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println(
                "AbortPolicy: "
                + runCase(new ThreadPoolExecutor.AbortPolicy())
        );
        System.out.println(
                "CallerRunsPolicy: "
                + runCase(new ThreadPoolExecutor.CallerRunsPolicy())
        );
        System.out.println(
                "DiscardPolicy: "
                + runCase(new ThreadPoolExecutor.DiscardPolicy())
        );
        System.out.println(
                "DiscardOldestPolicy: "
                + runCase(new ThreadPoolExecutor.DiscardOldestPolicy())
        );
    }
}
~~~

Biên dịch và chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 RejectionPolicyMatrixDemo.java
java RejectionPolicyMatrixDemo
~~~

Stdout khi chạy bình thường, không bị interrupt từ bên ngoài:

~~~text
AbortPolicy: rejected=true, ran=AB
CallerRunsPolicy: rejected=false, ran=ABC
DiscardPolicy: rejected=false, ran=AB
DiscardOldestPolicy: rejected=false, ran=AC
~~~

**Lập luận từng trường hợp:**

- **AbortPolicy**: A giữ worker, B giữ chỗ trong queue, C không được nhận và làm execute() ném exception. A và B chạy.
- **CallerRunsPolicy**: khi C bị reject, thread main gọi trực tiếp C.run() trước khi release A. Sau đó A/B tiếp tục. C vẫn được thực thi, nhưng không phải trên worker của pool.
- **DiscardPolicy**: C bị bỏ, execute() không báo lỗi. A và B chạy.
- **DiscardOldestPolicy**: B bị bỏ khỏi queue và C được enqueue; sau khi A rời worker, C chạy.
- **Collections.sort** chỉ dùng để in tên các task đã chạy theo thứ tự ổn định; nó **không khẳng định thứ tự thực thi**.
- Sau khi awaitTermination trả true, các task đã chạy và việc thay đổi list đã kết thúc, nên đọc kết quả là phù hợp.

Trong ứng dụng thật, **đừng dùng cách bỏ B hoặc C như một chính sách mặc định** nếu business yêu cầu không mất task. Ví dụ ở đây cố ý mô phỏng bão hòa để hiểu ngữ nghĩa của từng handler.

## 10. Ví dụ 2: CallerRuns thực sự chạy ở thread gửi

Ví dụ trước cho thấy cả ba task chạy khi dùng CallerRunsPolicy; dưới đây kiểm chứng **thread identity** bằng so sánh Thread object, không dựa vào tên thread do JVM gán.

Tệp **CallerRunsThreadDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CallerRunsThreadDemo {
    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread caller = Thread.currentThread();

        try {
            Future<Integer> first = pool.submit(() -> {
                started.countDown();
                release.await();
                return 1;
            });

            started.await();
            Future<Integer> second = pool.submit(() -> 2);

            Future<Boolean> third = pool.submit(
                    () -> Thread.currentThread() == caller
            );

            System.out.println(
                    "third on caller = " + third.get()
            );

            release.countDown();
            int completed = first.get() + second.get();
            System.out.println("worker sum = " + completed);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 CallerRunsThreadDemo.java
java CallerRunsThreadDemo
~~~

Stdout:

~~~text
third on caller = true
worker sum = 3
~~~

**Vì sao xác định được?** Worker duy nhất đang chờ release, queue chứa task second, vì vậy third không thể vào worker/queue. CallerRunsPolicy gọi RunnableFuture.run() ngay trên main; Callable bên trong trả **true** vì Thread.currentThread() == caller. Future của third hoàn tất ngay trong lời submit. Main sau đó mở latch để first/second chạy xong.

**Hệ quả về thiết kế:** Nếu task dùng ThreadLocal hoặc chỉ được phép sửa state trên worker, third có thể vận hành với context khác dự kiến. Nếu thread gửi là request thread, nó bị giữ trong suốt thời gian chạy third, làm tăng request latency. Nếu thread gửi đang giữ khóa mà third cần, có thể xảy ra reentrancy, chờ khóa hoặc deadlock tùy giao thức.

## 11. Ví dụ 3: Future của task bị DiscardOldest có thể không bao giờ done

Với submit(), command trong queue thường là một **FutureTask**. DiscardOldestPolicy lấy nó ra bằng queue.poll() nhưng **không gọi cancel()**. Ta quan sát trạng thái sau khi pool đã terminated; tuyệt đối không gọi get() vô thời hạn trên Future bị bỏ.

Tệp **DiscardedFutureDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DiscardedFutureDemo {
    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<Integer> first = pool.submit(() -> {
                started.countDown();
                release.await();
                return 1;
            });

            started.await();
            Future<Integer> dropped = pool.submit(() -> 2);
            Future<Integer> newest = pool.submit(() -> 3);

            release.countDown();
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }

            System.out.println("first = " + first.get());
            System.out.println("newest = " + newest.get());
            System.out.println("dropped done = " + dropped.isDone());
            System.out.println("dropped cancelled = " + dropped.isCancelled());
        } finally {
            release.countDown();
            if (!pool.isTerminated()) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 DiscardedFutureDemo.java
java DiscardedFutureDemo
~~~

Stdout:

~~~text
first = 1
newest = 3
dropped done = false
dropped cancelled = false
~~~

**Vì sao đáng chú ý?**

1. first đang chạy và chờ latch; dropped nằm trong queue capacity=1.
2. newest đến khi pool bão hòa; handler loại dropped khỏi queue, rồi enqueue newest.
3. first và newest hoàn thành, executor chấm dứt, nhưng Future tên dropped **chưa được run hoặc cancel**.
4. dropped.isDone() vẫn false, dropped.isCancelled() cũng false. Nếu gọi dropped.get() không timeout, caller có thể chờ không có điểm kết thúc.
5. **Sự chấm dứt của pool không khiến mọi Future từng trả ra tự động done** khi policy đã bỏ task một cách im lặng.

Hành vi tương tự có thể xảy ra với **DiscardPolicy** khi nó loại ngay RunnableFuture mới gửi. Khi yêu cầu kết quả/ack cho mọi request, tránh dùng silent-discard handler mặc định hoặc cần có một giao thức hoàn tất/cancel tường minh.

## 12. Ví dụ 4: custom rejection handler báo lỗi và ghi nhận metric

Trong vận hành, cần biết hệ thống từ chối bao nhiêu request và vì sao. Một handler tùy chỉnh có thể tăng counter rồi ném RejectedExecutionException, để producer có tín hiệu rõ ràng.

Tệp **MeasuredRejectionDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MeasuredRejectionDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger rejections = new AtomicInteger();

        RejectedExecutionHandler handler = (task, executor) -> {
            rejections.incrementAndGet();
            throw new RejectedExecutionException("capacity exceeded");
        };

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), handler
        );

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                    completed.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            started.await();
            pool.execute(completed::incrementAndGet);

            boolean rejected = false;
            try {
                pool.execute(completed::incrementAndGet);
            } catch (RejectedExecutionException ex) {
                rejected = true;
            }

            release.countDown();
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool did not terminate");
            }

            System.out.println("caller saw rejection = " + rejected);
            System.out.println("rejections = " + rejections.get());
            System.out.println("completed = " + completed.get());
        } finally {
            release.countDown();
            if (!pool.isTerminated()) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 MeasuredRejectionDemo.java
java MeasuredRejectionDemo
~~~

Stdout:

~~~text
caller saw rejection = true
rejections = 1
completed = 2
~~~

**Ý nghĩa:** task đầu đã giữ worker, task thứ hai nằm trong queue, task thứ ba làm handler được gọi. Handler vừa tăng metric vừa ném lỗi. Caller biết task không được nhận, có thể báo quá tải hoặc chuyển công việc sang kênh khác. Sau khi nhả latch, hai task đã được nhận hoàn thành.

Đối với production, metric nên phân biệt **đã từ chối do pool bão hòa** với **đã từ chối vì executor shutdown**, nếu thông tin này cần cho vận hành. Đừng lạm dụng metric/logging chậm hoặc blocking I/O trong rejection handler: nó chạy trên đường submit và có thể làm producer càng chậm hoặc gây contention.

## 13. Cẩn thận với submit(), execute() và Future

### execute(Runnable)

execute() nhận Runnable. Nếu handler **AbortPolicy** ném, caller bắt được lỗi gửi task. Nếu **CallerRunsPolicy** chạy Runnable ngay, execute chỉ trở về khi Runnable.run() đã kết thúc trên caller. Nếu **DiscardPolicy** hoặc **DiscardOldestPolicy** âm thầm bỏ task, execute có thể trở về bình thường dù task không được thực hiện.

### submit(Runnable/Callable)

submit() thường tạo một **RunnableFuture**, sau đó gọi execute(RunnableFuture):

~~~text
submit(callable)
    |
    v
new RunnableFuture
    |
    v
execute(runnableFuture)
    |
    +-- worker/queue nhận -> Future có thể hoàn tất
    |
    +-- CallerRunsPolicy -> FutureTask.run trên caller -> Future done
    |
    +-- AbortPolicy -> ném RejectedExecutionException,
    |                  không trả Future thành công
    |
    +-- DiscardPolicy -> bỏ FutureTask -> Future có thể không bao giờ done
    |
    +-- DiscardOldestPolicy -> FutureTask cũ bị loại khỏi queue
                               -> Future cũ có thể không bao giờ done
~~~

**Future.get() chỉ có hợp đồng chờ kết quả của Future**, không phải hợp đồng phát hiện task đã bị silent discard bởi một handler tùy ý. Đối với các policy mất task, caller cần giao thức xác nhận **đã được chấp nhận và sẽ có trạng thái cuối** nếu đó là yêu cầu nghiệp vụ.

### Exception trong task khác rejection

Một task đã được nhận, chạy rồi ném IllegalArgumentException là **failure của task**. Với submit(), Future.get() sẽ ném ExecutionException chứa cause, không phải RejectedExecutionException của bước gửi.

Hệ thống cần phân biệt tối thiểu:

- **Rejected**: không được nhận vào executor.
- **Queued**: đã được nhận, chưa chắc đã bắt đầu.
- **Running**: đang thực hiện.
- **Succeeded / Failed**: task đã kết thúc với kết quả.
- **Cancelled / Timed out while waiting**: trạng thái Future và trạng thái nghiệp vụ có thể khác nhau.

Nếu task bị silent discard nhưng Future chưa done, nó không tự thuộc các trạng thái cuối ở trên. Đây là lý do silent policy khó dùng cho workflow đòi tracking.

## 14. Rejection do shutdown: không phải lúc nào cũng do tải cao

Khi đã gọi **shutdown()**, executor **không nhận task mới**, dù queue đang rỗng và không có worker nào bận. Lời submit tiếp theo có thể đi vào rejection handler.

- Với AbortPolicy, caller thường nhận RejectedExecutionException.
- Với CallerRunsPolicy, task bị bỏ khi executor đã shutdown: **không chạy trên caller**.
- Với DiscardPolicy, task bị bỏ.
- Với DiscardOldestPolicy, task bị bỏ mà không cố enqueue lại.

**Đây là sự khác biệt quan trọng giữa “hết capacity” và “đã đóng nhận việc”**. Nếu handler chỉ tăng counter “overloaded”, metric sẽ bị sai khi shutdown. Một custom handler có thể kiểm tra executor.isShutdown() để phân loại, nhưng snapshot đó có thể thay đổi do race; muốn tính toán chính xác số loại lỗi có thể phải có giao thức lifecycle/telemetry chặt hơn.

### Không chọn CallerRunsPolicy để ép chạy sau shutdown

Một số người dùng CallerRunsPolicy với kỳ vọng “task sẽ luôn được chạy dù pool đã đóng”. **Không đúng.** Handler chỉ chạy task trên caller nếu executor **chưa shutdown**. Một execute() quay về bình thường sau shutdown dưới CallerRunsPolicy không phải bằng chứng task đã thực thi.

### Submit sau shutdown với DiscardPolicy

Future trả về từ submit sau shutdown dưới DiscardPolicy có thể bị bỏ mà không done, giống tình huống bão hòa. Vì vậy cần quản lý vòng đời producer và executor: **dừng producer trước khi shutdown executor**, hoặc quy định cách báo lỗi rõ khi gửi vào executor đã đóng.

## 15. CallerRunsPolicy là backpressure nhưng không phải điều tiết hoàn hảo

Khi producer tự chạy task, tốc độ submit thường giảm, giảm áp lực lên queue. Tuy nhiên mức độ phụ thuộc vào đặc tính task:

- Nếu task rất nhanh, caller chạy nó rất nhanh và có thể tiếp tục flood ngay lập tức.
- Nếu producer có nhiều thread, **nhiều caller có thể cùng chạy task**, làm tổng số task đang thực thi **vượt số worker của pool**. Do đó **maximumPoolSize không còn là giới hạn tổng concurrency** nếu tính cả caller-run tasks.
- Nếu caller là event-loop thread, request thread hoặc thread đang giữ lock, chạy task có thể ảnh hưởng lớn đến độ trễ và tiến độ của hệ thống khác.
- Nếu task yêu cầu thread name, ThreadLocal, transaction context hoặc security context nhất định, chạy trên caller thay đổi các giả định đó.
- Nếu task có thể block trên nguồn lực mà producer đang giữ, chờ có thể tạo deadlock.

### Trường hợp cần hard cap tổng concurrency

Nếu yêu cầu là “không quá K thao tác gọi dịch vụ bên ngoài đồng thời từ **mọi thread**”, **CallerRunsPolicy không tự bảo đảm K**. Cân nhắc Semaphore/quota độc lập bao quanh nơi gọi dịch vụ, hoặc policy từ chối rõ ràng khi bão hòa, với ranh giới permit hợp lệ.

Một executor chỉ giới hạn **worker của chính nó**; nó không quản lý số task chạy trực tiếp trên mọi producer.

## 16. Khi nào được phép bỏ task?

Một chiến lược loss-tolerant phải dựa trên đặc tả, không chỉ theo tên policy:

| Loại công việc | Có thể cân nhắc mất task? | Điều kiện cần thiết |
| --- | --- | --- |
| Telemetry debug tần suất cao | Có thể | Được phép thiếu mẫu; có metric dropped |
| Metrics cập nhật liên tục | Có thể | Snapshot kế tiếp thay thế được snapshot trước |
| Cache warm-up | Có thể | Cache miss vẫn có đường xử lý đúng |
| UI preview frame cũ | Có thể | Chỉ trạng thái mới nhất quan trọng, có cơ chế coalescing rõ |
| Ghi hóa đơn / chuyển tiền | Thường không | Cần xác nhận hoặc durable handoff, retry idempotent |
| Gửi kết quả task mà caller đang Future.get() | Không nên silent discard | Future phải đạt trạng thái cuối hoặc báo lỗi rõ |
| Tác vụ cleanup tài nguyên | Thường không | Bỏ task có thể gây rò tài nguyên |
| Xử lý lệnh có dependency trước-sau | Không thể bỏ tùy ý | Bỏ B có thể làm C sai điều kiện |

**DiscardOldestPolicy không tự tương đương “chỉ giữ giá trị mới nhất của mỗi key”.** Nó bỏ head của **toàn queue**, không coalesce theo key, không xem các dependency, không biết tính thay thế của payload. Nếu business cần latest-per-key, hãy tự định nghĩa queue/coalescing theo key và hợp đồng completion rõ ràng.

## 17. Chọn policy theo đặc tả: decision matrix

| Yêu cầu chính | Policy khởi đầu | Vì sao |
| --- | --- | --- |
| Task bắt buộc được nhận hoặc báo lỗi rõ | **AbortPolicy** / custom handler ném lỗi | Producer không bị silent loss |
| Producer có thể tự làm task và chấp nhận bị giữ | **CallerRunsPolicy** | Tạo áp lực ngược, nhưng phải chứng minh thread affinity và concurrency |
| Task mới có thể bỏ an toàn, không ai chờ Future | **DiscardPolicy**, kèm telemetry | Silent drop chỉ hợp lệ với loss-tolerant contract |
| Snapshot cũ trong queue có thể thay bằng task mới | **DiscardOldestPolicy** sau khi phân tích FIFO/order/dependency | Có thể giảm độ cũ, nhưng mất task chờ và Future |
| Nghiệp vụ không được mất việc qua restart | Không policy nào đủ | Cần durable queue/transaction/outbox hoặc giao thức persistence |
| Cần hard cap concurrency của resource | Executor bounded + quota/permit phù hợp | CallerRuns có thể chạy ngoài worker pool |
| Task bắt buộc chạy trên một owner thread | **Không dùng CallerRunsPolicy** | Caller execution phá confinement |
| Muốn người gửi giảm tốc có kiểm soát | Custom backpressure rõ, deadline hoặc bounded handoff | Không đồng nghĩa im lặng bỏ task |

**Lựa chọn mặc định an toàn để phát triển ứng dụng có yêu cầu không mất việc** thường là **AbortPolicy** hoặc custom handler vừa ghi metric vừa báo lỗi. CallerRunsPolicy là công cụ hữu ích nhưng phải xét **latency và execution context**. Discard và DiscardOldest chỉ phù hợp khi loss semantics thực sự được chấp thuận.

## 18. Hệ quả khi queue đầy và chính sách không thay đổi tài nguyên thật

Một sai lầm là nghĩ chọn handler tốt sẽ “giải quyết được overload”. Handler chỉ quyết định **điều gì xảy ra tại điểm không nhận được task**; nó không làm CPU, RAM, DB pool hay remote service mạnh hơn.

Giả sử:

~~~text
Input rate: 1.000 tasks/giây
Service capacity: 200 tasks/giây
Backlog growth: khoảng 800 tasks/giây nếu cứ nhận tất cả
~~~

Các hành vi:

- **AbortPolicy**: làm lộ hiện tượng overload; producer phải giảm tốc hoặc chuyển đường xử lý.
- **CallerRunsPolicy**: producer tự làm một phần công việc, thường bị chậm; nhưng có thể đẩy tải sang request thread hoặc nơi khác.
- **DiscardPolicy**: không để một số task mới tích lũy nhưng mất dữ liệu.
- **DiscardOldestPolicy**: ưu tiên task đến sau bằng cách loại task đang chờ trước; độ mới có thể tăng, nhưng việc mất task vẫn tồn tại.

Muốn ổn định lâu dài cần **capacity planning, admission control, rate limiting, bounded queue, deadline, autoscaling phù hợp, đo throughput** hoặc thay đổi thiết kế workload. Chọn handler chỉ là một phần của overload strategy.

## 19. Những lỗi thực tế thường gặp

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Tạo fixed pool và tin rằng queue tự giới hạn | Memory/backlog tăng trước khi reject | Dùng bounded queue khi cần backpressure rõ |
| Chỉ kiểm tra số worker mà bỏ qua queue | Nhầm trạng thái còn capacity | Xem worker, queue, active count và submit rate cùng nhau |
| Catch RejectedExecutionException rồi trả success | Task không nhận nhưng business báo hoàn thành | Propagate/report/retry có kiểm soát |
| CallerRunsPolicy trên task phải chạy ở owner worker | Race và sai thứ tự | Không dùng caller-run cho task thread-affine |
| CallerRunsPolicy trên request thread có SLA chặt | Latency đuôi tăng mạnh | Dùng hạn mức nhận việc hoặc báo bận rõ |
| Dùng CallerRunsPolicy để giới hạn tổng concurrency | Caller tasks cộng thêm ngoài worker | Quota/permit ở ranh giới resource |
| DiscardPolicy cho task bắt buộc | Mất task im lặng | Abort/custom handler/durable handoff |
| submit với DiscardPolicy rồi Future.get vô hạn | Future có thể không bao giờ done | Không silent discard hoặc có giao thức terminal state |
| DiscardOldestPolicy với FIFO command phụ thuộc nhau | Bị mất command cũ, sai workflow | Không bỏ tùy ý; giữ order hoặc coalesce có chủ đích |
| DiscardOldestPolicy với queue không có head thích hợp | Retry reject không tiến triển | Chọn handler hợp queue; tránh policy này với SynchronousQueue |
| Hiểu oldest là task đang chạy lâu nhất | Sai task được loại | Chỉ head trong work queue bị poll |
| Dùng isShutdown() như kiểm tra đồng bộ độc lập trước submit | Race giữa check và submit | Xử lý rejection và lifecycle đúng |
| Cho rằng shutdown tự hoàn tất Future bị bỏ | Future vẫn có thể pending | Có cơ chế cancel/report riêng |
| Retry không giới hạn sau rejection | Amplify overload | Deadline, backoff, idempotency, giới hạn retry |
| Gọi blocking API trong handler tùy chỉnh khi đang giữ khóa khác | Tăng contention/deadlock | Handler ngắn, không blocking hoặc có thiết kế chứng minh được |
| Đổi từ Abort sang Discard chỉ để hết exception | Che giấu overload, mất dữ liệu | Sửa capacity và chiến lược backpressure |

## 20. Quan sát và kiểm chứng trong hệ thống thực

Để biết rejection policy đang hoạt động như mong đợi, theo dõi:

- **Số task đã submit** theo nhóm workload.
- **Số task được chấp nhận** vào queue/worker hoặc chạy tại caller.
- **Số task bị reject/drop** theo lý do capacity hay shutdown nếu phân biệt được.
- **Queue size** và **queue age** (thời gian từ submit đến start).
- **Active worker count**, pool size, completed task count.
- **CallerRuns invocations** và thời gian producer phải chạy task.
- **DiscardOldest evictions** nếu dùng custom telemetry.
- **Latency p95/p99**, memory, thread count, số lỗi/timeout/retry.
- **Future đã hoàn tất, pending và canceled**; không chỉ đếm task đã submit.

**Lưu ý về metric:** ThreadPoolExecutor.getTaskCount(), getCompletedTaskCount(), getQueue().size(), getActiveCount() là các số đo/snapshot có giới hạn và có thể thay đổi giữa lúc đọc. Không xây dựng business logic “còn đúng một chỗ trống” bằng cách kiểm tra size rồi execute, vì thread khác có thể submit ngay sau check.

Nếu cần chính xác hóa trạng thái nhận việc, thiết kế **acknowledgement** ở ranh giới ingress, không suy từ các số đo chẩn đoán.

### Test policy nên kiểm tra điều gì?

1. Giữ worker bằng **CountDownLatch**, lấp đầy queue, rồi submit task gây rejection.
2. Kiểm tra **task nào thực sự chạy** và nhánh exception nào xuất hiện.
3. Với CallerRunsPolicy, kiểm tra **Thread identity** của task thứ ba.
4. Với Discard/DiscardOldest, kiểm tra **Future được trả có thể vẫn pending**, không gọi get vô hạn.
5. Thử submit sau shutdown; đánh giá hành vi riêng của từng handler.
6. Giả lập lỗi task và phân biệt **ExecutionException** với **RejectedExecutionException**.
7. Kiểm tra **shutdown/cleanup** để test không tự treo vì worker đang chờ latch.
8. Lập luận dựa trên hợp đồng API và invariant; một stress test chạy không lỗi không chứng minh mọi lịch chạy đều đúng.

## 21. Shutdown, cancellation, interruption và trách nhiệm cuối cùng

**ThreadPoolExecutor.shutdown()** từ chối nhận task mới nhưng vẫn xử lý task đã được chấp nhận trước đó theo hợp đồng. **shutdownNow()** cố ngắt những task đang chạy và trả danh sách task chưa bắt đầu, nhưng không bảo đảm task đang chạy sẽ dừng ngay.

**Future.cancel(true)** là yêu cầu hủy có thể interrupt worker; không cưỡng chế rollback các side effect đã phát sinh. Với task bị DiscardPolicy/DiscardOldestPolicy loại bỏ mà Future chưa done, caller có thể gọi **cancel()** nếu giữ được reference, nhưng biết **khi nào task bị mất** mới là vấn đề. Handler silent mặc định không cung cấp callback cho caller biết việc đó.

Nếu công việc liên quan thanh toán, file hoặc trạng thái tại database:

- Rejection **không** nhất thiết nghĩa chưa có side effect từ các bước trước khi submit.
- Retry có thể tạo thao tác trùng nếu không có idempotency key.
- CallerRunsPolicy có thể thực hiện side effect **ngay trong lời submit**, không đợi worker.
- DiscardOldestPolicy có thể bỏ một bước mà các bước khác đã dựa vào.
- Shutdown trong lúc producer vẫn gửi việc cần giao thức rõ để không thất lạc công việc.

Do đó, policy chỉ bảo vệ **ranh giới đưa task vào executor trong JVM**, không phải bảo đảm transaction hay exactly-once giữa nhiều dịch vụ.

## 22. Checklist trước khi chốt policy

**Đặc tả công việc:**

- [ ] Task có được phép mất không? Nếu có, mất mới hay mất cũ hợp lý?
- [ ] Task có dependency thứ tự hoặc trạng thái không được bỏ qua không?
- [ ] Caller có cần biết chính xác trạng thái “được nhận” và “hoàn thành” không?
- [ ] Có Future.get() đang chờ mọi task sẽ eventually done không?
- [ ] Task có side effect cần idempotency, retry hay durable store không?

**Thread và tài nguyên:**

- [ ] Worker và queue đã có giới hạn phù hợp với thực tế chưa?
- [ ] Task có thể chạy an toàn trên thread caller không?
- [ ] Nếu CallerRuns, tổng concurrency thực tế ở resource có còn được giới hạn không?
- [ ] Có lock/thread-affinity/ThreadLocal nào bị thay đổi khi caller trực tiếp chạy task không?
- [ ] Có giải pháp riêng cho burst, long blocking I/O và overload kéo dài không?

**Vận hành và kiểm thử:**

- [ ] Có metric rejected, discarded, queue size/age và caller-run latency không?
- [ ] Có test deterministic cho bão hòa và submit sau shutdown không?
- [ ] Có cảnh báo về Future pending do silent discard không?
- [ ] Có đường phản hồi hoặc retry/backoff có giới hạn khi AbortPolicy ném lỗi không?
- [ ] Shutdown có dừng producer hoặc báo trạng thái từ chối rõ ràng không?
- [ ] Có thu thập exception của task đã nhận qua Future/handler đúng cách không?

## 23. Câu hỏi tự kiểm tra

**Câu 1.** Worker đã bận, queue đã đầy, AbortPolicy làm gì?  
**Đáp án:** Ném RejectedExecutionException ngay trên đường execute/submit; caller phải xử lý không nhận task.

**Câu 2.** CallerRunsPolicy có luôn chạy task trên worker pool không?  
**Đáp án:** Không. Khi bão hòa và chưa shutdown, nó gọi task.run() trên chính thread gọi execute, thường là producer.

**Câu 3.** Nếu executor đã shutdown, CallerRunsPolicy có chạy task mới trên caller để “cứu” task không?  
**Đáp án:** Không. Handler chuẩn không chạy task khi executor đã shutdown và có thể bỏ nó mà không ném lỗi.

**Câu 4.** DiscardPolicy có phù hợp nếu caller luôn gọi Future.get() không timeout không?  
**Đáp án:** Không nên. Một RunnableFuture bị bỏ có thể không bao giờ done hoặc canceled, khiến get() chờ vô hạn.

**Câu 5.** DiscardOldestPolicy bỏ task nào?  
**Đáp án:** Head của work queue, không phải task đang chạy. Với FIFO queue, thường là task đã chờ lâu nhất.

**Câu 6.** Vì sao CallerRunsPolicy có thể không giữ được hard cap 4 tác vụ thực thi đồng thời?  
**Đáp án:** Ngoài 4 worker, nhiều thread producer có thể tự chạy rejected task, làm tổng concurrency vượt 4.

**Câu 7.** Dùng DiscardOldestPolicy với PriorityBlockingQueue có nghĩa chắc chắn loại task submit sớm nhất không?  
**Đáp án:** Không. Queue head do ordering của queue quyết định, không nhất thiết theo thời điểm submit.

**Câu 8.** Cần xử lý task quan trọng không mất, nên bắt đầu xem policy nào?  
**Đáp án:** AbortPolicy/custom fail-fast kèm retry/backpressure/durable handoff phù hợp. Không handler nào một mình cung cấp durability.

**Câu 9.** Thay exception bằng DiscardPolicy có làm executor mạnh hơn không?  
**Đáp án:** Không. Nó chỉ che đi việc từ chối và bỏ công việc. Phải xử lý capacity, queue, producer rate và đo tải.

**Câu 10.** Future.isDone() false sau pool.awaitTermination(true) có mâu thuẫn không?  
**Đáp án:** Không nếu FutureTask từng bị silent discard khỏi queue mà không chạy hoặc cancel. Pool đã hết task còn quản lý, nhưng Future bên ngoài vẫn pending.

**Câu 11.** Tại sao shutdown() rồi submit mới có thể bị từ chối dù queue còn trống?  
**Đáp án:** Vì executor đã ngừng nhận công việc mới; rejection không chỉ do queue đầy.

**Câu 12.** Khi task trong pool thất bại sau khi đã nhận, caller nhận loại lỗi nào?  
**Đáp án:** Với submit(), Future.get() thường ném ExecutionException có cause; khác với RejectedExecutionException xảy ra ở giai đoạn gửi task không được nhận.

## 24. Tổng kết

Bốn **RejectedExecutionHandler** chuẩn của ThreadPoolExecutor đưa ra **bốn quyết định rất khác nhau** khi executor không nhận task:

- **AbortPolicy:** báo lỗi rõ ràng, thích hợp khi producer phải chịu trách nhiệm xử lý công việc không được nhận.
- **CallerRunsPolicy:** chạy task trên thread gửi khi pool còn hoạt động; tạo một dạng backpressure nhưng ảnh hưởng latency, execution context và hard cap concurrency.
- **DiscardPolicy:** bỏ task mới im lặng; chỉ phù hợp khi loss là một phần được chấp nhận rõ trong đặc tả.
- **DiscardOldestPolicy:** loại head của queue rồi thử nhận task mới; có thể bỏ task đã chờ và làm Future của task bị loại treo mãi.

Để áp dụng đúng, luôn xét **kiểu queue, core/max workers, trạng thái shutdown, Future, thứ tự/dependency, thread confinement, metrics và trách nhiệm nghiệp vụ khi mất task**. Một rejection policy không tự sửa overload hay bảo đảm dữ liệu; nó chỉ quyết định hệ thống phản ứng thế nào tại điểm quá tải hoặc ngừng nhận công việc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-017">prestartAllCoreThreads() và allowCoreThreadTimeOut(): quản lý luồng lõi</a>

<details>
<summary>Xem chi tiết</summary>

# `prestartAllCoreThreads()` và `allowCoreThreadTimeOut()`: quản lý luồng lõi trong Java

`ThreadPoolExecutor` cho phép điều khiển số worker thread, hàng đợi công việc và cách thu hồi thread nhàn rỗi. Hai phương thức có tên dễ gây hiểu nhầm là **`prestartAllCoreThreads()`** và **`allowCoreThreadTimeOut(boolean)`**. Một phương thức chủ động khởi tạo các core worker **trước khi cần tới chúng**; phương thức kia cho phép ngay cả các core worker **chấm dứt sau khi nhàn rỗi đủ lâu**.

Chúng phục vụ hai nhu cầu có thể đối lập:

- **Giảm độ trễ tạo thread khi bắt đầu tải:** tạo trước worker bằng `prestartAllCoreThreads()`.
- **Giảm tài nguyên chiếm dụng trong giai đoạn ít việc:** cho phép core worker nghỉ hẳn bằng `allowCoreThreadTimeOut(true)`.

Không có cấu hình tốt nhất cho mọi ứng dụng. Một thread được prestart không có nghĩa đã xử lý task, và một core thread được phép timeout không có nghĩa nó sẽ chết đúng vào một mili-giây cố định. Bài học này giải thích hợp đồng Java SE, điều kiện sử dụng, chi phí, lỗi thường gặp và cách kiểm chứng bằng những chương trình Java 17+.

**Phạm vi:** nhóm **Executor Framework** trong **Java concurrency nói chung**. Bài chỉ dùng `java.util.concurrent.ThreadPoolExecutor` và các API Java SE; các chủ đề cấu hình executor riêng của Spring được để ở chương riêng.

## 1. Mô hình cơ bản của `ThreadPoolExecutor`

Một pool điển hình có bốn thành phần:

```text
              executor.execute(task)
                       |
                       v
             +-------------------+
             | ThreadPoolExecutor|
             +-------------------+
                       |
             +---------+----------+
             |                    |
       tạo worker           đưa vào workQueue
       khi cần               khi phù hợp
             |                    |
             v                    v
       +-----------+        +------------+
       | workers   | <----- | task queue |
       | (threads) |        +------------+
       +-----------+
             |
             v
         chạy task
             |
        chờ task kế tiếp
```

Một số thuộc tính quan trọng:

| Thuộc tính | Vai trò |
| --- | --- |
| `corePoolSize` | Ngưỡng số core worker mà pool ưu tiên tạo khi có công việc; **không phải lời hứa luôn giữ từng ấy thread sống** |
| `maximumPoolSize` | Giới hạn worker tối đa trong cấu hình |
| `keepAliveTime` | Khoảng idle được phép trước khi worker thuộc diện timeout có thể bị thu hồi |
| `workQueue` | Nơi chờ của task chưa có worker nhận |
| `ThreadFactory` | Cách tạo thread, tên, daemon status, handler... |
| `RejectedExecutionHandler` | Cách xử lý khi không nhận thêm task |
| `allowCoreThreadTimeOut` | Quy tắc liệu cả core worker cũng có thể timeout khi idle |

Đây là **cấu hình luồng thực thi**, không phải bảo đảm rằng mọi task sẽ bắt đầu trong một thời gian xác định. Lịch scheduler, độ dài queue, khả năng chặn trong task và tài nguyên máy đều ảnh hưởng độ trễ.

## 2. Phân biệt core thread và non-core thread

Trong `ThreadPoolExecutor`, "core" thường diễn tả **chính sách số lượng worker**. Không nên hình dung mỗi Java `Thread` có một thuộc tính bất biến tên "core" rồi giữ danh tính này suốt đời.

Theo mô hình cơ bản khi gọi `execute`:

1. Nếu số worker ít hơn `corePoolSize`, executor thường cố tạo worker mới cho task.
2. Khi số worker đã đạt ngưỡng core, executor thường cố **đưa task vào queue**.
3. Nếu queue không nhận được task (ví dụ bounded queue đầy), pool có thể cố tạo thêm worker đến `maximumPoolSize`.
4. Nếu không thể nhận thêm theo các quy tắc trên, task đi qua `RejectedExecutionHandler`.

Các thao tác kiểm tra trạng thái đồng thời có thể race với việc shutdown hoặc điều chỉnh cấu hình; `ThreadPoolExecutor` thực hiện thêm kiểm tra nội bộ. Vì thế bốn bước trên là **quy tắc hiểu và thiết kế**, không phải lời hứa thứ tự thực thi chính xác tới từng instruction.

Một cấu hình với **queue không giới hạn** (chẳng hạn `LinkedBlockingQueue` mặc định) thường khiến `maximumPoolSize` ít có cơ hội phát huy vì phần lớn task được xếp hàng sau khi pool đạt core. Ngược lại, với bounded queue nhỏ, pool có thể tăng worker vượt core khi queue đầy.

## 3. Core size không mặc định là số thread đã được tạo

Xét:

```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        3, 5, 30, TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>());
```

Đây là đoạn minh họa, cần import `ThreadPoolExecutor`, `TimeUnit` và `LinkedBlockingQueue` nếu đặt trong class.

Ngay sau constructor, với cấu hình mới không chạy task, `pool.getCorePoolSize()` là `3` nhưng `pool.getPoolSize()` thông thường là `0`. **Constructor không tự động tạo đủ ba worker**.

Nếu caller gửi task, pool có thể tạo core worker theo nhu cầu. Điều này tiết kiệm tài nguyên lúc mới khởi động, nhưng một vài task đầu có thể phải chịu thêm chi phí tạo thread và gọi `Thread.start()`.

`getCorePoolSize()` là **giá trị cấu hình**. `getPoolSize()` là **số worker hiện đang thuộc pool** tại thời điểm đo; `getActiveCount()` ước lượng số worker đang thực thi task. Ba số này không nên dùng thay cho nhau.

## 4. `prestartAllCoreThreads()` làm gì?

Chữ ký public:

```java
public int prestartAllCoreThreads()
```

Phương thức yêu cầu `ThreadPoolExecutor` **khởi động những core worker chưa có**, tối đa theo cấu hình core hiện tại, thay vì đợi task mới kích hoạt việc tạo worker.

**Giá trị trả về là số worker mới được khởi động bởi lần gọi đó**, không phải tổng worker đang chạy task. Ví dụ, pool mới có `corePoolSize == 3` thường có thể trả `3`. Lời gọi kế tiếp, khi ba worker ấy vẫn hiện diện, trả `0`.

Điều cần nhớ:

- `prestartAllCoreThreads()` **không** tự gọi `execute` hoặc tạo task nghiệp vụ.
- Nó **không** làm `maximumPoolSize` tăng, không tự tạo vượt giới hạn core.
- Nó có thể gọi `ThreadFactory` để tạo thread, dẫn đến chi phí lập tức lúc startup.
- Nó **không cam kết** các thread mới đã hoàn tất phần khởi tạo ứng dụng do task định nghĩa, hoặc đã chạy tới một checkpoint nghiệp vụ.
- Nếu cấu hình đồng thời thay đổi, pool đang shutdown hoặc `ThreadFactory` không tạo được thread như mong đợi, không nên suy ra rằng mọi lần gọi đều trả đúng `corePoolSize`.
- Giá trị trả về có thể bằng `0` khi pool đã có đủ worker hoặc không còn core worker thiếu để khởi động.

**Phân biệt prestart và warm-up:** prestart là khởi động worker của executor. Nếu task đầu cần kết nối, lazy class initialization, nạp cache hay mở tài nguyên, prestart **không tự làm** các bước đó. Warm-up nghiệp vụ cần task/khởi tạo riêng và phải có chính sách an toàn.

## 5. Ví dụ Java 17+: khởi động toàn bộ core worker

Lưu thành `PrestartAllCoreThreadsDemo.java`:

```java
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PrestartAllCoreThreadsDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2,
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());

        try {
            System.out.println("Before: " + pool.getPoolSize());

            int newlyStarted = pool.prestartAllCoreThreads();

            System.out.println("Started: " + newlyStarted);
            System.out.println("After: " + pool.getPoolSize());
            System.out.println("Second call: "
                    + pool.prestartAllCoreThreads());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac PrestartAllCoreThreadsDemo.java
java PrestartAllCoreThreadsDemo
```

Stdout với executor hoạt động bình thường:

```text
Before: 0
Started: 2
After: 2
Second call: 0
```

**Phân tích:**

1. Pool vừa tạo, chưa có task, nên ban đầu chưa tạo worker.
2. `prestartAllCoreThreads()` tạo đủ hai core worker theo cấu hình. Chúng chủ yếu chờ lấy task từ work queue.
3. `getPoolSize()` lúc này phản ánh hai worker đã được thêm vào pool, **không có nghĩa hai worker đang chạy tác vụ**.
4. Vì không thay đổi core size và worker không được phép idle-timeout ở cấu hình mặc định, lời gọi thứ hai không cần tạo thêm.
5. `finally` thực hiện shutdown để ví dụ không giữ process sống bằng những worker nhàn rỗi.

Nếu `ThreadFactory` được tùy biến để từ chối tạo thread, hoặc pool bị thay đổi/shutdown từ bên ngoài, kết quả có thể khác. Ví dụ dùng executor mới, mặc định và chỉ một thread main quản lý cấu hình.

## 6. `prestartCoreThread()` khác `prestartAllCoreThreads()`

Ngoài phương thức tạo tất cả, còn có:

```java
public boolean prestartCoreThread()
```

Nó cố tạo **một** core worker nếu pool chưa có đủ, trả `true` khi một worker mới được tạo; nếu không tạo thêm, trả `false`. Đây là cách điều khiển mức tăng dần.

| API | Công việc | Giá trị trả về |
| --- | --- | --- |
| `prestartCoreThread()` | Thử tạo một core worker còn thiếu | `boolean` |
| `prestartAllCoreThreads()` | Cố tạo toàn bộ core worker còn thiếu | `int`: số worker mới |

Lưu thành `PrestartOneByOneDemo.java`:

```java
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PrestartOneByOneDemo {
    public static void main(String[] args) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                3, 3,
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
        try {
            System.out.println("First: " + pool.prestartCoreThread());
            System.out.println("Second: " + pool.prestartCoreThread());
            System.out.println("Third: " + pool.prestartCoreThread());
            System.out.println("Fourth: " + pool.prestartCoreThread());
            System.out.println("Remaining: "
                    + pool.prestartAllCoreThreads());
            System.out.println("Pool size: " + pool.getPoolSize());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac PrestartOneByOneDemo.java
java PrestartOneByOneDemo
```

Stdout:

```text
First: true
Second: true
Third: true
Fourth: false
Remaining: 0
Pool size: 3
```

Với pool mới, không có task, không có concurrent reconfiguration và core timeout đang tắt, ba lời gọi đầu lần lượt tạo worker; lời gọi thứ tư không còn vị trí core để tạo. Sau đó `prestartAllCoreThreads()` trả `0`.

## 7. `allowCoreThreadTimeOut(boolean)` thay đổi điều gì?

Chữ ký:

```java
public void allowCoreThreadTimeOut(boolean value)
```

Theo mặc định, **core worker không bị thu hồi chỉ vì đã idle quá `keepAliveTime`**, còn worker trên ngưỡng core được phép kết thúc sau khi idle đủ lâu. Khi bật:

```java
pool.allowCoreThreadTimeOut(true);
```

thì **core worker cũng áp dụng chính sách idle timeout**. Trong giai đoạn không có task, pool có thể giảm **tới 0 worker**, kể cả khi `corePoolSize` vẫn là số dương.

Một số hệ quả:

- `corePoolSize` **không tự đổi thành 0**; nó vẫn là cấu hình số core dùng khi tạo worker.
- Những worker không nhận thêm việc trong `keepAliveTime` có thể chấm dứt, tùy tiến độ thực thi và trạng thái pool.
- Khi có task mới, pool có thể tạo worker trở lại; lần tạo này lại mang chi phí khởi tạo thread.
- Không có bảo đảm "sau đúng 200 ms mọi core worker biến mất". Scheduler, lúc worker bắt đầu đợi queue, việc có task mới và cách triển khai đều ảnh hưởng thời điểm quan sát.
- Chỉ worker **idle** mới là đối tượng của chính sách thu hồi idle. Một task chạy lâu không bị phương thức này tự interrupt hoặc hủy.

Truy vấn cấu hình bằng:

```java
boolean enabled = pool.allowsCoreThreadTimeOut();
```

Đây là đoạn minh họa với `pool` là `ThreadPoolExecutor`. Phương thức truy vấn có tên **`allowsCoreThreadTimeOut()`**, không phải `isCoreThreadTimedOut()`.

## 8. Điều kiện bắt buộc: `keepAliveTime > 0`

`ThreadPoolExecutor` không cho bật core-thread timeout nếu thời gian keep-alive hiện tại **không dương**. Ví dụ:

```java
pool.allowCoreThreadTimeOut(true);
```

Nếu `pool` đang có `keepAliveTime == 0`, lời gọi trên ném **`IllegalArgumentException`**.

Vì thế, thứ tự cấu hình an toàn thường là:

```java
pool.setKeepAliveTime(250, TimeUnit.MILLISECONDS);
pool.allowCoreThreadTimeOut(true);
```

Hoặc ngay từ constructor, chọn keep-alive dương trước khi bật core timeout.

Một điểm tinh tế: khi core timeout **đang bật**, cố đặt keep-alive không dương bằng `setKeepAliveTime(...)` cũng không phù hợp và có thể bị từ chối theo hợp đồng. Nếu muốn trở về chính sách core không timeout, **tắt core timeout trước**, sau đó mới điều chỉnh keep-alive về 0 nếu thực sự cần.

**Mặc định `allowCoreThreadTimeOut(false)`** không có nghĩa giữ mọi thread bất chấp shutdown: shutdown, worker failure hoặc thay đổi cấu hình có thể dẫn đến worker rời pool. Nó chỉ nói rằng idle-timeout thông thường không áp dụng cho các core worker trong trạng thái pool đang vận hành.

## 9. Ví dụ Java 17+: cấu hình và kiểm tra lỗi keep-alive

Lưu thành `CoreThreadTimeOutValidationDemo.java`:

```java
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CoreThreadTimeOutValidationDemo {
    public static void main(String[] args) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2,
                0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());

        try {
            boolean rejected = false;
            try {
                pool.allowCoreThreadTimeOut(true);
            } catch (IllegalArgumentException e) {
                rejected = true;
            }

            System.out.println("Zero keepAlive rejected: " + rejected);

            pool.setKeepAliveTime(250, TimeUnit.MILLISECONDS);
            pool.allowCoreThreadTimeOut(true);

            System.out.println("Core timeout enabled: "
                    + pool.allowsCoreThreadTimeOut());
            System.out.println("KeepAlive ms: "
                    + pool.getKeepAliveTime(TimeUnit.MILLISECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac CoreThreadTimeOutValidationDemo.java
java CoreThreadTimeOutValidationDemo
```

Stdout chính xác:

```text
Zero keepAlive rejected: true
Core timeout enabled: true
KeepAlive ms: 250
```

Điều được chứng minh là **quy tắc cấu hình**, không phải đo thời điểm thread hết hạn. Ví dụ không prestart hoặc submit task, vì vậy `getPoolSize()` có thể vẫn là `0` trong suốt chương trình dù cấu hình core size là 2.

Chỉ bật core timeout cũng **không tự tạo worker**. Đây là điểm khác biệt trọng tâm với `prestartAllCoreThreads()`.

## 10. `keepAliveTime` có hiệu lực trong trường hợp nào?

Không phải đồng hồ đo tuổi đời của thread từ lúc được tạo. `keepAliveTime` liên quan đến thời gian **worker idle trong lúc chờ công việc mới**, với chính sách timeout hiện hành.

Xét ba worker của pool, không worker nào đang chạy task:

```text
corePoolSize = 2
maximumPoolSize = 4
keepAliveTime = 5 seconds

Pool có 3 workers, cả 3 đều idle:

Core timeout OFF:
  worker vượt ngưỡng core có thể hết hạn khi idle;
  pool thường giữ tối đa cấu hình core để chờ task.

Core timeout ON:
  mọi worker idle đều thuộc diện timeout;
  pool có thể giảm về 0 nếu không có task mới.
```

Đây là mô hình ý nghĩa, không phải mô tả một thứ tự giết thread cụ thể.

**Lưu ý giới hạn của timeout:**

- Nó **không** làm task đang chạy bị interrupt khi task vượt 5 giây.
- Nó **không** tương đương `Future.get(5, SECONDS)` hay deadline của request.
- Nó **không** giải phóng thread-local value do bạn cố giữ reference ở nơi khác.
- Nó chỉ liên quan đến **quản lý lifecycle worker nhàn rỗi** theo chính sách pool.

Vì vậy, không nên dùng `allowCoreThreadTimeOut(true)` như cơ chế timeout hoặc cancellation cho **tác vụ**.

## 11. Kết hợp prestart và core timeout có mâu thuẫn không?

**Không mâu thuẫn về API**, nhưng hai cơ chế đẩy vòng đời worker theo hai hướng:

```text
             prestartAllCoreThreads()
                       |
                       v
                tạo core workers
                       |
                 chưa có task
                       |
             chờ trên work queue
                       |
                +------+------+
                |             |
       core timeout OFF  core timeout ON
                |             |
       giữ worker idle   idle đủ lâu có thể retire
                |             |
           task mới?         task mới?
                |             |
           tái sử dụng     tạo worker lại nếu cần
```

Khi `allowCoreThreadTimeOut(true)` và keep-alive rất ngắn, những worker vừa prestart có thể hết hạn trước khi tải thực sự tới. Khi đó prestart **có thể không còn tiết kiệm chi phí tạo thread cho burst sau**.

Nếu mục tiêu là "luôn có hai worker sẵn", cấu hình cho phép core timeout về 0 **không thực hiện cam kết đó**. Nếu mục tiêu là "chỉ warm up ở đầu burst rồi cho nghỉ lúc im lặng", cấu hình prestart kết hợp core timeout có thể hợp lý, nhưng phải đánh giá **lúc nào prestart** và chi phí tạo/thu hồi thực tế.

## 12. Quan sát core worker thu hồi: đừng coi thời điểm là deterministic

Ta có thể tạo pool:

```java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        2, 2,
        150, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<Runnable>());

pool.allowCoreThreadTimeOut(true);
pool.prestartAllCoreThreads();
```

Đây là đoạn minh họa, cần imports tương ứng.

Nếu **không có task**, hai worker idle có thể kết thúc sau khoảng keep-alive. Nhưng một đoạn:

```java
Thread.sleep(200);
System.out.println(pool.getPoolSize());
```

**không được tuyên bố luôn in 0** trong mọi lịch chạy/JVM. `sleep` của thread đo không bảo đảm hai worker đã thật sự chạy, vào trạng thái đợi và được scheduler thu hồi tại thời điểm main tỉnh lại.

### Chương trình quan sát có giới hạn thời gian

Lưu thành `ObserveCoreRetirementDemo.java`:

```java
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ObserveCoreRetirementDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2,
                150, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());

        try {
            pool.allowCoreThreadTimeOut(true);
            int started = pool.prestartAllCoreThreads();

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(5);

            while (pool.getPoolSize() != 0
                    && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }

            System.out.println("Started initially: " + started);
            System.out.println("Retired within observation: "
                    + (pool.getPoolSize() == 0));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
```

```bash
javac ObserveCoreRetirementDemo.java
java ObserveCoreRetirementDemo
```

**Đầu ra minh họa trong một lần chạy bình thường:**

```text
Started initially: 2
Retired within observation: true
```

Dòng thứ hai **không được cam kết tuyệt đối**: nếu thread chưa có thời gian chạy/retire trước deadline quan sát, kết quả có thể là `false`. Ví dụ dùng bounded polling chỉ để **quan sát**, không dùng nó làm bằng chứng tiến độ tuyệt đối hoặc cách phối hợp nghiệp vụ. Muốn chờ task hoàn tất, dùng `Future`/`CountDownLatch` thay vì polling số thread.

## 13. Bốn cấu hình thường gặp

Giả sử `corePoolSize > 0` và pool chưa shutdown:

| Prestart? | Core timeout? | Hành vi điển hình |
| --- | --- | --- |
| **Không** | **Không** | Worker được tạo dần khi có task; core worker thường ở lại khi idle |
| **Có** | **Không** | Tạo core worker lúc prestart; có worker nhàn rỗi sẵn để nhận task |
| **Không** | **Có** | Tạo worker khi có tải; cho phép core worker được thu hồi khi idle |
| **Có** | **Có** | Worker có sẵn ngay sau prestart nhưng có thể retire nếu burst không tới kịp |

Đây là tóm tắt **chính sách**, không phải lời bảo đảm số worker chính xác tại từng nanosecond. Còn phải xét `workQueue`, `ThreadFactory`, load, shutdown và điều chỉnh cấu hình động.

Một ví dụ workload:

- **Service có burst ngay sau startup và yêu cầu latency đầu tiên thấp:** cân nhắc prestart và không bật core timeout quá ngắn.
- **Batch processor chỉ thỉnh thoảng chạy:** có thể cho core timeout để không giữ idle thread mãi.
- **Ứng dụng tải liên tục:** nhiều thread đang bận; core timeout chỉ ảnh hưởng khi thực sự có giai đoạn nhàn rỗi.
- **Environment tài nguyên khắt khe:** không nên prestart rất nhiều core worker chỉ vì có CPU core; thread stack và scheduler vẫn có chi phí.
- **Executor chạy công việc blocking lâu:** giữ sẵn worker không giải quyết việc queue ùn ứ; cần sizing và giới hạn concurrency theo tải thực tế.

## 14. Queue quyết định liệu `maximumPoolSize` có phát huy

Một nhầm lẫn phổ biến: đặt `corePoolSize = 2`, `maximumPoolSize = 20` rồi dùng queue không giới hạn, tưởng khi 20 task tới, pool tự tạo 20 thread.

Trong cấu hình điển hình với `LinkedBlockingQueue` không giới hạn, sau khi tạo đủ core workers, phần lớn task mới sẽ được **xếp hàng**, chứ không kích hoạt tạo worker vượt core. `maximumPoolSize` chỉ phát huy khi pool không thể enqueue hoặc một tình huống nội bộ đặc biệt yêu cầu tạo worker.

Mặt khác, nếu dùng `ArrayBlockingQueue` có capacity nhỏ, task đến khi core workers bận và queue đầy có thể khiến pool tạo **non-core worker** cho đến max.

**Prestart core worker không làm thay đổi logic hàng đợi.** Nó chỉ tạo một số worker sớm hơn. Việc queue có giới hạn và rejection policy vẫn là quyết định quan trọng cho backpressure.

## 15. Ví dụ Java 17+: prestart không thay thế sizing theo queue

Lưu thành `QueueGrowthAfterPrestartDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class QueueGrowthAfterPrestartDemo {
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 3,
                30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(1));

        CountDownLatch hold = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch extraStarted = new CountDownLatch(1);

        try {
            System.out.println("Prestarted: "
                    + pool.prestartAllCoreThreads());

            pool.execute(() -> {
                firstStarted.countDown();
                await(hold);
            });

            firstStarted.await();

            pool.execute(() -> {
                // Đang chờ trong queue cho tới khi có worker rảnh.
            });

            pool.execute(() -> {
                extraStarted.countDown();
                await(hold);
            });

            extraStarted.await();

            System.out.println("Core size: " + pool.getCorePoolSize());
            System.out.println("Current workers: " + pool.getPoolSize());
            System.out.println("Queued tasks: "
                    + pool.getQueue().size());
        } finally {
            hold.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
```

```bash
javac QueueGrowthAfterPrestartDemo.java
java QueueGrowthAfterPrestartDemo
```

Stdout khi chạy bình thường:

```text
Prestarted: 1
Core size: 1
Current workers: 2
Queued tasks: 1
```

Giải thích lịch chạy có kiểm soát:

1. Pool có core size 1, max 3, queue capacity 1. `prestartAllCoreThreads` tạo một core worker.
2. Task thứ nhất chạy trên một worker và **đợi `hold`**, giữ worker đó bận. Main đợi `firstStarted` trước khi gửi task khác.
3. Task thứ hai được đưa vào queue duy nhất và không được lấy ra vì worker đầu đang chờ latch.
4. Queue đã đầy khi task thứ ba được submit; pool tạo thêm một worker (vượt core nhưng chưa chạm max 3) để chạy task đó. Task thứ ba cũng đợi `hold`.
5. Main đợi `extraStarted`, nên thời điểm in có hai worker đang bị chặn bởi latch và đúng một task nằm trong queue.
6. `finally` mở latch, cho các worker kết thúc, rồi shutdown executor.

**Không suy ra `prestartAllCoreThreads()` đã tạo worker thứ hai.** Nó chỉ tạo một core worker. Worker thứ hai được tạo do **queue đầy khi có task mới**, không phải do prestart.

## 16. Khi nào prestart giúp giảm latency?

Có hai nhóm chi phí cần phân biệt:

1. **Chi phí tạo worker**: `ThreadFactory.newThread`, khởi động thread, cấu trúc nội bộ của JVM và scheduler.
2. **Chi phí nghiệp vụ của task**: class loading, JIT, network handshake, đọc database, khởi tạo resource.

`prestartAllCoreThreads()` có thể chuyển một phần **chi phí nhóm 1** sang lúc startup. Nó không tự thực hiện nhóm 2.

Lợi ích có thể thấy khi:

- Những task đầu đến ngay sau startup và latency của chúng quan trọng.
- Pool có số core vừa phải; tạo thread upfront chấp nhận được.
- Có nhiệm vụ cần dùng `workQueue` đã được nạp trước khi worker bắt đầu đợi.
- Hệ thống muốn điều khiển thời điểm tạo worker trước khi mở nhận tải.

Nhưng **không có bảo đảm cứng** về thời gian task bắt đầu; core thread đã tạo vẫn cần được scheduler cấp CPU. Prestart không khắc phục CPU starvation, queue dài, deadlock hoặc task blocking.

## 17. Khi nào `allowCoreThreadTimeOut(true)` giúp tiết kiệm tài nguyên?

Một Java platform thread có chi phí quản lý, stack và tài nguyên scheduler. Pool giữ sẵn nhiều worker nhàn rỗi có thể lãng phí bộ nhớ trong hệ thống chỉ có các đợt tải ngắn.

Core timeout hữu ích hơn khi:

- Tải có **khoảng nghỉ đủ dài** để thu hồi worker.
- Thời gian khởi động lại worker ở burst sau **chấp nhận được**.
- Pool có core size dương nhưng không muốn giữ minimum idle threads quanh năm.
- Một ứng dụng có nhiều pool độc lập và muốn giảm tổng số idle thread.
- Môi trường chạy có giới hạn thread count/memory, nhưng vẫn cần pool phục vụ burst.

Đánh đổi:

- Task đến sau khi pool rỗng có thể phải chịu **cold thread creation**.
- Những burst ngắn cách nhau một khoảng lớn hơn keep-alive có thể làm thread liên tục được tạo và thu hồi (**churn**).
- Keep-alive quá ngắn có thể gây khó khăn cho latency tail và quan sát số thread.
- Thu hồi worker không phải phép đóng mọi resource do task tạo. Resource không được giải phóng đúng trong task có thể vẫn leak; `ThreadLocal` giữ dữ liệu theo vòng đời thread nhưng lifecycle và cleanup vẫn cần được thiết kế.

**Không tối ưu theo số thread nhỏ nhất bằng mọi giá.** Mục tiêu là độ trễ, throughput, memory footprint và khả năng phục hồi phù hợp workload.

## 18. Core timeout và task blocking: hiểu đúng biên giới

Nếu một worker đang thực hiện:

```java
queue.take(); // chờ lâu trong task nghiệp vụ
```

đây là **task đang chạy**, không phải "worker idle và chờ lấy task tiếp theo" của executor. `allowCoreThreadTimeOut(true)` **không tự interrupt tác vụ** hoặc đặt deadline cho lời gọi `queue.take()` bên trong task.

Điều tương tự áp dụng với:

- `Thread.sleep(60_000)` trong task.
- `Future.get()` trong task.
- Socket/database read đang block.
- Vòng lặp CPU vô hạn.
- Chờ monitor do application code tạo.

Core thread timeout quản lý thời gian **idle giữa các task**. Để giới hạn thời gian chờ của **task**, dùng timeout/cancellation của API nghiệp vụ hoặc `Future` kết hợp cooperative cancellation, chứ không dùng `keepAliveTime` của executor.

`ThreadPoolExecutor` cũng không tự tạo nhiều thread chỉ vì một core worker block trong `Future.get()`; hành vi tăng worker còn phụ thuộc queue/capacity như phần trước.

## 19. Quản lý runtime: `setCorePoolSize` và `setKeepAliveTime`

`ThreadPoolExecutor` cho thay đổi một số cấu hình khi pool còn chạy:

```java
pool.setCorePoolSize(4);
pool.setKeepAliveTime(2, TimeUnit.SECONDS);
pool.allowCoreThreadTimeOut(true);
```

Đây là đoạn minh họa, cần `pool` là `ThreadPoolExecutor` phù hợp.

**Lưu ý khi thay đổi động:**

- `setCorePoolSize` phải tôn trọng `maximumPoolSize`. Nếu core size vượt max, nhận `IllegalArgumentException`.
- Tăng core size có thể làm executor chủ động tạo thêm worker nhằm xử lý task đang chờ; không suy ra mọi thay đổi chỉ ảnh hưởng những task gửi sau đó.
- Giảm core size có thể khiến các worker vượt ngưỡng trở thành ứng viên thu hồi khi nhàn rỗi.
- Thay keep-alive hoặc bật/tắt core timeout có thể tác động cách các worker đang đợi queue được xử lý.
- Nếu core timeout đang bật, keep-alive phải dương. Đổi sang 0 cần tắt core timeout trước.
- Vì thao tác cấu hình có thể chạy đồng thời với submit/shutdown, các metric như `getPoolSize` và `getActiveCount` chỉ là snapshot gần đúng tại thời điểm đọc.

Không nên điều chỉnh pool mỗi vài mili-giây theo một metric duy nhất. Cần thiết kế chính sách ổn định, có hysteresis và quan sát chất lượng hệ thống thực tế nếu dùng adaptive sizing.

## 20. `corePoolSize == 0`: trường hợp đặc biệt

Một `ThreadPoolExecutor` có thể cấu hình `corePoolSize == 0` nếu thỏa các điều kiện constructor và `maximumPoolSize >= 1`.

Khi đó:

- `prestartAllCoreThreads()` **không có core worker nào để tạo**, nên thường trả `0`.
- Không nên dùng prestart như cách ép pool tạo một non-core worker khi core size bằng 0.
- Executor vẫn có thể tạo worker để xử lý những task được submit, theo logic queue và đảm bảo tiến độ của implementation.
- Nếu muốn giữ sẵn một worker khi không có task, cần cấu hình core size dương và chính sách timeout phù hợp.

Đây là cấu hình đặc thù. Không chọn `corePoolSize=0` và queue không giới hạn chỉ bằng suy đoán "mọi task sẽ không bao giờ chạy": implementation có cơ chế tránh để task bị bỏ chờ vô hạn chỉ vì không có worker. Hãy đọc hợp đồng `ThreadPoolExecutor.execute` và test bằng workload thực tế.

## 21. `prestartAllCoreThreads`, queue đã có việc và thay đổi thứ tự startup

Có trường hợp người lập trình tạo executor với một `BlockingQueue<Runnable>` đã chứa task trước khi pool bắt đầu xử lý. Javadoc của `ThreadPoolExecutor` có đề cập việc **prestart core threads** hữu ích khi dùng non-empty queue, để có worker lấy các task đang chờ.

Tuy vậy, hãy cẩn thận với cách đưa task vào queue:

- Gửi công việc qua `execute/submit` để executor kiểm soát chấp nhận task và thread creation.
- **Không trực tiếp thêm task vào `pool.getQueue()`** như một API lập lịch thông thường; điều đó bỏ qua logic rejection/sizing và không có lời hứa mọi task được quản lý như khi submit.
- Khi prestart, đảm bảo task lifecycle, shutdown và rejection được kiểm soát.
- Không nhầm `prestartAllCoreThreads` với `executor.submit`: prestart không trả `Future` cho công việc mới.

Nếu cần một pipeline bắt đầu xử lý chính xác tại mốc startup, thường rõ hơn là tạo executor, cấu hình, submit các task và dùng latch/barrier ở bên trong task để điều phối readiness.

## 22. `ThreadFactory` và chi phí của prestart

`ThreadPoolExecutor` dùng một `ThreadFactory` để tạo worker. Khi prestart, executor có thể gọi factory **trước khi bất kỳ task nghiệp vụ nào được submit**.

Các điều cần xem xét khi tùy biến factory:

- Đặt tên thread có ý nghĩa cho thread dump và profiler.
- Chọn daemon/non-daemon theo lifecycle phù hợp; không dựa vào daemon thread để thay shutdown đúng.
- Thiết lập `UncaughtExceptionHandler` phù hợp, nhưng nhớ executor có cách xử lý exception của task khác nhau giữa `execute` và `submit`.
- Không thực hiện blocking I/O hoặc callback phức tạp trong `ThreadFactory.newThread`.
- Không để factory trả `null` hoặc gây lỗi nếu ứng dụng vẫn giả định prestart luôn tạo đủ core threads.
- Tránh kích hoạt phụ thuộc vòng tròn: factory đợi task executor thực thi, trong khi executor đang chờ factory tạo worker.

Một ví dụ factory đặt tên, dùng cho pool có sẵn:

```java
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger counter = new AtomicInteger();

ThreadFactory factory = task -> {
    Thread thread = new Thread(task);
    thread.setName("worker-" + counter.incrementAndGet());
    return thread;
};
```

Đây là snippet minh họa, không có class `main`. Factory có thể giúp quan sát prestart thông qua tên thread. Nhưng đừng dựa vào việc thread đã có tên để khẳng định nó đã thực thi task đầu tiên.

## 23. Giám sát đúng metric, không chỉ `getPoolSize()`

Những method hữu ích của `ThreadPoolExecutor`:

| Method | Thông tin |
| --- | --- |
| `getCorePoolSize()` | Cấu hình core hiện tại |
| `getMaximumPoolSize()` | Cấu hình max hiện tại |
| `getPoolSize()` | Số worker hiện có trong pool tại thời điểm đọc |
| `getActiveCount()` | Số worker đang chạy task, mang tính ước lượng trong concurrent system |
| `getLargestPoolSize()` | Kích thước pool lớn nhất từng đạt từ khi tạo |
| `getQueue().size()` | Số task đang chờ trong queue tại thời điểm đo, không phải toàn bộ submitted tasks |
| `getTaskCount()` | Ước lượng tổng số task từng được lên lịch |
| `getCompletedTaskCount()` | Ước lượng số task đã hoàn tất |
| `getKeepAliveTime(unit)` | Giá trị keep-alive quy đổi sang đơn vị yêu cầu |
| `allowsCoreThreadTimeOut()` | Chính sách core timeout hiện hành |
| `isShutdown()` / `isTerminated()` | Trạng thái vòng đời executor |

Khi quyết định có prestart hoặc bật core timeout, nên đo ít nhất **latency các task đầu sau idle**, độ dài queue theo thời gian, số active workers, memory footprint, thread creation rate và mức sử dụng CPU.

**Không đồng nhất metric snapshot với một invariant liên thời gian.** Ví dụ `getPoolSize() == 0` ngay lúc quan sát không bảo đảm tới dòng tiếp theo pool vẫn trống. Không dùng nó để thực hiện protocol "chỉ submit khi có worker" — executor chịu trách nhiệm tạo worker khi tiếp nhận task phù hợp.

## 24. Shutdown và core worker: các chính sách timeout không thay lifecycle

Khi cần đóng pool:

```java
pool.shutdown();
if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
    pool.shutdownNow();
}
```

Đây là snippet minh họa, method bao phải xử lý/truyền `InterruptedException` từ `awaitTermination`.

- `shutdown()` từ chối công việc mới theo contract, cho phép task đã nhận tiếp tục.
- `shutdownNow()` **cố gắng** dừng task đang chạy (thường qua interrupt) và trả danh sách task chưa chạy; không bảo đảm mọi worker dừng tức thì.
- `awaitTermination(timeout, unit)` là **timeout của lần chờ termination**, không tự hủy task nếu trả `false`.
- `prestartAllCoreThreads` không phải lệnh "khởi động lại executor đã shutdown".
- `allowCoreThreadTimeOut(true)` không thay `shutdown()`: pool có thể vẫn nhận task mới và tạo worker trong tương lai dù hiện tại còn 0 thread.

**Không thể dùng core-thread timeout để đảm bảo mọi worker được giải phóng khi application kết thúc.** Shutdown và cleanup là một giao thức riêng, kể cả khi pool có thể co về 0 trong lúc vận hành.

## 25. Những lỗi dễ mắc

**1. "`corePoolSize=10` nghĩa là sau constructor có 10 threads."** Sai. Pool mặc định tạo worker theo nhu cầu; dùng prestart nếu cần tạo trước.

**2. "`prestartAllCoreThreads()` luôn trả core size."** Sai. Nó trả **số worker mới được khởi động trong lần gọi**, có thể là 0.

**3. "`prestartAllCoreThreads()` gửi task warm-up."** Sai. Nó tạo worker, không tự thực thi nghiệp vụ hay khởi tạo resource của application.

**4. "`allowCoreThreadTimeOut(true)` làm `corePoolSize=0`."** Sai. Cấu hình core giữ nguyên; chỉ idle core workers được phép retire.

**5. "Core timeout giết task chạy quá keep-alive."** Sai. Nó áp dụng khi worker **nhàn rỗi giữa các task**, không phải timeout của task.

**6. "Bật core timeout khi keepAlive bằng 0 vẫn hợp lệ."** Sai. Lời gọi ném `IllegalArgumentException`; đặt thời gian dương trước.

**7. "Sau đúng 100 ms pool chắc chắn còn 0 threads."** Sai. Thời điểm quan sát phụ thuộc scheduler, workers có đang idle hay không và timing của poll queue.

**8. "Prestart + core timeout là vô nghĩa."** Không hẳn; có thể prestart cho burst gần và thu hồi sau khoảng idle, nhưng phải benchmark churn/latency.

**9. "Prestart tạo đủ `maximumPoolSize` worker."** Sai. Nó chỉ cố tạo **core workers còn thiếu**; extra workers do workload và queue quyết định.

**10. "Queue không giới hạn sẽ tự tạo đến max khi queue dài."** Không theo quy tắc thông thường; sau ngưỡng core, pool có thể tiếp tục enqueue task.

**11. "`getPoolSize()` chứng minh pool sẵn sàng xử lý task trong deadline."** Không. Metric chỉ cho thấy số worker hiện diện, không bảo đảm scheduling hoặc free capacity.

**12. "Thread đã prestart thì task không còn latency cold-start."** Không chắc. Class loading, JIT, connection setup và resource initialization của task vẫn có thể xảy ra lần đầu.

**13. "`shutdownNow` sẽ luôn giết mọi thread lập tức."** Sai. Nó thường yêu cầu interrupt; task phải hợp tác và API blocking có hợp đồng riêng.

**14. "`ThreadFactory` có thể block tùy ý, không ảnh hưởng prestart."** Sai. Prestart gọi factory để tạo worker; chi phí hoặc lỗi factory ảnh hưởng quá trình này.

**15. "Cứ bật/tắt core timeout liên tục theo `getActiveCount()` là tối ưu."** Không có cơ sở. Metric là ước lượng; policy dao động có thể tăng churn và latency.

## 26. Chọn cấu hình theo mục tiêu vận hành

Trước khi viết:

```java
pool.prestartAllCoreThreads();
pool.allowCoreThreadTimeOut(true);
```

hãy trả lời các câu hỏi:

1. **Có cần worker sẵn ngay khi service bắt đầu nhận tải?** Nếu có, prestart có thể giảm chi phí khởi tạo core threads.
2. **Có thời gian idle dài không?** Nếu có, core timeout có thể tiết kiệm resource.
3. **Giá trị keepAlive có dương và đủ dài?** Quá ngắn gây tạo/hủy liên tục; quá dài giữ thread idle nhiều.
4. **Task đầu có khởi tạo thứ gì khác ngoài thread?** Prestart không thay warm-up nghiệp vụ.
5. **Task chủ yếu CPU-bound hay blocking I/O?** Sizing theo CPU/throughput khác nhau.
6. **Queue bounded hay unbounded?** Điều này quyết định áp lực queue và khả năng tăng vượt core.
7. **Có chính sách từ chối/backpressure không?** Prestart không loại bỏ quá tải.
8. **Task có thể giữ ThreadLocal hoặc resource riêng không?** Lập chính sách cleanup; worker retirement không thay cleanup nghiệp vụ.
9. **Ai sở hữu executor?** Shutdown đúng vòng đời, không chia sẻ một pool không kiểm soát rồi tự ý đóng.
10. **Đo hiệu quả bằng gì?** Startup latency, p95/p99 latency sau idle, queue depth, worker count, memory và thread creation.
11. **Có thay đổi cấu hình đồng thời không?** Test và monitor snapshot, tránh hứa độ lớn worker tại từng thời điểm.
12. **Có cần deadline task?** Dùng timeout/cancellation riêng; không dựa vào core keep-alive.

## 27. Câu hỏi tự kiểm tra

**Câu 1.** Tạo `ThreadPoolExecutor` core=4, max=8, không submit task. `getPoolSize()` có luôn bằng 4 sau constructor?

**Không.** Theo thiết kế thông thường pool tạo core worker khi có việc; ngay sau constructor thường có 0. Gọi `prestartAllCoreThreads()` để khởi động core workers trước.

**Câu 2.** `prestartAllCoreThreads()` được gọi hai lần trên pool core=3, không có task hay core timeout. Kết quả điển hình?

Lần đầu trả 3; lần hai trả 0. Giá trị trả về là **số worker mới của lần gọi**, không phải total pool size.

**Câu 3.** `prestartCoreThread()` trả `boolean` hay `int`?

Trả `boolean`. `prestartAllCoreThreads()` trả `int`.

**Câu 4.** Bật `allowCoreThreadTimeOut(true)` có làm task đang chạy quá 30 giây bị hủy?

Không. Core timeout chỉ áp dụng khi worker đang **idle chờ task**; task duration và cancellation là vấn đề riêng.

**Câu 5.** Có được gọi `allowCoreThreadTimeOut(true)` khi keepAlive bằng 0 không?

Không. API từ chối bằng `IllegalArgumentException`. Cần set keepAlive dương.

**Câu 6.** Sau khi bật core timeout, `corePoolSize` có tự thành 0 không?

Không. Giá trị cấu hình vẫn giữ nguyên; số worker thực tế **có thể** giảm về 0 khi idle đủ lâu.

**Câu 7.** Vì sao prestart cộng keepAlive rất ngắn có thể kém hiệu quả?

Worker được tạo sớm có thể hết hạn trước burst, buộc pool tạo lại khi task tới; gây thread churn và mất lợi ích prestart.

**Câu 8.** Đặt core=2, max=20, `LinkedBlockingQueue` không giới hạn có chắc pool tăng lên 20 khi tải cao không?

Không. Sau core, task thường được enqueue; max có thể ít được dùng nếu queue không đầy.

**Câu 9.** `getActiveCount()` bằng 0 có đồng nghĩa không có thread không?

Không. Worker có thể đang idle; cần phân biệt `getPoolSize()` với `getActiveCount()`.

**Câu 10.** `prestartAllCoreThreads()` có bảo đảm lazy database connection của task đã tạo không?

Không. Prestart tạo worker; resource do task khởi tạo chỉ được tạo khi code task thực sự chạy hoặc khi có warm-up riêng.

**Câu 11.** `allowCoreThreadTimeOut(true)` có thay `shutdown()` không?

Không. Pool có thể co về 0 lúc idle rồi lại tạo worker khi nhận task mới; shutdown mới chuyển vòng đời executor sang trạng thái không nhận task mới.

**Câu 12.** Nếu muốn prestart hai worker nhưng cũng muốn chúng có thể nghỉ hẳn khi idle, cần gì?

Đặt `corePoolSize >= 2`, chọn `keepAliveTime > 0`, bật core timeout theo policy và gọi prestart. Tuy nhiên phải chấp nhận worker có thể retire trước task mới, nên không thể đồng thời cam kết **luôn** có hai worker sẵn.

## 28. Tổng kết

- **`prestartAllCoreThreads()`** khởi động các core worker còn thiếu và trả **số worker mới khởi động**. Không tạo task, không tăng max size, không thay warm-up nghiệp vụ.
- **`prestartCoreThread()`** chỉ thử khởi động **một** core worker, trả `boolean`.
- **`allowCoreThreadTimeOut(true)`** cho phép **cả core worker** bị thu hồi khi idle lâu hơn keep-alive; pool có thể giảm về 0 dù core size vẫn dương.
- Core timeout yêu cầu **`keepAliveTime > 0`**. Khi không bật, core worker thường được giữ khi idle trong pool còn chạy.
- **Prestart** ưu tiên sẵn sàng/giảm một phần startup latency; **core timeout** ưu tiên tiết kiệm tài nguyên nhàn rỗi. Bật cả hai hợp lệ nhưng phải cân nhắc churn.
- **Queue và max size** ảnh hưởng việc tạo worker nhiều hơn khi tải tăng; prestart không thay logic enqueue/rejection.
- **Worker idle timeout khác task timeout/cancellation**. Một task block lâu không bị tự hủy vì đã quá keep-alive.
- **Vòng đời executor, shutdown, timeout, giám sát và resource cleanup** vẫn cần được thiết kế độc lập.

Khi tinh chỉnh `ThreadPoolExecutor`, hãy bắt đầu từ câu hỏi: **"Tôi muốn có thread sẵn để giảm latency ban đầu, hay muốn giải phóng idle threads sau khi tải giảm — và tải thực tế có đủ để đánh đổi chi phí tạo lại không?"**. Hai API giúp bạn điều chỉnh đúng mặt này, nhưng lựa chọn hiệu quả cần dựa trên workload, queue policy và đo đạc thay vì suy từ tên phương thức.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-framework-018">beforeExecute(), afterExecute() và cách nhận biết ngoại lệ của tác vụ submit()</a>

<details>
<summary>Xem chi tiết</summary>

# beforeExecute(), afterExecute() và cách nhận biết ngoại lệ của tác vụ submit() trong Java

> **Phạm vi:** Executor Framework trong **Java concurrency nói chung**, đặc biệt là các hook `ThreadPoolExecutor.beforeExecute(Thread, Runnable)`, `afterExecute(Runnable, Throwable)`, cách truyền ngoại lệ giữa `execute()`, `submit()` và `Future`, cũng như logging, metrics, cleanup và lifecycle. Không bàn về interception, scheduler hoặc thread-pool abstraction riêng của Spring vì phần đó có chương độc lập.

## 1. Vấn đề thực tế: task thất bại nhưng log không hiện lỗi

Giả sử một ứng dụng chạy nhiều task trong `ThreadPoolExecutor`. Khi thực thi một `Runnable` ném `RuntimeException` qua `execute()`, exception có thể thoát khỏi `run()` của worker, khiến thread kết thúc bất thường và xuất hiện trong đường xử lý uncaught exception.

Nhưng khi giao một task ném lỗi bằng `submit()`, nhiều người nhận thấy:

- `afterExecute(r, t)` **được gọi** nhưng `t == null`;
- không có stack trace tự động xuất hiện theo cách họ kỳ vọng;
- `Future.get()` lại ném `ExecutionException` chứa ngoại lệ thực sự.

Đây không phải lỗi của Java. `submit()` thường **bọc Runnable/Callable trong một `FutureTask`**. `FutureTask.run()` bắt lỗi của task, lưu outcome vào trạng thái Future và **không ném lại cùng lỗi đó ra ngoài `run()`**. Vì vậy `ThreadPoolExecutor` thường xem lần gọi `task.run()` của wrapper đã trở về bình thường và truyền `null` ở tham số `Throwable` của hook.

**Kết luận quan trọng:** `afterExecute` không phải một “bẫy bắt tất cả lỗi” nếu chỉ kiểm tra tham số `Throwable`. Khi `Runnable r` là một `Future<?>` đã hoàn tất, cần kiểm tra outcome qua `Future.get()` và xử lý `ExecutionException` / `CancellationException` đúng cách.

## 2. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt `Executor.execute(Runnable)` và `ExecutorService.submit(...)` về cách đóng gói task, kết quả và exception.
2. Giải thích chính xác khi nào `beforeExecute`, `afterExecute` và `terminated` được `ThreadPoolExecutor` gọi.
3. Giải thích tham số `Throwable t` của `afterExecute` thể hiện điều gì và vì sao thường là null với `submit()` dù task ném exception.
4. Viết hook `afterExecute` để nhận diện lỗi của cả task được `execute()` giao trực tiếp và task dạng `Future` đã hoàn thành.
5. Phân biệt `ExecutionException`, `CancellationException`, `InterruptedException`, rejection và lỗi phát sinh ngay trong chính hook.
6. Hiểu trường hợp **hook không chạy**: task chưa được worker thực thi, bị reject, bị loại khỏi queue, chạy qua một policy ngoài worker, hoặc `beforeExecute` ném exception.
7. Thiết kế logging/metrics/tracing/ThreadLocal cleanup **nhanh, không gây block vô hạn, không ném exception ngoài ý muốn**.
8. Kiểm thử kết quả từ Java 17 với lịch thực thi có kiểm soát và phân tích memory consistency, lifecycle của pool.

## 3. Trước hết, hiểu hai con đường thực thi

### 3.1. `execute(Runnable)`: chạy task do caller cung cấp

Ví dụ:

```java
pool.execute(() -> {
    throw new IllegalStateException("direct task failed");
});
```

Đây là **đoạn minh họa**, `pool` phải là một `Executor`/`ThreadPoolExecutor` được tạo ở bên ngoài. Một `Runnable` trực tiếp ném unchecked exception khi `run()` chạy. Đối với `ThreadPoolExecutor` thông thường, throwable này thoát khỏi `task.run()`, được `afterExecute(r, t)` nhận qua `t`; sau đó worker có thể kết thúc bất thường và được thay thế khi executor cần duy trì pool.

Không nên khẳng định mọi `execute()` luôn tạo lỗi ở tham số `t`: caller cũng có thể gọi `execute(futureTask)` hoặc `execute(wrapper)` mà wrapper tự bắt exception, khi đó `run()` không ném ra. Điều quyết định là **Runnable thật được worker gọi** có để exception thoát ra khỏi `run()` hay không.

### 3.2. `submit(...)`: đóng gói task và trả Future

Ví dụ:

```java
Future<Integer> result = pool.submit(() -> {
    throw new IllegalArgumentException("submitted task failed");
});
```

Đây là đoạn minh họa, cần import `java.util.concurrent.Future` và một `pool` là `ExecutorService`. `submit(Callable<T>)` thường tạo một `RunnableFuture<T>` như `FutureTask`, gửi wrapper đó vào `execute()` nội bộ rồi trả Future.

Khi `Callable.call()` ném `IllegalArgumentException`:

- `FutureTask` bắt throwable và lưu trạng thái **completed exceptionally**;
- `Future.get()` ném `ExecutionException`, trong đó `getCause()` là `IllegalArgumentException` gốc;
- `FutureTask.run()` không ném lại ngoại lệ đó ra bên ngoài;
- vì thế `afterExecute(r, t)` thường nhận `t == null`;
- `r` có thể là object có interface `Future<?>`, nên hook có thể đọc outcome từ Future sau khi task.run hoàn tất.

Tương tự, `submit(Runnable)` cũng thường bọc task để có Future, mặc dù `Runnable` không trả một giá trị kiểu T.

### 3.3. Bảng so sánh cốt lõi

| Câu hỏi | `execute(() -> throws)` trực tiếp | `submit(() -> throws)` |
|---|---|---|
| Đối tượng worker chạy | Runnable do caller cung cấp (hoặc wrapper do caller gửi) | Thường là `FutureTask`/`RunnableFuture` |
| API trả về | `void` | `Future<?>` / `Future<T>` |
| Khi logic task ném unchecked exception | Có thể thoát khỏi `Runnable.run()` | Thường bị wrapper bắt và lưu |
| `afterExecute(r, t)` | `t` thường chứa throwable nếu nó thoát từ `run()` | `t` thường `null` |
| Caller lấy lỗi qua `Future.get()` | Không có Future từ chính lời gọi `execute` | `ExecutionException.getCause()` |
| Worker bị kết thúc bởi exception task | Có thể có, khi exception thực sự thoát khỏi `run()` | Thường không, vì FutureTask đã bắt exception |
| `isDone()` / `isCancelled()` | Không có nếu task không tự là Future | Có trên Future trả về |

Bảng mô tả **đường chạy chuẩn trên ThreadPoolExecutor và FutureTask**, không là một khẳng định về mọi Executor implementation hay mọi Runnable wrapper tùy biến.

## 4. Các hook chính của ThreadPoolExecutor

`ThreadPoolExecutor` có ba phương thức hook được thiết kế để subclass override:

```java
protected void beforeExecute(Thread t, Runnable r)
protected void afterExecute(Runnable r, Throwable t)
protected void terminated()
```

Đây là **chữ ký API minh họa**, không phải một class Java độc lập.

### `beforeExecute(Thread thread, Runnable task)`

- Được worker thread gọi **ngay trước khi chạy `Runnable.run()`** cho task được lấy để thực thi.
- Dùng để gắn instrumentation, lấy timestamp, đặt context cục bộ theo thread, increment “task started” metric, hoặc kiểm tra điều kiện nhẹ.
- Tham số `thread` cho biết worker thread thực thi; `task` là Runnable thật sự đưa vào worker, **có thể là wrapper FutureTask** chứ không phải Callable ban đầu.
- JavaDoc khuyến khích override gọi `super.beforeExecute(thread, task)`, thường **ở đầu** method.

**Nguy hiểm:** nếu chính `beforeExecute` ném exception, task có thể **không được gọi `run()`**, và `afterExecute` **không được gọi cho task đó** theo cấu trúc worker loop chuẩn. Vì vậy không đặt validation tùy tiện ném exception trong hook nếu muốn theo dõi completion nhất quán.

### `afterExecute(Runnable task, Throwable error)`

- Được worker gọi **sau khi `task.run()` kết thúc**, bình thường hoặc ném ra throwable, **nếu `beforeExecute` đã trở về bình thường**.
- `error` khác null khi có throwable **thoát ra khỏi `task.run()`** tới vòng điều khiển worker.
- `error` thường null nếu `run()` trở về bình thường, kể cả trong trường hợp **logic bên trong một FutureTask đã thất bại** nhưng wrapper bắt được exception.
- Tham số `task` là wrapper Runnable mà worker vừa chạy, có thể đồng thời là `Future<?>`.
- Có thể đọc `Future.get()` nếu Future **đã hoàn thành** để xác định lỗi stored exception/cancellation.
- JavaDoc khuyến khích gọi `super.afterExecute(task, error)`, thường **ở cuối** method.

**Không dùng `afterExecute` để làm tác vụ dài hoặc blocking I/O.** Hook chạy trong **worker thread** đang chịu trách nhiệm thực thi work queue. Nếu hook mất vài giây để gọi network/logging đồng bộ, worker chưa sẵn sàng cho task tiếp theo; throughput giảm, queue tăng, có thể gây backpressure và deadlock.

### `terminated()`

Được gọi khi executor **chuyển sang trạng thái terminated** (sau khi task/workers đã đi đến lifecycle kết thúc theo contract), thường dùng để ghi một metric hoặc dọn state chung thuộc executor. Đây **không phải hook gọi sau mỗi task**.

`terminated()` không thay thế `shutdown()` hoặc `awaitTermination()` của caller; các điểm trên thuộc hai vai trò khác nhau.

## 5. Trình tự hook trong worker loop

Mô hình khái niệm của `ThreadPoolExecutor`:

```text
Worker lấy task từ workQueue hoặc nhận firstTask
             |
             v
    beforeExecute(thread, task)
             |
             v
         task.run()
         /       \
    trả về      throw ra
       \         /
        \       /
             v
   afterExecute(task, throwableOrNull)
             |
             v
     worker tiếp tục / kết thúc
```

Đây là **mô hình giản lược**; implementation còn phải giữ/nhả worker lock, cập nhật counters, xử lý interrupt, shutdown và worker replacement.

**Chi tiết quan trọng:** trong triển khai chuẩn, lời gọi `beforeExecute` ở ngoài phần `try/finally` bao quanh `task.run()` và `afterExecute`. Vì thế nếu `beforeExecute` tự ném exception, `task.run()` chưa chạy và `afterExecute` không được gọi. Ngoài ra, nếu `afterExecute` tự ném exception, worker cũng có thể kết thúc bất thường và làm exception handling/lifecycle phức tạp hơn.

**Không nhầm tham số thứ hai của `afterExecute` với “mọi Throwable từng xảy ra”.** Nó chỉ phản ánh throwable đi ra khỏi `task.run()` theo đường thực thi mà hook nhận được.

## 6. Tại sao afterExecute(Throwable) bỏ sót lỗi của submit()?

`AbstractExecutorService.submit` dùng một cơ chế kiểu `newTaskFor(...)` để biến `Runnable/Callable` thành `RunnableFuture`, thường là `FutureTask`, rồi gọi `execute` với wrapper.

Ý tưởng của `FutureTask.run()`:

```text
try:
    result = callable.call()
    save successful result
catch Throwable ex:
    save failure ex inside Future
return from run()
```

Mô tả này chỉ là **pseudocode**, không phải bytecode hoặc implementation đầy đủ của FutureTask.

Nếu worker gọi `task.run()` và wrapper trả về bình thường, `ThreadPoolExecutor` không thấy một Throwable thoát ra. Hook vì vậy nhận `t == null`. Tuy nhiên, **`Future.get()` biết failure** vì FutureTask đã lưu outcome.

Điều này giải thích hiện tượng hay gặp:

```java
// Minh họa lỗi giám sát chưa đủ.
@Override
protected void afterExecute(Runnable task, Throwable error) {
    super.afterExecute(task, error);
    if (error != null) {
        log(error);
    }
}
```

Đây là **method minh họa**, cần có `log(Throwable)` và một subclass của `ThreadPoolExecutor`. Nó phát hiện Throwable trực tiếp từ `execute(Runnable)`, nhưng có thể bỏ sót hầu hết exception do `submit()` lưu trong Future.

Cách chính xác là **kiểm tra cả hai kênh**, đồng thời xử lý cancellation, interrupt và tình huống task không phải Future.

## 7. Mẫu hook kiểm tra Future an toàn

Mẫu dùng trong một subclass `ThreadPoolExecutor`:

```java
@Override
protected void afterExecute(Runnable task, Throwable thrown) {
    try {
        Throwable failure = thrown;

        if (failure == null && task instanceof Future<?> future
                && future.isDone()) {
            try {
                future.get(); // completed Future: không chờ kết quả nữa
            } catch (CancellationException e) {
                recordCancellation(e);
            } catch (ExecutionException e) {
                failure = e.getCause();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                recordHookInterrupted(e);
            }
        }

        if (failure != null) {
            recordTaskFailure(failure);
        }
    } finally {
        super.afterExecute(task, thrown);
    }
}
```

Đây là **method minh họa**, chưa phải class độc lập: cần imports `Future`, `ExecutionException`, `CancellationException`, các phương thức `record...` và một subclass `ThreadPoolExecutor`. Những thao tác ghi metric/log trong hook phải **không ném exception không được xử lý và không block dài**.

Điều kiện `task instanceof Future<?>` cần thiết vì với `submit()`, task thường là `FutureTask`. Tuy nhiên, người gọi có thể truyền `FutureTask` trực tiếp qua `execute()`; hoặc một executor được subclass override `newTaskFor` trả một loại `RunnableFuture` khác. Vì thế nên dựa vào **interface Future** thay vì cố nhận diện task bằng cách chỉ kiểm tra nó được gửi từ method nào.

### Vì sao kiểm tra `future.isDone()`?

`afterExecute` được gọi sau khi `task.run()` hoàn thành và với một `FutureTask` chuẩn, Future đã done. Tuy nhiên, để không vô tình gọi `get()` blocking với một Runnable custom cũng implement Future nhưng semantics bất thường, mẫu kiểm tra `isDone()` trước. `isDone` true bao gồm thành công, thất bại hoặc canceled.

**Lưu ý:** một `Future` custom có thể vi phạm các kỳ vọng thực tế của code instrumentation, hoặc `isDone` có semantics được định nghĩa khác; không nên dùng một Future wrapper tùy ý không có contract rõ. Với FutureTask của JDK, đường kiểm tra trên là hợp lý.

### Xử lý các outcome riêng biệt

- **Task thành công:** `future.get()` trả kết quả; hook không cần sử dụng kết quả nếu chỉ thu metric.
- **Task thất bại:** `future.get()` ném `ExecutionException`; dùng **`getCause()`** để ghi lỗi gốc, không chỉ log wrapper.
- **Task bị hủy:** `future.get()` ném `CancellationException`; cancellation thường cần metric riêng, **không mặc định xem là business failure**.
- **Hook bị interrupt:** `get()` có thể ném `InterruptedException`. Nếu catch và không propagate, khôi phục interrupt status; ghi metric thích hợp mà không làm hook ném thêm.
- **`thrown != null`:** đã có lỗi thực sự thoát khỏi `Runnable.run()`; không cần gọi Future.get chỉ để tìm lại lỗi nếu quy tắc đã xác định kênh ưu tiên.

**Không được catch `Exception` rồi bỏ qua hết**: làm vậy vừa che lỗi task vừa che lỗi instrumentation. Hãy nêu rõ chính sách lưu lỗi và giữ hook đủ đơn giản.

## 8. Ví dụ Java 17 chạy được với cả execute() và submit()

Chương trình sau tạo subclass `ObservedPool` của `ThreadPoolExecutor` với một worker. Trong `beforeExecute`, pool đếm số lần task bắt đầu. Trong `afterExecute`, pool kiểm tra:

- `Runnable task` đồng thời implement `Future<?>`: đọc outcome từ `Future.get()`;
- task không phải Future có `error != null`: ghi lại loại exception từ `execute()` trực tiếp.

Một `CountDownLatch` đảm bảo main **đợi cả hai lần afterExecute kết thúc** trước khi in kết quả. `UncaughtExceptionHandler` được đặt thành handler rỗng **chỉ để stdout minh họa được xác định** khi task `execute()` cố ý ném exception; không sử dụng cách nuốt lỗi này trong production.

Tạo file **`ExecutorHookDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ExecutorHookDemo {
    static final class ObservedPool extends ThreadPoolExecutor {
        final AtomicInteger beforeCount = new AtomicInteger();
        final AtomicInteger afterCount = new AtomicInteger();
        final AtomicReference<String> executeError = new AtomicReference<>("none");
        final AtomicReference<String> submitError = new AtomicReference<>("none");
        final CountDownLatch completedHooks = new CountDownLatch(2);

        ObservedPool() {
            super(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
            setThreadFactory(task -> {
                Thread thread = new Thread(task, "observed-worker");
                thread.setUncaughtExceptionHandler((t, e) -> { });
                return thread;
            });
        }

        @Override
        protected void beforeExecute(Thread thread, Runnable task) {
            super.beforeExecute(thread, task);
            beforeCount.incrementAndGet();
        }

        @Override
        protected void afterExecute(Runnable task, Throwable error) {
            try {
                if (task instanceof Future<?> future) {
                    if (future.isDone()) {
                        try {
                            future.get();
                        } catch (ExecutionException e) {
                            submitError.set(e.getCause().getClass().getSimpleName());
                        } catch (CancellationException e) {
                            submitError.set("cancelled");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            submitError.set("interrupted");
                        }
                    }
                } else if (error != null) {
                    executeError.set(error.getClass().getSimpleName());
                }
            } finally {
                afterCount.incrementAndGet();
                completedHooks.countDown();
                super.afterExecute(task, error);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ObservedPool pool = new ObservedPool();
        try {
            Future<Integer> failed = pool.submit(() -> {
                throw new IllegalArgumentException("from submit");
            });
            try {
                failed.get();
            } catch (ExecutionException e) {
                System.out.println("callerSaw=" + e.getCause().getClass().getSimpleName());
            }

            pool.execute(() -> {
                throw new IllegalStateException("from execute");
            });

            if (!pool.completedHooks.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("hooks timed out");
            }
            System.out.println("beforeCount=" + pool.beforeCount.get());
            System.out.println("afterCount=" + pool.afterCount.get());
            System.out.println("submitHookError=" + pool.submitError.get());
            System.out.println("executeHookError=" + pool.executeError.get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
```

Biên dịch và chạy với JDK 17:

```bash
javac --release 17 ExecutorHookDemo.java
java ExecutorHookDemo
```

**Stdout đã kiểm chứng:**

```text
callerSaw=IllegalArgumentException
beforeCount=2
afterCount=2
submitHookError=IllegalArgumentException
executeHookError=IllegalStateException
```

### Giải thích vì sao kết quả không phụ thuộc scheduler

1. `pool.submit(...)` trả Future của một task **cố ý** ném `IllegalArgumentException`.
2. Main gọi `failed.get()`, nhận `ExecutionException` rồi đọc cause. Vì thế dòng `callerSaw=IllegalArgumentException` luôn có khi task thực sự đã chạy tới lời ném và đường thực thi bình thường của demo hoàn tất.
3. `FutureTask.run()` lưu failure; ở hook `afterExecute`, `task instanceof Future<?>` đúng, `future.isDone()` đúng, `future.get()` ném `ExecutionException` và hook ghi `submitHookError`.
4. `pool.execute(...)` nhận Runnable trực tiếp, tự ném `IllegalStateException` ra khỏi `run()`; hook nhận error khác null và ghi `executeHookError`.
5. Cả hai task đều đi qua `beforeExecute` và `afterExecute` trong tình huống đã xác minh. `AtomicInteger` bảo đảm đếm thread-safe, cho `beforeCount=2` và `afterCount=2`.
6. `completedHooks` đếm xuống sau mỗi lần after hook xử lý outcome. Main đợi bằng `await(5, SECONDS)` nên đọc trạng thái hook **sau** hai lần hoàn tất, không phải phỏng đoán bằng `Thread.sleep`.
7. `CountDownLatch` có memory-consistency effects phù hợp, nên main quan sát đúng dữ liệu các hook đã ghi trước `countDown`.
8. `setUncaughtExceptionHandler((t,e)->{})` cố tình không in lỗi `execute` trong bài thực hành để stdout có thể so sánh chính xác. **Production phải có xử lý uncaught exception thích hợp**: log, metric, alert hoặc escalation. Không sao chép handler rỗng cho công việc quan trọng.

**Lưu ý:** chương trình chỉ có một task submit và một task execute; các trường `submitError`/`executeError` chứa một string mới nhất, không phải hệ thống thu thập lỗi đầy đủ cho nhiều task. Với sản phẩm thật, dùng logging/tracing tập trung hoặc metrics counters, không chỉ ghi vào một AtomicReference. Demo đợi hook bằng latch có timeout để tránh kẹt vô hạn trong kiểm thử.

## 9. Phân biệt “hook chạy xong” và “task hoàn thành”

Với `submit()`, `FutureTask` có thể đánh dấu Future đã done **trước khi `afterExecute` hoàn tất**. Vì vậy:

- `future.get()` xác nhận **logic task đã có outcome**, với memory guarantees của Future.
- Nó **không nhất thiết xác nhận toàn bộ logging/cleanup bên trong `afterExecute` đã hoàn tất**.
- Nếu caller cần quan sát chính xác hook đã hoàn tất (ví dụ test một custom pool), cần một tín hiệu riêng như `CountDownLatch` của demo, hoặc giao thức completion riêng được thiết kế rõ.

Đây là lý do demo không chỉ gọi `failed.get()` rồi lập tức kiểm tra `submitError`. Main còn chờ `completedHooks`, nên kết quả kiểm tra hook có ranh giới đồng bộ rõ ràng.

**Tương tự:** `shutdown()` không tự chờ hook/task kết thúc. `awaitTermination()` chỉ trả thành công khi executor thực sự terminated, nhưng nếu đang cần một kết quả task cụ thể, `Future.get()` thường là API trực tiếp hơn.

## 10. Khi nào hook hoàn toàn không được gọi?

Không được coi hook là sự kiện “mọi task từng submit đã được nhận và kết thúc”. Hook thuộc **worker execution path** của `ThreadPoolExecutor`, không thuộc một hệ thống audit mọi lần submit ở mọi trạng thái.

| Tình huống | `beforeExecute` / `afterExecute` có chạy không? |
|---|---|
| Task được worker lấy và `run()` trở về bình thường | Thường cả hai chạy |
| Task được worker lấy, `run()` ném unchecked throwable | `beforeExecute` và `afterExecute` nhận throwable, nếu before hook trở về bình thường |
| Task chạy qua `FutureTask` và lưu failure | Hook vẫn chạy nếu worker đã thực sự gọi wrapper; `t` thường null |
| Task bị reject tại `execute/submit` | **Không**, vì worker không chạy task |
| Task còn trong queue và bị `shutdownNow()` trả ra, chưa được chạy | **Không**, vì chưa đi vào worker |
| Task bị loại khỏi queue bởi `remove/purge` trước khi worker lấy | **Không** |
| `beforeExecute` tự ném exception | `task.run()` chưa được gọi; `afterExecute` **không được gọi** cho task đó |
| `CallerRunsPolicy` chạy task trong caller khi pool bão hòa | Không qua worker loop ⇒ không nên mong pool hook chạy |
| `DiscardPolicy` âm thầm bỏ task | Không qua worker loop ⇒ không có hook |
| Caller tự chạy `Runnable.run()` bên ngoài pool | Không qua ThreadPoolExecutor worker ⇒ không có hook |

**Task đã bị hủy (`cancel`) cũng cần phân biệt:** một `FutureTask` bị cancel **nhưng vẫn nằm trong queue và sau đó được worker gọi `run()`** có thể đi qua hooks; `run` thấy trạng thái canceled rồi return nhanh. Nhưng nếu nó đã được loại khỏi queue hoặc không bao giờ được worker xử lý, hooks sẽ không có cơ hội chạy. Vì thế số lần `afterExecute` **không tự bằng số task từng được submit**.

### Rejection trong `submit()` có đặc điểm riêng

Một implementation `submit()` thường tạo FutureTask rồi gọi `execute`. Nếu executor dùng `AbortPolicy` và reject, `RejectedExecutionException` được ném ngay cho caller. Nếu dùng handler bỏ task im lặng, caller có thể đã nhận một Future **không bao giờ done**, gây nguy cơ `get()` chờ mãi. Không chọn discard policy cho công việc cần acknowledgment chắc chắn.

Để theo dõi số task **được nhận, bị reject, hoàn thành, thất bại và bị hủy**, cần instrument cả **admission path** và **outcome/completion path**, không chỉ after hook.

## 11. Exception bên trong chính các hook nguy hiểm thế nào?

### `beforeExecute` ném exception

```java
@Override
protected void beforeExecute(Thread t, Runnable r) {
    super.beforeExecute(t, r);
    throw new IllegalStateException("hook failed before task");
}
```

Đây là **đoạn minh họa lỗi**, không nên đưa vào production. Khi before hook ném, task có thể không được chạy và `afterExecute` cũng không được gọi. Một Future của task bị bỏ qua có thể không bao giờ chuyển sang done nếu không có cơ chế fail/cleanup bổ sung. Worker có thể kết thúc bất thường.

Do đó hook chỉ nên làm việc **nhanh, bounded và exception-safe**. Không nên đưa logic khởi tạo bất định, truy vấn network hoặc parse dữ liệu bên ngoài có thể ném exception không được bắt vào `beforeExecute`.

### `afterExecute` ném exception

Nếu after hook ném, worker có thể kết thúc bất thường dù logic task đã chạy xong. Việc này làm nhiễu métrics, log hoặc vòng đời worker; pool có thể tạo replacement worker khi cần. **Không cho logging/metrics throw ra ngoài hook**. Nếu instrumentation thất bại, nên xử lý/ghi nhận theo một kênh an toàn, giới hạn thay vì làm hỏng executor.

### Không chặn executor bằng synchronous logging

Một hook gọi HTTP endpoint hoặc ghi file đồng bộ chậm trong mỗi task khiến **worker bị chiếm dụng**. Điều này có thể tăng queue wait time, gây rejection, thậm chí tạo vòng đợi nếu destination logging cũng dùng cùng pool.

Tốt hơn:

- Ghi metric đơn giản bằng counters hoặc công cụ telemetry nonblocking/bounded.
- Dùng logging có cấu hình queue/batching đúng; kiểm soát chính sách khi log backend đầy.
- Không đưa token, password, payload nhạy cảm hoặc exception message không lọc vào log.
- Phân biệt errors thật với cancellations có chủ đích để không tạo alert storm.
- Ghi event id/task id để biết lỗi thuộc công việc nào, thay vì chỉ class name như demo.

## 12. Thiết kế metrics trước–sau task

Hook thường hữu ích cho:

1. Đếm số lần **worker bắt đầu task** (`beforeExecute`).
2. Đo **thời gian thực thi trên worker** đến khi `afterExecute` được gọi.
3. Đếm outcome thành công, thất bại và hủy.
4. Thu thập thread name, queue depth, active worker count.
5. Dọn `ThreadLocal`/context được thiết lập cho task.
6. Thông báo theo dõi những trường hợp worker bất thường.

### Đo elapsed time bằng monotonic clock

Một pattern minh họa:

```java
// Thành viên subclass ThreadPoolExecutor:
private final ThreadLocal<Long> startedNanos = new ThreadLocal<>();

@Override
protected void beforeExecute(Thread thread, Runnable task) {
    super.beforeExecute(thread, task);
    startedNanos.set(System.nanoTime());
}

@Override
protected void afterExecute(Runnable task, Throwable error) {
    try {
        Long start = startedNanos.get();
        if (start != null) {
            long durationNanos = System.nanoTime() - start;
            observeDuration(durationNanos);
        }
        // Có thể phân tích Throwable/Future outcome tại đây.
    } finally {
        startedNanos.remove();
        super.afterExecute(task, error);
    }
}
```

Đây là **code minh họa**, cần `observeDuration(long)` xử lý nhanh, không ném và một subclass đã cấu hình. `System.nanoTime()` thích hợp đo **khoảng thời gian**, không phải timestamp tường thực. Nếu `beforeExecute` xảy ra lỗi trước khi hoàn tất, `afterExecute` có thể không chạy nên đường cleanup riêng cần xem xét.

**Ý nghĩa của metric:** khoảng from-before to after là thời gian task chạy trên worker (và overhead nhỏ của hook), **không tính thời gian task chờ trong executor queue trước khi worker lấy**. Muốn đo queue wait, phải ghi thời gian enqueue/submit và liên kết nó đến Runnable được worker thực thi, thường qua decorator/wrapper hoặc instrumentation của admission path.

### ThreadLocal cleanup

`ThreadPoolExecutor` tái sử dụng platform worker threads. Nếu một task để lại `ThreadLocal`, task sau chạy trên cùng worker có thể nhìn thấy context cũ. Dọn `ThreadLocal.remove()` trong `afterExecute` là một công cụ hữu ích, nhưng phải hiểu:

- Chỉ dọn những key/context mà component **thực sự sở hữu**.
- Hook không được gọi nếu task chạy bên ngoài worker qua một handler như `CallerRunsPolicy`.
- Hook không được gọi nếu `beforeExecute` ném exception.
- Nếu cần cleanup **bất kể đường chạy**, một wrapper per-task dùng `try/finally` quanh logic của chính task thường đáng tin hơn hook pool-level.
- Nếu dùng context tracing trong async tasks, phải có quy tắc truyền context qua task boundary, không kỳ vọng ThreadLocal tự theo request.

## 13. Cancellation, interruption và kết quả Future

Một `Future` có thể có nhiều trạng thái outcome:

```text
      NEW / running
         |        \
   completed       cancel(true/false)
      /   \              |
  value   failure       cancelled
           |               |
    get throws            get throws
 ExecutionException    CancellationException
```

Đây là sơ đồ khái niệm; trạng thái nội bộ cụ thể của FutureTask/JDK chi tiết hơn.

### `CancellationException`

Nếu `Future.cancel(...)` thành công và caller gọi `get()`, Future ném `CancellationException`. Trong hook, điều này không nên bị gộp bừa thành task failure do business logic; có thể là hủy hợp lệ khi đóng service.

### `InterruptedException`

Với `get()`, thread đang chờ có thể bị interrupt và nhận `InterruptedException`. Nếu hook catch, khôi phục flag bằng `Thread.currentThread().interrupt()` nếu không propagate, và tránh đổi status theo cách khiến worker vẫn phải chạy task tiếp mà không biết mình bị interrupt.

**Chú ý:** `future.get()` sau `future.isDone()` trên `FutureTask` chuẩn không chờ task hoàn thành nữa, nhưng vẫn có signature `throws InterruptedException` và những interface implementations khác nhau. Cần xử lý exception theo API chứ không bỏ catch vì nghĩ “đã done thì không bao giờ exception”.

### `cancel(true)` không phải kill chắc chắn

`cancel(true)` thường yêu cầu interrupt thread đang chạy task. Task đang thực hiện I/O không interruptible hoặc cố tình bỏ qua interrupt có thể tiếp tục. Future có thể ở trạng thái canceled trong khi side effect đang tiếp diễn; đánh dấu cancellation không bảo đảm transaction đã rollback hay thread đã ngừng ngay.

### `isDone()` không đồng nghĩa thành công

`isDone()` true với success, failure và cancel. Để phát hiện failure của task submit, không thể chỉ viết:

```java
if (future.isDone()) {
    metrics.success(); // Sai nếu completed exceptionally hoặc cancelled.
}
```

Đây là đoạn **minh họa sai**. Cần phân loại outcome bằng `get()` sau done hoặc một kênh result/callback phù hợp. Đặc biệt, khi `Future.get()` ném `ExecutionException`, **nguyên nhân thật nằm ở `getCause()`**.

## 14. afterExecute có thể làm thay đổi quan sát của task như thế nào?

Một số tác động của instrumentation lên hệ thống:

- **Latency:** xử lý hook lâu làm worker chậm nhận task kế tiếp.
- **Ordering:** logging đồng bộ trong hook có thể làm thread khác hoàn thành trước, thay đổi thứ tự quan sát.
- **Contention:** metrics dùng cùng monitor/lock cho mọi workers có thể trở thành bottleneck.
- **Memory:** giữ reference đến toàn bộ `Runnable`, payload hoặc stack trace trong collection không giới hạn có thể gây memory leak.
- **Privacy/security:** log exception message có thể chứa query strings, token hoặc thông tin người dùng; phải lọc theo chính sách bảo mật.
- **Lifecycle:** hooks ném exception làm worker kết thúc và pool phải xử lý replacement.
- **Backpressure:** nếu log pipeline quá tải, hook cố đẩy log vào queue bounded bằng `put()` có thể block worker, lan nghẽn ngược vào executor.

Do đó, hook nên **observability-oriented**, ít side effect, không làm thay đổi business outcome. Nếu cần retry/transaction của task thì xây trong layer quản lý công việc, không nhét logic retry tùy tiện vào `afterExecute`.

## 15. Ưu tiên wrapper per-task trong trường hợp nào?

Override `ThreadPoolExecutor` là giải pháp hệ thống cho **mọi task đi qua worker** của một pool. Nhưng không phải framework hay ứng dụng nào cũng kiểm soát được class pool hoặc bảo đảm mọi execution path đi qua hooks.

Trong nhiều trường hợp, một **decorator/wrapper Runnable** rõ ràng hơn:

```java
static Runnable observed(Runnable task) {
    return () -> {
        long started = System.nanoTime();
        try {
            task.run();
        } catch (RuntimeException | Error e) {
            recordFailure(e);
            throw e;
        } finally {
            recordDuration(System.nanoTime() - started);
            cleanupContext();
        }
    };
}
```

Đây là **static method minh họa**; `recordFailure`, `recordDuration` và `cleanupContext` được khai báo bên ngoài. Các method instrumentation cũng phải exception-safe nếu không muốn che mất lỗi gốc.

Ưu điểm:

- Vòng `try/finally` gắn trực tiếp với logic task thay vì phụ thuộc hook.
- Có thể áp dụng cho executor bất kỳ nếu mọi task đều được wrap.
- Nếu task chạy bằng `CallerRunsPolicy` trong caller, wrapper vẫn tự đo/cleanup.
- Có thể định nghĩa rõ context propagation, correlation id và failure policy.

Giới hạn: nếu task bị **reject trước khi chạy**, wrapper cũng không chạy; cần xử lý rejection ngay lúc submit. Với `Callable`, cần decorator tương tự trả giá trị và rethrow exception.

### Exception trong wrapper khi dùng submit

Nếu wrapper ném exception, `FutureTask` do submit tạo vẫn sẽ **bắt và lưu** exception; caller phải quan sát Future nếu cần biết task thất bại. Wrapper log giúp nhìn thấy lỗi sớm, nhưng không thay thế completion contract.

## 16. Phân biệt các lớp lỗi của executor

Không phải mọi exception quanh một executor đều là lỗi bên trong `task.run()`:

| Nguồn lỗi | Vị trí phát sinh | Hook có thấy không? |
|---|---|---|
| Rejection do pool bão hòa/shutdown | Lời gọi `execute/submit` | Thường **không**, task không được worker chạy |
| Lỗi ngay trong `beforeExecute` | Worker hook trước task | `afterExecute` thường không gọi cho task đó |
| Throwable thoát khỏi Runnable trực tiếp | `task.run()` trên worker | `afterExecute` có thể nhận qua `t` |
| Throwable của Callable qua `submit` | Được `FutureTask` lưu | `t` thường null; xem `Future.get()` |
| Hủy task trước khi worker nhận | `Future.cancel`, có thể loại khỏi queue | Có thể không có hook |
| Lỗi logging/metrics trong `afterExecute` | Hook code | Là lỗi instrumentation, có thể làm worker kết thúc |
| Uncaught exception của worker | Thread-level error handling | Có thể đến `UncaughtExceptionHandler` khi không bị FutureTask bắt |
| Timeout của `Future.get(timeout)` | Thread gọi `get` | **Không tự là lỗi task**, task có thể vẫn chạy |
| External side effect thất bại sau task outcome | Hệ thống ngoài JVM | Phải có protocol xác nhận/transaction riêng |

Để có error reporting đầy đủ, cần tối thiểu:

- Catch/reject metrics ở **admission/submission**.
- Task outcome qua `Future`, wrapper hoặc `afterExecute` thích hợp.
- Uncaught exception handler/centralized error reporting ở worker level.
- Cancellation/timeout metrics tách biệt.
- Application-level acknowledgment cho side effect/commit nếu yêu cầu.

Một đơn vị “completed tasks” từ pool không mặc định đồng nghĩa business success. `ThreadPoolExecutor.getCompletedTaskCount()` phản ánh task đã hoàn thành ở mức executor, kể cả khi logic task được FutureTask ghi nhận là thất bại.

## 17. ThreadFactory và UncaughtExceptionHandler

`ThreadFactory` có thể tạo worker với tên và handler nhất quán:

```java
ThreadFactory factory = runnable -> {
    Thread worker = new Thread(runnable, "business-worker");
    worker.setUncaughtExceptionHandler((thread, error) -> {
        reportUncaught(thread.getName(), error);
    });
    return worker;
};
```

Đây là đoạn code trong method cần import `java.util.concurrent.ThreadFactory` và method `reportUncaught`. Hàm báo lỗi không nên ném exception hoặc block vô hạn.

**Cần phân biệt:**

- Lỗi unchecked thoát khỏi `Runnable.run()` khi dùng `execute` có thể đi tới thread uncaught handler.
- Lỗi của `Callable.call()` được `FutureTask` bắt khi dùng `submit` **không thoát khỏi worker.run()`**, nên uncaught handler thường **không nhận lỗi đó**.
- `afterExecute` cho phép theo dõi task ở mức pool worker, nhưng cần kiểm tra Future như đã trình bày.
- Một worker chết do unchecked exception có thể được pool tạo worker khác để tiếp tục xử lý task; việc thay thế không làm side effect trước lỗi được rollback.

Trong demo chính, handler rỗng chỉ phục vụ **kết quả stdout kiểm chứng**, không phải khuyến nghị vận hành. Trong production, cần log/alert/metrics phù hợp cho uncaught exceptions.

## 18. Liveness và tránh deadlock khi thu lỗi

Không nên trong `afterExecute` thực hiện các thao tác như:

```java
// Mô tả nguy cơ: hook chạy trên worker của chính pool.
Future<?> next = pool.submit(() -> sendReport());
next.get(); // Có thể đợi task cần chính worker vừa bị chiếm bởi hook.
```

Đây là **đoạn code minh họa nguy hiểm**, pool, `sendReport()` và checked exceptions cần ngữ cảnh ngoài. Với single-thread pool, hook đang giữ worker duy nhất và chờ task mới xếp hàng; task mới không thể bắt đầu, tạo **thread starvation deadlock**.

Tương tự, trong `afterExecute` gọi `BlockingQueue.put` lên queue đã đầy mà consumer phải chạy trên chính pool này có thể treo. Hãy dùng kênh telemetry độc lập, không blocking hoặc có deadline/failure policy, không tạo vòng phụ thuộc.

Nếu hook gọi `Future.get()` trên **chính task vừa chạy và FutureTask đã done**, thao tác đó không chờ một task khác, nên không gây kiểu deadlock này. Đây là khác biệt giữa “đọc outcome của Future đã hoàn tất” và “submit một task mới rồi chờ nó”.

## 19. Java Memory Model và mối quan hệ completion

Executor Framework có memory consistency guarantees theo tài liệu `java.util.concurrent`:

- Hành động trong thread trước khi gửi `Runnable` cho `Executor.execute` xảy ra trước những hành động của task đó theo *happens-before*.
- Với `ExecutorService`, hành động trước khi submit task *happen-before* hành động trong task; hành động trong task *happen-before* hành động ở caller **sau khi lấy kết quả qua `Future.get()`** theo contract.
- `CountDownLatch` của demo tạo ranh giới HB cho việc main quan sát trạng thái hook trước khi in.
- Các atomic variables dùng để lưu counters/outcomes có memory semantics riêng theo API.

Nhưng cần phân biệt:

1. `Future.get()` làm caller thấy kết quả task đã hoàn tất, **không tự chờ mọi hook instrumentation hoàn tất**.
2. `afterExecute` nhận `Throwable` không có nghĩa nó được phép đọc những mutable objects của caller mà không có giao thức ownership/synchronization thích hợp.
3. Nếu nhiều tasks cùng sửa `HashMap` hoặc biến `int` thường mà không có đồng bộ, việc main đợi Futures **không sửa được data race/lost update giữa các tasks**.
4. Kết quả task được công bố đúng trong JVM không đồng nghĩa transaction database đã commit hoặc việc gửi email đã thực hiện exactly once.

Thiết kế an toàn vẫn cần state confinement, immutable objects, `synchronized`/`Lock` hoặc concurrent collections/atomic operations tương ứng với invariant.

## 20. Kiểm thử hook, submit failure và cancellation

Một chiến lược kiểm thử chặt chẽ:

1. **Success test:** task chạy bình thường, `beforeExecute`/`afterExecute` được gọi, Future.get trả kết quả, không ghi failure.
2. **Direct execute failure:** Runnable ném `IllegalStateException`, hook thấy `t` và uncaught handler có thể nhận throwable.
3. **Submit Callable failure:** Callable ném `IllegalArgumentException`, hook `t == null` nhưng Future.get ném `ExecutionException` có cause đúng.
4. **Submit Runnable failure:** Runnable do submit bọc trong FutureTask, outcome cần phân tích như Callable failure.
5. **Cancellation:** hủy Future trước/khi chạy, phân biệt cancellation với task failure; không giả định hook luôn được gọi nếu task bị remove trước execution.
6. **Rejection:** cấu hình pool có queue bounded, giữ worker bận bằng latch và thử submit quá capacity; kiểm tra handler/rejection metric.
7. **Hook itself fails:** trong test đặc biệt xác minh before hook exception có thể làm mất after hook, rồi sửa instrument để exception-safe. Không đưa hook cố ý ném vào workload production.
8. **Pool shutdown:** nộp task khi pool đã shutdown; rejection được xử lý ở caller, không dựa vào after hook.
9. **ThreadLocal cleanup:** chạy hai task nối tiếp trên cùng worker, xác minh context task đầu không rò sang task sau.
10. **Instrumented latency:** load test queue depth, task duration, admission latency và logging overhead; hook không làm throughput/latency vượt SLA.

### Kiểm thử race một cách xác định

Dùng `CountDownLatch` hoặc `CyclicBarrier` để tạo checkpoint, thay vì `Thread.sleep()` và mong scheduler chạy theo thứ tự mong muốn. Trong demo, `Future.get()` xác nhận logic task, còn `completedHooks.await(...)` xác nhận instrumentation đã xử lý cả hai task. Hai mốc được phân biệt rõ để test không bị flaky.

### Kiểm tra kết quả lỗi đúng tầng

Đối với `ExecutionException`:

```java
try {
    future.get();
} catch (ExecutionException e) {
    Throwable root = e.getCause();
    // report(root), trả lỗi hoặc quyết định retry theo contract
}
```

Đây là đoạn minh họa trong method có `future` là Future; còn cần xử lý/khai báo `InterruptedException`. Không nên catch `ExecutionException` rồi chỉ ghi “future failed” mà mất root cause. Cũng không retry tự động khi task có side effect không idempotent.

## 21. Các hiểu lầm phổ biến

1. **“`afterExecute` luôn nhận Throwable nếu task do submit ném exception.”** Sai; `FutureTask` thường lưu lỗi, để `t==null`.
2. **“`t==null` nghĩa là business task chắc chắn thành công.”** Sai; Future có thể completed exceptionally hoặc cancelled.
3. **“`beforeExecute` và `afterExecute` chạy cho mọi task đã nộp.”** Sai; reject, discard, task chưa chạy, caller-runs và hook failure có thể bỏ qua đường worker.
4. **“`Future.isDone()` nghĩa là success.”** Sai; isDone bao gồm fail/cancel.
5. **“`Future.get()` ném đúng exception của task trực tiếp.”** Thường ném `ExecutionException` bọc cause; cancellation dùng `CancellationException`.
6. **“Chỉ cần UncaughtExceptionHandler là bắt được mọi submit error.”** Sai; FutureTask thường bắt exception trước khi thoát thread.
7. **“`execute` luôn đưa lỗi vào afterExecute.t.”** Không nhất thiết; Runnable tự bắt exception, FutureTask được gửi qua execute hoặc wrapper khác có thể return bình thường.
8. **“`afterExecute` gọi Future.get là luôn an toàn.”** Phải kiểm tra loại và completed state; tuyệt đối tránh get của một task khác chưa done trong worker hook.
9. **“Đặt logging network trong afterExecute không ảnh hưởng hiệu năng.”** Sai; hook chạy trong worker và có thể block throughput.
10. **“Có hook thì có thể bỏ Future.get ở caller.”** Không nếu caller cần result/completion/cancellation theo business contract.
11. **“Future.get đợi cả afterExecute.”** Không được mặc định; FutureTask có thể done trước khi hook xong.
12. **“Throwable ở afterExecute là mọi lỗi của executor.”** Sai; rejection, before-hook failure và external side effects thuộc đường khác.
13. **“Cancel Future tức task dừng ngay.”** Không; cancellation/interrupt phải được task hỗ trợ.
14. **“ThreadPoolExecutor.getCompletedTaskCount là success count.”** Sai; task completed exceptionally vẫn có thể được tính hoàn tất ở mức pool.
15. **“Hook được gọi trên thread submit.”** Thường hook chạy trên worker; riêng caller-runs có thể chạy task trong caller nhưng không qua pool hook.
16. **“Override hooks có thể thỏa sức throw để phát hiện lỗi.”** Sai; exception từ hook làm hỏng worker lifecycle.
17. **“Log mọi exception message là an toàn.”** Không; cần tránh lộ secrets/PII và giới hạn kích thước payload.
18. **“Cùng ThreadLocal key thì mỗi task có context riêng.”** Sai; pooled worker tái sử dụng, phải cleanup và propagate context có kiểm soát.
19. **“Hook ghi nhận lỗi rồi có nghĩa side effect được rollback.”** Không; task failure và external transaction là hai hợp đồng độc lập.
20. **“Không thấy log nghĩa là không có lỗi.”** Không; kiểm tra Future, rejection, task wrapper và error channel.

## 22. Khi nào nên override ThreadPoolExecutor?

**Phù hợp khi:**

- Bạn sở hữu class `ThreadPoolExecutor` và muốn áp dụng instrumentation chung cho **mọi task do worker của pool chạy**.
- Muốn đo thời gian chạy trên worker, queue/active metrics, tagging hoặc dọn ThreadLocal được tạo bởi pool.
- Có thể đảm bảo hook nhanh, exception-safe, không block và không thay đổi business semantics.
- Có chính sách outcome rõ ràng cho `Future`, cancellations và Throwable trực tiếp.

**Không nên coi hook là điểm duy nhất khi:**

- Nguồn task có thể chạy qua nhiều loại `Executor` khác nhau.
- Rejection, caller-runs, tasks run directly hoặc delayed tasks phải được theo dõi một cách đồng nhất.
- Cần bao phủ toàn bộ lifecycle từ submission đến outcome, không chỉ “task đã qua worker”.
- Task có side effect external cần giao dịch, retry, idempotency hay audit bền vững.
- Cần truyền correlation context qua các asynchronous boundaries; wrapper per-task có thể rõ ràng hơn.

Một giải pháp observability hoàn chỉnh thường kết hợp **submission wrapper**, **rejection handler**, **Future/callback handling**, metrics mức pool, error handler và domain-specific result tracking. Hook là một điểm mở rộng hữu ích, không phải toàn bộ kiến trúc quản lý lỗi.

## 23. Câu hỏi tự kiểm tra và đáp án

**Câu 1.** `afterExecute(r, t)` nhận `t == null` sau task `submit` ném `IllegalArgumentException`. Có gì bất thường không?  
**Đáp án:** Không. `submit` thường bọc task bằng `FutureTask`, lưu Throwable bên trong Future; `run()` của wrapper trở về bình thường nên `t` null.

**Câu 2.** Muốn tìm ngoại lệ gốc của `Future` bị failed, xử lý gì?  
**Đáp án:** `future.get()` ném `ExecutionException`; gọi `e.getCause()` để lấy throwable gốc.

**Câu 3.** Trong `afterExecute`, khi nào được cân nhắc gọi `Future.get()`?  
**Đáp án:** Khi `r instanceof Future<?>` và Future đã done, để không block worker vô hạn; bắt `ExecutionException`, `CancellationException` và `InterruptedException` đúng.

**Câu 4.** `afterExecute` có luôn chạy nếu `beforeExecute` tự ném RuntimeException không?  
**Đáp án:** Không; theo worker loop của ThreadPoolExecutor, before hook ném trước task.run khiến after hook không được gọi cho task đó.

**Câu 5.** Task bị reject bởi `AbortPolicy` có đi qua `beforeExecute` không?  
**Đáp án:** Không. Nó không được worker thực thi; caller thường nhận `RejectedExecutionException` tại submit/execute.

**Câu 6.** Một task dùng `execute(new FutureTask<>(...))` ném ngoại lệ bên trong. `afterExecute.t` có chắc khác null không?  
**Đáp án:** Không. FutureTask.run bắt và lưu lỗi dù được gửi bằng execute. Điều quan trọng là Runnable wrapper có để lỗi thoát khỏi run hay không.

**Câu 7.** Tại sao test phải chờ latch hook riêng, dù đã `future.get()`?  
**Đáp án:** FutureTask có thể done trước khi after hook hoàn tất; latch tạo ranh giới đã chạy xong instrumentation.

**Câu 8.** `CallerRunsPolicy` khi pool bão hòa và chưa shutdown có chạy `beforeExecute` của ThreadPoolExecutor không?  
**Đáp án:** Không nên trông chờ điều đó. Task chạy trong thread caller qua handler, không phải worker loop của pool.

**Câu 9.** `shutdownNow` trả ra task chưa thực thi; có `afterExecute` cho task đó không?  
**Đáp án:** Không, vì worker chưa gọi `task.run()`. Completion/audit phải xử lý những task này riêng.

**Câu 10.** Vì sao gọi `pool.submit(...).get()` ngay bên trong after hook của **cùng single-thread pool** có thể deadlock?  
**Đáp án:** Hook giữ worker duy nhất và chờ một task mới đang xếp hàng, task đó không có worker để chạy.

**Câu 11.** `Future.isDone() == true` có đủ để tăng success counter không?  
**Đáp án:** Không. Future có thể failure hoặc cancellation. Phải đọc outcome đúng hoặc theo result contract.

**Câu 12.** Khi một exception được log trong after hook, có nghĩa task chắc chắn được retry an toàn không?  
**Đáp án:** Không. Log chỉ quan sát lỗi; retry có thể lặp side effect nếu không có idempotency/transaction.

**Câu 13.** Tại sao hooks nên tránh network I/O?  
**Đáp án:** Chúng chạy trên worker, block lâu khiến worker không nhận task kế tiếp, làm giảm throughput và tăng queue pressure.

**Câu 14.** ThreadLocal context được set trong `beforeExecute` có chắc được remove bằng `afterExecute` trong mọi tình huống không?  
**Đáp án:** Không; nếu before hook ném hoặc task được chạy ngoài worker loop, after hook không làm cleanup. Có thể cần wrapper `try/finally` riêng.

**Câu 15.** Nếu chỉ có `ExecutorService` interface và không kiểm soát implementation, có thể override `afterExecute` trực tiếp không?  
**Đáp án:** Không. Đó là hook của `ThreadPoolExecutor`, không phải method trong `ExecutorService` interface. Dùng wrapper/decorator hoặc kiểm soát custom pool.

## 24. Tài liệu tham khảo chính thức

- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): phần **Hook methods**, `beforeExecute`, `afterExecute`, `terminated`; JavaDoc đặc biệt lưu ý `submit` và `FutureTask`.
- [Java SE 17 — FutureTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html): `run()`, lưu result/exception, `done()`, cancellation và trạng thái.
- [Java SE 17 — AbstractExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/AbstractExecutorService.html): `submit` và `newTaskFor`.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): `submit`, lifecycle, completion và memory consistency.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get`, `isDone`, `cancel`, `ExecutionException`, `CancellationException`.
- [Java SE 17 — Executor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html): `execute` và memory consistency.
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): phase synchronization và memory consistency.
- [Java SE 17 — Thread.UncaughtExceptionHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.UncaughtExceptionHandler.html): uncaught errors của thread.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): happens-before, thread lifecycle và memory model.

**Tóm tắt cần nhớ:** `beforeExecute` và `afterExecute` là hooks **của worker loop `ThreadPoolExecutor`**. `afterExecute` thấy throwable **thoát khỏi `Runnable.run()`**, chứ không tự giải mã mọi lỗi đã được `FutureTask` lưu. Khi dùng `submit()`, hãy kiểm tra `Future<?>` đã done và đọc outcome qua `get()`, phân loại success/failure/cancel; đồng thời xử lý rejection, lifecycle, uncaught exceptions và instrumentation không được làm nghẽn worker.

</details>

- [Quay lại đầu trang](#back-to-top)
