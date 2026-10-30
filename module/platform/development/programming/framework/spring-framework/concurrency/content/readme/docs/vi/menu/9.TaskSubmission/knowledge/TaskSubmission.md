<a id="back-to-top"></a>

# Gửi và nhận tác vụ

## Menu
- [Gửi Runnable không có kết quả bằng execute()](#task-submission-001)
- [Gửi Runnable hoặc Callable qua submit()](#task-submission-002)
- [Nhận kết quả, kiểm tra hoàn tất và nhận biết hủy qua Future](#task-submission-003)
- [get() có giới hạn thời gian chờ](#task-submission-004)
- [Khác biệt đường truyền ngoại lệ giữa execute() và submit()](#task-submission-005)
- [Thời điểm gửi tác vụ khác với thời điểm bắt đầu chạy](#task-submission-006)
- [Chụp dữ liệu đầu vào phù hợp trước khi chuyển sang luồng khác](#task-submission-007)
- [Không đồng nhất tác vụ đã được nhận với nghiệp vụ đã hoàn thành](#task-submission-008)

## <a id="task-submission-001">Gửi Runnable không có kết quả bằng execute()</a>

<details>
<summary>Xem chi tiết</summary>

# Gửi Runnable không có kết quả bằng execute()

## 1. Bài toán: giao một công việc cho bộ thực thi

Trong ứng dụng Spring, một luồng đang xử lý yêu cầu có thể cần giao một công việc cho thành phần khác xử lý: ghi log nghiệp vụ, cập nhật một bộ đếm, gửi sự kiện, làm sạch dữ liệu tạm, hoặc kích hoạt một thao tác nền. Khi công việc **không cần trả về một giá trị cho bên gửi**, Java cung cấp `Runnable` và phương thức `execute(Runnable)` của `Executor`; Spring cung cấp cùng cách làm qua `TaskExecutor`.

Ta cần phân biệt hai trách nhiệm:

1. **Tác vụ (`Runnable`)** mô tả việc phải thực hiện.
2. **Bộ thực thi (`Executor` hoặc `TaskExecutor`)** quyết định thực thi tác vụ ở đâu, khi nào và với chính sách tài nguyên nào.

Ví dụ về ý định của bên gửi:

```java
Runnable task = () -> System.out.println("Đã xử lý sự kiện");
executor.execute(task);
```

Đây là **mảnh mã minh họa**, giả định `executor` đã được khai báo và khởi tạo. Lời gọi `execute` trả về `void`. Bên gửi không nhận được giá trị của `run()`, không có `Future` để chờ, và không tự nhận thông báo thành công/thất bại sau khi task chạy.

Một điểm đặc biệt quan trọng: **`execute()` không đồng nghĩa “luôn chạy trên luồng mới”**. Với bộ thực thi trực tiếp, `run()` có thể được gọi ngay trên luồng gửi; với thread pool, task thường chạy trên worker thread sau khi được chấp nhận, có thể sau một thời gian ở hàng đợi. Vì vậy việc dùng `execute` chỉ nói lên *hợp đồng gửi công việc*, còn tính bất đồng bộ phụ thuộc **triển khai executor**.

### Mục tiêu học tập

Sau bài này, bạn có thể:

- Viết `Runnable` bằng lambda, method reference hoặc một lớp hiện thực.
- Gửi tác vụ bằng `execute(Runnable)` qua Java `Executor` và Spring `TaskExecutor`.
- Phân biệt `run()`, `Thread.start()`, `execute()` và `submit()`.
- Hiểu `void` không có nghĩa task chắc chắn thành công hoặc được thực hiện bất đồng bộ.
- Xử lý trường hợp executor từ chối tác vụ; quan sát lỗi, hoàn tất và vòng đời bằng cơ chế riêng khi cần.
- Đưa mẫu gửi việc vào một bean Spring mà vẫn kiểm soát giới hạn worker, queue, trạng thái chia sẻ và thời điểm ứng dụng đóng.

## 2. Hợp đồng của Runnable và execute()

### 2.1. Runnable biểu diễn tác vụ không trả giá trị

`Runnable` là một functional interface trong `java.lang`:

```java
@FunctionalInterface
public interface Runnable {
    void run();
}
```

Chữ ký `void run()` có nghĩa lời gọi `run()` không trả dữ liệu trực tiếp. Phương thức này cũng không khai báo `throws Exception` cho checked exception. Tuy nhiên, tác vụ vẫn có thể gặp lỗi, có thể ném `RuntimeException` hoặc `Error`, và có thể thực hiện tác động phụ như ghi dữ liệu hoặc gửi yêu cầu.

Ba kiểu biểu diễn tương đương về mục tiêu:

```java
// Lambda:
Runnable lambda = () -> System.out.println("Làm việc");

// Method reference, đặt trong phạm vi có phương thức logMessage():
Runnable reference = this::logMessage;

// Lớp ẩn danh:
Runnable anonymous = new Runnable() {
    @Override
    public void run() {
        System.out.println("Làm việc");
    }
};
```

Đoạn này **chỉ minh họa các cách khai báo trong một class phù hợp**; `this::logMessage` yêu cầu phương thức instance `logMessage()` có sẵn. Khi làm ứng dụng thật, lambda thường đủ gọn nếu công việc nhỏ; với logic phức tạp, nên đưa nghiệp vụ vào một method/service có tên rõ ràng.

Không nên suy luận từ tên `Runnable` rằng Java đã tạo hay bắt đầu một thread. `Runnable` chỉ là đối tượng chứa hành vi cho tới khi có bên gọi `run()` hoặc giao nó cho một executor.

### 2.2. Executor có một phương thức cốt lõi

Java định nghĩa:

```java
public interface Executor {
    void execute(Runnable command);
}
```

Spring định nghĩa một abstraction tương thích:

```java
public interface TaskExecutor extends java.util.concurrent.Executor {
    void execute(Runnable task);
}
```

Đây là **chữ ký API tóm lược**, không phải mã nguồn đầy đủ của thư viện. Trong Spring, `TaskExecutor` mang hợp đồng thực thi `Runnable`, với `TaskRejectedException` nếu task không được chấp nhận. Các implementation có thể thực thi trực tiếp, tạo thread mới hoặc tái sử dụng worker trong pool.

Khi gọi:

```java
executor.execute(task);
```

- `task` là việc phải làm, không phải tên thread.
- `execute(...)` **không** trả `Future`, ID job, trạng thái hoàn tất hoặc kết quả tính toán.
- Nếu executor sử dụng chiến lược bất đồng bộ, lời gọi có thể trả rất nhanh sau khi tiếp nhận task; công việc có thể chưa bắt đầu.
- Nếu executor chạy đồng bộ hoặc áp dụng chính sách cho caller tự chạy task, lời gọi có thể chặn cho đến khi task chạy xong.
- Executor có thể từ chối task khi đã đóng hoặc vượt giới hạn; trong Spring `ThreadPoolTaskExecutor` với chính sách từ chối mặc định, việc này thường xuất hiện dưới dạng `TaskRejectedException`.
- Một ngoại lệ phát sinh **khi gọi execute** cần được phân biệt với lỗi của tác vụ phát sinh **sau khi đã được nhận để chạy**.

### 2.3. Bốn hành vi không giống nhau

| Cách gọi | Ai quyết định luồng thực thi? | Bên gửi nhận gì? | Khi nào nên dùng? |
|---|---|---|---|
| `task.run()` | Chính luồng gọi | `void` | Muốn gọi tác vụ như một phương thức thông thường |
| `new Thread(task).start()` | JVM khởi chạy thread đã tạo | `void` | Cần quản lý một thread cụ thể ở mức thấp |
| `executor.execute(task)` | Triển khai `Executor` | `void` | Muốn tách gửi việc khỏi chính sách thực thi |
| `executorService.submit(task)` | `ExecutorService` | `Future<?>` | Cần theo dõi hoàn tất, lỗi, hủy hoặc chờ |

**Khác biệt chính của bài này:** `execute()` giao một `Runnable` theo hợp đồng đơn giản nhất; nó **không cung cấp kênh lấy kết quả hoàn tất**. Nếu người gọi thật sự cần biết task xong hay lỗi, hãy thiết kế cơ chế báo hiệu hoặc chọn `submit` khi thích hợp. Không nên tạo `Future` trong đầu rồi giả định `execute` đã tự cấp một cái.

## 3. Đường đi của một tác vụ sau execute()

Mô hình khi executor dùng pool:

```text
Luồng gửi (caller)
     |
     | tạo Runnable
     |
     +--> execute(task)
             |
             v
      Executor / TaskExecutor
             |
             +--> Có worker phù hợp -> chạy run()
             |
             +--> Worker đang bận -> xếp hàng (nếu còn chỗ)
             |
             +--> Không nhận thêm -> rejection
             |
             v
       execute() có thể đã trả về ở caller
             |
             ... một thời điểm khác ...
             v
           task.run()
             |
             +--> thực hiện thành công
             |
             +--> ném ngoại lệ
```

Chú ý chữ “có thể”: một executor trực tiếp không có hàng đợi và chạy `run()` trước khi `execute()` trở về. Java không yêu cầu mọi executor tạo thread mới.

Lời gọi `execute` trả về bình thường **chỉ cho biết executor không báo lỗi ngay cho lời gọi đó**. Nó không bảo đảm rằng mọi tác động bên trong task đã thành công. Với một số `RejectedExecutionHandler` như `DiscardPolicy`, thậm chí có thể bỏ task khi quá tải mà không ném ngoại lệ. Vì vậy kết luận “task đã được thực thi” từ việc `execute` trả về chỉ hợp lý khi đã biết rõ chính sách của executor cụ thể.

Không có bảo đảm chung rằng các tác vụ được xử lý đúng thứ tự gửi: điều này phụ thuộc executor, cấu trúc hàng đợi, số worker và lịch chạy. Trong một pool nhiều worker, thứ tự bắt đầu, kết thúc và ghi log có thể khác nhau.

## 4. Ví dụ Java 17 độc lập: trực tiếp và qua worker pool

Ví dụ này chứng minh cùng một lời gọi `execute` có thể chạy trên `main` hoặc trên worker thread. `CountDownLatch` làm thứ tự in ra có thể dự đoán, không cần ngủ để đoán task đã xong.

**Tệp `ExecuteRunnableDemo.java`:**

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ExecuteRunnableDemo {
    public static void main(String[] args)
            throws InterruptedException {

        Executor direct = Runnable::run;

        direct.execute(() -> System.out.println(
                "Trực tiếp: " + Thread.currentThread().getName()));

        ThreadFactory namedThread = task ->
                new Thread(task, "background-worker");

        ExecutorService pool =
                Executors.newSingleThreadExecutor(namedThread);

        CountDownLatch finished = new CountDownLatch(1);

        try {
            pool.execute(() -> {
                try {
                    System.out.println("Trong pool: "
                            + Thread.currentThread().getName());
                } finally {
                    finished.countDown();
                }
            });

            finished.await();
            System.out.println("Caller xác nhận đã hoàn tất");
        } finally {
            pool.shutdown();
        }
    }
}
```

**Biên dịch và chạy (JDK 17+):**

```bash
javac ExecuteRunnableDemo.java
java ExecuteRunnableDemo
```

**Đầu ra xác định:**

```text
Trực tiếp: main
Trong pool: background-worker
Caller xác nhận đã hoàn tất
```

Giải thích:

1. `Executor direct = Runnable::run` tạo executor có triển khai tương đương `command.run()`. Nó chạy **ngay trên main** và trở về khi lambda hoàn tất.
2. `Executors.newSingleThreadExecutor(...)` dùng một worker do factory tạo ra, tên `background-worker`. Code tạo task không cần biết chi tiết pool.
3. `pool.execute(...)` trả về `void`. Bản thân nó không tạo ra giá trị “thành công”.
4. Task gọi `finished.countDown()` trong `finally`; `finished.await()` chỉ cho caller đi tiếp sau tín hiệu đó.
5. `pool.shutdown()` là trách nhiệm của code đã tạo pool. Nó ngừng nhận việc mới; riêng `shutdown()` không phải lệnh chờ hoàn tất.
6. Hai dòng đầu xuất hiện trước dòng cuối: direct chạy trước, còn pool task phải báo `finished` trước khi main in dòng xác nhận.

Trong ví dụ trên, `CountDownLatch` đóng vai trò **kênh hoàn tất được thiết kế thêm**, không phải thứ `execute()` trả về. Nếu ứng dụng không cần chờ, có thể bỏ latch; nhưng khi ấy không được công bố rằng task đã hoàn tất ngay sau `execute`.

**Không nhầm với:**

```java
Runnable task = () -> System.out.println("Chạy trên caller");
task.run();  // Một lời gọi phương thức; không tạo thread.
```

## 5. Tích hợp Spring: cấu hình TaskExecutor có giới hạn

Trong Spring Framework, một lựa chọn phổ biến cho ứng dụng tự quản lý pool là `ThreadPoolTaskExecutor`. Lớp này triển khai `TaskExecutor`, dựa trên `ThreadPoolExecutor` của Java và hỗ trợ cấu hình số worker, hàng đợi, tên thread và xử lý từ chối.

**Tệp `BackgroundExecutorConfig.java` — một class cấu hình Spring, không có `main` độc lập:**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BackgroundExecutorConfig {

    @Bean("backgroundTaskExecutor")
    public ThreadPoolTaskExecutor backgroundTaskExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("background-");

        return executor;
    }
}
```

Phân tích:

- `corePoolSize=2`: khi cần, executor tạo tới hai worker lõi trước khi ưu tiên queue.
- `maxPoolSize=4`: giới hạn số worker tối đa là bốn. Với `ThreadPoolExecutor`, sau core, việc thường được đưa vào queue trước; khi queue không nhận thêm mới cân nhắc tăng worker đến max.
- `queueCapacity=32`: giới hạn tối đa 32 tác vụ đang chờ trong queue của pool, **không phải** 32 tác vụ đang chạy.
- `threadNamePrefix` giúp đọc log và thread dump.
- Bean do Spring quản lý nên việc khởi tạo và đóng executor đi theo lifecycle của `ApplicationContext` theo cấu hình Spring.

Ví dụ dùng `ThreadPoolTaskExecutor` để dạy hợp đồng gửi việc; **các con số 2/4/32 chỉ là giả định**. Trong production cần chọn theo độ trễ, bộ nhớ mỗi tác vụ, tải đến, downstream và chính sách từ chối. Một queue quá lớn có thể làm thời gian chờ và lượng heap giữ lại tăng mạnh.

### 5.1. Dùng TaskExecutor trong một service

**Tệp `EventDispatchService.java` — Spring component, cần dependency Spring và context:**

```java
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class EventDispatchService {
    private final TaskExecutor executor;
    private final AtomicInteger processed = new AtomicInteger();

    public EventDispatchService(
            @Qualifier("backgroundTaskExecutor")
            TaskExecutor executor) {
        this.executor = executor;
    }

    public void dispatch(String eventId) {
        String id = Objects.requireNonNull(eventId, "eventId");

        executor.execute(() -> {
            // Công việc minh họa: ghi nhận một sự kiện đã được xử lý.
            processed.incrementAndGet();
            System.out.println("Đã xử lý sự kiện: " + id);
        });
    }

    public int processedCount() {
        return processed.get();
    }
}
```

Mã này minh họa một bean Spring dùng executor với **constructor injection**. Không cần `@EnableAsync` chỉ để gọi `TaskExecutor.execute(...)` trực tiếp: `@EnableAsync` dùng cho cơ chế xử lý `@Async`.

`dispatch` trả `void` sau lời gọi `execute`. Nếu pool bất đồng bộ, một lời gọi `processedCount()` ngay sau `dispatch` **có thể chưa thấy tác vụ vừa gửi**. `AtomicInteger` làm phép tăng an toàn khi nhiều worker cùng cập nhật, nhưng không buộc task hoàn tất trước khi caller đọc; hai vấn đề **an toàn dữ liệu** và **chờ hoàn tất** là khác nhau.

`System.out.println(...)` chỉ giúp quan sát ví dụ, không phải cơ chế audit đáng tin cậy. Nếu nghiệp vụ cần bảo đảm sự kiện không bị mất khi JVM chết, gửi vào in-memory executor chưa đủ; cần lưu công việc bền vững, xử lý retry/idempotency và một cơ chế tiếp nhận có xác nhận theo yêu cầu.

### 5.2. Lời gọi từ controller có thể trả trước task

Ví dụ controller chỉ nhận yêu cầu gửi việc:

```java
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {
    private final EventDispatchService dispatchService;

    public EventController(EventDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/events/{id}/dispatch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void dispatch(@PathVariable String id) {
        dispatchService.dispatch(id);
    }
}
```

Đây là thành phần **Spring Web**, không phải mã Java SE chạy độc lập. Mã này dùng HTTP `202 Accepted` để biểu đạt rằng thao tác gửi đã được thực hiện ở tầng ứng dụng khi không có lỗi đồng bộ. **202 không chứng minh task nền đã thành công**, không cam kết có lưu trữ bền vững, và cũng không có cách truy vấn trạng thái job trong ví dụ. Đối với API sản xuất cần trạng thái công việc, phải thiết kế ID và kho lưu trạng thái riêng.

Nếu executor từ chối tác vụ và `TaskRejectedException` lan lên từ `dispatch`, request không nên vẫn được trả 202. Cần xử lý ngoại lệ ở tầng web để trả một phản hồi phù hợp, chẳng hạn 503 hoặc 429 theo hợp đồng API. Không nuốt rejection rồi giả vờ đã nhận việc.

## 6. Không trả kết quả không có nghĩa không cần xử lý lỗi

Có hai thời điểm lỗi riêng:

### 6.1. Lỗi khi gửi: task chưa được nhận

`ThreadPoolTaskExecutor` có thể từ chối khi pool đã đóng hoặc đã đạt giới hạn worker và queue. Với chính sách mặc định, Spring báo `TaskRejectedException` theo hợp đồng `TaskExecutor`.

```java
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

// Đoạn đặt trong một phương thức có biến executor và eventId:
try {
    executor.execute(() ->
            System.out.println("Xử lý: " + eventId));
} catch (TaskRejectedException rejected) {
    // Đẩy lỗi lên tầng có quyền trả trạng thái quá tải/retry.
    throw rejected;
}
```

Đoạn này chỉ minh họa **vị trí bắt lỗi đồng bộ**. Việc bắt rồi ném lại là hợp lệ nếu muốn bổ sung log/metrics trước khi lan lỗi; nếu không làm gì khác, không cần khối `try/catch` này. Không nên giả định mọi rejection policy đều ném ngoại lệ: `DiscardPolicy` có thể bỏ tác vụ, còn `CallerRunsPolicy` có thể khiến chính luồng gửi chạy tác vụ khi executor chưa đóng.

### 6.2. Lỗi khi task đã chạy: không có Future để lấy

`Runnable.run()` không khai báo checked exception, nhưng runtime exception vẫn có thể phát sinh trong nội dung:

```java
executor.execute(() -> {
    throw new IllegalStateException("Không xử lý được");
});
```

Nếu `executor` là executor đồng bộ, lỗi có thể ném ngược lại trong cùng lời gọi `execute`. Nếu `executor` là worker pool bất đồng bộ, caller thường đã rời khỏi phương thức gửi; không có `Future.get()` để báo lỗi này cho caller. Với `ThreadPoolExecutor.execute(...)` mặc định, runtime exception không được xử lý có thể làm worker kết thúc bất thường và đi qua `UncaughtExceptionHandler`; cách hiển thị log phụ thuộc thread factory, runtime và cơ chế giám sát.

Không nên viết unit test chỉ bắt exception ngay quanh `execute` rồi nghĩ đã bao phủ lỗi trong một worker bất đồng bộ.

**Mẫu xử lý lỗi ngay trong tác vụ** khi có một kênh giám sát phù hợp:

```java
executor.execute(() -> {
    try {
        processEvent(eventId);
    } catch (RuntimeException failure) {
        reportFailure(eventId, failure);
    }
});
```

Đây là **mảnh mã đặt trong class nghiệp vụ** đã có `processEvent(...)` và `reportFailure(...)`; không phải chương trình độc lập. `reportFailure` phải thực sự xử lý được việc ghi nhận lỗi, ví dụ metrics/log có cấu trúc hoặc kho trạng thái công việc. Nếu tác vụ có side effect nguy hiểm, cần quyết định có retry được không và tránh thực hiện trùng.

**Đặc biệt:** Đừng bắt `Throwable` rồi lặng lẽ bỏ qua mọi lỗi. Những lỗi nghiêm trọng ở mức JVM có thể cần để hệ thống giám sát hoặc xử lý theo chính sách. Với `InterruptedException` trong logic chặn, nên kết thúc tác vụ hoặc truyền tín hiệu phù hợp; khi bắt mà không ném tiếp, thường khôi phục bằng `Thread.currentThread().interrupt()`.

## 7. Java 17: quan sát queue đầy và execute() bị từ chối

Ví dụ sau dùng Java SE để mô tả trường hợp `execute` không chấp nhận thêm tác vụ. Nó có **một worker**, queue dung lượng **một**, và latch giữ worker đang bận trong lúc ta gửi các task còn lại. Vì vậy kết quả in ra không phụ thuộc tốc độ máy.

**Tệp `ExecuteRejectionDemo.java`:**

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ExecuteRejectionDemo {
    public static void main(String[] args)
            throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });

            started.await();

            executor.execute(() -> {});
            System.out.println("Số tác vụ chờ: "
                    + executor.getQueue().size());

            try {
                executor.execute(() -> {});
                System.out.println("Không mong đợi nhận thêm tác vụ");
            } catch (RejectedExecutionException rejected) {
                System.out.println("Tác vụ thứ ba bị từ chối");
            }
        } finally {
            release.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ExecuteRejectionDemo.java
java ExecuteRejectionDemo
```

Đầu ra chính xác:

```text
Số tác vụ chờ: 1
Tác vụ thứ ba bị từ chối
```

Tại sao chắc chắn? `started.await()` đợi task đầu tiên đã chiếm worker. Task đó phải đợi `release`, nên worker chưa lấy task thứ hai khỏi queue. `ArrayBlockingQueue<>(1)` chỉ cho một task chờ. Task thứ ba không thể chạy và không còn chỗ chờ, nên `AbortPolicy` ném `RejectedExecutionException` ngay ở caller.

**Phân biệt exception:** Trong ví dụ Java `ThreadPoolExecutor` trực tiếp, kiểu bị bắt là `RejectedExecutionException`. Trong `ThreadPoolTaskExecutor` của Spring, hợp đồng báo từ chối thường là `TaskRejectedException` (một dạng lỗi từ chối do Spring sử dụng). Cách chuyển đổi và xử lý phụ thuộc implementation/policy được chọn; không nên gộp mọi lỗi của task với lỗi từ chối khi gửi.

Ở môi trường thật, `getQueue().size()` thay đổi liên tục; dùng nó để quan sát tải, không xem nó là bảo đảm tuyệt đối cho quyết định nghiệp vụ ở luồng khác. Mẫu trên giữ worker có kiểm soát để minh họa tình huống hoàn toàn xác định.

## 8. Khi nào không nên dùng execute() một mình?

`execute` thích hợp nếu code gọi thực sự **không cần nhận kết quả tác vụ**. Nhưng “không trả dữ liệu” khác với “không cần biết đã hoàn tất”. Có ba loại nhu cầu:

| Nhu cầu | Cách phù hợp | Lưu ý |
|---|---|---|
| Gửi một tác vụ độc lập, không cần phản hồi giá trị | `TaskExecutor.execute(Runnable)` | Thiết kế xử lý lỗi, quá tải và vòng đời riêng |
| Tác vụ không trả giá trị nhưng caller phải biết khi nào xong/lỗi | `ExecutorService.submit(Runnable)` hoặc `AsyncTaskExecutor.submit(Runnable)` để nhận `Future<?>` | `get()` có thể chặn; phải xử lý `ExecutionException`/hủy |
| Tác vụ tạo ra kết quả thuộc kiểu `T` | `Callable<T>` + `submit`, hoặc API bất đồng bộ phù hợp | Có kết quả và đường xử lý ngoại lệ |
| Phối hợp nhiều bước bất đồng bộ và kết quả | `CompletableFuture` hoặc cơ chế orchestration | Cần cấu hình executor cho các bước chạy bất đồng bộ |
| Công việc phải sống qua crash/restart | Hàng đợi bền vững, cơ sở dữ liệu, cơ chế job chuyên dụng | Pool trong heap không tự bảo đảm giao việc bền vững |

Với `submit(Runnable)`, `Future.get()` trả `null` nếu tác vụ hoàn thành bình thường; lỗi tác vụ thường được bọc vào `ExecutionException`. `execute(Runnable)` không cung cấp `Future` tương ứng. Đây là **phân biệt API**, không có nghĩa `submit` sẽ tự bảo đảm task hoàn tất khi ứng dụng dừng hoặc hệ thống lỗi.

Nếu chỉ cần đếm task thành công hoặc ghi status, có thể đặt kênh quan sát trong thân `Runnable` và dùng cấu trúc an toàn luồng. Nhưng khi caller cần tính đúng đắn dựa trên trạng thái đó, vẫn phải phối hợp thời điểm đọc qua latch, Future, callback hoặc một điều kiện đồng bộ thích hợp.

## 9. Quan sát hoàn tất mà vẫn dùng execute()

Đôi khi kiến trúc yêu cầu chỉ phụ thuộc `Executor`, nhưng một bài kiểm thử hoặc bước điều phối vẫn cần biết lúc nào task kết thúc. Có thể truyền riêng `CountDownLatch` hoặc một callback để phát tín hiệu. Ví dụ sau cũng chứng minh `execute` không trả kết quả.

**Tệp `ExecuteCompletionDemo.java`:**

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecuteCompletionDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ExecutorService owner = Executors.newFixedThreadPool(2);
        Executor executor = owner;
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(3);

        try {
            for (int i = 0; i < 3; i++) {
                executor.execute(() -> {
                    try {
                        completed.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            done.await();
            System.out.println("Đã hoàn tất: " + completed.get());
        } finally {
            owner.shutdown();
        }
    }
}
```

Lệnh chạy:

```bash
javac ExecuteCompletionDemo.java
java ExecuteCompletionDemo
```

Kết quả:

```text
Đã hoàn tất: 3
```

Mặc dù pool có hai worker và thứ tự ba tác vụ không xác định, `AtomicInteger` bảo vệ phép tăng khỏi mất cập nhật và `CountDownLatch` chỉ mở cho main khi cả ba lần `countDown()` đã xảy ra. Thứ tự in dòng cuối vì thế xác định.

**Cảnh báo về ý nghĩa số liệu:** `completed` ở đây thực chất là **số task đã thực hiện phép tăng**, không phải một cơ chế bảo đảm tác động nghiệp vụ bên ngoài thành công. Đặt tên counter theo mốc mà chương trình thực sự kiểm tra (submitted, started, succeeded, failed); không đánh đồng task đã nhận với task đã hoàn tất thành công.

Nếu cần phản hồi lỗi khi chờ, latch đơn thuần chưa đủ; phải thêm một kênh chứa lỗi an toàn luồng hoặc chuyển sang `Future`/`CompletableFuture`. Nếu có thể xảy ra mất task, phải thiết kế timeout cho việc chờ thay vì chờ vô hạn.

## 10. Đồng thời không tự làm bean singleton an toàn luồng

Spring bean singleton thường được nhiều request gọi đồng thời. Khi một bean gửi nhiều `Runnable` vào pool, các tác vụ có thể cùng đọc hoặc ghi các trường của bean đó.

**Ví dụ nguy hiểm:**

```java
private int count;

void dispatch(TaskExecutor executor) {
    executor.execute(() -> count++);
}
```

Đây là **mảnh code minh họa lỗi** đặt trong một class. `count++` gồm thao tác đọc, tính và ghi; nhiều worker có thể cùng thấy giá trị cũ và làm mất cập nhật. Việc dùng `execute` không tạo đồng bộ tự động.

Một phương án khi chỉ cần đếm độc lập:

```java
private final AtomicInteger count = new AtomicInteger();

void dispatch(TaskExecutor executor) {
    executor.execute(count::incrementAndGet);
}
```

Đây là đoạn mã minh họa trong một class với import `java.util.concurrent.atomic.AtomicInteger` và `org.springframework.core.task.TaskExecutor`. Nếu cần bảo vệ nhiều trường có bất biến chung, một kiểu atomic riêng lẻ có thể chưa đủ; cân nhắc khóa ngắn, snapshot bất biến hoặc thay đổi cấu trúc dữ liệu. Không nên giữ khóa của bean trong thời gian gọi I/O chậm chỉ để thực hiện một cập nhật nhanh.

**Cũng không mặc định ThreadLocal được truyền theo.** Với thread pool, worker có thể được tái sử dụng giữa các task. Các giá trị `ThreadLocal`, ngữ cảnh request, MDC hoặc `SecurityContextHolder` từ luồng gửi không tự động được sao chép chính xác cho mọi executor. Nếu cần correlation ID hay tenant ID, truyền tham số bất biến hoặc cấu hình cơ chế truyền ngữ cảnh phù hợp và kiểm tra việc dọn context sau mỗi task.

## 11. execute() và ranh giới transaction trong Spring

Một task chạy trên thread khác **không tự tiếp tục transaction** của luồng gửi. Ví dụ:

```text
Thread request:
  bắt đầu @Transactional
  ghi dữ liệu chưa commit
  execute(runnable)
  kết thúc transaction

Worker:
  có thể chạy trước hoặc sau commit
  không tự tham gia transaction của caller
```

Nếu task đọc một bản ghi vừa được sửa nhưng chưa commit, kết quả phụ thuộc isolation và thời điểm thực thi; không nên giả định nó sẽ thấy bản ghi mới. Với hành động chỉ được thực hiện sau khi transaction thành công, cân nhắc sự kiện xử lý sau commit, outbox hoặc cơ chế chuyển việc có tính bền vững. Nếu task tự cần transaction, mở một ranh giới transaction riêng qua thành phần Spring được quản lý phù hợp.

**Không dùng `execute()` như cách “chuyển” an toàn một `EntityManager`, JDBC connection hoặc đối tượng request còn sống sang thread khác.** Những tài nguyên này có vòng đời, chính sách thread-safety và quyền sở hữu riêng.

## 12. Quản lý vòng đời và khả năng chịu tải

Khi tự tạo `ExecutorService` bằng `Executors` trong Java SE, code sở hữu pool phải chịu trách nhiệm `shutdown()` và chờ kết thúc theo chính sách. Trong Spring, nếu `ThreadPoolTaskExecutor` được đăng ký làm bean thông thường, Spring quản lý khởi tạo và đóng theo `ApplicationContext`.

Một số điểm thực tế:

- Tránh `new Thread(...)` hoặc `Executors.newFixedThreadPool(...)` trong mỗi request; tạo quá nhiều thread/pool và khó dọn dẹp.
- Tránh queue gần như vô hạn nếu task có thể tăng nhanh hơn khả năng xử lý; nó có thể giữ rất nhiều đối tượng trong heap.
- Cấu hình giới hạn số worker, queue và chính sách rejection đồng bộ với yêu cầu độ trễ và bộ nhớ.
- Chọn chính sách khi application đóng: chờ tác vụ đã nhận, từ chối task mới, hoặc yêu cầu hủy theo nhu cầu. Các thuộc tính lifecycle của Spring phụ thuộc phiên bản/cấu hình; kiểm tra tài liệu phiên bản đang dùng.
- Không tự gọi `shutdown()` trên một executor chung do Spring hoặc application server quản lý nếu service không phải owner.
- Không nhầm `TaskExecutor.execute(...)` với hệ thống job bền vững: JVM crash/redeploy có thể làm mất tác vụ chưa hoàn tất.

### 12.1. Nếu executor từ chối, caller nên làm gì?

Một API đồng bộ có thể ánh xạ rejection sang lỗi quá tải có chủ đích; một consumer có thể giảm tốc độ nhận; một tác vụ quan trọng có thể được đưa vào hàng đợi bền vững nếu thiết kế cho phép. Không có một chính sách phù hợp cho mọi trường hợp.

Với `CallerRunsPolicy` của `ThreadPoolExecutor`, nếu pool chưa đóng, task bị từ chối theo đường worker/queue có thể chạy ngay trên **thread gửi**. Điều này tạo áp lực ngược nhưng có thể làm request chậm hoặc khiến exception của task lan lên caller. Với `DiscardPolicy`, công việc có thể bị bỏ im lặng. Vì vậy phải xác minh chính sách thực tế trước khi diễn giải “`execute` trả bình thường”.

## 13. Thiết kế xử lý lỗi và khả năng quan sát cho task không có kết quả

Khi task không trả một giá trị, nên xác định rõ **bằng chứng nào đánh dấu thành công**. Tối thiểu, có thể quan sát ba thời điểm:

1. **Đã nhận để xử lý (`accepted`):** `execute` không báo lỗi trong chính sách nhận việc đã chọn.
2. **Đã bắt đầu (`started`):** task thật sự được worker/caller gọi `run()`.
3. **Đã hoàn tất thành công (`succeeded`) hoặc thất bại (`failed`):** logic nghiệp vụ đã đạt kết quả hoặc có ngoại lệ.

Các chỉ số nên tách biệt:

| Chỉ số | Diễn giải |
|---|---|
| `tasks.submitted` | Số lần caller cố gửi việc |
| `tasks.rejected` | Số việc bị từ chối theo policy có thể quan sát |
| `tasks.started` | Số task bước vào `run()` |
| `tasks.succeeded` | Số task hoàn tất nghiệp vụ thành công |
| `tasks.failed` | Số task lỗi |
| `queue.depth` | Số việc còn đợi trong queue |
| `executor.active` | Số worker hiện bận |
| `task.queue.wait` | Thời gian từ tiếp nhận đến bắt đầu |
| `task.run.duration` | Thời gian task thực thi |

Trong Spring, `ThreadPoolTaskExecutor` có các phương thức quan sát như `getActiveCount()`, `getPoolSize()`, `getQueueSize()` theo phiên bản Spring phù hợp. Các giá trị là số liệu tại thời điểm quan sát, không bảo đảm một ảnh chụp nguyên tử của cả hệ thống.

Nếu dùng `TaskDecorator` để ghi context hoặc đo thời gian, phải nhớ decorator có thể bao quanh một `Runnable` đã được framework bọc; cách lỗi xuất hiện khác nhau với `execute` và `submit`. Cần kiểm chứng trong đúng phiên bản Spring và chọn một nơi đáng tin để ghi lỗi của task.

### 13.1. Ghi nhận dữ liệu tối thiểu, tránh giữ cả request

Lambda có thể vô tình capture nguyên một request object, input stream, session hoặc đồ thị dữ liệu lớn; nếu queue còn nhiều việc chờ, chúng vẫn bị giữ trong heap. Thay vào đó, ưu tiên dữ liệu bất biến đủ dùng như ID, checksum hoặc bản sao nhỏ của các tham số.

Đối với task gửi email/thanh toán/ghi cơ sở dữ liệu, vấn đề quan trọng không chỉ là memory: cần xác định idempotency, thời điểm xác nhận cho caller, retry có giới hạn và quyền thao tác của worker. Một lệnh `execute` không tự giải quyết được các điều kiện đó.

## 14. Cách kiểm thử execute() cho đúng

Vì `execute` trả `void`, một unit test viết như sau thường sai mục đích:

```java
service.dispatch("A-1");
// Sai kỳ vọng nếu dùng pool: công việc có thể chưa chạy.
assertEquals(1, service.processedCount());
```

Đây là **mảnh kiểm thử minh họa**, không phải class độc lập; `assertEquals` thuộc framework kiểm thử. Với pool thật, kết quả phụ thuộc lịch chạy.

Có hai cách kiểm thử tùy mục tiêu:

**Kiểm thử logic tác vụ đồng bộ:** inject `SyncTaskExecutor` của Spring. Nó gọi task ngay trên luồng kiểm thử, giúp kết quả ổn định. Đây là lựa chọn tốt khi chỉ cần biết service đã tạo và gửi đúng logic, không cần kiểm tra thread thật.

```java
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;

// Đặt trong mã kiểm thử/tạo đối tượng service phù hợp.
TaskExecutor direct = new SyncTaskExecutor();
direct.execute(() -> System.out.println("Kiểm thử đồng bộ"));
```

**Kiểm thử tích hợp thực thi bất đồng bộ:** dùng latch, Future (nếu API cung cấp), callback hoặc một điều kiện có timeout để chờ mốc hoàn tất; không dùng `Thread.sleep(100)` rồi hy vọng worker đã xử lý xong.

Kiểm thử nên bao phủ tối thiểu: task thành công, task lỗi, executor từ chối, task bị hủy hoặc ứng dụng đóng khi còn việc, và tính đúng đắn của trạng thái chia sẻ. Đặc biệt, hãy kiểm tra hành vi API khi `dispatch` trả về trước khi task hoàn tất.

## 15. Những nhầm lẫn phổ biến

| Nhầm lẫn | Thực tế | Cách sửa tư duy |
|---|---|---|
| `execute()` luôn tạo thread mới | Executor có thể chạy trực tiếp, trong pool hoặc theo chiến lược khác | Kiểm tra implementation |
| `execute()` trả về là task đã hoàn tất | Với async pool, task có thể còn chờ hoặc đang chạy | Phân biệt accepted/started/succeeded |
| `Runnable` không thể thất bại vì `run()` trả `void` | Runtime exception vẫn có thể phát sinh | Thiết kế quan sát và xử lý lỗi |
| Bắt exception quanh `execute` là đủ | Không bắt được mọi lỗi xảy ra sau trên worker | Xử lý bên trong task hoặc qua kênh giám sát |
| `execute()` tự cấp `Future` | Hợp đồng trả `void` | Dùng `submit` nếu cần Future |
| Cần `@EnableAsync` để dùng `TaskExecutor.execute` | `execute` là lời gọi trực tiếp; `@EnableAsync` phục vụ `@Async` | Chọn cơ chế đúng mục đích |
| Gửi task bằng pool thì trường singleton an toàn luồng | Nhiều worker có thể sửa cùng biến | Atomic, lock ngắn, dữ liệu bất biến |
| `ThreadLocal`/transaction tự theo task | Context thường gắn với luồng; chuyển thread không tự sao chép | Truyền dữ liệu cần thiết, tạo transaction riêng |
| Bỏ qua `TaskRejectedException` | Có thể báo nhận việc sai hoặc mất task | Xử lý quá tải ở ranh giới người gọi |
| Queue lớn đảm bảo không mất việc | Queue trong heap không bền vững; có thể OOM | Giới hạn queue, backpressure, lưu bền vững nếu cần |
| Đặt `shutdown()` sau từng `execute` trên bean chung | Pool bị đóng, các request khác không thể gửi việc | Để owner quản lý lifecycle |
| Dùng `Thread.sleep` để kiểm thử xong việc | Thời gian chạy thay đổi theo máy/tải | Latch/Future/điều kiện có timeout |

## 16. Bài tập thực hành

### Bài 1 — Cùng task, hai executor

Viết một `Runnable` in tên thread. Gửi bằng `Runnable::run` và bằng `Executors.newSingleThreadExecutor(...)`. Giải thích tại sao kết quả có thể khác mặc dù chỗ gọi đều là `execute`.

**Định hướng:** Executor trực tiếp thực thi `run()` trong caller, còn pool có worker riêng; contract `execute` không quyết định tên/loại thread.

### Bài 2 — Không có Future

Cho lời gọi `executor.execute(task)`. Làm thế nào để caller biết task đã hoàn tất nếu không thay đổi API executor? Vì sao chỉ dùng `execute` và kiểm tra một biến `boolean` thường là chưa đủ?

**Định hướng:** Dùng latch/callback/đối tượng trạng thái an toàn luồng với quy tắc đồng bộ phù hợp. Biến boolean thường có thể gặp vấn đề visibility và timing; ngay cả `volatile` cũng chưa tự cung cấp chờ hoặc xử lý lỗi.

### Bài 3 — Phân biệt hai loại lỗi

Pool đã đầy và ném `TaskRejectedException` khi gửi A. Task B đã được nhận nhưng ném `IllegalStateException` sau khi chạy trên worker. Hai lỗi xuất hiện ở đâu? Vì sao một `try/catch` quanh `execute(B)` chưa chắc bắt được lỗi B?

**Định hướng:** Rejection xảy ra đồng bộ ở caller; lỗi runtime của B ở luồng thực thi. Không có `Future` từ `execute` để quan sát B tự động.

### Bài 4 — Thử nghiệm rejection Java 17

Chạy `ExecuteRejectionDemo.java`. Giữ số worker là 1, tăng queue từ 1 lên 2 rồi gửi thêm một task. Khi worker vẫn chờ latch, task thứ mấy bị từ chối?

**Định hướng:** Một task đang chạy, hai task được xếp hàng; task thứ tư bị từ chối nếu `AbortPolicy` đang dùng.

### Bài 5 — Chọn đúng API

Một service A gửi cập nhật dữ liệu không cần trả giá trị cho caller; service B cần nhận `Integer` từ tính toán; service C không cần giá trị nhưng bắt buộc biết công việc đã thành công trước khi commit một bước khác. Chọn `execute` hay `submit` cho từng trường hợp và giải thích.

**Định hướng:** A có thể dùng `execute` với cơ chế lỗi phù hợp; B cần `Callable<Integer>` và API trả kết quả; C cần kênh hoàn tất/lỗi như `Future` hoặc thiết kế orchestration rõ ràng, không chỉ `execute`.

### Bài 6 — Tình huống Spring thực tế

Một controller trả HTTP 202 ngay sau `eventDispatchService.dispatch(id)`. Pool vừa nhận task nhưng process chết trước khi worker thực hiện. Client có cơ sở nào để khẳng định sự kiện đã được xử lý hoặc sẽ chắc chắn được xử lý?

**Định hướng:** Không. 202 trong ví dụ chỉ xác nhận thao tác gửi ở thời điểm đó; cần lưu bền vững, ID công việc và cơ chế theo dõi nếu muốn cam kết khác.

## 17. Quy trình sử dụng trong dự án

Khi có nhu cầu gửi một `Runnable` không trả kết quả, kiểm tra theo thứ tự:

1. **Mục đích:** Có thật sự không cần giá trị, kết quả thành công hay phản hồi lỗi cho caller? Nếu cần, xem lại lựa chọn `submit` hoặc API khác.
2. **Executor:** Xác định rõ bean `TaskExecutor` được inject; biết nó có chạy đồng bộ hay qua pool.
3. **Tài nguyên:** Đặt giới hạn worker/queue phù hợp; xử lý trường hợp quá tải và đóng ứng dụng.
4. **Tác vụ:** Viết logic có trách nhiệm, không capture dữ liệu request/transaction không phù hợp; bảo vệ trạng thái chung.
5. **Lỗi:** Phân biệt lỗi gửi và lỗi khi task chạy; có logging/metrics/retry đúng nghiệp vụ.
6. **Quan sát:** Nếu task quan trọng, xác định mốc accepted/started/succeeded, cách kiểm tra quá hạn và tác vụ mất.
7. **Kiểm thử:** Dùng direct executor để kiểm thử logic, dùng latch/điều kiện với timeout cho integration test và xác minh rejection.

## 18. Điều cần nhớ

- `Runnable` mô tả **việc làm** và `run()` trả `void`; `execute(Runnable)` là cách **gửi việc** tới `Executor` hoặc `TaskExecutor`.
- `execute()` **không trả `Future`, không truyền kết quả và không bảo đảm task đã hoàn tất**.
- **Đồng bộ hay bất đồng bộ phụ thuộc executor**: `SyncTaskExecutor` chạy ngay, `ThreadPoolTaskExecutor` dùng pool theo cấu hình.
- **Bắt lỗi đúng thời điểm**: rejection xảy ra ở đường gửi; lỗi trong worker cần một kênh ghi nhận riêng.
- **Trạng thái bean và tài nguyên vẫn phải được quản lý**: thread safety, giới hạn pool/queue, context, transaction, shutdown và tính bền vững của công việc.
- Khi cần biết chính xác đã xong hay lỗi, thiết kế tín hiệu hoàn tất phù hợp hoặc dùng API như `submit`.

### Tài liệu API chính thức

- [Java SE 17 — Runnable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runnable.html)
- [Java SE 17 — Executor.execute](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Spring Framework — TaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/TaskExecutor.html)
- [Spring Framework — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-submission-002">Gửi Runnable hoặc Callable qua submit()</a>

<details>
<summary>Xem chi tiết</summary>

# Gửi Runnable hoặc Callable qua submit() trong Spring Framework

## 1. Vì sao cần submit() thay vì chỉ gọi execute()?

Một ứng dụng thường có những công việc như tạo báo cáo, tính toán điểm, xử lý dữ liệu nhập hoặc gửi thông báo. Thành phần nghiệp vụ muốn **gửi một tác vụ để executor thực thi**, đồng thời có thể cần biết khi nào tác vụ hoàn tất, nhận giá trị tính được hoặc biết tác vụ đã thất bại.

Khi chỉ muốn yêu cầu thực thi, hợp đồng `execute(Runnable)` đủ dùng. Nhưng khi cần **một handle để theo dõi kết quả**, các phương thức `submit(...)` tiện hơn vì trả về `Future`.

Trong Java và Spring, hai dạng tác vụ cơ bản là:

- **`Runnable`**: hành động có phương thức `run()`, không trả về giá trị. Phù hợp cho tác vụ tạo side effect như ghi audit, cập nhật chỉ số, gửi sự kiện.
- **`Callable<T>`**: tác vụ có phương thức `call()` trả về một `T`, đồng thời có thể khai báo ném checked exception. Phù hợp khi caller cần kết quả xử lý.

Ví dụ:

```java
Runnable audit = () -> System.out.println("Audit stored");

Callable<Integer> score = () -> 10 * 2 + 1;
```

Hai dòng này chỉ **tạo task**, chưa gửi chúng vào executor. Đây là fragment; cần import `java.util.concurrent.Callable` và có thể đặt trong method Java bình thường.

Gửi task thường có dòng đời:

```text
caller
  | submit(Runnable / Callable)
  v
executor -- accept / queue / schedule --> worker thread --> task.run()/call()
  |
  +-- return Future immediately (for an async executor)
          |
          +-- get() / get(timeout) -> value or exception
          +-- isDone(), isCancelled(), cancel(...)
```

Mô hình trên áp dụng cho executor thực sự chạy bất đồng bộ. Một implementation của `AsyncTaskExecutor` có thể sử dụng chính sách thực thi cụ thể khác, nên hãy kiểm tra bean đang dùng; **submit không tự bảo đảm task chạy trên một thread mới**.

## 2. Phải dùng đúng interface: TaskExecutor, AsyncTaskExecutor và ExecutorService

Trong Spring Framework 6.x, những kiểu thường gặp có cấp độ khả năng khác nhau:

| Kiểu | API chính | Có submit(Runnable/Callable)? | Vai trò |
| --- | --- | --- | --- |
| `java.util.concurrent.Executor` | `execute(Runnable)` | Không | Hợp đồng tối thiểu của JDK |
| `org.springframework.core.task.TaskExecutor` | `execute(Runnable)` | Không | Interface executor cơ bản trong Spring, mở rộng `Executor` |
| `org.springframework.core.task.AsyncTaskExecutor` | `execute(...)` và `submit(...)` | Có | Interface Spring khi caller muốn `Future` |
| `java.util.concurrent.ExecutorService` | `execute`, `submit`, `shutdown`... | Có | Executor của JDK có các API quản lý kết quả và vòng đời |
| `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor` | Hỗ trợ `AsyncTaskExecutor`, chạy trên pool | Có | Bean executor của Spring được cấu hình và quản lý vòng đời |

**Lỗi biên dịch rất dễ gặp:**

```java
TaskExecutor executor = /* Spring-managed executor */;
// executor.submit(() -> 42); // KHÔNG biên dịch: TaskExecutor không khai báo submit().
executor.execute(() -> System.out.println("Run without Future"));
```

Nếu service thực sự cần `Future`, tiêm interface `AsyncTaskExecutor` (hoặc `ExecutorService` nếu thiết kế phụ thuộc JDK service interface). Không ép kiểu một `TaskExecutor` bất kỳ thành `ThreadPoolTaskExecutor` chỉ để gọi `submit`; việc đó ràng buộc nghiệp vụ vào implementation và có thể gây `ClassCastException`.

Khi dùng Spring-managed `ThreadPoolTaskExecutor`, bạn có thể tiêm nó bằng **kiểu interface `AsyncTaskExecutor`**. Điều này cho phép đổi executor tương thích trong cấu hình mà không sửa business service.

## 3. Phân biệt submit(Runnable) và submit(Callable<T>)

### 3.1. submit(Runnable): theo dõi việc hoàn thành

Chữ ký:

```java
Future<?> submit(Runnable task);
```

Dùng khi task **không tạo một giá trị trả về trực tiếp**:

```java
Runnable task = () -> counter.incrementAndGet();
Future<?> future = asyncExecutor.submit(task);
Object result = future.get(); // null nếu task kết thúc bình thường.
```

Đây là fragment, yêu cầu các biến `counter` và `asyncExecutor` đã được khai báo và xử lý checked exceptions từ `get()`.

Những điều cần nắm:

- `Future.get()` đợi đến khi `Runnable` hoàn tất; **giá trị kết quả là `null`** nếu task thành công.
- `null` không chứng minh tác vụ “không làm gì”: side effect có thể đã xảy ra.
- Nếu `Runnable.run()` ném exception và implementation lưu lỗi qua `Future`, `get()` sẽ ném `ExecutionException` với nguyên nhân gốc ở `getCause()`.
- `submit` có thể từ chối task ngay tại thời điểm gửi, ví dụ `TaskRejectedException` từ `ThreadPoolTaskExecutor` khi pool và queue đã quá tải.

### 3.2. submit(Callable<T>): nhận giá trị hoặc lỗi

Chữ ký:

```java
<T> Future<T> submit(Callable<T> task);
```

Dùng khi task **có kết quả**:

```java
Callable<Integer> task = () -> 10 * 2 + 1;
Future<Integer> future = asyncExecutor.submit(task);
Integer value = future.get(); // 21 khi task thành công.
```

`Callable.call()` có thể ném checked exceptions, trong khi `Runnable.run()` không khai báo `throws Exception`. Dù vậy, cả runtime exception của hai loại task đều cần được quan sát qua `Future` và cách xử lý của executor.

### 3.3. Bảng kết quả cần nhớ

| Cách gọi | Kiểu trả về của submit | Giá trị từ Future.get() khi thành công | Tác vụ lỗi |
| --- | --- | --- | --- |
| `submit(Runnable)` | `Future<?>` | `null` | Thông thường `ExecutionException` chứa cause |
| `submit(Callable<String>)` | `Future<String>` | Chuỗi do `call()` trả về | `ExecutionException` |
| `submit(Callable<Integer>)` | `Future<Integer>` | Số nguyên do `call()` trả về | `ExecutionException` |

Trong Spring Framework 6.1+, `AsyncTaskExecutor` có default implementation cho hai overload `submit`, tạo `FutureTask` và ủy quyền sang `execute`. Implementation như `ThreadPoolTaskExecutor` có thể ghi đè phương thức để dùng trực tiếp executor service nội bộ. **Hợp đồng kết quả** giống nhau, còn chi tiết thực hiện phụ thuộc implementation.

### 3.4. Overload và lambda: đừng để ý nghĩa mơ hồ

Một lambda dạng biểu thức method invocation có thể phù hợp cả dạng value-producing và statement. Khi đọc code, khai báo rõ kiểu có thể giúp tránh nhầm overload:

```java
Runnable update = () -> counter.incrementAndGet();
Callable<Integer> calculate = () -> 10 * 2 + 1;

Future<?> updated = asyncExecutor.submit(update);
Future<Integer> calculated = asyncExecutor.submit(calculate);
```

Các biến `counter` và `asyncExecutor` là dependency trong fragment này. Kiểu `Runnable` hoặc `Callable<T>` giúp người đọc hiểu tác giả có mong đợi giá trị trả về hay không.

## 4. Future đại diện cho điều gì?

`Future<V>` là **handle** của một phép tính bất đồng bộ. Nó không tự lưu một bản sao đầy đủ nghiệp vụ, không cam kết durability qua restart và không phải công cụ quan sát mọi task trong hệ thống.

Các thao tác cốt lõi:

- `get()`: chờ vô hạn cho đến khi task hoàn thành, bị hủy hoặc thất bại. Nếu caller bị interrupt trong lúc đợi, có thể ném `InterruptedException`.
- `get(timeout, unit)`: chờ tối đa một khoảng thời gian; nếu chưa hoàn tất, ném `TimeoutException`. Nó **không tự hủy** task đang chạy.
- `isDone()`: true nếu đã hoàn tất bình thường, lỗi hoặc bị hủy; **không đồng nghĩa thành công**.
- `isCancelled()`: cho biết `Future` đã được hủy qua cơ chế cancellation.
- `cancel(true)`: thử hủy và cho phép interrupt worker nếu task đang chạy. Trả true không bảo đảm mã task dừng ngay lập tức.
- `cancel(false)`: thử hủy nhưng không yêu cầu interrupt task đã chạy. Với task đang chờ trong queue, có thể ngăn nó thực thi, tùy implementation.

Ví dụ khung xử lý timeout:

```java
try {
    String value = future.get(2, TimeUnit.SECONDS);
    use(value);
} catch (TimeoutException ex) {
    // Task có thể vẫn đang chạy.
    future.cancel(true); // Chỉ khi nghiệp vụ cho phép dừng.
}
```

Đây là **fragment**; `future` là `Future<String>`, `use` là hàm nghiệp vụ, cần import `TimeUnit` và `TimeoutException`, đồng thời xử lý hoặc khai báo `InterruptedException`/`ExecutionException`. Không tự ý gọi `cancel(true)` nếu công việc đã gửi có side effect không được phép bỏ dở.

**Quan hệ bộ nhớ:** trong JDK, các hành động thực hiện trong task xảy ra *happens-before* các hành động của thread khác sau khi thread đó trả về thành công từ `Future.get()` tương ứng. Vì thế test có thể kiểm tra side effect sau `get()` mà không cần ngủ một khoảng thời gian đoán trước.

## 5. Ví dụ Spring Framework 6.2.1 chạy được với Java 21

Chương trình minh họa đầy đủ:

1. Inject `AsyncTaskExecutor` vào một service qua constructor.
2. Gửi `Runnable` có side effect và quan sát kết quả `null`.
3. Gửi `Callable<Integer>` và nhận giá trị.
4. Gửi `Callable` ném lỗi và đọc nguyên nhân qua `ExecutionException`.
5. Dùng `CountDownLatch` để mô phỏng task đang chờ; `get(timeout)` ném timeout nhưng task vẫn tiếp tục sau khi được giải phóng.

### 5.1. Cấu trúc dự án

```text
spring-submit-demo/
├── pom.xml
└── src/main/java/demo/SubmitTaskDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-submit-demo</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>6.2.1</version>
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
        <configuration>
          <mainClass>demo.SubmitTaskDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

**Tệp `src/main/java/demo/SubmitTaskDemo.java`:**

```java
package demo;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class SubmitTaskDemo {
    public static final class JobService {
        private final AsyncTaskExecutor executor;
        private final AtomicInteger auditCount = new AtomicInteger();

        public JobService(AsyncTaskExecutor executor) {
            this.executor = executor;
        }

        public Future<?> recordAudit() {
            Runnable task = () -> auditCount.incrementAndGet();
            return executor.submit(task);
        }

        public int auditCount() {
            return auditCount.get();
        }

        public Future<Integer> calculate(int input) {
            Callable<Integer> task = () -> input * 2 + 1;
            return executor.submit(task);
        }

        public Future<Integer> fail() {
            Callable<Integer> task = () -> {
                throw new IllegalStateException("example error");
            };
            return executor.submit(task);
        }

        public Future<String> waitForRelease(
                CountDownLatch started, CountDownLatch release) {
            Callable<String> task = () -> {
                started.countDown();
                release.await();
                return "released";
            };
            return executor.submit(task);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean("jobExecutor")
        public ThreadPoolTaskExecutor jobExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(8);
            executor.setThreadNamePrefix("job-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(5);
            return executor;
        }

        @Bean
        public JobService jobService(
                @Qualifier("jobExecutor") AsyncTaskExecutor executor) {
            return new JobService(executor);
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            JobService jobs = context.getBean(JobService.class);

            Future<?> audit = jobs.recordAudit();
            System.out.println("Runnable result=" +
                    audit.get(5, TimeUnit.SECONDS));
            System.out.println("Audit count=" + jobs.auditCount());

            Future<Integer> value = jobs.calculate(10);
            System.out.println("Callable result=" +
                    value.get(5, TimeUnit.SECONDS));

            Future<Integer> failure = jobs.fail();
            try {
                failure.get(5, TimeUnit.SECONDS);
                throw new AssertionError("Expected task failure");
            } catch (ExecutionException ex) {
                System.out.println("Failure cause=" +
                        ex.getCause().getClass().getSimpleName());
            }

            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<String> slow = jobs.waitForRelease(started, release);
            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not start");
            }
            try {
                slow.get(20, TimeUnit.MILLISECONDS);
                throw new AssertionError("Expected timeout");
            } catch (TimeoutException expected) {
                System.out.println("Timeout observed=true");
            } finally {
                release.countDown();
            }
            System.out.println("After release=" +
                    slow.get(5, TimeUnit.SECONDS));
        }
    }
}
```

### 5.2. Chạy chương trình

Yêu cầu JDK 21 trở lên và Maven đã cài. Trong thư mục `spring-submit-demo`, chạy:

```bash
mvn -q -DskipTests compile exec:java
```

Output của chương trình đã được biên dịch và chạy kiểm chứng:

```text
Runnable result=null
Audit count=1
Callable result=21
Failure cause=IllegalStateException
Timeout observed=true
After release=released
```

Lệnh dùng `maven.compiler.release=21` để bảo đảm mã nguồn biên dịch theo Java 21; output phía trên là output của chương trình, không tính log phụ có thể xuất hiện tùy phiên bản Maven/JVM.

### 5.3. Vì sao kết quả ổn định?

- `audit.get(...)` chỉ trả về sau khi task `Runnable` kết thúc; vì vậy biến `auditCount` đã tăng trước khi in.
- `calculate(10)` trả về `21` thông qua `Future<Integer>`.
- `fail()` ném `IllegalStateException` bên trong worker; `Future.get(...)` ở caller ném `ExecutionException`, có `getCause()` là exception ban đầu.
- Task `waitForRelease(...)` báo đã bắt đầu bằng `started.countDown()`, sau đó chờ ở `release.await()`; caller chắc chắn không có kết quả trước khi mở latch, nên `get(20, MILLISECONDS)` đi vào nhánh timeout.
- Sau khi `release.countDown()`, task có thể chạy tiếp và `get(...)` trả về `"released"`; timeout ở caller **không tự hủy** công việc.
- Chương trình đóng Spring context bằng `try-with-resources`. Executor là Spring-managed bean nên được quản lý lifecycle. Các task đều đã hoàn tất trước khi context đóng.

Tất cả output được in từ thread chính sau khi quan sát `Future`; không giả định worker chạy theo một thứ tự tự nhiên nào khi nhiều task được gửi cùng lúc.

## 6. Trình tự xử lý kết quả và lỗi

Một `Future<T>` có thể có các trạng thái kết thúc khác nhau.

### 6.1. Thành công

Với `Callable<T>`, `get()` trả về giá trị của `call()`. Với `Runnable`, `get()` trả về `null` nếu không xảy ra lỗi.

### 6.2. Thất bại trong task

Nếu task ném exception, `Future.get()` ném `ExecutionException`; `ExecutionException.getCause()` là lỗi từ task. Với `ThreadPoolTaskExecutor.submit(...)`, một lỗi trong task thường được giữ trong `FutureTask`, nên **không nên trông chờ chỉ log của worker đã đủ**.

Một mẫu xử lý khi chờ có giới hạn:

```java
try {
    Integer result = future.get(3, TimeUnit.SECONDS);
    save(result);
} catch (ExecutionException ex) {
    Throwable cause = ex.getCause();
    // Log/categorize lỗi nghiệp vụ từ worker.
} catch (TimeoutException ex) {
    // Bổ sung chính sách timeout/cancel/retry phù hợp.
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Cho caller quyết định kết thúc/tháo lui tác vụ.
}
```

Đây là **fragment**: `future` thuộc kiểu `Future<Integer>`, `save` là method nghiệp vụ, cần imports `TimeUnit`, `ExecutionException` và `TimeoutException`. Mẫu dùng `Thread.currentThread().interrupt()` khi caller quyết định không ném tiếp `InterruptedException`, để giữ tín hiệu interruption. Nếu hợp đồng method cho phép, có thể truyền `InterruptedException` lên tầng trên.

### 6.3. Timeout khi chờ

`TimeoutException` chỉ nói rằng kết quả chưa sẵn sàng *trong giới hạn chờ của caller*. Tác vụ có thể tiếp tục xử lý thành công sau đó. Khi timeout xảy ra, cần xác định liệu caller có bỏ kết quả, retry, hủy hoặc lưu trạng thái để truy vấn về sau.

Không nên retry tác vụ tạo side effect ngay khi timeout mà không có idempotency key: lần cũ có thể hoàn thành sau đó, gây thực hiện hai lần.

### 6.4. Task bị hủy

Khi `Future.cancel(true)` thành công, `Future.get()` sẽ ném `CancellationException`. Nếu task đã chạy, interrupt chỉ là yêu cầu hợp tác; task có thể chưa dừng ngay. Một số lời gọi I/O, native API hoặc code bỏ qua interrupt có thể tiếp tục làm việc.

Nếu cần hủy công việc, thiết kế task có điểm kiểm tra interruption, đóng tài nguyên đúng cách và ghi trạng thái phù hợp. Không coi `cancel(true)` là rollback giao dịch hoặc undo side effect đã ghi.

### 6.5. Lỗi ngay lúc gửi task

`ThreadPoolTaskExecutor.submit(...)` có thể ném `TaskRejectedException` nếu executor không chấp nhận task vì saturation hoặc shutdown. Trường hợp này xảy ra **ngay tại submit**, trước khi có một `Future` hợp lệ để caller chờ.

Mẫu tách biệt:

```java
try {
    Future<Integer> submitted = asyncExecutor.submit(
            (Callable<Integer>) () -> 21);
    // Lúc này đã có handle để xử lý giá trị/lỗi sau khi chạy.
} catch (TaskRejectedException ex) {
    // Không có task mới được chấp nhận; xử lý overload ở caller.
}
```

Đây là fragment cần import `Callable`, `Future` và `TaskRejectedException`; `asyncExecutor` là dependency đã khai báo. Việc submit thành công có nghĩa task được chấp nhận theo chính sách executor, **không** bảo đảm task đã chạy thành công.

## 7. Phân biệt execute(), submit() và submitCompletable()

Trong Spring Framework 6.x:

| API | Tham số | Giá trị trả về | Khi phù hợp |
| --- | --- | --- | --- |
| `TaskExecutor.execute(Runnable)` | `Runnable` | `void` | Không cần handle kết quả |
| `AsyncTaskExecutor.submit(Runnable)` | `Runnable` | `Future<?>` | Cần biết task hoàn thành, lỗi hoặc hủy |
| `AsyncTaskExecutor.submit(Callable<T>)` | `Callable<T>` | `Future<T>` | Cần kết quả có kiểu `T` |
| `AsyncTaskExecutor.submitCompletable(Runnable)` | `Runnable` | `CompletableFuture<Void>` | Muốn chain callback khi task void hoàn tất |
| `AsyncTaskExecutor.submitCompletable(Callable<T>)` | `Callable<T>` | `CompletableFuture<T>` | Muốn kết hợp kết quả theo CompletionStage |

`CompletableFuture` có các API như `thenApply`, `thenCompose`, `allOf` và `exceptionally`, phù hợp khi cần kết hợp các bước bất đồng bộ mà không chủ động gọi `get()` ở mỗi bước. Tuy nhiên callback có thể chạy trên thread hoàn thành stage hoặc executor khác tùy phương thức; cần nắm rõ execution context.

Dùng `submitCompletable(...)` khi bạn thực sự cần chaining. Với bài toán “gửi một `Runnable` hoặc `Callable` và thu nhận `Future`”, hai overload `submit(...)` là đủ.

**`@Async` không đồng nghĩa `submit(...)`.** `@Async` là interceptor dựa trên Spring proxy cho method phù hợp, được kích hoạt bằng `@EnableAsync` hoặc XML annotation-driven. Gọi `asyncExecutor.submit(...)` trực tiếp không cần `@EnableAsync` và không phụ thuộc self-invocation/proxy của `@Async`.

## 8. Không gọi get() sai chỗ

`submit()` thường quay lại nhanh sau khi executor nhận task, cho phép caller làm việc khác. Nhưng:

```java
Integer value = asyncExecutor.submit(
        (Callable<Integer>) () -> expensiveCalculation()).get();
```

Nếu `expensiveCalculation()` mất nhiều thời gian, cách viết trên làm caller **đứng chờ ngay sau submit**. Nó có thể hợp lý khi cần kết quả ngay, nhưng không tạo được lợi ích bất đồng bộ ở phía caller. Đoạn là fragment, yêu cầu `expensiveCalculation` có sẵn và xử lý checked exceptions.

Một số vấn đề thường gặp:

- **Trong Spring MVC:** giữ servlet request thread chờ `Future.get()` có thể làm cạn request workers dưới tải. Hãy cân nhắc API thiết kế theo trạng thái tác vụ hoặc cơ chế async request nếu nghiệp vụ phù hợp.
- **Trong Reactor event loop:** gọi `get()`/`join()` blocking có thể chặn event-loop thread và làm tăng latency của nhiều request; không thực hiện lời gọi blocking trên event loop.
- **Trong cùng thread pool nhỏ:** Task A giữ worker và gọi `submit(B).get()` vào *chính pool đó*, trong khi tất cả workers đều đang chờ các task con khác. Những task con nằm trong queue không bao giờ được chạy — **thread starvation deadlock**. Cần bỏ việc chờ lồng nhau hoặc tách orchestration khỏi worker pool, thay đổi cấu trúc phụ thuộc, không chỉ tăng pool một cách tùy tiện.
- **Trong HTTP/API với timeout ngắn:** dùng `get(timeout)` nhưng quên timeout ở DB/HTTP client bên trong task có thể để worker tiếp tục chạy rất lâu sau khi caller bỏ cuộc.

### Tình huống deadlock điển hình

Giả sử một pool có `corePoolSize=maxPoolSize=1` và queue vẫn còn chỗ:

```text
Worker-1 thực thi task A
    -> A submit(task B) vào cùng pool
    -> B nằm trong queue
    -> A gọi B.get() và chờ
    -> Worker-1 không thể nhận B vì vẫn đang chạy A
    -> Không có worker khác -> không có tiến triển
```

Đây là deadlock do thiết kế luồng công việc, **không phải lỗi của Future**. Chỉ tăng `queueCapacity` làm B tiếp tục chờ mà không giải quyết dependency vòng.

## 9. Sizing executor và kiểm soát overload

`ThreadPoolTaskExecutor` sử dụng `ThreadPoolExecutor` bên dưới, nên `submit` cũng chịu các giới hạn thực thi như `execute`.

- **`corePoolSize`:** số worker lõi; pool thường mở worker tới ngưỡng này trước khi xếp hàng.
- **`queueCapacity`:** số task có thể chờ trong queue, không tính task đang chạy. Với giá trị dương, Spring thường dùng `LinkedBlockingQueue`; với 0, dùng `SynchronousQueue`, không lưu backlog.
- **`maxPoolSize`:** giới hạn tổng workers; khi queue đã nhận task, pool thường không mở rộng ngoài core cho đến khi queue đầy.
- **Rejection policy:** khi không thể nhận task, mặc định thường báo lỗi từ chối. Có thể cấu hình các policy khác nhưng phải hiểu hậu quả.
- **Shutdown:** executor đang đóng có thể không nhận thêm task; đã submit không có nghĩa có cơ chế đảm bảo task tồn tại qua restart.

Ví dụ trong chương trình hoàn chỉnh: `core=2`, `max=2`, `queue=8` chỉ là một cấu hình học tập có giới hạn. Không dùng nguyên các giá trị này cho mọi ứng dụng; cần đo throughput, queue wait, số task chạy, CPU, thời gian chờ I/O, giới hạn connection pool và rejection.

Hãy phân biệt hai loại thời gian:

1. **Queue wait:** từ lúc submit đến lúc worker bắt đầu thực thi.
2. **Execution time:** từ khi worker bắt đầu đến khi task kết thúc.

Thời gian từ `submit` tới lúc `Future.get()` có kết quả có thể gồm cả hai, cộng thời gian scheduler/caller. Nếu chỉ nhìn `Future.isDone()`, bạn chưa biết nút thắt nằm ở queue hay bên trong task.

### Khi nào cần backpressure?

Nếu tốc độ sinh task cao hơn tốc độ xử lý trong thời gian dài, một queue hữu hạn sẽ đầy. Cấu hình queue vô hạn có thể biến rejection thành latency lớn và memory retention. Hãy áp dụng giới hạn đầu vào, retry có backoff, timeout và chính sách giảm tải rõ ràng. Với nghiệp vụ cần bảo đảm giao việc, cân nhắc durable messaging/outbox; in-memory executor chỉ xử lý trong tiến trình hiện tại.

## 10. Exception visibility và giám sát

Một khác biệt vận hành quan trọng giữa `execute` và `submit` là **đường xuất hiện của exception**.

Với `submit`, task thường được bọc trong `FutureTask`; exception bên trong được giữ để `Future.get()` báo cho caller. Nó có thể **không** được một `UncaughtExceptionHandler` của worker nhìn thấy như một exception thoát thẳng khỏi `run()`.

Hệ quả:

- Nếu gọi `submit` nhưng bỏ hẳn `Future`, lỗi có thể không được ghi nhận ở nơi bạn mong đợi.
- Một `TaskDecorator` trong Spring thường thấy wrapper thực thi; với `submit` nó có thể thấy `FutureTask` thay vì `Runnable/Callable` gốc. Nếu muốn quan sát lỗi, phải thiết kế hook/metrics hoặc đọc kết quả theo hợp đồng.
- `ThreadPoolExecutor.afterExecute(...)` có thể cần kiểm tra `Future` để lấy lỗi từ task đã submit; không mặc định tham số `Throwable` luôn chứa lỗi task.
- Với các task quan trọng, một nơi rõ ràng phải chịu trách nhiệm kiểm tra `Future`, ghi log lỗi, cập nhật trạng thái, hoặc phát sự kiện thất bại.

Không dùng `Future.get()` vô hạn ngay trong instrumentation callback trên worker mà có thể deadlock; chỉ đọc kết quả khi Future chắc chắn đã hoàn tất và xử lý cancellation/exception phù hợp.

## 11. Thread safety, transaction và context của caller

Gửi task sang pool nghĩa là mã task có thể chạy ở **thread khác** và tại **thời điểm khác**.

### 11.1. Thread safety

Nhiều task có thể chạy đồng thời, cùng truy cập một service singleton hoặc dữ liệu chia sẻ. Sử dụng immutable inputs, `AtomicInteger`, đồng bộ hóa có chủ đích hoặc cấu trúc dữ liệu thread-safe khi cần. Ví dụ sử dụng `AtomicInteger` để side effect từ `Runnable` được an toàn khi nhiều threads cập nhật.

### 11.2. Transaction

Gửi một `Callable` bên trong method `@Transactional` không làm transaction hiện tại tự động truyền sang worker thread. Transaction context thường gắn với thread; task cần transaction riêng nếu nó làm DB work. Ngoài ra, nếu submit trước khi transaction của caller commit, task có thể đọc dữ liệu chưa sẵn sàng hoặc side effect bị lệch khi caller rollback.

Với việc phải xảy ra **sau commit**, cân nhắc `@TransactionalEventListener(phase = AFTER_COMMIT)` hoặc outbox pattern theo yêu cầu độ bền. Không để executor làm thay trách nhiệm ordering/consistency của giao dịch.

### 11.3. Security và log context

MDC, `SecurityContext` và `ThreadLocal` của thread gọi không mặc nhiên xuất hiện ở worker. Nếu cần correlation id hoặc principal, truyền những trường cần thiết một cách an toàn hoặc dùng cơ chế context propagation/task decorator được cấu hình đúng. Cần tránh truyền mutable request objects hoặc giữ thông tin bảo mật trong queue lâu hơn cần thiết.

### 11.4. Cancellation và resource cleanup

Task cần tôn trọng interruption khi phù hợp; đóng socket/file/DB resources trong `try-finally` hoặc `try-with-resources`. Với tác vụ đã ghi side effect một phần, cancellation không phục hồi dữ liệu; cần idempotency, transaction hoặc cơ chế bù trừ tùy nghiệp vụ.

## 12. Kiểm thử submit không phụ thuộc vào sleep

Một unit test không nhất thiết phải dùng thread pool thật. Nếu service chỉ cần `AsyncTaskExecutor`, có thể dùng fake executor hoặc implementation đồng bộ phù hợp để kiểm tra việc gửi task. Khi cần kiểm tra concurrency thật, dùng `CountDownLatch` hoặc `Phaser` để kiểm soát các mốc thay vì `Thread.sleep(100)` và hy vọng worker đã chạy.

Trong ví dụ chương trình, hai latch có nhiệm vụ:

- `started` bảo đảm worker đã đến điểm chờ.
- `release` bảo đảm caller kiểm thử nhánh timeout trước khi task được giải phóng.

Mẫu kiểm thử ngắn, yêu cầu một `AsyncTaskExecutor executor` có sẵn:

```java
CountDownLatch started = new CountDownLatch(1);
CountDownLatch release = new CountDownLatch(1);

Future<String> task = executor.submit(() -> {
    started.countDown();
    release.await();
    return "done";
});

try {
    if (!started.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("Worker did not start");
    }

    try {
        task.get(20, TimeUnit.MILLISECONDS);
        throw new AssertionError("Expected timeout");
    } catch (TimeoutException expected) {
        // Đúng: task vẫn chờ release.
    }
} finally {
    release.countDown();
}

if (!"done".equals(task.get(5, TimeUnit.SECONDS))) {
    throw new AssertionError("Unexpected result");
}
```

Đây là **fragment** cần imports `CountDownLatch`, `Future`, `TimeUnit`, `TimeoutException` và xử lý checked exceptions. Task được thả trong `finally` để tránh để worker bị kẹt nếu assert thất bại.

Unit test cho một `Callable<Integer>` thuần không cần Spring context; integration test mới cần kiểm tra bean wiring, pool sizing, rejection, lifecycle và actual threading.

## 13. Các lỗi phổ biến khi dùng submit()

| Sai lầm | Vì sao sai | Hướng sửa |
| --- | --- | --- |
| Gọi `submit` qua biến `TaskExecutor` | Interface không khai báo `submit` | Tiêm `AsyncTaskExecutor` khi cần `Future` |
| Cho rằng `submit(Runnable).get()` trả giá trị từ lambda | `Runnable.run()` là `void` | Dùng `Callable<T>` để có `T` |
| Submit xong bỏ `Future` nhưng muốn biết task lỗi | Lỗi có thể nằm trong Future mà không có consumer | Quan sát `get` hoặc error reporting phù hợp |
| Gọi `get()` ngay trong servlet/event loop | Caller bị chặn, giảm throughput | Tránh blocking ở đường nhạy latency |
| Dùng `get(timeout)` rồi nghĩ task bị hủy | Timeout chỉ áp dụng cho caller | Quyết định cancellation/cleanup riêng |
| Gọi `cancel(true)` rồi nghĩ chắc chắn đã rollback | Interrupt là tín hiệu; task có thể đã ghi side effect | Thiết kế idempotency, transaction, cleanup |
| Bỏ qua `ExecutionException.getCause()` | Mất nguyên nhân lỗi thật | Phân loại/ghi log cause đúng chỗ |
| Đếm `isDone()==true` là success | Trạng thái done bao gồm lỗi và cancel | Xử lý `get`/`isCancelled` để biết kết quả |
| Submit task con rồi `get()` trong cùng pool nhỏ | Có thể tạo starvation deadlock | Tránh nested blocking, điều chỉnh orchestration |
| Tăng `maxPoolSize` nhưng queue rất lớn | Queue được dùng trước khi mở thread ngoài core | Đo queue wait và chỉnh core/queue/max cùng nhau |
| Dùng `submit` thay cho durable messaging | Future và in-memory queue mất khi process chết | Dùng broker/outbox nếu cần durability |
| Tưởng ThreadLocal/transaction tự truyền sang worker | Thread context thường không chia sẻ | Truyền dữ liệu rõ ràng, cấu hình propagation khi cần |

## 14. Bài tập suy luận

**Bài 1.** Tác vụ sau thuộc kiểu nào và `Future.get()` trả gì?

```java
Runnable r = () -> System.out.println("saved");
Future<?> f = executor.submit(r);
```

Đáp án: đó là `Runnable`. Nếu task hoàn thành bình thường, `f.get()` trả `null`; dòng chữ `saved` là side effect được in ra. Fragment yêu cầu `AsyncTaskExecutor executor` đã được khai báo.

**Bài 2.** Với `Callable<String>` trả `"ok"`, `Future<String>.get()` cho kết quả gì?

Đáp án: trả `"ok"` nếu hoàn thành thành công; nếu task ném exception, caller nhận `ExecutionException`; nếu đang chờ và bị interrupt, caller nhận `InterruptedException`.

**Bài 3.** Caller nhận `TimeoutException` từ `future.get(1, SECONDS)`, sau đó retry gọi cùng tác vụ gửi email. Có chắc email chỉ được gửi một lần?

Không. Future cũ có thể vẫn đang chạy và hoàn tất sau timeout. Nếu cần retry an toàn, sử dụng idempotency key, trạng thái công việc, hoặc giải pháp giao việc bền vững đúng đặc thù nghiệp vụ.

**Bài 4.** Vì sao `Future.isDone()==true` chưa chứng minh task thành công?

Vì trạng thái done cũng bao gồm task thất bại hoặc bị hủy. Cần đọc kết quả hoặc exception bằng `get()` và nhận biết cancellation.

**Bài 5.** Một Spring service chỉ nhận `TaskExecutor` nhưng cần trả `Future<Integer>`. Có nên ép kiểu sang `ThreadPoolTaskExecutor`?

Không. Hãy biểu đạt dependency cần thiết bằng `AsyncTaskExecutor` và cấu hình một bean phù hợp. Điều đó vẫn tách business logic khỏi implementation.

## 15. Checklist áp dụng vào ứng dụng Spring

Trước khi dùng `submit` trong production, kiểm tra:

1. **API:** client cần kết quả hay chỉ side effect? Chọn `Runnable` hoặc `Callable<T>` và interface `AsyncTaskExecutor` đúng khả năng.
2. **Bean:** executor thực tế được tiêm có tên, core/max/queue/rejection và lifecycle phù hợp? Có bị nhầm pool không?
3. **Outcome:** ai đọc `Future`, xử lý `ExecutionException`, `CancellationException`, `TaskRejectedException` và ghi nhận trạng thái cuối?
4. **Timeout:** có giới hạn hợp lý trên Future và trên DB/HTTP bên trong task? Caller timeout thì task còn chạy hay bị hủy theo chính sách nào?
5. **Context:** transaction, security, MDC và dữ liệu input có còn hợp lệ khi worker chạy sau đó?
6. **Concurrency:** có nested blocking hoặc các task phụ thuộc nhau trong cùng pool nhỏ không? Có đủ hạn mức downstream?
7. **Operations:** quan sát active threads, queue size, queue wait, execution time, rejection, error và memory dưới tải thực?
8. **Durability:** tác vụ có bắt buộc tồn tại qua restart hay không? Nếu có, một in-memory executor với Future là chưa đủ.

**Ghi nhớ:** `submit(Runnable)` giúp theo dõi **việc hoàn thành** của hành động không có giá trị trả về, còn `submit(Callable<T>)` giúp theo dõi **một kết quả kiểu T**. Cả hai trả `Future` để caller có thể biết thành công, lỗi, timeout hoặc cancellation; quyền quyết định thực thi, giới hạn tài nguyên và lifecycle vẫn thuộc executor được cấu hình.

### Tài liệu tham khảo

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2 — AsyncTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html)
- [Spring Framework 6.2 — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Java 21 — Future Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html)
- [Java 21 — ExecutorService Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-submission-003">Nhận kết quả, kiểm tra hoàn tất và nhận biết hủy qua Future</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận kết quả, kiểm tra hoàn tất và nhận biết hủy qua Future

Khi gửi một tác vụ cho executor, bên gọi thường muốn biết ba điều: **kết quả là gì**, **công việc đã xong chưa**, và **yêu cầu hủy có được chấp nhận không**. Trong Java, `java.util.concurrent.Future<V>` là hợp đồng để truy cập trạng thái và kết quả của một phép tính có thể thực hiện bất đồng bộ. Spring Framework sử dụng chính hợp đồng này thông qua các executor có khả năng `submit`, đặc biệt là `AsyncTaskExecutor` và `ThreadPoolTaskExecutor`.

Điểm cần nhớ ngay từ đầu: **Future đại diện cho một phép tính; Future không tự tạo thread**. Executor quyết định nơi và cách chạy; Future cho bên gọi một *handle* để chờ, đọc kết quả, kiểm tra hoàn tất hoặc đề nghị hủy.

## 1. Từ gửi tác vụ tới nhận Future

Một `TaskExecutor` thuần cung cấp `execute(Runnable)` với kiểu trả về `void`. Muốn có handle kết quả, dùng `ExecutorService.submit(...)` của JDK hoặc `AsyncTaskExecutor.submit(...)` của Spring. `ThreadPoolTaskExecutor` là một implementation của `AsyncTaskExecutor`.

```java
// Mảnh mã minh họa với biến executor đã là một ExecutorService:
Future<Integer> value = executor.submit(() -> 21 * 2);
int answer = value.get(); // 42 sau khi tính xong.
```

Ở đây `submit(Callable<Integer>)` trả `Future<Integer>`. Biểu thức lambda được thực thi theo chính sách của executor; caller giữ Future và quyết định khi nào chờ. Đây là **mảnh mã tích hợp**, cần executor, import và xử lý ngoại lệ trong ngữ cảnh thật.

Có hai dạng gửi phổ biến:

| Cách gửi | Kiểu tác vụ | Handle nhận được | Ý nghĩa kết quả |
| --- | --- | --- | --- |
| `execute(Runnable)` | `Runnable` | Không có `Future` | Chỉ giao công việc |
| `submit(Runnable)` | `Runnable` | `Future<?>` | Nếu hoàn tất bình thường, `get()` trả `null` |
| `submit(Callable<T>)` | `Callable<T>` | `Future<T>` | `get()` trả giá trị `T` hoặc báo trạng thái lỗi/hủy |
| `submit(Runnable, T result)` | JDK `ExecutorService` với `Runnable` | `Future<T>` | `get()` trả `result` đã cung cấp khi chạy thành công |

Dạng `submit(Runnable, T result)` là phương thức của **`ExecutorService` JDK**, không nên mặc định gán nó cho interface `AsyncTaskExecutor` của Spring. Các implementation khác nhau có thể cung cấp thêm chức năng, nhưng code nên lập trình theo đúng kiểu interface đã chọn.

## 2. Các phương thức quan trọng của Future

```java
public interface Future<V> {
    boolean cancel(boolean mayInterruptIfRunning);
    boolean isCancelled();
    boolean isDone();
    V get() throws InterruptedException, java.util.concurrent.ExecutionException;
    V get(long timeout, java.util.concurrent.TimeUnit unit)
        throws InterruptedException, java.util.concurrent.ExecutionException,
               java.util.concurrent.TimeoutException;
}
```

Đây là **phần rút gọn các phương thức cơ bản của Future để học API Java 17**, không phải source interface đầy đủ cho mọi phiên bản Java. Không cần tự khai báo interface này trong ứng dụng.

- **`get()`**: chờ phép tính hoàn tất nếu cần, sau đó lấy giá trị hoặc ném ngoại lệ phản ánh lỗi/hủy. Nếu task chưa xong, thread **gọi get** có thể bị chặn.
- **`get(timeout, unit)`**: chờ tối đa một khoảng thời gian được chỉ định để lấy kết quả; khi chưa có kết quả kịp, ném `TimeoutException`. **Timeout khi chờ không tự hủy task**.
- **`isDone()`**: trả `true` khi phép tính ở trạng thái Future đã hoàn tất, bao gồm **thành công, thất bại hoặc bị hủy**. Nó không cho biết kết quả thành công.
- **`isCancelled()`**: trả `true` nếu Future đã bị hủy thành công trước khi hoàn tất bình thường.
- **`cancel(boolean)`**: thử chuyển Future sang trạng thái hủy. Giá trị `true` trả về có nghĩa yêu cầu hủy Future đã thành công theo hợp đồng của implementation; **không phải bằng chứng mọi thao tác bên ngoài của task đã dừng hoặc hoàn tác**.

Hai cách sử dụng thường đi cùng nhau: dùng `isDone()` để kiểm tra trạng thái không chặn, và dùng `get()` tại điểm cần **xác nhận kết quả cuối cùng, nhận exception và thiết lập quan hệ công bố bộ nhớ**. Gọi `isDone()` liên tục trong vòng lặp bận vừa lãng phí CPU vừa không thay thế thiết kế giao tiếp kết quả rõ ràng.

## 3. Đọc trạng thái Future như một bảng trạng thái

| Trạng thái hợp đồng | `isDone()` | `isCancelled()` | `get()` |
| --- | --- | --- | --- |
| Mới nộp, chưa hoàn tất | `false` | `false` | Có thể phải chờ |
| Hoàn tất bình thường | `true` | `false` | Trả giá trị (hoặc `null` cho Runnable) |
| Hoàn tất bằng exception | `true` | `false` | Ném `ExecutionException` chứa nguyên nhân |
| Đã hủy thành công | `true` | `true` | Ném `CancellationException` |

Sơ đồ trạng thái ở mức **Future**:

```text
       PENDING / RUNNING
         |     |      |
         |     |      +---- cancel thành công ----> CANCELLED
         |     |
         |     +----------- lỗi trong task -------> FAILED
         |
         +----------------- trả về bình thường ---> SUCCESS

   SUCCESS, FAILED, CANCELLED đều làm isDone() == true.
```

Lưu ý kỹ: `isDone() == true` vì **hủy** không đồng nghĩa mọi thread đang chạy tác vụ đó đã ngừng lập tức. `cancel(false)` có thể đánh dấu Future bị hủy trong khi task đã bắt đầu vẫn tiếp tục; `cancel(true)` chỉ **cố gắng** ngắt worker phù hợp với implementation. Hệ thống bên ngoài (database, HTTP, file) cũng không tự rollback nhờ Future bị hủy.

## 4. Một ví dụ Java 17+ chạy độc lập và có stdout xác định

Ví dụ sau thể hiện đủ các nhánh: nhận kết quả thành công, kiểm tra hoàn tất, chờ quá hạn, hủy một task còn xếp hàng và lấy nguyên nhân lỗi. Chương trình dùng executor **một worker**; một `CountDownLatch` giữ worker bận để task cần hủy chắc chắn vẫn nằm trong hàng đợi lúc gọi `cancel(false)`.

Tạo tệp **`FutureStateDemo.java`**:

```java
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class FutureStateDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch runningStarted = new CountDownLatch(1);
        CountDownLatch releaseRunning = new CountDownLatch(1);

        try {
            // 1. Nhận một kết quả thành công.
            Future<Integer> success = pool.submit(() -> 21 * 2);
            System.out.println("result = " + success.get());
            System.out.println("success done = " + success.isDone());

            // 2. Chiếm worker cho đến khi main chủ động thả latch.
            Future<?> running = pool.submit(() -> {
                runningStarted.countDown();
                releaseRunning.await();
                return null;
            });
            runningStarted.await();
            System.out.println("running done = " + running.isDone());

            // Chờ quá hạn không làm task tự dừng.
            try {
                running.get(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                System.out.println("wait = timeout");
            }

            // 3. Worker đang bận nên tác vụ sau chắc chắn chờ trong queue.
            Future<String> queued = pool.submit(() -> "unreachable");
            System.out.println("cancel accepted = " + queued.cancel(false));
            System.out.println("cancelled = " + queued.isCancelled());
            System.out.println("cancel done = " + queued.isDone());

            try {
                queued.get();
            } catch (CancellationException ex) {
                System.out.println("cancel get = CancellationException");
            }

            // 4. Giải phóng worker và kiểm tra nguyên nhân của task lỗi.
            releaseRunning.countDown();
            running.get();
            Future<Integer> failed = pool.submit(() -> {
                throw new IllegalStateException("boom");
            });
            try {
                failed.get();
            } catch (ExecutionException ex) {
                System.out.println("failure cause = "
                        + ex.getCause().getClass().getSimpleName());
            }
        } finally {
            releaseRunning.countDown();
            pool.shutdownNow();
        }
    }
}
```

Chạy trong thư mục chứa tệp:

```bash
javac FutureStateDemo.java
java FutureStateDemo
```

Kết quả chuẩn:

```text
result = 42
success done = true
running done = false
wait = timeout
cancel accepted = true
cancelled = true
cancel done = true
cancel get = CancellationException
failure cause = IllegalStateException
```

**Vì sao kết quả lặp lại ổn định?**

1. `success.get()` chờ phép tính `21*2` nên giá trị đầu là `42`; sau `get()`, Future đó đã hoàn tất.
2. `runningStarted.await()` xác nhận worker đã vào lambda đang chờ `releaseRunning`. Tại điểm đọc, Future của task ấy chưa hoàn tất, và `get(20ms)` phải timeout do latch chưa được thả.
3. Pool chỉ có một worker đang bị giữ; task `queued` chưa thể bắt đầu. Bởi vậy `queued.cancel(false)` chấp nhận hủy mà không phải ngắt một task đang chạy.
4. Future đã hủy có `isCancelled=true`, `isDone=true`, và `get()` ném `CancellationException`, dù task ấy chưa từng chạy.
5. Sau khi thả latch và `running.get()` hoàn tất, task ném `IllegalStateException` được gửi vào pool; `failed.get()` báo `ExecutionException` với `getCause()` là `IllegalStateException`.

Khối `finally` luôn thả latch và yêu cầu đóng pool, tránh để một worker bị giữ vô hạn nếu nhánh khác phát sinh lỗi. Trong ứng dụng Spring thực, pool thường là bean có vòng đời do container quản lý; service **không nên tự shutdown bean pool dùng chung sau mỗi request**.

## 5. Phân biệt chờ kết quả với kiểm tra hoàn tất

Có ba nhu cầu khác nhau:

### Khi bắt buộc cần kết quả ngay lúc này

Gọi `get()` hoặc `get(timeout, unit)` và xử lý tất cả kết quả/ngoại lệ tương ứng. Nếu caller là thread HTTP request, việc gọi `get()` ngay sau `submit()` **vẫn có thể làm request chờ**; “đã dùng thread pool” không đồng nghĩa đường xử lý end-to-end đã trở thành không chặn.

### Khi chỉ cần quan sát trạng thái

Có thể dùng `isDone()` và `isCancelled()` để trả tình trạng task cho giao diện hoặc log. Nhưng `isDone()` không giải thích được task thành công hay thất bại; muốn xác định kết quả và nguyên nhân lỗi, cần `get()` hay cơ chế lưu trạng thái kết quả được thiết kế riêng.

**Không dùng** vòng lặp sau như cơ chế chờ chuẩn:

```java
while (!future.isDone()) {
    // Busy-spin: liên tục hỏi trạng thái và tiêu thụ CPU.
}
```

Đoạn trên là **minh họa cách làm không nên dùng**, giả sử đã có `future`. Nếu cần chờ, dùng `get()`/timeout hoặc kết hợp kết quả bất đồng bộ bằng API như `CompletableFuture`.

### Khi không muốn chờ vô thời hạn

Dùng `get(timeout, unit)`:

```java
try {
    Integer result = future.get(500, TimeUnit.MILLISECONDS);
    useResult(result);
} catch (TimeoutException ex) {
    // Caller hết thời gian đợi; task có thể vẫn chạy.
}
```

Đây là **mảnh mã tích hợp**, cần biến `future`, `useResult` và xử lý thêm các checked exceptions `InterruptedException`, `ExecutionException` trong phương thức chứa nó. `get(timeout)` chỉ áp dụng thời hạn cho **lượt chờ cụ thể**, không tự đặt deadline cho task và không tự kích hoạt hủy.

## 6. Yêu cầu hủy: cancel(false) và cancel(true)

Đối số `mayInterruptIfRunning` quyết định liệu implementation có được **thử ngắt thread đang chạy** task hay không.

| Lệnh gọi | Ý định | Điều không bảo đảm |
| --- | --- | --- |
| `future.cancel(false)` | Hủy Future; không chủ động interrupt worker đang chạy | Không bảo đảm một task đã bắt đầu sẽ ngừng làm việc |
| `future.cancel(true)` | Hủy Future; cho phép thử interrupt thread thực thi | Không buộc mọi code dừng; không tự rollback side effects |
| `future.isCancelled()` | Kiểm tra Future đã bị đánh dấu hủy thành công | Không chứng minh external request hoặc DB transaction đã dừng |
| `future.get()` sau hủy | Ném `CancellationException` | Không chờ cho đến khi mọi side effect của task đang chạy đã chấm dứt |

**Hủy là hợp tác.** Trong Java, `Thread.interrupt()` không giết thread. Các lời gọi chặn hỗ trợ interruption như `Thread.sleep`, `BlockingQueue.take`, `CountDownLatch.await` thường ném `InterruptedException`. Code CPU-bound phải chủ động kiểm tra cờ interrupt và kết thúc ở vị trí an toàn. Một số thư viện I/O hoặc native API có cơ chế hủy riêng; không được mặc định mọi lời gọi đều đáp ứng interrupt.

Mảnh minh họa task có cơ chế hợp tác:

```java
Future<?> task = executor.submit(() -> {
    while (!Thread.currentThread().isInterrupted()) {
        doOneSmallUnitOfWork();
    }
});
task.cancel(true); // Đề nghị ngắt nếu task còn đang chạy.
```

Đây là **mảnh mã tích hợp**, cần `executor`, `doOneSmallUnitOfWork()` và chính sách kết thúc/phục hồi công việc. Nếu `doOneSmallUnitOfWork()` có thể chặn hoặc ném `InterruptedException`, cần xử lý ngắt theo đúng hợp đồng của thao tác. Đừng nuốt interruption rồi lặp vô hạn.

Khi `cancel(false)` gọi trên một task **đang chạy**, nhiều implementation có thể đánh dấu Future là cancelled, nhưng worker vẫn tiếp tục; điều này dễ gây nhầm lẫn nếu chỉ nhìn `isDone=true`. Với task chưa bắt đầu và Future hủy thành công, task đó không nên bắt đầu chạy.

Đặc biệt lưu ý **`CompletableFuture.cancel(true)` khác kỳ vọng ngắt thread kiểu `FutureTask`**: theo JavaDoc của `CompletableFuture`, tham số `mayInterruptIfRunning` không có tác dụng ở implementation này vì interruption không phải cơ chế điều khiển xử lý của nó. Hãy kiểm tra loại Future thực tế trước khi dựa vào hủy bằng interrupt.

## 7. Bốn nhóm ngoại lệ khi nhận kết quả

Khi gọi `get()`/`get(timeout)`, cần phân biệt:

| Ngoại lệ | Nguồn gốc | Cách xử lý thích hợp |
| --- | --- | --- |
| `ExecutionException` | Task chạy và ném exception | Xem `getCause()`; báo lỗi nghiệp vụ hoặc log nguyên nhân |
| `InterruptedException` | **Thread đang chờ** `get` bị interrupt | Tôn trọng yêu cầu dừng; nếu bắt để xử lý, thường khôi phục interrupt flag |
| `CancellationException` | Future bị hủy | Đánh dấu kết quả canceled, tránh xử lý như thành công |
| `TimeoutException` | `get(timeout, unit)` chưa nhận được kết quả trong thời hạn | Xử lý deadline; quyết định riêng có hủy task không |

Mẫu xử lý tại ranh giới service có thời hạn:

```java
try {
    return future.get(2, TimeUnit.SECONDS);
} catch (TimeoutException ex) {
    future.cancel(true); // Chính sách tùy nghiệp vụ, không bảo đảm dừng ngay.
    throw new IllegalStateException("Task timed out", ex);
} catch (CancellationException ex) {
    throw new IllegalStateException("Task cancelled", ex);
} catch (ExecutionException ex) {
    throw new IllegalStateException("Task failed", ex.getCause());
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt(); // Khôi phục trạng thái ngắt.
    throw new IllegalStateException("Wait interrupted", ex);
}
```

Đây là **mảnh phương thức minh họa**, giả sử `future` có kiểu `Future<T>`, phương thức trả `T`, và đã import `java.util.concurrent.*`. Lựa chọn `cancel(true)` khi timeout cần dựa trên khả năng dừng task, tính idempotent và tác dụng phụ; không phải quy tắc mặc định cho mọi ứng dụng.

Khi `get()` phát sinh `ExecutionException`, không nên chỉ log wrapper mà bỏ qua nguyên nhân. Bên trong `getCause()` mới có exception task đã ném. Ngược lại, `InterruptedException` ở caller không phải lỗi nghiệp vụ của worker và cũng **không tự nói task đã bị hủy**.

## 8. Các ranh giới bộ nhớ mà Future đảm bảo

Với `ExecutorService` và các cơ chế executor tuân thủ hợp đồng JDK, các hành động của caller **trước khi gửi task** có quan hệ *happens-before* với hành động trong task. Quan trọng hơn, các hành động của phép tính bất đồng bộ **happen-before** những hành động tiếp theo của thread sau khi lấy kết quả bằng **`Future.get()`**.

```text
caller:  prepare input --> submit(task) .......... get() --> read result
                             |                        ^
worker:                      +--> read input --> write output --> complete
```

Vì vậy, một mô hình hợp lệ là chuẩn bị dữ liệu trước submit, cho task tính toán, rồi chỉ đọc output sau khi `get()` trả về bình thường. Ví dụ trạng thái chia sẻ không có writer cạnh tranh khác có thể được công bố nhờ quan hệ này, ngay cả khi field không khai báo `volatile`.

Nhưng `get()` **không làm phép toán `counter++` nguyên tử**, không loại bỏ race giữa nhiều task và không cho phép đọc state tùy tiện trong lúc task đang chạy. `isDone()` được dùng để hỏi trạng thái; khi cần lập luận chuẩn về công bố dữ liệu và nhận kết quả, hãy dựa vào `get()` hoặc cơ chế đồng bộ có hợp đồng rõ ràng.

## 9. Trong Spring: AsyncTaskExecutor, ThreadPoolTaskExecutor và @Async

Spring `TaskExecutor` chỉ có `execute(Runnable)`. `AsyncTaskExecutor` mở rộng hợp đồng và cung cấp khả năng submit. `ThreadPoolTaskExecutor` là implementation thường dùng khi muốn cấu hình core/max/queue và lấy Future.

Ví dụ cấu hình bean trong Spring Framework (các snippet cần dependency `spring-context`, được container đăng ký, không phải chương trình JDK độc lập):

```java
@Configuration(proxyBeanMethods = false)
public class WorkerConfig {
    @Bean("workerPool")
    public ThreadPoolTaskExecutor workerPool() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(16);
        pool.setThreadNamePrefix("worker-");
        return pool;
    }
}
```

Giả sử một bean nhận `AsyncTaskExecutor` thông qua constructor/qualifier:

```java
Future<Integer> future = executor.submit(() -> expensiveCalculation());
try {
    Integer value = future.get(300, TimeUnit.MILLISECONDS);
    useValue(value);
} catch (TimeoutException ex) {
    // Tác vụ có thể vẫn chạy. Quyết định riêng có hủy hay trả trạng thái đang xử lý.
}
```

Đoạn trên cần import `AsyncTaskExecutor`, `Future`, `TimeUnit`, `TimeoutException` và xử lý các ngoại lệ còn lại. Lựa chọn thời gian chờ cần căn cứ SLO và nghiệp vụ; 300 ms chỉ là ví dụ.

Với phương thức `@Async`, khi đã bật `@EnableAsync` và lời gọi đi qua Spring proxy, phương thức có thể trả `Future<T>` hoặc `CompletableFuture<T>` để caller nhận handle:

```java
@Async("workerPool")
public CompletableFuture<Integer> calculate(int x) {
    return CompletableFuture.completedFuture(x * 2);
}
```

Trong ngữ cảnh này, đoạn code ở trên là **mảnh phương thức trong một Spring-managed bean**, cần khai báo import, `@EnableAsync`, bean executor và lời gọi **từ bên ngoài qua proxy**. `this.calculate(...)` tự gọi bên trong cùng object ở chế độ proxy mặc định thường bỏ qua cơ chế async. Phương thức trả `void` được đánh dấu `@Async` không mang `Future` về cho caller; để quan sát trạng thái/lỗi/kết quả, cần giao thức khác.

**Không nên gọi `get()` ngay trong chính executor có ít worker nếu kết quả phụ thuộc vào một task khác đang chờ cùng pool**: điều này có thể làm tất cả worker bị chặn trong khi task cần chạy không có worker rảnh, dẫn đến starvation/deadlock. Hãy thiết kế phụ thuộc bằng `CompletableFuture`/completion stages, pool phù hợp hoặc cấu trúc không chặn khi có thể.

## 10. Future và CompletableFuture giống, khác nhau ở đâu?

`CompletableFuture<T>` **implements `Future<T>`**, nên cũng có `get()`, `get(timeout)`, `isDone()`, `isCancelled()` và `cancel(...)`. Nhưng nó bổ sung khả năng hoàn tất một cách tường minh, kết hợp các stage và lập trình callback.

| Nhu cầu | `Future<T>` cơ bản | `CompletableFuture<T>` |
| --- | --- | --- |
| Chờ/đọc kết quả | `get()` | `get()`, `join()` |
| Kiểm tra trạng thái | `isDone()`, `isCancelled()` | Các phương thức Future, cộng API stage |
| Lấy exception | `ExecutionException` qua `get()` | `get()` hoặc `join()` thường ném `CompletionException` khi lỗi |
| Ghép phép tính | Không có API stage trong hợp đồng Java 17 `Future` | `thenApply`, `thenCompose`, `handle`, `exceptionally`... |
| Yêu cầu hủy bằng interrupt | Phụ thuộc implementation và `cancel(true)` | `cancel(true)` không dùng interrupt để dừng tính toán trong implementation này |

Ví dụ ghép kết quả, chỉ là **mảnh mã API** với `CompletableFuture<Integer> first` đã có:

```java
CompletableFuture<String> label = first.thenApply(x -> "Result = " + x);
String text = label.join();
```

`join()` cũng có thể chặn; nó báo lỗi qua các unchecked exceptions như `CompletionException`, còn `get()` khai báo checked `ExecutionException` và `InterruptedException`. Dù chọn kiểu nào, người dùng cần hiểu trạng thái terminal và chính sách timeout/hủy. `CompletableFuture` không tự bảo đảm một callback sẽ chạy trên thread nào: việc dùng biến thể `Async` và executor được chỉ định ảnh hưởng cách lập lịch.

## 11. Cần quan sát nhiều tác vụ? Đừng nhầm một Future với cả lô

Mỗi Future thường đại diện cho **một lần submit**. Nếu gửi ba task riêng, `futureA.isDone()` không nói `futureB` đã xong. Muốn chờ tất cả, lưu từng Future và gọi `get()` trên từng cái hoặc dùng cơ chế tổng hợp như `CompletableFuture.allOf` khi các tác vụ có kiểu thích hợp.

Ví dụ với `ExecutorService` JDK đã được tạo:

```java
List<Future<Integer>> tasks = new ArrayList<>();
for (int i = 1; i <= 3; i++) {
    final int input = i;
    tasks.add(executor.submit(() -> input * 10));
}
for (Future<Integer> task : tasks) {
    System.out.println(task.get());
}
```

Đây là **mảnh mã minh họa**, cần import và executor, không phải file Java độc lập. Các kết quả được in theo thứ tự **duyệt danh sách và chờ** `get()`, không chứng minh thứ tự thực thi hay hoàn thành trong pool giống nhau. Với nhiều task, cân nhắc tổng deadline thay vì cấp mỗi `get(timeout)` một khoảng riêng quá dài làm tổng thời gian vượt hạn mức yêu cầu.

Một lỗi kiến trúc khác: gọi `cancel()` trên một Future không tự hủy toàn bộ các task phụ thuộc nó, một request HTTP đang chạy ngoài process hoặc transaction trên worker khác. Hủy theo nhóm cần được thiết kế riêng, có nhận diện task và xử lý partial completion.

## 12. Hủy tác vụ không đồng nghĩa hoàn tác nghiệp vụ

Giả sử task đã:

1. Ghi một bản ghi vào database.
2. Gửi yêu cầu thanh toán qua HTTP.
3. Bị caller `cancel(true)` trước khi nhận response.

Future có thể chuyển sang trạng thái canceled, nhưng các tác dụng phụ ở bước 1–2 vẫn có thể đã xảy ra. Vì thế một thiết kế cần hủy thực sự nên phân biệt:

- **Hủy handle/chờ**: caller không còn muốn lấy kết quả.
- **Yêu cầu worker dừng**: worker kiểm tra cờ interrupt hoặc cờ cancellation của ứng dụng, rồi dừng ở điểm an toàn.
- **Hủy tác vụ tại dịch vụ ngoài**: có thể cần API riêng để dừng công việc đã gửi đi.
- **Bù trừ/khôi phục nghiệp vụ**: nếu đã ghi dữ liệu hoặc gọi dịch vụ ngoài, cần giao dịch, idempotency key hoặc luồng bù trừ theo nghiệp vụ; Future không làm thay.

Ngoài ra, **timeout ở caller không đảm bảo timeout ở worker hoặc downstream**. Một hệ thống có thể hết thời gian chờ HTTP trong 500 ms nhưng task chạy thêm nhiều giây. Cần phối hợp deadline, timeout của HTTP/DB client, vòng đời job và cơ chế tránh xử lý trùng.

## 13. Những lỗi thường gặp

1. **Nhầm `submit` với `get`.** `submit` tạo một Future, không tự đảm bảo đã có kết quả tại thời điểm trả về; `get` mới là thao tác chờ/lấy kết quả.
2. **Thấy `isDone=true` rồi kết luận thành công.** Future cũng hoàn tất do lỗi hoặc bị hủy; phải phân loại.
3. **Thấy `cancel(true)` trả true rồi coi thread đã chết.** Interruption chỉ là yêu cầu hợp tác, không phải cưỡng bức dừng và không rollback tác dụng phụ.
4. **Dùng `get(timeout)` và cho rằng task tự bị hủy.** `TimeoutException` chỉ nói lượt chờ hết hạn; chính sách hủy là quyết định riêng.
5. **Nuốt `InterruptedException` rồi tiếp tục chờ vô hạn.** Khi bắt ngắt ở caller, thường khôi phục cờ ngắt và kết thúc luồng xử lý phù hợp.
6. **Đọc `ExecutionException` mà không xem `getCause()`.** Nguyên nhân thực sự nằm ở exception do task ném.
7. **Đọc shared state trước khi Future hoàn tất.** Có thể xảy ra data race hoặc thấy giá trị cũ; sử dụng kết quả trả về hay cơ chế đồng bộ rõ.
8. **Gọi `get()` ngay sau mọi lần submit và tưởng đang xử lý song song.** Điều đó có thể tuần tự hóa caller; submit các task độc lập trước rồi lấy kết quả phù hợp.
9. **Chặn worker của pool để đợi task khác trong cùng pool.** Có thể dẫn đến starvation/deadlock; nên thiết kế phụ thuộc theo completion stage hoặc tách cơ chế thực thi.
10. **Nhầm `@Async` với lời gọi Java trực tiếp.** Trong mô hình proxy Spring, tự gọi `this.method()` không đi qua interceptor async.
11. **Giữ Future chỉ trong bộ nhớ rồi xem nó như trạng thái job bền vững.** Khi JVM khởi động lại, handle và trạng thái nội bộ mất; công việc dài hạn cần kho trạng thái/broker bền vững nếu nghiệp vụ yêu cầu.
12. **Không xử lý rejection lúc submit.** Khi executor quá tải hoặc đã đóng, `submit` có thể ném exception ngay; lúc đó chưa có Future hợp lệ để theo dõi.

## 14. Tự kiểm tra hiểu bài

**Câu 1.** `Future.isDone()` trả `true`. Có thể khẳng định đã nhận được kết quả thành công không?  
**Đáp án:** Không. Nó cũng true khi lỗi hoặc hủy; phải đọc kết quả/ngoại lệ hoặc kiểm tra trạng thái bổ sung.

**Câu 2.** `future.get(200, MILLISECONDS)` ném `TimeoutException`. Task có bị dừng không?  
**Đáp án:** Không tự động. Future có thể vẫn đang chờ hoặc chạy; muốn hủy cần gọi `cancel` theo chính sách thích hợp và task phải hỗ trợ dừng an toàn.

**Câu 3.** Một task đang thực hiện HTTP call; `cancel(true)` trả `true`. Có bảo đảm HTTP request đã được server bên kia hủy không?  
**Đáp án:** Không. Có thể đã có side effect; cần timeout/hủy ở HTTP client và cơ chế riêng ở hệ thống đích.

**Câu 4.** `get()` ném `ExecutionException`. Exception nghiệp vụ gốc ở đâu?  
**Đáp án:** `getCause()` của `ExecutionException`.

**Câu 5.** Cần một giá trị số nguyên từ task Spring. Nên chọn `TaskExecutor.execute` hay `AsyncTaskExecutor.submit(Callable<Integer>)`?  
**Đáp án:** `submit(Callable<Integer>)` cho `Future<Integer>` phù hợp với nhu cầu nhận kết quả.

**Câu 6.** Tại sao Future bị hủy vẫn có `isDone=true` dù worker có thể chưa dừng?  
**Đáp án:** `isDone` phản ánh trạng thái terminal **của Future**, không xác nhận mọi side effect hoặc thread thực thi đã chấm dứt vật lý.

Tóm lại, dùng **`get` để lấy kết quả**, **`isDone` để xem Future đã đi tới trạng thái cuối hay chưa**, **`isCancelled` để nhận biết trạng thái hủy**, và **`cancel` để yêu cầu hủy theo khả năng hợp tác của implementation**. Để vận hành chắc chắn, cần tính đến timeout, interruption, lỗi trong task và các tác dụng phụ đã phát sinh.

### Tài liệu tham khảo

- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java SE 17 — FutureTask](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Spring Framework — AsyncTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html)
- [Spring Framework — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-submission-004">get() có giới hạn thời gian chờ</a>

<details>
<summary>Xem chi tiết</summary>

# `get()` có giới hạn thời gian chờ: nhận kết quả tác vụ trong Spring Framework

## Mục tiêu

Khi gửi một tác vụ cho executor, thread gọi thường nhận một `Future<T>` hoặc `CompletableFuture<T>` trước khi kết quả thực sự sẵn sàng. Nếu gọi `get()` không có giới hạn, thread đang chờ có thể bị giữ rất lâu khi tác vụ bị nghẽn I/O, treo tại lock, chờ hàng đợi hoặc không bao giờ hoàn thành. Java cung cấp biến thể **`get(long timeout, TimeUnit unit)`** để người gọi đặt **thời gian chờ tối đa cho một lần lấy kết quả**.

Bài học tập trung vào cách dùng giới hạn thời gian chờ trong bối cảnh Spring Framework concurrency, nơi tác vụ có thể được gửi bằng `AsyncTaskExecutor`, `ThreadPoolTaskExecutor` hoặc một phương thức `@Async` trả `Future`/`CompletableFuture`.

Sau bài này, bạn sẽ:

1. Đọc đúng hợp đồng của `Future.get(timeout, unit)` và phân biệt nó với `get()` không giới hạn.
2. Xử lý riêng thành công, `TimeoutException`, `ExecutionException`, `InterruptedException` và `CancellationException`.
3. Hiểu rằng **hết thời gian chờ của caller không tự hủy tác vụ ở worker**.
4. Sử dụng `get` có thời hạn với Spring `@Async`, biết luồng nào đang chờ, luồng nào đang xử lý.
5. Phân biệt thời hạn chờ kết quả với timeout của HTTP client/database, timeout của cả request, deadline của một quy trình nhiều tác vụ và `CompletableFuture.orTimeout`.
6. Chọn hành vi sau timeout (trả lỗi, fallback, tiếp tục xử lý nền, hủy có kiểm soát) mà không làm mất kết quả hay giữ tài nguyên vô hạn.

## 1. `Future` là gì và `get()` chờ tại đâu?

`Future<T>` là **đối tượng đại diện cho kết quả của một tính toán có thể chưa hoàn tất**. Nó thường được trả từ `ExecutorService.submit(...)`, `AsyncTaskExecutor.submit(...)` hoặc từ lời gọi Spring `@Async` có kiểu kết quả phù hợp.

```text
Caller thread                   Executor / worker thread
    |                                      |
    | submit task ----------------------->|  tác vụ chạy
    |<------------ Future<T> -------------|
    |                                      |  (có thể còn đang xử lý)
    | future.get(timeout, unit)            |
    |     [caller chờ kết quả]             |
    |                                      |
    |<------------- value / error --------|
    | caller chạy tiếp                     |
```

**Điểm quan trọng:** `get()` được gọi trên thread nào thì **thread ấy chờ**. `get(timeout, unit)` không chuyển việc sang worker mới, không tự tăng kích thước pool và không biến I/O blocking thành non-blocking. Trong Spring MVC, nếu request thread gọi `get(...)`, chính request thread có thể bị chặn trong khoảng chờ.

Một future có thể đã hoàn tất trước khi caller gọi `get`; lúc đó phương thức trả về ngay (hoặc ném lỗi/hủy đã ghi nhận). Giới hạn thời gian chờ **không buộc phải đợi đủ thời hạn**.

## 2. Chữ ký và ý nghĩa của giới hạn

Java 17 định nghĩa trên `java.util.concurrent.Future<V>`:

```java
V get(long timeout, TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException;
```

Ví dụ:

```java
String result = future.get(500, TimeUnit.MILLISECONDS);
```

Diễn giải:

- `500` là độ dài **thời gian chờ cho lần gọi `get` này**, không phải thời lượng tối đa của task tính từ lúc submit.
- `TimeUnit.MILLISECONDS` xác định đơn vị. Có thể dùng `SECONDS`, `MICROSECONDS`, `NANOSECONDS` và các đơn vị khác.
- Nếu kết quả đã hoàn tất thành công, trả về giá trị `V`.
- Nếu chưa có kết quả sau thời gian được phép chờ, ném `TimeoutException`.
- Nếu task thất bại, ném `ExecutionException` với nguyên nhân thực trong `getCause()`.
- Nếu thread **đang chờ** bị interrupt, ném `InterruptedException`.
- Nếu future đã bị cancel, có thể ném `CancellationException` (unchecked).

`get()` không tham số khác ở chỗ có thể chờ đến khi future hoàn thành (hoặc bị interrupt/cancel), **không có deadline đặt bởi caller**. Vì vậy nó không thích hợp khi request có thời hạn nghiêm ngặt mà task có thể bị kẹt.

### Bảng kết quả

| Tình trạng tại hoặc trong khi chờ | `future.get(500, MILLISECONDS)` | Caller nên làm gì? |
| --- | --- | --- |
| Thành công trong thời hạn | Trả `T` | Xử lý kết quả bình thường. |
| Task vẫn chưa hoàn tất khi hết thời hạn | Ném `TimeoutException` | Chọn phản hồi/fallback/cancel; không ngầm coi task đã dừng. |
| Task hoàn tất bằng exception | Ném `ExecutionException` | Xem `getCause()`, ghi nhận và phân loại lỗi nghiệp vụ. |
| Thread chờ bị interrupt | Ném `InterruptedException` | Khôi phục interrupt flag nếu không xử lý ở cấp cao hơn; kết thúc chờ hợp lý. |
| Future bị cancel | Ném `CancellationException` | Xử lý trạng thái hủy; không cố lấy giá trị như thành công. |

Một future có `isDone() == true` có thể là **thành công, lỗi hoặc bị hủy**. `isDone()` không đồng nghĩa “có giá trị thành công”; `isCancelled()` chỉ cho biết đã bị hủy. Giữa việc kiểm tra `isDone()` và xử lý kết quả, trạng thái có thể tiếp tục thay đổi (trừ khi đã hoàn tất); thường nên gọi `get(...)` trực tiếp và xử lý ngoại lệ.

### Vì sao timeout không đồng nghĩa task chậm đúng 500 ms?

Thời hạn bắt đầu khi **caller đi vào lần `get`**, có thể sau khi task đã nằm trong queue một khoảng thời gian. Mặt khác, chỉ bản thân lời gọi chờ được giới hạn; tổng độ trễ còn gồm submit, xây request, CPU scheduling, xử lý ngoại lệ và trả response. Java/OS không cam kết thời điểm thread được lên lịch chạy lại chính xác từng mili giây.

Ví dụ: caller submit một task, làm việc khác 2 giây rồi mới gọi `get(500, MILLISECONDS)`. Nó có thể nhận kết quả ngay nếu task đã hoàn tất; hoặc chờ thêm tối đa khoảng thời hạn này nếu task vẫn chưa xong. **Không thể diễn giải thành “task phải hoàn thành trong 500 ms từ lúc submit”.**

## 3. Spring Framework: gửi task và chờ có giới hạn

Ở cấp interface, Spring cung cấp:

- `TaskExecutor` để gửi `Runnable`.
- `AsyncTaskExecutor` để gửi `Runnable`/`Callable` và nhận handle kết quả theo API hiện hành.
- `ThreadPoolTaskExecutor` để cấu hình pool, queue và lifecycle dưới dạng Spring bean.
- `@EnableAsync` và `@Async` để Spring chuyển thực thi phương thức qua executor khi lời gọi đi qua proxy.

Một phương thức Spring:

```java
@Async("reportExecutor")
public CompletableFuture<String> createReport(String id) {
    return CompletableFuture.completedFuture("report:" + id);
}
```

Đoạn trên **chỉ là minh họa thân phương thức**, giả định đã có Spring bean, `@EnableAsync` và executor tên `reportExecutor`. Khi Spring proxy xử lý `@Async`, thân phương thức thường được thực thi trong executor; caller nhận `CompletableFuture<String>` và có thể chờ bằng:

```java
String value = reportService.createReport("A")
    .get(2, TimeUnit.SECONDS);
```

Nếu caller gọi `get` ngay, caller có thể bị chặn tối đa thời hạn trong khi worker tiếp tục xử lý. `@Async` không làm cho một lời gọi `get` trở thành non-blocking. Nếu caller không muốn chặn, hãy cân nhắc callback/composition hoặc hợp đồng async của lớp gọi.

**Lưu ý proxy:** `this.createReport(...)` bên trong chính bean thường **không** đi qua Spring proxy ở mode mặc định, nên annotation có thể không kích hoạt executor. Cần gọi qua reference bean được Spring quản lý, thường từ một bean khác, và chọn executor rõ bằng `@Async("reportExecutor")` khi ứng dụng có nhiều pool.

## 4. Ví dụ hoàn chỉnh Java 17+ và Spring: timeout không hủy task

Ví dụ sau dùng **Java 17+**, Maven và **Spring Framework 6.2.12**. Nó không cần Spring Boot hoặc máy chủ HTTP. Ta dùng `CountDownLatch` để giữ worker tại một bước chờ có chủ đích, bảo đảm caller gặp `TimeoutException`; sau đó caller mở chốt và lấy được kết quả **từ cùng future**. Nhờ vậy không phải dựa vào `Thread.sleep` hoặc so sánh mili giây thiếu ổn định.

Cấu trúc:

```text
spring-timed-get-demo/
├── pom.xml
└── src/main/java/demo/TimedGetDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-timed-get-demo</artifactId>
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
                    <mainClass>demo.TimedGetDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/demo/TimedGetDemo.java`:

```java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class TimedGetDemo {
    public static class Gates {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
    }

    public static class ReportService {
        private final Gates gates;

        public ReportService(Gates gates) {
            this.gates = gates;
        }

        @Async("reportExecutor")
        public CompletableFuture<String> createReport(String id) {
            gates.started.countDown();

            try {
                gates.release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(ex);
            }

            return CompletableFuture.completedFuture("report:" + id);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {
        @Bean
        public Gates gates() {
            return new Gates();
        }

        @Bean
        public ReportService reportService(Gates gates) {
            return new ReportService(gates);
        }

        @Bean("reportExecutor")
        public ThreadPoolTaskExecutor reportExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(1);
            pool.setQueueCapacity(4);
            pool.setThreadNamePrefix("report-");
            return pool;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            Gates gates = context.getBean(Gates.class);
            ReportService service = context.getBean(ReportService.class);

            CompletableFuture<String> future = service.createReport("A");

            try {
                if (!gates.started.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Worker did not start");
                }

                boolean timeoutObserved = false;
                try {
                    future.get(100, TimeUnit.MILLISECONDS);
                } catch (TimeoutException expected) {
                    timeoutObserved = true;
                }

                System.out.println("timeoutObserved=" + timeoutObserved);
                System.out.println("doneBeforeRelease=" + future.isDone());
            } finally {
                // Quan trọng: không để worker chờ vô hạn nếu main gặp lỗi.
                gates.release.countDown();
            }

            String result = future.get(5, TimeUnit.SECONDS);
            System.out.println("result=" + result);
            System.out.println("doneAfterRelease=" + future.isDone());
        }
    }
}
```

Chạy từ thư mục `spring-timed-get-demo`:

```bash
mvn -q compile exec:java
```

Bốn dòng do chương trình in ra khi chạy bình thường:

```text
timeoutObserved=true
doneBeforeRelease=false
result=report:A
doneAfterRelease=true
```

### Phân tích từng bước

1. Spring context tạo `reportExecutor`, một `ThreadPoolTaskExecutor` chỉ có một worker trong ví dụ. `@EnableAsync` đăng ký cơ chế xử lý `@Async`.
2. `context.getBean(ReportService.class)` lấy bean qua Spring. `service.createReport("A")` đi qua proxy; Spring gửi công việc vào `reportExecutor` và trả một `CompletableFuture<String>` cho caller.
3. Worker vào thân `createReport`, giảm `started` xuống 0 rồi đợi `release`. Main chỉ tiếp tục sau khi `started.await(...)` xác nhận worker đã thực sự bắt đầu, vì vậy không dựa vào may rủi thứ tự lên lịch.
4. Main gọi `future.get(100, TimeUnit.MILLISECONDS)` trong khi worker **không thể hoàn tất** vì chốt `release` chưa mở. Lần chờ này hết hạn và ném `TimeoutException`; main in `timeoutObserved=true`.
5. Ngay trước khi mở chốt, worker vẫn đang đợi. Future chưa hoàn tất nên `doneBeforeRelease=false`. **Timeout không tự cancel future, không nhả chốt và không giết worker.**
6. Khối `finally` mở chốt; worker tạo `CompletableFuture.completedFuture("report:A")` và Spring hoàn tất future trả về cho caller.
7. Main gọi `future.get(5, TimeUnit.SECONDS)` **lần thứ hai trên chính future đó**, nhận `report:A`. Sau khi `get` trả giá trị, future đã hoàn tất nên `doneAfterRelease=true`.

Nếu `started.await` hết hạn hoặc thread bị interrupt, ví dụ chủ động ném lỗi và vẫn mở chốt trong `finally`. Một lỗi môi trường hay executor thất bại có thể làm chương trình không in các dòng trên; output mô tả trường hợp chạy bình thường.

### Vì sao đây là bằng chứng đúng?

- `CountDownLatch` tạo quan hệ phối hợp: caller chỉ chờ future khi worker đã chạy tới điểm kiểm soát.
- Worker không thể tính xong và trả kết quả trước khi main mở `release` (trừ khi cơ chế ngoài ý muốn can thiệp).
- Chờ `get(100, MILLISECONDS)` hết hạn chỉ tác động lên hành vi của **main**, không thay trạng thái hoàn tất của task.
- Lần `get(5, SECONDS)` tiếp theo chứng minh cùng future vẫn có thể nhận kết quả sau một lần timeout.

Trong ứng dụng thật, không dùng latch để mô phỏng I/O hoặc giữ worker dài hạn. Chọn timeout client tương ứng, phân bổ thread pool và bảo vệ số lượng tác vụ đang chạy/chờ.

## 5. Xử lý các ngoại lệ riêng biệt

Mẫu sau áp dụng khi đã có `Future<Result> future`. Đây là **mã minh họa trong một phương thức**, cần định nghĩa kiểu `Result` và `fallback()` theo ứng dụng; không phải một chương trình độc lập:

```java
try {
    Result result = future.get(2, TimeUnit.SECONDS);
    return result;
} catch (TimeoutException ex) {
    // Caller hết ngân sách chờ. Task có thể vẫn đang chạy.
    recordTimeout(ex);
    return fallback();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt(); // giữ yêu cầu dừng của caller
    throw new IllegalStateException("Interrupted while waiting", ex);
} catch (ExecutionException ex) {
    Throwable cause = ex.getCause(); // lỗi thật từ task
    throw new IllegalStateException("Task failed", cause);
} catch (CancellationException ex) {
    throw new IllegalStateException("Task was cancelled", ex);
}
```

`recordTimeout(...)`, `fallback()` và `Result` là thành phần nghiệp vụ cần tự triển khai; nếu không cần fallback, có thể chuyển timeout thành lỗi có kiểm soát. `CancellationException` là unchecked nên không nằm trong danh sách `throws` của `Future.get`, nhưng vẫn nên tính đến nếu ứng dụng cho phép hủy task. Các `catch` trên không bao phủ lỗi submit task từ executor trước khi nhận được future (ví dụ `TaskRejectedException` của Spring).

### TimeoutException: người chờ hết thời gian

Không được mặc định rằng timeout đồng nghĩa:

- Task chưa từng bắt đầu. Nó có thể đang nằm trong queue hoặc đã chạy.
- Task chết hoặc bị cancel. Future thường **vẫn pending**.
- Server bên ngoài đã ngừng xử lý request. Một HTTP call đã gửi có thể vẫn đang chạy.
- Có thể retry an toàn ngay. Task cũ có thể vẫn ghi database sau khi caller đã trả fallback.

Khi timeout, cần chọn rõ chiến lược: tiếp tục xử lý nền và lưu kết quả; trả lỗi/fallback trong khi task vẫn chạy; hoặc cố gắng hủy task và giới hạn tài nguyên. Nếu tác vụ có side effect, phải thiết kế idempotency và xác định xử lý kết quả trễ.

### ExecutionException: task đã thất bại

Một task ném `IllegalStateException` có thể khiến `future.get(...)` ném `ExecutionException` bọc lỗi thật. Quan sát `ex.getCause()`, không nhầm nó với timeout. Khi một `CompletableFuture` kết thúc ngoại lệ, `get` cũng đưa thông tin lỗi qua `ExecutionException`, còn `join` thường dùng `CompletionException` (unchecked).

### InterruptedException: thread đang chờ bị yêu cầu dừng

Nếu thread caller bị interrupt trong khi `get`, Java có thể ném `InterruptedException`; trạng thái interrupted thường đã bị xóa khi exception được ném. Nếu không truyền exception lên tầng trên theo hợp đồng, phục hồi bằng:

```java
catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return; // hoặc thoát phương thức theo hợp đồng của caller
}
```

Đây là snippet, không chạy độc lập. Không nuốt interrupt rồi tiếp tục vòng lặp chờ vô hạn. Cũng cần phân biệt **caller bị interrupt** với **worker bị interrupt**: hai thread có vòng đời và ý nghĩa khác nhau.

### CancellationException: task/future bị hủy

Một future bị hủy có thể khiến `get(...)` ném `CancellationException`. `isDone()` vẫn là `true` vì hủy cũng là một trạng thái hoàn tất của future. Nếu cancel đến từ deadline hoặc người dùng, ghi trạng thái hủy một cách tường minh.

## 6. `get(timeout)` không phải `cancel()`

Hai thao tác khác nhau:

| Thao tác | Tác động chính | Điều không được suy diễn |
| --- | --- | --- |
| `get(200, MILLISECONDS)` | Caller chờ kết quả không quá thời hạn định cho lần lấy đó. | Không dừng task, không ngắt worker, không rollback. |
| `future.cancel(false)` | Cố yêu cầu hủy future; với một số implementation có thể ngăn task chưa chạy bắt đầu. | Không buộc tác vụ đang chạy dừng ngay. |
| `future.cancel(true)` | Cố hủy và có thể yêu cầu interrupt worker nếu implementation hỗ trợ. | Không bảo đảm thread dừng, I/O dừng hay side effect được hoàn tác. |
| `future.isCancelled()` | Quan sát trạng thái hủy của future. | Không chứng minh tài nguyên bên ngoài đã sạch. |

Ví dụ Java `ExecutorService.submit(Callable)` thường trả `FutureTask` có khả năng cố interrupt worker nếu `cancel(true)` thành công. Tuy nhiên, `CompletableFuture.cancel(true)` theo hợp đồng của JDK không dùng `mayInterruptIfRunning` để điều khiển xử lý: việc hoàn tất future bằng trạng thái hủy **không bảo đảm ngắt công việc nền**. Một future nhận qua Spring `@Async` còn có lớp proxy/adaptation, nên không nên xây hệ thống dựa trên giả định cancel luôn ngắt một thread cụ thể.

Nếu chọn hủy sau timeout:

```java
try {
    return future.get(500, TimeUnit.MILLISECONDS);
} catch (TimeoutException ex) {
    boolean requested = future.cancel(true);
    // Ghi trạng thái và xử lý side effect có thể đã xảy ra.
    throw new IllegalStateException(
        "Timed out; cancel requested=" + requested, ex);
}
```

Đây là snippet trong phương thức có kiểu trả về phù hợp, không phải chương trình độc lập. **`cancel(true)` là yêu cầu best-effort**, không thay thế client-side request timeout, transaction design, idempotency hoặc kiểm tra trạng thái job.

Với nghiệp vụ có giá trị cao, đôi khi tiếp tục chạy nền và trả `202 Accepted` kèm job ID có thể hợp lý hơn việc tự động cancel, nhưng phải có job status bền vững để người gọi kiểm tra. Ngược lại, tác vụ chỉ để phục vụ request hiện tại có thể nên dừng hợp tác khi caller không cần kết quả nữa.

## 7. Ba loại timeout không nên đánh đồng

### A. Thời gian chờ kết quả của caller

```java
String value = future.get(300, TimeUnit.MILLISECONDS);
```

Giới hạn **một lần lấy kết quả** ở caller. Task có thể đã tốn thời gian nằm trong queue trước đó, và có thể tiếp tục chạy sau khi caller timeout.

### B. Timeout của thao tác I/O bên trong worker

Ví dụ một HTTP client blocking có thể cần connect timeout và response/read timeout; một truy vấn database có thể cần query timeout hoặc transaction timeout phù hợp. Nếu I/O có thể treo lâu, `future.get(300, MILLISECONDS)` chỉ giải phóng caller khỏi **lần chờ ấy**; worker vẫn có thể bị chiếm lâu, làm bão hòa pool.

Cần cấu hình timeout ở **tài nguyên thực sự gây chờ**, đồng thời đặt giới hạn số worker, queue, connection và retry.

### C. Deadline của cả request hoặc cả quy trình

Giả sử một API có ngân sách tổng 1 giây, nhưng phải submit 3 task và lấy từng kết quả. Gọi mỗi `future.get(1, SECONDS)` tuần tự có thể chờ gần 3 giây, chưa tính submit, queue và mã khác.

Nếu cần một **deadline chung**, hãy ghi nhận thời điểm bắt đầu bằng đồng hồ monotonic (`System.nanoTime()`), tính thời gian còn lại trước mỗi lần chờ rồi dùng nó. Không đặt lại full timeout cho mỗi bước. Nếu deadline đã qua, quyết định trả lỗi hoặc fallback thay vì vô tình mở thêm ngân sách.

Mẫu minh họa trong một phương thức có `List<Future<String>> futures`:

```java
long budgetNanos = TimeUnit.MILLISECONDS.toNanos(800);
long deadlineNanos = System.nanoTime() + budgetNanos;

List<String> results = new ArrayList<>();
for (Future<String> future : futures) {
    long remainingNanos = deadlineNanos - System.nanoTime();
    if (remainingNanos <= 0) {
        throw new TimeoutException("Overall deadline exceeded");
    }
    results.add(future.get(remainingNanos, TimeUnit.NANOSECONDS));
}
```

Mẫu này chỉ minh họa **ngân sách chờ chung**; cần import `List`, `ArrayList`, `Future`, `TimeUnit`, `TimeoutException` và xử lý checked exceptions. Nếu deadline phải bao gồm cả thời gian khởi tạo/submit, hãy tính `deadlineNanos` **trước** các thao tác đó. Khoảng deadline dùng trong ví dụ đủ nhỏ so với giới hạn đo của `nanoTime`; với thời hạn cực lớn, cần xử lý phép tính có thể tràn. Sau khi hết hạn, các future chưa hoàn tất vẫn có thể tiếp tục chạy.

`System.nanoTime()` dùng để đo khoảng thời gian, phù hợp hơn `System.currentTimeMillis()` cho deadline nội bộ vì clock wall time có thể bị điều chỉnh. Còn deadline truyền qua hệ thống phân tán cần hợp đồng rõ về múi giờ, clock skew và thời điểm hết hạn.

## 8. `CompletableFuture` có những lựa chọn khác nào?

`CompletableFuture<T>` triển khai `Future<T>` nên có `get(timeout, unit)`, nhưng còn hỗ trợ composition:

| API | Hành vi | Tác động lên future |
| --- | --- | --- |
| `future.get(timeout, unit)` | Caller chờ có giới hạn rồi lấy kết quả hoặc ném lỗi. | Không chủ động hoàn tất/hủy future khi hết hạn. |
| `future.get()` | Caller chờ không đặt hạn mức riêng. | Không chủ động hủy. |
| `future.join()` | Caller chờ tương tự `get()` không giới hạn nhưng lỗi theo dạng unchecked như `CompletionException`. | Không có tham số timeout. |
| `future.orTimeout(timeout, unit)` | Lên lịch hoàn tất future **bằng `TimeoutException`** nếu nó vẫn chưa hoàn tất khi hết thời hạn. | Thay trạng thái hoàn tất của chính `CompletableFuture` và trả cùng future; task nền có thể vẫn chạy. |
| `future.completeOnTimeout(value, timeout, unit)` | Lên lịch hoàn tất bằng giá trị dự phòng nếu còn pending khi hết hạn. | Cũng thay completion của chính future; không tự dừng worker. |
| `future.thenApply(...)` | Biến đổi giá trị sau khi hoàn tất. | Tạo dependent stage, tránh chờ trực tiếp trên caller. |
| `future.whenComplete(...)` | Quan sát thành công hoặc lỗi. | Có thể dùng để log/metrics; không tự tạo phương thức hủy. |

Ví dụ:

```java
CompletableFuture<String> result = service.createReport("B")
    .orTimeout(2, TimeUnit.SECONDS);
```

Đây là snippet dùng `ReportService` có trong ví dụ chính nhưng không độc lập; `orTimeout` có thể làm future kết thúc bằng lỗi timeout, khiến lần `get` sau ném `ExecutionException` với nguyên nhân `TimeoutException`, trong khi `join` thường ném `CompletionException`. Sự hoàn tất bằng timeout có thể xảy ra **trước** khi phương thức worker thật kết thúc; hoàn tất future không đồng nghĩa đã giải phóng mọi thread.

**Chọn API theo ý định:** `get(timeout)` phù hợp khi *một caller cụ thể* chỉ được chờ chừng đó; `orTimeout` phù hợp khi muốn chính completion stage có chính sách timeout. Nếu một future được nhiều consumer dùng chung, việc gọi `orTimeout` hoặc `completeOnTimeout` trên nó thay đổi kết quả mà mọi consumer cùng thấy. Nếu muốn giới hạn thời gian của một nhánh mà giữ future gốc, cần thiết kế một stage/adapter khác có hợp đồng riêng và cân nhắc side effect.

## 9. Vấn đề thực tế trong Spring MVC và background jobs

Một controller Servlet gọi:

```java
String report = reportService.createReport("X")
    .get(500, TimeUnit.MILLISECONDS);
```

đang **chặn request thread** trong tối đa thời hạn chờ. Nếu quá tải, hàng trăm HTTP request cùng chờ có thể giữ rất nhiều servlet threads; dù task nằm ở executor khác, HTTP capacity vẫn bị ảnh hưởng. Đoạn trên là snippet trong controller có xử lý ngoại lệ, không phải chương trình độc lập.

Nếu muốn cho Spring MVC quản lý vòng đời HTTP request bất đồng bộ, có thể dùng các kiểu trả về được hỗ trợ như `Callable<T>`, `DeferredResult<T>` hoặc `CompletionStage<T>`/`CompletableFuture<T>` theo phiên bản và cấu hình. Lúc đó timeout của async HTTP request là một **lớp chính sách khác**; cần xét trạng thái của task nền, response timeout và client disconnect. Việc trả `Future` cho MVC cũng không tự làm I/O bên trong worker non-blocking.

Với background job, caller có thể không cần kết quả ngay. Dùng `Future.get` có timeout chỉ để trả một response tạm thời có thể dẫn tới **kết quả đến muộn**. Nếu job tạo hiệu ứng phụ, nên ghi nhận `jobId`, status, thời điểm hết hạn, idempotency key, và nơi lưu kết quả. Một thread pool cùng queue trong RAM không bảo đảm job sống sót qua restart.

Ví dụ workflow:

```text
POST /reports
   -> kiểm tra idempotency key
   -> lưu job record (PENDING)
   -> gửi task/queue
   -> trả job ID (202 Accepted)

GET /reports/{jobId}
   -> đọc trạng thái PENDING/RUNNING/SUCCEEDED/FAILED
```

Đây là **sơ đồ kiến trúc**, không phải mã API được triển khai trong ví dụ. Nó minh họa lựa chọn khác khi business không yêu cầu caller chờ kết quả đồng bộ.

## 10. Thiết kế timeout hợp lý và các lỗi phổ biến

### Chọn ngân sách theo toàn đường đi

Khi có request deadline, phân bổ cho queue wait, connect, read, xử lý nghiệp vụ, serialization và overhead. Ví dụ client HTTP đặt deadline 1 giây nhưng server gọi `future.get(5, SECONDS)` có thể tiếp tục chờ sau khi client đã bỏ cuộc; cần thiết kế hạn mức ở từng tầng phù hợp.

Đừng chọn `get(timeout)` dài hơn mọi lớp timeout khác mà không có lý do. Ngược lại, timeout quá ngắn có thể gây fallback hoặc retry không cần thiết trong lúc worker sắp hoàn thành; đo p95/p99 và tỷ lệ false timeout để điều chỉnh.

### Tránh timeout lặp nhiều lần thành vô hạn

Mẫu sai:

```java
while (true) {
    try {
        return future.get(100, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ignored) {
        // Lặp mãi: mỗi lần tự cấp thêm 100 ms chờ.
    }
}
```

Nếu không có deadline chung hoặc điều kiện dừng, caller có thể chờ vô hạn dù mỗi lần `get` có timeout. Polling định kỳ chỉ có giá trị khi có mục đích cụ thể như cập nhật tiến độ và có deadline tổng.

### Không thay thế kiểm soát tài nguyên bằng timeout ở caller

Nếu worker treo do HTTP client không timeout, caller bỏ chờ không giải phóng worker. Hệ thống có thể tiếp tục đầy pool và queue. Đặt giới hạn executor, timeout của I/O, retry có ngân sách và quan sát health của downstream. Với `CallerRunsPolicy`, khi bão hòa task có thể chạy ngay trên caller, vì vậy **ngay cả lúc submit** cũng có thể gây chặn trước khi có future.

### Không bỏ qua exception và interrupt

Bắt chung `Exception` rồi trả về `null` khiến người gọi không biết đó là timeout, hủy hay lỗi nghiệp vụ. Đặc biệt, `InterruptedException` thường cần được truyền đi hoặc phục hồi cờ interrupt để shutdown/cancel ở tầng trên còn tác dụng. Chọn fallback cũng cần thể hiện “không có kết quả đúng hạn”, tránh coi nó là dữ liệu thành công có thể dùng để ghi database.

### Không coi `isDone()` là “task thành công”

`future.isDone()` trả `true` cả khi bị hủy hoặc thất bại. Hãy thử lấy kết quả theo hợp đồng, quan sát ngoại lệ và trạng thái tương ứng. `isCancelled()` cũng không xác nhận tác vụ đã dừng mọi side effect.

### Không dựa vào `Thread.sleep` để chứng minh timeout

Nếu ví dụ dùng `Thread.sleep(200)` và `get(100, MILLISECONDS)`, sự chậm trễ của scheduler hoặc tài nguyên môi trường có thể làm quan sát không ổn định. Ví dụ trong bài dùng `CountDownLatch` để chứng minh có kiểm soát. Với test sản phẩm, cân nhắc test double, đồng hồ điều khiển được hoặc latch/phaser thay vì chờ may rủi.

## 11. Checklist khi đọc code `get(timeout)` trong dự án

1. **Future đến từ đâu?** `ExecutorService.submit`, Spring `AsyncTaskExecutor`, `@Async` proxy, hay API khác? Executor có thể chạy đồng bộ và có chính sách từ chối gì?
2. **Ai đang gọi `get`?** Thread Servlet, `main`, worker của pool hay callback? Nó có được phép block và chịu được bao lâu?
3. **Thời hạn bắt đầu khi nào?** Tính từ lúc gọi `get` hay từ deadline toàn request? Có thời gian queue đã mất trước đó chưa?
4. **Nếu chờ hết hạn, task đi đâu?** Tiếp tục chạy, bị cancel best-effort, ghi kết quả trễ hay để lại side effect?
5. **Lỗi được phân biệt chưa?** Timeout, task failure, interrupt, cancel, reject khi submit đều có đường xử lý rõ ràng?
6. **I/O thật có timeout chưa?** HTTP/database/lock có thể giữ worker dù caller đã dừng chờ không?
7. **Có nhiều bước tuần tự không?** Dùng ngân sách deadline chung để tránh cộng dồn nhiều lần `get(timeout)`.
8. **Có thể bỏ chặn caller không?** Trong đường HTTP hoặc pipeline dài, có thể dùng composition, Spring MVC async, job status hoặc queue bền vững thay vì chờ trực tiếp.
9. **Có khả năng quan sát không?** Đo queue wait, task duration, count timeout, exception cause, cancellation và số worker bị chiếm; log cần correlation ID và cleanup khi truyền qua thread.
10. **Tính đúng đắn khi kết quả đến muộn?** Tác vụ cũ có thể tiếp tục update dữ liệu sau khi caller retry; dùng idempotency, version/transaction và chính sách giải quyết trùng lặp.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `future.get(300, MILLISECONDS)` ném `TimeoutException`. Future có nhất thiết đã chuyển thành cancelled không?

**Đáp án:** Không. Đây là hết thời gian chờ **của caller**. Future có thể vẫn pending và worker vẫn chạy. Có thể thử lại `get` sau đó hoặc chọn chiến lược hủy riêng.

**Câu 2.** Một task đã được submit 2 giây trước, caller bây giờ mới gọi `get(100, MILLISECONDS)`. Điều này đặt deadline task tại thời điểm submit + 100 ms không?

**Đáp án:** Không. Giới hạn áp dụng từ khi thực hiện lần `get` này; nếu task đã hoàn tất, `get` trả ngay.

**Câu 3.** Vì sao `future.get(1, SECONDS)` ba lần tuần tự không tự tạo deadline chung 1 giây?

**Đáp án:** Mỗi lần chờ có ngân sách riêng; thời gian chờ có thể cộng dồn. Cần lưu deadline chung bằng đồng hồ monotonic và chỉ truyền thời gian còn lại cho các lần `get` tiếp theo.

**Câu 4.** `get` ném `ExecutionException`. Đây có phải một lần timeout không?

**Đáp án:** Không. Task đã hoàn tất bằng lỗi; hãy xét `ex.getCause()`. Timeout của `get` có ngoại lệ riêng `TimeoutException`.

**Câu 5.** Caller bị interrupt khi đang chờ `Future.get`. Có thể bắt `InterruptedException` rồi tiếp tục chờ mãi không?

**Đáp án:** Không nên. Hãy truyền signal lên hoặc phục hồi interrupt flag và thoát theo hợp đồng để tác vụ shutdown/cancel không bị bỏ qua.

**Câu 6.** `future.cancel(true)` có bảo đảm worker dừng và HTTP request bên ngoài được hủy không?

**Đáp án:** Không. Khả năng interrupt phụ thuộc loại future/executor và hợp tác của tác vụ; `CompletableFuture.cancel(true)` không nhất thiết ngắt computation.

**Câu 7.** Trong demo, tại sao lần `get(5, SECONDS)` thứ hai trả được `report:A` sau khi lần đầu đã timeout?

**Đáp án:** Lần timeout đầu không hủy future. Khi main mở chốt, worker hoàn thành và Spring hoàn tất handle của caller, nên lần `get` sau nhận kết quả.

**Câu 8.** Spring MVC controller gọi `@Async` service rồi `get(500, MILLISECONDS)` ngay. Controller thread có được giải phóng để xử lý request khác trong lúc chờ không?

**Đáp án:** Không. Chính controller thread bị chặn bởi `get`; muốn giải phóng nó cần dùng hợp đồng request async của MVC hoặc thiết kế API khác.

### Ghi nhớ

`Future.get(timeout, unit)` là **giới hạn cho một lần caller chờ kết quả**, không phải công cụ tự dừng tác vụ. Thiết kế đúng phải làm rõ **caller được chờ bao lâu, worker có thể chạy bao lâu, khi nào cần hủy, khi nào cho phép kết quả muộn, và lỗi/side effect được xử lý thế nào**. Trong Spring, luôn xét cả executor, proxy `@Async`, vòng đời HTTP và tài nguyên ở bên dưới.

### Tài liệu tham khảo chính thức

- [Java 17 — Future API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get(timeout, unit)`, trạng thái và ngoại lệ.
- [Java 17 — CompletableFuture API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `orTimeout`, `completeOnTimeout`, `join`, `cancel`.
- [Java 17 — TimeUnit API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TimeUnit.html): đơn vị thời gian và chuyển đổi.
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `TaskExecutor`, `@EnableAsync`, `@Async` và executor qualification.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-submission-005">Khác biệt đường truyền ngoại lệ giữa execute() và submit()</a>

<details>
<summary>Xem chi tiết</summary>

# Khác biệt đường truyền ngoại lệ giữa execute() và submit()

> **Chủ đề:** Spring Framework – Concurrency / Gửi và nhận tác vụ.  
> **Ý chính:** Với executor chạy trên worker thread, `execute(Runnable)` có thể để ngoại lệ không được bắt thoát ra khỏi `Runnable.run()` và đi qua cơ chế xử lý exception của worker. Trong khi đó, `submit(...)` của `ExecutorService` thông dụng bọc công việc thành một `FutureTask`, giữ ngoại lệ trong `Future` và cho bên gọi lấy lại qua `Future.get()` dưới dạng `ExecutionException`. Tuy nhiên, cả hai đều có thể ném **lỗi nộp việc** ngay tại caller; và một executor chạy đồng bộ hay dùng `CallerRunsPolicy` có thể thay đổi đường truyền lỗi.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt ngoại lệ **trong tác vụ** với ngoại lệ **khi nộp tác vụ**.
2. Nêu hợp đồng của `Executor.execute(Runnable)`, `ExecutorService.submit(Runnable)`, `submit(Callable<T>)` và vai trò của `Future`.
3. Giải thích cơ chế `FutureTask`, `ExecutionException.getCause()`, `UncaughtExceptionHandler` và `ThreadPoolExecutor.afterExecute`.
4. Dự đoán khi nào `try/catch` ở caller bắt được exception và khi nào không.
5. Phân tích các góc cạnh trong Spring `ThreadPoolTaskExecutor`, `TaskDecorator`, `@Async void` và `@Async Future/CompletableFuture`.
6. Viết ví dụ Java 17 chạy được, đọc stdout đáng tin và thiết kế error handling, timeout/cancellation/rejection nhất quán.

## 2. Đầu tiên: có hai thời điểm lỗi rất khác nhau

Một lời gọi executor thường liên quan hai giai đoạn:

~~~text
Caller
   |
   +-- execute(task) / submit(task)
   |      |
   |      +-- Giai đoạn 1: nhận hoặc từ chối task
   |                |
   |                +-- rejection -> exception ngay trong caller
   |
   +-- Giai đoạn 2: chạy task (worker, hoặc đôi khi caller)
          |
          +-- code của Runnable/Callable thành công
          |
          +-- code của Runnable/Callable ném exception
                 |
                 +-- Runnable thô: có thể thành uncaught của worker
                 |
                 +-- FutureTask: lưu exception, đọc từ Future.get()
~~~

**Lỗi nộp (submission failure):** executor không thể nhận thêm công việc do shutdown, bão hòa, sai đối số hoặc lỗi được phát hiện ngay khi dispatch. `RejectedExecutionException` của JDK, hoặc `TaskRejectedException` qua abstraction Spring, có thể được ném ngay tại lời gọi. Một catch bao quanh `execute/submit` có cơ hội bắt loại lỗi này.

**Lỗi thực thi (task failure):** công việc đã được nhận, sau đó code trong `run()/call()` ném lỗi (ví dụ `IllegalStateException`, lỗi mạng, lỗi dữ liệu). Với tác vụ chạy trên worker, đây là **call stack khác**. Nó không tự nhảy ngược về catch đã rời khỏi caller.

Cần giữ riêng hai loại lỗi trong mọi thiết kế logging, API response, retry và quan sát trạng thái. `submit` trả `Future` chỉ khi phương thức nộp công việc trả về thành công; không phải mọi lỗi rejection đều được “biến thành Future bị lỗi”.

## 3. execute(Runnable): không có kênh kết quả mặc định

Giao diện `java.util.concurrent.Executor` rất nhỏ:

~~~java
public interface Executor {
    void execute(Runnable command);
}
~~~

Phương thức trả kiểu `void`, nên chính lời gọi này **không có `Future`** để chờ, hủy hay đọc kết quả. Với `ThreadPoolExecutor` chuẩn khi task là `Runnable` thô:

- Caller có thể trả về sau khi pool nhận task.
- Worker gọi `Runnable.run()`.
- Nếu `run()` ném một `RuntimeException`/`Error` không được bắt, task kết thúc bất thường. Hook `afterExecute(...)` có thể nhận exception; cơ chế `Thread.UncaughtExceptionHandler` của worker cũng có thể xử lý.
- Vì lỗi xuất hiện ở worker, catch trong caller thông thường **không bắt được**; một số môi trường in stack trace trên stderr, nhưng đó không phải bảo đảm báo lỗi nghiệp vụ cho caller.
- Thread worker có thể kết thúc và được pool thay thế; không được đánh đồng “một task chết vì lỗi” với “toàn bộ pool bị hỏng”.

Một ví dụ code dễ hiểu sai:

~~~java
try {
    pool.execute(() -> {
        throw new IllegalStateException("inside worker");
    });
} catch (IllegalStateException ex) {
    // Không mặc nhiên bắt được lỗi của worker ở đây.
}
~~~

Đây là **snippet**: `pool` phải là biến đã khai báo và khởi tạo.

**Ngoại lệ:** Một `Executor` có thể chạy thẳng `command.run()` trong cùng caller (ví dụ direct executor/`SyncTaskExecutor` của Spring). Ngoài ra, khi pool bão hòa, `CallerRunsPolicy` có thể chạy task ngay trong caller. Với các trường hợp đó, lỗi của `Runnable` thô có thể ném trực tiếp qua `execute`. Không thể phát biểu tuyệt đối “execute không bao giờ ném lỗi task cho caller”.

## 4. submit(...): FutureTask thay đổi đường đi của exception

`ExecutorService` thêm API `submit`:

~~~java
Future<?> submit(Runnable task);
<T> Future<T> submit(Callable<T> task);
<T> Future<T> submit(Runnable task, T result);
~~~

Với triển khai phổ biến dựa trên `AbstractExecutorService`:

1. `submit` tạo wrapper tương tự `FutureTask` để theo dõi trạng thái.
2. Executor được yêu cầu chạy wrapper (thường bằng cơ chế `execute(...)` ở tầng dưới).
3. Khi task ném exception, `FutureTask.run()` **bắt và lưu** exception vào trạng thái thất bại của Future; exception không thoát khỏi `run()` của wrapper như một `Runnable` thô.
4. `Future.get()` bên caller chờ tới khi có kết quả. Nếu task thất bại, `get()` ném `ExecutionException`, và `getCause()` chứa exception gốc.
5. Nếu `Runnable` thành công và bạn gọi `submit(Runnable)` không chỉ định result, `get()` trả `null`; nếu dùng `Callable<T>`, `get()` trả `T` do task tính.

~~~text
Caller                                 Worker
  |                                      |
  +-- submit(task)                       |
  |     +-- FutureTask(task) ------------+-- FutureTask.run()
  |     +-- return Future                 |    +-- gọi task
  |                                      |    +-- task throw
  |                                      |    +-- FutureTask giữ cause
  |                                      |
  +-- future.get() ----------------------+-- Future hoàn thành
          |
          +-- ExecutionException
                |
                +-- getCause() -> lỗi thực từ task
~~~

“Submit nuốt mất exception” là cách nói không chính xác. **Lỗi còn trong Future**, nhưng khi ứng dụng bỏ quên `Future` và không có observer khác thì lỗi có thể trở thành **vô hình về mặt vận hành**.

## 5. Bảng so sánh cách nhận lỗi

| Tiêu chí | `execute(Runnable)` | `submit(Runnable/Callable)` |
| --- | --- | --- |
| Interface gốc | `Executor` | `ExecutorService`; trong Spring có các executor hỗ trợ `submit` |
| Giá trị trả về | `void` | `Future<?>` hoặc `Future<T>` |
| Kết quả task | Không có handle do method trả về | `Future.get()`: `T`, kết quả định trước hoặc `null` |
| Exception task trên worker pool thông dụng | Thường thoát khỏi `Runnable.run()` → hook/uncaught handler | Thường được wrapper `FutureTask` ghi nhận |
| Cách bên gọi biết task đã thất bại | Chủ động thiết kế callback/error channel | Gọi `Future.get()`/dùng completion observer |
| Exception ở caller khi nộp task | Có thể ném rejection | Cũng có thể ném rejection trước khi có Future |
| Caller tự bắt exception logic task? | Có thể nếu task thực thi trên caller; thường không nếu ở worker | Thường phải lấy lỗi từ Future, ngay cả khi `CallerRunsPolicy` chạy wrapper trên caller |
| Hủy công việc | Method không trả handle hủy | `Future.cancel(...)` có khả năng hủy ở mức best-effort |
| Bảo đảm task chạy thread khác? | Không | Cũng không |

Hai cột mô tả triển khai phổ biến, không bao trùm mọi executor tự viết. Chú ý **kiểu đối tượng thực sự được executor chạy**: một `FutureTask` được đưa vào qua `execute` cũng giữ exception của logic gốc!

## 6. Java 17 độc lập: thấy đúng cả hai đường exception

Ví dụ chạy thực tế này dùng một worker và `ThreadFactory` đặt custom `UncaughtExceptionHandler`, để không phải phụ thuộc stack trace mặc định trên stderr. `CountDownLatch` giữ thứ tự output, `Future.get(timeout)` bảo đảm không đọc kết quả sớm.

Tạo **`ExecuteSubmitExceptionDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecuteSubmitExceptionDemo {

    public static void main(String[] args) throws Exception {
        CountDownLatch handlerDone = new CountDownLatch(1);
        AtomicInteger uncaughtEvents = new AtomicInteger();

        ThreadFactory factory = work -> {
            Thread thread = new Thread(work, "demo-worker");
            thread.setUncaughtExceptionHandler((t, ex) -> {
                uncaughtEvents.incrementAndGet();
                System.out.println("uncaught = "
                        + ex.getClass().getSimpleName()
                        + ": " + ex.getMessage());
                handlerDone.countDown();
            });
            return thread;
        };

        ExecutorService pool =
                Executors.newSingleThreadExecutor(factory);
        try {
            pool.execute(() -> {
                throw new IllegalStateException("execute-fail");
            });

            if (!handlerDone.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("handler timed out");
            }
            System.out.println("execute has Future = false");

            Future<?> failed = pool.submit(() -> {
                throw new IllegalArgumentException("submit-fail");
            });

            try {
                failed.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                System.out.println("submit cause = "
                        + cause.getClass().getSimpleName()
                        + ": " + cause.getMessage());
            }

            Future<String> success = pool.submit(() -> "OK");
            System.out.println("callable result = "
                    + success.get(5, TimeUnit.SECONDS));

            Future<?> done = pool.submit(() -> { });
            System.out.println("runnable result = "
                    + done.get(5, TimeUnit.SECONDS));

            // Dùng execute nhưng đưa vào một FutureTask.
            FutureTask<Integer> wrapped = new FutureTask<>(() -> {
                throw new IllegalArgumentException("wrapped-fail");
            });
            pool.execute(wrapped);
            try {
                wrapped.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                System.out.println("wrapped cause = "
                        + ex.getCause().getMessage());
            }
            System.out.println("uncaught events = "
                    + uncaughtEvents.get());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac ExecuteSubmitExceptionDemo.java
java ExecuteSubmitExceptionDemo
~~~

**Stdout dự kiến chính xác khi chạy thành công:**

~~~text
uncaught = IllegalStateException: execute-fail
execute has Future = false
submit cause = IllegalArgumentException: submit-fail
callable result = OK
runnable result = null
wrapped cause = wrapped-fail
uncaught events = 1
~~~

Giải thích:

- Lỗi đầu tiên thoát khỏi `Runnable.run()` thô do `execute` dispatch; custom worker handler in `uncaught` và tăng bộ đếm.
- `handlerDone.await()` không cho main in dòng tiếp theo trước khi handler thực hiện.
- Lỗi từ `submit` được đưa qua `Future.get()` rồi mới in, không tăng số uncaught event.
- `submit(Callable<String>)` thành công trả `OK`. `submit(Runnable)` thành công trả `null`.
- `FutureTask` được đưa qua chính **`execute`** vẫn giữ exception: chứng minh wrapper quan trọng hơn tên method.
- `newSingleThreadExecutor` có thể thay worker bị lỗi, và các task tiếp theo vẫn xử lý bình thường. `shutdown/awaitTermination` dọn tài nguyên.

Đây là **chương trình chạy độc lập**, không cần Maven/Spring. Không dùng `Thread.sleep` để tạo một lịch thực thi may rủi.

## 7. Một bẫy quan trọng: execute(FutureTask) không giống execute(Runnable) thô

Mã sau:

~~~java
FutureTask<Integer> wrapped = new FutureTask<>(() -> {
    throw new IllegalStateException("boom");
});
pool.execute(wrapped);
wrapped.get();
~~~

là một **snippet minh họa**, cần `pool`, import `FutureTask` và xử lý checked exceptions.

Nếu `wrapped` là FutureTask, lỗi gốc bị bắt trong `FutureTask.run()`, **không** làm worker kết thúc bất thường theo cách của `Runnable` thô. `wrapped.get()` ném `ExecutionException` chứa `IllegalStateException`.

Điều này làm rõ một điểm sâu hơn: `submit` điển hình cũng sẽ gọi tầng thực thi với một wrapper chứa trạng thái kết quả. **Cùng một executor và cùng một lỗi có thể đi theo hai kênh quan sát chỉ vì một bên bọc công việc vào FutureTask**. Vì vậy, khi debug không chỉ đọc `execute/submit` ở dòng gọi; cần biết object thực sự nằm trong queue.

## 8. Vì sao try/catch đặt ở caller thường không hoạt động?

~~~java
try {
    executor.execute(() -> {
        throw new IllegalStateException("worker failed");
    });
} catch (IllegalStateException ex) {
    // Không mặc nhiên vào đây nếu task chạy trên worker khác.
}
~~~

Đây không phải class chạy độc lập vì `executor` chưa được khởi tạo.

`try/catch` chạy trong call stack của caller. Task trên worker có call stack của worker, nên exception không thể “nhảy thread” đến catch của caller nếu không có cơ chế truyền tín hiệu/result rõ ràng.

Tuy nhiên, catch trên caller **có thể** bắt lỗi khi:

1. Executor đồng bộ gọi `Runnable.run()` trực tiếp trong caller, ví dụ `SyncTaskExecutor`.
2. Pool bão hòa với `CallerRunsPolicy` và handler tự gọi `run()` của `Runnable` thô ngay trên caller.
3. Phần code nộp task/đánh giá decorator ném exception đồng bộ trước khi task được dispatch.
4. Executor từ chối tác vụ với exception khi nó đã shutdown hoặc full.

Vì thế, câu “`execute` luôn cho lỗi lên caller” và câu “`execute` không bao giờ cho lỗi lên caller” **đều sai** nếu không nói rõ implementation và nơi chạy task.

## 9. Lỗi nộp việc: cả execute và submit đều có thể ném ngay

Với `ExecutorService` đã shutdown:

~~~java
ExecutorService executor = Executors.newSingleThreadExecutor();
executor.shutdown();

try {
    executor.submit(() -> System.out.println("never runs"));
} catch (java.util.concurrent.RejectedExecutionException ex) {
    System.out.println("submission rejected on caller");
}
~~~

Đoạn này có thể đặt trong một method có import `ExecutorService`/`Executors`. Trong implementation JDK thông dụng dùng `AbortPolicy`, exception xảy ra **ở lời gọi `submit`**, không phải một lỗi task được giữ trong Future. Khi `submit` ném exception như vậy, nó không trả về Future thành công để caller lấy kết quả.

### 9.1. Rejection policy có thể đổi đường truyền lỗi

| Policy của `ThreadPoolExecutor` | `execute(Runnable)` thô khi full | `submit(...)` (wrapper FutureTask) khi full |
| --- | --- | --- |
| `AbortPolicy` | Ném `RejectedExecutionException` tại caller | Ném rejection tại caller, không trả Future |
| `CallerRunsPolicy`, chưa shutdown | Caller thực thi `Runnable.run()`; lỗi có thể ném trực tiếp | Caller chạy FutureTask; task exception thường được lưu cho `get()` |
| `DiscardPolicy` | Task có thể bị bỏ mà caller không biết | Có thể nhận Future **không bao giờ hoàn thành**, nếu wrapper bị bỏ |
| `DiscardOldestPolicy` | Bỏ task ở đầu queue để thử enqueue task mới | Future của task bị loại khỏi queue có thể không hoàn thành nếu không có cancel bổ sung |

Không nên chọn các policy silent discard cho nghiệp vụ yêu cầu xác nhận thành công/thất bại nếu chưa có cơ chế hủy, bù hoặc quan sát riêng. Với `CallerRunsPolicy`, một request HTTP có thể bất ngờ phải chạy task trên request thread, gây tăng latency. Với `submit`, chính FutureTask vẫn giữ lỗi ngay cả nếu việc chạy xảy ra trong caller.

### 9.2. TaskRejectedException trong Spring

Spring `ThreadPoolTaskExecutor` dùng abstraction `TaskExecutor` và thường chuyển rejection của executor nền thành `org.springframework.core.task.TaskRejectedException`:

~~~java
import org.springframework.core.task.TaskRejectedException;

try {
    reportExecutor.execute(job);
} catch (TaskRejectedException ex) {
    // Thực thi chính sách quá tải: fail fast, retry có giới hạn,
    // trả lỗi thích hợp hoặc giao cho job store bền vững.
}
~~~

Đây là **snippet**, cần `reportExecutor`/`job` đã được khai báo. `TaskRejectedException` là **lỗi tại submission**, không phải `ExecutionException` bên trong `Future.get()`. Khi sử dụng `submit`, code cần xử lý **cả rejection ngay lúc gọi** và **failure đọc từ Future sau đó**.

## 10. Future.get: đọc lỗi, kết quả, timeout, cancel đúng cách

### 10.1. ExecutionException và nguyên nhân gốc

`Future.get()` có thể chờ cho đến khi task hoàn thành. Nếu task thất bại, `get()` ném một `ExecutionException` và `getCause()` trả exception logic gốc. Ví dụ:

~~~text
Future.get()
   |
   +-- ExecutionException
           |
           +-- getCause()
                   |
                   +-- IllegalArgumentException("bad input")
~~~

Một snippet sử dụng timeout:

~~~java
try {
    String answer = future.get(3, TimeUnit.SECONDS);
    return answer;
} catch (ExecutionException ex) {
    Throwable cause = ex.getCause();
    throw new IllegalStateException("task failed", cause);
} catch (TimeoutException ex) {
    future.cancel(true);
    throw new IllegalStateException("task timed out", ex);
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    future.cancel(true);
    throw new IllegalStateException("wait interrupted", ex);
} catch (CancellationException ex) {
    throw new IllegalStateException("task cancelled", ex);
}
~~~

Đây là **method-body snippet**, cần `Future<String> future` và imports các exception tương ứng. Nó là một chính sách minh họa, không phải bắt buộc luôn phải cancel khi timeout.

Quan trọng:

- `get(timeout)` ném `TimeoutException` nghĩa là **caller không đợi thêm**; task có thể vẫn chạy.
- `cancel(true)` chỉ **cố gắng** hủy, thường qua interrupt; nếu task đang làm blocking I/O không hỗ trợ interrupt, nó vẫn có thể còn chạy.
- Khi đón `InterruptedException`, thường phải khôi phục interrupt flag hoặc truyền tiếp exception phù hợp.
- `CancellationException` khác với business exception và khác lỗi rejection.
- `Future.isDone() == true` có thể là thành công, thất bại hoặc đã cancel. Không thể chỉ nhìn isDone để kết luận tác vụ thành công.

### 10.2. Runnable thành công có get() == null

~~~java
Future<?> f = executor.submit(() -> doSideEffect());
Object value = f.get(); // null khi Runnable hoàn thành bình thường.
~~~

Đây là snippet với `executor` và `doSideEffect()` được khai báo ở ứng dụng. `null` là completion value đúng của overload `submit(Runnable)`, không phải dấu hiệu lỗi.

### 10.3. Đừng bỏ quên Future

~~~java
executor.submit(() -> {
    sendMessage();
    throw new IllegalStateException("message failed");
});

// Không lưu Future, không get, không callback, không job-status.
~~~

Đoạn trên có thể khiến lỗi bị lưu trong FutureTask và không ai đọc. Nếu nghiệp vụ không cần chặn caller, hãy dùng callback/completion observer hoặc trạng thái job. Nếu công việc cần tiếp tục sau restart/crash, cần message queue/job store bền vững, không chỉ thread pool in-memory.

## 11. afterExecute() và UncaughtExceptionHandler: tại sao hai lỗi nhìn khác nhau?

`ThreadPoolExecutor` có hook:

~~~java
protected void afterExecute(Runnable task, Throwable error);
~~~

Với một `Runnable` thô ném exception không được bắt, `error` có thể là exception đó. Nhưng với `submit(...)`, `FutureTask.run()` bắt và ghi exception vào trạng thái Future, nên `afterExecute(..., error)` **thường nhận `error == null`** dù logic task thất bại.

Javadoc Java SE gợi ý kiểm tra Future wrapper khi đã hoàn thành:

~~~java
@Override
protected void afterExecute(Runnable task, Throwable error) {
    super.afterExecute(task, error);

    if (error == null
            && task instanceof Future<?> future
            && future.isDone()) {
        try {
            future.get();
        } catch (CancellationException ex) {
            error = ex;
        } catch (ExecutionException ex) {
            error = ex.getCause();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    if (error != null) {
        reportFailure(error);
    }
}
~~~

Đây là **method trong một subclass của `ThreadPoolExecutor`**, không độc lập; cần import Future và các exception, cùng phương thức `reportFailure(...)` do ứng dụng tự định nghĩa.

Vì `future.isDone()` true, việc gọi `get()` trong mẫu này không chờ task chưa hoàn thành. Cần phân biệt cancellation hợp lệ với failure nghiệp vụ; các wrapper/decorator tùy biến có thể khiến `task` không còn trực tiếp là `Future`, nên không được coi snippet là một giải pháp universal.

**Thread.UncaughtExceptionHandler** có thể quan sát exception làm worker kết thúc bất thường nhưng thông thường không được gọi cho task failure đã bị `FutureTask` giữ lại. Và ngay cả khi handler log được lỗi, caller vẫn **không tự có kết quả nghiệp vụ**. Phải xây đường phản hồi riêng nếu cần.

## 12. Spring ThreadPoolTaskExecutor: dùng đúng kênh lỗi

Spring Framework cung cấp `ThreadPoolTaskExecutor`, hỗ trợ `execute` và `submit`. Cấu hình bean có thể là:

~~~java
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class ExecutorConfig {
    @Bean("businessExecutor")
    ThreadPoolTaskExecutor businessExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(32);
        pool.setThreadNamePrefix("business-");
        pool.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        return pool; // Container khởi tạo và hủy pool.
    }
}
~~~

Đây là **snippet cấu hình Spring**, chưa phải chương trình độc lập; cần ứng dụng có `spring-context` hoặc starter Spring Boot tương ứng.

- `execute` dùng để gửi một `Runnable`; nếu task thô chạy trên worker và ném lỗi, nó có thể đi theo uncaught/error-observer của worker, không tự trở lại caller.
- `submit` trả `Future` cho phép caller lấy cause bằng `get()`.
- Khi queue/full, chính sách rejection quyết định liệu lỗi xuất hiện ngay lúc submission, chuyển việc sang caller hay bỏ task.
- Một TaskDecorator có thể bọc object thực thi; với `submit` object ấy thường đã là `FutureTask`.

### 12.1. Service sử dụng submit và giữ Future

~~~java
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final ThreadPoolTaskExecutor executor;

    public ReportService(
            @Qualifier("businessExecutor")
            ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    public Future<String> start(String jobId) {
        return executor.submit(() -> runJob(jobId));
    }

    private String runJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("missing jobId");
        }
        return "done:" + jobId;
    }
}
~~~

Đây là **service snippet** cho ứng dụng Spring; không có `main`. Điểm quan trọng không phải chỉ “gọi submit”, mà là caller phải **sở hữu Future**, kiểm tra completion/failure và biến kết quả đó thành trạng thái nghiệp vụ phù hợp.

### 12.2. Ví dụ Spring Context độc lập chạy bằng Maven + Java 17

Dự án nhỏ chứng minh `ThreadPoolTaskExecutor.submit(Callable)` giữ exception, Spring container quản lý lifecycle; không cần Spring Boot hay Web server.

Cấu trúc:

~~~text
spring-exception-demo/
├── pom.xml
└── src/main/java/demo/SpringFutureDemo.java
~~~

Tệp **`pom.xml`**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-exception-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.16</version>
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

Tệp **`src/main/java/demo/SpringFutureDemo.java`**:

~~~java
package demo;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class SpringFutureDemo {

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean("workExecutor")
        ThreadPoolTaskExecutor workExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(1);
            pool.setQueueCapacity(8);
            pool.setThreadNamePrefix("work-");
            pool.setRejectedExecutionHandler(
                    new ThreadPoolExecutor.AbortPolicy());
            return pool;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            ThreadPoolTaskExecutor executor =
                    context.getBean("workExecutor",
                            ThreadPoolTaskExecutor.class);

            Future<String> failed = executor.submit(() -> {
                throw new IllegalStateException("spring-failed");
            });

            try {
                failed.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                System.out.println("spring cause = "
                        + cause.getClass().getSimpleName()
                        + ": " + cause.getMessage());
            }

            Future<Integer> good = executor.submit(() -> 40 + 2);
            System.out.println("spring result = "
                    + good.get(5, TimeUnit.SECONDS));
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.SpringFutureDemo
~~~

**Stdout của class demo** khi chạy thành công (không tính thông báo riêng từ Maven nếu có):

~~~text
spring cause = IllegalStateException: spring-failed
spring result = 42
~~~

`get(5, TimeUnit.SECONDS)` chờ Future hoàn thành; exception gốc được lấy từ `ExecutionException`. Context tự quản lý bean và đóng executor khi đóng context. Đây là đường lỗi Spring tương ứng với `submit`; ví dụ Java 17 ở phần 6 đã minh họa rõ đường lỗi `execute`.

## 13. TaskDecorator của Spring có bẫy với submit

`ThreadPoolTaskExecutor#setTaskDecorator(...)` cho phép bọc callback thực thi để đặt MDC/tracing, đo thời gian hoặc log:

~~~java
executor.setTaskDecorator(original -> () -> {
    try {
        original.run();
    } catch (RuntimeException ex) {
        logger.error("task failed", ex);
        throw ex;
    }
});
~~~

Đây là **snippet**, giả định `executor` và `logger` có sẵn. Với `execute(Runnable)` thô, decorator có thể thấy exception thoát khỏi `run()`. Nhưng trong đường `submit`, `original` thường là `FutureTask` hoặc callback bao quanh nó. `FutureTask.run()` giữ lỗi bên trong, do đó catch trong decorator **có thể không chạy** dù task gốc đã thất bại.

Javadoc Spring có lưu ý chính thức về trường hợp này. Nếu cần quan sát tất cả failure, đừng dựa duy nhất vào `TaskDecorator.catch`. Hãy có chủ sở hữu `Future`/completion observer; hoặc xây một error-monitoring layer hiểu đúng wrapper và tránh block worker.

## 14. @Async void và @Async Future/CompletableFuture

`@Async` là abstraction interception của Spring; exception của một phương thức được `@Async` xử lý có đường đi phụ thuộc **kiểu trả về**. Cần có `@EnableAsync`/cấu hình tương đương và invocation đi **qua proxy**; self-invocation bằng `this.method()` trong proxy mode mặc định không kích hoạt async interception.

### 14.1. @Async void: không có Future cho caller

~~~java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
class MailService {
    @Async("businessExecutor")
    public void send(String recipient) {
        throw new IllegalStateException("mail failed");
    }
}
~~~

Đây là service snippet. Caller không có `Future` để nhận lỗi. Với `@Async void` có interception hợp lệ, Spring dùng `AsyncUncaughtExceptionHandler` để xử lý exception do phương thức ném. Handler mặc định thường log lỗi; ứng dụng có thể đăng ký handler tùy chỉnh.

Đây **không phải** `Thread.UncaughtExceptionHandler` của Java. Không được suy rằng mọi lỗi `@Async void` sẽ làm chết worker hoặc tự xuất hiện trong try/catch controller.

### 14.2. Tùy chỉnh AsyncUncaughtExceptionHandler

~~~java
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncErrorConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (error, method, args) -> {
            System.err.println("async method failure = "
                    + method.getName() + ": " + error.getMessage());
            // Ứng dụng thật: log có correlation ID, alert, job-status.
        };
    }
}
~~~

Đây là **class cấu hình Spring**, không có `main`. Với `@Async("businessExecutor")`, Spring chọn executor được chỉ định theo bean name/qualifier. Cấu hình này xử lý **ngoại lệ của void @Async method**; không thay thế việc caller phải đọc `Future` khi method trả Future.

### 14.3. @Async trả CompletableFuture

~~~java
@Async("businessExecutor")
public CompletableFuture<String> load(String key) {
    if (key.isBlank()) {
        throw new IllegalArgumentException("empty key");
    }
    return CompletableFuture.completedFuture("value:" + key);
}
~~~

Đây là method snippet, cần import `CompletableFuture` và `@Async`. Nếu phương thức được gọi qua Spring proxy:

- Caller nhận `CompletableFuture` để theo dõi việc hoàn thành.
- `future.get()` thất bại với `ExecutionException` có cause.
- `future.join()` thông thường ném `CompletionException` (unchecked) có cause khi thất bại.
- `whenComplete`, `handle` hoặc `exceptionally` cho phép xử lý không chặn.
- Lời gọi từ chính instance không qua proxy sẽ đi theo Java call stack bình thường và không có nghĩa vụ chạy async.

Một observer:

~~~java
service.load(key).whenComplete((value, error) -> {
    if (error != null) {
        logger.error("load failed for key={}", key, error);
    }
});
~~~

Đây là code tích hợp, cần `service`, `key` và `logger`. Callback có thể chạy trên thread hoàn tất future, vì vậy không gán cho nó giả định về `ThreadLocal`/transaction của caller. Một exception trong callback có thể làm completion stage của callback thất bại; phải theo dõi cả đường lỗi đó khi quan trọng.

## 15. CompletableFuture cũng chứng minh wrapper quan trọng

~~~java
CompletableFuture<Integer> f = CompletableFuture.supplyAsync(
        () -> {
            throw new IllegalStateException("calculation failed");
        },
        executor);

f.whenComplete((value, ex) -> {
    if (ex != null) {
        report(ex);
    }
});
~~~

Đây là snippet, giả định `executor` thực thi `Executor` và có hàm `report`.

Ngay cả khi implementation sử dụng `Executor.execute(...)` để dispatch callback nội bộ, `CompletableFuture` có cơ chế ghi trạng thái completion/exception của chính nó. Vì thế, quy tắc “execute luôn ra uncaught handler” là sai khi object thực thi có wrapper bắt lỗi.

Khác biệt cần nhớ:

| API đọc kết quả | Khi thất bại thông thường |
| --- | --- |
| `Future.get()` | `ExecutionException` (checked), `getCause()` chứa nguyên nhân |
| `CompletableFuture.get()` | `ExecutionException` (checked) |
| `CompletableFuture.join()` | `CompletionException` (unchecked), lấy cause để phân loại |
| `whenComplete(...)` | Nhận cả value/error trên completion, không tự block caller |
| `exceptionally(...)` | Có thể thay failure bằng giá trị fallback |
| `handle(...)` | Biến đổi cả success/failure, có thể thay kết quả cuối |

Future/completion không tự bảo đảm chạy khác thread; nếu executor là direct executor, việc chạy có thể diễn ra ngay ở caller.

## 16. Thiết kế xử lý lỗi theo trách nhiệm

| Tình huống | Kênh quan sát phù hợp | Cần chú ý |
| --- | --- | --- |
| `execute` với Runnable thô trên worker | Worker error handler / structured task logging | Caller không tự có kết quả; report failure có job ID |
| `submit` với Callable thất bại | `Future.get()`, completion observer | Đừng bỏ Future; unwrap `ExecutionException` |
| `@Async void` thất bại | `AsyncUncaughtExceptionHandler` | Không có Future trả caller |
| `@Async CompletableFuture` thất bại | Future exceptional / callback | Đừng chặn HTTP request vô hạn |
| Pool từ chối lúc nộp | `RejectedExecutionException` / Spring `TaskRejectedException` | Quyết định fail fast, backpressure, retry hoặc queue bền vững |
| Task bị hủy | `CancellationException` | Hủy chủ động không nhất thiết là business failure |
| Chờ Future quá deadline | `TimeoutException` | Task có thể vẫn chạy sau timeout |
| Thread chờ bị interrupt | `InterruptedException` | Khôi phục interrupt hoặc truyền lên policy |
| FutureTask trong `afterExecute` | Quan sát `Future.get()` nếu đã done | `Throwable == null` không chứng minh task thành công |

Một nguyên tắc thực hành là **một nơi sở hữu trạng thái kết quả**. Nếu worker, decorator, callback, controller và service cùng log một exception, sẽ có log trùng và khó suy nguyên nhân. Chọn nơi cập nhật job status, nơi log và nơi trả lỗi cho khách hàng rõ ràng.

### 16.1. Wrapper log lỗi mà vẫn giữ fail state

~~~java
public static Runnable withErrorReport(
        String jobId,
        Runnable work,
        java.util.function.BiConsumer<String, Throwable> reporter) {
    return () -> {
        try {
            work.run();
        } catch (RuntimeException | Error ex) {
            reporter.accept(jobId, ex);
            throw ex;
        }
    };
}
~~~

Đây là **phương thức có thể đặt trong utility class**. Nó giúp báo lỗi tại nơi task chạy, kể cả khi sau này task được gửi bằng `submit` và bọc thành FutureTask. Cần tránh để `reporter` tự ném lỗi che mất cause gốc; trong production nên thiết kế observer robust, cân nhắc log một lần và cập nhật status.

### 16.2. Lỗi task, giao dịch và context

`@Transactional` thường gắn transaction association vào thread. Nếu task chạy trên worker, transaction của caller không tự đi theo, bất kể dùng `execute` hay `submit`. Tương tự, MDC/tracing, security context hay request context thường cần giải pháp propagation cụ thể. **Lỗi không được gửi về caller** cũng không có nghĩa transaction của caller sẽ tự rollback khi worker ném lỗi.

Nếu nghiệp vụ cần lỗi quyết định transaction/câu trả lời HTTP, phải thiết kế điểm đồng bộ/orchestration và transaction boundary tương ứng, không chỉ thêm một try/catch bên ngoài lệnh submit.

## 17. Kiểm thử đúng đường ngoại lệ

### 17.1. Test submit bằng Future.get

Ví dụ JUnit 5, **snippet** yêu cầu pool fixture, imports và assertions:

~~~java
Future<?> failed = pool.submit(() -> {
    throw new IllegalArgumentException("invalid");
});

ExecutionException ex = assertThrows(
        ExecutionException.class,
        () -> failed.get(3, TimeUnit.SECONDS));

assertEquals(IllegalArgumentException.class,
        ex.getCause().getClass());
assertEquals("invalid", ex.getCause().getMessage());
~~~

Không dùng `Thread.sleep(100)` rồi thử xem log có stack trace không. Với `submit`, lỗi vốn có thể được giữ trong Future và không in ra stderr.

### 17.2. Test execute với handler có đồng bộ

Cấp `ThreadFactory` tùy biến ghi `Throwable` vào `BlockingQueue<Throwable>` hoặc đếm `CountDownLatch` sau khi thấy exception. Test đợi bounded timeout rồi assert loại exception. Ví dụ Java 17 ở phần 6 đã làm chính điều này, nên không phụ thuộc vào thời gian scheduler.

### 17.3. Những đường biên phải kiểm tra

- Lỗi runtime trong `execute(Runnable)` trên worker.
- Lỗi runtime trong `submit(Runnable/Callable)`, kể cả đúng cause sau `Future.get()`.
- `execute(FutureTask)` để xác nhận wrapper giữ lỗi.
- Lỗi rejection khi executor shutdown/full.
- `CallerRunsPolicy` với Runnable thô và FutureTask wrapper.
- `Future.get(timeout)`, cancel, interrupt; task có thể vẫn chạy nếu không phản ứng interrupt.
- Spring `TaskDecorator` với `submit`, `@Async void` có handler, `@Async CompletableFuture`.
- Error logging không trùng, có job ID/correlation ID, không lộ dữ liệu nhạy cảm.
- Dọn executor và Spring context đúng cách sau test.

## 18. Những ngộ nhận thường gặp

**1. “execute luôn ném exception task cho caller.”** Sai: worker có call stack khác. Caller chỉ có thể thấy lỗi synchronous hoặc lỗi nộp việc nếu không có cơ chế result riêng.

**2. “execute không bao giờ ném lỗi.”** Sai: executor đồng bộ, `CallerRunsPolicy` và rejection vẫn có thể ném cho caller.

**3. “submit nuốt mất lỗi mãi mãi.”** Sai: Future giữ cause; cần `get()` hoặc observer. Lỗi thực sự bị bỏ sót khi app không quan sát kết quả.

**4. “submit có Future nghĩa là task đã chạy xong.”** Sai: Future có thể đang pending, trong queue, đã lỗi hoặc bị hủy.

**5. “try/catch quanh submit bắt được IllegalStateException từ worker.”** Không, nếu failure nằm trong task đã được nhận; cần đọc `ExecutionException.getCause()`.

**6. “afterExecute(..., null) nghĩa là thành công.”** Sai với `FutureTask` vì nó có thể giữ lỗi mà không ném ra ngoài `run()`.

**7. “TaskDecorator catch(Exception) bắt được mọi lỗi submit.”** Sai khi wrapper FutureTask đã lưu exception trước đó.

**8. “Future.isDone() là thành công.”** Sai: isDone true với cả normal, exceptional và cancelled completion.

**9. “get(timeout) timeout thì task bị hủy.”** Sai: timeout chỉ chấm dứt việc chờ nếu không chủ động cancel hoặc có cơ chế deadline.

**10. “@Async void sẽ ném lỗi về controller.”** Sai trong proxy async path; Spring gọi `AsyncUncaughtExceptionHandler`.

**11. “Future.get và CompletableFuture.join giống nhau.”** Sai về exception class checked/unchecked.

**12. “Đổi execute thành submit là đủ để giám sát toàn bộ lỗi.”** Sai nếu không giữ/đọc Future và không xử lý lỗi rejection.

**13. “CallerRunsPolicy chỉ giúp hiệu năng, không ảnh hưởng exception.”** Sai: với Runnable thô, task có thể ném ngay trên caller; với FutureTask vẫn lưu lỗi.

**14. “Mọi exception đều nên retry.”** Sai: phân loại lỗi không hợp lệ, lỗi tạm thời, rejection, cancel; retry phải có backoff/idempotency, tránh khuếch đại quá tải.

## 19. Câu hỏi tự kiểm tra

**Câu 1:** `pool.submit(() -> { throw new IllegalStateException("x"); })` trả Future. Khi nào caller biết lỗi?

**Đáp án:** khi gọi `future.get()` (hoặc observer tương đương); với JDK implementation thông dụng, `get` ném `ExecutionException`, cause là `IllegalStateException`.

**Câu 2:** `pool.execute(new FutureTask<>(callable))` có chắc đi qua `UncaughtExceptionHandler` nếu callable lỗi?

**Đáp án:** không. FutureTask lưu exception và có thể kết thúc `run()` bình thường; vẫn cần đọc `FutureTask.get()`.

**Câu 3:** Pool shutdown trước `submit`, caller bắt `RejectedExecutionException`. Đây là lỗi logic bên trong task không?

**Đáp án:** không, đây là lỗi nộp việc. Task không được nhận nên không có Future trả về thành công.

**Câu 4:** Pool đầy với `CallerRunsPolicy`. Vì sao `execute` của một Runnable thô có thể ném ngay, còn `submit` của logic tương đương trả Future lỗi?

**Đáp án:** CallerRunsPolicy chạy `Runnable.run()` ngay trên caller; nhưng trong đường submit, Runnable chạy là FutureTask wrapper có cơ chế ghi exception.

**Câu 5:** Phương thức `@Async public void send()` lỗi. Caller có thể gọi `get()` không?

**Đáp án:** không, vì method trả `void`. Cần `AsyncUncaughtExceptionHandler` hoặc đổi thiết kế API sang `Future/CompletableFuture` nếu caller cần biết kết quả.

**Câu 6:** Vì sao `Future.isDone() == true` không có nghĩa task thành công?

**Đáp án:** trạng thái done gồm cả thành công, thất bại và hủy; cần `get()`/completion mechanism để phân loại.

## 20. Checklist trước khi áp dụng

- [ ] Biết implementation executor, nơi task chạy, có thể caller-runs/synchronous không.
- [ ] Phân biệt exception do task thực thi và exception xảy ra ngay khi nộp việc.
- [ ] Với `execute`, có cơ chế quan sát failure và quản lý task ID nếu nghiệp vụ cần.
- [ ] Với `submit`, có owner rõ ràng của `Future` và nơi đọc/quan sát `get()`.
- [ ] Không dựa duy nhất vào `afterExecute` `Throwable` hoặc `TaskDecorator.catch` để kết luận submit không có lỗi.
- [ ] Bắt `ExecutionException` rồi giữ cause gốc; phân biệt timeout, cancel và interrupt.
- [ ] Không để `get()` chờ vô hạn nếu caller có deadline.
- [ ] Có phản ứng khi pool reject, không retry vô hạn trên request thread.
- [ ] Với `@Async void`, đã cấu hình exception handler; với Future/CompletableFuture có consumer/observer.
- [ ] Có chính sách logging/tracing an toàn, không tạo log trùng.
- [ ] Có cơ chế lưu job bền vững nếu không được mất task qua crash/restart.
- [ ] Đã kiểm thử cả lỗi thực thi, lỗi nộp, cancellation, shutdown và load-saturation.

## 21. Tóm tắt

**`execute(Runnable)`** trả `void`. Với task thô chạy trên worker của `ThreadPoolExecutor`, exception không được bắt có thể tới worker error/uncaught handling, chứ không tự trở lại caller. Tuy nhiên, executor đồng bộ, `CallerRunsPolicy` hoặc rejection tại submission có thể tạo exception ngay ở caller.

**`submit(...)`** trả `Future`. Với triển khai thông dụng, task được bọc bởi `FutureTask`; lỗi task được lưu và đưa tới người gọi khi `get()` ném `ExecutionException` chứa cause gốc. **Bỏ Future mà không có observer** là một lỗi thiết kế giám sát.

Trong Spring, `ThreadPoolTaskExecutor` có cùng bài toán, cộng thêm bẫy `TaskDecorator` và hai đường lỗi khác nhau của `@Async void` so với `@Async CompletableFuture`. Hãy thiết kế kênh kết quả, chủ sở hữu xử lý lỗi, deadline/cancel/rejection rõ ràng thay vì suy luận dựa vào tên phương thức.

## Tài liệu tham khảo chính thức

- Java SE 17 — `Executor`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html
- Java SE 17 — `ExecutorService` (submit, lifecycle): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Java SE 17 — `Future` (get, cancel, isDone): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- Java SE 17 — `FutureTask`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html
- Java SE 17 — `ThreadPoolExecutor` (afterExecute, rejection): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Spring Reference — Task Execution and Scheduling, `@Async` Exception Management: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Javadoc — `ThreadPoolTaskExecutor`: https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Javadoc — `AsyncUncaughtExceptionHandler`: https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/aop/interceptor/AsyncUncaughtExceptionHandler.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-submission-006">Thời điểm gửi tác vụ khác với thời điểm bắt đầu chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Thời điểm gửi tác vụ khác với thời điểm bắt đầu chạy

Trong ứng dụng Spring Framework, khi controller gọi một phương thức `@Async` hoặc service gửi `Runnable` qua `TaskExecutor.execute(...)`, lời gọi gửi tác vụ có thể hoàn tất **trước khi tác vụ bắt đầu thực thi**. Điều đó xảy ra khi executor nhận task vào hàng đợi nhưng chưa có worker rảnh. Ngược lại, một worker rảnh có thể bắt đầu chạy ngay trước khi phương thức gửi kịp trả về; một executor đồng bộ còn có thể chạy cả task trên thread gọi.

Vì vậy phải phân biệt **gửi tác vụ (submit)**, **được nhận (accept)**, **bắt đầu chạy (start)**, **hoàn tất (finish)** và **thời điểm caller nhận lại quyền điều khiển (return)**. Bài học giải thích cơ chế, hệ quả với `Future` và `@Async`, cách đo độ trễ chờ trong Spring và ví dụ Java 17 chạy độc lập.

## 1. Các mốc thời gian không đồng nghĩa nhau

~~~text
Caller                         Executor / queue                     Worker
  |                                   |                               |
  |---- execute(task) --------------->|                               |
  |       t_submit                    |                               |
  |                                   |-- accept / enqueue ---------->|
  |                                   |      t_accept                 |
  |<--- execute trả về ---------------|                               |
  |       t_return                    |                               |
  |                                   |  chờ worker rảnh              |
  |                                   |---- dispatch task ---------->|
  |                                   |                          t_start
  |                                   |                        chạy task
  |                                   |                          t_finish
~~~

Các mốc có ý nghĩa khác nhau:

1. **`t_submit`**: caller bắt đầu yêu cầu executor thực thi task (gọi `execute`/`submit` hoặc đi qua proxy `@Async`).
2. **`t_accept`**: executor nhận task, cho chạy ngay hoặc đưa vào queue. Không phải executor nào cũng cung cấp timestamp này qua API.
3. **`t_return`**: phương thức gửi trả quyền điều khiển cho caller. `execute(Runnable)` trả `void`, còn `ExecutorService.submit(...)` trả `Future` khi tác vụ được nhận.
4. **`t_start`**: worker bắt đầu chạy phần thực thi mà executor quản lý, ví dụ callback `Runnable.run()`.
5. **`t_finish`**: callback kết thúc, bình thường hoặc do exception; một `Future` còn có thể hoàn tất do cancellation.

Ba khoảng thời gian cần đo riêng:

| Khoảng thời gian | Phép đo gần đúng | Trả lời câu hỏi |
| --- | --- | --- |
| Thời gian gửi (*submission duration*) | `t_return − t_submit` | Caller tốn bao lâu để gửi yêu cầu? |
| Thời gian chờ trước khi chạy (*wait/queue latency*) | `t_start − t_submit` | Task được gửi bao lâu thì mới có worker chạy? |
| Thời gian chạy (*execution duration*) | `t_finish − t_start` | Worker mất bao lâu để thực thi callback? |
| End-to-end của task | `t_finish − t_submit` | Từ yêu cầu gửi tới khi task thực sự hoàn tất là bao lâu? |

Trong một pool có queue và workers đang bận, thứ tự thường thấy là `t_submit < t_return < t_start < t_finish`. Tuy nhiên **không có bảo đảm chung rằng `t_return` luôn trước `t_start`**: task rất ngắn có thể được worker chạy ngay trong lúc caller vẫn ở phương thức `execute`. Với `SyncTaskExecutor` hoặc `CallerRunsPolicy`, callback còn có thể thực thi **ngay trên caller trước khi `execute` trả về**.

**Sự khác biệt giữa submit và start là khái niệm, không phải lời hứa mọi task đều phải chờ queue.**

## 2. Vì sao gửi thành công nhưng task vẫn chưa chạy?

### 2.1. Worker đang bận, task chờ trong queue

Giả sử executor có một worker. Task A giữ worker 5 giây; task B được gửi sau khi A đã bắt đầu:

~~~text
Thời gian      0s          1s                  5s         5,1s
               |-----------|-------------------|-----------|
Worker:        [================ Task A =======][ Task B ]
Caller:        submit A    submit B -> trả về
Queue:                     [ B chờ .............]
                           ^                   ^
                           đã gửi              B mới bắt đầu
~~~

Từ góc nhìn caller, `submit(B)` có thể trả về rất nhanh. Từ góc nhìn B, nó phải chờ nhiều giây mới được chạy. Nếu chỉ ghi log thời gian thực thi *bên trong* method B, hệ thống có thể báo “B chạy 100 ms”, dù người dùng thấy độ trễ hơn 4 giây.

### 2.2. Thứ tự tiếp nhận trong ThreadPoolExecutor

`ThreadPoolTaskExecutor` của Spring thường bọc `ThreadPoolExecutor` của Java, với thứ tự cơ bản:

1. Nếu số worker hiện có nhỏ hơn `corePoolSize`, thử tạo worker mới cho task.
2. Nếu đã đạt core, thử đưa task vào queue.
3. Nếu queue không nhận được, thử mở worker mới đến `maxPoolSize`.
4. Nếu không thể nhận, hoặc executor đã shutdown, task bị từ chối theo policy.

Vì **queue được ưu tiên sau core**, đặt `maxPoolSize=16` không có nghĩa task thứ ba sẽ lập tức chạy trên worker #3 khi `corePoolSize=2` và queue vẫn còn chỗ. Với queue rất lớn, pool có thể giữ gần core workers dù task chờ ngày càng nhiều. Queue không giới hạn về mặt cấu hình còn có thể tích tụ công việc đến mức tăng thời gian chờ và tiêu thụ bộ nhớ.

Ngược lại, với `queueCapacity=0` ở `ThreadPoolTaskExecutor`, Spring dùng `SynchronousQueue` để bàn giao trực tiếp; khi không có worker nhận task và pool dưới max, nó có thể tạo worker mới. Vì thế **độ trễ start phụ thuộc cả queue, core, max, tốc độ hoàn thành và tải thực tế**, không chỉ một tham số.

### 2.3. Worker bị chặn không có nghĩa CPU đang bận

Worker vẫn chiếm một slot của pool khi nó chờ JDBC connection, HTTP response, lock hoặc `Future.get()`. Nếu nhiều worker bị chặn I/O, queue wait có thể tăng dù CPU không cao. Muốn xử lý đúng phải phân biệt bão hòa CPU, worker thiếu do blocking và downstream cạn tài nguyên.

## 3. Java 17: chương trình xác nhận submit B trước khi start B

Tạo tệp **`SubmissionVsStartDemo.java`**. Chương trình dùng một worker và các `CountDownLatch` để tạo thứ tự xác định, không cần đoán thời gian bằng `Thread.sleep(...)`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SubmissionVsStartDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("First task did not start");
            }
            System.out.println("1. Task A started");
            Future<?> second = pool.submit(() -> {
                secondStarted.countDown();
                System.out.println("4. Task B started");
            });
            System.out.println("2. Task B submitted");
            System.out.println("3. Task B started yet? "
                    + (secondStarted.getCount() == 0));
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            System.out.println("5. Task B completed");
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac --release 17 SubmissionVsStartDemo.java
java SubmissionVsStartDemo
~~~

Stdout đã xác minh bằng cách biên dịch với `--release 17` và chạy chương trình:

~~~text
1. Task A started
2. Task B submitted
3. Task B started yet? false
4. Task B started
5. Task B completed
~~~

Giải thích từng bước:

- `firstStarted.await(...)` xác nhận task A đã bắt đầu trên worker duy nhất.
- A chờ `releaseFirst`, vì vậy worker chưa thể chuyển sang B.
- `submit(B)` trả về một `Future<?>` khi B được nhận. Dù vậy, B đang **nằm trong queue**.
- Khi kiểm tra `secondStarted.getCount()`, main chưa nhả A nên B chắc chắn chưa bắt đầu; dòng thứ ba in `false`.
- Sau `releaseFirst.countDown()`, A có thể thoát; worker chạy B và in dòng thứ tư.
- `second.get(...)` chờ B hoàn tất, rồi main mới in dòng thứ năm.

`CountDownLatch` khiến quan sát có tính xác định. Không suy luận mối quan hệ submit/start bằng cách chỉ thêm `Thread.sleep(10)` trong bài test: sự thay đổi tải CPU và lịch OS dễ làm loại test đó chập chờn.

### 3.1. Future không phải timestamp bắt đầu

| Trạng thái Future | Có thể kết luận | Không thể kết luận |
| --- | --- | --- |
| `isDone() == false` | Task chưa đạt trạng thái hoàn tất | Task còn ở queue hay đang chạy |
| `isDone() == true` | Task đã hoàn tất theo hợp đồng Future (có thể thành công, lỗi hoặc hủy) | Task bắt đầu khi nào, mất bao lâu để chờ |
| `isCancelled() == true` | Future đã bị hủy | Side effect trước đó đã được hoàn tác |
| `get()` trả giá trị | Task hoàn thành thành công với giá trị đó | Thời gian queue wait |
| `get(timeout)` ném `TimeoutException` | Caller chờ quá thời hạn cho phép | Task tự động bị dừng |

Khi cần biết `t_start`, phải đo ở đầu callback hoặc dùng instrumentation của executor. `Future.get(timeout)` chỉ giới hạn lượt **chờ của caller**, không mặc nhiên đặt deadline cho công việc đang chờ hoặc đang thực thi.

## 4. Trong Spring, @Async cũng chỉ là yêu cầu chuyển việc sang executor

Ví dụ **mã tích hợp Spring Framework 6.x, Java 17+** (cần dependencies Spring, component scanning và `@EnableAsync`):

~~~java
package example.concurrency;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportWorker {
    @Async("reportExecutor")
    public CompletableFuture<String> generate(String reportId) {
        // Logic thực tế bắt đầu khi worker của executor chạy lời gọi này.
        return CompletableFuture.completedFuture("ready:" + reportId);
    }
}
~~~

Caller ở một bean khác:

~~~java
CompletableFuture<String> result = reportWorker.generate("R-101");
// Lúc này task có thể đang chờ, đang chạy hoặc đã hoàn tất.
~~~

Đoạn caller giả định `reportWorker` đã được inject từ Spring context và lời gọi đi qua proxy. Tại thời điểm nhận `result`, **không có bằng chứng rằng method body đã bắt đầu**. Nếu caller lập tức gọi `result.join()` hoặc `get()`, caller sẽ phải chờ kết quả, làm nó mất khả năng tiếp tục công việc khác trong thời gian đó.

Điều kiện để `@Async` hoạt động đúng:

- Có `@EnableAsync` hoặc cơ chế kích hoạt tương đương.
- Có bean executor được chỉ rõ qua `@Async("reportExecutor")` và bean thực sự được Spring quản lý.
- Lời gọi đi **qua Spring proxy**. Trong chế độ proxy mặc định, gọi `this.generate(...)` từ cùng bean sẽ bỏ qua interceptor async và có thể chạy đồng bộ.
- Caller theo dõi cả **lỗi ở bước submit** (ví dụ pool từ chối) lẫn **lỗi trong lúc task đã bắt đầu chạy**. Future có thể biểu diễn lỗi thực thi; exception từ method `@Async` trả `void` cần cách xử lý riêng.

Điểm nữa: worker rảnh có thể bắt đầu chạy trước lúc lời gọi proxy trả về, nhất là với task ngắn. Do đó không nên suy luận “method body không thể bắt đầu trước khi caller có Future”.

## 5. Cấu hình pool Spring có queue để thấy khoảng chờ

Ví dụ một cấu hình bean rõ ràng:

~~~java
package example.concurrency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncExecutorConfiguration {
    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("report-");
        return executor;
    }
}
~~~

Đây là **tệp tích hợp Spring**, không biên dịch độc lập bằng một lệnh `javac` thiếu classpath Spring. Spring quản lý lifecycle và khởi tạo bean executor khi được đăng ký bình thường trong ApplicationContext.

Giả sử hai core workers đều bận với công việc dài, queue còn chỗ. Tác vụ mới gửi đến có thể được **enqueue** và caller nhận lại quyền điều khiển, trong khi task thực tế chờ worker. Khi queue đầy, pool mới có thể tăng tới max=4; khi cả worker và queue đều không nhận được, nó có thể từ chối.

Số 2/4/10 chỉ là ví dụ. Pool thật phải được sizing theo CPU, thời gian I/O, connection pool, throughput, p95/p99 và tải đỉnh. Queue quá lớn có thể biến lỗi rõ ràng thành thời gian chờ kéo dài.

### 5.1. Submit được nhận, bị từ chối hay chạy trên caller?

Ba kết quả cần phân biệt:

1. **Được nhận, chạy bất đồng bộ:** task có thể chạy ngay hoặc chờ queue.
2. **Bị từ chối:** với chính sách mặc định dựa trên `AbortPolicy`, Spring có thể ném `TaskRejectedException` khi executor đã bão hòa hoặc đóng. Không có `t_start` cho task không được nhận.
3. **Caller thực thi thay worker:** nếu cấu hình `CallerRunsPolicy` và pool chưa shutdown, tác vụ bị bão hòa có thể chạy ngay trên thread gửi. Khi ấy lời gọi `execute(...)` bị chặn tới khi thực thi xong.

Vì vậy không thể định nghĩa “submit luôn không chặn” cho mọi implementation/policy của `TaskExecutor`. `SyncTaskExecutor` còn chủ động thực thi đồng bộ trên thread caller và có ích trong một số môi trường test.

## 6. Đo độ trễ từ submit đến start bằng TaskDecorator

`ThreadPoolTaskExecutor.setTaskDecorator(...)` cho phép bọc callback được executor thực thi. Có thể ghi một dấu thời gian lúc tạo wrapper và một dấu lúc wrapper bắt đầu chạy.

Dưới đây là **phiên bản thay thế** của bean `reportExecutor` ở mục 5, không khai báo hai bean cùng tên trong một ApplicationContext:

~~~java
package example.concurrency;

import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncExecutorConfiguration {
    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("report-");

        executor.setTaskDecorator(callback -> {
            long submittedNanos = System.nanoTime();
            return () -> {
                long startedNanos = System.nanoTime();
                long waitMillis = TimeUnit.NANOSECONDS.toMillis(
                        startedNanos - submittedNanos);
                System.out.println("executor wait (ms): " + waitMillis);
                try {
                    callback.run();
                } finally {
                    long finishedNanos = System.nanoTime();
                    long runMillis = TimeUnit.NANOSECONDS.toMillis(
                            finishedNanos - startedNanos);
                    System.out.println("executor run (ms): " + runMillis);
                }
            };
        });
        return executor;
    }
}
~~~

Ví dụ tích hợp này **không có stdout cố định**: các giá trị đo thay đổi theo scheduling và workload. Trong production, nên gửi hai mẫu số đo tới histogram/timer của hệ thống metrics, kèm tag phù hợp, thay vì in log mỗi task.

Lưu ý khi giải thích metric:

- `System.nanoTime()` dùng đo **hiệu thời gian trong cùng JVM**, không phải giờ UTC và không so sánh số tuyệt đối giữa các máy.
- Callback mà `TaskDecorator` thấy có thể là **wrapper của Spring**, không nhất thiết là `Runnable`/`Callable` gốc. Thời gian đo là thời gian chờ và chạy callback được executor bọc.
- Một task bị reject không chạy wrapper, nên số lần reject phải đo riêng.
- Với `submit(...)`, callback có thể là `FutureTask` giữ exception bên trong Future. Bắt exception quanh `callback.run()` không bảo đảm thấy mọi exception nghiệp vụ.
- Nếu callback chỉ submit task khác rồi trả về, thời gian thực thi callback không bằng thời gian hoàn tất của task đã submit tiếp.
- Nếu policy chạy trên caller, “wait” đo được có thể rất ngắn và “run” xảy ra ngay trong lời gọi gửi. Đừng suy ra đó là worker nền.

### 6.1. Khi nào dùng timestamp đồng hồ?

Để tương quan log từ request đến worker, nên ghi job id/correlation id với timestamp có timezone (ví dụ UTC), ở cả mốc submit và start. Để tính duration chính xác trong cùng process, nên dùng monotonic timer như `System.nanoTime()`. Hai mục đích bổ trợ nhau nhưng không thể hoán đổi trực tiếp.

## 7. Queue wait ảnh hưởng đến timeout, SLA và kết quả nghiệp vụ

Ví dụ giả định:

| Giai đoạn | Thời gian |
| --- | ---: |
| Caller gửi task | 2 ms |
| Task nằm trong queue | 2.500 ms |
| Worker thực thi | 300 ms |
| Tổng từ submit tới hoàn tất | Khoảng 2.802 ms |

Nếu dashboard chỉ tính thời gian bên trong `@Async`, nó hiển thị khoảng **300 ms**, còn người dùng thấy gần **2,8 giây**. Queue wait phải được đưa vào phép đo end-to-end và cân nhắc khi thiết kế deadline.

Cần phân biệt:

- **Request timeout:** giới hạn thời gian HTTP caller chờ response; có thể xảy ra trước khi tác vụ bắt đầu.
- **Future timeout:** `Future.get(timeout)` hết giờ chỉ dừng caller chờ; task có thể tiếp tục trong queue/worker.
- **I/O timeout:** giới hạn thao tác HTTP/JDBC/network thực tế, không tự giải quyết thời gian chờ queue.
- **Business deadline:** thời điểm hết giá trị của công việc. Worker có thể cần kiểm tra deadline trước khi thực hiện side effect.
- **Cancellation:** có thể yêu cầu hủy task nhưng không đảo ngược mọi hiệu ứng đã xảy ra; việc ngắt còn phụ thuộc mã thực thi và thư viện đang chặn.

Nếu API trả `202 Accepted` với job id, phải định nghĩa rõ “accepted” là đã ghi vào hệ thống nào. Một executor có queue **chỉ trong bộ nhớ** không tự bảo đảm công việc tồn tại sau khi tiến trình chết hoặc restart. Nếu nghiệp vụ cần tiếp nhận bền vững, phải có job store/hàng đợi bền vững và trạng thái theo dõi phù hợp.

## 8. Thứ tự submit không phải thứ tự start hoặc finish

Với nhiều workers, B có thể hoàn thành trước A dù A được submit trước. Ngay cả khi queue là FIFO, task mới vẫn có thể được giao trực tiếp cho non-core worker khi queue đã đầy; trong khi task cũ còn chờ worker lấy từ queue.

~~~text
Caller:   submit A ---- submit B ---- submit C
Worker 1: [----------------- A ----------------]
Worker 2:                            [-- C --]
Queue:               [----- B đang chờ -----]
~~~

Sơ đồ minh họa một tình huống có thể xảy ra tùy cấu hình pool và tải. Quy tắc FIFO của một queue **không đồng nghĩa** với thứ tự hoàn tất toàn hệ thống.

Nếu nghiệp vụ đòi hỏi tác vụ của cùng một customer id chạy theo thứ tự, phải thiết kế ordering theo key/partition/transaction hoặc một hàng đợi tuần tự phù hợp. Chỉ gọi `submit(A)` trước `submit(B)` chưa đủ để bảo đảm side effect của A luôn trước B.

## 9. Cách chẩn đoán khi task đến chậm

Thu thập các thông tin dưới đây ở cả caller và worker:

| Dữ liệu | Mục đích |
| --- | --- |
| Job/correlation id | Ghép log của submit, queue, start, finish |
| Timestamp submit và kết quả admission | Phân biệt task được nhận và task bị từ chối |
| Timestamp start, finish, failure | Đo queue wait, execution duration và end-to-end |
| Active workers / pool size | Xem worker có bị chiếm hết không |
| Queue depth và tuổi task chờ | Phát hiện backlog trước khi request timeout |
| Rejection count, executor shutdown | Phân biệt quá tải và không còn nhận task |
| CPU, lock wait, DB/HTTP pool | Tìm tài nguyên khiến worker không tiến triển |
| p50/p95/p99 wait và run duration | Nhìn tác động lên các request chậm nhất |

Các bước kiểm tra:

1. Xác nhận ứng dụng đang dùng **bean executor nào**, `@Async` có đi qua proxy không và có vô tình dùng executor đồng bộ không.
2. Tách lỗi **không được nhận** với **được nhận nhưng chưa chạy**; không ghi queue wait cho task đã reject.
3. Đo từ submit đến start trên cùng một job id, rồi đo riêng khoảng worker chạy.
4. Xem core/max/queueCapacity/rejection policy. Với queue còn chỗ, pool có thể không mở tới max.
5. Kiểm tra thread dump và downstream: worker có đang chờ JDBC, HTTP, lock hoặc task con trong cùng pool không?
6. Thử tải tăng dần, so sánh throughput, queue wait, run duration, timeout, rejection, CPU và connection wait trước/sau khi đổi cấu hình.

Nếu queue wait tăng mạnh còn run duration giữ nguyên, có thể tốc độ đến đang lớn hơn năng lực phục vụ hoặc pool bị giới hạn worker. Nếu run duration cũng tăng, tìm bottleneck về I/O, DB, lock và CPU trước khi tăng số luồng.

## 10. Các nhầm lẫn thường gặp

**“`@Async` trả Future nghĩa là method body đã chạy.”** Sai: Future có thể được trả khi task vẫn đang nằm trong queue.

**“`Future.isDone() == false` nghĩa là task chưa start.”** Sai: Future chưa hoàn tất có thể đang chạy hoặc đang chờ.

**“Khi có worker rảnh, submit phải trả về trước start.”** Không có bảo đảm như vậy; worker có thể bắt đầu rất nhanh, trước khi caller hoàn tất thao tác gửi.

**“Đặt maxPoolSize lớn là không có queue wait.”** Sai: sau core, executor thường ưu tiên enqueue trước khi mở worker bổ sung.

**“Executor có nghĩa luôn chạy trên luồng nền.”** Sai: Spring có `SyncTaskExecutor`; `CallerRunsPolicy` còn có thể khiến caller trực tiếp thực thi task khi pool quá tải.

**“TaskDecorated wrapper catch exception là đếm đủ mọi lỗi.”** Sai: với wrapper `FutureTask`, exception có thể được giữ lại trong Future.

**“Chỉ cần đo thời gian bên trong `run()` để biết latency.”** Sai: phép đo đó bỏ qua khoảng chờ từ submit tới start.

**“Không có rejection tức là không quá tải.”** Sai: queue dài có thể tích tụ task, tăng thời gian chờ và bộ nhớ mà chưa reject.

**“Submit thành công có nghĩa xử lý chắc chắn được bảo toàn qua restart.”** Sai: queue in-memory không cung cấp độ bền như một message broker/job store.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** B được gửi thành công khi một worker đang bận 3 giây, B chạy 100 ms. Tổng thời gian của B tính từ submit có thể khoảng bao nhiêu?

**Đáp án:** khoảng 3,1 giây trong mô hình một worker và không có độ trễ khác: 3 giây chờ và 0,1 giây chạy.

**Câu 2.** Nhận được `CompletableFuture` nghĩa là method body `@Async` đã bắt đầu chưa?

**Đáp án:** chưa chắc. Có thể queue, đang chạy hoặc đã xong; cần mốc start/metric để biết.

**Câu 3.** `Future.isDone()==true` có thể có ý nghĩa nào khác thành công?

**Đáp án:** có thể task kết thúc bằng exception hoặc Future bị cancel.

**Câu 4.** Trong ví dụ Java 17, tại sao B luôn chưa start khi main kiểm tra?

**Đáp án:** A đã giữ worker duy nhất và chờ latch; main chưa mở latch ở thời điểm kiểm tra.

**Câu 5.** Với `CallerRunsPolicy`, `execute(task)` có thể chờ task chạy xong trước khi trả về không?

**Đáp án:** có. Khi policy được kích hoạt và pool chưa shutdown, caller tự chạy task.

**Câu 6.** Vì sao dashboard đo riêng thời gian trong method async thường thấp hơn độ trễ người dùng?

**Đáp án:** nó không tính thời gian task đợi worker trong queue.

**Câu 7.** Nếu pool có core=2, max=8 và queueCapacity=100, vì sao task thứ ba có thể chưa chạy ngay?

**Đáp án:** vì sau core, executor ưu tiên đưa task vào queue khi còn chỗ thay vì mở thêm worker.

**Câu 8.** Khi task bị từ chối, nên ghi nhận sự kiện gì?

**Đáp án:** ghi submission failure/rejection với thông tin pool và caller; không gán cho task một start timestamp không tồn tại.

## 12. Tài liệu tham khảo

- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html
- Spring Framework — `ThreadPoolTaskExecutor`, `setTaskDecorator(...)`: https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Java SE 17 — `ThreadPoolExecutor`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Java SE 17 — `Future`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- Java SE 17 — `System.nanoTime()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime()


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-submission-007">Chụp dữ liệu đầu vào phù hợp trước khi chuyển sang luồng khác</a>

<details>
<summary>Xem chi tiết</summary>

# Chụp dữ liệu đầu vào phù hợp trước khi chuyển sang luồng khác

Trong ứng dụng **Spring Framework - concurrency**, một HTTP request có thể nhận dữ liệu, gọi service, rồi giao một phần công việc cho **Executor**, **@Async** hoặc **CompletableFuture**. Ngay lúc giao việc, luồng khác chưa chắc đã bắt đầu chạy; thậm chí nó có thể chỉ chạy sau khi HTTP request kết thúc. Nếu tác vụ bất đồng bộ tiếp tục tham chiếu đến dữ liệu **có thể thay đổi, bị giải phóng hoặc không còn thuộc phạm vi hợp lệ**, kết quả sẽ không ổn định và có nguy cơ lộ dữ liệu giữa các request.

Nguyên tắc cốt lõi của bài **“Chụp dữ liệu đầu vào phù hợp trước khi chuyển sang luồng khác”** là: **xác định chính xác tác vụ cần dữ liệu gì, lấy một bản snapshot thích hợp ở thời điểm giao việc, xác định ai sở hữu bản đó và không để bên gửi tiếp tục sửa những phần mà tác vụ cần coi là bất biến**.

Đây là bài thuộc nhóm **Gửi và nhận tác vụ**. Nó không yêu cầu sao chép mù quáng mọi đối tượng: “phù hợp” nghĩa là **đủ dữ liệu, có vòng đời đúng, bất biến ở mức cần thiết, không chứa bí mật dư thừa và có cách xử lý lỗi/giới hạn tài nguyên phù hợp**.

## 1. Ranh giới giữa luồng nhận request và luồng làm việc

Giả sử request A đặt một đơn hàng:

~~~text
HTTP request thread
  |
  | 1. Đọc payload, principal, requestId
  | 2. Kiểm tra, chuẩn hóa dữ liệu
  | 3. Tạo snapshot cho tác vụ
  | 4. executor.submit(() -> process(snapshot))
  |
  +-------------------------------> Worker thread
  |                                    |
  |                                    | 5. Có thể chạy trễ
  |                                    | 6. Dùng snapshot đã nhận
  |                                    v
  +--> HTTP request kết thúc       Xử lý kết quả
~~~

Hai luồng không có một “vùng biến cục bộ chung” mặc định. Khi truyền một đối tượng vào lambda hoặc phương thức bất đồng bộ, Java thường chuyển **giá trị tham chiếu đến đối tượng**, không tự chụp một bản sao toàn bộ object graph. Nếu bên gửi và bên nhận cùng trỏ đến một **ArrayList**, **HashMap** hoặc DTO có setter, chúng có thể quan sát những lần sửa của nhau.

Nói cách khác:

- **Giao việc**: executor nhận một Runnable/Callable hoặc Spring proxy chuyển phương thức @Async tới một TaskExecutor.
- **Chụp dữ liệu**: ứng dụng quyết định giá trị nào được sao chép, đối tượng nào được tạo mới và mức bất biến nào cần bảo đảm.
- **Chạy tác vụ**: worker sử dụng những giá trị đã nhận; worker không nên tự giả định rằng request, session, transaction hoặc ThreadLocal của caller còn tồn tại.
- **Nhận kết quả**: Future/CompletableFuture cho biết trạng thái của công việc, không tự giải quyết tính an toàn của những input mà task đã đọc.

## 2. “Capture biến” của lambda không phải “snapshot dữ liệu”

Xét đoạn code:

~~~java
List<String> items = new ArrayList<>();
items.add("book");

executor.execute(() -> process(items));

items.add("pen");
~~~

Đây là **đoạn mã minh họa** (giả sử có các import, executor và process). Lambda giữ một tham chiếu đến cùng **List**. Biến cục bộ items không bị gán lại nên thỏa điều kiện **effectively final**, nhưng **đối tượng ArrayList vẫn mutable**. Worker có thể đọc danh sách sau khi đã có phần tử pen, trước khi có pen, hoặc gặp vấn đề an toàn luồng nếu hai bên truy cập/ghi không đồng bộ.

Không nên nói rằng task “đã nhận [book]” chỉ vì lời gọi **execute** xảy ra trước dòng **items.add**. Điều kiện effectively final chỉ giới hạn **việc gán lại biến được lambda capture**, không đóng băng trạng thái của đối tượng mà biến trỏ tới.

Thêm **final** cũng chưa giải quyết:

~~~java
final List<String> items = new ArrayList<>();
items.add("book");
// items = new ArrayList<>(); // Không được gán lại.
items.add("pen");             // Vẫn được sửa đối tượng!
~~~

Có hai loại tách biệt:

| Tình huống | Có đổi được tham chiếu? | Có đổi được dữ liệu bên trong? |
| --- | --- | --- |
| Biến local effectively final trỏ tới ArrayList | Không gán lại trong phạm vi capture | **Có** |
| Field final trỏ tới HashMap | Không gán lại field | **Có** |
| record chứa List mutable | Không gán lại component | **Có**, nếu List vẫn mutable |
| record chứa List.copyOf của String | Không gán lại component | List không sửa trực tiếp được; String cũng bất biến |
| DTO chứa nested object mutable | Tùy khai báo | **Có thể**, cần snapshot sâu hơn |

Vì vậy, câu hỏi không phải “tham số có final không?” mà là **“mọi dữ liệu worker cần đọc còn có thể thay đổi qua một tham chiếu khác không?”**.

## 3. Ví dụ Java 17: worker đọc nhầm dữ liệu vì tham chiếu bị sửa

Ví dụ độc lập sau cố ý chụp **tham chiếu**, không chụp giá trị. Chúng ta dùng **CountDownLatch** để tạo một tình huống xác định: worker đã nhận input, nhưng phải đợi caller sửa nó xong mới được đọc.

Lưu thành **CapturedReferenceDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CapturedReferenceDemo {
    static final class MutableRequest {
        String owner = "An";
        final List<String> tags =
                new ArrayList<>(List.of("standard"));
    }

    public static void main(String[] args) throws Exception {
        MutableRequest request = new MutableRequest();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        CountDownLatch workerReady = new CountDownLatch(1);
        CountDownLatch allowRead = new CountDownLatch(1);

        try {
            Future<String> result = executor.submit(() -> {
                workerReady.countDown();
                allowRead.await();
                return request.owner + " " + request.tags;
            });

            if (!workerReady.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Worker không bắt đầu trong thời hạn test");
            }

            request.owner = "Bình";
            request.tags.add("urgent");
            allowRead.countDown();

            System.out.println("Worker đọc: "
                    + result.get(5, TimeUnit.SECONDS));
        } finally {
            allowRead.countDown();
            executor.shutdownNow();
        }
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 CapturedReferenceDemo.java
java CapturedReferenceDemo
~~~

**Stdout**, nếu worker khởi động trong thời hạn test:

~~~text
Worker đọc: Bình [standard, urgent]
~~~

**Giải thích từng bước:**

1. Worker có tham chiếu đến cùng **MutableRequest** mà caller đang giữ.
2. Worker báo đã bắt đầu, rồi đợi **allowRead**.
3. Caller thay owner và thêm tag mới, sau đó mở latch.
4. Worker đọc **giá trị hiện tại sau khi bị sửa**, không phải giá trị tại lúc submit.
5. **CountDownLatch** tạo quan hệ đồng bộ cần thiết, giúp ví dụ có kết quả xác định. Không dùng Thread.sleep để đoán thứ tự chạy.

Ví dụ này chủ động mô phỏng sai lầm thiết kế. Nếu caller sửa ArrayList trong lúc worker đang duyệt nó mà không có đồng bộ, còn có thể xuất hiện race hoặc ConcurrentModificationException; nhưng **không nên khẳng định ConcurrentModificationException chắc chắn xảy ra**. Kết quả phụ thuộc lịch chạy và kiểu thao tác.

## 4. Cách sửa mặc định: snapshot trước khi submit

Tạo một đối tượng **chỉ chứa dữ liệu cần cho công việc**, bằng immutable scalars và collection copy khi thích hợp. Chụp snapshot **trên thread đang sở hữu dữ liệu**, **trước** khi giao việc, trong khi dữ liệu còn ở trạng thái nhất quán.

Lưu thành **InputSnapshotDemo.java**, chạy độc lập với Java 17:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class InputSnapshotDemo {
    static final class MutableRequest {
        String owner = "An";
        final List<String> tags =
                new ArrayList<>(List.of("standard"));
    }

    record JobSnapshot(String owner, List<String> tags) {
        JobSnapshot {
            tags = List.copyOf(tags);
        }
    }

    public static void main(String[] args) throws Exception {
        MutableRequest request = new MutableRequest();

        // Snapshot tạo trước khi đưa công việc vào executor.
        JobSnapshot snapshot =
                new JobSnapshot(request.owner, request.tags);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch workerReady = new CountDownLatch(1);
        CountDownLatch allowRead = new CountDownLatch(1);

        try {
            Future<String> result = executor.submit(() -> {
                workerReady.countDown();
                allowRead.await();
                return snapshot.owner() + " " + snapshot.tags();
            });

            if (!workerReady.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Worker không bắt đầu trong thời hạn test");
            }

            request.owner = "Bình";
            request.tags.add("urgent");
            allowRead.countDown();

            System.out.println("Dữ liệu nguồn: "
                    + request.owner + " " + request.tags);
            System.out.println("Snapshot của task: "
                    + result.get(5, TimeUnit.SECONDS));
        } finally {
            allowRead.countDown();
            executor.shutdownNow();
        }
    }
}
~~~

Lệnh biên dịch và chạy:

~~~bash
javac -encoding UTF-8 InputSnapshotDemo.java
java InputSnapshotDemo
~~~

**Stdout** (worker khởi động thành công):

~~~text
Dữ liệu nguồn: Bình [standard, urgent]
Snapshot của task: An [standard]
~~~

Tại sao bản sửa an toàn hơn?

- **owner** là String immutable, chụp giá trị tham chiếu String tại thời điểm snapshot là đủ.
- **List.copyOf(tags)** tạo một collection không thể chỉnh sửa qua các phương thức List, độc lập với những lần thêm/xóa phần tử khỏi ArrayList nguồn sau đó.
- Các phần tử trong ví dụ là **String** cũng immutable, nên không còn nhánh mutable bên trong List này.
- Worker chỉ nhận **snapshot**, không giữ **request** mutable hay đọc lại trạng thái gốc sau khi task đã được giao.
- Record bảo đảm tham chiếu các component không đổi sau khi khởi tạo; **copy phòng vệ của collection** bảo đảm phần dữ liệu list không bị sửa thông qua alias.

Lệnh **List.copyOf** không phải “phép màu đồng bộ”: nếu có một thread khác **đang sửa collection nguồn ngay trong lúc tạo snapshot**, thao tác copy có thể không có kết quả nhất quán hoặc có lỗi. Để snapshot đúng, trước tiên phải có **quyền sở hữu dữ liệu tạm thời**, dùng **lock**, **immutable input đã được publish an toàn** hoặc một cơ chế đồng bộ phù hợp.

## 5. Shallow copy và deep snapshot: phải nhìn cả object graph

Lỗi tiếp theo là tạo list mới nhưng mỗi phần tử vẫn là cùng một đối tượng mutable.

~~~java
List<MutableLine> copied = List.copyOf(originalLines);
~~~

Đây là **đoạn minh họa**. Dù **copied** không cho thêm/xóa phần tử, nếu một **MutableLine** đã có trong list đổi giá hoặc mã hàng, worker vẫn quan sát **đối tượng phần tử bị đổi**.

Một ví dụ độc lập, lưu thành **NestedSnapshotDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class NestedSnapshotDemo {
    static final class MutableLine {
        String sku;

        MutableLine(String sku) {
            this.sku = sku;
        }
    }

    record LineView(String sku) {
    }

    public static void main(String[] args) {
        MutableLine line = new MutableLine("SKU-01");
        List<MutableLine> source = new ArrayList<>();
        source.add(line);

        // Chỉ tạo list không sửa được; phần tử vẫn là MutableLine gốc.
        List<MutableLine> shallow = List.copyOf(source);

        // Chụp riêng dữ liệu cần thiết thành record chứa String.
        List<LineView> deepEnough = source.stream()
                .map(item -> new LineView(item.sku))
                .toList();

        line.sku = "SKU-02";

        System.out.println("Shallow thấy: " + shallow.get(0).sku);
        System.out.println("Snapshot thấy: "
                + deepEnough.get(0).sku());
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 NestedSnapshotDemo.java
java NestedSnapshotDemo
~~~

Stdout:

~~~text
Shallow thấy: SKU-02
Snapshot thấy: SKU-01
~~~

Trong ví dụ, **deepEnough** là một “deep snapshot đủ dùng” vì worker chỉ cần trường String sku. Nó không sao chép mọi thứ trong đối tượng nguồn mà chỉ lấy **giá trị có ý nghĩa với tác vụ**.

Các trường hợp cần suy nghĩ thêm:

| Dữ liệu gốc | Cách chụp thường phù hợp | Lưu ý |
| --- | --- | --- |
| String, enum, UUID | Giữ giá trị đã lấy | Các kiểu này là immutable |
| Primitive hoặc boxed immutable | Copy giá trị | Chọn đúng thời điểm đọc |
| List<String> | **List.copyOf(source)** | Phần tử String immutable; null bị từ chối |
| Set<String> | **Set.copyOf(source)** | Null bị từ chối; thứ tự duyệt không nhất thiết giữ theo nguồn |
| Map<String, String> | **Map.copyOf(source)** | Key/value không được null; key/value cần immutable |
| List<MutableDTO> | Chuyển từng phần tử thành immutable view/record mới | Copy list thôi chưa đủ |
| Mảng byte[] | **Arrays.copyOf(source, source.length)** | Mảng vẫn mutable; không chia sẻ reference nếu cần cô lập |
| Map<String, List<MutableDTO>> | Chụp map, list và từng DTO theo nhu cầu | Tránh alias ở nhiều cấp |
| Thực thể JPA/Hibernate | Chụp ID và field cần dùng, hoặc tải lại trong worker với transaction riêng | Không mang persistence context/entity mutable qua thread |
| Dữ liệu streaming/MultipartFile | Chụp nội dung/đường dẫn bền vững khi hợp lý, giới hạn kích thước | Vòng đời file/stream có thể kết thúc cùng request |

**Record không tự động deep immutable.** Một record như **record Payload(List<MutableDTO> items)** vẫn giữ tham chiếu tới list/element, trừ khi constructor chủ động phòng vệ và các phần tử được chuyển thành dạng phù hợp.

## 6. “Phù hợp” nghĩa là chụp dữ liệu tối thiểu và rõ nguồn gốc

Không cần copy toàn bộ HTTP request hay toàn bộ domain object. Tự hỏi:

1. Worker **thật sự cần thông tin gì** để hoàn thành công việc? Thường chỉ cần requestId, userId, mã tài nguyên, thời hạn, một vài tham số đã kiểm tra.
2. Dữ liệu có **bị caller mutate** sau submit không? Có nhánh collection/DTO mutable nào không?
3. Dữ liệu gốc có phụ thuộc vào **request scope, session, transaction, thread context, connection hay stream** sẽ hết hạn trước khi worker dùng không?
4. Dữ liệu có **nhạy cảm** không? Có đang sao chép cả mật khẩu, access token hoặc thông tin định danh mà worker không cần?
5. Nếu worker chạy **rất trễ** hoặc task bị retry, bản chụp có còn đúng về mặt nghiệp vụ không? Có cần tải lại trạng thái mới nhất từ database?
6. Bản chụp có **quá lớn** để giữ trong hàng đợi executor không? Có cần thay bằng ID/tham chiếu tới lưu trữ bền vững không?

Ví dụ một task gửi email thông báo hóa đơn có thể chỉ cần:

~~~java
public record EmailJobInput(
        String requestId,
        long invoiceId,
        String recipientId,
        String templateKey
) {
}
~~~

Đây là **định nghĩa record Java 17 độc lập** nhưng chỉ là **mảnh code mẫu**, không có main. Worker có thể nhận invoiceId và tải thông tin hóa đơn trong một transaction riêng, thay vì giữ cả đối tượng JPA EntityManager cùng với một cây object lớn từ request.

Trường hợp ngược lại, nếu yêu cầu là “gửi chính xác nội dung người dùng đã xác nhận tại thời điểm submit”, chỉ gửi ID rồi đọc lại dữ liệu **có thể đã đổi** cũng sai. Khi đó cần chụp immutable payload đã chuẩn hóa, snapshot có version, hoặc lưu một bản ghi giao dịch có tính bền vững để worker đọc đúng phiên bản. **Chọn dữ liệu nào được snapshot là quyết định nghiệp vụ**, không chỉ là chuyện Java syntax.

## 7. Java Memory Model: visibility không thay thế isolation

Trong Java, **ExecutorService** có quy tắc memory consistency quan trọng: những hành động trong thread trước khi gửi Runnable/Callable tới ExecutorService **happens-before** hành động của task đó; hành động của task xảy ra trước các bước sau khi một thread khác **Future.get()** trả về thành công.

Điều này giúp **công bố dữ liệu đã chuẩn bị trước submit** tới worker theo hợp đồng bộ thực thi. Tuy nhiên:

- Nó **không biến một ArrayList thành thread-safe**.
- Nó **không cấm** caller sửa input sau khi đã submit.
- Nó **không tự sao chép** object graph được lambda tham chiếu.
- Nó **không bảo đảm** một chuỗi nhiều field đọc từ nguồn mutable cùng thuộc về một phiên bản nhất quán nếu thread khác đang viết đồng thời.

Ví dụ:

~~~text
Caller: chuẩn bị list -> submit(task) -> sửa list
                           |
Worker:                 đọc list (bất cứ khi nào được chạy)
~~~

Quan hệ **trước submit → task** giúp publish trạng thái trước đó; đoạn **sửa list sau submit** có thể cạnh tranh với worker. Vì vậy, **snapshot + quyền sở hữu rõ** là giải pháp thiết kế, chứ không chỉ tăng volatile hoặc khóa một biến riêng lẻ.

Nếu tác vụ cần “một ảnh chụp tại cùng thời điểm” của nhiều trường trên một object mà **thread khác đồng thời cập nhật**, phải có một chiến lược nhất quán:

- Chặn mutate trong giai đoạn copy bằng **synchronized/Lock** dùng chung với writer.
- Xây object immutable hoàn chỉnh trước khi publish rồi **thay cả tham chiếu** bằng một publication mechanism đúng, thay vì sửa từng field.
- Đọc một **transaction snapshot** hoặc thông tin có version từ kho dữ liệu có bảo đảm phù hợp.
- **Chuyển quyền sở hữu** dữ liệu sang worker và cam kết caller không sửa nữa, nếu protocol thực sự cho phép.

Chỉ tạo **new ArrayList<>(source)** trong lúc một thread khác đang sửa source không phải một giao dịch snapshot nguyên tử.

## 8. Constructor copy phòng vệ: cả lúc nhận lẫn lúc trả dữ liệu

Một kiểu tổ chức tốt là tạo record hoặc final class có invariant rõ ràng:

~~~java
import java.util.List;
import java.util.Objects;

public record PriceJob(String orderId, List<Long> amounts) {
    public PriceJob {
        orderId = Objects.requireNonNull(orderId).strip();
        if (orderId.isEmpty()) {
            throw new IllegalArgumentException("orderId rỗng");
        }
        amounts = List.copyOf(amounts);
    }
}
~~~

Đây là **tệp PriceJob.java chạy được khi được dùng bởi mã gọi phù hợp**, không phải một chương trình có main. Nó yêu cầu **orderId** không null/rỗng và sao chép danh sách. Các phần tử **Long** immutable, nên phần dữ liệu worker cần đọc được cô lập tốt nếu caller đã sở hữu nguồn khi xây record.

Không phải mọi kiểu chỉ cần **List.copyOf**. Với **byte[]**, ngay cả một field final vẫn có thể bị sửa nếu trả ra mảng gốc. Một class có mảng cần copy phòng vệ cả ở constructor và getter:

~~~java
import java.util.Arrays;

public final class BinaryPayload {
    private final byte[] bytes;

    public BinaryPayload(byte[] input) {
        this.bytes = Arrays.copyOf(input, input.length);
    }

    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
~~~

Đây là **lớp Java độc lập, không có main**. Nếu getter trả thẳng bytes, code bên ngoài vẫn có thể sửa dữ liệu của instance. Với payload lớn, chi phí copy cần được cân nhắc; có thể dùng file/blob bền vững theo ID và quản lý quyền truy cập thay vì copy khối bộ nhớ lớn cho từng task.

**List.copyOf** và **Collections.unmodifiableList** không hoàn toàn giống nhau. **Collections.unmodifiableList(source)** thường là *view* không cho sửa qua view nhưng vẫn phản ánh những lần thay đổi trên list gốc. **List.copyOf(source)** tạo dữ liệu collection không bị ảnh hưởng bởi việc thêm/xóa phần tử nguồn sau đó (với điều kiện copy từ nguồn nhất quán). Cả hai vẫn không tự biến phần tử mutable thành immutable.

## 9. Spring MVC và @Async: lấy snapshot ở request boundary

Đây là trường hợp rất thường gặp: controller nhận một HTTP request, biết user đã xác thực, rồi gọi một service chạy async. Điều đúng cần làm là **lấy những giá trị cần thiết trước**, tạo một đối tượng snapshot, chuyển nó cho service và để worker chỉ đọc bản đó.

Các tệp tiếp theo thuộc một **ứng dụng Spring MVC có Spring Web và Spring Context**, không phải ví dụ javac thuần. Mỗi khối là **một tệp Java riêng**, cần đặt đúng package.

**ReportJobInput.java** — snapshot của một tác vụ:

~~~java
package example.async;

public record ReportJobInput(
        String requestId,
        String userId,
        String title
) {
}
~~~

**ReportWorker.java** — service không lưu request input vào instance field:

~~~java
package example.async;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportWorker {
    @Async("reportExecutor")
    public CompletableFuture<String> build(ReportJobInput input) {
        String message = "done:" + input.requestId()
                + ":" + input.userId()
                + ":" + input.title();

        return CompletableFuture.completedFuture(message);
    }
}
~~~

**ReportController.java** — chụp snapshot trước ranh giới @Async:

~~~java
package example.async;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ReportController {
    private final ReportWorker worker;

    public ReportController(ReportWorker worker) {
        this.worker = worker;
    }

    @PostMapping("/reports")
    public CompletableFuture<String> create(
            @RequestParam String title,
            Principal principal) {

        if (principal == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED);
        }

        String normalized = title.strip();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST);
        }

        ReportJobInput input = new ReportJobInput(
                UUID.randomUUID().toString(),
                principal.getName(),
                normalized
        );

        return worker.build(input);
    }
}
~~~

**AsyncConfig.java** — chọn executor có tên:

~~~java
package example.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("report-");
        return executor;
    }
}
~~~

Đoạn trên là **ví dụ kỹ thuật minh họa truyền snapshot**, không phải hệ thống tạo báo cáo/bảo mật hoàn chỉnh. Spring quản lý vòng đời và khởi tạo bean ThreadPoolTaskExecutor khi cấu hình qua container.

Điều đáng chú ý:

1. Controller lấy **userId** từ **Principal**, không nhận userId do client tự truyền rồi tin tưởng.
2. Controller chuẩn hóa title và tạo **ReportJobInput** chứa **String immutable**, thay vì chuyển thẳng HttpServletRequest, Principal object hoặc request-scoped bean sang worker.
3. **@Async("reportExecutor")** xác định executor dùng cho service. Phương thức build được **controller bean khác** gọi qua Spring proxy; self-invocation thông thường trong cùng bean không kích hoạt interceptor @Async.
4. Controller trả **CompletableFuture** để MVC có thể kết nối xử lý bất đồng bộ với kết quả. Đây không phải một **durable background job**; kết quả và vòng đời request vẫn liên hệ tới HTTP response.
5. Khi nghiệp vụ cần phân quyền ở thời điểm thực thi, **userId đã snapshot không tự thay thế authorization**. Worker cần áp dụng các bước kiểm tra quyền thích hợp trước thao tác đặc quyền; ví dụ này chỉ tạo chuỗi kết quả.
6. Nếu executor quá tải, việc gửi tác vụ có thể **bị từ chối ngay tại lời gọi worker.build**. Snapshot đúng không đảm bảo task được tiếp nhận. Người gọi cần xử lý overload phù hợp.

Đừng đưa **@RequestScope bean** hoặc **HttpServletRequest** vào task rồi đọc sau khi request kết thúc. Một scoped proxy có thể không giải quyết được target khi worker không còn request context; session, servlet request và request attributes không có vòng đời theo executor task.

## 10. CompletableFuture: snapshot trước supplyAsync, không đợi đến trong lambda

Một anti-pattern khác:

~~~java
CompletableFuture<String> future =
        CompletableFuture.supplyAsync(
                () -> callExternalService(request.getOwner(),
                        request.getItems()),
                executor);
~~~

Đây là **đoạn minh họa**, giả sử request, executor và callExternalService tồn tại. Lambda sẽ lấy **owner** và **items** khi worker thực sự chạy; nó **không lấy chúng tại thời điểm tạo CompletableFuture**. Nếu caller sửa request hoặc vòng đời request đã hết, task có thể nhận dữ liệu khác ý định.

Cách đúng thường là:

~~~java
String owner = request.getOwner();
List<String> items =
        List.copyOf(request.getItems());

JobSnapshot snapshot = new JobSnapshot(owner, items);

CompletableFuture<String> future =
        CompletableFuture.supplyAsync(
                () -> callExternalService(
                        snapshot.owner(),
                        snapshot.items()),
                executor);
~~~

Đây cũng là **đoạn minh họa** với các type và biến ngữ cảnh đã có. Quan trọng là **snapshot được tạo trước** lời gọi supplyAsync. **JobSnapshot** phải là immutable/defensive type phù hợp, không chỉ một DTO chứa collection mutable. Hàm **List.copyOf** vẫn yêu cầu nguồn đang ở trạng thái nhất quán lúc copy.

Với chuỗi CompletableFuture:

~~~java
CompletableFuture<Result> result =
        CompletableFuture.supplyAsync(
                () -> load(snapshot), executor)
            .thenApply(data -> transform(data, snapshot))
            .thenCompose(data -> saveAsync(data, snapshot));
~~~

Đây là **pseudocode Java được chú thích dưới dạng đoạn minh họa**: phải tự khai báo Result, load, transform, saveAsync và snapshot tương ứng. Các stage nhìn thấy cùng snapshot đã chụp. Không giả định callback **thenApply** luôn chạy ở request thread; callback non-async có thể chạy trên thread hoàn tất stage trước hoặc thread đăng ký tùy trạng thái hoàn tất.

**Điểm cốt lõi:** việc dùng CompletableFuture tạo pipeline bất đồng bộ **không tự cô lập mutable input**. Snapshot và quyền sở hữu đối tượng vẫn là trách nhiệm của ứng dụng.

## 11. ThreadLocal, MDC và SecurityContext không phải bản snapshot tự động

Nhiều thông tin trong ứng dụng web được framework buộc vào thread: **MDC logging**, **SecurityContext** trong một số chiến lược, **RequestContextHolder**, transaction context. Khi gọi một Executor, task có thể chạy trên thread pool khác; thread đó **không mặc định giữ mọi ThreadLocal của caller**.

Các nguyên tắc:

- **Dữ liệu nghiệp vụ:** truyền trực tiếp qua tham số hoặc một immutable DTO.
- **Correlation/tracing:** dùng cơ chế propagation của framework quan sát/tracing hoặc một **TaskDecorator** được thiết kế đúng và dọn ngữ cảnh sau khi task kết thúc.
- **Bảo mật:** không tự copy toàn bộ SecurityContext/Principal một cách tùy tiện. Nếu framework và chính sách cho phép, dùng wrapper như **DelegatingSecurityContextAsyncTaskExecutor**; đảm bảo quyền được kiểm tra đúng thời điểm cần thiết.
- **Transaction:** transaction/persistence context ở thread gửi **không tự chuyển** sang worker. Thiết lập transaction boundary tại worker nếu cần truy cập dữ liệu giao dịch.
- **Request context:** không coi HttpServletRequest, request-scoped bean, request attributes hoặc MultipartFile là dữ liệu có thể tiếp tục dùng tùy ý sau khi response đã trả.
- **ThreadLocal tự viết:** nếu bắt buộc dùng, cần quản lý **set/restore/remove trong finally**. Thread pool tái sử dụng worker, nên bỏ quên cleanup có thể rò rỉ dữ liệu sang task sau.

Cũng cần phân biệt **context propagation** với **input snapshot**: propagation có thể phục hồi ngữ cảnh kỹ thuật phù hợp của một task, nhưng không bảo đảm DTO mutable được chụp đúng hoặc dữ liệu bảo mật còn hợp lệ khi worker thực thi.

## 12. Spring Data/JPA: ưu tiên ID và dữ liệu đã đóng băng, không đẩy EntityManager sang thread khác

Một lỗi phổ biến là đọc một entity trong transaction HTTP rồi đưa nguyên entity cho worker để xử lý về sau.

Rủi ro gồm:

- **EntityManager** và persistence context theo transaction không được thiết kế để chia sẻ tùy ý giữa các thread.
- Entity có thể có **lazy association**; worker truy cập sau khi transaction/session đã đóng có thể gặp lỗi tải dữ liệu.
- Entity mutable có thể đang bị caller hoặc tầng khác sửa, không bảo đảm một snapshot nhất quán.
- Dữ liệu trong DB có thể thay đổi giữa lúc submit và lúc worker xử lý.

Hai chiến lược cần phân biệt:

**A. Tác vụ phải dùng trạng thái mới nhất tại thời điểm thực thi.** Chỉ chụp **ID** và input nghiệp vụ tối thiểu, rồi worker mở transaction riêng và tải lại entity có kiểm tra version/authorization tương ứng.

~~~java
public record UpdateJob(long orderId, String actorId) {
}
~~~

**B. Tác vụ phải xử lý chính xác nội dung đã được người dùng duyệt tại thời điểm submit.** Chụp **immutable DTO chứa dữ liệu đã duyệt**, kèm **version** hoặc ghi một bản snapshot vào kho bền vững cùng transaction/outbox nếu cần. Chỉ gửi orderId rồi tải “phiên bản mới nhất” sẽ không tương đương với yêu cầu này.

Trong các quy trình có side effect và yêu cầu không mất việc, **executor nội bộ không phải hàng đợi bền vững**. Khi transaction gửi job thất bại hoặc ứng dụng restart, một Runnable trong RAM có thể mất. Cân nhắc **transactional outbox**, durable queue hoặc job store, cùng idempotency và cơ chế retry phù hợp.

## 13. Request body lớn, file và stream: không phải cứ copy toàn bộ vào RAM

Một snapshot phù hợp còn phải có **ngân sách tài nguyên**:

~~~text
10.000 job đang chờ
  x 2 MB payload copy mỗi job
  = khoảng 20.000 MB dữ liệu payload chưa kể overhead
~~~

Đây là **phép tính minh họa**, không phải metric thu được từ hệ thống. Copy toàn bộ byte[]/multipart input vào mỗi task có thể tăng mạnh bộ nhớ và gây GC pressure.

Với file:

1. Xác thực kích thước, định dạng và quyền truy cập **ở ranh giới request**.
2. Nếu worker chạy sau khi request kết thúc, **đừng dựa vào tài nguyên tạm của MultipartFile/request InputStream** còn tồn tại.
3. Khi phù hợp, đưa file vào storage có vòng đời rõ, ghi **objectKey, checksum, size, version** trong snapshot, và chỉ để worker tải phần cần dùng.
4. Bảo vệ storage key bằng quyền truy cập, không đặt nội dung nhạy cảm vào log hay URL công khai.
5. Xác định cleanup nếu task lỗi/cancel, nếu job không được nhận hoặc đã hoàn thành.

Với secret như token/password, snapshot chỉ những gì worker thực sự có quyền và cần sử dụng. Nếu có thể dùng **reference tới secret manager**, access scope ngắn, token riêng cho task hoặc cơ chế cấp lại theo quyền, đừng đưa cả request headers/cookie và access token vào hàng đợi dài hạn.

## 14. Cancellation, timeout và overload không bảo đảm dữ liệu được sử dụng

Ngay cả khi đã chụp snapshot chính xác, tác vụ có thể **không bao giờ thực thi**:

- Executor có thể reject task vì quá tải hoặc shutdown.
- Future có thể bị cancel; task có thể bị ngắt hoặc vẫn tiếp tục tùy implementation và tình trạng I/O.
- Worker có thể gặp lỗi trước khi dùng snapshot.
- Deadline ứng dụng có thể hết trước khi task bắt đầu.
- Executor queue có thể tích tụ backlog, khiến snapshot quá cũ so với nghiệp vụ.

Do đó, cần tách các câu hỏi:

| Câu hỏi | Cơ chế liên quan |
| --- | --- |
| Dữ liệu gửi đi có độc lập và đúng thời điểm? | Snapshot/immutability/ownership |
| Tác vụ đã được executor chấp nhận? | Submit result, TaskRejectedException, metrics |
| Tác vụ đã thực sự hoàn thành? | Future/CompletableFuture, trạng thái job |
| Tác vụ hoàn tất trong deadline? | Timeout/cancellation/queue wait |
| Tác vụ không mất khi ứng dụng restart? | Durable queue/job store/outbox |
| Tác vụ có quyền đọc/ghi tài nguyên? | Authorization tại thời điểm phù hợp |

**Snapshot đúng không tự giải quyết delivery semantics**. Người dùng gửi request và nhận HTTP 202 Accepted chỉ khi ứng dụng thật sự đã **tiếp nhận công việc theo hợp đồng**; một lệnh submit thất bại hoặc một Runnable bị drop im lặng không phải thành công nghiệp vụ.

## 15. Khi snapshot trở nên “cũ”: thời điểm và hợp đồng nghiệp vụ

“Chụp trước khi sang luồng khác” không có nghĩa dữ liệu đó **mãi đúng**. Ví dụ:

- **Đúng theo thời điểm submit:** một email xác nhận phải chứa danh sách sản phẩm và giá đã xác nhận lúc đặt hàng. Snapshot versioned/committed giúp tái hiện đúng nội dung lịch sử.
- **Đúng theo thời điểm xử lý:** một thao tác khóa tài khoản hoặc thanh toán có thể cần kiểm tra lại trạng thái hiện tại và quyền, ngay khi task chạy.
- **Cần kết hợp hai loại dữ liệu:** worker nhận immutable command cho ý định của người dùng, nhưng vẫn tải lại trạng thái hiện tại để kiểm tra điều kiện thực hiện.
- **Có retry:** mỗi lần retry nên biết giữ snapshot gốc hay làm mới dữ liệu; thay đổi ngầm sau retry có thể gây kết quả khác với yêu cầu ban đầu.

Trong thiết kế tốt, **snapshot thể hiện một contract**: ai tạo, thời điểm tạo, những field nào là giá trị cố định, field nào cần refresh, và ai chịu trách nhiệm xác thực/ghi nhận.

Nếu ứng dụng có nhiều bước bất đồng bộ, không nhất thiết dùng lại duy nhất một object chứa mọi dữ liệu từ đầu đến cuối. Có thể dùng **những DTO bất biến theo từng stage**, vừa đủ dùng, giảm rủi ro lộ dữ liệu sang thành phần không cần.

## 16. Test tốt: cố tình thay đổi dữ liệu gốc sau khi submit

Để biết hệ thống thật sự tách input khỏi task, test theo trình tự **có điều phối**:

1. Tạo input mutable có giá trị **A**.
2. Chụp snapshot đúng cách trước khi submit.
3. Gửi worker nhưng giữ worker chờ bằng **CountDownLatch**.
4. Sửa dữ liệu gốc sang **B**, bao gồm nested collection/element có nguy cơ shared.
5. Mở latch, để worker đọc snapshot.
6. Assert worker thấy **A**, trong khi caller nhìn nguồn **B**.
7. Dùng **Future.get(timeout)** để tránh test treo; release latch và shutdown executor trong finally.

Những ví dụ ở mục 3, 4 và 5 đã kiểm tra lần lượt lỗi **captured reference**, cách sửa bằng **List.copyOf với phần tử immutable**, và lỗi **shallow copy với phần tử mutable**.

**Test đồng thời không thể chỉ dựa vào Thread.sleep.** Nếu may mắn worker chạy trước khi caller mutate, test sẽ pass dù thiết kế sai. Latch/Barrier giúp đặt chính xác điểm xen kẽ. Đồng thời, phải test cả tình huống **copy trong lúc dữ liệu đang bị writer khác mutate**; nếu không có ownership hoặc lock, một copy API chưa bảo đảm consistency.

Với ứng dụng Spring, test thêm:

- Gọi **@Async** thông qua **Spring-managed proxy**, không chỉ gọi trực tiếp new Service().
- Đảm bảo worker không đụng **request scope/transaction cũ** khi request đã kết thúc.
- Với payload lớn, đo memory footprint của queue và bản snapshot.
- Khi submit bị **TaskRejectedException**, xác nhận chưa báo công việc đã được nhận.
- Khi task lỗi/cancel, cleanup tài nguyên tham chiếu trong snapshot có được thực hiện không.

## 17. Những lỗi tư duy thường gặp

**“Lambda capture biến effectively final nên dữ liệu bất biến.”** Sai. Effectively final chỉ nói về việc gán lại biến local; object mà nó trỏ đến vẫn có thể bị thay đổi.

**“Record làm tất cả các field sâu bên trong trở thành immutable.”** Sai. Record chỉ giữ các component references cố định; collection và object nested vẫn mutable nếu không copy/phòng vệ.

**“List.copyOf trên List<MutableDTO> bảo đảm mọi dữ liệu đã frozen.”** Sai. Nó không clone các phần tử.

**“Collections.unmodifiableList(source) là snapshot.”** Không nhất thiết. View unmodifiable có thể tiếp tục phản ánh thay đổi trên source list.

**“Cứ gọi submit trước thì worker chắc chắn đọc dữ liệu trước khi caller thay đổi.”** Sai. Executor có thể hoãn chạy task; ngay cả khi chạy đồng thời, thiếu ownership/đồng bộ vẫn nguy hiểm.

**“ExecutorService có happens-before nên không cần immutable snapshot.”** Sai. Happens-before hỗ trợ visibility cho các hành động đúng thứ tự, không tạo isolation cho các lần ghi diễn ra sau submit.

**“@Async tự mang HttpServletRequest, transaction và mọi ThreadLocal sang worker.”** Không được bảo đảm. Những context đó có phạm vi và cơ chế propagation riêng.

**“Chuyển JPA entity qua executor không sao nếu chỉ đọc.”** Không thể kết luận như vậy; proxy lazy loading, persistence context và các trạng thái mutable/transaction boundary có thể làm hành vi sai.

**“Chụp toàn bộ request để chắc chắn không thiếu dữ liệu.”** Không tốt: request chứa quá nhiều thông tin, có thể bao gồm token, stream, object sống ngắn và payload lớn.

**“Sao chép byte[] một lần là immutable mãi mãi.”** Nếu trả lại byte[] nội bộ hoặc worker tiếp tục mutate nó, dữ liệu vẫn thay đổi. Cần defensive copy khi nhập/xuất hoặc hợp đồng ownership nghiêm ngặt.

**“Một snapshot luôn đúng cho mọi retry.”** Không hẳn. Task có thể cần phiên bản tại thời điểm submit hoặc trạng thái mới nhất; phải xác định yêu cầu nghiệp vụ.

**“Gửi được Runnable tức là job đã durable.”** Sai. Executor trong RAM không thay thế hàng đợi công việc có lưu trữ bền vững.

## 18. Bài tập áp dụng

**Bài 1.** Controller tạo `List<String> filters`, rồi gọi `executor.execute(() -> search(filters))`. Sau đó controller thêm phần tử vào filters. Có thể xem filters đã được chụp chưa?

**Đáp án:** Chưa. Lambda giữ cùng tham chiếu List. Nếu worker cần nội dung tại thời điểm submit, tạo **List.copyOf(filters)** trước execute (và chắc chắn không có thread khác đang sửa filters khi copy).

**Bài 2.** Code dùng `final MutableOrder order` rồi truyền order tới @Async. Từ khóa final đã đủ chưa?

**Đáp án:** Chưa. Nó chỉ ngăn gán lại tham chiếu order trong phạm vi khai báo; các setter/nested collections của MutableOrder vẫn có thể bị thay đổi.

**Bài 3.** Dùng `List.copyOf(lines)` với mỗi Line có trường price mutable. Worker có thấy price mới sau khi caller đổi không?

**Đáp án:** Có thể. List mới giữ cùng các Line instances. Cần chuyển từng Line thành immutable snapshot của những giá trị worker cần.

**Bài 4.** Một worker gửi email sau một giờ cần dùng tên người nhận và địa chỉ ở đúng thời điểm đơn hàng được đặt. Chỉ gửi customerId rồi query hiện tại có đúng không?

**Đáp án:** Không chắc. Nếu khách đổi thông tin trong giờ đó, kết quả sẽ khác. Cần mô hình snapshot versioned/committed của dữ liệu đã xác nhận hoặc bản ghi lịch sử để giữ đúng ngữ nghĩa.

**Bài 5.** Worker chỉ cần orderId để tải trạng thái đơn hàng mới nhất và quyết định hành động. Có nhất thiết copy toàn bộ order DTO không?

**Đáp án:** Không. Chỉ gửi ID và command metadata tối thiểu, rồi tải lại dữ liệu trong transaction của worker, kiểm tra version/authorization thích hợp.

**Bài 6.** Vì sao một bản copy bằng `new ArrayList<>(sharedList)` chưa chắc có tính nhất quán?

**Đáp án:** Nếu thread khác đồng thời mutate sharedList mà không đồng bộ, quá trình copy có thể quan sát trạng thái không nhất quán. Cần ownership, lock hoặc immutable publication.

**Bài 7.** Snapshot chứa List.copyOf(strings) nhưng một phần tử String bị gán lại trong biến nguồn. Worker có thấy String mới không?

**Đáp án:** Không thông qua List snapshot đã tạo: String immutable và List copy giữ các phần tử đã có tại thời điểm tạo. Gán lại biến nguồn không thay giá trị String được giữ trong snapshot.

**Bài 8.** Tại sao không truyền trực tiếp HttpServletRequest hoặc EntityManager vào CompletableFuture chạy sau HTTP response?

**Đáp án:** Chúng có scope/vòng đời riêng, không được thiết kế để tùy ý truy cập trên worker sau khi request/transaction kết thúc. Chụp DTO/ID cần thiết và thiết lập lại ranh giới tài nguyên tại worker.

**Bài 9.** Đã chụp input đúng nhưng executor từ chối task. Có thể trả “đã xử lý” không?

**Đáp án:** Không. Chụp input đúng chỉ bảo đảm tính phù hợp của dữ liệu **nếu task được thực thi**. Phải xử lý trạng thái tiếp nhận, lỗi, retry và delivery semantics riêng.

## 19. Checklist trước khi giao tác vụ sang thread khác

- [ ] Xác định chính xác **task cần những trường nào**.
- [ ] Đọc dữ liệu **khi nguồn còn có chủ sở hữu và phạm vi hợp lệ**.
- [ ] Kiểm tra/chuẩn hóa input, tạo snapshot **trước submit**.
- [ ] Đảm bảo **immutable** ở mọi lớp cần thiết; phòng vệ với nested mutable objects.
- [ ] Không copy một collection đang bị sửa đồng thời nếu thiếu synchronization.
- [ ] Không đưa request/session/EntityManager/stream sống ngắn vào task tùy ý.
- [ ] Không sao chép secret, token và dữ liệu cá nhân dư thừa.
- [ ] Giới hạn dung lượng snapshot và số task tồn đọng.
- [ ] Xác định dữ liệu **cố định lúc submit** hay **cần tải lại lúc thực thi**.
- [ ] Có hợp đồng xử lý **rejection, timeout, cancel, retry và shutdown**.
- [ ] Kiểm thử bằng latch để xác minh sửa nguồn sau submit **không đổi dữ liệu task**.
- [ ] Gắn observability/tracing bằng cơ chế propagation an toàn khi cần, không coi nó là thay thế của snapshot.

## Kết luận

**“Chụp dữ liệu đầu vào phù hợp trước khi chuyển sang luồng khác”** nghĩa là xác định **thời điểm**, **quyền sở hữu**, **mức bất biến** và **vòng đời** của những giá trị worker cần. Với Java ExecutorService, CompletableFuture và Spring @Async, lambda hoặc proxy **không tự sao chép toàn bộ mutable input**. Cách tiếp cận mặc định tốt là **chuẩn hóa và xác thực dữ liệu trên request thread, tạo immutable DTO/record đủ dùng với defensive copies, rồi truyền snapshot qua tham số cho worker**. Khi dữ liệu lớn, thay đổi theo nghiệp vụ hoặc phải durable, dùng ID/version, kho lưu trữ phù hợp và transaction boundary riêng.

Một snapshot đúng không chỉ ngăn race condition: nó giúp tránh rò rỉ request context, giảm lỗi sau khi request/transaction kết thúc, hỗ trợ kiểm thử có tính xác định và làm rõ hợp đồng giữa luồng gửi với luồng xử lý.

**Tài liệu tham khảo kỹ thuật:** Java SE 17 API của **ExecutorService**, **Future**, **List.copyOf**, **Arrays.copyOf**, **CountDownLatch**; Spring Framework reference về **Task Execution and Scheduling**, **@Async**, request scopes và Spring MVC asynchronous requests; hướng dẫn Java Memory Model.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-submission-008">Không đồng nhất tác vụ đã được nhận với nghiệp vụ đã hoàn thành</a>

<details>
<summary>Xem chi tiết</summary>

# Không đồng nhất tác vụ đã được nhận với nghiệp vụ đã hoàn thành

Một ứng dụng Spring nhận yêu cầu tạo báo cáo, gửi email hoặc xử lý đơn hàng, sau đó gọi `executor.execute(task)` hay một phương thức `@Async`. Lệnh giao việc trả về mà không ném lỗi. **Có thể trả ngay “đã xử lý thành công” cho khách hàng không?** Không. Ở thời điểm đó, hệ thống thường mới biết **tác vụ đã được executor nhận**, chứ chưa biết worker đã chạy, xử lý xong, ghi dữ liệu thành công hay nghiệp vụ đã đạt trạng thái cuối.

Đây là một ranh giới rất quan trọng của mô hình xử lý bất đồng bộ trong **Spring Framework – concurrency**, thuộc nhóm **Gửi và nhận tác vụ**. Bài học phân biệt bốn lớp thông tin: **tiếp nhận**, **thực thi**, **kết quả kỹ thuật** và **kết quả nghiệp vụ có thể kiểm chứng**. Từ đó thiết kế API, trạng thái job, xử lý lỗi, retry và khả năng phục hồi đúng kỳ vọng.

## 1. Một task đi qua nhiều trạng thái, không chỉ “thành công/thất bại”

Xét một HTTP request yêu cầu xuất báo cáo:

```text
Client                    Spring API              Executor            Worker / DB / Storage
  |                           |                       |                       |
  | POST /reports             |                       |                       |
  |-------------------------->|                       |                       |
  |                           | submit(task)          |                       |
  |                           |---------------------->|                       |
  |                           |<--- accepted ----------|                       |
  |<--- 202 Accepted + jobId--|                       |                       |
  |                           |                       |-- queue / start ------>|
  |                           |                       |                       | xử lý...
  |                           |                       |                       | ghi file / DB
  | GET /jobs/{id}            |                       |                       |
  |-------------------------->| đọc trạng thái        |                       |
  |<--- RUNNING / SUCCEEDED --|<-----------------------------------------------|
```

**202 Accepted** ở hình là ví dụ cho một API có giao thức job nền. Nó **không có nghĩa tác vụ đã hoàn thành**. Với kết quả chỉ nằm trong RAM, 202 cũng **không mặc nhiên bảo đảm sẽ sống sót qua crash**.

Một mô hình trạng thái thường gặp:

| Trạng thái | Ý nghĩa | Đã hoàn thành nghiệp vụ? |
| --- | --- | --- |
| `NEW` / `VALIDATING` | Kiểm tra dữ liệu, quyền, quota trước lúc nhận | Chưa |
| `ACCEPTED` | Hệ thống xác nhận đã nhận yêu cầu theo hợp đồng được công bố | Chưa |
| `QUEUED` | Task đã vào hàng chờ của executor/queue | Chưa |
| `RUNNING` | Worker đang thực thi | Chưa |
| `RETRYING` | Một lần thử thất bại, đang chuẩn bị thử lại | Chưa |
| `SUCCEEDED` | Điều kiện nghiệp vụ thành công đã được thỏa và có bằng chứng phù hợp | Có, theo hợp đồng nghiệp vụ |
| `FAILED` | Không đạt điều kiện thành công theo chính sách hiện tại | Không |
| `CANCELLED` | Đã hủy thành công theo ý nghĩa mà ứng dụng định nghĩa | Không nhất thiết “hoàn tác” tác động đã xảy ra |
| `EXPIRED` | Quá thời hạn cho phép xử lý/đợi | Chưa chắc tác động bên ngoài chưa xảy ra |

Không bắt buộc mọi hệ thống có tất cả trạng thái. Quan trọng hơn là **mỗi trạng thái có định nghĩa và điều kiện chuyển rõ ràng**. Ví dụ, một job chỉ được chuyển `SUCCEEDED` khi việc lưu báo cáo thực sự hoàn tất và có thể truy xuất được, không phải ngay sau khi caller submit task.

### Bốn câu cần phân biệt

1. **Đã chấp nhận yêu cầu chưa?** API/executor có cho công việc vào hệ thống hay trả lỗi ngay?
2. **Đã bắt đầu thực hiện chưa?** Worker đã lấy task khỏi queue hay chưa?
3. **Đã kết thúc thực thi chưa?** Phương thức `run`/`call` đã hoàn tất bình thường, ném lỗi hay bị hủy?
4. **Nghiệp vụ đã thành công chưa?** Tác động cần thiết như chuyển trạng thái đơn hàng, lưu chứng từ, gửi đúng thông báo hoặc xác nhận thanh toán đã đạt cam kết nghiệp vụ và có thể chứng minh chưa?

Bốn câu này có thể có bốn câu trả lời khác nhau tại cùng một thời điểm.

## 2. `execute` hoặc `submit` trả về thực sự nói gì?

Với một thread pool thông thường:

```java
executor.execute(() -> generateReport(reportId));
```

Khi `execute` trả về bình thường trong cấu hình ném lỗi khi từ chối, điều đó cho biết task đã được chấp nhận theo quy tắc executor, **không chứng minh `generateReport` đã chạy hoặc thành công**. Task có thể đang chờ trong queue. Trong chính sách `CallerRunsPolicy`, task có thể chạy ngay trên caller lúc pool bão hòa; vì vậy cũng không được mặc định “`execute` luôn return ngay”. Còn với handler `DiscardPolicy`, một task bị bỏ có thể không tạo exception, nên phải hiểu **chính sách từ chối cụ thể**, không coi “không throw” là bảo đảm nhận việc trong mọi cấu hình.

Tương tự:

```java
Future<String> future = executorService.submit(() -> generateReport());
```

`submit` thường trả một `Future` đại diện cho kết quả *sẽ có*. Nó không tự chờ worker chạy xong. Để biết kết quả, caller cần quan sát `get()`, `isDone()` và trạng thái exception/cancellation.

**Bảng so sánh:**

| Lệnh/thông tin | Điều biết được | Điều không thể suy ra |
| --- | --- | --- |
| `execute(task)` không ném lỗi, AbortPolicy | Task đã được executor nhận hoặc đã chạy trên cơ chế được cấu hình | Tác động nghiệp vụ đã hoàn thành |
| `submit(callable)` trả `Future` | Có handle để quan sát xử lý | Task đã kết thúc thành công |
| `future.isDone() == true` | Future đã ở trạng thái cuối (có thể hoàn tất, thất bại, hủy) | Nghiệp vụ thành công |
| `future.get()` trả giá trị bình thường | Callable đã trả về bình thường; có quan hệ memory-consistency phù hợp | Giao dịch DB, API ngoài hoặc side effect đã bền vững nếu code chưa bảo đảm |
| `future.get()` ném `ExecutionException` | Task kết thúc với exception được bao bên trong | Tác động bên ngoài chắc chắn chưa xảy ra |
| `CompletableFuture.isCompletedExceptionally()` | Future hoàn tất theo đường lỗi hoặc hủy tùy tình huống | Tác động nghiệp vụ đã được hoàn tác |
| HTTP 202 từ API job | Yêu cầu đã được chấp nhận **theo hợp đồng API** | Công việc đã xử lý xong hoặc sẽ chắc chắn chạy một lần duy nhất |

Đặc biệt, một task có thể thực hiện xong side effect rồi **bị lỗi khi ghi trạng thái kết quả**, khiến hệ thống báo `FAILED` dù side effect thực sự đã xảy ra. Ngược lại, code có thể return bình thường nhưng **chưa kiểm chứng** việc dịch vụ ngoài đã thực hiện yêu cầu.

## 3. Ba tầng “hoàn thành” khác nhau

### 3.1. Hoàn thành ở tầng executor

Worker chạy xong `Runnable.run()` hoặc `Callable.call()`. `Future` chuyển trạng thái cuối. Đây là trạng thái **kỹ thuật của tác vụ**.

```java
CompletableFuture<String> task =
        CompletableFuture.supplyAsync(() -> "done", executor);
```

Ở đây `"done"` chỉ là giá trị mà lambda trả về. Executor không biết từ `"done"` có nghĩa **báo cáo đã lưu thành công** hay không.

### 3.2. Hoàn thành ở tầng ứng dụng

Ứng dụng kiểm tra các bước cụ thể: tạo file, ghi storage, cập nhật DB, nhận response từ downstream, ghi lịch sử tác vụ, v.v. Nếu từng bước trả về thì application có thể đánh giá kết quả theo chính sách của mình.

Tuy nhiên, “gửi HTTP request tới nhà cung cấp email thành công” có thể chỉ nghĩa **nhà cung cấp đã nhận request**. Nó chưa chắc email đã được phát đến hộp thư người nhận. Mỗi hệ thống ngoài có ngữ nghĩa xác nhận khác nhau.

### 3.3. Hoàn thành ở tầng nghiệp vụ

Một yêu cầu tạo báo cáo có thể chỉ được xem là hoàn thành khi:

- File thực sự tồn tại ở nơi lưu trữ dự kiến.
- Metadata/file ID liên kết tới job đã được commit/ghi bền vững.
- Người có quyền có thể tải/tra cứu file theo hợp đồng.
- Trạng thái job được cập nhật theo điều kiện thành công này.

Với nghiệp vụ thanh toán, “đã gửi yêu cầu thanh toán” khác hoàn toàn “ngân hàng/PSP xác nhận giao dịch thành công”. Với nghiệp vụ gửi email, “đã enqueue vào email provider” khác “đã được phát”; có thể có trạng thái riêng như `SUBMITTED_TO_PROVIDER`, `DELIVERED`, `BOUNCED`.

**Nguyên tắc:** định nghĩa `SUCCEEDED` bằng **bằng chứng nghiệp vụ** thay vì bằng việc `executor.execute` return.

## 4. Ví dụ Java 17+ chạy độc lập: accepted ≠ done, done ≠ success

Ví dụ sau dùng JDK 17+ để minh họa rõ hai sự tách biệt. Một task được `submit` thành công nhưng còn đang chờ phép giải phóng có kiểm soát; một task khác **đã kết thúc nhưng kết thúc bằng exception**.

Lưu nội dung thành **AcceptedVsCompletedDemo.java**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class AcceptedVsCompletedDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean businessStepFinished = new AtomicBoolean(false);

        try {
            Future<?> accepted = pool.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                businessStepFinished.set(true);
            });

            started.await();
            System.out.println("accepted=true, done="
                    + accepted.isDone()
                    + ", businessStepFinished="
                    + businessStepFinished.get());

            release.countDown();
            accepted.get();
            System.out.println("accepted=true, done="
                    + accepted.isDone()
                    + ", businessStepFinished="
                    + businessStepFinished.get());

            Future<?> failed = pool.submit(() -> {
                throw new IllegalStateException("simulated failure");
            });
            try {
                failed.get();
            } catch (ExecutionException ex) {
                System.out.println("failedFutureDone="
                        + failed.isDone()
                        + ", taskSucceeded=false");
            }
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac AcceptedVsCompletedDemo.java
java AcceptedVsCompletedDemo
```

**Stdout xác định khi chương trình chạy bình thường:**

```text
accepted=true, done=false, businessStepFinished=false
accepted=true, done=true, businessStepFinished=true
failedFutureDone=true, taskSucceeded=false
```

**Vì sao kết quả đáng tin?**

1. `pool.submit(...)` trả về một `Future`. `started.await()` bảo đảm worker đã bắt đầu task nhưng vẫn bị giữ tại `release.await()`.
2. Khi latch chưa mở, task chưa thể chạy đến dòng đặt `businessStepFinished=true`; `Future.isDone()` là `false`. Việc đã nhận task **không bằng** việc hoàn thành.
3. Sau `release.countDown()`, `Future.get()` chờ tác vụ kết thúc và cho caller quan sát lần ghi vào `AtomicBoolean`. Khi ấy cả `done` và cờ mô phỏng đều `true`.
4. Task thứ hai cố tình ném `IllegalStateException`. `failed.get()` ném `ExecutionException`; Future đã `done=true` nhưng **không phải success**.
5. `CountDownLatch` loại bỏ sự may rủi khi đo trạng thái. Không dùng `Thread.sleep(100)` để đoán chắc worker đã chạy tới đâu.

**Giới hạn cố ý của demo:** biến `businessStepFinished` là một cờ **trong bộ nhớ**, chỉ mô phỏng việc hoàn thành. Nó không chứng minh database đã commit hoặc email đã tới người nhận. Trong ứng dụng thật, trạng thái nghiệp vụ phải dựa trên thao tác kiểm chứng tương ứng.

## 5. `@Async` của Spring và ý nghĩa của kết quả Future

Spring hỗ trợ thực thi method bất đồng bộ qua `@EnableAsync` và `@Async`:

```java
@Service
class ReportService {
    @Async("reportExecutor")
    public CompletableFuture<String> generateReport(String reportId) {
        String url = calculateAndStoreReport(reportId);
        return CompletableFuture.completedFuture(url);
    }

    private String calculateAndStoreReport(String reportId) {
        // Fragment: cần cài đặt nghiệp vụ và lưu trữ thực tế.
        return "/reports/" + reportId;
    }
}
```

Đây là **fragment minh họa**, không phải chương trình hoàn chỉnh: import và executor bean được giả định tồn tại; `calculateAndStoreReport` ở đây chỉ trả chuỗi, **không lưu file thật**. Nó giúp hiểu cách Spring proxy tạo một lời gọi bất đồng bộ và trả kết quả khi method chạy xong.

Khi bean khác gọi `reportService.generateReport(id)` qua Spring proxy, một task được giao cho `reportExecutor`. Caller nhận một `CompletableFuture` để quan sát; điều đó **không tự làm cho báo cáo có thật**. Khi method return bình thường, future đại diện cho completion của đường thực thi Spring, nhưng **ngữ nghĩa nghiệp vụ** vẫn phụ thuộc phần code đã làm và giao dịch/tài nguyên bên ngoài.

**Những điểm dễ sai:**

- Trong cấu hình proxy mặc định, `this.generateReport(id)` được gọi từ chính bean đó thường **không đi qua proxy `@Async`**. Không dựa vào self-invocation để suy luận executor có nhận task hay không.
- `@Async void` không cung cấp Future cho caller, nên không thích hợp nếu caller phải biết kết quả. Exception với kiểu return `void` cần `AsyncUncaughtExceptionHandler` và logging/monitoring phù hợp.
- Khi return `CompletableFuture<T>`, phải quan sát `whenComplete`, `handle`, `exceptionally` hoặc `join/get` tại vị trí thích hợp; nếu không kiểm tra, task lỗi có thể trôi qua trong log mà người dùng vẫn thấy “thành công”.
- `@Async` dùng worker khác không làm JDBC/HTTP blocking trở thành non-blocking. Worker vẫn bận chờ I/O và executor có thể bão hòa.
- Nếu dùng `CallerRunsPolicy` để xử lý overload, tác vụ có thể thực thi trên thread gọi khi pool đầy; còn với `AbortPolicy`, giao việc có thể thất bại ngay với `TaskRejectedException`.
- Future trong một JVM không phải job state bền vững sau restart và không thể hiện tự động trạng thái trên các replica khác.

### 5.1. Future đã hoàn tất nhưng transaction đã commit chưa?

Không nên đánh đồng “method return” với “dữ liệu đã được commit” khi giao dịch và proxy/side effect không nằm trong cùng điều kiện kiểm chứng. Ví dụ một method transactional ghi database rồi **phát tín hiệu `SUCCEEDED` ra ngoài ngay trong method**, trước khi transaction thực sự commit. Nếu transaction rollback khi kết thúc, tín hiệu đã phát sẽ là sai.

Cách an toàn hơn tùy thiết kế:

1. **Trạng thái job và kết quả nghiệp vụ trong cùng transaction DB:** commit thành công rồi dữ liệu mới hiển thị cho reader. Nếu rollback, cả hai cùng rollback.
2. **Có hoạt động ngoài DB:** sau khi xác nhận hoạt động đó theo contract của provider, mới đánh dấu thành công; phải dự kiến kết quả không chắc chắn khi timeout.
3. **Sự kiện chỉ được phát sau commit:** có thể dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` cho event trong tiến trình khi phù hợp, nhưng event listener in-memory có thể mất khi crash. Với yêu cầu bền vững nên dùng transactional outbox/broker.
4. **Nếu trạng thái completion được kiểm chứng bên ngoài:** chỉ chuyển `SUCCEEDED` khi có chứng cứ đủ mạnh, chẳng hạn báo cáo đã nằm trong object storage và metadata đã nhất quán.

Không có annotation nào của Spring tự động biến một chuỗi DB + HTTP + broker thành một giao dịch nguyên tử trên mọi hệ thống.

## 6. HTTP 202 Accepted và job API: trả lời thật đúng điều đã làm

Khi API nhận việc cần xử lý nền, có thể dùng:

```http
POST /jobs HTTP/1.1
Content-Type: application/json

{"reportType":"monthly"}
```

Nếu API đã nhận yêu cầu **theo hợp đồng** và cung cấp đường theo dõi, một response thường là:

```http
HTTP/1.1 202 Accepted
Location: /jobs/8f2a

{"jobId":"8f2a","statusUrl":"/jobs/8f2a"}
```

Client sau đó truy vấn:

```http
GET /jobs/8f2a HTTP/1.1
```

Có thể nhận:

```json
{"jobId":"8f2a","state":"RUNNING","message":"Generating report"}
```

Và về sau:

```json
{"jobId":"8f2a","state":"SUCCEEDED","message":"Report stored"}
```

**Đây là giao thức minh họa**, không phải log của một server đang chạy. Cách đặt trạng thái, payload và đường dẫn cần được thiết kế theo hợp đồng thực tế.

### Không dùng 200, 201, 202 thay thế tùy tiện

- **200 OK:** chỉ phản ánh request HTTP thành công theo response hiện tại; nếu body tuyên bố “báo cáo xong”, chỉ nên trả sau khi điều kiện thành công thực sự đã được kiểm chứng.
- **201 Created:** có thể nói một **tài nguyên job** đã được tạo, nhưng không có nghĩa *bản báo cáo* đã tạo xong. Phân biệt đối tượng “job” với kết quả của job.
- **202 Accepted:** chuẩn HTTP mô tả yêu cầu đã được chấp nhận để xử lý nhưng **chưa hoàn tất tại thời điểm phản hồi**; nó mang tính **noncommittal**, không phải cam kết tiến trình cuối cùng chắc chắn sẽ thành công.
- **400/422:** request không hợp lệ theo yêu cầu API.
- **429 Too Many Requests:** ứng dụng áp dụng giới hạn tốc độ/quota của client.
- **503 Service Unavailable:** backend tạm không có capacity/không tiếp nhận được công việc; cân nhắc `Retry-After` nếu có chính sách retry.
- **404 Not Found khi GET status:** có thể là job không tồn tại, bị xóa hết thời hạn hoặc client đang truy cập nhầm replica của hệ thống in-memory. Phải xác định ý nghĩa cụ thể trong API.

Không trả 202 nếu thực tế **chưa chấp nhận task** (executor từ chối hoặc lưu job thất bại), trừ khi hệ thống đã đưa nó vào một cơ chế tiếp nhận thay thế hợp lệ và có thể theo dõi được.

### Trường hợp client timeout sau khi server đã nhận

Client gửi POST, server enqueue thành công, nhưng mạng đứt trước khi response 202 tới client. Client nghĩ “không biết đã nhận hay chưa” rồi retry. Nếu mỗi POST tạo một job mới, báo cáo/email có thể bị xử lý **hai lần**. Cần **idempotency key** hoặc request ID ổn định để lần gửi lại có thể tìm kết quả của lần đầu.

Đó là lý do **nhận được HTTP response** và **server đã nhận việc** cũng có thể là hai góc nhìn khác nhau khi mạng lỗi.

## 7. Spring Boot 3.x: API báo “đã nhận” và cho tra trạng thái

Ví dụ sau dùng **Java 17+**, Spring Boot 3.x/Spring Framework 6.x và dependency `spring-boot-starter-web`. Mục đích là thể hiện **cách nói đúng về accepted vs completed** với `ThreadPoolTaskExecutor` và trạng thái job.

**Chú ý:** ví dụ dùng `ConcurrentHashMap` để lưu status **chỉ trong RAM**, nên không bảo đảm bền vững hoặc hoạt động trên nhiều replica. Nó mô phỏng công việc bằng `Thread.sleep`; `SUCCEEDED` chỉ nói rằng phần demo kết thúc bình thường, **không có báo cáo thật được lưu**. Phần 8 giải thích cách nâng cấp lên thiết kế production.

Đặt toàn bộ nội dung trong **src/main/java/com/example/demo/JobApplication.java**:

```java
package com.example.demo;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class JobApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }

    @Bean("jobExecutor")
    ThreadPoolTaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("job-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}

record JobReceipt(String jobId, String statusUrl) {}

record JobStatus(String jobId, String state, String message) {}

@Service
class JobService {
    private final ThreadPoolTaskExecutor executor;
    private final ConcurrentMap<String, JobStatus> jobs =
            new ConcurrentHashMap<>();

    JobService(@Qualifier("jobExecutor") ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    JobReceipt submit() {
        String id = UUID.randomUUID().toString();
        jobs.put(id, new JobStatus(id, "QUEUED", "Waiting for a worker"));

        try {
            executor.execute(() -> perform(id));
        } catch (TaskRejectedException rejected) {
            jobs.remove(id); // Không báo accepted cho task bị từ chối.
            throw rejected;
        }

        return new JobReceipt(id, "/jobs/" + id);
    }

    Optional<JobStatus> find(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    private void perform(String id) {
        jobs.put(id, new JobStatus(id, "RUNNING", "Demo work started"));
        try {
            Thread.sleep(300); // Mô phỏng xử lý, không lưu dữ liệu thật.
            jobs.put(id, new JobStatus(
                    id, "SUCCEEDED", "Demo step finished"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            jobs.put(id, new JobStatus(id, "FAILED", "Interrupted"));
        } catch (RuntimeException ex) {
            jobs.put(id, new JobStatus(id, "FAILED", "Processing error"));
        }
    }
}

@RestController
class JobController {
    private final JobService service;

    JobController(JobService service) {
        this.service = service;
    }

    @PostMapping("/jobs")
    public ResponseEntity<?> create() {
        try {
            JobReceipt receipt = service.submit();
            return ResponseEntity.accepted()
                    .location(URI.create(receipt.statusUrl()))
                    .body(receipt);
        } catch (TaskRejectedException rejected) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "executor_unavailable"));
        }
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobStatus> status(@PathVariable String id) {
        return service.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

Chạy bằng build của project Spring Boot:

```bash
./gradlew bootRun
curl -i -X POST http://localhost:8080/jobs
```

Trên Windows có thể dùng `gradlew.bat bootRun`. Khi executor nhận việc, response có **202 Accepted**, body JSON chứa `jobId` và `statusUrl`, ví dụ:

```json
{"jobId":"b610d4e5-8891-48b1-a63f-632faf38b7cf","statusUrl":"/jobs/b610d4e5-8891-48b1-a63f-632faf38b7cf"}
```

Đây là **ID ví dụ**, không phải ID thực tế cố định. Lấy ID trả về để truy vấn:

```bash
curl -i http://localhost:8080/jobs/<jobId>
```

`<jobId>` trong lệnh trên là **chỗ điền ID thật**, không gõ dấu ngoặc góc trong shell. JSON có thể cho thấy `QUEUED`, `RUNNING` hoặc `SUCCEEDED` tùy thời điểm. Không dự đoán thứ tự log/thread scheduling của từng request. Nếu executor từ chối ngay tại `execute`, API trả **503** với `executor_unavailable`.

### Vì sao ví dụ này không “báo thành công sớm”?

- `submit()` tạo một bản ghi trạng thái `QUEUED` **trước** khi gọi executor, để worker bắt đầu rất nhanh vẫn có record tương ứng.
- Nếu `execute` ném `TaskRejectedException` với `AbortPolicy`, record được xóa và controller không trả 202.
- Nếu được nhận, controller chỉ trả **202 + jobId**, không báo `SUCCEEDED`. Worker tự chuyển `RUNNING` và `SUCCEEDED` sau khi phần demo hoàn thành.
- Nếu worker kết thúc trước khi response HTTP 202 tới client, status GET có thể đã là `SUCCEEDED`. Điều đó **không khiến 202 thành tuyên bố kết quả nghiệp vụ**; response vẫn chỉ xác nhận giao việc theo hợp đồng.
- `ConcurrentHashMap` bảo vệ thao tác đọc/ghi status giữa HTTP thread và worker. Các `JobStatus` là record immutable; không cho caller sửa bản ghi nội bộ.
- Đây là ví dụ **best-effort in-memory**. `jobs` không lưu sau restart, không chia sẻ giữa các server. `SUCCEEDED` ở đây **chỉ là thành công của bước giả lập**.

**Giới hạn cần biết trong hệ thống thật:** Controller trả 202 sau khi `execute` đã nhận vào pool RAM; nếu JVM crash ngay sau đó, job và status đều mất. Muốn bảo đảm nhận việc bền vững, phải thay phần tiếp nhận bằng một transaction/persistent queue phù hợp trước khi trả 202.

## 8. Nâng cấp lên giao thức job bền vững

Giải pháp production thường cần một **job store** có transaction và một worker có khả năng retry. Một thiết kế phổ biến:

```text
Client
  |
  | POST /jobs + Idempotency-Key
  v
API: kiểm tra quyền, payload, quota
  |
  | BEGIN DB transaction
  |   INSERT job(id, status=ACCEPTED, requestHash, ...)
  |   INSERT outbox(jobId, event=JobAccepted, ...)
  | COMMIT
  v
HTTP 202 + jobId
  |
  | outbox publisher (có thể chạy lại)
  v
Message broker / durable queue
  |
  | worker nhận (có thể nhận lặp)
  v
Xử lý nghiệp vụ idempotent
  |
  | Lưu kết quả + trạng thái terminal trong transaction phù hợp
  v
SUCCEEDED / FAILED / RETRYING
  |
  | GET /jobs/{id}
  v
Client thấy trạng thái có thể kiểm chứng
```

### 8.1. Vì sao phải commit trước khi trả accepted?

Nếu chỉ lưu job trong RAM rồi trả 202, một crash có thể làm mất yêu cầu. Nếu lưu database nhưng trả 202 **trước khi transaction commit**, transaction có thể rollback sau response. Vì thế, hợp đồng “đã tiếp nhận bền vững” phải được phát ra **sau một commit thành công**.

Tùy định nghĩa của API, 202 có thể chỉ mang nghĩa best-effort nhận vào RAM. Điều đó không trái với giao thức HTTP nhưng phải minh bạch; không tự hứa “được xử lý chắc chắn” nếu không có cơ chế bảo đảm.

### 8.2. Vì sao cần transactional outbox?

Một lỗi hai bước điển hình:

1. API commit bản ghi job ở database.
2. API cố gửi message đến broker nhưng chết giữa hai bước.

Lúc này DB có job, nhưng broker chưa nhận event. Đảo thứ tự lại (publish trước rồi commit DB) cũng có cửa sổ lỗi: worker nhận event trước khi job tồn tại hoặc transaction rollback.

**Transactional outbox** ghi job + bản ghi event trong **cùng một DB transaction**. Một publisher riêng đọc outbox và gửi đi; có thể gửi lặp khi ack không chắc chắn, nên **consumer phải idempotent**. Outbox không tự tạo exactly-once end-to-end nhưng thu hẹp cửa sổ “DB có job mà không có dấu vết để tiếp tục publish”.

### 8.3. Hoàn tất thực sự ở đâu?

Trong worker, trạng thái `SUCCEEDED` nên được ghi **sau khi hoặc cùng lúc** các điều kiện thành công có bằng chứng:

- Với update DB đơn lẻ, ghi kết quả nghiệp vụ và job status trong cùng transaction khi khả thi.
- Với tạo file ngoài DB, chỉ công bố thành công khi upload được xác nhận theo hợp đồng storage và metadata liên kết với job đã được ghi an toàn. Phải dự phòng trường hợp upload thành công nhưng cập nhật DB thất bại.
- Với gửi mail qua provider, phân biệt provider **chấp nhận request** và **giao email đến người nhận**; trạng thái có thể cần nhiều tầng xác nhận.
- Với thanh toán, không suy luận thành công chỉ từ timeout hay HTTP response chưa đủ; cần đối soát provider transaction ID/trạng thái cuối, và idempotency cho thao tác có tác động tài chính.

**Đừng ghi `SUCCEEDED` trước side effect quan trọng**, sau đó kỳ vọng rollback tự động sửa mọi hệ thống ngoài DB. Nếu side effect đã xảy ra rồi ghi status thất bại, worker retry có thể lặp tác động — phải thiết kế idempotency hoặc compensation tương ứng.

## 9. Retry, lỗi và idempotency

Một tác vụ được nhận có thể thử xử lý nhiều lần. Các lý do phổ biến:

- Worker crash sau khi gọi API ngoài thành công nhưng **trước khi ghi trạng thái hoàn tất**.
- Broker redeliver vì chưa nhận ack.
- Timeout làm caller không biết thao tác ngoài đã xảy ra chưa.
- Người dùng gửi lại POST vì không nhận được response 202.
- Job quá thời hạn hoặc worker được scale/restart.

**At-least-once delivery** nghĩa là một task có thể được gửi/xử lý **nhiều lần**. Mục tiêu đúng thường là **hiệu ứng nghiệp vụ không bị nhân đôi**, không phải khẳng định executor chỉ chạy callback đúng một lần trên toàn hệ thống.

### 9.1. Idempotency key khi nhận job

Client gửi cùng `Idempotency-Key` cho các lần retry của **cùng một yêu cầu logic**. API lưu khóa với request hash và job ID trong DB có unique constraint. Nếu key đã tồn tại và payload trùng, trả lại cùng job ID/trạng thái; nếu key trùng nhưng payload khác, trả lỗi conflict theo hợp đồng.

```text
POST /jobs + key=abc, payload=P → job-123, HTTP 202
Mạng đứt trước khi client nhận response.
POST /jobs + key=abc, payload=P → job-123 (không tạo job thứ hai)
POST /jobs + key=abc, payload=Q → conflict (không tự dùng lại job khác nội dung)
```

Đừng chỉ dùng một `ConcurrentHashMap` in-memory làm kho idempotency cho hệ thống có nhiều replica, vì mỗi replica không nhìn thấy khóa của replica khác.

### 9.2. Idempotency ở worker

Ngay cả khi nhận request chỉ tạo một job, broker vẫn có thể redeliver cùng message. Worker nên dùng khóa ổn định như `jobId`/`paymentIntentId` và guard hiệu ứng nghiệp vụ:

- Kiểm tra trạng thái/deduplication trong transaction.
- Dùng unique constraint hoặc optimistic locking cho thao tác cần đúng một lần về mặt DB.
- Gửi idempotency key đến dịch vụ ngoài nếu provider hỗ trợ.
- Thiết kế retry với exponential backoff, jitter và số lần thử tối đa.
- Đưa nhiệm vụ không thể hoàn tất sang `FAILED` hoặc dead-letter/reconciliation, có công cụ vận hành xử lý.

**Không coi “exactly-once execution” là mặc định của `@Async`, executor hoặc message broker.** Trong thực tế, cần bảo đảm ở cấp **hiệu ứng nghiệp vụ**.

## 10. Khi nào được thông báo “thành công”?

Một quy tắc thực tiễn: thông báo phải phản ánh **điều chắc chắn nhất mà hệ thống đã kiểm chứng**, không phải điều muốn xảy ra.

| Tình huống | Thông báo đúng | Thông báo dễ gây hiểu nhầm |
| --- | --- | --- |
| `executor.execute` nhận task | “Đã nhận tác vụ để xử lý” | “Đã xử lý xong” |
| Task được đưa vào queue bền vững | “Đã ghi nhận yêu cầu, có thể theo dõi bằng job ID” | “Kết quả đã sẵn sàng” |
| Worker bắt đầu tạo báo cáo | “Đang xử lý” | “Báo cáo đã tạo” |
| Worker gửi request sang email provider, provider nhận | “Nhà cung cấp đã nhận yêu cầu gửi” | “Người nhận đã đọc email” |
| File tạo xong, truy cập được và trạng thái commit | “Báo cáo đã sẵn sàng” | Không cần trì hoãn vô cớ |
| `Future.isDone()==true` nhưng Future thất bại | “Xử lý thất bại” | “Hoàn thành thành công” |
| Task được nhận nhưng JVM crash trước khi xử lý | “Trạng thái cần khôi phục/đối soát” | “Thành công” |
| Worker timeout sau khi gọi dịch vụ ngoài | “Chưa xác định, đang kiểm tra trạng thái” | “Chắc chắn thất bại, thử lại ngay” |

Phải xác định *đối tượng của từ “thành công”*: thành công khi **nhận lệnh**, khi **gửi tới provider**, khi **provider thực hiện**, hay khi **khách hàng nhận được kết quả**. Một hệ thống có thể có nhiều trạng thái success khác nhau và không thể thay bằng một boolean mơ hồ.

## 11. Race condition và sự nhất quán trạng thái

Thiết kế job tracking cần đối mặt concurrency thực tế:

### 11.1. Worker bắt đầu trước khi API trả 202

Executor có thể chạy task ngay sau khi submit. Vì vậy, đừng đặt job record **sau** khi submit mà giả định worker chưa chạy. Trong ví dụ Spring, trạng thái `QUEUED` được lưu trước `execute`, sau đó worker cập nhật `RUNNING`. Trả 202 chỉ xác nhận acceptance, dù trạng thái đã tiến tới `RUNNING` hoặc `SUCCEEDED` trước khi response đến client.

### 11.2. Trạng thái có thể bị ghi lùi

Nếu nhiều retry worker và callback cùng sửa một record, có thể xuất hiện:

```text
Worker A: ghi SUCCEEDED
Worker B cũ: ghi RUNNING hoặc FAILED
=> trạng thái bị lùi hoặc ghi đè success
```

Với database, sử dụng **conditional updates**, version/optimistic locking, lease/attempt ID và transition rules. Ví dụ chỉ cho `RUNNING → SUCCEEDED` nếu đúng worker/attempt đang giữ lease, và không cho `SUCCEEDED → RUNNING` do callback cũ.

### 11.3. Trạng thái terminal không đồng nghĩa không còn side effect

Một client gửi cancel khi task đang gọi API ngoài. Worker có thể bị interrupt hoặc Future bị cancel, nhưng HTTP request đã rời hệ thống và provider vẫn xử lý. Do đó **`CANCELLED` không tự bảo đảm các tác động bên ngoài đã bị hoàn tác**. Cần định nghĩa cancellation best-effort và compensation nếu nghiệp vụ yêu cầu.

### 11.4. Multi-instance và visibility

Nếu API có hai replica A/B mà trạng thái job nằm trong memory của A, request `GET /jobs/{id}` đi vào B có thể trả 404 dù job đang chạy ở A. Muốn status nhất quán giữa các replica, dùng shared DB/cache phù hợp, có định nghĩa TTL, version, retention và quyền truy cập.

## 12. Kết quả “đã xong” phải quan sát được

Ngoài trạng thái `SUCCEEDED`, API nên cung cấp đầu ra có thể dùng:

```json
{
  "jobId": "job-123",
  "state": "SUCCEEDED",
  "result": {
    "reportId": "report-456",
    "downloadUrl": "/reports/report-456/download"
  }
}
```

Ví dụ trên là **hợp đồng gợi ý**, không phải response của Spring sample. Chỉ công bố `downloadUrl` khi tài nguyên có thể truy cập theo quyền của caller.

Với `FAILED`, cung cấp mã lỗi/loại lỗi có thể xử lý mà không lộ stack trace hoặc bí mật nội bộ:

```json
{
  "jobId": "job-123",
  "state": "FAILED",
  "error": {
    "code": "REPORT_SOURCE_UNAVAILABLE",
    "retryable": true
  }
}
```

Nên phân biệt lỗi **retryable** (dịch vụ ngoài tạm thời quá tải) với lỗi **permanent** (request không hợp lệ). Không để client tự retry những tác động có thể gây trùng nếu chưa có idempotency.

## 13. Quan sát và vận hành: số “accepted” không phải throughput thành công

Một dashboard chỉ có metric `jobs_submitted_total` sẽ dễ tạo cảm giác hệ thống đang hoạt động tốt dù queue đầy và kết quả nghiệp vụ chậm.

Các chỉ số cần tách:

| Metric | Giải thích |
| --- | --- |
| Requests received | API đã nhận bao nhiêu HTTP request |
| Jobs accepted | Bao nhiêu job được chấp nhận theo hợp đồng API |
| Jobs rejected | Bao nhiêu lần không tiếp nhận do overload/shutdown/validation |
| Jobs started | Bao nhiêu job thực sự được worker bắt đầu |
| Jobs completed successfully | Bao nhiêu job đạt điều kiện thành công nghiệp vụ |
| Jobs failed/cancelled/expired | Các kết thúc không thành công |
| Queue depth/age | Có bao nhiêu task đang chờ, task lâu nhất chờ bao lâu |
| Queue wait duration | Thời gian từ accepted đến started |
| Processing duration | Thời gian từ started đến terminal state |
| End-to-end completion latency | Thời gian từ client gửi yêu cầu đến kết quả có thể dùng |
| Retry count/duplicates | Tỷ lệ xử lý lại, tác động của idempotency |
| “Stuck” jobs | Job quá SLA vẫn `QUEUED` hoặc `RUNNING`, cần recovery |

Một số invariant có thể sử dụng cho **đối soát định kỳ** sau khi quy định rõ cách tính và tránh double counting retry:

```text
Accepted jobs (một job ID duy nhất)
≈ Active jobs + Terminal jobs + Jobs đang cần đối soát

Terminal = SUCCEEDED | FAILED | CANCELLED | EXPIRED (theo contract)
```

Đây chỉ là cách nhìn kiểm toán ở mức job logic, **không phải phép cộng tức thời tuyệt đối** trong hệ thống phân tán có eventual consistency, retry, job deletion và các trạng thái trung gian.

Cần đo cả tỷ lệ hoàn thành theo SLA, tuổi của task cũ nhất trong queue, độ trễ DB/broker và phần trăm request sau khi nhận việc đã cho kết quả cuối. Một hệ thống có 10.000 job accepted nhưng 9.000 job chưa hề hoàn thành chưa thể được mô tả là “đã xử lý thành công 10.000 công việc”.

## 14. Lỗi phổ biến

**Lỗi 1 — Trả “đã gửi email” ngay sau `@Async` submit.** Đúng hơn: “đã nhận yêu cầu gửi”; trạng thái giao email cần nguồn xác nhận riêng.

**Lỗi 2 — `Future.isDone()==true` nên đặt `SUCCEEDED`.** Future có thể bị cancel hoặc failed. Kiểm tra kết quả `get()/join()`, exception và điều kiện nghiệp vụ.

**Lỗi 3 — Method trả về bình thường nên tin mọi side effect đã commit.** Giao dịch có thể chưa commit; provider có thể chỉ trả acknowledgment ban đầu. Bằng chứng thành công phải đúng ranh giới.

**Lỗi 4 — 202 nghĩa bảo đảm thực hiện.** HTTP 202 chỉ nói yêu cầu được chấp nhận để xử lý; mức bảo đảm sống sót qua crash phụ thuộc kiến trúc.

**Lỗi 5 — Executor nhận task vào RAM queue nên coi là durable.** Queue mất khi tiến trình chết; cần DB/broker/outbox cho công việc quan trọng.

**Lỗi 6 — Client timeout thì gửi lại POST mới không có idempotency key.** Có thể sinh hai job cho cùng một yêu cầu logic và gây tác động trùng.

**Lỗi 7 — Worker retry mà không bảo vệ side effect.** Task có thể hoàn thành một phần rồi crash; lần retry có thể tạo thêm báo cáo, thu tiền hoặc gửi email lần hai.

**Lỗi 8 — Cập nhật trạng thái `SUCCEEDED` trước khi ghi file/commit DB.** Trạng thái có thể sai nếu thao tác tiếp theo thất bại hoặc rollback.

**Lỗi 9 — Không cho client truy vấn status.** Response “đã nhận” không có job ID hoặc URL tra cứu khiến không thể biết kết quả cuối.

**Lỗi 10 — Tất cả lỗi dùng chung status `FAILED`.** Không phân biệt retryable/permanent/unknown, mất khả năng recovery hoặc tự động retry hợp lý.

**Lỗi 11 — Một job state in-memory trên nhiều replica.** GET status có thể đi nhầm instance và 404; cần shared job store.

**Lỗi 12 — Không giới hạn backlog và không xử lý rejection.** “Đã nhận” có thể trở thành “xếp hàng vô hạn”; có thể trả 503/429 khi không còn capacity.

**Lỗi 13 — Không thu thập callback error của `@Async`.** Future thất bại sau khi HTTP response đã gửi; cần có trạng thái lỗi, log/metric và cơ chế thông báo.

**Lỗi 14 — Nhầm `CANCELLED` với rollback tác động ngoài.** Interrupt/cancel không thể tự động lấy lại email đã gửi hoặc đảo một giao dịch ngoài hệ thống.

## 15. Checklist thiết kế một API nhận tác vụ

1. Xác định rõ **tiêu chí nghiệp vụ hoàn thành**: điều kiện nào mới cho phép báo `SUCCEEDED`.
2. Định nghĩa **hợp đồng acceptance**: chỉ chấp nhận vào RAM hay đã ghi bền vững? Response phải phản ánh điều đã xảy ra.
3. Cung cấp **job ID**, trạng thái và đường tra cứu; phân biệt queued/running/succeeded/failed.
4. Tách **submit error** (`TaskRejectedException`, validation) khỏi **execution error** (worker exception) và **business failure** (điều kiện nghiệp vụ chưa đạt).
5. Dùng `Future`/`CompletableFuture` để quan sát completion trong JVM, nhưng không coi Future là job store phân tán.
6. Nếu có DB transaction, **không công bố success trước commit**; nếu có broker, cân nhắc transactional outbox.
7. Bảo đảm **idempotency** từ request đến worker khi client retry, broker redeliver hoặc task chạy lại.
8. Đặt timeout, retry/backoff, dead-letter/reconciliation, TTL và quyền truy cập job status rõ ràng.
9. Theo dõi riêng số **accepted**, **started**, **completed**, **failed**, **rejected** và độ trễ end-to-end.
10. Kiểm thử các cửa sổ lỗi: crash sau accepted, crash sau side effect nhưng trước ghi status, transaction rollback, client timeout và concurrent retry.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Controller gọi `executor.execute(task)` và không ném lỗi. Có thể trả HTTP 200 với body `{"status":"COMPLETED"}` không?

**Đáp án:** Chỉ khi nghiệp vụ thực sự đã thỏa điều kiện hoàn thành và đã được kiểm chứng. Việc giao task không lỗi thông thường chỉ chứng minh được acceptance theo policy, không đủ để công bố completed.

**Câu 2.** `Future.isDone()==true` nhưng `future.get()` ném `ExecutionException`. Kết luận gì?

**Đáp án:** Task đã đi đến trạng thái cuối, nhưng thực thi thất bại. Không được coi là thành công nghiệp vụ.

**Câu 3.** Client nhận HTTP 202 từ API ghi task vào in-memory executor. Nếu server crash ngay sau đó, task có chắc được chạy lại?

**Đáp án:** Không. Queue RAM và status in-memory không có cơ chế phục hồi bền vững. Muốn bảo đảm cần persistent job store/broker và retry.

**Câu 4.** Worker gửi lệnh chuyển tiền, network timeout, không rõ provider đã thực hiện chưa. Có thể lập tức đánh dấu `FAILED` và thử lại không?

**Đáp án:** Không nên suy ra chắc chắn giao dịch chưa xảy ra. Cần tra cứu provider status bằng khóa ổn định, áp idempotency và reconciliation để tránh chuyển tiền hai lần.

**Câu 5.** Vì sao `@Async void` không phù hợp khi cần theo dõi job kết quả?

**Đáp án:** Caller không nhận Future kết quả; lỗi có thể đi qua `AsyncUncaughtExceptionHandler`. Cần một cơ chế status bền vững hoặc trả kiểu Future khi phù hợp.

**Câu 6.** Khi worker chuyển job sang `SUCCEEDED` trước khi transaction DB commit, điều gì có thể sai?

**Đáp án:** Nếu transaction rollback, consumer đã thấy thông báo thành công giả. Đảm bảo trạng thái và nghiệp vụ nằm trong commit thích hợp hoặc chỉ phát sự kiện sau commit; với độ bền cao cân nhắc outbox.

**Câu 7.** Hai lần POST có cùng idempotency key và cùng payload nên tạo mấy job?

**Đáp án:** Theo giao thức idempotent được đề xuất, chỉ một job logic; lần retry trả lại job ID/status cũ. Unique constraint và lưu khóa ở nơi dùng chung giúp bảo đảm điều này khi nhiều replica.

**Câu 8.** Vì sao số lượng “accepted” không phản ánh số lượng “completed successfully”?

**Đáp án:** Một task có thể vẫn chờ queue, đang chạy, thất bại, bị hủy hoặc bị mất khi crash. Đếm thành công phải dựa trên terminal state có bằng chứng nghiệp vụ, không phải số lần submit.

## Tóm tắt

**Nhận việc (accepted) là lời hứa về bước tiếp nhận theo hợp đồng; hoàn thành nghiệp vụ (business completed) là kết quả phải có chứng cứ riêng.** `execute`, `submit`, `@Async` và HTTP 202 đều không tự biến một yêu cầu thành nghiệp vụ đã thành công.

Thiết kế tốt phải nối được **request ID → accepted/queued → running → kết quả cuối**, xử lý đúng những lần retry và cửa sổ lỗi, rồi chỉ công bố `SUCCEEDED` khi điều kiện nghiệp vụ thật sự được thỏa. Đó là cách tránh thông báo thành công quá sớm và tránh mất hoặc nhân đôi công việc bất đồng bộ.

## Tài liệu tham khảo

- Java SE 17 — `ExecutorService`, `Future` và memory consistency effects: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Java SE 17 — `Future`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- Java SE 17 — `CompletableFuture`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html
- Spring Framework Reference — Task Execution and Scheduling, `@Async`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework Javadoc — `ThreadPoolTaskExecutor` và `TaskRejectedException`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- HTTP Semantics (RFC 9110) — 202 Accepted: https://httpwg.org/specs/rfc9110.html#status.202


</details>

- [Quay lại đầu trang](#back-to-top)
