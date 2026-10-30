<a id="back-to-top"></a>

# Chọn scheduler

## Menu
- [ThreadPoolTaskScheduler dựa trên ScheduledExecutorService](#scheduler-selection-001)
- [poolSize mặc định và ảnh hưởng của công việc chạy lâu](#scheduler-selection-002)
- [Công việc thông thường chạy trên luồng của ThreadPoolTaskScheduler](#scheduler-selection-003)
- [ConcurrentTaskScheduler thích nghi bộ lập lịch JDK](#scheduler-selection-004)
- [DefaultManagedTaskScheduler trong môi trường Jakarta EE](#scheduler-selection-005)
- [SimpleAsyncTaskScheduler tách cơ chế kích hoạt với cách chạy tác vụ](#scheduler-selection-006)
- [Giới hạn fixed-delay chạy trên luồng scheduler của SimpleAsyncTaskScheduler](#scheduler-selection-007)
- [Tách bộ lập lịch khỏi nhóm luồng xử lý khi cần kiểm soát hai loại tải](#scheduler-selection-008)
- [Chọn cách theo dõi hoàn tất theo hợp đồng của từng scheduler](#scheduler-selection-009)

## <a id="scheduler-selection-001">ThreadPoolTaskScheduler dựa trên ScheduledExecutorService</a>

<details>
<summary>Xem chi tiết</summary>

# ThreadPoolTaskScheduler dựa trên ScheduledExecutorService

## 1. Tổng quan và mục tiêu

Spring Framework cung cấp `ThreadPoolTaskScheduler` để chạy các công việc **vào một thời điểm trong tương lai** hoặc **lặp lại theo chu kỳ/trigger**. Thay vì tự tạo và quản lý `ScheduledExecutorService` ở nhiều nơi, ứng dụng Spring có thể khai báo một bean scheduler, sử dụng API `TaskScheduler` hoặc kết hợp với `@EnableScheduling` và `@Scheduled`.

Điểm kiến trúc cốt lõi:

> **`ThreadPoolTaskScheduler` là implementation Spring của `TaskScheduler`, bao bọc một `java.util.concurrent.ScheduledThreadPoolExecutor` (một `ScheduledExecutorService` của JDK). Task thực thi ngay trên các scheduler thread do pool này quản lý.**

Vì vậy nó **không phải một bộ định giờ chạy ở một thread rồi mặc định đẩy toàn bộ tác vụ sang một worker pool riêng**. Nếu công việc được hẹn chạy mất 30 giây và pool chỉ có một thread, những công việc khác của **cùng scheduler** có thể phải đợi.

### Bạn sẽ học được gì?

- Phân biệt vai trò `ScheduledExecutorService`, `ScheduledThreadPoolExecutor`, `TaskScheduler` và `ThreadPoolTaskScheduler`.
- Dùng `schedule(Runnable, Instant)`, `scheduleAtFixedRate(...)`, `scheduleWithFixedDelay(...)` và `schedule(Runnable, Trigger)`.
- Hiểu **fixed rate theo nhịp bắt đầu**, **fixed delay theo lúc kết thúc**, trigger cron theo lịch và ảnh hưởng của task kéo dài.
- Biết **`setPoolSize(1)` là mặc định**, task chạy trên scheduler worker, và vì sao số worker không phải số job bền vững.
- Cấu hình một bean `ThreadPoolTaskScheduler` trong Spring Framework 6.1.3 và chạy chương trình hoàn chỉnh với **Java 17+**.
- Phân biệt `ThreadPoolTaskScheduler` với `ThreadPoolTaskExecutor`, cả về scheduling, queue và lifecycle.
- Hiểu `ScheduledFuture`, hủy task, rejection, error handling của Spring, shutdown, pause/resume và những giới hạn khi triển khai nhiều instance.
- Thiết kế monitoring, timeout, idempotency và lịch có time zone rõ ràng.

**Phiên bản:** Các ví dụ biên dịch theo **Java 17+**, sử dụng **Spring Framework 6.1.3** cho project Maven. Cơ chế `ScheduledExecutorService` đã có từ Java 5; `Duration`/`Instant` được dùng trong Spring `TaskScheduler` của Spring 6.x. Một số tính năng Spring 6.1 (pause/resume, tích hợp virtual-thread-aligned scheduler khác) được phân biệt rõ với bản JDK hoặc Spring cũ.

## 2. Kiến trúc: Spring TaskScheduler bao ngoài JDK ScheduledExecutorService

### 2.1. Lớp API của JDK

`ScheduledExecutorService` là một interface kế thừa `ExecutorService`. Ngoài việc `execute`/`submit` ngay lập tức, nó có các thao tác hẹn:

~~~java
// Chữ ký minh họa của JDK, không phải class độc lập.
ScheduledFuture<?> schedule(
        Runnable task, long delay, TimeUnit unit);

<V> ScheduledFuture<V> schedule(
        Callable<V> task, long delay, TimeUnit unit);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, long initialDelay, long period, TimeUnit unit);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, long initialDelay, long delay, TimeUnit unit);
~~~

Implementation phổ biến trong Java 17 là `ScheduledThreadPoolExecutor`. Nó sử dụng hàng đợi công việc có delay, một số worker threads và trả `ScheduledFuture` để theo dõi hoặc hủy lịch.

~~~text
Caller
  |
  | schedule(task, delay) / scheduleAtFixedRate(...) / ...
  v
ScheduledExecutorService (JDK interface)
  |
  v
ScheduledThreadPoolExecutor (JDK implementation)
  |                  |
  |-- delayed queue |-- worker-1
  |                  |-- worker-2
  |
  +-- ScheduledFuture<?>  (theo dõi / hủy)
~~~

Điểm quan trọng: task chỉ được phép bắt đầu khi đến hạn, **không được đảm bảo chính xác thời điểm đó**. Hệ điều hành, hàng đợi, thời gian task khác đang chạy và số scheduler threads có thể gây trễ. Đây là công cụ scheduling trong JVM, không phải bộ định thời gian thực (hard real-time).

### 2.2. Lớp API của Spring

`org.springframework.scheduling.TaskScheduler` dùng API thân thiện với Spring và kiểu thời gian của `java.time`:

~~~java
// Chữ ký tiêu biểu trong Spring Framework 6.1.x.
// Fragment interface minh họa, không phải file biên dịch độc lập.
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
~~~

`ThreadPoolTaskScheduler` triển khai interface này, đồng thời bao bọc `ScheduledThreadPoolExecutor` của JDK. Với implementation mặc định, có thể lấy đối tượng gốc qua:

~~~java
// Fragment sau khi bean ThreadPoolTaskScheduler đã khởi tạo.
ScheduledExecutorService nativeScheduler =
        scheduler.getScheduledExecutor();

ScheduledThreadPoolExecutor nativePool =
        scheduler.getScheduledThreadPoolExecutor();
~~~

Hai accessor trên hữu ích khi cần thống kê, cấu hình native đặc biệt hoặc điều tra hành vi. Nhưng **code nghiệp vụ nên phụ thuộc `TaskScheduler`** khi không cần thuộc tính implementation để dễ đổi backend.

### 2.3. Task có chạy trên thread khác do Spring giao tiếp không?

Một điểm Spring Javadoc nhấn mạnh: **task của `ThreadPoolTaskScheduler` thực thi trên chính scheduler thread(s)**, không mặc định đi tiếp tới một execution pool khác. Khi một task đang chặn I/O:

- Worker scheduler đó bị chiếm trong suốt khoảng thời gian task chờ.
- Pool có một worker có thể làm các lịch khác chậm, dù chúng đến hạn.
- Nếu tăng `setPoolSize(4)`, các task **khác nhau** có thể dùng những worker khác; nhưng từng lần lặp của **cùng một periodic JDK task** không tự chồng lấn chính nó.
- Nếu task thật được nộp sang executor khác hoặc method `@Scheduled` tự gọi service `@Async`, thời điểm hoàn thành thao tác thật có thể **khác thời điểm kết thúc lời gọi scheduled callback**. Đó là kiến trúc do ứng dụng chọn thêm, không phải mặc định của scheduler.

## 3. Một lần, fixed rate, fixed delay và trigger

### 3.1. Lệnh `schedule(Runnable, Instant)` chỉ chạy một lần

~~~java
// Fragment khi đã có TaskScheduler scheduler.
ScheduledFuture<?> future = scheduler.schedule(
        () -> sendReminder(),
        Instant.now().plusSeconds(10)
);
~~~

Lời gọi trả `ScheduledFuture<?>` để caller có thể `cancel(...)` hoặc `get()`. Với `Runnable` hoàn tất bình thường, `get()` trả `null`. Nếu thời điểm yêu cầu là quá khứ, scheduler có thể chạy task sớm nhất có thể; không dùng giá trị `Instant` quá khứ làm bảo đảm task chạy “quay ngược thời gian”.

Đây là fragment: `sendReminder()` và `scheduler` cần được định nghĩa trong ứng dụng. Với công việc có side effect như gửi email, cần idempotency và một nơi lưu trạng thái, không giả định việc schedule trong bộ nhớ là giao hàng chính xác một lần.

### 3.2. Fixed rate: nhịp tính từ lúc bắt đầu dự kiến

~~~java
// Fragment Spring 6.x.
ScheduledFuture<?> pulse = scheduler.scheduleAtFixedRate(
        () -> refreshCounters(),
        Duration.ofSeconds(5)
);
~~~

Mục tiêu là lặp theo nhịp khoảng 5 giây giữa **các mốc bắt đầu dự kiến**. Nếu lần chạy mất lâu hơn period, lần sau có thể **bắt đầu trễ**; đối với `ScheduledThreadPoolExecutor.scheduleAtFixedRate`, các lần thực thi của **cùng periodic task không chạy song song chồng nhau**, dù có nhiều worker.

~~~text
Ví dụ fixedRate period = 5s, mỗi lần task mất 8s:

mốc kỳ vọng:  0s    5s    10s    15s
chạy thực:    [0------------8][8------------16][16---------...]
                    bị trễ, không tạo bản sao song song
~~~

Đồ thị minh họa **độ trễ tích lũy**, không phải cam kết scheduler sẽ bắt đầu chính xác từng mốc. Những task khác trên cùng scheduler vẫn có thể chạy đồng thời nếu pool còn worker.

### 3.3. Fixed delay: đợi thêm sau khi lần trước kết thúc

~~~java
// Fragment Spring 6.x.
ScheduledFuture<?> cleanup = scheduler.scheduleWithFixedDelay(
        () -> deleteTemporaryFiles(),
        Duration.ofSeconds(5)
);
~~~

Delay 5 giây được đo từ **lúc hoàn tất** một lần chạy tới lúc bắt đầu lần kế tiếp, không tính từ lịch bắt đầu lý tưởng.

~~~text
Ví dụ fixedDelay = 5s, mỗi lần task mất 8s:

lần 1:       [0-------------8]
chờ delay:                    [8---13]
lần 2:                              [13-------------21]
chờ tiếp:                                            [21---26]
~~~

Với việc cần “hoàn tất, nghỉ một khoảng rồi tiếp tục”, `fixedDelay` hợp lý hơn `fixedRate`. Nó có độ dài chu kỳ bằng **duration thực của task + delay**, không phải chỉ delay.

### 3.4. Cron và Trigger: thời điểm theo lịch

Spring có `Trigger` để xác định thời điểm tiếp theo và `CronTrigger` để biểu diễn lịch dạng cron:

~~~java
// Fragment Spring Framework 6.1.3:
ScheduledFuture<?> billing = scheduler.schedule(
        () -> reconcileBilling(),
        new CronTrigger("0 0 2 * * *", ZoneId.of("Asia/Ho_Chi_Minh"))
);
~~~

Cron ở đây theo cú pháp Spring **6 trường** (giây, phút, giờ, ngày trong tháng, tháng, ngày trong tuần), ví dụ trên nhắm **02:00 hằng ngày theo giờ Việt Nam**. `reconcileBilling` là logic ứng dụng chưa định nghĩa. Hãy ghi rõ timezone bằng `ZoneId` nếu kết quả phụ thuộc ngày lịch. Cron không tự biến tác vụ thành một job bền vững.

Với `CronTrigger` thông thường, thời điểm tiếp theo được tính dựa trên thông tin hoàn tất trước đó (lenient execution). Khi task kéo dài, các lần kích hoạt bị lỡ có thể được **bỏ qua**, không mặc nhiên chạy bù hết. Ở Spring 6.1.3 có các factory như `CronTrigger.forFixedExecution(...)` và `forLenientExecution(...)` để chọn cách xử lý mốc theo nhu cầu; cần kiểm tra ý nghĩa trước khi dùng, đặc biệt với nghiệp vụ không được bỏ lần chạy.

### 3.5. So sánh nhanh

| Loại lịch | Điểm tính mốc tiếp theo | Tình huống thích hợp |
| --- | --- | --- |
| `schedule(Runnable, Instant)` | Một thời điểm | Nhắc việc một lần, điều phối một bước |
| `scheduleAtFixedRate(task, period)` | Các mốc bắt đầu theo period | Sampling, đồng bộ trạng thái định kỳ với nhịp mong muốn |
| `scheduleWithFixedDelay(task, delay)` | Sau khi lần trước hoàn tất thêm một khoảng delay | Cleanup/batch đơn giản không cần nhịp bắt đầu cố định |
| `schedule(task, CronTrigger)` | Lịch ngày/giờ theo cron, zone và trigger context | Công việc hằng ngày, giờ hành chính, ngày trong tuần |

**Không dùng fixedRate nếu bạn kỳ vọng auto-overlap một task để “đuổi kịp” backlog.** Trong `ScheduledThreadPoolExecutor`, cùng một periodic series không overlap. Muốn xử lý nhiều công việc song song, thiết kế hàng đợi tác vụ và worker pool riêng, đồng thời kiểm soát việc nhân bản side effects.

## 4. Bằng chứng từ Java 17: ScheduledExecutorService chạy task hẹn giờ

Chương trình thuần JDK sau giúp hiểu phần nền Spring đang dùng: lập lịch một task trả dữ liệu sau delay, rồi đăng ký một task lặp `fixedDelay`. Sử dụng `CountDownLatch` để quan sát **ít nhất ba lần chạy**, không phụ thuộc việc thread in log theo thứ tự ngẫu nhiên.

Lưu thành `JdkScheduledExecutorDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class JdkScheduledExecutorDemo {

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(1);

        try {
            ScheduledFuture<String> once =
                    scheduler.schedule(
                            () -> "one-shot-ready",
                            20, TimeUnit.MILLISECONDS);

            System.out.println("value="
                    + once.get(5, TimeUnit.SECONDS));

            CountDownLatch three = new CountDownLatch(3);

            ScheduledFuture<?> periodic =
                    scheduler.scheduleWithFixedDelay(
                            three::countDown,
                            0, 20, TimeUnit.MILLISECONDS);

            try {
                if (!three.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "periodic task did not run 3 times");
                }
                System.out.println("threeRunsObserved=true");
            } finally {
                periodic.cancel(false);
            }
        } finally {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac JdkScheduledExecutorDemo.java
java JdkScheduledExecutorDemo
~~~

**Đầu ra do `main` in khi executor hoạt động bình thường:**

~~~text
value=one-shot-ready
threeRunsObserved=true
~~~

Giải thích:

1. `schedule(Callable<String>, ...)` tạo `ScheduledFuture<String>`; `get(5, SECONDS)` chờ task chạy và nhận giá trị.
2. `scheduleWithFixedDelay(three::countDown, ...)` thực hiện callback lặp; mỗi lần chạy giảm latch một đơn vị cho đến khi bằng 0.
3. `three.await(...)` chỉ trở về `true` khi **ít nhất ba lần callback đã chạy**. Có thể có thêm lượt gần thời điểm hủy, vì vậy chương trình **không** tuyên bố tổng số execution luôn đúng ba.
4. `periodic.cancel(false)` hủy những lần lập lịch tiếp theo; `false` không yêu cầu interrupt nếu callback đang chạy.
5. `shutdown/awaitTermination` giữ lifecycle gọn. Nếu scheduler không chạy đúng trong 5 giây, chương trình báo lỗi có chủ đích chứ không in kết quả giả.

Chỉ có thread `main` in output. Worker không in bên trong callback, nên chương trình không lệ thuộc thứ tự log giữa các threads.

## 5. Ví dụ Spring Framework 6.1.3 hoàn chỉnh: dùng bean ThreadPoolTaskScheduler

Ở đây ta chứng minh bằng API **native** và API **Spring** rằng `ThreadPoolTaskScheduler` sử dụng `ScheduledThreadPoolExecutor`, cấu hình `poolSize` và thực thi callback trên scheduler worker.

### 5.1. Cấu trúc Maven

~~~text
spring-task-scheduler-demo/
├── pom.xml
└── src/main/java/demo/
    ├── SchedulerConfig.java
    └── SpringSchedulerDemo.java
~~~

Tệp `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-task-scheduler-demo</artifactId>
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
                    <mainClass>demo.SpringSchedulerDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Tệp `src/main/java/demo/SchedulerConfig.java`:

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("lesson-sched-");
        return scheduler;
    }
}
~~~

`@Bean("taskScheduler")` đăng ký scheduler để Spring quản lý khởi tạo và đóng. `setPoolSize(2)` chọn **hai scheduler threads** thay vì mặc định một. Trong ví dụ này **không cần `@EnableScheduling`**, vì ta gọi trực tiếp `TaskScheduler.schedule...` từ `main`; `@EnableScheduling` chỉ cần khi muốn Spring tự nhận diện các phương thức `@Scheduled`.

Tệp `src/main/java/demo/SpringSchedulerDemo.java`:

~~~java
package demo;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public final class SpringSchedulerDemo {

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             SchedulerConfig.class)) {

            ThreadPoolTaskScheduler scheduler =
                    context.getBean(
                            "taskScheduler", ThreadPoolTaskScheduler.class);

            ScheduledThreadPoolExecutor nativePool =
                    scheduler.getScheduledThreadPoolExecutor();

            System.out.println("nativeBackend="
                    + (scheduler.getScheduledExecutor()
                    instanceof ScheduledThreadPoolExecutor));
            System.out.println("poolCore=" + nativePool.getCorePoolSize());

            AtomicReference<String> threadName = new AtomicReference<>();

            ScheduledFuture<?> once = scheduler.schedule(
                    () -> threadName.set(
                            Thread.currentThread().getName()),
                    Instant.now().plusMillis(20));

            if (once == null) {
                throw new IllegalStateException("No execution scheduled");
            }

            once.get(5, TimeUnit.SECONDS);

            System.out.println("onSchedulerThread="
                    + threadName.get().startsWith("lesson-sched-"));

            CountDownLatch twice = new CountDownLatch(2);

            ScheduledFuture<?> repeated =
                    scheduler.scheduleWithFixedDelay(
                            twice::countDown, Duration.ofMillis(30));

            try {
                if (!twice.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "repeated task did not run twice");
                }
                System.out.println("twoRunsObserved=true");
            } finally {
                repeated.cancel(false);
            }
        }
    }
}
~~~

Chạy từ thư mục `spring-task-scheduler-demo`:

~~~bash
mvn -q compile exec:java
~~~

**Stdout do chương trình Java in (không tính log Maven):**

~~~text
nativeBackend=true
poolCore=2
onSchedulerThread=true
twoRunsObserved=true
~~~

### 5.2. Vì sao ví dụ này đúng và có thể kiểm chứng?

- Spring quản lý `ThreadPoolTaskScheduler` được tạo trong `SchedulerConfig`. Sau khi bean khởi tạo, `getScheduledExecutor()` trả về backend, còn `getScheduledThreadPoolExecutor()` cho truy cập native implementation ở cấu hình chuẩn.
- `nativeBackend=true` cho thấy backend thực tế là `ScheduledThreadPoolExecutor`, không chỉ tương thích interface `ScheduledExecutorService`.
- `poolCore=2` phản ánh giá trị `setPoolSize(2)` đã khai báo. Đây là **core pool size**, không phải số task đã chạy hoặc worker hiện đang hoạt động.
- `schedule(..., Instant)` đăng ký callback một lần; callback lấy tên **chính thread đang chạy**. Prefix `lesson-sched-` do bean cấu hình, vì vậy `onSchedulerThread=true`.
- `scheduleWithFixedDelay(..., Duration)` đăng ký callback lặp, latch quan sát **ít nhất hai lần**, sau đó `cancel(false)` ngăn các lần lặp về sau. Chương trình không in tên thread hay timestamp phụ thuộc môi trường.
- `try-with-resources` đóng `AnnotationConfigApplicationContext` và để Spring quản lý shutdown của scheduler, thay vì tạo một pool mới cho mỗi lần gọi.

**Giới hạn test:** Những lệnh `get(5, SECONDS)` và `await(5, SECONDS)` là giới hạn chờ phòng lỗi. Nếu máy bị đình trệ quá 5 giây, chương trình có thể báo lỗi timeout; đây là tình huống không hoàn tất được tác vụ, không phải một stdout bổ sung cần “sửa” để khớp.

## 6. Tích hợp @Scheduled: chỉ bật khi cần annotation-driven scheduling

Spring hỗ trợ viết method có `@Scheduled` và tự đăng ký lịch lúc ApplicationContext sẵn sàng. Để bật:

~~~java
// Fragment cấu hình, cần import Spring annotations.
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("jobs-");
        return scheduler;
    }

    @Bean
    public CleanupJob cleanupJob() {
        return new CleanupJob();
    }
}
~~~

Method nghiệp vụ nằm trong bean được container quản lý:

~~~java
// Fragment trên class riêng; cần import @Scheduled.
public class CleanupJob {

    @Scheduled(fixedDelay = 5000)
    public void cleanup() {
        // Cleanup xong, đợi 5 giây rồi lặp lại.
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    public void refreshDailyIndex() {
        // 03:00 hằng ngày theo múi giờ đã chọn.
    }
}
~~~

Các đoạn ở mục này là **mảnh kiến trúc**, không thuộc project `SpringSchedulerDemo` độc lập ở phần 5. Trong app thật cần import `EnableScheduling`, `Scheduled`, `Bean`, `Configuration`, `ThreadPoolTaskScheduler` và đặt class đúng file/package.

### Cách Spring tìm scheduler cho @Scheduled

Theo Javadoc `@EnableScheduling` của Spring 6.1.3, Spring tìm một `TaskScheduler` bean duy nhất trong context hoặc bean có tên chuẩn **`taskScheduler`**; cũng có khả năng tìm `ScheduledExecutorService` theo quy tắc tương ứng. Nếu không có scheduler phù hợp, framework có thể tạo **default local single-thread scheduler**.

Vì thế:

- Khai báo bean tên `taskScheduler` là cách trực tiếp để routing cho `@Scheduled` thông thường.
- Nếu có **nhiều scheduler beans**, cấu hình lựa chọn rõ ràng, ví dụ `@Scheduled(scheduler = "criticalScheduler", fixedRate = 5000)` khi dùng Spring 6.1+ hoặc thông qua `SchedulingConfigurer`.
- Đừng suy luận có một bean tên `businessExecutor` hay `ioExecutor` thì `@Scheduled` tự dùng nó. Executor cho `@Async` và scheduler cho `@Scheduled` là hai bài toán lựa chọn khác nhau.

`@Scheduled` có thể được khai báo trên method trong lớp `@Configuration` theo Javadoc `@EnableScheduling`. Đây là **quy tắc của @Scheduled**, không nên nhầm với giới hạn riêng của `@Async` trên methods thuộc lớp `@Configuration`.

### 6.1. @Scheduled không có tham số và kết quả thường bị bỏ qua

Một scheduled method thông thường không nhận tham số; trường hợp phổ biến nhất là `void`. Đừng khai báo scheduled callback cần một đối số chưa được Spring cung cấp rồi kỳ vọng hệ thống tự inject argument vào mỗi lần chạy.

Trong Spring 6.1, scheduled methods có hỗ trợ thêm các reactive return types với quy tắc riêng. Nhưng với việc học scheduler truyền thống, hãy tập trung vào **`Runnable`/`void` callback**; đừng xem việc return một `CompletableFuture` từ scheduled method là chắc chắn một “job đã chờ đến khi Future hoàn tất”.

### 6.2. @Scheduled và @Async có thể thay đổi ý nghĩa hoàn tất

Ví dụ một method `@Scheduled` lập tức gọi một `@Async` service rồi return:

~~~java
// Fragment: reportService là một Spring bean khác có method @Async.
@Scheduled(fixedDelay = 5000)
public void triggerReport() {
    reportService.generateAsync();
}
~~~

Trong tình huống này, scheduled callback có thể kết thúc **ngay sau khi nộp** công việc; `fixedDelay` khi đó tính từ lúc callback hoàn tất, **không nhất thiết** từ lúc báo cáo thật hoàn thành trên async executor. Nhiều công việc báo cáo có thể chồng nhau dù bản thân callback trên scheduler không overlap.

Nếu yêu cầu **không có hai job nghiệp vụ chạy đồng thời**, cần cơ chế backpressure/lock/queue hoặc chờ completion theo thiết kế, thay vì chỉ dựa vào `fixedDelay` ở callback gửi việc.

## 7. Sizing: tại sao ThreadPoolTaskScheduler khác ThreadPoolTaskExecutor?

Cả hai đều tạo pool bean, nhưng API và backend khác nhau:

| Điểm | `ThreadPoolTaskScheduler` | `ThreadPoolTaskExecutor` |
| --- | --- | --- |
| Vai trò chính | Lên lịch một lần hoặc định kỳ | Thực thi task async do caller nộp |
| Backend thường dùng | `ScheduledThreadPoolExecutor` | `ThreadPoolExecutor` |
| Interface Spring chính | `TaskScheduler` | `TaskExecutor` / `AsyncTaskExecutor` |
| Thời điểm bắt đầu | Theo delay, fixedRate, fixedDelay, cron trigger | Theo lúc nộp, worker/queue |
| Số worker cấu hình chính | `setPoolSize(int)`; mặc định **1** | `corePoolSize`, `maxPoolSize`, `queueCapacity` |
| Queue | Delayed queue gắn với `ScheduledThreadPoolExecutor`, về cơ bản **không giới hạn cố định** | Thường có queue cấu hình theo capacity |
| Task chạy ở đâu? | **Chính scheduler thread(s)** | Worker thread(s) của executor |
| Hủy/tình trạng | `ScheduledFuture` | `Future` hoặc thông qua API khác |
| Rủi ro nổi bật | Task dài làm trễ lịch; pending delayed tasks chiếm bộ nhớ | Queue dồn, pool quá tải, rejection, task chậm |

`ScheduledThreadPoolExecutor` trong Java 17 hoạt động như fixed-size pool với `corePoolSize` threads và **unbounded delayed queue**. Vì vậy thay đổi `maximumPoolSize` theo phong cách `ThreadPoolExecutor` **không hữu ích** cho backend này. Đừng tìm `setMaxPoolSize`/`setQueueCapacity` của `ThreadPoolTaskExecutor` để áp vào scheduler như thể hai class có cùng cấu trúc quản lý queue.

Nếu số lượng lịch tăng không kiểm soát, unbounded delayed queue có thể dẫn đến **retention bộ nhớ**; scheduler này không tạo ra độ bền hay khả năng tự phân phối jobs. Hãy giới hạn số lịch được tạo, quản lý hủy và lưu trạng thái ở hệ thống phù hợp khi cần.

### 7.1. Chọn poolSize theo workload

`setPoolSize(1)` phù hợp nếu rất ít job, chạy nhanh và không yêu cầu đồng thời. `setPoolSize(4)` cho phép những task khác nhau sử dụng nhiều scheduler threads, nhưng không giúp một task đang chờ I/O tự hoàn tất sớm.

Với task CPU-bound, số worker hợp lý phụ thuộc lõi CPU và tải khác. Với blocking I/O, nhiều scheduler worker có thể giúp chạy các job khác nhưng vẫn phải đặt timeout cho HTTP/SQL. Việc tăng poolSize quá lớn có thể tạo thêm contention và kéo dài queue/side effects.

Một cách tổ chức thường hữu ích:

~~~text
TaskScheduler (ít thread, điều phối giờ chạy)
      |
      +--> nhẹ: cập nhật cache/metric nhanh trên scheduler worker
      |
      +--> nặng: nộp một đơn vị việc sang TaskExecutor/queue riêng
                    |
                    +--> TaskExecutor workers + timeout + backpressure
~~~

Nếu tách việc nặng như vậy, phải lưu ý **ScheduledFuture chỉ đại diện callback đã schedule**, không nhất thiết đại diện toàn bộ công việc mà callback gửi sang một executor khác. Cần theo dõi Future hoặc job ID ở hệ thống xử lý thật.

### 7.2. Scheduling không phải durable queue

`ThreadPoolTaskScheduler` làm việc trong bộ nhớ của **một JVM**. Khi process restart, lịch tạo động bằng code có thể mất nếu không được đăng ký/lưu lại. Nếu ứng dụng chạy ba replicas và mỗi replica có `@Scheduled`, công việc có thể chạy **ba lần**, không tự có global single leader.

Với tác vụ không được phép trùng hoặc mất, hãy cân nhắc:

- Database-backed job state và distributed lock/leader election có timeout, fencing phù hợp.
- Quartz với job store phù hợp hoặc một bộ điều phối/queue bền vững.
- Idempotency keys, deduplication, transactional outbox khi side effects quan trọng.
- Monitoring “missed runs”, retries có giới hạn, reconciliation sau downtime.

Không dùng riêng bean scheduler làm đảm bảo “exactly once” hoặc “cluster-wide scheduling”.

## 8. ScheduledFuture: hủy và quan sát đúng

Khi gọi `TaskScheduler.schedule...`, Spring thường trả `java.util.concurrent.ScheduledFuture<?>`. Đừng nhầm Future này với một lời hứa rằng task luôn chạy đúng mốc đã đặt.

### 8.1. Một lần chạy

Với `schedule(Runnable, Instant)`, `future.get()` trả `null` nếu callback hoàn tất bình thường; nếu callback thất bại, Spring có thể chuyển exception theo cơ chế xử lý lỗi của scheduler. Với `schedule(Runnable, Trigger)`, việc trả `null` có thể xảy ra nếu trigger không định ra lần kích hoạt nào; hãy xem hợp đồng return của overload cụ thể.

### 8.2. Lịch lặp không có “giá trị cuối” tự nhiên

Với `scheduleAtFixedRate` hoặc `scheduleWithFixedDelay`, Future đại diện **chuỗi lần chạy định kỳ**. Một chuỗi có thể tiếp tục lâu dài cho đến khi bị hủy hoặc scheduler shutdown; vì vậy **không gọi `future.get()` vô thời hạn** rồi mong nó trả kết quả “tick tiếp theo”.

~~~java
// Fragment: periodic là ScheduledFuture<?> từ scheduleAtFixedRate(...).
boolean canceled = periodic.cancel(false);
boolean done = periodic.isDone();
~~~

`cancel(false)` yêu cầu ngừng các lần kích hoạt tiếp theo mà không cố interrupt callback đang chạy. `cancel(true)` thử interrupt trong những implementation hỗ trợ; task phải hợp tác với interrupt, và I/O bên dưới không tự rollback. Không dùng cancellation làm một lời bảo đảm side effect chưa xảy ra.

### 8.3. remove-on-cancel và bộ nhớ delayed queue

`ScheduledThreadPoolExecutor` của JDK có chính sách `setRemoveOnCancelPolicy(boolean)`. Khi bật, các delayed tasks đã cancel có thể được lấy ra khỏi queue sớm hơn, giảm retention nếu ứng dụng hủy rất nhiều lịch đang chờ.

~~~java
// Fragment sau khi bean scheduler đã được khởi tạo.
scheduler.getScheduledThreadPoolExecutor()
        .setRemoveOnCancelPolicy(true);
~~~

Ở một số phiên bản Spring, method convenience `ThreadPoolTaskScheduler.setRemoveOnCancelPolicy(true)` tồn tại nhưng đã deprecated; Javadoc hướng dẫn dùng native accessor khi phù hợp. Đừng bật policy bằng cách sửa một instance chưa khởi tạo hoặc nhầm với queue capacity của executor thường.

### 8.4. Shutdown policies

`ScheduledThreadPoolExecutor` có các chính sách như `setContinueExistingPeriodicTasksAfterShutdownPolicy` và `setExecuteExistingDelayedTasksAfterShutdownPolicy`. Chúng ảnh hưởng liệu lịch lặp hoặc tác vụ một lần đang chờ có được tiếp tục chạy sau `shutdown()` hay không.

Không tự đặt `true` cho mọi chính sách và mong shutdown ứng dụng nhanh. Thay vào đó, xác định job nào được phép hoàn tất, có cần lưu tiến độ để resume, và có deadline dừng hợp lý. Với Spring, tham số lifecycle của bean và đóng `ApplicationContext` liên quan trực tiếp đến hành vi dọn scheduler.

## 9. Error handling: Spring không hoàn toàn giống JDK thuần

Đây là một khác biệt quan trọng khi chọn scheduler.

### 9.1. JDK ScheduledExecutorService thuần

Với `ScheduledThreadPoolExecutor.scheduleAtFixedRate`/`scheduleWithFixedDelay`, nếu **một lần chạy ném exception chưa được xử lý**, các lần chạy tiếp theo của cùng periodic task **bị ngừng**, và `ScheduledFuture.get()` có thể ném `ExecutionException`. Đây là hợp đồng của JDK Java 17.

Để quan sát, lưu thành `JdkScheduledFailureDemo.java`:

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class JdkScheduledFailureDemo {

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        AtomicInteger attempts = new AtomicInteger();

        try {
            ScheduledFuture<?> repeated =
                    scheduler.scheduleAtFixedRate(() -> {
                        attempts.incrementAndGet();
                        throw new IllegalStateException("boom");
                    }, 0, 10, TimeUnit.MILLISECONDS);

            try {
                repeated.get(5, TimeUnit.SECONDS);
                System.out.println("unexpected-success");
            } catch (ExecutionException ex) {
                System.out.println("cause="
                        + ex.getCause().getClass().getSimpleName());
            }

            System.out.println("attempts=" + attempts.get());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
~~~

Chạy:

~~~bash
javac JdkScheduledFailureDemo.java
java JdkScheduledFailureDemo
~~~

Đầu ra:

~~~text
cause=IllegalStateException
attempts=1
~~~

Lần đầu ném `IllegalStateException`, JDK Future giữ exception và không chạy những lần định kỳ tiếp theo.

### 9.2. Spring ThreadPoolTaskScheduler mặc định

Spring có lớp bọc error handling cho scheduled Runnables. Theo `TaskUtils`, **mặc định**:

- Tác vụ **lặp**: lỗi được log, thường **bị suppress** để lần tiếp theo còn có thể chạy.
- Tác vụ **one-shot**: lỗi được log rồi **propagate**, vì caller có thể mong đọc lỗi từ `Future`.

Do đó không nên suy luận đơn giản “một exception trong `@Scheduled(fixedRate=...)` chắc chắn dừng lịch” chỉ từ hợp đồng JDK thuần. **Spring wrapper có thể bắt/suppress exception trước khi nó tới ScheduledThreadPoolExecutor.**

Có thể cài chính sách qua `setErrorHandler(...)`:

~~~java
// Fragment: scheduler là ThreadPoolTaskScheduler chưa initialize.
scheduler.setErrorHandler(error -> {
    // Log kèm job ID, metric/alert theo chính sách ứng dụng.
    // Nếu cần fail-stop, chủ động chọn cách propagate phù hợp.
});
~~~

Ví dụ handler trên **không ném lại lỗi**, nên nó đang thực hiện chính sách **suppress**. Nếu ứng dụng đòi hỏi dừng lịch sau lỗi, phải thiết kế rõ việc propagate/cancel và đảm bảo alert; nếu đòi hỏi chạy tiếp, phải có retry budget/idempotency để không lặp mãi lỗi có side effects.

**Cảnh báo:** `ErrorHandler` không phải đường xử lý mọi loại failure. Nếu method scheduled chỉ gửi một tác vụ sang executor khác, lỗi trong task thật có thể xuất hiện ở Future/handler **của executor kia**, không phải trong callback hẹn giờ.

### 9.3. Rejection tại điểm schedule

Khi executor đã shutdown hoặc không nhận thêm task, `schedule(...)` có thể ném **`TaskRejectedException`** của Spring (JDK native ném `RejectedExecutionException`). Lỗi này xảy ra **ngay tại điểm đặt lịch**, trước khi caller nhận được `ScheduledFuture`.

~~~java
// Fragment trong nơi điều phối công việc.
try {
    ScheduledFuture<?> handle =
            scheduler.schedule(() -> doWork(), Instant.now());
    storeHandle(handle);
} catch (org.springframework.core.task.TaskRejectedException ex) {
    reportScheduleFailure(ex);
}
~~~

`doWork`, `storeHandle` và `reportScheduleFailure` là hàm ứng dụng chưa được khai báo; đoạn này chỉ minh họa vị trí bắt lỗi. Không log “job đã được lên lịch” nếu lời gọi đã bị từ chối.

## 10. Lifecycle, pause/resume và ngữ cảnh thực thi

### 10.1. Bean lifecycle

Khi tạo scheduler bằng `@Bean`, container quản lý các callback khởi tạo, kết thúc và sự kiện context. Với project Java `main`, dùng `try-with-resources` đóng `AnnotationConfigApplicationContext` là cách dọn worker. Với ứng dụng web/Boot, hãy đảm bảo quá trình shutdown có chính sách rõ ràng cho task đang chạy.

Spring Framework 6.1 bổ sung **pause/resume** và cơ chế graceful shutdown qua lifecycle của `ThreadPoolTaskScheduler`. Điều này giúp điều phối hoạt động trong app, nhưng pause không phải freeze JVM hoàn hảo, không biến scheduler thành queue bền vững và không thay thế thiết kế xử lý công việc chưa hoàn tất.

### 10.2. Context propagation

Một worker scheduler là **thread khác request thread**. MDC, request attributes, Spring Security context và transaction của caller **không tự xuất hiện đúng** trong scheduled callback. Đặc biệt, việc sử dụng `InheritableThreadLocal` không phải giải pháp chung cho pool tái sử dụng workers: giá trị kế thừa lúc tạo thread, không phải mỗi lần job chạy.

Với scheduler chạy lặp mỗi giờ/ngày, nên thiết kế job tự lấy **dữ liệu và security context phù hợp ở thời điểm chạy**, dùng principal/service account được cấp quyền theo chính sách, và mở transaction boundary riêng cho job nếu cần.

Không lưu `HttpServletRequest`, entity session hoặc connection vào một `Runnable` chạy sau nhiều giờ. Truyền ID bất biến hoặc một job specification tối thiểu và truy xuất dữ liệu lại khi chạy.

### 10.3. Scheduler không quyết định độ chính xác của cron trong mọi tình huống

Cron liên quan đến timezone và lịch tường, nên khi múi giờ chuyển DST, có thể có giờ không tồn tại hoặc giờ lặp. Cron trigger phải được kiểm thử theo zone và version; không giả định 24 giờ luôn có 24 lần chạy ở mọi múi giờ.

Nếu task chậm và `CronTrigger` ở chế độ lenient, một số mốc lịch có thể bị bỏ qua. Với nghiệp vụ cần xử lý từng kỳ, hãy lưu watermark/job-period ID và tự lập kế hoạch catch-up hợp lệ, thay vì chỉ dựa vào số lần callback scheduler đánh thức.

## 11. Quan sát hệ thống và xử lý khi lịch trễ

### 11.1. Các metric nên ghi nhận

- **Scheduled lag**: chênh lệch giữa thời gian dự kiến và thời điểm task thật bắt đầu.
- **Execution duration**: thời gian task chiếm scheduler thread; phân biệt với thời gian toàn bộ job nếu task gửi việc sang pool khác.
- **Active worker** và **pool size**: `getActiveCount()`, `getPoolSize()` của scheduler theo thời gian.
- **Delayed queue depth**: kích thước queue native và số lịch chờ; cân nhắc cancel retention.
- **Failures**: số lần lỗi ở callback, số lần retry, số lần schedule bị từ chối và tình trạng downstream.
- **Missed run / duplicate run**: kỳ hạn bỏ lỡ, chạy lặp lại do redeploy, chạy trùng giữa replicas.

Với backend chuẩn có thể đọc:

~~~java
// Fragment sau khi scheduler đã initialize.
int activeWorkers = scheduler.getActiveCount();
int liveWorkers = scheduler.getPoolSize();

int delayedQueueDepth =
        scheduler.getScheduledThreadPoolExecutor()
                .getQueue().size();
~~~

Các số liệu này là **snapshot tức thời**; không suy ra SLA chỉ từ một lần đọc. Một queue dài chưa chắc có nghĩa pool nghẽn vì queue còn chứa lịch **chưa đến hạn**. Đo thêm những task **đã đến hạn nhưng phải chờ**, và phân biệt số task scheduled với số task executable.

### 11.2. Tình huống điều tra: @Scheduled không chạy

Giả sử method annotated `@Scheduled` không xuất hiện log trong vài phút. Kiểm tra theo thứ tự:

1. `@EnableScheduling` có được bật đúng application context hay chưa?
2. Bean chứa `@Scheduled` có được Spring container quản lý và khởi tạo duy nhất không?
3. Scheduler mà annotation đang chọn có đúng tên/qualifier không?
4. `setPoolSize(1)` có một task khác đang giữ worker quá lâu không? Kiểm tra thread dump/active count.
5. Method có exception và Spring error handler đang log/suppress không, hay chính sách tùy chỉnh đang dừng schedule?
6. Cron expression, timezone và thời gian hiện tại có phù hợp không? Có rơi vào DST hoặc lịch không có fire time không?
7. Context có đang shutdown/pause không? Có bean scheduler bị đóng sớm không?
8. Ứng dụng nhiều replicas có log ở instance khác mà bạn đang bỏ sót không?

Đừng vội tăng poolSize trước khi xác định task nào đang giữ worker và đang chờ tài nguyên gì.

## 12. Chọn công cụ phù hợp

| Nhu cầu | Lựa chọn cân nhắc |
| --- | --- |
| Hẹn một việc ngắn sau một khoảng delay | `ThreadPoolTaskScheduler` hoặc `ScheduledExecutorService` |
| Chạy việc định kỳ trong một app Spring | `ThreadPoolTaskScheduler` + `@Scheduled` |
| Lịch cron có timezone và cấu hình DI | Spring `CronTrigger` / `@Scheduled(cron=..., zone=...)` |
| Chạy nhiều task được submit tức thời, cần queue capacity và pool tuning riêng | `ThreadPoolTaskExecutor` |
| Điều phối lịch rồi xử lý việc nặng | `TaskScheduler` kích hoạt + worker executor/queue riêng có backpressure |
| Sử dụng Jakarta EE managed threads | `DefaultManagedTaskScheduler` hoặc managed scheduled executor |
| Cần task durable, cluster coordination, persistence, retry | Quartz/job store, workflow engine, broker/durable queue và idempotency |
| Tận dụng virtual threads trên JDK 21+ | `SimpleAsyncTaskScheduler` hoặc executor virtual-thread phù hợp; đánh giá bản Spring/JDK và semantics khác |

**Phân biệt `SimpleAsyncTaskScheduler` với `ThreadPoolTaskScheduler`:** Spring 6.1 có lựa chọn scheduler phù hợp virtual threads của JDK 21, với một scheduler thread kích hoạt task execution khác, nhưng fixed-delay có đặc điểm riêng trên scheduler thread. Không lấy hành vi này áp sang `ThreadPoolTaskScheduler` truyền thống; hai implementation có thiết kế khác nhau.

## 13. Những nhầm lẫn thường gặp

1. **“`ThreadPoolTaskScheduler` là một `ThreadPoolTaskExecutor` có thêm timer.”** Không đúng. Scheduler bao `ScheduledThreadPoolExecutor`, có API và queue semantics khác.
2. **“Task được scheduler đánh thức rồi chạy trên worker pool tách biệt.”** Không mặc định. `ThreadPoolTaskScheduler` chạy callback trên chính scheduler threads.
3. **“Pool mặc định đủ cho mọi job.”** Mặc định một thread; task chạy lâu có thể làm các lịch khác trễ.
4. **“`setMaxPoolSize(100)` là cách tăng scheduling concurrency.”** Không đúng với `ScheduledThreadPoolExecutor`; chỉnh `setPoolSize` của Spring scheduler.
5. **“Fixed rate bảo đảm bắt đầu đúng mỗi 5 giây dù task mất 10 giây.”** Sai. Lần sau có thể bắt đầu muộn; cùng periodic JDK task không overlap.
6. **“Fixed delay tính từ lúc bắt đầu task.”** Sai. Delay được tính sau khi lần trước kết thúc.
7. **“Cron tự chạy bù tất cả mốc bị bỏ qua.”** Sai. Tùy trigger semantics; lenient trigger có thể bỏ mốc.
8. **“`@Scheduled` chắc chắn có executor riêng nếu đã khai báo `@Async`.”** Sai. Hai cơ chế chọn tài nguyên riêng.
9. **“`@Scheduled` trong Spring luôn dừng lặp khi một lần callback ném exception.”** Không chắc. Spring mặc định log/suppress nhiều lỗi của repeating tasks.
10. **“`ScheduledFuture.get()` của periodic task sẽ trả mỗi khi có tick.”** Sai. Một Future đại diện cả chuỗi; có thể không bao giờ hoàn tất bình thường.
11. **“Đã cancel Future nên chắc chắn ngắt HTTP/SQL.”** Sai. `cancel(true)` chỉ thử interrupt; thư viện I/O và worker phải hợp tác.
12. **“Delayed queue dài có nghĩa từng ấy job đã tới hạn.”** Sai. Queue còn giữ task chưa đến hạn.
13. **“Mỗi replica tự chạy @Scheduled bảo đảm toàn cluster một lần.”** Sai. Scheduler cục bộ không tự có distributed lock.
14. **“Dùng scheduler là đã có persistent jobs.”** Sai. Cần job store hoặc queue durable.
15. **“Spring @Scheduled và Spring @Async có cùng giới hạn trên @Configuration.”** Sai. Javadoc `@EnableScheduling` cho phép @Scheduled trên method cấu hình, còn @Async có hợp đồng riêng.
16. **“Pause/resume từ Spring 6.1 là thay thế lưu tiến độ job.”** Sai. Lifecycle control không bảo đảm phục hồi các job bị mất sau JVM restart.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** `ThreadPoolTaskScheduler` dựa trên implementation nào của JDK?  
**Đáp án:** Theo mặc định, nó bao bọc `ScheduledThreadPoolExecutor`, triển khai `ScheduledExecutorService`.

**Câu 2.** Task của `ThreadPoolTaskScheduler` được thực thi ở đâu?  
**Đáp án:** Trên **chính scheduler thread(s)**, không tự được chuyển sang một pool xử lý khác.

**Câu 3.** Mặc định scheduler có bao nhiêu threads và tăng ở đâu?  
**Đáp án:** Mặc định **một thread**; gọi `setPoolSize(int)` để tăng số scheduler workers khi phù hợp.

**Câu 4.** Fixed rate period 5 giây, task mất 8 giây: hai lần của cùng periodic task có overlap trên `ScheduledThreadPoolExecutor` không?  
**Đáp án:** Không. Lần sau có thể bắt đầu muộn nhưng các lần chạy của cùng một series không chồng lên nhau.

**Câu 5.** Fixed delay 5 giây, task mất 8 giây: mốc bắt đầu tiếp theo gần nhất sau bao lâu tính từ lúc bắt đầu trước?  
**Đáp án:** Khoảng **13 giây** (8 giây chạy + 5 giây chờ), chưa tính scheduler lag.

**Câu 6.** `@Scheduled` có cần `@EnableScheduling` không?  
**Đáp án:** Có nếu muốn Spring tự quét và đăng ký annotation-driven scheduling trong application context, trừ khi infrastructure tương đương được bật bằng cách khác. **Không cần** nếu bạn chỉ gọi trực tiếp `TaskScheduler.schedule` trong code.

**Câu 7.** Vì sao `ScheduledFuture.get()` có thể chặn vô thời hạn?  
**Đáp án:** Với lịch lặp, Future đại diện **cả chuỗi** và có thể không bao giờ hoàn tất bình thường cho đến khi hủy, lỗi hoặc shutdown.

**Câu 8.** Vì sao ScheduledThreadPoolExecutor không có kiểu `maxPoolSize`/`queueCapacity` giống ThreadPoolTaskExecutor?  
**Đáp án:** Backend scheduler dùng pool size theo core thread và delayed queue về cơ bản unbounded; tăng maximum pool size không có hiệu quả tương tự queue bounded của executor thường.

**Câu 9.** Với Spring, repeating task ném exception có chắc bị dừng luôn?  
**Đáp án:** Không; Spring mặc định thường log/suppress lỗi cho repeating tasks, khác với JDK native periodic schedule không có wrapper.

**Câu 10.** Làm thế nào để một scheduled job chỉ chạy một lần trên toàn bộ ba replicas?  
**Đáp án:** Cần cơ chế điều phối cluster như distributed lock/leader election, Quartz job store hoặc hệ thống queue/workflow phù hợp cùng idempotency; `ThreadPoolTaskScheduler` cục bộ không tự giải quyết.

## 15. Tài liệu chính thức

- [Spring Framework 6.1.x — ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.8/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): backend `ScheduledThreadPoolExecutor`, default poolSize=1, task chạy trên scheduler workers, cấu hình native và lifecycle.
- [Spring Framework 6.1.3 — TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/TaskScheduler.html): API `schedule`, fixed rate, fixed delay và trigger.
- [Spring Framework 6.1.3 — EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html): cấu hình `@Scheduled` và cách chọn bean scheduler.
- [Spring Framework 6.1.3 — CronTrigger Javadoc](https://docs.spring.io/spring-framework/docs/6.1.3/javadoc-api/org/springframework/scheduling/support/CronTrigger.html): cron, múi giờ, lenient/fixed execution.
- [Spring Framework 6.1.x — TaskUtils](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/scheduling/support/TaskUtils.html): mặc định log/suppress lỗi repeating task và propagate one-shot.
- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): kiến trúc scheduler, `@Scheduled`, `CronTrigger`, task lifecycle và implementations.
- [Java SE 17 — ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): định nghĩa delay, periodic schedule và `ScheduledFuture`.
- [Java SE 17 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html): delayed queue, fixed-size core pool, no-overlap, exception suppression trong JDK native, remove-on-cancel và shutdown.
- [Java SE 17 — ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): Future cho task có độ trễ, cancel và trạng thái.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduler-selection-002">poolSize mặc định và ảnh hưởng của công việc chạy lâu</a>

<details>
<summary>Xem chi tiết</summary>

# poolSize mặc định và ảnh hưởng của công việc chạy lâu trong Spring TaskScheduler

## 1. Vấn đề thực tế: một job dài khiến job khác bị trễ

Giả sử ứng dụng Spring có hai công việc định kỳ:

- **Job A:** tổng hợp báo cáo, thường mất vài phút vì phải truy vấn cơ sở dữ liệu và gọi dịch vụ ngoài.
- **Job B:** cập nhật heartbeat hoặc trạng thái đơn hàng mỗi vài giây, cần có độ trễ nhỏ.

Cấu hình ban đầu không định nghĩa scheduler cụ thể, chỉ bật `@EnableScheduling` và đặt `@Scheduled` trên hai method. Môi trường có thể dùng một scheduler **chỉ có một thread**. Khi A giữ scheduler thread để chạy một tác vụ dài, B không thể bắt đầu đúng giờ dù trigger đã đến hạn.

Đây thường không phải lỗi của cron, lỗi của `@Scheduled` hay do một lời gọi `Thread.sleep()` bị sai. Đó là **cạnh tranh tài nguyên**: nhiều scheduled jobs cùng cần worker, nhưng scheduler chỉ có một worker khả dụng.

Câu hỏi cần trả lời:

1. `ThreadPoolTaskScheduler` dùng `poolSize` bao nhiêu nếu không thiết lập?
2. Điều gì xảy ra nếu thời gian chạy của task dài hơn chu kỳ của chính nó hoặc trùng thời điểm job khác?
3. Khi nào nên tăng `poolSize`, tách scheduler hoặc tách phần thực thi dài sang `TaskExecutor`?
4. Làm sao kiểm chứng job bị trễ bởi scheduler mà không đoán theo tên thread hoặc chỉ nhìn cron expression?

**Kết luận quan trọng nhất:** với Spring Framework 6.2, `ThreadPoolTaskScheduler` mặc định có **poolSize = 1**. Nó **chạy task trên chính scheduler thread**, không tự chuyển phần thân task sang một executor độc lập. Nếu một task giữ thread quá lâu, các task cùng scheduler bị trì hoãn. Tuy nhiên, `poolSize=1` không phải mặc định duy nhất của mọi sản phẩm hoặc mọi implementation mang tên scheduler trong toàn hệ Spring.

## 2. TaskScheduler khác ThreadPoolTaskExecutor như thế nào?

Hai abstraction có vai trò khác nhau:

| API / implementation | Trả lời câu hỏi gì? | Nguồn worker | Thuộc tính thường dùng |
| --- | --- | --- | --- |
| `TaskScheduler` | **Khi nào** chạy một Runnable? | Phụ thuộc implementation | Trigger, fixed rate, fixed delay, cron |
| `ThreadPoolTaskScheduler` | Lên lịch và **thực thi** các task trên scheduled executor | `ScheduledThreadPoolExecutor` | `setPoolSize(int)`, thread prefix, error handler |
| `TaskExecutor` | **Ở đâu / bằng cách nào** thực thi một Runnable? | Phụ thuộc implementation | `execute(Runnable)` |
| `ThreadPoolTaskExecutor` | Thực thi và queue các task thông thường | `ThreadPoolExecutor` | `corePoolSize`, `maxPoolSize`, `queueCapacity` |
| `SimpleAsyncTaskScheduler` (Spring 6.1+) | Scheduler đơn giản, phù hợp virtual threads | Một thread điều phối và thread thực thi tùy loại trigger | Không đồng nhất với pool tuning của ThreadPoolTaskScheduler |

**Không nhầm `poolSize` của scheduler với `maxPoolSize` của executor thường.** Khi cần tăng số scheduled jobs có thể chạy song song trên `ThreadPoolTaskScheduler`, dùng `setPoolSize(...)`; không tìm cách sửa `queueCapacity` như ở `ThreadPoolTaskExecutor`. `ScheduledThreadPoolExecutor` dùng một delayed queue và core pool cố định, nên việc đổi `maximumPoolSize` của lớp cha thường không mang lại tác dụng tăng concurrency mong đợi.

### 2.1. Scheduler chạy task trực tiếp trên scheduler thread

Javadoc của `ThreadPoolTaskScheduler` ghi rõ nó bọc `ScheduledThreadPoolExecutor`. Task execution diễn ra trên các scheduler threads, **không phải một thread pool riêng tự sinh thêm cho từng job**.

Hệ quả:

- Một lần chạy 30 giây giữ một worker khoảng 30 giây, kể cả khi phần lớn thời gian là block I/O.
- Nếu chỉ có một worker, một job khác đến hạn trong khoảng đó phải chờ.
- Nếu có hai worker, có thể có hai **scheduled executions độc lập** đang chạy cùng lúc.
- Nếu cả hai worker đều bị giữ bởi hai job dài, job thứ ba vẫn có thể bị trễ.
- Vì vậy tăng poolSize **tăng trần concurrency**, không tự làm giảm thời gian thực hiện của job.

## 3. Mặc định poolSize=1 có phạm vi nào?

Cần phân biệt những con đường tạo scheduler.

### 3.1. Tự tạo ThreadPoolTaskScheduler nhưng không setPoolSize

```java
@Bean("taskScheduler")
public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setThreadNamePrefix("schedule-");
    return scheduler;
}
```

Đây là **fragment** đặt trong class `@Configuration`, với imports `@Bean` và `ThreadPoolTaskScheduler`. Trong Spring Framework 6.2.1, nếu không gọi `setPoolSize(...)` thì scheduler có **một thread** theo mặc định.

### 3.2. Bật @EnableScheduling nhưng không cung cấp scheduler

Khi `@EnableScheduling` không tìm được scheduler phù hợp qua các cơ chế lookup trong ApplicationContext, Spring có thể tạo **một scheduler cục bộ một thread** để chạy `@Scheduled` jobs. Đừng mặc định rằng đã bật `@EnableScheduling` thì tự động có một pool lớn.

### 3.3. XML task namespace

Trong Spring XML, khai báo:

```xml
<task:scheduler id="taskScheduler"/>
```

nếu không thêm `pool-size` thì task namespace cũng tạo scheduler chỉ có **một thread**. Đặt rõ:

```xml
<task:scheduler id="taskScheduler" pool-size="4"/>
```

để có pool bốn thread cho scheduler do phần tử này khai báo. Đây là fragment XML, cần root `<beans>`, namespace `task` và schema tương ứng.

### 3.4. Spring Boot và hệ sinh thái có thể có mặc định khác

Với Spring Boot dùng scheduler auto-configured dựa trên `ThreadPoolTaskScheduler` (không bật virtual threads), tài liệu Boot nêu kích thước pool mặc định là **một thread**, có thể chỉnh qua property:

```properties
spring.task.scheduling.pool.size=4
spring.task.scheduling.thread-name-prefix=scheduling-
```

Đây là fragment cấu hình Spring Boot và chỉ có tác dụng khi **auto-configured scheduler thực sự được dùng**; một bean `@Bean ThreadPoolTaskScheduler` tự tạo không mặc nhiên nhận lại các properties này.

Với Boot ở phiên bản hỗ trợ và bật virtual threads bằng `spring.threads.virtual.enabled=true` trên Java 21+, implementation tự động có thể là `SimpleAsyncTaskScheduler` và bỏ qua properties pooling. Khi đó không nên gán kết quả của `setPoolSize(...)` trên ThreadPoolTaskScheduler cho scheduler đang chạy thực tế.

**Lưu ý các framework phụ:** Spring Integration, Quartz hoặc scheduler được application server quản lý có thể có chính sách mặc định riêng. Phải kiểm tra implementation và cấu hình đã được Spring resolve trong đúng ApplicationContext, không áp câu “mọi Spring scheduler mặc định một thread” một cách tuyệt đối.

## 4. Một scheduler một thread gây hiện tượng gì?

Xét ba job dùng chung một scheduler một thread:

```text
Tương đối theo thời gian:
 0s      2s      4s      6s      8s      10s
 |-------|-------|-------|-------|--------|

Scheduler thread-1
 [========== JOB A chiếm worker 0s..8s ==========][B][C]
          ^ B đến hạn ở 2s
                    ^ C đến hạn ở 4s
                                  ^ A mới hoàn tất ở 8s

B và C chỉ có thể bắt đầu khi worker có cơ hội nhận chúng.
```

Đây là sơ đồ ý tưởng, **không phải khẳng định chính xác thứ tự bắt đầu của mọi loại trigger**. Với fixedRate/fixedDelay/cron, cách xác định lần kích hoạt tiếp theo và các lần trễ khác nhau. Một số cron triggers có thể tính lần tiếp theo theo thời điểm hoàn tất và **bỏ qua những lần firing đã lỡ**, thay vì đưa tất cả lần lỡ vào queue để chạy bù.

Những hậu quả có thể đo được:

- Job B bị trễ dù logic của B chỉ mất vài mili giây.
- Latency từ thời điểm đến hạn đến thời điểm bắt đầu thực tế tăng.
- Heartbeat, cache refresh, batch và housekeeping có thể xuất hiện log không đều.
- Worker đang block I/O khiến CPU thấp nhưng scheduler vẫn “nghẽn”.
- Nhiều scheduled jobs cộng dồn có thể tạo **schedule drift** và làm SLO bị vi phạm.

**Điểm mấu chốt:** scheduler bị block không nhất thiết xuất hiện như CPU cao; nhìn active threads, task duration và lịch thực tế mới thấy rõ.

## 5. fixedDelay, fixedRate và cron khác nhau khi task chạy lâu

Giả sử một job cần **7 giây** để hoàn tất, trong khi lịch mong muốn là **3 giây**.

| Cách khai báo | Cách quyết định lần tiếp theo | Khi job mất 7 giây |
| --- | --- | --- |
| `@Scheduled(fixedDelay = 3000)` | Chờ **3 giây sau khi lần trước kết thúc** rồi mới đến hạn lần sau | Một chu kỳ từ start tới start khoảng 10 giây, chưa tính scheduler delay |
| `@Scheduled(fixedRate = 3000)` | Đến hạn theo nhịp đều 3 giây từ mốc bắt đầu | Lần chạy sau có thể **bắt đầu muộn** vì lần trước chưa xong; trong một periodic schedule trên ScheduledThreadPoolExecutor, không chạy chồng lấn cùng schedule |
| `@Scheduled(cron = "*/3 * * * * *")` | Theo các mốc cron mỗi ba giây | Hành vi khi chạy lâu/missed firing phụ thuộc trigger và cơ chế reschedule; không mặc định mọi lần bỏ lỡ đều được chạy bù |

### 5.1. fixedDelay

Ví dụ:

```java
@Scheduled(fixedDelay = 3000, initialDelay = 1000)
public void refresh() {
    blockingRefresh();
}
```

Fragment này nằm trên Spring-managed bean và cần `@EnableScheduling`, method `blockingRefresh()` có thật. Nếu `refresh()` mất 7 giây, lần sau không đến hạn trước khoảng 3 giây *sau khi lần trước kết thúc*. Với cùng một fixed-delay registration, tăng poolSize không làm **riêng job này** chạy nhiều bản sao song song; mục tiêu tăng poolSize là giải phóng chỗ cho **các jobs độc lập**.

### 5.2. fixedRate

```java
@Scheduled(fixedRate = 3000, initialDelay = 1000)
public void collectMetrics() {
    longRunningCollection();
}
```

Ở đây nhịp fixed rate tính theo các mốc thời gian mong muốn. Nếu công việc kéo dài hơn period, execution kế tiếp có thể muộn. Với một periodic task đã đăng ký bằng `ScheduledThreadPoolExecutor.scheduleAtFixedRate(...)`, JDK bảo đảm **các lần thực hiện nối tiếp của chính periodic registration ấy không chồng lấn**.

Do đó, đừng khẳng định “đặt poolSize=8 và fixedRate=3s trong khi task chạy 7s sẽ làm tám bản của cùng một registration chạy đồng thời”. Các jobs **khác nhau** hoặc các registration độc lập có thể chạy song song nếu scheduler có đủ threads.

### 5.3. cron

```java
@Scheduled(cron = "0 */5 * * * *", zone = "Asia/Ho_Chi_Minh")
public void everyFiveMinutes() {
    exportSummary();
}
```

Đây là fragment, `exportSummary()` cần tồn tại. Cron biểu diễn các mốc theo lịch và timezone; việc diễn giải missed executions có thể khác với fixedRate, phụ thuộc `CronTrigger` và cách lập lịch lại. Chú ý DST/timezone khi nghiệp vụ dựa vào lịch địa phương và môi trường có chuyển giờ.

Nếu một job chạy 7 phút nhưng cron mỗi 5 phút, không mặc định sẽ có hai bản đồng thời hoặc sẽ “bù” chính xác mọi lần bỏ lỡ. Cần đo behavior implementation đang dùng và thiết kế cơ chế concurrency cho business requirement.

### 5.4. @Scheduled lặp nhiều lần trên cùng method

Spring cho phép khai báo lặp `@Scheduled`. Mỗi declaration có thể tạo một registration/trigger riêng; những registration **độc lập** đó có thể overlap hoặc chạy song song nếu scheduler có đủ worker. Vì thế câu “một scheduled method không bao giờ chạy chồng lấn” là quá rộng: chỉ có thể kết luận hẹp theo **một registration** và semantics thực tế của scheduler.

## 6. Ví dụ runnable chứng minh poolSize=1 gây chậm job khác

Ví dụ kiểm thử bằng **Spring Framework 6.2.1, Java 21 và Maven**, không cần Spring Boot. Ta tạo hai scheduler:

- `singleScheduler`: không gọi `setPoolSize(...)`, để nguyên **mặc định 1**.
- `parallelScheduler`: gọi `setPoolSize(2)`, có **2 scheduler workers**.

Với mỗi scheduler, ta lập lịch **hai tác vụ một lần** vào thời điểm gần `Instant.now()`:

1. Tác vụ **slow** chạy trước rồi đợi `CountDownLatch` để giữ worker.
2. Khi slow đã bắt đầu, lập lịch tác vụ **fast** chỉ đếm xuống một latch.
3. Kiểm tra liệu fast có chạy **khi slow chưa được giải phóng** hay không.
4. Giải phóng slow, đợi cả hai Future hoàn tất, kiểm tra kết quả.

Không dùng `Thread.sleep()` hay suy đoán thứ tự thread. Test đặt điều kiện thời gian đủ lớn để quan sát việc fast có thể bắt đầu trước release trên máy bình thường, và có assertions để phát hiện nếu môi trường vận hành không đủ tài nguyên.

### 6.1. Cấu trúc dự án

```text
scheduler-pool-demo/
├── pom.xml
└── src/main/java/demo/SchedulerPoolDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>scheduler-pool-demo</artifactId>
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
          <mainClass>demo.SchedulerPoolDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

**Tệp `src/main/java/demo/SchedulerPoolDemo.java`:**

```java
package demo;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SchedulerPoolDemo {
    @Configuration(proxyBeanMethods = false)
    public static class AppConfig {
        @Bean("singleScheduler")
        public ThreadPoolTaskScheduler singleScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setThreadNamePrefix("single-");
            return scheduler;  // setPoolSize() absent: default is 1.
        }

        @Bean("parallelScheduler")
        public ThreadPoolTaskScheduler parallelScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("parallel-");
            return scheduler;
        }
    }

    private static boolean fastStartedWhileSlowBlocked(
            ThreadPoolTaskScheduler scheduler) throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        CountDownLatch fastStarted = new CountDownLatch(1);

        ScheduledFuture<?> slow = scheduler.schedule(() -> {
            slowStarted.countDown();
            try {
                if (!releaseSlow.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Slow task not released");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Slow task interrupted", ex);
            }
        }, Instant.now());

        try {
            if (!slowStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Slow task never started");
            }
            ScheduledFuture<?> fast =
                    scheduler.schedule(fastStarted::countDown, Instant.now());

            boolean fastRanBeforeRelease =
                    fastStarted.await(300, TimeUnit.MILLISECONDS);
            releaseSlow.countDown();

            if (!fastStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Fast task never started");
            }

            slow.get(5, TimeUnit.SECONDS);
            fast.get(5, TimeUnit.SECONDS);
            return fastRanBeforeRelease;
        } finally {
            releaseSlow.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            ThreadPoolTaskScheduler one =
                    context.getBean("singleScheduler", ThreadPoolTaskScheduler.class);
            ThreadPoolTaskScheduler two =
                    context.getBean("parallelScheduler", ThreadPoolTaskScheduler.class);

            int oneConfiguredCore =
                    one.getScheduledThreadPoolExecutor().getCorePoolSize();
            int twoConfiguredCore =
                    two.getScheduledThreadPoolExecutor().getCorePoolSize();

            boolean oneFast =
                    fastStartedWhileSlowBlocked(one);
            boolean twoFast =
                    fastStartedWhileSlowBlocked(two);

            if (oneConfiguredCore != 1 || twoConfiguredCore != 2 ||
                    oneFast || !twoFast) {
                throw new AssertionError("Unexpected scheduler concurrency");
            }

            System.out.println("defaultCoreSize=" + oneConfiguredCore);
            System.out.println("configuredCoreSize=" + twoConfiguredCore);
            System.out.println("fastBeforeReleasePool1=" + oneFast);
            System.out.println("fastBeforeReleasePool2=" + twoFast);
            System.out.println("bothFastJobsCompleted=true");
        }
    }
}
```

### 6.2. Lệnh chạy và output kiểm chứng

Cần Maven và JDK 21 trở lên. Từ thư mục `scheduler-pool-demo`:

```bash
mvn -q -DskipTests compile exec:java
```

**Stdout đã biên dịch/chạy với Spring Framework 6.2.1:**

```text
defaultCoreSize=1
configuredCoreSize=2
fastBeforeReleasePool1=false
fastBeforeReleasePool2=true
bothFastJobsCompleted=true
```

Diễn giải:

- `defaultCoreSize=1`: bean không setPoolSize có core pool size một thread.
- `configuredCoreSize=2`: bean được cấu hình rõ pool hai workers.
- `fastBeforeReleasePool1=false`: khi slow đang giữ scheduler một thread, fast chưa có worker rảnh để bắt đầu.
- `fastBeforeReleasePool2=true`: scheduler hai thread có worker khác để fast bắt đầu trong lúc slow vẫn chờ.
- `bothFastJobsCompleted=true`: cuối mỗi lượt, cả slow và fast đã thực thi xong; việc fast bị trễ trong pool1 không có nghĩa task fast đã bị bỏ qua hoàn toàn.

Ví dụ dùng **one-shot scheduling** qua `TaskScheduler.schedule(Runnable, Instant)` để cô lập vấn đề pool concurrency, không dùng `@Scheduled`; ý tưởng tranh chấp scheduler threads cũng áp dụng khi các job được `@Scheduled` đăng ký vào cùng `ThreadPoolTaskScheduler`.

Phép thử dùng một cửa sổ 300 ms để quan sát. Một môi trường cực kỳ quá tải hoặc bị pause lâu có thể làm test false negative cho pool2; vì vậy trong production nên đo scheduling delay bằng metrics qua nhiều mẫu, không dùng một boolean như SLO thực tế.

## 7. Cấu hình rõ scheduler trong Java @Configuration

Một ứng dụng dùng `@Scheduled` thực tế thường khai báo:

```java
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("app-schedule-");
        scheduler.setErrorHandler(ex ->
                System.err.println("Scheduled job failed: " + ex));
        return scheduler;
    }
}
```

Đây là **fragment** cần import `Configuration`, `EnableScheduling`, `Bean` và `ThreadPoolTaskScheduler`. `setErrorHandler` trong ví dụ chỉ minh họa điểm xử lý lỗi; production cần logging/alert đúng chỗ, tránh coi `System.err` là hệ thống quan sát đầy đủ.

Vì bean có tên `taskScheduler` và type `TaskScheduler`, Spring có một scheduler rõ ràng để sử dụng cho các `@Scheduled` method trong context phù hợp. Dù vậy, nếu dự án có nhiều schedulers hoặc có custom scheduling registrar, phải xác nhận đúng bean được chọn; đừng cho rằng bean vừa khai báo sẽ tự động thay thế mọi scheduler trong mọi context con.

### 7.1. Dùng XML namespace

Nếu ứng dụng giữ cấu hình Spring XML:

```xml
<task:scheduler id="taskScheduler" pool-size="4"/>
<task:annotation-driven scheduler="taskScheduler"/>
```

Đây là **fragment**, cần khai báo namespace `task`, schema và bean definitions phù hợp. `<task:scheduler>` tạo tài nguyên; `<task:annotation-driven>` kích hoạt xử lý `@Scheduled`/các annotation tương ứng khi được cấu hình. Chỉ tạo scheduler bean không tự có nghĩa mọi method annotated được scan và đăng ký.

### 7.2. Chọn scheduler riêng cho từng nhóm việc

Spring Framework 6.1+ cho phép dùng thuộc tính `scheduler` trên `@Scheduled` để chọn bean bằng tên/qualifier:

```java
@Scheduled(fixedDelay = 1_000, scheduler = "quickScheduler")
public void heartbeat() {
    sendHeartbeat();
}

@Scheduled(cron = "0 0 * * * *", scheduler = "batchScheduler")
public void hourlyBatch() {
    performLongBatch();
}
```

Đây là **fragment** trong một Spring-managed class, cần cấu hình hai bean `quickScheduler` và `batchScheduler`, bật `@EnableScheduling` và các method nghiệp vụ có thật. Mỗi nhóm sẽ có scheduler pool và metric riêng. Đặc biệt hữu ích khi job I/O dài có khả năng ảnh hưởng heartbeat hoặc lịch nhỏ cần chạy đều.

Ví dụ định nghĩa bean cho hai nhóm:

```java
@Bean("quickScheduler")
public ThreadPoolTaskScheduler quickScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("quick-");
    return scheduler;
}

@Bean("batchScheduler")
public ThreadPoolTaskScheduler batchScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("batch-");
    return scheduler;
}
```

Đặt fragments trong `@Configuration`, imports phù hợp. Điều quan trọng không phải con số 2 mà là **cách chia tài nguyên theo loại công việc/SLO**, tránh một hàng đợi chung khiến batch dài chặn job quan trọng.

## 8. Scheduler poolSize lớn hơn không giải quyết mọi vấn đề

Nếu job chạy lâu vì chờ database hoặc external API, tăng pool từ 1 lên 4 có thể giúp các job khác bắt đầu sớm hơn. Nhưng nếu tăng concurrency vượt khả năng downstream:

- Bốn job có thể cùng giữ DB connections, gây nghẽn pool JDBC.
- Retry trong nhiều job có thể tạo một đợt bùng nổ lưu lượng.
- Tất cả scheduler threads có thể bị block, dẫn tới delay mới.
- Scheduled tasks có side effect đồng thời có thể ghi đè/chồng dữ liệu.
- Nếu cùng một lịch xuất hiện trên nhiều application instances, tổng concurrency thực tế tăng theo số instance.

Vì vậy quy trình thiết kế là: **đo → xác định bottleneck → điều chỉnh isolation/concurrency → kiểm tra lại SLO và safety**. Không mặc định “pool càng lớn càng nhanh”.

### 8.1. Tính sơ bộ số workers

Một phép ước lượng **chỉ mang tính hoạch định**:

Giả sử có nhiều **jobs độc lập** đến hạn trong mỗi khoảng thời gian, mỗi job chiếm worker trung bình `S` giây và tần suất trung bình `λ` jobs/giây. Tải sử dụng worker trung bình xấp xỉ:

```text
requiredBusyWorkers ≈ λ × S
targetWorkers        > requiredBusyWorkers (có headroom cho burst)
```

Ví dụ nếu 2 scheduled executions độc lập mỗi giây, mỗi execution giữ worker trung bình 1,5 giây, trung bình có khoảng 3 workers bận. Pool 3 workers có thể là mức quá sát ngưỡng; thực tế cần headroom để xử lý sự biến động và tránh queue delay lớn.

Công thức không thay thế phân tích peak concurrency, cron synchronization, thời gian p99, locking và giới hạn DB. Không áp trực tiếp cho một periodic schedule duy nhất với các lần chạy không overlap; đó là tính tải **tổng cộng của nhiều scheduled jobs**.

### 8.2. CPU-bound và I/O-bound

- CPU-bound jobs: vượt quá số core hiệu dụng thường chỉ tăng context switching và contention.
- I/O-bound jobs: tăng workers có thể có ích cho các job độc lập, nhưng phải tôn trọng DB/HTTP connection pool, quota và timeout.
- Critical low-latency jobs: có thể cần scheduler riêng thay vì “đấu” với batch dài trong cùng pool.
- Long-running external calls: đặt timeout ở downstream, không để một network call treo vô hạn và giữ scheduler worker.

## 9. Tránh để scheduler thực thi công việc nặng trực tiếp khi cần điều phối nhẹ

Một thiết kế là dùng scheduler chỉ để **quyết định thời điểm**, còn phần thực thi nặng do `TaskExecutor` khác xử lý.

```text
TaskScheduler ("clock / trigger")
       |
       v
short scheduling callback
       |
       +-- bounded submission --> TaskExecutor ("workers")
                                   |
                                   +-- long-running I/O task
```

Ví dụ fragment:

```java
@Component
public class BatchTrigger {
    private final AsyncTaskExecutor batchExecutor;

    public BatchTrigger(@Qualifier("batchExecutor") AsyncTaskExecutor batchExecutor) {
        this.batchExecutor = batchExecutor;
    }

    @Scheduled(fixedDelay = 5_000, scheduler = "quickScheduler")
    public void trigger() {
        batchExecutor.execute(this::runBatchSafely);
    }

    private void runBatchSafely() {
        // Công việc nặng được chạy trên executor riêng.
    }
}
```

Đây là **fragment**, yêu cầu `@EnableScheduling`, bean `quickScheduler`, bean `batchExecutor` và các imports `@Component`, `@Scheduled`, `@Qualifier`, `AsyncTaskExecutor`.

Lợi ích: scheduling callback không phải chờ toàn bộ batch; scheduler thread nhanh chóng rảnh cho các trigger khác.

**Rủi ro rất quan trọng:** khi callback trả về ngay sau submit, `fixedDelay` đo từ lúc **callback kết thúc**, không phải từ lúc batch thực sự hoàn tất. Nếu batch mất 30 giây và trigger mỗi 5 giây, ứng dụng có thể submit nhiều batch chồng lấn. Phải thêm **admission control**: `Semaphore`, job status/lease, bounded queue, rejection policy, idempotency, deduplication hoặc distributed lock (nếu nhiều instance). Không biến một scheduler nghẽn thành một executor queue vô hạn.

### 9.1. @Async trên @Scheduled có phù hợp?

Gắn `@Async` để chuyển phần chạy sang executor khác cũng có thể giảm thời gian chiếm scheduler thread khi được cấu hình và intercept đúng. Tuy nhiên, khi scheduling callback trả về sớm, nhịp kích hoạt có thể tạo **nhiều execution đang chạy cùng lúc**. Điều này không phủ định bảo đảm no-overlap của một periodic task ở scheduler-level: task được scheduler quan sát ở đây chỉ là **callback gửi việc**, không phải toàn bộ công việc nền.

Vì thế luôn hỏi: **“Tôi đang giới hạn sự chồng lấn của callback hay của job nghiệp vụ thực?”** Đây là hai ranh giới khác nhau.

## 10. Lỗi và đời sống của ScheduledFuture

Khi dùng `TaskScheduler.schedule(...)`, caller nhận một `ScheduledFuture<?>` cho task tương ứng.

Với một one-shot scheduled task:

- `get()` có thể trả sau khi task hoàn tất, hoặc ném exception/cancellation nếu task lỗi/bị hủy.
- `cancel(...)` là yêu cầu hủy theo semantics của Future/implementation, không đảm bảo rollback side effect.
- Nếu scheduler shutdown hoặc không nhận task, lời gọi schedule có thể bị từ chối.

Với **periodic task**:

- `ScheduledFuture.get()` thường **không trở về bình thường** trong lúc chuỗi thực thi định kỳ còn hoạt động; nó chỉ kết thúc qua cancellation/abnormal completion.
- Không nên gọi `get()` vô hạn trên scheduled periodic future chỉ để “đợi một lượt chạy” — caller có thể bị chặn mãi.
- Một exception không được bắt bên trong Runnable định kỳ của JDK `ScheduledThreadPoolExecutor` có thể khiến các lượt tiếp theo bị dừng theo hợp đồng JDK; Spring có thể áp dụng error handler cho scheduled task để ghi log/xử lý khác. Phải kiểm tra implementation và error handling thực tế của ứng dụng.
- `cancel(true)` chỉ thử interrupt nếu implementation hỗ trợ và task phối hợp; không dùng làm cơ chế dừng cưỡng bức chắc chắn.

**Lưu ý cleanup:** ScheduledThreadPoolExecutor có delayed queue. Những delayed tasks bị cancel có thể còn nằm trong queue theo chính sách mặc định cho đến khi đến hạn; cần hiểu `removeOnCancelPolicy` khi lịch nhiều hoặc thay đổi thường xuyên. Đó là câu chuyện queue retention, **không phải** cách tăng số workers.

## 11. Quan sát độ trễ lịch và thời gian chạy

Tăng poolSize nên dựa vào dữ liệu đo được. Tối thiểu cần:

1. **Scheduled fire time** hoặc “lúc đáng lẽ task tới hạn”.
2. **Actual start time** ở worker.
3. **Finish time** của business task.
4. **Scheduler queue delay** = actual start − expected due time, có thể cần mốc do scheduler/trigger cung cấp.
5. **Task runtime** = finish − start.
6. **Active threads/pool size**, backlog, số job bị từ chối/cancel.
7. **Business outcome** và độ trễ hoàn tất end-to-end.

Ví dụ khi chủ động gọi `schedule(task, Instant)`, bạn biết thời điểm yêu cầu nên chạy và có thể lưu nó để ghi metrics khi task bắt đầu:

```java
Instant requestedStart = Instant.now().plusSeconds(10);
scheduler.schedule(() -> {
    Instant actualStart = Instant.now();
    Duration lateness = Duration.between(requestedStart, actualStart);
    recordScheduleLateness(lateness);
    executeBusinessJob();
}, requestedStart);
```

Đây là fragment; cần `scheduler`, imports `Instant`/`Duration`, hai hàm thống kê/nghiệp vụ. Đối với đo duration bên trong một process, `System.nanoTime()` thích hợp hơn để tránh việc wall clock bị điều chỉnh. Đối với cron, expected fire time cần được xác định theo đúng trigger/timezone; **không nên lấy “giờ trong cron expression” rồi tính trừ tùy tiện khi cron có policy skip/missed firing**.

### 11.1. Đừng chỉ nhìn queue size

Bản thân delayed queue có thể chứa các future executions còn lâu mới đến hạn; queue size lớn không đồng nghĩa tất cả là task đã quá hạn đang chờ worker. Cần phân biệt **chưa đến hạn** với **đã đến hạn nhưng chưa được bắt đầu**.

Ngược lại, queue size nhỏ không chứng minh scheduler đúng giờ; một long-running job vẫn có thể chặn mọi task khác nếu poolSize=1.

### 11.2. Log thread name không thay cho metrics

Gắn `threadNamePrefix` giúp chẩn đoán và đọc thread dump, nhưng không dùng tên thread làm logic nghiệp vụ. Sử dụng trace, task ID, start/end timestamps, metrics và audit trạng thái công việc.

## 12. Lựa chọn poolSize và chiến lược theo tình huống

| Tình huống | Hướng tiếp cận | Lưu ý |
| --- | --- | --- |
| Một job nhẹ, hiếm chạy, không có SLO chặt | poolSize=1 có thể đủ | Đo runtime và missed schedule |
| Nhiều jobs độc lập, một job đôi lúc chậm | Tăng poolSize có giới hạn | Tránh một job chặn tất cả |
| Heartbeat/job quan trọng cần đúng giờ | Scheduler riêng cho critical jobs | Theo dõi scheduler latency |
| Batch I/O lâu, dễ chồng lấn | Scheduler nhỏ + bounded TaskExecutor riêng | Phải có cơ chế chống overlap/backpressure |
| CPU-bound jobs dày đặc | Pool theo CPU budget và workload thực | Tránh oversubscription |
| Cron nhiều instance | Leader election/distributed lock/job store | poolSize chỉ giới hạn **mỗi instance** |
| Cần lịch bền vững sau restart/missed trigger | Job store/broker/Quartz tùy yêu cầu | @Scheduled in-memory không phải durable scheduling |
| Spring Boot virtual threads | Kiểm tra SimpleAsyncTaskScheduler thực tế | Pool properties có thể không áp dụng |

**Không có “poolSize tốt nhất cho mọi ứng dụng”.** Con số phải dựa vào số jobs đồng thời, runtime distribution, độ quan trọng của lịch, giới hạn tài nguyên và yêu cầu xử lý sau sự cố.

## 13. Spring Boot cấu hình gì và vì sao property có thể không tác dụng?

Trong ứng dụng Spring Boot, property thường dùng khi Boot auto-configured `ThreadPoolTaskScheduler` với platform threads:

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 4
      thread-name-prefix: "scheduling-"
```

Nhưng nếu ứng dụng có bean `TaskScheduler` tùy chỉnh, cấu hình Boot có thể back off hoặc không áp property này lên bean bạn tự tạo. Cần kiểm tra actual bean type và `getScheduledThreadPoolExecutor().getCorePoolSize()` ở trường hợp `ThreadPoolTaskScheduler`.

Khi Boot hỗ trợ và đã bật virtual threads, auto-configured scheduler có thể thành `SimpleAsyncTaskScheduler`, **không sử dụng pool.size theo cùng cách**. Spring Framework 6.1+ có scheduler này được thiết kế gần với JDK 21 virtual threads: nó dùng một scheduler thread để kích hoạt task và tạo thread thực thi riêng cho nhiều loại trigger, nhưng **fixed-delay tasks đều hoạt động trên scheduler thread** theo mô tả chính thức, nên blocking fixed-delay vẫn cần được đánh giá.

Đừng đọc một property rồi tuyên bố scheduler thật đã có bốn workers. Hãy kiểm tra implementation trong context và concurrency behavior bằng integration test.

## 14. Một job chậm, ba khả năng lỗi khác nhau

### 14.1. Scheduler starvation

- Job A chiếm tất cả scheduler threads.
- Job B đã đến hạn nhưng chưa được worker nhận.
- Chỉ số “schedule lateness” cao, dù body B nhanh.

Giải pháp: poolSize hợp lý, isolation giữa jobs, tránh blocking lâu trực tiếp trong scheduler.

### 14.2. Business execution chậm

- Job B bắt đầu đúng giờ.
- Body B chậm vì query/HTTP/lock.
- Runtime của B cao, scheduler latency có thể vẫn thấp ở giai đoạn đầu.

Giải pháp: tối ưu logic/downstream, timeout, batching, connection pool; chỉ tăng scheduler pool không chữa runtime của B.

### 14.3. Trigger/misfire semantics

- Job đã bỏ lỡ mốc cron khi app downtime hoặc khi lịch quá dày.
- Sau restart không thấy tất cả lần đã lỡ chạy bù.
- Không nhất thiết là scheduler worker bị thiếu; có thể do cơ chế trigger, in-memory registration và policy missed execution.

Giải pháp: xác định semantics chính xác cho cron, durable scheduling, missed-run reconciliation và leader election nếu nghiệp vụ đòi hỏi.

**Đừng đánh đồng ba nhóm lỗi.** Cả ba có thể làm người dùng thấy “job không chạy đúng giờ”, nhưng nguyên nhân và phương án sửa hoàn toàn khác nhau.

## 15. Concurrency, transaction, retry và nhiều instance

Tăng scheduler poolSize cho phép các jobs độc lập chạy gần nhau hơn; vì vậy phải kiểm tra race conditions:

- Hai jobs có thể truy cập cùng một bảng DB hoặc file đồng thời.
- Transaction được gắn với thread thực thi; scheduler workers khác nhau không tự dùng chung một transaction của caller.
- Lặp lại job sau timeout có thể nhân đôi side effect nếu không có idempotency.
- Một job đã bắt đầu có thể tiếp tục chạy dù endpoint đã trả timeout.
- Nếu ứng dụng chạy trên ba pods, mỗi pod có scheduler poolSize=4 thì **không có một giới hạn toàn cụm là 4**. Mỗi node có thể kích hoạt cùng scheduled method. Muốn duy nhất một node chạy, dùng coordination/distributed lock, leader election hoặc scheduler có job store phù hợp.
- Khi scheduling callback submit vào executor khác, số business tasks in-flight có thể lớn hơn poolSize của scheduler rất nhiều; phải giới hạn cả queue và tốc độ nhận việc.

**Độ tin cậy:** Spring `@Scheduled` thông thường không tự cung cấp durable job history, replay missed runs hoặc exactly-once trong cluster. Hệ thống giao dịch/đối soát cần job store/outbox và kiểm soát idempotency tương ứng.

## 16. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Nghĩ mặc định ThreadPoolTaskScheduler có nhiều thread | Job dài chặn job khác | Xác nhận default 1, cấu hình setPoolSize |
| Tăng `ThreadPoolTaskExecutor.maxPoolSize` để sửa scheduler | Không đổi scheduled executor pool | Chỉnh chính scheduler bean |
| Chỉ khai báo `@EnableScheduling` rồi nghĩ đã có pool lớn | Fallback có thể là một thread | Cấu hình TaskScheduler rõ ràng |
| Tăng poolSize=10 và nghĩ một fixedRate registration có 10 lượt overlap | JDK periodic registration không overlap | Phân biệt một registration với nhiều jobs |
| Coi fixedDelay tương đương fixedRate | Dự đoán sai mốc chạy | Hiểu delay sau finish vs nhịp fixed-rate |
| Nghĩ cron sẽ chạy bù tất cả missed fires | Sai trigger/misfire semantics | Kiểm tra trigger chính xác và durable scheduler |
| Đưa batch dài và heartbeat vào cùng pool 1 thread | Heartbeat trễ | Tách scheduler/đặt concurrency phù hợp |
| Tách batch sang TaskExecutor nhưng không giới hạn submission | Chồng lấn và queue phát triển | Bounded executor, semaphore, job state |
| Thấy CPU thấp thì tăng pool vô hạn | DB/HTTP có thể đã nghẽn | Đo downstream và giới hạn concurrency |
| Chỉ nhìn queue size để đo lịch bị trễ | Delayed queue có future tasks chưa đến hạn | Đo due-time-to-start latency |
| Dùng `get()` vô hạn trên periodic ScheduledFuture | Caller có thể chờ mãi | Dùng metrics/latch cho từng lượt, cancel khi cần |
| Coi Boot property chắc chắn điều khiển custom bean | Property có thể không được áp | Kiểm tra auto-config và bean type |
| Bật virtual threads rồi chỉnh pool.size để tăng threads | SimpleAsyncTaskScheduler bỏ qua pool property | Cấu hình theo đúng implementation |
| Coi scheduler poolSize là giới hạn toàn cluster | Mỗi instance có scheduler riêng | Distributed lock/leader election |
| Tăng concurrency mà quên idempotency/transaction | Race, duplicate side effect | Kiểm thử concurrent jobs và rollback |
| Dùng Thread.sleep() để test overlap | Test dễ flake theo timing | Dùng CountDownLatch, bounded await, assertions |

## 17. Bài tập tự kiểm tra

**Câu 1.** Tạo `new ThreadPoolTaskScheduler()` rồi đăng ký bean mà không gọi `setPoolSize`. Pool size theo Spring Framework 6.2 là bao nhiêu?

Một thread. Đó là mặc định của implementation `ThreadPoolTaskScheduler`, không phải quy tắc áp dụng cho mọi scheduler khác.

**Câu 2.** Một scheduler có poolSize=1. Job A bắt đầu lúc 10:00 và giữ worker 7 phút. Job B đến hạn lúc 10:01. B có chắc bắt đầu đúng 10:01 không?

Không. Nếu B dùng cùng scheduler và cần scheduler worker, nó phải đợi đến khi worker rảnh; nếu B là một trigger cần reschedule, behavior trễ/missed execution còn phụ thuộc loại trigger.

**Câu 3.** Với `fixedDelay=3000ms`, job mất 7000ms. Khoảng start-to-start tối thiểu theo điều kiện lý tưởng là bao nhiêu?

Khoảng 10.000ms: chạy 7 giây rồi đợi thêm 3 giây sau completion. Scheduler contention có thể khiến khoảng thực tế dài hơn.

**Câu 4.** Với một periodic `scheduleAtFixedRate` registration trên `ScheduledThreadPoolExecutor`, tăng poolSize=8 có tự làm các lượt của chính registration đó overlap không?

Không. JDK bảo đảm các lần thực thi liên tiếp của cùng periodic task không overlap; chúng có thể đến muộn khi task chạy lâu hơn period.

**Câu 5.** Nếu tách @Scheduled callback sang một TaskExecutor thì pool scheduler nhỏ có chắc an toàn chưa?

Chưa. Callback có thể submit nhiều công việc trùng nhau trước khi batch cũ xong; phải thêm bounded capacity, backpressure và ngăn overlap nếu nghiệp vụ yêu cầu.

**Câu 6.** Vì sao Java+Spring test trong bài dùng latch thay vì `Thread.sleep(100)`?

Vì latch xác nhận slow đã chiếm worker và kiểm soát lúc nó được giải phóng. Thử nghiệm phân biệt cấu hình pool một và hai worker theo trạng thái có kiểm soát, không trông đợi timing may rủi.

**Câu 7.** Một ứng dụng có ba instances, mỗi instance cấu hình scheduler poolSize=4. Có nghĩa toàn hệ thống chỉ có bốn scheduled jobs cùng chạy?

Không. poolSize điều khiển scheduler trong **một instance**. Nếu cả ba cùng kích hoạt, số executions đồng thời toàn cụm có thể cao hơn; muốn global limit cần coordination riêng.

## 18. Checklist trước khi đưa lịch vào production

- Đã kiểm tra **implementation thực tế** là `ThreadPoolTaskScheduler`, `SimpleAsyncTaskScheduler` hay scheduler khác?
- Đã biết poolSize mặc định hoặc configured thực tế, thay vì chỉ đoán từ property?
- Những jobs nào chia sẻ scheduler, jobs nào cần scheduler/worker riêng?
- Runtime trung bình/p95/p99 của mỗi job là bao nhiêu? Có I/O/lock/DB wait không?
- Có đo schedule due-time-to-start latency và business completion latency riêng?
- FixedDelay/fixedRate/cron được chọn đúng semantics chưa?
- Khi job dài hơn period, chính sách backlog, skip, overlap hoặc dedup đã rõ chưa?
- Nếu callback submit vào executor khác, có bounded queue và admission control không?
- Có timeout ở HTTP/JDBC downstream để scheduler worker không bị treo?
- Có thể tăng scheduler pool mà không làm cạn DB/HTTP connection pool không?
- Có log/metrics cho exceptions, cancels, rejections, shutdown và job outcome?
- Nếu triển khai nhiều nodes, có cần lock/leader election/job store để tránh chạy trùng?
- Khi ứng dụng restart, có yêu cầu replay missed runs hoặc durable job history không?
- Đã có integration test dùng latch kiểm tra contention thay vì chỉ test một method độc lập chưa?

## 19. Kết luận

Với Spring Framework 6.2, **`ThreadPoolTaskScheduler` mặc định chỉ có poolSize=1**. Vì scheduled task thực thi trên chính scheduler thread, một công việc chạy lâu hoặc block I/O có thể khiến **những công việc khác dùng chung scheduler** bắt đầu trễ. Tăng `setPoolSize(...)` cho phép nhiều jobs độc lập chạy đồng thời hơn, nhưng không làm chính một periodic registration của `ScheduledThreadPoolExecutor` tự overlap và không tự khắc phục bottleneck downstream.

Hãy xác minh implementation/default thực tế, phân biệt fixedDelay/fixedRate/cron, đo độ trễ đến lúc worker bắt đầu, tách critical jobs khỏi batch dài khi cần, và kiểm soát duplicate/overlap khi chuyển việc sang executor khác. Với production nhiều instance hoặc job cần replay, poolSize chỉ là một mảnh của bài toán: reliability và coordination phải được thiết kế riêng.

### Tài liệu tham khảo

- [Spring Framework 6.2.1 — ThreadPoolTaskScheduler API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2 — EnableScheduling API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html)
- [Spring Framework 6.2 — Scheduled API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Java SE 21 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html)
- [Spring Boot — Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduler-selection-003">Công việc thông thường chạy trên luồng của ThreadPoolTaskScheduler</a>

<details>
<summary>Xem chi tiết</summary>

# Công việc thông thường chạy trên luồng của `ThreadPoolTaskScheduler`

## Mục tiêu

Một hiểu nhầm phổ biến khi dùng Spring scheduling là: **scheduler chỉ canh giờ, sau đó tự động chuyển tác vụ sang một thread pool xử lý khác**. Điều đó **không đúng với cách vận hành thông thường của `ThreadPoolTaskScheduler`**. Theo hợp đồng lớp này trong Spring Framework, nó bọc `ScheduledThreadPoolExecutor`, và **thân `Runnable` được thực thi trên chính các scheduler thread**. Vì vậy một tác vụ đồng bộ lâu hoặc bị blocking có thể chiếm scheduler worker và làm các công việc đến hạn khác phải chờ.

Bài Knowledge thuộc mục **Chọn scheduler** này làm rõ mô hình execution, hệ quả với `@Scheduled`, kích thước pool, `ScheduledFuture`, các kiểu lịch, cách chọn thời điểm tách công việc nặng sang `TaskExecutor`, và ví dụ Java 17+ với Spring Framework để quan sát điều đó một cách có kiểm soát.

Sau bài học, bạn có thể:

1. Giải thích quan hệ `TaskScheduler` → `ThreadPoolTaskScheduler` → `ScheduledThreadPoolExecutor` → scheduler worker.
2. Phân biệt **sắp lịch** và **thực thi** trong cùng pool với việc **chuyển giao** sang pool khác.
3. Giải thích vì sao `poolSize = 1` có thể gây nghẽn khi một scheduled task chạy lâu, và `poolSize` lớn hơn thay đổi điều gì.
4. Hiểu `schedule(Runnable, Instant)`, `scheduleAtFixedRate`, `scheduleWithFixedDelay`, `CronTrigger` và `@Scheduled`.
5. Dùng `ScheduledFuture<?>` đúng cách cho tác vụ một lần, tránh gọi `get()` sai trên công việc lặp mãi.
6. Chạy ví dụ Spring 6.2.12 xác nhận hai task dùng **cùng một scheduler thread** khi pool chỉ có một worker.
7. Thiết kế offload có chủ đích, xử lý lỗi/overload, cleanup, lifecycle, quan sát và tránh các nhầm lẫn về thread context.

**Kết luận cốt lõi:** Với `ThreadPoolTaskScheduler`, một tác vụ scheduled thông thường **không có bước offload mặc định**. Nếu nghiệp vụ cần scheduler chỉ kích hoạt rồi nhanh chóng trả worker, ứng dụng phải **chủ động** gửi phần xử lý sang executor khác hoặc chọn mô hình scheduler có đặc tính khác phù hợp.

## 1. `TaskScheduler` là gì?

`org.springframework.scheduling.TaskScheduler` là abstraction của Spring để lên lịch thực hiện `Runnable` tại một thời điểm hoặc theo chu kỳ. Nó tách **cách mô tả lịch** khỏi implementation scheduler cụ thể.

Một số API tiêu biểu:

```java
ScheduledFuture<?> schedule(Runnable task, Instant startTime);
ScheduledFuture<?> schedule(Runnable task, Trigger trigger);
ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period);
ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay);
```

Đây là **chữ ký minh họa từ `TaskScheduler`**, không phải chương trình Java độc lập. Trong chương trình thật, bạn nhận một bean `TaskScheduler`, truyền `Runnable` và thời điểm/chu kỳ tương ứng. Một lần `schedule` thông thường trả `ScheduledFuture<?>` để theo dõi hoặc yêu cầu hủy.

Hai câu hỏi không được nhập làm một:

- **Khi nào chạy?** Quyết định bởi `Instant`, fixed rate, fixed delay hoặc trigger như cron.
- **Chạy ở đâu?** Quyết định bởi scheduler implementation, executor dưới nó và mọi bước delegation mà code tự tạo.

`ThreadPoolTaskScheduler` là một implementation cụ thể. Nó dựa trên `ScheduledThreadPoolExecutor` của JDK, có cấu hình thread name, pool size, lifecycle và một số tùy chọn tiếp nhận/hủy task.

## 2. Cơ chế chính xác của `ThreadPoolTaskScheduler`

Mô hình mặc định:

```text
Caller / @Scheduled infrastructure
                 |
                 v
      ThreadPoolTaskScheduler
                 |
        tính thời điểm chạy
                 |
                 v
      ScheduledThreadPoolExecutor
                 |
        queue các lần chạy
                 |
          +------+------+
          |             |
    scheduler-1     scheduler-2    (nếu poolSize = 2)
          |             |
       task.run()     task.run()
          |             |
      hoàn tất lần chạy, cập nhật lịch nếu là task lặp
```

**Điểm then chốt:** `task.run()` xảy ra **ngay trên một scheduler worker**. Trong đường thực thi thông thường, nó **không tự tạo `ThreadPoolTaskExecutor` riêng** rồi chuyển phần xử lý sang pool đó.

Javadoc `ThreadPoolTaskScheduler` ghi rõ hai đặc điểm:

- Số scheduler thread mặc định là **1**; có thể tăng bằng `setPoolSize(int)`.
- Task execution diễn ra trên **scheduler threads**, không trên một nhóm execution thread riêng. `ScheduledFuture` từ lịch một lần phản ánh sự hoàn thành của chính Runnable đã được cung cấp.

Nếu `poolSize=1`, task A đang chạy sẽ giữ scheduler worker. Task B đến hạn trong lúc A chưa kết thúc vẫn phải đợi một scheduler worker có thể thực thi. B không được cứu chỉ vì B có lịch đến hạn sớm hay ưu tiên nghiệp vụ cao hơn.

```text
poolSize = 1

time ------->

worker-1: [ task A blocking ........................ ][task B][task C]
queue   :             B due, C due -> đợi A kết thúc
```

Lịch xác định thời điểm **đủ điều kiện** chạy; nó không phải cam kết **bắt đầu đúng mili giây** khi toàn bộ worker đang bận.

### Thread pool của scheduler và thread pool của executor

`ThreadPoolTaskScheduler` và `ThreadPoolTaskExecutor` có tên gần nhau, nhưng điểm chính khác nhau:

| Thành phần | Vai trò chính | Bên dưới | Dùng khi |
| --- | --- | --- | --- |
| `ThreadPoolTaskScheduler` | Chạy task ở thời điểm/chu kỳ | `ScheduledThreadPoolExecutor` | `@Scheduled`, `TaskScheduler.schedule`, fixed rate/delay, cron. |
| `ThreadPoolTaskExecutor` | Chạy task được gửi vào một execution pool | `ThreadPoolExecutor` | `@Async`, task nghiệp vụ cần concurrency/queue/rejection riêng. |
| `ConcurrentTaskScheduler` | Adapter cho một `ScheduledExecutorService` có sẵn | Delegate do ứng dụng cung cấp | Tích hợp scheduler được tạo/quản lý từ nơi khác. |
| `SimpleAsyncTaskScheduler` | Mô hình scheduler khác, hướng tới Java 21 virtual threads | Cơ chế scheduling/dispatch riêng | Chọn khi thực sự cần mô hình per-task thread và đã đánh giá ngoại lệ fixed-delay. |
| `DefaultManagedTaskScheduler` | Dùng scheduler do môi trường Jakarta EE quản lý | Managed scheduled executor | Môi trường yêu cầu container sở hữu threads. |

**Không suy ra mọi implementation của `TaskScheduler` đều có cùng mô hình thread.** Bài này đang mô tả cụ thể `ThreadPoolTaskScheduler` của Spring Framework 6.2.x.

## 3. `poolSize` ảnh hưởng gì khi job blocking?

`ThreadPoolTaskScheduler` có một cấu hình quan trọng:

```java
ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setPoolSize(1);
scheduler.setThreadNamePrefix("scheduled-");
```

Đây là snippet khai báo scheduler, cần gắn với lifecycle khởi tạo/quản lý đúng (thường trả từ một phương thức `@Bean` của Spring). Ví dụ đầy đủ ở phần 6 sẽ dùng Spring container để tự khởi tạo và đóng scheduler.

Với `poolSize=1`, dù có nhiều task đến hạn, **tối đa một** scheduled task đang thực thi trong scheduler tại một thời điểm thông thường. Nếu task giữ worker để chờ API hoặc đợi lock, những job khác dùng chung scheduler sẽ bị trễ.

Với `poolSize=4`, có thể có đến khoảng bốn task khác nhau chạy song song trên bốn worker của cùng scheduler, theo scheduling policy. Nhưng:

- Không có nghĩa mỗi cron task luôn sở hữu một worker riêng.
- Không có nghĩa một task lặp với fixed rate được chạy chồng nhiều invocation của **chính cùng periodic registration**. `ScheduledThreadPoolExecutor` không chạy chồng hai lần kế tiếp của cùng một periodic task; lượt sau có thể bắt đầu trễ.
- Không có nghĩa tăng `poolSize` vô hạn sẽ cải thiện throughput. CPU, DB connection, HTTP client, lock và memory vẫn là tài nguyên chung.
- Không có nghĩa fixed delay/fixed rate sẽ giữ chính xác thời gian khi server bận, JVM pause hoặc executor bị shutdown.

`ThreadPoolTaskScheduler` dùng scheduled executor với delayed queue. **Đừng tự áp dụng** tư duy `corePoolSize`, `maxPoolSize`, `queueCapacity` của `ThreadPoolTaskExecutor` cho scheduler. Hai implementation có chính sách queue và thread growth khác nhau. Với scheduled executor, số core thread (`poolSize`) là núm điều khiển quan trọng để chạy các tác vụ đến hạn.

### Ví dụ một scheduler bị chiếm

Giả sử scheduler chỉ có một worker, dùng cho hai job:

- Job A chạy 20 giây vì một HTTP call blocking chưa có timeout tốt.
- Job B là việc ghi heartbeat, cần chạy mỗi 5 giây.

Nếu A bắt đầu trước và chiếm worker 20 giây, B có thể bị trì hoãn qua nhiều thời điểm đến hạn. **Không phải** lỗi của cron; scheduler có lịch nhưng thiếu worker rảnh để chạy code của B.

Tách scheduler riêng cho nhóm quan trọng, tăng pool size có ngân sách, hoặc chủ động offload job blocking là các phương án cần đánh giá. Nếu B phải đáp ứng một deadline cứng, chỉ chỉnh `poolSize` mà không có timeout/overload control ở hạ nguồn thường chưa đủ.

## 4. `ScheduledFuture` đại diện cho điều gì?

Với `ThreadPoolTaskScheduler.schedule(Runnable, Instant)` cho **một lần chạy**, `ScheduledFuture<?>` đại diện cho task thực sự chạy ở scheduler worker.

```java
ScheduledFuture<?> handle = scheduler.schedule(
    () -> doWork(),
    Instant.now().plusSeconds(2)
);

handle.get(10, TimeUnit.SECONDS);
```

Đây là snippet có `scheduler` và `doWork` được định nghĩa trước. Với một task chạy một lần và hoàn tất bình thường, `get` có thể trả `null` vì `Runnable` không có result. Nếu thân `Runnable` bị lỗi theo hợp đồng future, `get` có thể ném `ExecutionException`; nếu caller chờ quá thời hạn, nó ném `TimeoutException`.

**Khác biệt quan trọng với task lặp:** nếu dùng `scheduleAtFixedRate`, `scheduleWithFixedDelay` hoặc cron trigger chạy liên tục, `ScheduledFuture.get()` có thể chờ vô hạn **cho đến khi task lặp bị hủy hoặc kết thúc bất thường**. Không gọi `.get()` để “đợi một vòng cron hoàn tất” như với one-shot task. Muốn theo dõi từng lần chạy, hãy có cơ chế completion/result riêng cho từng invocation, ví dụ metric, callback, store trạng thái hoặc một `CompletableFuture` một lần.

`ScheduledFuture.cancel(...)` là yêu cầu hủy các lần chạy tương lai hoặc cố hủy lần đang chạy theo semantics implementation và khả năng interruption của task. Nó **không tự bảo đảm** JDBC query, socket I/O hay tác dụng phụ nghiệp vụ được rollback.

Nếu scheduled method chỉ **gửi** một task khác tới executor rồi return, future của scheduler phản ánh **việc gửi** đã hoàn tất, không nhất thiết phản ánh task được gửi đã xử lý xong. Hãy tách hai completion handles khi thiết kế kiểu offload.

## 5. `@Scheduled` có dùng `ThreadPoolTaskScheduler` không?

Spring cho phép gắn `@Scheduled` lên method và kích hoạt bằng `@EnableScheduling` trong application context phù hợp. Hạ tầng `ScheduledAnnotationBeanPostProcessor` tìm các method được đánh dấu để đăng ký lịch với scheduler đã chọn.

Ví dụ:

```java
@Configuration
@EnableScheduling
class SchedulingConfiguration {
    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }
}

@Component
class CleanupJob {
    @Scheduled(
        fixedDelay = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "taskScheduler"
    )
    public void cleanup() {
        // Nếu method này thực hiện blocking I/O,
        // scheduler worker vẫn bị chiếm cho tới khi method return.
    }
}
```

Đây là **cấu hình minh họa**, chưa phải chương trình độc lập: cần imports, component scanning/đăng ký bean và hạ tầng scheduling. Thuộc tính `scheduler` của `@Scheduled` cho phép chỉ rõ scheduler bean bằng qualifier hoặc bean name (được Spring hỗ trợ từ 6.1). Nếu chỉ có một scheduler rõ ràng, Spring cũng có các quy tắc chọn mặc định; **không nên dựa vào tên thread mặc định để suy đoán** bean thực sự được dùng khi có nhiều scheduler.

Một điểm quan trọng: **`@EnableScheduling` khác `@EnableAsync`**.

- `@EnableScheduling` kích hoạt việc tìm `@Scheduled` và đăng ký job theo lịch.
- `@EnableAsync` kích hoạt interception cho `@Async` ở Spring proxy mode.
- Thêm `@Scheduled` **không tự động** khiến thân method `cleanup()` chạy qua một `@Async` executor khác.
- Nếu cùng một method mang `@Scheduled` và `@Async`, cần xét ranh giới interception, executor, thứ tự xử lý và lỗi một cách tường minh; bài này khuyến nghị tách bean scheduling khỏi bean xử lý async để dễ kiểm soát.

Trong code chạy được ở phần 6, ta gọi `scheduler.schedule(...)` **trực tiếp bằng API** nên **không cần `@EnableScheduling`**. `@EnableScheduling` chỉ cần khi muốn Spring tự tìm và đăng ký method `@Scheduled` qua annotation.

## 6. Ví dụ Java 17+ chạy được: hai task tranh một scheduler worker

Ta kiểm chứng quan hệ **schedule và execute dùng cùng worker** bằng hai one-shot tasks. Cách kiểm tra không dựa vào `Thread.sleep` hoặc đo độ lệch thời gian bất định: dùng `CountDownLatch` giữ task A tại một điểm xác định, rồi gửi task B khi A đang chiếm worker của scheduler.

Với `poolSize=1`, B không thể chạy trước khi A được nhả. Sau đó cả hai hoàn tất; tên thread có tiền tố `scheduled-` và **cùng một worker** được dùng lại.

### Cấu trúc dự án

```text
scheduler-thread-demo/
├── pom.xml
└── src/main/java/example/SchedulerThreadDemo.java
```

Tạo tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>scheduler-thread-demo</artifactId>
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
                    <mainClass>example.SchedulerThreadDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo tệp `src/main/java/example/SchedulerThreadDemo.java`:

```java
package example;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SchedulerThreadDemo {

    public static class Signals {
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final AtomicReference<Thread> firstThread = new AtomicReference<>();
        final AtomicReference<Thread> secondThread = new AtomicReference<>();
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("scheduled-");
            return scheduler;
        }

        @Bean
        public Signals signals() {
            return new Signals();
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(Config.class)) {

            ThreadPoolTaskScheduler scheduler =
                context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
            Signals signals = context.getBean(Signals.class);
            Thread caller = Thread.currentThread();

            // Task A thực thi trên scheduler worker rồi chờ chốt.
            ScheduledFuture<?> first = scheduler.schedule(() -> {
                signals.firstThread.set(Thread.currentThread());
                signals.firstStarted.countDown();

                try {
                    signals.releaseFirst.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("First task interrupted", ex);
                }
            }, Instant.now());

            if (first == null) {
                throw new IllegalStateException("First task not scheduled");
            }

            try {
                if (!signals.firstStarted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("First task did not start");
                }

                // Task B cũng đến hạn ngay, nhưng pool chỉ có 1 worker.
                ScheduledFuture<?> second = scheduler.schedule(
                    () -> signals.secondThread.set(Thread.currentThread()),
                    Instant.now()
                );

                if (second == null) {
                    throw new IllegalStateException("Second task not scheduled");
                }

                // Trước khi nhả A, B không thể lấy worker để chạy.
                boolean secondWaitedBehindFirst =
                    signals.secondThread.get() == null && !second.isDone();
                boolean firstWasStillRunning = !first.isDone();

                signals.releaseFirst.countDown();

                // Chờ completion thật của cả hai Runnable.
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);

                Thread one = signals.firstThread.get();
                Thread two = signals.secondThread.get();

                System.out.println("callerAndSchedulerDifferent="
                    + (one != caller));
                System.out.println("firstOnSchedulerThread="
                    + one.getName().startsWith("scheduled-"));
                System.out.println("secondOnSchedulerThread="
                    + two.getName().startsWith("scheduled-"));
                System.out.println("sameSchedulerWorker=" + (one == two));
                System.out.println("secondWaitedBehindFirst="
                    + secondWaitedBehindFirst);
                System.out.println("firstPendingBeforeRelease="
                    + firstWasStillRunning);
                System.out.println("bothCompleted="
                    + (first.isDone() && second.isDone()));
            } finally {
                // Kể cả khi main bị lỗi, không để task A chờ vô hạn.
                signals.releaseFirst.countDown();
            }
        }
    }
}
```

Chạy từ thư mục `scheduler-thread-demo` (có JDK 17+ và Maven):

```bash
mvn -q compile exec:java
```

**Bảy dòng do chương trình in ra**, theo đúng thứ tự khi chạy thành công:

```text
callerAndSchedulerDifferent=true
firstOnSchedulerThread=true
secondOnSchedulerThread=true
sameSchedulerWorker=true
secondWaitedBehindFirst=true
firstPendingBeforeRelease=true
bothCompleted=true
```

### Tại sao không có race trong kết luận quan trọng?

1. `AnnotationConfigApplicationContext` khởi tạo Spring bean `ThreadPoolTaskScheduler` với `poolSize=1`; scheduler tạo/quản lý pool khi bean được khởi tạo.
2. Main gọi `schedule` tại `Instant.now()`. Scheduler worker nhận task A; task A lưu tham chiếu tới thread của mình, báo `firstStarted` rồi đợi `releaseFirst`.
3. Main chờ `firstStarted.await(...)` nên biết task A **đang giữ scheduler worker**, không chỉ đang nằm trong queue.
4. Main gửi task B cũng đến hạn tại `Instant.now()`. Trong lúc A chưa được nhả, pool chỉ có một worker nên B chưa thể thực thi, bất kể CPU bên ngoài còn rảnh. Hai biến `secondWaitedBehindFirst` và `firstWasStillRunning` được chụp **trước khi nhả chốt**.
5. Main nhả A. A return; task A one-shot hoàn tất. Worker tiếp tục lấy B từ scheduler queue và chạy B.
6. `first.get(...)` và `second.get(...)` chờ **Runnable thực sự hoàn tất**. Chúng không chỉ chờ thao tác “đã thêm lịch vào queue”.
7. Cả hai `Thread` được lưu đều có tên `scheduled-...`; do pool có đúng một worker và hai task tuần tự hoàn thành bình thường nên `sameSchedulerWorker=true`.
8. Chương trình chỉ `println` từ main sau khi cả hai future đã hoàn tất, vì thế thứ tự stdout ổn định.

**Giới hạn của demo:** `CountDownLatch` chỉ là công cụ kiểm soát thứ tự để học/test. Không dùng nó để giữ scheduler worker vô thời hạn trong hệ thống thật. Thời hạn `get(10, SECONDS)` ở caller là biện pháp test failure, **không** tự làm task đang chạy được hủy. Nếu máy quá tải nghiêm trọng, demo có thể timeout thay vì tạo output đúng; không suy diễn đó là quy tắc scheduling thay đổi.

## 7. `fixedRate`, `fixedDelay` và cron khi thân job chiếm scheduler worker

Ba kiểu lịch rất phổ biến:

| Kiểu | Điểm tham chiếu của lần kế tiếp | Hệ quả khi job lâu |
| --- | --- | --- |
| Fixed rate | Khoảng cách mục tiêu giữa các **thời điểm bắt đầu** | Task chạy lâu có thể làm lịch trễ; cùng một periodic registration của `ScheduledThreadPoolExecutor` không chạy chồng các lần kế tiếp. |
| Fixed delay | Chờ một khoảng thời gian **sau khi lần trước kết thúc** rồi mới đến hạn lần sau | Job 20 giây + delay 5 giây → lần sau bắt đầu không sớm hơn khoảng 25 giây tính từ lần bắt đầu trước, chưa tính chờ worker. |
| Cron trigger | Tính các mốc đến hạn theo biểu thức cron và trigger context | Nếu scheduler bận, lần thực thi có thể trễ; chính sách bỏ qua/makeup phụ thuộc trigger và cấu hình. |

Ví dụ API với chu kỳ 5 giây:

```java
scheduler.scheduleAtFixedRate(task, Duration.ofSeconds(5));
scheduler.scheduleWithFixedDelay(task, Duration.ofSeconds(5));
scheduler.schedule(task, new CronTrigger("0/5 * * * * *"));
```

Đây là snippet minh họa; cần import `Duration`, `CronTrigger`, `Runnable` và scheduler đã khởi tạo. Mỗi lần `schedule...` ở đây là **một registration khác** nếu thực thi cả ba dòng. Đừng dùng ví dụ này nguyên xi nếu chỉ muốn một job.

### Không suy diễn fixed rate = đồng thời vô hạn

`ScheduledThreadPoolExecutor` bảo đảm các invocation kế tiếp của **cùng một periodic task registration** không chạy đồng thời, ngay cả khi pool có nhiều worker. Nếu một lượt mất 8 giây còn period là 5 giây, các lượt kế tiếp có thể bắt đầu **trễ**, không tự tạo thêm lượt chạy chồng để “bắt kịp” đúng số giây.

Tuy nhiên, hai scheduled methods khác nhau hoặc hai registration riêng là các task khác nhau. Khi `poolSize > 1`, chúng có thể chạy song song. Nếu `@Scheduled` lặp lại nhiều annotation trên cùng một method, đó cũng có thể tạo các registration độc lập và dẫn đến chồng lấn; cần kiểm tra cấu hình ứng dụng.

### Cron không phải bảo đảm chính xác mốc thời gian

Một mốc cron đến hạn khi scheduler pool không có worker rảnh vẫn có thể bị trễ. Spring có các biến thể `CronTrigger` và chính sách tính mốc tiếp theo theo trigger context; không nên dựa vào giả định rằng mọi mốc bị bỏ lỡ sẽ được thực thi bù đầy đủ. Nếu xử lý định kỳ phải bù dữ liệu theo thời gian thực, hãy lưu mốc xử lý trong database và đọc theo watermark/idempotency thay vì suy từ số lần cron đã gọi.

## 8. Chuyển việc nặng sang `TaskExecutor` khi phù hợp

Nếu scheduled method có I/O blocking dài hoặc xử lý batch, một lựa chọn là làm nó thành **bộ kích hoạt ngắn** rồi giao công việc cho executor chuyên trách:

```text
ThreadPoolTaskScheduler
      |
      +-- mỗi 5 giây kích hoạt tick()
                  |
                  +-- submit job cho businessExecutor
                  |         |
                  |         +--> worker 1: xử lý batch
                  |         +--> worker 2: xử lý batch khác
                  |
                  +-- tick() return sớm, scheduler worker rảnh
```

Ví dụ cấu trúc qua **hai bean Spring khác nhau**:

```java
@Component
class ScheduleTick {
    private final BackgroundJobs jobs;

    ScheduleTick(BackgroundJobs jobs) {
        this.jobs = jobs;
    }

    @Scheduled(
        fixedRate = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "taskScheduler"
    )
    public void tick() {
        jobs.runBatchAsync(); // Gọi qua Spring bean/proxy khác.
    }
}

@Component
class BackgroundJobs {
    @Async("businessExecutor")
    public CompletableFuture<Void> runBatchAsync() {
        // Công việc lâu hoặc blocking thuộc pool businessExecutor.
        doBatchWork();
        return CompletableFuture.completedFuture(null);
    }

    private void doBatchWork() {
        // Logic nghiệp vụ được cài trong ứng dụng thật.
    }
}
```

**Đây là snippet kiến trúc**, không phải ứng dụng tự chạy: cần `@EnableScheduling`, `@EnableAsync`, imports, hai bean scheduler/executor, cấu hình error handling, giới hạn queue/concurrency và nghiệp vụ thực tế. Sử dụng hai bean để lời gọi đi qua async proxy; `this.runBatchAsync()` trong cùng bean sẽ **không tự chuyển sang executor** theo proxy mode mặc định.

**Offload là quyết định nghiệp vụ, không phải mặc định của `ThreadPoolTaskScheduler`.** Nó mang theo hệ quả:

- Fixed delay lúc này chỉ đo khoảng cách sau khi **tick trả về**, không phải sau khi **batch xử lý xong**. Lượt sau có thể gửi thêm batch khi batch trước vẫn chạy.
- Fixed rate có thể gửi task đều hơn khả năng xử lý, gây queue backlog. Cần giới hạn số job in-flight, queue, retry và backpressure.
- `ScheduledFuture` của tick không chứng minh batch đã thành công; phải theo dõi `CompletableFuture`, job ID, metric hoặc kết quả bền vững của batch riêng.
- Nếu `businessExecutor` có `CallerRunsPolicy`, khi bão hòa, call submit có thể khiến scheduler thread **tự chạy batch**, phá kỳ vọng “tick trả về ngay”.
- Nếu executor từ chối task (`TaskRejectedException`), lịch vẫn có thể đang chạy nhưng job nghiệp vụ không được chấp nhận. Xác định cách log/alert/ghi job record hoặc dừng kích hoạt.
- Nếu task quan trọng không được mất qua restart, queue RAM của executor chưa đủ; dùng database job store hoặc broker bền vững với idempotency.

Khi bản thân công việc rất ngắn, không blocking và không cần isolation, chạy trực tiếp trên scheduler worker có thể **đơn giản và hoàn toàn đúng**. Không phải job nào cũng cần thêm một executor.

## 9. Nhiều scheduler hay một scheduler có nhiều threads?

Có ít nhất ba hướng thiết kế:

**Một scheduler, `poolSize` đủ cho nhiều job nhỏ.** Hợp lý khi các job có thời gian ngắn, tải dự đoán được và không cần cách ly mạnh. Dễ quản lý bean và lifecycle, nhưng một nhóm blocking có thể ảnh hưởng nhóm khác nếu dùng hết worker.

**Nhiều scheduler riêng theo nhóm cần cách ly.** Ví dụ scheduler cho heartbeat/maintenance nhẹ và scheduler riêng cho công việc đòi độ trễ nghiêm ngặt. Cách ly worker và queue scheduling tốt hơn, nhưng tăng thread và cấu hình; các scheduler vẫn tranh chấp cùng CPU/DB/heap.

**Scheduler kích hoạt + `ThreadPoolTaskExecutor` xử lý.** Phù hợp khi chỉ cần xác định thời điểm gửi job, còn execution cần concurrency/queue, policy riêng. Đổi lại phải quản lý trạng thái in-flight, rejection, timeout, completion và job durable nếu cần.

| Câu hỏi thiết kế | Dấu hiệu cần chú ý |
| --- | --- |
| Job chiếm worker bao lâu? | p95/p99 duration dài hoặc bị block HTTP/DB. |
| Bao nhiêu job cùng đến hạn? | Cron cùng một giây, đợt nạp dữ liệu buổi sáng. |
| Job nào quan trọng hơn? | Heartbeat/health check không được đứng sau batch dài. |
| Có cần completion gắn với lịch? | Chạy trực tiếp hoặc theo dõi Future xử lý riêng khi offload. |
| Khi quá tải sẽ làm gì? | Chờ, bỏ lượt, trả lỗi, gộp lượt hay retry? |
| Có cần bền vững qua crash? | Dùng persistent job/broker thay vì chỉ scheduling RAM. |
| Tổng thread/connection budget? | Nhiều pool cùng có thể dùng hết connection hoặc CPU. |

## 10. Lỗi và `ErrorHandler` trong scheduled work

Với **one-shot** Runnable, exception có thể được phản ánh qua `ScheduledFuture.get()` dưới dạng `ExecutionException` (tùy lớp wrapper và error handler).

Với **periodic** work, một exception chưa được xử lý có thể ảnh hưởng đến việc lập lịch lần kế tiếp. Spring sử dụng error-handling strategy trong scheduling infrastructure; theo cấu hình mặc định của Spring, lỗi ở repeating tasks thường được log và **suppress** để những lần sau tiếp tục, trong khi one-shot task có semantics truyền lỗi khác. Không đoán rằng mọi exception sẽ dừng cron ngay hoặc mọi exception đều được nuốt.

Khi cần chính sách lỗi cụ thể, cấu hình `ErrorHandler`:

```java
ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
scheduler.setPoolSize(2);
scheduler.setThreadNamePrefix("jobs-");
scheduler.setErrorHandler(error ->
    System.err.println("Scheduled job failed: " + error.getMessage())
);
```

Đây là snippet cấu hình, không độc lập; cần `@Bean` hoặc quản lý lifecycle. Handler trên chỉ ghi lỗi, **không** tự retry/rollback hoặc lưu job status. Một `ErrorHandler` tự ném exception có thể thay đổi hành vi tiếp theo, nên hãy cân nhắc và test chính policy mong muốn.

Trong job định kỳ production, nên có các số liệu: số lần đến hạn, thời điểm **bắt đầu thật**, thời gian chờ scheduler queue, thời gian chạy, success/failure, last successful completion, số lượt bỏ qua, số job đang chạy và độ trễ so với lịch. Đừng chỉ log “cron fired” rồi kết luận batch đã hoàn tất.

### `@Scheduled` không trực tiếp trả Future cho caller

Các method `@Scheduled` thông thường không có caller theo kiểu RPC để trả một Future nghiệp vụ tại mỗi tick. Hạ tầng scheduling quản lý registration. Nếu muốn theo dõi kết quả từng invocation, dùng status record/metric, event, hoặc một completion handle do ứng dụng quản lý. Khi dùng reactive scheduled methods, Spring có hợp đồng riêng về Publisher subscription; không áp dụng suy luận từ Runnable đồng bộ cho mọi return type đặc biệt.

## 11. Shutdown, cancellation và xử lý công việc đang chạy

Scheduler bean do Spring container quản lý có vòng đời startup/shutdown. Trong Spring Framework 6.1+, `ThreadPoolTaskScheduler` còn hỗ trợ pause/resume và graceful shutdown theo lifecycle.

Một số câu hỏi cần thiết trước khi deploy:

1. Khi ứng dụng dừng, có ngừng nhận job mới ngay không?
2. One-shot task đã đến hạn nhưng chưa chạy có cần xử lý không?
3. Periodic job sẽ còn được chạy lại trong khi shutdown hay bị hủy?
4. Task đang chạy được phép hoàn tất trong bao lâu? Nó có hỗ trợ interruption không?
5. Nếu process crash giữa hai lần cron, dữ liệu nào cho biết lần xử lý còn thiếu?
6. Có bảo đảm idempotency để chạy lại một job mà không tạo side effect trùng không?

`setRemoveOnCancelPolicy(true)` (khi hỗ trợ bởi executor) có thể giúp loại task đã hủy khỏi delayed queue, giảm giữ tham chiếu không cần thiết. Các tùy chọn shutdown của `ScheduledThreadPoolExecutor`/Spring lifecycle quyết định task còn chờ được phép chạy hay không; phải đọc cấu hình thực tế, không suy từ việc gọi `cancel()`.

`ScheduledFuture.cancel(true)` có thể gửi yêu cầu interrupt nếu task đang chạy và implementation hỗ trợ, nhưng **không bảo đảm** JDBC call, network socket hoặc nghiệp vụ có side effect được rollback. Với công việc giá trị cao, cần trạng thái bền vững và recovery thay vì giả định scheduler trong RAM là queue đáng tin cậy.

## 12. ThreadLocal, transaction và Spring AOP khi scheduled task chạy

Vì thân task chạy trên scheduler worker, context có tính **thread-bound** của caller không tự đi theo:

- `ThreadLocal` tự định nghĩa, MDC logging, request ID/trace context.
- `SecurityContext` mặc định của request (tùy Spring Security strategy).
- Transaction JDBC/JPA gắn với caller thread.
- Request-scoped state hoặc HTTP request attributes.

`@Scheduled` thậm chí không nhất thiết có một HTTP caller tại thời điểm trigger; đừng giả định có request context từ đầu. Worker có thể được tái sử dụng qua nhiều lần chạy, nên bất kỳ `ThreadLocal` nào job tự set phải được cleanup bằng `try/finally` hoặc cơ chế context propagation có kiểm soát.

Nếu job cần ghi database một cách nguyên tử, hãy đặt transaction ở **bean dữ liệu** được gọi qua Spring proxy hoặc dùng `TransactionTemplate` trên scheduler/worker thread. Một transaction của request không tự được truyền sang scheduled job; không copy `TransactionSynchronizationManager` hay một JDBC connection sang thread khác.

Ví dụ hai bean:

```java
@Component
class ScheduledPoller {
    private final DatabaseWriter writer;

    ScheduledPoller(DatabaseWriter writer) {
        this.writer = writer;
    }

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void run() {
        writer.saveBatch(); // qua transaction proxy của bean khác
    }
}

@Component
class DatabaseWriter {
    @Transactional
    public void saveBatch() {
        // Transaction bắt đầu ở scheduler thread trong mô hình trực tiếp.
    }
}
```

Đây là **mã cấu trúc**, cần imports, `@EnableScheduling`, `@EnableTransactionManagement`, transaction manager, data access và bean registration đúng. Nó cũng cho thấy nếu `saveBatch` là một transaction dài, scheduler worker bị giữ tới khi method return. Có thể chủ động offload sang executor nếu cần tách concurrency, nhưng transaction phải được mở trên worker thực sự xử lý dữ liệu, không ở thread submit.

## 13. Những sai lầm phổ biến

| Nhận định sai | Vì sao sai | Cách sửa |
| --- | --- | --- |
| “Scheduler chỉ bấm giờ; Spring tự tạo worker khác để chạy job.” | `ThreadPoolTaskScheduler` chạy Runnable ngay trên scheduler threads. | Kiểm tra thread name/stack và thiết kế offload nếu cần. |
| “`poolSize=1` vẫn chạy song song nhiều scheduled jobs.” | Một worker chỉ chạy một Runnable tại một thời điểm. | Tăng worker có ngân sách, hoặc tách pool. |
| “Tăng poolSize bảo đảm job luôn đúng giờ.” | CPU, DB, locks và overload có thể gây chậm. | Đo lag, duration, connection usage và timeout. |
| “Mọi lần fixed rate của một registration đều overlap khi job quá dài.” | `ScheduledThreadPoolExecutor` không chạy chồng lần kế tiếp của cùng periodic task. | Phân biệt nhiều registration với một registration. |
| “`ScheduledFuture.get()` dùng để chờ một vòng cron.” | Future của periodic job thường không complete bình thường sau mỗi lượt. | Có per-run completion/status riêng. |
| “Khai báo `@Scheduled` luôn cần `@EnableAsync`.” | `@Scheduled` dùng `@EnableScheduling`. | Chỉ bật async khi thật sự dùng `@Async`. |
| “Gửi sang executor khác vẫn tính fixed delay từ lúc batch kết thúc.” | Nếu scheduled tick chỉ submit rồi return, scheduler coi tick đã xong. | Theo dõi batch completion và admission control riêng. |
| “`ThreadPoolTaskScheduler` có queueCapacity/maxPoolSize như ThreadPoolTaskExecutor.” | Chúng dựa trên các loại JDK executor khác nhau. | Dùng đúng setPoolSize và hiểu delayed queue. |
| “`@Scheduled` có thể tự lấy transaction từ caller.” | Không có cùng caller thread/transaction. | Mở transaction riêng nơi thực thi nếu cần. |
| “Có `@Async` trong cùng bean nên self-call chắc offload.” | Self-invocation thường bỏ qua Spring proxy. | Gọi qua bean khác/injected proxy. |
| “`CallerRunsPolicy` luôn giữ scheduler rảnh.” | Khi pool đích bão hòa, submitter có thể phải chạy task. | Quản lý rejection và giới hạn inflight. |
| “Exception luôn dừng mọi lượt sau.” | Tùy one-shot/periodic handler của Spring/JDK. | Xác định ErrorHandler và giám sát failures. |
| “Task bị cancel nghĩa side effects tự rollback.” | Cancellation best effort không tự hoàn tác I/O/DB. | Timeout thật, idempotency, transaction và cleanup. |
| “Cron trong RAM bảo đảm xử lý mọi lượt sau restart.” | Lịch/queue không phải persistent job store. | Dùng job store/outbox/broker khi cần durability. |

## 14. Checklist khi chọn scheduler cho một module

1. **Task dài bao lâu?** Tách queue wait và execution time, đặc biệt p95/p99 dưới tải.
2. **Có blocking?** HTTP/JDBC/lock/file operations có timeout và mức concurrency riêng chưa?
3. **Mục tiêu scheduling là gì?** One-shot, fixed delay, fixed rate, cron theo múi giờ hay trigger tùy chỉnh?
4. **Cần bao nhiêu scheduler worker?** `poolSize` hiện tại có đủ cho các job đến hạn trùng nhau không?
5. **Job nào cần isolation?** Heartbeat/health check có bị batch chặn không?
6. **Có chủ động offload không?** Nếu có, kết quả của scheduler future và business future có khác nhau không?
7. **Có overload policy không?** Queue executor bên dưới có thể đầy, caller-runs có thể block scheduler.
8. **Có state bền vững không?** Khi crash/restart hoặc chạy nhiều replica, công việc có thể trùng/bỏ sót không?
9. **Có duplicate scheduling không?** Nhiều application replicas cùng bật cron? Có cần distributed lock/idempotency?
10. **Có đúng context/transaction không?** Không copy ThreadLocal/transaction mù quáng, dọn context trên reused worker.
11. **Có quan sát được không?** Log scheduler name, lag, duration, success/failure, skipped runs, backlog, saturation.
12. **Có shutdown strategy không?** Chờ task đang chạy, cancel future, dọn queue, retry task chưa hoàn tất ra sao?

## 15. Tự kiểm tra kiến thức

**Câu 1.** Một `ThreadPoolTaskScheduler` có `poolSize=1`. Task A đến hạn và chờ HTTP 15 giây; task B đến hạn sau A 2 giây. B có mặc định được chạy ở execution thread khác không?

**Đáp án:** Không. Thân task thông thường chạy trên scheduler worker; một worker đang bận với A làm B phải đợi đến khi có worker rảnh.

**Câu 2.** Gọi `schedule(Runnable, Instant)` và nhận `ScheduledFuture`. Future đó có đại diện cho kết quả `Runnable` đã chạy không, hay chỉ việc đặt lịch?

**Đáp án:** Với one-shot task trên `ThreadPoolTaskScheduler`, future phản ánh completion của chính `Runnable` trên scheduler thread. Chỉ nhận handle không có nghĩa task đã completed, nhưng `future.get()` hoàn tất bình thường chứng tỏ Runnable đã hoàn tất.

**Câu 3.** Nếu `@Scheduled` dùng `ThreadPoolTaskScheduler`, có cần `@EnableAsync` để thân job chạy trên scheduler worker không?

**Đáp án:** Không. `@EnableScheduling` đăng ký lịch. `@EnableAsync` chỉ cần nếu nghiệp vụ còn sử dụng proxy `@Async` cho offload khác.

**Câu 4.** Với một periodic registration fixed rate period 5 giây nhưng một lượt chạy 8 giây, JDK scheduler có tự chồng lượt kế tiếp của **cùng registration** không?

**Đáp án:** Không. `ScheduledThreadPoolExecutor` không thực thi đồng thời các lượt của cùng periodic task; lượt tiếp theo có thể bắt đầu muộn.

**Câu 5.** Tại sao trong ví dụ `secondWaitedBehindFirst=true` mà không cần `Thread.sleep()`?

**Đáp án:** Main chỉ gửi B sau khi A báo đã bắt đầu và đang đợi `releaseFirst`; với một scheduler worker, B không thể chạy trước khi main mở chốt. Đây là quan hệ thứ tự xác định, không phải đo timing ngẫu nhiên.

**Câu 6.** Một scheduled tick chỉ submit batch sang `ThreadPoolTaskExecutor` rồi return. `ScheduledFuture` của tick đã xong có nghĩa batch đã commit database không?

**Đáp án:** Không. Completion của tick chỉ bao phủ hành động scheduling/submission; batch có lifecycle và future/status riêng.

**Câu 7.** Vì sao `ScheduledFuture.get()` không phải cách đợi “vòng thứ nhất” của periodic job?

**Đáp án:** Periodic registration tiếp tục qua nhiều lượt, nên future không hoàn tất bình thường sau mỗi lượt. Dùng per-run completion/metrics hoặc thiết kế job status riêng.

**Câu 8.** Tăng `poolSize` từ 1 lên 8 có giải quyết mọi trễ nếu tất cả task chờ cùng một DB connection pool hai connection không?

**Đáp án:** Không. Worker có thể nhiều hơn nhưng bottleneck chuyển sang database/lock/connection. Cần giới hạn concurrency, timeout và sizing toàn hệ thống.

**Câu 9.** Khi job scheduled cập nhật dữ liệu cần transaction, transaction của HTTP caller có tự đi theo không?

**Đáp án:** Không. Cần mở transaction trên scheduler/worker thread, tốt nhất qua bean dữ liệu `@Transactional` hoặc `TransactionTemplate` phù hợp.

**Câu 10.** `ThreadPoolTaskScheduler` và `SimpleAsyncTaskScheduler` có cùng mô hình thread với mọi loại trigger không?

**Đáp án:** Không. `ThreadPoolTaskScheduler` chạy task trên scheduler threads; `SimpleAsyncTaskScheduler` có mô hình khác, đặc biệt trường hợp fixed delay. Cần đọc Javadoc của implementation được chọn và cân nhắc phiên bản JDK.

### Ghi nhớ

`ThreadPoolTaskScheduler` **vừa canh lịch, vừa thực thi công việc thông thường trên chính scheduler worker**. Với mặc định một thread, một task blocking có thể trì hoãn các task khác. Chọn `poolSize` dựa trên workload đo được; nếu cần scheduler chỉ kích hoạt ngắn rồi xử lý song song, **chủ động offload** qua executor riêng và theo dõi completion, rejection, timeout, overload và durability của phần việc được gửi. Đừng hiểu “đến hạn” là “bảo đảm đã bắt đầu”, hay “đã submit” là “đã xử lý xong”.

### Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — `ThreadPoolTaskScheduler` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.15/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): trực tiếp xác nhận task chạy trên scheduler threads, `poolSize` mặc định 1 và `ScheduledFuture` phản ánh completion.
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `TaskScheduler`, `@Scheduled`, trigger, fixed rate/fixed delay, lifecycle và các scheduler implementations.
- [Spring Framework 6.2 — `TaskScheduler` API](https://docs.spring.io/spring-framework/docs/6.2.15/javadoc-api/org/springframework/scheduling/TaskScheduler.html): one-shot và recurring scheduling APIs.
- [Java SE 17 — `ScheduledThreadPoolExecutor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html): periodic executions, worker, cancellation, scheduling delays.
- [Java SE 17 — `ScheduledFuture`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): future và delay semantics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduler-selection-004">ConcurrentTaskScheduler thích nghi bộ lập lịch JDK</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentTaskScheduler thích nghi bộ lập lịch JDK

## 1. Vấn đề: đã có scheduler JDK, muốn dùng giao diện Spring

Trong Java, `ScheduledExecutorService` là API chuẩn để lên lịch thực thi `Runnable` hoặc `Callable` sau một khoảng trễ, theo **fixed rate** hoặc **fixed delay**. Trong Spring Framework, `TaskScheduler` là abstraction phục vụ cùng mục đích nhưng có thêm các phương thức gắn với `Instant`, `Duration`, `Trigger` và cách phối hợp với cấu hình Spring.

Nếu ứng dụng **đã có một `ScheduledExecutorService` do JDK hoặc môi trường khác cung cấp**, bạn không nhất thiết phải viết lại scheduler. **`ConcurrentTaskScheduler`** là adapter nhận `java.util.concurrent.ScheduledExecutorService`, rồi **cung cấp giao diện `org.springframework.scheduling.TaskScheduler`** cho các Spring component.

~~~text
Ứng dụng Spring
       |
       |  TaskScheduler.schedule(task, Instant)
       |  TaskScheduler.schedule(task, Trigger)
       |  TaskScheduler.scheduleAtFixedRate(task, Duration)
       v
+------------------------------+
| ConcurrentTaskScheduler      |  <- adapter Spring
| - dịch lịch về JDK scheduling |
| - hỗ trợ Spring Trigger      |
| - xử lý lỗi task             |
+---------------+--------------+
                |
                | delegate
                v
+------------------------------+
| ScheduledExecutorService     |  <- tài nguyên thực thi JDK
| thường là ScheduledThreadPool|
| Executor                     |
+---------------+--------------+
                |
                v
             worker threads
~~~

**Điểm phải nhớ:** adapter không biến `ScheduledExecutorService` thành một scheduler bền vững hay tự tạo transaction. Số worker, thread factory, chính sách shutdown, khả năng nhận task và năng lực chịu tải **vẫn phụ thuộc executor phía dưới**. Cách cấu hình lifecycle phải thể hiện rõ thành phần nào sở hữu executor.

Bài nằm trong nhóm **Chọn scheduler** của **Spring Framework – concurrency**. Trọng tâm là cách `ConcurrentTaskScheduler` thích nghi JDK, API sử dụng, trigger scheduling, vòng đời, so sánh với `ThreadPoolTaskScheduler` và ví dụ Java 17 + Spring có thể chạy.

## 2. Hai giao diện: ScheduledExecutorService và TaskScheduler

### 2.1. JDK ScheduledExecutorService

`ScheduledExecutorService` kế thừa `ExecutorService`. Các API thường dùng:

~~~java
ScheduledFuture<?> oneTime =
        scheduledExecutor.schedule(task, 2, TimeUnit.SECONDS);

ScheduledFuture<?> fixedRate =
        scheduledExecutor.scheduleAtFixedRate(
                task, 1, 5, TimeUnit.SECONDS);

ScheduledFuture<?> fixedDelay =
        scheduledExecutor.scheduleWithFixedDelay(
                task, 1, 5, TimeUnit.SECONDS);
~~~

Đây là **mảnh code minh họa**, cần `scheduledExecutor`, `task` và các import `java.util.concurrent`. Các đối số thời gian trong JDK trên là **relative delay** theo đơn vị thời gian.

- `schedule`: chạy **một lần** sau khoảng trễ được yêu cầu.
- `scheduleAtFixedRate`: lặp theo lịch dựa trên thời điểm bắt đầu được dự kiến và period.
- `scheduleWithFixedDelay`: lặp với khoảng chờ giữa **lần chạy trước hoàn tất** và **lần chạy tiếp theo bắt đầu**.
- `ScheduledFuture`: có thể theo dõi kết thúc/hủy. Với periodic tasks thông thường nó không tự hoàn thành thành công như một one-shot future; cần hủy khi không còn cần chạy.

Với một periodic task duy nhất được lịch JDK lập qua `scheduleAtFixedRate` hoặc `scheduleWithFixedDelay`, các lần chạy **không chồng lấp chính chúng** theo contract của `ScheduledThreadPoolExecutor`; nếu một lần chạy quá lâu thì các lần sau có thể trễ. **Các task khác nhau** vẫn có thể chạy song song nếu pool có đủ thread.

### 2.2. Spring TaskScheduler

Giao diện `TaskScheduler` (Spring Framework 6.1) chủ yếu có:

~~~java
Clock getClock();

ScheduledFuture<?> schedule(Runnable task, Instant startTime);

ScheduledFuture<?> schedule(Runnable task, Trigger trigger);

ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period);

ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, Duration period);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Duration delay);

ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, Duration delay);
~~~

Đây là **trích dạng chữ ký minh họa**, không phải một class Java đầy đủ. Khác JDK, Spring nhận `Instant` (mốc thời gian tuyệt đối), `Duration` (khoảng thời gian) và `Trigger` (tự tính lần chạy tiếp theo theo ngữ cảnh lịch).

Hai abstraction tách **ý định lên lịch** khỏi **cách cấp thread và thực thi**. Code nghiệp vụ có thể phụ thuộc vào `TaskScheduler` và dùng một backend JDK khi chạy đơn giản, backend do container quản lý trong Jakarta EE, hoặc backend Spring cấu hình theo bean.

### 2.3. Bảng đối chiếu

| Nhu cầu | JDK ScheduledExecutorService | Spring TaskScheduler |
| --- | --- | --- |
| Chạy một lần sau khoảng trễ | `schedule(command, delay, unit)` | `schedule(command, Instant)` |
| Fixed rate | `scheduleAtFixedRate(command, initialDelay, period, unit)` | `scheduleAtFixedRate(command, Duration)` hoặc có `Instant` |
| Fixed delay | `scheduleWithFixedDelay(command, initialDelay, delay, unit)` | `scheduleWithFixedDelay(command, Duration)` hoặc có `Instant` |
| Lịch dựa trên Cron | Không có Cron API chung trực tiếp | `schedule(task, new CronTrigger(...))` |
| Lịch tùy biến theo lần chạy trước | Phải tự lập logic/điều phối | `schedule(task, Trigger)` |
| Hủy lịch | `ScheduledFuture.cancel(...)` | `ScheduledFuture.cancel(...)` |
| Thread pool và vòng đời | Cấu hình/quản lý executor cụ thể | Phụ thuộc implementation hoặc tài nguyên delegate |

`ConcurrentTaskScheduler` làm cầu nối để code Spring dùng cột bên phải, trong khi backend là một đối tượng JDK ở cột trái.

## 3. ConcurrentTaskScheduler thực sự làm gì?

`org.springframework.scheduling.concurrent.ConcurrentTaskScheduler` kế thừa `ConcurrentTaskExecutor` và implement `TaskScheduler`. Vì vậy nó có khả năng hoạt động như một `TaskExecutor`/`SchedulingTaskExecutor` cho task thông thường, đồng thời cung cấp API lên lịch.

Trên nhánh **TaskScheduler**, nó chuyển các lời gọi đến `ScheduledExecutorService` được cấu hình. Với các lịch theo `Trigger`, khi backend là scheduler JDK thông thường, Spring sử dụng logic tính lần chạy tiếp theo và **lên lịch lại** (rescheduling) trên JDK executor theo từng lần chạy.

~~~text
taskScheduler.schedule(task, springTrigger)
                   |
                   v
             Trigger.nextExecution(context)
                   |
                   v
        tính thời điểm chạy tiếp theo
                   |
                   v
        ScheduledExecutorService.schedule(...)
                   |
                   v
             Runnable hoàn tất
                   |
                   v
         cập nhật TriggerContext
                   |
                   +----> tính/lên lịch lần kế tiếp
~~~

**Lưu ý:** với `ManagedScheduledExecutorService` theo **JSR-236/Jakarta Concurrency** trong môi trường phù hợp, `ConcurrentTaskScheduler` có cơ chế nhận diện và có thể dùng kiểu scheduling theo Trigger do môi trường quản lý thay vì nhánh rescheduling cục bộ. Nếu bạn chạy JDK thuần như ví dụ trong bài, hãy hiểu nó dùng backend `ScheduledExecutorService` thông thường.

Adapter còn có tùy chọn `setErrorHandler(...)` cho lỗi của task và `setClock(...)` để xác định Clock dùng trong tính toán lịch. Nó **không phải** persistence layer, distributed scheduler hay cơ chế đảm bảo chạy đúng giờ tuyệt đối trong điều kiện hệ thống bão hòa.

### 3.1. Ba constructor và phiên bản Spring

Trong dòng Spring Framework 6.1 có các cách khởi tạo đáng chú ý:

~~~java
// Ưu tiên khi một JDK scheduler phục vụ cả hai vai trò.
ConcurrentTaskScheduler scheduler =
        new ConcurrentTaskScheduler(jdkScheduledExecutor);

// Nếu muốn executor chạy task thường và scheduler khác nhau:
ConcurrentTaskScheduler separated =
        new ConcurrentTaskScheduler(
                generalExecutor, jdkScheduledExecutor);
~~~

Đây là **mảnh code minh họa**; cần khai báo `generalExecutor` kiểu `Executor` và `jdkScheduledExecutor` kiểu `ScheduledExecutorService`.

Constructor thứ nhất dùng **cùng một ScheduledExecutorService** làm delegate cho việc scheduling và, theo API constructor, các lời gọi task execution thông thường. Constructor thứ hai nhận **hai delegate** riêng: `Executor` cho các lời gọi `SchedulingTaskExecutor`, `ScheduledExecutorService` cho các phương thức `TaskScheduler`.

Constructor **không tham số** tồn tại trong Spring 6.1 nhưng đã được **đánh dấu deprecated**; trong code mới, nên cung cấp backend rõ ràng qua constructor thay vì dựa vào scheduler mặc định ngầm.

Có phương thức `setScheduledExecutor(ScheduledExecutorService)`, nhưng chú ý nó **chỉ thay delegate của nhánh scheduling**. Nếu muốn `execute`/`submit` của `ConcurrentTaskExecutor` đi vào một executor khác/giống backend mới, phải cấu hình nhánh concurrent executor tương ứng (ví dụ `setConcurrentExecutor(...)`) hoặc dùng constructor hai tham số.

### 3.2. Không nhầm adapter với ThreadPoolTaskScheduler

`ThreadPoolTaskScheduler` là một implementation Spring thường tiện hơn khi ứng dụng **muốn Spring cấu hình và quản lý pool** bằng các thuộc tính kiểu bean như pool size, thread name prefix, shutdown policies và lifecycle integration. `ConcurrentTaskScheduler` phù hợp khi **đã có `ScheduledExecutorService`** hoặc cần thích nghi một implementation JDK được tạo theo chính sách riêng.

Nếu chỉ cần một scheduler pool do Spring quản lý và không có backend JDK sẵn, `ThreadPoolTaskScheduler` thường ít cấu hình hơn. Chọn adapter để dùng tài nguyên bên dưới đã có, không phải vì tên "Concurrent" sẽ tự tạo thêm parallelism so với pool đã cấp.

## 4. Cấu hình Spring bean với JDK ScheduledExecutorService

### 4.1. Tạo backend trước, sau đó bọc bằng adapter

~~~java
@Configuration(proxyBeanMethods = false)
public class SchedulingConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService jdkScheduledExecutor() {
        return Executors.newScheduledThreadPool(2);
    }

    @Bean("taskScheduler")
    public TaskScheduler taskScheduler(
            ScheduledExecutorService jdkScheduledExecutor) {
        return new ConcurrentTaskScheduler(jdkScheduledExecutor);
    }
}
~~~

Đây là **mảnh cấu hình**; cần import `Configuration`, `Bean`, `TaskScheduler`, `ConcurrentTaskScheduler`, `Executors` và `ScheduledExecutorService`. Nó thể hiện rõ **ai quản lý tài nguyên**: backend JDK là Spring bean; lệnh `destroyMethod = "shutdown"` yêu cầu Spring shutdown nó khi context đóng.

Khi dùng JDK executor đã có sẵn trong ứng dụng, hãy đăng ký/wire instance đó vào bean adapter, không cần tạo pool thứ hai. Nếu executor được quản lý bởi **application server**, việc gọi `shutdown()` từ một ứng dụng con thường **không đúng quyền sở hữu**. Phải để container sở hữu lifecycle.

**Không dùng `new ConcurrentTaskScheduler()` rồi bỏ qua chuyện shutdown một executor ngầm**. Trong Spring 6.1 constructor không tham số đã deprecated; explicit dependency và owner rõ ràng giúp giảm thread leaks.

### 4.2. Nếu có nhiều ScheduledExecutorService bean

Constructor injection theo type có thể mơ hồ. Hãy dùng `@Qualifier`:

~~~java
@Bean("reportTaskScheduler")
public TaskScheduler reportTaskScheduler(
        @Qualifier("reportJdkExecutor")
        ScheduledExecutorService backend) {
    return new ConcurrentTaskScheduler(backend);
}
~~~

Đây là mảnh cấu hình, giả định một bean `reportJdkExecutor` đã tồn tại và import phù hợp. Khi có nhiều `TaskScheduler`, các consumer nên inject bằng qualifier thay vì dựa vào lựa chọn ngầm.

Đừng đặt tên `taskScheduler` cho nhiều bean khác nhau rồi trông chờ `@Scheduled` tự chọn đúng: điều phối annotation phụ thuộc cấu hình scheduler selection của context.

## 5. Chạy một lần bằng Instant

API thuận tiện nhất:

~~~java
ScheduledFuture<?> handle =
        taskScheduler.schedule(
                () -> System.out.println("once"),
                Instant.now().plusSeconds(10));
~~~

Đây là đoạn Java minh họa, `taskScheduler` phải là reference `TaskScheduler` đã được inject. `Instant` diễn tả **mốc thời gian trên timeline**. Backend JDK cần một delay tương đối, nên adapter tính delay giữa Clock hiện tại và mốc cần chạy rồi chuyển cho delegate.

Điều gì xảy ra nếu `startTime` đã ở trong quá khứ? Scheduler thông thường sẽ thực thi càng sớm càng có thể theo quy tắc scheduling delay; không bảo đảm một nanosecond hay millisecond cụ thể. Việc chuyển giờ hệ điều hành, pause JVM, pool bận hoặc backlog đều có thể khiến thời gian chạy thực tế lệch.

Lưu ý `schedule(Runnable, Trigger)` có thể trả **null** nếu `Trigger` không có thời điểm chạy tiếp theo; việc sử dụng `ScheduledFuture` phải xét kết quả ấy. Với schedule theo `Instant`, backend bình thường trả một handle có thể hủy.

## 6. Fixed rate và fixed delay qua adapter

### 6.1. Fixed rate: thời điểm bắt đầu dự kiến

~~~java
ScheduledFuture<?> fixedRate =
        taskScheduler.scheduleAtFixedRate(
                () -> refreshCache(),
                Duration.ofSeconds(5));
~~~

Mảnh code này cần method `refreshCache()` phù hợp. "Fixed rate" nghĩa là cố duy trì **khoảng cách giữa các mốc bắt đầu dự kiến**, không phải mỗi lần chắc chắn bắt đầu đúng giờ. Nếu job chạy lâu hơn period hoặc pool bận, các lần chạy sau có thể bị muộn.

### 6.2. Fixed delay: chờ sau khi kết thúc

~~~java
ScheduledFuture<?> fixedDelay =
        taskScheduler.scheduleWithFixedDelay(
                () -> pullMessages(),
                Duration.ofSeconds(5));
~~~

Mảnh này cần `pullMessages()`. "Fixed delay" đo từ **thời điểm job trước kết thúc** đến thời điểm job tiếp theo có thể bắt đầu. Nếu job mất 12 giây, delay 5 giây, lần tiếp theo thường chỉ đủ điều kiện chạy sau khoảng 17 giây tính từ lúc bắt đầu lần trước (cộng overhead).

### 6.3. Minh họa trục thời gian

~~~text
Một task chạy khoảng 3 đơn vị; period/delay = 5 đơn vị:

Fixed rate (mốc bắt đầu dự kiến: 0,5,10,...):
0    3  5    8 10   13
|====|  |====|  |====|
run 1   run 2   run 3

Fixed delay (khoảng chờ sau khi hoàn thành: 5):
0    3       8   11      16
|====|-------|===|-------|====
run 1  delay  run 2 delay  run 3
~~~

Đây là timeline minh họa lý tưởng, không có áp lực hệ thống. Với pool thực, thời điểm chạy có thể trễ. **Hai lần thực thi kế tiếp của cùng một periodic schedule** không mặc nhiên được chạy chồng lấp chỉ vì scheduler có nhiều worker; các job **khác** có thể cùng chạy nếu pool có capacity.

### 6.4. Dùng overload có Instant

~~~java
Instant firstRun = Instant.now().plusSeconds(30);

ScheduledFuture<?> rate =
        taskScheduler.scheduleAtFixedRate(
                task, firstRun, Duration.ofMinutes(1));

ScheduledFuture<?> delay =
        taskScheduler.scheduleWithFixedDelay(
                task, firstRun, Duration.ofMinutes(1));
~~~

Đây là đoạn minh họa cần `task`. Mốc `firstRun` cho phép chọn thời điểm chạy đầu tiên. Tránh nhầm overload có `Instant` của Spring với bản JDK lấy `initialDelay` tương đối.

## 7. Trigger: khả năng nổi bật khi thích nghi từ JDK

`ScheduledExecutorService` tự nó không có API tổng quát kiểu "hãy chạy theo cron biểu thức này". Spring `TaskScheduler.schedule(Runnable, Trigger)` giải quyết nhu cầu ấy.

### 7.1. CronTrigger

~~~java
ScheduledFuture<?> cronJob =
        taskScheduler.schedule(
                () -> System.out.println("quarter-hour tick"),
                new CronTrigger("0 */15 * * * *"));
~~~

Đây là đoạn minh họa, cần import `CronTrigger`; biểu thức gồm **sáu trường** Spring cron, nghĩa là chạy ở giây 0 mỗi 15 phút. Đặt `zone` rõ ràng khi nghiệp vụ phụ thuộc múi giờ:

~~~java
CronTrigger vietnamBusinessTime =
        new CronTrigger(
                "0 0 9 * * MON-FRI",
                ZoneId.of("Asia/Ho_Chi_Minh"));
~~~

Đây là mảnh code minh họa; import `ZoneId`. Nó diễn tả lịch 09:00 mỗi ngày làm việc theo múi giờ đã chọn (theo quy tắc cron Spring). `ZoneId` khiến cách diễn giải cron rõ ràng hơn khi ứng dụng chạy ở server có timezone khác.

Không sử dụng cron cho thời gian đo bằng mili-giây trong unit test. Cron theo giây và calendar scheduling có thể bị ảnh hưởng múi giờ và thay đổi thời gian (DST) ở một số khu vực.

### 7.2. PeriodicTrigger

~~~java
PeriodicTrigger trigger =
        new PeriodicTrigger(Duration.ofSeconds(10));

trigger.setInitialDelay(Duration.ofSeconds(2));
trigger.setFixedRate(false); // mặc định fixed-delay

ScheduledFuture<?> handle =
        taskScheduler.schedule(task, trigger);
~~~

Đây là mảnh code minh họa. `PeriodicTrigger` hữu ích khi một thành phần muốn nhận **`Trigger` trừu tượng**, có thể thay `CronTrigger`, `PeriodicTrigger` hoặc trigger tự định nghĩa. Nếu mục đích chỉ là fixed rate/fixed delay, dùng trực tiếp các method `scheduleAtFixedRate`/`scheduleWithFixedDelay` thường dễ hiểu hơn.

### 7.3. Custom Trigger và TriggerContext

Trong Spring 6.1, `Trigger.nextExecution(TriggerContext)` trả `Instant` (hoặc null khi không có lịch tiếp):

~~~java
Trigger custom = context -> {
    Instant last = context.lastCompletion();

    if (last == null) {
        return context.getClock().instant().plusSeconds(3);
    }

    return last.plusSeconds(15);
};
~~~

Đây là mảnh code minh họa; khi dùng custom trigger, hãy kiểm tra nullability, timestamp và logic cuối cùng. `TriggerContext` cung cấp thông tin như `lastScheduledExecution()`, `lastActualExecution()`, `lastCompletion()` và `getClock()`. Mỗi lần chạy xong, Spring có thể gọi trigger để tìm mốc kế tiếp và lên lịch lại trên executor JDK.

Nếu trigger trả `null`, lịch kết thúc theo contract trigger; không xem đây là một lần lỗi. Một custom trigger ném exception hoặc tính mốc bất hợp lý cần được kiểm thử và xử lý theo API.

## 8. Ví dụ chạy được: Java 17, Spring Framework 6.1 và ScheduledExecutorService

Đây là project command-line hoàn chỉnh chứng minh:

1. Spring bean `TaskScheduler` thực chất là một **`ConcurrentTaskScheduler` bọc `ScheduledExecutorService` JDK**.
2. One-shot `schedule(..., Instant)` chạy trên thread do JDK factory tạo.
3. `scheduleAtFixedRate(..., Duration)` chạy nhiều lần, không yêu cầu `@EnableScheduling`.
4. `schedule(..., PeriodicTrigger)` chạy nhiều lần qua cơ chế Trigger Spring.
5. `ScheduledFuture.cancel(false)` được dùng để ngừng nhận các lần chạy định kỳ trong demo.
6. Spring đóng JDK executor khi `ApplicationContext` đóng nhờ cấu hình bean destroy method.

Demo dùng **CountDownLatch** để kiểm tra số lần chạy và tránh suy luận từ `Thread.sleep` ngẫu nhiên. Không in tên thread hoặc timestamp dao động; stdout dùng boolean có kết quả xác định khi task hoàn thành trước timeout.

**Môi trường:** JDK 17+, Gradle 8.5+ (hoặc Gradle tương thích với JDK dự án), Spring Framework 6.1.14.

### 8.1. Cấu trúc project

~~~text
jdk-scheduler-adapter/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/SchedulerAdapterDemo.java
~~~

**settings.gradle**

~~~groovy
rootProject.name = 'jdk-scheduler-adapter'
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
    mainClass = 'demo.SchedulerAdapterDemo'
}
~~~

**src/main/java/demo/SchedulerAdapterDemo.java**

~~~java
package demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

public final class SchedulerAdapterDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {

            TaskScheduler scheduler = context.getBean(TaskScheduler.class);

            AtomicBoolean allUsedJdkThreads = new AtomicBoolean(true);
            CountDownLatch oneShotRan = new CountDownLatch(1);
            CountDownLatch fixedRanTwice = new CountDownLatch(2);
            CountDownLatch triggerRanTwice = new CountDownLatch(2);

            // Chạy một lần theo mốc Instant.
            ScheduledFuture<?> oneShot = Objects.requireNonNull(
                    scheduler.schedule(() -> {
                        checkThread(allUsedJdkThreads);
                        oneShotRan.countDown();
                    }, Instant.now().plusMillis(20)));

            // Chạy nhiều lần theo fixed rate.
            ScheduledFuture<?> fixedRate =
                    scheduler.scheduleAtFixedRate(() -> {
                        checkThread(allUsedJdkThreads);
                        fixedRanTwice.countDown();
                    }, Duration.ofMillis(30));

            // Spring Trigger được thực thi qua JDK backend.
            PeriodicTrigger periodic =
                    new PeriodicTrigger(Duration.ofMillis(30));
            periodic.setInitialDelay(Duration.ZERO);

            ScheduledFuture<?> byTrigger = Objects.requireNonNull(
                    scheduler.schedule(() -> {
                        checkThread(allUsedJdkThreads);
                        triggerRanTwice.countDown();
                    }, periodic));

            try {
                oneShot.get(5, TimeUnit.SECONDS);

                if (!fixedRanTwice.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Fixed-rate timeout");
                }
                if (!triggerRanTwice.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Trigger timeout");
                }

                System.out.println("one-shot fired: " +
                        (oneShotRan.getCount() == 0));
                System.out.println("fixed-rate fired twice: " +
                        (fixedRanTwice.getCount() == 0));
                System.out.println("trigger fired twice: " +
                        (triggerRanTwice.getCount() == 0));
                System.out.println("JDK threads used: " +
                        allUsedJdkThreads.get());
            } finally {
                // Hủy các lịch định kỳ; không cần ngắt task đang chạy.
                fixedRate.cancel(false);
                byTrigger.cancel(false);
            }
        }
    }

    private static void checkThread(AtomicBoolean observed) {
        if (!Thread.currentThread().getName()
                .startsWith("jdk-scheduler-")) {
            observed.set(false);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        @Bean(destroyMethod = "shutdown")
        public ScheduledExecutorService jdkScheduledExecutor() {
            AtomicInteger sequence = new AtomicInteger();

            return Executors.newScheduledThreadPool(2, command ->
                    new Thread(command, "jdk-scheduler-" +
                            sequence.incrementAndGet()));
        }

        @Bean("taskScheduler")
        public TaskScheduler taskScheduler(
                ScheduledExecutorService jdkScheduledExecutor) {

            return new ConcurrentTaskScheduler(jdkScheduledExecutor);
        }
    }
}
~~~

### 8.2. Chạy và kết quả

Từ thư mục `jdk-scheduler-adapter`, khi đã cài Java 17+ và Gradle:

~~~bash
gradle run
~~~

Nếu project có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

**Các dòng stdout do chương trình tự in trên đường chạy thành công**:

~~~text
one-shot fired: true
fixed-rate fired twice: true
trigger fired twice: true
JDK threads used: true
~~~

Có thể có thông báo do Gradle/Spring in thêm. Những con số "hai lần" ở đây là **điều kiện test** (CountDownLatch về 0), không khẳng định task chỉ chạy đúng hai lần. Lịch định kỳ có thể chạy thêm trong khi caller đang quan sát, trước lúc hủy; điều này không ảnh hưởng boolean. Nếu môi trường quá tải hoặc scheduler không chạy trong 5 giây, chương trình ném exception thay vì báo thành công giả.

### 8.3. Vì sao ví dụ chứng minh được adapter?

- Thread factory của **JDK `ScheduledExecutorService`** chủ động đặt prefix `jdk-scheduler-`; mọi task chạy thực đều kiểm tra prefix đó.
- Spring **không tạo một thread pool thứ hai**: `new ConcurrentTaskScheduler(jdkScheduledExecutor)` nhận đúng delegate từ bean.
- `TaskScheduler.schedule(..., Instant)` dùng lịch một lần.
- `scheduleAtFixedRate(..., Duration)` chuyển vào scheduling backend.
- `schedule(..., PeriodicTrigger)` dùng abstraction Trigger của Spring và scheduling backend JDK.
- `oneShot.get(timeout)` đảm bảo one-shot đã hoàn tất trước khi in kết quả; latch của các task định kỳ xác nhận mỗi loại đã bắt đầu ít nhất hai lần.
- `finally` hủy fixed-rate và trigger schedule. `@Bean(destroyMethod="shutdown")` giúp backend JDK có lifecycle theo Spring context; adapter không cần được ép tự đóng một pool không do nó sở hữu.
- Không có `@EnableScheduling` vì code **gọi trực tiếp API TaskScheduler**, không dùng `@Scheduled`. Nếu thêm annotation-driven scheduling, cần bật hạ tầng tương ứng như mục 9.

## 9. Tích hợp với @Scheduled: phân biệt lập lịch thủ công và annotation

### 9.1. TaskScheduler không tự bật @Scheduled

Lệnh này tự đăng ký lịch khi được gọi:

~~~java
taskScheduler.scheduleAtFixedRate(
        this::refreshIndex, Duration.ofMinutes(1));
~~~

Mảnh code minh họa trên cần class có method `refreshIndex`. **Không bắt buộc** bật `@EnableScheduling` chỉ để gọi trực tiếp TaskScheduler.

Ngược lại, annotation:

~~~java
@Component
class MaintenanceJobs {
    @Scheduled(fixedDelay = 60000)
    public void refreshIndex() {
        // Thực hiện công việc.
    }
}
~~~

Mảnh này cần Spring imports, bean component scan, và **`@EnableScheduling`** trong ApplicationContext. Annotation không tự hoạt động chỉ vì bạn có một bean `ConcurrentTaskScheduler`.

### 9.2. Chỉ rõ TaskScheduler cho annotation-driven scheduling

Trong cấu hình đơn giản, Spring scheduling infrastructure có thể tìm một `TaskScheduler` duy nhất hoặc một bean tên `taskScheduler` theo quy tắc của phiên bản. Cấu hình explicit hữu ích khi có nhiều loại scheduler:

~~~java
@Configuration
@EnableScheduling
public class ScheduledJobsConfig implements SchedulingConfigurer {

    private final TaskScheduler adapter;

    public ScheduledJobsConfig(
            @Qualifier("reportTaskScheduler") TaskScheduler adapter) {
        this.adapter = adapter;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(adapter);
    }
}
~~~

Đây là **mảnh cấu hình**, cần import `@Configuration`, `@EnableScheduling`, `SchedulingConfigurer`, `ScheduledTaskRegistrar`, `@Qualifier` và một bean `reportTaskScheduler`. Cách này để các `@Scheduled` tasks đi qua scheduler mong muốn thay vì dựa vào chọn bean mơ hồ.

`@Async` là cơ chế khác: nó yêu cầu `@EnableAsync` (hoặc cấu hình tương đương) và executor phù hợp. Việc có `ConcurrentTaskScheduler` **không làm tất cả method `@Async` tự động chạy theo lịch**. `@Scheduled` xác định **khi nào** method được kích hoạt; `@Async` xác định **cách dispatch** lời gọi method khi Spring proxy intercept. Hai ý niệm có thể phối hợp nhưng cần kiểm tra số thread, ordering, rejection và exception handling.

### 9.3. Tránh đăng ký lịch hai lần

Nếu cùng một hành động đã có `@Scheduled`, lại gọi `scheduler.schedule(...)` cho nó khi startup, ứng dụng có thể tạo **hai lịch độc lập**. Sự cố thường bị hiểu nhầm là Spring gọi method trùng. Trước khi sửa bằng lock, hãy kiểm tra có bao nhiêu registration thực sự được tạo và ai sở hữu các `ScheduledFuture` tương ứng.

## 10. Quản lý vòng đời backend JDK

### 10.1. Ai tạo executor thì người đó phải có chính sách dừng

`ScheduledExecutorService` do `Executors.newScheduledThreadPool(...)` tạo có worker threads và lịch đang chờ. Nếu bạn chỉ đặt nó vào một adapter rồi bỏ reference/lifecycle, JVM có thể không kết thúc và lịch vẫn tiếp tục chạy sau khi component nghiệp vụ không còn cần.

Các cách hợp lệ:

- Trong Spring, tạo `ScheduledExecutorService` thành bean và khai báo `destroyMethod = "shutdown"` hoặc một destroy strategy phù hợp.
- Nếu code tự tạo pool ngoài container, đóng trong `finally` bằng `shutdown()` hoặc `shutdownNow()` theo hợp đồng.
- Nếu backend do Jakarta EE/container cung cấp, **không shutdown tài nguyên server-wide** từ application code; để môi trường quản lý.
- Nếu đăng ký một lịch theo yêu cầu người dùng, lưu `ScheduledFuture` và cho phép hủy lịch khi yêu cầu bị gỡ bỏ.

### 10.2. shutdown và shutdownNow khác nhau

~~~java
ScheduledExecutorService backend =
        Executors.newScheduledThreadPool(2);

try {
    // Đăng ký và dùng các lịch.
} finally {
    backend.shutdown();
    if (!backend.awaitTermination(5, TimeUnit.SECONDS)) {
        backend.shutdownNow();
    }
}
~~~

Đây là mảnh code minh họa, đặt trong method có `throws InterruptedException` hoặc catch phù hợp; không phải class độc lập. `shutdown()` ngừng nhận task mới và xử lý phần còn lại theo shutdown policies của implementation; `shutdownNow()` cố yêu cầu ngắt worker và trả về các task chờ chưa thực thi. **Không API nào hứa mọi callback đã an toàn dừng hoặc side effect đã rollback**.

Nếu thread đang chờ `awaitTermination` bị interrupt, phải xử lý `InterruptedException` đúng contract (thường yêu cầu shutdownNow nếu thực sự sở hữu backend, rồi restore status hoặc propagate).

### 10.3. ScheduledThreadPoolExecutor shutdown policies

Khi cần kiểm soát chi tiết, có thể dùng `ScheduledThreadPoolExecutor` thay vì factory tổng quát. Nó có một số thuộc tính như:

- `setRemoveOnCancelPolicy(true)`: giúp dọn các cancelled delayed tasks khỏi delay queue theo policy để giảm giữ tài nguyên.
- `setExecuteExistingDelayedTasksAfterShutdownPolicy(...)`: quyết định có thực thi các delayed tasks còn chờ sau khi shutdown hay không.
- `setContinueExistingPeriodicTasksAfterShutdownPolicy(...)`: quyết định các periodic tasks đã có có được tiếp tục sau shutdown theo policy hay không.

Các lựa chọn này cần được kiểm thử cùng SLA shutdown. Không gán chính sách quá mạnh như "luôn hủy mọi task" chỉ từ việc `ConcurrentTaskScheduler` được khai báo: **executor phía dưới** quyết định phần lớn lifecycle thực tế.

## 11. Cancellation, ScheduledFuture và bài toán hủy một lịch

`TaskScheduler` trả `ScheduledFuture<?>` để caller có thể hủy lịch:

~~~java
ScheduledFuture<?> handle =
        scheduler.scheduleAtFixedRate(
                this::runMaintenance, Duration.ofMinutes(10));

// Khi không còn muốn nhận các lần chạy sau:
boolean cancellationRequested = handle.cancel(false);
~~~

Đây là đoạn minh họa. Với `cancel(false)`, code không yêu cầu interrupt một lần chạy đang active. `cancel(true)` có thể yêu cầu interrupt worker nếu implementation hỗ trợ, nhưng **không bảo đảm** task đang làm JDBC/HTTP hoặc external side effect sẽ dừng tức thì.

Điều quan trọng:

- **Cancel schedule** và **cancel một business job** không hoàn toàn giống nhau. Một lần chạy có thể đã kích hoạt nhiệm vụ nền khác.
- Một periodic schedule có thể đã chạy một số lần trước khi bị hủy; `cancel` chỉ tác động theo hợp đồng tại thời điểm gọi.
- `ScheduledFuture.isCancelled()` báo trạng thái handle; không chứng minh mọi downstream operation đã rollback.
- Với custom `Trigger`, scheduler Spring có thể tạo những lần scheduling mới sau mỗi completion; hãy giữ **handle do `TaskScheduler.schedule(..., trigger)` trả về**, không tự hủy một internal task riêng mà adapter có thể tiếp tục reschedule.
- Không gọi `get()` không timeout trên một Future định kỳ vốn được kỳ vọng chạy mãi; nó có thể không bao giờ hoàn thành bình thường.

Khi một nhiệm vụ thật sự có deadline, cần thêm timeout cho I/O, cancellation token hoặc trạng thái job, không chỉ rely on scheduled future.

## 12. Xử lý lỗi task: adapter Spring và hành vi JDK

### 12.1. Exception trong scheduled Runnable

Với JDK `ScheduledExecutorService.scheduleAtFixedRate` trực tiếp, nếu một execution ném exception không được xử lý, các lần thực thi tiếp theo của lịch định kỳ có thể bị **suppressed** theo contract JDK.

`ConcurrentTaskScheduler` có `setErrorHandler(ErrorHandler)` và sử dụng cơ chế Spring trang trí scheduled tasks. Với cấu hình mặc định Spring, các **repeating tasks** thường được log và **suppress exception** để cho phép tiếp tục lịch; **one-shot tasks** thường log và propagate exception để nó hiện qua Future. Khi cấu hình handler tùy biến, hành vi có thể thay đổi đáng kể.

~~~java
ConcurrentTaskScheduler adapter =
        new ConcurrentTaskScheduler(jdkBackend);

adapter.setErrorHandler(error -> {
    // Ghi metric, log có rate limit, cập nhật trạng thái task.
    recordScheduledFailure(error);

    // Nếu rethrow ở đây, lịch có thể dừng theo policy.
});
~~~

Đây là mảnh code minh họa, `jdkBackend` và `recordScheduledFailure` chưa định nghĩa. Không dùng handler chỉ nuốt mọi lỗi khi nghiệp vụ cần biết job thất bại; nhưng cũng không tự ném ngoại lệ trên mọi periodic callback mà không hiểu hậu quả ngừng lịch.

### 12.2. Rejection ở thời điểm lên lịch

Khi ScheduledExecutorService đã shutdown hoặc không nhận thêm việc, `schedule` có thể ném `RejectedExecutionException`. Qua API Spring, `ConcurrentTaskScheduler` có thể chuyển lỗi ấy thành `TaskRejectedException` theo hợp đồng Spring.

**Phân biệt hai pha**:

~~~text
scheduler.schedule(task, time)
        |
        +-- Rejected at registration? -> lỗi ngay tại caller
        |
        +-- Accepted -> ScheduledFuture
                         |
                         +-- task chạy và thành công
                         +-- task lỗi, xử lý theo ErrorHandler
                         +-- handle bị cancel
                         +-- backend shutdown / process crash
~~~

Nhận một `ScheduledFuture` **không có nghĩa** hệ thống bảo đảm task sẽ chạy đúng giờ, sẽ hoàn thành hay sống sót sau restart. Cần theo dõi cả số lần **registration**, **actual start**, **completed/failed**, **cancelled** và **missed deadlines** theo yêu cầu nghiệp vụ.

### 12.3. @Scheduled và AsyncUncaughtExceptionHandler không phải một

`AsyncUncaughtExceptionHandler` chủ yếu áp dụng cho `@Async void`. Nó không phải một giải pháp chung để quản lý lỗi của mọi method do `TaskScheduler` gọi. Các scheduled runnables có error handling của scheduler; hệ thống production nên đặt chính sách logging/metrics rõ ở **đúng tầng**.

## 13. So sánh các lựa chọn scheduler Spring

| Lựa chọn | Backend chính | Khi nào phù hợp? | Lưu ý |
| --- | --- | --- | --- |
| `ConcurrentTaskScheduler` | `ScheduledExecutorService` do ứng dụng/container cung cấp | Đã có JDK scheduler và muốn Spring `TaskScheduler`/Trigger | Lifecycle pool phụ thuộc owner, cần cấu hình rõ |
| `ThreadPoolTaskScheduler` | Spring quản lý `ScheduledThreadPoolExecutor` bên dưới | Muốn cấu hình pool, naming, shutdown qua Spring bean | Thường thuận tiện hơn khi bắt đầu từ đầu |
| `DefaultManagedTaskScheduler` | Managed scheduler từ Jakarta EE/container, thường qua JNDI | Môi trường không cho ứng dụng tự sở hữu threads | Server quản lý lifecycle |
| `SimpleAsyncTaskScheduler` (Spring 6.1+) | Một scheduler thread điều phối, cấp thread chạy task theo mô hình riêng | Nhu cầu scheduler đơn giản, có thể phối hợp JDK 21 virtual threads | Với fixed-delay, các lần chạy diễn ra trên một scheduler thread theo thiết kế; phải cân nhắc tác vụ blocking |
| JDK `ScheduledExecutorService` thuần | ScheduledExecutorService | Chỉ cần Java scheduling, không cần Spring Trigger/DI | Không có TaskScheduler abstraction để Spring component dùng |
| Quartz | Scheduler chuyên biệt | Lịch phức tạp/persistent jobs, nhiều node tùy cấu hình | Thiết lập và vận hành phức tạp hơn; độ bền tùy JobStore/configuration |

**Khuyến nghị lựa chọn:** nếu bạn cần adapt một executor đã có, chọn `ConcurrentTaskScheduler`. Nếu muốn tạo pool scheduler tiêu chuẩn trong Spring với lifecycle tiện lợi, chọn `ThreadPoolTaskScheduler`. Nếu cần lịch phải **khôi phục sau crash**, job definitions bền vững hoặc coordination giữa nhiều node, nghiên cứu một scheduler/job store chuyên biệt thay vì dựa vào in-memory ScheduledExecutorService.

## 14. Khả năng đồng thời thực tế phụ thuộc executor

Tên `ConcurrentTaskScheduler` dễ gây hiểu nhầm: nó **không** bảo đảm mọi lịch chạy song song. Nếu backend:

~~~java
ScheduledExecutorService single =
        Executors.newSingleThreadScheduledExecutor();

TaskScheduler adapter = new ConcurrentTaskScheduler(single);
~~~

Các scheduled tasks dùng cùng **một worker**, nên callback blocking dài có thể làm tất cả lịch còn lại trễ. Nếu backend là pool hai thread, tối đa số task đang chạy cùng lúc còn phụ thuộc số worker thực tế và cách job sử dụng tài nguyên.

### 14.1. Phân biệt scheduling thread và công việc được dispatch tiếp

Một callback scheduled có thể:

1. Chạy công việc trực tiếp, giữ worker scheduler đến khi xong.
2. Chỉ tạo job/submit vào một `TaskExecutor` khác rồi trả về, để scheduler thread rảnh sớm.

Mẫu thứ hai giúp scheduler tiếp tục điều phối lịch đúng lúc hơn khi job nặng, nhưng tạo **hai tầng queue/rejection/lifecycle**. Phải quản lý task executor đích; không tự động đồng nghĩa job đã hoàn tất chỉ vì callback scheduler trả về.

~~~text
JDK ScheduledExecutorService
         |
         +-- trigger: gọi Runnable ngắn
                          |
                          +--> TaskExecutor.execute(heavyTask)
                                        |
                                        +--> worker pool riêng
~~~

Nếu `heavyTask` quan trọng, việc `execute` thành công chưa bảo đảm durable delivery. Hãy dùng job store/outbox/broker phù hợp khi cần persist công việc.

### 14.2. Downstream có thể làm pool nghẽn

Ngay cả với `newScheduledThreadPool(8)`, tám job cùng chờ một DB connection duy nhất không tăng throughput. Các nguồn nghẽn thường là:

- JDBC connection pool nhỏ hơn số job cùng đòi kết nối.
- Remote API rate limit/HTTP connection cap.
- Lock trên singleton mutable state.
- Lịch có cùng thời điểm kích hoạt tạo burst.
- Cron chạy vào giờ cao điểm.
- Callback có retry vô hạn, sleep/block quá lâu hoặc quên timeout.

Dùng bounded concurrency, timeout, retries có giới hạn và quan sát queue wait/deadline lag. Không chỉ tăng thread count để sửa mọi sự cố trễ lịch.

## 15. Cron, Clock, múi giờ và thay đổi thời gian

`ConcurrentTaskScheduler.setClock(Clock)` cho biết Clock dùng để tính các thời điểm scheduling, mặc định là system clock theo zone mặc định của process. `getClock()` trả Clock đó theo `TaskScheduler`.

~~~java
ConcurrentTaskScheduler adapter =
        new ConcurrentTaskScheduler(jdkBackend);

adapter.setClock(
        Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")));
~~~

Mảnh code minh họa này cần import `Clock`, `ZoneId`; `jdkBackend` đã được tạo. Đây là lựa chọn clock của adapter; không nên nhầm với việc thay đồng hồ hệ điều hành hay thay implementation timer monotonic của `ScheduledExecutorService`.

Với cron theo business timezone, **chỉ rõ ZoneId trên CronTrigger** là cách thể hiện ý định dễ hiểu. Cron có thể tạo nhiều trường hợp đặc biệt tại nơi có DST, khi giờ bị bỏ qua hoặc lặp lại; cần kiểm tra chính sách và kỳ vọng theo time zone thực.

`Instant` là mốc tuyệt đối; `Duration` biểu diễn khoảng thời gian. Khi dựa vào Calendar lịch sử và business hours, dùng `CronTrigger`/Clock phù hợp; khi chỉ cần "mỗi n giây", fixed-rate/fixed-delay và `Duration` thường rõ hơn.

**Test thời gian:** Có thể dùng Clock giả cho logic tính mốc trigger, nhưng việc chỉ thay Clock của adapter **không tự đẩy thời gian của JDK worker**. Các integration tests có thể cần `CountDownLatch`, khoảng chờ có timeout và instrumentation để xác minh task thật đã chạy.

## 16. Monitoring và bảo vệ vận hành

Nên đo ít nhất:

- **scheduled registrations**: số lịch one-shot/periodic đang đăng ký.
- **next planned execution**: mốc dự kiến tiếp theo (nếu abstraction/registry cung cấp).
- **actual start** và **completion time**: độ trễ giữa planned và actual.
- **execution duration**, error count, cancellation count, rejection count.
- **active workers**, queue depth, task waiting time ở ScheduledThreadPoolExecutor.
- **retry/overlap state** nếu callback gửi tiếp công việc sang pool khác.
- **shutdown duration** và số lịch còn pending trước khi đóng ứng dụng.

Với `ScheduledThreadPoolExecutor` cụ thể, có thể quan sát trạng thái pool/queue theo API JDK hoặc metrics framework. Với `ScheduledExecutorService` trừu tượng, không phải implementation nào cũng hỗ trợ pool size/queue depth giống nhau; **không cast mù quáng** sang `ScheduledThreadPoolExecutor` khi backend được cung cấp từ môi trường khác.

Đặt tên thread qua `ThreadFactory` (như demo `jdk-scheduler-`) giúp thread dump và log rõ hơn. Trong production, thêm structured logging, job ID và correlation ID. Không đưa token/PII vào thread name; một thread pool được tái sử dụng cho nhiều jobs khác nhau.

### 16.1. Scheduler không tự bảo đảm exactly-once

Giả sử một job gửi email có lịch 09:00. Nếu JVM dừng lúc 08:59 và chỉ khởi động lại 09:05, một lịch chỉ tồn tại trong memory **không tự khôi phục lần chạy 09:00 bị bỏ lỡ**. Một `ScheduledFuture` cũng không phải xác nhận email đã đến người nhận.

Với job quan trọng:

1. Lưu định nghĩa lịch hoặc job due time trong DB/job store.
2. Có cơ chế claim/lease khi nhiều node cùng chạy.
3. Theo dõi trạng thái pending/running/succeeded/failed/missed.
4. Retry có idempotency key hoặc deduplication.
5. Quy định có chạy bù (misfire handling) sau restart hay không.
6. Phân biệt lịch kích hoạt với side effect đã commit.

`ConcurrentTaskScheduler` chỉ là adapter cho scheduling trong process; nó **không thay thế** các cơ chế durable job execution ấy.

## 17. Anti-pattern và cách sửa

| Anti-pattern | Rủi ro | Cách sửa |
| --- | --- | --- |
| Dùng no-arg `new ConcurrentTaskScheduler()` trong Spring 6.1 rồi quên pool | Constructor deprecated, lifecycle mơ hồ | Inject `ScheduledExecutorService` rõ ràng |
| Bean adapter tạo `Executors.newScheduledThreadPool()` nội bộ không quản lý | Thread leak, shutdown không rõ | Tạo JDK executor thành bean có destroy method |
| Cho rằng `setScheduledExecutor` đổi cả `execute` | Nhầm hai delegate của adapter | Dùng constructor phù hợp hoặc cấu hình `setConcurrentExecutor` |
| Một scheduler worker chạy job blocking rất dài | Lịch khác trễ | Tăng capacity đúng mức hoặc dispatch sang bounded worker pool riêng |
| Đặt `@Scheduled` mà không bật `@EnableScheduling` | Annotation không được đăng ký | Bật scheduling đúng context |
| Tự schedule thêm cùng method đã có `@Scheduled` | Job chạy trùng | Chọn một nơi đăng ký lịch |
| Hiểu fixed rate là luôn đúng mốc hoặc luôn overlap | Quá tải, kỳ vọng sai | Đo lag, timeout, điều chỉnh period và pool |
| Dùng cron không zone cho business giờ địa phương | Lệch giờ khi deploy server khác | Chỉ định ZoneId |
| Bắt exception rồi bỏ qua mà không metric | Job hỏng âm thầm | ErrorHandler và observability rõ |
| Bắt lỗi và luôn rethrow periodic callback | Có thể làm lịch dừng | Quyết định stop/retry/continue theo hợp đồng |
| `future.get()` vô hạn trên periodic schedule | Caller có thể treo | Dùng cancellation/status thay vì chờ hoàn thành bình thường |
| `cancel(true)` được coi là worker chắc chắn dừng | Side effect có thể vẫn chạy | Hủy hợp tác và timeout downstream |
| Gửi tác vụ mới lúc executor shutdown | Bị reject | Quản lý producer/lifecycle, bắt lỗi từ chối |
| Dùng adapter để mong lịch sống qua restart | In-memory state bị mất | Durable job store/Quartz/outbox tùy yêu cầu |
| Chia sẻ một ScheduledExecutorService toàn hệ thống nhưng app con tự shutdown | Ảnh hưởng các bên khác | Tuân quyền sở hữu tài nguyên của container |

## 18. Checklist khi chọn ConcurrentTaskScheduler

- [ ] Ứng dụng **đã có** `ScheduledExecutorService` hoặc cần đặc tính riêng của nó.
- [ ] Đã phân biệt `TaskScheduler` và `TaskExecutor`; chọn constructor một/hai delegate đúng ý.
- [ ] Constructor no-arg deprecated của 6.1 không được dùng trong code mới.
- [ ] Backend được cấp qua DI, không tạo ngầm một executor mỗi lần đăng ký lịch.
- [ ] Lịch cron có timezone rõ khi cần; fixed period dùng `Duration`, mốc dùng `Instant`.
- [ ] Có quy tắc dùng `@EnableScheduling` cho `@Scheduled`, tách khỏi lời gọi API programmatic.
- [ ] Khi nhiều scheduler bean, có `@Qualifier` hoặc `SchedulingConfigurer`.
- [ ] Có owner shutdown JDK executor khi context/app kết thúc; không tắt backend container-wide trái quyền.
- [ ] Đã chọn pool size/thread factory và tính đến DB/HTTP downstream capacity.
- [ ] Có `ScheduledFuture` và cơ chế cancel khi đăng ký lịch động.
- [ ] Có chính sách exception của scheduled tasks, không đoán job chắc chắn tiếp tục hay dừng.
- [ ] Bắt/quan sát task rejection khi executor shutdown hoặc saturation.
- [ ] Không gọi `get()` vô hạn trên Future định kỳ.
- [ ] Có metrics về execution lag/duration/error/cancellation.
- [ ] Không coi scheduler trong memory là job store bền vững.
- [ ] Có test bằng latch/timeouts và cleanup, không dựa hoàn toàn vào `Thread.sleep`.

## 19. Câu hỏi tự kiểm tra

1. **ConcurrentTaskScheduler thích nghi interface nào của JDK?** `ScheduledExecutorService`, để cung cấp API `TaskScheduler` của Spring.
2. **Adapter có tự tạo pool khi dùng constructor một tham số không?** Không. Nó dùng executor được truyền vào.
3. **Hai tham số constructor thể hiện hai mục đích nào?** Một `Executor` cho task execution thông thường, một `ScheduledExecutorService` cho scheduling.
4. **`setScheduledExecutor` có nhất thiết đổi delegate của `execute` không?** Không; nó ảnh hưởng nhánh TaskScheduler, không mặc nhiên đổi concurrent delegate.
5. **Tại sao dùng Spring Trigger nếu JDK đã có scheduleAtFixedRate?** Trigger hỗ trợ cron, điều kiện thời gian và lịch tính dựa trên ngữ cảnh chạy trước.
6. **`PeriodicTrigger` mặc định fixed rate hay fixed delay?** Fixed delay; có thể đổi bằng `setFixedRate(true)`.
7. **Trong fixed delay, khoảng delay được đo từ lúc nào?** Từ khi lần chạy trước hoàn tất đến mốc lần tiếp theo.
8. **Một periodic task chạy lâu thì fixed rate có chạy chồng lên chính nó không?** Theo hợp đồng JDK `ScheduledThreadPoolExecutor`, các executions của cùng periodic schedule không overlap; chúng có thể trễ.
9. **Có TaskScheduler bean rồi thì `@Scheduled` tự hoạt động chưa?** Chưa; cần scheduling annotation infrastructure như `@EnableScheduling` đúng context.
10. **Gọi `schedule(task, trigger)` có bắt buộc `@EnableScheduling` không?** Không; đây là API gọi trực tiếp.
11. **Ai phải shutdown JDK scheduled executor?** Thành phần sở hữu tài nguyên (bean/app/container) theo lifecycle phù hợp, không phải tùy tiện ở mọi consumer.
12. **`setErrorHandler` ảnh hưởng gì?** Cách xử lý exception của scheduled tasks; nó có thể quyết định task lỗi được ghi nhận và lịch tiếp tục hoặc dừng.
13. **`cancel(false)` chắc chắn hủy mọi side effect của job đang chạy không?** Không; nó không yêu cầu ngắt task active thông qua tham số.
14. **Vì sao `schedule(..., CronTrigger)` dễ chịu ảnh hưởng timezone?** Cron diễn tả lịch theo calendar; cần zone để diễn giải giờ kinh doanh đúng.
15. **Chọn `ConcurrentTaskScheduler` hay `ThreadPoolTaskScheduler` khi bắt đầu dự án từ đầu?** Nếu không có backend JDK riêng, `ThreadPoolTaskScheduler` thường tiện hơn để cấu hình pool/lifecycle.
16. **Nếu cần lịch tồn tại qua JVM restart, ConcurrentTaskScheduler có đủ chưa?** Không; cần durable scheduling/job store và quy tắc misfire/retry/idempotency.
17. **Làm sao biết adapter dùng đúng JDK worker?** Đặt tên thread bằng ThreadFactory, test task qua TaskScheduler và quan sát prefix/metrics.
18. **Vì sao tên Concurrent không đồng nghĩa chạy tất cả lịch đồng thời?** Năng lực thực phụ thuộc executor JDK và các tài nguyên/khóa/downstream.
19. **Có thể dùng ManagedScheduledExecutorService của Jakarta EE không?** Có khi môi trường tương thích; adapter có cơ chế nhận diện. Quyền quản lý lifecycle nằm ở container.
20. **Làm sao tránh một cron job chạy hai lần trong application?** Kiểm tra có annotation registration và programmatic registration trùng hoặc nhiều instances/nodes cùng chạy; nếu phải chạy một lần toàn cụm cần coordination ở tầng cao.

**Kết luận:** `ConcurrentTaskScheduler` là **cầu nối từ JDK `ScheduledExecutorService` sang Spring `TaskScheduler`**, bổ sung API Spring cho `Instant`, `Duration` và `Trigger` mà vẫn dùng backend JDK đã có. Chọn nó khi cần **thích nghi executor bên ngoài**; dùng `ThreadPoolTaskScheduler` khi muốn Spring tự quản lý scheduler pool. Dù chọn cách nào, phải quản lý **lifecycle, cancellation, error handling, concurrency và durability** theo hợp đồng của ứng dụng.

### Tài liệu chính thức

- [Spring Framework 6.1 — ConcurrentTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ConcurrentTaskScheduler.html)
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.1 — TaskScheduler](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Framework 6.1 — PeriodicTrigger](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/support/PeriodicTrigger.html)
- [Spring Framework 6.1 — CronTrigger](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html)
- [Spring Framework 6.1 — ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Java 17 — ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)
- [Java 17 — ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html)
- [Java 17 — ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduler-selection-005">DefaultManagedTaskScheduler trong môi trường Jakarta EE</a>

<details>
<summary>Xem chi tiết</summary>

# DefaultManagedTaskScheduler trong môi trường Jakarta EE

Trong Spring Framework, việc chạy định kỳ hoặc chạy một lần sau một thời điểm được biểu diễn qua giao diện `TaskScheduler`. Khi ứng dụng chạy độc lập bằng Spring Boot hoặc trong một servlet container cho phép tự quản lý thread, người phát triển thường dùng `ThreadPoolTaskScheduler`. Nhưng trong **môi trường Jakarta EE application server được quản lý**, việc ứng dụng tự tạo và sở hữu thread có thể không phù hợp với chính sách của container. Spring cung cấp **`DefaultManagedTaskScheduler`** để sử dụng một bộ thực thi lịch chạy **do application server quản lý** thay vì dựng một pool riêng.

`DefaultManagedTaskScheduler` thuộc package `org.springframework.scheduling.concurrent`. Nó là một biến thể dựa trên JNDI của `ConcurrentTaskScheduler`; mặc định tra cứu `java:comp/DefaultManagedScheduledExecutorService` và ủy quyền việc lên lịch cho đối tượng `jakarta.enterprise.concurrent.ManagedScheduledExecutorService` do Jakarta EE container cung cấp. Đây là lựa chọn đúng khi hệ thống đã có managed scheduling resource và muốn tuân theo quản lý thread, vòng đời và component context của application server.

**Trọng tâm:** tên lớp này là một **Spring adapter**, không phải dịch vụ lập lịch bền vững xuyên máy chủ; nó không tự tạo Jakarta EE container, không tự tạo resource JNDI, không thay thế `@EnableScheduling`, và không làm cho job đã được lên lịch tồn tại qua khởi động lại JVM.

## 1. Mục tiêu học tập

Sau bài học, bạn sẽ có thể:

- Phân biệt `TaskScheduler` với `TaskExecutor` và biết khi nào nên chọn `DefaultManagedTaskScheduler`.
- Hiểu `ManagedScheduledExecutorService` thuộc Jakarta Concurrency/JSR-236 và JNDI mặc định `java:comp/DefaultManagedScheduledExecutorService`.
- Cấu hình Spring bean `TaskScheduler` dựa trên managed resource; sử dụng `@EnableScheduling` và `@Scheduled` hoặc gọi `TaskScheduler` trực tiếp.
- Biết cách điều chỉnh `jndiName` khi container khai báo resource riêng và xác nhận lookup tại lúc bean khởi tạo.
- Phân biệt managed scheduler với `ThreadPoolTaskScheduler`, `ConcurrentTaskScheduler` và `DefaultManagedTaskExecutor`.
- Hiểu vòng đời, thread/component context, transaction và chính sách thực thi thuộc trách nhiệm của Jakarta EE server.
- Biết vì sao không được tự gọi `shutdown()` hoặc thay cấu hình core/max pool bằng API của Spring wrapper.
- Kiểm tra các kiểu lập lịch: một lần, fixed-rate, fixed-delay và Spring `Trigger`/cron; quan sát lỗi, delay, trôi lịch và overload.
- Phát hiện các tình huống test local thiếu JNDI, nhiều replica cùng chạy `@Scheduled`, job mất sau restart và những giả định sai về transaction/security context.
- Chạy một ví dụ Java 17+ độc lập để luyện API `ScheduledExecutorService` và kiểm tra cancellation; hiểu rõ ví dụ này **không mô phỏng Jakarta EE container**.

## 2. Phân biệt TaskScheduler và TaskExecutor

Spring có hai nhóm abstraction rất dễ nhầm:

| Giao diện | Nhiệm vụ | Ví dụ |
| --- | --- | --- |
| `TaskExecutor` | Thực thi tác vụ khi được gửi; không nhất thiết có lịch tương lai | `ThreadPoolTaskExecutor`, `DefaultManagedTaskExecutor` |
| `TaskScheduler` | Lên lịch tác vụ chạy ở thời điểm xác định hoặc lặp theo chu kỳ/trigger | `ThreadPoolTaskScheduler`, `ConcurrentTaskScheduler`, `DefaultManagedTaskScheduler` |
| `ScheduledExecutorService` của JDK | API thấp hơn hỗ trợ `schedule`, `scheduleAtFixedRate`, `scheduleWithFixedDelay` | `ScheduledThreadPoolExecutor` |
| `ManagedScheduledExecutorService` của Jakarta | Mở rộng API scheduled executor với ngữ nghĩa container-managed/context | Resource do Jakarta EE server cung cấp qua JNDI/injection |

`@Async` là cơ chế chuyển một **lời gọi** sang executor; `@Scheduled` đánh dấu method để hạ tầng scheduling của Spring **đăng ký một lịch**. Hai nhu cầu này có thể cùng tồn tại nhưng cấu hình không tự thay thế nhau. `@EnableScheduling` kích hoạt phát hiện `@Scheduled`; `@EnableAsync` kích hoạt xử lý `@Async`, không phải điều kiện bắt buộc để chạy scheduled methods.

## 3. Vì sao Jakarta EE cần managed scheduler?

Một Jakarta EE application server quản lý tài nguyên cho các component: thread, classloader, naming context, security và các chính sách quản trị. Tự gọi:

~~~java
Executors.newScheduledThreadPool(5)
~~~

trong một bean ứng dụng có thể gây vấn đề nếu runtime hạn chế tạo unmanaged thread, không thể giới hạn/quản lý pool theo application server, hoặc không dọn được task khi redeploy. Đây là một biểu thức **Java SE minh họa**, không phải phương án khuyến nghị trong môi trường container-managed nghiêm ngặt.

Jakarta Concurrency định nghĩa resource `ManagedScheduledExecutorService` để app submit tác vụ chạy sau một delay, chạy định kỳ hoặc theo cơ chế trigger được Jakarta hỗ trợ. Resource được **container tạo và quản lý**, còn code ứng dụng chỉ sử dụng.

Sơ đồ:

~~~text
Spring application component
  |
  | @Scheduled / TaskScheduler.schedule(...)
  v
Spring DefaultManagedTaskScheduler
  |
  | JNDI lookup trong lúc khởi tạo bean
  v
java:comp/DefaultManagedScheduledExecutorService
  |
  v
Jakarta EE ManagedScheduledExecutorService
  |
  +-- container quản lý thread pool, component context, policy
  |
  +-- thực thi Runnable/Callable theo lịch
~~~

Đây là nguyên tắc **phân tách trách nhiệm**:

- Spring: cung cấp API scheduling và ánh xạ `@Scheduled`/`Trigger`.
- Jakarta EE server: cung cấp managed executor resource, kiểm soát thread, cấu hình vận hành, lifecycle và context theo specification.
- Ứng dụng: định nghĩa việc cần làm, lịch chạy, xử lý lỗi và sự an toàn nghiệp vụ (idempotency, transaction, retry...).

## 4. DefaultManagedTaskScheduler chính xác là lớp gì?

Javadoc Spring Framework mô tả:

- Tên đầy đủ: `org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler`.
- Kế thừa: `ConcurrentTaskScheduler`.
- Triển khai: `InitializingBean` ngoài các giao diện scheduler/executor từ superclass.
- Không phải một scheduler tạo pool riêng khi gọi constructor.
- Mặc định tra cứu resource JNDI tên `java:comp/DefaultManagedScheduledExecutorService`.
- JNDI lookup được thực hiện ở `afterPropertiesSet()` khi Spring BeanFactory khởi tạo bean. Với một Spring bean thông thường, bạn **không cần tự gọi `afterPropertiesSet()`**.
- Có các property như `setJndiName`, `setJndiEnvironment`, `setJndiTemplate`, `setResourceRef` để thay cách tra cứu phù hợp môi trường.

Điểm thường bị bỏ qua: **chỉ tạo một object `new DefaultManagedTaskScheduler()` trong Java thuần không lập tức có một scheduled executor hoạt động**. Khi bean khởi tạo, class cần JNDI resource hợp lệ; chạy ngoài một naming environment có bind resource như Jakarta EE sẽ khiến lookup thất bại.

### JNDI chuẩn

Theo Jakarta Concurrency, môi trường Jakarta EE tương thích cung cấp default resource dưới tên:

~~~text
java:comp/DefaultManagedScheduledExecutorService
~~~

Đừng nhầm với:

~~~text
java:comp/DefaultManagedExecutorService
~~~

Tên thứ hai dành cho `ManagedExecutorService` phục vụ công việc không nhất thiết có scheduling API. Nếu cấu hình `DefaultManagedTaskScheduler` trỏ sang managed executor **không triển khai `ScheduledExecutorService`**, khởi tạo/sử dụng scheduler không đáp ứng contract.

### JSR-236 và package Jakarta

Tài liệu Spring vẫn thường dùng cụm “JSR-236” để chỉ nguồn gốc Java EE Concurrency Utilities. Trong Jakarta EE hiện đại, API nằm ở `jakarta.enterprise.concurrent.ManagedScheduledExecutorService`. Ở hệ thống Java EE/Jakarta EE cũ, bạn có thể gặp `javax.enterprise.concurrent.*`. **Không mặc định các package `javax` và `jakarta` là tương thích nhị phân.**

Khi dùng Spring Framework 6.x, cần kiểm tra runtime Jakarta EE và phiên bản Jakarta Concurrency tương thích; một application server cũ chỉ có `javax` không tự đáp ứng những điều Spring 6 dựa trên `jakarta`. Việc chỉ đổi dependency Maven mà không nâng server không sửa được namespace hoặc khả năng cung cấp resource.

## 5. Cấu hình Spring sử dụng managed scheduler mặc định

Trong một ứng dụng Spring deploy trên Jakarta EE application server có managed resource đúng tên JNDI:

Tệp **ManagedSchedulingConfig.java**:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.DefaultManagedTaskScheduler;

@Configuration
@EnableScheduling
public class ManagedSchedulingConfig {

    @Bean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        return new DefaultManagedTaskScheduler();
    }
}
~~~

Đây là **một Spring configuration class**, cần dependency `spring-context`, JNDI resource do Jakarta EE server cung cấp và một Spring ApplicationContext phù hợp. Không thể chạy riêng file này bằng `javac`/`java` mà không có các thư viện và container.

Giải thích:

1. `@EnableScheduling` bật hạ tầng nhận diện các method `@Scheduled` trong Spring context.
2. Bean tên `taskScheduler` có kiểu `TaskScheduler` giúp Spring lựa chọn scheduler cụ thể thay vì để hạ tầng tự tạo scheduler fallback.
3. `new DefaultManagedTaskScheduler()` chỉ khai báo wrapper Spring; khi BeanFactory khởi tạo bean và gọi `afterPropertiesSet()`, wrapper tra JNDI resource mặc định.
4. Sau lookup, Spring giao việc lập lịch qua managed scheduled executor, không tự sở hữu thread pool của executor đó.
5. Nếu JNDI resource không tồn tại hoặc không tương thích, ứng dụng có thể lỗi lúc khởi tạo bean. Điều này hữu ích để phát hiện cấu hình thiếu thay vì âm thầm chạy trên pool không được quản lý.

**Quan trọng:** đừng đăng ký đồng thời nhiều bean `TaskScheduler` cạnh tranh và kỳ vọng Spring chắc chắn tự chọn đúng. Tên `taskScheduler` là quy ước Spring tìm kiếm; hoặc dùng `SchedulingConfigurer` để chọn đích tường minh.

### Một scheduled method

Tệp **PeriodicCleanupJob.java**:

~~~java
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PeriodicCleanupJob {

    @Scheduled(fixedDelay = 60000)
    public void cleanExpiredEntries() {
        // Thực hiện cleanup có giới hạn thời gian và idempotent.
        System.out.println("cleanup requested at " + Instant.now());
    }
}
~~~

Đây là **Spring component class**; output timestamp thay đổi nên **không có stdout cố định**. `fixedDelay=60000` là 60.000 mili-giây và được tính từ khi một lần thực thi hoàn tất tới khi lần sau được lên lịch chạy. Mã thật phải có cơ chế quản lý exception, timeout, transaction và tránh kéo dài vô hạn.

Nếu application server không cung cấp resource JNDI cần thiết, việc chỉ có `@Scheduled` và `@EnableScheduling` không khiến `DefaultManagedTaskScheduler` tự tạo một resource quản lý mới.

## 6. Trường hợp JNDI name tùy chỉnh

Trong một số Jakarta EE server, administrator có thể khai báo một `ManagedScheduledExecutorService` riêng với giới hạn tài nguyên/monitoring khác default và expose qua một tên JNDI đã xác minh. Spring hỗ trợ override:

~~~java
@Bean(name = "taskScheduler")
public TaskScheduler taskScheduler() {
    DefaultManagedTaskScheduler scheduler = new DefaultManagedTaskScheduler();
    scheduler.setJndiName("java:comp/env/concurrent/ReportsScheduler");
    return scheduler;
}
~~~

Đây là **mã bean method Spring minh họa**, cần các import như `ManagedSchedulingConfig`. Tên `java:comp/env/concurrent/ReportsScheduler` chỉ là ví dụ cấu hình do administrator có thể đăng ký, **không phải JNDI resource luôn tồn tại trên mọi server**. Cần cấu hình resource/ref thật tại deployment descriptor hoặc application server và bảo đảm loại resource tra được triển khai `ScheduledExecutorService`.

Các setter khác:

- `setJndiEnvironment(Properties)`: đặt các thuộc tính JNDI environment nếu runtime yêu cầu.
- `setJndiTemplate(JndiTemplate)`: thay JNDI template để phù hợp integration/configuration.
- `setResourceRef(boolean)`: ảnh hưởng cách giải tên tương đối theo environment naming context `java:comp/env/`.
- `setJndiName(String)`: thay tên lookup mặc định.

Ưu tiên sử dụng **tên đầy đủ** khi muốn tránh nhầm quy tắc prefix. Khi tên đã bắt đầu bằng `java:`, không tự ghép thêm `java:comp/env/`. Không nên sửa tên lúc runtime sau khi bean đã lookup xong và kỳ vọng tất cả task cũ tự chuyển sang resource mới.

### Lookup tự chạy khi nào?

`DefaultManagedTaskScheduler` là Spring `InitializingBean`. Trong lifecycle của bean được Spring quản lý, `afterPropertiesSet()` được gọi sau khi set properties để thực hiện lookup. Do đó:

~~~text
Spring creates bean
  -> optional setJndiName / other setters
  -> BeanFactory invokes afterPropertiesSet()
  -> JNDI lookup resolves managed scheduler
  -> bean ready for schedule() / @Scheduled
~~~

Nếu bạn tự tạo wrapper bằng `new` **ngoài Spring**, bạn phải tự quản lý initialization và dependency; cách đó không phải pattern chính của bài này. **Không tự gọi afterPropertiesSet trong @Bean method** của bean do Spring quản lý; có thể gây lookup lặp hoặc lỗi lifecycle khó hiểu.

## 7. @EnableScheduling, @Scheduled và tên bean taskScheduler

Trong Spring 6.x, hạ tầng đăng ký `@Scheduled` tìm scheduler từ context theo quy tắc được tài liệu hóa: có thể chọn bean `TaskScheduler` duy nhất; hoặc bean với tên `taskScheduler`; cũng có cơ chế tìm `ScheduledExecutorService` nếu phù hợp. Khi không resolve được, Spring có thể tạo scheduler local single-thread mặc định.

Điều này dẫn đến một rủi ro: **thấy `@Scheduled` chạy không chứng minh ứng dụng đang sử dụng Jakarta managed scheduler**. Nếu cấu hình của bạn không được import hoặc bean scheduler không được chọn, Spring có thể chạy bằng scheduler dự phòng khác.

Kiểm tra bằng ba cách:

1. Spring context có bean tên `taskScheduler` đúng kiểu `DefaultManagedTaskScheduler`?
2. Bean đã lookup JNDI thành công, delegate là managed resource mong muốn?
3. Trong integration test trên **server thật**, task chạy theo policy/threads của managed executor, và metrics của application server phản ánh hoạt động?

### Chọn scheduler tường minh bằng SchedulingConfigurer

Khi một ứng dụng có nhiều scheduler:

~~~java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class ExplicitSchedulingSelection implements SchedulingConfigurer {
    private final TaskScheduler managedScheduler;

    public ExplicitSchedulingSelection(
            @Qualifier("taskScheduler") TaskScheduler managedScheduler) {
        this.managedScheduler = managedScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(managedScheduler);
    }
}
~~~

Đây là **Spring configuration class** được dùng cùng `@EnableScheduling` ở cấu hình phù hợp. Nó tường minh ràng buộc các tác vụ do registrar quản lý tới bean `taskScheduler`, giảm nguy cơ chọn nhầm scheduler khi thêm bean mới.

Nếu bạn chủ động dùng `@Scheduled(scheduler = "beanName")` trong phiên bản Spring hỗ trợ qualifier scheduler, hãy kiểm chứng đúng tên/qualifier và tương tác với `SchedulingConfigurer`; không dựa vào mặc định khi có nhiều lựa chọn.

## 8. Các API TaskScheduler mà DefaultManagedTaskScheduler cung cấp

`DefaultManagedTaskScheduler` kế thừa khả năng của `ConcurrentTaskScheduler`, triển khai Spring `TaskScheduler`:

| API | Nghĩa |
| --- | --- |
| `schedule(Runnable, Instant)` | Lên lịch chạy một lần tại khoảng thời gian được biểu diễn bằng `Instant` |
| `schedule(Runnable, Trigger)` | Lên lịch theo một `Trigger` do Spring cung cấp, ví dụ cron/custom trigger |
| `scheduleAtFixedRate(Runnable, Duration)` | Lặp theo chu kỳ cố định từ mốc start mặc định |
| `scheduleAtFixedRate(Runnable, Instant, Duration)` | Lặp fixed-rate từ mốc bắt đầu được chỉ định |
| `scheduleWithFixedDelay(Runnable, Duration)` | Lặp sau một khoảng delay tính từ khi lần chạy trước hoàn tất |
| `scheduleWithFixedDelay(Runnable, Instant, Duration)` | Lặp fixed-delay với mốc đầu |
| `getClock()` | Clock dùng cho việc xác định thời điểm lên lịch, tùy implementation |

Mỗi method có thể trả một `ScheduledFuture<?>` (với `schedule(Trigger)` có thể không tạo future nếu trigger không cho lần chạy kế tiếp), giúp caller theo dõi/hủy lịch trong phạm vi semantics của executor. Một callback định kỳ kéo dài, exception và việc cancel cần được xử lý có chủ đích.

### Gọi chương trình theo một Instant

Tệp **ReportSchedulingService.java**:

~~~java
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class ReportSchedulingService {
    private final TaskScheduler scheduler;

    public ReportSchedulingService(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public ScheduledFuture<?> requestReportOnce(
            String reportId, Instant at) {
        return scheduler.schedule(
                () -> generateReport(reportId), at);
    }

    private void generateReport(String id) {
        System.out.println("Generating report " + id);
    }
}
~~~

Đây là **Spring-managed service** cần bean `TaskScheduler`. Nó lên lịch một lần tại `at`. Giá trị `ScheduledFuture` là handle JVM phục vụ theo dõi/hủy lịch; nó **không phải một job ID bền vững** để lưu qua deployment. Nếu ứng dụng chạy nhiều replica, mỗi instance tự nhận `requestReportOnce` và lưu handle trong RAM sẽ không có phối hợp job toàn cụm.

### Dùng CronTrigger

~~~java
import java.time.ZoneId;
import org.springframework.scheduling.support.CronTrigger;

scheduler.schedule(
        () -> recalculateDailyStats(),
        CronTrigger.forFixedExecution("0 0 2 * * *"));
~~~

Đây là **đoạn tích hợp cần scheduler/recalculateDailyStats** do ứng dụng cung cấp. Với cron của Spring, biểu thức có sáu thành phần, bao gồm **giây**. Nếu cần chọn múi giờ, dùng API CronTrigger phù hợp phiên bản Spring hoặc khai báo `@Scheduled(cron = ..., zone = ...)`. Ví dụ trên chạy lúc 02:00 theo clock/timezone mặc định của trigger; không khẳng định nó chạy “02:00 tại Việt Nam” nếu chưa đặt zone.

**Lưu ý khi chọn Spring version:** API factory `CronTrigger.forFixedExecution(String)` có trong Spring 6.1.3+; khi dùng phiên bản cũ hơn, dùng constructor/factory tương thích đúng tài liệu phiên bản đó. Nếu mục tiêu chỉ là method annotation, `@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")` là lựa chọn rõ ý hơn.

## 9. Spring Trigger và Jakarta Trigger khác nhau ra sao?

Spring có interface `org.springframework.scheduling.Trigger` và class `CronTrigger`. Jakarta Concurrency có `jakarta.enterprise.concurrent.Trigger` và `ManagedScheduledExecutorService.schedule(Runnable, Trigger)`. Đây là **hai interface khác nhau**, không thể cast tùy ý.

`ConcurrentTaskScheduler` của Spring nhận biết một delegate kiểu `ManagedScheduledExecutorService` và có logic adapter để lập lịch theo `Spring Trigger` bằng trigger của Jakarta khi phù hợp. Nhờ vậy, một Spring service có thể dùng `scheduler.schedule(runnable, springTrigger)` mà không phải tự viết `jakarta.enterprise.concurrent.Trigger`.

~~~text
Spring CronTrigger
       |
       v
Spring TaskScheduler.schedule(Runnable, Spring Trigger)
       |
       v
ConcurrentTaskScheduler adapter
       |
       +-- delegate là ManagedScheduledExecutorService?
             |
             +-- Có: sử dụng Jakarta Trigger adapter khi thích hợp
             |
             +-- Không: fallback lên cách schedule theo delay của JDK
~~~

Điều này không đồng nghĩa các implementation có chi tiết scheduling hoàn toàn giống nhau. Cần integration test nhất là với cron, timezone, DST, misfire, skip và trigger tùy chỉnh. Chính sách của server vẫn ảnh hưởng thời điểm thật sự chạy.

## 10. Fixed-rate, fixed-delay và cron: không chọn chỉ dựa vào tên

Ba chiến lược cần hiểu:

### Fixed-rate

`scheduleAtFixedRate` biểu diễn chu kỳ dựa trên mốc bắt đầu. Nếu task chạy lâu hoặc executor bị quá tải, thời gian bắt đầu thực tế có thể lệch lịch. Theo contract `ScheduledThreadPoolExecutor` tiêu chuẩn, các lần thực thi định kỳ của **cùng một periodic task** không chạy đồng thời; nếu một lần kéo dài hơn chu kỳ, lần kế có thể bắt đầu muộn.

Không tự suy rộng mọi chi tiết đó cho mọi vendor implementation của `ManagedScheduledExecutorService`, nhất là các khả năng trigger mở rộng hoặc môi trường cluster. Cần kiểm thử/đọc tài liệu server.

### Fixed-delay

`scheduleWithFixedDelay` đợi một khoảng sau khi lần trước **kết thúc** rồi mới thực thi lần tiếp theo. Thường phù hợp khi muốn tránh chạy dồn các lần xử lý dữ liệu, nhất là tác vụ có thời gian chạy biến thiên.

### Cron/Trigger

`CronTrigger` dựa vào thời điểm lịch (giờ, ngày, thứ, tháng) hoặc `TriggerContext` của Spring. Kết quả thực tế chịu tác động của timezone, clock, daylight saving, thời gian task trước hoàn tất và semantics trigger. Với yêu cầu “mỗi ngày lúc 02:00” cần ghi rõ zone và xử lý ngày DST thiếu/lặp giờ. Với lịch công việc tài chính hoặc tích hợp có SLA, thêm idempotency và kiểm tra giờ thực tế.

### Bảng chọn

| Mong muốn | API/annotation phù hợp | Ghi chú |
| --- | --- | --- |
| Một lần sau 10 giây | `TaskScheduler.schedule(task, Instant)` | Không bền vững qua JVM restart |
| Cố gắng bắt đầu mỗi 60 giây | `scheduleAtFixedRate(..., Duration.ofSeconds(60))` | Task chậm hoặc overload có thể làm lệch nhịp |
| Đợi 60 giây sau lần trước kết thúc | `scheduleWithFixedDelay(..., Duration.ofSeconds(60))` | Thời gian thực giữa lần bắt đầu dài hơn duration |
| Mỗi ngày vào một giờ xác định | `@Scheduled(cron=..., zone=...)` hoặc `CronTrigger` | Cần hiểu timezone/DST và cron syntax |
| Dựa vào lần hoàn tất/thực tế | Spring `Trigger` tự định nghĩa | Test trigger theo `TriggerContext` |
| Khôi phục lịch sau khi server restart | Persistent job store/enterprise scheduler | Không phải chỉ DefaultManagedTaskScheduler |

## 11. Managed context được container hỗ trợ đến đâu?

Jakarta Concurrency định nghĩa rằng default `ManagedScheduledExecutorService` phải hỗ trợ những component context nhất định. Theo specification, context bắt buộc của default managed scheduled executor gồm **naming context**, **classloader** và **security information** liên quan đến component gửi task.

Điều này **không** có nghĩa:

- Toàn bộ `ThreadLocal` tùy chỉnh của request sẽ tự được sao chép.
- Mọi giá trị MDC/tracing context sẽ tự xuất hiện trên worker.
- Transaction đang hoạt động ở thread submit sẽ được truyền đến thread thực thi.
- `@RequestScoped` bean luôn có semantics giống khi chạy trong request HTTP sống lâu.
- Kết quả của một scheduled job sẽ tự được gửi đến client theo hợp đồng request/response.

Spec của managed executor nêu rõ task chạy **ngoài transaction của thread gửi**. Nếu task cần transaction, phải tự định nghĩa transaction boundary cho việc thực thi, ví dụ gọi một Spring service có `@Transactional` qua proxy và transaction manager phù hợp; Jakarta cũng có đường `UserTransaction` khi được phép.

### Ví dụ Spring service transactional riêng

~~~java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryRefreshService {
    private final InventoryRepository repository;

    public InventoryRefreshService(InventoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void refreshInventory() {
        repository.refreshCachedInventory();
    }
}
~~~

Đây là **Spring bean minh họa** với `InventoryRepository` thuộc ứng dụng. Scheduled method ở một bean khác:

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InventoryRefreshSchedule {
    private final InventoryRefreshService service;

    public InventoryRefreshSchedule(InventoryRefreshService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 60000)
    public void refresh() {
        service.refreshInventory();
    }
}
~~~

Đây là **Spring component riêng**, gọi qua bean proxy của `InventoryRefreshService` để transaction có thể mở ở worker. Không gọi `this.refreshInventory()` trong cùng service rồi kỳ vọng `@Transactional` proxy mode luôn có hiệu lực. Cấu hình transaction manager phải phù hợp resource Jakarta EE/JTA/JDBC thực tế.

Nếu ứng dụng cần propagate tracing/MDC, sử dụng context propagation được framework/container hỗ trợ hoặc truyền dữ liệu cần thiết qua payload/id; tránh dựa vào thread name hoặc ThreadLocal ngầm.

## 12. Vòng đời thuộc Jakarta EE server, không thuộc bean Spring

Đây là khác biệt lớn so với `ThreadPoolTaskScheduler` tạo local executor.

`ManagedScheduledExecutorService` được application server quản lý và có thể chia sẻ giữa nhiều component. Jakarta Concurrency quy định ứng dụng **không được tự điều khiển vòng đời chung** của managed executor bằng `shutdown()`/`shutdownNow()`; các lifecycle operations này được đặc tả là ném `IllegalStateException` khi ứng dụng gọi chúng.

Vì vậy:

- Không gọi `shutdown()` trên managed resource lấy qua JNDI khi một Spring bean bị destroy.
- Không cấu hình `ThreadPoolTaskScheduler.setPoolSize()` rồi mong nó thay đổi pool do administrator tạo.
- Không lấy `ScheduledThreadPoolExecutor` từ Spring wrapper để chỉnh core threads: delegate thực tế có thể là container-managed object không hề expose API đó.
- Khi redeploy/shutdown, server có quyền đình chỉ/stop/cancel task theo policy; ứng dụng phải có idempotency và trạng thái bền cho công việc quan trọng.
- Tác vụ được chạy trong context của component; khi component không còn started, spec không cho phép các task của nó tiếp tục thực thi như đang hoạt động bình thường.

**Lifecycle của wrapper và managed delegate là hai thứ khác nhau.** Spring có thể khởi tạo/hủy bean `DefaultManagedTaskScheduler` của ứng dụng, nhưng không sở hữu quyền shutdown resource chung của Jakarta EE server.

### Cancellation trong ứng dụng

Một `ScheduledFuture<?>` do scheduler trả về có thể được dùng để yêu cầu hủy **lịch cụ thể**, tùy hợp đồng của executor. Đây không phải là lệnh shutdown toàn bộ managed scheduler. Đối với periodic jobs, hãy giữ handle nếu cần tắt lịch trong một instance; nhưng khi multi-instance hoặc restart, handle RAM không thể dùng làm danh tính job bền vững.

## 13. DefaultManagedTaskScheduler so với các lựa chọn khác

| Lớp | Executor phía dưới | Ai sở hữu thread/lifecycle? | Môi trường tiêu biểu |
| --- | --- | --- | --- |
| `DefaultManagedTaskScheduler` | JNDI `ManagedScheduledExecutorService` hoặc ScheduledExecutorService phù hợp | Container/owner của JNDI resource | Jakarta EE application server |
| `ConcurrentTaskScheduler` | `ScheduledExecutorService` được truyền vào hoặc cấu hình | Bên tạo/đưa executor vào | Adapter cho executor có sẵn |
| `ThreadPoolTaskScheduler` | Local `ScheduledThreadPoolExecutor` | Spring bean/app | Spring Boot standalone, môi trường cho phép tự quản thread |
| `DefaultManagedTaskExecutor` | JNDI `ManagedExecutorService` | Jakarta EE container | Task dispatch không nhất thiết cần lịch |
| `ThreadPoolTaskExecutor` | Local `ThreadPoolExecutor` | Spring bean/app | `@Async` hoặc task queue thông thường |

### Vì sao không dùng DefaultManagedTaskExecutor để đặt lịch?

`DefaultManagedTaskExecutor` lookup default `java:comp/DefaultManagedExecutorService`, không phải default scheduled executor. Nó thích hợp cho `execute/submit` các task không yêu cầu `schedule`/`fixedRate`/`Trigger`. Nếu cần lịch trong Jakarta EE, **DefaultManagedTaskScheduler** mới là adapter đúng.

### Vì sao không luôn dùng ThreadPoolTaskScheduler?

`ThreadPoolTaskScheduler` rất hợp lý trong ứng dụng độc lập. Nó cho phép cấu hình pool size, thread-name prefix, error handler, shutdown behavior bằng bean properties. Nhưng nó sở hữu local pool và có thể không tuân thủ chính sách threading của một Jakarta EE server nghiêm ngặt. Spring reference khuyến nghị managed scheduler khi không nên tự tạo thread trong application server.

### Embedded servlet container có đồng nghĩa Jakarta EE full runtime không?

Không. Một ứng dụng Spring Boot chạy với embedded Tomcat/Jetty thường **không có** Jakarta Concurrency default managed scheduled executor bind tại `java:comp/DefaultManagedScheduledExecutorService` chỉ vì nó sử dụng servlet API `jakarta.servlet.*`. Nếu không có resource/JNDI infrastructure, `DefaultManagedTaskScheduler` là lựa chọn không phù hợp; dùng `ThreadPoolTaskScheduler` hoặc một executor adapter được cấu hình rõ.

Ngược lại, trên Jakarta EE application server đầy đủ, đừng tự động tạo unmanaged pool chỉ để bỏ qua bước xác minh JNDI vì nó chạy được ở local.

## 14. Ví dụ Java 17+ độc lập để kiểm tra API scheduled executor

`DefaultManagedTaskScheduler` **không thể được chứng minh đúng môi trường Jakarta EE** chỉ bằng một chương trình JDK thuần. Để có ví dụ chạy được và dễ kiểm chứng, phần này dùng **`ScheduledExecutorService` local** — chính là dạng API mà managed scheduled executor mở rộng. Demo giúp thực hành một lần, hủy trước khi chạy và lịch định kỳ; sau đó phải test riêng bean managed scheduler trên server thật.

Tệp **ScheduledExecutorContractDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScheduledExecutorContractDemo {

    private static void ensure(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        CountDownLatch release = new CountDownLatch(1);

        try {
            AtomicBoolean oneShotRan = new AtomicBoolean();
            ScheduledFuture<?> oneShot = scheduler.schedule(
                    () -> oneShotRan.set(true),
                    0, TimeUnit.MILLISECONDS);
            oneShot.get(5, TimeUnit.SECONDS);
            System.out.println("oneShot=" + oneShotRan.get());

            CountDownLatch blockerStarted = new CountDownLatch(1);
            Future<?> blocker = scheduler.submit(() -> {
                blockerStarted.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });

            ensure(blockerStarted.await(5, TimeUnit.SECONDS),
                    "Blocker did not start");

            AtomicBoolean cancelledBodyRan = new AtomicBoolean();
            ScheduledFuture<?> queued = scheduler.schedule(
                    () -> cancelledBodyRan.set(true),
                    0, TimeUnit.MILLISECONDS);

            boolean cancelled = queued.cancel(false);
            release.countDown();

            blocker.get(5, TimeUnit.SECONDS);
            scheduler.schedule(() -> {},
                    0, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS);

            System.out.println("cancelBeforeStart=" + cancelled);
            System.out.println("cancelledBodyRan=" + cancelledBodyRan.get());

            AtomicInteger count = new AtomicInteger();
            CountDownLatch thirdRun = new CountDownLatch(3);

            ScheduledFuture<?> periodic = scheduler.scheduleAtFixedRate(
                    () -> {
                        int current = count.incrementAndGet();
                        if (current <= 3) {
                            thirdRun.countDown();
                        }
                    },
                    0, 10, TimeUnit.MILLISECONDS);

            ensure(thirdRun.await(5, TimeUnit.SECONDS),
                    "Periodic task did not run three times");

            boolean cancelledPeriodic = periodic.cancel(false);
            System.out.println("periodicAtLeastThree=" + (count.get() >= 3));
            System.out.println("periodicCancelled=" + cancelledPeriodic);
        } finally {
            release.countDown();
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ScheduledExecutorContractDemo.java
java ScheduledExecutorContractDemo
~~~

Stdout được kiểm soát bằng latch/Future:

~~~text
oneShot=true
cancelBeforeStart=true
cancelledBodyRan=false
periodicAtLeastThree=true
periodicCancelled=true
~~~

**Vì sao output xác định?**

1. `oneShot.get(timeout)` chỉ trả về sau khi runnable đầu tiên hoàn tất, nên `oneShot=true`.
2. `blockerStarted.await` chứng minh single worker đang bị giữ; task queued chưa có thread chạy, nên `cancel(false)` thành công. Marker task bảo đảm worker đã đi qua phần queue cần xác nhận; body bị hủy chưa từng chạy.
3. `thirdRun` đếm ít nhất ba executions của periodic task; không in số lần thực tế để tránh phụ thuộc scheduler chạy nhanh hơn main. `cancel(false)` kết thúc periodic Future chưa hoàn thành bình thường; nó có thể ngăn các lần tiếp theo, nhưng không bảo đảm làm gián đoạn một body đang chạy.
4. `finally` đóng **local executor do demo tự tạo**. Trong ứng dụng Jakarta EE thật, **không được copy đoạn `shutdownNow()` để gọi trên managed scheduled executor của server**.

Demo này minh họa **JDK contract**, không mô phỏng JNDI, security/context propagation, Jakarta scheduling Trigger hoặc policies của application server.

## 15. Kiểm thử tích hợp bắt buộc trên Jakarta EE server

Muốn chứng minh `DefaultManagedTaskScheduler` hoạt động thực, test cần chạy trong hoặc tích hợp với **Jakarta EE runtime mục tiêu**, không chỉ một unit test Spring Boot local.

### Checklist

1. **JNDI resource:** kiểm tra server expose `java:comp/DefaultManagedScheduledExecutorService` hoặc tên do administrator cấp; kiểm tra nó thật sự triển khai `ManagedScheduledExecutorService` tương thích.
2. **Bean lookup:** khởi tạo Spring context với `DefaultManagedTaskScheduler`; startup không báo `NamingException`/`BeanCreationException`.
3. **Scheduler được chọn:** xác minh `@EnableScheduling` đang dùng bean `taskScheduler` hoặc cấu hình `SchedulingConfigurer` đúng, không dùng fallback local.
4. **One-shot:** lên lịch task một lần, dùng `CountDownLatch` và `ScheduledFuture.get(timeout)` để xác nhận có chạy.
5. **Fixed-rate/fixed-delay:** lên lịch task ngắn với period/delay nhỏ trong môi trường test và hủy future trong `finally`; không dùng sleep ngẫu nhiên.
6. **Cron/Trigger:** test múi giờ và lịch trigger; kiểm tra Spring-to-Jakarta trigger adapter với version/server thật.
7. **Component context:** trong task kiểm tra naming/classloader/security theo hợp đồng của server; không khẳng định MDC/transaction tự kế thừa.
8. **Transaction:** xác minh task không tự tham gia transaction từ thread submit, và transactional service riêng có thể mở transaction theo Spring/JTA.
9. **Error path:** ném exception có kiểm soát, xác minh error handler/logging và chính sách chạy lại periodic task của container, không mặc định giống local executor.
10. **Overload/rejection:** dùng resource có quota trong môi trường kiểm thử để xác nhận cách server phản ứng; không ghi giả số liệu max pool hoặc queue.
11. **Redeploy/shutdown:** xem task của application component cũ được xử lý ra sao; không để job cũ thao tác trên classloader đã unload.
12. **Multi-instance:** deploy nhiều replica/nodes nếu môi trường yêu cầu, kiểm tra mỗi instance có schedule độc lập hay application server thực hiện cơ chế cluster riêng.

### Một test có latch

Giả sử đã inject `TaskScheduler scheduler` từ Spring:

~~~java
CountDownLatch received = new CountDownLatch(1);
ScheduledFuture<?> future = scheduler.schedule(
        received::countDown,
        java.time.Instant.now().plusSeconds(1));

try {
    if (!received.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("Managed task did not execute");
    }
} finally {
    if (future != null) {
        future.cancel(false);
    }
}
~~~

Đây là **fragment integration test**, cần imports `CountDownLatch`, `ScheduledFuture`, `TimeUnit` và context/container đã cấu hình. Nó chỉ xác nhận lịch chạy; muốn chứng minh **managed context**, phải kiểm tra thuộc tính context/JNDI và evidence từ server. Không dùng `Thread.sleep(1000)` rồi đoán rằng worker đã chạy.

### Không được “làm giả” managed scheduler bằng startup local

Một unit test có thể inject stub/mock `TaskScheduler` để kiểm tra logic ứng dụng, hoặc sử dụng `ThreadPoolTaskScheduler` trong local profile để chạy nhanh. Nhưng test đó **không chứng minh** lookup JNDI, lifecycle và Jakarta EE context. Nên tách:

- **Unit test:** logic task, tính idempotent, exception handling, cron expression validation.
- **Integration test:** JNDI lookup, actual managed executor, server lifecycle, context propagation và scheduler selection.

Đây là một phần quan trọng của quyết định lựa chọn scheduler theo **môi trường deploy**, không phải theo sự tiện lợi của bài unit test.

## 16. Lỗi, exception và sự cố scheduling

### Lỗi lúc bean startup

Nếu default JNDI name không tồn tại, `DefaultManagedTaskScheduler.afterPropertiesSet` có thể ném `NamingException`; khi Spring khởi tạo bean, lỗi được bao thành startup failure. Khắc phục bằng cách:

- Xác minh ứng dụng chạy trên Jakarta EE runtime có resource tương thích.
- Đối chiếu JNDI naming tree/descriptor và tên `setJndiName`.
- Kiểm tra quyền truy cập JNDI resource, deployment context và classloader.
- Kiểm tra namespace `javax`/`jakarta` và version Spring/Jakarta EE.
- Nếu là Spring Boot standalone không có resource, chọn local scheduler đúng mục tiêu thay vì dựng một JNDI giả để qua startup.

### Lỗi trong task

Periodic task có thể ném exception. Spring scheduler có cơ chế error handler, trong khi managed delegate và server có chính sách xử lý exception riêng. Không tự cho rằng exception được ném ngược tới caller đã đăng ký lịch; caller từ lâu có thể đã thoát.

Cần thiết kế:

- Log/metric có correlation/job ID, không ghi payload nhạy cảm.
- Error policy rõ: log, stop schedule, retry, dead-letter hoặc chuyển trạng thái trong job store.
- Giới hạn timeout cho I/O, tránh một lần thực thi bị treo chiếm managed worker.
- Cân nhắc `ScheduledFuture.get()` chỉ cho one-shot hoặc khi periodic đã hoàn thành/hủy; `get()` trên periodic task đang chạy thường block vô hạn.

`ConcurrentTaskScheduler` có `setErrorHandler(ErrorHandler)` ở Spring adapter; khi dùng, phải test tác động thực của handler trong periodic scheduling và managed delegate. Đừng nuốt exception để mọi lần lỗi lặp vô hạn mà không báo cáo.

### Rejection và quota

Container có thể áp giới hạn capacity, policies và lifecycle. Khi resource không nhận task, lời gọi schedule có thể thất bại. Spring không tự retry một cách bền vững. Để xử lý lịch quan trọng, lưu request/job bền vững và có cơ chế phát lại; không suy đoán task đã được nhận khi method schedule vừa bị exception.

### Đồng hồ và timezone

Lịch một lần dùng `Instant`, còn cron dùng calendar/timezone. Một JVM clock thay đổi, timezone/DST hoặc chính sách clock của server có thể ảnh hưởng cách tính lượt chạy. Cần log **scheduled time**, **actual start time**, **completion time** để phân tích drift; không so sánh mỗi số lần chạy tổng.

## 17. Multiple replicas, exactly-once và độ bền

`DefaultManagedTaskScheduler` **không phải clustered persistent scheduler**. Mỗi Spring application context/instance thường đăng ký lịch riêng. Nếu cùng ứng dụng chạy trên ba node và mỗi node đều có bean `@Scheduled`, có khả năng cả ba node cùng chạy job, trừ khi hạ tầng server/ứng dụng có một cơ chế cluster coordination được cấu hình cụ thể.

Không được suy ra: “managed by Jakarta EE” nghĩa là scheduler **chỉ chạy một lần trên toàn cụm**. Jakarta Concurrency quy định quality-of-service at-most-once cho **mỗi task submission** trong phạm vi managed executor, đồng thời các task mặc định mang tính **transient** và không sống qua process restart. Nó **không** đồng nghĩa global exactly-once của một business job trên nhiều replica.

Nếu cần một job duy nhất toàn cụm:

- Dùng distributed lock/lease có thời hạn và fencing token nếu phù hợp.
- Dùng database job table với claim atomically và retry/idempotency.
- Dùng Quartz với persistent JobStore/cluster mode nếu yêu cầu tương ứng.
- Dùng message broker/job queue và consumer coordination khi task thực chất là công việc cần phân phối.
- Định nghĩa idempotency key để task lặp không gây double-charge/double-notification.

Một lịch **đúng giờ** cũng không bảo đảm side effect thành công: job có thể fail, timeout hoặc app crash. Nên đo completion outcome riêng với số lần trigger.

### Restart và lịch bị mất

Scheduled task trong memory không tự được hồi phục qua shutdown/redeploy. Nếu một one-shot task được lên lịch cho 23:00 nhưng server restart lúc 22:59 và không có durable job store, lịch đó có thể mất. Với `@Scheduled` định kỳ, mỗi context mới có thể đăng ký lại lịch, nhưng đó là lịch mới sau startup, không phải phục hồi transaction/“missed executions” theo business contract.

## 18. Quan sát vận hành đúng trọng tâm

Với Jakarta EE, capacity/quota do administrator quản lý, nên metrics từ application server rất quan trọng. Ứng dụng nên đo thêm:

| Metric | Câu hỏi |
| --- | --- |
| `scheduled_jobs_registered` | Có bao nhiêu lịch được đăng ký? |
| `scheduled_task_started_total` | Có bao nhiêu lượt thật sự bắt đầu? |
| `scheduled_task_completed_total` | Có bao nhiêu lượt hoàn tất thành công? |
| `scheduled_task_failed_total` | Bao nhiêu lượt lỗi? |
| `scheduler_rejected_total` | Có bao nhiêu lần container không nhận lịch mới? |
| `schedule_start_delay` | Chậm bao lâu so với thời điểm mong muốn? |
| `task_execution_duration` | Mỗi lượt giữ worker trong bao lâu? |
| `cancel_requested_total` | Có bao nhiêu lịch/task được yêu cầu hủy? |
| `managed_executor_active_threads` | Container đang cấp bao nhiêu worker (nếu có metric vendor)? |
| `missed_business_deadline_total` | Có bao nhiêu job chậm so với SLA nghiệp vụ? |

Đừng tự bịa thread-pool metrics nếu vendor không expose hoặc lớp wrapper không có API tương ứng. Xem JMX/management API của application server. Gắn tags vừa đủ (job type, node, outcome), tránh label theo jobId duy nhất tạo cardinality quá lớn.

### Đo đúng ba mốc

~~~text
scheduled timestamp ------ actual start ------- actual completion
               drift                     processing duration
~~~

Nếu app chỉ log “job hoàn tất lúc 10:05”, khó phân biệt job được scheduler kích hoạt trễ hay job chạy quá lâu. Vì `DefaultManagedTaskScheduler` không tự lưu lịch chạy bền vững, telemetry nên là một phần của thiết kế ngay từ đầu.

## 19. Các lỗi phổ biến cần tránh

1. **Chạy DefaultManagedTaskScheduler trong Java SE/Spring Boot standalone** rồi kỳ vọng có JNDI resource chuẩn của Jakarta EE.
2. **Nhầm DefaultManagedTaskScheduler với DefaultManagedTaskExecutor.** Hai default JNDI name khác nhau; scheduler cần scheduled executor.
3. **Chỉ bật @EnableAsync để chạy @Scheduled.** Scheduling cần `@EnableScheduling` hoặc cơ chế tương đương.
4. **Có nhiều TaskScheduler bean nhưng không chỉ rõ loại dùng.** Spring có thể chọn sai hoặc fallback local.
5. **Gọi constructor rồi tưởng adapter tự tạo thread pool.** Nó lookup executor qua JNDI khi bean init.
6. **Tự gọi afterPropertiesSet() trong @Bean method.** BeanFactory đã gọi lifecycle hook.
7. **Đặt setJndiName sai hoặc dùng JNDI name không được bind.** Bean startup có thể lỗi.
8. **Bắt NamingException rồi âm thầm dùng local pool trong production Jakarta EE** mà không có quyết định kiến trúc về quản lý thread.
9. **Dùng javax.enterprise.concurrent.* với Spring 6/Jakarta mới mà không xem compatibility.** API namespace không tương đương.
10. **Gọi shutdown/shutdownNow trên managed executor.** Lifecycle do server sở hữu, ứng dụng không có quyền đóng nó.
11. **Cố setPoolSize trên DefaultManagedTaskScheduler.** Lớp wrapper không sở hữu pool kiểu ThreadPoolTaskScheduler.
12. **Coi default managed scheduled service tự bảo đảm bền vững.** Jakarta Concurrency task thông thường transient.
13. **Coi “at-most-once” tương đương exactly-once toàn cụm.** Không có ràng buộc đó.
14. **Coi managed thread tự mang transaction của request.** Task chạy ngoài transaction của submitter.
15. **Coi MDC/ThreadLocal bất kỳ đều được truyền tự động.** Specification chỉ có context cụ thể, các loại khác cần hỗ trợ riêng.
16. **Dùng @Scheduled cho gửi email/charge quan trọng mà không idempotency.** Node restart và multi-replica có thể làm thiếu/trùng.
17. **Dùng fixedRate cho task lâu mà không đo drift.** Tác vụ không thể đảm bảo chạy đúng thời điểm dưới overload.
18. **Nhầm fixedDelay với fixedRate.** Một loại dựa vào completion, một loại dựa vào chu kỳ mốc.
19. **Chọn cron mà quên zone/DST.** Kết quả khác kỳ vọng lúc chuyển múi giờ.
20. **Cho rằng @Scheduled đều chạy trên thread riêng khỏi application.** Phụ thuộc adapter/executor và cấu hình thực.
21. **Chỉ unit test với local ScheduledExecutorService rồi khẳng định Jakarta JNDI đã đúng.** Cần integration test trên runtime thật.
22. **Không xử lý task exception hoặc rejection.** Các lần sau có thể bị ảnh hưởng bởi implementation/policy.
23. **Dùng ScheduledFuture.get() ngay sau schedule periodic và block mãi.** Periodic future không hoàn thành bình thường mỗi lượt.
24. **Không dọn lịch theo lifecycle app** nhưng lại tự shutdown managed resource chung. Cần phân biệt hủy task riêng và hủy executor.
25. **Bỏ qua việc hủy/chạy khi redeploy.** Code cũ có thể không được phép chạy sau component stop.
26. **Đọc số thread local để quản trị managed executor của server.** Capacity policy thuộc server.
27. **Để nhiều application context đăng ký @Scheduled trùng.** Có thể nhân số lượt chạy ngoài dự kiến.
28. **Tưởng Docker/Kubernetes tự cấp Jakarta managed resource.** Container infrastructure không tương đương Jakarta EE application server.
29. **Xem TaskScheduler như durable job scheduler.** TaskScheduler chủ yếu là API lên lịch in-process.
30. **Tự động fallback không được phê duyệt khi JNDI hỏng.** Che sự cố deployment/quản trị và sai chính sách thread.

## 20. Quy trình chọn scheduler đúng môi trường

Hãy quyết định theo điều kiện vận hành:

1. **Runtime là gì?** Jakarta EE application server có managed scheduled executor không, hay Spring Boot standalone/embedded servlet?
2. **Ai có quyền quản lý thread?** Nếu application server yêu cầu managed threads, chọn managed resource đúng contract.
3. **Scheduler cần gì?** `@Scheduled`, cron, fixed-delay, dynamic scheduling, cancellation; có cần điều chỉnh riêng pool/queue không?
4. **JNDI name nào thực sự tồn tại?** Default chuẩn hay resource do administrator provision? Đọc tài liệu deployment đúng server/version.
5. **Spring chọn bean nào?** Đăng ký `taskScheduler` hoặc `SchedulingConfigurer` rõ ràng; tránh hai đường fallback mơ hồ.
6. **Transaction/security context nào cần thiết?** Managed executor có component context nhưng không tự kế thừa caller transaction; thiết kế bean transactional riêng.
7. **Có yêu cầu bền vững/cluster không?** Nếu có, đưa vào job store/Quartz/message queue, không chỉ phụ thuộc scheduler.
8. **Kiểm thử ở đâu?** Unit test logic với local/mock; integration test JNDI và managed execution trên server thực.
9. **Quan sát thế nào?** Log run ID, planned/actual time, kết quả, duration, rejection và JVM/node.
10. **Rollback khi cấu hình lỗi?** Chuẩn bị deployment config/health check; không chuyển sang unmanaged threads âm thầm.

### Bảng quyết định nhanh

| Bối cảnh | Lựa chọn khởi đầu |
| --- | --- |
| Jakarta EE application server, có JSR-236/Jakarta managed scheduled executor | **`DefaultManagedTaskScheduler`** |
| Spring Boot standalone, không có JNDI managed resource | **`ThreadPoolTaskScheduler`** |
| Có `ScheduledExecutorService` do bên khác tạo và sở hữu | **`ConcurrentTaskScheduler`** |
| Chỉ cần submit/execute qua managed executor | **`DefaultManagedTaskExecutor`** |
| Cần lịch bền vững/cluster/misfire recovery | **Persistent scheduler/job store**, có thể dùng Quartz |
| Có nhiều scheduler trong một context | **Đặt bean name/qualifier và SchedulingConfigurer rõ ràng** |
| Có yêu cầu không tự tạo thread trong app server | **Tránh local pool, ưu tiên managed resource** |

## 21. Câu hỏi tự kiểm tra

1. `DefaultManagedTaskScheduler` kế thừa class nào, và Spring dùng nó để giải quyết vấn đề gì?
2. JNDI name mặc định của nó là gì? Khác gì `java:comp/DefaultManagedExecutorService`?
3. Khi nào `afterPropertiesSet()` lookup resource? Vì sao không gọi bằng tay trong một `@Bean` do Spring quản lý?
4. `@EnableScheduling` khác `@EnableAsync` ở chỗ nào?
5. Khi nào nên đặt bean tên `taskScheduler` hoặc dùng `SchedulingConfigurer`?
6. Vì sao Spring Boot dùng embedded Tomcat không nhất thiết có default Jakarta Concurrency resource?
7. Phân biệt fixed-rate, fixed-delay và cron trong một task chạy lâu hơn chu kỳ.
8. `Spring Trigger` và `jakarta.enterprise.concurrent.Trigger` có phải một interface không?
9. Những loại component context nào được Jakarta Concurrency default managed scheduled executor hỗ trợ?
10. Vì sao worker không tự thừa hưởng transaction của thread submit? Nếu job cần DB transaction, bạn tổ chức Spring service thế nào?
11. Ai sở hữu lifecycle của `ManagedScheduledExecutorService`? Ứng dụng có được gọi `shutdownNow()` không?
12. Vì sao `@Scheduled` chạy được chưa đủ chứng minh Spring đang dùng `DefaultManagedTaskScheduler`?
13. Một scheduled future có phải định danh job bền vững qua redeploy không?
14. Khi deploy ứng dụng ở ba node, managed scheduler có tự bảo đảm job chỉ chạy một lần toàn cụm không?
15. Vì sao cần test JNDI resource trong Jakarta EE server thật thay vì chỉ mock scheduler trong local?
16. Một transaction tài chính cần chạy đúng một lần và không mất khi restart có nên dùng riêng DefaultManagedTaskScheduler không?
17. Nếu `schedule` bị từ chối khi server quá tải, hệ thống phải có những bước báo lỗi/ghi nhận nào?
18. Vì sao không nên tự fallback sang `Executors.newScheduledThreadPool` trên Jakarta EE khi lookup JNDI lỗi?

## 22. Tài liệu chính thức

- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2 — DefaultManagedTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/DefaultManagedTaskScheduler.html)
- [Spring Framework 6.2 — ConcurrentTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ConcurrentTaskScheduler.html)
- [Spring Framework — @EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html)
- [Spring Framework — TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Spring Framework — SchedulingConfigurer Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/SchedulingConfigurer.html)
- [Jakarta Concurrency Specification 3.1](https://jakarta.ee/specifications/concurrency/3.1/jakarta-concurrency-spec-3.1)
- [Jakarta EE — Concurrency Explained](https://jakarta.ee/learn/specification-guides/concurrency-explained/)
- [Java SE 17 — ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)
- [Java SE 17 — ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html)

**Ghi nhớ:** `DefaultManagedTaskScheduler` là **Spring TaskScheduler adapter dựa trên JNDI**, mặc định tra `java:comp/DefaultManagedScheduledExecutorService` của Jakarta EE. Chọn nó khi application server **sở hữu thread và lifecycle**; để `@Scheduled` dùng nó, phải cấu hình đúng Spring bean và `@EnableScheduling`. Không gọi shutdown trên managed executor, không giả định transaction tự truyền, và không nhầm scheduled tasks trong RAM với một **persistent/clustered job scheduler**. Khi chạy standalone không có JNDI, chọn local scheduler có quản trị rõ ràng thay vì giả định môi trường Jakarta EE.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduler-selection-006">SimpleAsyncTaskScheduler tách cơ chế kích hoạt với cách chạy tác vụ</a>

<details>
<summary>Xem chi tiết</summary>

# SimpleAsyncTaskScheduler tách cơ chế kích hoạt với cách chạy tác vụ

## 1. Vì sao cần hiểu hai vai trò khác nhau?

Một công việc theo lịch không chỉ có câu hỏi **“mấy giờ chạy?”**. Nó còn có câu hỏi **“chạy trên thread/executor nào?”**, **“chạy bao nhiêu bản cùng lúc?”**, **“khi công việc chậm thì các lịch khác bị ảnh hưởng ra sao?”** và **“khi shutdown thì task đang chạy có được đợi hay không?”**.

Trong Spring Framework 6.1+, `SimpleAsyncTaskScheduler` cung cấp một mô hình đặc biệt: **một scheduler thread chịu trách nhiệm xác định và kích hoạt các lần chạy theo lịch**, còn những lần chạy phù hợp được **bàn giao** sang thread thực thi riêng hoặc `Executor` đích. Khi sử dụng Java 21+, nó đặc biệt phù hợp với **virtual threads**: thay vì phải giữ một pool platform threads cho nhiều I/O tasks, có thể tạo một virtual thread cho mỗi lần kích hoạt.

Điểm quan trọng không được quên: **`fixedDelay` là ngoại lệ**. Với `SimpleAsyncTaskScheduler`, các tác vụ fixed-delay được thực hiện trên **chính scheduler thread** để bảo toàn ngữ nghĩa “đợi xong rồi mới tính khoảng delay tiếp theo”. Vì vậy, nói “mọi `@Scheduled` task đều được tách ra thread riêng” là **sai**.

Sau bài học, bạn cần:

1. Vẽ được đường đi từ `@Scheduled`/`TaskScheduler` tới scheduler trigger thread và worker thực thi.
2. Nói đúng khác biệt giữa **thời điểm kích hoạt**, **bàn giao công việc** và **hoàn tất công việc**.
3. Giải thích tại sao `fixedRate`/cron phù hợp hơn `fixedDelay` với scheduler kiểu thread-per-task.
4. Cấu hình `SimpleAsyncTaskScheduler` trong Spring Framework và bật virtual threads trên Java 21+.
5. Biết khi nào sử dụng `setTargetTaskExecutor(...)` để bàn giao sang một executor có giới hạn.
6. Phân biệt với `ThreadPoolTaskScheduler` và cách Spring Boot tự cấu hình theo điều kiện.
7. Tránh hiểu nhầm `ScheduledFuture`, cancellation, lifecycle, overlapping runs và khả năng bảo đảm công việc.
8. Viết/chạy ví dụ Java 17+ và ví dụ Spring Java 21+ có kết quả quan sát được, đồng thời biết cách kiểm thử scheduling mà không dựa vào thứ tự tình cờ.

**Phạm vi:** lựa chọn scheduler và phân tách *trigger scheduling* khỏi *task execution* trong Spring concurrency; không biến local scheduler thành durable job queue hay distributed orchestrator.

## 2. Hai tầng cần phân biệt

### 2.1. Trigger: khi nào tới lượt công việc?

Scheduler quyết định lần kích hoạt dựa trên những thông tin như:

- Một thời điểm `Instant` cụ thể;
- Khoảng thời gian `fixedRate`;
- Khoảng nghỉ `fixedDelay` tính từ lúc lần trước kết thúc;
- Biểu thức `cron`, múi giờ và quy tắc lịch;
- `Trigger` tùy chỉnh cùng lịch sử thực thi;
- Thời gian hiện hành do `Clock` của scheduler cung cấp.

Nhiệm vụ của tầng trigger không nhất thiết là làm công việc nghiệp vụ. Nó phải **giữ lịch**, xác định thời điểm và khởi phát đúng contract.

### 2.2. Execution: ai thực sự chạy callback?

Khi một lần kích hoạt đã đến, callback có thể:

- Chạy **ngay trên scheduler thread** (cách phổ biến của một số scheduler dùng thread pool);
- Được bàn giao cho **một thread mới cho mỗi lần chạy**;
- Chạy trên **virtual thread** riêng trong Java 21+;
- Được submit tới **executor tùy chỉnh** như `ThreadPoolTaskExecutor`;
- Bị delay, bị reject hoặc phải chờ quota nếu executor/giới hạn đồng thời không nhận thêm task.

`SimpleAsyncTaskScheduler` được thiết kế để tách hai vai trò này cho các tác vụ phù hợp với mô hình bàn giao.

### 2.3. Sơ đồ kiến trúc

```text
                        Spring ApplicationContext
                                   |
                              @EnableScheduling
                                   |
                      @Scheduled / TaskScheduler API
                                   |
                                   v
                  +--------------------------------+
                  | SimpleAsyncTaskScheduler       |
                  | 1 scheduler/trigger thread     |
                  | - quyết định next fire         |
                  | - xử lý lịch theo thời gian    |
                  +--------------------------------+
                        |                 |
                  fixedRate/cron      fixedDelay
                        |                 |
                        v                 v
                  bàn giao task       chạy ngay trên
                        |             scheduler thread
                +-------+-------+         |
                |               |         +-- task dài có thể
                v               v             giữ trigger thread
          thread mới         target Executor
          mỗi lần chạy       (nếu cấu hình)
                |
          platform thread
          hoặc virtual thread
          với Java 21+
```

**Không có một pool cố định ẩn bên trong `SimpleAsyncTaskScheduler` chỉ vì tên class có chữ “Scheduler”.** Bản mặc định dùng cơ chế từ `SimpleAsyncTaskExecutor` tạo một thread mới cho mỗi lần thực thi được bàn giao, thay vì tái sử dụng một tập worker cố định. Với `setTargetTaskExecutor(...)`, phần thực thi có thể được chuyển sang pool được chỉ định.

## 3. Điểm cốt lõi trong Javadoc Spring

Spring Framework giới thiệu `SimpleAsyncTaskScheduler` từ **6.1**. Các đặc điểm quan trọng:

- Nó implement `TaskScheduler`, kế thừa `SimpleAsyncTaskExecutor` và tham gia lifecycle dưới dạng `SmartLifecycle`.
- Có **một scheduler thread** phục vụ việc kích hoạt lịch.
- Với tác vụ phù hợp, mỗi lần kích hoạt được **bàn giao cho thread thực thi riêng**, không dùng mô hình thread pool tái sử dụng theo mặc định.
- `setVirtualThreads(true)` dùng **virtual threads** trên Java 21+, là trường hợp sử dụng được khuyến khích của class này.
- `setTargetTaskExecutor(Executor)` cho phép bàn giao công việc tới một `Executor` khác, ví dụ `ThreadPoolTaskExecutor` dùng chung.
- `setConcurrencyLimit(int)` có thể giới hạn số lần thực thi đồng thời theo cơ chế của `SimpleAsyncTaskExecutor`. Mặc định không có giới hạn hữu hạn được áp dụng theo kiểu thread pool; phải tự thiết kế backpressure.
- Với **fixed-delay**, callback chạy trên **scheduler thread**, không áp dụng mô hình handoff như cron/fixed-rate.
- Hỗ trợ một số chính sách graceful shutdown/tracking khi cấu hình `setTaskTerminationTimeout(...)`, nhưng lifecycle của task đã bàn giao vẫn có giới hạn cần hiểu.
- `ScheduledFuture` trong mô hình bàn giao có thể phản ánh **việc trigger/handoff** chứ **không đồng nghĩa** với completion của công việc nghiệp vụ thực tế.

Đây là các đặc điểm của class, **không** phải bảo đảm mọi lần chạy sẽ bắt đầu đúng từng mili giây theo wall clock. JVM GC pause, OS scheduling, hàng đợi executor, CPU, network và clock thay đổi đều có thể làm lệch thời điểm thực tế.

### 3.1. Đừng nhầm ba mốc thời gian

```text
scheduled time          handoff accepted          task completed
      |                        |                        |
      v                        v                        v
   10:00:00                10:00:00.010             10:00:02.200
      |________________________|________________________|
          trigger latency            execution duration
                                     + queue wait nếu có
```

Tương ứng là:

1. **Due time:** khi lịch dự kiến phát tác vụ.
2. **Dispatch/handoff:** khi scheduler chấp nhận hoặc chuyển việc cho executor.
3. **Actual completion:** khi logic nghiệp vụ kết thúc, thành công hoặc lỗi.

Một `ScheduledFuture` của `SimpleAsyncTaskScheduler` **không phải công cụ đáng tin để đo actual completion của callback đã handoff**. Nếu cần biết job đã xử lý xong, phải tự ghi nhận bằng `CompletableFuture`, latch, trạng thái job hoặc hệ thống quan sát phù hợp.

## 4. Fixed rate, cron và fixed delay: khác biệt mang tính kiến trúc

### 4.1. Fixed rate

`@Scheduled(fixedRate = 1000)` yêu cầu phát lần chạy theo **nhịp khoảng một giây** theo contract scheduling, không phụ thuộc khoảng nghỉ sau lần chạy trước như fixed delay. Với `SimpleAsyncTaskScheduler`, một lần chạy chậm có thể **chưa kết thúc** khi lần kích hoạt tiếp theo đến; một lần khác có thể được bàn giao và chạy đồng thời.

```text
Fixed-rate, period = 1s, work duration ~2.5s:

trigger:  |0s|1s|2s|3s|4s|...
run A:   [-----------2.5s----]
run B:       [-----------2.5s----]
run C:           [-----------2.5s----]
```

Đây là sơ đồ tư duy, không phải lịch trình cứng của JVM. Số lượng run cùng lúc phụ thuộc execution strategy, quota, task duration và scheduler runtime.

**Hệ quả:** nếu công việc không cho phép chồng lấn, phải tự có guard, queue, lock, concurrency policy hoặc chọn scheduler/trigger khác phù hợp. Không nghĩ `@Scheduled` tự đảm bảo mỗi job chỉ có một instance ở bất kỳ thời điểm nào.

### 4.2. Cron

`@Scheduled(cron = "0 */5 * * * *", zone = "UTC")` biểu diễn mốc lịch theo cron và múi giờ. Cron hữu ích cho “vào thời điểm giờ/phút cụ thể”, khác bản chất với “mỗi N mili giây”.

Với mô hình handoff, cron có thể có nhiều lần kích hoạt trong khi một callback trước đó vẫn đang làm việc. Đừng dựa vào thứ tự completion; phải kiểm soát trùng lặp, idempotency và quản lý tài nguyên.

Lưu ý DST/múi giờ, thời điểm app không hoạt động và clock drift. Cron trong **local Spring scheduler** không có nghĩa JVM tự bù mọi lần trigger đã lỡ khi process tắt; yêu cầu catch-up phải được thiết kế như job scheduler bền vững.

### 4.3. Fixed delay: ngoại lệ cực kỳ quan trọng

`@Scheduled(fixedDelay = 1000)` yêu cầu đợi **một giây sau khi lần chạy trước hoàn thành** rồi mới kích hoạt tiếp. `SimpleAsyncTaskScheduler` thực hiện fixed-delay task ngay trên **một scheduler thread**, nhằm bảo toàn ngữ nghĩa completion-dependent.

```text
Fixed-delay, work duration 2.5s, delay 1s:

scheduler thread:
  run A [===========2.5s===========] wait 1s
  run B                                 [==========]
  run C sau run B + delay tương ứng
```

Điều này có nghĩa:

- Một fixed-delay callback dài/blocking có thể giữ **thread dùng chung cho kích hoạt lịch**, ảnh hưởng tới khả năng phục vụ trigger khác.
- Fixed-delay tasks trên cùng scheduler không có cơ chế bàn giao thread-per-task giống fixed-rate/cron.
- Bật virtual threads **không tự biến fixed-delay thành nhiều virtual threads độc lập**.
- Spring Javadoc khuyến nghị ưu tiên **fixed-rate hoặc cron** cho scheduler thread-per-task này khi phù hợp yêu cầu nghiệp vụ.

Đây không phải lỗi và cũng không nên “chữa” bằng cách đổi một con số `pool.size` không áp dụng. Đó là **sự đánh đổi giữa semantics và cơ chế thực thi**.

### 4.4. Bảng chọn nhanh

| Loại trigger | Nhịp chạy | Đường thực thi đặc trưng trên SimpleAsyncTaskScheduler | Nguy cơ cần xem |
| --- | --- | --- | --- |
| `schedule(task, Instant)` | Một thời điểm | Bàn giao task | Handoff không tương đương task completion |
| `fixedRate` | Theo nhịp định kỳ | Bàn giao thread/Executor | Có thể chồng lấn task và tích tụ tải |
| `cron` | Theo lịch mốc giờ | Bàn giao thread/Executor | Timezone, lịch chồng lấn, missed triggers |
| `fixedDelay` | Tính sau khi lần trước kết thúc | **Thực thi ngay trên scheduler thread** | Task chậm giữ scheduler thread |
| `Trigger` tùy chỉnh | Theo `nextExecution`/context | Phụ thuộc logic trigger và cách scheduler tính lần tiếp | Cần hiểu trigger context, completion vs handoff |

Với `Trigger` tùy chỉnh, không áp dụng máy móc mô hình fixedRate/cron vào mọi implementation: hãy đọc contract của trigger, `TriggerContext` và cách `TaskScheduler` tính lần chạy tiếp.

## 5. So sánh SimpleAsyncTaskScheduler với ThreadPoolTaskScheduler

| Khía cạnh | SimpleAsyncTaskScheduler | ThreadPoolTaskScheduler |
| --- | --- | --- |
| Cách giữ thời điểm kích hoạt | Một scheduler thread | `ScheduledThreadPoolExecutor` nền tảng |
| Cách chạy callback | Mặc định bàn giao task phù hợp tới thread mới; tùy chỉnh `Executor` đích | Thường callback chạy trên các scheduler pool threads |
| Reuse thread worker | Mặc định **không** | Có, tái sử dụng thread trong pool |
| Java 21 virtual threads | Hỗ trợ `setVirtualThreads(true)` | Pool platform threads theo cấu hình thông thường |
| Điều chỉnh pool size | Không phải pool fixed-worker mặc định | `setPoolSize(...)` |
| Điều chỉnh đồng thời | `setConcurrencyLimit` hoặc target executor và giới hạn downstream | Pool size, queue behavior của scheduled executor |
| Fixed-delay | Chạy trên scheduler thread | Giữ semantics delay trong mô hình pool |
| Task ngắn, liên tục, số lượng nhiều | Cân nhắc overhead tạo thread nếu chưa bật virtual threads | Pool có thể tiết kiệm overhead |
| Tác vụ blocking I/O đông, ngắn đến vừa | Virtual-thread execution có thể phù hợp sau khi giới hạn downstream | Pool có thể cần nhiều platform threads |
| Bằng chứng completion | Handoff future và completion công việc cần phân biệt | Cần vẫn đối chiếu semantics của future/trigger |
| Shutdown | SmartLifecycle, tracking phụ thuộc cấu hình | Shutdown theo chính sách pool của Spring |

Không kết luận một bên “nhanh hơn” chỉ vì ít thread hay có virtual threads. CPU-bound tasks không chạy nhanh hơn số lõi vật lý chỉ bằng cách tạo vô số virtual threads. Blocking I/O có thể tăng thông lượng khi được thiết kế giới hạn kết nối và memory phù hợp, nhưng cũng có thể làm DB/API downstream quá tải.

### 5.1. Khi nào chọn loại nào?

**Ưu tiên cân nhắc `SimpleAsyncTaskScheduler`** nếu:

- Dùng Java 21+ và muốn virtual threads cho tác vụ blocking I/O có thể xử lý độc lập.
- Có nhiều lịch fixed-rate/cron, muốn trigger thread không phải trực tiếp làm công việc lâu.
- Cần phân tách trigger timing với executor riêng để thực thi.
- Có admission control/timeout/idempotency và biết rõ yêu cầu overlap.
- Không phụ thuộc `fixedDelay` cho nhiều job chậm trên scheduler chung.

**Ưu tiên cân nhắc `ThreadPoolTaskScheduler`** nếu:

- Có nhiều task ngắn, tần suất cao và việc tạo thread-per-run không phù hợp.
- Muốn callback và scheduling dùng worker pool có số luồng cố định.
- Fixed-delay dài là cơ chế chủ đạo, cần hiểu pool contention rõ ràng.
- Cần điều chỉnh pool size theo tải đã đo.
- Môi trường dùng Java 17 hoặc không bật virtual threads và một pool ổn định là đủ.

**Cân nhắc scheduler chuyên dụng/durable job orchestration** nếu công việc phải sống qua restart, retry có trạng thái bền vững, chạy đúng một lần trên toàn cụm hay đòi hỏi distributed coordination. `@Scheduled` và `SimpleAsyncTaskScheduler` là công cụ in-process scheduling, không tự cung cấp những bảo đảm này.

## 6. Ví dụ hoàn chỉnh Spring Java 21+: kích hoạt một task trên virtual thread

Đây là **ví dụ thực sự dùng `SimpleAsyncTaskScheduler`**, không chỉ mô phỏng bằng Java `ScheduledExecutorService`. Để output có thể kiểm chứng mà không phụ thuộc vào nhịp cron, ứng dụng đăng ký một lần kích hoạt tức thời và chờ bằng `CountDownLatch`.

Yêu cầu: **JDK 21+**, **Maven**, **Spring Framework 6.2.14** (không cần Spring Boot).

Cấu trúc:

```text
simple-scheduler-demo/
├── pom.xml
└── src/main/java/demo/
    ├── VirtualTaskDemo.java
    └── TargetExecutorDemo.java
```

### 6.1. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>simple-scheduler-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>21</maven.compiler.release>
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

### 6.2. Tệp src/main/java/demo/VirtualTaskDemo.java

```java
package demo;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

public class VirtualTaskDemo {
    @Configuration(proxyBeanMethods = false)
    public static class SchedulerConfig {
        @Bean("taskScheduler")
        public SimpleAsyncTaskScheduler taskScheduler() {
            SimpleAsyncTaskScheduler scheduler =
                    new SimpleAsyncTaskScheduler();
            scheduler.setVirtualThreads(true);
            scheduler.setThreadNamePrefix("demo-work-");
            return scheduler;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             SchedulerConfig.class)) {
            SimpleAsyncTaskScheduler scheduler =
                    context.getBean(SimpleAsyncTaskScheduler.class);

            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean ranOnVirtual = new AtomicBoolean();

            scheduler.schedule(() -> {
                try {
                    ranOnVirtual.set(
                            Thread.currentThread().isVirtual());
                } finally {
                    done.countDown();
                }
            }, Instant.now());

            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "scheduled task did not run");
            }

            System.out.println(
                    "task-on-virtual-thread=" + ranOnVirtual.get());
        }
    }
}
```

### 6.3. Chạy và quan sát

Ở thư mục `simple-scheduler-demo`:

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.VirtualTaskDemo
```

Kỳ vọng **dòng từ chương trình** khi dùng đúng JDK 21+ và config trên:

```text
task-on-virtual-thread=true
```

Maven hoặc môi trường có thể ghi log riêng. `CountDownLatch` bảo đảm chương trình đợi **callback thật sự chạy**, chứ không kết luận dựa vào `ScheduledFuture.get()`. Bean scheduler do Spring quản lý và tham gia lifecycle của application context.

**Tại sao không có `@EnableScheduling`?** Ví dụ gọi trực tiếp API `scheduler.schedule(Runnable, Instant)`. `@EnableScheduling` dùng để kích hoạt processing các method `@Scheduled`; không bắt buộc cho một lệnh programmatic schedule trên bean scheduler đã được tạo và hoạt động.

**Tại sao phải dùng Java 21?** `Thread.currentThread().isVirtual()` và `setVirtualThreads(true)` trong Spring dựa vào khả năng virtual threads của JDK 21. Trên Java 17 không bật virtual threads; có thể dùng `SimpleAsyncTaskScheduler` ở chế độ platform-thread tương ứng hoặc chọn `ThreadPoolTaskScheduler`, nhưng không gọi `Thread.isVirtual()` khi biên dịch bằng Java 17.

### 6.4. Hạn chế của ví dụ

Ví dụ kiểm tra một lần `schedule`; nó **không** kiểm chứng cron, fixed-rate, fixed-delay hay tải lớn. Để khẳng định fixed-delay chạy trên scheduler thread, dựa trên Javadoc và thực hiện integration test riêng; không lấy output `task-on-virtual-thread=true` của one-shot task để suy rằng **fixed-delay** cũng chạy trên virtual worker riêng.

## 7. Ví dụ Spring Java 21+: bàn giao trigger sang target Executor tùy chỉnh

Mô hình mặc định tạo thread mới cho mỗi task có thể không phù hợp nếu bạn cần giới hạn số worker, hàng đợi hữu hạn và chính sách rejection. Khi đó dùng `setTargetTaskExecutor(Executor)` để bàn giao thực thi sang một pool đã cấu hình.

**Cùng pom.xml ở phần 6**, tạo tệp `src/main/java/demo/TargetExecutorDemo.java`:

```java
package demo;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class TargetExecutorDemo {
    @Configuration(proxyBeanMethods = false)
    public static class SchedulerConfig {
        @Bean("workPool")
        public ThreadPoolTaskExecutor workPool() {
            ThreadPoolTaskExecutor pool =
                    new ThreadPoolTaskExecutor();
            pool.setThreadNamePrefix("job-worker-");
            pool.setCorePoolSize(2);
            pool.setMaxPoolSize(4);
            pool.setQueueCapacity(8);
            return pool;
        }

        @Bean("taskScheduler")
        public SimpleAsyncTaskScheduler taskScheduler(
                ThreadPoolTaskExecutor workPool) {
            SimpleAsyncTaskScheduler scheduler =
                    new SimpleAsyncTaskScheduler();
            scheduler.setTargetTaskExecutor(workPool);
            return scheduler;
        }
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             SchedulerConfig.class)) {

            SimpleAsyncTaskScheduler scheduler =
                    context.getBean(SimpleAsyncTaskScheduler.class);

            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean usedPool = new AtomicBoolean();

            scheduler.schedule(() -> {
                try {
                    usedPool.set(Thread.currentThread()
                            .getName().startsWith("job-worker-"));
                } finally {
                    done.countDown();
                }
            }, Instant.now());

            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "scheduled callback did not run");
            }

            System.out.println(
                    "ran-on-target-pool=" + usedPool.get());
        }
    }
}
```

Chạy:

```bash
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.3.0:java -Dexec.mainClass=demo.TargetExecutorDemo
```

Dòng output mong đợi:

```text
ran-on-target-pool=true
```

**Giải thích:**

1. Spring tạo bean `workPool` kiểu `ThreadPoolTaskExecutor`; pool được khởi tạo và shutdown theo lifecycle container.
2. Spring tạo bean `taskScheduler` kiểu `SimpleAsyncTaskScheduler` và inject `workPool` vào factory method.
3. `setTargetTaskExecutor(workPool)` giữ trách nhiệm **kích hoạt lịch** tại scheduler, nhưng chuyển callback được bàn giao sang pool.
4. Callback kiểm tra prefix `job-worker-` để xác minh executor đích, không dựa vào việc đo thời gian ngẫu nhiên.
5. Số liệu core/max/queue chỉ là ví dụ. Khi pool đầy, việc submit có thể bị từ chối hoặc gặp chính sách backpressure, nên phải có error handling/metrics.
6. Chính sách target executor **không biến fixed-delay callback thành hoàn toàn độc lập với scheduler thread**: fixed-delay vẫn là ngoại lệ cần đặc biệt chú ý.

Đây là ví dụ **một lần `schedule`**, có stdout xác định; không nên suy từ nó rằng `fixedRate` hay cron luôn chạy ngay, không có queue wait hoặc không có overlap.

### 7.1. Bản chất của setTargetTaskExecutor

`setTargetTaskExecutor` không có nghĩa scheduler giờ sở hữu một pool bên trong. Bạn đã đưa một bean `Executor` **khác** vào làm nơi chạy callback. Có hai owner/lifecycle mà container cần quản lý:

```text
SimpleAsyncTaskScheduler (trigger thread)
               |
               | execute(task)
               v
ThreadPoolTaskExecutor (workers + queue)
               |
           business task
```

Nếu `workPool` bị shutdown, reject task hoặc tăng queue wait, scheduler không thể cam kết callback được thực thi đúng hạn dù trigger vẫn phát đúng lúc. Với một pool dùng chung giữa nhiều loại workload, sự cạnh tranh tài nguyên có thể làm méo SLA của tất cả chúng.

## 8. Ví dụ Java 17+ thuần: trigger handoff không đồng nghĩa work completion

Để hiểu sâu cơ chế **tách trigger khỏi worker** mà không cần phụ thuộc Spring/JDK 21, có thể mô phỏng nguyên lý bằng hai executor Java. **Đây là ví dụ tương tự về kiến trúc, không phải source code nội bộ của Spring.**

Tệp `TriggerHandoffDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TriggerHandoffDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService trigger =
                Executors.newSingleThreadScheduledExecutor(
                        task -> new Thread(task, "trigger-thread"));
        ExecutorService workers =
                Executors.newSingleThreadExecutor(
                        task -> new Thread(task, "worker-thread"));

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWork = new CountDownLatch(1);
        CountDownLatch workDone = new CountDownLatch(1);
        AtomicReference<String> triggerName = new AtomicReference<>();
        AtomicReference<String> workerName = new AtomicReference<>();

        try {
            trigger.schedule(() -> {
                triggerName.set(Thread.currentThread().getName());
                workers.execute(() -> {
                    workerName.set(Thread.currentThread().getName());
                    workerStarted.countDown();
                    try {
                        releaseWork.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        workDone.countDown();
                    }
                });
            }, 0, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS);

            if (!workerStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not start");
            }

            System.out.println("trigger=" + triggerName.get());
            System.out.println("work-done-at-handoff="
                    + (workDone.getCount() == 0));

            releaseWork.countDown();
            if (!workDone.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("work did not finish");
            }

            System.out.println("worker=" + workerName.get());
            System.out.println("work-done-after-release="
                    + (workDone.getCount() == 0));
        } finally {
            releaseWork.countDown();
            trigger.shutdownNow();
            workers.shutdownNow();
        }
    }
}
```

Lệnh chạy:

```bash
javac TriggerHandoffDemo.java
java TriggerHandoffDemo
```

Output:

```text
trigger=trigger-thread
work-done-at-handoff=false
worker=worker-thread
work-done-after-release=true
```

Vì `ScheduledFuture.get(...)` ở đây chỉ đợi **Runnable trên trigger executor** hoàn tất việc gọi `workers.execute`, nó có thể trở về trước khi business task xong. Đây là **minh họa có chủ đích** của sự khác nhau giữa handoff completion và task completion. Trong `SimpleAsyncTaskScheduler`, Javadoc cũng cảnh báo một số `ScheduledFuture` đại diện việc **bàn giao**, không phải completion của target task đã được bàn giao.

### 8.1. Nếu cần chính xác công việc đã hoàn thành

Phải theo dõi **outcome của callback thật**:

- `CountDownLatch` cho một test một lần chạy;
- `CompletableFuture<Result>` do business code sở hữu;
- Durable job status nếu công việc có thể sống qua process restart;
- Metrics/traces theo operation ID;
- Thành công hoặc lỗi của side effect trong nguồn dữ liệu authoritative.

Đừng chỉ gọi `scheduledFuture.get()` rồi ghi “đã hoàn tất gửi email” nếu nó chỉ phản ánh trigger/handoff.

## 9. Gắn với @EnableScheduling và @Scheduled

Cấu hình thông thường trong Spring Framework:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
    @Bean("taskScheduler")
    SimpleAsyncTaskScheduler taskScheduler() {
        SimpleAsyncTaskScheduler scheduler =
                new SimpleAsyncTaskScheduler();
        scheduler.setVirtualThreads(true); // Java 21+
        return scheduler;
    }
}
```

Đây là **fragment cấu hình** trong project Spring Java 21+, không phải file runnable riêng. `@EnableScheduling` bật infrastructure đọc `@Scheduled`; bean `taskScheduler` cung cấp implementation cụ thể.

Các method sau minh họa ba loại lịch:

```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledJobs {
    @Scheduled(fixedRate = 1000)
    public void pollFixedRate() {
        // Có thể được bàn giao sang task thread.
        pollRemoteService();
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void exportCron() {
        // Được kích hoạt theo mốc lịch và timezone.
        exportReport();
    }

    @Scheduled(fixedDelay = 1000)
    public void legacyFixedDelay() {
        // SimpleAsyncTaskScheduler chạy đoạn này trên scheduler thread.
        // Tránh chặn thread bằng I/O dài.
        doShortWork();
    }
}
```

Đây là **snippet minh họa**, cần định nghĩa `pollRemoteService`, `exportReport` và `doShortWork`. Spring xử lý việc đăng ký lịch qua `@EnableScheduling`. Trong một application context có nhiều candidate `TaskScheduler`, cần hiểu lookup tên/quy tắc registration hoặc dùng `SchedulingConfigurer` để chọn minh bạch; chỉ tạo một bean kiểu bất kỳ không tự đảm bảo tất cả `@Scheduled` dùng nó.

### 9.1. Lịch khởi tạo có thể chạy trước khi bạn nghĩ

`@Scheduled` được đăng ký trong quá trình Spring container khởi tạo/refresh. Nếu method job truy cập dữ liệu chưa sẵn sàng hoặc external service còn khởi động, nó có thể lỗi từ đầu. Cần readiness gating, startup delay, dependency state hoặc cơ chế lifecycle phù hợp. `initialDelay` hỗ trợ hoãn lần đầu nhưng không thay thế ready check và không tự giải quyết distributed coordination.

### 9.2. Có cần @EnableAsync không?

**Không** nếu chỉ dùng `@Scheduled` và `SimpleAsyncTaskScheduler`: scheduler đã định nghĩa execution policy cho callback được kích hoạt. `@EnableAsync` là cơ chế xử lý **method `@Async`** qua proxy, khác với `@EnableScheduling`.

Bạn có thể dùng cả hai trong ứng dụng, nhưng không nên dùng `@Async` như một “miếng vá” thiếu hiểu biết để ép fixed-delay chạy nhanh. Nếu method `@Scheduled(fixedDelay=...)` chỉ gọi một `@Async` method khác rồi trả ngay, “delay sau completion” có thể chỉ tính từ lúc **submit**, không phải khi công việc nghiệp vụ xong; như vậy semantics fixed-delay theo business đã thay đổi.

## 10. Spring Boot và virtual threads: cơ chế mặc định có điều kiện

Spring Boot có thể auto-configure scheduler khi application cần scheduling và thỏa các điều kiện về classpath/bean đã tồn tại. **Trong Spring Boot 3.3.x**, tài liệu mô tả:

- Khi dùng Java 21+ và đặt `spring.threads.virtual.enabled=true`, scheduler được Boot auto-configure có thể là `SimpleAsyncTaskScheduler` dùng virtual threads.
- Khi không bật virtual threads, auto-configured scheduler thường là `ThreadPoolTaskScheduler` với số worker mặc định nhỏ; `spring.task.scheduling.pool.size` là property tinh chỉnh cho trường hợp pool.
- Với `SimpleAsyncTaskScheduler` virtual threads, những **pooling-related properties bị bỏ qua** vì đây không phải pool worker cố định.
- Nếu ứng dụng khai báo scheduler/bean có kiểu thuộc điều kiện loại trừ, Boot có thể **back off**; không kết luận scheduler hiện hữu chỉ bằng đọc YAML.

Ví dụ `application.yml` cho ứng dụng Spring Boot 3.3.x Java 21+:

```yaml
spring:
  threads:
    virtual:
      enabled: true
  main:
    keep-alive: true
  task:
    scheduling:
      thread-name-prefix: scheduled-
```

`spring.main.keep-alive=true` có thể quan trọng đối với ứng dụng chỉ làm background scheduling khi virtual threads là daemon và process không có non-daemon thread khác giữ JVM sống. Kiểm tra môi trường triển khai và phiên bản Boot thực tế.

**Không nên đặt `spring.task.scheduling.pool.size=20` rồi nghĩ sẽ nhận 20 virtual workers**. Nếu scheduler là `SimpleAsyncTaskScheduler` virtual-thread mode thì property pool-size không điều chỉnh mô hình thread-per-task tương ứng. Cần xác nhận loại bean runtime.

### 10.1. Spring Framework không đồng nghĩa Spring Boot auto-configuration

- **Spring Framework** cung cấp `TaskScheduler`, `SimpleAsyncTaskScheduler`, `@EnableScheduling` và các API `@Bean` để bạn tự cấu hình.
- **Spring Boot** có thể dựng các bean và bind properties **theo điều kiện** cho bạn.
- Dù dùng Boot, `@Scheduled` vẫn cần infrastructure scheduling được bật theo cách thích hợp, ví dụ `@EnableScheduling`.
- Thêm custom `SimpleAsyncTaskScheduler` trong Boot có thể làm logic auto-configuration không còn tạo scheduler mặc định.

Tài liệu tham chiếu phải gắn đúng phiên bản; một release Boot tương lai có thể có điều kiện và builder API khác.

## 11. Số lượng task đồng thời và giới hạn tài nguyên

Mô hình thread-per-task đem lại khả năng tạo nhiều nhiệm vụ concurrent, nhưng **không đồng nghĩa khả năng hệ thống vô hạn**. Xét:

- 500 trigger mỗi giây;
- Mỗi task giữ DB connection trong 2 giây;
- DB pool có 20 connections;
- HTTP downstream giới hạn 50 requests/giây.

Nếu scheduler tiếp tục bàn giao mà không có giới hạn, queue ở DB/client hoặc lượng task đang chờ có thể tích lũy. Virtual threads giảm chi phí thread chờ so với platform threads trong nhiều trường hợp, nhưng **không tạo thêm connection, CPU, network bandwidth hoặc khả năng xử lý downstream**.

### 11.1. setConcurrencyLimit

`SimpleAsyncTaskScheduler` kế thừa `setConcurrencyLimit(int)` từ `SimpleAsyncTaskExecutor`. Đặt một concurrency limit có thể khiến quá trình nộp task **phải chờ slot** khi giới hạn được chạm. Đây là cơ chế **throttling**, không phải một bounded work queue với đủ metrics như `ThreadPoolTaskExecutor`.

**Cảnh báo:** nếu quota làm scheduler thread phải chờ ngay lúc bàn giao, trigger latency của lịch khác có thể tăng. Đo behavior thực tế trước khi bật trong hệ có nhiều lịch quan trọng.

### 11.2. Target Executor với bounded queue

Một `ThreadPoolTaskExecutor` cho phép chọn core/max/queue/rejection phù hợp. Nhưng việc tách executor không tự bảo đảm tránh backlog:

- Queue quá lớn → độ trễ cao và memory pressure.
- Queue quá nhỏ → reject và nguy cơ bỏ job nếu không xử lý.
- Một task blocking quá lâu → worker bị giữ, tăng queue wait.
- Rejection/cancellation của job cần phản hồi rõ trong logs/metrics.

Với công việc không được mất, dùng durable queue thay vì hy vọng in-memory queue đủ.

### 11.3. Chống overlap trong một JVM

Nếu cùng một job **không được chồng lấn**, một guard local có thể chặn lần chạy mới khi lần trước chưa xong. Tệp Java 17+ sau chỉ minh họa gate; nó **không** tự thực hiện scheduling của Spring.

Tệp `OverlapGuardDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class OverlapGuardDemo {
    public static void main(String[] args) throws Exception {
        AtomicBoolean inFlight = new AtomicBoolean(false);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();

        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        Runnable tick = () -> {
            if (!inFlight.compareAndSet(false, true)) {
                skipped.incrementAndGet();
                return;
            }
            accepted.incrementAndGet();
            worker.execute(() -> {
                try {
                    started.countDown();
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlight.set(false);
                    finished.countDown();
                }
            });
        };

        try {
            tick.run();

            if (!started.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not start");
            }
            tick.run(); // Lần kích hoạt thứ hai lúc job đầu chưa xong.

            System.out.println("accepted=" + accepted.get());
            System.out.println("skipped=" + skipped.get());

            release.countDown();
            if (!finished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("worker did not finish");
            }
            System.out.println("in-flight-after=" + inFlight.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }
}
```

Chạy:

```bash
javac OverlapGuardDemo.java
java OverlapGuardDemo
```

Output:

```text
accepted=1
skipped=1
in-flight-after=false
```

`AtomicBoolean.compareAndSet` chặn một lần chạy **trong cùng JVM** khi task cũ vẫn còn chạy. Lưu ý:

- Guard phải được reset trong `finally` để không khóa job mãi sau exception.
- Trong ví dụ này `worker.execute` giả định nhận task; production cần xử lý rejection và reset `inFlight` nếu submit thất bại.
- Nếu có **nhiều replica**, mỗi JVM có gate riêng, nên nhiều instance vẫn có thể chạy đồng thời. Dùng distributed lock/lease có fencing token hoặc durable job coordination khi cần.
- Nếu không muốn **skip**, có thể chọn **queue** và xử lý lần lượt; đó là một chính sách khác.
- Guard không bảo đảm cron missed triggers được chạy bù sau restart.

## 12. ScheduledFuture và hủy lịch: hủy cái gì?

Với `SimpleAsyncTaskScheduler`, Javadoc cảnh báo rằng handle `ScheduledFuture` của một số phương thức scheduling thể hiện **handoff** thay vì **actual task completion**. Điều này có hệ quả:

1. `future.get()` có thể trở về sau khi scheduler đã bàn giao callback, trong khi logic nghiệp vụ vẫn đang chạy.
2. `future.cancel(...)` có thể ngăn **các lần kích hoạt tiếp theo** của lịch lặp, nhưng không mặc nhiên hủy hoặc rollback task đã được bàn giao.
3. Với task chạy trong executor đích, cancellation phải được định nghĩa theo chính executor và API của task đó.
4. Với external side effect (email, payment, DB update), hủy future không đảo ngược tác động đã commit.
5. Để biết task nghiệp vụ đã kết thúc, theo dõi completion ở business layer, không chỉ dựa trên `ScheduledFuture` của trigger.

Một `@Scheduled` method thường trả `void` trong các ví dụ imperative; không có future kết quả cho từng invocation giống một API job queue. Nếu hệ thống phải cung cấp API “job 42 đã xong chưa?”, xây job status hoặc future completion riêng và gắn operation ID.

### 12.1. Hủy và deadline có ý nghĩa nghiệp vụ

Nếu lịch bị thay thế/hết hiệu lực trong lúc task đang chạy, cần kiểm tra revision/deadline ngay tại ranh giới side effect và dùng idempotency/optimistic locking khi phù hợp. **Cancel trigger** là một khái niệm khác với **ngăn stale task ghi dữ liệu**.

## 13. Lifecycle: dừng trigger không tự dừng mọi worker đã bàn giao

`SimpleAsyncTaskScheduler` tham gia Spring lifecycle và hỗ trợ shutdown, pause/resume theo API phù hợp. Tuy nhiên, bởi vì phần triggering và execution tách nhau, việc dừng scheduler có thể:

- Ngăn những lần kích hoạt **mới** được phát;
- Dừng phần fixed-delay chạy trên scheduler thread theo semantics implementation;
- Không lập tức dừng mọi task **đã bàn giao cho thread/executor khác**;
- Cần tracking/termination timeout có chủ đích nếu muốn chờ một phần công việc đang chạy;
- Cần phối hợp shutdown **của target executor riêng** nếu đã cấu hình `setTargetTaskExecutor(...)`.

Đừng đồng nhất `context.close()` với “mọi email đã được gửi”, hoặc `scheduler.stop()` với “đã rollback toàn bộ task”.

### 13.1. Quản lý tài nguyên trong Spring

Nếu scheduler là bean `@Bean`, context thường quản lý lifecycle thay vì bạn tự gọi `new` trong mỗi request. Khi có target pool `ThreadPoolTaskExecutor`, pool cũng là một bean cần được đóng đúng thứ tự.

Các tác vụ lâu phải có:

- Timeout ở HTTP/DB client;
- Quy định cooperative interruption/cancellation;
- Deadline, idempotency, retry giới hạn;
- Trạng thái giao nhận nếu phải xử lý qua restart;
- Grace period khi container bị SIGTERM;
- Metrics về in-flight jobs và backlog.

Một local scheduler không tự bảo đảm task đã submit được persist qua process restart. Với yêu cầu **durability**, dùng database job store, message broker hoặc hệ thống batch/workflow có quyền sở hữu job rõ ràng.

### 13.2. Virtual threads là daemon

Trong Java 21, virtual threads là daemon. Một ứng dụng chỉ có các virtual threads chạy background có thể kết thúc khi không còn non-daemon thread giữ process sống. Spring Boot có `spring.main.keep-alive=true` để xử lý tình huống tương ứng trong cấu hình hỗ trợ. Với Spring Framework thuần, ứng dụng phải có chính sách vòng đời/chờ thích hợp; không chỉ dựa vào sự tồn tại của task virtual thread.

## 14. Lỗi thực thi và quan sát

### 14.1. Nếu task ném exception

Một scheduled callback có thể ném exception vì database, HTTP timeout, serialization, logic nghiệp vụ hoặc interrupted operation. Cần:

- Xử lý các exception recoverable theo policy có giới hạn;
- Không bắt mọi `Exception` rồi bỏ qua làm lịch có vẻ “thành công”;
- Định nghĩa `ErrorHandler` thích hợp nếu dùng API cho phép;
- Ghi `jobName`, `scheduledAt`, `startedAt`, `finishedAt`, `executionId` và trạng thái lỗi;
- Phân biệt error khi **dispatch** (rejection) với error **bên trong task** sau handoff;
- Hiểu rằng một lỗi trong task đã bàn giao không có nghĩa `ScheduledFuture` của trigger sẽ mang exception đó theo cách bạn mong đợi.

### 14.2. Theo dõi hai độ trễ

```text
trigger lag = actual dispatch time - scheduled due time
queue wait  = actual work start - dispatch/handoff time
run time    = work finished - work started
```

Không thể suy chính xác cả ba đại lượng từ một log “job started” duy nhất. Nên instrument ở trigger, executor và business callback nếu SLA cần phân tích. Với task có thể chạy chồng lấn, ghi correlation ID cho **mỗi lần kích hoạt**, không chỉ tên method.

### 14.3. Metrics cần thiết

| Metric | Câu hỏi cần trả lời |
| --- | --- |
| Trigger fires / missed triggers | Có đúng nhịp không? |
| Dispatch rejects | Executor có từ chối việc không? |
| In-flight executions | Có bao nhiêu lần chạy chưa xong? |
| Queue depth/wait | Pool đích có nghẽn không? |
| Execution duration p95/p99 | Task chậm đến mức nào? |
| Overlap skips | Bao nhiêu lần kích hoạt bị bỏ theo policy? |
| Fixed-delay scheduler blocking | Có task chậm giữ trigger thread không? |
| Failed/retried jobs | Tác vụ có lỗi và retry thế nào? |
| Shutdown wait/unfinished | Còn task lúc đóng process không? |
| Downstream saturation | DB/API có đang giới hạn throughput không? |

Đừng tạo tag metrics bằng job execution ID độc nhất: cardinality quá cao. Để ID chi tiết trong structured logs/traces, còn metrics tags là loại job, scheduler, outcome và các nhóm hữu hạn.

## 15. Debug: vì sao @Scheduled không chạy song song?

Khi quan sát các job bị nối đuôi, kiểm tra **đúng loại scheduler và loại trigger** trước khi tăng thread:

1. Bean `TaskScheduler` thực tế là `SimpleAsyncTaskScheduler`, `ThreadPoolTaskScheduler` hay loại khác?
2. Có `@EnableScheduling` trong application context chứa method `@Scheduled` không?
3. Method `@Scheduled` có thuộc Spring-managed bean, không bị tạo bằng `new` ngoài context?
4. Task là `fixedDelay` hay `fixedRate`/cron?
5. Với `SimpleAsyncTaskScheduler` + fixed-delay, có **chạy trên một scheduler thread** khiến task dài giữ lịch không?
6. Có `setTargetTaskExecutor(...)`, `setConcurrencyLimit(...)` hoặc cơ chế throttle nào?
7. Pool đích có queue full, rejection, worker thread bị block, deadline hoặc rate limit?
8. Từng task có tự khóa một `synchronized` method/lock/DB row nên dù có nhiều thread vẫn chờ?
9. Có singleton mutable state hay guard `AtomicBoolean` chủ ý không cho overlap?
10. Nhiều pods có cùng lịch hay chỉ một instance đang được elected làm leader?
11. Bật virtual threads thực sự chưa? Java 21+, config Boot đúng, runtime class nào?
12. Phân biệt trigger-lag và queue-wait bằng timestamp/logs thay vì suy đoán.

### 15.1. Bảng triệu chứng

| Triệu chứng | Nguyên nhân có thể | Hướng xác minh |
| --- | --- | --- |
| `@Scheduled(fixedDelay)` giữ các lịch khác | Fixed-delay chạy trên scheduler thread | Đo task duration; xem loại scheduler |
| `@Scheduled(fixedRate)` chạy trùng nhau | Handoff khi lần trước chưa xong | Theo dõi execution ID/in-flight; chọn overlap policy |
| Tăng `spring.task.scheduling.pool.size` không tác dụng | Virtual-thread SimpleAsyncTaskScheduler không dùng pool properties | In runtime bean type + Boot conditions |
| Task chạy trên platform pool dù bật virtual threads | Có custom `setTargetTaskExecutor` hoặc custom scheduler | Kiểm tra config bean và thread names |
| `ScheduledFuture.get` trả sớm | Future phản ánh handoff, không phải actual task completion | Theo dõi latch/future của business task |
| Rất nhiều task đang đợi DB | Virtual threads không tăng connection capacity | Đo DB pool saturation và bound concurrency |
| Cron không bù sau JVM restart | Local scheduler không phải durable job store | Dùng durable scheduling khi cần |
| Lỗi task không thấy ở caller | Scheduled callback thường không có caller future riêng | ErrorHandler/logging/metrics/job status |
| Scheduler dừng nhưng task vẫn chạy | Handoff trước stop | Theo dõi target executor và graceful shutdown |
| Nhiều replica cùng gửi email | Mỗi JVM tự có local scheduler | Distributed coordination/idempotency |

## 16. Chọn chính sách overlap theo nghiệp vụ

Có bốn chính sách điển hình:

1. **Allow overlap:** thích hợp các poll độc lập, idempotent, không chạm shared mutable state; cần giới hạn concurrent executions.
2. **Skip while running:** bỏ lần kích hoạt mới khi job trước chưa xong; phù hợp refresh tạm, preview hoặc task không cần xử lý từng tick.
3. **Queue every tick:** giữ từng lần kích hoạt và xử lý lần lượt; có nguy cơ backlog lớn và cần bounded queue, deadline, durability nếu quan trọng.
4. **Coalesce/latest-wins:** chỉ giữ sự kiện kích hoạt mới nhất; phù hợp task refresh cache hoặc UI data, không tự phù hợp thanh toán/audit.

Với **multi-replica**, mọi chính sách cần mở rộng phạm vi: local `AtomicBoolean` chỉ chặn overlap trong một JVM; nếu yêu cầu một task toàn cụm, dùng lease/distributed lock có fencing token hoặc job scheduler tập trung. `@Scheduled` trên hai pod thường có hai lịch hoạt động độc lập.

### 16.1. Đừng đổi fixedDelay thành fixedRate mà không thay hợp đồng

Ví dụ polling một thiết bị không được có hai request cùng lúc:

- Fixed-delay tự nhiên biểu diễn “sau khi request xong, đợi 5 giây”.
- Chuyển thành fixed-rate để dùng virtual task handoff có thể tạo hai request chồng lấn.
- Nếu fixed-rate vẫn cần, phải có guard/skip/queue đúng nghiệp vụ.
- Nếu bạn cần completion-based delay nhưng không muốn chặn scheduler trigger thread, có thể thiết kế **tự lên lịch lần kế tiếp sau khi completion thật** (custom completion-aware scheduling) và kiểm soát retry/shutdown. Đây là thiết kế riêng, không có nghĩa `SimpleAsyncTaskScheduler` fixed-delay tự dùng handoff.

## 17. Kiểm thử scheduling đúng cách

### 17.1. Test một lần kích hoạt

Trong Spring context, lấy `SimpleAsyncTaskScheduler` bean, gọi `schedule(task, Instant.now())`, và dùng `CountDownLatch.await(timeout)` đợi business callback. Đó là cách ở `VirtualTaskDemo`, giúp test không phụ thuộc vào sleep.

Kiểm chứng:

- Callback có chạy?
- Khi virtual threads enabled, callback one-shot chạy trên virtual thread?
- Khi có target executor, prefix thread đúng?
- Exception có được ghi nhận ở business future/log không?
- Context đóng đúng lifecycle?

### 17.2. Test fixed-delay khác fixed-rate

Một integration test có thể:

1. Nạp `@EnableScheduling` với hai method, một fixed-rate và một fixed-delay.
2. Dùng latch để giữ một lần fixed-delay đang chạy, không dùng `Thread.sleep` để “đoán” trạng thái.
3. Quan sát thread name và thời điểm trigger của method khác trong cùng scheduler.
4. Cho release latch, xác minh scheduler phục hồi khả năng kích hoạt.
5. Đặt timeout để test không treo khi có lỗi.
6. So sánh với một scheduler/pool khác khi cần.

Không nên viết assert cứng “chính xác 3 lần chạy sau 3000ms” trên máy CI nhiều tải. Lịch theo wall clock có độ trễ biến thiên; test nên kiểm tra **quan hệ, loại thread, quyền overlap và eventual completion**.

### 17.3. Test concurrent load và rejection

Đăng ký nhiều task nhanh trong khi target pool hữu hạn bị giữ bằng latch:

- Pool có đủ worker/queue để nhận task không?
- Khi full, điều gì bị reject hoặc block?
- `SimpleAsyncTaskScheduler` trigger thread có bị kéo dài khi quota/throttle chạm giới hạn?
- Logs/metrics có phản ánh task chưa chạy hay đã thất bại không?
- Sau shutdown, việc bàn giao có được từ chối đúng cách không?

Không dùng “nhiều thread chạy thấy nhanh” làm tiêu chí thành công duy nhất. Với scheduler, **không bỏ công việc quan trọng**, **không ghi lặp**, **bắt deadline** và **giữ downstream ổn định** thường quan trọng hơn throughput tối đa.

## 18. Bài thực hành

### Bài 1 — Vẽ luồng

Vẽ ba sơ đồ cho `SimpleAsyncTaskScheduler`:

- One-shot trigger → virtual thread.
- Fixed-rate trigger → `ThreadPoolTaskExecutor` qua `setTargetTaskExecutor`.
- Fixed-delay → chạy ngay trên scheduler thread.

Chỉ ra mốc due time, handoff và actual completion. Giải thích scheduler thread có thể bị block tại đâu.

### Bài 2 — Thay executor

Chạy `VirtualTaskDemo` trên JDK 21+ và xác nhận `task-on-virtual-thread=true`. Sau đó chạy `TargetExecutorDemo` và xác nhận `ran-on-target-pool=true`. Giải thích **nơi kích hoạt lịch giữ nguyên**, nhưng **nơi thực thi callback thay đổi**.

### Bài 3 — So sánh trigger

Tạo ba method `@Scheduled` với fixedRate, fixedDelay và cron. Dùng latch giữ task chậm, quan sát khả năng overlap. Đặc biệt giải thích fixed-delay trên `SimpleAsyncTaskScheduler` không có handoff thread-per-task.

### Bài 4 — Backpressure

Đặt target pool core=1/max=2/queue=2, submit nhiều one-shot tasks đang chặn latch. Đo số task chạy, xếp hàng và bị reject; chọn một chính sách bounded admission hoặc durable enqueue. Không chỉ tăng pool size rồi bỏ qua downstream.

### Bài 5 — Chống chồng lấn

Áp dụng `AtomicBoolean.compareAndSet` như `OverlapGuardDemo` cho một công việc local. Sau đó chạy hai instances mô phỏng, chứng minh local flag không chống trùng toàn cụm. Đề xuất lock/lease có fencing token nếu chỉ một instance được tạo tác dụng phụ.

### Bài 6 — Completion thật

Viết task trả trạng thái hoàn tất riêng (future/latch/job record). Chứng minh `ScheduledFuture` của scheduler handoff không đủ để caller biết email/DB update đã xong; đo cả due time, dispatch time và finish time.

### Bài 7 — Shutdown/restart

Đóng context khi có task đã được handoff. Kiểm tra chính sách termination timeout và owner của target pool. Giả lập restart để thấy local scheduling không tự phục hồi những công việc bắt buộc phải giao qua durable job store.

## 19. Checklist tự đánh giá

- [ ] Tôi phân biệt được **scheduler trigger thread** và **worker execution thread**.
- [ ] Tôi biết `SimpleAsyncTaskScheduler` có một scheduler thread và mặc định handoff một thread cho mỗi lần chạy phù hợp.
- [ ] Tôi biết Java 21+ và `setVirtualThreads(true)` là lựa chọn virtual-thread phù hợp.
- [ ] Tôi biết `fixedDelay` là **ngoại lệ chạy trên scheduler thread**, không phải luôn offload.
- [ ] Tôi giải thích được vì sao fixed-rate/cron có thể overlap khi task chậm.
- [ ] Tôi hiểu `setTargetTaskExecutor` thay nơi thực thi nhưng không xóa vai trò trigger thread.
- [ ] Tôi hiểu `ScheduledFuture` của handoff có thể không phản ánh actual task completion.
- [ ] Tôi phân biệt được `SimpleAsyncTaskScheduler` và `ThreadPoolTaskScheduler`.
- [ ] Tôi không cho rằng virtual threads làm vô hạn DB connections hoặc CPU.
- [ ] Tôi không dùng `spring.task.scheduling.pool.size` để chỉnh scheduler thread-per-task ảo.
- [ ] Tôi biết `@EnableScheduling` khác `@EnableAsync`.
- [ ] Tôi hiểu shutdown scheduler không tự hoàn tất/rollback toàn bộ worker đã bàn giao.
- [ ] Tôi có chiến lược backpressure, overlap, idempotency và distributed coordination khi cần.
- [ ] Tôi dùng latch, metrics và context runtime để test thay vì đoán từ `Thread.sleep`.
- [ ] Tôi biết khi nào phải chọn durable job scheduling thay vì local `@Scheduled`.

**Kết luận:** `SimpleAsyncTaskScheduler` minh họa rõ sự khác biệt giữa **lịch kích hoạt** và **cơ chế thực thi**: một scheduler thread giữ nhịp, còn các lần chạy thích hợp được bàn giao cho task threads (virtual khi Java 21+ được bật) hoặc executor khác. **Ngoại lệ fixed-delay** phải được hiểu đúng để tránh chặn scheduler thread. Việc lựa chọn scheduler tốt không chỉ là cấu hình thread; đó là lựa chọn semantics overlap, khả năng chịu tải, lifecycle và cách chứng minh công việc thật sự hoàn tất.

## 20. Tài liệu chính thức

- Spring Framework 6.2 — [SimpleAsyncTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/SimpleAsyncTaskScheduler.html): single scheduler thread, thread-per-task, fixed-delay exception, `setTargetTaskExecutor`, concurrency limit, lifecycle và `ScheduledFuture` handoff.
- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@EnableScheduling`, `@Scheduled`, `TaskScheduler` và các implementation.
- Spring Framework 6.2 — [@EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html): scheduler resolution và `SchedulingConfigurer`.
- Spring Boot 3.3 — [Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.3/reference/features/task-execution-and-scheduling.html): auto-configuration với virtual threads, scheduler implementation và pool properties.
- Java SE 21 — [Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html): mục đích, đặc tính và hạn chế của virtual threads.
- Java SE 17 — [ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html): so sánh ngữ nghĩa trigger/future ở Java cơ bản.

Khi áp dụng, hãy đối chiếu version cụ thể của Spring Framework, Spring Boot và JDK. Những đoạn có `@Scheduled` chưa định nghĩa business methods được ghi rõ là **fragments cần tích hợp trong project**; những ví dụ có tệp, lệnh biên dịch và output là chương trình độc lập hoặc project Maven đầy đủ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduler-selection-007">Giới hạn fixed-delay chạy trên luồng scheduler của SimpleAsyncTaskScheduler</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn fixed-delay chạy trên luồng scheduler của SimpleAsyncTaskScheduler

## 1. Vấn đề cốt lõi: virtual threads không tự làm fixed-delay chạy song song

`SimpleAsyncTaskScheduler` là một implementation của Spring `TaskScheduler` được đưa vào từ Spring Framework 6.1. Thiết kế của nó có **một luồng lập lịch** (*scheduler thread*) và thường tạo **một luồng thực thi riêng cho mỗi lần kích hoạt công việc**. Trên Java 21 trở lên, nó có thể tạo virtual threads qua `setVirtualThreads(true)`, phù hợp với những tác vụ blocking độc lập được kích hoạt bằng fixed-rate hoặc cron.

Nhưng có ngoại lệ quan trọng và rất dễ bỏ qua:

> **Với `SimpleAsyncTaskScheduler`, các tác vụ `fixed-delay` chạy trực tiếp trên một luồng scheduler, không được chuyển sang luồng thực thi riêng như fixed-rate/cron.**

Đây là hạn chế được nêu trực tiếp trong tài liệu chính thức của Spring. Lý do là giữ semantics truyền thống của fixed-delay: lần thực thi kế tiếp chỉ được lên lịch sau khi lần trước **thực sự kết thúc** rồi chờ một khoảng delay.

Hệ quả thực tế:

- Nếu một `fixed-delay` task gọi API chậm, chờ JDBC, giữ lock hoặc `Thread.sleep(...)` trong thời gian dài, **nó giữ luồng scheduler duy nhất**.
- Một `fixed-delay` task khác dùng cùng instance cũng không thể đồng thời chạy trên chính luồng đó; lịch khác có thể bắt đầu muộn so với kỳ vọng.
- Bật virtual threads **không chuyển** thân của fixed-delay sang virtual thread riêng. Một scheduler có virtual threads vẫn chịu hạn chế này.
- Nếu scheduler thread đang bận, việc kích hoạt các lịch khác chia sẻ hạ tầng điều phối có thể bị trễ; mức tác động tới fixed-rate/cron còn phụ thuộc phiên bản và cách Spring xử lý trigger, nên cần đo trên cấu hình thực tế.
- Khi một callback quá lâu hoặc không bao giờ kết thúc, nhiều lịch có thể bị trì hoãn. Đây là vấn đề **độ trễ và cô lập công việc**, không chỉ là chuyện có bao nhiêu thread trên JVM.

Bài này giải thích cơ chế, so sánh các loại lịch, đưa ra thí nghiệm Java 17 + Spring 6.2 có thể chạy bằng Maven, rồi chỉ cách chọn `ThreadPoolTaskScheduler` hoặc thay đổi kiến trúc để tránh một fixed-delay job làm chậm job khác.

## 2. Fixed-delay thực sự đo từ thời điểm nào?

`TaskScheduler.scheduleWithFixedDelay(Runnable, Duration)` có nghĩa là:

1. Thực thi task lần thứ nhất khi có thể.
2. Đợi **lần chạy này hoàn tất**.
3. Chờ thêm khoảng `delay`.
4. Thực thi lần kế tiếp.
5. Lặp lại cho tới khi lịch bị hủy hoặc scheduler ngừng hoạt động.

Overload có `Instant startTime` cho phép xác định thời điểm mong muốn của **lần đầu tiên**:

```java
ScheduledFuture<?> recurring = taskScheduler.scheduleWithFixedDelay(
        () -> refreshCache(),
        Instant.now().plusSeconds(2),
        Duration.ofSeconds(5)
);
```

Đây là **snippet tích hợp**: `taskScheduler` và `refreshCache()` phải có trong ứng dụng. Chúng ta chưa viết chương trình standalone ở đoạn này.

Giả sử callback mất 8 giây, delay là 5 giây:

```text
Thời gian:
t=0s       t=8s       t=13s      t=21s       t=26s
  |----------|----------|----------|-----------|
  [ RUN #1  ]  wait 5 s [ RUN #2  ]   wait 5 s ...
              ^                 ^
         kết thúc #1       kết thúc #2

start #2 - end #1 = 5 s
start #2 - start #1 = 13 s (không phải 5 s)
```

Với một task chạy riêng lẻ và không bị scheduler khác cản trở, **thời gian giữa hai lần bắt đầu** khoảng bằng **execution duration + fixed delay**. Trong hệ thống đang bị nghẽn, khoảng này còn có thể dài hơn vì phải chờ luồng thực thi.

### Tránh hai cách hiểu sai

**Sai:** “`fixedDelay = 5s` nghĩa là cứ đúng 5 giây sẽ có một lần chạy mới.” Đúng là có tối thiểu khoảng 5 giây **sau khi lần trước hoàn tất** trước khi tiếp tục.

**Sai:** “Vì virtual threads nhẹ nên hai phương thức `@Scheduled(fixedDelay=...)` đều chạy đồng thời.” Với cùng một `SimpleAsyncTaskScheduler`, những callback fixed-delay dùng chung một scheduler thread; virtual threads cho các loại kích hoạt khác không làm chúng song song.

## 3. Kiến trúc SimpleAsyncTaskScheduler: một scheduler, hai đường thực thi

Spring mô tả `SimpleAsyncTaskScheduler` như một scheduler dùng **một thread** để quản lý thời điểm, rồi bắt đầu thread mới cho những lần kích hoạt được dispatch bất đồng bộ. Cách nó xử lý fixed-delay là ngoại lệ.

```text
                              SimpleAsyncTaskScheduler
                            +----------------------------+
Đăng ký schedule ---------->| SINGLE SCHEDULER THREAD    |
                            |                            |
fixed-rate / cron due ------>| kích hoạt / hand-off -------+---> execution thread A
                            |                            |     (virtual nếu JDK 21+ và bật)
                            |                            +---> execution thread B
                            |                            |     (mỗi lần kích hoạt)
fixed-delay due ------------>| RUN fixed-delay callback   |
                            | TRỰC TIẾP trên chính thread|
                            +----------------------------+
                                       |
                                       +-- callback lâu / blocking
                                           giữ scheduler thread
                                           => các lịch khác có thể bị trễ
```

### Đặc điểm từng đường

| Loại trigger | Cách chạy điển hình trong `SimpleAsyncTaskScheduler` | Điểm cần lưu ý |
| --- | --- | --- |
| `schedule(Runnable, Instant)` | Khi đến hạn, hand-off sang thread thực thi riêng | Future của scheduler có thể phản ánh hand-off, không nhất thiết completion nghiệp vụ |
| `scheduleAtFixedRate(...)` | Lần kích hoạt được hand-off sang thread riêng | Các lần chạy khác nhau có thể chồng lấn; cần giới hạn concurrency |
| `schedule(Runnable, CronTrigger)` | Khi trigger đến hạn, có thể hand-off sang thread riêng | Semantics cron, overlap và thời gian tính lịch cần kiểm tra |
| `scheduleWithFixedDelay(...)` | **Callback chạy trên scheduler thread duy nhất** | Công việc chậm/blocked cản các fixed-delay callback khác trên cùng scheduler |

**Không kết luận** “mọi `ScheduledFuture<?>` của `SimpleAsyncTaskScheduler` luôn báo hoàn tất công việc thật”. Javadoc cảnh báo scheduler loại này nhìn chung theo dõi **hand-off** tới execution thread chứ không phải lúc task hoàn thành. Fixed-delay có đường thực thi đặc biệt; vẫn nên dùng metric nghiệp vụ hoặc completion signal riêng để xác nhận kết quả, nhất là khi refactor thành task dispatch tới executor khác.

### Đặt cạnh ThreadPoolTaskScheduler

`ThreadPoolTaskScheduler` không dùng mẫu “thread mới cho mỗi lần kích hoạt”. Nó quản lý một pool `ScheduledThreadPoolExecutor` có kích thước cấu hình; các callback chạy trực tiếp trên các scheduler worker của pool.

Ví dụ `ThreadPoolTaskScheduler.setPoolSize(4)` có thể cho các công việc fixed-delay khác nhau chạy đồng thời trên các scheduler workers khác nhau, trong khi **mỗi lịch fixed-delay riêng lẻ** vẫn giữ quy tắc chờ hoàn tất rồi mới delay. Một task chậm không nhất thiết chiếm **toàn bộ** scheduler nếu các worker khác còn rảnh.

Lưu ý: `ThreadPoolTaskScheduler` mặc định có pool size **1**; chỉ đổi loại scheduler mà quên cấu hình pool size sẽ không tự tạo isolation nhiều worker.

## 4. Điều gì xảy ra khi có hai công việc fixed-delay?

Giả sử hai công việc `A` và `B` cùng dùng một `SimpleAsyncTaskScheduler`:

- `A` đến hạn và bắt đầu trước.
- `A` chờ HTTP response 10 giây.
- `B` đến hạn trong lúc `A` đang chờ.
- Vì cả hai fixed-delay callbacks dùng chung scheduler thread, `B` **không thể bắt đầu trên scheduler đó** tới khi `A` trả quyền lại.

```text
SINGLE SCHEDULER THREAD
time --------->
     [ A is running / blocking 10s      ][ B starts ][ A/B next due ... ]
             ^                           ^
             |                           |
       B becomes due                 A finally ends
       but cannot start              B can now run

Không có second fixed-delay worker để chạy B cùng lúc với A.
```

Đây là **head-of-line blocking**: một công việc ở đầu đang giữ tài nguyên duy nhất, khiến công việc ở sau bị trễ dù chúng không có quan hệ nghiệp vụ.

### Trễ dây chuyền

Khi có nhiều job fixed-delay cùng scheduler, độ trễ của job B không chỉ là độ dài riêng của B. Nó có thể còn bao gồm thời gian công việc A giữ thread, cùng các công việc khác đang chờ, GC pause và load hệ thống.

Ví dụ minh họa:

| Job | Thời gian chạy bình thường | Khi dependency lỗi |
| --- | ---: | ---: |
| A – poll remote API | 100 ms | 45 giây do timeout |
| B – kiểm tra hạn token | 20 ms | Không thay đổi |
| C – refresh cấu hình | 50 ms | Không thay đổi |

Nếu A bắt đầu đúng lúc API timeout 45 giây, B/C dùng chung một scheduler thread có thể phải đợi A trả về. Các deadline của B/C bị ảnh hưởng bởi sự cố của A, dù bản thân code của B/C rất nhanh.

**Hệ quả:** đặt `fixedDelay=1s` cho B không có nghĩa B có thể khởi động bất kể A làm gì. Đây là lý do phải xem cách scheduler thực thi, không chỉ giá trị interval.

### Ảnh hưởng đến fixed-rate/cron

Trong một số phiên bản/cấu hình Spring, fixed-delay callback chiếm luồng điều phối cũng có thể làm trễ việc trigger/dispatch fixed-rate hoặc cron trên cùng scheduler. Đã từng có báo cáo lỗi thực tế ở Spring Framework 6.1 liên quan việc một fixed-delay dài làm các lần kích hoạt fixed-rate bị dồn lại. Không suy rộng chính xác hành vi backlog/burst của báo cáo đó sang **mọi** phiên bản 6.2.x hoặc 7.x: nếu sự độc lập của trigger là yêu cầu nghiệp vụ, hãy kiểm thử trên **đúng phiên bản** đang chạy và tách scheduler khi cần.

Dù phiên bản có tối ưu riêng để giảm ảnh hưởng tới loại trigger khác, một giới hạn vẫn được tài liệu chính thức xác nhận: **những fixed-delay callbacks của SimpleAsyncTaskScheduler được thực thi trên một scheduler thread**.

## 5. So sánh fixed-delay, fixed-rate và cron

| Khía cạnh | Fixed-delay | Fixed-rate | Cron |
| --- | --- | --- | --- |
| Mốc tính lần kế tiếp | Kết thúc lần trước + delay | Theo chu kỳ tính từ các mốc bắt đầu dự kiến | Lịch theo biểu thức và đồng hồ/zone |
| Lần trước chạy lâu | Lần sau lùi lại | Có thể trễ, dồn hoặc chồng lấn tùy scheduler/implementation | Có thể trễ hoặc bỏ/dồn lần kích hoạt theo trigger semantics |
| Với SimpleAsyncTaskScheduler | **Chạy trên scheduler thread** | Thường hand-off sang thread thực thi riêng | Thường hand-off sang thread thực thi riêng |
| Với virtual threads | Không tự dùng virtual thread riêng cho thân fixed-delay | Có thể dùng virtual thread nếu bật | Có thể dùng virtual thread nếu bật |
| Rủi ro | Head-of-line blocking các fixed-delay jobs | Overlap, contention, quá nhiều việc in-flight | Overlap/misfire/timezone và tải bùng nổ |
| Phù hợp | Task phải có khoảng nghỉ sau mỗi lần chạy | Sampling/polling có lịch nhịp cố định | Lịch theo thời gian thực như 09:00 hàng ngày |

**Quan trọng:** lời khuyên “dùng fixed-rate/cron khi bật virtual threads” từ tài liệu Spring nhằm tránh giới hạn fixed-delay trên scheduler thread. Nó **không** có nghĩa cứ đổi `fixedDelay` sang `fixedRate` là giữ nguyên ý nghĩa nghiệp vụ.

Nếu task cần đảm bảo “xong việc, nghỉ 5 giây rồi chạy lại”, chuyển sang fixed-rate 5 giây có thể tạo overlap hoặc quá tải dependency khi một lần xử lý mất 20 giây. Cần quyết định:

- Có cho phép **hai lần cùng một job chạy đồng thời**?
- Task có idempotent không?
- Tần suất tối đa và quota downstream là bao nhiêu?
- Có cần tránh dồn việc sau một đợt bị chậm?
- Có thể bỏ qua một lần khi job trước chưa xong hay không?

Không đổi mode scheduling chỉ để cải thiện throughput nếu nó vi phạm những điều kiện trên.

## 6. Thực nghiệm Java 17 + Spring Framework 6.2: chứng minh hai fixed-delay không chạy song song

Thí nghiệm này tạo hai lịch fixed-delay trên cùng **một** `SimpleAsyncTaskScheduler` và dùng `CountDownLatch` để giữ lần chạy đầu tiên của job A chưa hoàn tất. Job B được đăng ký **sau khi** xác nhận A đã chiếm scheduler thread. Khi A còn bị chặn, B không thể bắt đầu; sau khi mở latch, B mới được thực thi trên **cùng scheduler thread**.

Cách này đáng tin cậy hơn `Thread.sleep(1000)` vì ta chủ động giữ worker cho tới khi phép đo kết thúc. Không cần bật virtual threads để tái hiện giới hạn; ví dụ dùng **JDK 17 platform threads**. Trên JDK 21 với `setVirtualThreads(true)`, nguyên tắc fixed-delay vẫn như vậy.

### 6.1. Điều kiện và cấu trúc

Cần **JDK 17+**, Maven và Spring Framework **6.2.14**:

```text
simple-scheduler-fixed-delay/
├── pom.xml
└── src/main/java/demo/FixedDelayThreadDemo.java
```

### 6.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>simple-scheduler-fixed-delay</artifactId>
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
                    <mainClass>demo.FixedDelayThreadDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 6.3. FixedDelayThreadDemo.java

```java
package demo;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

public final class FixedDelayThreadDemo {
    private static void awaitOrFail(CountDownLatch latch, String label)
            throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timeout at " + label);
        }
    }

    public static void main(String[] args) throws Exception {
        SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
        scheduler.setThreadNamePrefix("fixed-delay-demo-");
        scheduler.start();

        CountDownLatch enteredA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        CountDownLatch enteredB = new CountDownLatch(1);

        AtomicInteger runsA = new AtomicInteger();
        AtomicInteger runsB = new AtomicInteger();
        AtomicReference<Thread> workerA = new AtomicReference<>();
        AtomicReference<Thread> workerB = new AtomicReference<>();

        ScheduledFuture<?> futureA = null;
        ScheduledFuture<?> futureB = null;

        try {
            // A chạy đầu tiên và bị giữ bằng latch.
            futureA = scheduler.scheduleWithFixedDelay(() -> {
                if (runsA.incrementAndGet() == 1) {
                    workerA.set(Thread.currentThread());
                    enteredA.countDown();
                    try {
                        releaseA.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, Instant.now().plusMillis(20), Duration.ofHours(1));

            // Biết chắc A đã chiếm scheduler thread.
            awaitOrFail(enteredA, "A started");

            // B có hạn chạy ngay nhưng phải đợi A trả scheduler thread.
            futureB = scheduler.scheduleWithFixedDelay(() -> {
                runsB.incrementAndGet();
                workerB.set(Thread.currentThread());
                enteredB.countDown();
            }, Instant.now(), Duration.ofHours(1));

            boolean bStartedWhileABlocked =
                    enteredB.await(200, TimeUnit.MILLISECONDS);
            System.out.println(
                    "bStartedWhileABlocked=" + bStartedWhileABlocked);

            releaseA.countDown();
            awaitOrFail(enteredB, "B started after release");

            System.out.println(
                    "sameThread=" + (workerA.get() == workerB.get()));
            System.out.println("runsA=" + runsA.get());
            System.out.println("runsB=" + runsB.get());
        } finally {
            // Không để thread giữ latch nếu test thất bại.
            releaseA.countDown();
            if (futureA != null) futureA.cancel(false);
            if (futureB != null) futureB.cancel(false);
            scheduler.stop();
            scheduler.close();
        }
    }
}
```

Chạy tại thư mục có `pom.xml`:

```bash
mvn -q compile exec:java
```

**Standard output theo hành vi Spring 6.2.14 ở đường chạy bình thường:**

```text
bStartedWhileABlocked=false
sameThread=true
runsA=1
runsB=1
```

### 6.4. Tại sao không phải test dựa trên may mắn lập lịch?

1. Task A thông báo qua `enteredA.countDown()` **sau khi đã thực sự chạy**. Main chỉ đăng ký B khi `awaitOrFail(enteredA, ...)` xác nhận A đã bắt đầu.
2. A chờ `releaseA`; khi latch chưa mở, A vẫn giữ scheduler thread. Do đó B không có cơ hội chạy trên thread đó.
3. Main đợi B tối đa 200 ms trong khi A bị giữ. `bStartedWhileABlocked=false` không phải kết quả so CPU nhanh/chậm mà là do scheduler thread đang bị chiếm.
4. Khi main gọi `releaseA.countDown()`, A trả về; B có thể chạy trên scheduler thread. Biến `workerA` và `workerB` giữ tham chiếu thread để so sánh bằng **identity**.
5. Hai lịch có `Duration.ofHours(1)` nên sau lần đầu chúng không lặp lại trong khoảng thời gian rất ngắn của phép thử. Không cố chứng minh các chu kỳ giờ trong một CLI test.
6. `finally` mở latch, hủy hai lịch và đóng scheduler. Mọi phép `await` có timeout để tránh treo vô hạn nếu môi trường bị lỗi.

Chú ý: trong môi trường CI bị đóng băng hoặc quá tải nghiêm trọng, timeout 5 giây có thể ném lỗi. Khi đó test **báo thất bại** chứ không nên giả vờ rằng stdout bốn dòng đã được tạo. Để ổn định hơn trên CI chậm, tăng giới hạn timeout của test chứ không thay việc đồng bộ bằng một số `Thread.sleep` tùy ý.

### 6.5. Phạm vi chứng minh

Ví dụ chứng minh **hai fixed-delay jobs dùng cùng một SimpleAsyncTaskScheduler không đồng thời chạy**, ngay cả khi B không phụ thuộc nghiệp vụ vào A. Nó **không** đo throughput production, không mô phỏng database/HTTP timeout, không chứng minh mọi fixed-rate/cron job đều bị chặn theo cùng cách trên mọi phiên bản Spring.

Ví dụ cũng không tuyên bố lịch đã được lưu bền vững: tất cả ScheduledFuture và state của scheduler là cục bộ trong JVM.

## 7. Bật virtual threads ở Java 21 có thay đổi kết quả không?

Trong Spring 6.1+, `SimpleAsyncTaskScheduler` có lựa chọn:

```java
SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler();
scheduler.setVirtualThreads(true); // Chỉ bật trên JDK 21+.
```

Đây là snippet minh họa; không chạy trên Java 17 khi cố bật virtual-thread mode. Với virtual threads, các lần chạy mà scheduler **dispatch sang execution thread riêng** có thể dùng virtual threads thay platform threads.

**Nhưng đường fixed-delay vẫn là ngoại lệ.** Vì callback fixed-delay chạy trực tiếp trên single scheduler thread, `setVirtualThreads(true)` không tạo một virtual worker riêng để thực thi từng callback fixed-delay độc lập.

Các nhận định đúng:

- Virtual threads làm giảm chi phí của nhiều thread đồng thời chờ I/O so với tạo hàng nghìn platform threads, **không** xóa giới hạn CPU/DB connections/remote rate limits.
- Một `fixed-delay` task chờ mạng có thể giữ scheduler thread. Không thể dựa vào số lượng virtual threads toàn JVM để bảo đảm scheduler tiếp tục phản hồi.
- Thay `fixedDelay` bằng `fixedRate`/cron có thể khiến nhiều executions chạy trên virtual threads riêng, nhưng cũng có thể tạo **overlap** hoặc tải đột biến. Việc đổi chỉ hợp lệ nếu semantics nghiệp vụ cho phép.
- `SimpleAsyncTaskScheduler` không tái sử dụng execution threads theo kiểu pool; trên JDK 17 mặc định nó dùng platform threads cho những đường hand-off, có thể tốn tài nguyên khi rất nhiều tác vụ ngắn.
- `setConcurrencyLimit(int)` của họ `SimpleAsyncTaskExecutor` giới hạn số execution thread đang được điều phối bởi executor; **không** biến fixed-delay callback thành execution riêng. Tùy config, throttle còn có thể khiến việc dispatch bị chặn.
- Nếu sử dụng thread-per-task scheduling, hãy đo số task đang chạy, saturation downstream, memory, cancellation và thời gian hoàn tất.

### Spring Boot auto-configuration có thể làm bạn bất ngờ

Trong Spring Boot 3.3+ với JDK 21+ và `spring.threads.virtual.enabled=true`, scheduler được tự cấu hình cho `@Scheduled` (khi auto-configuration thích hợp) có thể là `SimpleAsyncTaskScheduler` sử dụng virtual threads thay vì `ThreadPoolTaskScheduler`.

Ví dụ property:

```properties
spring.threads.virtual.enabled=true
```

Khi virtual-thread scheduler được chọn, các thiết lập pool-size hướng tới `ThreadPoolTaskScheduler` **không còn ý nghĩa tương tự**; Boot document nêu các pooling properties bị bỏ qua với lựa chọn này. Vì vậy:

1. Không suy ra kiểu scheduler từ mỗi annotation `@Scheduled`.
2. Kiểm tra bean `TaskScheduler` thực tế trong `ApplicationContext`.
3. Với Java 21, xác nhận `spring.threads.virtual.enabled`, các scheduler bean tùy chỉnh và qualifier.
4. Test riêng mọi `@Scheduled(fixedDelay=...)` có blocking I/O trước khi chuyển sang virtual-thread mode.

Một lưu ý vận hành Java virtual threads: virtual threads là daemon, nên một CLI chỉ có daemon threads có thể kết thúc khi các thread non-daemon hoàn tất. Spring Boot có tùy chọn `spring.main.keep-alive=true` để giữ ứng dụng sống khi cần; không mặc định lịch sẽ giữ JVM hoạt động mãi.

## 8. Giải pháp 1: ThreadPoolTaskScheduler với pool size phù hợp

Nếu nhiều fixed-delay jobs cần được **cô lập tương đối** và có thể chạy đồng thời, hãy cân nhắc `ThreadPoolTaskScheduler` với nhiều worker thay vì cùng chia sẻ một single scheduler thread.

**SchedulerConfiguration.java** — cấu hình tích hợp Spring:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulerConfiguration {

    @Bean("fixedDelayPool")
    public ThreadPoolTaskScheduler fixedDelayPool() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("fixed-delay-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
```

Đây là code cần Spring context, không phải chương trình Java thuần chạy ngay bằng `javac`. Khi container quản lý bean, lifecycle initialization/shutdown thông thường được Spring thực hiện.

**ScheduledJobs.java** — chọn rõ scheduler cho từng method:

```java
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledJobs {

    @Scheduled(
            fixedDelay = 5,
            timeUnit = TimeUnit.SECONDS,
            scheduler = "fixedDelayPool")
    public void pollOrders() {
        // Blocking I/O với connect/read/query timeout phù hợp.
        // Không khóa scheduler worker vô hạn.
    }

    @Scheduled(
            fixedDelay = 2,
            timeUnit = TimeUnit.SECONDS,
            scheduler = "fixedDelayPool")
    public void refreshTokens() {
        // Độc lập với pollOrders nhưng cùng pool có bốn worker.
    }
}
```

Ở Spring Framework 6.1+, thuộc tính `@Scheduled.scheduler` cho phép chọn bean scheduler theo tên/qualifier. Không phải annotation này tự mở worker riêng: nó chỉ trỏ tới **executor có cấu hình phù hợp**.

### Vì sao pool size 4 giúp khác biệt?

- Một job đang chờ HTTP giữ một worker, nhưng nếu còn worker khác, job khác đến hạn có thể chạy.
- **Mỗi periodic task fixed-delay riêng** vẫn không tự chồng lấn lần chạy kế tiếp của chính nó trước khi lần trước kết thúc.
- Nếu đồng thời bốn worker đều bị blocking, job mới vẫn bị trễ. Chọn pool size từ workload, không phải cứ tăng vô hạn.
- Pool size là theo **JVM instance**. Ba pod, mỗi pod 4 worker có thể tạo đến 12 công việc scheduler chạy đồng thời trên cluster, chưa kể executor khác.
- Nếu job gọi DB nhưng HikariCP chỉ có 4 connections cho toàn app, tăng scheduler pool lên 20 không làm DB xử lý nhanh hơn mà có thể tạo thêm 16 worker đang chờ connection.

**Không coi `ThreadPoolTaskScheduler` là durable scheduler.** Nó vẫn dùng bộ nhớ JVM; restart làm mất lịch động nếu không có persistence. Với `@Scheduled` annotation, Spring đăng ký lại các lịch khi khởi tạo bean, nhưng không tự lưu mọi lần chạy lỡ hoặc trạng thái nghiệp vụ của chúng.

### Khi nào cần tách hẳn scheduler?

Nếu `pollOrders()` có nguy cơ chờ một dependency rất chậm còn `refreshTokens()` liên quan tính khả dụng hoặc bảo mật, dùng **hai scheduler bean riêng** có thể là quyết định hợp lý. Khi đó lỗi quá tải ở pool A không tự chiếm mọi worker của pool B. Tuy nhiên, cả hai vẫn chia sẻ CPU/heap/downstream resources, vì vậy phải kiểm soát tài nguyên ở những tầng khác.

## 9. Giải pháp 2: fixed-rate/cron + giới hạn concurrency theo nghiệp vụ

Khi requirement là “cứ tới mốc 09:00” hoặc “thử kiểm tra mỗi 1 giây theo nhịp cố định”, việc dùng fixed-rate hoặc cron có thể phù hợp hơn, đặc biệt với virtual-thread-aligned scheduler.

Ví dụ:

```java
@Scheduled(
        fixedRate = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "virtualScheduler")
public void checkStatus() {
    statusClient.fetch();
}
```

Đây là snippet tích hợp với `TimeUnit`, `statusClient` và bean scheduler thật tên `virtualScheduler`.

**Cần chống overlap** nếu cùng một lần chạy chưa xong đã đến mốc kế tiếp. Một cách đơn giản là dùng `AtomicBoolean`:

```java
private final AtomicBoolean running = new AtomicBoolean(false);

@Scheduled(
        fixedRate = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "virtualScheduler")
public void checkStatus() {
    if (!running.compareAndSet(false, true)) {
        // Bỏ lần này theo chính sách skip-if-running.
        return;
    }

    try {
        statusClient.fetch();
    } finally {
        running.set(false);
    }
}
```

Đây là **mảnh của một class Spring**, phải thêm import/bean tương ứng. Nó chỉ bảo vệ chống overlap **trong một instance của bean trên một JVM**; nhiều pod vẫn có thể cùng chạy. Nếu cần cluster-wide exclusivity, cần cơ chế lease/lock/job store phù hợp và xử lý crash.

Một `AtomicBoolean` đơn giản không có fairness, không xếp hàng lần bị bỏ và không bảo đảm “mỗi lần tick đều được phục vụ”. Đó là hành vi *skip* chủ động, không phải thay thế tương đương cho fixed-delay. Nếu không thể bỏ tick, cần mô hình queue/backpressure khác.

### Vì sao một vài lớp không nên dùng cron thay fixed-delay?

Ví dụ job dọn dẹp chỉ được bắt đầu sau khi lần trước hoàn tất và phải nghỉ 30 giây để downstream phục hồi. Cron mỗi 30 giây có thể tạo lần chạy mới **khi lần cũ chưa xong**, tùy scheduler. Kể cả có guard để skip, thời gian giữa các lần chạy thực tế không giống “xong rồi nghỉ đủ 30 giây”. Dùng fixed-delay trên pool thích hợp thường rõ hơn.

## 10. Giải pháp 3: fixed-delay callback ngắn, ủy quyền xử lý sang executor khác

Một chiến lược là giữ đoạn chạy trên scheduler thread cực ngắn, chỉ nộp việc nặng sang một `ThreadPoolTaskExecutor` hoặc message queue:

```java
@Scheduled(
        fixedDelay = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "simpleScheduler")
public void pollAsync() {
    workerExecutor.execute(() -> remoteService.refresh());
}
```

Đây là snippet tích hợp với `workerExecutor` và `remoteService` do ứng dụng cung cấp. Nó giải phóng scheduler thread ngay sau khi submit thành công, nhưng có **thay đổi ngữ nghĩa quan trọng**:

- `fixedDelay` lúc này đo từ **thời điểm callback submit kết thúc**, **không** phải từ lúc `remoteService.refresh()` ở worker hoàn thành.
- Nếu xử lý remote mất 45 giây còn callback submit mỗi 5 giây, có thể tích tụ 9 hoặc nhiều công việc trong executor/queue. Không còn bảo đảm “mỗi lần xử lý hoàn tất rồi nghỉ 5 giây”.
- Khi worker executor bị reject, exception xuất hiện ở callback và phải có chính sách rõ ràng: trả lỗi, skip, retry với backoff hoặc lưu job bền vững.
- Nếu task quan trọng, queue trong RAM không đủ để chống mất việc khi restart.
- Có thể cần `Semaphore`/bulkhead/atomic admission guard để giới hạn tối đa số task đang chạy.
- Nếu callback lại gọi `future.join()` để đợi worker hoàn tất, scheduler thread vẫn bị **chặn**; việc offload không còn giải quyết head-of-line blocking.

### Ví dụ giới hạn một công việc in-flight

```java
private final AtomicBoolean inFlight = new AtomicBoolean(false);

@Scheduled(
        fixedDelay = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "simpleScheduler")
public void pollOnceIfIdle() {
    if (!inFlight.compareAndSet(false, true)) {
        return; // Quy tắc skip thay vì tạo backlog.
    }

    try {
        workerExecutor.execute(() -> {
            try {
                remoteService.refresh();
            } finally {
                inFlight.set(false);
            }
        });
    } catch (RuntimeException ex) {
        inFlight.set(false); // Submission thất bại, không để guard mắc kẹt.
        throw ex;
    }
}
```

Đây là mảnh tích hợp trong class, cần `AtomicBoolean` và dependencies. Nó ngăn **các bản sao đang được submit thành công** chồng lấn trên một instance, nhưng “skip nếu đang bận” **không** giữ đúng fixed-delay tính từ hoàn tất công việc thật. Cần phân biệt semantics trong tài liệu API.

Cũng cần thận trọng: nếu executor dùng `CallerRunsPolicy` khi bị quá tải, `execute(...)` có thể chạy tác vụ trên **chính scheduler caller thread**, tái tạo vấn đề blocking. Hãy xem rejection policy, không chỉ tên executor.

### Có cách giữ đúng fixed-delay sau khi offload không?

Có, nhưng phải điều khiển lịch **sau khi công việc thực sự kết thúc** thay vì để callback cố định tiếp tục tick:

1. Khi một job hoàn thành, ghi nhận completion.
2. Tính thời điểm `completion + delay`.
3. Lên lịch một lần mới với `TaskScheduler.schedule(Runnable, Instant)`.
4. Giới hạn số job đang chạy và xử lý lỗi/restart.
5. Nếu cần đảm bảo bền vững, lưu lịch vào DB/job queue.

Đây là pattern tự lập lịch (*self-rescheduling*) với lifecycle riêng. Nó phức tạp hơn fixed-delay có sẵn nên chỉ dùng khi thật sự cần worker isolation và semantics completion-to-next-start, hoặc cần persistence. Không tự lập lịch vô hạn mà bỏ qua shutdown/cancellation.

## 11. Trường hợp @Scheduled trả Publisher/reactive type

Spring Framework 6.1+ hỗ trợ một số method `@Scheduled` trả reactive `Publisher` hoặc kiểu có thể adapt thành Publisher, với điều kiện hỗ trợ deferred subscription.

Điểm dễ nhầm: với `@Scheduled(fixedDelay = ...)` trên một reactive method, Spring có thể **đợi subscription kết thúc** để giữ fixed-delay semantics. Khi callback/đăng ký subscription bị chặn theo semantics này, một scheduler thread có thể tiếp tục bị giữ.

Vì vậy, chỉ thay phương thức `void poll()` bằng `Mono<Void> poll()` không đủ để khẳng định callback fixed-delay đã trở nên “không blocking scheduler”. Tìm hiểu rõ subscription lifecycle, onError handling, cancellation, retry và cách Framework schedule các subscriptions.

Đặc biệt:

- Reactive `@Scheduled` method thường được gọi để lấy Publisher một lần, rồi framework đăng ký các subscription theo lịch. Đừng ghi logic side effect vào lúc **tạo Publisher** nếu muốn nó xảy ra ở mỗi lần subscribe; dùng nguồn deferred thích hợp.
- Các `onNext` values không phải dữ liệu trả về cho caller của scheduler; chúng thường bị bỏ qua.
- Lỗi `onError` được Spring logging và xử lý theo semantics reactive scheduling; không mặc định nó được ném ra như exception từ `Runnable.run()` cho `ErrorHandler`.
- Với fixed-delay, framework cần biết khi nào subscription hoàn tất trước khi tạo lần tiếp theo.
- Một publisher không hoàn tất có thể khiến fixed-delay không tiếp tục; cần timeout và terminal condition khi phù hợp.

Đây là vấn đề bổ sung **có liên hệ** đến giới hạn “đợi completion trên scheduler thread”; không nên thay toàn bộ fixed-delay blocking jobs bằng reactive syntax rồi mặc định đã loại bỏ bottleneck.

## 12. Error handling, cancellation và lifecycle: chậm khác với hỏng

**Chậm** không đồng nghĩa **lỗi**. Một task có thể không bao giờ ném exception nhưng vẫn giữ scheduler thread vì đang đợi connection vô hạn.

### Lỗi bên trong callback

Các scheduled jobs có thể có `ErrorHandler`/wrapper ghi lỗi. Với periodic scheduling, framework thường cần tiếp tục lịch sau lỗi theo một chính sách xác định. Không dựa vào một `try/catch` trong callback để hứa scheduler luôn tiếp tục: exception, cancellation và handler của implementation cần được kiểm tra trên phiên bản thật.

Những biện pháp hữu ích:

- Kết nối HTTP/DB có connect/read/query timeout.
- Ranh giới transaction ngắn; không giữ transaction qua các phép chờ không cần thiết.
- Retry budget, exponential backoff và circuit breaker.
- Log/metric gắn job ID, loại exception và độ dài thực thi.
- Nếu một job là critical, lưu status và lỗi bền vững thay vì chỉ in stack trace.

### Hủy ScheduledFuture

`scheduleWithFixedDelay(...)` trả một `ScheduledFuture<?>` cho lịch lặp. Gọi `cancel(...)` có thể ngăn các lần kích hoạt tiếp theo, nhưng:

- `cancel(false)` không yêu cầu interrupt lần chạy đang thực thi.
- `cancel(true)` có thể yêu cầu interrupt tùy implementation/trạng thái; không bảo đảm driver JDBC hoặc remote API dừng lập tức.
- Hủy lịch **không phải** rollback side effects đã xảy ra.
- Với task đã bị ủy quyền sang worker thread khác, hủy handle của **lịch** không nhất thiết hủy công việc đang chạy ở executor kia.
- Với các lịch chạy hàng giờ/ngày, handle `ScheduledFuture` nằm trong JVM, không phải persistent job record.

**Cần mở latch, giải phóng lock và dọn tài nguyên trong `finally` khi test** để tránh treo cả JVM. Với production, dùng resource cancellation API/timeout thực tế.

### Shutdown của SimpleAsyncTaskScheduler

`SimpleAsyncTaskScheduler` tham gia lifecycle Spring; khi dừng có thể ngừng firing triggers/fixed-delay execution, nhưng các execution threads đã được hand-off có giới hạn quản lý khác. Javadoc cảnh báo scheduled future của nó không nhất thiết đại diện toàn bộ completion của execution riêng, và lifecycle stop không mặc định dừng mọi task đã được hand-off.

Nếu cần shutdown có điều kiện, theo dõi task completion riêng và xem `setTaskTerminationTimeout(...)`/các tùy chọn đóng executor thích hợp, nhưng không coi đó là cam kết mọi remote I/O sẽ tự ngừng.

## 13. Quan sát và định lượng nguy cơ trong production

Chỉ nhìn tổng số virtual threads hoặc số lượng scheduled annotations không đủ để phát hiện scheduler-thread bottleneck. Hãy thu thập:

| Chỉ số | Nó trả lời câu hỏi gì? |
| --- | --- |
| `scheduledDueAt` | Lịch lẽ ra bắt đầu lúc nào? |
| `actualStartAt` | Callback bắt đầu thực tế lúc nào? |
| `startLag = actualStartAt - scheduledDueAt` | Trễ do scheduler/queue/host bao nhiêu? |
| `executionDuration` | Callback giữ thread bao lâu? |
| `runCount`, success, failure, cancel | Tác vụ có thực sự hoàn thành? |
| active scheduler threads | Các scheduler workers có bị chiếm không? |
| running fixed-delay jobs | Có nhiều job đang đợi một worker không? |
| pending DB connections / HTTP requests | Có bottleneck downstream gây kéo dài callback? |
| queue wait và rejection ở worker executor | Khi offload có tạo bottleneck mới không? |
| p50/p95/p99 lag và duration | Tác động tới tail latency và deadline ra sao? |

Với fixed-delay, thời điểm lần sau phụ thuộc completion lần trước; cần xây metric phù hợp thay vì mặc định so mọi tick với một nhịp cố định. Nếu cần một benchmark kiểm soát, dùng `CountDownLatch`/barrier để giữ A rồi chứng minh B chưa thể chạy, như ví dụ Maven.

### Một quy trình phân tích khi thấy “scheduler không chạy đúng giờ”

1. Kiểm tra instance scheduler thực tế (`SimpleAsyncTaskScheduler` hay `ThreadPoolTaskScheduler`) trong Spring context.
2. Kiểm tra `@Scheduled` dùng `fixedDelay`, `fixedRate` hay `cron`, có `scheduler` qualifier hay không.
3. Chụp thread dump/JFR khi bị trễ: scheduler thread có đang đợi JDBC/HTTP/socket/lock/sleep không?
4. Đo thời gian callback và dependency latency. Nếu một job giữ thread quá lâu, xác định nguyên nhân.
5. Kiểm tra có scheduler bean khác hoặc pool tách biệt cho job cần độc lập chưa.
6. Kiểm tra callback có offload rồi vô tình `join()`/`get()` chờ kết quả ngay không.
7. Kiểm tra Boot virtual-thread auto configuration và giới hạn concurrency.
8. Tái hiện với latch trong integration test đúng phiên bản/framework/config trước khi quyết định refactor.

**Đừng vội tăng thread** nếu lỗi gốc là HTTP timeout vô hạn, deadlock hoặc SQL chậm. Cần giải quyết giới hạn từng dependency và thiết kế isolation tổng thể.

## 14. Kiểm thử đáng tin cậy: tối thiểu những trường hợp nào?

| Kịch bản | Cách dựng | Kết quả cần chứng minh |
| --- | --- | --- |
| Hai fixed-delay trên cùng SimpleAsyncTaskScheduler | Giữ A bằng latch, đăng ký B | B không chạy trong lúc A giữ scheduler thread |
| Thả A | `countDown` rồi chờ B | B bắt đầu sau đó, trên scheduler thread |
| Một fixed-delay callback quá lâu | Dùng fake dependency có barrier | Start lag của job khác tăng; không dựa vào timing ngẫu nhiên |
| Đổi sang ThreadPoolTaskScheduler pool=2 | Hai job chạy/giữ hai worker | Hai lịch khác nhau có thể thực thi song song |
| Pool size=1 của ThreadPoolTaskScheduler | Cấu hình một worker | Dù đổi class, vẫn có nguy cơ serialize |
| Java 21 virtual threads bật | `setVirtualThreads(true)` hoặc Boot property | Fixed-delay vẫn trên scheduler thread; fixed-rate/cron cần test riêng |
| Offload sang worker executor | Callback chỉ submit | Scheduler thread rảnh nhanh nhưng semantics completion-to-next-start đổi |
| Worker executor reject | Saturate giới hạn | Rejection không bị im lặng bỏ qua, state phục hồi đúng |
| Cancel/stop trong lúc A blocked | Dùng latch/timeout & cleanup | Không để thread và lịch tồn tại vô hạn |
| Nhiều pod | Khởi chạy hai instance | Có chính sách duplicate/idempotency nếu chỉ muốn một job toàn cluster |

### Những assertion có giá trị hơn Thread.sleep

Trong ví dụ mục 6, `awaitOrFail(enteredA, ...)` là mốc chứng minh A **đã** bắt đầu. `releaseA` giữ A chưa thể hoàn thành. `enteredB.await(200ms)` khi A còn blocked cho giá trị false; sau release, `enteredB.await(5s)` phải true. So sánh `Thread` bằng identity chứng minh hai callback chạy trên cùng một thread.

Đây là test về **sự phụ thuộc vào một scheduler thread**, không phải chỉ đo thời gian “B chậm hơn A vài mili-giây”.

Chạy integration tests trên đúng version Spring/Boot mà dịch vụ deploy. Với hành vi dispatch fixed-rate/cron và lỗi/backlog cụ thể, không dựa vào mỗi log từ một bug report ở phiên bản cũ.

## 15. Sai lầm phổ biến khi lựa chọn scheduler

### “Bật virtual threads thì mọi @Scheduled chạy trên virtual thread”

**Sai.** Với `SimpleAsyncTaskScheduler`, fixed-delay trực tiếp chạy trên scheduler thread; chỉ những đường hand-off mới được tạo execution thread riêng theo cấu hình virtual/platform.

### “setConcurrencyLimit(100) sẽ cho 100 fixed-delay jobs song song”

**Sai.** Giới hạn concurrency của thread-per-task executor không biến single scheduler thread thành 100 fixed-delay workers. Nếu cần nhiều fixed-delay jobs chạy độc lập, cân nhắc pool-based scheduler hoặc tách scheduler.

### “Thay fixedDelay bằng fixedRate giữ nguyên semantics”

**Sai.** Fixed-rate dựa trên nhịp start time, có thể chồng lấn/đẩy tải khi task lâu. Chỉ chuyển khi nghiệp vụ cho phép và có admission control.

### “Thay bằng ThreadPoolTaskScheduler nhưng không đặt poolSize”

Có thể vẫn **serialize** vì pool mặc định là 1. Đặt pool size hợp lý dựa trên số job, blocking duration và capacity downstream.

### “Mỗi job fixed-delay được bảo vệ khỏi job khác vì fixed-delay không overlap”

Không overlap **các lần chạy của chính một lịch** khác với **hai lịch độc lập có được phép song song hay không**. `SimpleAsyncTaskScheduler` khiến nhiều lịch fixed-delay chia sẻ thread; pool-based scheduler có thể cho chúng chạy đồng thời.

### “Task không ném lỗi tức là scheduler tốt”

Một task chờ HTTP vô hạn không ném exception nhưng vẫn giữ scheduler thread. Cần timeout, lag metric, thread dump và circuit breaker.

### “Offload rồi return là giữ đúng fixed-delay”

**Sai.** Delay bắt đầu từ lúc callback submit xong, không phải completion của việc ở worker. Dùng self-rescheduling/completion tracking nếu cần semantics nguyên bản.

### “Offload qua executor khác nhưng gọi future.get() ngay là giải quyết blocking”

**Sai.** `get()/join()` vẫn giữ scheduler thread chờ kết quả. Nếu cần chờ completion, cân nhắc pool-based scheduler hoặc kiến trúc tự reschedule trên completion.

### “Có bao nhiêu virtual threads cũng không bao giờ nghẽn DB”

Virtual threads nhẹ nhưng DB/HTTP connection pool và remote API có quota hữu hạn. Phải có bounded concurrency/backpressure.

### “@Scheduled có scheduler qualifier nhưng vẫn dùng default”

Kiểm tra tên bean/qualifier và log/metric. `@Scheduled.scheduler` (Spring 6.1+) dùng để chọn scheduler; không bỏ qua annotation và cấu hình cho rằng mọi method luôn đi qua một executor.

### “Một @Scheduled job chạy đúng một lần trên toàn cluster”

Khi có nhiều application instances, mỗi instance có thể đăng ký cùng `@Scheduled`. Cần distributed coordination/idempotency nếu nghiệp vụ yêu cầu chỉ một lần toàn cluster.

### “ScheduledFuture.isDone() luôn nghĩa job nghiệp vụ đã kết thúc”

Không được giả định cho `SimpleAsyncTaskScheduler` ở đường hand-off: future có thể đại diện việc đã giao execution thread chứ chưa phải completion. Phải theo dõi kết quả nghiệp vụ riêng.

## 16. Checklist lựa chọn scheduler và tài liệu chính thức

Trước khi quyết định cách triển khai một fixed-delay task:

1. **Đúng ngữ nghĩa thời gian?** Có thật sự cần “hoàn tất rồi chờ delay” hay cần fixed-rate/cron?
2. **Task có blocking không?** JDBC/HTTP/lock/sleep và timeout tối đa là gì?
3. **Bao nhiêu lịch độc lập?** Nếu có nhiều lịch cần chạy đồng thời, tránh đặt tất cả fixed-delay trên một `SimpleAsyncTaskScheduler`.
4. **Chạy với Java mấy?** Java 17 có platform threads; virtual threads cần Java 21+. Đừng bật flag virtual trên Java 17.
5. **Scheduler bean thực tế là gì?** Boot có thể auto-config SimpleAsyncTaskScheduler khi bật virtual threads; pool-size property không giải quyết đường fixed-delay.
6. **Có thread isolation không?** Chọn ThreadPoolTaskScheduler pool size đủ hoặc scheduler riêng cho job quan trọng.
7. **Có đổi semantics khi offload không?** Callback chỉ submit không đồng nghĩa completion của công việc thật.
8. **Có nguy cơ overlap không?** Nếu chuyển sang fixed-rate/cron, cần kiểm soát concurrency và idempotency.
9. **Timeout/quota downstream thế nào?** Không để scheduler thread chờ vô hạn; không mở quá nhiều connections.
10. **Monitoring?** Đo start lag, execution duration, queue/rejection, scheduler thread dump và error rate.
11. **Shutdown/restart?** Lịch trong RAM không bền vững; nếu cần durable jobs dùng job store/queue/Quartz với cấu hình đúng.
12. **Multi-instance?** Đảm bảo claim/idempotency nếu chỉ muốn một worker cluster xử lý job.
13. **Integration test?** Dùng latch tạo trạng thái bị chặn và đo đúng Spring version/instance.
14. **Rollback plan?** Khi thay đổi loại scheduler, kiểm tra chính xác nhịp chạy, behavior khi quá tải, shutdown và trạng thái nghiệp vụ.

### Bảng lựa chọn nhanh

| Nhu cầu | Lựa chọn phù hợp hơn | Cần cảnh giác |
| --- | --- | --- |
| Fixed-delay đơn giản, callback rất ngắn và chấp nhận tuần tự | SimpleAsyncTaskScheduler | Mọi fixed-delay jobs cùng chia scheduler thread |
| Nhiều fixed-delay jobs blocking cần hoạt động độc lập | ThreadPoolTaskScheduler có pool size >1 | Pool vẫn hữu hạn, không chống mọi slowdown |
| Tác vụ theo nhịp start time, độc lập và có thể chồng lấn | Fixed-rate với executor/bulkhead phù hợp | Burst, side effects, overload |
| Lịch theo thời điểm trên lịch, có timezone | CronTrigger/`@Scheduled(cron=...)` | DST, missed ticks, overlap |
| JDK 21+ rất nhiều công việc blocking độc lập | SimpleAsyncTaskScheduler với virtual threads cho fixed-rate/cron | Fixed-delay vẫn single-thread và downstream quotas vẫn hữu hạn |
| Job dài nhưng không được overlap | Pool-based fixed-delay hoặc completion-driven self-rescheduling | Cần giữ chính xác completion-to-next-start |
| Job phải sống qua restart/cluster | Durable scheduler/job store/Quartz/queue | Claim, retry, idempotency, misfire |
| Job quan trọng không được bị job kém ưu tiên làm trễ | Scheduler isolation | CPU, DB và network vẫn có thể cùng bottleneck |

### Tài liệu chính thức để đối chiếu

1. [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): giải thích `TaskScheduler` và lưu ý fixed-delay của `SimpleAsyncTaskScheduler` chạy trên single scheduler thread.
2. [Spring Framework 6.2 – SimpleAsyncTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/SimpleAsyncTaskScheduler.html): thiết kế single scheduler, thread-per-execution, giới hạn fixed-delay và semantics ScheduledFuture.
3. [Spring Framework 6.2 – @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): fixedDelay/fixedRate, scheduler qualifier và cảnh báo virtual threads.
4. [Spring Framework 6.2 – ThreadPoolTaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): pool size và execution trên các scheduler workers.
5. [Spring Boot 3.5 – Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html): virtual-thread auto-configuration và sự khác biệt với pool-based scheduler.
6. [Spring Boot – SpringApplication, Virtual Threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads): Java 21+, daemon threads và keep-alive.
7. [Java SE 17 – CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): thiết kế test blocking/ordering có thể tái hiện.
8. [Java SE 17 – ScheduledFuture](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html): trạng thái/cancellation của lịch.
9. [Spring Framework issue #33408](https://github.com/spring-projects/spring-framework/issues/33408): ví dụ lịch fixed-delay gây trì hoãn fixed-rate trên Spring 6.1.11; chỉ dùng như minh họa phiên bản được báo cáo, không thay thế test của bản hiện tại.

**Kết luận:** `SimpleAsyncTaskScheduler` rất phù hợp với mô hình “điều phối một thread, dispatch nhiều executions riêng”, nhất là cùng virtual threads cho fixed-rate/cron. Nhưng **fixed-delay là ngoại lệ thực thi trực tiếp trên một scheduler thread**. Với nhiều job fixed-delay có blocking I/O, đây là điểm nghẽn có thể khiến một job chậm làm trễ nhiều job khác. Muốn isolation, hãy chọn `ThreadPoolTaskScheduler` với pool size hợp lý, tách scheduler hoặc thiết kế điều phối qua completion — và luôn giữ nguyên semantics nghiệp vụ khi thay đổi chiến lược.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduler-selection-008">Tách bộ lập lịch khỏi nhóm luồng xử lý khi cần kiểm soát hai loại tải</a>

<details>
<summary>Xem chi tiết</summary>

# Tách bộ lập lịch khỏi nhóm luồng xử lý khi cần kiểm soát hai loại tải

## 1. Bài toán: “đúng giờ” và “xử lý xong” là hai năng lực khác nhau

Trong Spring Framework, một công việc theo lịch có thể chứa hai hoạt động rất khác nhau:

1. **Lập lịch (scheduling):** xác định *khi nào* cần kích hoạt công việc theo fixed rate, fixed delay, cron hoặc một `Trigger`.
2. **Xử lý (execution):** thực hiện *công việc thật*: truy vấn database, gọi API, tính toán, xuất báo cáo, gửi thông báo hoặc chạy một batch job.

Nếu phương thức `@Scheduled` vừa đợi tới thời điểm kích hoạt, vừa trực tiếp chạy nghiệp vụ mất nhiều giây/phút, chính thread scheduler có thể bị giữ bởi công việc đó. Một tác vụ chậm có thể làm tác vụ lịch khác bị trễ. Ngược lại, khi chỉ tách phần nghiệp vụ ra executor, scheduler có thể tiếp tục kích hoạt đúng nhịp nhưng **worker pool có thể đầy hoặc hàng đợi cứ dài ra**.

Đó là lý do đôi khi cần **hai nhóm tài nguyên riêng**:

- **Scheduler pool:** giữ các lời gọi kích hoạt lịch ngắn, đều đặn, ít blocking; tối ưu độ trễ kích hoạt và độ ổn định của timer.
- **Worker/executor pool:** nhận công việc nghiệp vụ, giới hạn số tác vụ đồng thời, queue, timeout, rejection và khả năng hấp thụ burst.

**Tách scheduler khỏi worker không làm công việc tự chạy nhanh hơn, không tăng năng lực database và không tạo hàng đợi bền vững.** Nó cho phép **điều khiển và đo hai loại tải một cách độc lập**, đồng thời cô lập tác động của tác vụ nghiệp vụ chậm lên nhịp kích hoạt.

Bài này tập trung vào các abstraction `TaskScheduler`, `TaskExecutor` của Spring, cách thiết kế `@Scheduled` chỉ để dispatch, ví dụ Java 17+/Spring 6.2.9 chạy được, hệ quả của `fixedDelay`/`fixedRate`, backpressure và các lỗi vận hành thường gặp.

## 2. Điều gì xảy ra nếu dùng chung thread để chờ lịch và làm việc?

Một `@Scheduled` method chạy trực tiếp nghiệp vụ có thể trông đơn giản:

```java
@Scheduled(fixedDelay = 1_000)
public void synchronize() {
    fetchFromRemoteApi(); // Có thể chặn 30 giây.
    updateDatabase();     // Có thể chặn hoặc giữ lock.
}
```

Đây là **mảnh mã Spring** giả định đã có các phương thức nghiệp vụ, bean được quản lý và `@EnableScheduling`. Với `ThreadPoolTaskScheduler` truyền thống, callback lịch được thực hiện **trên các scheduler thread**, không tự động chuyển sang một worker pool riêng. Nếu chỉ có một scheduler thread, phương thức mất 30 giây sẽ giữ chính thread ấy; những scheduled task khác dùng cùng scheduler có thể bị trễ.

```text
Một scheduler thread kiêm luôn worker:

time  ----->

scheduler-1:
    |---- chạy JOB A chậm (30s) ----------------------|
       JOB B tới hạn ---x chưa có thread rảnh
       JOB C tới hạn -------x chưa có thread rảnh
                                                      |---- B hoặc C mới có dịp chạy
```

Lưu ý: với `@Scheduled(fixedDelay=...)`, trigger kế tiếp của **cùng một lịch** được tính sau khi lần chạy trước hoàn tất; đây là semantics có chủ đích, không phải lúc nào cũng gọi là lỗi. Vấn đề là **tác vụ A đang chiếm năng lực lập lịch chung** khiến những công việc khác bị ảnh hưởng, hoặc việc chạy nghiệp vụ lâu nằm sai trên nhóm thread bạn định dùng cho timer.

Nếu tăng số scheduler threads, một số công việc khác có thể bớt bị trễ. Nhưng khi nhiều scheduled method chặn hoặc kéo dài, scheduler pool vẫn có thể cạn; hơn nữa bạn đang phải dùng cùng tham số pool để cân bằng **độ chính xác của timer** và **mức song song nghiệp vụ**.

## 3. Mô hình tách hai tầng

Thay vì chạy toàn bộ nghiệp vụ trong callback `@Scheduled`, cho callback **gửi một task ngắn** vào executor chuyên xử lý:

```text
                ThreadPoolTaskScheduler       ThreadPoolTaskExecutor
                      "timerPool"                   "workPool"
cron / fixedRate ---> scheduler thread
                          |
                          | tick(): tạo yêu cầu xử lý
                          | workPool.execute(task)
                          v
                   return rất nhanh  -------> Queue bounded
                                                   |
                                            worker-1, worker-2...
                                                   |
                                            DB / API / business logic
                                                   |
                                             completed / failed
```

Mô hình này tạo ra **hai đường thời gian riêng**:

- **Schedule time:** thời điểm theo cron/rate/delay mà callback dự định kích hoạt.
- **Dispatch time:** thời điểm callback nộp task và executor nhận hoặc từ chối.
- **Start time:** thời điểm worker bắt đầu xử lý task (có thể muộn do queue).
- **Completion time:** thời điểm công việc nghiệp vụ thực sự hoàn tất.

Một scheduled tick được dispatch thành công **không đồng nghĩa task đã bắt đầu**, và một task đã bắt đầu **không đồng nghĩa thành công**. Nếu cần báo trạng thái cho API/người dùng, phải đo completion hoặc lưu trạng thái job riêng.

## 4. Các abstraction của Spring cần phân biệt

| Thành phần | Trách nhiệm chính | Đặc tính lưu ý |
| --- | --- | --- |
| `TaskScheduler` | Lên lịch chạy một `Runnable` theo `Instant`, `Trigger`, fixed rate/delay | Không hứa lưu lịch bền vững qua crash |
| `ThreadPoolTaskScheduler` | Triển khai `TaskScheduler` bằng `ScheduledThreadPoolExecutor` | **Callback chạy trên scheduler thread(s)**; pool size riêng, mặc định thường là 1 |
| `TaskExecutor` | Thực thi một `Runnable` khi được giao | Không tự có logic cron hoặc lịch chạy lặp |
| `AsyncTaskExecutor` | Bổ sung khả năng submit/handle cho tác vụ | Có thể đưa về `Future` |
| `ThreadPoolTaskExecutor` | Dựa trên `ThreadPoolExecutor`, hỗ trợ core/max/queue | Bounded pool và rejection; queue in-memory |
| `@Scheduled` | Khai báo trigger cho method Spring-managed | Cần `@EnableScheduling`; method thường không nhận args |
| `@Async` | Đưa lời gọi method qua Spring async advice sang executor | Cần `@EnableAsync`, lời gọi qua proxy; **khác** scheduler |
| `SchedulingConfigurer` | Chọn/configure scheduler ở tầng đăng ký tác vụ | Hữu ích khi phải kiểm soát lịch phức tạp |
| `ScheduledFuture` | Handle lịch đã đăng ký/chạy theo `TaskScheduler` | Không đại diện cho hàng đợi nghiệp vụ bền vững |

Một điểm đặc biệt: `ThreadPoolTaskScheduler` cũng triển khai một số giao diện executor/scheduling task khác, nhưng điều đó **không có nghĩa nó tự chuyển callback sang một nhóm worker riêng**. Theo JavaDoc Spring, `ThreadPoolTaskScheduler` truyền thống thực thi callback trực tiếp trên scheduler thread. Nếu muốn chia hai loại tải, cần **thực sự dispatch sang một `TaskExecutor` khác**, không chỉ đổi tên bean.

## 5. Vì sao chọn tách pool? Những mục tiêu cụ thể

Tách scheduler và worker hữu ích khi:

- **Job xử lý lâu hoặc blocking I/O**: một lần gọi database bị giữ 20 giây không nên khóa timer dùng để kích hoạt các công việc ngắn khác.
- **Nhiều loại job có nhịp khác nhau**: task kiểm tra heartbeat mỗi vài giây cạnh tranh với batch tổng hợp báo cáo mỗi giờ.
- **Cần giới hạn throughput/downstream**: worker pool có thể có `core/max/queue` và admission policy riêng, không phải mở thêm scheduler threads.
- **Muốn đo riêng trễ lịch và trễ xử lý**: biết một việc tới hạn đúng giờ nhưng còn chờ 2 phút trong worker queue, thay vì gộp thành “scheduler chậm”.
- **Cần xử lý quá tải rõ ràng**: từ chối, coalesce, skip, ghi pending job bền vững, retry có kiểm soát.
- **Một job chạy lâu không được làm trễ các job quan trọng khác**: chia scheduler theo nhóm ưu tiên hoặc giữ callback lịch thật ngắn.

Tuy nhiên, không phải dự án nào cũng cần hai pool. Một vài scheduled method ngắn, ít khi chặn, không có yêu cầu kích hoạt chính xác có thể dùng `ThreadPoolTaskScheduler` đơn giản. Tách pool tạo thêm queue, cấu hình, shutdown order và metric; chỉ nên thêm khi có nhu cầu cách ly hoặc điều khiển tải.

## 6. Ví dụ Spring Framework 6.2.9 + Java 17+ chạy độc lập

Ví dụ dưới đây chứng minh **scheduler có thể dispatch tác vụ thứ hai khi worker vẫn đang bị giữ bởi tác vụ thứ nhất**.

Chúng ta dùng:

- `ThreadPoolTaskScheduler` tên `timerPool` với 1 scheduler thread.
- `ThreadPoolTaskExecutor` tên `workPool` với 1 worker và queue capacity = 1.
- `@Scheduled(fixedDelay = 100, initialDelay = 100)` để tạo hai lượt dispatch đầu.
- `CountDownLatch` cố tình giữ task đầu trong worker. Task thứ hai được nộp vào hàng đợi trong khi task đầu chưa hoàn tất.
- Một `Probe` chứa các latch và thread name. Không dùng `Thread.sleep` để đoán thứ tự thread.

### Cấu trúc Maven

```text
scheduler-separation/
├── pom.xml
└── src/main/java/demo/SchedulerSeparationDemo.java
```

Nội dung `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>scheduler-separation</artifactId>
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
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-observation</artifactId>
            <version>1.14.9</version>
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

Dependency `micrometer-observation` được khai báo tường minh trong demo để có các lớp observation mà hạ tầng `@Scheduled` Spring 6.2 sử dụng; nó kéo theo các phụ thuộc cần thiết của Micrometer. Trong dự án Spring Boot, các version thường được quản lý bởi BOM của Boot; không nên tùy tiện override nếu không cần.

Nội dung `src/main/java/demo/SchedulerSeparationDemo.java`:

```java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulerSeparationDemo {

    @Bean("timerPool")
    public ThreadPoolTaskScheduler timerPool() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("timer-");
        return scheduler;
    }

    @Bean("workPool")
    public ThreadPoolTaskExecutor workPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("job-");
        return executor;
    }

    public static final class Probe {
        final CountDownLatch submitted = new CountDownLatch(2);
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(2);
        final AtomicInteger tickCount = new AtomicInteger();

        volatile String schedulerThread;
        volatile String workerThread;
    }

    @Bean
    public Probe probe() {
        return new Probe();
    }

    @Bean
    public DispatchJob dispatchJob(
            Probe probe,
            @Qualifier("workPool") ThreadPoolTaskExecutor pool) {
        return new DispatchJob(probe, pool);
    }

    public static final class DispatchJob {
        private final Probe probe;
        private final ThreadPoolTaskExecutor workPool;

        public DispatchJob(Probe probe, ThreadPoolTaskExecutor workPool) {
            this.probe = probe;
            this.workPool = workPool;
        }

        @Scheduled(initialDelay = 100, fixedDelay = 100)
        public void tick() {
            int tickNumber = probe.tickCount.incrementAndGet();
            if (tickNumber > 2) {
                return; // Demo chỉ dispatch hai lượt để kiểm tra.
            }

            probe.schedulerThread = Thread.currentThread().getName();

            workPool.execute(() -> {
                probe.workerThread = Thread.currentThread().getName();

                if (tickNumber == 1) {
                    probe.firstStarted.countDown();
                    try {
                        probe.releaseFirst.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                probe.completed.countDown();
            });

            probe.submitted.countDown();
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(
                             SchedulerSeparationDemo.class)) {

            Probe probe = context.getBean(Probe.class);

            try {
                if (!probe.firstStarted.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("First worker did not start");
                }
                if (!probe.submitted.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Second task not submitted");
                }

                System.out.println("timer thread = "
                        + probe.schedulerThread.startsWith("timer-"));
                System.out.println("work thread = "
                        + probe.workerThread.startsWith("job-"));
                System.out.println("second submitted while first blocked = "
                        + (probe.completed.getCount() == 2));

                probe.releaseFirst.countDown();

                if (!probe.completed.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Jobs did not complete");
                }
                System.out.println("completed jobs = 2");
            } finally {
                // Tránh để worker bị giữ nếu phép kiểm tra có ngoại lệ.
                probe.releaseFirst.countDown();
            }
        }
    }
}
```

Chạy tại thư mục `scheduler-separation` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.SchedulerSeparationDemo
```

Stdout **do chương trình in**, không tính log của Maven/JShell:

```text
timer thread = true
work thread = true
second submitted while first blocked = true
completed jobs = 2
```

**Giải thích theo thứ tự:**

1. `@EnableScheduling` kích hoạt xử lý `@Scheduled`. Context có đúng một bean `TaskScheduler` (`timerPool`), nên cơ chế mặc định chọn scheduler đó. `workPool` là `TaskExecutor` riêng, không phải scheduler.
2. Tick đầu được `timerPool` kích hoạt sau initial delay. Trong method `tick()`, scheduler lập tức gọi `workPool.execute(...)` và trả về.
3. Worker của `workPool` bắt đầu task đầu, báo `firstStarted` rồi chờ `releaseFirst`. Lúc này worker đang bận nhưng **scheduler thread đã trở lại**.
4. Sau fixed delay từ lúc `tick` trả về, scheduler kích hoạt tick thứ hai và nộp task thứ hai vào queue capacity 1. `submitted` đếm đủ 2 trong khi task đầu còn bị giữ; `completed` vẫn ở 2.
5. Main thread xác nhận tên thread có tiền tố khác nhau, in kết quả rồi thả latch. Worker hoàn tất task đầu, tiếp tục lấy task thứ hai khỏi queue và hoàn tất; tổng hai job đã được ghi nhận.
6. `try/finally` đảm bảo latch được thả nếu test thất bại. Khi ApplicationContext đóng, các executor bean cũng được xử lý theo lifecycle Spring.

Ví dụ chạy trong JShell trên Spring 6.2.9/JDK với các thư viện Spring và Micrometer tương ứng; bốn dòng stdout đã được kiểm tra. Để demo nhỏ và có kết quả chắc chắn, hai task chỉ thực hiện đếm/latch; **nó không benchmark throughput và không đo độ ổn định scheduler dưới tải thật**.

**Một giới hạn quan trọng:** vì `tick()` chỉ gọi `execute` rồi trả về, `fixedDelay` được tính từ lúc **dispatch kết thúc**, **không phải** lúc worker thực hiện toàn bộ nghiệp vụ xong. Một task chạy hai phút không tự làm lịch chờ hai phút nếu dispatch chỉ mất vài ms.

## 7. Hai trục thời gian: fixedDelay và fixedRate sau khi tách pool

Spring hỗ trợ ba cấu hình thường dùng:

| Kiểu | Điểm tính lịch tiếp theo | Điều cần lưu ý sau khi dispatch sang executor |
| --- | --- | --- |
| `@Scheduled(fixedDelay = 5_000)` | Từ **kết thúc lần gọi scheduled method** trước tới lần kế tiếp | Nếu method chỉ submit, delay chỉ đợi **kết thúc submit**, không đợi job worker |
| `@Scheduled(fixedRate = 5_000)` | Theo chu kỳ giữa các mốc bắt đầu đã lên lịch | Nếu worker chậm, queue có thể tích lũy nhiều job; không tự tăng khả năng xử lý |
| `@Scheduled(cron = "0 */5 * * * *")` | Theo mốc lịch wall-clock/zone | Nếu worker backlog, các job có thể chạy muộn; cron không tự giải quyết catch-up/replay |

Một ví dụ:

```text
Schedule mỗi 1 giây:
tick:    0s   1s   2s   3s   4s   5s
         |    |    |    |    |    |
submit:  A    B    C    D    E    F
worker:  [-------- A: 4s --------][---- B ----]
queue:        B    B,C  B,C,D  B,C,D,E ...
```

Tác vụ được **kích hoạt đúng nhịp** không có nghĩa kết quả được tạo đúng nhịp. Với một worker, nếu mỗi job tiêu tốn trung bình 4 giây và lịch phát ra 1 job/giây liên tục, hệ thống nhận việc nhanh hơn năng lực xử lý; một queue lớn chỉ làm trì hoãn thời điểm bộc lộ vấn đề.

**Nếu yêu cầu là “đợi job trước xong rồi mới phát job tiếp theo”**, không thể chỉ mong `fixedDelay` của method dispatch bảo đảm việc đó. Cần cơ chế single-flight, semaphore/lease, hoặc chỉ lập lịch lần tiếp theo **sau completion thực** của job. Ngược lại, nếu các job độc lập và có thể overlap, hãy định nghĩa rõ giới hạn số concurrent jobs thay vì cấm mọi overlap một cách mặc định.

Để tránh hiểu nhầm, trong JavaDoc `ThreadPoolTaskScheduler`, nếu task trực tiếp thực thi trên scheduler thread, một `ScheduledFuture` của lịch đơn có thể đại diện cho **completion của callback thực tế**. Nhưng khi callback chỉ submit child task sang executor, kết thúc callback không còn đại diện cho **completion của child nghiệp vụ**.

## 8. Không dùng @Async và @Scheduled như một “phép cộng mặc định”

Một số code đặt cả hai annotation lên cùng method:

```java
@Async("workPool")
@Scheduled(fixedDelay = 1_000)
public void runReport() {
    // Thực hiện nghiệp vụ.
}
```

Đây là **mảnh minh họa trong bean Spring-managed**, cần cả `@EnableScheduling` và `@EnableAsync`, executor phù hợp, cấu hình proxy đúng. Tùy cách Spring đăng ký method và advice, việc kích hoạt qua scheduling có thể dẫn tới async invocation, nhưng chính sách execution và failure cần kiểm tra trên framework version thực tế. Không nên coi hai annotation ghép lại là một kiến trúc tự động có backpressure, no-overlap và durability.

Một thiết kế **dễ quan sát hơn** là:

- Method `@Scheduled` chỉ làm **admission/dispatch**.
- Một bean/dịch vụ xử lý công việc sở hữu business logic, có thể được gọi qua `TaskExecutor.execute` rõ ràng.
- Nếu muốn dùng `@Async` thay vì `execute`, chuyển method async sang **bean khác** và gọi qua Spring proxy; self-invocation `this.asyncMethod()` trong cùng bean ở proxy mode thường bỏ qua async advice.
- Tách tên executor và scheduler rõ ràng: ví dụ `@Scheduled(scheduler = "timerPool", ...)` (thuộc Spring 6.1+) chọn một scheduler theo qualifier, còn `@Async("workPool")` chọn executor cho lời gọi async.

Đặc biệt, **gọi `workPool.execute` ngay trong `@Scheduled`** như ví dụ hoàn chỉnh không yêu cầu `@EnableAsync`, vì thao tác dispatch đã explicit. `@EnableScheduling` là đủ để kích hoạt callback định kỳ.

## 9. Đặt tên và chọn đúng TaskScheduler, TaskExecutor

Theo `@EnableScheduling`, Spring thường tìm một bean `TaskScheduler` duy nhất hoặc bean tên `taskScheduler`; cơ chế tương tự với `ScheduledExecutorService`. Nếu không tìm được bean thích hợp, Spring có thể tạo scheduler đơn luồng cục bộ làm fallback. Điều này có thể khiến ứng dụng chạy được nhưng hành vi timer khác cấu hình bạn tưởng.

Với một scheduler bean và một worker executor, chọn tên rõ và inject chính xác:

```java
@Bean("timerPool")
public ThreadPoolTaskScheduler timerPool() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("timer-");
    return scheduler;
}

@Bean("workPool")
public ThreadPoolTaskExecutor workPool() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("work-");
    return executor;
}
```

Đây là **mảnh cấu hình** có import Spring và class `@Configuration` bao ngoài, không phải program độc lập. Các con số 2/4/8/200 chỉ minh họa, không phải tuning khuyến nghị cho mọi hệ thống.

Nếu có **nhiều scheduler**, có thể chỉ định:

```java
@Scheduled(scheduler = "timerPool", cron = "0 0 * * * *", zone = "UTC")
public void hourlyTick() {
    // Dispatch nhiệm vụ ngắn gọn vào worker executor riêng.
}
```

Tùy phiên bản Spring, còn có thể điều chỉnh `ScheduledTaskRegistrar` qua `SchedulingConfigurer`. Đừng để cùng lúc nhiều scheduler bean không rõ lựa chọn rồi đoán scheduler nào đang dùng dựa trên tên method. Tên thread prefix giúp debug, nhưng cũng phải xác nhận trong metric/configuration thực tế.

## 10. Backpressure: scheduler vẫn chạy nhưng worker không đủ công suất

**Tách pool chỉ cô lập tài nguyên, không tạo công suất mới.** Nếu rate phát task là `λ` jobs/giây và thời gian giữ worker trung bình `S` giây/job, số worker cần bận theo Little's Law ở trạng thái ổn định xấp xỉ:

```text
busyWorkers ≈ λ × S
```

Ví dụ giả định:

- Lịch phát trung bình **5 jobs/s**.
- Mỗi job cần trung bình **0,4 giây worker time**.
- Nhu cầu bận trung bình ≈ `5 × 0,4 = 2` workers.
- Nếu muốn target utilization `u = 0,7`, một ước lượng khởi đầu là `ceil(2 / 0,7) = 3` worker threads.

Đó chỉ là **ước lượng đầu**, không phải bảo đảm SLO. Nếu job dùng CPU nhiều, số threads hữu ích còn bị giới hạn bởi cores/quota; nếu job đợi DB/HTTP, giới hạn có thể là connection pool, downstream rate limit, lock hoặc memory. Burst và phân phối thời gian job (tail latency) cũng cần đo.

Khi workload tăng đột ngột, hàng đợi worker chỉ cho phép **hấp thụ một backlog hữu hạn**. Nếu tốc độ tạo job lâu dài lớn hơn tốc độ hoàn thành, queue sẽ tiếp tục dài ra cho tới khi đụng capacity, gây rejection hoặc memory pressure.

Một số chính sách overload có thể chọn:

| Chính sách | Hành vi | Phù hợp khi |
| --- | --- | --- |
| **Skip tick** | Bỏ lần kích hoạt nếu job cũ còn xử lý | Công việc kiểm tra trạng thái có thể thực hiện lại lần sau |
| **Coalesce** | Gộp nhiều tick thành một nhu cầu chạy | Chỉ cần “cập nhật tới trạng thái mới nhất”, không cần xử lý mọi tick |
| **Reject và báo metric** | Thể hiện rõ pool đã đầy | Muốn bảo vệ tài nguyên downstream |
| **Đưa việc vào kho bền vững** | Lưu job ID/record, worker lấy dần | Không được phép mất việc khi crash hoặc overload |
| **Bounded queue + retry có giới hạn** | Đệm burst nhỏ, trả lỗi khi vượt hạn | Có cơ chế retry an toàn/idempotent |
| **Thay đổi chu kỳ/phát theo phản hồi** | Giảm rate khi backlog tăng | Có quyền điều chỉnh tần suất |

Không nên âm thầm dùng một queue không giới hạn chỉ để tránh rejection. Một queue lớn có thể làm hàng trăm task cũ chạy sau thời điểm chúng còn hữu ích.

### Cạm bẫy CallerRunsPolicy

Với `ThreadPoolTaskExecutor`, nếu cấu hình `ThreadPoolExecutor.CallerRunsPolicy`, khi pool bão hòa, task bị từ chối có thể được **thực thi ngay trên thread gọi `execute`**. Trong mô hình này, **thread gọi chính là scheduler thread**. Khi đó một lần dispatch biến thành làm việc trực tiếp trên scheduler, phá cách ly bạn muốn.

```text
timer thread ---> workPool.execute(task)
                       |
                       +-- pool/queue đầy
                       |
                       +-- CallerRunsPolicy
                                  |
                                  +-- task.run() TRÊN TIMER THREAD
```

`CallerRunsPolicy` có thể được dùng cố ý để tạo backpressure, nhưng phải hiểu chính xác nó đánh đổi **độ trễ scheduler**. Nếu ưu tiên tuyệt đối việc timer không bị chặn, cần một rejection policy rõ ràng, metric và quyết định khác về xử lý job chưa được nhận.

`ThreadPoolTaskExecutor` mặc định thường báo `TaskRejectedException` trong tình huống rejection; phải log/đếm và xử lý, không bỏ qua như thể việc đã được nhận.

## 11. Mẫu single-flight khi không muốn job chồng lấp

Nếu nghiệp vụ yêu cầu **tối đa một job đang chạy trên một instance**, một cờ in-flight đơn giản có thể giúp bỏ lần tick trùng.

Mảnh code tích hợp Spring, giả sử bean có `ThreadPoolTaskExecutor workPool` đã inject:

```java
private final AtomicBoolean inFlight = new AtomicBoolean();

@Scheduled(scheduler = "timerPool", fixedDelay = 1_000)
public void tick() {
    if (!inFlight.compareAndSet(false, true)) {
        return; // Job cũ chưa hoàn tất: skip tick này.
    }

    try {
        workPool.execute(() -> {
            try {
                performWork();
            } finally {
                inFlight.set(false);
            }
        });
    } catch (TaskRejectedException ex) {
        inFlight.set(false); // Dispatch thất bại, không giữ cờ mãi.
        recordRejection(ex);
    }
}
```

Đây là **mảnh tích hợp**, cần imports `AtomicBoolean`, `@Scheduled`, `TaskRejectedException` và hai method nghiệp vụ `performWork`, `recordRejection` do ứng dụng định nghĩa. Nó cố ý minh họa chính sách **skip**, không phải durable job queue. Cờ phải được **giải phóng cả khi task lỗi** và cả khi submit thất bại.

Giới hạn quan trọng:

1. `AtomicBoolean` chỉ ngăn overlap **trong cùng JVM/bean instance**. Hai replicas có thể cùng chạy.
2. Nếu process chết, cờ biến mất; công việc chưa hoàn tất cũng có thể biến mất.
3. Nếu worker bị treo mãi và không chạy `finally`, cờ có thể giữ `true` rất lâu.
4. Nếu cần “đúng một job tại mọi thời điểm” trên cluster, phải dùng coordination bền vững/lease/fencing phù hợp và idempotency; cờ trong RAM không đủ.
5. Chọn skip chỉ khi nghiệp vụ cho phép bỏ một tick. Nếu mỗi tick mang nghĩa một khoản thanh toán/batch không được phép mất, phải lưu job ở nơi bền vững.

Nếu workflow cho phép **N jobs đồng thời**, có thể dùng semaphore hoặc cơ chế admission tương đương; nhớ phân biệt *đã queue* với *đang thực thi*, vì claim slot quá sớm có thể khiến queue chứa các slot chưa thực chạy.

## 12. Chọn giữa job trực tiếp, dispatch ngắn và persistent job store

| Tiêu chí | `@Scheduled` chạy trực tiếp | Scheduler dispatch vào executor | Scheduler phát job vào store/broker bền vững |
| --- | --- | --- | --- |
| Kiến trúc | Đơn giản, ít thành phần | Hai pool và queue trong RAM | Kho job/message và worker độc lập |
| Scheduler thread có thực thi nghiệp vụ? | Có | Chủ yếu submit ngắn | Chủ yếu ghi/đăng ký job |
| Job lâu ảnh hưởng timer khác? | Có thể rất rõ | Ít hơn nếu dispatch không chặn | Ít hơn nếu ghi/publish có timeout rõ |
| Worker concurrency control | Theo pool scheduler | Theo `ThreadPoolTaskExecutor` riêng | Theo consumer/worker và broker/store |
| Job còn khi JVM crash? | Không được bảo đảm | Queue RAM không được bảo đảm | Có thể khôi phục nếu storage/ack/retry thiết kế đúng |
| Retry và dedup sau restart | Không sẵn có | Không sẵn có | Phải cấu hình/thiết kế nhưng có nền tảng để thực hiện |
| Độ phức tạp | Thấp | Trung bình | Cao hơn |
| Phù hợp | Việc nhẹ, có thể chạy lại kỳ sau | Công việc vừa và có thể mất tick theo chính sách | Job quan trọng, cần recovery/replay |

Một số hệ thống chọn Quartz với **persistent JobStore** để lưu lịch và điều phối trong database. Điều này khác `ThreadPoolTaskScheduler` đơn giản của Spring và cần cấu hình recovery/misfire/cluster theo phiên bản Quartz và database. Không phải mọi Scheduler đều bền vững chỉ vì được gọi là “scheduler”.

Nếu một job không được phép mất, chỉ `workPool.execute` rồi trả HTTP 200 hoặc log “scheduled” là chưa đủ. Cần lưu **ý định xử lý** ở store có thể khôi phục và theo dõi trạng thái cho đến hoàn tất; xử lý lặp phải an toàn/idempotent.

## 13. Lỗi, timeout và semantics khi dispatch

Tách pool làm xuất hiện thêm **ranh giới lỗi**:

- **Lỗi lập lịch:** cấu hình cron sai, scheduler không khởi tạo, bean không được register, method không đủ điều kiện.
- **Lỗi dispatch:** executor đã shutdown, pool/queue đầy, `TaskRejectedException` khi `execute`.
- **Lỗi trong worker:** database timeout, API lỗi, business exception hoặc task bị interrupt.
- **Lỗi hoàn tất/ghi trạng thái:** xử lý thực đã xong nhưng không ghi được trạng thái/ack; retry sau đó có thể trùng side effects.

Một `try/catch` quanh `workPool.execute(...)` chỉ bắt **lỗi dispatch đồng bộ**, không tự bắt exception phát sinh trong task khi worker chạy về sau. Ví dụ:

```java
try {
    workPool.execute(() -> {
        // Business exception xuất hiện trên worker sau khi submit.
        performWork();
    });
} catch (TaskRejectedException ex) {
    recordRejection(ex);
}
```

Đây là **mảnh minh họa**, không phải method độc lập. Với `execute`, exception không xử lý có thể đi tới uncaught exception handler/log của worker. Với `submit`, lỗi thường được giữ trong `Future` và caller phải quan sát `get()`/`CompletableFuture.whenComplete` hoặc một cơ chế monitoring. Nếu dùng `@Async void`, cần `AsyncUncaughtExceptionHandler`/log/metrics tương ứng.

Đừng gắn một `Future.get()` ngay bên trong `@Scheduled` sau khi `submit`, vì bạn đang **chặn lại scheduler thread** để đợi worker; mục tiêu tách tải sẽ bị suy giảm. Nếu cần tracking completion, đăng ký callback/ghi kết quả hoặc nhận Future ở một orchestrator không giữ scheduler thread để chờ.

### Timeout không tự hủy job

Một lời gọi `future.get(timeout, unit)` ở observer chỉ giới hạn **thời gian observer chờ**, không tự dừng worker. `CompletableFuture.orTimeout` có thể hoàn tất stage bằng lỗi nhưng không bảo đảm tác vụ nền đã dừng. Nếu job có I/O, cần deadline cho HTTP/DB client và cleanup `finally`, đồng thời quyết định chính sách retry có kiểm soát.

## 14. @Scheduled và @Async khác transaction, context và durability

Khi dispatch task sang worker, bạn đi qua một **thread boundary**:

- Transaction imperative `@Transactional` của scheduler thread không tự chạy theo sang worker. Nếu worker cần DB transaction, hãy đặt boundary trong Spring-managed service/proxy trên worker, hoặc dùng `TransactionTemplate` rõ ràng.
- MDC, `ThreadLocal`, security/trace context không tự đi theo; có thể cần `TaskDecorator` hoặc cơ chế context propagation đúng framework. Không sao chép `EntityManager`/JDBC connection/transaction thread-local giữa threads.
- Payload của job nên là mã định danh, DTO/value object bất biến hoặc snapshot hợp lý; không chuyển một JPA entity đang gắn session hay stream thuộc request sang task chạy muộn.
- `ThreadPoolTaskExecutor` giữ queue trong bộ nhớ và không tự replay sau JVM crash. Nếu cần durable, dùng DB job record, outbox, broker hoặc scheduler store phù hợp.
- Nếu job chạy trên nhiều replicas, mỗi instance có thể kích hoạt lịch độc lập; chỉ dùng cờ local không loại trùng trên cluster.

Ví dụ một worker xử lý bằng bean transaction riêng:

```java
@Service
public class WorkerService {
    @Transactional
    public void process(long jobId) {
        // Đọc/ghi DB trong transaction của chính worker thread.
    }
}

@Service
public class JobDispatcher {
    private final WorkerService workerService;
    private final ThreadPoolTaskExecutor workPool;

    public JobDispatcher(
            WorkerService workerService,
            @Qualifier("workPool") ThreadPoolTaskExecutor workPool) {
        this.workerService = workerService;
        this.workPool = workPool;
    }

    @Scheduled(scheduler = "timerPool", fixedDelay = 5_000)
    public void tick() {
        long jobId = findCandidateJobId();
        workPool.execute(() -> workerService.process(jobId));
    }
}
```

Đây là **mảnh tích hợp Spring**, cần imports, persistence và `findCandidateJobId` do ứng dụng cung cấp. Ví dụ cho thấy worker gọi `workerService.process` qua **một bean riêng**, để transactional proxy thực sự có cơ hội mở transaction trên worker thread. Nhưng code này **chưa** có distributed claim, durable job state hoặc chống xử lý trùng; `findCandidateJobId` phải được thiết kế chặt chẽ trước khi dùng trong hệ thống nhiều instance.

Không đánh đồng `@EnableScheduling` với `@EnableAsync`: chúng bật hai hạ tầng khác nhau. Với explicit `workPool.execute`, worker dispatch không cần Spring `@Async` proxy.

## 15. Nhiều instance và tác vụ không được chạy trùng

Khi triển khai 3 replicas, mỗi JVM có ApplicationContext và scheduler của riêng nó:

```text
Replica A -- timerPool --> dispatch Job X
Replica B -- timerPool --> dispatch Job X
Replica C -- timerPool --> dispatch Job X
```

Nếu cùng một `@Scheduled(cron=...)` tồn tại trên cả ba replicas, Spring scheduling cơ bản **không tự chọn một leader toàn cluster**. Tách scheduler/worker chỉ cô lập thread **trong từng JVM**, không biến lịch thành distributed job coordinator.

Các biện pháp tùy yêu cầu:

1. **Cho phép chạy nhiều lần và làm idempotent** nếu tác vụ chỉ recompute cache hoặc chỉ đọc dữ liệu.
2. **Distributed lease/lock** để chỉ một instance claim nhiệm vụ, kèm TTL, gia hạn lease và fencing token nếu tài nguyên có thể bị thay đổi bởi instance cũ sau khi hết lease.
3. **Persistent job table** với atomic claim và trạng thái pending/running/completed/failed, version và recovery.
4. **Quartz cluster với persistent JobStore**, hoặc orchestrator/queue phù hợp với SLA.
5. **Phân vùng công việc** theo key để các replicas không xử lý cùng tập dữ liệu.

Distributed lock đơn thuần không tạo durable job result. Nếu một worker chết sau khi làm side effect nhưng trước khi ghi complete, job có thể được claim lại; vẫn cần idempotency. **At-most-one concurrent executor** và **exactly-once business effect** không phải cùng một bảo đảm.

## 16. Quan sát riêng scheduler và worker

Nếu dùng hai pool mà chỉ đo tổng thời gian HTTP hoặc tổng thread count, bạn sẽ không biết điểm nghẽn nằm ở đâu.

### Nhóm metric của scheduler

- `scheduled_tick_total`: số lần scheduler gọi method.
- `planned_fire_time` và `actual_fire_time`: lệch lịch/kích hoạt trễ nếu bạn có thể xác định mốc kế hoạch.
- `dispatch_duration`: thời gian scheduled callback thực hiện admission/submit.
- `dispatch_success_total`, `dispatch_rejected_total`, `dispatch_skipped_total`.
- Scheduler pool size, active threads, exception counts và job registration state.

### Nhóm metric của worker

- `submitted_jobs_total`, `started_jobs_total`, `completed_jobs_total`, `failed_jobs_total`.
- `queue_wait_seconds` từ submit tới worker bắt đầu.
- `execution_seconds` từ worker start tới completion.
- Worker activeCount/poolSize/queueSize, queue capacity/rejection.
- CPU load, GC, DB pool utilization, HTTP client latency và downstream rate limits.

Một bảng chẩn đoán:

| Biểu hiện | Khả năng | Cách xác nhận |
| --- | --- | --- |
| Timer đúng lịch nhưng job hoàn tất muộn | Worker queue hoặc downstream chậm | Queue wait + active worker + task duration |
| Timer trễ rõ, worker không bận | Scheduler callback còn blocking hoặc timer pool bận | Thread dump của timer threads |
| Timer trễ khi worker pool đầy | CallerRunsPolicy hoặc dispatch chặn | Rejection handler, stack `execute` trên timer thread |
| Tick tăng nhưng số started không tăng | Job bị queue lâu hoặc rejection | So sánh submitted/started/rejected |
| Nhiều job trùng lúc deploy nhiều replicas | Schedule chạy trên nhiều ApplicationContexts | Instance ID, distributed lock/claim |
| Queue size tăng liên tục | Offered rate > completion capacity | Dùng Little's Law, đo throughput và latency |
| Dispatch thành công nhưng job mất sau crash | Queue in-memory không durable | Kiểm tra job store/outbox và recovery test |

Đo **hai độ trễ** riêng: scheduler latency (thời điểm đáng lẽ phải tick đến lúc tick thật) và worker queue wait/processing latency. Một con số “job duration” gộp tất cả có thể che nguyên nhân.

## 17. Kế hoạch kiểm chứng tải và sự cố

Một phép thử đủ tin cậy nên có cả correctness lẫn overload:

1. **Baseline:** chỉ bật lịch nhẹ, xác định tick count và độ lệch lịch khi worker pool gần như rảnh.
2. **Giữ worker bận có kiểm soát:** dùng latch/fake I/O để giữ worker; xác nhận timer vẫn dispatch đúng nhịp mà không thực thi nghiệp vụ trên timer thread.
3. **Lấp queue đến capacity:** xác nhận rõ rejection policy. Nếu có CallerRunsPolicy, kiểm tra liệu timer thread có chạy task inline hay không.
4. **Tăng thời gian I/O:** đo queue wait, p95/p99 worker duration, DB/HTTP timeouts và độ trễ lịch; không chỉ số jobs submitted.
5. **Test skip/coalesce/single-flight:** chạy job lâu hơn chu kỳ, xác nhận quy tắc nghiệp vụ về tick bị bỏ và tick được gộp.
6. **Thử exception:** cả exception khi dispatch và khi worker chạy; kiểm tra completion metric, cleanup và state không bị giữ mãi.
7. **Shutdown:** dừng context có kiểm soát và dừng tiến trình đột ngột trong môi trường test; so sánh những job được nhận nhưng chưa hoàn tất và khả năng recovery nếu yêu cầu durable.
8. **Multi-instance:** chạy hai replicas với cùng cron, xem việc trùng có được phép hay có cơ chế claim/idempotency phù hợp.
9. **So sánh cấu hình:** trước/sau khi tách pool, so scheduler delay, throughput hoàn tất và resource pressure với cùng workload.

Không dùng một câu “timer đúng giờ” như bằng chứng hệ thống có đủ công suất. Bằng chứng phải gồm **completion throughput, queue age, error rate và kết quả nghiệp vụ**.

## 18. Những lỗi phổ biến

1. **Cho rằng `@Scheduled` tự chuyển tác vụ sang executor khác.** Với `ThreadPoolTaskScheduler` truyền thống, callback chạy trên scheduler thread trừ khi bạn dispatch rõ.
2. **Đặt business I/O nặng trực tiếp trong lịch dùng chung.** Một call chậm có thể giữ scheduler thread và làm các lịch khác bị trễ.
3. **Chỉ tăng scheduler pool size để tăng throughput nghiệp vụ.** Hai loại tải có bottleneck khác nhau; nhiều timer threads không thay thế worker capacity.
4. **Tưởng `fixedDelay` của method dispatch chờ job worker xong.** Nó chỉ đợi method `@Scheduled` hoàn tất, tức có thể chỉ là submit.
5. **Không chống chồng lấp khi job dài hơn chu kỳ.** Queue có thể chứa nhiều bản của cùng công việc.
6. **Dùng queue vô hạn để né rejection.** Backlog và RAM tăng, công việc cũ có thể hết giá trị.
7. **Dùng CallerRunsPolicy mà không biết nó đưa nghiệp vụ lên scheduler thread.** Cách ly bị phá khi executor bão hòa.
8. **Bắt `TaskRejectedException` rồi im lặng bỏ event quan trọng.** Cần skip/coalesce được phép hoặc lưu job bền vững.
9. **Gọi `Future.get()` trong `@Scheduled` ngay sau submit.** Scheduler lại bị chặn để chờ worker, làm mất lợi ích tách pool.
10. **Tưởng `@EnableScheduling` tự bật `@Async`.** Hai hạ tầng được kích hoạt riêng.
11. **Gọi `this.asyncMethod()` và mong proxy intercept.** Self-invocation thường bỏ qua `@Async` ở proxy mode.
12. **Cho rằng một `@Scheduled` chỉ chạy trên một replica của cluster.** Mỗi JVM có scheduler riêng, không có distributed coordination mặc định.
13. **Chuyển Transaction ThreadLocal sang worker.** Transaction imperative không tự đi theo; cần transactional boundary đúng.
14. **Ghi log “submitted” rồi coi như nghiệp vụ đã hoàn tất.** Phải theo dõi started/completed/failed và side effect.
15. **Cho rằng queue trong `ThreadPoolTaskExecutor` lưu bền qua crash.** Nó thường nằm trong RAM.
16. **Đặt timeout ở caller rồi cho rằng job đã dừng.** Timeout chờ không đồng nghĩa cancellation/cleanup.
17. **Không tách metric của timer và worker.** Dễ nhầm scheduler delay với worker queue wait.
18. **Dùng ví dụ pool 1/queue 1 làm cấu hình production.** Giá trị trong demo chỉ để buộc trạng thái dễ kiểm chứng, không phải sizing thật.

## 19. Tự kiểm tra

**Câu 1.** Một `@Scheduled(fixedDelay = 1000)` chỉ gọi `workPool.execute(...)` rồi trả ngay. Job worker chạy 30 giây. Lịch tiếp theo đợi 30 giây không?  
**Đáp án:** Không tự động. `fixedDelay` đo từ kết thúc **scheduled method**, tức có thể sau submit vài ms. Nếu muốn chờ job trước xong, phải có chính sách no-overlap/điều phối bổ sung.

**Câu 2.** Vì sao `ThreadPoolTaskScheduler` có 1 thread có thể làm cron thứ hai trễ khi cron thứ nhất gọi API 20 giây?  
**Đáp án:** `ThreadPoolTaskScheduler` thực thi callback trên scheduler thread. Thread duy nhất bị chặn bởi API nên chưa thể phục vụ callback khác.

**Câu 3.** Nếu worker pool bị đầy và dùng `CallerRunsPolicy`, chuyện gì xảy ra với timer thread?  
**Đáp án:** Nó có thể chạy job bị reject **ngay trên timer thread**, làm scheduler trễ và phá cách ly.

**Câu 4.** Có 3 replicas, cùng `@Scheduled(cron=...)`. Tách timerPool/workPool bảo đảm chỉ một job chạy toàn cluster không?  
**Đáp án:** Không. Tách pool chỉ quản lý thread trong mỗi instance; cần distributed coordination/claim hoặc idempotency theo nghiệp vụ.

**Câu 5.** Scheduler đúng giờ, worker queue dài và job hoàn tất muộn. Có nên tăng scheduler pool size trước không?  
**Đáp án:** Thường không. Cần đo offered rate, worker completion rate, queue wait, downstream bottlenecks và giới hạn admission.

**Câu 6.** `@EnableScheduling` đã bật, method `@Scheduled` gọi `workPool.execute` rõ ràng. Có cần `@EnableAsync` không?  
**Đáp án:** Không cho thao tác dispatch explicit. `@EnableAsync` dùng cho hạ tầng proxy `@Async`.

**Câu 7.** Chỉ dùng `AtomicBoolean inFlight` có ngăn chồng lấp giữa hai JVM không?  
**Đáp án:** Không. Đây là trạng thái local; cần cơ chế claim/lease trong kho chung nếu bắt buộc chống overlap trên cluster.

**Câu 8.** Làm sao phân biệt một tick đã được nộp thành công với một job đã chạy xong?  
**Đáp án:** Theo dõi riêng submitted/started/completed, queue wait và trạng thái nghiệp vụ; `execute` trả về không phải completion receipt.

**Câu 9.** Khi nào nên giữ đơn giản một scheduler pool, không tách executor?  
**Đáp án:** Khi scheduled methods rất ngắn, không blocking đáng kể, tải nhỏ và yêu cầu lịch không cần cách ly riêng. Nếu cần kiểm soát timer jitter và xử lý nặng độc lập, tách pool có giá trị.

**Câu 10.** Tách scheduler và worker đã tạo durable queue chưa?  
**Đáp án:** Chưa. Queue thread pool thường ở RAM. Recovery sau crash cần job store/broker/Quartz persistent store hoặc cơ chế tương đương.

**Ghi nhớ:** **Scheduler quyết định thời điểm được phép thử gửi việc; executor quyết định khi nào và với bao nhiêu worker việc được chạy.** Khi có hai loại tải cạnh tranh, tách `TaskScheduler` và `TaskExecutor` giúp đo và kiểm soát độc lập. Nhưng vẫn phải đặt chính sách queue, overlap, rejection, transaction, multi-instance và khôi phục phù hợp với nghiệp vụ.

### Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework — @Scheduled JavaDoc (scheduler qualifier, fixedDelay, fixedRate)](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework — @EnableScheduling JavaDoc (scheduler bean resolution)](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html)
- [Spring Framework — ThreadPoolTaskScheduler JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html)
- [Spring Framework — ThreadPoolTaskExecutor JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework — TaskScheduler JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html)
- [Java SE 17 — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scheduler-selection-009">Chọn cách theo dõi hoàn tất theo hợp đồng của từng scheduler</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn cách theo dõi hoàn tất theo hợp đồng của từng scheduler

Khi lập lịch trong Java hoặc Spring Framework, “scheduler đã nhận lịch”, “đến thời điểm kích hoạt”, “callback đã chạy xong” và “công việc nghiệp vụ đã hoàn tất” là **những sự kiện khác nhau**. Một cách theo dõi hoàn tất phù hợp với `schedule(Runnable, Instant)` chưa chắc phù hợp với `scheduleAtFixedRate(...)`, `@Scheduled(cron=...)`, `SimpleAsyncTaskScheduler` hoặc callback chỉ gửi tác vụ sang executor khác.

**Nguyên tắc của bài:** trước khi chọn `Future.get()`, `isDone()`, completion callback, listener hay job-status store, cần xác định **hợp đồng hoàn tất của scheduler cụ thể và ranh giới công việc cần quan sát**. Không thể dùng một biến `isDone()` chung rồi suy ra mọi lần chạy, mọi sự kiện, mọi tác vụ con đã hoàn tất thành công.

Bài học thuộc mục **Chọn scheduler** trong **Spring Framework - concurrency**. Các ví dụ sử dụng Java 17+, Spring Framework 6.1/6.2; phần Java thuần có thể chạy độc lập, còn đoạn Spring cần dependency và ApplicationContext phù hợp.

## 1. Trước hết, phải định nghĩa “hoàn tất” ở lớp nào

Một lần lập lịch có thể đi qua nhiều mốc:

~~~text
Người dùng / service
    |
    | schedule(task, when)       (1) Đã nhận lịch
    v
Scheduler timer / trigger
    |
    | đến hạn                  (2) Đã kích hoạt trigger
    v
Callback bắt đầu
    |
    | execute callback         (3) Callback đang chạy
    |-- executor.execute(job) --->
    |                             Job bắt đầu trên worker khác
    | callback return         (4) Callback đã kết thúc
    |                             |
    |                             | xử lý I/O, DB, retry
    |                             v
    |                          (5) Nghiệp vụ hoàn tất
    v
Lần kích hoạt tiếp theo?
    |
    +-- có: lịch tuần hoàn / cron
    +-- không: lịch một lần đã kết thúc
~~~

Năm kiểu quan sát:

| Câu hỏi của hệ thống | Bằng chứng phù hợp | Điều chưa được chứng minh |
| --- | --- | --- |
| Đăng ký lịch có thành công không? | `schedule(...)` trả handle hợp lệ, không bị reject; thông tin đăng ký được lưu theo hợp đồng | Task đã chạy hoặc đã hoàn tất |
| Lần kích hoạt nào đã bắt đầu? | Timestamp/counter tại đầu callback; trigger/job listener | Nghiệp vụ đã thành công |
| Callback được scheduler quản lý đã xong chưa? | Future của **lần chạy một lần**, hoặc hook sau callback | Công việc khác được callback submit đã xong |
| Một lần xử lý nghiệp vụ thành công hay thất bại? | `CompletableFuture` của job, completion event, kết quả worker, job status | Các lần kích hoạt tiếp theo sẽ thành công |
| Toàn bộ lịch tuần hoàn đã dừng chưa? | Handle đã cancel, lifecycle scheduler/job store, lịch không còn trigger | Các side effect của những lần chạy trước đã được rollback |

Cần bổ sung một cấp nữa nếu job chạy qua nhiều máy: “job đã hoàn tất và **trạng thái được lưu bền vững**” khác “thread hiện tại đã return”. Một Future trong bộ nhớ không thay thế database/job store hoặc cơ chế phát sự kiện khi hệ thống cần truy vấn sau restart.

## 2. Hợp đồng của ScheduledFuture trong Java SE 17

`ScheduledExecutorService` là API Java cho hẹn giờ một lần hoặc chạy lặp. Các method như `schedule(...)`, `scheduleAtFixedRate(...)` và `scheduleWithFixedDelay(...)` trả về `ScheduledFuture<?>`. Nó kế thừa `Future<?>` và `Delayed`.

Các phương thức quan trọng:

- `get()`: chờ **trạng thái hoàn tất của chính Future đó**, không mặc định là lần chạy gần nhất.
- `get(timeout, unit)`: caller chỉ chờ có thời hạn, tránh block vô hạn.
- `isDone()`: Future đã hoàn tất bình thường, thất bại hoặc bị hủy; không đồng nghĩa thành công.
- `isCancelled()`: trạng thái hủy của handle; không chứng minh một worker đang chạy đã dừng tức thì.
- `cancel(mayInterruptIfRunning)`: yêu cầu hủy theo hợp đồng implementation; ngay cả khi true cũng không rollback side effect.
- `getDelay(unit)`: thời gian còn lại trước mốc kích hoạt theo `Delayed`, **không phải execution duration**.

Với `ScheduledFuture<?>` của một `Runnable` chạy một lần, `get()` sẽ trả `null` khi callback kết thúc bình thường. Với `ScheduledFuture<V>` từ `Callable<V>` chạy một lần, `get()` trả giá trị tính toán của callable khi thành công.

**Một handle cùng tên type không đồng nghĩa cùng ngữ nghĩa hoàn tất giữa one-shot và recurring.**

### 2.1. Lịch một lần: get() có thể xác nhận callback đã kết thúc

~~~java
ScheduledFuture<?> handle = scheduler.schedule(
        () -> System.out.println("one-shot callback"),
        1, TimeUnit.SECONDS);

handle.get(5, TimeUnit.SECONDS);
// Khi get trả bình thường: callback Runnable được scheduler quản lý đã kết thúc.
~~~

Đây là **đoạn Java minh họa**, cần một biến `ScheduledExecutorService scheduler`, imports và lifecycle cleanup. Nó không phải ứng dụng hoàn chỉnh. Lưu ý:

1. Lời gọi `schedule(...)` trả về không chứng minh callback đã bắt đầu.
2. `handle.get(5, SECONDS)` trả bình thường khi callback chạy xong (hoặc ném `ExecutionException` nếu callback lỗi; `CancellationException` nếu bị hủy; `TimeoutException` nếu quá thời gian).
3. Nếu callback **chỉ enqueue một tác vụ khác rồi return**, handle này xác nhận **callback gửi việc đã xong**, không phải công việc được gửi đó đã xong.

Không nên gọi `get()` trên chính scheduler thread để chờ một công việc sẽ phải chạy trên cùng scheduler thread một-worker: đó là một công thức gây starvation/deadlock. Thường caller bên ngoài chờ có timeout, hoặc tốt hơn dùng callback / event cho workflow bất đồng bộ.

## 3. Lịch tuần hoàn: ScheduledFuture không hoàn tất sau từng tick

Khi dùng Java `scheduleAtFixedRate(...)` hoặc `scheduleWithFixedDelay(...)`, handle đại diện **chuỗi lần chạy**, không phải một Future mới được trả về sau mỗi lần kích hoạt.

Theo hợp đồng `ScheduledExecutorService`:

- Khi lịch vẫn tiếp tục, `isDone()` thường vẫn `false` **dù một, mười hay nhiều lần chạy đã xong**.
- `get()` của Future lặp **không trả bình thường** vì một lượt tick đã hoàn tất. Nó chờ tới khi lịch kết thúc bất thường hoặc bị hủy; có thể chờ mãi nếu lịch hoạt động bình thường.
- Nếu một lượt của tác vụ lặp trong **JDK ScheduledThreadPoolExecutor thuần** ném exception ra khỏi callback, những lần kế tiếp bị ngừng theo hợp đồng của JDK; Future hoàn tất thất bại và `get()` ném `ExecutionException`.
- Nếu hủy handle, `get()` có thể ném `CancellationException`; đó không phải bằng chứng rằng mọi công việc con đã được scheduler gửi trước đó đều dừng.

**Vì vậy không viết:**

~~~java
ScheduledFuture<?> recurring =
        scheduler.scheduleAtFixedRate(job, 0, 5, TimeUnit.SECONDS);
recurring.get(); // Không phải "chờ lần chạy đầu tiên xong".
~~~

Đoạn trên là **anti-pattern minh họa**, `scheduler` và `job` phải được định nghĩa ở nơi khác. Để biết **mỗi lần chạy**, hãy ghi nhận một invocation id, start/end/error ở trong `Runnable` hoặc dùng listener/metric theo từng lần, không dùng `recurring.isDone()` cho thành công của mỗi tick.

### 3.1. Fixed-rate và fixed-delay không có cùng mốc tham chiếu

| Chế độ | Mốc lập lịch lần sau | Điều cần quan sát |
| --- | --- | --- |
| `scheduleAtFixedRate` | Dựa trên chu kỳ từ mốc dự kiến các lần bắt đầu | Scheduled time, start delay/misfire, run duration; một lần chạy quá lâu có thể khiến lần sau bị trễ |
| `scheduleWithFixedDelay` | Sau khi callback trước kết thúc, chờ thêm delay rồi mới tới lần sau | Callback completion, delay thực tế, thời gian nghẽn worker |
| Cron/Trigger | Theo `Trigger.nextExecution(TriggerContext)` và lịch/zone | Scheduled time, actual start, last completion, số lần bị bỏ/lỡ tùy chính sách |
| Callback gửi việc sang executor khác | Callback có thể kết thúc rất nhanh | Hai mốc: scheduler callback completion và child business completion |

Với JDK `ScheduledThreadPoolExecutor`, các lượt liên tiếp của **cùng một periodic task** không chạy đồng thời; chúng có thể trễ nếu callback lâu. Nhưng một method Spring có nhiều annotation `@Scheduled` hoặc ứng dụng đăng ký nhiều task độc lập vẫn có thể chạy chồng nhau. Không suy ra một chính sách chống overlap toàn bộ hệ thống chỉ từ từ “fixed-rate”.

### 3.2. Không nhầm lỗi mặc định Spring với lỗi JDK thuần

Một điểm cần kiểm tra kỹ: Spring thường **bọc scheduled task để xử lý exception**. Ở các cơ chế mặc định dựa trên `TaskUtils`, lỗi từ **repeating task** có thể được ghi log rồi **suppressed**, để lịch tiếp tục; với **one-shot task** lỗi thường được log và propagated để Future thấy thất bại. Ứng dụng có thể thay đổi hành vi bằng `ErrorHandler`.

Điều này khác quy tắc của `ScheduledExecutorService.scheduleAtFixedRate` Java thuần, nơi một exception không được catch sẽ ngừng các lần lặp tiếp theo. Vì vậy:

- Không khẳng định `scheduledFuture.get()` nhất định ném `ExecutionException` khi một callback `@Scheduled` Spring lặp bị lỗi.
- Nếu handler Spring đã log và suppress lỗi, handle recurring vẫn chưa hoàn tất, những lượt sau tiếp tục.
- Nếu cần phát hiện lỗi **từng lần**, dùng counters, tracing và error handler hoặc bắt/ghi lỗi trong callback.
- Nếu yêu cầu **lỗi thì ngừng cả lịch**, phải cấu hình/thiết kế rõ chính sách dừng, thay vì dựa vào hiệu ứng ngẫu nhiên của exception.

Chính sách xử lý lỗi là **một phần hợp đồng scheduler**, không phải chi tiết có thể bỏ qua khi chọn cách theo dõi hoàn tất.

## 4. Các TaskScheduler của Spring không giống nhau về ranh giới execution

Spring `TaskScheduler` là abstraction để lập lịch. Nó có các method như:

~~~java
ScheduledFuture<?> oneShot = scheduler.schedule(task, instant);
ScheduledFuture<?> periodic = scheduler.scheduleAtFixedRate(task, period);
ScheduledFuture<?> delayed = scheduler.scheduleWithFixedDelay(task, delay);
ScheduledFuture<?> cron = scheduler.schedule(task, trigger);
~~~

Đoạn này minh họa API Spring; cần `TaskScheduler scheduler`, `Runnable task`, `Instant instant`, `Duration period/delay`, `Trigger trigger` và imports. Với `schedule(task, trigger)`, có trường hợp API trả **null** nếu trigger không có lần kích hoạt tiếp theo; phải kiểm tra điều đó trước khi lưu handle.

### 4.1. ThreadPoolTaskScheduler

Đây là scheduler Spring thông dụng, nội bộ bọc `ScheduledThreadPoolExecutor`. Theo Javadoc, `ThreadPoolTaskScheduler` thực thi callback **trên scheduler thread(s)**, không tự tách callback sang một nhóm worker khác. Vì vậy, `ScheduledFuture` của một lần chạy có thể đại diện cho **hoàn tất thực tế của callback được cung cấp**; với lịch lặp là trạng thái chuỗi lặp.

Ưu điểm: dễ lý giải khi task **thực hiện toàn bộ việc bên trong callback**, thay vì chỉ submit sang executor khác. Giới hạn: callback blocking lâu có thể chiếm scheduler thread, gây trễ cho các lịch khác; đặt pool size theo nhu cầu concurrency và tài nguyên thực tế, không chỉ theo số cron expression.

### 4.2. ConcurrentTaskScheduler / DefaultManagedTaskScheduler

`ConcurrentTaskScheduler` là adapter tới một `ScheduledExecutorService` bên dưới; `DefaultManagedTaskScheduler` tích hợp scheduler do container Jakarta EE quản lý. Abstraction không xóa hợp đồng thực thi của delegate:

- Kiểm tra kiểu delegate, lỗi/rejection, cancellation và shutdown của môi trường.
- Với handle một lần, theo dõi callback mà delegate quản lý.
- Với recurring, vẫn cần metric per invocation và chính sách error handler.
- Trong môi trường managed, lifecycle/ownership do container có thể khác với pool bạn tự tạo; không dùng `shutdownNow()` trên tài nguyên không do ứng dụng sở hữu.

Không suy đoán chi tiết thread hay độ bền chỉ vì class có tên `TaskScheduler`.

### 4.3. SimpleAsyncTaskScheduler (Spring 6.1+)

`SimpleAsyncTaskScheduler` có mô hình một scheduler thread điều phối thời điểm kích hoạt và thường **khởi chạy một thread mới cho mỗi scheduled execution**, phù hợp các lựa chọn virtual threads ở JDK 21+ khi cấu hình tương ứng. Ngoại lệ đáng nhớ: **fixed-delay executions chạy trên một scheduler thread**; với kiểu này, Spring khuyến nghị fixed-rate hoặc cron khi tận dụng virtual threads.

Vì thread chạy callback có thể **tách khỏi thread điều phối**, đừng suy diễn rằng thời điểm một trigger/handle ở scheduler kết thúc sẽ luôn đồng nghĩa nghiệp vụ trên execution thread đã xong trong mọi cách dùng và cấu hình. Hãy instrument **đầu và cuối callback thực tế** hoặc theo dõi Future/job-status mà công việc thật cung cấp.

**Java 17 không có virtual threads ổn định**; phần ví dụ độc lập ở dưới dùng thread pool truyền thống. Nếu cấu hình virtual threads, yêu cầu JDK hỗ trợ (ví dụ 21+) và kiểm tra đặc tính scheduler thực tế.

### 4.4. Quartz hoặc scheduler có job store

Nếu dùng Quartz, hợp đồng cần quan sát là scheduler/job/trigger theo Quartz, không phải mặc nhiên `ScheduledFuture` của Java. Có thể dùng job listener (chẳng hạn callback khi Job execution hoàn tất), trigger listener, execution id và job data/status do ứng dụng lưu. Một job đã được trigger không bảo đảm **toàn bộ workflow nghiệp vụ downstream** đã thành công.

Nếu có job store bền vững, nó giúp duy trì lịch/trigger phù hợp cấu hình; **không tự bảo đảm** một HTTP call, DB commit hay tác vụ con đã hoàn tất đúng một lần. Cần quan sát kết quả nghiệp vụ riêng và chính sách retry/idempotency.

### 4.5. Bảng chọn tín hiệu hoàn tất theo scheduler

| Scheduler/hợp đồng | Tín hiệu dùng được | Tín hiệu không nên dùng sai |
| --- | --- | --- |
| Java one-shot `ScheduledExecutorService.schedule` | `ScheduledFuture.get(timeout)` / `isDone`; callback result/exception | “Đã schedule” = “đã xong” |
| Java periodic fixed-rate/delay | Timestamp, counter, latch/event **cho từng tick**; Future để quản lý vòng đời lịch | `Future.get()` để chờ một tick thành công |
| Spring `ThreadPoolTaskScheduler` one-shot callback thực hiện toàn bộ việc | `ScheduledFuture<?>` cho completion của callback | Future của callback khi nó chỉ gửi job con |
| Spring `ThreadPoolTaskScheduler` repeating/cron | Metric/error handler/per-invocation status + handle cancel/lifecycle | `isDone()` cho từng lần chạy |
| Spring `SimpleAsyncTaskScheduler` | Tín hiệu ngay trong execution callback, Future nghiệp vụ hoặc job store | Chỉ nhìn thread điều phối đã gửi callback |
| Spring `@Scheduled` annotated | Instrumentation trong method, hook/error handler, job-status | Trông đợi method return trả ScheduledFuture cho caller |
| Quartz/job-store workflow | Job/trigger listener + bền vững hóa trạng thái, correlation id | Đăng ký trigger = nghiệp vụ đã hoàn tất |

## 5. @Scheduled không trả một Future cho caller theo cách gọi method

Spring `@Scheduled` thường đánh dấu method `void` không tham số:

~~~java
package example.scheduling;

import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CleanupSchedule {
    @Scheduled(
        fixedDelay = 5,
        timeUnit = TimeUnit.SECONDS,
        scheduler = "reportScheduler")
    public void cleanup() {
        // Đo start/end/error ngay trong công việc.
        System.out.println("Cleanup invocation finished");
    }
}
~~~

Đây là **mã Spring tích hợp**, cần `@EnableScheduling` và bean scheduler đúng tên. Thuộc tính `scheduler="reportScheduler"` hỗ trợ từ Spring Framework 6.1. Một method annotated thường không có caller giữ `ScheduledFuture` cho từng invocation. Method return thông thường bị scheduler bỏ qua; không biến giá trị trả về thành một công cụ theo dõi kết quả dành cho caller.

Đo thời điểm chạy thật bằng try/finally hoặc instrumentation:

~~~java
long start = System.nanoTime();
try {
    cleanupBusiness();
    successCounter.increment();
} catch (Exception ex) {
    failureCounter.increment();
    throw ex;
} finally {
    long elapsed = System.nanoTime() - start;
    durationRecorder.record(elapsed, TimeUnit.NANOSECONDS);
}
~~~

Đây là **pseudocode minh họa**: `cleanupBusiness`, các counter và recorder phải do ứng dụng cung cấp. Nếu thay vì ném lại lỗi ta bắt rồi bỏ, chính sách dừng/tiếp tục của scheduler cũng có thể bị thay đổi.

Trong Spring 6.1+, reactive `@Scheduled` có hợp đồng đặc biệt: Spring định kỳ subscribe tới một `Publisher` hỗ trợ deferred subscription và bỏ qua các giá trị onNext. **Không mặc nhiên dùng `CompletableFuture` trả về từ annotated method như một Publisher deferred hợp lệ**, và không suy từ một lần method được invoke sang việc toàn bộ stream đã hoàn tất. Muốn theo dõi kết quả reactive, quan sát tín hiệu terminal của subscription và hành vi lỗi theo tài liệu Spring đúng phiên bản.

### 5.1. @Scheduled + @Async: lịch có thể hoàn thành trước task nghiệp vụ

Một method có thể vừa là callback của scheduler vừa gửi công việc cho một bean async:

~~~java
@Scheduled(fixedRate = 10000)
public void tick() {
    reportWorker.generateAsync();
}
~~~

Đây là **đoạn method minh họa**, giả định `reportWorker` là bean được inject và `generateAsync()` gửi việc sang một executor khác. Khi `tick()` return, công việc thực tế trên `reportWorker` có thể vẫn đang queue hoặc đang chạy. Với fixed-delay, khoảng delay tính từ **kết thúc lời gọi scheduled callback**, không tự động tính từ thời điểm job async được gửi đi và hoàn tất.

Nếu nghiệp vụ đòi hỏi “mỗi report xong rồi mới lên lịch report tiếp”, cần hợp đồng completion rõ: đồng bộ chờ một Future một cách an toàn, dùng state machine/job store, hoặc tự schedule lần kế tiếp sau tín hiệu hoàn tất. Không dùng method return của `@Async` mà không quan sát lỗi và thời điểm finish.

## 6. Java 17: chương trình chứng minh bốn hợp đồng hoàn tất

Chương trình độc lập sau sử dụng `ScheduledExecutorService` và `ExecutorService` chuẩn Java. Các `CountDownLatch` tạo thứ tự xác định, tránh đoán thời gian bằng `Thread.sleep(...)`.

Bốn tình huống:

1. **One-shot:** callback đang chặn nên `isDone()==false`; sau khi thả latch, `get()` trả bình thường và `isDone()==true`.
2. **Recurring:** một tick đã chạy nhưng `get(timeout)` vẫn timeout; chỉ hủy lịch mới chuyển handle sang trạng thái kết thúc.
3. **Periodic failure:** JDK thuần gặp exception ở tick thứ hai, Future hoàn tất thất bại và suppress các tick tiếp theo.
4. **Delegation:** scheduler callback chỉ submit task cho một executor khác; handle scheduler đã hoàn tất dù child task vẫn bị chặn.

Tạo tệp **`SchedulerCompletionDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class SchedulerCompletionDemo {
    public static void main(String[] args) throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ExecutorService childPool = Executors.newSingleThreadExecutor();
        CountDownLatch releaseOneShot = new CountDownLatch(1);
        CountDownLatch releaseChild = new CountDownLatch(1);

        try {
            CountDownLatch oneStarted = new CountDownLatch(1);
            ScheduledFuture<?> oneShot = scheduler.schedule(() -> {
                oneStarted.countDown();
                try {
                    releaseOneShot.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, 0, TimeUnit.MILLISECONDS);
            if (!oneStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("one-shot did not start");
            }
            System.out.println("1. One-shot done while blocked: " + oneShot.isDone());
            releaseOneShot.countDown();
            oneShot.get(5, TimeUnit.SECONDS);
            System.out.println("2. One-shot done after get: " + oneShot.isDone());

            CountDownLatch firstTick = new CountDownLatch(1);
            ScheduledFuture<?> periodic = scheduler.scheduleAtFixedRate(
                    firstTick::countDown, 0, 1, TimeUnit.MINUTES);
            if (!firstTick.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("periodic task did not tick");
            }
            try {
                periodic.get(100, TimeUnit.MILLISECONDS);
                throw new IllegalStateException("periodic get returned unexpectedly");
            } catch (TimeoutException expected) {
                System.out.println("3. Periodic get after tick: TimeoutException");
            }
            System.out.println("4. Periodic cancel: " + periodic.cancel(false));

            AtomicInteger runs = new AtomicInteger();
            ScheduledFuture<?> failed = scheduler.scheduleAtFixedRate(() -> {
                if (runs.incrementAndGet() == 2) {
                    throw new IllegalStateException("second tick failed");
                }
            }, 0, 1, TimeUnit.MILLISECONDS);
            try {
                failed.get(5, TimeUnit.SECONDS);
                throw new IllegalStateException("failed periodic get returned");
            } catch (ExecutionException expected) {
                System.out.println("5. Periodic failure: "
                        + expected.getCause().getClass().getSimpleName()
                        + ", runs=" + runs.get());
            }

            CountDownLatch childStarted = new CountDownLatch(1);
            CountDownLatch childFinished = new CountDownLatch(1);
            ScheduledFuture<?> dispatch = scheduler.schedule(() ->
                    childPool.execute(() -> {
                        childStarted.countDown();
                        try {
                            releaseChild.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            childFinished.countDown();
                        }
                    }), 0, TimeUnit.MILLISECONDS);
            dispatch.get(5, TimeUnit.SECONDS);
            if (!childStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("child did not start");
            }
            System.out.println("6. Dispatch done: " + dispatch.isDone());
            System.out.println("7. Child finished at dispatch completion: "
                    + (childFinished.getCount() == 0));
            releaseChild.countDown();
            if (!childFinished.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("child did not finish");
            }
            System.out.println("8. Child finished after release: "
                    + (childFinished.getCount() == 0));
        } finally {
            releaseOneShot.countDown();
            releaseChild.countDown();
            scheduler.shutdownNow();
            childPool.shutdownNow();
        }
    }
}
~~~

Lệnh chạy bằng JDK 17+:

~~~bash
javac --release 17 SchedulerCompletionDemo.java
java SchedulerCompletionDemo
~~~

**Stdout chính xác đã được kiểm chứng khi biên dịch với `--release 17`:**

~~~text
1. One-shot done while blocked: false
2. One-shot done after get: true
3. Periodic get after tick: TimeoutException
4. Periodic cancel: true
5. Periodic failure: IllegalStateException, runs=2
6. Dispatch done: true
7. Child finished at dispatch completion: false
8. Child finished after release: true
~~~

Giải thích tại sao không phụ thuộc vào timing ngẫu nhiên:

- `oneStarted.await(...)` xác nhận callback one-shot đã bắt đầu; nó còn giữ `releaseOneShot` nên `isDone` chưa thể true.
- `oneShot.get(...)` chỉ trả sau callback đã thoát.
- `firstTick.await(...)` xác nhận lịch lặp đã chạy ít nhất một lượt, nhưng `periodic.get(100,...)` vẫn timeout vì chuỗi lặp **chưa kết thúc**. Chu kỳ 1 phút cho phép kiểm tra lượt đầu không cần xếp đè các tick khác.
- Lượt thứ hai của task cố tình lỗi sau khi `runs.incrementAndGet()==2`; JDK thuần ghi exception vào Future và không tiếp tục lượt thứ ba.
- `dispatch.get(...)` chỉ chờ callback submit sang `childPool`. `childStarted` xác nhận child đã bắt đầu nhưng `releaseChild` chưa mở nên child chưa thể hoàn tất.
- Sau khi main mở `releaseChild` và đợi `childFinished`, nó mới có bằng chứng worker con kết thúc.

Ví dụ cố ý minh họa **JDK ScheduledExecutorService thuần**, không dùng lớp bọc lỗi của Spring. Không dùng kết quả “periodic failure dừng toàn bộ lịch” để khẳng định mặc định của mọi `@Scheduled` Spring.

## 7. Spring: chọn bean TaskScheduler và quan sát callback thật

Một cấu hình Spring Framework 6.1+:

Tệp `ScheduleConfiguration.java`:

~~~java
package example.scheduling;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class ScheduleConfiguration {

    @Bean("reportScheduler")
    public ThreadPoolTaskScheduler reportScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("report-schedule-");
        return scheduler;
    }

    @Bean("businessExecutor")
    public ThreadPoolTaskExecutor businessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("report-work-");
        return executor;
    }
}
~~~

Đây là **mã Spring tích hợp**, cần Spring Context, ApplicationContext và được Spring quản lý lifecycle; không chạy chỉ bằng `javac` thuần. `@EnableScheduling` kích hoạt xử lý `@Scheduled`; chương trình đăng ký trực tiếp với `TaskScheduler` cũng có thể hoạt động khi bean được tạo/khởi tạo phù hợp, không bắt buộc phải dùng annotation.

Hai bean có trách nhiệm khác nhau:

- `reportScheduler` quản lý **khi nào** callback được kích hoạt.
- `businessExecutor` quản lý **thread nào** thực thi công việc mà callback gửi tới.

Không giả định số worker của scheduler là số worker tối đa cho business. Mỗi pool có capacity riêng; callback scheduler dài có thể chặn các lịch khác, còn queue của business executor có thể gây trễ giữa lúc kích hoạt và lúc job thực sự bắt đầu.

### 7.1. Nếu callback làm toàn bộ công việc trên scheduler thread

~~~java
ScheduledFuture<?> handle = reportScheduler.schedule(
        () -> {
            performShortLocalMaintenance();
        },
        Instant.now().plusSeconds(2));
~~~

Đây là snippet trong một class có biến `reportScheduler`, method `performShortLocalMaintenance()` và imports. Với `ThreadPoolTaskScheduler`, `handle.get(timeout)` có thể được dùng để xác nhận **callback bảo trì này** đã kết thúc. Tránh chạy callback quá lâu nếu scheduler còn cần kích hoạt nhiều lịch khác.

### 7.2. Nếu callback chỉ chuyển sang executor khác

Trong workflow phức tạp, cần có **hai handle**: một cho mốc scheduler dispatch, một cho hoàn tất nghiệp vụ. Tệp `ScheduledJobFacade.java`:

~~~java
package example.scheduling;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class ScheduledJobFacade {
    private final TaskScheduler scheduler;
    private final TaskExecutor businessExecutor;

    public ScheduledJobFacade(
            @Qualifier("reportScheduler") TaskScheduler scheduler,
            @Qualifier("businessExecutor") TaskExecutor businessExecutor) {
        this.scheduler = scheduler;
        this.businessExecutor = businessExecutor;
    }

    public record Handles(
            ScheduledFuture<?> dispatch,
            CompletableFuture<String> businessCompletion) {}

    public Handles scheduleOnce(String jobId, Instant when) {
        CompletableFuture<String> business = new CompletableFuture<>();
        ScheduledFuture<?> dispatch = scheduler.schedule(() -> {
            try {
                businessExecutor.execute(() -> {
                    try {
                        // Phần nghiệp vụ minh họa: trong sản phẩm thật có I/O.
                        business.complete("completed:" + jobId);
                    } catch (Exception ex) {
                        business.completeExceptionally(ex);
                    }
                });
            } catch (RuntimeException rejection) {
                business.completeExceptionally(rejection);
            }
        }, when);

        return new Handles(dispatch, business);
    }
}
~~~

Đây là **mã tích hợp Spring**, cần hai bean đúng tên đã cấu hình. `@Qualifier` bảo đảm injection dùng đúng scheduler/executor, tránh chọn nhầm default. Có những hạn chế được nêu rõ:

- `dispatch` là handle của callback scheduler: khi callback submit thành công vào `businessExecutor` rồi return, `dispatch.get()` có thể hoàn tất **trước khi** công việc trong worker hoàn thành.
- `businessCompletion` là `CompletableFuture<String>` do ví dụ chủ động hoàn thành trong worker, nên caller có thể quan sát kết quả business bằng callback hoặc `get(timeout)`.
- Nếu `businessExecutor.execute(...)` ném lỗi rejection, callback ghi lỗi này vào `businessCompletion`; không nhầm nó với thất bại của task được worker chấp nhận rồi chạy.
- Nếu `scheduler.schedule(...)` ném lỗi **trước khi trả về handle** thì method `scheduleOnce` cũng ném lỗi cho caller; cần xử lý ở ranh giới API.
- Nếu `dispatch` bị cancel **trước lúc kích hoạt**, Future business của ví dụ này có thể vẫn pending vì callback chưa được gọi để hoàn tất nó. Production cần một state machine/hủy cả job một cách có chủ đích, chứ không lưu hai Future rồi để trạng thái treo.
- Ví dụ không có job store và không có độ bền qua restart. `CompletableFuture.cancel(...)` không tự đảm bảo ngắt worker; nếu hỗ trợ cancel nghiệp vụ, dùng token/job status riêng.
- `business.complete(...)` ở đây minh họa kết quả đơn giản, không phải một lần ghi DB hay API thực tế. Với side effect, worker chỉ hoàn thành Future sau khi nghiệp vụ đạt điều kiện success thật sự.

**Chọn nơi hoàn thành Future theo hợp đồng.** Nếu worker trả ngay sau khi nó gửi một event ra broker, `businessCompletion` chỉ là xác nhận **đã gửi event**; nó chưa phải xác nhận consumer đã xử lý xong. Khi cần biết kết quả cuối ở downstream, dùng acknowledgment/event/status từ downstream.

### 7.3. Quan sát ở caller bằng hai tín hiệu

Trong bean khác, với `ScheduledJobFacade facade`:

~~~java
ScheduledJobFacade.Handles handles =
        facade.scheduleOnce("R-101", Instant.now().plusSeconds(2));

// dispatch chỉ cho biết callback scheduler hoàn tất.
handles.dispatch().get(10, TimeUnit.SECONDS);

// Tín hiệu hoàn tất thực sự của công việc được giao executor khác:
handles.businessCompletion().whenComplete((value, error) -> {
    if (error != null) {
        System.err.println("Job failed: " + error);
    } else {
        System.out.println("Job success: " + value);
    }
});
~~~

Đây là **caller snippet**, không độc lập: cần bean `facade`, imports và xử lý checked exceptions theo chương trình. `whenComplete` là cách quan sát không chặn; nếu caller cần đợi, dùng `get(timeout)` bên ngoài worker/scheduler thích hợp. Nếu client chỉ nhận HTTP 202, phải nói rõ “đã đăng ký lịch”, “đã gửi task” hay “đã hoàn thành”; không dùng một status để bao trùm cả ba.

## 8. TriggerContext và cron: lastCompletion không phải job business completion

Spring `TriggerContext` cung cấp:

~~~java
Instant lastScheduledExecution();
Instant lastActualExecution();
Instant lastCompletion();
~~~

Đây là **chữ ký minh họa các getter**, không phải phương thức có thể đặt trần vào một Java class rồi tự chạy. Chúng mô tả lịch sử các callback của scheduler:

- `lastScheduledExecution`: lần đã được scheduler chọn thời điểm thực thi.
- `lastActualExecution`: mốc callback thực tế được chạy.
- `lastCompletion`: mốc callback của lượt trước kết thúc.

Nếu callback gọi `@Async` hoặc `TaskExecutor.execute(...)` rồi return, `lastCompletion` có thể được ghi **sớm hơn** thời điểm nghiệp vụ trên executor khác hoàn tất. Không dùng field này làm bằng chứng “đã xử lý xong báo cáo” khi callback chỉ dispatch.

Ví dụ cron trong Spring:

~~~java
ScheduledFuture<?> handle =
        reportScheduler.schedule(
                () -> updateSnapshot(),
                new CronTrigger("0 */5 * * * *"));
~~~

Đây là **snippet tích hợp Spring**, giả định `reportScheduler`, `updateSnapshot()` và imports, biểu thức sáu trường chạy tại giây 0 của mỗi 5 phút theo zone/clock của lịch. Với Trigger-based schedule, phải kiểm tra `handle != null` vì Trigger có thể không trả lần kích hoạt tiếp theo.

Cron chỉ mô tả **khi nào có cơ hội chạy**. Scheduler có thể bị trễ, ứng dụng shutdown/restart, callback có thể lỗi hoặc bị bỏ qua theo trigger policy. Một Future recurring không tự cung cấp lịch sử kết quả từng lần. Cần log/metric per-fire và nếu nghiệp vụ yêu cầu, lưu job id/status per invocation.

## 9. Chọn cách ghi nhận kết quả từng lần chạy

Nếu hệ thống hỏi “lần chạy lúc 10:00 có thành công không?”, thông tin phải được gắn với **một invocation**, không chỉ một cron expression.

Một schema trạng thái nghiệp vụ có thể có:

| Trường | Ý nghĩa |
| --- | --- |
| `schedule_id` | Định danh một lịch (ví dụ cleanup-every-5-minutes) |
| `run_id` | Định danh duy nhất cho một lượt thực thi |
| `scheduled_at` | Mốc dự kiến kích hoạt |
| `started_at` | Mốc worker bắt đầu phần cần đo |
| `finished_at` | Mốc thật sự hoàn tất |
| `status` | PLANNED, RUNNING, SUCCEEDED, FAILED, CANCELLED, MISSED theo hợp đồng |
| `error_type` | Phân loại lỗi, không log payload nhạy cảm |
| `attempt` | Số lần thử của lượt chạy đó |
| `parent_run_id` | Quan hệ nếu callback tạo thêm job con |

Với hệ thống in-memory chỉ cần giám sát, counters/histograms có thể đủ. Với lịch chạy báo cáo cuối ngày, tài chính hoặc batch bắt buộc, thường cần **job-status store bền vững** để người dùng tra cứu sau restart hoặc khi chạy nhiều instance.

Các tên trạng thái trong bảng là **gợi ý thiết kế ứng dụng**, không phải enum tự có trong `TaskScheduler`.

### 9.1. Không đồng nhất success của callback với success của workflow

Nếu scheduled callback làm ba bước:

~~~text
Cron callback
   |
   +-- submit job A -> returns Future A
   +-- submit job B -> returns Future B
   +-- return callback (scheduler Future hoàn tất)
                         |
                         +-- Job A vẫn chạy
                         +-- Job B vẫn chạy
~~~

Để đánh dấu workflow hoàn tất, cần hợp đồng phối hợp các kết quả A/B (ví dụ `CompletableFuture.allOf(...)` rồi kiểm tra lỗi) hoặc một trạng thái workflow. `allOf(...)` chỉ cho biết các Future cung cấp đã hoàn tất theo hợp đồng riêng của chúng; nếu Future A chỉ xác nhận “đã gửi message”, nó không biến thành xác nhận consumer đã hoàn tất.

Nếu không muốn các lượt cron chồng nhau khi job dài, cần khóa theo job key, trạng thái RUNNING, semaphore, scheduler policy hoặc một pipeline điều phối. Việc phương thức annotated có `fixedDelay` chưa chắc ngăn hai **child async** của hai lượt khác nhau chồng nhau.

## 10. Lỗi, rejection và hủy phải đo đúng ranh giới

### 10.1. Không đăng ký lịch được

Nếu scheduler đã shutdown hoặc không còn chấp nhận task, `schedule(...)` có thể ném `RejectedExecutionException` (tầng Java) hoặc exception tương ứng của Spring. Đây là **registration/submission failure**: chưa có lần chạy thật. Log riêng `schedule_rejected_total` hoặc trả lỗi phù hợp; không ghi “business failed” cho task chưa bắt đầu.

### 10.2. Callback của một lượt bị lỗi

Với one-shot `ScheduledFuture`, `get()` có thể ném `ExecutionException`. Với Spring recurring, `ErrorHandler` có thể log/suppress lỗi để lịch tiếp tục, nên failure counter **phải nằm trong đường thực thi** chứ không chỉ nhìn `Future.isDone()`.

Nếu callback đã được gửi qua `@Async` trả `void`, exception trên worker có thể tới `AsyncUncaughtExceptionHandler`, không tự xuất hiện trong scheduler Future. Nếu child trả `CompletableFuture<T>`, dùng callback hoặc trạng thái Future đó để quan sát.

### 10.3. Cancel không phải hoàn thành thành công

`ScheduledFuture.cancel(...)` yêu cầu hủy lịch/handle. Với periodic, nó thường giúp ngăn các lượt tương lai; tác vụ đang chạy có thể cần hợp tác với interruption. Nó **không rollback** kết quả những lượt trước, không hủy hết các job con đã dispatch và không chứng minh worker phụ đã dừng.

Có thể tách:

- `SCHEDULE_CANCEL_REQUESTED`: đã có yêu cầu ngừng lịch.
- `SCHEDULE_STOPPED`: không còn lần kích hoạt mới theo hợp đồng.
- `JOB_CANCEL_REQUESTED`: đã gửi tín hiệu hủy công việc con.
- `JOB_STOPPED`: worker/job store đã xác nhận công việc không còn tiến triển.
- `JOB_SUCCEEDED/FAILED`: kết quả nghiệp vụ của một run cụ thể.

Không gán `SCHEDULE_STOPPED` = `ALL_CHILD_JOBS_COMPLETED` nếu chưa kiểm chứng.

### 10.4. Timeout không phải cancel

`scheduledFuture.get(1, TimeUnit.SECONDS)` ném `TimeoutException` chỉ cho biết **caller đã chờ quá thời hạn**, không tự ngừng scheduler hoặc callback. Tương tự, `CompletableFuture.orTimeout(...)` thay đổi trạng thái Future theo hợp đồng CompletableFuture, không bảo đảm ngắt worker/HTTP call.

Đặt timeout I/O, deadline end-to-end và cooperative cancellation riêng nếu cần ngừng side effect. Những thao tác như export report có thể đã ghi kết quả một phần trước khi caller hết giờ chờ.

## 11. Shutdown, restart và nhiều instance

Một lịch trong `ThreadPoolTaskScheduler` local tồn tại trong bộ nhớ tiến trình. Khi ApplicationContext shutdown, Spring quản lý lifecycle theo cấu hình shutdown của scheduler; không có bảo đảm “mọi lần chạy dự kiến sẽ được bù lại sau restart”.

Với một instance:

- Có thể theo dõi callback bằng metrics và Future in-memory.
- Kiểm tra `scheduler.getPoolSize()`, active threads, task duration, delayed starts và lỗi theo khả năng API/monitoring.
- Đừng dựa vào Future cũ sau restart: object đó không còn trong JVM mới.

Với nhiều replicas:

- Mỗi instance có thể đăng ký cùng một `@Scheduled`, dẫn đến nhiều lượt chạy nếu không có điều phối. Không mặc định Spring `@Scheduled` local là cluster singleton.
- Nếu yêu cầu một lần chạy duy nhất trên cụm, cần cơ chế distributed lock/leader election hoặc scheduler/job store có khả năng điều phối.
- Với scheduler bền vững, phải định nghĩa misfire, retry, deduplication và trạng thái lịch sau crash.
- Nếu job tạo side effect quan trọng, dùng idempotency key và kiểm tra kết quả thật của downstream, không chỉ trạng thái trigger đã fired.

### 11.1. Tần suất lịch không đồng nghĩa throughput công việc

Một cron mỗi phút có thể submit mười task mỗi lần, trong khi executor chỉ xử lý năm task mỗi phút. Scheduler vẫn “đã kích hoạt đúng giờ” nhưng queue business tăng vô hạn. Cần đo cả `trigger_delay`, `job_queue_wait`, `job_execution_duration`, `pending_jobs` và `job_failure_total`.

Không xử lý bằng cách tăng threads tùy tiện nếu bottleneck là database pool, external API quota, lock hoặc disk throughput.

## 12. Quy trình chọn cách theo dõi hoàn tất

Trước khi viết code, trả lời theo thứ tự:

1. **Scheduler nào?** Java ScheduledExecutorService, Spring ThreadPoolTaskScheduler, SimpleAsyncTaskScheduler, @Scheduled annotation, Quartz hay managed scheduler? Ghi rõ implementation/phiên bản.
2. **Lịch một lần hay lặp?** Với one-shot, Future.get(timeout) có thể theo dõi callback; với recurring, Future thường quản lý **vòng đời lịch**, không mỗi tick.
3. **Callback trực tiếp làm việc hay chỉ dispatch?** Nếu dispatch, tách handle scheduler và handle business.
4. **Định nghĩa success ở đâu?** Method return, DB commit, file upload hoàn tất, broker accepted hay consumer acknowledged? Completion phải lấy đúng ranh giới đó.
5. **Có cần xem từng lượt chạy?** Nếu có, phát run_id, timestamp và status cho từng invocation; dùng listener/metrics/job store.
6. **Khi callback lỗi, lịch tiếp tục không?** Xác định policy Java/Spring ErrorHandler; không chỉ trông chờ `isDone()`.
7. **Khi bị cancel/shutdown thì sao?** Phân biệt ngừng trigger mới với ngừng child đang chạy và độ bền sau restart.
8. **Cần chịu lỗi và chạy nhiều node không?** Chọn scheduler/job store bền vững, idempotency, retry và misfire policy phù hợp.

### 12.1. Ma trận quyết định ngắn

| Nhu cầu | Cách làm thường phù hợp |
| --- | --- |
| Chạy đúng một lần, quan sát callback kết thúc | `TaskScheduler.schedule(Runnable, Instant)` hoặc Java `schedule`; giữ `ScheduledFuture` |
| Chạy lặp, cần biết mỗi lần thành công/lỗi | Metrics/listener/tracing và run status riêng; không `get()` một Future vô hạn |
| Chạy lặp, cần ngừng lịch sau N lần | Counter per invocation + cancel handle/lifecycle sau điều kiện; giám sát cancel |
| Callback chỉ gửi sang `@Async` | Future/acknowledgment của job con; scheduler handle chỉ cho dispatch |
| Công việc dài cần tra cứu qua REST | Job id + status store; không giữ request thread block bằng Future.get vô hạn |
| Có nhiều instance và yêu cầu lịch không bị trùng | Điều phối phân tán/Quartz/job store/leader policy phù hợp |
| Muốn virtual threads cho nhiều callback ngắn chặn I/O | Kiểm tra `SimpleAsyncTaskScheduler` và JDK21+; đo completion tại worker thật, lưu ý fixed-delay |
| Muốn xử lý lỗi lặp bằng tiếp tục hoặc dừng | Chọn/kiểm thử `ErrorHandler` rõ ràng, ghi failure per-run |

## 13. Kiểm thử hợp đồng thay vì đoán từ tên API

Một bộ test đáng tin cần có các case sau:

- **One-shot đang chạy:** giữ callback bằng latch, xác nhận Future chưa done; mở latch rồi `get(timeout)` trả và done.
- **Periodic đã chạy ít nhất một tick:** đợi first-tick latch, xác nhận Future vẫn chưa done và `get(timeout)` timeout; hủy handle và cleanup.
- **Periodic callback ném exception:** với Java thuần xác nhận những lượt kế tiếp bị suppress; với Spring có error handler mặc định, kiểm tra chính sách log/suppress hoặc cấu hình custom thay vì dùng kỳ vọng Java máy móc.
- **Callback delegating:** scheduler callback return ngay sau khi submit, child chờ latch; xác nhận scheduler Future done nhưng child status chưa done.
- **Hủy khi task đang chạy:** xác nhận cancellation state khác worker exit; nếu cần stop, kiểm tra token/interrupt và cleanup riêng.
- **Cron không có lần sau:** đối với `schedule(Runnable, Trigger)`, kiểm tra trường hợp `null` nếu trigger không cung cấp lịch tiếp.
- **@Scheduled nhiều instance:** xác nhận số lần được kích hoạt và quy tắc tránh double run khi chạy nhiều replica.
- **Shutdown/restart:** xác nhận cách lịch được đăng ký lại, misfire/retry/dedup nếu có job store.

Test không nên phụ thuộc `Thread.sleep(100)` để đoán worker đã bắt đầu. Sử dụng latch, barrier hoặc fake I/O controllable. Sau test phải mở latch trong `finally`, hủy các handle và đóng executor để không rò thread.

## 14. Những hiểu nhầm thường gặp

**“schedule() trả về nghĩa là task hoàn tất.”** Sai. Nó có thể chỉ mới được đăng ký, đang đợi đến hạn hoặc chờ thread.

**“Periodic Future.isDone() false nghĩa là chưa có lượt nào chạy.”** Sai. Nó có thể đã chạy hàng nghìn tick nhưng chuỗi lặp vẫn còn active.

**“get() trên periodic Future đợi lần tick đầu.”** Sai. Với Java ScheduledExecutorService, Future của chuỗi lặp không hoàn tất bình thường sau một tick.

**“Java periodic ném lỗi thì Spring @Scheduled luôn dừng.”** Sai. Spring có error handler có thể log/suppress exception cho repeating task.

**“ThreadPoolTaskScheduler luôn tạo worker mới cho mỗi callback.”** Sai. Nó thực thi callback trên scheduler threads; cần phân biệt với SimpleAsyncTaskScheduler.

**“SimpleAsyncTaskScheduler trigger fired nghĩa là business hoàn tất.”** Không có bảo đảm chung. Cần đo callback execution/child completion theo implementation và kiểu schedule.

**“@Scheduled có return CompletableFuture thì caller tự nhận Future đó.”** Không đúng với hợp đồng annotated scheduling thông thường. Trả về thông thường bị bỏ qua; reactive scheduled methods có quy tắc khác, cần xem tài liệu phiên bản.

**“lastCompletion trong TriggerContext chắc chắn là lúc child async xong.”** Sai nếu callback chỉ enqueue job rồi return.

**“cancel handle dừng mọi job con đã gửi.”** Sai. Cần cancellation protocol riêng cho child.

**“Quartz có job store nên mọi side effect xử lý exactly once.”** Sai. Job store/lifecycle và business idempotency là hai trách nhiệm khác nhau.

**“HTTP 202 từ endpoint scheduling nghĩa là báo cáo đã sẵn sàng.”** Sai. Nó có thể chỉ xác nhận đã nhận yêu cầu lập lịch; cần job id/endpoint trạng thái nếu người dùng cần kết quả.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** `schedule(Runnable, Instant)` trên `ThreadPoolTaskScheduler` trả `ScheduledFuture<?>`. Khi `get(timeout)` trả bình thường, điều gì được xác nhận?

**Đáp án:** callback Runnable được scheduler quản lý đã kết thúc bình thường. Nếu callback chỉ gửi job sang executor khác rồi return, chưa xác nhận job con hoàn tất.

**Câu 2.** Một `ScheduledFuture<?>` periodic có `isDone()==false` sau 100 lần chạy; điều đó có bất thường không?

**Đáp án:** không. Nó đại diện cho chuỗi lặp còn hoạt động, không phải handle của lần chạy thứ 100.

**Câu 3.** Vì sao không nên gọi `get()` không timeout trên Future lặp để chờ một tick?

**Đáp án:** Future của lịch lặp có thể không hoàn tất bình thường chừng nào lịch tiếp tục. Caller có thể block vô hạn.

**Câu 4.** JDK ScheduledThreadPoolExecutor thuần gặp exception ở tick thứ hai sẽ làm gì?

**Đáp án:** một exception không được xử lý trong callback lặp có thể chấm dứt chuỗi, suppress các tick tiếp theo và Future.get ném ExecutionException.

**Câu 5.** Spring repeating task có chắc dừng khi ném exception như JDK thuần không?

**Đáp án:** không. Spring thường có ErrorHandler log/suppress lỗi để lịch tiếp tục; phải xác minh handler của implementation đã chọn.

**Câu 6.** Vì sao `lastCompletion()` trong Spring `TriggerContext` có thể thấp hơn thời điểm báo cáo thực sự được tạo xong?

**Đáp án:** vì đó là mốc callback scheduler kết thúc; callback có thể chỉ dispatch `@Async` rồi trả về trước worker business.

**Câu 7.** Khi nào cần hai handle?

**Đáp án:** khi callback scheduler chỉ lập lịch/gửi một task nghiệp vụ khác. Dùng `ScheduledFuture` cho dispatch và Future/job status riêng cho kết quả nghiệp vụ.

**Câu 8.** Với `@Scheduled` chạy ở ba replicas, có thể có ba lượt xử lý cùng job không?

**Đáp án:** có, nếu mỗi instance đăng ký lịch local và không có điều phối tránh trùng. Cần chiến lược distributed scheduling/locking/idempotency nếu yêu cầu một lần.

**Câu 9.** Một Future bị cancel có chứng minh mọi side effect đã rollback không?

**Đáp án:** không. Cần cơ chế stop/compensation/idempotency và quan sát kết quả thực tế.

## 16. Tài liệu tham khảo chính thức

- Spring Framework Reference — Task Execution and Scheduling, TaskScheduler, @Scheduled: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework 6.2 API — ThreadPoolTaskScheduler: https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html
- Spring Framework API — TaskUtils và ErrorHandler cho repeating/one-shot tasks: https://docs.spring.io/spring-framework/docs/6.0.1/javadoc-api/org/springframework/scheduling/support/TaskUtils.html
- Spring Framework API — @Scheduled và scheduler qualifier (Spring 6.1+): https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html
- Java SE 17 — ScheduledExecutorService: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html
- Java SE 17 — ScheduledFuture: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledFuture.html
- Java SE 17 — Future, cancellation và ExecutionException: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html


</details>

- [Quay lại đầu trang](#back-to-top)
