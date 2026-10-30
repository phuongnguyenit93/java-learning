<a id="back-to-top"></a>

# TaskScheduler

## Menu
- [TaskScheduler biểu diễn lập lịch tách khỏi môi trường thực thi](#task-scheduler-001)
- [schedule() để chạy một lần tại thời điểm chỉ định](#task-scheduler-002)
- [scheduleAtFixedRate() và lịch theo chu kỳ](#task-scheduler-003)
- [scheduleWithFixedDelay() và khoảng nghỉ sau khi hoàn tất](#task-scheduler-004)
- [ScheduledFuture để theo dõi và hủy lịch](#task-scheduler-005)
- [Clock của scheduler và ý nghĩa thời gian sử dụng](#task-scheduler-006)
- [Phân biệt luồng kích hoạt lịch với luồng thực thi công việc](#task-scheduler-007)
- [Chọn lập lịch trực tiếp hay annotation theo mức cần điều khiển](#task-scheduler-008)

## <a id="task-scheduler-001">TaskScheduler biểu diễn lập lịch tách khỏi môi trường thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# `TaskScheduler` biểu diễn lập lịch tách khỏi môi trường thực thi

**Module:** Spring Framework - concurrency  
**Nhóm:** TaskScheduler  
**Chủ đề:** TaskScheduler biểu diễn lập lịch tách khỏi môi trường thực thi.

## 1. Hai câu hỏi khác nhau: khi nào chạy và chạy bằng tài nguyên nào?

Một ứng dụng có thể cần đồng bộ dữ liệu mỗi 5 phút, hủy yêu cầu hết hạn sau 30 giây, hoặc tạo báo cáo lúc 09:00 theo giờ Việt Nam. Các trường hợp này luôn có hai quyết định:

1. **Scheduling policy:** việc nên được kích hoạt *khi nào*? Một lần tại `Instant`, sau một `Duration`, theo fixed-rate, fixed-delay, hoặc bởi `Trigger` như cron?
2. **Execution policy:** callback chạy ở đâu? Có bao nhiêu worker, queue thế nào, có phải chờ DB/HTTP không, lúc quá tải xử lý ra sao?

Spring dùng `org.springframework.scheduling.TaskScheduler` để biểu diễn **nhu cầu lập lịch** dưới một giao diện ổn định. Mã nghiệp vụ yêu cầu “chạy Runnable tại thời điểm T” mà không cần biết backend đang bọc `ScheduledExecutorService` của JDK, thread pool riêng hay scheduler do container cung cấp.

```text
Business code
      |
      | TaskScheduler.schedule(task, when/trigger)
      v
TaskScheduler SPI của Spring
      |
      +-- ThreadPoolTaskScheduler: local scheduler threads
      +-- ConcurrentTaskScheduler: adapter của JDK scheduler
      +-- DefaultManagedTaskScheduler: container-managed
      |
      v
Backend quyết định thread, queue, lifecycle
      |
      v
Runnable.run()
```

**Cảnh báo quan trọng:** “tách abstraction” **không có nghĩa** Spring luôn dùng một timer thread rồi tự chuyển business work sang một worker pool khác. `ThreadPoolTaskScheduler` truyền thống **thực thi callback ngay trên scheduler threads**. Nếu callback chặn I/O lâu, chính scheduler threads sẽ bận và những lịch khác có thể bị trễ. Muốn tách timer khỏi business worker thực sự, phải chủ động dispatch sang `TaskExecutor`.

## 2. Phân biệt các interface và implementation

| API | Vai trò | Phương thức tiêu biểu | Điều không mặc định |
| --- | --- | --- | --- |
| JDK `Executor` | Giao `Runnable` đi thực thi | `execute(Runnable)` | Không biểu diễn lịch thời gian |
| Spring `TaskExecutor` | Abstraction thực thi cho các component Spring | `execute(Runnable)` | Không tự tạo cron/fixed-delay |
| JDK `ScheduledExecutorService` | Executor có khả năng lập lịch | `schedule`, `scheduleAtFixedRate`, `scheduleWithFixedDelay` | Không nhận `Trigger` Spring trực tiếp |
| Spring `TaskScheduler` | Abstraction yêu cầu khởi động theo thời điểm/quy tắc | `schedule`, `scheduleAtFixedRate`, `scheduleWithFixedDelay` | Không tự durable hay cluster-wide once |
| `ThreadPoolTaskScheduler` | Implementation Spring với local `ScheduledThreadPoolExecutor` | Bean config pool, clock, error handler | Callback chạy trên scheduler threads |
| `ConcurrentTaskScheduler` | Thích nghi scheduler JDK sẵn có | `TaskScheduler` | Ownership của backend vẫn phải rõ |
| `DefaultManagedTaskScheduler` | Dùng managed scheduler trong Jakarta EE | `TaskScheduler` | Không tự thay quy tắc container |
| Quartz | Job/trigger subsystem có hỗ trợ JobStore | Trigger, JobDetail, JobStore | Persistence chỉ khi cấu hình đúng |

`ThreadPoolTaskExecutor` và `ThreadPoolTaskScheduler` không tương đương: loại trước tối ưu việc giao công việc vào worker pool; loại sau có semantics scheduling dựa trên thời gian. Một implementation có thể hỗ trợ hơn một interface, nhưng logic nghiệp vụ nên phụ thuộc **interface đúng mục đích**.

## 3. Các phương thức quan trọng của `TaskScheduler`

Trong Spring Framework 6.2, giao diện có các phương thức chính:

```java
public interface TaskScheduler {
    Clock getClock();

    ScheduledFuture<?> schedule(
            Runnable task, Instant startTime);
    ScheduledFuture<?> schedule(
            Runnable task, Trigger trigger);

    ScheduledFuture<?> scheduleAtFixedRate(
            Runnable task, Instant startTime, Duration period);
    ScheduledFuture<?> scheduleAtFixedRate(
            Runnable task, Duration period);

    ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable task, Instant startTime, Duration delay);
    ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable task, Duration delay);
}
```

Đây là **phác thảo chữ ký API**, không phải source code để viết lại. Spring còn có một số overload cũ dùng `Date`/`long` đã deprecated; hãy ưu tiên `Instant` và `Duration`.

### Một lần tại thời điểm tuyệt đối

```java
Instant when = scheduler.getClock().instant().plusSeconds(30);

ScheduledFuture<?> handle =
        scheduler.schedule(
                () -> expireRequest(requestId),
                when);
```

Đây là **đoạn minh họa**: cần có `scheduler`, `requestId` và method `expireRequest`. `Instant` là thời điểm tuyệt đối; khi lịch được nhận, callback sẽ chạy vào hoặc sau mốc đó theo khả năng backend. Nếu mốc đã qua, nó được chạy càng sớm càng tốt.

`getClock()` cho phép tính thời gian theo clock của scheduler thay vì trộn các nguồn clock tùy ý; điều này còn hữu ích khi kiểm thử.

### Fixed-rate theo nhịp dự kiến

```java
ScheduledFuture<?> handle =
        scheduler.scheduleAtFixedRate(
                this::measureHealth,
                Duration.ofSeconds(5));
```

`measureHealth` là method thuộc ứng dụng. Khoảng **5 giây giữa các mốc bắt đầu dự kiến** là policy. Nếu một lần chạy kéo dài 7 giây, lần tiếp theo có thể trễ so với lịch dự kiến; không có bảo đảm real-time chính xác đến millisecond.

### Fixed-delay nghỉ sau khi callback hoàn tất

```java
ScheduledFuture<?> handle =
        scheduler.scheduleWithFixedDelay(
                this::pollPartner,
                Duration.ofSeconds(5));
```

`pollPartner` thuộc ứng dụng. Delay bắt đầu **sau khi callback trước đã hoàn thành**. Callback chạy 7 giây cộng delay 5 giây khiến lần kế không bắt đầu trước khoảng 12 giây tính từ đầu lần trước.

### Trigger cho lịch linh hoạt

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
Trigger trigger = new CronTrigger("0 0/5 * * * *", zone);

ScheduledFuture<?> handle =
        scheduler.schedule(this::refreshCatalog, trigger);
```

Đây là đoạn minh họa cần imports `ZoneId`, `Trigger`, `CronTrigger` và method `refreshCatalog`. Spring cron có **sáu trường**: giây, phút, giờ, ngày trong tháng, tháng, thứ. Biểu thức này kích hoạt ở giây 0 của mỗi phút chia hết cho 5 theo zone chỉ định.

Nếu `Trigger` không trả thời điểm tiếp theo, `schedule(Runnable, Trigger)` **có thể trả `null`**; code lưu handle phải kiểm tra.

## 4. Fixed-rate, fixed-delay và cron không thể dùng thay nhau

Cho một callback chạy 7 giây với period/delay 5 giây:

```text
fixed-rate, period=5s:
Mốc dự kiến:  0s ---- 5s ---- 10s ---- 15s ...
Thực thi:     [0s ------ 7s][7s ------- 14s]...
               Lần thứ hai bị trễ so với mốc 5s.

fixed-delay, delay=5s:
Thực thi:     [0s ------ 7s].....[12s ------- 19s]...
                         ^ khoảng nghỉ 5 giây sau completion
```

Với `ScheduledThreadPoolExecutor` của JDK, các lần thực thi định kỳ của **cùng một scheduled task** không chồng nhau, kể cả với fixed-rate. Tuy nhiên nhiều scheduled tasks khác nhau, nhiều replicas, hoặc việc offload sang pool khác có thể chạy chồng nhau.

**Bẫy offload:** nếu callback chỉ làm `worker.execute(this::longJob)` rồi return nhanh, scheduler xem **callback dispatch** đã hoàn thành. Fixed-delay khi đó đo khoảng nghỉ từ lần dispatch trước, không phải từ lúc `longJob` kết thúc. Nếu nghiệp vụ yêu cầu không overlap, cần guard hoặc hệ thống job có khả năng claim/lock.

Cron phục vụ **lịch theo giờ địa phương** hơn là nhịp giữa các lần chạy. 09:00 giờ Việt Nam không phải luôn tương đương “cứ thêm đúng 24 giờ” ở mọi zone trên thế giới; các zone có DST còn có giờ bị lặp hoặc không tồn tại.

## 5. `Trigger` và `TriggerContext`

`Trigger` có API conceptually như sau:

```java
public interface Trigger {
    Instant nextExecution(TriggerContext context);
}
```

Đây là phác thảo; dùng interface thật của Spring. `TriggerContext` có:

```java
Clock getClock();
Instant lastScheduledExecution();
Instant lastActualExecution();
Instant lastCompletion();
```

- `lastScheduledExecution`: mốc **dự kiến** của lần trước.
- `lastActualExecution`: lúc callback **thực sự bắt đầu**.
- `lastCompletion`: lúc callback **kết thúc**.
- `getClock`: clock của scheduler.

Ở lần đầu, các `Instant` lịch sử có thể `null`. Ví dụ Trigger chạy ngay lần đầu rồi sau đó chờ 20 giây tính từ lần hoàn tất:

```java
Trigger afterCompletion = ctx -> {
    Instant last = ctx.lastCompletion();
    return last == null
            ? ctx.getClock().instant()
            : last.plusSeconds(20);
};
```

Đây là đoạn minh họa đặt trong method có import Spring `Trigger` và `Instant`. Khi chỉ cần fixed-delay thông thường, dùng API `scheduleWithFixedDelay` gọn hơn.

Trong Spring 6.2, `CronTrigger` có cách tính lenient và fixed execution. Lenient có thể bỏ mốc cron bị lỡ khi task dài; fixed dựa trên scheduled time, có thể làm các lần tới chạy muộn để không bỏ mốc. **Không** được suy ra rằng một Trigger trong RAM tự biết phải phát lại mọi tick khi JVM đã chết qua giờ cron rồi khởi động lại.

## 6. Implementation quyết định thread và lifecycle

`ThreadPoolTaskScheduler` là implementation phổ biến bọc `ScheduledThreadPoolExecutor`; mặc định số scheduler threads là **1** và có thể thay bằng `setPoolSize(int)`. Theo Javadoc, **task chạy trên chính scheduler threads**, không tự tách worker. `ScheduledFuture` của một one-shot Runnable đại diện việc Runnable hoàn tất.

`ConcurrentTaskScheduler` bọc `ScheduledExecutorService` đã được tạo/cấp ở nơi khác. Nếu backend thuộc thư viện hoặc container, phải xác định rõ ai shutdown; adapter không cho phép một component tùy tiện đóng tài nguyên shared.

`DefaultManagedTaskScheduler` dùng managed scheduler của Jakarta EE khi container áp đặt quy tắc quản lý thread.

`SimpleAsyncTaskScheduler` từ Spring 6.1 hướng tới mô hình thread-per-task/virtual threads của JDK 21. Với fixed-delay, nó thực thi callback trên scheduler thread đơn; do đó không được suy diễn rằng tất cả task đều chạy trên virtual thread. Virtual threads không làm CPU, DB connection hoặc rate limit trở thành vô hạn.

**Thiết kế quan trọng:** nếu callback nặng/blocking kéo dài, tăng scheduler pool size hoặc, tốt hơn khi cần cô lập, chỉ dùng timer để dispatch sang một `TaskExecutor` riêng. Nếu có queue và backpressure, phải quản lý overload ở worker pool thay vì giữ scheduler thread chờ.

## 7. Java 17 chạy độc lập: kiểm chứng thời gian và cancellation

Ví dụ JDK minh họa fixed-rate/fixed-delay bằng các `Instant` cố định, sau đó dùng scheduler thật. Latch giúp kiểm tra hoàn thành mà không đoán thứ tự thread bằng `Thread.sleep`.

Lưu **`SchedulingSemanticsDemo.java`**:

```java
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SchedulingSemanticsDemo {

    public static void main(String[] args) throws Exception {
        Instant firstStart =
                Instant.parse("2026-01-01T00:00:00Z");
        Instant firstCompletion = firstStart.plusSeconds(7);
        Duration interval = Duration.ofSeconds(5);

        System.out.println("plannedRate=" +
                firstStart.plus(interval));
        System.out.println("plannedDelay=" +
                firstCompletion.plus(interval));

        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor();

        try {
            AtomicInteger runs = new AtomicInteger();

            ScheduledFuture<?> once = executor.schedule(
                    runs::incrementAndGet,
                    10, TimeUnit.MILLISECONDS);

            once.get(5, TimeUnit.SECONDS);
            System.out.println("oneShotRuns=" + runs.get());
            System.out.println("oneShotDone=" + once.isDone());

            CountDownLatch ticks = new CountDownLatch(3);

            ScheduledFuture<?> repeated =
                    executor.scheduleWithFixedDelay(
                            ticks::countDown, 0, 10,
                            TimeUnit.MILLISECONDS);

            boolean reached = ticks.await(5, TimeUnit.SECONDS);
            repeated.cancel(false);

            System.out.println("atLeastThreeTicks=" + reached);
            System.out.println("periodicCancelled=" +
                    repeated.isCancelled());
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
```

Chạy bằng JDK 17+:

```bash
javac SchedulingSemanticsDemo.java
java SchedulingSemanticsDemo
```

**Stdout khi chạy hoàn tất bình thường:**

```text
plannedRate=2026-01-01T00:00:05Z
plannedDelay=2026-01-01T00:00:12Z
oneShotRuns=1
oneShotDone=true
atLeastThreeTicks=true
periodicCancelled=true
```

Mốc fixed-rate dự kiến thứ hai là `00:00:05Z`; mốc fixed-delay là `00:00:12Z` do completion 7 giây cộng delay 5 giây. `once.get()` đảm bảo one-shot đã xong trước khi in. Với periodic, chỉ khẳng định **ít nhất** ba tick vì tick tiếp có thể chen vào trước lúc cancel; không in số tick chính xác khi chưa kiểm soát race đó.

`ScheduledFuture.cancel(false)` ngăn các lần kích hoạt tương lai theo backend, **không** có nghĩa có thể hủy mọi thao tác I/O đã chạy.

## 8. Spring Boot hoàn chỉnh: dùng interface `TaskScheduler`

Ví dụ này dùng **Java 17+**, **Spring Boot 3.5.7** và Maven, chạy dạng CLI. Nó khai báo `ThreadPoolTaskScheduler` bean nhưng tra cứu bằng kiểu `TaskScheduler`, chạy one-shot theo `Instant` và fixed-delay theo `Duration`.

### 8.1. Cấu trúc

```text
task-scheduler-demo/
├── pom.xml
└── src/main/java/com/example/scheduler/
    ├── SchedulerConfiguration.java
    └── SchedulerDemoApplication.java
```

### 8.2. `pom.xml`

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
    <artifactId>task-scheduler-demo</artifactId>
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
```

### 8.3. `src/main/java/com/example/scheduler/SchedulerConfiguration.java`

```java
package com.example.scheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfiguration {

    @Bean("jobScheduler")
    public ThreadPoolTaskScheduler jobScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-demo-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
```

Bean được Spring quản lý vòng đời initialize/shutdown. `setPoolSize(1)` chỉ để dễ quan sát rằng callback chạy trên scheduler thread; không phải gợi ý pool size cho production.

### 8.4. `src/main/java/com/example/scheduler/SchedulerDemoApplication.java`

```java
package com.example.scheduler;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.TaskScheduler;

@SpringBootApplication
public class SchedulerDemoApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication app =
                new SpringApplication(SchedulerDemoApplication.class);

        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setDefaultProperties(
                Map.of("logging.level.root", "OFF"));

        try (ConfigurableApplicationContext context = app.run(args)) {
            TaskScheduler scheduler =
                    context.getBean("jobScheduler", TaskScheduler.class);

            AtomicInteger runs = new AtomicInteger();
            AtomicBoolean onSchedulerThread = new AtomicBoolean();

            ScheduledFuture<?> once = scheduler.schedule(
                    () -> {
                        runs.incrementAndGet();
                        onSchedulerThread.set(
                                Thread.currentThread().getName()
                                        .startsWith("scheduled-demo-"));
                    },
                    scheduler.getClock().instant().plusMillis(10));

            once.get(5, TimeUnit.SECONDS);

            System.out.println("oneShotRuns=" + runs.get());
            System.out.println("schedulerThread=" +
                    onSchedulerThread.get());
            System.out.println("oneShotDone=" + once.isDone());

            CountDownLatch ticks = new CountDownLatch(3);
            ScheduledFuture<?> repeated =
                    scheduler.scheduleWithFixedDelay(
                            ticks::countDown,
                            Duration.ofMillis(10));

            boolean reached = ticks.await(5, TimeUnit.SECONDS);
            repeated.cancel(false);

            System.out.println("atLeastThreeTicks=" + reached);
            System.out.println("periodicCancelled=" +
                    repeated.isCancelled());
        }
    }
}
```

Chạy tại thư mục có `pom.xml`:

```bash
mvn -q spring-boot:run
```

**Các dòng `System.out` dự kiến khi chương trình hoàn tất bình thường:**

```text
oneShotRuns=1
schedulerThread=true
oneShotDone=true
atLeastThreeTicks=true
periodicCancelled=true
```

Ứng dụng tắt banner và root logging trong ví dụ; build tool có thể in thêm thông tin khác. Cách dùng `oneShot.get(timeout)` và latch giúp main không in trước khi các điều kiện kiểm thử được quan sát.

**Vì sao ví dụ không có `@EnableScheduling`?** Đây là cách dùng `TaskScheduler.schedule(...)` **programmatic**, không phải method có `@Scheduled`. Bật `@EnableScheduling` chỉ cần để Spring nhận diện và đăng ký các method `@Scheduled` từ bean.

## 9. `@Scheduled`, `@EnableScheduling` và `SchedulingConfigurer`

Dạng khai báo phổ biến:

```java
@Configuration
@EnableScheduling
public class ScheduleConfiguration {

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("cron-");
        return scheduler;
    }
}

@Component
class CachePoller {

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void poll() {
        // Đồng bộ dữ liệu ngắn, có timeout thực.
    }

    @Scheduled(cron = "0 0/5 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void refreshEveryFiveMinutes() {
        // Chạy theo lịch địa phương.
    }
}
```

Đây là **hai class minh họa**, cần imports Spring và `TimeUnit`; không phải tệp duy nhất có thể biên dịch mà không có project. `@EnableScheduling` bật xử lý annotation trong đúng application context. `@EnableAsync` phục vụ `@Async`, là cơ chế khác.

Spring Framework mặc định tìm một `TaskScheduler` bean duy nhất hoặc bean tên `"taskScheduler"`, sau đó xét `ScheduledExecutorService` và có thể fallback scheduler một thread; Spring Boot có auto-configuration riêng tùy phiên bản và classpath, vì vậy cần kiểm tra bean thực tế.

Khi có nhiều scheduler, chỉ định chính xác bằng `SchedulingConfigurer`:

```java
@Configuration
@EnableScheduling
class ExplicitSchedulerConfig implements SchedulingConfigurer {
    private final TaskScheduler chosen;

    ExplicitSchedulerConfig(
            @Qualifier("businessScheduler") TaskScheduler chosen) {
        this.chosen = chosen;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(chosen);
    }
}
```

Đây là class minh họa cần imports tương ứng và bean `"businessScheduler"`. Không nhầm `SchedulingConfigurer` với `AsyncConfigurer`: cái sau chọn executor mặc định cho `@Async`, không phải scheduler của cron.

`@Scheduled` method thông thường không nhận tham số; đối tượng cần thiết được DI. Nếu một request muốn tạo reminder theo `jobId`/deadline động, dùng API programmatic hoặc durable job store thay vì tạo nhiều method `@Scheduled` khác nhau.

## 10. Tách scheduler callback khỏi business job dài

Vì `ThreadPoolTaskScheduler` chạy callback ngay trên scheduler threads, một tác vụ blocking dài sẽ giữ tài nguyên timer. Có thể để scheduler chỉ gửi tín hiệu rồi trả nhanh:

```text
TaskScheduler (canh giờ)
       |
       | tick đến hạn
       v
Runnable dispatch
       |
       | TaskExecutor.execute(...)
       v
TaskExecutor (bounded workers + queue)
       |
       v
Business job (DB / HTTP / CPU)
```

Pattern Spring:

```java
@Service
public class CleanupSchedule {
    private final TaskScheduler scheduler;
    private final TaskExecutor worker;

    public CleanupSchedule(
            @Qualifier("jobScheduler") TaskScheduler scheduler,
            @Qualifier("cleanupExecutor") TaskExecutor worker) {
        this.scheduler = scheduler;
        this.worker = worker;
    }

    public ScheduledFuture<?> start() {
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                worker.execute(this::cleanup);
            } catch (TaskRejectedException ex) {
                recordRejectedTick(ex);
            }
        }, Duration.ofMinutes(1));
    }
}
```

Đây là **class minh họa**, cần imports và các method `cleanup`, `recordRejectedTick`; các bean scheduler/worker phải tồn tại. `start()` nên được gọi tại lifecycle phù hợp và handle được giữ để hủy, không gọi mỗi HTTP request.

**Không được quên:**

- Future của scheduler theo dõi **tick**, không tự biết business job ở worker xong hay chưa.
- Một tick mới có thể enqueue job mới khi job cũ còn chạy. Muốn cấm overlap, thêm guard/concurrency limit.
- `TaskExecutor` có thể từ chối tác vụ; `TaskRejectedException` phải được quan sát và xử lý.
- `CallerRunsPolicy` trên worker pool có thể buộc **scheduler thread** tự chạy job khi full, phá ý định offload.
- `@Transactional`/MDC/ThreadLocal không tự động đi theo mọi worker; cần ranh giới transaction và context propagation đúng.
- Các task ở RAM không tự được lưu để chạy sau restart.

Một single-flight guard đơn giản trong một JVM:

```java
private final AtomicBoolean running = new AtomicBoolean();

void onTick() {
    if (!running.compareAndSet(false, true)) {
        recordSkippedOverlap();
        return;
    }

    try {
        worker.execute(() -> {
            try {
                doBusinessWork();
            } finally {
                running.set(false);
            }
        });
    } catch (TaskRejectedException ex) {
        running.set(false);
        recordRejected(ex);
    }
}
```

Đây là đoạn thành viên minh họa, cần class có `worker`, `AtomicBoolean` và các method nghiệp vụ/metric. `running=true` tính cả job đã enqueue nhưng chưa chạy; cuối job luôn reset bằng `finally`. Guard chỉ bảo vệ **một instance**, không thay thế cluster lock.

## 11. Lỗi, cancellation và clock

Có **hai thời điểm lỗi**:

1. **Lỗi nhận lịch:** scheduler shutdown/quá tải, `schedule(...)` có thể ném `TaskRejectedException` ở caller.
2. **Lỗi khi thực thi:** Runnable đã được lên lịch rồi mới ném exception; cần error handler/metrics trong worker. Với JDK `ScheduledExecutorService`, một periodic task ném exception không xử lý sẽ không chạy các kỳ tiếp theo; Spring `ThreadPoolTaskScheduler` có `setErrorHandler(ErrorHandler)` và chính sách riêng, thường log/suppress lỗi cho repeating tasks.

Không giả định mọi implementation có cách xử lý lỗi giống hệt nhau. Test error handler theo Spring version và kiểu task. Đừng bắt mọi `Exception` rồi im lặng để lịch nhìn có vẻ “đang chạy”.

`ScheduledFuture.get()` của **periodic** không phải “lấy kết quả tick vừa rồi”, có thể chờ mãi; dùng metrics/callback để quan sát từng kỳ. `cancel(false)` dừng các lần scheduled tiếp theo nhưng không tự dừng một JDBC query/HTTP request trong worker khác.

Về clock: `Instant` là một mốc tuyệt đối, `Duration` là khoảng trôi qua, `ZoneId` dùng cho giờ địa phương. Một cron theo 09:00 ở vùng có DST cần xét ngày giờ bị lặp hoặc bị bỏ. Mốc dự kiến không phải cam kết hard real-time; GC pause, CPU throttling và scheduler thread bận đều gây scheduling lag.

## 12. Nhiều replica và tính bền vững

Giả sử có ba Spring instances cùng bật:

```java
@Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
public void reconcile() {
    // Nghiệp vụ đối soát.
}
```

Đây là method minh họa cần nằm trong bean có `@EnableScheduling` được bật. Đến 09:00, mỗi instance có thể tự kích hoạt, tức **có thể có ba lần thực thi**. `TaskScheduler` không tự có distributed lock hoặc leader election.

Khi chỉ muốn một job toàn cụm, cân nhắc database lease/lock, leader election, broker/queue hoặc Quartz có cluster JobStore. Vẫn cần idempotency và xử lý crash window: worker có thể đã làm side effect rồi chết trước khi ghi `SUCCEEDED`.

Một local `ThreadPoolTaskScheduler` không persist lịch. Nếu server tắt vào giờ cron rồi bật lại 10 phút sau, nó không tự biết chính xác những job nào cần được chạy bù. Nếu không được bỏ việc, cần durable job ID, due time, trạng thái, retry và reconciliation; có thể dùng DB/job queue, transactional outbox hoặc hệ thống scheduler chuyên dụng với JobStore.

## 13. Chọn giải pháp theo tình huống

| Nhu cầu | Gợi ý | Điều cần kiểm tra |
| --- | --- | --- |
| Một lần sau 30 giây, chấp nhận mất khi restart | `schedule(task, Instant)` | Cancel/lifecycle |
| Poll nhẹ, nghỉ sau callback | Fixed-delay | Execution time và scheduler lag |
| Thu thập metric theo nhịp | Fixed-rate | Bị trễ dưới tải |
| Chạy 09:00 mỗi ngày | Cron + zone | Timezone, DST, downtime |
| Callback có blocking I/O dài | Dispatch sang TaskExecutor | Queue, overlap, rejection |
| Container quản lý thread | `DefaultManagedTaskScheduler` | Ownership của container |
| Đã có JDK scheduled pool | `ConcurrentTaskScheduler` | Shutdown backend đúng nơi |
| Cần một job trên toàn cụm | Distributed coordination | Lease/fencing, idempotency |
| Job phải survive restart | Durable job store/Quartz thích hợp | Recovery, audit, retry |
| Muốn thay backend không sửa domain | Inject `TaskScheduler` | Tránh API implementation-specific |

## 14. Kiểm thử và quan sát

**Unit test** policy: dùng `Instant.parse(...)`, `Duration` cố định; với custom `Trigger`, mock `TriggerContext` và clock để kiểm tra `nextExecution`. Test trường hợp lần đầu thiếu lịch sử, khi task trễ, khi trigger kết thúc (`null`), timezone và DST.

**Integration test** scheduler: dùng `CountDownLatch`, `ScheduledFuture.get(timeout)` cho one-shot; với periodic, đợi số tick *tối thiểu* rồi cancel. Tránh `Thread.sleep` và assertion đòi đúng số tick tuyệt đối khi cancellation cạnh tranh với callback.

**Failure test:** giữ scheduler thread bằng latch để thấy lịch khác trễ; simulate worker queue full, xem rejection; cho callback ném lỗi và quan sát những kỳ sau; đóng context; test nhiều replica/distributed lock nếu nghiệp vụ yêu cầu.

**Production metrics:** scheduled/actual/completion time, scheduling lag p95/p99, callback duration, error count, rejected ticks, overlap skipped, worker active/queue depth, DB connection wait, JVM CPU/GC và backlog durable. Một scheduler không báo exception chưa có nghĩa nghiệp vụ đang thành công.

## 15. Những lỗi phổ biến

| Sai lầm | Hậu quả | Sửa |
| --- | --- | --- |
| Nghĩ `TaskScheduler` và `TaskExecutor` cùng một API | Không phân biệt thời điểm và worker | Chọn abstraction đúng |
| Nghĩ `ThreadPoolTaskScheduler` tự có worker pool khác | Blocking job giữ scheduler threads | Offload khi cần |
| Pool size 1 với callback HTTP 60 giây | Các lịch khác bị trễ | Timeout, sizing, dispatch ngắn |
| Fixed-delay callback chỉ enqueue rồi return | Business jobs có thể overlap | Guard/concurrency limit |
| Fixed-rate nghĩa là không bao giờ trễ | GC/CPU/worker bận gây lag | Đo độ trễ |
| Cron Spring chỉ có năm trường | Biểu thức sai | Dùng sáu trường gồm giây |
| Không chỉ định timezone | Sai giờ nghiệp vụ khi đổi môi trường | Zone rõ |
| `schedule(Trigger)` luôn trả Future | NPE nếu trigger không có next | Kiểm tra null |
| Gọi periodic `future.get()` trong controller | Chặn vô hạn | Theo dõi metric/latch |
| Cancel scheduled Future rồi nghĩ job offload dừng | Job vẫn chạy ở worker | Hủy/timeout riêng |
| Tạo scheduler mỗi request | Trùng lịch, rò thread | Bean singleton/lifecycle |
| Quên `@EnableScheduling` với `@Scheduled` | Annotation không được đăng ký | Bật đúng context |
| Một lịch trên nhiều replicas, nghĩ chạy một lần | Side effect bị lặp | Cluster coordination |
| Dùng scheduler RAM cho job không được mất | Restart bỏ lỡ | Durable job store |
| Nuốt lỗi trong callback | Không thấy nghiệp vụ thất bại | ErrorHandler/metrics |
| Worker dùng `CallerRunsPolicy` | Scheduler thread chạy job dài | Rejection policy phù hợp |

## 16. Tự kiểm tra

**1.** `TaskScheduler` khác `TaskExecutor` thế nào?  
**Đáp:** TaskScheduler quyết định **khi nào** chạy qua `Instant`, `Duration`, `Trigger`. TaskExecutor cung cấp cơ chế giao task để thực thi.

**2.** `ThreadPoolTaskScheduler` có luôn tách timer và worker thread không?  
**Đáp:** Không. Callback chạy trên scheduler threads.

**3.** Fixed-delay 5 giây, task mất 7 giây. Lần tiếp theo sớm nhất khoảng khi nào?  
**Đáp:** Khoảng 12 giây sau lúc bắt đầu lần đầu, chưa kể trì hoãn hạ tầng.

**4.** Fixed-rate 5 giây, task đầu mất 7 giây. Mốc dự kiến lần hai?  
**Đáp:** Sau 5 giây, nhưng task thực tế sẽ trễ khi worker chưa rảnh.

**5.** Vì sao dùng `scheduler.getClock()`?  
**Đáp:** Để tính due time theo clock scheduler và dễ test.

**6.** `"0 0/5 * * * *"` trong Spring cron có nghĩa gì?  
**Đáp:** Mỗi năm phút tại giây 0, theo timezone được chọn.

**7.** `schedule(Runnable, Trigger)` có thể trả null không?  
**Đáp:** Có, khi trigger không còn lần chạy tiếp.

**8.** Fixed-delay có ngăn hai business jobs overlap nếu callback chỉ enqueue sang pool khác?  
**Đáp:** Không, vì callback đã hoàn thành trước business job.

**9.** Có ba replicas cùng `@Scheduled`, có chắc toàn cụm chỉ chạy một job không?  
**Đáp:** Không. Mỗi replica có lịch riêng; cần coordination/idempotency.

**10.** `cancel(false)` có dừng HTTP call đang chạy không?  
**Đáp:** Không tự động.

**11.** Có cần `@EnableScheduling` để gọi trực tiếp `TaskScheduler.schedule(...)`?  
**Đáp:** Không, chỉ cần cho `@Scheduled` annotation.

**12.** Công việc không được bỏ lỡ khi restart, chỉ dùng local TaskScheduler đủ không?  
**Đáp:** Không. Cần persistence và recovery.

## 17. Ghi nhớ và tài liệu chính thức

`TaskScheduler` **tách cách diễn đạt lịch** khỏi backend cụ thể. Nhưng backend vẫn quyết định thread và giới hạn thực tế. Muốn tách scheduler thread khỏi business worker, phải dispatch sang một executor riêng và xử lý overlap/rejection có chủ đích.

- Một lần: `schedule(task, Instant)`.
- Theo nhịp: `scheduleAtFixedRate(task, Duration)`.
- Nghỉ sau callback: `scheduleWithFixedDelay(task, Duration)`.
- Theo chính sách: `schedule(task, Trigger)` với cron hoặc trigger tùy chỉnh.
- Kiểm soát: `ScheduledFuture`, clock, cancel, error handler.
- Production: latency, timezone, cluster coordination, durability, idempotency.

**Tài liệu chính thức:**

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html).
- [Spring Framework 6.2 — `TaskScheduler`](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/scheduling/TaskScheduler.html).
- [Spring Framework 6.2 — `ThreadPoolTaskScheduler`](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html).
- [Spring Framework 6.2 — `SchedulingConfigurer`](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/scheduling/annotation/SchedulingConfigurer.html).
- [Spring Framework 6.2 — `CronTrigger`](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/scheduling/support/CronTrigger.html).
- [Java SE 17 — `ScheduledExecutorService`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html).
- [Java SE 17 — `ScheduledFuture`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-scheduler-002">schedule() để chạy một lần tại thời điểm chỉ định</a>

<details>
<summary>Xem chi tiết</summary>

# schedule() để chạy một lần tại thời điểm chỉ định

## 1. Mục tiêu: hẹn một công việc chạy một lần với TaskScheduler

Trong Spring Framework, `TaskScheduler` là abstraction dùng để yêu cầu một `Runnable` chạy **ở một thời điểm xác định** hoặc theo các quy tắc lặp lại. Khi ứng dụng cần gửi một lời nhắc, thực hiện bước hậu xử lý, kích hoạt một lần kiểm tra trạng thái hoặc chạy tác vụ sau một thời điểm do người dùng chọn, API trực tiếp nhất là:

```java
ScheduledFuture<?> handle = taskScheduler.schedule(
        runnable,
        startTime   // java.time.Instant
);
```

Với overload **`schedule(Runnable, Instant)`**, tác vụ được **lên lịch chạy một lần**. Không có `period`, không lặp lại theo cron và không tự tạo một chuỗi lần chạy. Đây là khác biệt quan trọng so với `scheduleAtFixedRate(...)`, `scheduleWithFixedDelay(...)` hay `schedule(Runnable, Trigger)`.

**Cần phân biệt hai việc:**

1. **Chấp nhận lịch chạy:** khi gọi `schedule(...)` thành công, scheduler giữ một công việc sẽ đến hạn ở thời điểm yêu cầu.
2. **Thực sự chạy và hoàn tất:** scheduler cần còn sống, còn worker khả dụng và dependency cần thiết vẫn hoạt động khi đến hạn.

Lịch đã được chấp nhận không có nghĩa công việc đã chạy, không bảo đảm chạy đúng chính xác từng mili-giây, và **không tự bảo đảm tồn tại sau khi JVM/process khởi động lại**.

### Mục tiêu học tập

Sau bài này, bạn có thể:

- Gọi đúng `TaskScheduler.schedule(Runnable, Instant)` để tạo **one-shot task**.
- Dùng `Instant`, `ZoneId`, `LocalDateTime` đúng khi người dùng nhập giờ địa phương.
- Hiểu trường hợp thời điểm tương lai, thời điểm quá khứ và khi scheduler bị quá tải.
- Sử dụng `ScheduledFuture<?>` để kiểm tra trạng thái, đợi kết quả, quan sát lỗi hoặc yêu cầu hủy.
- Cấu hình một `ThreadPoolTaskScheduler` bean và gọi nó từ service Spring.
- Viết ví dụ Java 17 chạy được, có kết quả xác định thay vì dùng `Thread.sleep(...)` để đoán task đã chạy.
- Tránh lỗi liên quan đến scheduler thread, đóng application, nhiều instance và lịch cần lưu bền vững.

## 2. Hợp đồng API: TaskScheduler.schedule(Runnable, Instant)

Giao diện của Spring có phương thức:

```java
ScheduledFuture<?> schedule(Runnable task, Instant startTime);
```

Trong đó:

- **`task`:** công việc triển khai `Runnable`, thường là lambda `() -> doSomething()`. Vì `Runnable.run()` trả `void`, future của tác vụ hoàn thành bình thường không chứa giá trị nghiệp vụ.
- **`startTime`:** thời điểm mong muốn được biểu diễn dưới dạng `java.time.Instant`, tức một điểm thời gian tuyệt đối trên trục UTC.
- **Giá trị trả về:** `ScheduledFuture<?>` để quản lý lần chạy đã được lên lịch, ví dụ `isDone()`, `cancel(...)`, `getDelay(...)`, `get(...)`.
- **Nếu `startTime` đã ở quá khứ:** theo hợp đồng API, công việc sẽ được thực thi **ngay khi có thể**, không tự báo lỗi chỉ vì thời điểm đã qua.
- **Nếu scheduler không chấp nhận task:** có thể ném `TaskRejectedException`, chẳng hạn scheduler đang shutdown hoặc bị chính sách giới hạn/từ chối nội bộ.
- **Nếu thời điểm đến hạn nhưng worker bận:** lần chạy có thể bắt đầu **muộn hơn** thời điểm yêu cầu; đây là scheduling best-effort trên executor, không phải một cơ chế hard real-time.

Ví dụ tạo lịch sau 3 giây:

```java
Instant fireAt = Instant.now().plusSeconds(3);
ScheduledFuture<?> handle = taskScheduler.schedule(
        () -> System.out.println("Run once"),
        fireAt
);
```

Đây là **snippet tích hợp**, giả định `taskScheduler` là một bean đã được inject/khởi tạo, không phải class Java độc lập. Lệnh `schedule` trả về ngay sau khi nộp lịch trong điều kiện bình thường; thread caller **không phải chờ ba giây**. Khi scheduler worker có khả năng phục vụ, callback sẽ được gọi vào hoặc sau thời điểm `fireAt`.

### Đừng nhầm “đúng giờ” với “đúng từng nano-giây”

`Instant` có thể có độ chính xác nano-giây ở cấp kiểu dữ liệu, nhưng thực thi còn phụ thuộc độ phân giải clock/timer, CPU scheduler, GC, lock, thread pool, độ dài task khác, contention và tình trạng máy. Vì thế điều kiện đúng cho nhiều ứng dụng là “không trước thời điểm mong muốn, chạy càng gần thời điểm đó càng tốt”, chứ không bảo đảm timestamp thực tế trùng hoàn toàn với `Instant` đầu vào.

Nếu nghiệp vụ yêu cầu hạn chót nghiêm ngặt, phải đặt **deadline**, đo độ trễ và quyết định thế nào là chạy quá muộn; đừng dùng `schedule` như đồng hồ thời gian thực cứng.

## 3. Dòng thời gian của một tác vụ chạy một lần

Xét yêu cầu lúc 10:00:00 đặt lịch chạy lúc 10:00:05:

```text
caller thread                 scheduler                       worker thread
     |                           |                                  |
     |  schedule(task, t=10:00:05)                                  |
     |-------------------------->|                                  |
     |                           | giữ delayed task                 |
     |<------ ScheduledFuture ---|                                  |
     |                           |                                  |
     |        caller có thể tiếp tục xử lý request                 |
     |                           |                                  |
     |                    đến hạn 10:00:05                          |
     |                           |-- dispatch khi có worker ------->|
     |                           |                                  | task.run()
     |                           |                                  | hoàn tất
     |                           |<------------------- future done -|
     |                           |                                  |
     |                         KHÔNG tự lặp lại                     |
```

Các trạng thái chính:

| Giai đoạn | Ý nghĩa | Kiểm tra được bằng gì? |
| --- | --- | --- |
| Mới submit | Scheduler đã chấp nhận lịch | Có `ScheduledFuture<?>`; không nhận rejection |
| Đang chờ đến hạn | Task chưa chạy | `future.isDone()` thường là `false`; `getDelay(...)` còn dương (xấp xỉ) |
| Đã đến hạn, chờ worker | Thời gian đã qua nhưng chưa bắt đầu vì bận/tắc nghẽn | Metric queue, active worker, lateness |
| Đang chạy | `Runnable.run()` đang thực thi | Instrument task start/end; future thường chưa done |
| Hoàn tất bình thường | `run()` trả về | `future.isDone() == true`; `get()` hoàn tất |
| Hoàn tất ngoại lệ | Task ném lỗi và executor ghi nhận exception theo implementation | `get()` có thể báo `ExecutionException` |
| Bị hủy | Yêu cầu cancel được chấp nhận | `isCancelled()`; `get()` ném `CancellationException` |

**Lưu ý:** `ScheduledFuture.getDelay(TimeUnit)` biểu diễn thời gian ước lượng còn tới lịch chạy, **không** cho biết task đã chạy xong. Khi đã quá hạn, delay có thể bằng không hoặc âm trong khi task vẫn chưa hoàn tất.

Để biết task **đã hoàn tất**, dùng `future.get(timeout, unit)` trong môi trường cho phép blocking, hoặc instrument/callback/bảng trạng thái của ứng dụng; không chỉ đọc delay.

## 4. Ví dụ hoàn chỉnh Java 17 + Spring Framework 6.2: một lần chạy và một lần hủy

Ví dụ sử dụng trực tiếp `ThreadPoolTaskScheduler`, một implementation phổ biến của Spring. Ta khởi tạo scheduler bằng code để chương trình **có thể chạy ngoài Spring Boot** và không phải triển khai server. Với Spring-managed bean ở ứng dụng thực, lifecycle được ApplicationContext quản lý; ví dụ CLI này tự gọi `initialize()` và `shutdown()`.

**Điều kiện:** JDK 17+, Maven 3.x, Spring Framework `6.2.14`.

Cấu trúc thư mục:

```text
spring-one-shot/
├── pom.xml
└── src/main/java/demo/OneShotDemo.java
```

### 4.1. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>spring-one-shot</artifactId>
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
                    <mainClass>demo.OneShotDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.2. src/main/java/demo/OneShotDemo.java

```java
package demo;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public final class OneShotDemo {
    public static void main(String[] args) throws Exception {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("one-shot-");
        scheduler.initialize(); // Vì ví dụ chạy ngoài Spring ApplicationContext.

        AtomicInteger runs = new AtomicInteger();
        AtomicBoolean properThread = new AtomicBoolean(false);
        AtomicInteger cancelledRuns = new AtomicInteger();

        try {
            Instant runAt = Instant.now().plusMillis(250);

            ScheduledFuture<?> first = scheduler.schedule(() -> {
                properThread.set(
                        Thread.currentThread().getName()
                                .startsWith("one-shot-"));
                runs.incrementAndGet();
            }, runAt);

            // Demo CLI có thể blocking để xác minh tác vụ đã chạy xong.
            first.get(5, TimeUnit.SECONDS);
            System.out.println("onSchedulerThread=" + properThread.get());
            System.out.println("completedRuns=" + runs.get());
            System.out.println("firstDone=" + first.isDone());

            // Lần chạy khác đặt xa trong tương lai và hủy ngay.
            ScheduledFuture<?> later = scheduler.schedule(
                    () -> cancelledRuns.incrementAndGet(),
                    Instant.now().plus(Duration.ofDays(1)));

            boolean accepted = later.cancel(false);
            System.out.println("cancelAccepted=" + accepted);
            System.out.println("laterCancelled=" + later.isCancelled());
            System.out.println("laterRuns=" + cancelledRuns.get());
        } finally {
            scheduler.shutdown();
        }
    }
}
```

Chạy trong thư mục `spring-one-shot`:

```bash
mvn -q compile exec:java
```

**Standard output dự kiến khi chương trình chạy bình thường:**

```text
onSchedulerThread=true
completedRuns=1
firstDone=true
cancelAccepted=true
laterCancelled=true
laterRuns=0
```

### 4.3. Phân tích hành vi của chương trình

1. `scheduler.initialize()` tạo scheduled executor bên dưới. Nếu bỏ bước này khi tự tạo object ngoài Spring context, scheduler chưa sẵn sàng để nhận lịch.
2. `setThreadNamePrefix("one-shot-")` cho phép kiểm tra task chạy trên thread do scheduler tạo; chương trình in giá trị boolean thay vì in tên thread có số thứ tự không cố định.
3. `Instant.now().plusMillis(250)` đặt thời điểm tương lai ngắn. Thời gian thực có thể lệch vài mili-giây, nhưng `first.get(5, ...)` **đợi đến lúc task hoàn tất**, nên phép kiểm tra số lần chạy không phụ thuộc vào `Thread.sleep`.
4. `runs.incrementAndGet()` chỉ được gọi một lần bởi one-shot task; kết quả bình thường là `completedRuns=1`. `first.isDone()` trở thành true sau khi task hoàn tất.
5. Tác vụ thứ hai được lên lịch sau một ngày và hủy **trước thời điểm đến hạn**. `later.cancel(false)` được chấp nhận, `later.isCancelled()` là true và `cancelledRuns` vẫn bằng 0.
6. `finally` đóng scheduler. Đoạn này rất quan trọng đối với CLI/test: nếu tạo thread pool rồi không đóng, chương trình có thể vẫn còn thread sống. Trong Spring context thật, dùng bean lifecycle thay vì tự khởi tạo một scheduler cho từng request.

**Không nên suy rộng:** đây là tình huống đơn giản, một JVM, không có restart, độ trễ nhỏ và không có DB/API. Nó chứng minh one-shot scheduling và hủy trước khi chạy, **không chứng minh độ chính xác tuyệt đối hoặc khả năng chạy sau khi server tắt**. Không đưa các dòng output timestamp cố định vì thời gian thực luôn khác.

Nếu môi trường cực kỳ quá tải khiến task không hoàn tất trong 5 giây, `get(5, ...)` sẽ ném `TimeoutException`: đây là điều kiện kiểm thử báo lỗi thay vì cho kết quả sáu dòng như dự kiến.

## 5. Dùng TaskScheduler như một Spring bean trong ứng dụng thật

Trong ứng dụng Spring Framework/Spring Boot, nên quản lý scheduler bằng container và inject qua abstraction `TaskScheduler`, thay vì `new ThreadPoolTaskScheduler()` mỗi khi nhận request.

**SchedulingConfiguration.java** — ví dụ class cấu hình tích hợp:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class SchedulingConfiguration {

    @Bean("oneShotScheduler")
    public ThreadPoolTaskScheduler oneShotScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("reminder-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
```

Đây là **class tích hợp**, không phải một file chạy độc lập chỉ bằng `javac`: ứng dụng phải có Spring context và đặt cấu hình trong component scan/import thích hợp. Spring sẽ gọi lifecycle initialization/destruction của `ThreadPoolTaskScheduler` bean; không cần gọi `initialize()` thủ công trên bean được quản lý bình thường.

**ReminderService.java** — nhận yêu cầu hẹn đúng một lần:

```java
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class ReminderService {
    private final TaskScheduler scheduler;
    private final ReminderSender sender;

    public ReminderService(
            @Qualifier("oneShotScheduler") TaskScheduler scheduler,
            ReminderSender sender) {
        this.scheduler = scheduler;
        this.sender = sender;
    }

    public ScheduledFuture<?> scheduleReminder(String reminderId, Instant at) {
        Objects.requireNonNull(reminderId, "reminderId");
        Objects.requireNonNull(at, "at");

        return scheduler.schedule(
                () -> sender.sendReminder(reminderId),
                at);
    }
}
```

Ở đây `ReminderSender` là **service nghiệp vụ do ứng dụng tự định nghĩa**, cần cung cấp bean tương ứng. Snippet minh họa API, không phải chương trình hoàn chỉnh cho tới khi có `ReminderSender`, main/bootstrapping, cấu hình và dependencies.

**Ví dụ caller:**

```java
Instant at = Instant.parse("2026-11-01T02:00:00Z");
ScheduledFuture<?> handle =
        reminderService.scheduleReminder("REM-100", at);
```

Caller nhận handle sau khi scheduler chấp nhận lịch; **không** nhận “reminder đã gửi thành công”. Với endpoint web, không nên gọi ngay `handle.get()` nếu điều đó làm request thread đợi đến tận thời điểm hẹn nhiều giờ sau.

### Có bắt buộc @EnableScheduling không?

**Không bắt buộc chỉ để gọi programmatic `TaskScheduler.schedule(...)` trên một bean đã được khởi tạo.** `@EnableScheduling` chủ yếu kích hoạt cơ chế xử lý các phương thức gắn `@Scheduled` trong Spring context. Hai cách sử dụng có thể cùng tồn tại, nhưng chúng khác nhau về cách đăng ký:

- `TaskScheduler.schedule(Runnable, Instant)`: caller quyết định một thời điểm cụ thể và chủ động tạo lịch.
- `@Scheduled`: phương thức được framework đăng ký theo metadata lịch trên bean, thông thường cho cron/fixed-rate/fixed-delay hoặc cơ chế one-time delay hỗ trợ bởi phiên bản cụ thể.

Với tính năng “người dùng đặt lịch gửi thông báo vào 09:00 ngày X” thay đổi theo từng yêu cầu, gọi `schedule(...)` trực tiếp thường rõ ràng hơn cố gắng gắn annotation cho hàng nghìn thời điểm động.

## 6. Instant, múi giờ và thời điểm do người dùng nhập

`Instant` đại diện **một thời điểm tuyệt đối**, không mang tên múi giờ địa phương. Còn giao diện người dùng thường nhập:

```text
"01/11/2026 09:00"    (giờ địa phương)
```

Chỉ chuỗi này chưa đủ để xác định thời điểm toàn cầu: phải biết **múi giờ của người dùng hoặc nghiệp vụ**. Không lấy timezone mặc định của server Docker rồi coi đó là múi giờ của mọi khách hàng.

Ví dụ chuyển 09:00 tại Việt Nam ngày 1/11/2026 sang `Instant`:

```java
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
LocalDateTime local = LocalDateTime.of(2026, 11, 1, 9, 0);
Instant fireAt = local.atZone(zone).toInstant();

System.out.println(fireAt);
// 2026-11-01T02:00:00Z
```

Đây là **snippet** trong một method có import thích hợp. Múi giờ `Asia/Ho_Chi_Minh` hiện sử dụng UTC+07:00 nên phép chuyển trên cho 02:00 UTC. Chọn `ZoneId` theo thông tin nghiệp vụ; một server có thể phục vụ nhiều zone.

### Daylight Saving Time (DST)

Một số múi giờ có giờ mùa hè và chuyển đổi DST:

- Một `LocalDateTime` có thể **không tồn tại** tại địa phương khi đồng hồ nhảy tới giờ mới.
- Một `LocalDateTime` có thể xuất hiện **hai lần** khi đồng hồ quay lại.
- `local.atZone(zone)` có quy tắc điều chỉnh/chọn offset; không mặc định đó luôn là quyết định nghiệp vụ bạn muốn.
- Nếu cần lịch có tính chính xác cao, xác thực qua `zone.getRules().getValidOffsets(local)`. Danh sách có 0 offset nghĩa là gap, 2 offset là overlap; yêu cầu người dùng xử lý hoặc chọn một chính sách được định nghĩa rõ.

Nếu người dùng chọn thời điểm tuyệt đối qua API, có thể nhận ISO-8601 có offset, chuyển sang `Instant` và lưu cùng zone gốc nếu sau này còn phải hiển thị đúng địa phương. Đừng xây lịch tương lai bằng phép cộng số giờ cố định thay cho quy tắc timezone khi tính “ngày mai lúc 9 giờ” tại zone có DST.

### Thời điểm đã qua có được chạy không?

Theo hợp đồng `schedule(Runnable, Instant)`, thời điểm quá khứ sẽ được chạy **ngay khi có thể**. Ví dụ một request đặt `Instant.now().minusSeconds(10)` không phải tự động “không hợp lệ”.

Nhưng nghiệp vụ có thể muốn **từ chối lịch quá hạn**:

```java
if (!requestedTime.isAfter(scheduler.getClock().instant())) {
    throw new IllegalArgumentException("Schedule time must be future");
}
ScheduledFuture<?> future = scheduler.schedule(job, requestedTime);
```

Snippet này có các biến `requestedTime`, `job` và `scheduler` đã được khởi tạo. Đây là kiểm tra nghiệp vụ, **không phải quy tắc tự động của API**; giữa lúc kiểm tra và lúc submit vẫn có race nếu thời gian vừa trôi qua. Nếu cần xử lý trường hợp đó, quy định rõ “chạy ngay”, “từ chối” hoặc “đánh dấu missed” thay vì âm thầm giả định.

## 7. ScheduledFuture: theo dõi, đợi, hủy

`schedule(...)` trả về một `ScheduledFuture<?>` kết hợp API của `Future` với API lấy độ trễ `Delayed`.

| Phương thức | Dùng để làm gì? | Điều không tự bảo đảm |
| --- | --- | --- |
| `getDelay(unit)` | Ước lượng còn bao lâu đến hạn | Không báo chắc task đã bắt đầu/hoàn tất |
| `isDone()` | Future đã hoàn tất theo trạng thái của nó | Không nhất thiết là thành công (có thể lỗi/hủy) |
| `isCancelled()` | Future ở trạng thái cancelled | Không chứng minh mọi side effect đã được rollback |
| `cancel(false)` | Cố hủy lịch; không yêu cầu interrupt task đang chạy | Không bảo đảm dừng task đã bắt đầu |
| `cancel(true)` | Cố hủy, có thể yêu cầu interrupt task đang chạy theo implementation | Không bảo đảm JDBC/HTTP/remote operation lập tức dừng |
| `get()` | Đợi tới khi task hoàn tất hoặc lỗi/hủy | Có thể chặn caller rất lâu |
| `get(timeout, unit)` | Chờ kết quả có hạn | Timeout đợi **không** tự cancel task |

**Đoạn bắt trạng thái trong test/CLI:**

```java
try {
    future.get(5, TimeUnit.SECONDS);
    System.out.println("success=true");
} catch (java.util.concurrent.CancellationException ex) {
    System.out.println("cancelled=true");
} catch (java.util.concurrent.ExecutionException ex) {
    Throwable original = ex.getCause();
    System.err.println("Task failed: " + original);
} catch (java.util.concurrent.TimeoutException ex) {
    System.err.println("Still waiting or running");
} catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
}
```

Snippet giả định đã có `future` và import `TimeUnit`. Dùng `get()` trong request handler chỉ khi có lý do hợp lệ để blocking; đa số endpoint lập lịch nên **trả xác nhận đã nhận yêu cầu và một ID theo dõi**, không đợi hàng giờ để gửi phản hồi.

### Hủy trước hạn

Nếu task chưa bắt đầu, `future.cancel(false)` thường ngăn task thực thi. Với một `ThreadPoolTaskScheduler` dựa trên `ScheduledThreadPoolExecutor`, việc hủy delayed task còn liên quan chính sách **remove-on-cancel**: một task đã cancel có thể vẫn được giữ trong queue cho đến thời điểm trigger nếu không bật xóa khi hủy.

Tùy cấu hình/version, có thể bật trên underlying executor:

```java
scheduler.getScheduledThreadPoolExecutor()
         .setRemoveOnCancelPolicy(true);
```

Snippet này chỉ gọi được khi `ThreadPoolTaskScheduler` **đã được khởi tạo** và implementation bên dưới là `ScheduledThreadPoolExecutor`. Nó tác động tới cách giữ task đã hủy trong queue, không thay đổi nghĩa của `cancel(...)` với công việc đã thực sự chạy.

### Hủy sau khi task bắt đầu

`cancel(false)` không gửi interrupt; `cancel(true)` có thể khiến worker bị interrupt theo semantics executor nhưng task phải **hợp tác** để dừng. Khi Runnable đang gọi remote service hoặc xử lý giao dịch DB, không coi future cancelled là bằng chứng remote request chưa xảy ra. Với tác vụ có side effect, cần cancellation-aware client, timeout, transaction và idempotency.

Nếu `cancel(...)` trả `false`, task có thể đã hoàn tất, bị hủy trước đó hoặc không thể hủy trong trạng thái hiện tại. Không coi `false` là “đã chạy thành công” khi chưa kiểm tra kết quả.

## 8. Lỗi trong Runnable và lỗi lúc đăng ký lịch

Có **hai nơi khác nhau** để lỗi xuất hiện:

### 8.1. Lỗi ở thời điểm submit

Nếu scheduler đang đóng hoặc từ chối lịch vì lý do nội bộ, `schedule(...)` có thể ném `TaskRejectedException` **trực tiếp ở caller**:

```java
try {
    ScheduledFuture<?> handle = scheduler.schedule(job, when);
    // Đã nhận lịch, CHƯA có nghĩa job đã hoàn tất.
} catch (org.springframework.core.task.TaskRejectedException ex) {
    // Lịch KHÔNG được nhận vào scheduler: trả lỗi phù hợp,
    // hoặc lưu lại theo cơ chế bền vững và retry có giới hạn.
}
```

Snippet này yêu cầu `scheduler`, `job`, `when` có sẵn trong integration code. Rejection lúc submit không đồng nghĩa task đã chạy và ném exception; callback có thể chưa bắt đầu lần nào.

### 8.2. Lỗi khi job thực thi

Nếu `Runnable.run()` đã chạy và ném `RuntimeException`, scheduler có thể ghi lại lỗi vào Future và/hoặc gửi qua `ErrorHandler` tùy implementation và cấu hình. Với one-shot scheduling trên `ThreadPoolTaskScheduler` theo cơ chế xử lý lỗi mặc định, exception của lần chạy có thể được phản ánh qua `ScheduledFuture.get()` dưới dạng `ExecutionException`; nếu ứng dụng cấu hình `ErrorHandler` riêng để nuốt lỗi thì semantics quan sát có thể thay đổi.

Điểm quan trọng: **không mặc định exception sẽ được ném ngược về thread đã gọi `schedule(...)`**. Thread caller thường đã tiếp tục từ lâu.

Một Runnable có xử lý lỗi nghiệp vụ:

```java
Runnable job = () -> {
    try {
        sender.sendReminder("REM-100");
    } catch (RemoteServiceException ex) {
        metric.increment();
        throw ex;
    }
};
```

Đây là snippet minh họa với `sender`, `RemoteServiceException` và `metric` do ứng dụng định nghĩa. Nếu cần retry, hãy quy định rõ retry budget, idempotency và trạng thái job; **không** giả định Spring sẽ tự động chạy lại one-shot task sau exception.

### 8.3. Đừng quên ErrorHandler

`ThreadPoolTaskScheduler.setErrorHandler(...)` cho phép thiết lập cách báo lỗi từ task. Lựa chọn handler cần phù hợp one-shot vs recurring: nếu handler chỉ log rồi suppress, callback có thể bị coi là đã hoàn tất bình thường ở tầng future/scheduler dù nghiệp vụ thất bại. Vì vậy phải có **một nơi sở hữu trạng thái FAILED** của job, không phụ thuộc hoàn toàn vào log của worker.

Với task quan trọng, nên lưu job ID/trạng thái trước khi đăng ký, rồi đánh dấu `RUNNING`/`SUCCEEDED`/`FAILED` từ boundary nghiệp vụ. Đừng dùng chỉ `future.isDone()` để quyết định đã gửi thành công.

## 9. Khác nhau giữa schedule() một lần và lịch lặp lại

| API | Đặc điểm | Tình huống phù hợp |
| --- | --- | --- |
| `schedule(Runnable, Instant)` | **Một lần**, tại/sau Instant | Gửi reminder tại thời điểm chỉ định |
| `schedule(Runnable, Trigger)` | Lịch do Trigger xác định; có thể tiếp tục phát sinh lần chạy | Cron, lịch tùy biến |
| `scheduleAtFixedRate(Runnable, Duration)` | Lặp theo nhịp cố định | Thu thập metric định kỳ |
| `scheduleAtFixedRate(Runnable, Instant, Duration)` | Có thời điểm bắt đầu và chu kỳ cố định | Bắt đầu một periodic job từ mốc X |
| `scheduleWithFixedDelay(Runnable, Duration)` | Đợi một khoảng sau khi lần trước hoàn tất | Polling có khoảng nghỉ |
| `scheduleWithFixedDelay(Runnable, Instant, Duration)` | Có mốc đầu và delay sau lần trước | Polling bắt đầu từ mốc được chọn |

**Tình huống dễ sai:** muốn gửi email đúng một lần vào 17:00 nhưng lại dùng `scheduleAtFixedRate(task, Duration.ofHours(24))`. Cách này tiếp tục gửi email vào những ngày sau. Muốn đúng một lần, dùng `schedule(task, instant)`.

Nếu muốn tạo một lịch **hàng ngày lúc 09:00 theo múi giờ**, đó là bài toán recurrence có DST và timezone, không tương đương “cộng 24 giờ vào Instant” ở những zone có DST. Dùng Trigger/Cron phù hợp hoặc scheduler lưu lịch bền vững theo quy tắc nghiệp vụ.

### Sự khác biệt giữa TaskScheduler và TaskExecutor

`TaskExecutor.execute(task)` thường yêu cầu thực thi càng sớm càng tốt; `TaskScheduler.schedule(task, instant)` yêu cầu **không chạy trước mốc được chỉ định**, rồi chạy khi đủ tài nguyên. Cả hai đều chịu hạn chế của thread, queue, scheduling và shutdown, nhưng một bên có **mốc thời gian tương lai**.

## 10. Năng lực thực thi: scheduler thread, pool size và việc chậm giờ

`ThreadPoolTaskScheduler` bao bọc `ScheduledThreadPoolExecutor` của JDK. Trong Spring Framework 6.2, giá trị mặc định `poolSize` là **1**. Không nên suy ra từ tên “thread pool scheduler” rằng nó có nhiều worker mặc định.

Đáng chú ý, theo Javadoc, task của `ThreadPoolTaskScheduler` **chạy trên chính scheduler thread**, thay vì tự động được chuyển sang một execution pool khác. Vì vậy:

- Một task chạy lâu có thể chiếm scheduler thread và làm job khác đến hạn cũng phải đợi.
- Với pool size 1, các one-shot jobs có thể bắt đầu tuần tự dù chúng có cùng `Instant`.
- Tăng pool size có thể giúp nhiều task đến hạn chạy đồng thời, nhưng không tăng DB connection/quota remote.
- Một scheduler thread đang chờ mạng vẫn chiếm worker của scheduler nếu dùng blocking client.
- Vùng scheduled queue không phải job queue bền vững trên disk.

Ví dụ hai job đến hạn lúc cùng thời điểm trên scheduler pool size 1:

```text
T = 10:00:00
Job A (chạy 8 giây) -----------+-------------------->
                               |
Job B (đến hạn cùng 10:00:00)   | [đợi worker]
                               +----> bắt đầu sau khi A kết thúc
```

Do đó, `Instant` là **desired time**, không phải deadline được bảo đảm. Nếu job quan trọng phải phát lệnh đúng hạn, cần đo **scheduling lag** = actualStart - desiredTime và có chính sách xử lý quá muộn.

### Có nên schedule một Runnable rồi tự submit sang executor khác?

Có thể dùng một Runnable **rất ngắn** tại thời điểm đến hạn để nộp công việc nặng sang executor riêng:

```java
scheduler.schedule(
        () -> workerExecutor.execute(
                () -> reportService.generateReport(reportId)),
        runAt);
```

Đây là snippet tích hợp với `scheduler`, `workerExecutor`, `reportService`, `reportId` và `runAt` đã có. **Cảnh báo quan trọng:** future trả về từ `schedule(...)` trong ví dụ này theo dõi **việc submit sang worker executor**, không theo dõi toàn bộ `generateReport` chạy ở worker khác. Nếu worker executor reject, điều đó cũng cần xử lý rõ ràng. Nếu cần kết quả nghiệp vụ, hãy lưu task ID và status, hoặc xây `CompletableFuture`/completion tracking đúng.

Đừng tạo một thread mới cho mỗi task chỉ để tránh scheduler pool nhỏ; chọn bounded executor và tính công suất downstream, memory và backpressure.

### Theo dõi metric

Các chỉ số hữu ích:

- Số job được lập lịch, đã đến hạn, bắt đầu, hoàn tất, thất bại, bị hủy.
- Queue depth, active scheduler threads, pool size và rejected schedules.
- Scheduling lag từ `Instant` dự kiến đến actual start, phân phối p50/p95/p99.
- Execution duration, số job chạy đồng thời và tổng backlog quá hạn.
- JDBC/HTTP pool saturation nếu callback gọi dependency.
- Số lượng lịch rất xa trong tương lai, số đã cancel nhưng còn ở queue.

Với Spring Boot Actuator/Micrometer, metric cụ thể phụ thuộc cách bind executor/scheduler và phiên bản. Không giả định `getPoolSize()` hay `getActiveCount()` tự xuất hiện thành dashboard nếu ứng dụng chưa cấu hình instrumentation.

## 11. Shutdown, restart và nhiều instance: giới hạn lớn nhất của one-shot trong RAM

Một câu lệnh `scheduler.schedule(job, when)` với `ThreadPoolTaskScheduler` lưu lịch trong **bộ nhớ của JVM hiện tại**. Điều này có hệ quả:

### 11.1. Ứng dụng restart trước thời điểm chạy

Nếu JVM dừng và khởi động lại, handle `ScheduledFuture` của tiến trình cũ không thể được sử dụng lại. Lịch đang chờ trong scheduled executor cũng không tự được phục hồi từ DB. Nếu ứng dụng cần lịch sống qua restart, phải **lưu dữ liệu lịch bền vững** và có cơ chế khôi phục.

Ngay cả khi cấu hình shutdown “chờ task hoàn tất”, một lần restart/deploy vẫn không tạo ra hệ thống durable scheduling cho các lịch sau vài ngày. Không giữ request HTTP hoặc shutdown JVM tới ngày hẹn chỉ để bảo toàn một lịch không có DB.

### 11.2. Nhiều instance cùng đăng ký một job

Nếu ứng dụng chạy 4 pod và mỗi pod đều gọi `schedule(job, when)` cho cùng job ID, có thể có **4 bản sao cùng thực thi** khi tới hạn. `TaskScheduler` cục bộ không tự có distributed lock hoặc exactly-once coordination.

Nếu chỉ một pod nhận request và giữ lịch, pod đó chết thì lịch có thể mất; load balancer chuyển request sau đó sang pod khác cũng không tự chuyển `ScheduledFuture` từ JVM cũ.

### 11.3. Cần idempotency và trạng thái bền vững

Với công việc nghiêm túc như gửi hóa đơn, chuyển trạng thái đơn hàng hoặc phát thông báo có chi phí, kiến trúc nên có:

```text
API nhận yêu cầu lập lịch
       |
       v
DB lưu: job_id, execute_at (Instant), status, payload/ref, version
       |
       v
worker/scheduler tìm các job due
       |
       v
atomic claim / transaction / lease theo job_id
       |
       v
chạy xử lý idempotent
       |
       +--> SUCCEEDED
       +--> FAILED / RETRY_AT
```

Cần cân nhắc **idempotency key** ở downstream, transaction/constraint của DB, lease timeout khi worker crash, retry budget và cách phân biệt “đã gửi nhưng chưa kịp ghi SUCCEEDED”.

**Không hứa exactly-once chỉ bằng khóa:** một worker có thể gọi external API thành công rồi crash trước khi commit trạng thái. Lúc retry có thể gửi lại; cần idempotency hoặc nghiệp vụ chịu được at-least-once.

### 11.4. Khi nào chọn Quartz hoặc job queue?

- **One-shot đơn giản, có thể mất khi restart, không có nhiều replica:** `ThreadPoolTaskScheduler` đủ tốt.
- **Lịch cần lưu và khôi phục:** cân nhắc Quartz với JDBC job store cấu hình đúng, hoặc cơ chế job table + worker với claiming bền vững.
- **Cần retry, dead-letter, workflow nhiều bước:** message broker/job queue/workflow engine thích hợp hơn một `ScheduledFuture` chỉ nằm trong RAM.
- **Chạy multi-node:** đảm bảo coordination trong job store hoặc tầng phân phối, không xem một instance `TaskScheduler` là khóa phân tán.

Không phải Quartz tự động giải quyết mọi vấn đề: phải chọn job store/persistence, cấu hình cluster/locking, misfire handling, concurrency, recovery và nghiệp vụ idempotent đúng.

## 12. Khi nào dùng Clock và cách kiểm thử thời gian?

API `TaskScheduler.getClock()` cho phép làm việc với clock của scheduler. `ThreadPoolTaskScheduler.setClock(Clock)` hỗ trợ chỉ định nguồn clock cho việc tính thời gian. Điều này hữu ích cho testing/time interpretation nhưng **không biến executor thành bộ điều khiển thời gian ảo hoàn toàn**: scheduler bên dưới vẫn có cơ chế delay/timing thực tế.

### Tách tính toán thời điểm khỏi việc thực thi

Để test nhanh và xác định, nên tách logic:

1. Parse/validate thời điểm do người dùng nhập, múi giờ và business rule.
2. Tạo `Instant scheduledAt` từ một `Clock` được inject.
3. Gọi một interface `TaskScheduler` (có thể mock/spy) với đúng `Runnable` và `Instant`.
4. Trong integration test riêng, dùng scheduler thật và `Future.get(timeout)`/latch để xác minh callback được chạy.

Snippet phương thức có Clock:

```java
public Instant nextFireTime(Clock clock, Duration delay) {
    if (delay.isNegative()) {
        throw new IllegalArgumentException("delay must not be negative");
    }
    return clock.instant().plus(delay);
}
```

Đây là method-body snippet cần import `Clock`, `Duration` và `Instant`. Với `Clock.fixed(...)` trong unit test, kết quả tính toán hoàn toàn xác định; không cần ngủ chờ vài phút.

**Đừng dùng cố định clock rồi nghĩ executor thật tự nhảy tới tương lai.** `ThreadPoolTaskScheduler` vẫn tính delay từ thời điểm hiện tại của clock cấu hình tại lúc schedule và phụ thuộc việc worker/timer thực tế thực thi. Với một lịch đã nộp, thay đổi clock hệ thống sau đó không nhất thiết dịch chuyển lại thời điểm thực thi theo wall-clock như bạn kỳ vọng.

### Cách kiểm thử one-shot với scheduler thật

- Dùng mốc tương lai ngắn và `Future.get(timeout)` hoặc `CountDownLatch.await(timeout)`.
- Kiểm tra counter == 1 sau khi Future hoàn tất.
- Kiểm tra cancellation khi task chắc chắn còn rất xa trong tương lai.
- Kiểm tra xử lý `TaskRejectedException` khi scheduler đã shutdown trong test chuyên biệt.
- Kiểm tra callback lỗi qua `ExecutionException` và logging/status ở business boundary.
- Kiểm tra không có lần chạy thứ hai, nhưng không dựa trên `Thread.sleep(5s)` để “chứng minh mãi mãi không lặp”: hợp đồng one-shot + Future hoàn tất cùng counter là bằng chứng phù hợp.
- Với chức năng theo múi giờ, unit-test DST gap/overlap cùng conversion từ `LocalDateTime` sang `Instant`.
- Với persistence, test restart/crash, duplicate claiming và retry/idempotency thay vì chỉ unit test scheduler.

## 13. Các lỗi thường gặp và cách sửa

### Lỗi 1: Dùng LocalDateTime như một thời điểm tuyệt đối

`LocalDateTime` không có timezone. Dùng `ZoneId` rõ ràng rồi chuyển sang `Instant`; không dựa vào timezone mặc định của server. Cẩn thận DST gap/overlap.

### Lỗi 2: Tưởng schedule() tạo lịch lặp lại

`schedule(Runnable, Instant)` là one-shot. Muốn lặp dùng Trigger/fixed-rate/fixed-delay; không tự giả định một lần chạy sẽ được scheduler tạo lại.

### Lỗi 3: Coi Instant là lời hứa đúng tới từng mili-giây

Sự kiện đến hạn khác với sự kiện worker bắt đầu. Một task chậm có thể làm những task khác trễ; đo scheduling lag và đặt capacity/deadline.

### Lỗi 4: Chờ ScheduledFuture.get() trong HTTP request nhiều giờ

Caller có thể bị block đến tận khi job chạy xong, dẫn tới timeout và giữ request thread/connection. Trả job ID/accepted response; theo dõi kết quả qua status/event, không chặn request.

### Lỗi 5: Tự new ThreadPoolTaskScheduler cho từng request

Mỗi instance có thread pool/lifecycle riêng; làm vậy tăng tài nguyên và khó đóng đúng. Dùng singleton Spring bean được quản lý, hoặc scheduler có owner/lifecycle rõ ràng.

### Lỗi 6: Không initialize scheduler trong chương trình tự tạo

Nếu tự tạo `new ThreadPoolTaskScheduler()` ngoài Spring container, cần `initialize()` trước khi `schedule()`; đóng sau khi xong. Với bean chuẩn, lifecycle do Spring quản lý.

### Lỗi 7: Quên xử lý job bị cancel hoặc lỗi

`isDone()` có thể true khi lỗi/cancel, không chỉ khi thành công. Với Future, kiểm tra `isCancelled()` và `get()`; với nghiệp vụ quan trọng có status/metric riêng.

### Lỗi 8: Cho rằng cancel(true) hủy mọi side effect

Interrupt không bắt buộc dừng remote HTTP/DB hoặc rollback giao dịch đã commit. Dùng cancellation-aware API, timeout và idempotency.

### Lỗi 9: Mặc định @EnableScheduling phải có cho programmatic schedule()

Với bean `TaskScheduler` đã được quản lý, gọi trực tiếp `schedule(...)` không đòi `@EnableScheduling`. Annotation này dành cho infrastructure xử lý `@Scheduled`. Không trộn hai cơ chế rồi không biết job được đăng ký từ đâu.

### Lỗi 10: Lưu ScheduledFuture vào static map và gọi đó là persistent scheduling

Map và handle chỉ tồn tại trong JVM. Restart mất tất cả, nhiều pod có map riêng. Nếu lịch phải sống qua deploy/restart, dùng storage bền vững và cơ chế khôi phục.

### Lỗi 11: Nhiều pod đều chạy một công việc

Một lịch cục bộ không có đảm bảo toàn cluster. Dùng atomic claim/distributed coordination đúng và xử lý duplicate với idempotency.

### Lỗi 12: Giao Runnable nặng cho scheduler pool chỉ có một thread

`ThreadPoolTaskScheduler` chạy task trên scheduler thread. Công việc blocking dài làm trễ job đến hạn khác. Tăng pool có kiểm soát hoặc chuyển việc nặng sang worker executor có tracking/rejection rõ.

### Lỗi 13: Tự động retry mọi lỗi bằng schedule() mới mà không kiểm soát

Nếu job lỗi rồi lặp vô hạn bằng cách tự đăng ký sau 100 ms, backlog có thể tăng và gây retry storm. Cần retry budget, exponential backoff/jitter, job ID cố định và idempotency.

### Lỗi 14: Bỏ qua thời điểm quá khứ

Theo API, quá khứ chạy ngay khi có thể; nhưng sản phẩm có thể muốn reject hoặc đánh dấu missed. Phải xác định hành vi rõ cho người dùng, nhất là với reminder/hợp đồng có hạn giờ.

## 14. Checklist đưa vào production

Trước khi triển khai một tính năng one-shot scheduling:

1. **Xác định điểm thời gian:** input gồm timezone/offset hay không? Có DST? Lưu `Instant` hay cả timezone gốc?
2. **Xác định độ chính xác chấp nhận được:** trễ 1 giây, 1 phút hay không được trễ? Có chính sách missed/deadline?
3. **Chọn lifecycle:** lịch chỉ cần khi JVM đang chạy hay phải tồn tại sau restart?
4. **Xác định số instance:** một JVM hay nhiều pod? Job có thể chạy trùng?
5. **Chọn API đúng:** `schedule(Runnable, Instant)` cho một lần, không dùng periodic API nếu không muốn lặp.
6. **Giới hạn công suất:** scheduler pool size, công việc blocking, DB/HTTP connection, quota downstream và backlog.
7. **Quản lý handle:** có job ID, cơ chế cancel, trạng thái và quyền người dùng để hủy không?
8. **Phân biệt lỗi:** schedule reject tại caller, callback failure ở worker, future cancellation và timeout khi caller đợi.
9. **Bảo đảm cleanup:** lifecycle của scheduler, shutdown, cancellation; không để thread pool không được đóng.
10. **Quan sát:** start lag, run duration, success/failure/cancel, missed deadline và queue depth.
11. **Kiểm thử:** runnable example, thời điểm tương lai/quá khứ, nhiều jobs cùng hạn, restart, nhiều instance, duplicate và idempotency.
12. **Đảm bảo nghiệp vụ:** nếu cần “chạy đúng một lần” theo nghĩa tác động kinh doanh, thiết kế job store và idempotency; one-shot API chỉ giới hạn lịch **trong một scheduler instance**.

### Bảng lựa chọn nhanh

| Nhu cầu | Cơ chế thường phù hợp | Giới hạn cần nhớ |
| --- | --- | --- |
| Một lần sau vài giây trong JVM đang chạy | `TaskScheduler.schedule(Runnable, Instant)` | Không bền vững qua restart |
| Một lần vào giờ cụ thể của người dùng | Chuyển `LocalDateTime + ZoneId → Instant` rồi `schedule` | Phải xử lý timezone/DST |
| Lặp mỗi khoảng thời gian | `scheduleAtFixedRate` / `scheduleWithFixedDelay` | Không phải one-shot |
| Theo cron/timezone | `Trigger`/Cron hoặc scheduler chuyên dụng | DST, misfire, job lifecycle |
| Rất nhiều lịch kéo dài nhiều ngày, cần recovery | Persistent job store/Quartz JDBC | Cần clustering, retry và idempotency |
| Hẹn giờ rồi giao việc nặng | One-shot schedule để submit sang bounded executor | Future của schedule có thể chỉ theo dõi submission |
| Hủy một lịch chưa chạy | Giữ `ScheduledFuture` rồi `cancel(false)` | Handle cục bộ JVM, không bền vững |
| Mọi pod không được xử lý trùng | Durable claiming/coordination | Không tự có với TaskScheduler cục bộ |

## 15. Tổng kết và tài liệu chính thức

**Tám ý quan trọng:**

1. `TaskScheduler.schedule(Runnable, Instant)` đăng ký một công việc chạy **một lần** tại hoặc sau thời điểm chỉ định.
2. `Instant` là điểm thời gian tuyệt đối; convert từ ngày/giờ địa phương bằng `ZoneId` trước khi schedule.
3. Nếu thời điểm đã qua, API lên lịch chạy **ngay khi có thể**, không tự bỏ task.
4. `ScheduledFuture<?>` là handle theo dõi completion/cancel; `get()` có thể chặn và không nên gọi trong HTTP request chỉ để đợi một lịch dài hạn.
5. `ThreadPoolTaskScheduler` chạy việc trên chính scheduler threads, mặc định pool size là 1; task dài có thể làm lịch khác chậm.
6. Lỗi từ `schedule(...)` khi bị từ chối khác với lỗi xảy ra bên trong `Runnable.run()`.
7. Cancellation/shutdown **không** đồng nghĩa rollback tác động đã xảy ra ở database/remote system.
8. Để lịch chạy qua restart hoặc chỉ một instance thực thi trong cluster, cần persistence, claim/idempotency và cơ chế điều phối ngoài một scheduler cục bộ.

**Tài liệu chính thức:**

- [Spring Framework 6.2 – TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/scheduling/TaskScheduler.html) — `schedule(Runnable, Instant)`, trường hợp thời điểm quá khứ và `TaskRejectedException`.
- [Spring Framework 6.2 – ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html) — pool size, lịch chạy, lifecycle, error handler và chính sách hủy.
- [Spring Framework 6.2 – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html) — cách dùng abstraction và các loại scheduler.
- [Java SE 17 – ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html) — `getDelay`, `cancel`, `get` và completion state.
- [Java SE 17 – ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html) — scheduled queue, worker pool và remove-on-cancel.
- [Java SE 17 – Instant](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Instant.html) — thời điểm tuyệt đối.
- [Java SE 17 – ZoneId](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZoneId.html) — chuyển đổi múi giờ, daylight saving rules.

**Kết luận:** `schedule(task, instant)` là lựa chọn rõ ràng để **chạy một lần tại thời điểm mong muốn** trong một Spring scheduler đang sống. Hãy dùng `ScheduledFuture` để quản lý lịch cục bộ, giám sát độ trễ thực tế và chủ động thiết kế persistence/cluster coordination nếu đây là một yêu cầu nghiệp vụ quan trọng không được mất khi deploy hoặc restart.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-scheduler-003">scheduleAtFixedRate() và lịch theo chu kỳ</a>

<details>
<summary>Xem chi tiết</summary>

# scheduleAtFixedRate() và lịch theo chu kỳ trong Spring TaskScheduler

Một hệ thống Spring thường cần thực hiện công việc lặp lại: quét đơn hàng quá hạn mỗi 10 giây, làm mới cache mỗi phút, đọc hàng đợi định kỳ hoặc ghi chỉ số sức khỏe của một dịch vụ. Với API `org.springframework.scheduling.TaskScheduler`, phương thức **`scheduleAtFixedRate()`** cho phép lên lịch một `Runnable` lặp lại theo **chu kỳ cố định (fixed rate)**.

Điểm cốt lõi: **fixed rate gắn các mốc thực thi dự kiến với thời điểm bắt đầu và độ dài chu kỳ, không phải đợi công việc trước hoàn tất rồi mới đếm chu kỳ tiếp theo**. Tuy nhiên, “lên lịch mỗi 5 giây” **không có nghĩa chắc chắn thực thi đúng mỗi 5 giây**, không có bảo đảm thời gian thực (real-time), không tự đảm bảo job thành công, không tự tạo giao dịch hoặc bảo đảm chỉ chạy một lần trên toàn cụm máy.

Bài học này nằm trong nhóm **TaskScheduler** của module **Spring Framework – concurrency**. Mục tiêu là sử dụng đúng API, hiểu mô hình thực thi, xử lý tác vụ chạy lâu, lựa chọn so với fixed delay/cron, quản lý `ScheduledFuture` và áp dụng an toàn trong Spring Boot.

## 1. TaskScheduler là gì và khác TaskExecutor thế nào?

`TaskExecutor` tập trung vào **giao việc để thực thi**; `TaskScheduler` tập trung vào **quyết định lúc nào một việc được thực thi**, gồm chạy một lần tại thời điểm hẹn hoặc chạy lặp theo quy luật.

```text
TaskExecutor
  caller --execute(task)--> worker/queue --> run một task

TaskScheduler
  caller --scheduleAtFixedRate(task, period)-->
       bộ lập lịch giữ lịch trong bộ nhớ
       |   |   |   |   |
       v   v   v   v   v
     run run run run run ... cho đến khi hủy/đóng
```

Trong Spring, `ThreadPoolTaskScheduler` là một implementation phổ biến của `TaskScheduler`. Nó sử dụng `ScheduledExecutorService` của JDK phía dưới và thêm quản lý bean/lifecycle và khả năng tích hợp với trigger của Spring. Thread thực thi `Runnable` định kỳ **thường chính là scheduler worker**, không phải tự động được chuyển qua một `ThreadPoolTaskExecutor` khác.

Do đó:

- Nếu task rất ngắn, pool scheduler nhỏ có thể đủ.
- Nếu task chặn I/O lâu hoặc xử lý CPU nặng, scheduler worker sẽ bị chiếm; các lịch khác có thể bị trễ.
- Nếu task chỉ submit tiếp sang executor khác, thời gian task scheduler “hoàn tất” là **thời gian giao việc**, không nhất thiết là lúc nghiệp vụ ở executor kia hoàn thành.
- `TaskScheduler` không phải một job queue bền vững hoặc distributed coordinator; mỗi ứng dụng/replica có thể có lịch của riêng nó.

### Các phương thức chính trong Spring Framework 6.x

```java
ScheduledFuture<?> schedule(Runnable task, Instant startTime);
ScheduledFuture<?> schedule(Runnable task, Trigger trigger);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Duration period);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, Duration period);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Duration delay);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, Duration delay);
```

Đây là **trích dạng chữ ký API để học**, không phải chương trình Java độc lập. Trong Spring 6.x, ưu tiên `Instant` và `Duration`; các overload kiểu `Date`/`long` tồn tại nhằm tương thích và nhiều overload đã bị deprecated từ Spring 6.0.

Nguồn: Javadoc chính thức của `TaskScheduler`: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/TaskScheduler.html

## 2. “Fixed rate” chính xác có nghĩa gì?

Với chu kỳ `P` và mốc bắt đầu `T0`, những thời điểm **đủ điều kiện để chạy theo kế hoạch** có dạng:

```text
T0, T0 + P, T0 + 2P, T0 + 3P, ...
```

Ví dụ bắt đầu gần 10:00:00, `period = 5 giây`:

```text
Thời gian      00     05     10     15     20     25  (giây)
Mốc dự kiến    |------|------|------|------|------|
Thực thi ngắn  [job]  [job]  [job]  [job]  [job]
```

Nếu mỗi lần chạy mất khoảng 1 giây và scheduler có đủ tài nguyên, các lượt thường được bắt đầu gần mốc 0, 5, 10, 15 giây. **“Gần”** rất quan trọng: thread scheduler phải chịu GC pauses, CPU scheduling, contention, quota container và tác vụ khác.

**Fixed rate là cách xác định mốc lịch, không phải cam kết chính xác đến millisecond.**

### 2.1. Khi task chạy lâu hơn period

Giả sử chu kỳ là 5 giây nhưng một lượt mất 8 giây:

```text
Mốc dự kiến:  0s      5s      10s     15s     20s
             |-------|-------|-------|-------|
Thực tế:     [Run 1 ---------]
                              [Run 2 ----------]
                                               [Run 3 ...]
```

Mốc tiếp theo đã đến trong khi lượt trước chưa xong. Với `ThreadPoolTaskScheduler` dựa trên `ScheduledThreadPoolExecutor`, **các lần chạy kế tiếp của cùng một tác vụ định kỳ không chồng lấp nhau**: lượt sau **bắt đầu muộn** và có thể được lên chạy sớm ngay sau lượt trước hoàn tất để bắt kịp tiến độ. Điều này **không có nghĩa** scheduler tạo ra một bản sao task chạy song song chỉ vì pool có nhiều thread.

Có vài giới hạn cần ghi nhớ:

- Scheduler không tạo “thời gian bị mất” thành một danh sách giao dịch durable; shutdown/crash có thể làm bỏ lỡ các lượt.
- Không bảo đảm tại một thời điểm tải nặng hệ thống sẽ hoàn thành đúng số lần bằng công thức thời gian lý tưởng.
- Nếu nhiều **tác vụ định kỳ khác nhau** dùng cùng pool, chúng có thể song song khi `poolSize > 1`.
- Nếu một lượt **chỉ submit việc sang pool khác rồi return**, các công việc thực tế được giao **có thể chồng lấp**, dù chính invocation của Runnable định kỳ không chồng lấp.
- Nếu có nhiều bản sao ứng dụng, mỗi bản sao có thể chạy **một lịch riêng**, tạo concurrency liên máy.

### 2.2. Khi task nhẹ hơn period

Nếu task chạy 1 giây, chu kỳ 5 giây:

```text
0s         5s         10s        15s
[job]      [job]      [job]      [job]
|--1s      |--1s      |--1s      |--1s
```

Khoảng rảnh giữa lần 1 hoàn tất và lần 2 bắt đầu xấp xỉ **4 giây**. Đó là hành vi đặc trưng phân biệt fixed rate với fixed delay.

### 2.3. Đừng hiểu fixed rate như một timer “chính xác tuyệt đối”

Một process đang stop-the-world GC, CPU thiếu quota hoặc machine bị suspend có thể khiến lịch trễ đáng kể. Lập lịch periodic của JDK không bảo đảm real-time; nếu tác vụ dùng để tính tiền, chốt sổ hoặc trigger phải chính xác theo lịch nghiệp vụ, cần thiết kế **theo mốc và dữ liệu nghiệp vụ**, không suy luận kết quả từ số lần callback được gọi.

## 3. Hai overload scheduleAtFixedRate() nên dùng

### 3.1. Bắt đầu sớm nhất có thể

```java
ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(
        this::refreshCache,
        Duration.ofSeconds(5));
```

Đây là **fragment**, giả định `scheduler` là một bean `TaskScheduler` và `refreshCache` là method `void refreshCache()` không tham số. Nó tạo một lịch có **chu kỳ 5 giây**, lượt đầu được đăng ký để chạy **sớm nhất có thể**, không mặc nhiên chờ đủ 5 giây trước lượt đầu.

Nếu muốn **trễ 5 giây trước lượt đầu**, dùng overload có `Instant startTime`:

```java
Instant firstRun = Instant.now().plusSeconds(5);

ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(
        this::refreshCache,
        firstRun,
        Duration.ofSeconds(5));
```

Nhớ import `java.time.Instant`, `java.time.Duration` và `java.util.concurrent.ScheduledFuture`. `Instant` là một mốc thời gian tuyệt đối cho **lần chạy đầu**, còn `Duration` là khoảng cách fixed rate cho các lần tiếp theo.

### 3.2. Lịch theo mốc bắt đầu cụ thể

```java
Instant startsAt = Instant.parse("2030-01-01T00:00:00Z");

ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(
        this::reconcile,
        startsAt,
        Duration.ofMinutes(10));
```

Đây là một fragment minh họa với thời điểm UTC cụ thể. Chạy định kỳ mỗi 10 phút từ mốc đầu **không giống** “chạy vào phút 00, 10, 20, ... của mọi giờ theo múi giờ doanh nghiệp” nếu mốc khởi động/độ lệch không phù hợp. Nếu cần lịch theo **ngày, thứ, tháng, ngày lễ hoặc giờ địa phương**, cân nhắc `CronTrigger` hoặc hệ thống scheduler dành cho lịch nghiệp vụ.

### 3.3. Period hợp lệ

Chu kỳ phải là **Duration dương**. Đừng truyền `Duration.ZERO` hoặc giá trị âm để nói “chạy càng nhanh càng tốt”: dưới `ScheduledExecutorService.scheduleAtFixedRate`, period không dương không hợp lệ. Nếu muốn xử lý liên tục theo tốc độ tài nguyên, dùng consumer/worker loop có backpressure, thay vì biến scheduler thành busy spin.

### 3.4. ScheduledFuture dùng để làm gì?

`scheduleAtFixedRate` trả `ScheduledFuture<?>`:

- `cancel(false)`: hủy **các lượt định kỳ sau**, không yêu cầu interrupt lượt đang chạy.
- `cancel(true)`: có thể phát interrupt cho lượt đang chạy tùy implementation; dừng là best-effort.
- `isCancelled()`: biết trạng thái cancellation của Future.
- `isDone()`: với periodic schedule đang chạy bình thường, thường vẫn false; trở thành true nếu canceled hoặc chấm dứt bất thường.
- `get()`: **không dùng để “đợi một lần tick hoàn thành”**. Với periodic future của JDK, `get()` không trả về bình thường trong lúc lịch còn hoạt động; nó sẽ chờ tới khi lịch bị cancel/hoàn tất bất thường rồi có thể ném exception. Để quan sát từng lần chạy, dùng metric, trạng thái job hoặc một future riêng cho mỗi lần tick.

Nếu không giữ `ScheduledFuture` và không có reference tới scheduler, bạn có thể khó hủy một job cụ thể khi cấu hình thay đổi. Với một bean lifecycle, nên lưu handle trong field an toàn và cancel khi dừng.

## 4. Fixed rate, fixed delay, cron và @Scheduled khác nhau thế nào?

| Mô hình | Mốc chạy tiếp theo | Thích hợp với |
| --- | --- | --- |
| `scheduleAtFixedRate(task, period)` | Theo mốc `start + n × period`; có thể bị trễ khi task chạy lâu | Polling theo cadence, cập nhật cache, đo metric |
| `scheduleWithFixedDelay(task, delay)` | **Sau khi lượt trước hoàn tất + delay** | Tránh dồn nhịp, task phụ thuộc vào thời gian nghỉ giữa các lượt |
| `schedule(task, Instant)` | Một lần ở hoặc sau mốc thời gian chỉ định | Chạy một lần trong tương lai |
| `schedule(task, CronTrigger)` | Dựa trên lịch cron/trigger | Chạy lúc 08:00 ngày làm việc, lịch theo giờ/ngày |
| `@Scheduled(fixedRate = ...)` | Spring đăng ký một lịch fixed-rate cho annotated method | Lịch cố định biết từ cấu hình |
| `@Scheduled(fixedDelay = ...)` | Spring đăng ký một lịch fixed-delay | Chờ hoàn tất rồi nghỉ |
| `@Scheduled(cron = ...)` | Spring dùng cron trigger | Lịch theo calendar |

### Ví dụ so sánh bằng con số

Giả sử task mất **3 giây**, chu kỳ hoặc độ trễ cấu hình đều là **5 giây**, bắt đầu ở mốc 0.

**Fixed rate:**

```text
Lượt 1: start 0s, end 3s
Lượt 2: start gần 5s, end gần 8s
Lượt 3: start gần 10s, end gần 13s
```

**Fixed delay:**

```text
Lượt 1: start 0s, end 3s
Lượt 2: start gần 8s  (3 + 5), end gần 11s
Lượt 3: start gần 16s (11 + 5), end gần 19s
```

Các thời điểm trên là **mô hình minh họa không có tài nguyên tranh chấp**, không phải log chính xác từ một JVM thật.

### Lựa chọn thực dụng

- **Fixed rate:** muốn hệ thống cố kiểm tra/đo/đồng bộ với cadence đều, miễn là chấp nhận có thể trễ và cần xử lý quá tải.
- **Fixed delay:** muốn chắc chắn có một khoảng nghỉ sau mỗi lượt đã hoàn tất, tránh gửi tiếp quá sát nếu downstream chậm.
- **Cron:** muốn dựa vào đồng hồ kinh doanh, ví dụ 02:00 mỗi ngày hoặc thứ Hai, có chính sách timezone/DST rõ ràng.

Đừng chỉ đổi `fixedRate` sang `fixedDelay` để giải quyết mọi lỗi: nếu logic xử lý còn trùng lặp qua nhiều replica, chạy chồng lấp do async delegation hoặc không idempotent, đổi nhịp **không sửa được những nguyên nhân đó**.

## 5. @Scheduled là cách khai báo, TaskScheduler là API lập lịch chủ động

Trong Spring, có hai kiểu tổ chức thông dụng.

**Khai báo trực tiếp:**

```java
@Configuration
@EnableScheduling
class SchedulingConfig {}

@Component
class CacheRefreshJob {
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
    public void refresh() {
        // Một lượt xử lý.
    }
}
```

Đây là **fragment**; cần imports `@Configuration`, `@EnableScheduling`, `@Component`, `@Scheduled` và `TimeUnit`. Với lịch khai báo như trên, Spring nhận diện method và đăng ký scheduling dựa trên metadata. Với method đồng bộ thông thường, return type nên là `void` và không nhận tham số.

**Lập lịch chủ động qua bean:**

```java
ScheduledFuture<?> handle = taskScheduler.scheduleAtFixedRate(
        () -> refreshCache(),
        Duration.ofSeconds(5));
```

Đây là fragment: code tự chọn **khi nào bắt đầu đăng ký**, chu kỳ bao lâu, và có handle để hủy cụ thể. Hữu ích khi cấu hình đến từ DB/feature flag hoặc cần start/stop linh hoạt.

### 5.1. Có cần @EnableScheduling để dùng scheduleAtFixedRate trực tiếp không?

**Không bắt buộc.** Nếu application đã có một bean `TaskScheduler` hợp lệ và code trực tiếp gọi `scheduleAtFixedRate(...)`, thao tác đăng ký lịch diễn ra thông qua API; không cần annotation processor của `@Scheduled` để làm việc đó.

`@EnableScheduling` cần khi bạn muốn Spring **phát hiện và đăng ký các method `@Scheduled`** tự động. Không bật cả hai cách cho **cùng một công việc** mà không có chủ đích, vì rất dễ tạo hai lịch độc lập và chạy trùng.

### 5.2. Tìm hiểu initial delay của @Scheduled

```java
@Scheduled(
        initialDelay = 2,
        fixedRate = 5,
        timeUnit = TimeUnit.SECONDS)
public void tick() {
    // Lượt đầu sau khoảng 2 giây, rồi cadence 5 giây.
}
```

Đây là fragment, được Spring hỗ trợ trong những phiên bản có thuộc tính `timeUnit`. Nó tương đương về **ý tưởng** với việc chỉ định một `Instant` cho lượt đầu, nhưng không cho bạn cùng quyền chủ động giữ `ScheduledFuture` trong method đang annotate.

`@Scheduled` cũng hỗ trợ các thuộc tính cấu hình chuỗi/placeholder (tùy version) và cron; nên chọn theo nhu cầu deploy, timezone và khả năng cần thay đổi schedule runtime.

## 6. ThreadPoolTaskScheduler và cách các worker được sử dụng

`ThreadPoolTaskScheduler` quản lý `ScheduledExecutorService` phía dưới. Đối với implementation này, cần hiểu sự khác nhau với `ThreadPoolTaskExecutor`:

| Thuộc tính | ThreadPoolTaskScheduler | ThreadPoolTaskExecutor |
| --- | --- | --- |
| Mục tiêu | Lập lịch theo thời gian/trigger | Giao công việc thực thi |
| Tham số chính | `poolSize`, error handler, shutdown, scheduler policies | `corePoolSize`, `maxPoolSize`, `queueCapacity` |
| Work chạy ở đâu? | Thường trên scheduler worker theo lịch | Worker của task executor |
| Nếu task giữ worker quá lâu | Các lịch khác có thể bị trễ | Queue/task khác có thể đợi hoặc bị từ chối |
| Future của periodic job | Đại diện **chuỗi lần chạy**, hủy lịch | Future thường đại diện **một task** |
| Chịu trách nhiệm persistence? | Không | Không |

### 6.1. Pool size có nghĩa gì?

```java
@Bean("billingScheduler")
ThreadPoolTaskScheduler billingScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("billing-schedule-");
    return scheduler;
}
```

Đây là fragment cho một Spring configuration class. Pool size 2 nghĩa là scheduler có khả năng chạy các công việc được kích hoạt **đồng thời trên hai worker**. Nhưng **không** có nghĩa một *đăng ký fixed-rate riêng lẻ* sẽ tự chồng hai lượt thực thi lên nhau nếu xử lý trễ. Một scheduler pool thường hỗ trợ nhiều lịch khác nhau, ví dụ quét hóa đơn và làm mới cache.

`ThreadPoolTaskScheduler` sử dụng kiểu scheduled executor có hàng chờ trì hoãn; không đánh đồng với queue hữu hạn `queueCapacity` của `ThreadPoolTaskExecutor`. Tạo hàng chục nghìn lịch sống lâu có thể tiêu tốn bộ nhớ/khó quản lý, dù vài lịch ít hoạt động không bão hòa CPU.

### 6.2. ThreadPoolTaskScheduler chạy việc trên scheduler threads

Nếu một `Runnable` fixed-rate gọi HTTP client blocking trong 10 giây, nó có thể giữ scheduler worker cả 10 giây. Khi pool nhỏ, lịch khác phải chờ.

Có thể tách lịch và xử lý:

```java
scheduler.scheduleAtFixedRate(
        () -> workExecutor.execute(this::processBatch),
        Duration.ofSeconds(5));
```

Nhưng lúc này **một “tick” chỉ giao việc**. Các batch trong `workExecutor` có thể chồng lấp dù scheduler wrapper chạy rất nhanh. Hãy định nghĩa giới hạn overlap và backpressure rõ ràng:

- Từ chối submit khi batch trước chưa xong hoặc dùng state machine/lease.
- Giới hạn queue và thread count của worker executor.
- Nếu cần bảo đảm không rơi job, ghi yêu cầu vào database/broker bền vững.
- Không coi sự thành công của `execute` là kết quả hoàn tất nghiệp vụ.
- Không mặc định mọi task được chạy đúng chu kỳ nếu worker pool đã đầy.

### 6.3. Không có thời gian thực và không có cluster leader tự động

Mỗi instance Spring chạy bean scheduler riêng. Nếu scale từ 1 lên 3 replica, một `@Scheduled(fixedRate=5000)` hoặc cùng lệnh đăng ký trên mỗi replica thường dẫn đến **ba lịch**; task có thể chạy ba lần tại những mốc gần nhau.

Muốn chỉ một instance thực hiện việc toàn cục, cần một cơ chế leader election, distributed lock/lease, coordinator/job store hoặc lựa chọn nền tảng scheduler thích hợp. Phải tính race giữa các replica, lease hết hạn trong khi worker chưa xong và idempotency khi retry.

## 7. Java 17 runnable: fixed-rate không chồng lấp một periodic task

Để chứng minh không dựa vào may rủi timing, demo sử dụng một `ScheduledThreadPoolExecutor` có **hai worker**, nhưng chỉ **một lịch recurring**. Task thứ ba cố ý chờ latch, main thread hủy lượt lặp tiếp theo rồi giải phóng latch. Ta đo số lượt và mức concurrency tối đa.

Lưu thành **FixedRateDemo.java**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class FixedRateDemo {
    public static void main(String[] args) throws Exception {
        ScheduledThreadPoolExecutor pool =
                new ScheduledThreadPoolExecutor(2);
        pool.setRemoveOnCancelPolicy(true);

        AtomicInteger runs = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch thirdStarted = new CountDownLatch(1);
        CountDownLatch releaseThird = new CountDownLatch(1);

        try {
            ScheduledFuture<?> schedule = pool.scheduleAtFixedRate(() -> {
                int concurrentlyRunning = active.incrementAndGet();
                maxActive.updateAndGet(
                        previous -> Math.max(previous, concurrentlyRunning));

                try {
                    int number = runs.incrementAndGet();
                    if (number == 3) {
                        thirdStarted.countDown();
                        try {
                            releaseThird.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } finally {
                    active.decrementAndGet();
                }
            }, 0, 20, TimeUnit.MILLISECONDS);

            thirdStarted.await();           // Lượt 3 đã vào Runnable
            boolean cancelled = schedule.cancel(false);
            releaseThird.countDown();       // Cho lượt 3 hoàn tất
            pool.shutdown();

            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Scheduler did not stop");
            }

            System.out.println("runs=" + runs.get());
            System.out.println("maxConcurrent=" + maxActive.get());
            System.out.println("cancelled=" + cancelled);
        } finally {
            releaseThird.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy trên Java 17+:

```bash
javac FixedRateDemo.java
java FixedRateDemo
```

**Stdout trong điều kiện demo bình thường:**

```text
runs=3
maxConcurrent=1
cancelled=true
```

### Vì sao ví dụ đáng tin?

1. `scheduleAtFixedRate` tạo **một periodic schedule** với lần đầu delay 0 và chu kỳ 20 ms. Không yêu cầu callback phải hoàn thành sau đúng 20 ms; đó là *lịch dự kiến*.
2. `thirdStarted.await()` đợi đến lúc lần chạy thứ ba đã được thực thi; nó không đoán bằng `Thread.sleep`.
3. Khi lượt ba đang chặn `releaseThird.await()`, main gọi `schedule.cancel(false)`. Tại thời điểm đó lịch chưa hoàn thành, nên cancellation có hiệu lực đối với những lần chạy tiếp theo.
4. `cancel(false)` không interrupt lượt ba; sau khi mở latch, lượt ba kết thúc bình thường.
5. `pool.shutdown()` và `awaitTermination` bảo đảm worker hoàn tất trước khi main in kết quả. `runs` chính xác là 3 vì các lượt sau đã bị hủy.
6. Hai thread sẵn có không khiến **cùng một periodic task** chạy đồng thời nhiều lượt; `maxConcurrent=1` cho thấy không có overlap trong demo.
7. `finally` luôn giải phóng latch và yêu cầu đóng pool để tránh giữ tiến trình nếu phần trước thất bại.

**Ranh giới kết luận:** demo này chứng minh behavior của **`ScheduledThreadPoolExecutor` trong Java 17** với một đăng ký fixed-rate. Nó không có nghĩa mọi thiết kế scheduler/job queue ngoài JDK đều cấm overlap, đặc biệt khi Runnable tự giao việc cho executor khác hoặc ứng dụng chạy nhiều replica. Ngoài ra, **tên thread và timestamp thực tế không phải hợp đồng** của scheduler.

## 8. Lỗi trong một lượt có làm dừng cả lịch?

Đây là khác biệt tinh vi giữa raw JDK periodic scheduling và cấu hình Spring:

### 8.1. ScheduledThreadPoolExecutor thuần JDK

Javadoc `ScheduledExecutorService.scheduleAtFixedRate` quy định nếu một lượt chạy ném exception không được xử lý, **các lượt tiếp theo bị ngăn lại**. Periodic `ScheduledFuture` hoàn tất bất thường; gọi `get()` có thể ném `ExecutionException` chứa nguyên nhân gốc.

```java
ScheduledFuture<?> future = pool.scheduleAtFixedRate(() -> {
    throw new IllegalStateException("tick failed");
}, 0, 1, TimeUnit.SECONDS);
```

Đây là fragment; `pool` cần là scheduled executor. Khi task ném exception không được bắt, chuỗi định kỳ theo JDK sẽ dừng.

### 8.2. Spring ThreadPoolTaskScheduler có ErrorHandler

Spring có lớp bọc xử lý lỗi cho task định kỳ. Với `ThreadPoolTaskScheduler`, **handler mặc định cho tác vụ lặp lại thường log rồi suppress lỗi** để một lần thất bại không ngăn các lần chạy sau. Ngược lại, nếu tự cấu hình handler để **ném lại exception**, lịch có thể chấm dứt như hành vi raw JDK.

Ví dụ cấu hình:

```java
scheduler.setErrorHandler(error ->
        log.error("Recurring task failed", error));
```

Đây là fragment, giả định `scheduler` và `log` đã tồn tại; lambda chỉ ghi lỗi rồi return, nên không yêu cầu propagate. **Không nên nhìn mỗi tên `scheduleAtFixedRate` rồi khẳng định một exception luôn dừng lịch** trên mọi implementation.

Tuy nhiên, việc tiếp tục scheduling không có nghĩa một lỗi nghiệp vụ đã được khắc phục. Nếu job thất bại 10.000 lần, log lỗi liên tục và tiếp tục chạy có thể làm hệ thống tệ hơn. Nên đo failed attempts, áp backoff/circuit breaker khi phù hợp, và quyết định có chuyển sang trạng thái PAUSED/FAILED theo quy tắc nghiệp vụ.

Tham khảo Spring `TaskUtils` và cơ chế error handling của recurring tasks:
https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/support/TaskUtils.html

### 8.3. Khác nhau giữa log lỗi và xử lý lỗi

Một error handler chỉ ghi log không tự tạo:

- Retry có backoff;
- Bảo đảm gửi đúng một email;
- Rollback thao tác đã commit;
- Lưu job vào queue bền vững;
- Cảnh báo vận hành khi lỗi vượt ngưỡng.

Hãy dùng trạng thái công việc, metric `success/failure` và cảnh báo riêng. Một lịch vẫn tiếp tục tick chỉ chứng minh **scheduler còn hoạt động**, không chứng minh nghiệp vụ các lượt trước đã thành công.

## 9. Spring Boot 3.x đầy đủ: đăng ký và hủy fixed-rate khi ứng dụng chạy

Ví dụ sau dùng **Java 17+, Spring Boot 3.x / Spring Framework 6.x** và dependency `spring-boot-starter-web`. Nó tạo một bean `ThreadPoolTaskScheduler` do Spring quản lý, đăng ký job fixed-rate **khi ứng dụng sẵn sàng** và giữ `ScheduledFuture` để hủy khi context đóng. Một endpoint đọc số lượt đã bắt đầu.

**Điểm cần ghi nhớ trước khi chạy:** job chỉ tăng bộ đếm và ghi log; **không thực hiện thao tác nghiệp vụ thực**. Không có bảo đảm thời điểm log chính xác đến millisecond hoặc job trạng thái bền vững qua restart.

Đặt toàn bộ nội dung vào **src/main/java/com/example/demo/PeriodicApplication.java**:

```java
package com.example.demo;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class PeriodicApplication {
    private static final Logger log =
            LoggerFactory.getLogger(PeriodicApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PeriodicApplication.class, args);
    }

    @Bean("periodicScheduler")
    ThreadPoolTaskScheduler periodicScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("periodic-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setErrorHandler(error ->
                log.error("Periodic task failed", error));
        return scheduler; // Spring quản lý initialize/shutdown của bean.
    }
}

@Service
class PeriodicJob {
    private static final Logger log =
            LoggerFactory.getLogger(PeriodicJob.class);

    private final TaskScheduler scheduler;
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile ScheduledFuture<?> schedule;

    PeriodicJob(
            @Qualifier("periodicScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (schedule != null) {
            return; // Không đăng ký lặp trong cùng bean.
        }
        schedule = scheduler.scheduleAtFixedRate(
                this::runOneTick, Duration.ofSeconds(5));
        log.info("Periodic job registered, period=5s");
    }

    private void runOneTick() {
        int attempt = attempts.incrementAndGet();
        log.info("Tick {} on {}", attempt,
                Thread.currentThread().getName());

        // Chỉ để minh họa handler; tick 3 cố tình thất bại.
        if (attempt == 3) {
            throw new IllegalStateException("Simulated tick failure");
        }
    }

    public int attempts() {
        return attempts.get();
    }

    public boolean isScheduled() {
        ScheduledFuture<?> current = schedule;
        return current != null
                && !current.isCancelled()
                && !current.isDone();
    }

    @PreDestroy
    public void stop() {
        ScheduledFuture<?> current = schedule;
        if (current != null) {
            current.cancel(false); // Không interrupt tick đang chạy.
        }
        log.info("Periodic job cancellation requested");
    }
}

record PeriodicStatus(int attempts, boolean scheduled) {}

@RestController
class PeriodicController {
    private final PeriodicJob job;

    PeriodicController(PeriodicJob job) {
        this.job = job;
    }

    @GetMapping("/schedule/status")
    public PeriodicStatus status() {
        return new PeriodicStatus(job.attempts(), job.isScheduled());
    }
}
```

Trong project có Spring Boot Gradle plugin và wrapper, chạy:

```bash
./gradlew bootRun
curl -s http://localhost:8080/schedule/status
```

Trên Windows, sử dụng `gradlew.bat bootRun`. Khi context đã phát sự kiện `ApplicationReadyEvent` và schedule được đăng ký thành công, một response ví dụ là:

```json
{"attempts":2,"scheduled":true}
```

**Đây là response minh họa, không phải output cố định**: số lần tick phụ thuộc thời điểm bạn gọi endpoint, lịch thực thi, việc dừng ứng dụng và điều kiện môi trường. Ở tick thứ ba, method cố ném `IllegalStateException`. Vì handler của `ThreadPoolTaskScheduler` trong ví dụ **ghi lỗi rồi return**, lịch có thể tiếp tục chạy các tick tiếp theo. Endpoint đo **số lần bắt đầu**, không phải số lần thành công; đây là chủ ý để thấy khác biệt giữa “đã chạy lịch” và “nghiệp vụ thành công”.

### 9.1. Phân tích vòng đời

1. Spring tạo `periodicScheduler` thành **bean có tên**, cấu hình pool và handler.
2. `PeriodicJob` inject `TaskScheduler` bằng `@Qualifier` để không phụ thuộc lựa chọn bean mặc định.
3. `@EventListener(ApplicationReadyEvent.class)` đăng ký lịch khi ứng dụng đã tới giai đoạn ready; không khởi động task trong constructor.
4. `scheduleAtFixedRate(this::runOneTick, Duration.ofSeconds(5))` đăng ký **một** periodic task, lượt đầu sớm nhất có thể.
5. `ScheduledFuture<?> schedule` được giữ lại để biết lịch đang tồn tại và hủy khi dừng.
6. `@PreDestroy` gọi `cancel(false)` để ngăn những lượt tiếp theo. Với một lượt đang chạy, `false` không phát interrupt.
7. Spring tiếp tục quản lý shutdown của bean scheduler; riêng cancellation ở service thể hiện rõ ranh giới sở hữu lịch.

**Không có `@EnableScheduling` trong ví dụ này** vì code **gọi TaskScheduler API trực tiếp**, không có method `@Scheduled`. Nếu thay bằng annotation thì cần bật scheduling annotation infrastructure một cách tương ứng.

### 9.2. Vì sao không phải dùng một biến boolean để biết job đã hoàn thành?

`isScheduled()` trong ví dụ cho biết **periodic future còn active**, không chứng minh đã có tick thành công. Nếu task mất kết nối DB 100 lần, lịch vẫn có thể active và mọi nghiệp vụ đều lỗi.

Một hệ thống thật nên có:

- Lần chạy gần nhất bắt đầu/kết thúc lúc nào;
- Số lần thành công và thất bại riêng;
- Thời gian chạy, độ trễ so với lịch, số lần bị skip;
- Tình trạng downstream và giới hạn retry;
- Job ID/attempt ID cho mỗi lần chạy nếu cần truy vết;
- Alert nếu quá lâu không có lượt thành công.

### 9.3. Có thể dùng @Scheduled thay cho API này không?

Có. Nếu lịch cố định từ cấu hình và không cần cancel từng handle một cách linh hoạt, ví dụ:

```java
@Configuration
@EnableScheduling
class EnableJobs {}

@Component
class ReconciliationTick {
    @Scheduled(fixedRate = 5000, initialDelay = 2000)
    public void reconcile() {
        // Job chạy ở khoảng thời gian do Spring lên lịch.
    }
}
```

Đây là **cấu hình thay thế**, không đặt thêm vào cùng ứng dụng nếu cùng nghiệp vụ đã được đăng ký thủ công và không muốn double scheduling. Trong Spring, `@Scheduled` không thể tự làm cho job **chỉ chạy trên một replica**. Nếu cần Cron, dùng `@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")` với chủ ý về lịch/ngày/giờ và DST theo khu vực cần thiết.

## 10. Fixed-rate và việc chạy chồng lấp: chính xác chỗ nào an toàn?

Thường nghe “fixed rate cứ đến giờ là mở thread chạy mới”. Điều này **không chính xác** đối với một registration định kỳ dựa trên `ScheduledThreadPoolExecutor`.

### 10.1. Một recurring task trực tiếp trên ThreadPoolTaskScheduler

```text
scheduleAtFixedRate(task, 5s)
       |
       +-- Runnable lượt 1 (chạy lâu 8s)
       +-- Runnable lượt 2 (bắt đầu trễ sau lượt 1)
       +-- Runnable lượt 3 (bắt đầu khi đủ điều kiện sau lượt 2)
```

Các lần thực thi liên tiếp của **chính periodic task này** không overlap, theo `ScheduledThreadPoolExecutor`. Pool size 4 không biến một recurring handle thành bốn bản sao song song.

### 10.2. Runnable chỉ giao việc sang @Async hoặc một pool khác

```java
scheduler.scheduleAtFixedRate(
        () -> reportService.startReportAsync(),
        Duration.ofSeconds(5));
```

Đây là fragment; `reportService` cần là **Spring bean khác** được inject và method được cấu hình `@Async` phù hợp. Lượt scheduler có thể return ngay sau khi submit. Nếu report mất 20 giây, sau 5 giây scheduler **vẫn có thể submit report khác**. Do đó hai nghiệp vụ report **có thể chạy đồng thời**.

Muốn tránh chồng lấp thật sự:

- Không submit mới khi job cũ chưa hoàn tất, sử dụng `AtomicBoolean`/state/lease phù hợp.
- Hoặc để scheduler chạy một công việc đồng bộ có thời gian chạy phù hợp và chấp nhận các lượt trễ.
- Hoặc dùng job store/queue độc lập, idempotency và giới hạn concurrency theo workload.

### 10.3. Nhiều lịch hoặc nhiều replica

Nếu đăng ký hai lần:

```java
scheduler.scheduleAtFixedRate(task, Duration.ofSeconds(5));
scheduler.scheduleAtFixedRate(task, Duration.ofSeconds(5));
```

Đây là **hai periodic registrations độc lập** dù dùng cùng một đối tượng `Runnable`. Chúng có thể overlap nếu scheduler có nhiều worker. Nếu một method vừa dùng `@Scheduled` vừa bị gọi đăng ký thủ công, tác động có thể lặp.

Nếu chạy ba replica, mỗi replica có thể có schedule riêng và cùng tác động DB. Muốn chỉ một instance thực hiện công việc toàn cụm, cần phối hợp phân tán **ngoài** TaskScheduler.

**Quy tắc:** khẳng định “non-overlap” chỉ có ý nghĩa khi bạn nói rõ **một handle, một executor implementation, phạm vi một process**.

## 11. Áp lực tải, trễ lịch và lựa chọn backpressure

Giả sử task quét hóa đơn chạy khoảng 12 giây nhưng fixed rate đặt 2 giây. Một scheduler worker gần như bị chiếm liên tục vì các lượt sau luôn đang đến hạn. Nếu không kiểm soát số lần làm việc, hệ thống có thể bị quá tải kéo dài hoặc downstream nhận request quá dày.

### 11.1. Tách ba loại độ trễ

| Chỉ số | Ý nghĩa |
| --- | --- |
| Scheduled time | Mốc mà task dự kiến đủ điều kiện chạy |
| Actual start | Khi worker thực sự bắt đầu thực thi |
| Completion time | Khi lượt đó kết thúc |
| Start lag | Actual start trừ scheduled time |
| Run duration | Completion trừ actual start |
| End-to-end job latency | Thời gian từ trigger tới kết quả nghiệp vụ có thể dùng |

Nếu chỉ log `"tick"` mà không đo duration/start lag, bạn khó biết vì sao lịch 5 giây nhưng nghiệp vụ cập nhật dữ liệu chậm 1 phút.

### 11.2. Cách giảm nghẽn

- **Rút ngắn tick:** chỉ chọn việc đủ điều kiện rồi chuyển sang pipeline có capacity control.
- **Batch có giới hạn:** xử lý từng nhóm nhỏ với state/offset để tránh một lượt giữ worker hàng chục phút.
- **Backpressure:** dùng semaphore/bulkhead, giới hạn số tác vụ cùng lúc và queue.
- **Skip có chủ đích:** nếu lần cũ chưa xong và nghiệp vụ cho phép, ghi metric `skipped_due_to_busy`, không tạo thêm job.
- **Điều chỉnh cadence:** một poller chờ downstream có thể thích hợp với fixed delay hơn.
- **Timeout ở dependency:** cấu hình HTTP/JDBC timeout phù hợp; `scheduleAtFixedRate` không tự chặn một task treo vô hạn.
- **Idempotency:** mỗi lần quét dùng khóa công việc để tránh tác động trùng khi restart/replica/retry.

### 11.3. Không tăng poolSize vô hạn

Nhiều scheduler workers giúp những **lịch độc lập** không làm nghẽn nhau, nhưng không làm task cũ nhanh hơn và không giải quyết sự quá tải của database/API. Việc mở rộng cần dựa trên đo lường CPU, latency, concurrency downstream và khả năng xử lý.

## 12. Hủy schedule và shutdown có nghĩa gì?

`ScheduledFuture.cancel(boolean)` hủy chuỗi lịch lặp khi được chấp nhận. Hai trường hợp:

**`cancel(false)`:** không yêu cầu interrupt lượt đang chạy, nhưng ngăn các lượt tiếp theo theo hợp đồng Future.

**`cancel(true)`:** có thể yêu cầu interrupt runner của lượt đang chạy trong implementation hỗ trợ. Interrupt là tín hiệu **hợp tác**; nếu task đang block ở API không hỗ trợ interrupt hoặc side effect đã được gửi ra ngoài, không thể đảm bảo dừng ngay hoặc rollback.

```java
ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(
        this::poll,
        Duration.ofSeconds(5));

// Khi cấu hình không còn cần lịch:
boolean cancellationAccepted = handle.cancel(false);
```

Đây là fragment, cần `scheduler`, `poll` và imports.

**Đừng dùng `Future.get()`** để chờ “lần chạy đầu xong”. Với periodic future, nó đại diện **cả series**, có thể chờ vô hạn khi lịch còn active. Muốn biết một lượt đã hoàn tất, task cần công bố kết quả/metric hoặc trả một `CompletableFuture` riêng cho lượt đó.

### 12.1. Shutdown scheduler bean

Khi Spring context đóng, `ThreadPoolTaskScheduler` có lifecycle cleanup. Các lựa chọn như chờ task hiện có hoàn tất và chính sách có tiếp tục periodic tasks sau shutdown hay không phụ thuộc cấu hình implementation. Thông thường lịch sẽ không còn nhận lượt mới sau shutdown; nhưng không được mặc định tắt JVM sẽ ghi lại lịch để khôi phục sau restart.

Với application có job quan trọng, thiết kế shutdown:

1. Ngừng nhận/đăng ký schedule mới.
2. Hủy handle hoặc đánh dấu stop-requested theo nghiệp vụ.
3. Để lượt đang chạy kết thúc trong grace period hợp lý, hoặc gửi interrupt có kiểm soát.
4. Ghi trạng thái tiến độ/job offset vào nguồn bền vững.
5. Đảm bảo restart có thể tiếp tục công việc cần làm, không phụ thuộc những tick đã mất trong RAM.

### 12.2. Remove-on-cancel policy

`ScheduledThreadPoolExecutor` có cơ chế giữ cancelled delayed tasks trong queue cho tới thời điểm delay trôi qua theo mặc định; điều này có thể gây giữ tham chiếu lâu khi ứng dụng tạo/hủy rất nhiều lịch.

`ThreadPoolTaskScheduler#setRemoveOnCancelPolicy(true)` yêu cầu implementation bên dưới cố xóa task đã cancel sớm hơn khi có thể. Nó giúp giảm retention trong những workload đăng ký/hủy thường xuyên, nhưng không thay thế việc **quản lý handle, lifecycle và giám sát tài nguyên**.

## 13. Lập lịch theo chu kỳ không đồng nghĩa lịch nghiệp vụ chính xác

### 13.1. Fixed rate là Duration, không phải “mỗi ngày đúng 02:00”

`Duration.ofHours(24)` là khoảng cách 24 giờ. Nó **không** đồng nghĩa “02:00 giờ địa phương mỗi ngày”, vì múi giờ/ngày đổi DST có thể có ngày dài 23 hoặc 25 giờ ở một số khu vực.

Nếu yêu cầu “mỗi ngày 02:00 theo một timezone”, dùng cron/trigger với timezone rõ ràng và xác định quy tắc khi giờ không tồn tại hoặc trùng lặp. Không dùng fixed rate mù quáng cho lịch cần xét ngày lễ hoặc calendar business.

### 13.2. Không dùng tick count làm nguồn sự thật

Ví dụ scheduler chạy mỗi 10 giây, một máy bị dừng 5 phút. Khi bật lại, tick count mới không tự biểu diễn tất cả các sự kiện nghiệp vụ trong 5 phút mất kết nối.

Đúng hơn, mỗi lượt **quét dữ liệu theo điều kiện**:

```sql
-- Chỉ là truy vấn minh họa, không phải schema hoàn chỉnh.
SELECT id
FROM pending_jobs
WHERE next_attempt_at <= CURRENT_TIMESTAMP
  AND status = 'PENDING'
ORDER BY next_attempt_at
LIMIT 100;
```

Worker xử lý có khóa/lease hoặc điều kiện cập nhật phù hợp để tránh nhiều replica lấy cùng job. **Database/job store là nguồn sự thật**, còn fixed-rate tick chỉ là cơ chế đánh thức poller.

### 13.3. Idempotency và nhiều instance

Với task quét invoice, nếu hai replica cùng phát hiện một invoice chưa xử lý, cả hai có thể cùng gửi request đến provider. `scheduleAtFixedRate` không có distributed lock. Cần optimistic locking, unique constraint, claim/lease hoặc job store đáng tin và idempotency key cho side effect. Không xem log “chỉ có một tick” ở một instance là bằng chứng toàn cluster chạy đúng một lần.

## 14. Quan sát, lỗi và ngữ cảnh thực thi

Trong production, theo dõi:

- **Registration count:** bao nhiêu lịch đang được đăng ký, có trùng hay không;
- **Actual tick count:** số lần callback bắt đầu;
- **Success/failure count:** số lượt đạt kết quả nghiệp vụ hoặc lỗi;
- **Duration và start lag:** việc xử lý có chậm hơn period không;
- **Skipped ticks/batches:** số lượt bỏ có chủ đích do job trước còn bận;
- **Worker active và pool size:** lịch khác có bị nghẽn không;
- **Shutdown/cancel:** khi nào hủy lịch, còn lượt đang chạy không;
- **Retry/duplicate processing:** đặc biệt trên nhiều replica.

### 14.1. Logging correlation ID cho scheduled job

Một scheduled task thường **không có HTTP request** và không nên mong MDC từ servlet thread tự đi theo. Có thể tạo `jobRunId` trong mỗi lượt chạy, đặt vào MDC trong scope rồi cleanup:

```java
String runId = UUID.randomUUID().toString();
String previous = MDC.get("jobRunId");
try {
    MDC.put("jobRunId", runId);
    log.info("Starting periodic reconciliation");
    reconcile();
} finally {
    if (previous == null) MDC.remove("jobRunId");
    else MDC.put("jobRunId", previous);
}
```

Đây là fragment, cần imports SLF4J `MDC`, `Logger`, UUID và implementation `reconcile`. Vì scheduler worker được tái sử dụng, thiếu `finally` có thể làm run sau mang sai ID.

Nếu job bền vững, có thể có `jobId` cố định cho logical job và `attemptId`/`runId` cho mỗi lần thực thi; không sử dụng request ID làm idempotency key thay cho job ID.

### 14.2. Với @Async hoặc CompletableFuture bên trong tick

`@Async` chuyển execution sang executor khác thông qua Spring proxy; `CompletableFuture.supplyAsync` không chỉ định executor có thể dùng common pool. MDC, transaction và security context **không tự được truyền** qua mọi thread. Nếu cần, dùng context-aware executor/TaskDecorator hoặc truyền metadata như dữ liệu.

**Tác vụ scheduler đã return không phải bằng chứng công việc ở async executor đã xong.** Điều này ảnh hưởng cả giới hạn overlap lẫn cách đếm thành công.

## 15. Kiểm thử scheduleAtFixedRate đúng cách

Timing test rất dễ flaky. Đừng viết test “ngủ 100 ms rồi chắc chắn có đúng ba lần chạy”; GC và scheduler có thể tạo lệch lớn.

### 15.1. Những kiểm thử cần có

1. **Registration:** task được đăng ký một lần, period và startTime đúng cấu hình.
2. **Activation:** có ít nhất một tick khi scheduler chạy; dùng `CountDownLatch`/condition có timeout tổng thay vì chờ vô hạn.
3. **Cancellation:** sau cancel, không có lượt mới được scheduling, trong khi lượt đang chạy với `cancel(false)` có thể kết thúc.
4. **Non-overlap:** với một recurring task của `ScheduledThreadPoolExecutor`, xác nhận concurrency tối đa là 1 dưới mô hình test.
5. **Exception:** một lượt lỗi có làm dừng lịch hay được handler suppress theo cấu hình không.
6. **Heavy task:** tick mất lâu hơn period làm tăng start lag, không tạo hai lượt overlap của cùng handle.
7. **Multiple schedules:** các job độc lập có thể song song theo pool size; không vô tình đăng ký một job hai lần.
8. **Shutdown:** context đóng, bean scheduler hủy task và thread được dọn.
9. **Cluster idempotency:** nếu nhiều replica cùng tick, không nhân đôi side effect khi claim một job logic.
10. **Observability:** metric số lượt thành công/thất bại tách riêng và có run ID.

### 15.2. Không kiểm tra timestamp như số tuyệt đối

Một test có thể ghi:

```java
long startedAtNanos = System.nanoTime();
// Trong callback: đo độ trễ, không khẳng định start đúng 5000 ms tuyệt đối.
```

Đây là fragment. Dùng `System.nanoTime()` để đo **khoảng thời gian**; không dùng nó làm wall-clock lịch nghiệp vụ. Khi so sánh actual time với target, chấp nhận jitter hợp lý và không ép độ chính xác mà Java scheduler không cam kết.

### 15.3. Test logic nghiệp vụ tách khỏi scheduler

Thay vì nhồi logic vào lambda không thể gọi riêng:

```java
scheduler.scheduleAtFixedRate(() -> {
    // Hàng trăm dòng business code.
}, Duration.ofSeconds(5));
```

Nên tổ chức:

```java
scheduler.scheduleAtFixedRate(
        jobService::runOneBatch,
        Duration.ofSeconds(5));
```

Các fragment trên giả định `jobService` là service đã inject. Test `runOneBatch` như một business unit độc lập bằng các fake repository/client; test scheduler chỉ xác nhận đúng trigger, cancellation và lifecycle. Điều đó giảm test phụ thuộc timing và giúp tái sử dụng job khi cần chạy manual/retry.

## 16. Các sai lầm thường gặp

**Lỗi 1 — Cho rằng fixed rate luôn đúng từng millisecond.** JVM scheduler không phải real-time; GC, CPU, queue và task lâu có thể gây trễ.

**Lỗi 2 — Cho rằng fixed rate = luôn overlap.** Với một periodic registration trên `ScheduledThreadPoolExecutor`, các lượt không overlap; có thể bắt đầu muộn.

**Lỗi 3 — Cho rằng fixed rate = không bao giờ overlap trong toàn hệ thống.** Nếu Runnable submit async work, có nhiều registrations hoặc replicas, nghiệp vụ vẫn có thể overlap.

**Lỗi 4 — Nhầm fixedRate với fixedDelay.** Fixed rate theo mốc bắt đầu dự kiến, fixed delay đếm từ lúc lượt trước hoàn tất.

**Lỗi 5 — Truyền `Duration.ZERO`/âm.** Chu kỳ không dương không hợp lệ; cần positive period.

**Lỗi 6 — Nghĩ `scheduleAtFixedRate(task, period)` sẽ chờ period rồi mới chạy lần đầu.** Overload này bắt đầu sớm nhất có thể. Muốn initial delay dùng `Instant startTime` hoặc `@Scheduled(initialDelay=...)`.

**Lỗi 7 — Gọi `ScheduledFuture.get()` để chờ “tick hiện tại”.** Future đại diện chuỗi định kỳ, có thể chờ tới cancel/error; cần completion riêng cho từng lượt.

**Lỗi 8 — Không giữ handle khi cần hủy riêng lịch.** Không có handle, việc thay cấu hình động hoặc dừng một job cụ thể khó khăn.

**Lỗi 9 — Không phân biệt default lỗi của JDK và Spring.** Raw JDK periodic task bị ngừng sau exception không bắt; Spring thường log/suppress cho repeating tasks. Cần xem ErrorHandler.

**Lỗi 10 — Chỉ tăng poolSize để khắc phục task chậm.** Pool lớn giúp các lịch độc lập, không tăng tốc một task CPU-bound/blocking hay sửa bottleneck downstream.

**Lỗi 11 — Tạo scheduler mới cho mỗi HTTP request.** Sinh nhiều pool và lịch sống lâu, khó shutdown, memory/thread retention. Ưu tiên Spring bean dùng chung.

**Lỗi 12 — Bật cả @Scheduled và đăng ký TaskScheduler thủ công cho cùng việc.** Có thể double schedule.

**Lỗi 13 — Giả định @Scheduled chạy một lần trên cả cluster.** Mỗi replica có lịch riêng; cần distributed coordination/idempotency nếu đó là yêu cầu.

**Lỗi 14 — Dùng fixed rate 24 giờ cho “02:00 địa phương hằng ngày”.** Một Duration 24 giờ không xử lý DST/calendar như cron.

**Lỗi 15 — Chỉ log tick mà không đo success/failure, duration và lag.** Có thể scheduler vẫn chạy nhưng business job thất bại liên tục.

**Lỗi 16 — Dùng cancel(true) như rollback.** Interrupt không tự hủy side effect đã xảy ra; cần cancellation hợp tác và reconciliation.

**Lỗi 17 — Nhầm ThreadPoolTaskScheduler với ThreadPoolTaskExecutor.** Scheduler có pool size và delayed queue semantics riêng, không dùng `queueCapacity` để mô tả chính xác nó.

**Lỗi 18 — Dùng một periodic tick làm nguồn sự thật về dữ liệu còn thiếu.** Khi shutdown/crash, các tick có thể mất; dùng job store với thời điểm `next_run`/`next_attempt` nếu cần phục hồi.

## 17. Checklist sử dụng trên hệ thống thật

- [ ] Xác định task cần **fixed rate**, **fixed delay** hay **cron**; viết rõ ý nghĩa nghiệp vụ.
- [ ] Đặt `Duration` dương và thời gian bắt đầu chính xác; đừng nhầm “run immediately” với “initial delay”.
- [ ] Cấu hình bean `TaskScheduler`/`ThreadPoolTaskScheduler` dùng chung; chọn `poolSize` dựa trên số job độc lập và blocking duration.
- [ ] Không đăng ký trùng một job (do start callback chạy hai lần, nhiều configuration hoặc cùng lúc có `@Scheduled`).
- [ ] Giữ `ScheduledFuture<?>` nếu cần hủy/start/stop động; cancel có chủ đích khi shutdown.
- [ ] Phân biệt `cancel(false)` với interrupt best-effort ở `cancel(true)`; có cleanup và timeout dependency.
- [ ] Hiểu error handler của **implementation thực tế**, không giả định một exception luôn dừng hoặc luôn giữ lịch.
- [ ] Không coi tick count là success count; log/metric kết quả nghiệp vụ theo mỗi run.
- [ ] Giám sát **start lag**, duration, pool utilization và số lượt bị skip do bận.
- [ ] Nếu Runnable dispatch async work, giới hạn số job đang xử lý và queue; chặn overlap ở tầng nghiệp vụ.
- [ ] Nếu ứng dụng có nhiều replica, dùng distributed lock/lease/leader hoặc idempotency khi cần xử lý duy nhất.
- [ ] Với công việc cần khôi phục sau restart, dùng persistent job store/message queue; fixed rate chỉ đánh thức consumer.
- [ ] Với lịch theo giờ địa phương/thứ/ngày, dùng cron/trigger với timezone chính xác thay vì cộng Duration mù quáng.
- [ ] Không dựa vào request MDC/transaction/security context trong scheduler worker; tạo run ID/context phù hợp và cleanup.
- [ ] Viết test có latch/timeout tổng; tránh kiểm tra mốc tuyệt đối hoặc `Thread.sleep` để suy ra thứ tự.

## 18. Câu hỏi tự kiểm tra

**Câu 1.** `scheduleAtFixedRate(task, Duration.ofSeconds(5))` có chờ 5 giây trước lần đầu không?

**Đáp án:** Không mặc định. Overload này đăng ký task bắt đầu sớm nhất có thể; dùng overload `Instant startTime` nếu muốn chỉ rõ lần đầu sau 5 giây.

**Câu 2.** Task chạy 8 giây, period 5 giây, `ThreadPoolTaskScheduler` có tạo hai lần thực thi cùng một recurring handle chồng lấp không?

**Đáp án:** Với backend `ScheduledThreadPoolExecutor` của implementation đó, các lượt liên tiếp của **cùng một periodic task** không overlap; lượt sau bị trễ, có thể được chạy sớm sau lượt trước.

**Câu 3.** Vậy nếu task chỉ gọi `@Async` rồi return sau 1 ms, business job có thể overlap không?

**Đáp án:** Có. Scheduler đã hoàn tất phần submit; business work trong executor khác có thể vẫn chạy khi tick kế tiếp đến. Cần limit concurrency/idempotency nếu không muốn chồng lấp.

**Câu 4.** Fixed-rate period 5 giây và fixed-delay 5 giây khác nhau thế nào khi task mất 3 giây?

**Đáp án:** Fixed rate thường bắt đầu ở các mốc gần 0, 5, 10,... giây; fixed delay bắt đầu các lượt gần 0, 8, 16,... giây vì đợi hoàn tất rồi thêm 5 giây.

**Câu 5.** Với JDK ScheduledThreadPoolExecutor, một periodic Runnable ném RuntimeException không bắt thì sao?

**Đáp án:** Những lượt sau của chuỗi định kỳ bị ngăn lại; periodic future có thể hoàn tất ngoại lệ. Nhưng Spring `ThreadPoolTaskScheduler` có error handler mặc định cho repeating tasks thường suppress/log, nên cần kiểm tra configuration thực tế.

**Câu 6.** `ScheduledFuture.get()` có phải API đúng để chờ một lượt tick thành công không?

**Đáp án:** Không. Future đó đại diện cả chuỗi lịch recurring; không hoàn tất bình thường sau từng tick. Dùng trạng thái/metric/future riêng theo từng run.

**Câu 7.** Muốn chỉ một replica chạy job mỗi 5 giây, có thể chỉ đặt `poolSize=1` không?

**Đáp án:** Không. Pool size 1 chỉ giới hạn worker **trong một scheduler process**. Nhiều replica vẫn có lịch riêng. Cần distributed coordination hoặc idempotency thích hợp.

**Câu 8.** Có cần `@EnableScheduling` để gọi trực tiếp `taskScheduler.scheduleAtFixedRate(...)` không?

**Đáp án:** Không. Annotation này cần cho việc tự phát hiện `@Scheduled`. Với API trực tiếp và scheduler bean có sẵn, code tự đăng ký lịch.

**Câu 9.** Hủy `handle.cancel(false)` có chắc dừng lượt đang chạy ngay không?

**Đáp án:** Không. Nó không yêu cầu interrupt lượt hiện tại; cancellation ngăn lần chạy tiếp theo theo hợp đồng Future. Nếu cần dừng sớm, phải có cơ chế hợp tác với deadline/interrupt.

**Câu 10.** Fixed rate 24 giờ có luôn đồng nghĩa 02:00 mỗi ngày trong mọi timezone không?

**Đáp án:** Không. Duration 24 giờ khác lịch theo giờ địa phương khi có đổi DST/calendar. Dùng cron/trigger với timezone phù hợp cho lịch nghiệp vụ.

## Kết luận

**`TaskScheduler.scheduleAtFixedRate()` lập lịch thực thi `Runnable` theo những mốc cách nhau một `Duration` cố định**. Khi task chạy quá lâu, các lượt sau của cùng periodic registration trong `ThreadPoolTaskScheduler` thường **bắt đầu trễ chứ không overlap**; nếu task chuyển công việc sang executor khác hoặc nhiều replica có lịch độc lập thì business operations vẫn có thể chồng lấp.

- Dùng `scheduleAtFixedRate(task, period)` để bắt đầu sớm nhất có thể; dùng overload `Instant startTime` để đặt thời điểm chạy đầu.
- Phân biệt **fixed rate** (mốc cố định), **fixed delay** (nghỉ sau khi xong), **cron** (theo lịch/calendar).
- Giữ `ScheduledFuture` để hủy; không dùng `get()` như kết quả một tick.
- Kiểm soát exception handler, worker capacity, blocking duration, retry/idempotency và shutdown.
- Với hệ thống phân tán hoặc công việc quan trọng, lịch định kỳ chỉ là **cơ chế kích hoạt**; cần một nguồn sự thật và bảo đảm xử lý ở tầng nghiệp vụ.

## Tài liệu tham khảo

1. Spring Framework Reference — Task Execution and Scheduling / TaskScheduler: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
2. Spring Framework 6.2 Javadoc — TaskScheduler: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/TaskScheduler.html
3. Spring Framework Javadoc — ThreadPoolTaskScheduler: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html
4. Spring Framework Javadoc — TaskUtils error handling: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/support/TaskUtils.html
5. Java SE 17 — ScheduledExecutorService.scheduleAtFixedRate: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html
6. Java SE 17 — ScheduledThreadPoolExecutor, no-overlap và remove-on-cancel: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html
7. Java SE 17 — ScheduledFuture / cancellation: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html
8. Spring Framework Javadoc — @Scheduled: https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-scheduler-004">scheduleWithFixedDelay() và khoảng nghỉ sau khi hoàn tất</a>

<details>
<summary>Xem chi tiết</summary>

# scheduleWithFixedDelay() và khoảng nghỉ sau khi hoàn tất

## 1. Mục tiêu và tình huống thực tế

Một ứng dụng Spring định kỳ đồng bộ tồn kho từ đối tác. Một lượt xử lý có khi mất 2 giây, có khi mất 15 giây. Yêu cầu nghiệp vụ là: **sau khi một lượt thực sự hoàn tất, nghỉ thêm 5 giây rồi mới bắt đầu lượt kế tiếp**. Nếu đối tác đang chậm, không muốn tạo một loạt lượt mới bám đuổi lịch cũ.

Đó là ý nghĩa của `TaskScheduler.scheduleWithFixedDelay(...)` trong Spring Framework: thời gian `delay` được tính **từ khi lượt thực thi trước kết thúc đến khi lượt tiếp theo được phép bắt đầu**. Nó không phải chu kỳ giữa hai thời điểm bắt đầu, không phải thời gian ngủ nằm bên trong task, cũng không phải cơ chế tự động tạo luồng nền vô hạn.

~~~text
fixed delay = 5 giây

Lượt 1:  START 10:00:00 ---- xử lý 7 s ---- END 10:00:07
                                                     |
                                                     +-- nghỉ ít nhất 5 s
Lượt 2:                                     START 10:00:12 ---- xử lý 2 s ---- END 10:00:14
                                                                                         |
                                                                                         +-- nghỉ ít nhất 5 s
Lượt 3:                                                                         START 10:00:19 ...

Công thức: start(n+1) >= finish(n) + delay
Thời điểm thực tế còn phụ thuộc tài nguyên scheduler, độ trễ hệ điều hành và tải.
~~~

Đừng hiểu `delay=5s` là “cứ 5 giây một lần”. Với lượt 1 chạy 7 giây, **khoảng giữa hai lần bắt đầu là ít nhất 12 giây**, không phải 5 giây. Nếu lượt 2 chạy 2 giây, khoảng bắt đầu từ lượt 2 đến lượt 3 là ít nhất 7 giây.

Mục tiêu sau bài học:

1. Dùng đúng hai overload `scheduleWithFixedDelay(Runnable, Duration)` và `scheduleWithFixedDelay(Runnable, Instant, Duration)`.
2. Phân biệt **fixed delay**, **fixed rate**, **cron**, `initialDelay` và lịch chạy một lần.
3. Hiểu vì sao một **chuỗi fixed-delay đơn lẻ không chồng lấp chính nó**, nhưng các job khác, annotation trùng và nhiều pod vẫn có thể chạy đồng thời.
4. Biết kiểm soát `ScheduledFuture`, cancel, lỗi nghiệp vụ, timeout, shutdown, pool size và observability.
5. Viết được chương trình Java 17 và ứng dụng Spring Boot minh họa thời điểm kết thúc rồi mới nghỉ.

Phiên bản minh họa: **Java 17, Spring Framework 6.x, Spring Boot 3.3.5**, dùng platform threads và `ThreadPoolTaskScheduler` thông thường. Chi tiết đặc biệt của Java 21 virtual threads được nêu riêng.

## 2. API TaskScheduler trong Spring Framework

`org.springframework.scheduling.TaskScheduler` là abstraction lập lịch của Spring. Nó tách code nghiệp vụ khỏi việc tạo và quản lý `ScheduledExecutorService`/worker threads. Những phương thức quan trọng:

~~~java
// Chữ ký rút gọn của TaskScheduler trong Spring Framework 6.x:
ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Duration delay);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, Duration delay);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Duration period);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, Duration period);

ScheduledFuture<?> schedule(
        Runnable task, Instant startTime);
~~~

Đây là **trích đoạn chữ ký API**, không phải class Java độc lập. Tham số `task` là `Runnable`; `Duration` thuộc `java.time`, `Instant` là mốc thời gian tuyệt đối; `ScheduledFuture<?>` thuộc `java.util.concurrent`.

### 2.1. Bắt đầu càng sớm càng tốt

~~~java
// Snippet trong service đã inject một TaskScheduler.
ScheduledFuture<?> handle = scheduler.scheduleWithFixedDelay(
        () -> refreshInventory(),
        Duration.ofSeconds(5));
~~~

- Lượt đầu **được phép chạy càng sớm càng tốt** khi scheduler có worker, không đợi 5 giây bắt buộc.
- Sau khi `refreshInventory()` kết thúc, scheduler mới tính khoảng nghỉ 5 giây tới lượt sau.
- Nếu scheduler quá tải, không có worker rảnh hoặc đang pause, giờ thực tế có thể muộn hơn.
- `handle` cho phép hủy chuỗi lặp; nó **không chứa kết quả mỗi lượt**.

`refreshInventory` và `scheduler` trong snippet là thành phần ứng dụng giả định. Không có annotation `@EnableScheduling` cũng có thể dùng trực tiếp API `TaskScheduler` miễn là có một scheduler đã khởi tạo/được Spring quản lý.

### 2.2. Chỉ định thời điểm lượt đầu

~~~java
// Snippet trong service đã có TaskScheduler.
Instant firstRun = scheduler.getClock().instant()
        .plus(Duration.ofSeconds(10));

ScheduledFuture<?> handle = scheduler.scheduleWithFixedDelay(
        () -> refreshInventory(),
        firstRun,
        Duration.ofSeconds(5));
~~~

Overload thứ hai dùng `startTime` cho **lần thực thi đầu tiên**, sau đó vẫn đợi 5 giây **từ lúc mỗi lượt hoàn tất**. Nếu `startTime` đã ở quá khứ, task được phép chạy ngay khi có thể. `scheduler.getClock()` giúp thống nhất nguồn thời gian của scheduler; trong ví dụ đơn giản dùng `Instant.now()` cũng được nếu đồng hồ hệ thống tương ứng.

**Không nhầm** `startTime` với một khoảng nghỉ sau mỗi lần chạy. Nếu muốn “đợi 10 giây trước lần đầu, rồi 5 giây sau mỗi lần kết thúc”, đây là cách trực tiếp.

### 2.3. Vì sao dùng Duration thay vì long?

Các overload kiểu `long` theo milliseconds từng tồn tại nhưng đã bị deprecate từ Spring Framework 6.0 để ưu tiên `Duration`. Viết `Duration.ofSeconds(5)` tránh nhầm `5000` là millisecond hay second. Trong ứng dụng có configuration properties, parse chúng thành `Duration` rõ ràng để kiểm tra và tránh đơn vị mơ hồ.

## 3. Fixed delay so với fixed rate và cron

| Cơ chế | Mốc tính lần kế tiếp | Phù hợp khi |
| --- | --- | --- |
| `scheduleWithFixedDelay(task, delay)` | **Kết thúc** lượt n + delay | Poll API/DB, đồng bộ định kỳ, muốn nghỉ sau khi xử lý |
| `scheduleAtFixedRate(task, period)` | **Bắt đầu dự kiến** lượt n + period | Lấy mẫu/heartbeat theo nhịp gần cố định |
| `schedule(task, instant)` | Một thời điểm chỉ định | Chạy một lần, không lặp |
| `@Scheduled(cron = "...")` | Mốc lịch tường minh (giờ/ngày, timezone) | Báo cáo hàng ngày, công việc theo giờ |
| `@Scheduled(fixedDelay = ...)` | Kết thúc invocation trước + delay | Cùng semantics fixed delay trong annotation |

Ví dụ `period/delay=5s`, lượt đầu xử lý 8s:

~~~text
FIXED DELAY:
  start 00:00 -> end 00:08 -> nghỉ 5s -> start ~00:13 -> ...

FIXED RATE trên ScheduledThreadPoolExecutor:
  kế hoạch starts: 00:00, 00:05, 00:10, 00:15...
  lượt đầu kéo dài 8s; lượt sau đến hạn có thể chạy ngay khi lượt đầu xong.
  Các invocation kế tiếp CỦA CÙNG MỘT periodic task không tự chồng lên nhau
  trong ScheduledThreadPoolExecutor, nhưng có thể xảy ra các lượt sát nhau.

CRON:
  ví dụ 02:00 mỗi ngày là mốc wall-clock; chịu ảnh hưởng timezone/DST.
~~~

**Fixed rate không tự có nghĩa “chạy song song một task trên nhiều worker”.** Với JDK `ScheduledThreadPoolExecutor`, các lần chạy liên tiếp của **cùng một periodic task** không overlap, kể cả khi pool có nhiều worker; nhưng nếu chạy lâu hơn period, có thể không còn khoảng nghỉ sau khi hoàn thành. Các scheduled task **khác nhau** có thể cạnh tranh hoặc đồng thời tùy số worker.

**Fixed delay không bù lịch chạy lỡ.** Khi một lượt mất 30 phút, scheduler không tạo 360 lượt để bù 5 giây đã trôi qua. Nó chờ lượt hiện tại kết thúc rồi mới đếm delay để chạy lượt tiếp theo. Đây là đặc tính có ích cho polling tránh dồn tải, nhưng không phù hợp nếu doanh nghiệp cần xử lý đúng **mỗi mốc thời gian lịch sử**. Trường hợp đó cần job ledger/cursor dữ liệu hoặc scheduler nghiệp vụ có khả năng catch-up.

### 3.1. Fixed delay không phải debounce/throttle

- **Debounce:** sau mỗi sự kiện mới, thường dời thời điểm xử lý, hay chỉ xử lý khi không còn event trong một khoảng.
- **Throttle/rate limit:** giới hạn số lần thực thi/số request trong một cửa sổ, có thể chia sẻ giữa nhiều node.
- **Fixed delay:** một chuỗi periodic task; sau khi lượt hiện tại kết thúc mới đếm thời gian nghỉ tới lượt sau.

Nếu server nhận 10.000 event/s, `scheduleWithFixedDelay` không tự giải quyết event ingestion hay backpressure. Nó chỉ quy định thời điểm **bắt đầu** từng lượt của một job định kỳ.

## 4. “Hoàn tất” chính xác là hoàn tất điều gì?

Trong `TaskScheduler` với `Runnable` đồng bộ, “hoàn tất” có nghĩa là **`Runnable.run()` đã trả về hoặc thoát vì exception đã được xử lý theo chính sách scheduler**. Đây **không mặc nhiên** là công việc business thực tế đã xong nếu `run()` chỉ dispatch một việc khác.

### 4.1. Sai lầm: Runnable chỉ submit rồi kết thúc

~~~java
// Anti-pattern: snippet; asyncWorker là service của ứng dụng.
scheduler.scheduleWithFixedDelay(() -> {
    asyncWorker.generateReport(); // Gửi sang executor khác rồi trả về rất nhanh.
}, Duration.ofSeconds(5));
~~~

Nếu `generateReport()` có `@Async` và caller đi qua Spring proxy, `Runnable` chỉ **gửi công việc**. “Lượt hoàn tất” của scheduler tính theo thời điểm **lời gọi gửi task** trả về, không đợi report thực tế xử lý xong. Vì vậy có thể có nhiều report còn đang chạy đồng thời dù scheduler đang dùng fixed delay.

Điều tương tự xảy ra khi:

~~~java
// Snippet: fire-and-forget bên trong một Runnable.
CompletableFuture.runAsync(() -> generateReport(), otherExecutor);
// Runnable gọi runAsync rồi trả về, không chờ completion.
~~~

Giải pháp phải theo **contract nghiệp vụ**: nếu cần các lượt **không overlap** theo hoàn tất thực tế, hãy để phần công việc chính nằm trong `Runnable` đồng bộ, hoặc quản lý một workflow có trạng thái `RUNNING` và không cho dispatch lượt mới khi lượt trước chưa terminal. Có thể `join/get` một Future trong Runnable để đợi, nhưng sẽ **chặn scheduler worker**; cần timeout, interruption, không dùng chính pool bị chặn để chạy những child task đang được đợi, nếu không có nguy cơ starvation/deadlock.

### 4.2. Fixed delay không tự đảm bảo exactly-once

Nếu deployment có 3 pod Spring, mỗi pod có thể đăng ký job fixed-delay riêng và chạy cùng logic trên mỗi JVM. Về mặt scheduler, **mỗi pod có một chuỗi không overlap**; toàn cụm vẫn có thể có 3 lượt xử lý đồng thời. Nếu job phải chạy đúng một instance, dùng distributed lock/lease, DB claim, message broker hoặc scheduler trung tâm, kết hợp idempotency.

Ngay trong một JVM, hai `@Scheduled` annotations trên cùng method (repeatable annotation), hai `TaskScheduler.scheduleWithFixedDelay` calls cho cùng Runnable hoặc hai context/bean instance đăng ký trùng có thể tạo **hai chuỗi độc lập**. “Không overlap” chỉ áp dụng cho **một handle của một periodic scheduling registration**, không phải mọi lời gọi tới cùng method từ bất kỳ đường nào.

## 5. Demo Java 17 độc lập: chứng minh nghỉ sau khi kết thúc

Ta dùng một worker cố định và `CountDownLatch` để giữ **lượt 1 đang chạy**, sau đó đo một khoảng tối thiểu từ lúc lượt 1 kết thúc tới lúc lượt 2 bắt đầu. Không dùng `Thread.sleep` làm bằng chứng và không dựa vào thời điểm ngẫu nhiên của hệ điều hành.

Lưu file **`FixedDelayDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class FixedDelayDemo {
    private static void await(CountDownLatch latch, String name)
            throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        AtomicInteger executions = new AtomicInteger();
        AtomicLong firstFinished = new AtomicLong();
        AtomicLong secondStarted = new AtomicLong();

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStartedSignal = new CountDownLatch(1);

        ScheduledFuture<?> handle = scheduler.scheduleWithFixedDelay(
                () -> {
                    int attempt = executions.incrementAndGet();
                    if (attempt == 1) {
                        firstStarted.countDown();
                        try {
                            releaseFirst.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        } finally {
                            firstFinished.set(System.nanoTime());
                        }
                    } else if (attempt == 2) {
                        secondStarted.set(System.nanoTime());
                        secondStartedSignal.countDown();
                    }
                },
                0, 150, TimeUnit.MILLISECONDS);

        try {
            await(firstStarted, "first start");

            // Khi lượt 1 chưa kết thúc, lượt 2 chưa thể bắt đầu.
            System.out.println("SECOND_DURING_FIRST="
                    + (secondStartedSignal.getCount() == 0));

            releaseFirst.countDown();
            await(secondStartedSignal, "second start");

            System.out.println("SECOND_AFTER_FIRST_END="
                    + (secondStarted.get() > firstFinished.get()));

            long gapNanos = secondStarted.get() - firstFinished.get();
            System.out.println("GAP_AT_LEAST_100_MS="
                    + (gapNanos >= TimeUnit.MILLISECONDS.toNanos(100)));

            System.out.println("TWO_OR_MORE_RUNS="
                    + (executions.get() >= 2));
        } finally {
            releaseFirst.countDown();
            handle.cancel(false);
            scheduler.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Scheduler did not stop");
            }
        }

        System.out.println("CANCELLED=" + handle.isCancelled());
    }
}
~~~

Chạy với JDK 17+:

~~~bash
javac --release 17 FixedDelayDemo.java
java FixedDelayDemo
~~~

Đầu ra các dòng do ứng dụng in:

~~~text
SECOND_DURING_FIRST=false
SECOND_AFTER_FIRST_END=true
GAP_AT_LEAST_100_MS=true
TWO_OR_MORE_RUNS=true
CANCELLED=true
~~~

**Vì sao kết quả xác định?**

- `firstStarted` xác nhận scheduler đã vào lượt 1 trước khi kiểm tra; `releaseFirst` còn đóng nên lượt 1 vẫn chưa kết thúc. Một recurring fixed-delay task chưa thể chạy lượt 2 khi lượt 1 đang xử lý.
- `firstFinished` được ghi ngay trước khi `run()` trả về. Scheduler bắt đầu đếm delay 150 ms **sau khi** task thực sự kết thúc, nên `gapNanos` đo được ít nhất khoảng delay; assertion chỉ dùng ngưỡng bảo thủ 100 ms để tránh khẳng định số đo chính xác ở mức từng millisecond.
- `secondStartedSignal` đánh dấu lượt 2 đã vào method; phép kiểm `TWO_OR_MORE_RUNS` không dựa vào việc lượt 3 có xảy ra hay chưa.
- `cancel(false)` hủy những lần kích hoạt kế tiếp của periodic task. Nó không có nghĩa “mọi tác vụ đã gửi tới DB đều rollback”, và `shutdownNow()` chỉ yêu cầu ngắt các task còn đang chạy.

Demo dùng JDK `ScheduledExecutorService` để thể hiện cùng nguyên lý nền của `ThreadPoolTaskScheduler`; phần Spring chạy được ở mục kế tiếp. Nếu JDK bị thiếu tài nguyên nghiêm trọng khiến các latch timeout, chương trình fail rõ ràng thay vì treo vô hạn.

## 6. Demo Spring Boot: scheduleWithFixedDelay(Runnable, Duration)

Dự án này sử dụng **Java 17, Maven, Spring Boot 3.3.5**. Nó đăng ký `ThreadPoolTaskScheduler` bean, gọi API `TaskScheduler.scheduleWithFixedDelay`, chứng minh khoảng nghỉ bắt đầu **sau lượt 1**. `ApplicationRunner` cố ý chờ bằng latch để in kết quả kiểm chứng rồi hủy recurring schedule; không cần web server, DB hay file ngoài những file liệt kê.

### 6.1. Cấu trúc

~~~text
spring-fixed-delay/
├── pom.xml
└── src/main/java/com/example/scheduler/
    └── FixedDelayApplication.java
~~~

### 6.2. pom.xml

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
    <artifactId>spring-fixed-delay</artifactId>
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

### 6.3. FixedDelayApplication.java

~~~java
package com.example.scheduler;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
public class FixedDelayApplication {
    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                SpringApplication.run(FixedDelayApplication.class, args)) {
            // Sau runner, đóng context và hủy các tài nguyên đã đăng ký.
        }
    }

    @Bean("demoScheduler")
    ThreadPoolTaskScheduler demoScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("fixed-delay-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    @Bean
    ApplicationRunner verifyFixedDelay(
            @Qualifier("demoScheduler") TaskScheduler scheduler) {
        return args -> {
            AtomicInteger count = new AtomicInteger();
            AtomicLong finishedFirst = new AtomicLong();
            AtomicLong beganSecond = new AtomicLong();

            CountDownLatch firstBegan = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch secondBegan = new CountDownLatch(1);

            Runnable task = () -> {
                int n = count.incrementAndGet();
                if (n == 1) {
                    firstBegan.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finishedFirst.set(System.nanoTime());
                    }
                } else if (n == 2) {
                    beganSecond.set(System.nanoTime());
                    secondBegan.countDown();
                }
            };

            ScheduledFuture<?> handle = scheduler.scheduleWithFixedDelay(
                    task, Duration.ofMillis(150));

            try {
                if (!firstBegan.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("First run did not start");
                }
                System.out.println("SECOND_WHILE_FIRST_RUNNING="
                        + (secondBegan.getCount() == 0));

                release.countDown();
                if (!secondBegan.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Second run did not start");
                }

                System.out.println("SECOND_AFTER_FIRST_END="
                        + (beganSecond.get() > finishedFirst.get()));
                System.out.println("PAUSE_AT_LEAST_100_MS="
                        + (beganSecond.get() - finishedFirst.get()
                                >= TimeUnit.MILLISECONDS.toNanos(100)));
                System.out.println("RUNS_AT_LEAST_2=" + (count.get() >= 2));
            } finally {
                release.countDown();
                handle.cancel(false);
            }

            System.out.println("HANDLE_CANCELLED=" + handle.isCancelled());
        };
    }
}
~~~

**Ý nghĩa của mã:**

- Bean `ThreadPoolTaskScheduler` được container khởi tạo, quản lý lifecycle và đóng khi context đóng. `setPoolSize(1)` chỉ phục vụ demo tái hiện dễ đọc; production phải chọn theo số task, thời gian xử lý và SLO.
- Không dùng `@EnableScheduling` vì ví dụ gọi **trực tiếp API TaskScheduler**. Annotation `@EnableScheduling` chỉ cần cho cơ chế tìm và đăng ký `@Scheduled` methods.
- `ApplicationRunner` giữ một `ScheduledFuture<?>` để hủy schedule sau khi quan sát đủ hai lần. Nếu không hủy, periodic task có thể tiếp tục chạy cho tới khi scheduler shutdown.
- Các `CountDownLatch` kiểm tra quan hệ giữa lượt đang chạy và lượt kế tiếp, không phụ thuộc `Thread.sleep` hay thứ tự log của nhiều workers.
- `Duration.ofMillis(150)` là thời gian nghỉ **sau** khi lượt trước hoàn tất, không phải chu kỳ giữa hai lần gọi.

### 6.4. Chạy và kết quả

Ở thư mục chứa `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua log Boot/Maven, chương trình in:

~~~text
SECOND_WHILE_FIRST_RUNNING=false
SECOND_AFTER_FIRST_END=true
PAUSE_AT_LEAST_100_MS=true
RUNS_AT_LEAST_2=true
HANDLE_CANCELLED=true
~~~

Các dòng này đánh giá tính chất hành vi; không in timestamp tuyệt đối vì timestamp phụ thuộc môi trường. Nếu môi trường scheduler chậm, khoảng nghỉ thực tế **dài hơn** cấu hình vẫn phù hợp semantics fixed delay. Phép đo dùng ngưỡng 100 ms cho delay 150 ms, thay vì khẳng định scheduler luôn chính xác tới từng nanosecond.

## 7. Dùng @Scheduled(fixedDelay) thay cho API trực tiếp

Nếu chỉ cần định kỳ gọi một method đã biết từ lúc ứng dụng khởi động, `@Scheduled` ngắn gọn hơn. Ví dụ trong một ứng dụng Spring Boot đã có `@EnableScheduling`:

~~~java
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InventoryPoller {
    @Scheduled(
            initialDelay = 2,
            fixedDelay = 5,
            timeUnit = TimeUnit.SECONDS)
    public void poll() {
        // Thực hiện đồng bộ một lượt; trả về khi lượt kết thúc.
        syncInventory();
    }

    private void syncInventory() {
        // Code nghiệp vụ phải có timeout/observability.
    }
}
~~~

Trong một class `@Configuration`:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
~~~

Đây là **hai class minh họa có thể đặt vào cùng dự án Spring**, không phải phần của demo chỉ gọi API ở mục 6. Với annotation trên:

- Lần đầu được phép chạy sau `initialDelay=2s` tính từ khi task được đăng ký/chạy scheduler theo lifecycle; không phải “2 giây sau HTTP request đầu tiên”.
- Sau khi `poll()` kết thúc, scheduler đợi `fixedDelay=5s` trước lần kế tiếp. Nếu `poll()` mất 8s, hai lần bắt đầu cách nhau ít nhất khoảng 13s.
- `timeUnit = TimeUnit.SECONDS` làm đơn vị số `2`/`5` rõ ràng. Nếu bỏ thuộc tính này, mặc định của `@Scheduled` là milliseconds.
- Method scheduled thông thường không nhận tham số; tránh block vô hạn, có timeout network/DB và ghi lại tình trạng lỗi.

Có thể dùng `fixedDelayString = "PT5S"` hoặc một property đã được Spring resolve để cấu hình từ môi trường. Khi dùng property bên ngoài, kiểm tra đơn vị, giá trị hợp lệ và profile đang chạy, vì một giá trị âm/không hợp lệ có thể khiến đăng ký lịch thất bại. Nếu thay delay khi ứng dụng đã chạy, không được mặc nhiên cho rằng annotation đã đăng ký tự reschedule ngay; tùy trường hợp cần đăng ký lại hoặc thiết kế trigger động.

### 7.1. Annotation không đồng nghĩa proxy của @Async

`@Scheduled` được Spring scheduling infrastructure phát hiện và đăng ký, trong khi `@Async` thường cần Spring proxy/interceptor. Đừng lấy quy tắc self-invocation của `@Async` rồi khẳng định rằng `@Scheduled` chỉ chạy khi có request đi qua proxy. Scheduler **tự kích hoạt** method theo lịch đã đăng ký. Ngược lại, chỉ khai báo `@Scheduled` mà không bật scheduling infrastructure trong context thì method có thể không được đăng ký.

Nếu một method vừa mang `@Scheduled` vừa mang `@Async`, hoặc method scheduled gọi sang một bean async khác, cần xác minh điểm nào được coi là “kết thúc”. Việc phương thức trả về ngay sau dispatch có thể làm fixed delay chỉ áp dụng cho **gửi việc** chứ không chờ task business đã kết thúc.

### 7.2. Đừng nhầm scheduler bean và executor bean

`spring.task.execution.*` cấu hình async task execution của Spring Boot khi auto-configuration áp dụng; `spring.task.scheduling.*` dành cho scheduler. Một `ThreadPoolTaskExecutor` cho `@Async` không tự biến thành `TaskScheduler` của `@Scheduled`. Khi viết hai lớp công việc, hãy đặt tên bean rõ và cấu hình đúng loại.

Ví dụ Spring Boot khi auto-config scheduler trong môi trường đủ điều kiện:

~~~properties
# Minh họa override do ứng dụng cung cấp; KHÔNG phải default:
spring.task.scheduling.pool.size=3
spring.task.scheduling.thread-name-prefix=poll-
~~~

Nếu ứng dụng đã tự tạo `@Bean ThreadPoolTaskScheduler` bằng setter như mục 6, **đừng** kỳ vọng Boot properties nhất thiết ghi đè số pool do code đã đặt. Hãy kiểm tra bean thực và resolved configuration.

## 8. Có thật sự không overlap?

### 8.1. Trong cùng một periodic registration

Đối với `ThreadPoolTaskScheduler` thông thường, backend là JDK `ScheduledExecutorService/ScheduledThreadPoolExecutor`. Các lần kích hoạt của **một** task được đăng ký qua `scheduleWithFixedDelay` không overlap: lượt kế tiếp chỉ trở nên eligible sau khi lượt trước kết thúc và hết delay. Tăng `poolSize` lên 10 không biến cùng một schedule thành 10 invocation song song.

### 8.2. Giữa các scheduled jobs khác nhau

Hai job `refreshProductCatalog` và `refreshSupplierCatalog` có thể chạy đồng thời nếu scheduler có nhiều worker, vì đó là **hai registrations riêng**. Nếu pool size=1, một job dài sẽ làm job kia chậm dù cả hai có delay riêng. Vì thế fixed delay là một nguyên tắc thời gian *mỗi schedule*, không phải mutex khóa toàn scheduler.

### 8.3. Khi code tách tác vụ qua async executor

Nếu `Runnable.run()` chỉ gửi một Future sang executor khác rồi trả về, lời gọi lịch có thể hoàn thành trong vài millisecond. Business task có thể vẫn chạy khi đến lượt dispatch sau; fixed delay không tự ngăn overlap của **các Future con**.

### 8.4. Giữa các application instances

Mỗi pod/JVM có một scheduler độc lập. Khi chạy nhiều pod, mỗi pod có thể bắt đầu job riêng và tranh cập nhật DB. Nếu chỉ được phép một job toàn cụm, dùng distributed lock/lease, atomic DB claim hoặc message broker. Cần idempotency để xử lý restart/retry, không chỉ dựa vào cron/fixed delay và pool size.

### 8.5. Với virtual threads và SimpleAsyncTaskScheduler

Spring Framework 6.1+ có `SimpleAsyncTaskScheduler` phù hợp định hướng virtual threads. Đặc biệt, fixed-delay tasks của implementation này chạy trên **một scheduler thread**, thay vì mỗi lượt đều được offload ra thread mới như fixed rate/cron. Tài liệu Spring khuyến nghị cân nhắc **fixed rate hoặc cron** với virtual-thread-aligned scheduler, nhất là khi công việc có thể block. Không mang máy móc các giả định về `ThreadPoolTaskScheduler` sang implementation khác chỉ vì cùng interface `TaskScheduler`.

## 9. Exception: phân biệt JDK raw scheduler với Spring wrapper

Đây là một trong những lỗi chẩn đoán quan trọng nhất.

### 9.1. Java ScheduledThreadPoolExecutor thuần

Theo JDK `ScheduledExecutorService.scheduleWithFixedDelay`, nếu một lần chạy của periodic command ném unchecked exception mà không được xử lý, **những lần chạy tiếp theo bị suppressed**. `ScheduledFuture` của chuỗi có thể chuyển sang exceptional completion; gọi `get()` nhận `ExecutionException`. Với chuỗi chạy bình thường chưa dừng, `ScheduledFuture.get()` **không trả kết quả cho từng lượt**, vì periodic task về bản chất chưa hoàn tất.

~~~java
// Snippet JDK, giả định rawExecutor là ScheduledExecutorService.
ScheduledFuture<?> handle = rawExecutor.scheduleWithFixedDelay(
        () -> {
            throw new IllegalStateException("failed");
        },
        0, 5, TimeUnit.SECONDS);

// Sau khi lượt đầu đã lỗi, get() có thể trả ExecutionException
// và lịch không tự khởi động lại.
~~~

### 9.2. Spring ThreadPoolTaskScheduler thông thường

Spring `ThreadPoolTaskScheduler` trang trí repeating `Runnable` bằng xử lý lỗi của Spring. Với error handler mặc định cho **repeating tasks**, Spring thường **ghi log rồi suppress exception**, cho phép các lần tiếp theo vẫn chạy. Vì vậy không được bê nguyên quy tắc “một exception sẽ dừng lịch” của raw JDK sang mọi ứng dụng Spring.

Spring `TaskUtils` có hai chiến lược minh họa:

- `LOG_AND_SUPPRESS_ERROR_HANDLER`: ghi lỗi, không rethrow; các lượt định kỳ có thể tiếp tục.
- `LOG_AND_PROPAGATE_ERROR_HANDLER`: ghi lỗi rồi rethrow; điều đó thường ngăn các lần sau trên scheduler JDK.

Nếu ứng dụng cung cấp `setErrorHandler(...)`, kết quả còn tùy handler có ném tiếp hay không.

~~~java
// Snippet trong @Configuration: chủ động log + tiếp tục.
ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setPoolSize(2);
scheduler.setErrorHandler(error -> {
    log.error("Scheduled iteration failed", error);
    failedRunsCounter.increment();
    // Không throw: iteration thất bại được ghi nhận, lịch tiếp tục.
});
~~~

`log` và `failedRunsCounter` là thành phần nghiệp vụ giả định; snippet không phải chương trình độc lập. Nếu lỗi mang ý nghĩa phải dừng job, chọn chính sách dừng rõ ràng và ghi trạng thái tại một job store; không đặt kỳ vọng vào một dòng log.

Một task “hoàn tất vì exception được wrapper xử lý” vẫn có thể khiến scheduler tính tiếp delay; **hoàn tất lịch chạy không đồng nghĩa nghiệp vụ thành công**. Theo dõi số lượt `FAILED`/`SUCCEEDED` riêng.

### 9.3. Dùng Future đúng cách

- `scheduleWithFixedDelay` trả `ScheduledFuture<?>` để **cancel/check status**, không để `get()` nhận kết quả của từng lượt.
- `handle.get()` khi lịch bình thường còn hoạt động có thể chờ vô hạn; tránh gọi ngay trên HTTP/startup thread để “đợi lượt đầu xong”.
- Muốn theo dõi lượt đầu, dùng callback/metric/job state, latch trong test, hoặc một Future kết quả do ứng dụng tự quản có deadline.
- `handle.isDone()` có thể chỉ rằng lịch đã bị cancel/terminated, **không** biểu thị “lượt gần nhất đã thành công”.
- Khi task bị từ chối đăng ký vì scheduler shutdown/overload, Spring có thể ném `TaskRejectedException` **ngay lúc gọi schedule**, trước khi trả handle.

## 10. Hủy, dừng ứng dụng và ScheduledFuture

### 10.1. Hủy lịch lặp

~~~java
ScheduledFuture<?> handle = scheduler.scheduleWithFixedDelay(
        this::runCleanup, Duration.ofMinutes(2));

// Ở nơi quản lý vòng đời của job:
boolean cancelled = handle.cancel(false);
~~~

`cancel(false)` chặn các lượt tương lai của chuỗi. Nếu task hiện đang chạy, cancellation không nhất thiết dừng ngay việc xử lý đang diễn ra. `cancel(true)` cho phép yêu cầu interrupt với backend có hỗ trợ, **không bảo đảm** DB transaction rollback, file đang được ghi sẽ biến mất hoặc I/O không hỗ trợ interruption dừng ngay.

Giữ `ScheduledFuture<?>` theo từng job registration; đừng tạo schedule mới mỗi request HTTP rồi vứt mất handle, vì việc không có chỗ hủy/giám sát có thể dẫn đến nhiều periodic jobs cùng chạy.

### 10.2. Shutdown và lifecycle Spring

`ThreadPoolTaskScheduler` là Spring-managed bean có lifecycle initialization/shutdown. Khi context đóng, ứng dụng phải dừng/huỷ lịch đúng chính sách đã chọn. Spring 6.1+ có thêm pause/resume và graceful-shutdown controls; chi tiết tùy version/configuration. Với job có side effects quan trọng, phải kiểm tra policy của task đang chạy, task đang đợi, shutdown timeout, và cách đảm bảo dữ liệu nhất quán sau restart.

Một `ScheduledFuture<?>` không là durable job queue. Nếu pod crash, toàn bộ handle trong JVM mất; muốn biết lần cuối đã xử lý tới đâu phải lưu checkpoint/watermark trong DB, không dựa vào `isDone()`.

### 10.3. Lưu ý thời điểm đăng ký

Một service có thể tự đăng ký schedule trong `@PostConstruct`, nhưng ở pha bean initialization có nguy cơ dependency/proxy chưa hoàn chỉnh hoặc cần lifecycle dừng chủ động. Tùy yêu cầu, dùng cấu hình bean, `ApplicationRunner`, `SmartLifecycle`, sự kiện sau refresh hoặc annotation `@Scheduled` phù hợp; tránh đăng ký hai lần khi context refresh hoặc nhiều instance được tạo.

## 11. Thiết kế job polling production: khoảng nghỉ, timeout, lỗi và dữ liệu

Ví dụ một job lấy hóa đơn mới từ API partner theo cursor. Yêu cầu “nghỉ 5s sau khi xong lượt trước” phù hợp fixed delay, nhưng để vận hành an toàn cần bổ sung:

1. **Điểm bắt đầu/hoàn tất thực:** log `startedAt`, `finishedAt` và `outcome` (success/failure) trong `Runnable` hoặc transaction quản lý job. Không đánh dấu hoàn tất khi chỉ dispatch Future khác.
2. **I/O timeout:** connection/read timeout cho HTTP client, query timeout/transaction timeout cho DB. Nếu `Runnable` treo vô hạn, lượt kế tiếp sẽ không được lên lịch dù đã cấu hình delay 5s.
3. **Checkpoint:** cập nhật cursor/watermark sau khi dữ liệu thực sự được ghi thành công, tránh bỏ sót sau restart hoặc khi có retry.
4. **Idempotency:** nếu request hoặc job bị retry hay nhiều pod chạy đồng thời, xử lý trùng theo key tự nhiên/unique index/upsert có điều kiện.
5. **Backpressure:** giới hạn batch size, số request song song tới partner, throughput DB và timeout; fixed delay chỉ kiểm soát **giữa các lượt**, không giới hạn tài nguyên **bên trong một lượt**.
6. **Lỗi và retry:** phân biệt lỗi tạm thời (429/503, timeout) và lỗi nghiệp vụ (payload sai, authorization). Có thể dùng exponential backoff/jitter và chính sách circuit breaker. Nếu cần interval động theo lỗi, một fixed delay bất biến có thể không đủ.
7. **Shutdown:** lưu trạng thái và hủy schedule; với lượt đang chạy, kết thúc an toàn hoặc cho phép restart phục hồi bằng job ledger.
8. **Giám sát:** metric successful_runs, failed_runs, last_success_timestamp, last_run_duration, delay_from_finish_to_next_start, scheduler_queue_delay, rejected scheduling attempts.
9. **Cluster:** nếu chỉ một worker toàn cụm được chạy, dùng distributed lease/scheduler ngoài JVM, không lấy poolSize=1 của từng pod làm khóa phân tán.

Nếu yêu cầu chính xác theo giờ địa phương (ví dụ 08:00 mỗi ngày), fixed delay không phù hợp vì **drift** theo thời gian xử lý và nghỉ. Khi ấy dùng cron với timezone rõ và quy tắc DST/missed execution riêng. Nếu partner quy định tối đa một request mỗi 5 giây kể từ lúc *trả kết quả*, fixed delay là công cụ gần đúng hữu ích, nhưng còn phải tính tất cả caller/task khác cùng gọi partner.

## 12. Kiểm thử scheduler mà không dựa vào sleep ngẫu nhiên

Một kiểm thử tốt có các tình huống:

| Test | Bằng chứng cần quan sát |
| --- | --- |
| Lượt 1 bị giữ bằng latch | Lượt 2 chưa bắt đầu khi lượt 1 chưa kết thúc |
| Lượt 1 được giải phóng | Lượt 2 chỉ bắt đầu sau khi đã hết delay kể từ kết thúc |
| Lượt 1 xử lý lâu hơn delay | Không có backlog nhiều lượt của cùng registration |
| Hủy `ScheduledFuture` | Không có lượt mới sau khi hủy (xem trạng thái cancellation/race) |
| Task ném exception | Phân biệt Spring default handler tiếp tục và raw JDK suppression |
| Một job A dài + job B khác | PoolSize=1 có thể trì hoãn B; nhiều workers có thể chạy song song |
| Executor shutdown | Đăng ký mới bị từ chối/cancel tùy policy, không chờ Future vô hạn |
| Cùng code trên hai pod | Có thể cùng chạy; cần coordination nếu yêu cầu một job toàn cụm |
| Task chỉ dispatch `@Async` | Fixed delay đo dispatch completion, không phải business completion |
| Virtual-thread-aligned scheduler | Semantics fixed delay cần được kiểm thử trên implementation thực |

Tại sao không nên test kiểu “`Thread.sleep(200)` rồi assert đã chạy đúng 2 lần”? Vì CI chậm, GC pause và hệ điều hành lên lịch không chính xác tuyệt đối. Với code nghiệp vụ, tách phần xử lý vào hàm độc lập để unit test sync; phần scheduling test bằng latch hoặc integration test có thời gian chờ hữu hạn. Đặt tolerance cho thời gian thực tế, không yêu cầu ms chính xác tuyệt đối.

### Test overlap và thứ tự

Một `AtomicInteger active` kết hợp `maxActive.accumulateAndGet(...)` có thể chứng minh cùng registration không có hai invocation đồng thời. Nhưng với fixed delay một worker trong demo, latch ở mục 5 đủ rõ. Nếu mã `Runnable` tách task sang executor khác, active counter phải bọc **công việc business thực tế**, không chỉ wrapper dispatch nhanh rồi trả về.

Khi có nhiều registration, cần test independent jobs và shared resource locks. Fixed delay không tự ngăn hai job **khác nhau** sửa cùng hàng trong DB.

## 13. Các lỗi thường gặp và cách sửa

| Hiểu nhầm / triệu chứng | Nguyên nhân thật | Cách sửa |
| --- | --- | --- |
| “delay=5s thì bắt đầu mỗi 5s” | Fixed delay tính từ **kết thúc** lượt trước | Dùng fixed rate nếu cần nhịp từ thời điểm bắt đầu |
| Lượt đầu chạy ngay, tưởng scheduler bỏ qua 5s | Overload không có `startTime` bắt đầu càng sớm càng tốt | Chỉ định `Instant` first run hoặc `@Scheduled(initialDelay=...)` |
| Lượt tiếp theo chậm hơn 5s | Delay là ngưỡng tối thiểu, không phải SLA start time | Đo worker contention, tải, GC, pause/resume |
| Tăng pool size mà một job vẫn không overlap | Mỗi periodic registration tuần tự theo fixed delay | Điều này đúng contract; dùng design khác nếu muốn parallel batches |
| Lịch vẫn chạy dù task ném exception | Spring default repeating ErrorHandler thường suppress lỗi | Theo dõi failed runs, cấu hình error policy có chủ đích |
| Lịch dừng sau exception trong JDK thuần | JDK periodic scheduling suppress các lượt tiếp theo | Handle lỗi trong task hoặc restart theo chính sách |
| `ScheduledFuture.get()` không trả | Future đại diện **chuỗi định kỳ**, không phải một lượt | Dùng metric/latch/job state, không chờ `get` vô hạn |
| Fixed delay mà report vẫn overlap | Task chỉ submit một `CompletableFuture` rồi trả về | Định nghĩa business-completion boundary, dùng gate/lease |
| API cần chạy đúng 02:00 nhưng bị trôi giờ | Fixed delay cộng cả thời gian xử lý | Dùng cron + timezone / job ledger |
| Có ba pod chạy ba lần | Mỗi pod có scheduler riêng | Distributed coordination, idempotency |
| Một scheduled job dài làm job khác trễ | Scheduler pool quá ít worker | Phân scheduler theo loại job hoặc tăng pool có kiểm soát |
| Đọc Boot execution core-size nhưng scheduler không thay đổi | Executor và scheduler khác abstraction/config | Dùng scheduling properties hoặc explicit scheduler bean |
| Task treo mãi khiến không có lượt mới | Không có timeout ở I/O hoặc task chờ Future vô hạn | Timeouts, deadline, cancellation contract |
| Đổi sang virtual threads thấy fixed-delay job bị nghẽn | `SimpleAsyncTaskScheduler` fixed-delay dùng scheduler thread | Chọn implementation/trigger phù hợp, kiểm thử tải |

## 14. Checklist code review

1. **Yêu cầu thời gian:** hệ thống cần nghỉ sau lúc hoàn tất hay bắt đầu mỗi N giây, hoặc chạy theo mốc giờ?
2. **Implementation:** ứng dụng dùng `TaskScheduler` nào (`ThreadPoolTaskScheduler`, `SimpleAsyncTaskScheduler`, scheduler do container quản lý)?
3. **Overload:** có chọn đúng `Duration` và `Instant startTime` cho lần đầu? Delay lớn hơn 0 và đơn vị rõ ràng?
4. **Completion boundary:** `Runnable` có thực thi đầy đủ nghiệp vụ hay chỉ dispatch qua `@Async` rồi trả về?
5. **Concurrency:** có nhiều registrations, @Scheduled annotation trùng hoặc nhiều pod khiến overlap ngoài ý muốn?
6. **Error handling:** dùng chính sách nào, lỗi được log/metric và có cần dừng lịch không?
7. **Lifecycle:** ai giữ `ScheduledFuture`, ai cancel, shutdown có chờ/hủy task đúng yêu cầu?
8. **Timeout:** network/DB và việc chờ child Future có deadline để tránh một lượt treo vô hạn?
9. **Observability:** có last success, failure rate, duration, scheduling lag và số lần chạy sau restart?
10. **State:** cursor/checkpoint/idempotency được lưu bền vững khi một lượt xử lý có side effect?
11. **Capacity:** pool size và số công việc khác đủ để không bỏ đói các job quan trọng?
12. **Validation:** có test latch chứng minh khoảng nghỉ sau completion, test exception và multi-instance?

## 15. Câu hỏi tự kiểm tra

1. Lượt A bắt đầu 09:00:00, kết thúc 09:00:11. `scheduleWithFixedDelay(..., Duration.ofSeconds(4))` có thể bắt đầu lượt B sớm nhất lúc nào nếu không bị chậm vì tải?
2. Vì sao `scheduleWithFixedDelay(task, Duration.ofSeconds(5))` có thể chạy lượt đầu ngay, còn overload có `Instant` thì chờ mốc giờ chỉ định?
3. Khác biệt giữa fixed delay và fixed rate khi một lượt chạy lâu hơn period?
4. Tăng `ThreadPoolTaskScheduler.poolSize` từ 1 lên 8 có khiến **cùng một periodic task** tự overlap không?
5. Một `@Scheduled(fixedDelay=5s)` gọi `@Async` ở bean khác và trở về ngay. Khoảng nghỉ 5s tính từ lúc nào?
6. Vì sao `handle.get()` trên `ScheduledFuture` định kỳ có thể chờ mãi?
7. Một exception trong raw JDK periodic Runnable so với Spring `ThreadPoolTaskScheduler` mặc định thường ảnh hưởng những lần chạy sau thế nào?
8. Nếu task gọi API ngoài không có read timeout và bị treo, fixed delay có tự khởi động lượt kế tiếp không?
9. Ba pod cùng bật `@Scheduled` fixed delay có tự điều phối để chỉ một pod xử lý không?
10. Khi nào cron phù hợp hơn fixed delay, và vì sao phải lưu cursor/checkpoint riêng?

**Ghi nhớ:** `scheduleWithFixedDelay()` mang nghĩa **“làm xong → nghỉ đủ delay → bắt đầu lượt kế tiếp khi có tài nguyên”**. Thời gian chạy của lượt trước làm thay đổi khoảng cách giữa các lần bắt đầu. Cơ chế này bảo vệ một chuỗi polling khỏi tự chồng lấp và đuổi lịch, nhưng **không** thay thế timeout, error handling, distributed lock, idempotency hay theo dõi kết quả nghiệp vụ.

## Tài liệu chính thức

- [Spring Framework 6.2 — TaskScheduler API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.2 — ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework 6.2 — @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework 6.2 — TaskUtils, xử lý lỗi tác vụ lặp](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/TaskUtils.html)
- [Java SE 17 — ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)
- [Java SE 17 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-scheduler-005">ScheduledFuture để theo dõi và hủy lịch</a>

<details>
<summary>Xem chi tiết</summary>

# ScheduledFuture để theo dõi và hủy lịch

## 1. Vì sao một TaskScheduler cần trả về ScheduledFuture?

Trong Spring Framework, `TaskScheduler` giúp đặt lịch cho một công việc `Runnable`: chạy một lần ở thời điểm xác định, chạy lặp theo chu kỳ cố định, chạy lại sau một khoảng nghỉ tính từ lần trước hoặc chạy theo `Trigger` như cron.

Khi công việc đã được đăng ký, ứng dụng thường cần **theo dõi trạng thái và chủ động hủy lịch**: người dùng tắt một báo cáo định kỳ, cấu hình tần suất thay đổi, tenant ngừng sử dụng dịch vụ, một tài nguyên được giải phóng, hoặc một job đang chờ không còn giá trị.

Các phương thức lập lịch programmatic của `TaskScheduler` thường trả về:

```java
java.util.concurrent.ScheduledFuture<?>
```

Đây là một **handle cho công việc đã lập lịch**. Nó không phải mã định danh job trong database, không phải bản ghi cron bền vững và không tự bảo đảm công việc đã hoàn thành. Nhưng nó cho phép sử dụng những API quan trọng:

- `cancel(boolean mayInterruptIfRunning)`: yêu cầu hủy lịch.
- `isCancelled()`: kiểm tra handle đã ở trạng thái bị hủy hay chưa.
- `isDone()`: kiểm tra một công việc đã kết thúc trạng thái của Future hay chưa.
- `getDelay(TimeUnit unit)`: ước lượng độ trễ còn lại tới lần kích hoạt phù hợp.
- `get()` hoặc `get(timeout, unit)`: chờ một Future hoàn tất; **đặc biệt phải phân biệt tác vụ một lần với tác vụ lặp**.

Ví dụ ý tưởng:

```java
ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(
        this::refreshCache,
        Duration.ofMinutes(5)
);

// Khi không muốn chạy các lượt tiếp theo:
boolean accepted = handle.cancel(false);
```

Đây là **mảnh code trong một class có `scheduler` và `refreshCache()`**, không phải chương trình độc lập. `cancel(false)` không buộc một lượt đã bắt đầu phải dừng ngay; nó thể hiện ý định hủy lịch mà không yêu cầu ngắt thread đang thực thi.

**Mục tiêu bài:** hiểu `ScheduledFuture` gắn với `TaskScheduler` như thế nào, đọc trạng thái chính xác, hủy one-shot/periodic/trigger task, phân biệt `cancel(false)` và `cancel(true)`, quản lý nhiều handle an toàn, xử lý lỗi và vòng đời Spring, kèm ví dụ Java 17 có kết quả kiểm chứng được.

## 2. Sơ đồ quan hệ: TaskScheduler → ScheduledFuture → vòng đời lịch

```text
Application / service
       |
       | scheduler.schedule*(Runnable, ...)
       v
    TaskScheduler
       |
       +--- nhận task, tính thời điểm chạy
       |
       +--- trả ScheduledFuture<?> cho caller
       |                       |
       |                       +-- getDelay(...)
       |                       +-- isDone()
       |                       +-- isCancelled()
       |                       +-- cancel(false/true)
       v
Scheduled executor / Trigger
       |
       +--- chưa đến hạn --> nằm trong cấu trúc chờ
       |
       +--- đến hạn --> worker chạy Runnable
       |                   |
       |                   +--- one-shot: kết thúc Future
       |                   |
       |                   +--- định kỳ: có thể lên lịch lượt tiếp
       |
       +--- cancel / shutdown / terminal failure
```

**Lịch là một đối tượng điều phối**, còn mỗi lần chạy `Runnable` là một lượt thực thi. Một handle của công việc lặp có thể vẫn “chưa xong” dù tác vụ đã chạy thành công hàng trăm lượt, vì lịch tiếp tục tồn tại cho tới khi hủy hoặc kết thúc bất thường.

Trong `ThreadPoolTaskScheduler` truyền thống, Spring sử dụng một `ScheduledThreadPoolExecutor` của Java và các tác vụ được thực thi trên scheduler thread(s). Điều đó khác với mô hình “scheduler chỉ đánh thức một executor phụ” ở các triển khai khác. Vì thế **thời gian tác vụ chiếm worker** ảnh hưởng trực tiếp khả năng thực thi các lịch khác. Không nên mặc định mọi `TaskScheduler` có cùng chiến lược thread hoặc cùng ngữ nghĩa quản lý lỗi.

## 3. ScheduledFuture có những phương thức nào?

`ScheduledFuture<V>` thuộc `java.util.concurrent`, **kế thừa `Delayed` và `Future<V>`**. Vì vậy nó có hai nhóm API.

| API | Nguồn | Ý nghĩa |
|---|---|---|
| `getDelay(TimeUnit)` | `Delayed` | Thời gian ước tính còn lại trước khi nhiệm vụ đủ điều kiện kích hoạt |
| `compareTo(Delayed)` | `Delayed`/`Comparable` | So sánh các trì hoãn theo hợp đồng |
| `cancel(false)` | `Future` | Hủy mà không yêu cầu ngắt một lượt đang chạy |
| `cancel(true)` | `Future` | Hủy và cho phép cố gắng ngắt thread nếu task đang chạy |
| `isCancelled()` | `Future` | Future đã bị hủy hay chưa |
| `isDone()` | `Future` | Future đã kết thúc bình thường, lỗi hoặc bị hủy hay chưa |
| `get()` | `Future` | Chờ kết thúc và nhận kết quả/exception |
| `get(timeout, unit)` | `Future` | Chờ hữu hạn và có thể báo `TimeoutException` |

Những quy tắc rất dễ nhầm:

1. **`isDone() == true` không đồng nghĩa “thành công”**. Nó cũng đúng khi Future bị hủy hoặc hoàn tất ngoại lệ.
2. **`isCancelled() == true` không chứng minh code trong một lượt đang chạy đã ngừng hoàn toàn**. Với `cancel(false)`, lượt đang thực thi có thể tiếp tục đến hết.
3. **`getDelay(...)` không phải timestamp tuyệt đối của lần chạy tiếp theo** và không phải phần trăm tiến độ. Giá trị có thể thay đổi ngay sau khi đọc; nếu thời hạn đã qua có thể bằng không hoặc âm, tùy implementation.
4. **`ScheduledFuture<?>` không mang kết quả nghiệp vụ từ một `Runnable`**. Với tác vụ one-shot `Runnable` hoàn tất bình thường trong Java `ScheduledExecutorService`, `get()` trả `null`; trong Spring `TaskScheduler` cũng không biến nó thành kết quả tính toán riêng.
5. Với **lịch lặp còn sống**, `get()` không phải “đợi một lượt chạy xong”. Nó thường chờ tới lúc toàn bộ chuỗi thực thi kết thúc — có thể là khi hủy hoặc lỗi.

### 3.1. Trạng thái một lượt không giống trạng thái cả lịch

Ví dụ cron chạy lúc 01:00 hằng ngày; hôm nay đã chạy xong. Nếu lịch ngày mai vẫn được duy trì:

```text
Một lần chạy hôm nay: SUCCEEDED
ScheduledFuture của lịch: vẫn ACTIVE / isDone() thường false
Lần kế tiếp: ngày mai 01:00
```

Không dùng `isDone()` để kết luận “hôm nay job đã xử lý xong chưa”. Muốn theo dõi từng lượt, hãy ghi nhận `lastStartedAt`, `lastCompletedAt`, `lastSucceededAt`, số lần chạy, lỗi và trạng thái theo mô hình riêng.

## 4. Những phương thức TaskScheduler trả handle

Với Spring Framework 6.x, `TaskScheduler` cung cấp các overload dựa trên `Instant`/`Duration` và `Trigger`. Các chữ ký tiêu biểu:

```java
ScheduledFuture<?> schedule(Runnable task, Instant startTime);

@Nullable
ScheduledFuture<?> schedule(Runnable task, Trigger trigger);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Duration period);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, Duration period);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Duration delay);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, Duration delay);
```

Đây là **tóm lược chữ ký của API Spring**, không phải một interface độc lập để tự biên dịch. `Trigger` là `org.springframework.scheduling.Trigger`, còn `ScheduledFuture` là của Java. Trên các phiên bản Spring 6.x, nhiều overload `Date`/`long` cũ đã deprecated; ưu tiên `Instant` và `Duration` trong code mới.

### 4.1. Chạy một lần ở thời điểm xác định

```java
ScheduledFuture<?> once = scheduler.schedule(
        () -> publishReport(reportId),
        Instant.now().plusSeconds(30));
```

Đây là mảnh code nghiệp vụ; `scheduler` là `TaskScheduler`, `publishReport` và `reportId` do ứng dụng cung cấp. Tác vụ **một lần** sẽ chạy khi đến thời điểm thích hợp và Future hoàn tất sau lần chạy đó (hoặc bị hủy/lỗi).

### 4.2. Fixed rate — khoảng cách mục tiêu giữa thời điểm bắt đầu

```java
ScheduledFuture<?> periodic = scheduler.scheduleAtFixedRate(
        this::pollStatus,
        Duration.ofSeconds(10));
```

Với fixed rate, lịch cố gắng duy trì các thời điểm bắt đầu theo chu kỳ đã định. Nếu một lượt chạy lâu hơn period, lượt tiếp theo có thể bắt đầu muộn; trong `ScheduledThreadPoolExecutor` của Java, các lượt **của cùng một periodic task** không thực thi chồng nhau. Điều này không cấm hai **job khác nhau** chạy đồng thời trên các worker khác nhau.

### 4.3. Fixed delay — đợi sau khi lượt trước kết thúc

```java
ScheduledFuture<?> periodic = scheduler.scheduleWithFixedDelay(
        this::cleanup,
        Duration.ofSeconds(10));
```

Fixed delay đếm khoảng nghỉ giữa **kết thúc một lượt** và **bắt đầu lượt kế tiếp**. Nếu `cleanup` chạy 30 giây thì khoảng cách giữa hai thời điểm bắt đầu lớn hơn 10 giây. Với job có thời gian thực thi biến thiên và không cần bám sát đồng hồ, fixed delay thường dễ kiểm soát hơn fixed rate.

### 4.4. Trigger/cron — thời điểm được tính động

```java
import org.springframework.scheduling.support.CronTrigger;

ScheduledFuture<?> cron = scheduler.schedule(
        () -> refreshIndexes(),
        new CronTrigger("0 */5 * * * *"));
```

Đoạn minh họa chạy mỗi 5 phút theo biểu thức cron 6 trường của Spring (`giây phút giờ ngày tháng thứ`). Nếu dùng giờ địa phương/múi giờ kinh doanh, hãy cấu hình timezone/clock phù hợp và kiểm tra DST.

**Lưu ý đặc biệt:** `schedule(Runnable, Trigger)` **có thể trả `null`** nếu trigger không đưa ra lần thực thi nào (`Trigger.nextExecution(...)` trả `null`). Vì vậy:

```java
ScheduledFuture<?> handle = scheduler.schedule(work, trigger);

if (handle == null) {
    // Không có lượt chạy nào được kích hoạt.
} else {
    // Có thể quản lý handle.
}
```

Một `null` ở đây không giống `Future` đã hủy; nó có nghĩa **không có lịch được đăng ký tại thời điểm gọi**. Với các overload `Instant` và `Duration`, hợp đồng trả về là `ScheduledFuture<?>` không nullable theo `TaskScheduler` thông thường.

## 5. cancel(false) và cancel(true): hiểu đúng chữ mayInterruptIfRunning

`cancel(boolean mayInterruptIfRunning)` yêu cầu hủy Future. Hai cách gọi khác nhau chủ yếu về **điều đang xảy ra nếu một lượt đã bắt đầu**:

| Cách gọi | Trường hợp thường phù hợp | Tác động tới lượt đang chạy |
|---|---|---|
| `cancel(false)` | Ngừng các lượt **tiếp theo**, cho lượt hiện tại tự kết thúc | Không yêu cầu ngắt thread đang thực thi |
| `cancel(true)` | Muốn cố gắng dừng sớm nếu việc đang chạy hỗ trợ interruption | Có thể interrupt worker; không bảo đảm mọi tác vụ dừng tức thì |

**Không diễn giải `cancel(false)` là “đợi lượt hiện tại chạy xong”.** Nó không chờ. Ngay sau khi phương thức trả về, một lượt hiện tại vẫn có thể đang dùng CPU, I/O, kết nối hoặc tác động hệ thống ngoài.

`cancel(true)` cũng không phải lệnh “kill thread” cưỡng bức. Interruption trong Java là cơ chế hợp tác:

- `Thread.sleep`/`BlockingQueue.take`/`CountDownLatch.await` và nhiều thao tác chặn có thể phản ứng bằng `InterruptedException`.
- Tính toán CPU trong vòng lặp dài chỉ dừng nếu code có kiểm tra cờ interrupted hoặc cơ chế hủy riêng.
- Nhiều thao tác I/O và API của bên thứ ba có cách phản hồi interruption khác nhau; cần timeout và hành vi hủy của chính thư viện.
- Nếu task đã thực hiện một side effect, `cancel(true)` không tự rollback email, thanh toán, bản ghi database hay lệnh HTTP.

### 5.1. Đọc giá trị boolean của cancel

`boolean accepted = handle.cancel(false)` thường cho biết **yêu cầu hủy Future đã được chấp nhận**; không phải một biên bản chứng minh worker đã hết chạy. `cancel(...)` có thể trả `false` nếu Future đã kết thúc hoặc không thể bị hủy theo implementation. Khi nhiều caller cùng hủy, một caller có thể thấy `true` và caller khác thấy `false`.

Nếu cần biết trạng thái sau cùng, kiểm tra `isCancelled()` và theo dõi riêng mốc tác vụ đã thật sự thoát khỏi phần thực thi. Khi hủy để dừng một service, cần một cơ chế shutdown/drain riêng; không dùng một cờ Future làm bằng chứng rằng mọi tài nguyên đã được giải phóng.

### 5.2. Mẫu xử lý interruption trong Runnable

```java
Runnable cancellable = () -> {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            doSmallUnitOfWork();
            Thread.sleep(100);
        }
    } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        // Kết thúc tác vụ, để vòng ngoài không tiếp tục chạy.
    } finally {
        releaseOwnedResources();
    }
};
```

Đây là mảnh minh họa; `doSmallUnitOfWork` và `releaseOwnedResources` phải do ứng dụng định nghĩa. Code phải được thiết kế sao cho `doSmallUnitOfWork()` tự không chạy vô hạn. Trong phương thức `finally`, chỉ giải phóng tài nguyên mà task thực sự sở hữu; đừng đóng scheduler chung của cả ứng dụng.

## 6. isDone/isCancelled: bảng trạng thái cần ghi nhớ

| Tình huống | `isDone()` | `isCancelled()` | Giải thích |
|---|---|---|---|
| One-shot đã được nhận, chưa đến giờ | Thường `false` | `false` | Còn chờ đến thời điểm chạy |
| One-shot đang thực thi | Thường `false` | `false` | Chưa hoàn tất Future |
| One-shot chạy xong bình thường | `true` | `false` | Đã kết thúc thành công |
| One-shot chạy và ném exception được propagate | `true` | `false` | Đã kết thúc thất bại |
| Future đã được hủy thành công | `true` | `true` | Terminal state là cancel |
| Periodic còn hoạt động dù chạy nhiều lượt | Thường `false` | `false` | Cả chuỗi lịch vẫn còn |
| Periodic dừng do propagated error | Có thể `true` | `false` | Phụ thuộc error policy của scheduler |
| Periodic của Spring được mặc định log/suppress lỗi | Thường vẫn `false` | `false` | Chuỗi có thể tiếp tục sau lỗi của một lượt |

**Cẩn thận với trường hợp Spring mặc định:** `ThreadPoolTaskScheduler` truyền thống bọc task bằng cơ chế `TaskUtils`. Mặc định, exception của tác vụ **lặp** thường được **log và suppress** để lịch còn cơ hội chạy lượt sau; tác vụ **một lần** thường được log và propagate. Vì vậy không thể sao chép nguyên kết luận của `ScheduledThreadPoolExecutor` Java thuần rằng một exception trong periodic runnable luôn tự chấm dứt lịch. Cần xem `ErrorHandler` của scheduler đang dùng.

## 7. Một chương trình Java 17 thuần chứng minh việc hủy

`ScheduledFuture` là API Java tiêu chuẩn, nên có thể kiểm tra các trạng thái căn bản mà **không cần Spring**. Ví dụ dưới đây lập lịch một công việc sau một ngày, hủy trước khi nó chạy, rồi kiểm tra trạng thái và hành vi `get()`.

**Tệp `ScheduledFutureCancelDemo.java` — chạy độc lập bằng JDK 17:**

```java
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledFutureCancelDemo {
    public static void main(String[] args) {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        AtomicInteger executions = new AtomicInteger();

        try {
            ScheduledFuture<?> handle = executor.schedule(
                    executions::incrementAndGet,
                    1, TimeUnit.DAYS);

            System.out.println("Còn trì hoãn: "
                    + (handle.getDelay(TimeUnit.SECONDS) > 0));

            System.out.println("Hủy được: " + handle.cancel(false));
            System.out.println("Đã hủy: " + handle.isCancelled());
            System.out.println("Đã kết thúc: " + handle.isDone());

            try {
                handle.get();
            } catch (CancellationException expected) {
                System.out.println("get(): CancellationException");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            } catch (java.util.concurrent.ExecutionException failure) {
                throw new IllegalStateException(failure);
            }

            System.out.println("Số lần chạy: " + executions.get());
        } finally {
            executor.shutdownNow();
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ScheduledFutureCancelDemo.java
java ScheduledFutureCancelDemo
```

Đầu ra trong điều kiện thời gian đồng hồ thông thường:

```text
Còn trì hoãn: true
Hủy được: true
Đã hủy: true
Đã kết thúc: true
get(): CancellationException
Số lần chạy: 0
```

Tại sao kết quả ổn định? Lịch được đặt cách một ngày, chương trình hủy ngay trong cùng luồng trước khi tới hạn. `cancel(false)` đánh dấu Future bị hủy, `get()` ném `CancellationException` thay vì đợi một ngày, và `executions` chưa được tăng. Với máy bị đóng băng tới hơn một ngày hoặc đồng hồ hệ thống bất thường, đầu ra về delay không còn là điều có thể bảo đảm; ví dụ cố ý dùng khoảng trì hoãn lớn để minh họa bình thường.

**`setRemoveOnCancelPolicy(true)`** giúp `ScheduledThreadPoolExecutor` loại bỏ task đã hủy khỏi queue kịp thời thay vì giữ nó tới khi hết thời gian trì hoãn. Đây là tùy chọn về quản lý queue, **không thay đổi ngữ nghĩa `cancel(false)`** và không xóa nghiệp vụ nào đã hoàn tất.

## 8. Một lần và nhiều lần: sử dụng get() như thế nào?

### 8.1. One-shot Runnable

Với lịch một lần, `get(timeout)` có thể dùng để chờ việc hoàn tất (hoặc phát hiện lỗi). Một `Runnable` không trả giá trị nghiệp vụ; khi hoàn tất bình thường, `get()` trả `null` theo hợp đồng của `ScheduledExecutorService`.

```java
ScheduledFuture<?> future = scheduler.schedule(
        () -> generateDailySnapshot(),
        Instant.now());

try {
    Object value = future.get(10, TimeUnit.SECONDS);
    // value thường null khi Runnable hoàn tất bình thường.
} catch (TimeoutException timeout) {
    future.cancel(true);
}
```

Đây là mảnh code đặt trong method có `scheduler`/`generateDailySnapshot` và import các lớp Java tương ứng; method còn phải xử lý `InterruptedException` và `ExecutionException` hoặc khai báo `throws`. `get(timeout)` chặn caller tới khi Future hoàn tất hoặc thời gian chờ hết. **Timeout không tự hủy**: gọi `cancel(...)` riêng nếu phù hợp, rồi xử lý deadline và side effect nghiệp vụ.

### 8.2. Periodic Runnable

Với công việc lặp đang hoạt động, **đừng gọi `get()` để “đợi một lượt cron vừa chạy xong”**. Future đại diện cho chuỗi, nên lời gọi có thể chặn vô thời hạn nếu lịch tiếp tục chạy bình thường. Trong Java `ScheduledExecutorService`, `get()` của periodic Future không trả bình thường; nó chỉ thoát bằng hủy/lỗi/đứt quãng theo hợp đồng.

Một mẫu sai:

```java
ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
        this::refresh, Duration.ofMinutes(1));
future.get(); // Có thể chờ mãi vì lịch còn chạy.
```

Đây là mảnh code **phản ví dụ**, không dùng trong luồng HTTP hay đường khởi tạo nếu chưa có kế hoạch dừng rõ. Với Spring, `ErrorHandler` mặc định có thể suppress lỗi lặp, làm Future tiếp tục tồn tại dù một lượt đã thất bại.

Nếu cần biết **lượt gần nhất** xong hay chưa, ghi nhận riêng: `AtomicInteger` đếm lượt, `CountDownLatch` cho một mốc cụ thể trong test, `CompletableFuture` per-run nếu API thiết kế như vậy, hoặc lưu `lastExecutionStatus` trong kho trạng thái có kiểm soát.

## 9. Dùng ScheduledFuture trong Spring ThreadPoolTaskScheduler

`ThreadPoolTaskScheduler` là triển khai thông dụng của Spring `TaskScheduler`, dùng `ScheduledThreadPoolExecutor` bên dưới. Nó có `setPoolSize(...)`, `setThreadNamePrefix(...)`, `setErrorHandler(...)`, `setRemoveOnCancelPolicy(...)` và các tùy chọn lifecycle/shutdown.

**Tệp `SchedulerConfig.java` — Spring Configuration, không độc lập:**

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {

    @Bean("jobScheduler")
    public ThreadPoolTaskScheduler jobScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("job-clock-");
        scheduler.setRemoveOnCancelPolicy(true);

        return scheduler;
    }
}
```

Spring quản lý khởi tạo và đóng bean này theo `ApplicationContext`. Một method service inject bean:

```java
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class AuditScheduleService {
    private final TaskScheduler scheduler;

    public AuditScheduleService(
            @Qualifier("jobScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public ScheduledFuture<?> start() {
        return scheduler.scheduleWithFixedDelay(
                () -> System.out.println("audit tick"),
                Duration.ofMinutes(2));
    }
}
```

Đây là một **Spring component**, không phải chương trình Java SE standalone. Ở production, hãy **giữ handle trả về** nếu muốn hủy chính lịch đã tạo; nếu `start()` tạo lịch mới trên mỗi request mà không ai sở hữu handle, ứng dụng có thể tạo nhiều lịch trùng nhau và không có cách quản lý thuận tiện.

**Không cần `@EnableScheduling` cho lời gọi programmatic `scheduler.schedule*(...)` trực tiếp.** `@EnableScheduling` chủ yếu bật xử lý annotation `@Scheduled`; đó là cơ chế đăng ký khác. Dù vậy, một ứng dụng có thể sử dụng đồng thời cả hai.

### 9.1. Thiết kế handle theo vòng đời nghiệp vụ

Một service nhận lệnh “bật/tắt lịch cho report-X” cần giữ một handle theo report ID thay vì một biến dùng chung:

```text
Report-A  -> ScheduledFuture-A
Report-B  -> ScheduledFuture-B
Report-C  -> ScheduledFuture-C
```

Nếu client muốn tắt `Report-B`, service tra handle của B và `cancel(...)` đúng đối tượng. `ScheduledFuture` chỉ có hiệu lực trong tiến trình/container tạo lịch. Nó không phải ID có thể gửi ra ngoài để JVM khác tự tìm được Future, và nó không tồn tại sau khi ứng dụng restart.

## 10. Ví dụ Maven hoàn chỉnh: chạy một lần, hủy lịch định kỳ và hủy lịch chưa đến hạn

Ví dụ sau dùng **JDK 17+**, Maven và **Spring Framework 6.2.7**. Nó chứng minh cùng một `ThreadPoolTaskScheduler` có thể:

1. Chạy `Runnable` một lần, rồi `get(...)` chờ kết quả `null`.
2. Chạy lịch fixed delay với chu kỳ rất dài, đợi lượt đầu qua `CountDownLatch` rồi gọi `cancel(false)` để ngăn lượt kế tiếp.
3. Đăng ký một job cách một giờ và hủy trước khi chạy.

Lý do dùng chu kỳ dài và latch là để có **stdout có thể kiểm chứng mà không dựa vào `Thread.sleep` hoặc cuộc đua giữa worker**.

### 10.1. Cấu trúc thư mục

```text
scheduled-handle-demo/
├── pom.xml
└── src/main/java/lesson/ScheduledHandleDemo.java
```

### 10.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lesson</groupId>
    <artifactId>scheduled-handle-demo</artifactId>
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

### 10.3. Tệp src/main/java/lesson/ScheduledHandleDemo.java

```java
package lesson;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class ScheduledHandleDemo {

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            TaskScheduler scheduler = context.getBean(
                    "demoScheduler", TaskScheduler.class);

            AtomicInteger onceCount = new AtomicInteger();
            ScheduledFuture<?> once = scheduler.schedule(
                    onceCount::incrementAndGet,
                    Instant.now());

            Object oneResult = once.get(5, TimeUnit.SECONDS);
            System.out.println("Một lần xong: " + once.isDone());
            System.out.println("Kết quả Runnable là null: "
                    + (oneResult == null));
            System.out.println("Số lượt một lần: "
                    + onceCount.get());

            CountDownLatch firstTick = new CountDownLatch(1);
            AtomicInteger tickCount = new AtomicInteger();

            ScheduledFuture<?> recurring =
                    scheduler.scheduleWithFixedDelay(() -> {
                        tickCount.incrementAndGet();
                        firstTick.countDown();
                    }, Duration.ofHours(1));

            if (!firstTick.await(5, TimeUnit.SECONDS)) {
                recurring.cancel(true);
                throw new IllegalStateException("Không có lượt đầu");
            }

            System.out.println("Hủy lịch lặp: "
                    + recurring.cancel(false));
            System.out.println("Lịch lặp đã hủy: "
                    + recurring.isCancelled());
            System.out.println("Số lượt đã chạy: "
                    + tickCount.get());

            AtomicInteger lateRuns = new AtomicInteger();
            ScheduledFuture<?> later = scheduler.schedule(
                    lateRuns::incrementAndGet,
                    Instant.now().plus(Duration.ofHours(1)));

            System.out.println("Hủy lịch chờ: "
                    + later.cancel(false));
            System.out.println("Lịch chờ đã chạy: "
                    + lateRuns.get());
        }
    }

    @Configuration
    public static class Config {
        @Bean("demoScheduler")
        public ThreadPoolTaskScheduler demoScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("schedule-demo-");
            scheduler.setRemoveOnCancelPolicy(true);
            return scheduler;
        }
    }
}
```

### 10.4. Biên dịch và chạy

Trong thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.ScheduledHandleDemo
```

**Tám dòng do chương trình in ra trong môi trường chạy bình thường:**

```text
Một lần xong: true
Kết quả Runnable là null: true
Số lượt một lần: 1
Hủy lịch lặp: true
Lịch lặp đã hủy: true
Số lượt đã chạy: 1
Hủy lịch chờ: true
Lịch chờ đã chạy: 0
```

Các dòng trên do chương trình in theo các lệnh `println`, không tính log riêng của Maven/Spring. Timeout 5 giây làm chương trình báo lỗi rõ ràng khi worker không được thực thi kịp trên máy quá tải, thay vì treo vô hạn.

Giải thích tính ổn định:

- `once.get(5, TimeUnit.SECONDS)` chờ một task one-shot đã hoàn tất; `Runnable` không trả giá trị nên `oneResult` là `null`, còn `onceCount` là 1.
- `scheduleWithFixedDelay(..., Duration.ofHours(1))` cho lượt đầu chạy ngay; `firstTick.await` xác nhận nó đã increment và báo chốt. Lượt tiếp theo chỉ đến sau **một giờ kể từ khi lượt trước kết thúc**.
- `recurring.cancel(false)` yêu cầu chấm dứt chuỗi định kỳ, không cần interrupt worker. Khi vẫn còn lịch tương lai, Future chưa tự hoàn tất bình thường sau lượt đầu.
- `later` là lịch một lần sau một giờ, được hủy trước khi đến hạn. Vì vậy `lateRuns` bằng 0 khi in.
- `try-with-resources` đóng Spring context, giao trách nhiệm kết thúc bean scheduler cho Spring; service không tự shutdown một scheduler dùng chung.

Ví dụ này minh họa **hợp đồng handle và hủy**, không phải cách triển khai một hệ thống job bền vững. Số worker 2, thời gian 1 giờ và các timeout được chọn cho tính minh họa, không có nghĩa phù hợp với production.

## 11. Tránh hiểu lầm: cancel(false) không chờ lượt hiện tại kết thúc

Hãy tưởng tượng một task đang chạy:

```text
T=0s       worker bắt đầu gửi file lớn
T=1s       caller gọi future.cancel(false)
T=1.001s   future.isCancelled() có thể true
T=10s      worker gửi file xong và giải phóng connection
```

`cancel(false)` có thể đánh dấu lịch bị hủy tại T=1s để ngăn lần lặp sau, nhưng **không ép dừng lượt đang chạy**. Nếu code quản lý job lập tức xóa tất cả trạng thái và giải phóng tài nguyên dùng chung ngay T=1.001s, worker vẫn có thể truy cập những tài nguyên đó và xảy ra race.

Một giao thức stop thường cần **hai giai đoạn**:

1. **Không lên lịch thêm lượt mới**: giữ handle và hủy lịch, thường với `cancel(false)` nếu muốn graceful.
2. **Chờ/quan sát các lượt đang chạy kết thúc**: dùng `CountDownLatch`, `Phaser`, bộ đếm active, trạng thái job hoặc shutdown/await termination ở cấp tài nguyên phù hợp.

Không sử dụng `future.get()` cho lịch periodic để chờ một lượt đã bắt đầu vì nó theo dõi **cả chuỗi**. Với task có I/O, thêm timeout và chính sách hủy/đối soát riêng.

## 12. Khi nào nên dùng cancel(true)?

Một trường hợp phù hợp là task hỗ trợ interruption, công việc có thể hủy giữa chừng và không có side effect nửa vời nguy hiểm.

```java
Runnable job = () -> {
    try {
        for (int i = 0; i < 100; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            performSmallBatch(i);
        }
    } finally {
        closeOwnedResources();
    }
};
```

Đây là **mảnh code minh họa**, với `performSmallBatch`/`closeOwnedResources` thuộc ứng dụng. Người gọi có thể yêu cầu `handle.cancel(true)`; task phải kiểm tra interrupted một cách có chủ đích, đồng thời bảo đảm tài nguyên được giải phóng.

Nếu mỗi batch thao tác database rồi commit từng phần, interruption không rollback các batch trước. Vì vậy hủy một job quan trọng phải có trạng thái `CANCEL_REQUESTED`, `CANCELLED`, `PARTIAL` hoặc tương đương nếu nghiệp vụ yêu cầu; không đồng nhất cờ Future với kết quả nghiệp vụ cuối cùng.

**Cẩn thận:** Một số thư viện dùng interruption để điều phối nội bộ. Hãy đọc contract trước khi interrupt worker của scheduler chung, đặc biệt khi tác vụ có callback hoặc tài nguyên dùng chung.

## 13. Quản lý nhiều lịch trong service bằng handle registry

Nếu người dùng có thể bật/tắt nhiều lịch, không thể chỉ giữ một biến `ScheduledFuture<?> last`. Một cách đơn giản trong **một JVM** là map từ job ID sang handle.

**Tệp `ManagedScheduleService.java` — Spring-managed component minh họa:**

```java
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class ManagedScheduleService {

    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> handles =
            new HashMap<>();

    public ManagedScheduleService(
            @Qualifier("jobScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public synchronized void replace(
            String jobId, Runnable work, Duration delay) {

        Objects.requireNonNull(jobId);
        Objects.requireNonNull(work);
        if (delay.isZero() || delay.isNegative()) {
            throw new IllegalArgumentException(
                    "Delay phải dương");
        }

        ScheduledFuture<?> fresh =
                scheduler.scheduleWithFixedDelay(work, delay);

        ScheduledFuture<?> previous = handles.put(jobId, fresh);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public synchronized boolean stop(String jobId) {
        ScheduledFuture<?> handle = handles.remove(jobId);
        return handle != null && handle.cancel(false);
    }

    public synchronized boolean isRegistered(String jobId) {
        return handles.containsKey(jobId);
    }
}
```

Đây là **class Spring**, không có `main` độc lập. Khóa `synchronized` bảo vệ các thao tác map khi nhiều request cùng gọi `replace/stop` trong cùng instance. Nó **không** giữ khóa trong lúc chạy `work`; phần nghiệp vụ được scheduler thực hiện bên ngoài khóa.

Các giới hạn của mẫu cần được nói rõ:

- `scheduleWithFixedDelay` có thể chạy lượt đầu **rất sớm**. Vì vậy khi thay lịch, có một khoảng ngắn cả `fresh` và lượt cũ có thể tồn tại/chạy đồng thời trước khi `previous.cancel(false)` có tác dụng.
- `cancel(false)` trên lịch cũ không ngắt lượt cũ đang thực thi. Nếu tuyệt đối không được chồng tác động của hai phiên bản job, cần cơ chế serialization/lease/idempotency ở cấp nghiệp vụ.
- `isRegistered` chỉ nói map có handle, không bảo đảm lịch còn sống. Có thể bổ sung logic kiểm tra `isCancelled`/`isDone` hoặc một registry trạng thái chặt hơn.
- Khi `scheduleWithFixedDelay` bị từ chối, phương thức ném exception trước khi thay map, nên handle cũ vẫn được giữ. Phải xử lý lỗi gửi lịch tại biên API.
- Map và Future chỉ sống trong **một tiến trình**. Chạy nhiều pod sẽ có các registry khác nhau; không có cơ chế chọn leader hoặc đồng bộ hủy ở đây.
- Không giữ `ScheduledFuture` vô thời hạn trong map sau khi one-shot đã xong; cần dọn những entry không còn dùng để tránh giữ tham chiếu và tài nguyên.
- Mẫu dùng `HashMap + synchronized` là minh họa quản lý một registry nhỏ. Với tải lớn, lifecycle nhiều trạng thái hoặc yêu cầu cluster, nên xây abstraction chuyên dụng và test nhiều luồng.

### 13.1. API hủy theo job ID

Một controller có thể gọi `managedScheduleService.stop(jobId)` để gửi yêu cầu hủy. HTTP response nên phản ánh **yêu cầu hủy ở scheduler cục bộ đã được chấp nhận hay chưa**, không tuyên bố mọi side effect đã dừng tức thì.

Nếu người dùng cần biết dừng hoàn toàn, API thường phải có trạng thái công việc và cơ chế đối soát với lượt đang chạy. Đối với hệ phân tán, một job ID nên được lưu ở kho bền vững và gắn với scheduler owner/lease thay vì trả đối tượng `ScheduledFuture` trực tiếp cho client.

## 14. CronTrigger và trường hợp không có lịch tiếp theo

`TaskScheduler.schedule(Runnable, Trigger)` có thể trả `null` khi `Trigger.nextExecution(...)` không cho thời điểm nào. Ví dụ một `Trigger` giới hạn số lượt hoặc không còn thỏa điều kiện nghiệp vụ.

```java
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.Trigger;

public ScheduledFuture<?> register(
        Runnable work, Trigger trigger) {
    return scheduler.schedule(work, trigger);
}
```

Đây là method minh họa trong class có `scheduler`. API có thể trả `null`, nên caller cần kiểm tra:

```java
ScheduledFuture<?> handle = register(work, trigger);
if (handle == null) {
    System.out.println("Không có lịch kế tiếp");
} else {
    saveHandle(handle);
}
```

Không nên gọi `handle.cancel(false)` khi `handle == null`. Đồng thời, `getDelay(...)` trên một ScheduledFuture của trigger chỉ là thông tin trì hoãn theo implementation tại thời điểm đọc, **không phải API metadata toàn bộ lịch cron**. Để hiển thị lịch theo timezone và rule, quản lý riêng biểu thức cron, vùng thời gian và lần chạy gần nhất.

Với `CronTrigger` chạy theo thời gian thực, các sự kiện **DST**, sửa giờ hệ thống, restart, một lượt kéo dài hoặc giờ không tồn tại ở một timezone có thể thay đổi thời điểm thực thi thực tế. Không giả định “cron lúc 02:30” chắc chắn xảy ra đúng một lần mỗi ngày ở mọi timezone.

## 15. @Scheduled và TaskScheduler programmatic khác nhau

Spring có hai mô hình tương tự nhưng khác cách quản lý handle:

| Cách dùng | Ai đăng ký lịch? | Handle đi đâu? | Khi nào phù hợp? |
|---|---|---|---|
| `taskScheduler.schedule(...)` trong service | Code ứng dụng | Được trả trực tiếp dưới dạng `ScheduledFuture<?>` | Cần bật/tắt/thay lịch theo job ID lúc runtime |
| `@Scheduled(fixedDelay=...)` trên method Spring | Hạ tầng annotation scheduling | Không trả ScheduledFuture trực tiếp từ lời gọi method | Lịch tĩnh theo cấu hình, do container quản lý |
| `ScheduledTaskRegistrar`/`ScheduledTask` | Spring scheduling registration API | Handle `ScheduledTask` riêng, có `cancel(...)` | Khi quản lý các task đăng ký qua registrar |

Ví dụ annotation:

```java
import org.springframework.scheduling.annotation.Scheduled;

@Scheduled(fixedDelay = 60000)
public void cleanCache() {
    // Spring tự đăng ký và lập lịch dựa trên annotation.
}
```

Đây là **mảnh method Spring**, cần bean hợp lệ và `@EnableScheduling` hoặc cơ chế tương đương để annotation được xử lý. Lời gọi bình thường `cleanCache()` **không trả ScheduledFuture**. Nếu cần hủy theo lựa chọn người dùng mà không phải ngừng cả bean, cân nhắc chuyển phần lập lịch sang service dùng `TaskScheduler` programmatic.

Không tự lấy một `ScheduledFuture` giả định từ `@Scheduled` nếu code chưa đăng ký/thu giữ handle; và không shutdown scheduler toàn ứng dụng chỉ để tắt một lịch cụ thể.

## 16. Exception của task định kỳ: khác biệt Spring và Java chuẩn

Đây là một điểm quan trọng khi dùng `ScheduledFuture` để **theo dõi lỗi**.

### 16.1. Java ScheduledExecutorService thuần

Với `ScheduledThreadPoolExecutor.scheduleAtFixedRate(...)` và `scheduleWithFixedDelay(...)`, nếu một lượt thực thi ném exception ra ngoài, Java ghi nhận **kết thúc bất thường của chuỗi periodic**. Những lượt tiếp theo bị dừng; `isDone()` có thể chuyển `true` và `get()` ném `ExecutionException` chứa nguyên nhân.

### 16.2. Spring ThreadPoolTaskScheduler mặc định

Spring thường bọc `Runnable` bằng error-handling wrapper. `TaskUtils.getDefaultErrorHandler(isRepeatingTask)` trả chính sách khác nhau:

- **Task lặp:** ghi log và **suppress** exception theo mặc định, để lịch có thể tiếp tục.
- **Task một lần:** ghi log và **propagate** exception theo mặc định, vì caller có thể quan sát lỗi qua Future.

Do đó, ngay cả khi một lượt `@Scheduled`/programmatic periodic xảy ra lỗi, `ScheduledFuture.isDone()` vẫn có thể là `false` và lịch vẫn có lượt tiếp theo. **Không dựa vào `isDone()` để tính số lượt lỗi**, mà nên ghi nhận kết quả của từng lần chạy.

### 16.3. Cấu hình ErrorHandler nếu cần chính sách khác

`ThreadPoolTaskScheduler` có:

```java
scheduler.setErrorHandler(error -> {
    logScheduledFailure(error);
});
```

Đây là **mảnh cấu hình** với `logScheduledFailure` do ứng dụng định nghĩa. Nếu handler chỉ log và không ném tiếp, nó có thể **suppress lỗi**. Nếu muốn chấm dứt lịch sau một exception, cần chính sách propagate và phải hiểu điều này ảnh hưởng đến Future/chuỗi periodic.

Spring có `TaskUtils.LOG_AND_PROPAGATE_ERROR_HANDLER` để log rồi ném lại, nhưng sử dụng nó phải là **quyết định nghiệp vụ có chủ đích**. Dùng một handler global kiểu propagate có thể khiến nhiều lịch vốn phải tự phục hồi bị dừng vì lỗi tạm thời.

Không nhầm `AsyncConfigurer.getAsyncUncaughtExceptionHandler()` dành cho `@Async void` với `ThreadPoolTaskScheduler.setErrorHandler(...)` dành cho scheduler. Đây là hai đường xử lý lỗi khác nhau.

## 17. Hủy lịch và loại bỏ task khỏi queue

Trong `ThreadPoolTaskScheduler` dựa trên `ScheduledThreadPoolExecutor`, một task đã bị hủy nhưng chưa đến hạn có thể còn được giữ trong queue tới thời điểm xử lý, tùy **remove-on-cancel policy**.

Cấu hình:

```java
ThreadPoolTaskScheduler scheduler =
        new ThreadPoolTaskScheduler();

scheduler.setPoolSize(2);
scheduler.setRemoveOnCancelPolicy(true);
```

Đây là mảnh cấu hình trước khi Spring khởi tạo bean. `setRemoveOnCancelPolicy(true)` yêu cầu underlying `ScheduledThreadPoolExecutor` loại bỏ task bị cancel khỏi queue sớm khi có thể. Điều này hữu ích nếu ứng dụng liên tục tạo rồi hủy hàng nghìn lịch tương lai.

Tuy nhiên:

- Nó **không hủy tất cả lịch**; chính code gọi `handle.cancel(...)` mới lựa chọn lịch cần hủy.
- Nó **không ngắt task đang chạy**; `cancel(true)` và khả năng hợp tác của task quyết định việc yêu cầu interruption.
- Nó **không tạo giới hạn dung lượng queue** như `queueCapacity` của `ThreadPoolTaskExecutor`.
- Nó **không thay thế cleanup map/registry** chứa handle do ứng dụng lưu.
- Khi dùng một implementation `TaskScheduler` khác, cần kiểm tra API chính sách tương ứng; không giả định setter này tồn tại trên interface `TaskScheduler`.

Đối với hệ thống nhiều tenant, hãy đo `queue depth`, số lịch active/cancelled và retained heap. Không dùng việc để queue không giới hạn như một cơ chế giữ job bền vững qua restart.

## 18. Shutdown: hủy từng lịch khác với đóng scheduler

`future.cancel(...)` nhắm tới **một handle cụ thể**. Ngược lại, `scheduler.shutdown()` ở tầng executor/scheduler có tác động tới toàn bộ tập công việc của pool và chính sách nhận lịch mới.

Trong Spring, một `ThreadPoolTaskScheduler` được khai báo `@Bean` được quản lý bởi `ApplicationContext`. Một service nghiệp vụ **không nên gọi `shutdown()` trên bean chung** khi chỉ muốn dừng một job của mình. Làm vậy có thể khiến các service khác bị `TaskRejectedException` khi cố đặt lịch.

Các chính sách cần chú ý:

- `setContinueExistingPeriodicTasksAfterShutdownPolicy(...)`: có tiếp tục các tác vụ periodic hiện có sau shutdown hay không.
- `setExecuteExistingDelayedTasksAfterShutdownPolicy(...)`: có thực thi những one-shot đã đặt lịch nhưng chưa đến hạn sau shutdown hay không.
- `setWaitForTasksToCompleteOnShutdown(...)` và các cấu hình lifecycle của Spring: quyết định đợi/không đợi như thế nào khi đóng context.

Trong Spring `ThreadPoolTaskScheduler` 6.2.7, `continueExistingPeriodicTasksAfterShutdown` mặc định là `false`; `executeExistingDelayedTasksAfterShutdown` mặc định là `true` trên ScheduledThreadPoolExecutor. Ứng dụng có yêu cầu **không chạy tác vụ sau khi bắt đầu shutdown** cần thiết kế chính sách rõ, kiểm tra version và thử nghiệm hành vi thật.

Hãy kiểm thử tắt ứng dụng khi có: one-shot chưa đến hạn, periodic đang chạy, periodic đang chờ, task bị interrupt, và lịch đang được thay đổi. Đối với job không được mất khi restart, cần persistence/broker hoặc distributed scheduler, không chỉ giữ `ScheduledFuture` trong heap.

## 19. Giám sát lịch: trạng thái handle chưa đủ

Một dashboard tốt cần phân biệt **đăng ký lịch**, **lần chạy**, **hủy**, **lỗi** và **kết thúc vòng đời**.

| Chỉ số/trường | Ý nghĩa | Nguồn có thể lấy |
|---|---|---|
| `schedule.registered` | Lịch được đăng ký thành công | Service quản lý handle |
| `schedule.cancel.requested` | Có yêu cầu hủy | Lệnh điều khiển |
| `schedule.cancel.accepted` | `cancel(...)` trả `true` | Kết quả Future |
| `schedule.active` | Lịch còn hoạt động theo nghiệp vụ | Registry + trạng thái Future |
| `run.started` | Một lượt đã bắt đầu | Wrapper quanh Runnable |
| `run.succeeded` | Một lượt hoàn tất thành công | Wrapper/logic nghiệp vụ |
| `run.failed` | Một lượt ném exception | Wrapper/ErrorHandler |
| `run.duration` | Thời gian thực thi một lượt | Timer per run |
| `run.lastSucceededAt` | Lần hoàn tất thành công gần nhất | State store |
| `schedule.nextDue` | Lần kích hoạt dự kiến | Trigger/metadata do ứng dụng quản lý |

`isDone()`/`isCancelled()` trả snapshot trạng thái Future, không tự cung cấp `lastSucceededAt`, số lượt và lỗi. `getDelay(...)` có thể gợi ý khoảng chờ kế tiếp nhưng không phải cách đáng tin cậy để tính SLO hoặc SLA từng job.

Một wrapper đo số lần chạy minh họa:

```java
AtomicInteger successCount = new AtomicInteger();

Runnable monitored = () -> {
    long startNs = System.nanoTime();
    try {
        executeBusinessStep();
        successCount.incrementAndGet();
    } finally {
        long elapsedNs = System.nanoTime() - startNs;
        recordDuration(elapsedNs);
    }
};
```

Đây là mảnh code với `executeBusinessStep`/`recordDuration` do ứng dụng định nghĩa. Để đếm lỗi, thêm `catch` phù hợp và quyết định ném lại hay suppress theo error policy của lịch. Nếu job có side effect, “không ném exception” chưa chắc là thành công cuối cùng ở dịch vụ ngoài.

## 20. Quản lý cron sau restart hoặc nhiều instance

`ScheduledFuture` là handle cục bộ trong JVM, không phải một cơ chế điều phối phân tán. Nếu một ứng dụng chạy trên ba pod và cả ba cùng gọi `scheduleAtFixedRate(...)` khi khởi động, thông thường sẽ có **ba lịch** độc lập và task có thể chạy ba lần.

Hủy Future ở pod A không tự hủy Future ở pod B/C. Cũng không có bảo đảm job sống qua:

- JVM crash hoặc kill -9.
- Pod bị thay thế/rolling deployment.
- Toàn bộ Spring ApplicationContext được tạo lại.
- Thay đổi người sở hữu job trong cluster.

Nếu công việc phải được bảo đảm và thực thi một lần theo hợp đồng nghiệp vụ, cân nhắc **leader election, distributed lock/lease, kho công việc bền vững, outbox hoặc scheduler chuyên dụng**. Dù chọn giải pháp nào, vẫn cần idempotency và chính sách retry/đối soát.

Nếu người dùng có nút “Tắt lịch”, hệ thống nhiều instance phải có giao thức truyền lệnh đến đúng owner hoặc đổi trạng thái trong kho phân tán để các owner tuân theo. `cancel(false)` tại một tiến trình không phải một API hủy cluster.

## 21. Các lỗi thường gặp khi sử dụng ScheduledFuture

| Sai lầm | Vì sao sai? | Cách làm đúng |
|---|---|---|
| Lập lịch nhưng bỏ Future trả về | Không còn handle thuận tiện để hủy riêng lịch | Lưu handle theo job ID nếu có yêu cầu điều khiển |
| Dùng `get()` của periodic Future để đợi một lượt | Có thể chờ mãi khi chuỗi lịch còn hoạt động | Dùng latch/counter/trạng thái per-run |
| Coi `isDone()` là “lượt gần nhất thành công” | Future của lịch lặp bao quát cả chuỗi | Đo từng lượt chạy riêng |
| Coi `isCancelled()` là worker đã dừng | `cancel(false)` không ngắt lượt đang chạy | Chờ mốc task hoàn tất nếu cần graceful stop |
| Gọi `cancel(true)` và tin đã rollback dữ liệu | Interruption là hợp tác; side effect không tự hoàn tác | Timeout, kiểm tra interrupted, idempotency |
| Không kiểm tra `null` từ `schedule(task, trigger)` | Trigger có thể không cho next execution | Xử lý nullable trước khi lưu/hủy |
| Cùng job ID đăng ký lặp mà không hủy lịch cũ | Nhiều handle cùng kích hoạt | Quản lý registry và chính sách replace |
| Chỉ tăng poolSize khi lịch chậm | Có thể do task blocking hoặc tài nguyên ngoài | Đo run duration, queue wait, downstream |
| Dùng scheduler cho I/O chặn lâu | Giữ thread lập lịch, làm trễ job khác | Giới hạn/chuyển tác vụ nặng qua executor phù hợp |
| Không hiểu Spring ErrorHandler | Lỗi periodic có thể bị suppress và vẫn chạy | Cấu hình/giám sát lỗi per-run |
| Gọi `scheduler.shutdown()` để tắt một report | Làm hỏng các lịch dùng chung pool | Gọi `handle.cancel(...)` |
| Không dọn handle one-shot đã xong | Map giữ tham chiếu và tăng memory | Cleanup registry theo lifecycle |
| Tưởng removeOnCancelPolicy là queueCapacity | Nó chỉ thay chính sách loại bỏ task bị cancel | Quản lý tải/worker/retained heap riêng |
| Ghi `@Scheduled` rồi gọi method để lấy Future | Annotation registration không trả Future cho caller method | Chọn programmatic TaskScheduler khi cần handle |
| Lưu ScheduledFuture vào database | Đây là object JVM không phải ID bền vững | Lưu job metadata/trạng thái/cron, tạo handle mới sau restart |

## 22. Kiểm thử scheduled cancellation có thể lặp lại

Concurrency test dựa vào `Thread.sleep(100)` rồi dự đoán trạng thái rất dễ flake trên CI. Dùng `CountDownLatch`/`Future.get(timeout)` để chứng minh mốc thực tế.

Những kịch bản tối thiểu:

1. **One-shot chưa đến hạn:** đăng ký xa trong tương lai, hủy trước khi chạy; khẳng định `isCancelled`/`isDone` và side effect chưa xảy ra.
2. **One-shot hoàn tất:** đặt lịch ngay, dùng `get(timeout)`; kỳ vọng `get()` trả `null` cho `Runnable` và side effect đã xảy ra.
3. **Periodic đã chạy một lượt:** latch báo lần đầu, gọi `cancel(false)`, xác nhận không có lần tiếp theo trong cửa sổ thử hợp lý; không dùng sleep làm bằng chứng duy nhất.
4. **Periodic đang chạy:** giữ task ở latch, gọi `cancel(false)`; giải phóng latch rồi quan sát worker kết thúc. Chứng minh Future có thể đã cancelled trước khi logic task thoát.
5. **Hủy có interrupt:** task chặn ở API hỗ trợ interruption, gọi `cancel(true)`; xác minh task nhận interruption và cleanup chạy. Đặt timeout để test không treo.
6. **Trigger không có lần chạy:** xây Trigger trả `null`, xác minh `schedule(...)` có thể trả `null`.
7. **Lỗi periodic:** test cả chính sách Spring suppress mặc định và handler propagate nếu nghiệp vụ muốn dừng.
8. **Đăng ký lại cùng job ID:** bảo đảm không tạo nhiều lịch ngoài dự định khi hai caller cùng cập nhật.
9. **Shutdown:** xác minh tác vụ đã nhận và task chưa đến hạn được xử lý theo policy đã cấu hình.
10. **Nhiều node:** test hợp đồng điều phối riêng nếu dùng cluster, không giả định Future được chia sẻ.

Một test có thể dùng một scheduler chỉ một worker với latch để tạo thứ tự kiểm soát được; nhưng đừng để task trong scheduler duy nhất chờ một Future khác cùng scheduler vì có thể gây starvation deadlock.

### 22.1. Một test minh họa cancellation trong lúc task đang chạy

```java
CountDownLatch started = new CountDownLatch(1);
CountDownLatch release = new CountDownLatch(1);
CountDownLatch exited = new CountDownLatch(1);

ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(() -> {
    started.countDown();
    try {
        release.await();
    } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
    } finally {
        exited.countDown();
    }
}, Duration.ofHours(1));

if (!started.await(5, TimeUnit.SECONDS)) {
    throw new IllegalStateException("Không thấy lượt đầu");
}

boolean cancelled = handle.cancel(false);
// Ở đây handle có thể đã cancelled, nhưng task vẫn chờ release.
release.countDown();

if (!exited.await(5, TimeUnit.SECONDS)) {
    throw new IllegalStateException("Task chưa thoát");
}
```

Đây là **mảnh integration test** với `scheduler` đã được khởi tạo; method test cần xử lý `InterruptedException` và import concurrent classes. Latch `exited` tách biệt “Future đã bị hủy” với “thân Runnable đã thực sự kết thúc”. Nên dùng `try/finally` trong test thật để luôn giải phóng `release` kể cả khi assertion thất bại.

## 23. Bài tập thực hành

### Bài 1 — Phân biệt một lượt và cả lịch

Một `ScheduledFuture` từ `scheduleAtFixedRate(...)` đã có 20 lượt thành công. `isDone()` vẫn `false`. Đây có phải lỗi không?

**Đáp án định hướng:** Không. Handle đại diện cho chuỗi periodic, chưa kết thúc; số lượt thành công phải được lưu riêng.

### Bài 2 — Hủy không interrupt

Một job đang gửi tệp, caller dùng `cancel(false)` và nhận `true`. Có thể xóa ngay tài nguyên tệp được worker giữ không?

**Đáp án định hướng:** Chưa chắc. Lượt đang chạy có thể tiếp tục; cần phối hợp dừng hoàn toàn, cleanup và ownership của tài nguyên.

### Bài 3 — Hủy trước khi đến hạn

Đặt lịch one-shot sau một giờ. Hủy ngay bằng `cancel(false)`. `isDone` và `isCancelled` nên biểu diễn điều gì? `get()` hành xử ra sao?

**Đáp án định hướng:** Với Future bị hủy thành công, cả hai là `true`; `get()` ném `CancellationException` thay vì trả kết quả.

### Bài 4 — Spring khác Java thuần

Tại sao một periodic task ném exception nhưng vẫn chạy lượt sau trong `ThreadPoolTaskScheduler`, dù Java `ScheduledThreadPoolExecutor` thuần có thể dừng chuỗi?

**Đáp án định hướng:** Spring dùng error-handling wrapper; mặc định với task lặp thường log/suppress exception. Tìm `ThreadPoolTaskScheduler.setErrorHandler(...)` và `TaskUtils`.

### Bài 5 — @Scheduled và TaskScheduler

Muốn một người dùng tắt riêng lịch báo cáo của chính họ mà không dừng các lịch của người dùng khác. Chọn `@Scheduled` cố định hay API `TaskScheduler` programmatic?

**Đáp án định hướng:** Programmatic `TaskScheduler` thuận tiện hơn nếu cần lưu/cancel handle theo job ID, nhưng còn phải giải quyết concurrency và cluster ownership.

### Bài 6 — Cron Trigger nullable

Một custom Trigger không cho thời điểm kế tiếp. `scheduler.schedule(work, trigger)` trả `null`. Có nên đưa null vào registry rồi gọi `cancel(false)`?

**Đáp án định hướng:** Không. Xử lý tình huống “không có lịch” ngay từ đăng ký, trả trạng thái phù hợp cho caller.

### Bài 7 — Thiết kế graceful shutdown

Một job đang ở lượt thực thi dài, người dùng bấm “Stop”. Thiết kế hai giai đoạn sao cho không thông báo đã dừng hoàn toàn khi worker vẫn làm việc.

**Đáp án định hướng:** Cancel handle để ngừng lượt tiếp theo; theo dõi active run và đợi/quan sát thoát của lượt hiện tại, có timeout và chính sách ngắt/compensation riêng.

### Bài 8 — Chạy demo Java 17 và Spring

Chạy `ScheduledFutureCancelDemo.java` và `ScheduledHandleDemo.java`. Giải thích vì sao cả hai có thể hủy lịch tương lai, nhưng ví dụ Spring còn xử lý một lịch periodic sau khi đã có lượt đầu.

**Đáp án định hướng:** Cả hai dùng hợp đồng ScheduledFuture; ví dụ Spring trả handle từ `TaskScheduler`, dùng latch để đánh dấu lượt periodic đầu tiên trước khi hủy.

### Bài 9 — Nhiều pod

Ba pod cùng lập lịch một job. Một pod hủy Future nội bộ và trả “đã hủy toàn bộ”. Kết luận có chính xác không?

**Đáp án định hướng:** Không. Mỗi pod có scheduler và Future khác nhau. Cần cơ chế điều phối phân tán, trạng thái lưu bền vững và idempotency.

## 24. Checklist review trước khi triển khai

Khi một PR thêm lịch động có thể hủy, hãy kiểm tra:

1. Mỗi lịch có **owner** và một nơi lưu `ScheduledFuture<?>` theo job ID cục bộ.
2. Overload `TaskScheduler` được chọn đúng: one-shot, fixed rate, fixed delay hay Trigger.
3. `schedule(Runnable, Trigger)` xử lý trường hợp trả `null`.
4. `cancel(false)`/`cancel(true)` được chọn theo khả năng interruption và side effect của task.
5. Không tuyên bố “worker đã dừng” chỉ vì `isCancelled` là `true`.
6. Không gọi `get()` trên periodic Future để đợi một lượt đơn lẻ.
7. `isDone` được hiểu là terminal state của **cả Future**, không phải kết quả lượt cuối.
8. Có thống kê từng lượt chạy/thất bại nếu nghiệp vụ cần theo dõi.
9. ErrorHandler của Spring được xác định rõ; không nhầm với ngữ nghĩa lỗi của Java executor thuần.
10. `setRemoveOnCancelPolicy` được cân nhắc nếu có nhiều lịch chờ lâu bị hủy.
11. Scheduler có thread pool và chính sách shutdown được kiểm thử theo yêu cầu.
12. Không shutdown bean scheduler chung khi chỉ cần ngừng một handle.
13. Registry quản lý concurrency/replace/cancel, dọn handle đã xong và không bị rò tham chiếu.
14. Tác vụ không chặn scheduler thread quá lâu; có timeout/downstream bulkhead nếu cần.
15. Trong cluster, có cơ chế owner/leader/lease/idempotency nếu yêu cầu tránh chạy trùng.
16. Lịch quan trọng được lưu metadata ở nơi bền vững nếu phải sống qua restart.
17. Test dùng latch và Future với timeout, không phụ thuộc vào `Thread.sleep` và thứ tự in log.

## 25. Tổng kết

- **`ScheduledFuture<?>` là handle của lịch tạo bằng `TaskScheduler`**, dùng để quan sát trạng thái và yêu cầu hủy mà không cần dừng toàn bộ scheduler.
- **`isDone()` là trạng thái kết thúc Future, không phải “lượt định kỳ gần nhất thành công”.** `isCancelled()` chỉ biểu diễn trạng thái hủy của Future.
- **`cancel(false)` ngăn lượt tiếp theo nhưng không yêu cầu ngắt lượt đang chạy; `cancel(true)` chỉ cố gắng interrupt**, không bảo đảm hard stop hay rollback.
- **`getDelay(...)` là ước lượng độ trễ đến hạn**, không phải timestamp tuyệt đối, lịch sử hay SLO.
- **`get()` hữu dụng với one-shot**, nhưng có thể chờ vô hạn với chuỗi periodic còn sống; cần theo dõi từng lượt bằng state/callback riêng.
- **`schedule(task, Trigger)` có thể trả `null`**; phải xử lý trước khi lưu hoặc hủy.
- **Spring có error policy khác Java thuần ở lịch lặp**: exception có thể bị log/suppress để lịch tiếp tục, nên phải quan sát lỗi per-run.
- **Giữ handle theo job ID và quản lý lifecycle**, phân biệt cancel một job với shutdown scheduler chung; trong cluster cần giải pháp điều phối bền vững.

### Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework Javadoc — TaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Framework 6.2.7 Javadoc — ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework Javadoc — TaskUtils and ErrorHandler](https://docs.spring.io/spring-framework/docs/6.0.1/javadoc-api/org/springframework/scheduling/support/TaskUtils.html)
- [Java SE 17 Javadoc — ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html)
- [Java SE 17 Javadoc — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html)
- [Java SE 17 Javadoc — ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)
- [Java SE 17 Javadoc — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-scheduler-006">Clock của scheduler và ý nghĩa thời gian sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Clock của scheduler và ý nghĩa thời gian sử dụng

> **Chủ đề:** Spring Framework – concurrency  
> **Nhóm:** TaskScheduler  
> **Tư tưởng cốt lõi:** `TaskScheduler` có một `java.time.Clock` để xác định “bây giờ” khi tính lịch. Nhưng **thời điểm tuyệt đối** (`Instant`), **khoảng thời gian** (`Duration`), **múi giờ lịch dân sự** (`ZoneId`) và **thời gian thực thi/đợi của thread pool** không phải một thứ. Chọn đúng loại thời gian và hiểu ranh giới của `getClock()`/`setClock()` giúp tránh chạy cron sai giờ, test chập chờn và nhầm rằng chỉ cần đổi clock là đã tạo được một scheduler ảo.

Bài học lấy **Spring Framework 6.2.x + Java 17** làm bối cảnh. Ví dụ chạy độc lập có kết quả xác định; ví dụ Spring Context phân biệt **clock dùng để tính toán lịch** với **executor thật sự chạy task**. Các cú pháp minh họa không độc lập được ghi rõ.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích `TaskScheduler#getClock()` trả gì và `ThreadPoolTaskScheduler#setClock(Clock)` dùng cho mục đích gì.
2. Phân biệt `Clock`, `Instant`, `Duration`, `LocalDateTime`, `ZonedDateTime`, `ZoneId` và `System.nanoTime()` theo đúng đơn vị và ngữ nghĩa.
3. Hiểu vì sao `schedule(task, Instant)` khác `scheduleAtFixedRate(task, Duration)` và `scheduleWithFixedDelay(task, Duration)`.
4. Dự đoán ngày giờ của một `CronTrigger` khi dùng clock có múi giờ mặc định hoặc khi chỉ định `ZoneId` rõ ràng.
5. Nhận ra rủi ro do timezone server, DST, thay đổi đồng hồ hệ điều hành và task chạy chậm/lỡ lần kích hoạt.
6. Dùng `Clock.fixed`/`Clock.offset` để kiểm thử phép **tính lịch** mà không ngủ và không đợi cron thật.
7. Cấu hình bean `ThreadPoolTaskScheduler` do Spring container quản lý và quan sát kết quả schedule một lần bằng `ScheduledFuture`.
8. Phân biệt “ảo hóa giờ” trong test với một **virtual-time scheduler có quyền điều khiển hàng đợi**; không gán cho `setClock` năng lực không được API cam kết.
9. Thiết kế logging, metrics, deadline và chính sách nhiều instance cho scheduled jobs.

## 2. Vì sao scheduler cần Clock?

Một scheduler phải trả lời nhiều loại câu hỏi:

- “**Bây giờ** là thời điểm nào?” — dùng một nguồn thời gian để ra quyết định.
- “Chạy vào **09:00 sáng theo giờ Việt Nam**?” — lịch theo múi giờ dân sự, có quy tắc chuyển sang thời điểm tuyệt đối.
- “Chạy vào đúng **Instant 2026-01-01T02:00:00Z**?” — đã có timestamp tuyệt đối.
- “Lặp lại **mỗi 10 giây**?” — khoảng thời gian, không phải một giờ trong ngày.
- “Chạy tiếp **5 giây sau khi lần trước hoàn thành**?” — khoảng chờ tính từ completion, khác lịch cố định.
- “Job đã trễ **bao nhiêu ms** so với lịch?” — so sánh thời điểm thực tế với planned time.
- “Job thật sự chiếm CPU hoặc đợi I/O **bao lâu**?” — đo elapsed time, thường cần nguồn monotonic.

Nếu chỉ dùng `LocalDateTime.now()` hoặc `System.currentTimeMillis()` rải khắp code, kết quả có thể thay đổi giữa môi trường test/dev/prod, khó mô phỏng đồng hồ và dễ dựa vào timezone mặc định ngoài ý muốn. Một `Clock` được inject giúp tách phần tính toán “hiện tại là khi nào” khỏi việc gọi đồng hồ hệ thống trực tiếp.

### 2.1. Clock là một đối tượng cung cấp thời gian, không phải worker thread

`java.time.Clock` cung cấp chủ yếu:

~~~java
Instant instant();
ZoneId getZone();
Clock withZone(ZoneId zone);
~~~

`Clock` **không** có lệnh “chạy task”, không tạo thread, không quản lý queue hay job state. Một `Clock.fixed(...)` luôn báo cùng một `Instant`; điều đó có ích cho test phép tính thời gian, **không** làm CPU/OS và `ScheduledThreadPoolExecutor` tự động tiến theo clock giả lập.

### 2.2. TaskScheduler quản lý quyết định đặt lịch và thực thi

Spring `org.springframework.scheduling.TaskScheduler` là một interface để gửi `Runnable` theo một thời điểm hoặc một quy luật kích hoạt. Nó có thể sử dụng clock để hiểu thời điểm hiện tại và cung cấp clock cho `TriggerContext` khi tính thời điểm kích hoạt tiếp theo.

Bảng phân vai:

| Thành phần | Trả lời câu hỏi nào? | Không tự đảm bảo |
| --- | --- | --- |
| `Clock` | Hiện tại là Instant nào? Zone mặc định là gì? | Không chạy task, không quản lý thread |
| `TaskScheduler` | Gửi/chạy Runnable khi nào theo Instant, Duration, Trigger? | Không bảo đảm chạy chính xác từng mili-giây |
| `Trigger` | Lần kích hoạt kế tiếp nên là Instant nào? | Không tự thực thi task |
| `TriggerContext` | Clock, lần scheduled/actual/completion trước là gì? | Không lưu job bền qua restart |
| `ThreadPoolTaskScheduler` | Triển khai TaskScheduler trên scheduled executor | Không tự tạo điều phối “chỉ chạy một lần toàn cụm” |
| `ScheduledFuture<?>` | Theo dõi/cancel execution theo contract | Không phải lịch sử nghiệp vụ bền vững |

## 3. TaskScheduler API: Clock nằm ở đâu?

Với Spring 6.2.x, các phương thức chính của `TaskScheduler` có ý nghĩa tương đương:

~~~java
Clock getClock();

ScheduledFuture<?> schedule(
        Runnable task, Instant startTime);

ScheduledFuture<?> schedule(
        Runnable task, Trigger trigger);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Duration period);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, Duration period);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Duration delay);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, Duration delay);
~~~

Đây là **bảng chữ ký minh họa từ interface**, không phải một file Java độc lập.

- `getClock()`: clock mà scheduler dùng cho mục đích schedule; trong interface có default method.
- `schedule(task, Instant)`: một lần theo **thời điểm tuyệt đối**; nếu thời điểm đã qua, implementation thông thường chạy càng sớm càng tốt.
- `schedule(task, Trigger)`: trigger chọn các `Instant` lần sau dựa trên `TriggerContext`, phù hợp với cron và rule tùy biến.
- `scheduleAtFixedRate`: diễn đạt **nhịp bắt đầu theo chu kỳ**, tùy số worker và thời lượng task mà lần chạy thực tế có thể muộn.
- `scheduleWithFixedDelay`: diễn đạt **thời gian chờ sau khi lần trước hoàn thành** trước khi khởi động lần kế tiếp.

API `TaskScheduler` không nên bị nhầm với `TaskExecutor`: `TaskExecutor.execute` nhận task chạy theo cơ chế executor, không cung cấp một `Instant`/`Trigger` để quyết định tương lai.

### 3.1. Clock mặc định

Javadoc của `TaskScheduler#getClock()` nói clock dùng cho mục đích scheduling; default clock là clock hệ thống của timezone mặc định. `ThreadPoolTaskScheduler` công bố:

~~~java
Clock getClock();
void setClock(Clock clock);
~~~

Trong `ThreadPoolTaskScheduler`, mặc định là tương đương `Clock.systemDefaultZone()` trừ khi cấu hình khác. **Không** cho rằng mọi `TaskScheduler` tùy biến luôn dùng cùng mặc định: hãy kiểm tra class thực tế và cấu hình của app.

Ví dụ:

~~~java
@Configuration(proxyBeanMethods = false)
class SchedulerConfig {
    @Bean("businessScheduler")
    ThreadPoolTaskScheduler businessScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setClock(Clock.systemUTC());
        s.setThreadNamePrefix("business-schedule-");
        return s;
    }
}
~~~

Đây là **snippet Spring**, cần imports cho `@Configuration`, `@Bean`, `ThreadPoolTaskScheduler` và `Clock`. Khi là bean, Spring khởi tạo/hủy scheduled executor theo lifecycle. `Clock.systemUTC()` làm clock có zone UTC, chứ không tự bắt buộc tất cả cron trong hệ thống phải chạy UTC: mỗi `CronTrigger` có thể chỉ định zone riêng và annotation `@Scheduled` cũng có thuộc tính `zone`.

### 3.2. Clock của scheduler không đồng nghĩa timezone của mọi business event

Giả sử người dùng có lịch “09:00 sáng tại Hà Nội”. Đó là một quy tắc theo `ZoneId.of("Asia/Ho_Chi_Minh")`. Nếu scheduler dùng `Clock.systemUTC()`, **vẫn có thể** chạy theo 09:00 giờ Việt Nam bằng cách chỉ định zone cho cron. Múi giờ của clock là **mặc định khi không có zone rõ ràng**, không phải một hạn chế bắt buộc của lịch.

Ngược lại, một `Clock.systemDefaultZone()` trên máy chủ ở UTC không thể tự suy rằng người dùng muốn 09:00 giờ Việt Nam. Khi yêu cầu là lịch theo địa phương, hãy lưu zone hợp lệ theo business identity; đừng dựa vào timezone của pod/node.

## 4. Phân biệt sáu khái niệm thời gian

### 4.1. Instant — một điểm tuyệt đối trên timeline

`Instant` biểu diễn một điểm trên timeline UTC. Ví dụ:

~~~text
2026-01-01T02:00:00Z
~~~

Đó là cùng một **thời điểm thực** khi hiển thị:

~~~text
2026-01-01 09:00 tại Asia/Ho_Chi_Minh (+07:00)
2026-01-01 02:00 tại UTC
~~~

Tạo `Instant`:

~~~java
Instant when = Instant.parse("2026-01-01T02:00:00Z");
scheduler.schedule(job, when);
~~~

**Snippet**: `scheduler` và `job` phải được khai báo. Sử dụng `Instant` cho ý nghĩa “chạy tại đúng thời điểm tuyệt đối này”.

### 4.2. Duration — một khoảng thời gian

`Duration.ofSeconds(10)` biểu diễn một **độ dài 10 giây**, không chứa ngày, giờ địa phương hay timezone:

~~~java
Duration period = Duration.ofSeconds(10);
scheduler.scheduleAtFixedRate(job, period);
~~~

**Snippet**: không nói “10 giờ sáng” hay “mỗi ngày lúc 09:00”. Nếu muốn lịch 09:00 địa phương mỗi ngày, dùng cron/zone; đừng mặc định `Duration.ofHours(24)` tương đương một ngày theo lịch ở mọi timezone.

### 4.3. LocalDateTime — thời gian dân sự thiếu zone

`LocalDateTime.of(2026, 1, 1, 9, 0)` nghĩa là “09:00 trên một lịch địa phương” **chưa có thông tin nó nằm ở đâu**. Vì vậy, nó không thể tự xác định một `Instant` nếu không chọn `ZoneId` hoặc offset:

~~~java
LocalDateTime local =
        LocalDateTime.of(2026, 1, 1, 9, 0);
ZoneId city = ZoneId.of("Asia/Ho_Chi_Minh");
Instant instant = local.atZone(city).toInstant();
~~~

**Snippet Java 17**. Dùng `LocalDateTime` cho form input kiểu “ngày/giờ”, sau đó **bắt buộc** kết hợp với zone được xác thực trước khi gửi vào `schedule(..., Instant)`.

### 4.4. ZonedDateTime — local date/time + ZoneId

`ZonedDateTime` mang thông tin quy tắc timezone đầy đủ: offset theo mùa, DST nếu có, chuyển đổi theo lịch sử/chính sách. Dùng `ZoneId.of("America/New_York")` sẽ khác `ZoneOffset.of("-05:00")` trong giai đoạn mùa hè vì New York có DST còn fixed offset thì không.

### 4.5. Clock — “now” có thể thay thế được

`Instant.now(clock)` và `LocalDate.now(clock)` giúp viết code test có kết quả xác định:

~~~java
Clock clock = Clock.fixed(
        Instant.parse("2026-01-01T00:00:00Z"),
        ZoneId.of("Asia/Ho_Chi_Minh"));

Instant now = Instant.now(clock);
LocalDate today = LocalDate.now(clock);
~~~

**Snippet**: đồng hồ luôn báo mốc đã chọn. Không có thread tự chạy khi bạn gọi `clock.instant()`. `Clock.withZone(...)` tạo một clock cùng timeline nhưng khác zone trình bày/diễn giải; nó không tự “đẩy giờ” lên 7 tiếng nếu hiện tại vẫn là cùng Instant.

### 4.6. System.nanoTime — đo thời gian trôi qua

Nếu mục tiêu là đo “job đã chạy bao lâu”, dùng:

~~~java
long startNanos = System.nanoTime();
try {
    doWork();
} finally {
    long elapsedNanos = System.nanoTime() - startNanos;
    recordDuration(elapsedNanos);
}
~~~

Đây là **snippet** với `doWork` và `recordDuration` do ứng dụng cung cấp. `System.nanoTime()` dành cho chênh lệch thời gian trong cùng một JVM, không phải Unix timestamp và không được serialize làm schedule time. Đừng convert `nanoTime()` thành ngày/giờ UTC.

## 5. Java 17 chạy độc lập: một Instant, hai timezone và Clock.fixed

Tạo tệp **`ClockSemanticsDemo.java`**:

~~~java
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class ClockSemanticsDemo {
    public static void main(String[] args) {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        ZoneId hcm = ZoneId.of("Asia/Ho_Chi_Minh");
        Clock vietnamClock = Clock.fixed(base, hcm);
        Clock utcClock = vietnamClock.withZone(ZoneOffset.UTC);

        System.out.println("instant = " + vietnamClock.instant());
        System.out.println("same instant = "
                + vietnamClock.instant().equals(utcClock.instant()));
        System.out.println("HCM date = " + LocalDate.now(vietnamClock));
        System.out.println("HCM local = "
                + LocalDateTime.now(vietnamClock));
        System.out.println("UTC local = "
                + LocalDateTime.now(utcClock));

        ZonedDateTime nineAm = ZonedDateTime.of(
                LocalDate.of(2026, 1, 1),
                LocalTime.of(9, 0),
                hcm);
        System.out.println("09 HCM in UTC = " + nineAm.toInstant());

        Duration wait = Duration.between(
                vietnamClock.instant(), nineAm.toInstant());
        System.out.println("until 09 HCM = " + wait);

        Clock shifted =
                Clock.offset(vietnamClock, Duration.ofMinutes(30));
        System.out.println("offset instant = " + shifted.instant());
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac ClockSemanticsDemo.java
java ClockSemanticsDemo
~~~

**Stdout xác định**:

~~~text
instant = 2026-01-01T00:00:00Z
same instant = true
HCM date = 2026-01-01
HCM local = 2026-01-01T07:00
UTC local = 2026-01-01T00:00
09 HCM in UTC = 2026-01-01T02:00:00Z
until 09 HCM = PT2H
offset instant = 2026-01-01T00:30:00Z
~~~

Điểm cần thấy:

1. `vietnamClock` và `utcClock` có **cùng Instant** mặc dù `getZone()` khác nhau.
2. Local time 07:00 tại Việt Nam chính là 00:00 UTC ở ví dụ này.
3. 09:00 giờ Việt Nam là 02:00 UTC cùng ngày; số `Instant` không cộng 7 giờ lần nữa khi schedule.
4. `Duration.between` cho một khoảng chờ **2 giờ** từ now đến lúc chạy.
5. `Clock.offset` dịch mốc do clock báo thêm 30 phút để **test tính toán**, không thực sự trôi 30 phút trên hệ điều hành.

**Không phải Spring demo:** đây là ví dụ Java SE thuần để hiểu các kiểu thời gian. Ví dụ Spring Context ở mục 11 dùng `TaskScheduler` thật và `CronTrigger` trên clock cố định để test **calculation**.

## 6. TriggerContext: clock truyền vào phép tính lần chạy sau

Spring `Trigger` có phương thức:

~~~java
Instant nextExecution(TriggerContext triggerContext);
~~~

`TriggerContext` cung cấp:

~~~java
Clock getClock();
Instant lastScheduledExecution();
Instant lastActualExecution();
Instant lastCompletion();
~~~

Những phương thức này có vai trò khác nhau:

- **`getClock()`:** “hiện tại/zone” để tính lần tiếp theo, nhất là khi chưa có lịch sử.
- **`lastScheduledExecution()`:** thời điểm đã định schedule ở lần trước; không nhất thiết là lúc worker thực sự vào `run()`.
- **`lastActualExecution()`:** lúc task thực sự bắt đầu ở lần trước.
- **`lastCompletion()`:** lúc task hoàn thành ở lần trước.

Tại sao cần phân biệt? Một task dự kiến chạy 09:00 nhưng pool bận đến 09:03 mới bắt đầu, hoàn thành 09:05. Lịch tiếp theo dựa trên **scheduled 09:00** có thể khác lịch dựa trên **completion 09:05**.

~~~text
09:00      09:03           09:05
  |          |               |
planned    actual          completed
  |          |               |
lastScheduledExecution()    lastCompletion()
             |
      lastActualExecution()
~~~

Sơ đồ là **một kịch bản minh họa**, không phải giả định mọi job đều muộn 3 phút.

### 6.1. CronTrigger dùng clock và lịch sử

Với `CronTrigger`, Spring lấy clock/zone trong trigger context khi cần. Nếu không cung cấp zone riêng cho cron, việc tính lịch dựa trên timezone của clock trong `TriggerContext`. Nếu truyền `ZoneId` rõ ràng trong constructor, zone đó được dùng để diễn giải cron.

Cron thông thường có 6 trường:

~~~text
second minute hour day-of-month month day-of-week
0      0      9    *            *     *
~~~

Biểu thức `"0 0 9 * * *"` tức 09:00 mỗi ngày trong timezone đang được dùng; nó **không tự có UTC**.

### 6.2. Context không tự lưu trạng thái bền vững

`SimpleTriggerContext` là holder đơn giản dùng trong tính toán/test. Một scheduler thật theo dõi execution context cho trigger trong runtime của nó. Nếu process restart, các thông tin in-memory trước đó có thể mất; đừng suy scheduler có lịch sử công việc bền vững hoặc replay tất cả missed fires sau deploy. Nếu nghiệp vụ cần lịch sử/leader election/persistence, cần giải pháp job scheduler bền vững có storage phù hợp.

## 7. CronTrigger và tác động của timezone

Giả sử biểu thức:

~~~text
0 0 9 * * *
~~~

Nếu dùng clock mặc định của scheduler đang ở **UTC**, nó diễn giải thành 09:00 UTC (tức 16:00 tại Việt Nam cho ngày trong ví dụ). Nếu dùng timezone **Asia/Ho_Chi_Minh**, nó diễn giải thành 09:00 Việt Nam (02:00 UTC).

Một cách rõ ràng:

~~~java
ZoneId vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
CronTrigger businessNine = new CronTrigger(
        "0 0 9 * * *", vietnam);

scheduler.schedule(
        () -> sendDailyReport(),
        businessNine);
~~~

Đây là **Spring snippet**, cần import `ZoneId`, `CronTrigger`, `TaskScheduler` và nghiệp vụ `sendDailyReport()`. Zone được khai báo tường minh nên lịch không bị thay đổi chỉ vì timezone của container khác.

### 7.1. Có nên chỉnh systemDefaultZone của JVM?

Chỉnh `user.timezone` cho cả JVM có thể ảnh hưởng logging, serialization, database client, cron mặc định và code gọi `LocalDate.now()`. Nếu một job cần chạy theo giờ Việt Nam, cách an toàn hơn là **chỉ định ZoneId của job hoặc scheduler** thay vì thay đổi timezone toàn process mà không đánh giá các subsystem khác.

Với multi-tenant, mỗi tenant có zone riêng, nên lưu zone theo tenant. Một global scheduler clock dùng UTC vẫn hoàn toàn có thể schedule từng cron theo zone khác nhau.

### 7.2. Asia/Ho_Chi_Minh và DST

Trong giai đoạn hiện tại, `Asia/Ho_Chi_Minh` có offset UTC+07 và không có chu kỳ DST thường niên. Nhưng một lịch toàn cầu phải xét zone có DST như `America/New_York`.

- **Spring forward (gap):** một local time như 02:30 có thể **không tồn tại** trong ngày chuyển DST.
- **Fall back (overlap):** một local time như 01:30 có thể xuất hiện **hai lần** theo hai offset.
- Cron theo local clock cần có business policy về bỏ qua, chạy một lần hay xử lý hai occurrence. Hành vi cụ thể phụ thuộc thuật toán trigger/version và lịch sử; không suy rằng mọi cron engine có cùng cách xử lý DST.
- `ZoneOffset.of("-05:00")` chỉ là offset cố định; nó **không giữ quy tắc DST của New York** qua các tháng.

Với báo cáo tài chính “một lần mỗi ngày”, thiết kế dedup theo **business date + zone + job key** quan trọng không kém việc chọn cron đúng. Nếu mất một lịch do downtime/DST, cần biết có backfill hay không.

## 8. Clock.fixed trong test: nên đặt ở đâu?

Khi kiểm thử “cron 09:00 Việt Nam sẽ chạy vào Instant nào”, bạn **không cần mở thread scheduler**. Tạo `Clock.fixed` rồi đưa vào một `SimpleTriggerContext`:

~~~java
Clock fixed = Clock.fixed(
        Instant.parse("2026-01-01T00:00:00Z"),
        ZoneId.of("Asia/Ho_Chi_Minh"));

SimpleTriggerContext context = new SimpleTriggerContext(fixed);
CronTrigger trigger = new CronTrigger("0 0 9 * * *");

Instant next = trigger.nextExecution(context);
// Kết quả kỳ vọng: 2026-01-01T02:00:00Z
~~~

Đây là **Spring snippet**, cần imports `Clock`, `Instant`, `ZoneId`, `SimpleTriggerContext` và `CronTrigger`. Vì `context` có clock cố định đang báo 07:00 tại Việt Nam, cron 09:00 của cùng ngày tính ra **02:00Z**. Không cần sleep hoặc đợi đến 09:00 thực.

### 8.1. Test zone explicit khác zone của clock

~~~java
Clock fixedHcm = Clock.fixed(
        Instant.parse("2026-01-01T00:00:00Z"),
        ZoneId.of("Asia/Ho_Chi_Minh"));

SimpleTriggerContext ctx = new SimpleTriggerContext(fixedHcm);

Instant localNine = new CronTrigger(
        "0 0 9 * * *").nextExecution(ctx);

Instant utcNine = new CronTrigger(
        "0 0 9 * * *",
        ZoneId.of("UTC")).nextExecution(ctx);
~~~

**Snippet**. Kỳ vọng `localNine = 2026-01-01T02:00:00Z` và `utcNine = 2026-01-01T09:00:00Z`. Hai cron có cùng chuỗi nhưng zone khác → hai `Instant` khác nhau.

### 8.2. Vì sao không set Clock.fixed trực tiếp lên scheduler thật rồi đợi nó chạy?

Đây là lỗi thiết kế test thường gặp:

~~~java
ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setClock(Clock.fixed(testInstant, ZoneId.of("UTC")));
scheduler.initialize();
scheduler.schedule(() -> doWork(), testInstant.plusSeconds(10));
// Sai nếu kỳ vọng gọi Clock.offset() sẽ tự khiến task chạy.
~~~

Đây là **snippet phản ví dụ**; cần `testInstant` và `doWork`, và phải shutdown scheduler nếu đã khởi tạo. Sau khi đã schedule, **underlying scheduled executor** vẫn quản lý delayed tasks theo cơ chế thời gian thực của JVM. Đổi biến Clock trong test không có nghĩa queue của scheduler có thể được đẩy tới tương lai theo yêu cầu. `Clock.fixed` không tự tiến; `Clock.offset(...)` tạo **clock mới**, không làm clock cũ hoặc queue tự cập nhật.

**Quy tắc:** unit test thuần **tính toán trigger** dùng fixed clock; test execution thực dùng scheduler thật + synchronization (future/latch), hoặc dùng **virtual scheduler/test harness** có khả năng điều khiển advancement của queue một cách rõ ràng.

## 9. schedule(Runnable, Instant): biến “lúc nào” thành task

Một lời gọi:

~~~java
Instant start = Instant.now(clock).plusSeconds(30);
ScheduledFuture<?> future = scheduler.schedule(job, start);
~~~

**Snippet**: `clock`, `scheduler` và `job` là dependency đã khai báo. Hàm `schedule` nhận **Instant tuyệt đối**, không nhận một timestamp tính theo timezone của máy.

### 9.1. Khi Instant đã ở trong quá khứ

Theo Javadoc Spring, nếu `startTime` đã qua, task được chạy **ngay khi có thể** chứ không được quay về quá khứ. “Ngay khi có thể” không đồng nghĩa ngay lập tức trên caller, vì còn scheduling và worker availability.

Trong demo Spring Context ở mục 11, ta cố ý đặt `Instant.now(clock).minusSeconds(1)` và đợi `ScheduledFuture.get(timeout)` để kiểm tra task thật sự hoàn thành.

### 9.2. Thay đổi giờ hệ điều hành

Khi ứng dụng schedule một Instant trong tương lai, implementation có thể chuyển nó thành delay dựa vào clock hiện tại tại thời điểm đặt lịch. Executor nền có thể dựa trên một bộ đếm elapsed time tương đối. Vì thế, thay đổi wall-clock **sau khi task đã được đưa vào queue** không nhất thiết làm thời gian chờ đã tính trước được điều chỉnh theo đúng mức đổi. Điều này phụ thuộc implementation và kiểu trigger (one-shot hay phải tính lịch lại).

**Không dựa vào** giả định “scheduler luôn chạy đúng hh:mm:ss dù NTP hoặc admin chỉnh đồng hồ hệ thống” cho những yêu cầu chính xác tuyệt đối; cân nhắc đồng bộ clock, đo schedule lag và xử lý late/missed triggers theo business policy.

## 10. Fixed rate, fixed delay và cron: ý nghĩa thời gian khác nhau

| Hình thức | Tham số thời gian | Cách hiểu | Phụ thuộc timezone? |
| --- | --- | --- | --- |
| `schedule(task, Instant)` | `Instant` | Một execution dự định tại timestamp tuyệt đối | Không để xác định timestamp; có thể phụ thuộc clock khi tính delay |
| `scheduleAtFixedRate(task, Duration)` | `Duration` | Lặp theo nhịp/period từ thời điểm bắt đầu | Không phải quy luật giờ địa phương |
| `scheduleWithFixedDelay(task, Duration)` | `Duration` | Đợi một khoảng sau khi task trước hoàn thành | Không phải quy luật giờ địa phương |
| `schedule(task, Trigger)` với `CronTrigger` | Chuỗi cron + `ZoneId` | Tính các giờ/ngày dân sự rồi đổi sang `Instant` | Có, nếu biểu thức chứa lịch/giờ địa phương |
| `@Scheduled(cron=..., zone=...)` | Cron + zone | Lịch declarative trong Spring | Có, theo zone được chọn |
| `@Scheduled(fixedDelayString=...)` | Thời lượng | Sau khi hoàn thành, chờ delay | Không phải cron địa phương |

### 10.1. Fixed rate

Ví dụ:

~~~java
scheduler.scheduleAtFixedRate(
        () -> pollMetrics(),
        Duration.ofSeconds(10));
~~~

**Snippet**, `scheduler` và `pollMetrics()` thuộc ứng dụng. Mục tiêu là nhịp khoảng 10 giây giữa các **thời điểm bắt đầu theo lịch**. Nếu task chạy 15 giây hoặc pool bão hòa, execution có thể bị trễ; không được ngầm hứa scheduler tạo thêm thread vô hạn để thực hiện đúng từng tick.

Với `ThreadPoolTaskScheduler` dựa trên `ScheduledThreadPoolExecutor`, các lần execution của **cùng một periodic task** không chạy đè lên nhau theo contract JDK; những lần chạy muộn có thể nối tiếp sát nhau. Với **nhiều task/annotation khác nhau**, chúng có thể chồng lấp nếu pool có worker và workload cho phép.

### 10.2. Fixed delay

~~~java
scheduler.scheduleWithFixedDelay(
        () -> pollMetrics(),
        Duration.ofSeconds(10));
~~~

**Snippet**. Nếu một lần chạy bắt đầu 09:00, mất 5 giây và hoàn thành 09:00:05, lần sau **không** bắt đầu trước khoảng 09:00:15 theo ý nghĩa fixed delay. Nếu lần trước mất 20 giây thì lần sau càng muộn. Đây là cơ chế phù hợp khi muốn “nghỉ” giữa các lần hoàn thành.

### 10.3. Cron

~~~java
scheduler.schedule(
        () -> generateDailySummary(),
        new CronTrigger(
                "0 0 9 * * *",
                ZoneId.of("Asia/Ho_Chi_Minh")));
~~~

**Snippet**. Cron biểu diễn **09:00 theo lịch của zone**, không phải cứ đúng 24 giờ kể từ lần chạy trước. Khi DST áp dụng ở zone khác, một “ngày dân sự” có thể dài 23 hoặc 25 giờ tính theo elapsed time.

### 10.4. Sơ đồ minh họa

Giả sử task mất 7 giây và period/delay được cấu hình là 5 giây:

~~~text
fixed-rate target:  00s  05s  10s  15s  20s ...
task run 1:         [---------7s---------]
task run 2:                    [---------7s---------]
=> start thực tế có thể trễ lịch, tùy pool/implementation

fixed-delay:
task run 1:         [---------7s---------]
chờ:                                  [---5s---]
task run 2:                                     [---------7s---------]
=> khoảng chờ đo từ completion
~~~

Sơ đồ chỉ minh họa quan hệ, không phải kết quả timing cứng của một môi trường cụ thể. Để kiểm chứng, hãy ghi `scheduled/actual/completion` và đo chênh lệch.

## 11. Spring Context demo Java 17: clock thật để chạy, clock cố định để tính lịch

Ví dụ sau **chạy được** chỉ với `spring-context`, không cần Spring Boot/Web server. Nó chứng minh:

1. `ThreadPoolTaskScheduler` là Spring bean được container quản lý.
2. `setClock(Clock.systemUTC())` làm scheduler công bố zone UTC.
3. Một one-shot task có `Instant` trong quá khứ được chạy càng sớm càng tốt; `ScheduledFuture.get` đồng bộ hóa kết quả.
4. `SimpleTriggerContext(Clock.fixed(...))` tính cron 09:00 theo timezone Việt Nam mà **không khởi chạy worker**.
5. Cron với zone UTC rõ ràng tạo `Instant` khác, dù cùng biểu thức.

### 11.1. Cấu trúc dự án

~~~text
spring-clock-demo/
├── pom.xml
└── src/main/java/demo/SchedulerClockDemo.java
~~~

Tệp **`pom.xml`**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-clock-demo</artifactId>
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

Tệp **`src/main/java/demo/SchedulerClockDemo.java`**:

~~~java
package demo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

public class SchedulerClockDemo {

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean("businessScheduler")
        ThreadPoolTaskScheduler businessScheduler() {
            ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
            s.setPoolSize(1);
            s.setClock(Clock.systemUTC());
            s.setThreadNamePrefix("clock-demo-");
            return s; // Spring gọi initialize và destroy.
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {

            TaskScheduler scheduler =
                    context.getBean("businessScheduler", TaskScheduler.class);

            System.out.println("scheduler zone = "
                    + scheduler.getClock().getZone());

            // Mốc đã qua -> one-shot task chạy ASAP, không in từ worker.
            Instant past = scheduler.getClock().instant().minusSeconds(1);
            ScheduledFuture<?> future = scheduler.schedule(() -> { }, past);
            if (future == null) {
                throw new IllegalStateException("no task scheduled");
            }
            future.get(5, TimeUnit.SECONDS);
            System.out.println("past task done = " + future.isDone());

            Clock fixed = Clock.fixed(
                    Instant.parse("2026-01-01T00:00:00Z"),
                    ZoneId.of("Asia/Ho_Chi_Minh"));

            SimpleTriggerContext triggerContext =
                    new SimpleTriggerContext(fixed);

            CronTrigger defaultZoneCron =
                    new CronTrigger("0 0 9 * * *");
            CronTrigger utcCron =
                    new CronTrigger(
                            "0 0 9 * * *",
                            ZoneId.of("UTC"));

            System.out.println("HCM nine = "
                    + defaultZoneCron.nextExecution(triggerContext));
            System.out.println("UTC nine = "
                    + utcCron.nextExecution(triggerContext));
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.SchedulerClockDemo
~~~

**Stdout do class demo in khi chạy thành công**, không tính log riêng của Maven/Spring:

~~~text
scheduler zone = Z
past task done = true
HCM nine = 2026-01-01T02:00:00Z
UTC nine = 2026-01-01T09:00:00Z
~~~

### 11.2. Vì sao stdout không phụ thuộc thời gian hệ thống?

- Zone `Z` đến từ `Clock.systemUTC()`, không đến từ default timezone của máy.
- `past` được tính bằng scheduler clock thật rồi lùi 1 giây; tại thời điểm schedule, nó đã thuộc quá khứ. `future.get(5, TimeUnit.SECONDS)` chờ task hoàn tất rồi mới kiểm tra `isDone`; timeout là guard khi môi trường có vấn đề.
- Hai phép tính cron dùng `Clock.fixed` tại thời điểm 2026-01-01T00:00Z và zone rõ ràng. Chúng không dựa vào current date; không cần chờ đến một giờ thật.
- `CronTrigger` không được gửi tới scheduler để chạy; chỉ hỏi `nextExecution` với context giả lập nên kết quả deterministic.
- Context tự hủy `ThreadPoolTaskScheduler` theo vòng đời Spring khi đóng; không bỏ lại một executor tự tạo chưa shutdown.

**Điều không chứng minh:** `Clock.fixed` không làm cho one-shot task chạy ở một “thời gian giả”. One-shot task ở ví dụ dùng `Clock.systemUTC` **thật**, còn fake clock chỉ để kiểm tra việc tính cron.

## 12. @Scheduled và múi giờ của ứng dụng

Spring `@Scheduled` cho phép khai báo lịch ở method bean, nếu đã bật `@EnableScheduling`:

~~~java
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {
}
~~~

~~~java
@Component
class DailyReportJob {

    @Scheduled(
            cron = "0 0 9 * * *",
            zone = "Asia/Ho_Chi_Minh")
    public void runDaily() {
        generateDailyReport();
    }
}
~~~

Đây là **hai class snippet Spring**, cần imports `@Configuration`, `@EnableScheduling`, `@Component`, `@Scheduled` và method `generateDailyReport`. Nếu hệ thống có nhiều bean `TaskScheduler`, phải cấu hình lựa chọn scheduler theo hợp đồng của Spring; không tin rằng annotation tự dùng đúng bean bạn muốn.

### 12.1. Khi có @Scheduled(zone = ...)

`@Scheduled(cron = ..., zone = "Asia/Ho_Chi_Minh")` nêu timezone cho cách tính **cron expression**. Vì thế chạy trên máy chủ UTC hay Asia/Singapore vẫn có cùng quy tắc lịch 09:00 Việt Nam, với điều kiện clock/timezone database và system configuration hoạt động nhất quán.

### 12.2. Khi bỏ zone

~~~java
@Scheduled(cron = "0 0 9 * * *")
public void runDaily() { /* business code */ }
~~~

**Snippet**. Lịch sẽ dùng timezone mặc định của scheduler/clock theo cơ chế Spring, vì thế môi trường deploy khác nhau có thể diễn giải giờ 09:00 khác nhau. Nếu business yêu cầu một zone xác định, hãy ghi thẳng `zone` hoặc cấu hình clock/trigger một cách rõ ràng.

### 12.3. Fixed delay và fixed rate không phải cron địa phương

~~~java
@Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
public void pollAfterCompletion() { /* poll */ }

@Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
public void pollOnCadence() { /* poll */ }
~~~

Đây là **method snippets**, yêu cầu imports và cấu hình Spring. Chúng là khoảng thời gian tương đối, không có “giờ 09:00 theo một timezone”. `zone` là thông số của cron, không phải một công tắc biến fixed-rate thành wall-clock calendar schedule.

**Lưu ý về Spring MVC/Boot:** `@EnableScheduling` cần được bật ở application context thích hợp. Nếu bạn chỉ thêm method annotation nhưng không kích hoạt scheduling hoặc bean không được container quản lý, task sẽ không chạy. Spring Boot có thể auto-configure scheduler theo điều kiện cụ thể; đừng giả định mọi ứng dụng Boot mặc nhiên dùng pool size hoặc timezone như nhau.

## 13. Clock.systemUTC và zone business: chiến lược thực tế

Một thiết kế dễ lý giải là:

- **Clock chung của scheduler** có zone UTC (`Clock.systemUTC()`), tránh phụ thuộc timezone của container.
- Mỗi rule cron của business có **ZoneId explicit**, ví dụ `Asia/Ho_Chi_Minh`, `Europe/Berlin`, `America/New_York`.
- Tất cả planned/actual/completion timestamps lưu/log ở **Instant UTC**.
- UI chuyển từ Instant sang local datetime theo zone của user/tenant.
- Đối với thao tác “hôm nay của tenant”, dùng `LocalDate.now(clock.withZone(tenantZone))` hoặc chuyển từ Instant, chứ không dùng `LocalDate.now()` mặc định.
- Kiểm thử fixed clock và timezone boundary độc lập với scheduler thật.

Ví dụ chuyển từ business date/time sang Instant:

~~~java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
LocalDate date = LocalDate.of(2026, 1, 1);
LocalTime time = LocalTime.of(9, 0);

ZonedDateTime target = ZonedDateTime.of(date, time, zone);
Instant triggerAt = target.toInstant();
// triggerAt = 2026-01-01T02:00:00Z
~~~

**Snippet**, cần import các type `java.time`. Mỗi lịch có thể lưu `zoneId` để xử lý DST theo quy tắc tương ứng. Không lưu mỗi một offset hiện tại cho lịch “09:00 mỗi ngày” tại nơi có DST.

### Clock cho business code có nên là scheduler.getClock()?

Nếu muốn application nhất quán, có thể inject một `Clock` bean vào service tính “ngày hôm nay” và vào scheduler configuration. Tuy nhiên, ranh giới ownership cần rõ:

- Clock của scheduler quyết định những phép tính scheduling mà implementation sử dụng.
- Business clock có thể cùng nguồn nhưng **được inject tường minh** thay vì tự truy cập scheduler ở mọi service.
- Test có thể thay business clock trong unit test mà không cần đổi scheduler thật.
- Nếu hai clock khác nhau, phải có lý do rõ và log/metric phân biệt, tránh dùng business clock fixed trong production.

Ví dụ:

~~~java
@Bean
Clock appClock() {
    return Clock.systemUTC();
}

@Bean
ThreadPoolTaskScheduler taskScheduler(Clock appClock) {
    ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
    s.setClock(appClock);
    s.setPoolSize(2);
    return s;
}
~~~

Đây là **Spring configuration snippet** trong class `@Configuration`, chưa phải file độc lập. Các bean dùng clock chung trong production; test chọn fake business clock hoặc trực tiếp test trigger context, tùy mục đích.

## 14. Wall clock khác elapsed-time clock

**Wall clock** (`Clock.systemUTC()`, `Clock.systemDefaultZone()`, `Instant.now()`) dùng để gắn thời điểm vào lịch dân sự và timestamp có thể lưu. Nó có thể bị điều chỉnh bởi NTP, cấu hình hệ thống, môi trường ảo hoặc thao tác quản trị. Vì vậy, hai lần đọc wall clock có thể cách nhau không đúng số nanosecond đã trôi.

**Elapsed-time / monotonic source** như `System.nanoTime()` dùng để so sánh khoảng thời gian trong JVM. Số trả về không phải “nanos kể từ 1970” và không thể đem sang process khác so sánh thành một timestamp.

### 14.1. Đừng dùng Clock để tự đo thời lượng chính xác

Không nên tính elapsed runtime nhạy cảm bằng:

~~~java
long startMs = System.currentTimeMillis();
doWork();
long elapsedMs = System.currentTimeMillis() - startMs;
~~~

Snippet này có thể bị ảnh hưởng khi đồng hồ hệ thống được chỉnh trong lúc làm việc. Nên đo chênh thời gian bằng `System.nanoTime()` khi phù hợp, hoặc Micrometer `Timer`/metric library dùng clock/monotonic mechanism của nó.

### 14.2. JDK scheduled executor sử dụng cơ chế trì hoãn tương đối

`ScheduledThreadPoolExecutor` của JDK sử dụng delayed tasks, tính toán theo bộ đếm elapsed-time thích hợp cho timeouts/delays (ví dụ `System.nanoTime` bên dưới). Việc thiết lập Spring `Clock` cho scheduler có thể ảnh hưởng cách chọn/so sánh **Instant lúc tạo lịch**, nhưng không đồng nghĩa thay đổi cơ chế thời gian của queue executor thành một “đồng hồ có thể điều khiển” bằng `Clock.fixed`.

Hệ quả vận hành: nếu hệ điều hành nhảy giờ, one-shot đã được chuyển thành delay và trigger phải tính lần kế tiếp **có thể phản ứng khác nhau**. Đừng suy đoán một chính sách catch-up/skip chung cho tất cả implementation; hãy test trên loại scheduler đang dùng.

## 15. CronTrigger: lenient và fixed execution

Một khái niệm không trực tiếp nằm trong `Clock` nhưng quan trọng khi hỏi “thời gian sử dụng” là **mốc làm căn cứ cho lần tiếp theo**.

Với Spring 6.2.x, `CronTrigger` thông thường dùng semantics **lenient execution**: lần kế tiếp có thể dựa trên thời điểm **completion** của lần trước, vì thế những mốc cron bị lỡ trong khi task còn chạy có thể bị bỏ qua.

Spring cũng có `CronTrigger.forFixedExecution(expression)` (khả dụng trong Spring 6.1.3+) để tính theo thời điểm **scheduled** của lần trước, nhằm xử lý các mốc cron đã lỡ theo chính sách fixed execution. Cả hai đều cần xem xét trường hợp process down, pool bão hòa và yêu cầu chạy bù:

~~~java
CronTrigger lenient =
        new CronTrigger("0 */5 * * * *");

CronTrigger fixed =
        CronTrigger.forFixedExecution("0 */5 * * * *");
~~~

Đây là **snippet**. Hai trigger có thể cho các chuỗi `nextExecution` khác nhau khi một job mất nhiều thời gian. Đây **không** có nghĩa một Spring scheduler in-memory trở thành job store bền vững sau restart.

### 15.1. Phân biệt “skip một tick” và “đảm bảo không mất job”

Một báo cáo cần chạy **mỗi 5 phút** có thể chấp nhận skip nếu hệ thống bận. Một nghiệp vụ tính cước cần xử lý **mỗi billing period** không thể chỉ dựa vào cron fire count. Nên lưu business key theo kỳ, trạng thái xử lý và idempotency trong DB/job store, để recovery/backfill có thể kiểm tra.

### 15.2. Clock giả không tự quyết định catch-up

Dù thay clock, một trigger vẫn cần **execution context/history** và policy. `Clock.fixed` giúp bạn test lúc “now” nhưng không tự tạo record `lastCompletion` hay replay lần chạy chưa diễn ra. Unit test cần cung cấp `SimpleTriggerContext.update(lastScheduled, lastActual, lastCompletion)` khi test mốc lịch sử.

## 16. Test TriggerContext với lịch sử thời gian

Giả sử đã có:

~~~text
lastScheduled = 2026-01-01T02:00:00Z
lastActual    = 2026-01-01T02:02:00Z
lastComplete  = 2026-01-01T02:04:00Z
~~~

Các mốc lần trước khác nhau vì pool trễ 2 phút và task chạy thêm 2 phút. Khi test, cần làm rõ trigger dựa vào **lastScheduled** hay **lastCompletion**.

Ví dụ `SimpleTriggerContext`:

~~~java
Clock fixed = Clock.fixed(
        Instant.parse("2026-01-01T02:10:00Z"),
        ZoneId.of("UTC"));

SimpleTriggerContext c = new SimpleTriggerContext(fixed);
c.update(
        Instant.parse("2026-01-01T02:00:00Z"),
        Instant.parse("2026-01-01T02:02:00Z"),
        Instant.parse("2026-01-01T02:04:00Z"));

System.out.println(c.lastScheduledExecution());
System.out.println(c.lastActualExecution());
System.out.println(c.lastCompletion());
~~~

Đây là **snippet**, cần imports `Clock`, `Instant`, `ZoneId` và `SimpleTriggerContext`. Ba dòng in chính là ba `Instant` ở trên, không phụ thuộc vào clock thật.

Nếu test `CronTrigger.forFixedExecution` với `lastScheduled`, có thể xuất hiện next instant trước thời điểm clock hiện tại, nhằm xử lý những lần schedule bị lỡ; không nhất thiết đồng nghĩa task sẽ chạy “đúng giờ trong quá khứ”. Scheduler thật phải quyết định cách thực hiện các mốc đã qua.

## 17. Giờ mùa hè (DST): kiểm thử theo từng quy tắc local time

Thử suy luận về một cron chạy lúc 02:30 địa phương mỗi ngày tại New York. Ngày Spring DST bắt đầu có thể không tồn tại 02:30. Một ví dụ Java 17 kiểm tra **tính hợp lệ của local time**:

~~~java
ZoneId ny = ZoneId.of("America/New_York");
LocalDateTime nonexistent =
        LocalDateTime.of(2026, 3, 8, 2, 30);

int offsets =
        ny.getRules().getValidOffsets(nonexistent).size();

System.out.println("valid offsets = " + offsets);
~~~

Đây là **snippet**. Với timezone database hiện hành phù hợp lịch chuyển DST tại New York ngày 2026-03-08, output là:

~~~text
valid offsets = 0
~~~

Điều đó chứng minh **02:30 là một local time không hợp lệ** trong ngày gap; không tự nói cho ta `CronTrigger` sẽ bỏ qua, chuyển sang 03:30 hay thực hiện lịch theo policy khác. Để xác nhận behavior của cron trong version bạn triển khai, test `nextExecution` trên fixed context quanh transition.

Trong ngày DST kết thúc có thể có **2 offset hợp lệ** cho một local time trong khoảng overlap. Nếu yêu cầu của doanh nghiệp là “chạy tối đa một lần cho mỗi ngày địa phương”, cần dedup bằng `(tenantId, businessDate, jobType)` hoặc key tương đương bất kể scheduler có phát ra một hay hai lần.

### 17.1. Đừng tính “ngày kế tiếp” bằng 24 giờ nếu là calendar schedule

~~~java
Instant next = previous.plus(Duration.ofHours(24));
~~~

**Snippet** trên có ý nghĩa “sau đúng 24 giờ elapsed”, không luôn là “09:00 ngày mai ở local timezone”. Với calendar rule, chuyển sang `ZonedDateTime` theo `ZoneId`, điều chỉnh ngày theo lịch và test DST.

### 17.2. ZoneId và ZoneOffset

- `ZoneId.of("Europe/Berlin")`: quy tắc thời gian theo Berlin, có DST trong nhiều giai đoạn.
- `ZoneOffset.of("+01:00")`: offset cố định, không tự chuyển thành +02:00 vào mùa hè.
- `ZoneId.of("Asia/Ho_Chi_Minh")`: tên vùng theo timezone database, giúp diễn giải lịch sử/quy tắc phù hợp; khác khái niệm một chuỗi cố định `"+07:00"` về mặt biểu diễn và khả năng xử lý quy tắc theo vùng.

## 18. Đồng hồ thay đổi giữa các lần tính: những gì được và không được cam kết

### 18.1. Thay Clock sau khi scheduler đã hoạt động

`ThreadPoolTaskScheduler#setClock(...)` là cấu hình Spring cho scheduler. Tuy nhiên, không nên tự cập nhật clock ở runtime để “chuyển timezone một job” mà chưa đánh giá tasks đang pending, cách tính delay, trigger context đã tạo và các cấu hình `@Scheduled`. Đặt một zone explicit cho mỗi cron hoặc tái cấu hình job qua lifecycle được quản lý sẽ dễ dự đoán hơn.

### 18.2. Thay zone không thay Instant

`clock.withZone(otherZone)` không thay thời điểm thật được trả bởi `Clock.fixed`; với các hệ thống clock dùng cùng nguồn, zone mới thay cách diễn giải lịch địa phương. Nhưng nếu bạn gọi `LocalDate.now(clock)` hoặc một cron không chỉ định zone, kết quả **có thể thay đổi**.

### 18.3. Thay giờ hệ thống không đồng nghĩa thay duration đang đợi

Nếu một delayed job đã được enqueue bằng relative delay, việc chỉnh wall-clock về quá khứ không nhất thiết khiến job phải đợi lâu thêm bằng đúng mức điều chỉnh. Ngược lại, một cron sẽ tính các lần tiếp theo từ clock/zone và lịch sử trigger; hành vi có thể thay đổi ở lần tính kế tiếp.

### 18.4. Không lấy thời điểm thực thi là bảo đảm SLA

`schedule(job, startTime)` diễn đạt **thời điểm dự định**, không phải một hard real-time guarantee. Lúc task bắt đầu thực phụ thuộc:

- Worker có sẵn hay bận.
- Queue/delayed tasks và chính sách prioritization.
- CPU/GC pause, I/O, lock và scheduler overhead.
- OS scheduling, virtualization, shutdown/pause.
- Lỗi trong task hoặc error handler.
- Clock synchronization và thay đổi wall time.

Cần thiết kế “làm gì nếu muộn 2 phút?” thành business policy, không chỉ dựa vào cron expression.

## 19. Tính thời gian trễ lịch và độ dài task

Có ba timestamp thường hữu ích:

~~~text
plannedAt   = lastScheduledExecution
startedAt   = lastActualExecution
finishedAt  = lastCompletion

scheduleLag = startedAt - plannedAt
taskDuration = finishedAt - startedAt
~~~

Ở lớp observability, nên lưu `plannedAt`, `startedAt`, `finishedAt` dưới dạng `Instant`, và dùng monotonic timer cho thời lượng thực thi khi cần chính xác. Nếu mốc đo bằng các clock không đồng bộ giữa các máy, chênh lệch có thể sai; cùng JVM và clock nhất quán dễ lý giải hơn.

Ví dụ:

~~~text
plannedAt  = 09:00:00
startedAt  = 09:00:03
finishedAt = 09:00:07

schedule lag = 3 seconds
run duration = 4 seconds
~~~

Khi theo dõi CronTrigger, **mốc dự định và thực tế** có thể khác nhau; chỉ log “job started 09:00:03” không đủ để biết nó trễ bao nhiêu. Cần giữ planned time trong metadata/callback hoặc đo thông qua `TriggerContext`/scheduler instrumentation phù hợp.

### Metrics nên có

| Metric | Diễn giải |
| --- | --- |
| `scheduler_trigger_lag_seconds` | Thời gian từ planned đến actual |
| `scheduler_run_duration_seconds` | Thời lượng task thực thi |
| `scheduler_execution_errors_total` | Tổng lần task thất bại |
| `scheduler_missed_or_skipped_total` | Mốc bị bỏ/lỡ theo quy tắc business |
| `scheduler_queue_wait_seconds` | Thời gian chờ worker/queue nếu đo được |
| `scheduler_active_threads` | Mức sử dụng worker của scheduler |
| `job_business_date`/job key | Có xử lý trùng/mất một kỳ nghiệp vụ không |

Tránh gắn label chứa jobId ngẫu nhiên/userId cho từng metric Prometheus khi cardinality quá lớn. Lưu chi tiết ID trong logs/traces/job store, metrics dùng label có giới hạn.

## 20. Tác động của pool size, lỗi và lifecycle

### 20.1. Clock đúng nhưng scheduler pool vẫn có thể nghẽn

`ThreadPoolTaskScheduler` dựa trên scheduled thread pool. Pool size mặc định thường là 1 nếu không cấu hình khác. Với task chạy lâu, các trigger khác có thể trễ dù `Clock` và cron đúng.

~~~java
@Bean
ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
    s.setPoolSize(4);
    s.setClock(Clock.systemUTC());
    s.setThreadNamePrefix("scheduled-");
    return s;
}
~~~

**Snippet Spring configuration**. `setPoolSize(4)` cho tối đa số scheduler threads theo cấu hình đó; không tạo guarantee 4 job/giây hoặc exactly-once.

### 20.2. Task thất bại

Trong lịch periodic, một exception không được xử lý có thể ảnh hưởng các lần thực thi sau tùy implementation/error handler. `ThreadPoolTaskScheduler#setErrorHandler(...)` cho phép cấu hình chiến lược xử lý exception; phải log có job key và phân loại nghiệp vụ, không chỉ dựa vào thời gian sẽ chữa lỗi.

Không coi lỗi periodic task là lý do để tự retry vô hạn ngay trong scheduler thread. Nếu cần retry, đặt budget, backoff và idempotency rõ ràng; cron tick và retry của cùng kỳ nghiệp vụ có thể trùng nhau.

### 20.3. Cancellation và shutdown

`ScheduledFuture.cancel(...)` thay đổi lịch đã được lên, nhưng không đảm bảo task I/O đang chạy ngừng ngay hoặc rollback side effect đã tạo. Cấu hình executor cần có shutdown policy, quản lý queue và cơ chế graceful shutdown khi context đóng.

Nếu ứng dụng cần lưu ý định scheduling qua deploy/restart, một Spring scheduler in-memory không đủ làm durable scheduler; phải persist job plans / state hoặc dùng giải pháp phù hợp. Clock đúng không thay thế persistence.

### 20.4. Clock giả trên live scheduler và resource leak

Không tạo mỗi request một `ThreadPoolTaskScheduler` mới chỉ để có clock theo user. Dùng một scheduler được container quản lý và mỗi job mang zone/trigger rõ ràng; tạo nhiều scheduler per request có thể tăng thread count và khiến shutdown phức tạp.

## 21. Nhiều instance/pod: clock thống nhất chưa có nghĩa job chỉ chạy một lần

Giả sử service có **3 replicas**, mỗi replica đều bật:

~~~java
@Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
public void chargeDaily() {
    processBilling();
}
~~~

Đây là **Spring snippet**.

Kết quả có thể là **3 job invocation** ở thời điểm 09:00 nếu cả ba replica đều chạy scheduler riêng. Việc cấu hình `Clock.systemUTC()` đồng nhất không giải quyết duplicate. Khi job có side effect:

- Dùng leader election/distributed coordination khi cần một worker active.
- Dùng durable job store với claim atomic, lease/fencing token và recovery khi node chết.
- Thiết kế idempotency theo business key (ví dụ tenant + ngày tính phí + loại tác vụ) để duplicate không gây phát hành hóa đơn lần hai.
- Quan sát clock skew giữa các pod và external store.
- Không dựa chỉ vào một distributed lock có TTL mà quên trường hợp worker cũ vẫn chạy sau khi lease hết.

**Clock đồng nhất giúp diễn giải lịch giống nhau; cơ chế ownership mới quyết định ai được phép thực thi side effect.**

## 22. Kiểm thử scheduler đúng lớp

### 22.1. Unit test pure time conversion

Với `Clock.fixed`, assert:

~~~java
Clock fixed = Clock.fixed(
        Instant.parse("2026-01-01T00:00:00Z"),
        ZoneId.of("Asia/Ho_Chi_Minh"));

assertEquals(
        Instant.parse("2026-01-01T00:00:00Z"),
        fixed.instant());

assertEquals(
        LocalDate.of(2026, 1, 1),
        LocalDate.now(fixed));
~~~

Đây là **JUnit 5 snippet**, cần import JUnit assertions và các type `java.time`. Không cần scheduler hay thread.

### 22.2. Unit test CronTrigger bằng fixed SimpleTriggerContext

~~~java
SimpleTriggerContext ctx =
        new SimpleTriggerContext(Clock.fixed(
                Instant.parse("2026-01-01T00:00:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh")));

Instant next = new CronTrigger("0 0 9 * * *")
        .nextExecution(ctx);

assertEquals(
        Instant.parse("2026-01-01T02:00:00Z"),
        next);
~~~

Đây là **Spring + JUnit 5 snippet**, không tạo task chạy thật. Test nên thêm `CronTrigger` có explicit zone UTC, ngày cuối tháng, DST gap/overlap và trigger context có lịch sử.

### 22.3. Test scheduled execution thật bằng Future hoặc CountDownLatch

~~~java
ScheduledFuture<?> done = scheduler.schedule(
        () -> doSmallWork(),
        scheduler.getClock().instant().minusSeconds(1));

done.get(5, TimeUnit.SECONDS);
assertTrue(done.isDone());
~~~

Đây là **snippet integration test** với scheduler đã chạy và `doSmallWork` deterministic. Không sleep một khoảng rồi đoán task chắc đã chạy. Timeout đủ lớn là guard, không phải phần xác định logic.

### 22.4. Không dùng Clock.fixed như một test scheduler điều khiển queue

Một bài test kiểu “setClock(fixed), schedule(job, now+1h), advance Clock.offset(fixed,+1h), assert job ran” là sai: `Clock.offset` tạo **một object Clock khác**, không tự cập nhật clock của executor hoặc kéo dài/thu ngắn queue deadline đã tính trước.

Muốn test scheduling với **virtual time**, dùng scheduler/test harness được thiết kế cho việc advance thời gian, hoặc tách `Trigger.nextExecution` thành pure function kiểm thử độc lập. Với Spring `TaskScheduler` trên JDK scheduled executor thật, phải chờ sự kiện thực tế hoặc thử một mốc đã qua.

### 22.5. Test môi trường timezone khác nhau

Chạy test trong JVM default timezone khác nhau (ví dụ UTC và Asia/Ho_Chi_Minh) để xác minh những business cron có `ZoneId` explicit không đổi ý nghĩa. Đừng chỉ test trên laptop developer rồi triển khai lên container UTC và bất ngờ lệch 7 tiếng.

### 22.6. Kiểm thử độ trễ và capacity

Dùng pool nhỏ để tạo contention có kiểm soát; đo `actual - planned`. Kiểm tra task dài hơn period, shutdown, pause/resume, exception, cancellation, missed fires và multiple instances. Không dùng độ chính xác 1 ms làm assertion cứng trong CI khi mục tiêu là test logic ngày/giờ.

## 23. Lỗi phổ biến cần tránh

**Lỗi 1 — “Clock của scheduler là một cái đồng hồ ảo để chạy task tùy ý.”** Sai. `Clock` chỉ cung cấp “now/zone”; thread pool và delayed queue có cơ chế thực thi riêng.

**Lỗi 2 — “Đổi Clock.fixed rồi tick thêm một giờ là cron tự nổ.”** Sai. Fixed clock không tự tiến và việc tạo Clock.offset không điều khiển executor đã schedule.

**Lỗi 3 — “Instant.parse(...Z) là 09:00 giờ Việt Nam nếu ghi 09:00Z.”** Sai. 09:00Z là 16:00 tại Việt Nam ở ví dụ UTC+7; cần chuyển local time/zone thành Instant đúng.

**Lỗi 4 — “LocalDateTime đã là thời điểm tuyệt đối.”** Sai. Nó chưa có timezone.

**Lỗi 5 — “ZoneOffset là ZoneId có DST.”** Sai. Offset cố định khác vùng quy tắc IANA.

**Lỗi 6 — “Cron 09:00 luôn là 09:00 địa phương của người dùng.”** Sai nếu không đặt `zone`/`ZoneId` theo business rule.

**Lỗi 7 — “Clock.systemUTC và Clock.systemDefaultZone khác nhau về thời gian thực đang trôi.”** Thông thường chúng cùng nguồn giờ thực, khác timezone dùng để diễn giải lịch. Đừng nhầm đổi zone với dịch Instant.

**Lỗi 8 — “Fixed rate 24 giờ = 09:00 mỗi ngày bất chấp DST.”** Sai ở zone có DST và với yêu cầu ngày lịch.

**Lỗi 9 — “Fixed delay đo từ lúc bắt đầu.”** Sai: fixed delay đo từ **completion** của execution trước.

**Lỗi 10 — “Fixed rate có nghĩa mọi lần thực thi bắt đầu đúng một giây tuyệt đối.”** Sai khi pool bận, task chậm hoặc OS scheduling trễ.

**Lỗi 11 — “CronTrigger chỉ cần biểu thức là biết timezone.”** Sai khi không có context/explicit zone; múi giờ của scheduler/clock quan trọng.

**Lỗi 12 — “Nếu schedule() nhận quá khứ, nó sẽ bỏ job.”** Không theo Javadoc Spring cho one-shot Instant: task được chạy càng sớm càng tốt.

**Lỗi 13 — “Đo elapsed runtime bằng System.currentTimeMillis chắc chắn chính xác.”** Sai khi wall-clock bị chỉnh; dùng monotonic source cho chênh lệch thời gian.

**Lỗi 14 — “NanoTime là Unix timestamp nanosecond.”** Sai. Nó chỉ thích hợp so sánh chênh lệch trong JVM.

**Lỗi 15 — “Một scheduler bean đảm bảo cron chỉ chạy một lần trên cluster.”** Sai. Mỗi instance thường có scheduler riêng, cần coordination/idempotency.

**Lỗi 16 — “@Scheduled return xong nghĩa đã chắc chắn ghi nghiệp vụ bền.”** Không nếu task spawn fire-and-forget, DB transaction/queue chưa commit hoặc process crash.

**Lỗi 17 — “TaskScheduler clock của mọi implementation giống nhau.”** Không có gì bảo đảm mọi adapter/custom scheduler có cùng behavior; kiểm tra đúng class/version.

**Lỗi 18 — “Chuyển Clock sang UTC sẽ tự sửa mọi cron.”** Không nếu các CronTrigger đã chỉ định timezone riêng hoặc business code tự dùng system default time.

**Lỗi 19 — “Mỗi CronTrigger đều replay tất cả mốc bỏ lỡ.”** Không. Lenient/fixed execution và restart persistence là những vấn đề riêng.

**Lỗi 20 — “Có Clock.fixed thì không cần cleanup scheduler trong test.”** Sai. Nếu test tạo `ThreadPoolTaskScheduler` thật, vẫn phải shutdown/đóng context.

## 24. Bài tập tự kiểm tra

**Câu 1.** `Clock.fixed(2026-01-01T00:00:00Z, Asia/Ho_Chi_Minh)` thì `LocalTime.now(clock)` là mấy giờ?

**Đáp án:** 07:00, vì Việt Nam UTC+7 tại thời điểm nêu. `clock.instant()` vẫn là 00:00Z.

**Câu 2.** Cron `"0 0 9 * * *"` với `SimpleTriggerContext` dùng clock trên sẽ cho lần đầu là Instant nào?

**Đáp án:** 2026-01-01T02:00:00Z. Local 09:00 HCM = UTC 02:00.

**Câu 3.** Nếu vẫn cùng clock nhưng CronTrigger được tạo với `ZoneId.of("UTC")` thì kết quả?

**Đáp án:** 2026-01-01T09:00:00Z. Explicit zone của trigger được ưu tiên khi tính cron.

**Câu 4.** `scheduleAtFixedRate(job, Duration.ofHours(24))` có tương đương “09:00 sáng theo giờ New York mỗi ngày” không?

**Đáp án:** không. Fixed rate lặp theo 24 giờ elapsed, còn business cron 09:00 phụ thuộc calendar/DST.

**Câu 5.** `ThreadPoolTaskScheduler#setClock(Clock.fixed(...))` có làm một delayed queue tự chạy sau khi test tạo một `Clock.offset(...)` mới không?

**Đáp án:** không. Clock không kiểm soát virtual-time advancement của underlying executor. Test trigger calculation riêng, hoặc dùng test scheduler có virtual time thật.

**Câu 6.** Một task dự kiến 09:00, thực bắt đầu 09:02 và kết thúc 09:04. Ba giá trị trong `TriggerContext` là gì?

**Đáp án:** `lastScheduledExecution≈09:00`, `lastActualExecution≈09:02`, `lastCompletion≈09:04`, nếu đó là một lần execution đã được ghi nhận đầy đủ.

**Câu 7.** Cron của một job được khai báo trên ba pod cùng zone. Lúc 09:00 có chắc chỉ một lần business effect không?

**Đáp án:** không. Các pod có thể cùng chạy; cần claim/leader/idempotency theo business key.

**Câu 8.** Vì sao không đo job duration bằng `Instant.now()` nếu cần elapsed chính xác?

**Đáp án:** wall-clock có thể bị chỉnh; dùng `System.nanoTime()` hoặc Timer phù hợp để đo elapsed trong JVM.

**Câu 9.** `@Scheduled(cron="0 0 9 * * *", zone="Asia/Ho_Chi_Minh")` chạy trên server UTC có bị đổi thành 09:00 UTC không?

**Đáp án:** không, zone đã được chỉ định cho cron. 09:00 Việt Nam ở ví dụ chuyển thành 02:00 UTC.

**Câu 10.** Một scheduler đặt one-shot task vào một `Instant` đã quá khứ. Điều gì xảy ra?

**Đáp án:** theo Spring `TaskScheduler`, task được thực thi sớm nhất có thể thay vì chạy ngược thời gian. Vẫn phụ thuộc worker và tình trạng hệ thống.

## 25. Checklist trước khi triển khai

- [ ] Phân biệt timestamp tuyệt đối (`Instant`), calendar local time (`LocalDateTime + ZoneId`), relative duration (`Duration`).
- [ ] Xác định `TaskScheduler` implementation và giá trị `getClock()` thực tế.
- [ ] Với lịch business địa phương, dùng `ZoneId` explicit, không dựa vào timezone máy chủ.
- [ ] Cron có 6 trường đúng cú pháp Spring, trong đó trường đầu là giây.
- [ ] Với DST zone, đã định nghĩa xử lý gap/overlap và dedup theo kỳ nghiệp vụ.
- [ ] Fixed rate/delay đã được chọn theo ý nghĩa thời gian, không nhầm chúng là cron.
- [ ] Clock inject cho tính toán, test bằng `Clock.fixed`/`SimpleTriggerContext` khi cần tính lịch.
- [ ] Không kỳ vọng `Clock.fixed` tự tiến hoặc điều khiển delayed queue của scheduler thật.
- [ ] Có deadline và timeout theo nghiệp vụ; chấp nhận scheduler không hard real-time.
- [ ] Đo schedule lag và runtime, dùng monotonic source cho elapsed duration.
- [ ] Pool size, error handler, cancellation và shutdown được cấu hình rõ.
- [ ] Không đánh đồng Clock config với đảm bảo exactly-once toàn cụm.
- [ ] Có quy trình backfill/recovery khi restart hoặc missed firings.
- [ ] Kết quả unit test không phụ thuộc giờ hiện tại, timezone của laptop, `Thread.sleep` hay lịch cron phải chạy thật.
- [ ] Integration test có future/latch và timeout, luôn dọn scheduler bean/executor sau test.
- [ ] Logs/metrics lưu planned/actual/completion timestamps rõ ràng, không dùng `nanoTime` làm Unix timestamp.

## 26. Tổng kết

**`Clock` trả lời “bây giờ là khi nào, trong timezone nào?”; `TaskScheduler` quyết định “task nào sẽ được đặt lịch và thực thi ở mốc nào?”.** Một `Instant` là timestamp tuyệt đối; một `Duration` là khoảng thời gian; `ZoneId` xác định cách diễn giải lịch dân sự. `TriggerContext` mang clock và lịch sử lần chạy để `CronTrigger` tính mốc tiếp theo.

Với Spring 6.2.x, `ThreadPoolTaskScheduler` có `getClock()` và `setClock(Clock)`. Cấu hình clock UTC giúp tránh phụ thuộc timezone server cho các phép tính mặc định; đặt zone rõ cho cron theo địa phương là cách biểu đạt business rule chắc chắn hơn. Để test logic thời gian, dùng `Clock.fixed` và hỏi `CronTrigger.nextExecution(SimpleTriggerContext)`; **không** coi Clock là công tắc điều khiển queue thời gian ảo của executor.

Trong production, luôn xem cả task duration, worker capacity, DST, crash/restart, cluster duplication, idempotency và observability. **Đúng clock chưa đủ để job chạy đúng một lần, đúng hạn, đúng nghiệp vụ.**

## Tài liệu tham khảo chính thức

- Spring Framework 6.2 Reference — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html
- Spring Framework 6.2 Javadoc — `TaskScheduler`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html
- Spring Framework 6.2 Javadoc — `ThreadPoolTaskScheduler` (getClock/setClock): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html
- Spring Framework 6.2 Javadoc — `TriggerContext`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TriggerContext.html
- Spring Framework 6.2 Javadoc — `CronTrigger`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html
- Spring Framework 6.2 Javadoc — `SimpleTriggerContext`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/SimpleTriggerContext.html
- Spring Framework 6.2 Javadoc — `@Scheduled`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html
- Java SE 17 Javadoc — `Clock`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html
- Java SE 17 Javadoc — `Instant`, `Duration`, `ZoneId`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/package-summary.html
- Java SE 17 Javadoc — `ScheduledThreadPoolExecutor`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-scheduler-007">Phân biệt luồng kích hoạt lịch với luồng thực thi công việc</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt luồng kích hoạt lịch với luồng thực thi công việc

Trong Spring Framework, **TaskScheduler** quyết định *khi nào* một công việc được kích hoạt theo thời gian; **TaskExecutor** thường được dùng để quyết định *ở đâu và bằng những worker nào* công việc được xử lý. Tuy nhiên, **việc dùng TaskScheduler hoặc @Scheduled không mặc nhiên tạo ra hai nhóm luồng tách biệt**. Với **ThreadPoolTaskScheduler**, chính các thread trong scheduler pool thực thi Runnable đã được lập lịch. Nếu Runnable đó chứa logic nghiệp vụ kéo dài, scheduler thread sẽ bận với công việc ấy và những callback lịch khác có thể bị trễ.

Bài **“Phân biệt luồng kích hoạt lịch với luồng thực thi công việc”** thuộc nhóm **TaskScheduler** của module **Spring Framework - concurrency**. Mục tiêu là hiểu ba ranh giới: **tính toán thời điểm kích hoạt**, **chạy callback được lập lịch**, và **thực thi phần việc nghiệp vụ**. Ba phần này có thể nằm trên cùng thread hoặc được chủ động tách qua TaskExecutor.

Sau bài này, bạn có thể:

- Giải thích ThreadPoolTaskScheduler thực sự chạy Runnable trên thread nào, và khi nào một TaskExecutor riêng mới tham gia.
- Phân biệt **fixedRate**, **fixedDelay** và **cron** khi callback thực thi trực tiếp hoặc chỉ gửi việc.
- Thiết kế **scheduler nhẹ + worker pool hữu hạn** mà không mất kiểm soát backlog, trùng lặp hoặc quyền sở hữu kết quả.
- Đọc đúng các chỉ số **scheduler lag, callback duration, executor queue wait, worker duration**.
- Kiểm thử bằng Java 17 sử dụng CountDownLatch thay vì dựa vào Thread.sleep, đồng thời cấu hình Spring với @EnableScheduling, @Scheduled, ThreadPoolTaskScheduler và ThreadPoolTaskExecutor.

## 1. Từ “trigger thread” dễ gây nhầm lẫn như thế nào?

Khi nói “luồng kích hoạt lịch”, người ta thường muốn chỉ thành phần chịu trách nhiệm nhận biết rằng **đã tới thời điểm** của một lịch chạy. Nhưng trong Java và Spring, không phải mọi implementation đều có một thread chỉ chuyên đánh chuông và một thread khác luôn chạy tác vụ.

Hãy phân biệt các thuật ngữ:

| Thành phần | Vai trò | Có bắt buộc là thread riêng không? |
| --- | --- | --- |
| **Trigger** | Quy tắc xác định thời điểm tiếp theo: fixed rate, fixed delay, cron hoặc Trigger tùy biến | **Không**. Trigger là quy tắc/lớp tính thời điểm, không đồng nghĩa với một worker thread riêng |
| **Scheduler** | Giữ lịch, phát hiện callback nào đã tới hạn và tổ chức thực thi callback | Có thread/resource thực thi theo implementation |
| **Scheduled callback** | Runnable hoặc method được scheduler gọi khi đến hạn | **Có thể chạy ngay trên scheduler thread** |
| **Business job** | Công việc thật: truy vấn DB, gọi HTTP, xuất báo cáo, tính toán | Có thể chạy trong scheduled callback hoặc worker khác |
| **TaskExecutor** | Nhận Runnable/Callable và quản lý cách thực thi công việc được gửi vào | Có thể là pool, executor trực tiếp hoặc implementation khác |
| **Worker pool** | Các thread thực thi business job nếu ứng dụng chủ động giao việc | **Không tự xuất hiện** khi chỉ bật @Scheduled |

Hai mô hình cốt lõi:

**Mô hình A — callback làm việc trực tiếp:**

~~~text
Clock / Trigger
    |
    v
ThreadPoolTaskScheduler
    |
    +-- scheduler-thread-1
            |
            +-- scheduledMethod()
                    |
                    +-- gọi DB
                    +-- xuất báo cáo 20 giây
                    +-- gửi email
            |
            +-- chỉ rảnh để xử lý callback tiếp theo khi method thoát
~~~

**Mô hình B — callback giao việc cho worker:**

~~~text
Clock / Trigger
    |
    v
ThreadPoolTaskScheduler
    |
    +-- scheduler-thread-1
            |
            +-- scheduledMethod()
                    |
                    +-- TaskExecutor.execute(job)
                    +-- return nhanh
                              |
                              v
                     ThreadPoolTaskExecutor
                              |
                              +-- worker-1 --> business job
                              +-- worker-2 --> business job
                              +-- queue nếu chưa có worker rảnh
~~~

Trong mô hình B, scheduler rảnh sớm hơn để xử lý những lịch khác, **nhưng business job không tự hoàn tất khi callback trả về**. Việc giao việc còn có thể bị **rejected**, và nếu không kiểm soát, lần kích hoạt sau có thể tạo công việc mới khi job trước vẫn chạy.

## 2. TaskScheduler là một abstraction, không phải lời hứa về kiến trúc thread

Spring cung cấp interface **org.springframework.scheduling.TaskScheduler** với các thao tác như:

~~~java
ScheduledFuture<?> schedule(Runnable task, Instant startTime);

ScheduledFuture<?> schedule(Runnable task, Trigger trigger);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Duration period);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Duration delay);
~~~

Đây là **chữ ký rút gọn để giải thích** từ API Spring, không phải file Java độc lập. Interface còn có overload nhận thời điểm bắt đầu và clock.

**TaskScheduler trả lời:** khi nào Runnable sẽ được scheduler tổ chức thực thi, hoặc dựa theo trigger để hẹn lần chạy tiếp theo. Interface **không buộc** tất cả implementation phải có một thread trigger riêng và một pool thực thi riêng.

### Các implementation cần phân biệt

**ThreadPoolTaskScheduler**: wrapper của một **ScheduledExecutorService/ScheduledThreadPoolExecutor**, cho phép cấu hình scheduler pool và lifecycle. **Task execution happens on the scheduler thread(s)** theo Javadoc. Mặc định pool size thường là **1** nếu không cấu hình lại, nhưng ứng dụng có thể tùy chỉnh.

**ConcurrentTaskScheduler**: adapter cho một **ScheduledExecutorService** đã có. Thread model được quyết định bởi underlying scheduler; đừng suy ra có TaskExecutor độc lập.

**SimpleAsyncTaskScheduler** (Spring Framework từ 6.1): dùng **một scheduler thread** và tạo thread mới cho nhiều lượt thực thi được kích hoạt. Nó phù hợp với mô hình dùng **virtual threads trên Java 21** khi cấu hình tương ứng; **riêng fixed-delay tasks của implementation này chạy trên một scheduler thread**, nên cần chú ý nếu tác vụ kéo dài. Không đánh đồng SimpleAsyncTaskScheduler với ThreadPoolTaskScheduler.

**DefaultManagedTaskScheduler**: tích hợp với scheduler do application server quản lý. Quy tắc cấp thread và lifecycle phụ thuộc môi trường.

**Quartz**: bộ lập lịch có kiến trúc, chính sách persistence/cluster và worker management riêng; không tự mặc định mọi chi tiết của Quartz giống TaskScheduler tích hợp trong JVM.

Bài này dùng **ThreadPoolTaskScheduler** làm ví dụ chính vì đây là lựa chọn phổ biến trong Spring và thể hiện rõ sự khác nhau giữa **scheduler thread trực tiếp làm việc** với **callback chủ động delegate**.

## 3. Vì sao ThreadPoolTaskScheduler không có worker pool riêng mặc định?

ThreadPoolTaskScheduler bọc **ScheduledExecutorService** ở tầng Java. Khi một ScheduledThreadPoolExecutor thực thi một Runnable đã tới hạn, **một thread của scheduled pool** gọi phương thức run của Runnable đó.

~~~text
ThreadPoolTaskScheduler(poolSize = 1)

Scheduler queue:
  [A due] [B due] [C due in 1 second]
       |
       v
scheduler-1:
  run A ------------------------> A returns
                                 |
                                 v
                              run B --> B returns
                                         |
                                         v
                                      run C
~~~

Nếu A là tác vụ I/O chặn 30 giây, B có thể bị trễ **ngay cả khi B theo lịch phải chạy vài giây trước**. Chỉnh **@Scheduled(fixedRate=...)** trên B không tạo ra thread độc lập cho nó.

Một scheduler pool có kích thước lớn hơn 1 cho phép các **callback khác nhau** đồng thời được chạy, trong giới hạn số thread. Nhưng đây vẫn là **pool của chính scheduler**. Chỉ tăng poolSize không tự tạo ranh giới tài nguyên giữa *lập lịch* và *thực thi business job*.

### Nên tăng poolSize hay tách executor?

| Hiện tượng | Tăng scheduler poolSize | Tách callback và worker pool |
| --- | --- | --- |
| Nhiều callback độc lập chặn nhau | Có thể giảm sự chờ lẫn nhau | Thường giảm chờ nếu callback gửi việc nhanh |
| Business job nặng/đợi I/O | Vẫn chiếm scheduler thread nếu làm trực tiếp | Business job tiêu thụ worker, không giữ scheduler |
| Cần giới hạn business concurrency | Có thể giới hạn gián tiếp bằng scheduler size | Cấu hình được worker/core/max/queue và cơ chế kiểm soát riêng |
| Muốn theo dõi trễ lịch và thời gian job tách biệt | Khó hơn nếu một callback làm cả hai | Dễ đo riêng scheduler delay và execution delay |
| Cần không trùng lặp job | Không tự bảo đảm ở mọi cách tổ chức | Vẫn phải thiết kế single-flight/lock/idempotency |
| Cần tránh backlog lớn | Scheduler vẫn có queue/callback được hẹn | Worker queue cũng phải bounded và có overload policy |

**Không phải mọi scheduled callback đều phải delegate.** Nếu công việc chỉ cập nhật một counter hoặc làm việc không chặn, rất ngắn, chạy trực tiếp trên scheduler thread có thể là hợp lý. Tách executor có chi phí context switching, thêm queue, thêm trạng thái và xử lý rejection.

## 4. Fixed rate, fixed delay và cron: đo thời gian của cái gì?

Điểm then chốt là **lịch đo thời gian dựa trên việc chạy callback của scheduler**, không nhất thiết dựa trên business job được delegate.

### 4.1 Fixed delay

**@Scheduled(fixedDelay = 5000)**: một lượt gọi method hoàn tất, sau đó chờ khoảng **5 giây** rồi mới kích hoạt lượt kế tiếp.

~~~text
Chạy trực tiếp:
t=00 start job A ----(8 giây)---- t=08 finish
                              + 5 giây
t=13 start job B

Delegate qua executor:
t=00 callback A --> gửi worker job A --> return rất nhanh
                    |
                    +-- job A chạy 8 giây trong worker

t~05 callback B --> gửi worker job B
                    (dù job A có thể đã/đang chạy)
~~~

Trong mô hình delegate, **fixedDelay** dựa trên **thời điểm callback A trả về**, chứ không tự đợi công việc trong worker hoàn tất. Vì vậy, nói “fixedDelay chắc chắn không làm hai business jobs chồng lấn” là **sai** khi scheduled method chỉ gửi việc bất đồng bộ.

### 4.2 Fixed rate

**@Scheduled(fixedRate = 5000)**: các lần chạy được hẹn theo chu kỳ xấp xỉ **5 giây giữa các thời điểm bắt đầu theo lịch**.

~~~text
t=00  t=05  t=10  t=15 ...  (mốc dự kiến)
  |     |     |     |
  v     v     v     v
callback attempts
~~~

Nếu một lượt **chạy trực tiếp** lâu hơn period, các lần chạy sau có thể bị trễ; không có phép màu bắt buộc một tác vụ duy nhất phải chạy hai lần đồng thời. Với ScheduledThreadPoolExecutor, **các lần thực thi liên tiếp của cùng periodic task không overlap**, dù scheduler có nhiều worker threads; khi việc trước lâu hơn chu kỳ, lần sau có thể bắt đầu muộn.

Nhưng nếu callback fixed-rate **chỉ submit công việc rồi return**, hai business jobs do các lần callback khác nhau tạo ra **có thể chạy chồng lấn** trong worker pool. Scheduler chỉ thấy callback đã kết thúc nhanh.

**Fixed rate không phải cam kết “đúng mili giây”**. Độ trễ, tải hệ thống, GC, scheduler queue, saturation, thời gian chuyển thread và khả năng tiến triển của worker ảnh hưởng thời điểm thực.

### 4.3 Cron trigger

**@Scheduled(cron = "0 */5 * * * *", zone = "UTC")** thường dùng biểu thức cron Spring gồm **6 trường** (giây, phút, giờ, ngày trong tháng, tháng, thứ) để định thời điểm lịch.

Cron không đồng nghĩa “cứ tới mốc phút là scheduler bắt buộc mở một worker mới”. Đối với TaskScheduler dựa trên Trigger, **tính thời điểm tiếp theo có thể phụ thuộc thông tin lần chạy trước** thông qua TriggerContext.

Trong cách dùng CronTrigger thông thường của Spring, lịch có thể sử dụng lần hoàn tất trước để tính mốc tiếp theo; các mốc bị bỏ lỡ không nhất thiết được chạy bù. Những tùy chọn cron fixed/lenient execution mới và phiên bản khác nhau có semantics riêng, nên phải đọc API thực tế nếu nghiệp vụ yêu cầu **catch-up/misfire**.

Khi callback chỉ delegate, scheduler coi việc **gửi thành công** là callback hoàn tất, không chờ job trong executor. Cron có thể tiếp tục kích hoạt trong khi job trước chưa xong.

Với lịch theo múi giờ, cần xem **zone**, chuyển đổi DST, ngày nghỉ và hợp đồng nghiệp vụ. Không lấy log của UTC làm bằng chứng lịch giờ địa phương đúng nếu cron được đánh giá theo zone khác.

### Bảng tóm tắt

| Kiểu lịch | Callback làm việc trực tiếp | Callback chỉ delegate vào executor |
| --- | --- | --- |
| **fixedDelay** | Khoảng delay tính sau khi phần việc trong callback hoàn tất | Khoảng delay tính sau khi **submit callback** kết thúc, không sau job |
| **fixedRate** | Các mốc cố định; callback cùng periodic task trong ScheduledThreadPoolExecutor không overlap | Các lần **submit** theo mốc; worker jobs có thể overlap |
| **cron** | Kích hoạt theo lịch/TriggerContext của callback | Lịch không tự biết trạng thái business job ở worker |
| **schedule(Runnable, Instant)** | Chạy một lần ở thời điểm tới hạn | Gửi một lần, future của scheduler không đại diện kết thúc job delegate |

Điểm chung: **schedule là cơ chế kích hoạt; hoàn thành công việc nghiệp vụ là một trạng thái riêng nếu có delegation**.

## 5. Ví dụ Java 17 chạy được: trực tiếp chặn scheduler và delegate không chặn

Chương trình **DirectVsDelegatedSchedulerDemo.java** có hai phần, dùng **ScheduledExecutorService** và **ExecutorService** thuần Java, không cần Spring. Nó chứng minh:

- Với **scheduler một thread**, callback A đang chờ làm callback B chưa thể chạy.
- Khi callback A **gửi công việc cho worker rồi trả về**, callback B chạy được **trong khi worker vẫn bận**.
- Không có vòng chờ theo thời gian ngẫu nhiên; **CountDownLatch** đặt đúng thời điểm, **Future.get(timeout)** giới hạn chờ, và tên thread được đặt bằng ThreadFactory.

Lưu thành **DirectVsDelegatedSchedulerDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DirectVsDelegatedSchedulerDemo {
    private static void direct() throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> new Thread(r, "scheduler-thread"));

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondRan = new AtomicBoolean(false);

        try {
            ScheduledFuture<?> first = scheduler.schedule(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, 0, TimeUnit.MILLISECONDS);

            if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("First callback chưa chạy");
            }

            ScheduledFuture<?> second = scheduler.schedule(
                    () -> secondRan.set(true),
                    0, TimeUnit.MILLISECONDS);

            // First vẫn giữ thread; second không thể được gọi.
            boolean blocked = !second.isDone() && !secondRan.get();

            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            System.out.println("Direct: callback thứ hai bị chặn: "
                    + blocked);
            System.out.println("Direct: callback thứ hai đã chạy: "
                    + secondRan.get());
        } finally {
            releaseFirst.countDown();
            scheduler.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Scheduler chưa kết thúc");
            }
        }
    }

    private static void delegated() throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> new Thread(r, "scheduler-thread"));
        ExecutorService workers = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "worker-thread"));

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch workerFinished = new CountDownLatch(1);

        AtomicReference<String> triggerThread = new AtomicReference<>();
        AtomicReference<String> jobThread = new AtomicReference<>();
        AtomicBoolean secondRan = new AtomicBoolean(false);

        try {
            ScheduledFuture<?> first = scheduler.schedule(() -> {
                triggerThread.set(Thread.currentThread().getName());
                workers.execute(() -> {
                    jobThread.set(Thread.currentThread().getName());
                    workerStarted.countDown();
                    try {
                        releaseWorker.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        workerFinished.countDown();
                    }
                });
            }, 0, TimeUnit.MILLISECONDS);

            first.get(5, TimeUnit.SECONDS);

            if (!workerStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker chưa bắt đầu");
            }

            ScheduledFuture<?> second = scheduler.schedule(
                    () -> secondRan.set(true),
                    0, TimeUnit.MILLISECONDS);

            // Scheduler có thể xử lý callback thứ hai dù worker
            // vẫn đang đợi releaseWorker.
            second.get(5, TimeUnit.SECONDS);
            boolean ranWhileWorkerBusy =
                    secondRan.get() && workerFinished.getCount() == 1;

            releaseWorker.countDown();

            if (!workerFinished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker chưa hoàn tất");
            }

            System.out.println("Delegated: callback trên scheduler-thread: "
                    + "scheduler-thread".equals(triggerThread.get()));
            System.out.println("Delegated: job trên worker-thread: "
                    + "worker-thread".equals(jobThread.get()));
            System.out.println("Delegated: callback hai khi worker bận: "
                    + ranWhileWorkerBusy);
            System.out.println("Delegated: worker đã hoàn tất: "
                    + (workerFinished.getCount() == 0));
        } finally {
            releaseWorker.countDown();
            scheduler.shutdownNow();
            workers.shutdownNow();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)
                    || !workers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Executor chưa kết thúc");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        direct();
        delegated();
    }
}
~~~

Biên dịch và chạy bằng **JDK 17+**:

~~~bash
javac -encoding UTF-8 DirectVsDelegatedSchedulerDemo.java
java DirectVsDelegatedSchedulerDemo
~~~

**Stdout** khi môi trường cho các thread khởi động và hoàn tất trong các thời hạn 5 giây:

~~~text
Direct: callback thứ hai bị chặn: true
Direct: callback thứ hai đã chạy: true
Delegated: callback trên scheduler-thread: true
Delegated: job trên worker-thread: true
Delegated: callback hai khi worker bận: true
Delegated: worker đã hoàn tất: true
~~~

**Vì sao các kết luận không dựa vào may rủi của scheduler?**

1. Ở **direct**, main đợi firstStarted nên chắc chắn callback A đã chạy và đang giữ scheduler thread trong await(). A không thể kết thúc cho đến khi main mở releaseFirst. Vì chỉ có một scheduler thread, B chưa thể được gọi khi main kiểm tra blocked.
2. Sau khi mở latch, main chờ cả hai ScheduledFuture bằng get(timeout) để xác nhận B đã chạy.
3. Ở **delegated**, callback A trả về sau khi workers.execute tiếp nhận job. first.get(timeout) hoàn tất trước khi main gửi callback B.
4. Main đợi workerStarted, tức là worker thực sự bắt đầu và đang chờ releaseWorker. Callback B vẫn được scheduler thread xử lý và Future của B hoàn tất; lúc ấy workerFinished chưa đếm xuống.
5. Main mở releaseWorker để job thoát, rồi mới in kết quả. Tên thread được định nghĩa tường minh nên so sánh mang ý nghĩa thực tế.
6. finally mở latch và shutdown cả hai executor để tránh giữ thread nền sau chương trình.

**Giới hạn:** ví dụ chứng minh sự khác nhau giữa callback trực tiếp và delegation, không phải mô phỏng cron hay benchmark tải. Cấu hình một worker để quan sát ranh giới, không có nghĩa một worker là lựa chọn mặc định tối ưu cho production.

## 6. ScheduledFuture biểu thị sự hoàn tất của cái gì?

Đối với một lần **scheduler.schedule(runnable, instant)** hoặc ScheduledExecutorService.schedule(...), một **ScheduledFuture** có thể cho biết callback được scheduler chạy xong, lỗi hoặc hủy.

Nếu callback là:

~~~java
scheduler.schedule(() -> workerExecutor.execute(
        () -> generateReport()), startTime);
~~~

Đây là **đoạn minh họa** với scheduler, workerExecutor, startTime, generateReport đã tồn tại.

**ScheduledFuture hoàn tất khi Runnable gửi việc kết thúc**, không chờ generateReport hoàn thành, trừ khi chính callback block để chờ worker. Khi dùng delegate, cần một **Future/CompletableFuture hoặc trạng thái job riêng** để theo dõi kết quả thực sự.

Hai handle không nên bị nhập làm một:

~~~text
ScheduledFuture of callback
      |
      +-- callback gửi việc: DONE
                  |
                  +-- business Future: RUNNING --> SUCCESS / FAILURE
~~~

Nếu gọi **cancel(true)** trên ScheduledFuture của một periodic callback, bạn yêu cầu hủy lịch/callback tương ứng theo hợp đồng. Điều đó **không mặc nhiên cancel** mọi job đã được submit vào workerExecutor trước đó.

Nếu gọi **cancel(true)** trên Future do TaskExecutor.submit trả về, bạn đang yêu cầu hủy **worker job**, nhưng việc ngắt vẫn mang tính hợp tác tùy Future implementation. Với CompletableFuture của JDK, **cancel(true) không tự interrupt supplier**. Chọn đúng handle và đúng lifecycle.

### Repeating ScheduledFuture

Với **scheduleAtFixedRate** hoặc **scheduleWithFixedDelay**, Future đại diện cho **chuỗi lặp**. Trong JDK ScheduledExecutorService, get() của Future lặp thường **không trả về bình thường khi lịch tiếp tục vô hạn**; nó chỉ kết thúc do hủy hoặc lỗi. Không gọi recurringFuture.get() trong một thread rồi kỳ vọng mỗi tick cho một kết quả business mới.

Thay vào đó, quan sát:
- Số lần callback bắt đầu/hoàn tất.
- Số lần submit worker được chấp nhận/rejected.
- Future hoặc trạng thái riêng của từng job.
- Cancellation của lịch và các job đang chạy như hai quyết định khác nhau.

## 7. Một định luật vận hành: callback trả về sớm không phải job đã xong

Một lỗi rất thường gặp trong code review:

~~~java
@Scheduled(fixedDelay = 5000)
public void exportData() {
    executor.execute(() -> reallyExportData());
}
~~~

Đây là **đoạn minh họa trong bean Spring có executor và reallyExportData**. Method exportData có thể return ngay sau khi executor nhận task. **FixedDelay 5 giây** không chờ reallyExportData hoàn thành.

Điều này ảnh hưởng tới:

- **Khả năng overlap** giữa các business jobs.
- **Thông báo thành công**: callback trả về không chứng minh tác vụ đã chạy.
- **Theo dõi lỗi**: exception trong worker không tự quay lại stack của scheduled callback.
- **Cancellation**: hủy lịch không hủy các job đã nhận.
- **Time budget**: chờ trong worker queue có thể khiến công việc chạy sau deadline.
- **Bảo đảm không mất job**: executor in-memory không phải durable queue.

Nếu logic nghiệp vụ cần “mỗi lượt chỉ bắt đầu khi lượt trước đã hoàn thành”, thì **scheduleWithFixedDelay cho callback delegate không tự bảo đảm điều này**. Cần một cơ chế quản lý trạng thái in-flight hoặc một scheduler/job system hiểu business completion.

## 8. Cấu hình Spring: hai pool tách biệt, một cho lịch và một cho công việc

Giải pháp phổ biến là dùng **ThreadPoolTaskScheduler** để giữ lịch và **ThreadPoolTaskExecutor** để chạy công việc nặng.

Các snippet sau thuộc một ứng dụng **Spring Framework 6.x, Java 17+ có spring-context**, không phải chương trình javac thuần. Mỗi khối là một file riêng. Các thông số chỉ minh họa, không phải sizing production.

**SchedulingPoolsConfig.java**:

~~~java
package example.scheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingPoolsConfig {

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("trigger-");
        return scheduler;
    }

    @Bean("jobExecutor")
    public ThreadPoolTaskExecutor jobExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("job-");
        return executor;
    }
}
~~~

**ReportProcessor.java**:

~~~java
package example.scheduler;

import org.springframework.stereotype.Service;

@Service
public class ReportProcessor {

    public void process(String jobId) {
        // Thực hiện công việc nghiệp vụ thật:
        // kiểm tra deadline và quyền, gọi dependency có timeout,
        // quản lý transaction và ghi kết quả theo jobId.
        System.out.println("Processing job: " + jobId);
    }
}
~~~

**ReportTrigger.java**:

~~~java
package example.scheduler;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class ReportTrigger {
    private final ThreadPoolTaskExecutor executor;
    private final ReportProcessor processor;

    public ReportTrigger(
            @Qualifier("jobExecutor") ThreadPoolTaskExecutor executor,
            ReportProcessor processor) {
        this.executor = executor;
        this.processor = processor;
    }

    @Scheduled(fixedDelay = 5_000)
    public void trigger() {
        String jobId = UUID.randomUUID().toString();

        try {
            // Callback scheduler chỉ làm việc gửi task ngắn gọn.
            executor.execute(() -> processor.process(jobId));
        } catch (TaskRejectedException rejected) {
            // Ghi metric và log có ngữ cảnh; quy định rõ
            // phải bỏ, lưu lại hay retry một job chưa nhận.
            System.err.println("Job rejected: " + jobId);
        }
    }
}
~~~

**Phân tích cấu hình:**

1. **@EnableScheduling** đăng ký bộ xử lý @Scheduled trong context. Bean tên **taskScheduler** được dùng làm scheduler phù hợp theo cấu hình Spring thông thường.
2. **@Scheduled(fixedDelay = 5_000)** khiến **ReportTrigger.trigger()** được gọi theo lịch. Với ThreadPoolTaskScheduler đang dùng, callback chạy trong pool có tên tiền tố **trigger-**.
3. Trong callback, **jobExecutor.execute(...)** gửi phần việc thật vào pool **job-**; khi được nhận, callback return mà không chờ processor.process.
4. **ThreadPoolTaskExecutor** có queue hữu hạn (16) và số worker trong ví dụ giới hạn từ 2 tới 4. Khi bão hòa, submit có thể bị từ chối; **TaskRejectedException** cần chính sách ứng dụng rõ ràng.
5. **ReportProcessor.process** được gọi trong worker của jobExecutor. Nó không cần @Async riêng vì **việc dispatch đã được thực hiện tường minh ở executor.execute**.
6. Bởi callback có thể kết thúc rất nhanh, những lần gọi trigger tiếp theo có thể tạo thêm job, **kể cả khi job trước còn chạy**.
7. Cấu hình trên **không cung cấp distributed lock** và không bảo đảm mỗi job chỉ chạy một lần trong nhiều application instances.

**Lưu ý về dependency:** những file này cần đặt đúng package và được Spring component scan/đăng ký vào ApplicationContext. Ví dụ ReportProcessor chỉ in jobId để thấy đường đi; production phải xử lý kết quả, bảo mật, lỗi và lưu job theo yêu cầu.

### Khi nào giữ logic trực tiếp trong @Scheduled?

Nếu công việc đơn giản:

~~~java
@Scheduled(fixedRate = 10_000)
public void refreshInMemoryCounter() {
    counter.incrementAndGet();
}
~~~

Đây là **đoạn minh họa** với counter là AtomicLong/AtomicInteger. Callback ngắn, không block, có thể chạy trực tiếp trên scheduler pool. Không cần tạo một worker queue khác chỉ để tăng counter.

Nếu refreshInMemoryCounter bắt đầu gọi API ngoài, chờ DB hoặc đọc file lớn, cần đánh giá lại độ dài callback và ảnh hưởng các lịch khác trước khi quyết định tách worker.

## 9. @Scheduled kết hợp @Async: hai interceptor, hai ranh giới

Có thể gặp mã:

~~~java
@Scheduled(fixedRate = 1_000)
@Async("jobExecutor")
public void work() {
    runHeavyJob();
}
~~~

Đây là **đoạn minh họa**. Cách kết hợp hai annotation phụ thuộc cấu hình **@EnableScheduling, @EnableAsync, proxy advice** và đường gọi. Không nên lấy việc source có cả hai annotation làm bằng chứng **lịch và công việc đã tách đúng thread** trong mọi runtime.

Một phương án rõ ràng, dễ test hơn là **hai bean khác nhau**:

~~~java
@Component
class ScheduledStarter {
    private final AsyncWorker worker;

    ScheduledStarter(AsyncWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedRate = 1_000)
    public void tick() {
        worker.executeAsync(); // Lời gọi đi qua bean Spring khác.
    }
}
~~~

~~~java
@Service
class AsyncWorker {
    @Async("jobExecutor")
    public void executeAsync() {
        // Công việc thực sự; cần xử lý lỗi của void @Async.
    }
}
~~~

Đây là **hai snippet Spring minh họa**, cần import Component, Service, Scheduled, Async, @EnableScheduling và **@EnableAsync** cùng executor bean. Khi gọi qua Spring-managed proxy, **@Async** dispatch method body vào executor. Với @Async void, lỗi trong worker **không tự ném trở lại caller đã return**; có thể cần AsyncUncaughtExceptionHandler, metrics và hợp đồng job riêng.

So sánh hai cách tách:

| Cách làm | Điểm dispatch | Điểm cần chú ý |
| --- | --- | --- |
| **@Scheduled → executor.execute(Runnable)** | Trong callback, trực tiếp qua TaskExecutor | Có thể bắt rejection tại execute, chủ động nhận/trả Future nếu dùng submit |
| **@Scheduled bean → @Async worker bean** | Spring proxy của worker | Phải bật @EnableAsync, gọi qua proxy và chọn executor đúng; tránh self-invocation |
| **@Scheduled và @Async trên cùng method** | Tùy advisor/proxy integration | Cần integration test chứng minh đường gọi, thread và lifecycle thực sự |
| **@Scheduled làm việc trực tiếp** | Không có dispatch bổ sung | Business logic chiếm scheduler thread trong toàn thời gian thực thi |

**Không double-dispatch** nếu không có chủ đích: một method đã được @Async chuyển vào worker rồi lại gọi supplyAsync vào commonPool có thể khiến công việc chạy ở pool thứ ba, gây khó phân định giới hạn tài nguyên và propagation context.

## 10. Có tự động overlap nếu scheduler poolSize lớn hơn 1 không?

Không thể trả lời “có” cho mọi trường hợp. Cần xem **đơn vị lặp** và **đường thực thi**.

- Với **một periodic Runnable** được đăng ký bằng **ScheduledThreadPoolExecutor.scheduleAtFixedRate / scheduleWithFixedDelay**, **các lượt thực thi của chính Runnable đó không overlap** theo hợp đồng JDK; nếu một lần lâu hơn period, lần sau có thể trễ.
- Với **nhiều lịch độc lập** (hai @Scheduled methods, hai schedule() khác nhau, hai khai báo @Scheduled trên cùng method), callback có thể chạy song song khi scheduler có nhiều worker và lịch đến hạn.
- Với một **callback periodic chỉ submit job** sang worker, các callback không overlap **không đồng nghĩa các business jobs không overlap**. Chúng là những Runnable khác nhau ở worker pool.
- Với **nhiều application instances**, cùng biểu thức cron có thể tạo các lượt business job đồng thời ở mỗi instance, bất kể mỗi instance chỉ có một scheduler thread.

### Ví dụ tình huống

~~~text
@Scheduled(fixedRate = 1 second)
scheduler poolSize=1, worker poolSize=2

t=0: callback A -> submit job A -> callback A returns
                         |
                         +-- worker-1 runs 5 seconds
t=1: callback B -> submit job B -> callback B returns
                         |
                         +-- worker-2 runs 5 seconds
t=2: callback C -> submits job C -> queued
...
~~~

Ở đây **callback lịch không nhất thiết chồng lấn**, nhưng **business job A và B chồng lấn**. Nếu nghiệp vụ là xuất báo cáo độc lập theo từng khoảng, điều này có thể chấp nhận. Nếu nghiệp vụ là cập nhật một bảng tổng hợp không thể chạy hai lượt cùng lúc, cần một **single-flight guard** hoặc cơ chế khóa bền vững.

## 11. Giới hạn business concurrency: single-flight guard

Để không tạo một business job thứ hai khi job trước còn chạy, có thể dùng **AtomicBoolean inFlight**:

~~~java
package example.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class NonOverlappingReportTrigger {
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private final ThreadPoolTaskExecutor executor;
    private final ReportProcessor processor;

    public NonOverlappingReportTrigger(
            @Qualifier("jobExecutor") ThreadPoolTaskExecutor executor,
            ReportProcessor processor) {
        this.executor = executor;
        this.processor = processor;
    }

    @Scheduled(fixedRate = 1_000)
    public void tick() {
        if (!inFlight.compareAndSet(false, true)) {
            // Task trước chưa kết thúc; lượt này được bỏ theo policy.
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    processor.process("single-flight-report");
                } finally {
                    inFlight.set(false);
                }
            });
        } catch (TaskRejectedException rejected) {
            // Task chưa được nhận; không giữ cờ mãi mãi.
            inFlight.set(false);
            throw rejected;
        }
    }
}
~~~

Đây là **một file Spring trong package example.scheduler**, dựa trên ReportProcessor và bean jobExecutor ở mục trước. Với default **AbortPolicy** và ThreadPoolTaskExecutor, TaskRejectedException biểu thị việc gửi task không thành công; nhánh catch reset guard.

**Invariant cần hiểu:**

1. Khi inFlight=false, callback có thể giành quyền bằng compareAndSet.
2. Từ lúc giành quyền đến lúc worker finally reset, callback khác thấy inFlight=true và bỏ qua lượt mới.
3. Nếu worker chưa chạy vì đang chờ queue, cờ **vẫn true**; điều đó thường đúng nếu muốn tính toàn bộ thời gian “job in-flight” gồm cả queue wait.
4. Nếu task không được nhận và ném TaskRejectedException, callback reset cờ để những lần sau còn thử lại.
5. Nếu process ném exception, finally của worker vẫn reset cờ.

**Giới hạn rất quan trọng:**

- AtomicBoolean chỉ bảo vệ **một instance trong một JVM**, không ngăn nhiều pod/instance chạy cùng job.
- Nếu process bị kill khi job đang chạy, trạng thái in-memory mất; không có durable recovery.
- Nếu handler dùng **DiscardPolicy** và âm thầm bỏ task, callback có thể đã set inFlight=true nhưng worker không bao giờ chạy finally; cờ bị kẹt. Dùng rejection policy **tường minh**, có quan sát.
- Nếu dùng **CallerRunsPolicy**, worker lambda có thể thực thi ngay trên scheduler thread khi executor bão hòa; lúc ấy callback không còn nhẹ. Chọn policy theo ảnh hưởng lên lịch.
- Nếu có một cơ chế khác có thể gửi cùng business job ngoài scheduler, guard chỉ bảo vệ những đường gửi đi qua cùng instance này.
- Nếu cần *không bỏ lượt* mà phải xử lý lần lượt mọi sự kiện, single-flight kiểu skip **không phù hợp**; cần queue bền vững hoặc coalescing theo hợp đồng dữ liệu.

**Một lịch không overlap** và **một job không overlap** là hai tính chất khác nhau. AtomicBoolean là ví dụ cách xây tính chất thứ hai trong process.

## 12. Cần lưu mọi lượt hay chỉ cần trạng thái mới nhất?

Tách scheduler khỏi worker có thể tạo ra **một job mới ở mỗi tick**, trong khi workload xử lý lâu hơn chu kỳ. Nếu cứ gửi thêm task vào queue, **queue wait tăng không giới hạn** hoặc mất kiểm soát bộ nhớ.

Chọn chính sách theo nhu cầu nghiệp vụ:

| Yêu cầu | Cách tiếp cận có thể phù hợp |
| --- | --- |
| Quét trạng thái hiện tại; nếu lượt trước còn chạy thì không cần lượt mới | **Single-flight + skip/coalesce** |
| Mỗi lượt đại diện cho sự kiện phải xử lý đúng một lần | **Durable queue/event log, idempotency và acknowledgement** |
| Chỉ được có một job đang chạy trên toàn cluster | **Distributed lock/lease** có fencing hoặc job coordinator |
| Nhiều báo cáo độc lập, có giới hạn tải | **Bounded worker pool + bounded queue + overload policy** |
| Muốn fixed delay từ **business completion** | Lập lịch lượt tiếp theo **sau khi Future/job thật kết thúc**, hoặc dùng cơ chế điều phối hiểu vòng đời job |
| Có deadline chặt | **Queue wait + execution + downstream timeout** chung một time budget |

**Không tăng queueCapacity vô hạn** để che giấu worker chậm. Hàng đợi dài khiến công việc được tiếp nhận nhưng chỉ bắt đầu sau nhiều chu kỳ, khi dữ liệu/credential đã cũ.

### “Delay tính từ job hoàn tất” nên làm thế nào?

Nếu business semantics yêu cầu **nghỉ 10 giây sau khi xuất báo cáo hoàn tất**, việc giữ callback scheduler đồng bộ đến hết job có thể đúng về thời gian nhưng chiếm scheduler thread. Một kiến trúc tách biệt có thể:

1. Scheduled callback khởi động job A trên worker.
2. Khi worker A hoàn tất, ghi nhận success/failure/cancel.
3. Từ completion đó, yêu cầu TaskScheduler **schedule** một callback tiếp theo tại **Instant.now().plus(delay)**.
4. Không tạo lịch periodic cố định song song; để một coordinator duy trì chính xác **một lượt hẹn tiếp theo**.
5. Xử lý shutdown, rejection và lỗi schedule mới; nếu tính bền vững quan trọng, lưu trạng thái hẹn trong job store.

Cách này yêu cầu quản lý state, nhưng phản ánh đúng **completion của business job**, không nhầm với completion của callback.

## 13. Executor bão hòa: nếu callback không block thì backlog đi đâu?

Giả sử scheduler tick mỗi giây và job mất 10 giây. Nếu worker xử lý ít việc hơn tốc độ submit, hệ thống có thể chuyển điểm nghẽn **từ scheduler thread sang worker queue**:

~~~text
Trigger callbacks: 1 job / second
            |
            v
Bounded TaskExecutor:
    2 workers, queue capacity 8
            |
            +-- workers bận
            +-- queue đầy dần
            +-- TaskRejectedException khi không nhận thêm
~~~

Vì callback gửi nhanh rồi return, dashboard có thể báo **scheduler callback duration rất nhỏ** trong khi công việc thực sự bị trễ hoặc bị từ chối.

Các chính sách cần biết:

- **AbortPolicy**: từ chối tường minh; Spring ThreadPoolTaskExecutor biểu diễn theo hợp đồng TaskRejectedException. Dễ đo số lượt không được nhận.
- **CallerRunsPolicy**: khi executor bão hòa và chưa shutdown, callback có thể **tự thực thi business job trên scheduler thread**. Đây là cơ chế backpressure nhưng **xóa ranh giới tách thread trong trường hợp quá tải**; scheduler có thể bắt đầu trễ những lịch khác.
- **DiscardPolicy**: có thể im lặng bỏ việc, khiến callback tưởng đã submit thành công. Không phù hợp nếu job bắt buộc được thực thi hoặc guard chờ worker finally.
- **DiscardOldestPolicy**: có thể bỏ một task đã nằm chờ rồi thử nhận task mới, ảnh hưởng tính nhất quán và Future của task cũ.

**Không thể bảo đảm “scheduler luôn không bao giờ block”** nếu dùng CallerRuns, blocking rejection handler, queue.put chờ chỗ hoặc logic callback tự thực hiện I/O. Kiến trúc tách biệt chỉ có tác dụng khi **đường giao việc cũng có giới hạn thời gian và chính sách quá tải rõ**.

### Khi submit bị reject, nên làm gì?

Điều đó tùy nghiệp vụ:

1. Nếu lượt tick là **best-effort quét dữ liệu hiện tại**, có thể ghi metrics, log ngắn và bỏ lượt theo policy.
2. Nếu tick đánh dấu công việc **không được phép mất**, cần ghi một job record/queue bền vững trước khi xác nhận tiếp nhận.
3. Nếu dự định retry, dùng **backoff hữu hạn, jitter, deadline** và idempotency. Đừng ngủ 30 giây ngay trong scheduler callback để “chờ pool có chỗ” khi có các lịch khác cần được kích hoạt.
4. Nếu executor đang **shutdown**, retry vào chính executor đó không giúp nó nhận thêm task.
5. Nếu business job có side effect, xác định trạng thái đã nhận, đang chạy, thành công, thất bại, cần retry, cancelled tách biệt.

**Scheduling không đồng nghĩa durability.** Một cron trong process dừng hoạt động khi process không chạy; task trong RAM có thể mất khi ứng dụng restart. Lịch định kỳ yêu cầu đảm bảo không mất lượt phải có cơ chế lưu/checkpoint khác.

## 14. Sự khác nhau giữa queue của scheduler và queue của worker

Trong mô hình hai pool có ít nhất **hai nơi chờ**:

~~~text
Trigger time -> scheduler delayed queue
                     |
                     v
             scheduled callback
                     |
                     v
            TaskExecutor work queue
                     |
                     v
               active worker
~~~

**Scheduler queue** lưu nhiệm vụ đã hẹn tới hạn hoặc chờ tới hạn. **Worker queue** lưu công việc đã được executor chấp nhận nhưng chưa có worker rảnh. Hai queue không thay thế nhau.

Ví dụ một callback đến hạn nhưng scheduler thread vẫn đang bận, dù worker pool rảnh, sẽ tạo **scheduler lag**. Ngược lại, callback chạy đúng giờ và gửi việc thành công, nhưng worker queue có 10 tác vụ, sẽ tạo **execution queue wait**.

Do vậy, khi điều tra “job chạy muộn”, phải phân biệt:

- **Trigger lag** = actual callback start − scheduled fire time.
- **Dispatch latency** = thời gian callback tính/submit vào executor.
- **Queue wait** = worker actual start − accepted/submitted time.
- **Execution duration** = worker completion − worker start.
- **End-to-end latency** = job completion − mốc nghiệp vụ bắt đầu cần xử lý.

**Đừng đo chung tất cả thời gian vào một nhãn “scheduler took 12 seconds”**; nó che nguyên nhân cần chỉnh. TriggerContext có thông tin lastScheduledExecution, lastActualExecution và lastCompletion cho một số kiểu trigger, nhưng đó vẫn nói về **callback do scheduler quản lý**, không tự biết completion của job delegate.

## 15. @Scheduled được gọi trên bao nhiêu instance?

Nếu một ứng dụng Spring chạy **3 pods**, mỗi pod có:

~~~java
@Scheduled(cron = "0 0 * * * *", zone = "UTC")
public void hourly() { /* ... */ }
~~~

thì thông thường **mỗi instance đều có scheduler riêng** và mỗi instance có thể chạy callback ở mốc giờ đó. @Scheduled của Spring **không tự biến thành cluster singleton**.

~~~text
pod-A: scheduler -> hourly() -> job A
pod-B: scheduler -> hourly() -> job B
pod-C: scheduler -> hourly() -> job C
~~~

Ngay cả khi mỗi pod dùng poolSize=1 và AtomicBoolean local, vẫn có ba job có thể cùng tác động lên database chung.

Nếu nghiệp vụ chỉ cho một job toàn hệ thống:

- Sử dụng distributed lease/lock được thiết kế đúng, kèm TTL và **fencing token** nếu cần ngăn writer cũ tiếp tục.
- Hoặc đưa lịch vào một **orchestrator/job scheduler** tập trung có state và leadership.
- Hoặc biến thao tác thành **idempotent** và xử lý cạnh tranh ở database với uniqueness/version constraints.
- Với workflow quan trọng, lưu **job identity và trạng thái** để hồi phục sau restart, tránh xem callback duy nhất như một bằng chứng thực thi thành công.

**Một scheduler thread duy nhất** chỉ có nghĩa một thread trong **một JVM/process**, không có ý nghĩa “một công việc duy nhất trên toàn cluster”.

## 16. Khi callback lỗi, lịch có còn chạy nữa không?

Cần phân biệt **JDK ScheduledExecutorService trực tiếp** với **Spring ThreadPoolTaskScheduler và ErrorHandler được cấu hình**.

Ở JDK ScheduledExecutorService:

- Với **scheduleAtFixedRate/scheduleWithFixedDelay**, nếu một lượt thực thi Runnable ném exception không được xử lý, các lượt tiếp theo của chính periodic task có thể bị **suppress** theo hợp đồng JDK.
- Future của chuỗi có thể hoàn tất ngoại lệ.
- Một ScheduledFuture của periodic task không phải dòng kết quả nghiệp vụ cho từng tick.

Trong Spring, ThreadPoolTaskScheduler có cơ chế **ErrorHandler** cho scheduled tasks; hành vi log/suppress/propagate phụ thuộc cấu hình và kiểu tác vụ. Không giả định ngoại lệ từ @Scheduled luôn dừng vĩnh viễn lịch hoặc luôn tự retry an toàn.

Khi callback chỉ gọi **executor.execute(...)**:

- Lỗi **submit/rejection** có thể xuất hiện ngay trong callback và được ErrorHandler của scheduler nhìn thấy.
- Lỗi **business job** xuất hiện sau đó trong worker và **không tự quay về stack** của callback đã return.
- Nếu dùng **ExecutorService.submit(Callable)**, lỗi thường được lưu vào Future và cần được quan sát bằng get, callback hoặc job status.
- Nếu dùng **CompletableFuture** hoặc @Async, cần xử lý **exceptional completion** hoặc handler phù hợp; không được coi không có log là không có lỗi.

Một chính sách tốt là ghi riêng **scheduler.callback.failure**, **executor.rejected**, **job.failed** và **job.completed** thay vì trộn vào một metric “cron failed”.

## 17. Khi nào nên dùng @Async, khi nào nên dùng execute/submit?

| Nhu cầu | Hướng triển khai |
| --- | --- |
| Chỉ muốn trigger gọi worker đơn giản | @Scheduled → TaskExecutor.execute |
| Muốn handle kết quả và cancel theo job | TaskExecutor.submit hoặc API trả Future/CompletableFuture có quản lý |
| Muốn gọi async method theo business service | @Scheduled trên bean A → @Async trên bean B qua Spring proxy |
| Muốn chỉ chọn executor theo tên | @Async("jobExecutor") hoặc inject đúng TaskExecutor |
| Muốn transaction/method security trong worker | Gọi service bean Spring khác qua proxy tại worker boundary, với context/transaction phù hợp |
| Muốn công việc dài chạy bền vững | Durable job engine/queue và state, không chỉ @Scheduled + in-memory executor |

**@Async cần proxy** trong chế độ Spring AOP phổ biến: bean phải được Spring quản lý, @EnableAsync bật và lời gọi phải đi qua proxy; self-invocation như this.asyncMethod() trong cùng bean có thể bỏ qua advice. Khi dùng TaskExecutor.execute tường minh, ranh giới chuyển worker được tạo **ngay ở execute**, không phụ thuộc @Async.

Nếu job có **@Transactional**, ranh giới transaction ở request/scheduler thread **không tự truyền** sang worker. Nên đặt transaction trên một service method được worker gọi qua proxy, hoặc quản lý transaction tại worker theo contract. Đừng giữ EntityManager/request-scoped bean sang thread khác.

## 18. Ngữ cảnh bảo mật, MDC và dữ liệu đầu vào

Scheduler callback và worker có thể chạy trên **hai thread khác nhau**, nên cần kiểm soát data/context propagation:

- **Dữ liệu business:** chụp immutable command/DTO khi callback tạo job, truyền qua parameter; không đặt ID job của từng lượt vào mutable instance field singleton.
- **MDC/tracing:** cần TaskDecorator hoặc propagation mechanism phù hợp; tự động truyền toàn bộ ThreadLocal không được bảo đảm.
- **SecurityContext:** cần Spring Security wrapper như DelegatingSecurityContextAsyncTaskExecutor hoặc chính sách service identity/authorization phù hợp. Một cron chạy không có HTTP user hiện tại, nên đừng tự nhận nó có principal của request gần nhất.
- **Transaction:** thiết lập transaction ở worker nếu logic cần, không coi transaction của callback sống xuyên thread.
- **Deadline:** chụp deadline vào job data, tính cả queue wait và thời gian chạy; nếu job bắt đầu quá muộn có thể phải bỏ trước khi tác động dữ liệu.
- **Resource ownership:** file/stream/connection mở ở callback có thể không hợp lệ khi worker thực thi sau đó; truyền ID/lifecycle handle bền vững khi phù hợp.

Trong cron chạy nền, thường nên dùng **service account với quyền tối thiểu** hoặc một job identity được quản lý, thay vì lấy SecurityContext của một người dùng bất kỳ từ ThreadLocal của pool.

## 19. Shutdown và cancellation: hai pool, hai vòng đời

Khi ứng dụng dừng, cả **scheduler** và **worker executor** đều cần có chiến lược lifecycle.

Trong mô hình direct:

~~~text
scheduler owns scheduled callback AND business work
    |
    +-- shutdown/cancel callback có thể tác động trực tiếp
        tới task đang thực thi theo implementation
~~~

Trong mô hình delegated:

~~~text
scheduler: ngừng kích hoạt callback mới
    |
    +-- worker executor vẫn có task đang chạy hoặc chờ
    |
    +-- phải đóng/đợi worker theo policy riêng
~~~

Những tình huống cần xử lý:

1. **Dừng nhận lịch mới** trước khi đóng worker executor để callback không tiếp tục submit vào pool đã shutdown.
2. **Đợi các job đã nhận** hoàn tất trong thời hạn phù hợp, hoặc yêu cầu hủy/cancel và ghi trạng thái nếu phải dừng.
3. **Đừng tin ScheduledFuture.cancel(...) sẽ hủy cả worker jobs đã được submit**.
4. Nếu worker đang có I/O chặn, việc ngắt thread là best effort; cần downstream timeout.
5. Nếu task đã nằm trong queue, cleanup trong body task có thể chưa từng chạy; tài nguyên tạo trước submit cần owner riêng.
6. Nếu job không được phép mất qua restart, chỉ dùng in-memory executors là chưa đủ; phải lưu job bền vững.

Spring có lifecycle hỗ trợ graceful shutdown đối với các executor/scheduler tương ứng; các thuộc tính cụ thể thay đổi theo implementation và phiên bản. Cần test shutdown trong deployment thật, không chỉ gọi close context trên test nhanh rồi giả định mọi job đã xong.

## 20. Monitoring đúng hai tầng: trigger và worker

Có ít nhất hai nhóm metric cần tách rõ.

### Nhóm A — scheduler

- **Scheduled fire count**: số lần callback theo lịch thực sự được kích hoạt.
- **Scheduler lag**: thời điểm callback thực tế bắt đầu so với mốc dự kiến.
- **Callback duration**: thời gian chạy chính callback.
- **Callback failure**: exception xảy ra trước khi callback return.
- **Scheduler active threads và delayed queue depth**, khi implementation có API/metrics đáng tin.
- **Drift theo cron zone** và lịch chuyển DST nếu có ảnh hưởng.

### Nhóm B — worker

- **Submission attempts/accepted/rejected**: bao nhiêu lần scheduler cố giao việc và kết quả ra sao.
- **Task queue wait**: từ khi accepted đến khi worker bắt đầu chạy.
- **Active worker threads**, worker pool size, max threads, queue size/capacity.
- **Business job duration, success, failure, cancelled**, deadline miss.
- **In-flight job count** và số lần skip/coalesce do single-flight guard.
- **Downstream resource saturation**, như DB connections, HTTP client limits hoặc CPU.

Khi dùng **queueCapacity=0** với ThreadPoolTaskExecutor, queue được tổ chức bằng **SynchronousQueue** không có buffer; queueSize có thể luôn bằng 0 ngay cả khi executor bão hòa và reject. Đừng coi queueSize=0 là bằng chứng worker rảnh.

Một dashboard tốt cho tình huống “cron đúng giờ nhưng report cập nhật chậm” sẽ so sánh:
- Callback bắt đầu đúng lịch?
- Submit accepted hay rejected?
- Worker bắt đầu sau bao lâu?
- Job hoàn tất hay fail?
- Có hơn một job cùng loại đang in-flight?
- Một pod hay nhiều pods đang cùng xử lý?

Nếu chỉ log **"@Scheduled fired"**, bạn chưa chứng minh công việc nghiệp vụ đã bắt đầu, càng chưa chứng minh nó hoàn tất.

## 21. Test ThreadPoolTaskScheduler trong Spring mà không phụ thuộc đồng hồ thực

Một integration test với Spring nên kiểm tra **cấu hình và đường chạy**, không kỳ vọng cron xảy ra đúng một mili giây trên CI.

Cách kiểm tra:

1. Tạo ThreadPoolTaskScheduler với prefix **trigger-** và ThreadPoolTaskExecutor prefix **job-**.
2. Dùng method được scheduling hoặc gọi TaskScheduler.schedule(Runnable, Instant.now()) trong test.
3. Trong callback, ghi thread identity/name, gửi việc sang executor.
4. Ở worker, ghi thread identity/name rồi chờ **CountDownLatch release**.
5. Trong khi worker đang chờ, schedule callback thứ hai và assert callback này vẫn chạy trong thời hạn.
6. Mở release; chờ worker-finished latch rồi đối chiếu kết quả.
7. Đóng ApplicationContext; đảm bảo không giữ worker vô hạn sau test.

Với **@Scheduled** thực tế, có thể dùng fixedDelay lớn và điều phối bằng latch, hoặc tách logic trigger thành method có thể gọi thủ công để test chính sách submit. Lịch theo cron không nên được test bằng cách ngủ tới phút tiếp theo nếu chỉ cần xác minh logic.

### Test overlap theo business contract

Nếu job không được chạy đồng thời:

1. Worker A đặt **inFlight=true** rồi chờ latch.
2. Trigger tick B xảy ra khi A chưa hoàn tất.
3. Xác minh B bị **skip/coalesce** hoặc được xếp hàng theo chính sách.
4. Mở latch để A hoàn tất và guard được reset.
5. Trigger tick C, xác minh một job mới được chấp nhận.
6. Nếu guard dùng AtomicBoolean, chạy test nhiều callbacks đồng thời để kiểm chứng **compareAndSet**, không dùng cờ boolean thường không đồng bộ.

### Test overload

- Cấu hình pool với worker và queue nhỏ.
- Giữ workers bằng latch để bão hòa.
- Gọi callback submit và xác minh chính sách **TaskRejectedException**, metric và việc reset guard.
- Nếu policy là CallerRunsPolicy, xác minh callback có thể chạy business task trong chính scheduler thread và đo tác động tới lịch tiếp theo.
- Đừng dùng DiscardPolicy rồi chỉ assert callback không ném exception; task có thể đã mất.

## 22. Những nhầm lẫn phổ biến

**“TaskScheduler có nghĩa Spring luôn dùng một trigger thread riêng và một executor xử lý riêng.”** Sai. ThreadPoolTaskScheduler thực thi scheduled Runnable ngay trên scheduler thread(s).

**“@Scheduled làm business job luôn chạy trên thread background riêng biệt.”** Không đủ chính xác. Method được gọi bởi scheduler thread; việc có worker pool khác phụ thuộc implementation hoặc code delegate.

**“Tăng scheduler poolSize từ 1 lên 10 là tách được trách nhiệm trigger và worker.”** Sai. Những thread đó vẫn thuộc scheduler; nó chỉ cho phép nhiều callback cùng thực thi.

**“FixedDelay=5 giây chắc chắn hai business jobs không overlap.”** Sai nếu scheduled callback chỉ submit job rồi return. Delay tính từ callback completion.

**“FixedRate sẽ chạy một periodic Runnable cùng lúc trên 2 worker nếu period quá ngắn.”** Không đúng với ScheduledThreadPoolExecutor cho cùng một periodic task; các lượt của task đó không overlap. Tuy nhiên, công việc được delegate từ các lượt callback có thể overlap.

**“CronTrigger luôn chạy bù tất cả mốc bị lỡ.”** Không thể khẳng định. Cron trigger semantics có biến thể và phụ thuộc completion/lịch sử; cần xem version/API và chính sách nghiệp vụ.

**“ScheduledFuture của callback delegate chứng minh business job đã hoàn thành.”** Sai. Nó chỉ phản ánh callback được scheduler quản lý; Future của worker hoặc job status mới mô tả việc nghiệp vụ.

**“Hủy ScheduledFuture sẽ hủy mọi Future của worker đã tạo từ callback.”** Sai. Các handle này độc lập trừ khi ứng dụng chủ động nối cancellation.

**“@Scheduled có @Async trong source chắc chắn chạy đúng pool.”** Không được đảm bảo nếu thiếu @EnableAsync, đường gọi qua proxy hoặc cấu hình executor. Test runtime.

**“Callback return nghĩa là đã xử lý xong và có thể trả success.”** Sai khi callback chỉ submit.

**“Scheduler không block thì hệ thống không overload.”** Sai. Backlog có thể chuyển sang worker queue, downstream connections hoặc CPU.

**“CallerRunsPolicy luôn giữ scheduler không bận.”** Sai. Khi worker pool bão hòa, scheduler callback có thể bị buộc chạy công việc ngay trên scheduler thread.

**“Một AtomicBoolean đảm bảo toàn cluster chỉ có một job.”** Sai. Nó chỉ bảo vệ một object instance/JVM.

**“Cứ thêm Thread.sleep ở callback là worker có thời gian xử lý.”** Không phải backpressure an toàn; ngủ giữ scheduler thread và không nêu rõ lifecycle/in-flight.

**“Không có exception trong @Scheduled nghĩa là job đã thành công.”** Sai khi lỗi phát sinh sau đó ở worker.

**“Tôi có một lịch cron nên job không thể chạy trùng.”** Sai khi có nhiều pods, nhiều trigger registrations hoặc business job delegate.

**“Trong cron có user đang đăng nhập gần nhất để mang SecurityContext sang worker.”** Sai. Lịch nền không tự có HTTP principal của request khác; cần identity và authorization riêng.

## 23. Câu hỏi tự kiểm tra

**Câu 1.** ThreadPoolTaskScheduler poolSize=1 có callback A chặn 20 giây. Callback B tới hạn trong lúc A đang chạy. B có được chạy ngay không?

**Đáp án:** Không được bảo đảm; với một scheduler thread đang bị giữ, B thường phải đợi A kết thúc. Cần giảm thời gian callback hoặc tăng pool/tách worker nếu phù hợp.

**Câu 2.** Callback @Scheduled chỉ gọi jobExecutor.execute(...) rồi return. Job chạy 30 giây, @Scheduled(fixedDelay=5_000). Lượt thứ hai phải đợi 30 giây không?

**Đáp án:** Không. FixedDelay được tính sau khi **callback gửi việc** hoàn tất, nên lượt kế tiếp có thể tới trong khi job trước vẫn chạy.

**Câu 3.** Một ScheduledThreadPoolExecutor có 4 threads và một Runnable được scheduleAtFixedRate mỗi giây; mỗi run mất 5 giây. Những lần chạy của cùng periodic Runnable có overlap không?

**Đáp án:** Không theo hợp đồng JDK; các lần kế tiếp có thể bắt đầu trễ. Các Runnable khác vẫn có thể chạy song song trên các worker còn lại.

**Câu 4.** Nếu callback periodic gửi hai job độc lập tới pool 2 workers, hai job đó có overlap không?

**Đáp án:** Có thể. Không overlap của callback periodic không đồng nghĩa không overlap của jobs đã delegate.

**Câu 5.** TaskScheduler.schedule trả ScheduledFuture xong rồi, nhưng job do callback submit vẫn đang chạy. Có phải bug của scheduler không?

**Đáp án:** Không. Future của scheduler chỉ đại diện callback; cần worker Future/job status để theo dõi kết quả nghiệp vụ.

**Câu 6.** Tại sao tăng scheduler poolSize có thể không phải giải pháp tốt khi công việc là blocking DB call?

**Đáp án:** Vì các DB calls vẫn chiếm scheduler threads và cạnh tranh downstream connections. Tách worker với giới hạn, queue và timeout thường dễ vận hành hơn.

**Câu 7.** Khi worker pool bão hòa với CallerRunsPolicy, callback @Scheduled có thể làm công việc nặng không?

**Đáp án:** Có. CallerRuns có thể thực thi Runnable trên chính scheduler thread đang gọi execute; kiến trúc tách thread tạm thời không còn.

**Câu 8.** Làm cách nào để không có hai business jobs chạy cùng lúc trong một JVM, dù callback cứ được trigger?

**Đáp án:** Dùng single-flight guard (như AtomicBoolean.compareAndSet) với reset trong worker finally và trong nhánh submit rejected; hoặc một cơ chế điều phối khác đúng contract.

**Câu 9.** Guard AtomicBoolean trong một pod có ngăn pod khác chạy cùng job không?

**Đáp án:** Không. Cần cơ chế cluster-wide như distributed lease/lock, job coordinator hoặc idempotency/constraints tại storage.

**Câu 10.** FixedDelay có thể đo từ khi business job hoàn tất khi callback đã delegate không?

**Đáp án:** Không tự động. Muốn như vậy phải điều phối lịch sau completion thật hoặc giữ callback cho tới completion, với đánh đổi scheduler thread bị chiếm.

**Câu 11.** Ứng dụng báo scheduler callback duration 2ms nhưng báo cáo đến muộn 2 phút. Có thể xảy ra không?

**Đáp án:** Có. Callback chỉ submit nhanh nhưng job chờ 2 phút trong worker queue hoặc downstream bị nghẽn.

**Câu 12.** Hủy recurring ScheduledFuture có tự hủy toàn bộ jobs đã submit qua executor từ những tick trước không?

**Đáp án:** Không mặc nhiên. Cần quản lý cancellation từng job.

**Câu 13.** Nếu @Scheduled method ném exception, mọi implementation đều chắc chắn dừng lịch vĩnh viễn không?

**Đáp án:** Không. JDK periodic tasks có semantics suppress sau uncaught exception; Spring scheduler có ErrorHandler và cấu hình khác. Kiểm tra implementation thực tế.

**Câu 14.** @Scheduled trên ba instance Spring nghĩa là chỉ một callback toàn hệ thống chạy đúng giờ không?

**Đáp án:** Không. Mỗi instance có scheduler riêng nếu không có điều phối cluster.

**Câu 15.** SimpleAsyncTaskScheduler giống ThreadPoolTaskScheduler trong mọi trường hợp chứ?

**Đáp án:** Không. SimpleAsyncTaskScheduler có scheduler thread và chiến lược tạo thread khác; Spring lưu ý fixed-delay tasks của loại này hoạt động trên scheduler thread, trong khi ThreadPoolTaskScheduler thực thi callback trên chính các scheduler threads.

## 24. Checklist triển khai

- [ ] Xác định **TaskScheduler implementation thực tế**, không chỉ interface.
- [ ] Đối với **ThreadPoolTaskScheduler**, nhớ callback chạy trên scheduler threads.
- [ ] Chỉ định kích thước scheduler pool theo số và độ dài callback.
- [ ] Quyết định callback thực hiện nghiệp vụ trực tiếp hay **delegate** vào TaskExecutor.
- [ ] Nếu delegate, tạo **worker pool hữu hạn và queue có giới hạn**, có rejection policy.
- [ ] Phân biệt **callback completion** với **business job completion**.
- [ ] Đánh giá fixedRate/fixedDelay/cron theo **thời gian thực sự của callback**, không giả định chờ worker.
- [ ] Nếu business job không được overlap, triển khai single-flight/lock hoặc job coordinator phù hợp.
- [ ] Nếu job phải được xử lý đủ mọi lượt, không dùng skip/coalesce mà không có chủ đích.
- [ ] Kiểm soát overload khi worker bị bão hòa, đặc biệt CallerRuns/Discard.
- [ ] Có metrics scheduler lag, callback duration, worker queue wait, active jobs, failures/rejections.
- [ ] Tách cancellation lịch và cancellation những jobs đã được submit.
- [ ] Xác định trình tự shutdown scheduler rồi worker và deadline cleanup.
- [ ] Nếu nhiều pods, thiết kế distributed coordination hoặc idempotency.
- [ ] Nếu có @Async, bảo đảm lời gọi qua Spring proxy và executor đúng tên.
- [ ] Không để business state mutable của từng lượt trong singleton bean.
- [ ] Dùng transaction, security identity và tracing phù hợp cho worker thread.
- [ ] Kiểm thử có latch/Future.get(timeout), tránh timing giả bằng sleep.
- [ ] Với job quan trọng, lưu trạng thái bền vững và cân nhắc job scheduler/queue chuyên dụng.

## Kết luận

**TaskScheduler điều phối thời điểm được phép kích hoạt; Runnable/callback đã được hẹn sẽ thực thi theo mô hình thread của implementation.** Đặc biệt, **ThreadPoolTaskScheduler chạy callback trên scheduler threads**, chứ không tự chuyển business logic sang một TaskExecutor khác. Nếu callback làm việc nặng trực tiếp, nó giữ scheduler thread; nếu callback giao việc sang worker pool, scheduler có thể rảnh sớm nhưng cần theo dõi trạng thái, lỗi, backlog, overlap và cancellation của business job riêng.

Để thiết kế concurrency có thể giải thích được, hãy đặt rõ **scheduler thread cho việc kích hoạt ngắn gọn** và **worker pool cho xử lý tài nguyên nặng** khi thực sự cần tách biệt, đồng thời không đánh đồng fixedDelay/callback completion với business completion. Sự tách biệt này giúp đo đúng scheduler lag so với queue wait, lựa chọn overload policy phù hợp, tránh chạy trùng trong cluster và không kết luận thành công chỉ vì một callback đã gửi xong task.

**Tài liệu tham khảo chính thức:**

- Spring Framework: [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html) — TaskScheduler, @Scheduled, ThreadPoolTaskScheduler, SimpleAsyncTaskScheduler và Trigger.
- Spring Javadoc: [ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html) và [ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html).
- Spring Javadoc: [TaskScheduler](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/TaskScheduler.html), [@Scheduled](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html), [CronTrigger](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/CronTrigger.html).
- Java SE 17: [ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html), [ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html), [CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="task-scheduler-008">Chọn lập lịch trực tiếp hay annotation theo mức cần điều khiển</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn lập lịch trực tiếp hay annotation theo mức cần điều khiển

> **Module:** Spring Framework – concurrency  
> **Nhóm:** TaskScheduler  
> **Mục tiêu:** Biết khi nào dùng `@Scheduled` để khai báo tác vụ lặp ổn định, khi nào gọi `TaskScheduler` trực tiếp để chủ động tạo, thay đổi, hủy và theo dõi lịch ở runtime; hiểu rằng cả hai có thể dùng cùng hạ tầng scheduler và đều cần kiểm soát tải, lỗi, thời gian và nhiều instance.

## 1. Câu trả lời nhanh

Trong Spring Framework, **`@Scheduled` là cách mô tả lịch cố định trên một phương thức do Spring quản lý**, còn **`TaskScheduler` là API điều khiển lịch bằng chương trình**. Đây không phải hai hệ thống thực thi tách biệt: một phương thức `@Scheduled` thường được đăng ký với scheduler tương thích, trong khi code có thể inject chính `TaskScheduler` ấy để lên lịch khác.

Chọn **`@Scheduled`** khi công việc có lịch tương đối ổn định, biết trước lúc ứng dụng khởi động, không đòi mỗi job có một handle để hủy/thay thế theo yêu cầu. Ví dụ: quét dữ liệu rác mỗi đêm, cập nhật cache định kỳ, ghi thống kê tổng hợp 5 phút/lần. Lịch có thể được externalize bằng property, nhưng được đăng ký theo vòng đời bean khi context khởi tạo.

Chọn **gọi `TaskScheduler` trực tiếp** khi lịch phụ thuộc vào dữ liệu hoặc hành động runtime: mỗi đơn hàng có thời điểm hết hạn riêng, người dùng bật/tắt một job, cần hủy một callback chưa chạy, đổi chu kỳ tại thời điểm thực thi, hoặc đưa một `Trigger` vào hệ thống do ứng dụng tính toán.

**Không chọn chỉ vì hiệu năng.** `@Scheduled` không phải “chậm hơn” `TaskScheduler`, và code gọi scheduler trực tiếp không tự có tính bền vững hoặc tính đúng cao hơn. Các yếu tố quyết định là **ai sở hữu lịch, ai có thể đổi/hủy lịch, lịch sống đến khi nào và hệ thống cần bảo đảm gì sau restart**.

## 2. Bức tranh cơ chế: annotation và API đều cần scheduler

~~~text
Nhánh A: lịch khai báo
  Spring context khởi tạo bean
         |
         v
  @EnableScheduling / scheduled annotation processor
         |
         v
  Tìm các phương thức @Scheduled
         |
         v
  Đăng ký tác vụ định kỳ vào scheduler
         |
         v
  ThreadPoolTaskScheduler / hạ tầng phù hợp
         |
         v
  Thực thi callback theo lịch

Nhánh B: lịch lập bằng chương trình
  Service nhận command hoặc dữ liệu runtime
         |
         v
  taskScheduler.schedule(...)/scheduleAtFixedRate(...)
         |
         v
  Giữ ScheduledFuture để cancel/kiểm tra
         |
         v
  Cùng scheduler (nếu inject cùng bean)
         |
         v
  Thực thi callback theo lịch
~~~

`TaskScheduler` là interface `org.springframework.scheduling.TaskScheduler`. Nó có API lên lịch một lần theo `Instant`, lên lịch bằng `Trigger`, chạy chu kỳ fixed-rate và fixed-delay.

`ThreadPoolTaskScheduler` là implementation thông dụng của Spring. Nó dùng scheduler thread pool có cấu hình, cung cấp vòng đời Spring, theo dõi và các hook liên quan. Khi dùng implementation này, **task thường thực thi ngay trên các scheduler thread**. Không được tưởng rằng scheduler chỉ gửi tín hiệu rồi một thread pool vô hạn khác tự chạy phần logic nặng.

`@Scheduled` là annotation trong package `org.springframework.scheduling.annotation`. Annotation chỉ có tác dụng khi Spring xử lý bean có lịch và scheduling được kích hoạt đúng context. Một method Java thông thường được gọi thủ công không vì thế trở thành một scheduled invocation.

## 3. Ma trận chọn cách lập lịch

| Yêu cầu | `@Scheduled` | `TaskScheduler` trực tiếp |
| --- | --- | --- |
| Chạy mỗi 5 phút hoặc vào giờ định sẵn, cấu hình ổn định | Rất phù hợp | Được, nhưng thêm code quản lý |
| Sử dụng property cho cron hoặc fixed delay từ lúc startup | Hỗ trợ tốt | Hỗ trợ qua code/config |
| Mỗi khách hàng có một lịch khác nhau | Không tiện, số phương thức không tương ứng số job | Phù hợp hơn để tạo lịch runtime |
| Có API “hủy job X” | Không có handle per-business-job trực tiếp từ annotation | Giữ và `cancel()` `ScheduledFuture` |
| Cần đổi chu kỳ lập tức mà không restart | Không tự động chỉ nhờ sửa property trong môi trường | Hủy và đăng ký lại theo code |
| Chỉ cần chạy một lần sau 30 giây | Không phải use case tự nhiên của `@Scheduled` | `schedule(Runnable, Instant)` |
| Lịch dựa trên kết quả bước trước hoặc điều kiện runtime | Có thể phải viết logic phụ | `Trigger` hoặc schedule lại sau completion |
| Tác vụ phải tồn tại qua JVM crash/restart | **Không tự bảo đảm** | **Không tự bảo đảm** |
| Chạy một lần toàn cluster khi có nhiều replica | **Không tự bảo đảm** | **Không tự bảo đảm** |
| Tránh đụng độ worker/DB khi quá tải | Cần thiết kế riêng | Cần thiết kế riêng |
| Muốn ít code, cấu hình dễ đọc với Spring | Thường tốt hơn | Tốt nếu cần quyền điều khiển thật sự |

Quy tắc quyết định: nếu lịch là **một phần của cấu hình triển khai**, hãy bắt đầu bằng `@Scheduled`. Nếu lịch là **một thực thể nghiệp vụ có ID, trạng thái và vòng đời riêng**, hãy bắt đầu từ API `TaskScheduler` hoặc một job scheduler bền vững hơn.

## 4. Lập lịch bằng @Scheduled: khai báo ngay trên bean

Trong một Spring Framework application, bật xử lý lịch:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
~~~

Đây là **configuration snippet**; phải nằm trong context chứa bean muốn lập lịch. Khi dùng Spring Boot, cần xem các auto-configuration và `@EnableScheduling` thực tế, không tự suy đoán mọi dự án Boot đều bật lịch.

Bean có method:

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheCleanupJob {
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        // Xóa những entry đã hết hạn theo logic ứng dụng.
    }
}
~~~

Đây là đoạn mã **tích hợp**, nội dung `cleanup()` cần implementation thực tế. `fixedDelay = 60_000` có đơn vị mặc định milliseconds: lần chạy tiếp theo được tính sau khi lần hiện tại **hoàn tất**, không phải cứ đúng 60 giây tính từ lúc bắt đầu.

Đặc trưng của hướng khai báo:

- Đăng ký theo vòng đời bean trong `ApplicationContext`.
- Mức điều khiển phù hợp với lịch **biết trước**: cron, fixed delay/fixed rate, initial delay và cấu hình thay thế bằng property.
- Không phải vì method có annotation mà bạn có một `ScheduledFuture` gắn với business ID để tự gọi `cancel()`.
- Return value của method imperative `@Scheduled` thường không được dùng làm kết quả nghiệp vụ. Đừng mong `return orderId` chuyển thành một job result cho API caller.
- Với use case thông thường, method nên không có tham số để scheduler có thể gọi mà không cần request input.
- Không chuyển thông tin của một request web vào field mutable của singleton scheduled bean để chờ lần chạy sau: đây là shared state dễ tạo race.

## 5. fixedRate, fixedDelay và cron khác nhau

`@Scheduled` hỗ trợ ba cách mô tả lịch phổ biến:

~~~java
@Scheduled(fixedRate = 5_000)
public void metricsPulse() { /* task nhanh */ }

@Scheduled(fixedDelay = 5_000)
public void reconcileAfterLastRun() { /* task dài */ }

@Scheduled(cron = "0 */5 * * * *", zone = "UTC")
public void everyFiveMinutes() { /* task theo đồng hồ */ }
~~~

Ba method trên là **các snippet mô tả**, không phải một Java class độc lập. Đừng chạy cả ba để thực hiện cùng một nghiệp vụ trừ khi thực sự cần ba lịch riêng.

| Loại | Mốc tính lần chạy kế tiếp | Ý nghĩa |
| --- | --- | --- |
| `fixedRate` | Lịch theo chu kỳ tính từ các thời điểm bắt đầu dự kiến | Thích hợp heartbeat/poll định kỳ; có thể trễ khi scheduler bận |
| `fixedDelay` | Sau khi **lần chạy trước hoàn tất** cộng delay | Thích hợp khi cần khoảng nghỉ giữa hai lượt |
| `cron` | Biểu thức dựa trên thời điểm trên lịch | Thích hợp chạy mỗi sáng, đầu giờ, ngày trong tuần... |

Ví dụ task mất 3 giây và `fixedDelay=5s`: nếu start tại `t=0`, hoàn tất `t=3s`, lần tiếp theo dự kiến khoảng `t=8s`, chưa xét scheduler contention. Với `fixedRate=5s`, mốc định kỳ dự kiến sẽ là `t=0,5,10...`, nhưng runtime không bảo đảm callback bắt đầu đúng tuyệt đối tại mọi mốc.

Đừng khẳng định rằng một fixed-rate task trên `ScheduledThreadPoolExecutor` mặc nhiên sẽ chạy **chồng lấn với chính nó** khi mất hơn một period: implementation JDK này không chạy chồng lấn những lần thực thi kế tiếp của **cùng một periodic task**. Tuy nhiên các lịch khác nhau, annotation repeatable, task chạy `@Async`, và các instance ứng dụng khác nhau vẫn có thể tạo **concurrency** thực tế.

Spring cron dùng **sáu trường**, có trường **giây** ở đầu: `0 */5 * * * *` là phút 0, 5, 10... tại giây 0. Không sao chép một biểu thức Unix cron năm trường rồi nghĩ rằng thứ tự giống hệt.

## 6. Chỉnh lịch khai báo qua property, và giới hạn runtime

Để lịch khác nhau giữa các môi trường triển khai:

~~~java
@Component
public class DailyCleanupJob {
    @Scheduled(cron = "${jobs.cleanup.cron:-}", zone = "UTC")
    public void cleanup() {
        // Công việc được gọi ở các mốc cron.
    }
}
~~~

Trong `application.yaml`:

~~~yaml
jobs:
  cleanup:
    cron: "0 0 2 * * *"
~~~

Đây là **mã Spring và cấu hình tích hợp**. Dấu `-` là giá trị disabled cron được Spring hỗ trợ khi dùng `@Scheduled`: cho phép tắt lịch bằng property. Tên property trong ví dụ là tùy ứng dụng.

Giới hạn: giá trị placeholder/SpEL thông thường được Spring đọc khi đăng ký lịch trong quá trình khởi tạo bean/context. **Thay đổi giá trị trong một cấu hình bên ngoài không mặc nhiên đăng ký lại lịch ngay lập tức** trong một ứng dụng thông thường. Nếu một hệ thống có hot reload config chuyên dụng, cần xác nhận nó thật sự hủy lịch cũ và đăng ký lịch mới; không đoán từ việc bean đọc lại property.

Khi cần người dùng nhấn “đổi từ 5 phút thành 10 phút” có hiệu lực tại runtime, hãy có một **service điều phối lịch bằng code** thay vì dùng một `@Scheduled` cố định rồi thay field mutable trong method.

## 7. Lập lịch trực tiếp: điều khiển bằng TaskScheduler

Inject `TaskScheduler` vào một service. Ba nhóm API thường dùng:

~~~java
ScheduledFuture<?> oneTime =
    scheduler.schedule(
        () -> doOneTimeWork(),
        Instant.now().plusSeconds(30));

ScheduledFuture<?> fixedRate =
    scheduler.scheduleAtFixedRate(
        () -> poll(),
        Duration.ofSeconds(10));

ScheduledFuture<?> fixedDelay =
    scheduler.scheduleWithFixedDelay(
        () -> processBatch(),
        Duration.ofSeconds(10));
~~~

Đây là **các snippet API**, giả định có `scheduler`, `doOneTimeWork`, `poll` và `processBatch`. Import `TaskScheduler`, `ScheduledFuture`, `Duration`, `Instant`. Cách đặt lịch thực tế nằm ở các overload tương ứng và implementation được inject.

Khác với annotation, **caller chủ động quyết định khi nào đăng ký**: lúc nhận API request, lúc đọc một bản ghi deadline, khi công việc trước hoàn tất hoặc sau khi áp dụng business rule. Lời gọi trả một `ScheduledFuture` (trường hợp lập lịch với một số `Trigger` có thể không có lần chạy tiếp theo và trả `null`) để ứng dụng theo dõi hoặc cancel.

`schedule(Runnable, Instant)` đặt **một lần thực thi** vào thời điểm cho trước; khi đặt thời điểm đã trôi qua, scheduler có thể thực thi ngay hoặc sớm nhất có thể. Nó không lưu một job bền vững vào database: nếu JVM chết trước thời điểm đó, task được giữ trong bộ nhớ có thể mất.

`scheduleAtFixedRate` và `scheduleWithFixedDelay` tạo **lịch lặp**. Nếu đã nhận handle, controller/service có thể gọi `cancel(false)` khi job bị dừng theo nghiệp vụ. Điều này có ý nghĩa vì business user có thể muốn bật/tắt lịch mà không cần rebuild/redeploy ứng dụng.

## 8. ScheduledFuture: quyền hủy không đồng nghĩa rollback

Với API `TaskScheduler`, handle có thể giữ lại:

~~~java
ScheduledFuture<?> handle =
    scheduler.scheduleAtFixedRate(action, Duration.ofSeconds(15));

// Khi nhận yêu cầu dừng:
boolean accepted = handle.cancel(false);
~~~

Đây là **mẫu tích hợp**, biến `scheduler`/`action` phải được cung cấp. `cancel(false)` ngăn những lần kích hoạt kế tiếp khi cancel thành công, **không yêu cầu interrupt một lượt đang chạy**. Với `cancel(true)`, implementation có thể cố gửi interrupt đến task, nhưng không bảo đảm JDBC, HTTP, file I/O hay remote side effect thực sự dừng.

Một số lưu ý:

- Nếu task đã hoàn tất, đã bị hủy, hoặc scheduler đã từ chối, `cancel` không nên được diễn giải là “chắc chắn mọi thứ đã dừng”.
- Lịch repeating bị cancel có thể vẫn có **một lần chạy đang in-flight**; handler nghiệp vụ phải idempotent và kiểm tra trạng thái nếu cần.
- Giữ `ScheduledFuture` chỉ trong `Map` của JVM là điều khiển **cục bộ**. Sau restart, map mất; không có automatic restore lịch theo business ID.
- Hủy lịch cũ rồi đăng ký lại có một khoảng chuyển tiếp. Nếu song song có nhiều caller, cần khóa/trạng thái để tránh hai lịch cùng đăng ký.
- Nếu hủy vì request hết deadline, vẫn cần I/O timeout thật ở HTTP/DB; việc cancel future không xóa giao dịch đã commit.

Đừng tạo một bản ghi “CANCELLED” ở DB và tin rằng mọi app instance đã dừng lịch chỉ vì một instance có handle. Bài toán nhiều node cần ownership/leader election/lease hoặc scheduler phân tán phù hợp.

## 9. Ví dụ Java 17 + Spring Framework chạy được: annotation và API cạnh nhau

Ví dụ dưới đây dùng **Spring Framework 6.2.9**. Một `@Scheduled` method tự kích hoạt sau initial delay. Đồng thời `main` dùng chính bean `TaskScheduler` để tạo một lịch chạy một lần và một lịch recurring sẽ bị hủy trước lần đầu.

Chương trình dùng latch để không phụ thuộc vào thứ tự thực thi của worker. Ba dòng in chỉ do `main` xuất ra nên thứ tự xác định; không cần internet, database hay Spring Boot.

### Cấu trúc

~~~text
spring-scheduling-choice/
├── pom.xml
└── src/main/java/demo/SchedulingChoiceDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-scheduling-choice</artifactId>
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

### src/main/java/demo/SchedulingChoiceDemo.java

~~~java
package demo;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SchedulingChoiceDemo {
    private static final CountDownLatch ANNOTATED = new CountDownLatch(1);

    public static class Heartbeat {
        @Scheduled(initialDelay = 50, fixedDelay = 200)
        public void pulse() {
            ANNOTATED.countDown();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class SchedulingConfig {
        @Bean
        public Heartbeat heartbeat() {
            return new Heartbeat();
        }

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler =
                    new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("schedule-");
            return scheduler;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(
                     SchedulingConfig.class)) {
            TaskScheduler scheduler = context.getBean(TaskScheduler.class);
            boolean annotationExecuted = ANNOTATED.await(3, TimeUnit.SECONDS);

            CountDownLatch DIRECT = new CountDownLatch(1);
            ScheduledFuture<?> once = scheduler.schedule(
                DIRECT::countDown,
                Instant.now().plusMillis(100));
            boolean directExecuted =
                once != null && DIRECT.await(3, TimeUnit.SECONDS);

            ScheduledFuture<?> repeating = scheduler.scheduleAtFixedRate(
                () -> { },
                Instant.now().plusSeconds(10),
                Duration.ofSeconds(1));
            boolean recurringCancelled = repeating.cancel(false);

            System.out.println("Annotation executed: " + annotationExecuted);
            System.out.println("Direct schedule executed: " + directExecuted);
            System.out.println("Recurring cancelled: " + recurringCancelled);
        }
    }
}
~~~

Chạy từ thư mục project (JDK 17+, Maven):

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.SchedulingChoiceDemo
~~~

**Stdout mong đợi trên máy đủ tài nguyên để xử lý callback trong hạn 3 giây**:

~~~text
Annotation executed: true
Direct schedule executed: true
Recurring cancelled: true
~~~

Mã đã được **biên dịch với target Java 17 và thực thi** cùng Spring Framework 6.2.9 trên JDK mới hơn; ba dòng stdout trên đã được kiểm tra chính xác.

Giải thích chi tiết:

1. `@EnableScheduling` đăng ký xử lý annotation trong context và `Heartbeat` là bean `@Bean`, nên method `pulse()` được đăng ký dù caller không gọi nó thủ công.
2. Bean `ThreadPoolTaskScheduler` tên `taskScheduler` có pool size 2; `@Scheduled` và API trực tiếp đều có thể dùng cùng scheduler này.
3. `ANNOTATED.await(...)` chỉ là điểm đồng bộ cho demo. Application production không cần main thread chờ từng tick.
4. Lệnh `schedule(..., Instant.now().plusMillis(100))` lên lịch một tác vụ một lần; latch xác nhận callback đã chạy.
5. Lệnh `scheduleAtFixedRate(..., Instant.now().plusSeconds(10), ...)` tạo lịch có lần đầu ở tương lai; `cancel(false)` được gọi ngay nên xác nhận có thể hủy lịch trước khi nó chạy.
6. `try`-with-resources đóng `ApplicationContext`; Spring quản lý lifecycle và shutdown scheduler bean. Không để thread pool demo sống mãi sau khi chương trình kết thúc.

**Giới hạn test:** Không nên lấy ba giá trị `true` làm cam kết mọi máy/VM đều lên lịch chính xác từng mili giây. Khi hệ thống bị treo hoặc scheduler shutdown, một latch có thể hết hạn. Điều quan trọng của demo là wiring và cơ chế registration/cancellation.

## 10. Dynamic job registry: khi mỗi job có ID và quyền dừng

Với API nghiệp vụ “bắt đầu polling đơn hàng `order-42` mỗi 10 giây, thay chu kỳ, dừng polling”, một service có thể lưu `ScheduledFuture` theo business key. Đây là **mã tích hợp Spring**, không phải app độc lập; nó giả định đã có `TaskScheduler` bean.

~~~java
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class DynamicJobRegistry implements DisposableBean {
    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> tasks = new HashMap<>();

    public DynamicJobRegistry(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public synchronized void startOrReplace(
            String jobId, Runnable work, Duration period) {
        if (jobId == null || jobId.isBlank() ||
            work == null || period == null || period.isZero() ||
            period.isNegative()) {
            throw new IllegalArgumentException("Invalid job settings");
        }

        stop(jobId);
        ScheduledFuture<?> next =
            scheduler.scheduleAtFixedRate(work, period);
        tasks.put(jobId, next);
    }

    public synchronized boolean stop(String jobId) {
        ScheduledFuture<?> previous = tasks.remove(jobId);
        return previous != null && previous.cancel(false);
    }

    @Override
    public synchronized void destroy() {
        for (ScheduledFuture<?> future : tasks.values()) {
            future.cancel(false);
        }
        tasks.clear();
    }
}
~~~

**Điều gì được cải thiện so với annotation?**

- Business ID của từng lịch xuất hiện rõ trong map, không cần một method `@Scheduled` khác cho mỗi ID.
- API/controller có thể gọi `startOrReplace` khi thông số thay đổi và `stop` khi người dùng tắt job.
- `synchronized` bảo vệ các thao tác cập nhật map trong **một JVM**, tránh hai request đồng thời thay đổi cùng `jobId` trong cùng instance.
- `DisposableBean.destroy()` yêu cầu hủy handle khi container shutdown; các callback đang chạy vẫn cần dừng/cleanup theo hợp đồng riêng.

**Giới hạn quan trọng:** Đây là **registry cục bộ trong bộ nhớ**, không phải production multi-node scheduler. Nó không lưu thời điểm và trạng thái job trong DB, không khôi phục sau process restart, không ngăn hai replica có hai lịch cho cùng job, và `cancel(false)` không đảo ngược một lượt đang chạy. Nếu có yêu cầu khắt khe về delivery hoặc cluster, cần một job store/lease/leader selection phù hợp.

Ngoài ra, thao tác “hủy lịch cũ rồi đăng ký lịch mới” không atomic với callback đã chạy. Một bản triển khai chặt chẽ có thể cần generation/version token: callback kiểm tra phiên bản job đang sở hữu trước khi tạo side effect.

## 11. Trigger khi thời điểm tiếp theo không phải chu kỳ cố định

Đôi khi thời điểm tiếp theo phụ thuộc vào lịch làm việc, kết quả lần chạy trước hoặc quy tắc runtime. `TaskScheduler.schedule(Runnable, Trigger)` cho phép đăng ký với một `Trigger`.

Ví dụ dùng `CronTrigger`:

~~~java
TaskScheduler scheduler = /* bean được Spring inject */;

ScheduledFuture<?> handle = scheduler.schedule(
    this::runReconciliation,
    new CronTrigger("0 */10 * * * *", ZoneId.of("UTC"))
);
~~~

Đây là **snippet tích hợp**, không phải class độc lập; cần import `CronTrigger`, `ZoneId`, `ScheduledFuture`, và method `runReconciliation`. Spring 6.2 hỗ trợ khởi tạo `CronTrigger` với timezone/zone. `schedule` có thể trả `null` nếu trigger không có thời điểm thực thi tiếp theo; code giữ handle phải kiểm tra điều này.

Với `Trigger` do ứng dụng định nghĩa, Spring truyền `TriggerContext` (các thời điểm được lên lịch, thực tế và hoàn tất trước đó) để tính lần kế tiếp. Có thể dùng để né giờ bảo trì hoặc áp delay phụ thuộc kết quả. Tuy nhiên nếu cần thay lịch ngay trong khi trigger đã đăng ký, việc thay đổi một object mutable bên ngoài dễ gây race; **hủy handle cũ và đăng ký handle mới** thường dễ kiểm soát hơn.

Đừng đánh đồng việc tính `nextExecution()` với một hệ thống lưu thời gian vào database: một `Trigger` trong memory không mang lại recovery sau crash.

## 12. Thread pool, độ trễ và tác vụ chặn

Cả hai cách lập lịch đều có thể gặp quá tải khi scheduler thread chạy công việc quá lâu.

Ví dụ một `ThreadPoolTaskScheduler` cấu hình `poolSize=1` và một task truy vấn DB mất 30 giây: các lịch khác dùng cùng scheduler có thể bị chậm dù chúng chỉ chạy vài mili giây. Dùng API trực tiếp thay vì annotation **không tự tạo thêm worker**.

Gợi ý thiết kế:

1. **Task rất ngắn:** chạy trực tiếp trên scheduler thread có thể đơn giản và đủ dùng.
2. **Task I/O/blocking dài:** dùng scheduler để phát tín hiệu đến một `TaskExecutor` riêng có pool/queue hữu hạn, hoặc dùng một job worker có giới hạn. Khi executor bão hòa, xử lý rejection/backpressure thay vì nộp mãi.
3. **Task có side effect lớn:** ngoài số thread, cần timeout, idempotency, retry policy và trạng thái job.
4. **Nhiều lịch cho nhiều tenant:** đếm tổng callback đến hạn, không chỉ số lịch được đăng ký. Một thời điểm cron chung có thể tạo thundering herd.
5. **SLA ngắn:** đo `scheduled_at`, `actual_started_at`, `finished_at` và tính scheduling lag; fixedRate/cron không cam kết exact real-time.
6. **Lịch lặp quá nhanh:** cân nhắc tối thiểu period, rate limiting và tránh polling khi hệ thống chưa phục hồi.

Trong Spring, `TaskScheduler` và `TaskExecutor` là hai abstraction khác nhau. Có thể cấu hình hai bean riêng có tên rõ ràng, tránh dựa vào auto-configuration không được kiểm chứng.

## 13. fixedRate, reentrancy và nhiều lịch chồng lấn

Một sai lầm thường gặp là nghĩ “tôi chỉ có một method `@Scheduled`, nên không thể có hai lượt xử lý cùng một nghiệp vụ”.

Các nguồn overlap bao gồm:

- Cùng bean có nhiều `@Scheduled` annotation áp lên cùng method (repeatable schedules) với trigger khác nhau.
- Code gọi trực tiếp method nghiệp vụ song song với lượt do scheduler kích hoạt.
- Có hai hoặc nhiều **replica** ứng dụng, mỗi replica đăng ký cùng một annotation.
- Task `@Scheduled` nhanh chóng submit một `@Async` job khác rồi return, khiến các lần async xử lý có thể overlap.
- Runtime registry tạo hai handle cho cùng business key do race hoặc recovery chưa đồng bộ.

Các giải pháp tùy bối cảnh: lock cục bộ khi chỉ có một JVM, version/lease hoặc khóa phân tán nếu nhiều instance, idempotency key khi có side effect và tránh dùng mutable state không thread-safe.

Không dùng `synchronized` ở một Spring bean rồi tưởng rằng nó bảo đảm **mỗi cluster chỉ có một job chạy**. Monitor lock lease, duplicate execution và partial failure, nhất là khi công việc tạo invoice, thu tiền hoặc gửi notification.

## 14. Timezone, cron và mốc thời gian

`@Scheduled(cron = "0 0 2 * * *", zone = "UTC")` thể hiện lịch theo **thời gian đồng hồ của một timezone**. Khi zone có daylight saving time, có ngày một giờ địa phương bị bỏ qua hoặc lặp lại; cần xem chính sách của business nếu job chạy vào 02:00.

Lưu ý:

- Cron mang ý nghĩa **calendar schedule**; fixed delay/rate dựa trên khoảng thời gian giữa các lần thực thi.
- Đối với bản ghi “hết hạn sau 15 phút”, thường nên lưu một **`Instant` deadline** thay vì một cron của server. Khi reload từ DB, tính lại thời điểm.
- Nên dùng `ZoneId`/timezone được chỉ rõ thay vì ngầm dựa vào default timezone của container.
- Nếu công việc yêu cầu chính xác một lần mỗi ngày theo giờ địa phương, phải có **business date key** và dedupe phù hợp, không chỉ tin scheduler.
- Clock shift/NTP, pause, GC stop-the-world, autoscaling và process restart có thể làm một lịch bị trễ; nếu phải catch-up, ứng dụng cần quy tắc riêng.
- Một cron schedule trong memory không có lịch sử phát lại những thời điểm đã bỏ lỡ sau restart.

## 15. Error handling, shutdown và cancellation

Một scheduled callback ném exception cần được ghi nhận và phân loại. Nếu chỉ tin rằng scheduler sẽ tiếp tục theo bất kỳ policy nào, ứng dụng có thể âm thầm bỏ công việc.

Với Spring `ThreadPoolTaskScheduler`, có thể cấu hình `ErrorHandler` phù hợp và xử lý lỗi tại ranh giới công việc. Hành vi tiếp tục hay dừng các future periodic sau lỗi có thể phụ thuộc wrapper, implementation và cách scheduler xử lý exception; hãy **test thực tế** thay vì suy luận từ exception của một method.

Một số nguyên tắc:

- Callback phải ghi log có correlation/job ID nhưng không dump thông tin nhạy cảm; metric có label cardinality thấp.
- Không auto-retry vô hạn hoặc retry nóng ngay trong scheduler thread khi downstream quá tải.
- Nếu cần retry, chọn backoff/jitter, max attempts, deadline và idempotency, có thể đưa retry vào job store.
- Khi shutdown, cần xác định task đang chạy sẽ được kết thúc, chờ bao lâu và có được interrupt hay không. `ThreadPoolTaskScheduler` có các tùy chọn lifecycle; không giả định default phù hợp mọi workload.
- `ScheduledFuture.cancel(false)` ngăn những lần chạy tiếp theo khi được chấp nhận; nó **không** bảo đảm giao dịch/HTTP call đang chạy bị dừng.
- Với lịch trực tiếp, **sở hữu handle** và vòng đời registry là trách nhiệm của service đăng ký. Nếu context đóng, các handle trong map không nên được coi là job persistent.

Từ góc độ API, status “job stopped” có thể nghĩa là **không đăng ký lần chạy kế tiếp**, khác với “không còn work đang chạy”. Hai trạng thái cần đặt tên riêng khi nghiệp vụ quan trọng.

## 16. Độ tin cậy: cả annotation lẫn direct đều không phải durable job store

Trong ứng dụng Spring chạy trên một JVM, `@Scheduled` và `TaskScheduler` dựa trên runtime process. Khi process crash hoặc được thay thế trong deployment, các lịch trong memory không tự được khôi phục như một hàng đợi durable.

Hỏi trước khi chọn:

- **Có được phép bỏ lỡ một lượt?** Với metrics refresh, có thể chấp nhận. Với hóa đơn đến hạn, có thể không.
- **Job có cần chạy đúng một lần toàn cluster?** `@Scheduled` chạy ở mọi replica trừ khi thiết kế cơ chế đồng bộ. Direct schedule trong nhiều replica cũng vậy.
- **Có cần lưu job ID và next fire time?** Nếu có, cần DB/job store để quản lý state và recovery.
- **Có cần pause/resume theo tenant, người dùng hay feature?** Direct registration hữu ích trong JVM; đa node phải có cơ chế ownership.
- **Có cần phục hồi công việc đang chạy khi worker chết?** Khi đó là bài toán durable queue, lease/retry, checkpoint và idempotency, không chỉ về cú pháp scheduling.

Các lựa chọn có thể gồm Quartz với JDBC JobStore, một broker/job queue và worker có lịch, hoặc hệ thống workflow orchestration tùy yêu cầu. Việc dùng Quartz không tự bảo đảm business effect exactly-once nếu worker crash sau side effect nhưng trước khi đánh dấu job hoàn tất.

**Tách hai lớp:** lớp “khi nào job đến hạn” và lớp “job được thực hiện đáng tin cậy ra sao”. `TaskScheduler` giải quyết lớp đầu trong tiến trình hiện tại; durability, dedupe, distributed coordination thuộc lớp thiết kế hệ thống.

## 17. Spring Boot: kiểm tra auto-configuration, đừng suy đoán

Spring Boot có hỗ trợ task scheduling auto-configuration và thuộc tính `spring.task.scheduling.*`. Tuy nhiên hành vi cụ thể phụ thuộc version Boot, các bean đã tự khai báo và lựa chọn virtual threads.

Trong một project Spring Framework thuần, ví dụ mục 9 **chủ động khai báo** `ThreadPoolTaskScheduler` để không phụ thuộc việc một framework khác có tạo scheduler hay không.

Khi làm việc với Boot:

1. Xác nhận `@EnableScheduling` thực sự được bật.
2. Kiểm tra bean `TaskScheduler` mà `@Scheduled` đang dùng; ghi rõ tên và `poolSize`.
3. Kiểm tra bean `TaskExecutor` cho `@Async` là một thành phần **khác**; đừng nghĩ tăng `spring.task.execution.pool.max-size` luôn làm scheduled callback chạy song song hơn.
4. Khi ứng dụng định nghĩa custom scheduler, thuộc tính auto-config có thể không tác động như mong đợi.
5. Nếu nhiều scheduler tồn tại, chỉ định lựa chọn với tên/qualifier hoặc cấu hình scheduling rõ ràng; không để việc chọn ngẫu nhiên theo bean order.
6. Với virtual threads, đọc tài liệu Spring Boot của phiên bản đang dùng, đặc biệt sự khác nhau giữa scheduler thread pool và `SimpleAsyncTaskScheduler`.

Một cấu hình chủ động đủ cho nhiều project:

~~~java
@Configuration
@EnableScheduling
public class ProductionSchedulerConfig {
    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
            new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("jobs-");
        return scheduler;
    }
}
~~~

Đây là **cấu hình tích hợp**; cần imports tương ứng và benchmark theo workload, không coi `poolSize=4` là một giá trị tối ưu chung.

## 18. Quan sát và kiểm thử sự khác biệt

Một bộ test tốt phải kiểm tra **quyền điều khiển lịch**, không chỉ kiểm tra method có chạy:

1. **Wiring annotation:** tạo context có `@EnableScheduling`, bean `@Scheduled`, latch và scheduler đã xác định. Assert method kích hoạt trong thời hạn hợp lý.
2. **Direct one-shot:** gọi `schedule(Runnable, Instant)` và xác nhận callback chạy một lần tại thời điểm không sớm hơn hợp đồng cho phép.
3. **Cancel trước lần chạy đầu:** lên lịch xa trong tương lai rồi gọi `cancel(false)`; xác nhận task không chạy trong khoảng kiểm thử.
4. **Cancel khi đang chạy:** dùng `CountDownLatch` giữ callback, gọi cancel và kiểm tra **khác biệt** giữa ngăn lượt sau và interrupt.
5. **Dynamic reconfiguration:** start ID A với chu kỳ P, thay bằng chu kỳ Q, bảo đảm không còn hai handle active cho cùng ID trong một JVM.
6. **Lỗi callback:** chủ động ném exception, đo log/metric và kiểm tra policy tiếp tục của scheduled job trên implementation đang chạy.
7. **Scheduler saturation:** giữ scheduler thread bằng latch, đo độ trễ của callback khác để chứng minh pool contention.
8. **Multiple replicas:** triển khai hai application instances, kiểm tra event có thể được thực hiện hai lần; xác nhận lease/lock hoặc dedupe hoạt động.
9. **Restart:** tạo job rồi restart JVM; kiểm tra contract khôi phục thực tế từ persistent store nếu có.
10. **Cron timezone:** test lịch qua mốc giờ mùa hè/giờ địa phương nếu nghiệp vụ yêu cầu.
11. **Integration với downstream:** kiểm tra database timeout, retry và hiệu ứng trùng lặp thay vì chỉ assert scheduler callback được gọi.
12. **Context shutdown:** đóng context và xác nhận scheduler thread được release, không còn job mới từ instance đã dừng.

Metric gợi ý: `scheduled_jobs_active`, `schedule_trigger_total`, `schedule_lag_ms`, `job_duration_ms`, `job_failure_total`, `job_cancel_total`, `scheduler_queue_depth` (nếu implementation expose), executor saturation và backlog persistent nếu có. Không gắn raw job/user ID có cardinality cao lên metric label; đưa ID vào trace/log có kiểm soát.

Với unit test, có thể mock `TaskScheduler` để kiểm tra `schedule`/`cancel` đúng hợp đồng; với test thời gian thực, dùng latch và tolerance phù hợp. Tránh viết test dễ flake chỉ vì `Thread.sleep(50)`.

## 19. Tình huống áp dụng

### A. Xóa cache mỗi đêm

**Yêu cầu:** một tác vụ kiểm tra cache hết hạn lúc 02:00 UTC mỗi ngày; không có business ID riêng, không cần từng khách hàng bật/tắt.

**Chọn:** `@Scheduled(cron = "0 0 2 * * *", zone = "UTC")`. Đưa cron vào property; theo dõi run duration và failure. Nếu triển khai nhiều replica, cân nhắc thực hiện idempotent hoặc có leader/lock nếu không muốn mỗi instance tự xóa.

### B. Hẹn giờ hết hạn phiên làm việc theo từng session

**Yêu cầu:** mỗi session có một `expiresAt` khác nhau, khi gia hạn thì dời thời điểm, khi logout thì hủy lịch.

**Chọn:** `TaskScheduler.schedule(Runnable, Instant)` với registry cho session/job ID, nếu scale nhỏ trong một process và chấp nhận mất lịch khi restart. Với sản phẩm cần chính xác qua restart và nhiều node, lưu `expiresAt` trong DB/TTL store, có scanner/queue bền vững và kiểm tra expiry lại khi xử lý; direct timer chỉ là tối ưu latency, không phải nguồn chân lý.

### C. Poll API bên ngoài sau khoảng nghỉ do server chỉ dẫn

**Yêu cầu:** sau khi gọi API, server trả `Retry-After`, và lượt tiếp theo phụ thuộc trạng thái runtime.

**Chọn:** direct scheduling hoặc trigger/async retry component có giới hạn. Không cần đặt một method `@Scheduled` cố định 100ms rồi liên tục kiểm tra xem đến giờ retry chưa. Khi downstream quá tải, tôn trọng quota, backoff/jitter, deadline và cancellation.

### D. Tổng hợp báo cáo theo thời khóa biểu đơn vị

**Yêu cầu:** chạy mỗi thứ Hai theo timezone của từng chi nhánh, có yêu cầu không bỏ sót và không thực thi trùng.

**Chọn:** business schedule được lưu bền và một scheduler/worker phân tán có policy về calendar, catch-up và deduplication. Có thể dùng `@Scheduled` rất nhẹ để đánh thức poller đọc danh sách job đến hạn, nhưng phần chính của bài toán là **job store và coordination**, không phải annotation hay API nào đẹp hơn.

## 20. Những hiểu nhầm và phản ví dụ

| Hiểu nhầm | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| “`@Scheduled` và `TaskScheduler` là hai engine độc lập.” | Annotation thường đăng ký lên scheduler tương thích | Nhìn vào scheduler bean thực tế |
| “Chỉ cần `@Scheduled` là có job ID để hủy.” | Annotation không trả `ScheduledFuture` cho business caller | Dùng direct scheduling nếu cần handle |
| “Sửa config cron thì job lập tức đổi mốc.” | Context có thể đã đăng ký lịch từ lúc startup | Implement dynamic re-registration có kiểm soát |
| “`TaskScheduler` cho phép job sống qua restart.” | Local scheduler giữ handle trong memory | Lưu job state vào DB/job store |
| “fixedRate luôn chạy đồng thời nhiều lượt cùng task.” | JDK periodic schedule không overlap cùng periodic task | Xem scheduler implementation và các nguồn concurrency khác |
| “fixedDelay chỉ là fixedRate viết khác cú pháp.” | Delay tính từ **completion**, rate theo chu kỳ nominal | Chọn đúng semantics dựa trên workload |
| “TaskScheduler tự cancel HTTP/SQL khi `Future.cancel`.” | Cancel không đảm bảo remote operation dừng | Đặt I/O timeout và idempotency |
| “`poolSize=1` đủ cho mọi @Scheduled job.” | Một callback blocking giữ scheduler thread lâu | Đo tải, tách worker pool hoặc tăng capacity hợp lý |
| “Một `synchronized` method đảm bảo một lượt trên toàn cluster.” | Lock chỉ trong một JVM | Lease/distributed lock/deduplication |
| “Cron luôn chạy bù mọi lần đã lỡ sau shutdown.” | Lịch memory thường không có replay history | Persist schedule/catch-up policy |
| “`@Scheduled` có thể nhận trực tiếp mọi tham số HTTP request.” | Method định kỳ được framework gọi, không gắn request input | Dùng queue/job record và direct control |
| “Thay annotation bằng direct là tự có backpressure.” | Chỉ khác registration/control, không khác resource limits | Bounded executor, rate limiting và metrics |

## 21. Checklist code review

- Tôi xác định lịch là **cấu hình cố định** hay **thực thể runtime** có ID và vòng đời riêng.
- Với lịch ổn định, tôi ưu tiên `@Scheduled` và `@EnableScheduling` đúng context.
- Với lịch động, tôi có registry/handle rõ ràng khi dùng `TaskScheduler`, biết khi nào cancel/reschedule.
- Tôi chọn `fixedRate`, `fixedDelay`, `cron` theo đúng mốc thời gian và hiểu timezone.
- Tôi không tin property đổi trong runtime sẽ tự động cập nhật mọi `@Scheduled` job.
- Tôi hiểu `ScheduledFuture.cancel(false)` không hủy tác vụ đã chạy hoặc rollback side effect.
- Scheduler thread không chạy blocking I/O vô hạn; tôi có timeout và pool/queue hữu hạn nếu cần.
- Tôi có chiến lược xử lý lỗi, retry/backoff, shutdown và lịch trễ.
- Tôi biết lịch có sống qua JVM restart hay không, và ai sở hữu job state bền vững.
- Tôi kiểm soát duplicate execution trong nhiều replica bằng lease/lock/dedupe/idempotency.
- Tôi test annotation registration, direct schedule, cancel, overload, nhiều node và recovery phù hợp.
- Tôi có metric job lag, duration, failure, cancellation, pending/retry và log correlation an toàn.

**Kết luận:** `@Scheduled` phù hợp nhất cho những công việc định kỳ **khai báo một lần theo cấu hình**, đơn giản và dễ vận hành. `TaskScheduler` phù hợp khi cần **ra quyết định tại runtime, nhận handle, hủy hoặc thay đổi lịch theo business ID**. Hai lựa chọn không khác nhau ở bảo đảm durability, tránh thực thi trùng hay chống quá tải: những yêu cầu đó cần scheduler/worker architecture, timeout, idempotency và persistence phù hợp.

## Tài liệu tham khảo chính thức

- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@EnableScheduling`, `@Scheduled`, `TaskScheduler`, `Trigger` và cách đăng ký công việc.
- [Spring Framework – TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/TaskScheduler.html): `schedule`, `scheduleAtFixedRate`, `scheduleWithFixedDelay` và `ScheduledFuture`.
- [Spring Framework – @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): `fixedDelay`, `fixedRate`, cron, zone và semantics đăng ký.
- [Spring Framework – CronTrigger Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/support/CronTrigger.html): trigger theo cron và zone.
- [Spring Framework – ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): scheduler pool và vòng đời.
- [Java SE 17 – ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): cơ chế periodic scheduling và quy tắc không overlap của cùng periodic task.
- [Spring Boot – Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html): auto-configuration và configuration property liên quan.


</details>

- [Quay lại đầu trang](#back-to-top)
