<a id="back-to-top"></a>

# Nhóm luồng

## Menu
- [Khai báo ThreadPoolTaskExecutor thành bean do container quản lý](#thread-pool-001)
- [corePoolSize và maxPoolSize](#thread-pool-002)
- [queueCapacity và lựa chọn hàng đợi nội bộ](#thread-pool-003)
- [Thứ tự nhận tác vụ: tạo luồng cơ bản, xếp hàng, mở rộng rồi từ chối](#thread-pool-004)
- [Vì sao hàng đợi không giới hạn làm maxPoolSize khó phát huy tác dụng](#thread-pool-005)
- [queueCapacity bằng 0 và chuyển giao trực tiếp](#thread-pool-006)
- [keepAliveSeconds và allowCoreThreadTimeOut](#thread-pool-007)
- [Khởi động trước luồng cơ bản bằng prestartAllCoreThreads](#thread-pool-008)
- [Đặt tên luồng bằng threadNamePrefix hoặc ThreadFactory](#thread-pool-009)
- [Phân biệt giá trị mặc định của Framework với cấu hình do ứng dụng cung cấp](#thread-pool-010)

## <a id="thread-pool-001">Khai báo ThreadPoolTaskExecutor thành bean do container quản lý</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo ThreadPoolTaskExecutor thành bean do container quản lý

> **Chủ đề:** Spring Framework – Concurrency / Nhóm luồng.  
> **Mục tiêu chính:** tạo một `ThreadPoolTaskExecutor` dưới dạng Spring bean, cấu hình rõ kích thước pool và hàng đợi, inject đúng bean vào nơi sử dụng, đồng thời giao cho **Spring container quản lý khởi tạo và hủy tài nguyên**. Một executor chạy đúng nghiệp vụ còn phải có chính sách xử lý quá tải, shutdown và quan sát trạng thái phù hợp.

## 1. Kết quả học tập

Sau khi hoàn thành, bạn có thể:

1. Nêu được sự khác nhau giữa `new ThreadPoolTaskExecutor()` như đối tượng Java thông thường và đối tượng được khai báo bằng `@Bean`/đăng ký trong `ApplicationContext`.
2. Viết cấu hình Java dùng `@Configuration` và `@Bean("...")` cho `ThreadPoolTaskExecutor`, không tự gọi `initialize()` hoặc `shutdown()` khi container sở hữu bean.
3. Hiểu tác động của `corePoolSize`, `maxPoolSize`, `queueCapacity`, `keepAliveSeconds`, `threadNamePrefix` và `RejectedExecutionHandler`.
4. Sử dụng constructor injection và `@Qualifier` khi có nhiều executor; chỉ định executor bằng `@Async("beanName")` khi cần.
5. Chạy ví dụ Spring Context + Java 17 có kiểm tra thread thực thi, bean identity và shutdown bằng tín hiệu đồng bộ, thay vì `Thread.sleep()`.
6. Thiết kế hành vi khi quá tải và dừng ứng dụng có chủ đích; tránh rò rỉ thread, mất task và vô tình gây chậm request.

## 2. ThreadPoolTaskExecutor là gì?

`org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor` là lớp Spring bọc `java.util.concurrent.ThreadPoolExecutor` và cung cấp giao diện `TaskExecutor`/`AsyncTaskExecutor`. Nó cho phép cấu hình các thuộc tính quen thuộc của thread pool bằng setter kiểu JavaBean, ví dụ:

- Số luồng cơ bản và tối đa.
- Sức chứa hàng đợi.
- Thời gian giữ luồng nhàn rỗi.
- Tiền tố tên thread.
- Cách xử lý khi executor không thể tiếp nhận task.
- Cách phản ứng khi Spring context đóng.

Ứng dụng gửi tác vụ qua `execute(Runnable)` hoặc một API trả `Future` phù hợp; Spring executor dùng worker/queue để chạy tác vụ theo cấu hình. Lời gọi `execute` **không phải** một yêu cầu tạo thread mới cho mỗi task.

### Khi nào nên dùng?

Dùng `ThreadPoolTaskExecutor` khi cần giới hạn và quản lý việc thực thi tác vụ trong **một JVM**, chẳng hạn xử lý ảnh, gửi email, tính toán báo cáo hoặc tác vụ tích hợp có thể thực hiện bất đồng bộ. Để bảo đảm tác vụ sống sót sau crash/restart, cần thêm cơ chế lưu bền như message queue/job store; thread pool nằm trong bộ nhớ không đảm nhiệm chức năng đó.

### Một bean được container quản lý có ý nghĩa gì?

Sơ đồ vòng đời cơ bản:

~~~text
@Configuration / @Bean
        |
        v
Spring tạo đối tượng ThreadPoolTaskExecutor
        |
        v
Áp dụng cấu hình (core/max/queue/name/policy...)
        |
        v
Spring gọi afterPropertiesSet() -> initialize()
        |
        v
Executor sẵn sàng nhận task qua DI / @Async
        |
        v
ApplicationContext bắt đầu đóng
        |
        v
Spring điều phối lifecycle và gọi destroy() -> shutdown()
~~~

`ThreadPoolTaskExecutor` kế thừa `ExecutorConfigurationSupport`, thực thi `InitializingBean`, `DisposableBean` và các giao thức lifecycle của Spring. Khi là bean do container sở hữu, việc gọi `afterPropertiesSet()` để tạo underlying `ExecutorService` diễn ra trong khâu khởi tạo bean; khi bean bị hủy, container gọi callback hủy tương ứng. Với ứng dụng chạy thực tế, cần đóng `ApplicationContext` một cách có trật tự để các callback này xảy ra.

**Điểm then chốt:** trong factory method `@Bean`, bạn **khởi tạo đối tượng và đặt thuộc tính rồi `return`**. Đừng gọi `executor.initialize()` ngay trong `@Bean`, vì container sẽ tự thực hiện bước này trong vòng đời bean; gọi thủ công có thể gây khởi tạo trùng và khiến việc quản lý executor khó kiểm soát. Cũng không tự gọi `shutdown()` từ service đang sử dụng bean chung.

## 3. Mẫu cấu hình @Bean đúng và ngắn gọn

Trong dự án có `spring-context` hoặc Spring Boot starter phù hợp:

~~~java
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class WorkerPoolConfig {

    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("report-worker-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        return executor;
    }
}
~~~

Đây là **class cấu hình Spring**, chưa phải ứng dụng độc lập. Khi container quét hoặc import cấu hình `WorkerPoolConfig`:

1. Bean có tên chính xác là `reportExecutor` và kiểu cụ thể là `ThreadPoolTaskExecutor`.
2. Spring khởi tạo pool ở giai đoạn initialize của bean; tác vụ không cần tự gọi `initialize()`.
3. Các thành phần khác có thể inject theo tên/kiểu để dùng cùng instance bean singleton mặc định.
4. Khi context đóng, Spring điều phối shutdown. Với cấu hình chờ hoàn tất task, pool xử lý các công việc được chấp nhận trước khi đóng theo những ràng buộc timeout được chỉ định.

`@Configuration(proxyBeanMethods = false)` là cách hợp lệ cho phương pháp cấu hình qua tham số DI. Nếu có một `@Bean` cần dùng bean khác, hãy nhận nó qua **tham số phương thức**, thay vì tự gọi một factory method trong cùng configuration rồi giả định luôn nhận bean đã quản lý.

### Chú giải từng thuộc tính

| Cấu hình | Ý nghĩa | Nhận xét thực hành |
| --- | --- | --- |
| `setCorePoolSize(2)` | Số worker cơ bản mà pool hướng tới khi có tải | Không có nghĩa phải tạo sẵn 2 thread ngay tại startup |
| `setMaxPoolSize(4)` | Trần worker của pool | Chỉ có thể tăng vượt core khi chính sách queue cho phép, thường sau khi queue đầy |
| `setQueueCapacity(16)` | Số task chờ tối đa khi worker đang bận | Bounded queue tránh hàng đợi tăng vô hạn, nhưng sẽ có thời điểm bị từ chối |
| `setKeepAliveSeconds(30)` | Thời gian giữ worker thừa so với core khi nhàn rỗi | Không mặc nhiên cho core thread hết hạn; xem `setAllowCoreThreadTimeOut(...)` |
| `setThreadNamePrefix(...)` | Đặt tiền tố tên worker | Giúp debug thread dump/log, không tạo cơ chế cách ly hay đồng bộ |
| `setRejectedExecutionHandler(...)` | Quyết định ứng xử khi pool không thể nhận task | `AbortPolicy` ném lỗi; `CallerRunsPolicy` có thể chạy task trên caller |
| `setWaitForTasksToCompleteOnShutdown(true)` | Khi shutdown, ưu tiên hoàn thành task đang chạy và đang chờ thay vì ngắt/loại bỏ theo đường mặc định | Có thể kéo dài thời gian dừng, cần giới hạn và điều phối tài nguyên phụ thuộc |
| `setAwaitTerminationSeconds(15)` | Cho container chờ tối đa 15 giây ở bước chờ kết thúc executor | Timeout **không bảo đảm** tất cả tác vụ chắc chắn hoàn tất sau 15 giây |

Đây là **giá trị ví dụ**, không phải bộ thông số đúng cho mọi hệ thống. Chọn dựa trên workload CPU/I/O, giới hạn kết nối, memory, tốc độ nộp/tiêu thụ task, mục tiêu latency và cách xử lý khi hết tài nguyên.

### Các giá trị mặc định cần tránh hiểu sai

Theo Javadoc Spring 6.2, `ThreadPoolTaskExecutor` mặc định có `corePoolSize = 1`, `maxPoolSize` rất lớn và `queueCapacity` rất lớn. Vì queue có thể luôn nhận thêm việc, cấu hình này về thực tế thường hành xử gần như một worker với hàng đợi dài, dù `maxPoolSize` nhìn có vẻ vô hạn. **Không nên coi default là pool tự động tăng thread theo số request.**

- `queueCapacity > 0`: Spring thường dùng một `LinkedBlockingQueue` có capacity được cấu hình.
- `queueCapacity = 0`: dùng cơ chế chuyển giao trực tiếp kiểu `SynchronousQueue`; pool có thể mở thêm worker đến `maxPoolSize` nếu cần.
- Queue lớn: giảm từ chối trong các burst nhỏ nhưng tăng chờ đợi và rủi ro giữ nhiều task/payload trong RAM.
- Queue rất nhỏ: phản ứng nhanh hơn với quá tải nhưng cần thiết kế rejection/backpressure rõ ràng.

## 4. Ví dụ Java 17+ chạy được với Spring Context

Ví dụ này minh họa **đầy đủ vòng đời bean**, từ lúc tạo `ApplicationContext`, inject `TaskExecutor` vào service, chạy một task trên worker khác, đến khi context đóng. Nó không yêu cầu Spring Boot hoặc HTTP server.

### 4.1. Cấu trúc dự án

~~~text
managed-pool-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── ManagedPoolDemo.java
~~~

Tệp **`pom.xml`** (Java 17, Spring Framework 6.2.16):

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>managed-pool-demo</artifactId>
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

Tệp **`src/main/java/demo/ManagedPoolDemo.java`**:

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ManagedPoolDemo {

    @Configuration(proxyBeanMethods = false)
    static class PoolConfig {

        @Bean("reportExecutor")
        ThreadPoolTaskExecutor reportExecutor() {
            ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
            pool.setCorePoolSize(2);
            pool.setMaxPoolSize(4);
            pool.setQueueCapacity(8);
            pool.setThreadNamePrefix("report-");
            pool.setRejectedExecutionHandler(
                    new ThreadPoolExecutor.AbortPolicy());
            pool.setWaitForTasksToCompleteOnShutdown(true);
            pool.setAwaitTerminationSeconds(5);
            return pool;  // Spring gọi initialize() và destroy().
        }

        @Bean
        ReportService reportService(
                @Qualifier("reportExecutor") TaskExecutor taskExecutor) {
            return new ReportService(taskExecutor);
        }
    }

    static final class ReportService {
        private final TaskExecutor executor;

        ReportService(TaskExecutor executor) {
            this.executor = executor;
        }

        void runOnce(AtomicReference<Thread> used,
                     CountDownLatch completed) {
            executor.execute(() -> {
                try {
                    used.set(Thread.currentThread());
                } finally {
                    completed.countDown();
                }
            });
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Thread caller = Thread.currentThread();
        ThreadPoolTaskExecutor pool;

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             PoolConfig.class)) {

            pool = context.getBean(
                    "reportExecutor", ThreadPoolTaskExecutor.class);
            ReportService service = context.getBean(ReportService.class);

            System.out.println("same bean = " +
                    (pool == context.getBean("reportExecutor")));

            AtomicReference<Thread> used = new AtomicReference<>();
            CountDownLatch completed = new CountDownLatch(1);
            service.runOnce(used, completed);

            if (!completed.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task timed out");
            }

            System.out.println("different thread = " +
                    (used.get() != caller));
            System.out.println("worker named = " +
                    used.get().getName().startsWith("report-"));
        } // context.close() gọi lifecycle shutdown của bean.

        System.out.println("pool shutdown = " +
                pool.getThreadPoolExecutor().isShutdown());
    }
}
~~~

### 4.2. Biên dịch và chạy

Trong thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.ManagedPoolDemo
~~~

**Stdout của chương trình** trong trường hợp chạy thành công (bỏ qua thông báo riêng của công cụ Maven nếu có):

~~~text
same bean = true
different thread = true
worker named = true
pool shutdown = true
~~~

Giải thích từng kết quả:

- Hai lần `getBean("reportExecutor")` trả lại cùng instance vì bean `@Bean` mặc định là singleton trong context.
- Task được nộp vào pool đang hoạt động, có worker với prefix `report-` nên phép so sánh với caller là `true` ở dòng `different thread`. Ở ví dụ tải nhẹ này, `AbortPolicy` không có lý do từ chối task.
- `CountDownLatch` bảo đảm task đã ghi `AtomicReference<Thread>` trước khi luồng main đọc; không dùng `sleep()`.
- `try-with-resources` đóng `AnnotationConfigApplicationContext` ngay sau khối `try`. `ThreadPoolTaskExecutor` đã được khởi tạo và được Spring hủy theo lifecycle, nên `isShutdown()` trả về `true`.

**Tại sao ví dụ không gọi `pool.initialize()` và `pool.shutdown()`?** Vì vòng đời thuộc container. Tự gọi `initialize()` trong `@Bean` không cần thiết; tự `shutdown()` trong service có thể làm những consumer khác của bean chung đột ngột nhận `TaskRejectedException`.

### Nếu không chạy trong Spring context

Đây là tình huống **khác**:

~~~java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(2);
executor.initialize();
try {
    executor.execute(() -> System.out.println("work"));
} finally {
    executor.shutdown();
}
~~~

Đoạn này là **minh họa thủ công**, giả định code chạy trong một phương thức; bản thân nó không có `main`. Khi chính bạn tạo đối tượng ngoài bean factory, **bạn** phải khởi tạo, shutdown và chờ kết thúc nếu cần. Điều đó giải thích giá trị của việc biến executor thành Spring bean: container tập trung quản lý vòng đời, DI và cấu hình, giảm rủi ro quên dọn tài nguyên.

## 5. Inject và sử dụng đúng bean

### Constructor injection cùng @Qualifier

Khi hệ thống có nhiều executor, inject trực tiếp bean có tên xác định:

~~~java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class InvoiceExportService {
    private final TaskExecutor reportExecutor;

    public InvoiceExportService(
            @Qualifier("reportExecutor") TaskExecutor reportExecutor) {
        this.reportExecutor = reportExecutor;
    }

    public void enqueue(long invoiceId) {
        reportExecutor.execute(() -> export(invoiceId));
    }

    private void export(long invoiceId) {
        // Thực hiện nghiệp vụ export; xử lý retry/lỗi tại tầng phù hợp.
    }
}
~~~

Đây là **service snippet cho dự án Spring đã quét component**, không phải tệp Java SE độc lập. Tên `@Qualifier("reportExecutor")` phải khớp bean name hoặc qualifier có hiệu lực. Nếu chỉ có một bean kiểu `TaskExecutor`, có thể inject theo type; khi nhiều bean cùng kiểu, chỉ rõ qualifier hoặc thiết kế primary bean để tránh lựa chọn mơ hồ.

### Vì sao chọn TaskExecutor ở service?

Service phần lớn chỉ cần phương thức `execute(...)`, nên có thể phụ thuộc `TaskExecutor` thay vì lớp cụ thể. Cấu hình Spring giữ thông tin pool size, queue và shutdown; business logic chỉ biết đang giao task cho executor đã cấp. Nếu service cần API `submit(...)`, dùng interface thích hợp như `AsyncTaskExecutor` hoặc phụ thuộc cụ thể có chủ đích.

### Tương tác với @Async

`@Async` là cơ chế annotation dựa trên proxy/interceptor của Spring. Nếu muốn một phương thức được điều phối qua bean cụ thể, bật `@EnableAsync` trong cấu hình và chỉ định tên:

~~~java
import java.util.concurrent.CompletableFuture;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncFeatureConfig {
}

@Service
class InvoicePreviewService {
    @Async("reportExecutor")
    public CompletableFuture<String> generatePreview(long id) {
        String result = "preview-" + id;
        return CompletableFuture.completedFuture(result);
    }
}
~~~

Đây là **hai lớp minh họa trong ứng dụng Spring**; nếu đặt chung một file cần tuân thủ quy tắc tên/public class của Java. Phương thức `@Async("reportExecutor")` dùng bean có tên `reportExecutor` khi được gọi **qua Spring proxy**. Với cách dùng mặc định (proxy mode), `this.generatePreview(...)` từ chính class không được interceptor bắt, nên có thể chạy như lời gọi Java bình thường.

`@Async` không tự bảo đảm persistence hoặc transaction giữa các thread; khác luồng thì transaction context/`ThreadLocal` của caller không mặc nhiên được chuyển sang. Nếu cần kiểm soát lỗi, phương thức trả `CompletableFuture`/`Future` cho phép phía nhận theo dõi thất bại tốt hơn `void`.

## 6. Bên trong thread pool: core, queue, max và rejection

`ThreadPoolTaskExecutor` dùng các quy tắc quản lý pool của `ThreadPoolExecutor`. Với `corePoolSize = 2`, `maxPoolSize = 4` và `queueCapacity = 3`, hình dung một tình huống các task chạy đủ lâu để tiếp tục chiếm worker:

| Số task được nhận liên tiếp khi tất cả đang bận | Hành vi lý tưởng hóa |
| --- | --- |
| Task 1–2 | Dùng đến 2 core worker |
| Task 3–5 | Có thể chờ trong 3 vị trí queue |
| Task 6–7 | Pool có thể tạo thêm worker đến tối đa 4 |
| Task 8 | Worker và queue đều đầy: kích hoạt rejection policy |

Bảng là **mô phỏng khi task không kịp kết thúc trong lúc nộp**, không phải dự báo lịch thực thi cố định. Nếu một worker hoàn thành sớm, task mới có thể được tiếp nhận khác đi.

- `AbortPolicy` (mặc định của underlying executor): từ chối khi không thể tiếp nhận, thường biểu hiện thành `TaskRejectedException` qua Spring `TaskExecutor`.
- `CallerRunsPolicy`: nếu pool chưa shutdown, task bị từ chối bởi pool sẽ chạy trực tiếp trên **thread gọi `execute`**. Đây là backpressure nhưng có thể làm request chậm bất ngờ.
- `DiscardPolicy` hoặc `DiscardOldestPolicy`: có nguy cơ mất task, không nên dùng cho nghiệp vụ bắt buộc xử lý nếu chưa có cơ chế bù/giám sát.
- Khi pool đang đóng hoặc đã đóng, submission có thể bị từ chối; với `CallerRunsPolicy`, JDK không luôn chạy task đã bị từ chối khi executor đã shutdown.

**Đừng đồng nhất “được giao cho pool” với “chắc chắn chạy trên thread khác”** nếu đã bật `CallerRunsPolicy`. Đừng khẳng định “chắc chắn thực hiện” chỉ vì `execute` không ném exception khi handler có thể loại task.

### Ví dụ xử lý từ chối

~~~java
import org.springframework.core.task.TaskRejectedException;

public void submitJob(Runnable job) {
    try {
        reportExecutor.execute(job);
    } catch (TaskRejectedException ex) {
        // Chuyển sang hàng đợi bền vững, retry có giới hạn
        // hoặc báo quá tải cho client tùy nghiệp vụ.
        throw ex;
    }
}
~~~

Đây là **mẫu phương thức minh họa**, giả định `reportExecutor` đã được inject. Tránh retry vô hạn ngay trên request thread vì nó có thể làm tăng tải đúng lúc pool đã quá tải. Với các job quan trọng, thiết kế thông điệp idempotent, retry và lưu trạng thái ở nơi bền vững thay vì chỉ dựa vào queue trong RAM.

## 7. Vòng đời bean và đóng ứng dụng một cách an toàn

### 7.1. Khởi tạo tự động

`ThreadPoolTaskExecutor` kế thừa cơ chế `InitializingBean.afterPropertiesSet()`, dẫn tới `initialize()`. Spring gọi callback này sau khi áp dụng các thuộc tính bean, tạo underlying pool cho việc gửi task.

Vì vậy, trong `@Bean`:

~~~java
@Bean("imageExecutor")
ThreadPoolTaskExecutor imageExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(6);
    executor.setQueueCapacity(50);
    return executor; // Chưa tự initialize() tại đây.
}
~~~

Nếu cần dùng pool trong component khác, hãy inject bean đó. **Đừng** viết `new ThreadPoolTaskExecutor()` trong mỗi request hoặc trong mỗi service chỉ để “dùng luôn”; điều này tạo ra nhiều pool không có vòng đời thống nhất, tốn thread và dễ bị quên shutdown.

### 7.2. Shutdown tự động

`ThreadPoolTaskExecutor` triển khai `DisposableBean.destroy()`, kế thừa từ `ExecutorConfigurationSupport`. Khi context đóng đúng cách, Spring gọi callback hủy để shutdown underlying executor. Đây là lý do không cần thêm `@PreDestroy` chỉ để gọi `shutdown()` cho cùng bean.

Tuy nhiên, **container quản lý shutdown không đồng nghĩa mọi task đã hoàn tất**. Cần phân biệt:

- Dừng tiếp nhận task mới.
- Cách xử lý task đang chạy.
- Cách xử lý task đang nằm trong queue.
- Có chờ task kết thúc hay không; chờ tối đa bao lâu.
- Các bean khác (database client, HTTP client...) còn sống đủ lâu để task đang chạy dùng chúng hay không.

### 7.3. Hai thuộc tính shutdown quan trọng

Với Spring Framework 6.2:

`setWaitForTasksToCompleteOnShutdown(true)` yêu cầu executor khi shutdown để task đã nhận được hoàn thành mà không chủ động interrupt chúng theo đường shutdown bình thường, kể cả các task còn trong queue. Điều này có thể kéo dài thời gian dừng nếu có backlog hoặc task bị treo.

`setAwaitTerminationSeconds(15)` yêu cầu phần shutdown chờ tối đa 15 giây cho executor kết thúc trước khi Spring tiếp tục hủy các bean còn lại. Nó **không hủy tự động** mọi task khi hết 15 giây theo cách biến công việc thành chắc chắn hoàn thành; nếu timeout, hệ thống vẫn có thể còn task hoặc cần shutdown orchestration ở tầng ứng dụng.

Kết hợp hai thuộc tính cần dựa trên thời gian chạy job thực tế. Nếu job có thể mất vài phút, giá trị 15 giây chỉ là chính sách chờ ngắn, không phải cam kết job kết thúc trước khi database pool đóng.

### 7.4. Lưu ý riêng về thay đổi lifecycle ở Spring 6.1/6.2

Các phiên bản Spring mới phối hợp shutdown của executor với `ContextClosedEvent` và `SmartLifecycle`. Thuộc tính như `acceptTasksAfterContextClose` và, trên `ThreadPoolTaskExecutor`, `strictEarlyShutdown` ảnh hưởng thời điểm từ chối task mới trong quá trình đóng context.

Đừng học thuộc một quy tắc “cứ context.close là mọi task lập tức dừng”. Hãy đọc Javadoc theo **phiên bản đang chạy** và kiểm thử đúng thứ tự shutdown của ứng dụng. Không bắt đầu tác vụ nền mới trong callback hủy rồi giả định pool còn nhận việc, trừ khi thiết kế lifecycle đã chủ động cho phép và được kiểm chứng.

### 7.5. Có nên tự thêm destroyMethod="shutdown"?

Đối với `ThreadPoolTaskExecutor` được Spring quản lý, **không cần**, vì lớp đã có callback hủy chuẩn. Ví dụ cấu hình thông thường chỉ dùng `@Bean("...")` và trả executor. Việc gọi `shutdown()` ở nhiều nơi có thể làm công việc còn lại bị từ chối; càng không nên để một consumer của pool có quyền tự đóng pool chia sẻ.

Nếu muốn loại bỏ trách nhiệm shutdown của container cho một bean đặc biệt, đó là quyết định thiết kế khác và phải tự chịu trách nhiệm lifecycle; không áp dụng cho mẫu bean quản lý thông thường của bài này.

## 8. Đặt tên và phân chia executor theo loại công việc

Một ứng dụng có thể có nhiều pool:

~~~java
@Configuration(proxyBeanMethods = false)
class MultiplePoolsConfig {

    @Bean("cpuExecutor")
    ThreadPoolTaskExecutor cpuExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(4);
        pool.setQueueCapacity(32);
        pool.setThreadNamePrefix("cpu-");
        return pool;
    }

    @Bean("ioExecutor")
    ThreadPoolTaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(8);
        pool.setMaxPoolSize(16);
        pool.setQueueCapacity(100);
        pool.setThreadNamePrefix("io-");
        return pool;
    }
}
~~~

**Đoạn cấu hình minh họa**, không phải cặp giá trị tuning mặc định. Số thread CPU-bound thường cần gắn với số CPU khả dụng; tác vụ I/O chờ lâu có thể cần mức concurrency khác nhưng phải tính thêm giới hạn kết nối DB/HTTP, memory và tốc độ nhận task. Nếu không cần cách ly tải, một pool đủ rõ ràng đôi khi đơn giản hơn nhiều pool.

Khi inject:

~~~java
public SomeService(
        @Qualifier("cpuExecutor") TaskExecutor cpuExecutor,
        @Qualifier("ioExecutor") TaskExecutor ioExecutor) {
    this.cpuExecutor = cpuExecutor;
    this.ioExecutor = ioExecutor;
}
~~~

Đây chỉ là **constructor snippet**: trong class thật cần khai báo hai field `TaskExecutor` tương ứng. Các tên bean riêng và `@Qualifier` giúp tránh trường hợp khi thêm executor thứ hai thì injection theo type đột ngột trở nên mơ hồ.

## 9. Cấu hình trong Spring Boot: chú ý bean mặc định

Spring Boot có tự động cấu hình executor theo phiên bản và các bean hiện diện. Ví dụ trong **Spring Boot 3.5**, khi chưa có custom `Executor`, Boot có thể tự tạo `AsyncTaskExecutor` cho các tích hợp khác nhau, thường là `ThreadPoolTaskExecutor` khi dùng platform threads.

**Khi tự khai báo một bean `Executor`**, auto-configuration có thể lùi lại và những tính năng như `@Async`, Spring MVC async handling hoặc WebFlux có thể chọn executor khác theo **tên bean và type**. Các quy tắc này phụ thuộc phiên bản Boot:

- `@Async("reportExecutor")`: chọn rõ bean `reportExecutor` cho riêng phương thức đó.
- Với một số tích hợp async web, tên `applicationTaskExecutor` và khả năng implement `AsyncTaskExecutor` có ý nghĩa đặc biệt.
- Với cấu hình `@EnableAsync`, Spring Framework có cơ chế chọn executor mặc định và có thể dùng `AsyncConfigurer`; đừng suy rằng bean nào có tên bất kỳ sẽ tự động được mọi tác vụ dùng.
- Cần phân biệt executor dùng cho `@Async` với scheduler cho `@Scheduled`. Một `ThreadPoolTaskExecutor` không tự biến thành `TaskScheduler`.

**Nguyên tắc thực hành:** nếu dự án đang dùng Spring Boot, đọc đúng mục “Task Execution and Scheduling” của **phiên bản Boot đang triển khai**, kiểm tra danh sách bean thực tế và chủ động đặt tên/qualifier. Không tự nhân đôi pool chỉ để có thêm một executor mà không kiểm soát ảnh hưởng auto-configuration.

## 10. TaskDecorator, context và lỗi trong worker

`ThreadPoolTaskExecutor#setTaskDecorator(...)` cho phép bọc tác vụ trước khi thực thi, hữu ích cho logging/tracing hoặc truyền một phần context được kiểm soát.

Ví dụ **minh họa**, đặt trong factory method trước `return executor`:

~~~java
executor.setTaskDecorator(original -> () -> {
    long started = System.nanoTime();
    try {
        original.run();
    } finally {
        long elapsedNanos = System.nanoTime() - started;
        System.out.println("Task runtime nanos = " + elapsedNanos);
    }
});
~~~

TaskDecorator được sử dụng cho `Runnable` đã được executor chuẩn bị; với những đường `submit(...)` có wrapper `FutureTask`, xử lý exception trong decorator có thể không hoạt động theo cách mong đợi. Muốn nhận exception của task, dùng `Future.get()`/`CompletableFuture`, exception handler phù hợp hoặc hook theo executor cụ thể.

**Không mặc định sao chép toàn bộ `ThreadLocal` từ request sang worker.** Transaction của Spring thông thường gắn với thread; ngữ cảnh bảo mật, MDC tracing và các giá trị request có thể cần cơ chế propagation riêng. Nếu tự capture/set context trong decorator, phải restore/clear trong `finally` để worker tái sử dụng không mang dữ liệu task trước sang task sau.

Đặc biệt, việc dùng `CallerRunsPolicy` có thể làm task chạy trong caller trong một số lần, thay đổi latency và context quan sát được. Chọn rejection handler phù hợp với yêu cầu vận hành và security của ứng dụng.

## 11. Giám sát và xác minh bean đang hoạt động

Để xem pool thật trong runtime, có thể lấy bean từ context:

~~~java
ThreadPoolTaskExecutor pool =
        context.getBean("reportExecutor", ThreadPoolTaskExecutor.class);

int currentPoolSize = pool.getPoolSize();
int activeThreads = pool.getActiveCount();
int queuedTasks = pool.getThreadPoolExecutor().getQueue().size();
~~~

Đây là **snippet bên trong code có `context` đã khởi tạo**. Các số là trạng thái tại thời điểm quan sát, thay đổi khi các task bắt đầu/kết thúc; không phải dữ liệu giao dịch nhất quán. Có thể đưa chúng vào Micrometer/Actuator hoặc hệ thống monitoring phù hợp, nhưng đừng phát log liên tục từng task ở mức gây tải lớn.

### Các chỉ số nên theo dõi

| Chỉ số | Điều cần quan sát |
| --- | --- |
| Active threads | Có liên tục sát max hay không |
| Queue depth | Hàng đợi tăng không ngừng, backlog mất bao lâu mới xử lý hết |
| Rejected submissions | Lần từ chối tăng đột biến và ảnh hưởng người gọi |
| Task duration / error rate | Tác vụ chậm do ngoại vi hay do CPU, lỗi có được báo đúng kênh |
| Caller latency | Có sự tăng mạnh do `CallerRunsPolicy` hay chờ submit không |
| Shutdown duration | Container có dừng trước khi task quan trọng kết thúc không |

Không thể suy ra throughput hoặc latency tốt chỉ từ số `corePoolSize`; cần kiểm tra thực tế với workload có cùng đặc điểm tài nguyên của production.

### Kiểm thử lifecycle có ý nghĩa

1. Bắt đầu context và lấy `reportExecutor`; bảo đảm nhận đúng bean name/type.
2. Submit một tác vụ và dùng `CountDownLatch` hoặc `Future` để đợi hoàn thành, không dùng `Thread.sleep`.
3. So sánh `Thread.currentThread()` với caller nếu yêu cầu off-thread là phần contract của cấu hình.
4. Đóng context; kiểm tra underlying `ThreadPoolExecutor.isShutdown()` và, nếu cần, `awaitTermination(...)`.
5. Với bài test quá tải, chủ động giữ worker bằng latch, lấp queue rồi gửi thêm task để kiểm tra policy. Không dựa vào may rủi scheduler.
6. Kiểm tra exception, cancellation, thời gian đóng và resource cleanup theo nhu cầu nghiệp vụ.

Một test pass ở tải nhẹ **không chứng minh** pool không bao giờ bị từ chối task hoặc luôn chạy ngoài caller khi đã cấu hình `CallerRunsPolicy`.

## 12. Những lỗi phổ biến khi khai báo bean

**Lỗi 1: tạo pool bằng `new` trong mỗi lời gọi request.**  
Hậu quả: thread tăng không kiểm soát, khó quản lý shutdown. Khắc phục: tạo một bean singleton dùng chung cho workload cụ thể và inject vào service.

**Lỗi 2: tự gọi `initialize()` bên trong phương thức `@Bean`.**  
Hậu quả: container cũng thực hiện callback khởi tạo. Khắc phục: cấu hình thuộc tính rồi `return`, để Spring điều khiển lifecycle.

**Lỗi 3: quên xác định khi nào pool dừng.**  
Hậu quả: khi tạo pool thủ công, không shutdown có thể giữ thread/process sống; nếu là bean, cần cho context đóng đúng cách. Khắc phục: container quản lý bean và thiết kế timeout shutdown phù hợp.

**Lỗi 4: nghĩ `maxPoolSize=100` đồng nghĩa có thể chạy 100 task ngay.**  
Hậu quả: với queue quá lớn, pool thường nhận task vào queue trước khi tăng vượt core. Khắc phục: hiểu thuật toán ưu tiên worker/queue và đặt queue capacity có chủ đích.

**Lỗi 5: để queue vô hạn vì muốn “không bao giờ lỗi”.**  
Hậu quả: độ trễ tăng, giữ nhiều payload trong RAM, nguy cơ cạn bộ nhớ. Khắc phục: giới hạn hợp lý, điều khiển tốc độ nộp task và xử lý rejection.

**Lỗi 6: cấu hình `CallerRunsPolicy` mà giả định task luôn background.**  
Hậu quả: request thread có thể tự làm task và chậm bất ngờ; context execution thay đổi. Khắc phục: chọn handler đúng mục tiêu, quan sát saturation.

**Lỗi 7: chỉ inject theo type khi có nhiều executor.**  
Hậu quả: ambiguity hoặc dùng nhầm pool. Khắc phục: đặt tên bean có ý nghĩa và `@Qualifier` hoặc `@Async("...")`.

**Lỗi 8: chỉ khai báo bean rồi nghĩ `@Async` tự bật.**  
Hậu quả: annotation có thể không được xử lý nếu không cấu hình `@EnableAsync`/cơ chế tương đương, hoặc self-invocation không đi qua proxy. Khắc phục: bật async support và gọi qua Spring proxy.

**Lỗi 9: nghĩ `@Bean` giúp các mutable field trong service thread-safe.**  
Hậu quả: mất cập nhật hoặc đọc dữ liệu sai khi nhiều task dùng cùng singleton. Khắc phục: thiết kế immutable state hoặc đồng bộ phù hợp; pool quản lý thread, không quản lý bất biến nghiệp vụ.

**Lỗi 10: tưởng shutdown thành công nghĩa là mọi công việc quan trọng đã được lưu bền.**  
Hậu quả: khi crash hoặc vượt timeout vẫn có thể mất task trong bộ nhớ. Khắc phục: dùng job store/message broker khi cần durability, retry và idempotency.

**Lỗi 11: bật `waitForTasksToCompleteOnShutdown` nhưng đặt timeout không đủ và bỏ qua phụ thuộc tài nguyên.**  
Hậu quả: task còn chạy trong khi các bean dùng bởi task đã đóng. Khắc phục: đánh giá worst-case duration, thử graceful shutdown thật và điều chỉnh thứ tự/timeout.

**Lỗi 12: thêm `@PreDestroy` trong consumer để tự đóng bean chung.**  
Hậu quả: consumer có thể khiến task của các consumer khác bị từ chối và việc hủy trở nên rối. Khắc phục: để bean executor sở hữu lifecycle qua container.

## 13. Bài tập áp dụng

### Bài 1 — Điền phần còn thiếu

Đoạn sau thiếu cấu hình cần để phân biệt pool dùng cho tác vụ xuất báo cáo:

~~~java
@Bean("reportExecutor")
ThreadPoolTaskExecutor reportExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(2);
    pool.setMaxPoolSize(4);
    pool.setQueueCapacity(20);
    return pool;
}
~~~

**Đáp án gợi ý:** thêm `setThreadNamePrefix("report-")`; chọn `RejectedExecutionHandler` phù hợp. Với task phải hoàn thành lúc shutdown, xem xét `setWaitForTasksToCompleteOnShutdown(...)` và `setAwaitTerminationSeconds(...)`. Không cần gọi `initialize()` trong factory method.

### Bài 2 — Vì sao chỉ chạy hai worker?

Một pool có `corePoolSize=2`, `maxPoolSize=20`, `queueCapacity=Integer.MAX_VALUE`. Khi hàng trăm task ngắn được submit liên tiếp, tại sao chỉ có vài worker bận?

**Đáp án:** sau core worker, pool ưu tiên đưa task vào queue; queue rất lớn gần như không bao giờ đầy nên ít có cơ hội tạo worker vượt core. Muốn tăng concurrency theo tải, phải cân nhắc capacity/pool sizing, không chỉ tăng `maxPoolSize`.

### Bài 3 — Bean nào được @Async sử dụng?

Ứng dụng có `reportExecutor` và `notificationExecutor`. Phương thức khai báo `@Async("notificationExecutor")`.

**Đáp án:** Spring chọn bean theo qualifier/tên `notificationExecutor` khi async interception hoạt động. Nếu gọi trực tiếp từ `this` trong proxy mode, không thể kết luận lời gọi sẽ được dispatch qua executor.

### Bài 4 — Shutdown có chắc hoàn tất toàn bộ queue?

Một executor có 1.000 task đang chờ, `setWaitForTasksToCompleteOnShutdown(true)` và `setAwaitTerminationSeconds(5)`. Có thể cam kết toàn bộ task xong trước 5 giây không?

**Đáp án:** không. Cấu hình chờ tối đa 5 giây không biến workload dài thành tác vụ ngắn hoặc bảo đảm kết thúc hết queue trước deadline. Cần kiểm soát khối lượng, timeout và định nghĩa rõ xử lý job còn dở.

### Bài 5 — Phân tích đoạn code sai

~~~java
@Service
class WrongReportService {
    void start() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.execute(() -> System.out.println("report"));
    }
}
~~~

**Đáp án:** pool được tạo thủ công chưa gọi `initialize()` nên chưa có underlying executor; lời gọi `execute` không có pool đã khởi tạo và có thể thất bại. Nếu tự sửa bằng initialize nhưng quên shutdown, lại thêm rủi ro rò rỉ thread. Cách đúng với ứng dụng Spring: khai báo `@Bean` và constructor-inject pool có quản lý vòng đời.

## 14. Checklist để review một ThreadPoolTaskExecutor bean

- [ ] Executor được tạo một lần ở `@Bean`/cơ chế đăng ký bean phù hợp, và được Spring context quản lý.
- [ ] Đã đặt `@Bean` name rõ ràng, service dùng constructor injection và `@Qualifier` nếu có nhiều pool.
- [ ] Không tự gọi `initialize()` trong factory method và không tự shutdown pool ở consumer.
- [ ] Đã chọn `corePoolSize`/`maxPoolSize`/`queueCapacity` theo workload, có giới hạn memory và phản ứng với quá tải.
- [ ] Biết rõ `RejectedExecutionHandler` và đường error/backup queue khi không nhận task.
- [ ] Thread name prefix giúp quan sát, không lấy tên thread làm bằng chứng duy nhất cho correctness.
- [ ] Với `@Async`: đã bật `@EnableAsync`, gọi qua proxy và chỉ rõ executor khi cần.
- [ ] Đã thiết kế graceful shutdown, timeout chờ, thứ tự đóng tài nguyên và xử lý task dang dở.
- [ ] Đã đo queue depth, active threads, rejection và latency; kiểm thử được hành vi khi bão hòa.
- [ ] Nếu yêu cầu không mất task sau crash, đã dùng thành phần lưu trữ bền vững ngoài executor.

## 15. Tóm tắt kiến thức cốt lõi

**Công thức thực hành:** `@Configuration` + `@Bean("tênExecutor")` + `new ThreadPoolTaskExecutor()` + các setter cần thiết + `return executor`. Spring quản lý initialize/destroy; service nhận bean qua dependency injection.

Kích thước pool, queue và rejection là **một bộ chính sách**, không phải ba con số độc lập. Để an toàn trong production, cần xét cả cách đóng context, kết quả task, giao dịch/tài nguyên phụ thuộc và các cơ chế lưu bền cần thiết. Khi dùng `@Async`, hãy xác định chính xác executor được chọn và đảm bảo lời gọi đi qua proxy.

## Tài liệu tham khảo chính thức

- Spring Framework Reference — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework 6.2 — `ThreadPoolTaskExecutor` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework 6.2 — `ExecutorConfigurationSupport` Javadoc (initialize, destroy, shutdown, await): https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html
- Spring Boot 3.5 — Task Execution and Scheduling: https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html
- Java SE 17 — `ThreadPoolExecutor`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Spring Framework — Bean Definition and Lifecycle: https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-002">corePoolSize và maxPoolSize</a>

<details>
<summary>Xem chi tiết</summary>

# corePoolSize và maxPoolSize

## 1. Bối cảnh: vì sao một thread pool cần hai giới hạn?

Trong ứng dụng Spring Framework, `ThreadPoolTaskExecutor` thường nhận nhiều tác vụ từ controller, service, listener hoặc phương thức `@Async`. Mỗi tác vụ cần một thread để chạy, nhưng số thread đang thực thi không nên tăng vô hạn. Hai thuộc tính quan trọng nhất của pool là:

- **`corePoolSize`**: ngưỡng số worker lõi mà pool ưu tiên tạo khi nhận tác vụ. Theo chính sách thông thường, các thread lõi được giữ lại khi rảnh, trừ khi bật `allowCoreThreadTimeOut`.
- **`maxPoolSize`**: **giới hạn trên** số worker mà pool được tạo để xử lý tải; nó chỉ phát huy vai trò mở rộng khi cơ chế xếp hàng không nhận thêm tác vụ.

Hai con số phải được hiểu cùng **`queueCapacity`**. Nếu không xét hàng đợi, rất dễ tưởng `corePoolSize=2, maxPoolSize=10` nghĩa là cứ nhiều tác vụ thì pool sẽ tự chạy đồng thời tới 10 thread. Với hàng đợi còn chỗ, `ThreadPoolExecutor` thường **ưu tiên xếp hàng sau khi đạt core**, nên không tạo thêm thread tới max.

Bài này tập trung vào `ThreadPoolTaskExecutor` của Spring và cơ chế `java.util.concurrent.ThreadPoolExecutor` bên dưới. Những nguyên tắc đó không áp dụng nguyên xi cho mọi `TaskExecutor` khác: ví dụ `SyncTaskExecutor` không có thread pool để cấu hình hai thuộc tính này.

## 2. Ý nghĩa chính xác của từng thuộc tính

| Thuộc tính | Vai trò | Điều dễ hiểu nhầm |
| --- | --- | --- |
| `corePoolSize` | Ngưỡng worker lõi: khi nhận task và số worker thấp hơn core, pool ưu tiên tạo worker | Không có nghĩa khi khởi động bean đã tồn tại sẵn từng ấy thread |
| `maxPoolSize` | Số worker tối đa pool được phép có tại một thời điểm | Không có nghĩa pool sẽ lập tức đạt tới max khi core bận |
| `queueCapacity` | Số task có thể chờ trong hàng đợi (không tính task đang chạy) | Không phải giới hạn số task đang chạy hoặc tổng số task trong toàn hệ thống |
| `keepAliveSeconds` | Thời gian worker **vượt core** có thể tiếp tục rảnh trước khi bị thu hồi | Không phải timeout của mỗi task |
| `allowCoreThreadTimeOut` | Khi bật, cho phép cả worker lõi rút lui sau thời gian rảnh | Không làm tăng `maxPoolSize` hoặc thay đổi quy tắc queue |

Các điểm cần nhớ:

1. `0 <= corePoolSize <= maxPoolSize`, và `maxPoolSize >= 1` trong `ThreadPoolExecutor` JDK. Cấu hình `core > max` không hợp lệ.
2. `corePoolSize` **không phải số thread luôn tồn tại ngay từ đầu**. Core worker thường được tạo khi có tác vụ; `prestartAllCoreThreads` là lựa chọn riêng để tạo trước.
3. `maxPoolSize` là trần thread, **không phải trần độ dài hàng đợi** và cũng không phải mục tiêu số thread cần đạt.
4. Worker đang rảnh vượt `corePoolSize` có thể bị thu hồi sau `keepAliveSeconds`. Worker lõi mặc định được giữ khi idle, trừ khi `allowCoreThreadTimeOut=true`.

Theo JavaDoc của Spring `ThreadPoolTaskExecutor`, giá trị mặc định của **chính class này** là `corePoolSize=1`, `maxPoolSize=Integer.MAX_VALUE`, `queueCapacity=Integer.MAX_VALUE`. Vì queue thực tế rất lớn, thiết lập mặc định thường vận hành gần giống một executor chạy một tác vụ tại một thời điểm. **Đừng nhầm mặc định này với executor được Spring Boot tự cấu hình**: Boot có thể dùng giá trị khác theo phiên bản và cấu hình ứng dụng.

## 3. Core và max phải được đọc cùng queueCapacity

Hai giới hạn worker không hoạt động độc lập: khi chưa đủ core workers, executor ưu tiên tạo worker; sau khi tới core, nó thử đưa task vào queue; chỉ khi queue không nhận mới thử mở rộng đến max, rồi áp dụng rejection policy nếu cũng không thể mở rộng. Với queue đủ lớn, rất nhiều task có thể chờ mà pool chưa dùng đến max.

Bài này sử dụng quy tắc trên để **tính số worker theo hai ngưỡng**. Thuật toán nhận việc đầy đủ, kiểm tra khi shutdown và tình huống task đến sau chạy trước task cũ được trình bày ở bài “Thứ tự nhận tác vụ: tạo luồng cơ bản, xếp hàng, mở rộng rồi từ chối”. Bảng mô phỏng và chương trình bên dưới sẽ kiểm chứng ảnh hưởng của core/max.

## 4. Mô phỏng với core=2, max=4, queueCapacity=2

Giả sử các task đều giữ thread bận và không hoàn tất trong lúc gửi lần lượt bảy tác vụ. Đặt:

```text
corePoolSize  = 2
maxPoolSize   = 4
queueCapacity = 2
```

Quá trình nhận việc diễn ra như sau:

| Tác vụ mới | Số worker sau khi nhận | Số task chờ queue | Giải thích |
| --- | ---: | ---: | --- |
| Task 1 | 1 | 0 | Dưới core nên tạo worker thứ nhất |
| Task 2 | 2 | 0 | Dưới core nên tạo worker thứ hai |
| Task 3 | 2 | 1 | Đã đạt core, queue còn chỗ |
| Task 4 | 2 | 2 | Queue đầy |
| Task 5 | 3 | 2 | Queue đầy, còn dưới max nên thêm worker |
| Task 6 | 4 | 2 | Thêm worker cuối cùng tới max |
| Task 7 | 4 | 2 | Pool và queue đều đầy, bị từ chối theo chính sách mặc định |

Hình dung trạng thái ngay trước Task 7:

```text
Đang thực thi:  [worker-1: task-1] [worker-2: task-2]
                [worker-3: task-5] [worker-4: task-6]
Queue đang chờ: [task-3] [task-4]
Task mới tới:   [task-7]  --> REJECTED (mặc định)
```

Tổng **sức chứa tức thời trong tình huống này** là tối đa 4 task đang chiếm thread cộng 2 task đang chờ = 6 task, với giả định tất cả tác vụ đều chặn và không có task nào kết thúc khi đang submit. **Không có công thức `max + queue` cho throughput hoặc sức chứa cố định trong mọi thời điểm**: task có thể kết thúc, worker mới nhận việc, queue rút ngắn và đợt task mới lại được nhận.

Khi hai worker vượt core hết việc và rảnh đủ lâu, chúng có thể bị thu hồi về gần mức core. Không thể kết luận `poolSize` lúc nào cũng là 4 chỉ từ cấu hình `maxPoolSize=4`.

## 5. Ví dụ Java 17+ chạy được và có kết quả xác định

Để thấy rõ từng ngưỡng, chương trình dưới đây dùng `ThreadPoolExecutor` JDK với cùng chiến lược lập lịch mà `ThreadPoolTaskExecutor` bao quanh. Một `CountDownLatch` giữ cả sáu task đầu chưa hoàn thành, vì vậy các con số được in không phụ thuộc vào việc CPU chạy nhanh hay chậm.

Tạo tệp `PoolSizingDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class PoolSizingDemo {
    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));

        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> accepted = new ArrayList<>();

        try {
            for (int i = 0; i < 6; i++) {
                accepted.add(pool.submit(() -> {
                    release.await(); // Giữ worker bận cho tới khi main cho phép.
                    return null;
                }));
            }

            System.out.println("poolSize = " + pool.getPoolSize());
            System.out.println("queued = " + pool.getQueue().size());

            try {
                pool.submit(() -> {
                    release.await();
                    return null;
                });
                System.out.println("rejected = false");
            } catch (RejectedExecutionException ex) {
                System.out.println("rejected = true");
            }
        } finally {
            release.countDown(); // Không để các worker bị giữ mãi.
            pool.shutdown();
        }

        for (Future<?> future : accepted) {
            future.get(); // Chỉ tiếp tục khi cả sáu task kết thúc.
        }
        System.out.println("completed = " + accepted.size());

        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Pool did not terminate");
        }
    }
}
```

Chạy:

```bash
javac PoolSizingDemo.java
java PoolSizingDemo
```

Stdout chính xác của ví dụ:

```text
poolSize = 4
queued = 2
rejected = true
completed = 6
```

Giải thích:

- Sáu lần `submit` đầu đều được nhận. Hai task đầu tạo core workers, hai task tiếp theo vào queue, hai task cuối làm pool mở rộng lên 4 workers.
- Vì `release` chưa được hạ, **không worker nào hoàn tất**, nên lúc đo luôn có 4 worker và 2 phần tử queue.
- Task thứ bảy bị từ chối bằng `RejectedExecutionException` theo `AbortPolicy` mặc định của JDK.
- Khối `finally` thả latch và yêu cầu dừng pool. `Future.get()` chứng minh sáu tác vụ được nhận đã kết thúc trước dòng cuối.

Ví dụ này dùng JDK độc lập để tránh phải khởi chạy Spring chỉ nhằm quan sát thuật toán. Trong Spring, `ThreadPoolTaskExecutor` dùng `ThreadPoolExecutor` tương ứng, nhưng exception khi giao việc bị từ chối theo hợp đồng Spring thường là `TaskRejectedException`.

## 6. Cấu hình trong Spring Framework

Trong một ứng dụng Spring-managed, có thể định nghĩa bean với ba tham số cùng quyết định hành vi:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class WorkerPoolConfig {
    @Bean("workerPool")
    public ThreadPoolTaskExecutor workerPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("worker-");
        return executor;
    }
}
```

Đây là **class cấu hình Spring**, cần project có thư viện `spring-context` tương thích với JDK đang dùng và được container quét/đăng ký. Không thể biên dịch riêng class này chỉ bằng JDK thuần. Khi bean do Spring quản lý, container thực hiện lifecycle khởi tạo và hủy thích hợp; không cần gọi `initialize()` ngay trong phương thức `@Bean`.

Service có thể inject bằng interface:

```java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final TaskExecutor workerPool;

    public ReportService(@Qualifier("workerPool") TaskExecutor workerPool) {
        this.workerPool = workerPool;
    }

    public void requestReport(String id) {
        workerPool.execute(() -> generateReport(id));
    }

    private void generateReport(String id) {
        // Ví dụ: xử lý báo cáo theo mã id.
    }
}
```

Đây là **mảnh tích hợp trong Spring**; code nghiệp vụ dùng `TaskExecutor.execute`, còn chính sách số thread được cấu hình ở `WorkerPoolConfig`. `execute` không trả Future và có thể trở về trước khi task hoàn tất. Muốn lấy kết quả/lỗi của một tác vụ bất đồng bộ, dùng API có Future như `AsyncTaskExecutor.submit` hoặc thiết kế cơ chế hoàn tất rõ ràng.

Nếu ứng dụng dùng `@Async`, cần xác định phương thức thực sự sử dụng bean executor mong muốn (ví dụ `@Async("workerPool")` và lời gọi đi qua proxy). Thay đổi bean không tác động tới một `@Async` đang dùng executor khác.

## 7. Queue quyết định khi nào maxPoolSize có tác dụng

`ThreadPoolTaskExecutor.setQueueCapacity` chọn hàng đợi dựa trên giá trị:

### Queue có sức chứa dương

Với `queueCapacity > 0`, Spring dùng `LinkedBlockingQueue` có giới hạn tương ứng. Ví dụ `core=2, max=8, queue=100`: khi 2 thread lõi bận, task mới thường sẽ vào queue; chỉ khi queue 100 phần tử không nhận thêm tác vụ, pool mới có lý do tăng worker lên 3...8. Kích thước queue lớn giúp hấp thụ burst ngắn nhưng có thể tăng thời gian chờ.

### Queue mặc định rất lớn

Theo JavaDoc, `queueCapacity=Integer.MAX_VALUE` là mặc định của `ThreadPoolTaskExecutor`. Trong điều kiện thực tế, queue này thường không đầy trước khi tài nguyên hệ thống cạn, vì vậy `maxPoolSize` hiếm khi được dùng để mở rộng. Nếu đặt:

```java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(20);
// Không đặt queueCapacity: vẫn dùng mặc định rất lớn.
```

thì *không thể* kỳ vọng pool thường tạo 20 worker dù có hàng trăm tác vụ chờ. Đây là một lý do phổ biến khiến việc “tăng max mà không thấy tăng số thread” xảy ra. Mảnh mã trên là **minh họa cấu hình một executor đã được tạo**.

### QueueCapacity bằng 0

Với `queueCapacity=0`, Spring sử dụng `SynchronousQueue`, tức cơ chế **bàn giao trực tiếp**, không lưu backlog trong queue. Khi không có worker sẵn sàng nhận tác vụ và chưa đạt max, pool có thể tạo thêm worker nhanh hơn. Vì vậy `maxPoolSize` có ảnh hưởng trực tiếp hơn tới số task chạy đồng thời.

Điều này cũng làm hệ thống chạm trần thread và từ chối task nhanh hơn nếu tải liên tục; nên đặt giới hạn max và chiến lược xử lý quá tải có chủ đích.

| Cấu hình | Khi đã có `core` worker bận | Khả năng mở rộng |
| --- | --- | --- |
| Queue rất lớn | Task tiếp tục chờ trong queue | Thường duy trì gần core |
| Queue dương, giới hạn nhỏ | Task chờ tới khi queue đầy | Sau đó tăng dần worker tới max |
| Queue bằng 0 | Cố bàn giao trực tiếp | Có thể mở thêm worker tới max nhanh hơn |

## 8. Thay đổi core và max ảnh hưởng gì tới hiệu năng?

**Tăng `corePoolSize`** có thể giúp xử lý đồng thời nhiều task sớm hơn và giảm thời gian đợi queue khi có tải liên tục. Nhưng quá nhiều thread với việc nặng CPU làm tăng chuyển ngữ cảnh, tranh chấp và độ trễ. Thread lõi cũng tiêu thụ tài nguyên khi rảnh.

**Tăng `maxPoolSize`** chỉ hữu ích nếu cơ chế queue thực sự cho phép mở rộng và hệ thống còn tài nguyên để thực thi thêm. Nếu mỗi task phải chờ một connection JDBC từ pool chỉ có 10 connections, việc tăng worker lên 100 thường chỉ tạo thêm nhiều thread chờ connection, có thể làm chậm hệ thống.

**Giảm `queueCapacity`** thường làm pool mở rộng sớm hơn nhưng cũng khiến saturation/rejection xảy ra sớm hơn khi đạt max. **Tăng queue** giúp chịu burst nhưng có thể biến thời gian chờ thành vấn đề khó thấy. Cả hai đều cần đo bằng lưu lượng, độ trễ và tài nguyên thực tế.

Để chọn cấu hình:

1. Xác định task chủ yếu dùng CPU, block I/O, chờ database hay gọi dịch vụ ngoài. Không có con số `core`/`max` dùng tốt cho mọi workload.
2. Đo lượng task đến, thời gian xử lý, thời gian đợi queue, độ trễ p95/p99, số worker đang hoạt động và số task bị từ chối dưới tải thực tế.
3. Giới hạn thread phù hợp với CPU, memory, connection pool, quota của dịch vụ ngoài và thời gian sống của tác vụ.
4. Chọn queue đủ hấp thụ biến động ngắn hạn mà không che giấu tình trạng quá tải lâu dài. Bổ sung chính sách giới hạn đầu vào/retry hoặc hàng đợi bền vững khi cần.
5. Load test theo các đợt burst và lúc tải kéo dài, quan sát sự ổn định thay vì chỉ đo throughput trong một lần thử.

Ví dụ có ý nghĩa: một task nặng CPU không thể tăng tốc vô hạn nhờ nhiều thread hơn số lõi CPU; một task thường chờ I/O có thể dùng nhiều worker hơn nhưng phải xét giới hạn connection và dịch vụ ngoài. Quyết định cuối cùng nên dựa trên phép đo.

## 9. Tương tác với keepAlive, timeout và cấu hình lúc chạy

`ThreadPoolTaskExecutor` cho phép thay đổi `corePoolSize`, `maxPoolSize` và `keepAliveSeconds` lúc runtime qua API quản lý thích hợp, với các điều kiện hợp lệ của `ThreadPoolExecutor`.

Những điểm cần nhớ:

- Worker **vượt core** thường có thể rút lui sau `keepAliveSeconds` rảnh; việc giảm từ max về core không cần chờ restart ứng dụng.
- Bật `setAllowCoreThreadTimeOut(true)` cho phép worker lõi cũng nghỉ khi rảnh đủ lâu; không làm tăng khả năng nhận task vượt max.
- Thay đổi `corePoolSize` hay `maxPoolSize` không có nghĩa ngay lập tức đúng từng ấy worker đang sống hoặc đang chạy task. Quan sát `getPoolSize()`, `getActiveCount()`, `getQueueSize()` để hiểu trạng thái hiện tại.
- **Không sửa `queueCapacity` như một núm vặn runtime thông thường.** Loại work queue được tạo khi executor khởi tạo; thay đổi giá trị cấu hình trên bean đã chạy không tự tạo lại queue hiện có.
- Khi thay đổi đồng thời hai giới hạn, luôn duy trì `core <= max`. Chẳng hạn muốn tăng core lên cao hơn max hiện tại, phải nâng max trước; muốn hạ max xuống thấp hơn core hiện tại, phải hạ core trước.

`getActiveCount()` và queue size là phép quan sát tại một thời điểm, có thể đổi ngay trong lúc đọc. Chúng thích hợp cho metric/trend, không nên dùng làm nền tảng duy nhất cho một thuật toán đồng bộ chính xác giữa các luồng.

## 10. Khi pool đạt giới hạn: rejection và khả năng xử lý lỗi

Nếu queue đã đầy, worker đã đạt max và executor không thể nhận thêm, Spring `ThreadPoolTaskExecutor` theo chính sách mặc định báo **`TaskRejectedException`** ở phía gọi `execute`/`submit`. Nó phản ánh **lỗi giao tác vụ**, khác với exception xảy ra *bên trong task sau khi đã được nhận*.

Minh họa phía gọi Spring:

```java
try {
    workerPool.execute(() -> process(data));
} catch (org.springframework.core.task.TaskRejectedException rejected) {
    // Phản hồi quá tải, retry có giới hạn hoặc chuyển sang cơ chế bền vững.
}
```

Đây là **mảnh code minh họa** giả sử `workerPool` và `process(data)` đã được định nghĩa. Khi `Runnable` chạy bất đồng bộ rồi ném exception, exception đó không tự nhảy về thread đã gọi `execute` trong `catch` ở trên. Nếu cần nhận kết quả, dùng Future hoặc cơ chế thông báo/lưu trạng thái riêng.

Không nên tự động chọn `CallerRunsPolicy` để “không bị mất task” mà bỏ qua hệ quả: chính sách ấy có thể khiến thread gọi (ví dụ request thread) chạy tác vụ và kéo dài độ trễ; khi pool đã shutdown, hành vi còn phụ thuộc hợp đồng policy. Tương tự, hàng đợi in-memory không giữ được task khi tiến trình chết đột ngột. Với nghiệp vụ cần delivery bền vững, cân nhắc messaging/queue có lưu trữ.

## 11. Những lỗi cấu hình thường gặp

1. **Chỉ tăng `maxPoolSize` mà không đổi queue.** Nếu queue còn rất rộng, pool không mở rộng; phải xét cả `queueCapacity`.
2. **Cho rằng `corePoolSize` là số thread có sẵn khi khởi tạo.** Core worker có thể được tạo lười; muốn prestart phải cấu hình riêng.
3. **Nhầm `maxPoolSize` với số task tối đa trong hệ thống.** Pool còn queue; task chờ không chiếm worker nhưng vẫn chiếm bộ nhớ và chịu độ trễ.
4. **Đặt `corePoolSize > maxPoolSize`.** Cấu hình không hợp lệ; khi chỉnh runtime cần giữ bất biến `core <= max`.
5. **Đặt queue quá lớn để không bị rejection.** Có thể che giấu backlog, tăng độ trễ và nguy cơ cạn bộ nhớ.
6. **Đặt `maxPoolSize` quá cao cho tác vụ phụ thuộc database/API.** Tăng số worker không tăng năng lực của dependency; chỉ tăng tranh chấp.
7. **Tin rằng mọi Spring `TaskExecutor` đều có cùng tham số.** `SyncTaskExecutor`, `SimpleAsyncTaskExecutor` và executor virtual thread có cơ chế khác.
8. **Thử tải không giữ task bận rồi kết luận pool không mở rộng.** Task quá nhanh có thể kết thúc trước khi queue đầy, nên cần kịch bản đo lường tái lập được.
9. **Không quan sát queue và rejection.** Chỉ nhìn CPU hoặc chỉ nhìn số thread không đủ biết backlog và chất lượng dịch vụ.
10. **Nhầm thread timeout với task timeout.** `keepAliveSeconds` áp dụng khi worker rảnh; nó không cắt task đang chạy lâu.
11. **Bỏ qua executor thực tế dùng bởi `@Async`.** Phải xác định bean được chọn trước khi giải thích vì sao cấu hình thay đổi mà luồng thực thi không đổi.

## 12. Tự kiểm tra và ghi nhớ

**Câu 1.** `core=2, max=12, queueCapacity=1000`, có 20 task đồng thời giữ thread bận. Pool thường tạo ngay bao nhiêu worker?  
**Đáp án:** Khoảng 2 worker lõi; 18 task còn lại có thể chờ trong queue còn rộng. Không có cơ sở để khẳng định pool sẽ tạo 12 worker ngay.

**Câu 2.** `core=2, max=4, queue=2`; sáu task được nhận và đều chưa kết thúc. Task thứ bảy sẽ ra sao với policy mặc định?  
**Đáp án:** Pool đang có 4 worker, queue 2 phần tử; task thứ bảy bị từ chối.

**Câu 3.** Giảm `queueCapacity` từ 100 xuống 0 có làm pool luôn chạy bằng `maxPoolSize`?  
**Đáp án:** Không. Queue 0 khiến việc bàn giao trực tiếp và tạo thêm worker xảy ra sớm hơn khi cần, nhưng số thread thực tế vẫn phụ thuộc lượng task, worker rảnh, max và trạng thái pool.

**Câu 4.** Tăng `maxPoolSize` có luôn làm task nặng CPU nhanh hơn?  
**Đáp án:** Không. Nhiều worker có thể gây tranh chấp, quá nhiều chuyển ngữ cảnh và độ trễ, đặc biệt khi vượt năng lực CPU.

**Câu 5.** Khi pool chạy xong một đợt tải cao, vì sao `poolSize` có thể giảm từ 4 về 2?  
**Đáp án:** Hai worker vượt core có thể rút lui sau thời gian rảnh `keepAliveSeconds` theo chính sách mặc định.

Khi phân tích một cấu hình Spring, hãy đọc **ba giá trị theo thứ tự `corePoolSize` → `queueCapacity` → `maxPoolSize`**. Chỉ sau khi hiểu ngưỡng xếp hàng mới suy luận đúng khi nào pool mở rộng và khi nào từ chối tác vụ.

### Tài liệu tham khảo

- [Spring Framework — ThreadPoolTaskExecutor JavaDoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Java SE 17 — ThreadPoolExecutor JavaDoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-003">queueCapacity và lựa chọn hàng đợi nội bộ</a>

<details>
<summary>Xem chi tiết</summary>

# queueCapacity và lựa chọn hàng đợi nội bộ trong ThreadPoolTaskExecutor

## 1. Vì sao queueCapacity quan trọng?

Trong Spring Framework, `ThreadPoolTaskExecutor` cho phép đưa `Runnable` vào một nhóm thread thay vì tạo một thread mới thủ công cho mỗi tác vụ. Khi các worker đang bận mà tiếp tục có công việc mới, executor phải quyết định **xếp hàng**, **tạo thêm worker** hay **từ chối tác vụ**.

Thuộc tính `queueCapacity` trực tiếp quyết định loại hàng đợi bên dưới `ThreadPoolTaskExecutor` và ảnh hưởng mạnh đến hành vi đó. Đây không chỉ là một giới hạn bộ nhớ: lựa chọn hàng đợi làm thay đổi thời điểm pool mở rộng từ `corePoolSize` đến `maxPoolSize`, độ trễ trước khi task bắt đầu, nguy cơ tích backlog và thời điểm xuất hiện rejection.

Ví dụ: hai ứng dụng đều cấu hình `corePoolSize=2`, `maxPoolSize=8`, nhưng một ứng dụng đặt `queueCapacity=1000`, ứng dụng kia đặt `queueCapacity=0`. Ứng dụng thứ nhất thường xếp nhiều việc chờ ngay từ lúc có 2 worker bận, còn ứng dụng thứ hai cố bàn giao ngay cho worker rảnh hoặc tạo thêm worker (tối đa 8). Chỉ đổi `queueCapacity` có thể làm biểu đồ số thread và latency hoàn toàn khác nhau.

**Phạm vi bài:** hàng đợi nội bộ dùng cho *task chưa được thực thi* của `ThreadPoolTaskExecutor`, cách nó tương tác với core/max threads và ý nghĩa vận hành trong ứng dụng Spring. Đây không phải queue bền vững như Kafka/RabbitMQ: khi tiến trình chết, những tác vụ chỉ nằm trong bộ nhớ của executor có thể mất.

## 2. queueCapacity chọn loại hàng đợi nào?

Với `ThreadPoolTaskExecutor` trong Spring Framework 6.x, việc chọn hàng đợi về cơ bản tương ứng:

```java
// Mô phỏng logic createQueue(int) của ThreadPoolTaskExecutor:
BlockingQueue<Runnable> queue = queueCapacity > 0
        ? new LinkedBlockingQueue<>(queueCapacity)
        : new SynchronousQueue<>();
```

Đây là đoạn **minh họa logic**, không phải toàn bộ phương thức tạo executor. Ý nghĩa cụ thể:

| `queueCapacity` | Hàng đợi nội bộ | Task có thể nằm chờ trong queue? | Hành vi đáng chú ý |
| --- | --- | --- | --- |
| Số nguyên dương, ví dụ `1`, `50`, `500` | `LinkedBlockingQueue<Runnable>` với dung lượng được chỉ định | Có, tối đa số phần tử cấu hình | Sau khi đủ core threads, task được xếp hàng trước khi mở thêm non-core threads |
| `0` | `SynchronousQueue<Runnable>` | Không; không có ô lưu task | Task phải được bàn giao trực tiếp cho worker sẵn sàng, hoặc pool tạo worker khác nếu còn chỗ |
| Số âm | `SynchronousQueue<Runnable>` | Không | Cũng đi theo nhánh “không có hàng đợi chứa phần tử”; dùng `0` để biểu đạt rõ ý định |
| Không gọi setter | `LinkedBlockingQueue` với `Integer.MAX_VALUE` | Về lý thuyết rất lớn, nhưng RAM là giới hạn thực tế | Có thể tích backlog lớn; `maxPoolSize` thường không được dùng đến vì queue ít khi đầy |

**Không tự động chọn `ArrayBlockingQueue`:** setter `queueCapacity` không làm Spring tạo mảng vòng có dung lượng cố định; queue mặc định cho mọi giá trị dương là `LinkedBlockingQueue`. Hai implementation có đặc điểm cấp phát và hành vi riêng, nên không thể suy từ tên thuộc tính rằng queue nào cũng như nhau.

### 2.1. LinkedBlockingQueue: lưu task để thực hiện sau

Với `queueCapacity > 0`, queue chứa các `Runnable` chưa có worker thực hiện. Khi người gửi task nhanh hơn khả năng xử lý, số task chờ tăng lên cho tới giới hạn capacity.

- Queue có thứ tự FIFO ở phía chờ, nhưng **không bảo đảm thứ tự hoàn tất** khi nhiều worker thực thi đồng thời.
- `queueCapacity=100` nghĩa là tối đa 100 phần tử đang đợi trong queue; **không có nghĩa** pool chỉ nhận tổng cộng 100 task, cũng không có nghĩa tự tạo 100 threads.
- Task đang chạy trên thread **không được tính** vào số phần tử queue.
- Queue chứa tham chiếu tới task và trạng thái nó giữ, có thể kéo theo retention của payload/request objects. Queue lớn tạo nguy cơ dùng bộ nhớ và chờ lâu trước khi chạy.

### 2.2. SynchronousQueue: bàn giao trực tiếp, không lưu trữ

Với `queueCapacity=0`, `SynchronousQueue` là cơ chế *handoff*: task chỉ được đưa vào khi có worker sẵn sàng nhận, hoặc executor phải tạo worker mới nếu pool còn khả năng mở rộng. Nó **không** giữ lại một backlog để lấy ra sau.

Một đặc điểm gây nhầm lẫn: `SynchronousQueue.size()` luôn là 0. Vì thế, biểu đồ “queue size = 0” **không chứng minh** pool không bị quá tải. Hãy xem thêm `activeCount`, `poolSize`, số lần bị từ chối và latency.

Cấu hình `queueCapacity=0` với `maxPoolSize` rất lớn có thể làm nhiều thread được tạo nhanh trong burst traffic. Do đó, khi chọn kiểu handoff, cần đặc biệt chú ý giới hạn max threads, nguồn downstream và chi phí tạo thread.

## 3. Loại queue làm thay đổi thời điểm mở rộng và từ chối

Trong `ThreadPoolExecutor`, khi số worker chưa tới `corePoolSize` thì task thường được giao cho worker mới. Khi đã tới core, executor thử `workQueue.offer(task)`: nếu queue nhận được, task được xếp chờ; nếu không nhận và pool còn dưới `maxPoolSize`, nó thử mở worker khác; cuối cùng mới đến rejection. Khi shutdown, trạng thái pool còn có thể khiến task bị từ chối sớm.

Điểm cần tập trung là **cùng lời gọi `offer` nhưng hai loại queue cho kết quả khác nhau**: `LinkedBlockingQueue` có chỗ lưu task, còn `SynchronousQueue` chỉ nhận khi một worker đang chờ handoff. Bảng tiếp theo phân tích điều này. Thuật toán bốn bước, race và các nhánh shutdown được trình bày đầy đủ ở bài “Thứ tự nhận tác vụ: tạo luồng cơ bản, xếp hàng, mở rộng rồi từ chối”.

## 4. Ví dụ diễn tiến với cùng core và max, chỉ đổi queueCapacity

Giả sử:

- `corePoolSize=2`.
- `maxPoolSize=4`.
- Mỗi task đều chạy lâu, tất cả worker còn bận trong lúc 7 task đến liên tiếp.
- Executor đang trống lúc bắt đầu, không có task nào hoàn tất giữa các lần gửi.

| Task đến | `queueCapacity=2` | `queueCapacity=0` |
| --- | --- | --- |
| T1 | Core worker 1 thực thi | Core worker 1 thực thi |
| T2 | Core worker 2 thực thi | Core worker 2 thực thi |
| T3 | Nằm trong queue (1/2) | Non-core worker 3 thực thi |
| T4 | Nằm trong queue (2/2) | Non-core worker 4 thực thi |
| T5 | Non-core worker 3 thực thi | Từ chối (đã bận 4 workers, không có chỗ queue) |
| T6 | Non-core worker 4 thực thi | Từ chối |
| T7 | Từ chối (4 workers đang bận, queue đã đầy) | Từ chối |

Kết quả tại thời điểm T7 được gửi:

- **Capacity 2:** 4 task đang chạy, 2 task chờ, một task bị từ chối.
- **Capacity 0:** 4 task đang chạy, không task nào được giữ trong queue, ba task bị từ chối.

Bảng là mô phỏng với điều kiện các task chưa hoàn tất. Trong runtime thực, worker có thể xong sớm và nhận task khác; những con số quan sát được sẽ khác. Cốt lõi không phải “queue 0 nhanh hơn” mà là **queue 0 ưu tiên mở thread/nhận hoặc từ chối ngay, queue dương có thêm không gian chờ trước khi mở rộng**.

## 5. Thực nghiệm chạy được bằng Java 21 + Spring Framework

Chương trình sau cố ý giữ các worker ở một `CountDownLatch` để tái hiện đúng tình huống queue đầy, không dựa vào `Thread.sleep()` và timing may rủi. Nó tạo hai `ThreadPoolTaskExecutor` lần lượt có capacity 1 và capacity 0, giữ nguyên `corePoolSize=1` và `maxPoolSize=2`.

**Cấu trúc dự án:**

```text
queue-capacity-demo/
├── pom.xml
└── src/main/java/example/QueueCapacityDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>queue-capacity-demo</artifactId>
  <version>1.0-SNAPSHOT</version>
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
          <mainClass>example.QueueCapacityDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

**Tệp `src/main/java/example/QueueCapacityDemo.java`:**

```java
package example;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class QueueCapacityDemo {
    public static void main(String[] args) throws InterruptedException {
        demonstrate(1);
        demonstrate(0);
    }

    private static void demonstrate(int capacity) throws InterruptedException {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(capacity);
        pool.setThreadNamePrefix("demo-");
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(5);
        pool.afterPropertiesSet();

        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch queuedDone = new CountDownLatch(1);

        Runnable holdWorker = () -> {
            firstStarted.countDown();
            bothStarted.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            pool.execute(holdWorker);
            if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("First worker did not start");
            }

            if (capacity > 0) {
                pool.execute(queuedDone::countDown);
            }
            pool.execute(holdWorker);

            if (!bothStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Second worker did not start");
            }

            boolean rejected = false;
            try {
                pool.execute(() -> { });
            } catch (TaskRejectedException ex) {
                rejected = true;
            }

            System.out.printf(
                    "capacity=%d queue=%s pool=%d waiting=%d rejected=%b%n",
                    capacity,
                    pool.getThreadPoolExecutor().getQueue().getClass().getSimpleName(),
                    pool.getPoolSize(),
                    pool.getQueueSize(),
                    rejected);

            release.countDown();
            if (capacity > 0 && !queuedDone.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Queued task was not executed");
            }
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }
}
```

**Biên dịch và chạy** trong thư mục dự án:

```bash
mvn -q -DskipTests compile exec:java
```

**Output kiểm chứng:**

```text
capacity=1 queue=LinkedBlockingQueue pool=2 waiting=1 rejected=true
capacity=0 queue=SynchronousQueue pool=2 waiting=0 rejected=true
```

Chương trình biên dịch với `maven.compiler.release=21`, đã được chạy kiểm chứng trên JDK có hỗ trợ Java 21. Ý nghĩa hai dòng:

1. Với capacity 1: task đầu đang giữ core worker; task thứ hai nằm trong `LinkedBlockingQueue`; task thứ ba làm pool tăng lên 2 workers; task tiếp theo bị từ chối.
2. Với capacity 0: task đầu đang giữ core worker; task thứ hai làm pool tăng lên 2 workers; task tiếp theo bị từ chối ngay vì `SynchronousQueue` không thể chứa backlog.

`firstStarted` và `bothStarted` bảo đảm worker đã thực sự chạy trước khi đo. `release` giải phóng worker; `queuedDone` bảo đảm task từng nằm trong queue đã được xử lý. Đoạn `finally` bảo đảm thả latch và shutdown pool, hạn chế thread bị treo nếu kiểm tra thất bại.

Ở đây gọi `afterPropertiesSet()` vì ta tự tạo executor ngoài Spring container. Khi khai báo `ThreadPoolTaskExecutor` là bean do Spring quản lý, container gọi callback khởi tạo tương ứng; thông thường không cần gọi thủ công.

Nếu loại bỏ cơ chế giữ worker bằng latch, số `poolSize` và `waiting` có thể thay đổi theo lịch chạy; đó là hành vi bình thường của hệ thống đồng thời, không phải lỗi của queue.

## 6. Kết hợp queueCapacity với corePoolSize và maxPoolSize

Ba tham số tạo thành một chính sách tiếp nhận công việc, không nên tối ưu riêng lẻ.

### 6.1. corePoolSize

Đây là ngưỡng worker lõi: khi có ít hơn `corePoolSize` worker, pool ưu tiên tạo worker trước khi xếp task vào queue. Mặc định `ThreadPoolTaskExecutor` có core size 1.

Điều đó **không có nghĩa** các core threads luôn được tạo sẵn ngay khi khởi tạo; theo cấu hình thông thường, thread được tạo khi có task, trừ khi có cấu hình chủ động prestart.

### 6.2. maxPoolSize

Đây là giới hạn tổng worker của pool. Khi queue có thể tiếp tục nhận task, pool thường không mở rộng vượt core chỉ vì có backlog. Khi queue đầy hoặc không thể handoff, pool mới cố tăng đến max.

Ví dụ đặt `core=2, max=100, queueCapacity=10000` vẫn có thể chỉ duy trì hai worker trong thời gian dài, trong khi hàng nghìn task xếp hàng. `max=100` không có nghĩa có ngay 100 worker để xử lý backlog.

### 6.3. keepAliveSeconds và allowCoreThreadTimeOut

Worker vượt core có thể rút lui sau thời gian nhàn rỗi `keepAliveSeconds` (mặc định thường là 60 giây). Nếu bật `allowCoreThreadTimeOut`, core workers cũng có thể hết hạn khi nhàn rỗi theo quy tắc của `ThreadPoolExecutor`. Chúng ảnh hưởng đến cách pool co lại, **không thay đổi loại hàng đợi** đã chọn bởi `queueCapacity`.

### 6.4. Đổi queueCapacity khi chương trình đang chạy

Hàng đợi được tạo trong giai đoạn khởi tạo executor. Việc chỉ gọi `setQueueCapacity(...)` lên một executor **đã khởi tạo** không tự thay thế queue hiện hữu hay di chuyển các task đang chờ. Hãy coi lựa chọn dung lượng/loại queue là cấu hình lúc tạo pool; nếu muốn thay đổi, cần một kế hoạch tái tạo/reconfigure executor an toàn phù hợp với lifecycle.

Không suy rằng mọi setter trong `ThreadPoolTaskExecutor` đều được hỗ trợ cập nhật runtime như nhau. `corePoolSize` và `maxPoolSize` có hỗ trợ thay đổi động trên executor bên dưới, còn lựa chọn hàng đợi được chốt khi khởi tạo.

## 7. Cấu hình trong Spring application

Một cấu hình dùng hàng đợi có giới hạn và đặt tên bean rõ ràng:

```java
// Fragment trong lớp @Configuration của ứng dụng Spring.
@Bean("businessExecutor")
public ThreadPoolTaskExecutor businessExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("business-");
    return executor;
}
```

Đây là **fragment** cần import `org.springframework.context.annotation.Bean` và `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor` trong một class cấu hình Spring. Các con số 4/8/100 chỉ minh họa, không phải preset phù hợp mọi hệ thống. Bean phải được tạo và quản lý bởi Spring để callback khởi tạo và shutdown diễn ra đúng lifecycle.

Nếu dùng `@Async`, cần cấu hình kích hoạt xử lý async (trong Spring Framework thuần thường là `@EnableAsync`) rồi chỉ định executor:

```java
// Fragment trong Spring-managed service, lời gọi qua proxy @Async.
@Async("businessExecutor")
public void processAsync(String id) {
    // Công việc nền.
}
```

`@Async` đưa phương thức vào executor tương ứng khi lời gọi đi qua Spring proxy; lời gọi `this.processAsync(...)` trong cùng bean thường không áp dụng async theo proxy mặc định. Hơn nữa, `@Async` **không loại bỏ queue**, vì task vẫn được gửi đến executor và chịu cùng giới hạn/saturation.

Với Spring Boot có auto-configured task executor, property `spring.task.execution.pool.queue-capacity` có thể được dùng theo cấu hình Boot tương ứng, ví dụ:

```properties
spring.task.execution.pool.core-size=4
spring.task.execution.pool.max-size=8
spring.task.execution.pool.queue-capacity=100
```

Phạm vi của các property phụ thuộc vào executor mà Boot thực sự tự cấu hình. Nếu ứng dụng tự định nghĩa `@Bean ThreadPoolTaskExecutor` như trên, không nên mặc định rằng các property này sẽ tự áp vào bean tùy chỉnh; cần cấu hình qua builder hoặc setter thích hợp và kiểm tra trạng thái thực tế sau khởi tạo.

## 8. Nên chọn queueCapacity bao nhiêu?

Không có một giá trị chung phù hợp mọi ứng dụng. Hãy bắt đầu từ tính chất task, yêu cầu latency, giới hạn downstream và khả năng chịu backlog.

| Lựa chọn | Ưu điểm | Chi phí/rủi ro | Tình huống cân nhắc |
| --- | --- | --- | --- |
| `queueCapacity=0` | Không tích task chờ, thấy saturation sớm, pool có thể tăng worker nhanh tới max | Burst tạo thêm threads; rejection ngay khi tất cả workers bận; có thể gây tải dồn xuống DB/API | Muốn handoff trực tiếp, quản lý nghiêm `maxPoolSize` và giới hạn nguồn |
| Queue dương nhỏ, ví dụ 10–100 | Hấp thụ burst ngắn; làm rõ giới hạn backlog | Vẫn có queue latency và rejection; cần sizing theo load | Tác vụ ngắn-vừa, cần giới hạn bộ nhớ/độ trễ |
| Queue dương lớn | Ít rejection trong burst, không cần mở thread thêm sớm | Chờ lâu, memory retention, che giấu quá tải, maxPoolSize khó phát huy | Chỉ khi backlog lớn thực sự chấp nhận được và có giám sát |
| Mặc định `Integer.MAX_VALUE` | Thuận tiện cho thử nghiệm hoặc tải rất thấp | Rủi ro backlog tăng không kiểm soát trong tải dai dẳng, có thể làm cạn RAM | Không nên coi là backpressure an toàn cho production |

### 8.1. Đặt giới hạn bằng một ngân sách chờ

Giả sử các task đang được xử lý với throughput ổn định khoảng 40 task/s, và mục tiêu phần thời gian **nằm trong queue** không quá 0,5 giây ở điều kiện tải quan tâm. Suy luận đơn giản gợi ý chỉ muốn khoảng `40 × 0,5 = 20` task chờ ở mức đó.

Đây là phép ước lượng dùng để suy nghĩ, **không phải công thức bảo đảm**: service time có phân phối, burst traffic, task dài/ngắn khác nhau, multi-thread scheduling và downstream saturation làm latency thực tế thay đổi. Đo p95/p99 queue wait và rejection trong load test để xác nhận. Nếu tốc độ gửi task thường xuyên lớn hơn tốc độ xử lý, **bất kỳ queue hữu hạn nào cũng sẽ đầy theo thời gian**.

### 8.2. CPU-bound khác I/O-bound

- Với **CPU-bound**, tăng số worker quá số lõi CPU hữu dụng thường làm tăng context switch và cạnh tranh CPU. Queue hữu hạn và chính sách giới hạn đầu vào thường quan trọng hơn “đẻ nhiều threads”.
- Với **I/O-bound**, nhiều worker hơn có thể giữ mức độ đồng thời cao khi một số thread đang chờ mạng/DB. Tuy vậy, quá nhiều task đồng thời có thể làm cạn connection pool hoặc gây timeout ở service khác; cần giới hạn concurrency downstream.
- Với task thời gian rất khác nhau, một queue FIFO chung có thể khiến tác vụ nhỏ nằm sau tác vụ dài. Cân nhắc phân tách executor theo loại việc khi có bằng chứng đo đạc.

## 9. Khi queue đầy: rejection và backpressure

Mặc định, executor bên dưới thường dùng chính sách từ chối kiểu abort. Spring `ThreadPoolTaskExecutor` biểu lộ sự kiện từ chối qua `TaskRejectedException` khi gọi `execute(...)`. Cần phân biệt **task bị từ chối lúc submit** với **task đã được chấp nhận nhưng chạy và ném exception ở worker**.

Một số chính sách có thể cấu hình bằng `setRejectedExecutionHandler(...)`:

- **AbortPolicy:** từ chối bằng exception; caller quyết định báo lỗi, retry giới hạn, hoặc lưu công việc sang cơ chế bền vững.
- **CallerRunsPolicy:** trong điều kiện executor chưa shutdown, caller tự thực thi task nếu pool không nhận, tạo một dạng điều tiết vì caller bị chậm lại. Đừng dùng tùy tiện với request thread nhạy latency, event loop hoặc task có thể block lâu.
- **DiscardPolicy/DiscardOldestPolicy:** có thể làm mất task; chỉ phù hợp khi đã phân tích rõ semantics và chấp nhận mất/loại task. Không dùng ngầm cho công việc nghiệp vụ bắt buộc hoàn thành.

Một `queueCapacity` hữu hạn kết hợp rejection rõ ràng giúp hệ thống **thừa nhận giới hạn năng lực**. Nhưng nếu caller retry liên tục không backoff, hoặc lưu vô hạn vào một queue khác, quá tải chỉ bị dời chỗ.

Nếu nghiệp vụ đòi hỏi việc phải tồn tại qua restart, phải có cơ chế bền vững như transaction outbox hoặc message broker phù hợp; hàng đợi nội bộ của `ThreadPoolTaskExecutor` không cung cấp durability, acknowledgement hay retry bền vững.

## 10. Quan sát đúng trước khi thay queueCapacity

Các chỉ số hữu ích nên đo theo thời gian:

1. **Pool size, active count, core/max:** hiện có bao nhiêu worker và bao nhiêu worker đang bận; lưu ý metric có thể là snapshot thay đổi rất nhanh.
2. **Queue size/capacity:** queue đang giữ bao nhiêu task; với `SynchronousQueue`, size=0 là thuộc tính cấu trúc, không cho biết saturation.
3. **Thời gian chờ vào queue:** tính từ lúc submit đến lúc task bắt đầu chạy. Có thể bọc task ở lớp submit để ghi timestamp; tách queue wait khỏi execution time.
4. **Thời gian thực thi task:** đo thời gian chạy sau khi worker nhận; kết hợp throughput và tỷ lệ chờ/CPU.
5. **Rejection, timeout, error, cancellation:** phản ánh giới hạn nhận việc và độ tin cậy. Không chỉ đo CPU tổng.
6. **Giới hạn downstream:** DB connection pool, HTTP client concurrency, network latency, GC/memory và CPU quota container.

Trong ví dụ Java, `getThreadPoolExecutor().getQueue()` được dùng **chỉ để xác nhận loại queue và quan sát**. Tránh chỉnh sửa thủ công queue, lấy `Runnable` ra ngoài hoặc dựa vào cấu trúc nội bộ trong business service; thao tác đó dễ phá giả định concurrency của executor.

Có thể gắn Micrometer/Actuator cho executor mà ứng dụng quản lý nếu nền tảng hỗ trợ; cần xác minh bean được instrument và dashboard phản ánh đúng pool đang dùng. Không đánh giá một giá trị metric tại duy nhất một thời điểm.

## 11. Lỗi phổ biến

1. **Nghĩ `queueCapacity=0` là queue dung lượng 0 vẫn xếp được một ít task:** thực tế Spring dùng `SynchronousQueue`, không có backlog lưu trữ.
2. **Cho rằng queue dương chọn `ArrayBlockingQueue`:** mặc định Spring dùng `LinkedBlockingQueue`.
3. **Tăng `maxPoolSize` mà giữ queue vô hạn:** các task vẫn được xếp hàng sau core; pool hiếm khi mở rộng đến max.
4. **Hiểu queueCapacity là tổng số task được nhận:** queue chỉ đếm việc **đang chờ**, không bao gồm task đang chạy trên worker.
5. **Đặt queue quá lớn để tránh lỗi:** có thể thay rejection sớm bằng latency cao và nguy cơ hết memory.
6. **Đặt queue bằng 0 nhưng không giới hạn max:** có thể tạo quá nhiều threads khi burst, gây nghẽn downstream.
7. **Chỉ nhìn `getQueueSize() == 0`:** với `SynchronousQueue`, metric này luôn bằng 0 kể cả khi mọi worker bận.
8. **Thay `setQueueCapacity` sau initialization rồi tưởng queue đổi:** queue đã được tạo; cấu hình lại an toàn yêu cầu lifecycle phù hợp.
9. **Bỏ qua `TaskRejectedException`:** caller có thể báo thành công dù executor không nhận task; phải định nghĩa xử lý quá tải.
10. **Tin rằng queue FIFO bảo đảm hoàn tất FIFO:** với nhiều worker, thứ tự kết thúc phụ thuộc thời gian chạy và scheduling.
11. **Chỉ kiểm thử không tải:** queue capacity, max pool và rejection thường chỉ hiện rõ khi tất cả worker đang bận; cần thử nghiệm có kiểm soát và đo dưới tải đại diện.

## 12. Bài tập tự kiểm tra

**Tình huống A.** Pool có `core=3, max=6, queueCapacity=2`; 3 worker đều bận, không task nào hoàn tất. Ba task mới đến lần lượt. Điều gì xảy ra?

- Task mới thứ nhất và thứ hai vào `LinkedBlockingQueue`.
- Task mới thứ ba khiến pool thử mở worker thứ tư vì queue đầy.
- Pool **không** đợi cho đến khi tạo đủ 6 worker rồi mới xếp queue.

**Tình huống B.** Cùng pool nhưng `queueCapacity=0`. Ba task mới đến khi 3 core workers đang bận thì sao?

- Không có chỗ lưu task trong queue; pool có thể mở thêm workers thứ 4, 5, 6 để chạy ba task mới.
- Nếu cả 6 workers vẫn bận, task tiếp theo không bàn giao được và bị từ chối theo rejection policy.

**Tình huống C.** Ứng dụng có 2 workers, 500 task đang chờ, CPU chỉ 15%. Có nên tăng `queueCapacity` thêm nữa không?

Không thể quyết định chỉ từ CPU thấp. Hãy kiểm tra workers đang chờ I/O/lock, DB connection pool, thời gian queue wait, tốc độ producer/consumer và timeout. Tăng queue không làm task chạy nhanh hơn, chỉ cho phép backlog lớn hơn.

## 13. Những điểm cần nhớ

- `queueCapacity > 0` → Spring tạo `LinkedBlockingQueue` có dung lượng chỉ định.
- `queueCapacity <= 0` → Spring tạo `SynchronousQueue` không chứa backlog.
- Quy tắc nhận việc thông thường: **tạo core worker → thử queue → tăng đến max nếu queue không nhận → reject nếu hết chỗ**.
- Queue lớn có thể tăng độ trễ và giữ bộ nhớ; queue 0 khiến pool mở thread sớm nhưng có thể reject sớm. Cần chọn cùng `corePoolSize`, `maxPoolSize`, mức tải và nguồn downstream.
- Quan sát queue wait, active threads, saturation và rejection; riêng queue size không đủ, đặc biệt với `SynchronousQueue`.
- `ThreadPoolTaskExecutor` là executor trong bộ nhớ, không phải hệ thống hàng đợi bền vững.

### Tài liệu tham khảo

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2 — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Java 21 — ThreadPoolExecutor API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java 21 — SynchronousQueue API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/SynchronousQueue.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-004">Thứ tự nhận tác vụ: tạo luồng cơ bản, xếp hàng, mở rộng rồi từ chối</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự nhận tác vụ trong nhóm luồng: tạo core thread, xếp hàng, mở rộng rồi từ chối

Khi gửi một tác vụ đến nhóm luồng (thread pool), việc có “còn chỗ” không chỉ phụ thuộc vào số luồng. Với cơ chế `ThreadPoolExecutor` của Java, cũng là nền tảng của `ThreadPoolTaskExecutor` trong Spring Framework, quyết định nhận tác vụ thường theo trình tự:

**Tạo worker trong giới hạn `corePoolSize` → thử đưa tác vụ vào queue → nếu không thể xếp hàng thì mở thêm worker đến `maxPoolSize` → nếu vẫn không nhận được thì áp dụng chính sách từ chối (rejection).**

Đây là **thứ tự ưu tiên khi nhận task**, không phải bốn giai đoạn bắt buộc mà mọi task phải đi qua. Một tác vụ được worker thực thi ngay có thể chưa từng nằm trong queue. Tác vụ khác có thể xếp hàng trong khi executor vẫn chưa dùng hết `maxPoolSize`.

Hiểu trình tự này giúp tránh lỗi cấu hình rất phổ biến: **tăng `maxPoolSize` nhưng không thấy pool mở thêm luồng vì queue vẫn còn chỗ**.

## 1. Những thành phần quyết định việc nhận tác vụ

Trong Java, `ThreadPoolExecutor` quản lý hai nơi đặt công việc:

- **Worker threads:** luồng thực sự lấy tác vụ và chạy `Runnable`/`Callable`. Một worker bận I/O hoặc chờ lock vẫn đang chiếm một vị trí thực thi.
- **Work queue:** nơi lưu tác vụ đã được executor chấp nhận nhưng chưa có worker lấy ra để chạy. Với queue có giới hạn, khi đầy, executor phải chọn phương án khác.
- **Rejection handler:** chính sách xử lý khi một tác vụ không được chấp nhận, chẳng hạn pool đã dừng hoặc số worker và queue đều không có khả năng nhận thêm.

Các tham số quan trọng:

| Tham số | Vai trò | Cần hiểu chính xác |
| --- | --- | --- |
| `corePoolSize` | Số worker lõi mà pool ưu tiên tạo khi có tác vụ mới | **Không có nghĩa** tất cả core threads đã tồn tại ngay lúc khởi tạo |
| `maxPoolSize` | Số worker tối đa pool được phép tạo | Chủ yếu phát huy tác dụng khi queue không nhận thêm task |
| `queueCapacity` | Số task tối đa được xếp chờ trong hàng đợi theo cấu hình Spring | Dương → bounded `LinkedBlockingQueue` (với dung lượng đã cho); bằng 0 → `SynchronousQueue` để bàn giao trực tiếp |
| `keepAliveSeconds` | Thời gian nhàn rỗi trước khi worker vượt core có thể được thu hồi | Mặc định không làm core worker hết hạn; có cấu hình cho phép core timeout |
| `RejectedExecutionHandler` | Hành vi khi không nhận được tác vụ | Từ chối không nhất thiết có nghĩa ném lỗi; tùy policy |
| Trạng thái executor | `RUNNING`, quá trình shutdown và trạng thái kết thúc | Pool đã shutdown có thể từ chối task **ngay cả khi chưa đầy** |

Trong `ThreadPoolExecutor` thuần Java, queue cụ thể được truyền qua constructor. Trong `ThreadPoolTaskExecutor` của Spring, `setQueueCapacity(...)` chọn kiểu queue: dung lượng **lớn hơn 0** tạo `LinkedBlockingQueue`; dung lượng **0 hoặc âm** tạo `SynchronousQueue` (thực tế nên dùng 0 để biểu đạt ý định bàn giao trực tiếp).

### Core threads có được tạo sẵn không?

Thông thường `ThreadPoolExecutor` tạo core workers **theo nhu cầu khi task đến**, không nhất thiết tạo đủ lúc khởi động. Có thể dùng `prestartCoreThread()`/`prestartAllCoreThreads()` trong Java, hoặc `setPrestartAllCoreThreads(true)` trong Spring, nếu cần tạo trước.

Vì vậy câu “tạo luồng cơ bản trước” nghĩa là **ưu tiên tạo worker trong giới hạn core khi nhận task mới và số worker hiện có còn thấp hơn core**, chứ không có nghĩa JVM phải tạo tất cả core workers ở thời điểm khởi tạo bean.

## 2. Thuật toán tiếp nhận task của ThreadPoolExecutor

Giả sử executor còn đang chạy và có `corePoolSize ≤ maxPoolSize`.

~~~text
             execute(task)
                    |
                    v
         pool còn dưới corePoolSize?
                /            \
              CÓ             KHÔNG
              |                |
     thử tạo core worker       v
        để chạy task       thử queue.offer(task)
              |             /           \
     tạo thành công?      THÀNH CÔNG    KHÔNG NHẬN
         /     \             |              |
        CÓ    KHÔNG          v              v
        |       |      kiểm tra lại     thử tạo worker
    đã nhận     +----> trạng thái       tới maxPoolSize
        |               executor         /       \
        v                   |         thành công thất bại
      chạy task        nếu shutdown          |        |
                       thì loại khỏi         v        v
                       queue và reject     chạy task reject
~~~

Sơ đồ là mô hình học tập của hàm `execute(...)`, bỏ qua một số nhánh kiểm tra cạnh tranh. Quy trình chính xác có kiểm tra lại trạng thái sau khi enqueue, bảo đảm có ít nhất một worker khi queue chứa việc mà worker count bằng 0, và xử lý cạnh tranh với shutdown. Không nên xem sơ đồ như một chuỗi thao tác nguyên tử, cố định cho mọi lần gọi đồng thời.

### Bước 1 — Ưu tiên tạo worker trong giới hạn core

Nếu số worker hiện có **nhỏ hơn `corePoolSize`**, executor cố tạo worker mới để chạy chính task vừa nhận. Quy tắc này được ưu tiên hơn xếp queue, **kể cả có worker khác đang rảnh** trong một số tình huống. Nếu tạo worker thành công, task được nhận và không cần thử xếp hàng.

Ví dụ core = 2, pool chưa có worker. Hai tác vụ tới khi tác vụ trước vẫn chạy: tác vụ thứ nhất làm pool tăng lên 1 worker; tác vụ thứ hai làm pool tăng lên 2 workers.

### Bước 2 — Khi đã tới core, ưu tiên xếp hàng

Nếu số worker đã đạt hoặc vượt core, executor gọi thao tác tương đương `workQueue.offer(task)` để **thử xếp hàng mà không chờ đến khi có chỗ**.

- Nếu queue nhận được task, task được chấp nhận và chờ worker lấy.
- Điều này xảy ra **trước** khi mở thêm worker đến max. Vì vậy pool có thể giữ 2 workers trong khi `maxPoolSize=20` và queue đang tích lũy hàng trăm task.
- Sau khi enqueue, executor cần kiểm tra lại trạng thái. Nếu một lệnh shutdown xảy ra đồng thời và task có thể được gỡ khỏi queue, executor sẽ từ chối task đó. Nếu pool không còn worker, nó phải cố khởi động worker để xử lý queue.

**“Đã enqueue” không bảo đảm task sẽ chạy ngay**, hoặc sẽ hoàn thành trước timeout của caller. Thời gian chờ queue phải được xem là một phần của độ trễ end-to-end.

### Bước 3 — Queue không nhận thì mới thử mở rộng đến max

Khi queue không thể nhận thêm (ví dụ queue bounded đã đầy hoặc `SynchronousQueue` không tìm được worker nhận bàn giao), executor thử tạo **non-core worker**, miễn tổng worker count còn dưới `maxPoolSize`.

Worker mới nhận luôn tác vụ vừa gửi. Các task cũ trong queue **không nhất thiết được xử lý trước task mới này**, bởi worker mở rộng có thể bắt đầu chạy task mới ngay, song song với các worker đang chạy. Không dựa vào tổng thứ tự hoàn tất theo thứ tự submit.

Một khi task đã vào queue, việc tăng max không tự động bắt pool đang chạy phải tạo thêm worker chỉ để rút ngắn queue; nó vẫn phụ thuộc vào đường đi tiếp nhận task, các cấu hình và hoạt động thực tế.

### Bước 4 — Không thể tạo worker/nhận queue thì reject

Nếu cả queue lẫn khả năng tăng worker đều không nhận task, executor gọi `RejectedExecutionHandler`. Ngoài ra, **task gửi sau khi executor đã shutdown cũng bị từ chối** dù số worker hoặc queue còn thấp hơn ngưỡng.

Với `AbortPolicy`, lời gọi `execute(task)` ném `RejectedExecutionException` ở Java. Trong `ThreadPoolTaskExecutor`, Spring thường chuyển tình huống từ chối thành `TaskRejectedException` theo hợp đồng `TaskExecutor`. Các policy khác có hành vi khác: chạy trên caller hoặc bỏ task, nên không thể suy ra “bị từ chối = chắc chắn có exception” trong mọi cấu hình.

## 3. Một ví dụ đếm từng task

Cho pool có:

~~~text
corePoolSize = 2
maxPoolSize  = 4
queueCapacity = 2
~~~

Giả sử các task đủ lâu để **không task nào kết thúc trong lúc lần lượt gửi 7 task**. Không có worker được prestart.

| Task đến | Quyết định | Pool size sau khi nhận | Queue đang chờ |
| --- | --- | ---: | ---: |
| 1 | Tạo core worker #1 | 1 | 0 |
| 2 | Tạo core worker #2 | 2 | 0 |
| 3 | Đưa vào queue | 2 | 1 |
| 4 | Đưa vào queue | 2 | 2 |
| 5 | Queue đầy → tạo worker #3 | 3 | 2 |
| 6 | Queue đầy → tạo worker #4 | 4 | 2 |
| 7 | Queue đầy + đã đạt max → reject (nếu AbortPolicy) | 4 | 2 |

Ở thời điểm đó executor giữ 4 tác vụ đang chạy và 2 tác vụ chờ: **6 task được nhận, task thứ 7 bị từ chối**.

Đây là **kịch bản giả định tải giữ nguyên**, không phải thứ tự đếm bất biến trong mọi chương trình đa luồng. Nếu một worker hoàn thành và lấy task khỏi queue trước khi task tiếp theo được gửi, executor có thể tiếp tục enqueue hoặc bàn giao công việc mà không tăng pool/reject. Với submission đồng thời, pool size/queue size chỉ là snapshot.

### Hiểu đúng “năng lực nhận tối đa”

Với một pool đã bận hoàn toàn, queue có giới hạn dương `Q` và pool max `M`, số công việc có thể tồn tại đồng thời trong pool khoảng **M đang chạy + Q đang chờ**, *khi xét các task bị giữ đủ lâu và không có chuyển động khác*. Đây là giới hạn sức chứa tức thời, **không phải throughput mỗi giây**.

Năng lực hoàn tất còn phụ thuộc thời gian thực thi, CPU, I/O, tài nguyên kết nối và lock. Không thể suy ra “pool max 20 nên xử lý được 20.000 request/giây”.

## 4. Ví dụ Java 17 chạy độc lập, xác nhận đủ bốn bước

Ví dụ dùng `ThreadPoolExecutor` của JDK, có cùng quy tắc tiếp nhận tác vụ mà Spring `ThreadPoolTaskExecutor` bọc lại. Dùng `CountDownLatch` để giữ worker thứ nhất và thứ ba đang bận; nhờ đó các kết quả đếm không phụ thuộc tốc độ máy.

Tạo tệp **`PoolAdmissionDemo.java`**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PoolAdmissionDemo {
    private static Runnable hold(CountDownLatch started, CountDownLatch release) {
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
                1, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch thirdStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            pool.execute(hold(firstStarted, release));
            if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task 1 did not start");
            }
            System.out.printf("task 1: workers=%d, queue=%d%n",
                    pool.getPoolSize(), pool.getQueue().size());

            pool.execute(() -> { });
            System.out.printf("task 2: workers=%d, queue=%d%n",
                    pool.getPoolSize(), pool.getQueue().size());

            pool.execute(hold(thirdStarted, release));
            if (!thirdStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Task 3 did not start");
            }
            System.out.printf("task 3: workers=%d, queue=%d%n",
                    pool.getPoolSize(), pool.getQueue().size());

            try {
                pool.execute(() -> { });
                System.out.println("task 4: accepted");
            } catch (RejectedExecutionException expected) {
                System.out.println("task 4: rejected");
            }
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

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 PoolAdmissionDemo.java
java PoolAdmissionDemo
~~~

**Stdout chính xác** của ví dụ (đã kiểm tra với JDK 21 biên dịch ở mức `--release 17`):

~~~text
task 1: workers=1, queue=0
task 2: workers=1, queue=1
task 3: workers=2, queue=1
task 4: rejected
~~~

Giải thích:

1. **Task 1:** pool đang có 0 worker, nhỏ hơn core=1, nên tạo worker đầu tiên. Latch `firstStarted` xác nhận task đã bắt đầu, sau đó nó chờ `release`.
2. **Task 2:** pool đã đạt core và worker đang chờ latch, nên task thứ hai vào queue có capacity=1; nó chưa chạy.
3. **Task 3:** queue đã đầy, pool còn dưới max=2, nên tạo worker thứ hai cho task mới. `thirdStarted` xác nhận worker thứ hai cũng đã bắt đầu và đang chờ.
4. **Task 4:** queue vẫn đầy và cả hai workers đã tồn tại, nên `AbortPolicy` ném exception; chương trình in `rejected`.
5. Khối `finally` mở latch để các task đã nhận có thể thoát rồi đóng pool. Không để các worker bị chặn vô hạn trong ứng dụng thật.

Điểm quan trọng là **task 2 xếp queue trước task 3 nhưng task 3 lại được worker #2 chạy ngay**. Do đó thứ tự *được tiếp nhận* không đồng nghĩa với thứ tự *bắt đầu*, *hoàn thành* hay *được commit xuống database*.

## 5. Hành vi thay đổi theo kiểu queue

### 5.1. Queue có giới hạn dương (bounded queue)

Ví dụ:

~~~java
new ThreadPoolExecutor(
    2, 4, 60, TimeUnit.SECONDS,
    new java.util.concurrent.ArrayBlockingQueue<>(10)
);
~~~

Đây là **biểu thức Java minh họa khởi tạo**, chưa phải một chương trình độc lập; cần import `ThreadPoolExecutor` và `TimeUnit` nếu đặt trong tệp khác.

Với task chạy đủ lâu, hai workers đầu tiên được tạo tới core=2, mười task tiếp theo có thể được xếp vào queue, sau đó pool mới mở tới max=4; khi bão hòa sẽ từ chối theo handler. Queue hữu hạn giúp tạo giới hạn chờ và tạo tín hiệu quá tải sớm hơn, nhưng không tự quyết định số giới hạn phù hợp: queue quá lớn vẫn có thể làm timeout tích tụ.

### 5.2. Queue rất lớn hoặc “không giới hạn thực tế”

Ví dụ Spring:

~~~java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(100);
executor.setQueueCapacity(Integer.MAX_VALUE);
~~~

Đây là các **lệnh cấu hình bên trong bean `ThreadPoolTaskExecutor` đã tạo**, không phải chương trình độc lập.

Khi đã có 2 workers, queue cực lớn sẽ tiếp tục nhận task, thường **không đầy trong tải thực tế**, do đó pool hầu như không có lý do tăng từ 2 lên 100. “Max bằng 100” không có nghĩa có 100 workers; nó chỉ là trần *khi điều kiện tăng workers xảy ra*. Không gọi `Integer.MAX_VALUE` là bộ nhớ vô hạn: mỗi task vẫn chiếm heap và có thể dẫn đến cạn bộ nhớ/độ trễ trước khi queue đạt mức số học đó.

Cấu hình mặc định trực tiếp của `ThreadPoolTaskExecutor` trong Spring Framework 6.x là core=1, max=`Integer.MAX_VALUE`, queueCapacity=`Integer.MAX_VALUE`. Điều này thường hoạt động gần như một worker xử lý, các task bổ sung đi vào queue. **Không nhầm mặc định của class này với các mặc định/auto-configuration theo phiên bản của Spring Boot.**

### 5.3. `queueCapacity=0`: bàn giao trực tiếp, không giữ task chờ

Trong Spring:

~~~java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(8);
executor.setQueueCapacity(0);
~~~

`ThreadPoolTaskExecutor` chọn `SynchronousQueue`. Đây không phải queue cho phép tích trữ 0 hoặc nhiều task theo cách `LinkedBlockingQueue`; nó là **điểm bàn giao trực tiếp**: thao tác `offer` thành công khi có một worker sẵn sàng nhận ngay.

Nếu các workers đang bận và không có worker nhận bàn giao, executor thử tăng pool từ core lên max. Nếu đã tới max mà vẫn không thể bàn giao, task bị từ chối. Cấu hình này giúp không tích backlog trong executor queue nhưng có thể làm số worker tăng nhanh khi workload tăng đột ngột; **phải giới hạn max và cân nhắc downstream**.

### 5.4. Fixed-size pool và core=0

- Nếu `corePoolSize == maxPoolSize`, pool không có vùng mở rộng bổ sung sau core; khi tất cả workers bận, task phải đợi trong queue hoặc bị từ chối.
- Nếu `corePoolSize=0` và `maxPoolSize>0`, pool không có core workers. Cơ chế queue và việc bảo đảm có worker khi queue chứa task có thể khiến đường đi khác với ví dụ core dương; không suy diễn “không có worker nào sẽ chạy”.
- `allowCoreThreadTimeOut(true)` có thể làm core workers cũng được thu hồi sau thời gian nhàn rỗi. `prestartAllCoreThreads(true)` làm pool tạo trước core workers; hai cấu hình này ảnh hưởng trạng thái worker theo thời gian nhưng không đảo nguyên tắc ưu tiên core/queue/max lúc tiếp nhận.

| Chiến lược | Ưu điểm | Rủi ro cần cân nhắc |
| --- | --- | --- |
| Bounded queue | Hữu hạn backlog, tạo cơ hội mở rộng pool sau khi queue đầy, giúp thể hiện quá tải | Queue quá lớn gây đợi lâu; quá nhỏ gây reject sớm |
| Gần như unbounded queue | Hấp thụ burst mà không reject sớm | Dễ tích tụ task, tăng latency/bộ nhớ; max ít được dùng |
| SynchronousQueue | Bàn giao ngay, không giữ backlog ở executor queue | Worker tăng nhanh; reject khi không thể nhận; phụ thuộc nặng vào downstream |
| Fixed-size pool | Giới hạn concurrency dễ hiểu | Khi tasks chặn lâu, queue và thời gian chờ có thể tăng |

## 6. Spring Framework: cấu hình ThreadPoolTaskExecutor đúng ý đồ

`ThreadPoolTaskExecutor` là bean Spring quản lý `ThreadPoolExecutor`. Khi ứng dụng dùng `@Async`, có thể định tuyến tác vụ đến bean đã cấu hình rõ ràng, tránh dựa vào executor mặc định.

Ví dụ **mã tích hợp trong Spring Framework 6.x / Spring Boot 3.x với Java 17+**, cần dependencies Spring và component scanning; không phải tệp chạy trực tiếp bằng `javac` đơn lẻ.

Tệp `ConcurrencyConfig.java`:

~~~java
package example.concurrency;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ConcurrencyConfig {
    @Bean("boundedExecutor")
    public ThreadPoolTaskExecutor boundedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("bounded-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
~~~

Tệp `ReportService.java`:

~~~java
package example.concurrency;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    @Async("boundedExecutor")
    public CompletableFuture<String> export(String reportId) {
        // Minh họa; I/O thật cần timeout và xử lý lỗi phù hợp.
        return CompletableFuture.completedFuture("exported:" + reportId);
    }
}
~~~

Khi một bean khác gọi `reportService.export("R1")` **qua Spring proxy**, Spring gửi công việc đến `boundedExecutor`. Nếu các worker và queue chưa đạt giới hạn, task được nhận theo thứ tự core → queue → tăng tới max. Nếu đã bão hòa, lời submit có thể gây `TaskRejectedException` theo API Spring, kể cả khi method đã khai báo trả về `CompletableFuture`. Cần xử lý cả **lỗi submit ngay tại caller** lẫn **lỗi phát sinh trong lúc công việc chạy**.

Lưu ý:

- `@EnableAsync` là điều kiện bật xử lý annotation; gọi `this.export(...)` từ chính đối tượng trong chế độ proxy mặc định sẽ không kích hoạt `@Async` theo cách mong đợi.
- `@Async` không xóa giới hạn pool, queue, connection pool hay CPU. Nó chỉ chuyển công việc sang cơ chế thực thi đã chọn.
- Những con số 2/4/10 chỉ để minh họa thứ tự; chọn giá trị theo đo đạc workload, không đưa nguyên vào sản phẩm nếu chưa xác nhận năng lực hệ thống.
- Trong lifecycle của Spring, bean executor được Spring quản lý để khởi tạo và đóng. Nếu tự tạo Java executor trong nghiệp vụ, phải quản lý shutdown và task còn chờ.
- Khi một tác vụ trong executor lại gửi tác vụ con **cùng executor** rồi chờ bằng `Future.get()`/`join()`, có thể tạo **thread-pool starvation**: workers đều chờ trong khi tasks con bị kẹt ở queue. Tăng max chỉ có thể che triệu chứng trong một tình huống, không sửa phụ thuộc vòng.

### 6.1. “Tôi đã tăng max lên 50 mà active chỉ có 2”

Kiểm tra trước `queueCapacity`, queue depth, `corePoolSize`, `getPoolSize()` và `getActiveCount()`. Nếu queue vẫn nhận task, theo thuật toán pool **ưu tiên enqueue**, nên max có thể chưa phát huy tác dụng. Phải nhìn cả tốc độ đến, thời gian chạy, queue wait và năng lực downstream.

Cũng cần phân biệt:

- `getPoolSize()`: số worker hiện đang tồn tại.
- `getActiveCount()`: ước lượng số worker đang thực thi task.
- `getQueueSize()`: số task đang chờ trong queue (trên các phiên bản Spring hỗ trợ).
- `getCorePoolSize()` và `getMaxPoolSize()`: giới hạn cấu hình, **không phải số worker đang có**.

Đừng dựa vào một snapshot để kết luận pool “lỗi”; cần quan sát biến thiên theo thời gian, throughput, p95/p99 và rejection.

## 7. Chính sách từ chối: bước cuối có nhiều cách xử lý

Trong Java `ThreadPoolExecutor` có bốn policy phổ biến. Spring cho phép cấu hình `RejectedExecutionHandler` tương ứng:

| Policy | Khi quá tải | Ý nghĩa vận hành |
| --- | --- | --- |
| `AbortPolicy` | Ném `RejectedExecutionException` (trong Spring có thể được biểu diễn là `TaskRejectedException`) | Dễ quan sát quá tải và trả phản hồi cho caller |
| `CallerRunsPolicy` | Nếu pool chưa shutdown, **thread gọi `execute` tự chạy task** | Tạo backpressure tự nhiên, nhưng caller bị chặn và độ trễ caller tăng |
| `DiscardPolicy` | Bỏ task mà không báo lỗi qua chính policy | Rất nguy hiểm khi nghiệp vụ cần đảm bảo xử lý; task có thể mất im lặng |
| `DiscardOldestPolicy` | Bỏ task đầu queue rồi thử nhận task mới nếu pool chưa shutdown | Có thể làm mất tác vụ chờ lâu nhất, phá kỳ vọng FIFO và làm các Future chờ không hoàn tất |

**Không mặc nhiên coi CallerRunsPolicy là “async an toàn”.** Nếu caller là HTTP request thread, nó có thể bị buộc chạy cả tác vụ nền. Nếu caller là Reactor event-loop, có thể chặn event loop và ảnh hưởng nhiều request. Với task có side effect hoặc đòi hỏi bảo đảm delivery, cần xác định rõ lúc nào được drop, retry, từ chối hay lưu vào queue bền vững.

Nếu yêu cầu tính đúng đắn quan trọng, `AbortPolicy` cộng với xử lý lỗi submit có thể dễ giám sát hơn chính sách bỏ việc. Đổi policy **không làm tăng năng lực thực sự** của CPU, database hay dịch vụ ngoài.

### Phản hồi khi executor không nhận thêm task

Tùy kiến trúc ứng dụng, có thể:

1. Trả lỗi/response quá tải phù hợp ở biên request thay vì treo chờ vô hạn.
2. Giới hạn số task đang xử lý (*bulkhead* / semaphore) trước khi submit.
3. Dùng timeout và cancellation có kiểm soát; `Future.get(timeout)` chỉ dừng thời gian caller chờ, không tự bảo đảm task nền đã dừng.
4. Nếu nghiệp vụ bắt buộc xử lý sau, ghi công việc vào **hàng đợi bền vững** có ACK/retry/idempotency thay vì chỉ tăng queue in-memory của executor.
5. Cân nhắc retry có backoff và jitter ở nơi được phép, tránh gửi ồ ạt khi downstream vừa suy yếu.

## 8. Vì sao thứ tự này quan trọng đối với độ trễ và tài nguyên?

### 8.1. Queue lớn chưa chắc là điều tốt

Queue giảm nguy cơ rejection khi có burst ngắn, nhưng khi tốc độ gửi task lớn hơn tốc độ hoàn thành trong thời gian dài, backlog tăng và **độ trễ queue** tăng. Tác vụ “được nhận” chưa chắc còn giá trị khi nó bắt đầu chạy: request từ lâu đã timeout, người dùng đã hủy thao tác hoặc dữ liệu nghiệp vụ đã đổi.

Không điều chỉnh queue chỉ để làm rejection về 0. Một hệ thống báo “đã nhận” nhưng giữ task hàng phút có thể tệ hơn hệ thống từ chối sớm và rõ ràng.

### 8.2. Max threads không phải throughput

Giả sử mỗi task dùng một database connection trong 200 ms và pool DB chỉ có 10 connection. Trong điều kiện lý tưởng, giới hạn bước DB có thể xấp xỉ 10 / 0,2 = **50 lần/giây** nếu các điều kiện khác không phải nút thắt. Nâng `maxPoolSize` từ 16 lên 160 không tạo thêm connection; nhiều worker mới có thể chỉ chờ DB. Phép tính là **minh họa giới hạn lý tưởng**, không phải đo throughput ứng dụng thật.

CPU-bound thường giới hạn ở số lõi CPU hữu dụng; I/O-bound có thể hưởng lợi từ concurrency cao hơn, nhưng phải kiểm tra timeout, connection limits, memory và downstream. Tăng thread quá mức cũng làm tăng chi phí lập lịch, stack/native memory, tranh chấp lock và context switching.

### 8.3. Thứ tự tiếp nhận không bảo đảm FIFO hoàn tất

Ngay cả khi queue là FIFO, task mới có thể được worker non-core xử lý ngay khi queue đầy, trong khi task cũ vẫn chờ. Nhiều workers xử lý song song có thời gian chạy khác nhau. Vì vậy:

- Không dựa vào thứ tự submit để bảo đảm thứ tự cập nhật trạng thái.
- Nếu nghiệp vụ cần ordering theo khóa (ví dụ cùng một customerId), thiết kế xử lý tuần tự theo key, partition hoặc trạng thái có kiểm soát.
- Nếu cần chính xác một lần xử lý theo nghiệp vụ, executor in-memory tự nó không cung cấp durability hoặc exactly-once.

## 9. Checklist chẩn đoán khi pool bão hòa

Khi gặp timeout, queue tăng hoặc bị reject, thu thập **đồng thời**:

| Câu hỏi | Bằng chứng |
| --- | --- |
| Bao nhiêu worker tồn tại/đang bận? | `poolSize`, `activeCount`, thread dump |
| Task đang nằm ở đâu? | Số task chờ, tuổi task trong queue, thời gian xử lý |
| Vì sao pool không tăng? | Core/max/queueCapacity, queue có đầy không, policy có giới hạn gì |
| Vì sao worker bận quá lâu? | Stack: CPU, chờ lock, HTTP, DB, `Future.get()` |
| Vì sao task bị từ chối? | Executor saturation, shutdown, rejection count, log/trace |
| Tăng luồng có giúp không? | Đo throughput, p95/p99, CPU, DB connections và backend latency trước/sau |

Thực hiện phân tích theo nguyên nhân:

1. Xác nhận chính xác bean executor mà request/`@Async` đang dùng; tránh đo nhầm pool.
2. Kiểm tra giá trị `corePoolSize`, `maxPoolSize`, `queueCapacity` và rejection policy **đang có hiệu lực**.
3. Xem worker stack và thời gian task chờ queue. Nếu workers đều chặn I/O/DB, tìm giới hạn downstream trước.
4. Phân biệt pool đã shutdown với pool đang saturation; cả hai có thể làm task bị reject nhưng cách khắc phục khác nhau.
5. Đặt concurrency budget, queue bound, timeout và phương án quá tải; kiểm chứng bằng thử tải tăng dần và tải kéo dài.
6. Sau mỗi thay đổi, so sánh completion rate, p95/p99, timeout, rejection, số connection và tài nguyên.

Một số lỗi thường gặp:

- **Hiểu sai:** “Đặt max=100 là mọi lúc sẽ có tối đa 100 task được chạy ngay.” **Đúng:** sau core, queue thường được ưu tiên; max chỉ là trần khi pool phải mở rộng.
- **Hiểu sai:** “Queue capacity=0 nghĩa là executor luôn từ chối.” **Đúng:** `SynchronousQueue` có thể bàn giao cho worker đang chờ hoặc làm pool mở rộng đến max.
- **Hiểu sai:** “Tác vụ bị reject chắc chắn đã chạy một phần.” **Đúng:** `AbortPolicy` từ chối task trước khi nó được thực thi theo lời submit đó.
- **Hiểu sai:** “Không có rejection nghĩa là hệ thống khỏe.” **Đúng:** queue lớn có thể che giấu backlog và đẩy lỗi thành timeout/memory pressure.
- **Hiểu sai:** “Shutdown chỉ là giải phóng worker.” **Đúng:** shutdown thay đổi việc chấp nhận task mới; những lời submit mới có thể bị từ chối kể cả còn capacity.

## 10. Bài tập suy luận và đáp án

**Câu 1.** Cấu hình core=3, max=8, queueCapacity=100. Có 3 worker đang bận; tác vụ thứ tư thường được tiếp nhận ở đâu?

**Đáp án:** vào queue, vì đã đạt core và queue vẫn còn chỗ. Không tự tăng ngay lên 4 workers.

**Câu 2.** Cấu hình core=2, max=4, queueCapacity=2. Bốn task đang chạy hoặc chờ như bảng ở mục 3; task thứ năm được tiếp nhận thế nào?

**Đáp án:** sau task 1–2 tạo core và task 3–4 vào queue, task 5 làm queue offer thất bại nên pool thử tạo worker #3 (chưa đạt max).

**Câu 3.** Nếu task thứ bảy bị từ chối với cấu hình ở câu 2, có thể kết luận luôn rằng pool đã shutdown?

**Đáp án:** không. Với 4 workers bận và 2 task queue, pool đang bão hòa cũng dẫn tới rejection. Cần kiểm tra trạng thái và metrics.

**Câu 4.** Vì sao queueCapacity=`Integer.MAX_VALUE` làm maxPoolSize thường không phát huy tác dụng?

**Đáp án:** sau core, queue gần như luôn nhận được task, nên nhánh “queue không thể nhận → tạo non-core worker” hiếm khi xảy ra.

**Câu 5.** `CallerRunsPolicy` giúp giảm số task bị bỏ như thế nào và đổi lại điều gì?

**Đáp án:** caller tự thực thi task nếu pool còn chạy, khiến tốc độ submit giảm. Đổi lại caller bị block và có thể làm tăng latency hoặc chặn request/event-loop thread.

**Câu 6.** Nếu tăng `maxPoolSize` mà throughput không đổi, cần kiểm tra điều gì?

**Đáp án:** có thể queue chưa đầy nên max chưa sử dụng; hoặc nút thắt là DB/HTTP connections, CPU, lock hay backend. So sánh queue wait, active/pool size và latency downstream.

**Câu 7.** Task thứ ba được worker mới chạy trước task thứ hai đang xếp queue có vi phạm FIFO không?

**Đáp án:** không. Queue nội bộ có thể FIFO cho những task đã **nằm trong queue**, nhưng task thứ ba được giao trực tiếp cho non-core worker nên không phải chờ sau task thứ hai.

## 11. Tài liệu tham khảo

- Oracle Java SE 17 — `ThreadPoolExecutor` (core size, queueing, rejection): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Oracle Java SE 17 — `BlockingQueue`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html
- Oracle Java SE 17 — `SynchronousQueue`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SynchronousQueue.html
- Spring Framework 6.x — `ThreadPoolTaskExecutor` API: https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-005">Vì sao hàng đợi không giới hạn làm maxPoolSize khó phát huy tác dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao hàng đợi không giới hạn làm maxPoolSize khó phát huy tác dụng

> **Module:** Spring Framework – concurrency · **Chủ đề cha:** Nhóm luồng  
> **Câu hỏi chính:** Vì sao cấu hình `corePoolSize = 2`, `maxPoolSize = 16` nhưng một `ThreadPoolTaskExecutor` dùng hàng đợi rất lớn lại thường chỉ chạy khoảng hai worker, trong khi hàng nghìn công việc chờ trong queue?

## 1. Câu trả lời cốt lõi

Với `ThreadPoolTaskExecutor` thông thường của Spring, cơ chế điều phối bên dưới là `java.util.concurrent.ThreadPoolExecutor`. Khi số worker đã đạt `corePoolSize`, executor **ưu tiên đưa task mới vào hàng đợi trước khi tạo thêm worker vượt mức core**. Chỉ khi việc đưa vào hàng đợi **không thành công** và tổng số worker chưa đạt `maximumPoolSize`, nó mới thử tạo worker bổ sung.

Do đó, với hàng đợi **không giới hạn trên thực tế**, thao tác thêm task vào queue hầu như luôn thành công. Điều kiện kích hoạt tăng từ `corePoolSize` lên `maxPoolSize` gần như không xuất hiện. Kết quả thường là ít worker đang xử lý, nhiều task chờ, độ trễ cao và bộ nhớ tăng. Việc đặt `maxPoolSize` rất lớn **không tự khiến pool tăng đến mức đó**.

Đây là quy tắc của **ThreadPoolExecutor và các adapter dùng nó**, không phải quy luật của mọi implementation `TaskExecutor`. Chẳng hạn, `SimpleAsyncTaskExecutor` không vận hành bằng cùng cơ chế thread pool/queue.

## 2. Ba thông số quyết định hành vi

| Thông số | Vai trò | Điều nó không có nghĩa |
| --- | --- | --- |
| `corePoolSize` | Số worker cơ bản mà executor ưu tiên tạo khi tiếp nhận công việc; mặc định worker core thường được giữ lại kể cả lúc nhàn rỗi | Không phải giới hạn tuyệt đối của tổng worker |
| `maxPoolSize` | Trần số worker mà executor có thể tạo khi cần | Không phải mục tiêu số worker mà executor tự cố đạt |
| `queueCapacity` | Số task có thể chờ trong queue, quyết định khi nào lời gọi `offer` thất bại và executor thử mở rộng worker | Không phải số task chạy đồng thời; task đang chạy thường không nằm trong queue |

`poolSize` là số worker hiện có, `activeCount` là số worker đang thực hiện task (ước lượng tức thời), còn `queueSize` là số task còn đang chờ. Đọc riêng `poolSize` mà không nhìn `queueSize` dễ dẫn đến kết luận sai về mức tải.

Một queue dung lượng 100 không đồng nghĩa với hệ thống chạy 100 task song song: nếu core chỉ có 2 và queue chưa đầy, thường chỉ 2 task được chạy, các task khác chờ.

## 3. Vì sao nhánh mở rộng thường không được kích hoạt?

`ThreadPoolExecutor.execute` ưu tiên tạo worker tới `corePoolSize`; tiếp theo thử `workQueue.offer(task)`; chỉ khi offer không thành công mới cố tạo worker tới `maxPoolSize`, rồi từ chối khi không thể nhận thêm. Implementation thật còn có các bước kiểm tra shutdown và cạnh tranh đồng thời.

**Nút thắt của bài này chính là điều kiện offer thất bại.** Với `LinkedBlockingQueue(Integer.MAX_VALUE)`, offer có thể thành công cực nhiều lần, nên nhánh mở rộng worker gần như không được thực hiện. Bài “Thứ tự nhận tác vụ: tạo luồng cơ bản, xếp hàng, mở rộng rồi từ chối” trình bày thuật toán chi tiết; ở đây ta đánh giá hệ quả **queue gần vô hạn** qua backlog, latency và khả năng mở tới max.

## 4. Vì sao hàng đợi rất lớn giữ pool gần corePoolSize?

Giả sử:

- `corePoolSize = 2`;
- `maxPoolSize = 16`;
- queue dạng `LinkedBlockingQueue` dung lượng cực lớn;
- các task là công việc chậm, cả hai worker ban đầu đều bận.

Khi task thứ ba đến, pool có 2 worker nên thử đưa vào queue. Thao tác này thành công. Task thứ tư, thứ năm và hàng trăm task sau cũng được enqueue vì queue vẫn còn chỗ. Pool không có lý do đi đến nhánh tạo worker thứ ba, thứ tư… thứ mười sáu.

~~~text
core = 2, max = 16, queue gần như không giới hạn

Worker #1: [TASK A đang chạy]
Worker #2: [TASK B đang chạy]
Queue:     [C][D][E][F][G][H][... nhiều task nữa ...]
Workers #3 ... #16: chưa được tạo

task mới -> queue.offer(...) thành công -> vẫn chỉ hai worker
~~~

Nếu task đến trung bình nhanh hơn hai worker hoàn thành, lượng chờ sẽ tích lũy. Tăng `maxPoolSize` từ 16 lên 64 **không trực tiếp thay đổi nhánh quyết định**: chừng nào `offer` còn thành công thì pool vẫn ưu tiên queue.

Với **queue thật sự không giới hạn** của mô hình JDK, Javadoc chỉ rõ worker thường không vượt `corePoolSize`; `maximumPoolSize` do đó không phát huy tác dụng. Với queue mặc định của Spring có giới hạn kỹ thuật `Integer.MAX_VALUE`, về mặt lý thuyết có thể đầy; nhưng thực tế nguy cơ thiếu bộ nhớ/tăng độ trễ thường đến rất lâu trước lúc chạm giới hạn số phần tử này. Vì vậy tài liệu và người vận hành thường gọi nó là “hàng đợi không giới hạn”.

## 5. Cấu hình mặc định của ThreadPoolTaskExecutor trong Spring

Với bản thân class `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor`, các giá trị mặc định tiêu biểu là:

| Property | Giá trị mặc định của class | Tác động |
| --- | --- | --- |
| `corePoolSize` | `1` | Một worker cơ bản |
| `maxPoolSize` | `Integer.MAX_VALUE` | Trần rất cao trên lý thuyết |
| `queueCapacity` | `Integer.MAX_VALUE` | Queue dương có khả năng chứa rất nhiều task |
| `keepAliveSeconds` | `60` | Thời gian giữ worker vượt core khi nhàn rỗi (nếu có) |
| `allowCoreThreadTimeOut` | `false` | Worker core thường không timeout |

`ThreadPoolTaskExecutor` tạo `LinkedBlockingQueue` khi `queueCapacity > 0`, còn `queueCapacity <= 0` dùng `SynchronousQueue` (giao task trực tiếp, không chứa backlog). Đây là các mặc định/nhánh tạo queue của **class Spring**, không phải lời khẳng định rằng mọi ứng dụng **Spring Boot** luôn dùng đúng các số này: cấu hình tự động của Boot, version, properties và bean executor tùy chỉnh có thể thay đổi chúng.

Hệ quả dễ bất ngờ: mặc dù `maxPoolSize` mặc định cực lớn, instance `ThreadPoolTaskExecutor` dùng toàn bộ giá trị mặc định thường có hành vi gần một worker xử lý từng task, với queue rất dài.

## 6. So sánh cùng một tải với ba loại queue

Giả sử `core = 2`, `max = 6`, có **sáu task** được gửi liên tiếp; tất cả đều chặn trên một latch nên chưa task nào hoàn tất lúc đo.

| Queue | Hai task đầu | Task 3 và 4 | Task 5 và 6 | Kết quả lúc đo |
| --- | --- | --- | --- | --- |
| `LinkedBlockingQueue(Integer.MAX_VALUE)` | Tạo 2 worker | Chờ trong queue | Tiếp tục chờ | 2 worker; 4 task chờ |
| `LinkedBlockingQueue(2)` | Tạo 2 worker | Lấp queue 2 vị trí | Queue đầy → tạo thêm 2 worker | 4 worker; 2 task chờ |
| `SynchronousQueue` | Tạo 2 worker | Không thể giữ trong queue → tạo thêm 2 worker | Tạo thêm 2 worker nữa | 6 worker; 0 task chờ |

Nhận xét quan trọng: cấu hình **queue có giới hạn** tạo cơ hội để `maxPoolSize` phát huy tác dụng, nhưng cũng có thể dẫn tới reject khi cả queue và pool đầy. **Queue bằng 0** khiến executor mở rộng sớm hơn, đồng thời có thể nhanh chạm giới hạn worker. Không có cấu hình nào tự động phù hợp mọi loại tải.

Một hệ quả ít được chú ý: với queue hữu hạn, task đến sau lúc queue đã đầy có thể được giao trực tiếp cho worker vừa tạo, trong khi task cũ vẫn chờ trong queue. **Không được dựa vào thứ tự nộp là thứ tự bắt đầu hoặc hoàn thành**, trừ khi có cơ chế bảo đảm thứ tự riêng.

## 7. Ví dụ Java 17+ độc lập: chứng minh bằng số đo

Ví dụ sau dùng trực tiếp JDK `ThreadPoolExecutor`, là nền tảng cho cơ chế đang giải thích. Nó **không cần dependency Spring**, do đó chạy độc lập để kiểm chứng đúng chính sách worker–queue trước khi đặt vào ứng dụng Spring.

Lưu vào tệp **QueueGrowthDemo.java**:

~~~java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class QueueGrowthDemo {
    static void demonstrate(String label, BlockingQueue<Runnable> queue,
                            int expectedRunning) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            2, 6, 30, TimeUnit.SECONDS, queue);
        CountDownLatch started = new CountDownLatch(expectedRunning);
        CountDownLatch release = new CountDownLatch(1);

        try {
            for (int i = 0; i < 6; i++) {
                pool.execute(() -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            if (!started.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers did not start");
            }

            System.out.printf("%s: pool=%d, queue=%d%n",
                label, pool.getPoolSize(), pool.getQueue().size());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        demonstrate("Almost unbounded",
            new LinkedBlockingQueue<>(Integer.MAX_VALUE), 2);
        demonstrate("Bounded 2",
            new LinkedBlockingQueue<>(2), 4);
        demonstrate("Direct handoff",
            new SynchronousQueue<>(), 6);
    }
}
~~~

Chạy với JDK 17 trở lên:

~~~bash
javac QueueGrowthDemo.java
java QueueGrowthDemo
~~~

**Stdout khi các worker khởi chạy trong thời hạn 10 giây**:

~~~text
Almost unbounded: pool=2, queue=4
Bounded 2: pool=4, queue=2
Direct handoff: pool=6, queue=0
~~~

Giải thích:

- Cả ba lần chạy đều dùng `core=2`, `max=6`, sáu task và cùng chức năng task.
- `CountDownLatch release` giữ task đang chạy chưa hoàn thành, tránh kết quả bị nhiễu vì worker kết thúc quá nhanh.
- `started` xác nhận đủ số worker dự kiến đã bắt đầu trước khi đo. Vì không có task nào vượt qua `release.await()`, số worker/queued tại thời điểm in ổn định theo từng cấu hình.
- Khi kết thúc, `release.countDown()` giải phóng công việc, `shutdown()` và `awaitTermination()` dọn tài nguyên; nếu worker không thoát trong thời hạn, mới gọi `shutdownNow()`.
- Ví dụ mô phỏng **cách nhận và xếp hàng**, chưa đo throughput của một hệ thống có DB, network hoặc giới hạn downstream. Không suy ra rằng “nhiều worker hơn” luôn tốt hơn.

## 8. Cấu hình tương ứng trong Spring Framework

Dưới đây là **đoạn tích hợp Spring**, phải đặt trong dự án có `spring-context` và được ApplicationContext nạp; không phải tệp Java chạy độc lập chỉ bằng `javac`.

~~~java
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class PoolConfiguration {
    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(2);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("report-");
        executor.setRejectedExecutionHandler(
            new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
~~~

Với `queueCapacity=2`, khi hai worker core bận và hai task khác đã chiếm hết queue, các task mới mới có thể khiến pool tăng lên ba, bốn… tối đa sáu worker. Khi queue và sáu worker đều đầy, `AbortPolicy` từ chối task.

Nếu đổi duy nhất sang `setQueueCapacity(Integer.MAX_VALUE)`, các task thường tiếp tục vào queue; việc tăng từ hai lên sáu worker hiếm xảy ra. Nếu đặt `setQueueCapacity(0)`, Spring dùng `SynchronousQueue`: không còn hàng chờ nội bộ, worker mới có thể được tạo sớm hơn đến giới hạn max, rồi task bị từ chối khi không còn khả năng nhận.

**Lưu ý:** hãy đối chiếu executor **thực sự** được `@Async` hoặc service lựa chọn. Nếu nhiều bean `TaskExecutor` tồn tại, cần chỉ rõ qualifier/tên executor thích hợp. Ngoài ra, với proxy mode thông thường, `this.methodAnnotatedAsync()` trong cùng một bean không đi qua `@Async` interceptor. Bài toán hàng đợi chỉ có ý nghĩa khi lời gọi thực sự được nộp đến pool đang xem xét.

### Không chỉ thay maxPoolSize

Cấu hình này chưa đủ để kỳ vọng pool mở rộng:

~~~java
executor.setCorePoolSize(2);
executor.setMaxPoolSize(64);
executor.setQueueCapacity(Integer.MAX_VALUE);
~~~

Thay `maxPoolSize` từ 6 thành 64 không giải quyết nút thắt nếu queue vẫn chấp nhận mọi task. Cần điều chỉnh **tương quan** giữa core, max và queue theo mục tiêu latency, sức chứa thực và khả năng downstream.

## 9. Không nên suy ra queue càng nhỏ thì càng tốt

Ba chiến lược phổ biến có đánh đổi khác nhau:

| Chiến lược | Ưu điểm | Rủi ro |
| --- | --- | --- |
| Queue rất dài | Hấp thụ burst ngắn mà ít tạo thêm worker; giảm biến động tạo thread | Độ trễ chờ tăng, giữ reference/task trong RAM, dễ che giấu tình trạng quá tải, max không được dùng |
| Queue hữu hạn | Giới hạn backlog; pool có thể mở rộng khi queue đầy; overload rõ ràng | Phải chọn rejection/backpressure; số worker lớn có thể làm cạn connection pool và tăng contention |
| `queueCapacity = 0` | Giao task trực tiếp; pool tăng worker nhanh khi toàn bộ worker bận | Dễ tăng thread mạnh, chạm max và reject sớm; không có bộ đệm cho burst |

Điều chỉnh `corePoolSize` lên mức phù hợp có thể giúp số worker hiện diện sớm hơn ngay cả khi queue dài. Nhưng cũng cần tính **CPU**, memory, blocking I/O, số connection DB, limit của API bên ngoài, timeout và khả năng tranh chấp. Một pool với 64 worker nhưng DB chỉ có 10 connection có thể tạo thêm rất nhiều worker chờ connection thay vì xử lý nhanh hơn.

`allowCoreThreadTimeOut(true)` cho phép core worker hết hạn khi nhàn rỗi; `keepAliveSeconds` liên quan thời gian sống của worker thừa. Hai tham số này **không đổi thứ tự ưu tiên queue trước mở rộng vượt core**, nên không phải lời giải trực tiếp cho hiện tượng max không được dùng. `prestartAllCoreThreads` chỉ tạo sẵn các worker core, không tự tăng đến max.

## 10. Khi queue đầy: từ chối là một phần thiết kế

Pool có giới hạn sẽ có lúc đạt công suất tối đa. Nếu mọi worker bận và queue đầy, việc nộp task mới đi tới `RejectedExecutionHandler`. Với `ThreadPoolTaskExecutor`, lời gọi thông qua Spring có thể thể hiện việc từ chối bằng `TaskRejectedException` (một dạng `RejectedExecutionException`).

Cần quyết định ở tầng gọi:

- **Abort/reject rõ ràng:** trả thông báo quá tải hoặc lỗi thích hợp; không âm thầm giả vờ đã nhận task.
- **CallerRunsPolicy:** trong điều kiện executor đang chạy, task có thể chạy ngay trên caller thread, khiến caller chậm lại và tạo backpressure; phải chấp nhận tác động đến request latency. Khi executor shutdown, chính sách này có hành vi riêng và không bảo đảm nhận việc.
- **Retry có giới hạn:** chỉ khi tác vụ idempotent và có backoff/jitter; retry liên tục ngay vào pool đang đầy có thể làm tình hình xấu hơn.
- **Durable queue/job store:** nếu không được phép mất nhiệm vụ khi JVM chết, phải dùng cơ chế lưu công việc ngoài memory và xử lý phục hồi. Queue của executor không phải kho bền vững.

Không sử dụng `DiscardPolicy`/biến thể bỏ task một cách im lặng cho nghiệp vụ cần xác nhận hoàn tất, trừ khi chính sách bỏ việc được định nghĩa rất rõ.

## 11. Cách chẩn đoán trong một ứng dụng chạy thật

Hãy đo đồng thời những đại lượng sau, tốt nhất khi có tải tăng dần:

1. **Pool:** `corePoolSize`, `maxPoolSize`, `poolSize`, `activeCount` và peak pool size.
2. **Queue:** capacity, số phần tử đang chờ, thời gian chờ từ lúc `submit/execute` đến khi bắt đầu chạy; với queue gần như không giới hạn, số pending có thể tăng rất lâu.
3. **Lỗi/overload:** số task bị từ chối, timeout, retry, cancellation và task đang dang dở lúc shutdown.
4. **Thời gian công việc:** tổng thời gian chạy, tỉ lệ I/O wait, thời gian transaction và mức sử dụng connection pool.
5. **Áp lực tài nguyên:** heap, số thread, CPU, GC, downstream rate limits và latency phía dịch vụ phụ thuộc.

Dấu hiệu điển hình của vấn đề đang hỏi:

~~~text
corePoolSize = 2
maxPoolSize = 32
poolSize = 2
activeCount ≈ 2
queueSize tăng liên tục
rejectedCount ≈ 0
p95/p99 thời gian hoàn tất tăng
~~~

Khi quan sát được mẫu này, cần kiểm tra `queueCapacity` và chính sách queue **trước khi chỉ tiếp tục tăng `maxPoolSize`**. Nếu task có thời gian chờ dài, kiểm tra luôn downstream; pool tăng thêm worker có thể chỉ làm số connection chờ tăng.

Một bài thử tải hợp lý phải xét thời điểm tăng tải, thời điểm hệ thống ổn định, burst ngắn, saturation kéo dài và giai đoạn giảm tải. Chọn cấu hình theo SLO (latency/throughput), bộ nhớ, số connection và tỷ lệ từ chối chấp nhận được.

## 12. Những hiểu nhầm phổ biến

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “`maxPoolSize=32` nghĩa là pool sẽ có 32 worker khi tải tăng.” | Pool chỉ tăng vượt core khi queue không nhận thêm và còn dưới max. |
| “Không thấy reject nghĩa là hệ thống còn rất khỏe.” | Queue lớn có thể nhận việc mãi dù thời gian chờ tăng không kiểm soát. |
| “Queue lớn làm task chạy song song nhiều hơn.” | Queue chứa task **chờ**; worker mới là nơi xử lý song song. |
| “Chỉ cần tăng maxPoolSize để chữa chậm.” | Nếu queue chưa đầy, đổi max thường không chạm nhánh tạo thêm worker. |
| “Đặt queue=0 luôn nhanh và an toàn hơn.” | Dễ mở rộng đến max, tạo contention và reject; cần hiểu tải thực. |
| “Đặt core=max nghĩa là không có queue.” | Pool fixed-size vẫn có queue nếu được cấu hình. |
| “Queue capacity mặc định là vô hạn về mặt toán học.” | Trong `ThreadPoolTaskExecutor` mặc định là `Integer.MAX_VALUE`, cực lớn nhưng vẫn là số hữu hạn. |
| “Executor bảo đảm FIFO completion.” | Queue có thể FIFO cho task đang chờ, nhưng nhiều worker và đường tạo worker bổ sung không bảo đảm thứ tự hoàn tất tổng thể. |
| “Công việc được enqueue sẽ tồn tại qua restart.” | In-memory executor queue không bền vững qua process crash. |

## 13. Quy trình chỉnh cấu hình có kiểm soát

1. **Xác nhận đúng executor:** kiểu bean, `@Async` qualifier, implementation thực tế, các property được áp dụng và vòng đời bean.
2. **Đo điểm nghẽn:** worker đang bận, queue wait, thời gian thực thi, CPU, DB connections, remote latency; tìm rõ là thiếu worker hay hệ thống phía sau bão hòa.
3. **Đặt mục tiêu:** mức song song tối đa được phép, thời gian chờ mong muốn, số job được giữ trong bộ nhớ, chính sách khi không thể nhận thêm.
4. **Chọn bộ ba:** core đáp ứng tải bình thường, max phù hợp tài nguyên, queue hữu hạn hấp thụ burst cho phép. Có thể chọn queue=0 khi cần direct handoff và đã thiết kế overload.
5. **Định nghĩa chính sách reject và recovery:** xác nhận task đã nhận, trả lỗi, retry/backoff, idempotency, durable jobs cho công việc quan trọng.
6. **Kiểm thử tăng tải và lỗi:** dùng latch trong unit test để khống chế bão hòa; tích hợp test với DB/API thật để phát hiện contention, timeout, deadlock và bottleneck.
7. **Theo dõi sau thay đổi:** đánh giá throughput, queue wait, p95/p99, rejection, memory và downstream trước khi kết luận tối ưu.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Với `core=2`, `max=20` và queue còn rất nhiều chỗ, 100 task dài hạn được nộp liên tiếp. Tại sao pool có thể vẫn chỉ khoảng hai worker?

Vì sau khi đạt core, task mới được enqueue thành công. Executor chưa cần chạy nhánh tạo worker bổ sung đến max. Các task còn lại chờ trong queue.

**Câu 2.** Với `core=2`, `max=6`, `queueCapacity=2`, sáu task chặn đồng thời thì dự kiến trạng thái là gì?

Hai task dùng worker core, hai task xếp hàng, hai task đến sau khiến mở rộng worker. Khi giữ nguyên bằng latch, có bốn worker và hai task đang chờ, như ví dụ Java.

**Câu 3.** Vì sao không thể đặt queue=0 rồi coi là giải quyết xong vấn đề?

Vì cấu hình này chuyển áp lực từ hàng đợi sang worker và rejection, vẫn bị giới hạn bởi CPU, số connection, downstream, lỗi và độ trễ. Đúng hay không phải được đánh giá qua tải thực và SLO.

**Kết luận:** Với `ThreadPoolExecutor`, nhánh **ưu tiên enqueue sau core** giải thích trực tiếp tại sao queue gần như không giới hạn khiến `maxPoolSize` khó phát huy tác dụng. Muốn pool có cơ hội tăng vượt core, cần một chính sách queue có điểm bão hòa; muốn hệ thống xử lý tốt, còn phải thiết kế giới hạn tải, độ trễ, từ chối và tài nguyên downstream.

## Tài liệu tham khảo chính thức

- [Java SE 17: ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html) — phần *Core and maximum pool sizes* và *Queuing*.
- [Spring Framework: ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html) — mặc định core/max/queue, đặc điểm tạo LinkedBlockingQueue và SynchronousQueue.
- [Spring Framework Reference: Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) — cấu hình executor, scheduler và tích hợp `@Async`.
- [Java SE 17: LinkedBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html) và [SynchronousQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SynchronousQueue.html) — bản chất hai loại queue.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-006">queueCapacity bằng 0 và chuyển giao trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# queueCapacity bằng 0 và chuyển giao trực tiếp trong ThreadPoolTaskExecutor

Trong Spring Framework, **ThreadPoolTaskExecutor** là một bộ thực thi có nhóm luồng (thread pool) để chạy các tác vụ ngoài luồng gọi. Khi cấu hình **`queueCapacity = 0`**, executor **không tạo hàng đợi có khả năng lưu tác vụ đang chờ**. Thay vào đó, nó dùng cơ chế **chuyển giao trực tiếp (direct handoff)** thông qua `SynchronousQueue` của Java: tác vụ được chuyển ngay tới một worker đang sẵn sàng, hoặc executor cố gắng tạo thêm worker trong giới hạn `maxPoolSize`. Nếu không thể làm cả hai, tác vụ bị **từ chối** theo chính sách rejection.

Đây là một lựa chọn quan trọng trong nhóm **Nhóm luồng** của **Spring Framework - concurrency**, bởi một thay đổi nhỏ ở `queueCapacity` có thể làm thay đổi hoàn toàn đường đi của tác vụ, cách pool mở rộng và hành vi khi quá tải.

Các ý cần nắm:

- **`queueCapacity = 0` không có nghĩa là executor không thể nhận task.** Nó có nghĩa là không có chỗ *tích trữ* task trong hàng đợi.
- **Direct handoff không đồng nghĩa với mỗi task một thread mới.** `ThreadPoolTaskExecutor` vẫn **tái sử dụng worker** khi worker đã rảnh và sẵn sàng nhận task.
- **`corePoolSize`, `maxPoolSize` và rejection policy quyết định khi nào tăng số worker, khi nào từ chối.**
- Hàng đợi bằng 0 có thể giúp phát hiện quá tải sớm, nhưng nếu để `maxPoolSize` quá lớn thì vẫn có thể sinh rất nhiều platform threads.

## 1. ThreadPoolTaskExecutor dùng hàng đợi để làm gì?

`ThreadPoolTaskExecutor` của Spring bọc một `java.util.concurrent.ThreadPoolExecutor`. Một pool thường có ba thành phần:

1. **Worker threads:** các luồng có thể xử lý hết task này rồi đến task khác.
2. **Task queue:** nơi giữ các task đã được chấp nhận nhưng chưa có worker thực thi.
3. **Saturation/rejection policy:** quyết định làm gì khi không thể nhận thêm việc.

Các cấu hình liên quan:

| Thuộc tính | Ý nghĩa |
| --- | --- |
| `corePoolSize` | Ngưỡng số worker cốt lõi mà pool ưu tiên tạo để xử lý task khi cần. |
| `maxPoolSize` | Giới hạn trên số worker pool có thể tạo. |
| `queueCapacity` | Khả năng lưu các tác vụ chờ; **0** chọn cơ chế không lưu task vào queue. |
| `keepAliveSeconds` | Khoảng thời gian worker vượt core được phép nhàn rỗi trước khi bị thu hồi (theo cấu hình thông thường). |
| `RejectedExecutionHandler` | Cách xử lý khi pool không tiếp nhận task. |
| `threadNamePrefix` | Tiền tố tên worker, giúp quan sát khi log/debug. |

Với `queueCapacity > 0`, Spring tạo hàng đợi kiểu **`LinkedBlockingQueue`** có dung lượng tương ứng. Với `queueCapacity <= 0`, Spring tạo **`SynchronousQueue`**. Trong bài này ta xét trường hợp được yêu cầu: **`queueCapacity = 0`**.

Cần hiểu đúng một điểm: **giới hạn `maxPoolSize` không phải chỉ số worker luôn có sẵn**. Pool có thể chưa tạo đủ số worker tối đa. Việc mở rộng phụ thuộc core, hàng đợi, số worker hiện tại và thời điểm gửi tác vụ.

## 2. SynchronousQueue: hàng đợi không lưu phần tử

Tên `SynchronousQueue` dễ làm người học nghĩ rằng đó là một queue bình thường với kích thước bằng 0. Thực tế, nó là **điểm trao đổi trực tiếp** giữa một bên đưa phần tử vào và một bên nhận phần tử ra.

~~~text
Hàng đợi có chỗ chứa (ví dụ LinkedBlockingQueue)
Producer -> [ task A | task B | task C ] -> Worker
                    task có thể nằm chờ

Chuyển giao trực tiếp (SynchronousQueue)
Producer -- task A ----------------------> Worker đang chờ nhận
              không có ngăn chứa ở giữa
~~~

Đặc tính của `SynchronousQueue`:

- **Không có dung lượng lưu trữ nội bộ**; một task không thể nằm trong queue để chờ worker xuất hiện sau đó.
- Một thao tác `offer(task)` không chờ sẽ thành công chỉ khi có bên nhận phù hợp đang chờ để nhận task.
- Phương thức `size()` luôn là **0**; điều đó không nói rằng executor không có task đang chạy.
- `put(task)` của chính `SynchronousQueue` có thể chờ bên nhận, nhưng **đừng suy ra rằng `ThreadPoolTaskExecutor.execute()` sẽ dùng `put()` để chặn caller đến khi worker rảnh**. Với `ThreadPoolExecutor`, đường nhận task dùng `offer()` rồi xét tạo worker hoặc từ chối.

Vì vậy, `queueCapacity=0` làm mất **khả năng đệm task**, chứ không làm mất **khả năng xử lý task**. Một worker vừa hoàn tất việc có thể quay lại chờ nhận việc tiếp theo; lúc đó tác vụ mới được handoff trực tiếp và **worker cũ được tái sử dụng**.

## 3. Thuật toán nhận tác vụ: core → handoff → max → reject

Có thể hình dung đường đi của `ThreadPoolExecutor.execute(task)` như sau (lược bỏ các chi tiết khóa nội bộ và kiểm tra trạng thái shutdown):

~~~text
                          execute(task)
                                |
               Số worker hiện tại < corePoolSize?
                       /                  \
                     Có                  Không
                      |                     |
             Tạo worker mới          Thử offer(task)
             chạy task ngay          vào hàng đợi
                                           |
                           +---------------+--------------+
                           |                              |
                       Thành công                      Thất bại
                           |                              |
                 Worker sẽ nhận task       Số worker < maxPoolSize?
                                            /                \
                                          Có                Không
                                           |                   |
                                  Tạo worker mới            REJECT
                                  chạy task ngay
~~~

Với **`queueCapacity=0`**, bước `offer()` là một lần thử **chuyển giao trực tiếp** cho worker chờ nhận, không phải ghi vào buffer. Nếu không có worker nào có thể nhận ngay, pool thử **tạo thêm worker**. Khi đã chạm `maxPoolSize` mà không handoff được, tác vụ bị từ chối.

Đường đi còn phụ thuộc trạng thái pool: executor đã shutdown thì không nên nhận task mới; sau khi `offer()` thành công, ThreadPoolExecutor cũng kiểm tra lại tình trạng pool để tránh giữ task khi đã ngừng nhận việc. Đây là lý do sơ đồ chỉ mô tả quy tắc chủ đạo.

**Ví dụ cấu hình:** `corePoolSize=2`, `maxPoolSize=4`, `queueCapacity=0`. Giả sử mọi task giữ worker bận cho tới khi test chủ động giải phóng:

| Lần gửi | Trạng thái trước khi gửi | Kết quả |
| --- | --- | --- |
| Task 1 | Chưa có worker | Tạo worker #1 (core). |
| Task 2 | Có 1 worker đang bận | Tạo worker #2 (core). |
| Task 3 | Hai core worker đều bận; không handoff được | Tạo worker #3. |
| Task 4 | Ba worker đều bận; không handoff được | Tạo worker #4. |
| Task 5 | Cả 4 worker đều bận; không có buffer | **Reject** theo chính sách mặc định. |

Nếu worker #2 đã xong task và **đang chờ nhận việc** lúc Task 5 được gửi, tác vụ đó có thể được handoff trực tiếp cho worker #2; **không nhất thiết tạo thread thứ năm** và không nhất thiết reject. Kết luận luôn phụ thuộc **trạng thái lúc submit**.

## 4. Ví dụ Java 17 chạy độc lập: pool 2–4 worker và queue bằng 0

Ta có thể kiểm chứng chính xác cơ chế nền của Spring bằng API chuẩn Java. Vì `ThreadPoolTaskExecutor` bọc `ThreadPoolExecutor`, ví dụ này phản ánh đường nhận task cốt lõi khi Spring chọn `SynchronousQueue`.

Lưu thành tệp **`DirectHandoffDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DirectHandoffDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2,
                4,
                30,
                TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new ThreadPoolExecutor.AbortPolicy()
        );

        CountDownLatch started = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();

        Runnable heldTask = () -> {
            active.incrementAndGet();
            started.countDown();

            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        };

        try {
            for (int i = 0; i < 4; i++) {
                pool.execute(heldTask);
            }

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Không đủ worker khởi động trong thời hạn test");
            }

            System.out.println("Tác vụ đang chạy: " + active.get());

            boolean rejected = false;
            try {
                pool.execute(() -> {
                    // Tác vụ thứ năm sẽ không được chấp nhận khi cả bốn bận.
                });
            } catch (RejectedExecutionException e) {
                rejected = true;
            }

            System.out.println("Tác vụ thứ năm bị từ chối: " + rejected);
            System.out.println("Số phần tử đang đợi trong queue: "
                    + pool.getQueue().size());
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

Biên dịch và chạy với **JDK 17+**:

~~~bash
javac -encoding UTF-8 DirectHandoffDemo.java
java DirectHandoffDemo
~~~

**Stdout** khi các worker khởi động thành công trong thời hạn test:

~~~text
Tác vụ đang chạy: 4
Tác vụ thứ năm bị từ chối: true
Số phần tử đang đợi trong queue: 0
~~~

Giải thích từng điểm:

1. **`CountDownLatch started`** buộc main chỉ kiểm tra sau khi cả bốn worker thực sự đã bắt đầu.
2. **`release`** giữ cả bốn worker bận. Vì vậy, không có worker đang chờ trong `SynchronousQueue` để nhận tác vụ thứ năm.
3. Pool đã đạt `maxPoolSize=4`, nên không thể tạo worker thứ năm; `AbortPolicy` ném `RejectedExecutionException`.
4. **`getQueue().size() == 0`** đúng bản chất của `SynchronousQueue`: không có phần tử lưu chờ. Nó **không phủ nhận** rằng bốn task đang thực thi.
5. `finally` luôn mở latch giải phóng worker và đóng pool. Dùng latch thay cho `Thread.sleep()` để tránh phụ thuộc lịch scheduler.

**Giới hạn của ví dụ:** đây là `java.util.concurrent.ThreadPoolExecutor` **thuần Java** để có thể chạy chỉ bằng `javac`/`java`. Trong ứng dụng Spring, `ThreadPoolTaskExecutor` sử dụng cùng cơ chế nền nhưng bao bọc API và ngoại lệ bằng lớp Spring. Phần tiếp theo cho thấy cấu hình Spring tương ứng.

## 5. Cấu hình ThreadPoolTaskExecutor trong Spring

Đây là một cấu hình mẫu cho ứng dụng có dependency **Spring Context**. Nó **không phải chương trình Java độc lập** chạy trực tiếp bằng `javac` khi không có Spring trong classpath.

**`DirectHandoffConfig.java`**:

~~~java
package example.concurrency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DirectHandoffConfig {
    @Bean("directHandoffExecutor")
    public ThreadPoolTaskExecutor directHandoffExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(0);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("direct-handoff-");

        return executor;
    }
}
~~~

Sau khi bean được khởi tạo, cấu hình cho phép pool bắt đầu từ **tối đa hai core workers**, mở rộng đến **bốn workers**, và không giữ task pending trong một queue có dung lượng dương. Những con số **2, 4, 30** chỉ để học; production phải đo tài nguyên và chọn giới hạn có chủ đích.

Có thể kiểm chứng cấu trúc queue bằng **đoạn mã Spring** sau (giả sử biến `executor` đã trỏ đến bean ở trên):

~~~java
import java.util.concurrent.SynchronousQueue;

boolean directHandoff =
        executor.getThreadPoolExecutor().getQueue()
                instanceof SynchronousQueue;

System.out.println(directHandoff); // true
~~~

Chỉ gọi `getThreadPoolExecutor()` khi `ThreadPoolTaskExecutor` đã được initialize. Đoạn này cho biết **kiểu queue** được cấu hình; nó không kiểm chứng riêng số task đã chạy hoặc hiệu suất.

### Vì sao phải đặt cả maxPoolSize?

Mặc định của `ThreadPoolTaskExecutor` là **core pool nhỏ, queue mặc định có dung lượng rất lớn, max pool rộng**. Nếu chỉ đổi `queueCapacity=0` mà giữ `maxPoolSize` mặc định rất lớn, executor có thể tạo **nhiều platform threads** trong đợt tải tăng. Không có buffer để hấp thụ burst, nhưng pool vẫn cố tăng worker cho tới khi vướng giới hạn.

Một cấu hình production cần giới hạn **`maxPoolSize` hữu hạn**, đồng thời lựa chọn rõ:

- Cho phép mức concurrent task tối đa bao nhiêu?
- Khi đầy thì trả lỗi ngay hay chạy task ở caller?
- Có cần chấp nhận task vào queue không, và queue được phép dài bao nhiêu?
- Một task chậm sẽ giữ worker và tài nguyên ngoài bao lâu?

**Không gán `maxPoolSize` bằng một số lớn chỉ để “tránh reject”.** Làm vậy có thể dời sự cố sang cạn thread, bộ nhớ hoặc kết nối downstream.

## 6. So sánh queueCapacity = 0 với queueCapacity > 0

Để thấy khác biệt, giữ **`corePoolSize=2`**, **`maxPoolSize=4`** và giả sử mỗi task đang chạy bị giữ cho đến khi ta chủ động thả:

| Lần gửi | `queueCapacity=0` | `queueCapacity=3` |
| --- | --- | --- |
| #1–#2 | Tạo hai core workers | Tạo hai core workers |
| #3 | Tạo worker #3 | Đặt vào queue |
| #4 | Tạo worker #4 | Đặt vào queue |
| #5 | **Reject** vì 4 worker đều bận | Đặt vào queue |
| #6 | — | Queue đã đầy, tạo worker #3 |
| #7 | — | Tạo worker #4 |
| #8 | — | **Reject** nếu các worker vẫn bận |

Đây là một **kịch bản giả lập với task luôn bận**, không phải một thứ tự bảo đảm của hệ thống khi task chạy nhanh. Khi worker giải phóng hoặc lịch xen kẽ khác, kết quả có thể khác.

Với hàng đợi có chỗ chứa, `ThreadPoolExecutor` thường **ưu tiên xếp queue sau khi đạt core**, rồi mới mở rộng số worker vượt core khi queue không nhận thêm. Với handoff bằng 0, không thể đệm task, nên khi core workers bận và không có worker chờ, pool mở rộng **sớm hơn** đến max.

Vì vậy:

- `queueCapacity=0` thường **tăng worker sớm** và **từ chối sớm** khi tất cả worker đều bận.
- `queueCapacity>0` có thể **làm mượt burst** bằng cách giữ task chờ, đổi lại chấp nhận **độ trễ trong queue** và chi phí lưu trữ.
- `queueCapacity` rất lớn có thể khiến `maxPoolSize` **hầu như không có cơ hội phát huy tác dụng**, vì task cứ được nhận vào queue thay vì kích hoạt tạo thêm non-core workers.
- **Không có một giá trị queueCapacity tốt cho mọi workload**. Cần dựa trên throughput, thời gian xử lý, SLA và giới hạn hệ thống phụ thuộc.

## 7. Khi nào worker được tái sử dụng dù queue bằng 0?

Một nhầm lẫn phổ biến là “hàng đợi không có chỗ chứa” đồng nghĩa “mỗi task phải tạo worker mới”. Với **ThreadPoolTaskExecutor**, worker xử lý xong task sẽ quay lại vòng lặp lấy task tiếp theo. Khi worker đang **chờ nhận** qua `SynchronousQueue`, producer có thể chuyển task mới trực tiếp cho worker đó.

~~~text
Task A ---> worker-1 ----> hoàn tất A
                             |
                             +--- chờ ở SynchronousQueue
                                          ^
                                          |
Task B -----------------------------------+
                  handoff trực tiếp
                                          |
                                cùng worker-1 chạy B
~~~

Vì vậy, **`queueCapacity=0` vẫn có thể tái sử dụng luồng**. Đây là khác biệt bản chất với **`SimpleAsyncTaskExecutor`**, vốn tạo luồng mới cho từng tác vụ thay vì duy trì worker pool.

Nếu có nhiều task đến đúng lúc không worker nào đang chờ, pool có thể cần tạo thêm thread (nếu dưới max) hoặc reject. Thời điểm giao việc ảnh hưởng đến việc nhận task, nhưng **không có queue lưu task để chờ** nếu không handoff ngay được.

**Phân biệt ba khái niệm:**

| Khái niệm | Ý nghĩa |
| --- | --- |
| **Direct handoff** | Không giữ task trong queue, gửi trực tiếp cho worker có thể nhận ngay. |
| **Thread reuse** | Worker đã làm task trước có thể làm task tiếp theo. |
| **Thread per task** | Mỗi task có một thread mới, không tái sử dụng. |

`ThreadPoolTaskExecutor(queueCapacity=0)` kết hợp **direct handoff + thread reuse**; `SimpleAsyncTaskExecutor` có mô hình **thread per task**. Không nên thay thế khái niệm này bằng khái niệm kia.

## 8. Quá tải: TaskRejectedException và chính sách xử lý

Khi queue không nhận được task và pool đã đạt giới hạn worker, **rejection** xảy ra. `ThreadPoolExecutor` mặc định dùng **AbortPolicy**: ném `RejectedExecutionException`. Trong API `ThreadPoolTaskExecutor` của Spring, lỗi từ chối được biểu diễn thành **`TaskRejectedException`** (một ngoại lệ Spring của việc gửi task không được chấp nhận).

Đây là lỗi ở **thời điểm gửi task**, khác với lỗi xảy ra *bên trong* worker sau khi task đã chạy.

~~~text
Caller
  |
  +-- execute(task)
         |
         +-- accepted -> worker thực thi -> có thể lỗi nghiệp vụ
         |
         +-- rejected -> caller nhận lỗi ngay khi submit
~~~

Ứng dụng phải xác định quy tắc khi quá tải. Một số lựa chọn:

| Rejection policy | Hành vi | Điều cần cân nhắc |
| --- | --- | --- |
| **AbortPolicy** | Ném ngoại lệ khi không nhận được task | Rõ ràng, dễ ghi log/đếm và phản hồi quá tải |
| **CallerRunsPolicy** | Caller tự thực thi task nếu executor chưa shutdown | Tạo backpressure gián tiếp nhưng có thể **chặn request thread** và làm mất tính async của lời gọi |
| **DiscardPolicy** | Bỏ task theo chính sách của executor | Dễ mất công việc âm thầm, thường không phù hợp việc nghiệp vụ quan trọng |
| **DiscardOldestPolicy** | Thử loại task đầu queue rồi gửi lại | Không phù hợp để suy diễn như queue có dữ liệu khi đang dùng SynchronousQueue; nên tránh nếu không có lý do đã kiểm chứng |

Có thể đặt chính sách cho bean:

~~~java
import java.util.concurrent.ThreadPoolExecutor;

executor.setRejectedExecutionHandler(
        new ThreadPoolExecutor.CallerRunsPolicy());
~~~

Đây là **đoạn cấu hình minh họa**, cần gọi trước `initialize()`. **CallerRunsPolicy không miễn phí**: nếu một HTTP request đang gửi tác vụ và pool đã đầy, chính request thread có thể phải chạy toàn bộ tác vụ, kéo dài response latency và khiến cơ chế `@Async` không còn chạy trên worker ở lần đó. Nếu executor đã shutdown, chính sách này không bảo đảm sẽ thực thi task.

Khi muốn trả lời nhanh dưới tải cao, thường cần **thừa nhận overload và từ chối có chủ đích**: bắt ngoại lệ ở ranh giới hợp lý, giới hạn retry và trả thông tin thích hợp cho caller. Khi muốn xử lý hết công việc, cần bộ đệm/queue có giới hạn, retry riêng hoặc **durable queue** bên ngoài cho tác vụ có yêu cầu bền vững; đừng xem `ThreadPoolTaskExecutor` là hàng đợi durable.

## 9. Tích hợp với @Async

Trong Spring, một phương thức được đánh dấu **`@Async`** có thể được interceptor chuyển tới một executor. Để dùng đúng bean có queue bằng 0, chỉ định **tên executor** tường minh.

**Cấu hình** (cần Spring Context, và thường có trong ứng dụng Spring Boot):

~~~java
package example.concurrency;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncSupport {
}
~~~

**Service**:

~~~java
package example.concurrency;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class BillingService {
    @Async("directHandoffExecutor")
    public CompletableFuture<Integer> computeTotal(int price, int fee) {
        int total = price + fee;
        return CompletableFuture.completedFuture(total);
    }
}
~~~

Nếu **một bean khác** gọi `billingService.computeTotal(100, 20)` qua Spring proxy và tác vụ được nhận, caller nhận một future. Khi hoàn tất thành công, future có kết quả **120**. Nếu caller gọi `join()` trước khi có kết quả thì caller vẫn chặn.

**Phân biệt lỗi hai thời điểm:**

- **Ngay khi gửi:** pool đầy, không có handoff, không thể thêm worker → lời gọi qua executor có thể ném `TaskRejectedException` tại ranh giới submit; caller cần xử lý riêng.
- **Sau khi được nhận:** logic bên trong `computeTotal` lỗi → future có thể hoàn tất exceptionally; caller xử lý qua `exceptionally`, `handle` hoặc `join`/`get`.

Những điều kiện của `@Async` vẫn áp dụng:

- Bật `@EnableAsync` hoặc cấu hình tương đương.
- Trong chế độ proxy thông thường, **self-invocation** như `this.computeTotal(...)` từ cùng bean **không đi qua proxy**, nên annotation không tạo việc chuyển luồng như mong đợi.
- `@Async` không có nghĩa **không bao giờ chặn**: nếu sử dụng CallerRunsPolicy, caller có thể phải thực thi task; nếu gọi `join()` thì caller đợi kết quả.
- Những giá trị gắn với thread (MDC, SecurityContext, transaction/request attributes) **không tự động bảo đảm được mang qua mọi executor**. Truyền dữ liệu nghiệp vụ dưới dạng tham số/snapshot, dùng cơ chế propagation được quản lý khi cần.

**Lưu ý:** ví dụ service trả `CompletableFuture.completedFuture(total)` vì bản thân phương thức đã được thực thi trong cơ chế `@Async`. Không cần khởi tạo thêm `supplyAsync` ở bên trong chỉ để tạo một tầng bất đồng bộ nữa.

## 10. Tác vụ phù hợp và cách chọn giới hạn

`queueCapacity=0` có thể phù hợp khi ưu tiên **không tích tụ backlog trong bộ nhớ** và muốn xác định giới hạn đồng thời rõ:

### Tác vụ CPU-bound

Ví dụ: nén, mã hóa, tính toán thuần CPU. Nếu CPU chỉ có một số core hữu hạn, tăng số worker quá cao không làm throughput tăng vô hạn. Một pool với **`maxPoolSize` xấp xỉ năng lực CPU phù hợp** và queue/rejection được đo lường có thể giữ tải ổn định. Handoff bằng 0 từ chối nhanh khi pool đầy, nhưng có thể từ chối những burst ngắn mà queue nhỏ lẽ ra hấp thụ được.

### Tác vụ I/O-bound

Ví dụ: gọi REST downstream, đọc DB hoặc storage. Worker thường chờ I/O; mức concurrency hữu ích có thể lớn hơn số core, nhưng **DB connection pool, HTTP connection limits và rate limit của downstream** là ràng buộc chính. Nếu `maxPoolSize=100` trong khi DB chỉ có 10 connection, tăng worker không tự tăng năng lực DB; ngược lại có thể làm tích tụ thêm tác vụ đang chờ connection.

### Công việc bắt buộc phải được xử lý

Nếu một task tương đương nghiệp vụ quan trọng (giao dịch, gửi thông báo phải bảo đảm, xử lý đơn hàng) thì **hàng đợi trong RAM của executor cũng không phải lưu trữ bền vững**. `queueCapacity=0` càng không có buffer. Khi cần tiếp nhận/ack và xử lý sau kể cả restart, hãy cân nhắc cơ chế job/queue bền vững có idempotency và retry phù hợp.

### Gợi ý lựa chọn

| Ưu tiên | Hướng xem xét |
| --- | --- |
| Từ chối sớm khi worker bận, không giữ backlog nội bộ | `queueCapacity=0`, max hữu hạn và rejection rõ ràng |
| Hấp thụ burst ngắn trong giới hạn chấp nhận được | `queueCapacity` dương, hữu hạn; theo dõi queue wait |
| Nhiều task CPU-bound có chi phí nhỏ | Worker pool được tái sử dụng, số worker phù hợp CPU |
| Nhiều task chặn I/O | Giới hạn theo tài nguyên đích; có thể xem xét virtual threads trên JDK hỗ trợ |
| Không được mất job | Hệ thống job queue bền vững, không dựa đơn độc vào executor nội bộ |

Không nên coi một giá trị cấu hình cụ thể là mặc định tốt cho mọi dự án; phải thử với tải đại diện.

## 11. Quan sát và giám sát khi queueCapacity=0

Vì `SynchronousQueue.size()` luôn trả **0**, chỉ nhìn `queueSize=0` sẽ dẫn đến nhận định sai rằng hệ thống không bận.

Theo dõi đồng thời:

| Tín hiệu | Ý nghĩa |
| --- | --- |
| `getPoolSize()` | Số worker hiện có trong pool. |
| `getActiveCount()` | Số worker đang thực thi task (snapshot tại thời điểm lấy số). |
| `getCorePoolSize()` / `getMaxPoolSize()` | Giới hạn cấu hình, dùng đối chiếu với active và pool size. |
| Rejected task count | Mức độ quá tải và công việc bị từ chối; cần instrumentation phù hợp để đếm. |
| Thời gian chạy task | Worker bận vì CPU hay đang chặn I/O? |
| Thời gian caller submit và response latency | Chính sách CallerRuns có làm caller chậm không? |
| Downstream saturation | Connection pool, timeout và các dịch vụ phụ thuộc đã đạt giới hạn chưa? |

**`queueSize=0` là thông tin về cấu trúc, không phải chứng nhận hệ thống khỏe.** Với handoff trực tiếp, chỉ số rejection và active/max thường có ích hơn chiều dài queue.

Ví dụ tình huống cần điều tra: pool size chạm 4/4, active 4/4, queueSize 0, và số lần reject tăng. Điều này **phù hợp với saturation trong direct handoff**, dù queue không hề tăng độ dài.

## 12. Kiểm thử hành vi queue bằng 0

Một kiểm thử đáng tin phải **giữ worker bận bằng synchronization primitive**, giống `DirectHandoffDemo`, thay vì gửi vài task thật nhanh rồi hy vọng scheduler tạo được race.

Quy trình thử với bean Spring:

1. Cấu hình pool **core=2, max=4, queue=0** với chính sách rejection đã biết.
2. Gửi bốn task chặn trên một `CountDownLatch`; dùng latch khác để xác nhận cả bốn đã bắt đầu.
3. Gửi task thứ năm trong khi bốn worker chắc chắn chưa kết thúc.
4. Với AbortPolicy, khẳng định lần gửi thứ năm bị từ chối bằng `TaskRejectedException` ở lớp Spring, hoặc `RejectedExecutionException` khi thử trực tiếp Java executor.
5. Giải phóng latch trong `finally`, đợi task kết thúc và shutdown pool; không để test treo.

Kiểm tra thêm việc **tái sử dụng thread** sau khi worker hoàn tất. Đừng khẳng định một tên worker cụ thể hoặc thứ tự nào chắc chắn được chọn; chỉ cần xác nhận một worker còn sống có thể nhận task kế tiếp khi sẵn sàng và hiểu tại sao điều đó có thể xảy ra.

Nếu muốn xác nhận Spring đã chọn đúng queue type, có thể truy vấn:

~~~java
boolean isDirect =
        executor.getThreadPoolExecutor().getQueue()
                instanceof java.util.concurrent.SynchronousQueue;
~~~

Đây là **đoạn minh họa trong scope có biến `executor` đã được khởi tạo**. Nó kiểm tra cấu hình thực tế, không thay thế bài test behavior.

**Đừng viết test chỉ assert `getQueueSize()==0`.** Kể cả khi pool đang đầy và reject mọi tác vụ tiếp theo, con số ấy vẫn bằng 0 với SynchronousQueue.

## 13. Các lỗi tư duy phổ biến

**“queueCapacity=0 là không có queue nên task nào cũng reject.”** Sai. Nếu worker nhận được ngay hoặc có thể tạo worker mới dưới max, task vẫn được chấp nhận.

**“Direct handoff luôn tạo thread mới.”** Sai. Worker pool có thể tái sử dụng worker rảnh; chỉ tạo thêm worker khi không handoff được và chưa chạm max.

**“Cấu hình maxPoolSize=4 nghĩa là luôn có bốn worker.”** Sai. Worker được tạo theo nhu cầu và chính sách pool; core/max là ngưỡng.

**“queueCapacity=0 giống SimpleAsyncTaskExecutor.”** Sai. Một bên là thread pool có thể tái sử dụng worker và SynchronousQueue, bên kia có mô hình tạo luồng mới cho từng task.

**“queueSize bằng 0 chứng minh không có backlog hoặc sự cố.”** Sai. Không có buffer nội bộ, nhưng bốn worker có thể đang nghẽn và caller có thể bị từ chối.

**“Đặt queueCapacity=0 là bật backpressure tự động bằng cách chặn caller.”** Sai. Với AbortPolicy mặc định, pool từ chối khi đầy; chỉ những chính sách cụ thể như CallerRuns mới khiến caller trực tiếp thực thi task.

**“Chỉ cần tăng maxPoolSize để hết reject.”** Rủi ro. Tăng không giới hạn số platform threads có thể đẩy sự cố sang OS hoặc downstream; cần chọn giới hạn theo khả năng hệ thống.

**“ThreadPoolTaskExecutor có queueCapacity=0 không thể nhận burst.”** Không hoàn toàn. Pool có thể tăng từ core tới max rất nhanh và tận dụng worker rảnh. Nhưng khi cả max workers đang bận, không có buffer để giữ thêm task.

**“@Async có nghĩa caller luôn trả về tức thì.”** Không tuyệt đối: caller có thể chịu chi phí submit, lỗi rejection, CallerRuns hoặc chờ qua future.

**“Không có queue nghĩa là không cần shutdown.”** Sai. Pool vẫn sở hữu worker threads và cần quản lý lifecycle, task duration và việc dừng nhận việc đúng cách.

## 14. Bài tập suy luận

**Bài 1.** Với `corePoolSize=2`, `maxPoolSize=4`, `queueCapacity=0`, cả bốn task đều đang chạy và không có worker rảnh. Gửi task thứ năm, dùng AbortPolicy mặc định. Điều gì xảy ra?

**Đáp án:** Không handoff được, không thể tạo worker thứ năm, nên **reject**.

**Bài 2.** Với cấu hình trên, worker #3 vừa hoàn tất và đang chờ task. Caller gửi một tác vụ. Có bắt buộc tạo worker mới không?

**Đáp án:** Không. Handoff trực tiếp có thể đưa task vào worker #3; worker đó được tái sử dụng.

**Bài 3.** `getQueueSize()` liên tục bằng 0, nhưng rejection tăng. Có mâu thuẫn không?

**Đáp án:** Không. SynchronousQueue không lưu task, còn pool có thể bận toàn bộ worker.

**Bài 4.** Đổi `queueCapacity` từ 0 sang 50, giữ core=2 và max=4. Pool còn luôn tăng đến 4 worker ngay khi task thứ ba tới không?

**Đáp án:** Không. Sau khi hai core workers bận, pool thường ưu tiên đưa task vào queue 50 chỗ; chỉ khi queue không nhận thêm mới xem xét tăng worker vượt core.

**Bài 5.** Tại sao `queueCapacity=0` không tương đương với `SimpleAsyncTaskExecutor`?

**Đáp án:** `ThreadPoolTaskExecutor` vẫn có pool, worker có thể được tái sử dụng. `SimpleAsyncTaskExecutor` tạo thread riêng cho từng task.

**Bài 6.** Nếu cấu hình **CallerRunsPolicy** và pool đã đầy, một phương thức `@Async` có chắc chạy ngoài request thread không?

**Đáp án:** Không. Chính request thread có thể thực thi task khi nhận rejection từ executor, ảnh hưởng trực tiếp tới response latency.

**Bài 7.** Có nên dùng `maxPoolSize` rất lớn để tránh rejection khi một database chỉ có 10 connections?

**Đáp án:** Không nên quyết định theo cách đó. Cần xác định mức đồng thời được phép với database, timeout và khả năng chờ/từ chối tại biên phù hợp.

## 15. Checklist triển khai thực tế

- [ ] Xác minh Spring bean thực sự có `queueCapacity=0` và sử dụng `SynchronousQueue`.
- [ ] Cấu hình `corePoolSize` và **`maxPoolSize` hữu hạn** theo workload.
- [ ] Xác định **rejection policy** và hành vi của caller khi pool đầy.
- [ ] Phân biệt rõ **không có buffer** với **không có khả năng nhận task**.
- [ ] Giới hạn tài nguyên downstream: DB/HTTP connections, timeouts, rate limits.
- [ ] Theo dõi `activeCount`, `poolSize`, rejections và task duration; không dựa riêng vào queue size.
- [ ] Kiểm thử bão hòa pool bằng latch và giải phóng/đóng executor đầy đủ.
- [ ] Với `@Async`, gọi qua Spring proxy và xử lý lỗi tại lúc submit riêng với lỗi trong future.
- [ ] Xem lại hành vi shutdown, tác vụ dài và lifecycle của ứng dụng.
- [ ] Đánh giá **queue dương hữu hạn** nếu workload có burst cần đệm ngắn.

## Tổng kết

**`queueCapacity=0` trong `ThreadPoolTaskExecutor` chọn `SynchronousQueue`: tác vụ được chuyển giao trực tiếp cho worker đang đợi, nếu không thì pool tăng worker tới `maxPoolSize`, và sau đó từ chối theo chính sách cấu hình.** Không có buffer lưu tác vụ chờ nhưng vẫn có **thread reuse** của pool. Hiệu quả của cấu hình này nằm ở việc kiểm soát mức đồng thời, độ trễ và quá tải một cách có chủ đích, chứ không nằm ở việc queue có độ dài bằng 0.

**Tài liệu tra cứu:** Spring Framework Javadoc của `ThreadPoolTaskExecutor` (`setQueueCapacity`, `createQueue`); Java SE Javadoc của `ThreadPoolExecutor`, `SynchronousQueue`, `RejectedExecutionHandler`; tài liệu Spring về Task Execution và `@Async`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-007">keepAliveSeconds và allowCoreThreadTimeOut</a>

<details>
<summary>Xem chi tiết</summary>

# keepAliveSeconds và allowCoreThreadTimeOut trong ThreadPoolTaskExecutor

## 1. Mục tiêu học tập

Khi xử lý nhiều công việc bất đồng bộ trong ứng dụng Spring, một nhóm luồng (**thread pool**) giúp tái sử dụng worker threads thay vì tạo thread nền tảng mới cho mọi tác vụ. Hai tham số quan trọng quyết định điều gì xảy ra **sau khi lưu lượng giảm** là `keepAliveSeconds` và `allowCoreThreadTimeOut`.

Sau bài này, bạn có thể:

- Giải thích ý nghĩa của `ThreadPoolTaskExecutor.setKeepAliveSeconds(int)` và `setAllowCoreThreadTimeOut(boolean)`.
- Phân biệt **core threads** và **non-core threads**, cấu hình “duy trì core threads” với “cho cả core threads được thu hồi”.
- Dự đoán tương tác giữa `corePoolSize`, `maxPoolSize`, `queueCapacity` và keep-alive khi pool tăng/giảm quy mô.
- Cấu hình đúng một `ThreadPoolTaskExecutor` trong Spring, biết khi nào cần giữ worker nóng để giảm độ trễ và khi nào nên tiết kiệm tài nguyên lúc nhàn rỗi.
- Tránh các lỗi như đặt keep-alive bằng 0 khi bật core timeout, nhầm idle timeout với thời hạn chạy task, hoặc tưởng `maxPoolSize` luôn điều khiển số worker ngay khi tải tăng.
- Quan sát pool bằng thông số runtime và kiểm tra được hành vi qua ví dụ Java 17+.

**Phạm vi:** Bài này tập trung vào `org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor` của Spring Framework, vốn quản lý một `java.util.concurrent.ThreadPoolExecutor` của JDK. Các nhận xét về thời gian chờ áp dụng cho **platform thread pools** kiểu này. Không áp đặt chúng lên mọi triển khai `TaskExecutor` hoặc executor tạo **virtual thread cho mỗi task**.

## 2. Hai tham số là gì?

### 2.1. `keepAliveSeconds` — thời gian một worker được phép chờ việc

~~~java
executor.setKeepAliveSeconds(30);
~~~

Đoạn mã trên chỉ minh họa một lời gọi setter trên `ThreadPoolTaskExecutor` đã có. Giá trị `30` nghĩa là **30 giây nhàn rỗi** được dùng làm thời hạn giữ worker đủ điều kiện bị thu hồi; không phải thời gian tối đa để chạy một task. Khi một worker kết thúc tác vụ và không lấy được tác vụ mới trong thời hạn này, pool có thể cho worker đó kết thúc.

**Mặc định của Spring `ThreadPoolTaskExecutor` là 60 giây.** Theo quy tắc mặc định của `ThreadPoolExecutor`, keep-alive chỉ áp dụng cho các worker **vượt quá** `corePoolSize`. Như vậy, nếu `corePoolSize = 2`, `maxPoolSize = 6` và pool tạm tăng lên 5 workers, các worker dư trên mức core có thể kết thúc sau thời gian nhàn rỗi; các core workers không tự kết thúc chỉ vì không có việc khi chưa bật core timeout.

Thời hạn keep-alive chỉ bắt đầu có ý nghĩa khi **worker đang chờ công việc**, không phải từ lúc ứng dụng khởi động, từ lúc worker được tạo hay từ lúc task bắt đầu chạy. Một task đang xử lý 5 phút **không bị ngắt** vì `keepAliveSeconds = 30`.

### 2.2. `allowCoreThreadTimeOut` — core workers có được tự kết thúc không?

~~~java
executor.setAllowCoreThreadTimeOut(true);
~~~

Mặc định là **`false`**. Khi `false`, một core worker đã được tạo thường tiếp tục tồn tại dù đang nhàn rỗi. Khi `true`, **core workers cũng chịu cùng keep-alive policy như non-core workers**. Vì vậy, nếu không có tác vụ trong một khoảng thời gian đủ dài, số worker thực tế có thể giảm về **0**, dù cấu hình `corePoolSize` vẫn lớn hơn 0. Khi có task mới, executor tạo worker trở lại theo nhu cầu.

Điều kiện bắt buộc của JDK khi bật core timeout: **`keepAliveTime > 0`**. Nếu bật `ThreadPoolExecutor.allowCoreThreadTimeOut(true)` trong khi keep-alive bằng 0, JDK ném `IllegalArgumentException`. Khi dùng Spring, hãy cấu hình keep-alive dương trước khi khởi tạo executor nếu định bật cờ này.

### 2.3. Bảng phân biệt nhanh

| Cấu hình | Core workers khi nhàn rỗi | Workers vượt core khi nhàn rỗi | Khi có việc trở lại |
| --- | --- | --- | --- |
| `keepAliveSeconds=60`, `allowCoreThreadTimeOut=false` (mặc định) | Được giữ lại sau khi đã tạo | Được thu hồi sau thời gian chờ xấp xỉ 60 giây | Tái sử dụng core còn sống hoặc tạo thêm khi cần |
| `keepAliveSeconds=10`, `allowCoreThreadTimeOut=false` | Vẫn được giữ | Được thu hồi nhanh hơn sau khoảng 10 giây nhàn rỗi | Core còn sống xử lý các đợt việc mới |
| `keepAliveSeconds=10`, `allowCoreThreadTimeOut=true` | Cũng được thu hồi khi đủ nhàn rỗi | Được thu hồi | Pool có thể từ 0 workers tạo lại các worker |
| `keepAliveSeconds=0`, `allowCoreThreadTimeOut=true` | **Không hợp lệ** | — | JDK không cho bật core timeout với keep-alive bằng 0 |

Các mốc trên là **quy tắc chờ tối đa cho idle worker**, không phải đồng hồ bảo đảm thu hồi chính xác ở giây thứ 10 hoặc 60. Lập lịch hệ điều hành, công việc đến trong lúc chờ và trạng thái pool ảnh hưởng thời điểm quan sát. Cần phân biệt **`getCorePoolSize()` (cấu hình)** với **`getPoolSize()` (số worker thực tế tại thời điểm đọc)**.

## 3. Cơ chế của ThreadPoolExecutor: tạo worker, đưa vào queue, rồi mới mở rộng

Hai cấu hình timeout chỉ có thể hiểu chính xác khi đặt cạnh các thuộc tính khác của `ThreadPoolTaskExecutor`:

- `corePoolSize`: số worker cơ sở mục tiêu; mặc định Spring là **1**.
- `maxPoolSize`: giới hạn số worker tối đa; mặc định là **`Integer.MAX_VALUE`**.
- `queueCapacity`: số tác vụ có thể nằm trong hàng đợi; mặc định là **`Integer.MAX_VALUE`**.
- `keepAliveSeconds`: thời hạn nhàn rỗi để worker đủ điều kiện kết thúc; mặc định **60**.
- `allowCoreThreadTimeOut`: core workers có dùng keep-alive hay không; mặc định **false**.

Với `ThreadPoolTaskExecutor`, `queueCapacity > 0` tạo `LinkedBlockingQueue` có giới hạn tương ứng; `queueCapacity <= 0` dùng `SynchronousQueue` (hàng đợi trao tay, không có chỗ lưu task chờ).

### 3.1. Trình tự khi task đến

Khi gọi `execute` hoặc `submit`:

1. Nếu số worker hiện có **chưa đến `corePoolSize`**, executor ưu tiên tạo worker để chạy task mới.
2. Khi đã có ít nhất core workers, executor ưu tiên **đưa task vào queue**.
3. Chỉ khi **không thể đưa vào queue** và số worker chưa tới `maxPoolSize`, nó tạo thêm non-core worker.
4. Nếu worker đã tới max và queue không nhận thêm task, tác vụ bị **từ chối** theo `RejectedExecutionHandler`.

Sơ đồ:

~~~text
Task mới
   |
   +-- workers < corePoolSize? -- Có --> tạo core worker
   |                              
   +-- Không --> queue còn chỗ? --- Có --> xếp hàng chờ
                    |
                    +-- Không --> workers < maxPoolSize? -- Có --> thêm worker
                                                |
                                                +-- Không --> từ chối task
~~~

**Hệ quả rất quan trọng:** `maxPoolSize` thường **không làm pool tăng** chỉ vì core workers đang bận, nếu `queueCapacity` còn chỗ. Với queue gần như vô hạn, executor thường chỉ cần chạy ở mức core và tiếp tục xếp hàng. Khi đó các non-core workers ít khi được tạo, nên việc chỉnh `keepAliveSeconds` cho non-core có thể gần như không tạo khác biệt quan sát được.

### 3.2. Ví dụ lý thuyết với queue hữu hạn

Giả sử cấu hình:

~~~java
executor.setCorePoolSize(1);
executor.setMaxPoolSize(3);
executor.setQueueCapacity(1);
executor.setKeepAliveSeconds(5);
executor.setAllowCoreThreadTimeOut(false);
~~~

Đây là cấu hình minh họa, chưa phải chương trình độc lập. Giả sử các task chạy đủ lâu để không có task nào hoàn tất trước khi nộp task thứ năm:

| Task nộp | Điều pool làm | Số worker / số task chờ, ở trạng thái giả định này |
| --- | --- | --- |
| Task 1 | Tạo core worker | 1 worker / 0 chờ |
| Task 2 | Đưa vào queue | 1 worker / 1 chờ |
| Task 3 | Queue đầy, tạo worker thứ 2 | 2 workers / 1 chờ |
| Task 4 | Queue vẫn đầy, tạo worker thứ 3 | 3 workers / 1 chờ |
| Task 5 | Max đạt 3 và queue đầy, áp dụng rejection policy | 3 workers / 1 chờ |

Khi tất cả hoàn tất và pool nhàn rỗi, với `allowCoreThreadTimeOut=false`, hai workers vượt core có thể được thu hồi sau thời gian nhàn rỗi tương ứng; **1 core worker** vẫn có thể ở lại. Nếu bật `true` với keep-alive dương, core worker cuối cùng cũng có thể bị thu hồi.

Bảng trên là một **lịch trình giả định có task bị giữ lại**, không phải khẳng định các lần submit thông thường luôn tạo đúng số worker đó: task thực tế có thể hoàn thành giữa hai lần nộp.

### 3.3. Tại sao “cho core timeout” vẫn hữu ích khi queue > 0?

Theo Javadoc Spring, bật `allowCoreThreadTimeOut` giúp pool **tăng khi có nhu cầu và giảm lúc rảnh** ngay cả với queue có dung lượng dương. Lý do: các core workers bình thường vốn được giữ lại vĩnh viễn khi nhàn rỗi, còn cờ này cho phép chúng được thu hồi.

Tuy nhiên, cờ này **không đảo trình tự nhận task**: pool vẫn ưu tiên queue khi đã có core workers và queue chưa đầy. Nếu muốn tăng mạnh số worker khi có burst, phải xem `queueCapacity`, tốc độ task đến và `maxPoolSize` cùng nhau.

## 4. Ví dụ Spring hoàn chỉnh: cấu hình và kiểm tra chính sách

Ví dụ sau là chương trình Java chạy độc lập, dùng **JDK 17+**, Maven và **Spring Framework 6.1.3**. Nó khởi tạo `ThreadPoolTaskExecutor` thủ công, đọc chính sách thực sự trên `ThreadPoolExecutor` bên trong rồi xử lý một task trả kết quả.

Cấu trúc:

~~~text
keepalive-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── KeepAliveDemo.java
~~~

### 4.1. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>keepalive-demo</artifactId>
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
                    <mainClass>demo.KeepAliveDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Dùng dependency `spring-context` đủ cho lớp `ThreadPoolTaskExecutor` trong ví dụ này. Trong ứng dụng Spring Boot thực tế, nên giữ phiên bản Spring Framework do BOM/parent của dự án quản lý, tránh tự ghim một bản khác chỉ vì đoạn minh họa.

### 4.2. `src/main/java/demo/KeepAliveDemo.java`

~~~java
package demo;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class KeepAliveDemo {

    public static void main(String[] args) throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2);
        executor.setKeepAliveSeconds(3);           // timeout nhàn rỗi: 3 giây
        executor.setAllowCoreThreadTimeOut(true);   // core workers cũng có thể hết hạn
        executor.setThreadNamePrefix("lesson-");
        executor.initialize();

        try {
            System.out.println("core=" + executor.getCorePoolSize());
            System.out.println("max=" + executor.getMaxPoolSize());
            System.out.println("keepAliveSeconds="
                    + executor.getKeepAliveSeconds());
            System.out.println("coreTimeout="
                    + executor.getThreadPoolExecutor()
                              .allowsCoreThreadTimeOut());
            System.out.println("effectiveKeepAliveSeconds="
                    + executor.getThreadPoolExecutor()
                              .getKeepAliveTime(TimeUnit.SECONDS));

            Future<Integer> answer = executor.submit(() -> 21 * 2);
            System.out.println("answer=" + answer.get());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Chạy trong thư mục `keepalive-demo`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chương trình in ra (không tính log riêng của Maven nếu có):

~~~text
core=2
max=4
keepAliveSeconds=3
coreTimeout=true
effectiveKeepAliveSeconds=3
answer=42
~~~

**Diễn giải:** `initialize()` tạo executor nội bộ với các thuộc tính được cung cấp. Gọi `getThreadPoolExecutor()` sau khi khởi tạo giúp xem **chính sách thực sự của JDK**: `allowsCoreThreadTimeOut()` trả `true` và `getKeepAliveTime(...)` là 3 giây. `submit` và `Future.get()` xác nhận pool nhận và xử lý task. Ví dụ cố tình **không in số worker tức thời** vì con số ấy phụ thuộc lịch chạy và thời điểm quan sát, không có stdout cố định đáng tin cậy.

Khi tự tạo executor ngoài Spring container như trên, ứng dụng phải chủ động quản lý lifecycle. Lệnh `shutdown()` là thao tác kết thúc pool, **khác hoàn toàn** với keep-alive tự thu hồi idle workers trong khi pool vẫn có thể nhận task mới.

## 5. Thí nghiệm quan sát việc thu hồi worker, không suy luận bằng sleep ngắn

Bạn có thể kiểm tra thời gian sống của worker bằng Java thuần trước khi đem nguyên tắc vào Spring. Đoạn dưới dùng `ThreadPoolExecutor` của JDK, bảo đảm task đã hoàn tất rồi mới quan sát. Nó **không phụ thuộc Spring** và chỉ dùng JDK 17+.

Lưu thành `CoreTimeoutExperiment.java`:

~~~java
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class CoreTimeoutExperiment {

    public static void main(String[] args) throws Exception {
        observe(false);
        observe(true);
    }

    private static void observe(boolean allowCoreTimeout)
            throws Exception {

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );
        pool.allowCoreThreadTimeOut(allowCoreTimeout);

        try {
            pool.submit(() -> 42).get(); // chờ task kết thúc
            System.out.println("coreTimeout=" + allowCoreTimeout);
            System.out.println("poolSizeAfterTask=" + pool.getPoolSize());

            // Chờ hơn keepAlive 1 giây để quan sát.
            // Không dùng kết quả này làm assertion cố định.
            Thread.sleep(1500);
            System.out.println("poolSizeAfterIdle=" + pool.getPoolSize());
        } finally {
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
javac CoreTimeoutExperiment.java
java CoreTimeoutExperiment
~~~

**Một kết quả thường quan sát được** trên máy ít tải là:

~~~text
coreTimeout=false
poolSizeAfterTask=1
poolSizeAfterIdle=1
coreTimeout=true
poolSizeAfterTask=1
poolSizeAfterIdle=0
~~~

Đây là **ví dụ kết quả quan sát**, không phải stdout bất biến. Một worker có thể chưa bị thu hồi tại đúng thời điểm `Thread.sleep(1500)` kết thúc nếu máy bận, bị tạm dừng lâu hoặc việc lập lịch khác dự kiến. Mục đích là đối chiếu **xu hướng**: khi giữ core, worker tồn tại; khi cho core timeout, nó có thể biến mất. Tránh viết test thành công/thất bại chỉ dựa trên một lần đọc `getPoolSize()` sau `sleep()` ngắn. Với kiểm thử tự động, dùng polling có deadline phù hợp và không giả định thời điểm hủy chính xác từng mili giây.

Chú ý: phép thử chọn `corePoolSize=maxPoolSize=1` để loại bỏ non-core workers; do đó sự khác biệt quan sát được đến **từ cờ core timeout**.

## 6. Cấu hình khi Spring quản lý bean

Trong ứng dụng dùng component scanning và cấu hình Spring, nên tạo **một bean dùng chung** thay vì tạo pool mới cho mỗi request.

Ví dụ cấu hình trong `WorkerPoolConfig.java` (class tích hợp vào ứng dụng Spring hiện có; không phải project độc lập):

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WorkerPoolConfig {

    @Bean("workerTaskExecutor")
    public ThreadPoolTaskExecutor workerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("worker-");
        return executor;
    }
}
~~~

Khi bean do Spring quản lý, container phụ trách khởi tạo và đóng executor theo vòng đời bean. Đoạn cấu hình **không tự khiến mọi `@Async` trong ứng dụng dùng bean này**: nếu có nhiều executor, cần chọn đúng tên/`@Qualifier` hoặc cấu hình executor dùng cho asynchronous method invocation.

Ví dụ chọn executor theo tên khi ứng dụng đã bật async processing:

~~~java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Async("workerTaskExecutor")
    public void notifyLater(String message) {
        System.out.println(message);
    }
}
~~~

Đoạn service minh họa tích hợp, không phải một chương trình độc lập. `@Async` làm việc qua proxy khi cấu hình async được bật; lệnh gọi nội bộ trong cùng instance có thể không qua proxy nên không chuyển sang worker pool như mong đợi. Nếu muốn nộp trực tiếp, inject `ThreadPoolTaskExecutor`/`AsyncTaskExecutor` tương ứng rồi gọi `execute` hoặc `submit`.

### Khi nào cờ này không có tác dụng?

- `SimpleAsyncTaskExecutor` không phải `ThreadPoolTaskExecutor` dùng pool tái sử dụng worker; đừng áp dụng quy tắc giữ/thu hồi core workers cho nó.
- Executor virtual-thread-per-task trong Java 21+ không có cùng cấu trúc core/max workers để chỉnh bằng hai setter này. Các carrier threads của JVM cũng không được điều khiển thông qua `ThreadPoolTaskExecutor.setKeepAliveSeconds`.
- Một `@Async` method dùng executor khác bean vừa cấu hình sẽ không chịu các thông số của bean đó.

Vì vậy, trước khi phân tích hiệu quả của `allowCoreThreadTimeOut`, hãy xác minh **executor nào thực sự đang chạy task**.

## 7. Cân nhắc hiệu năng: giảm tài nguyên hay giảm độ trễ khởi động?

Chọn `allowCoreThreadTimeOut` dựa vào hình dạng tải.

| Workload / mục tiêu | Gợi ý ban đầu | Lý do và đánh đổi |
| --- | --- | --- |
| Traffic liên tục, các task ngắn đến đều | `false`, giữ một lượng core workers hợp lý | Workers đã có sẵn, tránh chi phí tạo lại thread thường xuyên |
| Job ít khi chạy, thỉnh thoảng bùng tải | `true` với keep-alive dương vừa phải | Khi nhàn rỗi lâu có thể giảm worker về 0, đổi lại lần task mới cần tạo thread |
| Nhiều đợt burst cách nhau vài giây | Không đặt keep-alive quá nhỏ, thử `false` hoặc khoảng thời gian dài hơn | Tránh chu kỳ tạo/hủy worker liên tục (thread churn) |
| Task blocking I/O, nhiều request song song | Đo pool size, queue wait, max, downstream capacity | Keep-alive chỉ kiểm soát worker **lúc nhàn rỗi**, không giải quyết task đang block |
| Cần bảo vệ bộ nhớ khi không hoạt động | Thử `true` nhưng giới hạn core/max/queue theo tải | Tài nguyên giảm lúc rảnh nhưng không bảo đảm giảm peak usage |

**Không có một keep-alive tối ưu cho mọi dịch vụ.** Cấu hình tốt phụ thuộc độ dài task, tần suất đến, độ nhạy latency, khả năng scale, giới hạn CPU/memory và chi phí tạo thread. Tăng thời gian giữ workers thường giảm thread churn nhưng dùng thêm tài nguyên khi traffic thấp; giảm thời gian giữ workers tiết kiệm tài nguyên lúc rảnh nhưng có thể tăng độ trễ khởi động khi traffic quay lại.

### Những số liệu nên theo dõi

- **`poolSize`:** tổng worker thực đang tồn tại ở thời điểm đo; khác với `corePoolSize` cấu hình.
- **`activeCount`:** số worker đang thực thi task, thường là số liệu gần đúng.
- **`queue.size()`:** số task đang đợi; queue dài tăng nguy cơ latency cao.
- **`completedTaskCount` và throughput:** số task hoàn tất, dùng để thấy khả năng phục vụ.
- **Rejection count, task duration, queue waiting time, p95/p99 latency, CPU và memory:** phản ánh tác động lên hệ thống thực.

Với `ThreadPoolTaskExecutor` có thể đọc một số thông số:

~~~java
// Fragment trong ứng dụng đã có biến executor kiểu ThreadPoolTaskExecutor.
int workers = executor.getPoolSize();
int running = executor.getActiveCount();
int waiting = executor.getThreadPoolExecutor().getQueue().size();
boolean coreTimeout =
        executor.getThreadPoolExecutor().allowsCoreThreadTimeOut();
~~~

Các phép đọc này là **snapshot**, có thể thay đổi ngay sau đó; thích hợp monitoring/diagnostics, không phải cơ chế điều phối task. Không viết logic nghiệp vụ “nếu queue đang bằng 0 thì chắc chắn task tiếp theo sẽ chạy ngay”.

### Thay đổi cấu hình khi chương trình đang chạy

Javadoc Spring cho biết `setKeepAliveSeconds(...)` hỗ trợ điều chỉnh tại runtime, chẳng hạn qua JMX. Tuy nhiên khi thay đổi cấu hình của một pool đang hoạt động, cần kiểm tra tính hợp lệ với `allowCoreThreadTimeOut`: không thể đổi thời gian giữ worker về 0 khi core timeout đang được bật. Các thay đổi `corePoolSize`/`maxPoolSize` cũng có thể ảnh hưởng số worker được giữ hoặc thu hồi.

Nếu phải chỉnh tại runtime, nên quản lý thay đổi qua một cơ chế cấu hình được kiểm soát và có khả năng quan sát; không coi việc sửa một file properties là chắc chắn cập nhật ngay một bean đã khởi tạo. Những setter cấu trúc như `queueCapacity` chủ yếu tác động lúc tạo queue; không mặc định coi chúng là cấu hình động của queue đã tồn tại.

## 8. Các hiểu lầm thường gặp

1. **“`keepAliveSeconds=30` buộc mọi task kết thúc sau 30 giây.”** Sai. Đó là thời hạn **worker rảnh chờ task**, không phải deadline của task. Timeout nghiệp vụ, HTTP và DB phải cấu hình riêng.
2. **“`corePoolSize=4` nghĩa là luôn có 4 threads ngay khi bean tạo xong.”** Sai. Mặc định core workers được tạo khi có task; muốn khởi tạo trước có `setPrestartAllCoreThreads(true)`. Ngay cả khi prestart, bật core timeout vẫn cho phép chúng bị thu hồi khi rảnh.
3. **“`allowCoreThreadTimeOut=true` làm pool không còn corePoolSize.”** Sai. Thuộc tính core vẫn được dùng khi nhận task và quyết định quy tắc mở rộng; chỉ việc giữ core workers lúc nhàn rỗi thay đổi.
4. **“`maxPoolSize=100` chắc chắn sẽ có 100 workers khi tải tăng.”** Sai. Pool ưu tiên queue sau khi đạt core; queue không đầy thì có thể không tạo non-core workers.
5. **“`keepAliveSeconds=0` cùng core timeout true làm giải phóng tức thì.”** Sai. JDK **từ chối** trạng thái core timeout true với keep-alive bằng 0.
6. **“Worker kết thúc do keep-alive là task bị hủy.”** Sai. Chỉ worker đang **nhàn rỗi** mới đủ điều kiện thu hồi; task đang chạy không bị dừng bởi cài đặt này.
7. **“Bật core timeout là cơ chế shutdown service.”** Sai. Pool vẫn hoạt động và có thể tạo lại worker cho task mới; shutdown là một lifecycle action riêng.
8. **“ThreadPoolTaskExecutor và mọi Spring TaskExecutor có cùng thuộc tính.”** Sai. Các implementation khác nhau có mô hình quản lý thread khác nhau.
9. **“Chỉ cần thử `Thread.sleep(500)` rồi so `getPoolSize()` là chứng minh đúng/sai tuyệt đối.”** Sai. Trạng thái động phụ thuộc scheduling; cần quan sát đủ khoảng thời gian và dùng kiểm tra chịu được độ trễ.

## 9. Câu hỏi tự kiểm tra

**Câu 1.** Pool có `corePoolSize=3`, `maxPoolSize=8`, `keepAliveSeconds=20` và `allowCoreThreadTimeOut=false`. Khi traffic dừng, các workers được thu hồi thế nào?  
**Trả lời:** Những worker vượt core có thể hết hạn sau khoảng 20 giây nhàn rỗi. Các core workers đã tạo được giữ lại khi không có việc.

**Câu 2.** Với cùng cấu hình nhưng đặt `allowCoreThreadTimeOut=true`, số worker cuối cùng có thể là bao nhiêu?  
**Trả lời:** Có thể giảm về **0** nếu tất cả workers nhàn rỗi đủ lâu; `corePoolSize=3` vẫn giữ nguyên là tham số cấu hình để phục vụ task đến sau.

**Câu 3.** Pool `corePoolSize=2`, `maxPoolSize=20`, `queueCapacity=Integer.MAX_VALUE` có nhất thiết mở rộng đến 20 workers lúc tải cao?  
**Trả lời:** Không. Khi đã có 2 core workers, task mới thường được đưa vào queue gần như vô hạn, nên pool thường không phải tạo thêm workers vượt core.

**Câu 4.** Bật `allowCoreThreadTimeOut=true` trong khi `keepAliveSeconds=0` có hợp lệ không?  
**Trả lời:** Không. JDK yêu cầu keep-alive dương để core threads được phép timeout.

**Câu 5.** Một tác vụ đang gọi HTTP lâu hơn thời gian keep-alive có bị ngắt không?  
**Trả lời:** Không do keep-alive. Worker đang bận thực thi task; cần timeout của HTTP client hoặc nghiệp vụ riêng.

**Câu 6.** Vì sao bật core timeout giúp giảm tài nguyên lúc traffic thấp nhưng có thể tăng latency của request đầu tiên sau thời gian rảnh?  
**Trả lời:** Pool có thể đã thu hồi tất cả workers và phải tạo thread mới khi task kế tiếp đến.

## 10. Tài liệu tham khảo chính thức

- [Spring Framework 6.2.14 — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): `setKeepAliveSeconds`, `setAllowCoreThreadTimeOut`, `corePoolSize`, `maxPoolSize`, `queueCapacity` và mặc định.
- [Spring Framework 6.1.3 — ThreadPoolTaskExecutor Javadoc](https://www.springframework.org/spring/docs/6.1.3/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): API tương ứng phiên bản chạy trong ví dụ Maven.
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): quy tắc tăng worker/queue, idle keep-alive, core timeout, rejection và giám sát.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): các TaskExecutor implementation, cấu hình và lựa chọn executor cho asynchronous processing.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-008">Khởi động trước luồng cơ bản bằng prestartAllCoreThreads</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi động trước luồng cơ bản bằng prestartAllCoreThreads

Trong Java, một `ThreadPoolExecutor` thường **không tạo ngay toàn bộ core thread khi được khởi tạo**. Theo chính sách mặc định, pool tăng số worker theo nhu cầu khi nhận task đầu tiên. Nếu muốn các core thread được tạo trước để chờ công việc, có thể gọi `prestartAllCoreThreads()`. Với Spring Framework, `ThreadPoolTaskExecutor` cung cấp cấu hình tương ứng: `setPrestartAllCoreThreads(true)`.

Kỹ thuật này chủ yếu thay đổi **thời điểm tạo thread**: đưa một phần chi phí khởi tạo từ lần submit công việc đầu tiên sang giai đoạn chuẩn bị pool. Nó không tăng `corePoolSize`, không tự chạy nghiệp vụ, không đảm bảo task không phải đợi queue và không làm hệ thống có nhiều tài nguyên CPU hơn.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

- Giải thích khác biệt giữa `corePoolSize`, `maximumPoolSize`, số worker hiện có và số worker đang thực thi task.
- Mô tả `prestartAllCoreThreads()` làm gì, trả về gì và khi nào nên gọi.
- Phân biệt `prestartCoreThread()` (một worker) với `prestartAllCoreThreads()` (các core worker còn thiếu).
- Dùng API Java 17+ qua `ThreadPoolExecutor` và cấu hình đúng `ThreadPoolTaskExecutor` trong Spring.
- Phân tích tác động của `queueCapacity`, `allowCoreThreadTimeOut`, `ThreadFactory` và vòng đời ứng dụng.
- Đo lường chi phí khởi động và thời gian xử lý lượt task đầu tiên, nhận biết các trường hợp prestart không đem lại lợi ích.

## 2. Core thread là gì?

`ThreadPoolExecutor` quản lý một nhóm **worker thread** để thực thi `Runnable` hoặc `Callable`. Hai cấu hình quan trọng:

- `corePoolSize`: số worker cốt lõi mà pool hướng tới duy trì, trừ khi cho phép core timeout hoặc có thay đổi cấu hình/trạng thái.
- `maximumPoolSize`: trần worker mà pool có thể dùng khi lượng công việc tăng và chiến lược queue cho phép mở rộng.

Ngoài hai thông số cấu hình, trạng thái chạy thực tế còn có:

| Thuật ngữ / API | Ý nghĩa |
| --- | --- |
| `getCorePoolSize()` | Mức core được cấu hình; **không** phải số thread đã được tạo |
| `getMaximumPoolSize()` | Trần worker được cấu hình; không phải mục tiêu bắt buộc phải tạo ngay |
| `getPoolSize()` | Số worker hiện thuộc pool; có thể ít hơn core nếu chưa khởi động hoặc đã timeout |
| `getActiveCount()` | Ước lượng số worker đang thực thi task; thread đang chờ queue không được coi là active |
| `getLargestPoolSize()` | Số worker lớn nhất đã từng cùng tồn tại trong pool |
| `getQueue().size()` | Số task đang chờ trong queue ở thời điểm quan sát; đây là một giá trị có thể thay đổi ngay |

Ví dụ một pool có `corePoolSize=3` và `maximumPoolSize=8` có thể vừa mới khởi tạo với `getPoolSize()==0`. Điều đó bình thường: pool sẽ tạo worker khi task tới. Sau khi prestart, trong điều kiện khởi tạo thành công, ba core worker tồn tại và chờ công việc dù `getActiveCount()==0`.

**Worker đang tồn tại** khác với **worker đang chạy task**. Việc tạo sẵn ba worker không đồng nghĩa có ba task đã bắt đầu.

## 3. Vòng đời mặc định của một ThreadPoolExecutor

Với một pool thông thường có queue:

1. Khi tạo `ThreadPoolExecutor`, constructor thiết lập cấu hình nhưng không mặc định tạo toàn bộ core thread.
2. Khi gửi task và tổng worker chưa đạt `corePoolSize`, pool có thể tạo worker mới kèm task.
3. Khi đã có đủ core worker, pool ưu tiên đưa task tiếp theo vào queue.
4. Nếu queue không nhận thêm và còn khả năng tăng worker dưới `maximumPoolSize`, pool có thể tăng số worker.
5. Nếu vẫn không có cách nhận task, `RejectedExecutionHandler` xử lý trường hợp từ chối.
6. Worker lấy công việc từ queue, thực thi rồi tiếp tục chờ việc mới hoặc kết thúc theo chính sách vòng đời.

Đây là **thứ tự quyết định nhận task**, không phải đảm bảo cứng về thứ tự task bắt đầu. Những worker đã tồn tại có thể lấy task ra khỏi queue ngay khi được đưa vào; scheduler và thời điểm giải phóng worker vẫn ảnh hưởng thời gian hoàn thành.

Ví dụ với `corePoolSize=3`, `maximumPoolSize=8`, `queueCapacity=100`: dù bạn bật prestart, pool cũng chỉ tạo trước **3 core worker**. Nếu queue còn rộng, tải tăng thường đi vào queue trước khi dùng khả năng mở rộng vượt core. Vì vậy prestart **không thay thế** việc chọn kích thước pool và queue phù hợp.

## 4. Chính xác prestartAllCoreThreads() làm gì?

`ThreadPoolExecutor.prestartAllCoreThreads()` có chữ ký:

~~~java
public int prestartAllCoreThreads()
~~~

Phương thức cố gắng khởi động tất cả **core worker còn thiếu**, để chúng chờ công việc trong queue. Giá trị trả về là **số worker mới thực sự được bắt đầu trong lần gọi**. Đó không phải số task đã chạy, tổng số thread sau cùng hay `maximumPoolSize`.

Giả sử pool mới có core là 4, chưa có worker và ThreadFactory hoạt động bình thường:

~~~text
Trước lời gọi: poolSize = 0
prestartAllCoreThreads() -> 4
Sau lời gọi:   poolSize = 4
Gọi lại:       prestartAllCoreThreads() -> 0
~~~

Nếu đã có hai core worker đang tồn tại, lời gọi có thể tạo thêm hai để đạt mức 4. Nếu pool đã đủ core, kết quả thường bằng 0. Nếu `corePoolSize=0`, không có core worker nào cần khởi động và kết quả là 0. Số thực sự tạo được còn phụ thuộc trạng thái pool, ThreadFactory và cạnh tranh đồng thời với thay đổi cấu hình hoặc vòng đời.

### Khác với prestartCoreThread()

`prestartCoreThread()` trả về `boolean` và cố gắng bắt đầu **một** core worker còn thiếu. Nếu đã đủ số core worker, nó trả `false`.

| Phương thức | Mục đích | Kiểu trả về |
| --- | --- | --- |
| `prestartCoreThread()` | Cố gắng khởi động thêm một core worker | `boolean`: có tạo được worker hay không |
| `prestartAllCoreThreads()` | Cố gắng khởi động tất cả core worker còn thiếu | `int`: số worker khởi động trong lần gọi |

Cả hai phương thức đều **không nhận `Runnable` làm đối số**, không gửi một task giả vào queue, không đợi một tác vụ nghiệp vụ và không tự động chạy initialization logic của ứng dụng. Worker được tạo ra chủ yếu để chờ nhận việc.

Nếu muốn tải cache, mở kết nối hoặc biên dịch JIT cho một đường code cụ thể, cần một quy trình warm-up riêng; prestart không thực hiện các việc đó.

## 5. Ví dụ Java 17+ chạy độc lập

Ví dụ dưới đây tạo một pool với `corePoolSize=3`, `maximumPoolSize=5`, queue chứa tối đa 8 task. Nó kiểm tra số worker trước và sau prestart, gọi prestart lần nữa và giao ba task nhỏ. `CountDownLatch` đảm bảo chỉ in số task đã xong khi cả ba hoàn tất.

Tệp **PrestartDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class PrestartDemo {
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                3,
                5,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8));

        CountDownLatch finished = new CountDownLatch(3);

        try {
            System.out.println("before=" + executor.getPoolSize());

            int started = executor.prestartAllCoreThreads();
            System.out.println("started=" + started);
            System.out.println("poolSize=" + executor.getPoolSize());

            int startedAgain = executor.prestartAllCoreThreads();
            System.out.println("startedAgain=" + startedAgain);

            for (int i = 0; i < 3; i++) {
                executor.execute(finished::countDown);
            }

            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tasks did not finish");
            }

            System.out.println("completed=" + (3 - finished.getCount()));
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PrestartDemo.java
java PrestartDemo
~~~

Kết quả của lần chạy thành công với `ThreadFactory` mặc định, không có tác vụ khác:

~~~text
before=0
started=3
poolSize=3
startedAgain=0
completed=3
~~~

Giải thích từng dòng:

- `before=0`: pool vừa được tạo, chưa nhận task và chưa có worker.
- `started=3`: lần prestart đầu đã tạo ba core worker, không tạo đủ năm worker theo `maximumPoolSize`.
- `poolSize=3`: có ba worker trong pool; các thread có thể đang chờ công việc, không có nghĩa là đang bận.
- `startedAgain=0`: đã có đủ core worker nên không tạo thêm.
- `completed=3`: chính **ba lần `execute` và chốt `CountDownLatch`** chứng minh ba task đã hoàn thành. `prestartAllCoreThreads` không tự làm tăng biến đếm tác vụ.

Code dùng hai ngưỡng timeout để tránh treo vô hạn nếu môi trường chạy gặp lỗi; việc kiểm tra trạng thái không phụ thuộc `Thread.sleep()` hoặc thời điểm scheduler đánh thức từng thread.

### Ví dụ gọi một thread trước rồi gọi tất cả

Đây là **đoạn minh họa**, đặt trong method có `ThreadPoolExecutor pool` đã được tạo với `corePoolSize=4`, chưa có worker, chưa có task và không bật core timeout:

~~~java
boolean first = pool.prestartCoreThread();  // true
int remaining = pool.prestartAllCoreThreads(); // 3
int repeat = pool.prestartAllCoreThreads();    // 0
~~~

Trong kịch bản nêu trên, ba biến lần lượt mang giá trị `true`, `3`, `0`. Nếu pool đã xử lý task từ trước hoặc được thay đổi cấu hình cùng lúc, số worker bổ sung sẽ khác. Tránh dùng `remaining == corePoolSize` như điều kiện thành công cứng trong môi trường có nhiều tác nhân thay đổi pool.

## 6. Tích hợp trong Spring ThreadPoolTaskExecutor

`org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor` là adapter Spring phổ biến trên nền `ThreadPoolExecutor`. Từ Spring Framework 5.3.14, nó hỗ trợ:

~~~java
public void setPrestartAllCoreThreads(boolean prestartAllCoreThreads)
~~~

Đây là **setter cấu hình**, không phải cùng phương thức JDK:

- `ThreadPoolExecutor.prestartAllCoreThreads()`: hành động ngay trên pool đã có, trả về `int`.
- `ThreadPoolTaskExecutor.setPrestartAllCoreThreads(true)`: yêu cầu Spring khởi động các core worker trong quá trình **initialize executor**; trả về `void`.
- Giá trị mặc định của setter Spring là `false`, tức tạo thread theo nhu cầu.

Với bean do Spring quản lý, container sẽ gọi các bước khởi tạo cần thiết trong lifecycle của bean. Nên thiết lập thuộc tính này **trước khi bean hoàn tất khởi tạo**.

### Cấu hình @Bean

Tệp **WorkerExecutorConfig.java** trong ứng dụng Spring có dependency `spring-context`:

~~~java
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WorkerExecutorConfig {

    @Bean(name = "workerExecutor")
    public ThreadPoolTaskExecutor workerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("worker-");

        executor.setPrestartAllCoreThreads(true);
        executor.setAllowCoreThreadTimeOut(false);
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());

        return executor;
    }
}
~~~

Đây là **lớp cấu hình Spring**, không có `main` riêng và không dùng `javac` độc lập nếu thiếu thư viện Spring. Container khởi tạo bean; trong điều kiện bình thường ba core worker được tạo từ giai đoạn khởi tạo pool. Nếu để `setPrestartAllCoreThreads(false)` (mặc định), số worker sẽ tăng theo công việc thực tế.

`setAllowCoreThreadTimeOut(false)` giữ nguyên hành vi cốt lõi: các core thread không bị loại bỏ chỉ vì idle. Điều này phù hợp khi muốn chúng tiếp tục sẵn sàng chờ task, nhưng phải tính chi phí tài nguyên của ba thread đó.

**`@Bean` không yêu cầu `@EnableAsync` chỉ để tạo pool.** `@EnableAsync` chỉ cần khi muốn kích hoạt hạ tầng `@Async` theo cách cấu hình tương ứng. Nếu dùng `@Async("workerExecutor")`, cần chỉ định đúng bean tên `workerExecutor` và gọi method qua proxy của Spring; lời gọi nội bộ trong cùng bean ở proxy mode mặc định không được intercept.

### Dùng TaskExecutor tại một service

Tệp **JobDispatcher.java** (mã tích hợp với Spring):

~~~java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class JobDispatcher {
    private final TaskExecutor executor;

    public JobDispatcher(@Qualifier("workerExecutor") TaskExecutor executor) {
        this.executor = executor;
    }

    public void dispatch(String jobId) {
        executor.execute(() -> process(jobId));
    }

    private void process(String jobId) {
        System.out.println("Processing " + jobId);
    }
}
~~~

Mẫu minh họa cách submit task, **không hứa rằng công việc đã hoàn thành khi `dispatch()` trả về**. Đoạn `process` chỉ in ra log để học về luồng thực thi. Trong ứng dụng thật, cần xử lý exception, trạng thái job, idempotency, shutdown và bảo đảm bền vững nếu yêu cầu nghiệp vụ cần.

### Trường hợp tự tạo executor ngoài Spring

Nếu dùng `new ThreadPoolTaskExecutor()` bên ngoài container, bạn phải quản lý initialize và shutdown:

~~~java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(10);
executor.setPrestartAllCoreThreads(true);

executor.initialize();
try {
    // executor đã được khởi tạo, có thể submit công việc.
} finally {
    executor.shutdown();
}
~~~

Đây là **mẫu lifecycle rút gọn** dùng trong method/test có thư viện Spring, không phải một chương trình độc lập. Không gọi `initialize()` lặp lại trên một bean đã được Spring quản lý chỉ vì muốn warm-up lần nữa.

## 7. Nếu pool đã khởi tạo rồi thì sao?

Với Spring, nên bật setter trước khi khởi tạo. Nếu cần chủ động prestart **sau** khi `ThreadPoolTaskExecutor` đã khởi tạo, có thể truy cập JDK executor nền:

~~~java
ThreadPoolExecutor nativePool = springExecutor.getThreadPoolExecutor();
int newlyStarted = nativePool.prestartAllCoreThreads();
~~~

Đây là **đoạn minh họa** trong method có biến `springExecutor` kiểu `ThreadPoolTaskExecutor` đã được initialize. Nếu chưa khởi tạo, `getThreadPoolExecutor()` không trả về pool đã sẵn sàng để gọi. Không nên bật setter sau khi khởi tạo rồi mặc nhiên kỳ vọng các thread mới xuất hiện ngay; setter chủ yếu điều khiển hành vi tại thời điểm khởi tạo. Gọi trực tiếp API JDK khi thật sự cần thực hiện ngay trên pool hiện tại.

Nếu muốn tăng `corePoolSize` ở runtime rồi tạo sẵn phần core mới, cần xác định bạn sở hữu cấu hình pool đó, đảm bảo lifecycle còn hợp lệ, và đo tác động lên bộ nhớ/số thread. Tùy trạng thái queue, một thay đổi `corePoolSize` có thể tự khiến pool tạo worker để xử lý việc chờ; không nên giả định chỉ có lời gọi prestart mới làm số thread tăng.

Việc trực tiếp lấy native pool khiến code gắn chặt hơn với `ThreadPoolTaskExecutor`, vì không phải mọi `TaskExecutor` đều dựa trên `ThreadPoolExecutor`. Ưu tiên cấu hình Spring khi yêu cầu chỉ là “core worker sẵn sàng từ startup”.

## 8. Tương tác với queueCapacity, maxPoolSize và core timeout

### queueCapacity quyết định hành vi mở rộng

Spring `ThreadPoolTaskExecutor` dùng queue có sức chứa khi `queueCapacity > 0` và dùng `SynchronousQueue` khi `queueCapacity <= 0`. Với queue dương, pool thường cho task vào queue sau khi đã đạt core; chỉ khi queue đầy mới xem xét tăng worker tới `maxPoolSize`.

Vì vậy:

- `core=3, max=6, queue=50, prestart=true`: lúc startup tạo khoảng ba core worker. Với tải đến dần và queue vẫn nhận được, có thể chưa bao giờ dùng tới sáu worker.
- `core=3, max=6, queue=0, prestart=true`: không có vùng buffer thông thường; khi các core worker đã bận và không thể giao trực tiếp việc mới, pool có thể tăng tới sáu worker rồi từ chối nếu hết chỗ.
- `core=0, max=6, queue=50, prestart=true`: không có core worker nào để prestart. Cấu hình này cần được hiểu rõ nếu muốn worker sẵn sàng trước.

Prestart không tự làm thay đổi queue hoặc `maximumPoolSize`. Nó chỉ dời thời điểm tạo **các core worker**.

### allowCoreThreadTimeOut(true) có thể làm core worker biến mất

Theo mặc định, core worker không tự chết chỉ do idle. Nếu bật `allowCoreThreadTimeOut(true)` và `keepAliveTime > 0`, core worker cũng có thể kết thúc sau một khoảng chờ không có việc. Kết quả:

1. Startup gọi prestart tạo worker.
2. Ứng dụng yên lặng đủ lâu, core worker có thể timeout và rời pool.
3. Đợt task tiếp theo lại cần tạo worker mới.

Đây không phải lỗi của prestart. Hai cấu hình phục vụ mục tiêu khác nhau: prestart muốn giảm chi phí ở đợt đầu; core timeout muốn giảm tài nguyên khi nhàn rỗi. Có thể kết hợp, nhưng lợi ích giảm latency cho những đợt về sau sẽ không ổn định nếu các worker đã bị thu hồi.

### Số thread thật phụ thuộc ThreadFactory và môi trường

`ThreadPoolExecutor` lấy worker từ `ThreadFactory`. ThreadFactory có thể đặt tên, mức ưu tiên, uncaught exception handler hoặc tạo các loại thread phù hợp; nếu không thể cấp thread, số tạo được có thể không đạt kỳ vọng. Cạn tài nguyên native thread/memory hoặc shutdown cũng ảnh hưởng việc khởi động. `prestartAllCoreThreads()` chỉ trả về số đã tạo trong lần gọi, không cam kết mọi lần đều bằng `corePoolSize`.

Việc chờ worker chuyển sang trạng thái thực sự sẵn sàng nhận task vẫn phụ thuộc scheduling. Không dùng `getPoolSize() == corePoolSize` như một hàng rào đồng bộ để xác nhận mọi dependency bên trong tác vụ đã sẵn sàng.

## 9. Prestart mang lại lợi ích gì và phải trả giá gì?

### Lợi ích có thể có

- Giảm một phần độ trễ submit/task đầu tiên do **tạo Thread object và native thread** khi đợt việc đến.
- Làm chi phí tạo core worker xuất hiện sớm và dễ quan sát hơn trong giai đoạn khởi động.
- Hữu ích cho pool cố định, service có đợt tải đầu tiên nhạy với latency hoặc pipeline bắt đầu nhận một loạt job ngay khi ứng dụng được đánh dấu ready.

Lợi ích thực tế phụ thuộc từng ứng dụng và có thể rất nhỏ. Thread creation thường chỉ là một phần tổng thời gian xử lý, bên cạnh I/O, TLS, database, GC, lock và logic nghiệp vụ.

### Chi phí và rủi ro

- Tăng thời gian/chi phí startup do phải tạo đủ core worker trước khi có công việc thực.
- Giữ tài nguyên của thread đang idle (stack, native handle, metadata và những cấu trúc liên quan); đáng kể nếu nhiều bean/replica cùng có core lớn.
- Làm concurrency xuất hiện sớm ở quá trình khởi động, gây tương tác với ThreadFactory, monitoring hoặc cấu hình lifecycle.
- Không giải quyết queue đầy, rejection, starvation, deadlock, lỗi tác vụ hoặc yêu cầu xử lý xuyên nhiều replica.
- Nếu bean chỉ được dùng rất hiếm, prestart có thể tạo tài nguyên mà ứng dụng gần như không sử dụng.

**Không dùng prestart để “tăng throughput” nếu không có số liệu.** Khi pool đã được warm theo tải thực tế, số core/max worker và thời gian xử lý task quyết định năng lực song song nhiều hơn việc worker được tạo sớm một vài mili giây.

## 10. Prestart không phải application warm-up

Có bốn thao tác thường bị nhầm:

| Thao tác | Tạo sẵn worker? | Chạy code nghiệp vụ? | Bảo đảm |
| --- | --- | --- | --- |
| `prestartAllCoreThreads()` | Có, với core worker còn thiếu | Không | Khởi động worker để chờ việc, trả số worker tạo mới |
| Gửi task warm-up qua `execute` | Có thể, theo nhu cầu pool | Có, chạy task đã gửi | Phụ thuộc completion, lỗi và cách chờ kết quả |
| Warm-up JIT/cache/connection pool | Phụ thuộc cách implement | Có thể | Chỉ những thành phần được warm thực tế |
| Tăng `corePoolSize` hoặc `maxPoolSize` | Có thể tạo thêm worker theo trạng thái | Không tự chạy task nghiệp vụ | Tăng mức cấu hình năng lực; không tự cải thiện latency |

Ví dụ, nếu task đầu phải kết nối tới database và tải một mô hình lớn, prestart chỉ loại bỏ chi phí tạo worker; việc kết nối database và tải dữ liệu vẫn diễn ra khi task chạy, trừ khi bạn chủ động warm-up chúng qua cơ chế khác.

Tương tự, `ThreadPoolTaskExecutor` mặc định dùng **platform thread** kiểu pool truyền thống. Virtual thread theo mô hình thread-per-task trong Java 21+ có cơ chế khác; không áp máy móc phương thức này lên một `TaskExecutor` không có core pool.

## 11. Phối hợp với vòng đời Spring và readiness

### Khởi tạo bean

Khi Spring khởi tạo bean `ThreadPoolTaskExecutor`, nó sử dụng cấu hình đã đặt và tiến hành khởi tạo executor. Bật `setPrestartAllCoreThreads(true)` thường khiến thread được tạo trong bước này. Nếu có nhiều bean pool khác nhau, mỗi pool có core riêng và tạo worker riêng; cần tính tổng số thread trước khi bật hàng loạt.

Trong ứng dụng có readiness probe, **bean được initialize không có nghĩa mọi dependency bên ngoài đã sẵn sàng**, và prestart cũng không chứng minh toàn bộ ứng dụng đã phục vụ được request. Phân biệt rõ:

- Executor được khởi tạo.
- Core worker tồn tại.
- Task đầu có thể được submit.
- Dependency, dữ liệu và hệ thống đã sẵn sàng cho thao tác nghiệp vụ.

Nếu ứng dụng cần bảo đảm dependency được kiểm tra trước khi ready, dùng health/readiness contract tương ứng. Không coi `getPoolSize() > 0` là health check duy nhất.

### Shutdown

Prestart không thay đổi hợp đồng shutdown: pool đã chuyển sang ngừng nhận việc không trở thành active trở lại chỉ bằng một lời gọi prestart. Khi đóng ApplicationContext, Spring quản lý vòng đời bean và cách xử lý task chờ/đang chạy theo cấu hình. Việc prestart các thread idle có thể khiến lúc shutdown cần dọn thêm worker, nhưng không phải lý do bỏ qua shutdown đúng cách.

Chỉ gọi `shutdown()`/`shutdownNow()` trên executor tự quản lý hoặc theo lifecycle được thiết kế. Không chủ động shutdown một bean được nhiều component chia sẻ ngay khi một request đã xong.

## 12. Cách quan sát và kiểm thử

### Theo dõi trạng thái

Sau khi Spring đã khởi tạo một `ThreadPoolTaskExecutor executor`, có thể đọc:

~~~java
int configuredCore = executor.getCorePoolSize();
int currentPool = executor.getPoolSize();
int activeTasks = executor.getActiveCount();
int queuedTasks = executor.getQueueSize();
~~~

Đây là **đoạn minh họa**, không phải source độc lập. Với một pool mới cấu hình `core=3`, prestart bật và chưa có task, trạng thái quan sát thường là `configuredCore=3`, `currentPool=3`, `activeTasks=0`, `queuedTasks=0`. Các giá trị runtime có thể đổi ngay khi có submit đồng thời; không dùng chúng để tự tạo một giao thức quyết định nghiệp vụ nhạy race.

Nếu cần metrics trong Spring Boot, có thể dùng Micrometer/Actuator tùy cấu hình thực tế. Theo dõi pool size, active count, queue size, số rejection và phân phối latency, nhưng tránh kết luận “warm-up tốt” chỉ từ một snapshot số worker.

### Kiểm tra tác động latency

Một thí nghiệm công bằng nên:

1. Tạo hai pool tương đương về core, max, queue, ThreadFactory và loại công việc; chỉ khác prestart.
2. Đo **startup time**, thời gian từ submit tới lúc task đầu bắt đầu, rồi đo cả thời gian hoàn tất.
3. Thử cả đợt đầu sau startup và đợt sau khi pool đã xử lý công việc.
4. Lặp nhiều lần, kiểm soát JIT/GC/hệ điều hành, đo p50/p95/p99, và so sánh chi phí bộ nhớ/số thread.
5. Lặp dưới tải thực tế với queue và tác vụ có độ dài tương tự sản xuất.

Không dùng `Thread.sleep(...)` để chứng minh thread đã sẵn sàng. Nếu cần phối hợp task và đo thời điểm, sử dụng `CountDownLatch`, `Phaser` hoặc các primitive đồng bộ phù hợp. Đo đạc không cần tự dàn dựng race bằng cách chờ một số mili giây tùy ý.

### Kiểm tra cơ chế trong unit test

Với `ThreadPoolExecutor` cấu hình core là 2, không submit task nào:

~~~java
ThreadPoolExecutor pool = new ThreadPoolExecutor(
        2, 4, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(4));
try {
    if (pool.getPoolSize() != 0) {
        throw new AssertionError("Expected a fresh pool");
    }
    int newlyStarted = pool.prestartAllCoreThreads();
    if (newlyStarted != 2 || pool.getPoolSize() != 2) {
        throw new AssertionError("Core threads were not prestarted");
    }
} finally {
    pool.shutdownNow();
}
~~~

Đây là **mã minh họa đặt trong method**, cần import `java.util.concurrent.*`. Với factory mặc định và không có tác nhân đồng thời, assert này có thể xác định được. Test nên đóng executor trong `finally` để tránh giữ non-daemon worker thread làm quy trình test treo.

Với Spring bean, có thể inject đúng `ThreadPoolTaskExecutor`, kiểm tra cấu hình và pool size sau initialization. Không gọi `initialize()` lần nữa trên bean đang chạy chỉ để test số worker. Nếu muốn kiểm thử đường `@Async`, kiểm tra cả việc lời gọi đi qua proxy và cách task hoàn tất bằng `Future`/`CompletableFuture` hoặc latch.

## 13. Những nhầm lẫn và lỗi thường gặp

1. **Nhầm `corePoolSize=5` với “ngay lập tức có 5 thread”.** Mặc định worker khởi tạo theo nhu cầu; prestart mới yêu cầu tạo trước.
2. **Nhầm prestart với submit năm task.** Prestart chỉ làm worker chờ, không chạy `Runnable` do người dùng cung cấp.
3. **Dùng `maximumPoolSize` để suy ra số prestart.** Chỉ core worker còn thiếu được tạo trước; max không phải số cần tạo.
4. **Gọi prestart nhiều lần và nghĩ mỗi lần tạo thêm core mới.** Khi đã đủ core, lần gọi tiếp trả 0.
5. **Bật `setPrestartAllCoreThreads(true)` sau initialize rồi mặc định nghĩ có hiệu lực ngay.** Với pool đã hoạt động, dùng JDK method trên native pool nếu thực sự cần thực thi tức thì.
6. **Không gọi `initialize()` trên `ThreadPoolTaskExecutor` tự tạo ngoài container.** Setter chỉ cấu hình; chưa khởi tạo thì chưa có native executor để dùng.
7. **Tưởng bật prestart khiến max pool được dùng sớm hơn.** Queue vẫn quyết định đường mở rộng sau core.
8. **Dùng queue gần như vô hạn, max rất lớn rồi mong prestart giải quyết backlog.** Prestart không giải quyết tăng trưởng hàng đợi hoặc thiếu backpressure.
9. **Bật core timeout rồi kỳ vọng core worker luôn tồn tại.** Các worker có thể kết thúc sau thời gian idle.
10. **Tạo quá nhiều core worker trên mỗi replica.** Tài nguyên CPU, bộ nhớ và native thread đều bị chia sẻ; nhân số lượng pool với số lượng replica.
11. **Xem `getPoolSize()` như số thread active.** Chỉ số hoạt động có API riêng và đều có tính snapshot/ước lượng.
12. **Dùng thời gian lần chạy đầu làm bằng chứng tuyệt đối.** JIT, cache, disk, mạng, hệ điều hành và thread startup cùng ảnh hưởng latency.
13. **Cố dùng phương thức JDK trên mọi `TaskExecutor`.** Không phải mọi executor Spring đều chứa `ThreadPoolExecutor`; ví dụ executor dựa trên virtual thread có cơ chế khác.
14. **Bỏ quên cleanup trong demo hoặc test.** Core worker có thể tiếp tục sống và giữ JVM nếu không shutdown.
15. **Xem prestart là readiness của toàn hệ thống.** Worker sẵn sàng nhận task không chứng minh database hoặc service bên ngoài đã healthy.

## 14. Hướng dẫn ra quyết định

Chỉ bật prestart khi có nhu cầu rõ ràng. Một cách phân tích thực tế:

| Bối cảnh | Đề xuất | Lý do |
| --- | --- | --- |
| Dịch vụ nhận đợt tải quan trọng ngay sau startup, core nhỏ và cố định | Thử `prestartAllCoreThreads`, đo latency trước/sau | Có thể loại bớt chi phí tạo thread của đợt đầu |
| Batch worker dài hạn, luôn có task | Có thể không cần | Sau vài task, core worker thường đã được tạo |
| Dịch vụ hiếm khi chạy tác vụ, nhiều replica | Cân nhắc giữ lazy | Tránh tạo nhiều thread idle |
| Task đầu chậm vì kết nối DB/JIT/cache | Prestart đơn lẻ không giải quyết | Cần warm-up đúng dependency và work path |
| Queue đầy / từ chối do quá tải | Ưu tiên capacity planning và backpressure | Prestart không tăng giới hạn nhận việc |
| Muốn giảm tài nguyên khi idle và dùng `allowCoreThreadTimeOut(true)` | Đánh giá lại mục tiêu warm | Core worker tạo sớm có thể mất đi sau khi idle |
| Executor chạy theo kiểu virtual-thread-per-task | Không dùng trực tiếp công thức core pool | Cơ chế tạo thread khác với ThreadPoolExecutor |

Trong ứng dụng Spring, cấu hình ban đầu nên ngắn gọn và có chủ đích: định rõ core/max, queue hữu hạn khi phù hợp, thread name, chính sách rejection, vòng đời và monitoring. Sau đó thử prestart riêng biệt, đo độ trễ thật trước khi quyết định giữ.

## 15. Câu hỏi tự kiểm tra

1. Một `ThreadPoolExecutor(core=4,max=10)` vừa được tạo và chưa submit task. `getCorePoolSize()` và `getPoolSize()` có nhất thiết cùng bằng 4 không?
2. Vì sao `prestartAllCoreThreads()` trả về 0 ở lần thứ hai vẫn có thể là kết quả hoàn toàn đúng?
3. Một pool đã có hai worker, `corePoolSize=5`. Trong điều kiện bình thường, `prestartAllCoreThreads()` có thể trả về bao nhiêu?
4. `setPrestartAllCoreThreads(true)` của Spring khác gì `prestartAllCoreThreads()` của JDK về thời điểm áp dụng và giá trị trả về?
5. Vì sao prestart ba worker không khiến `maximumPoolSize=20` lập tức tạo 20 thread?
6. Nếu bật `allowCoreThreadTimeOut(true)` và service nhàn rỗi trong thời gian dài, lợi ích prestart có thể thay đổi thế nào?
7. Làm thế nào để kiểm tra prestart có thực sự giúp p99 của lượt request đầu tiên mà không nhầm với thời gian kết nối database?
8. Tại sao một pool có ba idle worker không chứng minh ứng dụng đã sẵn sàng phục vụ toàn bộ chức năng nghiệp vụ?

## 16. Tài liệu tham khảo chính thức

- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java SE 17 — ThreadFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadFactory.html)
- [Spring Framework 6.1 — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.1/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)

**Ghi nhớ:** `prestartAllCoreThreads()` là công cụ **khởi tạo sẵn core worker**. Nó có thể đổi thời gian tạo thread để tối ưu độ trễ đợt đầu, nhưng không thay đổi cấu hình sức chứa, không thực thi warm-up nghiệp vụ và không thay thế thiết kế pool/queue/rejection/shutdown đúng đắn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-009">Đặt tên luồng bằng threadNamePrefix hoặc ThreadFactory</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt tên luồng bằng threadNamePrefix hoặc ThreadFactory

## 1. Vì sao cần đặt tên luồng?

Trong ứng dụng Spring có nhiều nguồn công việc chạy đồng thời, tên luồng (**thread name**) là một dấu hiệu chẩn đoán rất hữu ích. Khi đọc log, thread dump hoặc kết quả theo dõi hiệu năng, tên như `http-nio-8080-exec-5`, `order-worker-2`, `report-scheduler-1` giúp người vận hành nhận ra **nhóm tác vụ** và **pool** đang xử lý, thay vì chỉ thấy các tên mặc định kiểu `pool-3-thread-1`.

Tên luồng trả lời câu hỏi **"mã đang chạy trên loại worker nào?"**, nhưng không tự trả lời **"request nào?"**, **"task nào?"**, **"user nào?"** hoặc **"công việc đã thành công hay chưa?"**. Một luồng trong thread pool có thể xử lý nhiều task khác nhau trong suốt vòng đời của nó.

Trong chủ đề **Nhóm luồng** của Spring concurrency, có hai cách đặt tên phổ biến:

- **`setThreadNamePrefix(...)`** trên các executor/scheduler do Spring cung cấp, ví dụ `ThreadPoolTaskExecutor`, `ThreadPoolTaskScheduler`, `SimpleAsyncTaskExecutor`. Đây thường là lựa chọn ngắn gọn khi chỉ cần prefix dễ nhận biết.
- **`ThreadFactory`** của `java.util.concurrent`: cung cấp một chính sách tạo **Thread**. Dùng khi làm việc trực tiếp với JDK ExecutorService/ThreadPoolExecutor hoặc cần tùy chỉnh quá trình tạo thread, như format tên, daemon, priority, uncaught exception handler, hay kết hợp với một runtime đã có factory.

Ví dụ cách đọc log:

~~~text
2026-10-29 09:00:00 INFO  [report-scheduler-1] Trigger report #42
2026-10-29 09:00:00 INFO  [report-worker-2]    Start report #42
2026-10-29 09:00:02 INFO  [report-worker-2]    Complete report #42
~~~

Đây là **log minh họa**, không phải output được đảm bảo tự xuất hiện khi cấu hình tên luồng. Tên `report-scheduler-` cho thấy nguồn thực thi nhiệm vụ lập lịch, còn `report-worker-` cho thấy pool làm công việc nền. Cần có logger hoặc lệnh in thích hợp mới thấy các dòng như trên.

## 2. Cần hiểu Thread, task, executor và ThreadFactory khác nhau thế nào

| Khái niệm | Vai trò | Có giữ tên luồng không? |
| --- | --- | --- |
| `Runnable` / `Callable<T>` | Mô tả một công việc cần thực thi | Không; cùng task có thể được thực thi bởi worker do executor chọn |
| `Thread` | Luồng Java thực hiện mã | Có tên, lấy bằng `Thread.currentThread().getName()` |
| `ThreadFactory` | Tạo đối tượng Thread mới theo yêu cầu của executor | Có thể đặt tên, daemon, priority và các thuộc tính khác lúc tạo |
| `Executor` / `TaskExecutor` | Nhận công việc rồi quyết định cách thực thi | Có thể sử dụng ThreadFactory và tái sử dụng các thread |
| `ThreadPoolTaskExecutor` | Bean Spring quản lý một ThreadPoolExecutor | Có thể đặt `threadNamePrefix` hoặc cấu hình factory |
| `ThreadPoolTaskScheduler` | Bean Spring quản lý ScheduledThreadPoolExecutor | Có thể đặt `threadNamePrefix` cho scheduler threads |

**ThreadFactory tạo thread, không tạo task cho từng lần submit.** Nếu pool đang có worker rảnh thì task mới có thể chạy trên worker cũ mà không gọi `ThreadFactory.newThread(...)` nữa. Vì vậy một task ID không nên được nhét cố định vào tên thread khi tạo, vì tên ấy sẽ tồn tại và có thể bị dùng lại cho task tiếp theo.

Luồng có thể quan sát tên hiện tại:

~~~java
String name = Thread.currentThread().getName();
System.out.println("Thread = " + name);
~~~

Đây chỉ là một mảnh code minh họa. Khi chạy ở các môi trường khác nhau, tên thực tế phụ thuộc runtime và executor.

## 3. Cách 1 — dùng threadNamePrefix trong Spring

### 3.1. Đặt prefix cho ThreadPoolTaskExecutor

`ThreadPoolTaskExecutor` là triển khai TaskExecutor phổ biến của Spring. Trong một bean configuration:

~~~java
@Configuration
public class ExecutorConfig {
    @Bean("orderExecutor")
    public ThreadPoolTaskExecutor orderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("order-worker-");
        return executor;
    }
}
~~~

Đoạn trên là **mảnh cấu hình Spring** cần import `@Configuration`, `@Bean`, `ThreadPoolTaskExecutor` và có mặt trong ApplicationContext. Spring gọi lifecycle initialization của bean, do đó thường không cần gọi `initialize()` thủ công khi bean được tạo như trên. Nếu tự `new ThreadPoolTaskExecutor()` **ngoài** container, phải quản lý khởi tạo và shutdown thích hợp.

Khi pool tạo worker, tên được sinh từ prefix và hậu tố chỉ số, ví dụ `order-worker-1`, `order-worker-2`. **Không nên viết nghiệp vụ phụ thuộc chỉ số**: pool có thể tạo thêm/thay worker, và thứ tự các log có thể khác giữa những lần chạy.

Nếu dùng `@Async`:

~~~java
@Service
public class OrderWorker {
    @Async("orderExecutor")
    public CompletableFuture<String> process() {
        String thread = Thread.currentThread().getName();
        return CompletableFuture.completedFuture(thread);
    }
}
~~~

Đây là mảnh code minh họa cần import và `@EnableAsync`. Khi một bean khác gọi method công khai thông qua **Spring proxy**, phần thân `process()` được gửi đến bean `orderExecutor`. Nhờ vậy tên thread thường có prefix `order-worker-`.

Nếu gọi `this.process()` từ chính bean, lời gọi thường **bỏ qua proxy** trong mode mặc định. Khi đó việc quan sát một thread không có prefix **không nhất thiết là lỗi prefix**: trước tiên hãy kiểm tra ranh giới proxy và `@EnableAsync`.

### 3.2. Đặt prefix cho ThreadPoolTaskScheduler

Đối với công việc theo lịch, cấu hình một scheduler riêng:

~~~java
@Bean("taskScheduler")
public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("schedule-");
    return scheduler;
}
~~~

Ví dụ minh họa này cần đặt trong `@Configuration` với import tương ứng. Những method `@Scheduled` thường được kích hoạt bởi TaskScheduler, và `ThreadPoolTaskScheduler` truyền thống chạy callback trên scheduler thread; do đó prefix `schedule-` sẽ giúp nhận ra các lần kích hoạt lịch.

**`TaskScheduler` và `TaskExecutor` không tự chia sẻ cùng một pool.** Nếu method `@Scheduled` chỉ gọi một service `@Async("orderExecutor")` rồi trả về, có thể thấy hai prefix khác nhau trên cùng hành trình nghiệp vụ:

~~~text
schedule-1       -> đến giờ, kích hoạt công việc
order-worker-3   -> thực thi phần xử lý được gửi sang @Async
~~~

Đừng gán `order-worker-` cho scheduler chỉ vì lịch đó tạo ra công việc của nhóm order. Mục tiêu của tên là **phản ánh pool thực sự tạo thread**, từ đó dễ tìm nơi nghẽn.

### 3.3. Prefix áp dụng ở thời điểm tạo thread

`setThreadNamePrefix` định cấu hình cách đặt tên **cho thread mới được tạo**. Cách làm nhất quán là đặt giá trị khi tạo bean, **trước lúc executor/scheduler bắt đầu xử lý task**. Đổi prefix lúc pool đã có worker không bảo đảm đổi tên các worker sẵn có.

Việc đặt tên **không thay đổi** corePoolSize, maxPoolSize, queueCapacity, policy từ chối công việc, khả năng giao dịch hay tính thread-safe của service. Prefix phục vụ quan sát và vận hành, không phải cơ chế bảo vệ chia sẻ dữ liệu.

## 4. Cách 2 — dùng ThreadFactory của Java

`java.util.concurrent.ThreadFactory` là functional interface:

~~~java
@FunctionalInterface
public interface ThreadFactory {
    Thread newThread(Runnable runnable);
}
~~~

Đây là chữ ký interface để giải thích, không cần tự khai báo lại trong chương trình đang dùng JDK. Khi cần tạo worker, một executor thường gọi `newThread(Runnable)`. Factory phải tạo **Thread mới, chưa khởi chạy**, và trả lại cho executor quản lý việc start/execute.

Một implementation thường có ba phần:

1. Một **prefix mô tả chức năng** (`batch-import-`).
2. Một bộ đếm **AtomicInteger** để cấp hậu tố riêng cho những thread do factory này tạo ra.
3. Một factory mặc định hoặc lời gọi `new Thread(task)` để tạo thread, rồi đặt tên trước khi trả về.

Ví dụ độc lập dùng Java 17:

~~~java
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class NamedThreadFactory implements ThreadFactory {
    private final ThreadFactory delegate = Executors.defaultThreadFactory();
    private final AtomicInteger sequence = new AtomicInteger();
    private final String prefix;

    public NamedThreadFactory(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable task) {
        Thread thread = delegate.newThread(task);
        thread.setName(prefix + sequence.incrementAndGet());
        return thread;
    }
}
~~~

Class này **biên dịch được nếu lưu trong `NamedThreadFactory.java`**, nhưng chưa chứa `main()` để chạy độc lập. Khi dùng `Executors.defaultThreadFactory()` làm delegate, ta giữ các đặc tính mặc định của JDK như thread nền tảng (platform thread), non-daemon và priority thông thường, rồi chỉ đổi tên. Trong môi trường container/managed runtime, chính sách thread có thể cần factory được runtime cấp; không tự ý thay đổi daemon hay priority khi chưa hiểu các ràng buộc.

Sử dụng với JDK ExecutorService:

~~~java
ExecutorService executor =
        Executors.newFixedThreadPool(3, new NamedThreadFactory("batch-import-"));

try {
    Future<String> result =
            executor.submit(() -> Thread.currentThread().getName());
    System.out.println(result.get());
} finally {
    executor.shutdown();
}
~~~

Mảnh code trên cần các import `java.util.concurrent.*` và class `NamedThreadFactory` trước đó. Tên có thể là `batch-import-1`, nhưng không được dựa vào con số cụ thể vì thời điểm khởi tạo/reuse worker có thể thay đổi.

### 4.1. ThreadFactory có thể làm nhiều hơn đặt tên

Một ThreadFactory tùy chỉnh có thể gán:

- **Tên** qua `Thread.setName(...)`.
- **Daemon status** qua `Thread.setDaemon(...)` trước khi start.
- **Priority** qua `Thread.setPriority(...)` nếu cần và runtime cho phép.
- **UncaughtExceptionHandler** qua `Thread.setUncaughtExceptionHandler(...)` cho exception không được bắt ở ranh giới Thread.
- **ThreadGroup** hoặc đặc tính thread khác khi thực sự có nhu cầu.

Nhưng các hành vi này có tác động vận hành: daemon thread không ngăn JVM kết thúc; priority là **gợi ý** cho scheduler, không phải SLA; exception của task gửi qua `submit()` thường được ghi trong `Future` thay vì luôn đi tới handler ở mức Thread. Vì vậy **đừng dùng UncaughtExceptionHandler như cách duy nhất quan sát lỗi async task**.

### 4.2. Dùng Spring CustomizableThreadFactory

Spring cũng cung cấp `org.springframework.scheduling.concurrent.CustomizableThreadFactory`, là một implementation của ThreadFactory với các tùy chọn từ `CustomizableThreadCreator`:

~~~java
ThreadFactory factory =
        new CustomizableThreadFactory("archive-");

ExecutorService executor = Executors.newFixedThreadPool(2, factory);
~~~

Đây là mảnh code cần import tương ứng và đóng executor khi không dùng nữa. Lựa chọn này phù hợp nếu muốn đặt prefix dễ dàng ngay cả khi đang tạo một JDK ExecutorService, nhưng đã dùng Spring Framework làm dependency.

### 4.3. Cấp ThreadFactory cho executor Spring

Các executor thuộc họ `ExecutorConfigurationSupport` như `ThreadPoolTaskExecutor` và `ThreadPoolTaskScheduler` trong Spring 6.1 cho phép cấu hình **`setThreadFactory(ThreadFactory)`**:

~~~java
@Bean("batchExecutor")
ThreadPoolTaskExecutor batchExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(50);
    executor.setThreadFactory(new CustomizableThreadFactory("batch-"));
    return executor;
}
~~~

Đây là mảnh cấu hình cần khai báo trong class `@Configuration`, cùng các import. **Khi đã cấp một ThreadFactory riêng, factory ấy chịu trách nhiệm cách tạo/đặt tên thread.** Đừng kỳ vọng `setThreadNamePrefix` trên executor tự động được ghép tiếp với một custom factory bất kỳ. Nếu chỉ cần đổi tên, ưu tiên cấu hình `setThreadNamePrefix` và tránh thêm tầng factory không cần thiết.

## 5. So sánh trực tiếp: threadNamePrefix và ThreadFactory

| Tiêu chí | `setThreadNamePrefix("order-")` | `ThreadFactory` tùy chỉnh |
| --- | --- | --- |
| Mức abstraction | Thuộc cấu hình executor/scheduler Spring | Chính sách tạo thread của Java |
| Độ phức tạp | Rất ít mã; thường chỉ một setter | Cần implementation/lambda và quản lý các thuộc tính thread |
| Mục tiêu chính | Dễ nhận diện worker theo pool | Kiểm soát chi tiết khi một Thread mới được tạo |
| Áp dụng cho | Những implementation Spring có setter tương ứng | JDK executors, hoặc Spring executors hỗ trợ custom factory |
| Suffix chỉ số | Do triển khai Spring tạo theo quy tắc nội bộ | Do chính factory định nghĩa; nên dùng counter an toàn đồng thời |
| Tùy chỉnh daemon/priority/handler | Có thể dùng API khác tùy implementation; prefix chỉ đổi tên | Có thể thực hiện trực tiếp trong factory |
| Phù hợp nhất | App Spring, yêu cầu quan sát theo chức năng | Kết hợp JDK executor, yêu cầu đặt tên đặc thù hoặc môi trường có factory riêng |

Có thể xem `threadNamePrefix` là **cấu hình tiện lợi** cho một phần nhu cầu mà ThreadFactory tổng quát hơn đáp ứng. Hai cách đều đặt tên cho **thread**, không đặt tên cho mỗi **task**.

## 6. Ví dụ hoàn chỉnh: Spring TaskExecutor, @Async, TaskScheduler và JDK ThreadFactory

Bài tập dùng **Java 17+, Spring Framework 6.1.14, Gradle 8.5+**. Nó in những phép kiểm tra boolean để output có thể dự đoán chính xác, thay vì phụ thuộc chỉ số worker ngẫu nhiên.

Cấu trúc:

~~~text
thread-naming-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/ThreadNamingDemo.java
~~~

### settings.gradle

~~~groovy
rootProject.name = 'thread-naming-demo'
~~~

### build.gradle

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
    mainClass = 'demo.ThreadNamingDemo'
}
~~~

### src/main/java/demo/ThreadNamingDemo.java

~~~java
package demo;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public final class ThreadNamingDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            // 1. TaskExecutor do Spring quản lý.
            var orderExecutor = context.getBean(
                    "orderExecutor", ThreadPoolTaskExecutor.class);
            String directName = orderExecutor.submit(
                    () -> Thread.currentThread().getName())
                    .get(5, TimeUnit.SECONDS);
            System.out.println("Spring TaskExecutor: " +
                    directName.startsWith("order-"));

            // 2. @Async gọi qua proxy của Spring.
            String asyncName = context.getBean(Worker.class)
                    .process().get(5, TimeUnit.SECONDS);
            System.out.println("Spring @Async: " +
                    asyncName.startsWith("order-"));

            // 3. ThreadPoolTaskScheduler dùng prefix riêng.
            var scheduler = context.getBean(
                    "taskScheduler", ThreadPoolTaskScheduler.class);
            var scheduledName = new CompletableFuture<String>();
            scheduler.schedule(
                    () -> scheduledName.complete(
                            Thread.currentThread().getName()),
                    Instant.now().plusMillis(50));
            String actualSchedulerName =
                    scheduledName.get(5, TimeUnit.SECONDS);
            System.out.println("Spring TaskScheduler: " +
                    actualSchedulerName.startsWith("schedule-"));

            // 4. JDK ExecutorService dùng ThreadFactory riêng.
            ExecutorService jdkPool = Executors.newFixedThreadPool(
                    2, new NamedFactory("import-"));
            try {
                String jdkName = jdkPool.submit(
                        () -> Thread.currentThread().getName())
                        .get(5, TimeUnit.SECONDS);
                System.out.println("JDK ThreadFactory: " +
                        jdkName.startsWith("import-"));
            } finally {
                jdkPool.shutdown();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class Config {

        @Bean("orderExecutor")
        public ThreadPoolTaskExecutor orderExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(2);
            executor.setQueueCapacity(10);
            executor.setThreadNamePrefix("order-");
            return executor;
        }

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("schedule-");
            return scheduler;
        }

        @Bean
        public Worker worker() {
            return new Worker();
        }
    }

    public static class Worker {
        @Async("orderExecutor")
        public CompletableFuture<String> process() {
            return CompletableFuture.completedFuture(
                    Thread.currentThread().getName());
        }
    }

    public static final class NamedFactory implements ThreadFactory {
        private final ThreadFactory delegate =
                Executors.defaultThreadFactory();
        private final AtomicInteger count = new AtomicInteger();
        private final String prefix;

        public NamedFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = delegate.newThread(task);
            thread.setName(prefix + count.incrementAndGet());
            return thread;
        }
    }
}
~~~

Để chạy từ thư mục `thread-naming-demo`:

~~~bash
gradle run
~~~

Nếu dự án đã có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows. Với điều kiện các task được chấp nhận và hoàn thành trước timeout năm giây, **bốn dòng do chương trình tự in** là:

~~~text
Spring TaskExecutor: true
Spring @Async: true
Spring TaskScheduler: true
JDK ThreadFactory: true
~~~

Log khởi động của Spring/Gradle có thể xuất hiện ngoài bốn dòng trên. Tên thread thực tế có hậu tố khác nhau nên ví dụ chỉ xác minh prefix. Nếu thiếu dependency, môi trường quá tải, task bị từ chối hoặc timeout, chương trình có thể báo lỗi thay vì in đủ bốn dòng; đó là tình huống vận hành cần kiểm tra.

Giải thích:

1. `orderExecutor.submit(...)` gửi một Callable tới pool của Spring. Tên thực thi bắt đầu bằng `order-`.
2. `Worker.process()` được gọi qua bean do Spring quản lý; `@EnableAsync` và `@Async("orderExecutor")` đưa phần thân method sang chính pool đó. Nếu gọi method bằng `this.process()` nội bộ, proxy thường không hoạt động.
3. `ThreadPoolTaskScheduler.schedule(...)` kích hoạt callback trên worker riêng có prefix `schedule-`.
4. `Executors.newFixedThreadPool(..., new NamedFactory("import-"))` yêu cầu factory cấp thread mới khi pool cần worker. Factory đặt tên `import-1`, `import-2`, ... theo bộ đếm riêng.
5. `Future.get` và `CompletableFuture.get` được dùng có chủ đích để **demo** đợi kết quả trước khi in. Trong nghiệp vụ thật, không nên đợi ngay tại caller nếu mục tiêu là xử lý bất đồng bộ mà không block.
6. `try` đóng `AnnotationConfigApplicationContext` giúp Spring xử lý vòng đời các bean executor/scheduler; `finally` gọi shutdown cho pool JDK được tạo thủ công.

## 7. Cấu hình tên luồng qua Spring Boot application.yml

Nếu dùng Spring Boot và để Boot tự cấu hình task executor/scheduler theo cơ chế auto-configuration, các property sau thường được dùng:

~~~yaml
spring:
  task:
    execution:
      thread-name-prefix: "app-async-"
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 100
    scheduling:
      thread-name-prefix: "app-schedule-"
      pool:
        size: 2
~~~

Đây là **cấu hình Spring Boot**; project Spring Framework thuần ở mục 6 không tự đọc những property này. Nó áp dụng cho các bean do cơ chế auto-configuration tương ứng tạo ra. Khi bạn **tự khai báo** `ThreadPoolTaskExecutor` và `ThreadPoolTaskScheduler`, đừng mặc định các property Boot sẽ tự chỉnh tên/size của mọi bean tùy chỉnh.

Trong ứng dụng có nhiều executors, có thể chọn tên theo chức năng ở từng bean:

~~~java
@Async("invoiceExecutor")
public CompletableFuture<Void> generateInvoice() {
    // Sử dụng pool invoiceExecutor đã được cấu hình.
    return CompletableFuture.completedFuture(null);
}
~~~

Đây là đoạn minh họa method, cần nằm trong Spring bean, có @EnableAsync, executor tương ứng và import. Tên bean `invoiceExecutor` dùng để **chọn pool**; tên thread thực tế dựa vào ThreadFactory/prefix của pool. **Tên bean và threadNamePrefix là hai cấu hình khác nhau**. Việc có bean tên `invoiceExecutor` không bảo đảm log xuất hiện `invoice-` nếu chưa kiểm tra factory.

Ngoài ra, trong Spring Boot quy tắc chọn executor mặc định cho `@Async`, Spring MVC, WebFlux và các tích hợp khác có thể phụ thuộc tên bean như `applicationTaskExecutor`, kiểu `AsyncTaskExecutor` và số bean trong context. Nếu cần biết chính xác pool nào chạy một method, hãy kiểm tra cả **đường gọi** và **cấu hình chọn bean**, thay vì chỉ đổi prefix của một pool không được sử dụng.

## 8. Đặt tên có ích cho log, thread dump và giám sát

### 8.1. Chọn một quy ước dễ đọc

Một format thực tế là:

~~~text
<domain-or-component>-<role>-<thread-sequence>
~~~

Ví dụ: `payment-io-3`, `billing-async-7`, `cleanup-schedule-1`.

Nên chọn prefix:

- **Ngắn, rõ vai trò**: `order-async-`, `report-schedule-`, `sync-worker-`.
- **Phân biệt pool**: khi có `@Async` và scheduler, đừng dùng cùng một prefix gây nhầm.
- **Ổn định trong một phiên bản ứng dụng**: nếu tên log đổi thường xuyên, dashboard hoặc bộ lọc thread dump sẽ khó sử dụng.
- **Không chứa dữ liệu nhạy cảm**: không nhúng email, access token, user id, session id hoặc payload vào tên thread. Thread được tái sử dụng và tên có thể bị log/thread dump lưu lại.
- **Không lệ thuộc vào hậu tố**: số đếm chỉ giúp phân biệt worker tại thời điểm quan sát, không phải khóa nghiệp vụ hay ID toàn cục.

Nếu chạy nhiều JVM/pod, hai thread ở hai instance có thể cùng tên `order-1`. Hãy dùng **service/instance/pod ID** ở trường log hoặc nhãn metrics thích hợp; không suy ra uniqueness trên toàn cluster từ threadNamePrefix.

### 8.2. Hiển thị tên thread trong log

Với Logback, pattern thường dùng `%thread` hoặc `%t` để hiển thị tên thread. Ví dụ một pattern minh họa:

~~~text
%d{HH:mm:ss.SSS} [%thread] %-5level %logger{30} - %msg%n
~~~

Khi một worker có tên `order-2`, dòng log có thể trông như:

~~~text
09:15:23.142 [order-2] INFO  OrderProcessor - Completed order
~~~

Tên worker chỉ nói **thread nào đang chạy**, không phải order nào. Để nối các log của một request/task, cần một **correlation ID**, trace/span ID hoặc MDC ở trường phù hợp. Khi chuyển sang `@Async`, MDC/ThreadLocal **không mặc nhiên lan truyền** sang thread mới; có thể cấu hình `TaskDecorator`, cơ chế context propagation của Spring/Micrometer hoặc wrapper thích hợp, đồng thời bảo đảm cleanup trên thread pool.

### 8.3. Kiểm tra trực tiếp trong chương trình

Trong một method được gọi bởi worker:

~~~java
System.out.printf("thread=%s%n",
        Thread.currentThread().getName());
~~~

Đây là cách xác nhận nhanh pool đang thực thi. Với hệ thống đang chạy, dùng thread dump (`jcmd <pid> Thread.print` trên JDK hỗ trợ) hoặc công cụ quan sát JVM để tìm thread theo prefix. Nếu một thread có tên `report-scheduler-1` bị block trong I/O lâu, điều đó gợi ý scheduler callback đang chiếm thời gian; nếu `report-worker-*` đều bận, xem xét backlog và concurrency của executor.

**Không nên để nghiệp vụ rẽ nhánh theo tên thread**, ví dụ `if (getName().startsWith("order-"))` để phân quyền, kiểm tra đã chạy async hay quyết định được phép truy cập database. Tên có thể đổi bằng cấu hình hoặc do thư viện tạo; nó không phải hợp đồng bảo mật.

## 9. Những lỗi triển khai dễ gặp

### 9.1. Đổi tên thread bằng Thread.currentThread().setName(...) ngay trong task

Ví dụ dễ gây sai lệch quan sát:

~~~java
executor.execute(() -> {
    Thread.currentThread().setName("processing-order-42");
    processOrder();
});
~~~

Đây là mảnh minh họa cần executor và method `processOrder()` được định nghĩa. Nếu worker được pool tái sử dụng và code không khôi phục tên ban đầu, **task tiếp theo có thể chạy trên thread vẫn mang tên order-42**. Log sẽ dẫn tới kết luận sai.

Nếu một yêu cầu đặc biệt thật sự cần đổi tên tạm thời, phải lưu tên cũ và khôi phục trong `finally`:

~~~java
String previous = Thread.currentThread().getName();
try {
    Thread.currentThread().setName(previous + "-temporary");
    // Công việc cần gắn nhãn tạm thời.
} finally {
    Thread.currentThread().setName(previous);
}
~~~

Tuy nhiên, thường tốt hơn dùng MDC/structured logging để ghi task ID và giữ thread name phản ánh **pool**. Đừng thay đổi tên worker vô cớ trong code nghiệp vụ.

### 9.2. Tạo ThreadFactory trả về cùng một Thread

Factory phải tạo **Thread mới** khi được yêu cầu. Trả về cùng một instance cho nhiều lần gọi có thể dẫn đến lỗi kiểu `IllegalThreadStateException` hoặc executor không khởi chạy được worker mới. Không tự `start()` bên trong `newThread()`: executor chịu trách nhiệm lúc bắt đầu chạy.

Không tạo một `new Thread(...)` cho mỗi request chỉ để đặt tên nếu nhiệm vụ thực tế cần **giới hạn concurrency, queue, lifecycle và shutdown**. Hãy dùng executor được cấu hình đúng.

### 9.3. Bộ đếm đặt tên không thread-safe

Trong ThreadFactory có thể dùng `AtomicInteger.incrementAndGet()` hoặc cơ chế đồng bộ tương đương. Dùng `int next++` không được bảo vệ có thể tạo tên trùng nếu factory bị nhiều thread gọi đồng thời. Tên trùng không nhất thiết làm executor hỏng, nhưng mất giá trị quan sát và dễ gây hiểu nhầm.

Cũng không suy ra chỉ vì tên luồng có hậu tố tăng liên tục thì task luôn được chạy đúng thứ tự gửi. Pool nhiều worker có thể hoàn thành không theo thứ tự.

### 9.4. Dùng prefix nhưng không kiểm tra đúng executor

Có thể đã cấu hình `batchExecutor.setThreadNamePrefix("batch-")`, nhưng method thực tế chạy bằng common pool, executor khác, hoặc scheduler. Ví dụ `CompletableFuture.supplyAsync(...)` không truyền executor thường dùng `ForkJoinPool.commonPool()`, không tự lấy Spring TaskExecutor của ứng dụng. Muốn dùng pool của bạn, **truyền executor rõ ràng**:

~~~java
CompletableFuture<String> future = CompletableFuture.supplyAsync(
        () -> Thread.currentThread().getName(),
        batchExecutor);
~~~

Đây là đoạn minh họa cần có `batchExecutor` tương thích `Executor`. Với `@Async`, dùng qualifier trên annotation và bảo đảm lời gọi qua proxy. Với `@Scheduled`, kiểm tra TaskScheduler được chọn. Với MVC Callable, Spring Batch hay Integration, kiểm tra điểm tích hợp riêng thay vì dựa vào một tên bean đoán trước.

### 9.5. Nhầm vấn đề naming với vấn đề concurrency

Một worker tên `single-` **không có nghĩa** pool chỉ có một thread; phải kiểm tra thực tế pool size. Prefix `priority-high-` cũng không tự nâng OS priority. Việc đặt tên không giải quyết race condition, starvation, task rejection, memory pressure hay transaction propagation.

Các thay đổi daemon/priority/uncaught handler qua ThreadFactory nên có lý do rõ ràng và kiểm thử, đặc biệt khi Spring quản lý lifecycle của pool.

## 10. Virtual threads và managed execution: lưu ý riêng

**JDK 17** trong ví dụ sử dụng platform threads. Trên **JDK 21+**, có thể dùng virtual threads và các cách tạo thread tương ứng; Spring Framework/Spring Boot phiên bản phù hợp cũng có lựa chọn hỗ trợ virtual threads. Các API tạo/factory và cơ chế quản lý concurrency ở môi trường virtual-thread có thể khác pool platform threads truyền thống. Không nên suy luận rằng mỗi virtual thread đều phải có một worker được tái sử dụng giống ThreadPoolExecutor.

Khi chọn tên cho virtual threads, vẫn phải cân nhắc correlation ID và observability: số lượng virtual threads có thể rất lớn; đặt một giá trị mang dữ liệu request vào tên thread không phải cách đáng tin để gắn kết toàn bộ log. Nếu một application server hoặc Jakarta EE container **quản lý việc tạo thread**, nên dùng managed executor/thread factory do môi trường cung cấp theo contract của nó, tránh tự tạo thread làm lệch lifecycle, context và chính sách vận hành.

Trong một ứng dụng có nhiều runtime, hãy xác định chính xác **ai sở hữu thread** trước khi chọn cấu hình tên. Đổi tên pool không đồng nghĩa sửa được cách thực thi của runtime khác.

## 11. Cách chọn giải pháp và checklist kiểm tra

**Chỉ cần log phân biệt các pool Spring:** ưu tiên `setThreadNamePrefix` trong bean `ThreadPoolTaskExecutor` hoặc `ThreadPoolTaskScheduler` (hoặc property Spring Boot khi dùng auto-configuration).

**Dùng một JDK ExecutorService riêng:** cung cấp `ThreadFactory` khi tạo pool qua `Executors.newFixedThreadPool(..., factory)` hoặc constructor thích hợp của `ThreadPoolExecutor`.

**Cần tên hoặc chính sách tạo thread đặc thù:** dùng ThreadFactory tùy chỉnh, ưu tiên delegate từ factory mặc định/managed factory phù hợp, chỉ thay những thuộc tính cần thiết. Với Spring executor hỗ trợ `setThreadFactory`, factory được cung cấp sẽ quyết định tên và thuộc tính thread.

Khi triển khai, kiểm tra lần lượt:

1. **Pool thực thi thật sự**: dòng code nào gửi task, bằng execute/submit, @Async proxy hay scheduler?
2. **Chỗ cấu hình có hiệu lực**: bean có được Spring tạo/khởi tạo không? Boot property có đang tác động đến auto-configured bean đúng không?
3. **Tên quan sát được**: log hoặc `Thread.currentThread().getName()` có prefix dự kiến không?
4. **Tính nhất quán**: các pool khác nhau có tiền tố đủ phân biệt; không mã hóa request/user ID vào tên worker.
5. **Điều kiện quá tải**: queue, core/max threads và chính sách rejection có phù hợp throughput không?
6. **Vòng đời**: executor do Spring quản lý hay do ứng dụng tự tạo, ai chịu trách nhiệm shutdown?
7. **Giám sát**: thread naming hỗ trợ đọc log/dump, nhưng metrics, trace ID và context propagation vẫn cần cấu hình riêng.

## 12. Câu hỏi tự kiểm tra

1. Vì sao hai lần `executor.submit(...)` có thể in cùng một thread name? **Vì pool tái sử dụng worker để chạy nhiều task.**
2. Một TaskExecutor đã có tên bean `paymentExecutor`, có chắc các worker bắt đầu bằng `payment-`? **Không; cần kiểm tra threadNamePrefix hoặc ThreadFactory thực tế.**
3. `setThreadNamePrefix("batch-")` có buộc pool tạo thread mới cho mỗi task không? **Không; chỉ ảnh hưởng tên thread được tạo, pool vẫn có thể tái sử dụng worker.**
4. Khi nào chọn ThreadFactory tùy chỉnh? **Khi dùng JDK executor hoặc cần chính sách tạo thread vượt quá prefix đơn giản.**
5. Vì sao không đổi tên worker thành order ID rồi để nguyên? **Task tiếp theo có thể tái sử dụng cùng worker và kế thừa tên gây nhầm.**
6. Nếu method `@Async("mailExecutor")` vẫn in tên HTTP thread, cần kiểm tra gì trước? **@EnableAsync, bean được Spring quản lý, self-invocation/proxy và executor thực tế.**
7. `@Scheduled` với `ThreadPoolTaskScheduler` có tự dùng prefix của `ThreadPoolTaskExecutor` không? **Không; scheduler được cấu hình/chọn riêng.**
8. Tên `worker-1` có duy nhất trên nhiều pod không? **Không; cần thêm instance/pod ID ở dữ liệu observability.**

**Ghi nhớ:** `threadNamePrefix` là cách cấu hình ngắn gọn cho các executor Spring; `ThreadFactory` là cơ chế tổng quát của Java để tạo và đặt tên thread. Cả hai phục vụ nhận diện **luồng thực thi**, còn định danh **công việc** nên được truyền/ghi nhận qua tham số, MDC, trace hoặc log có cấu trúc.

### Tài liệu chính thức

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — ThreadPoolTaskScheduler API](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework — CustomizableThreadFactory API](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/CustomizableThreadFactory.html)
- [Java 17 — ThreadFactory API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadFactory.html)
- [Java 17 — Executors API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Executors.html)
- [Spring Boot — Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-pool-010">Phân biệt giá trị mặc định của Framework với cấu hình do ứng dụng cung cấp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt giá trị mặc định của Spring Framework với cấu hình do ứng dụng cung cấp

## 1. Câu hỏi cần trả lời

Khi viết một ứng dụng dùng `@Async`, `@Scheduled` hoặc `TaskExecutor`, ta thường nghe những phát biểu như “Spring có sẵn 8 luồng”, “scheduler mặc định chỉ có 1 luồng”, hay “chỉnh `maxPoolSize` lên 50 sẽ chạy 50 tác vụ”. Những phát biểu này chỉ đúng trong **một lớp cấu hình và điều kiện cụ thể**. Cùng tên “Spring” nhưng thực tế có thể đề cập đến:

1. **Spring Framework thuần:** các interface, class thực thi và cơ chế chọn executor/scheduler khi bật annotation.
2. **Giá trị khởi tạo của một class:** ví dụ một `new ThreadPoolTaskExecutor()` có cấu hình ban đầu do chính class đó quy định.
3. **Spring Boot auto-configuration:** Boot quan sát các bean và điều kiện hiện có, có thể tạo thêm executor/scheduler với bộ mặc định **của Boot**.
4. **Thuộc tính cấu hình ứng dụng:** `application.properties`/`application.yml`, biến môi trường, command-line arguments, profile; chỉ ảnh hưởng khi có thành phần thực sự đọc/bind chúng.
5. **Bean hoặc cấu hình tường minh của ứng dụng:** `@Bean`, `AsyncConfigurer`, `SchedulingConfigurer`, `@Async("beanName")` và các setter như `setCorePoolSize`.
6. **Trạng thái thực khi chạy:** số luồng hiện có, số tác vụ active, hàng đợi và số task bị từ chối thay đổi theo tải, khác với các giá trị cấu hình.

Mục tiêu của bài: biết **ai tạo pool**, **ai chọn pool**, **giá trị nào thật sự có hiệu lực**, **cách kiểm chứng** thay vì dựa vào một con số “mặc định Spring” không có ngữ cảnh.

Ví dụ và các giá trị cụ thể được đặt trong phạm vi **Spring Framework 6.1/6.2, Spring Boot 3.3.x với Java 17 (platform threads)**. Khi chuyển sang Java 21 virtual threads, Spring Boot 3.4+, Spring Boot 4 hoặc đổi thành phần tích hợp, phải đối chiếu tài liệu theo đúng phiên bản. Không suy diễn một mặc định là bất biến qua các thế hệ.

## 2. Phân biệt bốn khái niệm dễ nhầm

### 2.1. Abstraction không đồng nghĩa một pool

`org.springframework.core.task.TaskExecutor` mô tả thao tác `execute(Runnable)`. Interface không quy định phải tạo thread mới hay có queue:

- `SyncTaskExecutor`: chạy ngay trên **luồng gọi**; lời gọi chờ task kết thúc.
- `SimpleAsyncTaskExecutor`: mỗi lần thực thi thường tạo thread mới, không phải thread pool tái sử dụng; có thể cấu hình giới hạn đồng thời.
- `ThreadPoolTaskExecutor`: bọc `ThreadPoolExecutor`, có `corePoolSize`, `maxPoolSize`, `queueCapacity` và chính sách từ chối.
- `TaskScheduler` phục vụ lập lịch theo thời điểm/chu kỳ, khác với mục tiêu chỉ “gửi việc” của executor. `ThreadPoolTaskScheduler` có pool dành cho scheduled tasks.

Vì vậy lời gọi `@Async` không chứng minh đang dùng một thread pool. Cần biết **implementation cụ thể** được chọn.

### 2.2. “Mặc định của class” và “mặc định khi auto-config” khác nhau

Ví dụ `new ThreadPoolTaskExecutor()` (được khởi tạo và quản lý vòng đời đúng) có các giá trị ban đầu trong Spring Framework 6.1:

| Thuộc tính | Giá trị mặc định của bản thân class |
| --- | --- |
| `corePoolSize` | 1 |
| `maxPoolSize` | `Integer.MAX_VALUE` |
| `queueCapacity` | `Integer.MAX_VALUE` (hàng đợi gần như không giới hạn) |
| `keepAliveSeconds` | 60 |
| `ThreadPoolTaskScheduler.poolSize` | 1 |

Điểm tinh tế: với queue gần như không giới hạn, khi số tác vụ vượt số core, các task thường được **xếp hàng**; pool gần như không có lý do tăng lên gần `maxPoolSize`. Tăng mỗi `maxPoolSize` mà vẫn giữ queue không giới hạn thường **không tạo thêm nhiều worker theo mong muốn**. Phải thiết kế cùng lúc core/max/queue/rejection dựa trên tải thực tế.

Một `ThreadPoolTaskExecutor` là **class của Spring Framework** nhưng nếu Spring Boot tạo và cấu hình class đó, giá trị thực có thể khác (ví dụ core 8 của Boot 3.3). **Cùng class, không nhất thiết cùng cấu hình.**

### 2.3. Auto-configuration là việc có điều kiện

Spring Framework cung cấp công cụ và fallback khi bật annotation. Spring Boot cung cấp thêm các cấu hình tự động, nhưng chỉ khi **điều kiện auto-configuration phù hợp**. Khi ứng dụng khai báo bean riêng, Boot có thể bỏ qua một phần auto-configuration. Không nên diễn giải “Boot mặc định tạo X” thành “Boot chắc chắn tạo X trong mọi application context”.

### 2.4. Cấu hình mong muốn khác cấu hình thực

Giá trị trong file properties có thể không được áp dụng nếu ứng dụng dùng bean tự tạo bằng `new`/`@Bean` và không bind giá trị từ Boot builder/properties. Vì thế phải kiểm tra **bean thực tế** và các getter sau khi context đã khởi động, đồng thời quan sát metric pool/queue dưới tải.

## 3. Spring Framework thuần: luật chọn mặc định

### 3.1. `@EnableAsync` và `@Async`

Với Spring Framework 6.1/6.2, khi đã bật `@EnableAsync`, nếu không chỉ định executor qua `AsyncConfigurer` hay qualifier cụ thể, cơ chế mặc định tìm:

1. Một bean **`TaskExecutor` duy nhất** trong context.
2. Nếu không chọn được theo cách đó, một bean kiểu `Executor` tên **`taskExecutor`**.
3. Nếu không có ứng viên phù hợp, dùng fallback **`SimpleAsyncTaskExecutor`** cho các lời gọi async.

Đây là quy tắc **lựa chọn khi annotation-driven async hoạt động**, không phải lời hứa rằng Spring Framework thuần tự tạo một `ThreadPoolTaskExecutor` 8 luồng. `SimpleAsyncTaskExecutor` không tái sử dụng worker kiểu pool; cần nghĩ đến chi phí thread và giới hạn đồng thời khi công việc nhiều.

Có thể điều khiển rõ bằng:

~~~java
// Trích đoạn bên trong một Spring-managed service:
@Async("reportExecutor")
public void createReport(String reportId) {
    // Chạy bằng executor bean tên reportExecutor nếu đi qua Spring proxy.
}
~~~

`@Async("reportExecutor")` chỉ rõ bean cần dùng cho phương thức đó; không phải thay đổi mặc định chung cho tất cả phương thức `@Async`. Với `@EnableAsync` mặc định proxy mode, gọi nội bộ `this.createReport(...)` có thể bỏ qua proxy và không kích hoạt interceptor. Muốn đặt mặc định chung theo code, dùng `AsyncConfigurer#getAsyncExecutor()` và quản lý vòng đời executor phù hợp.

**Nhận diện lỗi:** một app có hai `TaskExecutor` bean và không có bean phù hợp tên `taskExecutor` có thể không chọn được “mặc định” như bạn nghĩ. Đặt qualifier tường minh hoặc cấu hình default rõ ràng thay vì dựa vào thứ tự đăng ký bean.

### 3.2. `@EnableScheduling` và `@Scheduled`

Khi bật `@EnableScheduling`, Spring Framework tìm một `TaskScheduler` duy nhất, hoặc bean `TaskScheduler` tên **`taskScheduler`**; nó cũng thử lookup tương ứng với `ScheduledExecutorService`. Nếu không có ứng viên, registrar có thể tạo **scheduler cục bộ một luồng**.

Điều này không có nghĩa Framework mặc định đăng ký một bean `ThreadPoolTaskScheduler` do ứng dụng có thể `getBean(ThreadPoolTaskScheduler.class)` lấy được. Fallback nội bộ của registrar khác với một bean ứng dụng có thể inject hoặc cấu hình bằng Boot properties.

Ví dụ cấp bean tường minh:

~~~java
// Đặt trong class @Configuration.
@Bean("taskScheduler")
ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(3);
    scheduler.setThreadNamePrefix("periodic-");
    return scheduler;
}
~~~

Khi Spring quản lý bean, nó lo lifecycle/initialization thích hợp. Nếu tự tạo thủ công ngoài Spring context, cần khởi tạo/đóng executor đúng cách. Với `@Scheduled(fixedDelay = ...)`, lần tiếp theo được tính sau khi lần trước kết thúc; với `fixedRate` và cron, lịch chạy còn phụ thuộc scheduler được chọn và mức tải.

### 3.3. Không bật annotation thì không tự có hành vi annotation

- `@EnableAsync` cho phép Spring intercept lời gọi `@Async` qua cơ chế đã đăng ký.
- `@EnableScheduling` cho phép Spring đăng ký các phương thức `@Scheduled`.
- Khởi động Spring Boot hoặc có bean `TaskExecutor` **không tự đồng nghĩa** mọi phương thức có `@Async`/`@Scheduled` đã được bật. Kiểm tra annotation và application context thực sự đang hoạt động.

## 4. Spring Boot 3.3.x: một tầng mặc định khác

Với Java 17 và virtual threads **không bật**, tài liệu Spring Boot 3.3 mô tả:

| Trường hợp | Mặc định thường gặp khi đủ điều kiện auto-config |
| --- | --- |
| Không có bean `Executor` do ứng dụng cung cấp | Boot có thể tạo `AsyncTaskExecutor` kiểu `ThreadPoolTaskExecutor` |
| `ThreadPoolTaskExecutor` do Boot tự tạo | **8 core threads**; có thể điều chỉnh bằng `spring.task.execution.*` |
| Cần scheduler cho `@Scheduled`, chưa có scheduler do app cung cấp | Boot có thể auto-config `ThreadPoolTaskScheduler` |
| Scheduler Boot kiểu `ThreadPoolTaskScheduler` | **1 scheduler thread** mặc định; điều chỉnh qua `spring.task.scheduling.*` |
| Java 21+ và bật `spring.threads.virtual.enabled=true` | Boot dùng các implementation phù hợp virtual threads, như `SimpleAsyncTaskExecutor` và `SimpleAsyncTaskScheduler`; pooling properties không áp dụng tương đương cho scheduler virtual-thread |

Đặt cạnh Spring Framework thuần:

| Câu hỏi | Spring Framework khi dùng annotation | Spring Boot 3.3 auto-config thông thường |
| --- | --- | --- |
| Ai quyết định executor? | Lookup bean `TaskExecutor` duy nhất, rồi `Executor` tên `taskExecutor`, rồi fallback | Boot có thể **cung cấp bean** auto-config nên lookup của Framework nhìn thấy bean ấy |
| Không có executor khả dụng? | `SimpleAsyncTaskExecutor` fallback cho `@Async` | `ThreadPoolTaskExecutor` auto-config nếu điều kiện phù hợp và không bật virtual threads |
| Số core “mặc định”? | Không có một con số áp dụng cho mọi loại `TaskExecutor`; class `ThreadPoolTaskExecutor` khởi tạo trực tiếp có core 1 | Pool do Boot tạo có core 8 trên platform threads |
| Scheduler mặc định? | Fallback scheduler nội bộ một luồng nếu không tìm được bean | Boot có thể tạo bean `ThreadPoolTaskScheduler`, mặc định pool 1 |
| Cách cấu hình? | `@Bean`, setter, `AsyncConfigurer`, `SchedulingConfigurer`, qualifier | Cũng có các cách Framework, thêm `spring.task.execution.*`/`spring.task.scheduling.*` cho auto-config |

**Không đồng nhất executor và scheduler:** `spring.task.execution.pool.core-size=8` không biến scheduler thành tám luồng. Muốn scheduler pool 3, dùng `spring.task.scheduling.pool.size=3` (khi Boot auto-config scheduler) hoặc setter của bean tương ứng.

### 4.1. Các properties thường dùng

`src/main/resources/application.properties` trong Boot 3.3 (chỉ có tác dụng với auto-config tương ứng):

~~~properties
# Tác vụ bất đồng bộ do Boot quản lý:
spring.task.execution.pool.core-size=4
spring.task.execution.pool.max-size=12
spring.task.execution.pool.queue-capacity=100
spring.task.execution.pool.keep-alive=30s
spring.task.execution.thread-name-prefix=app-worker-

# Tác vụ có lịch do Boot quản lý:
spring.task.scheduling.pool.size=2
spring.task.scheduling.thread-name-prefix=app-schedule-
~~~

Ví dụ này **không phải giá trị default**: đây là **override do ứng dụng cung cấp**. Một số giá trị khi cùng khai báo mới có ý nghĩa kết hợp: `max-size` phát huy tác dụng khi queue hữu hạn bị đầy; khi queue đầy và pool tới max, task có thể bị từ chối tùy rejection policy.

Boot configuration properties có quy tắc precedence: command line/environment có thể ghi đè file cấu hình theo thứ tự nạp của Boot. Trong ứng dụng thực, đừng chỉ đọc file `application.properties` rồi kết luận giá trị đang chạy; hãy kiểm tra resolved `Environment` và bean runtime, đồng thời chú ý active profile.

### 4.2. Khi tự định nghĩa bean, properties có tự áp dụng không?

Giả sử ứng dụng tự khai báo:

~~~java
// Trích đoạn trong @Configuration; không phải class đầy đủ.
@Bean("applicationTaskExecutor")
ThreadPoolTaskExecutor applicationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(16);
    return executor;
}
~~~

Với bean này, các setter 2/4/16 là cấu hình **của ứng dụng**. Không được kỳ vọng chỉ cần thêm `spring.task.execution.pool.core-size=8` là Boot tự thay giá trị do setter đặt: Boot auto-config thường **back off** khi bean `Executor` tùy chỉnh đã tồn tại, và một bean tạo bằng setter thủ công không tự đọc toàn bộ namespace `spring.task.execution.*`.

Nếu muốn được áp dụng Boot configuration properties khi tạo bean riêng, dùng builder thích hợp của Boot (ví dụ `ThreadPoolTaskExecutorBuilder`, đã được Boot cấu hình theo môi trường) hoặc tự bind properties có chủ đích; kiểm tra builder và API đúng version. Tránh cấu hình đồng thời qua nhiều kênh mà không biết nguồn nào quyết định giá trị cuối.

### 4.3. Hai tên dễ nhầm: `taskExecutor` và `applicationTaskExecutor`

Trong Spring Framework, `taskExecutor` là tên fallback nổi bật cho `@EnableAsync` khi không tìm được một `TaskExecutor` duy nhất. Trong Spring Boot 3.3, executor auto-config thường mang tên **`applicationTaskExecutor`**; các tích hợp như Spring MVC asynchronous request processing yêu cầu một `AsyncTaskExecutor` phù hợp với tên này.

Nếu ứng dụng có nhiều bean `Executor`, việc `@Async` chọn default và việc MVC chọn executor có thể khác nhau. Một bean tên `reportExecutor` không tự trở thành default cho mọi loại async, cũng không bảo đảm MVC sử dụng nó.

Cách giảm mơ hồ:

1. Với background job quan trọng, dùng `@Async("reportExecutor")` hoặc inject `@Qualifier("reportExecutor") TaskExecutor`.
2. Với Spring MVC async, kiểm tra đúng `AsyncTaskExecutor` tên `applicationTaskExecutor` hoặc cấu hình `AsyncSupportConfigurer` cho MVC.
3. Với `@Scheduled`, chọn `taskScheduler` rõ ràng hoặc cấu hình `SchedulingConfigurer`; đó là **scheduler**, không phải executor bất kỳ.
4. Nếu có nhiều context, mỗi context có tập bean và điều kiện annotation riêng; đừng dựa vào bean ở một context khác.

## 5. Ví dụ chạy được: so sánh default Boot và bean custom

Ví dụ này sử dụng **Java 17, Maven, Spring Boot 3.3.13**, không cần web server. Nó in ra **implementation và giá trị của bean thực tế** trong hai lần chạy: (a) Boot tự cấu hình, (b) ứng dụng khai báo pool riêng. Trong ví dụ không bật virtual threads.

Cấu trúc:

~~~text
pool-defaults/
├── pom.xml
└── src/main/java/com/example/pools/
    ├── PoolDefaultsApplication.java
    ├── AuditService.java
    └── CustomPools.java
~~~

### 5.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>pool-defaults</artifactId>
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

### 5.2. PoolDefaultsApplication.java

~~~java
package com.example.pools;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PoolDefaultsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PoolDefaultsApplication.class, args);
    }

    @Bean
    ApplicationRunner auditPools(
            ApplicationContext context, AuditService auditService) {
        return args -> {
            ThreadPoolTaskExecutor executor = context.getBean(
                    "applicationTaskExecutor", ThreadPoolTaskExecutor.class);
            ThreadPoolTaskScheduler scheduler = context.getBean(
                    "taskScheduler", ThreadPoolTaskScheduler.class);

            System.out.println("EXECUTOR_TYPE="
                    + executor.getClass().getSimpleName());
            System.out.println("CORE=" + executor.getCorePoolSize());
            System.out.println("MAX=" + executor.getMaxPoolSize());
            System.out.println("QUEUE_CAPACITY=" + executor.getQueueCapacity());
            System.out.println("SCHEDULER_TYPE="
                    + scheduler.getClass().getSimpleName());
            System.out.println("SCHEDULER_CORE="
                    + scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());

            // Chạy @Async qua bean proxy; tên thread có thể thay đổi.
            String worker = auditService.workerName().get(5, TimeUnit.SECONDS);
            System.out.println("WORKER_THREAD=" + worker);
        };
    }
}
~~~

`getCorePoolSize()`, `getMaxPoolSize()` và `getQueueCapacity()` cho biết cấu hình pool. Chúng **không** cho biết số worker đang active: muốn xem trạng thái hiện tại dùng `getActiveCount()`, `getPoolSize()`, `getQueueSize()` hoặc metric. Với scheduler, đọc `getScheduledThreadPoolExecutor().getCorePoolSize()` để quan sát kích thước core được cấu hình trong `ScheduledThreadPoolExecutor`. Tại startup thường chưa có đủ task để tạo hết số worker; đừng dùng “current pool size” thay cho “configured core size”.

### 5.3. AuditService.java

~~~java
package com.example.pools;

import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    @Async("applicationTaskExecutor")
    public CompletableFuture<String> workerName() {
        return CompletableFuture.completedFuture(
                Thread.currentThread().getName());
    }
}
~~~

Method `workerName()` được gọi từ bean khác qua proxy và chọn `applicationTaskExecutor` một cách tường minh. Trả `CompletableFuture` cho phép demo đợi kết quả để in ra; trong endpoint xử lý nền thực tế thường không gọi `get()` trên request thread nếu muốn đáp ứng nhanh.

### 5.4. CustomPools.java

~~~java
package com.example.pools;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "demo.custom", havingValue = "true")
public class CustomPools {
    @Bean("applicationTaskExecutor")
    ThreadPoolTaskExecutor appExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(12);
        executor.setThreadNamePrefix("custom-worker-");
        return executor;
    }

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler scheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("custom-schedule-");
        return scheduler;
    }
}
~~~

Khi không có `demo.custom=true`, cả hai `@Bean` trên **không được khai báo** do điều kiện ở class. Spring Boot có thể tự tạo executor và scheduler tương ứng. Khi bật flag, bean do ứng dụng khai báo xuất hiện; auto-config thích hợp của Boot nhường quyền, và setter của ứng dụng quyết định giá trị. Mọi số 2, 4, 12, 3 trong `CustomPools` là cấu hình **custom**, không phải default của Framework.

Để chắc chắn Boot nhận ra cần scheduler, demo bật `@EnableScheduling`. Nếu project của bạn không có nhu cầu lập lịch thì có thể bỏ nó và phần scheduler; việc auto-configuration phụ thuộc điều kiện đăng ký của phiên bản Boot.

### 5.5. Chạy hai chế độ

Chạy ở thư mục chứa `pom.xml`, cần JDK 17+ và Maven:

~~~bash
# Chế độ A: dùng auto-configuration Boot khi không có custom bean.
mvn spring-boot:run

# Dừng app trước khi chạy tiếp, ví dụ Ctrl+C.
# Chế độ B: bật hai bean do ứng dụng tự khai báo.
mvn spring-boot:run "-Dspring-boot.run.arguments=--demo.custom=true"
~~~

**Chế độ A** (Spring Boot 3.3.13 trên Java 17, không có `Executor` khác; bỏ qua các dòng log framework và hậu tố tên worker biến thiên):

~~~text
EXECUTOR_TYPE=ThreadPoolTaskExecutor
CORE=8
MAX=2147483647
QUEUE_CAPACITY=2147483647
SCHEDULER_TYPE=ThreadPoolTaskScheduler
SCHEDULER_CORE=1
WORKER_THREAD=task-1
~~~

Dòng `WORKER_THREAD=task-1` là **ví dụ về tên luồng**, không phải một chuỗi được bảo đảm nếu cấu hình khác, có thành phần khác sử dụng pool hoặc thay thread factory. Các con số in từ getter là điểm so sánh chính trong môi trường giả định này.

**Chế độ B** (bean custom từ `CustomPools`; tương tự bỏ qua log):

~~~text
EXECUTOR_TYPE=ThreadPoolTaskExecutor
CORE=2
MAX=4
QUEUE_CAPACITY=12
SCHEDULER_TYPE=ThreadPoolTaskScheduler
SCHEDULER_CORE=3
WORKER_THREAD=custom-worker-1
~~~

Giá trị tên thread cuối cũng là ví dụ; prefix `custom-worker-` do ứng dụng đặt. **Cả hai chế độ cùng dùng class `ThreadPoolTaskExecutor` và `ThreadPoolTaskScheduler` nhưng cấu hình khác**. Do đó không thể chỉ nhìn tên class trong log và đoán “mặc định” đã có hiệu lực.

**Giới hạn của demo:** không cấu hình `spring.task.execution.pool.*` trong file properties nên chế độ A cho thấy baseline Boot; nếu bổ sung properties hoặc biến môi trường khác, số liệu sẽ thay đổi. Không có tác vụ `@Scheduled` trong ví dụ; bean scheduler vẫn có thể được Boot tạo do annotation scheduling đã bật, nhưng lịch thực tế cần một method `@Scheduled` để chạy.

### 5.6. Nếu chỉ muốn đổi Boot properties mà không thêm bean

Có thể giữ chế độ A và chạy với argument:

~~~bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.task.execution.pool.core-size=3 --spring.task.execution.pool.max-size=5 --spring.task.execution.pool.queue-capacity=10 --spring.task.scheduling.pool.size=2"
~~~

Khi Boot tự tạo cả hai pool, getter kỳ vọng phản ánh `CORE=3`, `MAX=5`, `QUEUE_CAPACITY=10` và `SCHEDULER_CORE=2`. Thứ tự/phân tích cú pháp command-line arguments ở đây do Spring Boot plugin truyền sang ứng dụng; nếu dùng PowerShell, giữ nguyên cặp dấu ngoặc kép quanh toàn bộ giá trị `-Dspring-boot.run.arguments=...`.

Ngược lại, nếu chạy flag `demo.custom=true` cùng các property trên, `CustomPools` vẫn gán 2/4/12 và 3 bằng setter, bởi nó không dùng Boot builder để đọc các property ấy. Đây là một **thử nghiệm có thể lặp lại** để phân biệt cấu hình do Boot cung cấp với cấu hình do ứng dụng tự khai báo.

## 6. Vì sao max-size có thể không phát huy tác dụng?

Dù số cấu hình đã xác định, hoạt động của `ThreadPoolTaskExecutor` còn phụ thuộc thuật toán `ThreadPoolExecutor` phía dưới.

Với core=2, max=4, queueCapacity=12:

1. Khi hai worker đang bận, task mới có thể đi vào queue.
2. Khi queue đạt giới hạn và pool chưa đạt max, executor có thể mở thêm worker.
3. Khi pool đã đạt max và queue đầy, task mới có thể bị từ chối (`TaskRejectedException` khi gọi qua Spring `TaskExecutor`); cần chính sách backpressure và trả HTTP 429/503 khi phù hợp.
4. Task chờ trong queue tăng latency; tăng queue quá lớn có thể che giấu quá tải, làm request đợi lâu trước khi được xử lý.

Với queue không giới hạn như giá trị ban đầu của `ThreadPoolTaskExecutor`, pool chủ yếu giữ ở mức core, dù max đặt rất lớn. Do đó một nhận định kiểu “max=100 thì chạy được 100 job song song” thường sai nếu không xem queue. Mặt khác, một pool lớn cũng không bảo đảm throughput nếu giới hạn thật nằm ở connection pool, DB hoặc API bên ngoài.

Trong ứng dụng thực, nên chọn pool theo đặc điểm công việc: CPU-bound không nên nhân bản vô hạn số worker; I/O-bound có thể cần thêm độ đồng thời nhưng phải tuân giới hạn tài nguyên và timeout. Chia pool cho nhiệm vụ dài/chậm và nhiệm vụ nhanh có SLA khác nhau để tránh chúng chiếm hết hàng đợi của nhau.

## 7. Java 21 và virtual threads thay đổi cách hiểu “default”

Spring Boot 3.3 có hỗ trợ bật virtual threads với Java 21+ bằng `spring.threads.virtual.enabled=true`. Khi đó Boot có thể tự cấu hình:

- `SimpleAsyncTaskExecutor` sử dụng virtual threads thay vì `ThreadPoolTaskExecutor` thông thường;
- `SimpleAsyncTaskScheduler` thay vì scheduler pool platform threads truyền thống.

Vì thế `spring.task.execution.pool.core-size` hay `spring.task.scheduling.pool.size` **không thể được diễn giải như số lượng virtual threads tối đa**. Với `SimpleAsyncTaskScheduler`, fixed-delay tasks có quy tắc đặc biệt liên quan đến một scheduler thread; phải đọc hướng dẫn theo implementation.

Virtual threads không miễn trừ giới hạn DB connections, sockets, API quota hoặc CPU; cần semaphore/rate limit và quan sát thực tế khi tải cao. Chỉ vì Java 21 có virtual threads không có nghĩa mọi custom `ThreadPoolTaskExecutor` tự biến thành virtual-thread executor: điều kiện và kiểu bean vẫn quan trọng.

## 8. Cách xác định cấu hình thật trong một hệ thống đang chạy

Có thể kiểm tra theo chuỗi sau:

1. **Chốt phiên bản:** Spring Framework, Spring Boot và Java đang chạy; có bật virtual threads không?
2. **Kiểm tra chức năng được bật:** có `@EnableAsync`, `@EnableScheduling` và method annotation tương ứng trong context không?
3. **Liệt kê beans:** các bean `Executor`, `TaskExecutor`, `AsyncTaskExecutor`, `TaskScheduler` và `ScheduledExecutorService`; chú ý tên, kiểu và số lượng.
4. **Xác nhận luật chọn:** `@Async("name")` có qualifier? Có `AsyncConfigurer` không? Scheduled registrar được `SchedulingConfigurer` trỏ tới đâu? Có trùng tên `taskExecutor`/`applicationTaskExecutor`/`taskScheduler` không?
5. **Đọc nguồn cấu hình:** resolved `Environment`, active profiles, command-line, YAML/properties; xem bean được Boot auto-config tạo hay code ứng dụng tự tạo.
6. **Đọc runtime:** getter cấu hình core/max/queue/pool size, active thread count, current pool size, queue length và rejection metrics; có thể xuất qua Micrometer/Actuator nếu dự án đã trang bị.
7. **Thử tải và shutdown:** đo thời gian chờ, task rejection, context propagation, timeout và cách dừng có chờ các job đang chạy hay không.

Trích đoạn Java để liệt kê executor và scheduler từ `ApplicationContext` (đặt trong runner/debug utility được phép; đây là snippet, không phải chương trình độc lập):

~~~java
String[] executors = context.getBeanNamesForType(
        java.util.concurrent.Executor.class);
String[] schedulers = context.getBeanNamesForType(
        org.springframework.scheduling.TaskScheduler.class);

System.out.println("Executor beans = " + java.util.Arrays.toString(executors));
System.out.println("Scheduler beans = " + java.util.Arrays.toString(schedulers));
~~~

Tên bean và số lượng trong danh sách tùy toàn bộ application context. Đây là cách xác minh sự tồn tại, **chưa đủ** để kết luận annotation chọn bean nào; đối chiếu thêm luật lookup và qualifier. Việc tự ý lấy một bean bằng kiểu chung khi có nhiều bean có thể gây `NoUniqueBeanDefinitionException`; muốn chắc chắn hãy dùng tên + kiểu hoặc `@Qualifier`.

### Mẫu ghi quyết định cấu hình khi review code

| Mục cần review | Giá trị cần ghi nhận |
| --- | --- |
| Framework / Boot / JDK | Chính xác phiên bản đang deploy |
| Công việc | `@Async` nào, `@Scheduled` nào, MVC async hay job do code gọi `execute` |
| Executor được chọn | Tên và class thực tế, nguồn tạo bean |
| Thread pool | core, max, queue, keepAlive, rejection policy |
| Scheduler | tên/kiểu bean, số luồng, cron/fixedDelay/fixedRate và múi giờ |
| Nguồn override | code setter, Boot properties, biến môi trường, profile, builder |
| Vận hành | active/queued/rejected, SLA, shutdown, retry, idempotency |

Dùng bảng này giúp review được vì sao hai môi trường “đều là Spring Boot” lại có số luồng và hiệu năng khác nhau.

## 9. Các lỗi phổ biến

| Hiểu nhầm / hiện tượng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| “Spring Framework mặc định có 8 worker” | 8 core là baseline của **Boot 3.3 auto-config platform-thread executor**, không phải mọi `TaskExecutor` | Xác định ai tạo bean và runtime type |
| “Tự `new ThreadPoolTaskExecutor` mặc định giống Boot” | Class direct default core=1, Boot builder có thể đặt core=8 | In getter, đặt setter rõ |
| “Đã đặt `max-size=20`, phải chạy 20 task” | Queue không giới hạn có thể giữ pool ở core | Thiết kế queue hữu hạn, kiểm thử saturation |
| “Sửa `application.properties` nhưng pool không đổi” | Bean custom không dùng Boot binding/builder; hoặc cấu hình bị nguồn khác override | Kiểm tra bean source và resolved environment |
| “`@Async` không chạy nền” | Chưa bật async, self-invocation, hoặc chọn `SyncTaskExecutor` | Kiểm tra `@EnableAsync`, proxy và tên luồng |
| “`@Scheduled` chạy song song khi tăng execution core” | Executor pool và scheduler pool khác loại | Cấu hình `spring.task.scheduling.pool.size` hoặc custom scheduler |
| “Đặt tên `reportExecutor` thì MVC cũng dùng” | MVC có yêu cầu riêng về `AsyncTaskExecutor`/`applicationTaskExecutor` | Cấu hình MVC async rõ ràng |
| “Mọi `@Async` dùng bean tôi vừa khai báo” | Nhiều bean, qualifier hoặc `AsyncConfigurer` quyết định chọn | Chỉ rõ `@Async("beanName")` và audit |
| “Một scheduler là đủ cho toàn cluster” | Mỗi application context/pod có scheduler riêng | Lock phân tán, idempotency, hoặc scheduler chuyên dụng |
| “Virtual threads vẫn có pool-size 8” | Auto-config có thể chọn implementation khác | Xem class thật và giới hạn external resources |

Một số lỗi chỉ xuất hiện khi deploy nhiều instance hoặc nhận tải lớn. Khởi động thành công và một request chạy đúng **không chứng minh** đã lựa chọn pool tối ưu.

## 10. Bài tập tự kiểm tra

1. Trên Spring Framework thuần, có `@EnableAsync` nhưng không có `TaskExecutor` duy nhất hay bean `Executor` tên `taskExecutor`. Spring dùng fallback nào? Có tái sử dụng worker kiểu pool không?
2. `new ThreadPoolTaskExecutor()` và `ThreadPoolTaskExecutor` do Spring Boot 3.3 tự tạo có cùng giá trị core mặc định không? Tại sao?
3. Boot tự cấu hình executor core=8, nhưng bạn thêm `@Bean("applicationTaskExecutor")` đặt core=2. Đâu là nguồn quyết định giá trị core thực tế?
4. Ứng dụng có hai `TaskExecutor`: `imageExecutor` và `reportExecutor`. `@Async` không qualifier nên chọn cái nào? Cần thay đổi gì để hành vi xác định?
5. Tại sao tăng `spring.task.execution.pool.max-size` mà để queue vô hạn có thể không tăng độ đồng thời?
6. `@Scheduled` dùng pool nào, và vì sao đổi cấu hình async executor chưa chắc làm job cron chạy song song?
7. Sau khi bật Java 21 virtual threads, tại sao phải kiểm tra class của executor/scheduler trước khi diễn giải `core-size` và `pool.size`?
8. Nếu hai pod chạy cùng cron lúc nửa đêm, việc tăng pool size hay đổi tên bean có ngăn dữ liệu cập nhật hai lần không? Cần cơ chế điều phối gì?

**Kết luận sử dụng:** khi nói “mặc định của Spring”, luôn hỏi rõ **phiên bản, class, nơi tạo bean, luật chọn executor và nguồn cấu hình thực sự có hiệu lực**. Chỉ sau khi trả lời các câu ấy mới hợp lý để nói pool có bao nhiêu luồng hoặc ứng dụng nên chỉnh property nào.

## Tài liệu tham khảo

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2 — @EnableAsync Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/annotation/EnableAsync.html)
- [Spring Framework 6.2 — @EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html)
- [Spring Boot 3.3 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html)

</details>

- [Quay lại đầu trang](#back-to-top)
