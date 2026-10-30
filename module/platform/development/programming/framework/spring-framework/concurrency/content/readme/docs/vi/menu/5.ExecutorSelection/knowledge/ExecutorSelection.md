<a id="back-to-top"></a>

# Chọn bộ thực thi

## Menu
- [SyncTaskExecutor chạy ngay trên luồng gọi](#executor-selection-001)
- [SimpleAsyncTaskExecutor tạo luồng cho từng tác vụ và không tái sử dụng luồng](#executor-selection-002)
- [Giới hạn đồng thời của SimpleAsyncTaskExecutor](#executor-selection-003)
- [ThreadPoolTaskExecutor quản lý nhóm luồng tái sử dụng](#executor-selection-004)
- [ConcurrentTaskExecutor thích nghi Executor có sẵn](#executor-selection-005)
- [TaskExecutorAdapter thích nghi hợp đồng Executor của JDK](#executor-selection-006)
- [DefaultManagedTaskExecutor trong môi trường Jakarta EE](#executor-selection-007)
- [Chọn cách thực thi theo tải, môi trường và yêu cầu vòng đời](#executor-selection-008)

## <a id="executor-selection-001">SyncTaskExecutor chạy ngay trên luồng gọi</a>

<details>
<summary>Xem chi tiết</summary>

# SyncTaskExecutor chạy ngay trên luồng gọi

## 1. Mục tiêu và ý tưởng cốt lõi

Trong Spring Framework, `TaskExecutor` là một abstraction để gửi công việc (một `Runnable`) cho thành phần thực thi. Tên “executor” **không bảo đảm** rằng công việc sẽ được đưa sang luồng mới. `org.springframework.core.task.SyncTaskExecutor` là trường hợp rõ nhất: khi gọi `execute(task)`, nó **chạy `task.run()` ngay trên luồng đang gọi**, và chỉ trả về sau khi `run()` kết thúc hoặc ném ngoại lệ.

Đây là một lựa chọn có chủ đích trong nhóm “**Chọn bộ thực thi**”: dùng nó khi muốn thực thi đồng bộ, hành vi dễ dự đoán hoặc không cần tách tác vụ khỏi caller. Không dùng chỉ vì thấy chữ “TaskExecutor” rồi cho rằng công việc sẽ tự chạy nền.

Ví dụ một luồng HTTP đang xử lý request:

~~~text
Luồng http-nio-8080-exec-1:
  log("before")
  syncTaskExecutor.execute(job)
    -> job.run()        [vẫn chính http-nio-8080-exec-1]
    -> job.run() xong
  log("after")

Thứ tự luôn là before -> toàn bộ job -> after nếu job kết thúc bình thường.
~~~

**Phân biệt quan trọng:** “đồng bộ” ở đây nghĩa là **mỗi lời gọi `execute` chờ chính task của nó**. Nó **không** có nghĩa rằng mọi task trên toàn ứng dụng được tuần tự hóa bằng một khóa. Nếu request A và B đang được xử lý trên hai luồng HTTP khác nhau, cùng một instance `SyncTaskExecutor` có thể chạy hai task đồng thời trên hai luồng gọi đó.

## 2. Cơ chế thực thi

Hợp đồng đơn giản nhất là:

~~~java
// Mã giả mô tả cơ chế; không phải source code Spring chép nguyên văn.
public void execute(Runnable task) {
    task.run(); // Gọi trực tiếp trên luồng hiện tại.
}
~~~

Phiên bản Spring thực còn kiểm tra đối số hợp lệ và có thể bổ sung cơ chế theo phiên bản framework; nhưng thuộc tính quyết định của `SyncTaskExecutor` vẫn là **thực thi trên caller thread**. Với Spring Framework 6.x dùng trong ví dụ bên dưới, không có pool thread hay hàng đợi nhiệm vụ bên trong executor này.

### Các hệ quả trực tiếp

- **Không chuyển luồng:** `Thread.currentThread()` bên trong task là cùng đối tượng thread của caller tại thời điểm gọi.
- **Có chặn caller:** task chạy 2 giây thì lời gọi `execute` cũng phải ở trong task khoảng 2 giây, cộng chi phí nhỏ của lời gọi Java. Nếu caller là HTTP request thread, thời gian phản hồi bị kéo dài.
- **Không có hàng đợi riêng:** không có nơi để gửi công việc rồi trả về ngay, không có số worker cố định hay throughput độc lập với số luồng đang gọi.
- **Kết quả / lỗi trực tiếp:** `execute(Runnable)` trả `void`; giá trị kết quả cần do code của caller/task quản lý. Nếu chính `Runnable.run()` ném unchecked exception mà không bắt, exception lan ra từ lời gọi `execute` trong trường hợp dùng trực tiếp.
- **Giữ ngữ cảnh luồng:** thread-local state, thread context classloader và mối liên kết transaction hiện tại của luồng vẫn là cùng ngữ cảnh trong lúc task chạy. Điều này có thể tiện khi test nhưng task cũng có thể vô tình thay đổi ngữ cảnh của caller.
- **Không tự bảo vệ shared state:** hai caller threads độc lập vẫn có thể đồng thời sửa cùng field của singleton service; cần atomic/lock/transaction theo đúng phạm vi dữ liệu.

Khác với `ThreadPoolTaskExecutor`, không có cấu hình `corePoolSize`, `maxPoolSize`, `queueCapacity` theo mô hình thread pool cho executor này trong Spring 6.x. Không suy ra những thuộc tính của bản Spring khác cho API đã được chọn.

## 3. Ví dụ chạy được: trực tiếp dùng SyncTaskExecutor

Ví dụ này dùng **Java 17** và **Spring Framework 6.2.12**. Nó chứng minh ba điểm bằng kết quả ổn định:

1. Task chạy giữa hai dòng `before/after` trên đúng caller thread.
2. Unchecked exception của task đi ra caller khi dùng trực tiếp `execute`.
3. Hai luồng gọi khác nhau có thể cùng chạy task trên một `SyncTaskExecutor` (không có khóa tuần tự toàn cục).

### 3.1. Cấu trúc tệp

~~~text
sync-executor-demo/
├── pom.xml
└── src/main/java/com/example/executor/
    └── SyncTaskExecutorDemo.java
~~~

### 3.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>sync-executor-demo</artifactId>
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
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` kéo theo `spring-core`, nơi chứa `TaskExecutor` và `SyncTaskExecutor`. Không cần Spring Boot, web server hay annotation để gọi API trực tiếp.

### 3.3. SyncTaskExecutorDemo.java

~~~java
package com.example.executor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

public final class SyncTaskExecutorDemo {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        Thread.currentThread().setName("request-caller");
        TaskExecutor executor = new SyncTaskExecutor();

        REQUEST_ID.set("REQ-42");
        try {
            System.out.println("1 before: " + Thread.currentThread().getName());

            executor.execute(() -> {
                System.out.println("2 task: " + Thread.currentThread().getName());
                System.out.println("3 trace: " + REQUEST_ID.get());
            });

            System.out.println("4 after: " + Thread.currentThread().getName());
        } finally {
            REQUEST_ID.remove();
        }

        try {
            executor.execute(() -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException ex) {
            System.out.println("5 caught: " + ex.getMessage());
        }

        demonstrateMultipleCallers(executor);
    }

    private static void demonstrateMultipleCallers(TaskExecutor executor)
            throws InterruptedException {
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();

        Runnable job = () -> {
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            bothEntered.countDown();

            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        };

        Thread callerA = new Thread(() -> executor.execute(job), "caller-A");
        Thread callerB = new Thread(() -> executor.execute(job), "caller-B");

        callerA.start();
        callerB.start();

        // Cả hai đã vào task, cùng chờ release; phải có 2 task active.
        bothEntered.await();
        release.countDown();
        callerA.join();
        callerB.join();

        System.out.println("6 max-active: " + maxActive.get());
    }
}
~~~

### 3.4. Lệnh chạy và stdout

Tại thư mục `sync-executor-demo`, có JDK 17+ và Maven:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=com.example.executor.SyncTaskExecutorDemo
~~~

Các dòng **do chương trình in**, theo đúng thứ tự:

~~~text
1 before: request-caller
2 task: request-caller
3 trace: REQ-42
4 after: request-caller
5 caught: boom
6 max-active: 2
~~~

Vì `execute` chạy task trực tiếp, dòng 4 không thể xuất hiện trước dòng 2 hoặc 3 khi task thành công. `REQUEST_ID` dùng `ThreadLocal` nên task thấy cùng giá trị `REQ-42` trên caller thread. Phần cuối dùng hai `CountDownLatch` để chắc chắn task A và task B đều đã vào `run()` trước khi giải phóng; `max-active: 2` chứng minh cùng một executor có thể có **hai task đang chạy** khi hai caller thread khác nhau đồng thời gọi nó. Giá trị này được đo bằng `AtomicInteger`; ví dụ cố ý dùng cơ chế đồng bộ của JDK để tránh test phụ thuộc vào thời điểm ngẫu nhiên.

Tín hiệu `ThreadLocal.remove()` trong `finally` là cần thiết nếu sau đó luồng gọi còn được tái sử dụng, chẳng hạn luồng servlet container. Mã ở trên là một file Java độc lập trong Maven project vừa chỉ ra; không dựa vào file bên ngoài.

## 4. Dùng SyncTaskExecutor trong Spring bean và @Async

Trong ứng dụng Spring, có thể đăng ký executor này thành bean và inject qua kiểu chung `TaskExecutor`. Chỉ chọn `@Async("syncExecutor")` khi **muốn chủ đích biến lời gọi qua proxy thành xử lý đồng bộ** (thường để test). Chữ `@Async` trên phương thức **không thể bắt buộc tạo thread mới** nếu executor được chọn chính là `SyncTaskExecutor`.

Để kiểm tra cả wiring và cơ chế Spring AOP proxy, thêm file thứ hai vào cùng project Maven ở mục 3:

`src/main/java/com/example/executor/AsyncSyncExecutorDemo.java`

~~~java
package com.example.executor;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncSyncExecutorDemo {
    @Bean("syncExecutor")
    public TaskExecutor syncExecutor() {
        return new SyncTaskExecutor();
    }

    @Bean
    public ReportTask reportTask() {
        return new ReportTask();
    }

    public static void main(String[] args) {
        Thread.currentThread().setName("request-caller");

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             AsyncSyncExecutorDemo.class)) {
            // Lấy bean do Spring quản lý: lời gọi đi qua @Async proxy.
            ReportTask worker = context.getBean(ReportTask.class);

            System.out.println("A before");
            worker.generate();
            System.out.println("C after");
        }
    }

    public static class ReportTask {
        @Async("syncExecutor")
        public void generate() {
            System.out.println("B task: " + Thread.currentThread().getName());
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=com.example.executor.AsyncSyncExecutorDemo
~~~

Các dòng do chương trình in (ngoài log framework nếu có):

~~~text
A before
B task: request-caller
C after
~~~

`@EnableAsync` bật hỗ trợ proxy cho `@Async`; `@Async("syncExecutor")` chọn đúng executor qua tên bean. Vì executor chạy đồng bộ, `worker.generate()` chỉ trả về sau khi nội dung phương thức hoàn tất. Ở ví dụ này `ReportTask` là public nested class không final, phương thức public; Spring có thể tạo proxy cho bean. Nếu gọi `this.generate()` từ chính `ReportTask`, lời gọi nội bộ thường bỏ qua proxy của `@Async` ở proxy mode; khi đó phương thức cũng chạy trực tiếp nhưng **vì bỏ qua interceptor**, không phải vì executor đã được áp dụng. Phân biệt hai nguyên nhân để chẩn đoán đúng.

### Tại sao không suy diễn toàn bộ hành vi của @Async từ execute?

- Với lời gọi **trực tiếp** `syncExecutor.execute(runnable)`, unchecked exception từ `Runnable.run()` thường truyền trực tiếp ra caller (như ví dụ 3).
- Khi qua **proxy `@Async`**, Spring gói tác vụ và xử lý kết quả/ngoại lệ theo kiểu trả về. Với `void`, lỗi chưa bắt được chuyển tới cơ chế `AsyncUncaughtExceptionHandler`; với `Future/CompletableFuture`, caller có thể quan sát lỗi qua kết quả tương lai (chẳng hạn `get()/join()`). Đừng hứa rằng mọi exception của phương thức `@Async` sẽ ném ra giống `execute` trực tiếp.
- Khi task chạy đồng bộ, một `CompletableFuture` được hoàn tất ngay trong lời gọi gửi task có thể đã hoàn thành lúc phương thức gọi trả về. API có tên “async” và kiểu `Future` **không bảo đảm** thao tác thực sự chạy nền.
- `@Async` có hỗ trợ return type hạn chế và quy tắc proxy của Spring; những chi tiết đó không thay đổi bản chất synchronous của `SyncTaskExecutor`.

Ví dụ với Java `CompletableFuture` (đoạn minh họa trong phương thức, giả sử `executor` là `TaskExecutor` ở mục 3):

~~~java
CompletableFuture<Void> future = CompletableFuture.runAsync(
        () -> System.out.println("runs inline"), executor);
System.out.println(future.isDone()); // true khi task thành công và đã chạy xong
~~~

`CompletableFuture.runAsync` nhận một `Executor` nhưng nó gọi `executor.execute`; với `SyncTaskExecutor` task được thực thi ngay, nên `future` đã hoàn tất khi `runAsync` trả về. Nếu task lỗi, `CompletableFuture` lưu lỗi để quan sát bằng `join/get`; đó là hành vi của wrapper `CompletableFuture`, không mâu thuẫn với cơ chế `execute` trực tiếp.

## 5. Ảnh hưởng trong HTTP request, transaction và thread context

Giả sử controller gọi:

~~~java
// Đoạn minh họa trong một @RestController; không phải class đầy đủ.
@GetMapping("/reports/{id}/refresh")
public String refresh(@PathVariable String id) {
    syncExecutor.execute(() -> reportService.refresh(id));
    return "Accepted";
}
~~~

Nếu `reportService.refresh(id)` gọi DB 5 giây, request HTTP phải đợi khoảng 5 giây mới trả chữ “Accepted”. Dùng chữ “Accepted” trong body **không** biến tác vụ thành xử lý background; status HTTP `202 Accepted` cũng không phù hợp nếu nhiệm vụ thực tế đã được xử lý xong ngay trong lời gọi mà không có ý định tiếp nhận tiếp. Với thao tác đồng bộ, API nên trả mã HTTP/kết quả phản ánh kết quả đã xử lý; với tác vụ nền thật sự dài, dùng executor bất đồng bộ có giới hạn hoặc message queue, kèm mã job để tra trạng thái.

### Ngữ cảnh nào còn trên luồng?

Khi dùng `SyncTaskExecutor` trực tiếp:

- `ThreadLocal` do chính caller thiết lập vẫn hiện diện trong task, bao gồm nhiều cấu hình logging MDC, security context hoặc context khác thường đặt trên luồng.
- `TransactionSynchronizationManager` ghi nhận transaction association trên luồng hiện tại. Vì task chạy cùng luồng, nếu transaction đã hoạt động thì truy cập tài nguyên theo transaction-bound context có thể tham gia chính transaction ấy, tùy các phương thức service và quy tắc propagation.
- `RequestContextHolder` thường có request attributes trong quá trình một HTTP request đồng bộ; task trực tiếp có thể nhìn thấy chúng khi context đang hoạt động. Điều này **không** có nghĩa có thể giữ và dùng request context sau khi request hoàn tất.
- Nếu task thay đổi MDC, security context, `ThreadLocal` hoặc context classloader mà không restore, caller có thể quan sát thay đổi đó ngay sau `execute`. Cần dọn hoặc khôi phục trong `finally` theo ownership.

Không suy ra rằng `SyncTaskExecutor` *tự tạo transaction*, tự propagate sang thread khác, hay thay thế `@Transactional`. Nếu code gọi qua Spring proxy của một service `@Transactional` thì các quy tắc transaction bình thường vẫn áp dụng; nếu self-invocation bypass proxy, annotation có thể không được xử lý.

### Vì sao điều này quan trọng khi thay executor?

Một thay đổi cấu hình từ `SyncTaskExecutor` sang `ThreadPoolTaskExecutor` có thể làm lộ ra giả định sai:

1. Caller trở về trước khi task xử lý xong; dữ liệu được đọc ngay sau `execute` có thể chưa sẵn sàng.
2. Task chạy trên luồng worker, nên `ThreadLocal` và transaction association của caller **không tự động** đi theo. Hãy truyền dữ liệu cần thiết như request ID, user ID đã xác thực hoặc immutable DTO; dùng cơ chế context propagation phù hợp nếu thật sự cần.
3. Exception của worker không thể mặc nhiên được bắt bằng `try/catch` quanh lời gọi gửi task; cần Future, error handler, callback hoặc trạng thái công việc.
4. Mutable collection chỉ an toàn vì task chạy inline trước đây có thể phát sinh race nếu caller tiếp tục sửa trong khi worker đọc. Truyền snapshot bất biến hoặc có đồng bộ đúng.

Ngược lại, khi test dùng `SyncTaskExecutor`, việc code chạy cùng thread có thể che giấu lỗi race/visibility và lỗi thiếu context propagation sẽ xuất hiện trên production executor. Cần thêm test chạy bằng executor thực tế khi những tính chất ấy là yêu cầu hệ thống.

## 6. So sánh khi chọn bộ thực thi

| Lựa chọn | Chạy ở đâu? | Lời gọi `execute` chờ task? | Điểm mạnh / khi dùng |
| --- | --- | --- | --- |
| `SyncTaskExecutor` | Trên caller thread | **Có**, đến khi task hoàn tất | Unit/integration test đồng bộ, luồng xử lý đơn giản |
| `SimpleAsyncTaskExecutor` | Mặc định tạo thread cho mỗi task; không tái sử dụng kiểu pool | Thường trả về sau khi giao task; có thể chờ vì cấu hình giới hạn | Tác vụ độc lập ngắn/đặc thù; phải hiểu chi phí tạo thread và giới hạn |
| `ThreadPoolTaskExecutor` | Các worker trong pool, có thể có queue | Thường trả về sau khi giao; có thể từ chối khi đầy | Tải nền có kiểm soát, tách khỏi request; cấu hình pool/queue/rejection |
| `ThreadPoolTaskScheduler` | Bộ thực thi kiêm lập lịch định kỳ | Được gọi theo lịch thay vì caller trực tiếp | `@Scheduled` và trigger cố định/cron |
| `ThreadPoolExecutor.CallerRunsPolicy` (JDK) | Chỉ có thể chạy trên caller khi pool từ chối task | Chỉ trong tình huống fallback tương ứng | Backpressure khi queue bão hòa; **khác** executor luôn đồng bộ |

`TaskExecutor` là interface, không cam kết thread mới. `SyncTaskExecutor` **không** giống “single-thread executor”: single-thread executor thường có **một worker thread riêng** và xếp các task vào hàng; `SyncTaskExecutor` dùng **bất cứ caller thread nào** đang gọi `execute`.

Đặc biệt khi triển khai 2 request đồng thời:

~~~text
Request A (thread-A) ---> sync.execute(taskA) ---> taskA.run() trên thread-A
Request B (thread-B) ---> sync.execute(taskB) ---> taskB.run() trên thread-B

Hai task có thể thực thi chồng thời gian.
Không có shared worker hay mutex chung ở SyncTaskExecutor.
~~~

Nếu mục tiêu là chỉ cho một task tại một thời điểm trong toàn JVM, cần một executor một worker thực sự hoặc một khóa phù hợp. Nếu mục tiêu là một task tại một thời điểm **trên nhiều pod**, phải dùng cơ chế coordination/lock ở kho chung, không dùng `SyncTaskExecutor`.

### Chọn executor cho tác vụ báo cáo lâu

Đối với request tạo báo cáo có bước đọc DB và render PDF chậm, `SyncTaskExecutor` khiến request giữ luồng HTTP. Chọn executor thực sự bất đồng bộ có giới hạn sẽ hợp lý hơn, ví dụ bean minh họa trong ứng dụng Spring:

~~~java
// Đoạn minh họa bên trong class @Configuration.
@Bean("reportExecutor")
public ThreadPoolTaskExecutor reportExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(2);
    pool.setMaxPoolSize(4);
    pool.setQueueCapacity(20);
    pool.setThreadNamePrefix("report-");
    pool.setWaitForTasksToCompleteOnShutdown(true);
    return pool;
}
~~~

Trong mã trên, cần import `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor` và `org.springframework.context.annotation.Bean`. Spring quản lý vòng đời bean khi đăng ký trong context; nếu khởi tạo `new ThreadPoolTaskExecutor()` thủ công ngoài Spring thì phải gọi `initialize()` trước khi dùng và đóng tài nguyên đúng cách. Khi queue/pool đã đầy, chọn chính sách rejection và cách HTTP phản hồi phù hợp thay vì tiếp nhận vô hạn. Việc đưa task sang pool không tự khiến công việc bền vững qua restart; báo cáo quan trọng nên có job state trong DB/queue bền vững.

## 7. Khi nào nên và không nên dùng SyncTaskExecutor?

**Nên cân nhắc khi:**

1. **Test cần tính quyết định:** đưa `TaskExecutor` vào constructor và inject `SyncTaskExecutor` trong unit test để assertion chạy sau `execute` và thấy kết quả ngay, không phải chờ/lặp poll. Với test concurrency thật, phải thay bằng pool thích hợp.
2. **Thao tác nhỏ, chủ đích chạy đồng bộ:** khi abstraction `TaskExecutor` được dùng chung nhưng một nhánh cần hành vi đồng bộ, không yêu cầu tách request khỏi task.
3. **Gỡ lỗi ứng dụng:** đổi sang executor đồng bộ tại môi trường thử nghiệm giúp call stack liền mạch. Phải ghi nhận rằng timing và race trong production có thể khác.

**Không phù hợp khi:**

- Tạo PDF, gửi email, gọi API chậm hoặc xử lý hàng nghìn bản ghi mà endpoint cần trả lời ngay.
- Muốn khống chế tổng số task đồng thời bằng chính executor trong phiên bản 6.x: số task active có thể bằng số caller đang gọi.
- Muốn thực thi tuần tự toàn JVM, chạy theo lịch, retry qua restart, hay phân tán tải nhiều node.
- Muốn kiểm chứng lỗi đồng bộ dữ liệu giữa worker threads mà test lại chạy tất cả inline.

### Cách dùng trong test

Giả sử service nhận abstraction qua constructor:

~~~java
// Đoạn minh họa, class còn cần import TaskExecutor.
public final class RefreshCoordinator {
    private final TaskExecutor executor;
    private boolean finished;

    public RefreshCoordinator(TaskExecutor executor) {
        this.executor = executor;
    }

    public void refresh() {
        executor.execute(() -> finished = true);
    }

    public boolean isFinished() {
        return finished;
    }
}
~~~

Trong test chạy tuần tự cùng thread:

~~~java
RefreshCoordinator coordinator =
        new RefreshCoordinator(new SyncTaskExecutor());
coordinator.refresh();
assert coordinator.isFinished();
~~~

Đây là *đoạn minh họa trong test*, không phải hai file Java độc lập. Nếu chạy với assertion Java, cần bật `-ea`; trong JUnit, nên dùng `assertTrue(...)`. Thuộc tính `finished` không được đồng bộ cho production async executor; chính vì test dùng sync executor nên có thể bỏ sót vấn đề visibility. Để production hỗ trợ async, hãy dùng `CompletableFuture`, `CountDownLatch` hoặc biến trạng thái thread-safe phù hợp với thiết kế thay vì dựa vào field boolean thông thường.

## 8. Lỗi phổ biến và cách chẩn đoán

| Triệu chứng / nhận định | Nguyên nhân đúng | Kiểm tra hoặc xử lý |
| --- | --- | --- |
| “`@Async` vẫn chạy trên HTTP thread” | Bean được chọn là `SyncTaskExecutor`; hoặc self-invocation bypass proxy | In thread name, kiểm tra tên executor trong `@Async` và đường gọi qua proxy |
| “`execute` trả về chậm” | Task chạy inline, caller chờ task | Đo thời gian ngay trước/sau; chuyển sang pool nếu cần nền thật |
| “Có `SyncTaskExecutor` thì chắc không có race” | Nhiều caller threads vẫn chạy task song song | Dùng latch trong test, khóa/atomic/transaction cho shared state |
| “Bắt lỗi quanh `execute` không thấy lỗi `@Async`” | Proxy / `Future` có cách xử lý lỗi riêng | Kiểm tra return type, `AsyncUncaughtExceptionHandler`, `Future.get/join` |
| “Transaction/request context trong test có nhưng production không” | Test dùng same thread, production dùng worker thread | Truyền dữ liệu rõ ràng, mở transaction trong worker khi cần |
| “Single-thread executor và SyncTaskExecutor giống nhau” | Single-thread executor có worker và queue; Sync dùng caller | Xem thread name và thứ tự trước/sau lời gọi |
| “Test qua với sync executor nên production ổn” | Test không mô phỏng lịch đồng thời, queue, shutdown, rejection | Bổ sung integration/load test trên cấu hình production |

### Checklist trước khi chọn

- API có cần trả ngay sau khi *gửi* việc không? Nếu có, `SyncTaskExecutor` không đáp ứng mục tiêu xử lý nền.
- Công việc có thể tốn bao lâu? Từng task có giữ luồng request hoặc event-loop không? Trên event-loop/reactive stack, tuyệt đối tránh chạy blocking work bằng executor đồng bộ rồi kỳ vọng không block event-loop.
- Task có dùng transaction, MDC, SecurityContext hoặc request data gắn với thread không? Nếu chuyển sang worker, quản lý propagation và ownership.
- Có yêu cầu giới hạn số task đồng thời, hàng đợi, timeout, cancellation, graceful shutdown hoặc khôi phục sau restart không? Nếu có, một executor inline đơn giản là chưa đủ.
- Test đang cố xác minh logic tuần tự hay behavior concurrency thật? Chọn executor đúng cho mục đích kiểm thử.

## 9. Câu hỏi tự kiểm tra

1. Nếu `execute` được gọi trên `http-nio-8080-exec-2`, `Thread.currentThread().getName()` bên trong `Runnable` là gì? Dòng lệnh phía sau `execute` chạy khi nào?
2. Hai HTTP threads cùng gọi một `SyncTaskExecutor` có chạy được hai task đồng thời không? Vì sao tên “sync” không có nghĩa là serial trên toàn hệ thống?
3. Trong direct `execute`, `IllegalStateException` của `Runnable` đi tới đâu? Điều này khác thế nào khi phương thức chạy qua `@Async` proxy hoặc khi dùng `CompletableFuture`?
4. Vì sao `ThreadLocal` có thể nhìn thấy trong task, và vì sao vẫn cần dọn `ThreadLocal` trong `finally`?
5. Nếu test inject `SyncTaskExecutor` còn production inject `ThreadPoolTaskExecutor`, giả định nào về kết quả, transaction, chia sẻ biến hoặc lỗi có thể cần thiết kế lại?
6. Khi endpoint phải nhận yêu cầu render báo cáo rồi trả 202 ngay, bạn chọn executor và cơ chế lưu trạng thái nào?

**Ghi nhớ:** `SyncTaskExecutor` cho abstraction `TaskExecutor` nhưng thực chất chạy `Runnable.run()` **ngay trên caller thread**. Nó hữu ích cho xử lý có chủ đích đồng bộ và test quyết định, đồng thời có thể làm caller bị chặn; nó không phải thread pool, queue hay cơ chế đồng bộ toàn ứng dụng.

Tài liệu chính thức: [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) và [Spring Framework 6.2 — SyncTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/task/SyncTaskExecutor.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-selection-002">SimpleAsyncTaskExecutor tạo luồng cho từng tác vụ và không tái sử dụng luồng</a>

<details>
<summary>Xem chi tiết</summary>

# SimpleAsyncTaskExecutor: mỗi tác vụ một luồng, không tái sử dụng luồng

**SimpleAsyncTaskExecutor** là một triển khai của **TaskExecutor** trong Spring Framework. Khi nhận một tác vụ, nó khởi chạy tác vụ đó trên **một thread mới**. Nó **không duy trì pool các worker để tái sử dụng thread** cho tác vụ kế tiếp. Đây là đặc tính quyết định khi chọn bộ thực thi: cách gửi tác vụ rất đơn giản, nhưng chi phí tạo luồng và mức đồng thời có thể lớn nếu có nhiều lời gọi.

Bài học thuộc chủ đề **Spring Framework - concurrency**, nhóm **Chọn bộ thực thi**. Trọng tâm là hiểu chính xác cơ chế thread-per-task của SimpleAsyncTaskExecutor, giới hạn đồng thời, tác động vận hành, cách dùng với **@Async**, khác biệt với **ThreadPoolTaskExecutor**, và lựa chọn **virtual threads trên JDK 21+** khi phù hợp.

## 1. TaskExecutor là hợp đồng; không đồng nghĩa với thread pool

Spring định nghĩa **TaskExecutor** để chạy một **Runnable**. Vì giao diện này mở rộng hợp đồng **java.util.concurrent.Executor**, lời gọi cốt lõi là:

~~~java
void execute(Runnable task);
~~~

Điểm quan trọng: giao diện nói **gửi tác vụ để thực thi**, chứ không quy định phải có pool. Hai implementation có thể nhận cùng Runnable nhưng điều phối hoàn toàn khác:

| Bộ thực thi | Nơi tác vụ chạy | Tái sử dụng thread? |
| --- | --- | --- |
| **SyncTaskExecutor** | Trực tiếp trên thread gọi execute | Không tạo worker mới |
| **SimpleAsyncTaskExecutor** | Thread mới cho mỗi lần thực thi | **Không** |
| **ThreadPoolTaskExecutor** | Những worker thuộc một thread pool | **Có**, theo cấu hình pool |
| **ConcurrentTaskExecutor** | Ủy quyền cho một java.util.concurrent.Executor khác | Phụ thuộc executor được bọc |

Vì vậy, tên **Async** diễn tả việc tác vụ có thể chạy tách khỏi caller; nó **không bảo đảm** việc thực thi được giới hạn, xếp hàng, quản trị vòng đời giống một thread pool.

## 2. Cơ chế thread-per-task diễn ra thế nào?

Ở cấu hình mặc định dùng platform threads, với mỗi lần gọi **execute(task)**, SimpleAsyncTaskExecutor tạo một Thread mới và chạy task bên trong. Khi Runnable kết thúc, thread đó kết thúc. Lần execute tiếp theo sẽ dùng **một thread khác**, kể cả nếu task trước đã hoàn thành.

~~~text
Thread gọi / HTTP request
  |
  +-- execute(task A) --> tạo thread A --> chạy A --> thread A kết thúc
  |
  +-- execute(task B) --> tạo thread B --> chạy B --> thread B kết thúc
  |
  +-- execute(task C) --> tạo thread C --> chạy C --> thread C kết thúc

Không có vòng lặp worker rảnh -> lấy task tiếp theo từ hàng đợi pool.
~~~

Ba hệ quả thực tế:

1. **Thread identity của hai tác vụ không được tái sử dụng.** Nếu hai task lần lượt chạy, chúng vẫn dùng hai thread khác nhau. Đừng suy luận chỉ từ tiền tố tên thread; phải kiểm tra object thread hoặc hành vi của executor.
2. **Mặc định không có giới hạn số task đang chạy đồng thời** do SimpleAsyncTaskExecutor đặt ra. Khả năng tạo platform thread thực tế vẫn bị giới hạn bởi bộ nhớ, hệ điều hành và tài nguyên của tiến trình.
3. **Không có task queue kiểu ThreadPoolExecutor.** Tác vụ không được gửi vào hàng đợi để một worker đang rảnh xử lý sau; nếu kích hoạt giới hạn đồng thời, hành vi chờ được thực hiện ở phía **submitter** theo cơ chế throttling, như phần dưới.

Tạo một thread platform mới không phải thao tác miễn phí: cần stack và cấu trúc quản lý của JVM/OS, dẫn tới chi phí khởi tạo, chuyển ngữ cảnh và bộ nhớ. Với nhiều công việc rất ngắn, tổng chi phí luồng có thể lớn hơn chi phí của chính task. Với tác vụ I/O chặn lâu, việc tạo nhiều platform threads có thể tăng mức sử dụng bộ nhớ dù CPU không bận tương ứng.

**Không tái sử dụng luồng** khác với **không chạy song song**. Nếu nhiều task được gửi trong thời gian ngắn, chúng có thể đồng thời tồn tại trên các thread khác nhau. Số tác vụ thực sự chạy trên CPU cùng lúc vẫn phụ thuộc số core, scheduler và trạng thái chặn.

## 3. Ví dụ thực thi thật bằng Java 17 và Spring Core

Ta sẽ quan sát hai task **gửi tuần tự** vào cùng một SimpleAsyncTaskExecutor. Mỗi task ghi lại đối tượng Thread của nó. Kết quả không phụ thuộc cách JVM đặt tên luồng hoặc lập lịch CPU.

Tạo dự án Maven tối thiểu theo cấu trúc:

~~~text
simple-executor-demo/
├── pom.xml
└── src/main/java/example/ExecutorIdentityDemo.java
~~~

**pom.xml** — Spring Core 6.2.12 chạy trên Java 17+:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>simple-executor-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
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
                <version>3.5.1</version>
                <configuration>
                    <mainClass>example.ExecutorIdentityDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/example/ExecutorIdentityDemo.java**:

~~~java
package example;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

public class ExecutorIdentityDemo {
    public static void main(String[] args) throws InterruptedException {
        try (SimpleAsyncTaskExecutor executor =
                     new SimpleAsyncTaskExecutor("lesson-")) {

            AtomicReference<Thread> first = new AtomicReference<>();
            AtomicReference<Thread> second = new AtomicReference<>();

            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);

            executor.execute(() -> {
                first.set(Thread.currentThread());
                firstStarted.countDown();
            });
            firstStarted.await();

            executor.execute(() -> {
                second.set(Thread.currentThread());
                secondStarted.countDown();
            });
            secondStarted.await();

            System.out.println("Hai task đã chạy: "
                    + (first.get() != null && second.get() != null));
            System.out.println("Tái sử dụng cùng thread: "
                    + (first.get() == second.get()));
        }
    }
}
~~~

Chạy tại thư mục chứa pom.xml:

~~~bash
mvn -q compile exec:java
~~~

**Stdout của chương trình** (không tính thông điệp của Maven nếu môi trường có in thêm):

~~~text
Hai task đã chạy: true
Tái sử dụng cùng thread: false
~~~

**Giải thích:**

- **firstStarted** và **secondStarted** bảo đảm main đợi đến khi mỗi task đã lưu tham chiếu Thread; không cần ngủ một khoảng đoán trước.
- Hai lệnh **execute** dùng chung một executor, nhưng mỗi lệnh chạy trên thread mới. Phép so sánh dùng **==** kiểm tra đúng danh tính object Thread.
- **try-with-resources** dùng vì SimpleAsyncTaskExecutor trong phiên bản minh họa hỗ trợ **AutoCloseable**. Mọi task trong ví dụ đã đến điểm báo hiệu trước khi thoát scope; phần quản lý task chưa hoàn tất khi đóng executor sẽ được giải thích riêng.
- Ví dụ dựa vào **Spring Core**; không thể chỉ gọi javac lên file này mà không có thư viện Spring trong classpath. Maven quản lý dependency và biên dịch.

Nếu in **Thread.currentThread().getName()**, bạn có thể thấy các tên kiểu lesson-1, lesson-2. Đó là công cụ quan sát hữu ích, nhưng **không nên khẳng định mọi môi trường đều có cùng số thứ tự tên luồng** hoặc dùng tên làm bằng chứng duy nhất cho thread identity.

## 4. Mặc định không giới hạn đồng thời có nghĩa gì?

Theo API của SimpleAsyncTaskExecutor, giá trị mặc định của concurrency limit là **UNBOUNDED_CONCURRENCY** (giá trị -1). Ở chế độ platform thread, nếu hàng trăm lời gọi gửi task gần như đồng thời, executor có thể tạo hàng trăm thread mới; nếu hàng nghìn tác vụ chặn network, khả năng còn tạo thêm rất nhiều luồng.

Ví dụ suy luận:

~~~text
100 request tới cùng lúc
    |
    +--> mỗi request gọi execute(tác vụ chặn I/O 5 giây)
    |
    +--> có thể tồn tại gần 100 thread task trong quãng chờ đó
    |
    +--> nếu tải tăng mạnh và không kiểm soát:
          thêm stack, context switching, tài nguyên OS
~~~

Đây là sơ đồ đặc tính, **không phải cam kết một số luồng cụ thể**: tốc độ gửi việc, thời lượng task và giới hạn tài nguyên của hệ thống ảnh hưởng đến số thread tồn tại đồng thời.

Đặc biệt, **SimpleAsyncTaskExecutor không cung cấp cơ chế lưu trữ task trong hàng đợi có capacity cấu hình sẵn** như ThreadPoolTaskExecutor. Muốn bảo vệ tài nguyên, bạn phải thiết kế giới hạn đồng thời/tốc độ gửi và chính sách từ chối ở đúng tầng.

## 5. setConcurrencyLimit: giới hạn số tác vụ hoạt động, nhưng không tạo pool

Bạn có thể đặt **setConcurrencyLimit(n)** để tối đa n tác vụ được phép thực thi đồng thời. Ví dụ khai báo trong Spring:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class SimpleExecutorConfig {
    @Bean(destroyMethod = "close")
    public SimpleAsyncTaskExecutor perTaskExecutor() {
        SimpleAsyncTaskExecutor executor =
                new SimpleAsyncTaskExecutor("per-task-");
        executor.setConcurrencyLimit(8);
        executor.setTaskTerminationTimeout(5_000);
        return executor;
    }
}
~~~

Đây là **đoạn cấu hình cho ứng dụng đã có Spring Context**, không phải chương trình javac độc lập. **Giới hạn 8 chỉ là ví dụ**; phải chọn số phù hợp CPU, I/O, tài nguyên downstream và yêu cầu độ trễ. Nếu 8 task đang chạy và có task thứ 9 được gửi tới **execute**, hành vi mặc định là **chặn thread gửi tác vụ** cho tới khi có slot trống rồi tạo một thread mới để chạy task thứ 9.

~~~text
concurrencyLimit = 2

execute(A)  -> thread A chạy ----------- hoàn tất
execute(B)  -> thread B chạy ----- hoàn tất
execute(C)  -> caller phải chờ slot
                        |
                        +-- B xong -> tạo thread C mới -> C chạy

C không dùng lại thread B. Không có worker pool để lấy C từ queue.
~~~

| Cấu hình | Hành vi cốt lõi |
| --- | --- |
| Mặc định (-1) | Không throttle số task đang chạy ở phía executor |
| setConcurrencyLimit(8) | Cho tối đa 8 task đồng thời; task gửi thêm có thể khiến submitter chờ |
| setConcurrencyLimit(1) | Mỗi thời điểm chỉ cho một task hoạt động, nhưng vẫn tạo **thread mới** cho từng task |
| setConcurrencyLimit(0) | Không cho phép bắt đầu task; không phải cấu hình để dùng thông thường |
| setRejectTasksWhenLimitReached(true) | Trên Spring Framework **6.2.6+**, chọn từ chối ngay khi đầy thay vì mặc định chờ submitter |

**Phân biệt backpressure với task queue:** khi submitter bị block trong execute, luồng request web cũng có thể bị block. Điều này có thể gây tăng độ trễ hoặc làm cạn request threads khi hệ thống quá tải. Có thể chọn **từ chối sớm** và trả một phản hồi quá tải rõ ràng nếu phiên bản Spring hỗ trợ, hoặc sử dụng executor có hàng đợi hữu hạn và rejection policy được thiết kế riêng.

Nếu bật **setRejectTasksWhenLimitReached(true)**, Spring ném **TaskRejectedException** cho thao tác gửi không được chấp nhận. Người gọi phải xử lý từ chối; nó không có nghĩa task được lưu để chạy sau. Nếu dùng Spring Framework cũ hơn 6.2.6, không giả định phương thức này có sẵn.

**Quan trọng:** giới hạn ở executor chỉ quản lý số task *đang thực thi qua executor đó*, không giới hạn toàn bộ HTTP requests, DB connections, file descriptors hay lời gọi đến mọi instance ứng dụng. Các tài nguyên downstream vẫn cần giới hạn riêng.

## 6. SimpleAsyncTaskExecutor so với ThreadPoolTaskExecutor

Một quyết định thường gặp trong ứng dụng Spring là chọn giữa **mỗi task một thread** và **pool worker được tái sử dụng**.

| Tiêu chí | SimpleAsyncTaskExecutor | ThreadPoolTaskExecutor |
| --- | --- | --- |
| Luồng cho từng task | Tạo mới, kết thúc sau task | Dùng worker thuộc pool |
| Tái sử dụng thread | Không | Có |
| Chi phí khi nhiều task ngắn | Tăng vì liên tục tạo/hủy thread | Thường hiệu quả hơn nhờ tái sử dụng |
| Điều khiển đồng thời | concurrencyLimit; mặc định không giới hạn | corePoolSize, maxPoolSize, queueCapacity |
| Lưu task chờ trong queue của pool | Không có queue tương đương | Có thể có queue được cấu hình |
| Khi quá tải | Mặc định block submitter với limit; phiên bản mới hỗ trợ reject ngay | Phụ thuộc cấu hình queue, pool và rejection |
| Shutdown/lifecycle | Có API close và tùy chọn chờ task, không tham gia phối hợp lifecycle như pool | Có điều khiển lifecycle/pause/shutdown đa dạng hơn |
| Công việc phù hợp | Dùng có chủ đích cho task-per-thread, số lượng vừa phải, hoặc virtual-thread mode | Phù hợp rộng hơn cho khối lượng lớn và thường xuyên trên platform threads |

Ví dụ một pool có giới hạn:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PoolConfig {
    @Bean("boundedPool")
    public ThreadPoolTaskExecutor boundedPool() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(40);
        pool.setThreadNamePrefix("pool-");
        return pool;
    }
}
~~~

Đây là **đoạn cấu hình Spring** để so sánh, không phải kết quả của một phép thử hiệu năng. Khi `ThreadPoolTaskExecutor` được tạo như một Spring bean, container tự gọi `initialize()` trong lifecycle; gọi thủ công ở đây có thể khởi tạo pool hai lần và không cần thiết. Với ThreadPoolTaskExecutor, hàng đợi ảnh hưởng đến thời điểm pool tạo thêm worker trên core size. Không thể chỉ nhìn maxPoolSize mà kết luận luôn có 8 worker cho mọi tải.

**Tài nguyên không giống năng lực xử lý:** dùng nhiều threads hơn số core không làm tính toán CPU-bound nhanh lên vô hạn. Với I/O-bound, thêm concurrency có thể giúp che thời gian chờ, nhưng phải cân bằng connection pool, rate limit và timeout.

## 7. Tùy chọn virtual threads trên Java 21+

Từ Spring Framework **6.1**, SimpleAsyncTaskExecutor có khả năng dùng **virtual threads của JDK 21** qua:

~~~java
SimpleAsyncTaskExecutor executor =
        new SimpleAsyncTaskExecutor("virtual-task-");
executor.setVirtualThreads(true);
~~~

Đây là **đoạn mã cấu hình cho JDK 21+ và Spring Framework phiên bản hỗ trợ**. Nếu dùng Java 17, không bật tùy chọn này.

**Điểm không thay đổi:** vẫn là mô hình **một virtual thread mới cho mỗi tác vụ**, không có pool tái sử dụng virtual threads. Điều thay đổi là chi phí mỗi luồng: virtual threads được JVM quản lý nhẹ hơn platform threads đối với nhiều tác vụ chặn I/O theo mô hình hỗ trợ. Nói “một thread cho mỗi task” không đồng nghĩa “mỗi task chiếm một platform thread suốt vòng đời” khi chạy chế độ virtual.

**Điểm cần giữ giới hạn:**

- Virtual thread nhẹ **không làm DB connection, RAM, file descriptor hoặc external API quota trở nên vô hạn**.
- Với CPU-bound, số core vẫn là giới hạn thực thi song song.
- Nếu task giữ khóa, blocking không hỗ trợ hoặc gặp tình huống pinning/mounting đặc thù, lợi ích có thể thấp hơn kỳ vọng; cần đo đạc trên runtime thực tế.
- Có thể vẫn cần **setConcurrencyLimit** hoặc semaphore để bảo vệ tài nguyên downstream. Virtual thread tự nó không phải rate limiter.
- Không thể sử dụng platform thread pool sizing như một công thức áp thẳng cho virtual-thread-per-task. Quyết định phải dựa vào loại tải và các tài nguyên thực sự bị giữ.

**Ví dụ định hướng lựa chọn:** một ứng dụng Java 21 có hàng nghìn tác vụ I/O độc lập ngắn hoặc vừa có thể cân nhắc virtual threads; một hệ thống có CPU nặng nên kiểm soát mức thực thi theo CPU thay vì tăng số task chạy vô hạn.

## 8. Sử dụng cụ thể với @Async trong Spring

**@Async** đánh dấu phương thức có thể được Spring gọi qua một executor thay vì chạy hoàn toàn trên thread của caller. Bạn có thể chọn executor có tên để tránh phụ thuộc vào cấu hình mặc định của ứng dụng.

Cấu hình Spring (giả sử ứng dụng có Spring Context và dependency cho @Async):

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfiguration {
    @Bean(name = "perTaskExecutor", destroyMethod = "close")
    public SimpleAsyncTaskExecutor perTaskExecutor() {
        SimpleAsyncTaskExecutor executor =
                new SimpleAsyncTaskExecutor("async-task-");
        executor.setConcurrencyLimit(12);
        executor.setTaskTerminationTimeout(5_000);
        return executor;
    }
}
~~~

Service minh họa:

~~~java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class PriceService {
    @Async("perTaskExecutor")
    public CompletableFuture<Integer> calculate(int basePrice) {
        int result = basePrice + 10;
        return CompletableFuture.completedFuture(result);
    }
}
~~~

Khi **một bean khác** gọi **priceService.calculate(100)** qua Spring proxy, lời gọi thực thi trên một thread do perTaskExecutor tạo. Caller nhận đối tượng tương lai để đợi hoặc nối các bước, ví dụ:

~~~java
CompletableFuture<Integer> price = priceService.calculate(100);
CompletableFuture<String> text =
        price.thenApply(value -> "Giá: " + value);
String result = text.join();
~~~

Đây là **đoạn minh họa trong một phương thức**, giả sử priceService đã được inject. Khi hoàn tất thành công, **result** có giá trị **"Giá: 110"**. Lệnh **join()** chặn nếu kết quả chưa sẵn sàng; không có bảo đảm tự động rằng caller luôn chạy tiếp mà không chờ.

Tại sao phương thức @Async trả về **completedFuture**? Phần thân phương thức đã được Spring interceptor gọi bất đồng bộ trên executor; nó có thể trả về một future biểu diễn kết quả vừa tính. Spring tạo phương tiện bất đồng bộ cho caller ở ranh giới proxy. Bọc thêm **CompletableFuture.supplyAsync** bên trong phương thức không cần thiết có thể khiến task nhảy thêm sang executor khác và làm phức tạp quản trị.

**Các điểm tích hợp không được nhầm:**

- **@EnableAsync** (hoặc cấu hình tương đương) phải được bật.
- Trong chế độ proxy thông thường, lời gọi **this.calculate(...)** từ cùng bean bỏ qua interceptor; không chắc được chuyển sang thread mới.
- **@Async("perTaskExecutor")** chọn executor tường minh; không giả định mọi ứng dụng Spring Boot đều mặc định dùng SimpleAsyncTaskExecutor. Cách resolve executor phụ thuộc Spring/Boot config và bean hiện có.
- **@Async void** không cho caller một future để lấy ngoại lệ; xem cơ chế **AsyncUncaughtExceptionHandler** nếu cần. Với **Future/CompletableFuture**, caller có thể theo dõi thành công hoặc lỗi.
- Cần tính đến **rejection và blocking ngay tại thời điểm gửi task** nếu concurrency limit đã đạt, dù tên phương thức là @Async.

## 9. Luồng mới và dữ liệu theo thread: cẩn thận với ngữ cảnh

Các giá trị như **MDC logging**, **SecurityContext**, transaction hoặc request attributes thường có liên hệ với thread hiện tại. Một thread mới của SimpleAsyncTaskExecutor **không mặc nhiên được bảo đảm** có đầy đủ những giá trị từ caller theo đúng ý nghĩa request.

Ví dụ một lời gọi HTTP gắn requestId vào log MDC. Nếu task chạy ở thread mới, log trong task có thể mất requestId nếu không có cơ chế chuyển ngữ cảnh. Không nên xử lý bằng cách nhét dữ liệu request vào field singleton để “thread nào cũng đọc được”: như vậy lại gây rò rỉ dữ liệu liên request.

Thay vào đó:

1. **Truyền dữ liệu nghiệp vụ** cần thiết qua tham số hoặc immutable request snapshot.
2. Dùng cơ chế **TaskDecorator** của Spring cho nhu cầu logging/tracing context phù hợp. Wrapper phải sao chép và dọn ngữ cảnh theo phạm vi công việc rõ ràng; đặc biệt không tự ý chuyển toàn bộ ngữ cảnh bảo mật.
3. Nếu cần chuyển **SecurityContext**, sử dụng lớp tích hợp Spring Security phù hợp với mô hình phân quyền và vòng đời task.
4. Không giả định một transaction đang mở ở caller sẽ tự chuyển theo thread; định nghĩa transaction boundary tại nơi thực thi thực tế.

**Không tái sử dụng thread** có thể giảm một kiểu lỗi “thread pool dùng lại thread với ThreadLocal cũ”, nhưng **không giải quyết bài toán context propagation** và không thay cho cleanup đúng cách. Dữ liệu request vẫn phải có owner rõ ràng.

## 10. Quản lý ngoại lệ của task

Với SimpleAsyncTaskExecutor:

- Nếu chạy qua **execute(Runnable)**, exception xảy ra ở task thread; caller đã trả khỏi execute thường không thể bắt exception ấy bằng try/catch bao quanh lời gọi submit sau khi task bắt đầu. Cần logging/error handling phù hợp với môi trường.
- Nếu chạy qua API **submit(Callable/T)** có Future, caller có thể lấy kết quả bằng get() và xử lý ExecutionException. Một Future không biến task thành synchronous; điểm get có thể block.
- Với **@Async trả CompletableFuture**, caller có thể dùng **exceptionally**, **handle**, **whenComplete** để xử lý hoặc quan sát lỗi theo ngữ nghĩa CompletableFuture.
- Nếu cấu hình giới hạn và executor **từ chối tại lúc gửi**, đó là lỗi **submission** chứ không phải lỗi chạy ở worker. Cần phân biệt hai đường lỗi này khi trả response và ghi log.

Ví dụ kết hợp future trong phần service minh họa:

~~~java
CompletableFuture<String> safeText = priceService.calculate(100)
        .thenApply(price -> "Giá: " + price)
        .exceptionally(error -> "Tạm thời không có giá");
~~~

Đây là **đoạn minh họa** về một nhánh phục hồi sau khi future đã được trả. **Nếu việc gọi calculate đã ném lỗi ngay khi submit bị từ chối**, đoạn exceptionally chưa có future để gắn vào; cần xử lý riêng ở nơi gọi. Trên production, fallback chỉ được áp dụng khi nghiệp vụ cho phép, đồng thời lưu lại nguyên nhân.

## 11. Shutdown và vòng đời: không có pool để dọn theo cách của pool

Mỗi task tạo một thread mới và không có tập worker lâu dài để giữ lại. Vì vậy, vòng đời của SimpleAsyncTaskExecutor có những điểm riêng:

- Nó cung cấp **close()** trong các phiên bản Spring hiện đại. **Đóng executor không có nghĩa mọi task đã hoàn tất tự động với thời gian chờ vô hạn**.
- **setTaskTerminationTimeout(milliseconds)** yêu cầu thời gian chờ task kết thúc khi close. Giá trị mặc định là **0**, nghĩa là không chờ. Khi bật timeout dương, executor phải **theo dõi các thread đang hoạt động**, có thêm overhead, đáng cân nhắc khi số task cực lớn.
- Không nên coi SimpleAsyncTaskExecutor là một **pool có lifecycle start/stop phối hợp** tương tự ThreadPoolTaskExecutor. Nó không cung cấp cơ chế tạm dừng/tiếp tục worker pool; các thread đã bàn giao đang chạy không thể quản trị tập trung như worker thuộc pool.
- Một task chạy lâu không phản ứng với interrupt hoặc I/O có thể tiếp tục tồn tại ngay cả khi hệ thống muốn đóng; cần deadline và cơ chế cancellation ở tầng tác vụ/công cụ I/O.

Trong ví dụ cấu hình trước, **@Bean(destroyMethod = "close")** cho Spring gọi close khi bean bị hủy. **setTaskTerminationTimeout(5_000)** là chính sách chờ tối đa 5 giây khi đóng; nó không thay thế timeout của HTTP client hay truy vấn DB.

Nếu cần shutdown được phối hợp chặt với lifecycle ứng dụng, kiểm soát hàng đợi và chính sách từ chối, thông thường **ThreadPoolTaskExecutor** là lựa chọn linh hoạt hơn.

## 12. Chi phí, tải cao và trường hợp không nên chọn

Hãy xét task loại A và B:

**A — hàng nghìn phép tính nhỏ, chạy dưới vài mili giây.** Một SimpleAsyncTaskExecutor trên platform threads tạo luồng mới cho từng phép tính. Chi phí khởi tạo/hủy, lập lịch và context switching có thể chiếm tỷ lệ lớn so với công việc. Pool cố định có giới hạn có thể hợp lý hơn, nhưng phải đo throughput và latency.

**B — nhiều lời gọi I/O có thể chặn vài giây.** Với platform threads, mỗi task có thể giữ một thread trong lúc đợi. Nếu gửi không giới hạn, số thread phình theo tải. Virtual threads trên JDK 21 có thể cải thiện chi phí luồng cho tình huống này, nhưng request concurrency, connection pool và giới hạn dịch vụ đích vẫn có thể cạn.

**C — một số tác vụ ít, độc lập, ngắn hoặc có mục tiêu thử nghiệm rõ.** Mô hình per-task-thread đơn giản và dễ quan sát có thể đủ. Khi thành phần này làm executor cho workload lớn lâu dài, cần đánh giá lại.

### Dấu hiệu nên xem lại cấu hình

- Số threads sống tăng theo lưu lượng, kèm bộ nhớ JVM/OS hoặc native thread pressure tăng.
- Latency ở điểm gửi task tăng vì **setConcurrencyLimit** làm block submitter.
- Nhiều request thread chờ để gửi tác vụ; chờ này không nằm trong thời gian chạy task mà dễ bị bỏ qua khi đo.
- Task bị từ chối hoặc JVM khó tạo thread khi lượng yêu cầu tăng.
- Tác vụ I/O lâu không có timeout khiến thread/tài nguyên tích tụ.
- Không có cơ chế giới hạn tác vụ gọi đến database/external service.
- Việc test chỉ đo throughput trung bình, chưa đo p95/p99 latency, số task active và overload behavior.

**Tối ưu không chỉ là đổi executor.** Cần giảm blocking không cần thiết, dùng batching khi hợp lý, bảo vệ downstream, kiểm soát queue và tổng khối lượng công việc; chọn executor theo CPU-bound/I/O-bound cùng yêu cầu vận hành.

## 13. Kiểm tra và quan sát đúng đặc tính executor

Để kiểm chứng **không tái sử dụng luồng**, ví dụ ở mục 3 đã so sánh **Thread identity** của hai task gửi tuần tự. Có thể mở rộng test:

1. Gửi nhiều task, ghi lại **Thread.currentThread()** và số task bắt đầu/kết thúc.
2. Kiểm tra rằng mỗi task chạy trên thread riêng thay vì kết luận từ thứ tự log hay tên thread.
3. Với concurrency limit N, tạo N tác vụ giữ **CountDownLatch** rồi gửi task thứ N+1 từ **một submitter thread khác**. Kiểm tra caller bị chờ hoặc reject theo cấu hình; sau đó release latch để test không deadlock.
4. Với Spring context, xác nhận bean **@Async("perTaskExecutor")** thật sự được gọi qua proxy; nếu test gọi trực tiếp new Service() thì annotation không được áp dụng.
5. Test failure, overload và shutdown, không chỉ đường thành công.

Tuy nhiên, test song song dựa hoàn toàn vào **Thread.sleep** rất dễ nhiễu do scheduler. Dùng CountDownLatch/Barrier để điều phối; luôn có timeout test để phát hiện treo, và dọn executor trong finally/try-with-resources.

### Các chỉ số hữu ích

| Nhóm đo | Câu hỏi cần trả lời |
| --- | --- |
| Task submitted / started / completed | Có tác vụ nào bị tồn đọng hoặc thất bại? |
| Thời gian execute từ phía submitter | Caller có bị block do concurrencyLimit? |
| Task execution duration | Task chậm vì nghiệp vụ hay I/O? |
| Thread counts / memory | Tải có khiến platform threads tăng nguy hiểm? |
| Rejections / exceptions | Quá tải xảy ra ở submission hay worker? |
| Downstream pool / rate limit | Database, API đích có bị nghẽn trước executor? |

Không nên dựa vào một metric “active threads trong pool” không tồn tại theo kiểu pool worker của SimpleAsyncTaskExecutor. Nếu cần số task đang chạy, có thể ghi nhận bằng instrumentation hoặc wrapper rõ ràng; đừng tự mặc định API này cung cấp mọi metric của ThreadPoolTaskExecutor.

## 14. Những hiểu nhầm quan trọng

**“SimpleAsyncTaskExecutor là một thread pool đơn giản.”** Sai. Nó tạo thread mới cho từng task, không tái sử dụng worker như pool.

**“Không tái sử dụng thread nghĩa là mỗi lần chỉ chạy một tác vụ.”** Sai. Mặc định nhiều tác vụ có thể chạy đồng thời trên nhiều thread mới.

**“Đặt concurrencyLimit(10) thì luôn có sẵn 10 worker.”** Sai. Giới hạn là số task có thể đồng thời hoạt động; executor vẫn tạo thread mới cho mỗi tác vụ và không giữ 10 worker để tái sử dụng.

**“Giới hạn đầy thì tác vụ tiếp theo nằm trong queue.”** Không theo cơ chế queue của pool. Với cấu hình mặc định khi có limit, thread gửi tác vụ bị block cho tới khi có slot. Spring 6.2.6+ còn có tùy chọn từ chối ngay.

**“Gửi task bằng @Async bảo đảm request thread không bao giờ chặn.”** Sai. Dù phần thực thi task được chuyển thread, điểm submit có thể block khi executor throttle, và lời gọi get()/join() sau đó vẫn chặn.

**“Virtual threads là pool mới và không còn giới hạn tài nguyên.”** Sai. Virtual thread vẫn per-task; database connection, memory và throughput dịch vụ ngoài vẫn hữu hạn.

**“Nếu thread của task mới hoàn toàn thì không cần truyền request context.”** Sai. Thread mới càng không thể tự biết dữ liệu nghiệp vụ của caller nếu không truyền qua tham số hoặc context propagation được quản lý.

**“Đóng executor mặc định sẽ đợi tất cả task chạy xong.”** Không nên giả định. Cần đọc chính sách close/termination timeout của phiên bản dùng và lựa chọn rõ ràng.

## 15. Câu hỏi tự kiểm tra

1. **Task A xong trước khi gửi B. SimpleAsyncTaskExecutor có dùng lại thread A cho B không?** Không. B có thread mới.
2. **Nếu cấu hình concurrencyLimit=2 và hai task đang chạy, task thứ ba được submit như thế nào?** Mặc định caller bị chặn cho tới khi có slot; không có queue worker pool.
3. **Muốn nhiều task ngắn tái sử dụng worker, dùng gì?** Xem xét ThreadPoolTaskExecutor với core/max/queue/rejection được cấu hình đúng.
4. **Bật setVirtualThreads(true) trên Java 21 thì có dùng thread pool không?** Không. Vẫn tạo virtual thread mới cho mỗi task.
5. **Nếu @Async không hoạt động do một method trong cùng bean gọi chính mình?** Vì self-invocation bỏ qua Spring proxy trong chế độ proxy thông thường, không phải do SimpleAsyncTaskExecutor không tạo thread.
6. **Exception của task và exception tại lúc submit khác nhau thế nào?** Lỗi task xảy ra sau khi worker bắt đầu; lỗi từ chối tại submission xảy ra ngay ở caller khi không nhận tác vụ.
7. **Giới hạn số task đồng thời có thay thế giới hạn DB connection không?** Không. Cần kiểm soát resource đích và các hành vi retry/timeout riêng.
8. **Làm sao xác nhận hai task không dùng cùng một thread?** Lưu và so sánh object Thread.currentThread() trong các task, thay vì chỉ nhìn log không bảo đảm thứ tự.

## 16. Cách đưa ra lựa chọn

Khi đánh giá SimpleAsyncTaskExecutor, hãy trả lời theo thứ tự:

1. **Tải là CPU-bound hay I/O-bound?** Bao nhiêu task/giây, thời gian p95/p99 mỗi task và điểm chặn chính?
2. **Có cần tái sử dụng platform threads không?** Nếu có nhiều công việc nhỏ lặp lại, pool thường có lợi.
3. **Có thể giới hạn số task active và kiểm soát submission không?** Nếu đặt limit, chấp nhận block caller hay muốn reject sớm?
4. **Java đang chạy phiên bản nào?** JDK 21+ và Spring hỗ trợ virtual threads có thể thay đổi lựa chọn, nhưng không thay đổi giới hạn downstream.
5. **Ai quản lý lifecycle, lỗi và context?** Có yêu cầu shutdown phối hợp, propagation, metrics và chính sách quá tải không?

**Kết luận:** SimpleAsyncTaskExecutor thật sự **tạo một thread mới cho từng tác vụ và không tái sử dụng thread**. Đây là đặc trưng thiết kế cần hiểu trước khi dùng, không phải lỗi của implementation. Nó phù hợp khi mô hình thread-per-task được chọn có chủ đích và mức đồng thời được kiểm soát; với tải platform-thread lớn, ngắn và lặp lại, **ThreadPoolTaskExecutor** thường hợp lý hơn. Trên Java 21+, **virtual threads** là một lựa chọn đáng đánh giá cho workload I/O, nhưng vẫn phải bảo vệ các nguồn lực hữu hạn.

**Tài liệu nên tra cứu:** Spring Framework Reference — *Task Execution and Scheduling*; Javadoc *org.springframework.core.task.SimpleAsyncTaskExecutor* và *org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor*; Java 21 documentation về virtual threads.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-selection-003">Giới hạn đồng thời của SimpleAsyncTaskExecutor</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn đồng thời của SimpleAsyncTaskExecutor

## 1. Mục tiêu và tình huống cần giải quyết

`SimpleAsyncTaskExecutor` là một triển khai `AsyncTaskExecutor` của Spring Framework. Mỗi lần nhận một tác vụ, nó tạo **một thread mới** để chạy tác vụ đó; nó **không duy trì pool để tái sử dụng thread**. Vì thế, nếu một ứng dụng nhận nhiều tác vụ chậm cùng lúc, số thread và số tác vụ đang thực thi có thể tăng nhanh. Giới hạn đồng thời (*concurrency limit*) cho phép đặt một trần trên **số lần thực thi tác vụ đang hoạt động tại một thời điểm** của chính executor này.

Ví dụ một dịch vụ chỉ chịu được tối đa 8 yêu cầu bên ngoài thực thi đồng thời. Nếu ứng dụng nhận 50 tác vụ gần như cùng lúc, không nên mặc định tạo 50 tác vụ đang chạy để gọi dịch vụ đó. Có thể cấu hình:

```java
SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("remote-");
executor.setConcurrencyLimit(8);
```

Đây là snippet cấu hình, cần có Spring Framework trên classpath. Với giới hạn 8, executor điều phối để không quá 8 tác vụ mà **nó** đang thực thi đồng thời. Tác vụ thứ 9 không mặc nhiên được đưa vào hàng đợi riêng. **Theo chính sách mặc định, thread gọi `execute(...)` bị chặn cho đến khi một suất thực thi trống.** Từ Spring Framework 6.2.6, có thể chọn từ chối ngay thay vì chặn caller.

Bài này làm rõ cơ chế giới hạn, hai chính sách khi chạm trần, cách tích hợp với `@Async`, ảnh hưởng của platform/virtual threads, khi nào nên dùng và lúc nào cần đổi sang executor có pool cùng hàng đợi.

## 2. Nắm đúng mô hình thực thi trước khi đặt limit

### 2.1. Mỗi tác vụ tạo thread riêng

Khi gọi:

```java
executor.execute(() -> handleRequest());
```

`SimpleAsyncTaskExecutor` tạo một thread mới cho tác vụ được chấp nhận. Sau khi tác vụ kết thúc, thread đó kết thúc, **không quay về pool để phục vụ tác vụ tiếp theo**. Một concurrency limit dương chỉ giới hạn số tác vụ có thể chạy đồng thời; nó **không biến executor thành thread pool**.

Phân biệt các thông số:

| Thuật ngữ | Ý nghĩa với `SimpleAsyncTaskExecutor` |
| --- | --- |
| Số tác vụ được chấp nhận | Tác vụ đã vượt qua bước giới hạn và được executor khởi chạy |
| Số tác vụ đồng thời | Số tác vụ còn đang thực thi, bị khống chế bởi `concurrencyLimit` nếu cấu hình dương |
| Số thread được tạo qua thời gian | Có thể rất lớn vì mỗi tác vụ mới lại tạo thread, kể cả khi limit nhỏ |
| Hàng đợi nội bộ | **Không có task queue kiểu `ThreadPoolExecutor`** để tích lũy công việc chờ |
| Thread gọi `execute` | Có thể **bị chặn** khi limit đầy theo cấu hình mặc định |
| Nhiều instance ứng dụng | Mỗi executor ở mỗi JVM giữ giới hạn riêng; không tạo hạn mức toàn cluster |

Đặt `setConcurrencyLimit(1)` có nghĩa là tại mỗi thời điểm chỉ có tối đa **một** tác vụ thực thi qua executor đó. Nó không có nghĩa rằng **cùng một thread** sẽ xử lý mọi tác vụ; các lần thực thi vẫn dùng những thread được tạo mới.

### 2.2. Diễn tiến khi concurrencyLimit = 2

Giả sử cả A, B và C đều mất 5 giây; A và B đã chạy:

```text
Thời điểm    Task A      Task B      Task C             Thread nộp C
t0           đang chạy   đang chạy   chưa được nhận    gọi execute(C)
t0..t5       đang chạy   đang chạy   chưa được nhận    bị chặn vì đủ 2 suất
t5           kết thúc    đang chạy   được nhận         execute(C) mới trả về
t5..         xong        đang chạy   chạy trên thread  tiếp tục việc của caller
                                    mới
```

Đây là dòng thời gian minh họa, không phải một lịch chạy được bảo đảm: A/B có thể kết thúc khác lúc, caller khác có thể chiếm suất trước, và hệ điều hành quyết định lúc thread mới được lập lịch.

Điểm mấu chốt: **cái đang chờ là lời gọi nộp tác vụ**, chứ không phải một task đã nằm sẵn trong hàng đợi executor. Chặn ở cổng nộp việc có thể tạo *backpressure* đơn giản, nhưng cũng khiến request thread, scheduled thread hoặc thread của consumer bị giữ lại.

Nếu sử dụng `@Async`, lời gọi từ bean khác được Spring gửi vào executor. Khi executor đang chạm giới hạn với chính sách chặn, **bản thân lời gọi phương thức được đánh dấu `@Async` vẫn có thể bị chặn ở bước submit**. Vì vậy, annotation không bảo đảm caller luôn trở lại ngay lập tức trong điều kiện quá tải.

## 3. Các thiết lập và giá trị biên cần hiểu

Các API dưới đây được xác nhận với Spring Framework **6.2.10**. Phần có ghi rõ “từ 6.2.6” không áp dụng cho mọi bản 6.0, 6.1 hoặc 6.2 cũ hơn.

| API / giá trị | Hành vi |
| --- | --- |
| `setConcurrencyLimit(n)` với `n > 0` | Giới hạn tối đa `n` task thực thi đồng thời trên executor này |
| `setConcurrencyLimit(1)` | Cho phép tối đa một task chạy mỗi lúc; vẫn tạo thread mới cho từng task |
| Mặc định `-1` / `UNBOUNDED_CONCURRENCY` | **Không giới hạn đồng thời**; đây là trạng thái mặc định |
| `0` / `NO_CONCURRENCY` | Tắt khả năng chấp nhận thực thi đồng thời; **không dùng 0 để biểu diễn chạy tuần tự**, hãy dùng 1 |
| `getConcurrencyLimit()` | Đọc giá trị giới hạn đã cấu hình |
| `isThrottleActive()` | Cho biết chế độ throttle có được bật, **không phải** số tác vụ đang chạy hay trạng thái đã đầy |
| `setRejectTasksWhenLimitReached(false)` | **Mặc định:** chặn thread nộp việc cho đến khi có chỗ |
| `setRejectTasksWhenLimitReached(true)` | **Từ 6.2.6:** khi đầy thì từ chối ngay bằng `TaskRejectedException` |
| `setVirtualThreads(true)` | **Từ Spring 6.1, JDK 21+:** tạo virtual thread thay cho platform thread; không tự bỏ giới hạn |
| `setTaskTerminationTimeout(ms)` | **Từ Spring 6.1:** thiết lập thời gian chờ kết thúc task khi `close()`; có chi phí theo dõi task |

**Lưu ý thay đổi cấu hình lúc runtime:** nên coi concurrency limit là cấu hình ở thời điểm tạo executor. API nền `ConcurrencyThrottleSupport` cảnh báo không đổi qua lại giữa `-1` và một giới hạn cụ thể khi đang chạy, vì trạng thái đếm concurrency có thể không nhất quán. Nếu cần đổi hạn mức động, hãy thiết kế và kiểm thử cơ chế quản trị riêng thay vì sửa tùy tiện giữa lúc nhiều task đang hoạt động.

### 3.1. Trường hợp để mặc định `-1`

Với platform threads, nhiều tác vụ dài/đợi I/O có thể tạo nhiều thread, dùng bộ nhớ stack, tài nguyên scheduler và gây áp lực lên CPU. Với virtual threads, chi phí thread thường thấp hơn nhưng **số truy vấn DB, HTTP requests, file descriptors, downstream quota, bộ nhớ ứng dụng và công việc đang chờ vẫn có giới hạn thật**.

Không có “giới hạn an toàn mặc định” áp dụng cho mọi workload. Nếu dùng `SimpleAsyncTaskExecutor` trong đường xử lý có lưu lượng lớn, phải biết mức tải dự kiến và cách hệ thống chống quá tải.

### 3.2. Giới hạn không đồng nghĩa với timeout

`setConcurrencyLimit(8)` **không** quy định tác vụ phải hoàn thành trong một thời gian nhất định và cũng **không** tự đặt thời gian chờ cho HTTP client, JDBC hoặc I/O. Nếu tám tác vụ đang chạy bị treo vô thời hạn, caller thứ chín có thể tiếp tục chờ theo chế độ blocking.

Cần phân biệt ba timeout khác nhau:

1. **Submission wait:** thời gian caller chờ được nhận vào executor khi đã đủ suất; `SimpleAsyncTaskExecutor` không cung cấp một queue-wait timeout cấu hình trực tiếp qua `setConcurrencyLimit`.
2. **Execution timeout:** thời gian tối đa mà tác vụ hoặc dependency được phép hoạt động; cần đặt ở API phù hợp như HTTP client, JDBC, hoặc cơ chế deadline/cancellation có kiểm soát.
3. **Termination timeout:** `setTaskTerminationTimeout` phục vụ **đóng executor**, không phải timeout cho mỗi task lúc đang chạy.

## 4. Hai chính sách khi chạm giới hạn: chặn caller và từ chối ngay

### 4.1. Mặc định: block người nộp việc

```java
SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("blocking-");
executor.setConcurrencyLimit(2);
// Không bật setRejectTasksWhenLimitReached(true): giữ mặc định false.
```

Nếu hai task đang chiếm hai suất, lời gọi `executor.execute(thirdTask)` sẽ chờ cho đến khi có suất trống. Điều này giúp không cho số task đang chạy vượt quá giới hạn nhưng **không tạo hàng đợi được quản lý riêng**.

Ưu điểm: đơn giản cho luồng nộp việc chấp nhận chờ, tạo sức ép ngược để giảm tốc độ submit.

Rủi ro: một HTTP request đang cần trả lời, event-loop, job scheduler, hoặc message consumer có thể bị chặn bất ngờ. Nếu task bên trong executor đang đợi một công việc được nộp lại vào **cùng executor** mà limit đã đầy, có thể xảy ra deadlock hoặc chuỗi chờ kéo dài. Không gọi `execute` theo kiểu lồng rồi chờ kết quả mà không tính công suất còn lại.

### 4.2. Từ Spring 6.2.6: reject ngay khi đầy

```java
SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("reject-");
executor.setConcurrencyLimit(2);
executor.setRejectTasksWhenLimitReached(true);
```

Khi cả hai suất đang bận, submission mới sẽ ném `org.springframework.core.task.TaskRejectedException`. Ngoại lệ này kế thừa `java.util.concurrent.RejectedExecutionException`. Caller cần có chính sách rõ: trả lỗi quá tải, yêu cầu retry sau, điều tiết đầu vào, hoặc chuyển việc sang một queue/job store đáng tin cậy tùy nghiệp vụ. **Không coi việc reject là tác vụ đã được lưu và sẽ tự chạy lại.**

Cơ chế reject này có mặt từ **Spring Framework 6.2.6**. Nếu dự án chạy Spring Framework 6.2.5 hoặc cũ hơn, không sao chép phương thức `setRejectTasksWhenLimitReached(true)` mà chưa nâng phiên bản/kiểm tra API.

### 4.3. Bảng chọn chính sách

| Yêu cầu | Gợi ý |
| --- | --- |
| Một batch producer kiểm soát được tốc độ, chấp nhận đợi | Concurrency limit dương và chính sách **block** có thể đủ |
| API HTTP cần phát hiện quá tải nhanh | Cân nhắc **reject**, xử lý lỗi tại biên HTTP; tránh giữ servlet/event-loop thread lâu |
| Muốn chờ trong một queue có kích thước, số worker ổn định | Dùng `ThreadPoolTaskExecutor` với pool/queue/rejection policy thiết kế rõ |
| Tác vụ bắt buộc tồn tại sau restart, retry có idempotency | Dùng job store, message broker, outbox hoặc hệ thống xử lý nền bền vững |
| Muốn giới hạn tổng số call đến downstream trên nhiều pod | Hạn mức/concurrency control dùng chung hoặc giới hạn tại downstream; giới hạn local không đủ |

## 5. Ví dụ chạy được: một suất thực thi và từ chối task thứ hai

Ví dụ độc lập dùng **Java 17 + Maven + Spring Framework 6.2.10**. Nó chứng minh tình huống đã có một task đang chiếm suất, task thứ hai bị từ chối ngay trong **chế độ reject**. `CountDownLatch` giữ task đầu tiên còn hoạt động để kết quả không phụ thuộc vào may rủi thread scheduling.

Tạo cây tệp:

```text
simple-executor-limit/
├── pom.xml
└── src/main/java/demo/LimitDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>simple-executor-limit</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
            <version>6.2.10</version>
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
                    <mainClass>demo.LimitDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Tệp `src/main/java/demo/LimitDemo.java`:**

```java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;

public final class LimitDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstFinished = new CountDownLatch(1);

        try (SimpleAsyncTaskExecutor executor =
                     new SimpleAsyncTaskExecutor("limited-")) {
            executor.setConcurrencyLimit(1);
            executor.setRejectTasksWhenLimitReached(true);

            executor.execute(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    firstFinished.countDown();
                }
            });

            try {
                if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("First task did not start");
                }

                System.out.println("limit=" + executor.getConcurrencyLimit());
                System.out.println("throttle=" + executor.isThrottleActive());

                boolean rejected = false;
                try {
                    executor.execute(() ->
                            System.out.println("UNEXPECTED SECOND TASK"));
                } catch (TaskRejectedException ex) {
                    rejected = true;
                }
                System.out.println("secondRejected=" + rejected);
            } finally {
                // Luôn giải phóng worker, kể cả khi phần kiểm tra thất bại.
                releaseFirst.countDown();
                if (!firstFinished.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("First task did not finish");
                }
            }

            System.out.println("firstFinished=true");
        }
    }
}
```

Tại thư mục chứa `pom.xml`, dùng JDK 17+ và Maven:

```bash
mvn -q compile exec:java
```

**Standard output từ chương trình:**

```text
limit=1
throttle=true
secondRejected=true
firstFinished=true
```

Tại sao kết quả xác định?

1. Trước khi thử submit task thứ hai, main thread đã đợi `firstStarted`: task thứ nhất đã bắt đầu và đang chờ `releaseFirst`.
2. `concurrencyLimit=1` nên không còn suất. Chính sách `setRejectTasksWhenLimitReached(true)` khiến lời gọi `execute` thứ hai ném `TaskRejectedException` ngay ở nơi submit.
3. Mệnh đề catch đặt `rejected=true`, vì vậy `UNEXPECTED SECOND TASK` không được in.
4. Khối `finally` giải phóng task thứ nhất và đợi `firstFinished`, không để ví dụ treo worker.

Đây là ví dụ **độc lập**, không cần chạy Spring Boot application context; lớp `SimpleAsyncTaskExecutor` có thể được khởi tạo trực tiếp khi đã có thư viện Spring trên classpath. Kết quả nói về **chế độ reject**; nếu bỏ dòng bật reject, lời gọi `execute` thứ hai trong main sẽ **bị chặn** trong khi task thứ nhất vẫn chờ main mở latch. Vì main không tới được `finally` để mở latch, ví dụ sẽ bị deadlock. Do đó **không xóa dòng reject và chạy nguyên ví dụ khi muốn thử blocking**.

### Nếu cần kiểm tra hành vi blocking an toàn

Dùng **một thread riêng làm submitter**, và để thread điều phối (không bị chặn) chịu trách nhiệm mở latch của task đang giữ suất:

```text
main:       submit task A -> chờ A bắt đầu -> mở submitter -> sau đó mở latch A
task A:     bắt đầu -> chờ latch -> hoàn thành, nhả suất
submitter:  execute(task B) -> bị chặn khi A giữ suất -> hết chặn -> trở lại
task B:     được chấp nhận, chạy trên thread mới
```

Cách này tránh vòng chờ “main bị chặn khi submit B; A lại đợi main mở latch”. Không dùng một phép `sleep(100)` rồi kết luận đã chứng minh điều kiện race; đồng bộ bằng latch/barrier và kiểm tra các trạng thái thực thi thực sự.

## 6. Cấu hình rõ executor cho `@Async` trong ứng dụng Spring

Để một phương thức `@Async` thực sự dùng executor vừa giới hạn, nên cấu hình bean có tên và chỉ định tên đó ở annotation. Đoạn dưới đây là **mã tích hợp trong ứng dụng Spring có cấu hình async**, không phải chương trình `javac` độc lập như mục 5.

**Cấu hình Spring Framework 6.2.10+:**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {
    @Bean("limitedIoExecutor")
    public SimpleAsyncTaskExecutor limitedIoExecutor() {
        SimpleAsyncTaskExecutor executor =
                new SimpleAsyncTaskExecutor("limited-io-");

        executor.setConcurrencyLimit(8);
        executor.setRejectTasksWhenLimitReached(true);
        return executor;
    }
}
```

**Service dùng executor này:**

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    @Async("limitedIoExecutor")
    public CompletableFuture<String> generate(String reportId) {
        // Công việc thực tế ở đây phải có timeout, logging và xử lý lỗi.
        String result = "Generated: " + reportId;
        return CompletableFuture.completedFuture(result);
    }
}
```

**Cách hoạt động:**

- Một bean khác inject `ReportService` rồi gọi `generate(...)`. Spring proxy bắt lời gọi, gửi phần thân phương thức vào executor tên `limitedIoExecutor`.
- Nếu có ít hơn 8 task đang chạy, Spring có thể submit task mới. Khi đủ 8, phương thức submit bị reject theo cấu hình và caller cần xử lý thất bại này. **Lỗi ở bước submit** khác với **lỗi xảy ra bên trong task** sau khi đã được chấp nhận.
- `@Async` trả `CompletableFuture<String>` giúp bên gọi xử lý kết quả hoặc lỗi bất đồng bộ. Nếu dùng `@Async void`, ngoại lệ trong phần thân không có future để trả về caller và cần thiết kế `AsyncUncaughtExceptionHandler`/quan sát phù hợp.
- Phải gọi service thông qua **Spring-managed proxy**. Tự gọi `this.generate(...)` trong cùng bean thường bỏ qua interception của `@Async` ở chế độ proxy mặc định.
- Nếu phương thức gọi dịch vụ thật, phải đặt timeout cho chính HTTP/DB client. Việc executor giới hạn 8 không tự đặt timeout cho tác vụ.

Không được mặc định rằng mỗi ứng dụng Spring Boot đều dùng `SimpleAsyncTaskExecutor`. Ví dụ, Spring Boot 3.5 có thể tự cấu hình `ThreadPoolTaskExecutor` khi dùng platform threads, hoặc `SimpleAsyncTaskExecutor` khi bật virtual threads trên JDK 21+ và điều kiện auto-configuration tương ứng thỏa mãn. Cấu hình tường minh với `@Async("limitedIoExecutor")` giúp tránh suy đoán executor mặc định và đảm bảo giới hạn của tác vụ này được áp dụng ở đúng bean.

## 7. Chọn giới hạn dựa trên công suất, không dựa vào số request tối đa

Một giá trị như 4, 8, 16 không thể đúng cho mọi ứng dụng. Nên đặt giới hạn dựa trên **tài nguyên khan hiếm nhất** mà các task tiêu thụ và kiểm tra thực nghiệm.

Ví dụ: dịch vụ xuất báo cáo gọi API bên ngoài, và mỗi instance ứng dụng chỉ được phép gửi tối đa 6 request đang hoạt động. Concurrency limit 6 có thể giới hạn số task chạy tại executor này; nhưng nếu mỗi task đồng thời mở thêm 3 kết nối hoặc gọi nhiều bước, số request thực tế không còn đồng nhất với số task. Khi đó, cần giới hạn ở cấp call/resource thích hợp.

Các câu hỏi trước khi chọn limit:

1. **Task chủ yếu chờ I/O hay xử lý CPU?** Tác vụ CPU-bound quá nhiều thread sẽ tranh CPU; tác vụ chờ I/O có thể cho phép concurrency cao hơn nhưng còn giới hạn kết nối.
2. **Một task sử dụng bao nhiêu tài nguyên?** Số kết nối DB, HTTP sockets, heap, request đến downstream, file descriptors, dịch vụ trả phí/quota.
3. **Dùng bao nhiêu instance?** Nếu có 4 pod, mỗi pod đặt 8, có thể có **tối đa 32 task đang chạy trên bốn executor độc lập**; giới hạn đó không tạo trần toàn hệ thống.
4. **Khi đã đủ suất thì caller sẽ ở đâu?** Nếu caller là HTTP request thread, blocking có thể làm tăng latency; reject cần phản hồi HTTP/retry rõ ràng.
5. **Mất bao lâu để một suất trống lại?** Task bị treo lâu, timeout quá lớn hoặc retry vô hạn có thể khiến toàn bộ suất bị chiếm.
6. **Cần hàng đợi hay xử lý bền vững không?** Nếu công việc cần xếp hàng, giữ được sau restart và retry, cân nhắc hệ queue/job ngoài JVM.

Có thể theo dõi active task count, thời gian chờ submit, tốc độ bị reject, p95/p99 latency, CPU, heap, HTTP connection pool và tỷ lệ lỗi downstream. `getConcurrencyLimit()` và `isThrottleActive()` chỉ cho biết **cấu hình**, không thay thế metric về saturation thực tế. Có thể dùng `TaskDecorator` hoặc instrumentation bên ngoài để đo số task đang chạy, nhưng phải bảo đảm luôn giảm bộ đếm trong `finally`.

## 8. Virtual threads và giới hạn đồng thời

Từ Spring Framework 6.1 trên **Java 21+**, `SimpleAsyncTaskExecutor` có:

```java
SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("virtual-");
executor.setVirtualThreads(true);
executor.setConcurrencyLimit(100);
```

Đây là snippet cho JDK 21+, không chạy được với JDK 17 nếu bật virtual threads. Khi sử dụng virtual threads, mỗi task vẫn có virtual thread mới; Spring không biến executor này thành pool. Virtual threads giúp giảm chi phí giữ nhiều công việc đang chờ I/O, nhưng **không tăng vô hạn năng lực của database, service downstream, CPU hay bộ nhớ**.

Giới hạn 100 trong ví dụ vẫn là trần của các task thực thi qua **executor này**, bất kể dùng virtual hay platform threads. Tránh hai kết luận sai:

- “Virtual threads rẻ nên đặt limit = -1 luôn an toàn.” Không đúng nếu task gọi downstream có quota hoặc tải memory/CPU đáng kể.
- “Đã có concurrency limit thì không cần timeout.” Không đúng; một task treo có thể giữ suất quá lâu.

Trong Spring Boot, `spring.threads.virtual.enabled=true` phụ thuộc phiên bản Spring Boot và yêu cầu JDK 21+; hãy xác nhận executor thực tế qua cấu hình/bean trước khi áp dụng giới hạn. Những thuộc tính cấu hình pool truyền thống có thể không tác động tới `SimpleAsyncTaskExecutor` khi ứng dụng dùng virtual threads.

## 9. So sánh với `ThreadPoolTaskExecutor` để chọn đúng công cụ

| Tiêu chí | `SimpleAsyncTaskExecutor` | `ThreadPoolTaskExecutor` |
| --- | --- | --- |
| Cách dùng thread | Thread mới cho mỗi tác vụ; không tái sử dụng | Worker threads trong pool, có thể tái sử dụng |
| Concurrency | `setConcurrencyLimit(n)` | `corePoolSize`, `maxPoolSize` và chính sách thực thi |
| Khi đầy | Mặc định chặn submitter; từ 6.2.6 có thể reject ngay | Thường đưa vào queue theo `queueCapacity`; khi pool/queue đạt giới hạn có rejection |
| Task queue | Không có queue quản lý riêng | Có hàng đợi được cấu hình |
| Độ phù hợp | Tác vụ ít hoặc có kiểm soát; đơn giản; thích hợp với virtual threads nếu giới hạn đúng | Lưu lượng tác vụ đáng kể, cần tái sử dụng worker và điều chỉnh queue |
| Vòng đời | Có `close()` và timeout chờ task; không có cơ chế pause/resume của pool | Quản lý lifecycle và giám sát pool phong phú hơn |

Khi chọn `ThreadPoolTaskExecutor`, đừng nghĩ `maxPoolSize` luôn là số thread đang chạy ngay khi tải tăng. Với queue có dung lượng dương, thread pool có thể ưu tiên đưa task vào queue trước khi tăng từ core lên max. Phải kiểm tra **core, max, queue và rejection policy** như một bộ.

Không thay `SimpleAsyncTaskExecutor` bằng `ThreadPoolTaskExecutor` chỉ vì thấy giới hạn thấp; chọn theo nhu cầu quản trị, tần suất task, behavior của caller khi quá tải và chi phí tạo thread. Nhưng với hệ thống sản xuất cần queue rõ ràng, số worker ổn định, metrics và shutdown có điều phối, thread pool thường hợp lý hơn.

## 10. Các lỗi thường gặp và cách sửa

### Lỗi 1: đặt limit nhưng tưởng có hàng đợi

**Hiện tượng:** request chậm khi đạt limit dù “chỉ gọi `@Async`”. **Nguyên nhân:** `SimpleAsyncTaskExecutor` mặc định chặn caller trong lúc submit, chứ không nhận task rồi xếp vào queue.

**Sửa:** chấp nhận backpressure và đo thời gian chờ nếu phù hợp; hoặc từ Spring 6.2.6 bật reject và xử lý overload; hoặc chuyển sang pool có queue nếu cần đợi bất đồng bộ trong queue.

### Lỗi 2: dùng `setConcurrencyLimit(0)` để chạy một task mỗi lần

**Sai:** 0 tương ứng `NO_CONCURRENCY`, không phải “một task”. **Sửa:** dùng 1. Với chính sách block, 0 có thể khiến submitter chờ không có suất; với reject, submission sẽ bị từ chối.

### Lỗi 3: bật `setRejectTasksWhenLimitReached(true)` nhưng không xử lý rejection

**Hiện tượng:** lỗi nộp tác vụ xuất hiện trực tiếp ở đường request/job, hoặc dữ liệu bị bỏ lỡ vì giả định task “đã vào queue”. **Sửa:** bắt lỗi tại biên phù hợp, quyết định trả HTTP 429/503, retry có giới hạn và idempotency, hoặc ghi việc vào hệ thống bền vững. Chọn HTTP status theo nguyên nhân và hợp đồng API, không áp một mã mặc định cho mọi trường hợp.

### Lỗi 4: chặn caller ngay trên event loop hoặc worker khan hiếm

**Hiện tượng:** dù task được tách bất đồng bộ, endpoint khác cũng chậm vì thread submit bị giữ. **Sửa:** tránh gọi blocking submission trên event-loop; cân nhắc reject, asynchronous queue/worker riêng hoặc kiến trúc non-blocking tương thích.

### Lỗi 5: giới hạn theo executor nhưng tưởng bảo vệ toàn cluster

**Hiện tượng:** tăng số pod khiến số call vào DB/downstream tăng vượt quota. **Sửa:** tính concurrency theo số instance và số call thực tế trong mỗi task; nếu cần trần toàn cluster thì dùng admission control/chính sách dùng chung ở tầng thích hợp.

### Lỗi 6: nhầm `setTaskTerminationTimeout` với timeout tác vụ

**Hiện tượng:** task I/O treo, không giải phóng suất dù đã cấu hình shutdown timeout. **Sửa:** đặt timeout cho từng dependency và chiến lược cancellation; shutdown timeout chỉ liên quan `close()`. Khi bật timeout đóng executor dương, Spring cần theo dõi các task, làm tăng overhead mỗi task.

### Lỗi 7: dùng `isThrottleActive()` như phép kiểm tra đã đầy

**Sai:** giá trị trả về biểu thị throttle đang bật, không cho biết số tác vụ đang chạy. **Sửa:** đo active submissions/running tasks và rejection/wait time bằng instrumentation riêng.

### Lỗi 8: đổi từ `-1` sang limit dương khi hệ thống đang hoạt động

**Rủi ro:** quá trình đếm concurrency ở trạng thái unbounded có thể không đồng nhất khi chuyển runtime, theo cảnh báo của API nền. **Sửa:** cấu hình giới hạn từ đầu, thay đổi theo quy trình có thử nghiệm và không đổi qua lại tùy tiện.

### Lỗi 9: lồng tác vụ trên executor có một suất

Một task đang giữ suất 1/1 lại gọi `execute(childTask)` vào cùng executor với chính sách block, rồi đợi child. Submit child không thể qua throttle khi parent chưa kết thúc; parent lại không thể kết thúc nếu chờ child. Đây là deadlock do quan hệ phụ thuộc. **Sửa:** tránh blocking nested submission cùng executor khan hiếm; thiết kế dependency không vòng hoặc sử dụng mô hình thực thi phù hợp.

### Lỗi 10: xem giới hạn đồng thời là cơ chế bảo đảm độ tin cậy

Executor chỉ điều phối tác vụ trong process, không lưu bền vững, không tự retry, không biến nghiệp vụ thành transaction và không chống request lặp. Với tác vụ bắt buộc hoàn thành sau restart, hãy dùng job/queue có trạng thái và chiến lược idempotency.

## 11. Cách kiểm thử cấu hình trong hệ thống thật

Để kiểm chứng ứng dụng chứ không chỉ đọc cấu hình, chuẩn bị một workload có thể chủ động giữ task rồi thả task:

1. Cấu hình limit nhỏ, ví dụ 2, trên **đúng executor bean được gọi**. Dùng `@Async("beanName")` để tránh nhầm executor mặc định.
2. Chạy đồng thời 2 task có điểm đồng bộ `CountDownLatch` để chắc chắn đang giữ hai suất. Đừng dựa hoàn toàn vào `Thread.sleep(...)` để tái hiện tình huống.
3. Từ một thread nộp việc riêng, submit task thứ ba. Với chính sách **block**, kiểm tra thread nộp việc chỉ hoàn tất sau khi đã có suất trống. Với **reject**, kiểm tra nhận `TaskRejectedException` và task thứ ba không chạy.
4. Giải phóng một task, xác nhận các tác vụ còn lại tiếp tục theo chính sách dự kiến. Dùng timeout cho test để test không treo nếu cấu hình sai.
5. Đo số task đồng thời cực đại và số thread được tạo trong nhiều lượt; với `SimpleAsyncTaskExecutor`, thread có thể được tạo lại giữa các lượt, khác với pool.
6. Thử khi task ném exception, bị timeout, shutdown và khi ứng dụng chạy nhiều instance. Kiểm tra các request/retry bị từ chối có được xử lý đúng hợp đồng nghiệp vụ.
7. Khi triển khai thực tế, giám sát p95/p99 latency của caller, số rejection, thời gian chờ submission, mức dùng DB/HTTP connections và khả năng phục hồi khi downstream chậm.

Bài test ở mục 5 kiểm chứng một trường hợp reject có tính xác định. Nó **không chứng minh** giới hạn sẽ đạt throughput mong muốn hoặc không gây nghẽn trong một hệ thống nhiều instance. Việc chọn limit cần phép đo ở tải và topology thật.

## 12. Ghi nhớ và tài liệu tham khảo

- **Không cấu hình:** `SimpleAsyncTaskExecutor` mặc định **unbounded** (`-1`), mỗi task có thread riêng.
- **Giới hạn dương:** `setConcurrencyLimit(n)` đặt trần số task chạy đồng thời trên executor đó.
- **Khi đầy:** mặc định **block caller**; từ Spring **6.2.6** có thể bật **reject ngay** bằng `setRejectTasksWhenLimitReached(true)`.
- **Không có pool/queue:** limit không tạo ra `ThreadPoolTaskExecutor`.
- **Virtual threads:** giảm chi phí thread trong một số workload nhưng không bỏ ràng buộc tài nguyên hoặc timeout.
- **Trong Spring:** xác định đúng bean executor, chính sách lỗi, số instance, latency của caller và khả năng xử lý quá tải.

Tài liệu chính thức:

1. [Spring Framework 6.2.10 Javadoc – SimpleAsyncTaskExecutor](https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/core/task/SimpleAsyncTaskExecutor.html) — hành vi tạo thread, limit, block/reject, virtual threads, shutdown.
2. [Spring Framework 6.2.14 Javadoc – ConcurrencyThrottleSupport](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/util/ConcurrencyThrottleSupport.html) — giá trị mặc định và cảnh báo về đổi limit lúc runtime.
3. [Spring Framework Reference – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) — mô hình `TaskExecutor`, `@Async`, so sánh các triển khai.
4. [Spring Boot 3.5 Reference – Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html) — executor auto-configuration và virtual threads.

Giới hạn đồng thời là **cơ chế kiểm soát số tác vụ đang thực thi**, không phải hàng đợi và không phải cam kết tốc độ. Khi chọn `SimpleAsyncTaskExecutor`, câu hỏi thiết kế quan trọng nhất là: **khi đã dùng hết mọi suất, thread nộp công việc phải đợi hay hệ thống phải từ chối ngay?**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-selection-004">ThreadPoolTaskExecutor quản lý nhóm luồng tái sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# ThreadPoolTaskExecutor quản lý nhóm luồng tái sử dụng trong Spring Framework

Một dịch vụ Spring có thể cần tạo báo cáo, gửi thông báo, xử lý tài liệu hoặc gọi dịch vụ ngoài mà không giữ thread tiếp nhận request cho tới khi công việc kết thúc. Tạo `new Thread(...)` cho từng yêu cầu không phải chiến lược tốt khi số lượng task tăng: thread nền tảng cần stack, bộ nhớ và công lập lịch; số luồng mất kiểm soát sẽ gây tranh chấp tài nguyên. **`ThreadPoolTaskExecutor`** là implementation `TaskExecutor` của Spring bao quanh một **`java.util.concurrent.ThreadPoolExecutor`**, cung cấp nhóm worker có thể tái sử dụng, hàng đợi, giới hạn số luồng, chính sách từ chối và lifecycle do Spring quản lý.

Để **chọn bộ thực thi** đúng, cần hiểu hai việc: **công việc được đưa vào pool như thế nào** và **pool tiếp nhận bao nhiêu công việc trước khi phải xếp hàng hoặc từ chối**. Chỉ gắn `@Async` lên một method không bảo đảm hiệu năng, độ tin cậy hay thread safety.

## 1. Task, worker, executor và queue: bốn vai trò

- **Task:** một việc được biểu diễn bằng `Runnable` (không có giá trị trả về trực tiếp) hoặc `Callable<T>` (có kết quả).
- **Worker thread:** luồng thực thi task. Sau khi hoàn thành, worker thường lấy task khác thay vì bị hủy và tạo lại.
- **Executor:** nhận task và điều phối thực thi theo cấu hình, cho caller có thể tiếp tục trước khi task hoàn tất.
- **Queue:** giữ các task đã nhận nhưng chưa có worker xử lý.

```text
Controller / Service / @Async proxy
                  │
                  │ submit(task)
                  ▼
         ThreadPoolTaskExecutor
                  │
                  ▼
          ThreadPoolExecutor
           ├── worker-1: Task A → Task D → Task G
           ├── worker-2: Task B → Task E
           ├── worker-3: Task C → Task F
           └── Waiting queue: [Task H] [Task I] ...
```

Mũi tên trong cùng một worker minh họa **luồng tái sử dụng để chạy nhiều task theo lượt**, không phải một task được chạy lặp. Tái sử dụng giúp giảm chi phí tạo/hủy platform thread và cho phép kiểm soát mức độ đồng thời.

**Các abstraction liên quan:**

| Khái niệm | Thành phần | Trách nhiệm |
| --- | --- | --- |
| JDK executor cơ bản | `Executor` | `execute(Runnable)` |
| JDK executor có lifecycle/Future | `ExecutorService` | `submit`, `Future`, `shutdown` |
| JDK pool | `ThreadPoolExecutor` | Thuật toán core/queue/max/rejection |
| Spring executor | `TaskExecutor` / `AsyncTaskExecutor` | Cách giao task thống nhất cho các Spring component |
| Spring pool executor | `ThreadPoolTaskExecutor` | Bean cấu hình pool và lifecycle, dùng JDK ThreadPoolExecutor |
| Spring async invocation | `@EnableAsync`, `@Async` | Proxy chuyển lời gọi method qua executor được chọn |
| Spring scheduler | `TaskScheduler`, `@Scheduled` | Kích hoạt task theo lịch |

**`@Async` không đồng nghĩa `ThreadPoolTaskExecutor`.** Annotation chỉ chọn cách chuyển lời gọi; executor có thể là implementation khác. Ngược lại, ta có thể inject `ThreadPoolTaskExecutor` và gọi `execute(...)` hoặc `submit(...)` mà không dùng `@Async`.

## 2. Thuật toán core → queue → max → rejection

Cấu hình ví dụ:

```java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(2);
```

Giả định pool vừa khởi tạo và **các task đã bắt đầu đều chưa kết thúc**. Khi task mới được gửi, `ThreadPoolExecutor` về cơ bản:

1. Nếu số worker hiện có ít hơn `corePoolSize`, tạo worker mới để xử lý task.
2. Khi đã đạt core, thử xếp task vào queue.
3. Nếu queue đầy, thử mở rộng worker nhưng không vượt `maxPoolSize`.
4. Nếu queue đầy **và** đã đạt max, chuyển cho chính sách từ chối.

| Task đến | Trạng thái | Kết quả khi task trước chưa hoàn tất |
| --- | --- | --- |
| #1 | Chưa đạt core=2 | Tạo worker #1 |
| #2 | Chưa đạt core=2 | Tạo worker #2 |
| #3 | Đã đạt core, queue còn chỗ | Chờ trong queue (1) |
| #4 | Đã đạt core, queue còn chỗ | Chờ trong queue (2) |
| #5 | Queue đầy, pool chưa đạt max=4 | Tạo worker #3 |
| #6 | Queue đầy, pool chưa đạt max=4 | Tạo worker #4 |
| #7 | Queue đầy, đã đạt max | Từ chối theo policy |

Bảng này không phải lời hứa thứ tự task sẽ chạy trong hệ thống thật. Nếu worker vừa rảnh, nó có thể lấy task đang chờ, khiến queue/pool thay đổi. Bài toán được cố định để thấy **quyết định tiếp nhận task**.

**Điểm khó nhớ nhất:** pool ưu tiên **xếp hàng sau khi đủ core**, rồi **mới tăng vượt core khi queue đầy**. Vì vậy, với `core=2, max=20, queueCapacity=10_000`, 100 task chậm thường chủ yếu nằm chờ sau hai worker; `max=20` không khiến 20 worker xuất hiện ngay.

### Tại sao pool dùng lại thread?

Một platform thread cần tài nguyên và quá trình tạo/lập lịch. Nếu mỗi task ngắn tự tạo thread, tải cao dễ tạo hàng nghìn thread. Pool giữ các worker để dùng lại, vừa giảm overhead vừa đặt giới hạn tài nguyên. Nhưng worker cũng có chi phí: quá nhiều thread làm tăng context switching và cạnh tranh CPU/heap/stack. Pool nhanh hay không còn phụ thuộc phần việc và hệ thống bên dưới, không chỉ số lượng thread.

## 3. Ý nghĩa các cấu hình

| Thuộc tính | Cơ chế | Lưu ý |
| --- | --- | --- |
| `corePoolSize` | Số worker nền tảng khi có tải | Không phải “số task chạy tối đa” |
| `maxPoolSize` | Trần số worker | Chỉ quan trọng khi queue đã đầy hoặc policy queue đặc biệt |
| `queueCapacity` | Số task chờ được giữ trong bộ nhớ | Nên đặt hữu hạn theo burst và độ trễ cho phép |
| `keepAliveSeconds` | Thời gian worker vượt core nhàn rỗi trước khi bị thu hồi | Mặc định 60 giây |
| `allowCoreThreadTimeOut` | Cho phép cả core worker rời pool khi nhàn rỗi | Mặc định `false` |
| `prestartAllCoreThreads` | Tạo sẵn core threads lúc init | Mặc định `false`, thường tạo khi có task |
| `threadNamePrefix` | Đặt tiền tố tên worker | Giúp log và thread dump dễ hiểu |
| `rejectedExecutionHandler` | Hành vi khi không nhận được task nữa | Không được bỏ qua mất task |
| `taskDecorator` | Bọc task trước khi worker chạy | Dùng truyền context/đo thời gian và cleanup |
| Shutdown flags | Cách đóng pool lúc context dừng | Có thể đợi hoặc gián đoạn task tùy cấu hình và phiên bản |

**Mặc định của bản thân `new ThreadPoolTaskExecutor()` trong Spring Framework 6.x**: `corePoolSize=1`, `maxPoolSize=Integer.MAX_VALUE`, `queueCapacity=Integer.MAX_VALUE`, `keepAliveSeconds=60` và `allowCoreThreadTimeOut=false`. Hàng đợi rất lớn có nghĩa pool thường chỉ chạy một worker ở tải thông thường; **max rất lớn không tự làm số worker tăng**.

**Phân biệt với Spring Boot auto-configuration:** Spring Boot có thể tạo `applicationTaskExecutor` theo mặc định riêng; ví dụ tài liệu Boot 3.5 mô tả một executor tự cấu hình có 8 core threads và các thuộc tính `spring.task.execution.*`. Không thể lấy default của class mới tạo bằng `new` để đoán default của bean Boot đang thực sự dùng. Khi cần ranh giới tải rõ ràng, cấu hình một bean có tên và chọn đích danh.

### 3.1. `queueCapacity > 0`: hàng chờ có sức chứa

`ThreadPoolTaskExecutor` dùng `LinkedBlockingQueue` khi queue capacity là số dương. Pool thử đưa task vào queue khi đủ core, chỉ mở rộng khi queue đầy. Hàng đợi có sức chứa quá lớn làm task tồn tại lâu trước khi chạy, vẫn chiếm bộ nhớ và có nguy cơ hết RAM.

Ví dụ `core=2, max=16, queue=1_000_000` không hợp lý chỉ vì “để không bao giờ reject”: nếu tốc độ nạp luôn lớn hơn tốc độ xử lý, backlog cứ tăng. Tính toán queue phải xét **độ trễ chờ**, kích thước từng task và tải burst.

### 3.2. `queueCapacity = 0`: bàn giao trực tiếp

`queueCapacity=0` dùng `SynchronousQueue`: không có chỗ lưu task chờ. Task được worker nhận trực tiếp hoặc executor mở thêm worker nếu chưa đạt max; nếu mọi worker bận và đã tới max, task bị từ chối. Mẫu này giúp tăng worker nhanh khi có tải, nhưng nếu max đặt cực cao có thể tạo rất nhiều platform threads.

**`queueCapacity=0` không có nghĩa executor không có queue ở nghĩa điều phối**, và không có nghĩa “không từ chối”; nó là một cơ chế handoff không lưu backlog.

### 3.3. Timeout và thread pool cố định

- Nếu `core=max=4`, pool tối đa bốn worker; task dư chờ queue, rồi bị từ chối nếu queue đầy.
- Worker **vượt core** có thể hết hạn khi rảnh `keepAliveSeconds`. Core worker thường được giữ lại.
- `setAllowCoreThreadTimeOut(true)` cho phép core worker cũng hết hạn khi rảnh (nếu cấu hình timeouts hợp lệ); hữu ích cho tải có lúc không hoạt động.
- `setPrestartAllCoreThreads(true)` có thể giảm delay khởi tạo worker của các task đầu tiên, nhưng giữ tài nguyên ngay cả trước khi có tải.

Các thông số là **chính sách capacity**, không phải giới hạn thời gian của mỗi task. Muốn task timeout cần cơ chế riêng phù hợp, ví dụ client timeout, deadline hoặc hủy task có kiểm soát.

## 4. Java 17+ runnable: thấy rõ worker reuse và queue capacity

`ThreadPoolTaskExecutor` bọc pool JDK nên có thể kiểm chứng hành vi lõi mà không cần Spring dependencies. Ví dụ sau cố ý dùng `CountDownLatch` thay vì đoán thứ tự task qua `Thread.sleep`.

Lưu toàn bộ vào **PoolBehaviorDemo.java**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PoolBehaviorDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService single = Executors.newSingleThreadExecutor(
                task -> new Thread(task, "reused-worker"));
        try {
            String a = single.submit(
                    () -> Thread.currentThread().getName()).get();
            String b = single.submit(
                    () -> Thread.currentThread().getName()).get();
            System.out.println("same worker: " + a.equals(b));
        } finally {
            single.shutdown();
        }

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2));

        CountDownLatch started = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> jobs = new ArrayList<>();

        try {
            Runnable heldTask = () -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            };

            for (int i = 0; i < 6; i++) {
                jobs.add(pool.submit(heldTask));
            }

            started.await(); // 4 worker đang chạy và đợi release
            System.out.println("pool=" + pool.getPoolSize()
                    + ", active=" + pool.getActiveCount()
                    + ", queued=" + pool.getQueue().size());

            release.countDown();
            for (Future<?> job : jobs) {
                job.get();
            }
            System.out.println("completed=" + jobs.size());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac PoolBehaviorDemo.java
java PoolBehaviorDemo
```

**Stdout xác định khi chương trình chạy bình thường:**

```text
same worker: true
pool=4, active=4, queued=2
completed=6
```

**Giải thích tính xác định:**

1. `single` có một worker tên `reused-worker`. Task A hoàn tất qua `Future.get()` trước khi gửi task B; cả hai thực thi trên cùng thread, nên dòng đầu là `true`.
2. Pool thứ hai có core=2, max=4 và queue=2. Hai task đầu chạy, hai task kế chờ, hai task cuối kích hoạt worker #3/#4.
3. Tất cả task bắt đầu đều chờ trên `release`. `started.await()` chỉ xong sau khi bốn worker đã bắt đầu và chưa thể kết thúc, nên lúc đo có bốn worker active và hai task trong queue.
4. `release.countDown()` giải phóng các worker; hai task đang queue được nhận xử lý. `Future.get()` chờ đủ sáu kết quả rồi mới in `completed=6`.
5. `shutdownNow()` trong `finally` chỉ dùng để tránh để lại pool chạy nếu chương trình bị lỗi; luồng chạy bình thường đã hoàn tất công việc trước đó.

Đây là minh họa JDK cho **cơ chế underlying pool**, không phải ví dụ Spring container tự khởi tạo. Ví dụ Spring ở phần tiếp theo cho thấy cách khai báo bean và chọn executor bằng `@Async`.

## 5. Cấu hình ThreadPoolTaskExecutor làm Spring bean

Ví dụ dùng **Java 17+**, **Spring Boot 3.x / Spring Framework 6.x** và dependency `spring-boot-starter-web`. Đặt toàn bộ mã sau trong **src/main/java/com/example/demo/PoolApplication.java**. Đây là chương trình Spring Boot đầy đủ về cấu trúc lớp, cần build Spring dependencies; không chạy bằng `javac PoolApplication.java` trần.

```java
package com.example.demo;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableAsync
public class PoolApplication {
    public static void main(String[] args) {
        SpringApplication.run(PoolApplication.class, args);
    }

    @Bean("reportExecutor")
    ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("report-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}

@Service
class ReportService {
    @Async("reportExecutor")
    public CompletableFuture<String> generate(String id) {
        try {
            Thread.sleep(400); // Giả lập I/O blocking
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }
        System.out.println("Working on " + id
                + " using " + Thread.currentThread().getName());
        return CompletableFuture.completedFuture("report:" + id);
    }
}

@RestController
class ReportController {
    private final ReportService reports;
    private final ThreadPoolTaskExecutor executor;

    ReportController(
            ReportService reports,
            @Qualifier("reportExecutor") ThreadPoolTaskExecutor executor) {
        this.reports = reports;
        this.executor = executor;
    }

    @GetMapping("/reports/{id}")
    public CompletableFuture<String> report(@PathVariable String id) {
        return reports.generate(id);
    }

    @GetMapping("/executor/stats")
    public Map<String, Integer> stats() {
        return Map.of(
                "poolSize", executor.getPoolSize(),
                "activeCount", executor.getActiveCount(),
                "queueSize", executor.getThreadPoolExecutor()
                        .getQueue().size(),
                "maxPoolSize", executor.getMaxPoolSize());
    }
}
```

Chạy bằng build Spring Boot của dự án:

```bash
./gradlew bootRun
curl -s http://localhost:8080/reports/R7
curl -s http://localhost:8080/executor/stats
```

Trên Windows, dùng `gradlew.bat bootRun`. Sau khi Future xử lý xong, body của request `/reports/R7` là:

```text
report:R7
```

Endpoint `/executor/stats` trả JSON chứa `poolSize`, `activeCount`, `queueSize`, `maxPoolSize`. Các con số thay đổi theo tải và thời điểm đo; **không có bộ giá trị JSON cố định**. Log worker bắt đầu bằng `report-` nhưng thứ tự và hậu tố tên thread không cố định.

### 5.1. Cơ chế kết nối với `@Async`

1. `@Bean("reportExecutor")` tạo **một** executor do Spring quản lý. Spring gọi lifecycle initialization cho bean; không cần tạo executor mới trên từng request.
2. `@EnableAsync` kích hoạt hạ tầng xử lý `@Async`. Controller gọi service bean được container quản lý.
3. `@Async("reportExecutor")` yêu cầu executor mang tên `reportExecutor`, nên đường thực thi không mơ hồ nếu context có thêm executor khác.
4. Khi proxy xử lý lời gọi, task `generate` được gửi tới pool. Worker chạy `Thread.sleep` mô phỏng I/O, tạo kết quả rồi hoàn thành `CompletableFuture`.
5. Spring MVC có thể xử lý kiểu trả về `CompletableFuture` bất đồng bộ ở phía server, nhưng **HTTP client vẫn chờ nội dung response hoàn thành**. Để client gửi việc rồi nhận mã job ngay, cần API riêng (ví dụ HTTP 202, job ID và endpoint tra kết quả).
6. Việc `Thread.sleep` vẫn chiếm worker dù controller xử lý request bất đồng bộ. `@Async` không biến một I/O blocking thành non-blocking I/O.

**Lỗi proxy quan trọng:** trong chế độ proxy mặc định, `this.generate(...)` gọi từ chính `ReportService` sẽ không đi qua interceptor `@Async`. Gọi từ bean khác như controller ở ví dụ trên, hoặc bố trí dịch vụ chuyên xử lý async để lời gọi thực sự qua Spring proxy.

### 5.2. Dùng executor trực tiếp

Khi đã có bean, ngoài `@Async` bạn có thể inject `ThreadPoolTaskExecutor` và gọi `execute(Runnable)`:

```java
@Component
class DispatchService {
    private final ThreadPoolTaskExecutor executor;

    DispatchService(
            @Qualifier("reportExecutor") ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    void dispatch(Runnable task) {
        executor.execute(task);
    }
}
```

Đây là **fragment** dành cho cùng project Spring ở trên; cần import `org.springframework.stereotype.Component` và `org.springframework.beans.factory.annotation.Qualifier` nếu tách thành file riêng. `execute` không trả giá trị nghiệp vụ. Nếu cần giá trị/kết quả lỗi, dùng `submit(Callable<T>)` và xử lý `Future<T>` theo phiên bản Spring, hoặc `CompletableFuture.supplyAsync(..., executor)`. Việc gọi `get()`/`join()` ngay sau submit khiến caller chủ động chờ, làm mất lợi ích overlap.

## 6. Khi quá tải: rejection và backpressure

Với `core=2, max=4, queue=16`, nếu bốn worker đang bận và queue đủ 16 task chờ, task mới có thể bị **reject**. Đối với API `TaskExecutor`, Spring có `TaskRejectedException`, phản ánh tình trạng không nhận được task. Khi đi qua annotation/proxy, cần kiểm tra đường truyền lỗi của API cụ thể.

| JDK RejectedExecutionHandler | Hành vi | Rủi ro |
| --- | --- | --- |
| `AbortPolicy` | Ném lỗi khi submit bị từ chối | Caller phải xử lý trạng thái quá tải; thường minh bạch |
| `CallerRunsPolicy` | Caller chạy task nếu executor chưa shutdown | Tạo backpressure nhưng có thể **chặn HTTP request thread**; `@Async` có thể trở thành việc đồng bộ lúc quá tải |
| `DiscardPolicy` | Bỏ task mới mà không báo lỗi | Dễ mất công việc âm thầm |
| `DiscardOldestPolicy` | Bỏ task cũ trong queue rồi thử lại | Phá thứ tự/độ tin cậy, có thể làm mất việc |

Chọn policy theo tính chất nghiệp vụ. Không dùng discard âm thầm khi công việc có hợp đồng **phải thực hiện** như ghi sự kiện thanh toán hoặc cập nhật bắt buộc. Nếu công việc quan trọng cần bền vững, queue trong RAM không thay thế message broker/job store.

**Phân biệt rõ:**

- **Rejected:** task chưa được pool nhận.
- **Failed:** task đã chạy nhưng code ném exception.
- **Timed out:** caller hoặc dependency vượt deadline.
- **Cancelled/interrupted:** có yêu cầu hủy, nhưng hiệu lực còn tùy task có hợp tác với interrupt hay không.
- **Shutdown:** pool không nhận task mới hoặc xử lý task tồn đọng theo lifecycle.

Đếm riêng các trường hợp để vận hành. Không báo “task thành công” chỉ vì lời gọi `execute` không ném lỗi; nghĩa là executor đã **chấp nhận** task, không phải đã hoàn tất.

## 7. Chọn pool size, queue và phân vùng theo loại tải

### 7.1. Tải CPU-bound

Công việc mã hóa, xử lý ảnh, nén hoặc tính toán nặng CPU thường bị giới hạn bởi số core thực tế và CPU quota của container. Tăng platform threads vượt xa số core có thể chỉ tăng context switching và cạnh tranh tài nguyên. Có thể bắt đầu từ mức worker gần số CPU được cấp, rồi đo throughput/latency với workload thật.

### 7.2. Tải blocking I/O-bound

Công việc thường đợi HTTP/database/disk có thể sử dụng nhiều worker hơn số CPU để che thời gian đợi. Nhưng nếu DB connection pool chỉ có 12 connections mà có 100 worker cùng thực hiện JDBC, nhiều worker sẽ đợi kết nối và làm tăng memory/latency. Cần xem cả connection pool, timeouts, quota và sức chịu tải downstream.

### 7.3. Queue quyết định độ trễ chờ

Giả sử producer sinh 200 task/giây, service ổn định chỉ hoàn thành 100 task/giây; nếu tiếp tục tiếp nhận không giới hạn, backlog tăng xấp xỉ 100 task mỗi giây trong điều kiện đơn giản hóa. Sau 10 giây có thể có khoảng 1.000 task chờ. Queue lớn chỉ trì hoãn việc bộc lộ overload.

Nếu hệ thống xử lý trung bình 100 task/giây và chỉ chấp nhận queue wait khoảng 0,5 giây, **50 task có thể là một mốc thử nghiệm**, không phải công thức bảo đảm latency. Burst traffic, phân phối thời gian chạy và downstream contention có thể khiến kết quả khác. Cần load test để chọn cấu hình thực.

### 7.4. Tách pool khi cần bảo vệ SLA

Một pool riêng cho email API chậm và pool riêng cho xử lý báo cáo CPU-bound giúp tải chậm không chiếm hết worker của nghiệp vụ quan trọng:

```text
Service A → @Async("emailExecutor")  → email worker pool → external mail API
Service B → @Async("reportExecutor") → report worker pool → CPU / storage
```

Đây là cách áp dụng **bulkhead** để cô lập sự cố. Nhưng nhiều pool cũng tăng tổng thread và chi phí cấu hình, nên phải chọn theo tải thật, không mặc định mỗi method một pool.

## 8. Lifecycle: từ khởi tạo tới đóng ứng dụng

Trong ứng dụng Spring, nên tạo pool dưới dạng **bean** để container quản lý vòng đời:

- Đặt các tham số `core/max/queue` trước khi bean initialize. Với `@Bean` thông thường, Spring tự khởi tạo lifecycle; không cần gọi `initialize()` thủ công.
- Nếu tự `new ThreadPoolTaskExecutor()` ngoài container, code sử dụng phải gọi `initialize()` và bảo đảm `shutdown()` khi không dùng nữa.
- Đừng tạo bean executor riêng mỗi request; pool sinh ra để dùng chung giữa nhiều task.
- Đừng gọi `shutdown()` sau mỗi lần submit; những task sau sẽ bị từ chối.
- Khi context đóng, lựa chọn chờ/hủy task tùy flags và version Spring. `setWaitForTasksToCompleteOnShutdown(true)` yêu cầu executor tiếp tục xử lý task đang chạy và task đã ở queue trong luồng shutdown có kiểm soát.
- `setAwaitTerminationSeconds(10)` giới hạn thời gian container đợi termination ở pha shutdown; không phải bảo đảm mọi task xong trước 10 giây.
- Trong container/Kubernetes, kết hợp readiness, dừng nhận request, termination grace period và hành vi dependency shutdown. Nếu bị kill/crash đột ngột, task chỉ tồn tại trong RAM queue có thể mất.

Một phần việc quan trọng cần **ít nhất một lần thực thi** hoặc khôi phục sau restart phải có state bền vững, idempotency key và cơ chế retry/ack phù hợp. `ThreadPoolTaskExecutor` tự thân không phải durable job queue và không phối hợp across multiple application replicas.

## 9. Thread-local context, log và an toàn concurrency

Worker được **tái sử dụng** nên mỗi task mới có thể chạy trên cùng thread mà task cũ vừa sử dụng. Đây là lý do phải cẩn thận với `ThreadLocal`, MDC logging và security context.

Mặc định, data trong request thread **không tự được copy** sang worker. Tự sao chép transaction hoặc request-scoped state tùy tiện cũng nguy hiểm: Spring transaction thông thường gắn với thread, nên `@Async` không tự thừa kế transaction của caller.

### 9.1. TaskDecorator truyền MDC có cleanup

Khi dự án dùng SLF4J, có thể cấu hình `TaskDecorator` trên bean executor. Đây là **fragment cấu hình** bổ sung vào method `reportExecutor()` trước khi trả bean:

```java
executor.setTaskDecorator(task -> {
    var submitted = org.slf4j.MDC.getCopyOfContextMap();
    return () -> {
        var previous = org.slf4j.MDC.getCopyOfContextMap();
        try {
            if (submitted == null) {
                org.slf4j.MDC.clear();
            } else {
                org.slf4j.MDC.setContextMap(submitted);
            }
            task.run();
        } finally {
            if (previous == null) {
                org.slf4j.MDC.clear();
            } else {
                org.slf4j.MDC.setContextMap(previous);
            }
        }
    };
});
```

Decorator chụp MDC **khi submit**, áp vào thread worker **khi chạy**, rồi dọn và khôi phục trạng thái ban đầu trong `finally`. Nếu không cleanup, worker tái sử dụng có thể đưa correlation ID hoặc dữ liệu nhạy cảm của task trước sang task sau.

**Lưu ý ngoại lệ:** TaskDecorator bọc runnable thực thi thực tế, có thể là `FutureTask` chứ không phải runnable của user. Với task gửi bằng `submit`, exception thường được chứa trong Future; không giả định decorator chỉ cần `catch` quanh `task.run()` là nhìn thấy mọi lỗi. Quan sát `Future.get()`, `CompletableFuture.exceptionally/whenComplete` và metric lỗi riêng. Với security context, cân nhắc wrapper từ Spring Security và chỉ truyền đúng context cần thiết.

### 9.2. Pool thread-safe không làm bean tự thread-safe

Pool đảm bảo cấu trúc nội bộ của nó khi nhận/phân phối task; **nó không bảo vệ biến dùng chung trong service**. Ví dụ hai task cùng làm `count++` trên field thường của singleton có thể làm mất cập nhật; cùng ghi vào `HashMap` không đồng bộ cũng sai. Sử dụng immutable DTO, biến local, atomic types, `ConcurrentHashMap`, hoặc synchronized/lock theo invariant cần bảo vệ.

## 10. Quan sát pool bằng metric

| Chỉ số | Dùng để trả lời |
| --- | --- |
| `getPoolSize()` | Hiện có bao nhiêu worker? |
| `getActiveCount()` | Khoảng bao nhiêu worker đang bận? |
| `getThreadPoolExecutor().getQueue().size()` | Bao nhiêu task đang chờ ở thời điểm đo? |
| Queue wait time | Task mất bao lâu từ submit đến bắt đầu thực thi? |
| Task run time | Task mất bao lâu khi đang chiếm worker? |
| Rejection count | Pool từ chối bao nhiêu task? |
| Completion/failure/cancellation count | Kết quả nghiệp vụ ra sao? |
| Downstream latency/connection usage | Bottleneck có nằm ngoài executor không? |

Các getter `poolSize`, `activeCount` và `queue.size()` phản ánh các **ảnh chụp có thể thay đổi tức thì**; đừng coi ba phép đọc rời rạc là snapshot nguyên tử. Đưa metric qua Micrometer/Actuator khi dự án có hệ thống quan sát tương ứng. Theo dõi p95/p99 latency, queue wait, task rejection và memory, không chỉ CPU.

Nếu CPU thấp nhưng `activeCount` gần max và queue tăng liên tục, có thể nhiều worker đang **chờ I/O hoặc lock**, không phải thiếu lõi CPU. Nếu CPU cao và các task CPU-bound chậm dần khi tăng thread, có thể đang oversubscribe CPU.

## 11. So sánh với các executor khác

| Executor | Đặc trưng | Phù hợp khi |
| --- | --- | --- |
| `SyncTaskExecutor` | Chạy trên caller thread | Kiểm thử hoặc cần đồng bộ |
| `SimpleAsyncTaskExecutor` | Không tái sử dụng platform threads; có tùy chọn virtual threads ở phiên bản hỗ trợ | Muốn strategy tạo thread theo task, không cần pool platform-thread truyền thống |
| **`ThreadPoolTaskExecutor`** | Tái sử dụng worker, core/max/queue/rejection rõ ràng | Workload nền có giới hạn tài nguyên và cần isolation |
| `ThreadPoolTaskScheduler` | Kích hoạt theo lịch | Job `@Scheduled` hoặc trigger thời gian |
| JDK `ThreadPoolExecutor` | Pool thuần Java | Không dùng Spring hoặc cần kiểm soát mức JDK |
| Virtual-thread-per-task executor (Java 21+) | Thread ảo mới mỗi task, không phải pool platform threads tái sử dụng theo kiểu trên | Nhiều blocking I/O phù hợp virtual thread, vẫn phải giới hạn downstream |

**Virtual thread không làm CPU và DB connection vô hạn**. Dù dùng virtual threads, vẫn có thể cần semaphore, admission control và timeout. Khi bài toán là một nhóm **platform worker** phải tái sử dụng có core/max/queue rõ ràng, `ThreadPoolTaskExecutor` là lựa chọn trực tiếp.

## 12. Lỗi phổ biến

**Lỗi 1 — Chỉ tăng `maxPoolSize` rồi thắc mắc vì sao không tăng threads.** Với queue lớn, task chờ trước khi pool vượt core. Kiểm tra đồng thời core, max, queue.

**Lỗi 2 — Đặt queue quá lớn để “khỏi reject”.** Backlog và thời gian chờ có thể tăng không giới hạn thực tế, đến lúc hết bộ nhớ.

**Lỗi 3 — Đặt `queueCapacity=0` với max quá cao.** Burst tạo nhiều platform threads, gây áp lực lên tài nguyên. Chọn max phù hợp.

**Lỗi 4 — Dùng một pool chung cho I/O chậm và CPU task nhanh.** Slow tasks chiếm hết worker, làm nghẽn việc quan trọng. Tách theo tài nguyên/SLA khi cần.

**Lỗi 5 — Cho rằng `@Async` luôn chạy trên pool mong muốn.** Có thể framework chọn executor khác; chỉ rõ `@Async("beanName")` và kiểm tra proxy.

**Lỗi 6 — Tự gọi `this.asyncMethod()`.** Self-invocation ở chế độ proxy mặc định bỏ qua interceptor `@Async`.

**Lỗi 7 — `join()` ngay sau submit.** Caller tự chờ, làm mất lợi ích tiếp tục xử lý song song với task.

**Lỗi 8 — Không xử lý rejection.** Task chưa được nhận cần phản hồi retry hoặc chính sách overload; không xem nó như đã xử lý thành công.

**Lỗi 9 — Không quan sát exception từ `Future`.** Task có thể đã thất bại trong worker nhưng caller vẫn không nhận ra; cần thu thập lỗi qua Future/callback hoặc handler.

**Lỗi 10 — Đánh đồng `@Async` với non-blocking I/O.** JDBC/HTTP client blocking vẫn giữ worker trong suốt thời gian chờ.

**Lỗi 11 — Rò rỉ `ThreadLocal` giữa task.** Worker tái sử dụng có thể chứa dữ liệu của task trước; dọn context trong `finally`.

**Lỗi 12 — Không tính shutdown/restart.** Task nằm trong RAM queue không tự tồn tại sau crash. Phải có job store/broker cho yêu cầu bền vững.

## 13. Cách kiểm thử trước khi đưa vào production

Một kiểm thử pool có ý nghĩa nên dựa trên điều kiện phối hợp **có thể kiểm soát**:

1. Gửi task được giữ bởi `CountDownLatch` để đạt trạng thái worker bận và queue đầy.
2. Với core=2, max=4, queue=2, xác minh sau sáu task có 4 worker bận và 2 task chờ; task thứ bảy bị từ chối nếu chưa nhả latch.
3. Nhả latch và đợi mọi `Future` hoàn tất. Chú ý task chạy xong khác với task đã được submit.
4. Chủ động cho một task ném lỗi và xác minh error path từ Future/callback, tách với rejection.
5. Thử `CallerRunsPolicy` khi quá tải, đo xem caller bị chặn bao lâu.
6. Thử MDC nhiều correlation ID trên cùng worker; xác minh không rò rỉ giữa task.
7. Đóng Spring context khi có task pending, đánh giá graceful shutdown, timeout và tài nguyên phụ thuộc.
8. Load test với traffic thật, đo CPU, memory, queue wait, p95/p99 latency, active threads, rejections và downstream capacity.

Đừng dùng `Thread.sleep(100)` làm bằng chứng xác định task nào sẽ chạy trước; OS/JVM scheduler không bảo đảm thứ tự. Dùng latch/barrier để tạo điều kiện kiểm thử rõ ràng.

## 14. Tự kiểm tra

**Câu 1.** `core=2, max=10, queue=100` và 20 task chậm cùng đến. Vì sao pool có thể chỉ có hai worker?

**Đáp án:** Sau khi đủ core, task được đưa vào queue. Queue còn chỗ nên pool chưa cần tăng vượt core; max=10 chỉ là giới hạn trên.

**Câu 2.** `queueCapacity=0` có nghĩa không chấp nhận task?

**Đáp án:** Không. Nó dùng `SynchronousQueue`, đòi hỏi chuyển giao trực tiếp cho worker có thể nhận hoặc tạo thêm worker nếu chưa đạt max. Chỉ khi không thể nhận nữa mới reject.

**Câu 3.** Một request HTTP gọi `@Async("reportExecutor")` và trả `CompletableFuture<String>`. Client có lập tức nhận body không?

**Đáp án:** Không. Server có thể trả thread xử lý request để làm việc khác, nhưng response có kết quả chỉ hoàn tất khi Future có kết quả; muốn trả job ID ngay cần thiết kế API riêng.

**Câu 4.** `CallerRunsPolicy` có thể thay đổi điều gì ở caller?

**Đáp án:** Khi overload, caller có thể trực tiếp chạy task và bị chặn cho đến lúc task xong, tạo backpressure nhưng tăng latency hoặc làm mất tính bất đồng bộ từ góc nhìn caller.

**Câu 5.** Pool có tự bảo vệ một `HashMap` chung trong bean không?

**Đáp án:** Không. Thread pool chỉ điều phối task. Dữ liệu chia sẻ vẫn phải thread-safe.

**Câu 6.** Tại sao queue trong pool không bảo đảm task còn tồn tại sau crash?

**Đáp án:** Queue này nằm trong RAM của tiến trình, không phải persistent job store.

**Câu 7.** Tại sao log MDC cần cleanup sau khi task hoàn tất?

**Đáp án:** Thread có thể được dùng lại cho task khác; context chưa xóa có thể rò rỉ giữa hai task.

**Câu 8.** Khi nào cân nhắc executor khác thay vì `ThreadPoolTaskExecutor`?

**Đáp án:** Khi muốn chạy đồng bộ (`SyncTaskExecutor`), cần trigger thời gian (`TaskScheduler`), hoặc chọn mô hình virtual-thread-per-task cho I/O phù hợp; lựa chọn phải bám theo ràng buộc tài nguyên.

## 15. Checklist khi chọn executor cho dự án

- Phân loại task theo CPU-bound hay blocking I/O-bound, thời gian chạy, mức burst và quota downstream.
- Dùng bean executor có tên cho nhóm công việc cần isolation; không tạo pool trong mỗi request.
- Chọn **corePoolSize, maxPoolSize, queueCapacity cùng nhau** theo thuật toán core → queue → max → reject.
- Đặt queue hữu hạn và xử lý overload; đo queue wait thay vì chỉ tăng max.
- Gắn `@Async("executorName")` khi cần proxy async, hoặc inject executor để submit trực tiếp.
- Quan sát Future/exception, xử lý timeout, retry, idempotency và interruption.
- Bảo vệ shared state của bean; truyền context cần thiết và dọn `ThreadLocal` khi worker tái sử dụng.
- Thiết kế lifecycle shutdown và độ bền task; phân biệt in-memory queue với persistent job system.
- Kiểm thử tải thực tế và thay đổi cấu hình dựa trên metric, không theo phỏng đoán.

**Ghi nhớ:** `ThreadPoolTaskExecutor` nhận task, **giữ worker để tái sử dụng**, đưa task vào queue khi đã đủ core, tăng worker khi queue đầy, và từ chối khi không còn capacity. Cách cấu hình quyết định mức công việc chạy đồng thời, độ trễ chờ và phản ứng khi hệ thống quá tải.

## Tài liệu tham khảo

- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework 6.1 Javadoc — ThreadPoolTaskExecutor: https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework 6.1 Javadoc — ExecutorConfigurationSupport: https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html
- Spring Boot 3.5 — Task Execution and Scheduling: https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html
- Java SE 17 — ThreadPoolExecutor API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-selection-005">ConcurrentTaskExecutor thích nghi Executor có sẵn</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentTaskExecutor: thích nghi Executor có sẵn trong Spring Framework

## Mục tiêu và tình huống sử dụng

Khi ứng dụng đã có một bộ thực thi của Java, chẳng hạn `ExecutorService` do thư viện, cấu hình hạ tầng hoặc một thành phần khác tạo ra, ta có thể muốn sử dụng **cùng bộ thực thi ấy** trong những API của Spring yêu cầu `TaskExecutor` hoặc `AsyncTaskExecutor`. `org.springframework.scheduling.concurrent.ConcurrentTaskExecutor` là một **adapter** cho tình huống đó.

Sau bài này, bạn có thể:

1. Giải thích quan hệ giữa `java.util.concurrent.Executor`, `ExecutorService`, Spring `TaskExecutor`, `AsyncTaskExecutor` và `ConcurrentTaskExecutor`.
2. Bọc một executor JDK đang có bằng `new ConcurrentTaskExecutor(existingExecutor)` và hiểu task chạy ở đâu.
3. Dùng adapter làm bean với `@Async("springExecutor")`, hoặc gọi `execute`/`submit` trực tiếp.
4. Xác định bên nào sở hữu thread pool, hàng đợi, chính sách từ chối, shutdown và monitoring.
5. Quyết định khi nào nên dùng `ConcurrentTaskExecutor`, `TaskExecutorAdapter`, `ThreadPoolTaskExecutor` hoặc `ConcurrentTaskScheduler`.

**Ý chính:** `ConcurrentTaskExecutor` **không tạo thêm một pool** khi bạn truyền vào một executor có sẵn. Nó cung cấp một bề mặt API tương thích với Spring và **ủy quyền thực thi** xuống executor gốc. Hành vi thực tế vẫn do executor gốc quyết định.

## 1. Vấn đề mà adapter giải quyết

Giả sử một hệ thống đã cấu hình:

```java
java.util.concurrent.ExecutorService existing =
    java.util.concurrent.Executors.newFixedThreadPool(2);
```

`existing` là bộ thực thi của JDK. Một thành phần Spring có thể muốn nhận `org.springframework.core.task.AsyncTaskExecutor` để dùng thống nhất các thao tác gửi công việc và nhận `Future`. Ta có thể thích nghi:

```java
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

ConcurrentTaskExecutor springExecutor = new ConcurrentTaskExecutor(existing);
```

Hai dòng trên chỉ minh họa phần liên kết, chưa phải chương trình độc lập: cần import, xử lý lifecycle của `existing` và đóng pool khi nó không còn được sử dụng.

Dòng chảy thực tế:

```text
Caller hoặc thành phần Spring
        |
        | execute(task) / submit(callable)
        v
ConcurrentTaskExecutor                 <-- adapter dùng API của Spring
        |
        | ủy quyền sang cùng Executor đã cung cấp
        v
Existing java.util.concurrent.Executor <-- nơi quyết định scheduling,
        |                                  threads, queue, rejection
        v
Thread thực thi công việc
        |
        +----> kết thúc, Future hoàn tất hoặc báo lỗi
```

**Đừng nhầm “thích nghi interface” với “biến đổi chính sách chạy”.** Adapter không tự đổi số worker, hàng đợi, độ ưu tiên, cancellation, timeout hay shutdown của executor bên dưới. Nó cũng không bảo đảm mọi task sẽ được chạy trên thread khác caller: JDK `Executor` được phép thực thi đồng bộ.

## 2. Phân biệt các interface có liên quan

| Kiểu | Hợp đồng chính | Điều quan trọng |
| --- | --- | --- |
| JDK `Executor` | `void execute(Runnable task)` | Chỉ yêu cầu thực thi; có thể chạy ngay, xếp hàng hoặc bị từ chối. |
| JDK `ExecutorService` | Mở rộng `Executor` với `submit`, `Future`, `shutdown`, quản lý vòng đời | Có thể là thread pool, nhưng không phải lúc nào cũng là loại pool bạn tự cấu hình. |
| Spring `TaskExecutor` | Mở rộng ý tưởng `Executor` với `execute(Runnable)` | Cổng chung để Spring nhận một bộ thực thi; quy ước lỗi của Spring có `TaskRejectedException`. |
| Spring `AsyncTaskExecutor` | Mở rộng `TaskExecutor`, thêm khả năng gửi task với `Callable` và nhận `Future`/`CompletableFuture` | Giúp thành phần Spring xử lý kết quả bất đồng bộ qua interface chung. |
| Spring `ConcurrentTaskExecutor` | Adapter nhận một JDK `Executor`, đồng thời triển khai các interface thực thi của Spring | Vẫn sử dụng executor được truyền vào; có thể dùng `execute`, `submit`, cấu hình `TaskDecorator`. |
| Spring `SchedulingTaskExecutor` | Biểu lộ một số đặc điểm liên quan tới scheduling cho bên gửi task | **Không phải** `TaskScheduler`, không cấp API đặt lịch chạy theo giờ/cron. |

Với Spring Framework 6.2, `ConcurrentTaskExecutor` triển khai cả `AsyncTaskExecutor` và `SchedulingTaskExecutor`; nó cũng hỗ trợ khả năng thích nghi của `ExecutorService` và phát hiện Jakarta `ManagedExecutorService` trong môi trường tương ứng.

### `execute` khác `submit` thế nào?

`execute(Runnable)` gửi một công việc không mang giá trị trả về. Phương thức có thể trả quyền điều khiển cho caller trước khi task chạy xong nếu executor thực thi bất đồng bộ. Nếu executor từ chối ngay khi gửi, caller nhận lỗi tại thời điểm gửi.

`submit(Callable<T>)` trả `Future<T>` để người gọi có thể lấy giá trị, chờ hoặc quan sát lỗi:

```java
Future<Integer> future = springExecutor.submit(() -> 21 * 2);
Integer value = future.get(); // có thể chặn caller cho đến khi xong
```

Đây là đoạn sử dụng bên trong phương thức đã có adapter và khai báo/handle ngoại lệ `get()`. Trong một ví dụ đầy đủ ở phần sau, ta dùng `CompletableFuture` với `@Async`.

Khi delegate là `ExecutorService`, adapter có thể tận dụng khả năng `submit` của delegate. Nếu chỉ là `Executor` cơ bản, Spring có thể dùng wrapper kiểu `FutureTask` và gửi nó qua `execute`. **Có `Future` không chứng tỏ task chạy song song**: hãy xem chính sách của delegate.

## 3. Chọn ConcurrentTaskExecutor khi nào?

| Bối cảnh | Lựa chọn phù hợp | Lý do |
| --- | --- | --- |
| Đã có `Executor`/`ExecutorService` cần tái sử dụng qua API Spring | `ConcurrentTaskExecutor(existingExecutor)` | Bọc executor đã được cấu hình/điều phối mà không tạo pool mới. |
| Chỉ cần adapter tối giản cho JDK `Executor` sang Spring `AsyncTaskExecutor` | `TaskExecutorAdapter` trong `org.springframework.core.task.support` | Adapter gọn hơn, hữu ích khi không cần các khả năng tích hợp thêm của `ConcurrentTaskExecutor`. |
| Muốn Spring cấu hình và quản lý thread pool bằng các bean properties | `ThreadPoolTaskExecutor` | Có `corePoolSize`, `maxPoolSize`, `queueCapacity`, điều khiển vòng đời và monitoring phù hợp. |
| Đã có `ScheduledExecutorService` và muốn API lập lịch `TaskScheduler` | `ConcurrentTaskScheduler` | Đây là adapter cho scheduling; **không** dùng `ConcurrentTaskExecutor` thay nó. |
| Chạy trong Jakarta EE server với executor do container quản lý | `DefaultManagedTaskExecutor` hoặc adapter tới managed executor phù hợp | Sử dụng cơ chế thread management của máy chủ; tránh tạo pool không được phép. |
| Chỉ cần gắn `@Async` với một bean `Executor` JDK theo tên | Có thể dùng trực tiếp bean `Executor` đó | Spring cho phép `@Async("beanName")` tham chiếu tới bean `Executor`; bọc adapter không phải thủ tục bắt buộc. |

Vậy vì sao vẫn dùng adapter khi `@Async` chấp nhận `Executor`? Bởi một thành phần khác có thể yêu cầu **chính interface `TaskExecutor`/`AsyncTaskExecutor`**, hoặc bạn muốn xuất một bean thống nhất của Spring đồng thời giữ quyền cấu hình tài nguyên ở JDK executor. Đừng tự động bọc mọi executor hai lần nếu ứng dụng không có nhu cầu đó.

`ConcurrentTaskExecutor` có constructor nhận `Executor`, cùng `setConcurrentExecutor(...)` và `getConcurrentExecutor()`. Constructor không đối số đã bị deprecated trong các bản Spring gần đây; ưu tiên **truyền executor tường minh** để ai tạo và ai sở hữu tài nguyên đều rõ ràng.

## 4. Ví dụ hoàn chỉnh: dùng chung một ExecutorService với @Async

Ví dụ dưới đây chạy với **Java 17+**, Maven và **Spring Framework 6.2.12**. Hệ thống sở hữu một JDK `ThreadPoolExecutor` đã được cấu hình, rồi công bố nó cho Spring qua `ConcurrentTaskExecutor`.

Ta chủ động chọn queue có giới hạn nhằm thể hiện rằng **chính pool JDK** quyết định mức song song và backpressure. Mẫu này tách bean JDK executor khỏi bean adapter; trong ứng dụng thực, bean JDK có thể được cung cấp bởi module hạ tầng hoặc thành phần tích hợp khác.

Cấu trúc thư mục:

```text
concurrent-executor-demo/
├── pom.xml
└── src/main/java/example/ConcurrentExecutorDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>concurrent-executor-demo</artifactId>
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
                    <mainClass>example.ConcurrentExecutorDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/example/ConcurrentExecutorDemo.java`:

```java
package example;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

public class ConcurrentExecutorDemo {

    public record WorkResult(String value, Thread executedBy) {
    }

    public static class WorkService {
        @Async("springExecutor")
        public CompletableFuture<WorkResult> process(String input) {
            // Thân phương thức chạy trên executor đã chọn qua Spring proxy.
            WorkResult result = new WorkResult(
                "processed:" + input,
                Thread.currentThread()
            );
            return CompletableFuture.completedFuture(result);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {
        @Bean(name = "externalPool", destroyMethod = "shutdown")
        public ExecutorService externalPool() {
            AtomicInteger threadIds = new AtomicInteger();
            ThreadFactory threadFactory = task ->
                new Thread(task, "external-pool-" + threadIds.incrementAndGet());

            return new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
            );
        }

        @Bean("springExecutor")
        public ConcurrentTaskExecutor springExecutor(
                @Qualifier("externalPool") ExecutorService rawExecutor) {
            return new ConcurrentTaskExecutor(rawExecutor);
        }

        @Bean
        public WorkService workService() {
            return new WorkService();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            ExecutorService raw = context.getBean(
                "externalPool", ExecutorService.class);
            ConcurrentTaskExecutor adapted = context.getBean(
                "springExecutor", ConcurrentTaskExecutor.class);
            WorkService service = context.getBean(WorkService.class);
            Thread caller = Thread.currentThread();

            CompletableFuture<WorkResult> handle = service.process("A");
            WorkResult result = handle.join();

            System.out.println("sameDelegate="
                + (adapted.getConcurrentExecutor() == raw));
            System.out.println("workerPrefix="
                + result.executedBy().getName().startsWith("external-pool-"));
            System.out.println("differentThread="
                + (result.executedBy() != caller));
            System.out.println("value=" + result.value());
        }
    }
}
```

Từ thư mục `concurrent-executor-demo`, chạy:

```bash
mvn -q compile exec:java
```

Bốn dòng được **chương trình** in ra theo thứ tự (log của Maven hoặc thư viện, nếu có, không thuộc bốn dòng này):

```text
sameDelegate=true
workerPrefix=true
differentThread=true
value=processed:A
```

### Phân tích từng bước

1. `externalPool()` tạo một `ThreadPoolExecutor` thuộc **JDK**: tối đa hai worker, queue chứa 16 task chờ và chính sách từ chối `AbortPolicy`. Bean này được Spring giữ và đóng bằng `shutdown` khi context kết thúc.
2. `springExecutor(rawExecutor)` tạo một `ConcurrentTaskExecutor` tham chiếu **đúng executor đã có**. Phép so sánh `getConcurrentExecutor() == raw` chứng minh adapter không thay đối tượng delegate.
3. `@EnableAsync` kích hoạt xử lý `@Async` theo cơ chế proxy mặc định. `context.getBean(WorkService.class)` lấy bean được quản lý bởi Spring; lời gọi `process("A")` từ `main` đi qua proxy.
4. `@Async("springExecutor")` định danh bean executor rõ ràng. Interceptor gửi công việc qua adapter tới `externalPool`; worker JDK chạy thân `process`.
5. Worker tạo `WorkResult` cùng tên thread và trả `CompletableFuture.completedFuture(result)`. **Future hoàn tất bên trong worker** không có nghĩa phần thân phương thức chạy trên caller.
6. Caller có `handle` và gọi `join()` để lấy giá trị. Nếu tác vụ chưa xong, `join()` chặn caller; nếu đã xong, lấy ngay. Các dòng kiểm tra cho thấy delegate đồng nhất và thread thực thi khác caller trong **cấu hình cụ thể này**.
7. Khi thoát `try`, `AnnotationConfigApplicationContext.close()` kích hoạt hủy bean do container quản lý. Ví dụ đã `join()` thành công nên không còn công việc chưa hoàn tất cần chờ.

`Thread` nằm trong `WorkResult` chỉ để kiểm chứng ví dụ. Trong ứng dụng thực, response nghiệp vụ nên trả dữ liệu nghiệp vụ, không trả tham chiếu tới thread.

### Vì sao kết quả ví dụ xác định được?

Ta **không dựa vào thứ tự log giữa nhiều worker**. Chương trình chờ task duy nhất bằng `join()`, rồi mới in. Pool được tạo với `ThreadFactory` đặt tiền tố rõ ràng cho worker; `ThreadPoolExecutor` với hai worker luôn gửi công việc tới worker của pool, không chạy trực tiếp trên caller với `AbortPolicy`. Vì vậy bốn phép kiểm tra trong điều kiện chạy thành công đều cho giá trị nêu trên.

Nếu thay `AbortPolicy` bằng `CallerRunsPolicy`, khi pool quá tải một task bị từ chối có thể được chạy ngay trên thread submit. Lúc đó giả định `differentThread=true` sẽ không còn đúng với mọi task. Cần phân biệt ví dụ có kiểm soát và hành vi trong các chính sách khác.

## 5. Adapter không bảo đảm một thread khác

Hợp đồng JDK `Executor.execute()` cho phép một executor chạy task ngay trên caller. Chẳng hạn đoạn sau có thể đặt trong một phương thức Java đã có dependency `spring-context`:

```java
import java.util.concurrent.Executor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

Thread caller = Thread.currentThread();
Executor directExecutor = Runnable::run;
ConcurrentTaskExecutor adapter = new ConcurrentTaskExecutor(directExecutor);

adapter.execute(() ->
    System.out.println("sameThread="
        + (Thread.currentThread() == caller))
);
```

Kết quả:

```text
sameThread=true
```

`Runnable::run` chạy task trực tiếp. Spring adapter chỉ ủy quyền, nên không tạo thread mới. Nếu dùng executor kiểu này với `@Async`, lời gọi vẫn có thể thực thi ngay trên caller dù phương thức được gắn annotation. Tên `AsyncTaskExecutor` nói về **API có khả năng nhận task kiểu bất đồng bộ**, không phải cam kết độc lập với delegate về chính sách chạy.

Tương tự, một `ExecutorService` có một worker có thể chạy task bất đồng bộ nhưng **không chạy song song nhiều task cùng lúc**. Hàng đợi có thể tăng trong khi chỉ một worker xử lý. Muốn xác định concurrency thật, kiểm tra executor gốc, không chỉ kiểu của adapter.

## 6. Gửi task trực tiếp và quan sát kết quả

Ngoài `@Async`, các thành phần nội bộ có thể được inject một `AsyncTaskExecutor` hoặc `ConcurrentTaskExecutor` rồi gọi trực tiếp:

```java
// Đoạn bên trong phương thức đã nhận AsyncTaskExecutor executor.
executor.execute(() -> sendAuditSignal());

Future<String> answer = executor.submit(() -> fetchValue());
String value = answer.get();
```

Đoạn này là ví dụ thao tác, **không** phải chương trình Java độc lập; `sendAuditSignal()` và `fetchValue()` là hàm nghiệp vụ do ứng dụng cung cấp. `Future.get()` yêu cầu xử lý `InterruptedException` và `ExecutionException`, và có thể chặn.

Với `@Async` trả `CompletableFuture<T>`, ta thường ghép các stage:

```java
CompletableFuture<String> display = service.process("B")
    .thenApply(result -> "Result: " + result.value());
```

Đoạn trên giả định đã có `WorkService service` của ví dụ chính. `thenApply` có thể chạy ở thread hoàn tất stage hoặc thread đã quan sát stage hoàn tất; nó **không** bảo đảm chạy trên pool mới. Nếu dùng `thenApplyAsync`, xét rõ executor nào xử lý bước kế tiếp; dùng overload nhận `Executor` để tránh vô tình chuyển việc sang common pool của JDK.

### Thành công, thất bại, bị từ chối

Ba thời điểm cần phân biệt:

- **Trước khi nhận task:** `ThreadPoolExecutor` có thể từ chối bởi queue đầy hoặc đã shutdown. Khi gọi qua Spring adapter, dạng lỗi thường được dịch sang `TaskRejectedException`. Không có `Future` hợp lệ cho công việc chưa được chấp nhận.
- **Trong khi task chạy:** exception trong công việc được phản ánh qua `Future.get()` dưới `ExecutionException`, hoặc qua `CompletableFuture.join()` thường dưới `CompletionException`. Không nên coi việc `submit()` trả về thành công là nghiệp vụ đã hoàn tất.
- **Khi hủy/timeout:** `Future.cancel(...)` có thể yêu cầu hủy, nhưng sự dừng thực tế phụ thuộc task có hợp tác ngắt hay không. Timeout lúc chờ kết quả không tự buộc worker ngừng làm việc.

Với phương thức `@Async` trả `void`, không có `Future` để caller kiểm tra ngoại lệ. Nếu thật sự cần kiểu này, cấu hình `AsyncUncaughtExceptionHandler` và kênh quan sát lỗi phù hợp. Với công việc quan trọng, ưu tiên một handle kết quả và chiến lược idempotent/retry hoặc queue bền vững.

## 7. Ai cấu hình, ai sở hữu, ai đóng executor?

Câu hỏi quan trọng nhất khi dùng adapter là **đơn vị nào sở hữu delegate**.

`ConcurrentTaskExecutor` nhận executor bằng constructor hoặc setter. **Adapter không thay bạn quản lý toàn bộ lifecycle của executor được cung cấp**. Nếu pool được tạo bởi ứng dụng, ứng dụng phải có chính sách tắt pool khi dừng. Nếu pool được chia sẻ bởi nhiều thành phần, một component không được tự ý `shutdown` pool giữa lúc các component khác còn dùng. Nếu là managed executor của Jakarta EE container, việc cấp phát và shutdown thường thuộc container.

Trong ví dụ trên:

```text
ApplicationContext
  ├── externalPool : ExecutorService
  |      ├── 2 worker threads
  |      └── bounded queue(16)
  |            (Spring gọi shutdown lúc đóng context vì bean có destroyMethod)
  |
  ├── springExecutor : ConcurrentTaskExecutor
  |      └── tham chiếu externalPool, không tạo pool thứ hai
  |
  └── workService
         └── @Async("springExecutor") dùng lại externalPool
```

`shutdown()` thông thường **ngừng tiếp nhận task mới nhưng không mặc nhiên chờ tất cả task đang chạy hoàn tất**. Nếu cần shutdown có kiểm soát, nơi sở hữu pool phải định nghĩa chính sách `awaitTermination`, timeout, xử lý interruption, công việc chưa bắt đầu và dữ liệu cần bảo toàn. Ví dụ tự đóng context sau khi đã `join()` nên đơn giản hơn trường hợp production.

`ConcurrentTaskExecutor` cũng cho phép `setConcurrentExecutor(newExecutor)` để thay delegate. Việc đổi delegate chỉ áp dụng theo thời điểm gửi về sau; task đã submit vào executor cũ không vì vậy được di chuyển sang executor mới. Phải phối hợp vòng đời của cả hai pool trước khi đổi trong hệ thống đang hoạt động; tốt nhất cấu hình một lần khi khởi động trừ khi có yêu cầu vận hành rõ ràng.

### ManagedExecutorService trong Jakarta EE

Trong môi trường Jakarta EE, Spring adapter có thể nhận biết `ManagedExecutorService` và thích nghi thêm metadata của managed task. Nếu lấy executor mặc định từ JNDI, `DefaultManagedTaskExecutor` là lựa chọn chuyên dụng. Hãy tuân thủ chính sách thread của application server; đừng tự tạo `ThreadPoolExecutor` để né cơ chế quản lý luồng của server.

## 8. Cấu hình tải, hàng đợi và theo dõi phải xét ở delegate

Trong ví dụ, pool cấu hình:

```java
new ThreadPoolExecutor(
    2, 2, 0, TimeUnit.MILLISECONDS,
    new ArrayBlockingQueue<>(16),
    threadFactory,
    new ThreadPoolExecutor.AbortPolicy()
);
```

Đây là ví dụ có giới hạn rõ ràng, không phải kích thước tối ưu cho mọi hệ thống.

- **Số worker (`corePoolSize`, `maximumPoolSize`)** giới hạn concurrency của pool JDK. Nếu công việc blocking I/O, cách chọn thông số khác với công việc CPU-bound; cần đo thời gian chạy, chờ và tải thực.
- **Hàng đợi** giữ task chưa được thực thi; queue dài làm độ trễ tăng dù caller nhận `Future` nhanh. `Executors.newFixedThreadPool` tạo hàng đợi không giới hạn về số phần tử theo triển khai phổ biến; tránh coi nó tự tạo backpressure bảo vệ hệ thống.
- **Rejection policy** quyết định ứng xử khi không còn chỗ nhận. `AbortPolicy` ném lỗi; `CallerRunsPolicy` có thể ép caller thực thi task, gây chặn và phá giả định luôn khác thread; các policy bỏ task cần cân nhắc tính đúng đắn nghiệp vụ.
- **Monitoring** phải theo dõi số worker đang chạy, queue depth, thời gian chờ/chạy, số lỗi và rejection từ delegate hoặc hệ đo phù hợp. `ConcurrentTaskExecutor` không tự cung cấp mọi metric và nút cấu hình pool của `ThreadPoolTaskExecutor`.

Nếu delegate thực sự là `ThreadPoolExecutor`, các phương thức `getActiveCount()`, `getPoolSize()` và `getQueue().size()` chỉ cho ảnh chụp tức thời, có thể thay đổi ngay sau khi đọc. Không viết test giả định giá trị các metric này luôn cố định dưới concurrent load.

## 9. TaskDecorator và dữ liệu theo thread

`ConcurrentTaskExecutor.setTaskDecorator(TaskDecorator)` cho phép bọc callback trước khi thực thi, thường dùng để mang correlation ID/logging context hoặc đo thời gian. Nhưng adapter **không tự truyền** tất cả `ThreadLocal` từ caller sang worker.

Một decorator đúng phải:

1. Chụp dữ liệu cần thiết **ở phía gửi task**.
2. Đặt dữ liệu đó trong thread đang chạy task.
3. Dọn dẹp/khôi phục trạng thái trong `finally`, vì worker của pool có thể phục vụ request khác sau đó.
4. Tránh mang đối tượng request đã hết vòng đời, `SecurityContext` sai user hoặc đối tượng mutable không an toàn luồng.

Khi dùng `submit`, callback mà decorator thấy có thể là wrapper kiểu `FutureTask`, không phải `Runnable` gốc. `FutureTask` giữ lỗi để người gọi quan sát qua `get()`, nên decorator bắt lỗi đơn giản bằng `try/catch` quanh `runnable.run()` **có thể không nhìn thấy exception của task**. Thiết kế logging/metrics riêng cho kết quả `Future` nếu cần.

Tương tự, transaction gắn với thread gọi không được tự chuyển sang worker chỉ nhờ adapter hay `@Async`. Nếu cần thao tác giao dịch trong task nền, xác định ranh giới transaction ở worker thông qua cơ chế Spring phù hợp; không dựa vào transaction đang mở trên HTTP request thread.

## 10. Những nhầm lẫn thường gặp

| Nhầm lẫn | Hệ quả | Cách xử lý |
| --- | --- | --- |
| “Bọc `Executor` sẽ tạo pool mới.” | Đếm nhầm thread, resource hoặc tuning sai nơi. | Dùng `getConcurrentExecutor()` và kiểm tra delegate; adapter ủy quyền tới cùng instance. |
| “Adapter có chữ Concurrent nên chắc chắn chạy thread khác.” | `Executor` trực tiếp hoặc `CallerRunsPolicy` có thể chạy trên caller. | Đọc chính sách delegate, kiểm tra thread và tình huống overload. |
| “Dùng `@Async` là tự động chạy qua adapter.” | Chưa bật `@EnableAsync`, self-invocation hoặc chọn nhầm bean có thể làm khác kỳ vọng. | Gọi qua bean/proxy Spring, chọn `@Async("springExecutor")`, kiểm tra cấu hình. |
| “`submit()` trả Future nghĩa là task đã chạy xong.” | Xử lý dữ liệu chưa sẵn sàng hoặc bỏ sót lỗi. | Chọn callback/composition hoặc `get()`/`join()` có chủ đích. |
| “Muốn lịch cron thì dùng `ConcurrentTaskExecutor`.” | Không có API `TaskScheduler` để đặt lịch theo thời gian. | Dùng `ConcurrentTaskScheduler` hoặc `ThreadPoolTaskScheduler`. |
| “Đổi wrapper là thay luôn queue và core size.” | Tuning không có tác dụng. | Cấu hình chính executor gốc; nếu muốn Spring bean-style pool, xét `ThreadPoolTaskExecutor`. |
| “Tắt adapter sẽ tự giải quyết mọi task còn chạy.” | Task dở dang hoặc shutdown không đúng tài nguyên. | Gán ownership rõ ràng, gọi shutdown/await ở bên sở hữu delegate. |
| “Truyền được `ThreadLocal` nhờ Spring.” | Mất trace/security context hoặc rò rỉ context qua worker tái sử dụng. | Dùng decorator hoặc công cụ propagation phù hợp, khôi phục trong `finally`. |
| “Bọc `ExecutorService` là đủ để xử lý overload.” | Queue tăng, rejection gây lỗi hoặc caller bị chặn. | Giới hạn tài nguyên, đặt policy và quan sát queue/rejection. |

### Tình huống trước và sau khi thích nghi

**Trước:** module hạ tầng đã tạo `ExecutorService sharedExecutor` và có chính sách riêng. Một module Spring muốn nhận `AsyncTaskExecutor` và gọi `submit(Callable<T>)`; việc viết pool Spring mới sẽ làm phân tán cấu hình tài nguyên và chia tải khác ý định.

**Sau:** module tích hợp tạo **một** bean `ConcurrentTaskExecutor(sharedExecutor)` và inject cho thành phần Spring. Nơi cấp `sharedExecutor` vẫn quản lý số worker, queue, shutdown. Thành phần Spring gọi qua interface của mình và quan sát `Future`. Việc tích hợp có một ranh giới rõ ràng, không cần pool thứ hai.

Nếu yêu cầu thực tế chỉ là `@Async` trỏ tới bean `Executor`, có thể bỏ adapter và đặt `@Async("sharedExecutor")`: Spring hỗ trợ chọn một `Executor` bean. Chỉ duy trì adapter khi có lợi ích cụ thể về interface Spring hoặc quản lý tích hợp.

## 11. Câu hỏi tự kiểm tra

**1. `ConcurrentTaskExecutor` được tạo với `Executor direct = Runnable::run`. Gọi `execute` có chắc chạy trên thread khác không?**

Không. Delegate gọi `Runnable.run()` ngay; task chạy trên caller. Adapter không đổi chính sách thực thi.

**2. Vì sao `getConcurrentExecutor() == rawExecutor` có thể là `true`?**

Adapter lưu và sử dụng chính delegate đã truyền vào; nó không tạo thêm một thread pool để thay thế. Kết quả `true` chứng minh đồng nhất object trong ví dụ.

**3. Có cần `ConcurrentTaskExecutor` để chỉ định `@Async("myJdkExecutor")` không?**

Không nhất thiết. Tên trong `@Async` có thể tham chiếu bean `Executor`. Adapter hữu ích khi API khác cần `TaskExecutor`/`AsyncTaskExecutor` hoặc có yêu cầu tích hợp Spring cụ thể.

**4. Queue của JDK executor đầy, `AbortPolicy` từ chối task. Adapter có tự tạo worker bổ sung không?**

Không. Delegate quyết định từ chối. Spring adapter có thể biểu diễn lỗi theo hợp đồng `TaskRejectedException`; cần xử lý overload ở tầng thiết kế pool và nghiệp vụ.

**5. Một `ConcurrentTaskExecutor` có thể dùng để schedule cron không?**

Không. `SchedulingTaskExecutor` không đồng nghĩa `TaskScheduler`. Với `ScheduledExecutorService` cần API lập lịch Spring, xem `ConcurrentTaskScheduler`.

**6. Có hai executor bean, một là JDK pool và một là adapter bọc chính pool ấy. Điều này có nghĩa có bốn thread nếu pool có hai worker không?**

Không. Hai bean trỏ tới hai bề mặt API, nhưng chỉ có **một** JDK thread pool hai worker trong ví dụ. Số thread có thể thay đổi theo chính sách pool; adapter không tự nhân đôi worker.

**7. `@Async("springExecutor")` chạy trong worker, nhưng callback sau `thenApply` có nhất thiết dùng worker đó không?**

Không. `thenApply` có thể chạy trên thread hoàn tất stage hoặc thread đăng ký khi stage đã hoàn tất. Nếu cần cố định executor cho stage, dùng biến thể `...Async(..., executor)` và truyền executor theo thiết kế.

### Tóm lược

Khi chọn bộ thực thi trong Spring, hãy xác định **nơi executor đã tồn tại**, **API mà thành phần Spring cần**, **ai sở hữu pool**, **tài nguyên được giới hạn thế nào**, và **kết quả/lỗi được quan sát ra sao**. `ConcurrentTaskExecutor` có nhiệm vụ nối các interface để dùng lại đúng executor JDK đã có; quyết định về thread, queue, tính song song và lifecycle vẫn nằm ở delegate.

### Tài liệu tham khảo

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): mô hình `TaskExecutor`, các loại executor và tích hợp `@Async`.
- [Spring Framework — ConcurrentTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/concurrent/ConcurrentTaskExecutor.html): constructor, delegate, `execute`, `submit`, `setTaskDecorator`.
- [Spring Framework — TaskExecutorAdapter API](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/task/support/TaskExecutorAdapter.html): adapter cơ bản từ JDK `Executor`.
- [Java 17 — Executor API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html) và [ExecutorService API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): hợp đồng thực thi, kết quả và shutdown.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-selection-006">TaskExecutorAdapter thích nghi hợp đồng Executor của JDK</a>

<details>
<summary>Xem chi tiết</summary>

# `TaskExecutorAdapter`: thích nghi hợp đồng `Executor` của JDK trong Spring

**Module:** Spring Framework - concurrency  
**Nhóm:** Chọn bộ thực thi  
**Chủ đề:** `TaskExecutorAdapter` thích nghi hợp đồng `Executor` của JDK.

## 1. Bài toán: đã có `Executor` của JDK, muốn dùng qua API của Spring

Nhiều hệ thống Java đã có sẵn bộ thực thi công việc (`Executor` hoặc `ExecutorService`) do thư viện khác, container hoặc tầng hạ tầng cung cấp. Trong khi đó, mã Spring có thể muốn giao tiếp qua `TaskExecutor` hoặc `AsyncTaskExecutor`, gắn `TaskDecorator` và thống nhất cách báo từ chối tác vụ. **`TaskExecutorAdapter` là lớp cầu nối theo chiều JDK → Spring**.

Một cách hình dung:

```text
Mã gọi dùng Spring TaskExecutor / AsyncTaskExecutor
                      |
                      v
     TaskExecutorAdapter (adapter, không chứa worker riêng)
                      |
                      v
        JDK Executor / ExecutorService có sẵn
                      |
                      v
       Thread/queue/scheduling thật của delegate
```

Đối tượng adapter **không tự tạo thread pool, không tự quyết định số worker, không tự lưu job bền vững**. Nó chuyển lời gọi sang executor đã được truyền vào constructor. Nếu delegate chạy tác vụ ngay trên thread gọi, adapter cũng có thể chạy đồng bộ; nếu delegate có thread pool, công việc chạy theo lịch của pool đó.

Ví dụ định hướng:

```java
Executor jdkExecutor = ...; // Executor đã tồn tại trong ứng dụng.
TaskExecutorAdapter springExecutor = new TaskExecutorAdapter(jdkExecutor);
springExecutor.execute(() -> doWork());
```

Đây là **đoạn minh họa**, cần khai báo `jdkExecutor`, `doWork()` và import các kiểu tương ứng trong ứng dụng của bạn. Điểm chính là **giữ nguyên delegate**, thay đổi giao diện mà phía tiêu thụ sử dụng.

### Vì sao không dùng thẳng `Executor`?

Bạn *có thể* dùng thẳng JDK `Executor` ở nhiều nơi trong Spring: `TaskExecutor` kế thừa hợp đồng `Executor`, và nhiều điểm tích hợp Spring chấp nhận `Executor`. Adapter hữu ích khi:

- API yêu cầu một đối tượng kiểu `TaskExecutor`/`AsyncTaskExecutor` của Spring.
- Muốn `submit(Runnable)` hoặc `submit(Callable<T>)` trả `Future` kể cả khi delegate chỉ là `Executor`.
- Muốn áp dụng `TaskDecorator` ở ranh giới giao việc.
- Muốn cách báo lỗi từ chối theo `TaskRejectedException` của Spring.
- Muốn tái sử dụng một executor đã có mà chưa cần chuyển toàn bộ cấu hình sang `ThreadPoolTaskExecutor`.

Adapter là một **lựa chọn tích hợp**, không phải bước bắt buộc cho mọi trường hợp bất đồng bộ.

## 2. Các hợp đồng cần phân biệt

| Giao diện/lớp | Đóng vai trò gì? | Khả năng nổi bật |
| --- | --- | --- |
| `java.util.concurrent.Executor` | Hợp đồng JDK cơ bản | `execute(Runnable)`, không quy định phải tạo thread mới |
| `java.util.concurrent.ExecutorService` | Hợp đồng JDK mở rộng | `submit`, `Future`, `shutdown`, chờ kết thúc, các thao tác xử lý nhiều task |
| `org.springframework.core.task.TaskExecutor` | Giao diện Spring, cũng là `Executor` | `execute(Runnable)` dưới mô hình task executor của Spring |
| `org.springframework.core.task.AsyncTaskExecutor` | Giao diện Spring mở rộng `TaskExecutor` | `submit(Runnable)` / `submit(Callable<T>)` trả `Future` |
| `org.springframework.core.task.support.TaskExecutorAdapter` | Bọc JDK `Executor` | Cung cấp `AsyncTaskExecutor` của Spring bằng cách ủy quyền/bao `FutureTask`, tùy delegate |
| `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor` | Executor do Spring cấu hình và quản lý | Thread pool, queue, thread name, lifecycle, metrics/cấu hình phù hợp Spring |
| `org.springframework.core.task.support.ExecutorServiceAdapter` | Adapter **ngược chiều** | Đưa Spring `TaskExecutor` ra dạng JDK `ExecutorService`; lifecycle vẫn thuộc executor thực |

Tên `TaskExecutorAdapter` dễ khiến ta tưởng nó là một “công cụ tạo pool”; thực chất nó chỉ làm **thích nghi giao diện**. Cũng không nên nhầm nó với `ExecutorServiceAdapter`: hai lớp đi ngược chiều nhau.

**Ghi chú phiên bản:** bài này dùng những API ổn định `execute`, `submit` và `setTaskDecorator`. Ở dòng Spring Framework 6.2, `TaskExecutorAdapter` còn triển khai `AsyncListenableTaskExecutor`; ở Spring Framework 7, tập giao diện đã được đơn giản hóa về `AsyncTaskExecutor`. Không dựa vào `submitListenable` khi viết ví dụ có khả năng chuyển phiên bản.

## 3. `execute(Runnable)`: ủy quyền thực thi, không hứa chạy trên thread khác

Hợp đồng JDK `Executor.execute` chỉ nhận một `Runnable` để executor xử lý; cách thực thi phụ thuộc delegate:

```java
Executor inline = Runnable::run;
TaskExecutorAdapter adapter = new TaskExecutorAdapter(inline);

adapter.execute(() -> System.out.println("work"));
```

Ở ví dụ này, `Runnable::run` gọi `run()` **ngay tại thread hiện tại**, nên `execute` có tính chất đồng bộ. Nếu thay delegate bằng `Executors.newFixedThreadPool(2)`, task được lên lịch qua thread pool. Adapter không đổi cách hành xử căn bản đó.

Một số vấn đề cần phân biệt:

- Lệnh `execute()` **trả về `void`**; không có `Future` trực tiếp để nhận kết quả.
- Không mặc định suy ra task đã hoàn thành khi `execute` trả về; điều này chỉ đúng với delegate chạy trực tiếp.
- Nếu executor từ chối vì đã shutdown hoặc đã đầy, lời gọi có thể ném lỗi tại thời điểm giao việc.
- Lỗi runtime **bên trong** task xảy ra lúc task thực thi; nếu task ở worker thread, ngoại lệ không tự quay về request thread đã gọi `execute`.
- Thay delegate thành executor mới là quyết định về worker, queue, độ song song và chính sách backpressure; thay adapter đơn thuần không thực hiện điều đó.

## 4. `submit`: bổ sung hợp đồng `Future` cho cả `Executor` thường

`AsyncTaskExecutor` có hai kiểu gọi:

```java
Future<?> done = adapter.submit((Runnable) () -> performAction());
Future<Integer> result = adapter.submit(() -> calculateValue());
```

Đây là **đoạn minh họa** cần phương thức `performAction()` và `calculateValue()` thích hợp. Với `Runnable`, `Future.get()` thành công trả `null`; với `Callable<T>`, nó trả giá trị kiểu `T`. Một ngoại lệ khi thực thi được lưu trong `Future`; gọi `get()` ném `ExecutionException` có `getCause()` là lỗi gốc. `get()` chặn thread gọi đến khi xong hoặc lỗi.

Về cơ chế, `TaskExecutorAdapter` có hai nhánh quan trọng:

1. **Delegate là `ExecutorService` và chưa cấu hình `TaskDecorator`:** adapter có thể gọi thẳng `ExecutorService.submit(...)` của delegate và trả lại `Future` mà delegate tạo.
2. **Delegate chỉ là `Executor` hoặc đã đặt `TaskDecorator`:** adapter tạo `FutureTask`, giao `Runnable` này bằng `execute`, rồi trả `FutureTask` cho phía gọi. Điều này cho phép một `Executor` vốn chỉ có `execute` vẫn phục vụ `submit`.

Đường đi điển hình:

```text
submit(Callable<T>)
       |
       +-- ExecutorService + không decorator --> delegate.submit(callable)
       |
       +-- Executor thường / có decorator --> FutureTask(callable)
                                              |
                                              v
                                  delegate.execute(FutureTask được bọc)
                                              |
                                              v
                                   Future<T> cho bên gọi
```

Cả hai nhánh vẫn phụ thuộc vào delegate: một executor chạy trực tiếp khiến `Future` hoàn tất ngay trên thread gọi; pool có thể xử lý chậm; queue quá tải có thể từ chối.

### `Future` không tự biến tác vụ thành job lâu dài

`Future` biểu diễn một lần tính toán **trong tiến trình đang chạy**. Nó không tự lưu trạng thái vào database, không tự retry sau crash và không đồng nghĩa client HTTP có thể tra cứu kết quả sau khi response kết thúc.

Nếu controller gọi `future.get()` ngay sau `submit`, thread controller vẫn **chờ** task xong. Chỉ chuyển từ `execute` sang `submit` không giúp giải phóng request thread. Việc xử lý nền và hợp đồng HTTP phải được thiết kế riêng.

## 5. Ví dụ Java 17 chạy được: direct executor, pool, `submit`, decorator, rejection

Ví dụ hoàn chỉnh sau minh họa chính xác các tính chất:

- Delegate **chạy trực tiếp** (`Runnable::run`) khiến adapter cũng chạy đồng bộ.
- Delegate là JDK `ExecutorService` với hai worker và queue có giới hạn; adapter nhận việc và trả `Future`.
- `TaskDecorator` bọc các callback để đếm số lần task thực thi.
- Delegate từ chối một task; adapter báo `TaskRejectedException` ở Spring.

Chọn **JDK 17+**, Maven và **Spring Core 6.2.12** cho ví dụ cố định phiên bản. Cấu trúc:

```text
taskexecutor-adapter-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── TaskExecutorAdapterDemo.java
```

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>taskexecutor-adapter-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
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
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `src/main/java/TaskExecutorAdapterDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.core.task.support.TaskExecutorAdapter;

public class TaskExecutorAdapterDemo {

    public static void main(String[] args) throws Exception {
        Thread callingThread = Thread.currentThread();

        // Executor hợp lệ của JDK, nhưng luôn chạy ngay tại thread gọi.
        Executor directExecutor = Runnable::run;
        TaskExecutorAdapter direct = new TaskExecutorAdapter(directExecutor);
        AtomicInteger sameThread = new AtomicInteger();

        direct.execute(() -> sameThread.set(
                Thread.currentThread() == callingThread ? 1 : 0));

        System.out.println("inline=" + (sameThread.get() == 1));
        System.out.println("inlineResult=" + direct.submit(() -> 6 * 7).get());

        // Pool JDK thật: 2 worker, queue tối đa 8 job đang chờ.
        ExecutorService jdkPool = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(8),
                new ThreadPoolExecutor.AbortPolicy());

        try {
            TaskExecutorAdapter spring = new TaskExecutorAdapter(jdkPool);
            AtomicInteger decoratedRuns = new AtomicInteger();

            spring.setTaskDecorator(task -> () -> {
                decoratedRuns.incrementAndGet();
                task.run();
            });

            Future<Integer> answer = spring.submit(() -> 40 + 2);
            CountDownLatch signal = new CountDownLatch(1);
            spring.execute(signal::countDown);
            Future<?> noResult = spring.submit(() -> { });

            // Chỉ trong demo: chờ để xác định output.
            System.out.println("poolResult=" + answer.get());
            if (!signal.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("execute task did not finish");
            }
            noResult.get();
            System.out.println("decorated=" + decoratedRuns.get());
        } finally {
            // Chủ sở hữu delegate quản lý shutdown, không phải adapter.
            jdkPool.shutdown();
            if (!jdkPool.awaitTermination(10, TimeUnit.SECONDS)) {
                jdkPool.shutdownNow();
            }
        }

        Executor alwaysReject = task -> {
            throw new RejectedExecutionException("busy");
        };
        TaskExecutorAdapter rejecting = new TaskExecutorAdapter(alwaysReject);

        try {
            rejecting.execute(() -> { });
        } catch (TaskRejectedException ex) {
            System.out.println("rejected=TaskRejectedException");
        }
    }
}
```

Chạy tại thư mục dự án:

```bash
mvn -q compile exec:java -Dexec.mainClass=TaskExecutorAdapterDemo
```

**Stdout của chương trình khi hoàn tất bình thường:**

```text
inline=true
inlineResult=42
poolResult=42
decorated=3
rejected=TaskRejectedException
```

Giải thích output:

- `inline=true`: adapter dùng `Runnable::run`, chạy trên thread gọi; tên lớp có chữ “TaskExecutor” không bảo đảm bất đồng bộ.
- `inlineResult=42`: `submit(Callable)` vẫn làm việc dù delegate chỉ có `Executor.execute`. Vì delegate chạy trực tiếp, phép tính đã xong trước khi `submit` trả về.
- `poolResult=42`: delegate JDK có worker pool; `Future.get()` đợi kết quả trả ra từ worker. Đây là chỗ chương trình demo **cố ý chờ** để in đúng output.
- `decorated=3`: có ba lời giao task qua cùng adapter sau khi đặt decorator: `submit(Callable)`, `execute(Runnable)`, `submit(Runnable)`. Decorator bọc callback trong cả ba trường hợp.
- `rejected=TaskRejectedException`: lỗi `RejectedExecutionException` từ delegate khi giao việc được chuyển thành ngoại lệ của Spring. Đó là lỗi *nhận việc*, khác với lỗi bên trong task đã nhận.

`Future.get()` trong ví dụ dùng để kiểm tra, không đại diện cho cách tối ưu độ trễ HTTP. Trong ứng dụng thật, không nên vô tình giữ một request thread chờ cùng pool đang quá tải.

## 6. `TaskDecorator`: bọc callback ở ranh giới chạy task

`setTaskDecorator(TaskDecorator)` cho phép thêm logic **quanh lúc chạy** task, thường dùng cho trace/metrics hoặc truyền một số context từ thread gửi task sang thread chạy task:

```java
TaskExecutorAdapter adapter = new TaskExecutorAdapter(jdkExecutor);

adapter.setTaskDecorator(task -> () -> {
    long started = System.nanoTime();
    try {
        task.run();
    } finally {
        long durationNanos = System.nanoTime() - started;
        metrics.record(durationNanos);
    }
});
```

Đây là **đoạn minh họa**: `jdkExecutor` và `metrics` là dependency cần định nghĩa trong ứng dụng. Decorator bọc `Runnable` được adapter thực thi, **có thể là callback nội bộ như `FutureTask`**, không nhất thiết chính là Runnable/Callable do người dùng gửi vào.

Khi cần truyền `ThreadLocal`, hãy chụp giá trị ở **thread gửi task** trong giai đoạn `decorate`, rồi đặt và khôi phục giá trị cũ ở **thread worker** trong `run`:

```java
private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

static org.springframework.core.task.TaskDecorator traceDecorator() {
    return task -> {
        String captured = TRACE_ID.get(); // Thread gửi task.
        return () -> {
            String previous = TRACE_ID.get(); // Thread worker.
            try {
                if (captured == null) {
                    TRACE_ID.remove();
                } else {
                    TRACE_ID.set(captured);
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
}
```

Đây là **đoạn phương thức minh họa**, cần đặt bên trong một class có trường `TRACE_ID` tương ứng. Trong ứng dụng production nên ưu tiên cơ chế truyền context chính thức của stack đang dùng, tránh tự sao chép dữ liệu nhạy cảm vào pool. Đừng nhầm việc truyền trace/MDC với việc tự mang theo database transaction; transaction gắn với thread và hệ thống quản lý transaction, không được nhân bản tùy tiện sang worker.

### Ngoại lệ trong decorator: `execute` khác `submit`

Với lời gọi `submit`, callback mà decorator thấy có thể là một `FutureTask`: `FutureTask.run()` **giữ lại exception trong Future**, không nhất thiết ném ngoại lệ ra khỏi `run()` cho wrapper bắt. Vì vậy:

```java
// Không đủ để kết luận mọi Callable đã thành công.
adapter.setTaskDecorator(task -> () -> {
    try {
        task.run();
    } catch (Exception ex) {
        logFailure(ex);
        throw ex;
    }
});
```

Đây chỉ là **đoạn minh họa** với `logFailure` chưa định nghĩa. Khi `submit`, một `Callable` thất bại vẫn có thể làm `task.run()` trở về bình thường đối với decorator, còn `future.get()` mới ném `ExecutionException`. Thiết kế error reporting theo contract `Future`/completion handler hoặc báo lỗi tường minh trong worker; đừng dựa vào một `catch` của decorator để bắt mọi lỗi nghiệp vụ.

## 7. Từ rejection của JDK sang lỗi của Spring

Một `ThreadPoolExecutor` có thể từ chối task khi đã shutdown hoặc vượt khả năng nhận việc theo giới hạn cấu hình. Với chính sách `AbortPolicy`, JDK ném `RejectedExecutionException`. `TaskExecutorAdapter` bắt lỗi từ quá trình giao việc và chuyển thành `TaskRejectedException` để phù hợp với hợp đồng Spring.

```text
Spring caller
    |
    | adapter.execute(task) / submit(task)
    v
JDK delegate nhận task?
    |
    +-- Có --> task chờ/chạy --> thành công hoặc lỗi nội bộ của task
    |
    +-- Không --> RejectedExecutionException
                       |
                       v
                  TaskRejectedException
```

**Phân biệt hai lỗi:**

- **Rejection:** executor không nhận việc; với `AbortPolicy` nó xuất hiện ngay lúc `execute`/`submit`. Bên gọi có thể phản ứng: trả `503`/`429` tùy giao thức, chuyển sang queue khác, hoặc báo hệ thống bận.
- **Execution failure:** task được nhận nhưng lỗi khi chạy; với `submit`, lỗi được quan sát qua `Future.get()`/completion handling. Không nên tự động retry thao tác có side effect nếu chưa có chiến lược idempotency.

Một số `RejectedExecutionHandler` của JDK **không ném lỗi**: `CallerRunsPolicy` có thể thực thi task ngay trên thread gửi (trừ trường hợp executor đã shutdown). Khi đó adapter không thể suy đoán rằng tác vụ đã bị từ chối, và request thread có thể bị chặn lâu. Chọn chính sách theo nhu cầu backpressure và trách nhiệm phản hồi, không mặc định rằng dùng adapter bảo đảm luôn ném `TaskRejectedException`.

## 8. Tích hợp vào Spring: bean adapter quanh pool JDK

Một ứng dụng có executor JDK đang được dùng bởi thư viện khác có thể công bố adapter như bean Spring. Ví dụ **hai file** trong một ứng dụng Spring Boot dùng Spring Framework 6.x:

**`AsyncExecutorConfig.java`** (đặt trong package ứng dụng, ví dụ `com.example.jobs`):

```java
package com.example.jobs;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncExecutorConfig {

    @Bean(name = "jdkBusinessPool", destroyMethod = "shutdown")
    public ExecutorService jdkBusinessPool() {
        return new ThreadPoolExecutor(
                2, 4, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean("businessExecutor")
    public AsyncTaskExecutor businessExecutor(
            @Qualifier("jdkBusinessPool") ExecutorService jdkPool) {
        return new TaskExecutorAdapter(jdkPool);
    }
}
```

Ở đây:

- `jdkBusinessPool` là **backend executor thật**, với tối đa 4 worker và 100 chỗ trong queue; `AbortPolicy` từ chối khi không còn sức chứa.
- `businessExecutor` chỉ là một đối tượng Spring bọc delegate đó. Chương trình không có *hai* thread pool.
- `destroyMethod="shutdown"` áp dụng cho **bean sở hữu `ExecutorService`**. Adapter không phải `ExecutorService` và không cung cấp lifecycle `shutdown()` thay mặt delegate.
- Cấu hình bean là ví dụ độc lập về cách kết nối; đặt nó vào ứng dụng có cấu hình phù hợp, tránh tạo nhiều executor trùng vai trò.

**`ReportService.java`**:

```java
package com.example.jobs;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("businessExecutor")
    public CompletableFuture<String> createReport(String reportId) {
        String artifactId = "report-" + reportId;
        return CompletableFuture.completedFuture(artifactId);
    }
}
```

Đây là **file class trong ứng dụng Spring**, không phải chương trình có `main` độc lập. Với `@EnableAsync`, lời gọi công khai đi qua Spring proxy tới `ReportService.createReport` được giao cho bean executor được đặt tên. `CompletableFuture.completedFuture(...)` bên trong là kết quả do worker tính ra; future mà bên gọi nhận biểu diễn lời gọi bất đồng bộ do Spring quản lý.

**Hai giới hạn quan trọng:**

1. Gọi `this.createReport(...)` từ chính `ReportService` **không đi qua proxy** trong chế độ proxy mặc định, nên `@Async` có thể không có hiệu lực. Gọi từ một Spring bean khác, hoặc dùng cơ chế invocation phù hợp.
2. Việc `@Async` trả future không tự ghi job bền vững. Nếu tiến trình dừng, task đang trong queue bộ nhớ có thể mất. Với tác vụ quan trọng, cần một kho trạng thái/queue bền vững và cách khôi phục.

Nếu ứng dụng đã dùng `ThreadPoolTaskExecutor`, thường chỉ cần dùng chính bean đó cho `@Async`. Chọn `TaskExecutorAdapter` khi bạn thực sự phải **tiêu thụ executor JDK đang có** hoặc cần lộ giao diện Spring một cách có chủ đích.

## 9. Quyền sở hữu và vòng đời: ai đóng pool?

Điều gì xảy ra khi gọi:

```java
ExecutorService jdkPool = java.util.concurrent.Executors.newFixedThreadPool(2);
TaskExecutorAdapter adapter = new TaskExecutorAdapter(jdkPool);
```

`adapter` **không trở thành chủ sở hữu mặc định** của `jdkPool`. Adapter không có API `shutdown()` như `ExecutorService`, không tự đóng pool khi đối tượng adapter bị bỏ tham chiếu. Thành phần tạo/đăng ký pool phải quản lý `shutdown` theo hợp đồng của ứng dụng.

Nếu bạn viết chương trình Java thường: dùng `try/finally`, shutdown và chờ các worker kết thúc khi có thể. Trong Spring IoC: có thể đăng ký backend dưới dạng bean có destroy method hoặc dùng executor Spring có lifecycle tích hợp.

**Không đóng pool ở mỗi request**: một shared executor phục vụ nhiều request; shutdown sau request đầu tiên sẽ làm các request tiếp theo bị từ chối. Ngược lại, nếu tạo executor mới mỗi lần gọi controller mà không đóng, ứng dụng có thể rò thread và cạn tài nguyên.

`ExecutorServiceAdapter` (Spring → JDK) là một lớp khác: nó phơi bày giao diện JDK của một `TaskExecutor` phía sau. Theo hợp đồng của lớp đó, lifecycle vẫn do backend đảm nhận; các thao tác shutdown trên adapter ngược chiều không có ý nghĩa thay thế việc quản lý backend.

## 10. Chọn adapter hay `ThreadPoolTaskExecutor`?

| Tình huống | Khuyến nghị | Lý do |
| --- | --- | --- |
| Đã có JDK `ExecutorService` do thư viện/ứng dụng quản lý, cần interface Spring | `TaskExecutorAdapter` | Tái sử dụng đúng pool hiện hữu, không đổi ownership |
| Chỉ có `Executor` JDK, nhưng bên gọi cần `submit`/`Future` | `TaskExecutorAdapter` | Cung cấp `FutureTask` phù hợp qua hợp đồng `AsyncTaskExecutor` |
| Dự án Spring mới, muốn cấu hình pool/queue/thread name/lifecycle qua bean Spring | `ThreadPoolTaskExecutor` | Cấu hình rõ và quản lý cùng container |
| Cần chạy task theo lịch hoặc cron | `TaskScheduler`/scheduler phù hợp | Adapter không tự cung cấp khả năng schedule |
| Cần executor từ container hoặc thư viện ngoài theo chuẩn riêng | Xem adapter của tích hợp tương ứng; có thể dùng `TaskExecutorAdapter` | Trước tiên cần tôn trọng ownership/quy tắc của container |
| Cần xử lý job bền vững qua restart và nhiều instance | DB/broker + worker | Adapter chỉ chuyển lời gọi trong một tiến trình |
| Chỉ có vài lời gọi `execute` qua `Executor` và API Spring đã nhận `Executor` | Có thể dùng trực tiếp JDK `Executor` | Không cần thêm lớp bọc khi không có lợi ích thực tế |

Một số lớp Spring như `ConcurrentTaskExecutor` cũng hỗ trợ thích nghi executor JDK với các tiện ích của `spring-context`; đó là lựa chọn khác ở tầng scheduling/concurrency. Không cần dựng nhiều tầng adapter nếu một bean hiện tại đã đáp ứng contract.

## 11. Các lỗi triển khai thường gặp

### 11.1. Tưởng adapter tự tạo thread mới

```java
TaskExecutorAdapter adapter = new TaskExecutorAdapter(Runnable::run);
adapter.execute(() -> longOperation());
```

Đây là **đoạn minh họa**, với `longOperation()` do bạn định nghĩa. Tác vụ này chạy ngay trong thread gọi. Nếu muốn chạy nền, chọn executor delegate thực sự xử lý trên worker khác.

### 11.2. Chọn `submit` rồi gọi `get()` ngay trong controller

`Future<T>` không phải tín hiệu tác vụ nền đã tách vòng đời HTTP. `get()` có thể giữ thread HTTP tới khi worker xong. Nếu cần trả kết quả trong cùng response, dùng cơ chế async request của Spring MVC/WebFlux phù hợp; nếu cần trả `202 Accepted` rồi theo dõi sau, thiết kế job ID, trạng thái, retry và lưu trữ.

### 11.3. Không giới hạn queue hoặc không xử lý rejection

`Executors.newFixedThreadPool(n)` dùng queue không giới hạn theo khả năng bộ nhớ, nên giới hạn **số worker** chưa tương đương giới hạn **số công việc tồn đọng**. Với tải thực, cân nhắc `ThreadPoolExecutor` có queue hữu hạn, handler rõ ràng, theo dõi queue depth, rejected count và latency.

### 11.4. Coi lỗi trong `Future` giống lỗi ném khi gửi task

`execute`/`submit` có thể bị từ chối ngay. Nhưng task đã được nhận rồi mới thất bại sẽ được quan sát ở luồng xử lý/lúc truy cập `Future`. Với `submit`, phải xử lý `ExecutionException` hoặc callback thích hợp. `TaskDecorator` chỉ bọc execution callback, không bảo đảm bắt hết exception bên trong `FutureTask`.

### 11.5. Cố “lan truyền transaction” qua decorator

Một `ThreadLocal` trace ID có thể truyền an toàn nếu có thiết kế capture/restore/cleanup. Nhưng database transaction gắn với resource và transaction manager; không sao chép nó sang worker bằng thao tác copy context. Worker muốn cập nhật DB phải mở transaction theo yêu cầu nghiệp vụ, và phải cân nhắc việc enqueue task trước hay sau commit.

### 11.6. Quên self-invocation với `@Async`

`@EnableAsync` ở chế độ proxy không bắt được lời gọi nội bộ qua `this`. Trước khi đổ lỗi cho adapter, kiểm tra method có được gọi qua Spring proxy, bean executor có tên đúng và service có thực sự được Spring quản lý.

### 11.7. Dùng nhiều adapter rồi nghĩ đã có nhiều pool độc lập

Hai `TaskExecutorAdapter` cùng trỏ vào một `ExecutorService` thì vẫn chia sẻ cùng worker/queue, chịu ảnh hưởng tải lẫn nhau. Muốn cô lập tác vụ quan trọng, cần các delegate executor riêng với kích thước và chính sách backpressure riêng.

### 11.8. Đóng delegate từ nơi không sở hữu

Nếu executor được chia sẻ, một thành phần tự `shutdown()` có thể làm tất cả consumer khác thất bại. Xác định rõ người tạo pool, phạm vi bean và thời điểm đóng trong lifecycle ứng dụng.

## 12. Câu hỏi tự kiểm tra và bài tập

**Câu 1.** `TaskExecutorAdapter` nhận `Executor direct = Runnable::run`. Lệnh `adapter.submit(() -> 42)` có thực sự chạy bất đồng bộ không?  
**Đáp:** Không. Delegate chạy `Runnable` trên thread gọi nên `FutureTask` hoàn tất trong lúc `submit` đang gọi; adapter không ép tạo thread mới.

**Câu 2.** Vì sao `TaskExecutorAdapter` có thể `submit(Callable)` dù delegate chỉ có `execute(Runnable)`?  
**Đáp:** Adapter có thể bọc `Callable` trong `FutureTask` rồi chuyển `FutureTask` vào `Executor.execute`. `FutureTask` vừa là `Runnable` vừa là `Future`.

**Câu 3.** Nếu delegate là `ExecutorService`, adapter luôn gọi `executorService.submit` chứ?  
**Đáp:** Không. Không có decorator, adapter có thể ủy quyền trực tiếp; có decorator, adapter có thể bọc task thành `FutureTask` để chạy qua đường decorator/`execute`.

**Câu 4.** Trong Spring, ai nên shutdown executor JDK đã bọc?  
**Đáp:** Thành phần/bean sở hữu delegate; bản thân `TaskExecutorAdapter` không thay thế lifecycle của `ExecutorService`.

**Câu 5.** Tại sao một decorator `try { task.run(); } catch (...)` có thể không thấy lỗi của `Callable` gửi qua `submit`?  
**Đáp:** Callback có thể là `FutureTask`; nó bắt lỗi của `Callable` và lưu để `Future.get()` ném `ExecutionException`, thay vì phát lỗi ra khỏi `run()`.

**Câu 6.** Ba replica Spring dùng cùng một DB. Chỉ bọc JDK pool bằng `TaskExecutorAdapter` có bảo vệ nhất quán dữ liệu liên replica không?  
**Đáp:** Không. Adapter chỉ liên quan việc gửi task tới executor trong JVM; ranh giới DB/phân tán cần transaction, locking/version, idempotency hoặc kiến trúc phù hợp.

### Bài tập ngắn

1. Thay delegate trực tiếp `Runnable::run` bằng `Executors.newSingleThreadExecutor()`. Dùng `CountDownLatch` để xác định work có chạy trên thread khác và **nhớ đóng delegate**.
2. Đặt decorator đếm số task và gửi lần lượt `execute`, `submit(Runnable)`, `submit(Callable)`. Vì sao số lần wrap có thể khác số lần task thành công?
3. Tạo một `ThreadPoolExecutor` có 1 worker, queue 1 phần tử và `AbortPolicy`; giữ worker bận với latch, thêm tác vụ vào queue, rồi thử gửi tác vụ thứ ba. Quan sát `TaskRejectedException` khi pool không nhận thêm việc. Dọn latch/pool bằng `finally`.
4. Trong một ứng dụng Spring, công bố một bean `AsyncTaskExecutor` bằng `TaskExecutorAdapter` quanh pool JDK. Dùng `@Async("...")` trên một service được **bean khác** gọi và quan sát thread; thử gọi nội bộ để hiểu ảnh hưởng proxy.

## 13. Ghi nhớ

`TaskExecutorAdapter` **thích nghi hợp đồng** giữa Spring và JDK; backend executor vẫn quyết định khi nào và bằng thread nào task chạy, có chỗ trong queue hay không, và khi nào cần shutdown.

- **`execute`** chuyển `Runnable` tới delegate.
- **`submit`** trả `Future`, ủy quyền qua `ExecutorService.submit` khi phù hợp hoặc dùng `FutureTask` khi cần.
- **`TaskDecorator`** bọc callback, hữu ích cho metrics/context; phải xử lý exception của `Future` bằng contract phù hợp.
- **Rejection** có thể được biểu diễn bằng `TaskRejectedException`, còn lỗi task đã chạy phải được theo dõi riêng.
- **Không có bảo đảm tự động** về async thread, durability, retry, isolation, transaction hay lifecycle chỉ vì có adapter.

Tài liệu chính thức để đối chiếu API:

- [Spring Framework 6.2 — `TaskExecutorAdapter` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/task/support/TaskExecutorAdapter.html).
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).
- [Spring Framework 6.2 — `AsyncTaskExecutor` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/core/task/AsyncTaskExecutor.html).
- [Java SE 17 — `Executor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executor.html).
- [Java SE 17 — `ExecutorService`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html).
- [Java SE 17 — `FutureTask`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html).
- [Spring Framework 7 — `TaskExecutorAdapter` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/task/support/TaskExecutorAdapter.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-selection-007">DefaultManagedTaskExecutor trong môi trường Jakarta EE</a>

<details>
<summary>Xem chi tiết</summary>

# DefaultManagedTaskExecutor trong môi trường Jakarta EE

## 1. Vì sao ứng dụng Spring chạy trong Jakarta EE cần bộ thực thi do container quản lý?

Trong Java SE, một ứng dụng có thể trực tiếp dùng `Executors.newFixedThreadPool(...)` để tạo và quản lý các luồng xử lý nền. Khi ứng dụng Spring được triển khai **bên trong một Jakarta EE application server**, câu chuyện khác: server quản lý vòng đời ứng dụng, tài nguyên dùng chung, định danh bảo mật, classloader, naming context và khả năng kiểm soát tải. Tác vụ nền thường nên chạy bằng **luồng do application server quản lý (managed threads)** thay vì một pool tự tạo mà server không biết cách theo dõi hoặc dọn dẹp.

Spring cung cấp lớp:

```java
org.springframework.scheduling.concurrent.DefaultManagedTaskExecutor
```

Lớp này nối **giao diện thực thi tác vụ của Spring** với **executor có sẵn trong JNDI** của môi trường triển khai. Mặc định nó tra cứu:

```text
java:comp/DefaultManagedExecutorService
```

Đây là tên tài nguyên chuẩn của `ManagedExecutorService` trong Jakarta Concurrency (trước đây được biết qua JSR-236). Server Jakarta EE tương thích cung cấp executor và chịu trách nhiệm quản lý các worker thread, năng lực xử lý, ngữ cảnh thực thi và vòng đời của tài nguyên. Spring nhận executor đã được cấp sẵn rồi chuyển các lời gọi `TaskExecutor.execute(...)` hoặc `AsyncTaskExecutor.submit(...)` sang executor ấy.

**Mô hình cần nhớ:**

```text
Lời gọi ứng dụng / @Async
          |
          v
TaskExecutor / AsyncTaskExecutor (Spring)
          |
          v
DefaultManagedTaskExecutor
          |
          | lookup JNDI khi bean khởi tạo
          v
java:comp/DefaultManagedExecutorService
          |
          v
ManagedExecutorService của Jakarta EE server
          |
          +-- luồng do server quản lý
          +-- cơ chế cấp phát và giới hạn tài nguyên của server
          +-- ngữ cảnh Jakarta EE theo cấu hình/tập hợp bảo đảm
          +-- vòng đời do server sở hữu
```

**DefaultManagedTaskExecutor không phải bộ tạo thread pool mới.** Nó là một bộ **ủy quyền/thích nghi (adapter)** để ứng dụng Spring gửi việc đến một `java.util.concurrent.Executor` được tìm thấy qua JNDI; trong trường hợp chuẩn, đối tượng đó là `jakarta.enterprise.concurrent.ManagedExecutorService`. Phần nhận biết và thích nghi khi delegate là managed executor nằm ở lớp cha `ConcurrentTaskExecutor`.

Mục tiêu của bài: hiểu đúng hợp đồng, cấu hình Spring, cơ chế lookup JNDI, cách dùng với `@Async` hoặc gửi `Runnable` trực tiếp, quản lý kết quả/lỗi, giới hạn lan truyền ngữ cảnh và những lỗi triển khai cần tránh.

## 2. Vị trí trong hệ thống API

### 2.1. Các kiểu và quan hệ kế thừa

```text
java.util.concurrent.Executor
  └─ execute(Runnable)

org.springframework.core.task.TaskExecutor
  └─ execute(Runnable)

org.springframework.core.task.AsyncTaskExecutor
  ├─ execute(Runnable)
  └─ submit(Runnable/Callable)

ConcurrentTaskExecutor
  └─ triển khai AsyncTaskExecutor,
     thích nghi với java.util.concurrent.Executor bất kỳ
          ^
          |
DefaultManagedTaskExecutor
  ├─ extends ConcurrentTaskExecutor
  ├─ implements InitializingBean
  ├─ tra cứu JNDI trong afterPropertiesSet()
  └─ delegate cho Executor đã tìm được

jakarta.enterprise.concurrent.ManagedExecutorService
  └─ extends java.util.concurrent.ExecutorService
     (là tài nguyên Jakarta EE, không phải lớp con của Spring executor)
```

Một số điểm dễ nhầm:

- `DefaultManagedTaskExecutor` là **lớp Spring**, gói `org.springframework.scheduling.concurrent`.
- `ManagedExecutorService` là **giao diện Jakarta Concurrency**, gói `jakarta.enterprise.concurrent` ở các phiên bản Jakarta hiện đại.
- `Executors` (số nhiều) là lớp factory của Java SE; **không** phải tên khác của `DefaultManagedTaskExecutor`.
- `ThreadPoolTaskExecutor` là bean Spring tự điều hành pool theo cấu hình ứng dụng; `DefaultManagedTaskExecutor` **dùng executor đã thuộc quyền quản lý của server**.
- `ConcurrentTaskExecutor` là adapter chung cho một `Executor` được cung cấp sẵn; `DefaultManagedTaskExecutor` thêm việc tra cứu JNDI theo quy ước.

JavaDoc Spring mô tả lớp này từ Spring Framework 4.0, và hiện diện cả trong Spring Framework 6.x/7.x. Dòng mô tả cũ nhắc “JSR-236” vì tên đặc tả ban đầu; khi làm việc với Jakarta EE mới, hãy dùng các package `jakarta.*` tương ứng của môi trường hiện tại thay vì sao chép `javax.enterprise.concurrent.*` từ ví dụ Java EE cũ.

### 2.2. Vì sao không gọi ManagedExecutorService trực tiếp?

Có thể dùng trực tiếp `ManagedExecutorService` khi viết một thành phần Jakarta EE. Tuy nhiên, trong ứng dụng Spring, việc phụ thuộc vào `TaskExecutor` hoặc `AsyncTaskExecutor` giúp service không biết tên tài nguyên JNDI hay cấu hình server. Một cấu hình runtime có thể chọn `DefaultManagedTaskExecutor`, còn cấu hình local/test chọn executor khác.

`DefaultManagedTaskExecutor` đặc biệt hữu ích khi muốn dùng chung một giao diện Spring cho:

1. Lời gọi `TaskExecutor.execute(Runnable)` trực tiếp.
2. Phương thức Spring `@Async("managedTaskExecutor")`.
3. Các phần mềm Spring yêu cầu một executor dạng `TaskExecutor` hoặc `AsyncTaskExecutor`.

Nó không phải cơ chế chạy tác vụ bền vững giữa các lần restart, không thay thế message broker, và cũng không tự bảo đảm thao tác nền sẽ chạy thành công trong mọi tình huống.

## 3. JNDI và tài nguyên mặc định của Jakarta EE

### 3.1. JNDI là gì trong ví dụ này?

JNDI (Java Naming and Directory Interface) là API Java cho phép lookup một đối tượng đã được ràng buộc dưới tên quản lý của runtime. Với Jakarta Concurrency, application server cung cấp mặc định:

```text
java:comp/DefaultManagedExecutorService
```

Tên này tham chiếu đến một `ManagedExecutorService` mà server đã tạo/cấu hình. Việc lookup không tự sinh một executor mới; nó lấy một tham chiếu đến đối tượng server cho phép thành phần ứng dụng sử dụng.

Theo Jakarta Concurrency, dịch vụ mặc định có thể truyền ít nhất ngữ cảnh **naming**, **classloader** và **security** từ thành phần đã gửi công việc, theo quy tắc của đặc tả. Những ngữ cảnh này có mục tiêu giúp tác vụ thực thi đúng môi trường Jakarta EE; không có nghĩa mọi loại `ThreadLocal` tùy ý của ứng dụng được sao chép.

### 3.2. Luồng khởi tạo bean

Khi `DefaultManagedTaskExecutor` là bean trong Spring, nó triển khai `InitializingBean`. Sau khi thiết lập các property, Spring gọi `afterPropertiesSet()`. Lớp thực hiện lookup JNDI với tên đã cấu hình (mặc định là tên chuẩn ở trên), yêu cầu kiểu `Executor`, rồi đặt đối tượng đó làm delegate cho `ConcurrentTaskExecutor`.

```text
Spring tạo bean
   |
   v
new DefaultManagedTaskExecutor()
   |
   +-- setJndiName(...), setResourceRef(...), nếu được cấu hình
   |
   v
afterPropertiesSet()
   |
   +-- JNDI lookup(name, Executor.class)
   |
   +-- thành công -> setConcurrentExecutor(foundExecutor)
   |
   +-- thất bại -> lỗi lookup/khởi tạo bean; cần sửa runtime/tên/mapping
```

**Không có cơ chế fallback mặc định sang một pool tự tạo khi JNDI lookup thất bại.** Nếu chạy ứng dụng trong môi trường Java SE hoặc embedded servlet container không cung cấp tài nguyên này, cấu hình bean có thể thất bại khi khởi động. Cần chọn executor phù hợp với runtime thay vì giả định chỉ thêm dependency Jakarta Concurrency là JNDI resource sẽ tự xuất hiện.

### 3.3. Tên đầy đủ và tên tương đối

API cấu hình đáng chú ý:

| Phương thức | Mục đích | Lưu ý |
|---|---|---|
| `setJndiName(String)` | Thay tên JNDI mặc định bằng executor do server công bố | Tên phải tồn tại và đối tượng phải thích hợp với `Executor` |
| `setResourceRef(boolean)` | Điều chỉnh cách nhận diện tên tương đối trong môi trường `java:comp/env/` | Không tự sinh resource mới trên server |
| `setJndiTemplate(JndiTemplate)` | Tùy biến đối tượng thực hiện JNDI lookup | Chủ yếu cho tích hợp/test hoặc môi trường JNDI đặc thù |
| `setJndiEnvironment(Properties)` | Cung cấp property JNDI nếu runtime yêu cầu | Thường không cần khi JNDI của container đã có sẵn |

Với tên **đầy đủ** `java:comp/DefaultManagedExecutorService`, thông thường không cần thêm `java:comp/env/`. Nếu dùng tên tương đối và `resourceRef=true`, quy tắc lookup có thể thêm prefix `java:comp/env/`. Không nên sửa tên mặc định chỉ vì thấy “env” xuất hiện trong các ví dụ khác.

## 4. Ví dụ Spring: đăng ký executor từ JNDI

Mẫu Java Configuration sau dùng cho một ứng dụng Spring Framework chạy **trong Jakarta EE server có cung cấp tài nguyên JNDI mặc định**.

**Tệp `ManagedExecutorConfiguration.java`**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.DefaultManagedTaskExecutor;

@Configuration
@EnableAsync
public class ManagedExecutorConfiguration {

    @Bean("managedTaskExecutor")
    public DefaultManagedTaskExecutor managedTaskExecutor() {
        return new DefaultManagedTaskExecutor();
    }
}
```

Đây là **class cấu hình Spring**, không phải một chương trình Java SE độc lập với `main`. Để hoạt động, ứng dụng cần Spring Framework phù hợp trên classpath, được khởi tạo bằng Spring ApplicationContext, và được triển khai trong Jakarta EE runtime cung cấp `java:comp/DefaultManagedExecutorService`. Lớp này không tạo hoặc cấu hình một pool mới.

Tại sao dùng `@EnableAsync`? Vì annotation này kích hoạt cơ chế Spring xử lý phương thức `@Async`. Nó **không bắt buộc** khi ứng dụng chỉ inject `TaskExecutor` rồi gọi `execute(...)` thủ công. Nhưng muốn `@Async` hoạt động qua proxy Spring thì cần bật hỗ trợ async theo cấu hình ứng dụng.

Tên `managedTaskExecutor` được khai báo rõ để các phương thức `@Async` có thể chọn đúng executor. Nếu ứng dụng có nhiều executor, đừng phụ thuộc vào việc Spring tự chọn một bean nào đó theo tình cờ.

### 4.1. Chọn JNDI name khác

Nếu người quản trị server tạo một resource riêng và công bố tại `java:comp/env/concurrent/ReportingExecutor`, có thể cấu hình:

```java
@Bean("managedTaskExecutor")
public DefaultManagedTaskExecutor managedTaskExecutor() {
    DefaultManagedTaskExecutor executor =
            new DefaultManagedTaskExecutor();

    executor.setJndiName(
            "java:comp/env/concurrent/ReportingExecutor");
    return executor;
}
```

Đây là **phương thức bean thay thế** phương thức trong `ManagedExecutorConfiguration`, không phải phương thức thứ hai được định nghĩa cùng tên trong cùng lớp. Server phải có cấu hình/mapping tương ứng. Tên ví dụ không phải một resource được Jakarta EE tự bảo đảm; hãy kiểm tra tài liệu và cấu hình của application server thực tế.

Không nên gọi `afterPropertiesSet()` thủ công trong phương thức bean trên. Khi tạo bean qua Spring, container sẽ xử lý lifecycle và gọi phương thức đó sau khi các property đã được cấu hình.

### 4.2. Cấu hình XML nếu dự án dùng XML

Cùng ý tưởng có thể biểu đạt bằng bean XML:

```xml
<bean id="managedTaskExecutor"
      class="org.springframework.scheduling.concurrent.DefaultManagedTaskExecutor" />
```

Đây là **mảnh XML cấu hình Spring**, cần namespace/khai báo XML đầy đủ ở file chứa nó. Spring khởi tạo bean rồi thực hiện JNDI lookup khi container bắt đầu. Có thể thêm property `jndiName` nếu cần tài nguyên khác.

## 5. Ví dụ @Async chạy trên managed executor

**Tệp `ReportService.java`**

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("managedTaskExecutor")
    public CompletableFuture<String> buildReport(String reportId) {
        // Giả lập phần tính toán hoặc gọi dịch vụ bên ngoài.
        String result = "Báo cáo " + reportId + " đã tạo";
        return CompletableFuture.completedFuture(result);
    }
}
```

**Tệp `ReportControllerOrCaller.java`** — ví dụ một Spring component khác gọi service:

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

@Component
public class ReportControllerOrCaller {
    private final ReportService reportService;

    public ReportControllerOrCaller(ReportService reportService) {
        this.reportService = reportService;
    }

    public CompletableFuture<String> request(String reportId) {
        return reportService.buildReport(reportId);
    }
}
```

Hai đoạn trên là **các class thành phần Spring**; để chạy thực sự còn cần cấu hình khởi tạo Spring, dependency và server Jakarta EE như đã nói. Không có stdout cố định: thứ tự chạy và thời gian hoàn tất phụ thuộc managed executor.

Cơ chế:

1. `ReportControllerOrCaller` nhận `ReportService` qua dependency injection. Lời gọi từ bean khác đi qua Spring proxy.
2. `@Async("managedTaskExecutor")` yêu cầu Spring dùng bean executor có tên đã chỉ định.
3. Spring gửi công việc vào executor; `DefaultManagedTaskExecutor` ủy quyền cho `ManagedExecutorService` nhận từ JNDI.
4. Worker do Jakarta EE server quản lý thực thi thân phương thức; kết quả được quan sát qua `CompletableFuture<String>`.
5. Caller có thể xử lý hoàn tất hoặc ngoại lệ mà không cần chặn ngay luồng request.

**Cẩn thận với self-invocation:** Nếu chính `ReportService` gọi `this.buildReport(...)` từ một phương thức của nó, lời gọi thường không đi qua proxy Spring nên không kích hoạt hành vi `@Async` ở chế độ proxy mặc định. Hãy thiết kế một bean gọi qua bean khác hoặc dùng cơ chế proxy/AspectJ có chủ đích, tùy ứng dụng.

### 5.1. @Async không có nghĩa mọi bước tiếp theo đều ở managed executor

Nếu phía gọi sử dụng các phép kết hợp `CompletableFuture`, cần biết chúng được chạy trên executor nào. Những lời gọi `thenApplyAsync(...)` hoặc `CompletableFuture.supplyAsync(...)` **không truyền executor** có thể dùng executor mặc định của CompletableFuture/Java SE. Không nên giả định mọi callback sẽ tiếp tục chạy bằng `ManagedExecutorService` chỉ vì giai đoạn đầu bắt đầu từ `@Async`.

Khi cần một callback cụ thể chạy bằng managed executor, hãy truyền executor đó vào overload nhận `Executor`, hoặc dùng cơ chế hoàn tất bất đồng bộ do `ManagedExecutorService` cung cấp trực tiếp theo phiên bản Jakarta Concurrency đang dùng.

### 5.2. Ngoại lệ của @Async

Với phương thức trả `CompletableFuture<T>`, caller nên quan sát lỗi qua `whenComplete(...)`, `handle(...)`, `exceptionally(...)` hoặc `get()/join()` ở nơi được phép chờ. Nếu `@Async` trả `void`, caller không có đối tượng kết quả để nhận lỗi; Spring cung cấp `AsyncUncaughtExceptionHandler` để xử lý các ngoại lệ đó.

Không dùng `join()` ngay trong luồng nhận HTTP request nếu mục tiêu là không chặn request. Điều phối kết quả theo hợp đồng của endpoint, timeout và khả năng của runtime.

## 6. Gửi tác vụ trực tiếp qua TaskExecutor

Không nhất thiết phải dùng `@Async`. Một service có thể nhận trực tiếp `TaskExecutor` bằng constructor injection và gửi `Runnable`:

**Tệp `AuditDispatchService.java`**

```java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class AuditDispatchService {
    private final TaskExecutor executor;

    public AuditDispatchService(
            @Qualifier("managedTaskExecutor") TaskExecutor executor) {
        this.executor = executor;
    }

    public void dispatch(String eventId) {
        executor.execute(() -> {
            // Minh họa tác vụ độc lập với caller.
            System.out.println("Đã xử lý sự kiện: " + eventId);
        });
    }
}
```

Đây là **thành phần Spring**, dùng bean cấu hình ở mục 4. Phương thức `execute(...)` chỉ gửi `Runnable`, không trả `Future` để lấy giá trị hoặc lỗi. Đối với tác vụ có yêu cầu lưu lại trạng thái nghiệp vụ, cần thiết kế ghi nhận kết quả, retry và idempotency theo yêu cầu.

Nếu inject dưới kiểu `AsyncTaskExecutor` (mà `DefaultManagedTaskExecutor` triển khai), có thể gọi `submit(Callable<T>)` để nhận `Future<T>`. Tuy nhiên kết quả/ngoại lệ vẫn cần được quan sát. Với Spring Framework 7 và các phiên bản có API khác nhau, luôn tra cứu Javadoc cho đúng phiên bản thư viện đang triển khai; không sao chép nguyên các overload từ tài liệu Spring cũ nếu API đã thay đổi.

## 7. Sự khác nhau giữa Spring adapter và ManagedExecutorService thật

Có hai lớp trách nhiệm:

| Mặt | DefaultManagedTaskExecutor | ManagedExecutorService |
|---|---|---|
| Nằm ở đâu? | Trong thư viện Spring Framework | Trong Jakarta Concurrency API và bản triển khai của server |
| Ai tạo worker thread? | Không tạo pool; ủy quyền | Application server cung cấp worker do nó quản lý |
| Chức năng chính | Lookup JNDI, điều hợp `Executor` với `TaskExecutor` | Nhận tác vụ, thực thi có ngữ cảnh, áp dụng cấu hình tài nguyên server |
| Cấu hình pool size/queue | Không có các setter quản trị pool như `ThreadPoolTaskExecutor` | Do cấu hình/chính sách của server, tùy sản phẩm |
| Vòng đời executor | Spring quản lý vòng đời **adapter bean** | Server quản lý vòng đời tài nguyên/worker |
| Quyền gọi `shutdown()` | Không phải chỗ đóng managed pool | Ứng dụng không được tự đóng pool do server quản lý |
| Mục tiêu | Dễ tích hợp với `TaskExecutor`, `@Async` | Bảo đảm môi trường Jakarta EE đúng quy tắc |

Cần phân biệt thêm: vì `DefaultManagedTaskExecutor` có thể ủy quyền tới **bất kỳ `Executor` phù hợp được đăng ký trong JNDI**, việc dùng lớp Spring này **không tự chứng minh** delegate là `ManagedExecutorService`. Với JNDI mặc định của Jakarta EE server tương thích, đó là tài nguyên managed executor; nếu đổi `setJndiName` sang resource khác, phải xác minh loại và ngữ nghĩa thực tế.

## 8. Jakarta EE context: những gì được và không được truyền

### 8.1. Các ngữ cảnh được server quản lý

Khi một Jakarta EE component gửi tác vụ vào `ManagedExecutorService`, server thực hiện các quy tắc truyền ngữ cảnh của Jakarta Concurrency. Với default managed executor, các nhóm ngữ cảnh được đặc tả yêu cầu gồm **naming context**, **classloader context** và **security information**.

Ví dụ, tác vụ có thể cần classloader của ứng dụng để nạp lớp hoặc JNDI context để truy cập resource tương ứng. Chạy worker “ngoài tầm quản lý” của application server có thể khiến những giả định này sai.

Tuy nhiên, các chi tiết cụ thể về thông tin bảo mật nào được dùng và quyền nào tác vụ có được vẫn phụ thuộc mô hình bảo mật và chính sách của Jakarta EE runtime. Không suy ra mọi user identity hoặc mọi context ứng dụng tự động được sao chép.

### 8.2. Ngữ cảnh transaction không tự theo caller

Theo Jakarta Concurrency, tác vụ chạy mà **không tham gia sẵn vào transaction của thành phần đã gửi**. Việc caller đang trong `@Transactional` không có nghĩa managed task tự tiếp tục cùng giao dịch. Nếu công việc nền cần transaction, hãy tạo ranh giới transaction thích hợp **trong worker** bằng dịch vụ Spring được proxy quản lý hoặc cơ chế Jakarta transaction của ứng dụng.

Ví dụ về nguyên tắc:

```text
Request thread:
  @Transactional bắt đầu TX-A
  gửi @Async task
  tiếp tục xử lý và kết thúc TX-A

Managed worker thread:
  chạy sau hoặc đồng thời
  không tự kế thừa TX-A
  nếu cần: bắt đầu TX-B phù hợp trong worker
```

Cần cân nhắc cả tính nhất quán dữ liệu: tác vụ async có thể đọc dữ liệu trước khi transaction của caller commit. Muốn công việc chỉ bắt đầu sau commit thì thiết kế sự kiện sau commit, outbox hoặc một cơ chế có độ bền phù hợp thay vì chỉ tin vào thứ tự gửi `@Async`.

### 8.3. ThreadLocal, Spring Security và MDC

Không mặc định rằng `ThreadLocal` của request, `RequestContextHolder`, `SecurityContextHolder`, MDC logging hoặc trace context của thư viện khác được truyền đúng chỉ vì dùng `DefaultManagedTaskExecutor`. Mỗi loại context có quy tắc riêng; context Jakarta EE do runtime quản lý khác với biến cục bộ theo luồng do ứng dụng tạo.

Nếu tác vụ cần `correlationId`, `tenantId` hoặc principal ứng dụng, truyền dữ liệu bất biến rõ ràng trong tham số task hoặc dùng cơ chế truyền ngữ cảnh đã được kiểm chứng. Với dữ liệu nhạy cảm, không nhân bản bừa bãi cả request object hoặc tài nguyên có vòng đời ngắn sang worker.

## 9. Tác vụ mẫu và kết quả: Runnable, Callable, Future

`ManagedExecutorService` kế thừa `ExecutorService`, vì vậy hỗ trợ những phương thức như `execute(Runnable)` và `submit(Callable<T>)`; bản thân adapter Spring đưa ra các phương thức gửi việc tương ứng với `TaskExecutor`/`AsyncTaskExecutor`.

```text
Runnable
  └── công việc không trả giá trị trực tiếp

Callable<T>
  └── công việc trả T, có thể ném checked exception

AsyncTaskExecutor.submit(Callable<T>)
  └── Future<T>
       ├── get()           nhận giá trị / báo lỗi / có thể chặn
       ├── isDone()        có thể true cả khi lỗi/hủy
       └── cancel(true)    yêu cầu hủy/ngắt theo cơ chế hợp tác
```

Các nguồn lỗi cần tính tới:

- Lookup JNDI thất bại ở **khởi tạo bean**: lỗi cấu hình/tài nguyên, khác với lỗi của một task.
- Executor từ chối nhận việc mới: có thể phát sinh `TaskRejectedException` ở tầng Spring hoặc lỗi từ delegate theo hợp đồng; xử lý theo nơi gọi.
- Task chạy rồi ném ngoại lệ: khi gửi bằng `submit`, `Future.get()` báo lại qua `ExecutionException`; xem `getCause()` để biết lỗi gốc.
- Đợi quá lâu: `Future.get(timeout, unit)` ném `TimeoutException`; việc hết hạn không tự hủy task.
- Task bị hủy: `Future` có thể báo `CancellationException`; không nên hiểu việc hủy luôn dừng ngay thao tác I/O.

**Không được dùng `future.get()` vô thời hạn trong một worker mà task phụ thuộc cũng cần chính worker/tài nguyên đó để chạy.** Hệ thống có thể kẹt vì phụ thuộc vòng, giới hạn số worker hoặc khóa chung. Managed executor vẫn phải tuân theo giới hạn tài nguyên thực tế.

## 10. Vòng đời: đừng đóng pool của application server

Một điểm khác biệt cốt lõi giữa Jakarta EE và Java SE:

**Trong chương trình Java SE tự tạo `ExecutorService`**, code sở hữu pool phải đảm bảo đóng nó bằng `shutdown()`/`shutdownNow()` theo chính sách thích hợp.

**Trong Jakarta EE**, `ManagedExecutorService` được quản lý bởi application server. Ứng dụng **không được tự đóng** shared managed executor. Đặc tả quy định các thao tác lifecycle trên `ManagedExecutorService` như `shutdown()`, `shutdownNow()`, `awaitTermination()`, `isShutdown()` và `isTerminated()` bị từ chối bằng `IllegalStateException`.

Do đó:

- Khi dùng `DefaultManagedTaskExecutor`, hãy để Spring quản lý **adapter bean**, nhưng để application server quản lý **executor thật**.
- Đừng cố lấy delegate ra rồi gọi `shutdown()` như đang sở hữu pool Java SE.
- Không tạo pool “mỗi lần gọi một cái” chỉ để chạy công việc nền; trong Jakarta EE hãy dùng tài nguyên server cung cấp.
- Khi redeploy/undeploy, server có thể ngừng nhận việc, hủy việc đang chờ hoặc ngắt task đang chạy theo đặc tả/chính sách runtime.
- Managed executor thực thi tác vụ trong tiến trình, không phải hàng đợi bền vững. Nếu cần hoàn thành công việc bất chấp restart/crash, hãy dùng giải pháp xử lý bền vững thích hợp.

Ở phía ứng dụng, vẫn cần thiết kế timeout, khả năng hủy, lỗi từ task, giới hạn đầu vào và cách khôi phục công việc. “Managed” là trách nhiệm quản trị tài nguyên của server; nó không có nghĩa ứng dụng không phải xử lý thất bại.

## 11. So sánh với ThreadPoolTaskExecutor và ConcurrentTaskExecutor

| Tiêu chí | DefaultManagedTaskExecutor | ThreadPoolTaskExecutor | ConcurrentTaskExecutor |
|---|---|---|---|
| Nguồn executor | Lookup JNDI; mặc định managed executor của Jakarta EE | Spring tạo và cấu hình `ThreadPoolExecutor` | Nhận một `Executor` đã có |
| Runtime phù hợp | Jakarta EE application server có JNDI resource | Ứng dụng Spring tự quản lý tài nguyên, điển hình Spring Boot độc lập | Khi có `Executor` từ thư viện/nguồn khác cần chuyển thành Spring executor |
| Cấu hình `corePoolSize`/`maxPoolSize`/`queueCapacity` | Cấu hình tại application server thay vì trên adapter này | Có setter trực tiếp trên bean | Tùy delegate, không tự định nghĩa pool |
| Ai sở hữu worker? | Application server | Ứng dụng/Spring bean | Bên cung cấp delegate |
| Lookup JNDI mặc định | Có | Không | Không |
| Hợp với `@Async` | Có, khi bean được chọn và runtime sẵn sàng | Có | Có |

**Chọn DefaultManagedTaskExecutor khi:** ứng dụng Spring được đóng gói/deploy vào một Jakarta EE runtime hỗ trợ Jakarta Concurrency, muốn dùng executor do server quản lý và muốn Spring service chỉ phụ thuộc `TaskExecutor`.

**Chọn ThreadPoolTaskExecutor khi:** ứng dụng Spring chạy độc lập và chủ động quản lý pool, giới hạn queue, shutdown; không có tài nguyên Jakarta EE managed executor mà runtime cấp cho ứng dụng.

**Chọn ConcurrentTaskExecutor khi:** bạn đã có một đối tượng `Executor` phù hợp và chỉ cần đưa nó vào giao diện Spring, không cần thêm quy ước JNDI mặc định.

Một embedded servlet container không tự động tương đương Jakarta EE application server với đầy đủ Jakarta Concurrency. Hãy kiểm tra tính năng/tài nguyên JNDI của runtime cụ thể thay vì chọn theo tên “Jakarta” trong dependency.

## 12. Mẫu triển khai theo môi trường

### 12.1. Môi trường production Jakarta EE

Bản triển khai tương đối điển hình:

1. Administrator xác nhận server tương thích và cho phép ứng dụng dùng default managed executor hoặc một resource riêng.
2. Ứng dụng Spring cấu hình bean `DefaultManagedTaskExecutor` với đúng JNDI name.
3. Tại khởi động, Spring lookup; lỗi lookup phải làm rõ cấu hình, không im lặng đổi pool.
4. Service inject `TaskExecutor` hoặc chọn `@Async("managedTaskExecutor")`.
5. Quản trị số worker, giới hạn tài nguyên, chính sách từ chối và giám sát theo tính năng application server.
6. Kiểm thử chính trên môi trường triển khai thực tế: quyền, context, timeout, hủy, shutdown/redeploy.

### 12.2. Chạy local/test không có Jakarta EE server

Không thể giả định `new DefaultManagedTaskExecutor()` chạy trong một unit test Java SE thuần vì thiếu binding JNDI mặc định. Tùy cấu trúc dự án, có thể:

- Trong unit test, inject một `TaskExecutor` chạy trực tiếp `Runnable::run` hoặc một executor test có kiểm soát; kiểm tra logic nghiệp vụ không phụ thuộc thread cụ thể.
- Trong cấu hình local của ứng dụng, dùng `ThreadPoolTaskExecutor` với giới hạn worker/queue và shutdown rõ ràng.
- Trong integration test muốn chứng minh JNDI và managed contexts thực sự, chạy trên Jakarta EE runtime tương thích hoặc một thiết bị kiểm thử tương đương có resource được cấu hình.

Ví dụ **Java 17 độc lập** chứng minh việc code phụ thuộc vào `Executor` có thể chạy trên delegate khác nhau. **Đây chỉ là mô phỏng nguyên tắc ủy quyền**, không có JNDI, Spring hay managed executor Jakarta EE.

**Tệp `ExecutorDelegationDemo.java`**

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorDelegationDemo {
    static void dispatch(Executor executor,
                         CountDownLatch done) {
        executor.execute(() -> {
            System.out.println(
                    "Luồng xử lý: "
                    + Thread.currentThread().getName());
            done.countDown();
        });
    }

    public static void main(String[] args)
            throws InterruptedException {

        ExecutorService owner = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "demo-worker"));

        try {
            CountDownLatch done = new CountDownLatch(1);
            dispatch(owner, done);
            done.await();
            System.out.println("Caller nhận tín hiệu hoàn tất");
        } finally {
            owner.shutdown();
        }
    }
}
```

Lệnh biên dịch/chạy bằng Java 17+:

```bash
javac ExecutorDelegationDemo.java
java ExecutorDelegationDemo
```

Kết quả chính xác:

```text
Luồng xử lý: demo-worker
Caller nhận tín hiệu hoàn tất
```

Tại sao chương trình này minh họa đúng kiến trúc? Phương thức `dispatch` chỉ biết `Executor`, không tạo hay quản lý pool. `main` tạo nguồn executor cho demo và chịu trách nhiệm `shutdown` chính pool nó đã tạo. Còn trong Jakarta EE, Spring adapter nhận executor do server sở hữu qua JNDI, và **ứng dụng không được shutdown delegate managed executor**.

Tín hiệu `CountDownLatch` làm thứ tự hai dòng ra xác định; không cần dùng `Thread.sleep(...)` để “đợi cho may”. Trong ví dụ tối giản này, tác vụ luôn in trước khi gửi `countDown()`. Ứng dụng thật phải thêm xử lý exception để bảo đảm chốt chờ không bị treo nếu task có thể thất bại trước khi đếm chốt.

## 13. So sánh hai cách truy cập resource: JNDI trực tiếp và Spring adapter

Jakarta EE cho phép lookup/inject `ManagedExecutorService` trực tiếp. Nếu viết code phụ thuộc Jakarta API, về nguyên tắc có thể làm như sau:

**Đoạn Java minh họa đặt trong một Jakarta EE component (không phải chương trình độc lập):**

```java
import jakarta.enterprise.concurrent.ManagedExecutorService;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public String runWithManagedExecutor()
        throws NamingException,
               InterruptedException,
               ExecutionException {

    ManagedExecutorService managed =
            (ManagedExecutorService) new InitialContext().lookup(
                    "java:comp/DefaultManagedExecutorService");

    Future<String> result = managed.submit(
            () -> "managed result");

    return result.get();
}
```

Để dùng snippet này, cần Jakarta Concurrency API thích hợp trong classpath (thường do server cung cấp) và một component đang chạy trong môi trường JNDI hợp lệ. `javax.naming.InitialContext` vẫn thuộc API JNDI của Java SE; không đổi tên thành `jakarta.naming.InitialContext`. `get()` có thể chặn, nên không dùng theo cách này trong code cần trả lời bất đồng bộ ngay.

Cách Spring:

```java
// Đặt trong một Spring-managed component đã được inject.
private final TaskExecutor executor;

public void submitJob() {
    executor.execute(() -> System.out.println("Managed job"));
}
```

Mảnh trên không phải class độc lập; cần inject bean `TaskExecutor` từ mục 4. Khi dùng Spring adapter, tầng nghiệp vụ không cần trực tiếp import `ManagedExecutorService` hay biết tên resource JNDI.

**Hai cách không mâu thuẫn:** chọn trực tiếp Jakarta API khi thật sự cần khả năng riêng của managed executor; chọn adapter Spring khi kiến trúc chủ yếu sử dụng abstraction của Spring.

## 14. Không nhầm ManagedTaskExecutor với scheduler

Jakarta Concurrency có `ManagedScheduledExecutorService` cho tác vụ theo lịch/độ trễ. Spring có lớp khác tên:

```java
org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler
```

Lớp scheduler này dùng tên JNDI mặc định tương ứng:

```text
java:comp/DefaultManagedScheduledExecutorService
```

`DefaultManagedTaskExecutor` trong bài tập trung vào **thực thi tác vụ**, không tự biến mọi `Runnable` thành tác vụ theo lịch lặp. Việc một executor thuộc nhóm `SchedulingTaskExecutor` trong Spring không đồng nghĩa nó là `TaskScheduler` phục vụ lịch cron hoặc delay.

Nếu yêu cầu là chạy mỗi 5 phút hoặc theo biểu thức cron, hãy nghiên cứu `TaskScheduler`, `@Scheduled` và cấu hình scheduler riêng, không áp dụng `DefaultManagedTaskExecutor` như một lịch hẹn giờ tùy ý.

## 15. Quản trị tải, tính ổn định và observability

Vì resource do server sở hữu, khả năng chạy đồng thời và giới hạn queue thường được điều chỉnh qua **cấu hình application server**, không phải `setCorePoolSize(...)` trên `DefaultManagedTaskExecutor`. Cần xem tài liệu của server được triển khai cho tên property và khả năng hỗ trợ cụ thể.

Các chỉ số cần quan sát:

- Số tác vụ được gửi, đang chạy, hoàn thành, lỗi hoặc bị từ chối.
- Độ trễ từ khi gửi đến lúc bắt đầu chạy; độ sâu hàng chờ khi runtime cho phép.
- Thời gian thực thi p95/p99 và tỷ lệ quá hạn.
- Độ trễ của hệ thống ngoài mà task phụ thuộc.
- Dấu hiệu worker bị chặn ở I/O, mutex hoặc `Future.get()`.
- Hành vi khi application đang tắt, restart hoặc redeploy.

**Giới hạn đồng thời không thay thế giới hạn đầu vào.** Nếu request có thể ồ ạt tạo tác vụ dài hoặc nhiều tác vụ con, server có thể từ chối task. Ứng dụng cần quyết định trả lỗi, áp dụng backpressure, retry có giới hạn, hoặc đưa công việc vào hàng đợi bền vững.

Đừng coi “managed” là cách để tránh mọi race condition: nếu nhiều task cùng sửa trạng thái mutable của bean singleton, vẫn phải dùng chiến lược thread safety như immutable data, `AtomicLong`, concurrent collection hoặc `synchronized` với vùng khóa ngắn. Ngữ cảnh do server quản lý không tự bảo vệ dữ liệu Java.

## 16. Các lỗi triển khai thường gặp

| Sai lầm | Triệu chứng hoặc hệ quả | Cách xử lý |
|---|---|---|
| Deploy Spring Boot JAR thuần nhưng bật `DefaultManagedTaskExecutor` | Lookup `java:comp/DefaultManagedExecutorService` không tồn tại | Chọn pool Spring cho runtime standalone hoặc cung cấp Jakarta EE resource thật |
| Tưởng `new DefaultManagedTaskExecutor()` tạo pool riêng | Không thấy tùy chọn core/max pool và gặp lỗi JNDI | Hiểu đây là adapter; quản trị pool tại server |
| Cấu hình `setJndiName(...)` nhưng chưa map resource trên server | Bean không khởi tạo do không tìm thấy tên | Kiểm tra JNDI binding và kiểu đối tượng được tra cứu |
| Dùng nhầm `java:comp/env/` trước tên mặc định | Lookup sai tên | Dùng tên chuẩn; chỉ dùng tên tương đối khi cấu hình đúng `resourceRef` |
| Gọi `@Async` mà chưa bật cơ chế async | Method chạy đồng bộ | Cấu hình `@EnableAsync` hoặc cơ chế tương đương |
| Gọi `this.asyncMethod()` trong cùng bean | Bỏ qua Spring proxy ở chế độ mặc định | Tách thành phần gọi qua bean được Spring quản lý |
| Có nhiều executor nhưng không chỉ rõ executor dùng cho task | Chạy trên pool ngoài mong đợi | Dùng `@Async("managedTaskExecutor")` hoặc `@Qualifier` |
| Nghĩ các `ThreadLocal`/request context đều tự theo task | Thiếu hoặc sai context | Truyền dữ liệu cần thiết, kiểm chứng công cụ context propagation |
| Nghĩ task async kế thừa transaction của caller | Dữ liệu chưa commit hoặc không cùng transaction | Tạo transaction mới thích hợp trong task hoặc thiết kế after-commit |
| Gọi `shutdown()` trên raw managed executor | Vi phạm lifecycle Jakarta EE; có thể ném `IllegalStateException` | Để application server quản lý |
| Bỏ qua `Future`/`CompletableFuture` hoặc không có error handler | Lỗi công việc không được ghi nhận đầy đủ | Quan sát hoàn tất/lỗi; xử lý retry có giới hạn |
| Dùng `CompletableFuture.supplyAsync(...)` không truyền executor | Có thể chạy trên common pool, ngoài kỳ vọng managed | Truyền executor hoặc dùng API Jakarta managed stage thích hợp |
| Cho rằng task tồn tại qua redeploy/crash | Mất công việc chưa hoàn tất | Chọn hàng đợi bền vững nếu nghiệp vụ yêu cầu |

## 17. Checklist kiểm chứng một triển khai thật

Khi nhận một ứng dụng Spring được giao triển khai lên Jakarta EE server, hãy lần lượt xác minh:

1. **Runtime:** Server thực sự hỗ trợ Jakarta Concurrency và cung cấp `java:comp/DefaultManagedExecutorService` hoặc tài nguyên riêng được cấu hình.
2. **Tương thích thư viện:** Phiên bản Spring Framework và Jakarta EE API phù hợp với classloader của runtime; tránh trộn package `javax.enterprise.concurrent` cũ và `jakarta.enterprise.concurrent` mới.
3. **Bean:** `DefaultManagedTaskExecutor` được Spring tạo và khởi tạo thành công; nếu lookup lỗi, kiểm tra JNDI name/mapping, không vá tạm bằng pool không kiểm soát.
4. **Định tuyến:** `@Async` chỉ rõ bean hoặc inject `TaskExecutor` với `@Qualifier`, và lời gọi đi qua proxy nếu dùng `@Async`.
5. **Ngữ cảnh:** Kiểm chứng naming, classloader, security; không dựa vào request `ThreadLocal` hay caller transaction tự lan truyền.
6. **Kết quả:** Caller có con đường quan sát thành công, lỗi, timeout, hủy; task không nuốt ngoại lệ hoặc ngắt.
7. **Tải:** Server có giới hạn số task/worker rõ ràng; thử tải và kiểm tra chính sách từ chối.
8. **Vòng đời:** Ứng dụng không tự `shutdown` raw managed executor; biết rõ điều gì xảy ra khi redeploy.
9. **Độ bền:** Nếu cần bảo đảm không mất việc sau crash, có hệ thống lưu công việc bền vững thay vì chỉ gửi in-memory task.

Trong kiểm thử tích hợp, có thể ghi nhận thời điểm gửi và bắt đầu thực thi, logging correlation ID rõ ràng, lỗi lookup và lỗi từ chối; tuyệt đối không lấy một tên thread cụ thể làm bằng chứng duy nhất rằng task đang chạy trên managed executor. Bằng chứng tốt hơn gồm JNDI binding đúng kiểu, cấu hình server và telemetry của runtime.

## 18. Bài tập tự kiểm tra

**Bài 1 — Chọn runtime phù hợp.** Một ứng dụng Spring Boot chạy bằng `java -jar` không có binding JNDI. Nếu thêm bean `DefaultManagedTaskExecutor`, chuyện gì có khả năng xảy ra khi khởi tạo context? Nêu hai phương án đúng để phát triển local và triển khai lên Jakarta EE server.

**Định hướng:** Lookup mặc định không tìm thấy resource; cấu hình executor khác cho local, giữ managed executor cho môi trường Jakarta EE có resource thật.

**Bài 2 — Chỉ ra ai sở hữu pool.** Ứng dụng gọi `managedExecutor.shutdown()` trong phương thức `@PreDestroy` vì cho rằng phải dọn dẹp mọi tài nguyên. Sai ở đâu?

**Định hướng:** Lifecycle của managed executor thuộc server; các method lifecycle của `ManagedExecutorService` bị cấm cho code ứng dụng. Đóng adapter bean không có nghĩa đóng pool server.

**Bài 3 — Chọn executor với @Async.** Trong ApplicationContext có hai bean `ThreadPoolTaskExecutor` và `DefaultManagedTaskExecutor`. Làm sao bảo đảm phương thức quan trọng được gửi đến managed executor?

**Định hướng:** Đặt tên bean ổn định và dùng `@Async("managedTaskExecutor")`; caller phải đi qua proxy Spring.

**Bài 4 — Phân tích transaction.** Request đang trong `@Transactional` tạo task bằng `@Async`, task đọc một bản ghi vừa được sửa nhưng chưa commit. Có bảo đảm task thấy bản mới và cùng transaction không?

**Định hướng:** Không. Task không tự tham gia transaction của caller. Cần thiết kế transaction trong task và điều phối sau commit nếu phù hợp.

**Bài 5 — Kiểm tra context.** Một task cần `tenantId` lưu trong `ThreadLocal` của request. Vì sao dùng managed executor chưa đủ để cam kết có `tenantId`? Đề xuất cách truyền dữ liệu.

**Định hướng:** Context Jakarta EE được đặc tả quản lý khác với `ThreadLocal` tự tạo; truyền tenant ID bất biến qua argument, kiểm tra quyền ở worker và không giữ đối tượng request lâu hơn vòng đời của nó.

**Bài 6 — Thực hành Java 17.** Chạy `ExecutorDelegationDemo`, thay `ExecutorService` bằng executor inline `Runnable::run` rồi giải thích vì sao đầu ra chuyển sang thread `main` dù code `dispatch` không đổi.

**Định hướng:** `Executor` chỉ cam kết thực thi `Runnable`, không cam kết tạo thread mới. Trường hợp inline chỉ chạy ngay trên luồng gọi; adapter Spring dùng delegate theo runtime nên hành vi cần dựa vào executor thật.

## 19. Tóm tắt nguyên tắc

- **`DefaultManagedTaskExecutor` là cầu nối của Spring** tới một `Executor` qua JNDI; mặc định tra cứu `java:comp/DefaultManagedExecutorService`.
- **ManagedExecutorService thuộc Jakarta Concurrency** và do application server tạo, cấu hình, cấp phát worker, áp dụng các ngữ cảnh tương ứng và quản lý vòng đời.
- **Không có fallback pool tự động** nếu không tìm thấy JNDI resource. Chọn đúng executor cho đúng môi trường.
- **Dùng Spring TaskExecutor hoặc @Async có tên rõ ràng** để tầng nghiệp vụ không phụ thuộc trực tiếp vào cấu hình JNDI.
- **Managed context không có nghĩa mang theo mọi ThreadLocal hoặc transaction**; phải thiết kế truyền thông tin và transaction cụ thể.
- **Không shutdown tài nguyên managed executor** từ ứng dụng; đồng thời vẫn phải xử lý lỗi, giới hạn tài nguyên, timeouts, hủy, và tính bền vững của công việc.

### Tài liệu gốc để đối chiếu

- [Spring Framework API — DefaultManagedTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/DefaultManagedTaskExecutor.html)
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Jakarta EE Platform API — ManagedExecutorService](https://jakarta.ee/specifications/platform/11/apidocs/jakarta/enterprise/concurrent/managedexecutorservice)
- [Jakarta Concurrency Specification](https://jakarta.ee/specifications/concurrency/3.0/jakarta-concurrency-spec-3.0)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executor-selection-008">Chọn cách thực thi theo tải, môi trường và yêu cầu vòng đời</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn cách thực thi theo tải, môi trường và yêu cầu vòng đời

## 1. Mục tiêu và nguyên tắc chọn bộ thực thi

Một lời gọi nghiệp vụ trong Spring có thể chạy trực tiếp trên thread hiện tại, được nộp vào pool, chạy trên virtual thread, được điều phối bởi scheduler, tiếp tục trong reactive pipeline hoặc chuyển thành công việc bền vững qua hàng đợi bên ngoài. Mỗi cách có đặc tính tài nguyên, phản hồi lỗi và vòng đời khác nhau.

**Bài toán chính:** chọn **cách thực thi** phù hợp với **tải công việc**, **môi trường triển khai** và **yêu cầu vòng đời**, sau đó cấu hình giới hạn, timeout, quan sát và cơ chế dừng. Không chọn executor chỉ dựa trên tên API hay mong muốn “càng nhiều thread càng nhanh”.

Sau bài học, người học cần có khả năng:

- Mô tả tải CPU-bound, blocking I/O, non-blocking I/O, burst, tác vụ ngắn/dài và yêu cầu độ trễ.
- Phân biệt chạy đồng bộ, `TaskExecutor`/`@Async`, `TaskScheduler`/`@Scheduled`, virtual threads, Reactor và durable job.
- Tính toán sơ bộ nhu cầu concurrency từ arrival rate và thời gian xử lý, đồng thời nhận biết giới hạn CPU/connection/downstream.
- Thiết kế thread pool và queue hữu hạn, chính sách khi quá tải, timeout và cancellation; tránh tạo hàng đợi vô hạn.
- Chọn cấu hình phù hợp giữa Spring MVC, WebFlux, tác vụ batch, Jakarta EE và nhiều instance của ứng dụng.
- Phân rõ ai sở hữu executor, ai được phép shutdown, cách xử lý task đang chạy khi ứng dụng dừng và việc cần thực thi bền vững sau restart.
- Kiểm chứng lựa chọn bằng metric, load test và tình huống lỗi.

Trong bài, “executor” là thành phần quyết định *chạy task bằng cách nào*; “scheduler” quyết định *khi nào nộp/chạy task*. Một scheduler có thể cũng sử dụng thread để thực thi callback, nhưng hai trách nhiệm này cần được phân biệt.

## 2. Ba trục quyết định trước khi chọn API

### 2.1. Trục tải: công việc thực sự dùng CPU hay chờ gì?

| Dạng công việc | Đặc điểm | Cách tiếp cận ban đầu |
| --- | --- | --- |
| Tính toán CPU-bound | Ít thời gian chờ; tốn CPU thật: mã hóa, phân tích, chuyển đổi dữ liệu lớn | Pool giới hạn gần năng lực CPU đo được; tránh tăng thread vô hạn |
| Blocking I/O | Thread thường chờ JDBC, HTTP client đồng bộ, file hoặc dịch vụ bên ngoài | Pool có giới hạn rõ, hoặc virtual threads (JDK 21+) kèm giới hạn downstream |
| Non-blocking I/O | Driver/client trả tín hiệu hoàn tất mà không chiếm thread chờ toàn thời gian | Duy trì reactive/event-driven pipeline, tránh block event loop |
| Tác vụ rất ngắn | Xử lý rẻ hơn chi phí nộp queue, context switch và đồng bộ | Có thể chạy ngay trên caller thread |
| Công việc dài, có thể mất phút/giờ | Không thích hợp giữ request chờ; cần theo dõi trạng thái, retry, khôi phục | Job bất đồng bộ có định danh; dùng storage/queue bền vững nếu không được mất |
| Burst, lưu lượng thay đổi nhanh | Số task đến tức thời lớn hơn khả năng xử lý | Bounded queue, backpressure, admission control và rejection policy |
| Tác vụ định kỳ | Chạy theo giờ/khoảng thời gian | `TaskScheduler` hoặc hệ thống lịch bên ngoài; cân nhắc nhiều replica |

Một tác vụ có thể có nhiều giai đoạn. Chẳng hạn đọc từ DB là blocking I/O, biến đổi dữ liệu là CPU-bound, gọi API bên ngoài lại blocking I/O. Không cần bắt mọi giai đoạn chạy cùng một executor nếu chúng có giới hạn tài nguyên và mức ưu tiên khác nhau.

### 2.2. Trục môi trường: runtime đang quản lý thread ra sao?

- **Spring MVC / Servlet:** request thường được xử lý qua thread do web container quản lý. Nộp tác vụ sang executor có thể giải phóng request thread trong một số kiểu xử lý async, nhưng một HTTP request **chờ** `Future.get()` ngay sau khi submit vẫn có thể chặn thời gian tương tự; việc chuyển thread chỉ làm tăng công việc điều phối.
- **Spring WebFlux / Reactor:** event-loop phải được giữ không bị chặn. Blocking client trên event-loop là lỗi thiết kế dễ gây suy giảm toàn hệ thống; ưu tiên non-blocking API. Khi phải bao một blocking API, chuyển nó sang bounded scheduler thích hợp, ví dụ `Schedulers.boundedElastic()`, và đo giới hạn của nó.
- **Spring Boot độc lập (embedded server):** Spring quản lý bean executor/scheduler; cấu hình auto-configuration phụ thuộc phiên bản Boot, bean nào đã khai báo và khả năng bật virtual threads.
- **Jakarta EE / application server quản lý thread:** kiểm tra hợp đồng container; dùng `ManagedExecutorService` hoặc adapter/managed executor của Spring thay vì tự tạo thread ngoài cơ chế quản lý.
- **Batch, CLI, job ngắn hạn:** kết quả và exit code là một phần hợp đồng; phải đợi task được yêu cầu hoàn tất và shutdown executor trước khi process kết thúc.
- **Container / Kubernetes / serverless:** instance có thể bị thay thế, scale về 0 hoặc hết thời gian chạy. Không dựa vào bộ nhớ/queue trong process nếu task bắt buộc tồn tại qua restart.
- **Nhiều replica:** một `@Scheduled` gắn trong mỗi instance có thể chạy trên từng replica; lịch đơn nhất toàn cụm cần cơ chế điều phối/lock hoặc scheduler bên ngoài.

### 2.3. Trục vòng đời: task được phép tồn tại bao lâu?

Trước khi chọn executor, ghi rõ:

1. **Nguồn kích hoạt:** request, message, timer, startup, batch command hay sự kiện nghiệp vụ.
2. **Deadline:** caller cần phản hồi trong bao lâu? Task có timeout riêng không?
3. **Ownership:** ai tạo, dùng và đóng executor? Spring context, application server hay code tạo bằng `new`?
4. **Durability:** task có được mất khi JVM tắt không? Có cần lưu job vào DB/queue trước khi báo accepted?
5. **Cancellation:** caller timeout/disconnect có cần hủy task không? Task có hỗ trợ interrupt và I/O timeout không?
6. **Error/result:** lỗi trả về caller, được lưu trạng thái job, hay gửi tới dead-letter queue?
7. **Context:** transaction, security identity, locale, trace ID và request context có thể bị đổi khi chuyển thread.
8. **Quá tải:** nếu hàng chờ đầy, caller chờ, nhận lỗi, retry có giới hạn hay giảm chất lượng dịch vụ?
9. **Shutdown:** ngừng nhận task, xử lý in-flight và thời gian grace; task chưa hoàn thành được khôi phục bằng cách nào?

Nếu không thể trả lời các câu hỏi trên, cấu hình số thread sẽ chỉ là một phỏng đoán.

## 3. Sơ đồ quyết định nhanh

```text
Có bắt buộc hoàn thành trong cùng lời gọi / transaction hiện tại?
  ├─ Có, công việc ngắn và không gây chặn kéo dài
  │    └─ Chạy đồng bộ trên caller thread
  └─ Không
       ├─ Cần tồn tại sau restart, retry bền vững hoặc xử lý nhiều phút?
       │    └─ Durable job/queue/workflow + worker có giới hạn
       └─ Có thể quản lý trong vòng đời JVM
            ├─ Kích hoạt theo lịch?
            │    └─ TaskScheduler/@Scheduled; tách worker pool nếu callback dài
            └─ Kích hoạt theo request/event/task
                 ├─ Reactor non-blocking pipeline?
                 │    ├─ Có non-blocking API → giữ pipeline non-blocking
                 │    └─ Chỉ có blocking API → offload có giới hạn
                 ├─ CPU-bound → pool hữu hạn theo CPU và ưu tiên
                 └─ Blocking I/O → pool bounded hoặc virtual threads
                                     + timeout + giới hạn tài nguyên downstream
```

Đây là điểm bắt đầu, không phải thuật toán có thể bỏ qua load test. Một request có deadline ngắn vẫn có thể cần workflow durable nếu công việc nghiệp vụ bắt buộc được thực thi nhưng caller chỉ nhận job ID.

## 4. Chạy đồng bộ: lựa chọn có chủ đích

Dùng trực tiếp lời gọi phương thức khi kết quả cần ngay, công việc nhanh, các bước phải nằm cùng transaction hoặc không cần tách vòng đời. Việc tạo bất đồng bộ không đem lại lợi ích nếu caller vẫn chờ kết quả ngay lập tức.

Trong Spring có `SyncTaskExecutor`, thực thi task **ngay trong thread gọi**. Đây là lựa chọn hữu ích cho test xác định hoặc khi muốn cố ý không chuyển thread; tên “executor” không có nghĩa luôn có pool.

Tệp `SyncAndAsyncChoiceDemo.java`, chạy trên Java 17+:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SyncAndAsyncChoiceDemo {
    static int calculate(int left, int right) {
        return left + right;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("sync=" + calculate(20, 22));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> future = executor.submit(
                    () -> calculate(20, 22));
            System.out.println("async result=" + future.get());
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac SyncAndAsyncChoiceDemo.java
java SyncAndAsyncChoiceDemo
```

Kết quả:

```text
sync=42
async result=42
```

Ở bước thứ hai, công việc được submit vào thread khác, nhưng `future.get()` chặn thread chính để nhận kết quả. Nếu công việc đơn giản như phép cộng, việc tạo executor/queue còn gây overhead; nếu phép tính nặng, tách khỏi request có thể hữu ích **khi caller không cần chờ trực tiếp** hoặc đã có cơ chế trả kết quả bất đồng bộ thực sự.

Chạy đồng bộ không có nghĩa là mặc nhiên thread-safe: nếu phương thức sửa bean singleton hoặc cache chung, vẫn cần bảo vệ trạng thái và kiểm tra invariant.

## 5. ThreadPoolTaskExecutor: lựa chọn quen thuộc cho tải hữu hạn

`ThreadPoolTaskExecutor` bọc `java.util.concurrent.ThreadPoolExecutor` và có lifecycle tích hợp với Spring. Các tham số cốt lõi:

- `corePoolSize`: số worker lõi mà pool có thể giữ sẵn/duy trì theo chính sách.
- `maxPoolSize`: trần số worker khi executor cần mở rộng.
- `queueCapacity`: khả năng chứa task chưa được thực thi.
- `keepAliveSeconds`: thời gian worker dư thừa được giữ khi rảnh; còn tùy chính sách core timeout.
- `RejectedExecutionHandler`: hành vi khi không nhận được thêm task do quá tải hoặc executor đã dừng.
- `threadNamePrefix`: giúp truy vết trong log, thread dump.
- Các thiết lập shutdown/await: quy định ứng dụng xử lý task đang chạy và queue ra sao lúc context đóng.

**Đừng bỏ qua mặc định.** Với `ThreadPoolTaskExecutor` của Spring Framework, mặc định cổ điển là `corePoolSize = 1`, `maxPoolSize` rất lớn và `queueCapacity` không giới hạn thực tế. Do pool thường **đưa task vào queue sau khi đạt core trước khi mở rộng đến max**, chỉ tăng `maxPoolSize` mà vẫn để queue vô hạn có thể không tạo hiệu quả tăng worker như kỳ vọng.

### 5.1. Quy tắc nhận task của ThreadPoolExecutor

Một cách gần đúng theo thứ tự thông thường:

1. Nếu đang có ít hơn `corePoolSize` worker, tạo worker cho task.
2. Nếu đã có core worker, thử xếp task vào queue.
3. Nếu queue đầy và số worker còn dưới `maxPoolSize`, thử tạo worker tiếp.
4. Nếu không thể nhận, áp dụng rejection policy.

Ví dụ `core = 4`, `max = 8`, `queue = 64`: sau khi bốn worker lõi bận, task mới thường được xếp vào queue; khi queue đầy mới cần mở thêm worker thứ 5–8. Cấu hình này không có nghĩa tám thread đều lập tức chạy ngay khi xuất hiện tám task. Scheduling/race có thể ảnh hưởng trạng thái tức thời, nên xem đây là quy tắc hoạt động của executor chứ không phải biểu đồ thời gian cố định.

`queueCapacity = 0` khiến pool dùng cơ chế handoff trực tiếp; khi không có worker sẵn sàng, nó có xu hướng mở rộng nhanh hơn tới giới hạn max rồi từ chối. Một queue lớn giúp hấp thụ burst nhưng làm tăng thời gian chờ và memory; queue nhỏ có thể làm task bị từ chối sớm hơn. Cần xác định chính sách nào đáp ứng SLA.

### 5.2. Cấu hình Spring cho nhóm I/O blocking

Ví dụ cấu hình sau cần Spring Framework và dependency phù hợp; đặt trong tệp `AsyncExecutionConfig.java` (đây là snippet ứng dụng Spring, không chạy đơn lẻ chỉ bằng `javac`):

```java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncExecutionConfig {
    @Bean("outboundIoExecutor")
    ThreadPoolTaskExecutor outboundIoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("outbound-io-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(64);
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
```

Ý nghĩa: tại thời điểm bình thường, executor dùng tối đa bốn core worker; có thể tăng tới tám khi queue hữu hạn đã đầy; nếu không còn năng lực nhận thì từ chối thay vì giữ vô hạn request. Các giá trị 4/8/64/20 **chỉ là mẫu để hiểu cơ chế**, không phải cấu hình sản xuất mặc định.

Khi dùng `AbortPolicy`, task bị từ chối sẽ dẫn đến lỗi nộp tác vụ (Spring có thể chuyển thành `TaskRejectedException`); tầng gọi phải quyết định trả 429/503, lưu công việc bền vững, chuyển hướng, hoặc retry có giới hạn. Đừng đợi tới khi đã quá tải mới quyết định chính sách xử lý.

Cấu hình `setWaitForTasksToCompleteOnShutdown(true)` ưu tiên hoàn tất công việc đã nhận, kể cả hàng đợi, khi shutdown. `setAwaitTerminationSeconds(20)` quy định khoảng chờ trong pha shutdown theo API; **không** bảo đảm mọi task đã hoàn thành trong 20 giây và không làm task bền vững qua process crash. Hành vi shutdown chính xác còn phụ thuộc phiên bản Spring, cách đóng context và hạ tầng cấp tín hiệu; cần kiểm thử với runtime mục tiêu.

### 5.3. Chọn pool cho CPU-bound

Với tính toán CPU thuần, đặt giới hạn số công việc thực sự chạy gần mức CPU hiệu dụng, rồi đo throughput/latency trong môi trường thật (container có thể bị CPU quota). Thread nhiều hơn số lõi không tự tăng năng lực tính toán; nó có thể chỉ tăng context switching và cạnh tranh cache.

Nếu một API vừa có tính toán nặng vừa có I/O blocking, cân nhắc pool riêng cho từng loại hoặc tách pipeline. Không dùng cùng một pool nhỏ cho task CPU kéo dài và task phản hồi nhanh nếu việc đó gây starvation.

### 5.4. Khi quá tải, tác vụ nên chờ ở đâu?

Một pool có queue không giới hạn biến “không đủ năng lực” thành **độ trễ và bộ nhớ tích tụ**. Cách tiếp cận an toàn hơn:

- Giới hạn số task đang chạy, queue và số connection tới downstream.
- Đặt timeout/đường retry ở tầng API, DB và client; tránh chờ vô hạn.
- Ưu tiên phản hồi fail-fast khi deadline caller đã không thể đáp ứng.
- Dùng hàng đợi bền vững khi công việc phải được xử lý sau, kèm idempotency key và retry policy.
- Quan sát rejection, queue length, queue wait, active count, completed count, latency và lỗi.
- Nếu áp dụng `CallerRunsPolicy`, hiểu rằng task sẽ chạy trên thread caller khi pool/queue đầy. Điều này tạo backpressure nhưng có thể kéo dài thời gian xử lý HTTP hoặc chặn event-loop; không chọn cho mọi môi trường.

### 5.5. Java 17+: mô phỏng pool hữu hạn và rejection

Ví dụ runnable sau chủ động giữ task đầu tiên cho tới khi main mở latch, để kết quả không phụ thuộc vào việc “đoán” lịch thread.

Tệp `BoundedPoolChoiceDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BoundedPoolChoiceDemo {
    public static void main(String[] args) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        try {
            Future<?> running = pool.submit(() -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            Future<?> queued =
                    pool.submit(() -> System.out.println("queued task ran"));

            try {
                pool.execute(() -> {});
                System.out.println("unexpected acceptance");
            } catch (RejectedExecutionException e) {
                System.out.println("third task rejected");
            }

            release.countDown();
            running.get();
            queued.get();
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }
}
```

```bash
javac BoundedPoolChoiceDemo.java
java BoundedPoolChoiceDemo
```

Kết quả:

```text
third task rejected
queued task ran
```

Một worker đang chờ; một slot queue đã chứa task thứ hai; task thứ ba bị từ chối. Latch bảo đảm task đầu chưa giải phóng worker trước khi main kiểm tra rejection. Đây là minh họa nguyên lý tải hữu hạn của `ThreadPoolExecutor`; trong Spring, `ThreadPoolTaskExecutor` cung cấp cấu hình tương tự và chuyển đổi một số lỗi theo hợp đồng riêng.

## 6. Virtual threads: tăng concurrency cho blocking I/O, vẫn phải kiểm soát tải

Java 21 cung cấp virtual threads ổn định. Chúng cho phép rất nhiều tác vụ dùng lối lập trình blocking dễ đọc trong lúc JVM điều phối chúng trên số platform threads nhỏ hơn. Virtual threads thường có ích khi **phần lớn thời gian task chờ I/O**, không giải quyết giới hạn CPU, DB connection pool hay quota của upstream.

Trong Spring Framework hỗ trợ tương ứng (ví dụ Spring 6.1+):

- `VirtualThreadTaskExecutor` tạo virtual thread mới cho từng task.
- `SimpleAsyncTaskExecutor` có thể dùng virtual threads khi bật tùy chọn hỗ trợ và chạy trên JDK 21+; executor này không có pool tái sử dụng thread theo nghĩa truyền thống.
- Spring Boot các phiên bản hỗ trợ có thể kích hoạt virtual threads bằng `spring.threads.virtual.enabled=true`; tác động tới auto-configured executor/scheduler tùy phiên bản và các bean đã tự khai báo. Không giả định mọi executor tùy chỉnh tự chuyển thành virtual threads.

**Virtual thread không phải cơ chế giới hạn concurrency.** Với 50.000 yêu cầu cùng gọi dịch vụ chỉ cho phép 40 connection, tạo 50.000 virtual threads không biến 40 connection thành 50.000. Cần semaphore, HTTP connection pool, rate limiter, bounded queue hoặc admission control ở boundary phù hợp.

### 6.1. Ví dụ Java 21+ với virtual-thread-per-task executor

Tệp `VirtualThreadChoiceDemo.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VirtualThreadChoiceDemo {
    public static void main(String[] args) throws Exception {
        try (ExecutorService executor =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> first = executor.submit(() -> 40);
            Future<Integer> second = executor.submit(() -> 2);
            System.out.println(first.get() + second.get());
        }
    }
}
```

```bash
javac VirtualThreadChoiceDemo.java
java VirtualThreadChoiceDemo
```

Kết quả:

```text
42
```

`try` với executor (JDK 21+) đóng tài nguyên và chờ theo hợp đồng `ExecutorService.close()`. Không suy ra hai phép tính nhỏ chạy nhanh hơn chỉ vì dùng virtual threads; ví dụ minh họa lifecycle và API. Khi worker truy cập remote I/O thật, áp dụng timeout, giới hạn connection và kiểm soát số request đồng thời.

### 6.2. Giới hạn downstream bằng Semaphore

Tệp `SemaphoreLimitDemo.java`, chạy với Java 17+:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class SemaphoreLimitDemo {
    public static void main(String[] args) throws Exception {
        Semaphore permits = new Semaphore(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                jobs.add(executor.submit(() -> {
                    try {
                        permits.acquire();
                        try {
                            int now = active.incrementAndGet();
                            peak.accumulateAndGet(now, Math::max);
                            // Đại diện cho thao tác tới một tài nguyên giới hạn.
                        } finally {
                            active.decrementAndGet();
                            permits.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            for (Future<?> job : jobs) {
                job.get();
            }
            System.out.println("underLimit=" + (peak.get() <= 2));
            System.out.println("allFinished=" + (active.get() == 0));
        } finally {
            executor.shutdown();
        }
    }
}
```

```bash
javac SemaphoreLimitDemo.java
java SemaphoreLimitDemo
```

Kết quả trong lần chạy bình thường, không có tác động interrupt ngoài chương trình:

```text
underLimit=true
allFinished=true
```

Dù executor có tối đa bốn worker, semaphore chỉ cho tối đa hai task đi vào vùng dùng tài nguyên đồng thời. Trong hệ thống thực, phép acquire có thể cần timeout, task có thể cần bị từ chối và metric phải đo số task đang đợi permit. Ví dụ này không khẳng định `peak` luôn bằng hai.

### 6.3. Các vấn đề thực tế cần kiểm tra

- Blocking I/O: thư viện/driver có hỗ trợ interrupt và timeout theo mong đợi không?
- Thread-local: mỗi virtual thread có giá trị ThreadLocal riêng; dùng ThreadLocal bừa bãi ở concurrency rất cao cũng có chi phí memory/context.
- CPU-bound: virtual threads không thay thế giới hạn theo lõi.
- Số lượng task: tạo không giới hạn vẫn có thể làm cạn heap, connection, file descriptor và nguồn lực hệ thống.
- Spring Boot với virtual threads: một số pool-size property không còn điều khiển virtual-thread executor như trước; cần kiểm tra chính xác auto-config ở phiên bản đang chạy.
- Vòng đời JVM: virtual threads là daemon; ở ứng dụng chỉ còn scheduled/background virtual threads, cần cơ chế keep-alive thích hợp khi môi trường yêu cầu process tiếp tục sống. Đối với Spring Boot hỗ trợ, xem `spring.main.keep-alive`.

## 7. Async method: chọn executor cho từng loại nghiệp vụ

`@EnableAsync` bật hỗ trợ `@Async` theo cấu hình phù hợp; `@Async("beanName")` yêu cầu Spring định tuyến method tới executor được chỉ định. Điều này hữu ích khi những task có đặc tính khác nhau không nên tranh cùng pool.

Ví dụ ứng dụng Spring (tách mỗi `public class` vào đúng file, có Spring dependencies):

Tệp `LookupWorker.java`:

```java
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LookupWorker {
    @Async("outboundIoExecutor")
    public CompletableFuture<String> normalize(String input) {
        return CompletableFuture.completedFuture(
                input.trim().toUpperCase(Locale.ROOT));
    }
}
```

Tệp `LookupCaller.java`:

```java
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class LookupCaller {
    private final LookupWorker worker;

    public LookupCaller(LookupWorker worker) {
        this.worker = worker;
    }

    public CompletableFuture<String> run(String text) {
        return worker.normalize(text);
    }
}
```

Đây là ví dụ định tuyến, công việc mẫu rất nhẹ và **thực tế nên chạy đồng bộ**. Nó cho thấy Spring sẽ chọn bean executor có tên `outboundIoExecutor` ở cấu hình trước khi lời gọi tới `LookupWorker.normalize(...)` đi qua proxy Spring. Không giả định luôn là thread mới nếu chưa xác nhận interceptor, scope/proxy và executor được cấu hình đúng.

Ba lưu ý quan trọng:

1. **Self-invocation:** gọi `this.normalize(...)` trong chính bean thường không đi qua proxy của `@Async` ở chế độ mặc định, vì thế method không được offload như mong đợi.
2. **Kết quả và lỗi:** dùng `CompletableFuture`/`Future` khi caller cần quan sát thành công hoặc thất bại. Method async trả `void` không có kênh trả exception về caller; cấu hình `AsyncUncaughtExceptionHandler` và logging nếu phải dùng.
3. **Thread boundary:** context transaction, security, MDC, locale và request-scoped data không mặc nhiên được truyền. Transaction ở thread caller không tự bao trùm việc xảy ra về sau trong thread worker.

Nếu caller gọi `worker.normalize(...).join()` tức thời trên request thread, người gọi vẫn chờ. Lợi ích của concurrency phải xuất phát từ cấu trúc ứng dụng, ví dụ caller trả job ID, kết hợp nhiều future đúng cách, hoặc dùng xử lý async phù hợp với HTTP framework.

### 7.1. Một executor chung hay nhiều executor?

Một pool dùng chung dễ cấu hình nhưng có thể gây **head-of-line blocking**: batch đồng bộ hóa dữ liệu chạy dài chiếm hết worker và queue, trong khi task gửi thông báo quan trọng phải xếp sau. Tách pool có thể tạo ranh giới tài nguyên:

- `cpuExecutor`: số worker tương ứng CPU, queue ngắn, phép tính có deadline.
- `outboundIoExecutor`: cho blocking calls, giới hạn thread và connection.
- `mailExecutor`: công việc ngoài đường trả lời request, nhưng vẫn có rejection/timeout/monitor.
- `maintenanceScheduler`: kích hoạt theo lịch; việc nặng nộp tới worker pool phù hợp.

Đổi lại, quá nhiều pool khiến tổng số thread, queue và memory khó quản lý. Mỗi pool phải có **ngân sách tài nguyên** và chủ sở hữu rõ ràng; chỉ tách khi có lý do về workload, ưu tiên hoặc fault isolation.

## 8. Scheduling: khi nào chạy và chạy bao nhiêu lần?

Dùng `TaskScheduler`/`@Scheduled` khi trigger theo thời gian: quét dữ liệu định kỳ, refresh cache, gửi báo cáo nhỏ. Chọn một trong các cách phổ biến:

- `fixedDelay`: đo thời gian chờ sau khi lượt trước kết thúc rồi mới kích hoạt lần kế tiếp; không cố giữ điểm đầu các lượt cách đều nhau.
- `fixedRate`: lịch kích hoạt theo nhịp đều đặn; nếu task chậm hoặc không đủ worker, thời điểm thực tế có thể trễ. Không khẳng định một tác vụ đơn có thể tự động overlap với chính nó trong mọi implementation.
- `cron`: kích hoạt theo lịch biểu. Cần quan tâm timezone, giờ mùa hè (nếu áp dụng), restart và việc bỏ lỡ lịch.
- `TaskScheduler.schedule(...)`: nộp callback theo `Instant`, `Trigger` hoặc chu kỳ qua API, thay vì annotation cố định.

`ThreadPoolTaskScheduler` thuộc nhóm Spring-managed scheduler. Số scheduler worker quá nhỏ có thể làm một callback chậm trì hoãn callback khác. Không mặc định cho rằng tăng kích thước scheduler là đủ khi callback giữ connection lâu; có thể cần **scheduler nhẹ chỉ điều phối**, còn công việc được xử lý bởi worker pool giới hạn.

Một fragment cấu hình Spring (không phải chương trình Java chạy độc lập):

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
class SchedulingConfig {
    @Bean
    ThreadPoolTaskScheduler maintenanceScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("maintenance-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(15);
        return scheduler;
    }
}
```

Cấu hình trên có ý nghĩa trong Spring context và cần test overlap/shutdown. Nó không bảo đảm các job chạy đúng một lần trong toàn cụm. Mỗi replica của ứng dụng có bean `@Scheduled` riêng, nên khi deploy ba instance có thể có ba lần thực thi cùng lịch. Nếu yêu cầu đúng một lần trên toàn hệ thống, hãy dùng scheduler tập trung, leader election, lease/lock phân tán hoặc hàng đợi có consumer coordination phù hợp; thiết kế job idempotent vì hệ phân tán vẫn có thể retry hoặc gặp failure giữa chừng.

**Không nhầm scheduler với job queue bền vững.** Lịch trong memory bị ảnh hưởng bởi JVM restart và node outage. Công việc tài chính hoặc nghiệp vụ không được phép mất cần có kế hoạch ghi nhận trạng thái, retry, kiểm soát duplicate và theo dõi tồn đọng.

## 9. Reactive / WebFlux: đừng chặn event-loop

Trong WebFlux, reactor pipelines hỗ trợ mô hình non-blocking khi toàn bộ thao tác I/O tương ứng không chặn. Nếu một endpoint reactive trực tiếp gọi một JDBC/HTTP client blocking trên event-loop, thread dùng chung có thể bị giữ chờ, ảnh hưởng nhiều request.

Nếu có driver/client reactive tương thích, ưu tiên sử dụng theo pipeline. Nếu chưa thể thay blocking dependency, bọc công việc bằng `Mono.fromCallable(...)` và `subscribeOn(Schedulers.boundedElastic())` tại boundary phù hợp:

```java
// Fragment: giả định đang ở service dùng Reactor và blockingClient đã tồn tại.
Mono<Result> result = Mono.fromCallable(
        () -> blockingClient.fetch(id))
    .subscribeOn(Schedulers.boundedElastic());
```

Đoạn trên cần import Reactor, kiểu `Result` và client trong project, nên **không** phải chương trình Java độc lập. `boundedElastic` là scheduler hữu hạn cho công việc blocking; nó không làm blocking API trở thành non-blocking thực sự và vẫn có giới hạn queue/tài nguyên. Với throughput lớn, phải kiểm tra backpressure, connection pool, timeout và mức concurrency downstream.

Không mặc nhiên chuyển mọi Reactor operator qua `@Async` hoặc `parallel()`. `parallel()` là lựa chọn cho công việc CPU thích hợp, còn `boundedElastic()` thường dành cho adapter tới code blocking; việc sử dụng sai làm tăng context switching mà không nâng throughput.

## 10. Môi trường Spring Boot, Java và container: kiểm tra cấu hình đang thực sự có hiệu lực

Spring Boot có cơ chế auto-configuration cho task execution/scheduling, nhưng hành vi phụ thuộc phiên bản, bean executor đang có và các thành phần tích hợp với nó. Với ứng dụng có cấu hình tùy chỉnh:

- Xác định executor nào thật sự được `@Async` chọn, có `@Async("name")` hoặc `AsyncConfigurer` nào không.
- Xác định bean executor nào Spring MVC/WebFlux, WebSocket, GraphQL hoặc thành phần khác dùng; **không** suy ra chúng đều dùng cùng một bean tùy chỉ vì có một bean `Executor` trong context.
- Xem application profile, environment variables và cấu hình ghi đè khi chạy local/test/staging/production.
- Xem CPU quota/memory limit thật của container, không lấy duy nhất số lõi máy chủ vật lý.
- Xem JDK (17/21+), phiên bản Spring/Boot và trạng thái bật virtual threads. Các thuộc tính chỉnh pool có thể không còn cùng ý nghĩa nếu executor được thay bằng virtual-thread strategy.

Ví dụ properties của Spring Boot có thể cấu hình pool auto-configured khi phù hợp, **không** bảo đảm áp dụng cho `@Bean` `ThreadPoolTaskExecutor` do chính bạn tạo:

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 64
      thread-name-prefix: app-task-
    scheduling:
      pool:
        size: 2
```

Kiểm chứng bean được tạo trong context và metric thực tế; việc một property có mặt trong YAML không chứng minh nó đang chi phối executor mà method đã sử dụng. Nếu ứng dụng có nhiều executor, ưu tiên tên bean/qualifier rõ ràng hơn suy luận dựa trên mặc định.

**Java 21 và virtual threads:** với các phiên bản Boot hỗ trợ, `spring.threads.virtual.enabled=true` cho phép chọn execution strategy dựa trên virtual threads; các pool configuration của platform threads có thể không còn tác dụng như trước. Quyết định kích hoạt cần bao gồm khả năng library tương thích, hành vi blocking, lượng ThreadLocal, tải database và cơ chế giữ JVM sống cho background tasks.

### 10.1. Trong Jakarta EE application server

Nếu hạ tầng quản lý thread qua container, việc tự tạo `new Thread` hoặc một `Executors.newFixedThreadPool` không được quản lý có thể vi phạm quy ước về classloader, context và shutdown. Spring có adapter cho managed executor/scheduler (ví dụ `DefaultManagedTaskExecutor` và `DefaultManagedTaskScheduler` khi môi trường tương ứng hỗ trợ); đây là bài toán ownership, không phải chỉ đổi API.

### 10.2. Trong test

Một executor chạy đồng bộ như `SyncTaskExecutor` giúp kiểm tra logic có tính xác định khi thread boundary không phải đối tượng test. Tuy nhiên test lỗi concurrency phải chạy bằng executor bất đồng bộ thật, latch/barrier và timeout rõ ràng. Không dùng test đồng bộ để tuyên bố race condition đã được giải quyết.

### 10.3. Trong môi trường container nhiều replica

Giới hạn tổng khả năng của upstream cần tính trên **toàn hệ thống**: nếu một service có 10 replica và mỗi instance cho phép 50 concurrent DB calls, tổng tối đa lý thuyết có thể là 500 concurrent calls, lớn hơn connection capacity của DB. Per-process semaphore chỉ hạn chế từng instance; muốn giới hạn toàn cụm cần kiến trúc admission/rate limiting tương ứng hoặc điều chỉnh tổng ngân sách theo replica.

## 11. Ước lượng năng lực: throughput, latency, concurrency, queue

Đừng cấu hình “max = 200 thread” mà không có dữ liệu. Ba đại lượng nền tảng:

- **Arrival rate** (λ): task/giây đến hệ thống.
- **Residence/response time** (W): thời gian trung bình mỗi task ở trong hệ thống (có thể gồm queue và service).
- **Số task đang hiện diện trung bình** (L): với hệ ổn định và cùng phạm vi đo, Little's Law cho `L = λ × W`.

Ví dụ: 100 yêu cầu/giây, thời gian một yêu cầu trong vùng đang xem là 0,4 giây, thì **trung bình khoảng 40 yêu cầu** tồn tại đồng thời trong vùng đó. Điều này **không** tự suy ra cần 40 CPU threads: nếu chúng phần lớn chờ I/O, số thread/virtual threads/connection cần quản lý khác nhau; nếu toàn bộ là CPU-bound, năng lực CPU sẽ là rào cản.

### 11.1. Một quy trình capacity planning tối thiểu

1. Đo p50/p95/p99 service time và queue wait trong từng nhóm tác vụ, tách CPU và blocking wait.
2. Đo arrival rate trung bình và peak, burst duration, deadline và khả năng chịu lỗi.
3. Đo CPU hiệu dụng, memory, database connection pool, outbound HTTP connection limit và quota API.
4. Đặt trần concurrency cho **điểm nghẽn thực**, rồi chọn pool/virtual-thread strategy tương ứng.
5. Chọn queue capacity theo burst có thể hấp thụ trong budget chờ; không để queue lớn hơn mức làm deadline hết hạn.
6. Đặt rejection policy, fallback và retry/backoff; kiểm tra dữ liệu có idempotent hay không.
7. Load test bình thường, burst và thời gian dài; đo throughput, p95/p99 latency, queue occupancy, active count, reject, timeout, error, CPU và memory.
8. Kiểm tra shutdown, deploy rolling, mạng chậm và downstream failure; điều chỉnh theo kết quả.

Một hàng đợi chứa 1.000 task với khả năng xử lý 20 task/giây sẽ cần khoảng 50 giây chỉ để giải phóng số task đó nếu không có task mới và tốc độ không đổi. Nếu caller chỉ chờ được 2 giây, queue lớn như vậy không giải quyết vấn đề. Dùng mô hình đơn giản này để phát hiện cấu hình vô lý, rồi xác thực bằng load test thực.

### 11.2. Công thức gần đúng cho CPU và blocking I/O

Đối với CPU-bound, điểm bắt đầu thường là mức concurrency tương đương số core hiệu dụng (có hiệu chỉnh cho tác vụ khác và JVM), sau đó đo. Với blocking I/O, số thread cần có thể lớn hơn vì mỗi thread chỉ sử dụng CPU trong một phần thời gian; tuy nhiên giới hạn kết nối và latency của hệ thống đích thường quyết định trần hợp lý.

Các công thức kinh nghiệm dựa trên wait/service ratio chỉ là **ước lượng ban đầu**. Không áp dụng cứng nhắc khi task trộn nhiều pha, thread virtual, downstream có rate limit, hoặc queue wait thay đổi lớn dưới tải.

## 12. Chọn theo ưu tiên, độ tin cậy và khả năng cách ly lỗi

Không chỉ phân loại CPU/I/O; ứng dụng còn có nhiều **mức độ quan trọng**.

| Nhóm công việc | Mục tiêu | Mẫu giải pháp |
| --- | --- | --- |
| Xác thực và trả API tương tác | p95 latency thấp, trả lỗi sớm khi quá tải | Đồng bộ ngắn hoặc offload giới hạn; queue nhỏ, timeout rõ |
| Gọi đối tác HTTP blocking | Kiểm soát fan-out và quota | Bounded I/O pool hoặc virtual threads + semaphore/connection pool |
| Tính toán báo cáo nặng | Giữ CPU ổn định, không gây đói request | CPU pool riêng, job ID nếu xử lý lâu |
| Email/thông báo không cần trong response | Chấp nhận trả kết quả trước khi gửi | Job queue bền vững nếu không được mất; worker có retry |
| Refresh cache định kỳ | Chấp nhận trễ một ít | Scheduler, deadline, tránh các lượt refresh chồng không cần thiết |
| Kiểm toán/thanh toán/bước nghiệp vụ quan trọng | At-least-once với xử lý duplicate an toàn và audit | Persistence + transactional outbox/queue/workflow khi phù hợp |

**Isolation:** nếu job đòi hỏi khả năng phục hồi và mức ưu tiên khác nhau, chia tài nguyên theo nhóm, hoặc dùng consumer process riêng. Không để việc tải ảnh ồ ạt chiếm hết connection pool, rồi gây timeout cho các giao dịch quan trọng.

**Task priority:** `ThreadPoolExecutor` mặc định không biến một FIFO queue thành bộ lập lịch nghiệp vụ có thứ tự ưu tiên tuyệt đối; nếu có priority queue cũng cần ngăn starvation, bảo đảm thread safety và phù hợp cancellation/metrics. Thường đơn giản hơn khi tách queue/pool theo criticality đã được xác định.

## 13. Vòng đời executor và shutdown có kiểm soát

Quyền sở hữu executor quyết định ai được gọi shutdown:

| Chủ sở hữu | Cách dùng | Đóng khi nào |
| --- | --- | --- |
| Spring-managed `@Bean ThreadPoolTaskExecutor` | Inject/`@Async` theo tên và để Spring điều phối | Khi application context đóng theo lifecycle tích hợp |
| Spring-managed `ThreadPoolTaskScheduler` | Trigger lịch bằng cấu hình Spring | Khi context đóng, với chính sách hoàn tất/hủy đã xác định |
| Executor do một phương thức tạo riêng | Chỉ dùng trong công việc cục bộ có phạm vi rõ | Chính phương thức phải `shutdown`/await trong `finally` |
| Container-managed executor của Jakarta EE | Dùng thông qua abstraction được cấp | Lifecycle thuộc container; ứng dụng không tự đóng shared resource |
| Queue/worker ngoài process | Publish job và nhận kết quả/trạng thái qua giao thức | Lifecycle do broker/worker manager/cluster kiểm soát |

Quy trình shutdown lý tưởng với in-memory executor:

1. **Ngừng nhận việc mới** hoặc chuyển sang trạng thái not-ready; xử lý rejection một cách có chủ đích.
2. **Đặt giới hạn chờ** cho tác vụ đã nhận; chờ những công việc cần hoàn thành trong grace period.
3. **Yêu cầu hủy hợp tác** cho task có thể bỏ qua: interrupt, cancellation, timeout của client/DB và đóng tài nguyên.
4. **Ghi nhận công việc dở dang** nếu nghiệp vụ cần retry; nếu chỉ ở RAM thì không thể bảo đảm phục hồi sau crash.
5. **Đợi theo deadline**, ghi metric/log tình trạng cuối rồi thoát; hạ tầng có thể kết thúc process sau grace period.
6. **Kiểm tra restart**: không chạy trùng job đã hoàn tất, không bỏ sót job chưa hoàn tất; có cơ chế idempotency/deduplication.

Spring-managed executor không nên bị mỗi service gọi `shutdown()` giữa chừng. Nếu nhiều consumer dùng cùng bean executor, một consumer tự đóng nó sẽ gây lỗi nộp task cho các consumer khác.

### 13.1. Java 17+: ví dụ shutdown và hủy hợp tác

Tệp `CooperativeShutdownDemo.java` minh họa gửi tín hiệu interrupt qua `Future.cancel(true)` và đợi executor kết thúc:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CooperativeShutdownDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicBoolean observedInterrupt = new AtomicBoolean(false);

        try {
            Future<?> job = executor.submit(() -> {
                started.countDown();
                try {
                    neverReleased.await();
                } catch (InterruptedException e) {
                    observedInterrupt.set(true);
                    Thread.currentThread().interrupt();
                }
            });

            started.await();
            job.cancel(true);
        } finally {
            executor.shutdown();
        }

        boolean terminated =
                executor.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("terminated=" + terminated);
        System.out.println("interruptSeen=" + observedInterrupt.get());
    }
}
```

```bash
javac CooperativeShutdownDemo.java
java CooperativeShutdownDemo
```

Kết quả trong môi trường chạy bình thường, khi worker phản hồi interrupt trong thời hạn năm giây:

```text
terminated=true
interruptSeen=true
```

`cancel(true)` chỉ yêu cầu interruption; mã task quyết định phản hồi. Nếu task bị kẹt ở I/O không hỗ trợ interruption, hoặc không kiểm tra trạng thái, có thể không dừng trong thời gian kỳ vọng. `awaitTermination` trả boolean để caller phân biệt đã kết thúc và còn task; không nên bỏ qua trường hợp `false` trong production.

### 13.2. Khi nào cần job bền vững thay vì executor trong process?

Chọn durable job khi có ít nhất một điều kiện:

- Job bắt buộc hoàn thành dù JVM/process restart.
- Phải retry sau khi DB/API bên ngoài tạm thời lỗi.
- Cần xem trạng thái `PENDING/RUNNING/SUCCEEDED/FAILED`, kiểm toán hoặc làm lại một cách chủ đích.
- Có thể mất nhiều phút/giờ hoặc lớn hơn deadline HTTP.
- Scale worker độc lập với web app hoặc điều tiết tốc độ tiêu thụ.

Kịch bản thích hợp: request ghi nhận công việc (hoặc transactional outbox), phản hồi `202 Accepted` với job ID, worker nhận job từ hệ thống bền vững, cập nhật trạng thái và dùng idempotency key để chống tác động lặp. “Nhận được 202” chỉ có nghĩa server **đã nhận yêu cầu theo hợp đồng đã định**; nếu chưa ghi bền vững thì không được hứa job chắc chắn sống qua restart.

## 14. Timeout, retry, backpressure và cancellation gắn với lựa chọn executor

Nhiều sự cố bắt nguồn từ việc offload mà không có **deadline**:

- `Future.get(timeout, unit)` chỉ giới hạn thời gian **thread gọi chờ**; hết hạn không tự động hủy worker. Quyết định `cancel(true)` cần có chính sách rõ.
- `CompletableFuture.orTimeout(...)` đặt trạng thái completion của future; không tự bảo đảm dừng thao tác I/O vật lý đã bắt đầu. Vẫn phải cấu hình timeout trong client/driver và cancellation nếu API hỗ trợ.
- HTTP client/DB timeout thường cần ít nhất connect/read/total deadline phù hợp; queue wait cũng phải nằm trong ngân sách độ trễ tổng.
- Retry không giới hạn trong task đang giữ thread có thể nhân tải lên đúng lúc downstream đang hỏng. Dùng bounded retry với backoff/jitter và idempotency khi side effect có thể xảy ra.
- `Semaphore`/rate limiter áp dụng ở boundary downstream hữu ích ngay cả khi executor có hàng nghìn virtual threads.
- Rejection policy là thành phần của API contract: task có thể không được nhận; cần quyết định trả lỗi, retry hay persist, không âm thầm làm mất việc.

Nếu thời gian còn lại của deadline đã nhỏ hơn ước lượng queue wait, nhận task rồi để nó chết vì timeout trong queue là cách lãng phí tài nguyên. Backpressure và fail-fast có thể bảo vệ p95 latency tốt hơn việc cho mọi task vào queue.

### 14.1. Không chuyển transaction hoặc request state một cách vô tình

`@Async` thường đổi thread; Spring transaction context và request-scoped state gắn thread caller không tự động có mặt ở worker. Tránh chuyển nguyên `HttpServletRequest`, persistence entity đang được quản lý hoặc DTO mutable sang background task lâu dài. Thay vào đó truyền ID/immutable snapshot cần thiết; task mở transaction riêng tại boundary của nó khi cần.

MDC/trace context và security identity cần propagation có chủ đích, kiểm thử đường cleanup khi worker thread được tái sử dụng. Spring có `TaskDecorator` và các tích hợp context propagation thích hợp, nhưng decorator không thay thế ownership và không bảo đảm tự động chuyển mọi thread-local/transaction.

## 15. Kiểm thử đúng ba trục: tải, môi trường, vòng đời

Một cấu hình chỉ được xem là phù hợp sau khi kiểm tra hành vi dưới điều kiện của chính hệ thống.

### 15.1. Kiểm thử tải

- **Steady-state:** arrival rate thấp hơn khả năng xử lý; queue có ổn định, p95 có đạt SLO?
- **Burst:** tăng lưu lượng đột ngột; queue đạt đỉnh bao nhiêu, rejection xảy ra khi nào?
- **Overload dài:** arrival rate cao hơn capacity; hệ thống có fail-fast hoặc giữ memory ổn định không?
- **Downstream chậm/mất kết nối:** timeout có ngăn task giữ worker vô hạn không?
- **CPU quota giảm:** ứng dụng có đang tạo quá nhiều worker so với CPU thực không?
- **Multi-replica:** tổng connection, rate-limit và scheduler duplicate được kiểm soát chưa?

### 15.2. Kiểm thử môi trường

Xác minh executor **thực sự được dùng** bằng bean name, thread-name prefix, metrics và quan sát thread dump. Với Spring Boot, chạy dưới đúng profile và phiên bản runtime. Với WebFlux, dùng công cụ phát hiện blocking trên event-loop khi thích hợp. Với virtual threads, đo cả tốc độ I/O, memory, ThreadLocal và giới hạn downstream.

### 15.3. Kiểm thử vòng đời

- Đóng Spring context khi còn queue và task chạy; task nào hoàn tất/hủy, có shutdown đúng thời hạn?
- Thử nộp task sau khi executor đã bắt đầu shutdown; caller nhận rejection ra sao?
- Gửi SIGTERM / rolling update trong môi trường tương tự production; kiểm tra grace period và readiness.
- Restart trong khi durable job đang chạy; job có được khôi phục/retry và có tác động lặp không?
- Test cancellation với `CountDownLatch`/barrier có timeout; không dựa vào `Thread.sleep` để đoán thứ tự.
- Với `@Async`, xác minh lời gọi thực sự đi qua proxy và exception được quan sát.

Metric nên gắn **tên nhóm công việc/executor** và trace ID khi phù hợp: active worker, pool size, queue depth, queue wait, tasks completed, rejected count, task duration, error/timeout/cancelled, CPU, heap, GC và độ trễ p95/p99. Không để log/metric rò thông tin cá nhân trong payload.

## 16. Các lựa chọn đối chiếu theo tình huống

| Tình huống | Cách thực thi đáng thử trước | Điều kiện bắt buộc |
| --- | --- | --- |
| Validate DTO và tính phí đơn giản trong request | Đồng bộ | Độ trễ nhỏ, invariant/transaction rõ |
| Gọi ba REST API blocking độc lập trong request MVC | I/O executor có giới hạn hoặc virtual threads với API tổng hợp kết quả | Timeout từng API, hạn mức kết nối, deadline request |
| Mã hóa hàng nghìn file lớn | CPU pool riêng, công việc có thể phân lô | Giới hạn CPU/memory và queue, cancellation |
| Ứng dụng WebFlux dùng reactive HTTP client | Reactor non-blocking pipeline | Không block event-loop, backpressure |
| WebFlux phải dùng SDK blocking tạm thời | Adapter `boundedElastic` | Giới hạn blocking, bảo vệ downstream và kế hoạch thay SDK |
| Gửi email sau khi API trả 202, không được mất | Durable queue/job worker | Persist trước khi xác nhận, retry, idempotency |
| Đồng bộ cache vào lúc 2 giờ sáng trên ba replica | Scheduler có điều phối toàn cụm | Không chạy ba bản sao ngoài ý muốn, job idempotent |
| Ứng dụng command-line xử lý một danh sách rồi thoát | Executor local hữu hạn + await | Đợi kết quả, đóng executor, exit code phản ánh lỗi |
| Jakarta EE server cung cấp managed executor | Managed executor/scheduler | Tuân thủ lifecycle/container context |
| Test unit muốn hành vi xác định | Executor đồng bộ | Vẫn có test concurrency độc lập |

## 17. Các sai lầm thường gặp

| Sai lầm | Vì sao có hại | Quy tắc thay thế |
| --- | --- | --- |
| “Tất cả xử lý lâu đều dùng `@Async`.” | Không có job ID, timeout, kết quả hoặc durability; lỗi có thể bị bỏ qua | Xác định hợp đồng với caller và thời gian sống trước |
| “`maxPoolSize` đủ lớn thì tự mở nhiều thread.” | Queue dài có thể giữ pool ở `corePoolSize` | Hiểu cơ chế core → queue → max → rejection |
| “Queue không giới hạn giúp tránh lỗi.” | Dời lỗi thành latency/memory và task hết deadline | Bounded queue, admission/rejection rõ |
| “Virtual threads không cần giới hạn concurrency.” | DB/API quota, heap và file descriptor vẫn hữu hạn | Semaphore/connection pool/rate limit tại nguồn tài nguyên |
| “Đã chuyển sang async là request không block.” | Caller vẫn có thể `get()/join()` ngay | Thiết kế async end-to-end hoặc trả job ID |
| “`@Async` luôn chạy khác thread.” | Self-invocation có thể bỏ qua proxy | Kiểm tra call path và bean executor |
| “Tăng scheduler pool đảm bảo mỗi cron chạy đúng một lần toàn cụm.” | Mỗi replica có scheduler riêng | Điều phối phân tán hoặc scheduler ngoài JVM |
| “`@Scheduled` là queue bền vững.” | Restart/stop có thể làm mất lần thực thi | Persist job nếu nghiệp vụ đòi hỏi |
| “`shutdownNow()` chắc chắn dừng mọi task.” | Interrupt mang tính hợp tác | Timeout, cancellation và cleanup ở task |
| “Cấu hình YAML chắc chắn áp dụng cho pool hiện tại.” | Có thể bị bean tùy chỉnh/phiên bản/virtual threads thay đổi | Kiểm chứng bean runtime và metric |
| “`Future.get(timeout)` đã dừng I/O.” | Hết thời gian chờ không phải hủy thao tác | Timeout trong client, cancellation, deadline tổng |
| “Một pool chung là đơn giản và đủ.” | Heavy tasks gây starvation cho nghiệp vụ quan trọng | Cách ly pool/queue theo priority và ngân sách |

## 18. Bài thực hành: thiết kế executor cho một hệ thống ba loại việc

Giả sử ứng dụng Spring có: (A) endpoint cần gọi API sản phẩm mất trung bình 200 ms, (B) báo cáo CPU nặng mất vài giây, (C) job đồng bộ trạng thái đối tác chạy định kỳ và phải tiếp tục sau restart.

### Yêu cầu thực hiện

1. Ghi bảng cho từng task: CPU hay I/O, concurrency peak, deadline, quota downstream, kết quả, lỗi và owner.
2. Đề xuất đường thực thi cho A: request blocking, MVC async hay end-to-end reactive; chọn bounded I/O pool/virtual threads phù hợp và giới hạn connection; giải thích caller chờ hay nhận future.
3. Đề xuất B: CPU pool riêng, queue hữu hạn, trả job ID nếu vượt HTTP deadline, test CPU quota và cancellation.
4. Đề xuất C: scheduler chỉ kích hoạt, durable job record và worker có cơ chế retry; tránh duplicate khi nhiều replica.
5. Đặt cấu hình khởi đầu mang tính giả thuyết: core/max/queue hoặc virtual-thread strategy, timeout, queue rejection, maximum downstream concurrency và shutdown grace.
6. Tạo kịch bản load steady-state, burst, một downstream treo, rolling restart và hai request cạnh tranh cùng pool.
7. Thu metric: queue wait, active worker, reject, error, p95/p99, CPU, số connection; điều chỉnh cấu hình dựa trên bottleneck thực.
8. Mô tả hợp đồng khi task bị từ chối: API trả mã gì, job có được lưu trước không, client có thể retry an toàn không.
9. Xác minh shutdown: không nhận việc mới khi not-ready; task đã nhận được xử lý/hủy theo SLA; không mất durable job.

### Tiêu chí đạt

- [ ] Tôi giải thích được vì sao “càng nhiều thread càng tốt” là sai với CPU-bound và downstream có quota.
- [ ] Tôi chọn được giữa synchronous, bounded pool, virtual threads, scheduler, Reactor và durable worker dựa trên đặc tính task.
- [ ] Tôi hiểu `corePoolSize`/`maxPoolSize`/`queueCapacity` và thời điểm reject.
- [ ] Tôi không dùng queue vô hạn để che quá tải hoặc để job vượt deadline.
- [ ] Tôi định nghĩa được timeout, cancellation, kết quả/lỗi và chính sách retry trước khi triển khai.
- [ ] Tôi biết sự khác nhau giữa môi trường MVC, WebFlux, application server và nhiều replica.
- [ ] Tôi xác định đúng ai sở hữu executor và quy trình graceful shutdown/restart.
- [ ] Tôi kiểm chứng cấu hình runtime thay vì suy từ YAML hoặc tên bean.
- [ ] Tôi có load test và failure test cho quyết định chọn executor.

**Điểm then chốt:** executor là một phần của **hợp đồng tài nguyên và vòng đời**. Cách thực thi phù hợp phải vừa bảo vệ CPU, connection và deadline dưới tải, vừa khớp mô hình thread của môi trường, vừa có đường xử lý hoàn tất, lỗi, quá tải, hủy và restart được kiểm chứng.

## 19. Tài liệu chính thức để đối chiếu theo phiên bản đang dùng

- Spring Framework Reference — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `TaskScheduler`, `@Async`, `@Scheduled` và các lựa chọn implementation.
- Spring Framework API — [ThreadPoolTaskExecutor 6.1](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): default queue/pool, tham số core/max/capacity và lifecycle.
- Spring Boot Reference — [Task Execution and Scheduling (3.4)](https://docs.spring.io/spring-boot/3.4/reference/features/task-execution-and-scheduling.html): nguyên tắc auto-configuration và phân biệt executor dùng bởi các thành phần.
- Spring Boot Reference — [Virtual Threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html): yêu cầu Java 21+, cấu hình và các vấn đề lifecycle/keep-alive.

Tài liệu Spring/Boot và JDK có thể thay đổi theo phiên bản. Khi triển khai, kiểm tra chính xác release đang dùng thay vì lấy mặc định từ tài liệu của một release khác.


</details>

- [Quay lại đầu trang](#back-to-top)
