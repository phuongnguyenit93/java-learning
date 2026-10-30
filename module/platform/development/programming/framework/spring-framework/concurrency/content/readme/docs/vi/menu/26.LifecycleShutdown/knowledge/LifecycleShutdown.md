<a id="back-to-top"></a>

# Vòng đời và dừng

## Menu
- [Để container khởi tạo và đóng executor hoặc scheduler được khai báo thành bean](#lifecycle-shutdown-001)
- [Phân biệt dừng nhận tác vụ mới với chờ tác vụ đang chạy](#lifecycle-shutdown-002)
- [SmartLifecycle và phối hợp thứ tự dừng các thành phần](#lifecycle-shutdown-003)
- [waitForTasksToCompleteOnShutdown và thời gian chờ kết thúc](#lifecycle-shutdown-004)
- [awaitTermination không tạo bảo đảm hoàn tất vô hạn](#lifecycle-shutdown-005)
- [Tương tác của các tùy chọn shutdown và khác biệt theo phiên bản](#lifecycle-shutdown-006)
- [Ngắt tác vụ đang chạy và hủy công việc chưa bắt đầu](#lifecycle-shutdown-007)
- [Pause, resume và giới hạn theo loại triển khai, phiên bản](#lifecycle-shutdown-008)
- [Giữ tài nguyên phụ thuộc còn hoạt động tới khi tác vụ cần dùng kết thúc](#lifecycle-shutdown-009)
- [Kiểm chứng khi context đóng trong lúc hàng đợi còn công việc](#lifecycle-shutdown-010)

## <a id="lifecycle-shutdown-001">Để container khởi tạo và đóng executor hoặc scheduler được khai báo thành bean</a>

<details>
<summary>Xem chi tiết</summary>

# Để container khởi tạo và đóng executor hoặc scheduler được khai báo thành bean

**Trong Spring Framework, một \`ThreadPoolTaskExecutor\` hoặc \`ThreadPoolTaskScheduler\` được khai báo làm bean sẽ được container quản lý vòng đời: khởi tạo hạ tầng executor sau khi cấu hình bean và gọi cơ chế dừng/hủy khi ApplicationContext đóng.** Đây là lợi ích quan trọng của việc để Spring **sở hữu pool**, thay vì tạo một \`new ThreadPoolTaskExecutor()\` tùy tiện, tự khởi động ở từng service rồi quên dừng.

Bài Knowledge thuộc nhóm **Vòng đời và dừng** của **Spring Framework - concurrency**, với chủ đề **“Để container khởi tạo và đóng executor hoặc scheduler được khai báo thành bean”**. Nội dung dùng Java 17+ và Spring Framework 6.2.x để giải thích lifecycle chính xác, các cấu hình shutdown đáng chú ý, sự khác nhau giữa shutdown bình thường và chờ hoàn tất, ảnh hưởng tới \`@Async\`/\`@Scheduled\`, kiểm thử bằng chương trình Maven chạy được và những lỗi thường gặp.

> **Ý chính:** Để Spring tạo bean executor/scheduler, cấu hình trước khi dùng, inject vào các component và để ApplicationContext dừng chúng đúng lifecycle. **Nhưng container-managed không đồng nghĩa mọi tác vụ chắc chắn hoàn thành, không có job bị hủy, hay dữ liệu tự trở nên bền vững qua restart.** Những bảo đảm này cần chính sách ứng dụng.

## 1. Ba phần của vòng đời một executor

Với một pool chạy tác vụ nền, có ít nhất ba giai đoạn cần quản lý:

1. **Khởi tạo (initialize):** các tham số thread pool, queue, ThreadFactory, handler và trạng thái điều phối được thiết lập; underlying executor được tạo, sẵn sàng nhận việc.
2. **Sử dụng (run):** các producer gửi công việc, executor nhận/từ chối, worker thực thi và trả kết quả; scheduler giữ lịch rồi kích hoạt callback.
3. **Dừng/giải phóng (stop/shutdown/destroy):** ngừng nhận việc mới, xử lý những việc đang chạy/đang đợi theo chính sách, chờ trong giới hạn, hủy lịch định kỳ và thả các worker/tài nguyên.

~~~text
ApplicationContext.refresh()
          |
          v
 @Bean / bean properties
          |
          v
 InitializingBean.afterPropertiesSet()
          |
          v
 ExecutorConfigurationSupport.initialize()
          |
          v
 THREAD POOL READY -------- execute / submit / schedule --------+
          |                                                     |
          |                                             RUNNING/QUEUED
          |                                                     |
 ApplicationContext.close()                                     |
          |                                                     |
          v                                                     |
 ContextClosedEvent / SmartLifecycle stop                      |
          |                                                     |
          v                                                     |
 DisposableBean.destroy() -> shutdown()  <---------------------+
          |
          v
 underlying ExecutorService shuts down / tasks are handled by policy
~~~

Đây là **mô hình khái niệm**, không phải một sequence bắt buộc giống nhau trong mọi implementation và cấu hình. Một số bước lifecycle có thể là **early shutdown**, **coordinated stop**, hoặc shutdown ở destruction phase, tùy bean và thuộc tính đã thiết lập.

Điểm đáng nhớ: **khởi tạo pool** khác với **tạo sẵn tất cả worker threads**. Một \`ThreadPoolTaskExecutor\` có thể được initialize nhưng chưa có worker nào hoạt động, bởi core threads thường được tạo theo nhu cầu nếu không yêu cầu prestart.

## 2. Vì sao Spring-managed bean tốt hơn việc tự new rồi bỏ mặc?

Cách cấu hình thông thường:

~~~java
@Bean("jobExecutor")
public ThreadPoolTaskExecutor jobExecutor() {
    ThreadPoolTaskExecutor executor =
            new ThreadPoolTaskExecutor();

    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(32);
    executor.setThreadNamePrefix("job-");
    return executor;
}
~~~

Đây là **một phương thức @Bean minh họa trong @Configuration class** có import \`ThreadPoolTaskExecutor\`; bản thân đoạn này không phải file Java độc lập.

Khi Spring tạo singleton bean qua factory, bean trải qua các callback lifecycle cần thiết. \`ThreadPoolTaskExecutor\` kế thừa \`ExecutorConfigurationSupport\`, triển khai \`InitializingBean\` và \`DisposableBean\`:

- **afterPropertiesSet()** gọi **initialize()**.
- **initialize()** tạo underlying **ThreadPoolExecutor** theo cấu hình.
- **destroy()** gọi **shutdown()** khi container hủy bean.
- Spring 6.1+ còn tích hợp **SmartLifecycle** và xử lý **ContextClosedEvent**, giúp phối hợp giai đoạn dừng theo cấu hình.

**Không cần gọi \`executor.initialize()\` thủ công trong phương thức @Bean thông thường**: container đã làm điều đó sau khi áp dụng cấu hình. Gọi initialize sớm hoặc gọi lại tùy tiện có thể tạo ra underlying executor không theo trạng thái lifecycle mà bạn mong đợi.

### Lợi ích cụ thể của sở hữu bởi container

| Vấn đề | Bean do Spring quản lý | Pool được tạo ngoài container |
| --- | --- | --- |
| Khởi tạo sau cấu hình | \`afterPropertiesSet()\` tự được gọi | Chủ sở hữu phải gọi initialize nếu implementation cần |
| Dừng khi context close | \`destroy()/shutdown()\` qua lifecycle | Phải tự shutdown hoặc đảm bảo có owner |
| Tái sử dụng chung | Inject một bean phù hợp vào nhiều consumers | Dễ tạo mỗi nơi một pool mới |
| Thread names, queue, rejection | Tập trung ở configuration | Mỗi nơi có thể cấu hình khác nhau |
| Quan sát pool | Có thể lấy metrics/monitor từ bean | Có thể bị phân tán, khó truy ra owner |
| Phối hợp với @Async/@Scheduled | Có integration và tên executor/scheduler rõ | Phải bắc cầu/configure thủ công |
| Test vòng đời | Có thể refresh/close context có kiểm soát | Test phải dọn từng pool tự tạo |

**Bean-managed không phải phép màu.** Nếu producer tiếp tục submit vào pool đã dừng, vẫn có thể bị **TaskRejectedException**. Nếu task đang chặn một API không hỗ trợ interrupt, nó vẫn có thể giữ tài nguyên sau khi shutdown được yêu cầu.

## 3. \`ThreadPoolTaskExecutor\`: khởi tạo do InitializingBean

Một \`ThreadPoolTaskExecutor\` mới được tạo bằng constructor ngoài Spring:

~~~java
ThreadPoolTaskExecutor executor =
        new ThreadPoolTaskExecutor();

executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(20);
~~~

Đây là **đoạn minh họa**. Tại đây các thuộc tính mới được đặt, nhưng **underlying ThreadPoolExecutor chưa chắc đã được khởi tạo**. Nếu gọi \`getThreadPoolExecutor()\` trước initialize, API có thể ném **IllegalStateException: not initialized yet**.

Khi bean do Spring quản lý:

~~~text
new ThreadPoolTaskExecutor()
        |
setCorePoolSize / setMaxPoolSize / setQueueCapacity
        |
Spring InitializingBean.afterPropertiesSet()
        |
ExecutorConfigurationSupport.initialize()
        |
getThreadPoolExecutor() sẵn sàng
        |
execute / submit
~~~

Bởi vậy không nên dùng một phương thức @Bean như:

~~~java
@Bean
ThreadPoolTaskExecutor badExecutor() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(2);
    e.initialize(); // Không cần trong @Bean thường.
    return e;
}
~~~

Điểm phê bình ở đây là **không cần tự quản lý initialize** khi bean được container quản lý. Ngoài Spring, nếu chủ động sở hữu instance, việc gọi initialize lại có thể cần thiết; cần phân biệt ngữ cảnh.

### Khởi tạo executor không đồng nghĩa khởi chạy công việc

- **Pool initialized:** underlying ThreadPoolExecutor đã được tạo.
- **Core worker created:** một số thread đã được tạo hoặc chưa, tùy nhu cầu và \`prestartAllCoreThreads\`.
- **Job submitted:** producer đã giao một Runnable/Callable.
- **Job started:** worker đã thực sự bắt đầu chạy body.
- **Job completed:** body hoàn tất bình thường hoặc với lỗi.
- **Pool terminated:** sau shutdown, tất cả tasks hoàn tất và worker kết thúc.

Không suy từ \`getPoolSize() == 0\` rằng bean chưa initialize. Khi chưa submit, pool có thể hợp lệ và chưa khởi tạo worker nào.

## 4. \`ThreadPoolTaskScheduler\`: bean cũng có lifecycle

Cách khai báo scheduler:

~~~java
@Bean("taskScheduler")
public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler =
            new ThreadPoolTaskScheduler();

    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("schedule-");
    return scheduler;
}
~~~

Đây là **một phương thức @Bean minh họa** trong Spring configuration.

**ThreadPoolTaskScheduler** cũng kế thừa \`ExecutorConfigurationSupport\` và được quản lý init/shutdown. Nó thường sử dụng underlying \`ScheduledThreadPoolExecutor\` để tính giờ và chạy các scheduled callbacks.

Khi dùng **@Scheduled**:

~~~java
@Scheduled(fixedDelay = 10_000)
public void check() {
    // Scheduled callback được TaskScheduler kích hoạt.
}
~~~

Đây là **method trong Spring-managed bean**, cần \`@EnableScheduling\` hoặc cấu hình scheduling tương đương. Với ThreadPoolTaskScheduler, **callback thường chạy ngay trên scheduler thread** chứ không tự chuyển sang TaskExecutor độc lập.

Khi ứng dụng đóng context, mục tiêu của Spring là **dừng kích hoạt lịch mới** và áp dụng chính sách xử lý những callback đang chạy/đã lên lịch. Một \`@Scheduled\` method không tự sở hữu một thread; nó được điều phối qua TaskScheduler.

**Cẩn thận:** scheduler có thể chứa nhiều loại nhiệm vụ:
- **One-shot delayed tasks** (chạy tại một thời điểm).
- **Periodic fixed-rate**.
- **Periodic fixed-delay**.
- **Cron/Trigger-based**.
- **Các callback được đăng ký thủ công** bên cạnh \`@Scheduled\`.

Khi shutdown, cách xử lý các delayed/periodic tasks có thể phụ thuộc chính sách underlying scheduler, không thể nói “shutdown chờ chạy hết mọi cron trong tương lai”.

## 5. Một ví dụ Maven Java 17 chạy được: Spring tạo và tự đóng cả hai pool

Ví dụ sau chứng minh ba điều bằng kiểm tra có thể lặp lại:

1. Sau khi \`AnnotationConfigApplicationContext\` tạo xong, các **underlying executors đã được initialize**, dù \`@Bean\` không gọi \`initialize()\`.
2. Callback được schedule chạy trên thread có tiền tố **schedule-**, sau đó **delegate** vào executor có tiền tố **job-**.
3. Sau \`context.close()\`, underlying executor và scheduler đều báo **isShutdown()==true**; code ứng dụng **không tự gọi shutdown từng bean**.

Đây là **ví dụ kiểm tra ownership/lifecycle**, không phải benchmark.

Cấu trúc thư mục:

~~~text
spring-managed-pools-demo/
├── pom.xml
└── src/main/java/example/SpringManagedPoolsDemo.java
~~~

**pom.xml** — Java 17 và Spring Framework 6.2.12:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>spring-managed-pools-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

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
                <version>3.5.1</version>
                <configuration>
                    <mainClass>example.SpringManagedPoolsDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/example/SpringManagedPoolsDemo.java**:

~~~java
package example;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SpringManagedPoolsDemo {

    @Configuration
    public static class Config {

        @Bean("jobExecutor")
        public ThreadPoolTaskExecutor jobExecutor() {
            ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
            e.setCorePoolSize(1);
            e.setMaxPoolSize(1);
            e.setQueueCapacity(4);
            e.setThreadNamePrefix("job-");

            // Trong demo, cả hai pool còn có policy chờ bounded.
            e.setWaitForTasksToCompleteOnShutdown(true);
            e.setAwaitTerminationSeconds(5);
            return e; // Không gọi e.initialize().
        }

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
            s.setPoolSize(1);
            s.setThreadNamePrefix("schedule-");

            s.setWaitForTasksToCompleteOnShutdown(true);
            s.setAwaitTerminationSeconds(5);
            s.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            s.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            return s; // Không gọi s.initialize().
        }
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class);

        ThreadPoolTaskExecutor worker =
                context.getBean("jobExecutor",
                        ThreadPoolTaskExecutor.class);

        ThreadPoolTaskScheduler scheduler =
                context.getBean("taskScheduler",
                        ThreadPoolTaskScheduler.class);

        boolean executorReady =
                !worker.getThreadPoolExecutor().isShutdown();
        boolean schedulerReady =
                !scheduler.getScheduledThreadPoolExecutor().isShutdown();

        CountDownLatch jobFinished = new CountDownLatch(1);
        AtomicBoolean schedulerThread = new AtomicBoolean(false);
        AtomicBoolean workerThread = new AtomicBoolean(false);

        try {
            ScheduledFuture<?> scheduled = scheduler.schedule(() -> {
                schedulerThread.set(
                        Thread.currentThread().getName()
                                .startsWith("schedule-"));

                worker.execute(() -> {
                    workerThread.set(
                            Thread.currentThread().getName()
                                    .startsWith("job-"));
                    jobFinished.countDown();
                });
            }, Instant.now());

            // get() chờ callback gửi việc hoàn tất; nó chưa
            // bảo đảm worker body đã kết thúc.
            scheduled.get(5, TimeUnit.SECONDS);

            if (!jobFinished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Worker chưa hoàn tất trong hạn test");
            }

            System.out.println("Executor được khởi tạo: "
                    + executorReady);
            System.out.println("Scheduler được khởi tạo: "
                    + schedulerReady);
            System.out.println("Callback trên schedule-: "
                    + schedulerThread.get());
            System.out.println("Job trên job-: "
                    + workerThread.get());
            System.out.println("Job đã hoàn tất: "
                    + (jobFinished.getCount() == 0));
        } finally {
            // Container quản lý cả hai beans; không tự shutdown
            // từng pool trước khi đóng context.
            context.close();
        }

        System.out.println("Context đã đóng executor: "
                + worker.getThreadPoolExecutor().isShutdown());
        System.out.println("Context đã đóng scheduler: "
                + scheduler.getScheduledThreadPoolExecutor().isShutdown());
    }
}
~~~

Chạy trong thư mục chứa pom.xml:

~~~bash
mvn -q compile exec:java
~~~

**Stdout** (bỏ qua log Maven và các log framework nếu có), khi môi trường đủ tài nguyên để test hoàn tất trong hạn 5 giây:

~~~text
Executor được khởi tạo: true
Scheduler được khởi tạo: true
Callback trên schedule-: true
Job trên job-: true
Job đã hoàn tất: true
Context đã đóng executor: true
Context đã đóng scheduler: true
~~~

### Giải thích vì sao output xác định

- \`new AnnotationConfigApplicationContext(Config.class)\` tạo và refresh context, Spring xử lý cả hai singleton @Bean.
- Trước khi in, code gọi \`getThreadPoolExecutor()\` và \`getScheduledThreadPoolExecutor()\`. Hai method có thể throw nếu underlying pool chưa được initialize; khi context đã khởi tạo bean hợp lệ, chúng trả executor.
- Lịch **one-shot** \`scheduler.schedule(..., Instant.now())\` kích hoạt một callback; tên thread đã được đặt rõ.
- Callback **execute** một job qua \`worker\`, nên job chạy trong executor riêng; \`scheduled.get(timeout)\` chỉ chờ callback.
- \`jobFinished.await(timeout)\` chứng minh body worker đã chạy tới vị trí đếm ngược trước khi in.
- \`finally context.close()\` khiến Spring thực hiện lifecycle destruction của cả hai bean. Không có \`worker.shutdown()\` hay \`scheduler.shutdown()\` ở main.
- Sau close, **isShutdown()** trả true. Trong ví dụ các tasks đã xong; **isShutdown(true)** ở một hệ thống khác vẫn **không đồng nghĩa isTerminated(true)** hoặc mọi business side effect đã commit.
- Không cần \`@EnableScheduling\` trong ví dụ này vì nó **gọi TaskScheduler.schedule tường minh**. Nếu chuyển thành methods @Scheduled, cần bật scheduling để container phát hiện annotation.

### Tránh một giả định sai về thread count

Vì cấu hình có \`setCorePoolSize(1)\`, không nhất thiết thread job- đã được tạo **ngay khi BeanFactory gọi afterPropertiesSet()**. Ví dụ chỉ kiểm tra underlying pool đã ready, sau đó tạo việc thật để quan sát worker.

## 6. Khi nào không cần \`initialize()\`, khi nào cần?

| Cách sở hữu | Có cần ứng dụng tự initialize? | Ai shutdown? |
| --- | --- | --- |
| \`@Bean ThreadPoolTaskExecutor\` trong Spring context | **Không**; BeanFactory gọi lifecycle | **Spring** khi bean bị destroy/context close |
| \`@Bean ThreadPoolTaskScheduler\` trong Spring context | **Không**; container làm lifecycle | **Spring** khi đóng context |
| \`@Component\` subclass của ExecutorConfigurationSupport nếu hợp lệ | **Không**, khi Spring quản lý đầy đủ bean lifecycle | Spring |
| \`new ThreadPoolTaskExecutor()\` nằm trong static field ngoài context | **Có thể cần \`initialize()\`**, do không có BeanFactory | Chính owner của field phải shutdown |
| \`new ThreadPoolTaskScheduler()\` cục bộ trong service method | **Có thể cần initialize**, nếu dùng trực tiếp | Chính owner phải shutdown; tránh thiết kế này cho service thường xuyên |
| \`Executors.newFixedThreadPool(...)\` ngoài Spring | JDK factory đã tạo executor, **không có initialize() của Spring** | Chủ sở hữu phải gọi shutdown/awaitTermination |
| \`@Bean ExecutorService\` trả raw JDK pool | Không cần initialize Spring riêng cho JDK executor | Có thể dùng \`@Bean(destroyMethod="shutdown")\` hoặc destroy-method inference; cần xác định contract |

**Đừng gọi \`initialize()\` để “reset” một Spring-managed executor đang sống**. Nó có thể tạo/tráo underlying executor và làm ownership/task tracking phức tạp. Nếu cần thay cấu hình tại runtime, dùng những API được tài liệu hóa là có thể thay đổi an toàn, hoặc thiết kế một quá trình replace bean/queue dưới quyền quản lý rõ ràng.

## 7. Tạo raw JDK ExecutorService thành bean cũng có thể để Spring đóng

Có những tình huống bạn muốn dùng trực tiếp một API JDK:

~~~java
@Configuration
public class RawExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public java.util.concurrent.ExecutorService ioExecutor() {
        return java.util.concurrent.Executors.newFixedThreadPool(4);
    }
}
~~~

Đây là **một class config minh họa**, cần imports Configuration/Bean. ExecutorService từ \`Executors.newFixedThreadPool\` **đã sẵn sàng sử dụng**; không có \`initialize()\` kiểu Spring để gọi.

Khi context hủy bean, Spring sẽ gọi **shutdown()** theo cấu hình. Tuy nhiên:

- **ExecutorService.shutdown() không chờ mọi worker dừng**. Muốn chờ bounded time, cần lifecycle wrapper/owner phù hợp hoặc một destroy method thực hiện awaitTermination.
- Nếu task đang chặn, pool có thể còn sống sau khi lệnh shutdown được phát.
- Nếu consumer tiếp tục submit khi pool đã shutdown, JDK thường ném **RejectedExecutionException**.
- Với cấu hình \`@Bean\`, Spring có cơ chế **suy luận destroy method** từ public \`close\` hoặc \`shutdown\` khi phù hợp; khai báo \`destroyMethod="shutdown"\` ở đây giúp ý định rõ ràng.
- \`@Bean(destroyMethod="")\` tắt destroy-method inference cho bean tương ứng. Đừng dùng nó khi bạn thực sự mong Spring đóng pool.

**Phân biệt:** raw ExecutorService bean có thể được đóng nhờ @Bean destruction, nhưng không tự có toàn bộ **SmartLifecycle, ContextClosedEvent handling, pause/resume, waitForTasksToCompleteOnShutdown** giống các implementation Spring chuyên dụng. Đó là lý do lựa chọn \`ThreadPoolTaskExecutor\`/\`ThreadPoolTaskScheduler\` thường dễ vận hành hơn cho tác vụ Spring.

## 8. Phương pháp đối chiếu bằng Java 17 thuần: chủ sở hữu phải dọn pool

Bên ngoài Spring, tạo một pool JDK cũng hoàn toàn hợp lệ **nếu owner chủ động shutdown**. Lưu thành **ManualExecutorOwnershipDemo.java**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ManualExecutorOwnershipDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "manual-"));

        String workerName;

        try {
            Future<String> result = pool.submit(
                    () -> Thread.currentThread().getName());

            workerName = result.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Pool không kết thúc trong thời hạn");
                }
            }
        }

        System.out.println("Chạy trên manual-: "
                + workerName.startsWith("manual-"));
        System.out.println("Đã gửi shutdown: " + pool.isShutdown());
        System.out.println("Đã kết thúc tất cả worker: "
                + pool.isTerminated());
    }
}
~~~

Chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 ManualExecutorOwnershipDemo.java
java ManualExecutorOwnershipDemo
~~~

**Stdout:**

~~~text
Chạy trên manual-: true
Đã gửi shutdown: true
Đã kết thúc tất cả worker: true
~~~

**Ý nghĩa:** JDK ExecutorService được tạo và sở hữu bởi phương thức main, vì vậy main chịu trách nhiệm **shutdown(), awaitTermination()** và fallback shutdownNow() trong finally. Ở ví dụ, vì worker đã hoàn tất trước khi shutdown, trạng thái isTerminated sẽ true sau awaitTermination.

Nếu bỏ đoạn finally, process Java có thể giữ các worker threads (thường non-daemon) sống, làm chương trình/test không kết thúc như mong đợi. Với Spring-managed singleton executor, lifecycle shutdown thuộc về container khi context đóng đúng cách.

## 9. Spring lifecycle hooks thực sự là gì?

\`ExecutorConfigurationSupport\` (base class của các pool Spring ở đây) tích hợp nhiều interface/callback:

| Callback/interface | Được dùng để | Ghi chú |
| --- | --- | --- |
| **InitializingBean.afterPropertiesSet()** | Gọi initialize sau khi cấu hình bean | Khởi tạo underlying ExecutorService |
| **DisposableBean.destroy()** | Gọi shutdown khi bean bị destroy | Phụ thuộc shutdown policy |
| **SmartLifecycle.start()/stop()** | Tích hợp vòng đời start/stop/pause và phase | Spring 6.1+ có coordinated stop |
| **ApplicationListener<ContextClosedEvent>** | Biết context bắt đầu đóng | Có thể phát tín hiệu early shutdown |
| **ExecutorConfigurationSupport.initiateShutdown()** | Bắt đầu từ chối task mới, không chờ ở bước này | Khác full shutdown |
| **ExecutorConfigurationSupport.shutdown()** | Thực hiện full shutdown theo policy | Có thể chờ \`awaitTerminationSeconds\` |
| **TaskScheduler.schedule** | Đăng ký lịch ở một scheduler đã initialize | Không phải callback khởi tạo bean |

**Không đồng nhất \`destroy()\` với \`Thread.stop()\`**. Spring không giết thread bằng cơ chế cưỡng bức không an toàn; underlying executor có thể gửi interrupt (best-effort) hoặc chờ task hoàn tất tùy policy.

### Một quy trình dừng hợp lý

~~~text
ApplicationContext nhận yêu cầu đóng
    |
    +-- chặn producer nhận job mới / ngừng schedule mới
    |
    +-- SmartLifecycle stop: phối hợp ngừng các components
    |
    +-- ExecutorConfigurationSupport shutdown
    |       |
    |       +-- không nhận task mới
    |       +-- xử lý queued/running theo policy
    |       +-- có thể đợi bounded termination
    |
    +-- destroy các dependency khác (DB/client/connection pools)
    |
    +-- ApplicationContext đóng hoàn tất
~~~

Đây là **mục tiêu thiết kế**, không phải một thứ tự tự động bất biến cho tất cả beans. Nếu các producer và worker có phase khác nhau hoặc \`waitForTasksToCompleteOnShutdown\` làm shutdown diễn ra muộn, việc hủy dependency có thể đan xen. Phải cấu hình và test **thứ tự stop/close thực tế** để giữ resources còn sẵn trong lúc worker cần chúng.

## 10. Graceful shutdown: \`waitForTasksToCompleteOnShutdown\`

Một option quan trọng từ \`ExecutorConfigurationSupport\`:

~~~java
executor.setWaitForTasksToCompleteOnShutdown(true);
~~~

Nó chọn chính sách **cho phép tasks đang chạy và các tasks trong queue được thực hiện để hoàn tất** khi full shutdown, thay vì lựa chọn shutdown ngắt/đổ queue theo mặc định của base class trong trường hợp không được phối hợp đủ thời gian.

**Nhưng tên option này dễ bị hiểu sai.** Chỉ đặt \`waitForTasksToCompleteOnShutdown(true)\` không nhất thiết làm **thread gọi context.close** chờ đến khi tất cả jobs hoàn thành trước khi các beans khác bị phá hủy. Theo Javadoc Spring, với lựa chọn này shutdown có thể được tiến hành muộn trong destruction phase, còn các worker tiếp tục chạy song song với việc container hủy các dependency khác.

Nếu business job cần database connection, HTTP client hay resource do Spring quản lý, việc executor tiếp tục chạy **trong khi DataSource đang bị đóng** có thể làm job thất bại.

### Tình huống

~~~text
context.close()
   |
   +-- executor cho phép chạy hết queued jobs
   |
   +-- container bắt đầu destroy DataSource
   |
   +-- worker chưa xong; worker cố truy vấn DataSource đã đóng
   |
   +-- SQLException / job failure
~~~

Vì vậy, đối với workload cần tài nguyên khác của context, cần kết hợp **chính sách dừng producer, timeout chờ termination, thứ tự dependencies** và quy tắc bỏ/hủy job sau deadline. Không chỉ bật một boolean rồi coi là graceful.

## 11. \`awaitTerminationSeconds\`: chờ có hạn trong shutdown

Một option khác:

~~~java
executor.setAwaitTerminationSeconds(10);
~~~

Nó yêu cầu quá trình **shutdown() của executor** chờ tối đa số giây chỉ định để task kết thúc, trước khi container tiếp tục phần shutdown kế tiếp. Có thể dùng biến thể **setAwaitTerminationMillis** nếu cần chính xác theo mili giây trong API hiện đại.

So sánh hai option:

| Thuộc tính | Tác động chính | Điều không bảo đảm |
| --- | --- | --- |
| \`waitForTasksToCompleteOnShutdown(true)\` | Chính sách xử lý đầy đủ running + queued jobs trong full shutdown | Không mặc nhiên chặn toàn bộ container chờ vô hạn |
| \`awaitTerminationSeconds(10)\` | Chặn shutdown path tối đa 10 giây đợi underlying executor termination | Không ép task kẹt I/O phải hoàn tất |
| Cả hai | Cho phép drain queue rồi chờ một hạn | Không đủ nếu backlog dài hơn budget hoặc resource khác đóng sớm |
| Không thiết lập | Áp dụng shutdown/lifecycle defaults của version | Không nên suy ra 100% jobs sẽ kết thúc |

**Dừng có thời hạn** cần cân đối:

- **Worker queue length**.
- **Thời gian xử lý một job**.
- **Timeout DB/HTTP**.
- **Thời hạn terminate process của orchestrator**.
- **Số job có thể bỏ hoặc cần replay sau restart**.

Ví dụ worker queue có 500 reports, mỗi report 2 giây và chỉ một worker: 10 giây shutdown là không đủ để drain toàn bộ. Tăng timeout vô hạn cũng không phù hợp với container có termination grace period hữu hạn.

## 12. \`acceptTasksAfterContextClose\` và early shutdown

Spring 6.1+ có:

~~~java
executor.setAcceptTasksAfterContextClose(false);
~~~

Mục đích là kiểm soát việc **tiếp tục nhận task sau khi ApplicationContext bắt đầu đóng**.

- Giá trị **false** ở base class hướng tới **early soft shutdown**, sớm báo ngừng nhận tasks theo context close.
- Giá trị **true** cho phép một số producers còn submit trong stop/destruction callbacks, nhưng làm shutdown diễn ra muộn và có thể bỏ qua coordinated lifecycle stop phase.

Tuy nhiên, **ThreadPoolTaskExecutor có một nuance quan trọng từ Spring 6.1.4**: nó có \`setStrictEarlyShutdown(boolean)\`, với **default false** (lenient) trong các version 6.1.4/6.2.x. Default này cho phép một số submissions muộn ở giai đoạn context closing mà vẫn tham gia coordinated lifecycle stop. Không suy rằng mọi executor/scheduler có cùng phản ứng immediate rejection tại đúng một microsecond khi context bắt đầu đóng.

Nếu yêu cầu rất rõ rằng **ngay khi đóng context phải không nhận task mới** và muốn hành vi tường minh, xem xét:

~~~java
executor.setStrictEarlyShutdown(true);
~~~

Đây là **đoạn minh họa**. Nó ảnh hưởng chính sách early shutdown của **ThreadPoolTaskExecutor**. Các tùy chọn \`acceptTasksAfterContextClose\` hoặc \`waitForTasksToCompleteOnShutdown\` có thể **override** hành vi early shutdown của nó theo Spring Javadoc.

**Không chọn flags bằng suy đoán.** Bắt đầu từ yêu cầu shutdown:

1. Có cho phép producer nhận việc ở phase dừng không?
2. Những jobs đã accepted có bắt buộc hoàn tất không?
3. Có cần phối hợp lifecycle stop của các consumers/producers trước destroy không?
4. Có giới hạn thời gian bao nhiêu?
5. Có cần giữ dependency như DataSource sống đến khi jobs xong không?
6. Nếu một job không hoàn tất trước deadline, cần persist/retry/compensate thế nào?

Đặt các option theo câu trả lời, rồi **integration test bằng chính phiên bản Spring** được deploy.

## 13. Dùng \`strictEarlyShutdown\` đúng phạm vi

Một configuration minh họa:

~~~java
@Bean("strictJobExecutor")
public ThreadPoolTaskExecutor strictJobExecutor() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(2);
    e.setMaxPoolSize(4);
    e.setQueueCapacity(8);
    e.setStrictEarlyShutdown(true);
    e.setAwaitTerminationSeconds(10);
    return e;
}
~~~

Đây là **method @Bean minh họa**, không có chủ ý bật \`waitForTasksToCompleteOnShutdown(true)\` nên thiết kế phải chấp nhận chính sách shutdown hiện hành với running/queued tasks.

**strictEarlyShutdown** giúp gửi tín hiệu không nhận tasks mới sớm, nhưng **không bảo đảm đã hoàn tất mọi tasks** trước khi mọi component khác bắt đầu đóng. Nếu cần drain queue và kết quả bền vững, phải định nghĩa một protocol end-to-end; có thể điều chỉnh \`awaitTerminationSeconds\`, in-flight job registry và thứ tự stop phù hợp.

**Không gán toàn bộ semantics này sang \`ThreadPoolTaskScheduler\`.** Scheduler có lịch hẹn delayed/periodic và có default early-shutdown behavior riêng; xem đúng API \`ThreadPoolTaskScheduler\` và cấu hình để biết lịch nào còn được phép chạy sau shutdown.

## 14. Scheduler còn có chính sách cho delayed và periodic tasks

Một ThreadPoolTaskScheduler có thể bao gồm:

- Delayed task một lần chưa tới hạn.
- Periodic fixed-rate/fixed-delay tasks.
- Cron/Trigger-based scheduled callbacks.
- Callback đang thực thi.
- Callback có thể delegate thêm việc sang một worker pool khác.

Một số thuộc tính liên quan:

~~~java
scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);

scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

scheduler.setRemoveOnCancelPolicy(true);
~~~

Đây là **đoạn minh họa** cho ThreadPoolTaskScheduler khi underlying implementation hỗ trợ.

**Ý nghĩa khái quát:**

- \`continueExistingPeriodicTasksAfterShutdownPolicy\`: nếu cho phép, tác vụ periodic đã có thể tiếp tục được thực hiện sau khi underlying scheduler bắt đầu shutdown; thường **không mong muốn** khi ứng dụng chuẩn bị dừng.
- \`executeExistingDelayedTasksAfterShutdownPolicy\`: ảnh hưởng việc những delayed one-shot tasks chưa tới hạn có còn được thực hiện sau shutdown hay không.
- \`removeOnCancelPolicy\`: quyết định cách các tasks đã bị hủy được loại khỏi delayed queue của ScheduledThreadPoolExecutor, liên quan đến giữ bộ nhớ.

Các flag này tương tác với shutdown mode và framework lifecycle. **Không lấy từng flag độc lập làm bảo đảm application đã tắt đúng hoặc mọi callback đều bị hủy sạch**. Trong ví dụ Maven, các delayed/periodic policies được đặt false để định nghĩa rõ rằng demo không chủ trương chạy những công việc lịch chưa bắt đầu sau shutdown.

### Hủy ScheduledFuture không có nghĩa hủy business job đã delegate

~~~text
ThreadPoolTaskScheduler
    |
    +-- @Scheduled callback --> jobExecutor.submit(job A)
              |
              +-- callback returns
                          |
                          +-- job A still RUNNING on jobExecutor
~~~

Khi đóng scheduler hoặc cancel ScheduledFuture, **job A không tự động bị hủy** nếu đã được submit vào một executor độc lập. Phải quản lý **hai vòng đời**: scheduler không kích hoạt lượt mới và worker pool xử lý những job đã tiếp nhận.

## 15. @Async: container chọn đúng executor rồi mới chạy method

Khi một method dùng:

~~~java
@Async("jobExecutor")
public void refresh() {
    // ...
}
~~~

Spring gọi method qua **proxy** và gửi body vào executor được chọn. Để hoạt động đúng cần:

- \`@EnableAsync\` hoặc async processing cấu hình tương đương.
- Bean chứa method được Spring quản lý.
- Lời gọi **đi qua Spring proxy** (self-invocation như \`this.refresh()\` trong cùng bean thường không được intercept ở chế độ proxy).
- Executor có tên/qualifier phù hợp, đã được container initialize.
- Lỗi submit và lỗi trong worker được xử lý phù hợp return type.

### Cấu hình có một executor rõ tên

~~~java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("jobExecutor")
    public ThreadPoolTaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(4);
        e.setQueueCapacity(24);
        e.setThreadNamePrefix("async-");
        e.setAwaitTerminationSeconds(10);
        return e;
    }
}
~~~

Đây là **một configuration class minh họa**; cần import Configuration, EnableAsync, Bean và ThreadPoolTaskExecutor.

Nếu chỉ có \`@Async\` mà không chọn executor tường minh, Spring có cơ chế tìm executor mặc định hoặc tạo fallback executor, tùy cấu hình. **Đừng giả định mọi @Async chắc chắn chạy trên bean jobExecutor** chỉ vì bean đó tồn tại, đặc biệt khi có nhiều TaskExecutor beans và các auto-configurations.

Khi container đóng, proxy không làm cho một method đang chạy tự động hoàn tác side effects. Shutdown pool là phần lifecycle của **TaskExecutor**, không phải business cancellation protocol của method.

## 16. @Scheduled: để container sở hữu cả lịch lẫn scheduler

Một ví dụ:

~~~java
@Configuration
@EnableScheduling
public class SchedulingConfig {
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s =
                new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("schedule-");
        s.setAwaitTerminationSeconds(10);
        return s;
    }
}
~~~

Và một scheduled bean:

~~~java
@Component
public class CleanupTrigger {
    @Scheduled(fixedDelay = 30_000)
    public void cleanup() {
        // Công việc ngắn hoặc gửi việc sang jobExecutor.
    }
}
~~~

Đây là **hai class Spring minh họa**; cần imports phù hợp. Khi context refresh, Spring phát hiện \`@Scheduled\` trên bean và sử dụng TaskScheduler phù hợp, nếu configuration hợp lệ.

Khi context đóng:
- Hạ tầng scheduling hủy registrations theo lifecycle.
- ThreadPoolTaskScheduler được đóng theo policy riêng.
- Tasks đang chạy được xử lý theo interruption/drain settings.
- Nếu callback đã gửi business jobs sang executor khác, phải đảm bảo executor đó cũng có shutdown policy.

**Nếu bạn tạo thủ công một ThreadPoolTaskScheduler trong một method rồi tự gọi initialize và schedule**, task đó không tự thuộc quyền sở hữu của context. Cần giữ handle/lifecycle để dừng nó. Tốt nhất là đưa scheduler thành bean và inject.

## 17. Không tự shutdown shared bean sau mỗi request/job

Lỗi điển hình:

~~~java
@Service
public class ReportService {
    private final ThreadPoolTaskExecutor sharedExecutor;

    public ReportService(ThreadPoolTaskExecutor sharedExecutor) {
        this.sharedExecutor = sharedExecutor;
    }

    public void submit() {
        sharedExecutor.execute(this::generateReport);

        // SAI: Bean còn được dùng chung bởi những request khác.
        sharedExecutor.shutdown();
    }

    private void generateReport() {
        // ...
    }
}
~~~

Đây là **class minh họa hành vi sai**. Một singleton ThreadPoolTaskExecutor có thể được nhiều services sử dụng. Nếu service A gọi shutdown sau khi submit việc của nó:

- Service B có thể bị TaskRejectedException dù context vẫn còn hoạt động.
- Jobs đã xếp hàng có thể bị hủy/gián đoạn theo policy.
- Metrics và lifecycle của Spring không còn phản ánh đúng kỳ vọng “bean đang hoạt động”.
- Khi container đóng sau đó, sẽ có một lần shutdown bổ sung, nhưng không sửa được hậu quả trước đó.

**Chỉ component thực sự sở hữu bean/pool mới quyết định shutdown.** Đối với bean do ApplicationContext quản lý, owner đó thường là container. Nếu một job cần cancellation riêng, hủy **Future/job handle** hoặc gửi cancellation token, **không đóng cả executor dùng chung**.

## 18. Không tạo một executor mới cho từng HTTP request

Anti-pattern khác:

~~~java
public void handleRequest() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(2);
    e.initialize();

    e.execute(() -> doWork());
    // Quên e.shutdown().
}
~~~

Đây là **đoạn minh họa sai**, doWork do ứng dụng định nghĩa.

Mỗi request có thể tạo underlying executor, các threads/queues riêng, khó giới hạn tổng số, không có owner shutdown rõ và dễ rò rỉ tài nguyên. Nếu hàng nghìn request cùng xảy ra, số pools tăng theo request count.

Đúng hơn, **inject một bounded singleton TaskExecutor** và gửi job vào đó:

~~~java
@Service
public class ReportService {
    private final TaskExecutor executor;

    public ReportService(
            @Qualifier("jobExecutor") TaskExecutor executor) {
        this.executor = executor;
    }

    public void handleRequest() {
        executor.execute(this::doWork);
    }

    private void doWork() {
        // ...
    }
}
~~~

Đây là **class Spring minh họa**, imports cần bao gồm TaskExecutor, Service và Qualifier. Đối với production, xử lý rejection, snapshot input, timeout, permission, logging và kết quả job.

**Một singleton pool không có nghĩa các request chia sẻ mutable request data.** Phải truyền DTO bất biến/cancellation/deadline theo job, không lưu input của từng request vào field mutable của bean singleton.

## 19. Khả năng bị từ chối việc lúc shutdown

Khi executor bắt đầu shutdown:

~~~java
try {
    executor.execute(task);
} catch (TaskRejectedException rejected) {
    // Executor không tiếp nhận công việc theo contract.
}
~~~

Đây là **đoạn minh họa** với executor kiểu Spring TaskExecutor và task là Runnable đã định nghĩa.

Một số tình huống dẫn tới rejection:

- **Context đang đóng**, executor đã ngừng tiếp nhận.
- **Worker pool + queue bị bão hòa**.
- Executor đã bị consumer tự shutdown nhầm.
- Component submit vào bean sai vì chọn nhầm qualifier.
- Executor implementation có rejection policy do ứng dụng cấu hình.

**TaskRejectedException không nhất thiết có nghĩa chỉ vì context close**. Khi điều tra, ghi trạng thái lifecycle, pool size/active count, queue capacity/size, reason/code và số lượng tác vụ đã accepted.

### Nếu task bắt buộc phải được xử lý

Nếu job không được mất:
1. Ghi nhận vào **durable queue/job store** trước khi trả response xác nhận thành công.
2. Có idempotency key để retry không tạo side effect hai lần.
3. Khi shutdown, ngừng producer mới rồi drain accepted jobs trong ngân sách.
4. Sau restart, resume các jobs pending chưa có acknowledgement.
5. Không chỉ retry vô hạn vào một executor đã shutdown.

Một TaskExecutor trong RAM không tự là durable queue, dù được Spring quản lý lifecycle hoàn hảo.

## 20. \`waitForTasksToCompleteOnShutdown\` không đồng nghĩa giao dịch thành công

Giả sử executor được cấu hình:

~~~java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(30);
~~~

Nếu một job gọi payment gateway, DB và email, shutdown có thể cho nó tiếp tục chạy. Nhưng:

- Payment gateway đã nhận request trước khi worker báo lỗi.
- DB transaction có thể commit/rollback tùy transaction boundary.
- Client HTTP có timeout riêng.
- Một email đã gửi không tự được hoàn tác.
- Trong một container có termination grace period ngắn hơn 30 giây, process có thể bị kill trước khi await kết thúc.
- Nếu queue dài, 30 giây không đủ cho tất cả jobs.

Cần quan sát **business outcome** riêng, không kết luận \`awaitTermination\` trả true = mọi tác động nghiệp vụ mong đợi thành công. Kết thúc threads chỉ nói hệ thống thực thi đã chấm dứt; còn success/failure/compensation thuộc về job status.

## 21. Lifecycle shutdown order: producer, scheduler, worker và dependencies

Một ứng dụng có thể có chuỗi:

~~~text
HTTP/API producer  --->  @Scheduled trigger
         \                   /
          \                 /
           v               v
            TaskExecutor workers
                    |
             +------+------+
             |             |
             v             v
          Database      HTTP client
~~~

Một chiến lược shutdown mong muốn:

1. **Ngừng nhận công việc mới ở producer** (HTTP readiness/drain, queue consumers).
2. **Ngừng lập lịch mới** trong TaskScheduler.
3. **Đợi hoặc hủy jobs đã accepted** theo policy.
4. **Khi worker không còn cần dependency**, mới đóng những resources DB/HTTP/file.
5. **Ghi trạng thái các jobs chưa xử lý** cho restart nếu workload bắt buộc bền vững.

Đây là **mục tiêu phối hợp**, không phải cam kết một annotation duy nhất sẽ tạo ra đúng thứ tự. Spring điều phối lifecycle theo **SmartLifecycle phase, dependencies và destruction order**, nhưng khi có nhiều \`@Bean\` và callback close/stop, cần kiểm tra thứ tự thực tế.

### SmartLifecycle phase

Spring 6.2 dùng một default phase đặc thù cho các executor/scheduler kế thừa ExecutorConfigurationSupport. Mục đích là dành không gian cho những lifecycle components khác phối hợp start/stop. Ứng dụng có thể tùy chỉnh \`setPhase(int)\` khi có lý do và test chắc chắn, nhưng **đừng tự đổi phase tùy tiện chỉ để “cho worker dừng cuối”**.

Với SmartLifecycle:
- **start** thường theo phase tăng dần.
- **stop** thường theo phase giảm dần.
- Dependencies của bean cũng ảnh hưởng thứ tự.
- \`stop(Runnable callback)\` cho phép component báo hoàn tất stop bất đồng bộ cho lifecycle coordinator.

Vì các option **acceptTasksAfterContextClose** và **waitForTasksToCompleteOnShutdown** có thể thay đổi việc tham gia coordinated stop, chỉ đổi phase không giải quyết mọi tương tác.

### Bean có thể bị đóng trong lúc worker còn dùng

Nếu context tiếp tục destroy database connections trong khi worker chưa thoát, task có thể gặp lỗi. Một \`awaitTerminationSeconds\` bounded và phân chia phase hợp lý giúp giảm rủi ro, nhưng vẫn cần **shutdown integration test** của hệ thống thật.

## 22. Spring Boot: tự cấu hình khác với bean tùy chỉnh

Nếu dùng Spring Boot, có thể đã có executor/scheduler beans do **auto-configuration** tạo theo điều kiện và phiên bản. Các thuộc tính cấu hình và cách lựa chọn bean phụ thuộc Boot release.

Một số ứng dụng có cả:
- Boot auto-configured task executor.
- Custom \`ThreadPoolTaskExecutor\` hoặc \`ThreadPoolTaskScheduler\`.
- AsyncConfigurer/SchedulingConfigurer.
- @Async có qualifier khác nhau.
- Internal scheduler của một thư viện/SDK.
- Raw JDK ExecutorService bean riêng.

**Đừng mặc định chỉ có một pool vì bạn mới viết một @Bean.** Cần kiểm tra:
1. Có bao nhiêu TaskExecutor/TaskScheduler bean trong context?
2. @Async mặc định thực sự chọn bean nào?
3. @Scheduled chọn scheduler nào?
4. Có thư viện tạo riêng thread pool ngoài Spring không?
5. Runtime profile/test có thay đổi auto-configuration không?
6. Tất cả các pools có owner và shutdown path rõ không?

### Một cấu hình tường minh dễ kiểm tra

~~~java
@Async("jobExecutor")
public CompletableFuture<String> calculate() {
    return CompletableFuture.completedFuture("done");
}
~~~

Đây là **đoạn minh họa**. Đặt tên rõ tránh nhầm executor khi có nhiều beans. Dù return CompletableFuture, **khả năng cancel underlying work** không thể suy từ việc caller gọi \`CompletableFuture.cancel(true)\`, vì cancellation semantics của CompletableFuture khác FutureTask. Hãy tách business cancellation khỏi lifecycle shutdown.

## 23. Shutdown của @Scheduled khác shutdown của các job đã delegate

Một \`@Scheduled\` callback có thể chỉ là “bộ phát việc”:

~~~java
@Scheduled(fixedRate = 10_000)
public void tick() {
    executor.execute(() -> processReport());
}
~~~

Đây là **đoạn minh họa** với executor và processReport trong scope.

Khi container dừng:
- **TaskScheduler** cần ngừng kích hoạt tick mới.
- **TaskExecutor** cần xử lý jobs đã accepted.
- Một callback vừa được kích hoạt có thể **submit đúng thời điểm pool worker đã bắt đầu shutdown** và nhận TaskRejectedException.
- Để tránh mất job, cần phối hợp **producer quiescence trước worker shutdown** hoặc persist job ở tầng bền vững.

**Không cho rằng dừng scheduler sẽ tự dừng tất cả tasks đã được submit vào TaskExecutor khác.** Nếu tick dùng \`schedule\` và trả về ScheduledFuture, handle của scheduler đại diện cho callback/lịch, không mặc nhiên đại diện cho business work chạy ở worker.

## 24. Đừng dùng @PreDestroy để kích hoạt một @Async mới rồi hy vọng nó chạy xong

Anti-pattern:

~~~java
@PreDestroy
public void shutdown() {
    asyncCleanup(); // Có thể gọi qua self-invocation và mất proxy.
}

@Async("jobExecutor")
public void asyncCleanup() {
    // Dọn dữ liệu.
}
~~~

Đây là **đoạn minh họa hành vi không đáng tin**. Spring documentation lưu ý **@Async không được dùng như một cơ chế tự động cho lifecycle callbacks**; lời gọi trong cùng object thường bỏ qua proxy và thời điểm destruction đã quá muộn để dựa vào việc submit mới.

Nguy cơ:
- Executor đã bắt đầu ngừng nhận việc.
- @Async proxy không được áp dụng qua self-invocation.
- Bean phụ thuộc có thể đã bị destroy.
- @PreDestroy không chờ background task tự chạy xong.
- App có thể bị kill theo timeout deployment.

Cách tốt hơn:
- Xử lý dừng producer và drain jobs **trước khi vào destruction phase**.
- Nếu cần asynchronous shutdown coordination, dùng **SmartLifecycle** hoặc lifecycle coordinator có state, timeout và callback hoàn tất.
- Nếu cleanup cần đủ thời gian và transactional consistency, thực hiện ngay trong phase thích hợp thay vì “fire-and-forget” khi container đang đóng.

## 25. Multiple ApplicationContexts và multiple pods

Ownership của bean chỉ có hiệu lực **trong ApplicationContext chứa bean**.

Nếu một JVM tạo hai contexts:

~~~text
JVM
  +-- context A -> jobExecutor A -> scheduler A
  +-- context B -> jobExecutor B -> scheduler B
~~~

Đóng context A **không tự đóng** các pools do context B sở hữu. Ngược lại, nếu một library tạo raw pools ngoài cả hai contexts, đóng cả A/B cũng không chắc shutdown các raw pools ấy.

Ở Kubernetes hoặc deployment nhiều replicas:

~~~text
pod-1 -> context-1 -> executor-1
pod-2 -> context-2 -> executor-2
pod-3 -> context-3 -> executor-3
~~~

Mỗi pod tự đóng pool của mình khi process/context dừng đúng cách. **Spring không tự phối hợp queue hoặc lịch qua các pods**, và graceful shutdown một pod không đảm bảo job chạy ở pod khác biết trạng thái job cũ.

Nếu job phải chạy đúng một lần toàn cluster, cần cơ chế distributed coordination/idempotency. Nếu job không được mất qua pod restart, cần durable queue/job store hoặc workflow engine.

### Điều gì xảy ra nếu process bị kill đột ngột?

Không có Java finally, @PreDestroy hoặc Spring destroy callback nào được **bảo đảm chạy** khi process bị SIGKILL, OOM crash hoặc node chết đột ngột. Vì vậy, những workflows quan trọng phải có:
- Durable input/queue.
- Idempotency/at-least-once processing.
- Lease/visibility timeout hoặc checkpoint.
- Recovery sau restart.
- Audit job outcome.

Managed lifecycle giúp dừng **bình thường**, không thay thế thiết kế chịu lỗi.

## 26. Những thông số cần cân nhắc trước khi đóng pool

| Thông số | Ảnh hưởng | Rủi ro khi chọn sai |
| --- | --- | --- |
| \`corePoolSize\` | Mức worker nền của ThreadPoolTaskExecutor | Quá nhỏ dễ queue wait lâu |
| \`maxPoolSize\` | Trần số workers khi queue không còn chỗ | Quá lớn có thể quá tải DB/CPU |
| \`queueCapacity\` | Bao nhiêu việc chờ được nhận | Queue quá lớn khiến shutdown/drain lâu |
| \`threadNamePrefix\` | Dễ truy tìm logs/threads | Thiếu nhãn khiến khó debug shutdown |
| \`waitForTasksToCompleteOnShutdown\` | Policy xử lý running/queued tasks | Drain dài, thiếu bounded wait, phụ thuộc resource |
| \`awaitTerminationSeconds\` | Thời gian shutdown() đợi | Quá ngắn mất job, quá dài vượt deployment grace |
| \`acceptTasksAfterContextClose\` | Có tiếp nhận submissions muộn | Việc mới được nhận lúc app sắp tắt |
| \`strictEarlyShutdown\` | Early rejection cho ThreadPoolTaskExecutor | Producer chưa kịp dừng có thể bị reject |
| \`phase\` | Thứ tự lifecycle stop/start | Sai thứ tự khiến worker mất dependencies |
| Scheduler delayed/periodic policies | Quy tắc khi shutdown | Tick vẫn nổ hoặc delayed job bị bỏ không chủ đích |

**Pool size và queue size là capacity tuning**, không tự mang ý nghĩa “dừng an toàn”. Với workload quan trọng, cần xác định throughput, service time, queue wait budget và deadline trước khi đặt các con số.

## 27. Monitoring khởi tạo, chạy và dừng

Một dashboard/log hữu ích nên phân biệt:

**Ở startup**
- Bean name và loại executor/scheduler.
- Config được áp dụng: core/max/queue, rejection handler, thread prefix.
- \`initialized/ready\` khi underlying pool được tạo.
- Số scheduled registrations được phát hiện.
- Số TaskExecutors/Schedulers thực sự tồn tại trong mỗi context.

**Khi hoạt động**
- Active worker count, current pool size.
- Queue size/capacity và submission rejections.
- Task latency: từ accepted tới worker start; worker execution duration.
- Scheduled callback lag và trigger errors.
- In-flight jobs theo business jobId.

**Ở shutdown**
- Context closed event time.
- Producer stopped accepting new work.
- Scheduler stopped issuing triggers.
- Executor shutdown initiated, accepted/rejected late submissions.
- Số tasks đang chạy và đang queue khi stop.
- Drain duration và số tasks hoàn tất/failed/cancelled.
- \`isShutdown()\` và \`isTerminated()\` phân biệt.
- Các job pending cần replay sau restart.

**Nếu metrics ghi "pool shutdown" nhưng active jobs vẫn >0**, đó có thể không mâu thuẫn: \`isShutdown\` báo không nhận thêm việc; \`isTerminated\` mới phản ánh tất cả tasks/threads đã dừng hẳn.

## 28. Kiểm thử lifecycle không cần chờ may rủi

### Test bean initialization

1. Tạo \`AnnotationConfigApplicationContext\` từ configuration class.
2. Lấy bean \`ThreadPoolTaskExecutor\` và \`ThreadPoolTaskScheduler\`.
3. Gọi \`getThreadPoolExecutor()\`/\`getScheduledThreadPoolExecutor()\` — các API này có thể throw nếu chưa initialize.
4. Assert underlying executor chưa shutdown và nhận tác vụ.
5. Không tự gọi initialize trong test để vô tình che lỗi bean lifecycle.

### Test task execution và thread ownership

1. Đặt threadNamePrefix riêng cho scheduler và executor.
2. Đăng ký một **one-shot scheduled callback**.
3. Callback ghi nhận tên thread, submit job sang TaskExecutor.
4. Main dùng \`ScheduledFuture.get(timeout)\` và \`CountDownLatch.await(timeout)\` để xác nhận cả callback và worker.
5. Kiểm tra hai tiền tố khác nhau; đừng dùng Thread.sleep cố đoán thứ tự.

### Test context shutdown

1. Đợi những công việc của test hoàn tất.
2. Gọi \`context.close()\` trong finally.
3. Kiểm tra underlying executor/scheduler \`isShutdown()\`.
4. Nếu cần xác nhận termination, dùng \`isTerminated()\` hoặc \`awaitTermination()\` với deadline và chính sách tương thích.
5. Không dừng ở assert context.isActive()==false rồi coi mọi worker đã hết.

### Test graceful drain với job đang chạy

Để test chính sách drain, đưa một worker vào \`CountDownLatch.await()\` và giữ vài queued tasks. Khởi động \`context.close()\` **trên một thread test riêng**, đợi tín hiệu giai đoạn shutdown thích hợp, rồi mở latch để worker thoát. Sau đó kiểm tra in-flight/queue outcomes theo policy. Đặt timeouts cho cả close thread lẫn worker. Không dùng latch vô hạn hoặc cleanup sai khiến test/JVM bị treo.

### Test rejection khi close

Dùng producer test phối hợp bằng latch để submit ở mốc mà executor đã khởi động shutdown; xác minh TaskRejectedException hoặc rejection behavior theo policy. **Không test bằng vòng lặp bắn hàng nghìn jobs cho đến khi tình cờ reject**, dễ không ổn định và tạo tải không mong muốn.

## 29. Lỗi thực tế phổ biến

**Lỗi 1 — Tạo \`new ThreadPoolTaskExecutor()\` rồi gọi execute mà không initialize.**

Ngoài Spring, underlying pool có thể chưa được tạo. Dùng Spring-managed bean hoặc khởi tạo theo API khi bạn thực sự sở hữu nó.

**Lỗi 2 — Gọi \`initialize()\` trong mọi @Bean cho “chắc”.**

Không cần; container sẽ gọi \`afterPropertiesSet()\`. Dễ làm người đọc hiểu sai owner và ranh giới lifecycle.

**Lỗi 3 — Tạo pool trong constructor/@PostConstruct và không đăng ký bean.**

Spring chỉ quản lý **đối tượng được đăng ký**, không tự phát hiện field chứa raw executor để shutdown.

**Lỗi 4 — Tự gọi \`sharedExecutor.shutdown()\` sau một job.**

Làm những producers khác bị reject. Chỉ owner của shared pool được quyết định shutdown.

**Lỗi 5 — \`@Bean(destroyMethod="")\` cho raw ExecutorService nhưng kỳ vọng Spring tự gọi shutdown.**

Đây là cách tắt destroy inference. Nếu cần shutdown, khai báo destroy method/lifecycle đúng.

**Lỗi 6 — Chỉ bật \`waitForTasksToCompleteOnShutdown(true)\` và nghĩ context.close chắc chắn chờ mọi job.**

Còn cần xem \`awaitTerminationSeconds\`, lifecycle phase, thời gian deployment và resource dependencies.

**Lỗi 7 — Chỉ bật \`awaitTerminationSeconds(30)\` và nghĩ mọi queue tasks luôn được chạy hết.**

Nó kiểm soát khoảng chờ, không tự đổi mọi cancellation policy hay bảo đảm 30 giây đủ.

**Lỗi 8 — Mong một pool continue periodic schedules vô hạn sau context close.**

Shutdown không phải chế độ chạy nền lâu dài. Nếu muốn tiếp tục job sau restart, dùng durable job system bên ngoài.

**Lỗi 9 — Để scheduler gửi việc sang worker đang shutdown mà không xử lý rejection.**

Callbacks có thể bị từ chối khi worker đã đóng; cần stop producer trước hoặc có durable queue.

**Lỗi 10 — Nghĩ \`isShutdown()==true\` tương đương \`isTerminated()==true\`.**

Sai. Shutdown là trạng thái không nhận việc; termination đợi mọi tasks/threads kết thúc.

**Lỗi 11 — Nghĩ @Scheduled và @Async dùng cùng executor.**

Không mặc nhiên. @Scheduled dùng TaskScheduler; @Async dùng AsyncTaskExecutor được chọn. Có thể cấu hình tên rõ và test thread prefixes.

**Lỗi 12 — Tăng corePoolSize mà không xét queueCapacity hoặc khả năng DB.**

Điều chỉnh thread pool không giải quyết tự động bottleneck downstream.

**Lỗi 13 — Trong @PreDestroy gọi @Async để cleanup rồi return.**

Có thể bỏ qua proxy, bị reject hoặc context hủy tài nguyên trước khi công việc hoàn tất.

**Lỗi 14 — Chỉ có một bean trong mỗi pod rồi nghĩ chỉ có một pool toàn cluster.**

Mỗi process có pool riêng. Lịch và business side effects cần cluster coordination nếu nghiệp vụ yêu cầu.

**Lỗi 15 — Trông cậy vào @PreDestroy khi process bị SIGKILL.**

Không có cam kết chạy cleanup callback khi bị kill cưỡng bức. Cần recovery/durability.

**Lỗi 16 — Không đặt deadline cho network calls rồi kỳ vọng interrupt shutdown luôn hiệu quả.**

Nhiều API blocking không bảo đảm phản ứng tức thì với Thread.interrupt. Đặt connect/read/query timeouts theo downstream.

**Lỗi 17 — Chạy unit test tự tạo pool nhưng không shutdown, khiến test process treo.**

Dù production bean được container quản lý, test tự new raw executors vẫn phải tự dọn.

**Lỗi 18 — Đếm có một Scheduler bean nhưng có thêm scheduler tạo bởi library ngoài Spring.**

Cần kiểm tra toàn bộ thread dump và ownership các nguồn tạo threads.

## 30. Câu hỏi tự kiểm tra

**Câu 1.** Khi khai báo \`@Bean ThreadPoolTaskExecutor\`, có phải tự gọi initialize trong @Bean method không?

**Đáp án:** Không trong cấu hình bình thường. Spring gọi InitializingBean.afterPropertiesSet(), dẫn tới initialize sau khi bean được cấu hình.

**Câu 2.** Khi Spring ApplicationContext.close(), nó gọi gì trên ThreadPoolTaskExecutor?

**Đáp án:** Có các bước ContextClosedEvent/SmartLifecycle stop tùy cấu hình, và ở destruction sẽ gọi DisposableBean.destroy(), sau đó shutdown() theo policy. Không suy rằng mọi worker đã kết thúc ngay thời điểm phát tín hiệu.

**Câu 3.** \`new ThreadPoolTaskExecutor()\` ngoài context có tự được dọn khi context đóng không?

**Đáp án:** Không, trừ khi đối tượng đó được đăng ký/quản lý đúng cách bởi container hoặc owner khác. Một raw field không phải bean tự động.

**Câu 4.** \`isShutdown()==true\` có chứng minh mọi task hoàn tất không?

**Đáp án:** Không. Dùng \`isTerminated()\`, awaitTermination và job outcome khi cần.

**Câu 5.** Tại sao \`waitForTasksToCompleteOnShutdown(true)\` không đủ để giữ DataSource luôn sống tới lúc mọi job dùng xong?

**Đáp án:** Nó chọn chính sách drain; container có thể tiếp tục destroy beans khác khi worker vẫn chạy. Cần chờ bounded và phối hợp lifecycle/dependency order.

**Câu 6.** \`awaitTerminationSeconds(10)\` có cưỡng bức dừng công việc chặn I/O quá 10 giây không?

**Đáp án:** Không. Nó giới hạn thời gian chờ trong shutdown; không bảo đảm API I/O phản ứng interrupt hoặc dừng đúng hạn.

**Câu 7.** Một service gọi \`shutdown()\` trên shared singleton executor sau khi submit task của nó. Hệ quả?

**Đáp án:** Producers khác bị reject; service đã tự đóng tài nguyên do container sở hữu không đúng phạm vi.

**Câu 8.** \`@Scheduled\` callback gửi job sang executor khác. Khi scheduler đóng, job đã gửi tự dừng không?

**Đáp án:** Không mặc nhiên. Worker executor có lifecycle riêng.

**Câu 9.** Có thể dùng \`@Bean(destroyMethod="shutdown")\` cho raw JDK ExecutorService không?

**Đáp án:** Có. Container gọi shutdown khi destroy bean, nhưng cần cơ chế chờ/cleanup nếu business yêu cầu.

**Câu 10.** Spring-managed ThreadPoolTaskExecutor đã initialize nhưng getPoolSize()==0. Có lỗi không?

**Đáp án:** Không nhất thiết. Worker threads có thể được tạo theo nhu cầu; underlying executor ready khác với số worker đã được tạo.

**Câu 11.** Vì sao không gọi @Async trong @PreDestroy để dọn dữ liệu dài?

**Đáp án:** @Async không phải callback lifecycle được khuyến nghị, self-invocation có thể bỏ qua proxy, executor có thể đã shutdown và dependencies có thể đang bị destroy.

**Câu 12.** ThreadPoolTaskExecutor 6.2 có strictEarlyShutdown mặc định true hay false?

**Đáp án:** Từ Spring 6.1.4, default của \`ThreadPoolTaskExecutor.setStrictEarlyShutdown\` là **false**. Không đánh đồng với base class acceptTasksAfterContextClose; xem Javadoc/version.

**Câu 13.** Một job rất quan trọng đang nằm trong queue khi pod bị SIGKILL. Spring có bảo đảm job tiếp tục chạy sau restart không?

**Đáp án:** Không. Cần durable job store/queue và cơ chế recovery, idempotency.

**Câu 14.** Một @Scheduled method chạy mỗi phút trên ba pods. Bean-managed scheduler có đảm bảo chỉ một callback toàn hệ thống?

**Đáp án:** Không. Mỗi pod có ApplicationContext/scheduler riêng; phải có distributed coordination nếu cần.

**Câu 15.** Khi shutdown executor, vì sao nên ngừng scheduler/producer gửi job trước?

**Đáp án:** Tránh các submissions mới tới pool đã không nhận việc, giảm rejection và cho thời gian drain những tasks đã được chấp nhận.

**Câu 16.** Có nên đổi SmartLifecycle phase mà không có test shutdown không?

**Đáp án:** Không. Phase và flags early/late shutdown cùng bean dependencies có tương tác; cần test đúng runtime.

## 31. Checklist trước khi triển khai

- [ ] Mỗi executor/scheduler có **owner** rõ (ưu tiên Spring ApplicationContext đối với shared pools).
- [ ] \`ThreadPoolTaskExecutor\` và \`ThreadPoolTaskScheduler\` được khai báo @Bean hoặc đăng ký bean hợp lệ.
- [ ] Không gọi initialize thủ công trong @Bean khi container đã làm lifecycle.
- [ ] Không tạo pool ở từng HTTP request hoặc mỗi scheduled tick.
- [ ] Có tên bean/qualifier và threadNamePrefix để kiểm tra executor thực sự được chọn.
- [ ] Worker pool cấu hình core/max/queue **hữu hạn phù hợp** với downstream capacity.
- [ ] Nếu có @Async, bật async support và bảo đảm lời gọi qua proxy.
- [ ] Nếu có @Scheduled, bật scheduling và chọn đúng TaskScheduler.
- [ ] Không coi scheduler callback completion là worker business job completion nếu có delegation.
- [ ] Producer ngừng nhận job trước khi shutdown worker pool hoặc có policy rejection rõ.
- [ ] Quy định running/queued tasks khi shutdown: drain, cancel, skip, replay hay compensate.
- [ ] Hiểu \`waitForTasksToCompleteOnShutdown\` và \`awaitTerminationSeconds\` là hai mối quan tâm khác.
- [ ] Xác định \`acceptTasksAfterContextClose\` và \`strictEarlyShutdown\` theo phiên bản Spring.
- [ ] Với scheduler, xem delayed/periodic tasks còn được chạy sau shutdown theo policy nào.
- [ ] Đảm bảo thứ tự đóng scheduler, executor và dependencies (DB/HTTP/file) phù hợp.
- [ ] Có deadline toàn cục, connect/read/query timeouts và xử lý interruption hợp tác.
- [ ] Không tự shutdown singleton bean từ một consumer/service.
- [ ] Với raw JDK ExecutorService bean, khai báo/suy luận destroy method có chủ đích.
- [ ] Không tắt destroy inference bằng \`destroyMethod=""\` khi chưa có owner thay thế.
- [ ] Đóng ApplicationContext trong integration tests; shutdown raw executors trong finally.
- [ ] Giám sát \`isShutdown\`, \`isTerminated\`, active jobs, queue backlog và rejections.
- [ ] Đặt termination grace period ở deployment đủ cho bounded shutdown path.
- [ ] Với multi-pod, có distributed coordination nếu một job phải chạy đơn nhất toàn cluster.
- [ ] Với jobs không được mất, có durable queue/job store, checkpoint và idempotency.
- [ ] Test startup/run/close có latch và timeout, không chỉ Thread.sleep.

## Kết luận

**Để Spring container khởi tạo và đóng \`ThreadPoolTaskExecutor\`/\`ThreadPoolTaskScheduler\` được khai báo thành bean là mô hình ownership chính xác cho hầu hết shared pools trong ứng dụng Spring.** Spring gọi lifecycle initialization sau khi cấu hình, quản lý tích hợp \`@Async\`/\`@Scheduled\` và đưa pool vào trình tự shutdown khi ApplicationContext đóng. Điều này giảm rò rỉ thread, tránh việc mỗi service tự tạo/dừng pool và giúp quan sát tập trung.

Nhưng **container-managed lifecycle không phải bảo đảm mọi job đều thành công hoặc đã dừng vật lý ngay**. Thiết kế có trách nhiệm phải phân biệt **accepting/stopping/shutdown/terminated**, chọn chính sách xử lý running/queued tasks, chờ có hạn bằng \`awaitTerminationSeconds\`, phối hợp producers/scheduler/dependencies và xử lý thời gian dừng ở deployment. Nếu công việc cần bền vững qua restart hoặc có side effect quan trọng, phải kết hợp **durable job state, retry an toàn và idempotency**.

**Nguồn tham khảo chính thức:**

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): TaskExecutor, ThreadPoolTaskExecutor, TaskScheduler, ThreadPoolTaskScheduler, @Async, @Scheduled.
- [Spring Framework — ExecutorConfigurationSupport Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html): afterPropertiesSet, initialize, destroy, shutdown, waitForTasksToCompleteOnShutdown, awaitTermination, ContextClosedEvent và lifecycle flags.
- [Spring Framework — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): core/max/queue, strictEarlyShutdown, native executor introspection, TaskRejectedException.
- [Spring Framework — ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): scheduler pool, delayed/periodic policies và ScheduledExecutorService.
- [Spring Framework — Bean lifecycle](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html): initialization/destruction callbacks, SmartLifecycle, inferred destroy methods trên @Bean.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): shutdown, shutdownNow, awaitTermination, isShutdown và isTerminated.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-shutdown-002">Phân biệt dừng nhận tác vụ mới với chờ tác vụ đang chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt dừng nhận tác vụ mới với chờ tác vụ đang chạy

Trong Spring Framework và Java concurrency, **ngừng tiếp nhận tác vụ mới** (*stop admission*) khác với **chờ những tác vụ đã nhận hoàn tất** (*drain*). Một executor có thể từ chối mọi tác vụ mới nhưng vẫn đang chạy tác vụ cũ; nó cũng có thể còn rất nhiều công việc nằm trong queue. Nếu nhầm hai pha này, ứng dụng dễ mất job khi deploy, trả thông báo shutdown thành công quá sớm hoặc đóng database connection khi worker vẫn cần dùng.

Với Java 17, `ExecutorService.shutdown()` **bắt đầu orderly shutdown**: ngừng nhận công việc mới, nhưng **không chặn caller** để chờ các task đã được nhận. Muốn chờ cần gọi riêng `awaitTermination(timeout, unit)`. Còn `shutdownNow()` cố gửi interrupt và trả danh sách công việc chưa bắt đầu, nhưng cũng **không đảm bảo worker đã dừng ngay**.

Spring `ThreadPoolTaskExecutor` thêm lifecycle quản lý bởi `ApplicationContext` và các tùy chọn như `setWaitForTasksToCompleteOnShutdown(true)`, `setAwaitTerminationSeconds(...)` và `setStrictEarlyShutdown(true)`. Chúng giải quyết những câu hỏi **khác nhau**, có tương tác theo phiên bản; không nên bật tất cả rồi mặc định hiểu rằng hệ thống vừa ngừng nhận sớm vừa drain toàn bộ queue.

Bài Knowledge thuộc nhóm **Vòng đời và dừng** của module **Spring Framework - concurrency**, tập trung vào ranh giới admission/drain, Java 17 runnable demo, shutdown có hai pha, cấu hình Spring, ứng dụng với HTTP/message/scheduler, deadline, monitoring và kiểm thử.

## 1. Ba mốc khác nhau của một executor

Hình dung lifecycle của một pool:

~~~text
APPLICATION RUNNING
  |
  | accepts new task
  |  ├── worker #1: RUNNING
  |  ├── worker #2: RUNNING
  |  └── task queue: WAITING
  |
  | request shutdown()
  v
EXECUTOR SHUTDOWN / DRAINING
  |
  | new task  ---> REJECT
  | running tasks keep processing
  | queued tasks may still be picked up
  |
  | last accepted task finishes
  v
EXECUTOR TERMINATED
  |
  | no active task and no queued task remain
  v
RESOURCES CAN BE RECLAIMED
~~~

Quan trọng: thời điểm nhận lời gọi `shutdown()` **không bằng** thời điểm `isTerminated()` trở thành `true`. Kể cả khi pool đã terminated, một worker có thể đã gửi HTTP request đến hệ thống khác rồi return; trạng thái pool **không tự xác nhận** remote side effect đã hoàn tất.

### 1.1. Câu hỏi, trạng thái và ý nghĩa

| Câu hỏi | API Java thường dùng | Trả lời được điều gì? |
| --- | --- | --- |
| Pool đã có yêu cầu ngừng nhận việc? | `isShutdown()` | Đã bắt đầu shutdown; chưa nói task cũ hoàn tất |
| Pool còn task đang chạy/chờ? | `isTerminated()` | Sau shutdown, true khi toàn bộ task mà executor quản lý chấm dứt |
| Muốn caller chờ pool dừng tối đa 30 giây? | `awaitTermination(30, SECONDS)` | True khi terminated, false khi hết timeout |
| Muốn yêu cầu dừng nhanh hơn? | `shutdownNow()` | Cố interrupt task đang chạy và lấy task chưa chạy khỏi queue |
| Muốn biết một job cụ thể thành công? | `Future.get()`, status/ACK của job | Kết quả của đúng công việc do Future/status đại diện |
| Muốn chắc remote workflow hoàn tất? | Job store, remote ACK, event hoặc trạng thái nghiệp vụ | Không thể suy ra chỉ từ pool shutdown |

Không lấy `getActiveCount()==0` của `ThreadPoolTaskExecutor` làm bằng chứng hoàn tất tuyệt đối: có thể còn task trong queue. Không lấy `isDone()` của **một Future** để suy ra toàn bộ pool đã drain.

## 2. ExecutorService.shutdown(): dừng nhận, không chờ

Java SE 17 quy định `ExecutorService.shutdown()` khởi tạo shutdown có trật tự:

- Executor không chấp nhận task gửi **sau** thời điểm chuyển sang shutdown.
- Những task đã được nhận trước đó **được phép tiếp tục**; task đang chạy và queue đã nhận không tự bị loại bỏ chỉ vì `shutdown()` được gọi.
- Method `shutdown()` **trả về ngay** mà không chờ các task cũ kết thúc.
- Gọi lặp lại `shutdown()` không có tác dụng chờ thêm hay khởi động pool lại.

Ví dụ minh họa:

~~~java
executor.execute(() -> doWork());
executor.shutdown();
System.out.println("Shutdown requested, not necessarily completed");
~~~

Đây là snippet Java, giả định đã có `ExecutorService executor` và `doWork()`. Dòng `println` có thể chạy khi `doWork()` còn hoạt động.

### 2.1. Task đã nhận có chắc thành công?

Không. `shutdown()` không bảo đảm outcome nghiệp vụ:

- Task có thể ném exception, thất bại hoặc tự cancel.
- Worker có thể block vô hạn ở network/database/lock.
- Một job có thể thiếu timeout I/O hoặc không phản ứng với interruption.
- Process có thể bị SIGKILL/crash trước khi drain xong.
- Task có thể chỉ enqueue công việc sang pool khác rồi return.
- Queue có thể rất dài, không kịp drain trong ngân sách shutdown.

Orderly shutdown chỉ mô tả **chính sách về admission và xử lý những task đã được nhận**, không phải một transaction all-or-nothing.

### 2.2. Rejection sau shutdown

Với JDK `ThreadPoolExecutor` dùng `AbortPolicy` mặc định, một lời gọi `execute(...)` hoặc `submit(...)` sau shutdown sẽ bị từ chối bằng `RejectedExecutionException`. Với Spring `ThreadPoolTaskExecutor`, người dùng thường gặp `TaskRejectedException` theo hợp đồng Spring `TaskExecutor`.

Nếu thay rejection policy, kết quả khác. Ví dụ `CallerRunsPolicy` chỉ chạy trong caller khi pool chưa shutdown; sau shutdown nó có thể **bỏ task mà không ném lỗi**. `DiscardPolicy` cũng có thể bỏ task. Do đó **không thấy exception** không chứng minh một task quan trọng đã được chấp nhận. Trong workflow cần độ bền, hãy có admission ACK rõ ràng và durable queue/job store.

## 3. awaitTermination(): bước đợi tách biệt

Chữ ký API:

~~~java
boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;
~~~

Đây là chữ ký rút gọn của interface. Method chặn **thread đang gọi**, đợi một trong ba tình huống:

1. Pool đã terminated → trả `true`.
2. Đã hết thời hạn → trả `false`.
3. Thread chờ bị interrupt → ném `InterruptedException`.

Sử dụng theo đúng thứ tự:

~~~java
pool.shutdown(); // Ngừng nhận tác vụ mới.
boolean done = pool.awaitTermination(30, TimeUnit.SECONDS);
~~~

Đây là snippet minh họa, nằm trong method có xử lý checked exception hoặc `throws InterruptedException`. `awaitTermination` **không tự gọi shutdown**; nếu pool vẫn có khả năng nhận task mới thì chỉ chờ có timeout thường không thể khiến nó terminated.

### 3.1. Timeout chờ không phải force-stop

`awaitTermination(30, SECONDS)==false` chỉ có nghĩa **pool chưa terminated khi hết 30 giây**. Nó **không** tự ngắt worker, xóa queue hay rollback dữ liệu. Nếu có chính sách khẩn cấp, caller phải chủ động gọi `shutdownNow()` và đợi tiếp; nếu không, task có thể vẫn hoàn tất sau thời hạn 30 giây.

### 3.2. isShutdown và isTerminated

| Trạng thái | isShutdown | isTerminated |
| --- | --- | --- |
| Pool đang chạy bình thường, kể cả idle | false | false |
| Đã shutdown nhưng worker chưa xong | true | false |
| Đã shutdown, còn task đã nhận trong queue | true | false |
| Đã shutdown và mọi task do pool quản lý chấm dứt | true | true |

`isTerminated()` không trở thành true khi pool chưa được shutdown, kể cả nếu không có task nào. “Idle” không phải “terminated”.

## 4. shutdownNow(): cố ngắt và rút queue, không bảo đảm dừng tức thì

`ExecutorService.shutdownNow()` khác `shutdown()`:

- Ngừng nhận task mới.
- Cố ngắt task đang chạy, thông thường bằng `Thread.interrupt()` trong JDK thread pool.
- Ngừng khởi động các task chưa bắt đầu, trả danh sách `List<Runnable>` của những task còn chờ.
- **Không chờ** task đang chạy đã kết thúc. Muốn xác minh pool terminated vẫn cần `awaitTermination` hoặc `isTerminated`.

~~~java
List<Runnable> neverStarted = pool.shutdownNow();
boolean stopped = pool.awaitTermination(10, TimeUnit.SECONDS);
~~~

Đây là snippet Java cần một `ExecutorService pool` và xử lý `InterruptedException`.

### 4.1. Interrupt không phải phép giết thread

`Thread.interrupt()` là tín hiệu hợp tác. Một task đang ở `Thread.sleep` hoặc `CountDownLatch.await` có thể nhận `InterruptedException`; task vòng lặp CPU không kiểm tra interrupt có thể tiếp tục. Một số thư viện I/O cần timeout/cancellation API riêng. Side effect đã commit ở database hoặc gửi sang remote **không tự rollback**.

Mẫu hợp tác trong một method `void`:

~~~java
try {
    blockingOperation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Đoạn này là pseudocode: `blockingOperation()` phải là thao tác có khai báo `throws InterruptedException`. Nếu method có thể `throws InterruptedException`, ném tiếp cũng hợp lệ. Không viết `catch (InterruptedException ignored) {}` rồi chạy tiếp vô hạn.

### 4.2. Danh sách từ shutdownNow không phải job store

Khi dùng `submit(Callable)`, phần tử trong queue có thể là wrapper `FutureTask`. Danh sách `Runnable` được trả bởi `shutdownNow()` không nhất thiết cho biết payload nghiệp vụ gốc hoặc tự hủy Future mà caller đang giữ. Một Future của task chưa chạy có thể vẫn pending trừ khi có cơ chế cleanup/cancel tương ứng. Muốn restart và retry an toàn, lưu `jobId`, trạng thái, idempotency key ngoài executor.

Không replay mù danh sách này mà không kiểm tra side effect; một task chạy dở không nằm trong danh sách “never started”, và task đã gửi việc tới remote có thể đã hoàn tất một phần.

## 5. Java 17 độc lập: thấy rõ hai giai đoạn

Tạo tệp **`ShutdownVsWaitDemo.java`**. Chương trình dùng một executor chỉ một thread, task đầu đợi `CountDownLatch` và task thứ hai chờ queue. Do đó có thể quan sát đúng trạng thái giữa `shutdown()` và `awaitTermination()` mà không phỏng đoán lịch CPU.

Sau đó chương trình tạo pool thứ hai để so sánh `shutdownNow()` với một worker đang chờ thao tác interruptible.

~~~java
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShutdownVsWaitDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService graceful = Executors.newSingleThreadExecutor();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstCompleted = new AtomicBoolean(false);
        AtomicBoolean queuedCompleted = new AtomicBoolean(false);
        try {
            graceful.execute(() -> {
                running.countDown();
                try {
                    release.await();
                    firstCompleted.set(true);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            if (!running.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("first worker never started");
            }
            graceful.execute(() -> queuedCompleted.set(true));
            graceful.shutdown();
            System.out.println("1. isShutdown immediately: " + graceful.isShutdown());
            System.out.println("2. isTerminated while blocked: " + graceful.isTerminated());
            try {
                graceful.execute(() -> {});
                throw new IllegalStateException("Unexpected admission");
            } catch (RejectedExecutionException expected) {
                System.out.println("3. New task rejected: true");
            }
            boolean early = graceful.awaitTermination(100, TimeUnit.MILLISECONDS);
            System.out.println("4. awaitTermination before release: " + early);
            System.out.println("5. Queued task ran before release: " + queuedCompleted.get());
            release.countDown();
            boolean terminated = graceful.awaitTermination(5, TimeUnit.SECONDS);
            System.out.println("6. awaitTermination after release: " + terminated);
            System.out.println("7. Original and queued completed: "
                    + (firstCompleted.get() && queuedCompleted.get()));
        } finally {
            release.countDown();
            graceful.shutdownNow();
        }

        ExecutorService urgent = Executors.newSingleThreadExecutor();
        CountDownLatch urgentStarted = new CountDownLatch(1);
        CountDownLatch urgentRelease = new CountDownLatch(1);
        CountDownLatch urgentExited = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);
        AtomicBoolean queuedRan = new AtomicBoolean(false);
        try {
            urgent.execute(() -> {
                urgentStarted.countDown();
                try {
                    urgentRelease.await();
                } catch (InterruptedException ex) {
                    sawInterrupt.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    urgentExited.countDown();
                }
            });
            if (!urgentStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("urgent worker never started");
            }
            urgent.execute(() -> queuedRan.set(true));
            List<Runnable> neverStarted = urgent.shutdownNow();
            System.out.println("8. shutdownNow returned queued tasks: " + neverStarted.size());
            if (!urgentExited.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("urgent worker did not exit");
            }
            System.out.println("9. Running task observed interrupt: " + sawInterrupt.get());
            System.out.println("10. Queued task ran: " + queuedRan.get());
            System.out.println("11. urgent pool terminated: "
                    + urgent.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            urgentRelease.countDown();
            urgent.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac --release 17 ShutdownVsWaitDemo.java
java ShutdownVsWaitDemo
~~~

Stdout đã kiểm chứng bằng thực thi:

~~~text
1. isShutdown immediately: true
2. isTerminated while blocked: false
3. New task rejected: true
4. awaitTermination before release: false
5. Queued task ran before release: false
6. awaitTermination after release: true
7. Original and queued completed: true
8. shutdownNow returned queued tasks: 1
9. Running task observed interrupt: true
10. Queued task ran: false
11. urgent pool terminated: true
~~~

**Giải thích:**

1. `running.await(...)` xác nhận task đầu đã bắt đầu. Pool có một worker nên task thứ hai chỉ có thể ở queue.
2. `shutdown()` đặt `isShutdown=true` và từ chối task mới; worker đầu vẫn bị giữ ở latch nên `isTerminated=false`.
3. `awaitTermination(100ms)` trả false vì task đầu chưa được thả. `queuedCompleted=false` vì queue chưa thể chạy.
4. Main mở latch. Worker xử lý xong task đầu, rồi task queue; lúc đó `awaitTermination(5s)` trả true và cả hai flag completed bằng true.
5. Pool khẩn cấp được dựng với trạng thái tương tự; `shutdownNow()` rút một task chưa chạy và cố interrupt task đang chờ.
6. Worker khẩn cấp đang ở `CountDownLatch.await()` nên quan sát `InterruptedException`. Đợi `urgentExited` rồi `awaitTermination` xác nhận thực sự kết thúc.

**Đây là thí nghiệm có kiểm soát.** Không suy ra mọi worker của production đều kết thúc khi bị interrupt: task có thể bỏ qua cờ, đang ở I/O không hỗ trợ interruption, hoặc đã gửi side effect ra ngoài.

## 6. Recipe graceful trước, force best-effort sau

Một mẫu điều phối shutdown tiêu chuẩn chia thành hai giai đoạn: dừng nhận + chờ một khoảng, rồi best-effort interrupt nếu vượt ngân sách.

Tệp helper **`ExecutorShutdown.java`**:

~~~java
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecutorShutdown {
    private ExecutorShutdown() {}

    public static boolean stop(
            ExecutorService pool, long gracefulSeconds, long forceSeconds) {
        pool.shutdown();
        try {
            if (pool.awaitTermination(gracefulSeconds, TimeUnit.SECONDS)) {
                return true;
            }

            List<Runnable> pending = pool.shutdownNow();
            System.err.println("Unstarted tasks: " + pending.size());
            return pool.awaitTermination(forceSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
~~~

Đây là class Java 17 có thể biên dịch độc lập với tên file khớp class, nhưng không có `main` để chạy riêng. Các tham số thời gian phải được validate theo ứng dụng (không âm, phù hợp deadline shutdown). Caller ở **ngoài pool** có thể dùng:

~~~java
boolean stopped = ExecutorShutdown.stop(pool, 30, 10);
if (!stopped) {
    System.err.println("Executor not terminated within deadline");
}
~~~

Đây là snippet caller, không có output cố định. **Không gọi `awaitTermination()` từ một task của chính pool đang được dừng**, vì task đó đang chờ pool “không còn task đang chạy”, trong đó có chính nó; có thể gây chờ vòng, starvation hoặc timeout.

### 6.1. Timeout của hai pha nói gì?

- **30 giây đầu:** thời gian cho task đã nhận, cả queued và running, hoàn tất bình thường.
- **10 giây sau:** thời gian worker phản ứng với interrupt, dọn tài nguyên và thoát sau `shutdownNow()`.
- **Kết quả `false`:** pool vẫn chưa terminated trong ngân sách. Không được báo “drain success”.

Hai khoảng này là **ví dụ**, phải nhỏ hơn deadline của orchestrator/container để còn thời gian đóng dependency và flush trạng thái. Không có lời hứa mọi task hoàn tất trong 40 giây; cần persistent retry với job quan trọng.

## 7. Spring ThreadPoolTaskExecutor: hai cấu hình không phải một

`ThreadPoolTaskExecutor` là Spring-managed `TaskExecutor` dựa trên `ThreadPoolExecutor`. Nó tham gia vòng đời của `ApplicationContext`. Các tùy chọn quan trọng:

| Tùy chọn | Tác động | Điều không nên suy ra |
| --- | --- | --- |
| `setWaitForTasksToCompleteOnShutdown(true)` | Khi full shutdown, ưu tiên để tác vụ đang chạy và trong queue hoàn tất, không interrupt và xóa queue chỉ để shutdown sớm | Không mặc định từ chối ngay tại ContextClosedEvent |
| `setAwaitTerminationSeconds(n)` | Đợi tối đa n giây ở bước shutdown của executor trước khi phần destruction tiếp tục | Không tự bật graceful hoặc tự force stop khi hết n giây |
| `setStrictEarlyShutdown(true)` | Gửi tín hiệu shutdown sớm ở context close, từ chối submissions muộn | Không bảo đảm tương thích cộng dồn với wait-for-tasks |
| `setAcceptTasksAfterContextClose(true)` | Cho phép submissions muộn khi context bắt đầu đóng, tránh coordinated lifecycle stop thông thường | Không bảo đảm mọi job đều xử lý xong |
| `ContextClosedEvent` | Thời điểm để application dừng producer/admission | Không có nghĩa mọi bean đã bị destroy |
| `SmartLifecycle.stop(Runnable)` | Hook lifecycle có callback theo hợp đồng implementation | Không phải lúc nào cũng tương đương `ExecutorService.shutdown()` |

Theo Spring Framework 6.2 Javadoc, **`setWaitForTasksToCompleteOnShutdown(true)` có thể override hiệu ứng early shutdown của `setStrictEarlyShutdown(true)`**, vì nó chọn đường late shutdown/destruction thay cho coordinated lifecycle stop. Không bật tất cả flags và mặc định kỳ vọng mọi tính chất cùng xảy ra.

### 7.1. Spring config ưu tiên hoàn thành task đã nhận

Tệp **`JobExecutorConfiguration.java`**:

~~~java
package example.lifecycle;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class JobExecutorConfiguration {
    @Bean("jobExecutor")
    public ThreadPoolTaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("job-");

        // Finish running and queued tasks at destruction.
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Limit blocking at executor destruction to 30 seconds.
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
~~~

Đây là **mã Spring tích hợp**, cần Spring Framework 6.x và ApplicationContext, không biên dịch chỉ với JDK. Những con số trong config là minh họa, không phải tuning cho mọi workload.

**Giải thích chính xác:**

- `setWaitForTasksToCompleteOnShutdown(true)` chọn hướng orderly shutdown ở bước executor destruction: tác vụ đã nhận được để chạy tiếp thay vì bị interrupt/clear queue như nhánh immediate shutdown.
- `setAwaitTerminationSeconds(30)` chặn tối đa 30 giây khi Spring thực hiện bước shutdown của executor để đợi task chấm dứt trước khi resources khác bị destroy tiếp.
- Nếu hết 30 giây, flag chờ không **tự động gọi shutdownNow()**. Các task có thể tiếp tục khi những dependency chúng cần đã bị đóng, hoặc bị container cắt ngang.
- Policy wait-for-tasks ảnh hưởng coordinated lifecycle stop: **nó không tự bảo đảm từ chối mọi submission ngay khi ContextClosedEvent xuất hiện**. Cần chủ động dừng ingress/producer.

### 7.2. Nếu ưu tiên từ chối sớm khi context đóng

Spring 6.1.4+ cung cấp `ThreadPoolTaskExecutor.setStrictEarlyShutdown(true)`: khi context đóng, phát tín hiệu shutdown sớm, từ chối việc submit muộn, đồng thời tham gia coordinated stop theo hợp đồng. Không nên dùng cùng `setWaitForTasksToCompleteOnShutdown(true)` rồi suy rằng cờ strict vẫn chi phối, vì Spring ghi rõ các flag có thể override nhau.

Có hai chiến lược:

1. **Early rejection qua executor lifecycle:** dùng `setStrictEarlyShutdown(true)`, sau đó hiểu rõ xử lý task hiện tại trong coordinated stop/full shutdown của phiên bản Spring.
2. **Drain queue graceful tại destruction:** dùng `setWaitForTasksToCompleteOnShutdown(true)` cùng ngân sách `awaitTerminationSeconds(...)`, đồng thời đóng admission/producers **ở cấp ứng dụng sớm hơn**.

Với hệ thống cần không mất job qua crash, cả hai cách đều cần bền vững hóa công việc ngoài bộ nhớ.

## 8. Admission gate ở cấp ứng dụng để dừng nhận trước khi drain

Nếu chọn nhánh graceful wait-for-tasks, có thể dùng một gate hạn chế submit mới khi ứng dụng nhận `ContextClosedEvent`.

Tệp **`JobSubmissionGate.java`**:

~~~java
package example.lifecycle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class JobSubmissionGate {
    private final ThreadPoolTaskExecutor executor;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public JobSubmissionGate(
            @Qualifier("jobExecutor") ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    @EventListener(ContextClosedEvent.class)
    public void stopAccepting() {
        accepting.set(false);
    }

    public CompletableFuture<Void> submit(Runnable task) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (!accepting.get()) {
            result.completeExceptionally(
                    new RejectedExecutionException("Application closing"));
            return result;
        }

        try {
            executor.execute(() -> {
                try {
                    task.run();
                    result.complete(null);
                } catch (RuntimeException | Error error) {
                    result.completeExceptionally(error);
                    throw error;
                }
            });
        } catch (TaskRejectedException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }
}
~~~

Đây là **Spring integration sample**, không chạy độc lập với `javac` thiếu dependencies. Nó có các giới hạn quan trọng:

- Mọi producer cần chịu gate phải gọi `JobSubmissionGate.submit(...)`, không submit trực tiếp vào `jobExecutor` hoặc dùng `@Async("jobExecutor")` bypass gate.
- Có một **race** giữa `accepting.get()` và `executor.execute(...)` nếu context đóng đồng thời; vì vậy code còn bắt `TaskRejectedException`.
- Future này đại diện kết quả của **Runnable được gate quản lý**, không tự đợi các task con mà Runnable gửi sang hệ thống khác.
- Gate từ chối việc mới, còn cấu hình bean ở mục 7.1 cho phép drain những task đã được nhận.
- Với job quan trọng, rejected submission cần trạng thái, retry/job store thay vì trả HTTP 202 và giả vờ task đã được nhận.

Đây là ví dụ về **tách hai trách nhiệm**, không phải giải pháp distributed queue hay đảm bảo exactly-once.

## 9. Dừng producer trước khi dừng executor

Các nguồn tác vụ thông thường bao gồm HTTP request, message consumer, scheduled callback và parent task sinh child task:

~~~text
HTTP ingress ----------+
Message listener ------+--> app admission --> executor queue --> worker
@Scheduled ------------+
Parent callback -------+
                         |
                   shutdown plan
                   1. close/pause producers
                   2. stop admitting new tasks
                   3. drain accepted work
                   4. close dependencies
~~~

Nếu executor đã shutdown nhưng producer vẫn tiếp tục enqueue:

- HTTP controller có thể gặp `TaskRejectedException` và trả lỗi.
- Kafka/JMS listener có thể đã nhận message nhưng không submit được worker; cần chính sách nack/retry/ack phù hợp.
- `@Scheduled` method có thể cố tạo job mới trong khi pool đang đóng.
- Task cha đã queue có thể cần submit child task xuống pool khác, nhưng child pool đã shutdown.
- Callback đang cleanup trong `@PreDestroy` có thể submit late task sau context close.

Vì vậy thứ tự shutdown cần phù hợp **đồ thị dependency**. Một quy trình thường là dừng ingress và producer, cho request đang xử lý hoàn tất, drain executor, rồi mới đóng DB pool, network client, producer và các tài nguyên worker cần. Không có một thứ tự cố định đúng cho mọi ứng dụng; cần kiểm thử với bean lifecycle thực tế.

### 9.1. Không đợi termination từ task của chính pool

Đây là anti-pattern:

~~~java
executor.execute(() -> {
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
});
~~~

Đây là **pseudocode sai**, chưa xử lý `InterruptedException`. Task đang gọi await là một task **đang chạy trong chính pool phải chờ kết thúc**; có thể tạo tự-chờ, starvation/deadlock hoặc luôn timeout. Nên điều phối shutdown từ thread/lifecycle coordinator bên ngoài pool.

## 10. Ngân sách thời gian và resource lifecycle

Shutdown trong container có ngân sách hữu hạn. Ví dụ ngân sách tổng 45 giây:

| Pha | Thời gian minh họa |
| --- | --- |
| Stop ingress, pause consumers, ngừng scheduled producers | 5 giây |
| Graceful drain của tasks đã nhận | 25 giây |
| Interrupt best-effort và cleanup | 10 giây |
| Đóng dependency, flush trạng thái/log | 5 giây |

**Đây là ví dụ phân bổ**, không phải cấu hình mặc định Spring hay Kubernetes. Điều cần tính:

- Số task trong queue và số worker thực tế.
- Thời gian xử lý trung bình, tail latency và độ trễ khi lock/DB/API.
- Timeout của I/O và retry/backoff ở mỗi job.
- Task cha có tạo thêm task con hay không.
- Những dependency còn sống cho tới khi worker kết thúc.
- Deadline bên ngoài, ví dụ termination grace period của orchestrator.
- Sau deadline có thể xảy ra process kill không hợp tác, nên cần recovery.

Nếu queue có 100 job, mỗi job mất 2 giây trên 2 worker, thời gian xử lý lý tưởng tối thiểu khoảng 100 giây trước overhead. Đặt `setAwaitTerminationSeconds(30)` **không làm 100 giây công việc biến thành 30 giây**. Khi workload vượt deadline, cần giảm backlog trước deploy, thay đổi kiến trúc hoặc dùng persistent job store để chạy tiếp sau restart.

### 10.1. Graceful shutdown không đảm bảo chống crash

Nếu JVM crash hoặc bị kill cứng, các task trong memory có thể mất trước khi handler shutdown chạy xong. Với nhiệm vụ quan trọng, dùng:

- Durable queue/job store với các trạng thái PENDING/RUNNING/SUCCEEDED/FAILED.
- Ack message ở thời điểm đáp ứng điều kiện thành công phù hợp.
- Retry/lease để lấy lại job khi worker chết giữa chừng.
- Idempotency key để replay không tạo side effect trùng.
- Trạng thái workflow/compensation cho hệ thống nhiều dịch vụ.

Không gọi một pool in-memory có `shutdown()` và `awaitTermination()` là hệ thống bảo đảm exactly-once.

## 11. Monitoring: đã yêu cầu đóng hay đã thật sự đóng?

Các event/metric gợi ý:

| Sự kiện | Ý nghĩa |
| --- | --- |
| `shutdown_requested_at` | Thời điểm nhận yêu cầu đóng |
| `admission_closed_at` | Thời điểm ngừng tiếp nhận ở cấp API/producer |
| `executor_shutdown_at` | Thời điểm pool được yêu cầu ngừng nhận task |
| `task_rejected_total` | Task bị từ chối khi đang shutdown hoặc quá tải |
| `active_tasks` | Snapshot số task đang chạy |
| `queue_depth` | Snapshot số task chờ |
| `task_succeeded_total` / `task_failed_total` | Phân biệt kết quả nghiệp vụ |
| `drain_completed_at` | Khi pool đã terminated |
| `await_termination_timeout_total` | Số lần hết hạn chờ mà pool chưa dừng |
| `forced_interrupt_requested_total` | Số lần chọn `shutdownNow()` |
| `unfinished_job_ids` | Những job cần tiếp tục/retry sau restart |

Tên metrics này là **gợi ý**, không phải tên có sẵn mặc định trong Spring. Dùng correlation id trong logs, không đưa tất cả job id vào metric tag có cardinality cao.

Không log “Shutdown completed” tại dòng ngay sau `pool.shutdown()`. Dòng đó chỉ xác nhận **đã bắt đầu shutdown**. Muốn báo “executor terminated”, phải có `awaitTermination()==true` hoặc trạng thái tương đương. Muốn báo “business succeeded”, cần bằng chứng từ từng job.

### 11.1. Nhận biết late submissions

Nếu một instance liên tục log rejection khi context đóng, cần tìm producer còn chạy: `@Scheduled` callback, message listener, API đã nhận request trước đó, hoặc parent job đang sinh child. Không cố khắc phục chỉ bằng cách tăng queue/thread count, vì shutdown rejection không phải một tình trạng thiếu capacity đơn thuần.

Một hệ thống tốt phân loại:

1. **Rejected because shutting down:** producer đến muộn, cần chuyển sang instance khác/retry bền vững.
2. **Rejected because overloaded:** backpressure, rate limiting hoặc capacity planning.
3. **Task accepted but failed:** lỗi worker/DB/API cần retry có kiểm soát.
4. **Task accepted but not drained before kill:** recovery từ durable source.

## 12. Kiểm thử lifecycle với latch, không đoán bằng sleep

Bộ test nên bao gồm:

1. **shutdown không chờ:** task đầu giữ latch, task hai queue, gọi `shutdown()`; `isShutdown()==true` nhưng `isTerminated()==false`.
2. **rejection đúng lúc:** sau shutdown, `execute(...)` bị từ chối theo policy đã chọn. Với policy discard, test phải phát hiện mất task.
3. **awaitTermination timeout:** khi worker còn bị giữ, lời chờ ngắn trả false.
4. **graceful drain:** mở latch, cả task đang chạy và queued task hoàn tất, await trả true.
5. **shutdownNow:** task đang chặn trên API interruptible nhận interrupt; task chưa chạy được trả khỏi queue.
6. **task không hợp tác:** test có đường cleanup hữu hạn; kiểm tra worker không dừng tức thì dù đã interrupt, không tạo vòng lặp vô tận làm treo suite.
7. **Spring context close:** test đúng bean lifecycle với cấu hình wait-for-tasks và await-termination, phân biệt policy early/late submission.
8. **admission gate race:** một thread đóng context trong lúc thread khác submit; kết quả chỉ có thể là accepted hoặc rejected có kiểm soát, không thất lạc trạng thái.
9. **dependency order:** worker dùng DB trong lúc close context, kiểm tra DB resources vẫn được giữ theo thiết kế cho tới khi job xong hoặc deadline.
10. **child executor:** pool cha terminated không đồng nghĩa pool con đã hoàn tất.
11. **restart:** job chưa hoàn tất được lấy lại từ durable store và xử lý idempotent.

Dùng `CountDownLatch` để đảm bảo task thật sự bắt đầu trước khi shutdown. Không dùng `Thread.sleep(100)` để phỏng đoán vì test sẽ chập chờn trên CI. Đặt timeout cho mọi await và cleanup executor trong `finally`.

## 13. Các sai lầm thường gặp

**“`shutdown()` đợi mọi task cũ xong.”** Sai. Nó chỉ bắt đầu orderly shutdown, không chặn caller.

**“`isShutdown()==true` tức là pool đã terminated.”** Sai. Các task cũ có thể còn chạy hoặc queue chưa hết.

**“`shutdown()` luôn bỏ các task đang xếp hàng.”** Sai trong orderly shutdown thông thường. Queue đã nhận được tiếp tục xử lý.

**“`awaitTermination(30s)` false thì Java tự gọi shutdownNow().”** Sai. Đây chỉ là timeout của caller.

**“`shutdownNow()` chắc chắn giết mọi worker ngay.”** Sai. Nó cố interrupt, không thể cưỡng ép mọi I/O/CPU loop dừng.

**“Danh sách từ shutdownNow() là những job đã rollback.”** Sai. Nó thường là task chưa bắt đầu, có thể chứa FutureTask wrapper.

**“CallerRunsPolicy sau shutdown vẫn chạy task trong caller.”** Không mặc định; policy này có thể discard task nếu executor đã shutdown.

**“Spring waitForTasks... và awaitTerminationSeconds là cùng một setting.”** Sai: một bên chọn hành vi xử lý queue/worker lúc shutdown, một bên chọn thời gian chờ.

**“Bật strictEarlyShutdown và waitForTasksToCompleteOnShutdown cùng lúc cho đủ cả hai.”** Không nên giả định; Spring Javadoc ghi rõ flag wait-for-tasks có thể override early-shutdown policy.

**“Đóng HTTP ingress là hết producer.”** Sai. Message consumer, scheduler và callback cha vẫn có thể submit.

**“pool terminated đồng nghĩa remote event đã xử lý xong.”** Sai. Pool chỉ theo dõi những task mà nó quản lý, không tự biết trạng thái remote.

**“Graceful shutdown đủ để không mất job qua SIGKILL.”** Sai. Cần durable queue/job store, retry và idempotency.

## 14. Câu hỏi kiểm tra và đáp án

**Câu 1.** Pool có một task chạy và năm task chờ. Gọi `shutdown()`, lời gọi trả ngay. Cả sáu task đã hoàn tất chưa?

**Đáp án:** chưa chắc. Shutdown chỉ dừng nhận việc mới; task đã nhận vẫn có thể đang chờ hoặc xử lý.

**Câu 2.** `isShutdown()==true` và `isTerminated()==false` có bất thường không?

**Đáp án:** không. Đó là trạng thái bình thường khi pool đang drain.

**Câu 3.** `awaitTermination(5, SECONDS)` trả false có khiến task bị interrupt?

**Đáp án:** không. Chỉ hết thời gian chờ; interrupt phải do thao tác khác yêu cầu.

**Câu 4.** `shutdownNow()` trả 3 Runnable. Có thể kết luận 3 job đã bị rollback không?

**Đáp án:** không. Chúng là task chưa bắt đầu theo executor; rollback và trạng thái nghiệp vụ là chuyện khác.

**Câu 5.** Vì sao không gọi `awaitTermination()` từ worker của chính pool?

**Đáp án:** worker sẽ chờ pool không còn task chạy, nhưng chính nó đang chạy; dễ tạo tự-chờ/deadlock.

**Câu 6.** Spring `setWaitForTasksToCompleteOnShutdown(true)` và `setAwaitTerminationSeconds(30)` khác nhau thế nào?

**Đáp án:** setting đầu chọn hướng graceful cho running/queued task; setting sau giới hạn thời gian chờ ở shutdown của bean, không tự đổi policy hoặc force-stop khi timeout.

**Câu 7.** Muốn ngừng nhận job từ sớm nhưng vẫn drain queue với Spring phải nghĩ tới gì?

**Đáp án:** dừng producer/admission khi context đóng, giữ graceful policy và deadline chờ; không dựa vào kết hợp các flag có thể override nhau.

**Câu 8.** Nếu worker đang dùng DB connection mà datasource đã bị destroy thì rủi ro gì?

**Đáp án:** task có thể fail I/O/commit dù pool đang graceful; cần thứ tự shutdown và budget phù hợp.

**Câu 9.** isTerminated của pool cha chứng minh job con được gửi tới một pool khác đã xong chưa?

**Đáp án:** không. Phải có completion signal/status từ child.

**Câu 10.** Làm gì để tránh mất job khi app bị kill giữa chừng?

**Đáp án:** durable queue/job store, ack đúng lúc, retry/lease/idempotency và khôi phục theo job id.

## 15. Tài liệu tham khảo

- Java SE 17 — `ExecutorService`, `shutdown`, `shutdownNow`, `awaitTermination`, `isShutdown` và `isTerminated`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Java SE 17 — `ThreadPoolExecutor` và rejection policy: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Java SE 17 — `ThreadPoolExecutor.CallerRunsPolicy`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.CallerRunsPolicy.html
- Spring Framework 6.2 Javadoc — `ExecutorConfigurationSupport`, await termination và wait-for-tasks: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html
- Spring Framework 6.2 Javadoc — `ThreadPoolTaskExecutor` và strict early shutdown: https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring Framework Reference — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework API — `SmartLifecycle`: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/context/SmartLifecycle.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-shutdown-003">SmartLifecycle và phối hợp thứ tự dừng các thành phần</a>

<details>
<summary>Xem chi tiết</summary>

# SmartLifecycle và phối hợp thứ tự dừng các thành phần

## 1. Vấn đề: dừng ứng dụng không đơn giản là đóng từng bean

Ứng dụng Spring có thể đồng thời vận hành HTTP server, consumer Kafka/RabbitMQ, scheduler, executor, worker xử lý công việc, connection pool, cache, client HTTP và các thành phần giữ trạng thái nội bộ. Khi triển khai phiên bản mới, nhận SIGTERM hoặc gọi `ApplicationContext.close()`, việc dừng sai thứ tự có thể gây ra những tình huống khó phát hiện:

- Consumer nhận message mới sau khi worker pool đã từ chối task.
- `@Scheduled` tiếp tục phát job trong lúc database client đang bị đóng.
- HTTP endpoint còn nhận request nhưng service không còn tài nguyên để xử lý.
- Worker đang ghi transaction thì một component phụ thuộc đã shutdown.
- Một bean gọi `stop()` rồi trở về ngay dù công việc bất đồng bộ của nó vẫn chưa dừng.
- Spring đợi hết timeout vì `stop(Runnable)` không bao giờ gọi callback.
- Container bị kill trước khi giai đoạn drain hoàn tất; có công việc chưa được ghi nhận bền vững.

**`SmartLifecycle`** là hợp đồng Spring giúp điều phối **khởi động và dừng theo phase** và cho phép **dừng bất đồng bộ có callback xác nhận hoàn tất**. Nó đặc biệt hữu ích trong module **Vòng đời và dừng** của **Spring Framework – concurrency**.

Sau bài này, bạn cần:

1. Phân biệt `Lifecycle`, `Phased`, `SmartLifecycle`, `LifecycleProcessor` và `DefaultLifecycleProcessor`.
2. Giải thích quy tắc **khởi động theo phase tăng dần, dừng theo phase giảm dần**.
3. Hiểu giá trị mặc định của `SmartLifecycle.getPhase()` và tương tác với `Lifecycle` thông thường.
4. Dùng `getPhase()` để tắt đầu vào trước, chặn phát job mới, đợi worker hoàn thành rồi mới giải phóng resource phụ thuộc.
5. Phân biệt `stop()` và `stop(Runnable callback)`; biết chính xác khi nào callback được gọi.
6. Hiểu timeout per shutdown phase, startup/shutdown dependencies và `depends-on`.
7. Viết/chạy hai ví dụ Spring Framework Java 17+ xác minh thứ tự start/stop và callback drain bằng `CountDownLatch`.
8. Cấu hình graceful shutdown trong Spring Boot và phối hợp với Kubernetes termination grace period.
9. Phân biệt “bean đã dừng” với “mọi công việc quan trọng đã được commit/ack”.
10. Kiểm thử race condition, rejection, timeout, restart và khả năng dừng thật của thành phần.

**Mục tiêu thiết kế:** không chỉ khiến bean được gọi `stop`, mà phải giữ đủ tài nguyên để các tác vụ đã nhận có thể hoàn tất theo chính sách và không tiếp nhận công việc mới sau khi hệ thống chuyển trạng thái drain.

## 2. Định nghĩa các interface liên quan

### 2.1. Lifecycle: bắt đầu, dừng, hỏi trạng thái

Spring có interface:

```java
public interface Lifecycle {
    void start();
    void stop();
    boolean isRunning();
}
```

Đây là **chữ ký minh họa**, không phải file Java độc lập. Nó phù hợp với component có quá trình start/stop riêng: message listener, worker loop, connection manager, scheduler hoặc dịch vụ nhận công việc.

`Lifecycle` một mình **không** diễn đạt phase riêng hay một callback dành cho shutdown bất đồng bộ. Container có thể quản lý `Lifecycle` qua `LifecycleProcessor`; ở lúc context refresh, `SmartLifecycle` mới có quy tắc auto-start đặc biệt.

### 2.2. Phased: xác định ưu tiên khởi động và dừng

`Phased` cung cấp:

```java
public interface Phased {
    int getPhase();
}
```

Giá trị `phase` là số nguyên. Trong quy tắc của `DefaultLifecycleProcessor`:

- **Startup:** phase nhỏ chạy **trước** phase lớn.
- **Shutdown:** phase lớn dừng **trước** phase nhỏ.

Ví dụ:

```text
Startup:
   phase -100    phase 0     phase 100    phase 200
   resources --> workers --> scheduler --> ingress

Shutdown:
   phase 200     phase 100    phase 0      phase -100
   ingress  --> scheduler --> workers --> resources
```

Sơ đồ biểu diễn một chính sách hợp lý, không phải danh sách phase chuẩn của Spring. Số phase là của ứng dụng; cần đối chiếu với phase của các bean thực tế (executor, scheduler, web server, message listener) để tránh xung đột.

### 2.3. SmartLifecycle: Lifecycle + Phased + auto-start + async stop callback

`SmartLifecycle` mở rộng cả `Lifecycle` và `Phased`, đồng thời có:

```java
public interface SmartLifecycle extends Lifecycle, Phased {
    default boolean isAutoStartup() { return true; }
    default int getPhase() { return DEFAULT_PHASE; }
    default void stop(Runnable callback) {
        stop();
        callback.run();
    }
}
```

Đây là **mô hình rút gọn để hiểu hợp đồng**, không phải nội dung đầy đủ từ source Spring. Điểm cần nhớ:

- `isAutoStartup()` mặc định `true`: component sẽ được start trong lúc context refresh nếu phù hợp.
- `getPhase()` mặc định `SmartLifecycle.DEFAULT_PHASE`, bằng `Integer.MAX_VALUE`.
- `stop(Runnable)` cung cấp cách cho component **báo đã dừng xong** sau một quá trình bất đồng bộ.
- Một implementation có thể override các method theo nhu cầu.

**Cực kỳ quan trọng:** khi `DefaultLifecycleProcessor` xử lý `SmartLifecycle`, nó sử dụng biến thể **`stop(Runnable)`**. Nếu bạn chỉ viết logic trong `stop()` nhưng lại override `stop(Runnable)` thành một hàm rỗng, bạn đã làm hỏng quy trình shutdown.

### 2.4. LifecycleProcessor và DefaultLifecycleProcessor

`LifecycleProcessor` là hợp đồng container dùng để start/stop lifecycle beans trong bối cảnh refresh, explicit start/stop và close.

`DefaultLifecycleProcessor` là implementation thông dụng, xử lý:

- Phân nhóm bean theo phase.
- Thứ tự startup và shutdown.
- Các dependency `depends-on`.
- Callback hoàn tất từ `SmartLifecycle.stop(Runnable)`.
- Giới hạn thời gian chờ mỗi shutdown phase.
- Một số cơ chế nâng cao tùy phiên bản Spring (như timeout từng phase).

Nó **không** biết rằng một task có gửi email thành công hay một transaction đã commit nếu component không thực hiện và báo trạng thái đó trong hợp đồng stop.

## 3. Quy tắc phase chính xác

### 3.1. Startup đi từ số nhỏ tới số lớn

```text
-200     -100      0       100       200
  |--------|-------|---------|---------|
   low phase             high phase
  START FIRST                         START LAST
```

Một component phase thấp thường là tài nguyên cần được chuẩn bị sớm; phase cao thường là thành phần bắt đầu nhận công việc từ bên ngoài sau khi mọi dependency đã sẵn sàng.

Nếu component B cần A, bạn nên đặt A ở phase thấp hơn B.

### 3.2. Shutdown đảo chiều: số lớn dừng trước

```text
200       100       0       -100      -200
  |---------|-------|---------|---------|
 STOP FIRST                         STOP LAST
```

Đây là một cơ chế quan trọng để bảo đảm ingress/consumer/scheduler dừng phát công việc mới **trước khi** worker và resource mà nó phụ thuộc được dừng.

### 3.3. Cùng phase không có thứ tự ổn định được bảo đảm

Hai component cùng `getPhase() == 100` được xử lý trong cùng nhóm phase. Bạn **không được dựa** vào thứ tự được khai báo trong `@Configuration`, tên bean hay constructor order để bảo đảm chúng dừng tuần tự theo mong muốn.

Nếu B **phải** dừng trước A, hãy dùng phase khác nhau hoặc dependency `depends-on` rõ ràng.

### 3.4. @DependsOn có quyền ưu tiên hơn phase

Spring Javadoc quy định explicit `depends-on` có thể **ghi đè thứ tự phase**. Nếu B phụ thuộc A:

- Khi start: **A trước B**.
- Khi stop: **B trước A**.

Ngay cả khi phase được gán mâu thuẫn, quan hệ dependency được ưu tiên. Do đó đừng sử dụng số phase như một “luật tuyệt đối” nếu đã khai báo dependency graph.

Ví dụ:

```java
@Bean("dbClient")
SmartLifecycle dbClient() { /* ... */ }

@Bean("messageConsumer")
@DependsOn("dbClient")
SmartLifecycle messageConsumer() { /* ... */ }
```

Đây là **fragment**, cần implementation thực tế. `messageConsumer` sẽ phụ thuộc `dbClient` theo bean dependency graph. Nhưng không nên gắn `@DependsOn` tràn lan để “sửa” một kiến trúc lifecycle không rõ ràng; hãy thống nhất dependency graph và phase.

### 3.5. Các giá trị mặc định không giống nhau

| Thành phần | Phase mặc định thường gặp | Ý nghĩa |
| --- | --- | --- |
| `Lifecycle` thường, không implements `SmartLifecycle` | `0` khi được lifecycle processor sắp xếp | Nhóm trung gian |
| `SmartLifecycle` không override `getPhase()` | `Integer.MAX_VALUE` | Khởi động muộn, dừng rất sớm |
| `ThreadPoolTaskExecutor` / scheduler thuộc nhóm Spring executor lifecycle | Có default phase riêng, thường `Integer.MAX_VALUE / 2` trên dòng Framework 6.2 | Phải kiểm tra implementation/version |
| Web server graceful shutdown trong Spring Boot | Có phase do Boot thiết kế | Không tự giả định phase của một server cụ thể từ số bạn đặt |

Cần nhớ đặc biệt: **đặt một custom worker ở phase 100 trong khi executor của nó có phase rất cao có thể làm executor dừng trước worker**. Điều này trái mong muốn “worker vẫn cần executor để drain”. Hãy kiểm tra phase của **cả dependency thực**, không chỉ custom wrapper.

### 3.6. Không dùng Integer.MAX_VALUE vô tội vạ

`SmartLifecycle.DEFAULT_PHASE` là `Integer.MAX_VALUE`. Nếu nhiều beans đều dùng mặc định, chúng sẽ rơi vào cùng phase rất cao, mất khả năng điều phối chi tiết. Dùng bộ hằng số phase có tên rõ ràng và kiểm tra bean của framework.

Ví dụ bộ phase của một hệ riêng:

```java
public final class ShutdownPhases {
    private ShutdownPhases() {}

    public static final int RESOURCES = -100;
    public static final int EXECUTORS = 0;
    public static final int WORKERS = 100;
    public static final int SCHEDULERS = 200;
    public static final int INGRESS = 300;
}
```

Đây là **fragment hằng số**, không phải các giá trị hệ thống Spring bắt buộc. Trong một ứng dụng Boot, web server và listener infrastructure có thể dùng các phase rất lớn; số của bạn phải tương thích thực tế.

## 4. Ví dụ Spring Java 17+: xác minh thứ tự start/stop qua phase

Ví dụ hoàn chỉnh sau dùng Spring Framework **6.2.14**, Maven và JDK 17+. Bốn `SmartLifecycle` beans ghi event khi start và stop, sau đó đóng `AnnotationConfigApplicationContext` và in danh sách. Nó không dùng sleep hay giả định thứ tự bean declarations.

### 4.1. Cấu trúc project

```text
smart-lifecycle-demo/
├── pom.xml
└── src/main/java/demo/
    ├── PhaseOrderingDemo.java
    └── AsyncStopCallbackDemo.java
```

### 4.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>smart-lifecycle-demo</artifactId>
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
        </plugins>
    </build>
</project>
```

### 4.3. src/main/java/demo/PhaseOrderingDemo.java

```java
package demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class PhaseOrderingDemo {

    public static class Timeline {
        private final List<String> events =
                Collections.synchronizedList(new ArrayList<>());

        void add(String event) {
            events.add(event);
        }

        String asLine() {
            synchronized (events) {
                return String.join(" > ", events);
            }
        }
    }

    public static class OrderedPart implements SmartLifecycle {
        private final String name;
        private final int phase;
        private final Timeline timeline;
        private final AtomicBoolean running = new AtomicBoolean();

        public OrderedPart(String name, int phase, Timeline timeline) {
            this.name = name;
            this.phase = phase;
            this.timeline = timeline;
        }

        @Override
        public void start() {
            if (running.compareAndSet(false, true)) {
                timeline.add("start-" + name);
            }
        }

        @Override
        public void stop() {
            if (running.compareAndSet(true, false)) {
                timeline.add("stop-" + name);
            }
        }

        @Override
        public void stop(Runnable callback) {
            try {
                stop();
            } finally {
                callback.run();
            }
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public int getPhase() {
            return phase;
        }

        @Override
        public boolean isAutoStartup() {
            return true;
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        public Timeline timeline() {
            return new Timeline();
        }

        @Bean
        public OrderedPart resources(Timeline timeline) {
            return new OrderedPart("resources", -100, timeline);
        }

        @Bean
        public OrderedPart workers(Timeline timeline) {
            return new OrderedPart("workers", 0, timeline);
        }

        @Bean
        public OrderedPart schedulers(Timeline timeline) {
            return new OrderedPart("schedulers", 100, timeline);
        }

        @Bean
        public OrderedPart ingress(Timeline timeline) {
            return new OrderedPart("ingress", 200, timeline);
        }
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class);

        Timeline timeline = context.getBean(Timeline.class);
        context.close();

        System.out.println(timeline.asLine());
    }
}
```

### 4.4. Biên dịch và chạy

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.PhaseOrderingDemo
```

Dòng stdout do chương trình in:

```text
start-resources > start-workers > start-schedulers > start-ingress > stop-ingress > stop-schedulers > stop-workers > stop-resources
```

**Phân tích:**

1. `AnnotationConfigApplicationContext(Config.class)` refresh context; `SmartLifecycle` beans với `isAutoStartup() == true` được start.
2. Spring start các phase `-100, 0, 100, 200` theo thứ tự tăng dần.
3. `context.close()` kích hoạt coordinated lifecycle shutdown.
4. Spring stop các phase `200, 100, 0, -100` theo thứ tự giảm dần.
5. `OrderedPart.stop(Runnable)` gọi `stop()` đồng bộ rồi invoke callback **sau khi** trạng thái running đã thành false.
6. `Timeline` ghi lại cả startup và shutdown để kiểm chứng; không cần dựa vào thread timing.

Đây là mô hình **logic components**, không phải bằng chứng các executor thực tế của Spring Boot đều có đúng phase này. Khi tích hợp hệ thống thật, phải kiểm tra phase của những beans framework đang có.

### 4.5. Vì sao dùng AtomicBoolean?

`running` được quan sát qua `isRunning()` và có thể thay đổi khi lifecycle processor chạy; `AtomicBoolean` cung cấp visibility và tránh ghi log start/stop nhiều lần trong mô hình đơn giản. Nó không thay thế lock/transaction cho mọi biến trạng thái của một worker thật.

### 4.6. Vì sao không cho stop(Runnable) chạy callback ngay trước stop()?

Nếu gọi callback trước khi resource thật sự đóng hoặc worker thật sự drain, Spring có thể tiến tới dừng phase thấp hơn, ví dụ đóng DB pool trước khi worker hoàn tất. Callback là **tín hiệu completion**, không phải “đã nhận lệnh stop”.

## 5. stop(Runnable callback): hợp đồng quan trọng nhất

`SmartLifecycle.stop(Runnable)` được thiết kế để xử lý các component cần một khoảng thời gian dừng: consumer phải ngừng polling, worker phải xử lý hàng đợi còn lại, HTTP server phải drain requests, scheduler phải ngừng phát task mới.

### 5.1. Synchronous stop

Nếu quá trình dừng hoàn toàn đồng bộ, implement theo mẫu:

```java
@Override
public void stop(Runnable callback) {
    try {
        stop();
    } finally {
        callback.run();
    }
}
```

Fragment này yêu cầu class có method `stop()` đúng nghĩa. Spring chỉ nên coi phase đã hoàn tất khi cleanup đã xong; `finally` bảo đảm callback vẫn được phát nếu quá trình dừng gặp exception, **nhưng** trong production phải phân biệt “đã dừng sạch” với “cleanup lỗi/aborted”; đừng báo hoàn tất thành công giả nếu tài nguyên vẫn hoạt động và không có phương án fallback.

### 5.2. Asynchronous stop

Với quá trình drain bất đồng bộ:

```java
@Override
public void stop(Runnable callback) {
    acceptingNewWork.set(false);

    drainingExecutor.execute(() -> {
        try {
            waitUntilInFlightWorkFinishes();
            releaseInternalResources();
        } finally {
            running.set(false);
            callback.run();
        }
    });
}
```

Đây là **fragment thiết kế**, không phải file chạy độc lập. Phải xác định `drainingExecutor` còn sống khi `stop` được gọi, có xử lý `execute` bị reject, và callback được gọi **đúng một lần**.

**Không** làm như sau:

```java
@Override
public void stop(Runnable callback) {
    acceptingNewWork.set(false);
    drainingExecutor.execute(this::waitUntilInFlightWorkFinishes);
    callback.run(); // Sai: worker có thể vẫn đang drain.
}
```

Đoạn trên phát tín hiệu hoàn tất quá sớm.

### 5.3. Callback phải được gọi sau khi dừng thật

Hợp đồng của Spring yêu cầu callback được gọi khi completion đã xảy ra. Nếu bean quên gọi callback, `DefaultLifecycleProcessor` có thể chờ tới hết **timeout của phase**, làm chậm shutdown tổng thể.

Callback nên được gọi **một lần**. Nếu nhiều nhánh code hoặc timeout fallback đều có thể gọi callback, dùng một cơ chế đảm bảo one-shot (ví dụ `AtomicBoolean.compareAndSet`) nhưng không sử dụng nó để che trạng thái chưa drain.

### 5.4. Nếu stop() không được gọi thì sao?

`DefaultLifecycleProcessor` chỉ stop những lifecycle beans nó đang quản lý và thấy đang running theo điều kiện của processor. Đừng mong stop callback luôn được gọi dù bean chưa từng start hoặc đã stop trước đó; nếu có các tác vụ ngoài vòng đời bean, phải có cleanup riêng hoặc hạ tầng sở hữu.

Khi container gọi variant `stop(Runnable)`, việc bạn override `stop()` nhưng bỏ qua variant callback có thể tạo khác biệt. Test cả explicit `context.stop()` lẫn `context.close()` theo đúng contract version.

### 5.5. Không giữ lock chặn callback

Nếu `stop(Runnable)` chờ một worker thread đang cần cùng lock mà stop giữ, bạn có thể tạo deadlock. Khi thực sự bất đồng bộ, phát tín hiệu dừng và return; giữ các tài nguyên mà worker cần cho tới khi drain xong, rồi gọi callback. Không gọi blocking I/O vô hạn trên Spring shutdown thread mà không có timeout.

## 6. Ví dụ Spring Java 17+: Spring chờ callback async trước khi đóng resource

Ví dụ thứ hai dùng cùng `pom.xml` ở phần 4. Nó tạo ba components:

- **Ingress**, phase 200: dừng nhận công việc mới.
- **DrainingWorker**, phase 100: chờ một latch để mô phỏng xử lý công việc đang chạy, sau đó mới gọi callback.
- **Resources**, phase 0: được dừng **chỉ sau khi worker callback hoàn tất**.

Để tránh deadlock trong demo, `context.close()` chạy trên một thread riêng, còn main giữ quyền quyết định khi nào drain được hoàn thành.

### 6.1. src/main/java/demo/AsyncStopCallbackDemo.java

```java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class AsyncStopCallbackDemo {

    static final class State {
        final CountDownLatch drainRequested = new CountDownLatch(1);
        final CountDownLatch releaseDrain = new CountDownLatch(1);
        final AtomicBoolean callbackFinished = new AtomicBoolean();
    }

    static final class Resources implements SmartLifecycle {
        final AtomicBoolean running = new AtomicBoolean();

        @Override public int getPhase() { return 0; }
        @Override public boolean isRunning() { return running.get(); }
        @Override public void start() { running.set(true); }
        @Override public void stop() { running.set(false); }
        @Override public void stop(Runnable callback) {
            try {
                stop();
            } finally {
                callback.run();
            }
        }
    }

    static final class DrainingWorker implements SmartLifecycle {
        final AtomicBoolean running = new AtomicBoolean();
        final State state;

        DrainingWorker(State state) {
            this.state = state;
        }

        @Override public int getPhase() { return 100; }
        @Override public boolean isRunning() { return running.get(); }
        @Override public void start() { running.set(true); }
        @Override public void stop() { running.set(false); }

        @Override
        public void stop(Runnable callback) {
            state.drainRequested.countDown();

            Thread drainer = new Thread(() -> {
                try {
                    state.releaseDrain.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    running.set(false);
                    state.callbackFinished.set(true);
                    callback.run();
                }
            }, "worker-drainer");

            drainer.start();
        }
    }

    static final class Ingress implements SmartLifecycle {
        final AtomicBoolean running = new AtomicBoolean();

        @Override public int getPhase() { return 200; }
        @Override public boolean isRunning() { return running.get(); }
        @Override public void start() { running.set(true); }
        @Override public void stop() { running.set(false); }
        @Override public void stop(Runnable callback) {
            try {
                stop();
            } finally {
                callback.run();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        State state() { return new State(); }

        @Bean
        Resources resources() { return new Resources(); }

        @Bean
        DrainingWorker worker(State state) {
            return new DrainingWorker(state);
        }

        @Bean
        Ingress ingress() { return new Ingress(); }
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class);

        State state = context.getBean(State.class);
        Resources resources = context.getBean(Resources.class);
        AtomicReference<Throwable> closeFailure =
                new AtomicReference<>();

        Thread closer = new Thread(() -> {
            try {
                context.close();
            } catch (Throwable ex) {
                closeFailure.set(ex);
            }
        }, "context-closer");

        try {
            closer.start();

            if (!state.drainRequested.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("drain was not requested");
            }

            System.out.println("resources-alive-during-drain="
                    + resources.isRunning());

            state.releaseDrain.countDown();
            closer.join(5_000);

            if (closer.isAlive()) {
                throw new IllegalStateException("context close timed out");
            }
            if (closeFailure.get() != null) {
                throw new IllegalStateException(closeFailure.get());
            }

            System.out.println("callback-finished="
                    + state.callbackFinished.get());
            System.out.println("resources-stopped-after-drain="
                    + !resources.isRunning());
        } finally {
            state.releaseDrain.countDown();
        }
    }
}
```

### 6.2. Lệnh chạy

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.AsyncStopCallbackDemo
```

Output do chương trình in:

```text
resources-alive-during-drain=true
callback-finished=true
resources-stopped-after-drain=true
```

**Vì sao đáng tin?**

1. `Resources` phase 0 và `DrainingWorker` phase 100 đều được auto-start; `Ingress` phase 200 cũng được start.
2. Khi `context.close()`, Spring dừng phase 200 trước.
3. Spring gọi `DrainingWorker.stop(Runnable)` ở phase 100, hàm lập thread drain và return.
4. `state.drainRequested` được giảm trước khi thread chờ; main đợi latch để chắc chắn phase 100 đã bắt đầu.
5. Main **chưa** mở `releaseDrain` khi in `resources.isRunning()`; vì phase 100 chưa gọi callback nên Spring chưa đi tới phase 0.
6. Main mở `releaseDrain`; drainer đánh dấu finished, gọi callback; `DefaultLifecycleProcessor` tiếp tục và dừng Resources.
7. Main `join` vào thread đóng context để quan sát kết quả cuối, không dựa vào `Thread.sleep`.

**Giới hạn:** code dùng latch để mô phỏng tiến trình drain; một worker thật có thể phải ngừng polling, chờ executor jobs, commit transaction và xử lý error/timeout. `callbackFinished=true` trong demo chỉ chứng minh callback đã được gọi, không phải bảo đảm một hệ thống bên ngoài đã ack message.

### 6.3. Không dùng ví dụ này như production shutdown code nguyên xi

Tác vụ production cần:

- Xử lý khi thread không tạo được hoặc `Executor.execute(...)` bị reject.
- Có deadline/bounded wait hoặc fallback rõ ràng.
- Tránh gọi callback sớm khi in-flight operation vẫn còn.
- Không lặp callback nếu timeout watchdog cùng gọi.
- Đo số công việc in-flight và kết quả drain.
- Phân loại `completed`, `requeued`, `cancelled`, `unknown`.
- Xác định quyền sở hữu job nếu worker/process chết.
- Đảm bảo tài nguyên cho drainer vẫn sống tới lúc callback hoàn tất.

## 7. Thiết kế thực tế: ingress → scheduler → worker → resource

Một nguyên tắc hiệu quả là **dừng nguồn sinh việc trước khi dừng nơi xử lý việc**, cuối cùng mới đóng tài nguyên.

```text
Application RUNNING
      |
      v
1. STOP ACCEPTING
   - đổi readiness / ngừng listener / tắt admission gate
      |
      v
2. STOP PRODUCING
   - hủy lịch định kỳ, ngừng enqueue job mới
      |
      v
3. DRAIN IN-FLIGHT
   - xử lý các task đã chấp nhận
   - commit/ack hoặc lưu trạng thái retry
      |
      v
4. CLOSE SHARED RESOURCES
   - shutdown executors còn lại
   - đóng DB/client/broker resources
      |
      v
Application TERMINATED
```

### 7.1. Đặt phase theo dependency, không theo tên component

Ví dụ lựa chọn (cần điều chỉnh sau khi xem framework bean phases):

| Component | Phase minh họa | Startup | Shutdown |
| --- | ---: | --- | --- |
| DB/remote resource owner | -100 | Mở đầu tiên | Đóng cuối |
| Executor mà worker phải dùng khi drain | 0 | Sẵn sàng sớm | Dừng sau worker |
| Worker dispatcher / in-flight drain | 100 | Start sau executor | Dừng trước executor |
| Scheduler phát background work | 200 | Start sau worker | Dừng trước worker |
| Ingress consumer / application gate | 300 | Start cuối | Dừng đầu tiên |

**Không đặt phase như bảng rồi tin Spring-managed executor/HTTP server đã tự nhận số đó.** Bạn phải kiểm tra phase của bean cụ thể. Ví dụ các executor/schedulers kế thừa `ExecutorConfigurationSupport` trong dòng 6.2 có default phase riêng gần `Integer.MAX_VALUE/2`. Nếu worker ở phase 100 trong khi executor ở phase cao hơn nhiều, executor có thể dừng trước worker: trái với ý định drain.

### 7.2. Phải kiểm soát real executor phase

Với `ThreadPoolTaskExecutor` được quản lý bởi Spring, có thể đặt phase cụ thể khi API phiên bản đó hỗ trợ:

```java
@Bean("businessExecutor")
public ThreadPoolTaskExecutor businessExecutor() {
    ThreadPoolTaskExecutor executor =
            new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("business-");
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setPhase(0); // Thí dụ: thấp hơn worker drain phase 100.
    return executor;
}
```

Đây là **fragment**, cần imports và cấu hình context. Mục tiêu là tránh executor shutdown sớm. Tuy vậy, **phase một mình chưa đủ**: `setStrictEarlyShutdown`, `setAcceptTasksAfterContextClose`, `setWaitForTasksToCompleteOnShutdown` và destroy callbacks có semantics khác nhau, có thể tác động tới lúc executor ngừng nhận task/đợi việc. Đối chiếu Javadoc Spring phiên bản cụ thể và test SIGTERM thật.

### 7.3. Ngưng nguồn nhận trước khi đổi worker state

Với message broker:

1. Stop/pause listener container hoặc ngưng polling/dispatch message mới.
2. Đợi các handler đang chạy.
3. Ack/commit offsets đúng contract sau thành công, hoặc để message được redeliver khi chưa thành công.
4. Dừng worker pool sau khi handler không còn cần nó.
5. Dừng broker client/DB resources cuối.

Nếu listener dừng rồi nhưng một callback đã dispatch vẫn đang hoạt động, phải đợi/ghi nhận nó theo chính sách. `stop(Runnable)` của một wrapper không tự dừng một consumer container bên dưới trừ khi wrapper thật sự gọi và đợi container đó.

### 7.4. Ngưng scheduler trước khi drain worker

`@Scheduled` hoặc `TaskScheduler` có thể vẫn đang tạo việc mới ngay cả khi worker bắt đầu shutdown. Bởi vậy, scheduler cần ngừng phát callback trước, sau đó mới drain những jobs đã chấp nhận. Dùng scheduled future cancel/TaskScheduler shutdown và policy phù hợp với loại scheduler.

Không nghĩ hủy lịch sẽ hủy luôn một `@Async` task đã được bàn giao; scheduler future là handle của trigger, còn business future/operation state là chuyện khác.

### 7.5. DB connection pool được giải phóng khi nào?

Một datasource thường là bean có destroy method, không nhất thiết là `SmartLifecycle` bean. Khi context close, Spring xử lý lifecycle stop rồi mới destroy beans theo bean factory semantics. Nhưng nếu bạn chủ động đóng datasource trong một custom lifecycle phase quá cao, worker phase thấp có thể mất DB connection trước khi kết thúc.

Đừng triển khai `@PreDestroy` sớm ở một bean dùng chung rồi giả định mọi `SmartLifecycle` stop đã hoàn tất; phải kiểm tra thứ tự thực tế và owner của tài nguyên.

## 8. DefaultLifecycleProcessor chờ callback như thế nào?

Khi shutdown:

1. Processor duyệt phases theo thứ tự giảm dần.
2. Trong mỗi phase, nó yêu cầu các beans đang chạy stop; `SmartLifecycle` nhận callback.
3. Processor đợi callback của nhóm phase đó, tối đa theo timeout đã cấu hình.
4. Khi nhóm phase hoàn tất hoặc timeout, processor tiếp tục với phase thấp hơn theo hành vi framework.
5. Cuối cùng context tiến tới destruction/cleanup của beans.

**Nếu callback không chạy:** phase đó có thể chờ tới timeout. **Nếu timeout hết:** Spring không thể bảo đảm worker vẫn có resources cần thiết khi nó tiếp tục hoạt động; các phase khác có thể được xử lý và tài nguyên bị dừng.

### 8.1. Timeout không phải bảo đảm dừng cưỡng chế

`setTimeoutPerShutdownPhase(...)` giới hạn thời gian processor **đợi tín hiệu hoàn tất**, không có nghĩa JVM sẽ giết mọi thread hoặc rollback transaction sau thời điểm đó.

Một callback đến sau timeout có thể được gọi khi Spring đã bắt đầu dừng resource của phase khác. Do đó cần timeout riêng cho downstream I/O, deadline nghiệp vụ, idempotency và một chiến lược dừng an toàn khi không thể drain kịp.

### 8.2. Spring Framework 6.2: default timeout và cấu hình

Javadoc `DefaultLifecycleProcessor` của Spring Framework **6.2** ghi default per-phase timeout là **10.000 milliseconds (10 giây)** trong implementation đó. Các version/distributions khác có thể cấu hình giá trị khác; không dựa vào con số này như một SLA bất biến của mọi ứng dụng Spring Boot.

Ví dụ tạo bean tên đặc biệt `lifecycleProcessor` (fragment Spring Framework 6.2):

```java
@Bean(name = "lifecycleProcessor")
public DefaultLifecycleProcessor lifecycleProcessor() {
    DefaultLifecycleProcessor processor =
            new DefaultLifecycleProcessor();
    processor.setTimeoutPerShutdownPhase(20_000);
    processor.setTimeoutForShutdownPhase(100, 40_000);
    return processor;
}
```

Nó đặt timeout mặc định 20 giây và phase 100 là 40 giây. `setTimeoutForShutdownPhase` có từ Spring Framework 6.2. Đoạn này cần imports và phải tích hợp đúng một bean `lifecycleProcessor`; nếu project Boot đã quản lý custom lifecycle processor, cần tránh tạo trùng hay vô tình ghi đè các cấu hình khác.

### 8.3. Timeout cộng dồn theo phase

Nếu có nhiều nhóm phase, mỗi phase có một timeout, shutdown có thể tốn tổng thời gian đáng kể khi nhiều callback treo. Tổng thời gian trong thực tế còn có destroy hooks, network, executor termination và các bước bên ngoài lifecycle processor. Kubernetes termination grace period phải tính đến **toàn bộ chuỗi**, không chỉ một phase.

Ví dụ bốn phases mỗi phase tối đa 20 giây không cho phép suy “shutdown hoàn toàn trong 20 giây” nếu tất cả đều chờ hết timeout.

### 8.4. Concurrent startup tùy phiên bản

Trong Spring Framework **6.2.6+**, `DefaultLifecycleProcessor` còn hỗ trợ cấu hình concurrent startup theo phase với timeout tương ứng. Điều này ảnh hưởng cách các beans cùng phase được start. Không lấy giả định startup luôn tuần tự trong mọi phiên bản/cấu hình để xây coupling không khai báo.

Nguyên tắc bền vững: **đừng dựa vào thứ tự các bean cùng phase**; hãy biểu diễn dependency hoặc phase rõ.

## 9. Spring Boot graceful shutdown

Spring Boot kết hợp lifecycle processor với shutdown của embedded web server và những thành phần đã đăng ký `SmartLifecycle`.

Với **Spring Boot 3.3**, có thể cấu hình:

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
```

Đây là **fragment application.yml** cho Boot 3.3 theo reference của phiên bản đó; các version Boot mới hơn có thể thay đổi default của `server.shutdown`. Không tuyên bố mọi bản Boot đều bật graceful shutdown mặc định.

### 9.1. Web server graceful shutdown làm gì?

Trong đường hỗ trợ, Boot sẽ ngừng nhận request mới hoặc trả trạng thái từ chối theo embedded server, đồng thời cho các request đang xử lý một khoảng grace để hoàn tất. Nó diễn ra như một phần của context shutdown ở **pha sớm trong quá trình dừng** `SmartLifecycle` beans.

**Không có nghĩa:**

- Task đã submit vào `@Async` executor mà controller không đợi sẽ tự được Boot đợi tới khi hoàn tất.
- Kafka consumer hay một custom scheduler tự được web server dừng thay.
- Một cuộc gọi HTTP ra dịch vụ khác đã được rollback.
- Job đã nhận được đảm bảo không mất nếu process bị kill.

Từng thành phần phải có lifecycle và state/ack riêng.

### 9.2. Boot property chỉ điều khiển processor theo bối cảnh

`spring.lifecycle.timeout-per-shutdown-phase` là cấu hình Spring Boot cho khoảng chờ mỗi shutdown phase; khác với `server.tomcat.connection-timeout`, HTTP client read timeout hoặc deadline nghiệp vụ của một job.

Bạn có thể cấu hình phase timeout của `DefaultLifecycleProcessor` trực tiếp trong Spring Framework, nhưng tránh kết hợp nhiều nguồn config mà không biết giá trị nào được áp dụng.

### 9.3. Readiness và traffic draining

Với Kubernetes/Service Mesh/load balancer, shutdown tốt thường là:

1. Pod được đánh dấu không sẵn sàng phục vụ request mới.
2. Load balancer dần thôi gửi request mới.
3. HTTP server ngừng nhận kết nối/request mới.
4. Đợi request in-flight và background jobs theo deadline.
5. Đóng executors/resources.
6. Process kết thúc.

Có thể có thời gian truyền tín hiệu readiness tới endpoints/proxy. Vì vậy cần admission gate trong app và idempotency; không chỉ tin rằng một health check đổi giá trị thì toàn bộ upstream lập tức ngừng gửi request.

## 10. Kubernetes: khớp shutdown timeout với terminationGracePeriodSeconds

Kubernetes khi chấm dứt Pod thường gửi tín hiệu termination tới process và chờ một grace period trước khi buộc dừng. Ứng dụng Java phải nhận tín hiệu và cho Spring đóng context đúng cách. Nếu bị `SIGKILL`, JVM **không chạy đầy đủ shutdown hooks**.

Ví dụ manifest snippet:

```yaml
spec:
  terminationGracePeriodSeconds: 90
  containers:
    - name: app
      image: example/app:java21
      ports:
        - containerPort: 8080
```

Đây là **YAML fragment**, không phải Pod manifest hoàn chỉnh. 90 giây chỉ là ví dụ cần tính từ thực tế.

Một budget mẫu:

```text
Load balancer / readiness propagation   5–10s
Web request draining                   10–20s
Background task / consumer draining    15–30s
Resource finalization                   3–5s
Safety margin                           5–10s
--------------------------------------------
Pod termination grace period: phải đủ cho kế hoạch thực tế
```

Đây là các khoảng minh họa, không phải số đo thực nghiệm của ứng dụng.

**Chú ý:** timeout per shutdown phase và OS grace period có thể không hoạt động như các phép cộng đơn giản nếu các bước chồng lấn hoặc có per-component timeout khác. Cần đo một lần SIGTERM/rolling update thật và theo dõi timeline.

### 10.1. Khi không đủ thời gian để drain

- Dừng nhận việc mới ngay khi shutdown bắt đầu.
- Cho in-flight jobs cơ hội kết thúc trong deadline.
- Nếu không kịp, lưu durable state để worker mới tiếp tục hoặc message được redeliver.
- Bảo đảm idempotency để retry không tạo duplicate effect.
- Nếu đã gửi external payment/HTTP call, có thể cần reconciliation, không tuyên bố “đã hủy”.
- Không giữ pod sống vô hạn vì một task kẹt trên network.

### 10.2. Background worker không giống HTTP request

Một server có thể ngừng HTTP tiếp nhận và vẫn còn queue consumers hoặc scheduler phát task. Bạn phải cho cả các nguồn khác tham gia lifecycle phối hợp. Nếu container bị dừng giữa job không bền vững chỉ sống trong RAM, dữ liệu công việc có thể mất.

## 11. Lifecycle của Spring ThreadPoolTaskExecutor và ThreadPoolTaskScheduler

### 11.1. Không tự chế wrapper nếu hạ tầng đã có lifecycle

`ThreadPoolTaskExecutor` và `ThreadPoolTaskScheduler` có lifecycle/shutdown semantics trong dòng Spring hiện đại. Kiểm tra implementation của chúng trước khi tạo một `SmartLifecycle` wrapper cạnh tranh ownership.

Ví dụ fragment:

```java
@Bean("reportExecutor")
ThreadPoolTaskExecutor reportExecutor() {
    ThreadPoolTaskExecutor executor =
            new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("report-");
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(50);
    executor.setPhase(0);
    return executor;
}
```

Bean framework sẽ được khởi tạo/destroy theo context. Nếu một wrapper khác tự gọi `executor.shutdown()` trước khi các phase worker drain hoàn tất, bạn đang tạo một shutdown path thứ hai và có thể gây rejection.

### 11.2. Các flag có semantics khác nhau

Tùy Spring Framework version, các method trên executor support gồm:

- `setPhase(int)`: phase lifecycle.
- `setStrictEarlyShutdown(boolean)`: điều khiển hành vi shutdown sớm/reject task mới khi context close ở những implementation có hỗ trợ.
- `setWaitForTasksToCompleteOnShutdown(boolean)`: tùy lựa chọn, chờ task đang/đã queue xong khi shutdown nhưng có tương tác với coordinated lifecycle stop.
- `setAwaitTerminationSeconds(int)`: chờ executor termination trong thời gian cấu hình.
- `setAcceptTasksAfterContextClose(boolean)`: thay đổi việc chấp nhận task sau context close, có thể bỏ qua coordinated lifecycle shutdown.

**Không kết hợp các flag theo phỏng đoán**. Javadoc Spring 6.2 mô tả các flag ảnh hưởng đến thời điểm executor dừng và việc tham gia coordinated lifecycle; một số flag có quyền ưu tiên/tác dụng ghi đè. Test với nhiệm vụ đang chạy và nhiệm vụ được submit sau context close.

### 11.3. Context.close() và ExecutorService.shutdown() không thay nhau

`ExecutorService.shutdown()` ngừng nhận task mới và cho phép những task đã được submit hoàn tất theo Java executor contract, nhưng code của bạn còn phải gọi `awaitTermination` hoặc dùng chính sách khác nếu cần **đợi**. Spring lifecycle processor có cơ chế dừng phối hợp theo phase, nhưng không tự thay đổi semantics của bất kỳ custom executor nào không tham gia quản lý.

Nếu bạn tạo `new ThreadPoolExecutor(...)` trong một singleton field mà không gắn lifecycle/destroy, Spring không thể đảm bảo pool đó được dừng khi context close. Hãy để Spring quản lý executor bean hoặc implement cleanup đúng.

### 11.4. Virtual threads cũng phải có shutdown policy

Virtual threads không tự giải quyết durability/lifecycle. `VirtualThreadTaskExecutor` tạo virtual thread per task; JVM termination, callback in-flight, I/O timeouts và business reconciliation vẫn cần thiết kế. Với Java 21, virtual threads là daemon threads: không dựa vào sự tồn tại của chúng để giữ JVM sống vô thời hạn.

## 12. @Scheduled, @Async và SmartLifecycle

### 12.1. Scheduled callback là nguồn sinh công việc

Một `@Scheduled` method có thể tiếp tục phát `@Async` tasks trong lúc process sắp dừng. Nếu muốn drain sạch, ngừng phát task mới trước:

```text
@Scheduled trigger
       |
       v
TaskScheduler       -- stop producing -->
       |
       v
@Async executor     -- stop accepting -->
       |
       v
business callback   -- drain/commit/ack -->
       |
       v
DB / external API    -- close last -->
```

**Thứ tự dừng** mới là một phần; phải ngăn race giữa callback đang submit và executor đã ngừng nhận task.

### 12.2. fixedDelay không đợi công việc async tự động

`@Scheduled(fixedDelay=...)` tính khoảng delay từ lúc scheduled invocation kết thúc. Nếu invocation chỉ gọi `@Async` rồi return, worker có thể vẫn chạy. Khi stop scheduler, một worker async đã được bàn giao vẫn cần được đợi riêng. Không dùng `ScheduledFuture.isDone()` để kết luận “mọi tác dụng phụ đã commit”.

### 12.3. CompletableFuture.cancel(true) không cưỡng chế mọi task dừng

`CompletableFuture.cancel(boolean)` theo Java API có semantics đánh dấu future cancelled, không dùng `mayInterruptIfRunning` để điều khiển computation. Nếu worker cần dừng hợp tác, dùng cancellation token/deadline, interrupt ở nơi có tác dụng và kiểm tra trước side effect; không suy từ cancelled future rằng database đã rollback.

### 12.4. @Transactional không đi theo @Async tự động

Transaction imperative của Spring thường gắn với thread thực thi. `@Async` chuyển task sang worker, không mang cùng Hibernate Session/JPA persistence context sang. Khi shutdown:

- Đợi transaction quan trọng hoàn tất trước khi đóng DB client.
- Không chia sẻ managed JPA entity giữa thread.
- Nếu job chưa commit, phải chọn rollback/retry/durable state rõ ràng.
- Nếu đã commit nhưng kết quả downstream chưa biết, cần idempotency/đối soát.

`SmartLifecycle` điều phối **khi dừng**; nó không tự giải quyết tính đúng/sai của transaction nghiệp vụ.

## 13. Các lớp trạng thái cần theo dõi

Một component không nên chỉ có biến `running`:

| Trạng thái | Nghĩa | Quyết định |
| --- | --- | --- |
| `STARTING` | Đang khởi tạo resources | Chưa mở nhận việc |
| `RUNNING` | Đã sẵn sàng nhận và thực thi | Accept theo quota |
| `QUIESCING` | Dừng nhận việc mới | Reject/requeue submissions mới |
| `DRAINING` | Đợi việc đang chạy | Duy trì dependencies |
| `STOPPED` | Không còn việc sở hữu, resources có thể giải phóng | Báo stop callback |
| `FAILED` | Không thể drain sạch | Báo lỗi/đối soát |
| `UNKNOWN` | External operation outcome chưa được xác nhận | Không retry mù quáng |

Một `isRunning()` của `SmartLifecycle` là tín hiệu lifecycle, không thay thế trạng thái job, độ sâu queue, connection pool usage hoặc downstream operation outcomes.

### 13.1. Admission gate

Một wrapper có thể dùng `AtomicBoolean accepting` để từ chối yêu cầu mới khi shutdown bắt đầu:

```java
if (!accepting.get()) {
    throw new RejectedExecutionException("component is stopping");
}
```

Đây là fragment, cần đồng bộ giữa **kiểm tra** và **submit thực tế** nếu strict acceptance boundary là bắt buộc; một lần đọc AtomicBoolean rồi submit sau có check-then-act race. Dùng lock/admission mechanism hoặc executor rejection handling đáng tin và xác định lúc nào job được xem là “đã được chấp nhận”.

### 13.2. In-flight counter

`AtomicInteger` hoặc `Phaser` có thể theo dõi số jobs đang chạy; đợi counter về 0 khi drain. Nhưng phải:

- Tăng counter **trước** hoặc nguyên tử với việc nhận việc.
- Giảm trong `finally` ngay cả khi job ném exception.
- Không decrement hai lần.
- Không coi `counter==0` là an toàn nếu thread khác vẫn có thể nhận job mới.
- Có timeout và chính sách xử lý lúc worker treo.
- Nếu nhiều replicas, counter cục bộ không thể hiện toàn cụm.

### 13.3. Đừng chờ dưới lock gây deadlock

Nếu stop thread giữ lock mà worker cần để giảm in-flight counter, stop đợi mãi. Thiết kế dừng hợp tác: đóng admission trước, release lock, đợi bằng latch/condition/future; các worker có thể tiếp tục cập nhật trạng thái và gọi callback sau khi hoàn tất.

## 14. stop(Runnable) và lỗi khi dừng

### 14.1. Nếu drain thành công

- Ghi trạng thái stopped.
- Đóng resources thuộc quyền sở hữu mà không ảnh hưởng dependency cần được giữ lâu hơn.
- Gọi callback **đúng một lần**.
- Log phase, thời lượng và số job đã drain.

### 14.2. Nếu drain timeout

Cần quyết định giữa:

- Chờ thêm nếu OS grace vẫn cho phép và policy của tổ chức chấp nhận.
- Hủy hợp tác rồi bảo đảm những task chưa commit được phục hồi qua queue/job store.
- Phát tình trạng `UNKNOWN` cho external effects đang in-flight.
- Thực hiện cleanup bắt buộc có giới hạn rồi gọi callback khi thật sự đạt trạng thái terminal theo contract.
- Nếu không thể an toàn, ghi nhận shutdown failure để operator biết.

**Không** dùng callback như “nút bỏ qua timeout”: gọi callback khi không biết worker đang làm gì có thể làm phase thấp đóng tài nguyên quá sớm. Nếu cuối cùng buộc phải bỏ chờ, application phải thiết kế bền vững/idempotent đủ để xử lý tình trạng đó.

### 14.3. Nếu callback bị gọi hai lần

Processor thường dùng latch/counter để đợi callbacks; gọi hai lần có thể làm số liệu không phản ánh đúng và là vi phạm hợp đồng. Đặt one-shot state ở implementation khi có nhiều nhánh completion cùng khả năng chạy:

```java
AtomicBoolean callbackSent = new AtomicBoolean();

void finishStop(Runnable callback) {
    if (callbackSent.compareAndSet(false, true)) {
        callback.run();
    }
}
```

Đây là **fragment**, không phải thay thế kiểm tra worker đã dừng. Nó chỉ bảo đảm callback không lặp. Cần reset state khi một lifecycle được phép `start()` trở lại sau `stop()`, hoặc thiết kế token theo từng lần stop.

### 14.4. Callback và exception handler

Nếu async drainer ném exception nhưng callback vẫn được gọi ở `finally`, processor có thể tiếp tục shutdown. Đó có thể là chính sách hợp lý để không treo process vô hạn, nhưng phải **ghi log nguyên nhân và lưu trạng thái chưa hoàn tất**. `finally` không biến lỗi cleanup thành thành công.

## 15. isAutoStartup=false và start/stop thủ công

`SmartLifecycle.isAutoStartup()` mặc định là true. Khi override false:

```java
@Override
public boolean isAutoStartup() {
    return false;
}
```

Bean không tự start bởi lifecycle processor trong lần refresh như một SmartLifecycle auto-start. Bạn có thể start nó có kiểm soát qua API hoặc ứng dụng, nhưng cần xác định ai sở hữu quyền bật và khi nào.

### 15.1. Lưu ý lazy-init

Spring Javadoc cảnh báo cờ `lazy-init` có tác dụng hạn chế với `SmartLifecycle`: để phát hiện và auto-start các beans, container thường cần instantiate chúng dù có đánh dấu lazy. Đừng chọn `SmartLifecycle` chỉ để giấu một công việc nặng đến khi có request đầu tiên.

### 15.2. ApplicationContext.start() khác close()

- `ConfigurableApplicationContext.start()` yêu cầu lifecycle processor start các lifecycle beans phù hợp.
- `ConfigurableApplicationContext.stop()` yêu cầu dừng lifecycle beans, nhưng **không đóng toàn bộ context**.
- `ConfigurableApplicationContext.close()` kích hoạt shutdown lifecycle **và** destruction/cleanup của các bean.

Sau `stop()`, ứng dụng có thể được `start()` trở lại theo contract; sau `close()`, context đó không còn có thể hoạt động bình thường. Do đó component phải xác định restartability nếu có dùng explicit start/stop.

### 15.3. @PreDestroy khác SmartLifecycle

`@PreDestroy` hoặc destroy method chạy trong giai đoạn destruction của bean và phù hợp cleanup đồng bộ đơn giản. Nó không mang phase start/stop hay async completion callback. Một số ứng dụng kết hợp `SmartLifecycle` để quiesce/drain trước và `@PreDestroy` cho cleanup còn lại.

Đừng chuyển toàn bộ logic drain sang `@PreDestroy` rồi giả định worker luôn còn resource; nếu muốn dừng có thứ tự, hãy xác định lifecycle phases rõ ràng.

## 16. Kiểm thử thứ tự dừng bằng integration test

### 16.1. Test thứ tự phase

`PhaseOrderingDemo` là smoke test có giá trị:

- Assert resources start trước worker.
- Assert ingress start sau scheduler.
- Assert ingress stop trước scheduler.
- Assert worker stop trước resources.

Không cần `Thread.sleep`; chỉ kiểm tra sự kiện do bean ghi lại.

### 16.2. Test callback thật sự được đợi

`AsyncStopCallbackDemo` giữ worker ở trạng thái drain bằng latch và xác minh resources chưa bị đóng trước callback. Đây là test cho invariant quan trọng: **phụ thuộc được giữ sống tới khi phase worker hoàn tất**.

### 16.3. Test thiếu callback

Trong một test context **riêng**, cố tình triển khai `stop(Runnable)` không gọi callback và cấu hình timeout ngắn. Xác nhận processor log timeout rồi tiếp tục shutdown theo version Spring. Không đưa implementation lỗi này vào production, vì nó có thể làm mọi deployment chậm hoặc để task sống khi resources đã bị dừng.

### 16.4. Test nhiều components cùng phase

Tạo hai beans phase 100, cho một bean stop lâu và một bean stop nhanh. Không assert thứ tự các event start/stop của chúng; assert rằng phase thấp hơn chưa bắt đầu stop trước khi nhóm phase 100 hoàn tất hoặc vượt timeout.

### 16.5. Test @DependsOn

Tạo B phụ thuộc A nhưng cố đặt phase mâu thuẫn. Kiểm tra dependent bean B start sau A và stop trước A, rồi sửa phase để mô hình consistent. Dùng test này để tránh reliance vào một ordering vô tình.

### 16.6. Test executor đã nhận việc

Cho một `ThreadPoolTaskExecutor` chạy một task đang chờ latch:

1. Bắt đầu shutdown.
2. Ngừng admission/ingress.
3. Kiểm tra executor vẫn đủ khả năng hoàn tất task đã nhận.
4. Release task.
5. Xác nhận worker drain callback chạy trước khi pool/resources stop.
6. Thử submit job sau điểm quiesce và kiểm tra rejection policy.
7. Kiểm tra exception/interrupt/timeout không khiến in-flight counter mắc kẹt.

### 16.7. Test graceful shutdown trong container

- Chạy app với Boot graceful shutdown và phase timeout.
- Gửi request/job đang chạy có thể điều phối bằng latch hoặc fake backend.
- Gửi SIGTERM hoặc chạy rolling update.
- Quan sát thứ tự `STOP_ACCEPTING → DRAIN → RESOURCES_CLOSED`.
- Kiểm tra request mới bị từ chối theo embedded server behavior.
- Đo thời lượng shutdown so với `terminationGracePeriodSeconds`.
- Lặp lại khi network downstream chậm, executor queue đầy và worker bị lỗi.

Đừng chỉ gọi `context.close()` trong unit test rồi tuyên bố Kubernetes sẽ cho đủ thời gian drain; phải test đúng runtime signal và deployment budget.

## 17. Troubleshooting

| Triệu chứng | Nguyên nhân có thể | Hướng khắc phục |
| --- | --- | --- |
| `SmartLifecycle.start()` không được gọi | `isAutoStartup=false` hoặc bean không được container quản lý | Kiểm tra context và auto-start |
| Bean startup không theo phase mong đợi | `@DependsOn` ưu tiên hoặc phase thực tế khác | Log tên bean/phase/dependencies |
| Spring shutdown treo khoảng 10s/20s/30s | Callback không được gọi hoặc phase timeout | Log `stop(Runnable)` completion và config |
| Resource bị đóng trước worker | Phase/dependency/early shutdown flag sai | Đổi phase, kiểm tra executor lifecycle |
| Executor từ chối task khi listener còn chạy | Ingress chưa dừng, pool đã shutdown sớm | Dừng producer trước, drain rồi mới executor |
| `@Scheduled` vẫn phát việc mới | Scheduler chưa dừng hoặc callback đã in-flight | Cancel future, gate, tracking |
| `@Async` job còn chạy sau context close | Task đã được handoff; không có completion tracking | Drain future/queue, timeout và durable state |
| Không thấy `stop()` nhưng có `stop(Runnable)` | Processor gọi variant callback | Đặt logic trong variant đúng |
| Callback được gọi nhưng DB vẫn có ghi muộn | Callback phát trước actual completion | Đợi task thực, fencing ở commit |
| Cùng phase nhưng dừng không theo khai báo | Thứ tự trong cùng phase không được bảo đảm | Tách phase hoặc `depends-on` |
| Một bean đã stop mà `isRunning=true` | State transition sai hoặc thiếu visibility | Kiểm tra atomic/locking/flag |
| Quên gọi callback, shutdown rất chậm | `DefaultLifecycleProcessor` chờ timeout | Always signal after terminal stop |
| Khi SIGKILL không có cleanup | SIGKILL không chạy Java shutdown hooks | Durable jobs và idempotency |
| Pod kill trước khi drain xong | Kubernetes grace nhỏ hơn tổng shutdown | Đo, tune budget, reduce drain time |
| Callback lỗi vì executor đã đóng | Stop async worker phụ thuộc vào executor sai phase | Giữ drainer/executor sống tới callback |
| Chạy hai pod thì job bị xử lý trùng | Mỗi pod có vòng đời/job listener riêng | Queue semantics, idempotency, lease/fencing |

## 18. Những thiết kế sai thường gặp

### 18.1. Chỉ override stop(), không quan tâm stop(Runnable)

Khi processor gọi variant callback, default implementation `SmartLifecycle.stop(Runnable)` có thể delegate sang `stop()` rồi callback; nhưng nếu bạn override variant một cách sai hoặc không phối hợp đúng asynchronous stop, có thể mất ý nghĩa hoàn tất. Viết cả hai method có hợp đồng rõ ràng và test.

### 18.2. Gọi callback ngay sau khi submit shutdown

`executor.execute(drain); callback.run();` là **báo hoàn tất giả**. Spring có thể dừng DB pool khi drain còn chạy. Callback phải được gọi sau khi thực sự chốt trạng thái terminal.

### 18.3. Dùng cùng phase cho mọi thứ

Khi tất cả cùng phase cao, bạn mất khả năng thể hiện dependency theo lớp. Đặc biệt `SmartLifecycle.DEFAULT_PHASE` là `Integer.MAX_VALUE`, không phải một giá trị trung lập.

### 18.4. Tự đóng executor trước khi worker stop

Một bean chủ động gọi `executor.shutdown()` trong giai đoạn nhận SIGTERM, nhưng worker cần executor để drain. Nếu không có admission/race handling, task còn lại có thể bị reject và callback không chạy. Điều phối một ownership plan duy nhất.

### 18.5. Dựa vào @PreDestroy để ngăn việc mới

`@PreDestroy` không phải cổng admission sớm. HTTP requests, broker listeners hoặc scheduled tasks có thể đã chuyển việc cho worker trước khi destroy. Dừng producers ở phase cao và đóng admission gate đúng lúc.

### 18.6. Tăng timeout mãi mà không sửa logic

Nếu shutdown kẹt vì callback thiếu hoặc worker không bao giờ rời blocking I/O, tăng `timeout-per-shutdown-phase` chỉ kéo dài sự chờ. Cần bounded I/O, cancellation policy, metric và fallback bền vững.

### 18.7. Ngầm hiểu context.close() là giao dịch phân tán

Spring lifecycle processor biết các beans trong context, không biết tất cả dịch vụ bên ngoài đã commit/ack. Nếu worker gửi email/payment trước khi shutdown, tình trạng external effect có thể là `UNKNOWN`. Idempotency và reconciliation mới xử lý được điều đó.

## 19. Bài tập thực hành

### Bài 1 — Gán phase cho một pipeline

Hệ thống có HTTP listener, scheduler, worker pool và database. Chọn phase cho bốn lớp để bảo đảm shutdown từ ngoài vào trong; kiểm tra default phase của executor Spring thực tế rồi cập nhật mô hình.

### Bài 2 — Chạy ví dụ thứ tự

Chạy `PhaseOrderingDemo`, ghi nhận thứ tự start và stop. Thử đổi phase worker từ 0 thành 150 và dự đoán thứ tự mới. Không dựa vào vị trí `@Bean` methods.

### Bài 3 — Đợi stop callback

Chạy `AsyncStopCallbackDemo`. Xác nhận resource vẫn running trong lúc worker chưa được release; sau release, callback hoàn tất và resource dừng. Thử bỏ callback trong môi trường test với timeout ngắn; phân tích kết quả.

### Bài 4 — Cùng phase và depends-on

Tạo hai `SmartLifecycle` beans cùng phase, test nhiều lần và xác minh không nên dùng registration order làm invariant. Sau đó thêm explicit `@DependsOn` và xem thứ tự dependency.

### Bài 5 — Shutdown executor đang có in-flight jobs

Tạo `ThreadPoolTaskExecutor` với pool/queue hữu hạn. Trong pha drain, đợi jobs đã chấp nhận, từ chối job mới. Chứng minh không có deadlock và shutdown budget đủ cho testcase. Thử thay `setStrictEarlyShutdown` để hiểu semantics trong version bạn dùng.

### Bài 6 — Kubernetes SIGTERM

Chạy Spring Boot app trong container; bật graceful shutdown, cấu hình `spring.lifecycle.timeout-per-shutdown-phase`. Gửi SIGTERM giữa một request/async job, đo các mốc stop. Tăng độ trễ downstream để kiểm chứng thời gian grace và fallback.

### Bài 7 — External side effect

Một worker gửi message và chờ ack. Hãy thiết kế state `COMMITTED`, `ACKED`, `RETRYABLE`, `UNKNOWN`. Giải thích tại sao `SmartLifecycle.stop(Runnable)` không tự biến external operation thành exactly-once, và cách dùng operation ID/idempotency.

## 20. Checklist trước khi production

- [ ] Tôi biết `SmartLifecycle` mở rộng `Lifecycle` và `Phased`.
- [ ] Tôi nêu đúng startup **phase tăng dần**, shutdown **phase giảm dần**.
- [ ] Tôi biết `SmartLifecycle.DEFAULT_PHASE = Integer.MAX_VALUE` và `Lifecycle` thường phase 0.
- [ ] Tôi không dựa vào thứ tự beans có cùng phase.
- [ ] Tôi hiểu `@DependsOn` có thể ghi đè phase ordering.
- [ ] Tôi không đặt custom worker phase mà bỏ qua phase của actual executor/scheduler/web server.
- [ ] Tôi biết `isAutoStartup()` mặc định true và ý nghĩa với context refresh.
- [ ] Tôi triển khai `stop(Runnable)` và gọi callback sau khi dừng thật.
- [ ] Tôi không nhầm “đã yêu cầu dừng” với “đã dừng xong”.
- [ ] Tôi có timeout cho drain/I/O và trạng thái khi không thể hoàn tất.
- [ ] Tôi hiểu per-phase timeout là giới hạn chờ, không phải lệnh kill thread.
- [ ] Tôi biết `context.stop()` khác `context.close()` và `@PreDestroy`.
- [ ] Tôi phối hợp dừng ingress/scheduler trước worker, rồi mới đóng resources.
- [ ] Tôi hiểu `@Scheduled`/`@Async` task có thể đã được handoff ngoài scheduled invocation.
- [ ] Tôi có idempotency/retry/durable job state cho tác vụ phải sống qua restart.
- [ ] Tôi test thứ tự start/stop, callback, timeout, rejected task, shutdown giữa job.
- [ ] Tôi đo SIGTERM và budget Kubernetes termination grace period thật.
- [ ] Tôi ghi log phase, callback timing, số in-flight và số task chưa hoàn tất.
- [ ] Tôi không dựa vào một local lifecycle contract để đảm bảo distributed exactly-once.

**Kết luận:** `SmartLifecycle` cung cấp công cụ chuẩn để điều phối startup/shutdown trong Spring. **Phase thấp start trước và stop sau; phase cao start sau và stop trước**, nhưng explicit dependencies được ưu tiên. Khi dừng bất đồng bộ, `stop(Runnable)` phải báo callback **sau khi component thật sự ngừng và drain theo hợp đồng**, để Spring có thể giữ các tài nguyên ở phase thấp cho tới khi công việc cần chúng kết thúc. Để shutdown an toàn trong production, kết hợp phase ordering với admission gate, in-flight tracking, timeout có giới hạn, executor lifecycle, graceful web shutdown, Kubernetes budget và idempotency/durable state khi cần.

## 21. Tài liệu tham khảo chính thức

- Spring Framework 6.2 — [SmartLifecycle Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/context/SmartLifecycle.html): `isAutoStartup`, `getPhase`, `stop(Runnable)` và thứ tự các phases.
- Spring Framework 6.2 — [DefaultLifecycleProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/context/support/DefaultLifecycleProcessor.html): phase groups, callbacks, timeout per phase và các cách cấu hình.
- Spring Framework — [Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html): `Lifecycle`, `SmartLifecycle`, startup/shutdown, dependency ordering, `@PreDestroy`.
- Spring Framework 6.2 — [ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): phase và executor shutdown/termination flags.
- Spring Framework 6.2 — [ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): scheduler lifecycle và shutdown.
- Spring Boot 3.3 — [Graceful Shutdown](https://docs.spring.io/spring-boot/3.3/reference/web/graceful-shutdown.html): `server.shutdown=graceful` và `spring.lifecycle.timeout-per-shutdown-phase`.
- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Scheduled`, `@Async` và completion semantics.
- Java SE 17 — [CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): test thứ tự shutdown không dựa vào `Thread.sleep`.

Các ví dụ có tên file, POM, lệnh Maven và stdout là **chương trình minh họa chạy độc lập trên Java 17+ với Spring Framework 6.2.14**. Những đoạn ghi là **fragment** không phải một file `javac` độc lập; chúng cần beans/imports/domain logic và cấu hình Spring thực tế trước khi sử dụng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-shutdown-004">waitForTasksToCompleteOnShutdown và thời gian chờ kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# `waitForTasksToCompleteOnShutdown` và thời gian chờ kết thúc trong Spring

**Module:** Spring Framework - concurrency  
**Nhóm kiến thức:** Vòng đời và dừng  
**Đề bài:** waitForTasksToCompleteOnShutdown và thời gian chờ kết thúc.

## 1. Vì sao shutdown là một bài toán concurrency?

Trong một Spring service, khi có yêu cầu dừng ứng dụng (deploy mới, SIGTERM, đóng `ApplicationContext`), vẫn có thể còn các `@Async` tasks, scheduled callbacks hoặc background jobs. Một task đang ghi database, task khác đang chờ trong queue; một bean `DataSource` có thể bị hủy trước khi background task sử dụng nó xong.

Các câu hỏi phải được trả lời **riêng rẽ**:

1. **Task đã được nhận có được hoàn thành không?** Bao gồm cả task đang chạy và task đã vào queue.
2. **Executor sẽ ngừng nhận công việc mới từ thời điểm nào?** Ngay khi context bắt đầu close, sau một giai đoạn coordinated lifecycle stop, hay muộn tới bước destroy?
3. **Spring sẽ chờ executor tối đa bao lâu trước khi tiếp tục hủy những bean khác?**
4. **Nếu chờ hết hạn mà task chưa xong thì sao?** Task có bị interrupt không, vẫn chạy hay đã mất?
5. **Khi process bị kill hoặc restart, job có còn tồn tại không?**

Hai thiết lập gắn chặt với bài học:

```java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(30);
```

Đây là **hai dòng cấu hình minh họa** trên `ThreadPoolTaskExecutor` hoặc `ThreadPoolTaskScheduler`, thuộc lớp cha Spring `ExecutorConfigurationSupport`.

**Định nghĩa ngắn gọn:**

- `waitForTasksToCompleteOnShutdown`: chọn **phương thức shutdown** nhằm cho các tasks đã nhận được hoàn tất thay vì chủ động interrupt và loại bỏ queue ở bước shutdown cuối.
- `awaitTerminationSeconds`: cho thread thực hiện shutdown **chờ tối đa N giây** để executor terminated trước khi container tiếp tục shutdown các tài nguyên phụ thuộc.

Một cờ chọn **chính sách xử lý**, một cờ chọn **ngân sách thời gian chờ**. Chúng không thay thế nhau.

## 2. Timeline dừng executor

Giả sử một `ThreadPoolTaskExecutor` có một worker, task A đang chạy, task B đã nằm trong queue:

```text
HTTP thread                         Worker             Work queue
    |                                  |                   |
    +-- submit(A) -------------------->[A đang chạy]        |
    +-- submit(B) --------------------------------------->[B đợi]
    |
    | context bắt đầu close
    v

waitForTasks...=true:
    executor orderly shutdown:
    - không chủ ý interrupt A
    - B đã nhận vẫn được chạy khi worker rảnh
    - task submit sau khi executor shutdown bị reject

waitForTasks...=false:
    ở final shutdown, có thể dùng immediate shutdown:
    - cố interrupt A
    - B có thể bị lấy khỏi queue, không chạy
    - trong Spring 6.1+ còn có coordinated lifecycle stop trước final shutdown

awaitTerminationSeconds=30:
    - nếu được cấu hình, thread đang shutdown chờ tối đa 30s
    - chờ không đồng nghĩa tự interrupt, tự kill hoặc tự retry
```

**Không kết luận** từ một dòng `waitForTasks...=false` rằng Spring chắc chắn interrupt mọi task *ngay khoảnh khắc nhận SIGTERM*. `ApplicationContext` có các giai đoạn `ContextClosedEvent`, coordinated `SmartLifecycle.stop` và bean destruction; chính sách tại **final shutdown** cần được đặt trong bối cảnh đó.

## 3. Hiểu gốc JDK: `shutdown()`, `shutdownNow()` và `awaitTermination()`

`ThreadPoolTaskExecutor` của Spring bọc `ThreadPoolExecutor` JDK. Ba method JDK là nền tảng:

| JDK method | Ý nghĩa | Có chờ tasks kết thúc trước khi trả về? |
| --- | --- | --- |
| `ExecutorService.shutdown()` | Ngừng nhận task mới, cho các task đã nhận (kể cả queue) chạy hết | Không |
| `ExecutorService.shutdownNow()` | Cố interrupt task đang chạy; loại các task chưa bắt đầu khỏi queue | Không |
| `ExecutorService.awaitTermination(timeout, unit)` | Đợi terminated sau shutdown, hoặc đến timeout/interrupt | Có, nhưng chỉ tối đa timeout |

Ví dụ minh họa chỉ dùng Java:

```java
ExecutorService pool = Executors.newFixedThreadPool(2);
pool.submit(() -> processOrder());

pool.shutdown();  // Orderly, không nhận task mới.
boolean terminated = pool.awaitTermination(30, TimeUnit.SECONDS);
```

Cần định nghĩa `processOrder` và import các class JDK. Nếu `terminated == false`, executor vẫn chưa terminated sau khoảng đợi; **không** có nghĩa task đã được hủy tự động. Nếu task bị interrupt, nó chỉ dừng khi code/driver thực sự phản hồi interrupt.

### `shutdownNow()` cũng không phải hard kill

Javadoc JDK gọi `shutdownNow()` là **best effort**. Một task bỏ qua `InterruptedException`, gọi native I/O không interruptible hoặc tiếp tục quay vòng CPU có thể không dừng. `shutdownNow()` trả về danh sách các tasks chưa bắt đầu; ứng dụng cần quyết định có ghi nhận/retry chúng không, thay vì mặc định chúng đã được xử lý.

`isShutdown() == true` chỉ có nghĩa đã khởi động shutdown. `isTerminated() == true` nghĩa executor không còn task hoạt động/chờ và đã chấm dứt sau shutdown. Hai trạng thái **khác nhau**.

## 4. `setWaitForTasksToCompleteOnShutdown(boolean)` thực sự làm gì?

Thuộc `org.springframework.scheduling.concurrent.ExecutorConfigurationSupport`:

```java
executor.setWaitForTasksToCompleteOnShutdown(true);
```

Khi **true**, bước shutdown của executor là kiểu **orderly**: tasks đang chạy không bị interrupt chỉ vì shutdown; tasks đã xếp queue được xử lý. Spring có thể tiếp tục những bước đóng context khác song song nếu không đặt await termination.

Khi **false** (giá trị mặc định của thuộc tính), ở **bước shutdown cuối**, executor có thể thực hiện `shutdownNow()` — cố interrupt tasks đang chạy và xóa queue. Nhưng trong Spring Framework 6.1+ còn có coordinated lifecycle stop trước đó, trừ các cấu hình làm bỏ qua giai đoạn này. Do đó phải kiểm tra **Spring version**, flags lifecycle và lời gọi có diễn ra trong container hay là gọi trực tiếp `destroy()`.

| Thuộc tính | Ongoing task tại final shutdown | Queued task tại final shutdown |
| --- | --- | --- |
| `false` | Có thể bị interrupt (best effort) | Có thể bị lấy khỏi queue, không thực thi |
| `true` | Không chủ động interrupt chỉ vì shutdown | Cho phép chạy dù đã ngừng nhận task mới |

**Giới hạn quan trọng:** `waitForTasksToCompleteOnShutdown(true)` **không tự buộc** `ApplicationContext.close()` phải block cho đến khi hết backlog. Nó chủ yếu chọn shutdown policy, và còn có ảnh hưởng tới cách tham gia giai đoạn coordinated stop.

Một task đã nhận có thể chạy sau bước `destroy` của executor được khởi phát; nếu những tài nguyên Spring mà task sử dụng đã đóng, task vẫn có thể thất bại.

## 5. `setAwaitTerminationSeconds` — đợi có giới hạn, không đổi chính sách

Hai cách biểu diễn khoảng đợi:

```java
executor.setAwaitTerminationSeconds(30);  // 30 giây.
// Hoặc, nếu muốn độ chính xác theo ms:
executor.setAwaitTerminationMillis(30_000);
```

Đây là **hai lựa chọn thay thế**. Chỉ cần dùng một trong một bean. Khi executor shutdown, Spring đợi tối đa khoảng thời gian chỉ định cho underlying pool terminated, trước khi bước shutdown/destroy của executor trả về để container tiếp tục hủy tài nguyên khác.

**Ba trường hợp dễ nhầm:**

**Chỉ đặt `awaitTerminationSeconds(30)` nhưng `waitForTasks...=false`:** khoảng chờ chỉ đợi worker kết thúc sau khi shutdown policy mặc định đã được áp dụng. Ở final shutdown, queue vẫn có thể bị xóa và running tasks có thể bị interrupt. Await **không** có phép biến `shutdownNow()` thành `shutdown()`.

**Chỉ đặt `waitForTasks...=true` nhưng không await:** tasks đã nhận được cho phép tiếp tục xử lý, nhưng Spring có thể đóng bean phụ thuộc song song. Nếu job còn cần `DataSource`, HTTP client, message producer hoặc bean khác, đây là một rủi ro.

**Đặt cả hai:** cho accepted tasks chạy hết **và** đợi tối đa N giây ở destruction. Nếu N quá nhỏ, phương thức shutdown có thể trả về khi tasks vẫn đang chạy. Khi process còn sống, chúng có thể tiếp tục; khi orchestrator kill process, những tác vụ chỉ lưu trong RAM mất theo.

### Ma trận hai thiết lập

| `waitForTasksToCompleteOnShutdown` | `awaitTerminationSeconds` | Hành vi cuối |
| --- | --- | --- |
| `false` | Không đặt / 0 | Immediate-style shutdown cuối, không block tại bước đó |
| `false` | 10 | Immediate-style shutdown, sau đó chờ tối đa 10s cho worker rời pool |
| `true` | Không đặt / 0 | Orderly shutdown, không block tại bước đó; dependencies có thể đóng song song |
| `true` | 30 | Orderly shutdown và đợi tối đa 30s; không có bảo đảm tất cả hoàn tất khi timeout |

**Lưu ý:** đây là cách đọc **bước shutdown cuối**. Giai đoạn `SmartLifecycle` trước destruction còn tác động đến kết quả shutdown toàn context.

### Ví dụ bean cấu hình trong Spring

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BackgroundConfig {

    @Bean("reportExecutor")
    public ThreadPoolTaskExecutor reportExecutor() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setThreadNamePrefix("report-");
        e.setCorePoolSize(2);
        e.setMaxPoolSize(4);
        e.setQueueCapacity(40);
        e.setWaitForTasksToCompleteOnShutdown(true);
        e.setAwaitTerminationSeconds(30);
        return e;
    }
}
```

Đây là một class config có đầy đủ imports, nhưng muốn chạy cần ứng dụng Spring context và các tasks sử dụng bean. Khi là Spring bean, framework quản lý initialize/destroy; **không cần gọi `initialize()` thủ công** trong method `@Bean`. Các giá trị core/max/queue chỉ để minh họa, không phải cấu hình tối ưu cho mọi tải.

## 6. Spring 6.1+: vòng đời của context có thêm chi tiết quan trọng

Trong context thật, thứ tự liên quan đến shutdown thường gồm:

```text
ContextClosedEvent
       |
       v
Coordinated stop các SmartLifecycle beans (theo phase)
       |
       v
Destruction step: gọi destroy()/shutdown() executor
       |
       v
Đóng các bean và resources còn lại
```

Các thành phần khác có thể có thứ tự phase và dependency riêng. `ThreadPoolTaskExecutor`, `ThreadPoolTaskScheduler` không luôn có cùng thời điểm ngừng nhận task mới.

Spring Framework 6.1+ cung cấp `setAcceptTasksAfterContextClose(boolean)`. Giá trị mặc định ở lớp cha là **false**, thường dẫn đến early soft shutdown và coordinated lifecycle stop. Tuy nhiên **`ThreadPoolTaskExecutor` từ Spring 6.1.4** có `setStrictEarlyShutdown(false)` mặc định, cho phép late tasks sau event đóng context theo mô hình lenient, đồng thời vẫn tham gia coordinated lifecycle stop.

Nếu đặt `setStrictEarlyShutdown(true)` trên `ThreadPoolTaskExecutor`, executor khởi động early shutdown signal lúc context close và từ chối task mới sớm hơn. Cờ này **không thay** `waitForTasks...` hay `awaitTermination...`.

Theo Javadoc, `waitForTasksToCompleteOnShutdown(true)` và `acceptTasksAfterContextClose(true)` có thể **bỏ qua coordinated lifecycle stop** thông thường để xử lý việc dừng tại bước destruction; hai cờ này cũng ảnh hưởng hiệu lực `strictEarlyShutdown`.

| Thành phần | Điều khiển | Không nên nhầm |
| --- | --- | --- |
| `setStrictEarlyShutdown(true)` | Lúc nào ngừng nhận task mới tại context close | Không tự bảo đảm accepted tasks xong |
| `setAcceptTasksAfterContextClose(true)` | Cho late submissions trong shutdown | Không phải cờ graceful backlog |
| `setWaitForTasksToCompleteOnShutdown(true)` | Policy orderly tại destruction | Không tự cấu hình await timeout |
| `setAwaitTerminationSeconds(30)` | Chặn trong actual shutdown tối đa 30s | Không phải lifecycle phase timeout |
| `spring.lifecycle.timeout-per-shutdown-phase` | Coordinated SmartLifecycle stop timeout | Không thay per-executor await |
| Kubernetes grace period | Thời gian process còn sống sau SIGTERM | Không bảo đảm mọi Java job hoàn tất |

**Bài học thực tế:** nếu cần dừng nhận job sớm và giữ các accepted tasks để drain, phải thiết kế thứ tự producers/consumers và test cấu hình Spring cụ thể, không bật tất cả flags rồi tin rằng chúng “cộng dồn” đơn giản.

## 7. Demo chạy được Java 17 + Spring Framework: 3 cách dừng

Ví dụ này dùng **Java 17+**, Maven và Spring Framework **6.2.16**. Không khởi tạo Spring context; ta gọi `initialize()` và `destroy()` trực tiếp để **cô lập chính sách shutdown**. Khi đem vào ứng dụng thật, để Spring quản lý lifecycle.

Ba kịch bản:

1. `wait=true`, await 30s: task A đang chạy, B nằm queue; cả hai được hoàn tất; late task bị từ chối.
2. `wait=false`, await 5s: task A bị interrupt; task B trong queue không chạy; thời gian await **không bảo vệ B**.
3. `wait=true`, await 50ms: `destroy()` trả về trong khi A vẫn đang bị giữ bằng latch; khi thả latch, A và B vẫn hoàn tất. Hết timeout chờ **không phải tự kill**.

### 7.1. Thư mục

```text
shutdown-demo/
├── pom.xml
└── src/main/java/com/example/shutdown/
    └── ShutdownModesDemo.java
```

### 7.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>shutdown-demo</artifactId>
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
                <version>3.5.0</version>
                <configuration>
                    <mainClass>com.example.shutdown.ShutdownModesDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 7.3. `src/main/java/com/example/shutdown/ShutdownModesDemo.java`

```java
package com.example.shutdown;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ShutdownModesDemo {

    static ThreadPoolTaskExecutor createPool(
            boolean graceful, long awaitMs) {

        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(3);
        pool.setThreadNamePrefix("shutdown-demo-");
        pool.setWaitForTasksToCompleteOnShutdown(graceful);
        pool.setAwaitTerminationMillis(awaitMs);
        pool.initialize(); // Ở demo không có Spring context.
        return pool;
    }

    static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new IllegalStateException(message);
        }
    }

    static boolean awaitShutdown(ThreadPoolExecutor pool, Duration limit) {
        long deadline = System.nanoTime() + limit.toNanos();

        while (!pool.isShutdown() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        return pool.isShutdown();
    }

    static void gracefulAndAwait() throws Exception {
        ThreadPoolTaskExecutor pool = createPool(true, 30_000);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch closerDone = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        Thread closer = null;
        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                    completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(started.await(5, TimeUnit.SECONDS),
                    "Task A not started");
            pool.execute(completed::incrementAndGet);

            closer = new Thread(() -> {
                try {
                    pool.destroy();
                } finally {
                    closerDone.countDown();
                }
            }, "closer");
            closer.start();

            assertTrue(awaitShutdown(pool.getThreadPoolExecutor(),
                    Duration.ofSeconds(5)), "Shutdown not observed");

            boolean waited = closerDone.getCount() == 1;
            boolean lateRejected = false;

            try {
                pool.execute(() -> {
                    throw new AssertionError("Should be rejected");
                });
            } catch (TaskRejectedException expected) {
                lateRejected = true;
            }

            release.countDown();
            closer.join(5_000);
            assertTrue(!closer.isAlive(), "Closer did not return");

            System.out.println("graceful.waited=" + waited);
            System.out.println("graceful.rejected=" + lateRejected);
            System.out.println("graceful.completed=" + completed.get());
            System.out.println("graceful.terminated=" +
                    pool.getThreadPoolExecutor().isTerminated());
        } finally {
            release.countDown();
            pool.getThreadPoolExecutor().shutdownNow();
            if (closer != null) {
                closer.join(5_000);
            }
        }
    }

    static void immediateWithAwait() throws Exception {
        ThreadPoolTaskExecutor pool = createPool(false, 5_000);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean queuedRan = new AtomicBoolean();

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS),
                    "Task A not started");

            pool.execute(() -> queuedRan.set(true));
            pool.destroy(); // Trực tiếp: minh họa final shutdown.

            System.out.println("immediate.interrupted=" +
                    interrupted.get());
            System.out.println("immediate.queuedRan=" +
                    queuedRan.get());
            System.out.println("immediate.terminated=" +
                    pool.getThreadPoolExecutor().isTerminated());
        } finally {
            release.countDown();
            pool.getThreadPoolExecutor().shutdownNow();
        }
    }

    static void timeoutDoesNotKill() throws Exception {
        ThreadPoolTaskExecutor pool = createPool(true, 50);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();

        try {
            pool.execute(() -> {
                started.countDown();
                try {
                    release.await();
                    completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS),
                    "Task A not started");
            pool.execute(completed::incrementAndGet);

            // Task A vẫn đợi release khi destroy được gọi.
            pool.destroy();

            boolean activeAfterAwait =
                    !pool.getThreadPoolExecutor().isTerminated();

            release.countDown();

            boolean eventuallyTerminated =
                    pool.getThreadPoolExecutor()
                            .awaitTermination(5, TimeUnit.SECONDS);

            System.out.println("timeout.activeAfterAwait=" +
                    activeAfterAwait);
            System.out.println("timeout.completed=" + completed.get());
            System.out.println("timeout.terminatedLater=" +
                    eventuallyTerminated);
        } finally {
            release.countDown();
            pool.getThreadPoolExecutor().shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        gracefulAndAwait();
        immediateWithAwait();
        timeoutDoesNotKill();
    }
}
```

### 7.4. Chạy và kiểm tra output

Từ thư mục có `pom.xml`:

```bash
mvn -q compile exec:java
```

**Các dòng `System.out` khi chương trình hoàn tất bình thường:**

```text
graceful.waited=true
graceful.rejected=true
graceful.completed=2
graceful.terminated=true
immediate.interrupted=true
immediate.queuedRan=false
immediate.terminated=true
timeout.activeAfterAwait=true
timeout.completed=2
timeout.terminatedLater=true
```

Thư viện Spring có thể phát một log cảnh báo await termination timeout ở kịch bản cuối; Maven có thể có output của build tool. Những dòng đó không phải phần stdout nghiệp vụ cần so sánh.

**Giải thích test:**

- Pool chỉ có **1 worker**; task A được giữ ở `CountDownLatch`, B xếp queue.
- Trong kịch bản `graceful`, thread khác gọi `destroy()`, main quan sát `isShutdown` rồi mới thả A. Vì `awaitTerminationMillis(30000)` đang đợi A/B hoàn tất, `graceful.waited=true`. Sau khi thả A, B cũng chạy và pool terminated.
- `graceful.rejected=true` xác nhận late task bị từ chối sau khi underlying executor đã shutdown.
- Với `wait=false`, gọi trực tiếp `destroy()` khiến pool cố interrupt task A, rút task B khỏi queue. **Dù có await 5 giây, B vẫn không chạy**.
- Với `wait=true` nhưng await 50ms, `destroy()` trả về khi A vẫn chờ; sau khi main thả latch, A/B tiếp tục hoàn tất và pool terminated. **Timeout không phải kill**.
- `Thread.sleep` không được dùng để đoán kết quả. Timeout 5 giây chỉ là safety net để test báo lỗi khi hệ thống treo.

**Giới hạn:** demo gọi `initialize/destroy` ngoài context nên không minh họa toàn bộ `ContextClosedEvent`/`SmartLifecycle`. Đừng lấy kết quả immediate làm cam kết một ứng dụng Spring Boot thật sẽ interrupt ngay khi SIGTERM đến.

## 8. Cấu hình trong Spring Boot 3.5: property và custom bean

Spring Boot có cấu hình cho **auto-configured** executor và scheduler:

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 100
      shutdown:
        await-termination: true
        await-termination-period: 30s
    scheduling:
      pool:
        size: 2
      shutdown:
        await-termination: true
        await-termination-period: 30s
  lifecycle:
    timeout-per-shutdown-phase: 20s

server:
  shutdown: graceful
```

Đường property đầy đủ để tra cứu:

```text
spring.task.execution.shutdown.await-termination
spring.task.execution.shutdown.await-termination-period
spring.task.scheduling.shutdown.await-termination
spring.task.scheduling.shutdown.await-termination-period
spring.lifecycle.timeout-per-shutdown-phase
```

Trong Spring Boot 3.5, `server.shutdown=graceful` là lựa chọn rõ ràng cho graceful web server shutdown; web server và background executor là **hai phạm vi khác nhau**. `spring.lifecycle.timeout-per-shutdown-phase` điều khiển thời gian dừng coordinated `SmartLifecycle` phase, không trực tiếp thay `setAwaitTerminationSeconds` của từng executor.

Các property `spring.task.execution.shutdown.*`/`spring.task.scheduling.shutdown.*` áp dụng theo **auto-configuration** Boot. Khi bạn tự tạo bean `ThreadPoolTaskExecutor`, cần cấu hình setter trên bean đó (hoặc dùng builder phù hợp); đừng mặc định property chung đã đi vào bean tùy biến.

Nếu bật **virtual threads** trên Java 21+, Boot có thể dùng implementation `SimpleAsyncTaskExecutor`/`SimpleAsyncTaskScheduler` thay vì pool truyền thống; phải kiểm tra bean thực tế và lifecycle semantics. Bài học này tập trung vào executor/scheduler kế thừa `ExecutorConfigurationSupport`, không quy mọi executor về cùng một API.

### `@Async` thực thi qua executor nào?

```java
@Service
public class ExportService {

    @Async("reportExecutor")
    public CompletableFuture<String> export(String id) {
        return CompletableFuture.completedFuture(
                performExport(id));
    }
}
```

Đây là **class minh họa**, cần import Spring annotations, `CompletableFuture`, method `performExport` và bật `@EnableAsync`; caller phải đi qua Spring proxy thay vì `this.export(...)`. `completedFuture` là Future tạm chứa kết quả của method đang chạy trong worker, không tạo task thứ hai.

`@Transactional` ở thread caller không tự lan sang `@Async` worker; muốn commit ổn định trước shutdown phải thiết kế transaction boundary, idempotency và data persistence riêng. Executor wait không tự commit transaction hộ job.

## 9. `ThreadPoolTaskScheduler`: delayed job và periodic job khác queue thông thường

`ThreadPoolTaskScheduler` cũng có các setter chung nhưng sở hữu một `ScheduledThreadPoolExecutor`, nên cần xét hai policy bổ sung:

```java
ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setPoolSize(2);
scheduler.setWaitForTasksToCompleteOnShutdown(true);
scheduler.setAwaitTerminationSeconds(20);

scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
```

Đây là **đoạn minh họa**, cần Spring bean/lifecycle hoặc initialize thủ công.

Theo Spring Framework 6.2:

- `setContinueExistingPeriodicTasksAfterShutdownPolicy(false)` mặc định **false**: các repeating schedules không tiếp tục kích hoạt mãi sau shutdown.
- `setExecuteExistingDelayedTasksAfterShutdownPolicy(true)` mặc định **true**: các one-shot delayed tasks đang chờ có thể được thực thi sau orderly shutdown.
- Nếu đặt execute-existing-delayed thành `false`, tác vụ hẹn ngày mai có thể **bị hủy thay vì chạy khi dừng**. Đây là chính sách, không phải mặc định an toàn cho mọi nghiệp vụ.
- Với wait=true và delayed one-shot hẹn rất xa, await 20s có thể hết mà scheduler chưa terminated.
- Một periodic schedule về bản chất có rất nhiều mốc trong tương lai; đừng yêu cầu “drain hết vô hạn periodic tasks” mà hãy ngừng phát mốc mới, cancel handle và xử lý in-flight work.

Với những lịch phải sống qua restart, dùng durable job store/queue; local `TaskScheduler` không tự giữ lịch trong database.

## 10. Giữ dependency sống khi background task còn chạy

Một task có thể cần `DataSource`, transaction manager, HTTP client, S3 SDK, message producer hoặc một bean đang được Spring quản lý. Nếu chọn wait=true nhưng không await, context có thể tiếp tục hủy các resource đó trong lúc task đang sử dụng.

```text
Job đang chạy:
  đọc DB -> gọi đối tác -> ghi kết quả

Shutdown không await:
  executor bắt đầu orderly shutdown
  Spring tiếp tục hủy DataSource
  job cố ghi DB -> có thể lỗi connection closed

Shutdown có await:
  executor shutdown orderly, đợi trong giới hạn timeout
  job còn cơ hội hoàn tất trước khi destruction tiếp diễn
```

Đây là lý do quan trọng để cân nhắc `setAwaitTerminationSeconds` bên cạnh `waitForTasks...`. Nhưng await **không đảm bảo** tất cả resource lifetime cho một ứng dụng có SmartLifecycle và shutdown hooks phức tạp; phải kiểm tra thứ tự dừng thực tế.

**Đối với task quan trọng,** durable accept mới là hợp đồng mạnh. Trả HTTP `202 Accepted` ngay sau `executor.execute(...)` chỉ chứng minh task đã vào queue trong RAM; nếu JVM crash, nó có thể mất. Ghi job record vào DB/queue trước khi trả Accepted, rồi dùng worker idempotent/retry khi cần bảo đảm recovery.

## 11. Tính ngân sách thời gian shutdown

Một ước lượng thô cho backlog độc lập:

```text
N: số tasks đã nhận
W: số workers có thể thực sự chạy song song
T: duration tối đa của một task khi đã có deadline I/O

waves       ≈ ceil(N / W)
drain_time  ≈ waves × T
```

Ví dụ 24 tasks với 4 workers, mỗi task khoảng 2 giây: 6 waves, lý tưởng khoảng **12 giây**, chưa kể queue contention, DB lock, retry và overhead. Nếu task không có timeout thật, `T` không hữu hạn — không thể chọn await hợp lý chỉ từ “task thường chạy nhanh”.

### Cân đối ba tầng timeout

| Cửa sổ | Phạm vi |
| --- | --- |
| `setAwaitTerminationSeconds(30)` | Một executor chờ trong destruction step |
| `spring.lifecycle.timeout-per-shutdown-phase=20s` | Một phase coordinated lifecycle stop |
| Kubernetes `terminationGracePeriodSeconds: 60` | Ngân sách ngoài tiến trình từ termination tới forcible kill, còn phụ thuộc preStop và cơ chế triển khai |

**Không nên cấu hình await 120 giây trong pod chỉ được 15 giây grace** rồi tin rằng JVM sẽ có đủ 120 giây để dọn. Timeout của shutdown toàn hệ thống có thể là tổng/hợp thành của nhiều bước; phải đo từ SIGTERM đến process exit thực tế.

Quy trình vận hành tốt thường:

1. Đánh dấu instance **không sẵn sàng**, ngừng nhận request/job mới.
2. Dừng timer/consumer tạo thêm task mới.
3. Đợi in-flight work theo deadline có giới hạn.
4. Ack/commit những job thành công; cho durable queue redeliver job chưa hoàn tất.
5. Đóng executor, database và client theo thứ tự kiểm thử được.
6. Kết thúc process trước grace period bên ngoài.

**Không thể dùng `waitForTasks...` thay thế durable queue, transactional outbox, idempotency hoặc business checkpoint.**

## 12. Interrupt, retry và tính đúng đắn dữ liệu

Task phải xử lý interrupt có chủ đích:

```java
public void run() {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            doOneUnitOfWork();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        recordInterrupted();
    } finally {
        cleanupResources();
    }
}
```

Đây là **đoạn method minh họa**; `doOneUnitOfWork`, `recordInterrupted`, `cleanupResources` thuộc ứng dụng và `doOneUnitOfWork` phải là method có thể ném `InterruptedException`. Thực tế còn cần chính sách có được retry, ack, checkpoint, rollback hay không. Không tự bắt exception và tiếp tục vô hạn vì điều đó làm `shutdownNow` không có hiệu lực.

`Future.get(timeout)` ở caller **không tự hủy** task nếu quá hạn. Cần gọi cancel khi phù hợp và đặt timeout ngay tại JDBC/HTTP client. Một task bị interrupt sau khi thực hiện side effect nhưng trước khi ghi trạng thái `SUCCEEDED` có thể được retry và tạo duplicate nếu không có idempotency key.

### Những task không thể “drain nhanh”

- Đợi row lock DB nhiều phút không có `queryTimeout`.
- HTTP request chờ vô hạn vì không đặt socket/read timeout.
- Tác vụ CPU vòng lặp không kiểm tra interrupt.
- Deadlock hoặc external service không phản hồi.
- Queue chứa 100.000 jobs trong khi chỉ có 2 workers.

Với những hệ thống này, đặt await termination 30 giây **không sửa được sự cố**. Cần timeout, backpressure, graceful cancellation, persisted job status và thiết kế recovery.

## 13. Test đúng bằng latches, không suy luận theo sleep

Checklist kiểm thử:

| Case | Thiết lập | Điều cần khẳng định |
| --- | --- | --- |
| Graceful backlog | A đang chạy, B queue, wait=true | A/B xong; late submit bị reject sau shutdown |
| Immediate shutdown | A đang chờ interruptible, B queue, wait=false | A thấy interrupt; B không chạy |
| Await timeout | wait=true, A giữ latch quá thời gian chờ | destroy trả về nhưng executor chưa terminated |
| Không await | wait=true | Context có thể tiếp tục destruction trước khi task xong |
| Task bỏ qua interrupt | wait=false, worker không cooperate | Await có thể hết timeout trước khi terminated |
| `ContextClosedEvent` | Context thật, flag strict/accept tùy cấu hình | Kiểm tra thời điểm ngừng nhận task |
| DB dependency | Task cần DataSource khi dừng | Không bị đóng connection pool trước khi job hoàn tất |
| Scheduler | Delayed one-shot, cron, periodic | Chính sách giữ/hủy delayed tasks đúng |
| Quá tải | Queue và workers bận | Rejection/cancellation không silently lose tasks |
| SIGTERM/Kubernetes | Deploy trên môi trường tương tự production | Grace period đủ, job durable có recovery |

Đừng dùng `Thread.sleep(100)` như kiểm chứng rằng job chắc chắn đã chạy trước/ sau shutdown: CI và OS scheduling có thể khác. `CountDownLatch`, trạng thái executor và `Future` cho kết quả quan sát có kiểm soát hơn.

Theo dõi production metrics: active count, pool size, queue size, accepted/started/completed/failed/rejected/cancelled tasks, shutdown duration, timed-out waits, DB active/pending connections và số job được retry/redeliver sau deploy.

## 14. Những lỗi rất thường gặp

| Sai lầm | Hậu quả | Khuyến nghị |
| --- | --- | --- |
| Bật wait=true rồi nghĩ executor.destroy sẽ chặn đến hết | Không có await, destruction có thể đi tiếp | Đặt await hữu hạn nếu cần |
| Chỉ đặt await 30s rồi tưởng tasks queue được drain | wait=false vẫn có thể drop queue ở final shutdown | Chọn shutdown policy tường minh |
| Coi await timeout là thời hạn của mọi JDBC call | Job có thể tiếp tục sau timeout | Đặt I/O client deadline |
| Coi timeout là hard kill | Worker có thể chưa terminate | Đo `isTerminated` và job status |
| Chờ vô hạn vì “không mất job” | Orchestrator có thể SIGKILL | Deadline + durable recovery |
| Đóng DataSource trước khi worker dừng | Job lỗi dù không bị interrupt | Kiểm thử destruction order và await |
| Gọi `destroy()` từ code request | Có thể phá bean vẫn được dùng | Để Spring lifecycle điều phối |
| Quên coordinated lifecycle stop Spring 6.1+ | Kết luận sai về lúc interrupt | Phân biệt context close, stop, destruction |
| Nhầm `strictEarlyShutdown` với wait=true | Early rejection không nói backlog sẽ xong | Đặt flags theo đúng vai trò |
| Không xét scheduled delayed jobs | Scheduler có thể tồn tại rất lâu hoặc bỏ job | Chọn delayed/periodic policy |
| Coi property Boot tự áp cho custom @Bean | Bean không được cấu hình như mong muốn | Setters trực tiếp/builder và kiểm tra |
| Bật server graceful shutdown nhưng bỏ executor | HTTP request xong, background task còn | Quản lý riêng từng lớp |
| 202 Accepted khi job chỉ trong RAM | Crash làm mất job | Durable job acceptance |
| Nuốt `InterruptedException` rồi loop mãi | Shutdown không thể kết thúc | Restore interrupt/cleanup |
| Coi queue empty = mọi job đã commit | Có thể đang chạy hoặc thất bại | Job-state/durable tracking |
| Đặt thời gian chờ hơn container grace | Process bị kill giữa backlog | Cân đối ngân sách shutdown |

## 15. Câu hỏi tự kiểm tra

**1.** `waitForTasksToCompleteOnShutdown(true)` có tự block `ApplicationContext.close` đến khi queue rỗng không?  
**Đáp:** Không tự động. Nó chọn orderly shutdown. Dùng await termination để chặn trong giới hạn thời gian.

**2.** `setAwaitTerminationSeconds(30)` có làm `shutdownNow()` chuyển thành `shutdown()` không?  
**Đáp:** Không. Nó là khoảng đợi, không phải policy.

**3.** `isShutdown()==true` có chắc `isTerminated()==true` không?  
**Đáp:** Không; ongoing task vẫn có thể chạy.

**4.** Await hết thời gian có tự ngắt tasks đang chạy không?  
**Đáp:** Không mặc định. Worker có thể tiếp tục khi JVM còn sống.

**5.** Vì sao immediate shutdown ở demo bỏ task B dù có await 5 giây?  
**Đáp:** Vì policy `wait=false` khiến direct `destroy` loại queue; await chỉ đợi các worker kết thúc.

**6.** Bật wait=true nhưng DataSource đóng trước worker có an toàn không?  
**Đáp:** Không. Task cần DB có thể lỗi. Kiểm soát destruction và await.

**7.** `setStrictEarlyShutdown(true)` có nghĩa “chờ xong mọi task” không?  
**Đáp:** Không. Nó quyết định early shutdown signal và từ chối new tasks.

**8.** `spring.lifecycle.timeout-per-shutdown-phase` có thay `setAwaitTerminationSeconds` không?  
**Đáp:** Không, chúng áp dụng cho hai giai đoạn khác nhau.

**9.** JDK `shutdownNow()` có bảo đảm task đang làm HTTP bị dừng ngay?  
**Đáp:** Không. Interrupt là best effort.

**10.** Với `ThreadPoolTaskScheduler` có delayed task hẹn 24 giờ sau, await 30 giây sẽ chắc terminated?  
**Đáp:** Không, phụ thuộc delayed-task shutdown policy và những task còn tồn tại.

**11.** Pod chỉ có 20 giây grace nhưng executor await 60 giây có bảo đảm xong không?  
**Đáp:** Không, container có thể cưỡng bức kết thúc process trước.

**12.** Task cần survive restart có nên chỉ nằm trong `ThreadPoolTaskExecutor` queue?  
**Đáp:** Không. Cần job store/queue bền vững và idempotency.

## 16. Tóm tắt và nguồn tham khảo

**Công thức tư duy:**

```text
waitForTasksToCompleteOnShutdown
    = CÁCH xử lý task đã nhận ở bước shutdown cuối.

awaitTerminationSeconds / awaitTerminationMillis
    = THỜI GIAN TỐI ĐA block chờ terminated.

ContextClosedEvent + SmartLifecycle + destruction
    = TRÌNH TỰ và thời điểm stop trong Spring context.

Orchestrator grace + durable queue
    = GIỚI HẠN THỜI GIAN BÊN NGOÀI và khả năng recovery.
```

Cặp cấu hình thường đáng cân nhắc cho background tasks ngắn cần dependency vẫn sống:

```java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(30);
```

Nhưng **không** có cấu hình local nào bảo đảm exactly-once, hoàn tất qua SIGKILL, hoặc khắc phục task bị deadlock. Từng tầng cần chính sách rõ: ngừng nhận việc, drain có giới hạn, timeout I/O, dọn resources, idempotency và durable recovery.

**Tài liệu chính thức:**

- [Spring Framework 6.2 — ExecutorConfigurationSupport](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html): `setWaitForTasksToCompleteOnShutdown`, `setAwaitTerminationSeconds`, context close.
- [Spring Framework 6.2 — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): strict early shutdown và pool configuration.
- [Spring Framework 6.2 — ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.15/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): periodic/delayed shutdown policy.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskExecutor`, `@Async`, `TaskScheduler`.
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): orderly vs immediate shutdown và `awaitTermination`.
- [Spring Boot 3.5 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html): executor/scheduler auto-configuration.
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/): shutdown settings.
- [Spring Boot 3.5 — Graceful Shutdown](https://docs.spring.io/spring-boot/3.5/reference/web/graceful-shutdown.html): grace của HTTP server và lifecycle phases.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-shutdown-005">awaitTermination không tạo bảo đảm hoàn tất vô hạn</a>

<details>
<summary>Xem chi tiết</summary>

# awaitTermination không tạo bảo đảm hoàn tất vô hạn

> **Chủ đề:** Spring Framework – concurrency  
> **Nhóm:** Vòng đời và dừng  
> **Phạm vi:** Java 17+ `ExecutorService`, `ThreadPoolExecutor`, `ScheduledThreadPoolExecutor`; Spring Framework 6.2.x `ThreadPoolTaskExecutor` và `ThreadPoolTaskScheduler`.  
> **Ý chính:** `awaitTermination(timeout, unit)` là một **phép chờ có giới hạn** sau yêu cầu shutdown. Nó không tự ra lệnh dừng executor, không bảo đảm mọi task hoàn tất thành công, không tự hủy task khi trả `false` và không thể cưỡng bức một tác vụ không hợp tác ngừng hoạt động. Spring `setAwaitTerminationSeconds(...)` cũng giới hạn **thời gian container chờ lúc shutdown**, không phải một hợp đồng “mọi việc sẽ hoàn tất trong N giây”.

Muốn shutdown có kiểm soát, cần tách **ngừng nhận việc mới**, **chờ tác vụ cũ**, **đề nghị hủy khi quá hạn**, **chờ phản ứng với interrupt**, **ghi nhận việc chưa hoàn thành** và **khôi phục từ trạng thái bền vững**. Bài hướng dẫn cách làm những bước đó, cùng ví dụ Java 17 và Spring Context có thể chạy, kết quả xác định nhờ `CountDownLatch`.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích chính xác `shutdown()`, `shutdownNow()`, `awaitTermination(...)`, `isShutdown()` và `isTerminated()`.
2. Phân biệt kết quả `true`, `false` và `InterruptedException` của `awaitTermination`.
3. Nhận ra rằng gọi `awaitTermination` **trước khi shutdown** không làm executor tự chuyển sang trạng thái terminated.
4. Chỉ ra vì sao “timeout 5 giây” không tạo bảo đảm hard deadline cho code task hay side effect bên ngoài.
5. Thiết kế quy trình **two-phase shutdown** có tổng ngân sách chờ, log/escalate rõ và preserve interrupt status.
6. Phân biệt trạng thái **pool đã kết thúc** với **các Future thành công**, transaction commit hoặc nghiệp vụ hoàn tất.
7. Dùng `Future.cancel(true)` và `shutdownNow()` đúng kỳ vọng: chỉ **yêu cầu hủy/interrupt hợp tác**.
8. Xử lý task bị block vì I/O, lock, CPU loop hoặc driver không phản ứng interrupt.
9. Cấu hình `ThreadPoolTaskExecutor`/`ThreadPoolTaskScheduler` do Spring quản lý và hiểu khác nhau giữa `setWaitForTasksToCompleteOnShutdown` và `setAwaitTerminationSeconds`.
10. Phân tích `@Async`, `@Scheduled`, queue, periodic jobs, bean destruction và container shutdown deadline.
11. Viết unit/integration tests với latch thay vì `Thread.sleep` để quan sát timeout, interrupt, cleanup và trạng thái thật.
12. Quyết định lúc nào cần durable queue/job store, idempotency, retry và cơ chế reconciliation sau khi process bị kill.

## 2. Phương thức awaitTermination cam kết điều gì?

Hợp đồng Java `ExecutorService`:

~~~java
boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;
~~~

Nó **chặn thread gọi** cho tới khi một trong ba điều kiện xảy ra:

1. Executor đã hoàn toàn terminated **sau một shutdown request**.
2. Đã hết khoảng thời gian chờ.
3. Thread gọi đang chờ bị interrupt.

Bảng diễn giải:

| Kết quả | Điều được xác nhận | Điều **không** được xác nhận |
| --- | --- | --- |
| `true` | Executor đã đạt trạng thái **terminated** trong/sau khoảng chờ | Mỗi task thành công về nghiệp vụ, tất cả external side effects đã commit, dữ liệu không trùng |
| `false` | Timeout đã qua trước khi executor đạt terminated | Executor đã bị hủy, tất cả jobs thất bại, công việc vẫn sẽ tồn tại mãi mãi |
| `InterruptedException` | Thread đang chờ bị yêu cầu interrupt | Worker chắc chắn đã bị ngắt, executor chắc chắn dừng, mọi transaction rollback |

`awaitTermination` **không phải** thao tác làm executor shutdown. Nếu chưa gọi `shutdown()` hoặc `shutdownNow()` (hoặc container/lifecycle chưa làm việc tương đương), một pool có thể hoàn toàn idle nhưng vẫn **chưa terminated**. Chờ thêm không tạo ra shutdown request.

### 2.1. “All tasks completed execution” không có nghĩa “all tasks succeeded”

Một task có thể kết thúc do:

- Return thành công.
- Ném exception được `Future` lưu lại.
- Bị cancel trước khi bắt đầu.
- Kết thúc sau khi bắt `InterruptedException`.
- Thực thi xong nhưng remote service đã lỗi hoặc transaction rollback.

Executor vẫn có thể **terminated** khi không còn task nào active/queued theo contract, bất kể các task từng có outcome gì. Muốn biết business success, phải đọc từng `Future`, job store hoặc trạng thái nghiệp vụ thích hợp.

### 2.2. Timeout chỉ ràng buộc việc chờ của caller

`awaitTermination(5, TimeUnit.SECONDS)` nói rằng caller chờ **tối đa một khoảng thời gian theo API**, không nói rằng task bị kill sau 5 giây. Khi trả `false`, thread worker có thể vẫn đang chạy, queue vẫn còn việc, database transaction vẫn mở hoặc HTTP call vẫn đang chờ. Hệ thống phải quyết định bước kế tiếp.

### 2.3. awaitTermination không phải Future.get

`Future.get(timeout, unit)` chờ **một Future** và có thể trả result hoặc ném `ExecutionException`, `TimeoutException`, `CancellationException`. `awaitTermination` chờ **toàn bộ executor** sau shutdown và trả boolean. Không thể dùng kết quả pool terminated để suy từng task đã thành công.

## 3. Vòng đời ExecutorService: năm trạng thái cần phân biệt

Một mô hình đơn giản:

~~~text
RUNNING --shutdown()---------> SHUTDOWN
  |                              |
  |                              | chạy nốt việc đã nhận
  |                              v
  |                          TERMINATING
  |                              |
  +--shutdownNow()---> STOP/INTERRUPT ATTEMPT
                                 |
                                 v
                             TERMINATED
~~~

Sơ đồ là **mô hình giải thích**, không phải các enum public của `ExecutorService`. `ThreadPoolExecutor` bên dưới có trạng thái nội bộ và cách chuyển phức tạp hơn. Điểm quan trọng là **đã được yêu cầu shutdown** khác **đã terminated**.

### 3.1. shutdown(): từ chối việc mới nhưng không đợi

~~~java
executor.shutdown();
~~~

Theo JDK, `shutdown()` bắt đầu orderly shutdown: không nhận task mới, nhưng các task đã nhận **được phép thực hiện** trước khi executor kết thúc. Phương thức này **không chờ** kết thúc.

Do đó:

~~~java
executor.shutdown();
System.out.println(executor.isShutdown());   // true
System.out.println(executor.isTerminated()); // Có thể false.
~~~

Đây là snippet; cần có `executor`. Giá trị đầu là true nếu shutdown request thành công; giá trị thứ hai phụ thuộc worker và queue. **Không** viết test hard-code `isTerminated()==true` ngay sau `shutdown()`.

### 3.2. shutdownNow(): nỗ lực dừng, không phải cưỡng chế dừng

~~~java
List<Runnable> neverStarted = executor.shutdownNow();
~~~

`shutdownNow()` cố ngắt các task active và không cho những task đang chờ bắt đầu; nó trả danh sách các `Runnable` đang chờ **chưa bắt đầu thực thi** theo contract. Với executor có wrapper như `FutureTask`, phần tử trả về có thể không phải business DTO gốc. Không suy rằng danh sách này đã được persist, retry hoặc cancel theo quy tắc nghiệp vụ.

Javadoc Java nói rõ chỉ có **best-effort attempts**; task bỏ qua interrupt có thể **không bao giờ kết thúc**, vì thế `shutdownNow()` cũng **không đảm bảo** `awaitTermination(...)` lần sau trả true.

### 3.3. isShutdown() và isTerminated() là hai câu hỏi khác nhau

- `isShutdown()==true`: pool đã nhận yêu cầu shutdown, không còn nhận task bình thường.
- `isTerminated()==true`: mọi tasks đã hoàn tất sau shutdown, pool ở trạng thái terminated.
- Có thể `isShutdown()==true` nhưng `isTerminated()==false` trong một thời gian dài.
- `isTerminated()` không trở thành true nếu chưa có shutdown, kể cả queue rỗng và không có task active.

### 3.4. Shutdown không nhận thêm việc: rejection là có chủ đích

Nếu sau `shutdown()` code khác vẫn gọi `submit`, executor thường từ chối với `RejectedExecutionException`. Trong Spring `ThreadPoolTaskExecutor`, có thể quan sát `TaskRejectedException` theo lớp adapter. Đây là một phần của lifecycle, không nhất thiết là lỗi bất ngờ; caller phải biết khi nào app bắt đầu dừng và không được tạo job mới.

## 4. Sai lầm căn bản: chỉ gọi awaitTermination mà chưa shutdown

Một ví dụ:

~~~java
ExecutorService pool =
        Executors.newSingleThreadExecutor();

try {
    boolean result = pool.awaitTermination(
            1, TimeUnit.SECONDS);
    System.out.println("terminated = " + result);
} finally {
    pool.shutdownNow();
}
~~~

Đây là **snippet Java 17**, cần imports `ExecutorService`, `Executors`, `TimeUnit`. Nếu pool chưa có shutdown request, `awaitTermination` hết timeout và trả `false` — dù pool đang idle. Điều này **không** chứng tỏ Java ExecutorService bị hỏng; đó là bạn đã đợi một trạng thái mà chưa yêu cầu.

### 4.1. Trình tự chuẩn

~~~java
pool.shutdown(); // 1. Ngừng nhận việc mới.

if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
    // 2. Hết thời gian chờ; phải ra quyết định.
}
~~~

Với `shutdown`, pool còn cơ hội hoàn thành các task đã được nhận. Với `awaitTermination`, caller dành một ngân sách chờ hữu hạn cho trạng thái cuối. Trình tự này chưa đủ cho production nếu tasks có thể treo; cần một kế hoạch escalation được nói rõ ở mục tiếp theo.

### 4.2. Nhiều thread cùng tham gia shutdown

`shutdown` thường là idempotent theo contract, nhưng không tự sắp xếp mọi dependency. Có thể có một thread ngừng nhận HTTP, một thread dừng scheduler và nhiều worker còn đang ghi DB. Nếu tắt database trước khi workers commit, `awaitTermination` có thể tiếp tục chờ rồi timeout. Hãy điều phối thứ tự ngừng nhận, drain, close resources, không chỉ viết một lệnh shutdown ở `@PreDestroy`.

## 5. Java 17 chạy được: timeout false, sau đó true

Ví dụ sau dùng `CountDownLatch` để bảo đảm worker **đang chờ** khi gọi `awaitTermination(0, ...)`. Nhờ vậy, kết quả không phụ thuộc vào dự đoán rằng thread “chắc sẽ chạy trong 100ms”.

Tạo tệp **`AwaitTerminationDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class AwaitTerminationDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<String> work = pool.submit(() -> {
                started.countDown();
                release.await();  // Worker chưa thể xong trước release.
                return "done";
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("task not started");
            }

            pool.shutdown();

            boolean immediate = pool.awaitTermination(
                    0, TimeUnit.MILLISECONDS);
            System.out.println("first await = " + immediate);
            System.out.println("shutdown = " + pool.isShutdown());
            System.out.println("terminated before release = "
                    + pool.isTerminated());

            release.countDown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool not terminated");
            }

            System.out.println("second await = true");
            System.out.println("result = " + work.get());
            System.out.println("terminated later = "
                    + pool.isTerminated());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Chạy bằng Java 17+:

~~~bash
javac AwaitTerminationDemo.java
java AwaitTerminationDemo
~~~

**Stdout chính xác trong lượt chạy thành công:**

~~~text
first await = false
shutdown = true
terminated before release = false
second await = true
result = done
terminated later = true
~~~

### 5.1. Vì sao output này có căn cứ?

- Worker gọi `started.countDown()` rồi đợi `release.await()`. Main chỉ tiếp tục khi biết worker thực sự bắt đầu.
- `shutdown()` dừng việc nhận mới nhưng **không interrupt** task đang chạy, nên worker vẫn bị giữ tại latch.
- `awaitTermination(0, ...)` không có ngân sách chờ. Vì worker chắc chưa kết thúc, nó trả false.
- `isShutdown` đã true; `isTerminated` vẫn false. Hai trạng thái khác nhau.
- Khi main mở `release`, worker trả `"done"`; lần `awaitTermination(5s)` thứ hai chờ kết thúc, và ví dụ chỉ in true **sau khi đã kiểm tra true**.
- `work.get()` cho kết quả task. Kết thúc executor không tự trả giá trị này; cần đọc `Future` riêng.
- `finally` bảo đảm latch được mở và executor nhận shutdownNow nếu test fail, tránh để JVM bị treo bởi worker bị khóa.

**Giới hạn test:** timeout 5 giây chỉ là **guard** cho máy CI có vấn đề. Nếu không kết thúc được trong 5 giây, chương trình fail rõ ràng chứ không giả vờ output đạt kỳ vọng. Bài học ở đây là contract và thứ tự sự kiện, không phải đo chính xác OS scheduler theo mili-giây.

## 6. Hai-phase shutdown: một chiến lược, không phải phép màu

Javadoc `ExecutorService` có ví dụ shutdown hai pha:

1. `shutdown()`: không nhận thêm tasks, cho công việc đã nhận hoàn tất.
2. `awaitTermination(grace, unit)`: dành thời gian cho graceful completion.
3. Nếu timeout, `shutdownNow()`: cố interrupt các task còn chạy và ngăn queued tasks bắt đầu.
4. `awaitTermination(forceWait, unit)`: dành thêm thời gian cho tasks phản ứng với interrupt.
5. Nếu vẫn chưa terminated, ghi nhận/escalate; **không** in “all tasks completed” giả.

Ví dụ helper Java 17, dùng như một phần của lifecycle orchestration:

~~~java
static boolean stopPool(ExecutorService pool)
        throws InterruptedException {

    pool.shutdown();

    if (pool.awaitTermination(10, TimeUnit.SECONDS)) {
        return true;
    }

    List<Runnable> neverStarted = pool.shutdownNow();
    System.err.println(
            "shutdownNow pending tasks = " + neverStarted.size());

    return pool.awaitTermination(3, TimeUnit.SECONDS);
}
~~~

Đây là **snippet** cần imports `ExecutorService`, `List`, `Runnable`, `TimeUnit`. Caller phải xử lý `InterruptedException` (hoặc propagate trong một method cho phép), không được nuốt. Helper trả boolean của trạng thái executor chứ không phải business success.

### 6.1. Xử lý InterruptedException trong shutdown owner

Nếu thread điều phối shutdown bị ngắt, một idiom phổ biến là:

~~~java
try {
    pool.shutdown();
    if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
        pool.shutdownNow();
    }
} catch (InterruptedException ex) {
    pool.shutdownNow();
    Thread.currentThread().interrupt();
}
~~~

Đây là **snippet**, cần dùng trong một scope quản lý executor. Restore interrupt flag vì các API blocking như `awaitTermination` thường xóa flag khi ném `InterruptedException`. Không nên tiếp tục chờ vô hạn sau đó khi shutdown coordinator đã bị yêu cầu ngừng.

### 6.2. “Graceful” không đồng nghĩa mọi việc hoàn tất

Nếu pool đã terminated, một task có thể đã ném exception; nếu `shutdownNow` trả 12 pending tasks, các task đó **chưa được chạy**. Ngoài ra, một task đã trả về thành công vẫn có thể chỉ enqueue message hoặc HTTP call chưa xác nhận ở downstream. Quyết định trạng thái nghiệp vụ phải dựa trên job store/results, không dựa vào boolean cuối của stop helper.

### 6.3. Tổng ngân sách chờ phải được thống nhất

Nếu bạn có năm executors và cho **mỗi** executor 30 giây graceful + 30 giây force theo thứ tự, tổng shutdown có thể vượt nhiều phút. Khi deployment platform chỉ cho 30 giây termination grace, app sẽ bị kill trước khi hoàn tất quy trình. Hãy phân bổ **một ngân sách tổng**, rồi lấy remaining time cho từng bước.

## 7. Deadline shutdown: tốt hơn nhiều timeout độc lập

Trong production, thường có một mốc đóng toàn ứng dụng, ví dụ orchestrator gửi SIGTERM và sau đó dùng SIGKILL khi hết grace period. Nếu chỉ tính “mỗi bước được 20 giây” mà không có global deadline, các bước nối tiếp dễ vượt thời gian thực tế được cấp.

Một chiến lược:

~~~text
T0: nhận yêu cầu dừng ứng dụng
    |
    +-- STOP_ACCEPTING: ngừng nhận request/tác vụ mới
    |
    +-- DRAIN: hoàn tất pending tasks trong phần grace budget
    |
    +-- CANCEL: khi quá hạn, đề nghị interrupt/cancel
    |
    +-- RECHECK: dành phần thời gian còn lại để cleanup
    |
Tdeadline: báo trạng thái còn dang dở + cho platform quyết định kill
~~~

**Không** tự đặt tất cả bước bằng “30 giây” nếu container grace chỉ 30 giây. Giữ ít nhất một khoảng cho logging, close DB, flushing telemetry và process exit.

### 7.1. Dùng nguồn monotonic cho thời gian còn lại

Khi đo elapsed shutdown duration, `System.nanoTime()` phù hợp hơn wall clock vì timestamp hệ điều hành có thể được điều chỉnh. Một snippet tính remaining:

~~~java
long deadlineNanos =
        System.nanoTime() + TimeUnit.SECONDS.toNanos(25);

long remainingNanos =
        Math.max(0L, deadlineNanos - System.nanoTime());

boolean finished = pool.awaitTermination(
        remainingNanos, TimeUnit.NANOSECONDS);
~~~

Đây là **snippet**; trong code production phải xử lý overflow nếu deadline/budget cực lớn, và nhớ rằng các thao tác khác cũng tiêu tốn thời gian. `System.nanoTime` chỉ để tính chênh lệch trong JVM, không phải Unix timestamp.

### 7.2. Đừng dùng awaitTermination(Long.MAX_VALUE, DAYS) để “chắc chắn xong”

Một timeout cực lớn không làm task hợp tác hơn. Nếu worker deadlock hoặc chờ network mãi, caller có thể block gần như vô hạn; thread chờ cũng có thể bị interrupt. Vì thế “đã gọi awaitTermination rất lâu” **không phải** guarantee tất cả business work sẽ hoàn tất.

### 7.3. Nếu chờ hết thời gian mà pool vẫn chạy?

Hãy phân loại task còn lại, log job IDs, request cancellation, chuyển các items chưa hoàn thành về trạng thái retry/recovery trong store và cho process close theo chính sách. Không được tiếp tục giữ một shutdown hook vô hạn vì platform vẫn có thể kill process; cũng không được đánh dấu tất cả jobs SUCCEEDED để dashboard xanh.

## 8. Interrupt không phải kill cưỡng bức

`shutdownNow()` và `Future.cancel(true)` thường dùng `Thread.interrupt()` để yêu cầu task dừng. Nhưng Java interruption là cơ chế **hợp tác**:

- `Thread.sleep`, `CountDownLatch.await`, `BlockingQueue.take` thường ném `InterruptedException` khi bị interrupt.
- Một vòng CPU không đọc interrupt flag có thể tiếp tục chạy.
- Một driver I/O có thể không hỗ trợ hoặc chậm phản ứng interrupt.
- Remote HTTP/DB/payment operation có thể đã commit ngay cả khi local thread nhận interrupt.
- Một task giữ lock hoặc đang deadlock có thể không kết thúc trước deadline.

Ví dụ task cooperative:

~~~java
while (!Thread.currentThread().isInterrupted()) {
    if (!hasMoreWork()) {
        break;
    }
    processNextItem();
}
~~~

Đây là **snippet**, các method nghiệp vụ chưa được khai báo. Với blocking API:

~~~java
try {
    queue.take();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Đây là **snippet** trong method `void` với `BlockingQueue<?> queue`. Restore flag rồi thoát hoặc propagate `InterruptedException`, không nuốt rồi tiếp tục vòng lặp.

### 8.1. isInterrupted khác Thread.interrupted

`Thread.currentThread().isInterrupted()` đọc cờ nhưng không xóa. `Thread.interrupted()` static đọc **và xóa** cờ của current thread. Dùng sai method khi kiểm tra cancellation có thể làm mất tín hiệu. Khi bắt `InterruptedException`, việc khôi phục flag bằng `Thread.currentThread().interrupt()` thường cần thiết nếu method không propagate.

### 8.2. Không biến cancellation thành retry vô hạn

Nếu job bị ngắt vì shutdown mà code bắt rồi retry cả transaction 100 lần, quá trình shutdown sẽ bị trì hoãn hoặc phụ thuộc platform kill. Đặt retry budget, nhận diện cancellation riêng với lỗi transient, và có deadline chung. Với remote side effects, cần idempotency key khi retry vì có thể request trước đã thành công nhưng caller không nhận response.

### 8.3. Task bỏ qua interrupt: trả false là hành vi đúng

~~~java
while (!stoppedByExternalFlag.get()) {
    doCpuWorkWithoutBlocking();
}
~~~

Đây là **phản ví dụ**, không dùng chạy production/test với vòng CPU vô hạn. Vì loop không kiểm tra `Thread.currentThread().isInterrupted()`, `shutdownNow()` có thể không khiến nó dừng. `awaitTermination(timeout)` tiếp tục trả false; đấy là kết quả phù hợp contract, không phải “awaitTermination hỏng”.

## 9. Java 17 chạy được: interrupt thread đang chờ không dừng worker

Một hiểu nhầm: thread gọi `awaitTermination` bị interrupt thì task executor cũng tự bị interrupt. Sai: thread **đang chờ** và các **worker threads** là các thread khác nhau. Chương trình sau kiểm chứng:

Tạo tệp **`AwaitInterruptedDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AwaitInterruptedDemo {

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        CountDownLatch waiterReady = new CountDownLatch(1);
        CountDownLatch waiterFinished = new CountDownLatch(1);
        AtomicBoolean flagRestored = new AtomicBoolean(false);

        try {
            pool.submit(() -> {
                taskStarted.countDown();
                releaseTask.await();
                return null;
            });

            if (!taskStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("task not started");
            }

            pool.shutdown();

            Thread waiter = new Thread(() -> {
                waiterReady.countDown();
                try {
                    pool.awaitTermination(1, TimeUnit.DAYS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    flagRestored.set(
                            Thread.currentThread().isInterrupted());
                } finally {
                    waiterFinished.countDown();
                }
            });

            waiter.start();

            if (!waiterReady.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("waiter not ready");
            }

            waiter.interrupt();

            if (!waiterFinished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("waiter did not exit");
            }

            System.out.println("waiter restored = "
                    + flagRestored.get());
            System.out.println("pool still running = "
                    + !pool.isTerminated());

            releaseTask.countDown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("pool not terminated");
            }

            System.out.println("terminated after release = "
                    + pool.isTerminated());
        } finally {
            releaseTask.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Chạy:

~~~bash
javac AwaitInterruptedDemo.java
java AwaitInterruptedDemo
~~~

**Stdout chính xác khi hoàn thành bình thường:**

~~~text
waiter restored = true
pool still running = true
terminated after release = true
~~~

### 9.1. Giải thích bất biến

- `taskStarted` xác nhận worker đã vào task. `releaseTask` chưa được mở, worker không thể trả về bình thường.
- `pool.shutdown()` không interrupt worker đang chạy, nên worker vẫn chờ latch.
- `waiterReady` xác nhận thread phụ đã bắt đầu; nếu `waiter.interrupt()` tới trước `awaitTermination`, interrupt status vẫn làm lần chờ tương ứng ném `InterruptedException`; nếu nó tới trong khi chờ, phương thức cũng ném.
- `flagRestored=true` là do thread waiter bắt và restore interrupt.
- `pool.isTerminated()==false` lúc đó vì **worker vẫn chờ**, chứ không phải do waiter.
- Khi main mở `releaseTask`, worker được phép kết thúc, nên executor có thể chuyển sang terminated.
- Hai latch và việc chờ có timeout tránh test phụ thuộc `Thread.sleep`; nếu CI bị treo bất thường, test fail với guard thay vì in số giả.

**Quan trọng:** `InterruptedException` từ `awaitTermination` nói về **thread gọi**, không phải kết quả hủy worker. Nếu policy là hủy luôn executor khi shutdown coordinator bị interrupt, code phải gọi `shutdownNow()` một cách tường minh.

## 10. Future, cancellation và trạng thái nghiệp vụ

### 10.1. Future.get không đồng nghĩa chờ shutdown

~~~java
Future<String> future = pool.submit(() -> performJob());
String result = future.get(5, TimeUnit.SECONDS);
~~~

Đây là **snippet**, cần `performJob()`. `Future.get` chỉ chờ một task, không dừng executor, không báo các tasks khác đã xong. `get(timeout)` hết hạn ném `TimeoutException` nhưng **không tự cancel** task.

### 10.2. Future.cancel(true) báo yêu cầu, không báo cleanup

~~~java
boolean accepted = future.cancel(true);
~~~

`accepted` phản ánh nỗ lực cancel theo Future state; nó không là một cam kết rằng DB transaction đã rollback hoặc external API chưa được gọi. Nếu cần biết đã cleanup, task phải báo completion qua latch/job status/transaction outcome hoặc một cơ chế khác.

### 10.3. Future cancelled nhưng side effect vẫn có thể xảy ra

Một HTTP request tới payment provider có thể đã được provider nhận và xử lý ngay trước khi local task bị interrupt. Local Future cancelled không thể thu hồi tiền tự động. Thực hành đúng là **idempotency key và reconciliation**: một retry sau restart không gửi lại một logical charge mới.

### 10.4. awaitTermination true sau task thất bại

Nếu ba tasks gồm một success, một failure và một cancelled, executor vẫn có thể terminated sau shutdown. Chỉ từ boolean true không biết cái nào thành công. `Future.get()` trên task fail có thể ném `ExecutionException` với cause; trên cancelled có thể ném `CancellationException`. Business job store nên lưu trạng thái riêng và cho người vận hành xem.

## 11. Spring ThreadPoolTaskExecutor: hai thiết lập dễ nhầm

Spring Framework `ThreadPoolTaskExecutor` dựa trên executor được cấu hình bởi `ExecutorConfigurationSupport`. Hai thiết lập thường liên quan:

~~~java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(10);
~~~

Đây là **snippet Spring** cho một executor bean.

**`setWaitForTasksToCompleteOnShutdown(true)`** lựa chọn phương thức dừng theo hướng **không interrupt tasks đang chạy, tiếp tục xử lý tasks trong queue** khi shutdown. Nó **không** tự bảo đảm Spring container sẽ đứng chờ tới khi jobs kết thúc trước khi tiếp tục destroy các bean khác.

**`setAwaitTerminationSeconds(10)`** bảo container **chờ tối đa** 10 giây ở giai đoạn shutdown của executor trước khi tiếp tục. Khi hết 10 giây, việc chờ kết thúc — **không mặc nhiên cancel/interrupt tasks còn lại nếu chế độ wait-for-tasks đã cho chúng tiếp tục**.

### 11.1. Hai cờ không phải cùng một khái niệm

| Thiết lập | Nó điều khiển gì? | Không có nghĩa |
| --- | --- | --- |
| `setWaitForTasksToCompleteOnShutdown(false)` | Theo chính sách lifecycle Spring, có thể shutdown ngay/interrupt active tasks, clear queue | Worker chắc chắn dừng tức thì |
| `setWaitForTasksToCompleteOnShutdown(true)` | Để queued và active work tiếp tục hoàn thành trong shutdown | Container chắc chắn chờ vô hạn để mọi task xong |
| `setAwaitTerminationSeconds(10)` | Max **thời gian chặn** trong executor shutdown callback | Mọi task có timeout 10 giây; tasks bị kill sau 10 giây |
| `setAwaitTerminationMillis(500)` | Cùng ý tưởng, cấu hình độ chính xác mili-giây | 500ms là deadline của mọi DB/HTTP call |
| `setAcceptTasksAfterContextClose(...)` | Chính sách nhận tasks khi context close bắt đầu | Job state được lưu bền hoặc mọi task tự cleanup |
| `setThreadNamePrefix(...)` | Dùng tên thread dễ truy vết | Thay đổi correctness của shutdown |

Spring 6.2 có lifecycle coordination như `SmartLifecycle`. Mỗi chế độ tương tác với việc dừng tiếp nhận task và phase của context khác nhau; **không** đơn giản hóa tất cả thành “Spring sẽ luôn đợi hết rồi mới đóng DB”. Cần đọc Javadoc đúng Spring version được dùng.

### 11.2. Ví dụ bean Spring

~~~java
@Configuration(proxyBeanMethods = false)
class ExecutorConfig {

    @Bean("jobExecutor")
    ThreadPoolTaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor e =
                new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(4);
        e.setQueueCapacity(100);
        e.setThreadNamePrefix("jobs-");

        e.setWaitForTasksToCompleteOnShutdown(true);
        e.setAwaitTerminationSeconds(15);
        return e;
    }
}
~~~

Đây là **Spring configuration snippet**, cần import `@Configuration`, `@Bean` và `ThreadPoolTaskExecutor`. Không dùng `@PreDestroy` tự gọi lại shutdown lên cùng bean một cách tùy tiện; Spring đã có lifecycle quản lý executor.

### 11.3. Vì sao true + 15 giây vẫn có thể còn việc?

Giả sử 200 jobs được xếp vào queue, một job mất 60 giây. `waitForTasksToCompleteOnShutdown(true)` cho phép các jobs đã nhận tiếp tục trong quá trình executor shutdown; `awaitTerminationSeconds(15)` **chỉ giới hạn việc Spring chờ** 15 giây rồi tiếp tục đóng các bean khác. Các jobs có thể tiếp tục dùng DB/messaging sau thời điểm đó, lúc các dependencies đã bị destroy. Vì vậy, cần sizing grace phù hợp với workload, drain policy và business recovery, không dựa vào cờ này để chặn shutdown vô hạn.

## 12. Spring Context Maven Java 17: close trả về trước khi task xong

Bài thực nghiệm này cho thấy **thời gian await của Spring không là hard timeout cho worker**. Ta cấu hình Spring executor tiếp tục xử lý task đang chạy khi shutdown, nhưng chỉ chờ tối đa **1 giây** trước khi context.close() tiếp tục. Một task được chặn có chủ đích ở latch, vì thế nó không thể hoàn thành trước khi test mở latch.

### 12.1. Cấu trúc

~~~text
spring-await-termination-demo/
├── pom.xml
└── src/main/java/demo/SpringAwaitDemo.java
~~~

**Tệp `pom.xml`:**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-await-termination-demo</artifactId>
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

**Tệp `src/main/java/demo/SpringAwaitDemo.java`:**

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class SpringAwaitDemo {

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean("jobExecutor")
        ThreadPoolTaskExecutor jobExecutor() {
            ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
            e.setCorePoolSize(1);
            e.setMaxPoolSize(1);
            e.setQueueCapacity(1);
            e.setThreadNamePrefix("job-");

            e.setWaitForTasksToCompleteOnShutdown(true);
            e.setAwaitTerminationSeconds(1);
            return e;
        }
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            ThreadPoolTaskExecutor e =
                    context.getBean("jobExecutor",
                            ThreadPoolTaskExecutor.class);

            try {
                Future<String> task = e.submit(() -> {
                    started.countDown();
                    try {
                        release.await();
                        return "done";
                    } finally {
                        finished.countDown();
                    }
                });

                if (!started.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("task not started");
                }

                // Context close chỉ đợi tối đa 1 giây
                // theo cấu hình awaitTerminationSeconds.
                context.close();

                System.out.println("context close returned = true");
                System.out.println("task finished at close = "
                        + (finished.getCount() == 0));
                System.out.println("executor terminated at close = "
                        + e.getThreadPoolExecutor().isTerminated());

                release.countDown();

                if (!e.getThreadPoolExecutor().awaitTermination(
                        5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "executor not terminated");
                }

                System.out.println("task result = "
                        + task.get(5, TimeUnit.SECONDS));
                System.out.println("executor terminated later = "
                        + e.getThreadPoolExecutor().isTerminated());
            } finally {
                release.countDown();
            }
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.SpringAwaitDemo
~~~

**Stdout do chương trình in khi chạy thành công** (bỏ qua log riêng của Maven/Spring):

~~~text
context close returned = true
task finished at close = false
executor terminated at close = false
task result = done
executor terminated later = true
~~~

### 12.2. Vì sao output hợp lý?

- Spring tạo bean `ThreadPoolTaskExecutor` và quản lý startup/shutdown.
- `started` xác nhận task đã bắt đầu; worker còn bị chặn bởi `release.await()`.
- `setWaitForTasksToCompleteOnShutdown(true)` khiến Spring dùng orderly shutdown để task chờ được phép tiếp tục; không chủ động interrupt worker.
- `setAwaitTerminationSeconds(1)` khiến container chỉ chờ tối đa khoảng một giây ở bước executor shutdown trước khi tiếp tục. Khi context.close() trả về, `release` vẫn chưa mở, nên task chưa thể hoàn tất.
- `finished.getCount()!=0` và `isTerminated()==false` chứng minh worker còn outstanding sau container close.
- Main mở latch, worker hoàn thành; lần `awaitTermination(5s)` độc lập sau đó trả true; `Future.get` đọc result `"done"`.
- `finally` luôn mở latch nếu xảy ra exception, tránh để ứng dụng treo vĩnh viễn do task chờ latch.
- Bộ test không đợi `Thread.sleep` để đoán task đã bắt đầu; phần đợi 1 giây xuất phát **từ cấu hình thực của Spring**, không phải lời khẳng định task phải xong sau 1 giây.

**Điều rất quan trọng:** không đưa mẫu “đóng context rồi vẫn để task chạy” vào sản phẩm nếu task cần DataSource, transaction manager hoặc bean khác đã bị destroy. Đây là **demo cảnh báo** về giới hạn await; production cần drain có thứ tự, shutdown deadline và job recovery.

## 13. Spring @Async: phải biết công việc thật đã kết thúc chưa

Một `@Async` method chạy trên executor có thể trả ngay ở caller và tiếp tục xử lý trong worker. Khi context đóng:

- Container có thể ngừng nhận `@Async` submissions mới.
- Tasks đã nhận có thể đang chạy, pending trong queue hoặc đã thất bại.
- `@Async void` không có Future cho caller; exception cần handler thích hợp.
- `@Async CompletableFuture<T>` giúp caller quan sát outcome, nhưng phải thực sự track/cancel/drain các futures.
- `ThreadPoolTaskExecutor` có lifecycle riêng, không đồng nghĩa HTTP response đã gửi là một durable job acknowledgment.

Ví dụ snippet:

~~~java
@Service
class ImportService {

    @Async("jobExecutor")
    public CompletableFuture<String> importFile(String jobId) {
        String value = doImport(jobId);
        return CompletableFuture.completedFuture(value);
    }
}
~~~

Đây là **Spring snippet**, cần `@EnableAsync`, `@Service`, `@Async`, bean `jobExecutor` và method `doImport`. Trong proxy mode mặc định, gọi `this.importFile(...)` từ cùng bean có thể bypass proxy và không dispatch async.

### 13.1. Result handle vẫn cần xử lý

~~~java
CompletableFuture<String> job = importService.importFile("job-7");
job.whenComplete((result, error) -> {
    if (error != null) {
        recordFailure("job-7", error);
    } else {
        recordSuccess("job-7", result);
    }
});
~~~

Đây là **snippet**. `recordFailure/recordSuccess` phải có persistence/idempotency phù hợp; nếu process crash, callback in-memory không được bảo đảm chạy. `awaitTermination(true)` của executor sau shutdown cũng không thay một durable trạng thái công việc.

### 13.2. Context dừng khi task đang giữ transaction

Nếu worker đang ở `@Transactional` và context phá hủy DataSource khi task còn chạy, transaction/connection có thể lỗi. Nếu đó là job quan trọng, cần một shutdown pipeline đảm bảo **đang hoàn thành hoặc đã checkpoint** trước khi các dependencies thiết yếu biến mất, hoặc dùng một worker/job queue có lifecycle tách biệt.

## 14. Spring TaskScheduler và @Scheduled: shutdown phức tạp hơn executor thường

`ThreadPoolTaskScheduler` dùng `ScheduledExecutorService`. Ngoài task đang chạy, nó có thể giữ:

- One-shot task được lên lịch trong tương lai.
- Fixed-rate/fixed-delay periodic registrations.
- Cron triggers có next execution dựa trên time zone và execution history.
- ScheduledFuture chưa tới deadline.
- Error handler, task decorator, pause/resume và lifecycle state.

### 14.1. Delayed task vẫn có thể chờ sau shutdown

`ScheduledThreadPoolExecutor` có các chính sách:

~~~java
setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean value)
setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean value)
~~~

Trong Spring `ThreadPoolTaskScheduler` có những setter tương ứng. Default của underlying scheduled executor thông thường là **thực hiện delayed one-shot tasks đã có sau shutdown** (`true`) và **không tiếp tục periodic tasks sau shutdown** (`false`). Hãy xác nhận cấu hình thật, Spring lifecycle mode và version trước khi chốt outcome.

Ví dụ có one-shot dự kiến **sau một giờ**: nếu shutdown chính sách vẫn cho chạy delayed task, pool có thể chưa terminated cho đến khi task được xử lý/cancel; `awaitTermination(10s)` sẽ trả false. Điều này khác tình huống task đang chạy quá lâu.

### 14.2. Periodic policy có thể cản trở termination

Nếu cố cấu hình periodic tasks tiếp tục sau shutdown, executor có thể không đạt terminated theo cách mong muốn, vì periodic series còn tiếp tục schedule. Không nên bật chính sách này mà không kiểm tra khả năng shutdown thực sự và test bounded termination. Khi application dừng, phải xác định có cancel repeating registrations không.

### 14.3. ScheduledFuture không phải durable cron job store

Một future trong bộ nhớ có thể bị cancel khi process down; Spring scheduler không tự nhớ tất cả ticks bị lỡ sau restart. Nếu một kỳ báo cáo bắt buộc hoàn tất, lưu kỳ nghiệp vụ và outcome trong database/job store; trigger chỉ đánh thức quá trình reconcile.

### 14.4. Scheduler thread có thể đang bị giữ bởi I/O

Nếu scheduled method trực tiếp làm một HTTP call 3 phút, pool scheduler có thể bị block, các triggers khác trễ. Tách một **dispatcher ngắn** sang bounded worker pool hoặc durable queue nếu phù hợp, nhưng đừng quên async work in-flight có vòng đời độc lập và có thể còn tồn tại sau khi scheduler đã terminated.

## 15. Bốn nhóm task khiến awaitTermination hết timeout

| Nhóm | Ví dụ | Vì sao chờ chưa kết thúc? | Phản ứng cần thiết |
| --- | --- | --- | --- |
| Task I/O chậm | HTTP call/DB query không timeout | Worker vẫn đang block | Driver/read/query timeout, cancel, reconciliation |
| Task CPU không hợp tác | Loop không check interrupt | `shutdownNow` không ngắt được logic | Poll interrupt/deadline, chia task thành chunks |
| Deadlock/lock contention | Hai locks lấy sai thứ tự | Không ai tiến tới completion | Lock ordering, diagnostics, fix deadlock; không tin await |
| Queue hoặc lịch tương lai | Hàng nghìn tasks, one-shot delayed | Executor còn outstanding tasks | Bounded queue, cancel/reject, persisted recovery |

### 15.1. I/O timeout và shutdown timeout phải khác nhau

`setAwaitTerminationSeconds(10)` không đặt HTTP client timeout 10 giây, JDBC statement timeout 10 giây hay DB transaction deadline. Nếu một connection đang chờ mạng mãi, container chỉ ngừng chờ sau 10 giây, không tự ép library quay về.

Đặt timeout ngay tại resource:

- HTTP: connect/read/request timeout và retry budget.
- DB: statement/transaction/connection acquisition timeout.
- Lock: bounded `tryLock`/deadline khi hợp lý.
- Queue: bounded capacity và rejection/backpressure.
- Worker: cooperative interruption và cancellation points.

### 15.2. CPU-bound không có điểm hủy

Một tight loop không kiểm tra cờ có thể đốt CPU ngay trong giai đoạn shutdown; `shutdownNow` không phải `Thread.stop`. `Thread.stop` là API không an toàn, không được dùng để “giải quyết” shutdown. Task phải kiểm tra cancel flag/interruption hoặc chia thành các phần nhỏ để thoát hợp tác.

### 15.3. Deadlock không được sửa bằng thêm thời gian chờ

Nếu hai threads deadlock trên locks, `awaitTermination(Long.MAX_VALUE, ...)` chỉ làm caller chờ rất lâu. Cần thread dump, lock graph, lock ordering và sửa code. Timeout giúp hệ thống phát hiện và báo vấn đề, không làm deadlock biến mất.

## 16. Graceful shutdown trong ứng dụng Spring: thứ tự quan trọng

Một kế hoạch mẫu:

~~~text
1. Mark service NOT_READY / ngừng nhận traffic mới
2. Ngừng scheduler và producers nộp task mới
3. Đóng kênh intake, chặn/reject submit mới một cách có chủ đích
4. Drain worker queue trong ngân sách đã tính
5. Với công việc quá hạn: cancel/interruption + record unfinished IDs
6. Chờ một khoảng ngắn để cleanup hợp tác
7. Persist trạng thái, flush logs/metrics có giới hạn
8. Đóng DB/network/message dependencies
9. Trả quyền điều khiển cho platform trước hard kill deadline
~~~

Đây là **sơ đồ chính sách**, không phải thứ tự cố định cho mọi ứng dụng. Nếu job cần database trong bước 4, đừng destroy DataSource trước khi job xong. Nếu scheduler liên tục nộp việc mới khi worker đang drain, executor có thể không bao giờ về trạng thái idle — dừng producer trước.

### 16.1. SmartLifecycle và phase

Spring hỗ trợ lifecycle coordination qua `SmartLifecycle` và các phase. `ExecutorConfigurationSupport` có phase, start/stop, accept-after-context-close và các cờ shutdown. Một ứng dụng phức tạp nên thiết kế rõ producer/consumer dependencies, phase và callback thay vì đặt tất cả logic vào một `@PreDestroy` method không đồng bộ.

### 16.2. Khi thêm @PreDestroy vào nhiều bean

Nếu mỗi bean tự gọi `awaitTermination(30s)` trên executor riêng, tổng shutdown budget có thể vượt deadline. Hơn nữa, trong lúc các destroy callbacks chạy, một bean dependency có thể đã bị hủy. Chọn một owner điều phối hoặc container lifecycle đúng, đánh giá dependency graph.

### 16.3. Readiness khác liveness

Khi bắt đầu drain, readiness nên chuyển false để loại instance khỏi routing mới; liveness có thể vẫn true trong thời gian công việc đang cleanup. Nếu liveness bị hạ quá sớm và orchestrator restart/kill process ngay, cơ hội graceful completion giảm. Chính sách thực tế phụ thuộc platform/health probes được triển khai.

### 16.4. Shutdown hook không bảo đảm luôn được chạy

SIGKILL, mất điện, OOM/crash hoặc hard termination của container có thể bỏ qua phần cleanup. Do đó, job quan trọng phải **được recover** khi process lên lại, không dựa toàn bộ độ tin cậy vào `@PreDestroy` hay `awaitTermination`.

## 17. Các trường hợp cần durable job store

Nếu một task có thể mất vài phút/giờ, hoặc phải tồn tại qua deploy/restart, executor RAM-only không đủ. Cần lưu:

~~~text
jobId
businessKey
status: PENDING | RUNNING | SUCCEEDED | FAILED | RETRYABLE
attempt
createdAt / startedAt / finishedAt
leaseOwner / leaseUntil / heartbeat (nếu dùng lease)
lastError / retryAfter
~~~

Một job store có unique business key giúp ngăn duplicate logical operations. Consumer sau restart có thể phát hiện `RUNNING` lease hết hạn, claim lại và tiếp tục/đối soát theo trạng thái thực tế.

### 17.1. AwaitTermination không thay thế persistence

Nếu ứng dụng đợi 30 giây nhưng còn 50 jobs, cần đánh dấu chúng một cách trung thực để lần sau có thể resume/retry. Không gán tất cả PENDING → SUCCEEDED chỉ vì pool đã bị shutdown; cũng không tự tạo một attempt mới trước khi biết remote call cũ đã commit hay chưa.

### 17.2. Idempotency ở boundary của side effect

Một task gửi thanh toán có thể bị caller timeout **sau khi provider đã xử lý**. Retry cần gửi cùng idempotency key hoặc tra trạng thái giao dịch trước; nếu không, graceful shutdown dài hơn cũng không ngăn duplicate khi có network partition.

### 17.3. Phân biệt job “đã nhận” và “đã chạy xong”

Một HTTP endpoint trả 202 Accepted sau khi chỉ gọi `executor.submit` đã gửi việc vào RAM. Nếu process chết trước task chạy, công việc có thể mất. Với yêu cầu durable acceptance, phải commit job intent/outbox trước khi trả acknowledgement, và thiết kế worker claim/recovery.

## 18. Spring Boot property không phải thay thế hiểu lifecycle

Nếu dùng Spring Boot, có thể có properties cấu hình shutdown của **executor do Boot auto-configure**, chẳng hạn nhóm `spring.task.execution.shutdown.*` hoặc `spring.task.scheduling.shutdown.*`, tùy Boot version. Chúng không mặc nhiên áp lên mọi custom `@Bean ThreadPoolTaskExecutor` hoặc thư viện tự tạo executor.

Luôn kiểm tra:

1. Executor được tạo bởi Boot auto-config hay code của bạn?
2. Có tên/qualifier nào được `@Async`, `@Scheduled`, Web MVC, messaging dùng?
3. Config của instance thực tế đang bật chế độ chờ/interrupt nào?
4. Spring Framework/Boot version có thay đổi coordinated lifecycle/shutdown semantics không?
5. Khi shutdown, scheduler và worker có tham chiếu tới resources đã destroy không?

Không suy rằng đặt một property ở `application.yml` sẽ quản lý tất cả `Executors.newFixedThreadPool(...)` tạo bằng tay; một executor tự tạo cần owner lifecycle rõ ràng.

## 19. Java 17 khác Java 21 về try-with-resources executor

Một nuance dễ sai khi copy ví dụ JDK mới:

~~~java
try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
    // ...
}
~~~

Đoạn này **không biên dịch với Java 17** vì ở JDK 17 `ExecutorService` chưa là `AutoCloseable`. Trong Java 17, dùng `try/finally` và `shutdown/awaitTermination` tường minh như các demo trong bài.

Với các JDK mới có `ExecutorService.close()` (ví dụ Java 21), try-with-resources có thể tự thực hiện orderly shutdown và chờ. Nhưng `close()` cũng không bảo đảm “mọi business side effect thành công”; nếu task không kết thúc, close có thể chờ lâu và phải áp policy phù hợp. Đây là lý do các ví dụ chạy Java 17 ở trên **không sử dụng** try-with-resources cho raw `ExecutorService`.

### 19.1. Virtual threads cũng cần quản lý lifecycle

Virtual thread là daemon và nhẹ, nhưng không có đảm bảo durably finish khi process exit. `Executors.newVirtualThreadPerTaskExecutor()` trên Java 21+ vẫn có vòng đời executor; việc dùng virtual thread không biến `awaitTermination` thành một hard deadline. Nhìn theo **task ownership và side effect**, không chỉ nhìn loại thread.

## 20. Cách kiểm thử không flaky

### 20.1. Test timeout bằng CountDownLatch

Giữ worker ở `release.await()` và chỉ mở khi đã assert `awaitTermination(0)==false`. Điều này chứng minh trạng thái một cách xác định. Đừng dùng:

~~~java
Thread.sleep(100);
assertFalse(pool.isTerminated());
~~~

Đây là **phản ví dụ**: trên máy CI chậm, task có thể chưa bắt đầu hoặc đã kết thúc theo timing không dự đoán. `CountDownLatch` xác nhận sự kiện có quan hệ *happens-before* thay vì giả định qua delay.

### 20.2. Test InterruptedException của waiter

Dùng một thread riêng gọi `awaitTermination`, một latch báo waiter đã sẵn sàng, rồi interrupt nó. Assert flag được restore và worker còn chờ latch. Chương trình `AwaitInterruptedDemo.java` ở mục 9 chính là fixture đầy đủ.

### 20.3. Test shutdownNow và cooperative task

Cho worker block tại `BlockingQueue.take()` hoặc `CountDownLatch.await()`, sau đó gọi `shutdownNow()`. Đợi worker báo `finally` xong qua latch; assert executor eventually terminated. **Không** lấy việc `shutdownNow()` trả về làm bằng chứng worker đã cleanup.

### 20.4. Test noncooperative task mà không làm treo suite

Nếu muốn kiểm tra task bỏ qua interrupt, dùng **một cancellation flag riêng được test mở** sau assertion timeout, hoặc chạy trong subprocess với hard kill deadline. Tuyệt đối không để một busy loop vô hạn tồn tại trong JUnit process vì nó có thể giữ pool alive và làm CI treo.

### 20.5. Test Spring Context shutdown

Dùng một worker chờ latch, gọi `context.close()`, assert điều gì được bảo đảm trong cấu hình `setAwaitTerminationSeconds` và `setWaitForTasksToCompleteOnShutdown` cụ thể. Sau đó mở latch rồi đợi executor terminated, như demo mục 12. Không assert thời lượng close chính xác 1000ms; JVM/OS scheduling có jitter.

### 20.6. Test không nhận thêm task sau shutdown

Submit vào executor sau khi `shutdown` rồi assert kiểu rejection theo executor implementation. Với Spring, đọc contract `TaskRejectedException`/`RejectedExecutionException` phù hợp. Đảm bảo producer biết dừng submit; không để error flood trong lúc deploy.

### 20.7. Test job state sau crash

Trong integration test, tạo job PENDING/RUNNING trong DB, mô phỏng worker fail/restart, cho consumer mới claim lại theo lease/idempotency. Một test chỉ đọc `awaitTermination()==true` không thể chứng minh nghiệp vụ survive crash.

### 20.8. Test tổng ngân sách ứng dụng

Nếu platform cho 30s termination grace, test thứ tự ngừng nhận, dừng producers, drain/cancel, DB cleanup và telemetry với tổng budget nhỏ hơn 30s. Đừng kiểm tra mỗi executor riêng rồi bỏ qua thời gian **cộng dồn** và sự cạnh tranh CPU/I/O khi nhiều beans cùng đóng.

## 21. Monitoring để shutdown không “xanh giả”

Những số liệu hữu ích:

| Metric/Log | Ý nghĩa |
| --- | --- |
| `executor_shutdown_requested_at` | Mốc bắt đầu shutdown |
| `executor_await_terminated_total` | Số lần pool terminated thành công trong budget |
| `executor_await_timeout_total` | Số lần timeout trước khi pool terminated |
| `executor_shutdown_interrupt_total` | Shutdown coordinator bị interrupt |
| `executor_active_threads` | Số worker active |
| `executor_queue_depth` | Số queued tasks chưa bắt đầu |
| `job_cancel_requested_total` | Số lần đề nghị cancel |
| `job_cancel_acknowledged_total` | Số job có xác nhận thoát/cancel/cleanup |
| `job_failed_total` | Số logical jobs thất bại |
| `job_recovery_pending_total` | Số unfinished jobs cần recovery sau restart |
| `shutdown_elapsed_seconds` | Tổng thời gian đóng ứng dụng |

Ghi rõ `poolId`, `component`, `jobId` và business key trong log/tracing; tránh đưa hàng triệu job UUID làm label của Prometheus (cardinality). Đo elapsed time bằng monotonic clock; timestamp audit dùng `Instant` UTC.

### 21.1. Phân loại shutdown outcome

Một report shutdown hữu ích có thể là:

~~~text
Graceful phase: TIMED_OUT
Force interrupt phase: COMPLETED
Executor state: TERMINATED
Unstarted tasks: 14 (scheduled for recovery)
Business jobs: 7 succeeded, 2 retryable, 1 unknown
~~~

Đây là **output minh họa**, không phải kết quả một chương trình trong bài. `UNKNOWN` có thể xuất hiện khi remote side effect chưa xác định được; cần reconciliation, không tự coi nó thành FAILED rồi retry mù quáng.

### 21.2. Đừng log “graceful completed” chỉ vì context.close trả về

`context.close()` có thể trả về sau thời hạn chờ cấu hình ngay cả khi worker còn chạy, như demo Spring. Log đúng là **container close callback returned**; nếu muốn xác nhận pool terminated, phải kiểm tra `ThreadPoolExecutor.isTerminated()` hoặc future/job state tương ứng.

## 22. Những nhầm lẫn thường gặp

**1. “awaitTermination tự gọi shutdown.”** Sai. Phải có shutdown request hoặc lifecycle tương ứng.

**2. “awaitTermination(true) chứng minh mọi job thành công.”** Sai. Nó chứng minh executor đã terminated, không chứng minh business outcome.

**3. “awaitTermination(false) nghĩa là tất cả tasks lỗi.”** Sai. Nó chỉ báo hết timeout khi chưa terminated.

**4. “false thì tác vụ tự bị cancel.”** Sai. API không tự cancel vì timeout.

**5. “InterruptedException nghĩa là worker đã bị ngắt.”** Sai. Đó là thread gọi `awaitTermination` bị interrupt.

**6. “shutdown() chờ cho mọi task xong.”** Sai. Nó bắt đầu orderly shutdown nhưng return không chờ.

**7. “shutdownNow() bảo đảm mọi task dừng ngay.”** Sai. Nó nỗ lực best-effort, thường interrupt.

**8. “shutdownNow() trả danh sách các job đã chạy xong.”** Sai. Danh sách là tasks chờ chưa bắt đầu.

**9. “isShutdown() tương đương isTerminated().”** Sai. Có thể shutdown rồi nhưng còn worker/queue.

**10. “pool không có việc thì awaitTermination tự true.”** Sai khi pool chưa shutdown.

**11. “Đợi vô hạn sẽ khiến deadlock tự hết.”** Sai. Cần sửa deadlock, timeout không sửa logic.

**12. “Future.get(timeout) hết hạn sẽ tự cancel task.”** Sai. Timeout của caller không tự gửi interrupt.

**13. “Future.cancel(true) rollback mọi HTTP/DB side effects.”** Sai. Cần idempotency/compensation.

**14. “Một task chờ I/O luôn phản ứng với interrupt.”** Sai. Driver/library có thể không hỗ trợ hoặc phản ứng chậm.

**15. “Bắt InterruptedException rồi bỏ qua giúp shutdown nhanh.”** Thường ngược lại: task có thể tiếp tục, cản trở termination.

**16. “setWaitForTasksToCompleteOnShutdown(true) làm container đợi vô hạn.”** Sai. Nó chọn shutdown policy, không tự tạo một blocking wait vô hạn.

**17. “setAwaitTerminationSeconds(5) kill mọi worker sau 5 giây.”** Sai. Nó giới hạn khoảng chờ của container, không phải hard worker timeout.

**18. “Context.close() trả về nghĩa tất cả tasks đã xong.”** Không luôn đúng; phụ thuộc mode và await budget.

**19. “Đặt property Boot sẽ quản lý mọi executor tự tạo.”** Sai. Phải xác định executor được auto-configure hay custom.

**20. “Thêm scheduler threads giải quyết task đang deadlock.”** Sai. Deadlock không được sửa bằng worker count.

**21. “Scheduled one-shot trong tương lai không ảnh hưởng termination.”** Có thể sai: delayed task policy có thể giữ pending jobs sau shutdown.

**22. “Periodic tasks chắc chắn dừng đúng cách ở mọi custom scheduler.”** Sai. Cần xem chính sách periodic-after-shutdown và version.

**23. “Java 17 ExecutorService hỗ trợ try-with-resources.”** Sai. `AutoCloseable`/`close()` ở các JDK mới hơn.

**24. “Virtual thread nhẹ nên không cần hủy/cleanup.”** Sai. Lifecycle và side effects vẫn cần owner.

**25. “Chỉ cần cài timeout ở shutdown là có HTTP/DB timeout.”** Sai. Đặt timeout tại chính downstream resource.

**26. “Một task có Future completed là message downstream đã commit.”** Không luôn; task có thể mới gửi bất đồng bộ.

**27. “Có awaitTermination 30s thì SIGKILL sau 30s không còn vấn đề.”** Sai. Hard kill có thể đến giữa shutdown, cần recovery.

**28. “Tăng awaitTermination lên vài giờ là cách xử lý backlog tốt.”** Không nếu deployment grace thấp hoặc job đang mắc lỗi.

**29. “Graceful shutdown loại trừ duplicate khi restart.”** Không. Trường hợp side effect đã commit nhưng local status chưa lưu vẫn cần idempotency.

**30. “Chỉ cần đo thời gian close(), không cần job status.”** Sai. Đoạn close có thể return khi vẫn còn workers hoặc jobs chưa xác định.

## 23. Câu hỏi tự kiểm tra

**Câu 1:** `awaitTermination(5, SECONDS)` có tự gọi `shutdown()` không?

**Đáp án:** không. Nó chỉ chờ trạng thái terminated sau shutdown request. Nếu pool chưa shutdown, có thể timeout ngay cả khi idle.

**Câu 2:** Pool đã `shutdown()`, `awaitTermination` trả false. Worker có chắc chắn đã dừng không?

**Đáp án:** không. Có thể vẫn chạy hoặc queue còn task; phải kiểm tra, cancel theo policy và chờ thêm có giới hạn.

**Câu 3:** `awaitTermination` trả true có nghĩa mọi Future.get đều trả value thành công không?

**Đáp án:** không. Có thể có task failed/cancelled; mỗi Future hoặc durable job status cần được kiểm tra riêng.

**Câu 4:** Caller bị `InterruptedException` trong `awaitTermination` thì worker cũng đã bị interrupt chưa?

**Đáp án:** chưa chắc. Interrupt này nhằm vào thread đang đợi. Nếu cần yêu cầu worker ngừng, phải gọi `shutdownNow()` hoặc cancellation policy.

**Câu 5:** `shutdownNow()` trả về 12 Runnables thì chúng là gì?

**Đáp án:** các tasks đang chờ chưa bắt đầu. Phải quyết định retry/recovery, không coi chúng đã hoàn tất.

**Câu 6:** Tại sao `shutdownNow()` có thể không giúp pool terminated?

**Đáp án:** task active có thể bỏ qua interrupt, mắc deadlock hoặc driver I/O không phản ứng.

**Câu 7:** Spring `setWaitForTasksToCompleteOnShutdown(true)` và `setAwaitTerminationSeconds(10)` có làm task chạy 60s bị kill ở giây thứ 10 không?

**Đáp án:** không. Spring chỉ chờ tối đa 10 giây ở bước shutdown, còn worker có thể tiếp tục theo shutdown policy.

**Câu 8:** `ThreadPoolTaskScheduler` có one-shot delayed 1h, vì sao await 10s có thể false?

**Đáp án:** nếu chính sách cho phép delayed task đã lên lịch tiếp tục sau shutdown, executor còn outstanding task; chờ 10s không đủ.

**Câu 9:** Một task thành công local nhưng gọi remote service không idempotent, crash sau commit. Retry có thể tạo duplicate không?

**Đáp án:** có. Dùng idempotency key/outbox/reconciliation, không dựa vào executor terminated.

**Câu 10:** Java 17 có thể viết `try(ExecutorService e=...)` không?

**Đáp án:** không. Dùng `try/finally` với shutdown/await; ở các JDK mới hơn có `ExecutorService.close()`.

**Câu 11:** Nếu container chỉ cấp 30 giây, năm executors mỗi cái await 30 giây tuần tự có phù hợp không?

**Đáp án:** không. Cần global deadline và phân bổ remaining budget; shutdown có thể bị hard-kill trước khi hết tất cả các lần chờ.

**Câu 12:** Test timeout có nên dùng `Thread.sleep(200)` rồi assert pool chưa xong không?

**Đáp án:** không ổn định. Dùng latch xác nhận worker đã vào và đang bị giữ, rồi kiểm tra timeout/state.

**Câu 13:** `isTerminated()==true` có chứng minh job bên ngoài đã được thanh toán chính xác một lần không?

**Đáp án:** không. Đó là vấn đề business idempotency và trạng thái remote/provider.

**Câu 14:** Vì sao lúc shutdown nên dừng producer trước consumer?

**Đáp án:** nếu producer còn submit, worker/queue có thể còn nhận việc mới hoặc tạo rejection storm, cản trở drain và làm khó xác định unfinished work.

## 24. Checklist thiết kế và code review

- [ ] Xác định executor owner; executor do Spring quản lý hay code tự tạo.
- [ ] Ngừng nhận task mới trước khi drain pool.
- [ ] `shutdown()` trước `awaitTermination` khi dùng raw ExecutorService.
- [ ] Phân biệt `isShutdown` và `isTerminated` trong logging/tests.
- [ ] Xử lý đủ ba trường hợp `true`, `false` và `InterruptedException`.
- [ ] Không đánh dấu all jobs SUCCEEDED chỉ từ `awaitTermination(true)`.
- [ ] Với timeout, có escalation policy và log unfinished job IDs.
- [ ] `shutdownNow` chỉ là best-effort interrupt, không phải hard kill.
- [ ] Task blocking và CPU loops có cancellation checkpoints.
- [ ] `InterruptedException` được propagate hoặc restore flag rồi kết thúc phù hợp.
- [ ] HTTP, DB, lock và queue có timeout/capacity riêng.
- [ ] Tổng shutdown budget không vượt termination grace của platform.
- [ ] Giữ resources thiết yếu (DataSource, clients) hoạt động trong drain phase.
- [ ] Spring `setWaitForTasksToCompleteOnShutdown` và `setAwaitTerminationSeconds` được hiểu đúng và tested.
- [ ] Không dùng các properties Boot để đoán behavior của custom executors.
- [ ] Kiểm tra policy delayed/periodic của `ThreadPoolTaskScheduler`.
- [ ] Có test `CountDownLatch` cho timeout, interruption và cleanup.
- [ ] Test cả rejection khi đã shutdown.
- [ ] Có job store/recovery nếu công việc bắt buộc sống qua crash/redeploy.
- [ ] External side effects có idempotency và reconciliation.
- [ ] Có metrics queue depth, active threads, await timeout, cancel requested/acknowledged.
- [ ] Kiểm tra Java version trước khi dùng `ExecutorService.close()`/try-with-resources.
- [ ] Không để shutdown hook chờ vô hạn hoặc giữ một lock khiến toàn app deadlock.
- [ ] Log chính xác context close returned vs executor terminated vs job success.
- [ ] Với @Async, theo dõi Future/CompletableFuture và errors riêng.
- [ ] Với @Scheduled, ngừng scheduler/producer trước khi drain workers.
- [ ] Mọi công việc chưa xong đều được xác định cách recover, không mặc nhiên biến mất.

## 25. Kết luận

**`awaitTermination(timeout, unit)` không tạo bảo đảm hoàn tất vô hạn; nó đặt giới hạn cho việc caller chờ executor đạt terminated.** Nó không tự shutdown, không tự cancel khi timeout, không kill task không hợp tác, và không cho biết mọi business job đã thành công. Khi trả `false`, nhiệm vụ của application là đánh giá trạng thái thực tế và thực hiện escalation/recovery. Khi bị interrupt, thread chờ cần xử lý đúng cờ interrupt và chính sách hủy, không nhầm với trạng thái worker.

Trong Spring Framework 6.2, `setWaitForTasksToCompleteOnShutdown(true)` là lựa chọn cho công việc/queue tiếp tục, còn `setAwaitTerminationSeconds(N)` giới hạn thời gian container đợi. **Chờ N giây không phải ép worker kết thúc sau N giây.** Hãy phối hợp shutdown order, bounded timeouts cho I/O, cooperative cancellation, overall deadline, quan sát lỗi, job store/idempotency và integration tests.

Nếu công việc phải hoàn thành đúng một lần dù pod bị SIGKILL, giải pháp không nằm trong một lần `awaitTermination` dài hơn; nó nằm trong **job identity, persistence, claim/retry/reconciliation và ownership rõ ràng**.

## Tài liệu tham khảo chính thức

- Java SE 17 Javadoc — `ExecutorService` (`shutdown`, `shutdownNow`, `awaitTermination`, example two-phase shutdown): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Java SE 17 Javadoc — `ThreadPoolExecutor`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- Java SE 17 Javadoc — `ScheduledThreadPoolExecutor` (delayed/periodic policies): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html
- Java SE 17 Javadoc — `Future` (`cancel`, `get`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- Java SE 17 Javadoc — `CountDownLatch`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- Java SE 17 Javadoc — `TimeUnit`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/TimeUnit.html
- Spring 6.2 — `ExecutorConfigurationSupport` (`setWaitForTasksToCompleteOnShutdown`, `setAwaitTerminationSeconds`): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html
- Spring 6.2 — `ThreadPoolTaskExecutor`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
- Spring 6.2 — `ThreadPoolTaskScheduler`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html
- Spring Framework Reference — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework Reference — Lifecycle Callbacks and SmartLifecycle: https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-shutdown-006">Tương tác của các tùy chọn shutdown và khác biệt theo phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

# Tương tác của các tùy chọn shutdown và khác biệt theo phiên bản

## 1. Vì sao cấu hình shutdown không chỉ là một boolean?

Trong Spring Framework, `ThreadPoolTaskExecutor` và `ThreadPoolTaskScheduler` là những bean quản lý các executor của JDK. Khi application dừng, ta không chỉ cần quyết định **dừng nhận việc mới** mà còn phải quyết định **xử lý việc đã nhận**, **đợi bao lâu**, **giải phóng tài nguyên Spring theo thứ tự nào** và **các task được hẹn chạy trong tương lai có còn chạy hay không**.

Những tùy chọn nhìn gần giống nhau thực ra trả lời các câu hỏi khác nhau:

- `setAcceptTasksAfterContextClose(boolean)`: có còn cho phép nhận task trong giai đoạn Spring `ApplicationContext` đang đóng?
- `setWaitForTasksToCompleteOnShutdown(boolean)`: lúc thực hiện shutdown cuối cùng, dùng kiểu chờ cho các việc đã nhận hay yêu cầu dừng ngay?
- `setAwaitTerminationSeconds(int)` / `setAwaitTerminationMillis(long)`: thread thực hiện shutdown **chờ tối đa bao lâu** để executor thật sự kết thúc?
- `setPhase(int)` và cơ chế `SmartLifecycle`: Spring điều phối các bean chạy/dừng ở pha nào?
- `ThreadPoolTaskScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(...)` và `setExecuteExistingDelayedTasksAfterShutdownPolicy(...)`: chính sách dành riêng cho **những lịch lặp** và **những lệnh một lần có delay** khi `ScheduledThreadPoolExecutor` nhận shutdown.
- `ThreadPoolTaskExecutor.setStrictEarlyShutdown(boolean)`: từ **Spring Framework 6.1.4**, kiểm soát cách executor này phát tín hiệu shutdown sớm tại context close.

**Không có một công tắc “graceful shutdown” duy nhất giải quyết mọi yêu cầu.** Một cấu hình có thể giữ task chạy hết nhưng khiến Spring đóng database connection quá sớm; một cấu hình khác chờ tối đa 30 giây nhưng vẫn đã interrupt worker từ trước; một scheduler có thể đã shutdown mà vẫn giữ periodic task đang chạy vì chính sách native JDK.

### Mục tiêu học tập

Sau bài này bạn sẽ:

1. Phân biệt `ExecutorService.shutdown()`, `shutdownNow()` và `awaitTermination(...)` trong Java 17.
2. Hiểu hai thời điểm Spring quan trọng: **`ContextClosedEvent`/coordinated lifecycle stop** và **bean destruction/actual executor shutdown**.
3. Giải thích tương tác `acceptTasksAfterContextClose`, `waitForTasksToCompleteOnShutdown`, `awaitTerminationSeconds` và `strictEarlyShutdown`.
4. Tránh gán API của **Spring 6.1.4+** cho dự án **Spring 6.1.3**, đặc biệt phương thức `setStrictEarlyShutdown`.
5. Hiểu vì sao `ThreadPoolTaskScheduler` có thêm chính sách cho delayed/periodic schedules, khác `ThreadPoolTaskExecutor`.
6. Viết/chạy chương trình Java 17+ với `ScheduledThreadPoolExecutor` để chứng minh delayed task và periodic task có thể tiếp tục hoặc bị hủy sau `shutdown()` tùy policy.
7. Viết/chạy ví dụ **Spring Framework 6.1.3** cho hai chế độ shutdown của `ThreadPoolTaskExecutor`, có đồng bộ bằng `CountDownLatch` và stdout có kiểm chứng.
8. Xây dựng checklist vận hành để không làm mất task, không giữ tài nguyên vô hạn, không tự tạo duplicate side effects khi ứng dụng dừng.

**Phiên bản nền của ví dụ Spring:** `6.1.3`, JDK `17+`. Phần “khác biệt theo phiên bản” chỉ ra rõ nơi API hoặc default thay đổi. Không thay số version trong POM rồi mặc định mọi thuộc tính/hành vi hoàn toàn giống nhau.

## 2. Lớp JDK: shutdown, shutdownNow và awaitTermination

Các executor Spring phía dưới vẫn chịu hợp đồng của `java.util.concurrent.ExecutorService`.

### 2.1. ExecutorService.shutdown()

`shutdown()` bắt đầu **orderly shutdown**:

- Không nhận các task mới gửi vào executor từ thời điểm nó đã shutdown.
- Những task **đã được nhận trước đó** có thể hoàn tất, bao gồm các task đang chạy và những task trong queue, theo hợp đồng của executor.
- Lời gọi `shutdown()` **không tự chờ** mọi task hoàn thành.
- Sau khi gọi shutdown, `isShutdown()` có thể là `true` trong khi `isTerminated()` vẫn `false`.

Với `ScheduledThreadPoolExecutor`, điểm “task đã nhận” cần đọc kèm **chính sách riêng cho delayed/periodic tasks**, vì các chính sách này có thể cho phép tiếp tục hay hủy một số lịch dù `shutdown()` là orderly.

### 2.2. ExecutorService.shutdownNow()

`shutdownNow()` là **best-effort immediate shutdown**:

- Thử interrupt những task đang chạy.
- Dừng bắt đầu các task còn nằm trong queue.
- Trả về danh sách những `Runnable` chưa bắt đầu chạy.
- **Không bảo đảm** task đang thực thi sẽ dừng: interrupt chỉ là tín hiệu hợp tác; một thư viện I/O, đoạn code bỏ qua interrupt hoặc remote service vẫn có thể tiếp tục hoạt động.

Đừng đọc tên phương thức rồi nghĩ nó là “kill thread” hoặc “rollback transaction”. Executor không thể cưỡng chế hoàn tác một side effect ở service khác chỉ bằng `shutdownNow()`.

### 2.3. ExecutorService.awaitTermination(timeout, unit)

`awaitTermination(...)` **chờ** executor chuyển sang terminated **sau khi** shutdown đã được khởi động. Nó trả `true` nếu kết thúc trong hạn, `false` nếu hết thời gian, hoặc có thể ném `InterruptedException` khi thread đang chờ bị interrupt.

Nó **không lựa chọn** graceful hay immediate; lựa chọn này được thực hiện bởi `shutdown()` hoặc `shutdownNow()`. Tương tự, Spring `setAwaitTerminationSeconds(30)` không tự biến `shutdownNow()` thành `shutdown()`.

~~~text
submit tasks -> running + queued
                |
                +-- shutdown() -----> ngừng nhận mới
                |                     existing work có thể hoàn tất
                |
                +-- shutdownNow() --> thử interrupt running
                |                     trả/dọn queued tasks
                |
                +-- awaitTermination(...) --> chờ kết quả của một
                                              chiến lược shutdown đã chọn
~~~

**Câu phải nhớ:** *Stop accepting*, *what to do with accepted work*, và *how long to wait* là **ba quyết định khác nhau**.

## 3. Lớp Spring: đóng ApplicationContext có nhiều pha

`ThreadPoolTaskExecutor` và `ThreadPoolTaskScheduler` kế thừa `ExecutorConfigurationSupport`. Từ Spring Framework 6.1, base class này tham gia `SmartLifecycle` và nhận `ContextClosedEvent`, giúp hỗ trợ coordinated lifecycle stop.

Ở mức khái niệm, một quá trình đóng context có thể gồm:

1. Spring phát `ContextClosedEvent`; executor có thể nhận **tín hiệu ngừng nhận task sớm**, tùy cấu hình và loại executor.
2. `LifecycleProcessor` dừng các bean `SmartLifecycle` theo phase và chờ các callback stop theo timeout của lifecycle processor.
3. Bean destruction diễn ra; `ExecutorConfigurationSupport.destroy()` gọi `shutdown()` để thực hiện bước shutdown executor thực tế.
4. Nếu đã cấu hình `awaitTerminationSeconds`, bước shutdown có thể **block** trong một khoảng giới hạn để chờ worker và queue.
5. Những resource/bean khác bị hủy theo lifecycle/dependency order của Spring.

Thứ tự chính xác, logic early/late và cơ chế stop phụ thuộc **phiên bản** và **từng subclass**. Không suy luận rằng phương thức `ApplicationContext.close()` luôn đợi toàn bộ executor work thành công, hoặc có thể gửi task an toàn từ mọi `@PreDestroy` callback.

### 3.1. Early shutdown khác final shutdown

**Early shutdown signal**: đến từ quá trình context close, có thể đóng cửa nhận task mới trước khi bean destruction bắt đầu, trong khi những task hiện có còn cơ hội hoàn tất qua coordinated lifecycle stop.

**Final executor shutdown**: khi bean executor bị destroy hoặc ai đó gọi trực tiếp `shutdown()`. Đường này có thể chọn orderly hay immediate theo `waitForTasksToCompleteOnShutdown` và có thể block theo `awaitTerminationSeconds`.

Không chỉ nhìn thời điểm gọi `setAcceptTasksAfterContextClose` để đoán lúc task sẽ bị interrupt; flag này điều chỉnh **khả năng nộp task trong quá trình context đóng**, không đơn giản thay thế hai hàm JDK shutdown.

### 3.2. SmartLifecycle phase và await termination là hai timeout khác nhau

`setPhase(int)` (có từ Spring 6.1) ảnh hưởng vị trí executor trong coordinated lifecycle stop. Spring `SmartLifecycle` có phase, stop callback và `LifecycleProcessor` có thời hạn chờ riêng cho từng phase.

`setAwaitTerminationSeconds` lại dành cho **bước shutdown của executor**, khi bean destruction gọi `shutdown()`. Nó không trực tiếp đặt thời hạn dừng của mọi bean cùng phase. Khi phân tích deployment shutdown lâu, phải xem **cả lifecycle timeout lẫn await-termination**.

### 3.3. Tương tác với @Async, @Scheduled và context còn sống

- Method `@Async` chạy trên `ThreadPoolTaskExecutor` có thể chưa hoàn tất khi Spring bắt đầu destroy các bean mà task cần dùng.
- Method `@Scheduled` chạy trên `ThreadPoolTaskScheduler`, vì vậy shutdown scheduler không giống việc chỉ đóng một pool chuyên xử lý task ngay lập tức.
- Một `@PreDestroy` listener có thể muốn submit thêm task, nhưng executor có thể đã không nhận do early signal. Đừng viết logic cleanup quan trọng phụ thuộc vào khả năng nộp việc mới “đến phút cuối”.
- Một task đang chạy có thể đang giữ database connection, transaction, socket hoặc file lock. Kết thúc executor **không** có nghĩa mọi side effect đã commit hoặc được hoàn tác đúng.

## 4. Bộ tùy chọn chung trong ExecutorConfigurationSupport

### 4.1. setAcceptTasksAfterContextClose(boolean)

~~~java
// Fragment trên một ThreadPoolTaskExecutor hoặc ThreadPoolTaskScheduler
// trước khi bean được Spring khởi tạo.
executor.setAcceptTasksAfterContextClose(true);
~~~

Ý nghĩa: cho phép executor **tiếp tục nhận task trong giai đoạn đóng context** thay vì phát tín hiệu từ chối sớm. Tùy chọn này có từ **Spring 6.1** và chỉ thực sự có ý nghĩa khi executor hoạt động như bean trong ApplicationContext để nhận `ContextClosedEvent`.

Theo Javadoc Spring 6.1.3 của base class, default là `false` để kích hoạt early soft shutdown; bật `true` cho phép late submissions từ các thành phần đang stop/destroy. Nhưng điều đó cũng khiến executor **không trải qua coordinated lifecycle stop giống đường default**, chỉ shutdown ở destruction step theo cấu hình. Hệ thống có thể dừng lâu hơn và lifecycle dependencies cần được đánh giá lại.

**Quan trọng:** Đừng coi đây là “thêm 5 giây grace”. Nó mở khả năng submit thêm trong cả một giai đoạn vòng đời; task mới có thể dựa vào resource đang trên đường bị đóng.

### 4.2. setWaitForTasksToCompleteOnShutdown(boolean)

~~~java
executor.setWaitForTasksToCompleteOnShutdown(true);
~~~

Tùy chọn này quyết định **kiểu final executor shutdown**:

- `false` (mặc định): sau coordinated stop khi áp dụng, đường final shutdown dùng kiểu **immediate/best-effort**, interrupt tasks đang chạy và dọn queue chưa bắt đầu.
- `true`: final shutdown dùng **orderly shutdown**, không yêu cầu interrupt running tasks và cho các tasks đã được nhận trong queue tiếp tục chạy theo hợp đồng executor.

Javadoc Spring 6.1.3 nêu khi bật `true`, executor **không đi qua coordinated lifecycle stop như bình thường**; shutdown và việc đợi (nếu có) chuyển sang destruction step. Về tác động đối với context close, nó gần với một dạng **late-shutdown behavior**, không phải “giữ đúng early stop rồi chỉ thay cách chờ”.

Đừng viết rằng `true` “block tới khi mọi task xong” nếu chưa đặt await-termination. Bản thân lựa chọn này **không nhất thiết chặn** shutdown caller đến lúc work hoàn tất.

### 4.3. setAwaitTerminationSeconds(int)

~~~java
executor.setAwaitTerminationSeconds(30);
~~~

Nó khiến thread thực hiện final `shutdown()` chờ **tối đa 30 giây** cho executor kết thúc, trước khi Spring tiếp tục shutdown các thành phần khác. Nếu work hoàn tất sớm, lời gọi trả sớm; nếu timeout, Spring có thể tiếp tục destruction **dù worker còn tồn tại**.

`awaitTerminationSeconds` không phải “kill timeout”: khi hết 30 giây, Spring không tự bảo đảm task đã bị dừng, và remote I/O càng không được bảo đảm rollback. Trong ví dụ Spring 6.1.3, dùng giá trị 5 giây chỉ là hạn an toàn của demo; production phải dựa trên ngân sách dừng thực tế.

Có overload `setAwaitTerminationMillis(long)` để đặt thời hạn với độ chính xác milli-giây.

**Nếu `waitForTasksToCompleteOnShutdown(true)` và queue còn nhiều task**, cần cho thời gian await đủ theo workload; nếu hạn quá ngắn, Spring vẫn tiếp tục destroy resources trong lúc task đang chạy tiếp ở background, tạo lỗi truy cập datasource/client đã đóng.

### 4.4. Bảng tương tác: không áp dụng một flag đơn lẻ

| Cấu hình | Tiếp nhận task sau context close? | Chiến lược final shutdown | Spring chờ ở destruction? |
| --- | --- | --- | --- |
| `accept=false`, `wait=false`, `await=0` | Thường phát early stop theo implementation/phiên bản | Best-effort immediate sau lifecycle stop | Không có thời hạn await tường minh |
| `accept=false`, `wait=true`, `await=0` | Late shutdown do `wait=true`; không áp early stop như đường default | Orderly: chạy task đã nhận/queued | Không chờ theo await property |
| `accept=false`, `wait=true`, `await=30` | Late shutdown; chú ý resource dependencies | Orderly | Có, tối đa 30 giây |
| `accept=true`, `wait=false`, `await=30` | Có thể tiếp tục nhận tới destruction phase | Immediate tại final shutdown | Có, nhưng không chuyển immediate thành orderly |
| `accept=true`, `wait=true`, `await=30` | Có thể nhận task muộn | Orderly tại final shutdown | Có, tối đa 30 giây |

Bảng mô tả **ý nghĩa cấu hình**, không phải bảo đảm mọi container/application event callback có cùng thời điểm hay tất cả work đều thành công. Đặc biệt với `ThreadPoolTaskExecutor` từ 6.1.4, default early/late có biến thể riêng; xem phần phiên bản dưới đây.

### 4.5. Các flag có thể “ghi đè ý định” của nhau

Một số kết hợp dễ gây hiểu lầm:

- `setAcceptTasksAfterContextClose(true)` nhưng `setWaitForTasksToCompleteOnShutdown(false)`: vẫn có thể nhận task muộn, **nhưng final shutdown có thể interrupt hoặc dọn queue**. Không phải “cho mọi task muộn chạy hết”.
- `setWaitForTasksToCompleteOnShutdown(true)` với `setAwaitTerminationSeconds(0)`: task đã nhận có thể tiếp tục chạy, **nhưng close có thể không đợi** trước khi destroy dependency.
- `setAwaitTerminationSeconds(60)` trong khi default shutdown là immediate: application có thể đợi tối đa 60 giây cho task **không chịu interrupt**, không phải đợi đủ một queue để chạy hết.
- `setPhase(...)` khác đi nhưng bật `wait=true`: không còn hành vi lifecycle stop phối hợp như cấu hình default; đừng chỉ đổi phase rồi tin toàn bộ job được bảo toàn.
- Nếu scheduler giữ **delayed task với hạn vài giờ** và native delayed policy cho phép tiếp tục sau orderly shutdown, await-termination 30 giây sẽ hết hạn trước khi delayed task tới thời điểm chạy.

## 5. Khác biệt quan trọng theo phiên bản Spring

Đây là nơi cần đặc biệt chính xác khi đọc ví dụ trên mạng: code biên dịch ở Spring 6.2 hoặc 6.1.14 **không nhất thiết biên dịch ở Spring 6.1.3**.

| Nhánh phiên bản | Khả năng/đặc điểm shutdown cần chú ý |
| --- | --- |
| Spring 5.3 / 6.0 | Có `setWaitForTasksToCompleteOnShutdown` và `setAwaitTerminationSeconds` từ lâu; **không có** API `setAcceptTasksAfterContextClose` và `setPhase` mới của 6.1 |
| Spring 6.1.0–6.1.3 | `ExecutorConfigurationSupport` là `SmartLifecycle` và nhận `ContextClosedEvent`; `setAcceptTasksAfterContextClose`, `setPhase` đã có; default của base class là early soft shutdown nếu không có late-shutdown flag |
| Spring **6.1.4+** | `ThreadPoolTaskExecutor` được bổ sung `setStrictEarlyShutdown(boolean)`, default **false**, cho phép các late submissions theo cách lenient và vẫn tham gia coordinated lifecycle stop |
| Spring 6.1.4+ khi `strictEarlyShutdown=true` | `ThreadPoolTaskExecutor` phát tín hiệu shutdown sớm khi context close, từ chối task mới; các late-shutdown flags như `acceptTasksAfterContextClose=true` hoặc `waitForTasksToCompleteOnShutdown=true` **ưu tiên** và làm hành vi này chuyển sang shutdown muộn |
| Spring 6.1.x `ThreadPoolTaskScheduler` | Theo mô tả Javadoc, scheduler truyền thống vẫn theo early shutdown model; không áp máy móc default lenient riêng của `ThreadPoolTaskExecutor` sang scheduler |
| Spring 6.2+ | Có thêm API tùy implementation (ví dụ `ThreadPoolTaskScheduler.setTaskDecorator` từ 6.2); phải kiểm tra Javadoc đích khi port code, không giả định các method đã tồn tại ở 6.1.3 |

### 5.1. Ví dụ sai phiên bản: setStrictEarlyShutdown trong Spring 6.1.3

~~~java
// Chỉ dùng với Spring Framework 6.1.4+
// Không copy vào project 6.1.3 ở mục 7.
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setStrictEarlyShutdown(true);
~~~

`setStrictEarlyShutdown(boolean)` có tag `@since 6.1.4`. Nó **không tồn tại trong class của Spring 6.1.3**. Nếu copy vào project 6.1.3, code sẽ báo lỗi biên dịch kiểu “cannot find symbol”.

Trong Spring 6.1.4+, `strictEarlyShutdown=false` (default) khác `setAcceptTasksAfterContextClose(true)`: chế độ strict=false vẫn cho phép late tasks và **tham gia coordinated lifecycle stop**, còn bật `acceptTasksAfterContextClose=true` sẽ **bỏ qua coordinated stop** và chuyển sang shutdown ở destruction phase.

### 5.2. Đọc Javadoc đúng phiên bản thay vì suy đoán

Khi upgrade, kiểm tra ba nơi:

1. `ExecutorConfigurationSupport`: API chung, default, việc xử lý `ContextClosedEvent`, coordinated lifecycle và các override.
2. `ThreadPoolTaskExecutor`: subclass có default riêng hay setter mới `strictEarlyShutdown` không?
3. `ThreadPoolTaskScheduler`: policy của delayed/periodic tasks và việc initialize native scheduler.

Không suy ra một lời gọi chỉ vì IDE autocomplete của môi trường đã nâng version mới hơn POM sản xuất. Đồng thời không khẳng định default của Spring Boot giống y hệt Spring Framework bean tự tạo: Boot có auto-configuration và properties riêng, tùy version và việc bean có bị user override hay không.

## 6. ThreadPoolTaskScheduler: hai chính sách JDK bổ sung

`ThreadPoolTaskScheduler` bọc một `ScheduledThreadPoolExecutor`. Ngoài các flags chung, nó cung cấp policy cho **các lần chạy đã lên lịch**.

### 6.1. setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean)

~~~java
// Fragment trên một ThreadPoolTaskScheduler.
scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
~~~

Default là `false`. Với `false`, các periodic tasks đã đăng ký **không tiếp tục lặp sau khi native scheduler shutdown**. Nếu đặt `true`, `ScheduledThreadPoolExecutor` có thể tiếp tục những periodic registrations đã tồn tại **ngay cả sau `shutdown()`**, khiến executor khó hoặc không bao giờ terminated cho đến khi những lịch đó bị cancel hoặc task kết thúc.

**Đặc biệt:** Đặt `true` kết hợp `waitForTasksToCompleteOnShutdown(true)` và `awaitTerminationSeconds(30)` có thể gây cảm giác “await không hoạt động”, vì periodic task vốn được phép lặp tiếp. Không nên cho scheduler vô hạn chu kỳ tiếp tục chạy qua shutdown nếu mục tiêu thật là dừng JVM gọn.

### 6.2. setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean)

~~~java
// Fragment trên một ThreadPoolTaskScheduler.
scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
~~~

Default là **`true`** ở native `ScheduledThreadPoolExecutor`: một **one-shot delayed task đã được nhận trước shutdown** vẫn có thể được thực thi khi đến hạn, dù executor đã gọi `shutdown()`.

Đặt `false` để hủy/bỏ các delayed tasks còn chờ sau khi orderly shutdown; không bắt đầu những one-shot delayed callbacks chưa đến hạn. Với job “gửi email sau 3 giờ”, default true có thể khiến executor **chưa terminated trong 3 giờ** nếu bạn chờ tất cả delayed tasks. Đừng đánh đồng “scheduled trước shutdown” với “cần thực thi sau shutdown” nếu application dependency đã đóng.

### 6.3. Bảng chính sách native của scheduler

| Loại task đã được nhận | Chính sách ảnh hưởng | Default JDK/Spring scheduler | Nếu đổi thành true/false khác default |
| --- | --- | --- | --- |
| One-shot task đang chờ delay | `executeExistingDelayedTasksAfterShutdown` | **true** | false: không thực thi delayed one-shot còn chờ sau shutdown |
| Periodic task fixed-rate/fixed-delay | `continueExistingPeriodicTasksAfterShutdown` | **false** | true: có thể tiếp tục lặp sau shutdown |
| Task đã cancel và còn trong delayed queue | `removeOnCancelPolicy` | **false** | true: loại nhanh task bị cancel khỏi queue để giảm retention |
| Task đang chạy | `shutdown` vs `shutdownNow` và khả năng xử lý interrupt | Phụ thuộc `waitForTasksToCompleteOnShutdown` | Không bảo đảm hủy remote I/O |
| Task mới sau shutdown | Hợp đồng `ExecutorService` | Bị từ chối | Không có policy native nào biến executor đã shutdown thành còn nhận task |

**Lưu ý về thứ tự:** Nếu Spring chọn **immediate shutdown** bằng `shutdownNow()` ở final phase, những delayed/periodic tasks còn chờ có thể bị hủy/dọn không phụ thuộc giá trị `executeExistingDelayedTasksAfterShutdown=true`. Policy này chủ yếu nói đến **orderly `shutdown()`**, không phải một phép khôi phục queue sau `shutdownNow()`.

### 6.4. Policy cho scheduler khác với executor thường

Đừng tìm `setQueueCapacity` và `setMaxPoolSize` của `ThreadPoolTaskExecutor` trên scheduler rồi coi nó cùng backend. `ThreadPoolTaskScheduler` dùng `ScheduledThreadPoolExecutor` có delayed queue về cơ bản không giới hạn và định lượng bằng `setPoolSize`. Ngược lại, `ThreadPoolTaskExecutor` dùng `ThreadPoolExecutor` với queue capacity và core/max worker tùy cấu hình.

`setRemoveOnCancelPolicy(true)` hữu ích khi ứng dụng hủy nhiều lịch trước khi đến hạn, nhưng **không thay thế** giới hạn số job được schedule hoặc kho lưu trữ bền vững.

## 7. Ví dụ Spring Framework 6.1.3: so sánh orderly và immediate shutdown

Ví dụ này dùng API **có trong Spring Framework 6.1.3**. Chúng ta chủ động gọi `ThreadPoolTaskExecutor.shutdown()` để **cô lập hành vi final shutdown** khỏi việc Spring ApplicationContext gửi events và sắp thứ tự các bean khác.

Thí nghiệm thiết kế để tránh đoán thread scheduling:

- Một worker được giữ tại `CountDownLatch.release.await()`.
- Task thứ hai được nhận và nằm trong queue vì pool có đúng một worker.
- Với `waitForTasksToCompleteOnShutdown(true)`, shutdown thread phát orderly shutdown rồi main mở latch: task đang chạy xong và task queued được chạy.
- Với `waitForTasksToCompleteOnShutdown(false)`, final shutdown cố interrupt worker và dọn queue, task queued không chạy.
- `awaitTerminationSeconds(5)` giúp `shutdown()` chờ có giới hạn; nó **không** thay đổi lựa chọn orderly/immediate.

### 7.1. Maven project độc lập

Cấu trúc:

~~~text
spring-shutdown-options/
├── pom.xml
└── src/main/java/demo/
    └── SpringShutdownOptionsDemo.java
~~~

`pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-shutdown-options</artifactId>
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
                    <mainClass>demo.SpringShutdownOptionsDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 7.2. SpringShutdownOptionsDemo.java

~~~java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class SpringShutdownOptionsDemo {

    private static ThreadPoolTaskExecutor newExecutor(boolean graceful) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix(graceful ? "graceful-" : "immediate-");
        executor.setWaitForTasksToCompleteOnShutdown(graceful);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize(); // Tự quản lý bean trong demo độc lập.
        return executor;
    }

    private static void awaitShutdownSignal(ThreadPoolExecutor nativePool)
            throws Exception {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(5);

        while (!nativePool.isShutdown()
                && System.nanoTime() < deadline) {
            Thread.yield();
        }

        if (!nativePool.isShutdown()) {
            throw new IllegalStateException("shutdown was not initiated");
        }
    }

    private static void runCase(boolean graceful) throws Exception {
        ThreadPoolTaskExecutor executor = newExecutor(graceful);
        ThreadPoolExecutor nativePool = executor.getThreadPoolExecutor();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        AtomicBoolean queuedRan = new AtomicBoolean(false);

        try {
            Future<?> running = executor.submit(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not start");
            }

            Future<?> queued = executor.submit(
                    () -> queuedRan.set(true));

            if (graceful) {
                // shutdown() sẽ chờ; dùng thread riêng để main mở latch.
                Thread closer = new Thread(executor::shutdown);
                closer.start();

                try {
                    // Xác nhận orderly shutdown đã bắt đầu
                    // trước khi mở worker để xử lý queue.
                    awaitShutdownSignal(nativePool);
                } finally {
                    release.countDown();
                }

                closer.join(5_000);
                if (closer.isAlive()) {
                    throw new IllegalStateException(
                            "graceful shutdown did not return");
                }

                running.get(5, TimeUnit.SECONDS);
                queued.get(5, TimeUnit.SECONDS);

                System.out.println("gracefulQueuedRan=" + queuedRan.get());
                System.out.println("gracefulInterrupted=" + interrupted.get());
                System.out.println("gracefulTerminated="
                        + nativePool.isTerminated());
            } else {
                // shutdown() chọn immediate/best-effort và interrupt.
                executor.shutdown();

                if (!finished.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "worker did not finish");
                }

                System.out.println("immediateQueuedRan=" + queuedRan.get());
                System.out.println("immediateInterrupted=" + interrupted.get());
                System.out.println("immediateQueuedCancelled="
                        + queued.isCancelled());
                System.out.println("immediateTerminated="
                        + nativePool.isTerminated());
            }
        } finally {
            release.countDown();
            if (!nativePool.isShutdown()) {
                executor.shutdown();
            }
            if (!nativePool.awaitTermination(5, TimeUnit.SECONDS)) {
                nativePool.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        runCase(true);
        runCase(false);
    }
}
~~~

Chạy từ thư mục project:

~~~bash
mvn -q compile exec:java
~~~

**Stdout của chương trình trong điều kiện worker phản hồi với interruption như ví dụ:**

~~~text
gracefulQueuedRan=true
gracefulInterrupted=false
gracefulTerminated=true
immediateQueuedRan=false
immediateInterrupted=true
immediateQueuedCancelled=true
immediateTerminated=true
~~~

### 7.3. Những gì ví dụ chứng minh, và những gì không

- Hai cấu hình đều dùng cùng một pool size, queue capacity và await termination 5 giây. **Chỉ** `waitForTasksToCompleteOnShutdown` thay đổi.
- Với `graceful=true`, `shutdown()` gọi đường orderly. Worker bị latch giữ chưa thể kết thúc; task thứ hai chưa chạy. Khi main phát hiện executor đã shutdown và mở latch, worker hoàn tất task đầu rồi lấy task thứ hai trong queue. Vì vậy `queuedRan=true`, `interrupted=false`.
- Với `graceful=false`, `shutdown()` gọi đường best-effort immediate. Worker đang chờ `release.await()` có xử lý `InterruptedException` nên thoát, còn task queued không chạy. Spring hủy task `Future` còn trong queue ở shutdown path này, nên `queued.isCancelled()` là true.
- `awaitTerminationSeconds(5)` khiến lệnh `shutdown()` đợi executor chuyển sang terminated trong giới hạn; không được hiểu là một bảo đảm mọi task sẽ hoàn thành nếu code không hợp tác.
- Cả hai trường hợp đều kết thúc pool và dọn tài nguyên trong `finally`. Vì chỉ `main` in kết quả và các latch quy định thứ tự, stdout không phụ thuộc vào tên worker hay thứ tự log của thread.

**Giới hạn quan trọng:** Đây là ví dụ **gọi trực tiếp final shutdown** trên executor đã `initialize()`, không phải phép kiểm chứng đầy đủ thứ tự `ContextClosedEvent` / `SmartLifecycle` / `@PreDestroy` của một ứng dụng Spring có nhiều bean. Để test context close thực, cần dựng các bean với lifecycle khác nhau và xác minh tương tác giữa chúng.

Đoạn code chạy trong JDK 17+ và dùng Spring Framework 6.1.3, **không dùng `setStrictEarlyShutdown`** vì API đó chưa tồn tại ở phiên bản này.

## 8. Ví dụ Java 17: delayed và periodic task sau shutdown

Ngay cả với orderly shutdown, scheduler cần chính sách riêng cho task chờ thời điểm tương lai. Chương trình Java thuần dưới đây dùng `ScheduledThreadPoolExecutor` để chứng minh:

1. Delayed one-shot task đã được nhận vẫn chạy sau `shutdown()` nếu policy true.
2. Nếu policy false, delayed one-shot task đang chờ bị cancel.
3. Periodic registration có thể tiếp tục phát tick sau `shutdown()` nếu bật policy tương ứng, và phải hủy rõ ràng để executor terminated.

Lưu thành `ScheduledShutdownPoliciesDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScheduledShutdownPoliciesDemo {

    private static void delayedAllowed() throws Exception {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(1);

        AtomicBoolean executed = new AtomicBoolean(false);

        try {
            scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);

            ScheduledFuture<?> future = scheduler.schedule(
                    () -> executed.set(true),
                    50, TimeUnit.MILLISECONDS);

            scheduler.shutdown(); // Không nhận task mới.
            future.get(5, TimeUnit.SECONDS);

            System.out.println("delayedAllowed=" + executed.get());
        } finally {
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static void delayedDropped() throws Exception {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(1);

        AtomicBoolean executed = new AtomicBoolean(false);

        try {
            scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

            ScheduledFuture<?> future = scheduler.schedule(
                    () -> executed.set(true),
                    1, TimeUnit.DAYS);

            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("scheduler still active");
            }

            System.out.println("delayedCancelled=" + future.isCancelled());
            System.out.println("delayedExecuted=" + executed.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static void periodicContinues() throws Exception {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(1);

        CountDownLatch two = new CountDownLatch(2);

        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
        ScheduledFuture<?> periodic = scheduler.scheduleAtFixedRate(
                two::countDown, 50, 30, TimeUnit.MILLISECONDS);

        try {
            scheduler.shutdown();

            if (!two.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "periodic task did not continue");
            }

            System.out.println("periodicContinued=true");
        } finally {
            periodic.cancel(false); // Cần hủy, nếu không lịch có thể lặp mãi.
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    public static void main(String[] args) throws Exception {
        delayedAllowed();
        delayedDropped();
        periodicContinues();
    }
}
~~~

Chạy:

~~~bash
javac ScheduledShutdownPoliciesDemo.java
java ScheduledShutdownPoliciesDemo
~~~

**Stdout khi các task hoàn tất trước hạn kiểm thử:**

~~~text
delayedAllowed=true
delayedCancelled=true
delayedExecuted=false
periodicContinued=true
~~~

### Giải thích kết quả

- `delayedAllowed=true`: task được nhận **trước** `shutdown`, policy delayed-task cho phép chạy vào thời điểm hẹn dù executor đã ngừng nhận task mới.
- `delayedCancelled=true`: task một lần bị hẹn sau một ngày, policy false khiến nó không được thực thi khi orderly shutdown. Code không chờ một ngày.
- `delayedExecuted=false`: callback chưa chạy trước khi được hủy.
- `periodicContinued=true`: policy periodic=true cho phép ít nhất hai callbacks xảy ra sau khi code đã gọi `shutdown()`. `finally` chủ động `cancel(false)` để tránh executor giữ lịch lặp vô hạn.

**Đừng nhầm policy của chương trình Java thuần với toàn bộ đường dừng Spring context.** Nếu Spring ở final destruction chọn `shutdownNow()`, các delayed callbacks đang chờ có thể bị dọn dù policy cho phép tiếp tục sau orderly shutdown. Đó là lý do phải đọc **cả hai tầng**.

## 9. Cấu hình Spring bean thực tế theo mục tiêu vận hành

### 9.1. Mục tiêu A: cố hoàn tất task đã nhận trước khi dừng

~~~java
// Fragment cấu hình Spring 6.1.3.
@Bean("businessExecutor")
public ThreadPoolTaskExecutor businessExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.setThreadNamePrefix("business-");
    return executor;
}
~~~

Phù hợp khi task đã nhận cần chạy hết, có thể giải phóng tài nguyên trong vòng hợp lý và dependency (database, remote client) còn sống đủ lâu. Tuy nhiên:

- Bật `wait=true` khiến đường shutdown nghiêng về **late shutdown**, làm thay đổi coordinated lifecycle stop.
- Queue có thể dài hơn sức xử lý trong 60 giây; nếu timeout, work vẫn có thể chạy sau đó.
- Nếu một task không bao giờ hoàn tất, thời hạn await kết thúc nhưng executor chưa chắc đã terminated.
- Với task có side effects quan trọng, không dùng memory queue làm thay thế persistence/transactional outbox.

### 9.2. Mục tiêu B: ngừng nhận việc mới sớm, không kéo dài dừng JVM

Với Spring 6.1.3, hiểu default `ExecutorConfigurationSupport` nhận `ContextClosedEvent` và chiến lược final shutdown khi `waitForTasksToCompleteOnShutdown=false`. Tránh một cấu hình trông “nhanh” nhưng lại chứa blocking I/O không phản hồi interrupt.

~~~java
// Fragment Spring 6.1.3, không dùng API ra đời ở 6.1.4.
@Bean("shortExecutor")
public ThreadPoolTaskExecutor shortExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(20);
    executor.setWaitForTasksToCompleteOnShutdown(false);
    executor.setAwaitTerminationSeconds(10);
    return executor;
}
~~~

Bước final shutdown thử interrupt và dọn queue. `awaitTerminationSeconds(10)` chỉ chờ đến 10 giây; nó **không** giúp task bất hợp tác dừng được. Cần timeout thật của JDBC/HTTP, hỗ trợ cancellation và thiết kế rollback/compensation phù hợp.

### 9.3. Mục tiêu C: ThreadPoolTaskScheduler không để job hẹn ngày mai giữ JVM

~~~java
@Bean("taskScheduler")
public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("schedule-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    scheduler.setAwaitTerminationSeconds(30);
    return scheduler;
}
~~~

Đây là cấu hình chọn **orderly shutdown** cho work đang chạy, nhưng không cố thực thi các one-shot delayed tasks chưa đến hạn và không cho periodic registrations lặp sau shutdown. Nó tránh chờ các lịch hẹn xa ở tương lai.

**Giới hạn:** Nếu job delayed là nghiệp vụ không được mất, policy false đồng nghĩa các lần chờ trong RAM có thể bị bỏ khi shutdown. Khi đó phải **lưu job state bền vững** và có cơ chế resume/reconciliation sau restart, không chỉ chỉnh scheduler flag.

### 9.4. Mục tiêu D: chấp nhận submission từ callback dọn dẹp

~~~java
// Fragment: dùng cẩn thận với Spring 6.1.3.
executor.setAcceptTasksAfterContextClose(true);
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(45);
~~~

Cấu hình có thể cho các thành phần gửi task muộn trong stop/destroy callback, đồng thời cố chạy những việc đã nhận. Nhưng “có thể submit” không đồng nghĩa dependency còn sẵn sàng để work chạy. Đặc biệt, đừng để callback destroy của client cố gửi một task sử dụng chính client ấy sau khi client đã đóng.

Thường tốt hơn là **không nộp thêm công việc quan trọng khi context đang hủy**: đánh dấu application unready, ngừng intake, drain trước deadline, bảo đảm state bền vững, rồi mới destroy resources.

## 10. Khi await-termination hết hạn: điều gì vẫn có thể xảy ra?

Giả sử cấu hình:

~~~java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(20);
~~~

Khi shutdown:

1. Executor không nhận thêm task ở bước final shutdown.
2. Các task đã nhận có thể tiếp tục chạy, bao gồm cả queue.
3. Spring đợi tối đa 20 giây ở shutdown method.
4. Nếu sau 20 giây executor chưa terminated, Spring có thể tiếp tục đóng những bean/resources khác.
5. Task còn chạy có thể gặp `DataSource` đã shutdown, socket pool đã bị đóng hoặc client không còn hợp lệ.
6. Non-daemon worker có thể khiến JVM còn sống sau khi web server đã ngừng; hoặc container orchestrator gửi SIGKILL khi hết grace period.

**Đây là lý do `awaitTerminationSeconds` phải gắn với shutdown budget của toàn ứng dụng**, chứ không phải đặt 20 giây vì con số đó “nghe hợp lý”. Tổng shutdown gồm draining traffic, lifecycle phases, multiple executors, transaction cleanup và thời hạn termination của container/k8s.

Nếu deadline bên ngoài ngắn hơn thời gian cần chạy xong toàn bộ queue, bảo đảm “mọi task sẽ chạy hết” là bất khả thi nếu không có persistence/recovery.

### 10.1. InterruptedException không phải dừng mọi I/O

Một task có thể phản hồi interrupt tại `sleep`, `wait`, `BlockingQueue.take`, `CountDownLatch.await`, nhưng không phải mọi JDBC driver/SDK chặn I/O đều hủy đúng theo interrupt. Vì vậy:

- Thiết lập HTTP connect/read/request timeout và JDBC query/transaction timeout ở nơi thật.
- Với công việc có side effects, dùng idempotency key, trạng thái “unknown” và compensation/reconciliation.
- Khi bắt `InterruptedException`, cleanup cần thiết và thường khôi phục interrupt flag bằng `Thread.currentThread().interrupt()`.
- Không bắt rồi nuốt interrupt để một vòng lặp chạy mãi.
- Đo worker active, queue depth và in-flight operations sau shutdown.

## 11. Những tình huống vận hành dễ làm shutdown treo

### 11.1. Deadlock khi chờ task trong cùng single-thread pool

Một scheduled/async task đang chạy trên pool một worker lại gọi một `Future.get()` để chờ task thứ hai **được nộp vào chính pool đó**. Nếu không còn worker khác, task thứ hai nằm trong queue và không thể chạy; task đầu chờ mãi.

Bật `waitForTasksToCompleteOnShutdown(true)` không sửa deadlock này; nó có thể khiến shutdown càng khó kết thúc. Cần thiết kế asynchronous composition đúng, tránh synchronous wait trong worker same pool hoặc tách executor/giới hạn concurrency.

### 11.2. Lịch lặp được cho phép tiếp tục sau shutdown

Với `ThreadPoolTaskScheduler`:

~~~java
scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
scheduler.setWaitForTasksToCompleteOnShutdown(true);
~~~

Một periodic registration có thể **không có thời điểm hoàn thành tự nhiên**. Khi orderly shutdown đã khởi động, scheduler vẫn có thể tiếp tục lặp. Nếu đang chờ tất cả tasks terminated, không có lý do để nó tự kết thúc trừ khi cancel lịch hoặc xảy ra điều kiện stop khác.

Thay vì cố chờ “tới lúc periodic task chạy hết”, hãy ngừng/huỷ registration và thiết kế xử lý riêng các one-shot in-flight tasks.

### 11.3. Delayed one-shot hẹn quá xa

`executeExistingDelayedTasksAfterShutdownPolicy=true` là default của scheduler. Một callback hẹn sau nhiều giờ có thể giữ executor chưa terminated sau orderly shutdown. Đặt false có thể giải phóng queue, nhưng cần job persistence nếu đây là lời hứa nghiệp vụ.

### 11.4. Destructor gửi task vào executor đã từ chối nhận

Nếu một bean trong `@PreDestroy` gửi việc vào executor đã nhận early shutdown, caller có thể gặp `TaskRejectedException`. Bắt lỗi ngay tại `execute/submit` là cần thiết; chỉ thêm `future.exceptionally(...)` không giúp nếu future chưa được trả về.

~~~java
// Fragment, businessExecutor là bean AsyncTaskExecutor.
try {
    businessExecutor.execute(this::flushCounters);
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Áp fallback đồng bộ/ghi ra kho bền vững nếu hợp đồng cho phép.
    handleFlushNotScheduled(ex);
}
~~~

`flushCounters` và `handleFlushNotScheduled` là phương thức ứng dụng chưa định nghĩa. Không tự gọi `execute` với task quan trọng trong `@PreDestroy` rồi tin Spring sẽ luôn nhận.

### 11.5. @Async và @Scheduled có vòng đời khác nhau

Một `@Scheduled` method chạy trên `ThreadPoolTaskScheduler` có thể gọi service `@Async` chạy trên `ThreadPoolTaskExecutor`. Khi shutdown, scheduler và executor có thể dừng ở các phase/chiến lược khác nhau. Nếu scheduler callback vẫn tạo work nhưng executor đã từ chối nhận, job có thể lỗi/được log và kỳ xử lý bị bỏ.

Đặt thứ tự ngừng phù hợp, hoặc dùng một queue/job store có owner và trạng thái bền vững; không coi một cấu hình của scheduler là đủ kiểm soát toàn bộ worker chain.

## 12. ThreadPoolTaskScheduler: mức shutdown của JDK và mức lifecycle của Spring

Một điểm tinh tế: `ThreadPoolTaskScheduler.scheduleAtFixedRate(...)` thường ủy thác native periodic scheduling vào `ScheduledThreadPoolExecutor`; còn `schedule(Runnable, Trigger)` dựa trên trigger logic có thể có wrapper tính lần kế tiếp sau callback. Khi shutdown, registration và cách Spring quản lý cancellation có thể khác đường native scheduler.

Vì vậy:

- Các policy `setContinueExistingPeriodicTasksAfterShutdownPolicy` và `setExecuteExistingDelayedTasksAfterShutdownPolicy` mô tả hành vi **native ScheduledThreadPoolExecutor** mà Spring bao.
- Không suy từ một policy native rằng mọi `CronTrigger` hoặc `Trigger` tùy chỉnh tự động có cùng cách “tiếp tục sau shutdown” và cùng lifecycle.
- Không dùng policy native để thay `ScheduledFuture.cancel(...)` cho những job động cần hủy riêng.
- Spring context close còn có coordinated lifecycle stop và bean destruction; nếu bạn muốn delayed task “chạy sau context close”, hãy kiểm tra xem dependencies của callback còn sống không và có hợp lý để làm vậy không.

### 12.1. Stop @Scheduled không nhất thiết stop downstream work

Nếu callback `@Scheduled` gửi `CompletableFuture.supplyAsync(...)` sang common pool, việc shutdown Spring scheduler **không tự shutdown ForkJoinPool.commonPool()` hoặc hủy work đã gửi vào executor khác. Đừng đánh đồng `ScheduledFuture` đã cancelled với remote operation đã hoàn tất.

Nếu cần end-to-end stop, phải có owner rõ ràng của mọi executor, job ID, cancellation policy và timeout ở tầng I/O.

## 13. Cách test shutdown mà không phải đợi lâu hay đoán timing

### 13.1. Hai chiều độc lập cần assert

Test shutdown tốt cần ít nhất hai quan sát:

1. **Future/executor state:** `isShutdown`, `isTerminated`, `Future.isCancelled`, queue size, rejection.
2. **Work thực:** task có đi tới `finally` không, bị interrupt không, queued task có chạy không, resources đã đóng chưa.

Chỉ assert `context.isActive() == false` không chứng minh mọi task đã hoàn tất. Ngược lại, task hoàn tất không tự chứng minh executor đã ngừng nhận submission mới.

### 13.2. Kịch bản kiểm thử tiêu chuẩn

| Test | Quan sát |
| --- | --- |
| Orderly shutdown và một running task | Task được mở latch, chạy tới cuối, không bị yêu cầu interrupt |
| Orderly shutdown và một queued task | Queued task vẫn được chạy trước termination |
| Immediate shutdown và running task chờ interruptible API | Task nhận interrupt, cleanup hợp tác |
| Immediate shutdown và queued Future | Future bị cancel hoặc được trả trong danh sách unstarted tasks, không có side effect |
| `awaitTerminationSeconds` quá ngắn | Close có thể trả về trong khi worker còn active |
| `acceptTasksAfterContextClose=false` | Task nộp **sau early signal** bị từ chối tùy subclass/version |
| `acceptTasksAfterContextClose=true` | Submission muộn có thể được nhận tới destruction, cần kiểm tra dependency sống |
| Spring 6.1.3 | Không gọi `setStrictEarlyShutdown`; xác minh default early behavior |
| Spring 6.1.4+ | Test cả `setStrictEarlyShutdown(false)` và `true`; chú ý late flags override |
| Scheduler delayed policy=true | Delayed one-shot còn chờ có thể thực thi sau orderly shutdown |
| Scheduler delayed policy=false | Task chưa đến hạn bị hủy sau orderly shutdown |
| Scheduler periodic policy=true | Periodic task có thể tiếp tục; cần cancel để terminated |
| Scheduler periodic policy=false | Periodic registrations không tiếp tục sau orderly shutdown |
| Multiple replicas | Không nhầm shutdown cục bộ với việc cluster ngừng một job toàn hệ thống |

### 13.3. Quan sát bằng latch, không dùng sleep để “đoán worker”

`CountDownLatch` cho phép test giữ running task ở một điểm cụ thể, bảo đảm queued task chưa bắt đầu. Ví dụ phần 7 dùng `started.await(...)`, `release.countDown()` và `awaitTermination` để test khi shutdown diễn ra.

Tránh `Thread.sleep(50)` rồi assert “queue chắc chắn có đúng một task”, vì scheduler của CI có thể chậm và máy nhiều cores có thể tạo thứ tự khác. Nếu dùng timeout trong test, đó là **giới hạn an toàn**, không phải khẳng định scheduler chính xác đến mili giây.

### 13.4. Đừng chỉ nhìn stdout trong ứng dụng thật

Trong production, log nên cho biết:

- Thời điểm ingress/readiness chuyển sang stopping.
- Tên bean executor/scheduler, Spring Framework/Boot version.
- `activeCount`, queue depth và số in-flight jobs khi bắt đầu drain.
- Số task hoàn tất, bị interrupt, bị cancel, chưa được nhận do rejection.
- Thời gian shutdown từng phase, số lần await timeout và số worker còn sống.
- Tình trạng connection/database/transaction và những kỳ job chưa xử lý.
- Các hoạt động recovery sau restart, duplicate attempts và idempotency outcomes.

Không log password, security token, raw request body hay nội dung nhạy cảm để debug vòng đời.

## 14. Checklist cấu hình theo từng trường hợp

### 14.1. Job ngắn, không bắt buộc hoàn tất

Một số polling/refresh cache có thể bỏ kỳ khi shutdown. Khi đó có thể chọn dừng sớm, interrupt/dọn queue ở final shutdown và đặt thời hạn await nhỏ. Nhưng vẫn cần đảm bảo cleanup không làm lộ resource.

### 14.2. Việc quan trọng, đã nhận là phải có kết quả

Không chỉ bật `wait=true` trên memory executor. Cần lưu job vào durable store, ghi business key, idempotency, retry/recovery; khi shutdown ngừng intake, drain trong ngân sách cho phép và resume phần còn lại sau restart.

### 14.3. Periodic maintenance task

Ngừng tạo nhịp mới khi context đóng; không bật periodic-after-shutdown vô thời hạn trừ khi có lý do rất cụ thể. Những job đang chạy phải có deadline/interrupt policy, không giữ connection quá lâu.

### 14.4. Delayed one-shot nhiệm vụ có hạn tương lai

Nếu người dùng đã được hứa “gửi email lúc 08:00 ngày mai”, đừng đặt lời hứa duy nhất trong delayed queue RAM của scheduler và kỳ vọng nó sống qua restart. Dùng persistent job store/queue, hoặc lưu deadline và quét lại khi startup.

### 14.5. Shutdown budget

Tính tổng budget của orchestrator/OS: readiness drain, HTTP shutdown, các lifecycle phases, executor await và external resource cleanup. Không đặt năm executors mỗi cái `awaitTerminationSeconds(60)` rồi nghĩ tổng thời gian dừng chắc chắn chỉ 60 giây.

## 15. Những hiểu lầm thường gặp

1. **“`setWaitForTasksToCompleteOnShutdown(true)` làm Spring chờ mọi task xong trước khi destroy bean khác.”** Không đủ; cần `awaitTerminationSeconds` để block ở destruction và cần cân nhắc lifecycle.
2. **“`setAwaitTerminationSeconds(30)` tự chọn graceful shutdown.”** Sai. Nó chỉ đặt giới hạn **chờ**, không đổi immediate sang orderly.
3. **“`shutdownNow()` giết mọi thread ngay.”** Sai. Nó thử interrupt, nhiều I/O không hợp tác.
4. **“Context close và gọi trực tiếp `executor.shutdown()` là cùng một test.”** Không; context close còn event, lifecycle phases và bean destruction.
5. **“`acceptTasksAfterContextClose(true)` bảo đảm mọi task nộp muộn chạy hết.”** Không; final shutdown vẫn có thể immediate hoặc await timeout.
6. **“`wait=true` không ảnh hưởng early shutdown.”** Sai; Spring Javadoc nêu tác động late-shutdown/bỏ coordinated stop path.
7. **“Cứ đổi `setPhase` là mọi task queued sẽ được giữ.”** Sai; phase không thay chính sách queue của final shutdown.
8. **“`setStrictEarlyShutdown` có trong Spring 6.1.3.”** Sai; phương thức xuất hiện từ **6.1.4**.
9. **“ThreadPoolTaskExecutor 6.1.4+ mặc định strict early giống scheduler.”** Sai; setter mới default false cho phép late tasks theo coordinated stop.
10. **“Scheduler mặc định hủy mọi delayed task khi gọi orderly shutdown.”** Sai; delayed one-shot policy mặc định true.
11. **“Scheduler mặc định tiếp tục mọi periodic task sau shutdown.”** Sai; periodic policy mặc định false.
12. **“Cho periodic tasks tiếp tục nghĩa là await sẽ tự trả sớm sau đúng một tick.”** Sai; periodic có thể chạy vô hạn, ngăn terminated.
13. **“`awaitTerminationSeconds` hết giờ sẽ tự `shutdownNow()` hoặc kill process.”** Không có bảo đảm vậy.
14. **“Spring shutdown rồi thì mọi CompletableFuture/common pool tự dừng.”** Sai; work ở executor khác có lifecycle riêng.
15. **“`cancel(true)` bảo đảm payment remote bị hoàn tác.”** Sai; cancellation không là rollback.
16. **“Bật `acceptTasksAfterContextClose` luôn tốt để tránh TaskRejectedException.”** Không; nó có thể cho work mới phụ thuộc bean/resource đã đóng.
17. **“Chỉ cần một annotation @Scheduled, job chắc chạy đúng một lần toàn cluster.”** Sai; mỗi replica có lịch cục bộ.
18. **“ThreadPoolTaskExecutor và ThreadPoolTaskScheduler có các queue/policy giống hệt nhau.”** Sai; chúng bao các JDK executor khác nhau.
19. **“Javadoc bản mới mô tả chính xác API của mọi bản cũ.”** Sai; phải đối chiếu bản đang chạy.
20. **“Trong test, thấy isShutdown=true tức là isTerminated=true.”** Sai; shutdown và termination là hai trạng thái khác nhau.

## 16. Tự kiểm tra

**Câu 1.** `ExecutorService.shutdown()` khác `shutdownNow()` ra sao?  
**Trả lời:** `shutdown()` từ chối task mới và cho tasks đã nhận có cơ hội hoàn tất; `shutdownNow()` cố interrupt running tasks và dừng queued tasks, không bảo đảm dừng mọi computation.

**Câu 2.** `setWaitForTasksToCompleteOnShutdown(true)` có tự block tới lúc xong tất cả không?  
**Trả lời:** Không nhất thiết. Nó chọn orderly shutdown; nếu muốn block khi bean destroy, đặt `setAwaitTerminationSeconds(...)` phù hợp.

**Câu 3.** `setAwaitTerminationSeconds(60)` đi cùng `wait=false` có buộc queue chạy hết không?  
**Trả lời:** Không. Final shutdown vẫn có thể immediate; timeout chỉ chờ executor kết thúc hoặc tasks không chịu interrupt.

**Câu 4.** `acceptTasksAfterContextClose=true` có làm executor trải qua cùng coordinated stop như mặc định không?  
**Trả lời:** Theo Javadoc Spring 6.1.3, không; nó cho phép late submissions và chuyển shutdown chính sang destruction phase.

**Câu 5.** Tại sao `setStrictEarlyShutdown(true)` không biên dịch với Spring 6.1.3?  
**Trả lời:** API đó được thêm vào `ThreadPoolTaskExecutor` ở Spring **6.1.4**.

**Câu 6.** Từ Spring 6.1.4, default `strictEarlyShutdown` của `ThreadPoolTaskExecutor` là gì?  
**Trả lời:** `false`, cho phép tiếp nhận late tasks theo cách lenient và vẫn phối hợp lifecycle stop; khác việc đặt `acceptTasksAfterContextClose=true`.

**Câu 7.** `ThreadPoolTaskScheduler` default có chạy delayed one-shot sau orderly shutdown không?  
**Trả lời:** Policy `executeExistingDelayedTasksAfterShutdown` mặc định **true**, nên one-shot delayed task đã nhận có thể chạy sau shutdown khi tới hạn.

**Câu 8.** Scheduler default có giữ periodic task chạy mãi sau shutdown không?  
**Trả lời:** Không; `continueExistingPeriodicTasksAfterShutdown` mặc định false.

**Câu 9.** Vì sao không nên bật periodic-after-shutdown=true kèm await mà không có cách cancel?  
**Trả lời:** Periodic task có thể tiếp tục mãi, executor không chuyển sang terminated và await có thể hết hạn.

**Câu 10.** Khi `awaitTerminationSeconds` hết hạn, Spring có chắc work đã dừng không?  
**Trả lời:** Không. Worker không chịu interrupt có thể còn hoạt động, và Spring có thể tiếp tục destroy resources khác.

**Câu 11.** Có nên nhét delayed job quan trọng hẹn nhiều giờ vào một scheduler RAM để chịu được restart không?  
**Trả lời:** Không; dùng durable job state/queue và resume/reconciliation theo business key.

**Câu 12.** `@Scheduled` method nộp task sang `@Async` rồi return; shutdown scheduler đã dừng task @Async chưa?  
**Trả lời:** Chưa chắc. Đây là hai executors/lifecycle riêng; phải quản lý downstream worker và hoàn tất work thật.

**Câu 13.** Tại sao kiểm tra một executor Spring vừa gọi `shutdown()` trực tiếp không đủ khẳng định hành vi khi đóng toàn bộ context?  
**Trả lời:** Vì context close phát event, điều phối `SmartLifecycle` và destroy nhiều bean. Những bước ấy có policy, phase, timeout và dependencies riêng.

**Câu 14.** Nếu một task bỏ qua interrupt trong một SDK call 120 giây, đặt await=5 giây có làm nó biến mất không?  
**Trả lời:** Không. Nó chỉ giới hạn thời gian chờ; task/remote call vẫn có thể đang chạy sau đó.

## 17. Tài liệu chính thức

- [Spring Framework 6.1.3 — ExecutorConfigurationSupport](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html): `acceptTasksAfterContextClose`, `waitForTasksToCompleteOnShutdown`, `awaitTerminationSeconds`, `setPhase` và cách Spring context đóng.
- [Spring Framework 6.1.3 — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): backend `ThreadPoolExecutor`, các tham số pool/queue và API có trong phiên bản.
- [Spring Framework 6.1.14 — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): `setStrictEarlyShutdown` với tag `@since 6.1.4`, default false và khác biệt với các late shutdown flags.
- [Spring Framework 6.1.x — ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): native delayed/periodic policies, default, pool size và lịch.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): lifecycle scheduling và phân biệt executor/scheduler.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): hợp đồng `shutdown`, `shutdownNow` và `awaitTermination`.
- [Java SE 17 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html): delayed/periodic shutdown policies, remove-on-cancel và queue behavior.
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html): worker, queue, rejection và lifecycle executor.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-shutdown-007">Ngắt tác vụ đang chạy và hủy công việc chưa bắt đầu</a>

<details>
<summary>Xem chi tiết</summary>

# Ngắt tác vụ đang chạy và hủy công việc chưa bắt đầu

## 1. Đặt vấn đề: shutdown là một giao thức, không phải nút tắt tức thì

Một ứng dụng Spring thường có nhiều công việc chạy ngoài request thread: `@Async` methods, `TaskExecutor` tasks, `@Scheduled` jobs, consumers, xử lý batch và các tác vụ dùng `CompletableFuture`. Khi application cần **dừng**, hai nhóm công việc phải được xử lý khác nhau:

1. **Đã bắt đầu chạy:** một worker đang xử lý tác vụ. Có thể yêu cầu nó **ngắt** bằng Java thread interruption hoặc gửi tín hiệu dừng hợp tác, nhưng **không bảo đảm nó sẽ dừng ngay**.
2. **Đã được tiếp nhận nhưng chưa bắt đầu:** công việc nằm trong hàng đợi của executor. Có thể **hủy trước khi chạy**, loại khỏi queue hoặc không thực hiện khi shutdown, nhưng phải quản lý `Future` và trạng thái nghiệp vụ tương ứng.

Nếu chỉ gọi `shutdownNow()` rồi in "đã dừng tất cả", ứng dụng dễ có dữ liệu mất, tác vụ không biết trạng thái, `Future.get()` chờ vô hạn hoặc side effect đang dang dở.

~~~text
                   Executor / TaskExecutor
                 +--------------------------+
submit(job A) -->| worker-1: A đang chạy   |--- interrupt request --->
submit(job B) -->| queue: B chưa bắt đầu   |--- remove / cancel ----->
submit(job C) -->| queue: C chưa bắt đầu   |--- remove / cancel ----->
                 +--------------------------+
                            |
                  STOP / SHUTDOWN
                            |
            +---------------+----------------+
            |                                |
      Running task A                  Queued tasks B,C
      cần hợp tác để dừng            có thể không bao giờ chạy
      hoặc hoàn tất an toàn          phải đánh dấu cancelled/abandoned
~~~

**Ba phát biểu không tương đương:**

- "Tôi đã gọi `Future.cancel(true)`" — đã **yêu cầu hủy**, có thể đã gửi interrupt.
- "`Future.isCancelled() == true`" — **handle** ở trạng thái canceled, không chứng minh worker đã ngừng side effects.
- "Tác vụ đã kết thúc và tài nguyên được giải phóng" — phải kiểm tra exit/completion, transaction outcome và cleanup thực tế.

Bài thuộc nhóm **Vòng đời và dừng** trong **Spring Framework – concurrency**, giải thích các primitive của JDK, cấu hình shutdown của Spring 6.1, code mẫu Java 17 chạy được, chiến lược graceful → forced, và cách không làm mất công việc quan trọng.

## 2. Các trạng thái phải phân biệt

| Trạng thái | Ý nghĩa | Hành động khi dừng |
| --- | --- | --- |
| Chưa được nhận | Producer đang chuẩn bị submit | Đóng admission, báo rejected hoặc trả trạng thái |
| Accepted, đang ở queue | Executor đã nhận nhưng task chưa chạy | Cancel/remove/drain, ghi nhận chưa xử lý |
| Running | Worker đã bắt đầu thực thi task | Cho hoàn tất hoặc yêu cầu interrupt/cooperative stop |
| Completed | Chạy xong bình thường | Lưu kết quả, không cần interrupt |
| Failed | Task ném exception | Ghi lỗi, retry theo chính sách |
| Cancelled | `Future` / job record đã hủy | Không tự suy ra external side effects đã rollback |
| Interrupted | Worker nhận signal interrupt hoặc `InterruptedException` | Cleanup, kết thúc/restore flag theo contract |
| Shutdown requested | Executor không nhận thêm công việc theo policy | Chờ termination hoặc escalate |
| Terminated | Executor không còn executing hoặc queued tasks | Có thể giải phóng resource do executor sở hữu |

**Task lifecycle và business lifecycle không giống nhau.** Một worker có thể trả về bình thường trong Java nhưng đã gửi một request thanh toán; hoặc `Future` bị cancel khi một database transaction vẫn chưa thực sự kết thúc. Tách trạng thái scheduling khỏi trạng thái nghiệp vụ là điều kiện để shutdown an toàn.

### 2.1. Hai loại mục tiêu shutdown

- **Graceful:** ngừng nhận việc mới, chờ việc đã nhận hoàn tất trong một deadline. Phù hợp khi mất việc là không chấp nhận được và downstream có thể hoàn tất.
- **Forceful / immediate request:** ngừng nhận mới, thử ngắt running work, lấy lại những việc queued chưa bắt đầu, dừng trong budget ngắn. Phù hợp khi cần giải phóng process nhanh; phải xử lý công việc bỏ dở riêng.

Không phải mọi ứng dụng nên dùng cùng một kiểu. Một email notification có thể retry qua outbox, còn một streaming listener có thể cần dừng đọc nguồn trước rồi commit offsets đúng thứ tự.

## 3. Java Thread.interrupt(): tín hiệu hợp tác

`Thread.interrupt()` đặt interrupted flag của thread và có thể khiến một số blocking methods ném `InterruptedException`, ví dụ `Thread.sleep`, `Object.wait`, `BlockingQueue.take`, `CountDownLatch.await` và `Future.get`. Nó **không phải** cơ chế kill thread an toàn và không tự tạo exception giữa mọi Java instruction.

Ví dụ worker phản hồi yêu cầu dừng:

~~~java
public void doWork() {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            WorkItem item = queue.poll(200, TimeUnit.MILLISECONDS);
            if (item != null) {
                process(item);
            }
        }
    } catch (InterruptedException ex) {
        // Chuyển tín hiệu dừng ra cấp quản lý cao hơn.
        Thread.currentThread().interrupt();
    } finally {
        closeOwnedResources();
    }
}
~~~

Đây là **mảnh code minh họa**, cần khai báo `queue`, `WorkItem`, `process` và `closeOwnedResources`. Khi blocking `poll` bị interrupt, `InterruptedException` thường **xóa interrupted status** lúc ném, nên code bắt exception cần **khôi phục flag** nếu không propagate và caller/outer framework cần biết thread đã bị yêu cầu dừng.

### 3.1. Phân biệt isInterrupted() và Thread.interrupted()

- `Thread.currentThread().isInterrupted()`: đọc flag mà **không xóa**.
- `Thread.interrupted()` (static): đọc **và xóa** interrupt flag của **thread hiện tại**.

Dùng `Thread.interrupted()` thiếu suy nghĩ trong vòng lặp có thể "nuốt" tín hiệu dừng và làm worker chạy tiếp. Khi viết cooperative stop loop, `isInterrupted()` thường trực quan hơn.

### 3.2. Đừng nuốt InterruptedException

~~~java
// Không nên:
try {
    Thread.sleep(1000);
} catch (InterruptedException ex) {
    log.warn("ignored", ex);  // Sau đó tiếp tục làm việc như chưa có stop.
}
~~~

Mảnh này cần logger. Nếu dự án đang đóng mà worker lặng lẽ tiếp tục, `awaitTermination` có thể timeout. Thay bằng một trong hai hợp đồng:

~~~java
// Nếu method được phép khai báo throws:
void waitForSignal() throws InterruptedException {
    Thread.sleep(1000); // Propagate lên caller xử lý shutdown.
}
~~~

Hoặc:

~~~java
void waitForSignal() {
    try {
        Thread.sleep(1000);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return; // Dừng công việc sau cleanup phù hợp.
    }
}
~~~

Nếu thật sự bắt buộc hoàn tất một bước dọn dẹp có thời hạn dù đã interrupt, hãy giải thích rõ trong code và đảm bảo khôi phục status sau đó; đừng vô tình chờ vô hạn hoặc suppress signal.

### 3.3. Những tác vụ không phản hồi interrupt

Tính toán CPU dài không kiểm tra flag, native calls, blocking I/O tùy driver, external HTTP requests, hoặc code có busy loop có thể không dừng khi interrupt. `shutdownNow()` cũng không giải quyết được nếu code phớt lờ tín hiệu.

Nên đưa vào code:

- Timeouts tại HTTP/JDBC/queue/client level.
- Periodic cancellation checks trong CPU loops.
- Đóng tài nguyên mà **task sở hữu** ở `finally`/try-with-resources.
- Bounded retry/batch units thay vì một vòng lặp không có exit condition.
- Trạng thái idempotency khi side effects đã xảy ra trước khi dừng.

**Không dùng `Thread.stop()`** hoặc các cách kill không an toàn để "đảm bảo" kết thúc; chúng có thể làm hỏng invariants khi đang giữ locks hoặc cập nhật state.

## 4. Future.cancel(false) và Future.cancel(true)

Khi submit task bằng `ExecutorService.submit` hoặc `AsyncTaskExecutor.submit`, ứng dụng có thể nhận `Future<?>` để quan sát kết quả hoặc hủy tác vụ.

~~~java
Future<?> future = executor.submit(this::processBatch);

// Chỉ yêu cầu hủy, không ngắt task đang chạy:
boolean canceledWithoutInterrupt = future.cancel(false);

// Hoặc cho phép cố interrupt nếu task đã chạy:
boolean canceledWithInterrupt = future.cancel(true);
~~~

Đây là **mảnh code minh họa**, hai lần cancel trên cùng Future chỉ để so sánh API; trong thực tế chọn **một** cách theo mục tiêu, và nếu đã cancel rồi thì lần sau không có ý nghĩa tương tự.

| Tình huống | `cancel(false)` | `cancel(true)` |
| --- | --- | --- |
| Chưa bắt đầu | Nếu hủy thành công, task không được chạy | Nếu hủy thành công, task không được chạy |
| Đang chạy | Không gửi interrupt đến worker; task có thể tiếp tục | Implementation có thể gửi interrupt đến worker |
| Đã hoàn thành | Hủy thường không còn hiệu lực | Hủy thường không còn hiệu lực |
| Trả `true` | Yêu cầu cancel được chấp nhận theo Future implementation | Yêu cầu cancel được chấp nhận; **không xác nhận worker đã dừng** |
| Sau cancel | `get()` có thể ném `CancellationException` | Tương tự |
| External side effect | Không rollback | Cũng không tự rollback |

`Future.isDone()` trả true cả khi completed normally, failed hoặc cancelled. Vì thế **`isDone()==true` không đồng nghĩa `success`**. Nếu cần phân loại, kiểm tra `isCancelled()` rồi gọi `get()` trong một ranh giới có timeout/exception handling thích hợp.

### 4.1. Một Future bị cancel không chứng minh task đã dừng

Với `FutureTask`, khi `cancel(true)` thành công, handle có thể chuyển sang canceled **trước khi** worker thực sự rời khỏi method và chạy xong `finally`. Nếu code không quan sát thread hoặc completion hook khác, không thể kết luận cleanup hoàn tất chỉ bằng `isDone()`.

**Cách làm an toàn:** cancellation request là **event**; việc worker kết thúc là **một event khác**. Khi cần chắc chắn resource đã được giải phóng, chờ executor termination, latch riêng hoặc job state do worker cập nhật trong `finally`.

### 4.2. CompletableFuture.cancel(true) không đồng nghĩa interrupt

Một trường hợp dễ hiểu sai:

~~~java
CompletableFuture<Result> stage =
        CompletableFuture.supplyAsync(this::compute, executor);

stage.cancel(true);
~~~

Đây là mảnh code minh họa, cần `Result` và `compute`. JDK `CompletableFuture` định nghĩa `cancel(boolean)` như **exceptional completion**; tham số `mayInterruptIfRunning` **không có tác dụng điều khiển thread** trong implementation này. Nó không phải một handle trực tiếp để interrupt task đang chạy như `FutureTask` của `ThreadPoolExecutor`.

Nếu cần cancel execution thật, nên có **token/cancellation protocol** được worker đọc, handle của executor nếu có, timeout I/O, hoặc cơ chế riêng của thư viện. Đừng chỉ dựa vào state của `CompletableFuture`.

## 5. ExecutorService.shutdown() và shutdownNow()

API JDK `ExecutorService` có hai cách dừng cơ bản.

### 5.1. shutdown(): dừng nhận việc mới, cho việc đã nhận chạy hết

~~~java
pool.shutdown();

boolean terminated =
        pool.awaitTermination(30, TimeUnit.SECONDS);
~~~

Mảnh code cần `ExecutorService pool` và xử lý `InterruptedException`. Sau `shutdown()`:

- Pool từ chối submissions mới.
- Running tasks được phép tiếp tục.
- Queued tasks đã nhận được phép lần lượt chạy.
- `shutdown()` **không chờ**; `awaitTermination(...)` mới chờ.
- Nếu một task kẹt vô hạn, pool có thể không bao giờ terminated.

Đây là graceful shutdown của JDK, phù hợp khi phải hoàn tất backlog trong thời gian cho phép.

### 5.2. shutdownNow(): thử dừng running, không thực thi queued

~~~java
List<Runnable> neverStarted = pool.shutdownNow();
~~~

Mảnh code cần `ExecutorService pool`. `shutdownNow()`:

1. Ngừng tiếp nhận việc mới.
2. **Cố interrupt** các tasks đang thực thi (best effort; thường dùng `Thread.interrupt()`).
3. **Ngừng xử lý** những tasks còn chờ trong queue.
4. **Trả về `List<Runnable>`** gồm tasks chưa hề bắt đầu.

**Không tự đợi worker dừng**; cần `awaitTermination(...)` để quan sát termination. Không có bảo đảm worker phớt lờ interrupt sẽ kết thúc. Danh sách `neverStarted` là một dữ kiện quan trọng để đưa jobs vào retry store hoặc đánh dấu canceled.

### 5.3. Sơ đồ so sánh

~~~text
Trước khi dừng:
     worker -> A đang chạy
     queue  -> B, C chưa bắt đầu

shutdown():
     từ chối D mới
     A tiếp tục -> B chạy -> C chạy -> TERMINATED

shutdownNow():
     từ chối D mới
     A được yêu cầu interrupt (có thể vẫn đang chạy)
     B,C rời queue trong List<Runnable> trả về
     caller quyết định hủy Future / lưu lại job state
     chờ awaitTermination trong deadline
~~~

**"Shutdown requested" khác "terminated".** Kiểm tra `isShutdown()` và `isTerminated()`, không dùng chúng thay thế nhau.

## 6. Điểm dễ bỏ sót: queued tasks và Future bị treo

`shutdownNow()` trả về `Runnable` đã nằm trong queue. Nhưng một `Runnable` có thể là **`FutureTask` wrapper** do `submit(...)` tạo, không phải chính Runnable nghiệp vụ ban đầu.

Với một `ThreadPoolExecutor` JDK trực tiếp, danh sách bị drain có thể chứa các `FutureTask` **chưa được cancel**. Nếu caller giữ `Future<?> pendingFuture` rồi chỉ gọi `shutdownNow()` và không xử lý gì thêm, `pendingFuture.get()` **có thể chờ mãi**: task đã bị lấy khỏi queue, không bao giờ chạy, nhưng handle chưa được chuyển sang done/canceled.

~~~java
List<Runnable> neverStarted = pool.shutdownNow();

for (Runnable task : neverStarted) {
    if (task instanceof Future<?> future) {
        future.cancel(false);
    }
}
~~~

Đây là mảnh code minh họa, cần `pool` và imports. Với JDK executor/`FutureTask` thông thường, cách này chuyển handle tương ứng sang canceled. Nhưng **không giả định mọi `Runnable` là `Future`**: `execute(...)` có thể nhận plain Runnable, hoặc Spring `TaskDecorator`/wrapper khác có thể được queue. Khi cần mapping một task sang job ID, hãy thiết kế một registry/tracking wrapper ngay từ lúc submit.

### 6.1. Spring có thể xử lý hủy pending wrappers khác JDK raw shutdownNow

`ExecutorConfigurationSupport` của Spring có hook `cancelRemainingTask(Runnable)` và `ThreadPoolTaskExecutor` override hook này để xử lý các tasks chưa bắt đầu trong lúc **Spring-managed shutdown**. Vì vậy không thể suy rằng mọi `Future` sẽ treo nếu bạn đóng bean theo lifecycle Spring.

**Tách rõ hai trường hợp:**

- **Gọi trực tiếp `getThreadPoolExecutor().shutdownNow()`:** đang thao tác trên JDK executor, cần xem xét manually cancel các handles còn pending; nó đi vòng qua một phần lifecycle của Spring.
- **Để Spring gọi shutdown bean:** Spring có code xử lý/cancel các remaining tasks theo policy/lifecycle của version; cấu hình những flags của Spring thay đổi kết quả.

Trong production, ưu tiên Spring quản lý bean shutdown; dùng raw JDK API khi bạn **thực sự sở hữu backend** và có lý do cụ thể. Nếu phải giành lại jobs chưa chạy để retry, cần chủ động giữ job IDs/metadata thay vì chỉ dựa vào một danh sách `Runnable` wrapper không đủ thông tin.

### 6.2. remove queue không tương đương cancel Future

Với `ThreadPoolExecutor.remove(runnable)`, việc xóa một task khỏi queue (nếu tìm được đúng object) không mặc nhiên hủy handle `FutureTask`. Tương tự, `purge()` chỉ hỗ trợ dọn canceled Future tasks khỏi queue. Trong quy trình hủy, quản lý **cả queue và trạng thái Future**, không chỉ chỉnh collection.

**Đừng tự xóa phần tử trong queue của executor một cách không kiểm soát** dưới nhiều producers/workers. Dùng API của executor và cơ chế job registry do ứng dụng thiết kế.

## 7. Graceful → escalated shutdown theo thời hạn

Pattern JDK thường được khuyên dùng:

~~~java
public static void stopPool(ExecutorService pool) {
    pool.shutdown();
    try {
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            List<Runnable> pending = pool.shutdownNow();

            // Nếu các pending jobs phải được xử lý lại,
            // ghi metadata của chúng vào job store trước.
            for (Runnable task : pending) {
                if (task instanceof Future<?> future) {
                    future.cancel(false);
                }
            }

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Workers still alive");
            }
        }
    } catch (InterruptedException ex) {
        List<Runnable> pending = pool.shutdownNow();
        for (Runnable task : pending) {
            if (task instanceof Future<?> future) {
                future.cancel(false);
            }
        }
        Thread.currentThread().interrupt();
    }
}
~~~

Đây là **method Java dùng được khi có imports**, nhận một pool **do caller sở hữu**. Các con số 10 và 5 giây là **ví dụ deadline**, không phải mặc định JDK/Spring. Ứng dụng thực cần cân đối SLA, container termination grace period và nhu cầu lưu jobs pending.

**Lưu ý về recovery:** việc in "ghi metadata vào job store" không phải một implementation bền vững. Nếu job bắt buộc không mất, **phải persist trạng thái công việc trước khi nhận hoặc thực hiện protocol đáng tin cậy**, bởi một process có thể crash mà không kịp chạy shutdown hook.

### 7.1. Producer phải ngừng trước consumer

Nếu controller/message listener còn tiếp tục submit vào pool sau khi shutdown bắt đầu, có thể nhận `RejectedExecutionException` hoặc Spring `TaskRejectedException`; nhiều retries có thể làm shutdown nhiễu hơn. Một trình tự hợp lý:

~~~text
1. Dừng admission/ingress mới (HTTP/listener/job producer).
2. Đánh dấu application đang DRAINING.
3. Cho accepted jobs chạy hết trong deadline.
4. Nếu quá hạn, yêu cầu interrupt running jobs.
5. Hủy/ghi nhận queued jobs chưa chạy.
6. Chờ cleanup, đóng resources theo dependency.
7. Báo trạng thái dừng, để platform kill process nếu quá deadline.
~~~

Điều này thường phù hợp hơn với việc đóng JDBC pool trước rồi bắt async tasks hoàn tất; chúng có thể cần database để commit trạng thái cuối cùng.

## 8. ThreadPoolTaskExecutor trong Spring Framework 6.1

`ThreadPoolTaskExecutor` là một implementation Spring `AsyncTaskExecutor` backed bởi `java.util.concurrent.ThreadPoolExecutor`. Nó cung cấp các phương thức như:

~~~java
executor.setCorePoolSize(4);
executor.setMaxPoolSize(8);
executor.setQueueCapacity(100);
executor.setThreadNamePrefix("orders-");
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(30);
~~~

Đây là mảnh cấu hình; cần `ThreadPoolTaskExecutor executor`. Những cờ liên quan shutdown cần hiểu **riêng**, không chỉ đọc tên.

### 8.1. setWaitForTasksToCompleteOnShutdown(boolean)

Khi `true`, Spring sử dụng shutdown kiểu **cho running tasks hoàn tất và queued tasks tiếp tục được thực thi** thay vì chủ động interrupt/clear queue ở bước shutdown. **Điểm dễ nhầm:** chỉ cài `true` không có nghĩa một lần đóng context **sẽ block chờ tất cả jobs**. Đó là quyết định **chiến lược shutdown**, còn thời gian chờ thực tế được cấu hình riêng.

Khi `false` (mặc định của flag trên dòng 6.1), Spring có coordinated lifecycle stop phase; đến bước shutdown có thể yêu cầu interrupt running jobs và loại khỏi queue theo policy hiện hành. Hành vi thực còn chịu tác động của lifecycle stop, thời điểm context close và các cờ khác, không nên khẳng định đơn giản "default false thì lúc nào cũng kill job ngay".

### 8.2. setAwaitTerminationSeconds(int)

Cấu hình `setAwaitTerminationSeconds(30)` cho phép bean executor **block tối đa** 30 giây trong phase shutdown để chờ termination. Nó **không tự bật graceful mode**. Nếu `waitForTasksToCompleteOnShutdown=false`, bean có thể vừa yêu cầu dừng nhanh vừa chờ các running jobs chưa phản hồi interrupt. Nếu `true`, nó có thể đợi cả backlog accepted còn lại, nên cần timeout đủ và tài nguyên phụ thuộc chưa bị đóng sớm.

### 8.3. setStrictEarlyShutdown(boolean)

Spring Framework **6.1.4+** có `ThreadPoolTaskExecutor.setStrictEarlyShutdown(boolean)`:

- `true`: gửi tín hiệu shutdown sớm khi context close bắt đầu, từ chối submissions mới; vẫn phối hợp lifecycle stop đối với những tasks đã tồn tại.
- `false` (mặc định của `ThreadPoolTaskExecutor` kể từ Spring 6.1.4): linh hoạt hơn đối với những submissions muộn quanh context close, nhưng vẫn có coordinated lifecycle stop.
- Các flag như `setAcceptTasksAfterContextClose(true)` và `setWaitForTasksToCompleteOnShutdown(true)` có thể **override** đường strict-early-shutdown, dẫn đến late shutdown thay vì cùng lifecycle stop phase.

**Không áp dụng quy tắc trên máy móc cho mọi phiên bản Spring**. Trong module này lấy ví dụ **Spring 6.1.14**, cần kiểm tra chính Javadocs và test trên version runtime.

### 8.4. setAcceptTasksAfterContextClose(boolean)

`setAcceptTasksAfterContextClose(true)` thay đổi policy về những submissions đến sau khi đóng context đã bắt đầu và ảnh hưởng cách phối hợp lifecycle. Nó không nên được dùng chỉ để "chữa" một task ném rejection lúc shutdown; có thể làm kéo dài vòng đời và phá ordering của resource dependencies.

Khi producer đã bước sang draining, tốt nhất là **ngừng submit job mới** và có logic trả kết quả rõ, thay vì ép executor tiếp tục nhận mọi việc lúc hạ tầng đang đóng.

### 8.5. Bảng chọn cấu hình

| Mục tiêu | Điều cần kiểm soát | Cẩn trọng |
| --- | --- | --- |
| Cho toàn bộ accepted jobs chạy hết | `setWaitForTasksToCompleteOnShutdown(true)` | Backlog có thể dài hơn timeout của platform |
| Chờ trước khi đóng resources phụ thuộc | `setAwaitTerminationSeconds(...)` | Timeout không bảo đảm task tự dừng |
| Ngừng nhận task sớm ở context close | `setStrictEarlyShutdown(true)` | Tương tác với các flag khác |
| Chấp nhận submissions muộn khi đóng | `setAcceptTasksAfterContextClose(true)` | Có thể vượt ý định graceful draining |
| Ngắt running và bỏ pending tại shutdown | Chính sách non-graceful Spring/JDK phù hợp | Có thể làm mất công việc chưa persist |
| Hủy một task cụ thể | Giữ `Future`/job handle riêng | Không cần shutdown cả pool |

**Không cấu hình đồng thời nhiều cờ chỉ để "cho chắc"**. Ví dụ đặt cả `waitForTasksToCompleteOnShutdown=true` và `strictEarlyShutdown=true` rồi nghĩ chắc chắn "reject ngay, wait all" là suy luận sai về ưu tiên cờ; Javadocs Spring nêu rõ các flag override nhau.

## 9. Bean lifecycle: đợi tasks trước khi đóng datasource

Với Spring-managed executor, bean được khởi tạo, tham gia lifecycle và bị destroy theo context. Tuy nhiên một service khác, connection pool, cache hoặc external client cũng có shutdown riêng. Nếu async jobs cần các resources đó để commit, cách dừng không đúng thứ tự sẽ gây lỗi.

Một cấu hình Spring cho chế độ drain accepted tasks:

~~~java
@Configuration(proxyBeanMethods = false)
public class WorkerConfig {
    @Bean("workExecutor")
    public ThreadPoolTaskExecutor workExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("work-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(45);
        return executor;
    }
}
~~~

Đây là **class minh họa**, cần imports `@Bean`, `@Configuration`, `ThreadPoolTaskExecutor`. Nó không cam kết mọi business job hoàn tất trước 45 giây; phải đo thời gian còn lại, thiết kế per-task timeout và xây protocol nếu backlog lớn.

### 9.1. SmartLifecycle và @PreDestroy

`SmartLifecycle` có thể giúp chủ động quản lý start/stop theo phase; `@PreDestroy` cho phép cleanup khi bean bị destroy. Nhưng **đừng tạo một `@PreDestroy` hook chờ vô hạn** hoặc giả định callback destroy của mọi bean sẽ chạy theo thứ tự mong muốn nếu không có dependency/phase rõ.

Nếu một job cần database để ghi checkpoint ở `finally`, cần bảo đảm datasource còn sống khi job hoàn tất. Có thể dùng coordinated lifecycle, explicit dependencies và shutdown phases để ngăn resource bị đóng quá sớm.

### 9.2. Ngắt tại request-level khác shutdown context

Nếu user nhấn "Hủy báo cáo" trong UI, nên hủy **đúng job handle** hoặc cập nhật một **cancellation token/job state** do worker đọc, không dừng toàn `ThreadPoolTaskExecutor`. Shutdown pool là hành động phạm vi rộng, ảnh hưởng mọi jobs cùng sử dụng executor.

## 10. Ví dụ hoàn chỉnh Java 17 + Spring Framework 6.1.14

Demo sau dùng một `ThreadPoolTaskExecutor` Spring-managed có **một worker**, chứng minh đồng thời:

1. Một task đã vào worker nhận `InterruptedException` khi backend được yêu cầu `shutdownNow()`.
2. Một task thứ hai đang **nằm trong queue** bị trả về trong danh sách `neverStarted`, không được thực thi.
3. Với raw JDK backend, ta cần **hủy Future của task bị rút khỏi queue** để các consumers không đợi vô hạn.
4. `awaitTermination` là một bước xác nhận worker đã kết thúc, khác với việc chỉ gọi shutdown.

**Phạm vi demo:** để thấy rõ `List<Runnable>` do `ExecutorService.shutdownNow()` trả về, code **cố tình gọi trực tiếp underlying `ThreadPoolExecutor`** lấy từ một Spring bean **chỉ phục vụ demo**. Đây **không phải cách khuyến nghị** cho mọi Spring production lifecycle; production nên sử dụng Spring-managed shutdown flags như mục 8–9, hoặc shutdown backend JDK khi thực sự là owner.

### 10.1. Cấu trúc project

~~~text
interrupt-and-drain-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/StopDemo.java
~~~

**settings.gradle**

~~~groovy
rootProject.name = 'interrupt-and-drain-demo'
~~~

**build.gradle**

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
    mainClass = 'demo.StopDemo'
}
~~~

### 10.2. src/main/java/demo/StopDemo.java

~~~java
package demo;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public final class StopDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            ThreadPoolTaskExecutor executor =
                    context.getBean(ThreadPoolTaskExecutor.class);

            // Dành riêng cho demo, không dùng để shutdown
            // pool chia sẻ trong ứng dụng production.
            ThreadPoolExecutor backend =
                    executor.getThreadPoolExecutor();

            CountDownLatch runningStarted = new CountDownLatch(1);
            CountDownLatch interruptObserved = new CountDownLatch(1);
            AtomicBoolean pendingRan = new AtomicBoolean(false);

            Future<?> running = executor.submit(() -> {
                runningStarted.countDown();

                try {
                    // Chờ vô hạn có chủ đích trong demo.
                    new CountDownLatch(1).await();
                } catch (InterruptedException ex) {
                    interruptObserved.countDown();
                    // Giữ tín hiệu interrupt cho tầng gọi.
                    Thread.currentThread().interrupt();
                }
            });

            // Bảo đảm worker đã chiếm đúng một thread.
            if (!runningStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not start");
            }

            // Worker đang bận nên task này chắc chắn vào queue.
            Future<?> pending = executor.submit(() ->
                    pendingRan.set(true));

            boolean queuedBefore = backend.getQueue().size() == 1;

            // Trong demo, ta chủ động shutdown backend để nhìn
            // danh sách Runnable chưa bao giờ bắt đầu.
            List<Runnable> neverStarted = backend.shutdownNow();

            // shutdownNow JDK không nhất thiết cancel Future
            // đang nằm trong danh sách bị lấy khỏi queue.
            pending.cancel(false);

            boolean terminated =
                    backend.awaitTermination(5, TimeUnit.SECONDS);

            System.out.println("running started: " +
                    (runningStarted.getCount() == 0));
            System.out.println("queued before stop: " + queuedBefore);
            System.out.println("pending returned: " +
                    (neverStarted.size() == 1));
            System.out.println("running interrupted: " +
                    (interruptObserved.getCount() == 0));
            System.out.println("queued never ran: " +
                    !pendingRan.get());
            System.out.println("pending future cancelled: " +
                    pending.isCancelled());
            System.out.println("executor terminated: " + terminated);
            System.out.println("running future done: " +
                    running.isDone());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean("demoExecutor")
        public ThreadPoolTaskExecutor demoExecutor() {
            ThreadPoolTaskExecutor executor =
                    new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("stop-demo-");
            executor.setWaitForTasksToCompleteOnShutdown(false);
            executor.setAwaitTerminationSeconds(2);
            return executor;
        }
    }
}
~~~

### 10.3. Lệnh chạy

Trong thư mục `interrupt-and-drain-demo`, với JDK 17+ và Gradle 8.5+:

~~~bash
gradle run
~~~

Nếu dự án có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

**Tám dòng stdout do chương trình tự in trong điều kiện executor hoạt động bình thường và interrupt được xử lý trước timeout:**

~~~text
running started: true
queued before stop: true
pending returned: true
running interrupted: true
queued never ran: true
pending future cancelled: true
executor terminated: true
running future done: true
~~~

Spring/Gradle có thể in log khác. Nếu worker không kịp start hoặc không thể terminate trong hạn, chương trình báo lỗi qua exception/boolean thay vì tuyên bố đã shutdown thành công một cách giả tạo.

### 10.4. Phân tích từng bước

1. `ThreadPoolTaskExecutor` có **một worker thread** và queue capacity 4. Spring khởi tạo bean và underlying ThreadPoolExecutor khi context tạo xong.
2. Task `running` đếm latch `runningStarted` rồi chờ trên `CountDownLatch.await()`. Đây là blocking operation phản hồi `Thread.interrupt()` bằng `InterruptedException`.
3. Main đợi latch, đảm bảo worker đã chạy trước khi submit `pending`.
4. Vì max pool size = 1 và worker đang bận, `pending` chỉ có thể vào queue, không được chạy trước khi có shutdown/interruption.
5. `backend.shutdownNow()` gửi interrupt request tới running worker và **drain queue**, trả danh sách chứa pending wrapper.
6. `pending.cancel(false)` là lệnh **riêng**, chuyển Future đang chờ sang trạng thái canceled. Nếu bỏ bước này khi đã rút FutureTask khỏi queue, consumer có thể chờ `get()` vô thời hạn.
7. Running task nhận `InterruptedException`, đánh dấu `interruptObserved`, restore interrupted status rồi thoát method. `Future` của running task có thể hoàn tất bình thường vì exception đã được catch; nó **không nhất thiết có trạng thái cancelled** chỉ vì thread bị interrupt.
8. `awaitTermination(5s)` chờ underlying executor không còn running tasks. Đó mới là bước kiểm tra worker đã kết thúc, khác với `shutdownNow()` request.
9. Cuối cùng Spring context được đóng. Lúc đó backend đã shutdown bằng JDK API riêng để minh họa; trong production, không nên thao tác trực tiếp một shared backend rồi để các Spring components khác tiếp tục submit vào nó.

**Demo minh họa một lịch trình được kiểm soát bởi latch**, không dựa vào `Thread.sleep(100)` rồi đoán task nào đã chạy. `CountDownLatch` giúp test ổn định hơn. Nó không kiểm chứng database rollback, queued job persistence hoặc network cancellation; đó là các bài toán khác.

## 11. Ví dụ hủy một tác vụ mà không shutdown cả executor

Với user action "Cancel export", cách phù hợp thường là giữ một handle cho mỗi job.

~~~java
public final class ExportService {
    private final AsyncTaskExecutor executor;
    private final ConcurrentMap<String, Future<?>> runningJobs =
            new ConcurrentHashMap<>();

    public ExportService(AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    public void start(String jobId) {
        Future<?> task = executor.submit(() -> exportJob(jobId));
        Future<?> previous = runningJobs.putIfAbsent(jobId, task);

        if (previous != null) {
            task.cancel(false);
            throw new IllegalStateException("Duplicate job: " + jobId);
        }
    }

    public boolean cancel(String jobId) {
        Future<?> task = runningJobs.get(jobId);
        return task != null && task.cancel(true);
    }
}
~~~

Đây là **mảnh code minh họa**, cần imports, method `exportJob`, cleanup logic và xử lý race giữa `submit` với registry. Nó không phải một implementation production hoàn chỉnh: task có thể **bắt đầu và hoàn thành trước** `putIfAbsent`, nên cần registration state được lưu **trước khi dispatch** hoặc phối hợp atomically qua job registry. Nếu đã submit duplicate rồi mới cancel, có thể side effect đã bắt đầu; ví dụ nêu để thấy vì sao job ownership/concurrency control cần được thiết kế cẩn thận.

Tốt hơn trong hệ thống có state store:

~~~text
POST /jobs/{id}/cancel
     |
     +-- authorize
     +-- mark cancellation_requested trong job store
     +-- tìm handle nếu còn trên node này
     +-- Future.cancel(true) nếu policy cho phép
     +-- worker tự kiểm tra token, cleanup, ghi CANCELLED
~~~

Tác vụ có thể đã hoàn thành trước khi yêu cầu cancel; API nên trả trạng thái cuối (completed/already cancelled/cancel requested), không bảo đảm trả 200 đồng nghĩa đã thu hồi mọi external side effect.

### 11.1. @Async void không có handle trực tiếp cho caller

Một `@Async void` method thường không trả `Future` để caller quản lý. Nếu nghiệp vụ cần hủy chính xác một execution, theo dõi lỗi hoặc chờ kết quả, nên cân nhắc `Future<T>`/`CompletableFuture<T>` qua contract rõ, hoặc một explicit job registry có cancellation token. `AsyncUncaughtExceptionHandler` liên quan exception từ `@Async void`, không là cơ chế cancel task.

## 12. @Scheduled và ScheduledFuture: ngừng lịch khác ngừng job

Một Spring `TaskScheduler` có thể trả `ScheduledFuture<?>`:

~~~java
ScheduledFuture<?> handle =
        taskScheduler.scheduleAtFixedRate(
                this::pollQueue,
                Duration.ofSeconds(10));

// Khi không muốn có tick tiếp:
handle.cancel(false);
~~~

Mảnh code cần `taskScheduler` và method `pollQueue`. Hủy `ScheduledFuture` dừng một registration theo hợp đồng scheduler. Nhưng **không luôn đồng nghĩa một job đã được tick trước đó và dispatch qua executor khác sẽ dừng**.

Trong các lịch `@Scheduled` xử lý bởi annotation post-processor, lifecycle Spring cũng quản lý scheduled registrations. Nếu muốn dừng một job riêng theo yêu cầu admin mà không hủy mọi lịch cùng pool, nên dùng `TaskScheduler` programmatically và giữ handle phù hợp, hoặc thiết kế registry thay vì đụng toàn bộ scheduler.

### 12.1. ScheduledThreadPoolExecutor có policies shutdown riêng

Với `ThreadPoolTaskScheduler` Spring (backed bởi `ScheduledThreadPoolExecutor`), cần biết các tùy chọn:

- `setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean)`: có tiếp tục periodic tasks sau shutdown theo underlying executor policy hay không; mặc định false.
- `setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean)`: có cho những delayed one-shot tasks đã chờ thực thi sau orderly shutdown không; mặc định true theo JDK/Spring policy thường dùng.
- `setRemoveOnCancelPolicy(boolean)`: dọn các canceled tasks khỏi delayed queue theo policy; hữu ích nếu ứng dụng hủy rất nhiều lịch dài hạn.

**Cẩn trọng:** hành vi `shutdown()` graceful và `shutdownNow()` immediate khác nhau, và Spring lifecycle flags có thể lựa chọn đường dừng. Đừng suy từ một thuộc tính đơn lẻ rằng scheduled callbacks sẽ luôn được chạy hoặc luôn bị hủy. Kiểm thử trên đúng Spring/JDK version.

### 12.2. Cron và công việc đã enqueue

~~~text
Cron registration -> tick lúc 09:00
                  -> callback gửi export job vào pool khác
                  -> scheduler callback hoàn thành

09:01 hủy Cron registration:
  ngăn các tick tiếp theo;
  KHÔNG tự hủy export job đã dispatch lúc 09:00.
~~~

Nếu cần cancel cả execution đã dispatch, phải giữ **hai handle/state**: handle lịch cron và handle/job ID của tác vụ xuất báo cáo đã được tạo.

## 13. Virtual threads không thay đổi nguyên lý cancellation

Java 21+ virtual threads vẫn là `Thread` và hỗ trợ interruption. `Thread.currentThread().isVirtual()` có thể giúp xác minh worker đang chạy trên virtual thread, nhưng không có nghĩa một virtual thread có thể bị kill an toàn tức thì.

Spring 6.1 có `VirtualThreadTaskExecutor` và `SimpleAsyncTaskExecutor.setVirtualThreads(true)`. Với những executors không có queue/pool cố định giống `ThreadPoolExecutor`, ý niệm "drain hàng đợi chưa bắt đầu" và khả năng chờ shutdown **khác nhau theo implementation**. Không đem thuật toán lấy `getThreadPoolExecutor().getQueue()` áp dụng lên mọi `AsyncTaskExecutor`.

Ví dụ:

~~~java
@Bean("virtualExecutor")
public AsyncTaskExecutor virtualExecutor() {
    return new VirtualThreadTaskExecutor("virtual-job-");
}
~~~

Mảnh này cần Spring imports và JDK 21+. Nếu yêu cầu cancel từng job, vẫn phải có contract lifecycle của Future/token và đáp ứng interruption. Nếu công việc gửi I/O đến thư viện không phản hồi cancel, virtual threads không tự hồi phục external side effects.

## 14. Transaction, JDBC, HTTP: interrupt không tự rollback

Giả sử worker đang:

~~~text
BEGIN TRANSACTION
  UPDATE orders SET status = 'SENT'
  CALL remote shipping API
  COMMIT
~~~

Nếu một admin gọi `Future.cancel(true)` đúng giữa remote API và commit, các outcome có thể khác nhau:

- API đã nhận request nhưng database transaction chưa commit.
- Transaction rollback do exception hoặc timeout, nhưng shipping API vẫn thực hiện.
- JDBC driver không ngắt query ngay, transaction tiếp tục một khoảng.
- Task catch interruption rồi chạy code khác, vô tình commit.
- Task đã hoàn tất trước yêu cầu cancel, nên cancel không có tác dụng.

Do vậy, **Java interrupt không phải giao thức distributed rollback**.

### 14.1. Hướng thiết kế an toàn hơn

- Chia công việc thành **những unit nhỏ, idempotent**, có checkpoints.
- Gắn job ID/idempotency key vào side effects.
- Dùng DB transaction để cập nhật trạng thái nội bộ nhất quán.
- Dùng **transactional outbox** khi cần phát sự kiện/side effects bền vững.
- Có HTTP/JDBC timeouts phù hợp hơn thời hạn shutdown tổng thể.
- Khi nhận interruption, cleanup/rollback theo business transaction contract.
- Có retry từ job store khi queued/running work bị bỏ dở vì process stop.
- Nếu một action không thể thu hồi (payment, email), phân biệt **cancel requested** và **already executed**; có thể cần compensation thay vì rollback.

Một `@Transactional` method chạy qua Spring proxy có thể rollback trên unchecked exceptions theo cấu hình mặc định; nhưng nếu code **bắt và nuốt** exception rồi return bình thường, transaction interceptor có thể xem method là thành công. `InterruptedException` là checked exception và rollback rule của Spring có thể khác nếu không cấu hình. **Không dựa vào interrupt tự động tương đương rollback.**

## 15. Timeout, Future.get và shutdown không đồng nghĩa nhau

| Cơ chế | Tác động chính | Không tự bảo đảm |
| --- | --- | --- |
| `Future.get(5, SECONDS)` | Caller đợi tối đa 5 giây | Task sẽ bị hủy khi hết 5 giây |
| `Future.cancel(true)` | Yêu cầu cancel/interrupt nếu có thể | Worker đã kết thúc hoặc side effect rollback |
| `CompletableFuture.orTimeout(...)` | Future có thể hoàn tất exceptionally khi quá thời hạn | Computation nền tự bị interrupt |
| `ExecutorService.shutdown()` | Ngừng nhận mới, cho accepted tasks chạy | Có deadline termination |
| `ExecutorService.shutdownNow()` | Thử interrupt running, drain queued | Worker chắc chắn kết thúc |
| `awaitTermination(timeout)` | Chờ termination trong timeout | Task hết hạn đã tự bị kill |
| Spring `setAwaitTerminationSeconds` | Dành thời gian chờ bean executor stop | Đổi shutdown policy thành graceful |
| Spring `setWaitForTasksToCompleteOnShutdown(true)` | Chọn chờ accepted queued/running theo shutdown strategy | Context tự block đến khi mọi work hoàn tất nếu không có await |
| HTTP/JDBC request timeout | Giới hạn I/O theo client/driver | Distributed transaction đã rollback toàn diện |

**Sai lầm điển hình:** gọi `future.get(5,SECONDS)` rồi khi bị `TimeoutException` chỉ bỏ Future reference; task có thể vẫn chạy. Nếu thật sự muốn ngắt, phải có chính sách cancellation riêng. Hơn nữa, `CompletableFuture` timeout/cancel chỉ là completion state, không phải guarantee của underlying computation.

## 16. Quan sát khi dừng: metrics và logs

Khi triển khai trong Spring service, đo ít nhất:

- `executor_active_threads` và `executor_pool_size`.
- `executor_queue_depth`, `tasks_submitted` và `tasks_rejected`.
- Số `running_jobs`, `queued_jobs` và `completed_jobs`.
- `stop_requested_at`, `stop_grace_period_remaining`, `executor_terminated_at`.
- Số tasks nhận interrupt và số tasks **thực sự rời worker**.
- Số tasks returned từ `shutdownNow()`, đã được cancel/redispatch/persist bao nhiêu.
- Job business state: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCEL_REQUESTED`, `CANCELLED`.
- Thời gian dọn tài nguyên và số dependent resources còn mở.
- `InterruptedException`/`TaskRejectedException`/`CancellationException`/`TimeoutException`.
- Số external operations đã hoàn thành dù job nhận cancel request.

Log nên mang job ID, executor name, task type và correlation ID. Không chỉ log "executor shutdown" khi mới gọi `shutdownNow()`. Trên hệ thống nhiều pods, cần log nhận SIGTERM, thời hạn grace của orchestrator và tình trạng backlog/retry.

### 16.1. Một sự kiện hủy cần có outcome

~~~text
12:00:00  stop_requested
12:00:01  new_submissions_rejected
12:00:01  running=4, queued=20
12:00:15  graceful_timeout
12:00:15  interrupt_requested_for_running
12:00:15  pending_drained=8
12:00:17  terminated=true
12:00:18  pending_requeued_or_marked=8
~~~

Dãy log trên chỉ **minh họa**; các số không phải metrics thực tế. Ý nghĩa là ghi cả **yêu cầu dừng** và **kết quả xử lý**.

## 17. Anti-pattern và cách sửa

| Anti-pattern | Vì sao nguy hiểm | Cách đúng hơn |
| --- | --- | --- |
| `shutdownNow()` rồi tuyên bố mọi task đã dừng | Interrupt best effort, không đợi | `awaitTermination` + task completion tracking |
| Không xử lý danh sách queued do shutdownNow trả về | Work bị bỏ, Future có thể treo | Cancel handles, ghi trạng thái/retry phù hợp |
| Nuốt `InterruptedException` | Worker có thể tiếp tục khi app cần dừng | Propagate hoặc restore flag rồi return/cleanup |
| `Future.isDone()==true` => success | Done gồm failure/cancel | Xem `isCancelled` và `get` |
| `Future.cancel(true)` => side effects rollback | Future state không là DB transaction | Idempotency/transaction/compensation |
| Dùng `CompletableFuture.cancel(true)` để interrupt worker | MayInterruptIfRunning bị bỏ qua | Cancellation token/underlying task handle |
| `Future.get(timeout)` được xem là task deadline | Timeout chỉ áp dụng caller wait | Timeout I/O và cancel protocol riêng |
| Hủy toàn pool khi chỉ muốn cancel một report | Ảnh hưởng mọi job | Giữ handle hoặc job ID đúng |
| `setWaitForTasksToCompleteOnShutdown(true)` mà không await | Bean có thể shutdown song song resource khác | `setAwaitTerminationSeconds` và lifecycle ordering |
| `setStrictEarlyShutdown(true)` kết hợp flags override nhưng không hiểu | Hành vi đóng có thể khác kỳ vọng | Đọc đúng Spring version Javadocs |
| Tắt datasource trước khi jobs cần commit | Job cuối lỗi, mất trạng thái | Coordinated lifecycle, resource ownership |
| `@Scheduled` cancel registration rồi tin job đã dispatch được hủy | Handle lịch khác handle execution | Theo dõi hai cấp riêng |
| Executor tự shutdown do một service không sở hữu | Service khác bị reject | Owner quản lý pool, consumer cancel handle riêng |
| Dùng `Thread.stop()` để kill | Phá state/locks | Interruption + cooperative cleanup |
| Không đóng admission trước shutdown | Producer submit vào pool đang dừng | Drain protocol trước, reject rõ |
| Lưu Future trong memory rồi kỳ vọng survive restart | Handle không bền vững | Job store/outbox khi cần |
| Không test nhiều tasks cùng chạy/queued | Race trong shutdown bị bỏ sót | Latch-based integration tests |

## 18. Checklist code review

- [ ] Phân biệt tác vụ đang RUNNING với tác vụ ACCEPTED nhưng còn QUEUED.
- [ ] Xác định owner của Executor/TaskExecutor và của từng job handle.
- [ ] Stop accepting new tasks trước khi drain khi quy trình yêu cầu.
- [ ] Chọn graceful hoặc immediate policy có deadline và outcome rõ.
- [ ] Dùng `shutdown()`/`shutdownNow()` đúng semantics; kiểm tra `awaitTermination`.
- [ ] Với raw `shutdownNow()`, kiểm tra tasks returned và trạng thái Future pending.
- [ ] Không cast mọi queued Runnable thành Future nếu TaskDecorator/wrapper có thể khác.
- [ ] `Future.cancel(false)` và `cancel(true)` được lựa chọn theo contract.
- [ ] Worker nhận `InterruptedException` thì cleanup và restore flag/propagate phù hợp.
- [ ] Blocking I/O có timeout và không giả định interrupt chắc chắn dừng.
- [ ] Không dùng `CompletableFuture.cancel(true)` như guarantee ngắt thread.
- [ ] Nếu dùng Spring `ThreadPoolTaskExecutor`, hiểu `setWaitForTasksToCompleteOnShutdown` và `setAwaitTerminationSeconds` khác nhau.
- [ ] Với Spring 6.1.4+, hiểu `setStrictEarlyShutdown` cùng các flags override.
- [ ] Không để async tasks phụ thuộc resources đã bị đóng trước.
- [ ] Không dùng shutdown pool để cancel một job riêng.
- [ ] Với `@Scheduled`, phân biệt hủy registration và hủy job đã dispatch.
- [ ] Với nhiều nodes, job state/retry/idempotency không chỉ nằm trong memory.
- [ ] Ghi nhận rejected, interrupted, cancelled, finished và abandoned tasks.
- [ ] Có unit/integration tests dùng latches, không dựa vào `Thread.sleep` tùy ý.
- [ ] Test cả trường hợp worker phớt lờ interrupt và deadline hết.
- [ ] Đảm bảo platform SIGTERM grace period dài hơn budget shutdown đã cấu hình.
- [ ] Kiểm tra policy shutdown trên Spring/JDK version chạy production.

## 19. Câu hỏi tự kiểm tra

1. **Ngắt running task là gì?** Gửi tín hiệu interruption/cooperative stop, không phải kill thread ngay.
2. **Hủy queued task là gì?** Không cho task chưa bắt đầu được thực thi, quản lý Future/job state.
3. **`Thread.interrupt()` có dừng mọi Java code ngay không?** Không.
4. **`InterruptedException` thường làm gì với interrupted flag?** Clear flag lúc ném; catch thường propagate hoặc restore.
5. **`Thread.interrupted()` khác `isInterrupted()` thế nào?** Static method đọc và clear, instance method đọc không clear.
6. **`Future.cancel(false)` khi task đang chạy?** Không yêu cầu interrupt worker.
7. **`Future.cancel(true)` có chắc worker đã kết thúc?** Không.
8. **`Future.isDone()` có đồng nghĩa success?** Không, có thể failed/cancelled.
9. **`CompletableFuture.cancel(true)` có ngắt underlying worker không?** Không có guarantee; JDK implementation bỏ qua tham số interrupt.
10. **`ExecutorService.shutdown()` làm gì với queued tasks?** Cho accepted tasks tiếp tục chạy theo policy.
11. **`shutdownNow()` trả gì?** Danh sách Runnables chưa bắt đầu, đồng thời thử interrupt running tasks.
12. **`shutdownNow()` có tự await termination?** Không.
13. **Vì sao pending Future có thể treo sau raw shutdownNow?** Queue wrapper bị drain nhưng Future chưa được đánh dấu cancelled.
14. **Spring có xử lý cancel pending wrappers khi shutdown bean?** Có hook `cancelRemainingTask` trong Spring executor support, tùy đường lifecycle.
15. **`setWaitForTasksToCompleteOnShutdown(true)` có nghĩa context chắc chắn đợi đến cùng?** Không; cần await configuration và resource ordering.
16. **`setAwaitTerminationSeconds` chủ yếu làm gì?** Giới hạn thời gian bean shutdown đợi termination.
17. **`setStrictEarlyShutdown` có từ Spring phiên bản nào?** `ThreadPoolTaskExecutor` 6.1.4+.
18. **Hủy `ScheduledFuture` cron có hủy job gửi sang executor khác trước đó?** Không tự động.
19. **Có thể chuyển một JPA transaction đang chạy về "canceled" chỉ bằng interrupt?** Không; phải xử lý transaction outcome.
20. **Làm sao test queued task chắc chắn chưa bắt đầu?** Dùng pool một worker và CountDownLatch cho running task trước khi submit pending.
21. **Nếu executor terminated thì business side effect chắc chắn rollback?** Không.
22. **Khi shutdown trước deadline, ưu tiên tác vụ nào?** Tùy nghiệp vụ; phải phân loại durability, khả năng retry và resource dependencies.
23. **Nếu hàng đợi có 1.000 công việc quan trọng chưa chạy, có nên chỉ bỏ queue?** Không; cần job store/redispatch/idempotency và trạng thái rõ.
24. **Thông điệp chính?** **Interruption là yêu cầu hợp tác cho running work; queued work phải được hủy/ghi nhận riêng, và shutdown chỉ hoàn tất khi termination cùng business outcome được kiểm chứng.**

**Kết luận:** Dừng một executor đúng cách không chỉ là gọi `shutdownNow()`. Hãy **ngừng nhận việc mới**, chọn chính sách graceful/forceful, yêu cầu những tác vụ đang chạy hợp tác qua interruption, **xử lý riêng các công việc chưa bắt đầu** và trạng thái Future, chờ termination trong thời hạn, giữ tài nguyên phụ thuộc đến khi cleanup xong, và bảo đảm mọi business job có trạng thái hoặc cơ chế retry bền vững. Trong Spring 6.1, hiểu rõ sự tương tác giữa lifecycle của `ThreadPoolTaskExecutor` và các flags shutdown thay vì suy diễn từ một setter duy nhất.

### Tài liệu chính thức

- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 — Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html)
- [Java SE 17 — CompletableFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Java SE 17 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html)
- [Spring Framework 6.1.14 — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework 6.1 — ExecutorConfigurationSupport Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html)
- [Spring Framework 6.1 — ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — Application Context Lifecycle](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-shutdown-008">Pause, resume và giới hạn theo loại triển khai, phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

# Pause, resume và giới hạn theo loại triển khai, phiên bản trong Spring Framework

## Mục tiêu

Trong các hệ thống scheduling, có lúc ta cần **tạm ngừng công việc mới bắt đầu** để bảo trì database, giới hạn tải hoặc chờ phụ thuộc bên ngoài phục hồi. Sau đó ứng dụng cần **tiếp tục chạy lịch đã đăng ký**. Nghe như một nút Pause/Resume đơn giản, nhưng trong Spring Framework cần đặt ba câu hỏi:

1. **Đang dùng implementation nào?** `ThreadPoolTaskScheduler`, `SimpleAsyncTaskScheduler`, `ConcurrentTaskScheduler`, một managed scheduler hay Quartz?
2. **Đang chạy Spring Framework phiên bản nào?** Pause/resume có sự khác biệt đáng kể từ **Spring Framework 6.1**.
3. **Muốn tạm ngừng điều gì?** Đợt chạy mới, toàn bộ scheduler, một registration, tác vụ đã offload sang executor khác, hay toàn bộ các replicas?

**Từ Spring Framework 6.1**, `ThreadPoolTaskScheduler` có khả năng pause/resume tích hợp `SmartLifecycle`. Trong API Spring, thao tác tương ứng là **`stop()` để pause** và **`start()` để resume**, kế thừa từ `ExecutorConfigurationSupport` — không phải cặp hàm tên `pause()`/`resume()` trên interface `TaskScheduler`.

Bài học trong phần **Vòng đời và dừng** của module **Spring Framework - concurrency** sẽ giúp bạn:

- Nắm đúng ý nghĩa của `start()`, `stop()`, `stop(Runnable callback)` và `isRunning()`.
- Không nhầm pause với `shutdown()`, `initiateShutdown()`, `ScheduledFuture.cancel(...)` hoặc Spring context close.
- Hiểu việc đang chạy không tự bị ngắt khi pause; callback trong `stop(Runnable)` dùng để đợi in-flight execution hoàn tất.
- Phân tích phạm vi và giới hạn theo loại scheduler/executor, version, Java 17/21 và hạ tầng Jakarta EE/Quartz.
- Đánh giá backlog, fixed rate, fixed delay, cron, reactive tasks và tác vụ `@Scheduled` gửi sang `@Async`.
- Chạy ví dụ Java 17+ với Spring 6.2.12 kiểm chứng pause task chờ, resume, stop callback bằng `CountDownLatch` thay vì dựa vào `Thread.sleep`.
- Xây dựng quy trình vận hành có timeout, job state, idempotency, metrics và quyền điều khiển.

**Thông điệp chính:** Pause/resume là **đặc điểm của implementation và lifecycle cụ thể**, không phải cam kết chung của mọi `TaskScheduler` hay công cụ bảo đảm xử lý đúng một lần.

## 1. `TaskScheduler` interface không có hai phương thức pause/resume

`org.springframework.scheduling.TaskScheduler` là abstraction để **đăng ký Runnable theo thời điểm hoặc trigger**. Các phương thức điển hình:

```java
ScheduledFuture<?> schedule(Runnable task, Instant at);
ScheduledFuture<?> schedule(Runnable task, Trigger trigger);
ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period);
ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay);
```

Đây là chữ ký API minh họa, không phải một file Java độc lập. Chúng mô tả **khi nào chạy**, không quy định chung rằng mọi scheduler phải có cơ chế tạm dừng.

Nếu service chỉ inject:

```java
private final TaskScheduler scheduler;
```

thì không thể tự viết:

```java
scheduler.pause();  // Không thuộc TaskScheduler interface
scheduler.resume(); // Không thuộc TaskScheduler interface
```

Hai dòng trên là **phản ví dụ về compile-time API**. Kể cả `scheduler.stop()` cũng không có trên static type `TaskScheduler` vì `stop` thuộc `Lifecycle`/implementation tương ứng, không thuộc hợp đồng scheduling.

### Hai tầng trách nhiệm

```text
TaskScheduler (Spring SPI)
   |
   +-- schedule(Runnable, Instant/Trigger)
   +-- scheduleAtFixedRate(...)
   +-- scheduleWithFixedDelay(...)
   |
   v
Implementation đang được inject
   |
   +-- ThreadPoolTaskScheduler
   |      |
   |      +-- ExecutorConfigurationSupport
   |      |      |
   |      |      +-- SmartLifecycle: start / stop / stop(callback)
   |      |
   |      +-- ScheduledThreadPoolExecutor bên dưới
   |
   +-- ConcurrentTaskScheduler (adapter)
   +-- DefaultManagedTaskScheduler (container-managed)
   +-- SimpleAsyncTaskScheduler (mô hình khác)
```

Nếu module **bắt buộc** có pause/resume, bạn nên công khai dependency vào một implementation đủ khả năng hoặc tự thiết kế giao diện `JobAdmissionControl` riêng. Không hứa tính năng mà dependency dưới dạng `TaskScheduler` không cam kết.

## 2. Kiểm tra phiên bản Spring Framework

Spring Framework **6.1** bổ sung **lifecycle-integrated pause/resume và graceful shutdown** cho `ThreadPoolTaskScheduler`. Release notes cũng đề cập `ThreadPoolTaskExecutor` và `SimpleAsyncTaskScheduler` trong nhóm cải tiến lifecycle.

| Version | Ý nghĩa đối với `ThreadPoolTaskScheduler` |
| --- | --- |
| Spring 5.x / 6.0.x | Đừng giả định có `SmartLifecycle.stop()`/`start()` với semantics pause/resume của 6.1; kiểm tra API của đúng bản cũ. |
| **Spring 6.1.x** | Các method lifecycle pause/resume có `Since: 6.1`; có thể phối hợp với container shutdown. |
| **Spring 6.2.x** | Tiếp tục hỗ trợ; `ExecutorConfigurationSupport` còn thay đổi default lifecycle phase và các tùy chọn quản lý executor. |
| Spring 7.x / bản mới hơn | Xem Javadoc của runtime, không lấy mọi default 6.2 làm cam kết vĩnh viễn. |

**Không nhầm:** Lớp `ThreadPoolTaskScheduler` đã tồn tại từ rất lâu nhưng không đồng nghĩa chức năng pause/resume xuất hiện cùng ngày lớp được tạo.

### Xác nhận runtime

Trong code có Spring Core:

```java
System.out.println(
    org.springframework.core.SpringVersion.getVersion()
);
```

Đây là snippet. Nếu dùng Spring Boot, version Spring Framework do BOM/dependency management quyết định. Xem resolved dependency tree, đừng suy chỉ từ tên artifact Boot hay IDE gợi ý.

### Lifecycle phase mặc định thay đổi trong 6.2

`ExecutorConfigurationSupport.DEFAULT_PHASE = Integer.MAX_VALUE / 2` **từ Spring 6.2**. `SmartLifecycle.DEFAULT_PHASE` của nhiều thành phần khác thường là `Integer.MAX_VALUE`. Container khởi động các phase thấp hơn trước và yêu cầu dừng phase cao hơn trước, vì vậy scheduler/executor có thể được start sớm và stop muộn hơn các components có phase cao.

Có thể chủ động:

```java
scheduler.setPhase(1000);
```

Đây là snippet cấu hình, dùng khi cần phối hợp thật sự với các `SmartLifecycle` khác. Không nhầm phase với cron scheduling delay: nó là **thứ tự lifecycle**, không đổi chu kỳ.

## 3. `stop()` là pause, `start()` là resume

Với đúng bean `ThreadPoolTaskScheduler` ở Spring 6.1+:

```java
ThreadPoolTaskScheduler scheduler = obtainSchedulerBean();

scheduler.stop();  // Tạm dừng, không đợi task đang chạy.
scheduler.start(); // Tiếp tục khi đang paused.
```

Đây là snippet ở một service có `obtainSchedulerBean()` được định nghĩa; mã độc lập xuất hiện ở phần 6.

Theo Javadoc của lớp cơ sở:

- `start()` — resume một executor đã pause, không làm gì thêm nếu đã running.
- `stop()` — pause một executor, **không chờ** mọi task đang chạy kết thúc.
- `isRunning()` — cho biết executor đang không paused và chưa shutdown.

Ví dụ kiểm tra trạng thái:

```java
boolean before = scheduler.isRunning();
scheduler.stop();
boolean paused = !scheduler.isRunning();
scheduler.start();
boolean resumed = scheduler.isRunning();
```

Khi Spring-managed bean đã khởi động và không bị shutdown, trước/pause/resume có giá trị kỳ vọng là `true`, `true`, `true`.

### Pause không tự interrupt task đang chạy

```text
Thời điểm T0:
  scheduler RUNNING
  task A đã bắt đầu -------------------------->
  task B chờ trong delayed queue

Thời điểm T1:
  scheduler.stop()
  scheduler PAUSED
  task A vẫn tiếp tục --> kết thúc bình thường
  task B chưa được bắt đầu trong trạng thái paused

Thời điểm T2:
  scheduler.start()
  scheduler RUNNING
  task B có thể bắt đầu nếu registration vẫn hợp lệ và có worker
```

Pause không đồng nghĩa `Thread.interrupt()`, `shutdownNow()`, `cancel(true)`, rollback transaction hay hủy registration. Tác vụ đang thực thi **có thể tiếp tục**; các tác vụ chờ sẽ phải chịu tác động của cơ chế pause cho tới resume.

Tuy nhiên, đừng coi một lần gọi `stop()` là ranh giới atomic trên mọi core CPU và mọi task: tác vụ đã được nhận vào execution phase trước khi pause có thể đã bắt đầu. Nếu yêu cầu **mọi công việc đã bắt đầu phải xong trước bảo trì**, dùng callback và một barrier riêng, kết hợp in-flight accounting.

## 4. `stop(Runnable callback)` khác `stop()` thế nào?

`SmartLifecycle` định nghĩa overload:

```java
scheduler.stop(() -> {
    // Tín hiệu khi các task hiện đang chạy đã hoàn tất.
});
```

Javadoc Spring mô tả chính xác: **pause executor và gọi callback một khi tất cả currently executing tasks đã complete**.

Điểm khác:

- `stop()` không tham số gửi tín hiệu pause **không đợi**.
- `stop(callback)` cũng pause nhưng thêm notification sau khi các task in-flight hoàn tất.
- Callback không phải lệnh yêu cầu interrupt hay force-terminate.
- Callback **không** đợi Future nằm trên một executor khác mà scheduled method đã submit trước khi return.
- Không suy ra callback luôn được gọi ngay sau vài mili giây; nếu task kẹt I/O, nó có thể phải chờ lâu.
- Callback không tự cho biết task đã **thành công nghiệp vụ**; một task có thể hoàn tất với exception và callback vẫn biểu thị kết thúc execution.

Ví dụ một task đã vào phần xử lý rồi bị giữ bởi `CountDownLatch`: sau khi main gọi `stop(callback)`, callback phải đợi cho tới khi chốt được nhả. Demo phần 6 sẽ kiểm chứng chính quan hệ thứ tự đó bằng two-phase synchronization.

### Cần một timeout riêng cho việc drain

Trong production, `stop(callback)` là event-based notification, không cung cấp timeout SLA cho từng task. Nếu dùng nó để bảo trì, phải thiết kế một giới hạn thời gian tổng thể và phương án khi tasks không hoàn thành. Các tùy chọn có thể bao gồm: đánh dấu job đang chạy trong store bền vững, ngừng nhận việc mới ở business service, và orchestration có quyền dừng/thu hồi task theo policy.

Không giữ một DB transaction/lock trên thread điều khiển rồi gọi callback đợi worker đang cần chính transaction/lock ấy: đó là công thức gây deadlock ở cấp ứng dụng.

## 5. Pause khác shutdown và cancellation

Đây là phân biệt quan trọng nhất khi triển khai:

| Hành động | Mục đích | Task đang chạy | Task chờ & registrations | Có thể chạy tiếp? |
| --- | --- | --- | --- | --- |
| `stop()` | **Pause** scheduler. | Không chủ động interrupt. | Vẫn có thể giữ ở scheduler, chưa tự cancel. | **Có**, `start()` nếu chưa shutdown. |
| `stop(callback)` | Pause và báo khi in-flight kết thúc. | Tiếp tục tới completion theo task logic. | Không tự trả lại/cancel hết queue. | Có, với executor chưa shutdown. |
| `start()` | **Resume** một scheduler paused. | Không buộc công việc cũ chạy lại từ đầu. | Cho task còn hợp lệ tiếp tục. | Không hồi sinh executor đã shutdown. |
| `ScheduledFuture.cancel(false)` | Hủy một registration cụ thể. | Không chủ động interrupt. | Không tự phục hồi khi `start()`. | Muốn chạy lại phải đăng ký anew. |
| `ScheduledFuture.cancel(true)` | Cố hủy và có thể interrupt. | Phụ thuộc khả năng hợp tác của task/driver. | Cùng registration bị hủy. | Không tự phục hồi. |
| `initiateShutdown()` | Bắt đầu shutdown mềm; từ chối submission mới. | Có thể cho hoàn tất. | Ngăn tiếp tục periodic scheduling. | **Không** phải pause có thể đảo ngược. |
| `shutdown()` | Kết thúc underlying executor theo policy. | Có thể interrupt hoặc cho hoàn tất. | Pending có thể bị hủy hoặc xử lý. | Không dùng `start()` để hồi sinh cùng executor. |
| `ApplicationContext.close()` | Dừng lifecycle và destroy beans. | Theo Spring lifecycle/shutdown policy. | Lịch RAM có thể mất. | Context mới phải đăng ký lại. |

Một `ScheduledFuture` đã cancel có thể vẫn tồn tại dưới dạng object Java, nhưng `start()` không tái kích hoạt registration ấy. Ngược lại, **pause** không làm `isCancelled()` tự thành true.

### `initiateShutdown()` dành cho kết thúc, không phải bảo trì ngắn

Từ Spring 6.1, `ExecutorConfigurationSupport.initiateShutdown()` báo cho underlying executor bắt đầu shutdown, không tiếp nhận tác vụ mới, cho đang chạy có cơ hội hoàn tất. Nó **không block** theo hợp đồng phương thức. Gọi sau đó `shutdown()` sẽ đi vào bước termination đầy đủ.

Không dùng `initiateShutdown()` với ý định “pause vài phút rồi start()”. Một `ExecutorService` đã shutdown không thể quay lại trạng thái nhận task mới bằng start.

### Cấu hình shutdown

Một số lựa chọn:

```java
ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setPoolSize(2);
scheduler.setAwaitTerminationSeconds(30);
scheduler.setWaitForTasksToCompleteOnShutdown(true);
```

Đây là snippet config Spring; cần để container initialize và destroy bean hoặc tự quản lý lifecycle đầy đủ. `setWaitForTasksToCompleteOnShutdown(true)` chọn cho các công việc đang chạy và queued phù hợp hoàn tất trong giai đoạn shutdown thay vì interrupt ngay, còn `setAwaitTerminationSeconds(30)` giới hạn **thời gian chặn trong shutdown**, không phải timeout của mỗi HTTP/JDBC task.

Cũng có `setAcceptTasksAfterContextClose(true)`, nhưng điều đó kéo dài cửa sổ chấp nhận việc sau khi nhận event đóng context; cấu hình cần xét dependency lifetime và cơ chế Spring 6.1+/6.2. Một số flag khiến scheduler không đi qua coordinated lifecycle stop phase như mặc định. Đừng bật theo thói quen hoặc nghĩ đây là pause switch.

Với `ThreadPoolTaskScheduler`, các cờ JDK-compatible:

```java
scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
```

điều khiển công việc **sau shutdown**, không phải việc các mốc bị lỡ trong lúc pause. Khi đánh giá, luôn xem bản JDK và underlying `ScheduledThreadPoolExecutor` tương ứng.

## 6. Ví dụ chạy được Java 17+ và Spring Framework 6.2.12

Ví dụ sau không dùng Spring Boot, không cần web server, không cần `@EnableScheduling`, vì đăng ký trực tiếp bằng `TaskScheduler.schedule(Runnable, Instant)`.

Hai phép thử độc lập:

**A. Pause khi chưa có task đang chạy.** Gọi `stop()`, tạo one-shot task đến hạn ngay, quan sát nó chưa chạy và Future chưa xong. Sau `start()`, `get(timeout)` chờ task chạy xong.

**B. Pause khi một task đang chạy.** Cho task báo đã vào execution bằng latch rồi chặn nó bằng một latch khác. Gọi `stop(callback)`, xác minh callback vẫn chưa gọi trong lúc task đang chạy. Nhả task, chờ Future xong và callback báo hoàn tất. Kiểm tra `stop(callback)` không tự interrupt.

Mọi kết luận quan trọng dựa trên `CountDownLatch`/`ScheduledFuture`, không dùng `Thread.sleep` để phỏng đoán thời gian.

### Cấu trúc thư mục

```text
spring-scheduler-pause-demo/
├── pom.xml
└── src/main/java/example/SchedulerPauseDemo.java
```

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-scheduler-pause-demo</artifactId>
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
                    <mainClass>example.SchedulerPauseDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `src/main/java/example/SchedulerPauseDemo.java`:

```java
package example;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SchedulerPauseDemo {
    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean("maintenanceScheduler")
        public ThreadPoolTaskScheduler maintenanceScheduler() {
            ThreadPoolTaskScheduler pool = new ThreadPoolTaskScheduler();
            pool.setPoolSize(1);
            pool.setThreadNamePrefix("maintenance-");
            return pool;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext ctx =
                 new AnnotationConfigApplicationContext(Config.class)) {
            ThreadPoolTaskScheduler scheduler =
                ctx.getBean("maintenanceScheduler",
                            ThreadPoolTaskScheduler.class);

            // A: Pause trước khi đăng ký task một lần.
            boolean initiallyRunning = scheduler.isRunning();
            AtomicInteger runCount = new AtomicInteger();

            scheduler.stop();
            boolean paused = !scheduler.isRunning();

            ScheduledFuture<?> pending = scheduler.schedule(
                () -> runCount.incrementAndGet(),
                Instant.now()
            );
            if (pending == null) {
                throw new IllegalStateException("No one-shot task");
            }

            // Task due đã có registration, nhưng scheduler đang paused.
            boolean pendingWhilePaused =
                runCount.get() == 0 && !pending.isDone();

            scheduler.start();
            boolean resumed = scheduler.isRunning();
            pending.get(10, TimeUnit.SECONDS);

            // B: Pause khi đang có một task in-flight.
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch stopCallback = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean();

            ScheduledFuture<?> active = scheduler.schedule(() -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            }, Instant.now());

            if (active == null) {
                throw new IllegalStateException("No in-flight task");
            }

            boolean stoppedWhileBusy;
            boolean callbackWaitedWhileBusy;

            try {
                if (!entered.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                        "In-flight task did not start"
                    );
                }

                scheduler.stop(stopCallback::countDown);
                stoppedWhileBusy = !scheduler.isRunning();
                callbackWaitedWhileBusy =
                    stopCallback.getCount() == 1 && !active.isDone();
            } finally {
                // Không bỏ worker chờ vô hạn nếu một assertion thất bại.
                release.countDown();
            }

            active.get(10, TimeUnit.SECONDS);
            boolean callbackAfterCompletion =
                stopCallback.await(10, TimeUnit.SECONDS);

            System.out.println("initiallyRunning=" + initiallyRunning);
            System.out.println("paused=" + paused);
            System.out.println("pendingWhilePaused=" + pendingWhilePaused);
            System.out.println("resumed=" + resumed);
            System.out.println("runCountAfterResume=" + runCount.get());
            System.out.println("stoppedWhileBusy=" + stoppedWhileBusy);
            System.out.println("callbackWaitedWhileBusy="
                + callbackWaitedWhileBusy);
            System.out.println("inFlightNotInterrupted="
                + !interrupted.get());
            System.out.println("callbackAfterCompletion="
                + callbackAfterCompletion);

            // Trả scheduler về running trước khi container đóng.
            scheduler.start();
        }
    }
}
```

Chạy từ thư mục `spring-scheduler-pause-demo`, có JDK 17+ và Maven:

```bash
mvn -q compile exec:java
```

Khi chạy thành công, **chín dòng do chương trình in ra** theo thứ tự:

```text
initiallyRunning=true
paused=true
pendingWhilePaused=true
resumed=true
runCountAfterResume=1
stoppedWhileBusy=true
callbackWaitedWhileBusy=true
inFlightNotInterrupted=true
callbackAfterCompletion=true
```

### Diễn giải từng kết quả

1. Spring context đã khởi tạo `ThreadPoolTaskScheduler` và auto-start lifecycle, do đó `initiallyRunning=true`.
2. `stop()` pause executor chứ không shutdown, nên `paused=true`.
3. Main tạo one-shot task **sau khi đã pause**. Nó đủ hạn nhưng chưa bắt đầu, vì vậy `runCount=0` và Future chưa done (`pendingWhilePaused=true`).
4. `start()` cho phép task đủ hạn tiếp tục. `pending.get(10, SECONDS)` chỉ hoàn tất bình thường khi Runnable chạy xong, dẫn đến `runCountAfterResume=1`.
5. Main tạo task khác và đợi `entered.await(...)`, chứng minh task đã thật sự bắt đầu, không chỉ xếp hàng.
6. Task này đợi `release`, còn main gọi `stop(stopCallback::countDown)`. `stoppedWhileBusy=true` phản ánh scheduler đã pause; callback vẫn pending trước khi task hoàn tất.
7. Main nhả `release` trong `finally` để worker không treo. `active.get(...)` đợi Runnable hoàn tất.
8. `stopCallback.await(...)` chứng minh callback được kích hoạt sau khi current task hoàn tất. `inFlightNotInterrupted=true` vì `stop(callback)` không chủ động interrupt worker.
9. Chương trình chỉ in từ main sau các mốc đồng bộ, nên thứ tự `System.out` ổn định. Context được đóng trong try-with-resources.

**Giới hạn test:** Các timeout 10 giây dùng để làm phép thử dừng có báo lỗi thay vì đợi mãi. Chúng **không** thay thế HTTP/DB timeouts của production. Nếu máy quá tải nặng hoặc task không được tạo, demo sẽ ném exception, không in ra kết luận thành công giả. `CountDownLatch` trong demo chỉ để kiểm chứng quan hệ thứ tự; không nên giữ scheduler worker chờ latch vô hạn trong ứng dụng thực.

## 7. `@Scheduled` và việc chọn đúng scheduler bean

Annotation scheduling cần `@EnableScheduling` trong context tương ứng. Ví dụ:

```java
@Configuration
@EnableScheduling
class SchedulingConfig {
    @Bean("maintenanceScheduler")
    ThreadPoolTaskScheduler maintenanceScheduler() {
        ThreadPoolTaskScheduler scheduler =
            new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("maintenance-");
        return scheduler;
    }
}

@Component
class MaintenanceJobs {
    @Scheduled(
        fixedRate = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "maintenanceScheduler"
    )
    public void tick() {
        // Một lượt chạy theo lịch.
    }
}
```

Đây là snippet kiến trúc, không phải ứng dụng độc lập; cần imports, component configuration, Spring-managed beans. Thuộc tính `scheduler = "maintenanceScheduler"` buộc method dùng scheduler được chọn thay vì một bean tình cờ được Spring tự chọn.

Pause đúng **bean đang được method sử dụng**:

```java
maintenanceScheduler.stop();  // Ảnh hưởng registrations của bean này.
maintenanceScheduler.start();
```

Nếu ứng dụng có `heartbeatScheduler`, `billingScheduler` và `maintenanceScheduler`, chỉ pause một bean sẽ không tự pause hai bean còn lại. Không được kết luận “toàn hệ thống đã dừng” khi chỉ điều khiển một scheduler.

### Programmatic scheduling không cần `@EnableScheduling`

Demo phần 6 gọi trực tiếp:

```java
scheduler.schedule(() -> doWork(), Instant.now());
```

nên không cần annotation processing. `@EnableScheduling` chỉ dùng để Spring tự tìm và đăng ký method `@Scheduled`. Nếu có annotation scheduling, method phải thuộc **Spring-managed bean**, không phải object tự tạo bằng `new`.

## 8. Giới hạn theo loại triển khai

Cần chọn implementation theo môi trường:

| Implementation | Đặc tính chính | Năng lực pause/resume |
| --- | --- | --- |
| **`ThreadPoolTaskScheduler` Spring 6.1+** | Pool scheduler workers, bọc `ScheduledThreadPoolExecutor`. | Có lifecycle stop/start, chủ đề chính của bài. |
| `SimpleAsyncTaskScheduler` Spring 6.1+ | Một scheduler thread, execution strategy khác; có lựa chọn virtual threads trên Java 21+. | Có lifecycle handling nhưng **không đồng nghĩa** semantics worker/pool giống ThreadPoolTaskScheduler. |
| `ThreadPoolTaskExecutor` Spring 6.1+ | Executor chạy task từ caller hoặc `@Async`, không phải scheduler theo thời gian. | Có lifecycle pause/resume riêng cho executor, **không** tự pause các triggers scheduler khác. |
| `ConcurrentTaskScheduler` | Adapter của `ScheduledExecutorService` do nơi khác cung cấp. | Không hứa pause/resume giống `ThreadPoolTaskScheduler`; cần xét delegate và ownership. |
| `DefaultManagedTaskScheduler` | Scheduler từ Jakarta EE container. | Không tự assume quyền pause/shutdown threads của container. |
| JDK `ScheduledThreadPoolExecutor` | Scheduled executor thuần Java. | Không cung cấp Spring `SmartLifecycle` stop/start; `shutdown` là hướng kết thúc. |
| Quartz `Scheduler` | Lịch/trigger với API và job store riêng. | Có cơ chế như `standby()`, `start()` hoặc pause trigger/job, nhưng không đồng nghĩa Spring lifecycle stop/start. |

### `ThreadPoolTaskScheduler`

Task thường chạy **trên chính scheduler workers**, không tự offload sang thread pool nghiệp vụ khác. Default pool size là một, nên một công việc blocking rất lâu có thể làm chậm các jobs đến hạn khác.

Pause không xóa bottleneck này: sau resume, worker vẫn có thể bận do task dài hoặc nhiều tasks sẵn sàng đồng thời. Cần quan sát schedule lag và downstream capacity.

### `SimpleAsyncTaskScheduler`

Được Spring 6.1 giới thiệu, tối ưu hướng dùng virtual threads trên Java 21 khi bật tùy chọn. Nó dùng chiến lược thread khác với `ThreadPoolTaskScheduler`; đặc biệt fixed-delay tasks có hành vi liên quan tới scheduler thread riêng. Đừng suy từ tên “async” rằng mọi invocation đều tách thread hoặc pause có cùng tác động với pool scheduler truyền thống.

**Java 17 không có virtual threads của Java 21**; không cấu hình `setVirtualThreads(true)` với JDK17 rồi kỳ vọng chạy được. Demo này chỉ dùng `ThreadPoolTaskScheduler` truyền thống.

### `ConcurrentTaskScheduler` và managed scheduler

Một adapter chỉ hứa hành vi của các method adapter và delegate mà nó có thể hỗ trợ. Nếu underlying JDK scheduler được tạo/đóng bởi một thư viện khác, Spring wrapper không tự sở hữu quyền lifecycle của nó như một bean tự quản.

Trong Jakarta EE, container sở hữu threads và có policy riêng. Không tự tìm lối shutdown executor của server để mô phỏng pause. Phải dựa vào contract của managed scheduler/container, hoặc thực hiện admission control ở tầng công việc.

### Quartz

Quartz có API lifecycle/job control riêng (`standby`, `pauseJob`, `pauseTrigger`, misfire handling, clustering). Nó không phải `ThreadPoolTaskScheduler` chỉ vì ứng dụng dùng Spring. Với persistent Quartz scheduler, xử lý các mốc lỡ và job recovery chịu quy tắc Quartz, không lấy từ JDK `ScheduledFuture` hoặc Spring `SmartLifecycle` một cách máy móc.

## 9. Pause toàn scheduler hay hủy một registration?

Khi tạo một registration:

```java
ScheduledFuture<?> future =
    scheduler.scheduleAtFixedRate(
        task, Duration.ofSeconds(10)
    );
```

Đây là snippet có `scheduler` và `task` tương ứng. Phân biệt:

- `scheduler.stop()` **tạm dừng scheduler**. Các registrations còn hợp lệ vẫn tồn tại, một future không tự bị cancel.
- `scheduler.start()` **tiếp tục scheduler**. Nó không khôi phục future đã cancel.
- `future.cancel(false)` ngăn các lượt về sau của **registration ấy** mà không chủ động interrupt lượt đang chạy.
- `future.cancel(true)` có thể yêu cầu interrupt theo Future/executor, nhưng I/O/DB không bắt buộc dừng.
- `scheduler.shutdown()` kết thúc underlying executor, không phải pause.
- `future.get()` trên periodic registration **không phải** phép đợi một lần tick; nó có thể chờ cho tới khi registration bị hủy hoặc kết thúc bất thường.

Nếu chỉ muốn “không xử lý job A, nhưng vẫn cho job B chạy”, đừng pause scheduler dùng chung; đặt job-level admission flag hay cancel/re-register job A theo policy. Khi cancel rồi đăng ký lại, cần ngăn duplicate registration.

## 10. Các mốc đến hạn trong thời gian paused

Một mốc cron/fixed-rate có thể đến hạn khi scheduler paused. Khi resume, việc bắt đầu thật có thể **muộn** so với thời điểm scheduled, và không có một cam kết chung cho mọi implementation về **số lượt bù các mốc đã lỡ**.

```text
t = 00s : job có fixed rate 5s
t = 02s : scheduler pause
t = 05s : mốc mục tiêu
t = 10s : mốc mục tiêu
t = 15s : mốc mục tiêu
t = 20s : scheduler resume

Không được tự suy: "Spring sẽ xử lý đủ ba side effects
của ba mốc bị lỡ và không có cái nào trùng".
```

Mỗi loại lịch còn có logic riêng:

| Loại lịch | Điểm cần kiểm soát sau resume |
| --- | --- |
| One-shot tại `Instant` | Nếu pending/không canceled/không shutdown, có thể chạy trễ sau resume. |
| Fixed rate | Mốc tính theo nhịp scheduled, có thể đủ hạn trong quá khứ và dẫn đến lượt chạy muộn. |
| Fixed delay | Lượt sau thường tính từ completion của lượt trước; trì hoãn làm cả chuỗi dời. |
| Cron/Trigger | Trigger context và chính sách tính mốc có thể bỏ qua, bù hoặc điều chỉnh theo implementation. |
| Reactive scheduled method | Có subscription lifecycle riêng; pause trigger không đồng nghĩa cancel Publisher đang chạy. |

Nếu doanh nghiệp yêu cầu xử lý **mọi khoảng thời gian**, lưu mỗi window ID hoặc watermark vào database; worker xử lý các window còn thiếu và ghi trạng thái idempotent. Đừng dùng số callback cron như durable transaction log.

### Backlog khi resume

Sau pause dài có thể có nhiều one-shot tasks cùng đến hạn và các triggers tranh pool. Tăng worker giúp một phần nhưng có thể làm nghẽn DB/HTTP connections. Cần có:

- Rate limit hoặc batching sau resume.
- Giới hạn số business tasks in-flight.
- Timeout client I/O/DB, lock timeout, error budget.
- Metrics scheduler lag, số queued tasks, active workers và last-success timestamp.
- Quarantine/durable retry cho task phụ thuộc chưa hồi phục.
- Cơ chế giảm tải nếu quá nhiều replicas cùng resume.

## 11. `@Async` gửi việc ra executor khác là một phạm vi độc lập

Một scheduled method có thể chỉ submit:

```java
@Scheduled(fixedRate = 1000)
public void tick() {
    backgroundExecutor.execute(this::processBatch);
}
```

Đây là snippet có executor và method `processBatch` được định nghĩa, cùng hạ tầng `@EnableScheduling`. Trong mô hình này:

```text
scheduler-worker:
    tick() -> submit business task -> tick return
                                |
                                v
business-executor-worker:
    processBatch() ---- chạy nhiều giây ---- hoàn tất
```

Gọi `scheduler.stop()` sẽ pause **những tick mới của scheduler**, nhưng **không dừng** `processBatch` đã gửi sang pool khác. `stop(callback)` chỉ đợi các tasks thực sự thuộc scheduler; nó không đợi business executor nếu scheduled tick đã return.

Nếu `@Async` được dùng trên một bean khác, hiệu ứng tương tự. Nếu gọi `this.asyncMethod()` trong cùng bean Spring AOP proxy mode, interceptor `@Async` có thể bị bỏ qua, và code có thể chạy trực tiếp trên scheduler thread. Phải biết bean/proxy thực tế trước khi vẽ lifecycle.

### Quy trình pause thực sự cho pipeline nhiều executor

1. Ngừng nhận **job mới** ở scheduler hoặc admission layer.
2. Ngăn gửi mới tới business executor hoặc broker nếu cần.
3. Ghi lại số in-flight business jobs và trạng thái.
4. Chờ in-flight hoàn tất bằng một deadline rõ hoặc chuyển về durable retry state.
5. Thực hiện bảo trì.
6. Kiểm tra dependencies đã ready, transaction và pool capacity.
7. Resume theo thứ tự, giới hạn burst và giám sát lỗi.

Pause một `ThreadPoolTaskScheduler` là **một bước**, không phải toàn bộ quy trình bảo trì hệ thống phân tán.

## 12. Các vấn đề transaction và cancellation

`@Transactional` trong imperative Spring thường gắn với thread đang thực thi. Nếu một scheduled task đã mở transaction JDBC/JPA, gọi `scheduler.stop()` **không tự rollback transaction đó**. Transaction vẫn theo method/transaction manager, có thể commit hoặc rollback theo nghiệp vụ.

Ngược lại, `Future.cancel(true)` hoặc shutdown cố interrupt thread cũng không bảo đảm một HTTP call/JDBC driver ngay lập tức dừng và rollback mọi side effect. Cần timeout thật, transaction boundary, idempotency và compensation khi có tác động đã commit.

`ThreadLocal`/MDC có thể bám trên scheduler worker được tái sử dụng sau resume. Hãy cleanup `ThreadLocal` trong `finally` hoặc dùng context-propagation mechanism phù hợp. Không copy `EntityManager` hoặc connection bound từ HTTP caller sang scheduler worker.

### Tránh deadlock do pause

Một lỗi logic:

```java
scheduler.stop();
ScheduledFuture<?> pending =
    scheduler.schedule(() -> doWork(), Instant.now());
pending.get(); // Chờ vô hạn nếu không có thread nào resume scheduler.
```

Đây là phản ví dụ: `doWork` được định nghĩa và không có lối `scheduler.start()` khác. `get()` đợi task đang bị pause, khiến control thread có thể không bao giờ gọi resume. Trong thực tế, luôn có deadline và đường recover, không giữ DB lock hay monitor đồng thời chờ drain các tasks đang cần lock ấy.

## 13. Lifecycle của application context

Spring scheduler beans có thể triển khai `SmartLifecycle` với các phương thức:

```java
boolean isRunning();
void start();
void stop();
void stop(Runnable callback);
int getPhase();
```

Đây là nhóm method tiêu biểu từ Spring lifecycle contracts, không phải nội dung đầy đủ của một interface.

Trong context đang sống, một admin control được phép gọi `stop/start` trên scheduler phù hợp. Nhưng `context.close()` khởi động cả quá trình **context-close event, phối hợp dừng lifecycle, bean destruction và shutdown ExecutorService**. Đây không phải pause để resume cùng bean sau đó.

Nếu scheduler có business task cần DB resources, giữ database client sống đủ lâu trong shutdown. Sử dụng `setAwaitTerminationSeconds`, `setWaitForTasksToCompleteOnShutdown` và phase/dependency có chủ đích; đừng chỉ tăng timeout mà không có task I/O timeout, vì một task treo có thể giữ shutdown lâu.

### Version 6.2 và `DEFAULT_PHASE`

`DEFAULT_PHASE = Integer.MAX_VALUE / 2` cho `ExecutorConfigurationSupport` trong Spring 6.2 nhằm để executor/scheduler auto-start sớm hơn nhiều `SmartLifecycle` khác và stop muộn hơn các component có phase cao. Nếu bạn tự gán phase, hãy kiểm tra cả hai chiều start/stop, đặc biệt khi scheduler cần message broker, database hoặc external services trong khi drain.

Nếu có lifecycle bean đặc biệt với phase khác, phải thiết kế dependency/ordering thực sự thay vì mặc định rằng mọi Spring bean đều có phase 0.

## 14. Quyền hạn và quan sát vận hành

Đặt `pause`/`resume` thành HTTP admin endpoint cần authentication, authorization và audit log. Khi scheduler bị pause có chủ đích, health check không nên đơn giản coi `isRunning()==false` là sự cố; nhưng ứng dụng cũng phải thể hiện rõ trạng thái maintenance, nếu không hệ thống giám sát sẽ khó phát hiện một scheduler bị bỏ quên.

Các tín hiệu hữu ích:

| Tín hiệu | Cách đọc |
| --- | --- |
| `scheduler.isRunning()` | Trạng thái lifecycle ở implementation, không chứng minh job thành công. |
| Pause start/stop timestamp | Ai điều khiển, lý do gì, scope scheduler/job/replica. |
| Active tasks | Có tác vụ thực sự đang chạy? |
| Queue/pending registrations | Bao nhiêu việc đã đến hạn nhưng chưa bắt đầu? |
| Scheduler lag | Actual start trễ hơn scheduled deadline bao nhiêu? |
| In-flight business jobs | Những Future đã offload nhưng chưa hoàn tất. |
| Last successful execution | Lần cuối nghiệp vụ thực sự thành công. |
| Failure rate | Có lỗi sau resume hoặc lỗi kéo dài không? |
| DB pool / HTTP pool saturation | Hạ nguồn có chịu được backlog khi resume? |

Một hệ thống điều khiển tốt có thể công bố các trạng thái riêng như `RUNNING`, `PAUSED`, `DRAINING`, `STOPPING`, `STOPPED`, `DEGRADED`. Nhưng đừng đồng nhất chúng với `SmartLifecycle.isRunning()`; đây là trạng thái nghiệp vụ mà ứng dụng phải định nghĩa/ghi nhận.

### Quy trình bảo trì gợi ý

1. Kiểm tra đúng scheduler bean và phạm vi ảnh hưởng; xác nhận quyền điều khiển.
2. Pause new invocations và chờ in-flight tới một deadline.
3. Kiểm tra business executor khác/queue/retry state; không để việc mới vào nơi đang bảo trì.
4. Hoàn tất thay đổi database/service phụ thuộc.
5. Đánh giá backlog, rate limits và khả năng chạy sau resume.
6. Resume đúng phạm vi, không khởi động đồng loạt mọi replica nếu gây tải đột biến.
7. Xác nhận job success chứ không chỉ `isRunning`, và audit toàn bộ thao tác.

## 15. Bảng quyết định chọn cơ chế

| Nhu cầu | Cách làm phù hợp |
| --- | --- |
| Tạm dừng **toàn bộ các registrations của một `ThreadPoolTaskScheduler`** | `stop()` và `start()` trên đúng Spring 6.1+ bean. |
| Tạm dừng và nhận callback khi task in-flight xong | `stop(Runnable callback)` và một bounded drain coordinator. |
| Dừng **một registration** | `ScheduledFuture.cancel(...)`, có thể đăng ký lại khi cần. |
| Chấm dứt scheduler vĩnh viễn khi ứng dụng đóng | `initiateShutdown()`/`shutdown()` và lifecycle context. |
| Tạm dừng jobs ở nhiều replicas | Job ownership/distributed admission control, không chỉ bean-local stop. |
| Cần đợi business tasks được offload | Drain business executor/futures riêng. |
| Cần giữ schedule qua restart | Persistent job store, broker hoặc Quartz với cấu hình thích hợp. |
| Ứng dụng dùng managed Jakarta EE scheduler | Dùng API/policy của container, không tự giả định quyền pause thread. |
| Ứng dụng dùng Quartz | Dùng lifecycle/pause trigger/job và misfire handling của Quartz. |
| Chỉ muốn tránh chạy khi dependency unhealthy | Admission condition + retry/backoff/circuit breaker, lưu state nếu quan trọng. |

## 16. Những sai lầm phổ biến

| Sai lầm | Điều đúng |
| --- | --- |
| “Mọi `TaskScheduler` đều có `pause()/resume()`.” | Interface không khai báo; lifecycle pause/resume thuộc implementation. |
| “Spring 5.x và 6.0.x đã có pause giống 6.1.” | Tính năng này được bổ sung ở 6.1; phải xem API của version thật. |
| “`scheduler.stop()` tương đương `shutdownNow()`.” | `stop()` pause không đợi, không tự interrupt; shutdown là bước kết thúc. |
| “`stop(callback)` dừng ngay task và gọi callback.” | Callback chỉ tới khi các tasks đang chạy hoàn tất. |
| “Pause một bean = pause mọi `@Scheduled` methods.” | Chỉ ảnh hưởng registrations đang dùng bean ấy. |
| “Pause scheduler cũng dừng những `@Async` Futures đã submit.” | Executor đích có lifecycle riêng. |
| “`start()` phục hồi executor sau `shutdown()`.” | Underlying JDK executor đã shutdown không thể restart cùng instance. |
| “Pause tự cancel ScheduledFuture.” | Không phải cancel; registration vẫn có thể pending. |
| “Cancel future sẽ tự chạy lại khi scheduler start.” | Cancel là kết thúc registration; muốn chạy cần đăng ký mới. |
| “Resume sẽ bù đủ mọi missed cron ticks.” | Không có cam kết chung, tùy trigger/version/implementation. |
| “`isRunning=true` nghĩa mọi job khỏe.” | Chỉ là lifecycle, cần business success metrics. |
| “Shutdown flags là pause mode.” | `setWaitForTasksToCompleteOnShutdown` và các flag tương tự chỉ xử lý shutdown. |
| “`ThreadPoolTaskExecutor` cũng là TaskScheduler.” | Đây là execution pool, không cung cấp time-based scheduling SPI. |
| “Raw JDK ScheduledThreadPoolExecutor có SmartLifecycle.” | Nó không phải Spring-managed pause/resume abstraction. |
| “Pause rollback DB transaction đang chạy.” | Transaction và executor lifecycle là hai cơ chế độc lập. |
| “Một tên scheduler bean giống nhau ở nhiều pods nghĩa global pause.” | Mỗi pod có bean riêng; phải điều phối toàn cluster. |
| “Pause không tạo backlog.” | Có thể tích tụ tasks đến hạn, lag và burst sau resume. |

## 17. Checklist kiểm thử và triển khai

1. Runtime Spring Framework có phải 6.1+? Có ghi cụ thể implementation?
2. Đúng bean mà hạ tầng scheduling thực sự sử dụng, hay chỉ một scheduler khác tên gần giống?
3. Đúng scope cần pause: scheduler-wide, job-wide, application-wide hay cluster-wide?
4. Có hiểu `stop/start` là `SmartLifecycle` và không có `TaskScheduler.pause()`?
5. Task in-flight có cần được đợi? `stop(callback)` có bounded timeout bên ngoài?
6. Future đang pending có bị cancel nhầm hoặc chờ `get()` khi paused?
7. Shutdown policy tách biệt với pause, không dùng `shutdown()` thay thế?
8. Sau resume có missed windows, backlog, duplicate runs cần xử lý không?
9. Fixed-rate/fixed-delay/cron trigger đang dùng version và semantics nào?
10. Nếu `@Scheduled` gọi `@Async`, có quản lý business executor riêng?
11. I/O có timeout thật, transaction đúng thread và idempotency?
12. Có nhiều replicas, cần distributed job admission hoặc ownership?
13. `SmartLifecycle.getPhase()` và dependencies shutdown đã được kiểm tra?
14. Security admin endpoint có authentication/authorization/audit?
15. Có metrics active/pending/lag/success/failure sau resume?
16. Unit/integration test có latch/future để chứng minh task pending và callback order, không chỉ `Thread.sleep`?
17. Có phương án khôi phục nếu process crash khi maintenance đang diễn ra?

## 18. Câu hỏi tự kiểm tra

**Câu 1.** `TaskScheduler` interface có hai phương thức `pause()`/`resume()` không?

**Đáp án:** Không. Tính năng pause/resume là khả năng của implementation. Với `ThreadPoolTaskScheduler` Spring 6.1+, dùng `stop()`/`start()` của lifecycle.

**Câu 2.** Spring Framework phiên bản nào bổ sung pause/resume có tích hợp lifecycle cho `ThreadPoolTaskScheduler`?

**Đáp án:** 6.1.

**Câu 3.** Khi task A đã chạy, gọi `scheduler.stop()`. A có bị interrupt ngay không?

**Đáp án:** Không theo hợp đồng pause. `stop()` không đợi và không tự interrupt, nên A có thể tiếp tục hoàn thành.

**Câu 4.** Khi nào `stop(Runnable callback)` phát callback?

**Đáp án:** Sau khi các task đang thực thi tại thời điểm pause hoàn tất; không phải khi tất cả Future ở executor ngoài đã xong.

**Câu 5.** One-shot task được đăng ký khi scheduler paused có bị hủy không?

**Đáp án:** Không tự động chỉ vì pause. Nếu chưa cancel/shutdown, nó có thể chạy sau resume khi có worker, như ví dụ.

**Câu 6.** Vì sao không nên gọi `scheduler.shutdown()` để pause khi bảo trì ngắn?

**Đáp án:** Shutdown của underlying executor là trạng thái kết thúc; `start()` không hồi sinh cùng executor đã shutdown.

**Câu 7.** Một `@Scheduled` tick đã submit `CompletableFuture` và return. `stop(callback)` có đợi future ấy không?

**Đáp án:** Không tự động. Nó chỉ quan sát execution thuộc scheduler; business future/executor khác có lifecycle độc lập.

**Câu 8.** Điều gì thay đổi ở default lifecycle phase của `ExecutorConfigurationSupport` trong Spring 6.2?

**Đáp án:** Default phase là `Integer.MAX_VALUE / 2`, thấp hơn `SmartLifecycle.DEFAULT_PHASE` thông thường.

**Câu 9.** Nếu có ba replicas dùng cùng cron, pause scheduler của pod A có dừng B/C không?

**Đáp án:** Không. Mỗi JVM có scheduler riêng; cần điều phối nhiều replicas nếu muốn global pause.

**Câu 10.** Có thể tin rằng mọi cron missed ticks được phát lại sau resume không?

**Đáp án:** Không. Tùy scheduler/trigger/version. Nếu cần xử lý mọi time window, lưu durable watermark và window IDs.

**Câu 11.** Vì sao `isRunning()` sau resume không phải business health check đầy đủ?

**Đáp án:** Nó phản ánh lifecycle, không xác nhận rằng các jobs đã hoàn tất thành công.

**Câu 12.** `ScheduledFuture.cancel(false)` có giống `scheduler.stop()` không?

**Đáp án:** Không. Cancel một registration; stop pause toàn scheduler bean trong phạm vi implementation.

**Câu 13.** Tại sao `ThreadPoolTaskExecutor` không thay thế `ThreadPoolTaskScheduler` dù cũng có pause/resume?

**Đáp án:** Nó là task execution pool, không tự cung cấp scheduling-by-Instant/Trigger của `TaskScheduler`.

**Câu 14.** Pause scheduler có rollback transaction JDBC đang chạy không?

**Đáp án:** Không. Transaction có ranh giới và quy tắc commit/rollback độc lập.

**Câu 15.** Demo xác nhận một task chưa chạy trong lúc paused bằng cách nào?

**Đáp án:** Pause trước khi register one-shot task, rồi kiểm tra count vẫn bằng 0 và future chưa done; sau resume chờ future hoàn tất. Latch kiểm tra in-flight và callback phase khác.

### Ghi nhớ

Trên **Spring Framework 6.1+**, `ThreadPoolTaskScheduler` hỗ trợ pause/resume qua **`stop()` và `start()`**, do `ExecutorConfigurationSupport` triển khai `SmartLifecycle`. `stop(Runnable callback)` cho phép đợi những task đang thực thi kết thúc; pause không tự ngắt chúng, cũng không tương đương shutdown, cancel registration hay rollback. Không suy ra mọi `TaskScheduler` hay mọi scheduler trong Spring có cùng đặc điểm, và không nhầm `ThreadPoolTaskExecutor` với scheduler. Khi resume, phải xử lý khả năng các mốc đến hạn đã bị trễ, backlog và các executor/replicas khác. Muốn duy trì dịch vụ đáng tin cần job state bền vững, idempotency, timeout, metrics và quyền điều khiển rõ ràng.

### Tài liệu tham khảo chính thức

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): TaskScheduler implementations và mô tả pause/resume, graceful shutdown từ Spring 6.1.
- [Spring Framework 6.2 — ExecutorConfigurationSupport](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html): `start`, `stop`, `stop(Runnable)`, `isRunning`, shutdown flags, lifecycle phase.
- [Spring Framework 6.2 — ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): pool implementation, scheduler worker, ScheduledFuture và shutdown policies.
- [Spring Framework — What's New in 6.1](https://github.com/spring-projects/spring-framework/wiki/What's-New-in-Spring-Framework-6.x): lifecycle pause/resume được giới thiệu trong 6.1.
- [Spring Framework — SmartLifecycle](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/context/SmartLifecycle.html): phases, callback và coordinated stop.
- [Java SE 17 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html): delayed tasks và scheduled task policies.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): shutdown semantics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-shutdown-009">Giữ tài nguyên phụ thuộc còn hoạt động tới khi tác vụ cần dùng kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ tài nguyên phụ thuộc còn hoạt động tới khi tác vụ cần dùng kết thúc

> **Module:** Spring Framework – concurrency  
> **Nhóm:** Vòng đời và dừng  
> **Mục tiêu:** Thiết kế shutdown của Spring application sao cho công việc `@Async`, `TaskExecutor`, `TaskScheduler` đang chạy **không mất DataSource, HTTP client, producer, file handle hoặc service phụ thuộc trước khi hoàn tất**. Phân biệt ngừng nhận việc, chờ task thoát và thứ tự hủy bean; cấu hình `ThreadPoolTaskExecutor`, `@DependsOn`, `SmartLifecycle` có chủ đích; kiểm thử với latch và biết giới hạn của shutdown timeout, interruption, container termination và recovery sau crash.

## 1. Vấn đề: executor còn task nhưng tài nguyên đã bị đóng

Một worker nền thường phụ thuộc vào nhiều thứ: `DataSource` để ghi DB, HTTP client để gọi đối tác, Kafka producer để xuất event, bộ lưu file, cache hoặc một service khác. Những dependency này thường là **Spring singleton beans** và sẽ bị đóng trong quá trình dừng `ApplicationContext`.

Nguy cơ xảy ra khi ứng dụng bắt đầu shutdown:

~~~text
t0  Request tiếp nhận -> @Async task được submit
t1  Worker đang chạy và còn cần DB/HTTP/broker
t2  Spring bắt đầu đóng ApplicationContext
t3  DataSource/HTTP client bị close
t4  Worker tiếp tục gọi resource đã đóng
    -> SQLException / "Pool has been shutdown" / client closed
t5  Task thất bại, dữ liệu mới xử lý được một phần
~~~

Không đủ để hỏi “executor có dừng chưa?”; phải hỏi **tại thời điểm tài nguyên bị đóng, còn task nào sẽ cần dùng nó không?** Đây là ràng buộc về vòng đời giữa **task consumer** và **resource provider**, không chỉ là một tùy chọn thread pool.

**Hợp đồng mong muốn:** Sau khi nhận lệnh shutdown, hệ thống ngừng nhận công việc mới, cho công việc đã được chấp nhận hoàn thành hoặc dừng có kiểm soát trong một deadline, rồi mới giải phóng các tài nguyên mà chúng cần. Nếu deadline hết, phải có chính sách interruption, lưu trạng thái/retry và tránh tuyên bố task thành công khi chưa hoàn tất.

## 2. Phân biệt ba giai đoạn shutdown

Một shutdown tốt gồm ba giai đoạn có ý nghĩa khác nhau:

1. **Quiesce / stop admission:** ngừng nhận HTTP request mới, pause consumer, stop poller/cron phát thêm task và từ chối submissions mới theo policy.
2. **Drain / finish in-flight:** chờ những task đã được **chấp nhận** hoàn tất, commit DB, flush/ack message và release các tài nguyên tạm thời trong giới hạn thời gian.
3. **Dispose dependencies:** đóng `DataSource`, HTTP client, message producer, file store và các bean khác sau khi không còn worker có quyền sử dụng chúng.

~~~text
RUNNING
    |
    v  shutdown requested
QUIESCING ----> stop admitting NEW work
    |
    v
DRAINING -----> wait for accepted/in-flight tasks
    |           -> SUCCESS | FAILED | TIMED_OUT
    v
DISPOSING ----> close resource beans
    |
    v
TERMINATED
~~~

Một ứng dụng có thể thực hiện các giai đoạn **chồng lấn có kiểm soát**, nhưng điều quan trọng là không đóng dependency quá sớm. Khi một task còn giữ kết nối đến DB, một cuộc gọi `dataSource.close()` thủ công ở `@PreDestroy` của bean khác có thể làm hỏng công việc dù executor đã được cấu hình chờ.

`ContextClosedEvent` chỉ báo context đang bước vào quá trình đóng. Nó **không** chứng minh mọi task đã dừng, mọi bean đã bị destroy hoặc mọi transaction đã commit. Không dùng event này như tín hiệu rằng dependency chắc chắn đã sẵn sàng đóng ngay.

## 3. Tác vụ, executor và dependency là ba chủ thể riêng

Giả sử hệ thống có cấu trúc:

~~~text
OrderEndpoint / KafkaListener
       |
       | submit
       v
ThreadPoolTaskExecutor "workerExecutor"
       |
       | run
       v
OrderAsyncWorker
       |
       +-- DataSource / HikariCP
       +-- HTTP client / payment gateway
       +-- KafkaTemplate / producer
       +-- disk/object storage
~~~

Những quan hệ cần phân biệt:

- **Đường submit:** endpoint/consumer gọi vào executor. Khi shutdown bắt đầu, đường này phải ngừng trước khi executor hết khả năng nhận task.
- **Đường thực thi:** executor còn worker và task đang xử lý, có thể đang blocking trên I/O.
- **Đường phụ thuộc:** task có thể gọi resource nhiều lần, kể cả lúc cuối để commit/flush/ack hoặc xử lý lỗi.
- **Đường Spring bean lifecycle:** các singleton/SmartLifecycle bean có initialization, stop và destruction; thứ tự này cần được kiểm soát khi service phụ thuộc nhau.

Spring biết dependency giữa các bean được inject, nhưng **không tự phân tích closure của từng Runnable** để suy ra nó cần DB pool tới lúc nào. Một lambda được giữ trong executor có thể giữ một tham chiếu tới resource mà Spring container không thấy là một dependency tường minh của executor bean.

Vì vậy cần thiết kế ranh giới và điều phối shutdown chủ động. “Spring sẽ tự đóng mọi thứ” là chính xác về **quản lý bean**, nhưng không đủ để bảo đảm **mọi tác vụ tùy biến đã drain xong trước dependency** trong mọi cấu hình.

## 4. Tại sao @PreDestroy đơn giản có thể gây race?

Một anti-pattern:

~~~java
@PreDestroy
public void stop() {
    dataSource.close();
}
~~~

Đây là **snippet phản ví dụ**, `dataSource` phải được định nghĩa trong bean thực tế; không nên sao chép để sử dụng. Nếu một task vẫn truy vấn DB, đóng resource ngay trong callback sẽ tạo cuộc đua.

Một cách khác cũng chưa đủ:

~~~java
@PreDestroy
public void stop() {
    executor.shutdown();
    dataSource.close();
}
~~~

Đây cũng chỉ là pseudocode ở mức ý tưởng. Tùy executor, `shutdown()` có thể **chỉ ra tín hiệu không nhận thêm task** chứ không chờ task thực tế hoàn tất. Với `ExecutorService` của JDK, `shutdown()` và `awaitTermination(...)` là hai bước khác nhau. Với Spring `ThreadPoolTaskExecutor`, lifecycle nằm trong `ExecutorConfigurationSupport`.

**Không tự close tài nguyên do Spring quản lý hai lần:** nếu `DataSource` là Spring bean có destroy method, hãy để container điều khiển destruction order, không vừa gọi `dataSource.close()` thủ công vừa để Spring gọi lại trừ khi API thực sự idempotent và việc đóng hai lần có chủ đích.

Đồng thời, `@PreDestroy` của bean nghiệp vụ và `@PreDestroy` của resource khác **không mặc nhiên mô tả** “worker xong mới close”. Phải xác định dependency graph, executor drain và `SmartLifecycle` stop phases.

## 5. ThreadPoolTaskExecutor: hai cấu hình phải hiểu đúng

Với Spring Framework 6.2, `ThreadPoolTaskExecutor` kế thừa các tùy chọn từ `ExecutorConfigurationSupport`:

~~~java
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(4);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(100);
executor.setThreadNamePrefix("jobs-");

// Cho các task đang chạy và task đã queue có cơ hội hoàn tất.
executor.setWaitForTasksToCompleteOnShutdown(true);

// Khi destroy executor, CHỜ tối đa 30 giây trước khi
// container tiếp tục bước destroy resource tiếp theo.
executor.setAwaitTerminationSeconds(30);
~~~

Đây là **đoạn cấu hình Spring**, thường đặt trong một `@Bean` thay vì tự tạo trong business method. Ý nghĩa hai flag **không đồng nhất**:

| API | Tác dụng chủ yếu | Điều KHÔNG được suy luận |
| --- | --- | --- |
| `setWaitForTasksToCompleteOnShutdown(true)` | Khi shutdown, dùng đường đi cho phép các task đã chấp nhận và queued tiếp tục hoàn tất, không chủ động interrupt running task như chính sách immediate shutdown | **Không đồng nghĩa tự động block toàn bộ container** đến khi mọi task xong |
| `setAwaitTerminationSeconds(n)` | Khi executor shutdown/destroy, block tối đa `n` giây chờ task kết thúc trước khi tiếp tục destruction | **Không phải chờ vô hạn**; hết hạn container có thể đi tiếp và đóng resource |
| `setStrictEarlyShutdown(true)` | Yêu cầu `ThreadPoolTaskExecutor` phát tín hiệu early shutdown nghiêm ngặt khi context close | **Không tự giải quyết dependency ordering** |
| `setAcceptTasksAfterContextClose(true)` | Cho phép nhận việc muộn ở giai đoạn close theo cơ chế Spring tương ứng | **Không nên bật chỉ để “sửa lỗi TaskRejected”**; có thể kéo dài shutdown |
| `setThreadNamePrefix(...)` | Đặt tên thread phục vụ quan sát | **Không** tác động shutdown ordering |

**Cảnh báo chính:** Javadoc Spring nêu rõ việc `setWaitForTasksToCompleteOnShutdown(true)` không tự bảo đảm container chờ hoàn tất mọi task trước khi hủy bean khác. Muốn giữ resource cần cho task, phải phối hợp **await termination** và **thứ tự dependency**, hoặc một shutdown coordinator có tương đương.

Đừng chọn `awaitTerminationSeconds=300` chỉ vì “an toàn”: giá trị phải nằm trong shutdown budget của process/orchestrator và được giám sát. Một task treo lâu hơn deadline vẫn phải được xử lý theo recovery policy.

## 6. Kiến thức version: late submissions và lifecycle Spring 6.2

Spring 6.1 và 6.2 bổ sung/chỉnh một số hành vi shutdown của executor. Trên `ThreadPoolTaskExecutor` Spring 6.2, `setStrictEarlyShutdown(false)` là mặc định từ 6.1.4. Thiết lập này cho phép hành vi tương đối linh hoạt với **late tasks** qua coordinated lifecycle stop phase trong một số trường hợp; nó **không phải cam kết hệ thống tiếp tục nhận mọi submit mãi trong lúc context đóng**.

Khi đặt `setWaitForTasksToCompleteOnShutdown(true)`, Spring dùng một đường shutdown muộn hơn ở bước destruction thay vì hoàn toàn dựa vào coordinated lifecycle stop; điều này có thể ảnh hưởng cách các `SmartLifecycle` bean khác dừng.

Các điểm phải kiểm tra cho đúng version:

1. Executor có nhận `ContextClosedEvent` không và có policy early shutdown nào?
2. Có worker nào còn submit task trong `@PreDestroy`/stop callback?
3. `waitForTasksToCompleteOnShutdown` có khiến queued task được chạy hết không?
4. `awaitTerminationSeconds` có đủ để **block destruction** tới khi resource được dùng xong không?
5. Nếu bean resource **cũng là `SmartLifecycle`**, nó ngừng phục vụ ở phase nào, trước hay sau worker drain?
6. Khi task bị interrupt/rejected, error handling có lưu trạng thái nghiệp vụ không?

Không dựa vào một câu “Spring graceful shutdown mặc định là X” áp dụng cho mọi version hoặc loại executor. `ThreadPoolTaskExecutor`, `ThreadPoolTaskScheduler`, `SimpleAsyncTaskExecutor` và một `ExecutorService` tự tạo có các quy tắc khác nhau.

## 7. @DependsOn: đảm bảo thứ tự destroy của singleton bean

Spring hỗ trợ `@DependsOn` để khai báo một bean cần một bean khác **được tạo trước**, và đối với singleton, bean **phụ thuộc bị destroy trước** bean được phụ thuộc.

Nếu executor phải **drain xong trước khi một resource đóng**, có thể nêu dependency tường minh:

~~~java
@Bean("workerExecutor")
@DependsOn("dataResource")
ThreadPoolTaskExecutor workerExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(20);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
}

@Bean(destroyMethod = "close")
DataResource dataResource() {
    return new DataResource();
}
~~~

Đây là **snippet tích hợp**, `DataResource` cần có `close()`, các imports và configuration class không được hiển thị. Điều quan trọng là hướng dependency: **executor phụ thuộc resource**, nên Spring destroy executor **trước**, rồi mới destroy resource. Nếu executor.destroy() chờ hết các task đang cần resource, resource vẫn sống trong khoảng chờ.

**Không đảo chiều `@DependsOn`:** đặt `@DependsOn("workerExecutor")` lên resource sẽ làm resource phụ thuộc executor và có thể bị destroy **trước executor**, ngược với mong muốn.

`@DependsOn` không phải một `CountDownLatch`. Nó chỉ đưa ra **ordering** trong container; nếu executor destroy không chờ task hoặc timeout hết, task vẫn có thể dùng resource đã bị đóng. Với resource thuộc một context khác, process khác hoặc quản lý ngoài Spring, quy tắc bean destroy không bao phủ resource đó.

## 8. Ví dụ chạy được Java 17 + Spring Framework 6.2.9

Ví dụ dùng một `DataResource` giả lập cho DB/HTTP client, một `@Async("workerExecutor")` worker và một `ContextClosedEvent` listener. Main chủ động tạo race có kiểm soát:

1. Submit task, giữ task ở `CountDownLatch` để nó chưa chạm tới resource ở bước cuối.
2. Khởi động thread khác để đóng `ApplicationContext`.
3. Chờ `ContextClosedEvent`, xác nhận shutdown đã **bắt đầu** nhưng resource vẫn mở.
4. Thả latch cho worker sử dụng resource, chờ future hoàn tất.
5. Kiểm tra context close xong và resource chỉ đóng **sau** khi worker hoàn thành bước cần dùng nó.

Cấu hình kết hợp `setWaitForTasksToCompleteOnShutdown(true)`, `setAwaitTerminationSeconds(8)` và `@DependsOn("dataResource")`. Không dùng `Thread.sleep` để “hy vọng” race rơi vào thời điểm mong muốn.

### Cấu trúc Maven

~~~text
resource-lifetime-demo/
├── pom.xml
└── src/main/java/demo/ResourceLifetimeDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>resource-lifetime-demo</artifactId>
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
  <build><plugins>
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
  </plugins></build>
</project>
~~~

### src/main/java/demo/ResourceLifetimeDemo.java

~~~java
package demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ResourceLifetimeDemo {
    private static final CountDownLatch STARTED = new CountDownLatch(1);
    private static final CountDownLatch RELEASE = new CountDownLatch(1);
    private static final CountDownLatch CLOSING = new CountDownLatch(1);

    public static class DataResource implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean workFinished = new AtomicBoolean();
        private final AtomicBoolean closedTooEarly = new AtomicBoolean();

        boolean doFinalStep() {
            boolean wasOpen = !closed.get();
            workFinished.set(true);
            return wasOpen;
        }

        boolean isClosed() {
            return closed.get();
        }

        boolean closedTooEarly() {
            return closedTooEarly.get();
        }

        @Override
        public void close() {
            closedTooEarly.set(!workFinished.get());
            closed.set(true);
        }
    }

    public static class AsyncWorker {
        private final DataResource resource;

        public AsyncWorker(DataResource resource) {
            this.resource = resource;
        }

        @Async("workerExecutor")
        public CompletableFuture<Boolean> finishUsingResource() {
            STARTED.countDown();
            try {
                if (!RELEASE.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Worker release timed out");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            return CompletableFuture.completedFuture(resource.doFinalStep());
        }
    }

    public static class ClosingSignal {
        @EventListener(ContextClosedEvent.class)
        public void whenCloseRequested() {
            CLOSING.countDown();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    public static class AppConfig {
        @Bean(destroyMethod = "close")
        DataResource dataResource() {
            return new DataResource();
        }

        @Bean("workerExecutor")
        @DependsOn("dataResource")
        ThreadPoolTaskExecutor workerExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(2);
            executor.setThreadNamePrefix("resource-worker-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(8);
            return executor;
        }

        @Bean
        AsyncWorker asyncWorker(DataResource dataResource) {
            return new AsyncWorker(dataResource);
        }

        @Bean
        ClosingSignal closingSignal() {
            return new ClosingSignal();
        }
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(AppConfig.class);
        DataResource resource = context.getBean(DataResource.class);
        CompletableFuture<Boolean> task =
            context.getBean(AsyncWorker.class).finishUsingResource();

        boolean started = STARTED.await(3, TimeUnit.SECONDS);
        Thread closer = new Thread(context::close, "context-closer");
        closer.start();

        boolean closeRequested = CLOSING.await(3, TimeUnit.SECONDS);
        boolean stillOpen = !resource.isClosed();
        RELEASE.countDown();

        boolean taskUsedOpenResource = task.get(5, TimeUnit.SECONDS);
        closer.join(5000);

        System.out.println("Task started: " + started);
        System.out.println("Close event observed: " + closeRequested);
        System.out.println("Resource open at close event: " + stillOpen);
        System.out.println("Task used open resource: " + taskUsedOpenResource);
        System.out.println("Context close completed: " + !closer.isAlive());
        System.out.println("Resource closed after task: " +
            (resource.isClosed() && !resource.closedTooEarly()));
    }
}
~~~

Chạy bằng JDK 17+ và Maven:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.ResourceLifetimeDemo
~~~

**Stdout thực tế đã kiểm tra** khi chạy Spring Framework 6.2.9, target Java 17:

~~~text
Task started: true
Close event observed: true
Resource open at close event: true
Task used open resource: true
Context close completed: true
Resource closed after task: true
~~~

Ví dụ đã được **biên dịch bằng `--release 17` và thực thi thành công** trên một JDK mới hơn; sáu dòng stdout được đối chiếu. Nó xác nhận trong **kịch bản có đủ thời gian drain**, resource chưa bị đóng ở thời điểm worker cần dùng, và được đóng sau khi task hoàn tất.

**Giới hạn:** `DataResource` chỉ mô phỏng một resource có `close()`, không phải HikariCP hay HTTP client thật. `ContextClosedEvent` dùng để xác nhận thời điểm bắt đầu shutdown; chính việc executor chờ trong destruction và dependency ordering mới bảo vệ resource. Trên máy chậm, latch có timeout 3–5 giây nên có thể gây test timeout; đó là cơ chế phát hiện test bị treo, không phải cam kết thời gian phản hồi cố định.

## 9. Vì sao @DependsOn + chờ executor cùng cần thiết?

Xét ba cấu hình:

| Cấu hình | Điều có thể xảy ra | Rút kinh nghiệm |
| --- | --- | --- |
| Chỉ `@DependsOn("dataResource")` cho executor | Executor được destroy trước resource nhưng có thể ngừng ngay, cancel/interrupt task hoặc timeout mà worker chưa xong | Thứ tự destroy **không phải** phép chờ công việc |
| Chỉ `setWaitForTasksToCompleteOnShutdown(true)` | Executor cho task đã submit tiếp tục, nhưng container có thể tiến tới destroy resource khác trước khi task hoàn tất | Cho phép hoàn tất **không có nghĩa block shutdown** |
| Chỉ `setAwaitTerminationSeconds(30)` | Có chờ trong shutdown nhưng nếu resource đã destroy trước executor hoặc resource ngừng ở lifecycle phase sớm, vẫn có rủi ro | Chờ đúng chỗ nhưng phải bảo toàn dependency ordering |
| **`@DependsOn` + wait + await** | Executor có cơ hội drain và block trước khi resource singleton được destroy | Phù hợp khi resource chỉ đóng qua singleton destroy và task kết thúc đúng timeout |
| Resource có `SmartLifecycle.stop()` riêng | Resource có thể ngừng phục vụ **trước destroy**, dù bean chưa gọi `close()` | Cần thêm phase/dependency/coordinator ở lifecycle stop |

Với `@Bean` có parameter `DataResource` trong executor factory method, Spring cũng có thể suy ra dependency; tuy nhiên dùng `@DependsOn` làm ràng buộc destroy **rõ ràng** ngay trong ví dụ khi executor cần resource để task hoàn tất, thay vì chỉ dựa vào closure của một `Runnable`.

Điều quan trọng nhất là **không đưa ra lời hứa toàn cục chỉ bằng config**. Nếu một resource nằm ngoài context Spring, bị đóng bởi SDK khác, hoặc bị Kubernetes kill trước deadline, các setter trên executor không thể kéo dài tuổi thọ resource đó.

## 10. SmartLifecycle và thứ tự stop khác với thứ tự destroy

Spring có hai lớp lifecycle đáng phân biệt:

- **Stop phase:** `SmartLifecycle` cho phép bean báo không còn chạy nữa; `DefaultLifecycleProcessor` điều phối `stop(Runnable)` theo phase và callback hoàn tất.
- **Destroy phase:** singleton `@PreDestroy`, `DisposableBean.destroy()`, `@Bean(destroyMethod=...)` giải phóng bean. `@DependsOn` ảnh hưởng destruction order.

Với `SmartLifecycle`, **phase cao hơn được stop trước** trong shutdown, phase thấp hơn stop sau. Ràng buộc dependency tường minh có thể ưu tiên hơn phase. Một gateway nhận việc mới có thể cần stop sớm, executor drain sau đó, rồi resource adapter dừng cuối.

Nhưng **đừng nhầm thứ tự stop và destroy**. Nếu resource là `SmartLifecycle` và tự vô hiệu hóa I/O ngay khi nhận `stop()`, việc bean resource chưa bị destroy vẫn **không giúp worker sử dụng được resource**. Đó là lý do ví dụ mục 8 chủ động dùng `AutoCloseable` resource đơn giản để kiểm chứng destruction-time lifetime.

Một quy trình production cần nắm rõ:

1. Ai bắt đầu shutdown? `ContextClosedEvent`, lifecycle processor, orchestrator?
2. Producer/admission gateway thuộc phase nào, dừng tạo task mới khi nào?
3. Executor ngừng nhận và chờ task ở coordinated stop hay bước destroy?
4. Resource có `stop()` tự đóng kết nối trước khi `destroy()` không?
5. Quan hệ `depends-on` đã đúng hướng chưa?
6. Nếu timeout hết, resource vẫn bị đóng khi nào và task recovery ra sao?

**Không đặt phase tùy tiện** chỉ để thấy log đúng thứ tự trong một lần test. Hãy có integration test kiểm tra I/O còn dùng được khi callback đang drain.

## 11. ThreadPoolTaskScheduler và @Scheduled có ranh giới khác

Với `@Scheduled`, ứng dụng có thể dùng `ThreadPoolTaskScheduler` thay vì `ThreadPoolTaskExecutor`. Scheduler tạo **các lượt chạy theo thời gian**, trong khi executor thông thường nhận task do caller submit.

Shutdown của scheduler cần quyết định **hai việc khác nhau**:

1. **Ngừng phát thêm tick trong tương lai** từ cron/fixed-rate/fixed-delay.
2. **Giữ dependency cho lượt đang chạy** đến khi lượt đó thoát hoặc tới deadline.

Không thể “đợi mọi lượt cron tương lai” vì lịch có thể tiếp tục mãi. Mục tiêu thực tế là **stop future triggering**, sau đó **drain in-flight occurrences** đã bắt đầu hoặc đã được accept theo policy.

Spring `ThreadPoolTaskScheduler` có các tùy chọn shutdown ở tầng `ExecutorConfigurationSupport`, nhưng hành vi delayed tasks/periodic tasks còn phụ thuộc policy của underlying `ScheduledThreadPoolExecutor` và các setter chuyên biệt. Không sao chép nguyên cấu hình của `ThreadPoolTaskExecutor` rồi tin chắc một cron recurring sẽ chạy hết các lần tương lai.

Một task scheduled nếu gọi `@Async` service có thể **chỉ submit** rồi return. Lượt scheduler đã hoàn thành nhưng async task thực tế vẫn đang dùng DB. Do đó shutdown cần theo dõi **executor làm việc thực sự**, không chỉ `ScheduledFuture` của cron.

## 12. @Async proxy, CompletableFuture và khoảng cách giữa submit với completion

Một `@Async` method muốn được chạy trên executor cấu hình phải được gọi **qua Spring-managed proxy**. Trong ví dụ mục 8:

~~~java
CompletableFuture<Boolean> result =
    context.getBean(AsyncWorker.class).finishUsingResource();
boolean completed = result.get(5, TimeUnit.SECONDS);
~~~

Đây là **snippet từ chương trình chạy được ở mục 8**, không phải code độc lập. Caller giữ future để quan sát completion; demo đợi nó trước khi xác nhận trạng thái.

Trong sản phẩm thực tế, một API trả `202 Accepted` có thể chỉ mang nghĩa **đã tiếp nhận task**, chưa phải hoàn tất business effect. Nếu shutdown xảy ra, worker vẫn có thể đang xử lý. `@Async void` khiến caller khó theo dõi result hơn; cần trạng thái công việc hoặc instrumentation nếu có SLA.

**Đừng nhầm** lời gọi `@Async` với propagation của `@Transactional`. Imperative transaction context của Spring gắn với thread; worker chạy trên thread khác không tự tiếp tục transaction của caller. Worker nên mở transaction riêng đúng ranh giới, release connection và flush/commit trước khi job được đánh dấu finished.

Một lời gọi self-invocation như `this.finishUsingResource()` trong cùng bean thường bỏ qua async advice ở proxy mode. Khi đó work có thể chạy đồng bộ, làm test shutdown “an toàn” giả tạo vì không có task nền thực sự.

## 13. Những resource cần giữ sống tới cuối task

| Resource | Khi đóng quá sớm | Điều cần xác minh |
| --- | --- | --- |
| HikariCP/`DataSource` | Task nhận lỗi pool closed hoặc query/commit thất bại | Còn transaction/connection nào đang dùng? Timeout DB phù hợp? |
| HTTP client/connection manager | Không mở được request mới hoặc I/O bị gián đoạn | Task có retry/timeout, còn dùng client tới cuối? |
| Kafka producer hoặc Rabbit client | Publish/flush/ack lỗi hoặc message chưa được broker xác nhận | Có flush/ack và outbox nếu không được mất message? |
| File stream/buffered writer | `IOException`, dữ liệu chưa được flush hoặc incomplete file | Flush/close sau write, atomic file rename khi cần |
| Object-storage SDK client | Upload multipart dở dang | Có checkpoint/cancel/cleanup và retry idempotent? |
| Database transaction manager / EntityManager factory | Task không thể mở/commit transaction mới | Worker hoàn tất trước bean hủy, không dùng session của caller |
| Cache/client connection | Task không thể update/invalidate cache | Có thể khôi phục/invalidate lại sau restart? |
| Logging/tracing exporter | Dấu vết cuối cùng bị mất | Flush telemetry sau task hoặc dùng shutdown hook đúng thứ tự |

Không phải mọi resource có cùng stop semantics. Ví dụ `DataSource.close()` không nhất thiết đồng nghĩa rollback mọi transaction đang chạy giống nhau ở mọi pool/driver; broker producer `close()` có timeout/flush policy riêng. Hãy đọc Javadoc và đo hành vi trên **phiên bản SDK cụ thể**.

Một request có thể đi qua nhiều resource: DB ghi xong, broker chưa ack, object store mới upload một phần. **Giữ resource lâu hơn** giúp task có cơ hội kết thúc, nhưng không tự tạo atomic transaction xuyên qua mọi hệ thống. Với side effect quan trọng, dùng outbox, idempotency, saga hoặc reconciliation.

## 14. Shutdown budget: có giới hạn, không chờ vô hạn

Giả sử platform chỉ cho tiến trình **30 giây** kể từ lúc nhận tín hiệu stop đến lúc bị kill cứng. Bạn không thể cho tất cả giai đoạn cùng tối đa 30 giây mà kỳ vọng tổng luôn phù hợp:

~~~text
Total shutdown budget, ví dụ: 30s
    |
    +-- mark unready / stop ingress:  3s
    +-- consumer stop/fetch pause:    2s
    +-- worker drain + DB commit:    18s
    +-- broker/client flush:         4s
    +-- final cleanup / margin:      3s
                                   -----
                                    30s
~~~

Các số trên chỉ minh họa cách **phân bổ budget**, không phải cấu hình mặc định của Spring/Kubernetes. Với container orchestrator, phải xác minh termination grace period, preStop hook, proxy/load-balancer propagation và workload thực tế.

**`setAwaitTerminationSeconds(30)` không bảo đảm task xong trong 30 giây.** Nó chỉ giới hạn thời gian Spring block khi executor shutdown; quá thời hạn, context có thể tiếp tục destroy resource trong khi task còn dang dở. Mặt khác, nếu timeout quá dài so với OS kill deadline, tiến trình có thể bị kill trước khi bean destroy hoàn tất.

Nên có deadline tổng cho task, connect/read/query timeouts theo downstream, cancellation policy rõ, và khả năng khôi phục từ một công việc không chắc kết quả. Hệ thống chỉ đạt graceful shutdown khi **thời gian chạy thực tế** của công việc tương thích với budget đã cấp.

## 15. Ngừng nhận việc mới trước khi drain

Một executor đang drain mà producer vẫn liên tục submit sẽ gặp hai vấn đề: shutdown kéo dài vì có task mới, và request mới bị `TaskRejectedException` không có đường xử lý rõ.

Các bước nên tách:

1. Đánh dấu instance **not ready** và ngừng admission tại entrypoint.
2. Pause Kafka/Rabbit/queue consumer hoặc dừng polling/scheduled triggers; xác định ai đang sở hữu message chưa ack.
3. Chặn các request quản trị tạo background jobs mới qua một **admission gate** dùng trạng thái thread-safe.
4. Chờ pipeline đang inflight nộp xong rồi quyết định executor sẽ nhận/cancel các submissions còn lại thế nào.
5. Đợi executor drain theo policy, sau đó mới đóng resource.
6. Nếu không drain kịp, ghi trạng thái unfinished để service khôi phục, không tự báo success.

Pseudocode cho admission gate:

~~~text
RUNNING:
  accept new task under controlled policy

CLOSING:
  reject new external work with controlled response
  pause external message delivery
  let accepted work finish within deadline

CLOSED:
  no new work; dependencies can be disposed
~~~

Một `AtomicBoolean closing` chỉ ngăn được một số race. Nếu cần bảo đảm “đã chấp nhận request thì chắc chắn submit trước shutdown”, cần **atomic admission + in-flight count/ownership** hoặc job queue bền vững. Không có flag đơn giản nào tự giải quyết hoàn toàn tranh chấp “kiểm tra closing trước, submit sau”.

## 16. Theo dõi in-flight và task con

Thread pool có các metric như active count và queue size, nhưng **chúng có ý nghĩa giới hạn**:

- `activeCount == 0` ở một thời điểm không chứng minh không còn ai đang chuẩn bị submit task mới.
- `queueSize == 0` không chứng minh các callback đã nộp một `CompletableFuture` con sang executor khác đều đã hoàn tất.
- `CompletableFuture.allOf(...)` chỉ chờ **những future được biết và truyền vào**, không tự dò mọi future bị bỏ quên.
- `@Async void` và fire-and-forget không mang return future cho caller; nếu quan trọng cần registry/ledger.
- `ScheduledFuture.isDone()` của một lịch lặp không phải trạng thái tất cả business occurrences.
- Future có thể completed exceptionally trước khi side effect ngoài hệ thống được đối soát xong.

Một mô hình vận hành hữu ích là tracking `accepted`, `started`, `completed`, `failed`, `canceled` và `inFlight` ở **một điểm chịu trách nhiệm rõ ràng**. Với workload nhiều node, còn có desired state/owner epoch và database occurrence ledger.

Đừng dựa vào thread name hay số lượng virtual threads để kết luận một task có còn cần resource hay không. Quan sát kết quả nghiệp vụ và trạng thái **được commit** mới là mục đích cuối cùng.

## 17. Task bị interrupt không có nghĩa dependency được đóng an toàn

Nếu worker đang chờ I/O và `Future.cancel(true)` hoặc shutdownNow gửi interrupt, Java chỉ **yêu cầu** tác vụ ngừng. Hành vi thực tế phụ thuộc nơi đang block và driver/library:

- Một số API kiểm tra interrupted flag và ném `InterruptedException`, một số I/O có timeout riêng.
- Một HTTP request đã đến server có thể vẫn tạo side effect dù client dừng chờ.
- Một DB transaction có thể đã commit trước khi caller nhận exception timeout.
- Trong `finally`, task vẫn có thể cần resource để rollback/flush/ghi audit trước khi hoàn tất.
- Nếu task bỏ qua interruption hoặc vướng native I/O, có thể vượt shutdown deadline.

Không nên làm:

~~~java
catch (InterruptedException ex) {
    // Bỏ qua hoàn toàn interruption và tiếp tục loop vô hạn.
}
~~~

Thay bằng xử lý hợp đồng:

~~~java
catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Dọn tài nguyên có giới hạn, ghi nhận unfinished status nếu có.
    return;
}
~~~

Hai đoạn này là **snippet minh họa**, không phải phương thức Java độc lập. Việc cleanup không được treo vô hạn; chính sách retry/compensation phải được thiết kế trước.

Giữ resource đến lúc work hoàn tất **không có nghĩa chờ vô hạn**. Nếu job bắt buộc chạy cho xong dù process sắp chết, chuyển thành durable job/workflow có khả năng tiếp tục trên instance khác mới là giải pháp phù hợp.

## 18. @DependsOn không phải cơ chế quản lý mọi loại tài nguyên

`@DependsOn` chỉ định thứ tự theo bean name trong cùng Spring context. Những trường hợp cần cơ chế bổ sung:

1. **Resource thuộc context cha/con:** cần xác định ranh giới, bởi container nào chịu trách nhiệm destroy resource và executor; không suy luận một annotation bao phủ mọi context.
2. **Resource có `SmartLifecycle` với stop sớm:** thứ tự destroy đúng vẫn không cứu được kết nối đã bị đóng trong `stop()`.
3. **Resource nằm ở dịch vụ khác:** Spring không thể giữ sống một database server hoặc HTTP service đã shutdown.
4. **Client tự close từ code nghiệp vụ:** container không thể ngăn một reference đóng sớm resource dùng chung.
5. **Task tạo raw threads không qua executor:** Spring không theo dõi thread ngoài bean lifecycle; cần quản lý join/shutdown rõ ràng.
6. **Executor được tạo bằng `Executors.newFixedThreadPool()` mà không thành Spring bean:** nếu không có destroy callback, thread pool có thể sống quá context hoặc không drain đúng.
7. **Nhiều executor phụ thuộc cùng resource:** muốn resource sống cho **tất cả** executor còn dùng nó, cần điều phối cả nhóm, không chỉ một bean.
8. **Shutdown vì JVM crash/OOM/kill -9:** không có Java finally/`@PreDestroy` nào được bảo đảm chạy.

Đối với nhiều executors, dependency graph có thể là:

~~~text
workerExecutorA ---+
                   +-- depends on dataResource
workerExecutorB ---+
workerScheduler ---+

Destroy (với ordered dependency + await phù hợp):
  executor A, B, scheduler được dừng/drain
             |
             v
        dataResource.close()
~~~

Sơ đồ là **ý định thiết kế**: Spring có thể xử lý destruction theo dependencies, nhưng nếu phase stop của resource diễn ra sớm hoặc một executor chưa await thật sự, kết quả vẫn có thể sai. Nên kiểm thử dưới tải với cả ba loại worker.

## 19. DataSource và transaction: cần commit trước khi đóng pool

Một tác vụ `@Async` thao tác DB có thể mang transaction của **chính worker thread**, không tiếp tục transaction của caller:

~~~java
@Async("workerExecutor")
public CompletableFuture<Void> handle(long orderId) {
    billingWriter.writeInOwnTransaction(orderId);
    return CompletableFuture.completedFuture(null);
}
~~~

Đây là **snippet tích hợp**, `billingWriter` là bean transaction-aware có transaction riêng và timeout/rollback policy phù hợp. Việc method return future đã hoàn tất là dấu hiệu tầng worker đã return; với Spring proxy, caller có future bao quanh quá trình async.

**Đừng đóng DataSource khi transaction cuối còn chưa commit/rollback.** Cần bảo đảm worker kết thúc cả bước commit, kể cả việc ghi outbox trong transaction, rồi mới close pool. Nhưng nếu sau DB commit còn bước publish broker, producer cũng phải sống tới lúc publish/ACK; không chỉ bảo vệ DataSource.

Đừng gửi một JPA entity managed/lazy proxy sang worker rồi kỳ vọng EntityManager của caller còn tồn tại. Khi worker mở transaction riêng, truyền immutable IDs/DTO và load state theo hợp đồng.

Ngay cả khi worker được phép hoàn tất, nếu process crash sau commit nhưng trước ACK, queue có thể redeliver. Vì vậy side effect cần idempotency key, outbox hoặc reconciliation nếu không thể mất/trùng.

## 20. Spring Boot: cấu hình và trách nhiệm vận hành

Trong Spring Boot, có thể có executor được auto-configure; tên bean, loại executor và shutdown properties thay đổi theo Boot version và custom beans. Thuộc tính cấu hình thường gặp của task execution là:

~~~properties
spring.task.execution.shutdown.await-termination=true
spring.task.execution.shutdown.await-termination-period=30s
~~~

Đây là **ví dụ cấu hình Spring Boot**, không phải cấu hình thay cho bean `@DependsOn` và không được áp dụng máy móc cho mọi custom executor. Phải xác minh Boot version đang dùng, executor bean thực tế, cách auto-configuration được áp dụng và các bean do nhóm tự khai báo có nhận property hay không.

Quan trọng hơn: **HTTP server graceful shutdown** và **background executor drain** là hai việc khác nhau. Dừng web server nhận request mới không đương nhiên chờ mọi task `@Async` của ứng dụng kết thúc. Kafka/Rabbit listener, schedulers và các ứng dụng message-driven cần chính sách shutdown riêng.

Khi triển khai trên container orchestration, còn có readiness probe, liveness probe, termination grace period và preStop hook. Dừng nhận traffic từ load balancer cần có một khoảng thời gian để request mới không bị chuyển tới pod đang đóng.

**Không nhận định hệ thống an toàn chỉ vì có `server.shutdown=graceful`.** Cần kiểm tra thực tế worker in-flight dùng dependency cho tới lúc nào và bean resource đóng ra sao khi pod được terminate dưới tải.

## 21. Test lifecycle đúng: tạo race có kiểm soát

Test shutdown phải **ép tồn tại một task đang chạy lúc context bắt đầu đóng**, sau đó quan sát dependency lifetime:

1. **Task started:** dùng `CountDownLatch` xác nhận worker đã bắt đầu trên executor thật.
2. **Hold before resource use:** giữ worker trên latch ngay trước lần cuối dùng dependency, không đặt `Thread.sleep` tùy ý.
3. **Context closing:** đóng context trên một thread khác, chờ `ContextClosedEvent` hoặc signal tương đương.
4. **Resource still usable:** trước khi thả latch, xác minh resource chưa bị close.
5. **Worker completion:** thả latch, `Future.get()` báo kết quả và không có lỗi resource closed.
6. **Destroy after drain:** đợi context.close trả về, assert resource bị close **sau** công việc.
7. **Timeout case:** giữ worker lâu hơn `awaitTerminationSeconds`, kiểm tra Spring có thể đi tiếp và resource bị đóng trước worker; code phải có recovery policy.
8. **Multiple workers:** chạy nhiều task, cả task queued và running, verify chính sách `waitForTasksToCompleteOnShutdown`.
9. **SmartLifecycle resource:** nếu resource có `stop()`, xác nhận nó không ngừng phục vụ trước khi worker đã drain.
10. **Late submission:** gọi submit khi shutdown đã bắt đầu, kiểm tra hành vi accept/reject theo executor policy; không mặc định luôn accept.
11. **Context relationships:** có nhiều executor/child context, xác minh thứ tự destroy của mọi resource.
12. **Container kill:** mô phỏng chấm dứt process trước deadline và restart; không đòi `@PreDestroy` phải chạy khi bị kill cứng.

Một unit test chỉ verify `executor.setAwaitTerminationSeconds(30)` được gọi **không chứng minh tài nguyên thực sự sống đủ lâu**. Bài demo mục 8 là một integration test tối thiểu, có event, thread, task và resource bean thật.

Nếu test viết `@Transactional` trong Spring Test và transaction của test tự rollback, nó có thể làm kết luận về commit của task sai lệch. Kiểm tra bằng transaction thực và connection độc lập khi cần chứng minh trạng thái DB.

## 22. Quan sát production: khi nào dừng là thành công?

Các metric/log gợi ý:

| Signal | Ý nghĩa |
| --- | --- |
| `shutdown_requested_at` | Thời điểm tiến trình bắt đầu đóng |
| `admission_closed_at` | Khi API/consumer ngừng tiếp nhận việc mới |
| `active_tasks`, `queued_tasks` | Work còn được executor quản lý |
| `inflight_business_jobs` | Business work đang xử lý, kể cả qua nhiều executors |
| `task_completed_total{result}` | Thành công, lỗi, timeout, cancel |
| `executor_rejected_total` | Submission bị từ chối khi bão hòa hoặc shutdown |
| `drain_duration_seconds` | Thời gian chờ work kết thúc |
| `drain_timeout_total` | Số lần hết shutdown deadline trước khi drain xong |
| `resource_close_at` | Khi tài nguyên cuối cùng được đóng |
| `resource_closed_while_inflight_total` | Bug nghiêm trọng: tài nguyên đóng khi work còn cần |
| `unacked_messages`, `unfinished_jobs` | Work cần phục hồi sau restart |

Đây là **mẫu metric đề xuất**, không phải metric chuẩn có sẵn trên mọi Spring Actuator installation. Metric labels nên giữ cardinality thấp; raw order ID, user ID, request URI có token không nên được dùng làm Prometheus label.

Log nên giữ `taskId`, `executorName`, `resourceName`, `startedAt`, `finishedAt`, `shutdownPhase`, `deadline` và `outcome` trong hệ thống tracing có phân quyền. Có thể đo **thời điểm resource close** sau **last worker finish**; nếu thứ tự đảo ngược, phải tạo cảnh báo thay vì chỉ log một exception trong worker.

Chỉ báo “Spring context đóng xong” không chứng minh mọi side effect ở bên ngoài đã được xác nhận. Nếu service có outbox/message ack, còn cần reconcile trạng thái downstream.

## 23. Những hiểu nhầm phổ biến

| Hiểu nhầm | Thực tế |
| --- | --- |
| “Gọi `shutdown()` là worker đã hoàn tất.” | Có thể chỉ ngừng nhận task, worker vẫn đang chạy. |
| “`setWaitForTasksToCompleteOnShutdown(true)` tự chờ và giữ mọi bean.” | Muốn block destruction cần `awaitTerminationSeconds` và ordering. |
| “Có `@DependsOn` là task không bao giờ bị interrupt.” | `@DependsOn` điều khiển thứ tự bean, không thay policy executor. |
| “`@DependsOn("executor")` trên DataSource là đúng.” | Đó thường là hướng ngược, khiến resource phụ thuộc executor và destroy trước. |
| “Resource chưa destroy thì luôn dùng được.” | Một `SmartLifecycle.stop()` có thể đã khiến resource ngừng phục vụ. |
| “`ContextClosedEvent` nghĩa là các bean đã đóng.” | Đây là tín hiệu bắt đầu close, không phải xác nhận mọi công việc xong. |
| “`@PreDestroy` đóng DataSource trước để shutdown nhanh hơn.” | Có thể làm hỏng work đang dùng DB và mất hiệu ứng nghiệp vụ. |
| “`@Async` tiếp tục transaction của request.” | Imperative transaction gắn với thread/context, worker cần transaction riêng. |
| “Future completed nghĩa là remote effect chắc chắn được ack.” | Phụ thuộc hợp đồng callback/SDK, có thể cần xác nhận và đối soát. |
| “Spring Boot graceful web shutdown chờ toàn bộ background jobs.” | Web request draining và background executor draining là hai phạm vi khác. |
| “awaitTermination dài hơn pod grace period vẫn bảo đảm finish.” | OS/orchestrator có thể kill trước khi Java hoàn tất. |
| “Cancel task làm DB transaction đã commit bị rollback.” | Interruption/cancellation không có tác dụng hoàn tác các side effect đã commit. |
| “Có một executor được drain là mọi worker đều xong.” | Có thể còn scheduler, executor khác, raw thread hoặc child future. |
| “Đóng resource sau executor destroy là đủ trong mọi trường hợp.” | Còn phase stop, deadline và resource ngoài Spring. |
| “Process crash vẫn chạy `@PreDestroy` chắc chắn.” | Crash/kill cứng không bảo đảm callback hoặc finally được thực thi. |

## 24. Checklist thiết kế và code review

- Tôi biết resource nào còn cần cho task **tới tận bước cuối**, bao gồm commit, flush và ACK.
- Đường nhận công việc mới được dừng/quiesce trước khi executor drain.
- Tất cả executors/schedulers liên quan đã được liệt kê; không bỏ quên task con.
- `ThreadPoolTaskExecutor` có shutdown policy và `awaitTerminationSeconds` được lựa chọn theo budget thực.
- Tôi không cho rằng `setWaitForTasksToCompleteOnShutdown(true)` tự block mọi destruction.
- Các resource singleton có dependency order rõ; `@DependsOn` không bị đặt ngược chiều.
- Nếu resource có `SmartLifecycle`, phase stop đã được thử nghiệm để nó không ngừng phục vụ quá sớm.
- Không có bean khác tự `close()` DataSource/client trong khi worker còn dùng chung.
- `@Async` được gọi qua Spring proxy đúng executor, không dựa vào self-invocation.
- Worker có transaction riêng khi cần, connection/HTTP timeouts và xử lý interruption đúng.
- Hệ thống có chính sách khi một task vượt shutdown timeout: unfinished record, retry/backfill và idempotency.
- Termination grace period của container đủ cho toàn bộ stop, drain, flush và cleanup có margin.
- Có integration test dùng latch giữ task, đóng context và assert resource chỉ đóng sau worker cần dùng xong.
- Tôi không tin `@PreDestroy` sẽ chạy trong mọi tình huống kill -9/crash.
- Có metric/log cho shutdown phase, in-flight, drain duration, timeout và resource-close ordering.
- Tôi đã xác minh hành vi theo **đúng Spring/Boot/SDK version** của ứng dụng.

**Kết luận:** “Giữ tài nguyên phụ thuộc còn hoạt động tới khi tác vụ cần dùng kết thúc” là bài toán **đồng bộ vòng đời**, không chỉ tăng timeout. Trong Spring, cần thiết kế thứ tự **stop admission → drain tasks → destroy dependencies**, đồng thời hiểu `ThreadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown`, `setAwaitTerminationSeconds`, `@DependsOn` và `SmartLifecycle`. Ví dụ Java cho thấy một resource singleton có thể được bảo vệ bằng **dependency order + executor wait** trong shutdown bình thường. Đối với crash/timeout/cluster, vẫn cần job state bền vững, idempotency và khả năng phục hồi.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 – ExecutorConfigurationSupport Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html): `setWaitForTasksToCompleteOnShutdown`, `setAwaitTerminationSeconds`, `setAcceptTasksAfterContextClose`, shutdown lifecycle.
- [Spring Framework 6.2 – ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): `setStrictEarlyShutdown`, executor behavior và bean lifecycle.
- [Spring Framework – Using depends-on](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-dependson.html): thứ tự tạo và hủy singleton theo dependency.
- [Spring Framework 6.2 – DependsOn Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/annotation/DependsOn.html): quan hệ phụ thuộc và destruction-time ordering.
- [Spring Framework 6.2 – SmartLifecycle Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/context/SmartLifecycle.html): start/stop phase, thứ tự shutdown và callback hoàn tất.
- [Spring Framework – Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html): init/destroy callbacks, lifecycle/phase.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Async`, `TaskExecutor`, `TaskScheduler` và shutdown implications.
- [Spring Framework 6.2 – ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): scheduled executor lifecycle và task execution threads.
- [Spring Boot 3.5 – Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html): cấu hình executor/scheduler và các điều kiện auto-configuration.
- [Java SE 17 – ExecutorService Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): khác nhau giữa `shutdown()`, `shutdownNow()`, `awaitTermination()`.
- [Java SE 17 – Future.cancel Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): cancellation và giới hạn của interruption.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="lifecycle-shutdown-010">Kiểm chứng khi context đóng trong lúc hàng đợi còn công việc</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng khi Spring ApplicationContext đóng trong lúc hàng đợi còn công việc

## 1. Tình huống: context đã đóng nhưng task trong queue sẽ ra sao?

Giả sử ứng dụng Spring nhận một batch, chuyển các bản ghi thành tác vụ bất đồng bộ và đưa chúng vào `ThreadPoolTaskExecutor`. Worker đầu tiên đang ghi database, các tác vụ còn lại nằm trong queue. Đúng lúc đó, ứng dụng được deploy lại, pod nhận SIGTERM, hoặc code gọi `ApplicationContext.close()`.

Câu hỏi thực tế không chỉ là “Spring có shutdown executor không?” mà là:

- **Tác vụ đang chạy** có bị interrupt hay được cho hoàn tất?
- **Tác vụ chưa bắt đầu trong queue** có tiếp tục được lấy ra chạy hay bị bỏ?
- `Future` đại diện cho một task chưa được chạy có trạng thái **cancelled**, **done**, hay có thể chờ mãi?
- Thread gọi `context.close()` có phải **đợi** pool kết thúc không, và đợi tối đa bao lâu?
- Sau khi context bắt đầu đóng, có thể **submit task mới** được nữa không?
- Nếu task còn chạy sau khi bean phụ thuộc đã bị destroy, liệu nó còn truy cập được `DataSource`/HTTP client không?
- Có thể **kiểm chứng bằng test xác định** thay vì đoán dựa vào `Thread.sleep()` hoặc log không?

Đáp án phụ thuộc **cấu hình executor**, **lifecycle của Spring context**, **trạng thái task**, **khả năng đáp ứng interruption** và **thời gian cho phép shutdown**. Không có quy tắc chung “`close()` luôn chạy hết queue” hoặc “`close()` luôn hủy ngay toàn bộ queue”.

Trong Spring Framework 6.2.1, hai hướng đi cơ bản:

1. **Graceful, ưu tiên hoàn tất backlog**: bật `setWaitForTasksToCompleteOnShutdown(true)` và cấu hình khoảng chờ termination phù hợp. Executor shutdown theo kiểu orderly: không nhận thêm task sau khi shutdown đã thực sự được thực hiện, task đang chạy được để hoàn tất, queue tiếp tục được drain.
2. **Shutdown ngay tại destruction**: khi cấu hình đi theo nhánh `shutdownNow()`, Spring cố interrupt tasks đang chạy và lấy các tasks chưa bắt đầu ra khỏi queue; Spring có thể cancel những Future còn pending. Hủy hoặc interrupt **không đồng nghĩa rollback tất cả side effects**.

Bài này sẽ tạo hai context thật, **đưa task vào queue trước khi đóng**, kiểm soát thời điểm bằng `CountDownLatch`, đóng context và assert kết quả. Không dựa vào giả định mơ hồ về thời điểm các threads được OS lập lịch.

## 2. Các thành phần cần phân biệt

### 2.1. ApplicationContext.close()

Với một Spring context có thể đóng (ví dụ `AnnotationConfigApplicationContext`), `close()` bắt đầu quy trình dừng application context. Spring phát `ContextClosedEvent`, thực hiện điều phối lifecycle (bao gồm các `SmartLifecycle` beans), sau đó destroy các singleton/beans theo hợp đồng của container.

**`ContextClosedEvent` không có nghĩa mọi tài nguyên đã bị destroy ngay tại mốc event.** Nó là một điểm quan sát hữu ích trong quy trình đóng, trước khi quá trình dừng và dọn dẹp hoàn tất.

### 2.2. ThreadPoolTaskExecutor

`ThreadPoolTaskExecutor` dùng `ThreadPoolExecutor` để điều phối tasks. Ta thường cấu hình:

```java
ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
pool.setCorePoolSize(1);
pool.setMaxPoolSize(1);
pool.setQueueCapacity(2);
pool.setThreadNamePrefix("ctx-worker-");
```

Đây là fragment (nếu tự tạo ngoài Spring container phải gọi `initialize()` và `shutdown()` đúng lifecycle). Trong một Spring-managed bean, Spring gọi init/destroy của executor tương ứng.

Với cấu hình này:

- Tối đa **một worker thread** chạy task cùng lúc.
- Queue có thể giữ **hai tasks đang chờ**.
- Nếu task A đã chiếm worker, task B được submit sẽ vào queue.
- `getThreadPoolExecutor().getQueue().size()` có thể dùng trong test để xác nhận task B đã thực sự được queue. Trong production, queue size là một snapshot thay đổi liên tục, không phải cam kết tác vụ sẽ được xử lý.

### 2.3. Ba trạng thái công việc khác nhau

| Trạng thái | Ý nghĩa | Rủi ro khi context đóng |
| --- | --- | --- |
| **RUNNING** | Worker đang thực thi task | Có thể tiếp tục hoàn tất hoặc nhận interrupt; side effects có thể đã diễn ra |
| **QUEUED** | Đã nhận vào executor nhưng chưa có worker xử lý | Có thể được drain về sau hoặc bị loại/cancel khi shutdown |
| **NOT SUBMITTED** | Caller định gửi nhưng chưa đưa vào queue | Có thể bị từ chối vì executor shutdown hoặc queue đầy |

Một log “đã submit task B” **không phải bằng chứng B đã bắt đầu hoặc hoàn tất**. Nếu ứng dụng lưu trạng thái nghiệp vụ `SUBMITTED` trước khi queue thực sự nhận, cần có thiết kế xử lý lỗi submit.

## 3. Vòng đời dừng trong Spring Framework 6.2.1

Lược đồ lý tưởng:

```text
Thread gọi ApplicationContext.close()
   |
   +-- publish ContextClosedEvent
   |       |
   |       +-- executor biết context đang đóng
   |       +-- tùy flags, có thể gửi tín hiệu early shutdown
   |
   +-- phối hợp stop SmartLifecycle beans theo phase (nếu áp dụng)
   |       |
   |       +-- có thể chờ những tasks đang thực thi hoàn tất
   |
   +-- destroy singleton beans / executor
           |
           +-- executor.shutdown() hoặc executor.shutdownNow()
           +-- awaitTermination(...) nếu đã cấu hình
   |
   +-- close() trả về
```

Đây là **mô hình lifecycle**, không phải trace bảo đảm mọi subclass/từng bean có chính xác cùng thứ tự callback. `ThreadPoolTaskExecutor` kế thừa `ExecutorConfigurationSupport`, có hỗ trợ `ApplicationListener<ContextClosedEvent>`, `SmartLifecycle` và `DisposableBean`. Những flags trong phần tiếp theo thay đổi **early shutdown**, **coordinated stop**, **shutdown method** và **await termination**.

Điểm dễ bị bỏ sót là Spring 6.1–6.2 có hành vi coordinated lifecycle tinh vi hơn việc gọi ngay `ExecutorService.shutdownNow()` ở thời điểm đầu của `context.close()`.

### 3.1. ContextClosedEvent khác destruction

Tại thời điểm listener của `ContextClosedEvent` chạy, task trong queue **có thể vẫn chưa bị dừng hay hủy**. Một handler ghi lại queue size ngay lúc event là phép đo trạng thái **trước các bước destruction về sau**, không phải kết quả cuối của shutdown.

Trong ví dụ runnable, ta sử dụng listener để đo `queuedAtClose` chính xác tại thời điểm context phát event, rồi **sau khi `close()` kết thúc** mới đo queued Future và cờ interrupted.

### 3.2. Đóng một context không đồng nghĩa tắt toàn bộ JVM

Sau khi một Spring context đóng, thread/task bên ngoài do người dùng tự tạo có thể vẫn tồn tại nếu lifecycle không được quản lý đúng. Ngược lại, một executor managed có thể đóng trong khi JVM vẫn chạy những context hoặc threads khác.

Đừng dùng `System.exit()` để mô phỏng `context.close()` trong integration test: process bị giết sẽ làm mất cơ hội kiểm tra lifecycle/queue/futures một cách đáng tin.

## 4. Các cờ shutdown quan trọng

Spring cung cấp nhiều cấu hình có tên gần giống nhưng ý nghĩa khác nhau:

| Cấu hình | Mặc định / phạm vi chính | Tác động quan trọng |
| --- | --- | --- |
| `setWaitForTasksToCompleteOnShutdown(boolean)` | `false` | `true`: orderly shutdown, để tasks đang chạy và queue hoàn tất thay vì interrupt/drop khi destruction |
| `setAwaitTerminationSeconds(int)` | Không chờ termination mặc định | Giới hạn khoảng thời gian method shutdown **block để đợi** executor kết thúc |
| `setAcceptTasksAfterContextClose(boolean)` | `false` ở lớp nền `ExecutorConfigurationSupport` | `true`: cho phép late submissions trong giai đoạn context close, bỏ coordinated lifecycle stop phase |
| `setStrictEarlyShutdown(boolean)` trên `ThreadPoolTaskExecutor` | `false` từ Spring 6.1.4 | `true`: gửi tín hiệu shutdown sớm khi context close, từ chối submissions mới ngay từ giai đoạn đó |
| `setCorePoolSize`, `setMaxPoolSize`, `setQueueCapacity` | Theo config workload | Quyết định số task có thể chạy/song song/chờ trước khi shutdown |

**Cực kỳ quan trọng:** mặc định `setWaitForTasksToCompleteOnShutdown(false)` **không tự suy ra** rằng *ngay thời điểm phát `ContextClosedEvent`* mọi task đang chờ bị hủy.

Với `ThreadPoolTaskExecutor` Spring 6.2.1, `strictEarlyShutdown` mặc định là `false`, cho phép late submissions trong giai đoạn context close **theo một coordinated lifecycle stop**. Các task đã chạy có thể có cơ hội hoàn tất trong phase này. Sau đó, ở destruction step, executor mới áp dụng shutdown theo flags cụ thể. Thời gian, thứ tự và việc backlog có hoàn tất hay không phải được kiểm chứng trong hệ thống thực.

### 4.1. waitForTasksToCompleteOnShutdown=true: ưu tiên queue drain

```java
pool.setWaitForTasksToCompleteOnShutdown(true);
```

Theo Javadoc `ExecutorConfigurationSupport`, cờ này chuyển sang **orderly shutdown** ở destruction, không interrupt tasks đang chạy và cho phép **các tasks đã queue** tiếp tục thực thi. Nó cũng **bỏ qua coordinated lifecycle stop phase** ở bước trước, vì quyết định dừng được xử lý riêng tại destruction.

Điểm cần cân nhắc: nếu **không cấu hình awaitTermination** thì Spring có thể tiếp tục destroy những beans khác trong khi các tasks vẫn đang chạy trong nền. Khi job còn cần `DataSource` hoặc HTTP client thuộc context, resource có thể đóng quá sớm.

### 4.2. awaitTerminationSeconds chỉ là khoảng chờ tối đa

```java
pool.setAwaitTerminationSeconds(10);
```

Cờ này yêu cầu Spring chờ executor termination **tối đa 10 giây** tại shutdown; nó **không tự thay đổi** `shutdownNow()` thành `shutdown()` hoặc thay đổi tính chất cancel queue.

- Kết hợp với `waitForTasksToCompleteOnShutdown=true`: thường muốn đợi toàn bộ queue drain, nhưng nếu queue dài hơn budget thì timeout tới hạn và Spring tiếp tục đóng.
- Kết hợp với `waitForTasksToCompleteOnShutdown=false`: Spring có thể đã yêu cầu interrupt/drain queue ngay; khoảng await chỉ đợi các tasks đang kết thúc sau tín hiệu shutdown.

**Đừng hiểu** “setAwaitTerminationSeconds(10)” là “mỗi task được chạy tối đa 10 giây” hoặc “job nào quá 10 giây sẽ được auto-cancel”. Đây là **budget đóng executor**, không phải timeout business của từng task.

### 4.3. acceptTasksAfterContextClose khác strictEarlyShutdown

```java
pool.setAcceptTasksAfterContextClose(true);
```

Khi cờ này là `true`, Spring cho phép một số late task submissions ngay trong các stop/destroy callbacks trước khi executor tự shutdown. Nó **bỏ qua coordinated stop**, rồi áp shutdown tại destruction. Nếu `waitForTasksToCompleteOnShutdown=false`, nhánh destruction thường đi qua `shutdownNow()`, interrupt running tasks và clear queue.

Đó là lý do ví dụ phần 7 dùng cờ này để **cố ý đi vào nhánh shutdownNow tại destruction** mà không bị coordinated lifecycle stop làm thay đổi thí nghiệm. Không coi cấu hình này là lời khuyên production để “hủy sớm” vì tên cờ thực chất cho phép *nộp task muộn hơn*, không phải chặn sớm.

```java
pool.setStrictEarlyShutdown(true);
```

Trên `ThreadPoolTaskExecutor`, cờ này chủ yếu điều khiển **early signal** và việc từ chối task mới khi context đang đóng. Nó không tự ngụ ý “lập tức cắt toàn bộ running/queued tasks” ngay tại sự kiện đóng. Theo Javadoc, các tasks hiện hữu vẫn có thể được phép hoàn tất trong coordinated lifecycle phase.

**Không đặt hai cờ rồi chỉ đọc tên để đoán kết quả**: một số flags ảnh hưởng/làm vô hiệu nhánh của flags khác. Cần kiểm tra thực tế của Spring version và scheduler/executor bean được dùng.

## 5. Orderly shutdown và shutdownNow của JDK là gì?

Spring cuối cùng điều khiển một `ExecutorService` bên dưới. Hai API Java quan trọng:

### 5.1. ExecutorService.shutdown()

- Ngừng chấp nhận **new submissions** kể từ lúc shutdown có hiệu lực.
- Để các tasks đã được submit trước đó tiếp tục chạy.
- Các tasks đang chờ trong queue có cơ hội được worker lấy ra chạy.
- Trả về mà không bảo đảm caller tự đợi đến khi mọi task hoàn tất; muốn đợi phải gọi `awaitTermination(...)` hoặc cơ chế lifecycle tương đương.

Ở Spring, `waitForTasksToCompleteOnShutdown(true)` chọn hành vi orderly tương ứng.

### 5.2. ExecutorService.shutdownNow()

- Cố gắng **interrupt** các tasks đang thực thi.
- Ngừng lấy tasks chưa bắt đầu từ queue và trả về các queued Runnables còn pending.
- Không hứa có thể dừng ngay lập tức một task nếu task **không hợp tác** với interruption.
- Không tự rollback các side effects như gửi message, ghi DB đã commit, gọi API ngoài.

Spring `ExecutorConfigurationSupport.shutdown()` có xử lý queued Runnables còn lại, ví dụ hủy các `Future` chưa bắt đầu. Test ở đây xác nhận task B bị loại khỏi queue và `Future.isCancelled()` thành `true` trong cấu hình immediate destruction.

### 5.3. So sánh ở góc nhìn workflow

| Quan sát | Orderly + await | shutdownNow tại destruction |
| --- | --- | --- |
| Task A đang chạy | Được tiếp tục đến khi tự kết thúc trong điều kiện bình thường | Có thể nhận interrupt; có thể vẫn chạy nếu bỏ qua interrupt |
| Task B đang queue | Thường được chạy sau A nếu budget/tài nguyên còn đủ | Bị lấy khỏi queue, không được thực thi |
| Future của B | Bình thường nếu B chạy xong | Có thể bị cancel; Spring test này xác nhận bị cancel |
| Thread gọi `close()` | Có thể block đến khi jobs hoàn tất hoặc hết await budget | Có thể trả nhanh sau interrupt; vẫn có thể phải đợi tasks phản ứng theo await |
| Đảm bảo không mất việc nếu JVM chết | **Không** | **Không** |
| Nguy cơ | Shutdown chậm, phụ thuộc resources bị destroy | Hủy queue, side effects dang dở, interruption không được xử lý |

**Orderly shutdown không phải durable job queue.** Nếu process chết đột ngột hoặc container bị SIGKILL trước khi queue drain, in-memory tasks vẫn có thể mất dù bạn bật `waitForTasksToCompleteOnShutdown(true)`.

## 6. Ví dụ minh họa trước–sau khi context đóng

Ta cố tình dùng:

- Một worker thread (`core=1, max=1`).
- Queue có capacity 2.
- Task **A** đang chạy và bị giữ ở `CountDownLatch release`.
- Task **B** được submit sau khi A đã bắt đầu, nên chắc chắn phải nằm trong queue.
- Một `ContextClosedEvent` listener lưu `queueSize` chính xác tại điểm context bắt đầu đóng.
- Một thread riêng gọi `context.close()` để main thread có thể kiểm tra close đang chờ và sau đó cho A kết thúc.
- Hai chế độ shutdown được cấu hình khác nhau; ngoài điểm khác này, các điều kiện thử nghiệm giống nhau.

### 6.1. Timeline graceful

```text
t0: pool=1 worker, queue=2
t1: submit A -> worker chạy A -> A await(release)
t2: submit B -> B vào queue (queue size=1)
t3: thread closer gọi context.close()
    -> ContextClosedEvent quan sát queue size=1
    -> orderly shutdown ở destruction
    -> close() vẫn đang chờ termination
t4: main thread giải phóng release
t5: A hoàn tất; B được worker thực thi
t6: pool terminated; close() trả về
```

Đây là thứ tự điều phối có chủ đích trong integration fixture. Nó kiểm chứng rằng khi đã cấu hình **wait for tasks + await termination** và có đủ thời gian, queued task B **thực sự hoàn tất**.

### 6.2. Timeline immediate destruction

```text
t0: submit A; A đang đợi release, B nằm queue
t1: thread closer gọi context.close()
    -> ContextClosedEvent thấy queue size=1
    -> bỏ coordinated lifecycle stop theo config thử nghiệm
    -> destroy executor, gọi shutdownNow()
t2: A nhận interrupt và thoát
t3: B bị lấy khỏi queue; Future của B canceled
t4: close() trả về; B chưa từng chạy
```

**Đừng suy** mọi cấu hình Spring đều đi theo timeline immediate này. Đây là **nhánh thử nghiệm được chọn tường minh** bằng flags, không phải mô tả một default bất biến.

## 7. Dự án runnable Java 21 + Spring Framework 6.2.1

Ví dụ dùng Spring context thật, không cần Spring Boot. Nó kiểm tra **cả hàng đợi lẫn lifecycle** bằng assertions; khác với unit test chỉ gọi `ThreadPoolExecutor.shutdownNow()` bên ngoài container, nó thực sự thực hiện `AnnotationConfigApplicationContext.close()`.

### 7.1. Cấu trúc project

```text
queued-context-close-demo/
├── pom.xml
└── src/main/java/demo/QueuedContextCloseDemo.java
```

### 7.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>queued-context-close-demo</artifactId>
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
          <mainClass>demo.QueuedContextCloseDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 7.3. Tệp src/main/java/demo/QueuedContextCloseDemo.java

```java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class QueuedContextCloseDemo {
    private enum Mode { GRACEFUL, IMMEDIATE }

    private static final class Run {
        final AnnotationConfigApplicationContext ctx;
        final ThreadPoolTaskExecutor pool;
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch queuedRan = new CountDownLatch(1);
        final CountDownLatch closeEvent = new CountDownLatch(1);
        final CountDownLatch closeReturned = new CountDownLatch(1);
        final AtomicBoolean runningInterrupted = new AtomicBoolean(false);
        final AtomicInteger queuedAtClose = new AtomicInteger(-1);
        final AtomicReference<Throwable> closingError = new AtomicReference<>();
        Future<?> activeFuture;
        Future<?> queuedFuture;
        Thread closer;

        Run(Mode mode) throws Exception {
            ctx = new AnnotationConfigApplicationContext();
            ctx.registerBean("workerPool", ThreadPoolTaskExecutor.class, () -> {
                ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
                executor.setCorePoolSize(1);
                executor.setMaxPoolSize(1);
                executor.setQueueCapacity(2);
                executor.setThreadNamePrefix("ctx-worker-");
                executor.setAwaitTerminationSeconds(5);
                if (mode == Mode.GRACEFUL) {
                    executor.setWaitForTasksToCompleteOnShutdown(true);
                } else {
                    // Bypass coordinated stop to show immediate shutdownNow behavior.
                    executor.setAcceptTasksAfterContextClose(true);
                }
                return executor;
            });
            ctx.refresh();
            pool = ctx.getBean("workerPool", ThreadPoolTaskExecutor.class);
            ctx.addApplicationListener((ApplicationListener<ContextClosedEvent>) event -> {
                queuedAtClose.set(pool.getThreadPoolExecutor().getQueue().size());
                closeEvent.countDown();
            });

            activeFuture = pool.submit(() -> {
                started.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Release deadline exceeded");
                    }
                } catch (InterruptedException e) {
                    runningInterrupted.set(true);
                    Thread.currentThread().interrupt();
                }
            });
            if (!started.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("Active task did not start");
            }
            queuedFuture = pool.submit(queuedRan::countDown);
            if (pool.getThreadPoolExecutor().getQueue().size() != 1) {
                throw new AssertionError("Second task was not queued");
            }
        }

        void closeAsync() {
            closer = new Thread(() -> {
                try {
                    ctx.close();
                } catch (Throwable t) {
                    closingError.set(t);
                } finally {
                    closeReturned.countDown();
                }
            }, "context-closer");
            closer.start();
        }

        void awaitClose() throws Exception {
            if (!closeReturned.await(7, TimeUnit.SECONDS)) {
                throw new AssertionError("Context close did not return");
            }
            if (closingError.get() != null) {
                throw new AssertionError("Context close failed", closingError.get());
            }
        }

        void cleanup() throws Exception {
            release.countDown();
            if (ctx.isActive()) {
                ctx.close();
            }
            if (closer != null && closer.isAlive()) {
                closer.join(1000);
            }
        }
    }

    private static boolean graceful() throws Exception {
        Run run = new Run(Mode.GRACEFUL);
        try {
            run.closeAsync();
            if (!run.closeEvent.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("Missing graceful close event");
            }
            boolean queuedAtClose = run.queuedAtClose.get() == 1;
            boolean notFinishedBeforeRelease =
                    !run.closeReturned.await(150, TimeUnit.MILLISECONDS);
            boolean queuedNotExecutedYet = run.queuedRan.getCount() == 1;
            run.release.countDown();
            run.awaitClose();
            run.activeFuture.get(2, TimeUnit.SECONDS);
            run.queuedFuture.get(2, TimeUnit.SECONDS);
            return queuedAtClose && notFinishedBeforeRelease
                    && queuedNotExecutedYet
                    && run.queuedRan.getCount() == 0
                    && !run.runningInterrupted.get();
        } finally {
            run.cleanup();
        }
    }

    private static boolean immediate() throws Exception {
        Run run = new Run(Mode.IMMEDIATE);
        try {
            run.closeAsync();
            if (!run.closeEvent.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("Missing immediate close event");
            }
            run.awaitClose();
            boolean queuedAtClose = run.queuedAtClose.get() == 1;
            boolean queuedNeverRan = run.queuedRan.getCount() == 1;
            boolean runningInterrupted = run.runningInterrupted.get();
            boolean queuedCancelled = run.queuedFuture.isCancelled();
            return queuedAtClose && queuedNeverRan
                    && runningInterrupted && queuedCancelled;
        } finally {
            run.cleanup();
        }
    }

    public static void main(String[] args) throws Exception {
        boolean keepQueue = graceful();
        boolean dropQueue = immediate();
        if (!keepQueue || !dropQueue) {
            throw new AssertionError("Unexpected shutdown semantics: " +
                    keepQueue + "," + dropQueue);
        }
        System.out.println("gracefulQueuedTaskCompleted=" + keepQueue);
        System.out.println("immediateQueuedTaskCancelled=" + dropQueue);
    }
}
```

### 7.4. Lệnh chạy và stdout kiểm chứng

Với JDK 21 và Maven, tại thư mục dự án:

```bash
mvn -q -DskipTests compile exec:java
```

**Output đã biên dịch và chạy thực tế với Spring Framework 6.2.1:**

```text
gracefulQueuedTaskCompleted=true
immediateQueuedTaskCancelled=true
```

Đây là output do assertions kiểm tra, không phải kết quả đoán bằng tay. Chương trình tạo **hai ApplicationContexts độc lập**, mỗi context có một executor và hai tasks. Task A đang chờ latch, B thực sự nằm queue ngay trước khi close. Khi `ContextClosedEvent` phát ra, listener xác nhận **queue size bằng 1** trong cả hai kịch bản.

### 7.5. Vì sao nhánh graceful trả true?

Test chờ `ContextClosedEvent`, xác nhận `close()` chưa trả về trong lúc A còn bị latch giữ (bounded wait 150ms), sau đó mới nhả latch. A kết thúc không bị interrupt; B chạy và `queuedFuture.get(...)` hoàn tất bình thường. Cuối cùng `context.close()` trả về. Điều kiện `awaitTerminationSeconds=5` đủ dài cho hai tasks ngắn của demo.

Kết quả này chứng minh **trong cấu hình cụ thể và tài nguyên đang còn hoạt động**, backlog một task đã được drain. Nó không chứng minh mọi ứng dụng production có queue dài, I/O chậm hay deadline shutdown ngắn sẽ luôn hoàn tất 100% trước SIGKILL.

### 7.6. Vì sao nhánh immediate trả true?

Nhánh IMMEDIATE cố ý gọi `setAcceptTasksAfterContextClose(true)` để bỏ qua coordinated lifecycle stop trong pha đóng context, trong khi `waitForTasksToCompleteOnShutdown` vẫn false. Ở destruction, Spring gọi đường `shutdownNow()`:

- A đang chờ interruptible latch nhận `InterruptedException`; test ghi cờ `runningInterrupted=true`.
- B nằm queue, chưa có worker chạy; Spring lấy task còn lại ra khỏi queue và hủy pending Future.
- `queuedRan` không được đếm xuống, chứng minh B không chạy.
- Khi `close()` trả về, test xác nhận B `isCancelled()`.

Điều này không có nghĩa mọi task bất kỳ sẽ kết thúc sau interrupt. A trong demo **chủ động hợp tác** với interruption bằng lời gọi `CountDownLatch.await`, nên thoát ngay. Một task đang loop CPU và bỏ qua interrupt có thể tiếp tục chạy.

## 8. Vì sao test không dùng Thread.sleep()?

Một test kiểu:

```java
pool.submit(taskA);
pool.submit(taskB);
Thread.sleep(200);
context.close();
```

không chứng minh B đang queue: trong 200ms, máy nhanh có thể đã chạy xong A và B; máy chậm có thể chưa bắt đầu A. Khi đó các kết luận “Spring hủy queue” hoặc “Spring drain queue” đều không có cơ sở.

Demo tránh race bằng:

1. Submit A.
2. Đợi `started.await(timeout)` để biết **A đã chiếm worker**.
3. Giữ A bằng `release.await(...)`.
4. Submit B và assert queue size = 1.
5. Kích hoạt close trong thread riêng.
6. Lắng nghe `ContextClosedEvent` và đo queue tại mốc này.
7. Ở graceful: kiểm tra close chưa hoàn tất rồi release A.
8. Ở immediate: đợi close hoàn tất và kiểm tra interrupt, queue cancellation.
9. Dùng `finally` release latch/close context để không giữ worker treo nếu assertion fail.

Cách này không cần đoán OS scheduling. Có một bounded observation window 150ms trong nhánh graceful để nhận biết close đang block; môi trường CI bị pause rất dài vẫn có thể làm test trễ hoặc timeout, nên ưu tiên event synchronization và mốc kiểm thử có thời gian chờ đủ lớn nếu dùng trong pipeline build thực.

## 9. Điều gì xảy ra với Future của task bị loại khỏi queue?

Với `ThreadPoolTaskExecutor.submit(...)`, ta nhận một `Future<?>`. Kết quả cần phân biệt:

| Trạng thái Future | Ý nghĩa | Cách kiểm tra |
| --- | --- | --- |
| Hoàn tất thành công | Task đã chạy và method trả bình thường | `isDone()==true`, `isCancelled()==false`, `get()` trả kết quả/`null` |
| Hoàn tất bất thường | Task có thể đã chạy và ném exception | `get()` ném `ExecutionException` |
| Cancelled | Task đã bị hủy, không có nghĩa side effects được rollback | `isCancelled()==true`; `get()` ném `CancellationException` |
| Chưa hoàn tất | Đang chạy/đang queue hoặc có trường hợp queue removal chưa hoàn thành | `isDone()==false`; không gọi `get()` vô hạn khi shutdown |

Trong nhánh immediate, Spring hủy Future cho queued task B đã bị loại. Nhưng không nên thiết kế correctness bằng việc **mọi ExecutorService implementation** nhất thiết cancel mọi Future đã queue ở `shutdownNow()`; JDK `shutdownNow()` trả danh sách tasks chưa bắt đầu, còn phần xử lý/cancel wrappers cụ thể là trách nhiệm của framework.

### 9.1. execute() khác submit()

```java
pool.execute(runnable);        // Không trả Future cho từng task.
Future<?> f = pool.submit(runnable); // Có Future để quan sát outcome.
```

Đây là fragment. Dù có Future, trách nhiệm **ghi business status durable** vẫn không được thay thế: khi process bị kill, Future in-memory biến mất theo JVM.

### 9.2. Đừng chờ Future bị cancel bằng get() vô hạn

Nếu đã biết policy shutdown có thể loại queue, code nên xử lý `CancellationException`, `ExecutionException` và timeout; không viết `future.get()` không thời hạn ở luồng đóng. Với deadline termination, mỗi lớp chờ phải nằm trong ngân sách shutdown chung.

## 10. Hàng đợi có bị xóa ngay khi ContextClosedEvent phát ra?

Không thể kết luận chung như vậy. `ContextClosedEvent` xảy ra sớm trong quy trình đóng. Với Spring Framework 6.2.1:

- `ThreadPoolTaskExecutor` có integrated lifecycle; mặc định `strictEarlyShutdown=false` có một nhánh coordinated stop mềm dẻo.
- `waitForTasksToCompleteOnShutdown=true` yêu cầu orderly completion tại destruction.
- `acceptTasksAfterContextClose=true` cho phép late submissions nhưng bỏ coordinated stop, nên destruction với `wait=false` có thể gọi `shutdownNow()` và loại queue.
- `strictEarlyShutdown=true` ưu tiên gửi tín hiệu shutdown sớm để từ chối task mới, nhưng các task đã nhận có thể được để hoàn tất theo coordinated lifecycle.

**Không đo queue size ngay lúc event rồi gọi đó là “queue sau shutdown”.** Trong demo, listener luôn ghi size=1 ở event; sau đó hai chính sách dẫn tới **hai trạng thái hoàn toàn khác** cho B. Đây là minh chứng trực tiếp cho tầm quan trọng của **thời điểm quan sát**.

Nếu muốn kiểm chứng default cụ thể, hãy tạo thêm một testcase không set bất kỳ flags nào, đo cả `ContextClosedEvent`, lifecycle stop completion, tasks đang chạy và queued tasks theo đúng Spring version. Đừng lấy hai trường hợp có flags trong demo rồi suy thành mô tả mặc định duy nhất.

## 11. Submit task mới trong lúc context đang đóng

Một service khác có thể cố gọi executor từ `@PreDestroy`, `SmartLifecycle.stop()` hoặc callback dọn tài nguyên. Kết quả **phụ thuộc**:

- Context đã bắt đầu đóng nhưng executor chưa gửi shutdown signal?
- `strictEarlyShutdown` đang bật?
- `acceptTasksAfterContextClose` hoặc `waitForTasksToCompleteOnShutdown` có làm thay đổi coordinated phase?
- Queue có còn chỗ hay đã đầy?
- Executor đã shutdown thực sự chưa?

Khi không còn nhận task, `ThreadPoolTaskExecutor` thường thể hiện rejection qua Spring `TaskRejectedException` đối với các call thích hợp; executor underlying cũng có `RejectedExecutionHandler`. Không xây logic nghiệp vụ giả định mọi callback `@PreDestroy` đều có thể submit thêm công việc.

### 11.1. Late submission không phải durable handoff

Ngay cả khi cờ cho phép submit trong giai đoạn context close, không có đảm bảo rằng task vừa nhận sẽ kịp chạy trước destruction hoặc container termination. **Không dùng late submit vào pool in-memory như cách chắc chắn giao việc lúc shutdown.**

Đối với việc phải hoàn tất, ghi nó vào durable queue/outbox **trước khi bắt đầu đóng**, hoặc giao cho một hệ thống ngoài có acknowledgement.

### 11.2. Rejection sau shutdown cần được xử lý

Ví dụ fragment:

```java
try {
    pool.execute(this::flushPendingBatch);
} catch (TaskRejectedException ex) {
    logger.warn("Executor closed or saturated; handoff not accepted", ex);
    persistForRetry();
}
```

Đây là fragment, cần bean `pool`, logger và các method/transaction nghiệp vụ có thật. `TaskRejectedException` có thể xảy ra vì **queue đầy** chứ không chỉ vì context đóng; thông điệp phải phân biệt tình trạng qua metrics.

## 12. Interrupt là lời đề nghị hợp tác, không phải “kill thread”

Trong một task đang chạy:

```java
try {
    remoteOperation();
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    // Hoàn tất cleanup rồi thoát hoặc báo trạng thái interrupted.
    return;
}
```

Đây là fragment — `remoteOperation()` chỉ là đại diện cho một thao tác có thể bị interrupt. Khi `shutdownNow()` gọi `interrupt()`:

- Một số blocking APIs như `CountDownLatch.await(...)`, `BlockingQueue.take()` hay `Thread.sleep(...)` sẽ ném `InterruptedException`.
- Nhiều HTTP/JDBC/legacy/native operations không nhất thiết dừng tức thì chỉ vì flag interrupted; tùy driver/client.
- Một CPU loop bỏ qua `Thread.currentThread().isInterrupted()` có thể tiếp tục chạy.
- Nếu catch `InterruptedException` mà vẫn tiếp tục vô hạn, shutdown có thể kéo dài hoặc timeout.
- Reset interrupt flag bằng `Thread.currentThread().interrupt()` giúp caller/framework biết rằng thread đã bị interrupt nếu code không ném lại.

### 12.1. Side effects không tự đảo ngược

Task có thể đã gửi một HTTP request trước khi nhận interrupt. Dù Future bị cancel, remote service có thể đã xử lý request. Cần **idempotency key** và reconciliation, không dùng `isCancelled()` làm bằng chứng “đã rollback toàn bộ”.

Nếu task ghi database trong transaction riêng, hãy hiểu commit/rollback rules của transaction manager; interrupt chỉ là tín hiệu thread, không phải một distributed transaction rollback bảo đảm.

## 13. Đóng context khi tasks vẫn cần bean phụ thuộc

Một hazard quan trọng: task B được phép chạy sau khi Spring bắt đầu destroy bean dependencies:

```text
Worker đang chạy:
  job -> JdbcTemplate -> DataSource / connection pool
                            ^
                            |
Context close: các singleton beans bị destroy
```

Nếu chỉ bật `waitForTasksToCompleteOnShutdown=true` nhưng **không chờ termination**, Spring có thể tiếp tục quá trình destruction trong khi queued/running work vẫn sử dụng những beans đã bị đóng.

Giải pháp thực dụng:

```java
@Bean
public ThreadPoolTaskExecutor businessExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(4);
    pool.setMaxPoolSize(4);
    pool.setQueueCapacity(200);
    pool.setThreadNamePrefix("business-");
    pool.setWaitForTasksToCompleteOnShutdown(true);
    pool.setAwaitTerminationSeconds(30);
    return pool;
}
```

Đây là **fragment Spring Java configuration** cần imports `@Bean` và `ThreadPoolTaskExecutor`. Các con số 4/200/30 chỉ để minh họa; phải chọn theo workload, SLA và termination deadline của hệ thống. Gắn `awaitTerminationSeconds` là cách trì hoãn bước destroy tài nguyên khác **trong budget** cho executor termination. Nếu job quá thời hạn, quá trình đóng vẫn tiếp tục và nguy cơ dang dở vẫn tồn tại.

**Không được hứa** rằng await 30 giây sẽ giúp hoàn tất queue 200 jobs nếu mỗi job mất vài phút. Cần capacity planning và có cơ chế bền vững khi backlog vượt deadline.

## 14. @Async và context close

Nếu một method có `@Async("businessExecutor")` được gọi qua Spring proxy, công việc của nó được submit vào executor tương ứng. Khi context đóng:

- Task `@Async` đang chạy là một running execution trên executor.
- Task `@Async` đã nhận nhưng chờ worker là queued execution.
- Policy của **executor được inject/resolve thực tế** quyết định cơ hội hoàn tất hay bị hủy khi shutdown.
- Một method tự gọi `this.asyncMethod()` trong proxy mode có thể chạy đồng bộ và **không nằm trong executor queue**; không dùng nó để kiểm thử shutdown policy.
- Method trả `CompletableFuture`/`Future` có error/cancel states riêng; hãy kiểm tra completion outcome và business status.

Ví dụ fragment:

```java
@Service
public class ExportService {
    private final ExportWorker worker;

    public ExportService(ExportWorker worker) {
        this.worker = worker;
    }

    public void export(long id) {
        worker.runAsync(id); // Gọi bean khác qua Spring async proxy.
    }
}

@Service
public class ExportWorker {
    @Async("businessExecutor")
    public CompletableFuture<Void> runAsync(long id) {
        performExport(id);
        return CompletableFuture.completedFuture(null);
    }
}
```

Đây là fragment, cần `@EnableAsync`, bean `businessExecutor`, imports và method `performExport()` có thật. Mẫu này chỉ cho thấy **đường submit async**. Trong thực tế, việc không giữ Future hoặc không lưu trạng thái durable có thể khiến lỗi/cancellation không được quan sát.

## 15. ThreadPoolTaskScheduler và @Scheduled không hoàn toàn giống ThreadPoolTaskExecutor

Bài này dùng `ThreadPoolTaskExecutor` vì mục tiêu là **queue các Runnable/Callable tác vụ thường** và đóng context khi có backlog. Với `ThreadPoolTaskScheduler`, các tasks gắn với trigger và `ScheduledFuture` có thêm các câu hỏi:

- Periodic task đang chờ **lần đến hạn kế tiếp** khác task runnable đã ready và đang chờ worker.
- `ScheduledThreadPoolExecutor` có delayed work queue; nhiều entries chưa đến hạn không phải “backlog cần drain ngay”.
- Policy tồn tại hay hủy periodic registrations khi shutdown tùy `executeExistingDelayedTasksAfterShutdownPolicy`, `continueExistingPeriodicTasksAfterShutdownPolicy`, cấu hình scheduler và wrapper Spring.
- Khi dùng `@Scheduled`, cũng cần đo actual scheduler bean/phase và xử lý các trường hợp shutdown/missed firing.

**Không sao chép tất cả kết quả** `Future.isCancelled()` của test `ThreadPoolTaskExecutor` sang một `ScheduledFuture` periodic và kết luận luôn đúng. Hai implementation có loại queue và termination semantics khác nhau.

## 16. Resource budget: tính xem drain queue có kịp không

Một ước lượng thô khi graceful shutdown:

```text
W = số workers có thể chạy song song
Q = số tasks đang queue ở thời điểm shutdown
R = số tasks đang chạy
S = thời gian xử lý trung bình của mỗi task
D = ngân sách termination còn lại

Thời gian drain gần đúng ≈ max(0, ceil((Q + R) / W)) × S
```

Ví dụ W=2, Q=18, R=2, S=1 giây, điều kiện lý tưởng là khoảng 10 giây để hoàn thành khoảng 20 tasks. Nhưng nếu một task chờ HTTP 40 giây, hoặc DB pool chỉ có một connection, thời gian thực có thể lớn hơn nhiều.

Phép ước lượng này **không phải bảo đảm**: tasks có thời gian khác nhau, resource contention, retries, FIFO behavior, scheduling overhead, GC pauses và deadlines. Trong production:

- Đo p95/p99 task duration, không chỉ average.
- Theo dõi queue depth tại mốc shutdown.
- Đặt admission control để queue không tăng vô hạn.
- Chọn timeout đủ dư so với workload thực nhưng không vượt termination budget.
- Đảm bảo phía orchestration (systemd/Kubernetes/app server) cho đủ thời gian graceful stop.
- Khi không đủ budget, spill/commit work sang durable job store hoặc broker trước khi shutdown.

Không dựa vào một TTL của container để “đảm bảo” in-memory tasks được hoàn tất sau khi process bị kill.

## 17. Kubernetes, SIGTERM và ứng dụng nhiều instance

Trong một Kubernetes Deployment phổ biến:

1. Pod cũ bắt đầu ngừng nhận request (readiness/endpoints phụ thuộc cấu hình và propagation delay).
2. Container nhận tín hiệu dừng như SIGTERM theo termination lifecycle.
3. Framework/application bắt đầu graceful shutdown và đóng Spring context.
4. Executor áp policy; các tasks còn queue có thể hoàn tất hoặc bị hủy.
5. Nếu vẫn chưa kết thúc khi hết `terminationGracePeriodSeconds`, container có thể bị kill mạnh.

**Điều kiện thực tế còn phụ thuộc** preStop hooks, ingress/load balancer, process signal handling và Spring Boot shutdown settings. Đừng mặc định mọi ứng dụng Java đều được gọi `context.close()` khi bị kết thúc bất ngờ.

Một chiến lược production nên bao gồm: **ngừng nhận việc mới → stop submitters → drain/flush hoặc persist backlog → đợi giới hạn → ghi nhận việc chưa hoàn tất → cho process dừng**.

### 17.1. Graceful close không thay exactly-once

Ngay cả khi queue drain trong một pod, hai pods khi rolling update có thể xử lý cùng business item nếu không có coordination hoặc idempotency. Spring task executor in-memory không tự cung cấp exactly-once hoặc distributed lock.

Nếu công việc không được phép mất, dùng **durable queue/transactional outbox**, có job IDs, trạng thái, retry/idempotency và khả năng reconcile sau restart. Lựa chọn policy “drain queue” chỉ giảm rủi ro trong **shutdown có kiểm soát**, không thay nơi lưu trữ bền vững.

## 18. Kiểm thử tích hợp mở rộng nên bao phủ những gì?

| Scenario | Điều cần assert | Lý do |
| --- | --- | --- |
| Close khi queue trống | Context đóng và executor terminated | Baseline |
| Close khi A running, B queued | Queue size tại `ContextClosedEvent` và outcome của B sau close | Bài kiểm chứng chính |
| Graceful + đủ await budget | A,B hoàn tất, A không bị interrupt, close chờ | Tránh mất việc trong shutdown bình thường |
| Graceful + quá deadline | Close có thể hết thời gian chờ dù tasks còn chạy | Không tuyên bố đảm bảo completion tuyệt đối |
| Immediate destruction | A nhận interrupt, B không chạy/cancel | Kiểm soát hành vi hủy |
| Task bỏ qua interruption | Close có thể trễ hoặc tasks còn chạy | Cần cooperative cancellation |
| Queue đầy trước close | Submit bị reject, không nhận nhầm là accepted | Backpressure |
| Submit từ `@PreDestroy` | Hành vi phụ thuộc acceptance/lifecycle flags | Tránh late submission race |
| Task dùng `DataSource` | Resource còn sống đủ lâu không? | Tránh đóng DB pool trước jobs |
| Spring Boot graceful shutdown | Kết hợp với web-server draining và SIGTERM | Xác minh môi trường thật |
| Nhiều instances | Không duplicate/không mất việc khi rolling deploy | Idempotency/durability |

**Cần phân biệt test contract với test timing.** Một integration test có bounded waits có thể fail trên CI cực kỳ quá tải dù logic đúng. Điều quan trọng là dùng latches để dựng đúng trạng thái **RUNNING + QUEUED** trước khi đóng, chứ không trông đợi `Thread.sleep(200)` đủ để phân biệt hai trạng thái.

### 18.1. Unit test không thay integration test

Một unit test gọi trực tiếp `ExecutorService.shutdown()` có thể xác minh JDK semantics nhưng **không xác minh Spring context** phát event, phối hợp `SmartLifecycle`, gọi destroy callbacks và await termination.

Bài demo dùng `AnnotationConfigApplicationContext.close()` thật. Nếu ứng dụng production dùng Spring Boot với nhiều executor beans hoặc auto-config, cần integration test thực với bean names, phase và flags được resolve từ cấu hình thực tế.

### 18.2. Chú ý Future và CancellationException

Task B bị hủy khi pending sẽ không thực thi, nhưng caller có thể chưa nhận callback/business status update khi quá trình close diễn ra. Nếu thiết kế service lưu Future để điều phối, phải xử lý `CancellationException` từ `get()` và không đồng nhất cancel với success. Với job cần tiếp tục sau restart, lưu ID/trạng thái ra nơi bền vững trước khi nhận vào in-memory queue.

## 19. Những nhầm lẫn phổ biến

| Nhận định sai | Thực tế |
| --- | --- |
| “context.close() lập tức hủy toàn bộ queued tasks” | Phụ thuộc coordinated lifecycle và shutdown flags |
| “Nếu ContextClosedEvent đã phát, queue chắc chắn trống” | Event xảy ra **trước** các bước stop/destroy; demo thấy queue size=1 |
| “waitForTasksToCompleteOnShutdown=true có nghĩa close() luôn block đến xong” | Không nếu không cấu hình await/phase phù hợp; Spring có thể tiếp tục destroy resources |
| “awaitTerminationSeconds tự động giữ lại queue” | Chỉ đặt tối đa thời gian chờ termination, không tự đổi shutdownNow thành shutdown |
| “acceptTasksAfterContextClose=true làm đóng sớm hơn” | Cờ này cho phép late submissions và bỏ coordinated lifecycle stop; không phải cách chặn nhận việc sớm |
| “strictEarlyShutdown=true lập tức interrupt mọi running task” | Chủ yếu là early signal/từ chối submissions; tasks hiện hữu có thể được phép hoàn tất |
| “shutdownNow() kill mọi thread” | Chỉ cố interrupt; task phải hợp tác để dừng |
| “isCancelled nghĩa là chưa có side effects” | Task có thể đã xử lý một phần hoặc remote side effect thành công |
| “Future hoàn tất là business thành công” | Có thể completed exceptionally/cancelled; cần kiểm tra outcome |
| “queueCapacity đủ lớn thì khi restart không mất việc” | Queue in-memory mất theo JVM, bất kể capacity |
| “Đọc queue size=0 sau close chứng minh tất cả tasks đã chạy” | Có thể queue đã bị clear/cancel, không phải completed |
| “Đọc queue size=1 tại event chứng minh B sẽ bị hủy” | Hai policies của demo cho kết quả đối nghịch |
| “Có @Async thì Spring sẽ giữ context sống đến lúc task hoàn tất” | Còn phụ thuộc executor lifecycle/await và termination deadline |
| “Tăng awaitTerminationSeconds giúp job không bị SIGKILL” | Chỉ hữu ích nếu process bên ngoài cho đủ thời gian |
| “ThreadPoolTaskScheduler delayed queue giống ready queue của TaskExecutor” | Có loại tasks chưa đến hạn, semantics riêng |
| “Thread.sleep trong test là đủ để dựng backlog” | Không deterministic; cần latch và assert queue |
| “Graceful drain bảo đảm exactly-once” | Không có durable state, recovery và idempotency thì vẫn có thể mất/duplicate |

## 20. Bài tập tự kiểm tra

**Bài 1.** Executor có một worker và một task A đã chiếm thread. B nằm queue. Với `waitForTasksToCompleteOnShutdown=true` và await đủ dài, B có thể chạy không?

Có. Khi orderly shutdown thực sự diễn ra, executor không nhận việc mới nhưng vẫn cho tasks đã queue được worker thực thi. Khi A hoàn tất, B có cơ hội chạy. Test Java của bài đã xác nhận B hoàn tất dưới cấu hình và thời gian chờ cụ thể.

**Bài 2.** Dùng `waitForTasksToCompleteOnShutdown=true` nhưng không đặt `awaitTerminationSeconds`. Có thể kết luận mọi beans mà B cần vẫn tồn tại cho tới khi B chạy xong không?

Không. Context có thể tiếp tục destroy resources khác trong lúc work còn chạy. Nếu B cần `DataSource` hoặc bean có lifecycle, phải thiết kế trật tự dừng và chờ termination có budget phù hợp.

**Bài 3.** Trong nhánh immediate, A nhận interrupt còn Future của B bị cancel. Điều đó chứng minh điều gì?

A đang chạy ở đoạn interruptible và đã phản hồi interrupt. B ở queue khi shutdownNow xảy ra, bị loại khỏi queue và Spring hủy Future trong demo. Không chứng minh mọi blocking driver sẽ phản hồi interrupt hoặc mọi side effect được rollback.

**Bài 4.** Vì sao `queuedAtClose=1` chưa đủ để đoán kết quả của B?

Bởi vì đó là trạng thái tại `ContextClosedEvent`, trước khi shutdown policy thực sự hoàn tất. Có thể sau đó orderly drain B hoặc shutdownNow hủy B.

**Bài 5.** Mặc định `strictEarlyShutdown` của `ThreadPoolTaskExecutor` Spring 6.2.1 là gì?

`false` từ Spring 6.1.4, thiên về cho phép late submissions trong phối hợp lifecycle stop; không đồng nghĩa context close luôn giữ queue vô hạn.

**Bài 6.** Khác biệt giữa `setAwaitTerminationSeconds(5)` và `setWaitForTasksToCompleteOnShutdown(true)` là gì?

Một cờ đặt **thời gian chờ tối đa** để executor dừng, cờ còn lại chọn **ngữ nghĩa orderly shutdown** cho tasks đang chạy và queue. Nhiều trường hợp cần cả hai để hoàn tất backlog mà vẫn không treo shutdown vô hạn.

**Bài 7.** Nếu container bị kill mạnh trước khi queue drain, queued tasks có còn lại ở lần restart không?

Không. Queue in-memory của JVM mất theo process. Muốn khôi phục, phải ghi job trạng thái ra durable store/broker/outbox trước đó.

**Bài 8.** Một integration test gọi `context.close()` sau khi `Thread.sleep(50)` rồi kết luận queued task bị hủy. Lỗi phương pháp là gì?

Không chứng minh B thật sự nằm queue tại thời điểm đóng; có thể B đã hoàn tất hoặc chưa được submit. Cần dùng latch giữ worker và assert queue size trước khi close.

## 21. Checklist trước khi phát hành

- Đã xác định executor thực tế là `ThreadPoolTaskExecutor` hay implementation khác, đúng bean được `@Async`/service gọi?
- Có phân biệt running, queued, future/cancelled và rejected tasks?
- Có biết default `strictEarlyShutdown=false` và các cờ ở phiên bản Spring đang dùng?
- Chính sách shutdown muốn **drain queue** hay **cancel pending**? Được ghi rõ theo business requirement?
- Nếu chọn drain, đã bật `waitForTasksToCompleteOnShutdown=true` chưa?
- Nếu cần giữ resources, đã cấu hình `awaitTerminationSeconds` đủ lớn nhưng không vượt container deadline?
- Task đang chạy có xử lý `InterruptedException`/thread interrupt flag có trách nhiệm?
- Với queue dài, đã đo thời gian thực tế để drain và kiểm tra p99 task duration?
- Có xác định mức queue capacity/worker concurrency và nguy cơ saturation khi shutdown?
- Có từ chối việc mới trước close theo admission control, thay vì rely vào một cờ?
- Có xử lý submit rejection một cách rõ ràng, không gắn nhãn accepted khi bị reject?
- Có đo `ContextClosedEvent`, actual task starts/completions/cancellations và termination?
- Có thử `close()` thực trong integration test, không chỉ `ExecutorService.shutdown()` thủ công?
- Có test khi close giữa lúc task dùng `DataSource` và các resource managed khác?
- Có kiểm tra tác động khi rolling deploy nhiều pods hoặc nhiều context?
- Có durable queue/outbox hoặc cách khôi phục missed work nếu process mất đột ngột?
- Có alert khi số queued/cancelled jobs tăng vào cuối vòng đời process?

## 22. Kết luận

Khi **Spring ApplicationContext đóng trong lúc hàng đợi vẫn còn việc**, kết quả phụ thuộc chính sách của executor chứ không chỉ `context.close()`.

- Với **orderly shutdown** (ví dụ `waitForTasksToCompleteOnShutdown=true`) và await termination phù hợp, queued tasks có thể được **drain và hoàn tất** trước khi context tiếp tục đóng.
- Với **shutdownNow tại destruction**, running tasks có thể bị interrupt và queued tasks bị loại/hủy; **cancellation không phải business rollback**.
- `ContextClosedEvent` chỉ là mốc trong quy trình đóng: queue còn task tại event **không dự đoán** được kết quả cuối cùng.
- Các mặc định của `ThreadPoolTaskExecutor` trong Spring 6.2.1 có coordinated lifecycle semantics, nên không suy rộng hai case thử nghiệm thành một kết luận mặc định duy nhất.

Cách kiểm chứng tin cậy là **giữ worker đang chạy bằng latch, xác nhận task thứ hai ở queue, đóng context trên thread khác, rồi assert outcome/Future/interrupt sau close**. Với ứng dụng không được mất việc, cần kết hợp graceful shutdown với **durable handoff, idempotency và recovery** thay vì dựa hoàn toàn vào queue trong RAM.

### Tài liệu tham khảo

- [Spring Framework 6.2.1 — ExecutorConfigurationSupport API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.html)
- [Spring Framework 6.2 — ThreadPoolTaskExecutor API](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Java 21 — ExecutorService shutdown/awaitTermination](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java 21 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Spring Framework — ApplicationContext lifecycle](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)

</details>

- [Quay lại đầu trang](#back-to-top)
