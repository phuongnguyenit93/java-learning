<a id="back-to-top"></a>

# Xử lý ngoại lệ

## Menu
- [Ngoại lệ xảy ra khi gửi tác vụ khác với ngoại lệ trong lúc chạy](#exception-handling-001)
- [ExecutionException, CompletionException và nguyên nhân gốc](#exception-handling-002)
- [AsyncUncaughtExceptionHandler cho phương thức @Async trả về void](#exception-handling-003)
- [Cấu hình bộ xử lý ngoại lệ bằng AsyncConfigurer](#exception-handling-004)
- [exceptionally(), handle() và whenComplete()](#exception-handling-005)
- [Phân biệt phục hồi bằng giá trị thay thế với chỉ quan sát lỗi](#exception-handling-006)
- [Không bỏ qua Future rồi đánh mất lỗi nghiệp vụ](#exception-handling-007)
- [Giới hạn bắt lỗi bằng TaskDecorator khi tác vụ được bọc trong Future](#exception-handling-008)
- [Xác định chính sách thử lại có giới hạn và tính an toàn khi chạy lại](#exception-handling-009)

## <a id="exception-handling-001">Ngoại lệ xảy ra khi gửi tác vụ khác với ngoại lệ trong lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ xảy ra khi gửi tác vụ khác với ngoại lệ trong lúc chạy

Trong Spring Framework, một lời gọi `@Async` hoặc `TaskExecutor.execute(...)` có thể thất bại ở **hai thời điểm có ý nghĩa rất khác nhau**:

1. **Khi gửi tác vụ (submission/admission):** executor không thể hoặc không muốn nhận công việc, chẳng hạn pool đã shutdown hoặc cả worker và queue đều bão hòa. Lỗi xảy ra trên đường gọi gửi tác vụ và thường có thể được phía caller phát hiện ngay.
2. **Trong lúc chạy (execution):** executor đã chấp nhận công việc, một worker bắt đầu thực hiện, nhưng logic nghiệp vụ ném exception. Cách lỗi được chuyển về phụ thuộc API: `Future`, `CompletableFuture`, `@Async void` hay `execute(Runnable)` trực tiếp.

Phân biệt đúng hai nhóm lỗi quyết định cách đặt `try/catch`, trả HTTP status, ghi metrics, cấu hình exception handler và lựa chọn retry. **Không phải cứ “đã có executor” là exception sẽ được gửi lại thread caller**, cũng không phải mọi lỗi xuất hiện gần `execute(...)` đều là lỗi nghiệp vụ.

Bài học thuộc nhóm **Xử lý ngoại lệ** trong module **Spring Framework - concurrency**. Các đoạn mã tích hợp sử dụng Spring Framework 6.x, Java 17+; các chương trình Java độc lập phía dưới có thể biên dịch và chạy ngay bằng JDK 17.

## 1. Hai giai đoạn khác nhau: nhận việc và thực thi

Một chu trình thông thường:

~~~text
HTTP / service caller                TaskExecutor / queue                 Worker
          |                                   |                               |
          |---- execute / submit(task) ------>|                               |
          |                                   |--- thử nhận task ------------>|
          |                                   |                               |
          |       +---------------------------+                               |
          |       | REJECT: hết capacity, shutdown, policy                     |
          |<------X  exception khi gửi, task chưa được nhận                    |
          |                                                                       |
          |<------ accepted / Future ---------|                                  |
          |                                   |-- chờ worker ----------------->|
          |                                   |                           task.run()
          |                                   |                              |
          |                                   |                     +--------+--------+
          |                                   |                     |                 |
          |                                   |                   success           throw
          |                                   |                     |                 |
          |                                   |                 completed       execution failure
          |                                   |                                       |
          |                                   |                         Future / handler / log
~~~

Sơ đồ mô tả tình huống có thread pool bất đồng bộ và policy từ chối bằng exception; các implementation đồng bộ và chính sách `CallerRunsPolicy` có thể có thứ tự khác, được phân tích ở phần sau.

| Đặc điểm | Ngoại lệ lúc gửi tác vụ | Ngoại lệ trong lúc chạy |
| --- | --- | --- |
| Thời điểm | Trước khi executor chấp nhận task hoặc trong chính lời gọi submit | Sau khi task đã được chấp nhận và logic thực thi bắt đầu |
| Vấn đề tiêu biểu | Pool quá tải, queue đầy, executor shutdown, không còn khả năng nhận | Dữ liệu sai, lỗi DB/HTTP, parse lỗi, business rule, NPE, timeout I/O |
| Java thường gặp | `RejectedExecutionException` từ `execute`/`submit` | Exception phát sinh trong `Runnable.run()` hoặc `Callable.call()` |
| Spring thường gặp | `TaskRejectedException` từ `TaskExecutor` | Exception trong target method `@Async` |
| Caller thấy thế nào? | Thường ném đồng bộ từ lời gọi submit khi dùng policy từ chối bằng exception | Tùy hợp đồng: `Future.get()`/`join()`, `AsyncUncaughtExceptionHandler`, hoặc worker uncaught handler |
| Công việc có chắc đã chạy? | **Không** nếu bị từ chối trước khi nhận | **Có bắt đầu thực thi** (nhưng không nhất thiết hoàn tất thành công) |
| Cách ứng phó | Điều tiết tải, 503/429 phù hợp, backpressure, pool/queue sizing, kiểm tra shutdown | Logging, phân loại lỗi nghiệp vụ, retry có giới hạn, lưu trạng thái thất bại và quan sát downstream |

Cần lưu ý “ngoại lệ tại lời gọi gửi” và “ngoại lệ của bản thân task” là **hai khái niệm nhân quả**. Thời điểm caller nhận thấy lỗi có thể xảy ra muộn hơn lỗi gốc: ví dụ exception đã ném ở worker từ lâu, nhưng chỉ đến khi caller gọi `Future.get()` mới nhìn thấy `ExecutionException`.

## 2. Submission failure: task không được nhận

### 2.1. Vì sao executor từ chối?

Với `ThreadPoolExecutor`, và thông qua đó là `ThreadPoolTaskExecutor` của Spring, trình tự nhận việc cơ bản là:

1. Nếu số worker dưới `corePoolSize`, thử tạo worker mới.
2. Khi đã đạt core, thử đưa task vào queue.
3. Nếu queue không nhận được, thử tăng số worker tới `maxPoolSize`.
4. Khi không còn khả năng nhận, áp dụng `RejectedExecutionHandler`.

Ngoài trường hợp bão hòa, **executor đã shutdown cũng có thể từ chối task mới**, dù CPU rảnh hoặc queue chưa đầy. Không nhìn thấy queue đầy không đủ để loại trừ nguyên nhân rejection.

Ví dụ với `corePoolSize=1`, `maxPoolSize=1`, queueCapacity=1:

~~~text
Task A: worker #1 đang chạy và bị chặn
Task B: được nhận và nằm trong queue duy nhất
Task C: không có worker trống, queue đã đầy
        -> AbortPolicy -> rejected khi submit
~~~

Khi dùng Java `ThreadPoolExecutor.AbortPolicy`, lời gọi `execute(C)` ném `RejectedExecutionException`. Trong Spring `ThreadPoolTaskExecutor`, hợp đồng `TaskExecutor` thường bộc lộ trường hợp tương ứng dưới dạng `TaskRejectedException`, một subtype của `RejectedExecutionException`.

### 2.2. Bắt lỗi ở điểm gửi tác vụ

Đoạn Java minh họa:

~~~java
try {
    executor.execute(task);
    System.out.println("Submission accepted");
} catch (RejectedExecutionException ex) {
    System.err.println("Executor did not accept the task");
}
~~~

Đây là **mã rút gọn**, giả định `executor` và `task` đã được khai báo. Với Spring có bean kiểu `ThreadPoolTaskExecutor`, thường bắt `TaskRejectedException` ở ranh giới gửi để quyết định trả lỗi, giảm tải hoặc ghi lại công việc ở một hàng đợi bền vững nếu nghiệp vụ cần.

Quan trọng: một lời gọi `execute(...)` trả bình thường chỉ cho biết task đã được xử lý theo **policy** của executor, không bảo đảm task sẽ chạy thành công. Một policy có thể chạy task trên caller hoặc âm thầm bỏ task. Do đó “không có exception khi gửi” **không phải** xác nhận task đã được lưu bền vững hay đã hoàn tất.

### 2.3. Không phải policy nào cũng ném exception

| Chính sách `ThreadPoolExecutor` | Khi không thể nhận thêm | Hệ quả với caller |
| --- | --- | --- |
| `AbortPolicy` | Ném `RejectedExecutionException` | Caller thường phát hiện ngay; dễ phân biệt overload |
| `CallerRunsPolicy` | Nếu executor chưa shutdown, caller **tự chạy** `Runnable` | Lời gửi có thể bị chặn và exception task có thể xảy ra trên caller |
| `DiscardPolicy` | Bỏ task, thường không ném từ policy | Không có exception không có nghĩa đã xử lý |
| `DiscardOldestPolicy` | Bỏ task đứng đầu queue rồi thử tiếp nhận task mới | Có thể làm mất task khác và phá kỳ vọng về thứ tự/delivery |

Các policy này đều có rủi ro nghiệp vụ; cần chọn theo yêu cầu chịu tải, khả năng mất việc và cách ghi nhận kết quả. Ví dụ một yêu cầu thanh toán không nên dùng `DiscardPolicy` rồi chỉ dựa vào việc method gửi đã trả về.

### 2.4. Ngoại lệ đến từ caller không nhất thiết là rejection

Nếu caller tự parse tham số trước khi submit và ném `IllegalArgumentException`, lỗi đó thuộc **validation tại biên caller**, không phải executor từ chối. Nếu thiếu bean executor hoặc cấu hình proxy sai, lời gọi có thể thất bại trước bước tiếp nhận; không được gộp mọi exception đồng bộ thành `TaskRejectedException`.

Muốn phân loại chính xác, đo và log **nguyên nhân, loại exception, bean executor và giai đoạn lỗi**, thay vì chỉ ghi thông báo “async failed”.

## 3. Execution failure: task đã chạy rồi ném lỗi

Sau khi executor nhận task, worker thực thi logic. Ví dụ:

~~~java
Runnable task = () -> {
    throw new IllegalStateException("Business processing failed");
};
~~~

Đây là một `Runnable` minh họa. Lỗi ở đây là **lỗi thực thi bên trong task**. Khi dùng thread pool, nó thường không được ném ngược qua stack của method `execute(task)` đã trả về trước đó.

Điều người gọi quan sát được phụ thuộc API:

- **`Executor.execute(Runnable)` trực tiếp:** một exception chưa bắt trong `run()` thường đi qua cơ chế uncaught exception của worker thread; nó không tạo một Future kết quả cho caller. Với `ThreadPoolExecutor`, worker gặp lỗi runtime có thể kết thúc đột ngột và pool quản lý việc tạo worker thay thế theo nhu cầu.
- **`ExecutorService.submit(Runnable/Callable)`:** task thường được bọc trong `FutureTask`; exception trong worker được giữ thành trạng thái thất bại của Future. Caller gọi `get()` sẽ nhận `ExecutionException`, có thể kiểm tra `getCause()` để thấy nguyên nhân gốc.
- **`CompletableFuture`:** khi task hoàn tất thất bại, có thể dùng `whenComplete`/`handle`/`exceptionally` để xử lý. `join()` thường bộc lộ lỗi qua `CompletionException`; `get()` thường qua `ExecutionException`.
- **Spring `@Async` trả `void`:** không có Future trả về, nên Spring dùng `AsyncUncaughtExceptionHandler` cho exception chưa được bắt từ target method.
- **Spring `@Async` trả `Future`/`CompletableFuture<T>`:** exception thực thi được truyền theo Future để caller theo dõi; `AsyncUncaughtExceptionHandler` không phải kênh chính cho những trường hợp này.

**Không đồng nhất “exception trong worker” với “exception đã được caller xử lý”**. Nếu không ai gọi `Future.get()`, gắn completion callback hoặc instrument task, lỗi của một task `submit` có thể không được quan sát kịp thời.

## 4. Java 17 — chứng minh rejection trước khi chạy và lỗi bên trong worker

Chương trình dưới đây tạo tình huống có thể kiểm chứng **mà không dựa vào `Thread.sleep`**. Pool chỉ có một worker và queue sức chứa một task:

- Task A được chạy và giữ worker bằng latch.
- Task B được submit thành công vào queue nhưng khi chạy sẽ ném lỗi nghiệp vụ.
- Task C bị từ chối **ngay tại lời gọi gửi** vì worker và queue đang bận.
- Sau khi mở latch, B mới được chạy và Future của B hoàn tất thất bại.

Tạo tệp `SubmitVsRunErrorDemo.java`:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SubmitVsRunErrorDemo {
    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("First worker did not start");
            }

            Future<?> second = pool.submit(() -> {
                throw new IllegalStateException("business failure");
            });
            System.out.println("1. Second task accepted");

            try {
                pool.execute(() -> System.out.println("never run"));
                System.out.println("unexpected acceptance");
            } catch (RejectedExecutionException ex) {
                System.out.println("2. Third task rejected during submission");
            }

            release.countDown();
            try {
                second.get(5, TimeUnit.SECONDS);
                System.out.println("unexpected success");
            } catch (ExecutionException ex) {
                System.out.println("3. Second task failed while running: "
                        + ex.getCause().getClass().getSimpleName());
            }
            System.out.println("4. Second task done=" + second.isDone());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy với Java 17 trở lên:

~~~bash
javac --release 17 SubmitVsRunErrorDemo.java
java SubmitVsRunErrorDemo
~~~

Stdout đã được xác minh bằng JDK khi biên dịch với `--release 17`:

~~~text
1. Second task accepted
2. Third task rejected during submission
3. Second task failed while running: IllegalStateException
4. Second task done=true
~~~

**Giải thích vì sao kết quả xác định:**

1. `started.await(...)` chờ tới khi A thực sự bắt đầu. A sẽ không rời worker trước khi `release.countDown()`.
2. Khi B được `submit`, queue còn đúng một chỗ và được điền bởi B; Future đã được trả về trước khi B có thể chạy.
3. C xuất hiện khi A còn giữ worker và B còn chiếm queue; `AbortPolicy` ném ngay `RejectedExecutionException`. C **không chạy**, không có Future và không có exception nghiệp vụ từ C.
4. `release.countDown()` cho A thoát; worker lấy B. B ném `IllegalStateException` trong worker, được giữ trong Future.
5. `second.get(...)` ném `ExecutionException` trên caller **lúc đọc kết quả**; `getCause()` là `IllegalStateException` gốc. `isDone()==true` không có nghĩa B thành công; trạng thái thất bại cũng được coi là hoàn tất.

Ví dụ không giả vờ đo thời gian cụ thể. Đối với code production, cần quan tâm thêm timeout, admission control, logging và shutdown có kiểm soát.

## 5. Java 17 — vì sao execute trực tiếp khác submit khi task ném lỗi?

Một điểm tinh tế: **`execute(...)` không nhất thiết tạo `FutureTask`**, còn `submit(...)` thường bọc task để lưu kết quả và exception.

Với `ThreadPoolExecutor.execute(rawRunnable)`, exception chưa bắt có thể đi tới `Thread.UncaughtExceptionHandler` của worker. Với `ExecutorService.submit(rawRunnable)`, FutureTask ghi nhận lỗi và `Future.get()` cung cấp đường đọc lỗi cho caller; uncaught handler của worker thường **không được gọi vì FutureTask đã bắt exception**.

Chương trình sau minh họa phía `execute` trực tiếp, không cần Spring. Tạo tệp `ExecuteUncaughtDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ExecuteUncaughtDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch handled = new CountDownLatch(1);
        ThreadFactory factory = task -> {
            Thread worker = new Thread(task, "worker-example");
            worker.setUncaughtExceptionHandler((thread, error) -> {
                System.out.println("1. Uncaught on " + thread.getName()
                        + ": " + error.getClass().getSimpleName());
                handled.countDown();
            });
            return worker;
        };
        ExecutorService pool = Executors.newSingleThreadExecutor(factory);
        try {
            pool.execute(() -> {
                throw new IllegalStateException("worker crash");
            });
            if (!handled.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Handler was not called");
            }
            System.out.println("2. Main observed uncaught handler");
        } finally {
            pool.shutdownNow();
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 ExecuteUncaughtDemo.java
java ExecuteUncaughtDemo
~~~

Stdout đã xác minh:

~~~text
1. Uncaught on worker-example: IllegalStateException
2. Main observed uncaught handler
~~~

Hạn chế cần hiểu: đây là hành vi của **Java ExecutorService với `execute(rawRunnable)`**. Spring `@Async` có interceptor và cơ chế xử lý exception riêng, vì vậy không được suy ra rằng mọi lỗi `@Async void` chắc chắn đến `Thread.UncaughtExceptionHandler` của worker.

Việc đếm lỗi chỉ bằng uncaught handler cũng không đủ nếu hệ thống dùng `submit`, `CompletableFuture` hoặc các wrapper bắt exception. Quan sát kết quả phải phù hợp với API thật sự được dùng.

## 6. Spring Framework: một executor, hai kênh lỗi rõ ràng

Phần này là mã **tích hợp trong Spring Framework 6.x / Java 17+**; cần Spring Context, Spring AOP, component scanning và `@EnableAsync`. Mỗi `public class` nằm trong tệp trùng tên ở package `example.concurrency`. Những đoạn sau **không chạy với `javac` đơn lẻ nếu không có dependency Spring**.

### 6.1. Cấu hình ThreadPoolTaskExecutor và handler cho @Async void

Tệp `AsyncErrorConfiguration.java`:

~~~java
package example.concurrency;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncErrorConfiguration implements AsyncConfigurer {

    @Bean("jobExecutor")
    public ThreadPoolTaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("job-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (exception, method, args) ->
                System.err.println("Async void execution failed: "
                        + method.getName() + " / "
                        + exception.getClass().getSimpleName());
    }
}
~~~

Ý nghĩa:

- `jobExecutor` là bean executor duy nhất được ví dụ chọn qua `@Async("jobExecutor")`. `@EnableAsync` kích hoạt cách Spring intercept lời gọi qua proxy.
- `core=2`, `max=4`, `queueCapacity=16` chỉ là giá trị demo, không phải cấu hình production khuyến nghị chung.
- `AbortPolicy` giúp pool bão hòa dẫn tới tín hiệu từ chối rõ ràng ở lời submit; với Spring `ThreadPoolTaskExecutor`, thường thấy `TaskRejectedException`.
- `AsyncConfigurer.getAsyncUncaughtExceptionHandler()` được Spring dùng cho exception chưa được xử lý từ **target method `@Async void`**. Ví dụ chỉ in tên method/loại lỗi; trong sản phẩm nên dùng structured logging, metrics, correlation id và không ghi lộ các tham số nhạy cảm.

Spring tự quản lý lifecycle/initialization của bean executor. Không cần gọi `initialize()` trong factory method nếu bean được tạo qua ApplicationContext theo cơ chế chuẩn.

### 6.2. Worker có hai loại phương thức async

Tệp `JobWorker.java`:

~~~java
package example.concurrency;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class JobWorker {

    @Async("jobExecutor")
    public CompletableFuture<String> generate(String jobId) {
        if (jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is blank");
        }
        if (jobId.equals("fail")) {
            throw new IllegalStateException("generate failed");
        }
        return CompletableFuture.completedFuture("generated:" + jobId);
    }

    @Async("jobExecutor")
    public void notifyBestEffort(String id) {
        if (id.equals("fail")) {
            throw new IllegalStateException("notification failed");
        }
        System.out.println("Notified: " + id);
    }
}
~~~

Với `@Async("jobExecutor")` hoạt động đúng:

- Gọi `generate("ok")`: caller nhận `CompletableFuture<String>`; task có thể chờ queue trước khi chạy. Nếu thành công, Future hoàn tất với `"generated:ok"`.
- Gọi `generate("fail")`: nếu submit thành công, `IllegalStateException` xảy ra **ở worker**. Future của caller hoàn tất thất bại; caller xem được thông qua `whenComplete(...)`, `join()` hoặc `get()`.
- Gọi `notifyBestEffort("fail")`: nếu submit thành công, lỗi xuất hiện trong target method `@Async void`, không có Future trả về nên Spring gửi lỗi chưa được bắt tới `AsyncUncaughtExceptionHandler`.
- Với **bất kỳ** method nào, nếu executor reject ngay trong bước submit, caller có thể nhận `TaskRejectedException` **trước khi có kết quả async**; handler dành cho lỗi thực thi của `void` không thay thế xử lý rejection.

Ví dụ dùng `String.isBlank()` (Java 11+) nên tương thích Java 17. Đây là logic minh họa; nếu cần validate tham số **trước khi submit**, nên kiểm tra ở biên controller/service caller để trả lỗi 4xx đồng bộ thay vì đẩy lỗi dữ liệu lên worker.

### 6.3. Caller bắt lỗi gửi, Future nhận lỗi chạy

Đoạn trong một bean khác đã inject `JobWorker worker`:

~~~java
try {
    CompletableFuture<String> future = worker.generate("fail");

    future.whenComplete((value, error) -> {
        if (error != null) {
            System.err.println("Execution failed: " + error);
        } else {
            System.out.println("Success: " + value);
        }
    });
} catch (org.springframework.core.task.TaskRejectedException ex) {
    System.err.println("Submission rejected: executor is not accepting work");
}
~~~

Đây là **mã caller minh họa**, không có stdout cố định vì việc chạy phụ thuộc executor và tải. Hai nhánh quan sát **không giống nhau**:

- `catch (TaskRejectedException)` bắt tình huống **không gửi được công việc** theo policy từ chối bằng exception.
- `whenComplete((value, error) -> ...)` quan sát **kết quả thực thi** của task đã được nhận, có thể đã bắt đầu hoặc còn chờ khi caller đăng ký callback.

Với `CompletableFuture`, `join()` thường ném `CompletionException` khi task lỗi, còn `get()` ném `ExecutionException` (hoặc các exception liên quan cancellation/interruption). Cả hai có thể bọc cause là exception nghiệp vụ thật; đừng dựa vào chuỗi thông báo bên ngoài để phân loại lỗi. Nếu caller dùng `join()` ngay sau khi submit, nó sẽ chờ task và không còn hoàn toàn không chặn.

### 6.4. Caller của @Async void xử lý hai nguồn lỗi thế nào?

Đoạn trong bean khác:

~~~java
try {
    worker.notifyBestEffort("fail");
    // Trả về bình thường chỉ có nghĩa lời gọi qua proxy đã đi qua
    // đường submit theo chính sách executor hiện hành.
} catch (org.springframework.core.task.TaskRejectedException ex) {
    System.err.println("Submission rejected");
}
~~~

Nếu task được nhận rồi ném `IllegalStateException` trong worker, `catch` trên không bắt lỗi đó; handler trong `AsyncErrorConfiguration` mới là kênh xử lý lỗi thực thi `@Async void`. Nếu task bị reject, handler đó không có một target exception nào để xử lý vì method chưa chạy.

Với `void`, caller không có handle để biết tác vụ đã hoàn thành hay chưa. Nếu cần acknowledgement về thành công/thất bại, hãy dùng `CompletableFuture<Void>` hoặc cơ chế job id/trạng thái thay vì chỉ thêm `@Async` cho method `void`.

## 7. Bảng phân biệt đường đi của exception theo API

| API / hợp đồng | Lỗi không nhận task | Lỗi target method đã chạy | Ai thường nhìn thấy? |
| --- | --- | --- | --- |
| Java `ThreadPoolExecutor.execute(Runnable)` + AbortPolicy | `RejectedExecutionException` ở `execute` | Uncaught trên worker nếu `Runnable` không bắt | Caller cho rejection; worker uncaught handler/log cho lỗi chạy |
| Java `ExecutorService.submit(Callable)` | `RejectedExecutionException` ở `submit` | Future hoàn tất thất bại; `get()` ném `ExecutionException` | Caller lúc gọi `get()`; metrics/callback nếu instrument |
| Java `CompletableFuture.runAsync(..., executor)` | Có thể ném `RejectedExecutionException` khi yêu cầu không được nhận | CompletableFuture hoàn tất thất bại | Caller khi submit; callback/`join()` cho lỗi chạy |
| Spring `TaskExecutor.execute(Runnable)` + AbortPolicy | `TaskRejectedException` | Phụ thuộc loại executor/wrapper và uncaught handler | Caller đối với rejection; worker/instrumentation cho lỗi chạy |
| Spring `@Async void` | `TaskRejectedException` tại proxy submit | `AsyncUncaughtExceptionHandler` | Caller cho lỗi gửi; handler cho lỗi chạy |
| Spring `@Async CompletableFuture<T>` | `TaskRejectedException` tại proxy submit | Future hoàn tất thất bại | Caller cho lỗi gửi; Future callback/`get()`/`join()` cho lỗi chạy |

Bảng lấy trường hợp **executor bất đồng bộ với chính sách AbortPolicy hoặc tương đương** làm mặc định để so sánh. Cần điều chỉnh cách diễn giải khi dùng `CallerRunsPolicy`, `DiscardPolicy`, `SyncTaskExecutor` hoặc wrapper tùy biến. Không biến bảng thành một kết luận rằng exception của task **không bao giờ** ném trên caller trong mọi implementation.

## 8. Trường hợp ngoại lệ vượt ranh giới giai đoạn

### 8.1. CallerRunsPolicy có thể chạy target ngay trên caller

Khi pool quá tải và policy là `CallerRunsPolicy`, lời `execute(rawRunnable)` có thể trực tiếp gọi `run()` trên caller. Nếu `run()` ném exception chưa bắt, exception đó có thể xuất hiện ngay **trong lời `execute(...)`**, dù nguyên nhân thuộc **logic thực thi**, không phải executor reject bằng AbortPolicy.

Ngược lại, nếu tác vụ gửi bằng `submit(...)` được bọc trong `FutureTask`, lỗi vẫn có thể được lưu trong Future, ngay cả khi `CallerRunsPolicy` khiến nó thực thi đồng bộ ở caller. **Kiểu wrapper và policy quan trọng hơn cách suy luận “nó ném sớm nên chắc là rejection”.**

### 8.2. SyncTaskExecutor không chuyển sang worker khác

Spring `SyncTaskExecutor` chạy `Runnable` trên thread gọi. Nếu method ném exception, caller có thể nhìn thấy ngay. Đây không phải ngoại lệ “bị trả về từ background thread”. Khi test dùng executor đồng bộ còn production dùng thread pool, kết quả của test về timing và exception propagation có thể khác. Nên có integration test xác nhận executor thật.

### 8.3. Self-invocation bỏ qua proxy @Async

Trong proxy mode mặc định, một method của bean gọi `this.otherAsyncMethod()` bên trong chính bean thường không đi qua interceptor `@Async`. Method có thể chạy **đồng bộ** trên caller và ném exception ngay, khiến người học nhầm rằng lỗi worker tự truyền về caller.

Kiểm tra `@EnableAsync`, bean do Spring quản lý và việc lời gọi đi qua proxy trước khi kết luận về hành vi lỗi. Thực tế thường tách method async sang bean khác để giữ đường gọi rõ ràng.

### 8.4. Exception bị bắt bên trong worker

Nếu method đã tự bắt exception rồi trả về bình thường, Future có thể hoàn tất thành công hoặc handler không được gọi, dù một bước nghiệp vụ bên trong đã thất bại. Đây không phải “Spring làm mất exception”; nó là hệ quả của việc code đã **nuốt lỗi**.

Chỉ catch khi có chiến lược xử lý thật: trả kết quả biểu diễn lỗi, cập nhật trạng thái, retry có giới hạn, hoặc ném lại để hoàn tất Future thất bại/đưa qua handler. Không dùng `catch (Exception ignored)` cho công việc bắt buộc.

## 9. Từ exception kỹ thuật đến HTTP response và SLA

Giả sử một endpoint gửi yêu cầu tạo báo cáo nền:

| Tình huống | Phản hồi hợp lý ở biên API | Nghĩa của phản hồi |
| --- | --- | --- |
| Dữ liệu đầu vào sai trước khi submit | `400 Bad Request` khi phù hợp | Request không hợp lệ; không gửi task |
| Executor bão hòa, từ chối nhận task | `503 Service Unavailable` hoặc `429 Too Many Requests` tùy hợp đồng | Hệ thống không nhận công việc; caller nên biết để điều tiết/retry |
| Task đã được nhận vào executor in-memory | Có thể `202 Accepted` theo hợp đồng API | Đã được submit; **chưa** phải bảo đảm thành công hoặc bền vững |
| Task chạy rồi lỗi sau khi HTTP response đã trả | Không thể đổi status của response trước đó | Cần kênh job status, completion event, metrics/log/alert |
| Công việc bắt buộc lưu bền vững | Xác nhận theo việc lưu thành công vào job store/queue | Không đồng nhất `ThreadPoolTaskExecutor.execute` với durable acceptance |

`503` và `429` không phải mapping bắt buộc của Spring cho mọi `TaskRejectedException`; đó là **quyết định thiết kế API** tùy nguyên nhân và chính sách điều tiết. Việc retry từ client cần backoff/jitter và idempotency để không tạo bão yêu cầu mới.

Với `@Async CompletableFuture<T>`, caller có thể gắn callback hoặc lưu job id. Nếu endpoint đã trả `202`, lỗi sau đó nên cập nhật trạng thái/ghi cảnh báo hoặc gửi thông báo qua kênh khác; không giả định completion lỗi sẽ tự chuyển response thành 500.

## 10. Retry khác nhau theo giai đoạn

**Khi submission bị từ chối:**

- Kiểm tra nguyên nhân saturation hay shutdown. Nếu node đang shutdown, retry cùng node ngay có thể vô ích.
- Chọn backpressure, bounded queue, rate limiting, giới hạn concurrency và cân đối năng lực.
- Nếu nghiệp vụ cho phép retry, đặt ngân sách retry, timeout/deadline, backoff và jitter; tránh vòng lặp `while(true) submit()` ngay trên request thread.
- Với công việc không được mất, cần ghi task vào hàng đợi bền vững trước hoặc trong giao thức tiếp nhận có ACK; một queue in-memory không đủ.

**Khi task đã chạy rồi lỗi:**

- Phân loại lỗi tạm thời (kết nối, quá tải downstream) và lỗi cố định (input không hợp lệ, điều kiện nghiệp vụ không thỏa).
- Retry phải dựa vào **idempotency**: một lần chạy có thể đã ghi dữ liệu một phần trước khi ném exception. Không mặc định chạy lại là không tạo side effect trùng.
- Không retry lỗi validation vĩnh viễn vô hạn.
- Nếu Future hoàn tất thất bại, quyết định trạng thái job, số lần thử và cách báo lỗi tới caller. Với `@Async void`, handler có thể phát cảnh báo nhưng tự nó không có logic retry.
- Có timeout độc lập cho HTTP/JDBC và deadline end-to-end, không chỉ cho `Future.get(timeout)`.

Ngoài ra, cancellation/interruption là nhánh riêng. `Future.cancel(true)` có thể yêu cầu ngắt, nhưng không bảo đảm task đang chạy ngừng ngay hoặc rollback thao tác đã làm. Bị hủy trước khi thực thi cũng không tương đương task chạy rồi lỗi nghiệp vụ.

## 11. Logging và metrics phải phân biệt lỗi submit/lỗi run

Khi theo dõi hệ thống, ghi nhận các loại sự kiện riêng thay vì một counter “async failed” mơ hồ:

| Sự kiện | Mốc ghi | Gợi ý thông tin |
| --- | --- | --- |
| `submitted_total` | Lúc caller bắt đầu gửi | Tên workload/loại task, correlation id |
| `accepted_total` | Sau khi executor nhận thành công theo policy có xác nhận | Executor, kích thước/giới hạn queue, trạng thái thời điểm nhận |
| `rejected_total` | Khi submit thất bại | Executor name, pool size, active count, queue depth, shutdown flag |
| `started_total` | Đầu worker callback | Job id, wait duration từ lúc submit |
| `completed_total` | Khi task hoàn tất thành công | Runtime, workload |
| `execution_failed_total` | Khi task đã bắt đầu và lỗi | Nhóm exception, downstream, retry count |
| `cancelled_total` | Khi được hủy | Task đang queue hay đang chạy, kết quả yêu cầu hủy |

Đây là **tên metric gợi ý**, không phải những metric mà Spring tự sinh với đúng tên đó. Instrumentation phải đúng vị trí: exception của `FutureTask` có thể được lưu trong Future, nên chỉ dựa vào `Thread.UncaughtExceptionHandler` hoặc `TaskDecorator` catch quanh `callback.run()` có thể **bỏ sót lỗi thực thi**.

Với một `FutureTask`, wrapper có thể hoàn tất bình thường ở mức `run()` bên ngoài trong khi Future đã ở trạng thái thất bại. Tùy loại executor, có thể quan sát `afterExecute(...)`, completion callback hoặc instrumentation ở chính ranh giới nghiệp vụ. Tránh tự `get()` chặn worker trong mọi callback nếu có thể gây deadlock/starvation.

Không log toàn bộ payload hoặc exception arguments có chứa token/dữ liệu nhạy cảm. Dùng job id và trace id để nối các giai đoạn.

## 12. Kiểm thử hai lỗi mà không dựa vào timing may rủi

Một bài integration test tốt có ít nhất hai ca **khác nhau**:

### 12.1. Case A: rejection ngay tại lời gửi

- Dùng executor core=max=1, queueCapacity=1, `AbortPolicy`.
- Dùng `CountDownLatch` giữ worker A.
- Gửi B vào queue, sau đó gửi C.
- Xác nhận C bị `TaskRejectedException` (trong Spring) hoặc `RejectedExecutionException` (Java) khi gọi submit.
- Xác nhận logic task C **không hề được gọi**.
- Mở latch và shutdown pool trong `finally` để test không treo.

Không chỉ thử gửi hàng nghìn task với `Thread.sleep` rồi đoán có rejection: test như vậy dễ chập chờn theo máy CI.

### 12.2. Case B: lỗi sau khi task đã được chấp nhận

- Submit một công việc được thiết kế sẽ ném `IllegalStateException` ở worker.
- Với Future, xác nhận `get()` ném `ExecutionException` với cause đúng; với `CompletableFuture`, xác nhận callback nhận lỗi hoặc Future hoàn tất thất bại.
- Với `@Async void`, inject một `AsyncUncaughtExceptionHandler` testable và xác nhận handler được gọi với method/exception đúng.
- Xác nhận không nhầm lỗi worker với `TaskRejectedException`.
- Gọi phương thức qua Spring proxy, không `new Service()` và không self-invocation, để test thật sự đi qua `@Async`.

### 12.3. Case C: chính sách đặc biệt và lifecycle

Thử thêm `CallerRunsPolicy`, `SyncTaskExecutor` và shutdown khi có yêu cầu tương ứng. Chúng có thể cho behavior khác về thread và thời điểm exception xuất hiện. Xác minh test không dựa vào giả định “mọi task đều ở background”.

## 13. Những nhầm lẫn thường gặp

**“`try/catch` quanh `submit(...)` sẽ bắt hết exception bên trong task.”** Không. Nó thường bắt rejection/lỗi lúc gửi; exception execution của task được lưu vào Future và được nhìn thấy qua `get()`/completion callback.

**“`Future.isDone() == true` tức là thành công.”** Không. Future có thể đã hoàn thành bằng lỗi hoặc cancellation.

**“`@Async void` chạy lỗi sẽ quay về controller.”** Không theo đường thông thường khi đã được submit bất đồng bộ. Spring dùng `AsyncUncaughtExceptionHandler`; caller có thể đã trả response.

**“Mọi exception xuất hiện trong `execute(...)` đều là rejection.”** Không. `CallerRunsPolicy` hoặc executor đồng bộ có thể chạy logic task ngay trên caller, và logic đó có thể ném exception.

**“`@Async CompletableFuture` có nghĩa submission không thể fail.”** Không. Executor có thể từ chối trước khi Future được trả cho caller.

**“Không thấy uncaught exception log nghĩa là task submit không lỗi.”** Không. `submit` thường bọc exception vào `FutureTask`; handler của worker có thể không được gọi.

**“Pool còn CPU rảnh thì không bao giờ reject.”** Không. Pool có thể shutdown, workers bị chặn, queue đầy hoặc giới hạn max bị chạm.

**“Đã trả 202 thì task chắc chắn hoàn tất.”** Không. Queue in-memory có thể mất khi crash, task có thể lỗi và response đã trả không đổi.

**“Chỉ thêm retry cho mọi loại exception là an toàn.”** Không. Retry lỗi cố định hoặc side effect không idempotent có thể gây lặp hành động và làm hệ thống quá tải thêm.

## 14. Bài tập tự kiểm tra

**Câu 1.** `ThreadPoolTaskExecutor` đã bão hòa và cấu hình `AbortPolicy`; controller gọi `@Async` method. Exception phát sinh ở đâu và làm gì?

**Đáp án:** có thể nhận `TaskRejectedException` ngay ở lời gọi gửi (trong proxy). Controller cần phản hồi quá tải/giảm tải/điều phối retry, không đợi worker xử lý task chưa được nhận.

**Câu 2.** `ExecutorService.submit(...)` trả `Future`, task được chấp nhận rồi ném `IllegalArgumentException`. `try/catch` quanh lời submit có chắc bắt được lỗi đó không?

**Đáp án:** không. Khi gọi `Future.get()`, caller thường nhận `ExecutionException` có cause là `IllegalArgumentException`.

**Câu 3.** Khi gọi `CompletableFuture.join()` cho Future hoàn tất thất bại, thường gặp wrapper gì?

**Đáp án:** `CompletionException`, với cause thể hiện nguyên nhân thực thi; `get()` thường dùng `ExecutionException`.

**Câu 4.** Một method `@Async void` đã được nhận nhưng lỗi ở worker. Caller có nhận lỗi qua return value không?

**Đáp án:** không. Cần `AsyncUncaughtExceptionHandler`, logging/metrics hoặc kênh trạng thái riêng.

**Câu 5.** Vì sao `CallerRunsPolicy` làm cách phân loại “sớm/muộn” trở nên nguy hiểm?

**Đáp án:** raw `Runnable` có thể được chạy trên caller trong lời `execute(...)`; exception nghiệp vụ sẽ xuất hiện ngay tại phương thức gửi dù bản chất là lỗi thực thi.

**Câu 6.** Vì sao `submit` có thể không kích hoạt uncaught exception handler của worker khi task lỗi?

**Đáp án:** task được bọc bởi `FutureTask`, wrapper giữ exception để caller đọc qua Future.

**Câu 7.** Đã submit thành công task tạo biên lai nhưng JVM crash trước khi worker xử lý. Đây có phải “execution exception” mà Future chắc chắn bắt được không?

**Đáp án:** không. Lỗi crash/loss-of-process nằm ngoài đường báo lỗi thông thường; cần cơ chế tiếp nhận/lưu công việc bền vững.

**Câu 8.** Khi theo dõi chất lượng, nên có một counter `async_failed_total` hay tách loại lỗi?

**Đáp án:** nên tách ít nhất `rejected_total` (không nhận được) và `execution_failed_total` (đã chạy nhưng thất bại); thêm hoàn tất, queue wait và cancel theo yêu cầu.

## 15. Tài liệu tham khảo chính thức

- Spring Framework Reference 6.2 — Task Execution and Scheduling, `@Async`, Exception Management: https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html
- Spring Framework API 6.2 — `ThreadPoolTaskExecutor` và `TaskRejectedException`: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework API 6.2 — `@Async`: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/Async.html
- Spring Framework API 6.2 — `@EnableAsync` / `AsyncConfigurer`: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html
- Oracle Java SE 17 — `ThreadPoolExecutor`, rejection policies: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Oracle Java SE 17 — `ExecutorService` / `Future`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Oracle Java SE 17 — `ExecutionException`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-002">ExecutionException, CompletionException và nguyên nhân gốc</a>

<details>
<summary>Xem chi tiết</summary>

# ExecutionException, CompletionException và nguyên nhân gốc

## 1. Tại sao một lỗi nghiệp vụ lại xuất hiện dưới tên exception khác?

Khi một công việc Java chạy qua `ExecutorService`, `CompletableFuture` hay Spring `@Async`, lỗi xảy ra **trong worker** thường không được ném trực tiếp vào stack của **thread gọi**. Thay vào đó, lỗi được lưu như trạng thái thất bại của một `Future`. Khi caller lấy kết quả hoặc phối hợp các giai đoạn, API báo thất bại bằng một **exception bao bọc (wrapper exception)**.

Hai wrapper quan trọng nhất:

- **`ExecutionException`**: thường gặp khi gọi `Future.get()`, `CompletableFuture.get()` hoặc `get(timeout, unit)` và computation đã thất bại. Đây là **checked exception**.
- **`CompletionException`**: thường gặp khi gọi `CompletableFuture.join()`, `getNow(...)` hoặc dùng các chuỗi `CompletionStage` có giai đoạn hoàn thành lỗi. Đây là **unchecked exception**.

Ví dụ một worker gặp `IllegalStateException("database unavailable")`. Caller có thể quan sát:

~~~text
Future.get()
  └── ExecutionException
        └── cause: IllegalStateException("database unavailable")

CompletableFuture.join()
  └── CompletionException
        └── cause: IllegalStateException("database unavailable")
~~~

Cả hai đường đều có thể mô tả **cùng một lỗi gốc**, chứ không phải hai lỗi nghiệp vụ khác nhau. Cần đọc **`getCause()`** và toàn bộ cause chain để hiểu lý do thật sự. Không nên tạo alert chỉ vì thấy `ExecutionException` rồi kết luận executor bị hỏng.

**Thông điệp chính:** wrapper nói về *cơ chế truyền kết quả bất đồng bộ*; nguyên nhân bên trong mới thường nói về *điều kiện nghiệp vụ/kỹ thuật dẫn tới thất bại*. Nhưng đừng mù quáng bỏ tất cả wrapper: đôi khi một exception ở tầng ứng dụng có ý nghĩa và cần được giữ nguyên trong log.

## 2. Mô hình lỗi của một công việc bất đồng bộ

### 2.1. Lỗi xảy ra ở worker, được quan sát ở caller

~~~text
Thread caller                           Worker trong pool
-------------                           ------------------
future = executor.submit(task)  ----->  chạy task
                                                |
                                                +-- throw SQLException /
                                                |   IllegalStateException
                                                |
                                                +-- Future ghi nhận thất bại

future.get() -------------------------->  Future đã hoàn thành lỗi
      |
      +-- throw ExecutionException(cause)
      |
      +-- caller xử lý hoặc chuyển lỗi sang domain/API
~~~

Nếu caller dùng `CompletableFuture.join()`, đường trả lỗi thường đi qua `CompletionException`, không phải `ExecutionException`. Đoạn code gọi `get` vẫn có thể **chặn chờ** cho đến khi worker hoàn thành; wrapper chỉ xuất hiện ở thời điểm caller quan sát trạng thái thất bại.

Phân biệt rõ **exception khi submit** và **exception khi thực thi**:

- **Submit bị từ chối**: một `RejectedExecutionException` (hoặc `TaskRejectedException` qua Spring TaskExecutor) có thể xuất hiện ngay khi gửi task. Khi đó chưa chắc đã có Future để đợi.
- **Task được nhận rồi thất bại**: lỗi thường được giữ bên trong Future/CompletableFuture và xuất hiện khi `get`, `join` hoặc callback quan sát lỗi.
- **Task bị policy bỏ âm thầm**: một Future có thể **không hoàn thành** nếu task không chạy và không bị hủy; gọi `get()` vô thời hạn không nhất thiết nhận được wrapper nào.

Không nên dùng chung một `catch (ExecutionException)` để xử lý mọi vấn đề của thread pool: rejection, cancellation, timeout và interruption có đường báo lỗi khác.

### 2.2. Cấu trúc Throwable và cause chain

Các lớp liên quan trong JDK:

~~~text
Throwable
  └── Exception
       ├── InterruptedException              (checked)
       ├── TimeoutException                  (checked)
       ├── ExecutionException                (checked wrapper)
       └── RuntimeException
            ├── CompletionException          (unchecked wrapper)
            └── CancellationException        (unchecked)
~~~

Ví dụ:

~~~java
Throwable outer = new CompletionException(
        new ExecutionException(
                new IllegalStateException("connection lost")));
~~~

Đây là một **mảnh code minh họa** cần import `java.util.concurrent.*`. Cause chain lúc này gồm **hai wrapper**. Chỉ gọi `outer.getCause()` một lần sẽ còn thấy `ExecutionException`. Nếu cần diễn giải lỗi gốc, phải đi tiếp qua wrapper thích hợp.

Không phải Throwable nào cũng có `getCause()`. Một exception có thể không có cause, có suppressed exceptions hoặc có cause mang ý nghĩa domain. Vì vậy một helper nên có **điều kiện dừng rõ ràng**, không luôn lặp tới Throwable sâu nhất rồi quên mất các tầng trung gian.

## 3. ExecutionException: hợp đồng của Future.get()

### 3.1. Khi nào xuất hiện?

`Future.get()` chờ cho đến khi công việc hoàn thành, rồi:

1. **Thành công**: trả giá trị.
2. **Worker thất bại**: ném `ExecutionException`, với `getCause()` đại diện cho lỗi của computation.
3. **Future bị hủy**: ném `CancellationException`, không phải `ExecutionException`.
4. **Thread đang chờ bị interrupt**: ném `InterruptedException`.
5. **Dùng bản `get(timeout, unit)` và hết thời gian chờ**: ném `TimeoutException`.

`ExecutionException` là checked exception; code Java phải bắt hoặc khai báo nó. Ví dụ:

~~~java
try {
    String result = future.get(3, TimeUnit.SECONDS);
    consume(result);
} catch (ExecutionException ex) {
    Throwable computationError = ex.getCause();
    reportFailure(computationError);
}
~~~

Đây là **mảnh code minh họa**, chưa bao gồm `future`, `consume`, `reportFailure`, cũng như các nhánh xử lý `InterruptedException`, `TimeoutException` và `CancellationException`. Trong code thật cần xử lý đầy đủ hợp đồng của `get(timeout)`.

**Một điểm quan trọng:** `ExecutionException` cho biết computation đã thất bại; nó không phải bản thân lỗi `SQLException`, `IllegalArgumentException` hay exception domain. Lấy cause để xác định nguyên nhân, nhưng **giữ wrapper trong exception log** nếu cần nhìn ranh giới giữa caller và worker.

### 3.2. Future từ ExecutorService và CompletableFuture

Cả hai dạng này có thể đưa lỗi tới `ExecutionException` khi caller gọi `get()`:

~~~java
Future<String> f1 = executor.submit(() -> {
    throw new IllegalArgumentException("bad input");
});

CompletableFuture<String> f2 =
        CompletableFuture.failedFuture(
                new IllegalStateException("service unavailable"));
~~~

Đây là mảnh code cần khai báo `executor`. Dù loại Future khác nhau, việc gọi `get()` trên một computation thất bại đều theo hợp đồng `Future`.

Khác biệt nằm ở API bổ sung: `CompletableFuture` có `join`, `handle`, `exceptionally`, `whenComplete`, `thenCompose` và nhiều cách phối hợp không đòi caller chờ ngay tại mỗi bước.

## 4. CompletionException: hợp đồng của CompletableFuture.join()

`CompletableFuture.join()` chờ kết quả giống `get()` ở điểm có thể **block**, nhưng **không khai báo checked exception**. Nếu stage đã hoàn thành lỗi bởi một computation, `join()` thường ném `CompletionException` bao bọc nguyên nhân.

~~~java
CompletableFuture<String> failed =
        CompletableFuture.failedFuture(
                new IllegalStateException("remote API failed"));

try {
    failed.join();
} catch (CompletionException ex) {
    System.out.println(ex.getCause().getMessage());
}
~~~

Stdout của **mảnh code** khi chạy trong một method hợp lệ:

~~~text
remote API failed
~~~

`join()` không có overload nhận timeout. Nếu cần giới hạn thời gian chờ ở caller, có thể dùng `get(timeout, unit)`; còn `CompletableFuture.orTimeout` thay đổi **trạng thái hoàn thành của future** nếu chưa xong đúng hạn và không mặc nhiên ngắt computation nền.

### 4.1. Vì sao CompletionException cũng có thể ở giữa một chuỗi?

Với `CompletableFuture`, exception có thể phát sinh từ một callback của `thenApply`, `thenCombine`, `thenCompose`, `supplyAsync` hoặc một stage khác.

~~~java
CompletableFuture<Integer> parsed =
        CompletableFuture.completedFuture("not-a-number")
                .thenApply(Integer::parseInt);
~~~

Stage `parsed` hoàn thành exceptional vì `NumberFormatException`; khi caller `parsed.join()`, nó quan sát `CompletionException` với cause liên quan đến `NumberFormatException`.

**Callback nhận `Throwable` không phải lúc nào cũng nhận cùng một hình dạng wrapper** ở mọi vị trí trong chuỗi. Tùy stage, bạn có thể nhận chính nguyên nhân gốc hoặc một `CompletionException` bao bên ngoài. Do đó code xử lý lỗi nên biết kiểm tra cause chain thay vì hard-code `(MyDomainException) error.getCause()` mà không kiểm tra kiểu và `null`.

### 4.2. CompletableFuture.get() và join() trên cùng một future

Đây là sự khác biệt có tính hệ thống:

| Quan sát | `CompletableFuture.get()` | `CompletableFuture.join()` |
| --- | --- | --- |
| Có chờ nếu chưa xong? | Có | Có |
| Khi hoàn thành thành công | Trả kết quả | Trả kết quả |
| Khi hoàn thành lỗi | Thường ném `ExecutionException` | Thường ném `CompletionException` |
| Kiểu exception báo lỗi computation | Checked | Unchecked |
| Khi cancelled | `CancellationException` | `CancellationException` |
| Nếu caller bị interrupt khi chờ | Có thể ném `InterruptedException` | Không khai báo `InterruptedException` |
| Timeout trực tiếp | `get(timeout, unit)` | Không có `join(timeout)` |
| Cần lấy cause? | `ex.getCause()` | `ex.getCause()` |

Javadoc `CompletableFuture` còn nêu rõ: nếu exceptional completion đang mang một `CompletionException`, các overload `get` có thể ném `ExecutionException` với **cùng cause** nằm trong wrapper ấy, trong khi `join` có thể ném chính `CompletionException`. **Không giả định hai API tạo ra cause chain có cùng số tầng**, kể cả khi underlying failure giống nhau.

## 5. Lấy nguyên nhân gốc an toàn

### 5.1. Unwrap những wrapper đã biết, không tháo mọi lớp tùy tiện

Một helper thường đủ tốt cho hai loại wrapper đang học:

~~~java
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

static Throwable unwrapAsyncFailure(Throwable error) {
    Throwable current = error;

    // Giới hạn để tránh vòng lặp bất thường trong cause chain.
    for (int depth = 0; depth < 32; depth++) {
        boolean asyncWrapper =
                current instanceof ExecutionException
                || current instanceof CompletionException;

        if (!asyncWrapper) {
            break;
        }

        Throwable cause = current.getCause();
        if (cause == null || cause == current) {
            break;
        }
        current = cause;
    }
    return current;
}
~~~

Đây là **mảnh method Java** hợp lệ khi đặt trong một class, không phải chương trình hoàn chỉnh. Hàm chỉ tháo **`ExecutionException` và `CompletionException`**. Nếu gặp `IllegalStateException`, `SQLException`, một lỗi domain hoặc một wrapper có ngữ nghĩa khác, nó **dừng lại** để ứng dụng tự quyết định.

Tại sao không viết luôn `while (t.getCause() != null) t = t.getCause()`?

- Một tầng domain như `PaymentRejectedException` có thể chứa cause kỹ thuật nhưng bản thân nó mới là tín hiệu cần map thành lỗi nghiệp vụ.
- `DataAccessException`, `UndeclaredThrowableException`, reflection wrappers và HTTP client exceptions có hợp đồng riêng.
- Log chỉ lỗi dưới cùng sẽ đánh mất việc lỗi đã đi qua asynchronous boundary và các tầng mapping.
- Một cause chain dị thường có thể khiến loop không an toàn; giới hạn depth và kiểm tra self-cause là biện pháp phòng vệ.

**Đừng xóa wrapper khỏi log**. Có thể dùng unwrapped cause để **phân loại** lỗi, nhưng vẫn ghi full `Throwable` ở ranh giới quan sát:

~~~java
try {
    return future.get(5, TimeUnit.SECONDS);
} catch (ExecutionException ex) {
    Throwable cause = unwrapAsyncFailure(ex);
    logFailure("Async execution failed", ex, cause);
    throw translateToDomainError(cause);
}
~~~

Đây là đoạn minh họa; cần triển khai `logFailure`, `translateToDomainError`, `future` và bắt các exception khác của `get(timeout)`. Không lặp kiểu log nguyên stack trace ở nhiều tầng gây nhiễu; mỗi ranh giới nên có trách nhiệm rõ ràng.

### 5.2. Cause, suppressed exceptions và stack trace

Ba dữ liệu này không đồng nghĩa:

- **Cause (`getCause()`)**: Throwable gắn với nguyên nhân trực tiếp của một wrapper hoặc lỗi ở tầng cao.
- **Suppressed (`getSuppressed()`)**: các lỗi bổ sung, ví dụ lỗi khi đóng tài nguyên trong `try-with-resources`; **không** phải cause chain.
- **Stack trace**: call stacks tại nơi các Throwable được tạo/throw; với async, wrapper và nguyên nhân có thể ghi dấu **hai thread/rành giới khác nhau**.

Không thay full exception bằng `logger.error("{}", ex.getMessage())`, vì message thường thiếu type, cause và stack trace. Tránh logging dữ liệu nhạy cảm trong input, URL có token, request body hoặc chi tiết nội bộ cần được che.

## 6. Ví dụ Java 17 độc lập: so sánh get, join, cause và recovery

Ví dụ này **không cần Spring** để kiểm tra semantic chuẩn của JDK. Nó dùng `CompletableFuture.failedFuture(...)` với một lỗi xác định và `ExecutorService.submit(...)` để thấy cả hai nguồn Future đều được bọc khi dùng `get`.

Tạo đúng tệp **`FutureExceptionDemo.java`**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FutureExceptionDemo {

    public static void main(String[] args) throws Exception {
        IllegalStateException root =
                new IllegalStateException("database unreachable");

        CompletableFuture<String> failed =
                CompletableFuture.failedFuture(root);

        try {
            failed.get();
        } catch (ExecutionException ex) {
            printFailure("get", ex);
        }

        try {
            failed.join();
        } catch (CompletionException ex) {
            printFailure("join", ex);
        }

        Throwable nested = new CompletionException(
                new ExecutionException(root));
        System.out.println("nested root: " +
                unwrap(nested).getMessage());

        String fallback = failed.exceptionally(ex -> "cached").join();
        System.out.println("exceptionally: " + fallback);

        String handled = failed.handle((value, error) ->
                error == null ? value : "recovered").join();
        System.out.println("handle: " + handled);

        CompletableFuture<String> observed =
                failed.whenComplete((value, error) -> {
                    // Quan sát thôi, không thay thế lỗi gốc.
                });
        System.out.println("whenComplete stays failed: " +
                observed.isCompletedExceptionally());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> submitted = pool.submit(() -> {
                throw new IllegalArgumentException("bad request");
            });

            try {
                submitted.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                printFailure("submit", ex);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void printFailure(String label, Throwable error) {
        Throwable root = unwrap(error);
        System.out.println(label + " wrapper: " +
                error.getClass().getSimpleName());
        System.out.println(label + " root: " +
                root.getClass().getSimpleName() +
                ": " + root.getMessage());
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        for (int depth = 0; depth < 32; depth++) {
            if (!(current instanceof ExecutionException)
                    && !(current instanceof CompletionException)) {
                break;
            }

            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return current;
    }
}
~~~

Biên dịch và chạy bằng **JDK 17+**:

~~~bash
javac FutureExceptionDemo.java
java FutureExceptionDemo
~~~

**Stdout chính xác** với điều kiện lệnh thực thi bình thường:

~~~text
get wrapper: ExecutionException
get root: IllegalStateException: database unreachable
join wrapper: CompletionException
join root: IllegalStateException: database unreachable
nested root: database unreachable
exceptionally: cached
handle: recovered
whenComplete stays failed: true
submit wrapper: ExecutionException
submit root: IllegalArgumentException: bad request
~~~

**Giải thích từng quan sát:**

1. `failedFuture(root)` tạo một `CompletableFuture` đã hoàn thành lỗi bằng `IllegalStateException`.
2. `get()` ném **`ExecutionException`**; `join()` ném **`CompletionException`**. Hai wrapper khác nhau nhưng helper đều tìm tới lỗi gốc.
3. `new CompletionException(new ExecutionException(root))` mô phỏng hai tầng bao; helper gỡ đúng hai tầng.
4. `exceptionally(...)` tạo một stage **phục hồi** với giá trị `cached`.
5. `handle(...)` được gọi cho cả kết quả thành công lẫn thất bại; ở đây trả `recovered`.
6. `whenComplete(...)` chỉ quan sát; khi callback không ném lỗi, stage trả về vẫn **exceptional** vì upstream đã thất bại.
7. `ExecutorService.submit` nhận một Callable ném `IllegalArgumentException`. `Future.get` báo `ExecutionException`, nguyên nhân là lỗi từ Callable. Dùng `get(timeout)` ở đây để test không chờ vô hạn nếu môi trường bất thường.
8. `finally` shutdown pool do ví dụ tự tạo, không để thread tồn tại ngoài chương trình.

Kết quả được xây dựng bằng exception type/message và các Future hoàn thành xác định, không dựa vào thứ tự thread hoặc thời gian ngẫu nhiên. Chương trình vẫn có thể bị ảnh hưởng nếu JVM không có tài nguyên để tạo worker hoặc bị interrupt bất thường; stdout trên là hợp đồng của đường chạy thành công.

## 7. Trong Spring @Async: lỗi thực thi đi qua proxy như thế nào?

Với Spring, `@Async` ở chế độ proxy mặc định cần:

1. `@EnableAsync` có hiệu lực trong đúng `ApplicationContext`.
2. Service/worker là **bean do Spring quản lý**.
3. Lời gọi method đi qua **Spring proxy**; self-invocation `this.method()` thường không kích hoạt annotation.
4. Có executor phù hợp được chọn, ví dụ bằng `@Async("ioExecutor")`.
5. Method trả kiểu hợp lệ: `void`, `Future<T>` hoặc `CompletableFuture<T>` theo API/version.

Ví dụ method trong service bean:

~~~java
@Async("ioExecutor")
public CompletableFuture<String> fetch(String key) {
    if (key.isBlank()) {
        throw new IllegalArgumentException("blank key");
    }
    return CompletableFuture.completedFuture(loadFromRemote(key));
}
~~~

Đây là **mảnh method minh họa** cần import `@Async`, `CompletableFuture`, `loadFromRemote` và bean `ioExecutor`. Nếu `loadFromRemote` ném exception trong worker, Future trả về cho caller có thể hoàn thành exceptional. `get()` trên Future ấy thường báo `ExecutionException`; `join()` báo `CompletionException`.

**Đừng nhầm lỗi từ Spring proxy với lỗi gốc.** Wrapper vẫn thuộc JDK Future/CompletionStage semantics; business exception của method `fetch` nằm trong cause chain.

### 7.1. @Async void: không có Future cho caller

~~~java
@Async("ioExecutor")
public void refreshIndex() {
    throw new IllegalStateException("index write failed");
}
~~~

Đây là mảnh minh họa. Khi method trả `void`, caller **không có Future để gọi `get` hoặc `join`**, nên exception chạy trên worker không tự động xuất hiện ở caller. Spring có `AsyncUncaughtExceptionHandler` để xử lý những lỗi loại này. Ví dụ cấu hình bên trong một class `@Configuration`:

~~~java
@EnableAsync
@Configuration
class AsyncErrorConfiguration implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler
            getAsyncUncaughtExceptionHandler() {
        return (error, method, args) ->
                System.err.println("Async void error in " +
                        method.getName() + ": " + error);
    }
}
~~~

Đây là **mảnh cấu hình**, cần import `AsyncConfigurer`, `AsyncUncaughtExceptionHandler`, `@EnableAsync`, `@Configuration` và chính sách logging production. Nếu ứng dụng có nhiều class cấu hình, sắp xếp để không vô tình khai báo nhiều `AsyncConfigurer` xung đột.

Với nhiệm vụ mà caller cần biết **đã thành công hay chưa**, cân nhắc kiểu `CompletableFuture<T>` và/hoặc job status bền vững. `@Async void` không thể truyền exception trực tiếp trở lại caller chỉ bằng `try/catch` bao quanh lời gọi.

### 7.2. Rejection và lỗi trước khi worker chạy

Một pool bão hòa có thể **không nhận** task. Khi handler ném exception, phía submit có thể thấy `RejectedExecutionException` hoặc `TaskRejectedException` ở **thời điểm gửi**, thay vì `ExecutionException` khi lấy kết quả. Nếu dùng policy `DiscardPolicy` hoặc `DiscardOldestPolicy` bỏ công việc âm thầm, Future có thể không bao giờ hoàn thành; **không có wrapper** để unwrap.

Do đó muốn điều tra “`@Async` bị lỗi” phải xác định lỗi xảy ra ở **submission**, **execution**, **completion-stage callback** hay **waiting**. Cả bốn điểm có khả năng báo lỗi khác nhau.

## 8. Demo Spring Framework: get và join trên cùng loại @Async method

Demo độc lập này dùng **Java 17+, Spring Framework 6.1.14 và Gradle 8.5+**. Nó không cần Spring Boot, servlet container hay database. Worker bean `FailureWorker` cố ý ném một `IllegalStateException` trên executor Spring; caller đọc bằng `get(timeout)`, `join()` và tạo một nhánh recovery.

### 8.1. Cấu trúc tệp

~~~text
spring-async-exception-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/AsyncRootCauseDemo.java
~~~

**settings.gradle**:

~~~groovy
rootProject.name = 'spring-async-exception-demo'
~~~

**build.gradle**:

~~~groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation 'org.springframework:spring-context:6.1.14'
}

application {
    mainClass = 'demo.AsyncRootCauseDemo'
}
~~~

**src/main/java/demo/AsyncRootCauseDemo.java**:

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class AsyncRootCauseDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            FailureWorker worker = context.getBean(FailureWorker.class);

            // Gửi cả hai job trước; caller quan sát lỗi sau.
            CompletableFuture<String> a = worker.load("A");
            CompletableFuture<String> b = worker.load("B");

            try {
                a.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                printFailure("get", ex);
            }

            try {
                b.join();
            } catch (CompletionException ex) {
                printFailure("join", ex);
            }

            CompletableFuture<String> recovered =
                    worker.load("C")
                            .exceptionally(error -> "cached-C");
            System.out.println("fallback: " +
                    recovered.get(5, TimeUnit.SECONDS));
        }
    }

    private static void printFailure(String label, Throwable error) {
        Throwable cause = error;
        for (int depth = 0; depth < 32; depth++) {
            if (!(cause instanceof ExecutionException)
                    && !(cause instanceof CompletionException)) {
                break;
            }
            if (cause.getCause() == null ||
                    cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }

        System.out.println(label + " wrapper: " +
                error.getClass().getSimpleName());
        System.out.println(label + " root: " +
                cause.getClass().getSimpleName() +
                ": " + cause.getMessage());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {
        @Bean("ioExecutor")
        public ThreadPoolTaskExecutor ioExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(10);
            executor.setThreadNamePrefix("io-");
            return executor;
        }

        @Bean
        public FailureWorker failureWorker() {
            return new FailureWorker();
        }
    }

    public static class FailureWorker {
        @Async("ioExecutor")
        public CompletableFuture<String> load(String key) {
            throw new IllegalStateException("load failed: " + key);
        }
    }
}
~~~

Tại thư mục project, chạy:

~~~bash
gradle run
~~~

Nếu có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

**Các dòng do chương trình tự in** khi executor khởi động và các Future hoàn thành trong thời gian chờ:

~~~text
get wrapper: ExecutionException
get root: IllegalStateException: load failed: A
join wrapper: CompletionException
join root: IllegalStateException: load failed: B
fallback: cached-C
~~~

Spring hoặc Gradle có thể in log khởi động bên ngoài năm dòng trên. Tên thread/độ trễ tùy runtime, nhưng code không in tên worker nên expected stdout không phụ thuộc scheduling.

**Giải thích:** `@EnableAsync` bật hạ tầng async; `FailureWorker` được Spring tạo qua `@Bean` và proxy có thể intercept public method `load`. Caller lấy worker qua context, không tự `new` ở bên ngoài. Khi method ném `IllegalStateException` trên thread executor, Future trả về từ proxy lưu lỗi; `get` và `join` bộc lộ wrapper khác nhau. `exceptionally` xử lý trạng thái exceptional để đưa giá trị fallback. Đó là **quyết định ở tầng caller**, không phải Spring tự chữa lỗi gốc.

## 9. Xử lý lỗi trong chuỗi CompletableFuture

### 9.1. exceptionally: chỉ phục hồi ở nhánh lỗi

~~~java
CompletableFuture<String> safe =
        worker.load("A").exceptionally(error -> {
            Throwable root = unwrapAsyncFailure(error);
            recordFailure(root);
            return "cached-value";
        });
~~~

Đây là mảnh code cần hàm `unwrapAsyncFailure`, `recordFailure` và bean `worker`. Nếu upstream thành công, callback `exceptionally` không được gọi; nếu thất bại, callback chọn fallback. Chọn fallback **không đồng nghĩa dữ liệu gốc đã được xử lý thành công** — cần định nghĩa rõ semantic và trạng thái job.

### 9.2. handle: xử lý cả thành công lẫn thất bại

~~~java
CompletableFuture<Result> result = worker.load("A")
        .handle((value, error) -> {
            if (error != null) {
                return Result.failed(unwrapAsyncFailure(error));
            }
            return Result.success(value);
        });
~~~

Mảnh code này cần định nghĩa kiểu `Result` và helper. `handle` được chạy sau khi stage trước hoàn tất, cho phép chuyển **cả success và failure** thành một kiểu kết quả miền ứng dụng. Nhưng nếu callback `handle` tự ném exception, stage mới vẫn có thể hoàn thành exceptional.

### 9.3. whenComplete: quan sát, không tự chuyển lỗi thành thành công

~~~java
CompletableFuture<String> traced = worker.load("A")
        .whenComplete((value, error) -> {
            if (error != null) {
                logFailure(unwrapAsyncFailure(error));
            }
        });
~~~

Đoạn trên không tự hồi phục lỗi. Khi stage gốc đã thất bại, stage `traced` vẫn hoàn thành lỗi nếu callback chỉ ghi log. Không dùng `whenComplete` như thể nó tương đương `exceptionally`.

### 9.4. thenApply/thenCompose: lỗi lan truyền qua phụ thuộc

~~~java
CompletableFuture<String> output = worker.load("A")
        .thenApply(String::trim)
        .thenApply(String::toUpperCase);
~~~

Nếu `load("A")` thất bại, các callback biến đổi thông thường **không chạy**; future `output` cũng có thể kết thúc exceptional. Không cần từng callback có `try/catch` riêng nếu bạn đã có một ranh giới xử lý lỗi chung phù hợp.

Với `thenCompose`, callback trả Future khác; nếu stage trước thất bại, callback thường không được gọi, lỗi đi qua downstream. Các lỗi của Future do callback trả ra cũng trở thành lỗi của stage kết hợp. Điều này có thể tạo thêm tầng `CompletionException` khi caller `join`.

### 9.5. allOf và nhiều lỗi cùng lúc

~~~java
CompletableFuture<Void> all =
        CompletableFuture.allOf(taskA, taskB, taskC);
~~~

Đây là mảnh code, các Future cần được tạo trước. Khi một hoặc nhiều nhánh hoàn thành lỗi, `all` có thể hoàn thành exceptional; **không xem một cause duy nhất là danh sách toàn bộ lỗi của ba nhánh**. Nếu nghiệp vụ cần báo cáo lỗi từng task, sau khi các task hoàn tất hãy kiểm tra từng Future có kiểm soát, hoặc chuyển từng nhánh thành một `Result` chứa success/failure trước khi tổng hợp.

Đừng dùng `CompletableFuture.allOf(...).join()` ngay giữa dòng thực thi mà không biết các nhánh đã được submit và xử lý thế nào. Nếu cần API bất đồng bộ xuyên tầng, có thể trả future tổng hợp thay vì block caller.

## 10. Timeout, cancellation và interruption không phải ExecutionException

Một ranh giới xử lý lỗi tốt cần phân biệt:

| Tín hiệu | Thường đến từ đâu? | Hành động phù hợp |
| --- | --- | --- |
| `ExecutionException` | `Future.get()` sau khi computation thất bại | Xem cause; map/retry/log theo nguyên nhân |
| `CompletionException` | `CompletableFuture.join()` hoặc stage exception | Xem cause; đừng xem wrapper là lỗi domain |
| `TimeoutException` | `get(timeout)` hoặc nguồn timeout | Phân biệt timeout **chờ** với task thất bại/đã dừng |
| `InterruptedException` | Caller bị interrupt khi đợi trong `get` | Tôn trọng cancellation/shutdown, thường khôi phục interrupt flag |
| `CancellationException` | Future đã bị hủy | Xử lý trạng thái canceled; không coi là lỗi computation thông thường |
| `RejectedExecutionException`/`TaskRejectedException` | Executor từ chối ở submission | Backpressure, phản hồi quá tải, retry có giới hạn hoặc durable queue |

### 10.1. Xử lý get(timeout) có chủ đích

~~~java
try {
    return future.get(5, TimeUnit.SECONDS);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new OperationCancelledException(ex);
} catch (TimeoutException ex) {
    throw new DownstreamTimeoutException(ex);
} catch (CancellationException ex) {
    throw new OperationCancelledException(ex);
} catch (ExecutionException ex) {
    Throwable failure = unwrapAsyncFailure(ex);
    throw mapToApplicationException(failure);
}
~~~

Đây là **đoạn code minh họa**; `future`, các exception miền ứng dụng và hàm mapping chưa được định nghĩa. Thứ tự catch trên chỉ minh họa chính sách. Khi bắt `InterruptedException`, việc khôi phục interrupt flag thường thích hợp nếu exception không được truyền ra theo contract, để vòng đời thread có cơ hội phản ứng đúng.

**Timeout khi đợi không tự hủy công việc.** `future.cancel(true)` cũng chỉ yêu cầu/cố gắng hủy theo contract/implementation; không thể đảm bảo mọi I/O hoặc external side effect dừng ngay. Với `CompletableFuture`, cancellation là một dạng exceptional completion và không tự điều khiển tất cả tác vụ nguồn đã gửi ở nơi khác.

### 10.2. Có thể dùng orTimeout/completeOnTimeout, nhưng hiểu hậu quả

~~~java
CompletableFuture<String> timed =
        worker.load("A").orTimeout(3, TimeUnit.SECONDS);
~~~

`orTimeout` có thể hoàn thành **exceptional** CompletableFuture nếu chưa xong đúng hạn; `completeOnTimeout` có thể hoàn thành bằng giá trị mặc định. Những API này không bảo đảm computation đang chạy bị interrupt hay database operation tự rollback. Nếu stage `timed` đã thất bại do timeout, `get` và `join` tiếp tục có thể biểu hiện thông qua wrapper, bên trong là `TimeoutException`.

Nếu sử dụng timeout cho cả HTTP request và worker, cần quy định rõ trạng thái nào là timeout của caller, trạng thái nào là timeout của downstream, trạng thái nào là kết quả cuối của job. Không dùng message exception như một mã trạng thái duy nhất.

## 11. Ánh xạ nguyên nhân gốc thành hành vi nghiệp vụ

Khi đã xác định cause thuộc loại gì, tầng service/controller có thể quyết định:

~~~text
Async wrapper
    |
    v
Unwrap ExecutionException / CompletionException
    |
    +-- Validation/domain exception ---> trả lỗi input hoặc trạng thái domain
    |
    +-- Timeout/temporary network -----> retry có giới hạn hoặc 503/504
    |
    +-- Cancellation ------------------> trạng thái cancelled, không retry tùy tiện
    |
    +-- Data integrity/permanent ------> báo lỗi ổn định, không retry mù quáng
    |
    +-- Unknown -----------------------> log đầy đủ, phản hồi lỗi an toàn
~~~

Đây là ví dụ **quy tắc xử lý mang tính thiết kế**, không phải bảng mapping HTTP bắt buộc cho mọi API. Tầng giao thức phải phân biệt exception nội bộ với thông tin được phép trả cho client. Không gửi stack trace, SQL, access token hoặc thông tin bí mật vào HTTP response.

**Không retry mọi `ExecutionException`.** Wrapper không cho biết lỗi có tạm thời hay không. Retry cần xét **nguyên nhân gốc**, tính **idempotent** của thao tác, retry budget, exponential backoff/jitter và giới hạn tải. Một lỗi nghiệp vụ như tham số sai sẽ không tự hết nếu thử lại y nguyên. Một tác vụ đã thực hiện side effect trước khi lỗi có thể tạo duplicate nếu retry không có idempotency key.

Tương tự, không nuốt hết bằng `exceptionally(e -> null)` nếu `null` làm tầng trên hiểu là thành công. Fallback phải có nghĩa rõ ràng (ví dụ cached read hợp lệ) hoặc chuyển thành kiểu `Result.failed(...)`.

## 12. Quan sát lỗi: log, tracing và tính nhất quán

Một lỗi qua async boundary có thể mang:

- **Thread worker**: nơi phát sinh `IllegalStateException`, `SQLException`, v.v.
- **Thread caller**: nơi gọi `get`/`join` và thấy wrapper.
- **Trace/correlation ID**: giúp nối hai hoạt động thuộc một request/job.
- **Job ID**: xác định tác vụ riêng, thời điểm submit, bắt đầu, hoàn thành hoặc thất bại.
- **Executor metadata**: tên pool, queue size, rejection count, active threads và latency.

**Ghi log đầy đủ một lần tại ranh giới chịu trách nhiệm**, ví dụ logger nhận nguyên `Throwable`, thay vì chỉ message. Tùy chiến lược quan sát, có thể bổ sung **unwrapped cause type** vào trường phân loại nhưng giữ full cause chain để điều tra.

Mã giả minh họa:

~~~java
Throwable cause = unwrapAsyncFailure(error);
logger.error("async_job_failed jobId={} causeType={}",
        jobId, cause.getClass().getSimpleName(), error);
~~~

Cần định nghĩa `logger`, `jobId` và helper. Nếu dùng SLF4J, đối số Throwable cuối thường được logger hiểu là stack trace; cách log chi tiết phụ thuộc binding/version. Không log dữ liệu nhạy cảm hoặc input nguyên văn không cần thiết.

**MDC và ThreadLocal không tự truyền qua `@Async`** vì worker có thread khác. Nếu muốn trace/correlation ID trên worker, cần chủ động truyền trong DTO hoặc cấu hình context propagation/`TaskDecorator` phù hợp, đồng thời cleanup để tránh lẫn dữ liệu giữa những task dùng chung thread pool.

### 12.1. Đo trạng thái trước khi quyết định nguyên nhân

Không nên đoán “Future treo vì exception”: có thể task còn chờ trong queue, đang chạy chậm, bị deadlock, hoặc bị handler từ chối/bỏ âm thầm. Một Future chưa hoàn thành **không hề có `ExecutionException` để bóc**. Cần kiểm tra pool saturation, submitted/started/completed/rejected counters, task age, timeout và thread dump khi phù hợp.

Nếu `@Async` không thực sự chạy qua Spring proxy (ví dụ self-invocation hoặc object tự tạo bằng `new`), lỗi có thể ném **đồng bộ ngay tại caller**. Không được suy diễn mọi exception của method có annotation đều đi qua `CompletableFuture`.

## 13. Các anti-pattern phổ biến và cách sửa

| Anti-pattern | Vấn đề | Cách làm đúng |
| --- | --- | --- |
| Chỉ log `ex.getMessage()` của `ExecutionException` | Mất nguyên nhân và stack trace | Log Throwable đầy đủ, đọc `getCause()` khi phân loại |
| `catch (Exception e) { return null; }` | Che lỗi, caller tưởng thành công | Trả fallback hợp lệ hoặc stage failed, log/map rõ |
| `(IOException) ex.getCause()` không kiểm tra kiểu | `ClassCastException` hoặc nhầm wrapper | Unwrap wrapper đã biết, kiểm tra `instanceof` trước khi map |
| `join()` được cho là non-blocking | Vẫn có thể chặn thread | Dùng continuation, chỉ join ở ranh giới cần kết quả |
| Retry mọi `CompletionException` | Không xét nguyên nhân, có thể tạo side effect trùng | Retry theo cause + idempotency + budget |
| Mong `@Async void` ném lỗi về caller | Caller không có Future kết quả | Dùng Future nếu cần giám sát, hoặc AsyncUncaughtExceptionHandler |
| Coi `TimeoutException` từ get là lỗi của worker | Timeout chỉ là giới hạn **chờ** | Theo dõi trạng thái task và hủy/thử lại đúng hợp đồng |
| Nuốt `InterruptedException` rồi tiếp tục | Phá cancellation/shutdown semantics | Khôi phục interrupt flag hoặc propagate phù hợp |
| Chỉ xem cause của `allOf` là toàn bộ lỗi | Có thể bỏ sót các nhánh hỏng khác | Tổng hợp kết quả/lỗi từng nhánh |
| Bỏ Future mà không attach observer | Lỗi nền không ai xử lý | Lưu handle, callback, metrics/job status |
| Dùng `DiscardPolicy` cho task quan trọng | Task mất không báo lỗi, Future có thể pending | Rejection rõ ràng, backpressure hoặc durable queue |
| Gọi `future.get()` trong worker pool một thread và child cần cùng pool | Có thể starvation deadlock | Dùng continuation hoặc thay dependency/pool |

## 14. Checklist kiểm thử và code review

### 14.1. Test tối thiểu

1. **Thành công**: `get`/`join` trả giá trị dự kiến.
2. **Thất bại từ worker**: `get` bắt `ExecutionException`, cause giữ exception mong đợi.
3. **Thất bại qua CompletableFuture**: `join` ném `CompletionException`, cause được unwrap đúng.
4. **Nhiều wrapper**: `CompletionException(new ExecutionException(root))` được helper tháo đúng nhưng không mất lỗi domain.
5. **Fallback**: `exceptionally`/ `handle` trả giá trị theo hợp đồng, `whenComplete` không tự thay thế failure.
6. **Một nhánh thất bại trong allOf**: downstream xử lý exceptional completion, kiểm tra trạng thái từng nhánh khi cần.
7. **Cancellation**: kiểm tra `CancellationException` thay vì `ExecutionException`.
8. **Timeout chờ**: `get(timeout)` ném `TimeoutException` và không mặc định dừng task.
9. **Interruption**: caller bị interrupt có xử lý flag và vòng đời phù hợp.
10. **Spring context**: `@EnableAsync`, worker bean, qualifier và proxy hoạt động; kiểm tra lỗi qua Future từ một bean khác.
11. **@Async void**: handler ghi nhận lỗi, không dựa vào caller catch.
12. **Pool quá tải**: task rejection được quan sát; không nhầm rejection với computation failure.

### 14.2. Review trước production

- [ ] Phân biệt `ExecutionException` và `CompletionException` theo API `get`/`join`.
- [ ] Không unwrap bừa mọi tầng Throwable; giữ full exception chain trong log.
- [ ] Catch riêng `InterruptedException`, `TimeoutException`, `CancellationException` và rejection khi cần.
- [ ] `@Async` được gọi qua bean/proxy của đúng ApplicationContext.
- [ ] Chọn executor bằng qualifier khi có nhiều pool.
- [ ] Không gọi `get()`/`join()` sớm nếu mục tiêu là chồng lấp các tác vụ độc lập.
- [ ] Có boundary xử lý lỗi: fallback, mapping, retry hoặc error result có ngữ nghĩa.
- [ ] Có timeout và chính sách cancellation/cleanup phù hợp.
- [ ] Có metrics cho exception type gốc, queue saturation, rejection và job trạng thái lâu.
- [ ] Với tác vụ bắt buộc, có durability qua job store/outbox/broker thay vì chỉ Future trong memory.

## 15. Câu hỏi tự kiểm tra

1. **`Future.get()` báo `ExecutionException` có chứng minh executor bị lỗi không?** Không. Nó thường là wrapper của lỗi ném trong computation; đọc `getCause()`.
2. **`CompletableFuture.join()` có non-blocking vì ném unchecked exception không?** Không. Nó vẫn có thể chờ kết quả.
3. **Với cùng một `CompletableFuture.failedFuture(root)`, `get` và `join` thường ném gì?** `get` ném `ExecutionException`; `join` ném `CompletionException`, đều liên quan đến root.
4. **`get(timeout)` bị timeout thì worker đã thất bại chưa?** Không nhất thiết. Đây là timeout khi caller đợi; task có thể vẫn chạy.
5. **Future bị cancel có ném `ExecutionException` không?** Thông thường báo `CancellationException`.
6. **Lấy `ex.getCause()` một lần có luôn ra lỗi gốc không?** Không. Có thể còn nhiều wrapper hoặc một tầng domain có ý nghĩa.
7. **Vì sao không nên unwrap mọi `getCause()` tới tận cùng?** Có thể làm mất ngữ nghĩa của exception tầng ứng dụng và bối cảnh truyền lỗi.
8. **`exceptionally` khác `whenComplete` ra sao?** `exceptionally` có thể phục hồi bằng giá trị; `whenComplete` chủ yếu quan sát và không tự biến failure thành success.
9. **`handle` có chạy cả lúc thành công và lỗi không?** Có; callback nhận value/error phù hợp.
10. **`CompletableFuture.allOf` thất bại có cho biết mọi lỗi của mọi task không?** Không; muốn báo cáo tất cả phải ghi nhận từng nhánh.
11. **Với `@Async void`, caller lấy lỗi qua `Future.get` được không?** Không có Future để gọi; cần handler/log/monitoring khác.
12. **Nếu method `@Async` bị tự gọi bằng `this.method()` thì exception có đi qua Future proxy không?** Thường không, vì self-invocation bỏ qua proxy ở mode mặc định.
13. **Tại sao log cả wrapper và cause?** Để biết cả ranh giới async và nguyên nhân kỹ thuật/nghiệp vụ.
14. **Một `CompletionException` có nghĩa nên retry luôn?** Không. Quyết định retry dựa trên nguyên nhân, idempotency và retry budget.

**Ghi nhớ:** `ExecutionException` và `CompletionException` thường là **vỏ bọc của lỗi bất đồng bộ**, không phải nguyên nhân gốc. Biết `get()` và `join()` báo lỗi thế nào, unwrap có kiểm soát, giữ full stack trace và phân biệt timeout/cancellation/rejection giúp xử lý đúng nghiệp vụ trong Java và Spring `@Async`.

### Tài liệu chính thức

- [Java 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java 17 — ExecutionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html)
- [Java 17 — CompletionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — @Async](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework — AsyncUncaughtExceptionHandler](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/aop/interceptor/AsyncUncaughtExceptionHandler.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-003">AsyncUncaughtExceptionHandler cho phương thức @Async trả về void</a>

<details>
<summary>Xem chi tiết</summary>

# AsyncUncaughtExceptionHandler cho phương thức @Async trả về void

> **Module:** Spring Framework – concurrency  
> **Chủ đề cha:** Xử lý ngoại lệ  
> **Mục tiêu:** Hiểu vì sao exception phát sinh trong một phương thức `@Async` trả về `void` không quay lại caller theo cách thông thường; biết cấu hình `AsyncUncaughtExceptionHandler`, quan sát thông tin `Throwable`/`Method`/`params`, phân biệt với `Future`/`CompletableFuture` và xử lý lỗi nộp task, đồng thời thiết kế logging/metrics đáng tin cậy.

## 1. Vấn đề nền tảng: @Async void không có Future để trả lỗi

Xét một service Spring:

~~~java
@Service
public class NotificationService {
    @Async
    public void sendNotification(String orderId) {
        throw new IllegalStateException("notification service unavailable");
    }
}
~~~

Đây là **đoạn tích hợp Spring**, cần import `@Service` và `@Async`, bật async processing và gọi qua Spring proxy; chưa phải chương trình độc lập. Khi lời gọi thực sự đi qua advice của `@Async` và task được executor chấp nhận, luồng caller thường nhận quyền điều khiển **trước khi** thân phương thức hoàn tất. Nếu thân phương thức ném `IllegalStateException` trong worker, caller không thể viết một `try/catch` ngay quanh lệnh `sendNotification(...)` để bắt exception đó như một lệnh gọi đồng bộ.

Không có `Future` trả về từ một phương thức `void`; do đó caller **không có handle kết quả** để gọi `get()`, `join()` hay kiểm tra trạng thái lỗi của task. Spring cung cấp `org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler` để xử lý những exception **không được bắt trong thân phương thức `@Async void`**.

Ý tưởng chính:

- **Caller:** gửi yêu cầu chạy một phương thức async, không nhận giá trị kết quả qua signature `void`.
- **Executor:** nhận và chạy công việc sau đó, trên một thread phù hợp với chính sách scheduling.
- **Worker:** khi công việc ném `Throwable` chưa xử lý, async interceptor đưa lỗi đến `AsyncUncaughtExceptionHandler`.
- **Handler:** nhận error, method và arguments; ghi nhận, cảnh báo hoặc phát tín hiệu quan sát. Handler **không biến `void` thành `Future`** và không tự gửi exception về caller.

Cơ chế này chỉ áp dụng cho lời gọi thực sự được interception. Nếu `@Async` bị bỏ qua (chẳng hạn self-invocation trong chế độ proxy mặc định), phương thức có thể chạy đồng bộ và exception lại đi theo stack thông thường.

## 2. Luồng thực thi và hai thời điểm có thể thất bại

~~~text
Caller thread
   |
   | gọi notificationService.sendNotification("ref-42")
   v
Spring @Async proxy / interceptor
   |
   +-- lỗi khi nộp task / executor shutdown hoặc reject?
   |       |
   |       +--> caller có thể nhận TaskRejectedException ngay tại lời gọi
   |
   +-- executor CHẤP NHẬN
           |
           +--> lời gọi void của caller kết thúc (thường chưa có kết quả)
           |
           v
       Thread thực thi task
           |
           +-- sendNotification chạy thành công --> hoàn tất
           |
           +-- sendNotification ném exception chưa bắt
                    |
                    v
             AsyncUncaughtExceptionHandler
               handleUncaughtException(error, method, params)
                    |
                    +--> log / metric / cảnh báo / báo lỗi có kiểm soát
                    +--> KHÔNG tự truyền Throwable về caller
~~~

**Hai đường lỗi phải được phân biệt:**

1. **Lỗi nộp task:** `TaskRejectedException` của Spring (hoặc rejection exception phù hợp theo executor) xảy ra **khi proxy cố nộp** vào executor đã đóng/bão hòa. Đây là lỗi của quá trình lên lịch, có thể quan sát ngay ở caller. Nó **không phải** exception được ném bởi thân phương thức async và không được bảo đảm đi qua `AsyncUncaughtExceptionHandler`.
2. **Lỗi khi task đã chạy:** exception được ném từ thân phương thức `@Async void` trong giai đoạn thực thi. Spring dùng handler để xử lý, bởi không có `Future` cho caller quan sát.

Nếu executor sử dụng chính sách đặc biệt như `CallerRunsPolicy` lúc quá tải, task có thể chạy trên caller thread; đừng suy luận vị trí thread chỉ từ tên `@Async`. Tuy nhiên đường xử lý exception của proxy cho phương thức `void` vẫn cần hiểu theo hợp đồng handler.

## 3. AsyncUncaughtExceptionHandler là interface gì?

Interface nằm ở package:

~~~java
org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
~~~

Chữ ký quan trọng (viết rút gọn để học, không phải một class Java độc lập):

~~~java
void handleUncaughtException(
    Throwable exception,
    java.lang.reflect.Method method,
    Object... params);
~~~

Ý nghĩa các tham số:

| Tham số | Đại diện cho | Lưu ý |
| --- | --- | --- |
| `Throwable exception` | Lỗi không được xử lý từ phương thức `@Async void` | Có thể là `RuntimeException`, hoặc `Error`; không mặc định chuyển thành `ExecutionException` |
| `Method method` | Reflection metadata về phương thức async được gọi | Có thể lấy `getName()`, `getDeclaringClass()`; không nên giả định Method luôn thuộc đúng class proxy cụ thể |
| `Object... params` | Các đối số của lời gọi gốc, theo thông tin Spring interceptor chuyển vào | Có thể chứa dữ liệu nhạy cảm, object mutable, collection lớn hoặc `toString()` nguy hiểm |

Một handler thường cần trả lời ba câu hỏi: **phương thức nào lỗi? lỗi gì? cần báo động/ghi nhận thế nào?** Đó không đồng nghĩa với “retry ngay” hoặc “đảo ngược mọi tác dụng phụ đã xảy ra”.

Ví dụ handler gọn:

~~~java
AsyncUncaughtExceptionHandler handler = (error, method, args) -> {
    logger.error("Async void method {} failed", method.getName(), error);
    failedTasksCounter.increment();
};
~~~

Đây là **snippet tích hợp**, yêu cầu ứng dụng đã có `logger` và `failedTasksCounter`. Nó không in toàn bộ `args`, vì argument có thể chứa token, email, thông tin cá nhân, dữ liệu giao dịch hoặc đối tượng có khả năng sinh log rất lớn.

## 4. Handler mặc định là gì nếu không cấu hình?

Khi xử lý phương thức `@Async` trả `void` mà ứng dụng không cung cấp exception handler riêng, Spring Framework dùng cơ chế mặc định với `SimpleAsyncUncaughtExceptionHandler`. Handler mặc định **ghi log lỗi** của phương thức async; nó không tự retry, không tự báo lỗi về HTTP caller và không đưa kết quả vào một future.

Điều này có một hệ quả sản phẩm quan trọng: **có log lỗi không đồng nghĩa hệ thống đã xử lý lỗi nghiệp vụ**. Nếu công việc là gửi email, tạo invoice hay đồng bộ hệ thống khác, chỉ logging chưa thể cho biết task có cần chạy lại hay không, đã phát sinh side effect nào và có cần cảnh báo vận hành không.

Nếu log aggregation không thu thập đúng logger/severity, lỗi của `@Async void` có thể bị bỏ qua trong quá trình vận hành. Vì thế handler chuyên dụng nên phát ra metric và correlation ID an toàn, đồng thời có runbook giải quyết task lỗi.

## 5. Cấu hình handler qua AsyncConfigurer

Cách chính thống với annotation-based async trong Spring là cung cấp `org.springframework.scheduling.annotation.AsyncConfigurer` trong cấu hình có `@EnableAsync`, rồi override `getAsyncUncaughtExceptionHandler()`.

~~~java
@Configuration
@EnableAsync
public class AsyncConfiguration implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (error, method, args) -> {
            // Ghi nhận lỗi của @Async void tại đây.
        };
    }
}
~~~

Đây là **mã minh họa tích hợp**; phải import các kiểu và để Spring nạp configuration. Một `AsyncConfigurer` đủ để cấu hình default exception handler cho việc xử lý `@Async` trong context tương ứng. Tránh đăng ký nhiều `AsyncConfigurer` cạnh tranh trong cùng context; cần một cấu hình mặc định xác định được.

`AsyncConfigurer` có hai thành phần cần phân biệt:

- `getAsyncExecutor()`: **tuỳ chọn** cung cấp executor mặc định cho `@Async`; khi không cung cấp, Spring thực hiện cơ chế chọn executor theo type/name và fallback tương ứng.
- `getAsyncUncaughtExceptionHandler()`: **tuỳ chọn** cung cấp handler cho exception không được bắt trong các method `@Async` trả `void`.

Không nhất thiết phải override cả hai. Ví dụ đầy đủ ở mục 8 **chỉ override handler** nhưng vẫn khai báo một `ThreadPoolTaskExecutor` làm bean cho Spring chọn.

**Lưu ý lifecycle:** class triển khai `AsyncConfigurer` được khởi tạo tương đối sớm trong quá trình cấu hình async. Nếu handler cần dependency phức tạp, tránh kích hoạt sớm bean chưa hoàn chỉnh; dùng cấu hình rõ ràng/lazy lookup khi thích hợp. Chỉ khai báo một bean tùy ý có type `AsyncUncaughtExceptionHandler` không đồng nghĩa mọi interceptor tự động dùng bean đó; đường cấu hình được đề cập ở đây là `AsyncConfigurer`.

## 6. Khi nào handler được gọi, và khi nào không?

| Tình huống | AsyncUncaughtExceptionHandler có phải đường xử lý chính? | Vì sao |
| --- | --- | --- |
| `@Async public void run()` ném exception chưa bắt khi thực thi | **Có** | Caller không có Future để lấy lỗi |
| `@Async public void run()` tự `try/catch` và không ném lại | **Không** | Không còn exception chưa được xử lý |
| `@Async public Future<T> run()` bị lỗi khi task chạy | **Không** | Lỗi được biểu diễn qua Future kết quả |
| `@Async public CompletableFuture<T> run()` bị lỗi khi task chạy | **Không** | Lỗi được biểu diễn qua CompletableFuture kết quả |
| `@Async public CompletableFuture<Void> run()` ném exception | **Không** | Dù không có giá trị hữu ích, method vẫn có Future để quan sát |
| `@Async void` bị từ chối lúc submit | **Không đảm bảo** | Lỗi có thể ném từ proxy ngay tại lời gọi của caller |
| `@Async` bị bỏ qua bởi self-invocation | **Không** | Không đi qua interceptor để gửi đến handler |
| `@Async void` khởi động future riêng, future ấy lỗi về sau | **Không mặc định** | Lỗi xảy ra ở một job detached; method void có thể đã kết thúc thành công |
| Code chạy bằng `executor.execute(() -> ...)` trực tiếp, không `@Async` | **Không mặc định** | Nó không đi qua `@Async` interceptor của Spring |

Bảng này cũng giải thích vì sao viết **`@Async void` cho task cần được caller theo dõi là lựa chọn thiếu thông tin**. Nếu caller cần biết task đã hoàn tất, thành công hay thất bại, nên cân nhắc trả `CompletableFuture<Void>` thay vì `void`.

## 7. Đối chiếu: @Async void với @Async CompletableFuture<Void>

Ví dụ hai API cùng thực hiện một công việc không có giá trị trả về:

~~~java
@Async
public void sendFireAndForget(String id) {
    sendToExternalService(id);
}

@Async
public CompletableFuture<Void> sendTracked(String id) {
    sendToExternalService(id);
    return CompletableFuture.completedFuture(null);
}
~~~

Đây là **snippet tích hợp Spring** với `sendToExternalService` là hàm nghiệp vụ giả định; không phải tệp Java độc lập.

- Với `sendFireAndForget`, exception chưa bắt ở worker đi đến `AsyncUncaughtExceptionHandler`. Caller không thể await bằng API đã khai báo.
- Với `sendTracked`, Spring trả một `CompletableFuture<Void>` cho caller. Caller có thể gọi `whenComplete`/`exceptionally` hoặc `get()`/`join()` để quan sát kết quả. Nếu có exception trong task, caller nhìn thấy exceptional completion; handler dành cho void **không được dùng** cho lỗi này.
- `CompletableFuture<Void>` không có payload nghiệp vụ, nhưng vẫn có **trạng thái completion**. Không nhầm “Void” generic type với “void” return type.
- Nếu một phương thức `@Async` tự gọi một API async khác và trả `CompletableFuture` đã hoàn tất trước khi API con thực sự hoàn thành, caller có thể nhận tín hiệu thành công quá sớm. Contract phải đại diện đúng vòng đời toàn bộ công việc mà bạn muốn theo dõi.

Một lỗi thường gặp là chuyển method từ `void` sang `CompletableFuture<Void>` nhưng không cập nhật nơi quan sát future. Khi đó exception vẫn có thể bị bỏ sót, chỉ là chuyển từ handler sang một future không ai kiểm tra.

## 8. Ví dụ Spring Framework 6.2.9 + Java 17 chạy được

Bài thực hành tạo `@Async void` service ném `IllegalStateException`; một `AsyncConfigurer` đưa lỗi tới handler tùy chỉnh. `CountDownLatch` cho phép CLI đợi handler hoàn tất, nhờ đó **thứ tự stdout xác định**, không cần đoán lúc thread chạy.

Cấu trúc project:

~~~text
spring-void-handler-demo/
├── pom.xml
└── src/main/java/demo/VoidAsyncHandlerDemo.java
~~~

**pom.xml**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-void-handler-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.9</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/demo/VoidAsyncHandlerDemo.java**:

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class VoidAsyncHandlerDemo implements AsyncConfigurer {
    private static final CountDownLatch HANDLED = new CountDownLatch(1);

    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(4);
        pool.setThreadNamePrefix("demo-async-");
        return pool;
    }

    @Bean
    public NoticeService noticeService() {
        return new NoticeService();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (error, method, args) -> {
            try {
                System.out.println("Method: " + method.getName());
                System.out.println("Error: " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
                System.out.println("Arguments: " + args.length);
            } finally {
                HANDLED.countDown();
            }
        };
    }

    public static class NoticeService {
        @Async
        public void sendNotice(String reference) {
            throw new IllegalStateException("delivery failed");
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(VoidAsyncHandlerDemo.class)) {
            context.getBean(NoticeService.class).sendNotice("ref-42");
            if (!HANDLED.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Handler was not called");
            }
            System.out.println("Caller: no task exception propagated");
        }
    }
}
~~~

Chạy từ thư mục `spring-void-handler-demo` (JDK 17+, Maven):

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.VoidAsyncHandlerDemo
~~~

**Các dòng stdout của chương trình**:

~~~text
Method: sendNotice
Error: IllegalStateException: delivery failed
Arguments: 1
Caller: no task exception propagated
~~~

Tác vụ được Spring lên lịch trên executor với tên `taskExecutor`; nó ném exception và handler nhận `Method`, `Throwable` cùng mảng một đối số. `HANDLED.countDown()` chạy trong `finally` của handler, bảo đảm `main` chỉ in dòng cuối sau khi handler đã in ba dòng trước. Đây là demo có đầu ra xác định trong điều kiện executor nhận task và handler chạy trong hạn năm giây; nếu quá chậm, chương trình chủ động báo timeout.

Ví dụ đã được **biên dịch với mục tiêu Java 17 và thực thi thành công** trên một JDK mới hơn; stdout thực tế khớp bốn dòng trên. Lệnh Maven vẫn có thể in các thông báo môi trường khác từ dependency/plugin, nhưng đó không phải bốn dòng `System.out.println` của chương trình.

**Tại sao bài không override `getAsyncExecutor()`?** `AsyncConfigurer` cho phép chỉ cung cấp handler. Spring vẫn giải quyết default executor từ bean `ThreadPoolTaskExecutor` duy nhất (đồng thời được đặt tên `taskExecutor`). Đây chính là cách cấu hình riêng xử lý lỗi mà không phải tự tạo thêm một executor khác.

**Điểm giới hạn:** ví dụ cố ý ném lỗi; không có gửi thông báo thật hay lưu trạng thái vào DB. `CountDownLatch` chỉ dùng để kiểm thử/CLI đợi minh họa, không phải chiến lược bảo đảm delivery cho một HTTP API production.

## 9. Cách implement handler dành cho production

Handler không phải nơi nên xử lý lại toàn bộ nghiệp vụ nặng. Nó cần **nhanh, an toàn và không gây lỗi thứ cấp**, vì thường đang chạy trên thread thực thi tác vụ hoặc đường hoàn tất của executor.

Một handler khả dụng trong ứng dụng thực tế nên:

1. **Ghi log có cấu trúc:** tên class/method, loại exception, message được kiểm soát, correlation ID/operation ID không nhạy cảm. Dùng logger với stack trace khi chính sách logging cho phép.
2. **Tăng metric lỗi:** ví dụ `async_void_failure_total` gắn tag giới hạn cardinality theo tên method và loại lỗi; tránh dùng raw order ID làm tag metric.
3. **Không in nguyên mảng params:** argument có thể là access token, email, dữ liệu người dùng, `MultipartFile` hoặc object graph khổng lồ. `toString()` còn có thể ném lỗi hoặc thực hiện công việc bất ngờ.
4. **Phân biệt loại lỗi:** lỗi validation, network, timeout, bug logic, reject, shutdown; không auto-retry mọi exception.
5. **Ghi nhận trạng thái nghiệp vụ qua cơ chế đáng tin cậy:** nếu công việc quan trọng, handler có thể phát một event lỗi có idempotency key hoặc yêu cầu cập nhật job record, nhưng phải xem xét transaction và độ tin cậy của kênh ghi.
6. **Không thực hiện I/O blocking kéo dài trong handler:** nếu handler bị kẹt, worker vẫn chưa được giải phóng; pool có thể bị giảm throughput hoặc thêm overload.
7. **Tự bảo vệ lỗi thứ cấp:** dùng minimal operations và biện pháp catch phù hợp quanh logging/notification. Không để handler ném thêm exception ngoài ý muốn rồi nghĩ rằng Spring đã tự sửa mọi việc.
8. **Quan sát cả đường submit:** dùng metric `TaskRejectedException` riêng vì một task bị reject có thể chưa từng gọi đến handler.

Mẫu pseudo-logging tối giản để trình bày nguyên tắc:

~~~java
@Override
public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (error, method, args) -> {
        try {
            metrics.incrementAsyncFailure(
                method.getDeclaringClass().getSimpleName(),
                method.getName(),
                error.getClass().getSimpleName());
            log.error("Unhandled @Async void failure in {}",
                method.getName(), error);
        } catch (RuntimeException loggingFailure) {
            // Fallback tối thiểu theo chính sách hệ thống.
        }
    };
}
~~~

Đây là **đoạn tích hợp**, giả định có dependency `metrics` và `log`, không phải chương trình biên dịch độc lập. Trong production, không nên âm thầm nuốt `loggingFailure` mà không có kênh báo lỗi dự phòng; cần chọn kênh có thể vận hành an toàn và tránh chính nó làm tràn log hoặc nghẽn executor.

## 10. Handler không phải cơ chế retry tự động

`AsyncUncaughtExceptionHandler` được gọi sau khi một task `@Async void` đã thất bại. Nó **không lưu lại đầy đủ một command bền vững**, không bảo đảm sự kiện chỉ xảy ra một lần, không có `Future` để biết caller đã đi đâu và không hoàn tác các side effect đã thực hiện trước khi exception.

Không làm như sau:

~~~java
return (error, method, args) -> {
    method.invoke(targetBean, args);  // Nguy hiểm: retry bằng reflection ngay
};
~~~

Đây là **phản ví dụ**, không độc lập vì biến `targetBean` chưa khai báo. Nó có thể:

- Gọi sai instance/proxy và bỏ qua advice hoặc gây recursion;
- Nhân tác dụng phụ khi task đã ghi DB/gửi request trước khi lỗi;
- Retry tức thời trong lúc dependency vẫn quá tải;
- Gây lỗi reflection, security, transaction, context hoặc duplicate.

Nếu cần retry, thiết kế **ngay từ công việc nghiệp vụ**: operation ID, idempotency key, dữ liệu đầu vào bền vững, phân loại lỗi, `maxAttempts`, backoff + jitter, deadline, queue/job store và trạng thái terminal rõ ràng. Một handler có thể là nơi **phát hiện** sự cố hoặc **khởi phát** một yêu cầu xử lý lỗi, không tự nhiên là một job scheduler bảo đảm delivery.

## 11. Exception chưa được xử lý và lỗi đã được catch

So sánh hai implementation:

~~~java
@Async
public void sendA(String id) {
    remoteService.send(id); // Ném IllegalStateException -> có thể đến handler
}

@Async
public void sendB(String id) {
    try {
        remoteService.send(id);
    } catch (IllegalStateException ex) {
        logger.warn("sendB failed");
        // Không rethrow -> handler không nhận exception này
    }
}
~~~

Đây là **đoạn minh họa tích hợp** với `remoteService`/`logger` và annotation được cấu hình. Nếu logic nghiệp vụ cố ý xử lý xong lỗi trong `catch`, handler không nên được gọi; nhưng nếu chỉ log rồi bỏ qua, hệ thống không còn tín hiệu failure tập trung.

Trường hợp công việc `@Async void` bên trong chỉ khởi chạy một `CompletableFuture` khác:

~~~java
@Async
public void detach(String id) {
    asyncClient.send(id);  // Trả CompletableFuture, nhưng bị bỏ qua
}
~~~

Nếu `asyncClient.send(id)` trả future chưa hoàn tất, phương thức `detach` có thể trở về bình thường trước khi future bên trong gặp lỗi. Lỗi của stage **sau khi method void đã kết thúc** không phải exception chưa bắt từ thân `detach`; `AsyncUncaughtExceptionHandler` không tự động theo dõi stage bị bỏ quên. Với thao tác cần hoàn tất end-to-end, hãy trả kết quả dưới dạng `CompletableFuture<Void>` và nối/quan sát stage đúng cách, hoặc thiết kế job status bền vững.

## 12. TaskRejectedException và overload cần được giám sát riêng

Một `ThreadPoolTaskExecutor` có giới hạn worker và queue có thể từ chối tác vụ khi quá tải. Từ chối xảy ra ở **lần nộp** nên đôi khi caller sẽ nhận `TaskRejectedException` trước cả khi worker bắt đầu chạy phương thức `@Async void`.

Snippet minh họa từ caller:

~~~java
try {
    notificationService.sendNotification(orderId);
} catch (TaskRejectedException rejected) {
    // Điều phối quá tải ở caller: phản hồi/backpressure hoặc lưu durable job.
}
~~~

Đây là **đoạn ghép vào ứng dụng**; `notificationService` là bean Spring proxy, `orderId` là input và `TaskRejectedException` thuộc `org.springframework.core.task`. `try/catch` này **có thể bắt lỗi submit**, nhưng **không bắt** `IllegalStateException` ném sau đó từ thân `@Async void` trong worker.

Đừng lập vòng `while(true)` gọi lại ngay khi reject: nó làm quá tải thêm nghiêm trọng. Thay vào đó, phân biệt request được phép từ chối với job bắt buộc lưu, có response `429`/`503` phù hợp, backoff/jitter và giới hạn retry, hoặc queue bền vững.

Một khác biệt khác: `CallerRunsPolicy` có thể thực thi task trên caller thread khi pool đang chạy và bão hòa. Nếu muốn giữ request latency có thể dự đoán, cần test policy này; không suy ra mọi lời gọi `@Async` luôn trả tức thì.

## 13. Ranh giới Spring proxy, context và transaction

**Proxy:** Với `@EnableAsync` ở chế độ proxy mặc định, gọi `this.sendNotification()` bên trong cùng bean thường bỏ qua interceptor. Handler cũng không can thiệp vào một lời gọi đồng bộ bỏ qua proxy như vậy. Kiểm thử cần gọi từ một bean/caller khác qua Spring-managed proxy.

**Phương thức không phù hợp:** Tránh phụ thuộc `@Async` trên private/final method trong class-based proxy vì không thể intercept theo cách thường mong đợi. Class/method phải được Spring proxy hợp lệ. Annotation không biến mọi lời gọi của một Java object tự tạo bằng `new` thành async.

**Thread context:** Worker không tự kế thừa an toàn toàn bộ `ThreadLocal`, MDC, security principal hoặc request attributes của caller. `params` trong handler không phải giải pháp context propagation chung. Truyền correlation ID an toàn qua command/context mechanism do ứng dụng thiết kế; sử dụng `TaskDecorator` hoặc adapter tương ứng khi cần.

**Transaction:** Transaction imperative thường gắn với thread. Lỗi trong worker không tự rollback transaction đã commit ở request thread; đồng thời handler được gọi **sau khi method lỗi** không tự mở một transaction mới để ghi trạng thái. Nếu muốn ghi failure status, cần transaction trong service xử lý hoặc `TransactionTemplate` có scope rõ.

**Bean singleton:** Nhiều task async có thể chạy đồng thời trên một service singleton. Handler cũng có thể bị gọi đồng thời; bộ đếm, cache, trạng thái dùng chung phải thread-safe. Nếu dùng logger và meter client sẵn có, xác minh tính thread-safe của các dependency.

## 14. Monitoring và test: đừng chỉ assert log

Một bộ test chuyên dụng cho `AsyncUncaughtExceptionHandler` nên xác minh:

1. **Lời gọi đi qua proxy:** tạo `ApplicationContext`, lấy bean từ Spring rồi gọi một method public `@Async void`.
2. **Exception của method:** method ném `IllegalStateException` với message đã biết; handler nhận đúng loại và message đó.
3. **Method metadata:** `method.getName()` đúng, `params.length` và dữ liệu đã được phép kiểm tra đúng; không dump raw token hoặc PII.
4. **Thực thi thread:** dùng `CountDownLatch` đợi handler, không test bằng `Thread.sleep` cố định hoặc giả định exception xảy ra trước lời gọi tiếp theo của caller.
5. **Phương thức void thành công:** handler không bị gọi khi task hoàn tất bình thường; cần latch/clock kiểm soát thời gian test.
6. **Exception đã được catch:** nếu method tự xử lý lỗi mà không rethrow, handler không được gọi.
7. **Future return:** một method `@Async CompletableFuture<Void>` lỗi thì returned future hoàn tất ngoại lệ; handler void không phải kênh lỗi của nó.
8. **Submit rejection:** tạo pool bounded và queue đầy với latch, assert caller nhận rejection theo policy; không chờ handler để phát hiện điều đó.
9. **Handler robustness:** giả lập logger/metric client lỗi; kiểm tra dự phòng và không gây retry hay gọi lại nghiệp vụ vô hạn.
10. **Idempotency / restart:** với job cần delivery, kiểm thử lost response, process restart, duplicate command và trạng thái job bền vững ở nơi lưu ngoài memory.

Ví dụ kiểm thử dùng `CountDownLatch` không nhằm biến mọi API thành blocking, mà để **test có điểm đồng bộ xác định**. Ví dụ trong mục 8 kiểm tra cả exception và method argument count, sau đó xác nhận caller không nhận task exception qua một `try/catch` bình thường.

Các metric hữu ích: `async_void_failure_total`, `async_submit_rejected_total`, queue depth, active worker, task duration, failure-by-method và tổng số job lỗi chưa xử lý. Không đưa object ID có cardinality lớn làm label metric; gắn vào log/tracing có kiểm soát nếu thực sự cần.

## 15. Những hiểu nhầm thường gặp

| Hiểu nhầm | Vì sao sai | Sửa thế nào |
| --- | --- | --- |
| “`try/catch` quanh `@Async void` sẽ bắt exception worker.” | Caller và worker khác đường thực thi | Dùng handler; chọn `CompletableFuture` nếu caller cần kết quả |
| “Chỉ cần khai báo bean `AsyncUncaughtExceptionHandler` là Spring sẽ luôn dùng.” | Handler cần được gắn vào async interceptor qua cấu hình | Dùng `AsyncConfigurer.getAsyncUncaughtExceptionHandler()` |
| “`@Async void` thất bại thì caller chắc chắn biết.” | Signature void không mang Future | Ghi metric/log/event; trả Future khi cần trạng thái |
| “Handler cũng nhận lỗi của `CompletableFuture`.” | Future đã có kênh lỗi riêng | `whenComplete`, `exceptionally`, `get()`/`join()` |
| “Handler sẽ nhận lỗi từ mọi `executor.execute`.” | API raw executor không nhất thiết qua `@Async` proxy | Thiết kế error handler riêng cho executor/task |
| “`TaskRejectedException` là một lỗi trong thân method.” | Nó có thể xảy ra ở bước nộp trước khi chạy | Bắt tại caller và quan sát metric reject |
| “Handler mặc định tự retry.” | `SimpleAsyncUncaughtExceptionHandler` chủ yếu logging | Retry có policy, job store, deadline và idempotency |
| “Log toàn bộ `params` là thuận tiện và vô hại.” | Có thể lộ bí mật/PII hoặc gây log bùng nổ | Sanitize, chỉ lưu method/type/correlation ID |
| “Handler luôn chạy trên thread riêng chuyên dụng.” | Nó thường được gọi trong đường thực thi task; policy executor có thể khác | Giữ handler ngắn, không blocking, test overload |
| “@Async void bảo đảm công việc sống qua JVM restart.” | Thread-pool task chỉ nằm trong memory | Dùng durable queue/job store nếu cần bảo đảm delivery |
| “Method tự catch/log rồi throw không liên quan.” | Nếu rethrow, handler vẫn có thể nhận exception | Phân định ownership logging để tránh log trùng và cảnh báo trùng |

## 16. Checklist khi đưa vào production

- `@EnableAsync` đã được bật trong đúng `ApplicationContext`, lời gọi qua Spring proxy.
- Method cần handler thực sự có kiểu trả về `void`, không phải `CompletableFuture<Void>`.
- Handler được cấu hình qua `AsyncConfigurer` và đã có integration test xác nhận chạy.
- Tôi biết exception nào bị bắt trong thân method và exception nào được rethrow đến handler.
- Handler ghi nhận lỗi an toàn, không dump nguyên `params` hoặc bí mật người dùng.
- Handler thread-safe, không block lâu, không làm thêm các thao tác không có hạn.
- Caller xử lý riêng `TaskRejectedException` tại thời điểm nộp.
- Tôi đã chọn có chủ đích giữa fire-and-forget `void` và API trả `CompletableFuture<Void>`.
- Tôi không dùng handler để retry reflection tức thời hoặc tạo thêm quá tải.
- Job quan trọng có idempotency key, durable state, retry policy, timeout và cơ chế reconciliation.
- Transaction, security context, logging context và lifecycle worker đều có owner rõ ràng.
- Metric/log/alert có thể phân biệt **task đã chạy rồi lỗi** với **task bị từ chối chưa chạy**.

**Kết luận:** `AsyncUncaughtExceptionHandler` là kênh xử lý exception **không được bắt từ phương thức `@Async` trả `void`** sau khi task thực sự chạy qua Spring interceptor. Nó có thông tin về lỗi, method và arguments, nhưng không chuyển lỗi trở lại caller, không làm tự động retry, và không thay thế `Future` hay job tracking. Với công việc cần kết quả/acknowledgement, ưu tiên API trả future hoặc cơ chế job bền vững; với fire-and-forget, phải có handler, logging, metric và chính sách xử lý failure rõ ràng.

## Tài liệu tham khảo chính thức

- Spring Framework — [AsyncUncaughtExceptionHandler Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/interceptor/AsyncUncaughtExceptionHandler.html): chữ ký `handleUncaughtException(Throwable, Method, Object...)`.
- Spring Framework — [SimpleAsyncUncaughtExceptionHandler Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/interceptor/SimpleAsyncUncaughtExceptionHandler.html): mặc định ghi log exception.
- Spring Framework — [AsyncConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/AsyncConfigurer.html): cấu hình default executor và uncaught exception handler.
- Spring Framework — [@EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html): kích hoạt async, proxy mode, default và handler configuration.
- Spring Framework — [@Async Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html): hỗ trợ return type `void`, `Future` và `CompletableFuture`.
- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): exception management cho async methods và executor integration.
- Spring Framework — [TaskRejectedException Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/TaskRejectedException.html): xử lý từ chối lúc nộp task.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-004">Cấu hình bộ xử lý ngoại lệ bằng AsyncConfigurer</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu hình bộ xử lý ngoại lệ bằng AsyncConfigurer

## 1. Vấn đề: @Async void lỗi nhưng caller không có Future để kiểm tra

Trong Spring Framework, `@Async` cho phép thực thi một phương thức bằng executor do Spring chọn. Khi method trả `void`, caller không có giá trị hay handle hoàn tất để nhận exception của task chạy sau đó trên worker. Ví dụ:

```java
import org.springframework.scheduling.annotation.Async;

@Async("backgroundExecutor")
public void notifyUser(String eventId) {
    throw new IllegalStateException("Không gửi được thông báo");
}
```

Đây là **mảnh method trong một Spring-managed bean**, không phải chương trình độc lập. Nếu lời gọi thực sự đi qua Spring async proxy và executor chạy task trên worker, exception phát sinh sau khi caller đã rời khỏi lời gọi. `try/catch` quanh `notifyUser(...)` ở caller thường không bắt được lỗi này.

Spring cung cấp `AsyncUncaughtExceptionHandler` để nhận lỗi **không thể truyền lại qua kiểu trả về** từ phương thức `@Async` trả `void`. Để thay handler mặc định trong Java Configuration, một lớp `@Configuration` triển khai `AsyncConfigurer` và override:

```java
@Override
public AsyncUncaughtExceptionHandler
        getAsyncUncaughtExceptionHandler() {
    return (exception, method, params) -> {
        // Ghi nhận exception từ @Async void.
    };
}
```

Điểm cần nhớ: **handler không phải công cụ bắt mọi exception của mọi task**. Method `@Async` trả `Future<T>` hoặc `CompletableFuture<T>` có kênh truyền lỗi qua kết quả; lỗi gửi việc (như executor bị từ chối) cũng khác lỗi trong thân task.

Bài học tập trung vào cách khai báo `AsyncConfigurer`, hợp đồng của `AsyncUncaughtExceptionHandler`, ví dụ Spring chạy được, phân biệt hai kênh báo lỗi, các rủi ro khi khởi tạo và thiết kế xử lý lỗi thực tế.

## 2. AsyncConfigurer nằm ở đâu trong luồng @Async?

Lỗi khi gửi tác vụ và lỗi xảy ra sau khi worker bắt đầu đã được phân biệt ở bài mở đầu của chương. Khi cấu hình Spring, điều quan trọng hơn là biết **lỗi nào do AsyncConfigurer phụ trách**.

| Ranh giới | Trách nhiệm cấu hình hoặc quan sát |
| --- | --- |
| Proxy lựa chọn executor và gửi công việc | Bean lookup lỗi, pool shutdown hoặc rejection có thể đi tới caller ngay; handler cho `@Async void` không bảo đảm nhận rejection. |
| Phương thức `@Async void` được worker chạy rồi ném exception chưa bắt | Async interceptor chuyển exception tới `AsyncUncaughtExceptionHandler` mà `AsyncConfigurer` cung cấp. |
| Phương thức `@Async Future` hoặc `CompletableFuture` thất bại | Caller quan sát exceptional completion của Future; handler dành cho void không phải nơi nhận kết quả. |

Vì thế cần **gọi bean qua Spring proxy** trong test và chọn executor có tên rõ ràng. Cấu hình đúng handler không thay thế cơ chế xử lý từ chối lúc submit, và không bảo đảm job đã được lưu bền vững.

## 3. AsyncConfigurer là điểm cấu hình nào?

Interface `org.springframework.scheduling.annotation.AsyncConfigurer` cho phép cấu hình hai thành phần tách biệt:

| Phương thức | Tác dụng | Có bắt buộc override cả hai không? |
|---|---|---|
| `getAsyncExecutor()` | Chọn `Executor` mặc định cho method `@Async` không chỉ rõ executor | Không |
| `getAsyncUncaughtExceptionHandler()` | Chọn handler cho lỗi không chuyển về caller từ `@Async void` | Không |

Từ các phiên bản Spring 6.x, các phương thức trên là default methods. Muốn thay handler, có thể chỉ override `getAsyncUncaughtExceptionHandler()`, để việc chọn executor theo bean name, qualifier hoặc quy tắc mặc định được cấu hình riêng.

Hợp đồng API rút gọn:

```java
public interface AsyncConfigurer {
    Executor getAsyncExecutor();
    AsyncUncaughtExceptionHandler
            getAsyncUncaughtExceptionHandler();
}
```

Đây là **sơ đồ chữ ký rút gọn**, không phải nguồn code đầy đủ: API thật có default implementations và annotation về nullable tùy phiên bản. Trong cấu hình thật cần import interface từ Spring và khai báo `@EnableAsync`.

**Không cần kế thừa `AsyncConfigurerSupport` ở ứng dụng Spring mới.** Class hỗ trợ này đã deprecated từ Spring Framework 6.0; triển khai trực tiếp `AsyncConfigurer` rõ ràng hơn.

Theo JavaDoc Spring, `AsyncConfigurer` thường được triển khai bởi class `@Configuration @EnableAsync`. Nó có thể **khởi tạo sớm trong ApplicationContext bootstrap**, nên cẩn thận khi inject những bean có proxy, transaction hoặc phụ thuộc phức tạp.

## 4. Dữ liệu lỗi mà AsyncConfigurer nên chuyển cho handler

`AsyncUncaughtExceptionHandler` có chữ ký `handleUncaughtException(Throwable error, Method method, Object... params)`. Bài trước đã giải thích hợp đồng và thời điểm handler chạy; ở đây trọng tâm là **thiết kế handler an toàn khi đăng ký vào Spring**.

- Dùng tên method/class và nhóm exception để ghi metric có cardinality hữu hạn; truyền cả Throwable vào logger tại ranh giới chịu trách nhiệm để giữ cause và stack trace.
- Chỉ chọn job/correlation ID được phép từ `params`. Không log toàn bộ đối số: chúng có thể chứa token, thông tin cá nhân hoặc object mutable.
- Handler có thể chạy trên worker, đồng thời với handler khác; dependency và bộ đếm phải thread-safe. Nếu recorder ghi I/O, xác định timeout, fallback và không block worker vô hạn.
- Exception sau một yêu cầu remote không chứng minh side effect đã rollback. Việc retry, ghi trạng thái FAILED hoặc reconciliation cần contract và transaction riêng.

Những quyết định này sẽ được kiểm chứng ở ví dụ `@Configuration` và test với Spring context trong các phần sau.

## 5. Cấu hình tối thiểu qua @Configuration và @EnableAsync

**Tệp `AsyncErrorConfig.java` — class cấu hình Spring, không có main độc lập:**

```java
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncErrorConfig implements AsyncConfigurer {

    @Bean("backgroundExecutor")
    public ThreadPoolTaskExecutor backgroundExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("background-");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler
            getAsyncUncaughtExceptionHandler() {
        return (error, method, params) -> {
            System.err.println("Lỗi async tại "
                    + method.getName()
                    + ": " + error.getMessage());
        };
    }
}
```

Ở đây:

1. `@EnableAsync` kích hoạt cơ chế dùng `@Async` trong ApplicationContext tương ứng.
2. Bean `backgroundExecutor` là `ThreadPoolTaskExecutor` do Spring quản lý vòng đời, không cần tự gọi `initialize()` trong phương thức `@Bean`.
3. `getAsyncUncaughtExceptionHandler()` cung cấp handler, được Spring async interceptor dùng cho những exception chưa xử lý của method `@Async void`.
4. Class không override `getAsyncExecutor()`. Vì các method có thể chọn bean theo `@Async("backgroundExecutor")`, không cần cấu hình default ngay trong ví dụ.
5. Số worker và queue 2/4/16 chỉ để minh họa. Production phải tính theo tải, thời gian chờ và bộ nhớ, không phải hằng số dùng chung.

Một service dùng cấu hình:

```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Async("backgroundExecutor")
    public void send(String jobId) {
        throw new IllegalStateException(
                "Không gửi được job " + jobId);
    }
}
```

Lời gọi đến `NotificationService.send(...)` cần đi **từ một bean khác qua Spring proxy**. Khi ấy handler nhận đúng `Method send`, argument `jobId` và `IllegalStateException`. Nếu muốn tái sử dụng, có thể trả `new ConsoleAsyncErrorHandler()` từ `getAsyncUncaughtExceptionHandler()` thay vì dùng lambda.

## 6. Demo đầy đủ Java 17 + Spring: handler cho void, Future cho method có kết quả

Ví dụ sau có **hai method đều ném exception**, nhưng chỉ một exception được gửi tới `AsyncUncaughtExceptionHandler`. Chương trình dùng `CountDownLatch` để in kết quả theo thứ tự có thể kiểm chứng, không dựa vào `Thread.sleep(...)`.

Yêu cầu: JDK 17 trở lên, Maven, Spring Framework 6.2.7 theo pom. Đây là dự án Maven nhỏ độc lập, không cần HTTP server.

### 6.1. Cấu trúc

```text
async-handler-demo/
├── pom.xml
└── src/main/java/lesson/AsyncHandlerDemo.java
```

### 6.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lesson</groupId>
    <artifactId>async-handler-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.7</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 6.3. Tệp src/main/java/lesson/AsyncHandlerDemo.java

```java
package lesson;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class AsyncHandlerDemo {

    public static void main(String[] args)
            throws InterruptedException {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            Tasks tasks = context.getBean(Tasks.class);

            tasks.failVoid("job-42");
            if (!Config.HANDLED.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Không nhận được tín hiệu handler");
            }
            System.out.println("Caller đã xác nhận handler chạy");

            CompletableFuture<String> result = tasks.failFuture();
            try {
                result.join();
            } catch (CompletionException error) {
                System.out.println("Future lỗi: "
                        + error.getCause().getMessage());
            }
        }
    }

    @Configuration
    @EnableAsync
    public static class Config implements AsyncConfigurer {
        static final CountDownLatch HANDLED = new CountDownLatch(1);

        @Bean("lessonExecutor")
        public ThreadPoolTaskExecutor lessonExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("lesson-");
            return executor;
        }

        @Bean
        public Tasks tasks() {
            return new Tasks();
        }

        @Override
        public AsyncUncaughtExceptionHandler
                getAsyncUncaughtExceptionHandler() {
            return (ex, method, params) -> {
                String jobId = String.valueOf(params[0]);
                System.out.println("Handler: " + method.getName()
                        + "(" + jobId + "): " + ex.getMessage());
                HANDLED.countDown();
            };
        }
    }

    public static class Tasks {
        @Async("lessonExecutor")
        public void failVoid(String id) {
            throw new IllegalStateException("lỗi void");
        }

        @Async("lessonExecutor")
        public CompletableFuture<String> failFuture() {
            throw new IllegalArgumentException("lỗi Future");
        }
    }
}
```

### 6.4. Chạy

Ở thư mục chứa pom:

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.AsyncHandlerDemo
```

Ba dòng in bởi **chính chương trình** trong điều kiện chạy bình thường:

```text
Handler: failVoid(job-42): lỗi void
Caller đã xác nhận handler chạy
Future lỗi: lỗi Future
```

Nếu runtime không lên lịch task trong vòng 5 giây thì chương trình chủ động ném lỗi timeout thay vì chờ vô hạn. Với môi trường chạy bình thường, thứ tự output được bảo đảm:

- `tasks.failVoid("job-42")` đi qua proxy `@Async`, method trả `void` nên exception trong worker đến handler.
- Handler in trước rồi mới gọi `HANDLED.countDown()`; main đợi chốt trước khi in dòng thứ hai.
- `tasks.failFuture()` có kiểu trả `CompletableFuture<String>`. Lỗi nằm trong Future, `join()` ném `CompletionException`; caller lấy `getCause().getMessage()` và in dòng cuối.
- Handler `void` **không** nhận một sự kiện lỗi bổ sung chỉ vì `failFuture()` ném exception.
- Context được đóng bởi `try-with-resources`; không gọi `shutdown()` tùy tiện trong service sử dụng bean executor chung.

Code này kiểm chứng **đường báo exception**, không minh họa tính bền vững hoặc retry. `CountDownLatch` chỉ là công cụ kiểm thử: nó không phải đối tượng `@Async void` tự trả về.

## 7. Handler mặc định hoạt động như thế nào?

Nếu không chỉ định handler tùy chỉnh, Spring xử lý exception chưa được bắt từ `@Async void` bằng cách ghi log thông qua handler mặc định, thường là `SimpleAsyncUncaughtExceptionHandler`. Theo tài liệu chính thức Spring, exception của method void không thể chuyển lại cho caller, vì vậy mặc định chỉ được ghi nhận.

Điểm dễ hiểu nhầm:

- **Có log không đồng nghĩa task đã được khôi phục**: handler không tự gọi lại nghiệp vụ.
- **Không có exception ở caller không đồng nghĩa task đã thành công**: worker có thể lỗi sau khi request đã trả về.
- **Dùng custom handler mà chỉ ghi một dòng `error.getMessage()`** thường làm mất stack trace, tên method hoặc correlation ID cần cho điều tra.
- **Không ghi log** trong custom handler có thể khiến lỗi hoàn toàn khó thấy nếu không có metrics, tracing và kho trạng thái khác.

Một handler production thường kết hợp structured logging, metric counter có nhãn nhóm lỗi phù hợp và cảnh báo theo ngưỡng. Với tác vụ quan trọng, nên có job ID và cơ chế lưu trạng thái thất bại, không chỉ phụ thuộc một câu log.

## 8. Lỗi nào handler không xử lý?

### 8.1. Lỗi từ Future hoặc CompletableFuture

`@Async` trả `CompletableFuture<T>` đưa lỗi về caller qua Future:

```java
@Async("backgroundExecutor")
public CompletableFuture<Integer> calculate(int x) {
    if (x < 0) {
        throw new IllegalArgumentException("x phải không âm");
    }
    return CompletableFuture.completedFuture(x * 2);
}
```

Caller:

```java
service.calculate(-1).whenComplete((value, error) -> {
    if (error != null) {
        System.err.println("Future thất bại: " + error);
    }
});
```

Hai đoạn trên là **các mảnh method/caller Spring**, không phải chương trình chạy độc lập. Trong `Future` cơ bản, `get()` báo lỗi qua `ExecutionException`; với `CompletableFuture.join()` thường qua `CompletionException`. Không chờ bằng `get()/join()` ngay ở HTTP request thread nếu mục tiêu là phản hồi không chặn.

Nếu nghiệp vụ không cần trả dữ liệu nhưng caller cần biết task đã hoàn tất/lỗi, dùng `CompletableFuture<Void>` thay vì `void`:

```java
@Async("backgroundExecutor")
public CompletableFuture<Void> saveAudit(String id) {
    auditRepository.save(id);
    return CompletableFuture.completedFuture(null);
}
```

Khi đó caller có handle để quan sát kết quả; `AsyncUncaughtExceptionHandler` không còn là kênh lỗi chính của method này. `auditRepository` là thành phần giả định do ứng dụng cung cấp.

### 8.2. Lỗi khi gửi việc hoặc sai cấu hình executor

Một pool có thể từ chối task mới khi đã đầy hoặc đóng. Với `ThreadPoolTaskExecutor`, trong cấu hình từ chối thông thường, caller có thể thấy `TaskRejectedException` từ lời gọi qua proxy. Task chưa chạy, vì vậy không thể mặc định chờ handler `void` nhận lỗi này.

```java
try {
    notificationService.send("job-9");
} catch (TaskRejectedException rejected) {
    // Trả lỗi quá tải hoặc có chính sách retry an toàn.
    throw rejected;
}
```

Mảnh mã này giả định đã import `org.springframework.core.task.TaskRejectedException` và có bean `notificationService`. Cần xử lý ở ranh giới nhận việc; nếu API trả HTTP 202 mà thực tế executor đã từ chối, đó là một lỗi hợp đồng.

Lỗi chọn bean executor không tồn tại theo `@Async("missingExecutor")`, lỗi khởi tạo ApplicationContext hoặc method khai báo kiểu trả về không được hỗ trợ cũng là vấn đề wiring/hợp đồng, không phải phương thức `void` đã chạy và ném lỗi thông thường.

### 8.3. Exception được bắt bên trong task

```java
@Async("backgroundExecutor")
public void work(String id) {
    try {
        process(id);
    } catch (ExpectedBusinessException known) {
        recordResult(id, known);
    }
}
```

Mảnh code này giả định `ExpectedBusinessException`, `process` và `recordResult` của ứng dụng. Vì exception đã được catch và không được ném lại, handler toàn cục **không** nhận nó. Đó có thể là chủ ý nếu công việc đã có cơ chế xử lý nghiệp vụ; nhưng không được lặng lẽ nuốt mọi lỗi rồi ghi nhận thành công.

## 9. AsyncConfigurer khởi tạo sớm và rủi ro dependency

Spring Javadoc nhấn mạnh cấu hình triển khai `AsyncConfigurer` có thể được khởi tạo sớm trong lúc bootstrap. Nếu configurer field-inject một bean có proxy hoặc quá nhiều dependency, bean ấy có thể được tạo chưa qua đầy đủ BeanPostProcessor như mong muốn.

Ví dụ cần cảnh giác:

```java
@Configuration
@EnableAsync
public class FragileConfiguration implements AsyncConfigurer {
    @Autowired
    private ErrorRecorder recorder;

    @Override
    public AsyncUncaughtExceptionHandler
            getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                recorder.record(method.getName(), ex);
    }
}
```

Đây là **mảnh cấu hình có nguy cơ**, chưa độc lập vì `ErrorRecorder` là component ứng dụng. Khi recorder cần `@Transactional`, `@Async`, proxy hoặc phụ thuộc vòng, việc inject quá sớm dễ gây khó đoán.

Một lựa chọn trì hoãn truy cập dependency là `ObjectProvider<ErrorRecorder>`:

```java
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class LazyAsyncConfiguration implements AsyncConfigurer {

    private final ObjectProvider<ErrorRecorder> recorderProvider;

    public LazyAsyncConfiguration(
            ObjectProvider<ErrorRecorder> recorderProvider) {
        this.recorderProvider = recorderProvider;
    }

    @Override
    public AsyncUncaughtExceptionHandler
            getAsyncUncaughtExceptionHandler() {
        return (error, method, args) -> {
            recorderProvider.getObject().record(
                    method.getName(), error);
        };
    }
}
```

Ví dụ này cần `ErrorRecorder` được khai báo trong dự án; không phải class Java chạy độc lập. Điểm quan trọng: **không gọi `recorderProvider.getObject()` ngay trong `getAsyncUncaughtExceptionHandler()`** nếu mục tiêu là hoãn khởi tạo. Chỉ lấy bean trong lúc handler thực sự xử lý lỗi, và vẫn phải kiểm soát lỗi thứ cấp/vòng gọi.

Nếu chỉ cần ghi log, handler stateless không phụ thuộc bean phức tạp là lựa chọn dễ kiểm thử và vận hành hơn.

## 10. Tùy chỉnh executor mặc định và handler là hai việc riêng

Cùng một class có thể override:

```java
@Override
public Executor getAsyncExecutor() {
    return defaultExecutor;
}

@Override
public AsyncUncaughtExceptionHandler
        getAsyncUncaughtExceptionHandler() {
    return customHandler;
}
```

Đây là **hai mảnh method minh họa**, với `defaultExecutor` và `customHandler` giả định đã được cấu hình đúng và thuộc vòng đời ứng dụng.

`getAsyncExecutor()` dùng để chỉ định **pool mặc định** cho những method `@Async` không chọn executor. `@Async("backgroundExecutor")` có thể chọn một bean executor cụ thể bằng tên/qualifier, kể cả khi default executor khác. Handler qua `getAsyncUncaughtExceptionHandler()` vẫn phục vụ lỗi của `@Async void` được xử lý bởi hạ tầng cùng context, không phải “chỉ bắt lỗi từ default pool”.

Nếu không cấu hình executor mặc định, Spring tìm theo quy tắc bean `TaskExecutor`/bean `taskExecutor`; nếu không tìm được phù hợp, có thể dùng `SimpleAsyncTaskExecutor`. Trong môi trường có nhiều executor, cần xác định rõ đường định tuyến và không dựa vào lựa chọn ngầm.

**Không tạo pool riêng trong handler chỉ để ghi log.** Handler chạy trong bối cảnh xử lý lỗi của một task; nếu cần đẩy telemetry đi nơi khác, phải có chính sách giới hạn tài nguyên, timeout và fallback. Dùng chung pool đang quá tải để đệ quy gửi thêm task ghi lỗi có thể làm tình hình xấu hơn.

## 11. Proxy và @EnableAsync: vì sao handler không chạy?

### 11.1. Gọi trong cùng bean (self-invocation)

```java
public void outer() {
    this.failAsync();  // Bỏ qua proxy ở chế độ mặc định.
}

@Async("backgroundExecutor")
public void failAsync() {
    throw new IllegalStateException("Lỗi");
}
```

Đây là phản ví dụ trong một class. Với proxy-based `@Async`, lời gọi `this.failAsync()` không đi qua interceptor nên Java thực thi method trực tiếp. Caller/`outer()` có thể thấy exception như lỗi đồng bộ. Handler của `@Async` không có cơ hội nhận lỗi.

Cách sửa phổ biến: đưa method async sang một bean khác, inject bean đó và gọi qua Spring-managed proxy. Không gọi method private/final và mong CGLIB proxy chặn mọi tình huống.

### 11.2. Tự tạo đối tượng bằng new

```java
new NotificationService().send("job-42");
```

Nếu đối tượng không được Spring quản lý, `@Async` chỉ là annotation ở source code; lời gọi không tự chuyển sang executor. Phải lấy bean do Spring quản lý từ context và bảo đảm `@EnableAsync` có hiệu lực đúng context.

### 11.3. Có nhiều ApplicationContext

Với ứng dụng có root context và servlet context, việc bật `@EnableAsync` trong một context không tự tạo proxy cho mọi bean ở context khác. Đặt cấu hình đúng nơi bean nghiệp vụ tồn tại và xác minh bằng integration test.

### 11.4. @Async method trả sai kiểu

Spring `@Async` thông thường hỗ trợ `void` hoặc kiểu `Future` như `CompletableFuture`. Chữ ký `@Async public String load()` là không hợp lệ cho async interceptor, không thể biến String đồng bộ thành kết quả nhận ngay. Lỗi kiểu trả về không phải lỗi từ thân method `@Async void` cần handler xử lý.

## 12. Xử lý lỗi handler thứ cấp

Handler được gọi khi tác vụ đã gặp sự cố. Nếu handler lại gọi database, network hoặc telemetry backend không khả dụng, bản thân handler có thể thất bại.

Spring có cơ chế ghi cảnh báo khi `AsyncUncaughtExceptionHandler` tự ném lỗi bất ngờ; nhưng caller của `@Async void` vẫn không có Future để nhận lỗi này, nên **không được xem đó là đường phục hồi đáng tin cậy**.

Một handler thực tế nên:

1. Ghi exception type, message, stack trace và method/operation đủ để điều tra.
2. Chỉ ghi những argument được phép, không log mật khẩu/token/payload đầy đủ.
3. Không lặp retry vô hạn hoặc gửi lại cùng công việc một cách mù quáng.
4. Có fallback logging/metrics tối thiểu nếu kho trạng thái không thể ghi.
5. Không làm các thao tác chờ vô hạn hoặc giữ khóa chung trong lúc gọi I/O.
6. Phân biệt lỗi nghiệp vụ đã dự kiến và lỗi hạ tầng để tránh cảnh báo sai.
7. Nếu cần lưu FAILED trạng thái job, thiết kế riêng transaction, khả năng ghi nhận và idempotency.

Một mẫu handler với fallback (mảnh method trong class có `logger` và `errorStore`):

```java
@Override
public void handleUncaughtException(
        Throwable error, Method method, Object... args) {
    String operation = method.getName();
    try {
        errorStore.markFailed(operation, error);
    } catch (RuntimeException reportingFailure) {
        logger.error("Async task thất bại: " + operation, error);
        logger.warn("Không ghi được trạng thái thất bại",
                reportingFailure);
    }
}
```

`errorStore` và `logger` ở đây là thành phần ứng dụng, không phải Java API có sẵn. Đừng dùng đoạn này như một class độc lập; nguyên tắc là **không để việc ghi nhận lỗi thứ cấp che khuất lỗi gốc**.

## 13. Log, metrics và bảo vệ dữ liệu

Một event lỗi có thể có các trường:

| Trường | Tại sao cần | Lưu ý |
|---|---|---|
| `operation`/`method` | Biết nghiệp vụ nào lỗi | Tên có cardinality hữu hạn |
| `exceptionType` | Phân loại lỗi kết nối/logic/timeout | Không chỉ in message |
| `jobId`/`correlationId` | Liên kết lần thực thi | Kiểm tra quyền ghi log |
| `executorName` | Khoanh vùng tài nguyên | Không nhất thiết suy từ thread name |
| `attempt` | Biết lượt thử khi có retry | Chống đếm trùng |
| `elapsed` | Phân biệt lỗi tức thời/chờ lâu | Cần đo queue wait riêng |
| `status` | Submitted/accepted/started/failed | Không đánh đồng accepted với succeeded |

Mẫu log **minh họa định dạng**, không phải output mặc định Spring:

```text
level=ERROR operation=sendNotification
jobId=J-102 exception=IllegalStateException
executor=backgroundExecutor message="Provider unavailable"
```

Nếu dùng Micrometer/Prometheus, một counter có thể mang nhãn operation và exception type. **Không đặt jobId làm tag metrics**, vì mỗi ID mới có thể tạo thêm time series, gây cardinality lớn. Job ID phù hợp hơn trong log/trace có chính sách dữ liệu riêng.

Handler chỉ ghi log không có nghĩa đã tạo job tracking. Nếu client cần xem `FAILED/SUCCEEDED` theo job ID, ứng dụng phải thiết kế kho trạng thái và giao diện truy vấn; không thể suy ra từ `AsyncConfigurer`.

## 14. Transaction, security và context không tự truyền

`@Async` thường chuyển thân method sang worker thread. Spring transaction gắn với caller không tự trở thành cùng transaction ở worker. Nếu worker gọi database và lỗi, handler không tự rollback transaction trước đó của caller.

```text
Request thread:
  @Transactional bắt đầu TX-A
  gửi @Async void job
  TX-A commit hoặc rollback theo chính sách caller

Worker thread:
  chạy độc lập về thời điểm
  nếu cần transaction thì thiết kế TX-B tại worker
  lỗi chưa xử lý -> AsyncUncaughtExceptionHandler
```

Nếu tác vụ nền đọc dữ liệu mà caller chưa commit, có thể thấy trạng thái cũ hoặc không thấy bản ghi, tùy transaction isolation. Với yêu cầu chỉ làm việc **sau commit**, cân nhắc transaction event/outbox hoặc cơ chế bảo đảm thời điểm gửi công việc.

`SecurityContextHolder`, MDC, custom `ThreadLocal` và request context không mặc nhiên được truyền đầy đủ sang worker khi dùng một executor bất kỳ. Nếu handler cần correlation ID, hãy truyền vào tham số task hoặc dùng context propagation được kiểm thử, và không giữ request object sau vòng đời.

**Handler không phải rollback của hệ thống ngoài**. Một email đã gửi hoặc tác vụ thanh toán đã thực hiện một phần có thể cần đối soát và bù trừ theo nghiệp vụ.

## 15. Khác với ExecutorService, @Scheduled và web exception handler

`AsyncUncaughtExceptionHandler` thuộc hạ tầng **Spring @Async**, không phải global exception handler cho JVM.

| Cơ chế | Kênh lỗi thông thường |
|---|---|
| `@Async void` qua Spring proxy | `AsyncUncaughtExceptionHandler` |
| `@Async Future<T>`/`CompletableFuture<T>` | Future/callback của caller |
| `ExecutorService.submit(...)` | Future mà `submit` trả |
| `ThreadPoolExecutor.execute(Runnable)` Java thuần | Theo worker/`UncaughtExceptionHandler` hoặc wrapper của task |
| `@Scheduled` | ErrorHandler/scheduler policy tương ứng |
| Spring MVC handler đồng bộ | Web exception resolvers/`@ControllerAdvice` theo cấu hình |
| Lỗi gửi vào pool bị từ chối | Caller/ranh giới submit, không mặc định void-handler |

Ví dụ `@Scheduled` có thể được cấu hình xử lý lỗi bởi `ErrorHandler` của Spring scheduling; không nên gắn `AsyncConfigurer` rồi mặc định rằng mọi tác vụ `@Scheduled` đều dùng cùng handler. Một ứng dụng có thể có cả `@Async` và `@Scheduled`, nhưng hai annotation biểu đạt đường thực thi khác nhau.

Nếu task có kết quả mà caller không quan sát Future, đó là **thiếu kiểm soát lỗi ở caller**; Spring không tự gửi exception đó sang void-handler chỉ vì Future bị bỏ quên.

## 16. Cách kiểm thử handler đúng, không dựa vào sleep

Một integration test nên dựng Spring context với `@EnableAsync`, bean executor có giới hạn và `AsyncConfigurer`. Gọi service qua proxy rồi dùng `CountDownLatch` hoặc callback để quan sát handler.

Một mảnh test JUnit (không độc lập, cần test context và assertions):

```java
CountDownLatch handled = new CountDownLatch(1);
AtomicReference<Throwable> observed = new AtomicReference<>();
AtomicReference<String> methodName = new AtomicReference<>();

AsyncUncaughtExceptionHandler testHandler = (error, method, args) -> {
    observed.set(error);
    methodName.set(method.getName());
    handled.countDown();
};

// Đăng ký testHandler qua AsyncConfigurer trong test context
// rồi gọi service Spring-managed:
service.failVoid("test-1");

assertTrue(handled.await(5, TimeUnit.SECONDS));
assertEquals("failVoid", methodName.get());
assertInstanceOf(IllegalStateException.class, observed.get());
```

Phải **thực sự đăng ký** `testHandler` vào `AsyncConfigurer` khi dựng context; việc chỉ tạo một biến handler trong test không làm Spring sử dụng nó. `AtomicReference`, `TimeUnit` và assertion methods cần import tương ứng. Với ví dụ độc lập đầy đủ, dùng `AsyncHandlerDemo` ở mục 6.

Những tình huống quan trọng:

1. `@Async void` ném exception: handler nhận đúng Throwable, Method, args.
2. `@Async void` thành công: handler không được gọi.
3. `@Async CompletableFuture` lỗi: Future báo lỗi; handler không có thêm lần gọi cho lỗi này.
4. Executor từ chối: caller quan sát rejection theo policy.
5. Self-invocation: chứng minh vì sao handler không nhận lỗi do advice không chạy.
6. Handler tự lỗi: fallback vẫn ghi được lỗi gốc ở mức phù hợp.
7. Tham số có thông tin nhạy cảm: không bị log toàn bộ.
8. Ứng dụng đóng/queue đầy: không tuyên bố task đã thành công nếu mới accepted.

`Thread.sleep(100)` không chứng minh worker hay handler đã chạy xong: trên CI/production, thời gian có thể dao động. Một tín hiệu hoàn tất có timeout là bằng chứng tốt hơn.

## 17. Những nhầm lẫn thường gặp

| Nhầm lẫn | Thực tế |
|---|---|
| `AsyncConfigurer` tự xử lý mọi lỗi async | Chỉ cấu hình executor mặc định và handler không truyền lỗi được về caller của method void |
| Cấu hình handler xong là không cần xử lý lỗi Future | Future-typed method vẫn cần caller theo dõi |
| `try/catch` quanh caller bắt được mọi exception trên worker | Với async pool, lỗi thường đến sau khi caller đã trả |
| `@Async void` không thể gây exception vì return `void` | Exception runtime vẫn có thể ném ở thân method |
| Handler được gọi cả khi task chưa được executor nhận | Rejection là đường gửi, thường thuộc caller |
| `@EnableAsync` tự tạo thread pool production đúng tải | Cần định nghĩa/chọn executor và giới hạn tài nguyên |
| `this.asyncMethod()` kích hoạt Spring proxy | Self-invocation bỏ qua advice ở proxy mode mặc định |
| `Future` ném lỗi thì handler void cũng nhận | Exception nằm trong Future và phải được quan sát |
| Log handler có nghĩa nghiệp vụ đã retry | Logging không thực hiện retry/rollback |
| Handler có thể dùng mọi dữ liệu trong params để log | Cần bảo mật/ẩn dữ liệu và tránh payload lớn |
| `AsyncConfigurerSupport` là cách mới nhất | Deprecated từ Spring 6.0; implement interface trực tiếp |
| Handler ghi lỗi vào DB tự động dùng transaction caller | Worker/context có ranh giới khác; cần transaction riêng nếu phù hợp |
| Task đã accepted nghĩa là sẽ sống qua JVM crash | In-memory pool không phải hàng đợi bền vững |

## 18. Bài tập thực hành

### Bài 1 — Cấu hình handler đơn giản

Tạo class `@Configuration @EnableAsync implements AsyncConfigurer` trả một `AsyncUncaughtExceptionHandler`. Handler ghi tên method, exception class và mã job đã được phép. Tạo bean có `@Async("backgroundExecutor") void fail(String id)` ném `IllegalStateException`.

**Gợi ý:** Gọi từ bean khác qua Spring proxy, dùng latch để biết handler chạy. Không dùng `Thread.sleep` để đoán.

### Bài 2 — void khác Future

Hai method `void push()` và `CompletableFuture<String> load()` cùng ném lỗi. Handler của `AsyncConfigurer` nhận lỗi nào? Caller cần làm gì với lỗi còn lại?

**Gợi ý:** Handler nhận lỗi của `void`. Future chứa lỗi của `load()`; dùng `whenComplete`, `handle` hoặc `join/get` tại ranh giới chấp nhận chặn.

### Bài 3 — Lỗi gửi việc

Executor đã đầy, `@Async void` bị từ chối khi submit. Có được bảo đảm rằng handler sẽ ghi nhận không? Nếu API trả HTTP 202 trong khi job bị từ chối thì sai gì?

**Gợi ý:** Handler không tự bắt rejection; caller cần phản hồi quá tải đúng hợp đồng. HTTP 202 không được dùng để báo một công việc đã được nhận nếu thực tế bị từ chối.

### Bài 4 — Lazy dependency

Một `AsyncConfigurer` inject `ErrorRecorder` có `@Transactional` và cần proxy. Vì sao không nên lấy bean đó ngay trong quá trình bootstrap? Dùng `ObjectProvider` thế nào?

**Gợi ý:** Configurer được khởi tạo sớm; trì hoãn `getObject()` đến khi handler thực sự chạy, đồng thời bảo đảm không tạo vòng phụ thuộc.

### Bài 5 — Bảo mật và metrics

Handler nhận argument gồm mã job và token API. Bạn sẽ ghi những trường nào vào log, và vì sao không gắn mã job làm metric label?

**Gợi ý:** Log method, exception type, job ID được phép, correlation ID; che token. Metrics dùng nhãn có cardinality hữu hạn như operation, error type.

### Bài 6 — Handler tự lỗi

Handler ghi trạng thái FAILED vào database nhưng database đang lỗi. Caller có nhận được lỗi thứ cấp không? Hãy thiết kế fallback không che mất lỗi gốc.

**Gợi ý:** Caller của void async không có handle, không dựa vào exception truyền ngược; dùng logging fallback và quan sát trạng thái công việc riêng.

### Bài 7 — Chạy demo

Chạy `AsyncHandlerDemo` bằng lệnh Maven ở mục 6. Giải thích vì sao hai method đều ném lỗi nhưng chỉ có một dòng `Handler: ...`.

**Gợi ý:** `failVoid` gửi lỗi tới handler. `failFuture` gửi lỗi qua Future mà caller `join()` quan sát.

## 19. Checklist triển khai

Trước khi đưa handler `@Async` vào production:

1. `@EnableAsync` có hiệu lực ở context chứa bean; caller gọi qua Spring proxy.
2. Class cấu hình implement `AsyncConfigurer` và override đúng `getAsyncUncaughtExceptionHandler()`.
3. Handler ghi exception type/stack trace và method; không log secret hoặc nguyên payload.
4. Không nhầm exception của method `void` với Future-typed method hoặc lỗi gửi việc.
5. Executor có worker/queue giới hạn và rejection policy rõ ràng.
6. Không đưa cả request object hoặc thread-local nhạy cảm sang task/handler không có cơ chế propagation.
7. Nếu handler dùng bean khác, chú ý AsyncConfigurer khởi tạo sớm, ưu tiên dependency lazy khi cần.
8. Handler có fallback và không retry vô hạn, không chặn worker quá lâu.
9. Có correlation ID/job ID, phân biệt accepted/started/succeeded/failed.
10. Nếu nghiệp vụ yêu cầu chắc chắn thực hiện qua restart, sử dụng lưu trữ/hàng đợi bền vững.
11. Có integration test kiểm tra handler thật và Future error, không chỉ kiểm tra annotation có mặt.
12. Biết rằng handler không rollback transaction của caller hoặc side effect của hệ thống khác.

## 20. Điều cần nhớ

- **`AsyncConfigurer.getAsyncUncaughtExceptionHandler()`** là API chuẩn để cấu hình handler cho ngoại lệ không chuyển được về caller của method **`@Async void`**.
- Handler nhận **`Throwable`, `Method` và `Object... params`**, cần xử lý và ghi nhận một cách an toàn, không log dữ liệu nhạy cảm.
- **Future và CompletableFuture có kênh báo lỗi riêng**; đừng mong handler void nhận mọi lỗi bất đồng bộ.
- **Lỗi từ chối executor, sai cấu hình bean và self-invocation** có đường xử lý khác với exception của thân method đã chạy.
- **`@EnableAsync`, Spring-managed bean và lời gọi qua proxy** là điều kiện cốt lõi để handler được sử dụng đúng trong proxy mode.
- **Configurer có thể khởi tạo sớm**; tránh dependency phức tạp hoặc lấy bean proxy ngay trong quá trình bootstrap.
- **Handler không phải hệ thống retry/job queue bền vững.** Phải thiết kế quan sát trạng thái, idempotency, timeout và bù trừ theo yêu cầu nghiệp vụ.

### Tài liệu chính thức

- [Spring Framework Javadoc — AsyncConfigurer](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/AsyncConfigurer.html)
- [Spring Framework Javadoc — EnableAsync](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework Javadoc — AsyncUncaughtExceptionHandler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/interceptor/AsyncUncaughtExceptionHandler.html)
- [Spring Framework Javadoc — SimpleAsyncUncaughtExceptionHandler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/interceptor/SimpleAsyncUncaughtExceptionHandler.html)
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Java SE 17 Javadoc — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-005">exceptionally(), handle() và whenComplete()</a>

<details>
<summary>Xem chi tiết</summary>

# exceptionally(), handle() và whenComplete() trong CompletableFuture và Spring @Async

Khi lập trình đồng thời với Spring Framework, một phương thức `@Async` có thể trả về `CompletableFuture<T>` để caller nhận kết quả sau hoặc ghép các bước xử lý. Nhưng tác vụ có thể lỗi: truy vấn cơ sở dữ liệu thất bại, dịch vụ ngoài timeout, tham số không hợp lệ, hoặc callback trong chuỗi pipeline tự ném exception. Ba API **`exceptionally()`**, **`handle()`** và **`whenComplete()`** giải quyết ba nhu cầu khác nhau: **phục hồi lỗi**, **biến đổi kết quả bất kể thành công hay thất bại**, và **quan sát kết quả mà vẫn giữ nguyên trạng thái hoàn thành**.

Điểm quan trọng nhất: cả ba API tạo **một completion stage phụ thuộc mới**. Chúng không tự sửa kết quả của stage gốc. Thứ tự gắn callback quyết định callback nhìn thấy kết quả **trước** hay **sau** khi đã phục hồi lỗi. Bài học thuộc nhóm **Xử lý ngoại lệ**, tập trung vào hợp đồng Java 17+ và cách sử dụng trong ứng dụng Spring `@Async`.

## 1. Bạn cần nắm được điều gì?

Sau bài này, bạn có thể:

- Giải thích `CompletableFuture` hoàn thành thành công, hoàn thành lỗi hoặc bị cancel, và exception được lưu/truyền giữa các stage như thế nào.
- Phân biệt chính xác thời điểm chạy, kiểu callback, kiểu trả về và khả năng phục hồi của `exceptionally`, `handle`, `whenComplete`.
- Biết lựa chọn API cho retry/fallback, đổi kiểu kết quả, logging/metrics, hoặc giữ lỗi để tầng trên quyết định.
- Hiểu trường hợp callback tự ném exception, đặc biệt quy tắc **giữ lỗi gốc của `whenComplete`** khi nguồn vốn đã lỗi.
- Biết rằng callback dạng không có hậu tố `Async` có thể chạy trên thread hoàn tất stage hoặc thread gắn callback, không có cam kết một worker mới.
- So sánh `get()`, `join()`, `ExecutionException`, `CompletionException` và `CancellationException`.
- Dùng trong Spring `@Async` có `ThreadPoolTaskExecutor`, phân biệt **lỗi trong task** với **rejection khi gửi task**.
- Tránh nuốt lỗi, fallback sai nghiệp vụ, logging trùng, blocking callback, mất thread context và suy luận sai về cancellation/timeout.
- Chạy được một ví dụ Java 17+ có kết quả xác định và viết test không phụ thuộc `Thread.sleep()`.

## 2. Nền tảng: CompletionStage và ba trạng thái kết thúc

`CompletableFuture<T>` thực hiện cả `Future<T>` và `CompletionStage<T>`. Một Future ban đầu có thể chưa hoàn thành; khi kết thúc, nó có thể:

1. **Hoàn thành bình thường:** chứa một giá trị `T`; giá trị này **có thể là `null`**.
2. **Hoàn thành ngoại lệ:** chứa một nguyên nhân thất bại, ví dụ `IllegalArgumentException` hoặc `IOException` đã được bọc ở mức phù hợp.
3. **Bị hủy:** `cancel(...)` của `CompletableFuture` về bản chất dẫn tới một dạng hoàn thành ngoại lệ với `CancellationException`.

Một giai đoạn có thể kết thúc thành công hoặc lỗi trong lúc một stage khác đang chờ nó:

~~~text
CompletableFuture<T> source
       |
       +--- completed normally (value T)
       |
       +--- completed exceptionally (Throwable)
       |
       +--- cancelled (CancellationException)
       |
       +--- dependent stage: callback tùy loại
                |
                +--- completed normally (T/U)
                |
                +--- completed exceptionally (old/new cause)
~~~

Một stage phụ thuộc được tạo bằng `thenApply` thường chỉ xử lý khi nguồn **thành công**. Nếu nguồn lỗi, callback xử lý giá trị không chạy; lỗi có thể lan tiếp trong pipeline. Ba phương thức trong chủ đề giúp định nghĩa rõ cách ứng dụng sẽ xử lý hoặc quan sát lỗi.

### Hai nguyên tắc trước khi viết callback

- **Kết quả `null` không đồng nghĩa thất bại.** Với `handle((value, error) -> ...)` hay `whenComplete((value, error) -> ...)`, kiểm tra `error == null` để biết nguồn thành công, không kiểm tra riêng `value == null`.
- **Stage gốc và stage mới khác nhau.** `CompletableFuture<Integer> recovered = source.exceptionally(...)` không “chữa” `source`; `source` vẫn có thể hoàn thành exceptionally trong khi `recovered` thành công với fallback.

## 3. Bảng so sánh quan trọng nhất

| Tiêu chí | `exceptionally(fn)` | `handle(fn)` | `whenComplete(action)` |
| --- | --- | --- | --- |
| Khi nào callback được gọi? | Chỉ khi nguồn hoàn thành **ngoại lệ** | Cả thành công lẫn lỗi | Cả thành công lẫn lỗi |
| Hàm callback | `Function<Throwable, ? extends T>` | `BiFunction<? super T, Throwable, ? extends U>` | `BiConsumer<? super T, ? super Throwable>` |
| Dữ liệu callback nhận | Exception | Giá trị hoặc `null`, cùng exception hoặc `null` | Giá trị hoặc `null`, cùng exception hoặc `null` |
| Callback có trả giá trị? | Có, phải phù hợp kiểu `T` | Có, cho phép chuyển sang kiểu `U` | Không, chỉ thực hiện side effect |
| Stage mới khi nguồn lỗi và callback trả bình thường | **Thành công với fallback** | **Thành công với giá trị mới** | **Vẫn lỗi như nguồn** |
| Stage mới khi nguồn thành công | Giữ nguyên giá trị nguồn; callback không chạy | Kết quả do callback tính toán | Giữ nguyên giá trị nguồn nếu action không lỗi |
| Có thể đổi kiểu `T -> U`? | Không, cùng kiểu `T` | Có | Không, cùng kiểu `T` |
| Có thể chỉ quan sát lỗi mà không “nuốt” lỗi? | Không tự nhiên; phải ném lại | Có nhưng dễ đổi semantics nếu trả fallback | **Có, lựa chọn phù hợp nhất** |
| Nếu callback tự ném lỗi | Stage mới thất bại vì callback | Stage mới thất bại vì callback | Nếu nguồn thành công, stage mới thất bại vì callback; nếu nguồn đã lỗi, lỗi nguồn được ưu tiên |
| Mục tiêu chính | Error recovery/fallback | Kết hợp success/failure, map kết quả | Logging, metric, tracing, cleanup nhẹ |

**Quy tắc ghi nhớ:** `exceptionally` giống một nhánh `catch` với giá trị thay thế; `handle` giống `try/catch` có tính toán giá trị cuối trên **mọi đường**; `whenComplete` giống một callback quan sát `finally` nhưng **không phải `finally` của Java ở mọi khía cạnh**: nếu observer tự lỗi trên đường thành công, stage mới cũng lỗi.

## 4. exceptionally(): chỉ chạy trên đường thất bại

Chữ ký trong `CompletableFuture<T>`:

~~~java
public CompletableFuture<T> exceptionally(
        java.util.function.Function<Throwable, ? extends T> fn)
~~~

Nếu stage nguồn thành công, `fn` **không chạy** và stage mới nhận cùng giá trị. Nếu nguồn thất bại (bao gồm cancel), `fn` chạy với `Throwable` của completion và trả một giá trị thay thế cùng kiểu `T`. Khi callback kết thúc bình thường, **stage mới trở thành thành công**, không còn giữ trạng thái lỗi trước đó.

Ví dụ Java 17:

~~~java
CompletableFuture<String> source =
        CompletableFuture.failedFuture(
                new IllegalStateException("service unavailable"));

CompletableFuture<String> fallback =
        source.exceptionally(error -> "cached-value");

System.out.println(fallback.join());             // cached-value
System.out.println(source.isCompletedExceptionally()); // true
~~~

Đây là **đoạn Java có thể đặt trong method với import `java.util.concurrent.CompletableFuture`**. Nó cho thấy việc phục hồi chỉ xảy ra ở `fallback`, còn `source` vẫn lỗi. Nếu exception handler tự ném exception thì stage `fallback` cũng kết thúc lỗi.

### Chỉ fallback khi nguyên nhân có thể xử lý

Một anti-pattern thường gặp:

~~~java
CompletableFuture<Order> safe =
        loadOrder(id).exceptionally(error -> new Order());
~~~

Đây là **mã minh họa chưa độc lập**, `Order`/`loadOrder` là thành phần ứng dụng. Nếu lỗi là mất kết nối DB, thông tin đơn hàng không tồn tại, bug lập trình hoặc lỗi bảo mật, trả đơn hàng rỗng có thể khiến hệ thống diễn giải sai. Nên xác định loại lỗi có thể phục hồi:

~~~java
CompletableFuture<Price> safe =
        priceClient.fetch(sku).exceptionally(error -> {
            Throwable cause = unwrapCompletion(error);
            if (cause instanceof PriceServiceUnavailableException) {
                return priceCache.getIfPresent(sku);
            }
            throw new java.util.concurrent.CompletionException(cause);
        });
~~~

Đây là **mã thiết kế minh họa trong ứng dụng**, cần định nghĩa `Price`, `PriceServiceUnavailableException`, `priceClient`, `priceCache` và helper `unwrapCompletion`. Fallback cache cũng có thể trả `null`; phải xác định chính sách nghiệp vụ nếu cache miss. Không tự động biến mọi exception thành giá trị mặc định.

### Thứ tự hồi phục lỗi

~~~java
CompletableFuture<Integer> recovered =
        CompletableFuture.<Integer>failedFuture(
                new IllegalArgumentException("bad"))
                .exceptionally(error -> 5)
                .thenApply(value -> value * 2);
~~~

`recovered.join()` bằng **10** vì `exceptionally` biến lỗi thành giá trị 5, sau đó `thenApply` có thể chạy. Nhưng nếu đặt `thenApply` **trước** `exceptionally`, khi nguồn lỗi thì `thenApply` bị bỏ qua và fallback sẽ là giá trị nhận trực tiếp ở `exceptionally`, không được nhân đôi.

## 5. handle(): xử lý cả thành công lẫn lỗi và cho phép đổi kiểu

Chữ ký điển hình:

~~~java
public <U> CompletableFuture<U> handle(
        java.util.function.BiFunction<? super T, Throwable, ? extends U> fn)
~~~

`handle` luôn nhận được kết quả hoặc lỗi của nguồn và có thể trả về giá trị **kiểu `U`**, nên rất phù hợp để biểu diễn một **Result object**, chuyển giá trị thành thông báo hoặc áp quy tắc fallback cho cả hai đường.

Ví dụ:

~~~java
CompletableFuture<Integer> number =
        CompletableFuture.completedFuture(42);

CompletableFuture<String> result =
        number.handle((value, error) -> {
            if (error != null) {
                return "failed";
            }
            return "value=" + value;
        });

System.out.println(result.join()); // value=42
~~~

Với nguồn lỗi, callback vẫn chạy và có thể trả `"failed"`; stage mới hoàn thành **bình thường**. Khi cần giữ lỗi cho caller, có thể ném lại một exception phù hợp thay vì vô điều kiện trả fallback.

### Phân biệt null với lỗi

~~~java
CompletableFuture<String> nullable =
        CompletableFuture.completedFuture(null);

CompletableFuture<String> state =
        nullable.handle((value, error) ->
                error == null ? "success-even-if-null" : "failed");
~~~

`state.join()` là `success-even-if-null`. Nếu dùng `value == null` để suy ra lỗi, ứng dụng sẽ làm sai với mọi API cho phép kết quả null và `CompletableFuture<Void>`, bởi task kiểu Void khi hoàn tất thành công thường có value null.

### Dùng handle để chuẩn hóa kết quả ứng dụng

Giả sử một API cần phân biệt trạng thái thay vì ném lỗi xuống tất cả các tầng, có thể tạo một lớp `Outcome<T>` chứa `value` hoặc `failure` rồi dùng:

~~~java
CompletableFuture<Outcome<Customer>> outcome =
        customerService.load(id)
                .handle((value, error) ->
                        error == null
                                ? Outcome.success(value)
                                : Outcome.failure(unwrapCompletion(error)));
~~~

Đây là **đoạn tích hợp**, cần các class `Outcome`, `Customer` và service thật; bản thân nó không biên dịch độc lập. Quan trọng là khi `handle` trả `Outcome.failure(...)` thì **stage ở cấp Future thành công**, nhưng **nghiệp vụ có thể thất bại**. Người tiêu thụ phải kiểm tra `Outcome`, không chỉ `isCompletedExceptionally()`.

### Khi handle tự ném exception

Nếu callback `handle` ném `IllegalStateException` trong bất kỳ nhánh nào, stage mới hoàn thành exceptionally bởi lỗi đó. Lỗi nguồn trước đây không tự được giữ nguyên nếu callback chọn ném lỗi mới. Vì thế nên thiết kế chuyển đổi exception cẩn thận, tránh che nguyên nhân ban đầu.

## 6. whenComplete(): quan sát và giữ nguyên kết quả hoặc lỗi nguồn

Chữ ký điển hình:

~~~java
public CompletableFuture<T> whenComplete(
        java.util.function.BiConsumer<? super T, ? super Throwable> action)
~~~

`whenComplete` được gọi cả khi thành công lẫn lỗi, nhưng callback **không trả giá trị**. Stage mới thường **mang cùng kết quả hoặc cùng thất bại** với nguồn. Điều này phù hợp cho logging, metrics, tracing, instrumentation và cleanup ngắn ở lúc công việc hoàn tất.

~~~java
CompletableFuture<String> original =
        CompletableFuture.completedFuture("ready");

CompletableFuture<String> observed =
        original.whenComplete((value, error) -> {
            if (error == null) {
                System.out.println("completed");
            } else {
                System.err.println("failed: " + error);
            }
        });
~~~

`observed.join()` vẫn là `"ready"`, miễn callback không lỗi. Nếu nguồn đã hoàn thành exceptionally, `observed.join()` cũng sẽ báo exception đó, ngay cả khi callback chỉ in log và không ném.

### Không dùng whenComplete để “trả fallback”

Sai kỳ vọng:

~~~java
CompletableFuture<Integer> result =
        failedIntegerFuture.whenComplete((value, error) -> {
            if (error != null) {
                System.out.println("Using default 0");
            }
        });
~~~

Đây là **đoạn minh họa** với `failedIntegerFuture` đã tồn tại. Nó **không** làm `result` có giá trị 0. `whenComplete` không có giá trị trả về để thay thế. Nếu cần fallback hãy dùng `exceptionally`; nếu cần ánh xạ hai nhánh hãy dùng `handle`.

### Nếu observer tự ném exception: có quy tắc ưu tiên

Theo hợp đồng `CompletionStage`:

1. Nguồn **thành công**, observer ném lỗi -> stage phụ thuộc **thất bại vì lỗi observer**.
2. Nguồn **đã thất bại**, observer cũng ném lỗi -> stage phụ thuộc **ưu tiên giữ lỗi nguồn** thay vì thay bằng lỗi observer.
3. Nguồn hoàn thành bất kỳ trạng thái nào, observer kết thúc bình thường -> stage phụ thuộc giữ đúng trạng thái nguồn.

Ví dụ:

~~~java
CompletableFuture<Integer> normal =
        CompletableFuture.completedFuture(3);

CompletableFuture<Integer> brokenObserver =
        normal.whenComplete((value, error) -> {
            throw new IllegalStateException("logging unavailable");
        });
// brokenObserver thất bại vì IllegalStateException.

CompletableFuture<Integer> failed =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("invalid"));

CompletableFuture<Integer> stillOriginalError =
        failed.whenComplete((value, error) -> {
            throw new IllegalStateException("observer also failed");
        });
// stillOriginalError vẫn ưu tiên IllegalArgumentException của nguồn.
~~~

Đây là **mã Java trong method**. Quy tắc này hữu ích khi phân tích nguyên nhân thất bại, nhưng đừng dựa vào nó để cho phép logging callback ném lỗi tùy ý. Một bộ quan sát tốt nên không làm phát sinh exception thứ cấp và nên nhẹ.

### whenComplete không đồng nhất với finally

`finally` trong cú pháp Java chạy khi rời khối `try` tương ứng; `whenComplete` chạy khi **stage được gắn vào** hoàn tất. Nếu callback `whenComplete` được đăng ký *sau* một `exceptionally` đã phục hồi, nó sẽ thấy **đường thành công**. Nếu đăng ký *trước* `exceptionally`, nó thấy **lỗi gốc**.

## 7. Ví dụ Java 17+ chạy độc lập: so sánh cả ba phương thức

Ví dụ sau dùng những future hoàn thành sẵn để có stdout **xác định**, không phụ thuộc scheduler hoặc thời gian ngủ. Nó cũng kiểm tra ưu tiên lỗi khi `whenComplete` tự ném và cancellation.

Tệp **CompletionHandlersDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class CompletionHandlersDemo {
    public static void main(String[] args) {
        CompletableFuture<Integer> success =
                CompletableFuture.completedFuture(10);
        AtomicInteger recoveryCalls = new AtomicInteger();

        CompletableFuture<Integer> unchanged =
                success.exceptionally(error -> {
                    recoveryCalls.incrementAndGet();
                    return -1;
                });

        System.out.println("success=" + unchanged.join());
        System.out.println("exceptionallyCalled=" + recoveryCalls.get());

        CompletableFuture<Integer> failed =
                CompletableFuture.failedFuture(
                        new IllegalArgumentException("bad input"));

        CompletableFuture<Integer> recovered =
                failed.exceptionally(error -> 7);

        System.out.println("recovered=" + recovered.join());
        System.out.println("originalFailed="
                + failed.isCompletedExceptionally());

        CompletableFuture<String> mapped =
                success.handle((value, error) ->
                        error == null ? "value=" + value : "error");
        CompletableFuture<String> mappedFailure =
                failed.handle((value, error) ->
                        error == null ? "value=" + value : "failure");

        System.out.println("handleSuccess=" + mapped.join());
        System.out.println("handleFailure=" + mappedFailure.join());

        AtomicBoolean noticed = new AtomicBoolean();
        CompletableFuture<Integer> observed =
                failed.whenComplete((value, error) ->
                        noticed.set(error != null));

        try {
            observed.join();
        } catch (CompletionException ex) {
            System.out.println("observedCause="
                    + ex.getCause().getClass().getSimpleName());
        }
        System.out.println("observerCalled=" + noticed.get());

        CompletableFuture<Integer> newError =
                success.whenComplete((value, error) -> {
                    throw new IllegalStateException("observer failed");
                });
        try {
            newError.join();
        } catch (CompletionException ex) {
            System.out.println("observerOnSuccess="
                    + ex.getCause().getClass().getSimpleName());
        }

        CompletableFuture<Integer> originalWins =
                failed.whenComplete((value, error) -> {
                    throw new IllegalStateException("second failure");
                });
        try {
            originalWins.join();
        } catch (CompletionException ex) {
            System.out.println("observerOnFailure="
                    + ex.getCause().getClass().getSimpleName());
        }

        CompletableFuture<String> nullSuccess =
                CompletableFuture.completedFuture(null);
        System.out.println("nullIsSuccess="
                + nullSuccess.handle((value, error) -> error == null).join());

        CompletableFuture<Integer> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        System.out.println("cancelled=" + cancelled.isCancelled());
        System.out.println("cancelFallback="
                + cancelled.exceptionally(error -> 99).join());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CompletionHandlersDemo.java
java CompletionHandlersDemo
~~~

Stdout trong lần chạy thành công:

~~~text
success=10
exceptionallyCalled=0
recovered=7
originalFailed=true
handleSuccess=value=10
handleFailure=failure
observedCause=IllegalArgumentException
observerCalled=true
observerOnSuccess=IllegalStateException
observerOnFailure=IllegalArgumentException
nullIsSuccess=true
cancelled=true
cancelFallback=99
~~~

**Giải thích từng nhóm kết quả:**

- Với `success`, callback `exceptionally` không chạy nên counter vẫn bằng 0.
- Với `failed`, `exceptionally` cho giá trị 7 ở stage mới; `failed` vẫn exceptional, vì recovery **không sửa stage nguồn**.
- `handle` biến thành công thành chuỗi `value=10`, biến lỗi thành chuỗi `failure`; cả hai stage mới đều thành công.
- `whenComplete` ghi nhận exception nhưng không phục hồi; `observed.join()` ném `CompletionException` với cause `IllegalArgumentException`.
- Nếu observer lỗi trên nguồn thành công, stage mới mang lỗi observer; nếu nguồn vốn lỗi, source exception được ưu tiên.
- `CompletableFuture.completedFuture(null)` vẫn là thành công: `handle` nhìn `error==null`.
- `cancelled.exceptionally(...)` có thể trả fallback 99 vì cancellation là một loại exceptional completion. Điều này **không** có nghĩa cancel đã được hoàn tác trên `cancelled`: `cancelled.isCancelled()` vẫn true.

Ví dụ sử dụng Java 17+, có class/file trùng tên, không phụ thuộc Spring và không dùng `Thread.sleep()`. Những đoạn tích hợp Spring ở các phần sau cần dependency của dự án.

## 8. Thứ tự callback quyết định lỗi nào được nhìn thấy

Giả sử một future nguồn thất bại. Hai pipeline sau **khác nhau về nghĩa**:

~~~java
CompletableFuture<String> a =
        source.whenComplete((value, error) -> {
            if (error != null) {
                recordOriginalError(error);
            }
        }).exceptionally(error -> "fallback");

CompletableFuture<String> b =
        source.exceptionally(error -> "fallback")
              .whenComplete((value, error) -> {
                  recordFinalState(value, error);
              });
~~~

Đây là **đoạn code minh họa với biến/hàm ứng dụng** `source`, `recordOriginalError` và `recordFinalState`. Ở `a`, observer chạy **trước recovery** nên thấy lỗi gốc, trong khi consumer cuối nhận fallback. Ở `b`, observer chạy **sau recovery** nên có thể thấy giá trị `"fallback"` và `error==null`.

Nếu cần vừa ghi lỗi gốc vừa báo kết quả cuối cùng, có thể chủ động có hai stage observer khác nhau với tên metric khác nhau. Không gọi cả hai chỉ để ghi cùng một lỗi, vì dễ đếm trùng.

### Callback phục hồi đặt sớm hay muộn?

~~~java
CompletableFuture<Integer> a =
        source.thenApply(value -> value * 2)
              .exceptionally(error -> 5);

CompletableFuture<Integer> b =
        source.exceptionally(error -> 5)
              .thenApply(value -> value * 2);
~~~

Với `source` lỗi, `a` cho 5 vì `thenApply` bị bỏ qua; `b` cho 10 vì `exceptionally` phục hồi thành 5 trước, rồi `thenApply` chạy. Hai biểu thức đều hợp lệ khi `source` có kiểu `CompletableFuture<Integer>`.

**Bài học:** mỗi callback làm việc với **stage ngay bên trái** của nó, không phải “toàn bộ pipeline” một cách tự động. Muốn tạo error boundary cho toàn bộ chuỗi, gắn `exceptionally` ở điểm phù hợp sau tất cả thao tác cần bao phủ.

## 9. Gỡ lớp bao exception: CompletionException và ExecutionException

Khi một stage lỗi, Java có thể truyền cause dưới lớp bọc `CompletionException`. Khi consumer gọi `join()`, lỗi thường xuất hiện dưới dạng unchecked `CompletionException` với `getCause()` là exception gốc. Khi consumer gọi `get()`, lỗi tác vụ thường được báo bằng checked `ExecutionException`.

~~~java
CompletableFuture<String> bad =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("invalid"));

try {
    bad.join();
} catch (CompletionException ex) {
    Throwable root = ex.getCause();
    System.err.println(root.getClass().getSimpleName());
}

try {
    bad.get();
} catch (java.util.concurrent.ExecutionException ex) {
    Throwable root = ex.getCause();
    System.err.println(root.getClass().getSimpleName());
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
}
~~~

Đoạn Java này dùng trong method có imports `CompletableFuture` và `CompletionException`; nó thể hiện cả hai đường lỗi. `get(timeout, unit)` còn có thể ném `TimeoutException`. `CancellationException` có thể được ném trực tiếp khi future bị cancel.

**Không được giả định** đối số `error` của callback luôn có một lớp `CompletionException`. Nếu nguồn là `failedFuture(new IllegalArgumentException(...))`, callback có thể nhận chính `IllegalArgumentException`. Nếu lỗi lan qua stage phụ thuộc, có thể có `CompletionException`. Vì vậy khi cần phân loại lỗi, hãy unwrap có giới hạn.

Mẫu helper:

~~~java
static Throwable unwrapCompletion(Throwable throwable) {
    Throwable current = throwable;
    while (current instanceof CompletionException
            || current instanceof java.util.concurrent.ExecutionException) {
        if (current.getCause() == null) {
            break;
        }
        current = current.getCause();
    }
    return current;
}
~~~

Đây là **method Java minh họa**, cần import `CompletionException` và có thể đặt trong utility class. Nó chỉ gỡ hai wrapper đại diện cho future completion, không tùy tiện gỡ mọi exception có cause. Khi logging nên giữ cả stack trace có wrapper lẫn root cause nếu cần điều tra.

## 10. Exception của Spring @Async khác với submission rejection

Trong Spring Framework, method được đánh dấu `@Async` có thể trả `CompletableFuture<T>` nếu được gọi qua Spring proxy và `@EnableAsync` đã bật. Có hai điểm lỗi khác nhau:

1. **Lỗi khi submit:** executor không thể nhận task vì bão hòa/shutdown, thường ném `TaskRejectedException` (đối với `ThreadPoolTaskExecutor` và `AbortPolicy`) ngay tại lời gọi proxy. Lúc này caller có thể **chưa nhận được future** để gắn callback.
2. **Lỗi trong task đã nhận:** body ném exception sau khi executor nhận việc, hoặc một stage sau tự lỗi. Future trả về sẽ hoàn thành exceptionally, và `exceptionally`/`handle`/`whenComplete` có thể xử lý.

Ví dụ method Spring:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PricingAsyncService {

    @Async("pricingExecutor")
    public CompletableFuture<Integer> calculatePrice(int units) {
        if (units < 0) {
            throw new IllegalArgumentException("units must be non-negative");
        }
        return CompletableFuture.completedFuture(units * 100);
    }
}
~~~

Đây là **Spring service** cần `spring-context`, executor tên `pricingExecutor` và lời gọi từ một bean khác qua proxy. Bên trong target method, `completedFuture(...)` là handle tạm để thỏa chữ ký; Spring proxy trao cho caller một handle đại diện cho tác vụ thực tế. Không suy ra phương thức này chạy đồng bộ chỉ vì nó tạo completedFuture trong worker.

Một consumer khác:

~~~java
CompletableFuture<Integer> result;
try {
    result = pricingAsyncService.calculatePrice(3);
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Executor không nhận task: phản hồi overload/đưa vào kho bền vững.
    throw ex;
}

CompletableFuture<Integer> visible =
        result.whenComplete((value, error) -> {
            if (error != null) {
                logJobFailure(error);
            }
        }).exceptionally(error -> 0);
~~~

Đây là **đoạn code tích hợp** với `pricingAsyncService` đã inject và `logJobFailure` do ứng dụng định nghĩa. Nếu `calculatePrice(-1)` bị lỗi trong worker, observer có thể log exception và `exceptionally` có thể trả fallback 0. Tuy nhiên **trả giá 0 khi tính lỗi là ví dụ về semantics dễ sai nghiệp vụ**; trong hệ thống bán hàng, phải có chính sách rõ ràng hoặc giữ nguyên lỗi thay vì tự đưa giá 0.

### Cấu hình executor minh họa

~~~java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class PricingAsyncConfig {

    @Bean(name = "pricingExecutor")
    public ThreadPoolTaskExecutor pricingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("pricing-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
~~~

Đây là **Spring configuration class** không có `main` độc lập. Cấu hình queue/rejection chỉ để minh họa; trong production phải sizing theo workload và giám sát queue, active thread, response time, timeout và rejection.

Một chi tiết: `@Async void` **không** trả `CompletableFuture`, nên không có nơi để caller gọi ba phương thức này. Lỗi không bắt trong body của `@Async void` thường đi qua `AsyncUncaughtExceptionHandler`. Chọn kiểu `CompletableFuture<T>` khi caller cần quan sát hoặc compose kết quả.

## 11. Chọn callback nào cho logging, metrics và fallback?

### Nhu cầu A: log mọi lần hoàn tất mà không thay giá trị

~~~java
CompletableFuture<Order> tracked =
        orderService.load(orderId)
                .whenComplete((order, error) -> {
                    if (error == null) {
                        metrics.incrementSuccess();
                    } else {
                        metrics.incrementFailure();
                    }
                });
~~~

Đây là **mã minh họa** với `Order`, `orderService` và `metrics` do ứng dụng định nghĩa. `whenComplete` giữ nguyên kết quả/lỗi từ `load` nếu callback metrics chạy bình thường. Metric handler phải nhanh, thread-safe, không làm I/O chậm hoặc ném exception không kiểm soát.

### Nhu cầu B: lỗi mạng có fallback được xác định

~~~java
CompletableFuture<Stock> current =
        stockService.fetch(sku)
                .exceptionally(error -> {
                    Throwable cause = unwrapCompletion(error);
                    if (cause instanceof RemoteStockTimeoutException) {
                        return lastKnownStock(sku);
                    }
                    throw new CompletionException(cause);
                });
~~~

Đây là đoạn **mô hình thiết kế**, các type/service/helper ngoài Java cần được khai báo trong dự án. Nếu fallback stale làm vi phạm nghiệp vụ, đừng dùng. Nên gắn metric để phân biệt số lần trả dữ liệu thật và số lần dùng cache.

### Nhu cầu C: API phải trả kết quả chuẩn hóa cả khi lỗi

~~~java
CompletableFuture<ApiResult<Report>> api =
        reportService.generate(reportId)
                .handle((report, error) -> {
                    if (error == null) {
                        return ApiResult.ok(report);
                    }
                    return ApiResult.failure(
                            classify(unwrapCompletion(error)));
                });
~~~

`ApiResult`, `Report` và `classify` là thành phần ứng dụng. Stage `api` hoàn thành **bình thường** với `ApiResult.failure` nếu `handle` trả về bình thường. Tầng HTTP phải xem trạng thái `ApiResult` và chọn status code phù hợp; `future.isCompletedExceptionally()==false` không có nghĩa nghiệp vụ thành công.

### Nhu cầu D: log lỗi rồi để lỗi truyền tiếp

~~~java
CompletableFuture<Report> visible =
        reportService.generate(reportId)
                .whenComplete((report, error) -> {
                    if (error != null) {
                        logFailure(unwrapCompletion(error));
                    }
                });
~~~

Đây là trường hợp `whenComplete` thường thích hợp hơn `exceptionally`: callback quan sát lỗi nhưng **không thay lỗi bằng một giá trị giả**. Nếu chỉ muốn log lỗi và không quan tâm đường thành công, vẫn có thể dùng `whenComplete` với nhánh điều kiện.

## 12. async variants và thread nào thực thi callback?

Các phương thức có nhiều biến thể:

- `exceptionally(fn)`, `handle(fn)`, `whenComplete(action)`.
- `exceptionallyAsync(fn)`, `handleAsync(fn)`, `whenCompleteAsync(action)`.
- Các overload `...Async(fn, Executor executor)` cho phép chọn executor cụ thể.

Với phương thức **không có hậu tố Async**, callback có thể chạy trên thread hoàn thành stage hoặc thread đăng ký callback nếu stage đã hoàn tất. Không nên ghi giả định “tất cả callback đều chạy trên worker của @Async” vào logic nghiệp vụ. Với biến thể `...Async`, công việc callback được lên lịch qua executor mặc định của stage (thường `ForkJoinPool.commonPool()` đối với `CompletableFuture` chuẩn) hoặc executor được truyền vào.

~~~java
CompletableFuture<String> result =
        source.handleAsync(
                (value, error) -> error == null
                        ? value.toUpperCase()
                        : "FALLBACK",
                callbackExecutor);
~~~

Đây là **đoạn minh họa** với `source` kiểu `CompletableFuture<String>` và `callbackExecutor` kiểu `Executor`. Chọn executor riêng có lợi khi callback có việc CPU/I/O đáng kể và cần cách ly khỏi thread đang hoàn tất nguồn; nhưng cũng thêm queue, rejection, shutdown và yêu cầu quản lý context.

Cần tránh hai hiểu lầm:

1. **Hậu tố Async không bảo đảm tự tạo thread mới.** Nó dùng cơ chế scheduling đã chỉ định; một executor có thể chọn thực thi trực tiếp trên caller hoặc bị bão hòa.
2. **Không có hậu tố Async không có nghĩa callback luôn chạy đồng bộ ở thời điểm bạn đăng ký.** Nếu nguồn chưa hoàn tất, callback sẽ chạy khi nguồn hoàn tất; nó chỉ không yêu cầu lịch riêng qua một executor khác.

### Cảnh báo blocking callback

Nếu dùng `whenComplete` để gọi HTTP, ghi file hoặc chờ một `Future.join()` khác, callback có thể block chính thread đang hoàn tất tác vụ nguồn. Điều này gây tăng latency cho các stage khác, giữ worker lâu hơn và có thể starvation khi callback chờ tác vụ dùng chung một pool nhỏ. Ưu tiên callback nhanh; khi có việc nặng, bố trí executor/queue và quan sát overload một cách riêng biệt.

## 13. Lỗi mới sinh trong callback: đừng làm mất nguyên nhân gốc

Ba API có điểm chung: nếu callback tính toán gặp exception chưa được xử lý, **stage trả về có thể hoàn thành exceptionally**. Nhưng `whenComplete` có luật ưu tiên lỗi gốc khi cả nguồn và observer đều lỗi, trong khi `handle` và `exceptionally` là nơi chủ động tính toán kết quả mới, nên lỗi mới của chúng có thể thay thế đường lỗi trước.

Một cách đặt callback có chủ ý:

~~~java
CompletableFuture<Result> pipeline =
        doRemoteWork()
                .whenComplete((value, error) -> auditResult(value, error))
                .handle((value, error) -> {
                    if (error == null) {
                        return Result.success(value);
                    }
                    return Result.failure(unwrapCompletion(error));
                });
~~~

Đây là **mẫu thiết kế** cần `Result`, `doRemoteWork` và `auditResult` do ứng dụng cung cấp. Nếu `auditResult` ném exception trên đường nguồn thành công, stage `whenComplete` sẽ thất bại vì observer và `handle` thấy lỗi observer, không phải lỗi làm việc từ xa. Vì vậy logging, tracing và metric phải tránh exception không kiểm soát.

Nếu một lỗi được phục hồi ở `exceptionally` trước khi đi qua một `whenComplete` khác, observer sau đó sẽ thấy **thành công**, dù thao tác nguồn từng lỗi. Muốn ghi cả lỗi gốc và phục hồi, phân biệt metric theo stage.

## 14. Cancellation và timeout cũng là nguồn exceptional completion

`CompletableFuture.cancel(true)` không giống việc chắc chắn interrupt một worker: với `CompletableFuture` chuẩn, tham số `mayInterruptIfRunning` không dùng để điều khiển interruption của task. Cancellation biểu diễn exceptional completion, nên `exceptionally`, `handle` hoặc `whenComplete` có thể được kích hoạt.

~~~java
CompletableFuture<String> source = new CompletableFuture<>();
source.cancel(false);

CompletableFuture<String> converted =
        source.exceptionally(error -> "cancelled-or-failed");
~~~

`converted.join()` cho `"cancelled-or-failed"`, nhưng `source.isCancelled()` vẫn `true`. Tự động phục hồi mọi cancellation thành giá trị thành công có thể **che đi ý định hủy** của caller. Nếu cancel có ý nghĩa dừng quy trình, nên phân biệt `CancellationException` với lỗi nghiệp vụ khác.

`orTimeout(2, TimeUnit.SECONDS)` có thể hoàn thành **chính future đó** exceptionally với `TimeoutException` nếu chưa xong. `completeOnTimeout(fallback, ...)` có thể hoàn thành chính future đó bằng giá trị fallback. Hai phương thức là quyết định completion/deadline; không tự dừng I/O hay rollback side effect đã diễn ra.

Đặc biệt, khi đặt `exceptionally` phía sau timeout, callback sẽ xem lỗi timeout như một nhánh failure. Nếu có retry, phải xác định task cũ đã ngừng chưa, có idempotency hay không và có thể có side effect trùng lặp không.

## 15. Chuỗi xử lý phức tạp: allOf, thenCompose và lỗi lan truyền

Các stage khác như `thenApply`, `thenCompose`, `thenCombine` và `allOf` có thể hoàn thành exceptionally khi nhánh phụ thuộc lỗi. Đặt error handler ở điểm phù hợp sẽ quyết định scope fallback.

Ví dụ một chuỗi tác vụ:

~~~java
CompletableFuture<Receipt> receipt =
        customerService.load(customerId)
                .thenCompose(customer -> paymentService.charge(customer))
                .thenApply(payment -> receiptMapper.toReceipt(payment))
                .whenComplete((value, error) ->
                        metrics.recordPaymentAttempt(error == null))
                .exceptionally(error -> {
                    throw new CompletionException(
                            unwrapCompletion(error));
                });
~~~

Đây là **mã kiến trúc minh họa**, không biên dịch độc lập vì cần các service/type của hệ thống. `whenComplete` quan sát completion trước recovery. `exceptionally` cuối ở đây chỉ ném lại lỗi đã unwrap, **không tạo fallback**, nên có thể là dư thừa; một cách rõ hơn là để lỗi tự lan tiếp và bắt tại boundary thích hợp.

Với tác vụ **charge/payment có side effect**, không dùng `exceptionally(error -> successReceipt)` để tạo receipt thành công giả. Cần lưu trạng thái giao dịch, idempotency, reconciliation và xử lý lỗi bất định theo yêu cầu thực tế.

### allOf và một nhánh lỗi

`CompletableFuture.allOf(a,b)` trả `CompletableFuture<Void>`, hoàn thành khi cả hai đã kết thúc; nếu một nhánh lỗi, future tổng cũng kết thúc exceptionally. Có thể đặt `whenComplete` trên future tổng để đo kết quả chung. Nếu cần thống kê **từng nhánh**, gắn `handle`/`whenComplete` trên từng nguồn thay vì suy luận từ chỉ một exception của tổng.

Nếu muốn báo cáo **mọi** thất bại mà không để lỗi một nhánh ngăn việc đọc nhánh khác, có thể dùng `handle` để biến mỗi nhánh thành một `Outcome` chứa success/failure, rồi ghép các `Outcome` lại. Đó là lựa chọn có ý thức, không phải cách “nuốt” lỗi vì tiện.

## 16. Transaction, ThreadLocal và logging context trong Spring

Trong Spring `@Async`, body thường chạy trên executor thread. Transaction gắn với caller thread không tự được kế thừa, tương tự nhiều `ThreadLocal` chứa SecurityContext, MDC/log correlation và trace context. Callback `thenApply/handle/whenComplete` không có hậu tố Async có thể chạy trên **thread bất kỳ hoàn thành stage**, nên cũng không an toàn khi mặc định nó thấy context request.

Những tình huống dễ gây lỗi:

- Callback `whenComplete` lấy MDC request ID nhưng thread hoàn tất là worker không có MDC.
- `exceptionally` fallback đọc thông tin security principal từ ThreadLocal thay vì tham số an toàn.
- `handle` ghi vào database nhưng transaction của controller đã commit/rollback từ trước.
- Dùng `@Async` gửi việc trước khi transaction nguồn commit; worker thấy dữ liệu chưa tồn tại hoặc bị rollback.

Giải pháp: truyền metadata cần thiết như giá trị bất biến; dùng `TaskDecorator` hoặc cơ chế propagation phù hợp với executor; xác định transaction boundary riêng trong worker; khi cần đảm bảo sau commit, dùng event after-commit hoặc transactional outbox. **Không chọn callback nào chỉ dựa trên kỳ vọng thread context tự tồn tại.**

## 17. Thiết kế logging và metrics tránh mất hoặc đếm trùng lỗi

Một pipeline có thể có nhiều stage:

~~~text
source (exception)
   |
   +--> whenComplete(log original) ---> exceptionally(fallback)
   |                                    |
   |                                    +--> whenComplete(log recovered)
   |
   +--> handle(create Outcome.failure)
~~~

Nếu cả ba nơi ghi cùng metric `failed_jobs++`, hệ thống sẽ đếm trùng một job. Nên phân định:

- **Nguồn lỗi thật:** metric ở boundary thực thi hoặc observer gắn trực tiếp vào source.
- **Fallback được sử dụng:** metric riêng, đếm số lần thay thế kết quả.
- **Kết quả cuối theo nghiệp vụ:** metric ở response/job-status boundary.
- **Lỗi xử lý callback:** metric riêng hoặc log riêng để không nhầm với lỗi source.

Không log nguyên exception object bằng cách chỉ gọi `error.toString()` rồi bỏ stack trace trong hệ thống cần điều tra sự cố. Nhưng cũng không log dữ liệu nhạy cảm từ request/exception. Khi logging callback có khả năng ném lỗi, hãy cô lập nó hoặc đảm bảo sink đáng tin và xử lý ngoại lệ nhẹ, vì `whenComplete` có thể làm pipeline thành công biến thành thất bại.

## 18. Testing với CompletableFuture: xác định, không ngủ đoán

### Test logic riêng của ba API

Dùng `CompletableFuture.completedFuture(value)` và `CompletableFuture.failedFuture(error)` để kiểm tra từng nhánh **không cần executor**. Có thể assert:

1. `exceptionally` **không được gọi** khi nguồn thành công.
2. `exceptionally` trả fallback khi nguồn lỗi, nhưng nguồn vẫn exceptional.
3. `handle` chạy trên cả hai nhánh và có thể đổi `Integer` thành `String`.
4. `whenComplete` thấy lỗi nhưng stage phụ thuộc vẫn exceptional.
5. Observer `whenComplete` tự lỗi trên đường thành công thì stage phụ thuộc lỗi vì observer.
6. Observer cũng lỗi khi nguồn đã lỗi thì stage phụ thuộc ưu tiên lỗi nguồn.
7. Nguồn thành công với giá trị `null` phải được xử lý đúng bằng `error == null`.
8. Cancellation có thể kích hoạt exceptional completion nhưng không tự được hoàn tác trên nguồn.

### Test tích hợp với Spring @Async

Để kiểm thử đường task thật:

- Tạo bean `@Async` được Spring quản lý, bật `@EnableAsync`, và **gọi qua Spring proxy**, không dùng `this.method()` từ cùng bean trong proxy mode.
- Dùng `CountDownLatch entered` để xác nhận worker bắt đầu, `CountDownLatch release` để kiểm soát thời điểm worker hoàn thành. Không dùng `Thread.sleep(...)` để tạo race.
- Trả `CompletableFuture.completedFuture(value)` hoặc để exception ném từ body; kiểm tra Future của proxy với `get(timeout, unit)`, `whenComplete` và `exceptionally`.
- Test riêng tình huống submit bị từ chối khi executor/queue đầy; lỗi submit có thể xảy ra **trước khi có Future** và không thể kiểm thử chỉ bằng callback gắn sau.
- Test cleanup của executor và bảo đảm giải phóng latch trong `finally` để không làm CI treo.
- Test thread identity nếu ứng dụng có yêu cầu executor/context cụ thể; không suy ra tính async từ việc future còn pending trong một thời điểm ngẫu nhiên.

Ví dụ assertions JUnit 5 minh họa:

~~~java
CompletableFuture<Integer> failed =
        CompletableFuture.failedFuture(
                new IllegalArgumentException("invalid"));

assertEquals(10, failed.exceptionally(error -> 10).join());

CompletableFuture<String> mapped =
        failed.handle((value, error) ->
                error == null ? "ok" : "failure");
assertEquals("failure", mapped.join());

CompletableFuture<Integer> observed =
        failed.whenComplete((value, error) ->
                assertNotNull(error));
assertThrows(CompletionException.class, observed::join);
~~~

Đây là **fragment JUnit 5**, cần các static imports `Assertions.assertEquals`, `assertNotNull`, `assertThrows`, các imports `CompletableFuture` và `CompletionException`, và một test method/test class. Không xem nó là chương trình standalone.

## 19. Các lỗi phổ biến cần tránh

1. **Dùng `whenComplete` và tưởng đã khôi phục lỗi.** Observer không trả fallback, lỗi vẫn lan tiếp.
2. **Dùng `exceptionally` để ghi log nhưng vô tình trả default.** Caller nhận thành công giả và có thể không thấy lỗi nguồn.
3. **Dùng `handle` và không kiểm tra `error`.** Có thể truy cập `value` bằng null khi nguồn lỗi hoặc coi null là lỗi dù nguồn thành công.
4. **Cho rằng cả ba API sửa trực tiếp future nguồn.** Mỗi API trả một stage phụ thuộc mới; nguồn không bị sửa.
5. **Đặt fallback quá sớm trong pipeline.** Một lỗi quan trọng bị chuyển thành thành công và các stage sau vẫn chạy side effect.
6. **Đặt fallback quá muộn khi cần phục hồi một nhánh cụ thể.** Các phép biến đổi ở giữa đã bị bỏ qua vì lỗi lan truyền.
7. **Bắt mọi Throwable rồi trả dữ liệu mặc định.** Có thể che bug lập trình, lỗi dữ liệu, interruption/cancellation và sự cố hệ thống.
8. **Không gỡ `CompletionException` khi phân loại nguyên nhân.** Kiểm tra nhầm wrapper thay vì root cause.
9. **Gỡ mọi lớp exception bằng vòng lặp không giới hạn loại.** Làm mất ngữ nghĩa nghiệp vụ của các exception có cause riêng.
10. **Dùng `join()` rồi nghĩ non-blocking.** `join()` vẫn chờ nếu stage chưa hoàn thành; chỉ loại bỏ checked exceptions.
11. **Cho rằng `whenComplete` luôn an toàn và không thể làm stage lỗi.** Observer tự ném lỗi trên nguồn thành công sẽ làm stage phụ thuộc thất bại.
12. **Block lâu trong callback không có hậu tố Async.** Có thể chiếm chính worker vừa hoàn tất nguồn và gây tắc nghẽn.
13. **Dùng `handleAsync` không cấp executor rồi mặc định vẫn chạy trên Spring pool.** Có thể chuyển sang commonPool/default execution facility.
14. **Quên submission rejection.** Nếu executor không nhận task, caller chưa có Future để gắn `exceptionally`.
15. **Đặt `@Async void` và cố gọi `whenComplete` trên lời gọi trả void.** Không có handle; cần Future hoặc job-state khác.
16. **Nhầm cancel với ngắt việc thật.** `CompletableFuture.cancel` không chắc dừng CPU/I/O đang chạy.
17. **Nhầm timeout của Future với rollback side effect.** Task có thể tiếp tục sau khi người chờ bỏ cuộc.
18. **Gắn cùng metric ở nhiều stage mà không phân biệt scope.** Đếm trùng lỗi hoặc báo success do fallback thành success thật.
19. **Không test nguồn thành công null và cancellation.** Logic phân biệt trạng thái dễ bị sai ở hai trường hợp này.
20. **Bỏ qua thread context ở callback.** MDC, security, transaction không tự bảo đảm chuyển từ request thread sang worker.
21. **Thêm callback chỉ để biến exception thành một thông điệp quá chung.** Mất nguyên nhân điều tra và semantic error code.
22. **Dùng fallback cho thao tác tài chính hoặc side effect quan trọng.** Trả “thành công” giả có thể tạo sai lệch dữ liệu; phải có job status, retry và reconciliation.

## 20. Chọn API qua câu hỏi ngắn

| Câu hỏi của ứng dụng | Lựa chọn |
| --- | --- |
| Nguồn lỗi, tôi muốn trả một giá trị dự phòng cùng kiểu `T`? | `exceptionally` |
| Nguồn thành công hoặc lỗi, tôi muốn biến cả hai thành kiểu `U` khác? | `handle` |
| Tôi chỉ cần ghi log/metric cho mọi kết quả, không đổi kết quả hoặc lỗi? | `whenComplete` |
| Tôi muốn giữ exception cho caller nhưng thêm thông tin quan sát? | `whenComplete` trước khi lan lỗi |
| Tôi muốn nhận `Outcome<T>` thay vì exceptional future? | `handle` tạo `Outcome` có status rõ ràng |
| Tôi muốn dùng callback nặng chạy ở một executor chuyên dụng? | `handleAsync`/`whenCompleteAsync`/`exceptionallyAsync` với executor explicit |
| Tôi muốn fallback bằng một công việc khác cũng bất đồng bộ? | `exceptionallyCompose` trong Java 17+ hoặc tổ chức `handle` + `thenCompose`; cần kiểm soát executor và retry |
| Tôi muốn xử lý lỗi `@Async void`? | `AsyncUncaughtExceptionHandler` hoặc đổi chữ ký sang Future |
| Tôi muốn biết lúc executor từ chối task trước khi trả Future? | Bắt `TaskRejectedException` ở chỗ gọi proxy, không đợi callback |
| Tôi muốn job không mất khi ứng dụng restart? | Durable queue/job table với idempotency/retry, không phải chỉ `CompletableFuture` |

### Thêm về exceptionallyCompose

`exceptionally` đòi callback trả trực tiếp giá trị `T`. Nếu fallback bản thân nó phải gọi một dịch vụ **bất đồng bộ** trả `CompletionStage<T>`, Java 17 có `exceptionallyCompose(fn)` để ghép một stage fallback mà không tạo Future lồng nhau. Ví dụ mô hình:

~~~java
CompletableFuture<String> result =
        primary.fetch(key)
                .exceptionallyCompose(error -> secondary.fetch(key));
~~~

Đây là **fragment tích hợp**: `primary`/`secondary` là dịch vụ giả định trả `CompletableFuture<String>`. Việc fallback sang nguồn thứ hai phải kiểm soát timeout, circuit breaker và không tạo vòng retry vô hạn. Nếu chỉ cần giá trị mặc định trong RAM, `exceptionally` đơn giản hơn.

## 21. Câu hỏi tự kiểm tra

1. Trong ba API, phương thức nào **chỉ chạy khi nguồn lỗi**?
2. Phương thức nào có thể đổi `CompletableFuture<Integer>` thành `CompletableFuture<String>` bằng một callback?
3. Tại sao `whenComplete((v,e)->log(e))` không có nghĩa là lỗi đã được khôi phục?
4. Khi nguồn thành công nhưng observer `whenComplete` ném exception, stage phụ thuộc có trạng thái gì?
5. Khi nguồn đã lỗi và observer cũng lỗi, lỗi nào được ưu tiên ở stage `whenComplete`?
6. `CompletableFuture.completedFuture(null)` là thành công hay thất bại? Trong `handle` nên kiểm tra field nào?
7. Vì sao `source.exceptionally(...)` không làm `source.isCompletedExceptionally()` chuyển từ true sang false?
8. Cách đặt `whenComplete` trước hay sau `exceptionally` ảnh hưởng kết quả logging thế nào?
9. `join()` có non-blocking hơn `get()` không? Hai phương thức khác nhau về cách báo exception ra sao?
10. Dùng `handleAsync` không truyền executor trong ứng dụng Spring có thể làm callback chạy ở thread pool nào?
11. Với `@Async`, lỗi từ method body và `TaskRejectedException` khi submit khác nhau ở điểm nào?
12. Một `CompletableFuture` đã bị cancel nhưng được `exceptionally` chuyển thành fallback ở stage sau có nghĩa task trước đó tiếp tục hay đã dừng?
13. Nếu metric observer thất bại, bạn muốn nó có khả năng đổi kết quả nghiệp vụ không? Thiết kế callback ra sao?
14. Vì sao lỗi thao tác thanh toán không được phục hồi thành “thành công” chỉ bằng một giá trị mặc định?
15. Nếu fallback cần gọi một dịch vụ trả `CompletionStage<T>`, `exceptionallyCompose` khác `exceptionally` ở điểm nào?

## 22. Tài liệu tham khảo chính thức

- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — CompletionStage (quy tắc whenComplete và handle)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html)
- [Java SE 17 — CompletionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionException.html)
- [Java SE 17 — ExecutionException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutionException.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — @Async Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/annotation/Async.html)
- [Spring Framework — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)

**Ghi nhớ:** **`exceptionally()`** phục hồi đường lỗi bằng giá trị cùng kiểu; **`handle()`** luôn biến đổi kết quả/lỗi và có thể đổi kiểu; **`whenComplete()`** quan sát cả hai đường nhưng giữ nguyên kết quả/lỗi nguồn khi callback thành công. Chúng tạo stage mới chứ không sửa stage gốc; trong Spring `@Async` phải xử lý thêm lỗi submit, executor và thread context để pipeline đáng tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-006">Phân biệt phục hồi bằng giá trị thay thế với chỉ quan sát lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt phục hồi bằng giá trị thay thế với chỉ quan sát lỗi trong Spring Framework

## Mục tiêu

Khi một lời gọi bất đồng bộ thất bại, ta thường muốn **ghi log/đo lỗi** hoặc **cung cấp giá trị thay thế** để bước sau tiếp tục. Hai mục đích này khác nhau: ghi nhận lỗi không tự làm kết quả thành công, còn trả fallback có thể biến một future thất bại thành future thành công. Trong Spring Framework, đặc biệt với phương thức `@Async` trả `CompletableFuture<T>`, sự khác biệt này quyết định caller nhận giá trị hay ngoại lệ.

Bài tập trung vào ba API của Java `CompletableFuture`/`CompletionStage`:

- **`whenComplete((value, error) -> ...)`**: quan sát kết quả hoặc lỗi, thực hiện side effect như log/metrics; nếu action kết thúc bình thường, stage trả về giữ **cùng kết quả hoặc lỗi** của stage nguồn.
- **`exceptionally(error -> fallbackValue)`**: chỉ chạy khi nguồn hoàn tất ngoại lệ; tạo stage mới **thành công với giá trị thay thế** nếu hàm phục hồi trả về bình thường.
- **`handle((value, error) -> newValue)`**: chạy với cả thành công và thất bại, có thể đổi kiểu hoặc giá trị cho **cả hai nhánh**, cũng có thể phục hồi một lỗi.

Sau bài này, bạn có thể giải thích luồng lan truyền lỗi, chọn operator đúng mục đích, dự đoán giá trị trả về của chuỗi stage, hiểu khi nào fallback vô tình che giấu lỗi, và thử một ví dụ **Java 17+ + Spring Framework 6.2.x** chạy được bằng Maven.

**Quy tắc ngắn:** `whenComplete` để **nhìn thấy** lỗi; `exceptionally` hoặc `handle` để **quyết định kết quả sau lỗi**. Một callback có ghi log chưa hẳn là xử lý phục hồi.

## 1. Trước tiên phân biệt lỗi của task và lỗi khi submit

Trong ứng dụng Spring, một service có thể được đánh dấu:

```java
@Async("lessonExecutor")
public CompletableFuture<String> fetch(String key) {
    String value = remoteCall(key);
    return CompletableFuture.completedFuture(value);
}
```

Đây là **mã minh họa chưa chạy độc lập**: cần Spring bean, `@EnableAsync`, executor `lessonExecutor` và `remoteCall` do ứng dụng định nghĩa. Khi gọi qua Spring AOP proxy, method thực thi trong worker của executor đã chọn. Caller nhận future để quan sát sự hoàn tất.

Có ít nhất hai ranh giới lỗi:

1. **Submit thất bại:** không tìm được executor, pool đã đóng hoặc queue đầy dẫn đến `TaskRejectedException`. Lỗi này có thể xảy ra ngay khi caller gọi phương thức qua proxy, **trước khi có future để gắn** `exceptionally` hay `whenComplete`. Cần bảo vệ ranh giới submit nếu muốn bắt lỗi này.
2. **Task đã được nhận, sau đó thất bại:** `remoteCall` ném exception trong worker hoặc target trả một future thất bại. Với phương thức `@Async` trả `CompletableFuture`, hạ tầng Spring phản ánh lỗi vào **future của caller**. Từ đây, các toán tử của `CompletableFuture` mới có cơ hội xử lý kết quả bất đồng bộ.

Lưu ý `@Async void` không trao future cho caller, vì vậy không thể gọi `whenComplete` hoặc `exceptionally` trên một kết quả không tồn tại. Spring hỗ trợ `AsyncUncaughtExceptionHandler` để quan sát lỗi không được trả qua future cho loại method này.

## 2. Phân biệt trạng thái nguồn với kết quả cuối theo nghiệp vụ

Các trạng thái success, exceptional và cancellation đã được giải thích trong bài ba toán tử CompletionStage. Ở đây, câu hỏi là **caller có được phép diễn giải một fallback như thành công nghiệp vụ không?**

| Trạng thái nguồn | Quyết định nghiệp vụ | Thông tin không được mất |
| --- | --- | --- |
| Thành công với dữ liệu hợp lệ, có thể là `null` theo contract | Dùng dữ liệu gốc | Nguồn và chất lượng dữ liệu thực. |
| Lỗi transient, có cache đủ mới | Có thể trả fallback | Lỗi backend, tuổi cache và cờ `degraded`. |
| Lỗi validation, quyền hoặc toàn vẹn dữ liệu | Giữ failure | Mã lỗi phù hợp và nguyên nhân để điều tra. |
| Timeout/cancellation trong khi I/O có thể còn chạy | Không mặc định thành công hay đã dừng task | Trạng thái chưa rõ, deadline và khả năng đối soát. |

Hai stage cùng hoàn thành bình thường không nhất thiết có cùng ý nghĩa: một chứa dữ liệu từ primary, stage kia chứa dữ liệu thay thế. Thiết kế domain outcome để thể hiện sự khác biệt.

## 3. Quan sát là một ranh giới telemetry, không phải hành động phục hồi

`whenComplete((value, error) -> ...)` thích hợp ghi nhận kết quả/lỗi nguồn mà vẫn giữ trạng thái stage nếu callback chạy bình thường. Khi định nghĩa chính sách, hãy tách ba sự kiện:

- `primary_failed`: nguồn thật sự lỗi trước khi fallback.
- `fallback_used`: hệ thống đã chấp nhận và dùng dữ liệu dự phòng.
- `final_failed`: kết quả cuối vẫn không đáp ứng hợp đồng.

Một observer chỉ đặt **sau** `exceptionally` đã phục hồi sẽ thấy stage thành công và có thể bỏ sót outage của nguồn. Nếu observer cần thuộc về kết quả caller nhận, phải giữ/trả stage do `whenComplete` tạo; bỏ stage này có thể bỏ luôn lỗi của callback quan sát.

Handler logging/metrics nên nhanh, không block và không ném lỗi không kiểm soát. Nếu nguồn thành công nhưng observer lỗi, stage phụ thuộc có thể thất bại vì chính observer; cần test điều kiện này.

## 4. Fallback phải có hợp đồng chấp nhận rõ ràng

`exceptionally(error -> fallbackValue)` tạo stage mới thành công nếu handler trả giá trị bình thường; **nó không tự gửi lại request**. Trước khi triển khai, cần quyết định:

1. Loại lỗi nào được phép fallback? Dữ liệu gợi ý giao diện có thể dùng cache; thanh toán và kiểm tra quyền không được trả success giả.
2. Dữ liệu thay thế có còn hợp lệ theo TTL, tenant, version hoặc giới hạn stale-data không?
3. Caller có biết nguồn dữ liệu suy giảm qua flag hoặc Outcome không?
4. Lỗi gốc và mức sử dụng fallback có được log/đo riêng hay đã bị che?
5. Nếu cache miss hoặc lỗi không thuộc loại cho phép, stage có giữ failure đúng nguyên nhân không?

Không dùng `exceptionally(ex -> null)` một cách đại trà nếu `null` không được định nghĩa là giá trị hợp lệ: điều đó có thể chuyển backend failure thành một NPE rất muộn. Cần phân loại exception và duy trì cause phù hợp.

## 5. Dùng handle để công bố nguồn và chất lượng kết quả

`handle((value, error) -> outcome)` chạy trên cả hai nhánh, cho phép trả kiểu domain rõ ràng về **kết quả thật, kết quả suy giảm hoặc thất bại**.

Đoạn Java 17 sau nằm trong method đã có biến `source` kiểu `CompletableFuture<String>`:

~~~java
record LookupOutcome(String value, boolean degraded, String errorCode) {}

CompletableFuture<LookupOutcome> result =
        source.handle((value, error) -> error == null
                ? new LookupOutcome(value, false, null)
                : new LookupOutcome("cached-value", true, "BACKEND_UNAVAILABLE"));
~~~

Ở đây nhánh lỗi giả định cache luôn hợp lệ **chỉ để minh họa hình dạng dữ liệu**; production phải kiểm tra loại lỗi và cache trước khi cho fallback. Stage trả `LookupOutcome(degraded=true)` là success về Future nhưng chứa kết quả suy giảm về nghiệp vụ. Consumer cần đọc các trường đó.

Nếu không có fallback hợp lệ, giữ exceptional completion hoặc biểu diễn `Outcome.failure` đúng contract. Dùng `handle` chỉ để ghi log rồi `return null` sẽ làm mất giá trị thành công và có thể che lỗi; `whenComplete` phù hợp hơn cho quan sát thuần túy.

## 6. Quyết định operator theo trách nhiệm nghiệp vụ

| Mục đích ở boundary | Operator | Dấu hiệu cần kiểm chứng |
| --- | --- | --- |
| Đếm lỗi nguồn nhưng vẫn để caller biết lỗi | `whenComplete` trước phục hồi | Observer thấy failure gốc, stage vẫn exceptional. |
| Dùng cache cho một lỗi transient được cho phép | `exceptionally` | Stage mới trả fallback hợp lệ và metric phân biệt cache. |
| Chuyển cả success và failure sang domain result | `handle` | Outcome có error/status/degraded rõ ràng. |
| Đo kết quả cuối sau mọi phục hồi | `whenComplete` sau phục hồi | Metric phản ánh outcome cuối, không giả là lỗi backend. |
| Fallback phải gọi API async khác | `exceptionallyCompose` | Stage phụ thuộc chờ nguồn dự phòng mà không `join` blocking. |

Trong ví dụ sau, cả ba nhánh xuất phát từ **cùng một Future lỗi** để quan sát riêng failure nguồn, fallback và kết quả đã ánh xạ. Điều đó rõ hơn việc chỉ nhớ chữ ký operator.

## 7. Ví dụ Spring Framework chạy được: cùng một lỗi, ba nhánh khác nhau

Chương trình dưới đây chạy với **Java 17+**, Maven và **Spring Framework 6.2.12**. Ta tạo Spring `@Async` service có một nhánh nghiệp vụ cố ý thất bại, rồi từ **cùng future nguồn** tạo ba nhánh:

- `observation = source.whenComplete(...)` để đếm số lần thấy lỗi.
- `recovery = source.exceptionally(...)` để trả fallback `"cache:B"`.
- `handled = source.handle(...)` để trả `"handled:B"` khi lỗi, nhưng cũng biết xử lý success.

Ta cũng gửi một lời gọi thành công để chứng minh `exceptionally` **không thay** giá trị thành công.

Để output xác định, callback chỉ cập nhật `AtomicInteger` chứ không in từ worker; `main` đợi các future hoàn tất rồi mới in theo thứ tự cố định. Không cần `Thread.sleep` hay dựa vào lịch chạy ngẫu nhiên.

### Cấu trúc

```text
async-error-recovery-demo/
├── pom.xml
└── src/main/java/example/AsyncErrorRecoveryDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>async-error-recovery-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.12</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <mainClass>example.AsyncErrorRecoveryDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/example/AsyncErrorRecoveryDemo.java`:

```java
package example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class AsyncErrorRecoveryDemo {

    public static class LookupService {
        @Async("lessonExecutor")
        public CompletableFuture<String> lookup(String key) {
            if ("bad".equals(key)) {
                throw new IllegalStateException("Backend unavailable");
            }
            return CompletableFuture.completedFuture("remote:" + key);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {
        @Bean
        public LookupService lookupService() {
            return new LookupService();
        }

        @Bean("lessonExecutor")
        public ThreadPoolTaskExecutor lessonExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(1);
            pool.setQueueCapacity(4);
            pool.setThreadNamePrefix("lesson-");
            return pool;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            LookupService service = context.getBean(LookupService.class);
            AtomicInteger observedErrors = new AtomicInteger();

            // Một invocation @Async cố ý thất bại trên worker.
            CompletableFuture<String> source = service.lookup("bad");

            // Nhánh 1: chỉ ghi nhận; không đổi lỗi thành success.
            CompletableFuture<String> observation = source.whenComplete(
                (value, error) -> {
                    if (error != null) {
                        observedErrors.incrementAndGet();
                    }
                }
            );

            // Nhánh 2: phục hồi lỗi bằng giá trị đã được cho phép.
            CompletableFuture<String> recovery = source.exceptionally(
                error -> "cache:B"
            );

            // Nhánh 3: xử lý cả success và failure.
            CompletableFuture<String> handled = source.handle(
                (value, error) ->
                    error == null ? "handled:" + value : "handled:B"
            );

            // Trường hợp thành công: exceptionally giữ value gốc.
            CompletableFuture<String> healthy = service.lookup("OK");
            CompletableFuture<String> successWithFallback =
                healthy.exceptionally(error -> "cache:OK");

            // Chờ từng stage trước khi in để stdout có thứ tự ổn định.
            String recoveredValue = recovery.get(5, TimeUnit.SECONDS);
            String handledValue = handled.get(5, TimeUnit.SECONDS);
            String healthyValue = successWithFallback.get(5, TimeUnit.SECONDS);

            boolean observerStillFailed = false;
            try {
                observation.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                observerStillFailed = true;
            }

            System.out.println("sourceFailed="
                + source.isCompletedExceptionally());
            System.out.println("observedErrors=" + observedErrors.get());
            System.out.println("observerStillFailed=" + observerStillFailed);
            System.out.println("recoveredValue=" + recoveredValue);
            System.out.println("handledValue=" + handledValue);
            System.out.println("healthyValue=" + healthyValue);
        }
    }
}
```

Chạy tại thư mục `async-error-recovery-demo` (có JDK 17+ và Maven):

```bash
mvn -q compile exec:java
```

Khi chương trình chạy thành công, sáu dòng **do ứng dụng in ra** theo đúng thứ tự:

```text
sourceFailed=true
observedErrors=1
observerStillFailed=true
recoveredValue=cache:B
handledValue=handled:B
healthyValue=remote:OK
```

### Diễn giải từng dòng

1. `sourceFailed=true`: `lookup("bad")` ném `IllegalStateException` trong worker. Spring proxy phản ánh lỗi vào `CompletableFuture<String>` mà caller nhận, nên source hoàn tất ngoại lệ.
2. `observedErrors=1`: `whenComplete` thực thi đúng một lần cho lần hoàn tất lỗi của source. `AtomicInteger` giúp công bố phép đếm an toàn giữa các thread; sau `observation.get`, `main` đã chờ callback này xong.
3. `observerStillFailed=true`: stage `observation` **vẫn ném `ExecutionException`** khi `get`, bởi action quan sát không làm fallback.
4. `recoveredValue=cache:B`: nhánh `exceptionally` nhận lỗi và trả về một `String`, làm stage recovery hoàn tất bình thường.
5. `handledValue=handled:B`: nhánh `handle` đọc `error != null` nên chọn nhánh phục hồi.
6. `healthyValue=remote:OK`: lời gọi `lookup("OK")` thành công; `exceptionally` không chạy, vì thế giữ nguyên value nguồn.

**Quan trọng:** `source`, `observation`, `recovery` và `handled` là những stage khác nhau, dù có cùng nguồn. `recovery` thành công **không viết lại** trạng thái failure của `source`, và `observation` không tự được chữa lỗi khi `recovery` thành công. Output không dựa vào thứ tự giữa worker và caller; chương trình chờ có timeout rồi in từ thread `main` theo thứ tự cố định.

Spring `@EnableAsync` và tên executor nằm trên cấu hình thực sự; `LookupService` được container quản lý và không tự gọi `this.lookup(...)`. Các phương thức/chữ ký phù hợp với Spring AOP proxy. Khi môi trường không tải được dependency hoặc tác vụ không hoàn tất trong hạn 5 giây, chương trình có thể báo lỗi thay vì có sáu dòng trên; đó không phải thay đổi hợp đồng của operator.

## 8. Thứ tự nối các operator làm đổi lỗi được quan sát

Đây là phần quan trọng nhất khi triển khai hệ thống cần vừa giám sát vừa fallback.

### Quan sát trước khi phục hồi

```java
CompletableFuture<String> answer = source
    .whenComplete((value, error) -> {
        if (error != null) {
            metrics.countBackendFailure();
        }
    })
    .exceptionally(error -> "cache");
```

Đây là snippet trong ứng dụng có `source` và `metrics`; không độc lập. `whenComplete` thấy **lỗi gốc** để cập nhật chỉ số. `exceptionally` phía sau nhận lỗi và chuyển thành `"cache"` nếu cần. `answer` là một stage thành công khi fallback thành công.

### Phục hồi trước rồi mới quan sát

```java
CompletableFuture<String> answer = source
    .exceptionally(error -> "cache")
    .whenComplete((value, error) -> {
        if (error != null) {
            metrics.countBackendFailure();
        }
    });
```

Nếu lỗi nguồn đã được `exceptionally` chuyển thành fallback thành công, callback sau đó nhận `error == null` và không đếm lỗi backend. Nếu mục tiêu giám sát là tỷ lệ lỗi thật của backend, thứ tự này **che mất nguyên nhân ở tầng quan sát sau phục hồi**.

Điều đó **không** có nghĩa operator phía sau không bao giờ thấy lỗi: nếu fallback tự ném exception hoặc stage khác ném lỗi, `whenComplete` phía sau có thể thấy lỗi mới. Hãy định nghĩa rõ metric là “backend failures”, “fallback used”, “final request failures” hay “user-visible failures”; chúng không đồng nhất.

### Minh họa pipeline

```text
Backend failure
     |
     +--> whenComplete(log backend failure)
                   |
                   v
             exceptionally(cache)
                   |
                   v
             SUCCESS("cache") ----------> caller

Backend failure
     |
     +--> exceptionally(cache)
                   |
                   v
             SUCCESS("cache")
                   |
                   v
             whenComplete(log final outcome)
                error == null ----------> caller
```

Trong thực tế nên đo **cả lỗi nguồn lẫn tỷ lệ dùng fallback**. Caller nhận được kết quả thay thế không có nghĩa backend đang khỏe hoặc không cần điều tra.

## 9. Nếu chỉ muốn ghi log nhưng giữ thất bại, làm thế nào?

Sử dụng `whenComplete` và **trả stage đó**, không biến lỗi thành giá trị:

```java
return service.lookup(key)
    .whenComplete((value, error) -> {
        if (error != null) {
            logger.error("Async lookup failed for key={}", key, error);
        }
    });
```

Snippet này nằm trong phương thức có `service`, `key`, logger; không độc lập. Nếu source lỗi, stage trả về vẫn lỗi (khi logging không ném lỗi). Caller vẫn có thể dùng `get()`/`join()` hoặc exception mapping ở controller để thấy thất bại.

Đừng dùng:

```java
return service.lookup(key)
    .handle((value, error) -> {
        if (error != null) {
            logger.error("Failed", error);
        }
        return null;
    });
```

Đoạn này chỉ minh họa lỗi thiết kế, cũng không chạy độc lập. `handle` trả null cho **cả success và failure**, khiến caller nhận future thành công với `null`; vừa mất value bình thường vừa nuốt nguyên nhân lỗi. Với API `Result<T>`, điều này có thể dẫn tới `NullPointerException` ở tầng sau và làm mất ngữ cảnh sự cố ban đầu.

Nếu muốn *lựa chọn* tiếp tục ném lỗi sau khi dùng `handle`, cần ném lại đúng cách (có thể dùng `CompletionException`) và hiểu rằng stage mới khi ấy vẫn failed, không phải recovered. Đơn giản hơn, dùng `whenComplete` cho mục đích quan sát.

## 10. Fallback có điều kiện: chỉ phục hồi lỗi được chấp nhận

Không phải mọi lỗi đều đáng được biến thành success. Ví dụ: backend không truy cập được có thể cho phép cache, nhưng invalid credentials, vi phạm quy tắc nghiệp vụ, dữ liệu không nhất quán hay error bảo mật có thể phải được truyền lên.

```java
CompletableFuture<String> controlled = source.exceptionally(error -> {
    Throwable root = error;
    while (root instanceof java.util.concurrent.CompletionException
            && root.getCause() != null) {
        root = root.getCause();
    }

    if (root instanceof TransientBackendException) {
        return "stale-cache-value";
    }

    throw new java.util.concurrent.CompletionException(root);
});
```

Đây là snippet minh họa, `TransientBackendException` là loại lỗi do ứng dụng định nghĩa. `CompletionException` có thể bọc nguyên nhân khi stage bất đồng bộ truyền lỗi; các implementation có thể tạo wrapper khác, nên phân loại cần được kiểm thử với chính pipeline của dự án. Không coi mọi `Throwable` là transient. Nếu fallback thất bại, ném lỗi từ handler khiến stage mới failed thay vì âm thầm trả null.

Với nghiệp vụ nhạy cảm, nên dùng result type có metadata:

```java
record LoadResult(String value, boolean fallbackUsed) {}

CompletableFuture<LoadResult> result = source.handle(
    (value, error) -> error == null
        ? new LoadResult(value, false)
        : new LoadResult("cached", true)
);
```

Đoạn trên là snippet Java 17 (cần `source` có kiểu `CompletableFuture<String>`). Nếu giá trị dự phòng không được chấp nhận cho lỗi hiện tại, handler phải **truyền tiếp lỗi** thay vì cứ dùng `"cached"`.

## 11. Fallback bất đồng bộ: `exceptionallyCompose` khác `exceptionally`

`exceptionally` yêu cầu callback trả **giá trị `T`**; nếu fallback bản thân cần gọi một API async khác, có thể dùng `exceptionallyCompose` (có trong Java 17) để lấy một `CompletionStage<T>` rồi “làm phẳng” nó.

Ví dụ:

```java
CompletableFuture<String> answer = primary.loadAsync(key)
    .exceptionallyCompose(error -> secondary.loadAsync(key));
```

Đây là snippet kiến trúc; cả `primary` và `secondary` phải cung cấp API trả `CompletableFuture<String>` hoặc stage tương thích, và cần thiết kế timeout, hạn mức, idempotency phù hợp. Tác vụ secondary chỉ được gọi khi primary thất bại. Nếu secondary cũng thất bại, stage tổng cũng thất bại, trừ khi có bước phục hồi khác.

**Đừng** viết một fallback chặn thread chỉ để lấy async value:

```java
CompletableFuture<String> answer = primary.loadAsync(key)
    .exceptionally(error -> secondary.loadAsync(key).join());
```

Đoạn này minh họa anti-pattern khi chạy trong pool nhỏ hoặc hệ thống có nhiều I/O. Callback `.join()` có thể chiếm thread cho đến khi secondary hoàn tất, làm giảm throughput hoặc tạo deadlock nếu secondary cần cùng worker. `exceptionallyCompose` giữ dạng chuỗi async rõ hơn.

Nếu fallback cần chuyển sang executor khác để làm công việc nặng, cân nhắc `exceptionallyAsync(fn, executor)` hoặc `exceptionallyComposeAsync(fn, executor)` theo API Java 17, không giả định callback non-async tự chọn pool riêng.

## 12. `whenCompleteAsync`, `handleAsync` và luồng callback

Các biến thể thường dùng:

| API | Callback được gọi ở đâu? | Khi nên dùng |
| --- | --- | --- |
| `whenComplete(action)` | Có thể chạy ở thread hoàn tất stage hoặc thread đăng ký/hỗ trợ completion nếu stage đã done. | Quan sát nhẹ, không blocking. |
| `whenCompleteAsync(action)` | Chuyển action qua executor async mặc định của stage/JDK. | Muốn action được lập lịch async nhưng chấp nhận executor mặc định. |
| `whenCompleteAsync(action, executor)` | Chạy action theo executor cung cấp. | Muốn kiểm soát pool cho logging/audit callback. |
| `exceptionally(fn)` | Thực thi theo completion policy không-async; không tự lập lịch thêm worker. | Fallback đơn giản và nhanh. |
| `exceptionallyAsync(fn, executor)` | Chạy fallback trên executor chỉ định. | Fallback có logic cần tách tài nguyên. |
| `handleAsync(fn, executor)` | Chạy hàm xử lý cả success/failure qua executor chỉ định. | Cần chuyển đổi outcome nhưng không muốn chiếm completion thread. |

Sự tồn tại của Spring `@Async("lessonExecutor")` **không có nghĩa** tất cả các stage nối sau đó tự dùng `lessonExecutor`. `CompletableFuture` có quy tắc executor riêng: các overload `...Async(...)` không truyền executor thường dùng facility mặc định của stage/JDK (thường là common pool). Với workload blocking hoặc dữ liệu nhạy cảm, chọn executor và context propagation rõ ràng.

Không đặt một thao tác I/O blocking rất lâu trong `whenComplete` nếu nó chạy trực tiếp trên thread đang hoàn tất một Future quan trọng. Callback có thể kéo dài thời gian trước khi dependent stage hoàn tất và giữ worker Spring bị bận. Đối với logging, cân nhắc cơ chế nonblocking/queue logging phù hợp và theo dõi lỗi của chính pipeline quan sát.

## 13. Khi `whenComplete`/`exceptionally` che hoặc đổi lỗi theo cách bất ngờ

### Trường hợp 1 — Logging callback ném lỗi trên success

```java
CompletableFuture<String> ok =
    CompletableFuture.completedFuture("OK");

CompletableFuture<String> afterLog = ok.whenComplete(
    (value, error) -> {
        throw new IllegalStateException("Logging is down");
    }
);
```

Đoạn Java thuần này tạo `afterLog` ở trạng thái failed, dù `ok` vẫn successful. Đây là lý do side-effect observer phải được thiết kế đáng tin cậy, nhất là audit/logging external có thể lỗi.

### Trường hợp 2 — Recovery callback tự thất bại

```java
CompletableFuture<String> afterFallback = source.exceptionally(
    error -> {
        throw new IllegalArgumentException("Cache is not ready");
    }
);
```

Nếu source failed, `afterFallback` sẽ failed do chính lỗi cache. Không phải cứ thêm `exceptionally` là có kết quả thành công; chỉ khi fallback trả về bình thường mới phục hồi được.

### Trường hợp 3 — Phục hồi `null` vô tình biến failure thành success

```java
CompletableFuture<String> masked = source.exceptionally(
    error -> null
);
```

Với `String` là kiểu tham chiếu, `masked.join()` có thể trả `null` mà không ném lỗi. Nếu caller không dự kiến null, bạn đã chuyển một backend failure rõ ràng thành NPE khó tìm ở bước sau. Hãy đặt fallback hợp đồng cụ thể, hoặc giữ failure để tầng trên xử lý.

### Trường hợp 4 — Chỉ quan sát rồi vẫn `thenApply`

```java
CompletableFuture<String> transformed = source
    .whenComplete((value, error) -> audit(error))
    .thenApply(String::toUpperCase);
```

Nếu source failed, `whenComplete` vẫn truyền failure và `thenApply` **không chạy**; `transformed` tiếp tục failed. Nếu cần `thenApply` luôn có một giá trị (trừ khi fallback cũng lỗi), đặt `exceptionally` hoặc `handle` **trước** bước chuyển đổi.

## 14. Phục hồi không đồng nghĩa mọi tác dụng phụ đã được xử lý

### Trong Spring MVC

Nếu controller nhận một `CompletableFuture<String>` đã phục hồi, tầng MVC/API có thể trả response success với fallback. Điều này hợp lý cho dịch vụ có chấp nhận cache, nhưng không nên biến một lỗi thanh toán hoặc kiểm tra quyền truy cập thành HTTP 200 giả. Nên phân biệt “result from primary” và “result from fallback” trong domain/observability khi ảnh hưởng quyết định người dùng.

Một controller gọi `future.get()` hoặc `join()` ngay vẫn chặn request thread dù service được `@Async`. Lớp HTTP async có hợp đồng riêng; một future không tự bảo đảm toàn bộ request xử lý non-blocking.

### Transaction và retry

Nếu worker đã ghi database một phần rồi ném exception, `exceptionally` trả fallback **không rollback** transaction hoặc undo side effect. Rollback phụ thuộc transaction boundary/isolation và chính code nghiệp vụ. `@Transactional` ở caller không mặc nhiên truyền qua worker `@Async`; cần xem proxy và transaction ở thread thực thi.

Fallback cũng **không retry** backend. Retry thực sự là thao tác gọi lại, phải giới hạn số lần, timeout, backoff, idempotency, tổng deadline và tránh làm nghẽn pool đang quá tải. Khi một tác vụ đã phát sinh side effect, retry có thể nhân đôi tác động nếu không có bảo vệ.

### Timeout và cancellation

`Future.get(timeout, unit)` chỉ giới hạn **caller chờ**, không tự cancel worker. `CompletableFuture.orTimeout(...)` có thể hoàn tất chính future bằng exception timeout khi pending; nếu sau đó `exceptionally` trả fallback, caller có thể nhận giá trị thay thế nhưng worker bên dưới vẫn tiếp tục chạy. `completeOnTimeout(...)` trực tiếp đưa fallback vào future nếu chưa hoàn tất đến hạn, cũng không tự buộc worker dừng.

`CompletableFuture.cancel(true)` không bảo đảm interrupt computation tạo ra completion. Nếu người dùng không còn cần kết quả, phải có chính sách cancellation hợp tác và timeout tại I/O client thực sự. Không giả định “đã phục hồi rồi” nghĩa tài nguyên đã được thu hồi.

### Context và thread safety

`@Async` là ranh giới thread. MDC logging, `SecurityContext`, request attributes, transaction, và nhiều `ThreadLocal` của caller không tự truyền sang worker. Callback `whenComplete` non-async cũng có thể chạy trên thread hoàn tất stage khác; nếu cần correlation ID, dùng context propagation có chủ đích và dọn/khôi phục trong `finally`. Không lưu thông tin request của người dùng vào một singleton mutable field để phục vụ logging bất đồng bộ.

## 15. Thiết kế quan sát lỗi và fallback trong production

Nên phân biệt các tín hiệu:

| Chỉ số | Ý nghĩa | Tại sao cần riêng? |
| --- | --- | --- |
| `primary_failure_total` | Backend/tác vụ nguồn lỗi trước phục hồi. | Fallback có thể làm request cuối thành công; chỉ đo final error sẽ bỏ lỡ outage. |
| `fallback_attempt_total` | Đã thử lấy giá trị dự phòng. | Biết hệ thống đang phụ thuộc fallback ở mức nào. |
| `fallback_success_total` | Fallback trả value hợp lệ. | Đo khả năng phục hồi thật. |
| `fallback_failure_total` | Chính fallback thất bại. | Phân biệt suy giảm kép. |
| `final_request_failure_total` | Caller cuối vẫn nhận lỗi. | Đo tác động thực đối với người dùng. |
| `fallback_data_age` | Độ cũ của cached/stale value nếu có. | Ngăn dữ liệu dự phòng không phù hợp bị coi như dữ liệu tươi. |

Nếu metric chỉ được ghi **sau** `exceptionally`, upstream failure có thể không còn biểu hiện dưới dạng `error`. Thường cần quan sát source trước khi phục hồi, rồi đo outcome cuối sau khi fallback. Đừng cho metric sink hoặc logger làm nghẽn thread pool nghiệp vụ hoặc gây exception bất ngờ.

Có thể biểu đạt kết quả cuối bằng domain type thay vì che khuất nguồn:

```java
record FetchOutcome(String value, boolean degraded) {}

CompletableFuture<FetchOutcome> finalResult = source.handle(
    (value, error) -> error == null
        ? new FetchOutcome(value, false)
        : new FetchOutcome("cached", true)
);
```

Đây là snippet Java 17; business phải xác định có thể dùng `"cached"` trong trường hợp lỗi nào, dữ liệu dự phòng có hợp lệ hay không. Nếu không có fallback tin cậy, hãy cho failure tiếp tục truyền lên tầng xử lý phù hợp.

## 16. Những sai lầm thường gặp

| Sai lầm | Hệ quả | Giải pháp |
| --- | --- | --- |
| Dùng `whenComplete` để log rồi tưởng đã chữa lỗi. | Caller tiếp tục nhận failure. | Dùng `exceptionally`/`handle` nếu **thực sự muốn** trả fallback. |
| Dùng `handle` chỉ để log rồi `return null`. | Nuốt lỗi, mất giá trị success, NPE xuất hiện muộn. | Dùng `whenComplete` để quan sát mà giữ outcome. |
| Dùng `exceptionally(ex -> null)` cho mọi lỗi. | Failure bị biến thành success null. | Có fallback hợp đồng rõ hoặc để lỗi truyền tiếp. |
| Fallback mọi exception, kể cả validation/security. | Che lỗi nghiệp vụ và có thể phá bảo mật. | Chỉ phục hồi các loại lỗi được chấp nhận. |
| Gắn metrics sau khi đã recovery, chỉ đếm `error != null`. | Không thấy backend outage đã được fallback che. | Đếm lỗi nguồn trước fallback, đo thêm outcome cuối. |
| Nghĩ `exceptionally` sửa chính future nguồn. | Consumer khác giữ source vẫn nhận lỗi. | Trả/tiếp tục dùng stage sau recovery; hiểu mỗi stage riêng. |
| Nghĩ `exceptionally` tự retry. | Không gửi lại backend, kết quả chỉ đến từ fallback. | Nếu cần retry, thiết kế gọi lại có giới hạn và idempotency. |
| Gọi `.join()` trong fallback để chờ API khác trên pool nhỏ. | Tốn worker, có thể deadlock. | Dùng `exceptionallyCompose` cho fallback async. |
| Đặt logging I/O dài trong `whenComplete`. | Giữ thread hoàn tất stage và làm tăng latency. | Observer nhẹ, hoặc tách executor có kiểm soát. |
| Cho rằng callback non-async chắc chạy trên main thread. | Context/MDC không đúng, thread safety sai. | Hiểu completion thread và propagation. |
| `@Async void` mà cố gọi `exceptionally` từ caller. | Không có handle kết quả. | Dùng Future return hoặc `AsyncUncaughtExceptionHandler`. |
| Bỏ qua lỗi submit task. | `TaskRejectedException` xảy ra trước callback chain. | Bắt tại điểm submit, quản lý quá tải/rejection. |
| Fallback thành công nên coi worker đã dừng. | Task/side effect cũ có thể tiếp tục. | Tách completion policy với cancellation và I/O timeout. |

## 17. Checklist khi đọc code

1. **Đang quan sát hay đang thay kết quả?** Nếu chỉ log/metrics, `whenComplete`; nếu muốn success fallback, `exceptionally`/`handle`.
2. **Stage nào được trả cho caller?** Original hay recovery stage? Có stage quan sát bị bỏ và exception của callback không ai theo dõi?
3. **Nhánh success có bị đổi không?** `exceptionally` giữ success, `handle` chạy cả hai nhánh.
4. **Lỗi nào được phục hồi?** Có phân loại transient, validation, auth, cancellation, timeout hay cứ bắt mọi `Throwable`?
5. **Fallback có ý nghĩa nghiệp vụ?** Dữ liệu dự phòng hợp lệ, đủ mới và được gắn nhãn degraded chưa?
6. **Có giám sát upstream failure không?** Log/metrics trước khi recovery, đếm số lần fallback và kết quả cuối?
7. **Callback có blocking/throw không?** `whenComplete` ném lỗi trên success làm stage mới thất bại; fallback ném lỗi làm stage recovery failed.
8. **Có đúng ranh giới Spring async không?** Bean Spring-managed, `@EnableAsync`, lời gọi qua proxy, executor qualifier?
9. **Có xử lý lỗi submit và @Async void không?** Chúng không nhất thiết đi vào pipeline Future.
10. **Có cần fallback bất đồng bộ không?** Ưu tiên `exceptionallyCompose` khi fallback trả một stage khác, tránh blocking `join`.
11. **Context và transaction được quản lý ở đâu?** Worker/callback không mặc nhiên có ThreadLocal caller.
12. **Caller cuối cùng cần thấy gì?** Thành công thật, thành công suy giảm, hoặc lỗi; đừng dùng HTTP 200 giả để che lỗi quan trọng.

## 18. Bài tập kiểm tra

**Câu 1.** `source` kết thúc với `IllegalStateException`. `source.whenComplete((v,e) -> log(e))` có làm `join()` của stage mới trả value không?

**Đáp án:** Không. Callback log kết thúc bình thường thì stage mới giữ exception của source; `join()` vẫn ném lỗi.

**Câu 2.** `source.exceptionally(e -> "cache")` làm thay đổi `source.isCompletedExceptionally()` thành false không?

**Đáp án:** Không. Recovery tạo stage phụ thuộc mới; source vẫn failed. Chỉ stage recovery thành công với `"cache"`.

**Câu 3.** `source` thành công `"OK"`. `source.exceptionally(e -> "cache")` trả gì?

**Đáp án:** `"OK"`. Callback fallback không chạy khi source thành công.

**Câu 4.** Một `handle` được viết là `(v,e) -> null` để in lỗi. Điều gì xảy ra?

**Đáp án:** Handler chạy cho mọi outcome và stage sau đó thành công với `null` nếu handler không ném lỗi; đây không phải “chỉ quan sát”, có thể che failure và mất success value.

**Câu 5.** `whenComplete` ném exception khi nguồn thành công. Stage mới có còn success không?

**Đáp án:** Không. Stage phụ thuộc thất bại với exception của callback. Nếu nguồn đã lỗi, theo quy tắc `whenComplete`, lỗi nguồn được ưu tiên khi callback cũng lỗi.

**Câu 6.** Khi muốn ghi nhận cả lỗi backend và tỷ lệ fallback thành công, nên đặt log/metric ở đâu?

**Đáp án:** Quan sát lỗi nguồn **trước** khi phục hồi và đo kết quả fallback **sau** đó. Nếu chỉ quan sát sau recovery, lỗi backend có thể đã biến thành một success.

**Câu 7.** Fallback phải gọi một API async khác. `exceptionally(error -> secondary.getAsync().join())` có phải cách tối ưu?

**Đáp án:** Thường không, vì `.join()` có thể chặn completion thread. Dùng `exceptionallyCompose` và quản lý timeout/executor phù hợp.

**Câu 8.** Một phương thức `@Async` trả `void` lỗi trong worker. Caller gắn `whenComplete` lên đâu?

**Đáp án:** Không có Future trả về để gắn. Cần exception handler/logging dành cho `@Async void`, hoặc thay hợp đồng method thành `CompletableFuture` nếu caller cần quan sát.

**Câu 9.** `get(timeout)` hết hạn và một `exceptionally` sau `orTimeout` trả cache. Có nghĩa I/O worker đã dừng không?

**Đáp án:** Không. Completion/fallback của future và lifecycle I/O task khác nhau; cần timeout/cancellation ở tầng thực thi.

### Ghi nhớ

**Chỉ quan sát lỗi** và **phục hồi bằng giá trị thay thế** là hai quyết định khác nhau. `whenComplete` giúp log/metrics và thông thường giữ nguyên outcome; `exceptionally` thay failure bằng value; `handle` có quyền tạo outcome mới trên cả thành công lẫn thất bại. Trong Spring `@Async`, hãy quan sát đúng stage caller nhận, cấu hình executor rõ ràng và tách recovery policy khỏi timeout, retry, transaction và side effects. Một fallback tốt có nghĩa nghiệp vụ, có giới hạn, có giám sát và không xóa dấu vết lỗi nguồn.

### Tài liệu tham khảo chính thức

- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): hợp đồng `whenComplete`, `exceptionally`, `handle`, `exceptionallyCompose`, `get`/`join` và completion state.
- [Java SE 17 — CompletionStage](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html): quy tắc các stage phụ thuộc và lỗi callback.
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `@Async`, executor selection, `Future` và quản lý exception.
- [Spring Framework 6.2 — @Async Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Async.html): kiểu return, proxy và future mà caller quan sát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-007">Không bỏ qua Future rồi đánh mất lỗi nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Không bỏ qua Future rồi đánh mất lỗi nghiệp vụ

## 1. Vấn đề: tác vụ thất bại nhưng hệ thống không biết

Một API Spring nhận yêu cầu tạo hóa đơn, gửi tác vụ sang executor và nhận một `Future` hoặc `CompletableFuture`. Sau đó code bỏ qua handle được trả về:

~~~java
// Anti-pattern: snippet trong service, không phải class độc lập.
invoiceWorker.generateInvoice(invoiceId); // CompletableFuture<Invoice>
return "Đã tiếp nhận";
~~~

Nếu worker ném `IllegalStateException` do hóa đơn không hợp lệ, lỗi **có thể được lưu trong Future** thay vì truyền tới luồng HTTP. Không gọi `get()/join()`, không gắn completion handler, không ghi trạng thái job và không có cơ chế giám sát khác thì tầng nghiệp vụ có thể mãi nghĩ việc đã thành công. Ngay cả khi Future đã `isDone() == true`, trạng thái ấy **không đồng nghĩa thành công**.

Các hệ quả thường gặp:

- Client nhận phản hồi “đã xử lý” nhưng không có hóa đơn; không thấy mã lỗi nghiệp vụ tương ứng.
- `ExecutorService.submit` bắt exception của `Callable` bên trong `FutureTask`, nên có thể **không xuất hiện stack trace từ thread uncaught-exception handler**.
- `CompletableFuture.supplyAsync` hoàn tất exceptionally nhưng không tự ép ứng dụng quan sát; chuỗi `thenApply` chỉ chạy khi thành công, còn các stage lỗi bị bỏ qua.
- `@Async` trả `Future/CompletableFuture` cho phép truyền lỗi cho caller qua kết quả, nhưng **caller phải sử dụng hoặc chuyển tiếp handle**.
- Lỗi từ một job quan trọng biến thành “silent failure”, ảnh hưởng tiền, tồn kho, email, audit và tính nhất quán.

**Quy tắc:** bất cứ khi nào code **gửi việc và nhận Future**, phải chỉ rõ **ai sở hữu Future, ai quan sát terminal state, ai ghi nhận lỗi nghiệp vụ, và ai thông báo hoặc retry**. Không nhất thiết phải block với `get()`; có thể gắn `whenComplete`, trả lại Future cho caller hoặc ghi trạng thái job ở cơ chế bền vững.

## 2. Future thực chất là gì?

### 2.1. execute, submit, CompletableFuture và @Async

| API | Người gọi nhận gì? | Lỗi trong task được xử lý theo đường nào? |
| --- | --- | --- |
| `Executor.execute(Runnable)` | `void` | Unchecked exception có thể thoát khỏi `run()` và được executor/thread uncaught handler xử lý; còn tùy implementation |
| `ExecutorService.submit(Callable<T>)` | `Future<T>` | Exception được lưu trong Future; `get()` ném `ExecutionException` có `getCause()` |
| `CompletableFuture.supplyAsync(supplier, executor)` | `CompletableFuture<T>` | Stage hoàn tất exceptionally; `join()` thường ném `CompletionException`, `get()` ném `ExecutionException` |
| `@Async` trả `CompletableFuture<T>` | Future proxy trả lại | Exception từ method có thể được quan sát trong Future; phải truyền/gắn handler |
| `@Async` trả `void` | Không có Future | Dùng `AsyncUncaughtExceptionHandler`/log/metrics do caller không nhận exception dưới dạng Future |
| Executor từ chối task khi submit | Có thể **không trả Future** | `RejectedExecutionException` (JDK) hoặc `TaskRejectedException` (Spring), tùy adapter/chính sách |

`Future` và `CompletableFuture` biểu diễn kết quả **sau này**, không phải bằng chứng nhiệm vụ thành công. Khi một task bị từ chối, hủy, mất khỏi queue hoặc kẹt I/O, đường xử lý cũng khác nhau. Thiết kế phải tính cả **submission failure** và **execution failure**, chứ không chỉ bắt exception trong method nghiệp vụ.

### 2.2. Một Future đã hoàn tất chưa chắc thành công

| Trạng thái | `Future.isDone()` | `get()` | Có cần xử lý? |
| --- | --- | --- | --- |
| Đang xếp hàng/chạy | `false` | Có thể chờ | Deadline, timeout, queue health |
| Thành công | `true` | Trả kết quả | Cập nhật `SUCCEEDED` |
| Ném lỗi nghiệp vụ | `true` | `ExecutionException` | Ghi `FAILED`, phân loại lỗi, retry khi phù hợp |
| Bị hủy | `true` | `CancellationException` | Ghi `CANCELLED` |
| Bị discard im lặng trước khi chạy | Có thể giữ `false` mãi | Có thể chờ vô hạn | Rejection minh bạch, giới hạn thời gian, reconciliation |

`CompletableFuture.isCompletedExceptionally()` trả `true` khi kết quả hoàn tất exceptional, gồm các trường hợp như cancellation. `Future.isDone()` không kể cho ta biết nguyên nhân, và chỉ gọi `isDone()` cũng không làm lỗi tự được ghi log.

## 3. Tại sao submit() có thể che lỗi?

Java `ExecutorService.submit(Callable<T>)` thường bọc task trong `RunnableFuture/FutureTask`. `FutureTask` bắt exception từ `Callable.call()` và ghi vào trạng thái Future. Một worker thread có thể xử lý task tiếp theo mà không in trực tiếp exception của task trước ra stderr. Nếu caller chỉ giữ hoặc bỏ qua reference `Future`, lỗi nghiệp vụ nằm đó mà không ai xử lý.

~~~java
// Snippet, giả sử executor là ExecutorService đang hoạt động.
Future<String> result = executor.submit(() -> {
    throw new IllegalArgumentException("invalid invoice");
});

// Không được bỏ result nếu nghiệp vụ yêu cầu hoàn tất.
// Không đồng nghĩa task thành công chỉ vì submit() trả về bình thường.
~~~

Cách quan sát đồng bộ có giới hạn:

~~~java
// Snippet tại ranh giới cần nhận kết quả (không phải chương trình độc lập).
try {
    String path = result.get(3, TimeUnit.SECONDS);
    markSucceeded(path);
} catch (ExecutionException ex) {
    Throwable businessCause = ex.getCause();
    markFailed(businessCause);
} catch (TimeoutException ex) {
    markTimedOut(ex);
    // Quyết định thêm việc cancel/retry theo contract nghiệp vụ.
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    markInterrupted(ex);
} catch (CancellationException ex) {
    markCancelled(ex);
}
~~~

`markSucceeded`/`markFailed`/`markTimedOut`/`markInterrupted`/`markCancelled` là các thao tác ứng dụng giả định. Cần import `java.util.concurrent.*`. `get(timeout)` chỉ giới hạn thời gian **caller chờ**; nó không tự cancel task và không biến Future chưa hoàn tất thành trạng thái terminal.

Một HTTP controller cần trả nhanh **không nên** gọi `get()` ngay sau submit rồi tự gọi đó là xử lý nền. Thay vào đó, trả một API asynchronous response phù hợp hoặc trả `202 Accepted` kèm job ID **sau khi thật sự nhận/lưu yêu cầu**, rồi cung cấp endpoint tra trạng thái.

## 4. Java 17: ví dụ độc lập minh họa lỗi bị giữ trong Future

Ví dụ hoàn chỉnh dưới đây không phụ thuộc race/thread scheduling để xác nhận kết quả: executor **một worker** chạy task lỗi, sau đó chạy task đánh dấu. `marker.get(...)` bảo đảm task lỗi đã kết thúc trước khi kiểm tra. Phần `CompletableFuture` dùng `failedFuture` và `whenComplete` để minh họa cách quan sát và cách *vô tình nuốt lỗi*.

Lưu đúng tên **`FutureFailureDemo.java`**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class FutureFailureDemo {
    static final class BusinessException extends RuntimeException {
        BusinessException(String message) {
            super(message);
        }
    }

    static Throwable unwrap(Throwable error) {
        while ((error instanceof ExecutionException
                || error instanceof CompletionException)
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Worker giữ BusinessException trong FutureTask.
            Future<String> failedSubmit = executor.submit(() -> {
                throw new BusinessException("invalid invoice");
            });

            // Worker đơn luồng xử lý marker sau failedSubmit.
            Future<String> marker = executor.submit(() -> "marker");
            System.out.println("MARKER=" + marker.get(3, TimeUnit.SECONDS));
            System.out.println("SUBMIT_DONE=" + failedSubmit.isDone());

            try {
                failedSubmit.get(3, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                System.out.println("SUBMIT_CAUSE="
                        + unwrap(ex).getClass().getSimpleName());
            }

            CompletableFuture<String> failed =
                    CompletableFuture.failedFuture(
                            new BusinessException("stock mismatch"));

            AtomicInteger observedCount = new AtomicInteger();
            CompletableFuture<String> observed =
                    failed.whenComplete((value, error) -> {
                        if (error != null) {
                            observedCount.incrementAndGet();
                        }
                    });

            try {
                observed.join();
            } catch (CompletionException ex) {
                System.out.println("OBSERVED_CAUSE="
                        + unwrap(ex).getClass().getSimpleName());
            }
            System.out.println("OBSERVATIONS=" + observedCount.get());

            // Fallback làm stage MỚI thành công; lỗi gốc vẫn còn.
            CompletableFuture<String> fallback =
                    failed.exceptionally(ex -> "DEFAULT");
            System.out.println("FALLBACK=" + fallback.join());

            String handled = failed.handle((value, error) ->
                    error == null ? value
                            : "FAILED:" + unwrap(error).getMessage()).join();
            System.out.println("HANDLED=" + handled);
            System.out.println("ORIGINAL_EXCEPTIONAL="
                    + failed.isCompletedExceptionally());
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch/chạy với JDK 17+:

~~~bash
javac --release 17 FutureFailureDemo.java
java FutureFailureDemo
~~~

Stdout **do chương trình in**, theo đúng thứ tự:

~~~text
MARKER=marker
SUBMIT_DONE=true
SUBMIT_CAUSE=BusinessException
OBSERVED_CAUSE=BusinessException
OBSERVATIONS=1
FALLBACK=DEFAULT
HANDLED=FAILED:stock mismatch
ORIGINAL_EXCEPTIONAL=true
~~~

**Giải thích:** task lỗi đã kết thúc khi marker được xử lý xong trên cùng executor đơn luồng. `SUBMIT_DONE=true` không biểu thị thành công; chỉ khi gọi `failedSubmit.get` mới thấy nguyên nhân `BusinessException`. Với `CompletableFuture.failedFuture`, `whenComplete` tăng biến đếm nhưng **không chữa lỗi**; `observed.join()` vẫn ném `CompletionException`. Ngược lại, `exceptionally` chuyển lỗi thành chuỗi fallback `DEFAULT` trong **stage mới**. Vì `failed` gốc vẫn exceptional, `ORIGINAL_EXCEPTIONAL=true`.

Ví dụ dùng giá trị xác định, không gọi mạng, không cần Spring, không cần `Thread.sleep` và không dựa vào thứ tự ngẫu nhiên giữa nhiều worker. Nó chỉ giải thích semantics; trong sản xuất phải thay `System.out` bằng logger/metrics và cập nhật trạng thái job.

## 5. Các công cụ của CompletableFuture: chọn đúng để không đánh mất lỗi

### 5.1. whenComplete: quan sát và chuyển tiếp trạng thái

~~~java
// Snippet, future có kiểu CompletableFuture<Invoice>.
CompletableFuture<Invoice> observed =
        future.whenComplete((invoice, error) -> {
            if (error != null) {
                log.error("invoice failed, invoiceId={}", invoiceId, error);
            }
        });
return observed;
~~~

`log`/`invoiceId` và kiểu `Invoice` thuộc service ứng dụng. `whenComplete` chạy cho cả thành công và lỗi, thích hợp cho structured logging, metric hoặc audit nhẹ. **Nó trả một Future mới mang cùng trạng thái/kết quả theo mặc định**, không biến lỗi thành thành công. Nếu callback tự ném lỗi trong khi upstream thành công, stage trả về cũng có thể lỗi; nếu upstream vốn lỗi, nguyên nhân của upstream được ưu tiên theo hợp đồng `CompletionStage`.

**Không gắn callback rồi bỏ luôn đường giám sát còn lại** nếu log cũng có thể thất bại. Với nghiệp vụ quan trọng, vẫn cần DB job status / outbox, retry và reconciliation. Ngoài ra `whenComplete` không tạo worker riêng: callback có thể chạy trên thread vừa hoàn tất stage hoặc trên caller khi stage đã xong. Giữ callback gọn và tránh I/O/blocking lâu trong completion thread.

### 5.2. handle: đổi cả thành công lẫn thất bại thành giá trị mới

~~~java
// Snippet trong service, kiểu Outcome do ứng dụng định nghĩa.
record Outcome<T>(String status, T value, String errorCode) { }

CompletableFuture<Outcome<Invoice>> outcome = future.handle(
        (value, error) -> error == null
                ? new Outcome<>("SUCCEEDED", value, null)
                : new Outcome<>("FAILED", null, mapErrorCode(error)));
~~~

`mapErrorCode` là method nghiệp vụ, snippet không độc lập. Vì `handle` trả giá trị mới, stage `outcome` có thể **hoàn tất bình thường ngay cả khi upstream lỗi**. Đây là cách đúng nếu contract của API là trả kết quả có trạng thái rõ ràng. Nhưng nếu chỉ `return null` hoặc trả đối tượng “success” mặc định khi có lỗi, bạn đang **che lỗi nghiệp vụ**.

### 5.3. exceptionally: phục hồi có chủ đích, không nuốt mọi lỗi

~~~java
// Snippet: chỉ cho phép fallback khi lỗi phụ trợ thật sự không quan trọng.
CompletableFuture<Recommendation> recommendations =
        remoteSuggestions.exceptionally(error -> {
            log.warn("recommendation service unavailable", error);
            return Recommendation.empty();
        });
~~~

Nếu gợi ý sản phẩm là tính năng phụ và API có fallback hợp lệ, hành vi trên có thể phù hợp. Nhưng nếu `remoteSuggestions` đang tính thuế, tồn kho, thanh toán hoặc trạng thái đơn hàng, trả `Recommendation.empty()` theo cách tương tự có thể che mất dữ liệu bắt buộc.

Đặc biệt không dùng một `exceptionally(ex -> null)` bao trùm mọi công việc quan trọng rồi quên gắn error status. `exceptionally` tạo một stage mới **thành công** nếu handler trả giá trị bình thường; caller phía sau không còn biết có lỗi nếu không lưu nguyên nhân trong kết quả hoặc metric.

### 5.4. thenApply và thenAccept không nhận nhánh lỗi

~~~java
// Sai khi dùng như cơ chế xử lý mọi lỗi:
future.thenAccept(value -> log.info("done: {}", value));
~~~

`thenAccept` chỉ chạy khi upstream thành công. Nếu upstream exceptional, callback thành công không chạy; **stage trả về vẫn exceptional**. Nếu không giữ stage đó và không có observer khác, lỗi vẫn có thể bị bỏ quên.

Đúng hơn:

~~~java
// Snippet minh họa: theo dõi cả hai kết quả.
CompletableFuture<Void> tracked = future.whenComplete(
        (value, error) -> {
            if (error != null) {
                log.error("failed", error);
            } else {
                log.info("done: {}", value);
            }
        }).thenApply(ignored -> null);
~~~

Ở đây `tracked` được giữ hoặc trả cho caller; ví dụ `thenApply(ignored -> null)` đổi kiểu giá trị trả thành `Void` sau thành công. Nếu upstream lỗi thì nhánh `thenApply` không chạy và stage vẫn thất bại. Khi chỉ cần quan sát, không bắt buộc biến đổi thành Void; thường đủ `return future.whenComplete(...)`.

### 5.5. get() và join() khác nhau ở kiểu exception

| API | Lỗi trong task | Interrupted/cancellation |
| --- | --- | --- |
| `Future.get()` | `ExecutionException`, lấy `getCause()` | `InterruptedException` hoặc `CancellationException` |
| `Future.get(timeout, unit)` | Như trên, có thêm `TimeoutException` khi quá hạn chờ | Hạn chờ không tự hủy task |
| `CompletableFuture.join()` | Thường `CompletionException` với cause gốc | Không ném checked `InterruptedException`; cancellation có thể ném `CancellationException` |
| `CompletableFuture.get()` | `ExecutionException` | Cũng là `Future` nên có checked interruption/timeout variant |

Tại ranh giới API và logging, thường nên **unwrap `ExecutionException`/`CompletionException`** rồi phân loại cause gốc, giữ stack trace và context. Không gói mọi lỗi thành `RuntimeException("failed")` mà không truyền cause; làm như vậy mất thông tin chẩn đoán.

## 6. Spring @Async: trả Future để lỗi có đường truyền về

### 6.1. Lỗi nghiệp vụ xảy ra sau khi dispatch

Một Spring-managed bean:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class InvoiceWorker {
    @Async("invoiceExecutor")
    public CompletableFuture<String> generate(String id) {
        if ("BAD".equals(id)) {
            throw new IllegalArgumentException("invoice rejected");
        }
        return CompletableFuture.completedFuture("invoice/" + id);
    }
}
~~~

Khi được gọi từ **bean khác qua Spring proxy** và `@EnableAsync` đã bật, method chạy trên executor mang tên `invoiceExecutor`. Với return type `CompletableFuture`, exception của target được biểu diễn bằng exceptional completion, để caller có thể `whenComplete` hoặc `get()`. Đừng tự gọi `this.generate(...)` từ cùng class rồi mong interceptor được kích hoạt.

Nếu đổi signature thành `@Async public void generate(...)`, caller không có Future để truyền thất bại. Khi đó lỗi sẽ đi qua `AsyncUncaughtExceptionHandler` (mặc định Spring chủ yếu log), và phải có kênh quan sát riêng nếu công việc có ảnh hưởng nghiệp vụ.

### 6.2. Ví dụ Spring Boot 3.3.5 chạy được

Ví dụ Java 17 + Maven sau có một công việc thành công và một công việc ném lỗi. `ApplicationRunner` **cố ý chờ có hạn** để in một kết quả ổn định; trong HTTP API sản xuất, nên trả Future, callback hoặc job ID thay vì block request tương tự.

Cấu trúc:

~~~text
spring-observed-future/
├── pom.xml
└── src/main/java/com/example/failure/
    ├── AsyncFailureApplication.java
    └── InvoiceWorker.java
~~~

`pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>spring-observed-future</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`src/main/java/com/example/failure/AsyncFailureApplication.java`:

~~~java
package com.example.failure;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableAsync
public class AsyncFailureApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                     SpringApplication.run(AsyncFailureApplication.class, args)) {
            // Demo kết thúc sau khi ApplicationRunner đã kiểm tra Future.
        }
    }

    @Bean("invoiceExecutor")
    ThreadPoolTaskExecutor invoiceExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("invoice-");
        pool.setWaitForTasksToCompleteOnShutdown(true);
        return pool;
    }

    @Bean
    ApplicationRunner verify(InvoiceWorker worker) {
        return args -> {
            CompletableFuture<String> ok = worker.generate("A-1");
            CompletableFuture<String> failed = worker.generate("BAD");

            AtomicInteger errorCount = new AtomicInteger();
            CompletableFuture<String> observed =
                    failed.whenComplete((value, error) -> {
                        if (error != null) {
                            errorCount.incrementAndGet();
                        }
                    });

            // Chuyển lỗi thành trạng thái HIỂN THỊ có chủ đích cho demo.
            CompletableFuture<String> report = observed.handle(
                    (value, error) -> error == null
                            ? "OK"
                            : "FAILED:" + root(error).getClass().getSimpleName());

            String success = ok.get(5, TimeUnit.SECONDS);
            String failureStatus = report.get(5, TimeUnit.SECONDS);

            System.out.println("SUCCESS=" + success);
            System.out.println("FAILURE_STATUS=" + failureStatus);
            System.out.println("ERROR_OBSERVATIONS=" + errorCount.get());
            System.out.println("ORIGINAL_FAILED="
                    + failed.isCompletedExceptionally());
        };
    }

    private static Throwable root(Throwable error) {
        while ((error instanceof CompletionException
                || error instanceof ExecutionException)
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }
}
~~~

`src/main/java/com/example/failure/InvoiceWorker.java`:

~~~java
package com.example.failure;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class InvoiceWorker {
    @Async("invoiceExecutor")
    public CompletableFuture<String> generate(String id) {
        if ("BAD".equals(id)) {
            throw new IllegalArgumentException("invoice rejected");
        }
        return CompletableFuture.completedFuture("invoice/" + id);
    }
}
~~~

Chạy:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua startup/shutdown log, bốn dòng in từ `ApplicationRunner` có nội dung:

~~~text
SUCCESS=invoice/A-1
FAILURE_STATUS=FAILED:IllegalArgumentException
ERROR_OBSERVATIONS=1
ORIGINAL_FAILED=true
~~~

**Vì sao ổn định?** Mọi biến được đọc **sau** `report.get(5, SECONDS)`; `report` chỉ hoàn tất sau khi `whenComplete` tăng counter và `handle` tạo trạng thái cho lỗi. Tên luồng, thứ tự hai worker hoàn tất và log startup không ảnh hưởng bốn giá trị này. Lỗi nghiệp vụ vẫn còn ở `failed` gốc, trong khi `report` là biểu diễn có chủ đích cho demo.

Khi chuyển sang production, thay `errorCount` bằng structured logging, metrics và trạng thái job; không in stack trace nhạy cảm hoặc mã đơn hàng đầy đủ vào log không được kiểm soát. Demo không chạy network, DB hoặc transaction, nên chưa có các bảo đảm bền vững.

## 7. @Async void: không có Future thì ai tiếp nhận lỗi?

Với method `void`:

~~~java
// Snippet trong Spring-managed bean.
@Async("invoiceExecutor")
public void sendReceipt(String invoiceId) {
    throw new IllegalStateException("mail gateway failed");
}
~~~

Caller không nhận được Future, do đó không thể dùng `get()`. Theo Spring, lỗi chưa bắt được chuyển đến `AsyncUncaughtExceptionHandler`. Có thể cấu hình handler:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

@Configuration
@EnableAsync
public class AsyncErrorConfiguration implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (error, method, parameters) -> {
            System.err.println("ASYNC_VOID_ERROR method="
                    + method.getName()
                    + ", cause=" + error.getClass().getSimpleName());
        };
    }
}
~~~

Đây là class cấu hình Spring đầy đủ, nhưng cần nằm trong một ứng dụng/context có bean async và executor tương ứng; không phải chương trình `main` độc lập. `System.err` dùng để minh họa, không phải hệ thống observability production. `@EnableAsync` chỉ cần được bật đúng context; nếu đã có cấu hình tương ứng thì không cần khai báo trùng.

**Hạn chế:** handler có thể log/metric và mở quy trình xử lý khác, nhưng **không trả kết quả trực tiếp tới caller**. Với nghiệp vụ phải bảo đảm hoàn tất hoặc retry, thường nên dùng `CompletableFuture<T>` (hoặc hàng đợi bền vững), vì có thể kết hợp phản hồi, timeout và truyền exception. Đừng cho rằng cấu hình handler là đủ để đảm bảo email/audit/thanh toán được thực hiện chính xác.

## 8. Khi trả Future cho caller: ai chịu trách nhiệm quan sát?

Một anti-pattern trong controller:

~~~java
// Snippet trong @RestController; không độc lập.
@PostMapping("/invoices/{id}/generate")
public ResponseEntity<Void> generate(@PathVariable String id) {
    worker.generate(id); // Bỏ mất Future.
    return ResponseEntity.accepted().build();
}
~~~

Phản hồi `202 Accepted` có thể phù hợp nếu hệ thống **thật sự đã tiếp nhận** yêu cầu để xử lý sau, có cách tra trạng thái và xử lý rejection. Nhưng nếu chỉ gửi lên một in-memory pool không có job ledger, khi task thất bại hoặc ứng dụng restart, caller không thể biết chuyện gì xảy ra. Ngoài ra `worker.generate` có thể ném `TaskRejectedException` **ngay lúc submit** nếu pool đầy; phải chuyển trường hợp này thành phản hồi có ý nghĩa thay vì trả 202 sai.

### 8.1. Contract chờ kết quả: trả CompletableFuture lên tầng biết xử lý

~~~java
// Snippet trong Spring service/gateway.
public CompletableFuture<Invoice> generate(String id) {
    return worker.generateInvoice(id)
            .whenComplete((value, error) -> {
                if (error != null) {
                    log.error("Invoice processing failed, id={}", id, error);
                }
            });
}
~~~

Ở đây `worker`, `Invoice` và `log` là thành phần do ứng dụng cung cấp. Người gọi phải tiếp tục trả Future (cho MVC async response nếu phù hợp) hoặc gắn terminal handler. Không phải cách đúng nếu gateway tự gọi xong rồi **bỏ cả Future lẫn việc ghi trạng thái**. Một completion callback có thể log lỗi nhưng không tự bảo đảm task tồn tại sau khi ứng dụng restart.

### 8.2. Contract job nền: ghi trạng thái bền vững

Thay vì coi Future trong RAM là job ledger, dùng một bảng trạng thái trong DB, ví dụ:

~~~text
PENDING -> RUNNING -> SUCCEEDED
                  \-> FAILED
          \-> REJECTED
          \-> TIMED_OUT / CANCELLED (theo quy tắc hệ thống)
~~~

Một quy trình đáng tin cậy hơn:

1. Nhận request với **idempotency key** và tạo bản ghi job `PENDING` trong transaction.
2. Ghi/dispatch công việc bằng cơ chế có thể phục hồi (outbox/message broker hoặc dispatcher đáng tin cậy). Chỉ phản hồi đã chấp nhận khi điều kiện tiếp nhận của API thật sự thỏa.
3. Worker claim job và chuyển `RUNNING`, thực thi với timeout I/O và kiểm tra điều kiện hủy.
4. Khi thành công, ghi `SUCCEEDED` và kết quả; khi lỗi, lưu `FAILED` cùng error code/trạng thái retry, không chỉ log stack trace.
5. Sau restart, reconciliation tìm job `PENDING/RUNNING` quá hạn và quyết định retry/compensation, đồng thời đảm bảo idempotency của side effects.

Trên nhiều pod, một `CompletableFuture` ở JVM của pod A không truy cập được từ pod B; DB/job store cần là nguồn dữ liệu chia sẻ. Không coi ghi trạng thái DB và side effect bên ngoài là một transaction nguyên tử nếu thực tế chúng không cùng transaction; dùng outbox và idempotency khi cần.

## 9. Xử lý submission rejection, timeout và cancellation

### 9.1. Hai thời điểm lỗi khác nhau

**Submission failure:** ngay khi gọi `executor.execute/submit` hoặc lời gọi `@Async` qua proxy, executor có thể từ chối vì shutdown/queue đầy, ném `RejectedExecutionException` (JDK) hoặc `TaskRejectedException` (Spring). Nếu call chưa trả Future, không có handle để gắn `whenComplete`.

~~~java
// Snippet minh họa tại nơi dispatch Spring @Async.
try {
    CompletableFuture<String> future = worker.generate("A-1");
    return future.whenComplete((value, error) -> recordOutcome(value, error));
} catch (org.springframework.core.task.TaskRejectedException ex) {
    recordRejected(ex);
    throw ex;
}
~~~

`recordOutcome`/`recordRejected` là phương thức nghiệp vụ giả định. Với API, có thể map sang `503 Service Unavailable` hoặc `429 Too Many Requests` theo contract. Nếu dùng `DiscardPolicy` hoặc `DiscardOldestPolicy` im lặng, `Future` có thể pending mãi mà không có lỗi hiển thị; ưu tiên policy ném lỗi và theo dõi saturation.

**Execution failure:** job đã được nhận và chạy, nhưng thao tác thực tế ném exception; Future hoàn tất exceptional, hãy dùng observer hoặc kết quả trả lên caller. Hai đường lỗi cần test riêng.

### 9.2. Timeout không đồng nghĩa task đã dừng

~~~java
// Snippet trên một CompletableFuture do ứng dụng sở hữu.
CompletableFuture<String> bounded =
        worker.generate("A-1").orTimeout(10, TimeUnit.SECONDS);
~~~

`orTimeout` (Java 9+) sẽ làm CompletableFuture đó hoàn tất exceptionally với `TimeoutException` nếu chưa hoàn tất sau hạn, nhưng **không tự interrupt hoặc dừng worker**, không tự rollback DB và không tự xóa task đã xếp hàng. Nó trả **chính Future đang gọi**, ảnh hưởng cả các tham chiếu khác tới cùng object. Nếu cần một timeout chỉ ở lớp quan sát mà không muốn mutate upstream Future, hãy tạo stage riêng hoặc wrapper phù hợp rồi quản lý timeout của riêng stage.

Tương tự, `future.get(10, SECONDS)` chỉ kết thúc thời gian **caller đợi**; nó không tự hoàn tất hoặc hủy Future.

`future.cancel(true)` là một yêu cầu hủy, không bảo đảm dừng tác vụ phớt lờ interrupt. Với `CompletableFuture`, cancellation là một dạng exceptional completion; tham số `mayInterruptIfRunning` không trực tiếp điều khiển interrupt job upstream theo cách `FutureTask` có thể làm.

Nếu HTTP client đã timeout nhưng worker còn ghi hóa đơn, request retry có thể tạo hóa đơn hai lần. Cần idempotency, transaction boundary thích hợp, deadline truyền xuống I/O, và trạng thái job để ghi nhận kết quả muộn.

## 10. Không để exception handler vô tình phá mất tín hiệu lỗi

### 10.1. Rất nhiều code “bắt lỗi” nhưng lại trả success

~~~java
// Anti-pattern: biến mọi lỗi thành kết quả rỗng không phân biệt.
CompletableFuture<List<Invoice>> maybe =
        invoiceFuture.exceptionally(error -> List.of());
~~~

Nếu `List.of()` ở đây được API giải thích là “không có hóa đơn” thì đang đánh đồng *lỗi hệ thống* với *không có dữ liệu*. Chỉ dùng fallback khi nghiệp vụ định nghĩa rõ đó là phản hồi hợp lệ và ghi nhận trạng thái degraded.

Một mẫu có trạng thái rõ hơn:

~~~java
// Snippet với record và nghiệp vụ trong service.
record JobResult<T>(String status, T value, String errorCode) { }

CompletableFuture<JobResult<Invoice>> visible = invoiceFuture.handle(
        (value, error) -> {
            if (error == null) {
                return new JobResult<>("SUCCEEDED", value, null);
            }
            return new JobResult<>("FAILED", null, classify(error));
        });
~~~

`classify` do ứng dụng định nghĩa; cần phân loại giữa lỗi business không nên retry và lỗi hạ tầng có thể retry. Tránh gửi stack trace, SQL query hay thông tin nhạy cảm cho client; lưu logs riêng và expose error code tối thiểu.

### 10.2. Unwrap exception nhưng vẫn giữ nguyên nhân gốc

Các stage có thể thêm `CompletionException` hoặc `ExecutionException`. Khi ghi log/metric:

~~~java
// Snippet helper, thích hợp đặt trong class utility.
static Throwable rootCause(Throwable error) {
    while ((error instanceof CompletionException
            || error instanceof ExecutionException)
            && error.getCause() != null) {
        error = error.getCause();
    }
    return error;
}
~~~

Helper này chỉ gỡ **wrapper của concurrency API**, không nhất thiết đi xuyên mọi `cause` nghiệp vụ. Ví dụ một `BusinessRuleException` có một `SQLException` làm cause thì **mã lỗi nghiệp vụ** có thể phải giữ `BusinessRuleException` làm root để phân loại, đồng thời log cả stack trace. Không tùy tiện lấy deepest cause và làm mất ngữ nghĩa nghiệp vụ.

### 10.3. Callback thất bại cũng cần được theo dõi

`future.whenComplete(...)` trả **một stage mới**. Nếu callback ghi DB/log ném exception, stage mới có thể exceptional; nếu chỉ quan sát stage gốc, có thể bỏ sót lỗi của chính observer. Vì vậy:

- Giữ/trả stage từ `whenComplete` nếu callback là một phần bắt buộc của luồng nghiệp vụ.
- Tránh làm blocking I/O nặng trong completion callback; nếu cần write bền vững, thiết kế bước riêng với retry.
- Dùng metric cảnh báo observer/persistence failures và reconciler đối chiếu job ledger.
- `thenApplyAsync`/`whenCompleteAsync` có thể cần executor riêng; nếu executor ấy quá tải, stage tiếp nối cũng có thể lỗi hoặc bị treo tùy rejection policy.

### 10.4. CompletableFuture.allOf() có thể che lỗi thành phần

~~~java
// Snippet: không giả định việc tạo barrier là thu được giá trị.
CompletableFuture<Void> all =
        CompletableFuture.allOf(priceFuture, stockFuture, taxFuture);
~~~

`allOf` hoàn tất khi **tất cả** các Future con hoàn tất; nếu có lỗi, barrier cũng exceptional. Nếu không ai xử lý `all` hoặc Future con, kết quả tổng hợp vẫn có thể bị bỏ quên. Khi cần quan sát từng nguồn, gắn `whenComplete`/`handle` cho **từng Future con** trước khi tạo barrier, rồi tùy contract chọn fail toàn bộ hay trả báo cáo partial failure có trạng thái rõ. `allOf` không tự lưu danh sách các kết quả hoặc mọi exception.

## 11. Transaction, logging và điều kiện “nghiệp vụ đã thành công”

- **Transaction:** `@Transactional` của HTTP thread không tự chạy theo worker thread. Đặt transaction ở đường xử lý của worker/service được Spring proxy quản lý. Nếu worker bắt exception rồi trả `completedFuture("OK")`, transaction có thể không rollback theo kỳ vọng; quản lý rollback/exception propagation theo quy tắc nghiệp vụ.
- **Log:** đưa job ID, trace ID, source, error code và stack trace vào structured logs; tránh chỉ in “task failed” không có định danh. Thread-local MDC/security không tự truyền qua thread khác nếu không cấu hình propagation.
- **Metric:** theo dõi `submitted`, `running`, `succeeded`, `failed`, `rejected`, `timed_out`, `cancelled` và `pending_over_deadline`; số job hoàn tất thành công không nói được có bao nhiêu job thất bại bị bỏ quên.
- **Alert:** báo động khi job chạy quá deadline, tỷ lệ business failure tăng hoặc hàng đợi bão hòa. Với tác vụ bắt buộc, log lỗi chưa chắc đủ; phải có người/cơ chế tiếp nhận và hành động.
- **Retry:** chỉ retry các lỗi phù hợp (ví dụ HTTP 503 tạm thời), giới hạn số lần và backoff. Business validation 4xx thường không retry mù. Retry cần idempotency để không tạo side effect trùng.
- **Shutdown:** khi process dừng, task chưa chạy trong queue có thể không bao giờ có kết quả; cần quản lý graceful shutdown, cancellation và lưu trạng thái bền vững với job quan trọng.
- **Chia sẻ trạng thái:** Java Future nằm trong RAM của một JVM; nhiều instance cần kho chung để giữ kết quả, retry và trạng thái terminal.

### Các trạng thái lỗi không nên đánh đồng

| Trạng thái | Ví dụ | Xử lý đề xuất |
| --- | --- | --- |
| `FAILED_BUSINESS` | Không đủ tồn kho, đơn không hợp lệ | Ghi error code, báo caller; thường không retry vô hạn |
| `FAILED_TRANSIENT` | Remote service 503, tạm mất kết nối | Retry có giới hạn, exponential backoff + jitter |
| `REJECTED` | Executor bão hòa | Backpressure, 429/503, queue bền vững khi cần |
| `TIMED_OUT` | Deadline hết nhưng worker chưa chắc dừng | Đánh dấu trạng thái cần đối soát, idempotency, kiểm tra kết quả muộn |
| `CANCELLED` | Hủy được Future | Bảo đảm UI/status phản ánh cancellation, lưu ý side effect có thể đã xảy ra |
| `SUCCEEDED` | Kết quả nghiệp vụ và side effect đã được xác nhận | Ghi kết quả/metadata cần thiết |

Không dùng một exception handler chung bắt `Throwable` rồi trả mã `200 OK` cho tất cả trường hợp; cần phân loại và trả contract nhất quán.

## 12. Checklist kiểm thử và review

Một test đáng tin nên có **ít nhất năm nhánh**: thành công, business exception, timeout, submission rejection và cancellation. Ngoài ra cần kiểm tra observer/retry và shutdown trong sản phẩm quan trọng.

1. **Đã giữ handle?** Mỗi `submit/supplyAsync/@Async` có caller nhận và xử lý Future, hoặc một watcher/job store rõ ràng?
2. **Đã quan sát terminal failure?** `whenComplete` có nhánh `error != null`, `get/join` có catch đúng kiểu, hoặc `handle` trả outcome lỗi tường minh?
3. **Có bỏ stage trả về của callback?** Nếu `whenComplete` là thao tác bắt buộc, có theo dõi chính stage mới khi callback thất bại không?
4. **Có đang nuốt lỗi?** Tìm `exceptionally(ex -> null)`, `catch (Exception e) { return defaultValue; }`, `Future<?>` không dùng, `thenAccept` chỉ log success.
5. **Chữ ký Spring đúng?** `@Async` trả `void`, `Future<T>` hoặc `CompletableFuture<T>`; method public và được gọi qua proxy từ bean khác; executor cụ thể được xác định?
6. **Lỗi xảy ra lúc submit hay lúc execute?** Có kiểm tra `TaskRejectedException`/queue saturation mà không cần đợi Future vốn chưa được trả?
7. **Có deadline?** `get(timeout)`/`orTimeout` và timeout I/O thực sự, cộng theo dõi task chạy muộn?
8. **HTTP contract?** Trả 202 chỉ khi yêu cầu đã được tiếp nhận đúng nghĩa; response/status rõ cho `FAILED/REJECTED`, không báo thành công giả?
9. **Trạng thái bền vững?** Với job quan trọng, có job ledger, outbox/broker, retry, idempotency và reconciliation sau restart?
10. **Có log có tương quan?** Job ID, error code, cause, trace và metric, nhưng không rò dữ liệu nhạy cảm?
11. **Test worker thật?** Test có kiểm tra một `@Async` bị business exception trên thread của pool và đúng proxy path, không chỉ test method trực tiếp?
12. **Test allOf/partial failure?** Có case một child thất bại, một child pending, deadline và thu trạng thái riêng từng nguồn?

### Câu hỏi tự kiểm tra

1. Tại sao `ExecutorService.submit` trả về một `Future` nhưng exception của `Callable` có thể không xuất hiện ở stderr?
2. `future.isDone() == true` có đủ chứng minh nghiệp vụ đã thành công chưa?
3. Nếu `CompletableFuture` thất bại, `thenApply` hoặc `thenAccept` có chạy không? Đặt observer lỗi ở đâu?
4. `whenComplete` khác `handle` và `exceptionally` về việc giữ nguyên hay biến đổi trạng thái lỗi như thế nào?
5. Tại sao `get(5, SECONDS)` hết hạn không có nghĩa worker đã bị hủy hoặc DB transaction rollback?
6. Trong Spring `@Async void`, caller lấy exception qua đâu? Khi nào nên chuyển sang return type `CompletableFuture<T>`?
7. Nếu pool từ chối ngay khi submit, `whenComplete` trên Future có xử lý được không nếu Future chưa hề trả về?
8. Một API báo 202 nhưng không có job ID hay job store, chuyện gì xảy ra khi process bị kill giữa chừng?
9. Nếu một callback ghi audit bị lỗi, vì sao chỉ giám sát Future gốc có thể không đủ?
10. `exceptionally(ex -> fallback)` được phép ở trường hợp nào, và trường hợp nào nó biến lỗi nghiêm trọng thành kết quả “thành công” sai?

**Kết luận:** Future là **kênh mang kết quả và lỗi**, không phải một lệnh “gửi rồi quên”. Hãy giữ hoặc chuyển tiếp handle, quan sát mọi nhánh hoàn tất, phân loại lỗi nghiệp vụ, gắn deadline và ghi trạng thái bền vững khi cần. Khi không giữ Future, phải có một cơ chế quan sát khác được thiết kế rõ ràng; không để exception bị giữ trong Future rồi biến mất khỏi luồng nghiệp vụ.

## Tài liệu tham khảo chính thức

- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Spring Framework — Task Execution, @Async và exception handling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — AsyncUncaughtExceptionHandler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/interceptor/AsyncUncaughtExceptionHandler.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-008">Giới hạn bắt lỗi bằng TaskDecorator khi tác vụ được bọc trong Future</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn bắt lỗi bằng TaskDecorator khi tác vụ được bọc trong Future

## 1. Vấn đề: vì sao try/catch ở TaskDecorator không bắt được lỗi?

Trong Spring Framework, `ThreadPoolTaskExecutor#setTaskDecorator(...)` cho phép bọc mã chạy một `Runnable` bằng logic bổ sung: thiết lập ngữ cảnh log, đo thời gian thực thi, ghi metric, làm sạch `ThreadLocal` hoặc quan sát lỗi **thoát ra từ lời gọi `run()`**.

Một mẫu thường gặp:

```java
executor.setTaskDecorator(task -> () -> {
    try {
        task.run();
    } catch (RuntimeException ex) {
        logger.error("Task failed", ex);
        throw ex;
    }
});
```

Đây chỉ là **snippet minh họa** với `executor` và `logger` đã tồn tại. Nhìn vào `catch`, ta dễ nghĩ “mọi task ném exception đều được log ở đây”. Thực tế **không đúng** khi tác vụ đã được bọc trong một `FutureTask` hoặc callback tương tự:

- Với một `Runnable` gốc được chạy trực tiếp qua đường `execute(Runnable)`, exception không bị một wrapper khác giữ lại có thể **thoát ra từ `task.run()`**, nên decorator bắt được.
- Với `submit(Runnable)` hoặc `submit(Callable)`, framework/executor thường tạo **`FutureTask`**: `FutureTask.run()` bắt lỗi nghiệp vụ và **lưu lỗi vào trạng thái của Future**. Nó trả về bình thường ở bề mặt `Runnable.run()`, vì vậy `catch` bên ngoài **không được gọi**.
- Với `@Async` trả `Future`/`CompletableFuture` hoặc các task đã được bọc nhiều tầng, callback thực sự được decorate **có thể không còn là Runnable/Callable người dùng truyền vào**. Không được suy luận kiểu hoặc quy tắc bắt lỗi chỉ từ chữ ký `TaskDecorator.decorate(Runnable)`.

Đây chính là giới hạn được nêu trong **Javadoc của Spring `ThreadPoolTaskExecutor.setTaskDecorator`**: decorator phù hợp để bổ sung context/monitoring cho callback thực thi, nhưng việc bắt lỗi có thể chỉ thấy exception của `Runnable` trực tiếp. Với đường `submit`, `FutureTask` không truyền exception ra khỏi `run()`; nếu cần quan sát lỗi, có thể phải truy cập `Future.get()`.

**Mục tiêu của bài:** hiểu ranh giới xảy ra lỗi, tái hiện bằng Java 17, nhận diện hành vi Spring và chọn vị trí xử lý exception đáng tin cậy thay vì kỳ vọng mọi lỗi đều tới `TaskDecorator.catch`.

## 2. TaskDecorator có tác dụng gì, và không có quyền gì?

Interface trong Spring:

```java
@FunctionalInterface
public interface TaskDecorator {
    Runnable decorate(Runnable runnable);
}
```

Đây là chữ ký rút gọn để học khái niệm, không phải mã source đầy đủ của Spring. Nó nhận một `Runnable` mà executor sẽ chạy và trả về một `Runnable` khác, thường cũng gọi lại `runnable.run()`.

```text
Caller / @Async proxy / ExecutorService
       |
       v
  Task submitted
       |
       v
  (có thể được bọc trong FutureTask / callback nội bộ)
       |
       v
  TaskDecorator.decorate(actualRunnable)
       |
       v
  decoratedRunnable chạy trên worker
       |
       +--> setup context / start timer
       |
       +--> actualRunnable.run()
       |       |
       |       +--> lỗi có thể THOÁT RA
       |       +--> hoặc bị FutureTask giữ lại
       |
       +--> finally: cleanup context / record duration
```

TaskDecorator rất hữu ích khi cần:

1. Chép/khôi phục ngữ cảnh log từ thread submit sang worker (ví dụ correlation ID/MDC).
2. Đặt `ThreadLocal` theo một scope rõ ràng, rồi khôi phục hoặc xóa ở `finally` để tránh dữ liệu của request trước rò sang task sau.
3. Đo thời gian callback thực thi và ghi metric với tag giới hạn cardinality.
4. Bổ sung context tracing/observation, nếu ứng dụng có đúng thư viện và cơ chế context propagation.
5. Đánh dấu vòng đời task ở cấp executor, không gắn logic đo đạc vào mọi method nghiệp vụ.

**TaskDecorator không phải:**

- Một error handler toàn cục hiểu mọi `Future`, `CompletableFuture` hoặc exception của `@Async`.
- Một `try/catch` có thể nhìn xuyên qua exception đã bị `FutureTask` ghi lại trong state.
- Một cơ chế tự hủy task, retry, rollback transaction hoặc cam kết lỗi sẽ được log đúng một lần.
- Một cơ chế tự động bao phủ công việc không được nộp qua executor bean có decorator đó.
- Một cơ chế bắt mọi lỗi từ các việc bất đồng bộ *được khởi tạo bởi* task gốc nhưng tiếp tục chạy sau khi `Runnable.run()` đã trả về.

Điều Spring decorate là **callback thực tế**, không nhất thiết là chính đối tượng `Runnable` hay `Callable` mà caller ban đầu cung cấp. Vì thế việc ép mọi callback thành kiểu ứng dụng tự định nghĩa thường không an toàn.

## 3. Phân biệt execute, submit và FutureTask

### 3.1. execute(Runnable): lỗi có thể thoát khỏi run()

Với JDK `Executor.execute(Runnable)` hoặc Spring `TaskExecutor.execute(Runnable)`, khi underlying executor thực sự chạy một `Runnable` trực tiếp và không có lớp bọc giữ lỗi:

```java
executor.execute(() -> {
    throw new IllegalStateException("direct failure");
});
```

Exception có thể thoát khỏi `Runnable.run()`, đi qua wrapper của decorator, rồi được xử lý bởi cơ chế exception/uncaught exception của thread hoặc executor. Nếu decorator bắt rồi ném lại, worker có thể chết và pool thay thread theo chính sách triển khai.

**Giới hạn của câu này:** “gọi `execute`” **không đủ** để bảo đảm callback decorate chính là lambda gốc. Spring `@Async`, `CompletableFuture`, các adapter khác hoặc middleware có thể đã bọc tác vụ trước đó. Hãy kiểm tra đường thực thi thật trước khi tin rằng decorator sẽ thấy mọi exception.

### 3.2. submit(...): lỗi được lưu trong FutureTask

JDK `ExecutorService.submit(Callable)` trả về `Future<T>`:

```java
Future<String> future = executor.submit(() -> {
    throw new IllegalArgumentException("task failed");
});

// Ở code sở hữu kết quả (không phải mặc định trong TaskDecorator):
try {
    String value = future.get();
} catch (ExecutionException ex) {
    Throwable cause = ex.getCause(); // IllegalArgumentException
    // Ghi nhận và quyết định xử lý theo nghiệp vụ.
}
```

Đây là snippet cần biến `executor` và các import JDK. Khi dùng `FutureTask`, lời gọi `run()` bên ngoài không cần ném `IllegalArgumentException` lên decorator; nó lưu lỗi để `Future.get()` báo `ExecutionException` với `cause` là lỗi gốc.

`FutureTask` triển khai **cả `Runnable` và `Future`** (qua `RunnableFuture`). Điều này giải thích vì sao nó có thể được worker chạy như `Runnable`, nhưng vẫn giữ trạng thái kết quả/ngoại lệ cho phía gọi.

### 3.3. Bảng đối chiếu

| Tình huống | Callback `run()` có thể ném ra lỗi nghiệp vụ? | Vị trí quan sát lỗi đáng tin cậy |
| --- | --- | --- |
| `execute(plainRunnable)`, không wrapper giữ lỗi | Có | `catch` quanh `run()`; uncaught handler/logger của executor |
| `submit(Runnable)` qua `FutureTask` | Thường không: lỗi nằm trong Future | `Future.get()` → `ExecutionException.getCause()` |
| `submit(Callable)` qua `FutureTask` | Thường không | `Future.get()` → `ExecutionException.getCause()` |
| `CompletableFuture.supplyAsync(...)` | Không nên giả định | `whenComplete`, `handle`, `exceptionally` hoặc `join/get` |
| Spring `@Async` trả `Future/CompletableFuture` | Không nên giả định TaskDecorator nhìn thấy | Future trả về caller; kiểm tra hoàn tất/lỗi |
| Spring `@Async void` | Không trả Future cho caller | `AsyncUncaughtExceptionHandler` |
| Submission bị pool/queue từ chối | Tác vụ chưa chạy | `TaskRejectedException` hoặc `RejectedExecutionException` **tại submitter** |

**Lưu ý:** `CompletableFuture.join()` ném `CompletionException` đối với thất bại thông thường; `Future.get()` ném `ExecutionException`. Với trường hợp cancellation, cần xử lý `CancellationException` riêng.

## 4. Cơ chế FutureTask.run(): vì sao catch không được kích hoạt?

Trình tự khi một `Callable` bị bọc bởi `FutureTask`:

```text
Caller: submit(callable)
            |
            v
        FutureTask(callable)   <-- implements RunnableFuture
            |
            v
TaskDecorator sees a Runnable that may be this FutureTask
            |
            v
decoratedRunnable.run()
            |
            +--> try {
            |        futureTask.run()
            |            |
            |            +--> callable.call() throws IllegalArgumentException
            |            |
            |            +--> FutureTask records exceptional state
            |            +--> returns normally from run()
            |    } catch (RuntimeException ex) {
            |        // KHÔNG chạy cho lỗi đã bị FutureTask giữ
            |    }
            |
            v
Caller calls Future.get()
            |
            +--> ExecutionException(cause=IllegalArgumentException)
```

**Cơ chế quan trọng nhất:** một `FutureTask` có thể hoàn thành ở trạng thái **exceptional** mà phương thức `run()` của nó vẫn **trả về bình thường**. Không có mâu thuẫn: Future là nơi lưu lỗi, còn `Runnable.run()` là giao diện thực thi callback.

Điều đó không có nghĩa “lỗi đã được xử lý xong về nghiệp vụ”. Một caller không bao giờ gọi `get()` và không có cơ chế quan sát trạng thái exceptional có thể **bỏ lỡ lỗi** dù pool vẫn tiếp tục phục vụ task khác.

Nó cũng không có nghĩa tất cả exception đều bị FutureTask nuốt: exception từ **decorator itself**, quá trình tạo context, cleanup, hoặc những lớp bọc khác có thể vẫn thoát ra. Ranh giới nào bắt lỗi phải được xác định từ chính đối tượng/callback đang chạy.

## 5. Ví dụ Java 17 độc lập: cùng một decorator, hai kết quả khác nhau

Ví dụ dưới mô phỏng logic wrapper mà một `TaskDecorator` tạo ra, nhưng dùng **100% JDK 17**, không cần Spring. Chạy đồng bộ trên thread chính để kết quả xác định và dễ thấy cách `Runnable.run()` khác với `FutureTask.run()`. **Đây là minh họa semantics**, không phải mô phỏng đầy đủ vòng đời bean/executor của Spring.

Tạo tệp **`FutureTaskDecoratorDemo.java`**:

```java
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public final class FutureTaskDecoratorDemo {
    static Runnable decorate(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                System.out.println(
                        "caughtAtRun=" + ex.getClass().getSimpleName());
                throw ex; // Giữ nguyên semantics báo lỗi của Runnable.
            } finally {
                // Chỉ xem Future sau run(), chỉ khi Future báo đã hoàn tất.
                if (task instanceof Future<?> future && future.isDone()) {
                    try {
                        future.get();
                    } catch (ExecutionException ex) {
                        System.out.println(
                                "causeFromGet="
                                        + ex.getCause().getClass().getSimpleName());
                    } catch (CancellationException ex) {
                        System.out.println("cancelled=true");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
    }

    public static void main(String[] args) {
        Runnable direct = () -> {
            throw new IllegalStateException("direct");
        };
        try {
            decorate(direct).run();
        } catch (IllegalStateException ex) {
            System.out.println("directPropagated=true");
        }

        FutureTask<String> future = new FutureTask<>(
                () -> {
                    throw new IllegalArgumentException("stored");
                });

        decorate(future).run();

        System.out.println("futureDone=" + future.isDone());
        System.out.println("futureCancelled=" + future.isCancelled());
    }
}
```

Biên dịch/chạy với JDK 17 hoặc mới hơn:

```bash
javac --release 17 FutureTaskDecoratorDemo.java
java FutureTaskDecoratorDemo
```

**Standard output đã được kiểm chứng khi chạy chương trình:**

```text
caughtAtRun=IllegalStateException
directPropagated=true
causeFromGet=IllegalArgumentException
futureDone=true
futureCancelled=false
```

### Giải thích đầu ra

1. Với `direct`, `run()` ném `IllegalStateException`. Wrapper bắt được nên in `caughtAtRun=IllegalStateException`, rồi ném lại. Main bắt tiếp và in `directPropagated=true`.
2. Với `future`, `FutureTask.run()` gọi `Callable.call()`, nhận `IllegalArgumentException`, **ghi exception vào trạng thái Future** và hoàn tất. Nó **không ném** ngoại lệ đó ra wrapper, nên không có dòng `caughtAtRun=IllegalArgumentException`.
3. Trong `finally`, wrapper nhận ra `task instanceof Future<?>` và `future.isDone()`. Gọi `get()` báo `ExecutionException`; `getCause()` là `IllegalArgumentException` nên in `causeFromGet=IllegalArgumentException`.
4. Future đã hoàn tất nhưng **không bị hủy**: `futureDone=true`, `futureCancelled=false`. Lỗi nghiệp vụ và cancellation là hai trạng thái khác nhau.

Ví dụ sử dụng `get()` **sau** khi `FutureTask.run()` đã hoàn tất; không có chờ vô hạn. Trong executor thật, tuyệt đối không gọi `Future.get()` bừa bãi từ một wrapper khi future còn chạy hoặc phụ thuộc chính worker đang bị chặn: có thể làm cạn pool và deadlock.

**Tính đúng đắn đã xác minh:** chương trình ở trên được biên dịch với `javac --release 17` và thực thi trên JDK hỗ trợ Java 17; stdout khớp đúng năm dòng đã ghi. Điều này xác nhận semantics của mẫu JDK, **không thay thế integration test với phiên bản Spring ứng dụng đang dùng**.

## 6. Cấu hình Spring TaskDecorator: mục đích phù hợp là context và metric

Trong Spring Framework 6.2, một bean `ThreadPoolTaskExecutor` có thể được cấu hình:

```java
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class ObservedExecutorConfig {
    private static final Logger LOG =
            Logger.getLogger(ObservedExecutorConfig.class.getName());

    @Bean("observedExecutor")
    public ThreadPoolTaskExecutor observedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("observed-");

        executor.setTaskDecorator(task -> () -> {
            long start = System.nanoTime();
            try {
                task.run();
            } catch (RuntimeException | Error ex) {
                // Chỉ các lỗi THOÁT RA khỏi callback.run() mới đến đây.
                LOG.log(Level.SEVERE, "Runnable callback failed", ex);
                throw ex;
            } finally {
                long elapsedNanos = System.nanoTime() - start;
                LOG.fine("Callback duration (ns): " + elapsedNanos);
            }
        });
        return executor;
    }
}
```

Đây là **class cấu hình tích hợp Spring**, cần dependency `spring-context`/`spring-aop` liên quan và application context phù hợp; không biên dịch trực tiếp bằng JDK thuần. Không có output cố định vì nó cấu hình worker và thời gian chạy. Spring quản lý việc khởi tạo/shutdown executor bean theo lifecycle.

**Phân tích đúng:**

- `setTaskDecorator` bọc callback thực tế mà Spring giao cho pool. Nó hữu ích cho scope thời gian chạy callback, context logging và cleanup.
- `catch` chỉ nhận exception thực sự thoát khỏi `task.run()`. Nó **có thể không nhận** lỗi đã được một `FutureTask`/callback giữ ở trạng thái kết quả.
- Giữ `throw ex` để không âm thầm thay đổi semantics của exception đối với `execute(Runnable)` trực tiếp.
- `finally` vẫn chạy khi callback hoàn thành bình thường hoặc ném lỗi. Đây là nơi hợp lý để hoàn trả context/metric.
- `System.nanoTime()` thích hợp đo thời gian tương đối, không dùng như timestamp UTC. Nếu callback chỉ **lên lịch thêm việc khác rồi trả về**, khoảng thời gian đo ở đây chỉ là **thời gian chạy callback**, không tự bao trùm toàn bộ các future con.
- `LOG.fine` có thể không xuất hiện nếu logger không bật mức FINE; đó không phải dấu hiệu decorator không chạy.

**Đừng dùng `TaskDecorator` làm nơi nuốt mọi exception** bằng một `catch (Exception ex) { log; }` không ném lại. Với `execute(Runnable)`, cách đó có thể làm tác vụ lỗi trông như hoàn thành bình thường; với `FutureTask`, ngay cả cách đó vẫn bỏ sót lỗi đã được lưu trong Future.

## 7. Có thể nhìn Future.get() trong TaskDecorator, nhưng có giới hạn

Javadoc Spring gợi ý: khi callback được expose dưới dạng `FutureTask`, có thể cast sang `Future` và dùng `get()` để đánh giá lỗi. Cách này **không phải một lời hứa** rằng mọi callback đều triển khai `Future`.

Một biến thể chẩn đoán:

```java
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.springframework.core.task.TaskDecorator;

// Dùng trong cấu hình executor hoặc nơi tạo decorator.
TaskDecorator diagnosticDecorator = runnable -> () -> {
    try {
        runnable.run();
    } finally {
        if (runnable instanceof Future<?> future && future.isDone()) {
            try {
                future.get();
            } catch (ExecutionException ex) {
                // Ghi nhận lỗi được FutureTask lưu (ex.getCause()).
                recordFailure(ex.getCause());
            } catch (CancellationException ex) {
                // Cancellation khác với lỗi tính toán.
                recordCancellation();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
};
```

Đây là **snippet minh họa** với `recordFailure(...)` và `recordCancellation()` do ứng dụng cung cấp; cần import/config phù hợp và **không phải chương trình độc lập**.

### Những điều kiện quan trọng

- **Phải kiểm tra `instanceof Future<?>`.** Decorator có thể thấy một `Runnable` là wrapper nội bộ **không** triển khai `Future`, dù logic cao hơn đang tạo ra kết quả kiểu `CompletableFuture`.
- **Chỉ cân nhắc `get()` sau `run()` và khi `isDone()`**. Cố gọi `get()` trước `run()` hoặc trên một Future chưa xong ngay trong worker có thể chặn vô hạn hoặc tạo deadlock.
- **`get()` có thể ném ba nhóm lỗi khác nhau**: `ExecutionException` (lỗi computation), `CancellationException` (task bị hủy) và `InterruptedException` (worker đang đợi bị interrupt). Cần phân loại riêng; khi bắt `InterruptedException` thường phải khôi phục cờ interrupt.
- Nếu Future là một implementation khác, `get()` không nhất thiết phản ánh toàn bộ công việc phụ thuộc hoặc callback phía sau. Không biến decorator thành một generic future orchestrator.
- Với `@Async` trả `CompletableFuture`, wrapper mà executor thực sự chạy có thể là **callback nội bộ**, không phải `FutureTask` trực tiếp; phép thử `instanceof Future` có thể luôn `false`. Phải kiểm chứng trên Spring version/executor implementation thực tế.
- `recordFailure` trong decorator có thể trùng với handler ở caller. Chọn một nơi sở hữu log/metric và định danh task để tránh đếm hai lần.
- Các hook như `ThreadPoolExecutor.afterExecute(Runnable, Throwable)` có cùng vấn đề: `Throwable` có thể là `null` khi `FutureTask` đã lưu exception. JDK đưa ví dụ kiểm tra `r instanceof Future<?>` rồi `get()`, nhưng nếu decorator bọc FutureTask trong một Runnable khác, `afterExecute` có thể chỉ thấy wrapper đó. Cần test cấu trúc callback thực tế.

**Khuyến nghị:** dùng kiểm tra Future trong decorator cho nhu cầu diagnostic được kiểm chứng, không dùng làm **nền tảng duy nhất** của xử lý lỗi nghiệp vụ. Phần sau trình bày các điểm bắt lỗi vững hơn.

## 8. Xử lý lỗi ở Future: vị trí sở hữu kết quả đáng tin cậy hơn

### 8.1. Future.get(): rõ nguyên nhân nhưng có thể chặn caller

Khi chủ động gọi `submit` và giữ kết quả:

```java
Future<String> result = executor.submit(() -> {
    throw new IllegalArgumentException("bad input");
});

try {
    String value = result.get();
    useValue(value);
} catch (ExecutionException ex) {
    Throwable original = ex.getCause();
    handleFailure(original);
} catch (CancellationException ex) {
    handleCancellation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    handleInterruptedWait(ex);
}
```

Đây là snippet có `executor` và các hàm nghiệp vụ được cung cấp bởi chương trình tích hợp. `Future.get()` có thể **chặn caller**; nếu caller là HTTP event-loop hoặc đang giữ lock/connection quý hiếm, cân nhắc API bất đồng bộ hoặc timeout.

Với deadline, dùng `get(timeout, unit)` và xử lý thêm `TimeoutException`. Timeout khi đợi **không tự xác nhận** task đã dừng; cần quyết định cancel/cleanup phù hợp với executor và dependency.

### 8.2. CompletableFuture.whenComplete(): quan sát mà không gọi blocking get

Với `CompletableFuture`, callback là lựa chọn phù hợp để xử lý kết quả hoàn thành bất đồng bộ:

```java
CompletableFuture<String> job =
        CompletableFuture.supplyAsync(() -> calculateReport(), executor);

job.whenComplete((value, error) -> {
    if (error != null) {
        // error có thể là CompletionException bọc nguyên nhân.
        logFailure(error);
    } else {
        logSuccess(value);
    }
});
```

Đây là **snippet tích hợp**. `whenComplete` nhận được exceptional completion của future, kể cả khi `Runnable.run()` phía executor trả về bình thường. Cần lưu ý callback không nhất thiết chạy trên một worker mới: tùy trạng thái completion và loại overload, hàm `whenComplete` có thể chạy trên thread hoàn tất stage hoặc thread đăng ký callback; không đặt trong đó một xử lý blocking dài.

Nếu muốn luôn dispatch callback sang một executor kiểm soát được, xem `whenCompleteAsync(action, executor)` nhưng phải tính tới saturation/rejection của executor đó.

### 8.3. exceptionally(), handle(), whenComplete(): không giống nhau

| API | Chức năng | Cẩn thận |
| --- | --- | --- |
| `whenComplete((v,e) -> ...)` | Quan sát cả thành công và lỗi | Thường giữ trạng thái kết quả/lỗi của stage nguồn; callback cũng có thể ném lỗi |
| `exceptionally(e -> fallback)` | Phục hồi bằng giá trị khác sau lỗi | Có thể biến lỗi thành thành công; đừng dùng chỉ để log rồi vô tình nuốt lỗi |
| `handle((v,e) -> newValue)` | Biến đổi kết quả của cả hai nhánh | Trạng thái mới do hàm handler quyết định |
| `join()` | Lấy kết quả, ném `CompletionException` khi lỗi | Có thể chặn; dùng khi phù hợp |
| `get()` | Lấy kết quả, ném `ExecutionException` khi lỗi | Checked exceptions, có thể chặn |

Nếu hệ thống phải báo lỗi cho caller, không dùng `exceptionally(ex -> null)` chỉ để “ghi log rồi xong” khi `null` bị hiểu là kết quả hợp lệ. Hãy định nghĩa contract phục hồi/fail-fast rõ ràng.

### 8.4. Đặt catch ngay trong task nghiệp vụ khi cần context

Một task biết `orderId`, transaction boundary và remote endpoint thường có đủ thông tin để phân loại lỗi:

```java
public Invoice calculateInvoice(String orderId) {
    try {
        return invoiceClient.calculate(orderId);
    } catch (RemoteTimeoutException ex) {
        failureMetric.increment();
        throw ex;
    }
}
```

Đây là **snippet minh họa**, phụ thuộc các kiểu nghiệp vụ và client của ứng dụng. Ưu điểm: log/counter có ngữ cảnh chính xác. Nhược điểm: lặp code nếu mọi task đều thêm đoạn giống nhau; khi đó tạo component wrapper nghiệp vụ hay aspect có contract rõ thay vì ép generic TaskDecorator biết toàn bộ lỗi.

## 9. Spring @Async: Future, CompletableFuture và void không cùng đường lỗi

Đối với Spring `@Async`, return type ảnh hưởng cách exception tới người gọi:

| Dạng phương thức | Khi thân phương thức ném lỗi | Điểm xử lý nên dùng |
| --- | --- | --- |
| `@Async CompletableFuture<T>` | Lỗi có thể được biểu diễn thành exceptional completion của future trả cho caller | `whenComplete`/`handle` hoặc `join/get` |
| `@Async Future<T>` | Caller có thể nhận exception qua `Future.get()` | `get()` và `ExecutionException.getCause()` |
| `@Async void` | Caller không nhận Future để lấy exception | `AsyncUncaughtExceptionHandler`, logging/metrics tại boundary |
| Tác vụ bị reject khi submit | Execution chưa được chấp nhận | Xử lý `TaskRejectedException` ở nơi nộp |

**Ví dụ Spring service:**

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("observedExecutor")
    public CompletableFuture<String> build(String id) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("id is blank");
        }
        return CompletableFuture.completedFuture("report:" + id);
    }

    @Async("observedExecutor")
    public void publish(String id) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("id is blank");
        }
    }
}
```

Đây là **class tích hợp Spring**, dùng bean executor ở mục 6 và cơ chế `@EnableAsync`; không phải chương trình JDK standalone. Spring quản lý dispatch qua proxy nếu method được gọi từ một Spring bean khác; tự gọi `this.build(...)` trong cùng target thường bypass `@Async` ở chế độ proxy.

**Caller xử lý future:**

```java
CompletableFuture<String> result = reportService.build("");
result.whenComplete((value, error) -> {
    if (error != null) {
        // Đây là lỗi của async computation, không nhất thiết
        // là RuntimeException thoát ra TaskDecorator.run().
        recordBuildError(error);
    }
});
```

Snippet cần `reportService` và `recordBuildError` được định nghĩa. Khi dùng `@Async void`, Spring cung cấp `AsyncUncaughtExceptionHandler` để xử lý exception không có Future trả về.

### Cấu hình AsyncUncaughtExceptionHandler cho void

```java
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

@Configuration
public class AsyncErrorsConfig implements AsyncConfigurer {
    private static final Logger LOG =
            Logger.getLogger(AsyncErrorsConfig.class.getName());

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (error, method, args) ->
                LOG.log(Level.SEVERE,
                        "Async void failed: " + method.getName(), error);
    }
}
```

Đây là class cấu hình Spring, có thể dùng trong ứng dụng đã bật `@EnableAsync` và có đúng bean executor; ***không*** phải một phương tiện nhận lỗi từ `Future<T>`. Nếu nhiều `AsyncConfigurer` hoặc cấu hình async cùng tồn tại, phải kiểm chứng executor/error handler thực sự được chọn.

**Không kỳ vọng `TaskDecorator` thay thế `AsyncUncaughtExceptionHandler`.** Với `@Async void`, exception có thể bị async interceptor bắt và chuyển tới handler trước khi callback chạy về đến decorator. Cơ chế nào được gọi phải được integration-test trên bản Spring thực tế.

## 10. Phân biệt bốn trạng thái: lỗi thực thi, hủy, timeout, từ chối

### Lỗi thực thi

Task được nhận vào executor, bắt đầu chạy và ném lỗi. Lỗi có thể được đưa vào `Future`/`CompletableFuture` hoặc thoát ra worker. Xử lý tại kết quả hoặc boundary thực thi có đủ context.

### Cancellation

Một `Future` bị hủy: `isCancelled()` có thể đúng; `get()` ném `CancellationException`. Đây **không phải** `ExecutionException` của lỗi nghiệp vụ. Hủy `CompletableFuture` có thể đổi trạng thái future mà không dừng supplier phía sau, tùy implementation.

### Timeout

`Future.get(timeout, unit)` có thể ném `TimeoutException` do caller chờ quá lâu, trong khi task vẫn tiếp tục chạy. Timeout của JDBC/HTTP client là cơ chế khác; đo riêng waiting duration, execution duration và resource release.

### Rejection

Khi executor đã shutdown hoặc hết khả năng nhận task (ví dụ full pool + full queue theo policy), lời gọi submit/execute có thể bị từ chối **trước khi task được chạy**. Trong Spring, lỗi này thường là `org.springframework.core.task.TaskRejectedException`. Decorator của tác vụ chưa chạy **không có cơ hội** bắt exception này. Hãy xử lý tại caller/admission layer, không lẫn với lỗi completion.

```text
                  submission
                      |
              +-------+-------+
              |               |
           rejected         accepted
              |               |
    TaskRejectedException    v
       (caller thread)     execution
                              |
                      +-------+-------+
                      |               |
                    plain          FutureTask/
                  Runnable         async wrapper
                      |               |
                may throw out     stores task error
                  of run()         in Future state
                      |               |
              decorator catch      Future.get() /
               may see error      whenComplete()
```

## 11. Vì sao không nên cố chữa bằng afterExecute hoặc Thread.UncaughtExceptionHandler?

JDK `ThreadPoolExecutor.afterExecute(Runnable r, Throwable t)` chạy sau khi worker thực thi task. `t` có thể là `null` ngay cả khi một `FutureTask` đã thất bại, vì `run()` của FutureTask không ném exception ra worker.

JDK Javadoc có ví dụ: nếu `t == null` và `r instanceof Future<?>`, hãy gọi `get()` để lấy `ExecutionException`/`CancellationException`; cần lưu ý cả `InterruptedException`.

**Nhưng trong Spring có TaskDecorator:**

1. `FutureTask` được wrap bởi decorator thành một `Runnable` mới.
2. `afterExecute` của underlying pool có thể nhận **wrapper** đó chứ không phải FutureTask gốc.
3. Nếu wrapper không triển khai Future, `r instanceof Future<?>` là `false` dù lỗi nằm ở FutureTask bên trong.
4. Vì vậy sao chép logic JDK `afterExecute` không tự bảo đảm quan sát được tất cả lỗi khi có thêm decorator, async interceptor, adapter hoặc callback nội bộ.

Tương tự, `Thread.UncaughtExceptionHandler` chỉ xử lý exception thật sự không được bắt trên thread. Một lỗi đã được `FutureTask` ghi vào state không trở thành uncaught exception.

**Chọn vị trí xử lý theo quyền sở hữu kết quả:** subscriber của `CompletableFuture`, caller có `Future`, handler của `@Async void`, hoặc boundary nghiệp vụ. Dùng TaskDecorator/afterExecute bổ sung metric/context thay vì kỳ vọng chúng nhìn xuyên mọi wrapper.

## 12. Context propagation và cleanup vẫn là use case mạnh của TaskDecorator

Với ứng dụng Spring có log correlation ID, MDC hoặc observation context, decorator thích hợp vì **nó chạy ở biên thực thi callback**, độc lập với thành công hay lỗi nghiệp vụ.

Cách làm đúng với `ThreadLocal`/MDC:

1. **Chụp context khi submit** ở thread nguồn, nếu implementation thực sự gọi decorate lúc submit. Đừng chỉ đọc `ThreadLocal` của worker rồi tưởng đó là context của request.
2. **Thiết lập context ở worker** trước `task.run()`.
3. **Trong `finally`, khôi phục context cũ hoặc xóa** để worker được tái sử dụng không làm rò trace/request ID từ task này sang task tiếp theo.
4. **Không quên cancellation và exception path**; cleanup vẫn phải chạy.
5. Tránh đính kèm cả security principal hay dữ liệu nhạy cảm vào log/metric tag không kiểm soát.
6. Đo overhead khi mỗi task rất ngắn: snapshot/restore context cũng có chi phí.

Spring Framework 6.1+ có `ContextPropagatingTaskDecorator` hỗ trợ truyền một số context như logging/observation khi có tích hợp Micrometer Context Propagation thích hợp. Tính năng này **không biến TaskDecorator thành exception handler toàn năng**. Nếu dùng `CompositeTaskDecorator` để phối hợp nhiều decorator, cần kiểm tra thứ tự wrap/cleanup và tác động đến đối tượng `Runnable` mà mỗi tầng nhìn thấy.

Đừng giả định worker-thread-local là nơi an toàn để lưu exception “cho ai đó lấy sau”: thread có thể được pool tái sử dụng; lỗi nên được liên kết với **task ID/future/trace context** thích hợp và được dọn đúng vòng đời.

## 13. Kiểm thử thực tế cho ThreadPoolTaskExecutor

Một integration test tốt nên tạo các trường hợp khác nhau, thay vì chỉ một lời gọi ném lỗi:

| Ca kiểm thử | Thiết lập | Kỳ vọng cần kiểm chứng |
| --- | --- | --- |
| A: `execute(Runnable)` trực tiếp | Runnable ném `IllegalStateException`, không wrapper giữ lỗi | Decorator catch có thể thấy exception thoát khỏi `run()` |
| B: `submit(Callable)` | Callable ném `IllegalArgumentException` | `Future.get()` ném `ExecutionException`; decorator catch không nên được coi là đủ |
| C: `submit(Runnable)` | Runnable ném lỗi | Future giữ lỗi tương tự ca B |
| D: `@Async CompletableFuture<T>` | Method body ném lỗi | Future của caller kết thúc exceptional, callback nhận lỗi |
| E: `@Async void` | Method body ném lỗi | `AsyncUncaughtExceptionHandler` được kích hoạt |
| F: cancellation | Hủy Future trước/đang thực thi | Xử lý `CancellationException` riêng; không coi là business failure |
| G: rejection | Saturate pool/queue hoặc shutdown | Caller nhận `TaskRejectedException`; task không chạy |
| H: cleanup | Task thành công/lỗi/cancel | Context/ThreadLocal được trả về trạng thái trước khi chạy |

**Cách tạo test ổn định:**

- Dùng `CountDownLatch`/barrier để xác nhận task bắt đầu, giữ worker tại điểm chặn rồi chủ động mở; đừng dùng duy nhất `Thread.sleep(100)` để chứng minh thứ tự race.
- Chạy trên đúng bean `ThreadPoolTaskExecutor` có decorator được cấu hình, không vô tình dùng common pool hoặc executor khác.
- Với `@Async`, lời gọi phải đi **qua Spring proxy** (bean khác gọi vào); `this.asyncMethod()` trong cùng target thường bypass interception.
- Với future, đặt thời hạn khi `get(timeout, unit)` hoặc `await` để test không treo vô hạn.
- Ghi nhận cả lỗi xuất hiện tại **submitter** và lỗi trong **worker/future**. Đây là hai điểm khác nhau.
- Khi đo metric, tránh double count nếu vừa có `Future.whenComplete`, vừa có logic `Future.get` ở decorator, vừa có logging trong method nghiệp vụ.
- Kiểm tra dọn dẹp worker, queue và context ngay cả khi assertion thất bại; đóng executor hoặc để Spring lifecycle đóng theo thiết kế.

### Assertion trọng tâm

Với ca B trên executor thực tế, cần chứng minh **hai điều đồng thời**:

```text
(1) Future.get() -> ExecutionException(cause=IllegalArgumentException)
(2) catch(RuntimeException) quanh callback.run()
    KHÔNG bắt được IllegalArgumentException đã bị FutureTask giữ.

Kết luận:
  TaskDecorator có thể đã CHẠY bình thường,
  nhưng nó vẫn KHÔNG thấy lỗi dưới dạng exception thoát từ run().
```

Đây là phép phân biệt quan trọng hơn việc chỉ kiểm tra “có log lỗi hay không”; log có thể đến từ nhiều nguồn khác nhau.

## 14. Checklist xử lý lỗi cho production

Khi thiết kế Spring service có executor và Future:

1. **Phân loại kiểu submission**: `execute`, `submit`, `CompletableFuture`, `@Async void` hay scheduler/adapter khác?
2. **Xác định Runnable thực tế đi vào decorator**: là task người dùng hay wrapper/FutureTask? Đừng ép kiểu dựa trên giả định.
3. **Chọn nơi sở hữu lỗi nghiệp vụ**: Future của caller, callback `CompletableFuture`, method boundary hoặc `AsyncUncaughtExceptionHandler`.
4. **Phân loại exception**: task failure, cancellation, timeout khi chờ, rejection ở submitter.
5. **Không block worker bằng get() tùy tiện**: chỉ chẩn đoán Future sau khi task hoàn tất, có `isDone()` và hiểu implementation; ưu tiên callback bất đồng bộ cho luồng kết quả.
6. **Giữ semantics**: decorator bắt RuntimeException/Error phải ném lại trừ khi cố ý chuyển sang một chính sách recovery được định nghĩa.
7. **Tách logging và recovery**: log không đồng nghĩa retry, không đồng nghĩa task được đánh dấu thành công.
8. **Xử lý rejection** ở cổng nộp việc, có backpressure/response/retry hoặc job store; không đặt toàn bộ trách nhiệm lên decorator.
9. **Giữ context sạch**: snapshot đúng lúc, restore ở `finally`, tránh rò ThreadLocal giữa worker pool.
10. **Không nhân đôi báo lỗi**: xác định owner của error metric/log với task ID và causal exception.
11. **Kiểm thử code thật** trên JDK/Spring/Boot version đang dùng: behavior của `submitCompletable`, `@Async`, callbacks và wrappers có thể không giống nhau.
12. **Bảo vệ tài nguyên**: bounded executor, queue, timeout JDBC/HTTP, metric về active/queued/rejected/failure và propagation ngữ cảnh.

### Bảng chọn vị trí quan sát lỗi

| Nhu cầu | Công cụ nên ưu tiên |
| --- | --- |
| Đo thời gian callback, MDC, trace context | `TaskDecorator` với `finally` |
| Bắt lỗi từ Runnable thuần được execute trực tiếp | `catch` quanh `run()` hoặc executor error hook |
| Biết vì sao một `FutureTask` thất bại | `Future.get()` → `ExecutionException.getCause()` |
| Quan sát lỗi của `CompletableFuture` không cần chặn | `whenComplete`/`handle` |
| Xử lý lỗi `@Async void` | `AsyncUncaughtExceptionHandler` |
| Xử lý lỗi nộp task khi hết chỗ | Catch `TaskRejectedException` tại submitter |
| Phân tích lỗi theo mã nghiệp vụ và retry | Handler/boundary ở tầng service/client |
| Test nguyên nhân lỗi và cleanup | Integration test có latch, timeout, assertions |

## 15. Tổng kết và tài liệu chính thức

**Ba câu cần nhớ:**

1. **`TaskDecorator` bọc `Runnable` thực tế**; nó không nhất thiết nhìn thấy task nguyên gốc.
2. **`FutureTask.run()` lưu exception thay vì ném ra ngoài**, nên `catch` quanh `run()` không đủ để quan sát lỗi của `submit`/future-backed tasks.
3. **Xử lý lỗi theo loại kết quả và đúng ranh giới**: `Future.get()`, `CompletableFuture.whenComplete(...)`, `AsyncUncaughtExceptionHandler` hoặc catch `TaskRejectedException` ở nơi submit; giữ TaskDecorator cho context/metrics và chỉ bổ sung future inspection khi biết rõ wrapper thật.

**Tài liệu đối chiếu:**

- [Spring Framework 6.2 – ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html) — `setTaskDecorator` và cảnh báo khi callback là `FutureTask`.
- [Spring Framework 6.2 – TaskDecorator Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/core/task/TaskDecorator.html) — hợp đồng decorate callback và giới hạn bắt lỗi.
- [Java SE 17 – FutureTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html) — `run`, `get` và `setException`.
- [Java SE 17 – ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html) — `afterExecute` và cách xem lỗi của Future.
- [Java SE 17 – CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html) — `whenComplete`, `handle`, `join`.
- [Spring Framework 6.2 – Async exception management](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html#scheduling-annotation-support-exception) — xử lý lỗi `@Async Future` và `@Async void`.
- [Spring Framework 6.2 – ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html) — truyền context logging/observation.

**Kết luận:** Khi `TaskDecorator.catch` không log được một lỗi từ tác vụ `submit`, nguyên nhân có thể hoàn toàn hợp lệ: lỗi đã nằm trong `FutureTask`, không thoát khỏi `Runnable.run()`. Đừng “chữa” bằng cách bọc thêm nhiều `try/catch` vào decorator; hãy đọc/đăng ký xử lý ở **kết quả Future**, giữ decorator làm đúng nhiệm vụ context/metrics, và kiểm chứng bằng thử nghiệm trên executor thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="exception-handling-009">Xác định chính sách thử lại có giới hạn và tính an toàn khi chạy lại</a>

<details>
<summary>Xem chi tiết</summary>

# Xác định chính sách thử lại có giới hạn và tính an toàn khi chạy lại

> **Module:** Spring Framework – concurrency  
> **Nhóm:** Xử lý ngoại lệ  
> **Mục tiêu:** thiết kế **bounded retry** (thử lại với số lần và thời gian hữu hạn) đồng thời chứng minh một tác vụ **an toàn để chạy lại** theo đúng hợp đồng nghiệp vụ. Retry giúp vượt qua lỗi tạm thời nhưng cũng có thể gây giao dịch trùng, quá tải dây chuyền hoặc che lỗi vĩnh viễn nếu làm sai.

## 1. Kết quả học tập

Sau bài học, bạn có thể:

1. Quyết định **có nên retry hay không** dựa trên bản chất lỗi, tính an toàn của thao tác và trạng thái không chắc chắn của lần gọi trước.
2. Viết một chính sách rõ ràng về `maxAttempts`, timeout mỗi lần, deadline tổng, exponential backoff, jitter, điều kiện dừng và exhausted handling.
3. Phân biệt **retryable failure** với **idempotent operation**: lỗi có thể hết sau khi thử lại không đồng nghĩa thao tác chạy lại là an toàn.
4. Hiểu idempotency key, deduplication, transaction boundary, unique constraint, outbox và cách xử lý “server đã thực hiện nhưng client chưa nhận response”.
5. Viết chương trình Java 17 dùng retry hữu hạn, test không cần đợi đồng hồ thật và xác nhận đúng số lần gọi.
6. Áp dụng vào `ThreadPoolTaskExecutor`, `CompletableFuture` và Spring Retry/Resilience4j mà không block worker vô hạn, không retry theo tầng nhân cấp số lần gọi.
7. Thiết kế metrics, log, kiểm thử tải, trường hợp hủy và graceful shutdown để kiểm soát retry trong production.

## 2. Vấn đề gốc: thất bại không có nghĩa thao tác chưa được thực hiện

Hình dung một ứng dụng gửi yêu cầu thanh toán:

~~~text
Client                       Payment service
   |                                |
   +----- POST /charge ------------>|
   |                                +--- ghi giao dịch thành công
   |                                |
   |<-------- response bị mất --- X  |
   |                                |
   +--- timeout ở client             |
   |                                |
   +--- retry không có định danh --->|--- có thể ghi thêm lần nữa
~~~

Client chỉ nhìn thấy `TimeoutException` hoặc lỗi socket, nên **không biết chắc** server chưa xử lý. Nếu retry như một giao dịch mới, khách hàng có thể bị trừ tiền hai lần. Đây là **kết quả không rõ (ambiguous outcome)**, không phải bằng chứng chưa thực thi.

Retry đúng cần trả lời **hai câu hỏi độc lập**:

- **Lỗi có khả năng tạm thời không?** Ví dụ một TCP connect timeout do hệ thống phụ trợ ngắn hạn quá tải.
- **Nếu lần trước thực ra đã thành công, thực hiện lại cùng yêu cầu có gây hậu quả mới không?** Nếu có, cần idempotency/dedup/reconciliation hoặc không tự động thử lại.

Một API có thể **retryable về mặt kỹ thuật** nhưng **không an toàn khi chạy lại về mặt nghiệp vụ**. Không nên cho mọi request dùng chung một chính sách retry chỉ vì chúng đều ném `RuntimeException`.

## 3. Định nghĩa retry, attempt, idempotency và duplicate

| Khái niệm | Ý nghĩa |
| --- | --- |
| **Attempt** | Một lần thực sự gọi operation/remote endpoint |
| **Retry** | Một attempt bổ sung **sau** một attempt trước thất bại |
| **Max attempts** | Tổng số attempt, **bao gồm** lần đầu, nếu API quy định như vậy |
| **Backoff** | Thời gian chờ trước attempt tiếp theo |
| **Jitter** | Biến thiên ngẫu nhiên của khoảng backoff để phân tán retry |
| **Retry budget** | Giới hạn tài nguyên được phép dùng cho retry (số lượng, thời gian, tỷ lệ) |
| **Deadline** | Thời điểm cuối toàn operation phải kết thúc |
| **Idempotent** | Lặp lại cùng yêu cầu có cùng **hiệu ứng dự định** lên hệ thống như một lần thực hiện |
| **Deduplication** | Phát hiện nhiều lần giao cùng identity và không áp dụng side effect hai lần |
| **At-least-once delivery** | Có thể gửi lại để tránh mất việc, nên phía nhận phải xử lý duplicate |
| **Unknown outcome** | Caller mất response, không biết việc trước đó đã commit hay chưa |

**Lưu ý cách đếm:** nếu chính sách của ứng dụng là **`maxAttempts = 3`**, nó thường có nghĩa **lần đầu + tối đa 2 lần thử lại**. Nhưng API khác có thể đặt tên `maxRetries = 3`, nghĩa là **1 lần đầu + 3 lần thử lại = tối đa 4 attempts**. Hãy đọc hợp đồng thư viện và đo số invocation thực tế thay vì suy từ tên thuộc tính.

### Idempotency không có nghĩa response byte-by-byte giống nhau

Theo RFC 9110, idempotency nói về **hiệu ứng dự định** trên server, không phải bắt buộc mọi mã HTTP, timestamp, log hoặc response đều giống hệt nhau. Một `PUT` lặp lại cùng representation có thể để lại cùng trạng thái tài nguyên, dù mỗi lần ghi thêm bản ghi audit. Bài toán thanh toán có yêu cầu mạnh hơn: **không ghi nhận cùng một charge hai lần** khi retry cùng operation identity.

## 4. Phân loại lỗi trước khi quyết định retry

Không có bộ quy tắc “mọi 5xx retry, mọi 4xx không retry” đúng cho tất cả API. Một ma trận khởi đầu:

| Nhóm lỗi | Có nên retry? | Điều kiện/giải thích |
| --- | --- | --- |
| Lỗi validation/thiếu trường bắt buộc, `400`, `422` | Thường **không** | Gửi lại y hệt không sửa input không giúp thành công |
| Sai quyền `401`/`403` | Không retry y hệt | Cần refresh credential hoặc thay quyền theo logic riêng, tránh loop |
| `404` với resource vĩnh viễn không tồn tại | Thường không | Nhưng hệ thống eventual consistency có thể cho phép retry ngắn nếu contract nói vậy |
| Xung đột `409`/optimistic locking | Phân tích trước | Có thể cần đọc trạng thái mới, merge hoặc thông báo conflict; không lặp thao tác mù |
| `408 Request Timeout` | Có thể | Chỉ khi operation an toàn để chạy lại hoặc đã có identity/dedup |
| `429 Too Many Requests` | Có thể sau chờ | Tôn trọng `Retry-After`/quota, hạn chế tổng số lần, tránh cùng lúc retry |
| `502 Bad Gateway` / `503 Service Unavailable` / `504 Gateway Timeout` | Thường xem là ứng viên | Kiểm tra loại downstream và tính idempotent, không retry liên tục khi quá tải |
| `500 Internal Server Error` | Có thể hoặc không | Có hệ thống báo lỗi 500 sau khi đã commit; không giả định vô side effect |
| TCP connect failure trước khi request được gửi | Thường có thể | Cần xác nhận client/transport thực sự chưa gửi yêu cầu |
| Read timeout / connection reset **sau khi gửi** | **Kết quả không chắc chắn** | Phải đảm bảo idempotency hoặc thực hiện status lookup/reconciliation |
| Lỗi dữ liệu/parse deterministic | Không | Cùng input sẽ lỗi lại, thử nhiều lần chỉ tiêu tốn tài nguyên |
| `CancellationException` hoặc chủ động hủy | Thường không | Người dùng hoặc hệ thống đã quyết định không tiếp tục |
| `InterruptedException` | Không auto-retry mù | Khôi phục interrupt flag hoặc truyền lên chính sách hủy |
| JVM `Error` / `OutOfMemoryError` | Không dùng retry thông thường | Cần xử lý lỗi hệ thống, tránh khuếch đại sự cố |

Phân loại nên được **cụ thể hóa bằng exception type và contract**. Ví dụ, thư viện HTTP có thể gộp timeout và connection errors vào một lớp exception chung, nhưng phải kiểm tra **giai đoạn gửi** để biết hiệu ứng có thể đã xảy ra hay chưa.

Đừng dùng `catch (Exception e) { retry(); }` cho mọi exception. Đặc biệt, lỗi programmer bug như `NullPointerException` hoặc dữ liệu bất hợp lệ sẽ bị lặp lại hàng loạt, làm log nhiễu và tăng tải.

### Retryable không đồng nghĩa recoverable

- **Retryable:** thử lại có cơ hội đổi kết quả.
- **Recoverable:** có đường xử lý khác khi không thể hoàn thành (trả fallback, queue bền vững, yêu cầu người dùng can thiệp).
- **Idempotent:** lặp lại không làm tăng số side effect mong muốn.
- **Compensatable:** có thể thực hiện một hành động bù sau khi đã phát sinh hiệu ứng. Bù trừ không phải một bảo đảm exactly-once.

Một tác vụ có thể retryable nhưng không idempotent, chẳng hạn gọi API “tạo hóa đơn mới” với mỗi POST không có operation key.

## 5. Chính sách retry cần có những thuộc tính nào?

Hãy ghi chính sách thành một bản mô tả có thể kiểm tra:

~~~text
Operation: partner.readProduct
Identity: tenantId + productId
Max attempts: 3 (bao gồm lần đầu)
Retry only: connect timeout; HTTP 429/502/503/504 theo contract
Never retry: validation, authorization, cancellation, interrupt
Per-attempt timeout: 700 ms
Overall deadline: 2.5 s từ lúc bắt đầu
Backoff: exponential base=100 ms, cap=500 ms, full jitter
Retry-After: tôn trọng nếu còn nằm trong deadline
On exhaustion: trả lỗi có cause gốc + metrics + trạng thái rõ ràng
Idempotency: read-only; không có side effect nghiệp vụ
Concurrency budget: bounded executor/queue, không retry vô hạn
~~~

Đây là **ví dụ thiết kế**, không phải thông số mặc định cho mọi hệ thống. Cùng chiến lược không được tự động áp vào `partner.createPayment` mà chưa có cơ chế tránh charge trùng.

### 5.1. Tổng số lần thử

Gọi `A` là max attempts:

~~~text
A = 1 -> 1 lần gọi đầu, 0 retry
A = 2 -> 1 lần gọi đầu, tối đa 1 retry
A = 3 -> 1 lần gọi đầu, tối đa 2 retries
~~~

Mỗi attempt có thể kết thúc bằng success, lỗi không retry được, timeout hoặc cancellation. Khi đạt ngưỡng, **dừng**, không lặp vòng vô hạn hoặc gửi việc lại queue mà không có chính sách tổng.

Nếu hệ thống có HTTP client tự retry và tầng service lại retry, số request thật có thể cao hơn kỳ vọng:

~~~text
service retry 3 attempts
  × SDK retry 3 attempts / service attempt
  = tới 9 request downstream cho 1 logical operation
~~~

Nếu thêm một API gateway cũng retry 2 lần, mức tối đa lý thuyết có thể thành 18 lần. Phải biết chính sách ở **tất cả các tầng**, tránh “retry storm”.

### 5.2. Timeout mỗi attempt và deadline tổng

Đặt `maxAttempts` chưa đủ. Giả sử mỗi attempt có read timeout 10 giây và có 3 attempts: dù backoff rất ngắn, yêu cầu có thể kéo dài hơn 30 giây, vượt xa SLA HTTP 2 giây.

Phân biệt:

- **Per-attempt timeout:** giới hạn một lần gọi mạng/I/O.
- **Overall deadline:** giới hạn toàn logical operation gồm attempts và thời gian chờ giữa chúng.
- **Queue wait budget:** nếu task phải đợi `ThreadPoolTaskExecutor` mới bắt đầu, nó cũng phải được trừ vào deadline.
- **Client disconnect/cancellation:** nếu request đã bị hủy, không tiếp tục retry trừ khi công việc đã chuyển sang workflow nền có chủ đích.

Trước khi bắt đầu attempt mới, kiểm tra thời gian còn lại có đủ cho **min attempt timeout + backoff** hoặc ít nhất một khoảng hữu ích không. Không làm tròn deadline từ lúc lên lịch mà quên thời gian chờ queue.

### 5.3. Backoff tăng dần

Một công thức exponential backoff không jitter:

\[
d_k = \min(d_{\max}, d_0 \cdot 2^{k-1})
\]

Trong đó `k` là số lần thất bại đã xảy ra trước retry tiếp theo (`k=1` là delay đầu tiên), `d_0` là delay cơ bản, `d_max` là mức trần.

Ví dụ `d_0 = 100 ms`, `d_max = 500 ms`:

| Sau attempt thất bại số | Delay trước retry tiếp |
| --- | ---: |
| 1 | 100 ms |
| 2 | 200 ms |
| 3 | 400 ms |
| 4 | 500 ms (đạt cap) |
| 5 | 500 ms |

Không nhân đôi `long` vô hạn rồi mới gọi `Math.min`: biểu thức có thể overflow. Có thể dùng so sánh trước khi nhân đôi hoặc thư viện backoff đã xử lý overflow.

### 5.4. Jitter chống đồng loạt gọi lại

Nếu 10.000 client cùng nhận 503 và đều chờ đúng 1 giây, chúng sẽ đồng loạt gọi lại, gây một đợt tải thứ hai.

Một cách là **full jitter**: với backoff tính được `d_k`, chọn ngẫu nhiên delay trong khoảng `[0, d_k]` (định nghĩa chính xác inclusive/exclusive theo implementation). Có cả equal jitter và decorrelated jitter với phân phối khác nhau; cần chọn theo nhu cầu.

Jitter không có nghĩa bỏ cap/deadline. Chọn delay xong vẫn kiểm tra còn đủ ngân sách để thực thi retry. Nếu server trả `Retry-After`, hãy tôn trọng ý nghĩa thời điểm có thể thử lại và giới hạn budget; không dùng jitter để thử **sớm hơn** yêu cầu của server khi contract yêu cầu chờ tối thiểu.

### 5.5. Điều kiện dừng hợp lệ

Dừng retry khi:

1. Operation thành công.
2. Gặp lỗi không retryable.
3. Đạt max attempts.
4. Deadline tổng hết hoặc không đủ ngân sách cho attempt kế tiếp.
5. Được cancel/interrupt, Spring context đang đóng hoặc client không còn cần kết quả.
6. Circuit breaker đã mở, rate/concurrency budget không cho phép retry hoặc cơ chế overload shedding yêu cầu dừng.

Nếu hết attempts, **trả/phát trạng thái thất bại có cause** theo hợp đồng. Không được log rồi trả một kết quả thành công giả, hoặc gọi lại trong background không giới hạn mà không có job ownership.

## 6. Ví dụ Java 17 chạy độc lập: bounded retry với kết quả xác định

Ví dụ sau thực sự hiện thực một **vòng lặp có tối đa 3 attempts**, chỉ retry loại lỗi do chính ứng dụng gắn nhãn là tạm thời. Nó sử dụng `Sleeper` có thể inject để không phụ thuộc `Thread.sleep` trong test.

Tạo tệp **`BoundedRetryDemo.java`**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class BoundedRetryDemo {

    @FunctionalInterface
    interface CheckedOperation<T> {
        T run() throws Exception;
    }

    @FunctionalInterface
    interface Sleeper {
        void pause(long millis) throws InterruptedException;
    }

    static final class TransientFailure extends Exception {
        TransientFailure(String message) {
            super(message);
        }
    }

    static final class RetryPolicy {
        private final int maxAttempts;
        private final long baseMs;
        private final long capMs;
        private final Sleeper sleeper;

        RetryPolicy(int maxAttempts, long baseMs, long capMs,
                    Sleeper sleeper) {
            if (maxAttempts < 1 || baseMs < 0 || capMs < baseMs) {
                throw new IllegalArgumentException("invalid policy");
            }
            this.maxAttempts = maxAttempts;
            this.baseMs = baseMs;
            this.capMs = capMs;
            this.sleeper = sleeper;
        }

        long backoffAfterFailure(int failedAttempt) {
            long delay = baseMs;
            for (int i = 1; i < failedAttempt; i++) {
                if (delay > capMs / 2) {
                    return capMs;
                }
                delay = Math.min(capMs, delay * 2);
            }
            return Math.min(capMs, delay);
        }

        <T> T call(CheckedOperation<T> action) throws Exception {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    return action.run();
                } catch (TransientFailure failure) {
                    if (attempt == maxAttempts) {
                        throw failure;
                    }

                    long delay = backoffAfterFailure(attempt);
                    System.out.println(
                            "retry after " + attempt + " = "
                                    + delay + "ms");

                    try {
                        sleeper.pause(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                }
            }
            throw new AssertionError("unreachable");
        }
    }

    public static void main(String[] args) throws Exception {
        // Test sleeper: không đợi thời gian thật.
        RetryPolicy retry = new RetryPolicy(3, 100, 500, ms -> {});

        AtomicInteger calls = new AtomicInteger();
        String result = retry.call(() -> {
            int attempt = calls.incrementAndGet();
            System.out.println("call = " + attempt);
            if (attempt < 3) {
                throw new TransientFailure("temporary");
            }
            return "OK";
        });

        System.out.println("result = " + result);
        System.out.println("attempts = " + calls.get());

        AtomicInteger invalidCalls = new AtomicInteger();
        try {
            retry.call(() -> {
                invalidCalls.incrementAndGet();
                throw new IllegalArgumentException("bad request");
            });
        } catch (IllegalArgumentException failure) {
            System.out.println(
                    "not retryable = " + failure.getMessage());
        }
        System.out.println(
                "validation attempts = " + invalidCalls.get());

        AtomicInteger downCalls = new AtomicInteger();
        try {
            retry.call(() -> {
                downCalls.incrementAndGet();
                throw new TransientFailure("service unavailable");
            });
        } catch (TransientFailure failure) {
            System.out.println(
                    "exhausted = " + failure.getMessage());
        }
        System.out.println(
                "exhausted attempts = " + downCalls.get());
    }
}
~~~

### Biên dịch và chạy

~~~bash
javac BoundedRetryDemo.java
java BoundedRetryDemo
~~~

**Stdout chính xác của chương trình**:

~~~text
call = 1
retry after 1 = 100ms
call = 2
retry after 2 = 200ms
call = 3
result = OK
attempts = 3
not retryable = bad request
validation attempts = 1
retry after 1 = 100ms
retry after 2 = 200ms
exhausted = service unavailable
exhausted attempts = 3
~~~

### Giải thích output

- Ở trường hợp đầu, attempt 1 và 2 ném `TransientFailure`, nên retry được schedule sau delay tính toán; attempt 3 trả `"OK"`. Tổng cộng có **3 attempts**, **2 retries**.
- `IllegalArgumentException` không thuộc catch trong retry loop, nên **chỉ gọi một lần**, không “chữa” input bằng cách thử thêm.
- Khi downstream luôn thất bại, loop dừng ở attempt 3 và **ném lại lỗi cuối** `TransientFailure`; không thử lần thứ tư.
- Thời gian đợi chỉ được **ghi kế hoạch** chứ không thực sự chờ vì truyền `ms -> {}`. Đó là cách kiểm thử logic backoff mà không bị flaky do scheduler.
- Nếu thay bằng `Thread::sleep`, chính **thread gọi `RetryPolicy.call`** sẽ bị block trong lúc chờ; điều này chỉ phù hợp với môi trường synchronous/worker được quản lý. Không gọi nó trên event-loop hoặc dùng để chiếm request thread quá lâu.
- Bài demo cố tình tách policy cơ bản (maxAttempts, loại lỗi, backoff, interrupt) khỏi production policy còn phải bổ sung **overall deadline, per-attempt timeout, jitter, HTTP status/Retry-After, quan sát metrics và idempotency**. Không nên copy nguyên demo thành client tiền thật.

### Vì sao không đặt một while(true)?

`while(true)` chỉ an toàn khi mọi đường đều chứng minh được có điều kiện dừng hữu hạn. Chính sách ở đây dùng `for (attempt <= maxAttempts)`, nên tránh vô hạn ngay cả khi lỗi tạm thời lặp mãi. Các component gọi retry **nhiều tầng** vẫn phải có ngân sách tổng, không thể chỉ dựa vào giới hạn cục bộ.

## 7. Tính an toàn khi chạy lại: ba kiểu thao tác

### 7.1. Read-only và thao tác không có side effect dự định

Tra cứu sản phẩm theo ID, lấy một trạng thái được xác định rõ thường có thể retry sau lỗi mạng. Tuy nhiên, **GET không tự động an toàn** nếu endpoint thực chất làm thay đổi trạng thái nghiêm trọng (ví dụ dùng GET để kích hoạt chuyển tiền): đó là API thiết kế sai semantics. Giới hạn thời gian vẫn cần để tránh bombard downstream.

### 7.2. Thao tác đặt trạng thái (idempotent theo nghiệp vụ)

Ví dụ `setInventoryLevel(productId, 10)`: gọi cùng request nhiều lần vẫn đặt mức tồn kho thành 10. Với HTTP, `PUT` là idempotent theo semantics chuẩn khi resource server thực hiện đúng hợp đồng.

Khác với `incrementInventory(productId, 10)`: gọi hai lần cộng thêm 20, **không idempotent**. Tên method hoặc HTTP verb riêng nó không làm phép cộng trở nên an toàn khi retry.

### 7.3. Thao tác tạo hoặc chuyển tiền (non-idempotent nếu không có key)

Ví dụ:

~~~text
createPayment(order=7, amount=500000)
createPayment(order=7, amount=500000)
~~~

Nếu API tạo một payment ID mới cho mỗi lần gọi, kết quả là **hai khoản thanh toán**, dù payload giống hệt. Để có thể retry, cần định danh một logical operation, ví dụ `Idempotency-Key: pay-order-7-v1` và xử lý trùng ở phía nhận.

**Đừng sinh key mới cho mỗi attempt**. Cùng logical operation phải dùng cùng idempotency key qua các retries, kể cả sau timeout/mất response. Nếu request thật sự là một giao dịch mới, phải dùng identity mới theo business contract.

## 8. Idempotency key: contract tối thiểu

Một mô hình thanh toán hoặc tạo job có thể định nghĩa:

~~~http
POST /payments HTTP/1.1
Idempotency-Key: 8fb1e0c4-... 
Content-Type: application/json

{"orderId":"order-7","amount":500000,"currency":"VND"}
~~~

Đây là **ví dụ HTTP minh họa**, key là placeholder cấu trúc, không phải token được cấp thực tế.

Phía server cần:

1. **Xác thực/giới hạn scope** của key: cùng key dưới cùng tenant/account hoặc namespace, tránh khách hàng A vô tình va chạm B.
2. **So khớp payload** bằng request fingerprint ổn định. Nếu key giống nhưng nội dung tiền/số lượng khác, phải báo xung đột/không hợp lệ theo API contract, không âm thầm trả response của request cũ.
3. **Claim key atomic**: hai request đồng thời cùng key không được cùng vượt qua bước kiểm tra rồi đều thực thi side effect.
4. **Lưu trạng thái** như `IN_PROGRESS`, `SUCCEEDED`, `FAILED_FINAL` và kết quả nghiệp vụ để request trùng biết hành động phù hợp.
5. **Có chính sách cho IN_PROGRESS**: chờ giới hạn, trả 202/409 hoặc hướng dẫn poll; không tự khẳng định đã thành công.
6. **Giữ key đủ lâu** theo thời gian mà client có thể retry/replay, có TTL và quy trình cleanup rõ.
7. **Xử lý crash giữa các bước**: lưu key nhưng side effect chưa chạy, hoặc side effect đã chạy nhưng chưa lưu kết quả; cần transaction boundary/outbox/provider idempotency/reconciliation.

### Bảng trạng thái cơ bản

| Trạng thái theo key | Request mới cùng key/payload | Điều kiện cần nhớ |
| --- | --- | --- |
| Chưa có key | Thử claim atomic và bắt đầu operation | UNIQUE constraint/transaction/conditional write |
| `IN_PROGRESS` | Không thực thi side effect lần hai; trả trạng thái/đợi có giới hạn | Nếu owner chết cần lease/timeout/recovery an toàn |
| `SUCCEEDED` | Trả kết quả đã lưu hoặc status của operation cũ | Không tạo thêm payment/job |
| `FAILED_FINAL` | Trả lỗi terminal đã lưu, hoặc contract cho phép tạo operation mới bằng key mới | Không coi cùng key là một giao dịch mới |
| Key cũ nhưng payload khác | Từ chối theo contract (thường 409 hoặc lỗi tương ứng) | Ngăn dùng lại key sai mục đích |

Không được dùng `if (!map.containsKey(key)) { performSideEffect(); map.put(key,...); }` trong môi trường cạnh tranh: hai thread/pod có thể cùng qua check trước khi write. `synchronized` local hoặc `ConcurrentHashMap` cũng **không** giải quyết việc nhiều pod/replica và lưu dữ liệu sau restart.

### Atomic claim và business transaction

Nếu side effect là cập nhật **cùng một database**, có thể tận dụng UNIQUE index và transaction để claim key + ghi nghiệp vụ + lưu outcome trong một boundary phù hợp. Nếu side effect là gọi **payment provider bên ngoài**, local DB transaction **không thể** khiến HTTP call từ xa và DB commit trở nên atomic. Khi đó cần chuyển **cùng idempotency key tới provider** nếu provider hỗ trợ, ghi outbox/status và có cơ chế reconciliation cho tình huống kết quả không chắc chắn.

**Không hứa exactly-once end-to-end chỉ vì có idempotency table.** Thực tế, mục tiêu là một logical effect duy nhất theo key trong phạm vi contract và failure model mà hệ thống có thể kiểm soát.

## 9. Java 17: demo idempotency nội bộ một JVM

Ví dụ ngắn này cho thấy cùng một identity trả cùng một kết quả trong **bộ nhớ một tiến trình**, không tăng số logical operation lần hai. Nó **không** phải implementation thanh toán production hoặc idempotency phân tán.

Tạo tệp **`LocalIdempotencyDemo.java`**:

~~~java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalIdempotencyDemo {
    private static final ConcurrentHashMap<String, String> operations =
            new ConcurrentHashMap<>();

    private static final AtomicInteger committed =
            new AtomicInteger();

    static String recordOnce(String key) {
        return operations.computeIfAbsent(key, ignored -> {
            int number = committed.incrementAndGet();
            return "operation-" + number;
        });
    }

    public static void main(String[] args) {
        System.out.println("first = " + recordOnce("order-7"));
        System.out.println("retry = " + recordOnce("order-7"));
        System.out.println("logical operations = " + committed.get());
    }
}
~~~

Chạy:

~~~bash
javac LocalIdempotencyDemo.java
java LocalIdempotencyDemo
~~~

**Stdout xác định:**

~~~text
first = operation-1
retry = operation-1
logical operations = 1
~~~

Trong demo, `computeIfAbsent` là thao tác đồng bộ phù hợp để thêm một entry trong một `ConcurrentHashMap` của cùng JVM; callback tạo kết quả in-memory, không gọi downstream.

**Cảnh báo quan trọng:** Không đặt thao tác chuyển tiền/gửi email thật vào callback `computeIfAbsent` rồi kết luận an toàn production. Nếu callback gây side effect bên ngoài rồi JVM chết trước khi map lưu entry, retry có thể thực hiện side effect lần nữa. Nếu nhiều pod có map riêng, mỗi pod có thể thực hiện một lần. Để tránh trùng thật, cần state được lưu bền và được điều phối cùng business operation/provider.

## 10. Mô hình dữ liệu thực tế cho idempotent operation

Một bảng minh họa PostgreSQL:

~~~sql
CREATE TABLE idempotent_operations (
    tenant_id        VARCHAR(100) NOT NULL,
    operation_key    VARCHAR(200) NOT NULL,
    request_hash     VARCHAR(128) NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    response_payload TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, operation_key)
);
~~~

Đây là **DDL minh họa**, cần bổ sung giới hạn độ dài, index, retention và quyền truy cập tùy hệ thống. `PRIMARY KEY` đảm bảo hai transaction không cùng chèn một key trong một database. Nhưng table này tự nó **chưa** bảo đảm atomic effect với hệ thống bên ngoài.

Một luồng tham khảo:

~~~text
Request (tenant, idempotency key, request body)
                |
                v
Validate + fingerprint request body
                |
                v
Atomic INSERT / claim key
                |
       +--------+--------------------+
       |                             |
   claim được                   đã tồn tại
       |                             |
       v                             +-- mismatch body -> reject
 status IN_PROGRESS                 |
       |                             +-- SUCCEEDED -> reuse outcome
       v                             |
 perform business side effect       +-- IN_PROGRESS -> wait/poll policy
       |                             |
       v                             +-- failed -> follow explicit contract
 Save durable outcome
       |
       v
 Return response
~~~

Điều khó nhất là **crash giữa side effect và ghi outcome**. Nếu hai thay đổi cùng database, cần transaction phù hợp. Nếu hệ thống gọi bên ngoài, phải dựa vào remote idempotency token hoặc quy trình xác minh trạng thái thực tế.

### Khi IN_PROGRESS bị kẹt

Một record IN_PROGRESS không nên khóa mãi vì instance xử lý cũ đã chết. Có thể dùng lease + owner token/fencing; khi lease hết, worker mới phải xác định thao tác cũ đã áp dụng chưa trước khi chạy lại. Chỉ xóa record và chạy lại mà không reconciliation có thể gây duplicate.

### Khi payload thay đổi

Cùng key, một bên yêu cầu 500.000 VND, bên kia 700.000 VND: đó **không** phải cùng logical command. Server phải từ chối theo contract, không lấy kết quả cũ hoặc tạo thêm side effect với cùng key.

### Khi key hết hạn

TTL ngắn hơn khả năng client replay có thể biến retry muộn thành operation mới. Chọn retention dựa trên SLA retry, xử lý queue, thời gian reconciliation và đặc tính nghiệp vụ; với thanh toán/đơn hàng cần lưu dấu vết nghiệp vụ bền vững hơn cache quota ngắn hạn.

## 11. Các kiểu công việc concurrency và điểm đặt retry

### 11.1. Retry ở HTTP client

Trong các lời gọi downstream, retry thường phù hợp **ở ranh giới gọi remote**, vì đây là nơi biết loại lỗi, mã HTTP, trạng thái gửi request và deadline. Hãy chuyển operation identity qua header, không sinh key trong mỗi attempt.

~~~java
public Receipt callProvider(String key, ChargeRequest request) {
    return paymentHttpClient.charge(key, request);
}
~~~

Đây là **snippet service**, các kiểu `Receipt`, `ChargeRequest` và `paymentHttpClient` thuộc ứng dụng. Đối với `charge`, retry chỉ an toàn khi provider hỗ trợ semantics idempotency theo `key`, hoặc có cơ chế status lookup/reconciliation đủ mạnh.

### 11.2. Retry trong ThreadPoolTaskExecutor

Một `ThreadPoolTaskExecutor` chỉ quyết định **worker/queue**, không tự tạo retry policy cho lỗi nghiệp vụ. Nếu task ném exception:

- `execute(Runnable)` với Runnable thô có thể để lỗi thoát trên worker/error observer.
- `submit(Callable)` thường giữ lỗi trong `Future`; caller gọi `get()` để thấy `ExecutionException` có cause.
- Lỗi **task rejection** khi pool bão hòa xảy ra tại thời điểm gửi; tự gửi lại ngay lập tức trong vòng lặp không delay có thể làm quá tải nặng hơn.
- Worker ngủ trong `Thread.sleep(backoff)` tiếp tục **chiếm slot**; nếu có hàng nghìn retry chờ, có thể gây thread starvation. Dùng scheduler/delayed queue có giới hạn hoặc retry async nonblocking khi cần nhiều pending retries.

**Không** dùng một retry vô hạn bên trong một Runnable mà không theo dõi trạng thái: nếu task bị cancel, shutdown hoặc context đóng, worker có thể không thoát đúng hạn.

### 11.3. Retry với CompletableFuture

Một future thất bại **không tự chạy lại** chỉ vì bạn gọi:

~~~java
future.exceptionally(error -> fallback);
~~~

Đó là **recovery từ lỗi của stage**, không tự tạo attempt mới. Để retry một `CompletableFuture`, cần **supplier tạo future mới cho mỗi attempt** và điều phối delay/exhaustion:

~~~java
Supplier<CompletableFuture<Receipt>> freshAttempt =
        () -> provider.chargeAsync(sameIdempotencyKey, request);
~~~

Đây là **snippet**, giả định `provider`, `sameIdempotencyKey`, `request` và type `Receipt` có sẵn.

Mỗi attempt phải gọi `freshAttempt.get()` để tạo một operation mới theo cùng logical idempotency identity; chỉ đăng ký callback khác lên **cùng một future đã exceptional** không phải thử lại.

Một scheduler có giới hạn có thể lên lịch attempt tiếp:

~~~java
scheduler.schedule(
        () -> startNextAttemptIfAllowed(),
        delayMillis,
        TimeUnit.MILLISECONDS);
~~~

Đây là **snippet**, cần `scheduler`, `startNextAttemptIfAllowed()`, `delayMillis` và deadline logic thực tế. Lên lịch không chặn worker nhưng job scheduler cũng cần queue capacity, ownership, cancel và shutdown. Khi future kết hợp thất bại, còn phải phân biệt retry **toàn chuỗi** hay chỉ source bị lỗi.

### 11.4. @Async và proxy của Spring

`@Async` quyết định cách dispatch một method qua Spring proxy, **không tự retry**. Nếu kết hợp `@Async` với retry annotation:

- Kiểm tra thứ tự interceptor và kiểu trả về thực tế.
- `@Async void` không trả Future để caller biết kết quả, phải có `AsyncUncaughtExceptionHandler` hoặc giám sát job riêng.
- `@Async CompletableFuture` yêu cầu quan sát future exceptional; retry một completion stage không giống retry method synchronous.
- Gọi từ chính bean bằng `this.method()` trong proxy mode thông thường bypass `@Async` và các advice proxy như retry.
- Không giả định transaction của caller hoặc MDC/security context tự chuyển vào worker.

Nếu công việc có side effect quan trọng, một queue có persistence, job ID và trạng thái retry bền vững thường phù hợp hơn một callback fire-and-forget trong JVM.

## 12. Spring Retry và Spring Framework 7: hai cách đếm dễ nhầm

Có **hai API khác nhau** cần phân biệt theo version/dependency, ngay cả khi annotation cùng tên `@Retryable`.

### 12.1. Spring Retry 2.x (module riêng, dùng với nhiều ứng dụng Spring 6/Boot 3)

Spring Retry nằm trong dependency **`org.springframework.retry:spring-retry`**, không phải một tính năng mặc định cứ có Spring 6.2 là tự bật. Đối với annotation/proxy, cần cấu hình `@EnableRetry` và các thành phần Spring AOP phù hợp.

Ví dụ Spring Retry 2.x:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class RetryConfiguration {
}
~~~

Một service:

~~~java
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class RemoteCatalog {
    private final PartnerClient client;

    public RemoteCatalog(PartnerClient client) {
        this.client = client;
    }

    @Retryable(
            retryFor = TemporaryPartnerFailure.class,
            maxAttempts = 3,
            backoff = @Backoff(
                    delay = 200,
                    multiplier = 2.0,
                    maxDelay = 1000))
    public Product load(String productId) {
        return client.fetch(productId);
    }
}
~~~

Đây là **các class minh họa trong ứng dụng Spring Retry 2.x**, cần các kiểu `PartnerClient`, `TemporaryPartnerFailure`, `Product` do dự án định nghĩa và dependency Spring Retry/AOP. `maxAttempts = 3` nghĩa là **3 lần gọi tổng cộng** (lần đầu + tối đa 2 retries). Backoff ví dụ: 200 ms rồi 400 ms, nếu cả hai retry đều xảy ra.

**Cảnh báo proxy:** gọi `this.load(...)` từ chính bean trong proxy mode có thể bypass retry advice. Gọi từ bean khác qua Spring proxy. Retry annotation cũng không thay thế overall deadline/idempotency và có thể block thread lúc backoff tùy interceptor.

### 12.2. Spring Framework 7 core resilience

Từ **Spring Framework 7.0**, core Spring Framework có bộ resilience API riêng, trong đó có một annotation `@Retryable` thuộc package **`org.springframework.resilience.annotation`**. Nó **không đồng nhất** với `org.springframework.retry.annotation.Retryable` của module Spring Retry.

Với cơ chế Framework 7, thuộc tính đếm retry sau lần đầu (ví dụ `maxRetries` trong reference) được định nghĩa theo **số lần retry**; tài liệu core ghi rõ tổng attempts bằng **1 initial attempt + số retry được phép**. Đây là một lý do quan trọng phải đọc đúng **version** và **package** của annotation đang dùng, không chuyển cấu hình `maxAttempts=3` từ Spring Retry sang một API `maxRetries=3` rồi mặc định vẫn chỉ có 3 lần gọi.

Khi nâng cấp Spring/Boot, hãy kiểm tra dependency graph, annotation import, proxy activation và semantics retry thực tế bằng test đếm invocation. Không giả định ứng dụng Spring Framework 6 có core `@Retryable` thuộc package Framework 7.

### 12.3. Khi nào dùng thư viện thay vì tự viết loop?

- **Spring Retry:** logic retry method synchronous, tích hợp bean/proxy, dễ xác định exception classifier và recovery.
- **Resilience4j Retry:** cấu hình retry, metric, composition với CircuitBreaker/RateLimiter/Bulkhead; cần chú ý cách decorator và thư viện nhận kết quả async.
- **HTTP client/SDK có retry tích hợp:** tận dụng khi đã biết semantics endpoint và có deadline/idempotency.
- **Custom loop:** phù hợp cho ví dụ học hoặc policy rất đặc thù, nhưng cần tự kiểm soát timeout, scheduler, observability và race condition.

Dù dùng công cụ nào, **không để nhiều tầng tự động retry cùng một thao tác** nếu không nắm được tổng số attempts.

## 13. Retry khác Circuit Breaker, Rate Limiter và Bulkhead

| Cơ chế | Câu hỏi chính | Khi nào giúp? |
| --- | --- | --- |
| Retry | “Lỗi tạm thời này có đáng thử lại trong budget không?” | Glitch ngắn hạn, lỗi transient, kết quả an toàn khi chạy lại |
| Circuit Breaker | “Downstream đang lỗi hàng loạt, có nên ngưng gửi việc một thời gian?” | Tránh đập liên tục vào dependency không khỏe |
| Rate Limiter | “Mỗi khoảng thời gian được bao nhiêu lượt?” | Giới hạn quota/rate theo client hoặc backend |
| Bulkhead/Semaphore | “Có bao nhiêu lời gọi đồng thời được giữ tài nguyên?” | Bảo vệ DB/HTTP connection, memory, worker |
| Timeout | “Một attempt hoặc toàn operation được chờ bao lâu?” | Ngăn request treo vô hạn |
| Idempotency | “Lặp cùng operation có tạo side effect trùng không?” | Ngăn thu tiền/đặt hàng/gửi job hai lần |
| Outbox/Job store | “Crash/restart có mất ý định thực hiện hoặc trạng thái retry không?” | Workflow cần durability và retry sau process restart |

**Retry là cách tạo thêm tải**, không phải cách giảm tải: với 10% lỗi và retry liên tục, hệ thống phụ trợ có thể nhận nhiều request hơn mức traffic ban đầu. Khi downstream đang bão hòa, backoff/jitter, circuit breaker, rate limit và retry budget giúp giảm khuếch đại.

### Tính toán retry storm qua nhiều tầng

Nếu một API frontend cho 3 attempts và service backend lại cho 3 attempts trên cùng remote call, một request nghiệp vụ có thể tạo **9 calls remote** ở trường hợp lỗi dai dẳng. Nếu client người dùng lại retry 2 lần, tổng thành **18 calls remote**. Cần quyết định **tầng nào sở hữu retry chính**, còn tầng khác truyền nguyên nhân/đặt deadline thay vì nhân số attempts.

### Budget theo tỷ lệ

Ngoài maxAttempts từng logical operation, nhiều hệ thống còn dùng “retry tokens” hoặc giới hạn retries trong một khoảng thời gian. Mục đích là tránh khi dependency gặp outage, hàng nghìn operation mỗi operation retry 3 lần tạo tổng tải quá lớn. Tỷ lệ retry được phép nên dựa vào SLO/downstream capacity và có thể giảm khi circuit breaker mở.

## 14. Xử lý HTTP Retry-After một cách đúng nghĩa

`Retry-After` là HTTP header có thể biểu thị **số giây chờ** hoặc **thời điểm HTTP-date**; không nên tự diễn giải mọi giá trị là milliseconds. Ví dụ:

~~~http
HTTP/1.1 503 Service Unavailable
Retry-After: 3
Content-Type: text/plain

Temporarily unavailable
~~~

Client có thể xem xét thử lại **sau ít nhất 3 giây** nếu còn trong overall deadline và operation an toàn để chạy lại. Nếu SLA cho phép chờ tối đa 1 giây, policy đúng có thể là **không retry ngay**, trả lỗi có nguyên nhân thay vì bất chấp header.

Với 429, rate limiter có thể gợi ý thời điểm quota hồi phục. Nhưng không phải mọi response 429/503 đều có Retry-After; client cần fallback backoff với jitter có cap. Cần parse HTTP-date theo chuẩn, dùng clock đáng tin và tính thêm độ trễ mạng.

## 15. Cách kiểm soát timeout và cancellation trong Spring concurrency

Một request có chuỗi:

~~~text
HTTP request deadline = 2.5 s
    |
    +-- thread pool queue wait
    +-- attempt 1: connect + read timeout
    +-- backoff
    +-- attempt 2
    +-- backoff
    +-- attempt 3
    |
    v
Response / failure trước deadline
~~~

Thời gian **xếp hàng** trong executor vẫn thuộc ngân sách tổng, không thể bỏ qua. Lưu deadline bằng nguồn đo elapsed-time phù hợp, ví dụ `System.nanoTime()` cho timeout trong JVM (đừng dùng `currentTimeMillis()` làm nguồn monotonic).

### Khi bị interrupt

Trong code blocking:

~~~java
try {
    Thread.sleep(delayMillis);
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException(
            "Retry interrupted", interrupted);
}
~~~

Đây là **snippet**, cần `delayMillis` đã được tính. Tác vụ nên tôn trọng shutdown/cancel; không bắt interrupt rồi lập tức retry lần nữa.

### Khi CompletableFuture timeout

`future.orTimeout(2, TimeUnit.SECONDS)` sẽ làm future hoàn thành exceptional nếu chưa xong khi đến hạn. Nó không mặc nhiên dừng remote request hoặc rollback side effect đã xảy ra. Tương tự, `future.get(2, TimeUnit.SECONDS)` hết hạn **chỉ là caller ngừng chờ**, còn task có thể vẫn chạy.

Khi hợp nhất nhiều attempt, cancellation cần được chuyển tới attempt hiện tại nếu underlying client hỗ trợ và việc hủy không gây side effect mới; không tự hủy một request thanh toán đã vào server rồi khẳng định chưa thanh toán.

## 16. Kết quả không chắc chắn: xử lý theo hợp đồng nghiệp vụ

Giả sử service tạo order:

1. Client gửi `createOrder(orderKey=K)`.
2. Server commit order thành công.
3. Connection bị reset trước khi client nhận order ID.
4. Client nhận `IOException`.

Ba chiến lược:

**A. Có idempotency key và server dedup:** client retry **cùng K**. Server trả order ID cũ, không tạo order mới.

**B. Có status lookup:** trước khi retry operation tạo mới, truy vấn theo `clientOrderId=K` hoặc tìm trạng thái đã commit. Nếu xác định chưa có, mới cân nhắc gửi lại; việc kiểm tra cũng cần xử lý race/concurrent requests.

**C. Không có identity/dedup và hậu quả lớn:** không tự retry. Báo trạng thái chưa xác định, lưu thông tin để reconciliation hoặc yêu cầu operator/manual review.

Tránh trả lỗi “order failed” như một khẳng định chắc chắn nếu kết quả thực tế **chưa biết**; nếu client tự bấm lại như một đơn mới, hậu quả có thể nghiêm trọng hơn.

### Outbox và retry sau crash

Trong nghiệp vụ cần phát event khi ghi DB, **transactional outbox** giúp ghi trạng thái nghiệp vụ và một hàng outbox trong cùng transaction; worker gửi outbox sau đó. Delivery có thể **at-least-once**, nên consumer vẫn phải idempotent theo event ID. Đừng coi outbox là công cụ magical exactly-once across systems.

## 17. Sơ đồ quyết định trước mỗi retry

~~~text
Attempt thất bại
        |
        v
Đã cancel / interrupt / shutdown?
   | yes -> DỪNG; trả cancellation/interruption
   | no
   v
Exception/HTTP status thật sự có cơ hội hồi phục?
   | no -> DỪNG; báo lỗi gốc
   | yes
   v
Operation chạy lại có an toàn?
   | no/unknown -> STATUS LOOKUP / RECONCILE / DỪNG
   | yes
   v
Đạt maxAttempts / retry budget?
   | yes -> DỪNG; báo exhaustion
   | no
   v
Còn deadline sau khi chờ backoff/Retry-After?
   | no -> DỪNG; báo deadline exhaustion
   | yes
   v
Chọn delay với backoff + jitter / Retry-After
        |
        v
Chờ không chặn nếu async, kiểm soát queue
        |
        v
Retry SAME logical operation identity
        |
        +-- ghi attempt metric và quay lại
~~~

Quy trình cần phân biệt “truy vấn trạng thái” với “gọi lại gây side effect”; một số read-only lookup cũng có quota và timeout riêng.

## 18. Giám sát để biết retry đang giúp hay gây hại

Những chỉ số tối thiểu:

| Metric | Ý nghĩa |
| --- | --- |
| `operation_attempts_total` | Tổng số attempt thực tế theo operation/endpoint |
| `operation_retries_total` | Số attempt bổ sung sau lần đầu |
| `operation_retry_exhausted_total` | Số logical operation hết quota |
| `retry_delay_ms` | Phân bố delay và jitter |
| `attempt_latency_ms` | Thời gian mỗi attempt |
| `operation_latency_ms` | Tổng latency kể cả queue/backoff |
| `retry_success_after_failure` | Phần trăm operation thành công sau khi retry |
| `retry_not_safe_total` | Số lần không retry vì unknown outcome/idempotency thiếu |
| `idempotency_duplicate_total` | Số request trùng key được dedup |
| `idempotency_in_progress` | Key đang xử lý và thời gian bị treo |
| `downstream_rejections` | 429/503/connection saturation, dấu hiệu retry đang tăng tải |

Gắn correlation ID, logical operation ID và **attempt number** vào log/tracing. Tránh dùng mỗi retry như một transaction mới với ID mới hoàn toàn khiến không nối được nguyên nhân.

Không đưa key thanh toán, token bảo mật, payload nhạy cảm hoặc thông tin cá nhân chưa che vào log; có thể hash/key scope và dùng internal job ID. Cần có cảnh báo khi retry exhausted tăng hoặc p99 latency tăng dù tỉ lệ thành công cuối cùng có vẻ vẫn ổn.

## 19. Những test quan trọng

### 19.1. Test số lần gọi chính xác

Dùng một fake operation ném `TransientFailure` hai lần rồi thành công. Assert tổng calls = 3, delay đã lên lịch = 100/200 ms trong cấu hình ví dụ, result = OK. Với lỗi validation, assert calls = 1. Với lỗi transient vĩnh viễn, assert calls không vượt maxAttempts.

### 19.2. Test thời gian bằng fake clock/sleeper

Không cần `Thread.sleep(1000)` trong unit test. Inject `Sleeper`/`Clock` hoặc scheduler kiểm soát được; assert policy đã chọn delay, deadline và cancellation đúng. Khi kiểm thử full jitter, inject nguồn random có seed hoặc fake random để test phân phối/biên mà không cố đo chính xác thời gian hệ điều hành.

### 19.3. Test timeout sau commit

Mô phỏng server **đã commit** nhưng response bị mất. Client retry cùng key; assert số business transaction thực = 1 và response trả về cùng resource. Nếu hệ thống không có dedup, test này phải báo **không an toàn để retry**, không cố che bằng retry thêm.

### 19.4. Test hai request cùng key đến đồng thời

Cho hai thread/pod đồng thời claim cùng key: chỉ một owner được thực thi side effect. Owner thứ hai nhận kết quả cũ, trạng thái pending, hoặc response theo contract. Unit test dùng `CountDownLatch`, integration test dùng database/Redis transaction thật; `synchronized` local không chứng minh an toàn đa pod.

### 19.5. Test retry storm và nested policies

Load test khi 30% downstream calls bị 503, giữ nguyên request rate của client và quan sát **tổng requests tới downstream**. Nếu upstream và SDK cùng retry, kiểm tra hệ số nhân. Kiểm tra bảo vệ bằng circuit breaker/retry budget và kết quả khi downstream hoàn toàn không phục hồi.

### 19.6. Test cancellation/shutdown

Đóng Spring context hoặc cancel future khi đang chờ backoff. Verify không có attempt mới sau cancellation/deadline; worker được giải phóng, tài nguyên trả về, không có queue retry vô hạn.

### 19.7. Test phân loại lỗi

Tách test cho validation, authentication, conflict, timeout trước khi gửi, timeout sau khi gửi, 429/503 có Retry-After, 500 sau commit, interrupt/cancel và failure nội bộ code. **Không** chỉ test mỗi happy path “thất bại một lần rồi thành công”.

## 20. Sai lầm thường gặp

**Sai lầm 1: “Bắt mọi Exception rồi retry”.**  
Lỗi dữ liệu, bug lập trình hoặc từ chối quyền sẽ không tự biến mất; retry tạo thêm tải và che nguyên nhân.

**Sai lầm 2: “maxAttempts=3 nghĩa là 3 retry cộng lần đầu”.**  
Sai với Spring Retry 2.x: `maxAttempts` tính cả lần đầu. Một số API khác dùng `maxRetries` để đếm lần sau; phải đọc đúng contract.

**Sai lầm 3: “Timeout chứng minh server chưa chạy”.**  
Sai: server có thể đã commit và chỉ mất response. Phải xét unknown outcome.

**Sai lầm 4: “POST cùng payload là idempotent”.**  
Không mặc định. Nếu API tạo mới mỗi lần, cùng payload vẫn tạo hai record/payment.

**Sai lầm 5: “Mỗi attempt dùng idempotency key mới cho chắc”.**  
Ngược lại, nó phá dedup giữa các attempts của cùng logical operation.

**Sai lầm 6: “Chỉ cần ConcurrentHashMap là an toàn”.**  
Nó không chia sẻ giữa pod, không bền qua crash, không atomic với remote side effect.

**Sai lầm 7: “Backoff cố định là đủ khi hàng nghìn client cùng lỗi”.**  
Retry đồng loạt sau cùng một delay có thể gây thundering herd; cần jitter/budget.

**Sai lầm 8: “Đã có maxAttempts nên không cần deadline”.**  
Ba lần I/O mỗi lần 20 giây vẫn có thể vượt SLA một phút.

**Sai lầm 9: “Có tổng deadline nên không cần per-attempt timeout”.**  
Một attempt I/O không giới hạn có thể không phản ứng với deadline nếu client không được cấu hình hủy.

**Sai lầm 10: “Thread.sleep trong @Async không tốn tài nguyên”.**  
Nó giữ worker, có thể làm đầy pool và queue; dùng scheduler khi nhiều retry dài.

**Sai lầm 11: “CompletableFuture.exceptionally là retry”.**  
Nó xử lý exceptional completion; để thử lại phải gọi supplier tạo attempt mới.

**Sai lầm 12: “Mọi tầng đều retry 3 lần là tăng khả năng thành công mà không có giá”.**  
Có thể tạo 9 hoặc 27 calls thực tới cùng dependency.

**Sai lầm 13: “@Retryable ở method nào cũng chạy”.**  
Với proxy-based Spring Retry, self-invocation thường bypass advice; cần gọi qua bean/proxy, cấu hình AOP đúng và test thật.

**Sai lầm 14: “@Async void ném lỗi lên caller”.**  
Ngoại lệ trong worker không tự truyền về caller; cần handler hoặc Future/status riêng.

**Sai lầm 15: “Idempotency key đã lưu là chắc chắn đúng một lần”.**  
Crash sau remote side effect nhưng trước khi lưu outcome vẫn có thể gây duplicate. Dùng provider key, transaction/outbox, reconciliation.

**Sai lầm 16: “Retry thất bại thì trả success để người dùng yên tâm”.**  
Đó là che lỗi. Cần báo trạng thái thất bại/chưa xác định hoặc job pending có thể theo dõi.

**Sai lầm 17: “Hết Retry-After thì nhất định thử lại”.**  
Nếu deadline đã hết hoặc operation không an toàn, cần dừng.

**Sai lầm 18: “Retry luôn có thể cải thiện hệ thống đang quá tải”.**  
Retry tăng traffic. Khi backend bão hòa, có thể làm outage kéo dài; cần backoff/jitter, rate/bulkhead/circuit breaker.

## 21. Bài tập tự luyện

### Bài 1 — Đếm đúng số attempts

Policy `maxAttempts=4`, operation fail transient mãi. Có bao nhiêu lần gọi tất cả?

**Đáp án:** tối đa 4 attempts, gồm 1 lần đầu + 3 retries nếu code policy dùng định nghĩa này. Đừng nhầm với `maxRetries=4` ở API khác.

### Bài 2 — Lỗi validation

API nhận thiếu `customerId` và trả 400. Có nên tự retry không?

**Đáp án:** không retry cùng payload. Cần sửa input hoặc trả lỗi validation cho client.

### Bài 3 — Timeout sau khi gửi thanh toán

Client đã gửi request chuyển tiền, bị read timeout. Có thể tự gửi lại bằng một key mới không?

**Đáp án:** không an toàn. Server có thể đã ghi giao dịch. Dùng lại cùng idempotency key nếu provider hỗ trợ hoặc status lookup/reconciliation; nếu không có bảo đảm, xử lý unknown outcome theo contract.

### Bài 4 — Tính backoff

Base = 100 ms, multiplier 2, cap 500 ms, maxAttempts=5. Nếu mọi attempt thất bại, delay nào xảy ra giữa các attempts trước khi exhausted?

**Đáp án:** 100 ms, 200 ms, 400 ms, 500 ms — có 4 khoảng chờ cho tối đa 5 lần gọi. Jitter có thể thay đổi delay thật trong giới hạn đã định.

### Bài 5 — Spring Retry khác Framework 7

Một nhóm đang dùng Spring Retry `org.springframework.retry.annotation.Retryable(maxAttempts=3)`. Nhóm khác tham khảo Spring Framework 7 core `@Retryable` với `maxRetries=3`. Số invocation tối đa có cùng không?

**Đáp án:** không. Trong Spring Retry, maxAttempts 3 là 3 lần tổng; trong core Framework 7, maxRetries 3 có thể cho 1 lần đầu + 3 retries. Phải xác minh đúng API và import.

### Bài 6 — Retry qua nhiều tầng

HTTP client retry 3 attempts, service cũng retry 3 attempts. Khi lỗi liên tục, tối đa bao nhiêu calls downstream cho một operation?

**Đáp án:** có thể là 9. Hãy chọn nơi sở hữu retry và đặt budget tổng.

### Bài 7 — Idempotency khi chạy nhiều pod

Hai request cùng key đến hai pod, mỗi pod dùng `HashMap` riêng để dedup. Có bảo đảm thực hiện đúng một lần không?

**Đáp án:** không. Hai map không chia sẻ state. Dùng database/coordination atomic hoặc provider idempotency để chặn duplicate toàn cụm.

### Bài 8 — Thread pool đầy

Một `ThreadPoolTaskExecutor` đã đầy. Service catch `TaskRejectedException` rồi ngay lập tức gọi submit lại trong vòng `while(true)`. Hệ quả?

**Đáp án:** vòng lặp có thể đốt CPU và đẩy áp lực vào executor đang đầy. Cần fail fast hoặc retry có giới hạn/backoff, đồng thời cân nhắc giới hạn queue và ownership.

### Bài 9 — Không có Retry-After

Server trả 503 không có Retry-After. Bạn nên làm gì?

**Đáp án:** nếu operation an toàn để chạy lại, chọn backoff với jitter, tôn trọng attempts/deadline/budget; nếu không an toàn thì dừng và reconcile thay vì mặc định retry.

### Bài 10 — CompletableFuture

`future.exceptionally(ex -> defaultValue)` có tạo thêm HTTP request không?

**Đáp án:** không. Nó chỉ recover failure của future. Retry thật cần nhà cung cấp tạo attempt mới và một bộ điều phối delay/exhaustion.

## 22. Checklist áp dụng cho Spring production

- [ ] Định nghĩa logical operation và phạm vi idempotency rõ: user/tenant/key/payload hash.
- [ ] Phân loại retryable exception/status; không retry validation/bug/interrupt bừa bãi.
- [ ] Max attempts **có giới hạn** và cách đếm đã được test.
- [ ] Có per-attempt timeout, overall deadline và retry budget toàn tầng.
- [ ] Backoff có cap, jitter; xử lý `Retry-After` đúng đơn vị và giới hạn.
- [ ] Không retry non-idempotent operation khi outcome chưa rõ nếu thiếu dedup/lookup.
- [ ] Giữ **cùng idempotency key** qua toàn bộ retries của một logical operation.
- [ ] Dùng atomic claim/unique constraint; xử lý mismatch, IN_PROGRESS, crash và TTL.
- [ ] Với external side effect: provider idempotency/outbox/reconciliation, không suy exactly-once chỉ từ DB local.
- [ ] Không nested retry quá mức; hiểu retry đã được bật trong SDK/client.
- [ ] Không sleep lâu trong pool nhỏ hoặc request thread, ưu tiên scheduler có giới hạn nếu async.
- [ ] Với Spring Retry: có dependency và proxy, method public được gọi qua bean; phân biệt Spring Framework 7 core.
- [ ] Với `@Async`/`CompletableFuture`: có error observer và cancellation lifecycle.
- [ ] Metrics có attempt, retry, exhausted, deadline, duplicate, latency và saturation.
- [ ] Có test timeout sau commit, duplicate concurrent, failover và shutdown.
- [ ] Không trả success giả khi exhaustion/unknown outcome; báo trạng thái có thể theo dõi.

## 23. Tóm tắt

**Retry tốt = đúng lỗi + đúng thao tác + số lần hữu hạn + ngân sách thời gian/tài nguyên + backoff/jitter + kết quả có thể quan sát.** Không có điều kiện nào trong số này là tùy chọn khi lỗi có thể gây ảnh hưởng nghiệp vụ.

**An toàn khi chạy lại** đòi hỏi hơn việc gửi request giống nhau: phải xác định cùng **logical operation identity**, bảo đảm **cùng side effect không được áp dụng hai lần** trong phạm vi contract, và xử lý kết quả không chắc chắn do timeout/crash.

Trong Spring concurrency, `ThreadPoolTaskExecutor`/`@Async` giải quyết việc dispatch công việc, không tự giải quyết retry hay idempotency. Spring Retry hoặc Resilience4j hỗ trợ orchestration nhưng không biến một thao tác chuyển tiền không có dedup trở thành an toàn. Nếu không thể chứng minh retry an toàn, **dừng, tra cứu trạng thái hoặc reconcile**, thay vì thử lại mù.

## Tài liệu tham khảo

- Java SE 17 — `ExecutorService`, `Future`, `ScheduledExecutorService`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html
- Java SE 17 — `ConcurrentHashMap`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html
- Java SE 17 — `CompletableFuture`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html
- RFC 9110 — HTTP Semantics, safe/idempotent methods và `Retry-After`: https://www.rfc-editor.org/rfc/rfc9110.html
- RFC 6585 — HTTP 429 Too Many Requests: https://www.rfc-editor.org/rfc/rfc6585.html
- Spring Retry 2.x `@Retryable` (maxAttempts tính cả lần đầu): https://docs.spring.io/spring-retry/docs/current/apidocs/spring.retry/org/springframework/retry/annotation/Retryable.html
- Spring Retry `@EnableRetry`: https://docs.spring.io/spring-retry/docs/current/apidocs/spring.retry/org/springframework/retry/annotation/EnableRetry.html
- Spring Framework 7 — Core Resilience Features: https://docs.spring.io/spring-framework/reference/core/resilience.html
- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- AWS Builders' Library — Timeouts, retries, backoff with jitter: https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/
- Microservices.io — Transactional Outbox Pattern: https://microservices.io/patterns/data/transactional-outbox.html


</details>

- [Quay lại đầu trang](#back-to-top)
