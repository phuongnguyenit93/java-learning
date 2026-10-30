<a id="back-to-top"></a>

# Lỗi của lịch chạy

## Menu
- [Lỗi tác vụ chạy một lần khác lỗi tác vụ lặp lại](#scheduling-failures-001)
- [ErrorHandler trong hạ tầng scheduler](#scheduling-failures-002)
- [Phân biệt chính sách ghi nhận rồi tiếp tục với cho lỗi lan ra](#scheduling-failures-003)
- [Không để công việc mất lịch mà không có tín hiệu quan sát](#scheduling-failures-004)
- [Phân biệt thất bại từng lần với trạng thái của cả lịch](#scheduling-failures-005)
- [Thử lại có khoảng nghỉ và giới hạn thời gian](#scheduling-failures-006)
- [Thiết kế tác vụ có thể chạy lại mà không lặp tác dụng phụ](#scheduling-failures-007)
- [Đối chiếu hành vi scheduler Spring với ScheduledExecutorService trực tiếp](#scheduling-failures-008)

## <a id="scheduling-failures-001">Lỗi tác vụ chạy một lần khác lỗi tác vụ lặp lại</a>

<details>
<summary>Xem chi tiết</summary>

# Lỗi tác vụ chạy một lần khác lỗi tác vụ lặp lại trong Spring Framework

## Mục tiêu

Một tác vụ được lập lịch có thể chạy **một lần** hoặc **lặp lại**. Dù cùng sử dụng `ThreadPoolTaskScheduler`, cách Spring xử lý exception **mặc định không giống nhau**:

- **One-shot task:** Spring mặc định **ghi log và ném tiếp lỗi**. Với API programmatic như `TaskScheduler.schedule(Runnable, Instant)`, người giữ `ScheduledFuture` có thể quan sát thất bại bằng `get()` và `ExecutionException`.
- **Repeating task:** Spring mặc định **ghi log nhưng chặn exception**, để một lần thất bại không tự làm mất các lần chạy kế tiếp của lịch lặp.
- **Nếu cấu hình `ErrorHandler` khác**, kết quả có thể thay đổi: handler ném tiếp lỗi từ một lượt định kỳ có thể làm future của periodic registration kết thúc bất thường và không còn chạy các lượt sau.
- **Reactive `@Scheduled`:** tín hiệu `onError` của `Publisher` được xử lý theo hợp đồng reactive riêng; scheduler `ErrorHandler` không phải nơi thông thường nhận tín hiệu đó.

Đây là khác biệt do **chính sách error handling của hạ tầng scheduling**, không phải quy tắc rằng “Java tự sửa lỗi của task lặp” hay “task một lần luôn dừng cả ứng dụng”.

Bài Knowledge thuộc mục **Lỗi của lịch chạy** trong module **Spring Framework - concurrency**. Bạn sẽ học:

1. Phân biệt **one-time registration**, **periodic registration**, **một lần thực thi** và **việc thành công/thất bại**.
2. Hiểu `TaskUtils.LOG_AND_PROPAGATE_ERROR_HANDLER` so với `LOG_AND_SUPPRESS_ERROR_HANDLER`.
3. Biết exception nào đi vào `ScheduledFuture.get()`, khi nào `isDone()` có thể vẫn là `false` dù nhiều lượt lỗi.
4. Hiểu vì sao Spring wrapper khác chính sách `ScheduledThreadPoolExecutor` thuần Java.
5. Tự chọn chính sách `ErrorHandler` mà không nhầm “log lỗi” với “retry nghiệp vụ”.
6. Chạy ví dụ **Java 17+ + Spring Framework 6.2.12**, dùng `CountDownLatch` thay cho `Thread.sleep` để kiểm tra one-shot lỗi, lịch lặp vẫn tiếp tục và lịch lặp dừng khi handler ném lỗi.
7. Hiểu tác động của `@Scheduled`, `@Async`, Publisher reactive, transaction, cancellation, shutdown và nhiều application replicas.
8. Xây dựng checklist vận hành khi lịch định kỳ lỗi liên tục hoặc xử lý một lần không thành công.

**Nguyên tắc cốt lõi:** Hãy hỏi hai câu riêng: **“Một lượt thất bại được báo ở đâu?”** và **“Có tiếp tục lên lịch các lượt sau hay không?”**. Một exception không tự trả lời cả hai câu trong mọi implementation.

## 1. One-shot và repeating khác nhau từ lúc đăng ký

`TaskScheduler` có các API biểu diễn hai loại lịch:

```java
// Chạy một lần ở/không sớm hơn một thời điểm.
ScheduledFuture<?> once =
    scheduler.schedule(task, Instant.now().plusSeconds(2));

// Lặp theo nhịp fixed rate.
ScheduledFuture<?> periodic =
    scheduler.scheduleAtFixedRate(task, Duration.ofSeconds(5));

// Lặp, nghỉ năm giây sau khi mỗi lượt hoàn tất.
ScheduledFuture<?> withDelay =
    scheduler.scheduleWithFixedDelay(task, Duration.ofSeconds(5));
```

Đây là snippet minh họa; cần `TaskScheduler scheduler`, `Runnable task`, `Instant`, `Duration`, `ScheduledFuture` và imports.

Bảng cơ sở:

| Tiêu chí | One-shot `schedule` | Repeating `scheduleAtFixedRate`/`scheduleWithFixedDelay` |
| --- | --- | --- |
| Số lượt dự định | Một lần. | Nhiều lần khi chưa bị hủy/dừng. |
| Sau khi Runnable thành công | Future một lần hoàn tất. | Đăng ký tiếp tục tới lượt sau. |
| Sau khi Runnable ném exception, với **Spring default ErrorHandler** | Log rồi propagate. | Log rồi suppress để lịch tiếp tục. |
| `Future.get()` | Có thể trả bình thường hoặc ném `ExecutionException`. | Không kết thúc bình thường sau mỗi lượt; thường chờ cả registration cho tới khi hủy/lỗi thoát ra. |
| Kỳ vọng retry | Không tự có lượt mới. | Có lượt tiếp theo theo lịch; **không phải** retry backoff có trạng thái. |
| Khi hết thời gian chờ `get(timeout)` | Caller hết hạn chờ; task có thể chưa kết thúc. | Caller hết hạn chờ handle lặp; **không có nghĩa** một lượt cụ thể thất bại. |
| Khi `cancel` | Ngăn một lượt chưa chạy hoặc yêu cầu hủy lượt đang chạy. | Ngăn các lượt tương lai và có thể yêu cầu interrupt lượt đang chạy. |

Cả hai loại đều có thể sử dụng **cùng một scheduler pool**, nên điểm khác nhau không nằm ở việc một loại có thread riêng còn loại kia không. Khi dùng `ThreadPoolTaskScheduler`, task thường thực thi trên scheduler worker.

### Không nhầm “repeating” với “task tự chạy lại sau khi fail”

Một registration định kỳ có **đồng hồ** quy định lần sau. Mặc định Spring chặn exception để registration còn tồn tại, rồi tiếp tục theo thời gian của fixed rate/delay/trigger. Đây **không** phải chính sách retry tự động “thử lại ngay thao tác vừa hỏng đúng N lần rồi exponential backoff”.

Ví dụ một job cứ mỗi phút gọi API thất bại sẽ tiếp tục thất bại ở phút sau. Nếu không có retry budget, cảnh báo và điều kiện tạm dừng, job có thể log lỗi vô thời hạn trong khi hệ thống không phục hồi.

## 2. Spring chọn ErrorHandler mặc định như thế nào?

Spring có lớp hỗ trợ nội bộ `org.springframework.scheduling.support.TaskUtils`. Theo Javadoc:

- `LOG_AND_SUPPRESS_ERROR_HANDLER`: ghi log exception rồi **không ném tiếp**.
- `LOG_AND_PROPAGATE_ERROR_HANDLER`: ghi log exception rồi **ném tiếp**.
- `getDefaultErrorHandler(boolean isRepeatingTask)` trả handler tương ứng: **repeating → suppress**, **one-time → propagate**.

Mô hình:

```text
                       scheduler chạy Runnable
                                  |
                                  v
                         code ứng dụng ném lỗi
                                  |
                                  v
                     Spring error-handling wrapper
                                  |
                  +---------------+---------------+
                  |                               |
             ONE-SHOT                       REPEATING
                  |                               |
             log ERROR                       log ERROR
                  |                               |
          propagate exception              suppress exception
                  |                               |
        ScheduledFuture lỗi              executor thấy wrapper
        (get -> ExecutionException)       hoàn tất lượt này
                                                  |
                                        registration còn sống
                                        -> lượt sau theo lịch
```

Đây là sơ đồ của **các tác vụ đồng bộ qua Spring scheduling với cấu hình mặc định**. Đừng áp nó nguyên xi lên mọi `ScheduledExecutorService`, reactive `Publisher`, hoặc một `ErrorHandler` tùy chỉnh.

### Vì sao có chủ ý khác nhau?

Với one-shot task, người gọi programmatic có thể giữ `ScheduledFuture` để đợi kết quả. Nếu lỗi bị nuốt, `Future.get()` có thể **trả về bình thường dù nghiệp vụ ném exception**. Mặc định propagate giúp thể hiện thất bại qua future.

Với repeating task, nếu một exception chưa xử lý thoát ra tới JDK `ScheduledThreadPoolExecutor`, periodic registration có thể dừng hẳn. Với một job giám sát, dọn dẹp hoặc đồng bộ trạng thái, một lỗi tạm thời có thể không nên vô hiệu hóa mọi lượt chạy sau. Spring chọn mặc định log và suppress để lịch vẫn tiếp tục.

**Nhưng đây là mặc định của framework, không phải quyết định nghiệp vụ phổ quát.** Đôi khi một job lặp phải **dừng** khi gặp lỗi nghiêm trọng: dữ liệu cấu hình bị hỏng, schema không tương thích, nguy cơ ghi dữ liệu sai hoặc mất quyền truy cập. Khi đó có thể dùng handler propagate/cancel có chủ đích hoặc cơ chế disable job có trạng thái.

### Đừng dùng TaskUtils như API nghiệp vụ chính

`TaskUtils` là utility chủ yếu dành cho implementation của Spring scheduler. Javadoc ghi rằng nó là lớp **intended for internal use** dù có các phương thức/constant public. Với ứng dụng thực tế, hãy cấu hình `ThreadPoolTaskScheduler.setErrorHandler(...)` bằng một `org.springframework.util.ErrorHandler` do bạn quản lý và dùng `ScheduledFuture`/job status để quan sát. Không cần gọi `TaskUtils.getDefaultErrorHandler(...)` trong service chỉ để hệ thống hoạt động.

## 3. Một tác vụ chạy một lần thất bại ra sao?

Ví dụ:

```java
ScheduledFuture<?> handle = scheduler.schedule(() -> {
    throw new IllegalStateException("cannot initialize");
}, Instant.now());
```

Đây là đoạn Java trong một method đã có `TaskScheduler scheduler`. Nếu sử dụng `ThreadPoolTaskScheduler` với default handler, Spring log lỗi rồi propagate. Future hoàn tất thất bại; người giữ handle có thể gọi:

```java
try {
    handle.get(5, TimeUnit.SECONDS);
} catch (ExecutionException ex) {
    Throwable businessCause = ex.getCause();
    // Ghi nhận businessCause, quyết định retry hoặc fail startup.
}
```

Đây là snippet chưa xử lý `InterruptedException` và `TimeoutException` — chương trình đầy đủ phía sau cho phép `throws Exception` ở main để tập trung vào chính sách lỗi. Trong production, phải xử lý các loại ngoại lệ chờ đúng ngữ cảnh.

Điểm quan trọng:

1. **Đã tạo Future** không có nghĩa đã chạy.
2. `get(timeout)` đợi tới khi one-shot hoàn tất hoặc hết thời gian; timeout chờ **khác** exception của Runnable.
3. Nếu Runnable ném unchecked exception, JDK `Future.get()` thường bọc nguyên nhân vào `ExecutionException`.
4. Nếu `ErrorHandler` tùy chỉnh **nuốt** exception, Future có thể hoàn tất **thành công theo quan sát của executor** dù operation thất bại về mặt nghiệp vụ.
5. `isDone()` đúng với cả future thành công, lỗi hoặc bị cancel; không dùng riêng `isDone()` để kết luận success.
6. `isCancelled()` chỉ cho biết trạng thái hủy, không thay thế kiểm tra `get()`/completion.
7. Nếu worker chưa chạy hoặc chưa xong khi caller `get(timeout)`, `TimeoutException` không chứng minh worker đã rollback hoặc dừng.

### One-time bằng `@Scheduled`

Từ Spring Framework **6.1**, `@Scheduled(initialDelay = ...)` không có `fixedRate`, `fixedDelay` hay `cron` có thể tạo **một registration chạy một lần**:

```java
@Component
class WarmupJob {
    @Scheduled(initialDelay = 2, timeUnit = TimeUnit.SECONDS)
    public void startOnce() {
        // Có thể ném exception khi chạy.
    }
}
```

Đây là snippet bean; cần `@EnableScheduling`, `@Component` registration và imports. Theo mặc định one-shot error handling, exception được log rồi propagate trong scheduler execution. Nhưng **không có một HTTP caller trực tiếp tự nhận `ExecutionException`** vì method được kích hoạt bởi Spring, không phải caller đang giữ Future từ `TaskScheduler.schedule(...)`.

Nếu muốn theo dõi trạng thái một lần chạy của `@Scheduled`, cần instrumentation, job record, event/metric hoặc một API programmatic quản lý handle. Đừng giả định annotation tự trả `ScheduledFuture` cho controller.

**Quan trọng về phiên bản:** cú pháp chỉ có `initialDelay` là tính năng từ 6.1. Trên Spring 6.0.x, thuộc tính `initialDelay` có tồn tại nhưng cần dùng kèm lịch lặp; không kết luận rằng đó là one-shot hợp lệ trên mọi version.

## 4. Một tác vụ lặp thất bại ra sao?

Ví dụ:

```java
ScheduledFuture<?> repeating =
    scheduler.scheduleAtFixedRate(() -> {
        System.out.println("tick");
        throw new IllegalStateException("backend unavailable");
    }, Duration.ofSeconds(5));
```

Trong cấu hình Spring default synchronous scheduler error handling:

```text
tick #1 -> ứng dụng ném IllegalStateException
          -> Spring log lỗi, suppress
          -> registration vẫn còn

tick #2 -> ứng dụng lại ném IllegalStateException
          -> Spring log lỗi, suppress
          -> registration vẫn còn

tick #3 -> ... tiếp tục tới khi hủy/shutdown/đổi handler
```

Không suy ra từ việc log liên tục rằng `ScheduledFuture` “đang lỗi” theo nghĩa nó đã complete exceptionally. Khi error bị suppress, wrapper Runnable return bình thường cho scheduled executor. **Periodic Future có thể vẫn chưa done**, vì bản thân registration chưa kết thúc.

### Không nên dùng `Future.get()` để đợi “một lượt”

```java
repeating.get();
```

Dòng trên **không** có nghĩa “đợi tick đầu xử lý xong”. Với một periodic registration còn hoạt động, `get()` có thể chờ rất lâu cho tới khi registration bị hủy, gặp lỗi thoát ra hoặc scheduler kết thúc theo semantics implementation.

Để biết **một lượt cụ thể** đã chạy, dùng `CountDownLatch` trong test hoặc trong production dùng per-run event, last-success timestamp, job run ID, status record và metrics. Lịch chạy lặp có một **handle điều khiển lịch**, không tự cung cấp kết quả nghiệp vụ riêng cho từng tick.

### Fixed rate, fixed delay và cron có cùng mặc định suppress không?

Với synchronous repeating tasks được Spring scheduler bọc bằng default error handler, nguyên lý **log và suppress** áp dụng cho các lịch lặp điển hình:

- `scheduleAtFixedRate(...)`.
- `scheduleWithFixedDelay(...)`.
- `schedule(Runnable, Trigger)` với trigger tạo nhiều mốc, như `PeriodicTrigger` hoặc `CronTrigger`.
- `@Scheduled` synchronous dạng `fixedRate`, `fixedDelay`, `cron` khi hạ tầng dùng scheduler Spring phù hợp.

Nhưng implementation và handler có thể thay đổi theo cấu hình, vì thế phải xác nhận **scheduler bean nào thực sự được chọn**. Một `ScheduledExecutorService` thuần JDK không có Spring wrapper có semantics khác.

## 5. Vì sao JDK thuần Java có thể dừng periodic task sau exception?

Đây là nguồn nhầm lẫn quan trọng khi so sánh tài liệu Java với Spring.

Với `ScheduledThreadPoolExecutor` hoặc `ScheduledExecutorService.scheduleAtFixedRate(...)` của JDK, nếu một lượt của periodic task **ném exception không được bắt**, JDK sẽ **suppress các lần thực hiện sau** của registration ấy. Future có thể complete exceptionally.

Ví dụ Java thuần:

```java
ScheduledExecutorService raw =
    Executors.newSingleThreadScheduledExecutor();
try {
    ScheduledFuture<?> handle = raw.scheduleAtFixedRate(
        () -> { throw new IllegalStateException("fail"); },
        0, 1, TimeUnit.SECONDS
    );
    try {
        handle.get(5, TimeUnit.SECONDS);
    } catch (ExecutionException ex) {
        System.out.println("periodic stopped: " + ex.getCause());
    }
} finally {
    raw.shutdownNow();
}
```

Đây là snippet đặt trong method có thể ném checked exceptions và đã import `java.util.concurrent.*`; không phải file hoàn chỉnh. Theo hợp đồng `ScheduledThreadPoolExecutor`, periodic registration bị chấm dứt khi một lượt ném exception thoát ra.

**Vậy tại sao Spring recurring vẫn chạy?** Vì Spring `ThreadPoolTaskScheduler` có lớp **error-handling wrapper**. Theo mặc định repeating, wrapper bắt Throwable, ghi log rồi return mà **không ném tiếp**. Từ góc nhìn JDK scheduled executor, Runnable của lượt đó kết thúc bình thường, do đó không kích hoạt cơ chế dừng periodic do exception thoát ra.

Hai nhận định sau **đều đúng trong bối cảnh của chúng**:

- “Unhandled exception của JDK periodic task có thể dừng schedule.”
- “Spring repeating task mặc định log lỗi mà vẫn có lượt chạy sau.”

Không được lấy kết luận ở bối cảnh này thay cho bối cảnh kia khi chưa kiểm tra wrapper/handler.

## 6. Tùy biến ErrorHandler: thay đổi chính sách có chủ đích

`ThreadPoolTaskScheduler` có `setErrorHandler(ErrorHandler)`. Đây là hook công khai để quyết định làm gì khi một synchronous scheduled Runnable ném Throwable.

Ví dụ log rồi suppress:

```java
@Bean("monitorScheduler")
ThreadPoolTaskScheduler monitorScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("monitor-");
    scheduler.setErrorHandler(error -> {
        System.err.println("Scheduled failure: " + error.getMessage());
        // Không throw: lỗi được suppress.
    });
    return scheduler;
}
```

Đây là snippet cấu hình bên trong `@Configuration` có imports. Nó có nghĩa ngay cả one-shot tasks dùng scheduler này cũng có thể bị suppress, khiến `Future.get()` không phản ánh lỗi đã phát sinh. **Hãy cân nhắc kỹ** trước khi đặt một global handler nuốt lỗi trên scheduler dùng chung.

Ví dụ propagate:

```java
@Bean("strictScheduler")
ThreadPoolTaskScheduler strictScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("strict-");
    scheduler.setErrorHandler(error -> {
        System.err.println("Scheduled failure: " + error.getMessage());
        throw new IllegalStateException("propagated-by-handler", error);
    });
    return scheduler;
}
```

Đây là snippet cấu hình trong Spring; nó **ném tiếp** một unchecked exception từ handler. Với repeating task chạy trên JDK scheduled executor của `ThreadPoolTaskScheduler`, exception thoát ra sẽ làm registration kết thúc bất thường và các lượt tiếp theo không chạy.

### Đặt custom handler lên toàn bộ scheduler nghĩa là gì?

`setErrorHandler` là **chính sách của scheduler bean**. Các task chạy trên scheduler ấy có thể dùng cùng handler. Vì vậy:

- Nếu chỉ muốn fail-fast một job quan trọng, nhưng muốn các job monitor khác luôn tiếp tục, nên **tách scheduler** hoặc bọc từng Runnable bằng handler nghiệp vụ riêng.
- Nếu dùng chung scheduler với custom handler suppress, một one-shot failure có thể bị che mất khỏi `ScheduledFuture`.
- Nếu custom handler ném lỗi, repeating tasks có thể dừng hẳn chỉ vì một exception tạm thời.
- Nếu handler chặn I/O hay gửi HTTP log rất lâu, nó có thể **giữ scheduler worker**, làm trễ nhiều job khác.
- Nếu handler tự ném exception khác, nguyên nhân hiển thị qua Future có thể là exception của handler; cần giữ `cause` để không đánh mất lỗi gốc.
- Nếu handler chỉ ghi log nhưng không cập nhật metric/trạng thái, đội vận hành khó biết job đã hỏng liên tục.

**Chính sách tốt** thường tách thành: ghi nhận thất bại của *một lần chạy*, quyết định tiếp tục/dừng *registration*, và quyết định retry/compensation ở *nghiệp vụ*. Đừng gộp ba quyết định vào một lệnh `catch {}` không có dữ liệu.

## 7. Ví dụ hoàn chỉnh Java 17+ và Spring Framework 6.2.12

Chương trình dưới đây trình diễn ba trường hợp trong một Spring context:

1. **One-shot mặc định** ném `IllegalStateException("one-shot")`. `ScheduledFuture.get()` ném `ExecutionException` giữ nguyên nguyên nhân; task chạy đúng một lần.
2. **Repeating mặc định** ném lỗi **ở mọi lượt**. `CountDownLatch(3)` chứng minh ít nhất ba lượt vẫn được thực thi, dù lỗi ở lượt đầu. Handle còn pending cho tới khi main cancel.
3. **Repeating với `ErrorHandler` propagate**: scheduler riêng có handler ném `IllegalStateException("propagated-by-handler", originalError)`. Future kết thúc với `ExecutionException` ngay sau lần đầu; không có lượt thứ hai.

Không dùng `Thread.sleep()` để phỏng đoán đã chạy bao nhiêu lần. Chương trình chỉ in các kết luận boolean/count sau khi future/latch báo điều kiện đã đạt. **Spring có thể in thêm log lỗi ra stderr** vì hai scheduler intentionally chạy tasks ném exception; khối expected output chỉ mô tả các dòng `System.out` do demo in.

### Cấu trúc project

```text
scheduler-error-policy-demo/
├── pom.xml
└── src/main/java/example/SchedulerErrorPolicyDemo.java
```

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>scheduler-error-policy-demo</artifactId>
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
                    <mainClass>example.SchedulerErrorPolicyDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `src/main/java/example/SchedulerErrorPolicyDemo.java`:

```java
package example;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SchedulerErrorPolicyDemo {

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        @Bean("defaultScheduler")
        public ThreadPoolTaskScheduler defaultScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("default-scheduled-");
            // Không gọi setErrorHandler: để Spring dùng mặc định.
            return scheduler;
        }

        @Bean("strictScheduler")
        public ThreadPoolTaskScheduler strictScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("strict-scheduled-");

            // Custom handler cố ý propagate lỗi.
            scheduler.setErrorHandler(error -> {
                throw new IllegalStateException(
                    "propagated-by-handler", error
                );
            });
            return scheduler;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            ThreadPoolTaskScheduler defaults = context.getBean(
                "defaultScheduler", ThreadPoolTaskScheduler.class
            );
            ThreadPoolTaskScheduler strict = context.getBean(
                "strictScheduler", ThreadPoolTaskScheduler.class
            );

            // CASE 1: one-shot; lỗi propagate qua Future.
            AtomicInteger oneShotCalls = new AtomicInteger();
            ScheduledFuture<?> once = defaults.schedule(() -> {
                oneShotCalls.incrementAndGet();
                throw new IllegalStateException("one-shot");
            }, Instant.now());

            if (once == null) {
                throw new IllegalStateException("One-shot was not scheduled");
            }

            boolean oneShotFailureExposed = false;
            try {
                once.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                oneShotFailureExposed =
                    cause instanceof IllegalStateException
                    && "one-shot".equals(cause.getMessage());
            }

            // CASE 2: repeating; mặc định Spring log và suppress.
            AtomicInteger repeatingCalls = new AtomicInteger();
            CountDownLatch firstThree = new CountDownLatch(3);

            ScheduledFuture<?> repeating = defaults.scheduleAtFixedRate(
                () -> {
                    repeatingCalls.incrementAndGet();
                    firstThree.countDown();
                    throw new IllegalStateException("repeating");
                },
                Duration.ofMillis(50)
            );

            boolean reachedThree;
            boolean repeatingPendingBeforeCancel;
            try {
                reachedThree = firstThree.await(5, TimeUnit.SECONDS);
                repeatingPendingBeforeCancel = !repeating.isDone();
            } finally {
                repeating.cancel(false);
            }

            if (!reachedThree) {
                throw new IllegalStateException(
                    "Repeating task ran fewer than three times"
                );
            }

            // CASE 3: repeating; custom handler làm lỗi thoát ra.
            AtomicInteger strictCalls = new AtomicInteger();
            ScheduledFuture<?> strictRepeating = strict.scheduleAtFixedRate(
                () -> {
                    strictCalls.incrementAndGet();
                    throw new IllegalStateException("strict-repeating");
                },
                Duration.ofMillis(50)
            );

            boolean strictFailureExposed = false;
            try {
                strictRepeating.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                strictFailureExposed =
                    cause instanceof IllegalStateException
                    && "propagated-by-handler".equals(cause.getMessage());
            }

            System.out.println("oneShotFailureExposed="
                + oneShotFailureExposed);
            System.out.println("oneShotRuns=" + oneShotCalls.get());
            System.out.println("repeatingContinuedAfterErrors="
                + (reachedThree && repeatingCalls.get() >= 3));
            System.out.println("repeatingPendingBeforeCancel="
                + repeatingPendingBeforeCancel);
            System.out.println("repeatingCancelled="
                + repeating.isCancelled());
            System.out.println("customHandlerStoppedRepeating="
                + (strictFailureExposed && strictCalls.get() == 1));
        }
    }
}
```

Chạy từ thư mục `scheduler-error-policy-demo`, với JDK 17+ và Maven:

```bash
mvn -q compile exec:java
```

**Sáu dòng do chính chương trình in ra stdout**, theo thứ tự khi chạy thành công:

```text
oneShotFailureExposed=true
oneShotRuns=1
repeatingContinuedAfterErrors=true
repeatingPendingBeforeCancel=true
repeatingCancelled=true
customHandlerStoppedRepeating=true
```

**Lưu ý về log:** Default Spring handler sẽ ghi log lỗi cho one-shot và mọi lượt repeating được thử; custom handler ở `strictScheduler` không tự log vì code handler chỉ ném exception. Tùy logging backend, terminal có thể hiển thị thêm log ra stderr. Điều này không làm thay đổi sáu dòng `System.out` của main.

### Phân tích vì sao kết quả được thiết kế để xác định

1. `AnnotationConfigApplicationContext` tạo hai bean `ThreadPoolTaskScheduler`, mỗi bean một worker; Spring quản lý việc khởi tạo/shutdown của chúng.
2. One-shot được lập lịch `Instant.now()`. `once.get(5, SECONDS)` chỉ return hay ném khi Runnable đã hoàn tất hoặc có lỗi; trong case này nó nhận `ExecutionException` với `IllegalStateException("one-shot")`.
3. `oneShotCalls` tăng một lần, và không có registration lặp cho `once`. Dữ liệu `oneShotRuns=1` không dựa vào sleep.
4. Repeating task được đăng ký bằng `scheduleAtFixedRate` với period 50 ms. Mỗi lượt đều ném lỗi, nhưng Spring default repeating handler **suppress**. Do đó `firstThree.countDown()` được gọi ít nhất ba lần trong các lần chạy tiếp nối.
5. `firstThree.await(5,SECONDS)` là điều kiện đồng bộ. Khi main quan sát latch đã mở, repeating registration vẫn active vì exception không thoát ra; `repeating.isDone()` là false **trước khi cancel**.
6. Main gọi `repeating.cancel(false)` trong `finally`. Handle chuyển sang cancelled; lệnh này không tự rollback một side effect của lượt đã chạy.
7. Scheduler strict có handler custom ném `IllegalStateException("propagated-by-handler", error)` sau khi ứng dụng ném `"strict-repeating"`. Future nhận exception từ handler, dừng periodic registration theo hợp đồng của executor.
8. `strictRepeating.get(...)` chờ future hoàn tất bất thường; sau completion này sẽ không có các lượt tiếp theo do cùng registration, nên `strictCalls.get() == 1`.
9. Mọi `println` do main thực hiện sau các điều kiện đồng bộ; **không đặt `println` trong worker**, nên thứ tự stdout ổn định. Nếu môi trường quá tải đến mức vượt giới hạn 5 giây, ví dụ thất bại có kiểm soát thay vì in các kết quả giả.

**Đây là bài demo về chính sách error-handling**, không phải chiến lược production retry. Job production không nên cố ý ném exception ở mọi lượt mà không có giới hạn log, cảnh báo và quyết định dừng/chờ.

## 8. `@Scheduled` đồng bộ và tên scheduler bean

Trong ứng dụng dùng annotation:

```java
@Configuration
@EnableScheduling
class SchedulingConfig {

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
            new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }
}

@Component
class Jobs {

    @Scheduled(
        fixedDelay = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "taskScheduler"
    )
    public void repeat() {
        // Một invocation thất bại mặc định thường được Spring log/suppress.
    }

    @Scheduled(
        initialDelay = 2,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "taskScheduler"
    )
    public void once() {
        // Spring 6.1+: chỉ initialDelay là one-shot registration.
    }
}
```

Đây là **cấu trúc Spring**, chưa phải chương trình độc lập: cần import `@Component`, `@Bean`, `@Configuration`, `@EnableScheduling`, `@Scheduled`, `TimeUnit` và đăng ký đúng context. Ví dụ chính phần 7 dùng API programmatic nên không cần `@EnableScheduling`.

Mọi method synchronous này thường chạy trên thread của `ThreadPoolTaskScheduler`. Nếu `repeat()` block HTTP 60 giây, scheduler worker có thể bị chiếm và gây trễ những job khác; cơ chế suppress lỗi **không** giúp chống blocking.

**Chọn scheduler rõ ràng** khi có nhiều bean và cần chính sách lỗi khác nhau. Nếu không nêu thuộc tính `scheduler`, Spring có quy tắc chọn scheduler mặc định dựa trên bean/qualifier; không dựa vào giả định rằng “job nào cũng cùng handler” nếu thực tế nhiều executors.

### Một method có nhiều `@Scheduled`

`@Scheduled` là repeatable. Hai annotation trên cùng method có thể tạo **hai registrations độc lập**. Nếu mỗi registration thất bại, bạn có thể thấy số log nhiều hơn tưởng tượng. Đây không phải Spring tự retry một registration; đó là cấu hình tạo nhiều lịch.

Tương tự, nếu chạy ba replicas, mỗi replica lập lịch riêng; tổng các lượt lỗi có thể gấp ba. Scheduler không có cơ chế distributed leader election ngầm.

## 9. Lỗi ở reactive `@Scheduled`: một đường xử lý riêng

Từ Spring Framework 6.1, scheduled method có thể trả `Publisher` như Reactor `Mono<Void>` hoặc `Flux<T>` và các kiểu thích nghi phù hợp. Spring lấy Publisher rồi subscribe theo lịch. Khi Publisher phát tín hiệu `onError`:

- Spring ghi nhận lỗi **ở mức WARN**.
- Giá trị `onNext` của reactive scheduled method thường bị bỏ qua.
- Reactive `onError` không được ném trực tiếp từ `Runnable` đang kích hoạt subscription, nên **`ErrorHandler` của scheduler không tham gia** theo hợp đồng thông thường.
- Các scheduled subscriptions tiếp theo vẫn tiếp tục sau lỗi.

Ví dụ:

```java
@Scheduled(fixedRate = 1000)
public Mono<Void> reactivePoll() {
    return Mono.defer(() -> {
        if (backendUnavailable()) {
            return Mono.error(
                new IllegalStateException("backend unavailable")
            );
        }
        return Mono.empty();
    });
}
```

Đây là snippet Reactor có `backendUnavailable`, Spring scheduling setup và imports cần thiết. `Mono.defer` đảm bảo kiểm tra backend **ở mỗi subscription**; Spring thường gọi method lấy Publisher một lần, rồi subscribe lại theo các tick.

**Không áp dụng mù quáng “one-shot propagate, repeating suppress” của `TaskUtils` lên `Publisher.onError`.** Ngay cả khi reactive method được cấu hình one-time, `onError` vẫn là tín hiệu của Publisher được xử lý bởi cơ chế reactive scheduling; không phải unchecked exception từ synchronous Runnable để `Future.get()` của caller bắt giống ví dụ phần 7.

### Fixed delay và Publisher không bao giờ complete

Với reactive `@Scheduled(fixedDelay=...)`, Spring chờ subscription hoàn tất để giữ semantics fixed delay. Một Publisher không bao giờ complete hoặc I/O reactive quá lâu có thể làm scheduler worker bị giữ. Nếu source lỗi nhưng có completion terminal, Spring tiếp tục lên lịch các lượt sau theo điều kiện tương ứng.

Dùng timeout thật, retry có giới hạn và metrics; đừng chỉ nhìn việc Spring có ghi log `onError` rồi kết luận scheduler sẽ tự phục hồi hệ thống nghiệp vụ.

## 10. `@Async`, `CompletableFuture` và lỗi không đồng nhất với scheduled

Một service có thể được gọi từ `@Scheduled`:

```java
@Component
class TriggerJob {
    private final AsyncService service;

    TriggerJob(AsyncService service) {
        this.service = service;
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        CompletableFuture<Result> future = service.processAsync();
        future.whenComplete((result, error) -> {
            if (error != null) {
                recordFailure(error);
            } else {
                recordSuccess(result);
            }
        });
    }
}
```

Đây là snippet kiến trúc, cần `AsyncService`, `Result`, các hàm ghi nhận kết quả, bean registration và `@EnableAsync` nếu service dùng `@Async`. Vì scheduled `tick()` **submit rồi return**, default ErrorHandler của scheduler chỉ thấy lỗi **đồng bộ xảy ra trong tick**, chẳng hạn `TaskRejectedException` lúc submit, chứ không tự bắt exception xảy ra sau đó trong `CompletableFuture`.

Nếu `processAsync` hoàn tất exceptionally, lỗi nằm trong future và cần được quan sát qua `whenComplete`, `handle`, `exceptionally` hoặc `get()` ở nơi phù hợp. `@Async` `void` có thêm cơ chế `AsyncUncaughtExceptionHandler` riêng; đó **không phải** default `ErrorHandler` của synchronous scheduler.

**Tránh self-invocation:** Trong Spring AOP proxy mode, gọi `this.processAsync()` từ cùng bean thường bỏ qua `@Async` interceptor. Khi đó công việc có thể chạy đồng bộ trên scheduler thread và ngoại lệ xuất hiện theo đường khác. Tách bean hoặc gọi qua Spring proxy để biết chắc ranh giới execution.

### Có nên gọi `join()` trong scheduled tick?

Chỉ khi nghiệp vụ thực sự cần đợi toàn bộ async operation hoàn tất và đã đánh giá blocking, timeout, pool sizing. `join()` không có timeout, có thể chiếm scheduler worker lâu; `get(timeout)` chỉ giới hạn thời gian caller chờ và **không tự hủy** operation bên dưới. Thông thường nên dùng completion callback và một cơ chế giới hạn in-flight để không tạo backlog vô hạn.

## 11. Exception trong transaction khác exception của scheduler

Một job có thể ghi JDBC/JPA và ném lỗi:

```java
@Service
class DatabaseWriter {
    @Transactional
    public void writeBatch() {
        repository.insertFirst();
        repository.insertSecond(); // Có thể ném RuntimeException.
    }
}
```

Đây là snippet cần Spring transaction manager, repository và imports. Nếu `@Transactional` thực sự được gọi qua Spring proxy, unchecked exception không bị nuốt trong method thường khiến transaction rollback theo default rollback rules.

**Nhưng** khi lỗi thoát ra khỏi `DatabaseWriter`, scheduler có thể log/suppress để registration lặp tiếp tục. Hai quyết định độc lập:

1. **Transaction:** Các write của **lượt hiện tại** được commit hay rollback?
2. **Scheduling:** Registration có được giữ lại để **lượt sau** chạy tiếp không?

Có thể có:

```text
tick #1:
  BEGIN transaction A
  writeFirst()
  writeSecond() throws
  ROLLBACK A
  Spring scheduler logs + suppresses
  registration remains active

tick #2:
  BEGIN transaction B
  writeFirst()
  writeSecond()
  COMMIT B
```

Lỗi đã bị **scheduler suppress** sau khi transaction advice thấy exception không có nghĩa transaction trước đó đã commit. Ngược lại, nếu **business method tự catch và nuốt** exception bên trong ranh giới `@Transactional`, transaction advice có thể thấy return bình thường và commit. Phải xác định nơi exception bị bắt.

### Nếu một lượt đã ghi một phần dữ liệu?

Một job chạy ngoài transaction có thể ghi một dòng rồi thất bại ở bước thứ hai. Việc Spring tiếp tục schedule không tự undo dòng đã ghi. Khi lượt kế tiếp chạy, nó có thể tạo bản ghi trùng hoặc xử lý lại input sai.

Cần transactional unit of work, idempotency key, unique constraint, versioning và retry/compensation theo nghiệp vụ. Đừng dùng “Spring sẽ chạy lại phút sau” làm chiến lược khôi phục dữ liệu mà không lưu trạng thái đã xử lý.

## 12. Điều gì xảy ra với ThreadLocal và tài nguyên?

`ThreadPoolTaskScheduler` thường chạy Runnable trên chính scheduler workers. Worker có thể được tái sử dụng qua nhiều lượt job và nhiều jobs khác nhau. Một `ThreadLocal` tự đặt rồi quên `remove()` có thể làm task sau đọc nhầm request ID, tenant ID hoặc logging context.

Ví dụ cleanup:

```java
String previous = traceId.get();
try {
    traceId.set(jobId);
    runOneIteration();
} finally {
    if (previous == null) {
        traceId.remove();
    } else {
        traceId.set(previous);
    }
}
```

Đây là snippet cần `traceId`, `jobId`, `runOneIteration` đã định nghĩa. Không dùng `ErrorHandler` làm nơi chính để cleanup vì code có thể không ném lỗi; cleanup phải xảy ra cả khi thành công, thường trong `finally` hay context scope thích hợp.

`@Transactional` trên caller HTTP không tự theo scheduled worker thread; nếu scheduled task cần database transaction phải mở đúng tại nơi thực thi dữ liệu. Đừng copy JDBC connection/`EntityManager` qua thread. Reactive contexts và Spring Security có cơ chế propagation riêng nếu được cấu hình.

## 13. “Log rồi tiếp tục” khác retry có backoff

Giả sử job chạy mỗi 5 giây và một external API trả HTTP 503 trong một giờ. Default repeating handler sẽ **vẫn lập lịch** theo fixed rate/delay, dù mỗi lượt lỗi. Điều này có thể giúp hệ thống tự phục hồi khi API trở lại, nhưng cũng có thể gây:

- Hàng trăm request lỗi và log spam.
- Rate limit hoặc cascade failure xuống service đang gặp sự cố.
- Tranh chấp DB connection nếu từng lượt mở transaction.
- Tồn backlog nếu task kéo dài hơn period hoặc tick chỉ submit Future.
- Chênh lệch giữa “scheduler còn chạy” với “business result đã thành công”.

Cần phân loại lỗi:

| Loại lỗi | Ví dụ | Chính sách tham khảo |
| --- | --- | --- |
| **Transient** | HTTP 503, temporary network failure | Bounded retry/backoff, jitter, timeout, giới hạn concurrency. |
| **Dependency down kéo dài** | DB/proxy unavailable nhiều phút | Circuit breaker hoặc tạm dừng có cảnh báo; không spam cùng một hành động. |
| **Permanent business validation** | Dữ liệu sai cấu trúc, ID không hợp lệ | Dead-letter/quarantine record, báo lỗi và tránh xử lý lặp vô hạn. |
| **Configuration/security fatal** | Sai credentials, schema không tương thích | Có thể fail-fast hoặc disable job; require intervention. |
| **Cancellation/shutdown** | App đóng, executor dừng | Phân biệt với nghiệp vụ thất bại; dọn resource và khôi phục theo job state. |

“Lặp lại theo schedule” chỉ là **thời điểm một lượt được khởi động**, không phải retry policy của từng record. Ví dụ một batch gồm 100 records có 2 records lỗi phải lưu được **các record chưa xong**, không nên chỉ chạy lại toàn batch mà không có idempotency.

### Đặt retry ở tầng nào?

- **Retry bên trong một lượt**: có thể giúp xử lý transient lỗi nhanh, nhưng giữ scheduler worker lâu và tăng độ trễ của các jobs khác.
- **Retry qua lượt schedule tiếp theo**: đợi đến kỳ mới; phải lưu trạng thái failed record/job để biết cần xử lý lại.
- **Retry bằng job queue riêng**: phù hợp nếu cần backoff cá thể, dead-letter, priority và durability.
- **Dừng registration**: phù hợp khi tiếp tục chạy có nguy cơ gây tác động xấu hoặc dữ liệu không hợp lệ, nhưng cần báo động rõ.

Dù chọn cách nào, không nên thực hiện retry không giới hạn trong `ErrorHandler` của scheduler vì handler cũng chạy trên scheduler worker và có thể gây deadlock/giữ worker mãi.

## 14. Monitoring và tiêu chí cảnh báo

Nếu repeating job được Spring suppress lỗi mặc định, chỉ xem `ScheduledFuture.isDone()` là sai cách kiểm tra sức khỏe: registration có thể **vẫn active** dù 100% số lượt gần đây thất bại.

Các tín hiệu nên có:

1. **Số lần bắt đầu**, `job_run_started_total`, có job name và schedule ID.
2. **Số lượt thành công**, `job_run_succeeded_total`.
3. **Số lượt thất bại**, `job_run_failed_total`, phân nhóm lỗi (tránh nhãn chứa ID bí mật hoặc cardinality quá cao).
4. **Thời điểm thành công cuối**, `last_success_timestamp`.
5. **Thời gian thực thi**, p50/p95/p99, phân biệt queue wait và run duration.
6. **Schedule lag**, actual start trễ bao lâu so với scheduled time.
7. **Số job đang xử lý / backlog**, để phát hiện overlap và executor saturation.
8. **Những lượt bị bỏ lỡ**, nếu business policy có khái niệm missed work cần lưu riêng.
9. **Trạng thái registration**, active/cancelled/failed, cùng reason.
10. **Trạng thái kết quả nghiệp vụ**, record ID, retry count, idempotency key hoặc job checkpoint.

Cảnh báo đáng tin không chỉ là “log có ERROR”. Có thể alert khi không có successful run trong N period, failure ratio vượt ngưỡng, duration tăng hoặc job bị dừng dù dự kiến còn chạy.

### Không để lỗi mặc định làm mất tín hiệu lỗi

Một `ErrorHandler` swallow exception **không được** dùng như cơ chế “coi job đã thành công”. Hãy ghi metric hoặc persisted result **trước khi** quyết định swallow/propagate. Nếu nhiều jobs dùng chung scheduler, có thể bọc từng Runnable bằng logic chuẩn có job name, correlation ID, status và error classification.

```java
Runnable observed = () -> {
    long started = System.nanoTime();
    try {
        original.run();
        metrics.recordSuccess();
    } catch (RuntimeException ex) {
        metrics.recordFailure(ex);
        throw ex; // để scheduler áp dụng policy lỗi đã chọn
    } finally {
        metrics.recordDuration(System.nanoTime() - started);
    }
};
```

Đây là snippet có `original`, `metrics` và imports trong ứng dụng thật; `System.nanoTime` phù hợp để đo elapsed duration. Với API Java có thể ném `Error`, cần chính sách quan sát phù hợp nhưng không nên bắt và nuốt các lỗi nghiêm trọng như `OutOfMemoryError` chỉ để tiếp tục schedule.

Đồng thời tránh log trùng cùng exception ở wrapper, Spring handler và reporter; cần phân công nơi log, metric và alert rõ ràng.

## 15. Những trường hợp cancellation và shutdown

Khi một task chạy một lần chưa đến hạn, `ScheduledFuture.cancel(false)` có thể ngăn nó bắt đầu. Với repeating, cancellation ngăn những lượt mới theo semantics future/executor. Nhưng:

- `cancel(true)` là yêu cầu interruption, không bảo đảm JDBC/HTTP call đang chạy dừng ngay.
- Một Future đã complete exceptionally có thể không thể “hủy lại” theo ý nghĩa chuyển trạng thái.
- `isDone()` đúng khi hoàn tất bình thường, thất bại hoặc đã bị hủy.
- Timeout khi đợi Future là **lỗi của người đợi về ngân sách thời gian**, không tự động là business failure của Runnable.
- App shutdown có thể hủy pending registrations hoặc xử lý running tasks theo policy; cần thiết kế recovery nếu công việc quan trọng.
- Nếu dùng periodic scheduling RAM thuần, application restart có thể mất các lượt đã đến hạn mà chưa chạy.

Với một one-shot job không được mất khi process crash, hãy lưu thời điểm chạy + trạng thái vào database/job store. Với repeating job cần xử lý đúng mọi kỳ lịch, phải có checkpoint/watermark hoặc durable queue; `ScheduledFuture` không phải transaction log.

## 16. Tránh một số sai lầm phổ biến

| Nhận định sai | Điều đúng |
| --- | --- |
| “Mọi scheduled exception đều dừng scheduler.” | Mặc định Spring repeating thường log và suppress để tiếp tục. |
| “One-shot failure cũng luôn bị suppress như recurring.” | Default one-shot log và propagate để Future có thể báo lỗi. |
| “Repeating future `get()` xong sau một lượt.” | Future thuộc registration, không tự complete mỗi tick. |
| “Periodic job log ERROR tức chắc chắn schedule đã dừng.” | Handler có thể suppress; cần check future/metrics và quan sát các lượt sau. |
| “Spring Scheduler và JDK ScheduledExecutorService có cùng default xử lý lỗi.” | Spring bọc task; JDK raw periodic exception thoát ra có thể ngăn lượt sau. |
| “Một custom `ErrorHandler` chỉ ảnh hưởng một method.” | Handler đặt ở scheduler bean có thể áp dụng cho nhiều tasks của bean đó. |
| “Handler chỉ log là future one-shot vẫn thất bại.” | Nếu handler swallow exception, Future có thể hoàn tất bình thường theo executor. |
| “Handler ném exception vẫn bảo đảm periodic cứ chạy.” | Exception thoát ra có thể khiến registration dừng. |
| “@Scheduled one-shot có Future tự chuyển tới HTTP caller.” | Scheduled annotation không tự trả handle cho caller. |
| “@Async CompletableFuture lỗi sẽ được scheduler ErrorHandler bắt.” | Lỗi async nằm trong completion handle; không nhất thiết đi qua synchronous Runnable. |
| “Reactive `onError` chính là Throwable của scheduler Runnable.” | Reactive onError được log/recovered trên subscription, không theo ErrorHandler thường. |
| “Scheduler suppress lỗi đồng nghĩa DB transaction vẫn commit.” | Transaction advice có thể rollback trước khi scheduler bắt lỗi. |
| “`get(timeout)` timeout nghĩa job dừng hoặc rollback.” | Timeout chỉ giới hạn thời gian đợi của caller. |
| “Periodic scheduling là retry đáng tin của từng record.” | Nó tạo lượt theo đồng hồ, không lưu retry state/side effects. |
| “`isDone() == false` chứng minh job khỏe.” | Periodic registration có thể active dù mọi lượt đều fail. |
| “Tăng poolSize tự chữa lỗi nghiệp vụ.” | Concurrency không sửa dữ liệu sai hoặc dependency failure. |
| “Một job trong mỗi replica nghĩa toàn cụm chỉ chạy một job.” | Mỗi context có registration riêng; cần coordination/idempotency. |

## 17. Checklist khi thiết kế và review

1. Tác vụ thuộc **one-shot** hay **repeating**? Có đúng một registration hay nhiều?
2. Scheduler implementation thật là `ThreadPoolTaskScheduler`, raw JDK scheduler hay scheduler khác?
3. `ErrorHandler` mặc định hay custom? Custom handler suppress hay propagate?
4. Với one-shot, caller có giữ `ScheduledFuture` và quan sát `ExecutionException` không?
5. Với recurring, cần xử lý từng lượt thế nào thay vì chỉ nhìn handle?
6. Một thất bại transient có nên để kỳ sau tiếp tục không? Lỗi fatal có cần disable không?
7. Nếu handler suppress, metric/job status có vẫn ghi **failure** không?
8. Có retry budget/backoff và idempotency cho side effects không?
9. Với `@Scheduled` annotation, scheduler qualifier/name có đúng bean và policy không?
10. `@Async`/CompletableFuture có được theo dõi completion riêng khi tick chỉ submit không?
11. Nếu reactive Publisher phát `onError`, có hiểu rằng scheduler ErrorHandler không nhận thông thường?
12. Có transaction đúng tại worker và rollback rule phù hợp không?
13. Khi scheduler shutdown/cancel, hệ thống có cách khôi phục việc chưa hoàn thành không?
14. Pool có bị blocking I/O hoặc long retry giữ worker quá lâu không?
15. Có cảnh báo khi không có successful run trong nhiều period không?
16. Test có sử dụng latch/future thay sleep, xác nhận một-shot failure, repeated continuation và custom fail-stop không?
17. Với nhiều pods, job identity/coordination và durable state được bảo vệ thế nào?

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao cùng một `IllegalStateException` từ Runnable lại gây tác động khác nhau với one-shot và repeating mặc định của Spring?

**Đáp án:** Vì Spring chọn default ErrorHandler theo loại registration. One-shot log và propagate; repeating log và suppress. Chính policy wrapper quyết định lỗi có thoát ra tới Future/executor hay không.

**Câu 2.** One-shot `ScheduledFuture.get()` ném `ExecutionException`. Có nghĩa scheduler pool phải shutdown không?

**Đáp án:** Không. Nó phản ánh **một task** hoàn tất thất bại. Scheduler có thể tiếp tục nhận/chạy tasks khác.

**Câu 3.** Periodic Runnable cứ ném lỗi nhưng `isDone()` vẫn false. Có phải Spring mất lỗi không?

**Đáp án:** Không nhất thiết: default handler có thể log/suppress lỗi mỗi lượt rồi tiếp tục registration. Cần đo per-run failure để biết nghiệp vụ thực tế.

**Câu 4.** Vì sao raw JDK `scheduleAtFixedRate` có thể dừng sau một exception, trong khi Spring `scheduleAtFixedRate` mặc định vẫn chạy?

**Đáp án:** Spring có error-handling wrapper cho repeating tasks; wrapper bắt và suppress exception trước khi nó thoát đến scheduled executor. Raw JDK không có wrapper đó theo mặc định.

**Câu 5.** Nếu custom handler chỉ in log và không throw, one-shot Future có chắc báo thất bại không?

**Đáp án:** Không. Lỗi có thể bị handler suppress, khiến executor nhìn thấy Runnable hoàn tất bình thường.

**Câu 6.** Nếu custom handler throw từ recurring task, điều gì có thể xảy ra?

**Đáp án:** Exception thoát khỏi wrapped Runnable, khiến JDK periodic future complete exceptionally và registration dừng các lượt sau.

**Câu 7.** `future.get(2, TimeUnit.SECONDS)` timeout có chứng minh worker không bao giờ chạy nữa không?

**Đáp án:** Không. Caller chỉ hết thời gian chờ, worker có thể tiếp tục hoặc hoàn thành sau đó.

**Câu 8.** Trong demo, `repeatingContinuedAfterErrors=true` được kiểm chứng bằng gì?

**Đáp án:** `CountDownLatch(3)` cho biết callback đã chạy ít nhất ba lượt, mặc dù từng lượt đều ném exception. Nó không dựa vào `Thread.sleep()`.

**Câu 9.** `TaskScheduler.ErrorHandler` có nhận một `Mono.onError` từ reactive `@Scheduled` như lỗi Runnable đồng bộ không?

**Đáp án:** Không theo cơ chế reactive thông thường. Spring log WARN/recover trong subscription, các lượt sau vẫn được lập lịch.

**Câu 10.** Vì sao một method `@Scheduled` trả `void`, chỉ gọi `@Async` service rồi return, không tự làm scheduler biết future thất bại?

**Đáp án:** Lỗi xảy ra sau khi submit nằm trong future; scheduler thấy tick return bình thường. Cần observe future qua callback/job status.

**Câu 11.** Nếu một job dùng `@Transactional` rollback vì lỗi, Spring scheduler vẫn tiếp tục tick sau đó có làm transaction cũ commit lại không?

**Đáp án:** Không. Rollback của từng transaction và việc còn registration để chạy lần sau là hai cơ chế độc lập.

**Câu 12.** Tại sao default suppress không thay thế được cơ chế retry có backoff?

**Đáp án:** Nó chỉ giữ đăng ký theo đồng hồ, không lưu failed record, retry count, deadline, idempotency hay circuit-breaker state.

**Câu 13.** Một `@Scheduled(initialDelay=...)` one-shot ném lỗi. Spring tự đăng ký one-shot mới để retry không?

**Đáp án:** Không theo one-time contract. Cần retry policy riêng hoặc durable job store nếu nghiệp vụ yêu cầu.

**Câu 14.** Nhiều application instances dùng cùng cron có thể log lỗi và xử lý một record đồng thời không?

**Đáp án:** Có. Scheduling mỗi instance độc lập; cần coordination và idempotency nếu không cho trùng.

### Ghi nhớ

Với **synchronous Spring scheduling mặc định**, khác biệt trọng yếu là **one-shot log và propagate exception**, còn **repeating log và suppress exception** để giữ registration tiếp tục. `ScheduledFuture` của one-shot có thể đưa lỗi tới caller bằng `ExecutionException`; future của repeating không trở thành “thành công/thất bại cho từng tick”. Custom `ErrorHandler` có thể thay đổi chính sách, thậm chí làm lịch lặp dừng. Raw JDK scheduled executor và reactive `@Scheduled` có đường lỗi riêng, nên phải hiểu đúng implementation. Quan sát lỗi **mỗi lượt**, thiết kế retry/idempotency, transaction, timeout và alert rõ ràng; đừng coi “scheduler vẫn active” là “công việc đang thành công”.

### Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — `TaskUtils` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/TaskUtils.html): chính sách default one-shot propagate / repeating suppress, hai `ErrorHandler` và ý nghĩa Future.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `TaskScheduler`, `@Scheduled`, reactive error handling, fixed rate/delay và scheduling infrastructure.
- [Spring Framework 6.2 — `ThreadPoolTaskScheduler` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): `ScheduledThreadPoolExecutor`, setErrorHandler và lifecycle.
- [Spring Framework 6.2 — `Scheduled` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): one-time initialDelay (6.1+), reactive onError, repeatable annotation.
- [Java SE 17 — `ScheduledThreadPoolExecutor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html): exception của periodic task và future completion.
- [Java SE 17 — `Future`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get`, `isDone`, `cancel` và ngoại lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduling-failures-002">ErrorHandler trong hạ tầng scheduler</a>

<details>
<summary>Xem chi tiết</summary>

# ErrorHandler trong hạ tầng scheduler của Spring Framework

Khi một phương thức `@Scheduled` hoặc một `Runnable` được giao cho `TaskScheduler` ném exception, câu hỏi đầu tiên không chỉ là “lỗi gì?” mà còn là **“Lỗi đó có làm mất các lần chạy sau hay không?”**. Với scheduler thuần Java, một exception thoát khỏi lần chạy định kỳ có thể khiến **toàn bộ chuỗi thực thi định kỳ bị dừng**. Spring Framework bổ sung lớp xử lý lỗi thông qua giao diện **`org.springframework.util.ErrorHandler`** và các lớp hỗ trợ của hạ tầng scheduling để quyết định **ghi log, chặn lỗi, hoặc truyền lỗi trở lại scheduler**.

Trong cấu hình scheduling truyền thống của Spring, mặc định thường khác nhau giữa hai loại công việc:

- **Tác vụ lặp lại**: lỗi được ghi log và **không bị ném tiếp khỏi wrapper**, để những lần thực thi tiếp theo không bị chặn chỉ vì một exception.
- **Tác vụ một lần**: lỗi được ghi log và **truyền tiếp**, giúp `Future` có thể thể hiện thất bại của lượt chạy đó.

Đây là hành vi của **`TaskUtils.getDefaultErrorHandler(boolean isRepeatingTask)`** được Spring ghi rõ trong Javadoc. Một `ErrorHandler` do ứng dụng tự cấu hình có thể thay đổi chính sách, vì thế phải phân biệt rõ **handler mặc định**, **handler tùy chỉnh**, **kiểu task**, và **loại scheduler thực tế**. Không được suy rằng “mọi @Scheduled chắc chắn cứ chạy tiếp”, cũng không được suy rằng “bất cứ exception nào đều khiến lịch chết”.

Bài thuộc nhóm **Lỗi của lịch chạy** trong module **Spring Framework - concurrency**, tập trung vào vị trí của `ErrorHandler` trong scheduler, cách cấu hình `ThreadPoolTaskScheduler`, quan hệ với Java `ScheduledExecutorService`, ngoại lệ của `@Scheduled` reactive và `@Async`, cách kiểm thử, quan sát và xây dựng chính sách lỗi phù hợp nghiệp vụ.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích sự khác nhau giữa **exception được ném từ `Runnable` đồng bộ** và **tín hiệu lỗi trong một publisher bất đồng bộ**.
- Nhận diện `org.springframework.util.ErrorHandler`, phương thức `handleError(Throwable)` và vai trò của nó trong hạ tầng Spring scheduling.
- Hiểu vì sao `TaskUtils` dùng chính sách khác nhau cho **repeating** và **one-shot** tasks.
- Biết sự khác nhau giữa `LOG_AND_SUPPRESS_ERROR_HANDLER` và `LOG_AND_PROPAGATE_ERROR_HANDLER` mà không lạm dụng các utility nội bộ.
- Cấu hình `ThreadPoolTaskScheduler.setErrorHandler(...)` và bảo đảm đúng scheduler bean được dùng cho `@Scheduled`.
- Dự đoán tác động của một handler **trả về bình thường** so với một handler **ném exception**.
- Hiểu cách `ScheduledThreadPoolExecutor` của JDK dừng các lượt định kỳ khi exception thoát khỏi một execution.
- Kiểm thử bằng Java 17+ và Spring integration test rằng job lỗi một lần vẫn có thể chạy lần hai khi lỗi được xử lý phù hợp.
- Biết những lỗi **không** được `ErrorHandler` của scheduler nhận: reactive `onError`, `@Async` worker errors, task bị reject trước khi chạy, hoặc exception đã được code nghiệp vụ tự bắt.
- Thiết kế metrics, retry, circuit breaker, transaction, idempotency và job store mà không nhầm “lịch còn chạy” với “công việc đã thành công”.

## 2. Đặt đúng ranh giới: scheduler không phải toàn bộ nghiệp vụ

Xét ba tầng:

~~~text
Spring @Scheduled / TaskScheduler.schedule(...)
                       |
                       v
         Scheduler đăng ký và dispatch
                       |
                       v
              Runnable wrapper
                       |
                       v
              Code nghiệp vụ
                       |
                +------+------+
                |             |
           hoàn thành      ném Throwable
                |             |
                |             v
                |       Spring ErrorHandler
                |             |
                |       +-----+------+
                |       |            |
                |   trả bình thường  ném tiếp
                |       |            |
                v       v            v
        underlying executor    underlying executor
        thấy task hoàn tất     thấy execution lỗi
~~~

**`ErrorHandler` không tự làm lại nghiệp vụ.** Nếu nó nhận một exception rồi chỉ ghi log, lần chạy hiện tại vẫn **đã thất bại về mặt business**, dù wrapper kết thúc bình thường với góc nhìn của executor. Lịch tiếp tục chỉ tạo **một cơ hội chạy tương lai**.

Phân biệt bốn kết luận:

| Phát biểu | Có thể kết luận chỉ từ ErrorHandler không? |
| --- | --- |
| “Một Throwable đã thoát khỏi phần task được bảo vệ” | Có, nếu handler thực sự được gọi |
| “Lỗi đã được ghi log hoặc metric” | Chỉ khi handler làm điều đó thành công |
| “Các lần tick tương lai có thể tiếp tục” | Tùy handler, loại task và scheduler |
| “Dữ liệu nghiệp vụ đã rollback, công việc lỗi được retry an toàn” | Không |

Hạ tầng scheduler biết **các lần kích hoạt**, không tự biết một item trong database đã commit, một email đã gửi hay một external request có idempotent hay không.

## 3. ErrorHandler của Spring là interface nào?

Giao diện là `org.springframework.util.ErrorHandler`, **không phải** `java.util.concurrent.ThreadPoolExecutor.RejectedExecutionHandler` và không phải interface error handler của HTTP framework.

Hợp đồng rất nhỏ:

~~~java
package org.springframework.util;

@FunctionalInterface
public interface ErrorHandler {
    void handleError(Throwable t);
}
~~~

Đây là **chữ ký API minh họa**; trong ứng dụng dùng interface từ Spring, không tạo lại file này. Điểm cần lưu ý:

- Tham số là **`Throwable`**, rộng hơn `Exception` và `RuntimeException`.
- Method trả `void`, nên thông thường handler chỉ ghi log, metric, cập nhật state hoặc phát tín hiệu quản trị.
- Nếu method **trả về bình thường**, wrapper có thể xem exception đã được xử lý.
- Nếu handler **ném lại Throwable dưới dạng unchecked `RuntimeException`/`Error`**, exception có thể thoát khỏi scheduled execution; tác động đến lịch phụ thuộc executor và kiểu registration.
- Handler **không biết sẵn job ID, trigger name hoặc payload**: hợp đồng chỉ truyền `Throwable`. Để phân loại theo job, cần log context hoặc một wrapper ứng dụng chứa metadata tương ứng.

### Không nhầm với các ErrorHandler khác

| Thành phần | Đường lỗi | Phạm vi |
| --- | --- | --- |
| `org.springframework.util.ErrorHandler` | Throwable thoát từ scheduled task wrapper đồng bộ | Lỗi ở tầng scheduler |
| `AsyncUncaughtExceptionHandler` | Uncaught exception của method `@Async void` | Lỗi ở tầng `@Async` |
| `@ControllerAdvice` / HTTP exception handler | Exception khi xử lý HTTP request | Request-response, không tự xử lý scheduler |
| Reactor `doOnError` / `onErrorResume` | Tín hiệu `onError` trong reactive pipeline | Một subscription và operator chain |
| `RejectedExecutionHandler` của JDK | Executor từ chối task tại lúc submit/schedule | Admission/overload, không phải Throwable từ body đã chạy |
| Global `Thread.UncaughtExceptionHandler` | Exception thực sự thoát khỏi thread boundary | Không phải chiến lược đầy đủ cho `ScheduledFuture` |

Dùng đúng handler cho đúng ranh giới. Một dashboard chỉ lắng nghe HTTP exceptions sẽ không thể tự động thấy các lỗi của scheduled jobs chạy nền.

## 4. Nền tảng JDK: tại sao exception có thể khiến lịch lặp dừng?

Java `ScheduledExecutorService.scheduleAtFixedRate` và `scheduleWithFixedDelay` tạo một **chuỗi periodic executions**. Theo Javadoc của Java 17, nếu một execution của chuỗi định kỳ **ném exception ra ngoài**, các execution tiếp theo của **chuỗi đó** bị chặn. `ScheduledFuture.get()` khi ấy có thể ném `ExecutionException` chứa nguyên nhân.

~~~java
ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor();

ScheduledFuture<?> future = executor.scheduleAtFixedRate(
        () -> {
            throw new IllegalStateException("failure");
        },
        0, 1, TimeUnit.SECONDS);
~~~

Đây là **fragment Java SE**; ứng dụng thật phải đóng executor trong lifecycle và xử lý Future. Tác vụ định kỳ trên không có wrapper chặn exception. Lần thực thi đầu ném `IllegalStateException`, nên scheduler JDK **không tiếp tục chuỗi đó**.

Điều này không có nghĩa toàn bộ `ScheduledThreadPoolExecutor` bị hỏng: những task khác vẫn có thể được nhận và chạy. Nó cũng không có nghĩa một method `@Scheduled` qua Spring sẽ mặc định giống hệt, bởi Spring có thể bọc `Runnable` bằng `ErrorHandler` trước khi giao cho JDK executor.

### Tại sao không dùng Thread.UncaughtExceptionHandler để thay thế?

Khi `ScheduledThreadPoolExecutor` chạy một task có Future, exception có thể được **lưu trong Future** thay vì thoát trực tiếp khỏi thread worker theo cách mà `UncaughtExceptionHandler` có thể quan sát. Vì vậy không nên dùng một global thread uncaught handler làm kênh giám sát duy nhất. Với Spring scheduling, cấu hình `ErrorHandler` và metrics của job phù hợp hơn.

### One-shot khác periodic ở điểm nào?

Một tác vụ chạy một lần:

~~~java
ScheduledFuture<?> once = executor.schedule(
        () -> {
            throw new IllegalArgumentException("one-shot failure");
        },
        0, TimeUnit.MILLISECONDS);
~~~

Nếu body ném exception và không bị chặn, `once.get()` ném `ExecutionException`; `once.isDone()` là `true`. Không có “lượt sau” của cùng registration để bị chặn. Chính sự khác biệt này giải thích tại sao chính sách lỗi mặc định của Spring thường **truyền lỗi cho one-shot** nhưng **chặn lỗi cho repeating task**.

## 5. TaskUtils: hai ErrorHandler mặc định của Spring

Spring có class `org.springframework.scheduling.support.TaskUtils`, cung cấp các chiến lược:

| Hằng / phương thức | Hành vi được tài liệu hóa |
| --- | --- |
| `LOG_AND_SUPPRESS_ERROR_HANDLER` | Ghi log lỗi rồi không ném lại; có thể để các lượt định kỳ sau không bị chặn |
| `LOG_AND_PROPAGATE_ERROR_HANDLER` | Ghi log lỗi rồi ném lại; thường làm chuỗi periodic execution không thể tiếp tục nếu exception thoát ra |
| `getDefaultErrorHandler(true)` | Lựa chọn mặc định dành cho **repeating task**: log và suppress |
| `getDefaultErrorHandler(false)` | Lựa chọn mặc định dành cho **one-shot task**: log và propagate |
| `decorateTaskWithErrorHandler(...)` | Bọc Runnable để điều phối Throwable tới ErrorHandler |

`TaskUtils` **được Spring đánh dấu là utility phục vụ nội bộ cho scheduler implementations**. Người viết ứng dụng thường **không cần gọi trực tiếp** `TaskUtils.decorateTaskWithErrorHandler`. Thay vào đó, dùng cấu hình của scheduler hoặc xử lý lỗi rõ ràng bên trong job.

### Mặc định lặp lại

Nếu `@Scheduled(fixedDelay=...)` dùng `ThreadPoolTaskScheduler` với cơ chế mặc định, một exception unchecked từ body có thể được wrapper bắt, ghi log và **không ném lại lên `ScheduledThreadPoolExecutor`**. JDK thấy execution của wrapper kết thúc nên chuỗi định kỳ tiếp tục theo nhịp.

### Mặc định một lần

Với `ThreadPoolTaskScheduler.schedule(task, instant)` cho một lần, handler mặc định có thể log rồi **propagate** exception, để người giữ `ScheduledFuture` thấy thất bại qua `get()`.

### Nếu custom ErrorHandler được cung cấp

Khi cấu hình một handler khác, bạn đang **thay đổi chính sách**. Ví dụ handler luôn trả về bình thường có thể làm **one-shot Future trông như thành công về mặt execution wrapper**, dù nghiệp vụ bên trong đã ném exception. Nếu handler ném lại, một repeating task vốn được mặc định tiếp tục có thể bị dừng. Vì vậy custom handler phải được chọn theo hợp đồng của từng nhóm job, không phải chỉ vì “muốn log đẹp hơn”.

## 6. Luồng lỗi của Spring @Scheduled đồng bộ

Một lần chạy có thể được hình dung:

~~~text
@Scheduled method trên Spring-managed bean
       |
       v
ScheduledAnnotationBeanPostProcessor đăng ký lịch
       |
       v
TaskScheduler phù hợp
       |
       v
Error-handling Runnable wrapper
       |
       +-- gọi method nghiệp vụ
               |
               +-- return bình thường --> next tick theo trigger
               |
               +-- throw Throwable
                           |
                           v
                       ErrorHandler
                           |
               +-----------+-----------+
               |                       |
         suppress/return            propagate
               |                       |
               v                       v
     executor thấy wrapper      executor thấy task lỗi
     hoàn tất bình thường        (có thể dừng series)
               |
               v
           next tick
~~~

Đây là mô hình **synchronous scheduling**. Không nhầm với reactive publisher, không nhầm với một method được `@Async` chuyển sang executor khác, và không nhầm với việc task bị **reject trước khi chạy**.

### Default logging không phải business acknowledgment

Handler mặc định chủ yếu bảo vệ chu kỳ scheduler và để lại thông tin lỗi. Nó không ghi job trạng thái FAILED vào một database ứng dụng, không bổ sung `retryWhen`, không gửi message sang dead-letter queue, không giữ distributed lock, và không rollback một external side effect đã commit.

Đặt câu hỏi đúng: **lịch có nên tiếp tục phát tick dù một lượt lỗi không?** Sau đó trả lời độc lập: **mỗi item bị lỗi được theo dõi và xử lý lại ra sao?**

## 7. Cấu hình ThreadPoolTaskScheduler.setErrorHandler

`ThreadPoolTaskScheduler` là scheduler phổ biến cho Spring applications chạy độc lập. Nó bao một `ScheduledThreadPoolExecutor` và có phương thức:

~~~java
void setErrorHandler(ErrorHandler errorHandler);
~~~

Đây là **chữ ký Spring rút gọn**, không phải hàm khai báo trong mọi class `TaskScheduler`. Ví dụ một bean Spring:

Tệp **SchedulerErrorConfig.java**:

~~~java
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulerErrorConfig {
    private static final Logger log =
            LoggerFactory.getLogger(SchedulerErrorConfig.class);

    private final AtomicLong failures = new AtomicLong();

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("business-schedule-");

        scheduler.setErrorHandler(error -> {
            failures.incrementAndGet();
            log.error("A scheduled invocation failed", error);
            // Không ném lại: chính sách best-effort CONTINUE.
        });

        return scheduler;
    }

    public long observedFailures() {
        return failures.get();
    }
}
~~~

Đây là **Spring configuration class**, cần `spring-context`, SLF4J, Spring-managed `ApplicationContext` và những method `@Scheduled` thuộc bean ứng dụng. Không thể chạy chỉ bằng `javac SchedulerErrorConfig.java` khi không có dependencies. Các tham số pool chỉ minh họa, không phải sizing production.

**Giải thích chính xác:**

1. `@EnableScheduling` kích hoạt xử lý `@Scheduled`.
2. Bean tên `taskScheduler` giúp Spring resolve scheduler theo quy ước, miễn không có cấu hình khác ghi đè/chọn scheduler khác.
3. `setErrorHandler(...)` đặt một callback **chung** cho những task dùng scheduler bean này.
4. `failures.incrementAndGet()` là metric RAM, chỉ dùng minh họa. Trong production, dùng Micrometer/counter với tags phù hợp và không tự đếm lại nhiều lần.
5. `log.error(...)` ghi exception cùng stack trace; **handler trả bình thường**, nên policy là suppress.
6. `observedFailures()` chỉ là số lần handler được gọi trên instance này, không phải số job failed trên toàn cụm.
7. Spring quản lý `ThreadPoolTaskScheduler` bean lifecycle và shutdown khi context đóng, theo cấu hình tương ứng.

### Vì sao setErrorHandler không dùng trực tiếp trên TaskScheduler?

Giao diện `TaskScheduler` là abstraction lên lịch, không phải API quản trị lỗi chung. Một implementation khác có thể không có setter tương tự hoặc có chính sách riêng. Nếu bean được inject dưới kiểu `TaskScheduler`, muốn set error policy phải cấu hình **implementation bean** tại nơi tạo nó, không cast tùy tiện ở một service nghiệp vụ.

### Không bắt buộc phải set custom handler

Nếu chỉ muốn một periodic `@Scheduled` tiếp tục sau một exception và được ghi log, **chính sách mặc định của Spring đã hỗ trợ điều đó** đối với các repeating tasks trong scheduler truyền thống. Việc tự tạo handler mà chỉ log trùng có thể làm khó bảo trì hoặc bỏ sót thông tin khi code logger có lỗi. Custom handler hợp lý khi cần metric, correlation, severity policy hoặc escalation đặc thù.

## 8. Spring @Scheduled có thật sự sử dụng scheduler bean đó?

Tạo `ThreadPoolTaskScheduler` bean là chưa đủ nếu một cấu hình khác chọn scheduler khác. Spring có thể chọn scheduler theo **unique bean**, tên `taskScheduler`, qualifier hoặc `SchedulingConfigurer`; trong một số trường hợp, khi không tìm thấy scheduler, hạ tầng có thể tạo scheduler mặc định.

Ví dụ job:

Tệp **CleanupJob.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupJob {
    private final AtomicInteger attempts = new AtomicInteger();

    @Scheduled(
            fixedDelayString = "${jobs.cleanup.delay:PT10S}")
    public void execute() {
        int attempt = attempts.incrementAndGet();

        if (attempt == 1) {
            throw new IllegalStateException("temporary cleanup failure");
        }

        System.out.println("Cleanup invocation " + attempt);
    }
}
~~~

Đây là **Spring component** dùng với `SchedulerErrorConfig` ở phần trước. Lượt đầu ném exception; handler được cấu hình trên scheduler có thể ghi log/metric, rồi các lượt sau vẫn được dispatch theo fixed-delay.

Không có stdout cố định vì số tick phụ thuộc thời gian ứng dụng chạy. Nếu handler tự ném RuntimeException hoặc một scheduler khác được chọn, behavior có thể khác. Muốn chắc chắn, phải kiểm tra scheduler bean đang dùng **trong integration test**.

### Chọn một scheduler tường minh

Nếu ứng dụng có nhiều scheduler, có thể dùng `SchedulingConfigurer`:

~~~java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class ExplicitSchedulerSelection implements SchedulingConfigurer {
    private final TaskScheduler selected;

    public ExplicitSchedulerSelection(
            @Qualifier("taskScheduler") TaskScheduler selected) {
        this.selected = selected;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(selected);
    }
}
~~~

Đây là **một Spring configuration class**. Lựa chọn tường minh làm rõ vì sao handler được gọi: task và handler phải thuộc **cùng scheduler instance**. Nếu một số job dùng `@Scheduled(scheduler = "...")` với bean khác (ở Spring version hỗ trợ thuộc tính này), phải xem handler của bean **thực tế được chọn**, không chỉ bean tên mặc định.

## 9. Java 17+ chạy độc lập: exception thoát ra sẽ dừng periodic series

Để kiểm chứng cơ chế thấp nhất, chương trình dưới đây dùng **JDK `ScheduledExecutorService`**, không cần Spring. Nó mô phỏng ba đường:

1. `Runnable` periodic ném `RuntimeException` và **không được bắt**, vì vậy future thất bại và các lượt sau bị chặn.
2. Task **one-shot** thất bại, và `ScheduledFuture.get()` nhận `ExecutionException`.
3. Periodic task được **wrapper bắt RuntimeException và trả bình thường**; hai lượt đầu lỗi nhưng lượt thứ ba vẫn chạy, sau đó caller hủy series.

Đây là phép chứng minh bằng Java 17, không phải implementation của `TaskUtils`; wrapper thứ ba cố ý chỉ bắt `RuntimeException`, không nuốt mọi `Error` của JVM.

Tệp **SchedulerErrorBoundaryDemo.java**:

~~~java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class SchedulerErrorBoundaryDemo {

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        try {
            AtomicInteger rawRuns = new AtomicInteger();

            ScheduledFuture<?> raw = scheduler.scheduleAtFixedRate(() -> {
                rawRuns.incrementAndGet();
                throw new IllegalStateException("raw task fails");
            }, 0, 25, TimeUnit.MILLISECONDS);

            String rawFailure = "NONE";

            try {
                raw.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                rawFailure = ex.getCause()
                        .getClass().getSimpleName();
            }

            System.out.println("rawRuns=" + rawRuns.get());
            System.out.println("rawFailure=" + rawFailure);
            System.out.println("rawDone=" + raw.isDone());

            ScheduledFuture<?> once = scheduler.schedule(
                    () -> {
                        throw new IllegalArgumentException("once");
                    },
                    0, TimeUnit.MILLISECONDS);

            String oneShotFailure = "NONE";

            try {
                once.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                oneShotFailure = ex.getCause()
                        .getClass().getSimpleName();
            }

            System.out.println("oneShotFailure=" + oneShotFailure);
            System.out.println("oneShotDone=" + once.isDone());

            AtomicInteger attempts = new AtomicInteger();
            AtomicInteger handled = new AtomicInteger();
            CountDownLatch third = new CountDownLatch(1);

            ScheduledFuture<?> wrapped =
                    scheduler.scheduleAtFixedRate(() -> {
                        try {
                            int attempt = attempts.incrementAndGet();

                            if (attempt <= 2) {
                                throw new IllegalStateException(
                                        "temporary " + attempt);
                            }

                            if (attempt == 3) {
                                third.countDown();
                            }
                        } catch (RuntimeException ex) {
                            handled.incrementAndGet();
                        }
                    },
                    0, 25, TimeUnit.MILLISECONDS);

            if (!third.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Third invocation did not run");
            }

            boolean cancelled = wrapped.cancel(false);

            System.out.println("wrappedAtLeastThree="
                    + (attempts.get() >= 3));
            System.out.println("handlerCaughtTwo="
                    + (handled.get() == 2));
            System.out.println("wrappedCancelled=" + cancelled);
        } finally {
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac SchedulerErrorBoundaryDemo.java
java SchedulerErrorBoundaryDemo
~~~

Stdout dự kiến:

~~~text
rawRuns=1
rawFailure=IllegalStateException
rawDone=true
oneShotFailure=IllegalArgumentException
oneShotDone=true
wrappedAtLeastThree=true
handlerCaughtTwo=true
wrappedCancelled=true
~~~

### Tại sao kiểm thử này không bị race ngẫu nhiên?

- `raw.get(5, TimeUnit.SECONDS)` chờ periodic series kết thúc vì failure. `rawRuns=1` chứng minh exception đầu đã dừng chính **series đó**; executor vẫn dùng được.
- `once.get(...)` chờ one-shot hoàn tất bất thường, nên nguyên nhân được quan sát qua `ExecutionException`.
- `third.await(...)` không dùng `Thread.sleep`; nó chờ cho tới lượt thứ ba thật sự chạy. Do tick thứ tư có thể bắt đầu trước lúc main gọi cancel, chương trình cố ý in **`wrappedAtLeastThree`** thay vì đòi đúng ba lượt.
- Hai exception được chủ động phát ở attempts 1 và 2, nên `handlerCaughtTwo=true` không phụ thuộc số tick đã tiếp tục sau lượt ba.
- `wrapped.cancel(false)` dừng chuỗi lặp ở mức Future nhưng không đòi interrupt một body đang chạy.
- `finally` shutdown **executor local do demo tự tạo**; không áp dụng lời gọi shutdown cho Jakarta EE managed scheduler.
- Mọi wait đều có timeout 5 giây, tránh treo vô hạn nếu JVM/CI có sự cố.

**Giới hạn:** ví dụ này chứng minh semantics của JDK periodic execution và một wrapper tự viết. Nó **không** chứng minh Spring bean đã chọn đúng `ThreadPoolTaskScheduler`, cấu hình `ErrorHandler` được áp dụng, hay logging thật của Spring. Những điều đó cần Spring integration test.

## 10. Handler trả bình thường và handler ném lại khác nhau thế nào?

Có hai chiến lược chính:

### 10.1 Best-effort continue

~~~java
scheduler.setErrorHandler(error -> {
    monitoring.recordScheduledFailure(error);
    log.error("Scheduled task failed", error);
    // Không throw => wrapper nhận lỗi đã được xử lý.
});
~~~

Đây là **fragment cấu hình** với `scheduler` là `ThreadPoolTaskScheduler`, `monitoring`/`log` là dịch vụ ứng dụng. Chính sách này thích hợp cho polling/cleanup mà một tick lỗi không nên làm mất toàn bộ lịch. Nhưng **không được dùng lỗi đã được chặn làm bằng chứng business work thành công**.

### 10.2 Fail-fast / stop the series

~~~java
scheduler.setErrorHandler(error -> {
    log.error("Critical scheduled task failed", error);
    throw new IllegalStateException(
            "Escalating scheduled failure", error);
});
~~~

Đây cũng là **fragment**, có thể làm error propagate khỏi wrapper. Với periodic scheduling dựa trên `ScheduledThreadPoolExecutor`, một exception thoát ra sẽ thường **kết thúc series**, làm những tick sau không chạy. Với cron/trigger và scheduler khác, cơ chế reschedule cụ thể có thể khác; **phải test implementation đang deploy**.

Fail-fast có thể hữu ích khi tiếp tục sẽ gây dữ liệu sai hoặc job không còn điều kiện an toàn. Nhưng nếu job chỉ bị lỗi tạm thời 503, chấm dứt lịch cho đến khi restart có thể làm mất việc. Không biến chính sách lỗi toàn cục thành fail-fast khi chỉ một job nhỏ cần behavior đó.

### Handler bản thân cũng có thể lỗi

Nếu `monitoring.recordScheduledFailure(...)` ném RuntimeException hoặc logging backend bị lỗi khiến handler throw, policy thực tế có thể biến thành propagate **ngoài ý định**. Vì thế handler nên ngắn, đáng tin cậy, không block network vô hạn, không tự thực hiện một giao dịch lớn, và có bảo vệ cho các side effect phụ của chính nó.

Một custom `ErrorHandler` dạng “cứ throw lại mọi lỗi” không hẳn là tăng reliability: nó có thể làm lịch biến mất âm thầm nếu không có alert và cơ chế đăng ký lại.

## 11. Khi nào chọn suppress, propagate hoặc retry?

Không có một chính sách tốt cho tất cả scheduled jobs:

| Loại công việc | Chính sách gợi ý | Lý do |
| --- | --- | --- |
| Cleanup cache tạm thời, retry được ở tick sau | Log/metric và tiếp tục schedule | Tick sau có thể xử lý trạng thái còn tồn |
| Polling queue có trạng thái PENDING bền vững | Log/metric và tiếp tục, retry theo job state | Lỗi tick không làm mất item |
| Đồng bộ hệ thống ngoài bị 503 | Backoff/timeout/circuit breaker; schedule còn nhưng có điều tiết | Tránh request storm |
| Tính số liệu tài chính có yêu cầu xử lý đủ từng kỳ | Lưu business window/job state; không chỉ suppress | Tick sau không mặc định bù kỳ đã lỡ |
| Vi phạm cấu hình khiến mọi lượt đều sai | Fail-fast hoặc disable/alert rõ, tùy SLA | Tránh lặp vô ích vô hạn |
| Job nguy hiểm nếu chạy lại sau partial commit | Dừng/circuit breaker và reconciliation | Tránh double side effect |
| One-shot có caller chờ kết quả | Propagate vào Future, đồng thời ghi lỗi | Caller cần biết tác vụ thất bại |
| Event/outbox delivery quan trọng | Durable outbox, idempotency, retry chính thức | Lịch tiếp tục không phải delivery guarantee |
| Task chạy trên Jakarta EE managed scheduler | Kiểm tra chính sách của managed executor | Không được giả định giống local pool |

**Khuyến nghị thiết kế:** ErrorHandler ở scheduler nên làm **biên giới ghi nhận và xác định có tiếp tục đăng ký hay không**. Retry nghiệp vụ, deduplication, backoff và persistence nên thuộc **job execution/service layer** hoặc job store, không nhồi vào ErrorHandler đồng bộ.

## 12. ErrorHandler không phải RetryTemplate hoặc circuit breaker

Một cách dễ sai là làm việc nặng ngay trong `handleError`:

~~~java
scheduler.setErrorHandler(error -> {
    // Không khuyến nghị gọi lại cùng job bằng một API không có context.
    remoteService.retryFailedRequest();
});
~~~

Đây là **anti-pattern**: handler chỉ nhận Throwable, không nhận đầy đủ context của item đang lỗi. Nó có thể không biết transaction đã commit phần nào, retries còn bao nhiêu, hay job ID nào cần xử lý. Nếu gọi network trong handler, bạn còn chặn worker scheduler.

Giải pháp phù hợp hơn:

1. **Bên trong job**: xác định input/jobId, chia thành transaction boundary rõ, áp retry hữu hạn với backoff nếu lỗi transient.
2. **Job state**: cập nhật FAILED/RETRYABLE với nextAttemptAt và attempt count.
3. **ErrorHandler**: ghi lỗi cuối cùng từ execution và gửi metric, có thể suppress để scheduler tiếp tục poll.
4. **Alert/circuit breaker**: dựa trên failure rate, last success và backlog để quyết định tạm dừng job có chủ đích.
5. **Idempotency**: bảo vệ side effects khi một lần retry có thể chạy sau khi lần cũ đã gửi request thành công nhưng chưa nhận ack.

### Tránh cộng chồng retries

Một execution tự retry ba lần rồi vẫn lỗi; handler suppress để tick sau lại tự retry ba lần. Nếu cron mỗi 5 giây, một outage có thể gây request storm. Hãy có **ngân sách retry tổng**, rate limit, backoff/jitter và circuit breaker nếu downstream dễ quá tải.

### Không tự hứa rollback

Suppression hay propagation của exception ở hạ tầng scheduler **không trực tiếp điều khiển commit** của transaction đã chạy. Nếu Spring `@Transactional` proxy xử lý exception ở service trước khi nó thoát đến scheduler, transaction manager có thể rollback theo rule; nếu commit đã xong, ErrorHandler không thể quay ngược hiệu ứng đó. External HTTP/email/queue side effect còn cần cơ chế bù trừ riêng.

## 13. Lỗi đồng bộ, @Async và reactive: ba đường khác nhau

Hãy xác định **lỗi xuất hiện ở thread và API nào**.

### 13.1 Scheduled synchronous method

~~~java
@Scheduled(fixedDelay = 10000)
public void cleanup() {
    cleanupService.removeExpired();
}
~~~

Đây là Spring method fragment. Nếu `removeExpired()` ném unchecked exception **thẳng ra method** và scheduler đã bọc Runnable với `ErrorHandler`, handler có thể nhận lỗi.

### 13.2 Scheduled method dispatch sang @Async

~~~java
@Async("workerExecutor")
@Scheduled(fixedRate = 10000)
public void dispatch() {
    workerService.execute();
}
~~~

Đây là fragment cần `@EnableAsync`, `@EnableScheduling` và executor bean. Scheduler có thể chỉ thực hiện thao tác **dispatch** rồi return; body chạy trên **async worker**. Exception ném sau đó trên worker **không nhất thiết đi qua ErrorHandler của TaskScheduler**. Với `@Async void`, Spring có đường `AsyncUncaughtExceptionHandler`; với `@Async Future/CompletableFuture`, lỗi có thể nằm trong future completion.

Đừng kết luận scheduler handler không được gọi tức là job không lỗi. Cần quan sát async execution riêng.

### 13.3 Reactive @Scheduled (Spring 6.1+)

~~~java
@Scheduled(fixedRate = 10000)
public Mono<Void> pollReactive() {
    return Mono.defer(() ->
            reactiveClient.pollOnce());
}
~~~

Đây là Spring Reactor fragment, cần `Mono`/Reactor dependency. Spring **lấy Publisher một lần** khi đăng ký rồi tạo subscription mới theo tick. Một subscription có thể phát `onError` không bằng cách ném exception trực tiếp khỏi Runnable. Spring **ghi log WARN và recover ranh giới subscription**, cho phép các subscription theo lịch sau tiếp tục. Theo Spring reference, **`ErrorHandler` của synchronous Runnable không tham gia** xử lý loại lỗi này.

Bảng so sánh:

| Đường lỗi | Nơi xem lỗi trước tiên | Có phải TaskScheduler ErrorHandler xử lý? |
| --- | --- | --- |
| `@Scheduled void` ném unchecked từ body | Scheduler wrapper / ErrorHandler | Thông thường có, khi qua scheduler cấu hình phù hợp |
| `TaskScheduler.schedule(Runnable, ...)` ném từ Runnable | Scheduler wrapper / Future | Phụ thuộc implementation, handler policy |
| `@Async void` worker ném exception | AsyncUncaughtExceptionHandler / log | Không mặc định |
| `@Async CompletableFuture` lỗi | Exceptional completion/observer | Không mặc định |
| Reactive Publisher phát `onError` | Reactive subscriber, WARN, `doOnError` | **Không** theo Spring 6.1+ |
| Task bị reject khi đăng ký/dispatch | Caller của schedule/submit, exception rejection | Không phải lỗi từ body |
| Lỗi đã bị service tự bắt | Service metrics/log/job state | Không, vì không thoát ra scheduler |

### Reactive onError không chứng minh business recovery

Nếu publisher phát onError ở tick #1, tick #2 có thể được subscribe, nhưng có thể xử lý một batch khác. Muốn retry item lỗi, dùng `retryWhen` hữu hạn hoặc durable job store; muốn log/metrics theo item, dùng `doOnError` và correlation ID. `onErrorResume` có thể chuyển lỗi thành onComplete, khiến Spring không thấy WARN nữa; hãy dùng cẩn thận.

## 14. Spring integration test: xác minh custom ErrorHandler và lượt sau

Ví dụ JUnit 5 sau chạy trong **Spring ApplicationContext thật** (Spring 6.x, JUnit 5), sử dụng một scheduler test riêng. Nó chứng minh: tick đầu ném exception, handler ghi nhận một lỗi và **trả về bình thường**, tick thứ hai vẫn chạy.

Tệp **ScheduledErrorIntegrationTest.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledErrorIntegrationTest {

    @Configuration
    @EnableScheduling
    static class Config {

        @Bean
        ErrorRecorder errorRecorder() {
            return new ErrorRecorder();
        }

        @Bean
        ProbeJob probeJob() {
            return new ProbeJob();
        }

        @Bean(name = "taskScheduler")
        ThreadPoolTaskScheduler taskScheduler(ErrorRecorder recorder) {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setErrorHandler(recorder::record);
            return scheduler;
        }
    }

    static class ErrorRecorder {
        final AtomicInteger count = new AtomicInteger();

        void record(Throwable error) {
            count.incrementAndGet();
            // Test handler trả bình thường: không propagate.
        }
    }

    static class ProbeJob {
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch secondTick = new CountDownLatch(1);

        @Scheduled(initialDelay = 0, fixedDelay = 20)
        public void run() {
            int n = calls.incrementAndGet();

            if (n == 1) {
                throw new IllegalStateException("first tick fails");
            }

            if (n == 2) {
                secondTick.countDown();
            }
        }
    }

    @Test
    void handlerSuppressesAndNextTickRuns() throws Exception {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            ProbeJob job = context.getBean(ProbeJob.class);

            assertTrue(job.secondTick.await(5, TimeUnit.SECONDS));
            assertTrue(job.calls.get() >= 2);
            assertEquals(1,
                    context.getBean(ErrorRecorder.class).count.get());
        }
    }
}
~~~

**Phụ thuộc:** `spring-context` 6.x, JUnit Jupiter và test runtime; đây **không phải Java SE executable độc lập**. Cần tổ chức file/package để JUnit discovery chạy; nếu một application có scheduler bean hoặc AOP thêm từ cấu hình ngoài, giữ test context cô lập như ví dụ.

### Vì sao assert này đáng tin?

- `@EnableScheduling` và bean `taskScheduler` thực sự được tạo trong Spring context.
- `ProbeJob.run()` ném lỗi **chỉ ở tick đầu**.
- `ErrorRecorder.record` nhận Throwable nhưng không throw, cho scheduler tiếp tục.
- `secondTick` chỉ được count down khi tick thứ hai thực sự vào body.
- `await(5s)` là synchronization có deadline, không phải ngủ một khoảng ngẫu nhiên rồi suy luận.
- Kiểm tra `calls >= 2` thay vì `calls == 2`: tick thứ ba có thể chạy trước assertion.
- Kiểm tra error count bằng 1: chỉ tick đầu bị ném exception.
- `try-with-resources` đóng ApplicationContext sau test, giải phóng scheduled registrations và scheduler do context sở hữu.

Nếu cần test handler **mặc định**, bỏ `setErrorHandler(...)`, thay việc assert counter bằng test log capture và xác minh second tick vẫn chạy. Lưu ý test log có thể bị logger backend hoặc context khác tạo noise; assert theo logger/exception cụ thể.

## 15. Kiểm thử một lần và Future exception

Trong khi periodic jobs cần chứng minh **lượt sau**, one-shot jobs cần chứng minh **caller nhận được failure**. Spring `ThreadPoolTaskScheduler` có API như:

~~~java
ScheduledFuture<?> future =
        scheduler.schedule(
                () -> {
                    throw new IllegalStateException("one-shot error");
                },
                java.time.Instant.now());
~~~

Đây là fragment trong môi trường có `scheduler` `TaskScheduler` thực. Với handler mặc định one-shot log-and-propagate, `future.get()` thường ném `ExecutionException` và `future.isDone()` là true. Phải xử lý `future == null` theo contract của API/trigger khi cần; ở `schedule(Runnable, Instant)` bình thường trả handle.

### Tại sao một custom suppress handler làm test khó hơn?

Nếu bạn gắn handler suppress cho mọi task trên một scheduler, one-shot body có thể thất bại nhưng wrapper kết thúc bình thường. Khi đó `ScheduledFuture.get()` có thể không báo lỗi nghiệp vụ như kỳ vọng. Vì thế:

- Một scheduler cho **repeating best-effort** có thể dùng suppress.
- Một scheduler khác cho **one-shot có kết quả quan trọng** có thể dùng propagate.
- Hoặc thiết kế task trả kết quả/exception có nguồn truth riêng như `CompletableFuture`/job state.

Đừng dùng một error policy toàn cục mà không phân loại loại task, nhất là khi một scheduler phục vụ cả cron và job one-shot cần audit.

## 16. Trigger, cron và chuỗi thực thi sau exception

Spring có các đường lập lịch:

- `scheduleAtFixedRate(Runnable, ...)`.
- `scheduleWithFixedDelay(Runnable, ...)`.
- `schedule(Runnable, Trigger)` với `CronTrigger`/custom Trigger.
- `@Scheduled(cron=...)` hoặc fixed delay/rate.
- `@Scheduled` reactive theo Publisher.

Khi exception thoát khỏi task, tác động chính xác phụ thuộc **wrapper** và **cách scheduler đăng ký lần sau**. Với underlying JDK `ScheduledThreadPoolExecutor.scheduleAtFixedRate`, exception thoát làm dừng các lượt sau theo JDK. Với một `Trigger` custom, scheduler thường tính lần kế sau khi task trước hoàn thành; nếu wrapper gặp exception mà handler propagate, đường rescheduling có thể không chạy.

**Đừng tuyên bố mọi implementation hoặc Trigger đều chắc chắn tiếp tục hay dừng chỉ dựa trên tên annotation.** Hãy nhìn scheduler bean thực, handler, future state và integration test trên đúng phiên bản Spring. Với `TaskUtils` default repeating suppress, kỳ vọng thực tế là scheduler có thể tiếp tục các lần sau sau khi lỗi được ghi log.

### Một custom Trigger có thể tự trả null

Nếu `Trigger.nextExecution(TriggerContext)` trả `null`, Spring **không có lần fire tiếp theo** cho registration đó. Đó là **quyết định lịch** độc lập với ErrorHandler. Nếu một operator thấy task không còn chạy, hãy kiểm tra đồng thời:

1. Có Throwable khiến future/registration dừng không?
2. Trigger có trả `null` hoặc cron đã bị disable bằng `"-"` không?
3. Executor/context có shutdown hay task bị cancel không?
4. Application còn Spring-managed bean và `@EnableScheduling` không?
5. Có rejection/admission policy chặn registration mới không?

Không nhìn thấy log exception không đủ để kết luận scheduler vẫn có task đang đăng ký.

## 17. Những lỗi mà ErrorHandler không nhận được

### 17.1 Lỗi đã bị code nghiệp vụ tự bắt

~~~java
@Scheduled(fixedDelay = 10000)
public void run() {
    try {
        service.callRemote();
    } catch (RuntimeException error) {
        log.warn("Remote call failed", error);
        // Method return bình thường.
    }
}
~~~

Đây là Spring fragment với service/log của ứng dụng. Nếu exception bị bắt và không ném lại, scheduler wrapper chỉ nhìn thấy method hoàn tất bình thường. `ErrorHandler` không được gọi cho lỗi đó. Cần metric ở bên trong hoặc rethrow có chủ đích.

### 17.2 Lỗi từ job chạy ở executor khác

Nếu scheduled method chỉ gọi `executor.submit(work)` rồi return, `work` có thể fail trên thread khác. Với `FutureTask`, lỗi có thể nằm trong Future; scheduler không tự gọi `get()` cho nó. Nếu muốn báo lỗi, quan sát Future/completion hoặc dùng service wrapper thích hợp.

### 17.3 Task không được executor nhận

Nếu scheduler shutdown, hết capacity hoặc policy từ chối, lời gọi `schedule(...)` có thể ném `TaskRejectedException`/`RejectedExecutionException` **trước khi Runnable được thực thi**. `ErrorHandler` cho body không đảm nhận admission failure. Cần catch ở nơi đăng ký/submit, phản hồi overload và ghi job state; không dùng Handler như kênh duy nhất.

### 17.4 Lỗi tạo bean hoặc cron không hợp lệ

`@Scheduled(cron = "bad")`, thiếu dependency hoặc thiếu JNDI managed executor có thể khiến application startup/registration fail. Task chưa có cơ hội chạy, nên không có exception của scheduled `Runnable` để handler xử lý. Đây là configuration/bean creation error, cần startup validation.

### 17.5 VM fatal error, interruption và resource exhaustion

`ErrorHandler` nhận `Throwable`; không phải mọi Throwable đều an toàn để suppress. `OutOfMemoryError` và các lỗi JVM nghiêm trọng cần chính sách vận hành riêng. Việc nuốt mọi `Error` và tiếp tục vô hạn có thể che dấu một process đã hỏng. Tương tự, `InterruptedException` là tín hiệu phối hợp hủy: nếu đã bắt, phải xử lý và thường khôi phục cờ interrupt khi không thể truyền tiếp; không biến nó thành một lỗi nghiệp vụ luôn retry.

## 18. Transaction: handler không đảo ngược commit

Giả sử service transactional được gọi qua proxy:

~~~java
@Service
public class InvoiceJobService {
    private final InvoiceRepository repository;

    public InvoiceJobService(InvoiceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void processBatch() {
        repository.processPendingInvoices();
    }
}
~~~

Đây là **Spring service fragment**, cần import `Service`, `Transactional` và repository ứng dụng. Scheduled bean khác:

~~~java
@Component
public class InvoiceSchedule {
    private final InvoiceJobService jobService;

    public InvoiceSchedule(InvoiceJobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(fixedDelayString = "${jobs.invoice.delay:PT1M}")
    public void run() {
        jobService.processBatch();
    }
}
~~~

Đây là fragment cần import `Component` và `Scheduled`. Nếu `processBatch()` ném unchecked exception qua Spring transactional proxy, transaction manager thường rollback theo rule mặc định **trước khi exception đến ErrorHandler**. Handler suppress chỉ bảo vệ lịch tiếp tục, **không làm giao dịch vừa thất bại thành success**.

Ngược lại, nếu job đã commit một phần, gọi external API hoặc gửi email xong rồi mới ném exception, ErrorHandler không thể rollback những side effect đã hoàn tất. Tick kế có thể xử lý lại và tạo duplicate nếu không idempotent.

### Self-invocation và transaction boundary

Nếu method `@Transactional` được gọi bằng `this.processBatch()` trong cùng bean khi dùng proxy mode, transactional advice có thể bị bỏ qua. Đừng lấy việc ErrorHandler vẫn thấy exception làm bằng chứng transaction đã được mở/rollback. Phải test transaction manager, proxy boundary và trạng thái database thật.

### Lỗi khi transaction commit

Một method body có thể return bình thường nhưng commit transaction sau đó thất bại; exception từ transactional proxy có thể đi ra scheduler wrapper nếu ranh giới nằm trong lời gọi. Điều cần quan sát là **outcome commit** và idempotency, không chỉ log exception. Spring transaction synchronization và error handling ở phase AFTER_COMMIT còn có semantics riêng; scheduled handler không phải giải pháp tổng quát cho mọi lỗi của transaction event.

## 19. Backpressure, overload và ErrorHandler

Nếu một job mất 3 phút nhưng `@Scheduled(fixedRate = 1000)` yêu cầu mỗi giây, một handler suppress không hề giải quyết việc scheduler/DB bị quá tải. Cần nhìn:

- `ThreadPoolTaskScheduler` có số worker nhất định; cùng một periodic series của JDK không tự overlap, nhưng **nhiều jobs/registrations khác nhau** vẫn tranh resource.
- Nếu scheduled method tạo `@Async` jobs hoặc reactive subscriptions rồi return nhanh, lượng work **in-flight** có thể tăng ngoài ý định.
- `ErrorHandler` gọi ở lúc task fail, sau khi nhiều side effects đã xảy ra; nó không phải admission controller.
- `TaskRejectedException` là đường lỗi riêng khi không nhận thêm việc.
- Một handler block dài để gửi alert/network có thể **giữ thread scheduler**, làm mọi lịch khác bị trễ.

Gợi ý: đặt queue/capacity phù hợp, hạn chế concurrency, timeout I/O, circuit breaker và bulkhead; nếu quá tải, hãy **throttle ngay trước thực thi**. Handler nên ghi metric nhanh và giao việc alert sang hệ thống quan sát có reliability riêng.

## 20. Một ErrorHandler chung có thể thiếu job identity

Khi cấu hình:

~~~java
scheduler.setErrorHandler(error ->
        log.error("Scheduled task failed", error));
~~~

handler chỉ có `Throwable`. Nếu nhiều jobs dùng chung scheduler, log này có thể thiếu **job nào lỗi**, tenant nào, request correlation nào. Có những giải pháp:

1. **Đặt scheduler riêng** cho nhóm jobs có chính sách lỗi khác nhau (nhưng tránh tạo quá nhiều pools).
2. **Wrapper ở mức job** biết `jobName`/`jobId`, ghi metric và ném lại hoặc xử lý rõ.
3. **MDC/tracing context** được set và dọn trong `try/finally` quanh mỗi job; không dựa vào thread name cho định danh nghiệp vụ.
4. **Job store** có run ID, attempt, startedAt, finishedAt, outcome, lastError.
5. **Metric tags có giới hạn** như jobType/outcome; không dùng job ID duy nhất làm label cao cardinality.

Ví dụ wrapper ở mức job:

~~~java
Runnable observed = () -> {
    String jobName = "cleanup";
    try {
        cleanupService.removeExpired();
    } catch (RuntimeException error) {
        metrics.incrementFailure(jobName);
        log.error("Job {} failed", jobName, error);
        throw error; // cho scheduler handler quyết định suppress/propagate
    }
};
~~~

Đây là fragment ứng dụng có `cleanupService`, `metrics` và `log`. Nếu đã log trong wrapper và scheduler handler cũng log, một lỗi có thể xuất hiện **hai lần**; thiết kế logging policy nhất quán để tránh noise.

### Hai tầng logging không tương đương hai lần thất bại

Đếm một job failure theo **một lần thực thi/job run ID**, không đếm số log records. Một lần exception có thể đi qua service, wrapper, ErrorHandler và alerting. Đối với job cần audit, database outcome/correlation ID nên là nguồn thật, còn logs là bằng chứng chẩn đoán.

## 21. Monitoring và cảnh báo

Đối với scheduled workloads, nên đo ít nhất:

| Metric | Ý nghĩa |
| --- | --- |
| `scheduled_invocations_total` | Tổng số lượt body thật sự bắt đầu |
| `scheduled_invocations_success_total` | Tổng lượt hoàn thành đúng hợp đồng nghiệp vụ |
| `scheduled_invocations_failure_total` | Tổng lượt lỗi, kể cả đã được catch trong job nếu cần |
| `scheduled_error_handler_calls_total` | Số lần handler được gọi vì Throwable thoát body |
| `scheduled_last_success_timestamp` | Lần cuối có một lần chạy thành công |
| `scheduled_last_failure_timestamp` | Lần cuối handler/service ghi nhận failure |
| `scheduled_execution_duration` | Duration của body, đo trước khi handler hoặc cùng với outcome |
| `scheduled_start_lag` | Chậm giữa planned và actual execution |
| `scheduled_active_registrations` | Số schedule vẫn còn được đăng ký |
| `scheduled_rejected_total` | Registration/dispatch không được nhận |
| `scheduled_in_flight` | Work async/reactive còn đang chạy |
| `scheduled_persistent_backlog` | Số job items chưa hoàn tất trong store bền vững |
| `scheduled_retry_attempts_total` | Số lần retry bên trong job, khác số scheduled ticks |

Một lịch có thể vẫn tick đều đặn và handler suppress mọi exception, khiến **scheduler alive** nhưng **business service thực tế không làm được việc gì**. Vì thế cảnh báo theo `last_success_timestamp`, failure ratio, backlog age và SLA nghiệp vụ quan trọng hơn chỉ kiểm tra thread scheduler sống.

### Log ở đâu?

Ghi stack trace, job type, run ID, instance ID, scheduled/actual start time và attempt khi có dữ liệu. Không log secrets, tokens hoặc payload nhạy cảm. ErrorHandler chỉ có Throwable nên thông tin khác phải được gắn ở tầng job hoặc MDC có scope đúng. Khi dùng thread pool, nhớ dọn MDC trong `finally` để không rò dữ liệu sang job khác.

### Khi nào alert?

- Số lỗi consecutive vượt ngưỡng.
- Không có success trong khoảng SLA.
- Backlog tăng liên tục hoặc oldest pending quá hạn.
- Có abnormal future termination, job không còn active registration.
- Executor rejection, shutdown ngoài lịch, saturation.
- Một lỗi đặc biệt làm policy fail-fast dừng registration.
- Nhiều replicas cùng chạy job không idempotent.

Không alert chỉ vì có một WARN/ERROR log đơn lẻ trong job best-effort nếu SLA vẫn đạt, nhưng phải có cơ chế tổng hợp không che outage kéo dài.

## 22. Lỗi do ErrorHandler tự gây ra

Một anti-pattern:

~~~java
scheduler.setErrorHandler(error -> {
    databaseAuditService.saveFailure(error);
    remotePager.sendAlert(error);
    throw new IllegalStateException("Stop", error);
});
~~~

Đây là **mẫu rủi ro**, không phải cấu hình khuyến nghị. Các side effects bên trong handler có thể block lâu, fail tiếp, hoặc tranh database với task vừa lỗi. Một Throwable ban đầu có thể bị che bởi exception thứ cấp. Handler còn tự quyết định stop toàn scheduler series mà không có job ID hoặc quy tắc mức nghiêm trọng.

Cách tốt hơn là:

- ErrorHandler ghi metric/log **ngắn gọn và không bị block**.
- Nếu cần bền vững, service nghiệp vụ ghi failure state vào job store ở boundary rõ.
- Alerting/notifications dùng một kênh quan sát riêng có retry và idempotency.
- Nếu cần stop job, điều khiển registration/feature flag/circuit breaker **có thể quan sát và phục hồi**, thay vì chỉ throw rồi hy vọng operator tự biết.
- Không nuốt `VirtualMachineError` hoặc các trường hợp JVM/resource hỏng mà không có chính sách được đánh giá kỹ.

## 23. Chẩn đoán sự cố: task đã dừng, chưa chạy hay vẫn lỗi đều?

| Triệu chứng | Khả năng | Cách kiểm tra |
| --- | --- | --- |
| `@Scheduled` lần đầu lỗi nhưng vẫn tiếp tục tick | Spring repeating default log/suppress | Javadoc TaskUtils, logs, metrics sau tick |
| Java `scheduleAtFixedRate` thuần chạy đúng một lần rồi ngừng | Exception thoát khỏi periodic Runnable | `ScheduledFuture.get()`/`isDone()` và stack trace |
| One-shot Future báo success dù body throw | Custom suppress handler che failure | Test `get()` và handler config |
| Custom handler đột nhiên khiến cron dừng | Handler ném lỗi hoặc rescheduling path fail | Logger handler, chosen scheduler, active registration |
| Có lỗi trong `@Async void` nhưng scheduler handler không được gọi | Body nằm ở async executor | AsyncUncaughtExceptionHandler/log worker |
| Reactive `Mono.error` WARN mà scheduler handler không được gọi | onError là reactive signal | `doOnError`/subscriber logs |
| Job lỗi nhưng không có handler call | Code đã catch/swallow | Inspect service errors/metrics |
| Cron hoàn toàn không chạy | `@EnableScheduling` thiếu, cron disabled, bean không tồn tại hoặc config sai | Context registration, resolved cron |
| Lịch không chạy sau một lần `cancel` | Future đã cancel | Kiểm tra handle/cancellation status |
| Job mất sau deployment | Không có state bền vững | Job store/queue, startup/redeploy behavior |
| Scheduler startup lỗi | Cron/JNDI/bean creation/rejection | Startup exception, configuration |
| Handler log error lặp nhưng không có success | Lỗi thường trực, suppress che outage | Last-success alert, root cause |
| Job chạy quá nhiều instance | Mỗi context đăng ký riêng | Instance ID, lease/idempotency |
| Alert mất khi handler đang log | Monitoring backend lỗi/handler blocking | Telemetry durability và exception handler callback |
| Transaction bị rollback nhưng next tick vẫn chạy | Scheduler continuation độc lập với transaction outcome | Database state và policy retry |

## 24. Kiểm thử policy một cách có hệ thống

### Test A: Spring default repeating policy

1. Tạo Spring context có `@EnableScheduling`, một `ThreadPoolTaskScheduler` và một `@Scheduled` job.
2. Tick đầu ném một RuntimeException có message dễ nhận diện.
3. Tick thứ hai count down latch.
4. Đợi latch với timeout, assert tick thứ hai xuất hiện.
5. Capture Spring logs cho failure đầu; không kết luận chỉ từ việc không thấy exception ở caller.

### Test B: Spring custom suppress handler

1. Gắn `setErrorHandler(recorder::record)`.
2. Tick đầu throw, handler increment counter và return.
3. Tick sau chạy, assertion `calls >= 2`.
4. Kiểm tra handler gọi đúng một lần cho failure đầu, không dùng `Thread.sleep`.
5. Đóng context trong `finally` hoặc try-with-resources.

### Test C: Custom propagating handler

1. Dùng một context/scheduler riêng, tránh ảnh hưởng các jobs khác.
2. Handler ghi nhận rồi ném một unchecked exception.
3. Tùy loại registration, xác minh future state hoặc không có tick tiếp theo theo contract đã chọn.
4. Không chỉ chờ “vài trăm mili-giây im lặng” rồi khẳng định chắc chắn lịch dừng; kiểm tra `Future` và registration khi có thể.

### Test D: One-shot Future

1. Gọi `schedule(Runnable, Instant)` với body throw.
2. Dưới handler mặc định one-shot, `get(timeout)` ném `ExecutionException`.
3. Dưới custom suppress handler, kiểm tra behavior thay đổi; quyết định có phù hợp caller contract không.

### Test E: ErrorHandler không thấy reactive onError

1. Reactive `@Scheduled` trả `Mono.defer(...)`, lần đầu `Mono.error`, lần sau `Mono.empty`.
2. Xác minh subscription tiếp diễn sau onError và Spring WARN xuất hiện.
3. Xác minh custom scheduler `ErrorHandler` **không nhận tín hiệu onError**.
4. Tách StepVerifier unit test của publisher với Spring integration test của actual scheduled registration.

### Test F: @Async boundary

1. Dùng `@Scheduled` method dispatch sang `@Async` method ở bean khác.
2. Cho body worker throw exception.
3. Kiểm tra đúng exception handler/future completion của async executor.
4. Không áp giả định handler của scheduler sẽ có cùng lỗi.

### Test G: Rejection và shutdown

1. Tạo executor có capacity hữu hạn trong test.
2. Làm nó shutdown hoặc không nhận task, bắt lỗi ở caller `schedule/submit`.
3. Assert job state/HTTP result phù hợp thay vì chỉ dựa vào ErrorHandler.
4. Test hủy future và đóng Spring context để không leak threads.

## 25. Những lỗi phổ biến cần tránh

1. **Tưởng mọi exception trong lịch đều dừng `@Scheduled`.** Spring repeating default thường chặn lỗi.
2. **Tưởng mọi exception đều được Spring chặn.** One-shot default có thể propagate; custom handler thay chính sách.
3. **Nhầm Java `ScheduledThreadPoolExecutor` thuần với Spring scheduler wrapper.** JDK periodic exception có thể chặn các lượt sau.
4. **Tưởng `ErrorHandler` là API chung của mọi `TaskScheduler`.** Setter chỉ có ở những implementation hỗ trợ.
5. **Set handler ở scheduler bean không được `@Scheduled` sử dụng.** Lỗi vẫn xuất hiện ở scheduler khác.
6. **Custom handler chỉ log rồi ngầm tuyên bố job đã thành công.** Tick tiếp tục không phải business acknowledgment.
7. **Đặt custom handler throw cho mọi task** khiến một failure tạm thời dừng các lịch quan trọng.
8. **Đặt custom handler suppress cho one-shot task cần Future exception**, làm caller mất tín hiệu lỗi.
9. **Dùng Thread.UncaughtExceptionHandler làm kênh duy nhất** để phát hiện lỗi bên trong FutureTask.
10. **Ghi metric và remote alert blocking trong handler**, chiếm worker scheduler.
11. **Để handler tự ném RuntimeException vì metric service bị lỗi**, vô tình thay đổi error policy.
12. **Nuốt mọi `Error` nghiêm trọng** mà không có chính sách fail-safe của JVM.
13. **Dùng `ErrorHandler` để retry nghiệp vụ nhưng không có job ID**, dễ thao tác nhầm/duplicate.
14. **Retry vô hạn ngay trong scheduler worker**, làm chặn nhịp kế và gây storm.
15. **Cho rằng tick mới luôn xử lý lại item lỗi trước** dù không có durable pending state.
16. **Quên idempotency sau partial commit/external API**, gây side effect lặp.
17. **Dùng `@Async void` rồi tìm exception ở TaskScheduler ErrorHandler**, sai thread/handler boundary.
18. **Dùng Reactor `Mono.error` rồi chờ synchronous ErrorHandler nhận**, sai reactive contract.
19. **Ghi `doOnError` rồi nghĩ lỗi đã recover**, operator này chỉ quan sát.
20. **Dùng `onErrorResume(Mono.empty())` cho tất cả lỗi** khiến log WARN biến mất và che failure.
21. **Đếm logs như số lần job failed**, trong khi cùng exception có thể được log nhiều tầng.
22. **Không đo last-success**, để scheduler liên tục fail nhưng dashboard vẫn “alive”.
23. **Quên xử lý rejection lúc schedule/submit**, task còn chưa chạy để ErrorHandler thấy.
24. **Coi `isDone()==true` là thành công** của one-shot Future, không gọi `get`/xem exception.
25. **Gọi `get()` lên periodic Future đang hoạt động rồi block vô hạn**; Future chỉ terminal khi cancel/error.
26. **Thấy periodic Future done và kết luận toàn executor chết**, trong khi task khác vẫn chạy.
27. **Bỏ qua cron/trigger null, disabled hoặc cancellation**, chẩn đoán nhầm là handler “nuốt job”.
28. **Quên `@EnableScheduling` hoặc Spring-managed bean**, handler cấu hình đúng nhưng không có registration.
29. **Dùng self-invocation cho `@Transactional`** rồi tưởng rollback chắc chắn vì ErrorHandler đã log.
30. **Không kiểm thử behavior trên đúng scheduler implementation** khi dùng managed executor/Jakarta EE.
31. **Dựa vào Thread.sleep để assert tick thứ hai**, dẫn đến test flaky dưới CI load.
32. **Không shutdown executor local trong test**, gây thread leak/hang.
33. **Dùng một scheduler bean cho mọi nhóm jobs với chính sách lỗi trái nhau**, gây rối hành vi.
34. **Đặt status DONE trước khi side effect thành công**, tick tiếp không biết cần retry.
35. **Không có alert khi propagation dừng schedule**, mất job lặng lẽ đến lần restart.
36. **Không log job run ID/correlation**, khó điều tra khi nhiều jobs dùng cùng pool.

## 26. Bảng quyết định nhanh

| Câu hỏi | Nếu câu trả lời là... | Nên làm |
| --- | --- | --- |
| Task có chạy lại nhiều lần không? | Có | Mặc định Spring thường suppress/log; kiểm tra SLA và monitoring |
| Task chỉ chạy một lần và caller cần kết quả? | Có | Bảo toàn lỗi trong Future, tránh suppress vô điều kiện |
| Muốn báo lỗi theo job ID? | Có | Bọc job/metrics riêng, không chỉ dựa vào Throwable ở handler |
| Lỗi là transient 503? | Có | Retry hữu hạn/backoff, idempotency, scheduler tiếp tục có điều tiết |
| Lỗi là sai cấu hình nghiêm trọng? | Có | Fail-fast/disable có alert rõ |
| Lỗi đến từ reactive publisher? | Có | `doOnError`/`onErrorResume`/`retryWhen`, không dựa vào synchronous ErrorHandler |
| Lỗi đến từ `@Async` worker? | Có | Async handler/Future completion, không chỉ scheduler handler |
| Lỗi xảy ra trước khi task chạy? | Có | Xử lý reject/startup/registration tại caller/config layer |
| Có nhiều replica? | Có | Job state bền vững, distributed lease/idempotency |
| Job không được mất qua restart? | Có | Outbox/queue/job store, không chỉ `@Scheduled` |
| Job lỗi nhưng tick vẫn đều? | Có | Theo dõi last success và backlog, không chỉ scheduler heartbeat |

## 27. Câu hỏi tự kiểm tra

1. Giao diện ErrorHandler của Spring ở package nào và nhận kiểu đối số gì?
2. `ErrorHandler.handleError(Throwable)` trả giá trị gì?
3. `TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER` khác `LOG_AND_PROPAGATE_ERROR_HANDLER` thế nào?
4. Vì sao Spring chọn suppress theo mặc định cho repeating task, còn propagate cho one-shot?
5. Với Java `ScheduledThreadPoolExecutor.scheduleAtFixedRate` thuần, exception thoát khỏi một lượt gây hậu quả gì?
6. Vì sao scheduler JDK vẫn có thể chạy task khác khi một periodic series đã failed?
7. `ThreadPoolTaskScheduler.setErrorHandler` dùng ở đâu trong Spring configuration?
8. Nếu `@Scheduled` thực tế dùng một scheduler khác bean bạn cấu hình, handler của bạn có thấy lỗi không?
9. Handler trả bình thường có bảo đảm transaction hoặc job nghiệp vụ đã thành công không?
10. Điều gì có thể xảy ra nếu custom handler ném lại RuntimeException?
11. Tại sao không nên suppress một one-shot task khi caller cần `ScheduledFuture.get()` nhận exception?
12. Nếu async worker lỗi sau khi scheduled method đã return, ErrorHandler của scheduler có tự nhìn thấy không?
13. Vì sao Reactor `Mono.error` phát `onError` không qua synchronous ErrorHandler trong Spring 6.1+?
14. `doOnError` khác `onErrorResume` ở điểm nào về terminal signal?
15. Nếu một tick thất bại nhưng các tick sau chạy tiếp, điều gì cần để đảm bảo item lỗi được retry?
16. Vì sao cần idempotency khi job có side effect ngoài transaction?
17. Cần metric gì để phát hiện scheduler vẫn tick nhưng đã không thành công trong một giờ?
18. Vì sao `Thread.UncaughtExceptionHandler` không phải nguồn duy nhất để quan sát lỗi trong Future?
19. Một ErrorHandler ghi log vào remote service rồi bị block có thể tác động scheduler thế nào?
20. Làm sao test tick #2 đã thật sự chạy mà không dùng `Thread.sleep`?
21. Làm sao phân biệt registration bị cancel, Trigger trả `null` và periodic future failed?
22. Nếu hệ thống dùng Jakarta EE managed scheduler thay vì Spring local scheduler, cần kiểm chứng gì trước khi áp giả định về lỗi?

## 28. Tài liệu tham khảo chính thức

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.2 — TaskUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/scheduling/support/TaskUtils.html)
- [Spring Framework 6.2 — ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.2/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework — ErrorHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/util/ErrorHandler.html)
- [Spring Framework — DelegatingErrorHandlingRunnable Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/DelegatingErrorHandlingRunnable.html)
- [Spring Framework — @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework — TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Framework — AsyncUncaughtExceptionHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/interceptor/AsyncUncaughtExceptionHandler.html)
- [Java SE 17 — ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)
- [Java SE 17 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html)
- [Java SE 17 — ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html)
- [Reactive Streams Specification](https://www.reactive-streams.org/)

**Ghi nhớ:** `ErrorHandler` là một **biên giới xử lý Throwable** của scheduler truyền thống, không phải cơ chế retry/rollback mọi lỗi. Spring `TaskUtils` mặc định **ghi log và suppress** exception của repeating task để các tick tiếp theo không tự bị chặn, còn **ghi log và propagate** với one-shot task để Future phản ánh failure. Một handler tùy chỉnh có thể đảo chính sách này, nên phải kiểm chứng trên scheduler thực sự được dùng. Với `@Scheduled` reactive, tín hiệu `onError` đi qua subscriber và được Spring log WARN chứ không qua ErrorHandler đồng bộ. Dù lịch còn chạy, muốn hệ thống đáng tin vẫn cần job state, metrics, retry có giới hạn, idempotency và đường xử lý overload.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduling-failures-003">Phân biệt chính sách ghi nhận rồi tiếp tục với cho lỗi lan ra</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt chính sách ghi nhận rồi tiếp tục với cho lỗi lan ra khi lập lịch trong Spring

## 1. Tại sao lỗi của một scheduled job cần chính sách riêng?

Một tác vụ định kỳ trong Spring có thể lỗi do database tạm thời không phản hồi, API bên ngoài trả 503, file chưa có, deadlock, dữ liệu không hợp lệ hoặc bug trong code. Nếu tác vụ xảy ra **mỗi phút**, câu hỏi quan trọng không chỉ là *“lỗi được ghi ở đâu?”* mà còn là:

- Lượt **hiện tại** được coi là thất bại như thế nào?
- Lượt **tiếp theo** có còn được chạy không?
- Người vận hành có biết job đã lỗi, hay chỉ thấy log?
- Một **ScheduledFuture** của registration đó đang còn hoạt động, kết thúc bất thường hay đã bị hủy?
- Lỗi có thể được retry an toàn không, nhất là khi job có side effect?

Hai chiến lược dễ nhầm:

1. **Ghi nhận rồi tiếp tục (log/record and suppress):** handler ghi lại exception, **không ném lại**, cho phép vòng lập lịch tiếp tục các lượt sau.
2. **Ghi nhận rồi cho lỗi lan ra (log/record and propagate):** handler ghi lại exception, sau đó **ném lại exception** để lỗi thoát ra khỏi scheduled `Runnable`. Với periodic task trên `ScheduledThreadPoolExecutor`, kết quả thông thường là registration bị kết thúc bất thường, các lượt kế tiếp của chính registration đó không được chạy.

**Đây không chỉ là hai mức logging khác nhau.** Khác biệt chủ đạo là **sau khi quan sát lỗi, exception có thoát ra khỏi `Runnable.run()` hay không**. Nó quyết định semantics của scheduled executor, cách `Future` hoàn thành và việc tiếp tục hay dừng các lượt sau.

Bài này tập trung vào lỗi của **scheduled Runnable imperative** trong **Spring Framework 6.2.1**, nhất là `ThreadPoolTaskScheduler`, `@Scheduled`, `TaskUtils` và `ErrorHandler`. Lỗi `onError` của reactive `@Scheduled` có đường xử lý khác và được nêu riêng ở cuối bài.

## 2. Đường đi của lỗi: task, wrapper, ErrorHandler và executor

Khi Spring dùng `ThreadPoolTaskScheduler` để chạy một `Runnable`, scheduler có thể dùng wrapper như `DelegatingErrorHandlingRunnable` để thực hiện:

```text
ScheduledThreadPoolExecutor
           |
           v
DelegatingErrorHandlingRunnable.run()
           |
           +-- originalRunnable.run()
                     |
                     +-- thành công ----------> run() trả bình thường
                     |
                     +-- throw Throwable
                                |
                                v
                       ErrorHandler.handleError(error)
                                |
               +----------------+-----------------+
               |                                  |
          ghi rồi return                       ghi rồi throw
               |                                  |
       wrapper run() return              wrapper run() thất bại
               |                                  |
       executor có thể tiếp tục            periodic registration
       các lần chạy sau                   có thể dừng sau lỗi
```

`ErrorHandler` là interface Spring:

```java
package org.springframework.util;

@FunctionalInterface
public interface ErrorHandler {
    void handleError(Throwable t);
}
```

Đây là chữ ký minh họa API, không phải một tệp cần tạo lại trong dự án. Handler nhận `Throwable`, nên code xử lý cần cân nhắc cả những lỗi nghiêm trọng ngoài `RuntimeException`. Một handler không nên tự ý bắt rồi nuốt các lỗi JVM nghiêm trọng theo kiểu khiến hệ thống trông vẫn khỏe mạnh.

**Điểm quyết định:** nếu handler **return bình thường**, wrapper được xem như đã thực thi xong dù business method ném lỗi; nếu handler **ném lại**, exception được đưa đến scheduled executor ở boundary của task. Đó là lý do cùng một lỗi `IllegalStateException` có thể tạo hai kết quả Future rất khác nhau.

### 2.1. Không nhầm nơi ném lỗi

- Exception ném **trong thân task** là lỗi nghiệp vụ hoặc lỗi runtime khi thực thi.
- Exception ném **ở thời điểm submit/schedule** (ví dụ executor đã shutdown và từ chối registration) là lỗi tiếp nhận công việc, cần xử lý ở caller; task chưa nhất thiết được chạy.
- Exception phát sinh trong **subscriber reactive** là tín hiệu `onError`, không tự động ném thoát `Runnable.run()`.
- Exception trong một **Future/CompletableFuture** có thể đã bị giữ trong completion state, không thoát khỏi wrapper Runnable bên ngoài.

Hãy xác định *error boundary* trước khi chọn handler. Một ErrorHandler chỉ thấy những exception thực sự được chuyển đến contract của nó; nó không phải một “bộ thu gom mọi lỗi” trong JVM.

## 3. Mặc định của Spring: repeating khác one-shot

Spring có utility `org.springframework.scheduling.support.TaskUtils` với các chiến lược:

- `TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER`: ghi log lỗi nhưng **không ném lại**; các lượt periodic có thể tiếp tục.
- `TaskUtils.LOG_AND_PROPAGATE_ERROR_HANDLER`: ghi log lỗi rồi **ném lại**; các lượt periodic sau thường bị ngăn.
- `TaskUtils.getDefaultErrorHandler(boolean isRepeatingTask)`: trả handler mặc định tùy tính chất repeating.

Javadoc `TaskUtils` nêu quy tắc mặc định:

| Hình thức task | Khi không cung cấp custom ErrorHandler | Lý do |
| --- | --- | --- |
| **Repeating**, ví dụ `scheduleAtFixedRate(...)`, `scheduleWithFixedDelay(...)` | **LOG_AND_SUPPRESS** | Một lỗi nhất thời không tự ngăn những lượt định kỳ sau |
| **One-shot**, ví dụ `schedule(Runnable, Instant)` | **LOG_AND_PROPAGATE** | Lỗi có thể được quan sát qua `Future` của lần thực thi duy nhất |

Trong cả hai trường hợp mặc định, Spring **ghi log**. Mức log khi dùng Spring `TaskUtils.LoggingErrorHandler` là **ERROR** theo logger tương ứng; cách Java Logging bridge hiển thị có thể là `SEVERE`. Không diễn giải “suppress” là “không ghi nhận lỗi”.

**Cảnh báo phạm vi:** `TaskUtils` là public class phục vụ hạ tầng scheduler của Spring, Javadoc ghi rõ **dành cho nội bộ implementation**, không phải API ứng dụng nên phụ thuộc lâu dài vào mọi chi tiết. Hiểu hai constants này rất có ích để phân tích hành vi; khi thiết kế chính sách production, ưu tiên cấu hình rõ `ThreadPoolTaskScheduler.setErrorHandler(...)` và kiểm thử phiên bản Spring đang dùng.

### 3.1. Tại sao one-shot lại propagate mặc định?

Một one-shot job có một `ScheduledFuture<?>` đại diện cho một lần chạy. Nếu task ném lỗi và exception propagate ra task boundary, caller có thể gọi `future.get()` để nhận `ExecutionException` với nguyên nhân gốc.

Nếu handler chỉ ghi log rồi **nuốt** exception của one-shot, wrapper trả về bình thường. Lúc đó `future.get()` có thể trả `null` như thể nhiệm vụ hoàn tất thành công, dù nghiệp vụ thật sự đã lỗi. Đây là một pitfall lớn nếu caller dùng Future để quyết định commit bước tiếp theo.

### 3.2. Tại sao repeating suppress mặc định?

Với một periodic job kiểm tra heartbeat mỗi phút, một lỗi tạm thời không nhất thiết là lý do ngừng hẳn việc kiểm tra các phút kế tiếp. Suppress tại scheduler boundary giữ registration còn hoạt động. Nhưng **bản thân lượt thất bại không được tự thực hiện lại ngay**: việc chạy tiếp thuộc **lượt lịch kế tiếp**, không phải retry của lần đang lỗi.

Đừng hiểu việc “tiếp tục lịch” là bảo đảm việc đã lỗi sẽ cuối cùng thành công. Cần cơ chế retry, audit, backfill và trạng thái nghiệp vụ nếu công việc quan trọng.

## 4. Chính sách ghi nhận rồi tiếp tục: bản chất và chi phí

Với một `ErrorHandler` trả về bình thường:

```java
scheduler.setErrorHandler(error -> {
    logger.error("Scheduled job failed; continuing schedule", error);
    metrics.incrementScheduledFailure();
    // Không throw: ErrorHandler return bình thường.
});
```

Đây là fragment cấu hình; `logger` và `metrics` là dependency giả định. Nó minh họa **record/log and suppress**. Dưới lớp `ScheduledThreadPoolExecutor`, Runnable wrapper được xem là đã hoàn tất bình thường và lượt định kỳ sau vẫn được phép chạy.

**Ưu điểm:** scheduler không tự dừng chỉ vì một lỗi thoáng qua; thích hợp cho polling, cache refresh, metrics scrape và tác vụ tự phục hồi ở lượt sau.

**Rủi ro:** hệ thống có thể lỗi mỗi lần nhưng vẫn liên tục chạy, tạo log flood và retry storm; caller không có dấu hiệu completion failure nếu chỉ nhìn một Future được bao bởi handler suppress; một tác vụ quan trọng có thể bị bỏ sót mà không có cơ chế đối soát.

Để dùng an toàn, cần:

- Ghi **counter**, **last failure time**, **consecutive failure count**, thời lượng và kiểu lỗi; không chỉ ghi một dòng log.
- Có alert khi lỗi liên tục vượt ngưỡng, dù scheduler vẫn tiếp tục.
- Xác định rõ một lượt thất bại có cần **backfill** hoặc enqueue bù công việc không.
- Có giới hạn tốc độ, timeout I/O và cơ chế chống duplicate side effects.
- Đối với lỗi cấu hình không thể tự hồi phục, cân nhắc stop/disable job thay vì tiếp tục mãi.

### 4.1. Nuốt lỗi ở ErrorHandler không rollback side effect

Một scheduled task có thể đã gửi email thành công rồi ném exception khi ghi trạng thái cuối. Handler suppress cho lịch tiếp tục nhưng không hoàn nguyên email đã gửi. Nếu lần sau lặp lại, email có thể gửi hai lần. Việc suppress exception không tạo một transaction ACID và không bảo đảm idempotency.

### 4.2. Log-and-continue khác retry

Giả sử lịch chạy mỗi 60 giây, lượt 10:00 thất bại. Nếu handler suppress, lượt 10:01 có cơ hội được gọi. Điều đó **không tự retry** sự kiện thất bại ở 10:00.

Muốn retry **ngay trong cùng lượt**, task phải chứa policy riêng: retry limit, backoff, phân loại lỗi, timeout budget. Nếu yêu cầu “mỗi bản ghi phải xử lý ít nhất một lần”, cần job store/outbox/broker hoặc durable task state; một cron cứ chạy tiếp không đủ.

## 5. Chính sách ghi nhận rồi propagate: bản chất và hậu quả

Một handler rethrow:

```java
scheduler.setErrorHandler(error -> {
    logger.error("Scheduled job failed; propagating", error);
    if (error instanceof RuntimeException runtime) {
        throw runtime;
    }
    if (error instanceof Error fatal) {
        throw fatal;
    }
    throw new IllegalStateException("Scheduled task failed", error);
});
```

Đây là fragment, `logger` cần có thật. Việc ném lại ở đây cố ý **cho lỗi lan ra khỏi ErrorHandler**, phù hợp một policy fail-fast. Cần cẩn trọng: chuyển một checked `Throwable` thành wrapper có thể làm thay đổi exception type mà caller quan sát; không tự cho rằng `Future.get()` luôn giữ đúng class lỗi gốc nếu ứng dụng wrap.

Spring cũng có built-in `TaskUtils.LOG_AND_PROPAGATE_ERROR_HANDLER` để mô tả chiến lược này, nhưng vì `TaskUtils` dành cho hạ tầng, các ứng dụng production nên cân nhắc custom handler nếu cần metric, policy và logging nhất quán.

Khi exception thoát khỏi một **periodic task** của JDK `ScheduledThreadPoolExecutor`:

- Những lần chạy kế tiếp của **cùng periodic registration** bị ngăn theo hợp đồng JDK.
- `ScheduledFuture.isDone()` sẽ là `true` sau khi kết thúc bất thường.
- `ScheduledFuture.get()` có thể ném `ExecutionException` chứa lỗi.
- `isCancelled()` có thể vẫn là `false`: **failed** khác **cancelled**.
- Scheduler pool vẫn có thể chạy **các jobs khác**; propagate không đồng nghĩa ứng dụng/Spring context tự dừng hoặc JVM bị kill.

**Chốt:** đây là “dừng chuỗi periodic execution đó khi có lỗi”, không phải “ném exception về thread đã gọi `scheduleAtFixedRate`”. Caller submit đã trả từ lâu; error xảy ra trên worker, còn Future lưu completion state.

### 5.1. Fail-fast hữu ích khi nào?

Nên xem xét propagate khi việc tiếp tục sẽ nguy hiểm:

- Sai schema/cấu hình khiến mọi lượt tiếp theo chắc chắn ghi hỏng dữ liệu.
- Vi phạm invariant nghiệp vụ, cần con người can thiệp.
- Một batch có nguy cơ tạo side effects trùng nếu chạy lại tự động.
- Task không có state management để tiếp tục an toàn, cần dừng đến khi được khắc phục.
- Policy muốn tắt registration và alert ngay khi lỗi để tránh làm tình trạng xấu hơn.

Nhưng propagate **không tự làm alert, rollback, khôi phục registration hoặc sửa dữ liệu**. Sau khi task dừng, cần có nơi giám sát rằng lịch đã ngừng, ai quyết định bật lại, và cách catch up các mốc đã lỡ.

### 5.2. Một lỗi tạm thời có đáng ngừng lịch vĩnh viễn?

Nếu downstream 503 trong 200ms, việc dừng hẳn job kiểm tra mỗi phút có thể là lựa chọn quá mạnh. Nếu vẫn muốn fail-fast nhưng không dừng dài hạn, có thể thiết kế một **supervisor** quản lý trạng thái rồi đăng ký lại có kiểm soát. Không tự động reschedule vô hạn ngay sau mỗi lỗi vì có thể tạo tight retry loop.

## 6. So sánh chính sách theo hợp đồng thực tế

| Khía cạnh | Record + suppress | Record + propagate |
| --- | --- | --- |
| ErrorHandler có ghi lỗi? | Có, nếu handler được thiết kế để ghi | Có, nếu handler được thiết kế để ghi |
| ErrorHandler ném lại exception? | **Không** | **Có** |
| Periodic task trên ScheduledThreadPoolExecutor | Có thể chạy lượt sau | Thường dừng registration sau lỗi |
| Task hiện tại đã thất bại nghiệp vụ? | Vẫn thất bại; chỉ wrapper trả bình thường | Thất bại và được propagate ở task boundary |
| `ScheduledFuture` của periodic registration sau lượt lỗi | Thường vẫn pending cho đến cancel/abnormal event khác | Có thể completed exceptionally |
| One-shot `future.get()` sau lỗi | Có thể trả bình thường, **che lỗi** | Ném `ExecutionException` |
| Rủi ro chính | Silent failure, log flood, thiếu backfill | Tắt lịch mà không ai biết, missed work |
| Use case phù hợp | Polling có thể bỏ qua lượt lẻ, transient failures | Critical invariant, lỗi không thể tiếp tục an toàn |
| Cần monitoring? | **Có** | **Có** |
| Có rollback/transaction tự động? | Không | Không |
| Có retry ngay trong cùng lượt? | Không | Không |

**Đừng chọn chỉ vì “cần log hay không”.** Cả hai đều có thể ghi log. Điều quyết định là lỗi có phải là **điều kiện cho phép job còn chạy lượt sau** hay không, cùng với cách xử lý failure outcome.

## 7. Cách cấu hình Spring cho scheduled methods

Khi dùng `@Scheduled`, Spring cần một scheduler phù hợp. Một cấu hình Java thường gặp:

```java
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulerConfig {
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("business-scheduler-");

        scheduler.setErrorHandler(error -> {
            // Ghi metric/log phù hợp ứng dụng.
            // Không throw: các recurring schedules được phép tiếp tục.
        });

        return scheduler;
    }
}
```

Đây là **fragment**; cần imports `Configuration`, `EnableScheduling`, `Bean`, `ThreadPoolTaskScheduler` và các bean nghiệp vụ khác.

Một job:

```java
@Component
public class StatusPollingJob {
    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void pollStatus() {
        service.poll();
    }
}
```

Fragment này cần bean `service` và imports `Component`, `Scheduled`, `TimeUnit`. Nếu `service.poll()` ném exception qua scheduled Runnable, ErrorHandler của scheduler phù hợp có thể xử lý.

### 7.1. Một handler cho tất cả tasks trên scheduler là phạm vi rộng

Khi đặt `scheduler.setErrorHandler(...)`, chính sách áp dụng cho các tasks đi qua scheduler đó; bạn **không truyền tên method/công việc** như một tham số riêng trong `handleError(Throwable)`. Đối với các nhóm jobs có policy khác nhau:

- Tách scheduler bean theo loại công việc/SLO, rồi dùng `@Scheduled(scheduler="beanName")` trong Spring Framework 6.1+ khi phù hợp.
- Hoặc đặt `try/catch` có phân loại lỗi tại business/job boundary, gắn job ID trong metric.
- Dùng wrapper có context/metadata an toàn để nhận biết task.
- Không chỉ phân loại theo chuỗi exception message; message dễ đổi, có thể chứa dữ liệu nhạy cảm.

**Phân biệt task registration với schedule bean:** tùy vào cấu hình và context, `@EnableScheduling` phải resolve đúng scheduler. Một handler đặt trên bean không thực sự được dùng sẽ không có tác dụng. Kiểm tra actual scheduler, tên thread và integration test.

### 7.2. Một lỗi không nên giết toàn pool

Một periodic task propagate lỗi thường dừng **registration đó**, nhưng các scheduler threads và jobs khác có thể tiếp tục. Nếu mục tiêu là “tạm dừng mọi schedules”, cần một cơ chế vận hành/lifecycle tách biệt, ví dụ feature flag, pause/resume scheduler khi implementation hỗ trợ hoặc dừng ApplicationContext có chủ đích; không dùng exception từ một job làm cơ chế điều phối toàn hệ.

## 8. Dự án Maven chạy được: kiểm chứng năm tình huống

Ví dụ độc lập dùng **Java 21 + Spring Framework 6.2.1 + Maven**, không cần Spring Boot. Nó tạo một `ThreadPoolTaskScheduler` tách biệt cho mỗi kịch bản và kiểm tra:

1. **Periodic mặc định**: hai lượt đầu ném lỗi, nhưng scheduler tiếp tục đến ít nhất lượt thứ ba.
2. **Custom record-and-suppress**: handler đếm lỗi và return bình thường, periodic task vẫn được chạy tiếp.
3. **Custom propagate**: handler ném lại; periodic task fail ngay lượt đầu, Future completed exceptionally và không bị cancel.
4. **One-shot mặc định**: lỗi đi qua Future và `get()` ném `ExecutionException`.
5. **One-shot dùng custom suppress**: lỗi đã xảy ra nhưng `get()` trả bình thường, cho thấy rủi ro che lỗi.

Để test ổn định, dùng `CountDownLatch` với timeout xác định và assertions; không cần `Thread.sleep()` để suy đoán scheduler đã chạy bao nhiêu lần.

### 8.1. Cấu trúc project

```text
scheduled-error-policy-demo/
├── pom.xml
└── src/main/java/demo/ScheduledErrorPolicyDemo.java
```

### 8.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>scheduled-error-policy-demo</artifactId>
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
          <mainClass>demo.ScheduledErrorPolicyDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 8.3. Tệp src/main/java/demo/ScheduledErrorPolicyDemo.java

```java
package demo;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.util.ErrorHandler;

public class ScheduledErrorPolicyDemo {
    private static ThreadPoolTaskScheduler create(
            String prefix, ErrorHandler handler) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        if (handler != null) {
            scheduler.setErrorHandler(handler);
        }
        scheduler.initialize();
        return scheduler;
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new AssertionError(problem);
        }
    }

    private static boolean defaultRepeatingContinues() throws Exception {
        ThreadPoolTaskScheduler scheduler = create("default-", null);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch seenThree = new CountDownLatch(3);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            int n = attempts.incrementAndGet();
            seenThree.countDown();
            if (n <= 2) {
                throw new IllegalStateException("repeating-default-" + n);
            }
        }, Duration.ofMillis(60));

        try {
            require(seenThree.await(5, TimeUnit.SECONDS),
                    "Default repeating task not executed 3 times");
            return attempts.get() >= 3 && !future.isDone();
        } finally {
            future.cancel(false);
            scheduler.shutdown();
        }
    }

    private static boolean customSuppressContinues() throws Exception {
        AtomicInteger observedErrors = new AtomicInteger();
        ThreadPoolTaskScheduler scheduler = create(
                "suppress-", error -> observedErrors.incrementAndGet());
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch seenThree = new CountDownLatch(3);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            attempts.incrementAndGet();
            seenThree.countDown();
            throw new IllegalStateException("record-only");
        }, Duration.ofMillis(60));

        try {
            require(seenThree.await(5, TimeUnit.SECONDS),
                    "Custom suppress task not executed 3 times");
            return attempts.get() >= 3
                    && observedErrors.get() >= 3
                    && !future.isDone();
        } finally {
            future.cancel(false);
            scheduler.shutdown();
        }
    }

    private static boolean customPropagateStops() throws Exception {
        ThreadPoolTaskScheduler scheduler = create(
                "propagate-", TaskUtils.LOG_AND_PROPAGATE_ERROR_HANDLER);
        AtomicInteger attempts = new AtomicInteger();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("stop-periodic");
        }, Duration.ofMillis(60));

        try {
            boolean executionFailed = false;
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                executionFailed = ex.getCause() instanceof IllegalStateException;
            }
            return executionFailed && future.isDone()
                    && attempts.get() == 1 && !future.isCancelled();
        } finally {
            future.cancel(false);
            scheduler.shutdown();
        }
    }

    private static boolean defaultOneShotPropagates() throws Exception {
        ThreadPoolTaskScheduler scheduler = create("one-shot-", null);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            throw new IllegalStateException("one-shot-default");
        }, Instant.now());

        try {
            try {
                future.get(5, TimeUnit.SECONDS);
                return false;
            } catch (ExecutionException ex) {
                return ex.getCause() instanceof IllegalStateException
                        && future.isDone();
            }
        } finally {
            scheduler.shutdown();
        }
    }

    private static boolean customSuppressHidesOneShotFailure() throws Exception {
        AtomicInteger observedErrors = new AtomicInteger();
        ThreadPoolTaskScheduler scheduler = create(
                "one-shot-suppress-", error -> observedErrors.incrementAndGet());
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            throw new IllegalStateException("hidden-one-shot");
        }, Instant.now());

        try {
            Object result = future.get(5, TimeUnit.SECONDS);
            return result == null && future.isDone()
                    && observedErrors.get() == 1;
        } finally {
            scheduler.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        boolean repeatDefault = defaultRepeatingContinues();
        boolean repeatSuppress = customSuppressContinues();
        boolean repeatPropagate = customPropagateStops();
        boolean oneShotDefault = defaultOneShotPropagates();
        boolean oneShotSuppress = customSuppressHidesOneShotFailure();

        require(repeatDefault && repeatSuppress && repeatPropagate
                        && oneShotDefault && oneShotSuppress,
                "Scheduler policies did not match expected behavior");

        System.out.println("defaultRepeatingContinues=" + repeatDefault);
        System.out.println("customSuppressRepeatingContinues=" + repeatSuppress);
        System.out.println("customPropagateStopsRepeating=" + repeatPropagate);
        System.out.println("defaultOneShotPropagates=" + oneShotDefault);
        System.out.println("customSuppressOneShotMasksFailure=" + oneShotSuppress);
    }
}
```

### 8.4. Lệnh biên dịch và chạy

Cần Maven và JDK 21. Tại thư mục chứa `pom.xml`:

```bash
mvn -q -DskipTests compile exec:java
```

**Stdout của chương trình, đã chạy thực tế với Spring Framework 6.2.1:**

```text
defaultRepeatingContinues=true
customSuppressRepeatingContinues=true
customPropagateStopsRepeating=true
defaultOneShotPropagates=true
customSuppressOneShotMasksFailure=true
```

Ngoài những dòng stdout trên, Spring có thể phát log **ERROR/SEVERE kèm stack trace** cho hai lỗi periodic mặc định, một lỗi propagate và một lỗi one-shot mặc định. Đây là log do `TaskUtils` ghi, **không phải assertion thất bại**. Custom suppress handlers trong demo chỉ tăng counter, nên không tự ghi log; production phải thêm log/metric có chủ đích.

**Đừng copy các giá trị 60ms/5s thành cấu hình production**: đây là thời gian ngắn để chạy test nhanh, còn 5 giây chỉ là timeout bảo vệ quá trình kiểm thử. Dưới môi trường CI quá tải, tăng ngưỡng await hoặc dùng controllable clock/test scheduler, không coi một lệch thời gian ngẫu nhiên là thay đổi semantics của Spring.

## 9. Đọc đúng kết quả của năm phép kiểm thử

### 9.1. Periodic mặc định vẫn tiếp tục

`defaultRepeatingContinues=true` chứng minh scheduler cho **ít nhất ba lượt** chạy dù hai lượt đầu ném exception. Chính sách mặc định repeating đã log và suppress, vì vậy Future còn pending trong giai đoạn quan sát.

Không có nghĩa lỗi ở hai lượt đầu được sửa, hay có retry immediate. Lần thứ ba chỉ là **lượt periodic mới** có logic trả về bình thường.

### 9.2. Custom suppress đếm lỗi nhưng lịch không dừng

`customSuppressRepeatingContinues=true`: handler increment bộ đếm rồi return. Task thực tế ném exception **mọi lượt**; scheduler vẫn tiếp tục vì wrapper không cho lỗi thoát ra.

Đây là ví dụ tại sao metrics “job vẫn được trigger” **không chứng minh job thành công**. Cần một counter failure riêng.

### 9.3. Custom propagate làm periodic Future fail

`customPropagateStopsRepeating=true`: ngay lượt đầu, handler log rồi rethrow. `ScheduledFuture.get(5, SECONDS)` ném `ExecutionException`. `future.isDone()` true; `future.isCancelled()` false; attempt count = 1.

**Failed ≠ Cancelled.** Nếu hệ thống chỉ đếm cancellation sẽ bỏ qua các schedules đã tự dừng vì exception.

### 9.4. One-shot mặc định có thể báo lỗi qua Future

`defaultOneShotPropagates=true`: lời gọi `schedule(Runnable, Instant.now())` tạo một Future cho **một lượt**. Task ném `IllegalStateException` và Future hoàn thành bất thường; caller thấy `ExecutionException`.

Điều đó phù hợp với default handler riêng cho one-shot của Spring. Không dựa vào console logs để suy rằng Future đã fail; test kiểm tra `get()` thực sự.

### 9.5. Custom suppress một lần có thể che failure

`customSuppressOneShotMasksFailure=true`: custom handler ghi nhận lỗi bằng counter rồi **không throw**. Future nhận thấy wrapper chạy xong bình thường và `get()` trả `null` như một successful Runnable.

Lỗi nghiệp vụ vẫn xảy ra. Đây là ví dụ nguy hiểm khi áp chung một ErrorHandler suppress cho **cả repeating và one-shot tasks** trên một scheduler bean mà caller của one-shot lại dùng Future để quyết định thành công.

## 10. Vì sao Future của periodic task không giống Future của one-shot?

Một periodic `ScheduledFuture<?>` thường đại diện **cả chuỗi các executions**. Theo semantics của `ScheduledThreadPoolExecutor`:

- Khi chuỗi periodic còn hoạt động bình thường, `future.isDone()` thường là `false`.
- Gọi `future.get()` có thể **chờ mãi** cho tới khi chuỗi bị cancel hoặc kết thúc bất thường.
- Khi một exception propagate ra periodic Runnable, những execution tiếp theo của chuỗi bị suppressed, Future hoàn thành bất thường và `get()` báo `ExecutionException`.
- Khi caller gọi `future.cancel(false/true)`, Future chuyển trạng thái cancelled theo hợp đồng; `get()` thường ném `CancellationException`.
- `isDone()==true` chỉ nói Future đã hoàn tất, **không đồng nghĩa success**.

Với one-shot:

- `get()` có thể trả `null` nếu Runnable hoàn tất bình thường.
- Nếu exception được propagate, `get()` ném `ExecutionException` với cause.
- Nếu custom handler nuốt exception, Future có thể được đánh dấu thành công dù task body đã thất bại.

**Không sử dụng Future của periodic registration như Future của một lượt xử lý.** Nếu muốn biết outcome mỗi lượt, task phải ghi job-run status/metrics hoặc tạo riêng kết quả nghiệp vụ cho từng occurrence.

### 10.1. Không gọi get() vô hạn trong request thread

Đây là fragment nguy hiểm:

```java
ScheduledFuture<?> forever =
        scheduler.scheduleAtFixedRate(job, Duration.ofMinutes(1));
forever.get(); // Có thể block vô hạn khi lịch đang hoạt động bình thường.
```

Thay vào đó, quản lý lifecycle bằng handle để cancel khi cần; giám sát từng lượt qua instrumentation và trạng thái task. Nếu kiểm thử việc dừng do exception, `get(timeout)` có thể dùng với bounded wait để không treo test.

## 11. fixedRate, fixedDelay và cron liên hệ với chính sách lỗi ra sao?

### 11.1. fixedRate

`scheduleAtFixedRate(...)` đăng ký một periodic task với nhịp cố định. Khi lỗi của lượt hiện tại bị suppress, scheduler tiếp tục cố gắng kích hoạt lượt theo nhịp tiếp theo; nếu task chạy lâu, lượt sau có thể trễ.

Khi exception propagate, theo `ScheduledThreadPoolExecutor` các lượt tiếp theo của **chính registration đó** bị dừng. Tăng poolSize không làm một periodic registration bị fail tự hồi phục.

### 11.2. fixedDelay

`scheduleWithFixedDelay(...)` tính lượt tiếp theo sau khi lượt trước **hoàn tất** rồi chờ delay. Khi handler suppress, wrapper hoàn tất bình thường sau khi xử lý lỗi, nên delay có thể bắt đầu cho lượt sau. Khi handler propagate, chuỗi periodic trên JDK executor có thể dừng.

**Không nhầm** fixedDelay=30s với retry backoff 30s cho chính job đã lỗi. Đây là thời gian giữa **các lượt theo lịch**, không nhất thiết xử lý lại cùng một business item.

### 11.3. cron

`@Scheduled(cron=...)` và `CronTrigger` lập lịch theo các mốc cron. Một handler suppress có thể cho cơ chế trigger tiếp tục lên kế hoạch mốc sau; một handler propagate có thể kết thúc task registration/trigger processing. Tuy nhiên chi tiết re-registration và cách missed firings được xử lý phụ thuộc **TaskScheduler implementation, loại trigger và phiên bản Spring**; đừng suy từ một Future fixed-rate rằng mọi cron implementation đều giống 100%.

Spring có thể tính cron kế tiếp theo trigger context (ví dụ last completion); vì vậy khi job chạy lâu hoặc lỗi, các mốc đã lỡ có thể bị bỏ qua. Không mặc định “suppress nghĩa là chạy bù tất cả mốc cron” hay “propagate nghĩa là mọi job khác trong pool ngừng chạy”.

### 11.4. Lịch đăng ký lặp lại và @Scheduled

Các `@Scheduled` methods imperative thường được hạ tầng Spring biến thành scheduled Runnable và đăng ký với TaskScheduler đã chọn. Việc cấu hình `setErrorHandler` trên scheduler **thực sự được resolve** có thể thay đổi policy xử lý exceptions ném trực tiếp từ các methods đó.

Nếu method tự gọi `this.scheduledMethod()` thông thường, lời gọi đó là một lời gọi Java đồng bộ, không phải một occurrence do scheduling infrastructure; đừng dùng kết quả của lời gọi tay để suy toàn bộ lifecycle của `@Scheduled`.

## 12. Xử lý lỗi trong thân task khác với ErrorHandler toàn scheduler

Có thể chủ động catch trong job để diễn đạt ngữ nghĩa nghiệp vụ rõ hơn:

```java
@Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
public void reconcile() {
    try {
        reconciliationService.reconcile();
        metrics.reconciliationSucceeded();
    } catch (TransientNetworkException ex) {
        metrics.reconciliationFailed("TRANSIENT");
        logger.warn("Transient failure; next schedule may retry", ex);
        // Không throw: bỏ qua lượt hiện tại và để lịch tiếp tục.
    } catch (PermanentConsistencyException ex) {
        metrics.reconciliationFailed("PERMANENT");
        logger.error("Unsafe to continue reconciliation", ex);
        throw ex; // Lỗi sẽ gặp ErrorHandler của scheduler.
    }
}
```

Đây là **fragment**, cần các class/bean nghiệp vụ, logger, metrics, imports và policy phù hợp. Nó minh họa cách phân loại lỗi thay vì gộp tất cả thành một default.

**Điều cần nhớ:** nếu catch tất cả rồi return trong method, ErrorHandler bên ngoài sẽ **không biết** đã xảy ra lỗi. Hãy ghi failure outcome bên trong task; không coi một callback hoàn tất bình thường là thành công nghiệp vụ.

### 12.1. ErrorHandler có thể không có job ID

Hợp đồng `handleError(Throwable)` chỉ nhận Throwable. Nếu nhiều jobs chia sẻ scheduler, handler chung không biết chính xác business job nào lỗi trừ khi có context bổ sung (ví dụ exception type, MDC hoặc wrapper). Để tránh metric mơ hồ, ghi tên job tại boundary của công việc hoặc chia scheduler theo nhóm.

### 12.2. Nếu custom ErrorHandler tự lỗi thì sao?

Nếu handler ghi metric rồi phát sinh `RuntimeException` từ metric client, wrapper sẽ thấy handler ném lỗi; chính nó có thể khiến periodic registration dừng dù policy dự định suppress. Thiết kế handler phải **nhanh, đơn giản, thread-safe và ít gây exception**. Với telemetry quan trọng, chọn cách gửi bất đồng bộ hoặc buffering hợp lý, có fallback/log an toàn.

## 13. Ghi log, retry và circuit breaker: ba khái niệm độc lập

Một policy hoàn chỉnh thường có ba phần:

1. **Quan sát:** ghi log, metrics, trace và status.
2. **Xử lý hiện tại:** retry, fallback, rollback, compensation hoặc ghi kết quả failed.
3. **Hành vi các lượt sau:** tiếp tục lịch, giảm tần suất, tạm ngừng hoặc stop registration.

`ErrorHandler` của Runnable chủ yếu nằm ở bước 1 và quyết định **exception có thoát khỏi scheduler boundary hay không** ở bước 3. Nó không thay toàn bộ logic bước 2.

### 13.1. Retry có điều kiện

Ví dụ minh họa: nếu một HTTP call timeout, có thể retry tối đa 2 lần với backoff. Nhưng nếu database báo dữ liệu sai định dạng vĩnh viễn, retry ngay chỉ làm tăng tải.

Một fragment đặt trong job:

```java
for (int attempt = 1; attempt <= 3; attempt++) {
    try {
        externalClient.push();
        return;
    } catch (TransientException ex) {
        if (attempt == 3) {
            throw ex;
        }
        backoffBetweenAttempts(attempt);
    }
}
```

Đây là fragment, không phải chương trình độc lập: `externalClient`, `TransientException`, `backoffBetweenAttempts` cần có thật; backoff phải tôn trọng interruption, timeout budget và cancellation. Ba iterations là **một occurrence với tối đa ba attempts**; không đồng nghĩa ba occurrences của `@Scheduled`.

### 13.2. Circuit breaker

Nếu downstream đang hỏng kéo dài, cho periodic job tiếp tục ném lỗi mỗi giây sẽ tạo log flood và saturation. Circuit breaker có thể chặn call khi downstream không healthy và mở lại sau khoảng phục hồi, nhưng cần định nghĩa outcome của lượt bị skip và cơ chế alert.

Một handler propagate có thể dừng registration, nhưng nó **không phải** circuit breaker: không tự reopen, không quyết định thời điểm thử lại, không cung cấp metrics trạng thái đóng/mở.

### 13.3. Backfill và missed work

Nếu job chạy mỗi 5 phút để xử lý dữ liệu theo time window, một lượt thất bại không nên bị bỏ qua âm thầm nếu business cần đủ dữ liệu. Với suppress + continue, ghi checkpoint hoặc watermark theo **dữ liệu đã xử lý thành công**, không tăng tiến chỉ vì method đã được trigger. Với propagate + stop, cần cách phát hiện lịch dừng và bắt lại các khoảng bị bỏ sót.

## 14. Reactive @Scheduled không dùng cùng ErrorHandler cho onError

Spring Framework 6.1+ hỗ trợ reactive `@Scheduled` trả `Publisher` như `Mono<Void>`. Trong mô hình này, Spring lấy Publisher và mỗi lần tới lịch sẽ subscribe; lỗi phát ra qua **Reactive Streams `onError`**, không nhất thiết ném thoát scheduled Runnable.

Ví dụ:

```java
@Scheduled(fixedRate = 30_000)
public Mono<Void> reactiveRefresh() {
    return Mono.defer(() -> refreshService.refresh())
            .doOnError(error -> metrics.recordReactiveFailure(error));
}
```

Đây là fragment, yêu cầu Reactor trên classpath. Spring ghi `onError` ở mức **WARN** và tiếp tục scheduled subscriptions theo semantics reactive; **ErrorHandler của Runnable không nhận tín hiệu onError theo cùng cách**.

Vì thế khi đọc bảng ở mục 3, hãy nhớ nó áp dụng cho **lỗi ném ra ngoài Runnable imperative**. Không chuyển mọi khái niệm “LOG_AND_PROPAGATE sẽ dừng periodic” sang reactive Publisher chỉ vì cùng annotation `@Scheduled`. Với reactive job cần policy ở trong pipeline:

- `doOnError`: quan sát lỗi, không tự khôi phục.
- `onErrorResume`: fallback hoặc bỏ qua lượt có chủ đích.
- `retryWhen`: retry bên trong subscription, có backoff và giới hạn.
- `doFinally`: ghi trạng thái completion/error/cancel của lượt.

Phải giám sát error channel của reactive pipeline, không chỉ đếm lỗi trong scheduler ErrorHandler.

## 15. @Async, Future và TaskExecutor cũng có đường lỗi khác

Một method `@Async` trả `CompletableFuture<T>` có thể lưu lỗi trong completion state của Future, caller xem qua `get()/join()` hoặc callback; exception không nhất thiết thoát `Runnable.run()` tại scheduler boundary. Với `@Async void`, Spring có `AsyncUncaughtExceptionHandler` riêng.

Do đó:

- `ErrorHandler` của `ThreadPoolTaskScheduler` quản lý lỗi các scheduled Runnables đi qua nó.
- `AsyncUncaughtExceptionHandler` dành cho lỗi uncaught của các `@Async void` methods theo hợp đồng async.
- `Future.get()` cho phép caller quan sát outcome của task có Future.
- Reactive `onError` thuộc Publisher/subscriber contract.

Không thiết lập một handler rồi cho rằng mọi loại lỗi Java/Spring đều sẽ hội tụ về đó.

## 16. Không làm chậm scheduler thread khi xử lý lỗi

Một handler ghi log trực tiếp lên network hoặc gọi DB đồng bộ có thể giữ scheduler thread lâu; với poolSize=1, các jobs khác cũng bị trễ. Trong demo, handler chỉ tăng `AtomicInteger` hoặc dùng logger mặc định. Production nên:

- Ghi metric/counter nhanh, tránh truy vấn dữ liệu lớn trong `handleError`.
- Không retry network trong chính ErrorHandler nếu đã có retry policy trong task.
- Thêm timeout cho telemetry client nếu cần.
- Phân loại lỗi để alert đúng cấp, không dùng nguyên exception message làm metric label.
- Gắn job name/correlation ID ở business boundary.
- Nếu dùng MDC/ThreadLocal, bảo đảm cleanup khi scheduler thread được tái sử dụng.

**Ghi nhận rồi tiếp tục** không chỉ là handler có return bình thường; nó phải để hệ thống **quan sát được** số lỗi, và không trở thành nguồn tạo nghẽn scheduler.

## 17. Các lỗi và hiểu sai phổ biến

| Hiểu sai | Hậu quả | Cách xử lý |
| --- | --- | --- |
| “Ghi lỗi rồi tiếp tục” nghĩa là task không thất bại | Business failure bị che mất | Tách outcome của một lượt khỏi status lịch |
| “Propagate” nghĩa là ném ngay về caller đã gọi schedule | Caller không thấy lỗi trực tiếp, vì worker chạy sau | Dùng Future/metrics/handler ở đúng thread |
| “Propagate” dừng toàn bộ Spring application | Một registration có thể dừng, jobs khác vẫn chạy | Giám sát registration và lifecycle cụ thể |
| “Suppress” nghĩa là không ghi log | Default TaskUtils vẫn log lỗi | Phân biệt logging và rethrow |
| Một lỗi periodic mặc định luôn làm lịch dừng | Spring recurring default thường suppress | Kiểm chứng actual handler và scheduler |
| Custom suppress giống default cho mọi task | One-shot Future có thể báo success giả | Tách chính sách one-shot và repeating |
| `future.isDone() == true` nghĩa là successful | Future có thể failed/cancelled | Kiểm tra `get()`/outcome |
| `future.isCancelled() == false` nghĩa là đang chạy | Future có thể completed exceptionally | Đọc cả done và exception state |
| `periodicFuture.get()` sẽ trả sau mỗi lượt | Thường đợi tới cancel/abnormal completion | Không dùng get() để theo dõi từng occurrence |
| Handler count = số task nghiệp vụ thành công | Nó chỉ thấy lỗi đi qua handler | Ghi metrics success/failure tại job boundary |
| Lịch vẫn còn chạy tức lỗi đã tự retry thành công | Lượt khác không phải retry cùng dữ liệu | Thiết kế retry/backfill/checkpoint |
| Tăng poolSize để một registration tự sống lại sau propagate | Future cũ đã failed | Cần đăng ký lại có chính sách |
| Chỉ giữ stack trace trong log | Không biết last success/consecutive failures | Status store và alert |
| Tắt lịch ngay với mọi timeout tạm thời | Có thể làm mất công việc quan trọng | Phân loại transient và permanent |
| Nuốt mọi lỗi bằng `catch(Throwable)` | Có thể che lỗi nghiêm trọng của JVM | Xử lý exception có chọn lọc |
| Handler gọi metric client rồi metric client ném lỗi | Chính handler có thể khiến lịch dừng | Làm telemetry robust, không để exception lan vô ý |
| Nghĩ `ErrorHandler` sẽ nhận `Mono.onError` của reactive @Scheduled | Bỏ sót reactive failures | Đo Reactor pipeline |
| Nghĩ TaskUtils public là API business ổn định | Phụ thuộc chi tiết infrastructure | Ưu tiên custom policy rõ và test version đang dùng |
| Gọi `Thread.sleep()` để test “đã chạy N lần” | Test flake | Dùng CountDownLatch, bounded wait, assertions |
| Thử policy bằng cách gọi trực tiếp method `@Scheduled` | Không qua scheduler wrapper | Test qua TaskScheduler/Spring context thực |

## 18. Bài tập tự kiểm tra

**Bài 1.** Spring lập lịch một Runnable mỗi 10 giây. Ở lần thứ nhất Runnable ném lỗi. ErrorHandler chỉ ghi metric rồi return. Những lượt sau có thể chạy không?

Có, nếu registration và scheduler vẫn hoạt động. Exception đã bị suppress tại boundary Runnable. Nhưng lượt thứ nhất vẫn thất bại về mặt nghiệp vụ; handler không tự retry phần việc đó.

**Bài 2.** Cũng job trên, ErrorHandler ghi log rồi rethrow. `ScheduledFuture.get()` có thể trả gì?

Với periodic registration trên `ScheduledThreadPoolExecutor`, Future có thể hoàn tất bất thường; `get()` ném `ExecutionException` chứa nguyên nhân. Các execution tiếp theo của cùng registration bị ngăn. Đó không phải một cơ chế dừng toàn bộ scheduler.

**Bài 3.** Tại sao Spring mặc định suppress lỗi của repeating tasks nhưng propagate ở one-shot?

Repeating tasks thường cần cơ hội chạy lượt sau dù một lần lỗi; one-shot có Future để caller nhận failure outcome. Đây là chính sách mặc định khi không có custom handler, không phải quy tắc áp dụng cho mọi implementation trong mọi hệ sinh thái Spring.

**Bài 4.** Một custom ErrorHandler nuốt lỗi của one-shot task. Caller gọi `future.get()` và nhận `null`. Có chứng minh thành công nghiệp vụ không?

Không. Chương trình demo đã kiểm chứng body có thể ném `IllegalStateException` mà wrapper return bình thường và Future báo completed. Cần outcome/business status độc lập hoặc không suppress one-shot nếu caller dựa trên Future.

**Bài 5.** `future.isDone()==true` và `future.isCancelled()==false` sau lỗi periodic nói điều gì?

Có thể là completion **exceptionally**, không phải success hay cancellation. Gọi `get()` với bounded timeout hoặc dùng cơ chế giám sát để xác định nguyên nhân.

**Bài 6.** Cron job bỏ lỡ ba mốc vì một lỗi đã được suppress. Suppress có tự chạy bù cả ba mốc không?

Không. Việc tính lần cron kế tiếp và xử lý missed firings phụ thuộc trigger/implementation. Nếu yêu cầu business đủ mọi time window, phải có watermark/checkpoint/backfill riêng.

**Bài 7.** Nếu reactive scheduled Mono phát onError, custom `ThreadPoolTaskScheduler.setErrorHandler(...)` có chắc nhận được không?

Không. Spring subscriber xử lý `onError` riêng theo Reactive Streams, thông thường ghi WARN rồi tiếp tục schedule; cần đo lỗi trong reactive pipeline.

**Bài 8.** Một tác vụ polling có lỗi 503 thoáng qua. Nên luôn propagate để dừng lịch không?

Không có đáp án chung. Với polling có thể bỏ qua lượt ngắn, suppress + metric + alert khi lỗi liên tiếp có thể hợp lý. Với critical state invariant có nguy cơ gây hỏng dữ liệu, propagate/fail-fast có thể an toàn hơn. Chính sách phụ thuộc nghiệp vụ và khả năng hồi phục.

## 19. Checklist triển khai production

Trước khi bật scheduling, kiểm tra:

- Đã xác định mỗi job là **repeating** hay **one-shot**?
- Có hiểu default Spring TaskUtils: repeating suppress, one-shot propagate khi không có custom ErrorHandler?
- Biết custom `setErrorHandler` có thể thay đổi chính sách mặc định cho các tasks dùng cùng scheduler?
- Có phân biệt **job-run failure** với **registration còn hoạt động**?
- ErrorHandler có ném lại hay không, và hành vi ấy được ghi rõ trong tài liệu policy?
- Với one-shot, caller có đang dựa vào `Future.get()` để suy thành công? Nếu có, có tránh suppress lỗi gây success giả?
- Với periodic Future, có tránh gọi `get()` vô hạn để chờ một lượt không?
- Có metrics last success, last failure, consecutive failures, job outcome, cancelled và stopped-registration?
- Có alert khi lịch ngừng sau propagate hoặc khi lỗi lặp vô hạn dưới suppress?
- Lỗi có được phân loại transient/permanent và có retry/backoff/timeouts hợp lý?
- Có idempotency, checkpoint và backfill cho job có side effects hoặc yêu cầu không mất dữ liệu?
- ErrorHandler có nhanh, thread-safe và không phát sinh exception ngoài ý muốn?
- Scheduler bean đang được `@Scheduled` sử dụng có đúng là bean được cấu hình?
- Nếu dùng fixedRate/fixedDelay/cron, đã xác định semantics của missed firings, long-running tasks, overlap?
- Reactive `onError` và `@Async` errors có được quan sát ở **đúng error boundary**?
- Nếu deployment có nhiều instances, đã xác định distributed coordination/duplicate execution policy chưa?
- Test trên Spring version thực tế có dùng latch, timeout và assertions để chứng minh continue/stop, thay vì chỉ xem log?

## 20. Kết luận

Trong Spring scheduling imperative, hai chính sách có thể **cùng ghi log** nhưng khác nhau ở việc xử lý exception sau đó:

- **Ghi nhận rồi tiếp tục:** `ErrorHandler.handleError(...)` return bình thường, suppress exception ở wrapper. Các lượt periodic tiếp theo có cơ hội chạy; tuy nhiên failure nghiệp vụ vẫn xảy ra, cần metrics, retry/backfill và idempotency phù hợp.
- **Ghi nhận rồi cho lỗi lan ra:** handler rethrow, executor nhìn thấy một execution thất bại. Với `ScheduledThreadPoolExecutor`, periodic registration thường kết thúc bất thường, `Future.get()` báo `ExecutionException`; lịch khác và toàn bộ app không tự động dừng.

Spring 6.2.1 mặc định **log + suppress cho repeating tasks**, **log + propagate cho one-shot tasks** khi không có custom handler. Một custom suppress handler có thể vô tình che lỗi của one-shot Future, nên chính sách cần được xác định **theo loại job và hợp đồng business**, không chỉ theo nhu cầu ghi log.

### Tài liệu tham khảo

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2 — TaskUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.6/javadoc-api/org/springframework/scheduling/support/TaskUtils.html)
- [Spring Framework 6.2.1 — ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework 6.2.1 — DelegatingErrorHandlingRunnable Javadoc](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/support/DelegatingErrorHandlingRunnable.html)
- [Java SE 21 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html)
- [Java SE 21 — Future](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduling-failures-004">Không để công việc mất lịch mà không có tín hiệu quan sát</a>

<details>
<summary>Xem chi tiết</summary>

# Không để công việc mất lịch mà không có tín hiệu quan sát

Một tác vụ chạy theo lịch có thể **ngừng hoạt động nhiều giờ mà không có thông báo rõ ràng**. Trong Java và Spring Framework, điều này có thể xảy ra khi một `Runnable` định kỳ ném exception làm lịch bị chấm dứt, scheduler bị shutdown, bean `@Scheduled` không được phát hiện, cron bị vô hiệu hóa, thread scheduler bị kẹt, hoặc một tác vụ vẫn được gọi đều nhưng **mọi lần xử lý nghiệp vụ đều thất bại**.

Đề bài **“Không để công việc mất lịch mà không có tín hiệu quan sát”** thuộc nhóm **Lỗi của lịch chạy** của module **Spring Framework – concurrency**. Mục tiêu không chỉ là bắt exception và ghi log, mà xây dựng **cơ chế chứng minh tác vụ còn được đăng ký, còn được kích hoạt, kết thúc có kết quả và thực sự đạt mục tiêu nghiệp vụ**, kèm cảnh báo khi bất kỳ lớp nào im lặng.

> Một job “không có exception” chưa chắc khỏe. Một `@Scheduled` bean “vẫn tồn tại” chưa chắc còn tick. Một tick “đã bắt đầu” chưa chắc đã thành công. Và không thấy log lỗi **không** chứng minh job đang hoạt động.

Bài học giải thích sự khác biệt giữa cơ chế của JDK `ScheduledExecutorService` và Spring `ThreadPoolTaskScheduler`, vai trò `ErrorHandler`, `ScheduledFuture`, `@Scheduled`, Actuator và monitoring độc lập; có ví dụ Java 17 chạy được, ứng dụng Spring Boot 3.x và integration test.

## 1. “Mất lịch” nghĩa là gì?

Trong một hệ thống xử lý đơn hàng, task kiểm tra đơn hàng quá hạn được dự kiến chạy mỗi phút. Có ít nhất năm trạng thái lỗi khác nhau:

| Trạng thái | Điều có thể đang xảy ra | Rủi ro |
| --- | --- | --- |
| **Không được đăng ký lịch** | Bean không được scan, thiếu `@EnableScheduling`, profile/condition không khớp | Không có tick nào ngay từ đầu |
| **Lịch đã bị hủy/chấm dứt** | `ScheduledFuture` bị cancel, raw JDK periodic task gặp exception, scheduler shutdown | Tick từng chạy rồi ngừng |
| **Lịch còn nhưng không được thực thi đúng hạn** | Worker bị block, pool starvation, GC pause, cron chưa đến giờ, queue quá tải | Thời điểm chạy lệch SLO |
| **Tick vẫn diễn ra nhưng mỗi lần đều lỗi** | Spring suppress exception để giữ lịch, downstream liên tục thất bại | Có “lịch sống” nhưng không có nghiệp vụ thành công |
| **Tick thành công về mặt kỹ thuật nhưng công việc thực thất bại** | Runnable chỉ `submit` vào worker khác rồi return; worker lỗi muộn | Dashboard hiển thị sai thành công |

Vì thế cần đo ít nhất bốn dấu hiệu:

1. **Registration:** chương trình có tạo schedule không?
2. **Liveness:** lịch có tiếp tục tạo lượt chạy vào những thời điểm hợp lệ không?
3. **Outcome:** mỗi lượt thành công, thất bại hay bị hủy?
4. **Business freshness:** dữ liệu đầu ra có được cập nhật bền vững trong thời gian chấp nhận được không?

Ví dụ công việc chạy mỗi 5 phút, SLO là ít nhất một lần **đối soát thành công trong 15 phút**. Chỉ đếm `attempts` hoặc `ticks` sẽ bỏ sót trường hợp tất cả attempt đều lỗi.

### 1.1. Trạng thái yêu cầu theo dõi là trạng thái nào?

```text
ApplicationContext
   |
   +--> job bean discovered?
   |            |
   |            v
   +--> schedule registered?
                |
                +--> next tick expected
                         |
                         +--> tick started
                                   |
                                   +--> work started
                                   |       |
                                   |       +--> succeeded and committed
                                   |       +--> failed / timeout
                                   |       +--> cancelled / unknown
                                   |
                                   +--> another executor accepted work
                                           |
                                           +--> business may still be running
```

Kết thúc HTTP response hay method `@Scheduled` không luôn đồng nghĩa công việc phía sau đã hoàn tất. Nếu tick chỉ chuyển việc sang `@Async`, ta cần quan sát **Future/job outcome riêng**, chứ không đánh dấu tick success ngay sau `submit()`.

## 2. JDK thuần: một exception có thể dừng vĩnh viễn periodic schedule

Theo Javadoc `ScheduledExecutorService.scheduleAtFixedRate` và `scheduleWithFixedDelay`, một loạt lần chạy định kỳ **bị ngăn các lần sau** nếu một lần thực thi task ném exception không được xử lý ra ngoài. `ScheduledFuture` có thể chuyển sang **done** và `get()` sẽ ném `ExecutionException` chứa exception gốc.

```java
ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
        () -> {
            processBatch(); // Nếu ném exception ra ngoài, lịch có thể dừng.
        },
        0, 5, TimeUnit.SECONDS);
```

Đây là fragment, cần imports, `scheduler` và `processBatch`. Nếu một lượt ném `IllegalStateException`, **không còn lượt nào tiếp theo cho chính periodic registration đó**. Cách nó biểu hiện ở sản phẩm có thể là “log lỗi một lần rồi im lặng mãi”.

### 2.1. Exception từ task không đồng nghĩa executor chết toàn bộ

Ở `ScheduledThreadPoolExecutor`, việc chấm dứt một periodic future **không có nghĩa toàn bộ thread pool hoặc mọi schedule khác đều chết**. Scheduler có thể còn chạy các task khác. Điều này dễ làm dashboard chung về “scheduler threads còn sống” báo xanh trong khi **một job quan trọng đã mất lịch**.

Một trạng thái cần phân biệt:

```text
scheduledFuture.isDone()      == true
scheduledFuture.isCancelled() == false
```

Trường hợp này có thể phản ánh một periodic task đã kết thúc **bất thường** vì exception. Nếu bị cancel chủ động, `isCancelled()` thường true. Nhưng các thuộc tính là snapshot và không thay thế log/metric cùng nguyên nhân.

### 2.2. Đừng gọi get() vô hạn để giám sát một lịch còn chạy

Đối với periodic future đang hoạt động bình thường, `ScheduledFuture.get()` **không trả về sau một tick**; nó chờ tới khi future kết thúc do cancellation/abnormal completion. Gọi `get()` không timeout trên thread giám sát có thể làm thread đó chặn mãi.

Giám sát nên dùng callback/metric của từng lượt và trạng thái future được lấy có kiểm soát. Khi `future.isDone()` true, có thể truy vấn kết quả terminal bằng `get()` và xử lý `CancellationException`/`ExecutionException`. Không đánh đồng trạng thái `done` với nghiệp vụ thành công.

## 3. Java 17 runnable: lịch mất sau exception, và cách giữ nó tiếp tục

Đây là chương trình JDK 17 độc lập không cần Spring. Nó minh họa **hai trường hợp đối chiếu** trên `ScheduledThreadPoolExecutor`:

- Một periodic task ném `IllegalStateException` ra ngoài ở lượt đầu; JDK **chấm dứt lịch** và future trở thành done.
- Một periodic task khác xử lý lỗi **bên trong** callback, ghi nhận thất bại rồi cho lượt tiếp theo diễn ra.

Thay vì dựa vào `Thread.sleep()` và đoán “chạy đủ hai lượt”, chương trình dùng `CountDownLatch` để chặn lượt thứ hai đúng điểm cần quan sát, rồi hủy lịch để stdout ổn định.

Tạo tệp **LostScheduleDemo.java**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LostScheduleDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService pool =
                Executors.newSingleThreadScheduledExecutor();

        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch allowSecondFinish = new CountDownLatch(1);

        try {
            AtomicInteger rawRuns = new AtomicInteger();
            ScheduledFuture<?> raw = pool.scheduleAtFixedRate(() -> {
                rawRuns.incrementAndGet();
                throw new IllegalStateException("boom");
            }, 0, 10, TimeUnit.MILLISECONDS);

            String cause;
            try {
                raw.get(5, TimeUnit.SECONDS);
                throw new AssertionError("Expected task failure");
            } catch (ExecutionException ex) {
                cause = ex.getCause().getClass().getSimpleName();
            }

            System.out.println("rawRuns=" + rawRuns.get());
            System.out.println("rawDone=" + raw.isDone());
            System.out.println("rawCancelled=" + raw.isCancelled());
            System.out.println("rawCause=" + cause);

            AtomicInteger guardedRuns = new AtomicInteger();
            AtomicInteger guardedFailures = new AtomicInteger();

            ScheduledFuture<?> guarded = pool.scheduleAtFixedRate(() -> {
                int attempt = guardedRuns.incrementAndGet();

                try {
                    if (attempt == 1) {
                        throw new IllegalStateException("transient");
                    }
                    if (attempt == 2) {
                        secondStarted.countDown();
                        allowSecondFinish.await();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ex) {
                    guardedFailures.incrementAndGet();
                }
            }, 0, 10, TimeUnit.MILLISECONDS);

            secondStarted.await(); // Lượt 2 đã bắt đầu.
            boolean cancelled = guarded.cancel(false);
            allowSecondFinish.countDown();

            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Scheduler did not stop");
            }

            System.out.println("guardedRuns=" + guardedRuns.get());
            System.out.println("guardedFailures=" + guardedFailures.get());
            System.out.println("guardedCancelled=" + cancelled);
        } finally {
            allowSecondFinish.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac LostScheduleDemo.java
java LostScheduleDemo
```

**Stdout xác định khi chương trình chạy bình thường:**

```text
rawRuns=1
rawDone=true
rawCancelled=false
rawCause=IllegalStateException
guardedRuns=2
guardedFailures=1
guardedCancelled=true
```

Giải thích:

1. `raw` chạy một lần, tăng counter rồi ném exception. JDK future chấm dứt bất thường; `raw.get(...)` phát `ExecutionException`, và `isDone()==true`, `isCancelled()==false`.
2. Vì `raw.get(...)` đợi future kết thúc trước khi in, không cần đợi ngẫu nhiên để biết nguyên nhân terminal.
3. `guarded` gặp lỗi ở lượt đầu nhưng **bắt lỗi bên trong Runnable**, nên không ném exception ra scheduler.
4. Lượt thứ hai mở `secondStarted` rồi chặn `allowSecondFinish`; main xác nhận đã đến lượt 2 bằng latch.
5. Main gọi `guarded.cancel(false)` khi lượt 2 còn đang chạy. Lịch không tạo lượt 3; `cancel(false)` không yêu cầu interrupt lượt 2.
6. Main mở latch, đóng pool và đợi termination, nên các giá trị counter được in sau khi lượt 2 kết thúc; số lượt bằng 2, số lỗi được bắt là 1.
7. `finally` giải phóng latch và đóng scheduler nếu có exception trên đường chạy khác.

**Giới hạn:** ví dụ JDK này không mô tả error handler mặc định của `ThreadPoolTaskScheduler` trong Spring. Chính khác biệt đó là phần tiếp theo. Một task “được giữ chạy tiếp” cũng **không đồng nghĩa lần lỗi trước đã được retry hoặc được xử lý thành công**.

## 4. Spring ThreadPoolTaskScheduler: mặc định với lỗi lặp lại thường là log rồi suppress

Spring Framework có `org.springframework.scheduling.support.TaskUtils` và các `ErrorHandler`. Theo Javadoc, với **repeating tasks**, default `ErrorHandler` thường **ghi log và suppress exception**, tức không để exception đó ngăn các lần chạy tiếp theo. Với **one-time tasks**, default thường log rồi propagate vì lỗi có thể được quan sát qua Future.

| Môi trường | Exception thoát khỏi một lượt periodic task | Hành vi tiếp theo |
| --- | --- | --- |
| JDK `ScheduledExecutorService.scheduleAtFixedRate` thuần | Không bắt | Các lượt tiếp theo của cùng future **bị ngăn lại** |
| Spring `ThreadPoolTaskScheduler`, repeating task với default handler | ErrorHandler log/suppress | Các lượt tiếp theo **thường tiếp tục** |
| Spring với custom `ErrorHandler` tự ném exception | Exception được propagate | Lịch periodic có thể **chấm dứt** như underlying scheduler |
| Spring với handler tự nuốt không log/metric | Exception bị suppress | Lịch có thể tiếp tục nhưng lỗi nghiệp vụ **bị che giấu** |

Vì vậy câu “mọi exception trong `@Scheduled` sẽ dừng lịch” là **sai nếu áp vào cấu hình Spring mặc định**. Câu “`@Scheduled` không bao giờ mất lịch vì Spring tự xử lý hết” cũng sai: cấu hình custom, registration error, shutdown, blocking và nhiều loại failure khác vẫn có thể xảy ra.

### 4.1. ErrorHandler có hai chính sách đáng nhớ

Trong `TaskUtils`:

- `LOG_AND_SUPPRESS_ERROR_HANDLER`: log rồi nuốt exception, không ngăn lần chạy tiếp theo.
- `LOG_AND_PROPAGATE_ERROR_HANDLER`: log rồi ném lại exception, thường ngăn những lần chạy sau.

```java
@Bean
ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler =
            new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("periodic-");

    scheduler.setErrorHandler(error -> {
        log.error("Uncaught scheduled task failure", error);
        // Không ném lại: cho recurring schedule tiếp tục.
    });

    return scheduler;
}
```

Đây là fragment trong một Spring `@Configuration` có logger hợp lệ. **Cấu hình handler log/suppress là chủ ý**, không đồng nghĩa mọi thất bại được khắc phục. Nếu thay phần handler thành `throw new IllegalStateException(error)`, code có thể khiến periodic future kết thúc sớm. Nếu chỉ để `error -> {}`, bạn còn tạo ra lỗi **không có tín hiệu quan sát**, trái mục tiêu bài.

### 4.2. Bắt exception ở đúng nơi nào?

Một cách an toàn thường gồm **hai lớp**:

- **Tại job:** ghi nhận attempt, failure/success, duration và trạng thái nghiệp vụ; xác định retry/skip/idempotency.
- **Tại scheduler:** đặt ErrorHandler làm **last-resort safety net**, ghi log/metric nếu một exception thoát khỏi job.

Không nên chỉ có một catch bao ngoài toàn bộ `Runnable` mà không lưu kết quả nghiệp vụ. Một tác vụ có thể tiếp tục chạy đúng chu kỳ nhưng thất bại 1.000 lần; chỉ nhìn số tick sẽ báo xanh sai.

**Lưu ý:** `ErrorHandler` trên scheduler không phải nơi đáng tin để bắt những exception ở detached `CompletableFuture` hoặc worker pool khác; exception có thể bị lưu trong Future và không hề ném ra thread scheduler.

### 4.3. Đừng mặc định ErrorHandler nhận mọi lỗi reactive

Trong Spring Framework 6.1+, một method `@Scheduled` có thể trả `Publisher` reactive được hỗ trợ. Spring nhận một publisher, rồi subscribe theo từng mốc; `onError` của publisher **được log ở WARN và không ngăn những lần subscribe sau** theo tài liệu. `ErrorHandler` dành cho `Runnable` đồng bộ không trực tiếp xử lý `onError` vì lỗi đó là reactive signal.

```java
@Scheduled(fixedRate = 5000)
public Mono<Void> refresh() {
    return Mono.defer(() ->
            remoteRefresh().then());
}
```

Đây là fragment có Reactor và một method `remoteRefresh()` trả publisher. Muốn ghi nhận error outcome cần gắn xử lý reactive phù hợp:

```java
return Mono.defer(() ->
        remoteRefresh()
            .doOnError(error ->
                    jobMetrics.recordFailure(error))
            .then());
```

Cũng là fragment; không gọi `subscribe()` thủ công trong method `@Scheduled` vì Spring quản lý subscription. Cần phân biệt **Publisher được tạo một lần** và **các subscription được tạo nhiều lần**; publisher phải là cold/deferred và hỗ trợ repeated subscriptions.

## 5. Những cách mất lịch không liên quan trực tiếp đến exception của job

Có rất nhiều nguyên nhân “không thấy tick” dù code business method không ném lỗi.

### 5.1. Bean không được Spring phát hiện

Method annotated nhưng class không là managed bean: dùng `new` thủ công, nằm ngoài component scan, bị `@Profile`/`@ConditionalOnProperty` loại, hoặc lazy bean chưa khởi tạo. Nếu không có bean được scheduling processor xử lý, **không có lịch từ annotation**.

`@EnableScheduling` phải được bật trong ApplicationContext **chứa bean tương ứng**. Nó không tự lan đến mọi parent/child context. `@EnableAsync` không thay thế `@EnableScheduling`.

### 5.2. Trigger bị disable hoặc không có mốc sắp tới

`@Scheduled(cron = "${jobs.cron:-}")` hỗ trợ sentinel `-` để disable cron. Bean có thể tồn tại, nhưng **không có trigger đang chạy**. Một cron cho 02:00 mỗi thứ Hai chưa đến giờ cũng không phát tick “để chứng minh sống”.

Với cron, phải tính theo `zone`/timezone và day-of-week chính xác; tránh lấy “đã qua 5 phút không thấy log” làm căn cứ cho cron chạy hằng ngày.

### 5.3. Scheduler bị shutdown, task bị cancel

Một `TaskScheduler` do context quản lý sẽ đóng theo vòng đời ứng dụng. Shutdown bình thường không phải lỗi cần pager “job mất lịch”. Nhưng nếu scheduler bị shutdown ngoài dự kiến khi ứng dụng vẫn nhận traffic, hoặc `ScheduledFuture.cancel(...)` bị gọi nhầm, sẽ không còn trigger tương ứng.

Cần ghi nhận **event đăng ký/hủy**, trạng thái instance đang shutdown/ready, và phân biệt hủy theo kế hoạch với mất lịch bất thường.

### 5.4. Worker bị block hoặc scheduler pool starvation

`ThreadPoolTaskScheduler` thường thực thi callback trên scheduler worker. Nếu một callback block ở JDBC/HTTP/lock quá lâu, các lịch khác có thể chậm. `fixedRate` không bảo đảm real-time; `fixedDelay` cố chờ lượt trước hoàn tất rồi mới tính delay. Khi việc chạy lâu hơn period, có thể **trễ lịch** mà future vẫn active.

Vì vậy cần đo **lastStart, runDuration, startLag, active workers** và timeout ở dependency, không chỉ đo “scheduler bean còn trong context”.

### 5.5. Mỗi replica có lịch riêng, nhưng không có job nào thành công

Với nhiều pods, từng replica có `@Scheduled` riêng. Một replica bị lỗi không có nghĩa mọi replica đều lỗi; ngược lại, metric tổng có thể vẫn xanh nhờ một replica trong khi replica khác im lặng. Phải xác định mục tiêu giám sát:

- Liveness của **từng instance** cần phát hiện instance im lặng.
- Business freshness **toàn hệ thống** cần được tính từ dữ liệu/job store có thẩm quyền.
- Nếu chỉ một leader được phép chạy, follower **không tick** là bình thường; cần quan sát leader lease/ownership và tiến độ của leader, không pager tất cả followers vì thiếu tick.

## 6. Actuator scheduledtasks giúp kiểm tra registration, không thay heartbeat nghiệp vụ

Spring Boot Actuator có endpoint `/actuator/scheduledtasks` khi được bật/expose phù hợp. Nó hiển thị thông tin về những scheduled tasks trong application, có thể gồm cron/fixedRate/fixedDelay, runnable, interval và một số thông tin last/next execution tùy version.

Ví dụ configuration minh họa với dependency `spring-boot-starter-actuator`:

```properties
management.endpoints.web.exposure.include=health,info,metrics,scheduledtasks
```

Kiểm tra trên instance được phép truy cập:

```bash
curl -s http://localhost:8080/actuator/scheduledtasks
```

**Giới hạn quan trọng:**

- Endpoint mô tả những scheduled registrations mà Actuator nhìn thấy, **không tự chứng minh một nghiệp vụ đã thành công**.
- Một task xuất hiện trong danh sách có thể đang bị block, thất bại ở mọi tick hoặc hoàn tất trước khi một async worker lỗi.
- Một task không xuất hiện có thể là job đăng ký động qua cơ chế khác chưa được endpoint phản ánh như bạn kỳ vọng; cần hiểu scheduler implementation/version.
- Một cron chưa đến giờ không được coi là mất lịch chỉ vì chưa phát tick.
- Đừng công khai `scheduledtasks`/`env`/`beans` cho internet không kiểm soát: endpoint có thể lộ topology, tên method và cấu hình. Áp access control và exposure policy.

**Phân loại tín hiệu:**

```text
/actuator/scheduledtasks     registration metadata
/actuator/health             health indicators đang cấu hình
/actuator/metrics            metrics hiện có
Custom job status/store      attempt, success, lastSuccess, pending, lag
External alerting            đánh giá ngưỡng, absence, dependency và SLO
```

`/actuator/health` không tự biết mọi công việc định kỳ đang thành công. Muốn health thật sự phản ánh job freshness, cần custom `HealthIndicator` hoặc hệ thống cảnh báo đọc metric/job state; không gắn tất cả việc hậu trường vào readiness probe một cách máy móc vì có thể gây restart loop.

## 7. Thiết kế tín hiệu: started, finished, success, failure, heartbeat

Đề nghị ghi nhận tối thiểu cho **mỗi logical job**:

| Tín hiệu | Khi cập nhật | Phát hiện được gì? |
| --- | --- | --- |
| `registered_at` | Khi lịch được đăng ký thành công | Chưa từng có schedule |
| `last_started_at` | Ngay khi một lượt chạy bắt đầu | Lâu rồi không có tick |
| `last_finished_at` | Khi lượt kết thúc, trong `finally` | Task đang treo hoặc không hoàn tất |
| `last_success_at` | **Sau khi** nghiệp vụ đạt tiêu chí thành công | Dữ liệu stale dù tick vẫn hoạt động |
| `last_failure_at` và error category | Khi lượt thất bại | Tần suất và nguyên nhân lỗi |
| `attempt_total` | Mỗi lượt bắt đầu | Lưu lượng thực thi |
| `success_total`/`failure_total` | Sau kết quả cuối | Phân biệt availability và outcome |
| `active_runs` | Tăng lúc bắt đầu, giảm trong `finally` | Có chạy chồng lấp hoặc stuck |
| `duration`/`start_lag` | Đo khoảng thời gian của một lượt | Vi phạm cadence/SLO |

Không lấy `last_started_at` làm bằng chứng healthy khi job có thể treo mãi. Không lấy `last_finished_at` làm bằng chứng success nếu outcome là failure.

### 7.1. Heartbeat nào dùng để cảnh báo?

Với job fixed-delay 5 phút chạy mất tối đa 1 phút, một policy có thể là:

- **No-start alert:** không có lượt bắt đầu hơn 15 phút, sau startup grace.
- **No-success alert:** không có một kết quả thành công hơn 20 phút nếu nghiệp vụ đòi freshness.
- **Stuck alert:** có active run kéo dài hơn 5 phút hoặc vượt operation deadline.
- **Error-rate alert:** thất bại 5 lần liên tiếp hoặc error ratio vượt ngưỡng.
- **Registration alert:** job quan trọng không nằm trong danh sách schedule/active ownership khi instance đã ready.

Các ngưỡng ở đây **chỉ minh họa**. Ngưỡng thực cần xét period/cron, runtime, jitter, retries, timezone, maintenance window, leader election và SLO nghiệp vụ.

### 7.2. Startup grace và khoảng không có lịch hợp lệ

Nếu một job cron chạy **mỗi đêm 02:00**, lúc 09:00 hôm nay vẫn chưa có lần thành công nào sau application restart lúc 08:00 không nhất thiết là lỗi; lần tiếp theo là 02:00 ngày mai. Tương tự, vừa deploy chưa tới initial delay không nên pager “job đã chết”.

Với fixed-rate/fixed-delay có chu kỳ ngắn, grace period có thể dựa trên `initialDelay + vài period + expected runtime`. Với cron, dùng **expected next occurrence** theo `CronExpression`/`CronTrigger` và business calendar. Với leader-only job, phải kiểm tra leader lease.

### 7.3. Alert absence phải chạy độc lập với scheduler cần giám sát

Đặt watchdog `@Scheduled` trên **cùng một scheduler worker** rồi kỳ vọng nó báo khi worker bị block là anti-pattern. Nếu thread pool bị kẹt hoặc process chết, watchdog cũng im lặng. Tín hiệu thực sự có ích phải được **thu thập và kiểm tra từ bên ngoài vòng lỗi**: monitoring system scrape/push, external probe, hoặc supervisor độc lập có chính sách không chặn bởi cùng task.

Không phải mọi job cần một watchdog riêng; nhưng **critical job không được chỉ tự tuyên bố mình còn sống**.

## 8. Spring Boot 3.x hoàn chỉnh: job có dấu vết thành công/thất bại

Ví dụ này dùng **Java 17+, Spring Boot 3.x / Spring Framework 6.x** cùng dependency `spring-boot-starter-web`. Nó tạo job cố ý thất bại ở lần đầu, thành công ở các lần sau; ghi counters/timestamps rồi cung cấp một status endpoint. Có `ErrorHandler` để log exception lọt ra khỏi scheduled method.

Mục tiêu chứng minh: **một tick lỗi không đồng nghĩa mất schedule; giám sát phải nhìn vào kết quả và thời gian thành công mới nhất**.

Đặt toàn bộ mã tại **src/main/java/com/example/demo/ScheduleObservabilityApplication.java**:

```java
package com.example.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
public class ScheduleObservabilityApplication {
    private static final Logger log = LoggerFactory.getLogger(
            ScheduleObservabilityApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(
                ScheduleObservabilityApplication.class, args);
    }

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("observable-schedule-");
        scheduler.setErrorHandler(error ->
                log.error("Unhandled scheduled task error", error));
        return scheduler;
    }
}

record PollerStatus(
        int attempts,
        int successes,
        int failures,
        boolean running,
        Instant lastStartedAt,
        Instant lastSuccessAt,
        String lastError,
        boolean successStale) {}

@Component
class ObservablePoller {
    private static final Logger log =
            LoggerFactory.getLogger(ObservablePoller.class);

    private final Instant createdAt = Instant.now();
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger successes = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Instant> lastStarted =
            new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccess =
            new AtomicReference<>();
    private final AtomicReference<String> lastError =
            new AtomicReference<>("");

    @Scheduled(initialDelay = 1, fixedDelay = 3,
               timeUnit = TimeUnit.SECONDS)
    public void poll() {
        int attempt = attempts.incrementAndGet();
        running.set(true);
        lastStarted.set(Instant.now());

        try {
            // Lỗi giả lập có kiểm soát: lần đầu thất bại.
            if (attempt == 1) {
                throw new IllegalStateException(
                        "Simulated downstream failure");
            }

            // Một application thật phải await/verify nghiệp vụ
            // và commit trước khi ghi lastSuccess.
            successes.incrementAndGet();
            lastSuccess.set(Instant.now());
            lastError.set("");
            log.info("Poll succeeded, attempt={}", attempt);
        } catch (RuntimeException error) {
            failures.incrementAndGet();
            lastError.set(error.getClass().getSimpleName());
            log.error("Poll failed, attempt={}", attempt, error);
        } finally {
            running.set(false);
        }
    }

    public PollerStatus status() {
        Instant now = Instant.now();
        Instant successAt = lastSuccess.get();
        Instant reference =
                successAt == null ? createdAt : successAt;
        boolean stale = Duration.between(reference, now)
                .compareTo(Duration.ofSeconds(15)) > 0;

        return new PollerStatus(
                attempts.get(),
                successes.get(),
                failures.get(),
                running.get(),
                lastStarted.get(),
                successAt,
                lastError.get(),
                stale);
    }
}

@RestController
class PollerStatusController {
    private final ObservablePoller poller;

    PollerStatusController(ObservablePoller poller) {
        this.poller = poller;
    }

    @GetMapping("/jobs/poller/status")
    public PollerStatus status() {
        return poller.status();
    }
}
```

**Cách chạy:** đặt code trên trong một project Spring Boot 3.x với `spring-boot-starter-web`, build plugin/Gradle wrapper phù hợp:

```bash
./gradlew bootRun
curl -s http://localhost:8080/jobs/poller/status
```

Trên Windows dùng `gradlew.bat bootRun` và `curl.exe` nếu shell yêu cầu. Không thể biên dịch đoạn Spring Boot bằng `javac` trần nếu chưa có dependencies.

Một response **minh họa** sau khi đã có ít nhất hai lượt:

```json
{
  "attempts": 2,
  "successes": 1,
  "failures": 1,
  "running": false,
  "lastStartedAt": "2026-10-29T14:00:05Z",
  "lastSuccessAt": "2026-10-29T14:00:05Z",
  "lastError": "",
  "successStale": false
}
```

**Timestamp và số lượng không cố định**: phụ thuộc lúc startup, scheduler và thời điểm gọi endpoint. `successStale` chỉ dùng ngưỡng **15 giây trong demo**, không phải ngưỡng production. `lastSuccessAt` có thể `null` trước lần thành công đầu tiên.

### 8.1. Tại sao demo có thể quan sát lỗi thay vì mất tích?

1. Bean `ObservablePoller` được Spring tạo và processor `@Scheduled` đăng ký job.
2. `initialDelay=1` cho phép lượt đầu bắt đầu sau khoảng một giây; không cam kết chính xác từng mili giây.
3. Lượt đầu cố ném `IllegalStateException`, nhưng method **bắt và ghi nhận lỗi**: `failures=1`, `lastError=IllegalStateException`, log ERROR.
4. Method return bình thường sau catch, nên periodic schedule tiếp tục.
5. Lượt sau tăng `successes` và cập nhật `lastSuccess` khi nghiệp vụ mô phỏng thành công.
6. Status endpoint cho thấy sự khác biệt giữa `attempts`, `successes`, `failures` và freshness.
7. `ErrorHandler` của scheduler vẫn là safety net cho lỗi bất ngờ thoát khỏi job, không thay nơi ghi nhận kết quả trong job.

### 8.2. Các giới hạn có chủ ý

Ví dụ chỉ lưu trạng thái trong `AtomicInteger`/`AtomicReference` **trong RAM**. Sau process restart, counters và `lastSuccess` reset. Nếu job có ý nghĩa nghiệp vụ xuyên restart, lưu trạng thái/heartbeat trong DB hoặc hệ thống quan sát dùng dữ liệu bền vững.

Ngoài ra:

- `successStale` dùng thời gian từ lúc bean được tạo hoặc lần success gần nhất; startup grace 15 giây là minh họa, không thay logic theo cron/initial delay thật.
- Cùng lúc một thread khác gọi endpoint, snapshot có thể gồm các giá trị được đọc ở những thời điểm rất sát nhau chứ không phải **transactional snapshot**. Với dashboard, các counters nguyên tử thường đủ; với state machine đòi tính nhất quán, dùng cấu trúc snapshot bất biến hoặc synchronization.
- Nếu method chỉ `submit` việc sang executor khác, cập nhật `lastSuccess` ngay sau `submit` sẽ **sai**. Chỉ ghi success sau khi Future/job thật hoàn tất và side effect đạt yêu cầu.
- Nếu dùng `@Transactional` cho business service, mốc `lastSuccess` nên được cập nhật khi có bằng chứng transaction đã commit/operation durable, không chỉ khi một method chưa commit return.
- Một scheduler ErrorHandler chỉ log và suppress có thể để job thất bại vô hạn nếu không có alert trên `lastSuccess`. Phải có hệ thống theo dõi **ngoài application** nếu đây là critical job.

### 8.3. Có cần HealthIndicator luôn trả DOWN khi stale không?

Không bắt buộc. Gắn tình trạng một job nền vào readiness/liveness có tác động vận hành: container có thể bị restart liên tục do downstream tạm lỗi, càng làm job không bao giờ kịp phục hồi. Thay vào đó, có thể:

- Expose **custom job status metric/endpoint** và tạo alert độc lập.
- Với job là chức năng tối quan trọng của một worker chuyên dụng, đưa freshness vào readiness theo policy đã thiết kế.
- Giữ liveness phản ánh **process/loop còn hoạt động**, không coi mọi lỗi business là lý do kill container.

Lựa chọn phải dựa trên SLO và topology, không biến một lỗi downstream ngắn thành restart storm.

## 9. Integration test: một lần lỗi không làm mất recurring schedule

Một Spring integration test nên chứng minh:

- Lịch thực sự được `@Scheduled` đăng ký trong context.
- Lượt đầu ném exception.
- Scheduler có ErrorHandler ghi nhận/suppress lỗi.
- **Lượt thứ hai vẫn được gọi**.
- Test không dựa vào `Thread.sleep` và số tick đoán mò.

Ví dụ JUnit Jupiter + Spring Test (có thể dùng `spring-boot-starter-test` trong test scope). Đặt tại **src/test/java/com/example/demo/RecurringScheduleErrorTest.java**:

```java
package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
        classes = RecurringScheduleErrorTest.TestConfig.class)
class RecurringScheduleErrorTest {
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class TestConfig {
        @Bean
        ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setErrorHandler(error ->
                    System.err.println("Scheduled error: "
                            + error.getClass().getSimpleName()));
            return scheduler;
        }

        @Bean
        ProbeJob probeJob() {
            return new ProbeJob();
        }
    }

    static class ProbeJob {
        final AtomicInteger attempts = new AtomicInteger();
        final CountDownLatch secondRun = new CountDownLatch(1);

        @Scheduled(fixedDelay = 20,
                   timeUnit = TimeUnit.MILLISECONDS)
        public void tick() {
            int n = attempts.incrementAndGet();

            if (n == 1) {
                throw new IllegalStateException("first run failed");
            }

            if (n == 2) {
                secondRun.countDown();
            }
        }
    }

    @Autowired
    ProbeJob job;

    @Test
    void errorIsObservedAndNextRunStillOccurs()
            throws Exception {
        assertTrue(job.secondRun.await(5, TimeUnit.SECONDS),
                "No second scheduled execution");
        assertTrue(job.attempts.get() >= 2);
    }
}
```

Test chạy với build tool của project:

```bash
./gradlew test --tests com.example.demo.RecurringScheduleErrorTest
```

**Vì sao test này có ý nghĩa?** Spring tạo bean `ProbeJob` qua `@Bean`; `@EnableScheduling` phát hiện method; lần đầu ném exception; `setErrorHandler` in lỗi rồi **return** (không propagate); `CountDownLatch` chứng minh có lượt thứ hai. Nó không đoán “20 ms là đã có hai lượt” mà chờ một event cụ thể với giới hạn 5 giây để tránh treo test.

Không khẳng định output stderr ở môi trường test là một chuỗi duy nhất của toàn runner: framework cũng có thể ghi log. Dòng lỗi trong handler có dạng:

```text
Scheduled error: IllegalStateException
```

**Đừng áp dụng handler `System.err.println` như thiết kế monitoring production**. Đây là test tối giản để quan sát. Production cần logger, meter/counters, alert và metadata job.

### 9.1. Negative test cho handler propagate

Một test khác có thể dùng `scheduler.setErrorHandler(error -> { throw new IllegalStateException(error); })`, sau đó kiểm tra một registration periodic không còn sinh lượt mới sau lỗi. Đây là behavior phụ thuộc underlying executor và cách task được bọc; cần cách lấy handle/trạng thái trong test hoặc kiểm tra registered tasks, tránh lấy `Thread.sleep(50)` làm kết luận duy nhất.

Với raw JDK, `LostScheduleDemo` đã chứng minh terminal future rõ ràng hơn. Với Spring, trọng tâm test ở đây là **safety net quan sát exception và schedule tiếp tục** theo cấu hình đang dùng.

## 10. Giám sát khi lịch có thể không khởi động từ đầu

Vì lỗi “job không chạy” có thể xảy ra **trước khi tick đầu tiên**, mọi monitoring chỉ được cập nhật **bên trong tick** đều bất lực trong trường hợp đó. Cần một lớp giám sát ở ngoài callback:

### 10.1. Kiểm tra registration lúc startup

- `@EnableScheduling` có nằm trong context thật không?
- Bean job có được scan hoặc đăng ký qua `@Bean`/`@Import` không?
- `@Profile`, `@ConditionalOnProperty`, cron `-` có đang disable chủ động không?
- Có schedule tương ứng trong Spring Actuator `scheduledtasks` không?
- Custom dynamic registration có trả `ScheduledFuture`/`ScheduledTask` hợp lệ không?
- Có biết **instance nào được phép chạy** trong leader-only deployment không?

Với job bắt buộc phải có nhưng không tìm thấy registration, có thể **fail startup** hoặc tạo một alert cấu hình, thay vì giả định sẽ tự xuất hiện về sau. Với job disabled theo feature flag, “không có registration” có thể là hành vi đúng.

### 10.2. Phân biệt “không xuất hiện trong scheduledtasks” với “không tồn tại job”

Actuator mô tả những task mà endpoint có thể introspect. Một job chạy qua scheduler ngoài Spring, Quartz, external orchestrator hay một process khác có thể không nằm ở danh sách ấy. Vậy check cần được thiết kế **theo scheduler thật của workload**, không coi endpoint là inventory tuyệt đối mọi tác vụ trong hệ thống.

### 10.3. Cron và giờ địa phương

Một cron chạy thứ Hai 09:00 theo `Asia/Ho_Chi_Minh` phải được giám sát theo **expected next execution** và timezone đó, không phải “cứ 10 phút phải thấy tick”. Nếu copy numeric DOW từ Quartz sang Spring, job có thể chạy sai thứ mà không lỗi cú pháp. Với critical cron, unit-test `CronExpression.next(...)` trên các ngày biên, DST của zone liên quan, và kiểm tra timestamp thực tế.

## 11. Thiết kế Prometheus/Micrometer alert không bị “mù tín hiệu”

Có thể công bố các metric tùy chỉnh qua Micrometer. Tên gợi ý (không phải metric mặc định Spring tự tạo cho mọi job):

```text
app_job_attempts_total{job="invoice_reconcile",outcome="started"}
app_job_attempts_total{job="invoice_reconcile",outcome="success"}
app_job_attempts_total{job="invoice_reconcile",outcome="failure"}
app_job_last_start_timestamp_seconds{job="invoice_reconcile"}
app_job_last_success_timestamp_seconds{job="invoice_reconcile"}
app_job_active_runs{job="invoice_reconcile"}
app_job_run_duration_seconds{job="invoice_reconcile",...}
```

Nếu dùng Micrometer và Prometheus registry, phải **đăng ký meter và cập nhật số liệu thực**; chỉ viết tên trong tài liệu không làm metric xuất hiện. Các suffix `_total`/`_seconds` theo conventions cần đối chiếu exporter thực tế. Đừng gắn `correlationId`/UUID/job-run ID vào label; cardinality sẽ bùng nổ và có rủi ro rò dữ liệu.

### 11.1. Một chính sách alert về last success

Với job kỳ vọng có một lần thành công mỗi 10 phút và ngưỡng 30 phút, ý tưởng PromQL:

```promql
time() - app_job_last_success_timestamp_seconds{job="invoice_reconcile"} > 1800
```

Đây là **biểu thức minh họa**, chỉ đúng nếu metric lưu **timestamp Unix seconds** của last success và đã được export. Cần thêm alert cho **metric vắng mặt hoàn toàn**, vì PromQL so sánh trên một series không tồn tại **không tự tạo một cảnh báo**:

```promql
absent(app_job_last_success_timestamp_seconds{job="invoice_reconcile"})
```

Trong production, kết hợp thêm startup grace, leader ownership, maintenance windows và alert routing. Không mặc định **một series thiếu** là lỗi nếu job đang được disabled đúng chủ đích.

### 11.2. Một metric có last success thôi vẫn chưa đủ

Nếu task đã bị kẹt nhưng vừa thành công năm phút trước, alert freshness 30 phút chưa kích hoạt. Cần bổ sung active run duration/start lag và deadline nếu phát hiện stuck sớm là quan trọng.

Ngược lại, nếu job chưa từng thành công từ khi deploy, metric lastSuccess có thể chưa được đăng ký/không tồn tại; phải dùng absence alert hoặc trạng thái bootstrap. Không khởi tạo metric lastSuccess bằng `time()` tại startup rồi gọi là thành công giả.

### 11.3. Monitors phải nhìn được từ ngoài job

Chỉ cập nhật heartbeat trong memory mà không có scraper/probe thật thì nếu JVM chết, chẳng ai đọc giá trị đó nữa. Monitoring cần có chính sách **target down** (ví dụ thiếu scrape/instance health) và business freshness. Nếu job chạy theo leader, metric leader ownership và business result nên được đánh giá phối hợp.

## 12. Khi scheduler vẫn tick nhưng nghiệp vụ thực chạy ở executor khác

Ví dụ:

```java
@Scheduled(fixedRate = 5000)
public void submitCleanup() {
    workExecutor.execute(this::cleanup);
    log.info("Cleanup submitted");
}
```

Đây là fragment. Nếu `workExecutor` chấp nhận task, scheduler method return nhanh. Nhưng `cleanup` có thể thất bại **sau đó**, hoặc không chạy nếu bị từ chối theo handler khác. Dòng “submitted” không chứng minh “done”.

Nếu dùng `CompletableFuture` hoặc `Future`:

```java
CompletableFuture<Void> result =
        CompletableFuture.runAsync(this::cleanup, workExecutor);

result.whenComplete((unused, error) -> {
    if (error == null) {
        recordBusinessSuccess();
    } else {
        recordBusinessFailure(error);
    }
});
```

Đây là fragment: `workExecutor` phải triển khai `Executor` và các method record phải tồn tại. `whenComplete` non-async có thể chạy trên thread hoàn tất hoặc registration thread; nếu callback dùng MDC, phải truyền correlation/job ID có chủ đích.

**Lưu ý:** callback chỉ được gọi khi Future hoàn tất theo contract; nếu task mắc kẹt vô hạn, cần deadline/stuck monitoring, không thể chờ callback để phát alert.

### 12.1. Khi nào bắt `TaskRejectedException`?

Trong Spring `ThreadPoolTaskExecutor`, việc submit có thể ném `TaskRejectedException` khi pool quá tải hoặc shutdown. Nếu scheduler chạy periodic và exception này thoát ra ngoài, ErrorHandler có thể log/suppress rồi tick tiếp; nhưng **job trong tick đó chưa được chấp nhận**.

Ghi outcome `REJECTED`, gửi metric và quyết định retry/backpressure/durable queue. Không lặng lẽ bỏ task nếu nghiệp vụ cần bảo đảm xử lý.

### 12.2. CallerRunsPolicy/DiscardPolicy

Nếu executor dùng `CallerRunsPolicy`, khi quá tải task có thể chạy **ngay trên scheduler thread**, làm lịch khác bị trễ; nếu dùng `DiscardPolicy`, task có thể bị loại im lặng. Vì vậy, thiết kế scheduler lẫn executor policy phải được xem cùng nhau. Với job quan trọng, không dùng silent discard mà không có tín hiệu.

## 13. Retry, idempotency và nguồn sự thật bền vững

Khi một lượt thất bại, câu hỏi không chỉ là “lịch còn tick không?” mà là “công việc cần làm có mất không?”.

### 13.1. Đừng dùng tick count làm nguồn sự thật

Giả sử job poll DB mỗi 30 giây, ứng dụng shutdown 10 phút. Sau restart, không có bảo đảm Spring in-memory scheduler sẽ phát lại đúng 20 tick đã lỡ. Vì vậy, hãy quét các bản ghi **đến hạn dựa trên dữ liệu**:

```sql
SELECT id
FROM pending_jobs
WHERE status = 'PENDING'
  AND next_attempt_at <= CURRENT_TIMESTAMP
ORDER BY next_attempt_at
LIMIT 100;
```

Đây là SQL minh họa; production cần transaction/locking/claim để xử lý multi-worker/replicas, không chỉ `SELECT` rồi xử lý không khóa.

### 13.2. Retry phải không nhân đôi side effect

Nếu job gửi email/thanh toán mà provider đã xử lý nhưng response timeout, việc retry có thể lặp side effect. Dùng idempotency key, status lookup, transactional outbox, reconciliation và policy retry/backoff phù hợp. Không coi exception của worker là bằng chứng hệ thống ngoài chưa nhận thao tác.

### 13.3. Last success nên gắn kết quả durable

`lastSuccessAt` cần tương ứng với **mốc thành công có thể xác minh**, không phải ngay khi worker “gửi yêu cầu”. Ví dụ chỉ đánh dấu invoice reconciliation thành công sau khi transaction DB commit và downstream confirmation theo hợp đồng.

Nếu cập nhật metric last success trong RAM trước khi DB commit rồi transaction rollback, dashboard có thể báo xanh sai. Với critical job, lưu kết quả/attempt vào durable job store, sau đó phát metric từ trạng thái ấy.

## 14. Khác biệt giữa “lịch ngừng” và “lịch trễ”

| Quan sát | Diễn giải có thể | Cần kiểm tra |
| --- | --- | --- |
| `ScheduledFuture.isDone()==true`, `isCancelled()==false` | Periodic future kết thúc bất thường | `get()` sau terminal và exception cause |
| `isCancelled()==true` | Lịch đã nhận cancellation | Ai hủy, có phải shutdown hợp lệ? |
| `isDone()==false` nhưng lastStart cũ | Có thể scheduler lag, pool block, cron chưa đến giờ | Expected next run, thread dump, active jobs |
| lastStart đều nhưng lastSuccess cũ | Business job thất bại hoặc detached task lỗi | Failure metrics, async completion, DB/provider |
| `scheduledtasks` liệt kê job nhưng không có tick | Trigger chưa đến giờ hoặc execution bị kẹt | NextExecution, timezone, pool, lifecycle |
| Không có entry trong `scheduledtasks` | Bean/registration sai hoặc scheduler ngoài phạm vi Actuator | Bean conditions, custom scheduler, dynamic registration |
| Last success mới nhưng dữ liệu stale | Đo success sai vị trí | Durable result, transaction boundaries |
| Không có metric nào | Job chưa chạy, scraper mất, app chết hoặc metric chưa được khởi tạo | Target up, absence alert, registration/startup |

**Đừng đưa tất cả trường hợp vào một lỗi “cron không chạy”.** Diagnosis đúng quyết định giải pháp: sửa bean scanning khác với cấu hình timeout HTTP, khác với chọn leader, khác với tránh silent exception.

## 15. Quản lý ScheduledFuture và trạng thái registration chủ động

Với code sử dụng `TaskScheduler` API trực tiếp:

```java
ScheduledFuture<?> handle = taskScheduler.scheduleAtFixedRate(
        this::runOnce, Duration.ofSeconds(5));
```

Đây là fragment cần `TaskScheduler`, `Duration` và method. Giữ handle để:

- Hủy lịch có chủ đích khi cấu hình thay đổi.
- Ghi audit khi registration/cancellation.
- Kiểm tra `isDone`/`isCancelled` khi cần.
- Phát hiện một periodic future kết thúc bất thường (tùy implementation và policy).

**Nhưng không chỉ nhìn handle để xác nhận business freshness.** Một periodic schedule có thể active và mọi lần xử lý nghiệp vụ đều lỗi.

Trong Spring, `ScheduledTask` từ registrar có API `cancel(...)` và ở các version có `nextExecution()` để hỗ trợ quan sát các lần dự kiến; tính sẵn có phụ thuộc version. `ScheduledAnnotationBeanPostProcessor` cũng quản lý registration của annotated bean theo lifecycle. Không tự hủy một scheduled task do Spring quản lý rồi kỳ vọng nó tự restart nếu không có cơ chế đăng ký lại.

### 15.1. Một supervisor độc lập nên làm gì?

Nó có thể đối chiếu:

1. Danh mục job **bắt buộc** theo config/feature flags.
2. Job nào có registration/owner hợp lệ.
3. Last expected start vs actual last start.
4. Last success và error count theo SLO.
5. Target/app đang online và metrics còn được scrape.
6. Có leader/replica đang sở hữu quyền xử lý job không.
7. Job đang chạy quá deadline hay chưa.

Nếu có vấn đề, supervisor **phát tín hiệu cảnh báo** và có thể kích hoạt runbook/controlled recovery, không tự restart/trigger lại mọi job bằng một vòng lặp thiếu idempotency. Một cảnh báo tốt chứa job name, instance/leader, lastStart, lastSuccess, lastError, duration/lag, config schedule và link logs.

## 16. Runbook chẩn đoán khi một job im lặng

Một quy trình điều tra khả thi:

1. **Xác nhận scope:** job nào, môi trường/replica/tenant nào? Có leader-only không?
2. **Kiểm tra deployment:** app còn running/ready hay đang shutdown/restart?
3. **Kiểm tra bean:** `@EnableScheduling` đúng context, bean được scan, conditions/profile/feature flags?
4. **Kiểm tra registration:** `scheduledtasks`/registrar có job và trigger phù hợp?
5. **Kiểm tra thời điểm:** fixed-rate/fixed-delay/cron? Initial delay, timezone, DST, next expected run?
6. **Kiểm tra scheduler worker:** thread dump, pool size, task duration, lock wait, HTTP/DB timeouts.
7. **Kiểm tra Future/cancellation:** `isDone`/`isCancelled`, owner gọi cancel/shutdown, exception terminal.
8. **Kiểm tra error handling:** Spring default suppress hay custom propagate, có log/metric fail không?
9. **Kiểm tra async delegation:** worker khác đã hoàn tất hay đang lỗi/saturated/rejected?
10. **Kiểm tra business data:** last successful durable result, pending jobs, idempotency/reconciliation.
11. **Khôi phục có kiểm soát:** sửa config/điều phối, claim các job tồn đọng, tránh nhân đôi side effect.
12. **Đóng sự cố bằng bằng chứng:** ít nhất một successful business run, metrics/alerts trở về bình thường và không còn stuck attempts.

**Không khắc phục bằng cách thêm `@Scheduled` lần hai** vào cùng method hoặc deploy nhiều replicas chỉ để “chắc chắn job chạy”: điều đó có thể nhân đôi nghiệp vụ.

## 17. Kiểm thử chống mất lịch

### 17.1. Test unit JDK

Dùng `LostScheduleDemo.java` để chứng minh future terminal sau exception không xử lý. Không lấy thread count còn sống làm bằng chứng schedule còn hoạt động.

### 17.2. Test Spring recurring error

Dùng `RecurringScheduleErrorTest.java`: xác nhận lần đầu thất bại, ErrorHandler nhìn thấy lỗi và lần thứ hai vẫn được gọi. Nếu thay handler thành propagate, kiểm tra behavior trên implementation đúng version.

### 17.3. Test job stale

Tách logic đánh giá freshness thành function nhận `Clock` hoặc `Instant now` để test định lượng không cần `Thread.sleep`:

```java
static boolean isStale(
        Instant lastSuccess, Instant now, Duration limit) {
    return lastSuccess == null
            || Duration.between(lastSuccess, now)
                    .compareTo(limit) > 0;
}
```

Đây là fragment. Với business cron, function thực tế còn cần startup grace/expected schedule window; null last success không luôn là lỗi ngay sau startup. Dùng `Clock.fixed(...)` và vài mốc vào/ra ngưỡng để test boundary.

### 17.4. Test overload/starvation

Tạo task có `CountDownLatch` để giữ scheduler worker, xác nhận một job thứ hai bị trễ khi pool size=1; giải phóng latch rồi kiểm tra job hoạt động. Tránh dùng `Thread.sleep(100)` làm chuẩn thời gian tuyệt đối. Với blocking HTTP/JDBC, đặt operation timeout và kiểm tra cleanup.

### 17.5. Test các trường hợp deployment

- Job conditional bị tắt đúng chủ đích → không cảnh báo sai.
- Job quan trọng không được đăng ký → cảnh báo cấu hình.
- Job cron chưa tới mốc → không cảnh báo no-start tùy tiện.
- App restart làm mất in-memory counters → monitors sử dụng durable/remote evidence.
- Một replica follower không tick → bình thường nếu leader đang healthy.
- Nhiều replicas cùng tick → idempotency/lease ngăn duplicate side effects.
- Scraper không đến được app → có alert absence/target-down, không báo hệ thống healthy giả.

## 18. Những sai lầm thường gặp

**Lỗi 1 — Nghĩ mọi exception đều làm Spring @Scheduled ngừng hẳn.** Spring repeating tasks mặc định thường log/suppress, khác raw JDK periodic future.

**Lỗi 2 — Nghĩ raw JDK vẫn sẽ retry tick sau RuntimeException.** `scheduleAtFixedRate`/`scheduleWithFixedDelay` có thể suppress subsequent executions khi exception thoát ra.

**Lỗi 3 — Chỉ giám sát scheduler thread còn sống.** Một periodic Future có thể done bất thường trong khi pool chạy task khác.

**Lỗi 4 — Chỉ nhìn `isDone()==true` rồi đánh dấu success.** Với periodic future, done có thể vì cancel hoặc failure.

**Lỗi 5 — Gọi `future.get()` không timeout trên periodic schedule đang healthy.** Nó có thể chờ vô hạn, không trả kết quả sau một tick.

**Lỗi 6 — Dùng ErrorHandler `error -> {}` để giữ job “êm”.** Lỗi bị che hoàn toàn; không có log/metric/alert.

**Lỗi 7 — Có error log nhưng không giám sát lastSuccess.** Job có thể lỗi liên tục nhiều ngày mà chỉ tích lũy log.

**Lỗi 8 — Chỉ theo dõi lastStart.** Tick đều không chứng minh nghiệp vụ thành công.

**Lỗi 9 — Cập nhật lastSuccess ngay sau `submit()`.** Detached Future có thể thất bại sau đó hoặc không chạy.

**Lỗi 10 — Cho rằng Actuator scheduledtasks là health check cho business job.** Nó mô tả scheduled tasks, không tự xác nhận durable success.

**Lỗi 11 — Không xử lý “metric không tồn tại”.** Alert `time()-lastSuccess` không bắn nếu series vắng mặt.

**Lỗi 12 — Đặt watchdog trên cùng scheduler đang có thể bị block.** Scheduler chết → watchdog cũng chết.

**Lỗi 13 — Alert no-start sau 5 phút cho một cron chỉ chạy hằng ngày.** Bỏ qua expected calendar/timezone dẫn đến false positives.

**Lỗi 14 — Không xét startup grace, pause theo maintenance hay leader-only.** Cảnh báo sai và fatigue.

**Lỗi 15 — Bỏ qua executor rejection/discard.** Task có thể không được giao cho worker dù tick vẫn chạy.

**Lỗi 16 — Chạy không idempotent side effects nhiều lần để “bù lịch”.** Dễ gửi email/thu tiền hai lần.

**Lỗi 17 — Dựa vào in-memory heartbeat cho job cần bền vững.** Restart xóa trạng thái, khó phân biệt chưa chạy với đã chạy trước đó.

**Lỗi 18 — Mỗi lần tick không đặt timeout dependency.** Worker block lâu khiến lịch sau trễ, không thấy exception.

**Lỗi 19 — Tin cancellation/HTTP timeout đồng nghĩa business operation không xảy ra.** Downstream có thể đã commit; cần reconciliation.

**Lỗi 20 — Không test context, conditions và cả lần tick đầu tiên.** Một bean không được scan có thể im lặng ngay từ khi deploy.

## 19. Checklist phòng ngừa “mất lịch im lặng”

- [ ] Có danh mục **logical jobs bắt buộc**, period/cron/zone, business owner và SLO.
- [ ] Kiểm tra `@EnableScheduling`, bean registration và conditions ở đúng context.
- [ ] Biết khi nào job được disable chủ động (`cron="-"`, profile/feature flag) để không báo sai.
- [ ] Có registration/startup signal hoặc kiểm tra Actuator `scheduledtasks` phù hợp.
- [ ] Với raw JDK periodic schedule, không để exception thoát không kiểm soát mà không có terminal observation.
- [ ] Với Spring, hiểu `ErrorHandler` mặc định log/suppress, và luôn ghi nhận **lỗi nghiệp vụ** độc lập.
- [ ] Không viết ErrorHandler silent; có log/metric theo job name và error category.
- [ ] Theo dõi `lastStartedAt`, `lastFinishedAt`, `lastSuccessAt` và duration/active runs.
- [ ] Đánh giá freshness theo **cron/period, jitter, startup grace, leader ownership và business SLO**.
- [ ] Có alert về metric vắng mặt/target down, không chỉ so sánh series đã có.
- [ ] Watchdog/alert không phụ thuộc chính scheduler đang bị giám sát.
- [ ] Nếu tick giao việc sang executor khác, giám sát completion/failure của task thật.
- [ ] Timeout downstream, giới hạn queue, xử lý rejection và backpressure rõ ràng.
- [ ] Lưu trạng thái/tiến độ bền vững cho critical jobs, hỗ trợ retry/idempotency/reconciliation.
- [ ] Viết integration test về exception, no-start, no-success, stuck worker, shutdown và multi-replica.
- [ ] Có runbook phục hồi với bằng chứng business success, không chỉ restart process.

## 20. Câu hỏi tự kiểm tra

**Câu 1.** Một `ScheduledExecutorService.scheduleAtFixedRate` Runnable ném `RuntimeException` trong lần đầu. Điều gì xảy ra với các lượt tiếp theo?

**Đáp án:** Theo JDK contract, các lượt tiếp theo của registration đó bị suppress; Future hoàn tất bất thường, `get()` phát `ExecutionException`.

**Câu 2.** Với Spring `ThreadPoolTaskScheduler` repeating task mặc định, lỗi RuntimeException có luôn làm mất lịch?

**Đáp án:** Không. Spring mặc định thường log và suppress exception cho repeating tasks, để lịch tiếp tục. Cần xem handler custom và implementation thực tế.

**Câu 3.** `future.isDone()==true` nhưng `future.isCancelled()==false` là thành công?

**Đáp án:** Không. Với periodic Future, đó có thể là abnormal termination do exception; kiểm tra terminal cause qua `get()` khi done.

**Câu 4.** Endpoint `/actuator/scheduledtasks` thấy job. Có đủ kết luận job healthy không?

**Đáp án:** Không. Nó chứng minh được metadata/registration trong phạm vi endpoint, không bảo đảm tick đúng hạn hay business success.

**Câu 5.** Job có 200 tick, 0 success trong một giờ. Có thể coi vẫn healthy vì lịch chạy đều?

**Đáp án:** Không nếu SLO yêu cầu có kết quả thành công; phải cảnh báo từ `lastSuccessAt`/error-rate và xử lý nguyên nhân nghiệp vụ.

**Câu 6.** Vì sao cần alert `absent(...)` trong Prometheus?

**Đáp án:** Nếu series lastSuccess chưa được phát hoặc biến mất, phép so sánh độ tuổi trên series đó không tạo kết quả; absence alert phát hiện thiếu tín hiệu.

**Câu 7.** Một job cron chạy Chủ nhật 02:00 nhưng hiện là thứ Ba. Không có tick ba ngày là lỗi?

**Đáp án:** Không mặc nhiên. Phải tính expected next occurrence theo cron/timezone và khoảng maintenance/startup, không dùng ngưỡng fixed-period chung.

**Câu 8.** Scheduled method chỉ gọi `workExecutor.execute(...)` rồi return. Có được ghi lastSuccess ngay?

**Đáp án:** Không. Chỉ biết submission; worker có thể fail/reject/chưa chạy. Cần giám sát Future/job outcome thực.

**Câu 9.** Watchdog dùng cùng scheduler pool size 1 với một job có thể block vô hạn có đáng tin?

**Đáp án:** Không. Khi worker bị chiếm, watchdog cũng không chạy. Cần external monitoring hoặc một supervisor thực sự độc lập.

**Câu 10.** `ErrorHandler` được custom để log rồi `throw` lại exception. Có ảnh hưởng lịch không?

**Đáp án:** Có thể khiến periodic schedule chấm dứt theo behavior của underlying scheduler. Cần chọn policy có chủ đích và kiểm thử.

**Câu 11.** Một Spring reactive `@Scheduled` phát `onError`. ErrorHandler của `ThreadPoolTaskScheduler` có luôn nhận lỗi này?

**Đáp án:** Không. Reactive `onError` được Spring xử lý/log theo đường khác, các subscription tương lai vẫn có thể tiếp tục. Cần reactive error metrics riêng.

**Câu 12.** Một job cần xử lý đủ các bản ghi đến hạn sau restart. Chỉ dựa vào số tick đã lỡ có an toàn không?

**Đáp án:** Không. Lịch in-memory không là durable queue; cần job store/watermark và claim/idempotency để xử lý dữ liệu còn tồn.

## Kết luận

**Không để một công việc biến mất khỏi lịch mà không có tín hiệu quan sát** đòi hỏi nhiều hơn một `try/catch`. Với raw JDK, exception thoát khỏi periodic task có thể **chặn tất cả lượt kế tiếp**. Với Spring, repeating task thường được ErrorHandler **log rồi tiếp tục**, nhưng điều đó vẫn có thể che một chuỗi thất bại nghiệp vụ nếu chỉ quan sát scheduler.

Thiết kế đúng cần:

1. Chứng minh **job được đăng ký** và xác định instance/leader có quyền chạy.
2. Ghi **attempt, completion, failure và last durable success** riêng.
3. Cảnh báo **no-start, no-success, stuck, metric absence** theo đúng period/cron/SLO.
4. Đặt monitoring **ngoài cùng failure domain** của job.
5. Quản lý async work, retries, idempotency và recovery dựa trên durable state.

Khi đó, dù lỗi nằm ở annotation, executor, scheduler, downstream hay shutdown, hệ thống vẫn có đủ bằng chứng để phát hiện, cảnh báo và khôi phục công việc — thay vì chỉ phát hiện nhiều ngày sau khi dữ liệu đã cũ.

## Tài liệu tham khảo

1. Java SE 17 — `ScheduledExecutorService`, periodic failures suppress subsequent executions: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html
2. Java SE 17 — `ScheduledThreadPoolExecutor`, no-overlap, executor lifecycle: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html
3. Spring Framework 6.2 — `TaskUtils` error handlers for repeating/one-shot: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/TaskUtils.html
4. Spring Framework Reference — Task Execution and Scheduling / `@Scheduled`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
5. Spring Framework 6.2 — `Scheduled`, reactive method/onError semantics: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html
6. Spring Framework 6.2 — `ThreadPoolTaskScheduler` error handler and scheduler configuration: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html
7. Spring Boot 3.5 — Actuator scheduled tasks endpoint: https://docs.spring.io/spring-boot/3.5/api/rest/actuator/scheduledtasks.html
8. Spring Boot 3.5 — Actuator endpoints, exposure and security: https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html
9. Spring Framework 6.2 — `ScheduledTask` cancellation and next execution: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/config/ScheduledTask.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduling-failures-005">Phân biệt thất bại từng lần với trạng thái của cả lịch</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt thất bại từng lần với trạng thái của cả lịch

> **Module:** Spring Framework – concurrency  
> **Nhóm:** Lỗi của lịch chạy  
> **Trọng tâm:** Một lượt thực thi của `@Scheduled` hoặc `TaskScheduler` có thể thất bại nhưng **lịch định kỳ vẫn tiếp tục hoạt động**. Ngược lại, một lỗi được truyền ra ngoài bộ xử lý lỗi, một thao tác hủy, shutdown hoặc mất tiến trình có thể làm lịch không còn kích hoạt lần tiếp theo. Học cách phân biệt trạng thái của **attempt**, **occurrence**, **schedule registration** và **business job** để xử lý lỗi, theo dõi sức khỏe và cảnh báo đúng.

## 1. Quy tắc nền tảng: “lần chạy lỗi” không đồng nghĩa “lịch đã chết”

Giả sử mỗi phút Spring gọi method đồng bộ để đồng bộ hóa dữ liệu:

~~~java
@Scheduled(fixedRate = 60_000)
public void synchronize() {
    externalService.refresh(); // có thể ném exception
}
~~~

Đây là **snippet tích hợp Spring**, cần một bean được Spring quản lý, `@EnableScheduling` và dependency `externalService`. Nếu lần chạy lúc 10:00 thất bại, có ít nhất hai câu hỏi độc lập:

1. **Lượt chạy lúc 10:00 kết thúc thế nào?** `SUCCESS`, `FAILED`, `TIMED_OUT`, `SKIPPED` hay `CANCELLED`?
2. **Lịch đồng bộ mỗi phút còn được đăng ký không?** `ACTIVE`, `CANCELLED`, `TERMINATED`, `PAUSED` theo logic ứng dụng, hoặc đã mất vì process shutdown?

Một lần `FAILED` có thể đi cùng lịch `ACTIVE`. Khi đến 10:01, scheduler vẫn có thể gọi lại method. Ngược lại, lịch có thể `CANCELLED` sau một lần chạy `SUCCESS` vì quản trị viên chủ động tắt; đây không phải một business failure.

**Không lấy cờ “lịch đang bật” để suy ra lượt vừa rồi thành công; cũng không dùng một exception của lượt chạy để kết luận scheduler bị hỏng.** Đó là hai loại trạng thái có vòng đời và cách kiểm tra khác nhau.

## 2. Bốn lớp trạng thái cần tách rời

Một ứng dụng có lịch nền nên tách bốn tầng:

| Lớp | Ví dụ định danh | Câu hỏi chính | Trạng thái ví dụ |
| --- | --- | --- | --- |
| **Schedule definition** | `inventory-sync` | Có mong muốn kích hoạt định kỳ? | ENABLED, DISABLED, PAUSED |
| **Runtime registration** | `ScheduledFuture` của một JVM | Trigger có còn được đăng ký/chờ lần sau? | ACTIVE, CANCELLED, DONE_EXCEPTIONALLY, SHUTDOWN |
| **Scheduled occurrence** | `inventory-sync@2026-10-29T10:00Z` | Lượt theo lịch nào đang được xử lý? | DUE, RUNNING, SUCCEEDED, FAILED, MISSED |
| **Execution attempt** | occurrence ID + attempt number | Nỗ lực thứ mấy, kết quả ra sao? | STARTED, SUCCEEDED, FAILED, TIMED_OUT |

Một occurrence có thể có **nhiều attempt** khi chính sách retry được triển khai chủ động. Một schedule có thể tạo **nhiều occurrence**. `ScheduledFuture` quản lý vòng đời đăng ký trên scheduler, không tự lưu từng dòng execution history trong database.

Với `@Scheduled` đơn giản, framework không cung cấp mặc định một job ledger bền vững gồm cả bốn lớp. Ứng dụng phải tự đo, lưu trạng thái hoặc dùng job/workflow infrastructure phù hợp khi cần audit, retry và phục hồi.

Trong một tiến trình, `ScheduledFuture.isDone()` cũng không thể thay thế trường `lastRunStatus`: future của tác vụ lặp vẫn chưa hoàn thành sau mỗi tick thành công hoặc thất bại đã được handler nuốt.

## 3. Luồng thời gian: lỗi lúc 10:00, lịch vẫn sống

Với một recurring scheduler có `ErrorHandler` **ghi nhận rồi không ném lại** exception:

~~~text
10:00:00  scheduler bắt đầu occurrence #1
10:00:02  callback ném IllegalStateException
10:00:02  ErrorHandler ghi nhận lỗi, không throw
10:00:02  occurrence #1 = FAILED
          schedule registration = ACTIVE
10:01:00  scheduler kích hoạt occurrence #2
10:01:01  callback hoàn tất bình thường
10:01:01  occurrence #2 = SUCCEEDED
          schedule registration = ACTIVE
10:02:00  scheduler có thể tiếp tục occurrence #3...
~~~

`ErrorHandler` có thể ngăn exception truyền tới bộ máy periodic scheduling, nên **một lần thất bại không tự kết thúc tất cả các lần tương lai**. Với `ThreadPoolTaskScheduler` trong Spring, cơ chế mặc định cho tác vụ lặp thường là **log rồi suppress** (xem mục 5).

Tuy nhiên đây không phải một chính sách retry toàn diện. Lượt lúc 10:01 là **lượt định kỳ tiếp theo**, không tự động là attempt #2 của cùng occurrence 10:00. Nếu 10:00 phải cập nhật một dữ liệu bắt buộc, việc chạy lượt 10:01 chưa chứng minh dữ liệu của 10:00 đã được khôi phục.

Người vận hành phải nhìn cả **failure count** và **last successful occurrence**. Một lịch vẫn `ACTIVE` nhưng thất bại 100 lần liên tiếp là vấn đề nghiêm trọng, dù scheduler vẫn đang “chạy bình thường”.

## 4. Luồng thời gian ngược lại: exception thoát ra, lịch có thể kết thúc

Một executor periodic ở mức JDK có thể dừng những lần chạy sau khi callback để exception thoát ra. Javadoc `ScheduledExecutorService.scheduleAtFixedRate` và `scheduleWithFixedDelay` nói rằng các lần tiếp theo bị ngăn sau khi một lần thực thi phát sinh exception.

~~~text
10:00:00  periodic future đang ACTIVE
10:00:01  callback ném IllegalStateException
10:00:01  exception thoát tới periodic scheduler
10:00:01  periodic future kết thúc exceptionally
10:01:00  không còn tick tiếp theo của registration đó
10:02:00  vẫn không có tick tiếp theo
~~~

Với Spring, `ErrorHandler` hoặc một wrapper có thể thay đổi đường đi của exception. Nếu handler **ném lại** thì recurring future có thể kết thúc như trên. Nếu handler **ghi nhận nhưng suppress**, periodic engine không nhận exception đó và tiếp tục đăng ký.

Do vậy câu “`@Scheduled` ném lỗi là luôn dừng lịch” **sai nếu không nói rõ Spring handler và kiểu scheduling**. Câu ngược lại “`@Scheduled` lỗi bao nhiêu lần cũng chắc chắn tiếp tục” cũng **sai trong mọi cấu hình**, vì ứng dụng có thể chọn propagating handler, trực tiếp hủy lịch, hoặc scheduler/process đã dừng.

## 5. Mặc định Spring khác với periodic executor trần của JDK

Ở **Spring Framework 6.2.x**, các task lập lịch qua `ThreadPoolTaskScheduler` sử dụng cơ chế `TaskUtils` để xử lý lỗi theo tính chất công việc:

| Đường thực thi | Xử lý lỗi thông thường | Tác động có thể quan sát |
| --- | --- | --- |
| Spring recurring (`@Scheduled` fixedRate/fixedDelay/cron hay `TaskScheduler` định kỳ) | Default handler **LOG_AND_SUPPRESS_ERROR_HANDLER** | Ghi log lỗi, không truyền exception tới periodic engine; những lần chạy sau vẫn có thể được lên lịch |
| Spring one-shot qua scheduler | Default handler thường **LOG_AND_PROPAGATE_ERROR_HANDLER** | Lỗi được ghi log rồi propagate; future một lần có thể kết thúc exceptionally |
| JDK `ScheduledExecutorService` periodic trực tiếp, callback ném exception | JDK ngăn các lần periodic tiếp theo | Returned scheduled future terminal exceptionally |
| Spring recurring với `ErrorHandler` tùy chỉnh **rethrow** | Exception rời error handler | Có thể làm registration periodic kết thúc exceptionally |
| Spring recurring với handler tùy chỉnh **suppress** | Handler giữ lỗi và return bình thường | Registration tiếp tục, dù occurrence đã thất bại |
| Scheduler bị shutdown, future bị cancel | Không phụ thuộc lượt gần nhất thành công/lỗi | Registration ngừng kích hoạt theo lifecycle/cancellation |

`@Scheduled` cũng có các trường hợp xử lý reactive return type trên Spring 6.1+, với quy tắc xử lý onError khác với method imperative `void`; bài này tập trung vào **imperative callback** và `TaskScheduler` dùng `Runnable`. Đừng áp nguyên một bảng kết luận cho mọi reactive publisher mà không kiểm tra contract Spring version tương ứng.

Cảnh báo quan trọng: **suppress exception không có nghĩa business attempt đã thành công**. Nó chỉ quyết định exception có được truyền đến tầng scheduling hay không. Phải có logging/metrics/trạng thái nghiệp vụ riêng để nhận diện lần chạy lỗi.

## 6. Lỗi của callback khác lỗi đăng ký và lỗi hạ tầng

Có ít nhất năm loại lỗi khác nhau:

1. **Business execution failure:** callback đang chạy ném exception khi truy vấn DB, gọi HTTP, validate hoặc xử lý dữ liệu. Một occurrence có thể `FAILED` trong khi lịch còn đăng ký.
2. **Registration/submission failure:** code gọi `TaskScheduler.schedule(...)` nhưng scheduler đang shutdown hoặc từ chối; có thể nhận `TaskRejectedException`. Đây là **không đăng ký được lịch**, không phải một lượt callback đã chạy thất bại.
3. **Trigger failure:** một `Trigger` tùy biến tính `nextExecution()` sai, ném exception hoặc không còn thời điểm tiếp theo; không nhất thiết đồng nghĩa business method đã chạy.
4. **Runtime lifecycle failure:** context shutdown, JVM crash, container bị kill, scheduler worker bị mất. Không có `ScheduledFuture` cục bộ nào bền qua restart.
5. **Observability/reconciliation failure:** callback chạy thành công nhưng ghi trạng thái vào DB lỗi, hoặc broker ACK bị mất. Trạng thái ứng dụng có thể **không chắc chắn**, không được tự gán `SUCCESS` chỉ vì method không ném exception.

Khi cảnh báo, hãy phân loại ít nhất **`registration_error`**, **`execution_error`**, **`trigger_error`**, **`shutdown`**, **`state_persistence_error`**. Đối với SLA nghiêm ngặt, số lần scheduled fire bị bỏ lỡ cũng cần được theo dõi riêng.

## 7. Runnable demo Java 17 + Spring Framework 6.2.9: hai chính sách ErrorHandler

Bài thực hành tạo hai `ThreadPoolTaskScheduler` độc lập để so sánh rõ chính sách:

- **Suppress:** lần thực thi đầu tiên ném lỗi; `ErrorHandler` ghi nhận và **không ném lại**, nên lần thứ hai vẫn chạy.
- **Propagate:** callback ném lỗi và `ErrorHandler` ném lại; periodic future kết thúc exceptionally ngay sau lần đầu.
- Chương trình dùng `CountDownLatch` để đồng bộ, không dựa vào `Thread.sleep` may rủi; `Future.get` trên future lỗi chứng minh trạng thái terminal.
- Không cần Spring Boot, database, broker hay network. Demo có đủ `pom.xml` và class Java để chạy trên JDK 17+.

### Cấu trúc project

~~~text
schedule-failure-lifecycle-demo/
├── pom.xml
└── src/main/java/demo/ScheduleFailureLifecycleDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>schedule-failure-lifecycle-demo</artifactId>
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
        <version>3.13.0</version>
        <artifactId>maven-compiler-plugin</artifactId>
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

### src/main/java/demo/ScheduleFailureLifecycleDemo.java

~~~java
package demo;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class ScheduleFailureLifecycleDemo {
    static ThreadPoolTaskScheduler newScheduler(String prefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.initialize();
        return scheduler;
    }

    public static void main(String[] args) throws Exception {
        ThreadPoolTaskScheduler suppress = newScheduler("suppress-");
        ThreadPoolTaskScheduler propagate = newScheduler("propagate-");

        try {
            AtomicInteger attempts = new AtomicInteger();
            CountDownLatch failureLogged = new CountDownLatch(1);
            CountDownLatch laterSuccess = new CountDownLatch(1);
            suppress.setErrorHandler(error -> failureLogged.countDown());

            ScheduledFuture<?> recurring = suppress.scheduleAtFixedRate(() -> {
                int n = attempts.incrementAndGet();
                if (n == 1) {
                    throw new IllegalStateException("first attempt failed");
                }
                if (n == 2) {
                    laterSuccess.countDown();
                }
            }, Duration.ofMillis(100));

            boolean failedAttempt = failureLogged.await(3, TimeUnit.SECONDS);
            boolean succeedingAttempt = laterSuccess.await(3, TimeUnit.SECONDS);
            boolean scheduleStillActive = !recurring.isDone();
            boolean cancelled = recurring.cancel(false);

            System.out.println("Failure recorded: " + failedAttempt);
            System.out.println("Next attempt succeeded: " + succeedingAttempt);
            System.out.println("Schedule survived failure: " + scheduleStillActive);
            System.out.println("Schedule cancelled: " + cancelled);

            AtomicInteger stoppedAttempts = new AtomicInteger();
            propagate.setErrorHandler(error -> {
                throw new IllegalStateException("Stop periodic schedule", error);
            });
            ScheduledFuture<?> stopped = propagate.scheduleAtFixedRate(() -> {
                stoppedAttempts.incrementAndGet();
                throw new IllegalStateException("failed job execution");
            }, Duration.ofMillis(100));

            boolean observableFailure = false;
            try {
                stopped.get(3, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                observableFailure = true;
            }

            System.out.println("Failure propagated to future: " + observableFailure);
            System.out.println("Propagated schedule done: " + stopped.isDone());
            System.out.println("Stopped after first attempt: " +
                (stoppedAttempts.get() == 1));
        } finally {
            suppress.shutdown();
            propagate.shutdown();
        }
    }
}
~~~

Chạy trong thư mục project bằng JDK 17+ và Maven:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.ScheduleFailureLifecycleDemo
~~~

**Stdout đã được kiểm tra thực tế:**

~~~text
Failure recorded: true
Next attempt succeeded: true
Schedule survived failure: true
Schedule cancelled: true
Failure propagated to future: true
Propagated schedule done: true
Stopped after first attempt: true
~~~

Mã được biên dịch ở mức **`--release 17`** và thực thi thành công với **Spring Framework 6.2.9** trên JDK mới hơn. Bảy dòng stdout được đối chiếu đúng. `ThreadPoolTaskScheduler` có hai instance độc lập, mỗi instance dùng một scheduler thread và một error handler riêng.

**Không nhầm việc test với hệ thống retry:** lần thứ hai ở nhánh suppress là một lần thực thi periodic tiếp theo. Ví dụ không gắn occurrence ID, không retry lại payload của lần đầu và không lưu trạng thái bền vững. Muốn triển khai production, cần bổ sung execution ledger và chính sách khôi phục như các mục tiếp theo.

## 8. Giải thích bản chất của hai tương tác ErrorHandler

Trong nhánh **suppress**, `suppress.setErrorHandler(error -> failureLogged.countDown())` nhận exception và return bình thường. Exception không đi ra ngoài error-handling wrapper để kết thúc periodic executor. Lượt #1 có lỗi, nhưng `ScheduledFuture.isDone()` lúc kiểm tra vẫn là `false`. Sau lượt #2 thành công, chương trình gọi `cancel(false)` để chấm dứt registration một cách **chủ động**.

Trong nhánh **propagate**, `ErrorHandler` ném một `IllegalStateException` mới có cause là exception ban đầu. Future của periodic schedule kết thúc exceptionally: `stopped.get(...)` ném `ExecutionException`, `stopped.isDone()` trả `true`, và counter cho thấy callback chạy đúng **một lần**. Do kết thúc ở tầng scheduling, không còn các lượt tiếp theo của registration đó.

Vì sao không gọi `get()` trên recurring future còn `ACTIVE`? Một `ScheduledFuture` periodic thông thường **không hoàn tất thành công sau mỗi tick**. `get()` có thể chờ vô thời hạn cho đến khi future bị hủy hoặc kết thúc bất thường. Đừng viết endpoint quản trị gọi `future.get()` với kỳ vọng nhận “kết quả lượt gần nhất”.

Khi `cancel(false)` được chấp nhận, future có thể chuyển sang trạng thái done/cancelled; đó **không** biến business attempts đã xảy ra thành `FAILED` hoặc `SUCCESS`. Cancellation là trạng thái vòng đời scheduling khác hẳn.

**Độ chính xác về testing:** latch có timeout 3 giây để tránh treo test; môi trường bị scheduler starvation hoặc JVM pause nặng có thể không đủ điều kiện in các giá trị `true` dù API hoạt động đúng. Đây là kiểm thử hành vi trên môi trường bình thường, không phải hợp đồng hard real-time.

## 9. Default ErrorHandler không thay thế chính sách giám sát

Spring cung cấp handler mặc định **log rồi suppress** cho recurring tasks để một exception tạm thời không vô tình tắt lịch dài hạn. Nhưng nó không tự tạo những thứ sau:

- Một database record `FAILED` cho occurrence cụ thể.
- Một số đếm `consecutiveFailures` và thời điểm `lastSuccessAt`.
- Một cảnh báo “job không có lần thành công trong 2 giờ”.
- Một dead-letter queue hoặc retry có backoff.
- Một idempotency key cho side effect đã được thực hiện một phần.
- Một recovery job sau process restart hoặc task bị skip vì scheduler không có tài nguyên.

Log có thể bị lọc, bị mất trong quá trình restart hoặc không đủ cấu trúc để tổng hợp theo business occurrence. Vì vậy nếu kết quả mỗi lần chạy quan trọng, hãy thiết kế **execution result tracking** trong chính job boundary (hoặc instrumentation) thay vì chỉ dựa vào stack trace của scheduler.

Một `ErrorHandler` custom được cấu hình qua `ThreadPoolTaskScheduler.setErrorHandler` áp dụng ở tầng scheduler. Không giả định handler biết sẵn tên business job, tenant ID, occurrence time hay retry attempt; các thông tin đó phải được truyền rõ hoặc thêm qua wrapper/record.

## 10. Mô hình dữ liệu tối thiểu để lưu trạng thái thực thi

Ví dụ thiết kế bảng (SQL **minh họa**, cú pháp chi tiết tùy DB):

~~~sql
CREATE TABLE job_schedules (
    job_id VARCHAR(128) PRIMARY KEY,
    cron_expression VARCHAR(128),
    zone_id VARCHAR(128),
    enabled BOOLEAN NOT NULL,
    revision BIGINT NOT NULL,
    last_success_at TIMESTAMP NULL,
    last_failure_at TIMESTAMP NULL
);

CREATE TABLE job_occurrences (
    occurrence_id VARCHAR(160) PRIMARY KEY,
    job_id VARCHAR(128) NOT NULL,
    scheduled_for TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    last_error_code VARCHAR(80) NULL
);
~~~

Đây không phải migration production áp dụng nguyên xi: cần loại timestamp có timezone/UTC phù hợp, FK/index, retention, partitioning và ràng buộc concurrency tùy DB. Với job nhiều tenant, khóa business occurrence có thể gồm `(job_id, business_date, schedule_revision)` hoặc một ID ổn định theo policy sản phẩm.

**Không chỉ lưu `status=FAILED` trên dòng `job_schedules`** rồi ghi đè thành `SUCCESS` ở lượt sau, vì như vậy mất khả năng trả lời lượt nào đã lỗi và có cần đối soát hay không. Dòng schedule nên biểu diễn **cấu hình/desired state và health summary**, còn occurrences biểu diễn lịch sử kết quả cụ thể.

Nếu mỗi occurrence có nhiều retry, tạo thêm `job_attempts` hoặc lưu attempt records có khóa `(occurrence_id, attempt_number)`. Tránh ghi cùng một trạng thái từ hai worker không có khóa/optimistic locking.

## 11. “Lần sau chạy thành công” không tự sửa “lần trước thất bại”

Ví dụ nightly billing chạy lúc 00:00 ngày 29/10 nhưng thất bại vì DB timeout. Ngày 30/10 cron kích hoạt bình thường và xử lý “hôm nay”. Nếu cả hai dùng `LocalDate.now()`, ngày 29/10 có thể **chưa bao giờ được xử lý**.

~~~text
Business date      Kết quả lịch                  Kết quả dữ liệu
2026-10-29         occurrence FAILED            Chưa chốt sổ
2026-10-30         occurrence SUCCEEDED         Chỉ chốt sổ ngày 30
schedule           ACTIVE                       Không đảm bảo đủ hai ngày
~~~

Muốn bảo đảm không mất business occurrence, phải lưu `businessDate`, `occurrenceId`, trạng thái completion và tạo **catch-up/reconciliation** cho các ngày chưa hoàn tất. Đừng coi việc cron tiếp tục là chính sách retry có đủ dữ liệu nghiệp vụ.

Ngược lại, một job cache refresh có thể chỉ cần trạng thái mới nhất; bỏ qua một lượt và thử lại vào tick kế tiếp là hợp lý. **Policy phụ thuộc business semantics**, không có một cách “cứ retry” đúng cho mọi lịch.

Cần phân biệt `fixedRate`, `fixedDelay` và `cron`: thời điểm lượt tiếp theo phụ thuộc loại trigger và completion history, không phải mặc định retry ngay khi exception xảy ra. Với `fixedDelay`, lần sau được tính theo lúc lượt trước hoàn tất cộng delay; một lỗi được suppress vẫn là một **lần completion** của callback đã được handler xử lý.

## 12. Quyết định lỗi: suppress, stop, pause hay retry?

| Loại công việc / lỗi | Hướng xử lý gợi ý | Lý do |
| --- | --- | --- |
| Cache refresh, metric polling gặp HTTP 503 một lần | Ghi FAILED, giữ lịch và thử lại ở lượt định kỳ sau | Kết quả mới có thể thay thế kết quả cũ |
| Batch cần xử lý đủ mọi ngày nghiệp vụ | Ghi failed occurrence, retry/backfill có ID | Lượt cron tiếp theo không thay thế ngày đã bỏ lỡ |
| Validation cấu hình sai nghiêm trọng | Đánh dấu `PAUSED` hoặc `ERROR`, báo vận hành; không lặp vô hạn | Lỗi không tự chữa bằng tick tiếp theo |
| DB tạm quá tải | Bounded backoff, giới hạn concurrency/retry, giám sát | Retry liên tục có thể làm quá tải nặng hơn |
| Lỗi permission hoặc credential bị thu hồi | Escalate, tránh spam; có thể pause bằng policy | Lặp nhanh thường không giúp |
| Side effect thanh toán không rõ đã thực hiện hay chưa | Idempotency key + reconciliation trước retry | Không được nhân đôi tác dụng phụ |
| Worker bị shutdown | Không coi là execution failure một cách mù quáng | Cần phân biệt interrupt do shutdown với lỗi nghiệp vụ |
| Scheduler registration bị reject | Báo lỗi lifecycle/registration, không nói “attempt đã thất bại” | Callback chưa bắt đầu |

**Không bắt mọi `Throwable` rồi suppress vô điều kiện.** `Error` có thể báo vấn đề nghiêm trọng của JVM; việc tiếp tục lặp vô hạn tùy tiện không phải giải pháp an toàn. Spring `ErrorHandler` nhận `Throwable`, nhưng business retry cần phân loại lỗi và có đường escalation.

Một chính sách sản xuất có thể giữ lịch registration `ACTIVE` đồng thời đặt business state `DEGRADED` sau N lần thất bại liên tiếp. `DEGRADED` là state **do ứng dụng định nghĩa**, không phải trạng thái chuẩn của `ScheduledFuture`.

## 13. Theo dõi trạng thái per-attempt bằng wrapper

Một service có thể bọc tác vụ để ghi thành công/thất bại **mỗi lần** (mã Java **tích hợp**, không chạy độc lập vì cần kho lưu kết quả và runnable nghiệp vụ):

~~~java
public Runnable observed(
        String jobId, Runnable businessWork, JobRunRecorder recorder) {
    return () -> {
        String attemptId = recorder.start(jobId);
        try {
            businessWork.run();
            recorder.succeeded(attemptId);
        } catch (RuntimeException ex) {
            recorder.failed(attemptId, ex.getClass().getSimpleName());
            throw ex; // Để ErrorHandler của scheduler áp policy.
        }
    };
}
~~~

Đoạn này cần một interface `JobRunRecorder` do hệ thống triển khai với ghi trạng thái bền vững, uniqueness và error handling thích hợp. `recorder.succeeded` cũng có thể lỗi **sau khi business effect đã hoàn tất**; không được mặc định retry business effect nếu trạng thái recorder không ghi được.

**Vị trí đếm lỗi:** Nếu wrapper ghi `FAILED` rồi ném lại, `ErrorHandler` cũng có thể log, cần correlation ID để không đếm một lỗi thành hai executions. Có thể đặt metrics ở một boundary duy nhất, còn handler lo policy scheduling.

**Vấn đề ngoại lệ khi ghi trạng thái:** Nếu `recorder.failed()` lại ném exception, nó có thể che exception business ban đầu. Trong production nên bảo toàn original cause, log lỗi ghi trạng thái riêng và có recovery/reconciliation. Không để việc ghi metric tạo thêm side effect không kiểm soát.

Nếu cần status `TIMED_OUT`, đừng chỉ dựa vào `Future.orTimeout`: nó không bảo đảm tác vụ blocking bên dưới đã dừng. Ghi rõ `caller timed out` khác `work interrupted` và `remote effect committed`.

## 14. ScheduledFuture phản ánh registration, không phản ánh mọi tick

Khi bạn dùng `TaskScheduler` trực tiếp:

~~~java
ScheduledFuture<?> recurring =
    taskScheduler.scheduleAtFixedRate(
        () -> synchronize(),
        Duration.ofMinutes(1));
~~~

Đây là snippet tích hợp, cần `taskScheduler` và `synchronize`. Một future recurring hoạt động khác với future one-shot:

- `recurring.isDone() == false` không có nghĩa tick cuối thành công; nó thường có nghĩa registration chưa terminal.
- `recurring.get()` **không phải API lấy kết quả của tick cuối**. Khi recurring tiếp tục, nó thường không complete bình thường; `get()` có thể chờ dài vô hạn.
- `recurring.isCancelled() == true` cho biết future đã bị cancel, không khẳng định một callback đã chạy trước đó đã rollback.
- `recurring.isDone() == true` có thể vì **hủy** hoặc kết thúc **do lỗi được propagate**; phải kiểm tra nguyên nhân, không gộp mọi done thành `FAILED`.
- Với `schedule(Runnable, Instant)` one-shot, future hoàn tất sau **một lần** dù thành công hay lỗi; nó không có “lượt tiếp theo” để duy trì.
- Một số overload `schedule(Runnable, Trigger)` có thể trả `null` nếu trigger không có thời điểm chạy tiếp; điều này là trạng thái registration/trigger, không phải callback đã thất bại.

Để biết lần nào thành công, cần **execution log/metric/ledger** riêng. Nếu API quản trị muốn trả `scheduleActive` và `lastRunSuccess`, đó là hai field tách biệt. Trạng thái chính xác trong nhiều JVM đòi hỏi nguồn sự thật dùng chung, không chỉ local future.

## 15. @Scheduled, @Async và direct TaskScheduler: lỗi đi đâu?

| Cơ chế | Nơi exception được quan sát | Nhận xét |
| --- | --- | --- |
| `@Scheduled` imperative đồng bộ | Scheduler `ErrorHandler`/logging; không có caller HTTP chờ kết quả tick | Không tự trả exception của tick về request từng gọi API ở một thời điểm khác |
| `TaskScheduler.scheduleAtFixedRate` trực tiếp | `ErrorHandler` và `ScheduledFuture` nếu bị terminal | Có handle để cancel/inspect đăng ký |
| `@Async void` | `AsyncUncaughtExceptionHandler` nếu lời gọi đi qua Spring async proxy | Đây **không** phải mặc định cùng cơ chế như scheduled error handler |
| `@Async CompletableFuture<T>` | Returned future exceptional completion | Caller có thể `join/get/whenComplete` |
| `@Scheduled` method gọi `@Async` service | Scheduler tick có thể chỉ nộp việc; lỗi async xảy ra sau | Phải giám sát cả submission và background future/void |
| `ScheduledExecutorService` JDK trực tiếp | Future returned và behavior JDK khi exception thoát callback | Không áp mặc định Spring ErrorHandler nếu không dùng wrapper Spring |

Trường hợp kết hợp `@Scheduled` và `@Async` dễ gây đánh giá sai: method scheduled có thể hoàn tất nhanh sau khi submit một async job; scheduler coi tick đã return, nhưng công việc downstream có thể thất bại sau đó trên một thread khác. Một `ErrorHandler` của scheduler **không đương nhiên nhận** mọi exception phát sinh trong future async con.

Nên có một **business job result** theo occurrence, được cập nhật bởi worker thực sự hoàn tất side effect. Không gắn nhãn `SUCCESS` ngay khi task vừa được enqueue nếu nghiệp vụ đòi hỏi đã xử lý xong.

## 16. Transaction và side effect: failure phải được đánh giá ở đúng ranh giới

Một scheduled job ghi dữ liệu trong `@Transactional` có thể thất bại giữa chừng. Lựa chọn xử lý exception ảnh hưởng rollback và ý nghĩa trạng thái:

~~~java
@Scheduled(cron = "0 0 1 * * *", zone = "UTC")
public void nightly() {
    billingService.closeBusinessDay();
}
~~~

Đây là snippet Spring tích hợp; `billingService` có thể có một `@Transactional` method được gọi qua Spring proxy. Nếu service ném `RuntimeException`, transaction interceptor mặc định thường rollback theo rollback rule; scheduler sẽ nhận exception khi nó đi ra ngoài boundary.

**Lỗi phổ biến:** bên trong transaction bắt `RuntimeException`, log rồi return bình thường. Khi đó transaction manager có thể nhìn thấy method kết thúc bình thường và **commit**, mặc dù logic đã đánh dấu “lỗi”. Cần quyết định chính sách rollback: ném lại lỗi, cấu hình rollback rules hoặc `setRollbackOnly()` theo tình huống.

Một DB timeout/HTTP timeout cũng không khẳng định remote side effect đã bị hoàn tác. Một request thanh toán có thể thành công ở remote nhưng client không nhận được response; trạng thái occurrence nên là `UNKNOWN`/needs reconciliation nếu không thể kết luận.

Với side effect quan trọng, dùng idempotency key theo business occurrence, transaction boundaries và outbox/reconciliation. Không cho rằng một attempt `FAILED` luôn đồng nghĩa “không có thay đổi nào xảy ra”.

## 17. Retry của cùng occurrence khác tick kế tiếp

Giả sử cron chạy lúc 02:00 mỗi ngày; job của ngày 29/10 thất bại. Có ba cơ chế có ý nghĩa khác nhau:

1. **Next scheduled tick:** 02:00 ngày 30/10, bình thường một occurrence mới.
2. **Retry same occurrence:** thử lại occurrence 29/10 sau backoff, giữ nguyên business ID/idempotency key, tăng attempt number.
3. **Catch-up/backfill:** scanner phát hiện ngày 29/10 chưa complete và tạo work item để xử lý nốt, có thể cùng logic retry nhưng thường dựa vào persisted ledger.

~~~text
schedule: nightly 02:00
occurrence: job=closing, businessDate=2026-10-29
  attempt #1 -> FAILED (transient DB timeout)
  attempt #2 -> SUCCEEDED (after bounded backoff)
occurrence: job=closing, businessDate=2026-10-30
  attempt #1 -> SUCCEEDED

schedule state: ACTIVE suốt các mốc trên
~~~

Để không nhân side effect: cùng occurrence retry phải dùng một **business key ổn định**. Lượt cron mới có key khác, nhưng cả hai vẫn phải tôn trọng phiên bản nghiệp vụ nếu cùng tác động một aggregate.

Khi retry, cần **max attempts, exponential backoff + jitter, deadline và giới hạn concurrency**. Nếu cùng lúc cron next tick và retry của occurrence cũ chạy, phải xác định ordering, lock và idempotency. Nếu đang fail do DB quá tải, retry nóng ngay có thể làm tình hình nghiêm trọng hơn.

## 18. Khi nào chủ động tạm dừng hoặc dừng lịch sau nhiều lỗi?

Một policy có thể quyết định **đóng lịch** khi lỗi không thể phục hồi:

~~~text
ON occurrence finished:
  if SUCCEEDED:
      consecutiveFailures = 0
      lastSuccessAt = now
  else if FAILED:
      consecutiveFailures += 1
      lastFailureAt = now

  if consecutiveFailures >= threshold:
      desiredScheduleState = PAUSED
      alert operator
      request local ScheduledFuture cancellation
      persist pause reason and revision
~~~

Đây là **pseudocode thiết kế**, không phải tính năng tự có của `@Scheduled` hay `ThreadPoolTaskScheduler`. Nếu app chỉ dùng `@Scheduled` khai báo, không có handle business-job riêng để gọi `cancel()` theo từng job; có thể dùng dynamic scheduling registry, guard theo flag hoặc job scheduler có state để triển khai pause.

**Đừng nhầm pause với stop do exception:** hủy future qua quản trị là một thao tác lifecycle có chủ ý. Nếu worker đã bắt đầu, `cancel(false)` không tự interrupt hoặc rollback. Có thể vẫn còn in-flight work dù lịch không tạo tick mới.

Lựa chọn threshold cần căn cứ SLO và mức quan trọng. Một job reconcile có thể tiếp tục với circuit breaker/backoff để tự hồi phục; một job gặp lỗi cấu hình vĩnh viễn có thể nên pause và yêu cầu can thiệp. Hệ thống nên có đường **resume có kiểm soát**, audit và reconciliation để không bỏ quên công việc bị pause.

## 19. Failure và schedule health trong nhiều replica

Với ba ứng dụng Spring cùng bật một cron, mỗi instance có thể đăng ký một local future:

~~~text
Replica A: schedule ACTIVE, occurrence 10:00 FAILED
Replica B: schedule ACTIVE, occurrence 10:00 SUCCEEDED
Replica C: process RESTARTING, không chạy occurrence 10:00
~~~

Không thể dùng `isDone()` của A để kết luận **toàn hệ thống** đang healthy hay crashed. Đồng thời, việc B thành công có thể che giấu duplicate side effect nếu cả hai cố xử lý cùng business occurrence.

Cần định nghĩa **ownership** và **aggregate result**:

- Nếu công việc được phép chạy ở mỗi instance (ví dụ cache local), outcome A/B/C là ba lượt hợp lệ, nên metric gắn instance/service.
- Nếu công việc chỉ được thực hiện **một lần toàn cluster**, cần distributed lease/lock, job store hoặc queue có quyền sở hữu và idempotency.
- Nếu owner A mất giữa lúc chạy, lease hết hạn không bảo đảm task cũ đã bị hủy. Fencing token ở DB/downstream có thể cần để ngăn stale worker commit.
- Một occurrence có thể được chạy lại sau failover; thiết kế dedupe khi nhận lại công việc.

**State chung phải có nguồn dữ liệu authoritative**. Một `Map<jobId,ScheduledFuture>` là local runtime view, không phải global business schedule database. Với workflow quan trọng, nên lưu desired job state/occurrence ledger trong DB rồi reconcile sau restart.

## 20. Logging, metrics và alerting để không bị đánh lừa bởi “scheduler còn chạy”

Cần phân tách quan sát **kết quả từng lần** và **sức khỏe lịch**:

| Metric / signal | Đo cái gì |
| --- | --- |
| `job_attempts_total{result}` | Số nỗ lực thành công/thất bại/timeout |
| `job_occurrences_total{status}` | Số occurrence hoàn tất, bị bỏ lỡ, đang chạy |
| `job_consecutive_failures` | Chuỗi thất bại liên tiếp của business job |
| `job_last_success_timestamp_seconds` | Thời điểm hoàn tất thành công gần nhất |
| `job_last_failure_timestamp_seconds` | Thời điểm thất bại gần nhất |
| `schedule_registration_active` | Trạng thái local registration; không thay kết quả business |
| `schedule_registration_errors_total` | Lỗi tạo mới/đăng ký lịch |
| `schedule_fires_expected_vs_started` | Phát hiện missed executions / scheduler lag |
| `job_duration_seconds` | Độ dài execution, bao gồm phần I/O |
| `job_retry_attempts_total` | Số attempt thêm, không nhầm với tick mới |
| `job_overdue_occurrences` | Các lượt đã đến hạn nhưng chưa đạt terminal business outcome |

Những metric là **mẫu đặt tên**, không phải mọi metric đều có sẵn trong Spring Actuator. Hệ thống phải tự instrument hoặc tích hợp monitoring/job library tương ứng.

**Không dùng raw order ID, user ID hoặc occurrence ID làm Prometheus label** vì cardinality cao và rủi ro lộ dữ liệu. Ghi các ID đó trong structured logs/traces với chính sách bảo mật; metric label nên giữ hữu hạn như `job_type`, `result`, `environment`, `error_class` đã được chuẩn hóa.

Alert nên có nhiều lớp:

- **Một lần lỗi quan trọng:** alert theo severity của business job.
- **N lần thất bại liên tiếp:** phát hiện scheduler vẫn active nhưng kết quả luôn lỗi.
- **Không có lần thành công trong SLO:** phát hiện job bị dead, bị pause hoặc lỗi liên tục.
- **Đến hạn nhưng không có attempt:** phát hiện registration/shutdown/queue/scheduler starvation.
- **Nhiều lần chạy cùng business key:** phát hiện duplicate do nhiều replica, retry hoặc restart.

## 21. Bộ kiểm thử lỗi lịch chạy

Nên kiểm thử bằng `CountDownLatch`, fake clock hoặc injected scheduler thay vì `Thread.sleep(10)` phụ thuộc timing:

1. **ErrorHandler suppress:** callback lỗi ở lần đầu, handler đếm lỗi và return, lần hai được chạy, future còn active; như demo.
2. **ErrorHandler propagate:** callback lỗi một lần, handler ném lại, `Future.get()` báo `ExecutionException`, không có tick tiếp theo.
3. **One-shot failure:** `schedule(Runnable, Instant)` ném lỗi, future kết thúc; không suy luận “lịch sẽ retry”.
4. **Cancel active periodic:** `cancel(false)` ngăn lần sau, state schedule cancel nhưng previous attempt vẫn ghi nguyên status.
5. **Cancel while running:** latch giữ callback đã bắt đầu; cancel không ngầm rollback side effect.
6. **Scheduler registration rejection:** shutdown scheduler trước khi đăng ký, xác minh code xử lý `TaskRejectedException` và không ghi nhận occurrence `FAILED` nếu chưa chạy.
7. **Business failure suppressed internally:** callback catch lỗi rồi return; xác minh recorder vẫn ghi `FAILED`, không chỉ dựa vào `ErrorHandler`.
8. **Consecutive failures:** ba lần lỗi, lần thứ tư thành công, counter reset và lastSuccessAt cập nhật.
9. **Retry vs next tick:** retry cùng occurrence ID với attempt number mới; cron tick sau có occurrence ID khác.
10. **Transaction rollback:** lỗi trong service transactional, xác minh trạng thái DB và business ledger sau commit/rollback.
11. **Async child failure:** scheduled callback submit `CompletableFuture` rồi return; lỗi future con vẫn được quan sát.
12. **Scheduler saturation:** chặn một scheduler thread và kiểm tra job khác bị trễ; không gán mọi trễ thành business failure.
13. **Process restart:** tạo job, dừng/restart qua mốc cron, xác nhận missed-occurrence reconciliation theo contract.
14. **Multiple replicas:** hai node tranh cùng occurrence, kiểm tra lease, fencing và idempotency.
15. **Logging privacy:** stack trace không chứa secret, metrics không có label cardinality cao.

Khi dùng `@Scheduled` để test trong một Spring `ApplicationContext`, phải bật `@EnableScheduling` và inject đúng `TaskScheduler`. Nếu mục tiêu chỉ là default/error propagation, gọi `TaskScheduler` trực tiếp như ví dụ giúp test nhanh hơn và ít phụ thuộc vào lịch đồng hồ.

## 22. Các hiểu nhầm thường gặp

| Hiểu nhầm | Sự thật |
| --- | --- |
| “Một lần `@Scheduled` ném exception thì chắc chắn dừng cả lịch.” | Spring recurring thường log và suppress mặc định; handler khác có thể propagate. |
| “Vì lịch còn active nên đồng bộ hóa dữ liệu đã thành công.” | Active chỉ phản ánh registration; cần kết quả occurrence. |
| “`ScheduledFuture.isDone() == false` là lần cuối không lỗi.” | Future periodic còn chạy không biểu diễn outcome của từng tick. |
| “`get()` của recurring future trả kết quả lần chạy gần nhất.” | Nó thường chờ đến cancel/terminal, không hoàn tất sau mỗi tick. |
| “Lượt cron kế tiếp là retry đúng nghĩa của lượt trước.” | Có thể là occurrence mới, với business key khác. |
| “`ErrorHandler` trả bình thường nghĩa là nghiệp vụ thành công.” | Chỉ suppress exception ở tầng scheduling. |
| “Cứ bắt và nuốt exception trong transaction là ổn.” | Có thể làm transaction commit dữ liệu không nhất quán. |
| “Future cancelled nghĩa là một task đã chạy bị rollback.” | Cancellation không xác nhận downstream đã ngừng hay hoàn tác. |
| “`@Async void` dùng cùng handler mặc định với `@Scheduled`.” | Async void có `AsyncUncaughtExceptionHandler` theo proxy; khác scheduling. |
| “Ba replica cron có cùng một trạng thái lịch.” | Mỗi replica có local runtime; cần source of truth toàn cụm. |
| “Job quá tải mà không thấy exception nghĩa là khỏe.” | Có thể bị queue delay, missed fires, shutdown hoặc worker starvation. |
| “Restart tự khôi phục mọi lượt cron bỏ lỡ.” | In-memory schedule không có durable occurrence ledger mặc định. |
| “Khi `@Scheduled` gọi async service, success của tick là success business.” | Nó có thể chỉ là submit, lỗi thực tế đến sau. |
| “Một lỗi tạm thời phải stop schedule vĩnh viễn.” | Tùy workload; có thể tiếp tục với alert/retry/backoff hoặc pause có chủ đích. |
| “Exception nghĩa là remote side effect chắc chắn chưa xảy ra.” | Timeout/ACK loss có thể làm kết quả không chắc chắn; cần đối soát. |

## 23. Checklist code review

- Tôi phân biệt **desired schedule**, **local registration**, **business occurrence** và **execution attempt**.
- Tôi biết `ErrorHandler` đang được dùng và chính sách suppress/propagate cho periodic jobs.
- Tôi không dựa vào `ScheduledFuture.isDone()` để suy ra success/failure của lượt gần nhất.
- Tôi có structured log/metric cho từng attempt và một `lastSuccessAt` đúng business contract.
- Tôi biết cron tick kế tiếp có phải retry của lượt cũ hay là occurrence mới.
- Với job bắt buộc hoàn tất, tôi lưu occurrence ID/business date và có cơ chế retry/backfill.
- Tôi giới hạn retry bằng backoff, jitter, deadline và số lần tối đa.
- Tôi chọn rõ lỗi nào giữ lịch, lỗi nào pause và lỗi nào cần vận hành can thiệp.
- Tôi không dùng `@Async` để che lỗi rồi ghi “scheduler success” khi công việc con chưa xong.
- Tôi kiểm tra transaction rollback và không coi timeout là bằng chứng side effect chưa xảy ra.
- Khi cancel hoặc shutdown, tôi phân biệt ngăn tick mới và work đã in-flight.
- Nếu có nhiều replica, tôi có owner/lease/dedupe hoặc chấp nhận rõ việc mỗi node chạy riêng.
- Tôi monitor **không có thành công**, **lỗi liên tiếp**, **đến hạn không chạy**, **registration failure** và **duplicate occurrence**.
- Tôi đã kiểm thử suppress/propagate, one-shot, cancel, retry, restart và race bằng latch/clock.
- Tôi có cảnh báo khi schedule desired enabled nhưng runtime không có registration hoạt động.

**Kết luận:** `FAILED` của một **lượt chạy** không có nghĩa `FAILED` của **cả lịch**. Với Spring recurring task, error handler có thể ghi log rồi suppress để các tick sau tiếp tục; nếu handler propagate, periodic future có thể trở thành terminal. Muốn vận hành đúng phải giữ **hai đường trạng thái** riêng: kết quả từng occurrence/attempt và vòng đời registration/desired schedule. Retry, catch-up, idempotency, transaction, theo dõi last success và phục hồi sau restart đều là trách nhiệm thiết kế ngoài việc scheduler tự gọi callback.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 – ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): `setErrorHandler`, scheduling methods, `ScheduledFuture` và lifecycle.
- [Spring Framework 6.2 – TaskUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/support/TaskUtils.html): default error handlers `LOG_AND_SUPPRESS_ERROR_HANDLER`, `LOG_AND_PROPAGATE_ERROR_HANDLER`.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Scheduled`, `TaskScheduler`, `Trigger` và cấu hình scheduler.
- [Spring Framework 6.2 – @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): fixedRate, fixedDelay, cron và giới hạn.
- [Spring Framework 6.2 – TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/TaskScheduler.html): scheduling one-shot/recurring, `TaskRejectedException`, nullable Trigger result.
- [Spring Framework – @Async Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/annotation/Async.html): return types và exception observation.
- [Java SE 17 – ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): khi exception thoát periodic task, subsequent executions bị suppressed.
- [Java SE 17 – ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): `isDone`, `isCancelled`, `get` và periodic future.
- [Java SE 17 – Future.cancel](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): cancellation không đồng nghĩa rollback hoặc bảo đảm dừng remote operation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduling-failures-006">Thử lại có khoảng nghỉ và giới hạn thời gian</a>

<details>
<summary>Xem chi tiết</summary>

# Thử lại có khoảng nghỉ và giới hạn thời gian

## 1. Bài toán: lỗi lịch chạy có cần thử lại?

Một service Spring chạy định kỳ, cứ 1 phút gọi API đối tác để đồng bộ dữ liệu. Một lần API trả HTTP 503, nhưng lần kế tiếp dịch vụ có thể hồi phục. Có nên thực hiện lại ngay hay chờ lịch phút tiếp theo? Nếu thử liên tục không nghỉ, ứng dụng có thể **tạo retry storm**, chiếm hết các worker threads, vượt quota hoặc nhân đôi side effect. Nếu không thử lại, một job quan trọng có thể chậm và mất tiến độ.

Cách giải là **retry có điều kiện**, với **khoảng nghỉ (backoff)**, **trần số lần thử** và **ngân sách thời gian tổng** (deadline), gắn với **một logical job** có danh tính ổn định.

~~~text
Job B42, maxAttempts=3, overallDeadline=10s

attempt #1 -> HTTP 503 (transient)
             |  backoff 100ms
attempt #2 -> HTTP 503 (transient)
             |  backoff 200ms
attempt #3 -> HTTP 200 -> SUCCEEDED

Không có attempt #4.
Nếu hết 10s trước lần thử tiếp theo -> TIMED_OUT.
Nếu lỗi 400/validation -> FAILED ngay, không retry.
~~~

Nguyên tắc:

- Phân loại lỗi: **transient** mới retry; **permanent** phải fail sớm.
- Giới hạn cả số lần và **thời gian tổng**, không chỉ một trong hai.
- Timeout I/O **từng attempt** khác với khoảng nghỉ **giữa attempts**.
- Không dùng vòng `while(true)`/`Thread.sleep` vô hạn trong scheduler thread.
- Dừng/hủy/rejection phải kết thúc một job với trạng thái rõ, không để `Future` pending mãi.
- Với side effect, dùng **idempotency key**, checkpoint, lock/lease và reconciliation khi cần.
- Không coi việc Spring tiếp tục kích hoạt `@Scheduled` sau lỗi là một retry của cùng logical job.

## 2. Năm thời gian và giới hạn phải tách rời

| Khái niệm | Dùng cho | Ví dụ |
| --- | --- | --- |
| `scheduleInterval` | Khi nào scheduler khởi tạo logical job mới | Poll mỗi 60s |
| `maxAttempts` | Số lần thực hiện tối đa, **bao gồm lần đầu** | 3 attempts = 2 retries |
| `backoffDelay` | Thời gian từ một lỗi retryable tới lần thử kế | 100ms, 200ms |
| `perAttemptTimeout` | Thời gian tối đa của **một** HTTP/DB call | 2s |
| `overallDeadline` | Ngân sách toàn bộ logical job, **gồm backoff** | 10s |

Có thư viện gọi `maxRetries` và tính **không gồm lần đầu**. Ví dụ `maxRetries=3` có thể tạo bốn attempts; không nhầm với `maxAttempts=3`.

### 2.1. Tính ngân sách còn lại

~~~text
t=0s:  bắt đầu attempt 1
t=2s:  timeout attempt 1
t=3s:  hết backoff 1s, bắt đầu attempt 2
t=6s:  attempt 2 fail
t=9s:  hết backoff 3s, chỉ còn 1s trước deadline 10s
~~~

Nếu attempt #3 cần timeout HTTP 5s, không thể tự cho phép toàn bộ 5s khi còn 1s ngân sách. Quy tắc:

~~~text
remaining = overallDeadline - elapsedTime

remaining <= 0              -> TIMED_OUT
backoff >= remaining        -> không đặt lịch attempt mới
attemptTimeout              = min(clientTimeout, remaining)
~~~

Trong một JVM, nên dùng `System.nanoTime()` để đo duration/remaining vì đây là nguồn **monotonic elapsed time**. Để đặt lịch vào thời điểm thực, `TaskScheduler.schedule(Runnable, Instant)` nhận `Instant`, và có thể lấy từ `scheduler.getClock().instant().plus(delay)`. Không lưu `System.nanoTime()` xuống database dùng giữa các pod: nó không phải timestamp UTC bền vững; job store cần `deadline_at` bằng `Instant`.

### 2.2. Deadline không là preemption tự động

`Future.get(3, SECONDS)` chỉ giới hạn **caller chờ**; nó không tự hủy một task đang chạy. `CompletableFuture.orTimeout(...)` làm Future hoàn tất exceptional sau hạn nhưng cũng không mặc nhiên dừng I/O hoặc rollback DB. Phải thiết lập connect/read/request timeout trên HTTP client, timeout cho query/transaction, và quy tắc xử lý late result.

Nếu API đã ghi thanh toán nhưng response bị mất, retry phải giữ **cùng idempotency key** của logical operation và có thể tra trạng thái, không tạo thanh toán mới.

## 3. Spring @Scheduled: tick sau lỗi không phải retry đúng nghĩa

~~~java
// Snippet trong Spring-managed component.
@Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
public void synchronize() {
    supplierClient.synchronize();
}
~~~

Nếu invocation 10:00 ném exception, `ThreadPoolTaskScheduler` với Spring repeating `ErrorHandler` mặc định thường ghi log rồi **suppress** lỗi, cho phép lần kích hoạt kế tiếp chạy. Nhưng 10:01 là **một scheduled invocation mới**: không tự mang trạng thái attempt count, remaining deadline hoặc job ID của lần 10:00.

Ngược lại, trong **raw JDK `ScheduledExecutorService.scheduleAtFixedRate/scheduleWithFixedDelay`**, unchecked exception thoát khỏi periodic `Runnable` thường làm các lần chạy tiếp theo của **cùng scheduled command** bị suppressed. Spring `TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER` và `LOG_AND_PROPAGATE_ERROR_HANDLER` có chính sách khác nhau; nếu app cung cấp `setErrorHandler` riêng, kết quả tùy implementation.

Vì vậy phải thiết kế độc lập:

- Lịch định kỳ: **khi nào quét/tạo logical job mới**.
- Retry: **cách xử lý cùng job sau một failure**.
- Deadline: **điểm kết thúc muộn nhất** cho job.
- Trạng thái: `SUCCEEDED`, `FAILED`, `WAITING_RETRY`, `TIMED_OUT`, `CANCELLED`, `REJECTED`.

Nếu một method `@Scheduled` chỉ gọi `someAsyncService.start()` rồi bỏ Future, scheduler có thể thấy lời gọi dispatch thành công trong khi công việc bên kia fail. Tầng nghiệp vụ phải giữ/quan sát Future hoặc lưu job status bền vững.

## 4. Phân loại lỗi có retry được hay không

| Loại lỗi | Retry? | Lưu ý |
| --- | --- | --- |
| HTTP 502/503/504 tạm thời | Có, có giới hạn | Backoff, quan sát sức khỏe partner |
| HTTP 429 | Có điều kiện | Respect `Retry-After`/quota, tránh gửi dồn |
| Connect timeout, reset | Có điều kiện | Side effect có thể đã xảy ra ở phía remote |
| Deadlock/transaction conflict | Có điều kiện | Retry transaction mới, tránh duplicate side effect |
| HTTP 400 validation | Không | Cùng payload không tự trở nên hợp lệ |
| HTTP 401/403 | Thường không | Cần credential/quyền đúng |
| `NullPointerException`/sai invariant | Không retry mù | Lỗi logic cần sửa |
| `InterruptedException` | Không nóng vòng lặp | Restore interrupt, dừng/hủy theo contract |
| Scheduler `TaskRejectedException` | Không spin | Throttling, durable queue hoặc fail rõ |
| Deadline đã hết | Không | Ghi terminal status, không tạo attempt mới |

Đừng chỉ retry dựa trên tên class exception chung chung nếu có mã HTTP, mã DB hoặc contract business chi tiết. Đặc biệt **request timeout không chứng minh request chưa được xử lý ở server**. Với email, payment, tạo hóa đơn hoặc transfer, chuẩn bị idempotency key và cơ chế tra cứu kết quả/đối soát.

## 5. Backoff cố định, exponential và jitter

### 5.1. Fixed backoff

~~~text
attempt 1 --500ms--> attempt 2 --500ms--> attempt 3
~~~

Đơn giản, dễ test. Nhược điểm là nhiều pods gặp lỗi lúc 10:00 có thể cùng retry đúng sau 500ms, gây **thundering herd**.

### 5.2. Exponential có trần

~~~text
delayAfterAttempt(n) = min(cap, base * 2^(n-1))

base=100ms, cap=800ms:
n=1 -> 100ms
n=2 -> 200ms
n=3 -> 400ms
n=4 -> 800ms
n=5 -> 800ms
~~~

Khi nhân/shift trong Java, phải tránh overflow; đừng để `2^n` lớn làm duration chuyển thành số âm. Một hàm cap:

~~~java
// Snippet utility, không phải class main độc lập.
static long cappedDelayMillis(
        int failedAttempt, long baseMillis, long capMillis) {
    if (failedAttempt < 1 || baseMillis < 0 || capMillis < 0) {
        throw new IllegalArgumentException("invalid retry settings");
    }
    long delay = Math.min(baseMillis, capMillis);
    for (int i = 1; i < failedAttempt; i++) {
        if (delay >= capMillis) {
            return capMillis;
        }
        delay = delay > capMillis / 2 ? capMillis : delay * 2;
    }
    return delay;
}
~~~

### 5.3. Jitter và Retry-After

Với full jitter, chọn ngẫu nhiên trong khoảng từ 0 tới capped delay; với equal jitter, giữ một phần delay tối thiểu rồi cộng random. Mục đích là **rải các lượt retry giữa các pod**:

~~~text
capped delay = 800ms
pod A chọn 127ms
pod B chọn 631ms
pod C chọn 414ms
~~~

Nếu đối tác trả HTTP `Retry-After: 30`, không tự ý retry sau 100ms chỉ vì chính sách nội bộ. Lập kế hoạch chờ tối thiểu khoảng thời gian được yêu cầu theo HTTP contract. Nếu nó lớn hơn remaining deadline, chuyển `TIMED_OUT` hoặc tạo deferred durable job theo quy tắc nghiệp vụ. Không reset deadline mỗi lần retry để lách giới hạn.

## 6. Demo Java 17 đầy đủ: lên lịch retry không chặn worker ngủ

`RetryDelayDemo.java` dùng `ScheduledExecutorService` để đặt **one-shot retry** sau 40ms/80ms, thay vì `Thread.sleep` ngay trên scheduler worker. Nó chứng minh transient success, permanent failure và deadline hết trước khi chạy.

### 6.1. Mã nguồn RetryDelayDemo.java

~~~java
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class RetryDelayDemo {
    static final class TransientFailure extends RuntimeException {
        TransientFailure(String message) {
            super(message);
        }
    }

    static CompletableFuture<String> retry(
            ScheduledExecutorService scheduler,
            Callable<String> work,
            Duration budget,
            AtomicInteger attempts) {

        if (budget.isNegative()) {
            throw new IllegalArgumentException("negative budget");
        }

        CompletableFuture<String> result = new CompletableFuture<>();
        long deadline = System.nanoTime() + budget.toNanos();

        class Attempt implements Runnable {
            @Override
            public void run() {
                if (result.isDone()) {
                    return;
                }
                if (System.nanoTime() >= deadline) {
                    result.completeExceptionally(
                            new TimeoutException("budget exhausted"));
                    return;
                }

                int number = attempts.incrementAndGet();
                try {
                    String response = work.call();
                    if (System.nanoTime() >= deadline) {
                        result.completeExceptionally(
                                new TimeoutException("late success"));
                    } else {
                        result.complete(response);
                    }
                } catch (TransientFailure transientError) {
                    if (number >= 3) {
                        result.completeExceptionally(transientError);
                        return;
                    }

                    long delayMillis = 40L << (number - 1);
                    long remaining = deadline - System.nanoTime();

                    if (remaining <=
                            TimeUnit.MILLISECONDS.toNanos(delayMillis)) {
                        result.completeExceptionally(
                                new TimeoutException("no budget for retry"));
                        return;
                    }
                    try {
                        scheduler.schedule(
                                this, delayMillis, TimeUnit.MILLISECONDS);
                    } catch (RejectedExecutionException rejected) {
                        result.completeExceptionally(rejected);
                    }
                } catch (Exception permanentError) {
                    result.completeExceptionally(permanentError);
                }
            }
        }

        try {
            scheduler.execute(new Attempt());
        } catch (RejectedExecutionException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        try {
            AtomicInteger successCount = new AtomicInteger();
            CompletableFuture<String> success = retry(
                    scheduler,
                    () -> {
                        if (successCount.get() < 3) {
                            throw new TransientFailure("HTTP 503");
                        }
                        return "SYNCED";
                    },
                    Duration.ofSeconds(10),
                    successCount);

            System.out.println("RESULT="
                    + success.get(15, TimeUnit.SECONDS));
            System.out.println("ATTEMPTS=" + successCount.get());

            AtomicInteger permanentCount = new AtomicInteger();
            CompletableFuture<String> permanent = retry(
                    scheduler,
                    () -> {
                        throw new IllegalArgumentException("bad data");
                    },
                    Duration.ofSeconds(10),
                    permanentCount);

            try {
                permanent.get(15, TimeUnit.SECONDS);
            } catch (ExecutionException error) {
                System.out.println("PERMANENT_CAUSE="
                        + error.getCause().getClass().getSimpleName());
            }
            System.out.println("PERMANENT_ATTEMPTS="
                    + permanentCount.get());

            AtomicInteger timeoutCount = new AtomicInteger();
            CompletableFuture<String> expired = retry(
                    scheduler,
                    () -> "UNEXPECTED",
                    Duration.ZERO,
                    timeoutCount);

            try {
                expired.get(15, TimeUnit.SECONDS);
            } catch (ExecutionException error) {
                System.out.println("DEADLINE_CAUSE="
                        + error.getCause().getClass().getSimpleName());
            }
            System.out.println("DEADLINE_ATTEMPTS="
                    + timeoutCount.get());
        } finally {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        }
    }
}
~~~

### 6.2. Chạy và kết quả

Lưu đúng `RetryDelayDemo.java`, chạy với JDK 17+:

~~~bash
javac --release 17 RetryDelayDemo.java
java RetryDelayDemo
~~~

Các dòng stdout trong điều kiện scheduler có đủ thời gian thực thi:

~~~text
RESULT=SYNCED
ATTEMPTS=3
PERMANENT_CAUSE=IllegalArgumentException
PERMANENT_ATTEMPTS=1
DEADLINE_CAUSE=TimeoutException
DEADLINE_ATTEMPTS=0
~~~

`Attempt.run()` kiểm tra deadline và tăng bộ đếm trước khi gọi `work.call()`. Hai lần đầu supplier nhìn thấy attempts 1,2 và ném `TransientFailure`; lần thứ ba trả `SYNCED`. Với `IllegalArgumentException`, không vào catch transient nên job fail ngay lần đầu. Budget `Duration.ZERO` hết ngay: Future exceptional với `TimeoutException` trước khi attempt nào chạy.

**Giới hạn demo:** Callable đều rất nhanh, không có I/O thật. Deadline được kiểm tra trước/sau Callable nhưng không preempt một Callable đang treo. Cần timeout ở HTTP/DB client trong production. `CompletableFuture.cancel(...)` khiến callback về sau bỏ qua do `isDone()` nhưng không tự hủy ngay pending `ScheduledFuture`; sản phẩm nên giữ và cancel đúng handle, đồng thời cập nhật job state. Demo chỉ xác minh policy, không thay thế durable queue.

## 7. API Spring TaskScheduler: đặt one-shot retry đúng thời điểm

`TaskScheduler.schedule(Runnable, Instant)` cho phép gửi một callback vào một **mốc trong tương lai**. Một cách dùng:

~~~java
// Snippet trong service đã inject TaskScheduler.
Instant nextAttemptAt = scheduler.getClock().instant()
        .plus(Duration.ofMillis(200));

ScheduledFuture<?> nextHandle = scheduler.schedule(
        () -> processOneAttempt(jobId),
        nextAttemptAt);
~~~

Không `Thread.sleep(200)` trong worker; khi nhận lỗi transient, callback **đăng ký lượt mới rồi trả quyền cho scheduler**. Đây là cơ chế lập lịch one-shot, khác với recurring `@Scheduled(fixedRate/fixedDelay/cron)`. Nếu scheduler bị shutdown hoặc từ chối, `TaskScheduler.schedule` có thể ném Spring `TaskRejectedException` ngay lúc đăng ký. Phải hoàn tất business Future exceptional hoặc lưu trạng thái rejected; không được bỏ qua lỗi để job treo.

### 7.1. Tránh vòng lặp retry vô hạn trong scheduled method

~~~java
// Anti-pattern (snippet, không biên dịch độc lập):
@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
public void retryForever() {
    while (true) {
        try {
            supplier.call();
            return;
        } catch (RuntimeException error) {
            // Thread.sleep(1000) ngay tại đây vừa cần xử lý
            // InterruptedException, vừa giữ scheduler worker khi nghỉ.
        }
    }
}
~~~

Đoạn sai vì không trần attempts, không deadline, mọi lỗi đều retry và chiếm worker vô ích. Trên `SimpleAsyncTaskScheduler` của Spring 6.1, fixed-delay tasks có thể thực thi trên **scheduler thread chung**, càng dễ chặn lịch khác. Một retry đồng bộ có bounded sleep có thể phù hợp với dedicated worker, nhưng không phù hợp với timer chung hoặc hàng nghìn jobs.

## 8. Dự án Spring Boot 3.3.5 + Java 17 chạy được

Ví dụ dưới dùng một bean `ThreadPoolTaskScheduler` và `schedule(Runnable, Instant)`. Hai lỗi transient được thử lại với các khoảng 50ms và 100ms; một lỗi permanent dừng ngay. Dự án không cần web server/DB và không cần `@EnableScheduling` vì **không sử dụng annotation @Scheduled** trong code chạy, chỉ dùng API `TaskScheduler` trực tiếp.

### 8.1. Cấu trúc

~~~text
spring-scheduled-retry/
├── pom.xml
└── src/main/java/com/example/retry/
    ├── RetrySchedulingApplication.java
    └── RetryJob.java
~~~

### 8.2. pom.xml

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
    <artifactId>spring-scheduled-retry</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
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

Maven plugin có cặp thẻ `<plugin> ... </plugin>` hợp lệ trong `<plugins>`, bên trong `<build>`.

### 8.3. RetrySchedulingApplication.java

~~~java
package com.example.retry;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
public class RetrySchedulingApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                SpringApplication.run(RetrySchedulingApplication.class, args)) {
            // Runner chạy xong thì đóng ApplicationContext.
        }
    }

    @Bean("retryScheduler")
    ThreadPoolTaskScheduler retryScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("retry-demo-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    @Bean
    ApplicationRunner verify(RetryJob job) {
        return args -> {
            RetryJob.Run success = job.start("batch-42");
            RetryJob.Outcome result =
                    success.future().get(10, TimeUnit.SECONDS);
            System.out.println("RESULT=" + result.value());
            System.out.println("SUCCESS_ATTEMPTS=" + result.attempts());

            RetryJob.Run permanent = job.start("INVALID");
            try {
                permanent.future().get(10, TimeUnit.SECONDS);
                throw new IllegalStateException("Expected failure");
            } catch (ExecutionException error) {
                System.out.println("PERMANENT_CAUSE="
                        + error.getCause().getClass().getSimpleName());
            }
            System.out.println("PERMANENT_ATTEMPTS="
                    + permanent.attempts().get());
        };
    }
}
~~~

### 8.4. RetryJob.java

~~~java
package com.example.retry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class RetryJob {
    public record Outcome(String value, int attempts) { }
    public record Run(
            CompletableFuture<Outcome> future,
            AtomicInteger attempts) { }

    static final class TransientFailure extends RuntimeException {
        TransientFailure(String message) {
            super(message);
        }
    }

    private final TaskScheduler scheduler;

    public RetryJob(@Qualifier("retryScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Run start(String id) {
        AtomicInteger attempts = new AtomicInteger();
        CompletableFuture<Outcome> done = new CompletableFuture<>();
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(5);
        scheduleNext(id, attempts, done, deadline, Duration.ZERO);
        return new Run(done, attempts);
    }

    private void scheduleNext(
            String id,
            AtomicInteger attempts,
            CompletableFuture<Outcome> done,
            long deadline,
            Duration delay) {
        if (done.isDone()) {
            return;
        }
        long remaining = deadline - System.nanoTime();
        if (remaining <= delay.toNanos()) {
            done.completeExceptionally(
                    new TimeoutException("retry budget exhausted"));
            return;
        }

        try {
            scheduler.schedule(
                    () -> performAttempt(id, attempts, done, deadline),
                    scheduler.getClock().instant().plus(delay));
        } catch (TaskRejectedException rejected) {
            done.completeExceptionally(rejected);
        }
    }

    private void performAttempt(
            String id,
            AtomicInteger attempts,
            CompletableFuture<Outcome> done,
            long deadline) {
        if (done.isDone()) {
            return;
        }
        if (System.nanoTime() >= deadline) {
            done.completeExceptionally(
                    new TimeoutException("deadline before attempt"));
            return;
        }

        int n = attempts.incrementAndGet();
        try {
            if ("INVALID".equals(id)) {
                throw new IllegalArgumentException("invalid batch");
            }
            if (n < 3) {
                // Mô phỏng đối tác HTTP 503 hai lần đầu.
                throw new TransientFailure("HTTP 503");
            }

            if (System.nanoTime() >= deadline) {
                done.completeExceptionally(
                        new TimeoutException("late result"));
            } else {
                done.complete(new Outcome("synced:" + id, n));
            }
        } catch (TransientFailure error) {
            if (n >= 3) {
                done.completeExceptionally(error);
                return;
            }
            scheduleNext(id, attempts, done, deadline,
                    Duration.ofMillis(50L * n));
        } catch (RuntimeException error) {
            done.completeExceptionally(error);
        }
    }
}
~~~

**Giải thích:** bộ đếm và Future mới được tạo **cho mỗi logical job**, deadline chỉ tính một lần. `scheduleNext` kiểm tra remaining trước khi đăng ký, catch rejection; `performAttempt` kiểm tra deadline trước khi làm, phân loại transient/permanent và lập lịch retry 50ms rồi 100ms. Không có `Thread.sleep` giữa attempts. `@Qualifier` chọn đúng scheduler bean; `@Service` được component scan trong cùng package.

Giới hạn của bản demo: thao tác giả lập cực ngắn, không cần per-attempt timeout; nếu thay bằng HTTP blocking thật thì phải áp timeout ở client, chọn worker/pool phù hợp, chống quá tải và giữ `ScheduledFuture` để hỗ trợ cancel pending retries. Không sử dụng `done.cancel(true)` như thể nó tự interrupt side effect đã dispatch.

### 8.5. Lệnh chạy và stdout

Trong thư mục chứa `pom.xml`, JDK 17+ và Maven:

~~~bash
mvn spring-boot:run
~~~

Ngoài các log Maven/Boot, chương trình in bốn dòng trong điều kiện máy có đủ thời gian:

~~~text
RESULT=synced:batch-42
SUCCESS_ATTEMPTS=3
PERMANENT_CAUSE=IllegalArgumentException
PERMANENT_ATTEMPTS=1
~~~

Lỗi transient hai lần trước khi lần ba thành công. Lỗi `INVALID` thuộc nhóm permanent nên chỉ có một attempt và Future hoàn tất exceptional. `get(10, SECONDS)` là timeout đợi của **runner kiểm chứng**, không phải timeout của từng HTTP attempt.

## 9. Chống overlap giữa các logical jobs

`@Scheduled(fixedRate)` có thể mở job mới khi job cũ còn retry. Ngay cả `fixedDelay` cũng không bảo đảm completion nghiệp vụ nếu method chỉ dispatch một `CompletableFuture` rồi trả về.

~~~text
Timeline:
  scheduler tick 10:00 --> job A attempt1 --retry--attempt2 --retry...
  scheduler tick 10:01 --> job B attempt1 ...
Có thể overlap nếu A chưa terminal.
~~~

Một gate đơn giản **trong một JVM**:

~~~java
// Snippet trong Spring-managed component.
private final AtomicBoolean running = new AtomicBoolean();

@Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
public void startIfIdle() {
    if (!running.compareAndSet(false, true)) {
        return; // Chủ động bỏ tick khi logical job cũ còn active.
    }
    try {
        jobService.startWithRetry()
                .whenComplete((value, error) -> {
                    try {
                        recordTerminalState(value, error);
                    } finally {
                        running.set(false);
                    }
                });
    } catch (RuntimeException error) {
        running.set(false);
        throw error;
    }
}
~~~

`jobService`/`recordTerminalState` là logic ứng dụng. Một `AtomicBoolean` không hoạt động **giữa ba pods**; cần DB claim/distributed lease và idempotency. Nếu callback ghi trạng thái cũng có thể fail, phải giám sát **stage trả về** từ `whenComplete` hoặc dùng một job ledger đáng tin, không chỉ giả định Future gốc thành công là mọi audit đều thành công.

Một `@Scheduled(fixedDelay=30s)` chỉ gọi `asyncJob.startWithRetry()` rồi bỏ Future thì delay tính từ lúc **dispatch return**, không phải từ khi job thực sự terminal. Muốn nghỉ sau **business completion**, dùng state machine lập lịch từ terminal event, hoặc mô hình blocking chuyên biệt có deadline, không chặn scheduler timer chung.

## 10. Công cụ của Spring và Reactor

| Công cụ | Khi nên dùng | Cảnh báo |
| --- | --- | --- |
| `TaskScheduler.schedule(Runnable, Instant)` | Delayed one-shot retry không ngủ trong timer thread | Tự quản deadline, handles, job state |
| Spring Retry `RetryTemplate` | Code đồng bộ, policy retry/backoff rõ | Backoff thường giữ caller/worker; cần bounded pool và timeout |
| Spring Retry `@Retryable` | Method qua Spring proxy có AOP và dependency kích hoạt đúng | Self-invocation bypass proxy; không tự giải quyết lịch tick |
| Resilience4j Retry/TimeLimiter/Bulkhead | Retry, timeout và giới hạn tài nguyên phối hợp | Cần version/type integration và quan sát |
| Reactor `retryWhen(Retry.backoff(...))` | Pipeline `Mono/Flux` lazy, reactive thực sự | Deadline, idempotency, cancellation, overlap |
| Durable delayed queue / DB job store | Retry qua restart, nhiều pod và tác vụ quan trọng | Cần lease, dedup, DLQ, reconciliation |

Ví dụ Reactor (snippet; không thuộc Maven demo đồng bộ):

~~~java
return remoteClient.fetch()
        .retryWhen(
                Retry.backoff(2, Duration.ofMillis(200))
                        .maxBackoff(Duration.ofSeconds(2))
                        .filter(this::isTransientFailure))
        .timeout(Duration.ofSeconds(10))
        .doOnError(this::recordFailure);
~~~

`Retry.backoff(2,...)` cho phép **hai retries sau subscription đầu**, tức có thể tổng cộng ba attempts. Timeout của pipeline không bảo đảm external transaction đã commit bị rollback. Spring 6.1 reactive `@Scheduled` với fixed-delay chờ terminal subscription; nếu retry vô hạn/publisher không bao giờ hoàn tất, lịch kế tiếp có thể bị chặn.

## 11. Cancellation, timeout, shutdown và durable job

### 11.1. Caller timeout và Future cancellation

~~~java
// Snippet: executor, Invoice, persist và generateInvoice do app cung cấp.
Future<Invoice> future = executor.submit(this::generateInvoice);
try {
    Invoice invoice = future.get(3, TimeUnit.SECONDS);
    persist(invoice);
} catch (TimeoutException error) {
    future.cancel(true);
    // cancel là yêu cầu; không chứng minh DB/remote rollback.
}
~~~

Snippet chưa xử lý `InterruptedException` và `ExecutionException`; trong code hoàn chỉnh cần bắt riêng, khôi phục interrupt bằng `Thread.currentThread().interrupt()` và giữ cause gốc. Đặt timeout ở HTTP client, JDBC/DB và hợp đồng hủy khi có thể.

### 11.2. Nếu pod chết giữa backoff

One-shot `ScheduledFuture<?>` chỉ tồn tại trong RAM JVM. Với tác vụ bắt buộc sống qua restart, lưu:

~~~text
job_id, idempotency_key, status, attempt_count,
next_attempt_at, deadline_at, last_error_code,
owner_lease_until, checkpoint, updated_at
~~~

Một quy trình bền vững:

1. Ghi logical job `PENDING` và idempotency key.
2. Worker claim bằng transaction, chuyển `RUNNING` và tăng attempts.
3. Nếu retryable failure, ghi `WAITING_RETRY` và `next_attempt_at` vào DB, không chỉ `Thread.sleep`.
4. Dispatcher claim những job đến hạn với lease, tôn trọng budget và quota; nhiều pods không xử lý cùng một job vô ý.
5. Khi thành công ghi `SUCCEEDED`; hết attempts/permanent/deadline ghi `FAILED`/`TIMED_OUT`.
6. Sau restart reconciler phục hồi `RUNNING` quá lease và `WAITING_RETRY` đã đến hạn, cùng idempotency/cursor.
7. Không coi transaction DB và side effect của remote API là một giao dịch nguyên tử; dùng outbox/compensation khi cần.

`System.nanoTime()` chỉ đo elapsed trong **một process**. Dùng `Instant deadline_at` UTC trong DB và chuyển remaining sang monotonic budget cục bộ sau khi worker claim job. Khi side effect hoàn tất muộn sau deadline, áp policy đối soát kết quả, không giả định timeout tự hoàn tác công việc.

### 11.3. Khi shutdown hoặc rejection

- Scheduler shutdown không nhận đăng ký one-shot mới; catch `TaskRejectedException` và ghi terminal state hoặc đưa lại queue bền vững.
- `ScheduledFuture.cancel(false)` ngăn task pending nếu cancellation thắng race, không hủy side effect đã chạy.
- `cancel(true)` chỉ gửi interruption khi backend hỗ trợ; transaction đã commit không tự rollback.
- Không block một scheduler một worker để `join()` child Future cũng được dispatch vào chính worker đó: có thể starvation/deadlock.
- Không dùng timeout caller làm bằng chứng rằng worker đã cleanup MDC/security context; cleanup phải trong `finally` tại đúng thread.

## 12. Kiểm thử và giám sát

### 12.1. Các metric nên theo dõi

| Tín hiệu | Mục tiêu |
| --- | --- |
| `logical_jobs_started_total` | Số jobs mới, không phải số attempts |
| `retry_attempts_total` | Số lần retry thật |
| `retry_delay_seconds` | Backoff và `Retry-After` được áp dụng |
| `job_succeeded_total` / `job_failed_total` | Business terminal outcome |
| `job_timed_out_total` | Hết deadline tổng |
| `scheduler_rejected_total` | Pool overload/shutdown |
| `job_inflight` | Overlap và backlog |
| `last_success_timestamp` | Job vẫn tick nhưng luôn fail |
| `downstream_429/503_latency` | Đánh giá sức khỏe đối tác |

Structured logs nên có `jobId`, `attempt`, `maxAttempts`, `errorCode`, `remainingMillis`, `outcome`. Không đưa credential, PII hoặc job ID vô hạn vào metric labels có cardinality cao.

### 12.2. Bảng kiểm thử

| Case | Kết quả đúng |
| --- | --- |
| Hai lỗi transient rồi thành công | 3 attempts, hai backoffs, SUCCEEDED |
| Lỗi permanent ở attempt 1 | FAILED, không retry |
| Deadline hết trước attempt 1 | TIMED_OUT, 0 attempts |
| Remaining nhỏ hơn backoff | Không schedule attempt tiếp |
| Hết maxAttempts | Không attempt vượt trần |
| Scheduler shutdown/rejected | Trạng thái terminal, không Future treo |
| HTTP 429 có Retry-After > budget | Tôn trọng delay hoặc durable deferral |
| Caller timeout nhưng worker còn chạy | Không khẳng định side effect rollback |
| Tick mới khi job cũ chưa terminal | Skip/queue/overlap theo policy rõ |
| Nhiều pods claim cùng job | Lease/idempotency chống trùng |
| Cancel trong lúc chờ retry | Pending future được cancel hoặc callback kiểm tra terminal |
| Restart lúc WAITING_RETRY | Tiếp tục từ durable next_attempt_at hoặc terminal |

Test `cappedDelayMillis`, phân loại lỗi và quyết định timeout như **hàm thuần**; integration test với `CountDownLatch`/fake scheduler và clock injectable. Tránh `Thread.sleep(100)` rồi assert số tick chính xác theo thời gian hệ điều hành; dùng timeout hữu hạn và tolerance.

### 12.3. Checklist review

1. Retry có cùng logical job ID và deadline xuyên suốt các attempts?
2. `maxAttempts` và `maxRetries` đã phân biệt đúng?
3. Chỉ retry transient, dừng permanent/cancellation đúng?
4. Backoff có cap/jitter khi cần và tôn trọng Retry-After?
5. Có per-attempt I/O timeout và overall deadline?
6. Có kiểm tra remaining **trước dispatch** và sau hoàn tất?
7. Có chống overlap giữa các tick và nhiều pods?
8. Có job idempotency để tránh side effects trùng?
9. Có xử lý scheduler rejection, cancellation và shutdown?
10. Có log/metrics terminal result, không chỉ “submitted”?
11. Có durable next_attempt_at và reconciliation nếu job cần sống qua restart?
12. Có test các nhánh success/permanent/deadline/rejection và late result?

## 13. Câu hỏi tự kiểm tra

1. Vì sao `maxAttempts=3` không nhất thiết bằng `maxRetries=3`?
2. Tại sao Spring continuing `@Scheduled` sau exception không tự là retry của cùng logical job?
3. Nếu budget 4s, attempt 1 mất 3s và backoff cần 2s, có được thử tiếp không?
4. `Future.get(timeout)` có tự interrupt worker hoặc rollback DB không?
5. Vì sao exponential backoff nên có cap và jitter khi deploy nhiều pods?
6. HTTP 429 với `Retry-After: 30` nhưng còn 10s budget: có nên retry sau 200ms?
7. `TaskScheduler.schedule(Runnable, Instant)` giúp tránh giữ worker ngủ thế nào?
8. Khi scheduler từ chối schedule next attempt, phải làm gì để Future không pending mãi?
9. Vì sao fixedDelay vẫn có thể overlap business jobs nếu method chỉ dispatch Future rồi return?
10. `System.nanoTime()` khác `Instant` ở việc lưu deadline trong DB?
11. Nếu thanh toán đã commit nhưng response mất, retry nên dùng thông tin nào để tránh duplicate?
12. Khi nào cần durable job queue thay vì retry scheduler trong RAM?

**Kết luận:** Retry của lỗi lịch chạy phải có **phân loại nguyên nhân**, **backoff**, **giới hạn attempts**, **deadline tổng**, **timeout I/O** và **trạng thái nghiệp vụ** có thể quan sát. Trigger định kỳ và attempt retry là hai tầng khác nhau. Không block scheduler bằng sleep vô hạn, không coi timeout là rollback, và không retry side effect thiếu idempotency. Với độ tin cậy qua restart, lưu `next_attempt_at`, attempts, lease và checkpoint bền vững.

## Tài liệu chính thức

- [Spring Framework 6.1 — TaskScheduler API](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — TaskUtils/ErrorHandler](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/support/TaskUtils.html)
- [Java SE 17 — ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Spring Retry — Documentation](https://docs.spring.io/spring-retry/docs/current/reference/html/)
- [Project Reactor — Retry](https://projectreactor.io/docs/core/release/api/reactor/util/retry/Retry.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduling-failures-007">Thiết kế tác vụ có thể chạy lại mà không lặp tác dụng phụ</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế tác vụ có thể chạy lại mà không lặp tác dụng phụ

## 1. Vì sao tác vụ được lập lịch phải chịu được chạy lại?

Trong Spring Framework, một phương thức `@Scheduled` có thể được gọi nhiều lần trong suốt vòng đời ứng dụng. Sau khi một lượt chạy thất bại, scheduler có thể tiếp tục kích hoạt lượt kế tiếp; ứng dụng cũng có thể chủ động retry, khởi động lại, chạy job bù hoặc triển khai nhiều instance. Khi một lượt gặp timeout, ta thường không biết chắc tác dụng phụ của nó đã thành công hay chưa.

Ví dụ job xử lý sự kiện `invoice-731`:

```text
09:00:00  Job đọc invoice-731
09:00:01  Job ghi cộng 100 điểm thưởng cho khách
09:00:02  Gửi ACK/chuyển trạng thái job thất bại do timeout
09:05:00  Job đọc lại invoice-731
09:05:01  Nếu cộng điểm lần nữa -> khách được 200 thay vì 100
```

Vấn đề không nằm ở việc Spring “tự gọi hai lần một cách sai”. Đây là hệ quả của cơ chế chạy lại nhằm phục hồi sau lỗi: **một công việc có thể được thực thi hơn một lần, nhưng một tác dụng phụ logic chỉ nên được áp dụng một lần**.

Đây là bài học về *idempotency* trong tác vụ theo lịch, với các mục tiêu:

- Phân biệt **lượt gọi**, **công việc logic** và **tác dụng phụ**.
- Chọn **idempotency key** ổn định qua retry/restart.
- Dùng **ràng buộc UNIQUE và transaction** để chặn duplicate khi nhiều worker cùng chạy.
- Phân biệt trường hợp dữ liệu trong cùng database với email, thanh toán và API bên ngoài.
- Tránh các cửa sổ lỗi “đã làm nhưng chưa đánh dấu” hoặc “đã đánh dấu nhưng chưa làm”.
- Hiểu `@Scheduled`, `TaskScheduler`, error handler và retry không tự tạo bảo đảm exactly-once.
- Viết ví dụ Java 17 độc lập và Spring JDBC `@Transactional` có thể chạy để quan sát rollback, retry và duplicate.
- Kiểm thử các trạng thái crash, timeout, race, multi-instance và dữ liệu gửi sai cùng khóa.

**Nguyên tắc xuyên suốt:** Cho phép **thử thực thi nhiều lần**, nhưng làm cho việc **lặp lại cùng một yêu cầu logic** không gây thêm tác dụng phụ ngoài ý muốn.

## 2. Định nghĩa idempotent và ý nghĩa trong nghiệp vụ

Một thao tác `f` được gọi là idempotent khi lặp lại trên cùng đối tượng/ý nghĩa không làm thay đổi kết quả sau lần đầu:

```text
f(f(state)) = f(state)
```

Trong hệ thống nghiệp vụ, ta quan tâm đến **kết quả quan sát được** và tác dụng phụ, không chỉ giá trị method trả về.

Ví dụ:

| Thao tác | Có tự idempotent không? | Vì sao |
|---|---|---|
| Gán `order.status = "PAID"` | Có thể | Gán cùng trạng thái không nhân tác động nếu không kèm hành động khác |
| `balance = balance + 100` | Không | Mỗi lần gọi tăng thêm 100 |
| `INSERT` bản ghi với primary key cố định | Có thể | DB ngăn hai row trùng khóa, nếu xử lý đúng conflict |
| Gửi cùng một email | Thường không | Có thể gửi hai email dù nội dung giống |
| Gọi API thanh toán `charge(100)` | Không thể giả định | Có thể tạo hai khoản thu khác nhau |
| `PUT` cập nhật một resource về một giá trị | Có thể | Tùy hệ thống đích và side effects kèm theo |
| Đặt cờ `processed=true` rồi gửi HTTP | Không đủ | HTTP vẫn có thể gửi lại hoặc bị bỏ sót tùy thứ tự |

**Một function trả về cùng chuỗi `"OK"` ở mọi lần gọi không có nghĩa function ấy idempotent**. Nếu mỗi lần nó trừ tiền một lần, tác dụng phụ vẫn bị nhân đôi.

Một số thao tác tự có tính idempotent về trạng thái, nhưng vẫn có side effects bên cạnh như gửi notification, ghi audit, publish event. Phải xét **toàn bộ ranh giới tác động** mà yêu cầu nghiệp vụ coi là “không trùng”.

### 2.1. Retry-safe khác với “không chạy lại”

Giải pháp sai thường gặp là đặt biến `AtomicBoolean ranOnce` hoặc `synchronized` lên scheduled method rồi từ chối mọi lần tiếp theo. Nó chỉ quản lý một instance JVM và **không phân biệt lần retry của cùng nghiệp vụ với kỳ chạy mới hợp lệ**.

Một job báo cáo mỗi ngày vẫn phải xử lý **mỗi ngày một lần**. Vì vậy cần khóa idempotency có định danh đúng **kỳ/công việc logic**, ví dụ `tenantId + reportType + businessDate`, không phải khóa cố định `"report"` cho mọi ngày.

## 3. Tại sao scheduler không thể bảo đảm exactly-once?

Có nhiều nguồn kích hoạt lặp:

1. `@Scheduled(fixedRate)`, `fixedDelay` hoặc `cron` mở ra nhiều lượt theo thời gian.
2. Job polling dữ liệu mà lượt trước chưa cập nhật checkpoint đúng.
3. Retry thủ công sau exception, timeout hoặc trả lời HTTP không rõ kết quả.
4. Lượt xử lý commit DB thành công nhưng crash trước khi ACK.
5. Pod bị restart/rolling deployment; job khởi động lại và đọc công việc cũ.
6. Hai JVM cùng đăng ký `@Scheduled`, cùng thấy một record cần xử lý.
7. Broker phân phối lại một message chưa ACK theo chính sách at-least-once.
8. Operator bấm “Run now”, “Retry failed” hoặc replay lịch sử.
9. Task bị interrupt nhưng đã ghi được một phần side effect.
10. Kết quả API ngoài thành công nhưng response bị mất trên network.

Không thể khắc phục toàn bộ bằng một cờ `running` trong Spring bean. Hệ thống cần **định danh công việc logic, cơ chế lưu trạng thái bền vững, ràng buộc nguyên tử và thiết kế side effect**.

### 3.1. At-most-once, at-least-once và hiệu quả exactly-once

| Mục tiêu | Cách thực thi | Rủi ro |
|---|---|---|
| At-most-once attempt | Không retry khi thất bại/không chắc kết quả | Có thể **mất việc** |
| At-least-once attempt | Thử lại đến khi xác nhận thành công hoặc hết chính sách | Có thể **chạy trùng** |
| Exactly-once effect | Các lần thử trùng cùng khóa chỉ gây một thay đổi quan sát được | Cần cơ chế idempotency/atomicity tại đúng ranh giới |

Một scheduler không tự tạo giao dịch nguyên tử xuyên database, SMTP và payment gateway. **Exactly-once execution trên toàn bộ hệ thống** là lời hứa quá mạnh; mục tiêu thực tế là **at-least-once attempts + idempotent effects trong một phạm vi được mô tả rõ**.

## 4. Định danh đúng: cùng công việc hay một công việc mới?

Một **idempotency key** phải ổn định đối với **cùng sự kiện/công việc logic** và khác nhau đối với **hai sự kiện thực sự khác nhau**.

Ví dụ:

```text
tenant-7 | invoice-credit | invoice-731
tenant-7 | daily-report   | 2026-10-29
tenant-9 | daily-report   | 2026-10-29
tenant-7 | sync-batch     | batch-114 | version-2
```

Dùng tổ hợp `tenantId + operation + businessEventId` hoặc `tenantId + jobType + businessPeriod` theo nhu cầu.

### 4.1. Những khóa sai

| Khóa | Vấn đề |
|---|---|
| `UUID.randomUUID()` mỗi lần retry | Mỗi lần là khóa mới, không chặn duplicate |
| `System.currentTimeMillis()` khi task chạy | Retry có timestamp khác |
| `Thread.currentThread().getId()` | Thread không đại diện cho công việc |
| `"daily-report"` cho tất cả khách và mọi ngày | Chặn cả kỳ hợp lệ mới |
| `hash(payload)` không gắn scope/phiên bản | Hai yêu cầu khác nhau nhưng payload giống có thể bị gộp nhầm |
| `invoiceId` không gắn loại tác vụ | Có thể xung đột giữa credit và notification |

**Khóa idempotency cần được hình thành ở phía tạo công việc hoặc lấy từ định danh nghiệp vụ bền vững**, rồi truyền nguyên vẹn qua mọi lần retry. Một scheduled job quét database nên dùng ID của record/event cần xử lý thay vì lấy ID mới cho mỗi lần cron đánh thức.

### 4.2. Cùng khóa nhưng payload khác thì sao?

Nếu hai yêu cầu dùng cùng `idempotencyKey` nhưng số tiền khác nhau:

```text
key = "invoice-731-credit"
request A: amount=100
request B: amount=900
```

Không được im lặng coi B là duplicate thành công của A. Trong production, nên lưu **hash của các trường nghiệp vụ có ý nghĩa** cùng key và trả lỗi `KEY_REUSED_WITH_DIFFERENT_PAYLOAD` nếu không khớp. Chuẩn hóa encoding, thứ tự trường, loại tiền và version của schema trước khi tính hash; đừng đưa các trường biến động vô nghĩa như retry timestamp vào hash.

## 5. Cửa sổ lỗi: nơi duplicate hoặc mất tác dụng phụ xuất hiện

Tưởng tượng hai thao tác riêng biệt:

```text
A: Thực hiện thay đổi nghiệp vụ
B: Ghi dấu đã xử lý
```

Nếu làm A rồi B:

```text
A thành công -> JVM crash -> B chưa ghi
             -> Retry tưởng chưa làm -> A làm lần 2
```

Nếu làm B rồi A:

```text
B thành công -> JVM crash -> A chưa làm
             -> Retry thấy marker -> bỏ qua A mãi
```

**Chỉ đổi thứ tự hai câu lệnh không thể giải quyết đồng thời cả duplicate và mất việc.**

Bảng phân tích:

| Thời điểm sự cố | Không có atomic transaction | Phương án khi cùng một DB |
|---|---|---|
| Trước mọi thao tác | Chưa có tác dụng | Retry |
| Sau khi đánh dấu, trước business update | Có thể bỏ sót business update | Marker và update cùng transaction sẽ rollback |
| Sau business update, trước marker | Có thể ghi trùng | Marker và update cùng transaction commit/rollback chung |
| Sau commit, trước ACK | Có thể retry dù công việc đã xong | UNIQUE marker giúp phát hiện duplicate |
| Đang chờ hệ thống ngoài | Không rõ side effect đã xảy ra? | Cần idempotency ở bên ngoài/đối soát |

Trong một **database có transaction**, cách thông dụng là thêm bản ghi xử lý và cập nhật nghiệp vụ **trong cùng một transaction**. Nhưng điều này **không tự bao phủ email, payment gateway hoặc broker khác**.

## 6. Dùng UNIQUE constraint làm chốt chống trùng

Một câu `SELECT` để hỏi “đã xử lý chưa?” không đủ khi có hai worker:

```text
Worker A: SELECT -> chưa có
Worker B: SELECT -> chưa có
Worker A: UPDATE balance
Worker B: UPDATE balance
=> đã ghi hai lần
```

Nên có **unique constraint ở database**, nơi có thể phân xử xung đột nguyên tử giữa các transaction.

Ví dụ schema PostgreSQL:

```sql
CREATE TABLE processed_job_events (
    tenant_id      VARCHAR(64) NOT NULL,
    operation      VARCHAR(64) NOT NULL,
    event_id       VARCHAR(128) NOT NULL,
    payload_hash   VARCHAR(128) NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, operation, event_id)
);

CREATE TABLE account_balances (
    tenant_id      VARCHAR(64) NOT NULL,
    account_id     VARCHAR(64) NOT NULL,
    balance_cents  BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, account_id)
);
```

Một thử nghiệm tạo marker mới:

```sql
INSERT INTO processed_job_events
    (tenant_id, operation, event_id, payload_hash)
VALUES
    ('tenant-7', 'credit', 'invoice-731', 'hash-of-business-input')
ON CONFLICT (tenant_id, operation, event_id) DO NOTHING;
```

`ON CONFLICT DO NOTHING` của PostgreSQL khiến một duplicate key không tạo thêm row. Trong ứng dụng, phải xem **số row được chèn**, ví dụ `JdbcTemplate.update(...)` trả 1 khi insert, 0 khi conflict cho câu lệnh này. Nếu trả 0, không thực hiện lại side effect và nên đọc stored `payload_hash` để phát hiện key bị tái sử dụng với dữ liệu khác.

### 6.1. Marker và thay đổi nghiệp vụ phải commit cùng nhau

```text
BEGIN TRANSACTION
  INSERT marker với khóa UNIQUE
  Nếu duplicate:
      kiểm tra payload/return trạng thái đã xử lý
  Nếu mới:
      UPDATE tài khoản / INSERT ledger entry
COMMIT TRANSACTION
```

**Quan trọng:** Nếu insert marker thành công rồi business update thất bại, transaction **rollback cả hai**. Retry sau đó có thể nhận marker mới và thử lại.

Nếu business update thành công nhưng commit chưa xong, crash/rollback cũng xóa cả hai thay đổi. Nếu commit thành công nhưng ACK bị mất, retry đụng unique marker và không thực hiện thêm thay đổi.

**Transaction là ranh giới của kết quả idempotent trong database**. Nó không bảo đảm một external HTTP call nằm giữa transaction sẽ rollback khi DB rollback.

## 7. Spring @Transactional: ranh giới cần có hiệu lực thật

Trong Spring, `@Transactional` thường hoạt động qua AOP proxy. Một service được Spring quản lý có method public được gọi **qua proxy** mới nhận đúng advice trong cấu hình mặc định.

Ví dụ thành phần:

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditService {
    private final CreditRepository repository;

    public CreditService(CreditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void credit(BusinessEvent event) {
        repository.insertDedupMarker(event);
        repository.addBalance(event.accountId(), event.cents());
    }
}
```

Đây là **mảnh thiết kế**, `CreditRepository` và `BusinessEvent` là các kiểu nghiệp vụ giả định, **không phải ví dụ độc lập có thể biên dịch**. Mục đích là hai thao tác phải cùng nằm trong transaction DB với constraint xử lý trùng.

### 7.1. Cảnh báo self-invocation

```java
@Service
public class ScheduledCreditJob {
    @Scheduled(fixedDelay = 30000)
    public void run() {
        this.creditInternally(); // Self-invocation qua this.
    }

    @Transactional
    public void creditInternally() {
        // Có thể không được transaction proxy intercept.
    }
}
```

Đây là **phản ví dụ về thiết kế proxy mặc định**. Lời gọi `this.creditInternally()` trong cùng object không đi qua proxy của transaction advice. Cách rõ ràng hơn: method `@Scheduled` ở bean orchestration khác gọi một bean `CreditService` có `@Transactional` qua Spring injection, hoặc dùng `TransactionTemplate`.

### 7.2. Rollback không phải lúc nào cũng theo mọi Exception

Theo default thường gặp của Spring, `RuntimeException` hoặc `Error` kích hoạt rollback; checked `Exception` mặc định có thể không. Cần đọc quy tắc rollback của phiên bản Spring và cấu hình `rollbackFor`/transaction manager nếu có checked exceptions. Spring 6.2 cũng hỗ trợ thay đổi default rollback policy qua `@EnableTransactionManagement` trong những cấu hình phù hợp.

Không tự catch rồi suppress exception bên trong `@Transactional` nếu bạn muốn transaction rollback; khi nuốt lỗi, transaction có thể commit marker sai. Nếu muốn trả về `ALREADY_PROCESSED` khi gặp unique conflict, nên cân nhắc thực hiện catch **bên ngoài transaction** hoặc dùng câu lệnh `ON CONFLICT DO NOTHING` phù hợp database, không dựa vào việc một PostgreSQL transaction còn sử dụng được sau unique-violation exception chưa rollback.

## 8. Ví dụ Java 17 độc lập: đồng thời gọi trùng nhưng chỉ nhận một lần

Ví dụ đầu tiên minh họa **tính nguyên tử trong một JVM**, sử dụng `ConcurrentHashMap.newKeySet()` để xử lý cuộc đua giữa tám threads. Nó giúp hiểu check-and-claim, **không phải giải pháp production** vì không bền vững qua crash và không có transaction với side effect.

**Tệp `InMemoryIdempotencyDemo.java`, chạy độc lập trên Java 17:**

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryIdempotencyDemo {

    private static boolean apply(
            String key,
            Set<String> seen,
            AtomicInteger effects) {

        if (!seen.add(key)) {
            return false; // Claim trùng trong cùng JVM.
        }

        effects.incrementAndGet();
        return true;
    }

    public static void main(String[] args) throws Exception {
        Set<String> seen = ConcurrentHashMap.newKeySet();
        AtomicInteger effects = new AtomicInteger();

        int callers = 8;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);

        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return apply("tenant-7/invoice-731",
                            seen, effects);
                }));
            }

            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Caller chưa sẵn sàng");
            }

            go.countDown();

            int accepted = 0;
            int duplicate = 0;
            for (Future<Boolean> future : results) {
                if (future.get(5, TimeUnit.SECONDS)) {
                    accepted++;
                } else {
                    duplicate++;
                }
            }

            System.out.println("Đã áp dụng: " + accepted);
            System.out.println("Đã bỏ qua trùng: " + duplicate);
            System.out.println("Tác dụng phụ mô phỏng: "
                    + effects.get());

            System.out.println("Lặp cùng khóa được chấp nhận: "
                    + apply("tenant-7/invoice-731",
                            seen, effects));

            System.out.println("Tác dụng phụ cuối: "
                    + effects.get());

        } finally {
            go.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac InMemoryIdempotencyDemo.java
java InMemoryIdempotencyDemo
```

**Đầu ra của chính chương trình**, khi tám workers được tạo trong timeout 5 giây:

```text
Đã áp dụng: 1
Đã bỏ qua trùng: 7
Tác dụng phụ mô phỏng: 1
Lặp cùng khóa được chấp nhận: false
Tác dụng phụ cuối: 1
```

Vì sao những con số này đáng tin?

1. Tám tasks có cùng key; `ready` bảo đảm chúng đều chờ ở chốt `go` trước khi tranh quyền.
2. `ConcurrentHashMap.newKeySet().add(key)` nguyên tử; chỉ một caller có thể thêm key lần đầu.
3. Chỉ caller nhận `true` mới tăng `effects`; bảy caller khác bỏ qua.
4. `Future.get(timeout)` đợi đủ cả tám task hoàn tất trước khi main in kết quả.
5. Lời gọi thứ chín dùng lại key nên trả `false`.

**Không chuyển nguyên si mẫu này sang production!** Nếu `seen.add(key)` thành công rồi JVM crash trước `effects.incrementAndGet()`, thao tác có thể bị bỏ sót mãi nếu trạng thái còn được giữ, hoặc bị thử lại sau restart theo cách khác. Nếu side effect là HTTP/DB ngoài, một `Set` trong bộ nhớ không thể có transaction chung, không thể bảo vệ nhiều pod và không chống mất dữ liệu. Ví dụ chỉ minh họa **chặn race trong cùng process**.

## 9. Spring Framework + H2: ví dụ chạy được về rollback, retry và unique key

Ví dụ thứ hai minh họa cơ chế production quan trọng hơn: **ghi marker và cập nhật số dư trong cùng một transaction**, dùng unique key của relational database. Nó sử dụng **Java 17, Maven, Spring Framework 6.2.7, Spring JDBC và H2** để chạy độc lập mà không cần server database bên ngoài.

Kịch bản:

1. Số dư ban đầu 1000 cent.
2. Gọi xử lý sự kiện `tenant-7/event-731` để cộng 250 cent nhưng **cố ý ném exception sau khi insert marker, trước khi update**.
3. Spring rollback marker, nên marker count vẫn 0.
4. Gọi lại cùng event; transaction thành công, balance trở thành 1250 và marker count 1.
5. Gọi lại thêm một lần; unique constraint từ chối duplicate **trước khi cộng tiền**.
6. Xác nhận số dư cuối vẫn là 1250, không phải 1500.

### 9.1. Cấu trúc dự án

```text
spring-idempotent-job/
├── pom.xml
└── src/main/java/lesson/IdempotentSpringDemo.java
```

### 9.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lesson</groupId>
    <artifactId>spring-idempotent-job</artifactId>
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
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-jdbc</artifactId>
            <version>6.2.7</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>2.2.224</version>
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

### 9.3. Tệp src/main/java/lesson/IdempotentSpringDemo.java

```java
package lesson;

import javax.sql.DataSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

public class IdempotentSpringDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("""
                CREATE TABLE balances (
                    account_id VARCHAR(64) PRIMARY KEY,
                    cents BIGINT NOT NULL
                )
                """);
            jdbc.execute("""
                CREATE TABLE processed_events (
                    tenant_id VARCHAR(64) NOT NULL,
                    event_id VARCHAR(128) NOT NULL,
                    PRIMARY KEY (tenant_id, event_id)
                )
                """);
            jdbc.update(
                    "INSERT INTO balances(account_id, cents) "
                            + "VALUES (?, ?)",
                    "account-A", 1000L);

            LedgerService service = context.getBean(
                    LedgerService.class);

            boolean failedFirst = false;
            try {
                service.apply("tenant-7", "event-731",
                        "account-A", 250L, true);
            } catch (IllegalStateException expected) {
                failedFirst = true;
            }

            System.out.println("Lượt đầu lỗi: " + failedFirst);
            System.out.println("Marker sau rollback: "
                    + jdbc.queryForObject(
                        "SELECT COUNT(*) FROM processed_events",
                        Integer.class));

            service.apply("tenant-7", "event-731",
                    "account-A", 250L, false);
            System.out.println("Retry thành công: true");

            boolean duplicateRejected = false;
            try {
                service.apply("tenant-7", "event-731",
                        "account-A", 250L, false);
            } catch (DuplicateKeyException expected) {
                duplicateRejected = true;
            }

            System.out.println("Lặp cùng event bị chặn: "
                    + duplicateRejected);
            System.out.println("Số marker cuối: "
                    + jdbc.queryForObject(
                        "SELECT COUNT(*) FROM processed_events",
                        Integer.class));
            System.out.println("Số dư cuối (cent): "
                    + jdbc.queryForObject(
                        "SELECT cents FROM balances "
                                + "WHERE account_id = ?",
                        Long.class, "account-A"));
        }
    }

    @Configuration
    @EnableTransactionManagement
    public static class Config {

        @Bean
        public DataSource dataSource() {
            DriverManagerDataSource ds =
                    new DriverManagerDataSource();

            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl(
                    "jdbc:h2:mem:idempotentDemo;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        public DataSourceTransactionManager transactionManager(
                DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public LedgerService ledgerService(JdbcTemplate jdbc) {
            return new LedgerService(jdbc);
        }
    }

    public static class LedgerService {
        private final JdbcTemplate jdbc;

        public LedgerService(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        public void apply(
                String tenantId,
                String eventId,
                String accountId,
                long deltaCents,
                boolean failAfterMarker) {

            // Cùng transaction: insert marker + business update.
            // Unique constraint giải quyết tranh chấp nhiều caller.
            jdbc.update(
                    "INSERT INTO processed_events "
                    + "(tenant_id, event_id) VALUES (?, ?)",
                    tenantId, eventId);

            if (failAfterMarker) {
                throw new IllegalStateException(
                        "Simulated failure before balance update");
            }

            int updated = jdbc.update(
                    "UPDATE balances SET cents = cents + ? "
                    + "WHERE account_id = ?",
                    deltaCents, accountId);

            if (updated != 1) {
                throw new IllegalStateException(
                        "Account not found");
            }
        }
    }
}
```

### 9.4. Biên dịch và chạy

Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.IdempotentSpringDemo
```

**Những dòng do chương trình in**, không tính log tùy môi trường:

```text
Lượt đầu lỗi: true
Marker sau rollback: 0
Retry thành công: true
Lặp cùng event bị chặn: true
Số marker cuối: 1
Số dư cuối (cent): 1250
```

### 9.5. Vì sao đúng?

- `@EnableTransactionManagement` kích hoạt transaction AOP. `LedgerService` được lấy qua Spring context, nên lời gọi `apply(...)` đi qua proxy `@Transactional`.
- `DataSourceTransactionManager` và `JdbcTemplate` cùng dùng một `DataSource`; hai câu lệnh JDBC tham gia cùng một transaction.
- Lần đầu ném `IllegalStateException` sau khi insert marker; vì đây là `RuntimeException`, Spring rollback transaction gồm marker, nên marker count = 0.
- Lần retry insert marker thành công rồi cộng 250, cùng commit; balance 1000 + 250 = 1250.
- Lần gọi thứ ba vi phạm primary key `(tenant_id,event_id)` và `JdbcTemplate` chuyển lỗi ràng buộc thành `DuplicateKeyException`. Vì insert marker xảy ra **trước** update balance, lần này không cộng 250 nữa.
- `DuplicateKeyException` được xử lý **bên ngoài** method transactional tại caller, không làm transaction commit nửa chừng.
- Cơ chế unique constraint của database xử lý cả khi hai tiến trình đồng thời cố chèn cùng một khóa; chỉ một transaction có thể commit marker đó.

**Giới hạn của demo:**

1. H2 in-memory phù hợp học và kiểm thử; **restart tạo database mới**, nên không chứng minh idempotency bền vững qua restart. Production dùng database bền vững và migration/schema constraints chính xác.
2. Demo không lưu `payload_hash`, không xử lý chi tiết khóa trùng nhưng payload khác, không có retention và không có ledger audit đầy đủ.
3. Với tài khoản thật, cần ràng buộc tenant với account, kiểm tra tiền tệ, overflow/negative values, quyền truy cập và transaction isolation theo nghiệp vụ.
4. Demo chỉ chứng minh **một lần cập nhật trong cùng database**. Nó **không** gửi email hoặc gọi thanh toán bên ngoài trong transaction đó.
5. Một sự kiện được dự định có thể tạo nhiều effects (balance, audit, notification); mỗi effect cần được mô hình hóa trong ranh giới idempotency phù hợp.

## 10. Đưa nguyên tắc vào @Scheduled thật

Một scheduled task thường là **orchestrator**: tìm các đơn vị công việc có ID ổn định rồi gửi cho service xử lý idempotent.

Mảnh Spring code:

```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CreditPollingJob {
    private final PendingEventRepository pending;
    private final IdempotentCreditService credit;

    public CreditPollingJob(
            PendingEventRepository pending,
            IdempotentCreditService credit) {
        this.pending = pending;
        this.credit = credit;
    }

    @Scheduled(fixedDelay = 30000)
    public void poll() {
        for (BusinessEvent event : pending.findPendingBatch(100)) {
            credit.applyOnce(event);
        }
    }
}
```

Đây là **mảnh thiết kế**, các kiểu `PendingEventRepository`, `IdempotentCreditService`, `BusinessEvent` do ứng dụng định nghĩa. Cần `@EnableScheduling` và beans trong cùng Spring context. `credit.applyOnce(event)` nên là method Spring-managed có transaction thật, khóa idempotency bền vững và xử lý kết quả rõ.

### 10.1. Lượt scheduled mới không phải cùng một event mới

Mỗi 30 giây, job có thể đọc lại cùng `eventId` nếu pending record chưa được ACK vì crash hoặc timeout. Đây là một **retry của cùng công việc logic**; sử dụng lại key, không sinh UUID mới ở mỗi lần cron gọi.

Nếu một job tự tạo thêm một công việc mới cho mỗi ngày, key cần gắn `businessDate`/`period` theo timezone nghiệp vụ. Nếu job chạy lúc 23:59 và retry lúc 00:01, **không được tính lại key theo thời gian retry**, vì như vậy sẽ biến một công việc cũ thành công việc mới.

### 10.2. Tách scheduler và transaction service

Một cấu trúc rõ ràng:

```text
@Scheduled polling bean
    |
    +-- Đọc batch pending có ID ổn định
    |
    +-- Gọi qua proxy -> @Transactional applyOnce(event)
    |                       |
    |                       +-- UNIQUE marker
    |                       +-- Business update
    |                       +-- Commit/rollback
    |
    +-- Ghi nhận outcome, retry/backoff nếu cần
```

Không đặt toàn bộ một batch gồm hàng nghìn external requests vào **một transaction dài** khi công việc có thể tách thành các đơn vị atomic nhỏ hơn. Thiết kế **transaction per logical event** hoặc chunk theo nhu cầu, có cơ chế khôi phục rõ.

## 11. ErrorHandler của Spring không phải idempotency mechanism

Điểm liên quan trực tiếp đến chuyên mục **“Lỗi của lịch chạy”**: `ThreadPoolTaskScheduler` và hạ tầng Spring thường bọc tác vụ bằng error handler.

Theo `TaskUtils`:

- Với task **lặp**, mặc định Spring thường **log và suppress** exception để các lượt sau vẫn có thể xảy ra.
- Với task **một lần**, mặc định thường **log và propagate** exception để người dùng có thể quan sát qua `Future`.
- Nếu cấu hình `setErrorHandler(...)` khác, hành vi có thể thay đổi.

```java
scheduler.setErrorHandler(error -> {
    recordFailure(error);
});
```

Đây là mảnh cấu hình `ThreadPoolTaskScheduler` có `recordFailure(...)` do ứng dụng định nghĩa. Nếu handler chỉ log, nó có thể làm lỗi bị suppress. Điều này **không có nghĩa Spring đã tự retry đúng event** hoặc đã rollback các side effects.

**Không nhầm các việc:**

| Cơ chế | Chức năng | Không bảo đảm |
|---|---|---|
| `@Scheduled(fixedDelay=...)` | Kích hoạt lượt theo thời gian | Exactly-once effect |
| Spring `ErrorHandler` | Quyết định log/suppress/propagate lỗi | Dedupe dữ liệu nghiệp vụ |
| `@Retryable` hoặc retry loop | Thử lại khi lỗi theo chính sách | Side effect không bị nhân đôi |
| `@Transactional` | Atomicity của các tài nguyên thuộc transaction | Rollback external HTTP/email |
| UNIQUE constraint + marker | Ngăn lặp cùng khóa tại database | Lịch không overlap ở JVM khác |
| Outbox + dedupe consumer | Tăng độ tin cậy với thông điệp liên hệ bên ngoài | Mọi hệ thống đích tự nhiên exactly-once |

Nếu một periodic `Runnable` ném exception và error handler suppress, chính **lượt schedule kế tiếp** có thể quét lại pending data. Đó vẫn là một lần thử mới; key phải giữ nguyên cho đơn vị chưa được xử lý thành công.

## 12. Retry đúng loại lỗi, có backoff và giới hạn

Không phải lỗi nào cũng nên thử lại.

| Lỗi | Chính sách thường hợp lý | Lý do |
|---|---|---|
| Socket timeout, HTTP 503, database deadlock có thể phục hồi | Retry có giới hạn, backoff và jitter | Lỗi tạm thời |
| Payload sai schema, quyền không hợp lệ | Không retry vô hạn; chuyển xử lý thủ công/DLQ | Retry không tự sửa dữ liệu |
| Idempotency key trùng, payload giống | Trả `ALREADY_PROCESSED` hoặc kết quả cũ | Không thực hiện lại |
| Key trùng, payload khác | Báo conflict, không thực hiện effect | Sai định danh hoặc hành vi bất thường |
| External call không rõ kết quả | Dùng key ở đích, query status/đối soát trước retry | Tránh hai side effects |
| Lỗi do dependency overload | Backoff, circuit breaker, rate limit | Giảm áp lực hệ thống |

Exponential backoff thường có dạng:

```text
delay_n = min(maxDelay, baseDelay × 2^n)
delay thực tế = delay_n có jitter phù hợp
```

Giới hạn số lần thử, tổng thời gian chờ và thời hạn nghiệp vụ. Nếu lỗi cần can thiệp, chuyển sang trạng thái `FAILED_PERMANENT` hoặc dead-letter queue để vận hành xử lý thay vì để cron spam cùng một lỗi vô hạn.

### 12.1. Không retry bằng cách tạo event ID mới

```java
// Sai: cùng một tác dụng phụ nhưng mỗi retry có key mới.
String key = java.util.UUID.randomUUID().toString();
creditService.applyOnce(key, event);

// Đúng về định danh: dùng key ổn định từ đơn vị công việc.
String stableKey = event.tenantId() + "/credit/" + event.id();
creditService.applyOnce(stableKey, event);
```

Đây là **mảnh code giả định** với các kiểu nghiệp vụ tồn tại ở ứng dụng, không phải Java program độc lập. Key dùng cho lần thử thứ 2, 3, 4 phải bằng key của lần thử đầu nếu tất cả là **cùng yêu cầu**.

### 12.2. Retry và timeout không cho biết request cũ đã làm gì

Nếu caller timeout sau 2 giây nhưng bên ngoài mất 3 giây để commit, ngay lúc nhận timeout bạn vẫn chưa chắc biết request có thành công không. Retry với **cùng idempotency key** ở downstream có hỗ trợ sẽ giúp hệ thống đích trả kết quả đã lưu hoặc tránh trừ tiền thêm.

Nếu downstream **không hỗ trợ** idempotency key hay query status, không thể tự tuyên bố “chắc chắn không trùng” chỉ vì local DB lưu một `processed` flag. Cần một hợp đồng hoặc quy trình đối soát/bù trừ.

## 13. Email, thanh toán, webhook: side effects bên ngoài transaction

Đây là nơi **ràng buộc UNIQUE trong database cục bộ không đủ**.

Phản ví dụ:

```text
BEGIN TX
  INSERT processed_marker
  Gọi paymentGateway.charge(100)  -> gateway thành công
  UPDATE local_order
COMMIT thất bại
```

Khi DB transaction rollback, marker biến mất nhưng gateway đã thu tiền. Lượt retry có thể charge tiếp.

Nếu đảo thứ tự:

```text
BEGIN TX
  INSERT processed_marker
  UPDATE local_order
COMMIT
Gửi email
JVM crash trước khi gửi email
```

Retry thấy marker “đã xử lý” và không gửi email nữa, mặc dù nghiệp vụ yêu cầu gửi.

Vì thế phải phân biệt:

1. **Cùng một relational database**: dùng transaction + unique constraint.
2. **Ngoài database**: dùng khả năng idempotency ở đích, transaction/outbox ở nguồn và cơ chế delivery/reconciliation.
3. **Hiệu ứng không thể rollback**: phải thiết kế timeout/đối soát/compensation, không kỳ vọng `@Transactional` Java có thể undo SMTP hoặc REST đã gửi.

### 13.1. Transactional outbox

Một mẫu được dùng rộng rãi:

```text
Transaction DB:
    cập nhật nghiệp vụ (ví dụ trạng thái invoice)
    + INSERT outbox event có businessEventId ổn định
COMMIT cùng nhau
          |
          v
Outbox relay đọc những event đã commit
          |
          +-- publish message tới broker
          |
          +-- nếu crash trước khi ACK -> có thể publish lại
          v
Consumer nhận message
          |
          +-- kiểm tra idempotency key
          +-- áp dụng tác dụng phụ đúng một lần theo contract consumer
```

Ví dụ PostgreSQL:

```sql
CREATE TABLE outbox_events (
    event_id       VARCHAR(128) PRIMARY KEY,
    event_type     VARCHAR(64) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
```

Khi thay đổi bản ghi nghiệp vụ, insert `outbox_events` **trong cùng transaction**. Relay có thể gửi lại cùng `event_id` nếu crash sau publish nhưng trước khi đánh dấu `published_at`. Consumer phải **dedupe** bằng event ID, không kỳ vọng outbox tự bảo đảm chỉ một lần delivery.

### 13.2. Thanh toán

Nếu payment API hỗ trợ `Idempotency-Key`, gửi **cùng khóa** cho mọi retry của một payment intent, không tạo khóa mới mỗi lần socket timeout. Dùng key phản ánh payment intent bền vững, đồng thời xử lý trường hợp payload khác cùng key theo quy tắc gateway.

Không tự giả định gateway nào cũng hỗ trợ lưu trạng thái key trong bao lâu, có trả đúng response cũ hay không, hoặc mọi loại API cùng có semantics. Phải đọc tài liệu và giới hạn retention của **nhà cung cấp cụ thể**.

### 13.3. Email

SMTP thường không có giao thức idempotency end-to-end đủ mạnh để bảo đảm tuyệt đối “chỉ gửi một email”. Có thể dùng outbox, một delivery ID ổn định, API provider có dedupe nếu có, trạng thái gửi và cơ chế đối soát. Trạng thái `SENT` local không phải bằng chứng email đã đến inbox, và retry có thể vẫn gây gửi lại khi ACK mất.

Trong một số nghiệp vụ, duplicate email chấp nhận được hơn missing email; lựa chọn chính sách rõ ràng thay vì tuyên bố exactly-once không kiểm chứng.

## 14. Một state machine cho job cần retry

Với một logical event, ứng dụng có thể lưu:

```text
PENDING
   |
   | worker claim thành công
   v
IN_PROGRESS (owner, leaseUntil, attempt)
   |
   +-- success + commit -> SUCCEEDED
   |
   +-- transient failure -> RETRY_WAIT
   |                           |
   |                           +-- đến thời điểm -> PENDING
   |
   +-- permanent failure -> DEAD_LETTER / FAILED
```

Đây là **thiết kế tham khảo**, không có state machine duy nhất phù hợp mọi hệ thống.

### 14.1. Phân biệt claim và completed marker

`IN_PROGRESS` chỉ nói một worker đang/đã nhận quyền thử xử lý, **không đồng nghĩa công việc đã hoàn tất**. Nếu đánh dấu `PROCESSED` ngay khi claim, worker crash ở giữa sẽ làm công việc bị mất.

Cần có:

- `leaseUntil` hoặc deadline để thu hồi claim khi worker chết.
- `attempt` và `nextRetryAt` để giới hạn retry.
- Owner/generation/fencing token khi có nhiều pod và nguy cơ worker cũ quay lại.
- `SUCCEEDED` chỉ khi side effect trong ranh giới bảo đảm đã commit.
- Trạng thái ngoại lệ không rõ outcome cần `UNKNOWN/NEEDS_RECONCILIATION` thay vì coi như thất bại chắc chắn.

### 14.2. Lease hết hạn không tự hủy worker cũ

Khi lease hết hạn, worker B có thể nhận job dù worker A **vẫn đang chạy chậm**. Nếu A tiếp tục ghi dữ liệu, có thể xảy ra overlap.

Đối với side effect quan trọng, dùng **fencing token/monotonic version** mà database hoặc tài nguyên đích kiểm tra, để một owner cũ không thể ghi sau khi quyền đã được chuyển. Local `synchronized` và `AtomicBoolean` không đủ bảo vệ nhiều JVM.

## 15. @Scheduled nhiều instance: một công việc không được xử lý trùng

Giả sử hai pod cùng chạy:

```java
@Scheduled(fixedDelay = 10000)
public void poll() {
    pendingRepository.findPendingBatch(100)
            .forEach(creditService::applyOnce);
}
```

Đây là mảnh method trong Spring bean có dependencies phù hợp. Hai pod có thể đọc cùng event trước khi pod nào commit checkpoint.

**Giải pháp tối thiểu cho effect trong DB:** unique business key và transaction, vì database có thể phân xử hai writers đồng thời. Thêm distributed lock/lease có thể giảm xử lý thừa, nhưng **không thay thế** idempotency tại side effect.

Một distributed lock chỉ giữ trách nhiệm “ai được thử”, còn crash, lease expiry, timeout và duplicated delivery vẫn có thể gây lần thử lặp. Đừng coi lock là bằng chứng business effect không lặp.

### 15.1. SELECT ... FOR UPDATE SKIP LOCKED

Với PostgreSQL hoặc database hỗ trợ, worker queue có thể claim các rows theo transaction bằng `FOR UPDATE SKIP LOCKED` để hạn chế việc hai worker cùng lấy cùng record tại một thời điểm. Đây là một cơ chế **phân phối công việc**, không phải bảo đảm idempotency sau crash.

Mẫu ý tưởng:

```sql
BEGIN;

SELECT id
FROM pending_jobs
WHERE status = 'PENDING'
ORDER BY id
FOR UPDATE SKIP LOCKED
LIMIT 10;

-- Cập nhật claim/lease theo chính sách.
-- Commit nhanh, không giữ transaction chặn bên ngoài.

COMMIT;
```

Đây là **mảnh SQL định hướng**, cần schema phù hợp. Tránh giữ DB transaction và row locks trong khi gọi remote HTTP chậm. Nếu có claim lease, phải có cơ chế phục hồi worker chết và fencing stale owner.

## 16. Dedupe marker cần giữ bao lâu?

Nếu marker bị xóa quá sớm, một sự kiện cũ xuất hiện lại sau cleanup có thể được xem là event mới và gây duplicate.

Thời gian retention tối thiểu phải dựa trên:

- Khoảng thời gian broker có thể redeliver.
- Thời hạn người vận hành có thể replay.
- Thời gian tối đa của retry/backoff.
- Chính sách lưu audit/quy định nghiệp vụ.
- Thời gian mà downstream còn hỗ trợ idempotency keys.
- Khả năng rebuild state từ event history.

Ví dụ: một payment reconciliation cho phép replay giao dịch sau 90 ngày không thể vô tư xóa marker sau 24 giờ rồi khẳng định an toàn. Khi cần xóa vì dung lượng, cân nhắc watermark, archive và bảo đảm không nhận lại event cũ ngoài horizon.

**Không dùng TTL như một con số mặc định chung** cho mọi job. Lưu key vô hạn cũng có chi phí; phải cân bằng retention, auditing, privacy và storage.

## 17. Khi nào một upsert là idempotent, khi nào không?

Các câu lệnh SQL dễ bị dùng sai:

```sql
-- Có thể idempotent nếu tất cả giá trị và side effects thực sự ổn định:
UPDATE orders SET status = 'PAID' WHERE id = 731;

-- KHÔNG idempotent khi gọi lại:
UPDATE balances SET cents = cents + 250
WHERE account_id = 'A';

-- Có thể chặn duplicate bằng khóa duy nhất:
INSERT INTO processed_job_events
    (tenant_id, operation, event_id, payload_hash)
VALUES
    ('t7', 'credit', 'e731', 'hash')
ON CONFLICT (tenant_id, operation, event_id) DO NOTHING;
```

`ON CONFLICT DO UPDATE` **không tự động** idempotent: nếu nhánh UPDATE lại tăng bộ đếm, gửi trigger notification hoặc cập nhật giá trị từ timestamp hiện tại, gọi lặp vẫn có tác dụng.

Một `UPSERT` ghi cùng giá trị có thể ít thay đổi trạng thái nghiệp vụ nhưng vẫn kích hoạt update hooks/audit, tạo row version hoặc side effects. Khi đánh giá idempotency, phải xét cả trigger, event publication và downstream.

## 18. Khóa theo kỳ lịch: khác nhau giữa chạy lại và kỳ mới

Một scheduled báo cáo ngày có thể dùng key:

```text
tenantId + reportType + localBusinessDate
```

Vấn đề là “ngày” của ai? Nếu tenant ở `Asia/Ho_Chi_Minh` nhưng server sử dụng UTC, việc lấy `LocalDate.now()` theo default timezone có thể lệch kỳ.

Mảnh Java 17:

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
LocalDate period = Instant.now()
        .atZone(zone)
        .toLocalDate();

String key = tenantId + "|DAILY_REPORT|" + period;
```

Đây là mảnh code trong class đã có `tenantId` và imports `java.time.*`. Trong thiết kế production, **period nên được quyết định lúc tạo công việc và lưu cùng job**, không tính lại theo `Instant.now()` trong mỗi retry vì một retry có thể sang ngày hôm sau.

Nếu người dùng cố ý yêu cầu “tạo bản báo cáo mới cho cùng ngày” và được phép gửi một lần khác, phải dùng **một operation/version ID riêng**. Không thay key âm thầm mỗi lần retry để né dedupe.

Với cron có DST, kỳ giờ không tồn tại hoặc lặp lại phải có chính sách rõ. Một hệ thống billing có thể dùng business period/tổng hợp theo kỳ thay vì timestamp thô làm event key.

## 19. Tránh ghi checkpoint quá sớm hoặc quá muộn

Một job đồng bộ theo cursor có thể quét 100 records từ nguồn:

```text
cursor = 100
đọc records 101..200
ghi business effects của 101..200
cập nhật cursor = 200
```

Nếu cursor được commit **trước khi effects hoàn tất**, crash có thể làm mất records 101..200. Nếu effects đã commit nhưng cursor chưa được cập nhật, retry đọc lại cùng records. Khi cùng một DB và phù hợp schema, cập nhật cursor/marker và effects trong cùng transaction. Khi liên quan nhiều hệ thống, dùng per-record idempotency và khôi phục tại các checkpoint có chủ đích.

Một batch lớn thất bại giữa chừng nên có khả năng tiếp tục từ **logical unit of work** đã được ghi thành công, không “đánh dấu cả batch DONE” chỉ vì một vài phần hoàn tất.

### 19.1. Trường hợp job chỉ đọc

Một job tính metrics rồi **ghi đè** cùng bucket theo khóa ổn định có thể tự nhiên idempotent hơn một job cộng dồn `counter += value`. Khi có thể, chọn phép toán convergent như set-to-value/replace partition thay vì increment không có marker, nhưng vẫn phải nghĩ về concurrency, version và side effects kèm theo.

## 20. Chính sách lỗi Spring và transaction propagation

Spring `@Transactional` thường có `Propagation.REQUIRED` mặc định. Nếu một batch scheduler gọi nhiều event-processing methods trên service có proxy, mỗi lời gọi có thể tạo hoặc tham gia transaction tùy caller hiện tại.

Thiết kế cần tránh:

- Một transaction quá dài chờ HTTP hoặc lock DB.
- `@Scheduled` tự gọi method `@Transactional` của chính bean theo `this.method()` làm bypass proxy.
- Catch exception trong transaction rồi return “đã xử lý” khiến marker commit sai.
- Đặt `@Async` rồi kỳ vọng transaction của caller tự chuyển sang worker khác.
- Dùng retry bên trong một transaction đã bị đánh dấu rollback-only và mong commit cuối thành công.
- Không bắt `DuplicateKeyException` ở ranh giới phù hợp khiến scheduler log lỗi liên tục mà thiếu kết quả nghiệp vụ.

**Điểm thiết kế:** Một unit of work có key ổn định, một transaction rõ, kết quả xử lý gồm `APPLIED`, `ALREADY_PROCESSED`, `PERMANENT_FAILURE`, `RETRYABLE_FAILURE` hoặc `UNKNOWN`.

Không dùng `boolean success` để gộp mọi trạng thái; khác biệt “lỗi phải retry” và “đã xử lý trùng” quyết định có gây side effect thêm hay không.

## 21. Observability và audit: đếm attempts riêng với effects

Một dashboard chỉ đếm “scheduled task invoked 100 lần” không cho biết có bao nhiêu công việc logic thành công.

Đề xuất:

| Trường/metric | Ý nghĩa |
|---|---|
| `job.attempts` | Tổng lượt thử |
| `job.unique_keys` | Số ID nghiệp vụ khác nhau |
| `job.applied` | Số effects được commit lần đầu |
| `job.duplicate_skipped` | Số lượt trùng bỏ qua |
| `job.retryable_failure` | Số lỗi có thể thử lại |
| `job.permanent_failure` | Số lỗi không thể tự phục hồi |
| `job.unknown_outcome` | Số tình huống chưa rõ external outcome |
| `job.max_attempts_exhausted` | Công việc vượt retry budget |
| `job.processing_latency` | Thời gian xử lý |
| `job.last_success_at` | Lần thành công gần nhất |
| `job.outbox_backlog` | Event chưa được relay |
| `job.lease_expired` | Claim chưa hoàn tất trước hạn |

Ghi log có `jobId`, `eventId`, `attempt`, `owner`, `generation` và correlation ID ở mức phù hợp. Không đưa PII hoặc payment token vào log.

**Cardinality:** Nếu có hàng triệu event IDs, đừng dùng từng event ID làm tag time-series metric. Event IDs thích hợp cho structured logs/traces hoặc truy vấn trong kho audit; metric tags nên là job type, outcome class, tenant tier... có số giá trị kiểm soát được.

## 22. Ma trận kiểm thử chống lặp tác dụng phụ

Không chỉ test “hàm gọi hai lần mà kết quả bằng nhau”. Hãy test cả lỗi và race.

| Kịch bản | Kỳ vọng |
|---|---|
| Cùng key gọi 2 lần tuần tự | Effect đúng một lần |
| Cùng key gọi song song 20 threads | Chỉ một transaction có thể commit marker/effect |
| Hai pod dùng cùng key | UNIQUE constraint phân xử, không nhân effect |
| Cùng key nhưng payload khác | Báo conflict, không bỏ qua âm thầm |
| Lỗi sau insert marker, trước business update | Rollback marker; retry vẫn có thể làm |
| Lỗi sau update, trước commit | Rollback toàn transaction, không để effect nửa vời |
| Crash sau commit, trước ACK | Retry thấy marker và không làm lại |
| External API thành công, response timeout | Reconcile hoặc retry cùng downstream idempotency key |
| Outbox publish lại sau restart | Consumer dedupe theo event ID |
| Lease hết hạn và owner cũ vẫn chạy | Fencing/idempotency ngăn stale write |
| TTL marker đã hết nhưng event cũ được replay | Có policy retention/archive hoặc từ chối |
| Retry sau khi qua nửa đêm | Dùng lại business key cũ |
| Downstream lỗi dài hạn | Backoff, quota, DLQ, không spam |
| Scheduler gặp lỗi định kỳ | ErrorHandler phù hợp; unit-of-work state vẫn đúng |

### 22.1. Fault injection nên đặt ở đâu?

Đặt những điểm kiểm thử crash/lỗi có chủ đích:

```text
1. Trước khi claim job
2. Sau claim, trước khi mở transaction nghiệp vụ
3. Sau insert marker, trước update
4. Sau update, trước commit
5. Sau commit, trước ACK
6. Sau publish external, trước ghi nhận external result
7. Sau lease expiry, trước worker cũ kết thúc
```

Chạy test bằng latch/barrier và timeout thay vì dựa vào `Thread.sleep(100)` để “chắc chắn thread đã đến đoạn cần test”. Tại đúng điểm lỗi, quan sát dữ liệu **sau rollback hoặc commit**, không chỉ log exception của ứng dụng.

## 23. Lỗi thường gặp khi thiết kế retry-safe scheduled jobs

| Sai lầm | Vì sao nguy hiểm | Cách sửa |
|---|---|---|
| Cứ lỗi là retry với UUID mới | Key khác -> trùng tác dụng phụ | Giữ key của công việc logic |
| Dùng `AtomicBoolean` trong bean để chống lặp | Mất khi restart, không bảo vệ multi-node | DB unique key / durable store |
| `SELECT` rồi `INSERT` không unique constraint | Hai worker đều có thể thấy chưa xử lý | Unique key + transaction |
| Ghi marker trước rồi commit, làm effect sau | Crash làm mất việc | Cùng DB: marker và effect cùng transaction |
| Làm effect trước rồi ghi marker sau | Crash gây duplicate | Atomic transaction hoặc downstream idempotency |
| Gọi API ngoài bên trong `@Transactional` và tin rollback undo | DB không rollback được HTTP/SMTP | Outbox/idempotency ở đích |
| Catch mọi exception rồi return OK | Giấu failure, có thể commit sai | Phân loại lỗi và rollback rõ |
| Cho rằng `@Scheduled` tự retry đúng event | Scheduler chỉ kích hoạt lượt | State/key ở unit of work |
| Chỉ dùng distributed lock | Lease expiry/crash vẫn có trùng | Idempotent effect và fencing |
| Xóa marker quá sớm | Replay cũ trở thành “mới” | Retention theo replay horizon |
| Dedupe key là tên job cho mọi ngày | Chặn cả kỳ mới hợp lệ | Key gồm business period |
| Dedupe bằng payload hash duy nhất | Có thể gộp nhầm yêu cầu khác | Event ID + scope/operation/version |
| Retry lỗi input vĩnh viễn | Bão lỗi, lãng phí tài nguyên | Permanent failure/DLQ |
| Không đo duplicate-skipped | Khó biết upstream có vấn đề | Metrics attempts vs effects |
| Để batch transaction quá dài | Lock/rollback lớn, dễ quá tải | Per-event/chunk atomicity |
| Tin “1 runner” nghĩa là “1 side effect” | At-least-once và timeouts vẫn lặp | Side-effect-level idempotency |

## 24. Bài tập thực hành

### Bài 1 — Tính khóa idempotency

Một tenant có báo cáo hàng ngày; ngày 29/10 job lỗi và được retry sáng 30/10. Nếu key được tạo bằng `LocalDate.now()` ở thời điểm retry, điều gì xảy ra?

**Đáp án định hướng:** Retry có khóa của ngày 30/10 thay vì khóa công việc ngày 29/10. Cần gắn business date khi tạo job và giữ qua mọi attempt.

### Bài 2 — Đọc bảng failure window

Một job insert marker rồi commit, sau đó gửi email. JVM crash trước email. Vì sao lần chạy lại có thể làm email bị mất?

**Đáp án định hướng:** Marker đã commit, retry tưởng đã xong và bỏ qua. Dùng outbox/event delivery với state và xử lý trùng ở bên nhận theo khả năng.

### Bài 3 — Cùng DB trong transaction

Trong `IdempotentSpringDemo`, vì sao `Marker sau rollback: 0` sau lỗi giả lập?

**Đáp án định hướng:** Marker và update nằm trong transaction Spring `@Transactional`; `IllegalStateException` ném trước update làm transaction rollback, xóa insert marker.

### Bài 4 — Hai pod cùng xử lý

Hai JVM cùng đọc `event-731`. Một distributed lock có thay thế unique constraint được không?

**Đáp án định hướng:** Không nên. Lock giảm tranh chấp, nhưng lease timeout, crash và retry vẫn có thể tạo overlap. Unique key đặt tại hiệu ứng DB giúp phân xử chính xác.

### Bài 5 — External gateway

Payment gateway trả timeout nhưng charge đã thành công. Có nên retry với idempotency key mới?

**Đáp án định hướng:** Không. Dùng lại cùng payment intent key nếu gateway hỗ trợ, hoặc query/reconcile trạng thái trước khi quyết định; tránh hai khoản thu.

### Bài 6 — Duplicate payload conflict

Hai event có cùng key nhưng giá trị tiền khác nhau. Có nên trả ALREADY_PROCESSED cho event thứ hai mà không kiểm tra payload?

**Đáp án định hướng:** Không. Lưu hash các trường nghiệp vụ quan trọng và từ chối conflict để phát hiện bug/gian lận.

### Bài 7 — Throughput và retry

Downstream bị 503 trong 10 phút. Fixed-delay 100 ms có đủ an toàn không?

**Đáp án định hướng:** Không. Cần backoff/jitter, circuit breaker, max attempts, rate limit, theo dõi backlog; idempotency không thay thế overload protection.

### Bài 8 — Dùng @Transactional đúng

Một `@Scheduled` method trong bean tự gọi `this.apply()` được đánh dấu `@Transactional`. Rủi ro là gì?

**Đáp án định hướng:** Self-invocation có thể bypass proxy trong chế độ Spring AOP mặc định. Tách transaction service bean hoặc dùng TransactionTemplate.

### Bài 9 — Tự chạy Java 17

Chạy `InMemoryIdempotencyDemo.java`. Vì sao tám callers chỉ có một caller accepted và vì sao vẫn không dùng nó làm giải pháp lưu bền vững?

**Đáp án định hướng:** `ConcurrentHashMap.newKeySet().add(key)` nguyên tử trong một JVM; map không persist, không có transaction với side effect ngoài, không bảo vệ các node khác.

### Bài 10 — Tự chạy Spring JDBC

Chạy `IdempotentSpringDemo` và sửa lần đầu thành `failAfterMarker=false`. Kỳ vọng lần đầu đã commit và lần thứ hai như thế nào?

**Đáp án định hướng:** Marker và balance update cùng commit ngay ở lần đầu; lần kế tiếp với cùng key sẽ bị unique constraint chặn, số dư chỉ tăng một lần.

### Bài 11 — Retention

Marker lưu 1 ngày nhưng nghiệp vụ cho replay trong 30 ngày. Tác hại là gì?

**Đáp án định hướng:** Event cũ sau cleanup có thể được xem là mới và thực hiện lại effect. Retention phải phù hợp replay horizon hoặc có cơ chế archive/reject cũ.

### Bài 12 — Thiết kế end-to-end

Thiết kế job xử lý invoice vừa cập nhật số dư trong database vừa gửi email báo đã cộng điểm. Chọn transaction, outbox và dedupe keys như thế nào?

**Đáp án định hướng:** Cập nhật balance và insert marker/outbox event trong cùng DB transaction với key từ invoice; relay gửi event theo at-least-once; email consumer/provider có dedupe/reconciliation theo event ID tùy khả năng, không tự tuyên bố exactly-once email nếu provider không hỗ trợ.

## 25. Checklist review production

1. Đã định nghĩa **một công việc logic** và tác dụng phụ nào cần không lặp?
2. Idempotency key ổn định qua retry, crash, restart, replay và không chặn kỳ mới?
3. Khóa gồm tenant/operation/business ID hoặc period phù hợp?
4. Cùng khóa payload khác được kiểm tra và từ chối?
5. DB có unique constraint thật, không chỉ `SELECT` kiểm tra trước?
6. Marker và effect **trong cùng một DB** được commit/rollback nguyên tử?
7. Transaction `@Transactional` thực sự đi qua Spring proxy/manager đúng?
8. Lỗi muốn rollback không bị catch/suppress vô ý trong transaction?
9. Có cách xử lý unique conflict ngoài transaction hoặc bằng SQL conflict-safe?
10. Side effect external không bị coi là nằm trong local DB transaction?
11. Nếu gửi message/event sau commit, có outbox hoặc cơ chế tin cậy tương đương?
12. Consumer/gateway có idempotency contract và retention được xác minh?
13. Có kế hoạch xử lý outcome không rõ do timeout?
14. Có retry budget, jitter/backoff, circuit breaker và permanent failure state?
15. Mỗi pod/worker có cơ chế claim/lease phù hợp và đã xét stale owner?
16. Không chỉ dựa vào `@Scheduled`, `synchronized` hoặc distributed lock để chống duplicate?
17. Có checkpoint/batch policy không mất và không nhân đôi records?
18. Marker retention phù hợp late replay và chính sách dữ liệu?
19. Có metrics riêng cho attempts, duplicate skips và committed effects?
20. Có concurrency tests, crash injection, duplicate payload mismatch và multi-node tests?
21. Có tài liệu mô tả ranh giới **exactly-once effect** thay vì tuyên bố exactly-once execution toàn hệ thống?

## 26. Tổng kết

- **Scheduled jobs phải giả định có thể chạy lại**, vì timeout, lỗi, restart, retry, cron và nhiều JVM đều có thể tạo lần thử trùng.
- **Idempotency phụ thuộc định danh công việc logic**, không phụ thuộc thread, invocation count hay UUID mới sinh ở mỗi retry.
- **Cùng một database:** unique constraint, marker và business update **trong một transaction** giúp việc thử lại không nhân đôi tác dụng phụ được bảo vệ.
- **Ra ngoài database:** cần idempotency ở hệ thống đích, transactional outbox, đối soát và chính sách delivery rõ ràng; local transaction không rollback được email/HTTP.
- **Spring `@Scheduled`/`ErrorHandler` điều phối lượt thực thi và lỗi, không tự biến side effects thành idempotent.**
- **Retry có thể xảy ra nhiều lần, nhưng effects phải được kiểm soát theo khóa.** Kết hợp backoff, timeout, claim/lease, fencing, observability và test failure windows.
- **Không nhầm “một worker được giao” với “một kết quả nghiệp vụ”**. Ranh giới commit và khóa chống trùng mới là nơi cần đặt bảo đảm.

### Tài liệu tham khảo

- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework Javadoc — TaskUtils, default error handlers](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/scheduling/support/TaskUtils.html)
- [Spring Framework Reference — Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Spring Framework Reference — Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- [Spring Framework Reference — Declarative Transaction Implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html)
- [Spring Integration — Idempotent Receiver Enterprise Integration Pattern](https://docs.spring.io/spring-integration/reference/handler-advice/idempotent-receiver.html)
- [Spring Integration — Metadata Store](https://docs.spring.io/spring-integration/reference/meta-data-store.html)
- [PostgreSQL Documentation — INSERT and ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html)
- [AWS Prescriptive Guidance — Transactional Outbox Pattern](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html)
- [Microservices.io — Idempotent Consumer Pattern](https://microservices.io/patterns/communication-style/idempotent-consumer.html)
- [Java SE 17 API — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduling-failures-008">Đối chiếu hành vi scheduler Spring với ScheduledExecutorService trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu hành vi scheduler Spring với ScheduledExecutorService trực tiếp

## 1. Vì sao hai scheduler cùng chạy trên JDK nhưng xử lý lỗi khác nhau?

Trong Java, `ScheduledExecutorService` là API chuẩn để chạy một `Runnable` hoặc `Callable` sau một khoảng trễ hay lặp lại theo fixed-rate/fixed-delay. Spring Framework cung cấp `TaskScheduler` cùng các implementation như `ThreadPoolTaskScheduler`, có khả năng sử dụng `ScheduledThreadPoolExecutor` của JDK bên dưới nhưng thêm abstraction, error handling, trigger, lifecycle và tích hợp `@Scheduled`.

**Điểm khác biệt rất quan trọng khi tác vụ định kỳ ném exception:**

- Với **`ScheduledExecutorService.scheduleAtFixedRate(...)` hoặc `scheduleWithFixedDelay(...)` trực tiếp**, một lần chạy ném exception không được bắt khiến **những lần chạy tiếp theo của chính registration đó bị ngừng**. `ScheduledFuture.get()` báo `ExecutionException`; `isDone()` trở thành `true`.
- Với **`ThreadPoolTaskScheduler` của Spring theo chính sách error handling mặc định**, tác vụ **lặp lại** được bọc bởi handler ghi log và **không ném tiếp lỗi ra scheduled executor**. Vì vậy lịch có thể tiếp tục ở kỳ sau dù một lần chạy đã thất bại.
- Với **tác vụ one-shot** được nộp qua Spring `schedule(Runnable, Instant)`, chính sách mặc định **ghi log rồi truyền lại lỗi** để `ScheduledFuture.get()` có thể quan sát thất bại.
- Nếu ứng dụng **đặt `ErrorHandler` tùy chỉnh** hoặc dùng một scheduler implementation khác, kết quả có thể thay đổi. Không nên suy rộng chính sách mặc định trên đây thành hợp đồng chung của mọi class Spring, mọi `@Scheduled` reactive hay mọi `Executor`.

Sự khác nhau này dễ tạo hai lỗi vận hành ngược nhau:

1. **JDK trực tiếp:** job im lặng ngừng chạy sau một exception, nhưng người quản trị tưởng nó vẫn đang lặp.
2. **Spring mặc định:** job vẫn tick sau lỗi, nhưng người quản trị tưởng exception đã chấm dứt lịch hoặc `ScheduledFuture.get()` sẽ tự trả về lỗi của từng lần chạy.

**Mục tiêu bài học:** phân biệt các API, hiểu lớp bọc lỗi của Spring, so sánh one-shot và periodic tasks, viết ví dụ có thể chạy/kiểm chứng, chọn chính sách dừng/tiếp tục phù hợp, và vận hành các lịch có timeout, cancellation, monitoring và cleanup rõ ràng.

Bài dùng **Java 17+ và Spring Framework 6.2.x**. Các đoạn nhắc `ThreadPoolTaskScheduler` nói về implementation đó; những ngoại lệ của `SimpleAsyncTaskScheduler`, Quartz và reactive `@Scheduled` sẽ được chỉ ra riêng.

## 2. Hai tầng abstraction và nơi thực thi thật sự

Nhìn từ ngoài, một Spring scheduler có thể giống một JDK scheduled executor:

```text
Ứng dụng dùng JDK trực tiếp                   Ứng dụng dùng Spring
──────────────────────────────                ─────────────────────────────
ScheduledExecutorService                       TaskScheduler
    |                                               |
    +--> ScheduledThreadPoolExecutor                 +--> ThreadPoolTaskScheduler
                 |                                            |
                 +--> FutureTask / delayed task               +--> error-handler wrapper
                 |                                            +--> TaskDecorator (nếu có)
                 +--> worker thread                           +--> ScheduledThreadPoolExecutor
                                                                      |
                                                                      +--> worker thread
```

`ThreadPoolTaskScheduler` là adapter Spring bao quanh `ScheduledThreadPoolExecutor`, với pool size mặc định **1**. Task chạy trên các scheduler threads của pool; `ScheduledFuture` của nó thường phản ánh **completion thật của callback** hoặc chuỗi lần chạy đang được lịch quản lý.

Ngược lại, `SimpleAsyncTaskScheduler` (Spring 6.1+) có mô hình **một scheduler thread và thread-per-execution** cho một số đường kích hoạt; fixed-delay là ngoại lệ chạy trực tiếp trên scheduler thread. Vì thế không thể mặc định `ScheduledFuture` của mọi Spring scheduler luôn đại diện cùng loại completion như `ThreadPoolTaskScheduler`.

### Không phải cùng tên là cùng hành vi

| Khía cạnh | `ScheduledExecutorService` trực tiếp | Spring `TaskScheduler` / `ThreadPoolTaskScheduler` |
| --- | --- | --- |
| API | `java.util.concurrent` | `org.springframework.scheduling` |
| Mốc one-shot | Delay + `TimeUnit` | `Instant` |
| Fixed-rate/fixed-delay | Delay/period + `TimeUnit` | `Duration` và có thể có `Instant` bắt đầu |
| Cron/Trigger | Không có cron built-in trong interface | `Trigger` / `CronTrigger` |
| Default periodic error | Exception không bắt được làm ngừng các lần kế tiếp | Handler mặc định thường log và suppress lỗi của periodic callback |
| Default one-shot error | Future ghi exception; `get()` báo lỗi | Spring mặc định log và propagate với one-shot |
| Error customization | Bọc `Runnable`, `Future.get()`, custom executor hooks | `setErrorHandler(ErrorHandler)` cùng cách bọc task của Spring |
| Lifecycle | Caller phải quản lý `shutdown`, `awaitTermination` | Spring-managed bean có lifecycle/context close; tự tạo vẫn phải initialize/shutdown |
| Rejection | `RejectedExecutionException` | Thường `TaskRejectedException` ở caller của Spring API |
| Chạy cụm nhiều pod | Không tự coordination | Cũng không tự coordination chỉ vì dùng Spring |
| Durability qua restart | Không có mặc định | `ThreadPoolTaskScheduler` vẫn không có mặc định |

Điều quan trọng là **lỗi xảy ra ở tầng nào và đi qua wrapper nào**, không chỉ “thư viện nào chạy thread”.

## 3. API JDK: ScheduledExecutorService dùng delay tương đối

API phổ biến:

```java
ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2);

ScheduledFuture<?> once = scheduler.schedule(
        () -> sendReminder(),
        5, TimeUnit.SECONDS);

ScheduledFuture<?> rate = scheduler.scheduleAtFixedRate(
        () -> pollStatus(),
        0, 10, TimeUnit.SECONDS);

ScheduledFuture<?> delay = scheduler.scheduleWithFixedDelay(
        () -> cleanup(),
        0, 10, TimeUnit.SECONDS);
```

Đây là **snippet tích hợp** với các hàm nghiệp vụ và import JDK tương ứng; ví dụ full runnable ở mục 8. Các tham số là **delay/period tương đối**, không phải một `Instant` tuyệt đối.

- `schedule(task, delay, unit)`: kích hoạt **một lần** sau delay.
- `scheduleAtFixedRate(task, initialDelay, period, unit)`: các mốc start dự kiến theo nhịp cố định. Khi xử lý chậm, lần sau có thể trễ nhưng **các lần cùng một periodic task không overlap** trong `ScheduledThreadPoolExecutor`.
- `scheduleWithFixedDelay(task, initialDelay, delay, unit)`: chờ **lần trước hoàn tất** rồi đợi thêm delay mới chạy tiếp.
- `schedule(Callable<V>, delay, unit)`: có thể trả kết quả giá trị `V` thông qua `ScheduledFuture<V>`.
- `shutdown()` / `shutdownNow()`: lifecycle do ứng dụng quản lý khi không có container khác sở hữu executor.

`Executors.newScheduledThreadPool(2)` dùng JDK `ScheduledThreadPoolExecutor` bên dưới. Mặc định scheduled queue của implementation này là delayed queue **không có dung lượng giới hạn hữu hạn cố định**. Việc tăng `maximumPoolSize` không có tác dụng giống một `ThreadPoolExecutor` có bounded queue thông thường.

### Cần hẹn thời điểm tuyệt đối thì sao?

JDK `ScheduledExecutorService` không có overload `schedule(Runnable, Instant)`. Có thể tính delay:

```java
Instant when = Instant.parse("2026-11-03T09:00:00Z");
long waitMillis = Math.max(0,
        Duration.between(Instant.now(), when).toMillis());

ScheduledFuture<?> task = scheduler.schedule(
        () -> deliver(),
        waitMillis, TimeUnit.MILLISECONDS);
```

Đây là snippet tích hợp; cần import `Instant`, `Duration`, `TimeUnit` và biến `scheduler`. Cách này biến **wall-clock instant thành relative delay tại thời điểm nộp**. Thay đổi clock sau khi đã nộp không nhất thiết dời lịch đã đặt theo wall clock, và độ chính xác khi thức dậy vẫn phụ thuộc scheduler/JVM/OS. Không giả định nó tự có time zone hoặc daylight-saving policy cho các lần lặp.

## 4. API Spring: TaskScheduler dùng Instant, Duration và Trigger

Với `TaskScheduler`:

```java
ScheduledFuture<?> once = taskScheduler.schedule(
        () -> sendReminder(),
        Instant.now().plusSeconds(5));

ScheduledFuture<?> rate = taskScheduler.scheduleAtFixedRate(
        () -> pollStatus(),
        Duration.ofSeconds(10));

ScheduledFuture<?> delay = taskScheduler.scheduleWithFixedDelay(
        () -> cleanup(),
        Duration.ofSeconds(10));

ScheduledFuture<?> cron = taskScheduler.schedule(
        () -> generateReport(),
        new CronTrigger("0 30 9 * * MON-FRI"));
```

Đây là các **snippet** có `taskScheduler` và hàm nghiệp vụ đã tồn tại, không phải một file độc lập.

Spring cung cấp các overload để chọn `Instant` bắt đầu, nên không cần tính độ trễ từ epoch timestamp bằng tay ở mỗi caller. Nó cũng cho phép dùng:

- `CronTrigger`: cron **sáu trường của Spring**, có thể đi kèm `ZoneId`.
- `PeriodicTrigger`: cấu hình lịch theo period/initial delay bằng đối tượng Trigger.
- Trigger tự viết: tính lần chạy kế tiếp dựa trên `TriggerContext` và `Clock`.
- `@Scheduled`: cách khai báo lịch trên Spring-managed bean khi có `@EnableScheduling`.

**Chú ý:** `TaskScheduler.schedule(Runnable, Trigger)` có thể trả `null` nếu trigger không cho lần chạy tiếp theo. Khi dùng JDK trực tiếp, API `scheduleAtFixedRate` không phải parser cron hoặc một bộ lịch động theo tháng/ngày; muốn hành vi tương đương phải tự xây trigger management hoặc dùng thư viện phù hợp.

## 5. Điểm khác biệt nhất: exception của periodic task

### 5.1. JDK trực tiếp: lỗi không bắt được sẽ dừng registration định kỳ

Theo Javadoc của Java `ScheduledThreadPoolExecutor` và `ScheduledExecutorService`:

- Nếu một lần chạy trong `scheduleAtFixedRate` hoặc `scheduleWithFixedDelay` **ném exception ra ngoài**, các lần thực thi tiếp theo **bị suppress**.
- `ScheduledFuture<?>` có thể vào trạng thái done/exceptions: `future.isDone() == true`.
- Gọi `future.get()` trả bằng một `ExecutionException` có `cause` là lỗi của task.
- Lỗi do scheduled task ném thường được **FutureTask/RunnableScheduledFuture giữ lại**, không có bảo đảm một `Thread.UncaughtExceptionHandler` hoặc `afterExecute(..., Throwable)` của executor sẽ nhận exception đó trực tiếp.

Ví dụ luồng chạy:

```text
JDK direct scheduleAtFixedRate
         |
         +--> tick #1: task.run()
         |                  |
         |                  +--> throws IllegalStateException
         |
         +--> underlying ScheduledFuture records FAILURE
         |
         +--> future.isDone() = true
         |
         +--> future.get() -> ExecutionException(cause=IllegalStateException)
         |
         +--> tick #2, #3 ... KHÔNG diễn ra cho cùng registration
```

**Điều này không nhất thiết giết cả thread pool.** Một registration periodic bị dừng vì lỗi; các tác vụ khác của executor vẫn có thể được chạy. Nếu muốn job sau thất bại vẫn chạy, phải thiết kế chính sách riêng ở task wrapper.

### 5.2. Spring ThreadPoolTaskScheduler: mặc định tiếp tục periodic job sau lỗi

Spring dùng lớp bọc `DelegatingErrorHandlingRunnable` và chính sách mặc định thông qua `TaskUtils` để xử lý `Throwable` từ callback. Với **repeating task** trên đường scheduler thông thường, Spring mặc định:

1. Bắt lỗi thoát khỏi `Runnable.run()`.
2. Ghi log ở mức lỗi.
3. **Suppress**, tức không truyền exception trở lại `ScheduledThreadPoolExecutor`.
4. Lịch vẫn tiếp tục ở mốc sau nếu chưa bị hủy hoặc shutdown.

```text
Spring ThreadPoolTaskScheduler.scheduleAtFixedRate
         |
         +--> tick #1: business.run()
         |                  |
         |                  +--> throws IllegalStateException
         |                          |
         |           Spring wrapper ErrorHandler logs & suppresses
         |
         +--> JDK scheduled callback returns normally
         |
         +--> tick #2 vẫn có thể chạy
         +--> tick #3 vẫn có thể chạy
```

**Quan trọng:** “lịch tiếp tục” **không phải** “công việc lỗi đã thành công”, “DB transaction tự rollback”, “Spring retry cùng một tick” hoặc “lỗi được caller nhận qua future”. Đây là **lần kích hoạt định kỳ mới**, không phải retry có backoff của công việc trước.

Nếu một job liên tục thất bại, Spring vẫn có thể gọi lại theo lịch và ghi log lỗi nhiều lần. Vì vậy production cần metric, alert, retry budget/circuit breaker và chính sách dừng có chủ đích khi lỗi nghiêm trọng.

### 5.3. Bảng so sánh trạng thái Future

| Sự kiện | JDK direct periodic | Spring ThreadPoolTaskScheduler periodic mặc định |
| --- | --- | --- |
| Lần chạy #1 ném exception | Registration dừng | Exception được log, registration thường tiếp tục |
| `future.isDone()` sau lỗi đầu | `true` | Thường vẫn `false` nếu chưa cancel/shutdown |
| `future.get()` sau lỗi đầu | `ExecutionException` | Có thể tiếp tục **chờ vô hạn** vì future vẫn đại diện chuỗi periodic |
| Lần chạy #2 | Không xảy ra | Có thể xảy ra ở kỳ kế tiếp |
| Log lỗi mặc định | Không nên mặc định có log từ Future | Spring error handler thường log |
| Muốn đổi hành vi | Bọc Runnable và tự catch/rethrow | `setErrorHandler(...)` hoặc catch trong nghiệp vụ |

**Không gọi `get()` vô thời hạn để “kiểm tra một periodic task có lỗi chưa” trên Spring default**. Nếu lịch vẫn active, `get()` có thể chặn vô hạn. Sử dụng timeout, metrics, event/status nghiệp vụ hoặc điều khiển hủy tách biệt.

## 6. One-shot task: hai bên gần nhau hơn về exception

Với JDK:

```java
ScheduledFuture<?> future = scheduler.schedule(
        () -> {
            throw new IllegalArgumentException("one-shot failed");
        },
        0, TimeUnit.MILLISECONDS);
```

Với Spring:

```java
ScheduledFuture<?> future = taskScheduler.schedule(
        () -> {
            throw new IllegalArgumentException("one-shot failed");
        },
        Instant.now());
```

Hai đoạn trên là **snippet ở hai môi trường độc lập**, dùng biến `scheduler` hay `taskScheduler` tương ứng.

- Cả hai đều chỉ tạo **một lần chạy**; không có “tick sau” để duy trì.
- Khi exception thực sự thoát khỏi callback, JDK ScheduledFuture giữ lỗi, `future.get()` ném `ExecutionException`.
- Với `ThreadPoolTaskScheduler` theo **error handler mặc định cho one-shot**, Spring ghi log và **propagate exception**; Future cũng có thể phản ánh lỗi qua `get()`.
- Nếu một ứng dụng đặt custom `ErrorHandler` chỉ log rồi suppress **cho mọi task**, one-shot ở Spring có thể kết thúc dưới góc nhìn Future như callback hoàn tất bình thường, dù nghiệp vụ thất bại. Đây là thay đổi semantics nguy hiểm nếu caller dựa vào `future.get()` để kiểm tra thành công.

**Lưu ý:** `TaskScheduler.schedule(Runnable, Instant)` chỉ trả `ScheduledFuture<?>` của Runnable, không phải API `Callable<V>` để tự động trả giá trị nghiệp vụ. Nếu cần kết quả `V`, có thể dùng `CompletableFuture<V>` riêng, hoặc JDK `ScheduledExecutorService.schedule(Callable<V>, delay, unit)` khi appropriate.

## 7. Ví dụ Maven Java 17: so sánh trực tiếp hai scheduler

Ví dụ dùng `ScheduledExecutorService` của JDK và `ThreadPoolTaskScheduler` Spring Framework 6.2.14 trong cùng một chương trình. Thay vì chờ khoảng thời gian dài để đoán task đã chạy bao nhiêu lần, ta dùng:

- `Future.get(timeout)` để xác minh JDK periodic đã kết thúc vì lỗi.
- `CountDownLatch` để xác minh Spring thực sự chạy được **tick thứ hai** sau khi tick đầu ném lỗi.
- `ScheduledFuture.cancel(false)` để dừng Spring periodic có kiểm soát.
- Một one-shot Spring để xác nhận lỗi truyền qua `Future.get()`.

**Điều kiện:** Java 17+, Maven 3.x, Spring Framework `6.2.14`.

Cấu trúc:

```text
spring-vs-jdk-scheduler/
├── pom.xml
└── src/main/java/demo/SchedulerErrorComparisonDemo.java
```

### 7.1. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-vs-jdk-scheduler</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.14</version>
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
                <configuration>
                    <mainClass>demo.SchedulerErrorComparisonDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 7.2. src/main/java/demo/SchedulerErrorComparisonDemo.java

```java
package demo;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public final class SchedulerErrorComparisonDemo {

    public static void main(String[] args) throws Exception {
        compareDirectJdk();
        compareSpringDefaults();
    }

    private static void compareDirectJdk() throws Exception {
        ScheduledExecutorService pool =
                Executors.newSingleThreadScheduledExecutor();
        AtomicInteger runs = new AtomicInteger();

        try {
            ScheduledFuture<?> future = pool.scheduleAtFixedRate(() -> {
                runs.incrementAndGet();
                throw new IllegalStateException("jdk failure");
            }, 0, 20, TimeUnit.MILLISECONDS);

            String cause = "NONE";
            try {
                // Dừng bằng lỗi ngay ở tick #1:
                future.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                cause = ex.getCause().getClass().getSimpleName();
            }

            System.out.println("jdkRuns=" + runs.get());
            System.out.println("jdkFutureDone=" + future.isDone());
            System.out.println("jdkFailureCause=" + cause);
        } finally {
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JDK pool not terminated");
            }
        }
    }

    private static void compareSpringDefaults() throws Exception {
        ThreadPoolTaskScheduler spring = new ThreadPoolTaskScheduler();
        spring.setPoolSize(1);
        spring.setThreadNamePrefix("spring-scheduler-");
        spring.initialize(); // Demo CLI ngoài ApplicationContext.

        AtomicInteger runs = new AtomicInteger();
        CountDownLatch secondRun = new CountDownLatch(1);
        ScheduledFuture<?> periodic = null;

        try {
            periodic = spring.scheduleAtFixedRate(() -> {
                int count = runs.incrementAndGet();
                if (count == 1) {
                    throw new IllegalStateException("spring first tick");
                }
                secondRun.countDown();
            }, Duration.ofMillis(30));

            if (!secondRun.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Spring did not reach second tick");
            }

            // Boolean ổn định dù scheduler kịp chạy thêm tick nữa.
            System.out.println(
                    "springReachedSecondRun=" + (runs.get() >= 2));
            System.out.println(
                    "springDoneBeforeCancel=" + periodic.isDone());

            periodic.cancel(false);
            System.out.println(
                    "springCancelled=" + periodic.isCancelled());

            ScheduledFuture<?> once = spring.schedule(
                    () -> {
                        throw new IllegalArgumentException("one-shot");
                    },
                    Instant.now());

            String oneShotCause = "NONE";
            try {
                once.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                oneShotCause =
                        ex.getCause().getClass().getSimpleName();
            }
            System.out.println("springOneShotCause=" + oneShotCause);
        } finally {
            if (periodic != null) {
                periodic.cancel(false);
            }
            spring.shutdown();
        }
    }
}
```

### 7.3. Chạy và đối chiếu

Trong thư mục `spring-vs-jdk-scheduler`:

```bash
mvn -q compile exec:java
```

**Những dòng do `System.out.println` sinh ra khi chạy thành công:**

```text
jdkRuns=1
jdkFutureDone=true
jdkFailureCause=IllegalStateException
springReachedSecondRun=true
springDoneBeforeCancel=false
springCancelled=true
springOneShotCause=IllegalArgumentException
```

**Spring mặc định sẽ còn ghi log lỗi** cho tick đầu tiên và one-shot, có thể xuất hiện trên stderr hoặc trong output tích hợp của Maven tùy logging backend. Các log này **không thuộc bảy dòng stdout nghiệp vụ** kể trên và không nên bị nhầm là thêm một lần chạy business.

### Giải thích chi tiết

1. **JDK tick #1:** callback tăng `runs` lên 1 rồi ném `IllegalStateException`. JDK ghi lỗi vào ScheduledFuture, ngừng lần chạy định kỳ tiếp theo. `future.get(5s)` nhận `ExecutionException`, nên `jdkRuns=1`, `jdkFutureDone=true` và cause là `IllegalStateException`.
2. **Spring tick #1:** callback tăng `runs` lên 1 rồi ném `IllegalStateException`. `ThreadPoolTaskScheduler` bọc repeating task bằng handler mặc định **log & suppress**, nên JDK executor bên dưới không nhìn thấy exception ở bề mặt callback.
3. **Spring tick #2:** lịch vẫn chạy, tăng biến đếm và đánh dấu `secondRun`. Latch được mở, chứng minh có ít nhất hai lần thực thi. Vì số lần chạy có thể là 2 hoặc 3 tùy thời gian, ví dụ **không** in giá trị `runs.get()` cố định.
4. **Spring future trước cancel:** repeating future vẫn active nên `isDone()` là `false`. Điều này **không chứng minh chưa từng có lỗi** — lỗi đã được Spring log và suppress.
5. **Cancel:** `periodic.cancel(false)` yêu cầu hủy lịch mà không interrupt callback đang chạy. `isCancelled()` báo `true` khi hủy thành công.
6. **Spring one-shot:** lỗi `IllegalArgumentException` được log và propagate theo chính sách mặc định one-shot; `once.get(...)` cho `ExecutionException` với cause tương ứng.
7. **Cleanup:** chương trình đóng JDK scheduler và Spring scheduler, không để một periodic task chạy vô hạn sau khi main kết thúc.

**QA của ví dụ:** nhánh JDK `ScheduledExecutorService` độc lập đã được biên dịch bằng `javac --release 17` và chạy trên JDK hiện có; ba dòng JDK khớp đầu ra ở trên. Phần Spring 6.2.14 được đối chiếu API/Javadoc và source policy, nhưng **chưa thực thi toàn bộ Maven demo trong môi trường hiện tại**; hãy chạy lệnh Maven trên dự án có dependency để xác nhận trong runtime của bạn.

Không nên dùng `Thread.sleep(100)` rồi đếm tick để suy ra số lần chạy, vì lịch thực tế còn bị JVM/OS scheduling, log và CI load chi phối. Latch/Future làm cho assertion có cơ sở rõ ràng hơn.

## 8. ErrorHandler của Spring thay đổi “dừng hay chạy tiếp” thế nào?

Spring cung cấp `org.springframework.util.ErrorHandler` với một phương thức `handleError(Throwable)`. Với `ThreadPoolTaskScheduler`:

```java
ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setPoolSize(2);
scheduler.setErrorHandler(error -> {
    // Ứng dụng tự quyết định: log, count, propagate hay suppress.
    // Nếu chỉ return bình thường: lỗi được suppress.
});
```

Đây là **snippet cấu hình**, không phải một main độc lập. Nó cố tình cho thấy **return bình thường từ handler** đồng nghĩa callback wrapper không ném exception ra executor.

### 8.1. Mặc định của Spring phân biệt repeating và one-shot

Spring dùng các chiến lược trong `TaskUtils`:

| Chính sách | Cách xử lý Throwable | Hệ quả với periodic JDK executor bên dưới |
| --- | --- | --- |
| `LOG_AND_SUPPRESS_ERROR_HANDLER` | Ghi log rồi return | Lịch tiếp tục vì callback không ném exception |
| `LOG_AND_PROPAGATE_ERROR_HANDLER` | Ghi log rồi ném lại | Lịch periodic có thể ngừng sau lỗi, Future có thể exceptional |
| Custom `ErrorHandler` chỉ log | Return bình thường | Lỗi bị suppress |
| Custom `ErrorHandler` có rethrow | Truyền lại exception | Phụ thuộc loại task, nhưng periodic JDK có thể dừng |
| Catch bên trong business task và không rethrow | ErrorHandler thậm chí không thấy lỗi | Lịch có thể tiếp tục như bình thường |

Spring chọn **LOG_AND_SUPPRESS cho repeating task** và **LOG_AND_PROPAGATE cho one-shot task** theo mặc định. Nếu gọi `setErrorHandler(...)`, bạn đang thay policy mặc định, nên cần xem cả hai loại job trong cùng scheduler.

### 8.2. Muốn thất bại một lần thì dừng các lần tiếp theo

Có thể dùng handler propagate:

```java
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setPoolSize(2);
scheduler.setErrorHandler(TaskUtils.LOG_AND_PROPAGATE_ERROR_HANDLER);
```

Đây là **snippet tích hợp**; cần tạo bean hoặc gọi `initialize()` nếu tự khởi tạo ngoài Spring context. Khi một periodic callback ném exception, handler sẽ log và ném lại; nền JDK nhận exceptional completion và ngừng các tick của registration.

**Không áp dụng hàng loạt chính sách này một cách vô thức:** một lỗi tạm thời trong lần polling có thể khiến job ngừng mãi tới khi được đăng ký lại. Với job giám sát hoặc đồng bộ phải tiếp tục kiểm tra, việc “ngừng sau lần lỗi đầu” có thể tệ hơn “ghi log rồi chạy tiếp”. Cần metric/job health để phát hiện và cơ chế restart/retry có kiểm soát.

### 8.3. Muốn tiếp tục sau lỗi nhưng không che giấu sự cố

Có thể dùng handler **log + counter + suppress**:

```java
scheduler.setErrorHandler(error -> {
    failedTickCounter.increment(); // Metric do ứng dụng cung cấp.
    logger.error("Scheduled task failed", error);
    // Không rethrow: các lần scheduled tick sau có thể tiếp tục.
});
```

Đây là snippet với `failedTickCounter` và `logger` do ứng dụng cung cấp. Phải tránh việc cả business method và ErrorHandler cùng tăng một counter cho **cùng một lỗi**, gây double count. Nên định nghĩa một owner cho metric và log.

Nếu cần **retry chính xác một tác vụ thất bại** thay vì chờ kỳ cron tiếp theo, hãy dùng retry policy/budget và idempotency riêng; không mặc định rằng “tiếp tục lịch” là retry.

### 8.4. Nguy cơ suppress ở one-shot

Một custom handler return bình thường có thể khiến `Future.get()` của one-shot xem callback là hoàn tất bình thường mặc dù bên trong đã có exception. Điều này ảnh hưởng trực tiếp tới nơi gọi đang chờ kết quả. Nếu hệ thống xử lý lẫn one-shot và periodic trên cùng scheduler, cần test **cả hai nhánh** sau mỗi thay đổi ErrorHandler.

### 8.5. ErrorHandler không thay thế hoàn toàn try/catch nghiệp vụ

ErrorHandler biết `Throwable` ở callback boundary, nhưng không nhất thiết có `orderId`, `tenantId`, loại remote API hay số lần retry. Ở business method, catch exception phù hợp để làm rollback/retry/fallback có context; sau đó quyết định **rethrow hay không** theo semantics của lịch. Đừng ghi log rồi nuốt lỗi ở nhiều tầng khiến job trông như thành công nhưng dữ liệu bị xử lý dang dở.

## 9. Khi Exception nằm trong FutureTask, handler nào nhìn thấy nó?

JDK `ScheduledThreadPoolExecutor` thường bọc callback thành `RunnableScheduledFuture` (một biến thể lưu completion state như `FutureTask`). Do đó:

```text
Business Runnable (có thể ném exception)
             |
             +--> Spring DelegatingErrorHandlingRunnable
                         |
                         +--> error handler log/suppress/propagate
                                     |
                                     v
                             JDK RunnableScheduledFuture
                                     |
                                     +--> stores final state
                                     +--> worker thread
```

Với Spring repeating **suppress**, exception được xử lý trước khi đi vào Future của JDK: `Future.get()` của periodic sẽ không có `ExecutionException` từ lỗi đã suppress.

Với JDK direct **không có Spring wrapper**, exception đi vào Future state và gây ngừng periodic sequence.

Điều này giải thích vì sao hai cấu hình đều dùng một JDK executor bên dưới nhưng vẫn có kết quả khác. **Nguồn khác biệt là exception boundary của wrapper**, không phải Spring có “thread engine” hoàn toàn khác.

### Vì sao afterExecute(Throwable) hoặc UncaughtExceptionHandler có thể không đủ?

Trong `ScheduledThreadPoolExecutor`, một periodic task có thể ghi exception vào `FutureTask` thay vì ném ra khỏi phương thức `run()` của wrapper. Vì vậy tham số `Throwable` trong `afterExecute(Runnable, Throwable)` có thể là `null` dù Future đã hoàn tất thất bại. UncaughtExceptionHandler cũng không nhất thiết nhận lỗi.

Muốn quan sát JDK direct failure, có thể:

- Giữ `ScheduledFuture<?>` và giám sát `isDone()`/`get()` khi future đã done (không block vô hạn).
- Bọc Runnable để log và đặt trạng thái lỗi trước khi rethrow, nếu muốn stop-on-failure.
- Dùng callback/reporting vào một status store ứng dụng có owner rõ.
- Với custom `afterExecute`, cân nhắc Future inspection như gợi ý JDK, nhưng hiểu rằng Spring wrapper, executor adapter và custom Runnable có thể che loại Future gốc.

**Không dùng `future.get()` trong cùng worker của một periodic task đang chạy** để đợi chính periodic future ấy: nó có thể không bao giờ hoàn tất bình thường và tạo deadlock/starvation.

## 10. @Scheduled không phải là lời gọi trực tiếp ScheduledExecutorService

Spring `@Scheduled` được đăng ký bởi scheduling infrastructure, thường thông qua `@EnableScheduling`:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
```

Một job:

```java
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InventoryRefreshJob {

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    public void refresh() {
        // Nếu ném lỗi: quy tắc ErrorHandler của scheduler được chọn.
        // Không mặc định nó giống submit raw ScheduledExecutorService.
    }
}
```

Đây là **các class Spring integration**, không phải chương trình JDK độc lập.

Những điều cần hiểu:

1. `@Scheduled` yêu cầu bean nằm trong Spring ApplicationContext và scheduling infrastructure được bật.
2. Spring đăng ký callback tại startup/context lifecycle; bạn không trực tiếp giữ `ScheduledFuture<?>` ở business method như code manual.
3. Spring tìm scheduler theo bean/config. Nếu không có bean phù hợp, framework có thể tạo scheduler mặc định cục bộ; hãy xác minh scheduler thực tế để biết pool/error policy.
4. Nếu muốn cấu hình error handler, tạo scheduler bean thích hợp rồi chọn nó bằng `@Scheduled(scheduler="...")` (Spring Framework 6.1+).
5. Nếu nhiều `@Scheduled` trên cùng method hoặc nhiều bean instances, có thể có **nhiều registrations độc lập**, có thể overlap mặc dù từng registration riêng lẻ được serialize.
6. Reactive `@Scheduled` có semantics error đặc biệt; xem mục 11.

**Một điều dễ nhầm:** `@Scheduled` khác `@Async`. `@Async` được kích hoạt bởi caller qua proxy và có `AsyncUncaughtExceptionHandler` cho method `void`; scheduled task có cơ chế scheduling và ErrorHandler riêng. Không đặt `AsyncUncaughtExceptionHandler` rồi nghĩ nó tự xử lý mọi lỗi `@Scheduled`.

### Chọn scheduler trong @Scheduled

```java
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulerBeans {
    @Bean("criticalScheduler")
    public ThreadPoolTaskScheduler criticalScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("critical-");
        scheduler.setErrorHandler(
                TaskUtils.LOG_AND_PROPAGATE_ERROR_HANDLER);
        return scheduler;
    }
}
```

Đây là snippet **cấu hình Spring**, cần thêm imports `@Bean`, `@Configuration`, `@EnableScheduling`, `ThreadPoolTaskScheduler`, `TaskUtils`. Không phải file độc lập nếu copy nguyên khối. Spring container quản lý khởi tạo/dừng bean.

```java
@Scheduled(
    fixedDelay = 10,
    timeUnit = TimeUnit.SECONDS,
    scheduler = "criticalScheduler")
public void criticalTask() {
    verifyExternalState();
}
```

Snippet này cần `verifyExternalState()` trong bean. Với handler propagate, một exception thoát khỏi callback có thể chấm dứt các lần chạy kế tiếp. Nếu nghiệp vụ đòi scheduler tiếp tục, chọn handler phù hợp; không cấu hình chỉ theo thói quen.

## 11. Reactive @Scheduled: ErrorHandler không nhìn thấy mọi onError

Spring Framework 6.1+ hỗ trợ một số kiểu reactive return (ví dụ `Publisher` với deferred subscription) trong `@Scheduled`. Với reactive scheduled method, framework lập lịch **việc subscribe** vào publisher. Lỗi phát qua kênh `onError` không phải exception được ném đồng bộ từ `Runnable.run()`.

Theo tài liệu Spring:

- Một lỗi `onError` của Publisher được logging và recovered để các lần subscribe sau vẫn có thể diễn ra.
- `ErrorHandler` của scheduler không được gọi cho lỗi `onError` theo đường reactive này, vì callback scheduler không ném exception cùng cách như Runnable truyền thống.
- Những giá trị `onNext` thường bị bỏ qua.
- Với fixed-delay, Spring có thể chờ subscription hoàn tất để giữ semantics delay.
- Không nên áp chính sách “ErrorHandler propagate nên dừng mọi @Scheduled reactive” lên lỗi `onError` mà chưa kiểm tra subscriber semantics.

Vì vậy bài so sánh **default ErrorHandler của ThreadPoolTaskScheduler với JDK direct** là đúng cho **Runnable scheduling thông thường**. Với reactive scheduling, Futures, async adapters hoặc thư viện ngoài, phải xác định lỗi được biểu diễn bằng **thrown exception hay completion signal** trước khi chọn nơi bắt.

## 12. Cron, timezone và trigger linh hoạt: lợi thế của Spring abstraction

`ScheduledExecutorService` JDK cung cấp delay, fixed-rate và fixed-delay. Nó **không** có built-in `CronTrigger` với sáu trường như Spring:

```java
CronTrigger trigger = new CronTrigger(
        "0 0 9 * * MON-FRI",
        ZoneId.of("Asia/Ho_Chi_Minh"));

ScheduledFuture<?> future = taskScheduler.schedule(
        () -> publishReport(),
        trigger);
```

Đây là snippet trong Spring method, cần imports `CronTrigger`, `ZoneId` và các biến.

- Cron được diễn giải thành mốc theo lịch **wall clock** và timezone.
- Lịch fixed-rate JDK dựa trên period tương đối tính theo các mốc đã lên lịch, không tự biết thứ Hai–Sáu hoặc DST.
- `CronTrigger` lenient/fixed execution có semantics riêng khi task chạy lâu hoặc bỏ lỡ tick. Một trigger mới không nhất thiết biểu diễn cùng chính sách catch-up như fixed-rate JDK.
- Khi đổi cron động, gọi `schedule(task, newTrigger)` tạo **registration mới**; không tự hủy registration cũ. Giữ `ScheduledFuture<?>`, cancel và có generation/idempotency nếu cần.
- Một cron đúng cú pháp không đảm bảo thực thi đúng từng mili-giây, không tự cung cấp distributed lock hoặc job persistence.

### Thời điểm đã ở quá khứ

Với JDK one-shot, delay âm hoặc 0 được xem như yêu cầu thực thi càng sớm càng tốt. Với Spring `schedule(Runnable, Instant)`, một `Instant` trong quá khứ cũng sẽ được kích hoạt càng sớm càng tốt theo hợp đồng API. Dù cách gọi khác nhau, không bên nào có hard real-time guarantee.

## 13. Cancellation, Future và trạng thái “đã hoàn tất”

Đừng coi `isDone()` đồng nghĩa `succeeded`.

| Kiểm tra | One-shot direct JDK | Periodic direct JDK | Periodic Spring mặc định |
| --- | --- | --- | --- |
| `future.isDone()` sau thành công | Thường `true` | `false` khi lịch còn hoạt động | `false` khi lịch còn hoạt động |
| `future.isDone()` sau một exception thoát | `true` | `true`, lịch bị ngừng | Có thể vẫn `false` do Spring suppress |
| `future.isCancelled()` sau cancel thành công | `true` | `true` | `true` |
| `future.get()` sau lỗi đã lưu | `ExecutionException` | `ExecutionException` | Không nhất thiết trả về lỗi của tick đã bị suppress |
| `future.get()` khi periodic vẫn active | Không áp dụng | Chờ tới cancel/abnormal end | Chờ tới cancel/abnormal end |

Các exception khác:

- `CancellationException`: Future đã bị hủy.
- `TimeoutException`: caller chờ `get(timeout, unit)` quá thời hạn; **không tự hủy task**.
- `InterruptedException`: caller đang chờ bị interrupt; cần khôi phục interrupt flag hoặc truyền lỗi phù hợp.
- `ExecutionException`: tính toán hoàn tất thất bại; nguyên nhân nằm ở `getCause()`.
- `RejectedExecutionException` hoặc Spring `TaskRejectedException`: nộp công việc bị từ chối ở **caller**, có thể vì scheduler đã shutdown; task chưa chắc được chạy.

### Hủy một công việc đang chạy

`cancel(false)` không yêu cầu interrupt callback đang chạy. `cancel(true)` có thể yêu cầu interrupt nhưng không bảo đảm JDBC/HTTP transaction hay side effect đã được rollback. Nếu dùng `@Async` hoặc chuyển việc nặng sang một executor khác, Future của scheduler có thể chỉ theo dõi bước **submit**, không theo dõi toàn bộ công việc đã offload.

### Một future với periodic task không phải “kết quả lần n”

Periodic registration chỉ có một `ScheduledFuture<?>` đại diện trạng thái của chuỗi. Nó không tự cung cấp một Future riêng cho mỗi tick hoặc một danh sách exception từ từng lần chạy. Nếu nghiệp vụ cần audit mọi lần, tạo `executionId`, status/metric/event trên mỗi callback.

## 14. Shutdown và vòng đời: Spring bean khác JDK tự tạo

### 14.1. JDK scheduler do caller quản lý

```java
ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(2);

try {
    // Đăng ký tasks và quản lý futures.
} finally {
    scheduler.shutdown();
    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
    }
}
```

Snippet cần import JDK. Việc `shutdown()` nhận hay không các delayed/periodic tasks còn lại phụ thuộc các chính sách của implementation. Với `ScheduledThreadPoolExecutor`, có các flag:

- `setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean)`.
- `setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean)`.
- `setRemoveOnCancelPolicy(boolean)`.

Không mặc định `shutdown()` và `shutdownNow()` có cùng nghĩa: shutdownNow cố ngắt các task đang chạy và trả các task đang đợi, nhưng không bảo đảm mọi I/O lập tức dừng.

### 14.2. Spring-managed ThreadPoolTaskScheduler

```java
@Configuration(proxyBeanMethods = false)
public class SchedulerConfiguration {

    @Bean("appScheduler")
    public ThreadPoolTaskScheduler appScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("app-schedule-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
```

Đây là snippet cấu hình cần imports và Spring context. Với bean bình thường, Spring thực hiện initialization và destruction theo ApplicationContext lifecycle. Khi tự `new ThreadPoolTaskScheduler()` ngoài container, phải gọi `initialize()` rồi `shutdown()` như demo.

**Đừng dùng một ThreadPoolTaskScheduler mới cho mỗi request**: sẽ tạo nhiều executor riêng, khó dừng và dễ leak threads/delayed tasks. Đặt một scheduler bean dùng chung hoặc số scheduler có chủ đích để cô lập workload.

### 14.3. Mặc định pool size là một

`ThreadPoolTaskScheduler` mặc định một scheduler thread, tương tự việc dùng `Executors.newSingleThreadScheduledExecutor()` về năng lực chạy tuần tự. Tăng `setPoolSize(3)` cho phép các registrations độc lập có thể chạy đồng thời, nhưng các lần chạy liên tiếp của **cùng một periodic registration** dựa trên ScheduledThreadPoolExecutor không overlap.

Một tác vụ giữ DB connection lâu có thể làm các lịch khác trễ; cần timeout, giới hạn tải, pool size phù hợp và quan sát scheduling lag. Tăng số scheduler threads không tự tăng pool connection DB hoặc năng lực dịch vụ đích.

## 15. Sự khác biệt giữa continue-on-error, retry và restart lịch

Ba hành vi dễ bị gọi lẫn đều là “chạy lại”:

1. **Continue-on-error:** Spring suppress exception của tick n, nên khi tới tick n+1 theo fixed-rate/fixed-delay/cron, callback được gọi lại theo lịch.
2. **Retry cùng một lần chạy:** vì tick n thất bại, ứng dụng có thể retry chính giao dịch n với backoff, retry budget và idempotency. Đây là quyết định của tầng nghiệp vụ.
3. **Re-register sau khi bị dừng:** JDK periodic registration kết thúc vì lỗi; để tiếp tục, phải **tạo một scheduled registration mới** (hoặc job orchestration khác). Chỉ gọi `Future.get()` không hồi sinh registration cũ.

Ví dụ:

```text
Tick thứ 1 (09:00): lỗi HTTP 503
       |
       +--> continue: đợi đúng 09:05 rồi chạy tick #2
       |
       +--> retry: thử lại HTTP cho tick #1 sau backoff,
       |           không nhất thiết đợi 09:05
       |
       +--> stop: đánh dấu job FAILED/STOPPED,
                   cần re-register sau khi khắc phục
```

**Không nên retry vô hạn trong chính scheduler callback** nếu nó giữ worker, connection hoặc transaction quá lâu. Có thể tạo head-of-line blocking, backlog và làm trễ các job khác.

Nếu job làm việc có side effect (chuyển trạng thái đơn hàng, gửi thông báo, gọi API thanh toán), mọi chiến lược phải có idempotency key và chính sách khôi phục. Việc Spring tiếp tục lên lịch không bảo đảm “mỗi business event chỉ xử lý một lần” và việc JDK dừng khi lỗi cũng không đảm bảo rollback side effect của tick trước.

## 16. Quan sát lỗi: log thôi chưa đủ, Future thôi cũng chưa đủ

Với JDK direct, `ScheduledFuture.isDone()` của periodic task có thể trở thành `true` sau exception. Nếu ứng dụng không kiểm tra, job có thể “chết lặng” mà không có alert. Với Spring default, job có thể còn active dù tick liên tục thất bại; nếu ứng dụng chỉ kiểm tra `isDone()==false`, có thể tưởng mọi thứ tốt.

Cần hệ thống observability theo **cả registration và mỗi execution**:

| Tín hiệu | JDK direct stop-on-error | Spring continue-on-error |
| --- | --- | --- |
| Registration active/done/cancelled | Phát hiện job đã dừng bất thường | Phát hiện lịch vẫn tồn tại |
| Lần chạy cuối thành công | Bắt buộc để tránh lỗi không được log | Bắt buộc để biết lịch tiếp tục nhưng công việc thất bại |
| Số lỗi liên tiếp | Cảnh báo và quyết định re-register | Cảnh báo/circuit-breaker dừng spam lỗi |
| Execution duration | Nhận diện job bị treo | Nhận diện worker bị chiếm |
| Scheduled-vs-actual start lag | Nhận diện delay/overload | Nhận diện backlog, pool saturation |
| Rejection count | Phát hiện nộp lịch không thành công | Cũng cần xử lý từ caller |
| In-flight executions | Giám sát concurrency | Phát hiện duplicate triggers |
| Lỗi downstream | Phân biệt lỗi scheduler và lỗi service | Phân biệt log ErrorHandler và lỗi nghiệp vụ |
| Job ID + execution ID | Audit/diagnostic | Audit/diagnostic |

Một wrapper nghiệp vụ đơn giản:

```java
Runnable observed = () -> {
    long start = System.nanoTime();
    try {
        businessJob.run();
        successCounter.increment();
    } catch (RuntimeException | Error ex) {
        failureCounter.increment();
        throw ex; // Chọn policy có chủ đích.
    } finally {
        durationRecorder.record(System.nanoTime() - start);
    }
};
```

Đây là snippet với `businessJob`, `successCounter`, `failureCounter`, `durationRecorder` do ứng dụng cung cấp. Trong Spring default, `throw ex` sẽ tới ErrorHandler và có thể được suppress, nhưng metric nghiệp vụ vẫn ghi lại lỗi. Trong JDK direct, cùng cách rethrow làm periodic registration dừng.

**Đặt log/metric ở một owner rõ ràng:** nếu wrapper, Spring ErrorHandler và client layer cùng ghi một lỗi, có thể tạo ba log và ba counter cho một execution. Gắn execution ID và tracing context để tránh đếm trùng.

### Báo động theo thời gian thành công cuối

Một job mà cứ 5 phút phải cập nhật cache nên có alert khi `now - lastSuccess > threshold`, không chỉ alert theo trạng thái Future. Với cron theo timezone, phải dùng deadline/lịch hợp lệ thay vì giả định một khoảng đều giữa mọi tick. Khi ứng dụng restart hoặc nhiều pod, lưu health ở nơi có thể quan sát toàn cluster.

## 17. Lỗi thường gặp khi chuyển code JDK sang Spring hoặc ngược lại

**1. “Cả hai dùng ScheduledThreadPoolExecutor nên exception behavior giống hệt.”** Không đúng vì Spring có lớp bọc ErrorHandler. So sánh tại callback boundary, không chỉ underlying executor.

**2. “Spring log lỗi xong thì tự retry ngay tick thất bại.”** Không mặc định. Nó có thể tiếp tục tick **kế tiếp** theo lịch, không phải retry với cùng execution ID.

**3. “Một exception làm chết cả thread pool JDK.”** Trong scheduleAtFixedRate, một **registration** bị ngừng; các registrations khác vẫn có thể tiếp tục.

**4. “Future.isDone=false nên không có lỗi.”** Sai với Spring repeating handler suppress. Lỗi có thể đã log ở các tick trước.

**5. “Future.get() trên periodic registration sẽ trả kết quả mỗi 10 giây.”** Sai. Future đại diện chuỗi định kỳ; `get()` không trả bình thường sau mỗi tick.

**6. “Bắt lỗi bằng UncaughtExceptionHandler là đủ.”** ScheduledFutureTask có thể ghi exception trong Future, nên handler uncaught có thể không được gọi.

**7. “afterExecute(Throwable) luôn khác null khi task lỗi.”** Không đúng với FutureTask wrappers; phải biết loại callback và quan sát Future/status phù hợp.

**8. “Đặt custom ErrorHandler chỉ log là vô hại.”** Với one-shot, điều này có thể khiến Future báo hoàn tất bình thường dù nghiệp vụ lỗi; với periodic có thể giữ lịch tiếp tục.

**9. “Một lần tick lỗi rồi Spring tự ngừng để bảo vệ hệ thống.”** Spring repeating default thường tiếp tục; cần policy riêng nếu lỗi nghiêm trọng phải dừng.

**10. “Đổi từ JDK scheduleAtFixedRate sang CronTrigger là giữ nguyên semantics.”** Cron dùng wall-clock/zone và có lenient/fixed execution; fixed-rate dùng period. Các mốc trễ/missed runs có thể khác.

**11. “@Scheduled và @Async dùng cùng error handler.”** Hai cơ chế khác nhau; `AsyncUncaughtExceptionHandler` không mặc định xử lý lỗi của scheduled callback.

**12. “@Scheduled reactive onError đi qua scheduler ErrorHandler.”** Spring tài liệu nói onError của Publisher được log/recovered tại reactive scheduling layer, không qua ErrorHandler thường.

**13. “Pool size 1 sẽ ngăn mọi duplicate business execution.”** Nó chỉ serialize callback trong pool đó. Nếu cùng job được đăng ký hai lần, hoặc offload sang executor khác, hoặc có nhiều pod, duplicate vẫn có thể xảy ra.

**14. “Shutdown scheduler là mọi remote I/O dừng ngay.”** Cancel/interrupt không bảo đảm remote side effect đã rollback. Cần client timeout và cleanup đúng.

**15. “TaskRejectedException là lỗi của một lần chạy đã bắt đầu.”** Thường rejection xuất hiện ở caller khi nộp task, trước khi worker chạy.

**16. “Chạy trên virtual threads thì mọi scheduler Future phản ánh completion như JDK.”** `SimpleAsyncTaskScheduler` có hand-off thread-per-task; Future có thể đại diện hand-off, và fixed-delay chạy trên single scheduler thread. Đọc implementation docs.

## 18. Checklist vận hành và lựa chọn policy

Trước khi đưa job định kỳ vào production:

1. **Xác định API đang dùng:** JDK `ScheduledExecutorService`, Spring `ThreadPoolTaskScheduler`, `SimpleAsyncTaskScheduler`, Quartz, hay `@Scheduled` reactive?
2. **Kiểm tra ErrorHandler thật:** mặc định repeating/one-shot hay handler custom? Có catch trong business task không?
3. **Chọn rõ stop hay continue:** lỗi tạm thời được bỏ qua để tick sau chạy hay phải dừng job?
4. **Phân biệt continue với retry:** mỗi execution có ID, budget, backoff và idempotency đúng.
5. **Ghi nhận mỗi lần chạy:** start, success, failure, duration và lỗi gốc.
6. **Có alert lastSuccess:** tránh cả hai kiểu job dừng ngầm và job lỗi liên tục nhưng Future vẫn active.
7. **Không gọi get() vô hạn trên periodic Future:** dùng timeout/status store hoặc observer khác.
8. **Xử lý rejection ở nơi nộp:** scheduler shutdown, queue/resource pressure, policy fail-fast.
9. **Đặt timeout của dependency:** không giữ scheduler thread/DB connection vô hạn.
10. **Thiết kế pool size theo workload:** fixed-rate/fixed-delay không tự bảo đảm tất cả jobs luôn đúng giờ.
11. **Có chính sách hủy và shutdown:** handle lifecycle, interrupt semantics, remove-on-cancel.
12. **Đúng timezone và mốc lịch:** `CronTrigger` của Spring khác fixed-rate period; DST có thể đổi mốc.
13. **Không đăng ký trùng:** một owner cho logical job; tránh `@Scheduled` và manual cùng gọi một business method nếu không chủ đích.
14. **Nhiều pods:** mỗi pod có thể đăng ký một lịch; dùng distributed claim/lease/idempotency khi chỉ muốn một lần xử lý toàn cluster.
15. **Kiểm thử đường lỗi:** JDK periodic dừng; Spring periodic mặc định tiếp tục; one-shot Future exceptional; custom handler có thể đổi hành vi.
16. **Không chỉ kiểm tra stdout:** test log/error metric/Future state và business side effects.
17. **Giữ quyền sở hữu resource:** Spring-managed bean để container đóng, JDK self-managed thì `shutdown()/awaitTermination()`.
18. **Nâng version phải chạy lại test:** error/cancellation/trigger behavior của custom adapters có thể khác.

### Khi nào nên chọn loại scheduler?

| Nhu cầu | Gợi ý lựa chọn | Điểm cần kiểm tra |
| --- | --- | --- |
| Ứng dụng Java không dùng Spring, chỉ delay/fixed-period | `ScheduledExecutorService` | Error làm ngừng periodic registration, lifecycle tự quản |
| Spring app cần đơn giản hóa configuration/bean lifecycle | `ThreadPoolTaskScheduler` | Default ErrorHandler khác JDK direct cho repeating jobs |
| Cron theo thứ/giờ/múi giờ và dynamic trigger | Spring `TaskScheduler + CronTrigger` | Timezone, missed ticks, cancellation và error policy |
| Rất nhiều blocking tasks trên Java 21+ | `SimpleAsyncTaskScheduler`/virtual threads đúng tình huống | Fixed-delay single scheduler thread, Future hand-off semantics |
| Job cần lưu qua restart hoặc cluster | Quartz job store hoặc durable queue/worker | Retry, misfire, idempotency, distributed coordination |
| Job phải dừng hẳn sau lỗi đầu | JDK periodic mặc định hoặc Spring propagate handler | Phải có alert/re-registration có kiểm soát |
| Job phải tiếp tục dù một tick lỗi | Spring default repeating hoặc JDK wrapper catch | Không che giấu lỗi; cần success/failure metrics |

## 19. Tài liệu chính thức và kết luận

Đọc cùng phiên bản đang deploy, nhất là khi dùng custom `ErrorHandler` hoặc một adapter không phải `ThreadPoolTaskScheduler`:

1. [Java SE 17 – ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): one-shot, fixed-rate, fixed-delay và trạng thái exceptional của periodic tasks.
2. [Java SE 17 – ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html): không overlap các lần chạy cùng registration, future và shutdown/remove-on-cancel policies.
3. [Java SE 17 – ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): delay, `cancel()`, `isDone()`, `get()`.
4. [Spring Framework 6.2 – TaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/TaskScheduler.html): `Instant`, `Duration` và `Trigger`.
5. [Spring Framework 6.2 – ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): adapter JDK, `setErrorHandler`, lifecycle, pool size và completion semantics.
6. [Spring Framework 6.2 – DelegatingErrorHandlingRunnable](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/support/DelegatingErrorHandlingRunnable.html): wrapper bắt lỗi của Runnable và gọi ErrorHandler.
7. [Spring Framework – TaskUtils source](https://github.com/spring-projects/spring-framework/blob/6.2.x/spring-context/src/main/java/org/springframework/scheduling/support/TaskUtils.java): `LOG_AND_SUPPRESS_ERROR_HANDLER` và `LOG_AND_PROPAGATE_ERROR_HANDLER`, chính sách mặc định cho repeating và one-shot.
8. [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Scheduled`, `@EnableScheduling`, `CronTrigger` và ngoại lệ của reactive scheduled methods.
9. [Spring Framework 6.2 – SimpleAsyncTaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/SimpleAsyncTaskScheduler.html): mô hình hand-off và giới hạn fixed-delay.
10. [Spring Framework 6.2 – CronTrigger](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/support/CronTrigger.html): cron, timezone, lenient/fixed execution.

### Ba câu cần nhớ

1. **JDK `ScheduledExecutorService` direct:** exception thoát khỏi periodic callback làm **ngừng các lần chạy tiếp theo** và đưa lỗi vào `ScheduledFuture`.
2. **Spring `ThreadPoolTaskScheduler` mặc định:** exception của **repeating task** được log và **suppress**, nên job vẫn có thể tiếp tục ở tick sau; **one-shot** mặc định log và propagate.
3. **Giữ đúng semantics nghiệp vụ:** hãy chọn chủ đích “dừng, tiếp tục hay retry”, quan sát mỗi execution, xử lý Future/error/lifecycle riêng và test trên đúng scheduler implementation/version.

**Kết luận:** hai scheduler có thể cùng dùng `ScheduledThreadPoolExecutor` của JDK nhưng **không có chính sách lỗi giống nhau**. Khi một lịch Spring vẫn tiếp tục sau exception còn một lịch JDK trực tiếp ngừng hoạt động, đó có thể là hành vi đúng theo thiết kế của từng API, không nhất thiết là bug. Cần quyết định và kiểm chứng error policy thay vì suy ra từ tên executor.


</details>

- [Quay lại đầu trang](#back-to-top)
