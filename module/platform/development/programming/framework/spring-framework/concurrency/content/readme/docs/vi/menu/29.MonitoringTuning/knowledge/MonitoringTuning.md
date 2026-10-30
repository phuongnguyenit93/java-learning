<a id="back-to-top"></a>

# Quan sát và điều chỉnh

## Menu
- [Đặt tên luồng để lần theo đường đi tác vụ](#monitoring-tuning-001)
- [Ghi nhận thời điểm gửi, bắt đầu, hoàn tất và mã công việc](#monitoring-tuning-002)
- [Đo thời gian chờ hàng đợi khác thời gian thực thi](#monitoring-tuning-003)
- [Quan sát số luồng, activeCount, kích thước hàng đợi và tác vụ bị từ chối](#monitoring-tuning-004)
- [Theo dõi số lần chạy lịch thành công, lỗi và lần chạy gần nhất](#monitoring-tuning-005)
- [Không đưa dữ liệu riêng của yêu cầu vào nhãn số liệu có quá nhiều giá trị](#monitoring-tuning-006)
- [Dùng thread dump và công cụ JVM để tìm chờ khóa hoặc cạn nhóm luồng](#monitoring-tuning-007)
- [Đối chiếu thay đổi cấu hình với độ trễ, công suất và tài nguyên đích](#monitoring-tuning-008)
- [Giám sát không thay thế cơ chế lưu kết quả nghiệp vụ](#monitoring-tuning-009)

## <a id="monitoring-tuning-001">Đặt tên luồng để lần theo đường đi tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt tên luồng để lần theo đường đi tác vụ trong Spring Framework

Khi ứng dụng Spring xử lý đồng thời nhiều request, tác vụ `@Async` và job `@Scheduled`, các dòng log có thể xen kẽ. **Tên luồng (thread name)** giúp nhận biết *nhóm thực thi nào* đang chạy một đoạn mã và nhìn thấy điểm chuyển từ luồng gọi sang luồng nền. Đây là một công cụ quan sát và chẩn đoán; tên luồng **không phải định danh duy nhất của một tác vụ**.

Sau bài này, bạn có thể đặt tên rõ nghĩa cho luồng do executor/scheduler quản lý, hiển thị tên đó trong log, theo dõi một tác vụ khi chuyển luồng, và phân biệt tên luồng với `traceId`/`requestId`.

## 1. Hiểu đúng điều tên luồng cho biết

Giả sử một HTTP request khởi phát thao tác ghi nhật ký kiểm toán bất đồng bộ:

```text
14:08:12.100 [http-nio-8080-exec-3] Nhận sự kiện EV-42
14:08:12.107 [audit-async-1]       Bắt đầu xử lý EV-42
14:08:12.113 [audit-async-1]       Hoàn tất EV-42
```

Đây là **log minh họa**; tên luồng HTTP thực tế phụ thuộc web server và cấu hình. Ta suy ra được:

- `http-nio-8080-exec-3` đang thực hiện phần xử lý request ở luồng của server.
- `audit-async-1` là một worker thuộc nhóm executor dành cho audit; đã có **chuyển ngữ cảnh thực thi** từ luồng request sang executor.
- Hậu tố `1` phân biệt một luồng được executor tạo ra; nó không phải số thứ tự request hay mã tác vụ.
- `EV-42` là thông tin nghiệp vụ giúp gắn các dòng log với cùng một sự kiện. Một luồng `audit-async-1` có thể xử lý lần lượt `EV-42`, `EV-43` và nhiều tác vụ khác.

Một tên luồng trả lời câu hỏi **“mã này đang chạy trên luồng nào?”**. Nó không tự trả lời đầy đủ câu hỏi **“mọi bước của request này, qua nhiều luồng hoặc nhiều service, là gì?”**.

## 2. Quy tắc đặt tên dễ đọc và dễ truy vết

Nên dùng tiền tố ổn định phản ánh **chức năng hoặc nhóm tác vụ**, sau đó để thread factory/executor tạo hậu tố phân biệt luồng:

| Nơi thực thi | Tiền tố gợi ý | Ý nghĩa |
| --- | --- | --- |
| Executor xử lý email bất đồng bộ | `mail-async-` | Worker của nghiệp vụ gửi email |
| Executor ghi audit | `audit-async-` | Worker của hàng đợi audit |
| Scheduler tạo báo cáo | `report-scheduler-` | Luồng thực thi job định kỳ |
| Executor đồng bộ dữ liệu | `sync-worker-` | Worker của tác vụ đồng bộ |

Một cách đặt tên hữu ích có ba tính chất:

1. **Phân biệt được các executor**: tránh mọi pool đều mang tên chung chung như `task-` hoặc `pool-`.
2. **Ổn định theo loại công việc**: tên `audit-async-2` vẫn hữu ích khi worker đó xử lý nhiều sự kiện khác nhau.
3. **Không chứa dữ liệu của từng request**: không đưa email, token, dữ liệu nhạy cảm hoặc `requestId` vào tên luồng dài hạn; dùng log context cho dữ liệu theo tác vụ.

Trong một JVM, có thể nhận ra worker nhờ tên. Khi gom log từ nhiều instance/pod, hai JVM khác nhau vẫn có thể cùng một tên `audit-async-1`; cần thêm trường `service`, `instance` hoặc `pod` trong log để phân biệt nguồn.

## 3. Đặt tên cho `@Async` bằng `ThreadPoolTaskExecutor`

Spring cung cấp `ThreadPoolTaskExecutor` và phương thức `setThreadNamePrefix(...)`. Khi dùng `@Async`, hãy chỉ rõ executor để tác vụ chắc chắn đi vào nhóm luồng đã đặt tên.

**Tệp `AsyncNamingConfig.java`**, trong một ứng dụng Spring có component scan:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncNamingConfig {

    @Bean("auditExecutor")
    public ThreadPoolTaskExecutor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("audit-async-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        return executor;
    }
}
```

**Tệp `AuditService.java`**:

```java
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    @Async("auditExecutor")
    public CompletableFuture<Void> recordEvent(String eventId) {
        log.info("Bắt đầu audit, eventId={}", eventId);
        log.info("Hoàn tất audit, eventId={}", eventId);
        return CompletableFuture.completedFuture(null);
    }
}
```

**Tệp `AuditFacade.java`**, đại diện cho bean nhận yêu cầu rồi gọi service khác:

```java
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditFacade {

    private static final Logger log = LoggerFactory.getLogger(AuditFacade.class);
    private final AuditService auditService;

    public AuditFacade(AuditService auditService) {
        this.auditService = auditService;
    }

    public CompletableFuture<Void> accept(String eventId) {
        log.info("Nhận sự kiện, eventId={}", eventId);
        return auditService.recordEvent(eventId);
    }
}
```

Khi một controller gọi `auditFacade.accept("EV-42")`, log trong `accept` chạy trên luồng gọi (chẳng hạn luồng HTTP), còn hai log trong `recordEvent` chạy trên worker có tiền tố `audit-async-`. Đối với ví dụ này, các giá trị pool `2`, `4`, `100` chỉ minh họa cấu hình executor, **không phải khuyến nghị kích thước pool cho mọi hệ thống**.

Các chi tiết cần hiểu:

- `@EnableAsync` bật cơ chế xử lý `@Async`. `@Async("auditExecutor")` tham chiếu đến tên bean đã khai báo.
- Khi `ThreadPoolTaskExecutor` là bean Spring, vòng đời của Spring sẽ khởi tạo và quản lý executor; không cần gọi `initialize()` thủ công trong phương thức `@Bean` ở trên.
- `@Async` mặc định dùng proxy: lệnh gọi từ **một bean khác** được intercept, còn `this.recordEvent(...)` trong chính `AuditService` sẽ đi thẳng vào phương thức, không chuyển sang worker như mong đợi.
- Muốn thấy tên `audit-async-*` trong log, phải thực sự chạy qua executor này. Đặt prefix trên một bean nhưng lại thực thi bằng executor khác không tạo ra tác dụng.
- `CompletableFuture` cho phép caller theo dõi kết quả/lỗi. Việc các dòng log từ các request khác nhau xen kẽ là bình thường.

Đoạn Spring trên là mã thuộc **ứng dụng có thư viện Spring và SLF4J**, không phải ví dụ `javac` độc lập. Với nhiều bean executor, luôn dùng qualifier rõ ràng tại nơi gửi việc.

## 4. Cơ chế Java nền tảng: `ThreadFactory` đặt tên khi tạo luồng

Spring dựa trên các cơ chế concurrency của Java. Để hiểu gốc của thread naming, dưới đây là chương trình Java 17+ **độc lập, chạy được**:

**Tệp `NamedThreadsDemo.java`**:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadsDemo {

    public static void main(String[] args) throws Exception {
        AtomicInteger sequence = new AtomicInteger(1);

        ThreadFactory factory = task ->
                new Thread(task, "billing-worker-" + sequence.getAndIncrement());

        ExecutorService executor = Executors.newFixedThreadPool(2, factory);
        try {
            Future<String> result =
                    executor.submit(() -> Thread.currentThread().getName());
            System.out.println(result.get());
        } finally {
            executor.shutdown();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac NamedThreadsDemo.java
java NamedThreadsDemo
```

**Stdout chính xác của chương trình trên** (chỉ có một tác vụ được nộp):

```text
billing-worker-1
```

`ThreadFactory.newThread(Runnable)` nhận công việc và tạo `Thread` mang tên có prefix. `AtomicInteger` cấp số duy nhất cho từng lần tạo luồng *trong factory đó*. `Future.get()` đợi tác vụ trả về tên luồng, nên chương trình in kết quả trước khi kết thúc. `shutdown()` giải phóng executor sau sử dụng.

**Điểm mấu chốt:** factory đặt tên lúc **tạo thread**, không đặt tên riêng cho mỗi `Runnable`. Một worker có thể phục vụ nhiều `Runnable`, và một tác vụ phức tạp có thể tiếp tục trên một worker khác.

## 5. Đặt tên cho tác vụ `@Scheduled`

Một job định kỳ có nguồn thực thi khác với `@Async`: Spring dùng `TaskScheduler` để kích hoạt tác vụ theo lịch. Khi ứng dụng có `@Scheduled`, có thể đặt tên riêng cho scheduler:

**Tệp `SchedulingNamingConfig.java`**:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingNamingConfig {

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("report-scheduler-");
        return scheduler;
    }
}
```

**Tệp `ReportJob.java`**:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReportJob {

    private static final Logger log = LoggerFactory.getLogger(ReportJob.class);

    @Scheduled(fixedDelay = 60_000)
    public void refreshReport() {
        log.info("Đang làm mới báo cáo");
    }
}
```

Với một bean scheduler được đặt tên `taskScheduler` như trên, Spring có thể chọn scheduler này cho các phương thức `@Scheduled`. Log của `refreshReport` sẽ mang tên dạng `report-scheduler-1`. `fixedDelay` là khoảng chờ tính từ lúc **lần chạy trước hoàn thành** đến lần chạy tiếp theo; thread naming không thay đổi lịch kích hoạt.

Nếu có nhiều scheduler, cần cấu hình chỉ định rõ scheduler nào chạy từng nhóm job; không dựa vào phỏng đoán từ tên bean. Không nhầm `ThreadPoolTaskScheduler` với `ThreadPoolTaskExecutor`: một cái lập lịch, cái còn lại thực thi công việc được submit.

## 6. Hiển thị tên luồng trong log

Tên luồng chỉ giúp chẩn đoán nếu log có ghi lại nó. Với **Spring Boot dùng Logback**, ví dụ cấu hình trong `application.properties`:

```properties
logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] %-5level [traceId=%X{traceId}] %logger{36} - %msg%n
```

Trong mẫu này:

- `%thread`: tên luồng hiện tại.
- `%X{traceId}`: giá trị khóa `traceId` trong MDC (Mapped Diagnostic Context), có thể để trống nếu chưa được đặt.
- `%logger{36}` và `%msg`: nguồn log và thông điệp.

Với ứng dụng Spring Framework không dùng Spring Boot, thiết lập pattern tương ứng trong cấu hình Logback/Log4j đang dùng; thuộc tính `logging.pattern.console` ở trên là tiện ích cấu hình của **Spring Boot**.

Ví dụ khi chưa có MDC:

```text
14:08:12.100 [http-nio-8080-exec-3] INFO  [traceId=] ... - Nhận sự kiện, eventId=EV-42
14:08:12.107 [audit-async-1]       INFO  [traceId=] ... - Bắt đầu audit, eventId=EV-42
```

Cách kiểm tra nhanh tại đúng vị trí cần quan sát là `Thread.currentThread().getName()`, nhưng logging framework nên chịu trách nhiệm in tên luồng chung thay vì lặp `getName()` ở mọi câu lệnh log.

## 7. Theo dõi **cùng tác vụ** khi qua nhiều luồng: dùng thêm correlation ID

Các request có thể chia sẻ một pool. Ví dụ `audit-async-1` xử lý `EV-42` rồi `EV-43`; nếu chỉ lọc theo tên luồng, hai chuỗi công việc sẽ trộn với nhau. Hãy kết hợp:

- **Thread name**: cho biết *bước đó đang chạy ở đâu*.
- **`traceId`/`requestId`**: cho biết *bước đó thuộc luồng nghiệp vụ nào*.
- **`eventId`/`jobId`** (nếu phù hợp): cho biết *đối tượng nghiệp vụ nào đang được xử lý*.

SLF4J `MDC` thường dựa trên context của thread, nên dữ liệu MDC trên luồng gọi **không tự động xuất hiện** trong worker của `@Async`. Với `ThreadPoolTaskExecutor`, có thể chụp MDC lúc submit và gắn nó vào lúc thực thi bằng `TaskDecorator`.

**Đoạn bổ sung bên trong `auditExecutor()` ở mục 3, trước `return executor;`** (thêm `import java.util.Map;` và `import org.slf4j.MDC;`):

```java
executor.setTaskDecorator(task -> {
    Map<String, String> submitted = MDC.getCopyOfContextMap();

    return () -> {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            if (submitted == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(submitted);
            }
            task.run();
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    };
});
```

Khi caller đã đặt `MDC.put("traceId", "T-203")` trong phạm vi xử lý của mình, decorator trên sẽ chụp bản sao tại lúc submit, gắn nó cho worker trong lúc chạy, rồi **khôi phục context cũ** ở `finally`. Caller cũng phải xóa/khôi phục MDC của chính mình khi kết thúc scope. Đây là phần quan trọng để các tác vụ tiếp theo dùng lại worker không nhận nhầm context trước đó.

Lúc đó, log có thể giống:

```text
14:08:12.100 [http-nio-8080-exec-3] INFO [traceId=T-203] ... - Nhận sự kiện, eventId=EV-42
14:08:12.107 [audit-async-1]       INFO [traceId=T-203] ... - Bắt đầu audit, eventId=EV-42
14:08:12.113 [audit-async-1]       INFO [traceId=T-203] ... - Hoàn tất audit, eventId=EV-42
```

**Giới hạn của cách này:** decorator chỉ áp dụng với các tác vụ thực thi qua executor được cấu hình. Một `CompletableFuture.thenApplyAsync(...)`/`supplyAsync(...)` không truyền executor có thể dùng `ForkJoinPool.commonPool()` thay vì `auditExecutor`; việc đó làm thay đổi cả thread name lẫn khả năng mang MDC. Khi chuyển sang executor khác, phải cấu hình truyền context ở ranh giới tương ứng. Trong pipeline Reactor, context có cơ chế riêng; không nên giả định MDC kiểu `ThreadLocal` luôn đi theo pipeline.

Nếu hệ thống đã có tracing bằng Micrometer Tracing/OpenTelemetry, ưu tiên dùng trace/span ID và cơ chế context propagation của hệ thống đó. Tên luồng là tín hiệu hỗ trợ quan sát, không thay thế distributed tracing.

## 8. Lần theo một sự cố thực tế

Tình huống: một request gọi `AuditFacade.accept("EV-42")` nhưng người trực vận hành không thấy log xử lý audit đúng như mong đợi.

1. **Lọc theo `traceId` hoặc `eventId`**, sắp xếp theo thời gian để thấy các bước của cùng tác vụ.
2. **Đọc tên luồng ở từng bước**: có `http-nio-...` rồi `audit-async-...` hay không? Nếu log trong `@Async` vẫn mang tên luồng HTTP, kiểm tra `@EnableAsync`, lời gọi qua proxy và bean executor.
3. **Kiểm tra executor được chọn**: `@Async("auditExecutor")` có trùng tên bean? Có nơi nào dùng executor mặc định hoặc `CompletableFuture` không truyền executor?
4. **Nếu có chuyển luồng nhưng mất `traceId`**, kiểm tra decorator/context propagation. Đừng “khắc phục” bằng cách đưa `traceId` vào tên của worker tái sử dụng.
5. **Nếu tác vụ không bắt đầu**, kiểm tra trạng thái pool/queue, khả năng bị từ chối submit, hoặc lỗi trước khi log; chỉ nhìn tên luồng không chứng minh tác vụ đã được nhận.

Khi điều tra thread dump trong JVM đang chạy, có thể dùng lệnh JDK (nếu được phép và có quyền truy cập tiến trình):

```bash
jcmd <PID> Thread.print
```

Tên như `audit-async-2` giúp tìm nhóm worker liên quan. Tuy nhiên, một thread dump chỉ là **ảnh chụp tại một thời điểm**; worker có thể đang đợi việc, và tên thread không cung cấp lịch sử đầy đủ các task đã chạy.

## 9. Các lỗi thường gặp

| Lỗi | Vì sao gây khó chẩn đoán | Cách làm đúng |
| --- | --- | --- |
| Mọi pool có prefix `task-` | Không biết log thuộc executor nào | Đặt `audit-async-`, `mail-async-`, `report-scheduler-` theo trách nhiệm |
| Gán mã request bằng `Thread.currentThread().setName(...)` rồi để nguyên | Pool tái sử dụng thread; tên tác vụ cũ gây hiểu lầm cho tác vụ sau | Dùng tên ổn định của pool + `traceId`/`requestId` trong log |
| Gọi `@Async` bằng `this.method()` | Bỏ qua proxy mặc định; phương thức có thể chạy đồng bộ trên caller | Gọi qua một bean Spring khác hoặc dùng cơ chế phù hợp |
| Cấu hình prefix cho một executor nhưng submit sang executor khác | Log hiện tên luồng không mong đợi | Truyền/chỉ định đúng executor ở mọi điểm submit |
| Chỉ nhìn số hậu tố worker | Số không phải thứ tự các request hay task | Gắn correlation ID cho mỗi luồng nghiệp vụ |
| Chép MDC vào worker nhưng không dọn sạch | Context có thể rò sang tác vụ tiếp theo | Chụp và khôi phục context trong `try/finally` |
| Cho rằng mọi callback tương lai giữ nguyên thread | Các bước async có thể đổi executor/thread | Ghi log ở ranh giới, mang trace context qua từng lần chuyển |

**Lưu ý với virtual thread (Java 21+)**: nếu chọn executor dùng virtual threads, tuổi thọ và cách gán thread cho task có thể khác với pool platform thread cố định. Vẫn nên đặt prefix rõ nghĩa nếu API executor hỗ trợ, nhưng càng không dùng tên thread để đại diện cho toàn bộ trace.

## 10. Tự kiểm tra

Hãy thử cấu hình hai executor, `audit-async-` và `mail-async-`, gọi mỗi executor từ một bean khác rồi quan sát:

- Log có hiển thị `%thread` và cho thấy đúng tiền tố của từng nhóm không?
- Khi nộp hai tác vụ liên tiếp vào một pool, bạn có thấy thread name có thể được dùng lại không?
- Khi thêm `traceId` và `TaskDecorator`, log caller và worker có cùng `traceId` không?
- Sau khi tác vụ kết thúc, trace ID của request trước có bị xuất hiện ở request sau không?
- Khi thay `@Async("auditExecutor")` bằng một đường gọi nội bộ `this.recordEvent(...)`, tên luồng thay đổi như thế nào và vì sao?

**Tiêu chí hoàn thành:** bạn có thể nhìn một dòng log và xác định executor/thread đang chạy, dùng correlation ID để ghép toàn bộ bước của một tác vụ, và giải thích vì sao **đổi luồng không đồng nghĩa đổi tác vụ**, còn **cùng luồng không đồng nghĩa cùng tác vụ**.

## Tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — ThreadPoolTaskScheduler API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Java SE — ThreadFactory](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadFactory.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monitoring-tuning-002">Ghi nhận thời điểm gửi, bắt đầu, hoàn tất và mã công việc</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi nhận thời điểm gửi, bắt đầu, hoàn tất và mã công việc

## 1. Vì sao cần bốn thông tin này?

Khi Spring Framework xử lý công việc bất đồng bộ bằng `TaskExecutor`, lời gọi `execute()` hoặc `submit()` có thể chỉ đưa công việc vào hàng đợi. Một task có thể phải đợi, chạy xong, ném lỗi hoặc bị từ chối. Muốn giải thích độ trễ và ghép các dòng log từ nhiều thread, ta cần một **mã công việc** đi cùng **ba mốc thời gian**.

| Trường | Ý nghĩa chính xác | Thời điểm ghi |
| --- | --- | --- |
| `taskId` / `jobId` | Mã định danh **một lần thực thi task** | Tạo trước khi gửi cho executor |
| `submittedAt` | Bên gọi bắt đầu gửi task; **chưa có nghĩa là task đã chạy** | Ngay trước `executor.execute(...)` / `submit(...)` |
| `startedAt` | Callback thực sự bắt đầu chạy (trên worker hoặc caller tùy chính sách executor) | Dòng đầu của `Runnable` / `Callable` được bọc |
| `completedAt` | Callback kết thúc, gồm cả kết thúc thành công và kết thúc vì ngoại lệ | Khi công việc đã dừng, trước khi báo kết quả cuối |

Ví dụ: gửi lúc `10:00:00.100`, bắt đầu `10:00:00.280`, xong `10:00:00.780`. Như vậy thời gian **chờ là 180 ms**, thời gian **thực thi là 500 ms**, thời gian **từ gửi đến kết thúc là 680 ms**. Chúng phản ánh các vấn đề khác nhau: backlog của pool, độ nặng của tác vụ và độ trễ tổng mà khách hàng quan sát.

Bài học tập trung vào quan sát từng công việc trong Spring concurrency, không xem việc có log là bảo đảm job được thực thi đúng một lần hoặc được lưu bền vững sau khi restart.

## 2. Tách sự kiện khỏi trạng thái

```text
Tạo taskId, ghi submittedAt
              |
         gọi execute()
              +---- từ chối ----> REJECTED, rejectedAt
              |
      task được chấp nhận
              |
      chờ queue hoặc chạy ngay
              |
         RUNNING, startedAt
              |
              +---- bình thường ---> SUCCEEDED, completedAt
              |
              +---- ném lỗi -------> FAILED, completedAt
```

- `SUBMITTED` nghĩa là đã **bắt đầu yêu cầu gửi**, không đồng nghĩa với `RUNNING`. Nếu `execute()` ném `TaskRejectedException` thì ghi `REJECTED`; **không** đặt `completedAt` như thể tác vụ đã chạy.
- `SUCCEEDED` và `FAILED` đều là trạng thái kết thúc xử lý. Vì vậy thời điểm “hoàn tất” không tự nói lên kết quả thành công.
- `CANCELLED`, `TIMED_OUT` và `UNKNOWN` cần quy tắc riêng. `Future.cancel(...)` trả về thành công không bảo đảm công việc nghiệp vụ đã dừng, nhất là khi task bỏ qua ngắt. Process bị kill có thể làm mất event hoàn tất.
- Không dùng tên thread làm `taskId`: một thread pool có thể chạy hàng nghìn task trên cùng thread. Nếu retry, nên giữ `logicalJobId` đại diện yêu cầu nghiệp vụ và cấp `attemptId`/`taskId` mới cho từng lượt thử.
- Trong `@Scheduled`, `scheduledFor` (giờ dự kiến), `submittedAt` (giờ gửi) và `startedAt` (giờ thực chạy) là ba khái niệm khác nhau.

Một bản ghi thực tế có thể chứa `taskId`, `logicalJobId`, `taskType`, `executorName`, `submittedAt`, `startedAt`, `completedAt`, `rejectedAt`, `status`, `queueWaitMs`, `runTimeMs`, `failureType`, `traceId`. Trạng thái phải được cập nhật an toàn giữa các thread; ưu tiên snapshot bất biến, `ConcurrentHashMap`, cập nhật có điều kiện hoặc giao dịch trong kho lưu trữ.

## 3. Đồng hồ nào dùng để ghi mốc và đo duration?

Dùng `Instant.now()` để ghi mốc lịch thực (phù hợp log ISO-8601/UTC). Dùng thêm `System.nanoTime()` ở ba điểm tương ứng khi đo khoảng thời gian **trong cùng JVM**:

```text
queueWaitNs = startedTick   - submittedTick
runTimeNs   = completedTick - startedTick
totalNs     = completedTick - submittedTick
```

Chỉ có `queueWait` khi đã thực sự bắt đầu, và chỉ có `runTime` khi đã có cả bắt đầu lẫn kết thúc. `System.nanoTime()` không phải Unix timestamp; không so hoặc trừ các tick thu từ hai JVM/process. Khoảng chênh giữa các `Instant` có thể bị ảnh hưởng khi đồng hồ hệ điều hành điều chỉnh theo NTP. Hai timestamp sát nhau có thể bằng nhau vì độ phân giải, không có nghĩa thực thi bằng 0 nanosecond.

Với task chạy qua nhiều service, ghi thời điểm UTC và dùng trace/span ID để liên kết. Không lấy chênh lệch `nanoTime` xuyên máy.

## 4. Ví dụ Java 17+ chạy được với `ThreadPoolTaskExecutor`

Ví dụ dùng Spring Framework 6.1.x và Java 17 trở lên. Lớp `TrackedExecutor` sinh UUID, ghi snapshot khi gửi và ngay trong callback, phản ánh cả thành công, lỗi và từ chối. Future trả về báo **kết quả callback được bọc**, không đại diện mọi công việc phát sinh gián tiếp từ callback. Demo chỉ dùng RAM, chưa thêm lưu trữ bền vững, TTL, retry hoặc cancel.

Cấu trúc tệp:

```text
pom.xml
src/main/java/demo/TrackingDemo.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>tracked-jobs</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.14</version>
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
                <version>3.4.1</version>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/demo/TrackingDemo.java`:

```java
package demo;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class TrackingDemo {
    enum State { SUBMITTED, RUNNING, SUCCEEDED, FAILED, REJECTED }

    record Snapshot(
            String taskId,
            Instant submittedAt,
            Instant startedAt,
            Instant completedAt,
            Instant rejectedAt,
            State state,
            Long queueWaitMs,
            Long runTimeMs,
            String failureType
    ) {}

    record Ticket(String taskId, CompletableFuture<Void> completion) {}

    static final class TrackedExecutor {
        private final ThreadPoolTaskExecutor delegate;
        private final ConcurrentMap<String, Snapshot> tasks =
                new ConcurrentHashMap<>();

        TrackedExecutor(ThreadPoolTaskExecutor delegate) {
            this.delegate = delegate;
        }

        Ticket submit(Runnable work) {
            String id = UUID.randomUUID().toString();
            Instant submitted = Instant.now();
            long submittedTick = System.nanoTime();
            tasks.put(id, new Snapshot(
                    id, submitted, null, null, null,
                    State.SUBMITTED, null, null, null));

            CompletableFuture<Void> completion = new CompletableFuture<>();
            Runnable wrapped = () -> {
                Instant started = Instant.now();
                long startedTick = System.nanoTime();
                long queueMs = TimeUnit.NANOSECONDS.toMillis(
                        startedTick - submittedTick);

                tasks.put(id, new Snapshot(
                        id, submitted, started, null, null,
                        State.RUNNING, queueMs, null, null));
                try {
                    work.run();
                    long runMs = TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startedTick);
                    tasks.put(id, new Snapshot(
                            id, submitted, started, Instant.now(), null,
                            State.SUCCEEDED, queueMs, runMs, null));
                    completion.complete(null);
                } catch (Throwable failure) {
                    long runMs = TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startedTick);
                    tasks.put(id, new Snapshot(
                            id, submitted, started, Instant.now(), null,
                            State.FAILED, queueMs, runMs,
                            failure.getClass().getSimpleName()));
                    completion.completeExceptionally(failure);
                    // Lỗi nghiêm trọng của JVM vẫn được truyền lên worker.
                    if (failure instanceof Error fatal) {
                        throw fatal;
                    }
                }
            };

            try {
                delegate.execute(wrapped);
            } catch (TaskRejectedException rejection) {
                tasks.put(id, new Snapshot(
                        id, submitted, null, null, Instant.now(),
                        State.REJECTED, null, null,
                        rejection.getClass().getSimpleName()));
                completion.completeExceptionally(rejection);
            }
            return new Ticket(id, completion);
        }

        Snapshot find(String taskId) {
            return tasks.get(taskId);
        }
    }

    public static void main(String[] args) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("tracked-");
        pool.initialize();

        try {
            TrackedExecutor tracked = new TrackedExecutor(pool);
            Ticket ticket = tracked.submit(() -> {
                int result = 2 + 3;
                if (result != 5) throw new IllegalStateException();
            });

            ticket.completion().join();
            Snapshot s = tracked.find(ticket.taskId());
            System.out.println("has id: " + !s.taskId().isBlank());
            System.out.println("state: " + s.state());
            System.out.println("all timestamps: " +
                    (s.submittedAt() != null &&
                     s.startedAt() != null &&
                     s.completedAt() != null));
        } finally {
            pool.shutdown();
        }
    }
}
```

Chạy tại thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.TrackingDemo
```

**Stdout của chương trình demo** (môi trường có thể in thông báo Maven/JVM khác):

```text
has id: true
state: SUCCEEDED
all timestamps: true
```

UUID thay đổi giữa các lần chạy nhưng vẫn có mặt trong `Ticket` và `Snapshot`. Nếu callback ném `IllegalStateException`, bản ghi chuyển `FAILED`, có `completedAt`, và `completion.join()` ném `CompletionException` chứa nguyên nhân gốc. Nếu pool từ chối, bản ghi là `REJECTED` với `rejectedAt`, trong khi `startedAt` và `completedAt` vẫn rỗng. Code bắt `Throwable` để ghi nhận trạng thái, hoàn tất future bằng ngoại lệ và ném lại `Error` nghiêm trọng.

**Vì sao ghi `submittedAt` trước `execute()`?** Executor có thể chạy callback tức thì và callback thậm chí kết thúc trước khi `execute()` trả về. Ghi “đã gửi” sau `execute()` có thể làm đảo thứ tự timestamp. Ngược lại, không được tự gán `startedAt` chỉ vì đã gọi được `execute()`; task có thể vẫn đang chờ.

Lưu ý: bản demo lưu snapshot bằng `ConcurrentHashMap`, đủ để đọc nhất quán từng bản ghi trong một process. Đây không phải kho bền vững; khi restart sẽ mất dữ liệu. Cần xử lý cạnh tranh terminal update, khả năng hủy và retention nếu phát triển thành job tracker thực tế.

## 5. Đưa vào Spring Boot và phương thức `@Async`

Ứng dụng có Spring container nên tạo executor bằng bean, ví dụ đoạn mã sau **đặt bên trong một lớp `@Configuration`**, không phải chương trình độc lập:

```java
@Bean("businessExecutor")
ThreadPoolTaskExecutor businessExecutor() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(4);
    e.setMaxPoolSize(8);
    e.setQueueCapacity(100);
    e.setThreadNamePrefix("business-");
    e.initialize();
    return e;
}
```

Có thể inject executor bean vào `TrackedExecutor` thay vì tạo trong `main`. Nếu ứng dụng sử dụng `@Async("businessExecutor")`, cần phân biệt hai đường đo:

1. **Bên gọi:** tạo `taskId` và ghi `submittedAt` ngay trước lời gọi phương thức `@Async` **qua Spring proxy**. Một lời gọi nội bộ `this.method()` thường không đi qua proxy, dẫn đến chạy đồng bộ ngoài dự kiến.
2. **Bên worker:** nhận cùng `taskId` qua tham số hoặc context, ghi `startedAt` ở đầu thân phương thức thực chạy.
3. **Hoàn tất nghiệp vụ:** nếu phương thức khởi tạo pipeline `CompletableFuture` rồi trả sớm, thời điểm thoát khỏi thân phương thức chưa chắc là thời điểm toàn bộ pipeline kết thúc. Chọn đúng future/stage đại diện kết quả cuối và dùng `whenComplete(...)` để ghi kết cục nếu mục tiêu là đo đầu cuối.
4. **Ngoại lệ:** với `@Async` trả `Future`/`CompletableFuture`, quan sát exceptional completion. Với phương thức `@Async void`, caller không nhận exception qua future; dùng `AsyncUncaughtExceptionHandler` hoặc ghi lỗi ở tầng nghiệp vụ.
5. **Giới hạn `TaskDecorator`:** rất hữu ích để truyền context và ghi nhận phần chạy của wrapper, nhưng khi gọi `submit(...)`, decorator có thể được đưa `FutureTask` thay cho `Runnable` nghiệp vụ. `FutureTask` giữ exception bên trong, nên `try/catch` bao quanh `runnable.run()` của decorator có thể **không nhìn thấy lỗi nghiệp vụ**. Muốn ghi `FAILED` đáng tin, hãy quan sát future/kết quả nghiệp vụ hoặc dùng wrapper task chuyên dụng.

Nếu sao chép `MDC`/trace ID sang thread pool, hãy chụp context **ở thread gửi**, thiết lập trước khi callback chạy và **khôi phục/clear trong `finally`**. Thread pool tái sử dụng thread, nên không dọn MDC sẽ làm log task sau mang nhầm `traceId` của task trước. `traceId` nối một request với nhiều tác vụ; `taskId` phân biệt từng tác vụ.

## 6. Log, metric và đối soát

Một bộ event dễ tra cứu (các timestamp và ID sau đây chỉ minh họa, không phải stdout cố định):

```text
event=task_submitted taskId=9f2a... taskType=send-report executor=business submittedAt=2026-10-29T03:00:00.100Z
event=task_started   taskId=9f2a... taskType=send-report executor=business startedAt=2026-10-29T03:00:00.280Z queueWaitMs=180
event=task_finished  taskId=9f2a... taskType=send-report executor=business completedAt=2026-10-29T03:00:00.780Z status=SUCCEEDED runTimeMs=500 endToEndMs=680
```

Giữ tên khóa ổn định (`taskId`, `event`, `taskType`, `executor`, `status`) để lọc log theo đúng một tác vụ ngay cả khi các thread ghi xen kẽ. Nếu endpoint trả HTTP `202 Accepted` kèm mã job cho client, hãy hiểu đó là **đã nhận yêu cầu để xử lý**, không phải lời hứa task đã chạy hoặc chạy thành công; cung cấp endpoint trạng thái/callback nếu sản phẩm cần.

Có thể dùng Micrometer để theo dõi số task submitted/failed/rejected, phân bố `queueWait`/`runTime`, độ dài queue và số worker bận. Dùng tag ít giá trị khác nhau như `executor`, `taskType`, `outcome`; **không dùng UUID taskId làm tag metric** vì tạo cardinality cực cao. Đặt ID trong log, trace hoặc kho trạng thái. Tránh ghi payload nhạy cảm, secret và dữ liệu cá nhân vào log hay tag. Nếu cần tra cứu sau restart hoặc qua nhiều instance, lưu trạng thái ở database/broker có khóa định danh, thời hạn lưu và cơ chế reconciliation; map RAM của demo không đáp ứng yêu cầu đó.

## 7. Kiểm thử những trường hợp quyết định

Không cần đo bằng `Thread.sleep(...)` trong test: dùng `CountDownLatch` chặn worker hoặc phối hợp nhiều task để kiểm soát thứ tự sự kiện.

| Tình huống | Bằng chứng cần kiểm |
| --- | --- |
| Task thành công | Mã khác rỗng, đủ ba mốc, trạng thái `SUCCEEDED`, duration không âm |
| Worker đang bận, task mới xếp hàng | Task mới ở `SUBMITTED` khi worker chưa nhả latch; sau đó `RUNNING` và kết thúc; queue wait có ý nghĩa |
| Callback ném ngoại lệ | `FAILED`, có `completedAt`, `failureType`, future hoàn tất ngoại lệ |
| Queue đầy và policy từ chối | `REJECTED` có `rejectedAt`; không có `startedAt`/`completedAt` |
| Nhiều task cùng lúc | ID riêng, các event ghép đúng ID, không mất snapshot khi truy cập đồng thời |
| Cancel, timeout, retry | Phân biệt caller thôi chờ với task thực sự dừng; mỗi retry có attempt ID riêng |
| Restart hoặc mất log | Không mặc định thiếu `completedAt` nghĩa là task vẫn chạy; có kiểm tra quá hạn và đối soát |

Các lỗi phổ biến:

- **Đặt `startedAt` ngay khi `submit`:** che mất thời gian đợi queue.
- **Chỉ ghi `completedAt` ở nhánh thành công:** lỗi nhìn giống task treo; nhánh ngoại lệ cũng phải chốt kết quả.
- **Dùng `Future.isDone()` suy ra thành công:** future bị hủy hoặc thất bại cũng có thể `isDone() == true`.
- **Đo xong khi mới tạo stage async:** bỏ sót thời gian của pipeline phía sau; xác định chính xác phạm vi “hoàn tất”.
- **Chỉ bắt exception trong `TaskDecorator`:** `FutureTask` có thể nuốt exception vào future.
- **Dùng một taskId chung cho mọi retry:** kết quả và duration của các attempt bị trộn.
- **Đưa taskId vào tag Micrometer:** tăng số chuỗi metric đến mức khó vận hành.
- **Giữ mọi bản ghi vô hạn trong `ConcurrentHashMap`:** rò bộ nhớ; cần eviction/TTL và lưu bền nếu cần.
- **Giả định lúc nào cũng chạy trên worker:** `CallerRunsPolicy` có thể chạy callback trên thread gửi, nên thời gian gửi và bắt đầu rất sát nhau, còn `execute()` có thể chặn caller.

## 8. Checklist áp dụng

Trước khi triển khai, định nghĩa rõ một task kết thúc ở đâu; tạo ID trước khi gửi; đặt ba timestamp ở đúng ranh giới; ghi riêng từ chối và lỗi; dùng `nanoTime` đo duration; truyền cùng ID qua log/trace; thử trường hợp queue chật, lỗi và retry; và đặt giới hạn lưu trữ. Khi làm đúng, ta có thể phân biệt rõ task **chờ lâu**, **chạy lâu**, **thất bại**, **bị từ chối** hay **thiếu dấu kết thúc do sự cố quan sát**.

### Tài liệu tham khảo

- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework Javadoc — `ThreadPoolTaskExecutor`: https://docs.spring.io/spring-framework/docs/6.1.1/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework — Observability Support: https://docs.spring.io/spring-framework/reference/integration/observability.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monitoring-tuning-003">Đo thời gian chờ hàng đợi khác thời gian thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Đo thời gian chờ hàng đợi khác thời gian thực thi

## 1. Vì sao phải tách hai loại thời gian?

Trong một hệ thống xử lý bất đồng bộ, một tác vụ có thể mất rất lâu mới hoàn tất dù phần mã xử lý của nó chạy nhanh. Nguyên nhân thường là tác vụ phải **đợi trong hàng đợi của executor** trước khi được một worker thread nhận. Nếu chỉ đo từ lúc gửi tác vụ đến khi nhận kết quả, ta không biết thời gian bị tiêu tốn ở khâu chờ hay ở khâu thực thi, vì thế dễ điều chỉnh sai: tăng tốc thuật toán khi vấn đề thực sự là executor quá tải, hoặc tăng số thread khi tác vụ đang bị chậm bởi cơ sở dữ liệu.

Bài này tập trung vào việc quan sát và điều chỉnh các tác vụ chạy qua Java Executor/ThreadPoolExecutor và Spring ThreadPoolTaskExecutor, chẳng hạn công việc do một phương thức @Async gửi vào executor.

Sau bài học, bạn có thể:

- Xác định chính xác các mốc thời gian của một tác vụ và gọi tên từng độ trễ.
- Đo riêng thời gian chờ được chạy và thời gian chạy bằng đồng hồ đơn điệu.
- Gắn phép đo vào Spring ThreadPoolTaskExecutor mà không phải sao chép đồng hồ đo trong từng nghiệp vụ.
- Đọc số liệu cùng độ sâu hàng đợi, số worker bận và số tác vụ bị từ chối để tìm điểm nghẽn.
- Tránh những kết luận sai do đo nhầm ranh giới, dùng trung bình thay cho phân vị hoặc chỉ nhìn một chỉ số.

## 2. Ba mốc, ba phép đo

Giả sử mỗi tác vụ có ba mốc trên **cùng một tiến trình và cùng một đồng hồ**:

~~~text
t0: ứng dụng giao tác vụ cho executor
          |
          |  Chờ được phân công / xếp hàng
          v
t1: worker bắt đầu chạy callback của tác vụ
          |
          |  Thực thi callback
          v
t2: callback kết thúc (thành công hoặc lỗi)
~~~

Đặt:

- **Thời gian chờ (submission-to-start delay)** = t1 − t0. Trong các dashboard thực tế, số đo này thường được gọi là queue wait/queue latency.
- **Thời gian thực thi (run duration)** = t2 − t1. Đây là thời gian worker dành để chạy callback, bao gồm cả lúc callback bị chặn vì I/O, lock hoặc sleep.
- **Thời gian từ gửi đến kết thúc (executor sojourn time)** = t2 − t0 = thời gian chờ + thời gian thực thi, nếu cả ba mốc đo một cách nhất quán cho cùng tác vụ.

**Phân biệt quan trọng:** phép đo t1 − t0 bao gồm cả chi phí tiếp nhận, lập lịch và chuyển giao thread, nên không phải lúc nào cũng bằng *thời gian thực sự nằm trong cấu trúc BlockingQueue*. Ví dụ, executor dùng SynchronousQueue không giữ tác vụ trong một hàng đợi có bộ nhớ đệm nhưng vẫn có một độ trễ nhỏ từ lúc gửi đến khi chạy. Muốn đo đúng thời gian cư trú trong queue, cần đo riêng thời điểm enqueue thành công và dequeue; decorator thông thường không cung cấp hai mốc này.

Ngoài ra, thời gian chạy không đồng nghĩa với **thời gian CPU**: một tác vụ chạy 2 giây vì chờ HTTP/DB vẫn có run duration khoảng 2 giây, dù CPU chỉ bận vài mili giây. Thời gian end-to-end mà người dùng nhìn thấy còn có thể gồm cả các bước trước khi submit, truyền dữ liệu, callback sau khi task hoàn tất và gửi phản hồi.

Ví dụ: task A được gửi lúc 10:00:00.000, bắt đầu lúc 10:00:00.350 và kết thúc lúc 10:00:00.400. Khi đó nó chờ 350 ms, chạy 50 ms, tổng từ submit đến hết là 400 ms. Việc tối ưu 50 ms xử lý xuống 25 ms chỉ cải thiện tổng khoảng 6,25% nếu hàng đợi vẫn giữ nguyên.

## 3. Vì sao một tác vụ phải đợi?

ThreadPoolExecutor nhận tác vụ theo cấu hình pool và queue. Với các thiết lập thông thường:

1. Khi số worker hiện tại còn dưới corePoolSize, executor có thể tạo worker để chạy tác vụ mới.
2. Khi đã đạt corePoolSize, executor ưu tiên đưa tác vụ vào queue nếu còn chỗ.
3. Khi queue đầy, executor mới cố tạo thêm worker đến maxPoolSize.
4. Khi pool đã đạt maxPoolSize và không còn chỗ nhận thêm tác vụ, chính sách từ chối được áp dụng.

Đây là quy tắc của ThreadPoolExecutor; không nên suy ra rằng cứ đặt maxPoolSize lớn thì executor sẽ lập tức mở rộng đến số đó. Nếu queue rất lớn và chưa đầy, tác vụ tiếp tục đợi trong queue thay vì kích hoạt thêm worker trên core size.

Trong Spring, **ThreadPoolTaskExecutor** cung cấp corePoolSize, maxPoolSize, queueCapacity cùng các chỉ số như getPoolSize(), getActiveCount() và getQueueSize(). Với queueCapacity dương, nó sử dụng LinkedBlockingQueue; khi queueCapacity bằng 0, nó dùng SynchronousQueue. Cấu hình mặc định của ThreadPoolTaskExecutor có core size bằng 1 và queue capacity rất lớn, vì vậy có thể tạo độ trễ chờ dài nếu lượng công việc đầu vào vượt năng lực một worker.

Các nguyên nhân queue wait tăng thường gặp:

- **Tốc độ nhận việc lớn hơn tốc độ hoàn thành:** lượng tác vụ tồn đọng tích lũy.
- **Pool quá ít worker** so với mức đồng thời hợp lý của loại công việc.
- **Task trước đó chạy lâu hoặc bị block**, khiến worker không quay lại lấy việc mới.
- **Burst traffic:** rất nhiều tác vụ đến trong thời gian ngắn.
- **Một queue chung cho việc nhanh và chậm:** tác vụ ngắn phải đứng sau tác vụ dài (head-of-line blocking).
- **Giới hạn tài nguyên ở nơi khác:** DB connection pool, HTTP client pool hoặc semaphore khiến worker đứng chờ, từ đó làm queue của executor tiếp tục dài.

Cần xem cấu hình và dữ liệu đo cùng nhau. Tăng thread không giải quyết được nút thắt DB và thậm chí có thể làm hệ thống đích chịu tải nặng hơn.

## 4. Ví dụ Java 17+: đo được hai khoảng thời gian

Đo bằng System.nanoTime(), vì đây là đồng hồ dùng cho **khoảng thời gian đã trôi qua**. Không dùng System.currentTimeMillis() để trừ hai mốc tính latency, vì đồng hồ lịch có thể được điều chỉnh.

Chương trình độc lập dưới đây tạo executor **một worker**, giữ worker đó bận bằng latch, rồi gửi tác vụ thứ hai. Vì vậy tác vụ thứ hai buộc phải chờ trước khi chạy.

Tệp **QueueWaitDemo.java**:

~~~java
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class QueueWaitDemo {
    private static double toMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2));

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try {
            Future<?> first = pool.submit(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            firstStarted.await();

            long submittedAt = System.nanoTime();
            Future<?> second = pool.submit(() -> {
                long startedAt = System.nanoTime();

                try {
                    Thread.sleep(40); // Mô phỏng phần xử lý của tác vụ.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    long finishedAt = System.nanoTime();

                    System.out.printf(Locale.ROOT,
                            "wait=%.1f ms, run=%.1f ms, total=%.1f ms%n",
                            toMillis(startedAt - submittedAt),
                            toMillis(finishedAt - startedAt),
                            toMillis(finishedAt - submittedAt));
                }
            });

            Thread.sleep(150);      // Giữ worker đầu tiên bận thêm một lúc.
            releaseFirst.countDown();
            first.get();
            second.get();
        } finally {
            releaseFirst.countDown();
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
javac QueueWaitDemo.java
java QueueWaitDemo
~~~

**Một kết quả minh họa** (các con số không cố định giữa các lần chạy):

~~~text
wait=151.0 ms, run=40.2 ms, total=191.2 ms
~~~

Diễn giải: worker duy nhất đang xử lý task đầu tiên nên task thứ hai chưa thể bắt đầu, dù phần xử lý của task thứ hai chỉ mất khoảng 40 ms. Mốc submittedAt được lấy ngay trước submit; bởi vậy wait còn có một ít chi phí tạo/đưa task vào executor, đúng với khái niệm submission-to-start delay.

Nếu thay bằng phép đo chỉ bao quanh nội dung trong lambda, ta thấy **40 ms** và bỏ sót **khoảng 150 ms chờ**. Ngược lại, nếu chỉ đo thời gian Future.get() chặn ở thread gọi, kết quả còn phụ thuộc vào lúc thread gọi bắt đầu get(), nên cũng không thể coi đó là queue wait.

Ví dụ trên không dùng số mili giây cố định để khẳng định hiệu năng: Sleep, lập lịch của OS, tải máy và độ phân giải đồng hồ có thể khiến số thực tế chênh lệch. Mục tiêu là kiểm chứng *ranh giới đo*, không phải benchmark tốc độ máy.

## 5. Gắn phép đo tập trung vào Spring ThreadPoolTaskExecutor

Một cách áp dụng thuận tiện là cấu hình **TaskDecorator**. Khi executor chuẩn bị thực thi một Runnable, decorator có thể bọc Runnable để ghi lại thời điểm trước khi giao việc và thời điểm worker bắt đầu/kết thúc. Đo bằng timer có thống kê độ trễ thay vì chỉ in log cho mọi task.

Ví dụ dưới đây là **một lớp cấu hình trong ứng dụng Spring**, không phải chương trình Java độc lập: cần Spring Context/Scheduling và một MeterRegistry của Micrometer (chẳng hạn từ cấu hình metrics của Spring Boot + Micrometer).

Tệp **AsyncTimingConfig.java**:

~~~java
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncTimingConfig {

    @Bean("measuredExecutor")
    public ThreadPoolTaskExecutor measuredExecutor(MeterRegistry registry) {
        Timer queueWait = Timer.builder("app.executor.queue.wait")
                .description("Delay from decoration to worker start")
                .tag("executor", "measured")
                .publishPercentileHistogram()
                .register(registry);

        Timer runTime = Timer.builder("app.executor.task.run")
                .description("Duration of the execution callback")
                .tag("executor", "measured")
                .publishPercentileHistogram()
                .register(registry);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("measured-");

        executor.setTaskDecorator(task -> {
            long submittedAt = System.nanoTime();

            return () -> {
                long startedAt = System.nanoTime();
                queueWait.record(
                        startedAt - submittedAt, TimeUnit.NANOSECONDS);
                try {
                    task.run();
                } finally {
                    runTime.record(
                            System.nanoTime() - startedAt,
                            TimeUnit.NANOSECONDS);
                }
            };
        });

        return executor;
    }
}
~~~

Một phương thức sử dụng executor đó:

~~~java
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("measuredExecutor")
    public CompletableFuture<String> generateReport() {
        // Phần nghiệp vụ minh họa.
        return CompletableFuture.completedFuture("ready");
    }
}
~~~

**Cách hiểu số đo:** biến submittedAt trong decorator được tạo khi Spring trang trí callback để submit; đây là xấp xỉ rất tốt cho độ trễ từ lúc chuẩn bị submit đến lúc worker chạy, nhưng không ghi trực tiếp thời điểm enqueue thành công. runTime đo toàn bộ callback thực sự mà executor chạy, nên có thể bao gồm lớp wrapper (ví dụ FutureTask) bên ngoài mã nghiệp vụ. Muốn biết riêng thời gian của một lời gọi DB hay đoạn nghiệp vụ, hãy đặt một timer chuyên biệt tại đoạn đó; không gán mặc nhiên runTime của executor bằng CPU time hoặc DB time.

Khối finally đảm bảo ghi run duration ngay cả khi callback ném exception. Tuy nhiên, một FutureTask có thể **giữ exception bên trong Future** thay vì ném ra ngoài Runnable.run(); decorator không thay thế giám sát lỗi của Future, CompletableFuture hoặc handler của @Async. Tác vụ bị từ chối trước khi chạy sẽ không tạo mẫu runTime/queueWait theo cách đo này: cần đếm rejection riêng.

Khi áp dụng @Async, lời gọi phải đi qua Spring proxy (ví dụ gọi từ bean khác); một phương thức gọi chính phương thức @Async khác trên cùng đối tượng theo kiểu self-invocation có thể chạy đồng bộ và bỏ qua executor. Chỉ giải thích số đo trong bối cảnh executor mà tác vụ **thực sự** sử dụng.

Các tên metric trên có tag ổn định theo executor; tránh gắn task ID, user ID, URL có tham số hoặc giá trị duy nhất vào tag vì sẽ làm bùng nổ số chuỗi thời gian. Có thể tách timer theo nhóm công việc hữu hạn như report, email hoặc indexing nếu việc đó giúp đưa ra quyết định vận hành.

## 6. Cần quan sát thêm gì bên cạnh hai timer?

Hai timer trả lời **đợi bao lâu** và **chạy bao lâu**. Để giải thích nguyên nhân, hãy quan sát thêm:

| Chỉ số | Ý nghĩa | Câu hỏi cần đặt |
| --- | --- | --- |
| Queue wait: count, p50, p95, p99 | Phân bố độ trễ trước khi worker nhận task | Chỉ một số ít task đợi lâu hay cả hệ thống cùng đợi? |
| Run duration: count, p50, p95, p99 | Phân bố thời gian chạy callback | Phần thực thi có thực sự chậm hơn trước? |
| Queue depth và queue capacity | Mức tồn đọng ở thời điểm đo | Hàng đợi có tăng liên tục hoặc chạm trần không? |
| Active threads / pool size / max pool size | Năng lực thực thi hiện đang sử dụng | Có worker rảnh không; pool có đang bão hòa không? |
| Task submitted / completed per second | Tốc độ nhận việc và hoàn thành | Đầu vào có vượt năng lực tiêu thụ trong thời gian dài không? |
| Rejected task count, retry count, timeout | Việc mất khả năng tiếp nhận | Có task không bao giờ chạy nên vắng mặt khỏi timer không? |
| Latency nghiệp vụ / dependency | Thời gian trong DB, HTTP, lock, công việc phía sau | Run duration cao có bắt nguồn từ phụ thuộc chậm không? |

Với Spring ThreadPoolTaskExecutor có thể lấy nhanh getPoolSize(), getActiveCount(), getQueueSize() và getThreadPoolExecutor().getQueue().remainingCapacity() cho executor phù hợp. Khi xuất metrics, dùng cơ chế binder/observability của hệ thống đang chạy nếu có; không liên tục quét hoặc ghi log một dòng cho mọi task trên đường nóng.

**Đọc phân vị đúng:** p95 queue wait = 500 ms nghĩa là khoảng 95% các mẫu wait không vượt 500 ms trong phân bố/cửa sổ tính toán tương ứng. Nó không nghĩa là mọi tác vụ đều đợi 500 ms. Đặc biệt, **p95(wait) + p95(run) không nhất thiết bằng p95(total)** vì các phân vị có thể đến từ những tác vụ khác nhau. Nếu cần SLO từ submit đến hoàn tất, hãy đo riêng total duration trên từng task rồi tính phân vị của chính nó.

Đối với @Async, đừng quên có thể tồn tại nhiều executor trong một ứng dụng. Tổng metrics toàn hệ thống mà không xác định executor cụ thể dễ che mất một hàng đợi bị tắc.

## 7. Chẩn đoán bằng cách ghép các tín hiệu

| Dấu hiệu | Diễn giải có thể xảy ra | Điều cần kiểm tra / điều chỉnh |
| --- | --- | --- |
| Queue wait tăng, run duration ổn định, active threads đạt trần | Năng lực lấy việc không theo kịp đầu vào; backlog | Xem arrival/completion rate, kích thước pool, queue capacity và khả năng scale của dependency; giới hạn đầu vào khi cần |
| Run duration tăng, queue wait ban đầu thấp | Mã xử lý hoặc dependency chậm; worker giữ việc lâu hơn | Profile CPU, đo DB/HTTP/lock, kiểm tra truy vấn và I/O; không vội tăng pool |
| Cả queue wait và run duration cùng tăng | Phụ thuộc chậm làm worker bận lâu, kéo theo tồn đọng | Điều tra nguyên nhân run duration trước, sau đó đánh giá pool và backpressure |
| Queue wait gần 0 nhưng rejection/timeout tăng | Có thể dùng queue rất nhỏ, SynchronousQueue, cấu hình không nhận thêm việc | Theo dõi rejection và chính sách xử lý quá tải; chỉ số wait một mình không đủ |
| Trung bình tốt nhưng p99 wait rất cao | Burst, tác vụ dài trộn tác vụ ngắn, hoặc điều phối không đều | Xem histogram theo thời gian, phân loại tác vụ, cân nhắc executor riêng và ưu tiên hợp lý |
| Active threads thấp nhưng queue wait vẫn cao | Phân loại sai executor, giới hạn khác, worker chưa được tạo hoặc đo sai mốc | Kiểm tra executor thực tế, cấu hình core/max, thread dumps và phép đo |

Một tình huống minh họa:

- Trước giờ cao điểm: p95 wait = 12 ms, p95 run = 80 ms, queue depth thường 0–2.
- Khi tăng tải: p95 wait = 900 ms, p95 run = 85 ms, queue depth lên 100 và active threads đạt mức tối đa.
- Nhận xét hợp lý: phần chạy chưa chậm đáng kể; tác vụ đang **xếp hàng**. Nên kiểm tra tốc độ đến, giới hạn pool và downstream capacity thay vì tối ưu vài mili giây trong xử lý.
- Sau điều chỉnh: cần đo lại cả wait, run, rejection, throughput và SLO. Tăng throughput nhưng gây lỗi downstream hoặc tăng run duration là một đánh đổi có thể không chấp nhận được.

Các số trên chỉ minh họa cách suy luận, không phải ngưỡng chuẩn cho mọi hệ thống.

## 8. Điều chỉnh có kiểm soát

**Bước 1 — Chọn đúng ranh giới.** Xác nhận mốc t0 nằm trước khi giao task, t1 trong worker và t2 trong finally sau callback. Gắn một executor name nhất quán. Nếu tính cả thời gian chờ semaphore bên ngoài hoặc bước tạo payload trước submit, đặt timer riêng cho phần đó.

**Bước 2 — Lập baseline theo tải.** Thu thập count, phân vị wait/run/total, queue depth, active threads, completion rate và rejection trong cùng một khung thời gian. Lấy dữ liệu cả lúc bình thường và lúc cao điểm; một ảnh chụp queue depth tức thời không thể hiện lịch sử hàng đợi.

**Bước 3 — Xác định tính chất tác vụ.** Việc chủ yếu CPU-bound thường không có lợi khi tăng thread quá mức số lõi thực dụng; việc I/O-bound có thể chịu mức đồng thời cao hơn nhưng bị giới hạn bởi DB connection pool, HTTP quotas, memory, lock và băng thông. Đừng áp dụng một kích cỡ pool cho mọi workload.

**Bước 4 — Thay đổi từng biến và kiểm chứng.** Cân nhắc corePoolSize, maxPoolSize, queueCapacity, tách executor cho nhóm công việc, giới hạn tốc độ gửi vào, giảm kích thước batch, caching, tối ưu dependency hoặc backpressure. Queue lớn có thể che burst ngắn nhưng cũng giữ task quá lâu và tăng sử dụng bộ nhớ; queue nhỏ dễ bộc lộ quá tải bằng rejection. Chính sách rejection phải được xử lý rõ tại nơi gửi tác vụ.

**Bước 5 — Xem lại SLO và tác dụng phụ.** So sánh trước/sau dưới mức tải tương đương. Quan sát cả error rate, timeout, p95/p99 end-to-end, tài nguyên, retry và lượng việc tồn đọng; không coi việc giảm queue wait đơn lẻ là đã thành công.

Không dùng những trị số ví dụ trong bài như giới hạn cấu hình production. Ngưỡng cảnh báo nên xuất phát từ deadline và SLO của từng loại tác vụ: chẳng hạn task cần hoàn thành trong 300 ms thì queue wait p95 250 ms có thể đã nghiêm trọng dù run time p95 chỉ 40 ms.

## 9. Sai lầm thường gặp và các trường hợp đặc biệt

1. **Chỉ bấm đồng hồ trong method @Async.** Khi method bắt đầu, task đã rời giai đoạn chờ; timer này không phản ánh backlog trước đó. Bọc điểm giao việc/executor mới đo được t0.
2. **Lấy Future.get() làm run duration.** Đây là thời gian thread gọi phải đợi kể từ khi gọi get(), không phải thời gian callback chạy. get() có thể trả về ngay nếu task đã hoàn tất.
3. **Dùng thời gian đồng hồ lịch hoặc đồng hồ từ hai máy để trừ.** Dùng System.nanoTime() trong cùng tiến trình cho duration; thời gian xuyên dịch vụ cần tracing, các span nhất quán và hiểu sai lệch đồng hồ.
4. **Không đo khi có exception.** Ghi run duration trong finally, đồng thời đo outcome/error bằng cơ chế riêng. Task bị cancel trước khi chạy hoặc bị reject phải có thống kê riêng; chúng không xuất hiện trong phân bố các task đã bắt đầu.
5. **Dùng run duration như CPU time.** I/O, sleep, tranh chấp lock đều có thể làm run duration tăng. Khi cần CPU time, dùng profiler, Java Flight Recorder hoặc ThreadMXBean phù hợp.
6. **Quên CallerRunsPolicy.** Khi quá tải, caller có thể tự thực thi task, làm submission-to-start wait rất nhỏ dù request gốc bị block. Cần đo thời gian execute()/submit() ở caller và phân biệt thread thực thi.
7. **Áp dụng nhầm cho mọi kiểu scheduler.** Với @Scheduled, độ trễ từ thời điểm dự kiến đến lúc trigger thật là *scheduling lateness*, còn chờ trong executor là một giai đoạn khác. Với virtual threads hoặc giới hạn concurrency bằng semaphore, điểm chờ có thể nằm ngoài ThreadPoolExecutor truyền thống.
8. **Không phân biệt các stage của CompletableFuture.** thenApplyAsync, thenComposeAsync và các stage khác có thể được gửi vào executor khác nhau; mỗi lần chuyển giao có thể tạo một khoảng chờ riêng. Một timer quanh stage cuối không giải thích toàn bộ pipeline.
9. **Chỉ nhìn average và queue size hiện tại.** Burst rất ngắn có thể làm p99 tăng vọt trong khi mean bình thường; queue có thể đã được tháo rỗng vào lúc bạn kiểm tra. Dùng histogram và cửa sổ đo nhất quán.
10. **Tăng maxPoolSize trong khi queue vẫn không đầy.** ThreadPoolExecutor thường xếp hàng sau khi đạt corePoolSize và chỉ mở rộng vượt core khi queue không nhận được việc. Hãy đọc lại chính sách queue của cấu hình đang dùng.
11. **Cho rằng decorator quan sát toàn bộ mã nghiệp vụ.** Decorator có thể bọc FutureTask/callback thay vì chính Runnable do người dùng tạo. Dùng timer nghiệp vụ riêng khi cần biết thời gian phương thức cụ thể, đồng thời đo status/exception theo ngữ cảnh phù hợp.
12. **Ghi log đồng bộ cho từng task ở tải lớn.** Chính thao tác log/ghi metric có thể làm tăng run duration; ưu tiên timer gọn nhẹ, sampling hợp lý và giới hạn cardinality.

## 10. Bài thực hành tự kiểm tra

Giả sử executor có hai worker, queue hữu hạn và nhận nhóm tác vụ dùng HTTP chậm:

- Ở mức tải thấp, queue wait p95 = 5 ms, run p95 = 120 ms.
- Ở mức tải cao, queue wait p95 = 700 ms, run p95 = 620 ms, active threads luôn bằng 2 và queue depth tăng.

Hãy trả lời trước khi thay đổi cấu hình:

1. Có thể kết luận rằng chỉ cần tăng maxPoolSize lên 20 là xong không? **Không.** Phải xem queueCapacity có cho phép mở thêm worker hay không, đồng thời xác định giới hạn của HTTP service phía sau.
2. Vì sao queue wait tăng theo sau run duration? **Worker bị chiếm lâu hơn**, nên không đủ tốc độ lấy task tiếp theo khỏi hàng đợi.
3. Cần thêm số đo nào? **Latency HTTP, timeout/retry, active connections, submitted/completed rate, rejection**, cùng phân bố end-to-end cho cùng loại task.
4. Tiêu chí kiểm chứng cải tiến là gì? **Giảm wait và total latency trong SLO mà không làm xấu run duration, lỗi downstream, memory và lượng việc bị từ chối.**

**Điểm nhớ:** thời gian chờ trả lời câu hỏi “tác vụ đợi bao lâu mới được chạy?”, còn thời gian thực thi trả lời “sau khi được chạy, callback mất bao lâu?”. Chỉ khi đo riêng hai giai đoạn, đặt chúng cạnh độ sâu queue, mức bận của pool và lỗi quá tải, bạn mới biết nên tối ưu tác vụ, điều chỉnh executor hay kiểm soát đầu vào.

## Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): cách cấu hình TaskExecutor, kích thước pool và queue.
- [Spring Framework — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): TaskDecorator và các chỉ số pool.
- [Java SE — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): thuật toán tiếp nhận task, queue và rejection.
- [Java SE — System.nanoTime](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime()): dùng để đo elapsed time, không biểu diễn thời gian lịch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monitoring-tuning-004">Quan sát số luồng, activeCount, kích thước hàng đợi và tác vụ bị từ chối</a>

<details>
<summary>Xem chi tiết</summary>

# Quan sát số luồng, activeCount, kích thước hàng đợi và tác vụ bị từ chối

Trong Spring Framework, `ThreadPoolTaskExecutor` thường chạy các công việc bất đồng bộ như xử lý thông báo, gọi dịch vụ ngoài hoặc thực thi phương thức `@Async`. Để biết executor còn khả năng tiếp nhận việc hay đang quá tải, cần quan sát **số luồng thực tế**, **luồng đang bận**, **số tác vụ chờ trong hàng đợi** và **số lần từ chối nhận tác vụ**. Bốn nhóm tín hiệu này bổ sung cho nhau; không thể kết luận tình trạng pool chỉ từ một chỉ số.

## 1. Hiểu cách pool nhận công việc

`ThreadPoolTaskExecutor` của Spring bọc một `java.util.concurrent.ThreadPoolExecutor`. Ba tham số quan trọng là:

- `corePoolSize`: số luồng lõi mà pool hướng tới duy trì (không đồng nghĩa số luồng luôn đang bận).
- `maxPoolSize`: giới hạn trên của số luồng do pool tạo.
- `queueCapacity`: số tác vụ tối đa có thể chờ trong hàng đợi nếu cấu hình hàng đợi hữu hạn.

Với một pool đang chạy và hàng đợi hữu hạn, quá trình tiếp nhận tác vụ thường theo thứ tự:

1. Khi số luồng hiện có còn dưới `corePoolSize`, tạo luồng mới để chạy tác vụ.
2. Khi đã đạt số luồng lõi, ưu tiên đưa tác vụ vào hàng đợi nếu còn chỗ.
3. Khi hàng đợi đầy, tạo thêm luồng đến `maxPoolSize` nếu được phép.
4. Khi cả pool và hàng đợi đều hết sức chứa (hoặc executor đã dừng), kích hoạt chính sách từ chối (`RejectedExecutionHandler`).

Ví dụ: `corePoolSize=2`, `maxPoolSize=4`, `queueCapacity=8`. Nếu các tác vụ chạy đủ lâu, hai tác vụ đầu có thể chiếm hai luồng; tám tác vụ tiếp theo chờ; sau đó pool mới tăng dần đến bốn luồng. Khi mọi chỗ đã kín, yêu cầu kế tiếp sẽ bị xử lý theo chính sách từ chối.

**Điểm dễ nhầm:** mặc định `ThreadPoolTaskExecutor` có hàng đợi dung lượng rất lớn (`Integer.MAX_VALUE`). Với hàng đợi gần như không giới hạn, pool thường tiếp tục xếp hàng sau khi đạt `corePoolSize` thay vì mở rộng đến `maxPoolSize`; việc chờ có thể tăng mạnh và tiêu tốn bộ nhớ. Ngược lại, `queueCapacity <= 0` sử dụng kiểu hàng đợi chuyển giao trực tiếp (`SynchronousQueue`), không lưu tác vụ chờ: kích thước queue thường bằng 0 dù pool đang rất bận.

## 2. Đọc đúng các chỉ số

| Chỉ số | Lấy từ Spring / JDK | Ý nghĩa |
| --- | --- | --- |
| Luồng hiện có | `executor.getPoolSize()` | Số luồng hiện đang thuộc pool, gồm cả bận và rảnh. |
| Luồng đang xử lý | `executor.getActiveCount()` | Số luồng ước lượng đang thực hiện tác vụ; không phải tổng số thread của JVM hay số CPU đang dùng. |
| Luồng lõi và tối đa | `executor.getCorePoolSize()`, `executor.getMaxPoolSize()` | Tham số cấu hình, dùng làm mốc so sánh với tải thực tế. |
| Tác vụ đang chờ | `executor.getQueueSize()` | Số tác vụ đang nằm trong hàng đợi, chưa được luồng lấy ra thực thi. |
| Chỗ trống của queue | `executor.getThreadPoolExecutor().getQueue().remainingCapacity()` | Sức chứa còn lại theo hàng đợi; hữu ích với queue hữu hạn. |
| Tác vụ đã hoàn tất | `executor.getThreadPoolExecutor().getCompletedTaskCount()` | Tổng số tác vụ đã kết thúc thực thi, mang tính xấp xỉ; không khẳng định nghiệp vụ thành công. |
| Đỉnh số luồng | `executor.getThreadPoolExecutor().getLargestPoolSize()` | Số luồng đồng thời lớn nhất từng có trong pool. |
| Tác vụ đã được lập lịch thực thi | `executor.getThreadPoolExecutor().getTaskCount()` | Ước lượng tổng tác vụ từng được pool tiếp nhận/lên lịch, không phải số yêu cầu API. |
| Số lần từ chối | Tự đếm ở `RejectedExecutionHandler` hoặc điểm gọi `execute/submit` | Không có một phương thức `getRejectedCount()` tích hợp sẵn trên `ThreadPoolExecutor`. |

`ThreadPoolTaskExecutor.getQueueSize()` có từ Spring Framework 5.3.21. Với phiên bản cũ hơn, sau khi executor đã khởi tạo, có thể đọc `getThreadPoolExecutor().getQueue().size()`.

Các phép đọc đều là **ảnh chụp gần đúng tại một thời điểm**, không phải bản snapshot nguyên tử. Ngay giữa hai lệnh đọc, thread có thể bắt đầu/kết thúc tác vụ hoặc một tác vụ mới có thể vào/ra queue. Do đó, `poolSize - activeCount` chỉ là ước lượng số luồng rảnh; không nên dùng để bảo đảm tuyệt đối có thể nhận thêm tác vụ. `queueSize=0` cũng không có nghĩa hệ thống không có việc: các luồng có thể đang xử lý rất nhiều tác vụ.

## 3. Cấu hình một executor có giới hạn và có bộ đếm từ chối

Ví dụ dưới đây dùng Spring Framework với ứng dụng có component scan trong package `demo`. Hai đoạn là hai tệp Java của **ứng dụng Spring**, không phải chương trình Java SE chạy độc lập. Spring quản lý việc `initialize()` và `shutdown()` của bean.

Tệp `ExecutorConfig.java`:

~~~java
package demo;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class ExecutorConfig {

    @Bean("workExecutorRejectedTotal")
    AtomicLong workExecutorRejectedTotal() {
        return new AtomicLong();
    }

    @Bean("workExecutor")
    ThreadPoolTaskExecutor workExecutor(
            @Qualifier("workExecutorRejectedTotal") AtomicLong rejectedTotal) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("work-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);

        executor.setRejectedExecutionHandler((task, pool) -> {
            rejectedTotal.incrementAndGet();
            throw new RejectedExecutionException(
                    "workExecutor is saturated or shutting down");
        });
        return executor;
    }
}
~~~

Ở đây hàng đợi được giới hạn để tránh tích lũy vô hạn, còn handler **đếm một lần cho mỗi lần gửi tác vụ bị từ chối** rồi ném `RejectedExecutionException`. Với lời gọi `ThreadPoolTaskExecutor.execute(...)`/`submit(...)`, Spring chuyển ngoại lệ từ JDK thành `TaskRejectedException` để phía gửi có thể xử lý. Con số này tính cả các lần từ chối do shutdown; không đồng nhất với số tác vụ chạy lỗi bên trong luồng. `AtomicLong` chỉ tồn tại trong một tiến trình và trở về 0 khi ứng dụng khởi động lại; trong hệ thống thật nên xuất nó thành metric dạng counter có nhãn executor ổn định.

Nếu thực thi phương thức `@Async`, chỉ rõ tên pool để chắc chắn quan sát đúng executor:

~~~java
@Async("workExecutor")
public void processOrder(String orderId) {
    // Thực hiện nghiệp vụ; đây là đoạn minh họa trong một Spring bean.
}
~~~

Cần có `@EnableAsync` ở cấu hình async tương ứng. Đoạn `@Async` trên minh họa cách gắn công việc vào pool; nó không phải một chương trình độc lập.

## 4. Chụp trạng thái định kỳ

Tệp `WorkExecutorMonitor.java` trong cùng ứng dụng:

~~~java
package demo;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class WorkExecutorMonitor {
    private final ThreadPoolTaskExecutor executor;
    private final AtomicLong rejectedTotal;

    public WorkExecutorMonitor(
            @Qualifier("workExecutor") ThreadPoolTaskExecutor executor,
            @Qualifier("workExecutorRejectedTotal") AtomicLong rejectedTotal) {
        this.executor = executor;
        this.rejectedTotal = rejectedTotal;
    }

    @Scheduled(fixedDelay = 10_000)
    public void report() {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();

        System.out.printf(
                "pool=%d core=%d max=%d active~=%d queued=%d "
                        + "remaining=%d completed~=%d rejected=%d%n",
                executor.getPoolSize(),
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getActiveCount(),
                executor.getQueueSize(),
                pool.getQueue().remainingCapacity(),
                pool.getCompletedTaskCount(),
                rejectedTotal.get());
    }
}
~~~

Kết quả có dạng `pool=3 core=2 max=4 active~=3 queued=8 remaining=0 completed~=120 rejected=2`, nhưng **mọi con số chỉ là ví dụ minh họa**, thay đổi theo tải và thời điểm đo. Trong production nên gửi các giá trị vào log có cấu trúc hoặc hệ thống metrics thay cho in `System.out`. Tránh ghi log quá thường xuyên với mọi tác vụ; lấy mẫu theo chu kỳ (ví dụ 10 giây) là đủ để bắt đầu quan sát xu hướng.

`getThreadPoolExecutor()` yêu cầu executor đã khởi tạo, nếu gọi trước khi Spring khởi tạo bean có thể ném `IllegalStateException`.

## 5. Phân biệt từ chối với lỗi khi chạy và chính sách xử lý

Một tác vụ có thể không hoàn tất theo nhiều cách. Cần ghi nhận chúng khác nhau:

- **Bị từ chối tại lúc gửi:** executor không tiếp nhận tác vụ; với handler kiểu `AbortPolicy` như ví dụ, phía gửi nhận `TaskRejectedException` từ Spring.
- **Đã được nhận nhưng thực thi lỗi:** lỗi xuất hiện bên trong `Runnable`/`Callable` hoặc kết quả `Future`; đây **không phải** một lần bị từ chối.
- **Bị hủy hoặc bỏ qua:** phụ thuộc chính sách, shutdown và hành vi của ứng dụng; không thể suy ra đầy đủ từ `completedTaskCount`.

Các chính sách từ chối phổ biến của JDK:

| Chính sách | Khi pool không nhận thêm được | Ý nghĩa cho quan sát |
| --- | --- | --- |
| `AbortPolicy` (thường là mặc định) | Ném `RejectedExecutionException`; Spring bao thành `TaskRejectedException` tại API Spring. | Dễ phát hiện lỗi gửi và đếm số lần từ chối. |
| `CallerRunsPolicy` | Thường chạy tác vụ ngay ở luồng gọi, tạo hiệu ứng chậm đầu vào; khi pool đã shutdown, tác vụ có thể không được chạy. | Không được coi mọi lần kích hoạt handler là tác vụ mất; theo dõi cả độ trễ bên gọi. |
| `DiscardPolicy` | Im lặng bỏ tác vụ. | Bắt ngoại lệ ở nơi gửi sẽ không phát hiện được các tác vụ bị bỏ. |
| `DiscardOldestPolicy` | Loại tác vụ chờ lâu nhất rồi thử gửi lại tác vụ mới. | Có thể mất tác vụ cũ dù không thấy ngoại lệ từ lời gọi mới. |

Nếu cần **đếm chính xác yêu cầu không được chấp nhận**, dùng một chính sách xác định và thống nhất định nghĩa counter. Handler trong ví dụ dùng cách abort rõ ràng. Không đồng thời đếm trong handler và trong `catch (TaskRejectedException)` của cùng lời gọi, vì sẽ đếm trùng. Với công việc không được phép mất, phải có cơ chế backpressure, retry có giới hạn với idempotency hoặc hàng đợi bền vững theo yêu cầu nghiệp vụ; chỉ tăng `maxPoolSize` thường không giải quyết nguyên nhân quá tải.

## 6. Minh họa có thể chạy bằng Java 17, không cần Spring

Ví dụ thuần JDK dưới đây cố tình giữ hai worker đang bận để có snapshot **ổn định**: một việc đang đợi trong queue, một lời gọi tiếp theo bị từ chối. Đây là cách kiểm chứng quy tắc vận hành của `ThreadPoolExecutor` mà Spring sử dụng bên dưới.

Lưu thành `PoolPressureDemo.java`:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PoolPressureDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger rejected = new AtomicInteger();

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 2, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        Runnable blockedTask = () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            pool.execute(blockedTask); // Tạo worker thứ nhất.
            pool.execute(blockedTask); // Đưa một tác vụ vào queue.
            pool.execute(blockedTask); // Queue đầy: tạo worker thứ hai.
            started.await();           // Chờ cả hai worker thực sự đang chạy.

            try {
                pool.execute(blockedTask); // Pool + queue đều đầy.
            } catch (RejectedExecutionException expected) {
                rejected.incrementAndGet();
            }

            System.out.printf("pool=%d active=%d queued=%d rejected=%d%n",
                    pool.getPoolSize(), pool.getActiveCount(),
                    pool.getQueue().size(), rejected.get());
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
javac PoolPressureDemo.java
java PoolPressureDemo
~~~

Đầu ra trong tình huống các worker được giữ bởi `CountDownLatch`:

~~~text
pool=2 active=2 queued=1 rejected=1
~~~

`started.await()` bảo đảm cả hai worker đã bắt đầu và đang đợi tại `release.await()` trước lúc đo; vì vậy tác vụ thứ tư không thể chen vào queue hoặc chạy ngay. Sau khi in, chương trình mở latch, đóng pool và đợi các tác vụ còn lại hoàn thành. Trong ứng dụng thực tế, số đo sẽ thay đổi liên tục và không nên khẳng định một stdout cố định như thí nghiệm này.

## 7. Thu thập metric trong Spring Boot và Micrometer

Nếu ứng dụng sử dụng **Spring Boot Actuator + Micrometer**, Boot có thể tự instrument các bean `ThreadPoolTaskExecutor` khi truy cập được `ThreadPoolExecutor` bên dưới. Tùy phiên bản và registry, các metric thường gặp là:

- `executor.pool.size`: số luồng hiện tại;
- `executor.active`: số luồng đang bận (gauge);
- `executor.queued`: số tác vụ chờ (gauge);
- `executor.queue.remaining`: chỗ trống còn lại (gauge);
- `executor.completed`: tổng việc đã hoàn thành (counter/function counter);
- các chỉ số `executor.pool.core` và `executor.pool.max` khi binder cung cấp.

Các metric thường được gắn nhãn theo tên executor. Trong môi trường có Actuator, kiểm tra danh sách thực tế tại `GET /actuator/metrics` rồi truy vấn tên metric xuất hiện, thay vì giả định mọi phiên bản đều có toàn bộ tên nói trên. Với Prometheus, cần thêm Micrometer Prometheus registry và bật endpoint tương ứng trong cấu hình dành cho môi trường giám sát; giữ endpoint được bảo vệ.

**Số lần reject thường phải tự bổ sung.** Thay bộ đếm `AtomicLong` của ví dụ bằng `Counter` của Micrometer trong handler nếu muốn biểu đồ và cảnh báo qua nhiều thời điểm (đây là đoạn mã gắn vào cấu hình bean, không phải chương trình độc lập):

~~~java
Counter rejectedCounter = Counter.builder("app.executor.rejected")
        .description("Rejected submission attempts")
        .tag("executor", "workExecutor")
        .register(meterRegistry);

executor.setRejectedExecutionHandler((task, pool) -> {
    rejectedCounter.increment();
    throw new RejectedExecutionException("workExecutor rejected task");
});
~~~

Cần import `io.micrometer.core.instrument.Counter`, cung cấp `MeterRegistry meterRegistry` từ Spring và dùng **một** handler đếm reject để tránh tăng hai lần. Nhãn phải có số giá trị hữu hạn (tên executor, tên dịch vụ), không gắn ID của từng request, đơn hàng hay người dùng. Counter nên được quan sát theo **mức tăng mỗi khoảng thời gian**, không chỉ nhìn giá trị tích lũy; khi ứng dụng khởi động lại, counter trong tiến trình bắt đầu lại.

Các gauge cho pool/queue giúp biết tình trạng hiện tại; counter reject giúp biết có lúc hệ thống đã **không tiếp nhận** công việc. Nếu cần biết người dùng phải đợi bao lâu, hãy đo thêm thời gian tác vụ nằm trong queue và thời gian thực thi; chỉ `queueSize` không thể hiện độ trễ.

## 8. Cách đọc dashboard và tìm nguyên nhân

| Hiện tượng quan sát trong nhiều lần lấy mẫu | Cách diễn giải và bước kiểm tra |
| --- | --- |
| `activeCount` gần `maxPoolSize`, `queueSize` gần dung lượng, reject tăng | Tải đến vượt năng lực xử lý hoặc tác vụ chạy quá chậm. Kiểm tra thời gian xử lý, DB/I/O, retry và giới hạn đầu vào. |
| `poolSize` gần `corePoolSize`, queue tăng rất dài nhưng chưa lên `maxPoolSize` | Queue có thể quá lớn hoặc gần như vô hạn: executor ưu tiên xếp hàng thay vì tạo thêm worker. Xem lại `queueCapacity`. |
| `poolSize` cao nhưng `activeCount` thấp, queue gần 0 | Nhiều worker đang rảnh sau đợt tải; chưa phải dấu hiệu quá tải. Xem thêm keep-alive và xu hướng theo thời gian. |
| Queue tăng liên tục, reject chưa xuất hiện | Đầu vào đang vượt thông lượng đầu ra nhưng vẫn còn chỗ đệm. Nên xử lý trước khi queue đầy. |
| Reject tăng ngay cả khi snapshot trông còn chỗ | Có thể có burst giữa các lần đo, pool đang shutdown, chính sách riêng, hoặc các lần đọc không đồng thời. Xem thời điểm sự kiện và log phía gửi. |
| `activeCount` cao trong thời gian dài, thông lượng thấp | Worker có thể bị chặn ở lock, HTTP/DB, I/O hoặc chờ kết quả khác; kiểm tra thread dump, timeout và dependency latency. |

Một công thức sơ bộ với hàng đợi hữu hạn là `queueFill = queued / queueCapacity`; không dùng tỉ lệ này khi queue không có sức chứa hữu hạn hoặc `queueCapacity=0`. Cùng một giá trị `activeCount=4` có thể là bình thường khi pool tối đa 32, nhưng là đầy tải nếu pool tối đa 4. Cần luôn đặt số đo trong bối cảnh cấu hình và tốc độ đến/hoàn thành của tác vụ.

Không đưa ra cảnh báo chỉ vì một lần `activeCount` đạt `maxPoolSize`; các spike ngắn có thể hợp lý. Nên cảnh báo khi queue tăng kéo dài, thời gian chờ vượt SLO, hoặc bộ đếm reject tăng đối với công việc bắt buộc phải nhận. Đồng thời theo dõi tỷ lệ lỗi thực thi vì tác vụ **được nhận rồi thất bại** không xuất hiện trong reject counter.

## 9. Lỗi phổ biến và checklist áp dụng

**Lỗi thường gặp:**

1. Nhầm `poolSize` với `activeCount`, hoặc nghĩ `activeCount` bằng số luồng của cả ứng dụng.
2. Chỉ tăng `maxPoolSize` khi queue đang gần như không giới hạn: mức max có thể không được dùng.
3. Kết luận `queueSize=0` là không có tải, đặc biệt với `SynchronousQueue`.
4. Lấy một mẫu đơn lẻ làm trạng thái chính xác; bỏ qua biến động và tính xấp xỉ.
5. Coi `completedTaskCount` là tổng công việc thành công và coi mọi exception trong task là reject.
6. Bắt mỗi `TaskRejectedException` để đếm trong khi đang dùng `DiscardPolicy`/`CallerRunsPolicy`: nhiều tình huống không phát sinh exception.
7. Theo dõi sai bean executor do `@Async` đang chọn executor khác, hoặc quan sát một instance không phải instance ứng dụng sử dụng.
8. Tăng luồng vô hạn mà không kiểm tra RAM, CPU, kết nối DB, downstream rate limit và băng thông.

**Checklist triển khai:** xác định đúng executor đang xử lý việc; cấu hình `core/max/queueCapacity` hữu hạn và có chủ đích; xuất `poolSize`, `activeCount`, `queueSize`, `completedTaskCount` và reject counter; phân biệt reject với task failure; theo dõi chuỗi thời gian cùng độ trễ thực thi/queue; thử tải tăng dần và shutdown để xác minh alert cũng như hành vi fallback/retry. Sau mỗi thay đổi cấu hình, đo lại thông lượng, p95/p99 latency, CPU, RAM và áp lực lên dịch vụ phụ thuộc.

## Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring `ThreadPoolTaskExecutor` API](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [JDK `ThreadPoolExecutor` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Spring Boot — Task Execution and Scheduling Metrics](https://docs.spring.io/spring-boot/3.5/reference/actuator/metrics.html)
- [Micrometer — JVM/Executor metrics](https://docs.micrometer.io/micrometer/reference/1.12/reference/jvm.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monitoring-tuning-005">Theo dõi số lần chạy lịch thành công, lỗi và lần chạy gần nhất</a>

<details>
<summary>Xem chi tiết</summary>

# Theo dõi số lần chạy lịch thành công, lỗi và lần chạy gần nhất trong Spring Framework

## 1. Vì sao cần theo dõi tác vụ chạy theo lịch?

Một phương thức gắn `@Scheduled` có thể chạy âm thầm trong nền. Việc ứng dụng vẫn trả HTTP 200 không chứng minh tác vụ đồng bộ dữ liệu, dọn dẹp hay gửi báo cáo đang hoạt động tốt. Khi vận hành cần trả lời được: tác vụ **đã thực sự chạy chưa**, bao nhiêu lượt **thành công/thất bại**, **lần gần nhất** bắt đầu và kết thúc khi nào, kết quả là gì, có đang quá hạn không.

Trong module `Spring Framework - concurrency`, chủ đề này thuộc nhóm **Quan sát và điều chỉnh**. Mục tiêu là thiết kế tín hiệu đủ chính xác để chẩn đoán lỗi, tạo dashboard và cảnh báo; đồng thời hiểu giới hạn của trạng thái lưu trong bộ nhớ.

### Phân biệt các khái niệm

| Khái niệm | Định nghĩa nên thống nhất | Cách biểu diễn |
| --- | --- | --- |
| Lượt được lên lịch (scheduled/trigger) | Đến thời điểm mà bộ lập lịch dự định gọi tác vụ | Lịch cron/fixed rate; không mặc nhiên là một lượt đã chạy |
| Lượt bắt đầu (attempt/start) | Phần công việc thực sự được bắt đầu | Thời điểm `lastStartedAt`; tùy yêu cầu có thêm bộ đếm attempts |
| Thành công (success) | Công việc nghiệp vụ hoàn tất theo tiêu chí đã định | Counter `outcome=success`; `lastSuccessfulAt` |
| Thất bại (failure) | Lượt chạy hoàn tất với ngoại lệ hoặc kết quả nghiệp vụ được xác định là lỗi | Counter `outcome=failure`; `lastFailedAt` |
| Lần chạy gần nhất | Cần nói rõ: **bắt đầu gần nhất** hay **hoàn tất gần nhất** | `lastStartedAt` khác `lastFinishedAt`; snapshot có `outcome` |
| Đang chạy (running) | Đã bắt đầu nhưng chưa kết thúc | Trạng thái đang chạy hoặc gauge số lượt active |
| Bị bỏ qua (skipped) | Không thực thi vì khóa, điều kiện nghiệp vụ, tính năng tắt… | Nên phân loại riêng, tránh mặc định tính là success |

Với tác vụ tuần tự, không bỏ qua, và chỉ đếm lượt **đã kết thúc**, ta kỳ vọng:

`completed = successful + failed`

Nếu có trạng thái `skipped` hoặc `cancelled` thì cần cộng chúng vào mẫu số tương ứng. Không suy ra số lỗi từ số log `ERROR`: một lượt có thể ghi nhiều log lỗi, hoặc lỗi bị bắt và không được log. Cũng không suy ra thành công chỉ vì phương thức trả về bình thường nếu công việc thực tế được đẩy sang luồng/`Future` khác chưa kết thúc.

## 2. Vòng đời của một lần thực thi

```text
Đến thời điểm kích hoạt
        |
        v
Tác vụ thật sự bắt đầu --> ghi lastStartedAt
        |
        v
Thực thi nghiệp vụ
   /                  \
hoàn tất tốt        có lỗi
   |                  |
tăng success         tăng failure; log và chuyển tiếp lỗi
   \                  /
    v                v
ghi thời lượng, lastFinishedAt, lastRun (thời điểm + kết quả)
        |
        v
Bộ lập lịch tiếp tục đánh giá lần kích hoạt tiếp theo
```

- `fixedDelay`: khoảng cách tính từ **khi lần trước kết thúc** đến khi lần sau bắt đầu. Phù hợp khi không muốn dồn lượt trên một trigger tuần tự.
- `fixedRate`: nhắm đến khoảng cách giữa **thời điểm bắt đầu** các lượt; thời gian thực tế có thể trễ do luồng bận. Không được mặc định mọi lần đều chạy đúng mốc.
- `cron`: chọn các thời điểm theo biểu thức cron (Spring dùng sáu trường, gồm giây); chỉ định `zone` nếu phụ thuộc múi giờ. Một lịch bị lỡ không tự chứng minh là đã có lượt chạy thành công.
- `@Scheduled` đồng bộ được gọi bởi scheduler; cấu hình thread pool, nhiều lịch trên cùng method, `@Async` và nhiều instance ứng dụng có thể khiến phạm vi đo hoặc thứ tự hoàn tất phức tạp hơn.

Hãy đặt thao tác ghi nhận thành công **sau** khi nghiệp vụ hoàn tất, ghi lỗi trong `catch`, và cập nhật thời điểm kết thúc trong `finally`. Nên đo thời lượng bằng `System.nanoTime()` (đồng hồ đơn điệu), còn timestamp để hiển thị/so sánh theo thời gian bằng `Instant.now()`.

## 3. Quan sát sẵn có: Spring Boot Actuator

Với ứng dụng có Spring Boot Actuator, endpoint `/actuator/scheduledtasks` liệt kê các tác vụ `cron`, `fixedDelay`, `fixedRate` và custom trigger. Trong các phiên bản Spring Boot 3.5/4.1, dữ liệu có thể chứa `lastExecution` với `status` (`STARTED`, `SUCCESS`, `ERROR`), `time` và thông tin ngoại lệ, cùng `nextExecution` khi biết được. Đây là công cụ **kiểm tra lịch và lần chạy cuối**, không phải kho lịch sử hay bộ đếm thành công/lỗi bền vững.

`/actuator/metrics` có thể đọc các meter được đăng ký qua Micrometer; `/actuator/prometheus` dùng cho Prometheus scrape khi có `micrometer-registry-prometheus`. Actuator không tự tạo đúng các bộ đếm nghiệp vụ mà mọi hệ thống cần: ta vẫn phải xác định success/failure và thêm meter phù hợp, hoặc cấu hình observation cho scheduled tasks.

Ví dụ cấu hình endpoint (dùng nội bộ, có kiểm soát truy cập):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,scheduledtasks,metrics,prometheus
jobs:
  cleanup:
    fixed-delay-ms: 10000
```

Các endpoint ngoài `health` thường không tự được phơi bày qua HTTP. Trong môi trường thật, giới hạn chúng bằng xác thực, quyền và mạng nội bộ; đặc biệt `scheduledtasks` có thể lộ tên method hoặc thông điệp lỗi.

## 4. Ví dụ Spring Boot 3.5, Java 17: đếm success/failure và lưu lần chạy cuối

Ví dụ dưới đây chạy tác vụ `cleanup` mỗi 10 giây **sau khi lần trước kết thúc**, cố ý gây lỗi ở mỗi lượt thứ tư để kiểm tra đường thất bại. Các bộ đếm dùng Micrometer `Counter`; các thời điểm dùng `Gauge` đọc từ `AtomicLong`; snapshot kết quả dùng `AtomicReference` để đọc nhất quán trong một instance.

### Cấu trúc dự án

```text
scheduled-monitoring/
├── pom.xml
└── src/main/
    ├── java/com/example/jobs/
    │   ├── JobsApplication.java
    │   ├── CleanupJob.java
    │   └── JobStatusController.java
    └── resources/application.yml
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>scheduled-monitoring</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
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
```

`src/main/java/com/example/jobs/JobsApplication.java`:

```java
package com.example.jobs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobsApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobsApplication.class, args);
    }
}
```

`src/main/java/com/example/jobs/CleanupJob.java`:

```java
package com.example.jobs;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupJob {
    private static final Logger log = LoggerFactory.getLogger(CleanupJob.class);

    // Chỉ dùng để tạo lỗi có thể dự đoán trong ví dụ.
    private final AtomicInteger demoAttempt = new AtomicInteger();

    private final AtomicLong lastStarted = new AtomicLong();
    private final AtomicLong lastFinished = new AtomicLong();
    private final AtomicLong lastSucceeded = new AtomicLong();
    private final AtomicLong lastFailed = new AtomicLong();
    private final AtomicReference<RunSnapshot> lastRun = new AtomicReference<>();

    private final Counter successes;
    private final Counter failures;
    private final Timer duration;

    public CleanupJob(MeterRegistry registry) {
        successes = Counter.builder("app.scheduled.executions")
                .description("Completed executions of a scheduled job")
                .tags("job", "cleanup", "outcome", "success")
                .register(registry);

        failures = Counter.builder("app.scheduled.executions")
                .description("Completed executions of a scheduled job")
                .tags("job", "cleanup", "outcome", "failure")
                .register(registry);

        duration = Timer.builder("app.scheduled.duration")
                .description("Execution duration including failed runs")
                .tag("job", "cleanup")
                .register(registry);

        Gauge.builder("app.scheduled.last.started.epoch.seconds",
                        lastStarted, AtomicLong::doubleValue)
                .tag("job", "cleanup").register(registry);
        Gauge.builder("app.scheduled.last.finished.epoch.seconds",
                        lastFinished, AtomicLong::doubleValue)
                .tag("job", "cleanup").register(registry);
        Gauge.builder("app.scheduled.last.success.epoch.seconds",
                        lastSucceeded, AtomicLong::doubleValue)
                .tag("job", "cleanup").register(registry);
        Gauge.builder("app.scheduled.last.failure.epoch.seconds",
                        lastFailed, AtomicLong::doubleValue)
                .tag("job", "cleanup").register(registry);
    }

    @Scheduled(fixedDelayString = "${jobs.cleanup.fixed-delay-ms:10000}")
    public void scheduledCleanup() {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        lastStarted.set(startedAt.getEpochSecond());
        boolean success = false;

        try {
            performCleanup();
            successes.increment();  // Chỉ tăng sau khi nghiệp vụ hoàn tất
            success = true;
        } catch (RuntimeException ex) {
            failures.increment();
            log.error("job=cleanup outcome=failure", ex);
            throw ex;               // Giữ nguyên tín hiệu lỗi cho scheduler
        } finally {
            Instant finishedAt = Instant.now();
            duration.record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
            lastFinished.set(finishedAt.getEpochSecond());
            if (success) {
                lastSucceeded.set(finishedAt.getEpochSecond());
            } else {
                lastFailed.set(finishedAt.getEpochSecond());
            }
            String outcome = success ? "SUCCESS" : "ERROR";
            lastRun.set(new RunSnapshot(startedAt, finishedAt, outcome));
            log.info("job=cleanup outcome={} startedAt={} finishedAt={}",
                    outcome, startedAt, finishedAt);
        }
    }

    private void performCleanup() {
        int attempt = demoAttempt.incrementAndGet();
        if (attempt % 4 == 0) {
            throw new IllegalStateException("Simulated cleanup failure: " + attempt);
        }
        // Thay bằng công việc thật và quy tắc xác định thành công.
    }

    public RunSnapshot lastRun() {
        return lastRun.get();
    }

    public record RunSnapshot(Instant startedAt, Instant finishedAt, String outcome) {}
}
```

Ở đây `lastRun` là **lần hoàn tất gần nhất**. Khi đang có lượt mới chạy, nó vẫn giữ snapshot của lượt hoàn tất trước; còn gauge `last.started` đã ghi thời điểm bắt đầu lượt mới. Giá trị timestamp là **Unix epoch seconds (UTC)**; `0` nghĩa là chưa có dữ liệu từ lúc instance khởi động. `AtomicLong` được giữ làm field để Micrometer `Gauge` không mất đối tượng quan sát do garbage collection.

`src/main/java/com/example/jobs/JobStatusController.java` (để đọc snapshot trực tiếp trong môi trường thử nghiệm):

```java
package com.example.jobs;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobStatusController {
    private final CleanupJob cleanupJob;

    public JobStatusController(CleanupJob cleanupJob) {
        this.cleanupJob = cleanupJob;
    }

    @GetMapping("/internal/jobs/cleanup")
    public ResponseEntity<CleanupJob.RunSnapshot> latest() {
        CleanupJob.RunSnapshot snapshot = cleanupJob.lastRun();
        if (snapshot == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(snapshot);
    }
}
```

`src/main/resources/application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,scheduledtasks,metrics,prometheus
jobs:
  cleanup:
    fixed-delay-ms: 10000
```

Chạy từ thư mục chứa `pom.xml` (cần JDK 17+ và Maven):

```bash
mvn spring-boot:run
```

Sau khi ứng dụng chạy, kiểm tra:

```bash
curl http://localhost:8080/actuator/scheduledtasks
curl "http://localhost:8080/actuator/metrics/app.scheduled.executions?tag=job:cleanup&tag=outcome:success"
curl "http://localhost:8080/actuator/metrics/app.scheduled.executions?tag=job:cleanup&tag=outcome:failure"
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8080/internal/jobs/cleanup
```

Sau ba lượt hoàn tất, kỳ vọng `success=3` và `failure=0`; lượt thứ tư phát sinh `IllegalStateException` nên `success=3`, `failure=1` và snapshot gần nhất có `outcome="ERROR"`. Lượt thứ năm thành công thì `success=4`, `failure=1`. Thời điểm thực tế, tổng thời lượng và thứ tự log khởi động phụ thuộc lúc chạy; không có stdout cố định. Với cấu hình `@Scheduled` đồng bộ thông thường, ngoại lệ được Spring xử lý/log để những lượt định kỳ tiếp theo vẫn có thể được kích hoạt; nếu thay cơ chế lập lịch hoặc error handler, cần kiểm tra chính sách lỗi tương ứng.

### Vì sao các thành phần trên được chọn?

- **`Counter`**: chỉ tăng, phù hợp đo tổng số hoàn tất có `outcome=success/failure`. Giá trị trong process bắt đầu lại sau restart; khi tính tần suất nên dùng hàm `rate`/`increase` của hệ thống time-series.
- **`Timer`**: ghi số lượt và thời gian chạy (bao gồm các lượt lỗi vì đặt trong `finally`). Tránh nhầm `Timer.count()` không gắn outcome với số lần thành công.
- **`Gauge`**: cung cấp giá trị hiện tại của timestamp; thích hợp hiển thị “đã bao lâu từ lần thành công cuối”. Không dùng Counter để lưu một timestamp có thể thay đổi.
- **`AtomicReference<RunSnapshot>`**: cập nhật một object bất biến, giúp endpoint trả đúng cặp `startedAt`/`finishedAt`/`outcome` của **cùng một lần hoàn tất**. Việc đọc nhiều `AtomicLong` độc lập không bảo đảm chúng cùng thuộc một snapshot nếu có concurrency.
- **Log có cấu trúc**: cho biết nguyên nhân và stack trace khi lỗi. Metric cho biết mức độ; log cho biết chuyện gì xảy ra. Không gắn full message ngoại lệ, run ID hoặc user ID vào metric label vì số lượng nhãn sẽ bùng nổ.

Cấu hình demo chỉ phù hợp cho thực hành. Khi áp dụng thật, thay `performCleanup()` bằng nghiệp vụ của hệ thống và giữ một định nghĩa rõ ràng cho “thành công” (ví dụ transaction đã commit, file đã được lưu, downstream đã xác nhận). Nếu tác vụ chủ động bỏ qua do không đủ điều kiện, hãy thêm outcome `skipped` thay vì cộng nhầm success.

## 5. Đọc dashboard và thiết lập cảnh báo

Với Prometheus, tên Micrometer dấu chấm thường được chuẩn hóa thành dạng dấu gạch dưới; counter được xuất dạng `_total`. Kiểm tra tên thực tế trong `/actuator/prometheus` vì registry và cấu hình có thể thay đổi tên. Các truy vấn minh họa cho metric do ví dụ tự đăng ký:

```promql
# Số lượt thất bại của job cleanup trong 15 phút
sum(increase(app_scheduled_executions_total{job="cleanup",outcome="failure"}[15m]))
```

```promql
# Tỷ lệ lỗi trong 1 giờ; chỉ đọc khi mẫu số > 0
sum(increase(app_scheduled_executions_total{job="cleanup",outcome="failure"}[1h]))
/
clamp_min(sum(increase(app_scheduled_executions_total{job="cleanup"}[1h])), 1)
```

```promql
# Số giây kể từ thành công cuối cùng trên các instance còn đang được scrape
time() - max(app_scheduled_last_success_epoch_seconds{job="cleanup"})
```

Nếu chưa có thành công, gauge bằng `0` và phép trừ sẽ ra tuổi thời gian rất lớn: phải xử lý trạng thái `NEVER_RUN`, khoảng grace sau khởi động, và lịch dự kiến trước khi bật alert. Một cron chạy mỗi đêm không thể dùng cùng ngưỡng “quá 10 phút” của tác vụ mỗi phút. Có thể cảnh báo khi `lastSuccess` quá thời hạn SLA, khi `increase(failure[15m]) > 0` hoặc khi không có lượt chạy nào trong khoảng đáng lẽ phải có. Hãy tránh cảnh báo khi ứng dụng đang được bảo trì hoặc lịch được vô hiệu hóa có chủ đích.

Dashboard tối thiểu nên có: **số lần thành công**, **số lần thất bại**, **tỷ lệ lỗi theo cửa sổ thời gian**, **thời điểm hoàn tất gần nhất và outcome**, **thời điểm thành công gần nhất**, **thời lượng p95/p99 khi đủ dữ liệu**, cùng thông tin instance/cluster. Đếm lượt là số tăng dần từ khi process khởi động; để xem “hôm nay” hoặc “1 giờ qua”, tính chênh lệch trên chuỗi thời gian chứ không đọc giá trị counter thô.

## 6. Khi nào phải lưu lịch sử vào database?

`Counter`, `Gauge` và `AtomicReference` trong ví dụ phản ánh **instance đang chạy**. Sau restart, bộ đếm và snapshot trong bộ nhớ bắt đầu lại; khi có nhiều replica, mỗi replica giữ số liệu riêng. Vì vậy chúng không đáp ứng yêu cầu kiểm toán như “tổng số success toàn hệ thống trong 90 ngày” nếu không có nền tảng giám sát lưu dữ liệu phù hợp.

Với lịch sử cần truy vấn độc lập khỏi vòng đời process, lưu một bản ghi cho mỗi lượt trong bảng hoặc event store, chẳng hạn:

| Trường | Ý nghĩa |
| --- | --- |
| `run_id` | ID duy nhất để truy vết/idempotency |
| `job_name`, `instance_id` | Tên job, nơi thực thi |
| `scheduled_at`, `started_at`, `finished_at` | Mốc dự kiến, bắt đầu, hoàn tất (nếu có) |
| `status` | STARTED, SUCCESS, ERROR, SKIPPED… |
| `duration_ms`, `error_type` | Độ trễ và loại lỗi có kiểm soát |

Ghi `STARTED` lúc bắt đầu và chuyển sang trạng thái cuối khi đã có kết quả. Thiết kế thêm timeout/reconciliation cho lượt bị chết giữa chừng: process bị kill không chắc chạy được `finally` để lưu ERROR. Nếu job chỉ được chạy một lần trên toàn cluster, cần cơ chế khóa/leader/distributed scheduler riêng; chỉ thêm metric không ngăn được chạy trùng. Metric tổng hợp toàn cluster phải thống nhất cách cộng theo instance, tránh trùng do retry.

## 7. Các lỗi phổ biến cần tránh

1. **Cộng success trước khi hoàn tất:** đặt `success.increment()` ngay khi vào method khiến một lượt lỗi cũng được đếm thành công.
2. **Nuốt ngoại lệ:** `catch` rồi chỉ log hoặc trả về bình thường có thể làm status từ framework trông như SUCCESS. Nếu nghiệp vụ cho phép tự xử lý lỗi, phải phân biệt rõ outcome và đưa nó vào quan sát.
3. **Sai ranh giới tác vụ bất đồng bộ:** gọi `@Async`, tạo `CompletableFuture` hoặc subscribe reactive rồi return; phần `try/catch` bên ngoài chỉ đo thao tác kích hoạt, không đo kết quả thật. Đặt telemetry ở chỗ future/publisher hoàn tất; lỗi reactive `onError` không đi qua `catch` của runnable như ngoại lệ đồng bộ.
4. **Chỉ lưu lần bắt đầu gần nhất:** job có thể bị treo. Theo dõi cả `startedAt`, `finishedAt`, outcome và thời gian đang chạy; phát hiện lượt quá lâu hoặc không tiến triển.
5. **Dùng `LocalDateTime.now()` không nêu zone:** khó ghép metric nhiều máy. Lưu `Instant`/UTC; chuyển múi giờ ở giao diện.
6. **Tin counter bộ nhớ là lịch sử bền vững:** khi restart hoặc autoscale sẽ bị reset. Dùng time-series backend, database hoặc event log tùy yêu cầu giữ dữ liệu.
7. **Nhãn metric có độ phân biệt quá cao:** `run_id`, UUID, message lỗi, path động tạo hàng loạt time series. Dùng nhãn ổn định như `job`, `outcome`, `instance`; chi tiết nằm trong log/tracing.
8. **Quên trạng thái không chạy:** không thấy failure không có nghĩa là healthy. Đặt cảnh báo khi không có success trong khoảng dự kiến và phân biệt job disabled, bị kẹt, không nhận trigger.
9. **Đánh đồng phạm vi process với cluster:** hai replica có thể cùng chạy một `@Scheduled`; số đếm tăng hai lần nếu cùng làm một công việc. Quan sát và kiểm soát thực thi phân tán là hai bài toán liên quan nhưng khác nhau.
10. **Trộn trạng thái last-start với last-completed khi có chồng lấn:** xác định rõ lần “gần nhất” theo thời điểm bắt đầu hay kết thúc, và giữ snapshot nguyên tử cho từng lượt.

## 8. Bài tập kiểm chứng

1. Chạy ví dụ qua năm lượt và đối chiếu `success/failure`, snapshot từ endpoint, `last.success` và `last.failure`. Giải thích vì sao lần thứ tư lỗi nhưng lần thứ năm vẫn chạy.
2. Thay `fixedDelay` 10 giây bằng một lịch chậm hơn, sau đó đo ngưỡng cảnh báo “không có success”: ngưỡng phải tính theo lịch thực tế và thời gian chạy tối đa.
3. Thay phần giả lập bằng nghiệp vụ trả trạng thái “bỏ qua” và thiết kế thêm metric `outcome=skipped`; kiểm chứng mẫu số khi tính failure rate.
4. Khởi động lại ứng dụng. Ghi nhận trường hợp `lastRun` trống và counter reset; nêu cách lấy số liệu lịch sử từ Prometheus hoặc bảng `job_run` nếu yêu cầu lưu lâu dài.

**Tài liệu tham khảo chính thức**

- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework — Observability for scheduled tasks (cấu hình `ObservationRegistry` qua `ScheduledTaskRegistrar`): https://docs.spring.io/spring-framework/reference/integration/observability.html
- Spring Boot Actuator — Scheduled Tasks: https://docs.spring.io/spring-boot/3.5/api/rest/actuator/scheduledtasks.html
- Spring Boot Actuator — Metrics/Prometheus: https://docs.spring.io/spring-boot/3.5/reference/actuator/metrics.html
- Micrometer — Meters, Counters, Gauges, Timers: https://docs.micrometer.io/micrometer/reference/concepts/meters.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monitoring-tuning-006">Không đưa dữ liệu riêng của yêu cầu vào nhãn số liệu có quá nhiều giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Không đưa dữ liệu riêng của yêu cầu vào nhãn số liệu có quá nhiều giá trị

## 1. Vấn đề cần giải quyết

Trong ứng dụng Spring Framework xử lý đồng thời, một yêu cầu HTTP có thể tạo công việc chạy qua `@Async`, `TaskExecutor`, hàng đợi hoặc một tác vụ nền. Ta cần đo thời gian chạy, số lần thành công/thất bại, số tác vụ đang chờ và tình trạng của từng executor để phát hiện nghẽn, điều chỉnh kích thước pool và đặt cảnh báo.

Sai lầm thường gặp là gắn `requestId`, `userId`, `jobId`, `traceId`, URL chứa tham số, tên tệp hoặc nội dung đầu vào vào **nhãn số liệu** (metric tag/label). Các giá trị này thường thay đổi theo từng yêu cầu. Khi đó, hệ thống giám sát phải duy trì rất nhiều chuỗi thời gian khác nhau, dù câu hỏi vận hành thực sự chỉ là “loại công việc nào đang chậm hoặc lỗi?”.

**Nguyên tắc:** số liệu tổng hợp dùng nhãn có tập giá trị **nhỏ, có giới hạn và ổn định**; dữ liệu nhận dạng một yêu cầu cụ thể phục vụ điều tra từng trường hợp thì đặt ở log/trace có kiểm soát, không đưa vào metric label.

## 2. Cardinality là gì và vì sao đặc biệt quan trọng với concurrency?

Một time series thường được xác định bởi **tên metric cùng toàn bộ tập nhãn và giá trị nhãn** (hệ thống lưu trữ còn phân biệt nguồn scrape/instance). Ví dụ hai mẫu cùng tên `app.async.job.duration` nhưng khác `request.id` là hai series khác nhau.

- **Low cardinality:** `job.type=report|export|reconcile|other`, `outcome=success|error`, `executor=jobsExecutor` với tên bean được cấu hình cố định.
- **High cardinality:** `request.id=req-...`, `user.id=...`, `job.id=UUID`, `thread.name=jobs-57` nếu số luồng/tên thay đổi, `path=/orders/982731`, `exception.message=...`.
- **Unbounded cardinality:** số giá trị nhãn tăng theo dữ liệu người dùng hoặc theo thời gian, không có danh sách cho phép rõ ràng. Hash một ID vẫn có gần như từng giá trị khác nhau; cắt ngắn chuỗi không tạo giới hạn an toàn.

Ước lượng số tổ hợp nhãn tiềm năng bằng cách nhân số giá trị của các nhãn. Ví dụ `job.type` có 4 giá trị, `outcome` có 2 và `executor` có 3: nhiều nhất **4 × 2 × 3 = 24** tổ hợp ở mức nhãn này. Nếu thêm 50.000 `request.id` và các tổ hợp đều xảy ra thì có thể lên đến **1.200.000** tổ hợp, chưa kể instance, histogram bucket và các chuỗi liên quan. Số thực tế phụ thuộc dữ liệu xuất hiện; phép nhân này cho thấy vì sao một nhãn vô hạn có thể làm bùng nổ series.

Hậu quả: tăng bộ nhớ của `MeterRegistry` và backend, tăng lưu trữ/chi phí scrape, truy vấn/dashboard chậm, cảnh báo kém ổn định, đồng thời có nguy cơ lộ định danh người dùng trong hệ thống metrics. Với tác vụ bất đồng bộ nhận nhiều yêu cầu trong thời gian ngắn, churn của nhãn càng dễ tích lũy theo thời gian lưu dữ liệu. Điều chỉnh thread pool không giải quyết được nguyên nhân này.

## 3. Chọn nhãn theo câu hỏi vận hành

| Câu hỏi cần trả lời | Nhãn phù hợp | Không nên sử dụng |
| --- | --- | --- |
| Loại công việc nào chậm? | `job.type` từ enum/allowlist: `report`, `export`, `reconcile`, `other` | `jobId`, tên file xuất, tham số lọc do người dùng nhập |
| Executor nào bão hòa? | `executor` là tên bean/pool cấu hình cố định | tên task sinh động, request ID, thread ID riêng của mỗi lượt |
| Tác vụ có thất bại không? | `outcome=success|error` hoặc một nhóm nguyên nhân lỗi đã chuẩn hóa | exception message, stack trace, dữ liệu payload |
| Endpoint nào phát sinh nhiều công việc? | route mẫu `/orders/{id}`, loại thao tác cố định | URI đầy đủ, query string, `/orders/12345` |
| Nhóm khách hàng nào ảnh hưởng đến tải? | `plan=free|pro|enterprise|other` nếu thực sự cần | `customerId`, `email`, `tenantId` tự do |

Chỉ thêm nhãn nếu nó giúp phân nhóm, lọc hoặc cảnh báo. Mỗi nhãn phải có “ngân sách” số giá trị rõ ràng; nhiều nhãn hữu hạn vẫn có thể tạo quá nhiều tổ hợp. Giá trị mới hoặc không hợp lệ nên được ánh xạ vào `other`, không được tự động trở thành nhãn mới.

## 4. Nhận diện lỗi qua ví dụ trước và sau

**Không nên:** mỗi yêu cầu tạo một giá trị `request.id` và có thể tạo một meter mới.

```java
// Ví dụ chống chỉ định: requestId thay đổi ở mỗi yêu cầu.
registry.counter(
    "app.async.jobs.completed",
    "job.type", jobTypeFromRequest,
    "request.id", requestId
).increment();
```

Ở đây cả `jobTypeFromRequest` lẫn `requestId` đều có thể không được kiểm soát. Đổi thành metric name động, chẳng hạn `app.async.jobs.<jobId>`, cũng gây nổ cardinality dù không có nhãn.

**Nên:** chuẩn hóa loại công việc về một tập cố định, chỉ ghi kết quả tổng hợp. `requestId` vẫn có thể đi qua nghiệp vụ để đối chiếu log/trace theo chính sách bảo mật.

```java
String safeJobType = switch (jobTypeFromRequest) {
    case "report", "export", "reconcile" -> jobTypeFromRequest;
    default -> "other";
};

registry.counter(
    "app.async.jobs.completed",
    "job.type", safeJobType,
    "outcome", "success"
).increment();
```

Đoạn trên chỉ minh họa việc tạo counter sau khi công việc **đã hoàn tất thành công**. Trong thực tế, phải đặt lệnh tăng counter ở đúng nhánh hoàn tất; nếu muốn đo cả thất bại hãy dùng `outcome=error` với tập giá trị cố định. Không ghi thành công ngay khi vừa nhận yêu cầu hoặc vừa đưa task vào queue.

## 5. Ví dụ Java 17 + Spring Boot 3: đo tác vụ `@Async` an toàn

Ví dụ sau là tệp `SafeAsyncMetricsApplication.java` trong dự án Spring Boot 3.x có `spring-boot-starter-actuator` (Micrometer được cấu hình cùng ứng dụng). `ApplicationRunner` gọi service thông qua Spring bean để `@Async` thực sự qua proxy. Chương trình cố tình dùng hai tác vụ đơn giản để làm rõ cơ chế đo; nghiệp vụ thực tế có thể phức tạp hơn nhưng không làm đổi thiết kế tag.

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@SpringBootApplication
@EnableAsync
public class SafeAsyncMetricsApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafeAsyncMetricsApplication.class, args);
    }

    @Bean(name = "jobsExecutor")
    ThreadPoolTaskExecutor jobsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("jobs-");
        return executor; // Spring khởi tạo vòng đời bean.
    }

    @Bean
    ApplicationRunner demo(AsyncWorkService service) {
        return args -> {
            service.runAsync("report", "req-001",
                () -> System.out.println("report done")).join();
            service.runAsync("export", "req-002",
                () -> System.out.println("export done")).join();
        };
    }
}

enum JobType {
    REPORT("report"), EXPORT("export"), RECONCILE("reconcile"), OTHER("other");

    private final String metricValue;

    JobType(String metricValue) {
        this.metricValue = metricValue;
    }

    String metricValue() {
        return metricValue;
    }

    static JobType fromExternal(String raw) {
        if (raw == null) {
            return OTHER;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "report" -> REPORT;
            case "export" -> EXPORT;
            case "reconcile" -> RECONCILE;
            default -> OTHER;
        };
    }
}

@Service
class AsyncWorkService {
    private final MeterRegistry registry;

    AsyncWorkService(MeterRegistry registry) {
        this.registry = registry;
    }

    @Async("jobsExecutor")
    public CompletableFuture<Void> runAsync(
            String rawJobType, String requestId, Runnable work) {
        JobType jobType = JobType.fromExternal(rawJobType);
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";

        try {
            if (requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException("requestId is required");
            }
            Objects.requireNonNull(work, "work").run();
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException ex) {
            outcome = "error";
            throw ex;
        } finally {
            Timer timer = Timer.builder("app.async.job.duration")
                .description("Time spent executing an asynchronous job")
                .tag("job.type", jobType.metricValue())
                .tag("outcome", outcome)
                .register(registry);
            sample.stop(timer);
        }
    }
}
```

Chạy bằng `./mvnw spring-boot:run` (hoặc `mvnw.cmd spring-boot:run` trên Windows) khi các dependency Spring Boot/Actuator đã có. Hai dòng do các lambda in ra, **không tính log khởi động Spring Boot**, theo đúng thứ tự vì mỗi lệnh `join()` hoàn tất trước khi gọi lệnh tiếp theo:

```text
report done
export done
```

Ý nghĩa quan trọng:

1. `requestId` được truyền vào tác vụ nhưng **không phải metric tag**. Với 10.000 ID khác nhau, số loại meter cho metric này vẫn phụ thuộc `job.type` (tối đa 4) và `outcome` (2), tức tối đa **8 tổ hợp** từ hai nhãn đó, nếu ứng dụng chỉ dùng đúng đoạn mã này.
2. `job.type` được chuẩn hóa **trước** khi đăng ký meter. Dù đầu vào là `report`, `EXPORT` hay một tên tự do, giá trị metric luôn thuộc tập xác định.
3. `Timer.Sample` bắt đầu **bên trong** phương thức `@Async`, nên ghi thời gian chạy phần thân phương thức. Nó **không tự đo** thời gian task nằm trong queue trước khi bắt đầu chạy, cũng không ghi sự kiện executor từ chối task trước khi phương thức được gọi.
4. Nhánh `finally` ghi thời gian cho cả thành công lẫn lỗi `RuntimeException`; với lỗi thực tế phải giữ nguyên cơ chế xử lý exception của ứng dụng. Vì phương thức trả `CompletableFuture`, caller có thể quan sát lỗi khi hoàn tất future.
5. `@Async` mặc định dựa trên proxy: gọi một phương thức `@Async` từ chính đối tượng đó (`this.runAsync(...)`) thường không qua advice bất đồng bộ. Ví dụ dùng một bean khác gọi service qua proxy để tránh hiểu sai việc đo.

Trong ứng dụng Spring Boot có Actuator, các bean `ThreadPoolTaskExecutor` và `ThreadPoolTaskScheduler` phù hợp có thể được tự động instrument với nhãn dựa trên **tên bean executor/scheduler**, có giới hạn. Dùng các số liệu active threads, pool size, queue depth và task duration theo khả năng backend để kiểm tra nghẽn; không thêm ID từng request vào các nhãn executor.

## 6. Phân biệt metrics, logs và traces

Micrometer Observation phân biệt:

- **Low-cardinality key-values**: dùng làm nhãn metrics theo handler mặc định, ví dụ `job.type=export`.
- **High-cardinality key-values**: có thể dùng trong tracing, ví dụ request ID, và **không được** `DefaultMeterObservationHandler` đưa thành metric tags.

Ví dụ ý tưởng khi dùng Observation thay cho Timer thủ công:

```java
Observation observation = Observation.createNotStarted(
        "app.async.job", observationRegistry)
    .lowCardinalityKeyValue("job.type", jobType.metricValue())
    .highCardinalityKeyValue("request.id", requestId);

observation.observe(work::run);
```

Đoạn này là **trích đoạn tích hợp**: cần `io.micrometer.observation.Observation`, biến `observationRegistry`, `jobType`, `requestId`, `work` từ ngữ cảnh ứng dụng, không phải một tệp Java chạy độc lập. Khi chọn Observation để ghi thời gian, tránh instrument thêm Timer trùng cho chính cùng thao tác.

High-cardinality trong trace **không đồng nghĩa** với tự do đưa PII/bí mật vào mọi hệ thống. Cần áp dụng quy định bảo mật, quyền truy cập, sampling và thời gian lưu. Khi có custom `ObservationHandler`, convention hoặc bridge tự phát triển, kiểm tra lại việc chuyển key-values để bảo đảm request ID không vô tình chảy vào metrics. Với log, sử dụng ID tương quan phù hợp chính sách; không đưa payload nhạy cảm.

## 7. Phòng vệ, kiểm tra và theo dõi

**Thiết kế trước khi ghi metric:**

1. Liệt kê toàn bộ tag keys của từng meter; với mỗi key ghi rõ nguồn giá trị, danh sách giá trị hợp lệ, số lượng tối đa, owner và câu hỏi dashboard mà nó phục vụ.
2. Chuẩn hóa đầu vào bằng enum/allowlist, route template, bucket hoặc nhóm lỗi cố định; mọi đầu vào bất ngờ về `other`. **Không** chỉ cắt chuỗi hoặc hash giá trị tự do.
3. Giữ tên metric và tên tag cố định trong code. Không nối dữ liệu yêu cầu vào tên metric, tên tag hoặc tên executor.
4. Phân biệt thời điểm `submitted`, `started`, `completed`, `rejected` nếu dashboard cần quan sát queue: timer trong task chỉ đo một phần vòng đời.
5. Đặt giới hạn khẩn cấp bằng `MeterFilter` nếu thích hợp; ví dụ bean dưới đây giới hạn tối đa 4 giá trị `job.type` cho các meter có tên bắt đầu bằng `app.async.job.duration`:

```java
@Bean
MeterFilter protectJobTypes() {
    return MeterFilter.maximumAllowableTags(
        "app.async.job.duration",
        "job.type",
        4,
        MeterFilter.deny()
    );
}
```

Với đoạn cấu hình trên, import `io.micrometer.core.instrument.config.MeterFilter` và `org.springframework.context.annotation.Bean` trong lớp `@Configuration`. Đây là **cầu chì cuối cùng**: khi vượt ngưỡng, meter mới liên quan có thể bị từ chối, làm thiếu dữ liệu; cần ghi nhận và cảnh báo việc vượt ngưỡng. Không dùng giới hạn này thay cho bước chuẩn hóa.

**Kiểm chứng trước khi phát hành:** gửi 1.000–10.000 yêu cầu có `requestId` khác nhau nhưng chỉ một vài `job.type`; xác nhận số meter/series của metric nghiệp vụ không tăng theo số ID. Lặp lại với giá trị job type lạ (`other`), nhánh lỗi (`error`) và nhiều executor thực tế. Kiểm tra đồng thời log/trace vẫn đối chiếu được sự cố theo chính sách truy cập. Micrometer có `HighCardinalityTagsDetector` để phát hiện số meter bất thường cùng tên, nhưng nó không thay thế việc rà soát tên metric động.

Nếu dùng Prometheus và xuất Timer ở dạng chuẩn, một truy vấn tổng hợp hữu ích là:

```promql
sum by (job_type, outcome) (
  rate(app_async_job_duration_seconds_count[5m])
)
```

Tên metric và tag sau khi export có thể được chuẩn hóa theo registry/version; đối chiếu tên thực tế trên `/actuator/prometheus` trước khi sao chép truy vấn. Có thể dùng `count(app_async_job_duration_seconds_count)` để quan sát số series hiện có của meter tương ứng theo cấu hình scrape. Hãy cảnh báo khi series tăng đột ngột hoặc xuất hiện tag value ngoài allowlist, đồng thời theo dõi sức khỏe executor và độ trễ nghiệp vụ.

## 8. Những lỗi dễ tái phạm

- **“Cần drill-down từng yêu cầu, nên thêm request ID vào metric.”** Metric được thiết kế để tổng hợp; dùng exemplar/trace link hoặc truy vết log theo ID để drill-down.
- **“Hash email/user ID thì đã an toàn.”** Hash không tạo tập giá trị hữu hạn và vẫn có thể nhạy cảm; cardinality gần như giữ nguyên.
- **“Tên job type do người dùng nhập là nhóm hợp lý.”** Chỉ đúng nếu chuẩn hóa về allowlist; nếu nhận giá trị tự do, `job.type` vẫn là high cardinality.
- **“Chỉ có vài thread nên nhãn tên thread an toàn.”** Tên thread có thể thay đổi khi pool tăng/giảm và khởi tạo lại; khi cần giám sát pool, dùng tên bean/pool cố định.
- **“Một nhãn chỉ có 20 giá trị là ít.”** Nhiều nhãn cùng có 20 giá trị có thể tạo tổ hợp rất lớn; đánh giá cardinality của **toàn bộ tập tag**, số instance và histogram.
- **“Đã cài MeterFilter nên không cần xem lại metric.”** Bộ lọc có thể làm mất chuỗi đo; vẫn phải thiết kế tag, theo dõi số meter và rà soát dashboard.

## 9. Checklist áp dụng cho bài toán concurrency

Trước khi thêm metric cho `@Async`, `TaskExecutor` hoặc tác vụ lập lịch, hãy tự kiểm tra: metric đó giúp quyết định điều chỉnh gì (pool size, queue, timeout, retry, lỗi)? Tag nào là enum/allowlist có giới hạn? ID request/job/user có bị đưa vào tên metric, label hoặc thuộc tính dùng làm label qua custom handler không? Số tổ hợp có chấp nhận được khi nhân với instance và các chuỗi histogram không? Đã thử tải với nhiều request ID khác nhau và đo số series chưa?

**Tiêu chí hoàn thành:** vẫn phân tích được tải và lỗi theo loại công việc, kết quả và executor; số lượng metric series không tăng tuyến tính với số yêu cầu hay số định danh duy nhất. Khi cần lần theo một tác vụ cụ thể, tìm nó trong log/trace có kiểm soát.

### Tài liệu tham khảo

- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework — Observability Support: https://docs.spring.io/spring-framework/reference/integration/observability.html
- Micrometer — High Cardinality Tags Detector: https://docs.micrometer.io/micrometer/reference/concepts/high-cardinality-tags-detector.html
- Micrometer — Observation Components: https://docs.micrometer.io/micrometer/reference/observation/components.html
- Micrometer — Meter Filters: https://docs.micrometer.io/micrometer/reference/concepts/meter-filters.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monitoring-tuning-007">Dùng thread dump và công cụ JVM để tìm chờ khóa hoặc cạn nhóm luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng thread dump và công cụ JVM để tìm chờ khóa hoặc cạn nhóm luồng

## 1. Mục tiêu và bối cảnh

Trong ứng dụng Spring Framework, HTTP request, tác vụ `@Async` hoặc `@Scheduled` có thể chậm dù CPU không cao. Cần phân biệt:

- **Chờ khóa (lock contention):** nhiều luồng cạnh tranh cùng `synchronized` monitor hoặc `Lock`; một luồng giữ khóa quá lâu khiến luồng khác chờ. **Deadlock** là trường hợp đặc biệt tạo thành vòng chờ khóa không thể tự tháo gỡ.
- **Cạn năng lực thread pool (pool saturation/starvation):** các worker đang bận, hàng đợi tăng và task không được xử lý kịp. Worker có thể bận vì CPU, I/O, khóa, `Future` hoặc gọi dịch vụ khác. Pool đầy không đồng nghĩa JVM hết khả năng tạo native thread.

Mục tiêu là xác định **luồng nào chờ, chờ tài nguyên gì, ai giữ tài nguyên, tình trạng kéo dài bao lâu, pool nào bị ảnh hưởng**, rồi đối chiếu với latency, queue và rejection để chọn cách sửa.

## 2. Những dấu hiệu không được nhầm lẫn

| Trạng thái/dấu hiệu | Cách hiểu đúng | Cần kiểm tra tiếp |
| --- | --- | --- |
| `BLOCKED (on object monitor)` | Chờ vào khối/phương thức `synchronized` hoặc tái chiếm monitor | Monitor ID, thread đang giữ monitor, stack của owner |
| `WAITING (parking)` | Đợi vô thời hạn qua park; có thể từ `ReentrantLock`, `Future.get()`, `CountDownLatch`, queue | Frame `LockSupport` / `AbstractQueuedSynchronizer` và mã ứng dụng |
| `TIMED_WAITING` | Ngủ/đợi có thời hạn; có thể hoàn toàn bình thường | `sleep`, timed lock/poll, timeout |
| `RUNNABLE` | JVM xem là có thể chạy, kể cả một số thao tác native I/O | Stack, CPU theo thread, I/O và nhiều snapshot |
| `ThreadPoolExecutor.getTask` / `BlockingQueue.take` | Worker thường **rảnh**, đang đợi công việc mới | Không kết luận pool đầy chỉ vì thấy `WAITING` |
| Active sát max, queue tăng, task reject | Bằng chứng mạnh về pool bão hòa khi kéo dài và đúng executor | Rate submit/complete, stack worker, latency |
| `OutOfMemoryError: unable to create native thread` | Có thể hết native memory/giới hạn thread của OS/container | Tổng số thread và giới hạn process; khác queue đầy |

**Contention khác deadlock:** chờ khóa vài mili giây có thể bình thường. Deadlock cần vòng phụ thuộc: A giữ L1 chờ L2, B giữ L2 chờ L1. Nhiều `BLOCKED` hoặc chờ lâu chưa tự chứng minh deadlock.

**Starvation khác deadlock:** pool có hai worker; cả hai gọi `Future.get()` để chờ task con được gửi vào **chính pool đó**. Task con nằm trong queue, không còn worker trống. Không cần vòng `synchronized`, hệ thống vẫn có thể ngừng tiến triển.

## 3. Xác định đúng pool trong Spring

- `ThreadPoolTaskExecutor` thường xử lý `@Async` hoặc task do ứng dụng submit. Nên đặt tên riêng, ví dụ `report-async-`.
- `ThreadPoolTaskScheduler` phục vụ công việc lập lịch; phải xem cấu hình và thread riêng.
- Tomcat/Jetty/Undertow có pool xử lý HTTP request riêng; không mặc định coi thread HTTP là worker `@Async`.
- WebFlux/Reactor dùng event loop/scheduler với nguyên tắc khác Servlet; blocking event loop là vấn đề khác queue executor đầy.
- `@Async` thường đi qua Spring proxy: self-invocation trên cùng object không tự kích hoạt async qua proxy. Hãy xác nhận executor thực sự được gọi.

```text
HTTP request / @Scheduled / consumer
             |
             v
      submit task ------> queue (số lượng, thời gian chờ)
             |                    |
             v                    v
     executor worker ------> lock / I/O / Future / CPU
             |
             v
       task completion
```

Thread dump cho biết **worker đang làm gì khi chụp**. Nó thường không cho biết trực tiếp số task chưa bắt đầu trong queue. Muốn tìm pool cạn phải thêm thống kê executor và chỉ số ứng dụng.

## 4. Quy trình lấy bằng chứng trên JVM

### Bước 1 — Đúng tiến trình, đúng thời điểm

Ghi timestamp, instance/pod, endpoint/job chậm, response latency, throughput, timeout và `TaskRejectedException`. Trên máy có JDK cùng quyền truy cập JVM đích:

```bash
jcmd -l
jps -l
jcmd 12345 VM.command_line
jcmd 12345 help Thread.print
```

Thay `12345` bằng PID thực tế. Dùng đúng host/container/PID namespace và tài khoản có quyền attach. Nếu JVM nằm trong container khác, không thấy PID từ host chưa chứng minh tiến trình không tồn tại.

### Bước 2 — Chụp nhiều thread dump

```bash
jcmd 12345 Thread.print -l > dump-1.txt
jcmd 12345 Thread.print -l > dump-2.txt
jcmd 12345 Thread.print -l > dump-3.txt
```

Chạy lần lượt, **cách nhau 5–10 giây khi sự cố còn diễn ra**, và ghi thời điểm từng dump. Với HotSpot/JDK hỗ trợ, `jcmd 12345 Thread.print -e -l` thêm thông tin mở rộng; có thể dùng `jstack -l 12345` khi cần phương án thay thế.

Một dump chỉ là ảnh tức thời. Cùng stack/lock xuất hiện ở nhiều dump, đồng thời latency hoặc queue tăng, đáng tin hơn một trạng thái `BLOCKED` đơn lẻ. Lấy dump có chi phí quan sát, nhất là khi rất nhiều thread; tránh lặp vô hạn. File dump có thể chứa thông tin hệ thống, nên lưu trữ và chia sẻ theo quy định vận hành.

### Bước 3 — Dùng Java Flight Recorder (JFR) để thấy diễn tiến

```bash
jcmd 12345 JFR.start name=concurrency-diagnosis settings=profile duration=60s filename=concurrency-diagnosis.jfr
jfr summary concurrency-diagnosis.jfr
jfr print --events jdk.JavaMonitorEnter,jdk.ThreadPark --stack-depth 20 concurrency-diagnosis.jfr
```

File `.jfr` do JVM tạo tại đường dẫn nó truy cập được; đọc file sau khi recording kết thúc. `jdk.JavaMonitorEnter` giúp tìm contention trên monitor; `jdk.ThreadPark` giúp khảo sát park. Sự kiện được thu thập tùy cấu hình/threshold/khoảng thời gian, nên **không có event không có nghĩa không có nghẽn**. JDK Mission Control cho phép khám phá JFR bằng giao diện và nhóm sự kiện theo stack/thời gian.

## 5. Đọc thread dump để tìm chờ khóa

Với `BLOCKED`, tìm frame ứng dụng và dòng `waiting to lock <...>`. Tìm **chính monitor ID đó** ở dòng `locked <...>` của thread khác.

Ví dụ rút gọn minh họa cấu trúc; tên thread, địa chỉ và số dòng trong dump thực tế sẽ khác:

```text
"demo-lock-waiter" ... BLOCKED (on object monitor)
    at ThreadDumpLab.lambda$showLockWait$1(ThreadDumpLab.java:...)
    - waiting to lock <0x00000000abcd1234> (a java.lang.Object)

"demo-lock-owner" ... TIMED_WAITING (sleeping)
    at java.lang.Thread.sleep(java.base@.../Native Method)
    at ThreadDumpLab.lambda$showLockWait$0(ThreadDumpLab.java:...)
    - locked <0x00000000abcd1234> (a java.lang.Object)
```

Waiter chờ đúng monitor owner đang giữ. Owner `sleep` **bên trong** `synchronized` nên vẫn giữ khóa trong lúc ngủ. Trong ứng dụng thật, truy theo frame owner đến mã xử lý: có I/O, DB, gọi API, logic nặng hoặc nested lock trong critical section không?

Với `ReentrantLock`, waiter thường có thể ở `WAITING (parking)` tại `LockSupport.park`/`AbstractQueuedSynchronizer`, không hiện `BLOCKED`. Tham khảo phần synchronizer/owner của `-l` nếu JVM cung cấp; chỉ lọc từ khóa `BLOCKED` sẽ bỏ sót trường hợp này.

**Cách kiểm chứng:**

1. Xác định waiter: tên, ID thread, state, frame ứng dụng.
2. Xác định tài nguyên: monitor ID, synchronizer, `Future`, semaphore, connection pool hay I/O.
3. Xác định owner/nguyên nhân: ai giữ khóa; owner đang làm gì.
4. So sánh 2–3 dump: cùng waiter/owner có kéo dài hay thay đổi?
5. Đối chiếu timestamp với latency, queue, timeout và đường đi request/job.

### Khi nào được gọi là deadlock?

Thread A giữ L1 và chờ L2, thread B giữ L2 và chờ L1: đây là vòng khóa. Dump HotSpot đôi khi có phần `Found one Java-level deadlock`. `ThreadMXBean.findDeadlockedThreads()` có thể phát hiện vòng chờ monitor/ownable synchronizer của **platform threads**, nhưng không phát hiện mọi kiểu starvation hoặc dependency liên quan virtual threads. Không thấy deadlock qua API đó không loại trừ nghẽn do queue, I/O hoặc `Future`.

## 6. Tìm pool cạn: cần dump **và** metrics executor

Đừng dựa vào số thread có chữ `WAITING` để kết luận hết worker. Quan sát ít nhất:

| Số đo | Ý nghĩa |
| --- | --- |
| `poolSize`, `corePoolSize`, `maximumPoolSize` | Worker hiện tại và giới hạn cấu hình |
| `activeCount` | Worker đang thực thi task; cần xem xu hướng |
| `queue.size()` và queue capacity | Backlog có tăng/đụng trần không |
| `completedTaskCount` / throughput hoàn tất | Worker có giải phóng công việc đều không |
| Rejected task/`TaskRejectedException` | Năng lực thực thi bị vượt quá không |
| Queue wait time, task runtime, request latency | Độ trễ phát sinh trước hay trong thực thi |
| Stack worker active | Chờ lock, DB/socket I/O, `Future`, CPU hoặc sleep |

Với bean `ThreadPoolTaskExecutor` **đã khởi tạo**, có thể lấy delegate `ThreadPoolExecutor` để lấy số đo:

```java
// Đoạn tích hợp trong ứng dụng Spring, không phải chương trình độc lập.
ThreadPoolExecutor pool = taskExecutor.getThreadPoolExecutor();
System.out.printf(
    "active=%d pool=%d max=%d queued=%d completed=%d%n",
    pool.getActiveCount(),
    pool.getPoolSize(),
    pool.getMaximumPoolSize(),
    pool.getQueue().size(),
    pool.getCompletedTaskCount()
);
```

`taskExecutor` là bean được inject trong ứng dụng. Khi vận hành, xuất metrics cùng timestamp (chẳng hạn qua Micrometer nếu hệ thống đã tích hợp), bao gồm cả rejection. Một lần lấy số chưa đủ để kết luận.

### Cơ chế queue gây hiểu nhầm

Với `ThreadPoolExecutor` thường được `ThreadPoolTaskExecutor` bọc:

1. Dưới `corePoolSize`, executor tạo worker để thực thi task mới.
2. Đạt core thì task mới **ưu tiên vào queue** nếu queue còn chỗ.
3. Chỉ khi queue không nhận thêm task, số worker mới tăng tối đa đến `maxPoolSize`.
4. Worker và queue cùng đạt trần thì áp dụng rejection policy.

Đặt `maxPoolSize=64` nhưng để queue không giới hạn có thể làm pool chỉ quanh `corePoolSize` trong khi backlog tăng. `ThreadPoolTaskExecutor` mặc định có core size 1 và max/queue capacity rất lớn. Không dùng mặc định đó làm giả định về thông lượng ứng dụng.

Ví dụ cấu hình pool có tên dễ nhận diện:

```java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncPoolConfig {
    @Bean("diagnosticExecutor")
    public ThreadPoolTaskExecutor diagnosticExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("diag-async-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
```

Đây là class dành cho **dự án đã có Spring dependencies**, không biên dịch độc lập chỉ bằng JDK. Gắn `@Async("diagnosticExecutor")` ở phương thức thích hợp. Các giá trị 4/8/32 là **minh họa**, không phải sizing khuyến nghị chung. Với queue 32, pool thường chỉ mở rộng vượt core 4 khi queue đã đầy; đến max 8 và queue đầy thì task bị từ chối (trong API Spring có thể là `TaskRejectedException`). Cần đo tải CPU/I/O, giới hạn downstream, timeout và backpressure trước khi chọn cấu hình.

## 7. Thí nghiệm Java 17+ có thể chạy

Lưu tệp `ThreadDumpLab.java`. Chế độ `lock` tạo một owner giữ monitor trong 60 giây và một waiter bị `BLOCKED`. Chế độ `pool` tạo hai worker đang bận, queue chứa hai task và task thứ năm bị reject.

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadDumpLab {
    private static final long HOLD_MILLIS = 60_000L;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java ThreadDumpLab lock|pool");
            return;
        }
        switch (args[0]) {
            case "lock" -> showLockWait();
            case "pool" -> showPoolSaturation();
            default -> System.err.println("Choose lock or pool");
        }
    }

    private static void showLockWait() throws InterruptedException {
        Object monitor = new Object();
        CountDownLatch ownerEntered = new CountDownLatch(1);

        Thread owner = new Thread(() -> {
            synchronized (monitor) {
                ownerEntered.countDown();
                pause(HOLD_MILLIS); // Giữ monitor khi sleep.
            }
        }, "demo-lock-owner");

        Thread waiter = new Thread(() -> {
            synchronized (monitor) {
                System.out.println("waiter acquired monitor");
            }
        }, "demo-lock-waiter");

        owner.start();
        ownerEntered.await();
        waiter.start();
        System.out.println("Inspect demo-lock-owner and demo-lock-waiter now");
        owner.join();
        waiter.join();
    }

    private static void showPoolSaturation() throws InterruptedException {
        AtomicInteger sequence = new AtomicInteger();
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        CountDownLatch twoStarted = new CountDownLatch(2);

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2),
                runnable -> new Thread(runnable, "lab-pool-" + sequence.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());

        int rejected = 0;
        try {
            for (int i = 0; i < 5; i++) {
                try {
                    pool.execute(() -> {
                        twoStarted.countDown();
                        try {
                            releaseWorkers.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                } catch (RejectedExecutionException expected) {
                    rejected++;
                }
            }

            twoStarted.await();
            System.out.printf("active=%d queued=%d rejected=%d%n",
                    pool.getActiveCount(), pool.getQueue().size(), rejected);
            Thread.sleep(HOLD_MILLIS); // Cửa sổ để chụp dump.
        } finally {
            releaseWorkers.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

Biên dịch và chạy **một chế độ mỗi lần** tại terminal thứ nhất:

```bash
javac ThreadDumpLab.java
java ThreadDumpLab lock
```

Trong 60 giây, mở terminal thứ hai:

```bash
jcmd -l
jcmd 12345 Thread.print -l
```

Đổi PID thành tiến trình `ThreadDumpLab`. Tìm `demo-lock-waiter`: `BLOCKED`, monitor đang chờ; và `demo-lock-owner` đang giữ cùng monitor. Sau 60 giây, owner thả khóa, waiter in:

```text
waiter acquired monitor
```

Chạy lại `java ThreadDumpLab pool`. Sau khi hai worker bắt đầu, dòng số liệu tại thời điểm in là:

```text
active=2 queued=2 rejected=1
```

Trong dump, `lab-pool-1`/`lab-pool-2` có thể ở `WAITING (parking)` do `CountDownLatch.await()`, nhưng **cả hai đang bận với task**. Hai task còn trong queue chưa có worker stack riêng. Kết hợp `queued=2` và `rejected=1` mới chứng minh saturation. Sau 60 giây latch được mở để task kết thúc, không tạo deadlock vĩnh viễn. Thứ tự thông báo giữa thread có thể thay đổi; không lấy số liệu mô phỏng để quyết định sizing production.

## 8. Phân loại nguyên nhân và hướng xử lý

| Bằng chứng ở nhiều lần quan sát | Khả năng | Hành động kiểm chứng |
| --- | --- | --- |
| Nhiều `BLOCKED` cùng monitor, owner giữ lâu | Lock contention | Thu hẹp `synchronized`, tránh I/O/remote call trong critical section |
| Chuỗi thread giữ/chờ khóa thành vòng | Deadlock | Cố định thứ tự lấy khóa, loại nested lock/callback lấy khóa ngược |
| Worker ở `Future.get()`/`join()` chờ task con nằm chính pool đó | Dependency starvation | Tránh chờ đồng bộ, phân tách workload, thiết kế lại chuỗi task |
| Active sát max, queue/rejection/latency tăng; worker xử lý I/O hoặc tác vụ nặng | Pool saturation | Giảm thời gian task, timeout, backpressure, load test sizing |
| Worker đợi `BlockingQueue.take`, queue gần 0, không reject | Pool có worker rảnh | Tìm bottleneck khác trước khi tăng pool |
| `unable to create native thread` và tổng thread tăng bất thường | Native thread/resource exhaustion | Kiểm tra thread leak, OS/container/process limits, native memory |

Tăng `maxPoolSize` khi chưa biết worker kẹt ở đâu có thể làm áp lực DB, khóa chung hoặc downstream tệ hơn. Queue quá lớn có thể che quá tải bằng latency tăng. Ưu tiên khắc phục nguyên nhân được chứng minh rồi mới tuning.

### Checklist hiện trường

1. Khớp **đúng JVM, thời điểm, pool và tên thread** với triệu chứng.
2. Lấy 2–3 dump cách vài giây; so sánh cùng PID/instance.
3. Với lock: waiter → lock ID → owner → stack ứng dụng → thời gian chờ.
4. Với pool: ghi `active/pool/max/queued/completed/rejected` cùng timestamp.
5. Dùng JFR nếu cần biết contention/park kéo dài bao lâu.
6. Phân biệt contention, deadlock, starvation, saturation, I/O và native thread exhaustion.
7. Sửa có mục tiêu và kiểm thử với tải tương đương; đối chiếu latency/queue/reject/dump trước–sau.

## 9. Sai lầm thường gặp

- Kết luận mọi `WAITING` là deadlock hoặc pool đầy; worker ở `BlockingQueue.take` có thể rảnh.
- Đưa ra kết luận từ một snapshot; không chứng minh thời gian chờ.
- Chỉ tìm `BLOCKED` và bỏ sót `ReentrantLock`/`Future` thể hiện qua `WAITING (parking)`.
- Coi task còn trong queue là đã có stack thread; task chưa chạy chưa có worker riêng.
- Tăng pool `@Async` trong khi nghẽn thật nằm ở Servlet, DB pool hoặc scheduler.
- Tưởng `maxPoolSize` luôn mở rộng dù queue không giới hạn.
- Nhầm reject/queue đầy với lỗi tạo native thread.
- Quên trường hợp JDK 21 virtual threads: công cụ dump/MXBean cổ điển có thể không hiển thị đầy đủ như platform threads; kiểm tra hỗ trợ theo JDK, dùng JFR/công cụ phù hợp khi liên quan.
- Chia sẻ dump chứa dữ liệu vận hành hoặc tên hệ thống không được phép.

## 10. Tài liệu chính thức

- [Oracle JDK 21 — `jcmd` và `Thread.print`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)
- [Oracle JDK 21 — `jstack`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jstack.html)
- [Oracle JDK 21 — `jfr`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — `ThreadPoolTaskExecutor` API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)

Một chẩn đoán có giá trị phải nối **stack waiter** với **owner khóa hoặc việc worker đang thực hiện**, rồi xác minh tác động qua **thời gian, queue, tốc độ hoàn tất và rejection**. Chỉ sau đó mới điều chỉnh khóa, task dependency, timeout hoặc pool.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monitoring-tuning-008">Đối chiếu thay đổi cấu hình với độ trễ, công suất và tài nguyên đích</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu thay đổi cấu hình với độ trễ, công suất và tài nguyên đích

## 1. Mục tiêu và phạm vi

Trong ứng dụng Spring Framework xử lý tác vụ bất đồng bộ hoặc theo lịch, một thay đổi như tăng số luồng, giảm độ dài hàng đợi hay đổi chính sách từ chối có thể làm **độ trễ giảm nhưng CPU tăng**, hoặc làm **thông lượng tăng nhưng số lỗi/timeout tăng**. Vì vậy, không đánh giá cấu hình bằng một chỉ số đơn lẻ.

Sau bài này, bạn có thể:

- Xác định cấu hình cần so sánh và đặt mục tiêu đo được cho độ trễ, công suất xử lý và tài nguyên.
- Đọc đúng cơ chế của `ThreadPoolTaskExecutor` để giải thích vì sao thay đổi `corePoolSize`, `maxPoolSize`, `queueCapacity` có hoặc không có tác dụng.
- Thiết kế phép đo trước–sau công bằng, diễn giải bảng kết quả và quyết định giữ hoặc hoàn tác cấu hình.
- Phân biệt nghẽn ở hàng đợi executor với nghẽn CPU, cơ sở dữ liệu, mạng hoặc hệ thống phụ thuộc.

**Ranh giới:** Tập trung vào `@Async`, `TaskExecutor` và nơi cần thiết là `TaskScheduler` trong Spring; bài không giả định tăng thread luôn tốt hoặc coi cấu hình thread pool là cách chữa mọi lỗi hiệu năng.

## 2. Ba nhóm mục tiêu phải xét cùng nhau

| Nhóm | Đo cái gì | Câu hỏi cần trả lời |
| --- | --- | --- |
| Độ trễ (*latency*) | `p50`, `p95`, `p99` và thời gian hàng đợi/thực thi, đơn vị ms | Phần lớn tác vụ, đặc biệt nhóm chậm, có xong đúng hạn không? |
| Công suất (*throughput/capacity*) | Số tác vụ **hoàn tất** thành công mỗi giây, tốc độ tiếp nhận, tải cực đại vẫn đạt SLO | Hệ thống xử lý bền vững được bao nhiêu công việc mà không tích nợ? |
| Tài nguyên (*resources*) | CPU, heap/RSS, GC, số thread, queue, connection pool, I/O và giới hạn môi trường | Có vượt ngân sách hoặc đẩy nghẽn sang thành phần khác không? |

Một số định nghĩa quan trọng:

- `p95 = 400 ms` nghĩa là khoảng 95% quan sát trong cửa sổ đo có độ trễ không vượt 400 ms. Nó **không** có nghĩa là mọi tác vụ dưới 400 ms.
- **Throughput hoàn tất** khác số task đã được nhận vào queue. Khi queue lớn dần, số task được nhận có thể cao dù công suất xử lý thực không tăng.
- **Capacity tại SLO** là tải cao nhất có thể duy trì trong thời gian đo quy định, đồng thời thỏa độ trễ, lỗi và tài nguyên. Không lấy throughput đỉnh vài giây để kết luận.
- Với một tác vụ bất đồng bộ, hãy phân biệt thời gian người gọi chờ phản hồi HTTP, thời gian từ lúc giao việc đến lúc tác vụ hoàn thành, và thời gian chạy trên worker. Chúng là các phép đo khác nhau.

Một vòng đời tác vụ có thể ký hiệu:

```text
t0: nhận yêu cầu
t1: giao tác vụ vào executor
t2: worker bắt đầu chạy
t3: tác vụ hoàn tất (thành công/thất bại)

Thời gian chờ queue = t2 - t1
Thời gian thực thi = t3 - t2
Thời gian async end-to-end = t3 - t1
Thời gian phía ứng dụng trước khi giao việc = t1 - t0
```

Tất cả dấu thời gian dùng đồng hồ đơn điệu (`System.nanoTime()`) khi đo khoảng thời gian trong **cùng một tiến trình**. Dùng timestamp có đồng bộ clock và trace/span khi đo qua nhiều dịch vụ. Tập hợp p95/p99 từ **cùng một loại tác vụ, cùng cửa sổ tải và cùng ranh giới bắt đầu/kết thúc**.

## 3. Hiểu cơ chế cấu hình trước khi so sánh

`ThreadPoolTaskExecutor` của Spring bao `ThreadPoolExecutor` trong JDK. Các tham số chính:

| Cấu hình | Ý nghĩa | Tác động hoặc đánh đổi có thể gặp |
| --- | --- | --- |
| `corePoolSize` | Số worker lõi được giữ trong pool (theo chính sách timeout) | Tăng có thể giảm chờ nhưng tăng cạnh tranh CPU, bộ nhớ stack và kết nối phụ thuộc |
| `maxPoolSize` | Giới hạn trên số worker | Với queue còn chỗ, executor thường **xếp hàng trước khi tăng vượt core** |
| `queueCapacity` | Sức chứa tác vụ chờ; giá trị dương tạo `LinkedBlockingQueue`, giá trị không dương tạo `SynchronousQueue` | Queue dài che giấu thiếu công suất và làm tail latency tăng; queue quá ngắn dễ từ chối lúc burst |
| `keepAliveSeconds` | Thời gian nhàn rỗi cho worker vượt core trước khi thu hồi | Chủ yếu ảnh hưởng việc co pool sau đợt tải, không tự làm tác vụ đang chờ nhanh hơn |
| `RejectedExecutionHandler` | Hành vi khi không thể nhận thêm task | `AbortPolicy` báo lỗi rõ; `CallerRunsPolicy` có thể chặn chính thread gọi và chuyển độ trễ sang HTTP/caller |

Trình tự phổ biến khi submit một task vào pool dùng hàng đợi có giới hạn:

1. Nếu số worker ít hơn `corePoolSize`, tạo worker để chạy.
2. Nếu đã đạt core và hàng đợi còn chỗ, đưa task vào queue.
3. Khi queue đầy, có thể tăng worker cho đến `maxPoolSize`.
4. Nếu cả queue và pool đều hết chỗ, áp dụng chính sách từ chối.

**Hệ quả dễ bỏ sót:** tăng `maxPoolSize` từ 8 lên 32 trong khi `corePoolSize = 8` và queue rất lớn có thể gần như không đổi kết quả, vì queue ít khi đầy để kích hoạt worker thứ 9. Không suy luận “đã tăng max thì chắc đã tăng song song thực tế”.

Đối với `ThreadPoolTaskScheduler`, hãy đo **độ lệch thời điểm kích hoạt (schedule lag)** và thời gian job hoàn tất. Pool của scheduler, `fixedRate`, `fixedDelay`, cron và thời gian chạy job cần được hiểu riêng; không áp dụng cơ học bảng queue của `ThreadPoolTaskExecutor` cho mọi scheduler. Với virtual threads (khi phiên bản Spring/JDK hỗ trợ), kết quả cũng cần một lượt đo riêng; virtual threads không tạo thêm lõi CPU hay kết nối cơ sở dữ liệu.

## 4. Đặt mục tiêu và ranh giới thí nghiệm trước khi đổi cấu hình

Ví dụ **mục tiêu giả định** cho luồng xử lý báo cáo bất đồng bộ, tính tại tải chuẩn 160 task/s:

- `p95` thời gian từ submit đến hoàn tất **≤ 400 ms**; theo dõi thêm `p99` để tránh bỏ qua đuôi chậm.
- Throughput thành công ổn định **≥ 160 task/s**; tỷ lệ thất bại và từ chối **≤ ngưỡng đã thống nhất** (ví dụ bài thử này yêu cầu 0 từ chối ở tải chuẩn).
- CPU của tiến trình **≤ 75%** trong pha ổn định, heap sử dụng đỉnh **≤ 768 MiB**; không có GC pause kéo dài hoặc tình trạng cạn DB connections.
- Queue không tăng liên tục trong cửa sổ ổn định; sau burst ngắn phải tiêu hết backlog trong thời hạn đã định.

Các con số trên **chỉ để thực hành cách ra quyết định**; mục tiêu thật đến từ SLO nghiệp vụ, giới hạn tài nguyên triển khai, thời gian chờ caller và độ lớn payload.

Cần cố định cho mỗi phép so sánh: phiên bản mã, JVM, số CPU/memory limit của container, dependency phiên bản, dữ liệu kiểm thử, độ trễ service phụ thuộc, số kết nối DB, loại task và chính sách retry/timeout. Nếu thay đổi đồng thời cấu hình pool lẫn số pod hoặc chỉ số cache hit, phép so sánh không còn cô lập được nguyên nhân.

## 5. Đo gì, ở đâu và bằng công cụ nào?

Đo ít nhất bốn lớp:

1. **Tại điểm vào:** incoming rate, số request/task được chấp nhận, trạng thái HTTP, timeout và thời gian đáp ứng caller.
2. **Tại executor:** active threads, current pool size, queue size/capacity, lượng task hoàn tất và số task bị từ chối. Dùng counter/timer thực sự cho số thành công/thất bại; `ThreadPoolExecutor.getCompletedTaskCount()` tính task kết thúc ở mức executor, không đồng nghĩa nghiệp vụ thành công.
3. **Trong tác vụ:** queue wait, execution time, tổng thời gian hoàn tất, lỗi/retry, tỷ lệ chờ I/O. Với nhiều loại công việc, gắn nhãn loại tác vụ để tránh pha trộn hai phân phối khác nhau.
4. **Ở máy và phụ thuộc:** CPU usage/throttling, heap, GC pause/allocation, thread count, DB pool active/pending, connection timeouts, HTTP client latency/rate limit và số lần I/O lỗi.

Spring Boot Actuator + Micrometer có thể xuất timer, counter và gauge qua hệ thống metrics đang dùng; tracing có thể phân đoạn thời gian. Kiểm tra **tên metric thực tế** của phiên bản Boot, registry và cách instrument executor trong dự án, thay vì giả định mọi ứng dụng tự có đủ mọi metric. Java Flight Recorder (JFR), thread dump và GC log hỗ trợ giải thích cơ chế khi con số không khớp.

Một điểm đọc trạng thái executor trong ứng dụng Spring:

```java
// Ví dụ phương thức trong một Spring bean đã inject reportExecutor.
// Đây là mẫu quan sát tại một thời điểm, không phải bộ đo p95.
ThreadPoolExecutor delegate = reportExecutor.getThreadPoolExecutor();
int active = delegate.getActiveCount();
int poolSize = delegate.getPoolSize();
int queued = delegate.getQueue().size();
long completedExecutorTasks = delegate.getCompletedTaskCount();
```

`getActiveCount()` và `getQueue().size()` là ảnh chụp tức thời, có thể thay đổi ngay sau khi đọc. Lấy mẫu định kỳ, cộng với các timer/counter cho từng task. Đừng gộp kết quả trung bình của một khoảng thử với p95 của khoảng thử khác.

## 6. Ví dụ cấu hình Spring có thể thay đổi để đo

Trong một ứng dụng đã có Spring Framework 6.x, dùng Java 17+, tạo file `AsyncPoolConfig.java`:

```java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncPoolConfig {

    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setThreadNamePrefix("report-");
        pool.setCorePoolSize(12);
        pool.setMaxPoolSize(16);
        pool.setQueueCapacity(80);
        pool.setKeepAliveSeconds(30);
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return pool; // Spring khởi tạo bean theo vòng đời container.
    }
}
```

Và file `ReportService.java` để minh họa `@Async` chọn đúng executor:

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("reportExecutor")
    public CompletableFuture<String> generate(String id) {
        // Chỉ là mô phỏng I/O; thay bằng công việc thực khi benchmark.
        try {
            Thread.sleep(200);
            return CompletableFuture.completedFuture("report:" + id);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(ex);
        }
    }
}
```

Đây là **hai class để ghép vào ứng dụng Spring có dependencies phù hợp**, không phải một chương trình độc lập chạy chỉ bằng `javac` không có classpath Spring. Gọi `reportService.generate(...)` **từ bean khác qua Spring proxy**; tự gọi `this.generate(...)` trong cùng class thường bỏ qua `@Async` trong chế độ proxy mặc định. Gọi `.join()` ngay trên mỗi kết quả ở thread nhận yêu cầu có thể vô tình làm tuần tự hóa luồng gọi.

Để đo, thay lần lượt các giá trị `corePoolSize`, `maxPoolSize`, `queueCapacity` ở từng phiên bản thí nghiệm và giữ nguyên workload. Không coi `Thread.sleep(200)` là tải thực tế: nó mô phỏng chờ, không phản ánh CPU, DB, network và kích thước payload của hệ thống sản xuất. Nếu Spring Boot tự cấu hình executor qua `spring.task.execution.*`, xác nhận **executor thực sự được `@Async` dùng**; một bean riêng được gọi rõ bằng `@Async("reportExecutor")` giúp tránh nhầm pool.

**Ghi nhận lỗi từ chối:** với `AbortPolicy`, việc submit khi quá tải có thể báo `TaskRejectedException` tại lớp Spring (gắn với `RejectedExecutionException` của JDK). Log/counter các lỗi từ chối và áp dụng chính sách retry có giới hạn, backoff hoặc trả lỗi phù hợp; không âm thầm bỏ task. `CallerRunsPolicy` thay đổi nơi task chạy và có thể làm tăng độ trễ caller, nên cần đo cả phía caller.

## 7. Bài thí nghiệm JDK thuần để thấy queue và max pool hoạt động

Ví dụ này không cần Spring; giúp xác minh quy tắc nhận task bằng cách giữ các worker đang chạy bằng `CountDownLatch`.

Tạo file `PoolSizingLab.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PoolSizingLab {
    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.AbortPolicy());

        CountDownLatch release = new CountDownLatch(1);
        int accepted = 0;
        int rejected = 0;

        for (int i = 0; i < 8; i++) {
            try {
                pool.execute(() -> {
                    try {
                        release.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
                accepted++;
            } catch (RejectedExecutionException ex) {
                rejected++;
            }
        }

        System.out.println("accepted=" + accepted + ", rejected=" + rejected);
        release.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }
    }
}
```

Chạy với JDK 17+:

```bash
javac PoolSizingLab.java
java PoolSizingLab
```

Kết quả:

```text
accepted=6, rejected=2
```

Giải thích: 2 task đầu có worker lõi; 2 task tiếp được xếp queue; khi queue đầy, 2 task tiếp kích hoạt worker thứ 3 và thứ 4; 2 task còn lại bị từ chối. `release` giữ các task đã nhận không hoàn tất trước khi vòng lặp submit xong, nên kết quả đếm không phụ thuộc tốc độ `sleep`. Đây là **mô hình giải thích cấu trúc pool**, không phải benchmark throughput hay latency.

## 8. Thiết kế phép so sánh trước–sau công bằng

Một quy trình có thể lặp lại:

1. **Baseline:** ghi cấu hình, SLO, giới hạn CPU/RAM/container, phiên bản mã, số instance, loại workload và tình trạng dịch vụ phụ thuộc. Xác nhận có đủ metrics cho latency, completion, failure và queue.
2. **Tải thử:** dùng cùng phân phối payload, cùng tỷ lệ task ngắn/dài, cùng dữ liệu. Chạy nhiều mức tải (ví dụ 80, 120, 160, 200 task/s) và một pha burst riêng. Công cụ phát tải phải đủ khỏe để không trở thành nút thắt.
3. **Khởi động:** warm-up JVM/JIT, connection pool và cache theo quy trình xác định; bỏ số liệu warm-up khỏi pha so sánh ổn định. Nếu muốn đánh giá cold start, đo nó thành một phép thử riêng.
4. **Thay đổi từng giả thuyết:** ví dụ chỉ tăng `maxPoolSize`; sau đó chỉ thu queue; sau đó chỉ tăng `corePoolSize`. Ghi cấu hình đầy đủ ở mỗi lượt. Giữ nguyên quota tài nguyên và dependency.
5. **Thu mẫu đủ dài:** lưu các chuỗi thời gian trong giai đoạn ổn định, lặp lại vài lần (ví dụ 3–5 lượt) để thấy dao động. So sánh cùng cửa sổ tải, không chỉ nhìn một điểm lớn nhất/nhỏ nhất.
6. **Đọc đồng thời tất cả guardrail:** p95/p99, throughput thành công, lỗi/từ chối, tốc độ queue tăng/giảm, CPU throttling, heap/GC và sức khỏe DB/network.
7. **Ra quyết định:** chọn cấu hình thỏa **mọi mục tiêu bắt buộc** và còn dư địa an toàn; nếu không phương án nào đạt, xác định bottleneck và đổi cách xử lý (tối ưu code, giảm fan-out, hạn chế concurrency, cache, mở rộng dependency...).

**Hai kiểu tải không nên đánh đồng:** kiểm thử *constant arrival rate* giữ tốc độ task đến để nhìn ra backlog khi quá công suất; kiểm thử *closed-loop* với số client cố định có thể tự giảm tốc độ gửi khi server chậm. Ghi rõ kiểu tải trong báo cáo để tránh diễn giải sai throughput.

Dữ liệu cần lưu cho mỗi thử nghiệm có thể gồm:

```text
run_id, code_version, JVM, pod_count, cpu_limit, memory_limit,
core_size, max_size, queue_capacity, rejection_policy,
workload_type, offered_tasks_per_sec, completed_ok_per_sec,
failed_per_sec, rejected_per_sec, latency_p50_ms, latency_p95_ms,
latency_p99_ms, queue_wait_p95_ms, max_queue_depth,
active_threads_peak, cpu_percent, cpu_throttling, heap_peak_mib,
gc_pause_ms, downstream_pending, measurement_window
```

Không cần hiển thị tất cả vào bảng quyết định cuối cùng, nhưng giữ dữ liệu thô và cấu hình giúp kiểm chứng lại kết luận.

## 9. Diễn giải bảng kết quả minh họa

Giả sử workload chủ yếu chờ I/O và tất cả số dưới đây là **số liệu giả định cho bài học** (không phải benchmark của một hệ thống thật). Tại tải 160 task/s, tiêu chí là `p95 ≤ 400 ms`, completed throughput `≥ 160 task/s`, `CPU ≤ 75%`, heap peak `≤ 768 MiB`, không bị từ chối task.

| Lượt | `core/max/queue` | Hoàn tất thành công (task/s) | `p95` (ms) | CPU (%) | Heap peak (MiB) | Queue peak | Reject | Đánh giá |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Gốc | `8/8/200` | 130 | 640 | 45 | 610 | 180 | 0 | Không đạt công suất/độ trễ |
| A | `8/16/200` | 131 | 635 | 47 | 615 | 178 | 0 | Tăng max gần như không giúp |
| B | `8/16/80` | 151 | 435 | 62 | 620 | 78 | 0 | Tốt hơn, nhưng chưa đạt |
| C | `12/16/80` | 164 | 365 | 72 | 650 | 51 | 0 | Đạt mọi mục tiêu đã nêu |
| D | `16/16/80` | 168 | 320 | 86 | 680 | 14 | 0 | Nhanh hơn nhưng vượt CPU |

Đọc bảng theo chuỗi giả thuyết:

- **Gốc → A:** chỉ tăng max, nhưng queue lớn nên pool ít có lý do mở thêm worker; throughput và latency gần như không đổi.
- **A → B:** queue nhỏ hơn khiến pool có thể tăng vượt core sớm hơn, nên throughput tăng và p95 giảm. Không kết luận giảm queue luôn tốt: dưới burst mạnh có thể tăng reject.
- **B → C:** tăng core đem lại đủ worker để đạt cả công suất lẫn p95, trong giới hạn CPU/heap đã đặt.
- **C → D:** thêm worker giúp p95 giảm thêm nhưng CPU chạm 86%; D **không đạt ngân sách tài nguyên**, nên không thể chọn chỉ vì nhanh hơn.

Theo mục tiêu giả định, **C là ứng viên**. Vẫn cần xác nhận bằng nhiều lượt đo, p99, trạng thái DB/network, burst test và khoảng dư an toàn trước khi triển khai. Nếu CPU target thay đổi hoặc chi phí vận hành khác đi, quyết định cũng có thể thay đổi.

### Vì sao số liệu queue và throughput phải đọc cùng nhau?

Trong trạng thái ổn định, định luật Little cho quan hệ xấp xỉ:

```text
L = λ × W

L: số tác vụ trung bình trong hệ thống
λ: tốc độ hoàn tất/đến ổn định (task/s)
W: thời gian trung bình trong hệ thống (giây)
```

Ví dụ, nếu hệ thống hoàn tất đều `100 task/s` và thời gian trung bình submit→complete là `0,2 s`, số tác vụ trung bình đang ở trong hệ thống khoảng `20`. Quan hệ này áp dụng cho ranh giới đo nhất quán và hệ thống tương đối ổn định; **không** dùng để suy đoán công suất ở giai đoạn backlog tăng không giới hạn, reject hoặc timeout liên tục. Nó cũng áp dụng cho **trung bình**, không phải lấy p95 nhân throughput để ước lượng trực tiếp.

## 10. Nhận diện bottleneck qua dấu hiệu

| Dấu hiệu quan sát | Giả thuyết cần kiểm tra | Hướng thử tiếp theo |
| --- | --- | --- |
| Queue tăng, active threads sát giới hạn, CPU còn thấp | Nhiều task chờ I/O hoặc pool quá nhỏ | Tách queue wait khỏi execution time; kiểm tra DB/HTTP client và thử tăng có kiểm soát |
| CPU cao/throttling, runnable threads đông, throughput không tăng | Tranh chấp CPU, context switching, CPU-bound workload | Giảm concurrency, tối ưu thuật toán, tăng CPU quota/scale nếu có cơ sở |
| Queue ngắn nhưng p95 task cao, DB pending cao | Nghẽn connection pool/query hoặc downstream | Đo query/client latency, giới hạn fan-out, sửa truy vấn hoặc nâng capacity phụ thuộc |
| Heap/RSS/GC tăng khi tăng queue | Task/payload bị giữ trong queue, backlog tích lũy | Đặt queue hữu hạn, backpressure, giới hạn payload và thời gian lưu |
| Throughput cao nhưng reject/timeout tăng | Nhận vượt công suất ổn định hoặc backpressure sai | Đo tỷ lệ thành công, giới hạn tải, retry có backoff; không dùng accepted rate làm throughput |
| Scheduler chạy lệch giờ, job chồng hoặc trễ | Scheduler pool nhỏ, job giữ thread quá lâu, trigger không phù hợp | Đo schedule lag, thời gian job, xem semantics fixed-rate/fixed-delay và tách job nặng |

Những hiện tượng có thể xảy ra cùng lúc. Ví dụ, tăng thread giúp queue giảm nhưng số yêu cầu SQL đồng thời tăng khiến DB pool nghẽn; p95 của tác vụ có thể lại xấu hơn. Cần đọc trace và metric ở cả executor lẫn dependency mới kết luận nguyên nhân.

## 11. Lỗi phổ biến khi đối chiếu cấu hình

- **Chỉ xem trung bình:** mean giảm trong khi `p99` tăng mạnh. Giữ các phân vị và timeout/error rate.
- **Đếm task đã submit là đã xử lý:** queue tích lũy làm biểu đồ công suất giả tạo. Luôn dùng completed-success và đo backlog.
- **Đổi nhiều biến không ghi lại:** tăng pool đồng thời tăng số instance, đổi payload hoặc cache rồi gán kết quả cho một tham số.
- **Dùng queue vô hạn để tránh reject:** chỉ trì hoãn dấu hiệu quá tải, tăng bộ nhớ và thời gian chờ; đặt giới hạn dựa trên ngân sách latency và xử lý từ chối có chủ đích.
- **Bỏ qua khác biệt CPU-bound/I/O-bound:** số thread tối ưu phụ thuộc thời gian chờ, số lõi, lock contention và dependency; không có một con số mặc định tốt cho mọi tác vụ.
- **Dùng `CallerRunsPolicy` nhưng không đo caller latency:** task chuyển sang thread gửi làm thời gian HTTP tăng, dù queue executor nhìn đẹp hơn.
- **Retry vô hạn sau reject/timeout:** làm tải tăng vòng lặp và khiến nghẽn nặng thêm. Cần deadline, giới hạn retry, jitter/backoff và tính idempotency.
- **Đọc metric trong pha warm-up như steady state:** JIT, cache và kết nối mới khiến so sánh lệch.
- **Quên scope của `@Async`:** self-invocation không qua proxy; đo sai executor hoặc để exception trong `void @Async` chỉ được ghi log.
- **Xem virtual threads là thuốc chữa mọi vấn đề:** chúng giúp một số tải blocking I/O về chi phí thread, nhưng không làm CPU, DB connections hay remote service nhanh hơn.

## 12. Quyết định triển khai và kiểm chứng sau thay đổi

Một quyết định cấu hình tốt có thể viết thành câu có thể kiểm tra:

> “Ở workload W, tải 160 task/s, giới hạn 2 vCPU và 768 MiB heap, cấu hình C giảm p95 từ 640 ms xuống 365 ms, tăng completed throughput từ 130 lên 164 task/s, không có reject và CPU đạt 72% trong cửa sổ thử. Các con số là ví dụ minh họa; trong hệ thống thật phải trích run ID và dashboard tương ứng.”

Khi áp dụng thật, giữ cấu hình cũ để rollback, triển khai canary/nhóm nhỏ, đối chiếu cùng dạng lưu lượng và tiếp tục nhìn p95/p99, errors, backlog, CPU/GC và downstream. Đặt điều kiện hoàn tác rõ ràng, chẳng hạn p95 vượt ngưỡng trong nhiều cửa sổ liên tiếp, tỷ lệ lỗi tăng, CPU throttling kéo dài hoặc backlog không tiêu hết sau burst. Tránh quyết định chỉ dựa trên vài phút lưu lượng nhẹ.

### Bài tự kiểm tra

1. Một pool `core=8, max=32, queueCapacity=100000` có active 8, queue tăng dần và CPU 30%. Vì sao tăng riêng `max` có thể không giảm p95?
2. Sau khi giảm `queueCapacity`, queue giảm và throughput tăng nhưng `rejected/s` tăng mạnh ở burst. Có thể gọi phương án đó “tốt hơn” chỉ từ p95 không?
3. `@Async` hoàn tất nhanh hơn nhưng HTTP p95 xấu đi sau khi dùng `CallerRunsPolicy`. Cần kiểm tra thêm metric nào?
4. Cấu hình C đạt p95/throughput nhưng làm DB connection wait vượt SLO. Có nên chọn C không?

**Gợi ý đối chiếu:** (1) Queue chưa đầy nên pool chưa mở thêm worker vượt core; (2) không, phải xét tỷ lệ thành công, khả năng chịu burst và giới hạn tài nguyên; (3) thời gian caller, số lần caller chạy task, reject/backpressure, CPU và end-to-end trace; (4) không, vì nghẽn đã chuyển sang phụ thuộc và phá SLO toàn hệ thống.

## Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `ThreadPoolTaskExecutor`, `@Async`, `TaskScheduler`, queue và rejection.
- [Spring Boot — Task Execution and Scheduling (3.3)](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html): cách Boot tự cấu hình executor/scheduler, các thuộc tính cấu hình và lưu ý virtual threads. Khi áp dụng dự án khác phiên bản, kiểm tra tài liệu đúng phiên bản đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="monitoring-tuning-009">Giám sát không thay thế cơ chế lưu kết quả nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Giám sát không thay thế cơ chế lưu kết quả nghiệp vụ

## 1. Vấn đề cần giải quyết

Trong ứng dụng Spring có xử lý đồng thời, một yêu cầu có thể được nhận ở HTTP thread, chuyển sang `@Async`/`TaskExecutor` hoặc job định kỳ, hoàn thành ở một thread khác rồi mới trả kết quả cho người dùng. Đôi khi hệ thống có log `"completed"`, metric `success_count` tăng và dashboard vẫn xanh, nhưng người dùng hỏi lại thì **không tìm thấy kết quả**. Nguyên nhân thường là trạng thái/kết quả nghiệp vụ chỉ tồn tại trong bộ nhớ hoặc ở hệ thống giám sát, chưa được ghi bền vững vào nơi dữ liệu nghiệp vụ được quản lý.

**Nguyên tắc:** *Observability cho biết hệ thống đã quan sát được gì; business persistence xác nhận hệ thống đã ghi nhận điều gì như một kết quả nghiệp vụ.* Hai thứ bổ trợ nhau, nhưng không thay thế nhau.

Bài học tập trung vào Spring Framework concurrency: `@Async`, `ThreadPoolTaskExecutor`, tác vụ nền/`@Scheduled` và job Quartz. Mục tiêu là nhận biết ranh giới giữa **thực thi tác vụ**, **ghi nhận kết quả** và **giám sát thực thi**; thiết kế API có thể tra cứu kết quả sau khi tiến trình tắt, thread thay đổi hoặc hệ thống giám sát mất dữ liệu.

Sau bài này, bạn cần có thể:

1. Phân biệt log, metric, trace, alert với bản ghi kết quả và trạng thái nghiệp vụ.
2. Định nghĩa vòng đời tác vụ có trạng thái bền vững (ví dụ `QUEUED → RUNNING → SUCCEEDED/FAILED`).
3. Đặt đúng ranh giới transaction giữa HTTP thread và worker thread.
4. Giải thích vì sao `@Async`/`CompletableFuture` hoặc một dashboard không bảo đảm kết quả được lưu.
5. Nhận diện khoảng trống crash, retry, xử lý trùng và thiết kế phục hồi phù hợp.

## 2. Giám sát trả lời câu hỏi khác với lưu kết quả

Giả sử khách hàng yêu cầu xuất báo cáo. API trả `202 Accepted` với mã `jobId`; worker xử lý ở nền. Lúc này:

| Cơ chế | Câu hỏi nó trả lời tốt | Điều nó **không** tự bảo đảm |
| --- | --- | --- |
| Log có `jobId` | Lần chạy nào bắt đầu, lỗi ở bước nào, stack trace là gì? | Một trạng thái kết quả có cấu trúc, duy nhất, được commit theo nghiệp vụ. |
| Metric Micrometer/Actuator | Có bao nhiêu tác vụ hoàn thành, mất bao lâu, queue sâu bao nhiêu? | Truy vấn kết quả chính xác cho một `jobId`; counter có thể reset hoặc dữ liệu bị tổng hợp. |
| Distributed tracing | Request đi qua service/thread nào, latency nằm ở span nào? | Kết quả cuối cùng được commit vào DB; trace có thể được sampling. |
| Health check/alert | Dịch vụ, pool, DB đang khỏe hay có dấu hiệu bất thường? | Kết quả của từng lần xử lý đã được lưu và sẽ đọc lại được. |
| `Future`/`CompletableFuture` trong RAM | Một lần chạy còn sống đã hoàn thành hay lỗi? | Tra cứu được sau restart, trên node khác hoặc khi object bị giải phóng. |
| **Kho nghiệp vụ** (DB/object store + metadata DB) | Với `jobId` này: trạng thái, kết quả, lỗi, thời điểm và phiên bản nào đã commit? | Tự giám sát CPU/queue/latency hoặc tự bảo đảm worker được phát lại nếu chưa thiết kế dispatch bền vững. |

Ngay cả khi log và trace được đẩy vào kho có retention dài, chúng vẫn thường **không có hợp đồng giao dịch** với việc thay đổi bản ghi nghiệp vụ. Một dòng log `"done"` có thể xuất hiện trước khi DB commit thất bại; ngược lại, DB đã commit nhưng log/metric bị mất do tiến trình chết.

Hãy xác định *nguồn sự thật* theo từng loại thông tin:

- **Business outcome**: DB và bảng/trạng thái nghiệp vụ hoặc tệp kết quả đã lưu bền vững, có tham chiếu trong DB.
- **Technical execution evidence**: log, trace, metric, error report, sự kiện vận hành.
- **Vận hành job**: queue, scheduler, consumer checkpoint; các tín hiệu này cũng không mặc nhiên đồng nghĩa với kết quả kinh doanh.

Ví dụ, Quartz có thể lưu `JobDetail`/`Trigger` trong JDBC JobStore, nhưng lịch được lưu **không có nghĩa** file báo cáo hay kết quả thanh toán đã được lưu. Tương tự, `@Scheduled` hay `@Async` xác định *cách chạy* chứ không tự thiết kế bảng kết quả.

## 3. Thiết kế hợp đồng kết quả bền vững

### 3.1. Một bản ghi trạng thái có định danh ổn định

Với thao tác chạy nền có thể được người dùng hỏi lại, tối thiểu cần:

| Trường | Ý nghĩa |
| --- | --- |
| `job_id` | Mã truy vấn ổn định cho lần xử lý. |
| `status` | `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`; có thể mở rộng `CANCELLED`/`TIMED_OUT` theo nghiệp vụ. |
| `created_at`, `updated_at` | Biết tác vụ được nhận khi nào, trạng thái dừng lâu bao nhiêu. |
| `result` hoặc `result_ref` | Kết quả nhỏ lưu trực tiếp; kết quả lớn lưu object storage và ghi đường dẫn/metadata nhất quán. |
| `error_code` | Mã lỗi nghiệp vụ đã kiểm soát, không thay thế bởi stack trace. |
| `version` | Hỗ trợ optimistic locking, tránh tác vụ trùng ghi đè trạng thái mới. |
| `idempotency_key`/`attempt` (khi cần) | Nhận diện cùng một yêu cầu và điều phối retry/đánh giá trùng lặp. |

API nên phân biệt **đã nhận yêu cầu** với **đã hoàn tất kết quả**:

- `POST /jobs` nhận hợp lệ, **lưu bản ghi QUEUED thành công**, trả `202 Accepted` và `jobId`. Phản hồi này không được diễn đạt là nghiệp vụ đã thành công.
- `GET /jobs/{id}` đọc **trạng thái đã commit từ kho nghiệp vụ**, trả `QUEUED/RUNNING/SUCCEEDED/FAILED` cùng `result`/`errorCode` thích hợp.
- Khi xử lý hoàn tất, ghi `result` và `SUCCEEDED` trong **cùng giao dịch DB** nếu dữ liệu thuộc cùng DB. Nếu kết quả nằm ở một hệ thống khác, cần cơ chế phối hợp kết quả và metadata theo hợp đồng riêng.

### 3.2. Một state machine rõ ràng

```text
           persist request
     ┌────────────────────────┐
     │                        v
 [request] --> QUEUED ------> RUNNING ------> SUCCEEDED
                   │              │
                   │              └----------> FAILED
                   └-------------------------> FAILED
                         dispatch rejected

Crash/restart có thể để lại QUEUED hoặc RUNNING.
Cần reconciliation/re-dispatch theo chính sách; monitoring chỉ cảnh báo.
```

Không coi `"worker method returned"` là `SUCCEEDED` nếu bản ghi kết quả chưa commit. Ngược lại, không suy ra `FAILED` chỉ vì không thấy log `"completed"`. Trạng thái thực phải đọc từ dữ liệu đã commit và bằng chứng liên quan.

Định nghĩa chuyển trạng thái có điều kiện: chỉ `QUEUED` được đánh dấu `RUNNING`; chỉ `RUNNING` được ghi kết quả thành công. Với nhiều worker, cần update có điều kiện, version/row lock hoặc lease để tránh hai worker nhận cùng một job. Đối với việc tạo *cùng một ý định nghiệp vụ* nhiều lần, thêm idempotency key với unique constraint để chống yêu cầu trùng ngay từ đầu.

## 4. Ranh giới Spring concurrency và transaction

Trong mô hình Spring thông thường, `@Async` đưa công việc qua một `TaskExecutor` sang thread khác. Giao dịch `@Transactional` kiểu imperative dùng ngữ cảnh gắn với thread, **không tự truyền giao dịch từ HTTP thread sang worker thread**. Spring AOP thường áp dụng `@Async`/`@Transactional` khi gọi phương thức **qua proxy của bean**; tự gọi `this.method()` bên trong cùng bean có thể bỏ qua advice.

Một luồng dễ kiểm soát là:

```text
HTTP thread                         worker thread
-----------                         -------------
validate request
DB TX1: INSERT QUEUED
COMMIT TX1
dispatch @Async(jobId) -----------> DB TX2: QUEUED -> RUNNING
202 + jobId                         COMMIT TX2
                                    thực hiện nghiệp vụ
                                    DB TX3: ghi result + SUCCEEDED
                                    COMMIT TX3
                                    tăng counter / log hoàn tất
GET /jobs/{id} -> đọc DB <-------- trạng thái/kết quả đã commit
```

**Lưu ý quan trọng:** Việc `COMMIT TX1` xảy ra trước khi gọi `@Async` ngăn worker nhìn thấy bản ghi chưa được commit. Nhưng nếu ứng dụng chết **sau COMMIT TX1 và trước khi dispatch**, bản ghi `QUEUED` vẫn tồn tại còn task trong executor chưa được gửi đi. Đây là lý do database kết quả và queue trong bộ nhớ chưa đủ tạo thành hệ thống phân phối công việc bền vững: production cần durable queue, transactional outbox, hoặc DB worker polling + cơ chế claim/lease và phục hồi.

## 5. Ví dụ chạy được: lưu kết quả vào DB, metric chỉ để quan sát

Ví dụ dùng **Java 17 + Spring Boot 3.5.16 + Spring Data JPA + H2 file database + Actuator/Micrometer**. H2 ghi ra tệp để thử restart; đây là **minh họa**, production nên dùng DB thực và migration có kiểm soát. Công việc demo chỉ biến chuỗi thành chữ in hoa, không tạo side effect bên ngoài. Chuỗi `fail` mô phỏng lỗi nghiệp vụ.

### 5.1. Cấu trúc project

```text
async-result-demo/
├── pom.xml
├── src/main/resources/application.properties
└── src/main/java/example/asyncresults/
    ├── AsyncResultApplication.java
    ├── AsyncConfig.java
    ├── JobState.java
    ├── BusinessJob.java
    ├── BusinessJobRepository.java
    ├── JobView.java
    ├── OutcomeStore.java
    ├── BusinessWorker.java
    └── JobController.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>
    <groupId>example</groupId>
    <artifactId>async-result-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
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
```

`src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:h2:file:./business-results
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
management.endpoints.web.exposure.include=health,metrics
```

`business-results.mv.db` được tạo ở thư mục chạy ứng dụng và tồn tại qua restart bình thường. `ddl-auto=update` thuận tiện để học, **không dùng thay migration trong môi trường thực**.

### 5.2. Khởi động Spring và executor có queue hữu hạn

`src/main/java/example/asyncresults/AsyncResultApplication.java`:

```java
package example.asyncresults;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AsyncResultApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsyncResultApplication.class, args);
    }
}
```

`src/main/java/example/asyncresults/AsyncConfig.java`:

```java
package example.asyncresults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class AsyncConfig {

    @Bean("businessExecutor")
    public ThreadPoolTaskExecutor businessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("business-");
        executor.initialize();
        return executor;
    }
}
```

Pool và queue có giới hạn; khi đạt ngưỡng, việc nộp task có thể bị từ chối (`TaskRejectedException`). **Queue này vẫn ở trong bộ nhớ tiến trình**: việc đặt `queueCapacity=20` không làm task trở nên bền vững.

### 5.3. Lưu bản ghi trạng thái nghiệp vụ

`src/main/java/example/asyncresults/JobState.java`:

```java
package example.asyncresults;

public enum JobState {
    QUEUED, RUNNING, SUCCEEDED, FAILED
}
```

`src/main/java/example/asyncresults/BusinessJob.java`:

```java
package example.asyncresults;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "business_jobs")
public class BusinessJob {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobState state;

    @Column(nullable = false, length = 500)
    private String payload;

    @Column(length = 500)
    private String result;

    @Column(length = 64)
    private String errorCode;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected BusinessJob() {
    }

    public static BusinessJob queued(String id, String payload) {
        BusinessJob job = new BusinessJob();
        job.id = id;
        job.payload = payload;
        job.state = JobState.QUEUED;
        job.createdAt = Instant.now();
        job.updatedAt = job.createdAt;
        return job;
    }

    public String id() { return id; }
    public JobState state() { return state; }
    public String payload() { return payload; }
    public String result() { return result; }
    public String errorCode() { return errorCode; }
    public Instant updatedAt() { return updatedAt; }

    public void markRunning() {
        if (state != JobState.QUEUED) {
            throw new IllegalStateException("Job is not queued");
        }
        state = JobState.RUNNING;
        updatedAt = Instant.now();
    }

    public void markSucceeded(String value) {
        if (state != JobState.RUNNING) {
            throw new IllegalStateException("Job is not running");
        }
        result = value;
        errorCode = null;
        state = JobState.SUCCEEDED;
        updatedAt = Instant.now();
    }

    public void markFailed(String code) {
        if (state != JobState.RUNNING && state != JobState.QUEUED) {
            throw new IllegalStateException("Job cannot fail from " + state);
        }
        errorCode = code;
        state = JobState.FAILED;
        updatedAt = Instant.now();
    }
}
```

`src/main/java/example/asyncresults/BusinessJobRepository.java`:

```java
package example.asyncresults;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessJobRepository
        extends JpaRepository<BusinessJob, String> {
}
```

`src/main/java/example/asyncresults/JobView.java`:

```java
package example.asyncresults;

import java.time.Instant;

public record JobView(
        String id,
        JobState status,
        String result,
        String errorCode,
        Instant updatedAt) {

    public static JobView of(BusinessJob job) {
        return new JobView(
                job.id(),
                job.state(),
                job.result(),
                job.errorCode(),
                job.updatedAt());
    }
}
```

`@Version` giúp phát hiện cập nhật đồng thời trên một hàng thông qua optimistic locking. Nó **không tự chống submit trùng**, không thay thế idempotency key, và không thay thế row claim/lease khi nhiều node xử lý chung một queue.

### 5.4. Tách transaction ghi trạng thái vào Spring bean riêng

`src/main/java/example/asyncresults/OutcomeStore.java`:

```java
package example.asyncresults;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutcomeStore {

    private final BusinessJobRepository repository;

    public OutcomeStore(BusinessJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public JobView enqueue(String payload) {
        BusinessJob job = BusinessJob.queued(
                UUID.randomUUID().toString(), payload);
        return JobView.of(repository.save(job));
    }

    @Transactional
    public String begin(String id) {
        BusinessJob job = repository.findById(id).orElseThrow();
        if (job.state() != JobState.QUEUED) {
            return null;
        }
        job.markRunning();
        return job.payload();
    }

    @Transactional
    public void succeed(String id, String result) {
        BusinessJob job = repository.findById(id).orElseThrow();
        job.markSucceeded(result);
    }

    @Transactional
    public void fail(String id, String errorCode) {
        BusinessJob job = repository.findById(id).orElseThrow();
        job.markFailed(errorCode);
    }

    @Transactional(readOnly = true)
    public Optional<JobView> find(String id) {
        return repository.findById(id).map(JobView::of);
    }
}
```

Mỗi phương thức cập nhật trên `OutcomeStore` được bean khác gọi **qua proxy Spring**, nên transaction tương ứng commit trước khi lời gọi hoàn tất. Trong `succeed`, thay đổi `result` và `status` cùng một entity nằm trong **một transaction**; nếu transaction rollback, không thể có `SUCCEEDED` đã commit mà không có `result` do chính method này ghi. Kết quả được JPA dirty checking ghi khi transaction commit. Trường hợp hai worker cùng đọc `QUEUED`, `@Version` khiến một cập nhật cạnh tranh có thể thất bại thay vì âm thầm ghi đè.

### 5.5. Worker: commit kết quả trước, phát metric sau

`src/main/java/example/asyncresults/BusinessWorker.java`:

```java
package example.asyncresults;

import java.util.Locale;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class BusinessWorker {

    private static final Logger log =
            LoggerFactory.getLogger(BusinessWorker.class);

    private final OutcomeStore store;
    private final MeterRegistry meters;

    public BusinessWorker(OutcomeStore store, MeterRegistry meters) {
        this.store = store;
        this.meters = meters;
    }

    @Async("businessExecutor")
    public void process(String jobId) {
        String payload = store.begin(jobId);
        if (payload == null) {
            return; // Job đã được nhận hoặc đổi trạng thái.
        }

        String businessResult;
        try {
            if ("fail".equalsIgnoreCase(payload)) {
                throw new IllegalArgumentException("Demo business failure");
            }
            businessResult = payload.toUpperCase(Locale.ROOT);
        } catch (RuntimeException businessError) {
            log.warn("Business job failed, id={}", jobId, businessError);
            store.fail(jobId, "BUSINESS_FAILURE");
            meters.counter("business.jobs.finished",
                    "outcome", "failed").increment();
            return;
        }

        // Chỉ sau khi transaction lưu kết quả commit thành công:
        store.succeed(jobId, businessResult);
        meters.counter("business.jobs.finished",
                "outcome", "succeeded").increment();
        log.info("Business result persisted, id={}", jobId);
    }
}
```

**Vì sao không bắt mọi `Exception` rồi tăng `success_count`?** Trong đoạn trên, khối `try` chỉ bao quanh *bước tính toán nghiệp vụ*; lỗi từ `store.succeed(...)` (chẳng hạn DB mất kết nối) không bị giả vờ thành một lỗi nghiệp vụ đã được ghi nhận. Job có thể còn `RUNNING` và cần reconciliation. Vì `@Async` trả `void`, exception chưa xử lý không được ném ngược về HTTP caller; mặc định Spring ghi log hoặc có thể cấu hình `AsyncUncaughtExceptionHandler`. Dù có handler, **log lỗi vẫn không thay thế bản ghi kết quả**.

Counter được tăng **sau khi lời gọi lưu hoàn tất**. Đây là nguyên tắc đúng cho metric đếm những lần commit thành công trong demo; tuy nhiên nếu tiến trình chết giữa DB commit và tăng counter thì metric vẫn có thể thiếu, nên không dùng counter làm sổ cái. Không đặt `jobId` hoặc dữ liệu người dùng làm tag metric vì độ phân biệt quá cao; đặt `jobId` ở log có kiểm soát quyền truy cập.

### 5.6. API: 202 là đã nhận để xử lý, GET đọc DB

`src/main/java/example/asyncresults/JobController.java`:

```java
package example.asyncresults;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final OutcomeStore store;
    private final BusinessWorker worker;

    public JobController(OutcomeStore store, BusinessWorker worker) {
        this.store = store;
        this.worker = worker;
    }

    @PostMapping
    public ResponseEntity<JobView> submit(
            @RequestBody CreateJobRequest request) {

        String payload = request == null ? null : request.payload();
        if (payload == null || payload.isBlank()
                || payload.strip().length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "payload must contain 1..500 characters");
        }

        JobView queued = store.enqueue(payload.strip());
        try {
            worker.process(queued.id());
        } catch (TaskRejectedException rejected) {
            store.fail(queued.id(), "EXECUTOR_REJECTED");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(store.find(queued.id()).orElseThrow());
        }

        return ResponseEntity.accepted().body(queued);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobView> get(@PathVariable String id) {
        return store.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record CreateJobRequest(String payload) {
    }
}
```

Trong tình huống executor từ chối task, ví dụ cố ghi `FAILED` với mã `EXECUTOR_REJECTED` và trả `503 Service Unavailable`. Nếu **bản thân DB cũng lỗi**, việc ghi trạng thái có thể thất bại; không được hiểu trả lời HTTP hoặc log là bằng chứng đã lưu `FAILED`. Khi hệ thống tải lớn, cần giới hạn và chính sách backpressure rõ ràng.

### 5.7. Chạy và tự quan sát

Yêu cầu JDK 17+, Maven và thư mục project có đúng các file đã liệt kê. Từ thư mục `async-result-demo`:

```bash
mvn spring-boot:run
```

Mở một terminal PowerShell khác:

```powershell
$created = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/jobs" -ContentType "application/json" -Body '{"payload":"spring concurrency"}'
$created
Invoke-RestMethod -Method Get -Uri ("http://localhost:8080/jobs/" + $created.id)
```

POST trả HTTP 202 với `status=QUEUED` trong response ban đầu và `id` dạng UUID. GET có thể tạm trả `QUEUED`/`RUNNING` nếu worker chưa hoàn tất; sau đó trả `status=SUCCEEDED`, `result=SPRING CONCURRENCY` và `errorCode=null`. UUID và thời điểm thay đổi giữa các lần chạy, vì vậy không có một stdout/JSON cố định cho toàn bộ lần chạy.

Kiểm tra nhánh thất bại:

```powershell
$failed = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/jobs" -ContentType "application/json" -Body '{"payload":"fail"}'
Invoke-RestMethod -Method Get -Uri ("http://localhost:8080/jobs/" + $failed.id)
```

Sau khi worker xử lý, trạng thái sẽ là `FAILED` với `errorCode=BUSINESS_FAILURE`. Truy vấn metric sau khi đã có lần thực thi hoàn tất:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/business.jobs.finished"
```

Thử **dừng ứng dụng rồi khởi động lại tại cùng working directory** và GET lại `$created.id`: bản ghi `SUCCEEDED` và kết quả vẫn được đọc từ tệp H2. Metric trong memory registry có thể không còn số đếm cũ. Từ đó thấy: **DB giữ business outcome; metric mô tả hoạt động quan sát được trong một khoảng thời gian**.

Lưu ý demo chưa tự động chạy lại các bản ghi `QUEUED`/`RUNNING` tồn đọng sau crash. Việc bản ghi *tồn tại* không có nghĩa công việc chắc chắn sẽ *được phát lại*; phần sau giải thích cơ chế còn thiếu.

## 6. Những khoảng trống cần xử lý trong production

### 6.1. Mất task ở khoảng giữa commit và dispatch

Nếu `enqueue()` đã commit `QUEUED`, nhưng tiến trình chết trước khi `worker.process(...)` đẩy vào executor, ta có **bản ghi bền vững nhưng không còn task đang chạy**. Metric có thể hoàn toàn không báo lỗi.

Cách xử lý: dùng **transactional outbox** (lưu yêu cầu nghiệp vụ + sự kiện cần gửi trong một transaction, một publisher phát lại có checkpoint) hoặc **DB-backed work queue** với worker polling, claim có điều kiện, lease/heartbeat và quy trình lấy lại job quá hạn. Nếu dùng broker, chỉ ACK sau khi dữ liệu cần thiết được commit; việc delivery thường phải thiết kế cho **at-least-once**.

### 6.2. Worker chết giữa chừng

`RUNNING` tồn tại lâu không đồng nghĩa “đang bận bình thường”. Lưu `started_at`/`updated_at` hoặc lease expiry và định kỳ quét các job quá hạn. Chính sách có thể là retry với attempt mới, đối soát trạng thái bên ngoài hoặc đưa vào hàng chờ xử lý thủ công. **Đừng tùy tiện đổi mọi `RUNNING` quá hạn sang `FAILED`** nếu hành động bên ngoài có thể đã hoàn tất nhưng chưa lưu được kết quả.

### 6.3. Side effect đã xảy ra nhưng ghi kết quả thất bại

Giả sử worker đã gọi cổng thanh toán hoặc gửi email, sau đó DB lỗi trước khi ghi `SUCCEEDED`. Retry “chạy lại từ đầu” có thể thanh toán/gửi email hai lần. Với side effect bên ngoài:

- Dùng **idempotency key** ổn định cho ý định nghiệp vụ và truyền khóa đó đến hệ thống hỗ trợ deduplication.
- Lưu record quyết định và sự kiện cần phát theo **transactional outbox**; consumer cần idempotent/inbox khi có redelivery.
- Nếu không có giao dịch nguyên tử giữa các hệ thống, thiết kế trạng thái `UNKNOWN`/`RECONCILING` và bước đối soát, thay vì khẳng định exactly-once.
- Ràng buộc unique constraint/conditional update ở DB bảo vệ các chuyển trạng thái quan trọng. `@Version` chỉ giải quyết một phần xung đột ghi.

Đoạn tính chữ in hoa trong ví dụ là phép biến đổi thuần, nên không có side effect ngoài DB; **không** suy rộng sự đơn giản đó sang thanh toán hoặc gửi thông báo thực.

### 6.4. Transaction bị rollback nhưng log/metric đã ghi

Sai về mặt thứ tự:

```java
// Ví dụ PHẢN DIỆN: metric không chứng minh transaction đã commit.
@Transactional
public void finishBad(String id, String result) {
    repository.saveResult(id, result); // API giả định, minh họa thứ tự
    counter.increment();
    log.info("finished {}", id);
    // Nếu commit xảy ra sau method return và thất bại,
    // log/counter đã báo xong nhưng DB không có kết quả.
}
```

Đây là **mã minh họa rời**, không phải class runnable (thiếu repository/counter/log). Cách phù hợp: đặt giao dịch trong một bean riêng, cho lời gọi bean đó kết thúc và commit trước khi emit metric; hoặc dùng event/listener sau commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`) cho telemetry phù hợp. **Ngay cả sau commit**, telemetry có thể mất nếu tiến trình chết, nên trạng thái trong DB vẫn là nguồn sự thật.

### 6.5. `@Scheduled`/Quartz cũng cần kết quả riêng

`@Scheduled` chỉ kích hoạt method theo lịch. Quartz `JobExecutionContext`, trigger history hoặc JDBC JobStore phục vụ scheduling; nếu một job chạy hàng ngày tạo file hay giao dịch nghiệp vụ, hãy lưu bảng **run/attempt/result** theo `jobId` hoặc `scheduleId + fireTime` (theo hợp đồng định danh rõ ràng). Không nhầm “scheduler thấy Job đã fire” với “đã commit báo cáo thành công”. Với cluster, cần cơ chế tránh xử lý trùng và đối soát theo ý định nghiệp vụ.

## 7. Giám sát đúng cách trên nền dữ liệu đã lưu

Dù không thay thế persistence, giám sát vẫn thiết yếu để **phát hiện** sai lệch và **điều tra**. Chọn tín hiệu từ hai nguồn:

| Nhóm | Ví dụ có ích | Cách dùng |
| --- | --- | --- |
| Runtime | executor active threads, queue depth, rejections, thời gian xử lý | Cảnh báo nghẽn tài nguyên, saturation, lỗi thread pool. |
| Ứng dụng | counter succeeded/failed, duration histogram theo loại job | Theo dõi throughput, latency, failure rate; tag có cardinality thấp. |
| Dữ liệu nghiệp vụ | số job `QUEUED` quá hạn, `RUNNING` quá lease, trạng thái kết quả thiếu | Phát hiện job bị kẹt/mất dispatch cần đối soát. |
| Log/trace | correlation `jobId`, attempt, lỗi commit, external request ID | Điều tra đường đi và nguyên nhân; bảo vệ dữ liệu nhạy cảm. |
| SLO | % job kết thúc trong thời hạn, backlog cũ nhất, tỷ lệ đối soát thất bại | Cảnh báo tác động đến người dùng, dựa trên kết quả đã commit. |

Không đặt payload chứa dữ liệu cá nhân vào log hay nhãn metric. Không suy ra mỗi increment metric tương ứng chính xác với một kết quả nếu producer, scrape/export hoặc registry có thể thất bại. Khi đếm “số kết quả đã lưu thật”, truy vấn hoặc tổng hợp có kiểm soát từ DB, định nghĩa rõ thời điểm commit và xử lý duplicate.

## 8. Các lỗi thường gặp

1. **Chỉ lưu `CompletableFuture` trong `Map<UUID, Future>`**: tiện cho demo trong một tiến trình nhưng mất khi restart, không chia sẻ giữa nhiều instance và không có lịch sử lâu dài.
2. **Trả `200 OK` hoặc `202 Accepted` với thông điệp “thành công” ngay khi `@Async` được gọi**: nhầm accepted với succeeded. Hãy trả job ID và trạng thái, tra cứu kết quả sau.
3. **Đưa `@Transactional` lên controller rồi nghĩ worker thừa hưởng transaction**: transaction imperative không tự xuyên thread. Cần giao dịch riêng ở service worker; tránh self-invocation không qua proxy.
4. **Chỉ nhìn log `"finished"` hoặc Actuator metrics**: log có thể trước commit, metric có thể reset; dùng DB để kết luận outcome.
5. **Bắt mọi exception rồi set `SUCCEEDED` ở `finally`**: trạng thái giả, có thể mất kết quả hoặc che lỗi DB. Chỉ đánh dấu thành công sau khi dữ liệu tương ứng đã commit.
6. **Retry mà không có idempotency/claim**: có thể chạy trùng hoặc ghi đè kết quả; cần unique key, conditional transition, locking và đối soát khi side effect không thể đảo ngược.
7. **Có bảng `QUEUED` nhưng không có cơ chế phục hồi dispatch**: persisted row không tự chạy; dùng durable queue/outbox/DB polling phù hợp.
8. **Dùng status `FAILED` cho mọi timeout**: timeout có thể là “không biết kết quả” khi external system đã xử lý. Xây dựng quy trình xác nhận thay vì kết luận thiếu bằng chứng.

## 9. Checklist kiểm chứng trong một hệ thống thực

1. Gọi API khởi tạo job; **quan sát bản ghi `QUEUED` đã commit** trước khi response `202`.
2. Tắt worker hoặc làm đầy executor; xác minh request không bị tuyên bố là thành công, trạng thái rejected/đợi được ghi theo hợp đồng.
3. Thực hiện job thành công; kiểm tra `result` và `SUCCEEDED` được commit atomically trong cùng DB.
4. Mô phỏng lỗi nghiệp vụ; kiểm tra `FAILED` + error code phù hợp, không đưa stack trace thô vào response.
5. Dừng process rồi khởi động lại; đọc lại kết quả đã commit mà không phụ thuộc RAM hay metrics.
6. Mô phỏng crash ở từng ranh giới: sau enqueue, sau begin, sau side effect, trước/sau result commit. Quyết định rõ retry, lease timeout, reconciliation và idempotency cho từng trường hợp.
7. Chạy đồng thời hai worker cùng claim một job; kiểm tra version/conditional update ngăn chuyển trạng thái sai.
8. Tắt hoặc ngắt exporter metric; xác minh business outcome vẫn được ghi. Ngược lại, làm DB thất bại; xác minh log/metric không được dùng để tuyên bố business success.

## 10. Kết luận và tài liệu

**Quy tắc thiết kế:** *Persist the outcome; observe the execution.* Trong Spring concurrency, executor, scheduler và trace mô tả hoạt động; giao dịch và mô hình trạng thái nghiệp vụ xác định kết quả có thể tin cậy, truy vấn lại và đối soát. Để sống qua restart, cần persistence; để không mất công việc sau commit, cần durable dispatch/recovery; để xử lý retry mà không lặp side effect, cần idempotency và reconciliation.

Tài liệu tham khảo chính thức:

- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Async`, executor, exception với `void`.
- [Spring Framework – Declarative Transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html): transaction theo thread và proxy.
- [Spring Framework – Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): propagation, rollback và self-invocation.
- [Spring Boot 3.5 – Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html): executor mặc định và tùy chỉnh.
- [Spring Boot – Actuator](https://docs.spring.io/spring-boot/how-to/actuator.html): giám sát và metrics.


</details>

- [Quay lại đầu trang](#back-to-top)
