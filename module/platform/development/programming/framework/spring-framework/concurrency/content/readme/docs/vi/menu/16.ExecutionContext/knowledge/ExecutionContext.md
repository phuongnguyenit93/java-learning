<a id="back-to-top"></a>

# Ngữ cảnh thực thi

## Menu
- [ThreadLocal không tự theo công việc sang nhóm luồng khác](#execution-context-001)
- [Nguy cơ dữ liệu ngữ cảnh của tác vụ trước bị dùng lại](#execution-context-002)
- [TaskDecorator bao tác vụ để thiết lập và dọn ngữ cảnh](#execution-context-003)
- [Chụp ngữ cảnh ở bên gửi rồi khôi phục trạng thái cũ bằng try-finally](#execution-context-004)
- [Truyền mã liên kết yêu cầu và ngữ cảnh ghi nhật ký](#execution-context-005)
- [ContextPropagatingTaskDecorator và điều kiện tích hợp thư viện ngữ cảnh](#execution-context-006)
- [Đánh giá chi phí trang trí với số lượng tác vụ rất nhỏ](#execution-context-007)
- [Không giữ toàn bộ đối tượng request sau khi yêu cầu đã kết thúc](#execution-context-008)
- [Phân biệt ngữ cảnh bảo mật cần bộ hỗ trợ riêng với ThreadLocal tùy ý](#execution-context-009)
- [Không dùng InheritableThreadLocal như giải pháp chung cho nhóm luồng tái sử dụng](#execution-context-010)

## <a id="execution-context-001">ThreadLocal không tự theo công việc sang nhóm luồng khác</a>

<details>
<summary>Xem chi tiết</summary>

# ThreadLocal không tự theo công việc sang nhóm luồng khác

## Mục tiêu

Trong ứng dụng Spring, thông tin về một lần xử lý như correlation ID, request ID, tài khoản đang thao tác hay dữ liệu để ghi log thường được lưu trong `ThreadLocal`. Điều này thuận tiện khi mọi lời gọi diễn ra **trên cùng một thread**. Nhưng `@Async`, `TaskExecutor`, `ExecutorService`, `CompletableFuture.supplyAsync(...)` và một số cơ chế xử lý song song có thể chuyển phần việc sang **một thread khác**. Khi ấy, `ThreadLocal` của thread gửi **không tự xuất hiện** ở thread nhận.

Đây là một vấn đề về **ngữ cảnh thực thi**, không phải lỗi của `@Async`. Hiểu sai có thể gây mất correlation ID, log không gắn được request, đọc nhầm người dùng, hoặc tệ hơn là **rò rỉ dữ liệu ngữ cảnh của tác vụ trước** khi thread pool tái sử dụng worker.

Bài này giúp bạn:

1. Giải thích chính xác `ThreadLocal` gắn giá trị với **thread**, không với task, `Future`, HTTP request hay một Spring bean.
2. Hiểu lý do `ThreadLocal.get()` có thể có giá trị ở caller nhưng trả `null` ở worker chạy `@Async`.
3. Phân biệt `ThreadLocal`, `InheritableThreadLocal`, truyền dữ liệu bằng tham số và cơ chế context propagation có chủ đích.
4. Dùng `TaskDecorator` của Spring để **chụp** context lúc gửi task, **gắn** vào worker khi chạy, và **khôi phục/xóa** trong `finally`.
5. Chạy một chương trình Java 17+ với Spring Framework 6.2.x chứng minh cả trường hợp context mất, context được truyền có chủ đích và ngăn rò rỉ qua một worker được tái sử dụng.
6. Nhận diện những context **không nên sao chép mù quáng**, đặc biệt transaction, `EntityManager`, request-scoped object và security state mutable.

**Thông điệp chính:** `ThreadLocal` giải quyết việc truy cập dữ liệu **theo thread hiện tại**. Executor giải quyết việc thực thi **theo task**. Nếu muốn đưa một phần ngữ cảnh từ caller sang worker, phải thiết kế **chuyển giao context** tại ranh giới submit và cleanup tại ranh giới kết thúc task.

## 1. ThreadLocal lưu dữ liệu theo thread như thế nào?

`java.lang.ThreadLocal<T>` cung cấp các API cơ bản:

```java
ThreadLocal<String> requestId = new ThreadLocal<>();

requestId.set("req-123");
String sameThread = requestId.get(); // "req-123"
requestId.remove();                  // xóa binding trên thread hiện tại
```

Đây là đoạn Java đơn giản bên trong một method, không phải chương trình độc lập. Một object `ThreadLocal` dùng chung **không** có nghĩa các thread dùng chung một giá trị. Mỗi thread có binding riêng đối với cùng key `ThreadLocal`.

Mô hình:

```text
                  CÙNG MỘT ThreadLocal<String> KEY
                                  |
     +----------------------------+----------------------------+
     |                            |                            |
 request-thread-1             pool-worker-1                pool-worker-2
 key -> "req-A"               key -> null                  key -> "req-old"
     |                            |                            |
 get() -> "req-A"             get() -> null                get() -> "req-old"
```

`new ThreadLocal<>()` có `initialValue()` mặc định là `null`; thread chưa từng `set` key đó sẽ đọc được `null`. Nếu dùng `ThreadLocal.withInitial(supplier)`, một thread có thể nhận **giá trị khởi tạo riêng của nó**, nhưng đó vẫn **không phải** giá trị được copy tự động từ caller.

Một biến `static final ThreadLocal<String> ID` thường là **key chung** được mọi thread tham chiếu. Giá trị vẫn ở trong vùng lưu riêng của từng thread. Đừng nhầm `static` ở key với `static String currentRequestId` (một biến giá trị dùng chung, không an toàn nếu các request đồng thời ghi đè).

### ThreadLocal không phải kho dữ liệu cho một request

Một request HTTP đôi khi được xử lý hoàn toàn bởi một Servlet thread nên các thao tác cùng thread đọc `ThreadLocal` tương ứng. Nhưng khi request được dispatch qua worker, chuyển sang callback, lên lịch retry hoặc tham gia xử lý bất đồng bộ, việc giữ nguyên request identity **không còn được bảo đảm bằng ThreadLocal thông thường**. Nó cũng không phù hợp để lưu kết quả nghiệp vụ dài hạn hoặc chia sẻ state qua nhiều tiến trình.

## 2. Vì sao @Async không tự mang ThreadLocal sang worker?

Hãy hình dung code ở một controller/service:

```java
ContextHolder.TRACE_ID.set("trace-A");

CompletableFuture<String> answer = service.runAsync();
```

Đây là snippet giải thích hành vi; `ContextHolder`, `service`, hạ tầng Spring và các kiểu import được định nghĩa trong chương trình đầy đủ phía sau.

Nếu `service` là bean Spring có method `@Async("workerExecutor")`, lời gọi qua proxy thường được gửi sang executor:

```text
CALLER THREAD                            WORKER THREAD
--------------------------------        --------------------------------
TRACE_ID.set("trace-A")
TRACE_ID.get() == "trace-A"

call service.runAsync()
    |
    +-- Spring async proxy
    +-- executor.submit(task) ----------> worker nhận task
                                         TRACE_ID.get() == null (*)
                                         chạy thân runAsync()
    |
    +-- caller nhận Future
    |
caller vẫn giữ "trace-A"                 worker có map ThreadLocal riêng

(*) Giả định worker chưa được gắn giá trị cho key đó và ThreadLocal
    không có initialValue tùy chỉnh.
```

`@Async` chủ yếu điều phối **lời gọi phương thức** sang executor. Nó không tự sao chép tất cả thread-local variables, HTTP session state hay transaction từ caller. Tùy framework, một số loại context có wrapper/propagation chuyên biệt, nhưng **không được mặc định** rằng mọi ThreadLocal được chuyển đi.

Còn nếu `@Async` không kích hoạt vì thiếu `@EnableAsync` hoặc self-invocation `this.runAsync()`, thân method có thể chạy ngay trên caller. Lúc đó `ThreadLocal.get()` thấy giá trị caller, nhưng **đó không phải bằng chứng có propagation thành công**: thực tế có thể chưa hề chuyển thread.

### Phân biệt Java `Executor` và Spring `@Async`

- `Executor.execute(...)` nhận một `Runnable` và thực thi theo policy của executor. Có executor chạy trực tiếp trên caller (`Runnable::run`), có executor dùng pool.
- `ExecutorService.submit(...)` thường trả `Future`, nhưng `Future` **không mang** `ThreadLocal` của thread gửi theo hợp đồng.
- `@Async` trong Spring dùng proxy/interceptor để submit lời gọi tới executor, rồi trả completion handle; quy tắc dữ liệu theo thread vẫn như JDK.
- `CompletableFuture.supplyAsync(...)` lập task theo executor mặc định của JDK hoặc executor được truyền. Supplier có thể chạy trên thread không có `ThreadLocal` caller.
- Với callback `thenApply`, `whenComplete`, `handle`, code callback có thể chạy trên thread hoàn tất stage hoặc thread gọi đăng ký callback nếu stage đã hoàn tất. Context của callback phải được xác định theo chính thread thực thi, **không** theo thread đã tạo future lúc đầu.

## 3. Nguy cơ ở thread pool: thiếu context và rò rỉ context cũ

Thread pool **tái sử dụng** worker cho nhiều task:

```text
Request A:
  submit Task A -> worker-1
  Task A: TRACE_ID.set("A")
  Task A: quên remove()

Request B:
  submit Task B -> worker-1
  Task B: TRACE_ID.get() -> "A"  !!! nếu không reset

Request C:
  submit Task C -> worker-1
  Task C: có thể nhìn thấy giá trị để lại từ B
```

Nếu task tự `set` rồi quên cleanup, trạng thái tồn tại trên worker cho tới khi được xóa/thay thế hoặc thread kết thúc. Trong server lâu sống, đây là lỗi khó phát hiện: log của người dùng B có thể mang trace ID của A; với thông tin bảo mật, hậu quả nghiêm trọng hơn.

Hai vấn đề đối xứng:

1. **Không có dữ liệu đúng**: caller có `"B"` nhưng worker có `null` vì không truyền.
2. **Có dữ liệu sai**: worker giữ `"A"` từ task cũ vì không dọn, rồi Task B đọc nhầm.

Một cơ chế propagation đúng cần xử lý **cả hai**: thiết lập snapshot đúng cho task hiện tại, kể cả snapshot là `null`, và khôi phục trạng thái worker trước đó sau task.

Không nên chỉ viết:

```java
executor.execute(() -> {
    TRACE_ID.set(captured);
    doWork();
});
```

Đây là ví dụ thiếu cleanup, không phải mẫu nên dùng. Nếu `doWork()` ném exception, worker vẫn còn binding. Nếu `captured` null và ta muốn xóa binding, gọi `set(null)` chưa thể hiện rõ ý nghĩa remove, và còn có vấn đề khi `ThreadLocal.withInitial` cần tái khởi tạo. Thực hành tốt là dùng `try/finally`.

## 4. InheritableThreadLocal có cứu được không?

`InheritableThreadLocal<T>` là subclass của `ThreadLocal<T>`: khi **tạo một child thread mới**, runtime có thể cho thread đó một **giá trị ban đầu được kế thừa** từ parent tại thời điểm tạo thread. Đây không phải cơ chế “copy mỗi lần submit task”.

Ví dụ thuần Java 17:

```java
InheritableThreadLocal<String> inherited = new InheritableThreadLocal<>();
inherited.set("parent-A");

Thread child = new Thread(() ->
    System.out.println(inherited.get())
);
child.start();
child.join();
inherited.remove();
```

Đoạn này đặt trong method có xử lý `InterruptedException`, chạy thành công sẽ in:

```text
parent-A
```

Vì sao cách này **không phù hợp để truyền request context qua thread pool**?

- Pool worker có thể được tạo từ lâu **trước** request mới, nên nó không nhận giá trị cập nhật ở thread submit.
- Nếu worker tình cờ được tạo khi request A đang active, nó có thể kế thừa `"A"` và **giữ giá trị đó** cho request B/C, trừ khi ứng dụng có cleanup đúng.
- Tạo thread mới hay tái sử dụng thread là quyết định của executor. Không nên thiết kế đúng/sai ngữ cảnh dựa vào timing bất định này.
- Với mutable objects, inheritance có thể sao chép **tham chiếu**, không nhất thiết deep copy; các thread cùng sửa có thể gây race/data leak.
- Một số thread factory có thể chọn không kế thừa inheritable thread locals. Không nên đặt giả định phổ quát khi code chạy trên container hoặc thư viện quản lý worker.

Vì vậy, **đừng** thay `ThreadLocal` bằng `InheritableThreadLocal` như một cách sửa nhanh cho `@Async` hay pool. Tính đúng đắn cần dựa vào propagation theo **task submission**, không theo **thread construction**.

## 5. Truyền tham số tường minh thường là lựa chọn an toàn nhất

Nếu dữ liệu cần dùng được biết ở caller, cách dễ hiểu nhất là đưa nó thành tham số hoặc một request context immutable:

```java
public record JobContext(String correlationId, String tenantId) {}

@Async("workerExecutor")
public CompletableFuture<String> process(JobContext context, String payload) {
    return CompletableFuture.completedFuture(
        context.correlationId() + ":" + payload
    );
}
```

Đây là snippet của một Spring bean, cần `@EnableAsync`, bean `workerExecutor`, import và lời gọi qua proxy. Không phải file độc lập.

**Ưu điểm:** Không cần trạng thái ẩn, có thể đọc đúng thông tin mà task nhận, dễ test, thích hợp với công việc kéo dài qua request và qua message queue. Nếu task chạy sau khi caller hết request, thông tin cần thiết vẫn nằm trong DTO được truyền có chủ đích.

**Lưu ý:** Chỉ truyền dữ liệu cần thiết; không truyền tham chiếu tới HTTP request object, security mutable internals, transaction-bound resource hoặc JPA `EntityManager` sang worker. Khi giá trị của record là một object mutable, record **không tự tạo deep immutability**. Cần xét copy/ownership.

`ThreadLocal` vẫn hữu ích cho những API logging/tracing sử dụng context theo thread mà ta không thể thêm tham số vào mọi method. Khi đó nên thiết kế một bridge rõ ràng qua `TaskDecorator` hoặc wrapper tương đương.

## 6. TaskDecorator: chụp lúc gửi, gắn lúc chạy, dọn lúc kết thúc

Spring cung cấp `org.springframework.core.task.TaskDecorator`, một interface dùng để bọc `Runnable` **trước khi executor thực thi**. `ThreadPoolTaskExecutor` có setter `setTaskDecorator(...)`. Mục đích chính là bổ sung execution context hoặc theo dõi việc thực thi.

Mẫu an toàn cho một `ThreadLocal<String>`:

```java
executor.setTaskDecorator(task -> {
    String captured = ContextHolder.TRACE_ID.get(); // tại thread submit
    return () -> {
        String previous = ContextHolder.TRACE_ID.get(); // tại worker
        try {
            if (captured == null) {
                ContextHolder.TRACE_ID.remove();
            } else {
                ContextHolder.TRACE_ID.set(captured);
            }

            task.run();
        } finally {
            // Khôi phục trạng thái của worker, kể cả khi task thất bại.
            if (previous == null) {
                ContextHolder.TRACE_ID.remove();
            } else {
                ContextHolder.TRACE_ID.set(previous);
            }
        }
    };
});
```

Đây là đoạn cấu hình trong method có `ThreadPoolTaskExecutor executor` và `ContextHolder`, không phải Java file độc lập. Đoạn code đầy đủ ở phần kế tiếp sử dụng đúng nguyên lý.

### Giải thích từng điểm

1. **Lúc decorator được gọi ở thread submit:** `captured` là một snapshot của giá trị cần truyền. Nếu caller đổi `ThreadLocal` ngay sau submit, task vẫn dùng snapshot trước đó.
2. **Lúc wrapper chạy trong worker:** `previous` lưu binding cũ của **worker**, không phải của caller. Điều này giúp xử lý đúng khi có wrapper lồng nhau hoặc executor có context hợp lệ trước đó.
3. **Trước `task.run()`:** set giá trị captured, hoặc remove khi caller không có context. Nhờ vậy task B không nhìn thấy giá trị A để lại.
4. **Trong `finally`:** luôn khôi phục trạng thái worker trước khi wrapper chạy. Không phụ thuộc kết quả thành công hay exception.
5. **Không làm thay đổi caller:** code gắn và cleanup chỉ được chạy trên worker. Caller vẫn tự chịu trách nhiệm cleanup `ThreadLocal` của chính nó khi kết thúc request.
6. **Không tự tăng worker hoặc retry:** decorator chỉ bọc callback, không đổi chính sách queue/threads/rejection và không tự giải quyết lỗi nghiệp vụ.

Nếu muốn `TaskDecorator` luôn để worker sạch ngay cả khi có giá trị `previous` không mong muốn, cần quyết định ownership và yêu cầu từ các framework khác. Trong mô hình bọc callback có khả năng lồng nhau, **khôi phục giá trị trước đó** là cách ít phá hủy ngữ cảnh của lớp bao ngoài. Nhưng trạng thái rác đã có trước khi decorator chạy cần được xử lý từ nguồn phát sinh, không thể lấy “khôi phục previous” làm cách chữa rò rỉ có sẵn.

**Quan trọng khi dùng `submit` / `Future`:** `TaskDecorator` có thể được gọi trên callback wrapper như `FutureTask`, không trực tiếp trên `Runnable` của người dùng. Lỗi từ computation có thể được giữ bên trong `FutureTask` và **không ném ra** từ `Runnable.run()` của decorator. Vì vậy `try/catch` quanh `task.run()` trong decorator **không phải** cơ chế đầy đủ để quan sát task failure. Hãy dùng `Future`, completion callbacks, `AsyncUncaughtExceptionHandler` hoặc logging/metrics có hợp đồng rõ.

## 7. Ví dụ hoàn chỉnh Java 17+ và Spring Framework 6.2

Ví dụ thực hiện ba phép kiểm chứng:

- **Không propagation:** caller có trace `"req-A"`, lời gọi `@Async("plainExecutor")` dùng worker riêng và thấy `null`.
- **Có propagation:** `@Async("contextExecutor")` chạy cùng kiểu công việc nhưng `TaskDecorator` chụp `"req-A"` rồi gắn vào worker; worker đọc đúng.
- **Không rò rỉ qua reuse:** caller đổi context sang `"req-B"`, rồi xóa context; một worker của `contextExecutor` được dùng **ba lần** lần lượt thấy `"req-A"`, `"req-B"` và `null`, không giữ giá trị cũ.

Ta dùng một worker cố định cho mỗi pool và `Future.get(...)` để đảm bảo thứ tự quan sát. Không dựa vào `Thread.sleep()` hoặc sự cạnh tranh thời gian giữa các worker.

### Cấu trúc thư mục

```text
threadlocal-propagation-demo/
├── pom.xml
└── src/main/java/example/ThreadLocalPropagationDemo.java
```

Tạo tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>threadlocal-propagation-demo</artifactId>
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
                    <mainClass>example.ThreadLocalPropagationDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `src/main/java/example/ThreadLocalPropagationDemo.java`:

```java
package example;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ThreadLocalPropagationDemo {

    // Chỉ có key dùng chung; mỗi thread giữ giá trị riêng.
    public static class TraceContext {
        static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    }

    public record Reading(String traceId, String threadName) {
    }

    public static class WorkService {
        @Async("plainExecutor")
        public CompletableFuture<Reading> plainRead() {
            return CompletableFuture.completedFuture(
                new Reading(
                    TraceContext.TRACE_ID.get(),
                    Thread.currentThread().getName()
                )
            );
        }

        @Async("contextExecutor")
        public CompletableFuture<Reading> decoratedRead() {
            return CompletableFuture.completedFuture(
                new Reading(
                    TraceContext.TRACE_ID.get(),
                    Thread.currentThread().getName()
                )
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {
        @Bean
        public WorkService workService() {
            return new WorkService();
        }

        @Bean("plainExecutor")
        public ThreadPoolTaskExecutor plainExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(1);
            pool.setQueueCapacity(4);
            pool.setThreadNamePrefix("plain-");
            return pool;
        }

        @Bean("contextExecutor")
        public ThreadPoolTaskExecutor contextExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(1);
            pool.setMaxPoolSize(1);
            pool.setQueueCapacity(4);
            pool.setThreadNamePrefix("ctx-");

            pool.setTaskDecorator(task -> {
                // Chụp context từ thread đang submit task.
                String captured = TraceContext.TRACE_ID.get();

                return () -> {
                    String previous = TraceContext.TRACE_ID.get();
                    try {
                        if (captured == null) {
                            TraceContext.TRACE_ID.remove();
                        } else {
                            TraceContext.TRACE_ID.set(captured);
                        }
                        task.run();
                    } finally {
                        if (previous == null) {
                            TraceContext.TRACE_ID.remove();
                        } else {
                            TraceContext.TRACE_ID.set(previous);
                        }
                    }
                };
            });

            return pool;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            WorkService service = context.getBean(WorkService.class);

            // Một "request" giả lập đặt ThreadLocal ở caller.
            TraceContext.TRACE_ID.set("req-A");
            try {
                Reading plain = service.plainRead()
                    .get(5, TimeUnit.SECONDS);
                Reading copiedA = service.decoratedRead()
                    .get(5, TimeUnit.SECONDS);

                // Một request khác đi qua cùng worker ctx.
                TraceContext.TRACE_ID.set("req-B");
                Reading copiedB = service.decoratedRead()
                    .get(5, TimeUnit.SECONDS);

                // Caller không có context; worker phải không nhìn thấy
                // giá trị từ hai task trước.
                TraceContext.TRACE_ID.remove();
                Reading withoutContext = service.decoratedRead()
                    .get(5, TimeUnit.SECONDS);

                boolean reusedWorker =
                    copiedA.threadName().equals(copiedB.threadName())
                    && copiedB.threadName().equals(
                        withoutContext.threadName()
                    );

                System.out.println("plainMissing="
                    + (plain.traceId() == null));
                System.out.println("firstCopied=" + copiedA.traceId());
                System.out.println("secondCopied=" + copiedB.traceId());
                System.out.println("afterCallerClearMissing="
                    + (withoutContext.traceId() == null));
                System.out.println("plainPoolUsed="
                    + plain.threadName().startsWith("plain-"));
                System.out.println("contextPoolUsed="
                    + copiedA.threadName().startsWith("ctx-"));
                System.out.println("sameWorkerReused=" + reusedWorker);
                System.out.println("callerClean="
                    + (TraceContext.TRACE_ID.get() == null));
            } finally {
                // Caller thread cũng phải tự dọn context của chính nó.
                TraceContext.TRACE_ID.remove();
            }
        }
    }
}
```

Chạy tại thư mục `threadlocal-propagation-demo` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java
```

Tám dòng do **chương trình** in ra theo thứ tự, khi chạy thành công:

```text
plainMissing=true
firstCopied=req-A
secondCopied=req-B
afterCallerClearMissing=true
plainPoolUsed=true
contextPoolUsed=true
sameWorkerReused=true
callerClean=true
```

### Tại sao kết quả này có thể kiểm chứng?

1. Spring context tạo `WorkService` bean, hai `ThreadPoolTaskExecutor` bean và bật `@Async`. `WorkService` là một class không có interface và có public methods, vì vậy lời gọi từ `context.getBean(...)` được async proxy xử lý.
2. Main đặt `"req-A"` trên **ThreadLocal của caller**, rồi gọi `plainRead()`. `plainExecutor` có worker `plain-...`; ThreadLocal **thông thường** không kế thừa từ main, nên `plain.traceId()` là `null`.
3. Gọi `decoratedRead()` trong cùng trạng thái caller. Khi task được submit, `TaskDecorator` của `contextExecutor` chụp `"req-A"`. Worker đặt snapshot, đọc giá trị và trả `Reading("req-A", "ctx-...")`.
4. Main chờ kết quả bằng `get` trước khi đổi giá trị sang `"req-B"`. Lần submit thứ hai chụp `"req-B"`, còn việc dùng lại worker không làm mất snapshot mới.
5. Main gọi `remove()` rồi submit lần thứ ba. Decorator chụp **giá trị không có** (`null`), nên wrapper xóa binding trước khi task chạy. Task đọc `null`, không đọc `req-B` cũ.
6. Vì mỗi pool `core=max=1` và các task được chờ tuần tự, ba task trang trí dùng cùng worker của `contextExecutor`; tên worker bằng nhau.
7. Tên thread được đặt prefix riêng nên hai phép kiểm tra `plainPoolUsed` và `contextPoolUsed` cho `true`. `finally` dọn caller ngay cả khi một lệnh chờ lỗi.
8. Các `println` được gọi **sau khi tất cả future đã hoàn tất**, không in cạnh tranh từ worker, nên stdout có thứ tự cố định.

Nếu môi trường không tải được dependency hoặc task không hoàn tất trước `get(5, SECONDS)`, chương trình báo lỗi thay vì in cả tám dòng; đó là vấn đề chạy trong môi trường, không phải thay đổi nguyên tắc ThreadLocal.

### Tại sao demo dùng một ThreadLocal tự định nghĩa?

Nó cho phép quan sát độc lập với Spring Security, servlet container, logging framework hoặc request scope. Nếu dùng `SecurityContextHolder` hay MDC, thư viện có thể đã có wrapper chuyên biệt hoặc cấu hình context propagation khác, làm việc chẩn đoán phức tạp hơn. Đầu tiên cần hiểu **ThreadLocal cơ bản**; sau đó chọn cơ chế chuyên biệt cho từng loại context.

`TraceContext` trong demo chỉ lưu một chuỗi giả lập, **không** lưu token bí mật hoặc quyền truy cập. Không dùng nguyên mẫu propagation một chuỗi request ID này để tuyên bố đã truyền đầy đủ, an toàn, mọi security context trong hệ thống thật.

## 8. Dọn ngữ cảnh: tại sao phải dùng try/finally?

Trong worker, exception có thể xảy ra ở bất kỳ thời điểm nào:

```java
String old = TRACE_ID.get();
try {
    TRACE_ID.set(captured);
    runPossiblyFailingTask();
} finally {
    if (old == null) {
        TRACE_ID.remove();
    } else {
        TRACE_ID.set(old);
    }
}
```

Đây là snippet, yêu cầu `TRACE_ID`, `captured` và `runPossiblyFailingTask` được định nghĩa ở method. Dù task trả bình thường, ném `RuntimeException` hay có đường code `return` bên trong try, `finally` vẫn được thực thi trong các tình huống Java thông thường.

Nếu wrapper chỉ gọi `TRACE_ID.remove()` trong finally mà quên khôi phục `old`, bạn có thể phá context của một lớp bao ngoài trên chính worker (ví dụ decorator lồng nhau). Nếu wrapper chỉ khôi phục `old` nhưng `old` đã là dữ liệu rác từ task trước, nguồn rò rỉ vẫn cần sửa. Quy tắc thiết kế đúng là **mỗi thành phần chịu trách nhiệm với binding mà nó tự đặt**, còn nơi sở hữu thread pool phải có chính sách tránh stale context.

`ThreadLocal.remove()` rất quan trọng với thread pool lâu sống vì giá trị còn gắn với thread cho đến khi được xóa, thay thế hoặc thread kết thúc. Đối với dữ liệu lớn, quên cleanup vừa gây memory retention vừa có nguy cơ lộ nhầm dữ liệu giữa các request.

### Có nên truyền nguyên object mutable không?

Không nên. Nếu caller chụp một tham chiếu mutable rồi tiếp tục thay đổi nó, worker có thể nhìn thấy state không nhất quán. `TaskDecorator` capture một **reference** không tự tạo snapshot sâu. Ưu tiên truyền giá trị nhỏ, immutable hoặc bản sao rõ ràng. Đặc biệt, tránh để worker giữ tham chiếu tới request-scoped bean, HTTP request đã hoàn tất hoặc data structure gắn với user khác.

## 9. Spring Security: security context là một bài toán riêng

Spring Security thường quản lý `SecurityContext` qua `SecurityContextHolder` với chiến lược gắn context vào thread (mặc định thường sử dụng `ThreadLocal`). Bởi vậy một phương thức `@Async` có thể thấy **khác** security context so với request thread.

Trong hệ thống cần mang authentication sang worker, Spring Security có các adapter như:

- `DelegatingSecurityContextRunnable`
- `DelegatingSecurityContextCallable`
- `DelegatingSecurityContextAsyncTaskExecutor`

Các loại này được thiết kế để thiết lập và dọn `SecurityContext` xung quanh callback, theo hợp đồng của Spring Security. Hãy đọc API tương ứng với phiên bản dự án và lựa chọn context snapshot/ownership đúng. Không tự copy toàn bộ `SecurityContextHolder` theo cách thô sơ, không giữ quyền của người dùng sau khi request kết thúc nếu nghiệp vụ không cho phép, và không kết luận có context là tự động có quyền truy cập hợp lệ ở mọi thời điểm.

**Security là ranh giới tin cậy.** Việc propagating correlation ID cho log không giống việc propagating authentication để ủy quyền tác vụ nền. Với tác vụ kéo dài, có thể phải xác thực lại hoặc dùng một danh tính dịch vụ được cấp quyền riêng thay vì mang security context của HTTP request.

## 10. MDC và correlation ID cho logging

Một trường hợp phổ biến là MDC (Mapped Diagnostic Context) của logging framework. MDC thường được backing bởi thread-local storage, nên log trong `@Async` có thể mất request ID trừ khi có propagation được cấu hình.

Ví dụ nguyên tắc dùng SLF4J MDC:

```java
Map<String, String> snapshot = MDC.getCopyOfContextMap();

return () -> {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    try {
        if (snapshot == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(snapshot);
        }

        delegate.run();
    } finally {
        if (previous == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }
};
```

Đây là **snippet không độc lập**, cần logging implementation có MDC, `Map`, biến `delegate` và decorator được đặt đúng vào executor. Ý tưởng giống phần 6 nhưng áp dụng lên **toàn bộ map MDC**; cần kiểm tra cơ chế MDC và cách copy của logging provider đang dùng. Chỉ propagate các field phục vụ quan sát, không đưa secret, access token, thông tin nhạy cảm hoặc toàn bộ payload request vào log.

Trong hệ thống dùng OpenTelemetry hoặc Micrometer, tracing context có API và lifecycle riêng; tốt hơn sử dụng instrumentations/context propagation đã được kiểm chứng thay vì tự sao chép nhiều ThreadLocal một cách tản mạn.

## 11. ContextPropagatingTaskDecorator của Spring

Spring Framework cung cấp `org.springframework.core.task.support.ContextPropagatingTaskDecorator` tích hợp với **Micrometer Context Propagation** để hỗ trợ một số execution context, ví dụ tracing observation, khi môi trường có thư viện và context accessors phù hợp.

Ví dụ cấu hình minh họa:

```java
@Bean("observedExecutor")
ThreadPoolTaskExecutor observedExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(2);
    pool.setMaxPoolSize(4);
    pool.setQueueCapacity(16);
    pool.setThreadNamePrefix("observed-");
    pool.setTaskDecorator(
        new org.springframework.core.task.support.ContextPropagatingTaskDecorator()
    );
    return pool;
}
```

Đây là cấu hình Spring không độc lập; cần Spring Framework phiên bản hỗ trợ và dependency `io.micrometer:context-propagation` cùng các context accessor cần thiết. Việc cài decorator **không tự bảo đảm mọi ThreadLocal tùy ý được sao chép**, và không được hiểu là sao chép transaction hoặc security state một cách an toàn chỉ vì có thư viện.

Các framework có thể có cách propagation riêng, và instrumentation thêm overhead. Nên chọn đúng tập context cần truyền, benchmark nếu gửi rất nhiều task nhỏ, đồng thời tránh double-propagation khi nhiều decorator/wrapper cùng được áp dụng.

### Giới hạn của propagation tự động

- Chỉ các context được capture bởi cơ chế đã cấu hình mới đi theo task.
- Context có thể **hết hiệu lực** vào lúc task bắt đầu (ví dụ HTTP request đã kết thúc).
- Object mutable được share có thể race, bất kể reference được chuyển đi theo cách nào.
- Truyền security context không đồng nghĩa được phép dùng nó vô thời hạn.
- Không nên capture transaction-bound resources để dùng trên thread khác.

## 12. Transaction, RequestContextHolder và request scope

### Transaction là ngữ cảnh gắn với thread

`@Transactional` của Spring thường sử dụng `TransactionSynchronizationManager` để quản lý các resource/transaction-bound data **trên thread đang thực thi**. Nếu caller mở transaction rồi gọi một method `@Async`, worker **không tự tham gia transaction caller** chỉ vì có annotation hoặc decorator truyền một số ThreadLocal.

```text
Caller thread                             @Async worker
--------------------------------         --------------------------------
@Transactional method
  transaction A / connection A
  |
  +-- submit async task ----------------> method chạy trên thread khác
                                          không tự có transaction A
  |
  +-- caller commit/rollback A
```

Không cố “propagate transaction” bằng cách copy các ThreadLocal nội bộ hoặc chia sẻ JDBC `Connection`/JPA `EntityManager` sang worker. Điều đó phá lifecycle và các giả định về thread-safety của resource. Nếu task nền cần transaction, hãy **mở ranh giới giao dịch riêng ở worker** bằng cấu trúc Spring bean/proxy thích hợp và hiểu thứ tự advice của `@Async`/`@Transactional`.

### Request-scoped object có thể không tồn tại ở worker

`RequestContextHolder` gắn request attributes với thread. Một `@Async` task có thể chạy sau khi HTTP request kết thúc, và worker không được tự đưa vào một request context hợp lệ chỉ từ một tham chiếu cũ.

Các cơ chế inheritable request attributes có thể nguy hiểm với thread pool: worker có thể tồn tại lâu hơn request và bị tái sử dụng cho user khác. Không truyền `HttpServletRequest`, session object hay request-scoped proxy qua worker một cách mù quáng; hãy trích xuất dữ liệu immutable cần thiết trước lúc submit, hoặc thiết kế task nhận DTO rõ ràng.

### HTTP async không đồng nghĩa ThreadLocal tự truyền

Spring MVC có các hợp đồng xử lý HTTP bất đồng bộ riêng. Dù sử dụng một service `@Async`, request thread có thể bị chặn nếu caller lập tức `get()`/`join()`. Với `DeferredResult`, `Callable` hoặc các dạng asynchronous response khác, cần kiểm tra context propagation của **từng lớp**: servlet dispatch, executor, completion callback và rendering response. Đừng coi async HTTP như một thread liên tục giữ nguyên ThreadLocal.

## 13. Thứ tự gọi, callback và nested async

Một task async có thể gửi thêm task khác:

```java
CompletableFuture<Result> stage2 =
    stage1.thenApplyAsync(value -> transform(value), anotherExecutor);
```

Đây là snippet trong method có các biến và kiểu `Result`/`transform`; không phải chương trình độc lập. Callback chạy theo policy của `anotherExecutor`, do đó context mà nó nhìn thấy phụ thuộc thread thực thi **ở chính stage2**.

Ngay cả khi task đầu tiên đã được decorator chuyển trace ID, một bước `thenApplyAsync` đưa sang common pool hoặc executor khác **không có decorator** vẫn có thể mất context. Với callback non-async `thenApply`, callback có thể chạy trên thread làm stage trước hoàn tất hoặc thread đăng ký callback khi stage trước đã done. Không xây code dựa trên kỳ vọng callback luôn có một ThreadLocal nhất định nếu chưa kiểm soát ranh giới thread.

Khi có nhiều executor hoặc các stage gửi qua message broker, lựa chọn thường tốt hơn là **truyền context metadata bằng tham số hoặc message headers**. `TaskDecorator` chỉ giải quyết đường task đi qua executor đã gắn decorator, không phải toàn bộ pipeline qua nhiều hệ thống.

## 14. Bảng sai lầm thường gặp

| Nhận định sai | Vì sao sai | Thay bằng cách nào? |
| --- | --- | --- |
| “ThreadLocal là biến chung của mọi task.” | Giá trị gắn với thread, không gắn task. | Truyền context theo tham số hoặc bọc tại submit. |
| “@Async tự mang mọi ThreadLocal từ caller.” | Async proxy chọn executor, không mặc định clone toàn bộ thread state. | Dùng `TaskDecorator` hoặc wrapper phù hợp. |
| “Worker đọc `null` chứng tỏ @Async lỗi.” | Đó là hành vi ThreadLocal chuẩn trên thread khác. | Kiểm tra thread/qualifier và propagation riêng. |
| “Đổi sang InheritableThreadLocal là đủ.” | Kế thừa lúc tạo thread, không phải mỗi lần pool nhận task. | Snapshot theo task submission. |
| “Một thread pool luôn tạo worker mới cho task mới.” | Worker thường được tái sử dụng lâu dài. | Dọn binding trong `finally`. |
| “Task trước set rồi task sau tự ghi đè là được.” | Task sau có thể không set, hoặc có exception trước lúc set. | Clear cho snapshot absent và restore trong finally. |
| “Dùng `set(null)` luôn tương đương `remove()`.” | Binding vẫn tồn tại; withInitial có khác biệt khi đọc sau remove. | Dùng `remove()` để xóa binding theo hợp đồng. |
| “Decorator sẽ bắt được mọi exception bằng catch.” | FutureTask có thể giữ exception bên trong `Future`. | Quan sát Future/completion hoặc async handler riêng. |
| “Có security context thì có thể dùng vĩnh viễn.” | Quyền có lifecycle, hạn mức và tính hợp lệ riêng. | Dùng cơ chế Spring Security chuyên dụng, kiểm soát ủy quyền. |
| “Có @Transactional ở caller thì worker cùng transaction.” | Transaction-bound resources gắn thread. | Mở transaction của worker, không copy binding. |
| “TaskDecorator của tracing copy mọi ThreadLocal.” | Chỉ những context được đăng ký capture mới có thể propagate. | Cấu hình context accessors và kiểm thử từng loại. |
| “@Async xong thì mọi callback đều cùng worker.” | CompletableFuture có quy tắc executor riêng. | Chỉ rõ executor tại mỗi nhánh async. |
| “ThreadLocal giữ result của request là cách cache.” | Worker reuse có thể giữ nhầm dữ liệu và memory. | Dùng state thuộc request/task hoặc cache có lifecycle riêng. |
| “Kết thúc request thì mọi worker binding biến mất.” | Worker sống lâu hơn request và có thể giữ binding. | Cleanup cả caller và worker; không giữ request object. |

## 15. Checklist khi review một đường @Async

1. **Context nào cần thiết?** Trace ID, tenant ID, MDC, SecurityContext, locale, request metadata hay transaction? Đừng xếp tất cả vào một nhóm có cùng chiến lược.
2. **Giá trị thuộc về ai?** Caller request hiện tại, người dùng, hay một job dài hạn? Có thể hết hạn trước khi task chạy?
3. **Điểm submit nằm đâu?** Đúng bean/executor gắn `TaskDecorator` chưa, hay task đi sang một pool khác/common pool?
4. **Chụp khi nào?** Đã lấy snapshot trước khi caller dọn ThreadLocal chưa? `String`/immutable DTO hay object mutable?
5. **Đặt ở worker lúc nào?** Wrapper phải set trước `task.run()`, kể cả trường hợp snapshot null.
6. **Cleanup thế nào?** Có `finally` để restore/remove, ngay cả khi callback ném lỗi hoặc bị interrupt?
7. **Caller có tự dọn không?** Worker decorator không xóa ThreadLocal ở request thread; servlet filter/interceptor phải quản lý lifecycle riêng.
8. **Có lạm dụng InheritableThreadLocal không?** Pool có worker tái sử dụng và có nguy cơ kế thừa stale context khi tạo worker?
9. **Những context không thể copy là gì?** Transaction/JDBC connection, EntityManager, HttpServletRequest, mutable authentication state, security secret.
10. **Có nhiều stage async không?** `thenApplyAsync`, `@Scheduled`, `@EventListener`, messaging hoặc publisher khác cần xem riêng.
11. **Quan sát lỗi có đúng không?** `TaskDecorator` không phải toàn bộ error handling; Future/CompletableFuture có kết quả/lỗi riêng.
12. **Đã test đồng thời chưa?** Tạo Request A/B, một pool ít worker, kiểm tra không mất context và không rò rỉ; test cả exception, cancellation và shutdown.

## 16. Bài tập tự kiểm tra

**Câu 1.** Thread A gọi `TRACE_ID.set("A")`, rồi gửi task sang một worker của `ThreadPoolTaskExecutor`. Worker có mặc định đọc `"A"` không?

**Đáp án:** Không. `ThreadLocal` chuẩn lưu riêng theo thread; worker chỉ thấy binding của chính nó (thường null nếu chưa set). `@Async` không tự clone binding.

**Câu 2.** Vì sao `InheritableThreadLocal` không giải quyết ổn định propagation qua thread pool?

**Đáp án:** Nó kế thừa khi **thread mới được tạo**, trong khi pool có thể tái sử dụng worker để chạy rất nhiều task của các caller khác. Worker không nhận snapshot mới cho mỗi submit, thậm chí có thể giữ context cũ.

**Câu 3.** Trong ví dụ, tại sao `firstCopied=req-A`, `secondCopied=req-B`, nhưng `afterCallerClearMissing=true` dù cùng worker được dùng lại?

**Đáp án:** Decorator chụp snapshot ở mỗi lần submit, set/remove tương ứng trước `task.run()`, rồi khôi phục worker trong finally. Task sau không nhìn thấy binding trước.

**Câu 4.** Nếu `task.run()` ném exception, khi nào `finally` của decorator được chạy?

**Đáp án:** Khi rời khối try theo quy tắc Java, kể cả exception; wrapper khôi phục/remove context trước khi worker nhận task tiếp theo. Tuy nhiên một `FutureTask` có thể giữ exception trong Future, do đó decorator không nhất thiết nhìn thấy lỗi bằng catch.

**Câu 5.** Có nên copy `TransactionSynchronizationManager` hoặc JDBC connection từ caller sang worker không?

**Đáp án:** Không. Transaction/resource không tự an toàn qua thread; cần ranh giới giao dịch riêng ở worker, không copy ThreadLocal nội bộ.

**Câu 6.** Một callback `thenApplyAsync` được gửi sang executor khác không có decorator. Nó có chắc thấy trace ID từ task trước không?

**Đáp án:** Không. Mỗi ranh giới submit/thread cần cơ chế propagation riêng; trace ID không tự di chuyển giữa các pool.

**Câu 7.** `ThreadLocal.withInitial(() -> "default")` có tự copy value của caller sang worker không?

**Đáp án:** Không. Supplier khởi tạo giá trị cho **thread đang gọi `get`** khi chưa có binding; không copy giá trị từ thread khác.

**Câu 8.** Caller gọi `remove()` sau khi submit có làm task mất snapshot mà `TaskDecorator` đã chụp không?

**Đáp án:** Nếu snapshot là một giá trị immutable độc lập như `String`, không. Wrapper đã giữ giá trị tại thời điểm submit. Nếu snapshot là tham chiếu mutable, việc thay đổi state bên trong object vẫn có thể ảnh hưởng worker.

**Câu 9.** Một `@Async` method thấy `ThreadLocal` của caller vì nó đang chạy trên caller. Điều đó có khẳng định propagation đúng không?

**Đáp án:** Không. Có thể `@Async` chưa được bật, self-invocation bỏ qua proxy, hoặc executor chạy trực tiếp. Cần xác minh worker thread và đường proxy trước khi kết luận.

### Ghi nhớ

`ThreadLocal` gắn **giá trị vào thread**, không gắn **ngữ cảnh vào task**. Với Spring `@Async` và thread pools, một task có thể chạy trên worker khác caller và worker được dùng lại. Khi cần propagation, ưu tiên truyền metadata immutable bằng tham số; nếu API cần thread-bound context thì dùng `TaskDecorator` hoặc wrapper chuyên dụng để **capture ở caller → set ở worker → run → restore/remove trong finally**. Đừng truyền transaction/request/security state mù quáng và đừng nhầm log context với authorization.

### Tài liệu tham khảo chính thức

- [Java SE 17 — ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): binding theo thread, `get`, `set`, `remove` và `withInitial`.
- [Java SE 17 — InheritableThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InheritableThreadLocal.html): kế thừa giá trị tại lúc tạo child thread, không phải lúc submit vào pool.
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): `@EnableAsync`, `@Async` và executor.
- [Spring Framework 6.2.12 — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): `setTaskDecorator`, pool, queue và lifecycle.
- [Spring Framework 6.2.12 — TaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/task/TaskDecorator.html): bọc callback để đặt context và giới hạn quan sát exception.
- [Spring Framework — Observability Support](https://docs.spring.io/spring-framework/reference/6.2/integration/observability.html): `ContextPropagatingTaskDecorator` và Micrometer Context Propagation khi thích hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-context-002">Nguy cơ dữ liệu ngữ cảnh của tác vụ trước bị dùng lại</a>

<details>
<summary>Xem chi tiết</summary>

# Nguy cơ dữ liệu ngữ cảnh của tác vụ trước bị dùng lại

## 1. Vấn đề: một request đọc nhầm ngữ cảnh của request trước

Trong Spring Framework, `ThreadPoolTaskExecutor` thường **tái sử dụng worker thread**. Tác vụ A chạy trên worker W, gắn `tenantId=tenant-A` vào `ThreadLocal` hoặc SLF4J `MDC`. Sau khi A xong, W không biến mất: pool dùng lại W để chạy tác vụ B. Nếu A không dọn context, B có thể đọc lại `tenant-A` dù B thuộc `tenant-B` hoặc không có tenant nào. Đây là lỗi **stale execution context** hay *cross-task context leakage*.

Hệ quả có thể nghiêm trọng:

- Log B bị gán `requestId`/`traceId` của A, khiến điều tra sự cố và observability sai.
- Service lọc dữ liệu theo `tenantId` hay `userId` lấy từ thread-local cũ, có nguy cơ lộ dữ liệu chéo tenant hoặc phân quyền sai.
- `SecurityContextHolder` trên worker bị giữ lại `Authentication` của tác vụ khác; logic authorization dựa sai vào user hiện tại.
- Dữ liệu locale, timezone, feature flags, request metadata, audit actor bị dùng lại mà không được xác thực.
- `ThreadLocal` có thể nắm tham chiếu đến object lớn hoặc request-scoped object cho đến khi worker chết; memory retention tăng cùng số worker sống lâu.
- Lỗi chỉ xuất hiện khi **trúng đúng worker đã dùng trước**, nên test một request hoặc test tạo thread mới dễ bỏ sót.

**Nguyên tắc:** task mới không có nghĩa thread mới. Phải xác định ranh giới **capture → install/clear → run → restore/remove** cho từng tác vụ, trên đúng worker thread. Không được dùng `ThreadLocal` hay MDC như một nguồn xác thực duy nhất cho tenant hoặc security.

## 2. Cơ chế: ThreadLocal theo vòng đời thread, không theo vòng đời job

`ThreadLocal<T>` cho mỗi thread một giá trị riêng khi code gọi `set()/get()`. Giá trị đó thuộc **thread hiện tại**, không thuộc HTTP request hoặc đơn vị công việc. `ThreadLocal.remove()` loại bỏ giá trị của chính thread đang gọi; nó không dọn những worker thread khác.

~~~text
Caller request A        ThreadPoolTaskExecutor       Worker pool-1-thread-1
       |                          |                            |
       +---- task A ------------->|------ chạy A ------------>|
       |                          |    tenant.set("tenant-A")  |
       |                          |    process(A)              |
       |                          |    // quên remove()        |
       |                          |<----- task A xong --------|
       |                          |       worker giữ tenant-A  |
Caller request B                 |                            |
       +---- task B ------------->|------ chạy B ------------>|
                                  |    tenant.get() = tenant-A |
                                  |    // nhầm ngữ cảnh A      |
                                  |<----- task B xong --------|
~~~

Nếu hai task chạy **trên hai thread khác nhau**, chúng không tự đọc cùng ô `ThreadLocal`. Lỗi ở đây xuất hiện vì **worker W được sử dụng lại**. Điều này khác với race condition của một field singleton chia sẻ giữa nhiều worker.

Cũng cần phân biệt hai tình huống:

| Tình huống | Worker nhìn thấy | Ý nghĩa |
| --- | --- | --- |
| Caller có `tenant-A`, worker chưa được truyền context | `null` | Thiếu propagation, không phải stale |
| Caller B không có tenant, worker còn giá trị A | `tenant-A` | Stale value, lỗi isolation |
| Caller có `tenant-B`, wrapper chụp B và cài trước khi chạy | `tenant-B` | Propagation đúng khi giá trị đáng tin |
| Caller không có context, wrapper **clear** worker trước khi chạy | `null` | B không nhặt context cũ |
| Sau task, wrapper dọn/restore trong `finally` | Trở lại context thuộc scope trước | Không để context của task này rơi sang task kế |

Một decorator chỉ gọi `MDC.setContextMap(snapshot)` **khi snapshot khác null** sẽ sai: nếu tác vụ sau **không có MDC**, wrapper không set gì, nên worker có thể giữ MDC từ tác vụ trước. Nhánh snapshot null cần có hành động rõ ràng như `MDC.clear()` tại boundary phù hợp.

## 3. Những context nào trong Spring có rủi ro?

| Loại | Cơ chế thường gặp | Rủi ro khi dùng sai |
| --- | --- | --- |
| Application `ThreadLocal` | `ThreadLocal<String>` lưu tenant, job ID, batch metadata | Nhầm tenant/job, memory retention |
| Logging MDC | `org.slf4j.MDC` lưu map `requestId`, `traceId` | Log nhầm request, lộ thông tin và sai truy vết |
| Spring Security | `SecurityContextHolder`, thường `MODE_THREADLOCAL` | Dùng lại `Authentication`/principal của task trước |
| Request context | `RequestContextHolder`, request attributes gắn luồng | Dùng request đã hết vòng đời hoặc sai request |
| Transaction | `TransactionSynchronizationManager` và resource association | Resource/connection/transaction-bound state không hợp lệ |
| Micrometer observation | Scope/context của tracing/metrics | Correlation sai hoặc scope không được close |
| Context từ thư viện tự viết | Locale, timezone, routing, audit | Hành vi nghiệp vụ khác với payload thực |

**Không phải mọi thứ đều tự động propagate.** Khi đi từ servlet request thread sang worker thread, `ThreadLocal` thường không tự theo sang. Ngược lại, khi worker đã **có** giá trị cũ, nó cũng không tự xóa lúc tác vụ trước hoàn thành.

`ThreadLocal.remove()` được gọi trên HTTP caller thread không dọn biến của worker. `MDC.clear()` cũng chỉ dọn MDC của **thread đang gọi**; chỉ gọi ở controller sau khi submit chưa đủ để xóa MDC worker.

## 4. Ví dụ Java 17 độc lập: tái hiện leak có tính quyết định

Ví dụ dùng `Executors.newSingleThreadExecutor` nên các task đều chạy trên **cùng một worker**. `Future.get(timeout)` đợi task trước hoàn tất rồi mới chạy bước tiếp, không dựa vào `Thread.sleep` hay lịch CPU ngẫu nhiên.

### 4.1. ThreadLocalLeakDemo.java

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ThreadLocalLeakDemo {
    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private static <T> T run(
            ExecutorService pool, Callable<T> job) throws Exception {
        return pool.submit(job).get(3, TimeUnit.SECONDS);
    }

    // Capture trên caller, install và restore trên worker.
    private static <T> Callable<T> withTenantContext(Callable<T> job) {
        String captured = TENANT.get();
        return () -> {
            String previous = TENANT.get();
            try {
                if (captured == null) {
                    TENANT.remove();
                } else {
                    TENANT.set(captured);
                }
                return job.call();
            } finally {
                if (previous == null) {
                    TENANT.remove();
                } else {
                    TENANT.set(previous);
                }
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "context-worker"));
        try {
            System.out.println("FIRST=" + run(pool, () -> {
                TENANT.set("tenant-A");
                return TENANT.get(); // Anti-pattern: quên cleanup.
            }));

            // Worker cũ được sử dụng lại: B không có tenant vẫn thấy A.
            System.out.println("LEAKED_IN_SECOND="
                    + run(pool, TENANT::get));

            System.out.println("RESET=" + run(pool, () -> {
                TENANT.remove();
                return TENANT.get();
            }));

            // Cách đúng nếu task sở hữu riêng context của nó.
            System.out.println("SAFE_REQUEST=" + run(pool, () -> {
                TENANT.set("tenant-B");
                try {
                    return TENANT.get();
                } finally {
                    TENANT.remove();
                }
            }));
            System.out.println("AFTER_SAFE=" + run(pool, TENANT::get));

            // Caller có tenant-C, worker không tự nhận tenant-C.
            TENANT.set("tenant-C");
            try {
                System.out.println("NO_AUTO_PROPAGATION="
                        + run(pool, TENANT::get));

                System.out.println("PROPAGATED="
                        + run(pool, withTenantContext(TENANT::get)));

                System.out.println("AFTER_PROPAGATED="
                        + run(pool, TENANT::get));
            } finally {
                TENANT.remove(); // Dọn caller.
            }

            // Một lỗi nghiệp vụ cũng không được bỏ qua cleanup.
            Future<?> failed = pool.submit((Runnable) () -> {
                TENANT.set("tenant-D");
                try {
                    throw new IllegalStateException("boom");
                } finally {
                    TENANT.remove();
                }
            });
            try {
                failed.get(3, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                System.out.println("ERROR_CAUSE="
                        + ex.getCause().getClass().getSimpleName());
            }
            System.out.println("AFTER_ERROR="
                    + run(pool, TENANT::get));
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Lưu file đúng tên, dùng JDK 17+:

~~~bash
javac --release 17 ThreadLocalLeakDemo.java
java ThreadLocalLeakDemo
~~~

Stdout của chương trình:

~~~text
FIRST=tenant-A
LEAKED_IN_SECOND=tenant-A
RESET=null
SAFE_REQUEST=tenant-B
AFTER_SAFE=null
NO_AUTO_PROPAGATION=null
PROPAGATED=tenant-C
AFTER_PROPAGATED=null
ERROR_CAUSE=IllegalStateException
AFTER_ERROR=null
~~~

Lý do kết quả xác định: `newSingleThreadExecutor` chỉ có một worker; các lệnh `run` đợi task trước xong. Khi A quên `remove()`, B đọc tenant-A ngay trên cùng worker. Sau reset, tác vụ B an toàn được bao trong `try/finally` nên lần sau đọc `null`. Context trên caller không tự truyền vào worker; wrapper `withTenantContext` snapshot tại caller, cài trên worker rồi restore. Khi task ném lỗi, `finally` vẫn chạy nên `AFTER_ERROR=null`.

**Chi tiết cần nhớ:** `withTenantContext` restore giá trị `previous` của worker, hữu ích cho nested scope. Nhưng nếu worker đã bị bẩn từ một đoạn code khác thì restore có thể trả nó về **chính giá trị sai**. Vì thế phải bảo đảm boundary hoặc component sở hữu worker dọn sạch từ gốc, không coi “restore previous” là cách chữa mọi leak đã tồn tại.

## 5. Cách xử lý: always cleanup và quản lý ownership

### 5.1. Task tự tạo ThreadLocal thì dọn trong finally

~~~java
// Snippet minh họa bên trong worker; Job và perform do dự án định nghĩa.
void process(String tenant, Job job) {
    try {
        CURRENT_TENANT.set(tenant);
        perform(job);
    } finally {
        CURRENT_TENANT.remove();
    }
}
~~~

`CURRENT_TENANT` là `ThreadLocal<String>` do ứng dụng tạo; `remove()` được thực thi trong chính worker. Dù `perform` ném exception hay return sớm, `finally` vẫn chạy. Không chỉ cleanup trong `catch`: ở đường thành công, `catch` không chạy và context vẫn còn.

### 5.2. Scope lồng nhau cần restore trạng thái cũ

~~~java
// Snippet: trong thread có thể đã có scope cha hợp lệ.
String previous = CURRENT_TENANT.get();
try {
    if (newTenant == null) {
        CURRENT_TENANT.remove();
    } else {
        CURRENT_TENANT.set(newTenant);
    }
    perform();
} finally {
    if (previous == null) {
        CURRENT_TENANT.remove();
    } else {
        CURRENT_TENANT.set(previous);
    }
}
~~~

Mẫu trên chỉ minh họa việc quản lý một `ThreadLocal` do ứng dụng sở hữu. Nếu `T` là object mutable (ví dụ `Map`, `SecurityContext`, `HttpServletRequest`) thì snapshot một **reference** không biến nó thành dữ liệu bất biến. Để task làm việc với user/tenant, ưu tiên truyền scalar ID đã xác thực hoặc immutable DTO thay vì chia sẻ toàn bộ request object.

### 5.3. Xác định ai chịu trách nhiệm cleanup

Có ít nhất ba boundary:

1. **Servlet/security/tracing filter**: tạo và dọn context của HTTP request. Không tùy tiện gọi `MDC.clear()` trong service rồi xóa dữ liệu của filter hoặc tracing framework.
2. **Task executor/decorator**: capture lúc submit, cài hoặc clear trên worker, chạy task và restore hoặc dọn trong `finally`. Bảo vệ giữa **các task**.
3. **Code nghiệp vụ**: nếu tự tạo `ThreadLocal` riêng trong worker, phải dọn chính scope đó, kể cả trường hợp task ném lỗi.

Nếu mỗi lớp tự xóa context của lớp khác, nested scope có thể hỏng; nếu không có lớp nào chịu trách nhiệm, stale context tồn tại qua các task. Hãy chỉ rõ ownership khi thiết kế.

## 6. Spring TaskDecorator: chụp và restore MDC cho mỗi submission

Spring `ThreadPoolTaskExecutor` có `setTaskDecorator(TaskDecorator)` để bao bọc execution callback. Đây là điểm phù hợp để đưa MDC từ caller sang worker **và không để MDC rơi sang tác vụ kế tiếp**. Ta minh họa với SLF4J `MDC.getCopyOfContextMap()` và một snapshot map gồm `String → String`.

### 6.1. Dự án Maven Java 17 + Spring Boot 3.3.5

Cấu trúc:

~~~text
spring-context-isolation/
├── pom.xml
└── src/main/java/com/example/context/
    ├── ContextIsolationApplication.java
    ├── MdcSnapshotTaskDecorator.java
    └── ContextProbe.java
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
    <artifactId>spring-context-isolation</artifactId>
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

`spring-boot-starter` bao gồm logging backend phổ biến hỗ trợ SLF4J MDC. Ví dụ là ứng dụng command-line, không cần DB hoặc web server.

### 6.2. MdcSnapshotTaskDecorator.java

~~~java
package com.example.context;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

public final class MdcSnapshotTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable delegate) {
        // Capture lúc SUBMIT trên caller, không phải trên worker.
        Map<String, String> captured = MDC.getCopyOfContextMap();

        return () -> {
            // Snapshot scope đã có trên worker để restore.
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (captured == null) {
                    // Thiếu context cũng phải clear, tránh nhặt MDC cũ.
                    MDC.clear();
                } else {
                    MDC.setContextMap(captured);
                }

                delegate.run();
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }
}
~~~

Ba điều cần kiểm tra trong bất kỳ decorator tương tự nào:

- `captured` được đọc trong `decorate()` vào lúc task được gửi, trước khi chạy trên worker. Nếu đọc `MDC` trong `Runnable.run()` mà không capture trước, đó là **MDC của worker**, không phải MDC của caller.
- Khi `captured == null`, wrapper **xóa MDC trên worker trước khi delegate chạy**. Nếu chỉ set khi snapshot khác null, task không có context có thể thấy giá trị của task trước.
- Cleanup đặt trong `finally`. Snapshot `previous` giúp nested scope khôi phục context có trước; nhưng nếu context đó là dư lượng do component khác bỏ quên, chính component ấy cũng cần được sửa.

Javadoc `TaskDecorator` nói callback nhận được có thể là wrapper của executor, **không phải Runnable gốc do người dùng gửi**. Vì vậy decorator phù hợp quản lý context nhưng không phải điểm duy nhất để bắt lỗi nghiệp vụ của `FutureTask`: exception của `submit` có thể được giữ trong Future, vẫn cần handler theo dõi kết quả.

### 6.3. ContextProbe.java

~~~java
package com.example.context;

import java.util.concurrent.CompletableFuture;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ContextProbe {
    public record Observed(
            String request, String tenant, String worker) { }

    @Async("contextExecutor")
    public CompletableFuture<Observed> read(String request) {
        return CompletableFuture.completedFuture(new Observed(
                request,
                MDC.get("tenant"),
                Thread.currentThread().getName()));
    }
}
~~~

`ContextProbe` là Spring-managed bean khác với caller. `@EnableAsync` phải bật; gọi `worker.read()` qua proxy, không phải `this.read()` nội bộ. Method trả `CompletableFuture` hợp lệ và ghi lại `MDC.get("tenant")` ngay bên trong worker.

### 6.4. ContextIsolationApplication.java

~~~java
package com.example.context;

import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableAsync
public class ContextIsolationApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                SpringApplication.run(ContextIsolationApplication.class, args)) {
            // Runner kết thúc, context được đóng theo lifecycle.
        }
    }

    @Bean("contextExecutor")
    ThreadPoolTaskExecutor contextExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("ctx-");
        pool.setTaskDecorator(new MdcSnapshotTaskDecorator());
        pool.setWaitForTasksToCompleteOnShutdown(true);
        return pool;
    }

    @Bean
    ApplicationRunner showIsolation(ContextProbe worker) {
        return args -> {
            ContextProbe.Observed first;
            ContextProbe.Observed second;
            ContextProbe.Observed missing;

            try {
                MDC.put("tenant", "tenant-A");
                first = worker.read("A").get(3, TimeUnit.SECONDS);

                MDC.put("tenant", "tenant-B");
                second = worker.read("B").get(3, TimeUnit.SECONDS);

                MDC.remove("tenant");
                missing = worker.read("C").get(3, TimeUnit.SECONDS);
            } finally {
                MDC.remove("tenant"); // Dọn caller.
            }

            System.out.println("A=" + first.tenant());
            System.out.println("B=" + second.tenant());
            System.out.println("C=" + missing.tenant());
            System.out.println("REUSED_SAME_THREAD="
                    + first.worker().equals(second.worker()));
            System.out.println("WORKER_PREFIX="
                    + first.worker().startsWith("ctx-"));
            System.out.println("CALLER_CLEARED="
                    + (MDC.get("tenant") == null));
        };
    }
}
~~~

Pool được Spring quản lý, có một worker `ctx-*`. `ApplicationRunner` chủ động chờ từng `Future.get(3, SECONDS)` để đưa ra kết quả kiểm chứng xác định; đây **không** phải mẫu controller HTTP nên chờ đồng bộ để gọi đó là background processing. Trong hệ thống thật, trả Future hoặc job ID theo contract và cấu hình timeout/backpressure.

### 6.5. Chạy và stdout

Ở thư mục chứa `pom.xml`, dùng JDK 17+ và Maven:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua các log khởi động của Spring/Maven, sáu dòng do ứng dụng in:

~~~text
A=tenant-A
B=tenant-B
C=null
REUSED_SAME_THREAD=true
WORKER_PREFIX=true
CALLER_CLEARED=true
~~~

A và B dùng cùng worker vì pool chỉ có một thread. Tuy vậy giá trị trên worker thay theo MDC snapshot tại **từng submission**. Task C không có MDC tenant trên caller, nên decorator clear MDC trước khi chạy và trả `null` thay vì giữ `tenant-B`. Các task được chạy tuần tự trong demo để kết quả có tính quyết định; production có thể có nhiều worker song song.

## 7. Vì sao decorator không có finally vẫn sai?

~~~java
// Anti-pattern: chỉ cài context nếu khác null, không bao giờ dọn.
Map<String, String> captured = MDC.getCopyOfContextMap();
return () -> {
    if (captured != null) {
        MDC.setContextMap(captured);
    }
    delegate.run();
};
~~~

Nếu task A mang `tenant-A`, worker giữ map đó sau A. Task B **không có** context trên caller, nhánh `if` bỏ qua, nên B nhìn thấy tenant-A. Nếu task ném exception, không có cleanup nào được thực hiện. Trong trường hợp context lồng nhau, gọi `MDC.clear()` không điều kiện ở cuối còn có thể phá context do scope cha sở hữu.

Mẫu đúng ở mục 6 **cài map hoặc clear khi vào**, rồi **restore previous hoặc clear khi ra**, luôn trong `finally`. Nhưng nó không được hiểu là “mọi context của mọi thư viện đều an toàn”: decorator chỉ quản lý **MDC map**, không tự propagate Spring Security, transaction, request attributes hoặc mọi `ThreadLocal` ứng dụng.

Có thể dùng `MDC.remove("key")` thay vì `MDC.clear()` khi một lớp chỉ sở hữu một khóa; với một decorator bao trọn MDC snapshot, restore map giúp bảo toàn scope hợp lệ. Cần thiết kế ownership cho cả hai đầu.

## 8. ContextPropagatingTaskDecorator từ Spring Framework 6.1

Spring Framework 6.1+ có `org.springframework.core.task.support.ContextPropagatingTaskDecorator` cho các context được hỗ trợ qua **Micrometer Context Propagation**. Thư viện có thể snapshot và khôi phục observation/logging context khi worker chạy.

Snippet cấu hình trong một `@Configuration`, **không phải project độc lập**:

~~~java
@Bean("observedExecutor")
ThreadPoolTaskExecutor observedExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(32);
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    return executor;
}
~~~

Cần import `ThreadPoolTaskExecutor`, `ContextPropagatingTaskDecorator` và `@Bean`; ứng dụng cần dependency `io.micrometer:context-propagation` tương thích, cùng `ThreadLocalAccessor` đã đăng ký cho các loại context muốn truyền.

**Không suy luận rằng mọi ThreadLocal tự được truyền** chỉ vì dùng class trên. Phải xác nhận đúng accessor/registry và test thực tế. Thư viện cũng có chi phí capture/restore, không đáng áp dụng mù cho hàng triệu task cực nhỏ. Với một số giá trị đơn giản, explicit immutable payload (`tenantId` đã xác thực, `jobId`, `correlationId`) rẻ hơn và minh bạch hơn.

Cũng không nên cài đồng thời nhiều decorator tự quản cùng một MDC mà không hiểu thứ tự install/restore; chúng có thể ghi đè hoặc khôi phục sai scope.

## 9. Spring Security: tránh sử dụng Authentication của tác vụ trước

`SecurityContextHolder` mặc định thường dùng `ThreadLocal`. Trong servlet request, Spring Security filter chain quản lý thiết lập và dọn context ở ranh giới request. Nhưng trên thread pool custom, code `@Async` không tự được bảo đảm nhận đúng SecurityContext của caller, cũng không được phép đọc context cũ nếu task trước làm bẩn worker.

Anti-pattern (snippet, không dùng để ra quyết định phân quyền):

~~~java
Authentication auth =
        SecurityContextHolder.getContext().getAuthentication();
// Worker bị bẩn có thể trả auth của tác vụ trước.
~~~

Spring Security có `DelegatingSecurityContextRunnable` và các wrapper như `DelegatingSecurityContextAsyncTaskExecutor` để cài context cho từng task rồi dọn theo scope. Snippet dưới đây chỉ minh họa cấu hình:

~~~java
@Bean("securedExecutor")
AsyncTaskExecutor securedExecutor(
        @Qualifier("rawAsyncExecutor") AsyncTaskExecutor raw) {
    return new DelegatingSecurityContextAsyncTaskExecutor(raw);
}
~~~

Cần import `org.springframework.core.task.AsyncTaskExecutor`, `org.springframework.beans.factory.annotation.Qualifier`, `org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor`, `@Bean`; bean `rawAsyncExecutor` và các dependency Spring Security phải tồn tại. Constructor không nhận `SecurityContext` cố định dùng context được capture theo submission; overload có context cố định sẽ dùng một principal đã chỉ định cho mọi task, chỉ phù hợp với service account có chủ đích.

Lưu ý:

- `SecurityContext` có thể chứa `Authentication` và object mutable; capture reference không làm nó immutable. Không chia sẻ rồi mutate đối tượng ấy tùy tiện giữa các thread.
- Một task không có quyền người dùng **không được** kế thừa principal của task trước; yêu cầu quyền phải được kiểm tra lại ở tầng service/DB.
- Nếu chỉ cần `userId` hoặc `tenantId` đã kiểm tra để làm việc nền, truyền DTO bất biến thường an toàn hơn truyền cả `HttpServletRequest`/session.
- Security context propagation không đồng nghĩa business authorization: tenant trong payload vẫn cần so khớp với quyền thực.
- `SecurityContextHolder.clearContext()` dọn context của thread đang gọi; đừng nghĩ gọi trên caller dọn được worker.

**Không dùng `MODE_INHERITABLETHREADLOCAL` như bản sửa cho thread pool.** `InheritableThreadLocal` nhận giá trị của parent **lúc tạo thread**, không phải lúc submit mỗi task. Một worker lâu sống có thể bị “thừa kế” dữ liệu từ request A khi sinh ra, rồi tiếp tục chạy request B/C; đó chính là stale value. Hơn nữa giá trị mutable có thể bị chia sẻ tham chiếu.

## 10. Transaction và RequestContext: không cố propagate theo cách thủ công

### 10.1. @Transactional và resource gắn theo thread

Spring quản lý transaction/resource association theo thread (ví dụ thông qua `TransactionSynchronizationManager`). Khi task chuyển sang một worker khác, transaction của caller **không tự đi theo**. Không copy `EntityManager`, `ConnectionHolder` hoặc JDBC connection từ HTTP thread vào worker: những resource này có vòng đời và thread-safety riêng.

Nếu worker cần transaction, mở boundary `@Transactional` trong service được gọi qua Spring proxy **trên worker thread**. Khi job dispatch trước commit và cần dữ liệu đã commit, dùng after-commit hook/outbox hoặc thiết kế ordering khác phù hợp. Nếu thư viện custom tự `bindResource` vào worker, nó phải `unbindResource` chính xác; code chuẩn nên để Spring transaction interceptor lo lifecycle.

### 10.2. RequestContextHolder

`RequestContextHolder` gắn request attributes vào thread của servlet request. Worker chạy lâu hơn request không được phép giữ `ServletRequestAttributes` hoặc `HttpServletRequest` rồi dùng sau khi request đã kết thúc. Những giá trị cần thiết nên được trích xuất **trước lúc submit** thành payload rõ ràng.

Snippet Java 17:

~~~java
record JobEnvelope(
        String jobId,
        String tenantId,
        String initiatedBy,
        String correlationId) { }
~~~

Các ID trong `JobEnvelope` phải được lấy từ nguồn đã xác thực và không chứa dữ liệu nhạy cảm thừa. Record là dữ liệu bất biến ở mức tham chiếu tới `String`, có thể truyền qua executor hoặc lưu vào job store; ThreadLocal trong JVM không thể thay thế durable job state cho multi-pod/restart.

## 11. Những đường kết thúc thường bị bỏ sót

| Đường kết thúc | Điều cần làm |
| --- | --- |
| Thành công | `finally` cleanup, giữ hoặc truyền kết quả theo contract |
| Exception | `finally` vẫn chạy; Future/handler nhận lỗi nghiệp vụ |
| Return sớm | Cleanup phải ở `finally`, không chỉ nhánh success |
| Timeout caller | `Future.get(timeout)` chỉ dừng **đợi**, worker có thể tiếp tục chạy và giữ context cho tới khi xong |
| Cancel task chưa chạy | Wrapper chưa vào worker nên chưa cài context; vẫn phải dọn job state/handle ở caller |
| Cancel task đang chạy | Interrupt chỉ là yêu cầu, không bảo đảm dừng ngay; dùng `finally` khi worker thoát |
| Submission rejection | Task không chạy, có thể không có Future trả về; catch `TaskRejectedException`/`RejectedExecutionException` |
| Shutdown | Queue có thể còn task chưa chạy; xử lý Future/job ledger riêng, cleanup thread-local không đủ |
| Nested async | Mỗi executor cần propagation boundary và scope hợp lệ riêng |
| `CompletableFuture.supplyAsync` mặc định | Có thể dùng `ForkJoinPool.commonPool()`, không áp dụng decorator của pool Spring khác |

Thời gian chờ ngắn ở HTTP caller không tự xóa MDC đang nằm trên worker. Khi task bị treo I/O, cả context lẫn resource có thể còn giữ cho đến khi task thật sự thoát, vì vậy cần timeout ở chính network/DB và chính sách cancellation thực tế.

## 12. Kiểm thử lỗi reuse và nguyên tắc fail-closed

### 12.1. Test có ít nhất hai tác vụ trên đúng một worker

Một test chỉ gửi một task không chứng minh isolation. Nên:

1. Tạo executor core=max=1 hoặc `newSingleThreadExecutor`.
2. Gửi A gắn `tenant-A` rồi kết thúc (trong bản lỗi, quên cleanup).
3. Đợi A qua Future có timeout; gửi B **không đặt tenant**.
4. Trước khi sửa, B sẽ đọc tenant-A. Sau khi sửa, B phải nhận null hoặc bị từ chối rõ ràng vì thiếu context.
5. Gửi C với `tenant-C` để kiểm tra propagation/cleanup. Gây exception bên trong C; gửi D để bảo đảm D không còn context C.
6. Bổ sung nested scope, cancellation, rejection và shutdown để cover mọi đường kết thúc.

Dùng `CountDownLatch`/barrier khi test nhiều worker song song, không dựa vào `Thread.sleep` và trùng hợp scheduling. Nếu test bằng `SyncTaskExecutor` chạy inline, nhiều lỗi thread-local sẽ không xuất hiện vì caller thread xử lý trực tiếp; cần ít nhất một test bằng executor tái sử dụng thread thực.

### 12.2. Test end-to-end đúng yêu cầu bảo mật

- Giả lập hai principal/tenant khác nhau và một request thiếu tenant trên cùng worker; xác minh DB query/authz không dùng principal/tenant cũ.
- Test log với `requestId` khác nhau: dòng của job B phải mang B hoặc null đúng contract, tuyệt đối không mang A.
- Nếu một bean có `@Async`, gọi từ **bean khác qua proxy**; `this.asyncMethod()` có thể bypass proxy khiến test đang chạy inline thay vì trên pool.
- Kiểm tra tất cả executor thật dùng trong ứng dụng, gồm `@Async`, `@Scheduled`, event listeners bất đồng bộ và CompletableFuture với executor chỉ định.
- Với nhiều worker, test high-load để phát hiện reuse dưới race/rejection; với một worker, test xác định đường leak.

### 12.3. Chỉ check tenant != null có đủ không?

Không. Nếu worker B có **stale tenant-A**, câu điều kiện `tenant != null` vẫn đúng. Quyết định truy vấn/authorization phải dựa trên **tenant đã xác thực gắn với job B**, không dựa vào một giá trị thread-local chưa được xác minh.

~~~java
// Snippet minh họa; tenantContext, repository là thành phần ứng dụng.
String actualTenant = tenantContext.currentTenant();
if (!job.verifiedTenantId().equals(actualTenant)) {
    throw new SecurityException("Unexpected tenant execution context");
}
repository.findByTenantIdAndOrderId(job.verifiedTenantId(), job.orderId());
~~~

`job` cần là object chứa verified tenant từ boundary an toàn; nếu không có context hợp lệ thì từ chối (fail closed). Tốt hơn nữa, truyền verified tenant trực tiếp vào service/repository để tránh phụ thuộc context ngầm trong logic bảo mật.

## 13. Lỗi phổ biến và checklist

| Triệu chứng | Nguyên nhân | Sửa đúng |
| --- | --- | --- |
| Log B mang requestId của A | MDC chưa dọn trên worker | Capture map lúc submit, install/clear và restore trong `finally` |
| Tenant B nhầm A | `ThreadLocal` còn giá trị của task trước | Worker cleanup, kiểm tra tenant đã xác thực |
| User B được xử lý bằng Authentication A | SecurityContext chưa dọn đúng | DelegatingSecurityContext wrapper, clear/restore đúng ownership |
| Test dev không thấy lỗi | Không ép reuse worker | Test một worker và nhiều task nối tiếp |
| Task không có MDC vẫn thấy MDC | Decorator chỉ set khi snapshot non-null | Khi snapshot null, clear worker |
| `@Async` chạy nhưng request context mất | Chuyển sang thread khác, chưa có propagation | Trích xuất ID bất biến, dùng integration phù hợp |
| `@Transactional` của caller không có trong worker | Transaction thread-bound | Tạo transaction trong worker qua Spring service |
| `MDC.clear()` ở service xóa cả tracing scope cha | Xóa context của thành phần không thuộc ownership | Snapshot previous và restore tại boundary |
| `InheritableThreadLocal` dùng sai user | Giá trị thừa hưởng lúc tạo worker, không theo task | Propagation theo từng submission |
| Task timeout nhưng context chưa biến mất | Worker vẫn chạy dù caller đã timeout | Timeout I/O và cleanup khi worker thật sự thoát |
| Chỉ pool A có decorator | Pool B hoặc commonPool bypass decorator | Audit mọi executor/scheduler/callback boundary |
| Lộ thông tin dù log context đúng | Log context không phải cơ chế authorization | Kiểm tra quyền và tenant tại nghiệp vụ/DB |

**Checklist trước khi merge:** (1) Có nhận diện mọi context dùng `ThreadLocal`/MDC/SecurityContext không? (2) Ai sở hữu cleanup? (3) Có xử lý snapshot null ở worker? (4) Có `finally` cho success/failure? (5) Có truyền mutable security/request/session object không? (6) Có truyền transaction/EntityManager sai luồng không? (7) Có test nhiều task trên một worker và task thiếu context? (8) Có test exception, timeout, cancellation và overload? (9) Có đảm bảo mọi executor cần propagation đều dùng cùng chiến lược? (10) Có cơ chế fail closed với tenant/user không hợp lệ?

## 14. Câu hỏi tự kiểm tra

1. Vì sao `ThreadLocal` không phải “một biến cho mỗi HTTP request”?
2. Task B không set tenant nhưng thấy tenant-A: đó là lỗi propagation hay stale reuse?
3. Gọi `MDC.clear()` trên controller có dọn được MDC của `ThreadPoolTaskExecutor` worker không?
4. Vì sao decorator phải **clear khi captured null**, không chỉ set khi có giá trị?
5. Nếu task A ném exception sau khi set context, vì sao `finally` quan trọng?
6. Một TaskDecorator MDC có tự truyền Spring SecurityContext và transaction không?
7. `MODE_INHERITABLETHREADLOCAL` có cập nhật context mỗi lần pool nhận task mới không?
8. Nếu `CompletableFuture.supplyAsync` dùng commonPool, decorator gắn vào `contextExecutor` có được gọi không?
9. Tại sao timeout từ `get(3, SECONDS)` không chứng minh worker đã dọn context?
10. Có stale tenant-A nhưng `tenant != null` là true: cần kiểm tra và thiết kế authorization thế nào?

**Kết luận:** worker thread thường sống lâu hơn từng task. Để tránh dùng lại ngữ cảnh cũ, phải **capture theo từng submission, cài hoặc clear trước khi chạy, restore/remove trong finally trên worker**, và kiểm thử hai tác vụ nối tiếp trên cùng thread. Propagation phục vụ tính nhất quán của execution context; authorization phải dựa vào dữ liệu đã xác thực, không phụ thuộc một giá trị thread-local không được kiểm chứng.

## Tài liệu tham khảo chính thức

- [Java SE 17 — ThreadLocal, set/get/remove](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Spring Framework 6.2 — TaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/core/task/TaskDecorator.html)
- [Spring Framework 6.2 — ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html)
- [Spring Framework — Observability and context propagation](https://docs.spring.io/spring-framework/reference/integration/observability.html)
- [Spring Security — Concurrency Support](https://docs.spring.io/spring-security/reference/features/integrations/concurrency.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-context-003">TaskDecorator bao tác vụ để thiết lập và dọn ngữ cảnh</a>

<details>
<summary>Xem chi tiết</summary>

# TaskDecorator bao tác vụ để thiết lập và dọn ngữ cảnh

## 1. Vì sao tác vụ bất đồng bộ có thể mất ngữ cảnh?

Trong Spring Framework, lời gọi qua `@Async` hoặc `ThreadPoolTaskExecutor` thường thực thi công việc trên **worker thread khác caller thread**. Điều này làm nảy sinh một lỗi khó nhận biết: caller có request ID trong `ThreadLocal` hoặc MDC để ghi log, nhưng bên trong tác vụ async giá trị lại `null`; ngược lại, một worker tái sử dụng còn giữ ngữ cảnh **của request trước**, gây nhầm log, trace hoặc thậm chí rò rỉ dữ liệu giữa các tác vụ.

`ThreadLocal` gắn dữ liệu **với từng thread**, không gắn với một logical request, một `Runnable` hay một `Future`. Thread pool tái sử dụng worker giữa nhiều tác vụ. Vì vậy, những gì caller thiết lập trên chính thread của nó **không tự xuất hiện** trong worker, và thứ worker từng lưu trước đó cũng không tự biến mất khi task kết thúc.

`TaskDecorator` trong Spring là điểm mở rộng để **bao tác vụ bằng một `Runnable` khác**. Decorator thường được dùng nhằm:

1. **Chụp một snapshot ngữ cảnh phù hợp** ở thread gọi, tại thời điểm executor nhận công việc.
2. **Thiết lập snapshot trên worker** ngay trước khi delegate `Runnable.run()`.
3. **Chạy tác vụ gốc**.
4. **Khôi phục ngữ cảnh worker đã có trước đó, hoặc xóa hẳn nếu ban đầu không có**, trong `finally`, kể cả khi công việc thất bại.

Đây là một mẫu **capture → set → run → restore/clear**, rất quan trọng khi worker được tái sử dụng. Bài này giải thích cơ chế ấy, tích hợp `ThreadPoolTaskExecutor`, minh họa Java 17+ chạy thật, cách dùng MDC, giới hạn bảo mật/transaction và kiểm thử chống rò rỉ ngữ cảnh.

## 2. TaskDecorator là gì, nó nằm ở đâu trong đường đi của tác vụ?

Spring khai báo interface chức năng:

```java
@FunctionalInterface
public interface TaskDecorator {
    Runnable decorate(Runnable runnable);
}
```

Đây là **chữ ký giản lược đúng trọng tâm** của `org.springframework.core.task.TaskDecorator`. `decorate` nhận callback sắp được executor chạy và **trả về một `Runnable` mới**, có trách nhiệm cuối cùng gọi đến `runnable.run()`. Phần quan trọng không phải biến đổi dữ liệu đầu vào, mà là tạo một **execution scope** bao quanh việc thực thi.

Đường đi điển hình:

```text
Caller thread                                   Worker thread của pool
-------------                                   ---------------------
MDC / ThreadLocal có requestId=REQ-7
          |
          v
executor.submit(task)
          |
          v
TaskDecorator.decorate(callback)
          |
          +-- capture requestId="REQ-7"
          |
          +-- return wrapped Runnable ---------+
                                               |
                                        worker lấy callback
                                               |
                                        lưu ngữ cảnh cũ
                                               |
                                        set requestId="REQ-7"
                                               |
                                        callback.run()
                                               |
                                          finally
                                               |
                                        restore / remove
                                               |
                                        worker trở lại pool
```

**Thời điểm quan trọng:** việc chụp ngữ cảnh cần diễn ra **khi submit trên caller thread**; không đợi tới lúc `run()` trên worker mới đọc từ `ThreadLocal`, bởi worker có thread-local riêng. Chức năng của `Runnable` mới trả về là thiết lập snapshot đã chụp lên worker.

Điều này không có nghĩa mọi implementation `TaskExecutor` đều bảo đảm chuyển sang một thread khác. `TaskDecorator` chỉ bao callback được thực thi; thực tế thread nào chạy còn tùy executor. Lợi ích chuyển context rõ nhất ở `ThreadPoolTaskExecutor`, nơi worker tách khỏi caller.

## 3. Ba đối tượng cần phân biệt: caller context, captured snapshot và worker context

Để không ghi đè/để sót dữ liệu, phải tách rõ:

| Đối tượng | Sống ở đâu? | Thời điểm đọc/ghi | Trách nhiệm |
| --- | --- | --- | --- |
| **Caller context** | `ThreadLocal` hoặc MDC trên thread gọi | Có trước khi gửi tác vụ | Cung cấp các giá trị cần truyền |
| **Captured snapshot** | Biến được đóng gói trong decorator `Runnable` | Chụp khi `decorate` được gọi | Lưu giá trị bất biến/độc lập cho đúng task |
| **Worker previous context** | `ThreadLocal` hoặc MDC đã hiện diện trên worker | Đọc ngay trước khi chạy task | Phải được khôi phục ở `finally`, kể cả khi task lỗi |
| **Worker current context** | Context được thiết lập cho task hiện hành | Trong lúc `runnable.run()` | Dành cho log/tracing/logic theo yêu cầu |
| **Cleanup state** | Worker sau task | Sau khi `finally` chạy | Không để lại context của task hiện hành |

Ví dụ worker trước khi chạy task A không có request ID. Khi A chạy, decorator thiết lập `A`. Khi A xong, phải **remove**, không để A tồn tại trên worker để B nhận nhầm. Nếu worker vốn có một context hợp lệ khác (chẳng hạn từ một wrapper ngoài), decorator phải **restore giá trị cũ**, không xóa bừa. Đây là lý do nên có biến `previous` thay vì chỉ gọi `ThreadLocal.remove()` vô điều kiện.

Một snapshot đúng thường là **giá trị**, không phải tham chiếu tới request object mutable. Nếu capture một `Map` có thể bị caller sửa sau khi submit, các task không còn snapshot đáng tin; hãy copy dữ liệu cần thiết hoặc dùng API snapshot chuyên dụng.

## 4. Mẫu TaskDecorator chuẩn: capture khi submit, restore trong finally

Ý tưởng triển khai với `ThreadLocal<String>`:

```java
private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

TaskDecorator decorator = runnable -> {
    String captured = REQUEST_ID.get(); // Đọc trên caller thread.
    return () -> {
        String previous = REQUEST_ID.get(); // Đọc trên worker thread.
        try {
            if (captured == null) {
                REQUEST_ID.remove();
            } else {
                REQUEST_ID.set(captured);
            }
            runnable.run();
        } finally {
            if (previous == null) {
                REQUEST_ID.remove();
            } else {
                REQUEST_ID.set(previous);
            }
        }
    };
};
```

Đây là **mảnh Java minh họa**, đặt trong class có import `org.springframework.core.task.TaskDecorator` và biến `REQUEST_ID`. Nó thể hiện đầy đủ chính sách cho một context key đơn giản:

- `captured` được đọc **trước khi Runnable được giao cho worker**.
- `previous` được đọc ở **worker** để bảo vệ một scope đã có.
- Nếu caller không có ID, phải xóa ID ở worker cho thời gian chạy task để tránh đọc nhầm request trước.
- `runnable.run()` có thể thành công hoặc ném exception. `finally` chạy trong cả hai trường hợp khi control rời khỏi `try`.
- Sau task, worker trở về context trước đó; nếu ban đầu trống thì trở về trống.

**Không sử dụng mẫu dưới đây:**

```java
// SAI: thiết lập context nhưng quên cleanup/restore.
return () -> {
    REQUEST_ID.set(captured);
    runnable.run();
};
```

Đây là **mảnh phản ví dụ**; worker thread sẽ có thể giữ lại `captured` sau khi chạy xong. Nếu `captured == null`, việc `set(null)` cũng không có ý nghĩa như remove: `ThreadLocal.remove()` rõ ràng loại entry khỏi ThreadLocalMap, phù hợp để tránh giữ tham chiếu không cần thiết trên worker dài hạn.

## 5. Ví dụ Spring Framework 6.2.9 + Java 17+ có thể chạy

Ví dụ sau sử dụng chính `ThreadPoolTaskExecutor.setTaskDecorator` với một worker tái sử dụng. Nó chứng minh ba tình huống:

1. Context `req-169` từ caller được chụp và có mặt khi task đầu chạy.
2. Task thứ hai ném `IllegalStateException`; lỗi được nhận qua `Future.get()`.
3. Sau khi xóa context caller, một task tiếp theo không thấy context cũ. Decorator đồng thời kiểm tra ngữ cảnh worker đã trống **trước khi thiết lập context mới**.

Cấu trúc Maven:

```text
task-decorator-demo/
├── pom.xml
└── src/main/java/demo/TaskDecoratorDemo.java
```

File `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>task-decorator-demo</artifactId>
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
                <version>3.14.0</version>
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

File `src/main/java/demo/TaskDecoratorDemo.java`:

```java
package demo;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class TaskDecoratorDemo {

    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();
    private static final AtomicBoolean WORKER_CLEAN = new AtomicBoolean(false);

    private static TaskDecorator capturedTraceDecorator() {
        return task -> {
            String submittedTrace = TRACE.get(); // Thread nộp task.

            return () -> { // Thread worker chạy đoạn này.
                String previous = TRACE.get();

                // Chứng minh worker không mang context từ task trước.
                if (submittedTrace == null) {
                    WORKER_CLEAN.set(previous == null);
                }

                try {
                    if (submittedTrace == null) {
                        TRACE.remove();
                    } else {
                        TRACE.set(submittedTrace);
                    }

                    task.run();
                } finally {
                    if (previous == null) {
                        TRACE.remove();
                    } else {
                        TRACE.set(previous);
                    }
                }
            };
        };
    }

    public static void main(String[] args) throws Exception {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(4);
        pool.setThreadNamePrefix("worker-");
        pool.setTaskDecorator(capturedTraceDecorator());
        pool.initialize();

        try {
            TRACE.set("req-169");
            Future<String> first = pool.submit(() -> {
                return TRACE.get();
            });
            System.out.println("first = " + first.get());

            TRACE.set("req-fail");
            Future<?> failed = pool.submit(() -> {
                throw new IllegalStateException("boom");
            });
            try {
                failed.get();
            } catch (ExecutionException ex) {
                System.out.println("failure = "
                        + ex.getCause().getClass().getSimpleName());
            }

            TRACE.remove();
            Future<String> second = pool.submit(() -> {
                return String.valueOf(TRACE.get());
            });
            System.out.println("second = " + second.get());
            System.out.println("worker clean = " + WORKER_CLEAN.get());
            System.out.println("caller clean = " + (TRACE.get() == null));
        } finally {
            TRACE.remove();
            pool.shutdown();
        }
    }
}
```

Chạy ở thư mục dự án với Maven và JDK 17 trở lên:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.TaskDecoratorDemo
```

Stdout phần chương trình:

```text
first = req-169
failure = IllegalStateException
second = null
worker clean = true
caller clean = true
```

**Giải thích kết quả:**

- Caller đặt `TRACE=req-169` rồi submit. `TaskDecorator.decorate` chụp chuỗi này khi thread gọi giao việc. `first.get()` đợi worker hoàn tất rồi in `req-169`.
- Caller đổi sang `req-fail`. Task thứ hai vẫn được chụp/thiết lập độc lập; nó ném exception. `Future.get()` ném `ExecutionException`; `getCause()` cho biết lỗi gốc `IllegalStateException`.
- Sau task lỗi, `finally` của decorator vẫn khôi phục context worker trước task. Caller xóa `TRACE` rồi submit task thứ ba.
- Vì pool chỉ có một worker và mỗi bước đều được `get()` trước khi gửi bước kế tiếp, task thứ ba chạy trên worker đã xử lý hai task trước. `WORKER_CLEAN` kiểm tra `previous == null` ngay **trước** khi decorator xóa/thiết lập context cho task thứ ba. `true` là bằng chứng cleanup đã hoạt động ở worker được tái sử dụng trong ví dụ này.
- Task thứ ba thấy `TRACE.get() == null`; caller cũng không còn ID. Không có dữ liệu request cũ bị giữ trên worker.

Ví dụ sử dụng Spring Framework 6.2.9 để minh họa API ổn định tại phiên bản đó. Đã thực thi lớp tương đương với các thư viện Spring 6.2.9 trên JShell, kết quả đúng năm dòng trên; điều này kiểm chứng đường chạy chính, còn cấu hình Maven được trình bày như project có thể tái tạo.

`TaskDecorator` là một **callback runtime**, không phải annotation trên nghiệp vụ. Trong chương trình console tự tạo executor, phải gọi `initialize()` trước khi dùng và `shutdown()` khi xong. Với executor được khai báo là Spring bean, container quản lý lifecycle; không nên gọi `shutdown` theo mỗi request.

## 6. Cấu hình TaskDecorator thành Spring bean cho @Async

Trong Spring-managed application, đặt decorator vào bean `ThreadPoolTaskExecutor` để mọi task **được gửi qua chính executor đó** có wrapper. Ví dụ cấu hình:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncContextConfig {

    @Bean("contextPool")
    public ThreadPoolTaskExecutor contextPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("context-");
        executor.setTaskDecorator(requestIdDecorator());
        return executor;
    }

    private TaskDecorator requestIdDecorator() {
        return task -> {
            String captured = RequestIdContext.get();
            return () -> {
                String previous = RequestIdContext.get();
                try {
                    RequestIdContext.setOrClear(captured);
                    task.run();
                } finally {
                    RequestIdContext.setOrClear(previous);
                }
            };
        };
    }
}
```

Đây là **mảnh cấu hình Spring cần class tiện ích `RequestIdContext` do ứng dụng định nghĩa**; `setOrClear(null)` phải gọi `ThreadLocal.remove()`, không chỉ `set(null)`. Các thông số 4/8/100 là ví dụ, không phải đề xuất mặc định cho production. Khi bean do Spring quản lý, container khởi tạo và dọn executor theo lifecycle của context.

Một service dùng executor đó:

```java
@Service
public class AuditService {

    @Async("contextPool")
    public CompletableFuture<String> writeAudit() {
        String requestId = RequestIdContext.get();
        // Xử lý nghiệp vụ và log theo requestId đã thiết lập trên worker.
        return CompletableFuture.completedFuture(requestId);
    }
}
```

Đây cũng là **mảnh tích hợp Spring**, cần imports `@Service`, `@Async` và `CompletableFuture`. `@EnableAsync` phải bật trong đúng application context, caller phải gọi bean qua Spring proxy (không tự `new` hoặc `this.writeAudit()` trong cùng target), và `@Async("contextPool")` phải chọn bean chứa decorator. Nếu `@Async` chọn executor khác, decorator gắn trên `contextPool` sẽ **không tự đi theo** tác vụ đó.

Trong proxy mode điển hình, interceptor giao callback tới executor khi nhận lời gọi; decorator chụp context từ thread đang submit. Một lời gọi async đã đi qua nhiều tầng executor có thể có **nhiều lần chụp context** tại các ranh giới tương ứng; cần hiểu nơi nào sinh task mới và nơi nào worker đã mang context.

## 7. MDC logging: sao chép bản đồ và dọn đúng scope

Một ứng dụng dùng SLF4J MDC thường đặt `traceId` hoặc `requestId` trên caller thread:

```java
MDC.put("requestId", requestId);
```

`MDC` thường dựa trên context gắn với thread (tùy binding/implementation). Với thread pool, không nên giả định bản đồ MDC của caller tự theo worker. Mẫu decorator minh họa:

```java
TaskDecorator mdcDecorator = task -> {
    Map<String, String> captured = MDC.getCopyOfContextMap();

    return () -> {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            if (captured == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(captured);
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
};
```

Đây là **mảnh mã tích hợp**, cần SLF4J binding hỗ trợ MDC, imports `org.slf4j.MDC`, `java.util.Map`, `TaskDecorator`; không phải chương trình JDK thuần. `getCopyOfContextMap` trả bản sao bản đồ hiện tại (hoặc null), phù hợp hơn việc giữ reference mutable dùng chung. `MDC.setContextMap` thay thế map trên thread thực thi, nên khôi phục map cũ trong `finally` là bước bắt buộc.

Sử dụng `MDC.clear()` khi không có map vừa bảo vệ request khác, vừa tránh sót thread-local map trên worker. Không chỉ `MDC.remove("requestId")` nếu decorator đã thay đổi **cả map**; nó có thể xóa hoặc giữ sai các key ngữ cảnh khác. Khi ứng dụng có nhiều lớp bọc context, thứ tự capture/restore phải được thống nhất.

**Không sao chép mọi thông tin nhạy cảm vào MDC.** Chỉ đưa ID và thuộc tính log an toàn cần thiết, không đưa password, token, thông tin riêng tư hoặc body request; MDC có thể được log hoặc gửi tới hệ thống quan sát bên ngoài.

## 8. ContextPropagatingTaskDecorator: lựa chọn có sẵn từ Spring 6.1

Spring Framework 6.1+ có class `org.springframework.core.task.support.ContextPropagatingTaskDecorator` hỗ trợ truyền một số execution context nhờ hệ sinh thái **Micrometer Context Propagation**, đặc biệt hữu ích với logging và observation/tracing context.

Ví dụ cấu hình khi đã có những thư viện và context accessors cần thiết:

```java
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

// Trong cấu hình ThreadPoolTaskExecutor:
executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
```

Đây là **mảnh tích hợp**, không phải file Java chạy độc lập. Khi dùng, phải kiểm tra phiên bản Spring tương thích, dependency Micrometer context-propagation và **các ThreadLocalAccessor/context accessors được đăng ký**. Decorator này không thể tự biết mọi `ThreadLocal` tùy ý mà ứng dụng tạo: nếu không có accessor phù hợp, giá trị đó có thể không được capture/restore.

Cơ chế snapshot/scope giúp tránh phải tự viết wrapper cho từng integration thông dụng, nhưng không tự truyền hay sao chép một transaction JDBC đang mở. JavaDoc cũng cảnh báo overhead: với hàng trăm nghìn task cực nhỏ, chi phí chụp/khôi phục context có thể đáng kể; đo trước khi áp dụng đại trà.

Nếu nhiều decorator cần dùng cùng lúc, Spring cung cấp `CompositeTaskDecorator` ở các phiên bản hỗ trợ. Dù tổ hợp nhiều wrapper, vẫn cần hiểu thứ tự thực thi và thứ tự restore; tránh vừa dùng decorator thủ công vừa dùng propagation tự động cho cùng context mà không xác định ai là chủ sở hữu scope.

## 9. Đừng dùng InheritableThreadLocal như cách thay thế per-task propagation

`InheritableThreadLocal` có thể chuyển một giá trị từ thread cha sang thread con **khi thread con được tạo**, nhưng worker của thread pool có thể tồn tại lâu trước khi một request mới được xử lý. Do đó:

- Worker tạo trong request A có thể thừa hưởng một giá trị từ A.
- Worker ấy sau đó phục vụ request B, C, D mà không được tạo lại.
- `InheritableThreadLocal` **không tự cập nhật snapshot cho mỗi lần submit** và có thể gây ngữ cảnh sai/đọng lại.

Đừng dùng thread inheritance để giải quyết context propagation trong pool tái sử dụng. Capture per task và restore sau task phù hợp hơn. Một số executor tạo thread mới theo mỗi task có thể có hành vi kế thừa khác, nhưng điều đó không biến inheritance thành cơ chế rõ ràng hay an toàn cho tất cả executor.

## 10. Ngữ cảnh nào có thể truyền, ngữ cảnh nào không được sao chép tùy tiện?

| Ngữ cảnh | Hướng xử lý | Vì sao |
| --- | --- | --- |
| Correlation ID, request ID | Chụp một chuỗi bất biến, khôi phục sau chạy | Thích hợp cho log/tracing, ít trạng thái |
| MDC Map | Copy snapshot và restore Map cũ | Worker được tái sử dụng; không để sót key |
| Tracing/observation context | Dùng Micrometer Context Propagation/instrumentation tương thích | Cần scope và lifecycle chính xác |
| Spring SecurityContext | Dùng cơ chế truyền SecurityContext được Spring Security hỗ trợ, xác định principal/authorities và cleanup | Tự copy context mutable có thể sai hoặc rò rỉ quyền |
| Transaction JDBC/JPA | **Không** copy `TransactionSynchronizationManager`, `Connection` hoặc `EntityManager` sang thread khác | Transaction imperative và persistence context thường gắn với thread/tài nguyên không thread-safe |
| Servlet request/response | Không mang nguyên `HttpServletRequest`/`Response` sang worker sau khi request scope kết thúc | Container quản lý vòng đời, object có thể không còn hợp lệ |
| Locale/timezone | Nếu cần, truyền giá trị an toàn và restore theo scope | Không tự giả định locale từ caller xuất hiện trên worker |
| Reactor Context | Dùng tích hợp context propagation tương thích Reactor | Reactor có mô hình context khác `ThreadLocal` |
| Credentials/tokens bí mật | Hạn chế tối đa; dùng API/context chuyên dụng khi cần | Nguy cơ dữ liệu nhạy cảm bị giữ trên worker/log |

**Không dùng `TaskDecorator` để “chuyển transaction” bằng cách copy các thread-local nội bộ của Spring.** Spring imperative transaction thường dùng `TransactionSynchronizationManager` gắn tài nguyên với thread; mang một `Connection`/`EntityManager` sang worker khác có thể vi phạm lifecycle và an toàn đồng thời. Nếu worker cần thao tác database, hãy mở transaction **trên worker** thông qua một transactional service/proxy phù hợp.

Ví dụ một service async có thể gọi sang bean khác có `@Transactional` để thiết lập transaction mới trong worker, rồi trả DTO độc lập. Context truyền bằng decorator có thể mang correlation ID phục vụ log, nhưng **transaction boundary vẫn là trách nhiệm riêng**.

## 11. Error handling: decorator không thay thế Future.get()

Một điều tinh tế trong JavaDoc Spring: `TaskDecorator.decorate` có thể được áp dụng lên **callback thực thi nội bộ**, chứ không nhất thiết đúng chính `Runnable`/`Callable` do người dùng truyền vào. Đặc biệt với `submit(...)`, callback thường là một `FutureTask` hoặc wrapper tương tự.

Với `FutureTask`, exception của callable được **ghi vào Future**, không phải luôn ném trực tiếp ra khỏi `Runnable.run()`. Do đó:

```java
return () -> {
    try {
        task.run();
    } catch (RuntimeException ex) {
        // Có thể KHÔNG nhận được lỗi nghiệp vụ nếu task là FutureTask.
        logFailure(ex);
        throw ex;
    } finally {
        restoreContext();
    }
};
```

Đoạn này là **mảnh phản ví dụ về giả định catch được mọi lỗi**, cần `logFailure` và `restoreContext` do ứng dụng định nghĩa. Phần `finally` vẫn là nơi đúng để dọn context, nhưng `catch` quanh `run()` **không phải cơ chế giám sát đáng tin cho mọi lỗi trong task gửi qua `submit`**.

Hãy đọc lỗi qua `Future.get()` (bắt `ExecutionException` rồi kiểm tra `getCause()`), hoặc dùng `CompletableFuture.handle/whenComplete/exceptionally`, `AsyncUncaughtExceptionHandler` với phương thức `@Async void`, hay điểm quan sát phù hợp với API. Ví dụ Spring đầy đủ ở mục 5 chứng minh `failed.get()` nhìn được `IllegalStateException`.

Phân biệt rõ:

- **Lỗi lúc gửi việc/rejection:** executor có thể ném `TaskRejectedException` ngay phía submit/caller.
- **Lỗi khi chạy:** task đã được nhận rồi ném exception; `submit` thường phản ánh qua Future.
- **Lỗi khi cleanup context:** `finally` có thể ném exception riêng; cần thiết kế `restore` đáng tin và không vô tình che mất nguyên nhân nghiệp vụ.

Decorator nên đơn giản, không thực hiện I/O chặn, không nuốt exception của delegate và không phụ thuộc vào việc phải đoán trạng thái Future.

## 12. Cancellation, timeout và queue có thể khiến ngữ cảnh sống lâu hơn dự kiến

Context snapshot được capture khi submit, nhưng task có thể chờ lâu trước khi chạy. Điều đó gây các hệ quả:

1. **Snapshot là giá trị tại thời điểm submit**, không phải trạng thái mới nhất trên caller ở lúc task thật bắt đầu.
2. Một task chờ queue lâu có thể giữ snapshot, bao gồm tham chiếu tới object nếu capture không đúng. Tránh giữ servlet request, session hoặc object lớn.
3. Nếu task bị từ chối khi submit, wrapper có thể đã được tạo nhưng `run()` không được gọi; phải xử lý rejection ở caller. Việc thiết lập context **trên worker** không diễn ra nếu không có worker thực thi.
4. Nếu Future bị hủy **trước khi chạy**, worker có thể chạy một callback Future đã bị cancel mà không thực hiện business logic, hoặc implementation có thể loại khỏi queue; không suy đoán rằng một cleanup worker chắc chắn chạy trong mọi trạng thái. Tránh lưu tài nguyên phải đóng trong snapshot ngay từ đầu.
5. `get(timeout)` chỉ giới hạn lượt chờ của caller, không tự dừng worker hay bảo đảm cleanup đã chạy. `cancel(true)` không cưỡng bức dừng mọi I/O; `finally` chỉ chạy khi luồng thực thi thật sự rời scope.
6. Nếu task tự tạo **một child task** trên executor khác, decorator của pool đầu không tự gắn vào pool thứ hai. Muốn context xuyên suốt qua nhiều boundary, phải có propagation tại mỗi boundary được xác định.

Không nên làm cho decorator phụ thuộc vào request body/HTTP session sau khi request đã kết thúc. Nếu chỉ cần ID/logging, chụp giá trị `String`. Với dữ liệu nghiệp vụ, tạo DTO/value object độc lập có lifecycle và giới hạn rõ.

## 13. Kiểm thử chống rò rỉ ngữ cảnh trên pool tái sử dụng

Một phép test chỉ chạy **một task duy nhất** rồi xác nhận nó thấy request ID là chưa đủ. Vì worker chưa được tái sử dụng, bạn chưa chứng minh cleanup.

Test có giá trị nên:

1. Tạo pool cố định **một worker**, để chắc chắn các task chạy lần lượt trên cùng thread.
2. Caller đặt `requestId=A`, submit task A, đợi kết quả và xác nhận worker thấy A.
3. Caller đặt `requestId=B`, submit task lỗi B, xác nhận exception được truyền về caller và `finally` vẫn xử lý.
4. Caller xóa `requestId`, submit task C; decorator kiểm tra **context hiện hữu trước khi set/clear** trên worker. Nó phải bằng null nếu các task trước đã cleanup đúng.
5. Trong task C, xác nhận request ID không có, và sau C worker vẫn sạch. Khi test có thêm scope lồng nhau, kiểm tra khả năng **khôi phục previous** thay vì luôn clear.
6. Kiểm tra cả nhánh queue đầy/rejection, caller bị interrupt, timeout/hủy, và task chạy lâu.
7. Thử concurrency nhiều request với các ID khác nhau; không thấy một task đọc nhầm ID của task khác và không bị memory/resource retention theo thời gian.

Ví dụ `TaskDecoratorDemo` ở mục 5 làm các bước 1–4 với `AtomicBoolean WORKER_CLEAN`. Không chỉ kiểm tra output của task C, bởi một decorator **bắt đầu bằng `remove()`** có thể che đi việc task A đã để sót context. Cần kiểm tra `previous` ngay trước thao tác thiết lập mới để thực sự phát hiện rò rỉ.

### Kiểm tra log và trace

Khi làm việc với MDC/observability, ghi nhận correlation ID ở caller và worker. Dùng id an toàn, không chứa thông tin nhạy cảm. Quan sát cùng một request qua các log nhưng vẫn cần bảo đảm không có ID của request cũ trong log của request mới. Với trace context, kiểm tra parent–child span relation bằng instrumentation phù hợp; chỉ copy một chuỗi `traceId` vào MDC không tự tạo một span hợp lệ.

## 14. Hiệu năng và giới hạn áp dụng

Decorator được gọi trên đường nộp task và wrapper chạy mỗi lần task thực thi, do đó có overhead:

- Snapshot một map MDC có thể phải copy các key-value mỗi lần submit.
- Restore context tốn thao tác ThreadLocal và có thể giữ nhiều reference nếu thực hiện sai.
- Wrapper nhiều tầng và capture context không cần thiết sẽ tăng chi phí khi tác vụ cực ngắn.
- Context propagation không làm một API chặn trở thành nonblocking, không tăng giới hạn CPU hay database, và không làm dữ liệu mutable thành thread-safe.

Hãy chỉ truyền **ngữ cảnh thật sự cần** cho logging, tracing hoặc business boundary. Nếu mọi task chỉ tính một phép toán rất nhỏ mà không cần request ID, có thể không cần decorator. Nếu task I/O dài và việc giữ correlation ID có ích cho debug, chi phí decorator thường hợp lý nhưng vẫn cần đo.

Khi dùng `@Async`, phải xác nhận executor thực tế được chọn; `setTaskDecorator` trên bean A không tác động các task chạy ở bean B hoặc common pool của `CompletableFuture.supplyAsync` không chỉ định executor.

## 15. Những lỗi thực tế thường gặp

1. **Chụp context ở thời điểm `run()` trên worker.** Lúc đó `ThreadLocal` của caller không tồn tại trên worker; phải capture khi submit.
2. **Không có `finally`.** Nếu task ném exception, context có thể bị bỏ lại trên worker phục vụ request tiếp.
3. **Luôn `remove()` mà không restore previous.** Có thể phá scope do decorator khác đã thiết lập trước đó; phải lưu ngữ cảnh cũ của worker.
4. **Luôn `set(captured)` kể cả khi null.** Nên `remove()` khi không có giá trị để tránh giữ một ThreadLocal entry vô ích.
5. **Truyền tham chiếu mutable thay vì snapshot.** Caller có thể sửa map/list sau submit; task đọc dữ liệu không nhất quán.
6. **Dùng `InheritableThreadLocal` cho thread pool.** Worker tái sử dụng không nhận snapshot mới theo từng request; có thể giữ context cũ.
7. **Chuyển nguyên `HttpServletRequest`, `Connection`, `EntityManager` hay transaction sang worker.** Chúng có scope/thread lifecycle riêng và có thể không an toàn.
8. **Tưởng TaskDecorator bắt được mọi exception khi `submit`.** `FutureTask` có thể giữ lỗi bên trong Future; phải kiểm tra `get()`/handler.
9. **Tưởng TaskDecorator hoạt động trên mọi executor trong ứng dụng.** Nó chỉ áp dụng lên nơi được cấu hình; `@Async` và `CompletableFuture` có thể chọn executor khác.
10. **Tin rằng `ContextPropagatingTaskDecorator` tự biết mọi custom ThreadLocal.** Cần context accessors đã đăng ký và dependency phù hợp.
11. **Chỉ test một request.** Lỗi rò rỉ chỉ lộ khi worker tái sử dụng cho request khác hoặc task trước ném exception.
12. **Chỉ copy ID vào MDC rồi coi như trace propagation.** Log correlation không tự thay thế observation/span context.
13. **Để snapshot giữ credentials/token không cần thiết.** Có nguy cơ rò rỉ dữ liệu và tồn tại lâu trong queue.
14. **Dùng decorator làm nơi mở transaction rồi chuyển resource sang worker khác.** Transaction cần được thiết kế trong đúng thread thực thi, không copy thread-local nội bộ.
15. **Bỏ qua cleanup khi task tự sinh nested async work.** Mỗi boundary cần propagation rõ ràng; cleanup của parent không trực tiếp cleanup child.
16. **Quên lifecycle của pool.** Console demo cần `initialize` và shutdown; Spring bean do container quản lý không nên tự shutdown sau mỗi request.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `ThreadLocal` của caller thường không hiện trong tác vụ chạy trên `ThreadPoolTaskExecutor`?  
**Đáp án:** ThreadLocal gắn với thread, còn worker của pool là thread khác. Cần chụp giá trị tại submission và thiết lập trong worker.

**Câu 2.** Vì sao phải restore `previous` thay vì chỉ gọi `remove()` sau task?  
**Đáp án:** Worker có thể đang ở trong một scope bên ngoài có context hợp lệ. Restore giữ nguyên contract của scope đó; nếu previous null, mới remove.

**Câu 3.** Task ném exception nhưng `TaskDecorator` vẫn cần làm gì?  
**Đáp án:** Chạy cleanup ở `finally`. Với `submit`, lỗi có thể nằm trong Future thay vì được ném trực tiếp từ callback.

**Câu 4.** Dùng `ContextPropagatingTaskDecorator` có tự chuyển transaction JDBC từ caller sang worker không?  
**Đáp án:** Không. Transaction imperative và resource gắn với thread; cần transactional boundary riêng trên worker.

**Câu 5.** Test task A thấy request ID đúng rồi kết luận không rò rỉ, đã đủ chưa?  
**Đáp án:** Chưa. Cần test B/C dùng cùng worker, nhất là sau task lỗi, và kiểm tra previous worker context trước khi setup task mới.

**Câu 6.** Nếu method dùng `@Async("poolA")` nhưng TaskDecorator chỉ gắn trên poolB, có tác dụng không?  
**Đáp án:** Không, trừ khi thực sự có task được giao qua poolB theo đường khác. Phải gắn vào executor đúng đang nhận tác vụ.

**Câu 7.** Khi caller đã đổi request ID sau submit, worker nên thấy ID nào?  
**Đáp án:** Snapshot tại thời điểm `decorate`/submit của chính task, không phải giá trị mới nhất của caller sau đó.

**Ghi nhớ:** với mỗi tác vụ bất đồng bộ, **capture trên caller → set trên worker → chạy delegate → restore/clear trong finally**. Chính bước dọn và khôi phục trên worker tái sử dụng mới giúp propagation vừa hữu ích vừa không làm lẫn ngữ cảnh giữa các request.

### Tài liệu tham khảo

- [Spring Framework — TaskDecorator JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/TaskDecorator.html)
- [Spring Framework — ThreadPoolTaskExecutor JavaDoc và setTaskDecorator](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Micrometer — Context Propagation](https://docs.micrometer.io/context-propagation/reference/)
- [Java SE 17 — ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html)
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-context-004">Chụp ngữ cảnh ở bên gửi rồi khôi phục trạng thái cũ bằng try-finally</a>

<details>
<summary>Xem chi tiết</summary>

# Chụp ngữ cảnh ở bên gửi rồi khôi phục trạng thái cũ bằng `try-finally`

**Module:** Spring Framework - concurrency  
**Nhóm:** Ngữ cảnh thực thi  
**Chủ đề:** Chụp ngữ cảnh ở bên gửi rồi khôi phục trạng thái cũ bằng `try-finally`.

## 1. Vấn đề: một request đi qua nhiều thread nhưng ngữ cảnh không tự đi theo

Trong ứng dụng Spring, request HTTP ban đầu thường có những thông tin phục vụ xử lý và quan sát: `traceId`, `requestId`, tên tenant, locale hoặc một ngữ cảnh bảo mật. Một số thư viện lưu thông tin này trong `ThreadLocal` hay MDC (Mapped Diagnostic Context) để không phải truyền thủ công vào mọi phương thức ghi log.

Ví dụ một request có `traceId=REQ-A` rồi gọi phương thức `@Async`:

```text
Servlet thread: http-nio-8080-exec-1
   MDC.traceId = REQ-A
           |
           | executor.execute(task)
           v
Thread pool worker: business-1
   MDC.traceId = ?     <- không tự động giống thread gửi
```

**`ThreadLocal` gắn dữ liệu với thread, không gắn với logical request hoặc Runnable.** Khi một `Runnable` chuyển từ thread nhận request sang thread trong pool, giá trị `ThreadLocal` của thread gửi không tự được sao chép sang thread worker. Executor có thể sử dụng lại cùng worker cho hàng nghìn task khác nhau.

Nếu worker từng xử lý request A và để sót `traceId=A`, task B chạy tiếp trên worker đó có thể:

- Ghi log với `traceId` của A dù B đến từ request khác.
- Dùng nhầm ngữ cảnh tenant/người dùng nếu code dựa vào ThreadLocal.
- Làm lệch quan sát lỗi, audit và thống kê request.
- Rò dữ liệu nhạy cảm giữa các logical requests.

Hai yêu cầu cần giải quyết **cùng lúc**:

1. **Truyền đúng snapshot của thread gửi** tại thời điểm task được giao.
2. **Khôi phục chính xác trạng thái thread worker vốn có** sau khi task kết thúc — **kể cả task ném exception**.

Giải pháp là **capture → install → run → restore** với `try-finally`, không phải chỉ gán một giá trị rồi hy vọng pool tự xóa.

## 2. Bốn thời điểm và một bất biến không được vi phạm

Giả sử thread gửi đang có `requestId=A`, worker đang có `requestId=WORKER-BASELINE` do đoạn code bao ngoài cài tạm. Quy trình:

```text
T0: SUBMITTER                         T1: WORKER
    requestId = A                        requestId = WORKER-BASELINE
         |
    capture snapshot = A
         |
    executor.execute(wrappedTask) ----------->
                                              previous = WORKER-BASELINE
                                              try {
                                                  install(A)
                                                  delegate.run()
                                              } finally {
                                                  restore(WORKER-BASELINE)
                                              }
                                          -> requestId = WORKER-BASELINE
```

**Bất biến quan trọng:** sau khi callback hoàn tất (thành công hoặc thất bại), ngữ cảnh thread chạy phải quay về **đúng trạng thái trước khi callback bắt đầu**, không phải về một hằng số hay về snapshot của request trước.

| Thời điểm | Thread thực hiện | Hành động |
| --- | --- | --- |
| T0 — tạo wrapper / giao task | Thread **gửi** | Đọc/copy các giá trị cần truyền; không đợi tới worker |
| T1 — bắt đầu `run()` | Thread **thực thi** | Đọc trạng thái hiện tại của worker và lưu vào `previous` |
| T2 — trước khi gọi business work | Thread thực thi | Cài snapshot từ bên gửi; nếu snapshot không có giá trị thì **xóa** giá trị cũ |
| T3 — trong `finally` | Thread thực thi | Khôi phục `previous` — kể cả business work lỗi, có lệnh `return`, hoặc decorator lồng nhau |

### Vì sao phải snapshot **trước khi đưa vào queue**?

Một task có thể chờ hàng giây trước khi worker lấy ra. Trong thời gian đó, HTTP thread có thể:

- Đổi `traceId` để xử lý một đoạn khác.
- Xóa MDC khi hoàn tất request.
- Bị servlet container tái sử dụng cho request tiếp theo.

Nếu wrapper chụp giá trị **bên trong `run()`**, nó sẽ đọc ngữ cảnh **của worker tại thời điểm chạy**, không phải của request khi submit. Phải chụp trong `execute(...)`, `decorate(...)` hoặc tại callsite ở **thread gửi**.

## 3. Mẫu Java tối thiểu, đúng thứ tự

Một wrapper cơ bản cho `ThreadLocal<String>`:

```java
private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

static void setOrRemove(String value) {
    if (value == null) {
        REQUEST_ID.remove();
    } else {
        REQUEST_ID.set(value);
    }
}

static Runnable wrapWithCurrentContext(Runnable delegate) {
    String captured = REQUEST_ID.get(); // Thread gửi: CHỤP NGAY.
    return () -> {
        String previous = REQUEST_ID.get(); // Thread chạy: LƯU NGỮ CẢNH CŨ.
        try {
            setOrRemove(captured);       // Cài snapshot, kể cả null.
            delegate.run();             // Chạy nghiệp vụ.
        } finally {
            setOrRemove(previous);       // Phục hồi chính xác.
        }
    };
}
```

Đây là **đoạn thành viên minh họa**, cần đặt trong class có import phù hợp. Nếu dùng:

```java
executor.execute(wrapWithCurrentContext(() -> process()));
```

`process()` và `executor` phải được ứng dụng định nghĩa. **Điểm quan trọng:** `wrapWithCurrentContext()` được gọi tại thread submitter trước `executor.execute`, và `delegate.run()` chỉ được gọi **một lần** trong wrapper.

`ThreadLocal.remove()` tốt hơn `ThreadLocal.set(null)` khi muốn bỏ hẳn binding. Nếu `previous == null`, ta **remove** trong `finally`; nếu trước đó worker có giá trị khác null, phải **set lại giá trị đó**, không chỉ `remove()` một cách vô điều kiện.

Một số thư viện cho phép snapshot chứa nhiều trường, nhưng nguyên tắc vẫn như nhau: snapshot bất biến hoặc sao chép phòng vệ tại thời điểm gửi; lưu **toàn bộ trạng thái cần phục hồi** tại thời điểm chạy; dọn/khôi phục qua `finally`.

## 4. Vì sao chỉ `remove()` trong `finally` có thể sai?

Mẫu thường thấy:

```java
Runnable wrapped = () -> {
    REQUEST_ID.set(capturedId);
    try {
        businessWork();
    } finally {
        REQUEST_ID.remove();
    }
};
```

Đây là đoạn minh họa, `capturedId` và `businessWork()` do ứng dụng cung cấp. Mẫu này có thể đủ trong **một** hệ thống mà worker luôn bắt đầu với trạng thái trống và không có wrapper lồng nhau, nhưng **không phục hồi đúng trạng thái cũ**.

Ví dụ:

1. Task ngoài cài `requestId=A`.
2. Một component trong task ngoài gọi executor chạy **trực tiếp trên cùng thread** (`Runnable::run`) hoặc `CallerRunsPolicy`, tạo task trong với `requestId=B`.
3. Task trong kết thúc và gọi `remove()`.
4. Task ngoài tiếp tục chạy, nhưng `requestId=A` đã **bị xóa**.

```text
Ngoài:  previous=null -> set A
    Trong: previous=A -> set B -> finally restore A   [đúng]
Ngoài:  vẫn thấy A -> finally restore null
```

Dùng `try-finally` **restore previous**, không chỉ cleanup thành “trống”, để composable với nested decorators và executor có thể chạy inline. Với worker mới hoàn toàn trống, `previous=null`, kết quả cuối vẫn là `remove()`, nhưng nguyên tắc tổng quát hơn giúp không phá context của code bao ngoài.

### `CallerRunsPolicy` làm vấn đề này trở nên thực tế

Khi pool đầy, `ThreadPoolExecutor.CallerRunsPolicy` có thể chạy task **ngay trên thread đang submit**, nếu executor chưa shutdown. `try-finally` phải khôi phục ngữ cảnh của **thread đó**, dù nó vốn là servlet/request thread chứ không phải worker cố định. Đừng viết decorator giả định mọi task luôn chạy trên một thread mới và luôn có `ThreadLocal` trống.

## 5. Vì sao snapshot cần được copy, không chỉ giữ một tham chiếu?

Với giá trị bất biến như `String requestId`, việc lưu tham chiếu tới `String` là đủ. Nhưng một `ThreadLocal<Map<String,String>>` chứa **đối tượng mutable** có thể bị thread gửi sửa sau khi submit:

```java
Map<String, String> captured = currentMap; // Chỉ copy tham chiếu!
executor.execute(() -> use(captured));
currentMap.put("tenant", "another-tenant");
```

Đây là đoạn minh họa (`currentMap`, `executor`, `use` phải tồn tại). Worker có thể nhìn thấy dữ liệu bị đổi muộn, thậm chí có race khi nhiều thread đọc/ghi `HashMap` không an toàn.

Khi cần snapshot:

```java
Map<String, String> captured =
        currentMap == null ? Map.of() : Map.copyOf(currentMap);
```

Đây là đoạn minh họa Java 17; `Map.copyOf` tạo bản map không cho sửa bằng API map nhưng **chỉ copy nông**: nếu value là object mutable thì value đó vẫn dùng chung tham chiếu. `Map.copyOf` cũng không chấp nhận key/value null. Với cấu trúc lồng nhau, phải xác định copy sâu ở các phần cần thiết hoặc chuyển sang một DTO bất biến đã được validate.

**Đừng snapshot cả object HTTP request, session, JDBC connection, entity manager, transaction hay credential nhạy cảm** chỉ để tiện. Nên truyền **giá trị tối thiểu**, ví dụ `correlationId`, tenant ID được xác thực và các tham số nghiệp vụ cần thiết.

### Snapshot khác với “ngữ cảnh luôn cập nhật”

Context propagation dùng **giá trị tại thời điểm submit**; nếu thread gửi đổi `traceId` sau đó, task đã xếp queue vẫn dùng snapshot cũ. Đó là tính chất được mong muốn để log thuộc về đúng request ban đầu.

## 6. Java 17 chạy độc lập: chụp đúng lúc submit, thử lỗi và phục hồi cả worker/caller

Chương trình dưới đây không cần Spring, cho phép kiểm chứng **chính xác** ý nghĩa của `try-finally`. Nó dùng một single-thread executor để chứng minh thread được tái sử dụng. `CountDownLatch` giữ worker trong khi thread gửi đổi context, nên test không dựa vào `Thread.sleep` hay đoán scheduling.

Mục tiêu:

- Chụp `request-A` **trước khi task chạy**, dù caller đổi thành `changed-after-submit`.
- Task của request B thấy `request-B` chứ không thấy A.
- Nếu thread gửi **không có context**, task phải thấy `null`, dù worker có sẵn `worker-baseline`.
- Task ném lỗi vẫn khiến worker trở về context ban đầu.
- Executor chạy inline (`Runnable::run`) cũng phải phục hồi context của **caller** sau khi callback sửa ThreadLocal.
- `FutureTask` giữ exception cho `Future.get`, trong khi `finally` của wrapper vẫn chạy.

Lưu toàn bộ vào **`ContextSnapshotDemo.java`**:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class ContextSnapshotDemo {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    static void setOrRemove(String value) {
        if (value == null) {
            REQUEST_ID.remove();
        } else {
            REQUEST_ID.set(value);
        }
    }

    static final class SnapshotExecutor implements Executor {
        private final Executor delegate;

        SnapshotExecutor(Executor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable task) {
            // CHỤP trong thread submitter, trước khi đưa task vào queue.
            String captured = REQUEST_ID.get();

            delegate.execute(() -> {
                // LƯU trong thread thực sự chạy task.
                String previous = REQUEST_ID.get();
                try {
                    setOrRemove(captured);
                    task.run();
                } finally {
                    setOrRemove(previous);
                }
            });
        }
    }

    static <T> Future<T> submit(Executor executor, Callable<T> work) {
        FutureTask<T> future = new FutureTask<>(work);
        executor.execute(future);
        return future;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            // Thiết lập context cũ trên thread worker để kiểm tra phục hồi.
            raw.submit(() -> setOrRemove("worker-baseline")).get();

            SnapshotExecutor propagated = new SnapshotExecutor(raw);

            // Giữ worker bận để task A chắc chắn còn trong queue.
            raw.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker not started");
            }

            setOrRemove("request-A");
            Future<String> first = submit(propagated, REQUEST_ID::get);

            // Đổi context SAU khi submit nhưng TRƯỚC khi task A chạy.
            setOrRemove("changed-after-submit");
            release.countDown();

            System.out.println("snapshotAtSubmit=" + first.get());

            setOrRemove("request-B");
            System.out.println("secondRequest=" +
                    submit(propagated, REQUEST_ID::get).get());

            // Snapshot null phải xóa tạm context của worker.
            setOrRemove(null);
            System.out.println("missingSnapshot=" +
                    submit(propagated, () ->
                            REQUEST_ID.get() == null
                                    ? "<none>" : REQUEST_ID.get()).get());

            setOrRemove("request-error");
            Future<String> failure = submit(propagated, () -> {
                throw new IllegalStateException("boom");
            });
            try {
                failure.get();
            } catch (ExecutionException ex) {
                System.out.println("failure=" +
                        ex.getCause().getClass().getSimpleName());
            }

            // Kiểm tra cùng mẫu decorator khi chạy ngay trên caller.
            setOrRemove("caller-before-inline");
            SnapshotExecutor direct =
                    new SnapshotExecutor(Runnable::run);
            direct.execute(() -> setOrRemove("temporary-in-task"));

            System.out.println("callerAfterInline=" + REQUEST_ID.get());
            setOrRemove(null);

            // Đọc trực tiếp từ raw executor để xác nhận phục hồi worker.
            String restoredWorker = raw.submit(
                    (Callable<String>) REQUEST_ID::get).get();
            System.out.println("workerAfterAll=" + restoredWorker);
        } finally {
            release.countDown();
            setOrRemove(null);
            raw.shutdown();
            if (!raw.awaitTermination(5, TimeUnit.SECONDS)) {
                raw.shutdownNow();
            }
        }
    }
}
```

Chạy tại thư mục chứa file bằng Java 17+:

```bash
javac ContextSnapshotDemo.java
java ContextSnapshotDemo
```

**Stdout của chương trình khi hoàn tất bình thường:**

```text
snapshotAtSubmit=request-A
secondRequest=request-B
missingSnapshot=<none>
failure=IllegalStateException
callerAfterInline=caller-before-inline
workerAfterAll=worker-baseline
```

Giải thích:

1. `snapshotAtSubmit=request-A` dù `REQUEST_ID` phía gửi đã đổi trước khi worker được thả. Wrapper chụp ở lúc `execute`, không phải khi chạy.
2. `secondRequest=request-B`: task B không thừa hưởng A hay baseline từ lần worker dùng trước.
3. `missingSnapshot=<none>`: wrapper xử lý snapshot null bằng `remove`, tránh đọc giá trị cũ `worker-baseline` đang có trên worker.
4. `failure=IllegalStateException`: `FutureTask` ghi nhận ngoại lệ, còn wrapper vẫn chạy `finally`.
5. `callerAfterInline=caller-before-inline`: executor `Runnable::run` làm callback chạy ngay trên caller; khôi phục `previous` bảo vệ request context dù callback gán giá trị khác.
6. `workerAfterAll=worker-baseline`: sau A, B, task trống và task lỗi, worker vẫn có đúng context trước khi các wrapper bắt đầu. Không rò A/B vào lần sử dụng tiếp theo.

**Giới hạn:** demo cố ý đặt một `ThreadLocal` trong worker để kiểm chứng cơ chế. Trong production, nên để worker sau mỗi task ở trạng thái phù hợp, thường là **trống**. Việc lưu `worker-baseline` trong pool ở đây không phải khuyến nghị thiết kế ứng dụng thật.

## 7. `TaskDecorator` trong Spring: capture ở `decorate`, restore trong `run`

Spring cung cấp `org.springframework.core.task.TaskDecorator` để bọc callback trước khi executor chạy task. Cách viết tương tự wrapper JDK:

```java
import org.springframework.core.task.TaskDecorator;

public final class RequestIdTaskDecorator implements TaskDecorator {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    @Override
    public Runnable decorate(Runnable callback) {
        String captured = REQUEST_ID.get(); // Thread GỬI.
        return () -> {
            String previous = REQUEST_ID.get(); // Thread CHẠY.
            try {
                if (captured == null) {
                    REQUEST_ID.remove();
                } else {
                    REQUEST_ID.set(captured);
                }
                callback.run();
            } finally {
                if (previous == null) {
                    REQUEST_ID.remove();
                } else {
                    REQUEST_ID.set(previous);
                }
            }
        };
    }
}
```

Đây là **một class Java đầy đủ về cấu trúc**, nhưng ứng dụng muốn sử dụng cùng `REQUEST_ID` phải có API để set/get tại thread gửi; ví dụ đơn giản này giấu trường `private` nhằm tập trung vào thuật toán. Phần tiếp theo dùng MDC, là API có sẵn cho ứng dụng Spring Boot.

Kết nối với một executor Spring:

```java
@Bean("businessExecutor")
public ThreadPoolTaskExecutor businessExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(64);
    executor.setThreadNamePrefix("business-");
    executor.setTaskDecorator(new RequestIdTaskDecorator());
    return executor;
}
```

Đây là **đoạn method cấu hình minh họa**, cần import `@Bean`, `ThreadPoolTaskExecutor` và đặt trong class cấu hình. Không gọi `initialize()` thủ công nếu `ThreadPoolTaskExecutor` thực sự được đăng ký thành Spring bean; Spring quản lý lifecycle thông thường.

**Hai nhầm lẫn cần tránh:**

- `decorate()` chạy ở **ranh giới giao task**; giá trị lấy trong `decorate` mới là snapshot từ thread gửi. Nếu `decorate` chỉ trả một lambda và lambda mới gọi `ThreadLocal.get()` để “capture”, bạn đã quá muộn.
- `TaskDecorator` có thể bọc **execution callback nội bộ** như `FutureTask`, không nhất thiết là `Runnable`/`Callable` ban đầu. Với `submit`, exception có thể được `FutureTask` giữ lại thay vì thoát ra khỏi `callback.run()`. Vì vậy `try-finally` chắc chắn có ích để cleanup, nhưng `catch` trong decorator **không** phải cơ chế bắt mọi lỗi business. Phải quan sát `Future.get`, `CompletableFuture.whenComplete` hoặc error handler thích hợp.

## 8. Copy và phục hồi toàn bộ MDC thay vì chỉ một key

Trong ứng dụng dùng SLF4J/Logback, MDC giúp gắn các cặp `traceId`, `requestId`, `tenant` với log của thread. Khi truyền MDC sang executor, có thể copy toàn bộ map tại thời điểm gửi và **khôi phục toàn bộ map trước đó của worker**.

Một `TaskDecorator` đúng:

```java
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

public final class MdcSnapshotTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable callback) {
        // T0: snapshot ngay tại thread gửi.
        Map<String, String> captured = MDC.getCopyOfContextMap();

        return () -> {
            // T1: lưu map hiện tại của thread sẽ chạy callback.
            Map<String, String> previous = MDC.getCopyOfContextMap();

            try {
                // T2: cài snapshot, kể cả khi submitter không có MDC.
                restore(captured);
                callback.run();
            } finally {
                // T3: phục hồi đúng trạng thái trước callback.
                restore(previous);
            }
        };
    }

    private static void restore(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(map);
        }
    }
}
```

Điểm cần chú ý:

- `MDC.getCopyOfContextMap()` cung cấp bản **copy** của context map khi backend MDC hỗ trợ. Không lưu `MDC` như một singleton mutable map dùng chung giữa thread.
- Khi snapshot là `null` hoặc rỗng, `MDC.clear()` trên worker **trước** khi chạy callback; nếu không, log có thể mang dấu vết task trước.
- `previous` được lấy **sau khi worker bắt đầu**, ngay trước khi cài snapshot, và được khôi phục trong `finally`; cách này cho phép wrapper lồng nhau hoặc chạy trực tiếp cùng thread.
- `MDC.setContextMap` thay thế context map hiện tại theo backend hỗ trợ. Đừng chỉ gọi `MDC.put("traceId", captured)` rồi bỏ nguyên các key `tenant`/`user` còn sót ở worker.
- Không log hoặc propagate tự động các giá trị nhạy cảm như access token, mật khẩu, session cookie. Chỉ đưa vào MDC những trường được chính sách bảo mật cho phép.
- Nếu ứng dụng dùng một logging backend không có MDC đúng nghĩa, hãy kiểm tra khả năng hỗ trợ thực tế. Ví dụ Spring Boot bên dưới dùng Logback qua starter để có MDC hoạt động.

### Vì sao khôi phục **map trước** chứ không `MDC.clear()` cuối cùng?

Giả sử task A đang chạy với map `{traceId=A, spanId=1}`, rồi gọi một callback B trực tiếp trên **cùng thread** với `{traceId=B, spanId=2}`. Nếu B kết thúc bằng `MDC.clear()`, A mất toàn bộ thông tin. Nếu B lưu `previous={traceId=A,spanId=1}` rồi `MDC.setContextMap(previous)` trong `finally`, A tiếp tục với map vốn có.

Điều này đặc biệt quan trọng khi executor dùng `CallerRunsPolicy`, test dùng `Runnable::run`, hoặc thư viện có callback lồng nhau chạy trên completion thread.

## 9. Ví dụ Spring Boot chạy được: MDC qua `@Async` với thread pool tái sử dụng

Ví dụ **Java 17+**, **Spring Boot 3.5.7** (Spring Framework 6.2.x), Maven. Chương trình không mở HTTP server; nó mô phỏng ba request khác nhau trên main thread, gọi một Spring `@Async` service qua proxy, rồi kiểm tra kết quả MDC trên worker:

1. Request A gửi với `traceId=req-A`, sau đó caller đổi sang `req-B`.
2. Request B gửi với `traceId=req-B`.
3. Caller xóa MDC; task thứ ba phải thấy `<none>`, không được thấy A hoặc B.
4. Method `@Async` ném lỗi; caller thấy exceptional Future. Decorator vẫn khôi phục MDC dù lỗi.

### 9.1. Cấu trúc dự án

```text
mdc-async-demo/
├── pom.xml
└── src/main/java/com/example/mdcdemo/
    ├── MdcAsyncApplication.java
    ├── AsyncConfiguration.java
    ├── MdcTaskDecorator.java
    └── ContextService.java
```

### 9.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>mdc-async-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
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
```

Spring Boot starter này kéo theo các thành phần AOP, Spring Context và logging backend mặc định đủ cho ví dụ `MDC`. Không cần cấu hình `@WebMvcTest` hoặc servlet container để thực hành `@Async`.

### 9.3. `src/main/java/com/example/mdcdemo/MdcTaskDecorator.java`

```java
package com.example.mdcdemo;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

public final class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable callback) {
        final Map<String, String> snapshot = MDC.getCopyOfContextMap();

        return () -> {
            final Map<String, String> previous = MDC.getCopyOfContextMap();

            try {
                restore(snapshot);
                callback.run();
            } finally {
                restore(previous);
            }
        };
    }

    private static void restore(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
```

### 9.4. `src/main/java/com/example/mdcdemo/AsyncConfiguration.java`

```java
package com.example.mdcdemo;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean("contextExecutor")
    public ThreadPoolTaskExecutor contextExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("context-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }
}
```

Một worker duy nhất được dùng để dễ chứng minh **tái sử dụng thread**. Trong production, phải sizing core/max/queue theo tải thực và deadline; không lấy giá trị `1/1/8` của ví dụ làm mặc định.

### 9.5. `src/main/java/com/example/mdcdemo/ContextService.java`

```java
package com.example.mdcdemo;

import java.util.concurrent.CompletableFuture;

import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ContextService {

    @Async("contextExecutor")
    public CompletableFuture<String> readTrace() {
        String traceId = MDC.get("traceId");
        return CompletableFuture.completedFuture(
                traceId == null ? "<none>" : traceId);
    }

    @Async("contextExecutor")
    public CompletableFuture<String> fail() {
        throw new IllegalStateException("demo failure");
    }
}
```

`readTrace()` thực thi trên worker do Spring proxy chọn. Trong body, `CompletableFuture.completedFuture(...)` **không** tạo task bất đồng bộ thứ hai. Với Spring, Future từ proxy cho caller có thể khác object Future tạm do method đích tạo. `fail()` ném ngoại lệ trong worker, Future phía caller hoàn thành ngoại lệ; error handler dành cho `@Async void` không phải nơi xử lý lỗi này.

### 9.6. `src/main/java/com/example/mdcdemo/MdcAsyncApplication.java`

```java
package com.example.mdcdemo;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.MDC;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MdcAsyncApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication app = new SpringApplication(MdcAsyncApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(Map.of("logging.level.root", "OFF"));

        try (ConfigurableApplicationContext context = app.run(args)) {
            ContextService service = context.getBean(ContextService.class);

            MDC.put("traceId", "req-A");
            var first = service.readTrace();

            // Sau submit A, caller đổi MDC; worker vẫn dùng snapshot A.
            MDC.put("traceId", "req-B");
            System.out.println("first=" +
                    first.get(5, TimeUnit.SECONDS));
            System.out.println("second=" +
                    service.readTrace().get(5, TimeUnit.SECONDS));

            MDC.clear();
            System.out.println("empty=" +
                    service.readTrace().get(5, TimeUnit.SECONDS));

            try {
                service.fail().get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                System.out.println("futureError=" +
                        ex.getCause().getClass().getSimpleName());
            }
        } finally {
            MDC.clear();
        }
    }
}
```

Tại thư mục chứa `pom.xml`:

```bash
mvn -q spring-boot:run
```

**Bốn dòng do `System.out` chủ động in khi hoàn tất bình thường:**

```text
first=req-A
second=req-B
empty=<none>
futureError=IllegalStateException
```

Build tool hoặc môi trường có thể in thêm thông tin của chúng. Ví dụ tắt banner/root logging để tập trung vào output, nhưng không phụ thuộc tên thread để quyết định logic nghiệp vụ.

Giải thích cơ chế:

- `first=req-A`: `TaskDecorator.decorate()` chụp MDC trước khi Spring đặt task vào queue, do đó caller đổi thành `req-B` không làm thay đổi snapshot của A.
- `second=req-B`: task thứ hai được submit sau khi caller đặt B, do đó worker nhận B dù cùng một thread đã xử lý A.
- `empty=<none>`: khi submitter không có MDC, decorator **xóa tạm MDC trên worker** thay vì để lại trace từ task trước.
- `futureError=IllegalStateException`: task thất bại nhưng Future phản ánh lỗi. Decorator chạy `finally` để phục hồi MDC của worker, kể cả khi callback thực thi không phát exception ra ngoài do wrapper Future.

Không thể suy ra “đã chứng minh durable job” từ ví dụ: tất cả task vẫn nằm trong bộ nhớ JVM. Muốn job tiếp tục sau restart, cần DB/queue/broker và thiết kế recovery riêng.

## 10. `ContextPropagatingTaskDecorator` của Spring 6.1+: khi nào dùng thay wrapper tự viết?

Spring Framework 6.1+ có `org.springframework.core.task.support.ContextPropagatingTaskDecorator`. Nó tích hợp cơ chế **Micrometer Context Propagation** để chụp/cài lại một số context được đăng ký, đặc biệt hữu ích cho logging/observation context.

Ví dụ định hướng:

```java
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
```

Đây là **đoạn minh họa**, còn cần cấu hình pool và lifecycle. Trong Spring Boot production, thường đăng ký executor thành bean; phụ thuộc Micrometer/context-propagation và các `ThreadLocalAccessor`/context accessor cần được cấu hình phù hợp với context bạn thực sự muốn truyền.

**Không hiểu nhầm** rằng thêm một `ContextPropagatingTaskDecorator` sẽ tự copy **mọi** `ThreadLocal` tùy biến hoặc mọi đối tượng trong request. Chỉ các context có cơ chế/accessor phù hợp mới được chụp và khôi phục. Cần kiểm thử thực tế với trace/MDC của stack hiện dùng; tùy backend và tích hợp, có thể cần dependency hoặc configuration bổ sung.

Một decorator tự viết như `MdcTaskDecorator` có thể hợp lý cho một nhu cầu **cụ thể, đơn giản** và được kiểm thử rõ. Với stack observability phong phú, ưu tiên tích hợp chính thức có context snapshot/accessors hơn là tự nhân bản mỗi loại context bằng code rời rạc.

Lưu ý hiệu năng: capture/restore context có chi phí phân bổ object/copy map. Đặc biệt với hàng triệu task cực nhỏ, hãy đo overhead và quyết định có cần truyền đầy đủ hay chỉ một vài trường tối thiểu.

## 11. Spring Security: không sao chép `SecurityContext` như một Map bình thường

`SecurityContextHolder` theo cấu hình phổ biến dựa trên `ThreadLocal`. Một task `@Async` không tự nhiên có cùng authentication như request thread, và việc mất context có thể làm downstream authorization hoặc audit sai.

Spring Security cung cấp các wrapper chuyên dụng:

- `DelegatingSecurityContextRunnable` và `DelegatingSecurityContextCallable`.
- `DelegatingSecurityContextExecutor`.
- `DelegatingSecurityContextTaskExecutor`.
- `DelegatingSecurityContextAsyncTaskExecutor`.

Các lớp này tổ chức việc gắn/cleanup `SecurityContext` quanh task và giúp tích hợp với Spring `TaskExecutor`. Chúng phù hợp hơn việc tự `SecurityContextHolder.setContext(...)` rồi quên restore/clear.

**Các giới hạn bảo mật:**

1. Chỉ truyền authentication cần thiết sau khi xác thực/ủy quyền; **không** “nâng quyền” cho worker vì caller có một role nào đó.
2. `SecurityContext` và authentication có thể chứa mutable object/reference. Không mặc định rằng truyền context là một **deep immutable snapshot**. Nếu cần snapshot không thay đổi, chuyển thành một DTO claim tối thiểu hoặc dùng cơ chế framework phù hợp.
3. Tác vụ chạy lâu sau request có thể giữ thông tin quyền không còn mới. Đối với tác vụ nhạy cảm, cần xác định điểm kiểm tra quyền ở **thời điểm thực thi** và chính sách hết hạn/quyền bị thu hồi.
4. Không lưu access token, session secret hoặc thông tin cá nhân nhạy cảm vào MDC/log chỉ để tiện propagation.
5. Với kết nối hệ thống khác, truyền đúng identity/authorization theo hợp đồng; sao chép `ThreadLocal` không thay thế xác thực hoặc ủy quyền ở downstream.

Ví dụ **minh họa** nếu đã có một bean `AsyncTaskExecutor delegate`:

```java
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

var secured =
        new DelegatingSecurityContextAsyncTaskExecutor(delegate);
```

Đoạn này cần dependency Spring Security và `delegate` đúng kiểu trong context; nó không phải chương trình độc lập. Nên làm rõ wrapper nào đang chịu trách nhiệm snapshot/cleanup, tránh kết hợp nhiều decorator tự viết dễ ghi đè context lẫn nhau.

## 12. Những gì **không** nên propagate: transaction và request resource

### Transaction không phải một map cần copy

Spring transaction thường được liên kết với thread và tài nguyên thực (JDBC connection, transaction manager, entity manager). `@Transactional` đang mở ở thread nhận request **không tự lan** sang worker chỉ vì ta copy trace ID. Cố gắng chuyển `TransactionSynchronizationManager` hoặc cùng một JDBC connection/EntityManager sang thread khác có thể phá tính đúng đắn, lifecycle và an toàn thread.

**Cách đúng:** truyền dữ liệu/ID cần thiết, để worker có **transaction boundary riêng** khi cần thao tác DB. Nếu worker phải chạy sau khi transaction caller commit, thiết kế thời điểm enqueue phù hợp, ví dụ after-commit publication hoặc transactional outbox; không giả lập transaction bằng copy ThreadLocal.

### `RequestContextHolder` và Servlet request

`RequestAttributes` hoặc `HttpServletRequest` là dữ liệu gắn với vòng đời request và container. Một task nền có thể chạy **sau khi response kết thúc**, khi request object đã hết hiệu lực hoặc bị giải phóng/tái sử dụng. Không mang cả request scope sang thread pool và giả định dùng được mãi.

Thay vào đó:

- Chụp các **giá trị nguyên thủy hoặc DTO bất biến** cần thiết khi request còn hợp lệ.
- Xác thực/ủy quyền ngay khi nhận yêu cầu; thiết kế lại kiểm tra quyền khi worker thực thi nếu cần.
- Không giữ session/resource/stream còn phụ thuộc HTTP response vào job dài.
- Có TTL và cơ chế dọn context nếu cần kết nối tới một hệ thống durable job.

### `InheritableThreadLocal` không giải quyết bài toán thread pool

`InheritableThreadLocal` giúp child thread mới có thể thừa hưởng giá trị lúc tạo thread, **không** cung cấp snapshot riêng cho mỗi task trên một worker **đã được tạo và tái sử dụng**. Thread pool tái sử dụng worker có thể giữ giá trị từ lúc worker được khởi tạo, dẫn đến context cũ/sai. Không dùng nó như cách thay thế cơ chế capture/restore per-task.

## 13. Nhiều lần đổi thread: decorator có được áp dụng ở mọi stage không?

Một pipeline gồm `@Async` và `CompletableFuture` có thể đi qua nhiều executor:

```text
HTTP thread (MDC A)
      |
      | @Async("contextExecutor")
      v
Spring contextExecutor worker (MDC A)
      |
      | thenApplyAsync(...) KHÔNG truyền executor
      v
ForkJoinPool.commonPool worker (MDC ???)
```

`TaskDecorator` gắn với **một `ThreadPoolTaskExecutor` cụ thể** chỉ bọc những task được **gửi qua chính executor đó**. Nếu một continuation dùng `thenApplyAsync` không truyền executor, JDK thường dùng common pool; decorator của Spring executor **không tự chạy** trên common pool.

Để chủ động duy trì context ở ranh giới cụ thể:

```java
CompletableFuture<Dto> dtoFuture =
        source.thenApplyAsync(this::mapToDto, contextExecutor);
```

Đoạn minh họa yêu cầu `source`, `mapToDto`, `contextExecutor` có tồn tại. Nếu `contextExecutor` được cấu hình với `MdcTaskDecorator`, việc submit continuation qua nó sẽ capture context **của thread thực sự lên lịch callback** tại thời điểm submit. Nhưng **không** mặc định rằng thread lên lịch callback luôn là HTTP thread ban đầu. Nếu dependency source hoàn tất từ một thread không có MDC, snapshot có thể null. Vì vậy khi cần logical request context đi xuyên **toàn bộ graph**, hãy xét cơ chế `ContextSnapshot`/propagation được framework hỗ trợ và thiết kế điểm tạo từng stage cho đúng.

Các phương thức không có hậu tố `Async` như `thenApply` hoặc `whenComplete` có thể chạy trên thread hoàn thành source hoặc thread đăng ký callback; không cam kết chạy trên worker nào. Đừng dùng MDC như nguồn dữ liệu nghiệp vụ bắt buộc nếu stage có thể được thực thi từ nhiều thread khác nhau; truyền tham số tường minh sẽ chắc chắn hơn.

### Nên ưu tiên truyền dữ liệu nghiệp vụ qua tham số

```java
record WorkContext(String requestId, String tenantId) {}

CompletableFuture<Result> process(WorkContext context, Input input) {
    return service.computeAsync(context, input);
}
```

Đây là **đoạn minh họa**, cần định nghĩa `Result`, `Input` và `service`. Một `record` bất biến với String phù hợp để truyền rõ logical context qua nhiều stage. ThreadLocal/MDC chủ yếu phục vụ tích hợp logging/observability và một số framework API; không thay thế dữ liệu nghiệp vụ được truyền tường minh.

## 14. Ngoại lệ và `FutureTask`: tại sao `try-finally` vẫn đúng dù `catch` không thấy lỗi?

Với executor `submit(Callable<T>)`, task thực tế mà `TaskDecorator` thấy có thể là `FutureTask`. Khi `Callable` ném exception, `FutureTask.run()` thường **bắt và giữ** exception để `Future.get()` phát `ExecutionException`. Wrapper kiểu:

```java
return () -> {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    try {
        restore(captured);
        callback.run();
    } catch (Exception ex) {
        logFailure(ex); // Không bảo đảm bắt được lỗi của Callable.
    } finally {
        restore(previous);
    }
};
```

Đây chỉ là đoạn **minh họa**, các biến `captured`, `callback`, `logFailure` và `restore` thuộc ứng dụng. Nếu callback là `FutureTask`, lỗi của `Callable` có thể không thoát khỏi `callback.run()` và `catch` không được kích hoạt. Tuy nhiên `finally` vẫn chạy **khi callback.run() trả về bình thường**, nên restore **vẫn hoạt động**.

Với `CompletableFuture`, lỗi nghiệp vụ thường được biểu diễn bằng exceptional completion. Bên gọi cần:

```java
future.whenComplete((value, failure) -> {
    if (failure != null) {
        metrics.recordFailure(failure);
    }
});
```

Đây là đoạn minh họa có `future` và `metrics` do ứng dụng định nghĩa. Với phương thức `@Async void`, dùng `AsyncUncaughtExceptionHandler` hoặc cơ chế quan sát của ứng dụng. Trách nhiệm **phục hồi context** và **phát hiện lỗi nghiệp vụ** là hai việc khác nhau; đừng để logic cleanup phụ thuộc vào `catch`.

Nếu lỗi xảy ra **trước khi delegate task được nhận** (executor từ chối hoặc shutdown), wrapper chưa chạy thì cũng chưa cài context lên worker; code caller phải xử lý rejection (`TaskRejectedException`/`RejectedExecutionException`) theo hợp đồng. Không hứa đã nhận job khi executor không nhận.

## 15. Nhiều decorator, thứ tự và ownership

Trong một ứng dụng thực tế, một task có thể cần truyền MDC, trace/observation và SecurityContext. Hướng tốt thường là:

1. **Xác định ngữ cảnh thực sự cần** cho mỗi loại task; không copy mọi thứ một cách mù quáng.
2. **Ưu tiên wrapper/TaskDecorator có sẵn của framework** (Micrometer/Spring Security) cho các loại context đã có contract.
3. Nếu nhiều decorator, dùng `CompositeTaskDecorator` hoặc xây một decorator kết hợp có thứ tự rõ; kiểm tra việc chụp/khôi phục khi có nested wrappers.
4. Chỉ một tầng chịu trách nhiệm reset từng loại context, tránh hai decorator cùng ghi đè cùng map mà không biết thứ tự.
5. Với JDK `Executor` tự viết, chọn một adapter/wrapper ở **ranh giới executor** thay vì copy code `set/clear` rải rác trong từng business method.
6. Cần quan sát `TaskRejectedException`, timeout và cancellation ở ranh giới gửi việc; decorator không phải durable queue hay cơ chế retry.

Đặc biệt: nếu bạn dùng `CallerRunsPolicy` hoặc một direct executor trong test, wrapper có thể chạy **ngay trên thread submitter**. Vì vậy `restore previous` trong `finally` bắt buộc phải đúng trong cả mô hình worker độc lập lẫn cùng thread.

## 16. Cách kiểm thử đúng mà không cần `sleep`

Để test context propagation, **đừng** dựa vào việc các worker vô tình hoàn tất theo một thứ tự nào đó. Dùng `CountDownLatch` và `Future.get(timeout)` để chủ động điều khiển:

| Bài kiểm tra | Thiết lập | Điều kiện đúng |
| --- | --- | --- |
| Capture đúng lúc | Giữ worker bằng latch, submit snapshot A, đổi caller sang B, thả worker | Task vẫn thấy A |
| Thread reuse | Pool 1 worker; task A xong rồi task B | B chỉ thấy snapshot B |
| Null snapshot | Caller không có context, worker đã có baseline | Task thấy null, không thấy baseline |
| Restoration | Chụp baseline của worker trước task, đọc lại bằng raw executor sau task | Baseline được giữ nguyên |
| Task thất bại | Task ném exception qua FutureTask | Caller thấy lỗi, worker vẫn được restore |
| Nested / inline | Dùng `Runnable::run`, callback đổi ThreadLocal | Caller vẫn giữ giá trị trước callback |
| CallerRuns | Pool bị đầy, policy chạy callback trên caller | Caller context được khôi phục sau callback |
| Nhiều key MDC | A có `{traceId,tenant}`, B thiếu `tenant` | B không mang `tenant` của A |
| Cấu trúc mutable | Thread gửi thay map sau submit | Snapshot không bị đổi ngoài ý muốn |
| Async graph | `@Async` rồi `thenApplyAsync` qua executor khác | Mọi boundary cần thiết đều có propagation hoặc tham số rõ |

Với JUnit 5, có thể kiểm thử deterministic cho wrapper:

```java
@Test
void inlineExecutionRestoresCaller() {
    ThreadLocal<String> current = new ThreadLocal<>();
    current.set("caller");

    Executor wrapped = task -> {
        String snapshot = current.get();
        Runnable delegated = () -> {
            String previous = current.get();
            try {
                current.set(snapshot);
                task.run();
            } finally {
                if (previous == null) {
                    current.remove();
                } else {
                    current.set(previous);
                }
            }
        };
        delegated.run(); // Direct execution.
    };

    wrapped.execute(() -> current.set("inner"));
    assertEquals("caller", current.get());
    current.remove();
}
```

Đây là **đoạn test minh họa**, cần import `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions.assertEquals` và `java.util.concurrent.Executor`, đồng thời dependency JUnit 5. Với một `ThreadLocal` thực dùng chung trong ứng dụng, hãy viết fixture/extension cleanup `afterEach` để test không rò context qua các test khác.

Khi kiểm thử Spring `@Async`, **phải gọi qua Spring proxy từ bean/test context khác**. Gọi `this.asyncMethod()` từ cùng bean bỏ qua async advice theo proxy mode mặc định, khiến test “pass” trên cùng thread nhưng không kiểm chứng propagation thực.

## 17. Các anti-pattern cần nhận diện

| Anti-pattern | Sai ở đâu? | Cách sửa |
| --- | --- | --- |
| Chụp `ThreadLocal.get()` **trong `run()`** | Đọc giá trị của worker, không phải thread gửi | Chụp trước `executor.execute`/trong `decorate` |
| `ThreadLocal.set(snapshot); task.run();` không `finally` | Task lỗi sẽ để sót dữ liệu trên worker | `try-finally` luôn khôi phục previous |
| Cuối task luôn `remove()` | Phá context của wrapper lồng nhau / caller-run | Lưu `previous` trước, restore đúng giá trị cũ |
| Chỉ `put` key mới, không xóa key cũ trong MDC | Key từ request trước tiếp tục tồn tại | Restore **toàn bộ map** hoặc clear khi snapshot rỗng |
| Dùng tham chiếu `HashMap` mutable làm snapshot | Thread gửi sửa map, worker thấy dữ liệu đổi | Copy phòng vệ/DTO bất biến |
| Propagate mọi ThreadLocal tự động | Mang token/session/transaction không nên chuyển | Allowlist context, kiểm tra bảo mật/lifecycle |
| Dùng `InheritableThreadLocal` cho pooled workers | Snapshot ở lúc **tạo thread**, không phải mỗi task | Decorator per-task hoặc tham số tường minh |
| Tưởng `@Async` tự truyền MDC | Worker khác thread, MDC có thể null/sai | Gắn TaskDecorator hoặc tích hợp propagation |
| Dùng `supplyAsync()` không executor sau decorated `@Async` | Callback vào common pool, mất context đã được truyền | Chọn executor tường minh hoặc context snapshot cho stage |
| Gọi `MDC.clear()` trong business method mà không restore | Phá context của caller/nested stage | Wrapper capture/restore trong `finally` |
| Copy `TransactionSynchronizationManager` sang worker | Sai ranh giới transaction và resource | Worker tự mở transaction, truyền dữ liệu cần thiết |
| Chụp `HttpServletRequest` để chạy job lâu | Resource hết vòng đời request | Snapshot DTO bất biến/ID |
| Dựa vào catch của `TaskDecorator` để bắt mọi lỗi Future | `FutureTask` giữ exception bên trong | Quan sát Future và dùng error handler |
| Dùng thread pool không giới hạn và copy context lớn | Tốn memory, backlog và độ trễ | Bounded queue, tối giản snapshot, metrics |
| Truyền cùng một mutable SecurityContext sang nhiều task | Race/quyền không nhất quán | Spring Security wrapper, identity snapshot tối thiểu |
| Quên cleanup trong integration test | Test kế tiếp thừa hưởng MDC/request ID | Teardown/fixture với `finally` hoặc `afterEach` |

## 18. Checklist production và vận hành

Trước khi deploy một cơ chế propagation:

- **Phạm vi:** context nào được phép truyền? `traceId`, `requestId`, locale, tenant, authentication? Cái nào phải đi theo tham số rõ ràng?
- **Thời điểm:** snapshot được tạo ở **submit thread**, không phải worker?
- **Sao chép:** snapshot có độc lập với dữ liệu mutable sau submit không?
- **Cài:** worker có được xóa các key cũ khi snapshot trống không?
- **Phục hồi:** có lưu `previous` **trên thread thực thi**, và `finally` chạy cho cả success/error/cancellation/inline?
- **Nested execution:** chạy trực tiếp hoặc `CallerRunsPolicy` có giữ context của code bao ngoài không?
- **Mọi boundary:** `@Async`, `Executor.submit`, `CompletableFuture.supplyAsync/thenApplyAsync`, scheduled tasks, event listeners có đi qua decorator đúng nơi không?
- **Bảo mật:** tránh copy token, request object, transaction và dữ liệu cá nhân không cần thiết; tái xác thực khi cần.
- **Tài nguyên:** snapshot có nhỏ và TTL hợp lý? Executor có queue giới hạn? Rủi ro memory nếu task backlog lâu?
- **Lỗi:** `Future` completion/error được quan sát riêng, không dựa vào `catch` của decorator; rejection được xử lý tại submit.
- **Khả năng quan sát:** log trace ID ở submit và worker, tỷ lệ task không có trace mong đợi, ngữ cảnh lẫn tenant, queue wait, executor active/queue/rejection.
- **Kiểm thử:** thread reuse, submitter đổi context trước worker chạy, null snapshot, exception, nested, async graph, shutdown và overload.

Một ứng dụng có tracing chuẩn nên ưu tiên cơ chế propagation có sẵn, nhưng vẫn phải hiểu thuật toán capture/install/restore. Khi có lỗi “trace của người A xuất hiện trong tác vụ người B”, nguyên nhân thường là **không clear snapshot rỗng, thiếu restore ở finally, hoặc bọc sai ranh giới executor**.

## 19. Câu hỏi tự kiểm tra

**1.** Vì sao phải lấy `MDC.getCopyOfContextMap()` ở `decorate()` chứ không trong `run()`?  
**Đáp:** `decorate()` diễn ra khi giao task từ thread gửi. `run()` chạy trên worker, có thể nhiều giây sau và có MDC khác. Chụp trong `run()` mất quan hệ với request ban đầu.

**2.** Tại sao trước khi cài snapshot phải lưu `previous` của worker?  
**Đáp:** Để sau task trả lại đúng context mà thread thực thi đã có, kể cả nested wrapper hoặc executor chạy trên caller; chỉ clear có thể phá context của code bao ngoài.

**3.** Caller không có trace, worker còn `traceId=A` từ task trước. Worker nên thấy gì trong task mới?  
**Đáp:** Không có trace (`null`/`<none>`); snapshot rỗng phải **xóa tạm** MDC cũ. Cuối callback khôi phục `previous` theo contract wrapper; ở ứng dụng tốt worker không nên lưu trace rác ngoài mọi task.

**4.** Task ném exception trong worker. `finally` có chạy không?  
**Đáp:** Có khi luồng điều khiển rời `try` bình thường bằng exception, với điều kiện JVM không bị chấm dứt bất thường. Vì vậy restore đặt trong `finally`, không chỉ ở nhánh thành công.

**5.** `FutureTask` giữ exception của `Callable`. `catch` trong decorator luôn bắt lỗi đó chứ?  
**Đáp:** Không. `FutureTask.run()` có thể không ném lỗi ra ngoài; caller phải quan sát `Future.get()`. `finally` vẫn được thực hiện quanh `run()`.

**6.** Một `ThreadLocal<Map>` đang trỏ tới `HashMap`, giữ tham chiếu này trong snapshot có đủ không?  
**Đáp:** Không nếu map được sửa sau submit. Cần copy phòng vệ (và cân nhắc deep copy/immutable values).

**7.** Một `@Async` method gọi `CompletableFuture.supplyAsync()` không truyền executor. Decorator của Spring pool có tự bọc supplier này không?  
**Đáp:** Không. Supplier có thể chạy trên JDK common pool, nằm ngoài decorator của bean Spring. Phải cấu hình từng boundary hoặc truyền context bằng cơ chế phù hợp.

**8.** Có nên copy `@Transactional` context từ HTTP thread sang worker?  
**Đáp:** Không. Transaction và resource có lifecycle/thread binding; worker nên có transaction riêng và truyền dữ liệu/ID rõ ràng.

**9.** `CallerRunsPolicy` khiến task chạy ngay trên thread gửi. Tại sao restore vẫn cần?  
**Đáp:** Task có thể thay đổi `ThreadLocal` của **chính HTTP thread**; `finally` phải trả lại context gốc sau callback.

**10.** `ContextPropagatingTaskDecorator` có tự truyền mọi `ThreadLocal` do ứng dụng tạo không?  
**Đáp:** Không. Cần cơ chế/accessor phù hợp cho context muốn truyền; phải kiểm thử theo stack thực, không giả định một decorator tự hiểu mọi giá trị.

## 20. Ghi nhớ và tài liệu chính thức

**Công thức căn bản của context propagation trên executor:**

```text
SUBMITTER: snapshot = captureCurrentContext()  // chụp trước khi queue
WORKER:    previous = captureCurrentContext()  // lưu trạng thái hiện tại
           try {
               install(snapshot)               // kể cả snapshot rỗng
               runOriginalTask()
           } finally {
               restore(previous)               // không rò, không phá ngữ cảnh ngoài
           }
```

Một wrapper đúng phải chịu được **pool reuse, task lỗi, snapshot null, inline execution, nested context** và nhiều ranh giới `CompletableFuture`. Khi cần bọc context chuyên biệt như SecurityContext/observability, dùng integration chính thức của Spring/Spring Security/Micrometer và kiểm thử hiệu quả thực tế.

**Nguồn tham khảo:**

- [Java SE 17 — `ThreadLocal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): phạm vi dữ liệu theo thread, `get`, `set`, `remove`.
- [Java SE 17 — `Executor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html): hợp đồng gửi task, executor có thể chạy inline.
- [Spring Framework — `TaskDecorator` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/task/TaskDecorator.html): callback wrapper và giới hạn bắt exception với Future.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): cấu hình `ThreadPoolTaskExecutor.setTaskDecorator` và `@Async`.
- [Spring Framework — `ContextPropagatingTaskDecorator` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html): Micrometer context capture/restore.
- [Spring Framework — Observability Support](https://docs.spring.io/spring-framework/reference/integration/observability.html): kết hợp task execution và context propagation.
- [Spring Security — Concurrency Support](https://docs.spring.io/spring-security/reference/6.5/features/integrations/concurrency.html): wrappers bảo vệ `SecurityContext`.
- [SLF4J — MDC API](https://www.slf4j.org/apidocs/org/slf4j/MDC.html): `getCopyOfContextMap`, `setContextMap`, `clear`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-context-005">Truyền mã liên kết yêu cầu và ngữ cảnh ghi nhật ký</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền mã liên kết yêu cầu và ngữ cảnh ghi nhật ký trong Spring Framework

Khi một HTTP request đi qua controller rồi được giao cho `@Async`, `ThreadPoolTaskExecutor` hoặc `CompletableFuture`, mã liên kết yêu cầu (*correlation ID*) thường bị mất khỏi các dòng log ở worker thread. Nguyên nhân không phải Spring “đánh rơi” một tham số thông thường, mà vì nhiều thư viện logging lưu **Mapped Diagnostic Context (MDC)** theo **thread**. Thread xử lý HTTP và thread lấy task từ pool không nhất thiết là một. Thread pool còn **tái sử dụng worker**, nên nếu không dọn context đúng cách, một request có thể bị gắn nhầm mã của request trước.

Mục tiêu của bài học thuộc nhóm **Ngữ cảnh thực thi** của module **Spring Framework – concurrency** là xây dựng cách truyền correlation ID và logging context qua ranh giới bất đồng bộ **đúng thời điểm, đúng phạm vi, có cleanup**, đồng thời phân biệt với distributed tracing và security context. Những quy tắc này giúp truy vết một yêu cầu qua controller, executor, worker và các dịch vụ tiếp theo mà không trộn log của nhiều khách hàng.

> Nguyên tắc cốt lõi: **correlation ID là dữ liệu định danh luồng xử lý; MDC chỉ là một phương tiện gắn dữ liệu đó vào log của thread hiện tại.** Nếu muốn giá trị đi qua một task hoặc một hệ thống khác, phải có cơ chế truyền riêng; không thể dựa vào việc cùng một JVM hay cùng một request.

## 1. Correlation ID giải quyết vấn đề gì?

Một request tạo báo cáo có thể trải qua nhiều bước:

```text
                         X-Correlation-ID: req-8f2
                                      |
                                      v
Client ----HTTP----> Spring MVC Filter ----> Controller / Service
                           |                       |
                           | MDC[corrId=req-8f2]   | submit
                           v                       v
                     Log request thread     ThreadPoolTaskExecutor
                                                    |
                                       [queue] -> pooled worker
                                                    |
                                         MDC cần được truyền
                                                    |
                                            Database / API
                                                    |
                                          outbound HTTP header
                                                    |
                                                    v
                                              Downstream service
```

Nếu các dòng log có cùng mã `req-8f2`, hệ thống quan sát có thể tìm toàn bộ sự kiện liên quan đến cùng một yêu cầu, kể cả khi các bước thực thi trên nhiều thread hoặc nhiều dịch vụ.

Ví dụ log minh họa:

```text
INFO  [http-nio-8080-exec-2] [corr=req-8f2] Received report request
INFO  [report-1]            [corr=req-8f2] Started report generation
INFO  [report-1]            [corr=req-8f2] Stored report result
```

Các tên thread ở ví dụ chỉ minh họa; thread thực tế, thời điểm và thứ tự log phụ thuộc executor/scheduler.

**Không có correlation ID**, việc tra các dòng log chỉ theo timestamp, endpoint hoặc tên worker rất dễ sai khi ứng dụng xử lý hàng nghìn task song song. **Có correlation ID nhưng truyền sai**, còn nguy hiểm hơn vì log của hai yêu cầu có thể bị nhập chung, khiến quá trình điều tra sai nguyên nhân.

### 1.1. Ba yêu cầu của một correlation ID tốt

1. **Ổn định trong phạm vi logic cần truy vết.** Các bước xử lý cùng một request/job nên biết cùng ID hoặc quan hệ parent-child rõ ràng.
2. **Có thể truyền qua ranh giới.** HTTP header, queue message metadata, job payload và task wrapper đều có cơ chế cụ thể.
3. **Không chứa bí mật hoặc PII.** Tránh email, token, số thẻ, số điện thoại hay nội dung nghiệp vụ nhạy cảm làm correlation ID. Mã cần đủ khó trùng và có validation/giới hạn độ dài nếu lấy từ client.

Correlation ID **không phải** bằng chứng authentication/authorization; bất kỳ người dùng nào cũng có thể gửi một HTTP header giả nếu hệ thống công khai cho phép. Dùng ID để tìm log, **không dùng nó làm điều kiện cấp quyền**.

## 2. Phân biệt correlation ID, request ID, job ID, trace ID và span ID

Các hệ thống dùng những từ gần nhau nhưng phạm vi khác nhau. Không nên ép một biến vào mọi vai trò.

| Định danh | Phạm vi điển hình | Dùng để làm gì? |
| --- | --- | --- |
| **Request ID** | Một HTTP request tại một service/edge | Tìm log hoặc lỗi gắn với request cụ thể |
| **Correlation ID** | Một luồng nghiệp vụ có thể qua nhiều bước | Gom log của các bước liên quan, có thể qua async/broker |
| **Job ID** | Một tác vụ nền được theo dõi | Truy vấn trạng thái QUEUED/RUNNING/SUCCEEDED/FAILED |
| **Trace ID** | Một distributed trace | Liên kết các span theo chuẩn tracing |
| **Span ID** | Một bước/operation trong trace | Định danh hoạt động con, đo latency/parent-child |
| **Idempotency key** | Một yêu cầu logic có thể gửi lại | Tránh thực hiện tác động nghiệp vụ hai lần |

Ví dụ một client POST tạo báo cáo với `X-Correlation-ID=req-8f2`. Server có thể tạo `jobId=job-92` rồi trả HTTP 202. Nếu worker chạy sau khi HTTP response đã kết thúc, log của worker có thể lưu cả `correlationId=req-8f2` và `jobId=job-92`. Một client khác gọi `GET /jobs/job-92` tạo **request ID mới**, nhưng vẫn liên hệ được với cùng job.

**Distributed tracing** thường dùng chuẩn W3C Trace Context như `traceparent` và `tracestate`; `traceId`/`spanId` có định dạng và ngữ nghĩa riêng. Một custom header `X-Correlation-ID` **không thay thế** tracing instrumentation. Nếu dùng Micrometer Tracing/OpenTelemetry, ưu tiên quy tắc propagation của tracing framework; có thể ghi thêm correlation ID khi nghiệp vụ cần.

## 3. MDC là gì? Vì sao dễ mất khi sang thread khác?

`org.slf4j.MDC` cung cấp API để đặt/đọc các cặp key-value dùng trong logging context:

```java
MDC.put("correlationId", "req-8f2");
String id = MDC.get("correlationId");
MDC.remove("correlationId");
MDC.clear();
```

Đây là **fragment**, cần SLF4J API và một backend hỗ trợ MDC như Logback trong project. MDC không phải API lưu trạng thái request ở database; nó lưu ngữ cảnh được logging adapter gắn với thread hiện tại.

Trong mô hình SLF4J/Logback phổ biến, MDC dùng thread-local context:

```text
Thread HTTP-A     MDC = { correlationId: req-A }
Thread HTTP-B     MDC = { correlationId: req-B }
Thread worker-1   MDC = { }          // Chưa tự nhận req-A
Thread worker-2   MDC = { }          // Chưa tự nhận req-B
```

Từ đó có hai lỗi đối nghịch:

- **Không truyền:** worker log không có mã `correlationId`, dù caller đang có.
- **Không dọn:** worker giữ MDC của task trước; lần sau có thể gắn nhầm `req-A` vào log của task `req-B`.

Hãy phân biệt:

- **ThreadLocal:** cơ chế lưu dữ liệu theo thread; không tự gắn dữ liệu vào `Runnable`/`Callable` được giao qua executor.
- **MDC:** API logging abstraction có backend cụ thể. Với Logback, MDC thường gắn với thread-local, do đó cần sao chép/khôi phục khi chuyển thread.
- **InheritableThreadLocal:** có thể truyền snapshot khi *tạo thread con* trong một số tình huống, nhưng **không phù hợp làm lời giải mặc định cho thread pool tái sử dụng**. Worker thường được tạo trước khi request tới, và dữ liệu có thể rò rỉ giữa các request.
- **Reactor Context:** context theo luồng reactive subscription; không đồng nhất với `ThreadLocal`. WebFlux có thể đổi thread, nên cần cơ chế tích hợp context propagation riêng.

**Không có ràng buộc:** một object `CompletableFuture` không tự chuyển toàn bộ MDC của thread caller sang mọi dependent callback.

## 4. Vì sao ThreadPoolTaskExecutor cần cách truyền có chủ đích?

`ThreadPoolTaskExecutor` của Spring quản lý một pool worker và hàng đợi. Khi controller gọi:

```java
executor.execute(() -> generateReport(reportId));
```

`execute` giao một `Runnable` cho executor. Worker sẽ chạy task vào một thời điểm khác, có thể trên một thread tồn tại sẵn. MDC của controller được đặt trên thread request, **không tự nằm trong worker**.

Một cách giải quyết là **chụp (capture) context tại thời điểm submit** và **cài đặt (install) context đó khi worker bắt đầu chạy**. Cuối cùng **khôi phục context có trước của worker**, kể cả khi task ném exception:

```text
Request thread:  MDC[corr=req-8f2]
       |
       | submit
       v
TaskDecorator chụp snapshot {corr=req-8f2}
       |
       | executor queue
       v
Worker thread:
    previous = snapshot MDC hiện có của worker
    try:
        install {corr=req-8f2}
        run business task
    finally:
        restore previous
```

Với `ThreadPoolTaskExecutor`, Spring cung cấp `TaskDecorator` để bọc `Runnable` được executor nhận. Đây là điểm thích hợp để triển khai chính sách context capture/restore tập trung cho các task đi qua bean executor đó.

### 4.1. Vì sao phải chụp lúc submit, không phải lúc run?

Nếu chỉ gọi `MDC.get("correlationId")` ở trong worker, bạn đang đọc **context của worker**, vốn không có request ID hoặc chứa dữ liệu cũ. Snapshot phải lấy từ nơi còn context đúng: **thread đang giao task**.

Ví dụ sai:

```java
executor.execute(() -> {
    String id = MDC.get("correlationId"); // Có thể null hoặc stale
    log.info("job {}", id);
});
```

Ví dụ đúng về mặt nguyên lý:

```java
String capturedId = MDC.get("correlationId");
executor.execute(() -> {
    String previous = MDC.get("correlationId");
    try {
        if (capturedId == null) MDC.remove("correlationId");
        else MDC.put("correlationId", capturedId);
        doWork();
    } finally {
        if (previous == null) MDC.remove("correlationId");
        else MDC.put("correlationId", previous);
    }
});
```

Hai đoạn là **fragment**, cần executor/logger/`doWork` và imports thích hợp. Trong dự án có nhiều task, nên **không chép thủ công** đoạn wrapper này vào từng call site; một `TaskDecorator` có kiểm tra rõ ràng dễ duy trì hơn.

### 4.2. Tại sao restore thay vì chỉ clear?

Nếu worker trước khi task bắt đầu đã có một context hợp lệ do một wrapper bên ngoài đặt, gọi `MDC.clear()` sau task có thể xóa mất context của scope bao ngoài. **Restoring previous** xử lý cả hai trường hợp:

- Trước task không có MDC → dọn về rỗng.
- Trước task có MDC → khôi phục đúng context đó.

Điều này đặc biệt hữu ích khi có **decorator lồng nhau**, instrumentation hoặc một executor wrapper khác. Không được để một task ghi dữ liệu context rồi quên cleanup khi có exception.

## 5. Java 17 runnable: chứng minh ThreadLocal không tự truyền và cần cleanup

Ví dụ thuần JDK không phụ thuộc Spring/SLF4J. Nó dùng `ThreadLocal<String>` để mô phỏng một trường `correlationId` trong MDC của Logback. Một single-thread executor cho thấy giá trị ở caller **không tự xuất hiện** trên worker. Sau đó một wrapper **capture → install → restore** truyền đúng ID và làm sạch sau khi task kết thúc.

Lưu chương trình vào **ContextBoundaryDemo.java**:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContextBoundaryDemo {
    private static final ThreadLocal<String> REQUEST_ID =
            new ThreadLocal<>();

    private static String current() {
        String id = REQUEST_ID.get();
        return id == null ? "absent" : id;
    }

    private static void assign(String value) {
        if (value == null) {
            REQUEST_ID.remove();
        } else {
            REQUEST_ID.set(value);
        }
    }

    private static Runnable capture(Runnable action) {
        // Dòng này chạy ở thread SUBMIT, trước khi có worker.
        String captured = REQUEST_ID.get();

        return () -> {
            String previous = REQUEST_ID.get();
            try {
                assign(captured);
                action.run();
            } finally {
                assign(previous);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "pooled-worker"));

        try {
            REQUEST_ID.set("req-123");

            // Không truyền: worker không thấy ThreadLocal của caller.
            pool.submit(() ->
                    System.out.println("without: " + current())).get();

            // Wrapper chụp req-123 khi submit, rồi đặt vào worker.
            pool.submit(capture(() ->
                    System.out.println("with: " + current()))).get();

            // Worker không bị rò rỉ req-123 sang task kế tiếp.
            pool.submit(() ->
                    System.out.println("after: " + current())).get();

            System.out.println("caller: " + current());
        } finally {
            REQUEST_ID.remove();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy với **JDK 17+**:

```bash
javac ContextBoundaryDemo.java
java ContextBoundaryDemo
```

**Stdout xác định của chương trình:**

```text
without: absent
with: req-123
after: absent
caller: req-123
```

Phân tích:

1. `REQUEST_ID.set("req-123")` chỉ đặt giá trị trên main thread. `ThreadLocal` thường không thừa kế giá trị sang worker, nên dòng đầu là `without: absent`.
2. `capture(...)` chạy **trên main thread lúc tạo wrapper**, giữ snapshot `req-123` trước khi giao cho pool.
3. Wrapper đặt ID trong worker, chạy action, rồi `finally` **restore `previous`**. Vì worker chưa có giá trị nên restore bằng `remove()`.
4. Task kế tiếp dùng lại single-thread pool và thấy `after: absent`, chứng minh wrapper không để lại context.
5. Main thread vẫn thấy `caller: req-123` cho tới khi `finally` dọn giá trị của main.
6. Mỗi lệnh `Future.get()` đợi task trước hoàn thành, vì vậy thứ tự các dòng in không phụ thuộc may rủi scheduling. Không cần `Thread.sleep` để giả định lúc nào worker chạy xong.

**Giới hạn:** demo minh họa `ThreadLocal`, **không phải implementation của SLF4J MDC**. Trong Spring/Logback, thay vì một `String`, thường chụp cả `Map<String,String>` qua `MDC.getCopyOfContextMap()`. Cần xử lý snapshot `null` (không có map) khác với map có dữ liệu và restore đúng context của worker.

## 6. Spring TaskDecorator để sao chép và khôi phục MDC

Một `TaskDecorator` triển khai cơ chế snapshot một cách tập trung:

```java
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

public final class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        // Capture trên thread submit, không phải trong worker.
        Map<String, String> captured = MDC.getCopyOfContextMap();

        return () -> {
            // Có thể có context hợp lệ của scope ngoài.
            Map<String, String> previous = MDC.getCopyOfContextMap();

            try {
                if (captured == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(captured);
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
    }
}
```

Class trên có thể được biên dịch trong dự án có **Spring Core và SLF4J MDC backend**. Nó cũng được dùng trong ứng dụng hoàn chỉnh ở phần sau (bài Spring Boot dùng class cùng ý tưởng).

### Vì sao các chi tiết này quan trọng?

- `MDC.getCopyOfContextMap()` lấy **snapshot**, tránh truyền một map dùng chung để hai thread sửa cùng một object.
- `decorate(...)` được gọi khi executor nhận `Runnable` để tạo wrapper; trong trường hợp này nó capture MDC trên luồng submit. Thời điểm capture phải nằm **trước** khi request filter dọn context.
- Khi worker chạy, code chụp `previous` trước khi ghi đè. Sau task, `finally` restore nguyên trạng. Nếu task ném lỗi, `finally` vẫn chạy.
- `captured == null` nghĩa không có map; ở worker cần **clear** để không log bằng ID cũ.
- `MDC.setContextMap(...)` dùng một bản đồ string keys/values; không nên đưa object nghiệp vụ mutable hay secret vào MDC.

Với task cực ngắn và tần suất rất cao, capture/restore context có overhead. Cần đo trong trường hợp đặc biệt, nhưng **không bỏ cleanup để “tối ưu”**. Có thể chỉ propagate những key cần thiết thay vì toàn bộ map khi yêu cầu bảo mật và hiệu năng chặt chẽ.

### 6.1. Không capture một lần khi tạo bean executor

Một lỗi tinh vi:

```java
Map<String, String> startupMdc = MDC.getCopyOfContextMap();

executor.setTaskDecorator(task -> () -> {
    MDC.setContextMap(startupMdc);
    task.run();
});
```

Đây là **phản ví dụ, không dùng trong production**; `setContextMap` còn không chấp nhận `null` an toàn trong mọi backend. `startupMdc` được chụp **một lần lúc startup**, thường trống, chứ không phải tại mỗi task submission. Nó cũng thiếu restore trong `finally`.

**Đúng:** đặt lệnh `MDC.getCopyOfContextMap()` **trong `TaskDecorator.decorate(...)`**, rồi restore previous trong wrapper. Không lưu ID của một request vào field singleton của executor hoặc service.

### 6.2. TaskDecorator chỉ bao trùm task qua đúng executor

Nếu một method dùng `@Async("workExecutor")`, decorator của `workExecutor` có thể áp dụng. Nhưng nếu code gọi:

```java
CompletableFuture.supplyAsync(() -> compute());
```

mà **không truyền executor**, JDK thường dùng `ForkJoinPool.commonPool()` (với quy tắc fallback riêng). Pool đó **không tự đi qua `TaskDecorator` của Spring `workExecutor`**.

Nếu cần continuation chạy qua executor có propagation:

```java
CompletableFuture.supplyAsync(() -> compute(), workExecutor);
```

Đây là fragment, cần `compute` và `workExecutor` được khai báo. Đồng thời, `whenComplete`/`thenApply` **không có hậu tố Async** có thể chạy trên thread hoàn tất hoặc registration thread tùy timing. Không nên dựa vào việc “đã từng đi qua một executor” để khẳng định mọi callback sau này sẽ có MDC. Khi cần bảo đảm rõ ràng, truyền correlation ID như dữ liệu, hoặc dùng context-aware executor cho **từng điểm scheduling** quan trọng.

## 7. Spring Boot 3.x runnable: filter + executor + @Async + MDC

Ví dụ sau minh họa end-to-end trong **Spring Boot 3.x / Spring Framework 6.x / Java 17+**, với dependency `spring-boot-starter-web` (bao gồm Spring MVC và logging thông dụng). Nó có bốn thành phần:

1. `OncePerRequestFilter` lấy hoặc tạo `X-Correlation-ID`, validate và đặt vào MDC trên request thread.
2. `ThreadPoolTaskExecutor` có `TaskDecorator`, capture/restore MDC cho worker.
3. `WorkService` chạy method `@Async("workExecutor")`; log trên worker và trả lại mã thực sự nhìn thấy.
4. `WorkController` gọi bean service khác qua proxy, dùng correlation ID từ request để kiểm tra kết quả.

Đặt **toàn bộ mã dưới đây** vào **src/main/java/com/example/demo/CorrelationApplication.java**:

```java
package com.example.demo;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

@SpringBootApplication
@EnableAsync
public class CorrelationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CorrelationApplication.class, args);
    }

    @Bean("workExecutor")
    ThreadPoolTaskExecutor workExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(20);
        pool.setThreadNamePrefix("work-");
        pool.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        pool.setTaskDecorator(new MdcTaskDecorator());
        return pool; // Spring quản lý initialize và shutdown của bean
    }
}

final class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> captured = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (captured == null) MDC.clear();
                else MDC.setContextMap(captured);
                runnable.run();
            } finally {
                if (previous == null) MDC.clear();
                else MDC.setContextMap(previous);
            }
        };
    }
}

@Component
class CorrelationFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Correlation-ID";
    private static final String KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);

        // Chỉ nhận ID ngắn, không có ký tự điều khiển/CRLF.
        String correlationId =
                incoming != null
                && incoming.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                ? incoming
                : UUID.randomUUID().toString();

        String previous = MDC.get(KEY);
        try {
            MDC.put(KEY, correlationId);
            response.setHeader(HEADER, correlationId);
            chain.doFilter(request, response);
        } finally {
            if (previous == null) MDC.remove(KEY);
            else MDC.put(KEY, previous);
        }
    }
}

record WorkResult(
        String jobId,
        String requestCorrelationId,
        String workerCorrelationId) {}

@Service
class WorkService {
    private static final Logger log =
            LoggerFactory.getLogger(WorkService.class);

    @Async("workExecutor")
    public CompletableFuture<WorkResult> process(
            String jobId, String requestCorrelationId) {
        String workerCorrelationId = MDC.get("correlationId");
        log.info("Processing job {}", jobId);

        WorkResult result = new WorkResult(
                jobId, requestCorrelationId, workerCorrelationId);
        return CompletableFuture.completedFuture(result);
    }
}

@RestController
class WorkController {
    private static final Logger log =
            LoggerFactory.getLogger(WorkController.class);
    private final WorkService service;

    WorkController(WorkService service) {
        this.service = service;
    }

    @GetMapping("/work/{id}")
    public CompletableFuture<WorkResult> process(
            @PathVariable String id) {
        String correlationId = MDC.get("correlationId");
        log.info("Accepted work {}", id);
        return service.process(id, correlationId);
    }
}
```

**Lưu ý về cấu trúc:** tất cả các class package-private đặt chung một file với public class `CorrelationApplication` là hợp lệ trong Java. Dự án cần Spring Boot 3.x, logging backend mặc định hoặc backend tương đương và HTTP stack Jakarta Servlet. Không thể dùng `javac CorrelationApplication.java` khi không có dependencies Spring.

Ví dụ chạy trong project có Gradle wrapper:

```bash
./gradlew bootRun
curl -i -H "X-Correlation-ID: req-demo-01" \
  http://localhost:8080/work/J7
```

Trên Windows PowerShell có thể dùng `gradlew.bat bootRun` rồi chạy riêng lệnh `curl.exe`; dấu `\` nối dòng trong ví dụ shell phía trên dành cho bash, không phải cú pháp tiếp dòng của PowerShell.

Khi request được nhận và worker hoàn thành, **phần JSON kết quả** có giá trị tương đương:

```json
{
  "jobId": "J7",
  "requestCorrelationId": "req-demo-01",
  "workerCorrelationId": "req-demo-01"
}
```

**Vì sao có thể quan sát điều đó?**

1. Filter nhận một mã hợp lệ và đặt `MDC["correlationId"]` trên servlet thread.
2. Controller đọc mã trong MDC và truyền **tường minh** thành tham số `requestCorrelationId` vào service.
3. Controller gọi bean `WorkService` khác, lời gọi `@Async` được Spring proxy intercept và giao task cho `workExecutor`.
4. `TaskDecorator.decorate(...)` capture snapshot MDC **tại lúc task được submit**.
5. Worker chạy wrapper và cài MDC trước khi gọi method thực, nên `MDC.get("correlationId")` trong `WorkService` là `req-demo-01`.
6. Sau khi task kết thúc, wrapper restore MDC trước đó của worker, tránh rò rỉ sang task tiếp theo.
7. Filter restore giá trị cũ của servlet thread sau khi rời scope xử lý request.

Dù method `WorkService.process` trả `CompletableFuture.completedFuture(...)` trong worker, **Spring MVC vẫn phản hồi khi `CompletableFuture` trả về từ controller đã hoàn tất**. Đây là response của endpoint demo, không phải giao thức job `202 Accepted`. JSON được tạo từ **giá trị truyền tường minh và giá trị worker quan sát**; không phụ thuộc việc callback serialization của Spring MVC chạy ở thread nào.

### 7.1. Hiện correlation ID trong log với Logback

MDC chỉ hữu ích khi layout log **đọc key tương ứng**. Trong Spring Boot dùng Logback, có thể cấu hình mẫu console log, ví dụ trong `src/main/resources/application.properties`:

```properties
logging.pattern.console=%d{HH:mm:ss.SSS} %-5level [%thread] [corr=%X{correlationId}] %logger{36} - %msg%n
```

Đây là cấu hình minh họa dùng Logback pattern `%X{correlationId}`. Log thực tế có timestamp/tên thread khác nhau, nhưng khi filter và decorator hoạt động đúng, request và worker có thể cùng có đoạn:

```text
[corr=req-demo-01] ... Accepted work J7
[corr=req-demo-01] ... Processing job J7
```

Không dự đoán tên thread hoặc thứ tự tuyệt đối của mọi log trong xử lý bất đồng bộ. Nếu dùng JSON structured logging, thêm trường `correlationId` từ MDC theo cấu hình encoder, **đừng ghi ID bằng cách nối chuỗi thủ công ở mọi log statement** nếu backend đã hỗ trợ field.

### 7.2. Nếu client không gửi header?

Filter dùng `UUID.randomUUID().toString()` tạo mã mới, đặt vào MDC và trả trong response header `X-Correlation-ID`. Client có thể lưu mã đó để cung cấp khi báo lỗi. Giá trị cụ thể thay đổi mỗi request và **không thể coi là stdout cố định**.

Nếu client gửi giá trị chứa dấu xuống dòng, quá dài hoặc ký tự ngoài regex, filter thay bằng UUID do server tạo. Đây là một biện pháp đơn giản tránh log injection và header abuse; trong production còn có thể muốn giới hạn theo chính sách gateway và chuẩn ID của tổ chức.

### 7.3. Cần hiểu giới hạn servlet async dispatch

`OncePerRequestFilter` mặc định có cách chọn những dispatcher types cần lọc; quá trình Spring MVC trả `CompletableFuture` có thể bao gồm **async dispatch** sau khi filter scope ban đầu đã kết thúc. Demo chỉ cần filter đặt MDC **lúc ban đầu controller submit task**, nên `TaskDecorator` capture kịp.

Không suy ra MDC sẽ tự có ở **mọi filter invocation, error dispatch, log của framework sau response hay async dispatch thread**. Nếu cần correlation ID tại tất cả các giai đoạn servlet async, phải thiết kế propagation/filtering theo dispatcher type/lifecycle phù hợp, hoặc dùng observability/context propagation integration hỗ trợ.

## 8. Cơ chế khởi tạo và chọn executor trong Spring

Trong mẫu trên:

```java
@Bean("workExecutor")
ThreadPoolTaskExecutor workExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new MdcTaskDecorator());
    return executor;
}
```

Đây là **fragment rút gọn**; trong ví dụ đầy đủ còn có sizing và rejection policy. `ThreadPoolTaskExecutor` là Spring bean, container có thể quản lý initialization và shutdown theo lifecycle. `@Async("workExecutor")` chọn chính executor có decorator đó.

Các trường hợp MDC không được truyền:

- Một method `@Async` chọn bean executor **khác** không có decorator.
- Code `CompletableFuture.supplyAsync(fn)` không truyền executor và chạy trên default JDK facility.
- Code tự gọi `this.asyncMethod()` bên trong cùng bean trong proxy mode nên **không qua Spring async proxy**.
- Một thư viện bên ngoài tự tạo thread và không dùng executor được trang bị propagation.
- A callback `thenApply(...)` non-async thực thi inline trên thread không có MDC theo thời điểm hoàn tất.
- Scheduler/message listener khởi chạy task khi chưa tạo correlation ID cho job/message gốc.

Cần hiểu **đường gọi thực tế** và **executor thực tế**. Không được gán trách nhiệm cho `TaskDecorator` nếu task không hề đi qua executor đó.

### 8.1. Rejection xảy ra lúc nào?

`ThreadPoolTaskExecutor` có giới hạn pool và queue. Nếu dùng `AbortPolicy` và executor quá tải hoặc đã shutdown, việc submit có thể phát `TaskRejectedException`. Task bị từ chối **không chạy worker wrapper**, nhưng request thread đang submit vẫn có MDC, nên log tại điểm catch có thể có correlation ID:

```java
try {
    workExecutor.execute(task);
} catch (TaskRejectedException ex) {
    log.warn("Executor rejected a task", ex);
    // Phản hồi 503/429 hoặc lưu job bền vững tùy hợp đồng.
}
```

Đây là fragment, giả định `log`, `task` và `workExecutor` tồn tại. Không dùng một handler discard im lặng rồi hứa rằng mọi task đã thực thi. Việc trace rejection quan trọng vì log trên worker sẽ **không tồn tại** cho task chưa được nhận.

### 8.2. TaskDecorator và exception của Future

Spring lưu ý `TaskDecorator` có thể nhận một wrapper `Runnable` thay vì user-supplied task nguyên bản; với lời gọi `submit`, wrapper có thể là một `FutureTask` che exception. Vì vậy đừng coi `TaskDecorator` là nơi đáng tin để **bắt mọi exception nghiệp vụ** bằng `catch (Throwable)` rồi ghi log. Để quan sát lỗi, dùng Future/CompletableFuture callback, `@Async` exception handler phù hợp hoặc error handler ở tầng công việc.

**Trách nhiệm của decorator là thiết lập/khôi phục context**, không phải biến nó thành nơi xử lý mọi kết quả nghiệp vụ.

## 9. Truyền correlation ID qua CompletableFuture chain

Ví dụ:

```java
CompletableFuture<String> stage =
        CompletableFuture.supplyAsync(() -> fetchData(), workExecutor)
                .thenApply(this::transform);
```

`supplyAsync(..., workExecutor)` được scheduling qua executor có decorator, nhưng `thenApply` **không có hậu tố Async** có thể chạy trên completion thread hoặc registration thread tùy timing. Khi callback cần logging context, phải hiểu nơi thực thi:

- Nếu upstream chưa hoàn tất, callback non-async có thể chạy trước khi worker wrapper của upstream restore MDC, nên quan sát được context trong worker.
- Nếu upstream đã hoàn tất trước khi callback đăng ký, nó có thể chạy inline trên caller thread; context phụ thuộc caller đó.
- Nếu stage được hoàn tất từ một thread khác hoặc context đã được restore, callback có thể không thấy MDC mong đợi.
- Dùng `thenApplyAsync(fn, workExecutor)` khi thật sự cần dispatch qua executor đã trang bị context propagation. **Tuy nhiên capture diễn ra ở thread submit continuation**, thread đó phải có context cần truyền.

### Không cho rằng propagation ở upstream sẽ “lây” tự động mãi mãi

```java
CompletableFuture<String> next =
        upstream.thenApplyAsync(this::work, workExecutor);
```

Đây là fragment. Nếu `upstream` chưa hoàn tất, hành động scheduling continuation có thể xảy ra trên thread hoàn tất upstream. `TaskDecorator` chụp **MDC đang có trên thread scheduling đó**, không nhất thiết là MDC của HTTP request ban đầu. Nếu upstream được hoàn tất từ một foreign executor không mang context, việc chỉ truyền `workExecutor` chưa chắc làm ID “hồi sinh”.

Khi yêu cầu correlation nhất quán qua nhiều stage, có những lựa chọn rõ ràng:

1. Truyền **correlation ID như dữ liệu**: `record Context(jobId, correlationId)` đi cùng payload, không phụ thuộc thread.
2. Bọc callback với snapshot context **tại thời điểm còn biết ID đúng**, rồi restore khi chạy.
3. Dùng context-aware executor hoặc cơ chế context propagation có hỗ trợ chính thức, kiểm tra mọi ranh giới asynchronous.
4. Tránh dựa vào callback timing/thread identity vì `CompletableFuture` không cam kết non-async stage chạy trên một thread cố định.

### 9.1. Truyền như dữ liệu khi job sống lâu hơn HTTP request

Một cách ít phụ thuộc MDC là định nghĩa:

```java
record JobContext(
        String jobId,
        String correlationId,
        String traceId) {}

record WorkItem(JobContext context, String payload) {}
```

Đây là **fragment record Java 17**. Worker nhận `WorkItem`, đưa `context.correlationId()` vào MDC cho **scope xử lý của job**, rồi cleanup. Nếu job được lưu trong database/broker, metadata có thể được lưu/publish cùng job và được phục hồi sau restart.

Điểm lợi là một job dài vài phút không cần giữ HTTP request object hoặc servlet thread. **Không lưu toàn bộ security context, access token hoặc session vào job chỉ để có một correlation ID.**

## 10. Ngoài JVM: propagate correlation header qua dịch vụ khác

`MDC.put(...)` chỉ thay đổi logging context của thread hiện tại. Nó **không tự thêm header** vào outgoing HTTP request và **không tự đi vào Kafka/RabbitMQ message**.

Ví dụ Java 17 standard `HttpClient`:

```java
String correlationId = MDC.get("correlationId");

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://service.example.test/data"))
        .header("X-Correlation-ID",
                correlationId == null ? "unknown" : correlationId)
        .GET()
        .build();
```

Đây là **fragment**, cần `java.net.URI`, `java.net.http.HttpRequest` và SLF4J MDC. Dùng `"unknown"` chỉ để giải thích; hệ thống thật nên **tạo ID hợp lệ ở ranh giới ingress hoặc job** thay vì dùng mã chung gây nhập log nhiều request.

Tốt hơn, tích hợp một outbound interceptor phù hợp với client đang sử dụng, ví dụ Spring `RestClient`/`WebClient`/`RestTemplate` hoặc HTTP client do thư viện cung cấp, và dùng một **CorrelationContext** đã được xác định đúng theo request/job. Với message broker, đặt correlation ID trong **headers/message metadata** khi publish và đọc nó khi consume.

```text
Service A:
  MDC[corr=req-8f2]  --> outgoing HTTP header / message header
                                       |
                                       v
Service B:
  ingress filter / listener reads corr=req-8f2
  validates value and chooses trust policy
  sets MDC for local scope
  runs work, propagates onward if needed
  finally restores/clears MDC
```

**Quan trọng:** service B không nên xem correlation ID như một identity đã xác thực; header có thể do client bất kỳ tạo. Trace/span context có quy tắc propagation riêng nếu dùng OpenTelemetry/Micrometer Tracing. Correlation ID dùng để nối log, không thay thế một trace có parent/child span chuẩn.

### 10.1. Với Kafka/RabbitMQ và scheduled jobs

- **Producer:** lấy mã từ request/job context và ghi vào message headers; không giả định MDC tự được serialize.
- **Consumer:** validate/normalize mã từ message, đặt vào MDC trong scope xử lý một message, rồi **restore/clear trong finally** để không rò sang message kế.
- **Scheduled job:** không có HTTP header; tạo một correlation ID cho mỗi lần kích hoạt/job logic và ghi vào metadata trước khi submit. Nếu một lượt schedule gửi hàng trăm task, xác định có cùng correlation ID cha hay thêm child/job ID.
- **Retry/redelivery:** nên giữ correlation ID ổn định cho cùng job logic nếu mục tiêu là truy vết cùng workflow, đồng thời ghi thêm `attempt` để phân biệt lần thử. Không dùng correlation ID làm idempotency key duy nhất.
- **Distributed trace:** nếu broker/client instrumentation hỗ trợ trace context, dùng propagation chuẩn thay vì tự tạo trace/span IDs bằng chuỗi ngẫu nhiên và cho rằng đã có distributed tracing.

## 11. Micrometer Context Propagation, tracing và Reactor Context

Trong hệ sinh thái Spring hiện đại, một hệ thống có thể dùng:

- **Micrometer Observation/Tracing** để tạo span và trace, log correlation với `traceId`/`spanId`.
- **Micrometer Context Propagation** để capture/restore một số context qua các ranh giới thread/reactive thông qua các accessor đã đăng ký.
- **`ContextPropagatingTaskDecorator`** của Spring để dùng cơ chế context snapshot khi đủ dependency và cấu hình thích hợp.
- **Reactor Context** để lưu dữ liệu theo chuỗi reactive thay vì dựa vào `ThreadLocal` của một event-loop worker.

Điều quan trọng không phải chọn một tên class thần kỳ, mà là hiểu **context nào đang được quản lý** và **điểm capture/restore nào được áp dụng**. Không mặc định bật Micrometer tracing sẽ copy mọi MDC custom key tự tạo hoặc toàn bộ security context qua mọi `CompletableFuture`/raw thread.

### 11.1. Khi nào cần context propagation framework?

Một `MdcTaskDecorator` đơn giản có thể đủ cho ứng dụng Spring MVC với một vài executor rõ ràng. Khi luồng xử lý đi qua nhiều loại boundary — servlet, async task, reactor operator, broker listener, tracing span — giải pháp framework có accessor/observation chuẩn dễ duy trì hơn, miễn là ứng dụng kiểm tra khả năng propagation trong phiên bản thư viện đang dùng.

**Không trộn nhầm:**

- `traceId` và `spanId` do tracing system quản lý; không tự ý thay thế bằng `correlationId`.
- `MDC` là ngữ cảnh hiển thị vào log, không phải context propagation protocol.
- `SecurityContextHolder` có chính sách khác; không nên sao chép authentication vào MDC.
- Reactor operator có thể chuyển thread theo scheduler; `MDC.get(...)` không tự phản ánh Reactor Context ở mọi callback.
- Logging có thể chạy ở thread khác khi dùng async appender; kiểm tra cơ chế MDC capture của logging backend và test end-to-end.

Đọc tài liệu theo **phiên bản Spring Framework/Spring Boot thực tế** trước khi ghép các giải pháp propagation với nhau để tránh double wrapping, mất context hoặc overhead không cần thiết.

## 12. Bảo mật và kiểm soát dữ liệu trong logging context

Correlation ID có vẻ vô hại nhưng vẫn là dữ liệu do client có thể kiểm soát. Một số nguyên tắc an toàn:

### 12.1. Validate header, không tin chuỗi tùy ý

Một header chứa `\r`/`\n`, ký tự điều khiển hoặc chuỗi dài hàng megabyte có thể gây log injection, tăng chi phí lưu log hoặc làm sai cấu trúc JSON. Hãy giới hạn:

- Độ dài (ví dụ tối đa 64 ký tự).
- Bộ ký tự cho phép (ví dụ chữ, số, `-`, `_`, dấu chấm).
- Số lượng header lặp lại.
- Chính sách khi không hợp lệ: tự tạo ID mới hoặc từ chối request theo yêu cầu API.

Đừng dùng request body, access token hoặc tên người dùng làm correlation ID.

### 12.2. MDC chỉ chứa thông tin cần truy vết

Không đặt password, authorization bearer token, cookie, dữ liệu thẻ thanh toán, nội dung email hay dữ liệu cá nhân nhạy cảm vào MDC chỉ để dễ debug. Context được copy sang worker/structured logger và có thể được gửi tới hệ thống lưu log tập trung.

Chỉ nên chứa các giá trị có phạm vi và retention rõ: `correlationId`, `jobId`, `component` hoặc request path đã được chuẩn hóa nếu hợp lệ. Nếu cần thông tin principal để phân quyền, dùng cơ chế bảo mật phù hợp và đánh giá propagation riêng.

### 12.3. Cross-tenant và ID collision

Nếu nhận ID trực tiếp từ internet, hai người dùng có thể cùng gửi `X-Correlation-ID=abc` để cố ý trộn log. Có thể:

- Tạo ID tin cậy **tại gateway** và không để client tùy ý ghi đè.
- Dùng ID server-generated kết hợp với external client-provided ID ở **trường riêng biệt**, có nhãn nguồn tin cậy.
- Không truy vấn dữ liệu nhạy cảm chỉ bằng một correlation ID.
- Dùng tenant/authorization policy chính thức trong API status; correlation ID không cho phép truy cập log hoặc job của người khác.

Nếu một hệ thống cần distributed tracing, tuân thủ chính sách sampling/trust/propagation tại ingress chứ không tùy tiện tin mọi trace context bên ngoài.

## 13. Cleanup, nested scopes và khả năng rò rỉ ngữ cảnh

### 13.1. Worker thread là tài nguyên được tái sử dụng

Giả sử pool có một worker:

```text
Task A: MDC.put("correlationId", "req-A")
        run...
        // Quên remove

Task B: không đặt MDC
        log.info("Processing")
        => log của B có thể mang corr=req-A (SAI)
```

Một lỗi chỉ xuất hiện dưới tải thực có thể khó tái hiện, vì vài lần test tạo thread mới không cho thấy việc tái sử dụng. Luôn có `finally`, kể cả khi task timeout, cancel hợp tác hoặc ném exception.

### 13.2. Restore toàn bộ map khi có nested contexts

Giả sử worker đang có context `{traceId=abc, component=outer}` từ một wrapper. Một decorator chỉ làm `MDC.remove("correlationId")` có thể giữ lại các key mới do task tự thêm; một decorator chỉ `MDC.clear()` có thể xóa cả `traceId` của scope ngoài.

Cách snapshot đầy đủ:

```java
Map<String, String> previous = MDC.getCopyOfContextMap();
try {
    MDC.setContextMap(captured);
    runnable.run();
} finally {
    if (previous == null) MDC.clear();
    else MDC.setContextMap(previous);
}
```

Đây là **fragment**, với giả thiết `captured` không null. Nếu null, cần `MDC.clear()` ở đầu như class `MdcTaskDecorator` hoàn chỉnh. Việc restore map giải quyết các key phát sinh trong task một cách tập trung.

### 13.3. Cleanup filter khi controller trả CompletableFuture

Một Spring MVC controller trả `CompletableFuture` có thể hoàn tất HTTP response sau khi servlet thread ban đầu đã thoát khỏi filter. Filter `finally` vẫn nên restore MDC trên thread của nó. Đừng giữ MDC mãi “để MVC còn log”; làm vậy sẽ rò rỉ ID sang request tiếp theo chạy trên cùng servlet thread.

Đối với log phát sinh ở MVC async/error dispatch sau đó, dùng cơ chế context propagation phù hợp cho dispatch mới hoặc framework observability. **MDC không phải kho trạng thái request bền vững xuyên mọi dispatch.**

## 14. Xử lý lỗi, cancellation và shutdown không làm mất dấu vết

Một request có thể gửi task vào executor rồi kết thúc trước khi worker chạy. Nếu client timeout, **task có thể vẫn tiếp tục**. Mã liên kết nên được giữ trong job payload hoặc context snapshot cho task đã được nhận; không phụ thuộc HTTP thread còn tồn tại.

Các tình huống cần kiểm thử:

| Tình huống | Điều cần giữ đúng |
| --- | --- |
| Task ném exception | Log ở worker có mã đúng; `finally` restore MDC |
| Task bị từ chối trước khi chạy | Log ở submitter có mã đúng; không gán trạng thái “đã chạy” |
| Task chờ queue lâu | Context được capture lúc submit, không phải lúc worker lấy task |
| Client request timeout | Job có thể tiếp tục; correlation/job ID lưu riêng nếu cần theo dõi |
| CompletableFuture callback ngoài executor | Không mặc định có MDC của request |
| Worker được tái sử dụng | Không được giữ lại correlation ID của task trước |
| App shutdown / task interrupt | Cleanup MDC dù thoát sớm trong nhánh lỗi |
| Retry cùng job | Giữ logical correlation ID, thêm attempt ID riêng |
| Cross-service HTTP | Đưa correlation ID vào header rõ ràng; receiver validate lại |
| Broker consumer | Đọc message headers, đặt MDC trong scope message, restore sau xử lý |

**Log của worker hoàn tất muộn** có thể xuất hiện sau HTTP 202/timeout; đây không phải vấn đề nếu hệ thống có job ID/correlation ID và state machine rõ ràng. Không diễn giải “client đã ngừng chờ” thành “worker chắc chắn không chạy nữa”.

### 14.1. @Async void và CompletableFuture exception

`@Async void` không trả Future cho caller; exception cần được quan sát qua `AsyncUncaughtExceptionHandler`/logging, và handler có thể chạy theo execution lifecycle riêng. Với method trả `CompletableFuture<T>`, caller nên gắn `whenComplete` hoặc `handle` phù hợp để ghi nhận kết quả.

`whenComplete` **không có hậu tố Async** có thể chạy trên thread hoàn tất future hoặc thread đăng ký, tùy timing. Nếu callback logging cần ID chắc chắn, hãy **mang correlation ID vào closure bằng một biến immutable** hoặc định tuyến callback qua context-aware executor; không tin `MDC.get` lúc callback luôn giống HTTP request.

Một giải pháp tường minh:

```java
String requestCorrelationId = MDC.get("correlationId");

future.whenComplete((result, error) -> {
    // Dùng requestCorrelationId làm dữ liệu truy vết,
    // không suy luận MDC của thread callback luôn có ID.
    if (error != null) {
        log.warn("Job failed, correlationId={}",
                requestCorrelationId, error);
    }
});
```

Đây là fragment; cần `future` và `log`. Để callback log theo MDC layout, vẫn cần đặt/restore MDC trong callback hoặc sử dụng context-aware scheduling; việc ghi ID tường minh vào message ở đây chỉ tránh suy luận sai về thread.

## 15. Kiểm thử propagation đúng cách

Chỉ nhìn hai dòng log có cùng ID trong một lần chạy **chưa đủ**. Cần kiểm thử cả trường hợp có nhiều request, lỗi và tái sử dụng thread.

### 15.1. JDK unit test ý tưởng

Với `ContextBoundaryDemo.java`, kiểm tra ba invariant:

1. Không bọc: worker không thấy request ID của caller.
2. Có wrapper: worker thấy đúng snapshot từ caller.
3. Sau wrapper: worker không giữ snapshot của request trước.

Mở rộng test bằng cách cố ý ném `RuntimeException` trong action được decorate, bắt qua `Future.get()` rồi submit task tiếp để kiểm tra MDC đã được restore nhờ `finally`.

### 15.2. Spring integration test

Với ứng dụng `CorrelationApplication`:

- Gửi request mang `X-Correlation-ID: req-A`, kiểm tra response body `requestCorrelationId=workerCorrelationId=req-A`.
- Gửi request mang `X-Correlation-ID: req-B` ngay sau đó, xác minh worker không mang `req-A`.
- Gửi request không có header, kiểm tra response header chứa ID do server tạo và body dùng cùng ID đó.
- Gửi header quá dài/ký tự không hợp lệ, kiểm tra filter không copy thẳng vào MDC/response.
- Cố tình làm `WorkService` ném exception; xác minh task sau vẫn có MDC đúng và không leak.
- Kiểm thử `@Async` gọi **qua bean khác** và đúng `workExecutor`; self-invocation không được dùng làm test propagation.
- Dùng pool core=1 trong test để tạo khả năng tái sử dụng cùng worker và giảm biến số lịch trình.
- Kiểm thử callback `CompletableFuture.thenApply` sau upstream hoàn tất để thấy registration thread có thể tham gia, không giả định MDC ở tất cả callback.

Với logging thực tế có thể dùng appender capture hoặc test instrumentation để kiểm tra MDC tại thời điểm log event được tạo, thay vì chỉ so sánh chuỗi format output bị tác động bởi appender async.

### 15.3. Production monitoring

Nên theo dõi cả chất lượng propagation:

- Tỷ lệ log/job thiếu `correlationId` ở điểm cần có.
- Các job/HTTP request có correlation ID bất thường, quá dài hoặc trùng lặp.
- Task ở worker có ID khác với metadata trong payload.
- Execution time, queue wait, rejection count và exception theo executor.
- Số lượng request/job kết thúc nhưng worker vẫn tiếp tục — có thể hợp lệ nhưng cần liên kết log.
- Tương quan giữa `correlationId`, `jobId`, `traceId`/`spanId` trong structured logs.

Không xuất toàn bộ MDC map hoặc payload người dùng vào metric labels: cardinality cực cao và rò rỉ dữ liệu. **Correlation ID thường có cardinality lớn**, phù hợp với log và trace lookup hơn là làm label chính cho mọi metric time series.

## 16. Các lỗi phổ biến

**Lỗi 1 — Tưởng MDC tự đi theo @Async.** ThreadPoolTaskExecutor dùng worker riêng; MDC thường thread-local và cần propagation.

**Lỗi 2 — Đọc MDC trong worker rồi nghĩ đó là ID của request submit.** Worker có thể chưa có context hoặc giữ ID cũ. Capture từ submitter.

**Lỗi 3 — Capture MDC một lần khi khởi tạo singleton executor.** Snapshot startup không phải snapshot từng task.

**Lỗi 4 — Đặt MDC ở worker nhưng không có finally.** Exception hoặc cancel hợp tác có thể làm rò rỉ context sang request khác.

**Lỗi 5 — Chỉ clear mà không restore previous.** Có thể phá scope của instrumentation/decorator bao ngoài.

**Lỗi 6 — Dùng InheritableThreadLocal để thay TaskDecorator trong pool tái sử dụng.** Giá trị chỉ liên quan thời điểm tạo thread, không phù hợp với mỗi lần submit.

**Lỗi 7 — Truyền toàn bộ MDC vô điều kiện.** Có thể sao chép PII/token và quá nhiều key không cần thiết; chọn phạm vi tối thiểu.

**Lỗi 8 — Tin rằng CompletablFuture.supplyAsync(...) không truyền executor dùng Spring pool.** Mặc định JDK async facility thường là commonPool, không đi qua TaskDecorator Spring.

**Lỗi 9 — Tin callback non-Async luôn chạy trên worker mang context.** Callback có thể chạy inline trên registration thread khi stage đã hoàn tất hoặc theo completion policy khác.

**Lỗi 10 — Tưởng @Async("workExecutor") áp dụng cho this.method() trong cùng bean.** Self-invocation ở proxy mode bỏ qua interceptor.

**Lỗi 11 — Chỉ đặt MDC mà không cấu hình log pattern/structured encoder.** Correlation ID không tự xuất hiện trong log output nếu layout không đọc field.

**Lỗi 12 — Dùng correlation ID làm authentication hoặc idempotency key độc lập.** ID do client có thể tự đặt và không chứng minh quyền hay tính duy nhất của tác động nghiệp vụ.

**Lỗi 13 — Không propagate outgoing HTTP/message headers.** MDC của Service A không tự xuất hiện trong Service B.

**Lỗi 14 — Tưởng custom correlation ID là trace context chuẩn.** W3C trace/span có ngữ nghĩa riêng; dùng tooling tracing đúng khi cần distributed trace.

**Lỗi 15 — Bọc task nhưng dùng trực tiếp một executor khác ở nhánh lỗi hoặc retry.** Không phải task nào cũng đi qua cùng decorator; cần inventory các execution boundaries.

**Lỗi 16 — Dùng correlation ID làm metric label cho mọi task.** Cardinality bùng nổ; nên log/trace theo ID và đo metric aggregate.

**Lỗi 17 — Giữ ServletRequest hoặc SecurityContext như mutable object trong job dài.** Request đã kết thúc, thread thay đổi; chỉ truyền dữ liệu đã được kiểm chứng và cần thiết.

**Lỗi 18 — Tin rằng TaskDecorator bắt hết exception của Callable/Future.** Với `FutureTask`, exception có thể được lưu trong Future; quan sát đường lỗi đúng thay vì chỉ `catch` quanh `Runnable.run()`.

## 17. Checklist triển khai cho một hệ thống Spring

- [ ] Quy định **tên và phạm vi ID**: correlation ID, request ID, job ID, trace ID khác nhau thế nào.
- [ ] Xác định điểm ingress tạo/validate ID: gateway, servlet filter, message listener hoặc scheduler.
- [ ] Dùng header nhất quán như `X-Correlation-ID` nếu có hợp đồng; không coi header do client gửi là trusted identity.
- [ ] Đặt MDC trong scope ngắn của request/task và **restore trong finally**.
- [ ] Đăng ký `TaskDecorator` cho đúng `ThreadPoolTaskExecutor` được `@Async` hoặc service sử dụng.
- [ ] Chụp MDC **trên submitting thread tại mỗi task**, không chụp singleton lúc startup.
- [ ] Khi task không có snapshot, **clear worker MDC** để ngăn dữ liệu cũ.
- [ ] Với `CompletableFuture`, rà soát các callback non-async/Async và explicit executor ở mỗi bước cần propagation.
- [ ] Với job/broker, truyền correlation ID trong **job payload/headers** và định nghĩa retry attempt.
- [ ] Với HTTP client, thêm outbound propagation và validation tại downstream ingress.
- [ ] Khi dùng Micrometer Tracing, không tự tạo trace/span IDs thay thế chuẩn; kiểm tra integrations context propagation.
- [ ] Không đặt secret/PII vào MDC; kiểm soát độ dài/ký tự header và retention log.
- [ ] Thiết lập Logback/structured logging đọc key `correlationId`.
- [ ] Kiểm thử request A/B trên **cùng worker tái sử dụng**, exception, shutdown và callback chạy ở thread khác.
- [ ] Log/metric executor rejection với correlation ID của caller, không chờ worker log một task chưa nhận.
- [ ] Giám sát tỷ lệ log thiếu hoặc sai correlation ID và tính toàn vẹn liên kết job/request/trace.

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Controller đặt `MDC.put("correlationId","req-1")` rồi gọi `@Async`. Worker chắc chắn đọc được `req-1` chưa?

**Đáp án:** Chưa. MDC thường là thread-local; cần TaskDecorator hoặc context propagation phù hợp, hoặc truyền ID như dữ liệu.

**Câu 2.** Tại sao phải chụp `MDC.getCopyOfContextMap()` trong `decorate` thay vì trong `run` của wrapper?

**Đáp án:** `decorate` được tạo tại điểm submit, nơi caller còn request context. `run` chạy trên worker, thường không có MDC của caller.

**Câu 3.** Vì sao cần `finally` dù task thường return bình thường?

**Đáp án:** Pool tái sử dụng worker; task có thể ném exception hoặc thoát sớm. `finally` bảo đảm restore/clear context, tránh rò ID sang task sau.

**Câu 4.** `MDC.clear()` sau task có luôn đúng hơn restore previous?

**Đáp án:** Không. Nếu wrapper hoặc instrumentation bên ngoài đã đặt context hợp lệ trên worker, clear sẽ xóa dữ liệu của scope đó. Snapshot/restore previous là cách lồng scope an toàn hơn.

**Câu 5.** `CompletableFuture.supplyAsync(fn)` có tự sử dụng bean `ThreadPoolTaskExecutor` gắn TaskDecorator trong Spring không?

**Đáp án:** Không. Nếu không truyền executor, nó dùng default async facility của JDK. Phải kiểm soát executor hoặc truyền ID như dữ liệu.

**Câu 6.** `@Async("workExecutor")` có khiến mọi `thenApply` phía sau luôn có đúng MDC không?

**Đáp án:** Không. Non-async continuation có thể chạy trên completion thread hoặc registration thread tùy timing; snapshot và boundary phải được quản lý rõ ràng.

**Câu 7.** Một consumer nhận Kafka message mang correlation ID nhưng không có HTTP request. Nên làm gì?

**Đáp án:** Đọc metadata, validate ID, đặt vào MDC trong scope của xử lý message, propagate khi publish tiếp và restore/clear sau khi xử lý. Không cần tạo ServletRequest giả.

**Câu 8.** Có nên đưa email khách hàng hoặc bearer token vào correlation ID?

**Đáp án:** Không. Dùng ID không chứa PII/secret; token không được ghi vào MDC hay log tập trung.

**Câu 9.** Correlation ID có thay thế W3C `traceparent` và OpenTelemetry span không?

**Đáp án:** Không. Correlation ID gom sự kiện theo ứng dụng; distributed tracing dùng trace/span context và cơ chế propagation chuẩn. Có thể dùng cả hai với mục đích rõ ràng.

**Câu 10.** Worker log có `correlationId=req-A` khi đang làm task của req-B. Nơi cần kiểm tra trước là gì?

**Đáp án:** Xem task nào đã đặt MDC mà không restore/clear, task req-B có đi qua TaskDecorator đúng executor không, và có capture snapshot từ submitter hay nhầm từ worker không.

**Câu 11.** Filter đã restore MDC sau HTTP request, liệu background job có cần giữ nguyên ServletRequest để còn ID không?

**Đáp án:** Không. Job nên có correlation ID như metadata immutable hoặc snapshot khi submit; giữ ServletRequest qua lifecycle lâu dài có thể gây lỗi/rò dữ liệu.

## Tóm tắt

Trong Spring Framework, **MDC là ngữ cảnh gắn với thread**, còn một logical request/job có thể chạy qua **nhiều thread và nhiều service**. Vì vậy:

1. **Tạo/validate correlation ID tại ingress**; phân biệt request/job/trace/span/idempotency IDs.
2. **Truyền nó tại ranh giới bất đồng bộ** bằng TaskDecorator capture tại submit, install trước khi worker chạy và restore previous trong `finally`; hoặc truyền ID như dữ liệu/job metadata.
3. **Không giả định `@Async`, `CompletableFuture`, raw thread, Reactor hay HTTP client tự chuyển MDC**. Mỗi ranh giới cần cơ chế propagation phù hợp.
4. **Trả đúng ID trong response header và đưa vào log pattern/structured logging**. Khi đi sang dịch vụ khác, truyền qua header hoặc message metadata với validation/trust policy.
5. **Bảo mật và đo lường:** không copy secret/PII, không dùng ID làm authorization, không biến ID có cardinality cao thành metric label; kiểm thử worker reuse và exception cleanup.

Một correlation ID được truyền đúng không chỉ giúp “tìm log nhanh hơn”, mà còn bảo đảm **log của request A không bị gán sang request B** trong hệ thống concurrency thực tế.

## Tài liệu tham khảo

1. SLF4J API — `MDC`: https://www.slf4j.org/apidocs/org/slf4j/MDC.html
2. Logback Manual — Mapped Diagnostic Context: https://logback.qos.ch/manual/mdc.html
3. Spring Framework Javadoc — `TaskDecorator`: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/task/TaskDecorator.html
4. Spring Framework Javadoc — `ThreadPoolTaskExecutor`: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
5. Spring Framework Reference — Task Execution and Scheduling (`@Async`): https://docs.spring.io/spring-framework/reference/integration/scheduling.html
6. Spring Framework Javadoc — `ContextPropagatingTaskDecorator`: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html
7. Spring Framework Reference — MVC asynchronous requests: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
8. Java SE 17 — `ThreadLocal`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html
9. W3C Trace Context — `traceparent` and `tracestate`: https://www.w3.org/TR/trace-context/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-context-006">ContextPropagatingTaskDecorator và điều kiện tích hợp thư viện ngữ cảnh</a>

<details>
<summary>Xem chi tiết</summary>

# ContextPropagatingTaskDecorator và điều kiện tích hợp thư viện ngữ cảnh

## 1. Bài toán: vì sao context biến mất khi chuyển sang worker thread?

Trong một ứng dụng Spring, luồng HTTP request thường có dữ liệu ngữ cảnh gắn với luồng hiện tại: mã tương quan (`correlationId`), tenant ID, thông tin quan sát (`Observation`), trace context hoặc MDC dùng để gắn metadata vào log. Nhiều thư viện dùng `ThreadLocal` để lưu các giá trị này trong phạm vi một luồng.

Khi gửi tác vụ sang `TaskExecutor` hoặc gọi một phương thức `@Async`, Spring có thể thực thi tác vụ trên **worker thread khác**. `ThreadLocal` của caller **không tự động chuyển sang worker**. Ví dụ:

```java
private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

// Thread đang nhận request:
TENANT.set("tenant-A");

executor.execute(() -> {
    // Nếu không có cơ chế lan truyền, giá trị thường là null:
    System.out.println("Worker tenant = " + TENANT.get());
});

TENANT.remove();
```

Đây chỉ là **mảnh code minh họa**, giả định `executor` đã được cấu hình. Kết quả quan sát phụ thuộc executor và cấu hình thực tế: một executor chạy trực tiếp trên caller có thể đọc được `tenant-A`; thread pool bình thường không tự mang `ThreadLocal` của caller sang worker.

Spring Framework **6.1 trở lên** cung cấp lớp:

```java
org.springframework.core.task.support.ContextPropagatingTaskDecorator
```

Đây là một `TaskDecorator` chuyên **chụp một snapshot của context có thể truy cập tại nơi gửi task, rồi thiết lập context ấy trong lúc task thực thi**, sau đó khôi phục trạng thái trước đó khi scope kết thúc. Lớp dựa vào **Micrometer Context Propagation**, không phải một bản sao thần kỳ của toàn bộ `ThreadLocal` trên JVM.

Đề bài này có hai vế không thể tách rời:

1. **Cấu hình `ContextPropagatingTaskDecorator` trên executor thực sự nhận task.**
2. **Bảo đảm tích hợp thư viện ngữ cảnh và các accessor tương ứng** để dữ liệu cần truyền có thể được chụp/khôi phục.

Nếu thiếu vế thứ hai, decorator có thể vẫn được tạo nhưng không biết cách truy cập một `ThreadLocal` tùy chỉnh. Nếu thiếu thư viện `io.micrometer:context-propagation` ở runtime, ngay cả việc nạp hoặc khởi tạo lớp cũng có thể thất bại.

### Mục tiêu học tập

Sau bài này, bạn sẽ hiểu cơ chế snapshot/scope, kiểm tra đúng dependency và phiên bản, đăng ký `ThreadLocalAccessor`, dùng `ThreadPoolTaskExecutor.setTaskDecorator(...)`, áp dụng với `@Async`, viết ví dụ Maven chạy trên Java 17, kiểm chứng không rò context khi tái sử dụng worker và phân biệt nó với các cơ chế transaction, security, Reactor, MDC hoặc truyền tham số thủ công.

## 2. Vì sao ThreadLocal bình thường không qua ranh giới luồng?

`ThreadLocal<T>` cho mỗi thread một giá trị riêng. Cùng một đối tượng `ThreadLocal` nhưng các thread khác nhau có vùng lưu riêng:

```text
HTTP thread / caller              Worker thread
---------------------            ----------------
TENANT.set("tenant-A")             TENANT.get() -> null
        |
        +-- executor.execute(task) ---------->
        |
        +-- tiếp tục công việc               task.run()
                                           không tự có tenant-A
```

`InheritableThreadLocal` không phải cách sửa tổng quát: nó hỗ trợ kế thừa ở **thời điểm tạo thread con**, còn thread pool thường tái sử dụng worker được tạo từ trước hoặc bởi thread khác. Giá trị có thể cũ, sai tenant hoặc tồn tại vượt phạm vi request nếu sao chép không đúng.

Cũng không nên tự `TENANT.set(...)` trên worker rồi quên `remove()`. Worker thread có thể xử lý nhiều request liên tiếp; dữ liệu của yêu cầu A lưu trong thread-local có thể rò sang yêu cầu B. Với thông tin tenant hoặc security, lỗi này có thể thành sự cố cách ly dữ liệu.

**Nguyên tắc an toàn:** Nếu cần dùng thread-local ở worker, phải biết chính xác **giá trị nào được truyền, thời điểm chụp, thời điểm thiết lập, cách khôi phục và lúc nào xóa**. Một wrapper chỉ `set` mà không `restore` là chưa đủ.

## 3. ContextPropagatingTaskDecorator làm gì?

`ContextPropagatingTaskDecorator` nằm trong `spring-core` và hiện thực giao diện `TaskDecorator`. JavaDoc mô tả nó bọc việc thực thi task nhằm hỗ trợ context propagation, đặc biệt hữu ích cho **logging context** hoặc **observation context**.

Các thành phần chính:

| Thành phần | Package/API | Vai trò |
|---|---|---|
| `TaskDecorator` | `org.springframework.core.task.TaskDecorator` | Hợp đồng bọc một `Runnable` trước khi executor chạy |
| `ContextPropagatingTaskDecorator` | `org.springframework.core.task.support` | Decorator dựa vào Micrometer snapshot để truyền context |
| `ContextSnapshotFactory` | `io.micrometer.context` | Tạo snapshot và quản lý logic chụp/khôi phục |
| `ContextSnapshot` | `io.micrometer.context` | Giữ những giá trị context đã chụp |
| `ContextRegistry` | `io.micrometer.context` | Danh sách các accessor mà Micrometer biết |
| `ThreadLocalAccessor<T>` | `io.micrometer.context` | Đọc, đặt và reset một loại ThreadLocal |
| `ContextAccessor` | `io.micrometer.context` | Truy cập context dạng map-like, ví dụ tích hợp reactive |

Hợp đồng decorator rút gọn:

```java
Runnable decorate(Runnable original);
```

Spring gọi `decorate(...)` để tạo một Runnable có khả năng khôi phục ngữ cảnh xung quanh `original.run()`. Với `ThreadPoolTaskExecutor`, việc trang trí được kết nối bằng:

```java
executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
```

**Không phải mọi Executor đều hỗ trợ `setTaskDecorator(...)`.** Một `java.util.concurrent.ExecutorService` tạo trực tiếp bằng `Executors.newFixedThreadPool(...)` không tự chạy decorator Spring chỉ vì bean khác trong context đã cấu hình nó. Phải dùng một Spring executor hỗ trợ `TaskDecorator` hoặc bọc task/executor bằng cơ chế tương ứng có chủ đích.

### 3.1. Hai constructor

JavaDoc Spring 6.1+ công bố:

```java
new ContextPropagatingTaskDecorator()
```

Constructor mặc định tạo một `ContextSnapshotFactory` mặc định, thông thường dùng `ContextRegistry` toàn cục của Micrometer.

Khi cần registry/factory riêng:

```java
new ContextPropagatingTaskDecorator(customFactory)
```

`customFactory` là một `io.micrometer.context.ContextSnapshotFactory` được cấu hình trước. Cách này hữu ích khi muốn kiểm soát các accessor/kiểu context tham gia, tránh nhầm với registry toàn cục; ví dụ đầy đủ ở mục 10.

### 3.2. Decorator không tạo thêm worker

Một decorator không có ý nghĩa quản trị thread pool. Nó không tự tăng worker, không thêm queue, không giải quyết rejection và không làm một phép tính đồng bộ biến thành bất đồng bộ. Nó chỉ bao quanh việc **thực thi Runnable** khi executor đã quyết định chạy task.

Nếu executor thực thi đồng bộ trên caller, thread-local của caller vốn đã sẵn có; decorator có thể không đem lại ích lợi mà vẫn tăng overhead. Điều này được chính JavaDoc của Spring nhấn mạnh: cơ chế có chi phí thêm, đặc biệt đáng cân nhắc nếu ứng dụng gửi số lượng lớn task cực nhỏ.

## 4. Điều kiện tích hợp: chỉ thêm spring-core là chưa đủ

Lớp `ContextPropagatingTaskDecorator` thuộc Spring Framework, nhưng phần lõi của cơ chế context snapshot dùng Micrometer. Với môi trường Spring Framework 6.1+ thông thường, hãy đảm bảo artifact sau **thực sự có trên runtime classpath**:

```text
io.micrometer:context-propagation
```

Ví dụ Maven cho dự án không dùng Spring Boot BOM:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context</artifactId>
        <version>6.2.7</version>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>context-propagation</artifactId>
        <version>1.1.3</version>
    </dependency>
</dependencies>
```

Hai phiên bản ở đây là bộ phiên bản **cụ thể để minh họa và dùng trong demo** của bài, không phải tuyên bố đó là các phiên bản mới nhất. Khi làm việc trong dự án có Spring Boot/dependency management, ưu tiên **BOM của stack đã chọn** thay vì ép phiên bản Micrometer không tương thích. Cần đồng bộ Java requirement, classpath của runtime, dependency exclusions và môi trường triển khai.

**Phân biệt ba tầng điều kiện:**

1. Có Spring Framework phiên bản cung cấp `ContextPropagatingTaskDecorator` (**since Spring 6.1**).
2. Có jar `context-propagation` tương thích ở **runtime**, không chỉ compile; kiểm tra dependency tree, classloader và version conflicts.
3. Có `ThreadLocalAccessor`/`ContextAccessor` phù hợp trong `ContextRegistry` cho **loại dữ liệu bạn thực sự muốn truyền**. Custom `ThreadLocal` sẽ không tự có accessor chỉ vì đã thêm jar.

Thư viện này chỉ cung cấp **hạ tầng truyền context**. Muốn tạo trace/metrics đầy đủ còn cần instrumentation, registry, tracing bridge/exporter và cấu hình observability tương ứng. `ContextPropagatingTaskDecorator` không tự gửi span đến một hệ thống quan sát nếu không có hạ tầng tracing.

### 4.1. Những lỗi dependency thường gặp

| Hiện tượng | Khả năng nguyên nhân | Cách xử lý |
|---|---|---|
| Không tìm thấy `ContextPropagatingTaskDecorator` lúc biên dịch | Spring Framework quá cũ hoặc module/classpath sai | Xác minh Spring 6.1+ và import đúng `spring-core` |
| `NoClassDefFoundError: io/micrometer/context/ContextSnapshotFactory` | Thiếu Micrometer Context Propagation ở runtime | Thêm `io.micrometer:context-propagation` đúng scope/runtime |
| `NoSuchMethodError` liên quan Micrometer | Phiên bản jar không tương thích hoặc conflict | Dùng BOM phù hợp, kiểm tra cây phụ thuộc thực tế |
| Decorator chạy nhưng custom tenant vẫn null | Chưa đăng ký accessor, sai registry hoặc context vốn trống ở thread submit | Xác minh `ContextRegistry`, thời điểm set và capture |
| Chỉ một vài đường thực thi có context | Decorator gắn vào một executor, các đường khác dùng executor khác | Xác định đúng executor và mọi ranh giới chuyển thread |
| Context của request trước xuất hiện trong task sau | Set không reset, wrapper tự viết sai hoặc custom accessor lỗi | Test cùng worker tái sử dụng, khôi phục bằng Scope/finally |

Với dự án Maven, có thể kiểm tra dependency:

```bash
mvn dependency:tree -Dincludes=io.micrometer:context-propagation
```

Lệnh cho thấy version/scope/đường đi của dependency trong mô hình Maven; không đảm bảo bản deploy cuối có đúng jar nếu packaging/classloader bị cấu hình khác. Cũng cần kiểm tra bản build và môi trường chạy thực tế.

## 5. ContextRegistry và ThreadLocalAccessor: phần không được bỏ qua

`ContextRegistry` biết **cách đọc và đặt từng loại context** thông qua các accessor. Micrometer không thể phản chiếu toàn bộ các biến `ThreadLocal` trong một ứng dụng để tự hiểu ý nghĩa và vòng đời của chúng.

Ví dụ ứng dụng có:

```java
private static final ThreadLocal<String> REQUEST_ID =
        new ThreadLocal<>();
```

Để Micrometer có thể chụp giá trị `REQUEST_ID.get()` và khôi phục vào thread chạy task, có thể đăng ký accessor bằng callback:

```java
import io.micrometer.context.ContextRegistry;

ContextRegistry.getInstance().registerThreadLocalAccessor(
        "lesson.requestId",
        REQUEST_ID::get,
        REQUEST_ID::set,
        REQUEST_ID::remove
);
```

Đây là **mảnh mã Java trong class có `REQUEST_ID`**, không phải chương trình độc lập. `registerThreadLocalAccessor` với bốn đối số dùng:

- **key**: mã định danh ổn định của loại context, ví dụ `lesson.requestId`.
- **getter**: đọc giá trị ở **thread hiện tại**.
- **setter**: đặt giá trị trong scope lúc khôi phục.
- **resetter**: làm sạch giá trị (`ThreadLocal.remove()`) khi cần reset.

Trong demo, dùng `ContextRegistry.getInstance()` vì constructor mặc định của decorator tìm accessor qua registry toàn cục. Nếu tạo một `ContextRegistry` **riêng**, phải cung cấp registry đó cho `ContextSnapshotFactory` rồi đưa factory vào decorator; chỉ register vào một registry không được decorator sử dụng sẽ không có tác dụng.

### 5.1. Có thể triển khai ThreadLocalAccessor thành class

Micrometer còn có interface:

```java
import io.micrometer.context.ThreadLocalAccessor;

public final class RequestIdAccessor
        implements ThreadLocalAccessor<String> {

    public static final String KEY = "lesson.requestId";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public String getValue() {
        return RequestIdContext.get();
    }

    @Override
    public void setValue(String value) {
        RequestIdContext.set(value);
    }

    @Override
    public void setValue() {
        RequestIdContext.clear();
    }
}
```

Đây là **class minh họa phụ thuộc `RequestIdContext` do ứng dụng định nghĩa**. Khi đó:

```java
ContextRegistry.getInstance().registerThreadLocalAccessor(
        new RequestIdAccessor());
```

Điều quan trọng không phải dạng callback hay class, mà là **accessor trả đúng key, đọc đúng ThreadLocal, khôi phục đúng giá trị và xóa đúng lúc**. Với nhiều loại context khác nhau, cần các accessor riêng hoặc bridge do thư viện tương ứng cung cấp.

### 5.2. Đăng ký accessor khi nào?

Nên đăng ký ở giai đoạn **khởi động cấu hình ứng dụng**, trước khi tạo/sử dụng các task cần context, và làm rõ ownership của registry. Đăng ký accessor trùng key từ nhiều module mà không kiểm soát có thể thay thế accessor cũ, khiến quan sát khó hiểu.

`ContextRegistry` là danh sách accessor, **không phải nơi giữ request ID cụ thể của từng request**. Giá trị request ID vẫn được set vào `ThreadLocal` trên caller khi thực thi; snapshot đọc giá trị lúc gửi task.

## 6. Cơ chế capture → restore → cleanup

Một lần thực thi qua decorator có thể hiểu như sau:

```text
Thread submit:
  set REQUEST_ID = "R-100"
  |
  v
TaskDecorator.decorate(original Runnable)
  |
  +-- ContextSnapshotFactory.captureAll()
  |   nhìn vào các accessor đã đăng ký
  |   lưu giá trị "R-100" trong snapshot
  |
  +-- trả Runnable có wrapper
  |
  v
Executor đưa wrapper vào queue / worker
  |
  v
Worker thread:
  snapshot mở Scope -> đặt REQUEST_ID = "R-100"
  |
  +-- chạy original.run()
  |      logging/tracing có thể đọc REQUEST_ID tương ứng
  |
  +-- Scope.close() -> khôi phục giá trị trước scope
  v
Worker có thể được tái sử dụng cho request khác
```

Đây là mô hình logic. Snapshot/scope được triển khai bởi Micrometer, còn `ThreadPoolTaskExecutor` quyết định lúc nào decorator được áp dụng và task được chạy.

### 6.1. Snapshot ở thời điểm **gửi**, không phải lúc worker lấy task

Giả sử:

```java
REQUEST_ID.set("R-100");
executor.execute(work); // decorator capture context ở đường gửi

REQUEST_ID.set("R-200");
// Worker có thể mới chạy work sau thời điểm đổi này.
```

Nếu `REQUEST_ID` có accessor và executor dùng decorator đúng, `work` dùng **giá trị đã chụp `R-100`**, không tự đổi thành `R-200` chỉ vì caller thay thread-local sau submit.

Hãy tránh giải thích rằng decorator giữ một reference “sống” tới toàn bộ ThreadLocal của caller. Nó lưu những giá trị mà accessor capture được tại thời điểm tương ứng. Với **đối tượng mutable**, snapshot thường giữ tham chiếu tới đối tượng đó, không tự deep-copy tất cả nội dung. Nếu đối tượng được sửa đồng thời, vẫn có race và quy tắc thread-safety riêng.

### 6.2. Scope và cleanup ngăn rò context

Micrometer dùng `ContextSnapshot.Scope` có thể đóng để reset/restore các thread-local sau khi task kết thúc. Điều này đặc biệt quan trọng với worker pool vì cùng một worker lần lượt chạy nhiều task.

Mô hình dùng snapshot thủ công:

```java
ContextSnapshot snapshot = factory.captureAll();

try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
    // Trong scope: các ThreadLocal đã đăng ký được khôi phục.
    process();
}
// Sau scope: các ThreadLocal được khôi phục về giá trị trước đó.
```

Đây là mảnh code minh họa; `factory` là `ContextSnapshotFactory` và `process()` do ứng dụng cung cấp. `ContextPropagatingTaskDecorator` giúp áp dụng logic tương tự quanh việc chạy `Runnable`, tránh phải lặp mã này ở mỗi task.

Không được kết luận “không có rò rỉ nào” trong mọi ứng dụng chỉ từ việc bật decorator: một `ThreadLocal` **chưa đăng ký**, một thư viện dùng cơ chế lưu khác hoặc một wrapper ngoài scope vẫn có thể gây leak. Test cần chứng minh các context **thực sự được hỗ trợ** đã được restore.

### 6.3. Context không có giá trị khi gửi

Nếu accessor được đăng ký nhưng caller **không set giá trị**, snapshot không thể phát minh ra tenant ID. Tùy cơ chế và cấu hình snapshot, một giá trị vắng mặt có thể không được đưa vào snapshot; vì vậy điều quan trọng là worker phải có baseline sạch và scope đúng, tránh dữ liệu từ task trước.

Với nghiệp vụ cần tuyệt đối không dùng lại tenant cũ, kết hợp accessor reset chính xác, dọn context tại biên request/task và integration test trên **cùng một worker tái sử dụng**. Không dùng việc truyền context như cơ chế cấp quyền truy cập một cách độc lập.

## 7. Cấu hình ThreadPoolTaskExecutor trong Spring

Mẫu Java Configuration thường gặp:

**Tệp `ContextExecutorConfiguration.java` — class cấu hình Spring.**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ContextExecutorConfiguration {

    @Bean("contextExecutor")
    public ThreadPoolTaskExecutor contextExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("ctx-worker-");

        executor.setTaskDecorator(
                new ContextPropagatingTaskDecorator());

        return executor;
    }
}
```

Điều kiện để đoạn này có tác dụng:

1. Có Spring Framework 6.1+ và `io.micrometer:context-propagation` phù hợp trong classpath.
2. Executor bean này **thực sự được sử dụng** để gửi task.
3. Accessor phù hợp đã được đăng ký hoặc thư viện tích hợp cung cấp.
4. Context cần truyền **đã được set trên thread gửi** trước khi task được trang trí.
5. Worker không dùng context ngoài chính sách bảo mật, transaction hay vòng đời đúng.

**Không cần `@EnableAsync` nếu chỉ inject `TaskExecutor` và gọi `execute(...)` trực tiếp.** Nếu muốn sử dụng phương thức `@Async("contextExecutor")`, cần bật cơ chế `@Async` và gọi qua Spring proxy.

### 7.1. @Async chọn đúng executor

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("contextExecutor")
    public CompletableFuture<String> generate(String id) {
        return CompletableFuture.completedFuture(
                "report=" + id + ", thread="
                + Thread.currentThread().getName());
    }
}
```

Lớp này là **Spring component**, cần application context, `@EnableAsync` và cấu hình executor ở trên; không phải Java SE standalone. Spring sẽ dùng bean có tên `contextExecutor`, tại đó task được decorate. Nhưng nếu method được gọi qua `this.generate(...)` trong chính bean, interceptor `@Async` bị bỏ qua ở proxy mode mặc định và decorator không được áp dụng qua đường đó.

Gắn decorator vào **một pool khác** không làm pool do `@Async` chọn có khả năng context propagation. Đây là lỗi cấu hình rất thường gặp khi ứng dụng có nhiều executor.

## 8. Ví dụ đầy đủ Java 17 + Spring Framework + Micrometer

Ví dụ dưới đây không cần servlet container hay hệ thống tracing. Ta đăng ký một accessor cho `ThreadLocal<String>` tự tạo, cấu hình hai executor: một pool **không** có decorator và một pool **có** decorator. Sau đó kiểm tra `@Async` cũng sử dụng pool được decorate.

Ví dụ cố tình làm kết quả có thể kiểm chứng bằng `Future.get(timeout)` thay vì `Thread.sleep(...)`.

### 8.1. Cấu trúc dự án

```text
context-propagation-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── lesson/
                └── ContextPropagationDemo.java
```

### 8.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lesson</groupId>
    <artifactId>context-propagation-demo</artifactId>
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
            <groupId>io.micrometer</groupId>
            <artifactId>context-propagation</artifactId>
            <version>1.1.3</version>
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

`spring-context` mang các thành phần Spring cần dùng, còn `context-propagation` là thư viện Micrometer cung cấp registry, snapshot và accessor. Các phiên bản được ghim để demo có cùng dependency khi người học chạy lại; dự án thật nên dùng dependency management của stack.

### 8.3. Tệp src/main/java/lesson/ContextPropagationDemo.java

```java
package lesson;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.micrometer.context.ContextRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ContextPropagationDemo {

    private static final ThreadLocal<String> TENANT =
            new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ContextRegistry registry = ContextRegistry.getInstance();
        registry.registerThreadLocalAccessor(
                "lesson.tenant",
                TENANT::get,
                TENANT::set,
                TENANT::remove);

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            TaskExecutor plain = context.getBean(
                    "plainExecutor", TaskExecutor.class);
            TaskExecutor propagating = context.getBean(
                    "propagatingExecutor", TaskExecutor.class);
            TenantService service = context.getBean(TenantService.class);

            try {
                TENANT.set("tenant-A");
                System.out.println("Không decorator: "
                        + readTenant(plain));
                System.out.println("Có decorator: "
                        + readTenant(propagating));

                TENANT.set("tenant-B");
                System.out.println("Giá trị mới: "
                        + readTenant(propagating));
                System.out.println("@Async: "
                        + service.currentTenant()
                                .get(5, TimeUnit.SECONDS));

                TENANT.remove();
                System.out.println("Sau clear: "
                        + readTenant(propagating));
            } finally {
                TENANT.remove();
            }
        } finally {
            registry.removeThreadLocalAccessor("lesson.tenant");
        }
    }

    private static String readTenant(TaskExecutor executor)
            throws Exception {
        return CompletableFuture
                .supplyAsync(TENANT::get, executor)
                .get(5, TimeUnit.SECONDS);
    }

    private static ThreadPoolTaskExecutor pool(String prefix) {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix(prefix);
        return executor;
    }

    @Configuration
    @EnableAsync
    public static class Config {

        @Bean("plainExecutor")
        public ThreadPoolTaskExecutor plainExecutor() {
            return pool("plain-");
        }

        @Bean("propagatingExecutor")
        public ThreadPoolTaskExecutor propagatingExecutor() {
            ThreadPoolTaskExecutor executor = pool("ctx-");
            executor.setTaskDecorator(
                    new ContextPropagatingTaskDecorator());
            return executor;
        }

        @Bean
        public TenantService tenantService() {
            return new TenantService();
        }
    }

    public static class TenantService {

        @Async("propagatingExecutor")
        public CompletableFuture<String> currentTenant() {
            return CompletableFuture.completedFuture(
                    TENANT.get());
        }
    }
}
```

### 8.4. Biên dịch và chạy

Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.ContextPropagationDemo
```

Năm dòng **do chương trình in ra khi chạy bình thường**:

```text
Không decorator: null
Có decorator: tenant-A
Giá trị mới: tenant-B
@Async: tenant-B
Sau clear: null
```

Maven có thể in thêm thông báo riêng nếu cách chạy/cấu hình khác đi. Kết quả chương trình là xác định theo logic:

1. `TENANT` là `ThreadLocal` **thường**, không phải `InheritableThreadLocal`. Pool `plainExecutor` chạy trên worker riêng, nên không tự thấy `tenant-A` từ caller.
2. Pool `propagatingExecutor` được trang trí. Khi caller đang chứa `tenant-A`, accessor đọc nó vào snapshot; worker đọc được cùng giá trị trong scope.
3. Caller đổi thành `tenant-B` rồi gửi một task mới. Snapshot mới chứa giá trị mới; task mới không được dùng lại snapshot của task trước.
4. `TenantService.currentTenant()` là method `@Async("propagatingExecutor")` được gọi **qua Spring bean proxy**, nên nó đọc được `tenant-B` từ worker được decorate.
5. Caller gọi `TENANT.remove()` trước lần gửi cuối. Worker không có giá trị tenant còn sót lại; lần đọc cuối trả `null`.
6. `get(5, TimeUnit.SECONDS)` chỉ là cách demo chờ kết quả có giới hạn. Nó chặn `main`; trong HTTP request thật, việc chờ ngay có thể làm request chậm.
7. Context đóng bằng `try-with-resources`; Spring quản lý vòng đời hai bean executor. `ContextRegistry` toàn cục được trả về trạng thái không có accessor `lesson.tenant` khi demo kết thúc.

Mẫu này cố tình không dùng số thứ tự thread để kiểm thử: tên thread là chi tiết vận hành, còn **giá trị tenant** mới là điều cần xác nhận. Để kiểm thử chặt hơn trong dự án thật, hãy kết hợp với theo dõi worker reuse và bảo đảm không rò tenant qua các request đồng thời.

### 8.5. Điều cần tránh khi sao chép demo vào production

- Không dùng `TENANT` như một cơ chế tự quyết định quyền truy cập. Identity/authorization phải được kiểm chứng ở ranh giới nghiệp vụ và bảo mật.
- Không đăng ký lặp accessor toàn cục mỗi khi có HTTP request. Hãy đăng ký một lần theo vòng đời ứng dụng, và xác định ownership khi có nhiều module.
- Không tự thay đổi registry toàn cục của các thư viện khác để “sửa” lỗi context nếu chưa hiểu hệ thống tracing/security của chúng.
- Không tạo hai pool chỉ để chứng minh decorator; demo có pool plain để so sánh trước–sau.
- Không giữ token, request stream hoặc object mutable có vòng đời ngắn trong snapshot.
- Không diễn giải `TENANT.remove()` trên caller là thao tác xóa trực tiếp `ThreadLocal` của worker; việc dọn worker do scope/accessor đảm nhiệm.

## 9. Có thể dùng ContextSnapshotFactory tùy chỉnh thay registry toàn cục

Một ứng dụng lớn có thể muốn một tập accessor **riêng cho một executor**. Micrometer cho phép cấu hình `ContextSnapshotFactory` với một `ContextRegistry` cụ thể:

```java
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

ContextRegistry registry = new ContextRegistry();
registry.registerThreadLocalAccessor(
        "lesson.requestId",
        REQUEST_ID::get,
        REQUEST_ID::set,
        REQUEST_ID::remove);

ContextSnapshotFactory factory =
        ContextSnapshotFactory.builder()
                .contextRegistry(registry)
                .build();

ContextPropagatingTaskDecorator decorator =
        new ContextPropagatingTaskDecorator(factory);

executor.setTaskDecorator(decorator);
```

Đây là **mảnh code cấu hình**; `REQUEST_ID` là `ThreadLocal<String>` của ứng dụng và `executor` là một Spring executor hỗ trợ `setTaskDecorator`. Khác với constructor không tham số, đoạn này **ràng buộc decorator vào factory dùng registry được chỉ định**.

Cách tùy chỉnh có ích khi:

- Muốn test với registry riêng, tránh đụng accessor của cả JVM.
- Chỉ muốn truyền một tập context đã được đánh giá an toàn.
- Muốn kiểm soát cấu hình snapshot (và nếu phiên bản Micrometer hỗ trợ, các tùy chọn xử lý giá trị vắng mặt).
- Có nhiều thư viện đăng ký accessor khác nhau và cần tránh tương tác ngoài chủ đích.

Nhưng registry riêng có thể bỏ sót những accessor quan trọng do các thư viện observability đăng ký trên registry toàn cục. Nếu dùng registry tùy chỉnh, bạn phải biết rõ mình **đã loại bỏ hay giữ lại những loại context nào**. Không tự suy ra mọi span/MDC sẽ xuất hiện nếu factory chỉ nhìn thấy accessor của request ID.

## 10. Logging MDC, Observation và trace context: những trường hợp khác nhau

`ContextPropagatingTaskDecorator` đặc biệt hữu ích cho log correlation và observability, nhưng các khái niệm sau không giống nhau:

| Context | Mục đích | Yêu cầu tích hợp |
|---|---|---|
| Custom `ThreadLocal<String>` | Request ID, tenant ID hoặc thông tin kỹ thuật | Accessor do ứng dụng cung cấp/đăng ký |
| SLF4J/Logback `MDC` | Metadata trong log, ví dụ traceId/correlationId | Tích hợp `ThreadLocalAccessor` tương thích với cơ chế MDC; không giả định có sẵn |
| Micrometer `Observation` | Theo dõi phạm vi quan sát, metrics/tracing khi được cấu hình | Observation registry, accessor/instrumentation thích hợp |
| Trace/span context | Nối chuỗi thao tác trong hệ thống phân tán | Tracing bridge, exporters và context propagation phù hợp |
| Spring Security context | Thông tin xác thực/ủy quyền theo luồng | Cơ chế Spring Security riêng hoặc accessor đã được xác minh |
| Spring transaction context | Ranh giới giao dịch, resource gắn với luồng | **Không** coi là ngữ cảnh được sao chép an toàn sang worker |

**Có thư viện Context Propagation không có nghĩa mọi context đều tự được đăng ký.** Tùy bộ thư viện đang dùng, một số accessor có thể được cung cấp bởi integration/SPI; custom context thường cần đăng ký thủ công. Hãy kiểm tra thực tế `ContextRegistry` và thử một task để xác nhận, thay vì chỉ nhìn thấy dependency trong `pom.xml`.

### 10.1. MDC có tự được truyền không?

Không nên đưa ra một tuyên bố chung rằng mọi phiên bản SLF4J/Logback/Micrometer đều tự truyền MDC. Với hệ sinh thái observability được cấu hình đầy đủ, trace/span context và logging correlation có thể được bridge thông qua accessor/instrumentation. Ở ứng dụng không có bridge thích hợp, cần đăng ký một accessor cho dữ liệu MDC cần truyền hoặc chọn giải pháp log-context propagation tương ứng.

MDC thường là map theo thread. Nếu thực hiện thủ công, phải **copy** map có chủ đích khi capture và khôi phục map cũ trong `finally`; không dùng chung một mutable map giữa các request. Nếu dùng accessor chuyên dụng, phải xác minh nó hỗ trợ reset và không để MDC của request A rò sang request B.

### 10.2. Observation không đồng nghĩa tracing đã bật

Micrometer Observation có thể giúp tạo metric và span khi có handler/tracing bridge được cấu hình. Decorator có thể chuyển observation context sang worker, nhưng **nó không tự phát sinh observation mới hoặc xuất trace** cho mọi code.

Một task chỉ được decorate mà không có Observation hiện hành ở nơi submit có thể không có trace correlation nào để truyền. Hãy kiểm tra điểm bắt đầu observation, các accessor, logging pattern, tracing exporter và dữ liệu log thật.

## 11. SecurityContext và transaction: ranh giới an toàn

### 11.1. Context bảo mật cần chính sách riêng

`SecurityContextHolder` của Spring Security có chiến lược lưu theo thread; chuyển thread thường làm mất context nếu không có cơ chế rõ ràng. Một số integration có thể cung cấp accessor/adapter phù hợp, nhưng **không mặc định rằng `ContextPropagatingTaskDecorator` tự chép toàn bộ security context**.

Spring Security còn có những wrapper và executor riêng như `DelegatingSecurityContextAsyncTaskExecutor`. Khi chọn giải pháp, cần kiểm tra:

- Security context được chụp lúc nào; đang ở bước xác thực hay đã được làm sạch?
- Worker thực sự cần principal hay chỉ cần một ID phi nhạy cảm?
- Context của người dùng A có bị giữ sau khi task xong không?
- Có bị truyền nhầm một principal cũ qua worker pool hoặc callback khác không?
- Quyền được kiểm tra lại ở tác vụ nền nếu nghiệp vụ có thời gian chờ dài không?

Context propagation là **cơ chế vận chuyển thông tin**, không thay thế xác thực/ủy quyền. Không nên sao chép token, password hay đối tượng HTTP request sang worker nếu không có yêu cầu bảo mật được duyệt.

### 11.2. Không truyền transaction của caller như một ThreadLocal tùy ý

Spring transaction thường sử dụng thread-bound resource và trạng thái giao dịch. Truyền các `ThreadLocal` liên quan sang worker **không có nghĩa chuyển được an toàn cùng một transaction** sang luồng khác.

```text
Request thread:
  @Transactional TX-A bắt đầu
       |
       +--> executor gửi task
       |
  TX-A commit/rollback theo caller

Worker thread:
  thực thi độc lập về thời điểm
  không tự tham gia TX-A
  nếu cần transaction: thiết kế TX-B phù hợp ở worker
```

Khi task cần dữ liệu chỉ hợp lệ sau commit, cân nhắc transaction event, outbox hoặc cơ chế điều phối có độ bền phù hợp. Không tự viết `ThreadLocalAccessor` để sao chép raw JDBC connection, `EntityManager` hoặc transaction status của caller.

**Truyền correlation ID** và **truyền quyền truy cập vào resource gắn với thread** là hai vấn đề rất khác nhau.

## 12. @Async, CompletableFuture và mọi bước đổi executor

Một method `@Async("contextExecutor")` đi qua Spring proxy sẽ gửi phần thực thi method đến bean executor có decorator. Nhưng điều đó **không bảo đảm mọi bước CompletableFuture sau này chạy trên cùng executor**.

Ví dụ:

```java
CompletableFuture<Result> future = service.load();

CompletableFuture<String> next = future.thenApplyAsync(
        value -> transform(value));
```

Đây là **mảnh code minh họa**, với `Result` và `transform` do ứng dụng định nghĩa. `thenApplyAsync(...)` không truyền executor có thể dùng executor mặc định của CompletableFuture (thường common pool), nơi **không chắc có context propagation** của `contextExecutor`. Tương tự, `CompletableFuture.supplyAsync(...)` không truyền executor cũng có thể chạy ngoài pool được trang trí.

Nếu cần giữ đường thực thi có context, hãy chọn executor rõ:

```java
CompletableFuture<String> next = future.thenApplyAsync(
        value -> transform(value),
        contextExecutor);
```

Nhưng vẫn phải biết **thread nào đăng ký continuation** và context nào đang hiện hành **ở thời điểm continuation được schedule**. Nếu callback được lên lịch từ một thread không có context, decorator không thể tự tìm lại request ID gốc từ một Future đã hoàn tất.

Với pipeline phức tạp, có thể cần cách chụp snapshot ở ranh giới gốc, một executor/context-aware API, hoặc truyền explicit correlation ID vào từng bước. Không chỉ thêm decorator một lần rồi mặc định propagation xuyên qua mọi thư viện và mọi giai đoạn.

### 12.1. Tránh tạo hai lớp scheduling vô ích

Nếu service đã dùng `@Async("contextExecutor")`, không cần bọc thêm `CompletableFuture.supplyAsync(...)` không truyền executor trong thân method chỉ để “chạy bất đồng bộ hơn”. Làm vậy có thể chuyển công việc sang common pool, mất context và tăng độ phức tạp xử lý lỗi.

Tương tự, phương thức `@Async` trả `CompletableFuture.completedFuture(value)` trong worker **vẫn hợp lệ**: proxy Spring giữ handle bất đồng bộ của lần chạy method, còn method đích cung cấp future tạm đúng chữ ký.

## 13. TaskDecorator không phải cơ chế bắt lỗi task

JavaDoc `ThreadPoolTaskExecutor.setTaskDecorator(...)` nhắc rằng decorator có thể được áp dụng lên **callback thực thi nội bộ**, không phải chính `Runnable`/`Callable` do caller gửi. Với `submit(...)`, callback có thể là `FutureTask`: `FutureTask.run()` thường giữ exception bên trong Future thay vì ném ra khỏi wrapper.

Vì vậy một `TaskDecorator` tự viết kiểu:

```java
return () -> {
    try {
        runnable.run();
    } catch (RuntimeException error) {
        log(error);
        throw error;
    }
};
```

không thể được tin là sẽ log **mọi** lỗi từ `submit(...)`. Mảnh code trên chỉ minh họa cách catch ở wrapper; `runnable` và `log(...)` phải có trong một method/class phù hợp.

`ContextPropagatingTaskDecorator` có nhiệm vụ **đặt lại context xung quanh execution**, không phải xử lý lỗi. Với Spring `@Async void`, lỗi phù hợp có thể đến `AsyncUncaughtExceptionHandler`; với `Future/CompletableFuture`, caller phải quan sát Future; với `ExecutorService` Java thuần, lỗi theo hợp đồng `execute/submit` tương ứng.

Nếu task ném exception, scope của snapshot vẫn cần được đóng trong đường `finally` của cơ chế bọc để tránh rò context. Đây là một lý do nên dùng thư viện có hợp đồng scope rõ thay vì tự `set` rồi quên cleanup.

## 14. CompositeTaskDecorator khi cần nhiều concern

Một ứng dụng có thể cần thêm telemetry, đặt tên nghiệp vụ hoặc ghi thời gian chạy bên cạnh context propagation. Spring cung cấp `CompositeTaskDecorator` để kết hợp nhiều `TaskDecorator`:

```java
import java.util.List;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.CompositeTaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

TaskDecorator timing = runnable -> () -> {
    long start = System.nanoTime();
    try {
        runnable.run();
    } finally {
        long elapsed = System.nanoTime() - start;
        System.out.println("elapsedNs=" + elapsed);
    }
};

TaskDecorator composite = new CompositeTaskDecorator(
        List.of(new ContextPropagatingTaskDecorator(), timing));

executor.setTaskDecorator(composite);
```

Đây là **đoạn code trong class cấu hình** với `executor` hỗ trợ `setTaskDecorator`. Nó không phải chương trình độc lập. Mục đích là chỉ ra cách ghép concern; trong production không dùng `System.out` cho mỗi task vì overhead.

**Thứ tự decorator ảnh hưởng thứ tự mở/đóng scope và thứ tự code đo thời gian.** Khi logging/timing decorator cần quan sát context đã được khôi phục, hãy kiểm thử thứ tự bọc thay vì suy luận theo cách đặt vào danh sách. Nếu hai decorator cùng thay một `ThreadLocal`, quy tắc cleanup phải tương thích, nếu không có thể ghi đè nhầm giá trị.

## 15. Quan sát và hiệu năng: không phải context nào cũng đáng chụp

Theo JavaDoc Spring, `ContextPropagatingTaskDecorator` có overhead và **không được khuyến nghị áp dụng mù quáng cho số lượng rất lớn tác vụ cực nhỏ**.

Các chi phí có thể bao gồm:

- Đọc các accessor đã đăng ký khi task được gửi.
- Tạo snapshot và wrapper cho mỗi task (tùy implementation).
- Đặt/khôi phục các giá trị trong `ThreadLocal` quanh mỗi lần thực thi.
- Phân bổ thêm object, áp lực GC và công việc của logging/tracing integration.
- Truyền quá nhiều dữ liệu context không cần thiết cho task.

Nếu task chỉ tăng một counter trong vài microsecond, overhead có thể đáng kể so với nghiệp vụ; nếu task gọi I/O hàng trăm millisecond, chi phí đó thường nhỏ hơn nhưng vẫn nên đo trên workload thực.

Gợi ý kiểm chứng:

1. Benchmark hai cấu hình cùng tải: có và không decorator, giữ nguyên số worker/queue.
2. Đo `task queue wait`, `run duration`, p95/p99 và throughput.
3. Đo allocated bytes/GC, kích thước snapshot và số accessor tham gia.
4. Đo khả năng trace/log correlation tăng lên có thực sự hữu ích không.
5. Giới hạn context vào những dữ liệu thật sự cần cho worker.
6. Không đặt token hoặc object request lớn vào ThreadLocal chỉ để chúng “đi theo” mọi task.

Lưu ý phân biệt **thông lượng**, **chi phí mỗi task** và **độ chính xác của ngữ cảnh**. Bật decorator mà tác vụ chạy được không đủ để nói integration đáp ứng yêu cầu vận hành.

## 16. Spring Boot: tự cấu hình không thay việc hiểu executor thực tế

Với một số phiên bản Spring Boot có hỗ trợ, tài liệu observability cung cấp thuộc tính:

```properties
spring.task.execution.propagate-context=true
```

Thuộc tính này có ý nghĩa **opt-in propagation cho AsyncTaskExecutor do Spring Boot tự cấu hình**. **Không phải mọi phiên bản Boot đều có thuộc tính này**, và nó không nên được dùng như bằng chứng một executor **tự đăng ký** đã có decorator.

Tài liệu Boot còn chỉ ra: nếu tự định nghĩa `AsyncTaskExecutor`, hãy cấu hình `ContextPropagatingTaskDecorator` phù hợp — chẳng hạn khai báo decorator bean hoặc gắn trực tiếp vào executor theo khả năng của phiên bản — thay vì kỳ vọng thuộc tính tự động thay đổi mọi pool tùy chỉnh.

Trong một hệ thống cụ thể, cần xác định:

- Phiên bản Spring Boot và `spring-context` thực tế.
- Bean `TaskExecutor`/`AsyncTaskExecutor` mà `@Async` đang dùng.
- Bean được Boot tạo hay do ứng dụng tự tạo.
- Có dependency `context-propagation` trong runtime không.
- Accessor/registry có được khởi tạo trước khi task gửi không.
- Các đường khác như `@Scheduled`, `CompletableFuture` hoặc thư viện bên ngoài có đi qua executor ấy không.

**Không nhầm với** `spring.reactor.context-propagation=auto`: đây là cấu hình cho cơ chế propagation của Reactor trong các phiên bản Boot hỗ trợ, không phải một lệnh toàn cục ép mọi Java thread pool sao chép ThreadLocal.

## 17. Reactor Context khác ThreadLocal context

Trong Spring WebFlux/Reactor, dữ liệu ngữ cảnh thường được đặt trong `reactor.util.context.Context` gắn với pipeline reactive. Nó không đồng nhất với `ThreadLocal`:

- `ThreadLocal` gắn với thread đang thực thi.
- Reactor `Context` gắn với chuỗi subscription/Publisher.
- Một pipeline reactive có thể đổi thread qua `publishOn`/`subscribeOn` mà không có nghĩa ThreadLocal hiện hành được giữ đúng theo thread mới.

Micrometer Context Propagation cung cấp `ContextAccessor` và các cơ chế snapshot để **chuyển giữa các kiểu context** khi được cấu hình. `ContextPropagatingTaskDecorator` hữu ích tại **ranh giới Runnable của TaskExecutor**, nhưng không tự biến mọi operator Reactor thành một đối tượng có MDC/ThreadLocal được khôi phục.

Nếu ứng dụng chuyển từ WebFlux sang `@Async` hoặc ngược lại, cần kiểm tra rõ:

1. Context nguồn là Reactor Context hay ThreadLocal?
2. Khi nào và ở đâu snapshot được capture?
3. Accessor thích hợp đã đăng ký chưa?
4. Executor/reactive operator nào chịu trách nhiệm restore?
5. Có thể làm mất/ghi đè các key context giữa các scope không?

Với trường hợp chỉ cần request ID để ghi log một task đơn giản, truyền explicit ID qua tham số còn dễ kiểm soát hơn việc dựng pipeline propagation phức tạp.

## 18. Ranh giới cần chọn giải pháp khác

| Nhu cầu | ContextPropagatingTaskDecorator có đủ không? | Giải pháp/kiểm tra thêm |
|---|---|---|
| Truyền custom request ID qua `ThreadPoolTaskExecutor` | Có thể, **khi accessor đã đăng ký** | Test capture/restore, cleanup |
| Giữ trace correlation cho `@Async` | Có thể, với observability integration phù hợp | Kiểm tra tracing bridge/accessor và đúng executor |
| Truyền MDC mọi logging framework | Không có bảo đảm chung | Đánh giá accessor và logging stack |
| Truyền Spring Security context | Không mặc nhiên | Cân nhắc Spring Security wrappers/bridge phù hợp |
| Giữ transaction của caller trên worker | **Không** | Mở transaction riêng, điều phối sau commit |
| Tự truyền context qua Java `ExecutorService` thuần | Không tự | Bọc task/executor hoặc chọn Spring executor có decorator |
| Truyền context qua mọi `CompletableFuture.*Async` | Không tự | Truyền executor phù hợp, xác định context ở lúc schedule |
| Truyền context sang JVM/service khác | Không | HTTP headers, messaging metadata, tracing propagation qua mạng |
| Đảm bảo task sống qua restart | Không | Hàng đợi bền vững, outbox, persistence |
| Giới hạn worker/queue/backpressure | Không | Cấu hình executor, load shedding, rejection policy |

**Điểm khó nhất:** truyền context không tạo ra **quan hệ giao dịch, quyền hạn hay độ bền công việc** giữa caller và worker. Nó chỉ đưa những giá trị được phép và được hỗ trợ đến phạm vi thực thi tương ứng.

## 19. Những lỗi triển khai và cách khắc phục

| Sai lầm | Triệu chứng | Sửa đúng |
|---|---|---|
| Thêm decorator nhưng không có `context-propagation` | Runtime thiếu lớp Micrometer | Khai báo dependency tương thích ở runtime |
| Dùng Spring cũ hơn 6.1 | Không tìm được class decorator | Nâng phiên bản Spring có API đó |
| Chỉ tạo `new ContextPropagatingTaskDecorator()` mà không gắn vào pool | Worker vẫn mất context | `executor.setTaskDecorator(...)` trên **pool đang dùng** |
| Đăng ký custom accessor vào registry riêng nhưng decorator dùng registry toàn cục | Task thấy `null` | Truyền factory sử dụng đúng registry |
| Tưởng mọi `ThreadLocal` tự được truyền | Tenant/MDC không xuất hiện | Đăng ký/kiểm tra `ThreadLocalAccessor` tương ứng |
| Thay `ThreadLocal` sau khi submit rồi mong worker thấy bản mới | Worker dùng giá trị cũ | Hiểu snapshot ở thời điểm schedule; gửi task mới nếu cần |
| Dùng `InheritableThreadLocal` để “sửa” thread pool | Giá trị có thể stale/sai yêu cầu | Truyền context có scope/restore rõ ràng |
| Tự set context trên worker nhưng quên remove | Request B đọc dữ liệu request A | Dùng scope/finally và test worker reuse |
| `@Async` gọi nội bộ `this.method()` | Không đi qua proxy, decorator không chạy | Gọi bean khác qua Spring proxy |
| `@Async` dùng executor khác với bean được decorate | Chỉ một vài task có context | Chỉ rõ `@Async("contextExecutor")` |
| `CompletableFuture.supplyAsync(...)` không truyền executor | Context có thể mất ở common pool | Cung cấp executor và capture đúng lúc |
| Xem context snapshot là sao chép sâu object | Shared mutable object gây race | Dùng immutable value hoặc ownership rõ |
| Sao chép transaction/JDBC resource sang worker | Lỗi transaction hoặc sử dụng tài nguyên sai luồng | Tạo transaction/resource riêng |
| Giả định có trace chỉ nhờ decorator | Không có span/export | Cấu hình observation/tracing bridge |
| Dùng decorator để bắt lỗi `submit` | Exception nằm trong Future | Quan sát `Future.get/CompletableFuture` |
| Bật cho hàng triệu task cực nhỏ mà không đo | Tăng allocation/CPU/GC | Benchmark, giới hạn context, chọn đường khác |

## 20. Kiểm thử đúng: truyền, không truyền và không rò rỉ

Để đánh giá một tính năng context propagation, không chỉ kiểm thử trường hợp “một request một task”.

### 20.1. Bộ kiểm thử tối thiểu

1. **Có context:** Caller set `requestId=A` rồi gửi task vào executor có decorator; worker phải thấy A.
2. **Không có decorator:** Gửi vào pool thường; worker không được vô tình thấy ThreadLocal A của caller.
3. **Đổi context:** Caller đổi thành B trước khi gửi task thứ hai; worker phải thấy B cho task đó, không phải A.
4. **Caller clear:** Sau khi caller remove context, task kế tiếp trên cùng worker phải không thấy A/B.
5. **Tác vụ lỗi:** Task ném exception; scope vẫn đóng, lần gửi kế tiếp không rò context của task lỗi.
6. **Nhiều worker:** Các task xen kẽ thuộc tenant khác nhau phải giữ đúng mỗi snapshot, không bị lẫn.
7. **Thử khi queue chờ:** Caller đổi context sau khi task đã được queue; worker vẫn dùng snapshot tại thời điểm submit.
8. **Nhiều executor:** Một executor có decorator, một executor không; xác nhận ranh giới nào thật sự hỗ trợ.
9. **Không có accessor:** Chứng minh custom ThreadLocal không được propagate khi chưa có bridge.
10. **Dependency runtime:** Đảm bảo deploy artifact chứa jar Micrometer và đúng phiên bản; không chỉ compile pass.

### 20.2. Tránh test dựa vào Thread.sleep

`Thread.sleep(100)` không bảo đảm worker chạy/handler cleanup xong. Dùng `CountDownLatch`, `Future.get(timeout)` hoặc `CompletableFuture` để chờ mốc cụ thể, đặt timeout hữu hạn cho integration test.

Ở demo mục 8, `get(5, TimeUnit.SECONDS)` giúp test có điểm chờ rõ ràng và thất bại khi môi trường không đáp ứng. Với test concurrency tải cao, nên dùng latch/queue có kiểm soát thay vì phụ thuộc vào thứ tự in log của các worker.

### 20.3. Kiểm chứng lỗi thiếu accessor riêng biệt

Nếu không đăng ký accessor cho `TENANT` mà vẫn gắn decorator vào executor, worker có thể không thấy tenant. Điều đó không chứng minh Spring decorator không hoạt động; nó cho biết registry không có cầu nối tới giá trị đó. Test cần phân biệt **thiếu dependency**, **thiếu accessor** và **chọn sai executor**, vì cách sửa khác nhau.

## 21. Checklist triển khai thực tế

Khi một PR thêm `ContextPropagatingTaskDecorator`, hãy lần lượt hỏi:

1. **Phiên bản:** Spring Framework có API từ 6.1+, dependency Micrometer Context Propagation tồn tại trong **runtime** và tương thích?
2. **Registry:** Dữ liệu cần truyền có accessor? Accessor được đăng ký vào registry mà decorator/factory thật sự dùng?
3. **Thời điểm:** Context đã được set trên thread gửi **trước** khi executor nhận task?
4. **Đường thực thi:** Task hoặc `@Async` có đi qua đúng `TaskExecutor` đã gắn decorator?
5. **Scope:** Context có được restore/reset sau mỗi task, kể cả khi ném exception?
6. **Bảo mật:** Không truyền token, principal hoặc request object trái quyền; không dùng propagation thay authorization?
7. **Transaction:** Không cố sao chép transaction/resource gắn với caller thread?
8. **Reactive boundary:** Có pipeline Reactor hay CompletableFuture chuyển executor khác cần chiến lược bổ sung?
9. **Hiệu năng:** Đo overhead context snapshot và tác động ở p95/p99 khi task ngắn hoặc submit quá nhiều?
10. **Vòng đời:** Registry/global accessor được đăng ký nhất quán, không lặp mỗi request; executor có giới hạn worker/queue?
11. **Tính quan sát:** Có kiểm tra log/trace thật, không suy từ việc jar đã được thêm?
12. **Test:** Có test worker reuse, task lỗi, context vắng mặt và nhiều tenant?
13. **Giới hạn cam kết:** Không tuyên bố decorator bảo đảm việc, retry, trace export, rollback hoặc truyền thông tin sang hệ thống ngoài.

## 22. Bài tập thực hành và đáp án gợi ý

### Bài 1 — Phân biệt hai dependency

Ứng dụng có Spring Framework 6.2.x nhưng thiếu `io.micrometer:context-propagation`. Developer thêm `new ContextPropagatingTaskDecorator()`. Việc gì có thể xảy ra?

**Gợi ý:** Thiếu lớp Micrometer tại runtime, có thể gặp `NoClassDefFoundError` hoặc lỗi khởi tạo. Thêm dependency hợp lệ và kiểm tra đúng scope/classpath.

### Bài 2 — Thiếu accessor

Ứng dụng đã gắn decorator vào `ThreadPoolTaskExecutor` nhưng custom `ThreadLocal<String> TENANT` vẫn null trên worker. Cần kiểm tra ba điều gì?

**Gợi ý:** (1) Registry có `ThreadLocalAccessor` cho TENANT không, (2) decorator dùng đúng registry/factory không, (3) caller set TENANT trước khi submit và task đi qua đúng executor không.

### Bài 3 — Snapshot theo thời điểm

Caller set `requestId=R1` rồi `execute(task)`; ngay sau đó đổi thành `R2` trước lúc worker bắt đầu. Task phải nhìn thấy giá trị nào khi context được capture đúng?

**Gợi ý:** R1 — snapshot đã chụp lúc schedule, không phải giá trị mới trên caller.

### Bài 4 — Kiểm tra rò context

Pool chỉ có một worker. Task A thuộc tenant X; task B thuộc tenant Y; task C không có tenant. Thứ tự giá trị nào cần có ở worker nếu truyền đúng và cleanup sạch?

**Gợi ý:** X, Y, rồi null. Nếu C thấy X hoặc Y thì cần điều tra cleanup, accessor reset và baseline thread.

### Bài 5 — @Async gọi nội bộ

Method `start()` trong cùng bean gọi `this.reportAsync()`, mặc dù `reportAsync()` gắn `@Async("contextExecutor")`. Vì sao decorator không có tác dụng như mong muốn?

**Gợi ý:** Self-invocation không đi qua Spring proxy ở chế độ mặc định. Tách thành bean khác để lời gọi được intercept.

### Bài 6 — Continuation ngoài executor

Method `@Async("contextExecutor")` trả CompletableFuture; caller dùng `thenApplyAsync(...)` không truyền executor. Có bảo đảm continuation nhận context từ method đầu không?

**Gợi ý:** Không. Continuation có thể chạy trên common pool; cần chọn executor và xác định context tại thời điểm schedule hoặc dùng cơ chế propagation riêng.

### Bài 7 — Security và transaction

Một task cần đọc dữ liệu tenant và ghi database. Có nên truyền thẳng JDBC Connection và SecurityContext qua custom ThreadLocalAccessor cho worker không?

**Gợi ý:** Không tự ý sao chép resource/transaction thread-bound hoặc dữ liệu bảo mật; dùng các abstraction và ranh giới authorization/transaction phù hợp ở worker.

### Bài 8 — Chạy Maven demo

Chạy project `ContextPropagationDemo`, xác nhận năm dòng output. Sau đó bỏ `registerThreadLocalAccessor(...)` và giải thích vì sao pool có decorator vẫn không có custom tenant.

**Gợi ý:** Không có accessor, snapshot không thể đọc/đặt TENANT. Decorator là hạ tầng bọc task; registry xác định context nào được hỗ trợ.

## 23. Tổng kết

- **`ContextPropagatingTaskDecorator` (Spring 6.1+)** hỗ trợ chụp và khôi phục context tại ranh giới thực thi `Runnable` của một executor phù hợp.
- **Điều kiện tích hợp thiết yếu:** `io.micrometer:context-propagation` phải có trên runtime classpath và có `ThreadLocalAccessor`/`ContextAccessor` phù hợp với dữ liệu cần truyền.
- **Không có phép màu tự sao chép mọi ThreadLocal.** Custom request ID/tenant cần một accessor đúng và đăng ký vào registry mà snapshot factory sử dụng.
- **Gắn decorator vào đúng executor**, ví dụ `ThreadPoolTaskExecutor.setTaskDecorator(...)`; với `@Async` cần thêm `@EnableAsync` và lời gọi qua proxy.
- **Snapshot phản ánh context tại thời điểm gửi**; worker tạm thiết lập context trong scope và khôi phục sau khi task chạy, tránh lẫn dữ liệu giữa các request.
- **Không đồng nhất logging, Observation, tracing, security và transaction.** Mỗi cơ chế cần tích hợp, quyền truy cập và kiểm chứng riêng.
- **Không bỏ qua overhead, scope hoặc lỗi Future.** Decorator không tạo worker, không bắt mọi exception, không bảo đảm job bền vững và không tự truyền context qua mọi pipeline.
- **Kiểm thử có/không decorator, đổi giá trị, clear, lỗi, worker reuse và đúng executor** là bằng chứng vận hành đáng tin hơn việc chỉ thêm dependency.

### Tài liệu chính thức

- [Spring Framework API — ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html)
- [Spring Framework API — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework Reference — Observability Support](https://docs.spring.io/spring-framework/reference/integration/observability.html)
- [Micrometer Context Propagation — Purpose](https://docs.micrometer.io/context-propagation/reference/purpose.html)
- [Micrometer Context Propagation — Examples](https://docs.micrometer.io/context-propagation/reference/usage.html)
- [Micrometer Context Propagation — Installing](https://docs.micrometer.io/context-propagation/reference/installing.html)
- [Micrometer ContextRegistry API](https://javadoc.io/doc/io.micrometer/context-propagation/1.1.3/io/micrometer/context/ContextRegistry.html)
- [Spring Boot Reference — Observability and Context Propagation](https://docs.spring.io/spring-boot/reference/actuator/observability.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-context-007">Đánh giá chi phí trang trí với số lượng tác vụ rất nhỏ</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá chi phí trang trí với số lượng tác vụ rất nhỏ

## 1. Mục tiêu và cách hiểu đúng bài toán

Trong Spring Framework, `TaskDecorator` cho phép bọc một callback `Runnable` trước khi executor chạy nó. Cơ chế này thường được dùng để truyền ngữ cảnh từ thread nộp việc sang worker (correlation ID/MDC, observation/tracing context), đo thời gian hoặc dọn `ThreadLocal` sau khi task hoàn tất.

Với một tác vụ xử lý HTTP/JDBC mất hàng trăm mili-giây, chi phí tạo wrapper và chụp/khôi phục context có thể nhỏ so với thời gian I/O. Nhưng với nhiều tác vụ chỉ làm vài phép tính hoặc đọc một biến, thời gian lập lịch và trang trí có thể chiếm phần đáng kể của toàn bộ thời gian. Javadoc của Spring cho `ContextPropagatingTaskDecorator` cũng cảnh báo thao tác này có overhead, **không khuyến nghị khi ứng dụng chạy rất nhiều tác vụ cực nhỏ**.

Cụm “số lượng tác vụ rất nhỏ” còn nêu một vấn đề đo lường: nếu chỉ chạy **vài task** (ví dụ 5 hoặc 20), kết quả stopwatch sẽ bị chi phối bởi khởi tạo thread, JIT warm-up, GC và nhiễu hệ điều hành. Vì vậy cần tách **hai trường hợp**:

1. **Mỗi task rất ngắn** (*tiny task*), nhưng tổng số task có thể lớn: cần xem chi phí decorator được lặp lại bao nhiêu lần và tỉ trọng overhead.
2. **Tổng số task rất ít** (*small sample count*): chi phí tuyệt đối có thể không quan trọng, trong khi benchmark lại thiếu đủ mẫu để kết luận hơn/kém vài phần trăm.

Bài này trình bày cách phân loại chi phí, giữ đúng semantics context, tạo một chương trình Java 17 để quan sát và đo sơ bộ, cách dùng JMH khi cần số liệu tin cậy hơn, rồi đưa ra tiêu chí cấu hình Spring theo workload thật.

**Nguyên tắc:** không kết luận “TaskDecorator chậm” hay “overhead = X ns” nếu chưa biết task, context, JVM, executor, số mẫu, warm-up và phương pháp đo. Không có con số nano-giây cố định áp dụng cho mọi môi trường.

## 2. TaskDecorator thực thi ở đâu và phát sinh chi phí ở bước nào?

Một `TaskDecorator` của Spring có hợp đồng cơ bản:

```java
@FunctionalInterface
public interface TaskDecorator {
    Runnable decorate(Runnable runnable);
}
```

Đây là chữ ký API minh họa, không phải một source file độc lập. Spring có thể trao cho decorator callback thực thi đã được framework bọc sẵn, không nhất thiết chính `Runnable`/`Callable` người dùng truyền vào.

Ví dụ manual context propagation:

```java
TaskDecorator decorator = task -> {
    String capturedTrace = TRACE_ID.get();  // snapshot trên thread submit

    return () -> {
        String previous = TRACE_ID.get();   // context sẵn có của worker
        try {
            if (capturedTrace == null) {
                TRACE_ID.remove();
            } else {
                TRACE_ID.set(capturedTrace);
            }
            task.run();
        } finally {
            if (previous == null) {
                TRACE_ID.remove();
            } else {
                TRACE_ID.set(previous);
            }
        }
    };
};
```

Đây là snippet tích hợp với biến `TRACE_ID` kiểu `ThreadLocal<String>` được khai báo ở class ứng dụng; nó chưa phải chương trình chạy độc lập. Với một task, có thể xảy ra:

```text
THREAD NỘP VIỆC                      WORKER THREAD
────────────────────────────────────────────────────────────────
đọc TRACE_ID
tạo wrapper / capturedTrace
nộp vào executor
                 ---- queue / dispatch ---->
                                     đọc context cũ
                                     set context mới
                                     task.run()
                                     restore/remove context trong finally
                                     wrapper trả về
```

**Những chi phí khác nhau cần tách:**

| Công đoạn | Loại chi phí có thể phát sinh | Vị trí |
| --- | --- | --- |
| Chụp context khi nộp task | `ThreadLocal.get`, snapshot, sao chép map, cấp phát | Submitter |
| Tạo wrapper | Lambda/đối tượng giữ `Runnable` và context | Thường ở thời điểm decorate/submit |
| Đưa task vào executor | Kiểm tra pool/queue, lock/CAS, contention | Submitter và executor |
| Chuyển thread | Scheduler, context switch, cache locality | JVM/OS |
| Khôi phục context | `ThreadLocal.get/set/remove`, scope allocation | Worker |
| Chạy nghiệp vụ | CPU, I/O, lock, DB, HTTP | Worker |
| Dọn context | `finally` restore/remove, resource cleanup | Worker |
| Monitoring trong decorator | Timer, histogram, log tags, MDC | Worker/caller |
| GC | Bộ nhớ cấp phát từ wrapper/context snapshot | JVM |

Không phải tất cả khoản trên đều do decorator gây ra. **Executor scheduling và task queue tồn tại kể cả không dùng decorator**. Một phép benchmark phải phân biệt tổng chi phí async với **chi phí tăng thêm** khi bật decorator.

### Không nhầm TaskDecorator với logic gọi API

Decorating chỉ bọc callback để chạy thêm mã. Nó không tự làm cho `@Async` song song hơn, không tăng kích thước pool, không bảo đảm DB connection và cũng không biến task thành có thể hủy. Với `@Async`, lời gọi phải đi qua Spring-managed proxy; self-invocation `this.method()` có thể bỏ qua async interceptor, khiến bài test không còn đo executor dự định.

## 3. Mô hình chi phí: tuyệt đối và tương đối

Với một batch gồm N task, có thể viết mô hình tư duy:

```text
T_batch ≈ T_setup
        + T_submission_and_queue
        + T_worker_execution
        + T_context_capture_and_restore
        + T_metrics/logging
        + T_scheduler_and_GC
        + interaction/contension effects
```

Đây là **mô hình định tính**, không phải công thức đo từng thành phần cộng tuyến tính: các bước có thể chồng lấp giữa thread và chịu contention. Để so sánh một workload:

```text
ΔT_batch = T_batch(decorated) - T_batch(plain)

Tương đối (%) = ΔT_batch / T_batch(plain) × 100%
```

Cần cẩn thận khi dùng công thức trên:

- Nếu task rất ngắn, phần `T_context_capture_and_restore` lặp lại với mỗi task; tỉ trọng tương đối có thể tăng đáng kể.
- Nếu task chờ DB 200 ms, overhead của decorator có thể nhỏ xét theo latency, **nhưng** vẫn tiêu tốn CPU/allocations nếu số request mỗi giây rất lớn.
- Với **N=5**, sai khác một lần tạo thread hoặc JIT compilation có thể lớn hơn toàn bộ chi phí decorator. Tính ra “tăng 80%” từ một phép đo ngắn không chứng minh decorator làm chậm ổn định 80%.
- Nếu decorator ghi log đồng bộ cho từng task, chi phí có thể chủ yếu từ formatting/I/O của logger chứ không phải bản thân `ThreadLocal.set`. Đừng so một nhánh bật log với nhánh không log rồi chỉ đổ lỗi cho context propagation.
- Nếu task ngắn và queue đang bão hòa, queue waiting có thể lớn hơn nhiều execution time. Cần tách thời gian **chờ**, **được chạy**, và **toàn bộ từ submit tới complete**.

Ví dụ giả định để minh họa, **không phải số đo của Java/Spring**: một task chạy 5 µs, thêm 1 µs overhead thì tỉ trọng tăng khoảng 20%; một task chạy 50 ms thêm đúng 1 µs chỉ tăng khoảng 0,002%. Đây là phép tính minh họa cho ý tưởng “overhead cố định chiếm tỉ trọng lớn khi task ngắn”, **không** khẳng định chi phí thực tế luôn là 1 µs.

### Vì sao throughput và latency phải đo cùng nhau?

Decorator có thể gây thêm cấp phát trên **thread submit** nhưng kết quả cuối lại biểu hiện ở **GC pause**, hoặc làm tăng CPU worker khiến queue chờ dài ra. Đo một task đơn lẻ không đủ để biết hệ thống có bị giảm throughput ở tải cao hay không. Mặt khác, một throughput benchmark đạt mức cao không chứng minh latency p99 của các request quan trọng vẫn tốt.

## 4. Điều kiện đo công bằng trước khi viết benchmark

Để so sánh “không decorator”, “wrapper rất nhẹ” và “copy/restore context”, hãy đảm bảo cùng:

1. **Loại executor**: dùng một cấu hình thread pool cố định; không so `SimpleAsyncTaskExecutor` tạo thread mới với `ThreadPoolTaskExecutor` tái sử dụng thread rồi quy toàn bộ chênh lệch cho decorator.
2. **Độ dài task**: cùng loại công việc và side effect kiểm chứng được; tránh nhánh được JIT tối ưu bỏ hoàn toàn.
3. **Warm-up**: cho JIT và class loading xảy ra trước phần ghi nhận chính thức, hoặc dùng JMH.
4. **JVM/host**: cùng bản JDK, heap flags, cấu hình logging/tracing, CPU limit và mức tải nền.
5. **Số mẫu**: đo nhiều vòng, đổi thứ tự các mode để bớt thiên lệch do cache/JIT/CPU frequency.
6. **Điểm đo**: định nghĩa rõ đo từ lúc bắt đầu submit tới khi tất cả task hoàn tất, hay riêng thời gian decor/mỗi `run()`.
7. **Khả năng quan sát**: kiểm tra mọi task thực sự hoàn tất; dùng `CountDownLatch`/Future và timeout, không dùng `Thread.sleep(...)` đoán.
8. **Không mất task**: một queue đầy có thể reject task; nếu vô tình bỏ qua lỗi, mode “nhanh nhất” có thể chỉ là mode chạy thiếu công việc.
9. **Không đổi nghĩa**: khi tắt context propagation, task cần context có thể lỗi hoặc dùng nhầm context. Chỉ bỏ decorator nếu nghiệp vụ/trace vẫn đúng.
10. **Quản trị cấp phát**: nhớ rằng wrapper và snapshot có thể tạo object trên mỗi task; đếm allocations/GC nếu throughput cao.

**Tiêu chí giá trị của phép đo:** khác biệt phải lớn hơn mức nhiễu tự nhiên, lặp lại qua nhiều vòng/host và có giải thích về resource usage. Nếu không, kết luận đúng thường là **“chưa có bằng chứng chi phí đáng kể trong điều kiện đã đo”**, không phải “overhead bằng không”.

## 5. Ví dụ Java 17 độc lập: kiểm tra context đúng trước khi đo hiệu năng

Trước khi so sánh tốc độ, phải đảm bảo decorator **giữ đúng semantics**. Code dưới đây là một chương trình độc lập dùng JDK 17, không cần Maven hay Spring. Nó tạo ba mode:

- `PLAIN`: nộp `Runnable` gốc.
- `WRAPPED`: thêm đúng một wrapper gọi `original.run()`, không truyền context.
- `CONTEXT`: snapshot một `ThreadLocal<String>` trên submitter; set/restore trên worker.

**Lưu ý về mô hình:** đây là *analog* của TaskDecorator bằng JDK thuần. Nó không đo trực tiếp chi phí implementation `ContextPropagatingTaskDecorator` của Spring, Micrometer Context Propagation hay MDC thật. Mục tiêu là có một baseline minh bạch, lặp lại được.

Tạo tệp **`TinyTaskDecorationDemo.java`**:

```java
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public final class TinyTaskDecorationDemo {
    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

    enum Mode { PLAIN, WRAPPED, CONTEXT }

    private static void setTrace(String value) {
        if (value == null) {
            TRACE.remove();
        } else {
            TRACE.set(value);
        }
    }

    private static Runnable decorate(Runnable original, Mode mode) {
        return switch (mode) {
            case PLAIN -> original;
            case WRAPPED -> () -> original.run();
            case CONTEXT -> {
                String captured = TRACE.get(); // snapshot on submitter
                yield () -> {
                    String previous = TRACE.get(); // worker context
                    try {
                        setTrace(captured);
                        original.run();
                    } finally {
                        setTrace(previous); // no leak across reused workers
                    }
                };
            }
        };
    }

    private static void verifyContext() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            setTrace("request-173");

            CompletableFuture<String> observed = new CompletableFuture<>();
            worker.execute(decorate(
                    () -> observed.complete(TRACE.get()), Mode.CONTEXT));

            // Chỉ đọc khi job thứ nhất đã hoàn thành giá trị.
            String value = observed.get(5, TimeUnit.SECONDS);

            CompletableFuture<Boolean> leaked = new CompletableFuture<>();
            worker.execute(() -> leaked.complete(TRACE.get() != null));

            // Single-thread executor đảm bảo job kiểm tra chạy sau
            // khi wrapper trước đó trả quyền về worker.
            System.out.println("workerTrace=" + value);
            System.out.println(
                    "contextLeaked=" + leaked.get(5, TimeUnit.SECONDS));
        } finally {
            setTrace(null);
            worker.shutdownNow();
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not terminate");
            }
        }
    }

    private static long runBatch(Mode mode, int taskCount)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch done = new CountDownLatch(taskCount);
        LongAdder completed = new LongAdder();
        setTrace("request-173");

        try {
            long start = System.nanoTime();

            for (int i = 0; i < taskCount; i++) {
                Runnable work = completed::increment;
                Runnable decorated = decorate(work, mode);
                pool.execute(() -> {
                    try {
                        decorated.run();
                    } finally {
                        // Báo hoàn tất sau khi decorator đã restore context.
                        done.countDown();
                    }
                });
            }

            if (!done.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Batch timed out: " + mode);
            }
            long elapsed = System.nanoTime() - start;

            if (completed.sum() != taskCount) {
                throw new IllegalStateException(
                        "Missing tasks: " + mode + "/" + taskCount);
            }
            return elapsed;
        } finally {
            setTrace(null);
            pool.shutdownNow();
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Pool did not terminate");
            }
        }
    }

    private static double medianMillis(long[] durations) {
        long[] copy = durations.clone();
        Arrays.sort(copy);
        return copy[copy.length / 2] / 1_000_000.0;
    }

    public static void main(String[] args) throws Exception {
        verifyContext();

        // 20 task: minh họa ít mẫu; 30.000 task: nhiều task ngắn.
        for (int n : new int[] { 20, 30_000 }) {
            long[][] measurements = new long[Mode.values().length][5];

            // Hai vòng warm-up + năm vòng đo; xoay thứ tự mode.
            for (int round = 0; round < 7; round++) {
                for (int offset = 0; offset < Mode.values().length; offset++) {
                    Mode mode = Mode.values()[
                            (round + offset) % Mode.values().length];
                    long elapsed = runBatch(mode, n);
                    if (round >= 2) {
                        measurements[mode.ordinal()][round - 2] = elapsed;
                    }
                }
            }

            System.out.println("taskCount=" + n);
            for (Mode mode : Mode.values()) {
                System.out.printf(Locale.ROOT,
                        "%s medianBatchMs=%.3f%n", mode,
                        medianMillis(measurements[mode.ordinal()]));
            }
        }

        System.out.println("allBatchesVerified=true");
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac --release 17 TinyTaskDecorationDemo.java
java TinyTaskDecorationDemo
```

**Các dòng output có thể dự đoán chính xác:**

```text
workerTrace=request-173
contextLeaked=false
taskCount=20
PLAIN medianBatchMs=...
WRAPPED medianBatchMs=...
CONTEXT medianBatchMs=...
taskCount=30000
PLAIN medianBatchMs=...
WRAPPED medianBatchMs=...
CONTEXT medianBatchMs=...
allBatchesVerified=true
```

Dấu `...` trong output trên **là ký hiệu mô tả giá trị thay đổi theo máy**, không phải stdout cố định để copy vào test. Mã thực tế sẽ in các số mili-giây, ví dụ theo format `%.3f`. **Không đưa ra số thời gian bịa đặt**; kết quả đúng phải lấy từ máy chạy, nhiều lượt đo và phân tích nhiễu.

### Vì sao bài demo có ý nghĩa?

- `verifyContext()` kiểm tra context ở worker thật bằng một pool 1 thread. Nó cũng kiểm tra worker không giữ `TRACE` sau khi decorator chạy xong.
- `PLAIN` là baseline không wrapper; `WRAPPED` cô lập ý tưởng “thêm một lớp `Runnable`”; `CONTEXT` cộng thêm `ThreadLocal.get/set/remove` và snapshot.
- `CountDownLatch` nằm **bên ngoài decorator** và chỉ đếm sau khi `decorated.run()` đã hoàn tất, nhờ đó phép đo bao gồm cả bước restore trong `finally`. Mọi mode đều dùng cùng một lớp wrapper báo hoàn tất để so sánh công bằng; `LongAdder` xác nhận số task đã chạy.
- `shutdownNow/awaitTermination` dọn pool sau mỗi batch; nếu task không chịu dừng hoặc môi trường quá tải, chương trình báo lỗi thay vì âm thầm bỏ thiếu công việc.
- Thử N=20 và N=30.000 để thấy nguy cơ suy luận từ ít mẫu. Dù N=30.000 lớn hơn, đây vẫn **không phải benchmark nanosecond chuyên nghiệp**.
- Xoay thứ tự chạy giúp giảm một phần thiên lệch do JIT, nhiệt độ CPU và cache; không thay thế thống kê/đo đạc chuyên nghiệp.
- Chu trình work cực ngắn nhằm nhấn mạnh overhead của scheduling và wrapper; workload I/O thật sẽ có kết quả khác.

### Điều demo chưa thể tách hoàn toàn

`runBatch` dùng một pool mới mỗi lượt nên thời gian còn bao gồm khởi tạo thread, đi qua queue, chờ scheduler, `LongAdder`/`CountDownLatch` và cấp phát từ mỗi `Runnable`. Chênh lệch giữa mode **không phải “số nanosecond riêng của decorator”**. Hơn nữa, worker có thể thực thi song song lúc main đang submit, nên tổng thời gian batch không bằng tổng thời gian của N task. Muốn đo chính xác mỗi operation, dùng JMH hoặc profiling sau khi xác định câu hỏi đo.

## 6. Cách diễn giải kết quả: tránh biến phép đo nhiễu thành quyết định kiến trúc

Đừng mặc định `CONTEXT` luôn chậm nhất trong mọi lần chạy. Nhiễu thread scheduling, JIT, GC, CPU throttling hoặc máy đang chạy ứng dụng khác có thể tạo kết quả không theo thứ tự dự đoán.

| Quan sát | Giải thích khả dĩ | Hành động kiểm chứng |
| --- | --- | --- |
| N=20 chênh vài mili-giây giữa mode | Phần lớn có thể do startup/scheduling/GC | Lặp nhiều batch, xem phân phối, không kết luận từ một lần |
| N lớn, CONTEXT chậm ổn định | Snapshot/restore/allocations có thể đáng kể | Profile allocation/CPU, thay workload task, kiểm JMH |
| WRAPPED xấp xỉ PLAIN | Wrapper đơn giản có thể đã được JIT tối ưu tốt | Không suy rộng sang MDC/Micrometer snapshot |
| Decorator bật làm p99 latency cao | Có thể do GC, contention, logging hoặc queue delay | Xem JFR, GC log, queue depth, worker busy |
| Throughput giảm nhưng average latency ít đổi | Bottleneck ở submitter hoặc tài nguyên xử lý | Đo CPU, submission time, saturation theo instance |
| Truyền context đúng ở test nhỏ, lỗi ở production | Có thể thiếu capture ở một đường submit hoặc cleanup | Test với async callback, cancellation, exception và nhiều thread |
| Tắt decorator tăng tốc nhưng mất trace | Thay đổi semantics vận hành | Giữ context tối thiểu hoặc dùng cơ chế tích hợp khác |

**Cách báo cáo kết quả có trách nhiệm:** ghi phiên bản JDK/Spring, CPU/vCPU, số worker, queue policy, N, số vòng, warm-up, GC, logging state; đưa median và p95 nếu có đủ số mẫu. Trình bày chênh lệch tuyệt đối **và** tương đối, cùng phạm vi sai số. Nếu khác biệt nhỏ hơn độ dao động giữa các lượt, viết “chưa xác nhận chênh lệch”.

Không dùng một lần benchmark như bằng chứng rằng ứng dụng sẽ nhanh hơn khi tắt correlation ID; mất khả năng truy vết có thể tạo chi phí vận hành lớn hơn phần tiết kiệm CPU.

## 7. Khi cần đo micro-overhead thật: JMH thay vì stopwatch thô

Java Microbenchmark Harness (**JMH**) của OpenJDK thiết kế để giảm sai lệch do JIT compilation, dead-code elimination, constant folding, warm-up, forking và cách đo sai. Nó phù hợp hơn khi cần so sánh overhead nhỏ của hai cách implement decorator.

Một thiết kế benchmark nên tách **ít nhất hai câu hỏi**:

### 7.1. Chi phí tạo wrapper/capture ở submitter

Đo chi phí chạy `decorate(original)`: capture context và tạo wrapper. Mục tiêu là phát hiện số allocation/task và thời gian chuẩn bị. Cần đảm bảo kết quả wrapper không bị tối ưu loại bỏ; trong JMH dùng `Blackhole.consume(wrapper)` hoặc chiến lược tương đương.

### 7.2. Chi phí chạy wrapper trên worker

Tạo wrapper trước hoặc thiết kế state để tách allocation khỏi execution; đo `wrapper.run()` có set/restore context và task logic cực ngắn. Cần đảm bảo mã nghiệp vụ không bị JIT tối ưu hết.

### 7.3. Chi phí end-to-end của Spring executor

JMH đơn luồng không thay thế việc đo **submit → queue → worker → completion** ở Spring. Với async dispatch có blocking, cần xem thread contention, queue, thực thi song song, pool saturation và timeout. Kết quả “10 ns/op” của một wrapper trong JMH không thể cộng thẳng vào p99 latency mà bỏ qua queue và CPU contention.

Để làm JMH đúng:

- Dùng fork JVM riêng, warm-up iterations và measurement iterations; không chỉ gọi method vài lần trong `main`.
- Cấu hình *state scope* (per-thread hoặc shared) đúng mục đích; lưu ý nếu dùng chung `ThreadLocal` hay counter có contention.
- Tránh loop tự viết quanh benchmark method nếu JMH đã đo operation. Có thể dùng `Blackhole` để giữ side effect quan sát được.
- Kiểm tra allocation bằng profiler hỗ trợ như JFR hoặc JMH GC profiler. Chạy dưới cùng Java version và cùng cấu hình host.
- Đổi task size: no-op/tiny CPU, một số thao tác thật, và task I/O được mock đúng độ trễ; không chỉ test mỗi no-op.
- Đo riêng “số tác vụ cực nhỏ” và “rất nhiều tác vụ nhỏ”; số lượng ít dễ làm overhead setup nổi bật nhưng thiếu ý nghĩa thống kê.
- Khi đo MDC/Micrometer, đăng ký đúng accessor và tạo context có kích thước tương đương production. Một context rỗng không phản ánh chi phí copy map có nhiều entry.

Tham khảo [OpenJDK JMH](https://github.com/openjdk/jmh) để tạo dự án benchmark. Không cần “bịa” con số JMH khi chưa thực sự chạy trong môi trường mục tiêu.

## 8. Cấu hình TaskDecorator trong Spring ThreadPoolTaskExecutor

Bên dưới là **class cấu hình tích hợp** vào Spring Framework 6.2 hoặc Spring Boot dùng `ThreadPoolTaskExecutor`. Nó minh họa cách chụp một correlation ID bằng `ThreadLocal` và khôi phục sau khi task hoàn tất.

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class TinyTaskExecutorConfiguration {
    public static final ThreadLocal<String> CORRELATION_ID =
            new ThreadLocal<>();

    @Bean("contextAwareExecutor")
    public ThreadPoolTaskExecutor contextAwareExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("tiny-work-");

        executor.setTaskDecorator(task -> {
            String captured = CORRELATION_ID.get();

            return () -> {
                String previous = CORRELATION_ID.get();
                try {
                    if (captured == null) {
                        CORRELATION_ID.remove();
                    } else {
                        CORRELATION_ID.set(captured);
                    }
                    task.run();
                } finally {
                    if (previous == null) {
                        CORRELATION_ID.remove();
                    } else {
                        CORRELATION_ID.set(previous);
                    }
                }
            };
        });

        return executor;
    }
}
```

Đây là mã cấu hình **không chạy độc lập** nếu chỉ có JDK: cần dependency `spring-context` và application context. `ThreadLocal` static trong ví dụ phục vụ minh họa; hệ thống thật nên dùng cơ chế context có owner, lifecycle và chính sách dữ liệu rõ ràng, tránh phát tán context nhạy cảm ra ngoài đường xử lý cần thiết.

**Điểm quan trọng:**

- Chụp `CORRELATION_ID.get()` ở nơi decorate để có snapshot của submitter; set ở worker trước `task.run()` và restore trong `finally`.
- Việc capture/restore được thực hiện **mỗi task**; với rất nhiều task ngắn, chi phí cộng dồn có thể trở thành đáng kể.
- Dù `CORRELATION_ID` bằng `null`, ví dụ vẫn thêm wrapper và thực hiện get/remove; nếu hầu hết task không cần context, cân nhắc một chiến lược chọn lọc đã kiểm chứng semantics.
- `queueCapacity=64` và 4 worker chỉ là thông số minh họa; không phải khuyến nghị cho mọi dự án. Thực nghiệm thay đổi một biến mỗi lần và kiểm tra rejection.
- Nếu task bị reject khi submit, callback chưa được chạy; không dùng decorator như nơi bắt `TaskRejectedException` ở submitter.
- Nếu task được bọc bằng `FutureTask`, exception nghiệp vụ có thể nằm ở `Future.get()`, không đi qua `catch` trong decorator; quan sát lỗi phải thiết kế riêng.

### Bật ContextPropagatingTaskDecorator

Khi cần logging/observation context với Micrometer Context Propagation (Spring Framework 6.1+), có thể dùng:

```java
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// Trong cấu hình bean executor đã tạo:
executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
```

Snippet trên cần dependency **`io.micrometer:context-propagation`**, cấu hình `ThreadLocalAccessor`/context registry phù hợp và module Spring được hỗ trợ. Không mặc định nó tự sao chép mọi `ThreadLocal` hoặc thông tin bảo mật; việc truyền gì phụ thuộc các accessor đã đăng ký.

Spring Javadoc nêu trực tiếp: decorator này có **overhead** và không khuyến nghị cho ứng dụng xử lý rất nhiều tác vụ cực nhỏ. Lý do là snapshot/restore context được thực hiện lặp đi lặp lại, có thể tạo scope/đối tượng bổ sung. Nếu chỉ cần một correlation ID và đã có cách truyền rõ ràng bằng tham số, dùng toàn bộ context snapshot machinery có thể không đáng với chi phí — nhưng phải cân nhắc requirement tracing/observation.

### CompositeTaskDecorator và lớp bọc chồng nhau

Nếu dùng nhiều decorator (MDC, observation, custom timer, security context), mỗi lớp có thể làm thêm công việc và tạo wrapper. Không cộng các ước lượng overhead của từng decorator một cách máy móc: chúng có thể chia sẻ context hoặc tạo nested restoration. Hãy đo **tổng cấu hình thật**, kiểm tra thứ tự áp dụng, exception/cleanup và tránh copy trùng cùng một context.

## 9. Có thể tối ưu ở đâu nếu tác vụ quá nhỏ?

### 9.1. Gộp công việc thành batch phù hợp

Giả sử có 10.000 task, mỗi task chỉ cập nhật một giá trị rất nhỏ. Nếu mỗi task đều qua submit/queue/decorator, chi phí per-task lặp lại 10.000 lần. Gộp 100 thao tác thành một task có thể giảm số lần decorate/submit từ 10.000 xuống 100.

Nhưng batching không miễn phí:

- Task lớn hơn có thể tăng latency của từng phần việc, làm mất fairness.
- Cần giới hạn kích thước/thời gian batch, memory footprint và cơ chế retry.
- Một phần việc lỗi có thể khiến phải xử lý lại cả batch; phải có idempotency hoặc checkpoint.
- Với per-request context khác nhau, không được gộp nhiều tenant/user context rồi vô ý dùng một context chung.
- Tránh giữ lock/DB transaction quá dài khi gộp.

### 9.2. Truyền context tối thiểu

Chỉ chụp những giá trị cần thiết. Nếu task cần một correlation ID, truyền ID như tham số của command/DTO có thể rõ ràng hơn snapshot cả MDC map hoặc nhiều ThreadLocal accessors. Tuy nhiên, có những thư viện logging/trace cần context trong ThreadLocal; bỏ propagation khi nó là requirement nghiệp vụ/quan sát là sai. Tối ưu sau khi có baseline.

### 9.3. Tách loại executor theo yêu cầu

Một executor cho các task có tracing đầy đủ và một executor cho các phép tính rất ngắn không cần context có thể giảm overhead; nhưng phải kiểm tra rằng không có đường nào bypass context cần thiết. Không thiết kế việc tách này chỉ để đạt một điểm benchmark đẹp nếu nó tạo thêm concurrency và queue management phức tạp.

### 9.4. Tránh log từng task ở mức đồng bộ

Ghi log INFO cho từng task có thể tốn nhiều CPU/I/O hơn chính task cực nhỏ. Ưu tiên aggregate metrics hoặc sampling có kiểm soát; chú ý tag cardinality và dữ liệu nhạy cảm. Đừng bỏ log lỗi quan trọng hoặc tracing bắt buộc chỉ vì thử nghiệm cho thấy throughput tăng.

### 9.5. Chọn cách thực thi phù hợp

Không phải mọi thao tác 1–2 µs đều đáng được gửi qua executor. Nếu phép toán rất rẻ, không blocking và đang ở thread phù hợp, chạy trực tiếp có thể đơn giản hơn. Nhưng không đưa blocking I/O vào event-loop hoặc request thread chỉ để tránh decorator. Quyết định phải dựa trên latency, isolation, response behavior và công suất downstream.

## 10. Cẩn thận với virtual threads, pool và Spring @Async

Java 21+ có virtual threads, nhưng **Java 17 không có virtual threads**. Spring 6.1+ có hỗ trợ `SimpleAsyncTaskExecutor.setVirtualThreads(true)` trên JDK 21+. So sánh platform-thread pool với virtual-thread executor có thể làm thay đổi chi phí tạo thread, queue, context lookup và scheduling. Đó là **hai kiến trúc thực thi khác nhau**, không phải benchmark sạch để chỉ ra overhead của TaskDecorator.

Với `@Async`, đường đi thông thường là:

```text
Caller
  |
  v
Spring proxy (phải đi qua proxy để kích hoạt @Async)
  |
  v
Executor submit
  |
  +--> decorate actual callback
  |
  +--> queue / worker or virtual thread
            |
            v
        decorator wrapper
            |
            +--> set context
            +--> original callback.run()
            +--> restore context
```

Để đo thực tế, xác định executor bean cụ thể bằng `@Async("contextAwareExecutor")`, tên thread, active count và config. Không dùng một ứng dụng tự gọi `this.asyncMethod()` rồi suy luận decorator không có overhead: self-call thường không đi qua proxy nên không hề submit task theo cách dự định.

Chú ý: nếu task trả `CompletableFuture` hoặc có callback bên trong, decorator bao phủ **execution callback được nộp tới executor đó**, không tự bao trùm toàn bộ chuỗi asynchronous stages chạy trên executor khác hoặc common pool. Nếu chuỗi reactive/CompletionStage cần context tại nhiều ranh giới, phải thiết kế propagation ở từng ranh giới phù hợp.

## 11. Kiểm thử tính đúng đắn khi giảm overhead

Trước khi “tối ưu”, tạo các test:

| Test | Trường hợp | Điều cần kiểm chứng |
| --- | --- | --- |
| A | Context có giá trị khi submit | Worker thấy ID đúng |
| B | Submitter không có context | Worker không dùng context cũ còn sót |
| C | Nhiều request trên cùng worker | ID của request trước không rò sang request sau |
| D | Task ném RuntimeException | `finally` restore/remove vẫn chạy |
| E | Task bị hủy hoặc bị reject | Không kết luận task đã chạy; cleanup đúng tại nơi thực thi |
| F | `@Async` từ bean khác | Gọi thực sự đi qua proxy và executor có decorator |
| G | `CompletableFuture` stages | Context tại mỗi stage được xác định, không tự assume propagation |
| H | Task chậm hoặc lock contention | Decorator không giữ lock/ThreadLocal ngoài scope quá lâu |
| I | Bật logging/tracing thật | Correlation/observation vẫn đầy đủ sau tối ưu |
| J | N=20, N=30.000, task CPU/I/O khác nhau | Kết luận hiệu năng không dựa một trường hợp duy nhất |

Dùng `CountDownLatch` để đồng bộ thời điểm task bắt đầu/kết thúc và kiểm tra biến đếm hoàn tất. Test phải có timeout để không treo. Chỉ đo latency sau khi đã xác minh không mất task và không sai ngữ cảnh.

**Một lỗi nguy hiểm:** bật/tắt decorator tùy cờ context nhưng không có contract rõ; những tác vụ phát sinh từ thread background/HTTP khác nhau có thể bị mất trace hoặc kế thừa context nhầm. Thay đổi cấu hình nên được rollout có metric và kiểm thử regressions.

## 12. Đo production: phân biệt chi phí context với bottleneck khác

Các metric nên ghi nhận ở môi trường thật:

- **Throughput**: tasks/second, số task được chấp nhận/hoàn tất/rejected.
- **Queue**: độ sâu và thời gian chờ; đừng quy queue wait vào TaskDecorator.
- **Worker**: active thread count, pool size, CPU utilization, contention.
- **Latency**: p50/p95/p99 từ submit tới hoàn tất, và riêng thời gian callback.
- **GC/allocation**: allocations per task, GC pause, heap pressure.
- **Context correctness**: số request thiếu correlation ID, trace/span bị đứt, leakage giữa request.
- **Downstream**: JDBC connection pending, HTTP connection pool, 429/503 và retry.
- **Log/observation**: chi phí synchronous formatting/export và cardinality của metric tags.

Dùng JFR/async-profiler/JMH theo mức đo phù hợp. Trong Spring Boot, Micrometer/Actuator có thể cung cấp executor metrics khi binding/configuration đúng; tên metric cụ thể phụ thuộc phiên bản và registry, không mặc định mọi executor tự được instrument đầy đủ.

Một thử nghiệm production an toàn có thể so **hai nhóm traffic tương đương** với bật/tắt một tối ưu đã chứng minh giữ đúng semantics, theo dõi cả error rate/tracing completeness. Không giảm tracing của giao dịch tài chính/nhạy cảm chỉ để lấy benchmark tốt hơn mà chưa xem xét audit/security requirement.

## 13. Sai lầm thường gặp

**“Decorate chỉ tạo một lambda, overhead chắc chắn bằng không.”** Lambda có thể được tối ưu trong một vài bối cảnh, nhưng snapshot/restore, cấp phát wrapper, context accessor, MDC và metric còn chi phí thật. Đo workload.

**“Có 5 task thì mỗi task chậm 30%.”** Chưa chắc; 5 mẫu dễ bị nhiễu. Báo cáo time tuyệt đối và độ phân tán, thử nhiều vòng rồi mới kết luận.

**“Có 50.000 task nên TaskDecorator chắc chắn là bottleneck.”** Không mặc định; có thể bottleneck tại queue, LongAdder/lock, CPU, DB hoặc logging. Profile.

**“Tắt decorator để tăng tốc thì không ảnh hưởng tính đúng.”** Sai nếu business/logging/observability dựa trên context. Test về correlation ID, cleanup và security context trước.

**“ThreadLocal tự truyền sang thread pool.”** Không đúng; `ThreadLocal` thường là per-thread. Worker thread tái sử dụng có thể giữ giá trị cũ nếu không cleanup. `InheritableThreadLocal` cũng không phải giải pháp đáng tin cậy cho pooled workers.

**“ContextPropagatingTaskDecorator copy tất cả mọi thứ.”** Nó truyền các context được đăng ký và hỗ trợ bởi Micrometer accessors; không tự động truyền mọi đối tượng hoặc bảo đảm transaction/secret isolation.

**“Phép đo nanoTime 1 lần là JMH.”** Không. NanoTime hữu ích để đo elapsed trong một chương trình, nhưng benchmark micro-level cần warm-up/fork/statistics và tránh tối ưu hóa không chủ đích.

**“Dùng Thread.sleep(1) mô phỏng tiny task.”** `sleep` khiến task thuộc nhóm waiting/blocked, không phản ánh phép toán CPU ngắn; kết quả chủ yếu từ timer granularity và scheduling.

**“Bỏ decorator rồi vẫn thấy MDC ở worker, nghĩa là propagation ổn.”** Có thể worker đang **rò context cũ**. Test bằng request khác và kiểm tra `ThreadLocal.get()==null` sau cleanup.

**“@Async method đã được gọi nên decorator luôn chạy.”** Self-invocation, dùng sai executor bean, submit trực tiếp vào executor khác hoặc chạy `CompletableFuture.supplyAsync` trên common pool đều có thể bypass decorator đang đo.

**“Decorator catch được tất cả exception nên metrics lỗi luôn chính xác.”** Với Future-backed callback, lỗi có thể nằm trong Future và không thoát khỏi `Runnable.run()`. Quan sát lỗi ở Future/caller hoặc handler chuyên biệt; không đếm sai rồi dùng metric ấy để quyết định performance.

## 14. Quy trình quyết định và tài liệu tham khảo

Một quy trình ngắn, có bằng chứng:

1. **Xác định workload:** ít task thực sự hay rất nhiều task cực nhỏ? Workload là CPU, blocking I/O hay mixed?
2. **Xác định yêu cầu context:** cần MDC/tracing/security/transaction? Cái gì phải được truyền, cái gì tuyệt đối không được copy?
3. **Kiểm chứng correctness:** context đúng ở worker và không rò sau task, kể cả lỗi/cancel/rejection.
4. **Đo baseline:** executor và cấu hình thật không decorator; xác nhận task được hoàn tất, không bị silently discarded.
5. **Đo decorator nhẹ và decorator thực:** giữ cùng pool/task, warm-up, nhiều vòng, nhiều cỡ N và nhiều task sizes.
6. **Phân tích metric:** median/p95/p99, throughput, allocation/GC và queue, không chỉ % dựa một phép đo.
7. **Xác định tác nhân:** wrapper allocation, MDC copy, ContextSnapshot, logging/export, scheduling hay downstream.
8. **Chọn tối ưu:** batching, truyền context tối thiểu, sampling, executor tách biệt, hoặc giữ nguyên nếu overhead không có ý nghĩa.
9. **Kiểm thử regressions và rollout:** ngữ cảnh, trace completeness, behavior khi overload và khi exception phải còn đúng.
10. **Ghi lại điều kiện đo:** JDK/Spring, máy, thread count, warm-up, repetitions và kết quả có bất định.

### Tài liệu chính thức

1. [Spring Framework 6.2 – TaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/core/task/TaskDecorator.html): callback thực thi, context/monitoring và giới hạn nhìn thấy exception.
2. [Spring Framework 6.2 – ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html): context propagation và cảnh báo overhead khi có nhiều tác vụ rất nhỏ.
3. [Spring Framework 6.2 – ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): cấu hình `setTaskDecorator` và tài nguyên pool/queue.
4. [Micrometer Context Propagation – Purpose](https://docs.micrometer.io/context-propagation/reference/purpose.html): `ThreadLocalAccessor`, `ContextSnapshot` và propagation qua ranh giới thread.
5. [OpenJDK – Java Microbenchmark Harness](https://github.com/openjdk/jmh): warm-up, fork, measurement và microbenchmark.
6. [Java SE 17 – ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): scope per-thread, `get/set/remove`.
7. [Java SE 17 – ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): submission, worker lifecycle, shutdown và Future.

**Kết luận:** chi phí TaskDecorator phụ thuộc **số lần trang trí, độ ngắn của mỗi task và loại context phải chụp/khôi phục**. Nếu chỉ có rất ít task, phép đo cũng dễ nhiễu và overhead tuyệt đối có thể không đáng kể. Hãy giữ đúng semantics context trước, đo có baseline và đủ mẫu sau, rồi tối ưu có chọn lọc thay vì tắt tracing hoặc suy đoán từ một con số stopwatch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-context-008">Không giữ toàn bộ đối tượng request sau khi yêu cầu đã kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# Không giữ toàn bộ đối tượng request sau khi yêu cầu đã kết thúc

> **Module:** Spring Framework – concurrency  
> **Nhóm kiến thức:** Ngữ cảnh thực thi  
> **Nguyên tắc:** Khi gửi công việc ra `ThreadPoolTaskExecutor`, `@Async`, `CompletableFuture` hoặc một hàng đợi, **không giữ tham chiếu tới toàn bộ `HttpServletRequest`/`ServletRequestAttributes` hoặc đối tượng gắn với vòng đời request** nếu công việc có thể tiếp tục sau khi request kết thúc. Hãy **trích xuất đúng những dữ liệu cần thiết tại ranh giới request**, kiểm tra và chuyển thành **snapshot tối thiểu, bất biến**, không chứa request/response/session/container object.

Bài học tập trung vào Java 17, Spring MVC/Servlet và thực thi bất đồng bộ. Cần phân biệt **controller method kết thúc** với **Servlet async request thật sự hoàn thành**: Spring MVC có thể tiếp tục giữ response mở trong một quy trình async hợp lệ. Điều bị cấm về thiết kế ở đây là *lưu lại/đọc lại toàn bộ request ngoài vòng đời hợp lệ*, chứ không phải phủ nhận Servlet async được tiêu chuẩn hỗ trợ.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Phân biệt thời gian sống của `HttpServletRequest`, `HttpServletResponse`, request-scoped bean, tham chiếu tới `ServletRequestAttributes` và context được đặt trong `ThreadLocal`.
2. Nhìn ra các đường giữ tham chiếu **trực tiếp hoặc gián tiếp** qua lambda, anonymous class, `CompletableFuture` callback, `@Async` argument, queue, scheduled task, cache, static map và `TaskDecorator`.
3. Giải thích nguy cơ: đọc dữ liệu đã hết hiệu lực, race/cross-request contamination, giữ object graph/payload quá lâu, rò rỉ dữ liệu, lỗi khi context không còn active.
4. Thiết kế record/DTO bất biến nhỏ gọn với các trường thực sự cần: `actorId`, `tenantId`, `correlationId`, `jobId`, tham số nghiệp vụ đã kiểm tra.
5. Dùng `@Async`/`ThreadPoolTaskExecutor`/`CompletableFuture` trong Spring mà task **chỉ nhận snapshot**, không truy xuất lại raw request khi worker chạy.
6. Phân biệt việc chuyển vài giá trị logging/tracing với việc **truyền cả request attributes**, security context hoặc session qua thread.
7. Xử lý file upload, body stream, cancellation, timeout, retry, shutdown và công việc chạy lâu sau khi response đóng.
8. Viết ví dụ Java 17 và Spring Context có output xác định; kiểm thử bằng latch và assertion có thể tái lập, không phụ thuộc vào garbage collector.

## 2. Request thuộc về ai và sống bao lâu?

Trong Servlet stack, container tạo hoặc quản lý một `HttpServletRequest` tương ứng với quá trình xử lý HTTP. Object này không phải DTO do ứng dụng sở hữu tự do; nó có thể mang hoặc dẫn tới:

- HTTP headers, parameters, cookies, session-related data.
- Các attribute do filter/interceptor/Servlet/Spring thêm vào.
- Stream/body, multipart metadata và wrapper liên quan.
- References đến `ServletContext`, các container wrappers hoặc tài nguyên tạm.
- Thông tin authentication/locale qua các subsystem liên quan đến request.
- Trạng thái async dispatch và response tương ứng.

**Không phải thuộc tính nào cũng chắc chắn tồn tại trên mọi implementation**. Điều cần nhớ là **một tham chiếu tới `HttpServletRequest` có thể giữ lại nhiều đối tượng ngoài ý muốn**. Nó cũng gắn với một *hợp đồng về thời gian sử dụng*, không chỉ vấn đề RAM.

### 2.1. Servlet request đồng bộ

Với request thông thường, Servlet specification giới hạn hiệu lực của request object vào phạm vi `service()` hoặc filter `doFilter()`. Khi xử lý kết thúc và container thu hồi quyền kiểm soát, không được giả định request vẫn dùng được. Container có thể tái sử dụng/recycle object để giảm chi phí cấp phát. Vì vậy, một lambda chạy nền vài giây sau đó gọi `request.getHeader(...)` là **thiết kế sai**, dù ở môi trường local thỉnh thoảng nó vẫn trả về giá trị.

~~~text
Container nhận HTTP request
       |
       v
Filter -> DispatcherServlet -> Controller
       |
       +-- Đọc/kiểm tra request (trong lifecycle hợp lệ)
       |
       +-- Tạo immutable WorkInput với vài scalar fields
       |
       +-- Enqueue work(WorkInput)
       |
       v
HTTP request kết thúc
       |
       +-- Container có thể cleanup/recycle request
       |
       v
Worker chạy muộn
       |
       +-- Chỉ sử dụng WorkInput (không giữ HttpServletRequest)
~~~

**Không làm:** chuyển chính `HttpServletRequest` vào worker với hy vọng nó sống đến khi worker cần.

### 2.2. Ngoại lệ quan trọng: Servlet async request hợp lệ

Servlet API có mô hình bất đồng bộ: `request.startAsync()` trả về `AsyncContext`. Khi async processing thực sự đã được khởi tạo, request/response có **vòng đời async hợp lệ**: request object vẫn có hiệu lực trong thời gian xử lý async cho đến khi `AsyncContext.complete()` (và những chuyển tiếp/timeout theo hợp đồng container).

Trong Spring MVC, các kiểu trả về như `Callable`, `DeferredResult`, `CompletionStage` hoặc `CompletableFuture` được framework tích hợp với Servlet async processing. Controller có thể **trả về trước**, Servlet thread được giải phóng, nhưng logical HTTP request/response **chưa hoàn tất**.

~~~text
Controller method returns CompletableFuture
              |
              v
Spring MVC bắt đầu Servlet async processing
              |
              +-- Request/response thuộc vòng đời async hiện hành
              +-- Container thread ban đầu có thể rời filter chain
              |
              +-- Future completes -> Async dispatch -> write response
              |
              v
Async request completes / timeout / error
              |
              v
Không sử dụng lại request/response của vòng đời đã hoàn tất
~~~

Do vậy, **không đồng nhất** “method controller return” với “request hoàn tất”. Tuy nhiên, việc Servlet async duy trì hiệu lực **không có nghĩa** mọi API trên `HttpServletRequest` đều thread-safe, hoặc bất kỳ worker nào cũng có thể truy cập tùy ý. Tự nộp một `Runnable` vào executor **không tự động** kéo dài vòng đời Servlet request, nếu framework chưa thực sự thiết lập async lifecycle phù hợp.

**Quy tắc thiết kế vẫn nên giữ:** worker nghiệp vụ sử dụng snapshot tối thiểu. Để framework/container kiểm soát request/response; đừng biến sự hiện diện của `AsyncContext` thành lý do lưu trữ raw request ngoài phạm vi.

### 2.3. Đâu là sự kiện “yêu cầu đã kết thúc”?

Với request thường: sau khi Servlet/filter processing thực sự kết thúc theo lifecycle. Với request async: sau khi async handling hoàn tất, bị timeout, lỗi hoặc được complete theo cơ chế framework/container. **Đừng dự đoán bằng một dòng controller `return` duy nhất**.

Các thông tin như request đã được client đóng kết nối, Spring MVC đã hoàn thành response hay asynchronous task nội bộ đã xong là những tín hiệu khác nhau; một task fire-and-forget có thể chạy lâu hơn cả request. Việc client hủy request cũng không đảm bảo Java task bất kỳ sẽ tự bị hủy.

## 3. Vấn đề trọng tâm: giữ một object graph, không chỉ một biến

Giả sử một lambda giữ biến `request`:

~~~java
executor.execute(() -> {
    String tenantId = request.getHeader("X-Tenant-Id");
    generateReport(tenantId);
});
~~~

Đây là **snippet không độc lập**: `executor`, `request` và `generateReport` thuộc ứng dụng.

Có hai vấn đề:

1. **Thời điểm truy cập:** lambda có thể chạy sau khi response hoàn tất, nên `request.getHeader` nằm ngoài lifecycle thích hợp.
2. **Strong reference:** lambda/task phải giữ `request` để có thể chạy sau. Nếu queue dài hoặc future bị giữ trong một map, `request` có thể tiếp tục **reachable** dù request HTTP đã kết thúc, kèm các wrapper, attributes và các đối tượng mà nó dẫn tới.

Một sơ đồ giữ tham chiếu:

~~~text
Singleton / ThreadPoolExecutor
     |
     +-- workQueue
             |
             +-- Runnable / Callable / FutureTask
                       |
                       +-- lambda captured request
                                 |
                                 +-- HttpServletRequest
                                 +-- attributes / wrappers
                                 +-- session-related references
                                 +-- potentially large payload and buffers
~~~

GC không thể thu hồi object graph khi vẫn còn đường tham chiếu mạnh từ các root. Không phải mọi request được capture đều tạo **memory leak vĩnh viễn**: nếu task nhanh chóng hoàn tất và tham chiếu được giải phóng, retention có thể chỉ tạm thời. Nhưng queue kéo dài, scheduler, static cache, unresolved future hoặc worker bất thường có thể biến **retention tạm thời thành vấn đề nghiêm trọng**.

### 3.1. Vì sao queue capacity liên quan trực tiếp?

Giả sử một task vô tình giữ khoảng **1 MiB** dữ liệu từ object graph của request, và queue giữ 5.000 task như vậy. Riêng phần dữ liệu đó có thể bị giữ khoảng **5.000 MiB (xấp xỉ 4,88 GiB)**, chưa tính overhead của objects, futures và thread pools. Đây chỉ là **ví dụ tính toán minh họa**, kích thước object graph thực tế phải đo bằng heap profiler.

Do đó, ngoài việc chỉ giữ snapshot, cần giới hạn `queueCapacity`, số task đang chờ, deadline và chính sách từ chối. Tạo một DTO có chứa toàn bộ body 10 MiB cũng không giải quyết vấn đề memory chỉ bằng việc đổi tên object.

## 4. Những dạng capture trực tiếp và gián tiếp

### 4.1. Lambda bắt biến request

~~~java
CompletableFuture.runAsync(() -> {
    audit(request.getRequestURI());
}, executor);
~~~

Sai khi task có thể chạy sau lifecycle; `CompletableFuture` không tự làm request sống hợp lệ. **Giải pháp:** lấy giá trị cần thiết trước khi schedule:

~~~java
String path = request.getRequestURI(); // Trong request lifecycle.
CompletableFuture.runAsync(
        () -> audit(path),
        executor);
~~~

Đây là **snippet**; `audit` và `executor` là dependency ứng dụng. Hãy chuẩn hóa/giới hạn dữ liệu path trước khi log; URL có thể chứa thông tin nhạy cảm.

### 4.2. Capturing `this` của controller/handler

~~~java
executor.execute(() -> this.processLater());
~~~

Vấn đề không phải mọi capture `this` đều sai: nếu `this` là singleton stateless service, việc tham chiếu service không kéo raw request. Nhưng nếu controller/handler có **field chứa request hiện tại** hoặc object request-scoped, lambda có thể gián tiếp giữ toàn bộ request. Đặc biệt **không lưu request vào field mutable của singleton controller**, vì các request đồng thời sẽ ghi đè lẫn nhau.

Nên giữ controller stateless và truyền argument snapshot vào service.

### 4.3. Anonymous class và inner class

Một anonymous/inner class có thể giữ tham chiếu tới outer instance dù không nhìn rõ trong code. Nếu outer chứa request, session, file stream hoặc mutable holder, task có thể kéo theo cả chuỗi object. Static nested class hoặc record nhỏ có dependency hiển thị rõ là một lựa chọn dễ audit hơn.

### 4.4. CompletableFuture callback

~~~java
future.whenComplete((result, error) -> {
    request.setAttribute("result", result);
});
~~~

Callback có thể chạy trên một thread khác hoặc sau HTTP request đã kết thúc; `request.setAttribute` không phải kênh trả dữ liệu hợp lệ cho job nền. Future/callback cũng có thể giữ tham chiếu đến `request` cho đến khi pipeline hoàn tất.

Tương tự với `thenApply`, `thenCompose`, `handle`, `exceptionally` và `thenCombine`: xem toàn bộ closure được capture, không chỉ lambda đầu tiên.

### 4.5. Task/scheduler/cache giữ request quá lâu

Không lưu các object này vào `@Scheduled` job, `DelayQueue`, message payload, `ConcurrentHashMap` toàn cục, application event dùng executor bất đồng bộ hoặc state singleton. Nếu tác vụ cần tiếp tục sau restart, lưu **dữ liệu nghiệp vụ đã kiểm tra** vào kho bền vững, không serialize request container.

### 4.6. Capture qua `ServletRequestAttributes`

~~~java
RequestAttributes attrs =
        RequestContextHolder.currentRequestAttributes();

executor.execute(() ->
        useAttributesLater(attrs));
~~~

Đây là **snippet có import Spring web**. Với request Servlet thông dụng, `ServletRequestAttributes` thường giữ tham chiếu tới request/response; vì thế capture nó có thể **gián tiếp** giữ toàn bộ request. Ngoài nguy cơ retention, xử lý các attributes đó sau lifecycle có thể lỗi. Không “đóng gói” request vào một wrapper rồi gọi nó là snapshot.

## 5. Đừng nhầm ThreadLocal context với dữ liệu bền cho công việc nền

Trong Spring MVC, `RequestContextHolder` thường công bố `RequestAttributes` qua `ThreadLocal` gắn với thread xử lý hiện tại. Spring Security và logging frameworks cũng có context lưu theo thread. Một worker từ `ThreadPoolTaskExecutor` là thread khác, được **tái sử dụng** cho nhiều task.

Khi rời request thread:

- `RequestContextHolder.getRequestAttributes()` trên worker có thể là `null`; `currentRequestAttributes()` có thể ném lỗi nếu không có context hợp lệ.
- `@RequestScope` bean gắn với HTTP request, không phải bean có thể tùy ý dùng khi không còn request scope. Scoped proxy không tự gia hạn request lifecycle.
- MDC/locale/SecurityContext thường không tự truyền đúng qua mọi kiểu executor; nếu cần, dùng cơ chế propagation rõ ràng.
- Nếu copy toàn bộ `RequestAttributes` sang worker, bạn có thể giữ raw request lâu hơn hoặc truy cập object ngoài lifecycle.
- Nếu không cleanup `ThreadLocal` trên pooled thread, task kế tiếp có thể nhìn thấy correlation/user/tenant của task trước. Đây là vấn đề bảo mật và tính đúng đắn, không chỉ memory leak.

### 5.1. Vì sao InheritableThreadLocal không phải giải pháp cho thread pool?

`RequestContextFilter.setThreadContextInheritable(true)` hoặc cơ chế inheritance tương tự chỉ phù hợp trong điều kiện rất hẹp: child thread được tạo riêng cho request và **không được tái sử dụng**. Spring Javadoc cảnh báo rõ không nên bật inheritance cho thread pool có thể tạo worker mới theo nhu cầu. Một pooled worker có thể kế thừa context của request ngẫu nhiên tại thời điểm thread được tạo rồi tiếp tục tái sử dụng context đó cho những request khác.

**Không bật kế thừa toàn bộ request context chỉ để @Async “nhìn thấy” HttpServletRequest.**

### 5.2. Propagation ≠ snapshot cho job dài hạn

Context propagation là việc mang một số trạng thái xuyên thread theo **phạm vi thực thi**. Nó hữu ích cho tracing/logging trong một chain có vòng đời xác định. Nhưng không nên hiểu là được phép giữ cả `ServletRequestAttributes` trong một task có thể tồn tại hàng phút/hàng giờ sau response.

Spring có `ContextPropagatingTaskDecorator` dựa trên Micrometer Context Propagation cho một số loại context; nếu registry có accessor dành cho request attributes, việc truyền context đó cần được xem xét kỹ theo lifecycle. Đây **không phải** cơ chế biến raw request thành dữ liệu an toàn sau completion.

## 6. Cách đúng: trích xuất snapshot tối thiểu, bất biến

Định nghĩa các giá trị worker **thực sự cần**, không mang toàn bộ “context để cho chắc”.

Ví dụ Java 17:

~~~java
public record JobInput(
        String jobId,
        String actorId,
        String tenantId,
        String correlationId,
        String documentId) {

    public JobInput {
        java.util.Objects.requireNonNull(jobId);
        java.util.Objects.requireNonNull(actorId);
        java.util.Objects.requireNonNull(tenantId);
        java.util.Objects.requireNonNull(correlationId);
        java.util.Objects.requireNonNull(documentId);
    }
}
~~~

Đây là **tệp/record Java 17** có thể dùng trong ứng dụng; khi khai báo `public record JobInput` ở top-level, phải lưu trong **`JobInput.java`**. Nó không có `main` nên không phải demo chạy độc lập.

Các field đều là `String` bất biến nên record này là một **snapshot bất biến về dữ liệu chứa bên trong**. Record chỉ đảm bảo các reference field là `final`; nếu field chứa `List<MutableItem>`, `byte[]` hoặc một entity mutable, record **không tự deep-copy**.

### 6.1. Chọn đúng trường

| Nhu cầu của worker | Nên truyền | Tránh truyền |
| --- | --- | --- |
| Định danh user/actor | `actorId` ổn định, đã xác thực | Raw `Principal`/`Authentication` object, toàn security session nếu không cần |
| Phân biệt tenant | `tenantId` đã validate/authorize | Header `X-Tenant-Id` nguyên văn khi chưa xác minh |
| Liên kết log/tracing | `correlationId` đã chuẩn hóa, hạn chế độ dài | `HttpServletRequest`, tất cả headers/cookies |
| Xử lý đơn/biểu mẫu | `orderId`, `documentId`, DTO đã validate | Cả `@RequestBody` mutable hoặc raw input stream |
| Gửi file lớn | Storage object key/URI nội bộ có ACL | `MultipartFile`/`ServletInputStream` còn gắn vào request |
| Điều khiển deadline | Deadline/timeout đơn giản và có hợp đồng | Container request/response để worker “tự hỏi thời gian” |
| Phục vụ phân quyền | Actor ID và bằng chứng tối thiểu theo policy | Token, password, bearer Authorization header |

Việc chỉ truyền actor ID không tự động cấp quyền cho job. Nếu tác vụ chạy lâu, xử lý quyền thực sự có thể cần **kiểm tra lại tại thời điểm thực thi** vì quyền của user có thể đã thay đổi. Với workload đặc biệt cần identity delegation, dùng contract bảo mật chính thức thay vì cache raw token vô thời hạn.

### 6.2. Snapshot phải được tạo đúng thời điểm

~~~text
HTTP request thread
    |
    +-- Authenticate/authorize user
    +-- Validate payload, headers, tenant
    +-- Extract stable IDs + small fields
    +-- Construct immutable JobInput
    +-- Send JobInput to bounded executor/queue
    |
    v
Request completes

Worker:
    |
    +-- Only JobInput + injected services
    +-- Perform business work with own transaction
    +-- Report success/failure using jobId
~~~

Không tạo snapshot **bên trong worker** bằng cách chờ rồi đọc lại `HttpServletRequest`; hành động đó đã quá muộn. Nếu cần body, đọc/validate **trong lifecycle hợp lệ** và cân nhắc lưu payload cần thiết vào kho bền nếu nó lớn hoặc xử lý lâu.

### 6.3. Immutable collection và array

~~~java
record BatchInput(String tenantId, java.util.List<String> ids) {
    BatchInput {
        ids = java.util.List.copyOf(ids);
    }
}
~~~

Đây là **record snippet**: `List.copyOf` tạo một collection unmodifiable cho tập các `String` bất biến. Nếu phần tử là object mutable, vẫn phải deep-copy nội dung nếu worker cần trạng thái nhất quán. Với `byte[]` cần clone khi nhận/trả để tránh caller sửa mảng sau khi submit. Với `Map<String, Object>` tùy ý, **không** được xem là snapshot bất biến nếu value chứa entity/request object mutable.

## 7. Java 17 chạy độc lập: mô phỏng lifecycle và sửa lỗi

Ví dụ sau **không triển khai Jakarta Servlet**; nó mô phỏng chính xác vấn đề *dùng dữ liệu của request sau khi request đã bị đóng* bằng một object thử nghiệm. Một `CountDownLatch` bảo đảm cả task xấu và tốt chỉ chạy **sau** lúc request giả lập đã hết hiệu lực, vì thế stdout không phụ thuộc may rủi scheduler hay `Thread.sleep`.

Tạo tệp **`RequestLifetimeDemo.java`**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RequestLifetimeDemo {

    static final class SimulatedRequest {
        private final String requestId;
        private final String tenantId;
        private boolean active = true;

        SimulatedRequest(String requestId, String tenantId) {
            this.requestId = requestId;
            this.tenantId = tenantId;
        }

        synchronized String read(String key) {
            if (!active) {
                throw new IllegalStateException("request already ended");
            }
            return switch (key) {
                case "requestId" -> requestId;
                case "tenantId" -> tenantId;
                default -> throw new IllegalArgumentException(key);
            };
        }

        synchronized void end() {
            active = false;
        }
    }

    record Snapshot(String requestId, String tenantId) { }

    static void await(CountDownLatch gate) {
        try {
            if (!gate.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("gate timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch gate = new CountDownLatch(1);
        SimulatedRequest request =
                new SimulatedRequest("req-17", "tenant-A");

        // Snapshot được tạo khi request còn active.
        Snapshot input = new Snapshot(
                request.read("requestId"),
                request.read("tenantId"));

        try {
            // SAI: lambda giữ chính request; sẽ đọc quá muộn.
            CompletableFuture<String> bad =
                    CompletableFuture.supplyAsync(() -> {
                        await(gate);
                        return request.read("requestId");
                    }, worker);

            // ĐÚNG: lambda chỉ giữ hai chuỗi trong snapshot.
            CompletableFuture<String> good =
                    CompletableFuture.supplyAsync(() -> {
                        await(gate);
                        return input.requestId()
                                + "/" + input.tenantId();
                    }, worker);

            request.end();   // Mô phỏng HTTP request hoàn tất.
            gate.countDown();

            try {
                bad.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable root = e.getCause();
                System.out.println("bad = "
                        + root.getClass().getSimpleName()
                        + ": " + root.getMessage());
            }

            System.out.println("good = "
                    + good.get(5, TimeUnit.SECONDS));
        } finally {
            gate.countDown();
            worker.shutdown();
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac RequestLifetimeDemo.java
java RequestLifetimeDemo
~~~

**Stdout chính xác khi chạy thành công:**

~~~text
bad = IllegalStateException: request already ended
good = req-17/tenant-A
~~~

### Vì sao ví dụ có output xác định?

1. `request.read(...)` được gọi để tạo `Snapshot` trước `request.end()`, nên lấy được hai scalar fields ổn định.
2. Task “bad” được enqueue trước nhưng chờ `gate`; `main` gọi `request.end()` **trước** `gate.countDown()`.
3. Vì thế khi bad task đọc request, object giả lập luôn đã ở trạng thái ended; `CompletableFuture` lưu `IllegalStateException`, `get(...)` trả `ExecutionException` với cause gốc.
4. Task “good” không truy cập `request`, chỉ đọc record bất biến; nó trả `req-17/tenant-A` sau khi gate mở.
5. `get(timeout)` và `awaitTermination` làm rõ thời điểm chờ; chương trình không dùng `Thread.sleep` hoặc đợi GC.

**Giới hạn mô phỏng:** Servlet container thật có nhiều trạng thái và có `AsyncContext`; việc một container object đọc sau lifecycle có ném `IllegalStateException` hay không **không có bảo đảm giống fake này**. Demo cố chứng minh **mẫu dependency/lifecycle an toàn**, không mô phỏng byte-for-byte cách một container thật recycle request.

## 8. So sánh trực tiếp: từ code dễ lỗi sang code an toàn

### 8.1. Bad: gửi HttpServletRequest vào async service

~~~java
@Service
class BadReportService {
    @Async("reportExecutor")
    public void generate(HttpServletRequest request) {
        // Request có thể đã hết vòng đời vào lúc worker chạy.
        String tenant = request.getHeader("X-Tenant-Id");
        createReport(tenant);
    }
}
~~~

Đây là **snippet minh họa lỗi**, yêu cầu import Spring `@Service`, `@Async`, `HttpServletRequest` và service `createReport`. Cả việc truyền request qua proxy lẫn giữ closure sau đó đều có thể kéo request sang một vòng đời không phù hợp.

### 8.2. Good: tạo snapshot trong controller

~~~java
public record ReportInput(
        String reportId,
        String actorId,
        String tenantId,
        String correlationId) { }

@RestController
class ReportController {
    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/reports/{reportId}")
    public CompletableFuture<String> start(
            @PathVariable String reportId,
            @RequestHeader("X-Correlation-Id") String correlationId,
            Principal principal) {

        // Ví dụ: tenantId lấy từ identity đã xác thực,
        // KHÔNG tin header tenant tuỳ ý của client.
        String actorId = principal.getName();
        String tenantId = lookupAuthorizedTenant(actorId);
        String traceId = validateCorrelationId(correlationId);

        ReportInput input =
                new ReportInput(reportId, actorId, tenantId, traceId);
        return reports.generate(input);
    }
}
~~~

Đây là **ví dụ Spring MVC tích hợp (không standalone)**. Cần import `CompletableFuture`, `Principal`, annotation MVC, và triển khai `lookupAuthorizedTenant`/`validateCorrelationId` theo security/business contract. `ReportInput` phải được khai báo trong file riêng nếu để `public record` ở top-level. `reports.generate(input)` trả future cho Spring MVC, để framework quản lý async response. Worker không cần request, principal hay session.

Một service tương ứng:

~~~java
@Service
class ReportService {
    @Async("reportExecutor")
    public CompletableFuture<String> generate(ReportInput input) {
        // Có thể bắt đầu sau lúc controller đã return.
        // Sử dụng input đã validate và các bean cần thiết.
        String result = "report-" + input.reportId()
                + "-tenant-" + input.tenantId();
        return CompletableFuture.completedFuture(result);
    }
}
~~~

Đây cũng là **snippet Spring**, cần `@EnableAsync`/executor bean và lời gọi qua Spring proxy. Không trả `HttpServletResponse` từ worker hoặc cố sửa request attributes để chuyển kết quả. Ngoại lệ từ method `@Async CompletableFuture` cần được quan sát/đưa về HTTP error mapping phù hợp.

### 8.3. Dữ liệu header chưa chắc đáng tin

`X-Tenant-Id`, `X-User-Id`, `X-Forwarded-For` và `X-Correlation-Id` có thể do client gửi. **Không** biến chúng thành “identity đã xác thực” chỉ vì đã copy từ request sang DTO. Hãy xác thực/chuẩn hóa theo trust boundary: security principal/claims đã verify, policy tenant, allowlisted proxy headers, length/character restrictions cho correlation ID.

Khi dùng `principal.getName()` làm actor ID, phải xét mapping định danh thực tế; không nhất thiết username hiển thị là key bền. Với job dài hạn, có thể dùng internal account ID không thay đổi khi đổi tên tài khoản.

## 9. Spring @Async: boundary dữ liệu và proxy

### 9.1. @Async không lưu giữ request hợp lệ

`@Async` điều phối việc gọi method qua Spring proxy tới một executor. Nó **không** tự kéo dài lifetime của `HttpServletRequest` được truyền vào argument, và không có nghĩa thread worker là thread servlet xử lý ban đầu.

~~~text
HTTP thread:
    validate -> build ReportInput -> asyncBean.generate(input) -> return

Spring proxy:
    submit invocation to executor

Worker thread:
    call generate(ReportInput) -> result / failure
~~~

Nếu method trả `void` và request đã trả HTTP response trước khi worker xong, cần có job ownership/logging/retry/durability riêng. Nếu method trả `CompletableFuture` cho MVC, framework có thể giữ logical async response mở đến khi future hoàn tất. Cả hai trường hợp đều không cần worker giữ raw request.

### 9.2. @Async self-invocation

Trong proxy mode mặc định, gọi `this.generate(input)` từ chính instance có thể bypass `@Async`: công việc sẽ chạy trực tiếp chứ không đi qua proxy. Cần gọi qua bean proxy ở tầng khác. Nhưng kể cả khi “trông như synchronous” trong test, **đừng** chuyển raw request sang async API chỉ vì đường gọi hiện tại chưa async; refactor sau có thể làm lỗi xuất hiện.

### 9.3. Executor bean nên do container quản lý

~~~java
@Configuration(proxyBeanMethods = false)
@EnableAsync
class ExecutorConfig {

    @Bean("reportExecutor")
    ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(64);
        pool.setThreadNamePrefix("report-");
        return pool;
    }
}
~~~

Đây là **snippet cấu hình Spring**, cần import từ `org.springframework.context.annotation`, `org.springframework.scheduling.annotation` và `org.springframework.scheduling.concurrent`. Spring context quản lý khởi tạo và shutdown bean. Queue phải có giới hạn phù hợp; tham số chỉ minh họa, không phải “64 là tối ưu”.

`ThreadPoolTaskExecutor` là **nơi task có thể nằm chờ**. Nếu task capture request, pool queue sẽ kéo dài reference lifetime. Dùng immutable snapshot giải quyết lớp vấn đề ownership; còn queue capacity, timeout, rejection và memory profile giải quyết giới hạn tài nguyên.

## 10. Spring Context Java 17 chạy được: chỉ truyền snapshot vào @Async

Demo tiếp theo **không cần servlet container**, nên không giả vờ rằng `@Async` đang quản lý Servlet request. Nó dùng Spring `AnnotationConfigApplicationContext`, `@EnableAsync` và `ThreadPoolTaskExecutor` để chứng minh cách truyền input đơn giản và tách biệt khỏi request object.

### 10.1. Cấu trúc Maven

~~~text
spring-request-snapshot/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── SnapshotSpringDemo.java
~~~

`pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-request-snapshot</artifactId>
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

`src/main/java/demo/SnapshotSpringDemo.java`:

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class SnapshotSpringDemo {

    public record JobInput(String actorId, String correlationId) { }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {
        @Bean("jobExecutor")
        ThreadPoolTaskExecutor jobExecutor() {
            ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
            e.setCorePoolSize(1);
            e.setMaxPoolSize(1);
            e.setQueueCapacity(8);
            e.setThreadNamePrefix("job-");
            return e;
        }

        @Bean
        Worker worker() {
            return new Worker();
        }
    }

    public static class Worker {
        @Async("jobExecutor")
        public CompletableFuture<String> process(JobInput input) {
            return CompletableFuture.completedFuture(
                    input.actorId() + "/" + input.correlationId());
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            Worker worker = context.getBean(Worker.class);
            JobInput snapshot = new JobInput("actor-7", "corr-9");
            String result = worker.process(snapshot)
                                  .get(5, TimeUnit.SECONDS);

            System.out.println("job = " + result);
        }
    }
}
~~~

Chạy trong thư mục có `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.SnapshotSpringDemo
~~~

**Stdout từ lớp demo khi chạy thành công:**

~~~text
job = actor-7/corr-9
~~~

Mã có đủ `main`, cấu hình bean, Spring async interceptor và đóng context. Không cần `HttpServletRequest` để thực thi worker; input là record gồm hai `String` bất biến. Trong ứng dụng HTTP thật, snapshot phải được tạo sau authentication/validation trong request boundary.

**Giới hạn của demo:** nó không chứng minh Servlet lifecycle vì không có web server. Ví dụ `RequestLifetimeDemo.java` phía trên mô phỏng sai khác thời gian sống; phần controller ở mục 8 minh họa tích hợp Servlet/Spring MVC thật.

## 11. MDC và correlation ID: truyền nhỏ, cleanup đúng

Một worker có thể cần correlation ID để log. Cách an toàn hơn là truyền **một chuỗi đã kiểm tra** vào task, rồi thiết lập MDC trong phạm vi task; dọn trong `finally` vì pooled thread còn dùng cho request/job tiếp theo.

Ví dụ với SLF4J `MDC` (dependency của logging framework, **snippet không standalone**):

~~~java
static Runnable withCorrelation(
        String correlationId, Runnable task) {

    return () -> {
        String previous = MDC.get("correlationId");
        try {
            if (correlationId == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", correlationId);
            }
            task.run();
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    };
}
~~~

Tại submission:

~~~java
String correlationId = validateCorrelationId(
        request.getHeader("X-Correlation-Id"));

executor.execute(withCorrelation(
        correlationId,
        () -> processWithId(correlationId)));
~~~

Đây là snippet với `validateCorrelationId`, `processWithId` và `executor` đã tồn tại. Snapshot được lấy trước khi schedule; wrapper **không cần** capture `request`. Ngoài ra, MDC có thể có nhiều key khác nhau; ví dụ chỉ quản lý một key để minh họa. Nếu application truyền nhiều key, cần snapshot nhỏ/được phép và restore từng key có trách nhiệm.

### TaskDecorator cũng cần capture đúng thời điểm

~~~java
executor.setTaskDecorator(delegate -> {
    String correlationId = MDC.get("correlationId");

    return () -> {
        String before = MDC.get("correlationId");
        try {
            if (correlationId == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", correlationId);
            }
            delegate.run();
        } finally {
            if (before == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", before);
            }
        }
    };
});
~~~

Đây là **snippet tích hợp**, giả định `executor` và `MDC` có dependency. `TaskDecorator` được áp vào callback thực thi, nhưng một task được `submit` có thể được bọc bằng `FutureTask`; error handling của decorator **không** phải nơi bảo đảm quan sát mọi exception của `submit`. Mục tiêu đoạn code ở đây chỉ là đưa **một correlation ID** xuyên thread và restore sạch sẽ, không truy cập request trong worker.

**Không** lưu `RequestContextHolder.currentRequestAttributes()` trong decorator rồi đính kèm cho mọi task để “dễ lấy header”. Việc đó có thể giữ request object graph ngoài lifecycle.

## 12. Spring Security: actor ID khác SecurityContext

Spring Security thường dùng `SecurityContextHolder` có context gắn với thread. Một worker tự tạo **không** mặc nhiên nhìn thấy authentication của servlet thread.

Nếu job chỉ cần định danh actor để audit và truy cập những resource có policy riêng, hãy trích xuất **actor ID đã xác thực** ở request boundary rồi cho phép worker kiểm tra quyền theo nghiệp vụ. Đừng chuyền bearer token hoặc principal object tùy ý vào queue dài hạn.

Nếu thật sự cần thực thi trong security context cụ thể, Spring Security cung cấp các wrapper như `DelegatingSecurityContextRunnable`, `DelegatingSecurityContextExecutor`, `DelegatingSecurityContextAsyncTaskExecutor`. Chúng thiết lập/clear security context đúng phạm vi callback theo contract, nhưng:

- Không biến `HttpServletRequest` thành object hợp lệ sau completion.
- Không miễn trừ chính sách token expiry/revocation và quyền thay đổi theo thời gian.
- Không nên snapshot/chia sẻ một `SecurityContext` mutable cho nhiều task rồi sửa nó từ nhiều thread.
- Không nên dùng context propagation như “copy toàn bộ request” cho background job lâu dài.
- Với job bền qua restart, xử lý security bằng internal service identity và authorization policy thích hợp, không dựa trên in-memory thread context của HTTP request cũ.

Các wrapper security là công cụ cho **task-scoped execution context**, không phải giấy phép bỏ qua request lifecycle.

## 13. File upload, request body và stream: trường hợp rất dễ lỗi

### 13.1. Không truyền ServletInputStream ra ngoài request

~~~java
InputStream stream = request.getInputStream();

executor.execute(() -> parseDocument(stream));
~~~

**Snippet không an toàn** nếu stream vẫn phụ thuộc Servlet request hoặc bị container/request processing đóng trước khi worker đọc. Stream cũng không nhất thiết thread-safe và một worker lỗi có thể không đóng tài nguyên đúng lúc.

### 13.2. MultipartFile cũng không mặc nhiên là dữ liệu bền

Trong Spring MVC, `MultipartFile` có thể tham chiếu đến dữ liệu tạm do multipart processing quản lý. Không lưu cả `MultipartFile` hoặc `getInputStream()` vào queue để đọc nhiều phút sau response, vì dữ liệu tạm có thể được cleanup theo request lifecycle.

Với file lớn cần xử lý nền, ví dụ thiết kế hợp lý:

~~~text
HTTP multipart request
      |
      +-- Xác thực/kiểm tra content type, kích thước, quota
      +-- Lưu stream vào durable object storage hoặc vùng staging
      +-- Commit metadata: fileId, ownerId, size, checksum
      +-- Đưa fileId + jobId vào queue (atomic/outbox nếu cần)
      |
      v
Response 202 Accepted (+ jobId)

Background worker:
      |
      +-- Load dữ liệu theo fileId từ storage
      +-- Kiểm tra quyền/quota và tính toàn vẹn
      +-- Xử lý, cập nhật trạng thái
      +-- Dọn dữ liệu staging theo retention policy
~~~

Không cần copy toàn bộ file vào `byte[]` trong RAM để gọi đó là snapshot. Đối với upload cần durability, truyền **reference tới file đã được lưu có hợp đồng retention** hiệu quả và an toàn hơn.

### 13.3. Ví dụ Spring MVC: staging rồi giao job

~~~java
@PostMapping(path = "/imports", consumes = "multipart/form-data")
public ResponseEntity<JobAccepted> upload(
        @RequestParam("file") MultipartFile file,
        Principal principal) throws IOException {

    String ownerId = principal.getName();
    String objectKey = storage.saveValidated(file.getInputStream(),
                                             file.getSize(), ownerId);
    String jobId = jobs.enqueueImport(ownerId, objectKey);

    return ResponseEntity.accepted()
            .body(new JobAccepted(jobId));
}
~~~

Đây là **snippet Spring MVC**: `storage` và `jobs` là các bean ứng dụng cần implement; `JobAccepted` là DTO; cần imports cho `MultipartFile`, `Principal`, `IOException`, `ResponseEntity`. Trong production, phải giới hạn size, xác thực MIME/format độc lập client, cleanup stream và xử lý atomicity giữa save/enqueue. **Bảo đảm `saveValidated` thực sự đã ghi dữ liệu vào storage ổn định trước khi nhận job**. Nếu thao tác thất bại, không trả 202 giả.

Nếu object storage và DB queue không cùng transaction, dùng job state/idempotency/outbox hoặc compensation/cleanup để tránh job trỏ tới file chưa tồn tại hoặc file mồ côi.

## 14. Timeout, cancellation và các việc tiếp tục sau HTTP response

Một task có thể vẫn chạy khi:

- Client đóng kết nối.
- Spring MVC async request timeout.
- Controller trả 202 và đã kết thúc response.
- Downstream chậm hơn deadline HTTP.
- Worker đã nằm trong queue lúc context shutdown.
- Frontend retry tạo ra logical request thứ hai.

Trong tất cả trường hợp, **request object không trở thành nguồn dữ liệu nền bền vững**. Cần thiết kế lifecycle của **job** riêng với lifecycle của **HTTP request**.

### Khi trả CompletableFuture cho Spring MVC

~~~java
@GetMapping("/reports/{id}")
public CompletableFuture<String> report(@PathVariable String id) {
    String reportId = validateId(id);
    return reportService.generateAsync(reportId);
}
~~~

Đây là **Spring MVC snippet**; cần service và validator. Framework có thể giữ async HTTP request tới khi future hoàn thành/timeout/được xử lý theo contract. Worker vẫn chỉ dùng `reportId` chứ không cần `HttpServletRequest`.

### Khi trả 202 rồi thực hiện job nền

Nếu hệ thống có durable job queue:

~~~text
POST /exports
    |
    +-- validate payload, identity
    +-- persist job(input snapshot)
    +-- return 202 + jobId
                 |
                 v
            background worker
                 |
                 +-- status=RUNNING/SUCCEEDED/FAILED
                 +-- GET /jobs/{jobId} để theo dõi
~~~

Ở đây 202 thường chỉ có nghĩa **yêu cầu đã được chấp nhận để xử lý**, không phải công việc hoàn tất. Nếu chỉ gọi `@Async void` rồi trả 202 mà không lưu job trạng thái/queue, application crash có thể làm mất việc.

### Khi cancel

Hủy `CompletableFuture` không có nghĩa underlying network/db action sẽ dừng ngay hoặc rollback side effect. Với task trong executor, cần chủ động gắn cancellation/deadline với job và tài nguyên đang dùng theo API tương ứng. Cleanup chạy ở `finally` của task hoặc lifecycle callback phù hợp, không cố “sửa response” bằng `HttpServletResponse` cũ.

## 15. Request-scoped bean: một góc dễ bỏ sót

~~~java
@Component
@RequestScope
public class RequestMetadata {
    private final String requestId;
    // ...
}
~~~

Đây là **snippet minh họa**, không độc lập. Một bean `@RequestScope` có vòng đời gắn với một HTTP request trong web-aware `ApplicationContext`. Khi code ở worker cố truy cập request-scoped proxy ngoài scope thích hợp, có thể gặp lỗi không có request scope active, ví dụ một `ScopeNotActiveException` hoặc exception gốc bên dưới tùy cấu hình.

Cũng không nên nhận toàn bộ request-scoped bean làm argument của một job chạy sau response. Bean đó có thể giữ request, body hoặc tài nguyên tạm qua các field.

**Nên:** lấy `requestMetadata.getRequestId()` khi request scope còn hợp lệ, copy kết quả `String` vào DTO, rồi truyền DTO cho worker. Nếu bean chứa object mutable, phải xác định ranh giới deep copy và validation.

## 16. Phân biệt dữ liệu, context và quyền truy cập

Ba thứ thường bị trộn lẫn:

1. **Dữ liệu nghiệp vụ:** document ID, order ID, amount, validated options. Cần để worker làm việc. Nên nằm trong immutable DTO hoặc durable job payload.
2. **Thông tin quan sát:** trace ID, correlation ID, locale code, operation ID. Có thể chuyển một tập nhỏ được phép qua thread hoặc task decorator; cleanup sau mỗi task.
3. **Quyền/ngữ cảnh bảo mật:** authentication token, session, security principal, tenant policy, request-specific authorities. Không nên copy mù vào job dài hạn; cần security contract riêng, hết hạn/quyền hiện hành và auditing.

“Snapshot tất cả headers để chắc” là phản ví dụ: sẽ copy cookie, bearer token, proxy headers chưa kiểm chứng và metadata không cần thiết. Một record nhỏ chỉ an toàn nếu **các giá trị trong nó đã được kiểm tra và hợp lệ với thời gian sống của job**.

## 17. Bài toán retry và request đã đóng

Một task đã tách khỏi request có thể bị retry sau vài phút. Nếu nó mang request cũ:

~~~java
scheduler.schedule(
        () -> callPartner(request.getHeader("Authorization")),
        5, TimeUnit.MINUTES);
~~~

Đây là **snippet phản ví dụ**. Sau 5 phút, request đã hết lifecycle; token có thể hết hạn; giữ header Authorization trong callback là retention của bí mật. Không sửa bằng cách bọc request vào `Supplier<HttpServletRequest>` — supplier vẫn có thể giữ tham chiếu hoặc trả context sai.

**Thiết kế đúng hơn**:

- Xác định **logical operation ID** và các scalar parameters được phép.
- Lưu job payload bền vững nếu việc cần tồn tại qua restart.
- Tại lúc thực thi/retry, dùng service credential/authorization thích hợp hoặc refresh token qua cơ chế được bảo mật, không lấy token từ request cũ.
- Dùng cùng idempotency key cho các attempts của một logical operation nếu downstream có side effect.
- Khi job hết TTL/permission, dừng và đánh dấu trạng thái rõ ràng.

### Khi cần hồi cứu HTTP metadata

Nếu cần audit request path, client ID hoặc idempotency key, chuẩn hóa và ghi log/event **trong request boundary**, không giữ raw `HttpServletRequest` để “sau này mở ra xem”. Audit cần retention/access controls chứ không dựa vào container object sống trong RAM.

## 18. Giảm nguy cơ memory retention

Một queue hữu hạn vẫn có thể giữ payload lớn nếu input quá nặng. Các biện pháp cần phối hợp:

1. **Small DTO:** chỉ các ID/scalar cần dùng, không giữ body/session/request.
2. **Bounded executor:** hạn chế worker/queue và có chính sách overload rõ.
3. **Timeout/deadline:** không cho future/task pending vô hạn.
4. **Job ownership:** không để static registry giữ Future hoàn thành mãi không cleanup.
5. **Explicit cleanup:** stream, temp file, storage lease, context restoration.
6. **Metric:** queue depth, waiting time, task age, payload size, heap old-generation, retained objects.
7. **Heap analysis:** dùng heap dump/profiler trong môi trường an toàn để tìm đường tham chiếu từ GC root tới `HttpServletRequest`/`ServletRequestAttributes` khi nghi ngờ.
8. **Data minimization:** không giữ bearer token/cookies/body ở background queues; áp retention/access controls cho job payload.

Không cần chứng minh một request đã được GC để khẳng định code đúng; quan trọng hơn là **không có đường tham chiếu sai cấu trúc**. `System.gc()` và `WeakReference.get()==null` không phải assertion đáng tin trong unit test vì GC không có thời hạn bắt buộc.

## 19. Kiểm thử và QA trong dự án

### 19.1. Test snapshot không phụ thuộc request object

JUnit 5 snippet:

~~~java
JobInput input = new JobInput(
        "job-1", "actor-2", "tenant-3", "corr-4", "doc-5");

assertEquals("tenant-3", input.tenantId());
assertEquals("doc-5", input.documentId());
~~~

Đây là **snippet**, cần record `JobInput` ở mục 6 và các imports JUnit 5. Mục đích là xác minh dữ liệu có kiểu, bất biến và thuộc tính được copy rõ ràng. Test thêm validation không cho phép giá trị rỗng, quá dài hoặc sai định dạng theo policy; code record mẫu chỉ kiểm tra null, chưa thay validator.

### 19.2. Test không truy cập request sau khi lifecycle hoàn tất

Ví dụ `RequestLifetimeDemo` ở mục 7 dùng fake request có trạng thái active và `CountDownLatch`. Trong unit test service, có thể dùng fake/spy request để ném lỗi nếu gọi sau khi method handler kết thúc; assert service chỉ nhận DTO. Không nên dùng `Thread.sleep(1000)` rồi đoán worker chắc chắn đã chạy sau response.

### 19.3. Test Spring MVC async thực sự

Dùng Spring MVC test hoặc integration test với container để xác minh:

- Controller tạo snapshot **trước** khi return.
- `@Async` được kích hoạt qua proxy (không self-invocation).
- Controller trả `CompletableFuture`/`DeferredResult` được framework quản lý; lỗi/timeout được map chính xác.
- Không có code bên worker gọi `RequestContextHolder.currentRequestAttributes()` hoặc bean `@RequestScope` sau khi response đã complete.
- Sau khi async request hoàn tất, không giữ references trong custom scheduler/registry.
- Với file upload, worker nhận storage key; multipart temp file chỉ được dùng trong request lifecycle đúng.
- Khi task bị reject hoặc context shutdown, không mất job status hoặc trả “success” giả.

### 19.4. Test chống lộ context giữa hai task

Sử dụng **một pooled worker** chạy lần lượt hai task với correlation ID khác nhau. Task thứ nhất gắn `traceId="A"` rồi cleanup; task thứ hai phải không thấy A nếu không được cấp context. Kiểm tra `finally` của decorator/MD C wrapper cả khi task ném exception và khi bị cancel. Không dùng thread inheritance để “vá” lỗi.

### 19.5. Test footprint/retained references

Có thể profile dưới load với hàng đợi có giới hạn, đo heap retained size và class histogram trước/sau một loạt request. Không dùng `WeakReference` + ép GC làm tiêu chí pass/fail; thay vào đó, enforce code review/type boundaries “job payload không chứa ServletRequest/ServletResponse/RequestAttributes/MultipartFile/InputStream/Session” và kiểm thử chức năng.

### 19.6. Test phân quyền và bảo mật dữ liệu

- Client giả mạo `X-Tenant-Id` phải không thể chọn tenant khác trong snapshot.
- Input có bearer token/cookie không được sao chép vào job payload/log/trace.
- Job chạy lâu hoặc retry phải dùng policy authorization phù hợp với thời điểm thực thi.
- Người dùng không được đọc kết quả job khác chỉ bằng đoán job ID.
- Storage key không lộ dữ liệu nếu bị lấy trực tiếp; dùng internal ACL hoặc signed URL ngắn hạn theo contract.

## 20. Những ngộ nhận phổ biến

**1. “Controller return nghĩa là Servlet request luôn hoàn tất ngay.”** Sai trong Servlet async được quản lý; có thể vẫn open đến future/async completion.

**2. “Nếu @Async được gọi, container tự giữ HttpServletRequest sống tới lúc task xong.”** Sai. `@Async` và Servlet async request là hai cơ chế khác nhau.

**3. “Giữ request chỉ tốn một pointer.”** Sai về retained object graph: một pointer có thể giữ nhiều attributes, wrappers hoặc dữ liệu.

**4. “Có reference thì request vẫn hợp lệ về mặt API.”** Sai. GC reachability và lifecycle validity là hai tính chất khác nhau.

**5. “Không thấy exception khi đọc request trên worker nghĩa là code an toàn.”** Sai. Có thể là race hoặc container vẫn chưa recycle ở lần test đó.

**6. “Copy ServletRequestAttributes sang thread là snapshot.”** Sai. Nó thường giữ request/response và các attributes gắn với lifecycle.

**7. “Record luôn deep immutable.”** Sai. Record với field là List mutable element hoặc `byte[]` vẫn có dữ liệu thay đổi được.

**8. “Truyền Principal hay Authentication nguyên bản chắc chắn an toàn cho job 1 giờ.”** Không. Cần cân nhắc credential, quyền thay đổi, token expiry, sensitivity và security contract.

**9. “RequestContextHolder tự hoạt động ở mọi @Async worker.”** Sai. ThreadLocal thường không tự di chuyển qua executor thread.

**10. “Bật setThreadContextInheritable(true) là giải pháp tốt cho pool.”** Sai. Spring cảnh báo truyền context vào pooled thread mới tạo có thể làm lộ context qua công việc khác.

**11. “TaskDecorator copy mọi context sẽ không gây retention.”** Sai nếu context chứa `RequestAttributes` hoặc token/objects nặng.

**12. “Giữ MultipartFile rồi xử lý sau response vẫn an toàn.”** Không mặc định. Multipart storage và stream có thể bị cleanup theo request.

**13. “Cứ copy body thành byte[] là xong.”** Không đủ. Nếu body lớn, queue chứa hàng nghìn bản sao có thể làm OOM; dùng staging/object storage.

**14. “Snapshot correlation ID nghĩa là đã xác thực user.”** Sai. Correlation ID chỉ phục vụ observability; auth/tenant phải từ nguồn tin cậy.

**15. “Thread pool chỉ giữ task đang chạy, không giữ queue.”** Sai. Pending Runnable/Callable vẫn giữ closure và dữ liệu đi kèm.

**16. “Request-scoped proxy dùng được ở mọi worker sau HTTP completion.”** Sai. Proxy không kéo dài request scope.

**17. “Return 202 đảm bảo job đã chạy thành công.”** Sai. 202 chỉ nêu yêu cầu được nhận để xử lý theo hợp đồng; cần durable job status nếu business yêu cầu.

**18. “Hủy CompletableFuture là đã hủy luôn I/O hay rollback side effect.”** Sai. Hủy future không thay thế cancellation của underlying resource và nghiệp vụ.

**19. “Dùng WeakReference test là đủ xác nhận không leak.”** Sai: GC nondeterministic; kiểm tra ownership và retained references cấu trúc đáng tin hơn.

**20. “Dọn MDC chỉ khi task thành công.”** Sai. `finally` là bắt buộc để không làm contamination khi worker được tái sử dụng.

## 21. Câu hỏi tự kiểm tra

**Câu 1:** `@Async public void export(HttpServletRequest request)` an toàn vì Spring đã làm bất đồng bộ?

**Đáp án:** không. `@Async` chỉ điều phối method. Request có thể đã hết lifecycle khi worker đọc. Hãy trích xuất `ExportInput` immutable trong controller và truyền object đó.

**Câu 2:** Spring MVC controller trả `CompletableFuture`. Request có chắc hết hiệu lực ngay khi method trả không?

**Đáp án:** không. Spring MVC có cơ chế Servlet async, logical request có thể vẫn còn đến async completion. Nhưng không vì vậy mà nên truyền raw request vào worker ngoài kiểm soát framework.

**Câu 3:** Task đã xong nhưng một static map vẫn giữ `FutureTask` tham chiếu request. Heap có thể còn giữ request không?

**Đáp án:** có thể, nếu FutureTask/callback vẫn có đường tham chiếu mạnh tới object liên quan. Tùy implementation và thời điểm cleanup, retention graph cần được kiểm tra; giải pháp là không capture request và có cleanup registry.

**Câu 4:** `record Input(byte[] raw)` là immutable hoàn toàn?

**Đáp án:** không. Record chỉ cố định reference `raw`, còn phần tử mảng có thể bị sửa. Cần defensive copy nếu thật sự cần dữ liệu và kiểm soát kích thước; tốt hơn dùng storage key cho payload lớn.

**Câu 5:** Có nên truyền cả `ServletRequestAttributes` sang thread khác để gọi `RequestContextHolder`?

**Đáp án:** không như giải pháp chung cho job chạy sau response. Attributes có thể giữ raw request và chỉ hợp lệ trong lifecycle request; chọn snapshot nhỏ hoặc context propagation được quản lý theo lifetime rõ ràng.

**Câu 6:** Multipart file 200 MiB được xử lý 10 phút sau response. Truyền `MultipartFile` vào queue có phù hợp không?

**Đáp án:** không nên. Lưu dữ liệu theo kiểm soát an toàn vào durable storage trong request boundary, chỉ enqueue storage key và job ID.

**Câu 7:** Vì sao không cần GC test để chứng minh input an toàn?

**Đáp án:** vấn đề là dependency/lifetime ownership: DTO không giữ request, worker không truy cập context sau completion, queue có giới hạn. GC timing không xác định, nên `System.gc`/`WeakReference` không bảo đảm test pass đáng tin.

**Câu 8:** Worker có phải sử dụng bearer token cũ của client để gọi downstream sau một giờ?

**Đáp án:** không mặc định. Phải thiết kế credential/delegation có expiry/revocation/authorization và storage bảo mật; tốt hơn là service identity cùng business actor ID được audit theo contract.

**Câu 9:** Một executor dùng `InheritableThreadLocal` để truyền tenant từ request thread vào pooled thread, có rủi ro gì?

**Đáp án:** pooled thread được tạo trong một request có thể giữ tenant/context cũ và phục vụ tác vụ khác sau đó; Spring cảnh báo chính trường hợp này. Dùng propagation theo task và cleanup chặt, không copy nguyên request.

**Câu 10:** Trả 202 ngay sau khi `@Async void` nhận task, tiến trình crash trước khi task chạy. Vì sao người dùng không thể biết trạng thái?

**Đáp án:** công việc chỉ tồn tại trong RAM/queue không bền nếu chưa có job store. Nếu tác vụ cần delivery guarantee, phải lưu intent/job status bền vững trước khi trả accepted.

## 22. Checklist code review/triển khai

- [ ] Không truyền `HttpServletRequest`, `HttpServletResponse`, `ServletRequestAttributes`, `HttpSession` hoặc request-scoped proxy vào background job.
- [ ] Không capture request qua lambda/anonymous class/`this`/callback, scheduler, static cache hoặc queue.
- [ ] Snapshot chỉ có field cần thiết, được validate/authorize, không chứa raw headers/cookies/bearer token.
- [ ] Với record/DTO có collection/mutable value, đã copy hoặc chọn immutable representation phù hợp.
- [ ] Không giữ `MultipartFile`/`ServletInputStream` sau lifecycle; file lớn có storage object key và retention/ACL phù hợp.
- [ ] Hiểu sự khác biệt giữa controller return, Servlet async completion và fire-and-forget `@Async`.
- [ ] Spring `@EnableAsync` và proxy được cấu hình đúng; không lạm dụng self-invocation.
- [ ] `ThreadPoolTaskExecutor` có queue/worker cap, timeout, rejection handling và shutdown.
- [ ] Context/trace propagation chỉ mang dữ liệu cần thiết, cleanup `ThreadLocal/MDC` trong `finally`.
- [ ] Không bật `setThreadContextInheritable(true)` cho pool tái sử dụng.
- [ ] Quyền actor/tenant được xác minh, không tin header chưa xác thực, job dài hạn có policy security riêng.
- [ ] Có deadline/cancellation, không dùng response/request object cũ để hoàn tất job đã tách.
- [ ] Với job lâu dài hoặc phải retry sau crash, lưu payload/intent/status bền vững cùng idempotency.
- [ ] Có metrics queue depth, task age, retained heap, lỗi async, trạng thái job.
- [ ] Test có lifecycle fake/latch, async MVC integration, cross-task MDC, file staging và giả mạo tenant.
- [ ] Không dùng `System.gc()`/`WeakReference` như điều kiện bắt buộc để coi mã đúng.

## 23. Kết luận

**Không giữ toàn bộ object request sau khi request hoàn tất** là nguyên tắc về **lifecycle ownership, tính đúng đắn đồng thời, bảo mật và memory retention**.

Trong request boundary, hãy xác thực, kiểm tra đầu vào, trích xuất **immutable snapshot tối thiểu**, rồi truyền snapshot đến executor hoặc persistent job queue. Worker phải dùng snapshot và các dependency độc lập với Servlet request; không đọc lại request attributes, raw headers, session, stream hay response sau lifecycle.

Spring MVC có Servlet async processing hợp lệ nên **controller return không luôn đồng nghĩa request đã kết thúc**. Dù vậy, `@Async` hay `CompletableFuture` tự nó không cho phép giữ request vô thời hạn. Với context propagation, chỉ chuyển giá trị cần thiết và cleanup theo task. Với job chạy lâu, truyền **job ID/storage key** và quản lý trạng thái bền vững thay vì giữ object container trong RAM.

## Tài liệu tham khảo

- Jakarta Servlet specification — **Lifetime of the Request Object** và asynchronous processing: https://jakarta.ee/specifications/servlet/6.1/jakarta-servlet-spec-6.1
- Spring MVC Reference — Asynchronous Requests: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Spring MVC Reference — Controller Return Values (CompletionStage/CompletableFuture): https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html
- Spring Framework Reference — Bean Scopes (`request` scope): https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- Spring Framework — `RequestContextHolder` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/web/context/request/RequestContextHolder.html
- Spring Framework — `RequestContextFilter` Javadoc (InheritableThreadLocal warning): https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/web/filter/RequestContextFilter.html
- Spring Framework — `ContextPropagatingTaskDecorator` (Spring 6.1+): https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html
- Spring Framework Reference — Task Execution / @Async: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Security Reference — Concurrency Support: https://docs.spring.io/spring-security/reference/servlet/integrations/concurrency.html
- Java SE 17 — `CompletableFuture`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html
- Spring Framework Reference — Multipart Form Data: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-context-009">Phân biệt ngữ cảnh bảo mật cần bộ hỗ trợ riêng với ThreadLocal tùy ý</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt ngữ cảnh bảo mật cần bộ hỗ trợ riêng với ThreadLocal tùy ý

Trong **Spring Framework - concurrency**, một ứng dụng có thể tiếp nhận hai HTTP request của hai người dùng khác nhau, sau đó gửi công việc sang cùng một **ThreadPoolTaskExecutor**. Nếu xử lý ngữ cảnh thực thi không đúng, worker có thể không biết người dùng hiện tại là ai, hoặc tệ hơn, nhìn thấy thông tin của request trước trên một thread được tái sử dụng.

Có hai loại dữ liệu thường bị đánh đồng:

1. **ThreadLocal tùy ý** của ứng dụng: chẳng hạn mã tương quan log, ngôn ngữ, requestId hoặc một giá trị tạm dùng để tiện truy cập trong cùng thread.
2. **Ngữ cảnh bảo mật** do **Spring Security** quản lý: **SecurityContextHolder**, **SecurityContext**, **Authentication**, principal và authorities. Chúng không chỉ là chuỗi để gắn log mà còn liên quan trực tiếp tới **xác thực, phân quyền và tính cô lập người dùng**.

Mặc dù SecurityContextHolder trong ứng dụng servlet thông thường mặc định dùng cơ chế gần với **ThreadLocal**, **không nên truyền ngữ cảnh bảo mật qua thread pool bằng cách tự sao chép một ThreadLocal tùy tiện**. Spring Security cung cấp những bộ hỗ trợ chuyên biệt, như **DelegatingSecurityContextRunnable**, **DelegatingSecurityContextCallable**, **DelegatingSecurityContextExecutor**, **DelegatingSecurityContextExecutorService** và **DelegatingSecurityContextAsyncTaskExecutor**, để thiết lập/ngừng sử dụng ngữ cảnh đúng ranh giới tác vụ.

**Kết luận cần nhớ ngay:** ThreadLocal là **cơ chế lưu giá trị theo thread**; SecurityContext là **một phần hợp đồng bảo mật**; việc truyền SecurityContext cần chính sách identity, quyền, vòng đời và cleanup. Không phải cứ copy một tham chiếu ThreadLocal là đã có context propagation an toàn.

Bài học thuộc nhóm **Ngữ cảnh thực thi** và hướng tới Java 17+, ExecutorService, Spring **@Async** và Spring Security trong ứng dụng server có nhiều request đồng thời.

## 1. Vì sao việc chuyển sang worker làm mất context?

Một HTTP servlet request thường được xử lý trên một thread được framework/container quản lý:

~~~text
HTTP request A -- Security filters --> request-thread-7
                                    |
                                    +-- SecurityContextHolder
                                    |      Authentication: alice
                                    |
                                    +-- submit(task) ---> worker-2
                                                          |
                                                          +-- ThreadLocal riêng
                                                              thường không có Alice
~~~

Trong cấu hình mặc định **MODE_THREADLOCAL**, SecurityContextHolder gắn thông tin với **thread đang chạy**, không gắn với một đối tượng HTTP request có khả năng tự đi qua mọi executor.

Khi task được gửi bằng **ExecutorService.submit**, **CompletableFuture.supplyAsync**, **ThreadPoolTaskExecutor.execute** hay lời gọi **@Async**, worker có thể là một thread khác. Trên worker:

- **SecurityContextHolder.getContext().getAuthentication()** có thể là null.
- Một ThreadLocal ứng dụng tự tạo cũng thường không có giá trị của request thread.
- Một worker bị sử dụng không đúng có thể giữ **giá trị cũ từ lần thực thi trước**.
- Transaction, MDC, RequestContextHolder và tracing không nhất thiết có cùng hành vi truyền/cleanup như SecurityContext.

**Khác biệt giữa “không có context” và “sai context” cực kỳ quan trọng.** Không có Authentication có thể khiến tác vụ bị từ chối truy cập như mong đợi; sai Authentication có thể khiến tác vụ bị thực thi dưới danh tính hoặc quyền của người khác, nghiêm trọng hơn nhiều.

### Quy tắc Spring Security

Theo tài liệu Spring Security, đối với servlet security thông thường, **SecurityContextHolder** lưu thông tin xác thực của thread hiện hành và cơ chế filter quản lý việc dọn nó sau request. Cấu trúc cơ bản là:

~~~text
SecurityContextHolder (holder + strategy)
    |
    +-- SecurityContext (trạng thái bảo mật hiện tại)
             |
             +-- Authentication
                     |
                     +-- principal
                     +-- credentials (tùy cơ chế)
                     +-- authorities / roles / scopes
~~~

Một **Authentication** không chỉ là tên đăng nhập. Nó có thể chứa authorities, thông tin về phương thức xác thực, principal object và những chi tiết liên quan quyền. Vì vậy, một custom ThreadLocal<String> lưu mỗi username **không tương đương** với việc cung cấp SecurityContext hợp lệ.

## 2. ThreadLocal bình thường hoạt động ra sao?

**ThreadLocal<T>** cho phép mỗi **Thread** giữ một giá trị T riêng:

~~~java
private static final ThreadLocal<String> REQUEST_ID =
        new ThreadLocal<>();
~~~

Đây là **khai báo trường trong class Java**, không phải chương trình độc lập.

Nếu main/request thread set REQUEST_ID="A", worker từ pool **không tự đọc ra "A"** chỉ vì Runnable/lambda do main gửi có thể tham chiếu trường static REQUEST_ID. Trường ThreadLocal là một điểm truy cập; **giá trị được tra theo Thread.currentThread()**, không theo caller đã submit.

~~~text
ThreadLocal REQUEST_ID
    |
    +-- request-thread: "A"
    +-- worker-1:      null
    +-- worker-2:      "stale-B"  (nếu lần trước không remove!)
~~~

Lưu ý rằng ThreadLocal **không tự đồng bộ dữ liệu chung**. Nó phân tách slot theo thread, nhưng giá trị T vẫn có thể là **một object mutable được chia sẻ** bởi các thread nếu bạn đưa cùng tham chiếu vào nhiều slot.

### Thread pool khiến lỗi nặng hơn

ThreadPoolTaskExecutor và ExecutorService thường **tái sử dụng worker**. Nếu một task gọi **ThreadLocal.set(value)** rồi quên **remove()** trong finally:

- Worker có thể được dùng cho task sau.
- Task sau nhìn thấy **stale value** dù nó xuất phát từ request khác.
- Dữ liệu nhạy cảm có thể còn bám vào thread sống lâu.
- Khi worker không có việc và chạy lâu, stale references có thể giữ các object lớn trong bộ nhớ.

Không chỉ có memory leak; với dữ liệu định danh/ủy quyền, đây còn là **cross-request context leak**.

## 3. Java 17 chạy độc lập: không tự truyền context và có thể rò rỉ

Chương trình dưới đây hoàn toàn **thuần Java 17**, không cần Spring. Nó chứng minh ba sự kiện trong cùng một worker thread:

1. ThreadLocal của caller **không tự đến worker**.
2. Một Executor wrapper có thể **chụp giá trị tại submit, set trong worker và remove trong finally** khi dùng đúng.
3. Nếu một task tự set ThreadLocal rồi **không dọn**, worker có thể để lại giá trị sai cho task sau.

Lưu thành **ThreadLocalBoundaryDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadLocalBoundaryDemo {
    private static final ThreadLocal<String> CONTEXT =
            new ThreadLocal<>();

    private static String current() {
        String value = CONTEXT.get();
        return value == null ? "<empty>" : value;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();

        // Wrapper ví dụ cho một ThreadLocal phi bảo mật đơn giản.
        Executor contextAware = command -> {
            String captured = CONTEXT.get();  // Đọc tại submit.
            worker.execute(() -> {
                try {
                    if (captured == null) {
                        CONTEXT.remove();
                    } else {
                        CONTEXT.set(captured);
                    }
                    command.run();
                } finally {
                    CONTEXT.remove();
                }
            });
        };

        try {
            CONTEXT.set("alice");

            String rawAlice = worker.submit(
                    ThreadLocalBoundaryDemo::current)
                    .get(5, TimeUnit.SECONDS);

            String wrappedAlice = CompletableFuture.supplyAsync(
                    ThreadLocalBoundaryDemo::current, contextAware)
                    .get(5, TimeUnit.SECONDS);

            CONTEXT.set("bob");

            String wrappedBob = CompletableFuture.supplyAsync(
                    ThreadLocalBoundaryDemo::current, contextAware)
                    .get(5, TimeUnit.SECONDS);

            String rawAfter = worker.submit(
                    ThreadLocalBoundaryDemo::current)
                    .get(5, TimeUnit.SECONDS);

            // Minh họa một task sai quên cleanup trên worker.
            worker.submit(() -> CONTEXT.set("stale-alice"))
                    .get(5, TimeUnit.SECONDS);

            String leaked = worker.submit(
                    ThreadLocalBoundaryDemo::current)
                    .get(5, TimeUnit.SECONDS);

            // Dọn sự cố cố ý tạo ra trong ví dụ.
            worker.submit(CONTEXT::remove)
                    .get(5, TimeUnit.SECONDS);

            System.out.println("Raw worker thấy: " + rawAlice);
            System.out.println("Wrapped Alice: " + wrappedAlice);
            System.out.println("Wrapped Bob: " + wrappedBob);
            System.out.println("Raw sau wrapper: " + rawAfter);
            System.out.println("Giá trị bị rò: " + leaked);
        } finally {
            CONTEXT.remove();
            worker.shutdown();
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ThreadLocalBoundaryDemo.java
java ThreadLocalBoundaryDemo
~~~

**Stdout của ví dụ:**

~~~text
Raw worker thấy: <empty>
Wrapped Alice: alice
Wrapped Bob: bob
Raw sau wrapper: <empty>
Giá trị bị rò: stale-alice
~~~

Giải thích:

- Worker đầu tiên được tạo bởi **newSingleThreadExecutor** và không có giá trị CONTEXT của main, nên rawAlice = empty.
- **contextAware** chụp alice/bob ngay lúc **execute** được gọi, sau đó đặt đúng giá trị trên worker.
- **finally CONTEXT.remove()** bảo đảm worker không giữ lại thông tin Alice/Bob sau mỗi task thông qua wrapper.
- Một task **không đi qua wrapper** cố ý set stale-alice và quên remove; task raw kế tiếp sử dụng cùng worker nên đọc được stale-alice.
- Dòng dọn cuối được gửi như một task vào cùng worker để loại giá trị cũ; pool được shutdown ở finally.
- **get(timeout)** đợi từng bước hoàn tất, không dựa vào Thread.sleep hay đoán lịch scheduler.

**Quan trọng:** wrapper này chỉ nhằm giảng giải ThreadLocal **phi bảo mật**, không phải bộ truyền SecurityContext nên copy vào production. Trong thư viện/phân hệ có callback lồng nhau chạy đồng bộ cùng thread, còn cần cân nhắc **lưu và phục hồi giá trị context trước đó** thay vì chỉ remove. Với thông tin bảo mật, dùng cơ chế Spring Security chuyên dụng.

## 4. Tại sao SecurityContextHolder không thể thay bằng ThreadLocal<String> userId?

Một custom value như REQUEST_USER.set("alice") có thể hữu ích cho việc tạo **label log** nếu dữ liệu không nhạy cảm và có cleanup. Nhưng nó **không tự trở thành Authentication được Spring Security tin cậy**.

Phân biệt:

| Thành phần | Mục đích | Ai quản lý hợp đồng? |
| --- | --- | --- |
| **ThreadLocal<String> requestId** | Metadata tương quan log | Ứng dụng, logging framework hoặc context propagation |
| **MDC** | Log diagnostic context | Logging framework; thường cần TaskDecorator/context propagation |
| **SecurityContextHolder** | Truy cập SecurityContext của thread hiện hành | Spring Security và SecurityContextHolderStrategy |
| **SecurityContext** | Chứa Authentication | Spring Security; gắn với lifecycle xác thực/ủy quyền |
| **Authentication** | Principal, trạng thái authenticated, authorities và chi tiết khác | Spring Security và authentication providers |
| **AuthorizationManager / method security** | Ra quyết định truy cập tài nguyên/thao tác | Spring Security theo policy ứng dụng |
| **RequestContextHolder** | Request attributes | Spring Web, không phải kho bảo mật |
| **Reactor Context** | Context đi theo reactive subscriber chain | Reactor; dùng **ReactiveSecurityContextHolder** cho reactive security |

Sai lầm nguy hiểm là tạo một **ThreadLocal<String> CURRENT_USER** rồi truyền nó sang worker và tin rằng **@PreAuthorize**, **hasRole** hoặc security authorization sẽ tự nhìn thấy user đó. Spring Security thường đọc **SecurityContextHolder / Authentication** theo integration thực tế, không đọc biến tự tạo.

Ở chiều ngược lại, gắn một Authentication vào SecurityContextHolder **không tự bảo đảm người dùng còn quyền tại thời điểm một tác vụ nền chạy nhiều giờ sau**. Context propagation là kỹ thuật đưa một ngữ cảnh vào đúng thread; **quyết định authorization hợp lệ** là vấn đề nghiệp vụ và chính sách phải xét tại điểm sử dụng.

## 5. Spring Security hỗ trợ cơ chế chuyên biệt nào?

Các wrapper chính thức hoạt động theo một ý tưởng chung:

~~~text
caller thread (SecurityContextHolder)
     |
     | submit Runnable / Callable
     v
DelegatingSecurityContext* wrapper
     |
     | chọn SecurityContext theo constructor và thời điểm submit
     v
underlying executor
     |
     v
worker:
     set SecurityContextHolder -> delegate.run()/call()
                                   |
                                   v
                           finally clearContext()
~~~

Spring Security cung cấp:

| Loại lời gọi / executor | Công cụ |
| --- | --- |
| Chỉ một Runnable cụ thể | **DelegatingSecurityContextRunnable** |
| Một Callable<T> cụ thể | **DelegatingSecurityContextCallable<T>** |
| Executor với **execute(Runnable)** | **DelegatingSecurityContextExecutor** |
| ExecutorService có **submit/execute** | **DelegatingSecurityContextExecutorService** |
| Spring TaskExecutor | **DelegatingSecurityContextTaskExecutor** |
| Spring AsyncTaskExecutor và **@Async** | **DelegatingSecurityContextAsyncTaskExecutor** |
| Scheduled executor, scheduler | Các biến thể **DelegatingSecurityContextScheduledExecutorService / TaskScheduler** tương ứng |

**Các wrapper chuyên dụng** thực hiện việc chuyển ngữ cảnh ở ranh giới task và **dọn context trong finally**. Chúng không biến pool thành pool mới không giới hạn, không bảo đảm task được chấp nhận, và không tự mang mọi ThreadLocal ứng dụng khác đi theo.

### Một SecurityContext cố định hay context của từng lần submit?

Có hai dạng constructor đáng phân biệt:

**Dùng context cố định:**

~~~java
SecurityContext serviceContext = /* context đã tạo hợp lệ */;
Executor secured = new DelegatingSecurityContextExecutor(
        underlyingExecutor,
        serviceContext);
~~~

Đây là **đoạn minh họa**. Mọi task đi qua wrapper này nhận **cùng context** được cung cấp, bất kể caller hiện tại. Chế độ này chỉ phù hợp khi **có chủ đích sử dụng một identity cố định**, ví dụ một công việc nội bộ đã được phê duyệt. Nó **không thích hợp cho một shared request executor được tạo bằng context của user đầu tiên**.

**Dùng context hiện hành tại submit:**

~~~java
Executor secured = new DelegatingSecurityContextExecutor(
        underlyingExecutor);
~~~

Đây cũng là **đoạn minh họa**. Dạng constructor không nhận SecurityContext làm wrapper chọn **context từ SecurityContextHolder khi task được gửi vào wrapper**. Nó hữu ích cho nhiều request của người dùng khác nhau: mỗi lần submit dùng context đang hiện diện tại ranh giới gửi việc.

**Cảnh báo quan trọng:** các wrapper sử dụng tham chiếu **SecurityContext**, không nên coi đây là thao tác deep-copy toàn bộ Authentication/principal/authorities. Nếu code khác tiếp tục **mutate cùng SecurityContext object**, bản “capture” tham chiếu vẫn có thể quan sát thay đổi. Tránh sửa chung một SecurityContext giữa các thread; nếu thực sự cần snapshot bất biến, xác định chính xác field và nghiệp vụ cần thiết. Spring Security cũng khuyến nghị tạo một SecurityContext mới thay vì sửa Authentication ngay trên object context có thể đang được chia sẻ.

## 6. Ví dụ chạy được với Spring Security: đúng user trên cùng một worker

Ví dụ này dùng **Spring Security Core** nhưng **không cần web server, filter chain hoặc Spring Boot**. Nó chứng minh một ThreadPoolExecutor một worker lần lượt phục vụ request giả lập của **alice** và **bob**, wrapper lấy đúng SecurityContext ở mỗi lần submit, rồi dọn sạch context trên worker.

Cấu trúc:

~~~text
security-context-demo/
├── pom.xml
└── src/main/java/example/SecurityContextDelegationDemo.java
~~~

**pom.xml** (Java 17, Spring Security Core 6.5.11):

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>security-context-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-core</artifactId>
            <version>6.5.11</version>
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
                    <mainClass>example.SecurityContextDelegationDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/example/SecurityContextDelegationDemo.java**:

~~~java
package example;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityContextDelegationDemo {
    private static void signInForDemo(String name) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        name,
                        "not-a-real-password",
                        AuthorityUtils.createAuthorityList("ROLE_USER"));

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private static String currentPrincipal() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return auth == null ? "<none>" : auth.getName();
    }

    public static void main(String[] args) throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        DelegatingSecurityContextExecutorService secured =
                new DelegatingSecurityContextExecutorService(raw);

        try {
            signInForDemo("alice");

            String rawAlice = raw.submit(
                    SecurityContextDelegationDemo::currentPrincipal)
                    .get(5, TimeUnit.SECONDS);

            String securedAlice = secured.submit(
                    SecurityContextDelegationDemo::currentPrincipal)
                    .get(5, TimeUnit.SECONDS);

            signInForDemo("bob");

            String securedBob = secured.submit(
                    SecurityContextDelegationDemo::currentPrincipal)
                    .get(5, TimeUnit.SECONDS);

            String rawAfter = raw.submit(
                    SecurityContextDelegationDemo::currentPrincipal)
                    .get(5, TimeUnit.SECONDS);

            System.out.println("Raw Alice: " + rawAlice);
            System.out.println("Wrapped Alice: " + securedAlice);
            System.out.println("Wrapped Bob: " + securedBob);
            System.out.println("Raw sau wrappers: " + rawAfter);
        } finally {
            SecurityContextHolder.clearContext();
            secured.shutdown();
            if (!secured.awaitTermination(5, TimeUnit.SECONDS)) {
                secured.shutdownNow();
            }
        }
    }
}
~~~

Chạy từ thư mục có **pom.xml**:

~~~bash
mvn -q compile exec:java
~~~

**Stdout của ứng dụng**, không tính thông tin Maven/logging:

~~~text
Raw Alice: <none>
Wrapped Alice: alice
Wrapped Bob: bob
Raw sau wrappers: <none>
~~~

**Tại sao ví dụ đúng?**

1. **signInForDemo** tạo SecurityContext mới, rồi đặt Authentication giả lập cho thread đang submit. Đây **không phải quy trình đăng nhập production**; chỉ dùng để tạo fixture.
2. **raw.submit** đưa task vào worker chưa có SecurityContextHolder được thiết lập, vì context của main không tự truyền qua thread pool.
3. **secured.submit** dùng DelegatingSecurityContextExecutorService để bọc Callable. Wrapper lấy SecurityContext tương ứng ở **thời điểm submit** của Alice.
4. Khi worker thực thi, wrapper đặt context của Alice và gọi Callable; Callable đọc Authentication.getName() đúng là alice.
5. Caller thay context của mình bằng một **SecurityContext object mới** cho Bob. Wrapper của lần submit tiếp theo dùng Bob, mặc dù executor vẫn tái sử dụng cùng một worker.
6. Wrapper dọn SecurityContextHolder sau mỗi lần chạy. Task cuối chạy **raw** nên không còn thấy Alice hay Bob trên worker.
7. **get(timeout)** bảo đảm từng bước hoàn tất trước khi in. Kết quả không phụ thuộc worker name hay may rủi scheduler.

**Phạm vi:** đây là chương trình minh họa library integration, không khởi tạo Spring web security, không xác thực mật khẩu thật, không cấp quyền thực tế. Cấu hình Maven dùng một phiên bản phát hành Spring Security đã được xác nhận có artifact; trong dự án production, chọn bản patch được duy trì và quản lý dependency qua Spring Boot BOM thích hợp.

### Tại sao không tự viết lời gọi setContext mà bỏ finally?

Nếu một task tự làm:

~~~java
SecurityContextHolder.setContext(requestContext);
doWork();
// Quên SecurityContextHolder.clearContext().
~~~

Đây là **đoạn minh họa**. Worker có thể tiếp tục giữ Authentication cũ khi task kế tiếp được gán vào cùng thread. Hãy ưu tiên wrapper Spring Security đã kiểm soát lifecycle, thay vì copy-paste đoạn setContext/clearContext thủ công ở từng service.

## 7. MODE_INHERITABLETHREADLOCAL không giải quyết được thread pool

Spring Security cho phép cấu hình các chiến lược lưu SecurityContext, bao gồm:

- **MODE_THREADLOCAL**: mặc định thông thường trong servlet application; context thuộc thread hiện tại.
- **MODE_INHERITABLETHREADLOCAL**: thread con mới có thể thừa hưởng giá trị khi **thread được tạo**.
- **MODE_GLOBAL**: một context dùng toàn JVM, phù hợp một số loại ứng dụng đơn người dùng đặc thù, **không phải cấu hình thích hợp cho web server đa người dùng**.

Thay đổi chiến lược của **SecurityContextHolder** là thao tác mang tính **toàn cục**, không nên làm tùy tiện trong khi ứng dụng đang có request in-flight. Đặc biệt, MODE_INHERITABLETHREADLOCAL **không truyền đúng context theo từng lần submit vào một worker pool tái sử dụng**.

Ví dụ thuần Java minh họa sai lầm này, lưu thành **InheritablePoolTrap.java**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class InheritablePoolTrap {
    private static final InheritableThreadLocal<String> USER =
            new InheritableThreadLocal<>();

    private static String current() {
        String value = USER.get();
        return value == null ? "<empty>" : value;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            USER.set("alice");
            String first = pool.submit(
                    InheritablePoolTrap::current)
                    .get(5, TimeUnit.SECONDS);

            // Thay caller nhưng pool vẫn tái sử dụng thread cũ.
            USER.set("bob");
            String second = pool.submit(
                    InheritablePoolTrap::current)
                    .get(5, TimeUnit.SECONDS);

            System.out.println("Task đầu: " + first);
            System.out.println("Task sau: " + second);
        } finally {
            USER.remove();
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
javac -encoding UTF-8 InheritablePoolTrap.java
java InheritablePoolTrap
~~~

Stdout với executor một worker được tạo khi submit đầu tiên:

~~~text
Task đầu: alice
Task sau: alice
~~~

**Giải thích:** InheritableThreadLocal sao chép/thừa hưởng giá trị lúc **worker thread mới được tạo**. Worker không phải được tạo lại mỗi lần submit. Khi Bob gửi task, worker cũ vẫn giữ giá trị đã thừa hưởng từ Alice.

Nếu worker của pool được tạo **trước khi** caller set Alice, giá trị có thể là null; nếu worker được tạo bởi một thread khác, giá trị cũng có thể khác. Vì vậy đây **không phải cơ chế bảo đảm per-request propagation**. Ví dụ làm rõ một tình huống có kiểm soát chứ không tuyên bố mọi pool luôn trả Alice.

**Không chuyển SecurityContextHolder sang MODE_INHERITABLETHREADLOCAL trên web server để “sửa nhanh @Async”.** Nó còn có thể gây rò rỉ ngữ cảnh vào thread con không mong muốn hoặc chia sẻ tham chiếu SecurityContext có thể bị thay đổi.

## 8. Tích hợp đúng vào Spring @Async với AsyncTaskExecutor

Khi một method được đánh dấu **@Async**, Spring AOP thường chuyển lời gọi qua một **TaskExecutor**. Nếu method body cần đọc Authentication, có thể bọc một ThreadPoolTaskExecutor bằng **DelegatingSecurityContextAsyncTaskExecutor**.

Cấu hình dưới đây là **hai tệp minh họa trong ứng dụng đã tích hợp Spring Framework 6.x và Spring Security 6.x**; để biên dịch cần dependencies Spring Context và Security Core thích hợp, không phải javac thuần.

**SecureAsyncConfig.java**:

~~~java
package example.async;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Configuration
@EnableAsync
public class SecureAsyncConfig {
    @Bean("rawAsyncExecutor")
    public ThreadPoolTaskExecutor rawAsyncExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("secure-work-");
        return executor;
    }

    @Bean("securityAwareExecutor")
    public DelegatingSecurityContextAsyncTaskExecutor
            securityAwareExecutor(
                @Qualifier("rawAsyncExecutor")
                ThreadPoolTaskExecutor delegate) {

        // Không truyền context cố định ở lúc tạo bean.
        return new DelegatingSecurityContextAsyncTaskExecutor(
                delegate);
    }
}
~~~

**SecureReportService.java**:

~~~java
package example.async;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SecureReportService {
    @Async("securityAwareExecutor")
    public CompletableFuture<String> currentUserReport() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Chưa xác thực");
        }

        return CompletableFuture.completedFuture(
                "report-for:" + auth.getName());
    }
}
~~~

Khi một bean/controller khác được bảo vệ bởi Spring Security gọi **secureReportService.currentUserReport()** qua Spring proxy:

1. Thread request đang có SecurityContextHolder do cơ chế security chính thức quản lý.
2. **@Async("securityAwareExecutor")** chọn wrapper Security-aware thay vì rawAsyncExecutor.
3. Wrapper **chụp ngữ cảnh của lời submit hiện tại**, đưa task tới rawAsyncExecutor rồi thiết lập context cho worker trước khi body method chạy.
4. Body có thể đọc Authentication tương ứng; khi task kết thúc, wrapper dọn SecurityContextHolder trên worker.
5. Return type **CompletableFuture<String>** giúp caller quan sát outcome, nhưng **không thay thế authorization tại điểm sử dụng dữ liệu**.

**Điều kiện bắt buộc:** bean do Spring quản lý, @EnableAsync được bật, method async là điểm vào thích hợp và lời gọi **đi qua Spring proxy**. Tự gọi this.currentUserReport() trong cùng service thường bỏ qua interception ở chế độ proxy.

**Lưu ý:** method này chỉ minh họa đọc Authentication và định danh. Trong sản phẩm thật, một lần đọc auth.getName() **không thay thế việc kiểm tra quyền đọc bản báo cáo**. Nếu task chạy trễ và quyền người dùng thay đổi, phải có chính sách ủy quyền lại thích hợp.

### Tại sao không đặt context của Alice trong @Bean?

Sai lầm đáng tránh:

~~~java
SecurityContext ctx = SecurityContextHolder.getContext();
return new DelegatingSecurityContextAsyncTaskExecutor(
        delegate, ctx);
~~~

Đây là **đoạn minh họa trong phương thức cấu hình bean**. Nếu vô tình cố định context lúc khởi tạo bean toàn cục, mọi task sau đó có thể dùng một context rỗng hoặc context của một người không liên quan. Constructor có đối số SecurityContext chỉ nên dùng khi **cố ý muốn mọi tác vụ của wrapper dùng cùng identity đã xác lập**. Đối với executor xử lý nhiều HTTP users, constructor không truyền context thường phù hợp hơn.

## 9. @Async và CompletableFuture không tự động truyền context ở mọi stage

Xét service:

~~~java
@Async("securityAwareExecutor")
public CompletableFuture<String> load() {
    return CompletableFuture.completedFuture("data");
}
~~~

Nếu gọi qua proxy, **body của load** có thể chạy trong worker đã được wrapper truyền SecurityContext.

Tuy nhiên:

~~~java
CompletableFuture<String> result = service.load()
        .thenApplyAsync(value -> {
            // Không được tự giả định SecurityContextHolder có ở đây.
            return enrich(value);
        });
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập. **thenApplyAsync** không truyền executor thường dùng default asynchronous execution facility của CompletableFuture (thường là ForkJoinPool.commonPool), nên callback có thể **không có SecurityContext** của request.

Ngay cả khi truyền **securityAwareExecutor** cho **thenApplyAsync**, cần hiểu thời điểm capture:

- Một callback được scheduling sau khi upstream hoàn thành có thể được gửi từ **thread làm upstream complete**, không phải từ thread HTTP request ban đầu.
- Wrapper với constructor không nhận SecurityContext lấy context **tại lúc callback được đưa vào Executor**, không nhất thiết là context gốc từ HTTP.
- Do đó, **từng ranh giới async phải được thiết kế và test riêng**, nhất là khi task pipeline có nhiều executor hoặc callback chạy sau khi request kết thúc.

Nếu toàn pipeline cần dùng một context **được xác định rõ cho một job duy nhất**, có thể tạo **wrapper theo từng job/request** với context cố định có kiểm soát, thay vì dùng shared singleton executor giữ một user:

~~~java
SecurityContext taskContext =
        SecurityContextHolder.getContext();

Executor perJob = new DelegatingSecurityContextExecutor(
        rawExecutor,
        taskContext);

CompletableFuture<String> future =
        CompletableFuture.supplyAsync(
                () -> loadForAuthorizedUser(), perJob)
        .thenApplyAsync(
                value -> transformForAuthorizedUser(value), perJob);
~~~

Đây là **đoạn code minh họa** trong nơi có rawExecutor, loadForAuthorizedUser và transformForAuthorizedUser. Nó nhấn mạnh rằng **perJob wrapper** giữ cùng SecurityContext được chỉ định cho hai lần schedule. Nếu muốn một snapshot bất biến, code phải đảm bảo SecurityContext/Authentication được sở hữu và không bị mutate đồng thời; constructor **không deep-copy** context. Khi job kéo dài, quyền và trạng thái bảo mật có thể phải được kiểm tra lại ở thời điểm thực hiện.

**Không đưa một perJob wrapper chứa user-specific context vào một @Bean singleton dùng cho mọi request khác.** Đây là lỗi về scope/ownership có thể khiến cả ứng dụng chạy dưới identity sai.

## 10. SecurityContext khác ngữ cảnh logging/tracing

Trong ứng dụng Spring, một TaskDecorator thường được dùng để mang theo **MDC** hoặc một số metadata quan sát:

~~~java
import org.springframework.core.task.TaskDecorator;

public final class LoggingTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        String traceId = TraceIdHolder.get();  // Ở lúc submit.

        return () -> {
            try {
                TraceIdHolder.set(traceId);
                task.run();
            } finally {
                TraceIdHolder.remove();
            }
        };
    }
}
~~~

Đây là **đoạn minh họa không độc lập**, vì TraceIdHolder là abstraction giả định của ứng dụng. Nó không nên được sao chép nguyên vào production nếu TaskDecorator cần xử lý nested scopes, rollback trạng thái cũ hoặc tracing framework có contract khác.

**TaskDecorator** là hook chuẩn trên nhiều Spring TaskExecutor, hữu ích cho những phần cần thiết lập/cleanup context chung. Nhưng:

- **MDC không phải Authentication**; traceId có trong log không chứng minh caller đã được xác thực.
- **Truyền log context** không tự khiến @PreAuthorize hoặc quyền thao tác chạy dưới đúng user.
- **Không nên tự viết TaskDecorator copy và set một SecurityContextHolder tùy tiện** nếu thư viện đã cung cấp wrapper chuyên dụng. Các implementation security chính thức hiểu holder strategy và thiết kế lifecycle của chúng.
- Nếu kết hợp nhiều decorator/wrapper, phải kiểm tra **thứ tự thiết lập và cleanup**, trường hợp lỗi/reject, và cả việc stage chạy lại trên worker tái sử dụng.

**Micrometer Context Propagation / ContextSnapshot** có thể giúp chuyển một số context đã đăng ký accessors phù hợp giữa thread, nhưng **không được mặc nhiên coi SecurityContext được truyền tự động cho mọi library hoặc reactive stage**. Cần chọn đúng integration, cấu hình accessor và kiểm thử bảo mật trên đường thực thi thực tế.

## 11. Authentication và SecurityContext có thể mutable: cẩn thận chia sẻ tham chiếu

Một điểm dễ bỏ sót: **SecurityContext là object**. Khi một wrapper “capture SecurityContext”, trong nhiều trường hợp nó giữ **tham chiếu tới object** đó, không serialize và clone toàn bộ Authentication.

Ví dụ nguy hiểm:

~~~java
SecurityContext context = SecurityContextHolder.getContext();
context.setAuthentication(otherUserAuthentication);
~~~

Đây là **đoạn minh họa**. Nếu object context được tham chiếu bởi một worker đang chờ thực thi, việc mutate cùng object có thể thay đổi identity mà worker thấy. Trong luồng xử lý authentication bình thường, nên tạo một **SecurityContext mới** rồi gọi **SecurityContextHolder.setContext(newContext)**, thay vì sửa một object có thể đang được dùng chung, phù hợp với hướng dẫn của Spring Security.

Cần cân nhắc sâu hơn:

- Principal/UserDetails hoặc Authentication instance có thể chứa trạng thái mutable.
- Authorities/scope có thể thay đổi trong cơ sở dữ liệu sau khi task được xếp hàng.
- Credentials/tokens là dữ liệu nhạy cảm; không sao chép toàn bộ vào message queue hoặc log.
- Context capture không phải bằng chứng **token còn hạn** hoặc user vẫn có quyền ở thời điểm xử lý.
- Một background service không được nâng quyền chỉ vì caller gửi một userId hoặc role string không được kiểm chứng.

Khi task chỉ cần **gắn người tạo tác vụ vào audit record**, có thể đủ để chụp một **userId**/requestId immutable và xác thực được ở request boundary. Khi task thực sự cần gọi API phụ thuộc Spring method security, nên dùng **SecurityContext propagation có chủ đích** hoặc **tái xác thực/ủy quyền tại worker**, không chỉ truyền String tùy ý.

### Phân biệt identity dùng cho audit với quyền được dùng để thao tác

| Mục đích | Dữ liệu thường cần | Điều phải bảo đảm |
| --- | --- | --- |
| Audit: ai đã yêu cầu job? | userId, requestId, thời điểm và nguồn xác thực đã được kiểm chứng | Không bị client tự điền userId giả mạo; lưu được theo hợp đồng audit |
| Authorization: có được xuất dữ liệu này? | Authentication/authorities và policy đọc tài nguyên hiện hành | Kiểm tra ở điểm thao tác, cả resource-level permissions |
| Gọi downstream thay mặt user | Token có scope thích hợp, thời hạn và cơ chế delegation được thiết kế | Không dùng token đã hết hạn; giới hạn quyền |
| Job nội bộ theo service account | Service identity có quyền tối thiểu | Không chia sẻ quyền cao hơn cần thiết cho mọi task của app |
| Correlation logging | traceId hoặc requestId | Không coi ID log như bằng chứng danh tính |

**Propagation** giải quyết “context nào được nhìn thấy ở đây”; **authentication/authorization** giải quyết “context đó có hợp lệ và có quyền thực hiện hành động hiện tại không”.

## 12. Spring Security filter chain và ranh giới HTTP request

Trong Spring MVC với Security Filter Chain, các filter bảo mật quản lý **đọc/ghi/xóa** SecurityContext theo request và session/repository phù hợp.

Tùy cấu hình và phiên bản, **Spring MVC Callable** và các cơ chế MVC async chính thức có thể được Spring Security tích hợp propagation đặc biệt. Nhưng điều này **không có nghĩa** một Runnable tự gửi đến ExecutorService riêng hoặc một **CompletableFuture.supplyAsync** bất kỳ tự nhận SecurityContext.

Cần phân biệt:

~~~text
A. MVC-managed async flow
   Security Filter Chain -> MVC async integration -> Callable path
   Có thể được framework áp dụng security integration phù hợp.

B. User-created background work
   Controller -> customExecutor.submit(task)
   Cần khai báo rõ SecurityContext propagation hoặc policy khác.

C. Reactive flow
   WebFlux/Reactor publisher chain
   Dùng ReactiveSecurityContextHolder / Reactor Context,
   không giả định cách hoạt động của servlet ThreadLocal.
~~~

**Không dùng HttpServletRequest hoặc request-scoped object như một bản snapshot hợp lệ cho background job**; sau khi request hoàn tất, vòng đời tài nguyên có thể không còn. Nếu worker chỉ cần một vài tham số đã kiểm chứng, hãy chụp immutable DTO trước khi chuyển luồng.

## 13. Vì sao ThreadLocal và InheritableThreadLocal không phải chiến lược phân quyền?

Xét ba anti-pattern:

**A. Tự gắn userId vào một ThreadLocal ứng dụng và coi đó là identity đáng tin.**

~~~java
CURRENT_USER.set(request.getParameter("userId"));
executor.execute(() -> readSecret(CURRENT_USER.get()));
~~~

Đây là **đoạn mã sai nhằm minh họa**. Nó vừa tin userId do client gửi, vừa giả định ThreadLocal truyền sang executor. Không dùng giá trị này làm nguồn authorization.

**B. Đổi tất cả SecurityContextHolder sang MODE_GLOBAL.**

Một global context dùng chung cho tất cả threads là rủi ro nghiêm trọng trong server đa người dùng. Không khắc phục context loss bằng cách đánh đổi cách ly identity của các request.

**C. Bật MODE_INHERITABLETHREADLOCAL rồi tin thread pool luôn tạo worker mới.**

Pool có thể sống lâu, worker được tạo khi nhận task đầu tiên, và giá trị kế thừa có thể cũ hoặc của request khác. Các thread con không thuộc quyền kiểm soát ứng dụng có thể vô tình thừa hưởng identity. Giải pháp này không bảo đảm đúng context theo lần submit.

**D. Dùng một static mutable field cho Authentication hiện hành.**

~~~java
static Authentication CURRENT_AUTH;
~~~

Trong web server có nhiều request đồng thời, mọi thread cùng đọc/ghi một field nên có thể ghi đè user của nhau. Đây không phải một implementation thay thế cho SecurityContextHolder.

**E. Tạo một DelegatingSecurityContextExecutor với context của Alice ở application startup và chia sẻ cho toàn bộ người dùng.**

Đó là **fixed-context mode**, không phải mode capture theo request. Nếu muốn mỗi task dùng quyền của caller hiện hành, dùng wrapper không cố định context ở lần tạo bean và xác định đúng ranh giới submit.

## 14. Xử lý cleanup đúng cách kể cả khi task lỗi

Một wrapper context propagation an toàn phải coi **cleanup là bắt buộc**, không chỉ chạy khi nghiệp vụ thành công.

~~~text
set context for worker
    |
    +-- task returns normally --------+
    |                                 |
    +-- task throws exception --------+--> finally cleanup
    |                                 |
    +-- task cancellation/interruption+
~~~

Với custom ThreadLocal metadata, dùng try/finally và thận trọng với giá trị đang tồn tại trước khi wrapper chạy. Trong một cơ chế nested synchronous execution, **lưu previous rồi restore previous** có thể cần thiết; trong worker pool thuần với boundary độc lập, **clear** phải bảo đảm không lưu giá trị của request trước. Không chọn một cách cleanup chung bất kể framework có hợp đồng gì.

Với Spring Security, **DelegatingSecurityContextRunnable/Callable** và các wrapper executor dùng cơ chế setup/clear của SecurityContextHolder. Đừng chèn thêm một finally thủ công **clearContext()** ở giữa một flow mà framework đang sở hữu và sau đó còn cần dùng identity, nếu điều đó phá nested scope. Nên để wrapper chuyên dụng làm chủ toàn bộ ranh giới.

**Nếu task bị reject trước khi bắt đầu:** body và finally **bên trong body không bao giờ chạy**. Do đó, không phân bổ tài nguyên hoặc giữ reference nhạy cảm ở caller rồi trông cậy vào finally của task để dọn. Khi wrapper được tạo nhưng task chưa thực thi, cần xem lifecycle của Runnable/Callable và queue theo executor.

## 15. Thời điểm capture: submit, execute và future completion

Ngữ cảnh của caller chỉ đúng tại **một thời điểm**. Đây là yếu tố rất quan trọng nếu tác vụ được gửi trễ:

~~~text
Request thread Alice:
    t0: SecurityContextHolder = Alice
    t1: create Runnable
    t2: later, thread context đã đổi
    t3: executor.execute(Runnable)
                                |
                                +-- wrapper capture tại execute/submit
~~~

Với wrapper không truyền SecurityContext cố định, **context được chọn ở lúc nó wrap task cho lần submit**. Nếu code tạo Runnable dưới Alice nhưng chỉ submit khi context đã bị xóa, task có thể không mang Alice.

Tương tự, với **CompletableFuture**:

~~~java
CompletableFuture<String> future =
        CompletableFuture.supplyAsync(
                () -> load(), securedExecutor);
~~~

Đây là **đoạn minh họa**. Với Executor wrapper capture tại execute, supplier có context theo thread đã gọi supplyAsync ở lúc scheduling, miễn không có tầng xử lý khác thay đổi ngữ cảnh.

Khi một **thenApplyAsync** được scheduling sau khi upstream hoàn tất, thread yêu cầu executor chạy callback có thể là thread của upstream completion. Nếu thread đó không có SecurityContext phù hợp, một wrapper capture-per-submit cũng không tự tìm lại request cũ.

Muốn pipeline nhiều bước giữ context nghiệp vụ nhất quán, hãy chọn rõ:

1. Dùng **per-job context** được quản lý an toàn và wrapper riêng cho scope ấy (không dùng lại như singleton của toàn ứng dụng).
2. Hoặc **chụp immutable user ID/command** và ủy quyền lại tại từng bước có side effect.
3. Hoặc dùng integration context propagation phù hợp với loại pipeline/framework, rồi test thực tế.
4. Không chỉ nối thêm Async callbacks mà không chỉ định executor hoặc context contract.

## 16. Ranh giới bảo mật trong tác vụ nền dài hạn

Context capture có thể đúng lúc request bắt đầu, nhưng sau vài phút hoặc vài giờ:

- Quyền người dùng có thể bị thu hồi.
- Session/token có thể hết hạn.
- Resource permission có thể thay đổi.
- Người dùng có thể không còn là thành viên tổ chức.
- Job có thể được chuyển sang một process/queue khác không chia sẻ ThreadLocal.

Vì vậy, lựa chọn cần phù hợp yêu cầu nghiệp vụ:

| Kiểu tác vụ | Mô hình thường phù hợp |
| --- | --- |
| Tác vụ ngắn trong cùng process và cần method security | Dùng Spring Security wrapper trên executor, kiểm tra context theo contract |
| Tác vụ chỉ ghi audit ai yêu cầu | Chụp immutable audit identity/requestId từ ngữ cảnh đã xác thực |
| Tác vụ lâu hoặc có quyền nhạy cảm | Lưu job identity, xác thực/ủy quyền lại tại thời điểm thực hiện |
| Công việc qua message broker | Truyền metadata đã xác thực/ký hoặc job ID; bên nhận xây authorization context theo chính sách |
| Tác vụ hệ thống không đại diện user nào | Identity riêng của service account với quyền tối thiểu, không tái sử dụng context người dùng |
| Reactive/WebFlux pipeline | ReactiveSecurityContextHolder trên Reactor Context, không chỉ servlet ThreadLocal |

**Không serialize nguyên SecurityContext** (đặc biệt credentials, token hoặc principal object lớn) vào durable queue chỉ để dễ truyền ngữ cảnh. Chỉ lưu những dữ liệu tối thiểu, có thể chứng thực lại và đáp ứng quy định bảo mật của hệ thống.

## 17. Cách chọn DelegatingSecurityContext wrapper phù hợp

| Bạn đang có | Nên xem xét | Lưu ý |
| --- | --- | --- |
| Một Runnable cần chạy với context hiện tại | DelegatingSecurityContextRunnable | Capture theo constructor; cleanup được hỗ trợ |
| Một Callable trả dữ liệu | DelegatingSecurityContextCallable | Dùng khi cần result/exception qua Callable |
| JDK Executor chỉ có execute | DelegatingSecurityContextExecutor | Không có submit/get trực tiếp ở interface Executor |
| JDK ExecutorService có submit/Future | DelegatingSecurityContextExecutorService | Bao cả Runnable và Callable, quản lý lifecycle delegate theo wrapper |
| Spring TaskExecutor | DelegatingSecurityContextTaskExecutor | Có thể chọn context cố định hoặc theo submit |
| Spring @Async với AsyncTaskExecutor | DelegatingSecurityContextAsyncTaskExecutor | Đăng ký bean executor có tên rồi dùng @Async("...") |
| Chạy định kỳ/scheduled | Các wrapper ScheduledExecutorService/TaskScheduler tương ứng | Phân biệt job chạy theo service identity hay theo caller |

**Đừng nhầm:** wrapper xử lý context không nâng **corePoolSize**, **maxPoolSize** hoặc dung lượng queue của underlying executor. Nếu pool bị overload, task có thể bị reject; nếu executor chưa nhận, không có worker để mang context. Vẫn phải xử lý TaskRejectedException/RejectedExecutionException và retry policy.

## 18. Test an toàn: hai người dùng, cùng một worker, không rò context

Một kiểm thử integration có giá trị cần đủ các trường hợp:

1. Thiết lập context **Alice** trên caller.
2. Submit vào **raw executor** và xác minh worker không tự có Alice.
3. Submit qua **security wrapper** và xác minh worker thấy Alice.
4. Đặt **SecurityContext object mới** của **Bob**, submit qua cùng wrapper/pool và xác minh thấy Bob.
5. Submit task raw ngay sau đó trên **chính worker** và xác minh **không còn Alice/Bob**.
6. Cho task được bảo vệ ném exception; xác minh wrapper vẫn cleanup sau exceptional completion.
7. Nếu sử dụng CompletableFuture và nhiều executor, kiểm tra từng **thenApplyAsync**/thenComposeAsync khi upstream complete trên thread khác.
8. Kiểm tra một nhánh **executor rejection** và một nhánh shutdown; không báo job đã được tiếp nhận nếu task không chạy.
9. Nếu test với @Async, luôn gọi **Spring-managed bean qua proxy**, không new bean trực tiếp và không self-invocation.
10. Khi cần ổn định lịch chạy, dùng **CountDownLatch** và **Future.get(timeout)**, không chỉ Thread.sleep để mong worker đúng thời điểm.

**Không chỉ assert kết quả nghiệp vụ đúng.** Một đoạn code chạy dưới Authentication sai vẫn có thể trả kết quả giống kỳ vọng trong trường hợp dữ liệu công khai. Test phải quan sát **principal/authorities trong worker** và việc cleanup sau task.

### Kiểm tra lỗi trong công việc bảo mật

Một integration test thực tế nên có test:

~~~java
Future<?> failed = secured.submit(() -> {
    if (SecurityContextHolder.getContext()
            .getAuthentication() == null) {
        throw new IllegalStateException("Thiếu context");
    }
    throw new IllegalStateException("Giả lập lỗi nghiệp vụ");
});
~~~

Đây là **đoạn minh họa** trong scope có secured kiểu ExecutorService và caller đã set SecurityContext. Sau khi failed.get() báo lỗi (ExecutionException), submit một task **raw** vào cùng worker để xác minh context đã bị dọn. Đừng dùng kết quả Future.isDone() làm bằng chứng rằng cleanup đã xong mà không có quan sát thích hợp.

## 19. Những nhầm lẫn rất thường gặp

**“SecurityContextHolder dùng ThreadLocal nên tôi có thể truyền bằng bất kỳ ThreadLocal wrapper nào.”** Sai. Cần giữ đúng security strategy, identity và lifecycle; dùng wrapper Spring Security hoặc integration có hợp đồng tương đương.

**“Cứ tạo ThreadLocal<String> USER rồi set username là Spring method security nhận biết.”** Sai. Spring Security đọc SecurityContext/Authentication theo cơ chế của nó, không đọc biến tùy ý của bạn.

**“Đổi sang InheritableThreadLocal là đủ cho ThreadPoolTaskExecutor.”** Sai. Inheritance chủ yếu xảy ra khi thread mới được tạo; worker pool có thể tái sử dụng giá trị cũ.

**“MODE_GLOBAL giúp mọi worker thấy user hiện tại, nên tốt cho web.”** Sai. Nó có thể làm nhiều request chia sẻ identity, không phù hợp server đa người dùng.

**“Đã dùng DelegatingSecurityContextAsyncTaskExecutor là mọi CompletableFuture stage tự có security context.”** Sai. Mỗi lần scheduling/Executor khác là một ranh giới cần xem xét.

**“Constructor có SecurityContext và constructor không có SecurityContext giống nhau.”** Sai. Một dạng dùng context cố định; dạng kia capture context hiện tại khi submit.

**“Wrapper Spring Security deep-copy toàn bộ Authentication.”** Không nên giả định. Có thể giữ reference SecurityContext; tránh mutate context dùng chung và xem lại authorization của job chạy trễ.

**“SecurityContext đã được propagate thì job được quyền đọc mọi resource.”** Sai. Propagation không thay thế authorization/resource-level checks.

**“@Async luôn chạy trên worker có đúng identity.”** Sai. Cần Spring proxy, executor đã đăng ký, wrapper phù hợp và context hợp lệ ở submit.

**“Dùng TaskDecorator truyền MDC là đã truyền SecurityContext.”** Sai. MDC và SecurityContext là hai miền khác nhau; không dùng log metadata để quyết định quyền.

**“Lưu nguyên SecurityContext vào queue để xử lý ngày mai là an toàn.”** Không mặc nhiên. Context có thể chứa dữ liệu nhạy cảm và quyền đã lỗi thời; job lâu dài cần chính sách identity/ủy quyền rõ ràng.

**“Task kết thúc sẽ tự remove ThreadLocal do tôi set.”** Sai. ThreadLocal không tự remove khi Runnable hoàn tất; pool giữ thread, nên cần cleanup ở ranh giới.

**“Một userId client gửi trong request là đủ chứng minh người đó đã xác thực.”** Sai. User identity dùng cho auth/audit phải được lấy từ cơ chế xác thực đáng tin và kiểm tra theo policy.

**“Reactor dùng thread pool nên tôi cứ dùng SecurityContextHolder từ servlet.”** Không đúng mô hình chung. Reactive security dùng **Reactor Context/ReactiveSecurityContextHolder**; việc đổi thread trong reactive pipeline không tương đương ThreadLocal-based servlet flow.

## 20. Bài tập suy luận

**Câu 1.** Caller có SecurityContext của Alice nhưng gửi Runnable qua Executors.newFixedThreadPool mà không wrapper. Worker có chắc nhìn thấy Alice không?

**Đáp án:** Không. Với MODE_THREADLOCAL mặc định, worker thường không tự có context của caller.

**Câu 2.** Một worker được tái sử dụng đã từng chạy tác vụ của Bob. Task mới của Alice chỉ dùng ThreadLocal tùy ý và quên remove sau khi chạy. Rủi ro là gì?

**Đáp án:** Task sau có thể thấy stale value của Alice hoặc Bob nếu thread không được dọn; rủi ro rò thông tin giữa request.

**Câu 3.** DelegatingSecurityContextExecutor được tạo bằng constructor chỉ nhận Executor. Lúc submit từ Alice và Bob, nó dùng context nào?

**Đáp án:** Theo cơ chế wrapper hiện hành, nó lấy SecurityContextHolder ở **thời điểm mỗi lần task được gửi vào wrapper**, không cố định một user từ lúc khởi tạo wrapper.

**Câu 4.** Nếu constructor nhận một SecurityContext của service account, rồi wrapper được dùng bởi nhiều caller khác nhau, các task nhận context nào?

**Đáp án:** Cùng context cố định đã được cung cấp, theo thiết kế của constructor đó; chỉ hợp lý nếu việc dùng identity chung có chủ đích và đúng quyền.

**Câu 5.** Vì sao MODE_INHERITABLETHREADLOCAL có thể để worker tiếp tục thấy Alice khi Bob gửi task?

**Đáp án:** Nó truyền giá trị lúc worker được tạo; pool có thể tái sử dụng worker thay vì sinh một thread mới tại mỗi submit.

**Câu 6.** @Async("securityAwareExecutor") method thấy đúng Alice. Sau đó caller dùng thenApplyAsync(fn) không truyền executor. Có chắc fn thấy Alice không?

**Đáp án:** Không. CompletableFuture có default async executor riêng, thường commonPool. Phải xét context ở stage đó.

**Câu 7.** Task chạy trong worker có Authentication của Alice nhưng quyền trên một resource vừa bị thu hồi. Chỉ nhìn auth.getName() có cho phép truy cập resource không?

**Đáp án:** Không. Cần áp dụng policy authorization phù hợp tại thời điểm thao tác; context capture không bảo đảm quyền còn hiệu lực.

**Câu 8.** Một custom wrapper set SecurityContextHolder nhưng không clear trong finally vì task đã hoàn thành. Có vấn đề gì?

**Đáp án:** Có. Worker thread pool có thể tái sử dụng và giữ Authentication cũ, rò dữ liệu/nguy cơ chạy với user sai.

**Câu 9.** Một task cần ghi lại ai đã tạo job, nhưng không cần thực thi thay mặt người dùng. Có nhất thiết giữ nguyên SecurityContext trong queue nhiều giờ không?

**Đáp án:** Không. Có thể chụp userId đã xác thực và dữ liệu audit tối thiểu, lưu theo hợp đồng nghiệp vụ, không giữ credentials/Authentication dư thừa.

**Câu 10.** Reactor/WebFlux có nên dùng các giả định y hệt ThreadLocal của servlet security không?

**Đáp án:** Không. Reactive security dùng Reactor Context và integration riêng; phải xác định loại ứng dụng và cơ chế propagation thực sự.

## 21. Checklist triển khai

- [ ] Xác định loại context cần dùng: **SecurityContext** hay metadata logging/tracing.
- [ ] Không thay thế Spring Security Authentication bằng **ThreadLocal<String> userId** tự tạo.
- [ ] Với Spring Security servlet, hiểu rõ **SecurityContextHolderStrategy / MODE_THREADLOCAL** đang dùng.
- [ ] Không dùng **MODE_GLOBAL** trong web app đa người dùng để sửa context loss.
- [ ] Không dùng **MODE_INHERITABLETHREADLOCAL** làm lời giải mặc định cho thread pool.
- [ ] Với Runnable/Callable, chọn **DelegatingSecurityContextRunnable/Callable** khi phù hợp.
- [ ] Với Executor/ExecutorService, dùng **DelegatingSecurityContextExecutor/ExecutorService**.
- [ ] Với Spring @Async, xác định **DelegatingSecurityContextAsyncTaskExecutor** và tên bean đúng.
- [ ] Không cố định một user SecurityContext trong singleton executor xử lý nhiều request.
- [ ] Tránh chia sẻ/mutate cùng SecurityContext instance giữa nhiều thread.
- [ ] Xác nhận **@Async đi qua proxy**, không self-invocation.
- [ ] Nếu pipeline có nhiều CompletableFuture stages, xác định context ở **từng lần scheduling**.
- [ ] Dùng **TaskDecorator hoặc tracing integration** cho MDC/request correlation, không đánh đồng với security.
- [ ] Cleanup context trong finally hoặc để wrapper Spring Security chuyên dụng đảm trách.
- [ ] Truyền input nghiệp vụ bằng **immutable snapshot**, không giữ HttpServletRequest/transaction ở background.
- [ ] Khi job chạy trễ hoặc lâu dài, kiểm tra lại quyền/token/resource theo policy.
- [ ] Test **Alice → Bob trên cùng một worker**, rồi raw task để xác minh không rò context.
- [ ] Kiểm tra cả lỗi/rejection/shutdown, timeout và cleanup khi task bất thường.
- [ ] Không serialize credentials hoặc SecurityContext nhạy cảm vào queue/log một cách tùy tiện.

## Kết luận

**ThreadLocal chỉ cung cấp nơi lưu trữ theo thread; SecurityContextHolder là cổng truy cập vào một ngữ cảnh bảo mật có ý nghĩa xác thực và phân quyền.** Khi công việc đi qua ExecutorService, CompletableFuture hoặc Spring @Async, context của request không tự đi theo mọi thread. Tự truyền một ThreadLocal tùy ý có thể gây mất context, rò rỉ giữa request và nhầm lẫn identity.

Với Spring Security, ưu tiên **DelegatingSecurityContextRunnable/Callable** hoặc những wrapper **DelegatingSecurityContextExecutor / ExecutorService / AsyncTaskExecutor** tương ứng. Chọn rõ **context cố định hay context tại lúc submit**, bảo đảm **cleanup trên worker**, không coi InheritableThreadLocal là phương án mặc định cho pool, và test nhiều người dùng trên **cùng một worker tái sử dụng**. Cuối cùng, **context propagation không thay thế authorization**: dữ liệu, token và quyền phải được kiểm chứng theo thời điểm thao tác và yêu cầu nghiệp vụ.

**Tài liệu chính thức để tra cứu:**

- [Spring Security — Concurrency Support](https://docs.spring.io/spring-security/reference/features/integrations/concurrency.html): các wrapper DelegatingSecurityContext* và cơ chế set/clear.
- [Spring Security — Servlet Authentication Architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html): SecurityContextHolder, Authentication và holder strategy.
- [DelegatingSecurityContextAsyncTaskExecutor Javadoc](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/task/DelegatingSecurityContextAsyncTaskExecutor.html).
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): TaskExecutor, TaskDecorator và @Async.
- [Java SE 17 — ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html), [InheritableThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InheritableThreadLocal.html) và [ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="execution-context-010">Không dùng InheritableThreadLocal như giải pháp chung cho nhóm luồng tái sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng InheritableThreadLocal như giải pháp chung cho nhóm luồng tái sử dụng

## 1. Vấn đề: thread con kế thừa lúc được tạo, không phải mỗi lần chạy task

Trong ứng dụng Java và Spring, một request có thể cần chuyển một ít **ngữ cảnh thực thi** sang công việc bất đồng bộ: request ID, tenant ID, correlation ID dùng cho log hoặc thông tin bảo mật đã được framework kiểm soát. Vì các ngữ cảnh này thường gắn với thread hiện hành, developer dễ nghĩ rằng thay `ThreadLocal<T>` bằng `InheritableThreadLocal<T>` sẽ khiến task bất đồng bộ “tự có” dữ liệu của request.

**Cách nghĩ đó không đúng khi dùng thread pool tái sử dụng worker.** `InheritableThreadLocal` truyền **giá trị khởi tạo từ thread cha sang thread con tại thời điểm thread con được tạo**. Một pool có thể tạo worker khi khởi động hoặc khi nhận một task đầu tiên, rồi tái sử dụng **cùng worker** để chạy các task của nhiều request khác nhau. Việc request B gửi task vào worker đã được tạo từ request A **không làm Java kế thừa lại** ngữ cảnh B.

Hệ quả có thể là **đọc ngữ cảnh cũ**, đọc `null` không ngờ tới, hoặc lẫn dữ liệu giữa các request/tenant. Đây không chỉ là lỗi log: nếu code vô tình dùng một tenant/user ID cũ để quyết định truy cập dữ liệu, vấn đề có thể trở thành **sự cố phân quyền và cô lập dữ liệu**.

### Mục tiêu bài học

Sau bài này, bạn sẽ:

- Hiểu thời điểm `InheritableThreadLocal` sao chép giá trị và cơ chế `childValue(parentValue)`, phân biệt với truyền ngữ cảnh **theo từng task**.
- Giải thích vì sao `ExecutorService`, `ThreadPoolExecutor`, `ThreadPoolTaskExecutor` và `@Async` có thể tái sử dụng worker, làm dữ liệu thừa kế không cập nhật theo caller.
- Tái hiện lỗi bằng chương trình **Java 17+ có stdout xác định**, sau đó sửa bằng wrapper capture/restore ngữ cảnh ở thời điểm **submit**.
- Biết khi nào nên truyền dữ liệu bằng tham số rõ ràng, khi nào dùng `TaskDecorator` của Spring, công cụ context propagation, hoặc wrapper của Spring Security.
- Xử lý `ThreadLocal.remove()` và khôi phục giá trị trước đó trong `finally`, tránh lưu trạng thái request trên thread phục vụ nhiều yêu cầu.
- Phân biệt trường hợp một thread mới thực sự được tạo với trường hợp worker đã tồn tại; hiểu rủi ro của giá trị mutable được chia sẻ theo tham chiếu.
- Kiểm thử tính đúng đắn của ngữ cảnh với **cùng một worker được tái sử dụng** và nhiều request, không dựa vào số lượng thread/ngẫu nhiên.

**Phiên bản ví dụ:** Java 17+, Spring Framework 6.1.3 (ví dụ Maven Spring thuần). Các quy tắc kế thừa của `InheritableThreadLocal` là hợp đồng Java; Spring không tự sửa hợp đồng đó chỉ bằng việc dùng `@EnableAsync`.

## 2. Chính xác InheritableThreadLocal hoạt động thế nào?

### 2.1. ThreadLocal thường và InheritableThreadLocal

`ThreadLocal<T>` cung cấp một vùng giá trị **theo từng thread**. Nếu hai thread cùng gọi `REQUEST_ID.get()` trên một key `ThreadLocal`, mỗi thread đọc giá trị riêng của mình, không mặc nhiên thấy giá trị của thread kia.

`InheritableThreadLocal<T>` kế thừa `ThreadLocal<T>`, thêm bước **khởi tạo dữ liệu cho một thread con khi thread con được tạo**. Theo Javadoc Java 17:

- Khi tạo child thread, child có thể nhận những giá trị ban đầu từ parent thread cho các biến `InheritableThreadLocal` đang có giá trị.
- Phương thức `childValue(parentValue)` tính giá trị khởi tạo của child; việc tính xảy ra **trong parent thread trước khi child được khởi động**.
- Mặc định `childValue` chỉ **trả lại đối tượng `parentValue`**. Nếu giá trị là đối tượng mutable, parent và child có thể giữ **cùng tham chiếu**, không phải một bản sao sâu.
- Java cho phép một số hình thức tạo thread **không kế thừa** inheritable thread-locals. Do đó không được coi tính kế thừa là một cơ chế xuyên qua mọi executor và runtime.

Bảng so sánh:

| Đặc điểm | `ThreadLocal<T>` | `InheritableThreadLocal<T>` |
| --- | --- | --- |
| Giá trị được phân theo thread | Có | Có |
| Child thread tự nhận giá trị khởi tạo từ parent theo mặc định | Không | Có, tại lúc **child thread được tạo** |
| Tự cập nhật giá trị mỗi lần executor nhận task mới | Không | **Không** |
| Worker của thread pool tự đổi sang context của request B | Không | **Không** |
| `remove()` trên caller tự xóa giá trị ở worker đã tồn tại | Không | **Không** |
| Dùng cho ngữ cảnh request với pool tái sử dụng mà không có wrapper | Không phù hợp | **Không phù hợp** |
| Có thể dùng khi tạo một child thread thật với lifecycle gắn parent | Cần tự truyền | Có thể phù hợp nếu hiểu rõ phạm vi và đối tượng chia sẻ |

**Quy tắc:** `ThreadLocal` quản lý dữ liệu theo **thread**; request/task là một phạm vi logic **khác thread**. Việc đặt dữ liệu trên thread không tự thiết lập ranh giới cleanup sau mỗi task.

### 2.2. Dòng thời gian gây lỗi

Giả sử một thread pool chỉ có một worker:

~~~text
Request A (caller): TENANT.set("tenant-A")
    |
    +-- submit task A
          |
          +-- pool cần worker mới: tạo worker-1
                |
                +-- worker-1 kế thừa giá trị "tenant-A"
                +-- chạy task A, đọc tenant-A
                +-- task A hoàn tất, worker-1 KHÔNG bị hủy

Request B (caller): TENANT.set("tenant-B")
    |
    +-- submit task B
          |
          +-- pool TÁI SỬ DỤNG worker-1
                |
                +-- KHÔNG tạo thread mới
                +-- KHÔNG gọi lại childValue cho request B
                +-- task B vẫn đọc tenant-A từ worker-1
~~~

**Sai lầm cốt lõi:** developer nghĩ việc `submit(task B)` tạo ra một quan hệ **parent/child mới** giữa request B và worker-1. Thực tế submit chỉ đưa `Runnable`/`Callable` đến executor; worker-1 **đã được tạo từ trước**.

Không bảo đảm mọi pool đều trả kết quả sai theo đúng một cách. Nếu worker được tạo trong lúc khởi động, dữ liệu thừa kế có thể **trống**; nếu pool mở rộng khi load tăng, worker mới có thể nhận context khác; khi thread chết và được tạo lại, hành vi có thể đổi. Chính sự phụ thuộc vào thời điểm tạo worker làm cách dùng này **không ổn định**.

## 3. Java 17+ chạy độc lập: tái hiện ngữ cảnh request bị cũ

Ví dụ cố ý dùng `InheritableThreadLocal` theo cách sai để quan sát lỗi. `Executors.newSingleThreadExecutor()` sử dụng một worker và sẽ tái sử dụng worker đó cho các task tiếp theo trong điều kiện bình thường. Ta gọi `Future.get()` sau mỗi task để bảo đảm task A hoàn tất rồi mới nộp B.

Lưu thành `InheritablePoolPitfallDemo.java`:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class InheritablePoolPitfallDemo {

    private static final InheritableThreadLocal<String> TENANT =
            new InheritableThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            TENANT.set("tenant-A");

            // Worker được tạo khi nhận task đầu tiên và kế thừa A.
            System.out.println("first="
                    + pool.submit(TENANT::get).get());

            // Caller thay đổi context, worker cũ không tự đổi.
            TENANT.set("tenant-B");
            System.out.println("caller=" + TENANT.get());

            // B vẫn chạy trên worker đã tồn tại.
            System.out.println("second="
                    + pool.submit(TENANT::get).get());

        } finally {
            // Chỉ xóa giá trị trên MAIN, không sửa context worker.
            TENANT.remove();

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
javac InheritablePoolPitfallDemo.java
java InheritablePoolPitfallDemo
~~~

**Stdout với JDK 17+ thông thường trong ví dụ một worker này:**

~~~text
first=tenant-A
caller=tenant-B
second=tenant-A
~~~

### Vì sao kết quả này đáng tin?

1. `TENANT.set("tenant-A")` xảy ra trước lần đầu nộp task, nên worker được tạo để chạy task A nhận giá trị A khi được tạo.
2. `pool.submit(TENANT::get).get()` chờ task A hoàn tất. Task chỉ đọc giá trị; nó không gây exception làm worker bị thay thế.
3. Caller đổi `TENANT` sang B nhưng không tác động đến giá trị trong **worker khác**.
4. Executor một worker tái sử dụng worker đó, nên task B đọc giá trị **A**.
5. `TENANT.remove()` trong `main` chỉ tác động đến **main thread**. Cleanup của worker không được thực hiện tự động chỉ vì caller đã xóa context.
6. Chương trình đóng executor trong `finally`, không bỏ lại worker chạy sau khi demo kết thúc.

Nếu code production thật dùng worker với lifecycle dài và nhiều request, việc thấy tenant A tại request B là một **kết quả không chấp nhận được**. Đừng sao chép kiểu truyền ngữ cảnh của phản ví dụ này vào hệ thống có thông tin người dùng.

**Giới hạn của thí nghiệm:** Với các executor có nhiều worker, worker có thể được tạo vào thời điểm khác hoặc bị thay thế; khi đó dữ liệu quan sát có thể thay đổi. Điều này không làm phương pháp trở nên an toàn, mà cho thấy nó còn khó dự đoán hơn.

## 4. Không chỉ sai thời điểm: còn có nguy cơ chia sẻ mutable object

Mặc định `InheritableThreadLocal.childValue(parentValue)` trả lại **chính tham chiếu đầu vào**. Ví dụ nếu thread cha đặt một `Map<String, String>` hoặc `List<String>`, thread con có thể thấy và sửa **cùng object**. Hai thread có hai vị trí trong thread-local map nhưng giá trị các vị trí đó có thể trỏ tới **cùng instance**.

Đoạn Java minh họa ngắn (fragment, cần imports và method bao quanh):

~~~java
InheritableThreadLocal<java.util.List<String>> tags =
        new InheritableThreadLocal<>();

java.util.List<String> list = new java.util.ArrayList<>();
tags.set(list);

Thread child = new Thread(() -> tags.get().add("child"));
child.start();
child.join();

System.out.println(list); // [child]
tags.remove();
~~~

Ở đây `join()` bảo đảm child đã sửa xong trước khi main đọc `list`; không có cập nhật đồng thời vào `ArrayList` trong fragment. Nếu parent và child cùng sửa `ArrayList` đồng thời, lại thêm một nguy cơ **data race** vì `ArrayList` không thread-safe.

Bạn có thể override `childValue` để tạo một bản sao khi child thread được tạo:

~~~java
// Fragment minh họa một cách copy list khi tạo thread con.
InheritableThreadLocal<java.util.List<String>> tags =
        new InheritableThreadLocal<>() {
            @Override
            protected java.util.List<String> childValue(
                    java.util.List<String> parent) {
                return parent == null
                        ? null
                        : new java.util.ArrayList<>(parent);
            }
        };
~~~

Nhưng **cách này không sửa lỗi thread pool**: `childValue` vẫn chỉ được gọi **khi tạo worker**, không phải lúc mỗi task được nộp. Copy lúc tạo thread làm dữ liệu được tách về tham chiếu, **không làm dữ liệu luôn mới theo request**.

**Không gắn một entity JPA, connection, transaction object, mutable security context hay request object nặng** vào inheritable thread-local để truyền cho task. Những đối tượng đó thường gắn lifecycle, session hoặc thread cụ thể và có thể không thread-safe.

## 5. Những tình huống làm bug xuất hiện thất thường

### 5.1. Worker tạo sẵn trước khi request tới

`ThreadPoolExecutor` có thể được khởi tạo với core worker **chạy sẵn** bằng `prestartCoreThread()` hoặc `prestartAllCoreThreads()`. Spring `ThreadPoolTaskExecutor` cũng có tùy chọn `setPrestartAllCoreThreads(true)`.

~~~java
// Fragment JDK, không phải class độc lập.
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        1, 1, 60, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>());
pool.prestartAllCoreThreads();
~~~

Nếu tạo worker ở startup thread **không có tenant/request context**, worker đó không tự kế thừa context của những caller gửi task sau. Bạn có thể thấy `null`, ngay cả khi request thread đã đặt `InheritableThreadLocal`.

### 5.2. Worker tạo lười khi có request đầu tiên

Mặc định nhiều pool tạo worker khi có task. Nếu request A làm pool tạo worker, thread đó có thể nhận context A. Khi B/C/D dùng lại worker, context A vẫn còn. Nguy cơ gây rò rỉ thông tin giữa request nghiêm trọng hơn một giá trị `null` dễ phát hiện.

### 5.3. Pool tăng worker khi queue đầy

Với `ThreadPoolExecutor`, việc tạo worker phụ thuộc `corePoolSize`, `maximumPoolSize` và tình trạng queue. Worker mới có thể được tạo trong các thời điểm và từ các thread gửi task khác nhau. Do đó trong cùng một pool có thể tồn tại worker mang context A, worker mang context B và worker mang context rỗng. Kết quả còn phụ thuộc worker nào được chọn để chạy task.

Đừng viết test kiểu “gửi ba task rồi mong chúng dùng worker A/B/C theo thứ tự”. Hãy dùng executor một worker cho bài test khẳng định tính sai, hoặc kiểm tra có hệ thống với latch, giới hạn worker, queue và context của từng task.

### 5.4. Worker bị thay thế hoặc pool thay đổi size

Worker có thể kết thúc, bị thay thế, hoặc một số pool có idle timeout tùy cấu hình. Khi worker mới được tạo, một giá trị inheritable khác có thể xuất hiện. Vì thế lỗi có thể “biến mất” sau restart hoặc tự đổi khi tải cao; không có nghĩa là code đã đúng.

### 5.5. Parent thread cũng là worker của một pool khác

Trong pipeline có nhiều lớp executor, một task chạy trên worker của pool thứ nhất có thể tạo worker cho pool thứ hai. Giá trị `InheritableThreadLocal` được thừa kế từ **thread đang tạo worker**, chứ không nhất thiết là request gốc. Khi cấu trúc pool phức tạp, lineage của thread không phản ánh lineage của request.

## 6. Phân biệt phạm vi: thread, task, request và transaction

~~~text
Phạm vi request:    [ Request A ]  [ Request B ]  [ Request C ]
Phạm vi task:         [ Task A ]     [ Task B ]     [ Task C ]
Phạm vi worker:     [------------ worker-1 dùng lại ------------]
                           ^
                           |
                  InheritableThreadLocal
                  kế thừa một lần tại
                  creation, KHÔNG tại submit
~~~

Muốn task B biết ngữ cảnh B, cách đúng là **nắm ngữ cảnh tại thời điểm nộp B** rồi thiết lập ngữ cảnh đó **chỉ trong phạm vi thực thi B**, sau đó dọn/khôi phục.

Đặc biệt, những dữ liệu sau có cơ chế riêng:

| Dữ liệu | Giải pháp thích hợp |
| --- | --- |
| `tenantId`/`requestId` đơn giản | **Truyền tham số bất biến** hoặc snapshot ở điểm submit |
| MDC/correlation logging | Copy MDC map có kiểm soát hoặc context propagation facility được cấu hình |
| Spring Security `SecurityContext` | Dùng các wrapper/async integrations của **Spring Security**, kiểm tra scope và cleanup |
| Spring transaction | Thiết lập **transaction boundary riêng** ở nơi thực thi; không truyền transaction của caller qua ThreadLocal |
| Servlet request attributes | Hạn chế giữ request object sau response; truyền những dữ liệu cần thiết hoặc dùng facility có vòng đời rõ ràng |
| Micrometer observation/tracing | Context propagation có đăng ký accessor thích hợp và instrumentation |
| Database session/JPA EntityManager | Không chia sẻ resource gắn thread/session; đi qua service/repository tại worker với transaction phù hợp |

**Bảo mật:** Chuyển user ID bằng String không tự trở thành cơ chế authorization. Việc quyết định quyền phải dựa trên principal/context đã được xác thực theo contract bảo mật, không tin một ThreadLocal tùy ý như nguồn duy nhất của thông tin nhạy cảm.

## 7. Lựa chọn ưu tiên: truyền ngữ cảnh bằng tham số

Nếu một tác vụ chỉ cần `tenantId`, `documentId`, correlation ID hoặc một vài giá trị business **bất biến**, cách rõ nhất là truyền chúng trực tiếp cho task:

~~~java
// Fragment, tenantId là giá trị đã được xác thực ở boundary.
String tenantId = currentTenantId();

CompletableFuture<String> result =
        CompletableFuture.supplyAsync(
                () -> documentService.loadForTenant(tenantId),
                documentExecutor
        );
~~~

Đây là **fragment không độc lập**, cần các biến, hàm và executor của ứng dụng. Không có `InheritableThreadLocal`; task giữ snapshot `tenantId` trong closure. Khi cùng worker chạy task của các request khác nhau, mỗi closure mang giá trị riêng.

Ưu điểm: dễ test, quan hệ dữ liệu tường minh, không rò rỉ context qua worker và không cần đoán framework hiện có interceptor nào. Đổi lại, API cần chấp nhận tham số rõ ràng; với nhiều loại framework-specific context, một cơ chế propagation chính thức có thể phù hợp hơn.

**Cẩn thận với mutable object:** Nếu closure giữ object có thể bị caller sửa sau `submit`, vẫn có race. Hãy snapshot/immutable copy các giá trị cần thiết hoặc xây dựng DTO bất biến thay vì chuyển nguyên request/entity có thể bị thay đổi đồng thời.

## 8. Java 17+ chạy độc lập: capture/restore context theo từng task

Khi phải tương tác với một API dùng `ThreadLocal`, có thể viết wrapper **chụp giá trị tại thread nộp task**, rồi **cài giá trị đó trong worker đúng thời gian chạy task**. Sau đó wrapper khôi phục context có trước trong worker hoặc `remove()` nếu trước đó trống.

Ví dụ sau sử dụng `ThreadLocal<String>` thông thường, **không cần InheritableThreadLocal**. Đó là chủ ý: inheritance lúc tạo thread không giải quyết được scope theo task.

Lưu thành `ExplicitContextDemo.java`:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ExplicitContextDemo {

    private static final ThreadLocal<String> TENANT =
            new ThreadLocal<>();

    private static <T> Future<T> submitWithTenant(
            ExecutorService executor,
            Callable<T> task) {

        // Đọc tại caller NGAY LÚC SUBMIT, không đợi đến lúc worker chạy.
        final String captured = TENANT.get();

        return executor.submit(() -> {
            // Worker có thể đang phục vụ một scope khác.
            final String previous = TENANT.get();

            try {
                if (captured == null) {
                    TENANT.remove();
                } else {
                    TENANT.set(captured);
                }

                return task.call();
            } finally {
                // Đảm bảo không để lại context của task mới trên worker.
                if (previous == null) {
                    TENANT.remove();
                } else {
                    TENANT.set(previous);
                }
            }
        });
    }

    private static String currentTenant() {
        String tenant = TENANT.get();
        return tenant == null ? "none" : tenant;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            TENANT.set("tenant-A");
            Future<String> first =
                    submitWithTenant(pool, ExplicitContextDemo::currentTenant);
            System.out.println("first=" + first.get());

            TENANT.set("tenant-B");
            Future<String> second =
                    submitWithTenant(pool, ExplicitContextDemo::currentTenant);
            System.out.println("second=" + second.get());

            TENANT.remove();
            Future<String> empty =
                    submitWithTenant(pool, ExplicitContextDemo::currentTenant);
            System.out.println("empty=" + empty.get());
            System.out.println("caller=" + currentTenant());

        } finally {
            // Cleanup ngữ cảnh ở MAIN và vòng đời executor.
            TENANT.remove();
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
javac ExplicitContextDemo.java
java ExplicitContextDemo
~~~

Đầu ra:

~~~text
first=tenant-A
second=tenant-B
empty=none
caller=none
~~~

### Vì sao wrapper sửa được lỗi?

1. `captured = TENANT.get()` chạy trong **caller đang gửi task**. Vì vậy task A bắt snapshot A, task B bắt snapshot B.
2. Worker nhận task thì lưu lại `previous` của **worker thread**, sau đó cài snapshot của đúng task.
3. Task đọc giá trị của chính nó, không phụ thuộc worker được tạo ở thời điểm nào.
4. `finally` chạy cả khi `task.call()` ném exception, bảo đảm gỡ/khôi phục context theo scope đã thiết lập.
5. Nếu caller không có giá trị, wrapper gọi `TENANT.remove()` trong worker **trước task**, tránh nhìn thấy context cũ.
6. Sau khi task xong, context của worker được phục hồi đúng theo trước đó. Trong một worker sạch ban đầu, điều này nghĩa là không để lại giá trị của task A/B cho task tiếp.

**Giải thích “restore previous” so với “remove luôn”:** Khi wrapper chỉ sở hữu một phần context trong một stack tác vụ lồng nhau, khôi phục giá trị cũ là cần thiết để không phá scope bên ngoài. Với worker hoàn toàn chuyên dụng và không có context nền, cleanup bằng `remove()` sau mỗi task cũng là một chiến lược hợp lệ. Nhưng nếu `previous` vốn đã chứa context rò rỉ do code khác, việc restore không tự sửa bug bên ngoài; cần kiểm soát owner của executor và mọi đường thiết lập context.

**Chú ý:** Đây là ví dụ tối giản cho một `ThreadLocal<String>`. Không lấy nguyên mẫu và tự động áp dụng cho `SecurityContext`, transaction, MDC hoặc dữ liệu mutable khi chưa hiểu API snapshot/restore chính thức. Với dữ liệu bảo mật, ưu tiên wrapper của framework phù hợp.

## 9. Trong Spring: TaskDecorator là điểm đặt context theo từng task

`org.springframework.core.task.TaskDecorator` là functional interface để bọc `Runnable` trước khi chạy. `ThreadPoolTaskExecutor.setTaskDecorator(...)` áp decorator cho callback thực thi. Đây là vị trí phù hợp để tạo **per-submission context snapshot** và thiết lập/khôi phục trong worker, thay vì dựa vào inheritance lúc worker ra đời.

Trong Spring Framework 6.1.3, Javadoc `ThreadPoolTaskExecutor` ghi rõ decorator có thể nhận **wrapper của task** (không nhất thiết là đúng Runnable/Callable mà caller gửi). Vai trò chính là thiết lập execution context hoặc quan sát thời gian thực thi; không nên dựa vào decorator để bắt toàn bộ exception phát sinh từ `FutureTask` vì wrapper có thể giữ lỗi bên trong `Future`.

### 9.1. Ví dụ Maven Spring Framework 6.1.3 chạy được

Cấu trúc ba Java file:

~~~text
spring-context-demo/
├── pom.xml
└── src/main/java/demo/
    ├── ContextConfig.java
    ├── TenantService.java
    └── SpringContextDemo.java
~~~

**Tệp `pom.xml`:**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-context-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.3</version>
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
                    <mainClass>demo.SpringContextDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**Tệp `src/main/java/demo/ContextConfig.java`:**

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ContextConfig {

    // Một giá trị đơn giản để quan sát kết quả; không dùng làm
    // nguồn ủy quyền bảo mật trong ứng dụng thực tế.
    public static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    @Bean("contextExecutor")
    public ThreadPoolTaskExecutor contextExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("tenant-");

        executor.setTaskDecorator(task -> {
            // Callback này được tạo khi task được nộp vào executor.
            String captured = TENANT.get();

            return () -> {
                String previous = TENANT.get();

                try {
                    if (captured == null) {
                        TENANT.remove();
                    } else {
                        TENANT.set(captured);
                    }
                    task.run();
                } finally {
                    if (previous == null) {
                        TENANT.remove();
                    } else {
                        TENANT.set(previous);
                    }
                }
            };
        });

        return executor;
    }

    @Bean
    public TenantService tenantService() {
        return new TenantService();
    }
}
~~~

`@EnableAsync` kích hoạt annotation processing, `@Bean` tạo executor có tên cụ thể để chọn bằng `@Async("contextExecutor")`. `TaskDecorator` chụp `TENANT` từ **thread nộp task**, rồi thiết lập snapshot trong worker và dọn dẹp sau khi task hoàn tất.

**Tệp `src/main/java/demo/TenantService.java`:**

~~~java
package demo;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;

public class TenantService {

    @Async("contextExecutor")
    public CompletableFuture<String> readTenant() {
        String current = ContextConfig.TENANT.get();
        String result = current == null ? "none" : current;
        return CompletableFuture.completedFuture(result);
    }
}
~~~

Method `readTenant()` nằm trên **bean service**, không được khai báo trong lớp `@Configuration`. Spring Javadoc `@Async` không hỗ trợ methods khai báo trực tiếp trong `@Configuration`, nên việc tách hai class là chủ ý.

**Tệp `src/main/java/demo/SpringContextDemo.java`:**

~~~java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class SpringContextDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             ContextConfig.class)) {

            TenantService service = context.getBean(TenantService.class);

            try {
                ContextConfig.TENANT.set("tenant-A");
                System.out.println("first=" + service.readTenant().join());

                ContextConfig.TENANT.set("tenant-B");
                System.out.println("second=" + service.readTenant().join());

                ContextConfig.TENANT.remove();
                System.out.println("empty=" + service.readTenant().join());
            } finally {
                ContextConfig.TENANT.remove();
            }
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java
~~~

Đầu ra của chương trình Java:

~~~text
first=tenant-A
second=tenant-B
empty=none
~~~

### Phân tích kết quả Spring

- Spring tạo một `ThreadPoolTaskExecutor` dùng **một worker**. Worker có thể được tái sử dụng từ lần gọi A sang B và lần gọi không có context.
- `@Async("contextExecutor")` làm lời gọi bên ngoài đi qua Spring proxy, được nộp vào executor chọn rõ.
- Mỗi lần nộp, `TaskDecorator` chụp giá trị ở caller đang gọi service: A, B, rồi `null`.
- Trước khi chạy method thực, worker được đặt context tương ứng. Sau khi method và callback kết thúc, `finally` khôi phục giá trị trước đó.
- `join()` chờ completion của `CompletableFuture`; vì chương trình chỉ in ở main, stdout có thứ tự ổn định.
- Đoạn này dùng **`ThreadLocal` thường**, không cần `InheritableThreadLocal`. Nó kiểm soát ngữ cảnh **theo task**, không theo lúc worker sinh ra.

**Không sao chép nguyên trạng `static ThreadLocal` chứa tenant ID vào production rồi dùng nó để cấp quyền.** Ví dụ này dùng String để minh họa cách truyền ngữ cảnh; hệ thống thật cần security boundary có thể audit, xác thực principal, ownership rõ ràng và cleanup ở điểm nhận/trả request.

**Vòng đời:** `AnnotationConfigApplicationContext` quản lý bean executor. Khi context đóng, Spring dọn dẹp thread pool theo lifecycle. Không khởi tạo một `ThreadPoolTaskExecutor` mới cho mỗi request.

## 10. Các lựa chọn tích hợp chuẩn của Spring

### 10.1. Spring 6.1+ và Micrometer Context Propagation

Spring Framework 6.1 cung cấp `org.springframework.core.task.support.ContextPropagatingTaskDecorator`. Nó có thể dùng cơ chế Micrometer Context Propagation để chụp và phục hồi những context đã đăng ký accessor phù hợp, hữu ích cho **tracing, observation và logging**.

~~~java
// Fragment Spring 6.1+; cần dependency/context accessors phù hợp.
executor.setTaskDecorator(
        new ContextPropagatingTaskDecorator());
~~~

Đây **không phải** câu thần chú tự truyền **mọi** `ThreadLocal` tùy ý. Cần xem các dependency `io.micrometer:context-propagation`, `ThreadLocalAccessor` được đăng ký, version Spring và cách instrumentation hiện hành lấy snapshot. Nếu chưa có accessor cho biến của ứng dụng, đừng khẳng định nó được truyền. Việc capture/restore cũng có overhead, nên cần đo với khối lượng lớn các task rất ngắn.

Đối với những context đơn giản chỉ chứa business ID, **truyền tham số tường minh** vẫn thường dễ hiểu và ít phụ thuộc hơn.

### 10.2. Spring Security: không dùng inheritable holder mode làm giải pháp chung

Spring Security thường lưu `SecurityContext` gắn với thread thông qua `SecurityContextHolder`. Chuyển sang `SecurityContextHolder.MODE_INHERITABLETHREADLOCAL` không giải quyết lỗi stale context trên một pool tái sử dụng worker; giá trị vẫn có thể được kế thừa lúc worker tạo, không phải lúc task mới nộp.

Spring Security cung cấp những wrapper concurrency chuyên dụng, ví dụ `DelegatingSecurityContextRunnable`, `DelegatingSecurityContextExecutor` và `DelegatingSecurityContextAsyncTaskExecutor` (tùy API executor) để thiết lập/clear security context theo phạm vi thực thi. Với overload nhận `SecurityContext` cố định, wrapper có thể cố tình dùng **một context chung cho mọi task**; với chế độ chụp context tại submit, cần chọn constructor/wrapper đúng ý định. Không tự đoán mọi lớp đều truyền principal động theo cùng một cách.

Khi xử lý quyền truy cập thật, hãy đi qua cơ chế Spring Security chính thức và test **hai request có hai principal khác nhau trên cùng worker**, thêm case không có principal. Không tự copy reference mutable của `SecurityContext` rồi xem như đảm bảo snapshot an toàn.

### 10.3. MDC dùng map snapshot và cleanup

Với logging framework hỗ trợ MDC, hướng xử lý là:

1. Ở thread nộp task, lấy **bản sao context map** theo API MDC của framework (không chỉ chia sẻ cùng mutable map).
2. Trong worker, lưu/khôi phục MDC hiện hành theo yêu cầu framework, cài bản sao của đúng task.
3. Trong `finally`, xóa hoặc khôi phục dữ liệu cũ để worker không đem correlation ID của request A sang B.
4. Kiểm tra khi caller không có context, khi task lỗi, khi future bị từ chối và khi worker đã được tạo từ trước.

Không phải bản triển khai MDC nào cũng có cùng API và cấu trúc; đừng trộn `ThreadLocal` tùy ý với MDC mà không kiểm tra hợp đồng của thư viện log.

### 10.4. @Async và CompletableFuture phải dùng đúng executor

`@Async` trong Spring thường dựa vào proxy; **self-invocation** (`this.asyncMethod()` trong cùng service) sẽ bỏ qua proxy trong `AdviceMode.PROXY` mặc định. Do đó `TaskDecorator` trên một executor không tự giúp nếu lời gọi chưa đi qua executor.

Tương tự:

~~~java
// Fragment: businessExecutor là bean TaskExecutor/Executor.
CompletableFuture<String> future =
        CompletableFuture.supplyAsync(
                () -> loadDocument(), businessExecutor);
~~~

Khi truyền executor Spring, decorator được áp cho task được gửi qua executor đó. Ngược lại, `CompletableFuture.supplyAsync(() -> loadDocument())` **không truyền executor** thường dùng `ForkJoinPool.commonPool()`, nên decorator của Spring **không được tự động áp dụng**.

Các stage `thenApplyAsync(fn)` hoặc `handleAsync(fn)` cũng có default async facility riêng khi không truyền executor. Nếu context quan trọng, truyền executor cần thiết vào **từng stage async cần isolation**, hoặc sử dụng cơ chế propagation đã thiết kế rõ.

## 11. Ngữ cảnh nào không nên “copy” như một String?

Không phải mọi context có thể sao chép đơn giản:

- **Transaction:** Spring transaction thường gắn với thread thực thi; không nên chụp rồi chuyển nguyên transaction/resource handles sang worker. Tạo transaction phù hợp trong method của worker qua Spring transaction interceptor/transaction template nếu cần.
- **Request/response object:** Servlet request có lifecycle giới hạn; không giữ nguyên object sau khi HTTP response xong chỉ để đọc header ở background thread. Truyền những giá trị bất biến thật sự cần thiết.
- **EntityManager/Session:** các resource persistence gắn phiên/transaction không mặc định thread-safe; mở tài nguyên theo worker và quản lý lifecycle.
- **Mutable security context:** có thể thay đổi trong lúc task đang chờ; dùng API/wrapper chính thức và xác định snapshot/ownership.
- **ThreadLocal có dữ liệu lớn:** pool worker sống lâu có thể giữ đối tượng lớn, dẫn đến tăng bộ nhớ và retention ngoài dự tính. Cleanup bằng `remove()` là trách nhiệm của code đặt context vào worker.

Với job chạy qua MQ hoặc process khác, `ThreadLocal`/`InheritableThreadLocal` không phải giao thức truyền context giữa process. Cần đưa metadata cần thiết vào message/headers theo chính sách bảo mật, rồi tái tạo context hợp lệ ở consumer.

## 12. Quan sát và kiểm thử đúng tình huống

### Test tối thiểu cần có

| Ca kiểm thử | Điều cần xác nhận |
| --- | --- |
| Một worker, hai task A/B từ caller đổi tenant | Mỗi task thấy **tenant đúng của chính nó** |
| Sau A/B có task C không có tenant | C phải thấy **none/null**, không rò rỉ A hoặc B |
| Worker đã `prestart` trước khi có tenant | Task vẫn nhận context đúng nhờ wrapper, không phụ thuộc inheritance |
| Task ném exception giữa chừng | `finally` khôi phục/clear context; task tiếp theo không bị nhiễm |
| Caller đổi tenant **sau** khi nộp task nhưng trước lúc worker chạy | Task dùng **snapshot lúc nộp**, không dùng giá trị mới |
| Pool có nhiều workers | Kết quả không phụ thuộc worker nào được scheduler chọn |
| `@Async` qua proxy và gọi self-invocation | Chỉ khẳng định async cho lời gọi đi qua interceptor phù hợp |
| Executor shutdown/rejection | Bắt lỗi tại submit và không để caller báo “đã nhận task” khi chưa nhận |
| Security context theo hai principal | Không có lẫn danh tính/authorization giữa request |
| MDC/correlation | Log trong task mang trace phù hợp, được cleanup khi hết task |

Để test race có kiểm soát, dùng `CountDownLatch` giữ worker, nộp một task rồi đổi context caller trước khi mở latch. Sau khi worker đọc, assert rằng wrapper đã chụp giá trị **ở thời điểm submit**. Tránh dùng `Thread.sleep(10)` rồi đoán worker chắc chắn đã chạy lúc nào.

### Các số liệu production nên quan sát

- Số task đang chạy, chiều dài queue, latency submit → start, thời gian chạy task và số rejection.
- Số job thiếu tenant/correlation ID, tỉ lệ context mismatch, số callback chạy trên executor ngoài dự tính.
- Lỗi bảo mật “wrong principal”, request ID trùng lặp bất thường và log lệch trace; **không log token, session secrets hoặc chi tiết nhạy cảm** để debug.
- Retention/memory của worker và `ThreadLocalMap` khi nghi ngờ leak.
- Đường đi của async context qua `@Async`, scheduler, `CompletableFuture` stages, retry và message consumer.

### Checklist code review

1. Tìm mọi `new InheritableThreadLocal<>()` và nơi code đặt `SecurityContextHolder.MODE_INHERITABLETHREADLOCAL`.
2. Xác định giá trị đó có phải **per-request/per-task** hay thực sự là dữ liệu hợp lệ của **child thread một lần**.
3. Nếu task chạy trên pool tái sử dụng worker, **không dựa vào inheritance** để lấy request context.
4. Ưu tiên tham số bất biến, hoặc wrapper/scope capture tại submit và restore/clear bằng `finally`.
5. Với Spring, cấu hình `ThreadPoolTaskExecutor.setTaskDecorator(...)` hoặc built-in propagation phù hợp, kiểm tra thứ tự decorator/context propagation khác nhau.
6. Với Spring Security, dùng lớp/wrapper tích hợp và kiểm thử principal cụ thể; không tự thêm cơ chế phân quyền dựa trên String ThreadLocal.
7. Đảm bảo `@Async` và các `CompletableFuture` stages thực sự chạy trên executor đã cấu hình; đừng vô tình bỏ qua proxy hoặc dùng common pool.
8. Nếu truyền mutable values, cân nhắc snapshot copy và thread-safety; không để child và parent vô tình chia sẻ state.
9. Xử lý cleanup cả khi task thất bại, bị cancel trước khi chạy, timeout hoặc exception ở worker; cẩn thận điều gì được gọi khi task chưa được nhận.
10. Không tuyên bố đã giải quyết chỉ bằng việc đổi tên thread hay tăng pool size; test trên cùng một worker nhiều lượt và đo hành vi thực tế.

## 13. Những hiểu lầm thường gặp

**“InheritableThreadLocal tự kế thừa lại mỗi lần submit.”** Sai: kế thừa gắn với **thread creation**, không phải submission.

**“Caller đã gọi remove() thì worker cũng mất giá trị cũ.”** Sai: `remove()` ảnh hưởng thread **đang gọi**, không xóa giá trị của mọi worker khác.

**“Pool không thể dùng InheritableThreadLocal vì không bao giờ có thread mới.”** Không chính xác. Pool có thể tạo worker mới, nhưng thời điểm tạo **không trùng phạm vi logic của mọi request**; đó mới là lý do inheritance không đáng tin.

**“Override childValue để clone context sẽ giải quyết được per-request propagation.”** Không. Nó chỉ thay đổi giá trị khởi tạo khi worker tạo, không chạy lại cho mỗi task.

**“Giá trị thread-local ở hai thread chắc chắn là hai object khác nhau.”** Sai. Hai vị trí có thể giữ **cùng tham chiếu mutable** nếu giá trị inherited mặc định.

**“Gọi TaskDecorator quanh task xong thì mọi Future exception đều ném ra từ decorator.”** Không. Với `submit`/`FutureTask`, lỗi có thể được giữ trong `Future`; cần kiểm tra `get()`/callback theo API.

**“Bật @EnableAsync đã đủ để context tự truyền.”** Không. Nó bật xử lý async method, không tự xác định propagation cho mọi `ThreadLocal` tùy ý.

**“Dùng MODE_INHERITABLETHREADLOCAL của Spring Security là an toàn cho pool tái sử dụng.”** Không. Nó có cùng giới hạn kế thừa lúc tạo thread; dùng wrapper bảo mật phù hợp.

**“CompletableFuture không truyền executor sẽ dùng ThreadPoolTaskExecutor của Spring.”** Sai. Overload không executor thường chạy trên common pool của JDK.

**“Mỗi lần chạy task gọi set(context) nhưng không cần finally.”** Sai. Worker có thể chạy task khác sau đó và nhìn thấy giá trị cũ.

**“Truyền một EntityManager/transaction bằng ThreadLocal sẽ làm chúng dùng được ở thread khác.”** Sai. Những resource này có ràng buộc lifecycle/thread riêng.

**“Khi worker thread bị thay thế làm bug biến mất tức là bug đã sửa.”** Sai. Sự thay đổi chỉ phản ánh thời điểm inheritance mới; lỗi vẫn có thể lặp lại.

## 14. Tự kiểm tra

**Câu 1.** `InheritableThreadLocal` sao chép giá trị của parent vào child khi nào?  
**Đáp án:** Khi **child thread được tạo**, theo cơ chế tính `childValue(parentValue)`, không tự lặp lại khi thread nhận task.

**Câu 2.** Pool một worker được tạo bởi request A. Request B thay đổi `TENANT` rồi submit task; task B tự nhận context B không?  
**Đáp án:** Không nếu chỉ dùng inheritance. Worker đã tồn tại có thể tiếp tục đọc giá trị A; cần snapshot tại submit và cài/cleanup trong worker.

**Câu 3.** `TENANT.remove()` ở request B có xóa `TENANT` trong worker không?  
**Đáp án:** Không. `remove()` chỉ tác động thread hiện hành; worker là thread khác.

**Câu 4.** Vì sao một `InheritableThreadLocal<List<String>>` có thể dẫn đến dữ liệu chung ngoài dự tính?  
**Đáp án:** `childValue` mặc định trả cùng tham chiếu parent; list mutable có thể được chia sẻ, dẫn tới thay đổi chéo và race.

**Câu 5.** Tại sao clone trong `childValue` không bảo đảm task B nhận context B?  
**Đáp án:** Clone chỉ xảy ra khi tạo child thread. Worker cũ không bị tạo lại khi nhận B.

**Câu 6.** Giải pháp đơn giản nhất nếu task chỉ cần một `tenantId` đã xác thực là gì?  
**Đáp án:** Truyền `tenantId` dưới dạng tham số bất biến vào task/service, thay vì ngầm lấy từ inheritable thread-local.

**Câu 7.** Trong Spring, `TaskDecorator` nên chụp context và dọn ở đâu?  
**Đáp án:** Chụp khi **task được nộp**; cài giá trị trên worker trước khi chạy callback; khôi phục hoặc remove trong `finally` sau khi callback kết thúc.

**Câu 8.** Khi `@Async` bị self-invocation thì TaskDecorator có chắc được chạy không?  
**Đáp án:** Không. Trong proxy mode, lời gọi tự gọi method có thể không đi qua async interceptor/executor, nên decorator có thể không tham gia.

**Câu 9.** Có nên truyền transaction/JPA EntityManager qua InheritableThreadLocal để giữ nguyên transaction caller sang worker?  
**Đáp án:** Không. Resource gắn thread/session/lifecycle cần boundary riêng trong worker; không copy trực tiếp để giả lập cùng transaction.

**Câu 10.** Một task trên pool một worker trả `null` dù caller vừa set context; trường hợp nào có thể gây ra?  
**Đáp án:** Worker đã được tạo sẵn lúc không có context; inheritance chỉ có ở thời điểm tạo, không ở lúc submit.

## 15. Tài liệu tham khảo chính thức

- [Java SE 17 — InheritableThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/InheritableThreadLocal.html): thời điểm inheritance, `childValue` và việc child có thể nhận cùng tham chiếu.
- [Java SE 17 — ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): `get`, `set`, `remove` theo thread hiện hành.
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): worker reuse, on-demand construction, prestart, pool sizing và queue.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): `submit`, `Future` và shutdown.
- [Spring Framework 6.1.3 — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): `setTaskDecorator`, thread pool lifecycle, queue và behavior của decorator với `FutureTask`.
- [Spring Framework 6.1.3 — TaskDecorator](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/core/task/TaskDecorator.html): callback bọc task, thiết lập execution context.
- [Spring Framework 6.1.2 — ContextPropagatingTaskDecorator](https://docs.spring.io/spring-framework/docs/6.1.2/javadoc-api/org/springframework/core/task/support/ContextPropagatingTaskDecorator.html): context propagation bằng Micrometer, có từ Spring 6.1.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): executor, `@Async`, lựa chọn pool và xử lý concurrency.
- [Spring Security Reference — Concurrency Support](https://docs.spring.io/spring-security/reference/features/integrations/concurrency.html): `DelegatingSecurityContextRunnable`, `DelegatingSecurityContextExecutor` và cleanup bảo mật theo task.


</details>

- [Quay lại đầu trang](#back-to-top)
