<a id="back-to-top"></a>

# Giới hạn phân tán

## Menu
- [@Scheduled đăng ký lịch riêng trên từng thể hiện ứng dụng](#distributed-limits-001)
- [Khóa trong JVM không ngăn công việc ở máy khác](#distributed-limits-002)
- [Chọn một nơi kích hoạt hoặc cơ chế phối hợp có phạm vi phù hợp](#distributed-limits-003)
- [Tính lặp an toàn bằng mã công việc và dấu mốc xử lý](#distributed-limits-004)
- [Không coi nhận tác vụ trong bộ nhớ là đã lưu công việc bền vững](#distributed-limits-005)
- [Xử lý mất tiến trình, khởi động lại và công việc bị bỏ dở](#distributed-limits-006)
- [Phân biệt điều phối tác vụ cục bộ với hàng đợi, workflow hoặc lịch phân tán](#distributed-limits-007)
- [Ghi rõ kỳ vọng về chạy trùng, bỏ lỡ và thứ tự](#distributed-limits-008)

## <a id="distributed-limits-001">@Scheduled đăng ký lịch riêng trên từng thể hiện ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# @Scheduled đăng ký lịch riêng trên từng thể hiện ứng dụng

## 1. Vấn đề: một cron expression, nhiều ứng dụng cùng thực hiện

Giả sử một ứng dụng Spring Boot có tác vụ đóng sổ hằng ngày:

```java
@Scheduled(cron = "0 0 2 * * *", zone = "UTC")
public void closeDailyLedger() {
    ledgerService.closeDay();
}
```

Đây là **mảnh mã tích hợp Spring**, giả định bean được Spring quản lý, đã bật `@EnableScheduling` và có `ledgerService`. Tại một môi trường chỉ có một application instance, bạn có thể quan sát một lần gọi theo lịch và lầm tưởng annotation `@Scheduled` đã tạo ra **một lịch duy nhất cho toàn hệ thống**.

Khi triển khai ba replica trên Kubernetes hoặc ba tiến trình JVM sau load balancer, mỗi replica thường có **ApplicationContext và scheduler của riêng nó**. Nếu cả ba đều bật scheduling và đều có cùng bean `closeDailyLedger`, mỗi replica **tự đăng ký lịch 02:00** và đều có thể gọi method ở thời điểm đó.

```text
                    cùng một mã Spring Boot
                              |
                    +---------+---------+
                    |         |         |
                 Pod A      Pod B      Pod C
                 JVM A      JVM B      JVM C
                    |         |         |
              Context A  Context B  Context C
                    |         |         |
              @Scheduled  @Scheduled  @Scheduled
                    |         |         |
             02:00 UTC   02:00 UTC   02:00 UTC
                    |         |         |
               closeDay    closeDay    closeDay
                    \         |         /
                     \        |        /
                        chung database
```

**Đây không phải một race condition lạ hoặc lỗi của Spring.** `@Scheduled` là hạ tầng lập lịch **cục bộ trong phạm vi Spring application context**, không phải một dịch vụ phối hợp lịch phân tán. Nó không tự bầu leader, không tự tạo distributed lock, không tự đồng bộ số lần xử lý giữa các JVM, và không bảo đảm chính xác một lần cho toàn cluster.

Điểm cần nhớ: **mỗi thể hiện ứng dụng có lịch riêng** khi nó đăng ký bean và bật scheduling. Điều kiện “tại cùng một phút có ba lần gọi method” có thể xảy ra, nhưng không thể hứa luôn đúng ba lần: node có thể down, khởi động trễ, bị quá tải, hoặc trigger/lifecycle làm bỏ lượt.

Bài này giải thích ranh giới của `@Scheduled`, chứng minh bằng hai ApplicationContexts chạy thật, chỉ ra những tình huống duplicate/missed job khi scale/rolling deployment, và hướng dẫn chọn giải pháp phù hợp: best-effort local job, một scheduler chuyên trách, distributed lock/lease, atomic claim vào database, durable queue và Quartz cluster.

## 2. Phạm vi chính xác: ApplicationContext, JVM, pod và cluster

Không nên dùng từ “instance” mơ hồ. Có ít nhất bốn tầng:

| Tầng | Đối tượng | Ý nghĩa với @Scheduled |
| --- | --- | --- |
| Spring bean instance | Một object được Spring quản lý | Bean có method annotated được đăng ký lịch khi hạ tầng scheduling xử lý nó |
| `ApplicationContext` | Container quản lý một tập beans | `@EnableScheduling` áp dụng cho **context cục bộ**; có thể tồn tại nhiều contexts trong một JVM |
| JVM / application process | Một tiến trình Java | Có thể chứa một hoặc nhiều contexts; scheduler state thường ở trong process |
| Replica/pod/container | Một bản chạy ứng dụng trên hạ tầng | Thường có JVM/context riêng; scale lên thêm replica có thể thêm scheduled executions |
| Cluster | Nhiều tiến trình/replicas cùng phục vụ hệ thống | `@Scheduled` mặc định **không** có điều phối leader/lock chia sẻ giữa các thành viên |

Do đó, phát biểu chính xác không phải “mỗi JVM đúng một lịch”, mà là:

> **Mỗi Spring-managed bean instance có method @Scheduled được xử lý trong mỗi ApplicationContext có bật scheduling sẽ đăng ký các trigger tương ứng trong context ấy.**

Thông thường, một Boot replica có một context chính và một bean singleton cho mỗi loại job; trong mô hình phổ biến ấy, một replica có một bộ trigger. Nhưng không được khái quát cho mọi ứng dụng: có thể có root/child contexts, bean được tạo nhiều lần, method có nhiều annotation `@Scheduled`, hoặc hai application contexts trong một JVM.

Chính JavaDoc `@EnableScheduling` của Spring xác nhận annotation chỉ có hiệu lực trong **local application context**. Muốn áp dụng ở nhiều contexts, phải bật ở từng context; điều này cũng có nghĩa tạo thêm context có scheduling **không tự hợp nhất** các lịch với context cũ.

## 3. Spring đăng ký lịch qua những thành phần nào?

Với cấu hình Spring Framework 6.2.x:

1. `@EnableScheduling` nhập cấu hình hạ tầng hỗ trợ scheduling.
2. Spring đăng ký `ScheduledAnnotationBeanPostProcessor` để phát hiện methods `@Scheduled` trên các beans mà context quản lý.
3. Mỗi khai báo lịch được chuẩn bị thành scheduled task theo `cron`, `fixedRate`, `fixedDelay`, `initialDelay` và cấu hình liên quan.
4. `ScheduledTaskRegistrar` và `TaskScheduler` (hoặc `ScheduledExecutorService`) của context cung cấp cơ chế thực thi trigger.
5. Khi ApplicationContext bị đóng, các scheduled tasks/resources được quản lý theo vòng đời context/scheduler tương ứng.

```text
ApplicationContext của instance A
  @EnableScheduling
         |
         v
ScheduledAnnotationBeanPostProcessor
         |
         v
tìm bean Foo có @Scheduled
         |
         v
ScheduledTaskRegistrar
         |
         v
TaskScheduler trong context A
         |
         v
lần gọi Foo.tick() trong instance A


ApplicationContext của instance B
  có bộ hạ tầng tương tự nhưng KHÁC và ĐỘC LẬP
```

Nếu có một `TaskScheduler` bean duy nhất hoặc một bean tên `taskScheduler`, Spring có quy tắc tìm scheduler thích hợp; nếu không có cấu hình phù hợp, Spring có thể tạo scheduler cục bộ đơn luồng làm fallback. Việc sử dụng **cùng tên bean** `taskScheduler` tại ba replicas **không** có nghĩa ba replica dùng chung một scheduler object. Tên bean chỉ có ý nghĩa tra cứu trong mỗi context.

Nếu khai báo:

```java
@Scheduled(scheduler = "reportTimer", cron = "0 0 2 * * *", zone = "UTC")
public void closeDailyLedger() { /* ... */ }
```

thì từ Spring 6.1, thuộc tính `scheduler` chọn scheduler bean/qualifier trong **context hiện tại**. Dùng tên `reportTimer` giống nhau trên mọi replica **vẫn không tạo distributed scheduler**.

Tương tự, tăng `poolSize` của `ThreadPoolTaskScheduler` từ 1 lên 5 chỉ thêm khả năng chạy callback trong **một context**, không thay đổi số contexts cũng như số replica có quyền phát sự kiện nghiệp vụ.

## 4. Chứng minh bằng hai Spring ApplicationContexts riêng trong cùng một JVM

Ví dụ sau không cần Kubernetes hoặc hai service thật. Nó tạo **hai `AnnotationConfigApplicationContext` độc lập** từ cùng một cấu hình `@EnableScheduling`; mỗi context có scheduler và job bean riêng. Một `CountDownLatch` chờ cho đến khi mỗi context đã chạy callback ít nhất một lần. Bộ đếm static dùng **chỉ để quan sát trong demo**, không đại diện cho distributed lock hoặc shared storage.

### Cấu trúc Maven

```text
per-context-schedule/
├── pom.xml
└── src/main/java/demo/PerContextScheduleDemo.java
```

File `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>per-context-schedule</artifactId>
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

File `src/main/java/demo/PerContextScheduleDemo.java`:

```java
package demo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public final class PerContextScheduleDemo {
    private static final AtomicInteger nextId = new AtomicInteger();
    private static final Set<Integer> firstFires = ConcurrentHashMap.newKeySet();
    private static final CountDownLatch bothFired = new CountDownLatch(2);

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    public static class NodeConfig {

        @Bean("taskScheduler")
        public ThreadPoolTaskScheduler taskScheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("node-timer-");
            return scheduler;
        }

        @Bean
        public NodeJob job() {
            return new NodeJob(nextId.incrementAndGet());
        }
    }

    public static final class NodeJob {
        private final int nodeId;
        private final AtomicBoolean first = new AtomicBoolean();

        public NodeJob(int nodeId) {
            this.nodeId = nodeId;
        }

        @Scheduled(initialDelay = 100, fixedDelay = 100)
        public void tick() {
            if (first.compareAndSet(false, true)) {
                firstFires.add(nodeId);
                bothFired.countDown();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (var nodeOne =
                     new AnnotationConfigApplicationContext(NodeConfig.class);
             var nodeTwo =
                     new AnnotationConfigApplicationContext(NodeConfig.class)) {

            if (!bothFired.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Not all local schedulers fired");
            }

            boolean separateSchedulers =
                    nodeOne.getBean("taskScheduler")
                    != nodeTwo.getBean("taskScheduler");

            System.out.println("contexts = 2");
            System.out.println("different scheduler beans = "
                    + separateSchedulers);
            System.out.println("node 1 fired = " + firstFires.contains(1));
            System.out.println("node 2 fired = " + firstFires.contains(2));
            System.out.println("distinct first fires = " + firstFires.size());
        }
    }
}
```

Chạy bằng **JDK 17+** và Maven trong thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.PerContextScheduleDemo
```

Stdout do chương trình in, không kể log của Maven/JShell:

```text
contexts = 2
different scheduler beans = true
node 1 fired = true
node 2 fired = true
distinct first fires = 2
```

Đã chạy logic tương đương với Spring 6.2.9 trong JShell và kiểm chứng cả năm dòng kết quả. Ví dụ không dùng thời gian tuyệt đối làm điều kiện test, mà dùng latch có timeout. Lịch vẫn tiếp tục tick cho đến khi context đóng; `AtomicBoolean first` chỉ giới hạn **số lần demo ghi nhận lượt đầu**, không ngừng phương thức `tick` được scheduling.

Giải thích:

1. `NodeConfig` chỉ là **một loại cấu hình Java** được dùng để tạo **hai ApplicationContexts** riêng.
2. Mỗi lần context được khởi tạo, `@EnableScheduling` đăng ký một bộ scheduling infrastructure trong context ấy.
3. Mỗi context tạo một bean `NodeJob` với ID khác nhau và một `ThreadPoolTaskScheduler` riêng.
4. Mỗi scheduler tự gọi `tick()`. Tới khi cả hai callback có lượt đầu, `bothFired` về 0 và main in kết quả.
5. `nodeOne.getBean("taskScheduler") != nodeTwo.getBean(...)` xác nhận hai bean là hai object khác nhau.
6. Đóng contexts qua `try-with-resources` dọn lifecycle scheduler do Spring quản lý.

**Giới hạn minh họa:** hai contexts ở **cùng JVM** dùng chung static fields để test, nhưng ở **hai JVM/pods thật** những static fields hoàn toàn **không chia sẻ**. Trong cluster, muốn điều phối phải dùng storage/coordination chung như database, broker, lease service; không thể thay bằng `static AtomicInteger`, `ConcurrentHashMap` hoặc `synchronized` trong một replica.

## 5. Cùng một cron trên ba replicas sẽ xảy ra điều gì?

Ví dụ ba pod cùng chạy:

```java
@Scheduled(cron = "0 0 2 * * *", zone = "UTC")
public void produceDailyReport() {
    reportService.create();
}
```

Tại mốc 02:00 UTC hợp lệ, nếu cả ba ứng dụng đang hoạt động, scheduler có capacity và mỗi bean đã được đăng ký đúng, **cả ba đều có thể gọi `reportService.create()`**.

```text
Dự kiến cron: 02:00:00 UTC
                  |
                  +-- Pod A tick 02:00:00.004 -> create()
                  +-- Pod B tick 02:00:00.019 -> create()
                  +-- Pod C tick 02:00:00.056 -> create()
                                                      |
                                           có thể sinh 3 kết quả
```

Các timestamp chỉ là **giả định minh họa**, không phải jitter được đo trong hệ thống thật. Ba lần gọi không nhất thiết đồng thời: schedule của từng JVM, GC pause, tải thread pool, đồng hồ node và thời điểm startup ảnh hưởng đến giờ thực thi.

Nếu `create()` không idempotent, hậu quả có thể là:

- Gửi ba email giống nhau.
- Cộng doanh thu hoặc tính phí ba lần.
- Hai worker cùng cập nhật một snapshot làm mất update.
- Tạo ba record cho cùng business date.
- Cùng tải dữ liệu lớn ba lần, gây DB/HTTP overload.
- Một job lấy lock DB, hai job còn lại đứng chờ và gây tăng p99.

**Không có bảo đảm “ít nhất một replica sẽ chạy”** chỉ vì có ba replicas. Nếu cả ba down tại mốc cron hoặc resource cạn, cả ba có thể bỏ lỡ. Do đó số replicas tăng **không tự giải quyết durability của lịch**.

## 6. Scale, rolling deployment và restart có thể thay đổi số lượt chạy

### Scale từ 1 lên 3 replicas

Khi một deployment đổi `replicas=1` thành `replicas=3`, mỗi pod mới tạo ApplicationContext và đăng ký scheduled methods của nó. Nếu cron giống nhau, số **candidate dispatches** tăng tương ứng với số contexts đủ điều kiện. Không cần ai “bật thêm lịch” trong một bảng trung tâm, vì bản thân mỗi context tự làm việc đó.

### Rolling update

Có lúc pod phiên bản cũ và pod phiên bản mới cùng tồn tại trong giai đoạn triển khai. Hai versions có thể cùng chạy cron tại mốc cận thời gian rollout. Nếu cả hai sử dụng cùng business key, phải chống duplicate giữa phiên bản, không chỉ giữa các replica cùng version.

Không thể suy ra rằng chỉ vì Kubernetes giữ `desiredReplicas=2` thì tại mọi thời điểm thực tế luôn có đúng hai JVM với scheduling hoạt động. Readiness, termination grace period, deployment strategy và startup time đều ảnh hưởng.

### Restart trùng thời điểm

- Với `initialDelay` hoặc `fixedRate`, mốc đầu tiên có thể tính tương đối với lúc scheduler được đăng ký trong từng process, nên các replicas có thể không đồng pha.
- Với `cron`, nhiều replicas nhìn cùng wall-clock zone có thể có cùng ứng viên mốc, nhưng không bắt buộc bắt đầu chính xác cùng millisecond.
- `@Scheduled` thông thường không lưu mỗi missed firing vào database để replay khi JVM restart.
- Một job đang chạy có thể bị interrupt/hủy khi shutdown, nhưng side effect bên ngoài có thể đã hoàn thành trước đó. Không thể dùng “shutdown sạch” như cơ sở đảm bảo exactly-once.

### Root context và child contexts

Một JVM có thể có nhiều ApplicationContexts (chẳng hạn cấu hình ứng dụng web theo mô hình root/child). `@EnableScheduling` áp dụng **cục bộ** cho context chứa nó. Nếu cùng loại job bean được tạo trong nhiều contexts có scheduling, bạn có thể nhận nhiều schedules **trong một JVM**, chứ không cần chờ tới khi scale thành nhiều pods.

## 7. Nhận diện các nguyên nhân “@Scheduled chạy hai lần”

Không phải mọi duplicate đều do cluster. Cần phân biệt:

| Nguyên nhân | Phạm vi | Dấu hiệu |
| --- | --- | --- |
| Hai hoặc nhiều replicas cùng chạy cron | Nhiều JVMs/pods | Instance ID khác nhau, cùng job/period key |
| Hai ApplicationContexts có scheduling | Cùng hoặc khác JVM | Context ID khác, có bean job tương ứng |
| Tạo hai bean instances từ cùng class `@Scheduled` | Một context | Hai bean names/identities đăng ký callback |
| Một method có nhiều khai báo `@Scheduled` | Một bean | Spring xử lý từng trigger riêng, có thể overlap |
| Dùng `@Configurable` và đồng thời đăng ký bean theo cách khác | Một JVM | Bean có thể bị khởi tạo kép, Spring docs cảnh báo |
| Worker retry cùng một job | Một hay nhiều JVM | Cùng job ID, attempt number khác |
| Message broker redelivery | Nhiều consumers | Cùng event ID/offset-key, ack/timeout khác |
| Lịch chạy gần nhau do timezone/DST | Theo zone và trigger | Cùng local time nhưng có thể khác Instant |
| Scheduler thread bị chậm rồi kích hoạt muộn | Một context | Scheduled instant và actual start khác nhau |

Đầu tiên log ít nhất: `instanceId`, `contextId`, `jobName`, `businessKey`, `scheduledInstant`/`startedInstant`, `attemptId` và `threadName`. **Cùng giờ trong log chưa đủ kết luận** vì cron có thể tạo một bản ghi cho mỗi instance hoặc hai lần local time hợp lệ khi DST.

JavaDoc Spring cũng nhắc `@Scheduled` là annotation repeatable: cùng method có nhiều declarations sẽ tạo các triggers độc lập, thậm chí chạy đồng thời hoặc liền nhau. Spring còn cảnh báo không đồng thời áp dụng `@Configurable` để khởi tạo một class `@Scheduled` vốn đã là bean trong container nếu không muốn double invocation.

## 8. Bài toán nghiệp vụ quyết định có cần điều phối cluster hay không

Không phải mọi lần tick trùng đều là lỗi. Hãy định nghĩa **hợp đồng công việc**:

| Yêu cầu | Ví dụ | Chiến lược gợi ý |
| --- | --- | --- |
| **Mỗi replica phải tự chạy** | Dọn cache local, cập nhật metric local, kiểm tra connection tại chính pod | Dùng `@Scheduled` ở mọi replica; không dùng distributed lock |
| **Có thể chạy trùng, kết quả giống nhau** | Recompute cache chung một cách idempotent, đọc trạng thái | Chấp nhận duplicate nếu chi phí chấp nhận được; đo downstream load |
| **Tối đa một worker cùng lúc** | Batch nặng không được overlap | Distributed lock/lease với chính sách timeout rõ |
| **Một intent cho mỗi business date** | Khóa sổ, gửi báo cáo, lập hóa đơn | Unique key + atomic claim, durable status/worker |
| **Mỗi job phải hoàn tất cuối cùng** | Settlement và đồng bộ tích hợp quan trọng | Persistent job ledger/queue, retry, recovery, idempotency |
| **Nhiều job cần chia tải** | Xử lý 10.000 tenants theo partition | Partition/claim per item; đừng khóa toàn cluster thành một worker |
| **Cần cron phân tán và misfire policies** | Hàng nghìn durable triggers, failover | Scheduler chuyên dụng hoặc Quartz cluster với persistent JobStore |

**Tránh áp dụng distributed lock cho mọi tác vụ.** Một cache cleanup local nên chạy ở mọi pod; nếu dùng lock toàn cluster chỉ cho một pod cleanup, các pod khác có thể giữ cache cũ. Ngược lại, một job tính phí không nên chạy trên mỗi pod chỉ vì scheduler mặc định làm như vậy.

## 9. Cách 1: một scheduler chuyên trách hoặc chỉ một replica bật lịch

Phương án đơn giản là tách vai trò:

```text
Web replicas A/B/C (không bật job trọng yếu)
                   |
                   +----- DB/queue <----- Scheduler process S
                                             |
                                          @Scheduled
                                             |
                                     phát durable job intent
```

Có thể dùng feature flag/config để chỉ một process đăng ký scheduled job:

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "jobs.daily.enabled",
        havingValue = "true")
@EnableScheduling
public class DailySchedulingConfiguration {
    // Chỉ cấu hình jobs muốn được bật ở process này.
}
```

Đây là **mảnh cấu hình Spring Boot**, cần Spring Boot `@ConditionalOnProperty`, Spring `@Configuration`, `@EnableScheduling` và tổ chức bean/scanning phù hợp. Chú ý: **tắt `@EnableScheduling` ở một config không tự vô hiệu hóa tất cả scheduled jobs** nếu một config khác đã bật scheduling và đã có bean job trong context. Hãy thiết kế gating cụ thể cho bean/job, kiểm thử từng deployment profile.

Ưu điểm: dễ vận hành và ít contention nếu luôn có đúng một scheduler process.

Giới hạn:

- Scheduler process chết thì không có instance khác tự nhận vai trò nếu chưa cấu hình failover.
- Rollout có thể tạo giai đoạn cả scheduler cũ và mới cùng chạy, hoặc không có cái nào.
- Một process riêng không tự tạo persistence cho missed ticks.
- Nếu chỉ dùng một biến môi trường trên mỗi pod mà không có coordination, cấu hình lỗi có thể khiến hai pods cùng bật job.

Nếu cần high availability, dùng **leader election/lease** và vẫn cần persistent job state nếu không được phép bỏ việc.

## 10. Cách 2: distributed lock hoặc lease cho critical section

Một mô hình phổ biến:

```text
02:00:00   Pod A --> try acquire lease -- success --> run job
           Pod B --> try acquire lease -- denied  --> skip
           Pod C --> try acquire lease -- denied  --> skip
```

Lock phải nằm ở một **hệ thống chung** như database/Redis/coordination service. `synchronized`, `ReentrantLock` hoặc `AtomicBoolean` bên trong một JVM **không điều phối được các JVM khác**.

Trong Spring, có thể dùng các library như **ShedLock** với `@SchedulerLock` và provider lưu lock vào database/Redis. Nhưng ShedLock là **dependency bổ sung**, không phải thành phần tự có chỉ nhờ `@EnableScheduling`. Quy tắc `lockAtMostFor`/`lockAtLeastFor`, version, provider, server time và behavior của AOP/proxy cần đọc đúng tài liệu library.

Một pseudo-code thuần khái niệm:

```java
@Scheduled(cron = "0 0 2 * * *", zone = "UTC")
public void scheduledTick() {
    lockService.withClusterLease("daily-job", lease -> {
        dailyService.runOnce();
    });
}
```

Đây **không phải Java độc lập**; `lockService`, `dailyService` và semantics của `withClusterLease` do ứng dụng cung cấp. Đừng chép như một API có sẵn trong Spring core.

### Lease hết hạn là một bẫy

Nếu worker A lấy lease 5 phút nhưng job chạy 10 phút, lease hết hạn khi A **vẫn đang làm việc**. Pod B có thể lấy lease mới, và **hai worker cùng chạy**. Ngay cả khi dùng TTL dài, GC pause, network partition và mất kết nối đến lock store vẫn có thể tạo split-brain trong một khoảng thời gian.

Muốn bảo vệ shared resource mạnh hơn, xem **fencing token**: mỗi lần acquire cấp một số thứ tự tăng. Downstream/database từ chối lệnh ghi từ owner có token cũ sau khi owner mới đã chiếm quyền. Chỉ lưu một boolean “đang giữ lock” trong RAM không đủ.

**Distributed lock chủ yếu kiểm soát tính đồng thời**, không bảo đảm công việc chắc chắn hoàn tất đúng một lần. Nếu A làm side effect xong rồi chết trước khi ghi kết quả, B có thể retry và lặp side effect. Vì thế còn cần idempotency/persistent completion state.

## 11. Cách 3: atomic claim và unique business key trong database

Với mục tiêu **một ý định xử lý mỗi ngày**, cách đáng tin thường là để mọi pod có thể tick, nhưng chỉ một record công việc được **tạo nguyên tử** theo khóa nghiệp vụ.

Ví dụ khóa:

```text
(job_name, tenant_id, business_date)
```

Với các tác vụ theo **giờ UTC**, business key có thể là `scheduled_utc_hour`/`window_start`. Không dùng `System.currentTimeMillis()` làm khóa ngẫu nhiên cho mỗi tick vì mỗi pod sẽ tạo một key khác, khiến unique constraint vô hiệu.

### PostgreSQL: bảng theo dõi intent bền vững

```sql
CREATE TABLE scheduled_runs (
    job_name text NOT NULL,
    tenant_id text NOT NULL,
    business_date date NOT NULL,
    status text NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    claimed_by text,
    lease_until timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (job_name, tenant_id, business_date)
);
```

Tại cùng thời điểm, ba pod đều chạy câu lệnh:

```sql
INSERT INTO scheduled_runs (
    job_name, tenant_id, business_date, status
) VALUES (
    'DAILY_SETTLEMENT', 'tenant-42', DATE '2026-10-29', 'PENDING'
)
ON CONFLICT DO NOTHING
RETURNING job_name, tenant_id, business_date;
```

Nếu database hoạt động và transaction commit thành công theo điều kiện bình thường, **chỉ một câu insert tạo được row mới** nhờ primary key; pod khác nhận zero rows do conflict. Nếu ứng dụng bị timeout sau commit nhưng trước khi nhận response, nó không thể biết chắc kết quả của mình; hãy **đọc lại bằng business key**, không phát một intent khác với key mới.

Câu lệnh trên mới bảo đảm **một row intent** cho business key, **chưa bảo đảm một lần side effect**. Đó là chủ ý: cron chỉ có trách nhiệm tạo/đối soát những ý định công việc bền vững, worker xử lý sau.

### Claim và retry sau crash

Một worker có thể chọn một row `PENDING`, đặt `status='RUNNING'` và `lease_until` bằng một transaction claim nguyên tử; nhiều workers không được claim cùng một row trong cùng trạng thái. Với PostgreSQL, `SELECT ... FOR UPDATE SKIP LOCKED` trong transaction hoặc một câu `UPDATE ... RETURNING` có điều kiện là các building blocks. Thiết kế cụ thể phải tính tới transaction isolation, locks, lease expiry, retry count và worker ownership.

Luồng hợp lý:

```text
@Scheduled ở mỗi replica
           |
           v
INSERT intent có unique business key
           |
           +--- conflict ---> không tạo bản thứ hai
           |
           +--- 1 row PENDING bền vững
                         |
                         v
               worker atomic claim
                         |
                    RUNNING + lease
                         |
                 side effect idempotent
                         |
                 COMPLETED/FAILED
                         |
                 retry/recovery nếu cần
```

**Trạng thái `RUNNING` không được treo vĩnh viễn.** Nếu worker chết, một worker khác phải khôi phục sau lease timeout hoặc quy tắc recovery. Ngược lại, nếu worker cũ vẫn đang chạy sau khi lease hết hạn, cần fencing/idempotency để không ghi chồng.

Khi cần đảm bảo thay đổi business DB và intent phát thông báo được commit cùng nhau, dùng **transactional outbox** hoặc cùng transaction trên một database phù hợp, tránh dual-write gap giữa DB và broker.

## 12. Minh họa Spring: nhiều tick nhưng chỉ một ý định cho ngày

Mẫu sau dành cho ứng dụng có database job ledger. Nó **không phải chương trình độc lập**; các repository/service được giả định và cần được triển khai với transaction, unique constraint và recovery đúng:

```java
@Component
public class DailySettlementTicker {
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("UTC");

    private final ScheduledRunService runs;

    public DailySettlementTicker(ScheduledRunService runs) {
        this.runs = runs;
    }

    @Scheduled(cron = "0 5 1 * * *", zone = "UTC")
    public void tick() {
        LocalDate yesterday =
                LocalDate.now(BUSINESS_ZONE).minusDays(1);

        runs.ensurePendingThrough("DAILY_SETTLEMENT", yesterday);
    }
}
```

Đối với Spring-managed service `ScheduledRunService`:

- `ensurePendingThrough` duyệt **mọi business date cần xử lý nhưng chưa có intent**, không chỉ đúng hôm qua. Vì nếu ứng dụng down ba ngày, khởi động lại cần backfill chứ không được bỏ quên các ngày trước.
- Mỗi lần tạo intent sử dụng `INSERT ... ON CONFLICT DO NOTHING` với unique key (job, tenant, business date).
- Nếu nhiều replicas đồng thời gọi service, database là nơi đưa ra quyết định nguyên tử.
- Sau khi ghi `PENDING`, worker/broker phụ trách thực thi và cập nhật completion.
- Phần `@Scheduled` nên nhanh, tránh giữ scheduler thread thực thi một batch kéo dài nhiều giờ.

Tại thời điểm cron, cả ba pods vẫn **có thể gọi `tick()`**. Điều thay đổi là **chỉ một intent được tạo** cho từng business key. Số lần method chạy và số lần nghiệp vụ hoàn tất là hai metric khác nhau.

Không đặt `@Transactional` lên method giả định rồi mặc nhiên cho rằng ba replicas được serialize. Transaction bảo đảm atomicity của thao tác trong DB, nhưng **unique constraint/locking** mới giải quyết cạnh tranh claim. Self-invocation qua `this.method()` còn có thể làm mất transactional proxy trong chế độ proxy mặc định.

## 13. Vì sao “một intent” chưa phải exactly-once execution?

Xét một worker đã claim job:

```text
T0:  Claim job PENDING -> RUNNING
T1:  Gọi API thanh toán, API trả thành công
T2:  JVM chết trước khi lưu COMPLETED
T3:  Lease hết hạn
T4:  Worker khác chạy lại cùng job
```

Ở T4, job có thể thực hiện lại side effect dù API đã xử lý ở T1. **At-least-once processing** dẫn đến possibility of duplicates. Để an toàn:

1. Dùng một **idempotency key ổn định** theo business operation mà external API hỗ trợ, ví dụ kết hợp order ID và loại operation.
2. Nếu side effect là database trong cùng transaction, lưu **processed-job marker** và áp dụng thay đổi trong cùng DB transaction với unique constraint.
3. Nếu side effect ở bên thứ ba không hỗ trợ idempotency, cần cơ chế đối soát/truy vấn kết quả/bù nghiệp vụ, không thể chỉ dựa vào local DB marker rồi hứa exactly-once.
4. Chỉ ghi `COMPLETED` khi có bằng chứng việc thực sự hoàn tất theo contract.
5. Khi retry, giữ cùng operation/business ID; không phát ID mới làm mỗi lần retry trở thành một công việc độc lập.

**Phân biệt ba mục tiêu**:

| Mục tiêu | Cần tối thiểu |
| --- | --- |
| *At-most-one active worker* | Lock/lease/claim, có thể cần fencing |
| *At-most-one intent for business window* | Unique business key + atomic insert |
| *Eventually process every durable intent* | Persistent status/queue, retry, worker recovery |
| *Exactly-once business outcome* | Idempotency/atomic transaction/coordination thực tế; không tự có bởi @Scheduled |

Khi yêu cầu cao, hãy thiết kế từ **business invariant** (“không trừ tiền hai lần”) rồi chọn lock/idempotency/recovery, thay vì chỉ nhìn một con số “số lần tick”.

## 14. Cách 4: hàng đợi bền vững và worker consumers

Một lựa chọn hiệu quả khi có nhiều công việc độc lập:

```text
3 replicas đều tick
       |
       v
atomic insert vào job ledger / outbox
       |
       v
relay phát thông điệp cho queue/broker
       |
       v
consumer group phân phối jobs
       |
       v
worker xử lý + idempotency + ack
```

Lợi ích: các web replicas không phải làm batch nặng trên thread scheduler; có thể scale consumers riêng; broker hỗ trợ backlog và retry theo cấu hình.

Nhưng **broker không tự xóa mọi giới hạn**:

- Publisher cần producer confirm/persistence phù hợp, nếu không message vẫn có thể mất.
- Database transaction và broker publish thường không nguyên tử nếu dual-write; outbox là một giải pháp phổ biến.
- Consumer có thể nhận lại message sau crash trước ack; side effect phải idempotent.
- Ordering, retention, dead-letter queue, rate limiting và recovery cần được cấu hình.
- Nếu enqueue một message độc lập từ **mỗi replica** mà không có unique business key hoặc dedup, broker có thể chỉ làm **xử lý ba messages nhanh hơn**, không sửa duplicate.

Đúng mục tiêu là **một ý định nghiệp vụ bền vững**, rồi chia tác vụ tới workers; không nhất thiết là chỉ một JVM được phép thực thi mọi loại job.

## 15. Cách 5: Quartz clustered JobStore hoặc Kubernetes CronJob

Nếu yêu cầu là một **lịch tập trung có quản lý trigger và failover**, nên cân nhắc scheduler chuyên dụng.

### Quartz với JDBC JobStore clustering

Quartz hỗ trợ chạy nhiều scheduler nodes dùng **chung JDBC JobStore** với cluster configuration phù hợp. Với một trigger firing, các scheduler nodes phối hợp qua database để một node acquire trigger; có chính sách misfire và recovery, tùy job/triggers/configuration.

Điểm cần chú ý:

- Các Quartz nodes cần chung tên scheduler/JobStore theo cấu hình cluster; mỗi node có instance ID riêng.
- Dùng đúng database schema, locking và `org.quartz.jobStore.isClustered=true` theo tài liệu Quartz version thực tế.
- Recovery của job đang chạy phụ thuộc cấu hình `requestsRecovery` và định nghĩa job; không suy ra mọi side effect chắc chắn chỉ thực hiện một lần.
- Database cluster locks có chi phí, không phải mọi nhiệm vụ 1 giây đều nên đi qua một cơ chế khóa toàn cluster.
- Quartz **khác** annotation `@Scheduled` đơn giản. Thêm một `SchedulerFactoryBean` chưa tự đổi mọi phương thức `@Scheduled` của Spring thành Quartz jobs.

Quartz phù hợp khi cần cron persistence, nhiều triggers, quản lý misfire, recovery và điều phối scheduler workers — nhưng vẫn cần idempotency của business side effects.

### Kubernetes CronJob

Thay vì đặt `@Scheduled` trong mỗi web pod, Kubernetes `CronJob` có thể tạo Job theo cron ở tầng control plane:

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: daily-settlement
spec:
  schedule: "5 1 * * *"
  timeZone: "Etc/UTC"
  concurrencyPolicy: Forbid
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
            - name: settlement
              image: example.invalid/settlement:release
```

Đây là **manifest minh họa**, `image` phải được thay bằng image hợp lệ trong hệ thống thật. `concurrencyPolicy: Forbid` hạn chế overlap giữa các Jobs của **cùng CronJob** theo Kubernetes control plane; không cấm một script được chạy qua một cơ chế khác hoặc thao tác trùng từ nhiều CronJobs.

Quan trọng: **Kubernetes CronJob cũng không hứa exactly-once Job creation hoặc exactly-once side effect**. Controller có thể trễ/miss hoặc tạo hai Jobs trong một số trường hợp theo docs. Job/pod có thể restart hoặc retry. Vì vậy job vẫn cần idempotency/ledger và quan sát trạng thái.

`timeZone` xác định zone của lịch; `startingDeadlineSeconds`, `concurrencyPolicy`, concurrency tại Job/pod level và chính sách history là các chi tiết vận hành cần đọc theo phiên bản Kubernetes.

Nếu chỉ yêu cầu “một nơi khởi tạo công việc theo lịch”, CronJob có thể đơn giản hơn scale-out web replicas cùng bật `@Scheduled`. Nếu yêu cầu business date không được bỏ sót qua downtime kéo dài, vẫn cần ledger/reconciliation.

## 16. Spring Batch và JobRepository không đồng nghĩa với scheduler cluster

Một Spring Batch job có thể được kích hoạt từ `@Scheduled`, nhưng **hai Spring Batch JobLaunchers ở hai replicas vẫn có thể cùng được gọi**. Spring Batch `JobRepository` dùng `JobInstance` và identifying JobParameters để quản lý metadata/trạng thái execution; đúng cách dùng có thể ngăn mở các executions xung đột cho **cùng JobInstance**.

Nhưng nếu mỗi replica đặt `run.id=timestamp ngẫu nhiên` hoặc tạo một bộ JobParameters khác nhau cho cùng business date, Batch có thể coi đó là những **JobInstances khác nhau** và xử lý trùng nghiệp vụ. Hãy xác định identifying parameters theo period/job key và chính sách restartability.

Một `JobRepository` không tự định nghĩa thời gian kích hoạt cron và không mặc nhiên giải quyết mọi việc ở external API. Nó là thành phần cho batch execution state; scheduling/cluster coordination/idempotency vẫn là thiết kế tổng thể.

Không nên biến một job toàn cluster thành **một transaction dài hàng giờ**; dùng checkpoints/chunks và status bền vững khi thích hợp.

## 17. Tác vụ chạy ở mọi replica đôi khi là đúng

Xét vài trường hợp:

- Mỗi pod có một cache **local**: dọn cache tại một pod không tự dọn cache của pod khác. Mỗi pod có `@Scheduled` là hợp lý.
- Mỗi pod tự cập nhật metric `activeConnections` của nó: một pod khác không thể đại diện chính xác cho node này.
- Một polling worker được **phân vùng theo shard key**: mỗi node có thể tick để claim phân đoạn công việc riêng; dùng unique partition ownership thay vì một global lock.
- Một health check cục bộ: có thể tất cả node đều cần chạy, nhưng theo dõi tải của dependencies chung để không tạo thundering herd.

Khi tất cả replicas cùng polling một datasource chung, cân nhắc **jitter** hoặc random initial delay có kiểm soát để trải đều tải nếu nghiệp vụ không cần cùng mốc tuyệt đối. Nhưng **jitter không giải quyết duplicate**: nó chỉ giảm việc nhiều pods cùng đánh vào downstream đúng một millisecond.

Đối với một periodic scan chung có thể thực thi idempotently, đôi khi **chấp nhận một vài scans trùng** rẻ và đơn giản hơn phát triển leader election phức tạp. Quyết định dựa trên mức độ nguy hại của duplicate, năng lực downstream và mục tiêu vận hành.

## 18. Timezone, đồng hồ node và business window

Ba nodes có thể chạy cùng cron nhưng dùng thời gian khác nếu cấu hình khác nhau. Đặt zone cụ thể:

```java
@Scheduled(cron = "0 0 2 * * *", zone = "UTC")
public void tick() {
    // Chọn một business window ổn định, rồi claim qua DB/queue.
}
```

Nếu doanh nghiệp tính ngày theo Việt Nam, zone đúng có thể là `Asia/Ho_Chi_Minh`; nếu theo chi nhánh New York, dùng `America/New_York` và có chính sách DST cho giờ bị bỏ/lặp.

Đừng tạo idempotency key bằng **timestamp thực của mỗi replica khi method bắt đầu**, vì các node có millisecond khác nhau. Dùng window đã được định nghĩa theo business zone/period, ví dụ `(tenant, businessDate)` hoặc `(job, scheduledUtcHour)`.

Đồng hồ node chênh lệch có thể khiến cron kích hoạt lệch nhau, lease timeout không nhất quán và job bị claim không đúng thời điểm. Nếu dùng lease, nên ưu tiên **DB/server time** nơi có thể và có monitoring clock skew. Đồng bộ thời gian của hosts là yêu cầu vận hành, không phải tác dụng phụ của `@Scheduled`.

Nếu dùng cron 02:30 giờ địa phương ở vùng DST, có thể có ngày 02:30 không tồn tại hoặc hai lần 01:30. Một unique key theo business date sẽ xử lý khác key theo `Instant`; hãy chọn theo invariant, không dựa vào cách Spring hiển thị giờ.

## 19. Lifecycle, tài nguyên và độ chính xác của lịch

Với `ThreadPoolTaskScheduler`, callback thường thực thi trên scheduler thread của context. Nếu method chặn I/O trong thời gian dài, những scheduled methods khác trong cùng context có thể bị trễ, nhưng **không ảnh hưởng trực tiếp lên thread pool của instance khác**.

Lịch `fixedDelay` được tính sau lần gọi trước hoàn tất **trong cùng scheduler/trigger**. `fixedRate` và `cron` có semantics khác. Vì vậy, khi nhiều replicas khởi động ở các thời điểm khác nhau, `fixedDelay`/`fixedRate` có thể tạo các pha chạy lệch nhau.

Nếu một method chỉ submit vào worker pool rồi trả ngay, `@Scheduled(fixedDelay=...)` đang chờ **kết thúc dispatch**, không nhất thiết đợi side effect worker. Có thể phát sinh overlap **trong cùng instance**, bên cạnh duplicate giữa instances.

Nếu executor sử dụng `CallerRunsPolicy` khi bị bão hòa, work có thể chạy trên scheduler thread và kéo trễ lịch. Nếu queue in-memory bị mất khi crash, restart không tự khôi phục. Những giới hạn này tách biệt với câu hỏi cluster có bao nhiêu replicas nhưng góp phần quyết định job có được hoàn tất.

**Tóm lại:** “scheduler đã tick”, “executor nhận việc”, “worker bắt đầu”, “DB/API commit” và “business result đã có” là các trạng thái khác nhau, cần đo riêng.

## 20. Kế hoạch kiểm thử phân tán thực tế

Một chương trình Java chỉ mở hai contexts như mục 4 chứng minh **local registration semantics**, chưa chứng minh một cluster đúng-once. Test production-like cần ít nhất:

1. **Một instance:** chạy baseline, xác định registration, cron zone, scheduler bean và times.
2. **Hai hoặc ba replicas:** bật cùng `@Scheduled`, thu log có `instanceId` và `businessKey`; thấy mỗi node đăng ký lịch riêng.
3. **Race khi claim:** cho hai nodes cùng tranh cùng row/key; đảm bảo unique constraint chỉ cho một intent commit.
4. **Rolling deployment:** hai versions sống chồng trong một khoảng; các versions cùng phải giữ invariant.
5. **Restart ngay trước mốc cron:** kiểm tra missed tick có được ledger/reconciliation phát hiện.
6. **Crash sau tạo intent trước worker:** worker khác có thể tiếp tục từ record `PENDING`.
7. **Crash sau side effect trước mark complete:** retry phải không gây duplicate nghiệp vụ.
8. **Lease hết hạn khi worker cũ còn chạy:** xác nhận fencing/idempotency ngăn stale owner ghi không hợp lệ.
9. **Database/Redis/broker down:** không được log “job complete” khi chưa có bảo đảm; có retry/backoff.
10. **Downstream chậm hoặc bị rate limit:** tránh tất cả replicas tạo retry storm.
11. **DST/timezone:** cùng business window được tính thống nhất ở mọi node.
12. **Soak và load:** kiểm tra DB lock contention, hàng đợi, backlog tuổi lớn nhất, CPU/heap và thời gian hoàn tất.

Những test gây crash hoặc network partition cần môi trường test cô lập; không tự kill production pods khi đang thực hiện tác vụ tiền thật.

### Một assertion dễ hiểu

Nếu 3 replicas cùng chạy một cron ngày, dashboard có thể ghi:

```text
local_tick_total{instance="A"} = 1
local_tick_total{instance="B"} = 1
local_tick_total{instance="C"} = 1

job_intent_created_total = 1
duplicate_intent_conflict_total = 2

job_completed_total = 1
```

Các số này là **trường hợp mong muốn minh họa**, không phải kết quả production. Nó cho thấy **3 tick cục bộ là bình thường**, điều cần chặn là ba tác dụng nghiệp vụ cho một business date. Nếu `job_completed_total` bằng 0, phải xem backlog/retry chứ không được kết luận “đã an toàn vì chỉ tạo một intent”.

## 21. Monitoring và troubleshooting khi phát hiện job trùng

Nhóm metric tối thiểu:

| Metric | Câu hỏi |
| --- | --- |
| `scheduled_tick_total` theo instance/context | Có bao nhiêu contexts đang tick? |
| `job_claim_attempt_total` theo job/window | Bao nhiêu nodes cố nhận một công việc? |
| `job_claim_success_total` | Ai giành được quyền xử lý? |
| `duplicate_intent_conflict_total` | Có bao nhiêu insert thua do unique constraint? |
| `job_started_total` / `job_completed_total` | Bao nhiêu công việc thật sự bắt đầu/hoàn tất? |
| `job_failed_total` / `retry_total` | Công việc nào đang lỗi/lặp lại? |
| `pending_age_seconds` | Intent bền vững nào chưa được xử lý lâu nhất? |
| `lease_expired_total` | Có jobs bị treo hoặc worker mất kết nối không? |
| `business_effect_duplicate_total` | Có hậu quả nghiệp vụ không mong muốn không? |

Log có cấu trúc nên chứa `jobName`, `businessDate`/`windowStart`, `instanceId`, `contextId`, `attempt`, `claimToken`, `scheduledInstant`, `actualInstant` và lỗi cùng correlation ID.

Khi thấy "cùng job 02:00 chạy ba lần", xác định:

1. Có bao nhiêu pods/contexts đang bật `@EnableScheduling`?
2. Có nhiều bean instances hoặc repeatable `@Scheduled` trên cùng method không?
3. Các log là **tick**, **attempt** hay **completion**?
4. Cùng business key hay key khác (do timezone/mốc thực)?
5. Chỉ có duplicate submission hay side effect đã nhân đôi?
6. Có lock/unique key/idempotency? Transaction đã commit chưa?
7. Có rolling deployment, restart hoặc lease expiry cùng thời điểm không?

Đừng dập triệu chứng bằng cách giảm replica về 1 nếu hệ thống cần scale; hãy thiết kế đúng ranh giới local schedule vs cluster work ownership.

## 22. Lỗi triển khai phổ biến

1. **“Có một @Scheduled class nên toàn cluster chỉ có một lịch.”** Sai: mỗi context có thể đăng ký riêng.
2. **“Ba pods luôn có đúng ba lần hoàn tất.”** Không: có thể missed, duplicate, restart, overload và lỗi nghiệp vụ.
3. **“`@EnableScheduling` global cho tất cả JVMs.”** Không; nó áp dụng cho context cục bộ.
4. **“Tên bean `taskScheduler` giống nhau là shared scheduler.”** Không; bean scope theo context.
5. **“Tăng scheduler pool size sẽ giải quyết duplicate giữa pods.”** Chỉ thay concurrency cục bộ.
6. **“`synchronized` ngăn được hai JVMs cùng chạy.”** Không; monitor là local process.
7. **“Một Redis lock 5 phút đảm bảo job 10 phút không overlap.”** Lease có thể hết hạn khi owner còn chạy.
8. **“Lấy distributed lock là đạt exactly-once.”** Không bảo đảm commit/retry/side effects không trùng.
9. **“Thêm random runId cho Spring Batch giúp chống trùng.”** Nó có thể tạo JobInstances khác nhau cho cùng business date.
10. **“Bật Quartz clustering tự chuyển @Scheduled methods thành Quartz jobs.”** Hai registration mechanisms khác nhau.
11. **“Kubernetes CronJob mặc định exactly-once.”** Controller và Jobs có thể miss hoặc duplicate.
12. **“Trả success khi workPool.execute() nhận task là job đã hoàn tất.”** Submit và completion khác nhau.
13. **“Một unique key rồi bỏ luôn trạng thái job.”** Worker crash có thể để job pending/running không được recovery.
14. **“Retry với business key mới sẽ an toàn.”** Nó phá idempotency và tạo nhiều intent độc lập.
15. **“Chặn tất cả tick trùng nghĩa là mọi business date đều có kết quả.”** At-most-once trigger không phải eventual completion.
16. **“Đọc `LocalDate.now()` trên mọi node sẽ luôn ra cùng business date.”** Zone/clock drift có thể khác.
17. **“Ghi row COMPLETED trước external API để tránh duplicate.”** Có thể mất side effect nếu API thất bại sau đó.
18. **“Chỉ cần log mỗi @Scheduled invocation.”** Cần trạng thái claim/start/completed và business effect.
19. **“Chỉ một @EnableScheduling ở root context đủ cho mọi child contexts.”** EnableScheduling cục bộ, phải hiểu bean visibility và registration.
20. **“Có một instance riêng làm scheduler là tự có HA.”** Một process có thể down/rollout, vẫn cần recovery hoặc failover.

## 23. Tự kiểm tra kiến thức

**Câu 1.** Nếu một Spring Boot deployment có 4 replicas, cùng bật `@EnableScheduling` và cùng bean `@Scheduled(cron=...)`, số schedulers là bao nhiêu?  
**Đáp án:** Theo mô hình thông thường, mỗi replica có scheduler/context của nó, tức có bốn lịch đăng ký cục bộ. Không suy ra chắc chắn bốn business completions tại mọi mốc.

**Câu 2.** `@EnableScheduling` có tự bầu leader toàn cluster không?  
**Đáp án:** Không. Annotation bật xử lý lịch trong ApplicationContext hiện tại, không có distributed leader election/lock mặc định.

**Câu 3.** Hai `ApplicationContexts` ở cùng JVM có thể đều chạy một class `@Scheduled` không?  
**Đáp án:** Có nếu mỗi context bật scheduling và có bean tương ứng. Ví dụ Java hoàn chỉnh trong bài chứng minh điều này.

**Câu 4.** Tăng `poolSize` của `ThreadPoolTaskScheduler` từ 1 lên 10 có làm lịch chạy đúng một lần toàn cluster không?  
**Đáp án:** Không; đó là khả năng song song trong một context, không điều phối giữa replicas.

**Câu 5.** Vì sao `AtomicBoolean` hoặc `synchronized` không chống duplicate giữa pods?  
**Đáp án:** Vì đó là state/process-local. Nhiều JVMs không chia sẻ monitor hoặc biến static.

**Câu 6.** Một distributed lock ngăn hai workers hoạt động cùng lúc có đủ cho đúng một side effect không?  
**Đáp án:** Không; lease expiry và crash sau side effect trước complete có thể tạo retry trùng. Cần idempotency/transaction và có thể fencing.

**Câu 7.** Muốn mỗi business date có một ý định job, có nên đưa thời điểm `System.nanoTime()` của node vào unique key không?  
**Đáp án:** Không. Dùng key ổn định như `(job_name, tenant_id, business_date)`, tạo bằng unique constraint và atomic insert.

**Câu 8.** Hai pods cùng `INSERT ... ON CONFLICT DO NOTHING` cho một key, kết quả gì?  
**Đáp án:** Khi transaction/DB đúng và commit thành công, chỉ một row intent được tạo; nhưng chưa chứng minh worker chỉ thực hiện side effect một lần.

**Câu 9.** Có nên dùng distributed lock cho task dọn cache local của mỗi pod không?  
**Đáp án:** Thường không, vì mỗi pod cần dọn cache riêng. Mỗi instance chạy là hành vi mong muốn.

**Câu 10.** Một pod chạy cron bị down hai ngày. Thêm replica có tự backfill không?  
**Đáp án:** Không. Cần persistent ledger/job store và reconciliation hoặc scheduler có misfire/recovery policy phù hợp.

**Câu 11.** Quartz clustered JobStore và Spring `@Scheduled` có tương đương nhau không?  
**Đáp án:** Không. Quartz có cơ chế cluster triggers qua shared JobStore; `@Scheduled` mặc định là local task registration.

**Câu 12.** Nếu ba local ticks tạo một intent và cuối cùng một job hoàn tất, có phải hạ tầng scheduling bị lỗi vì 3 tick không?  
**Đáp án:** Không. Ba ticks là hệ quả của ba contexts. Mục tiêu là bảo đảm **một outcome nghiệp vụ đúng hợp đồng**.

**Ghi nhớ:** **`@Scheduled` đăng ký và thực thi lịch trong từng ApplicationContext/ứng dụng; nó không tạo một cron duy nhất trên cluster.** Khi mở rộng nhiều replicas, trước hết xác định job nào phải chạy ở từng node và job nào cần một lần theo business window. Với công việc cluster-wide quan trọng, hãy kết hợp **atomic business-key claim, durable job state, recovery và idempotency**; lock hoặc scheduler cluster là một phần của giải pháp, không tự bảo đảm exactly-once business side effect.

### Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling, @Scheduled, bean instances, repeatable triggers](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.2 — @EnableScheduling JavaDoc, local application context](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html)
- [Spring Framework 6.2.9 — @Scheduled JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework — ScheduledAnnotationBeanPostProcessor JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/ScheduledAnnotationBeanPostProcessor.html)
- [Quartz — Clustering with JDBC JobStore](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/configuration/ConfigJDBCJobStoreClustering.html)
- [Kubernetes — CronJob concepts and limitations](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)
- [Spring Batch — Domain Language / JobRepository and JobInstance](https://docs.spring.io/spring-batch/reference/domain.html)
- [PostgreSQL — INSERT ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html)
- [ShedLock — distributed locks for scheduled jobs](https://github.com/lukas-krecan/ShedLock)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="distributed-limits-002">Khóa trong JVM không ngăn công việc ở máy khác</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa trong JVM không ngăn công việc ở máy khác

Một Spring Boot application có phương thức `@Scheduled` quét các hóa đơn cần xử lý. Khi chỉ chạy một instance, nhóm phát triển thêm `synchronized`, `ReentrantLock` hoặc `AtomicBoolean` để tránh hai thread xử lý cùng một lúc. Trên máy lập trình, mọi bài kiểm tra đều đạt. Nhưng khi triển khai ba replica, **cả ba vẫn có thể cùng lấy khóa cục bộ và thực thi cùng nghiệp vụ**.

Đây là giới hạn căn bản của chủ đề **“Khóa trong JVM không ngăn công việc ở máy khác”**, thuộc nhóm **Giới hạn phân tán** của module **Spring Framework – concurrency**:

**Khóa nội bộ JVM chỉ điều phối những thread chia sẻ cùng một đối tượng/trạng thái khóa trong một JVM. Nó không tự điều phối các tiến trình Java, container, pod hoặc máy chủ khác.**

Để tránh xử lý trùng trên nhiều instance, phải xác định yêu cầu nghiệp vụ và sử dụng **một điểm điều phối dùng chung** khi cần: database transaction/claim, distributed lock hoặc lease, leader election, durable job queue, ràng buộc unique/idempotency. Kể cả khi có distributed lock, cần hiểu giới hạn của TTL, crash, network partition, fencing token và các side effect đã gửi ra bên ngoài.

Bài học giải thích vì sao khóa JVM thất bại trong mô hình phân tán, cách chứng minh bằng **hai tiến trình Java 17 độc lập**, áp dụng Spring Boot trên nhiều replica, một ví dụ PostgreSQL transaction-scoped advisory lock kết hợp khóa duy nhất theo lần chạy, kiểm thử và những tình huống vận hành quan trọng.

## 1. Bắt đầu từ tình huống cụ thể

Giả sử ứng dụng có job:

```java
@Component
public class InvoiceReminderJob {
    @Scheduled(fixedDelay = 10_000)
    public synchronized void sendReminders() {
        invoiceService.findPendingInvoices()
                .forEach(invoiceService::sendReminder);
    }
}
```

Đây là **fragment**: cần bean `invoiceService` được inject, `@EnableScheduling` và imports Spring tương ứng. Trên một instance với một singleton `InvoiceReminderJob`, từ khóa `synchronized` bảo đảm **mỗi lần chỉ một thread lấy monitor của instance đó** để chạy method.

Khi triển khai hai replica:

```text
                   LOAD BALANCER / KUBERNETES DEPLOYMENT
                                      |
              +-----------------------+-----------------------+
              |                                               |
              v                                               v
        Spring Pod A                                      Spring Pod B
        JVM process A                                     JVM process B
        InvoiceReminderJob bean A                         InvoiceReminderJob bean B
        monitor A (free)                                  monitor B (free)
              |                                               |
         @Scheduled tick                                @Scheduled tick
              |                                               |
         synchronized A                                synchronized B
         ACQUIRED                                     ACQUIRED
              |                                               |
              +----------> cùng truy vấn DB / API <-----------+
                          cùng hóa đơn đủ điều kiện
                          có thể gửi nhắc hai lần
```

Cả hai đoạn `synchronized` đều **đúng theo Java**: mỗi monitor chỉ có một owner thread tại một thời điểm. Nhưng không có monitor chung giữa hai JVM. Nếu mỗi instance gửi email, **email có thể bị gửi hai lần** dù không có race trong nội bộ từng process.

### 1.1. Đúng trong một JVM không suy ra đúng toàn cluster

| Phát biểu | Đúng hay sai? | Điều kiện |
| --- | --- | --- |
| `synchronized` trên cùng một object ngăn hai thread chạy cùng lúc | Đúng | Chúng dùng cùng object/monitor **trong cùng JVM** |
| `static synchronized` dùng một monitor cho mọi JVM | Sai | `Class` object là trạng thái runtime theo JVM/classloader |
| `ReentrantLock` có thể loại trừ lẫn nhau giữa các thread cùng dùng lock | Đúng | Các thread chia sẻ **cùng instance ReentrantLock** |
| `AtomicBoolean.compareAndSet` là “cluster-wide lock” | Sai | Giá trị atomic nằm trong bộ nhớ JVM hiện tại |
| Một Kubernetes Deployment với ba replicas có một monitor Java chung | Sai | Mỗi pod/process có heap và lock riêng |
| `@Scheduled` chạy một lần cho toàn ứng dụng nhiều replica | Sai theo mặc định | Mỗi context có thể đăng ký lịch riêng |
| DB unique constraint có thể chặn hai insert trùng khóa | Có | Mọi writer sử dụng cùng database/constraint và cùng business key |
| Distributed lock mặc nhiên bảo đảm exactly-once side effect | Sai | Crash, lease expiry, retry và external side effects vẫn cần thiết kế |

Điểm học tập: **phạm vi bảo vệ phải trùng phạm vi tài nguyên/side effect cần bảo vệ**. Nếu dữ liệu được dùng chung toàn cluster mà khóa chỉ nằm trong process, khóa không bao phủ bài toán.

## 2. Khóa Java thực sự nằm ở đâu?

### 2.1. synchronized dùng monitor của một đối tượng

Java `synchronized (lock) { ... }` yêu cầu thread lấy monitor gắn với **đối tượng `lock`**. Với method synchronized của instance, monitor là `this`; với static synchronized method, monitor là `Class` object tương ứng trong JVM/classloader.

```java
final class LocalGuard {
    private final Object monitor = new Object();

    void runOnceAtATime() {
        synchronized (monitor) {
            doWork();
        }
    }

    private void doWork() {
        // ...
    }
}
```

Đây là fragment. Nếu hai thread gọi **cùng `LocalGuard` instance**, chúng tranh cùng monitor. Nếu hai đối tượng `new LocalGuard()` khác nhau, mỗi đối tượng có monitor riêng — **ngay cả trong cùng JVM** chúng đã có thể chạy song song.

### 2.2. static không có nghĩa “toàn thế giới”

```java
public final class ClusterJob {
    private static final Object MONITOR = new Object();

    public static void run() {
        synchronized (MONITOR) {
            // ...
        }
    }
}
```

`static` nghĩa là trạng thái gắn với class trong một runtime/classloader, **không phải biến chia sẻ qua tất cả tiến trình** có cùng class name. Khi JVM A và JVM B cùng load class `ClusterJob`, chúng có **hai `MONITOR` khác nhau** và hai heap riêng.

Ngay trong một JVM, hệ thống classloader đặc biệt còn có thể làm các bản sao class được load tách biệt. Vì vậy, với local lock, phải biết **đối tượng lock cụ thể** nào được chia sẻ, không chỉ biết tên class.

### 2.3. ReentrantLock, Semaphore và AtomicBoolean cũng cục bộ

Một mẫu tránh overlap cục bộ:

```java
private final ReentrantLock localLock = new ReentrantLock();

@Scheduled(fixedRate = 5_000)
public void poll() {
    if (!localLock.tryLock()) {
        return; // Chỉ bỏ qua khi một thread trong cùng JVM giữ lock.
    }
    try {
        processBatch();
    } finally {
        localLock.unlock();
    }
}
```

Đây là fragment, cần Spring bean và imports `ReentrantLock`. Nó có giá trị **trong một instance** nhưng không ngăn một instance Spring khác cũng chạy `processBatch()`.

Tương tự:

- `Semaphore(1)`: số permit thuộc một object trong một JVM.
- `AtomicBoolean.compareAndSet(false, true)`: atomic cho một vị trí bộ nhớ trong một JVM.
- `ConcurrentHashMap` khóa theo key: map nằm trong heap của instance.
- `ReadWriteLock`/`StampedLock`: không tự truyền trạng thái qua mạng.
- `volatile`: quy tắc visibility/happens-before trong Java Memory Model, **không phải distributed consistency**.
- Một “singleton” Spring bean là singleton **trong bean factory/application context**, không tự là global singleton giữa các process.

### 2.4. Khóa nội bộ vẫn hữu ích

Không phải thấy hệ thống phân tán là phải bỏ local locks. Chúng vẫn có thể:

- Ngăn hai code paths trong **cùng process** sửa cùng state không thread-safe.
- Giới hạn số local requests tới một API/connection.
- Bảo vệ cache in-memory hoặc queue cục bộ.
- Hạn chế số lần submit trùng trong một instance.
- Giảm tranh chấp trước khi truy cập shared lock/store.

Nhưng **local lock là lớp phòng vệ cục bộ**. Nếu yêu cầu của job là “không có hai replica cùng tác động một tài khoản”, phải bổ sung cơ chế ở phạm vi **tài khoản/toàn cluster**.

## 3. @Scheduled có gì đặc biệt khi chạy nhiều replica?

Spring Framework `@Scheduled` được phát hiện bởi scheduling infrastructure trong mỗi Spring `ApplicationContext` đã bật `@EnableScheduling`. Spring không tự tạo một “cluster-wide coordinator” cho tất cả JVM cùng có class/job đó.

Ví dụ:

```java
@Configuration
@EnableScheduling
class SchedulingConfig {
}

@Component
class DailyReconciliation {
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void run() {
        // ...
    }
}
```

Nếu deployment chạy ba replica đều load config/job này, **mỗi instance có thể đăng ký một cron trigger** cho 02:00 UTC. Ba tick gần cùng mốc là behavior bình thường, không phải lỗi Spring.

Chú ý các ranh giới:

1. `@EnableScheduling` chỉ kích hoạt processor trong **context** tương ứng, không bật distributed coordination.
2. `ThreadPoolTaskScheduler#setPoolSize(1)` chỉ giới hạn worker của một scheduler trong một JVM.
3. `@Scheduled(fixedDelay=...)` có thể tránh overlap của cùng một recurring schedule ở một instance, nhưng không kiểm soát instance khác.
4. `@Scheduled(cron=...)` kèm `zone` đồng bộ **cách tính mốc thời gian**, không đồng bộ **quyền thực thi nghiệp vụ**.
5. `@Transactional` tạo transaction ở database theo cấu hình, nhưng một transaction đơn thuần **không mặc nhiên** ngăn hai replica thực hiện hai nghiệp vụ giống nhau; cần khóa/điều kiện ghi/unique constraint phù hợp.
6. Thêm `@Async` có thể tạo thêm overlap ngay trong cùng JVM nếu method lịch chỉ submit việc rồi return.

### 3.1. “Một pod chạy” có thật sự là một pod?

Khi deploy rolling update, có thể tồn tại old pod và new pod **cùng lúc**. Replica count được cấu hình là 1 **không đủ làm cam kết tuyệt đối** không bao giờ có hai process sống trong mọi thời điểm rollout/failover/restart. Nếu xử lý trùng là không chấp nhận được, hãy dùng ràng buộc dữ liệu hoặc cơ chế coordination ngoài process ngay cả khi topology hôm nay trông như singleton.

### 3.2. Hai node không nhất thiết là hai máy vật lý

Hai container trên cùng một physical host vẫn có **hai JVM/heaps/monitors riêng**. Ngược lại, hai threads trong một JVM có thể share lock bất kể CPU core khác nhau. Ranh giới ở đây là **shared memory/lock object**, không phải dây mạng hay số CPU.

## 4. Java 17 runnable: hai JVM có thể cùng vào vùng synchronized

Thí nghiệm chỉ nói “mở hai terminal rồi nhìn log” dễ phụ thuộc timing. Chương trình dưới đây **tự tạo hai tiến trình Java con** từ một tiến trình cha. Mỗi child khóa một `static final Object` **của chính JVM đó** rồi viết file marker vào một thư mục tạm dùng chung.

Child A **chưa được phép rời vùng synchronized** cho tới khi thấy marker do child B tạo, và ngược lại. Nếu hai JVM phải tranh cùng monitor, chúng sẽ không thể cùng tạo marker trong khi giữ khóa. Vì chúng thực sự có hai monitor độc lập, cả hai marker xuất hiện và tiến trình cha xác nhận thành công.

Đây là demo về **phạm vi JVM lock**. Các file marker chỉ đóng vai trò *barrier để quan sát*, không phải một distributed lock production hay một cơ chế đảm bảo xử lý duy nhất.

Lưu **ProcessLocalLockDemo.java**:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class ProcessLocalLockDemo {
    private static final Object LOCAL_MONITOR = new Object();

    private static void child(String name, Path sharedDir)
            throws Exception {
        synchronized (LOCAL_MONITOR) {
            Files.writeString(
                    sharedDir.resolve(name + ".entered"), "inside");

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(15);

            // Vẫn ở trong synchronized cho tới khi CẢ HAI
            // JVM đã vào vùng khóa cục bộ của chúng.
            while (!(Files.exists(sharedDir.resolve("A.entered"))
                    && Files.exists(sharedDir.resolve("B.entered")))) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException(
                            "Other JVM did not enter");
                }
                Thread.sleep(10);
            }
        }
    }

    private static Process launch(String javaExecutable,
            String classPath, Path dir, String name)
            throws Exception {
        return new ProcessBuilder(
                javaExecutable,
                "-cp", classPath,
                ProcessLocalLockDemo.class.getName(),
                "child", name, dir.toString())
                .inheritIO()
                .start();
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("child")) {
            child(args[1], Path.of(args[2]));
            return;
        }

        String executable = System.getProperty("os.name")
                .toLowerCase().contains("win") ? "java.exe" : "java";
        String javaPath = Path.of(System.getProperty("java.home"),
                "bin", executable).toString();
        String classPath = System.getProperty("java.class.path");

        Path dir = Files.createTempDirectory("jvm-lock-demo-");
        Process a = null;
        Process b = null;

        try {
            a = launch(javaPath, classPath, dir, "A");
            b = launch(javaPath, classPath, dir, "B");

            boolean aFinished = a.waitFor(20, TimeUnit.SECONDS);
            boolean bFinished = b.waitFor(20, TimeUnit.SECONDS);
            if (!aFinished || !bFinished) {
                throw new IllegalStateException(
                        "Child process timeout");
            }
            if (a.exitValue() != 0 || b.exitValue() != 0) {
                throw new IllegalStateException(
                        "Child process failed");
            }
            if (!Files.exists(dir.resolve("A.entered"))
                    || !Files.exists(dir.resolve("B.entered"))) {
                throw new IllegalStateException(
                        "Missing marker");
            }

            System.out.println("jvmCount=2");
            System.out.println("bothHeldOwnLocalLock=true");
        } finally {
            if (a != null && a.isAlive()) a.destroyForcibly();
            if (b != null && b.isAlive()) b.destroyForcibly();
            Files.deleteIfExists(dir.resolve("A.entered"));
            Files.deleteIfExists(dir.resolve("B.entered"));
            Files.deleteIfExists(dir);
        }
    }
}
```

Chạy với **JDK 17+** (cần cho phép tạo subprocess/temp file ở môi trường chạy):

```bash
javac ProcessLocalLockDemo.java
java ProcessLocalLockDemo
```

**Stdout của tiến trình cha trong điều kiện thành công:**

```text
jvmCount=2
bothHeldOwnLocalLock=true
```

### 4.1. Vì sao kết luận có cơ sở?

1. Tiến trình cha tạo hai child JVM bằng `ProcessBuilder` và sử dụng `java.home` của runtime hiện tại.
2. Child A và B đều chạy cùng class `ProcessLocalLockDemo`, nhưng mỗi process có **heap và object `LOCAL_MONITOR` riêng**.
3. Mỗi child ghi marker **bên trong vùng synchronized**, rồi **tiếp tục giữ khóa** cho tới khi thấy cả hai marker.
4. Để cả A và B đều thoát thành công, chúng phải **cùng hiện diện trong vùng synchronized cục bộ của mình** tại một thời điểm.
5. Tiến trình cha chỉ in kết quả sau khi hai child kết thúc exit code 0 và marker tồn tại.
6. Khi một child lỗi hoặc chậm quá deadline, chương trình ném exception, **không in kết luận thành công giả**.
7. `finally` cố dọn child còn chạy và file tạm.

**Điều không chứng minh:** các marker file không tạo ra giao dịch phân tán, không bảo vệ database hay email. Demo chỉ chứng minh `synchronized` trên hai process **không phải một monitor chung**. Trên máy bị sandbox hạn chế subprocess hoặc filesystem, demo có thể fail vì môi trường, không làm thay đổi quy tắc JVM.

### 4.2. Một JVM với hai thread có khác gì?

Nếu cả hai threads dùng đúng `LOCAL_MONITOR` **trong một JVM**, một thread vào synchronized trước sẽ chặn thread còn lại cho tới lúc nhả monitor. Khi mỗi thread chạy trong JVM khác, không có shared monitor để tranh. Vì thế, một unit test tạo hai threads trong cùng Spring context **không đủ chứng minh tính an toàn khi deploy hai pods**.

## 5. Vì sao local AtomicBoolean vẫn bị trùng trên hai replica?

Một mẫu thường được dùng:

```java
private final AtomicBoolean busy = new AtomicBoolean(false);

@Scheduled(fixedRate = 5000)
public void poll() {
    if (!busy.compareAndSet(false, true)) {
        return;
    }
    try {
        processPendingJobs();
    } finally {
        busy.set(false);
    }
}
```

Trong JVM A:

```text
busyA: false -> true    [được vào]
```

Trong JVM B:

```text
busyB: false -> true    [cũng được vào]
```

Cả hai `compareAndSet` đều đúng, nhưng chúng tác động **hai AtomicBoolean khác nhau**. Không có một “atomic compare-and-set xuyên hai máy” chỉ bằng class Java.

Bản thân `AtomicBoolean` còn cần được sử dụng cẩn thận trong **cùng** JVM: nếu một async operation được submit rồi method return, `finally` reset `busy` quá sớm, trong khi công việc thật vẫn chạy. Khi đó local guard còn không bảo vệ trọn business scope ở một instance.

Kết luận: biết **thời điểm bắt đầu và kết thúc nghiệp vụ thật** trước khi xác định phạm vi lock; **phạm vi bảo vệ của khóa phải bao trùm side effect**.

## 6. Ba câu hỏi trước khi chọn distributed coordination

Trước khi chọn Redis/ShedLock/PostgreSQL/Quartz, hãy định nghĩa yêu cầu rõ:

### 6.1. Muốn tránh overlap hay tránh chạy trùng một lần logic?

- **Không overlap:** tại một thời điểm, tối đa một worker đang xử lý cùng job/key.
- **At most once per time window:** ví dụ với mốc 10:00, chỉ được ghi nhận một lần chạy `daily_reconciliation:2026-10-29`, kể cả hai worker chạy **nối tiếp** nhau.
- **At least once:** job đủ điều kiện cuối cùng phải được thực hiện, kể cả sau crash.
- **Effectively once / exactly-once business effect:** không nhân đôi side effect quan sát được; thường phải dựa vào idempotency, transaction, outbox/consumer dedup và reconciliation.

Một **distributed mutex** chủ yếu giúp tránh overlap **trong thời gian lock còn hợp lệ**. Nó **không** tự lưu lịch sử mốc job đã chạy, không replay task mất và không ngăn mọi duplicate do retry sau lỗi mạng.

### 6.2. Key khóa phải trùng với tài nguyên cần bảo vệ

Ví dụ:

- `job:nightly-aggregate`: khóa một job toàn hệ thống.
- `account:123:reconcile`: khóa chỉ những thao tác trên account 123; account 456 vẫn có thể xử lý song song.
- `invoice:INV-999:send-email`: business idempotency key của một invoice/event.

Một global lock cho mọi invoice có thể làm giảm throughput không cần thiết. Ngược lại, một khóa **per replica ID** không bảo vệ business key chung.

Nếu mục tiêu là “mỗi hóa đơn được gửi email đúng một lần”, khóa theo tên job mỗi phút chưa chắc đủ; nhiều lượt kế tiếp vẫn có thể gửi lại cùng invoice. Cần trạng thái/unique key cho **invoice notification event**, không chỉ mutex.

### 6.3. Khi một node chết, ai tiếp quản?

Khóa thuần JVM tự biến mất khi process chết, nhưng **không giúp process khác biết ai là chủ sở hữu job và đang tới bước nào**. Distributed coordination cần:

- Cách xác định owner hiện tại.
- Quy tắc hết hạn/giải phóng khi owner chết hoặc network partition.
- Cách follower nhận quyền xử lý.
- Cách tránh owner cũ tiếp tục ghi sau khi đã mất quyền.
- Cách phục hồi công việc chưa hoàn thành và tránh lặp side effect.

Nếu không giải thích được các điều này, một distributed lock có thể tạo cảm giác an toàn hơn thực tế.

## 7. Các lựa chọn thực tế và phạm vi đảm bảo

| Cơ chế | Chia sẻ giữa JVM? | Mạnh ở đâu? | Giới hạn chính |
| --- | --- | --- | --- |
| `synchronized` / `ReentrantLock` | Không | Thread safety cục bộ | Không bao phủ process khác |
| `AtomicBoolean` / `Semaphore` | Không | Local non-overlap/concurrency limit | Không có cluster ownership |
| PostgreSQL advisory transaction lock | Có, nếu cùng DB và lock key | Mutual exclusion trong thời gian transaction giữ khóa | Không tự định danh “đã chạy mốc nào” hay bảo vệ side effect ngoài DB |
| DB row claim/unique constraint | Có, nếu cùng DB/schema | Atomic claim/at-most-once record theo key | Phải thiết kế retry, failed state, transaction, cleanup |
| Redis-based lease/lock | Có, nếu dùng cùng Redis/namespace | Mutual exclusion theo TTL/owner convention | TTL, failover, partition, stale owner, token/clock caveats |
| ShedLock với shared LockProvider | Có | Bỏ qua tick ở node không có lock, tích hợp `@Scheduled` | Không phải job scheduler bền vững; hết lease khi task còn chạy có thể trùng |
| Leader election (Kubernetes Lease, v.v.) | Có | Chỉ một leader điều phối lịch trong một giai đoạn | Lease mất, chuyển leader, stale leader, cần fencing/idempotency |
| Durable queue/DB job store | Có | Claim, retry, state, phục hồi sau crash | Phức tạp hơn; exactly-once effects vẫn cần idempotency |

**Không có hàng nào trong bảng tự cung cấp mọi bảo đảm.** Chọn theo SLO và độ nghiêm trọng của trùng/mất job; kết hợp nhiều tầng nếu cần.

## 8. PostgreSQL transaction-scoped advisory lock: một ví dụ phối hợp thật

PostgreSQL có **advisory locks** cho tài nguyên do ứng dụng định danh. `pg_try_advisory_xact_lock(bigint)` thử lấy khóa exclusive ở **phạm vi transaction**:

- Trả `true` nếu lấy được lock ngay.
- Trả `false` nếu một session/transaction khác đang giữ lock không tương thích.
- Khi transaction kết thúc bằng **commit hoặc rollback**, transaction-level advisory lock được tự giải phóng.
- Mọi replica phải dùng **cùng PostgreSQL database/lock key** để phối hợp.
- Advisory locks là **tự nguyện (advisory)**: code bỏ qua việc lấy khóa vẫn có thể sửa data, trừ khi database constraints/transactions chặn ở tầng khác.

### 8.1. Dùng transaction-level thay vì session-level như thế nào?

Với connection pool, session-level `pg_try_advisory_lock` cần được unlock có kỷ luật trên **đúng connection**; quên unlock hoặc trả connection vào pool khi đang giữ lock có thể gây giữ khóa lâu ngoài ý muốn.

`pg_try_advisory_xact_lock` gắn khóa với transaction, tự giải phóng khi transaction kết thúc. Điều kiện là **thao tác cần bảo vệ và lock acquisition nằm trong cùng transaction/connection**. Với Spring, `TransactionTemplate` hoặc `@Transactional` đúng boundary giúp bảo đảm điều đó.

**Không gọi `pg_try_advisory_xact_lock` ở một transaction đã kết thúc rồi thực hiện công việc nhiều phút sau**, vì lúc ấy lock đã được nhả. Cũng không lấy nó trên connection A rồi chạy nghiệp vụ ở connection B ngoài transaction mà nghĩ vẫn được bảo vệ.

### 8.2. Một transaction có khóa chưa đủ “một lần mỗi mốc”

Ví dụ hai node:

```text
10:00:00.000  A lấy advisory lock, xử lý 300ms, commit/nhả khóa
10:00:00.400  B lấy được cùng advisory lock, xử lý thêm lần nữa
```

Không overlap, nhưng đã **xử lý hai lần** trong cùng một business window. Vì thế nếu yêu cầu **mỗi khoảng 10 giây chỉ một lần**, cần thêm **business key unique**, ví dụ `(job_name, slot)`.

**Bài học ở đây:** distributed lock giúp điều phối **đang xử lý**; unique constraint/job store giúp ghi nhận **đã claim mốc nào**. Không có một mẫu nào thay thế mọi lớp còn lại.

## 9. Spring Boot 3.x runnable: hai replica dùng một PostgreSQL

Ứng dụng ví dụ dùng **Java 17+, Spring Boot 3.x / Spring Framework 6.x**, dependencies:

- `spring-boot-starter-web` để có endpoint kiểm tra.
- `spring-boot-starter-jdbc` để có `JdbcTemplate` và transaction manager.
- PostgreSQL JDBC driver (runtime).
- PostgreSQL database dùng chung cho **cả hai process**.

Ứng dụng có một job `@Scheduled(cron="*/10 * * * * *")`. Mỗi tick đều:

1. Mở database transaction bằng `TransactionTemplate`.
2. Thử lấy PostgreSQL transaction advisory lock cùng một key.
3. Nếu lock bận, **skip** tick (không đợi).
4. Nếu lấy được, tính `slot` từ **đồng hồ PostgreSQL** theo nhóm 10 giây.
5. Thử insert `(job_name, slot)` vào bảng có primary key: đã có nghĩa là **không làm lại mốc ấy**.
6. Nếu insert thành công, cập nhật một bộ đếm dữ liệu DB **trong cùng transaction**.
7. Chỉ sau transaction commit, ghi nhận instance đã hoàn thành lượt demo.

Đây là demo **DB-only side effect** để có atomicity rõ ràng. Nếu thay câu update bằng HTTP/email/payment, transaction DB **không tự rollback được thế giới bên ngoài**.

### 9.1. Schema khởi tạo một lần trên PostgreSQL

Tạo file **schema.sql** và chạy một lần trước khi khởi động hai replicas:

```sql
CREATE TABLE IF NOT EXISTS job_run_claims (
    job_name TEXT NOT NULL,
    slot BIGINT NOT NULL,
    PRIMARY KEY (job_name, slot)
);

CREATE TABLE IF NOT EXISTS job_state (
    job_name TEXT PRIMARY KEY,
    successful_runs BIGINT NOT NULL DEFAULT 0,
    last_run_at TIMESTAMPTZ
);

INSERT INTO job_state (job_name, successful_runs)
VALUES ('inventory_sweep', 0)
ON CONFLICT (job_name) DO NOTHING;
```

`job_run_claims(job_name, slot)` là **durable uniqueness barrier**. Nếu một transaction rollback, insert và state update đều rollback cùng nhau.

**Cảnh báo:** ứng dụng production nên triển khai migration bằng Flyway/Liquibase hoặc cơ chế migration phối hợp, không để mọi replica chạy schema migration tùy tiện cùng lúc trong lúc deploy.

### 9.2. Cấu hình kết nối

Trong **src/main/resources/application.properties**:

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/concurrency_demo}
spring.datasource.username=${DB_USER:demo}
spring.datasource.password=${DB_PASSWORD:demo}
server.port=${PORT:8080}
spring.application.name=cluster-lock-demo
```

Các biến `DB_URL`, `DB_USER`, `DB_PASSWORD` và `PORT` có thể được cấu hình theo môi trường. Giá trị `demo` chỉ là ví dụ local; không commit production credentials vào repository.

Tạo database `concurrency_demo` và user/permissions phù hợp rồi chạy schema:

```bash
psql "postgresql://demo:demo@localhost:5432/concurrency_demo" -f schema.sql
```

Lệnh `psql` là ví dụ local; dùng connection string được quản lý an toàn trong thực tế. Cả hai Spring processes phải trỏ tới **cùng một PostgreSQL logical database/schema**.

### 9.3. File Spring Boot Java đầy đủ

Đặt toàn bộ nội dung vào **src/main/java/com/example/demo/ClusterLockApplication.java**:

```java
package com.example.demo;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
public class ClusterLockApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClusterLockApplication.class, args);
    }

    @Bean
    TransactionTemplate transactionTemplate(
            PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }
}

enum TickOutcome {
    RECORDED, LOCK_BUSY, ALREADY_DONE
}

@Component
class InventorySweepJob {
    private static final Logger log =
            LoggerFactory.getLogger(InventorySweepJob.class);

    private static final String JOB_NAME = "inventory_sweep";
    private static final long LOCK_KEY = 802_280L;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String instanceId;

    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger recorded = new AtomicInteger();
    private final AtomicInteger skips = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();

    InventorySweepJob(
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            @Value("${INSTANCE_ID:local}") String instanceId) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.instanceId = instanceId;
    }

    @Scheduled(cron = "*/10 * * * * *", zone = "UTC")
    public void tick() {
        attempts.incrementAndGet();

        try {
            TickOutcome outcome = tx.execute(status -> {
                Boolean locked = jdbc.queryForObject(
                        "SELECT pg_try_advisory_xact_lock(?)",
                        Boolean.class, LOCK_KEY);

                if (!Boolean.TRUE.equals(locked)) {
                    return TickOutcome.LOCK_BUSY;
                }

                // Thời gian lấy từ DB chung, nhóm theo 10 giây.
                Long slot = jdbc.queryForObject(
                        """
                        SELECT floor(
                            extract(epoch from clock_timestamp()) / 10
                        )::bigint
                        """,
                        Long.class);

                int inserted = jdbc.update(
                        """
                        INSERT INTO job_run_claims(job_name, slot)
                        VALUES (?, ?)
                        ON CONFLICT (job_name, slot) DO NOTHING
                        """,
                        JOB_NAME, slot);

                if (inserted == 0) {
                    return TickOutcome.ALREADY_DONE;
                }

                // Business effect DEMO: cùng một DB transaction.
                int rows = jdbc.update(
                        """
                        UPDATE job_state
                           SET successful_runs = successful_runs + 1,
                               last_run_at = clock_timestamp()
                         WHERE job_name = ?
                        """,
                        JOB_NAME);

                if (rows != 1) {
                    throw new IllegalStateException(
                            "Missing job_state row");
                }

                return TickOutcome.RECORDED;
            });

            if (outcome == TickOutcome.RECORDED) {
                recorded.incrementAndGet();
            } else {
                skips.incrementAndGet();
            }

            log.info("instance={}, tickOutcome={}",
                    instanceId, outcome);
        } catch (RuntimeException error) {
            failures.incrementAndGet();
            log.error("instance={} tick failed", instanceId, error);
        }
    }

    public Map<String, Object> status() {
        Long durableCount = jdbc.queryForObject(
                """
                SELECT successful_runs FROM job_state
                 WHERE job_name = ?
                """,
                Long.class, JOB_NAME);

        return Map.of(
                "instanceId", instanceId,
                "attempts", attempts.get(),
                "recordedByThisInstance", recorded.get(),
                "skips", skips.get(),
                "failures", failures.get(),
                "durableCountInDatabase",
                    durableCount == null ? 0L : durableCount);
    }
}

@RestController
class ClusterJobController {
    private final InventorySweepJob job;

    ClusterJobController(InventorySweepJob job) {
        this.job = job;
    }

    @GetMapping("/jobs/status")
    public Map<String, Object> status() {
        return job.status();
    }
}
```

Đây là **file Java hoàn chỉnh với các imports/class cần thiết**, nhưng phải nằm trong Spring Boot project có những dependencies vừa liệt kê. Không thể compile bằng `javac` trần nếu không có Spring jars và JDBC driver.

### 9.4. Chạy hai instance

Ví dụ với Gradle wrapper. Tại hai terminal/shell khác nhau, cùng kết nối PostgreSQL:

**Terminal A:**

```bash
INSTANCE_ID=node-a PORT=8080 ./gradlew bootRun
```

**Terminal B:**

```bash
INSTANCE_ID=node-b PORT=8081 ./gradlew bootRun
```

Trên Windows PowerShell, đặt environment variables bằng `$env:INSTANCE_ID="node-a"` và `$env:PORT="8080"` trước khi chạy `.\gradlew.bat bootRun` trong terminal A; dùng `node-b`/`8081` ở terminal B. Không chạy hai lệnh `bootRun` đồng thời trong cùng shell rồi giả định biến môi trường tự tách biệt.

Kiểm tra:

```bash
curl -s http://localhost:8080/jobs/status
curl -s http://localhost:8081/jobs/status
```

Một response **minh họa**, không phải stdout cố định:

```json
{
  "instanceId": "node-a",
  "attempts": 6,
  "recordedByThisInstance": 4,
  "skips": 2,
  "failures": 0,
  "durableCountInDatabase": 6
}
```

Node B có thể có `recordedByThisInstance=2` và `skips=4` ở cùng một thời điểm. Các số sẽ thay đổi theo thời gian và scheduler jitter. Tác vụ có thể được ghi nhận bởi A hoặc B; **không có bảo đảm chia đều 50/50**.

Truy vấn để xem kết quả thật:

```sql
SELECT job_name, slot
FROM job_run_claims
WHERE job_name = 'inventory_sweep'
ORDER BY slot;

SELECT job_name, successful_runs, last_run_at
FROM job_state
WHERE job_name = 'inventory_sweep';
```

### 9.5. Vì sao ví dụ này giải được hơn một vấn đề?

- `pg_try_advisory_xact_lock` giới hạn **một transaction thực thi phần protected DB work tại cùng thời điểm** trên cùng database/lock key.
- `PRIMARY KEY(job_name, slot)` bảo vệ **không có hai row claim cho cùng business window**, kể cả node B lấy lock **sau khi** node A đã commit.
- `TransactionTemplate` giữ lấy advisory lock, insert claim và update business state trong **một transaction**. Nếu lỗi trước commit, thay đổi DB rollback.
- `clock_timestamp()` của PostgreSQL giúp lấy mốc từ **một nguồn thời gian chung** cho tất cả replicas trong demo. Nó không đảm bảo mốc cron của từng JVM đến chính xác cùng mili giây.
- Counter local đo node nào đã attempt/skipped; bảng DB là **nguồn sự thật bền vững** của số lần cập nhật.
- Nếu transaction throw lỗi, code ghi failure/log; không đánh dấu `RECORDED` trước khi transaction commit thành công.

**Giới hạn cố ý:** đây là job **DB-only**. Nó bảo vệ một transaction/unique key, không phải lời hứa “exactly-once email/payment”. `slot` tính theo 10 giây thời gian của PostgreSQL: một tick thực tế chạy trễ tới slot kế tiếp sẽ được coi là slot thời điểm **thực thi**, không nhất thiết “mốc cron dự kiến”. Với nghiệp vụ yêu cầu biết chính xác scheduled fire time, phải mang **logical scheduled occurrence ID** vào claim.

### 9.6. Nếu chỉ có advisory lock, tại sao vẫn cần bảng job_run_claims?

Giả sử A lấy lock xử lý xong trong 100ms và nhả, B vào sau 200ms. Hai lần không overlap nhưng vẫn cùng slot. Unique key loại được lần B. **Distributed lock và idempotency key xử lý hai vấn đề khác nhau**.

Nếu yêu cầu là “chỉ một worker xử lý tại một thời điểm, không quan tâm số lần”, advisory lock có thể đủ cho đoạn DB-only. Nếu yêu cầu là “mỗi logical slot chỉ một lần”, cần durable dedup/claim.

### 9.7. Đừng giữ DB transaction lâu để chờ HTTP/payment

Một transaction-level advisory lock giữ connection/transaction cho đến commit/rollback. Nếu trong transaction làm HTTP blocking 5 phút, nó giữ DB resources rất lâu và có rủi ro timeout/deadlock. Với side effect ngoài DB:

- Claim job và cập nhật state trong transaction ngắn.
- Dùng outbox/job store để worker tiêu thụ có kiểm soát.
- Dùng idempotency key với external provider.
- Lưu outcome/retry/reconciliation sau khi nhận kết quả.
- Nếu cần lease dài, thêm TTL/heartbeat/fencing policy theo scheduler/job store thích hợp.

**Không kéo dài transaction chỉ vì muốn giả lập một distributed lock cho mọi thứ.**

## 10. ShedLock: tích hợp @Scheduled nhưng không phải distributed scheduler đầy đủ

`ShedLock` là một thư viện bên thứ ba hỗ trợ dùng **shared lock provider** (chẳng hạn JDBC/Redis/Mongo) để bảo vệ những scheduled task được annotate. Khi một node đang giữ lock, các node khác thường **skip lượt** thay vì chờ lock.

Mã minh họa sau cần dependency `net.javacrumbs.shedlock`, cấu hình `LockProvider` trỏ đến **cùng shared store** ở các replicas, và annotation/scheduler setup đúng phiên bản:

```java
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class LockSchedulingConfig {
    // LockProvider bean dùng shared database/Redis cần được khai báo.
}

@Component
class ReconcileJob {
    @Scheduled(fixedDelayString = "PT1M")
    @SchedulerLock(
        name = "global_invoice_reconciliation",
        lockAtMostFor = "PT10M",
        lockAtLeastFor = "PT10S")
    public void reconcile() {
        reconciliationService.reconcilePending();
    }
}
```

Đây là **fragment kiến trúc**: chưa có `LockProvider`, service hoặc import đầy đủ và không thể chạy độc lập. Các property/annotation phải đối chiếu **version ShedLock đang sử dụng**. `@SchedulerLock` **không phải annotation có sẵn trong Spring Framework core**.

### 10.1. Ba điểm cần nhớ về ShedLock

1. **ShedLock là lock, không phải distributed scheduler/job queue.** Mỗi replica vẫn có cron/fixed-rate tick riêng; lock quyết định node nào được thực thi.
2. Lượt không lấy được lock thường **bị skip**, không tự xếp hàng chờ và không tự retry chính lượt đó.
3. `lockAtMostFor` là một giới hạn để lock không bị giữ vĩnh viễn khi node chết; nếu job vẫn đang chạy sau khi lease hết, **node khác có thể lấy lock và chạy trùng**.

Nếu hệ thống yêu cầu mọi lượt cron phải được xử lý, mô hình skip không đủ; nên dùng persistent job store/queue hoặc scheduler có chính sách recover.

### 10.2. Clock skew và TTL

Lock theo thời gian phụ thuộc đồng hồ/TTL và shared store implementation. Đặt `lockAtMostFor` thấp hơn thời gian xử lý thực tế có thể tạo overlap. Đặt quá lớn có thể trì hoãn failover khi node chết. Cần dùng số đo runtime tối đa, timeouts, khả năng extend/renew lease (nếu hỗ trợ), clock synchronization và alert.

Một `lockAtLeastFor` dài hơn period có thể chủ ý làm bỏ qua lượt để tránh chạy quá sát; không phải cơ chế exactly-once cho mọi business event.

### 10.3. AOP/proxy configuration

Nếu thư viện dùng method proxy/AOP mà job được gọi sai cách hoặc annotation không được áp dụng, lock có thể bị bỏ qua. Cần test hai instances dùng cùng store; không kết luận chỉ từ việc source có `@SchedulerLock`.

Với library hỗ trợ, có thể dùng `LockAssert.assertLocked()` trong method để kiểm tra lock đang được giữ khi thực thi. Nhưng test critical side effect vẫn phải độc lập và kiểm tra data-level duplication.

## 11. Redis distributed lock: khác JVM lock nhưng vẫn có failure modes

Một mẫu đơn Redis được nhiều người biết là:

```text
SET job:inventory_sweep <unique-owner-token> NX PX 30000
```

- `NX` yêu cầu chỉ tạo key nếu chưa tồn tại.
- `PX 30000` đặt expiry 30.000 ms để tránh khóa bị giữ mãi nếu owner chết.
- Value là **token owner ngẫu nhiên**, giúp giải phóng khóa có điều kiện: chỉ xóa nếu token trong key còn trùng owner, qua thao tác atomic/script phù hợp.
- Các node dùng **cùng Redis/key namespace** mới phối hợp được.

**Không dùng `DEL key` vô điều kiện khi unlock**: nếu lock của A đã hết hạn và B đã lấy lock mới, A xóa key sẽ vô tình phá lock của B. Cần compare-owner-and-delete atomically.

### 11.1. “Đang giữ lock” có thể trở thành “đã mất lock”

Tình huống:

```text
T0: A lấy Redis lease 30 giây.
T1: A bị stop-the-world GC pause / mất mạng >30 giây.
T2: Redis key hết hạn.
T3: B lấy lease mới và bắt đầu việc.
T4: A resume, vẫn tiếp tục ghi dù không còn là owner.
```

Ngay cả nếu release đúng token, **A đã có thể thực hiện side effect sau khi mất quyền**. Vì vậy lock lease chỉ là một phần điều phối. Với những tài nguyên yêu cầu an toàn mạnh, cần **fencing token** hoặc điều kiện ghi do protected resource kiểm tra, cộng với idempotency.

### 11.2. Redlock không phải phép màu exactly-once

Redis có tài liệu về thuật toán Redlock và phân tích consistency/clock assumptions. Một số đánh đổi/safety claims được tranh luận trong cộng đồng hệ phân tán. **Không tuyên bố mọi biến thể Redlock luôn đủ an toàn cho mọi loại payment/ledger**. Với dữ liệu cực kỳ nhạy cảm với duplicate, ưu tiên cơ chế transaction/unique constraint do source of truth thực thi, hoặc hệ thống coordination có guarantee được kiểm chứng và fencing ở đích.

### 11.3. Khi nào Redis lock có ích?

Có ích để giảm overlap tác vụ có thể retry/idempotent, cần coordination nhẹ và chấp nhận lease semantics. Không phù hợp làm **bằng chứng duy nhất** rằng một external effect xảy ra đúng một lần nếu tác vụ có thể mất lease và tiếp tục chạy.

## 12. Fencing token: chống owner cũ ghi sau khi hết lease

Distributed locks dựa trên thời hạn có nguy cơ **stale owner**. Một chiến lược là phát **fencing token tăng đơn điệu** mỗi khi cấp quyền mới:

```text
A lấy lease: token = 41
A bị pause lâu, lease hết hạn.
B lấy lease: token = 42
B ghi vào protected resource với 42.
A thức dậy, cố ghi với 41.
Resource kiểm tra token cũ < token đã chấp nhận -> từ chối.
```

Điều kiện thiết yếu là **protected resource thực sự kiểm tra token**. Chỉ đính kèm `token` trong log mà không có enforcement ở nơi ghi thì không tạo an toàn.

Một ý tưởng DB-level, nếu resource lưu `last_fence`:

```sql
UPDATE protected_resource
   SET payload = :new_payload,
       last_fence = :new_fence
 WHERE id = :resource_id
   AND last_fence < :new_fence;
```

Đây là **mẫu điều kiện minh họa**, không phải câu SQL chạy nguyên xi nếu chưa bind parameters/create schema. Cần xử lý affected-row count, transaction isolation và contract cấp fencing token. **Không đủ** chỉ tạo số token từ clock wall time hoặc UUID ngẫu nhiên: fencing token cần ordering đáng tin theo từng resource/lock epoch.

### 12.1. Fencing và idempotency khác nhau

- **Fencing:** chặn một owner/attempt đã mất quyền ghi đè state do owner có token mới hơn.
- **Idempotency:** nếu cùng một logical action được thử lại, kết quả quan sát được không bị nhân đôi.

Đối với payment/email, external provider phải hỗ trợ idempotency key hoặc workflow reconciliation; một fencing token trong database không thể undo email đã gửi.

### 12.2. Một race cần phân tích

Nếu A còn token cũ 41 nhưng B mới lấy token 42 mà **chưa ghi** vào resource, A có thể tới resource trước B. Việc kiểm tra monotonic last-fence tại resource làm cho khi B ghi sau, B thắng về version, nhưng không tự bảo đảm mọi intermediate external side effect bị ngăn trước đó. Vì thế fencing không thay thiết kế transaction/command preconditions và domain-level idempotency.

## 13. Kubernetes Lease và leader election

Kubernetes có tài nguyên `Lease` trong API group `coordination.k8s.io`; nhiều hệ thống dùng nó để điều phối leader giữa replicas. Khi một leader nắm lease hợp lệ, nó có thể được chỉ định thực hiện scheduled work, còn followers không làm.

```text
Pod A ----- acquire/renew ----> Kubernetes Lease
Pod B ----- observe/compete ---> Kubernetes Lease
Pod C ----- observe/compete ---> Kubernetes Lease

Leader active -> chạy logic
Followers      -> chờ/không xử lý
```

Nhưng cần phân biệt:

- **Leader election là một cơ chế bổ sung**, không tự bật chỉ vì Spring Boot đang chạy trong Kubernetes.
- Cần RBAC để đọc/ghi Lease, identity, renew deadline và reaction khi mất leadership.
- Node cũ có thể bị pause/partition và không nhận tín hiệu mất leader kịp, nên **stale leader** vẫn là vấn đề nếu công việc có external side effect.
- Kubernetes `CronJob` là một **controller/job resource khác**; không đồng nhất với `@Scheduled` nội bộ Spring. CronJob có chính sách concurrency như `Forbid` hoặc `Replace` cho các Jobs do **cùng CronJob** tạo, nhưng cũng cần xem các trường hợp missed schedules, duplicate jobs và idempotency theo docs/version.

Nếu ứng dụng cần xử lý **danh sách công việc bền vững**, một queue/DB job store thường phù hợp hơn việc bắt một leader chạy tất cả mọi tác vụ rồi tự duy trì hàng đợi trong RAM.

## 14. Database row claiming: đôi khi tốt hơn khóa toàn cục

Thay vì mỗi replica cố lấy một global job lock, có thể để các workers **claim những dòng pending khác nhau** bằng database transaction. Với PostgreSQL, một mẫu thường sử dụng `FOR UPDATE SKIP LOCKED` trong transaction phù hợp để hai workers tránh lấy cùng row tại cùng lúc.

Ví dụ ý tưởng:

```sql
BEGIN;

SELECT id
FROM jobs
WHERE status = 'PENDING'
  AND available_at <= clock_timestamp()
ORDER BY available_at, id
FOR UPDATE SKIP LOCKED
LIMIT 20;

-- Claim hoặc xử lý những id đã khóa trong transaction.
-- Trong production, nên có owner, state, retry/deadline.

COMMIT;
```

Đây là **phác thảo SQL**: bảng `jobs` phải tồn tại, thao tác claim/update chưa được cung cấp trong đoạn này. Nó **không** bảo đảm exactly-once side effect nếu worker commit claim rồi crash trước khi hoàn tất, hoặc nếu worker gửi remote request trước commit và bị rollback.

### 14.1. Cách tổ chức durable job state

Một job record có thể gồm:

| Cột | Vai trò |
| --- | --- |
| `job_id` | Logical work identity/idempotency key |
| `status` | PENDING, CLAIMED, SUCCEEDED, FAILED, v.v. |
| `owner_id` | Instance đang sở hữu claim |
| `lease_until` | Deadline cho retry/failover |
| `attempts` | Số attempt/retry |
| `next_attempt_at` | Thời điểm đủ điều kiện tiếp tục |
| `last_error` | Nguyên nhân thất bại |
| `completed_at` | Mốc thành công được ghi bền vững |
| `version`/`fencing_token` | Phòng stale owner cập nhật sai |

Một worker chỉ claim bằng điều kiện update atomic; nếu không còn giữ owner/version hợp lệ, phải dừng hoặc bị database từ chối ghi trạng thái. Cần retry policy, timeout và idempotency khi downstream không rõ thành công hay chưa.

### 14.2. Chọn đúng granularity

Một lock cho toàn job `inventory_sweep` chỉ cho một worker làm mọi thứ, có thể thành bottleneck. Claim theo rows/account partitions giúp scale ngang và tránh overlap **trên từng record**, không phải toàn hệ thống.

Nếu điều kiện nghiệp vụ cho phép parallel work khác account, đây thường là mô hình thích hợp hơn một global mutex. Nhưng phải xử lý contention, hot keys, fair scheduling và stale leases.

## 15. @Transactional không tự thành distributed lock

Một method:

```java
@Scheduled(fixedDelay = 1000)
@Transactional
public void scan() {
    var pending = repository.findPending();
    pending.forEach(this::sendPayment);
}
```

Đây là **phản ví dụ thiết kế**: chưa có repository/transaction boundary đầy đủ. Ngay cả khi `@Transactional` có hiệu lực qua proxy trên scheduled invocation, nó chỉ tạo transaction với isolation/DB access được cấu hình. **Không tự tạo khóa toàn cluster cho toàn bộ logical job**; hai transactions có thể đọc cùng những records đủ điều kiện và thực hiện side effect trước khi phát hiện conflict.

Cần xác định cơ chế thực sự:

- Optimistic locking với `@Version` và kiểm tra affected rows/version.
- Pessimistic row locks (`SELECT ... FOR UPDATE`, JPA locking modes) trong transaction.
- Unique constraint trên business key.
- Atomic claim update với owner/version.
- Transaction-level advisory lock chỉ khi cùng DB và cùng key.
- Idempotency/outbox khi có external side effect.

**Đặc biệt:** gọi một `@Transactional` method từ `this` trong proxy mode có thể bypass transaction advice. Khi dùng PostgreSQL transaction advisory lock, phải bảo đảm có **Spring transaction thực sự bắt đầu**, không chỉ tin annotation xuất hiện trong source.

## 16. Local lock + distributed lock: thứ tự và thời hạn bảo vệ

Có thể dùng cả hai:

```text
Scheduled tick
   |
   +-- local tryLock (tránh hai callbacks cùng instance)
   |
   +-- acquire shared lock/claim (điều phối mọi replicas)
   |
   +-- xử lý DB rows đã claim + ghi durable outcome
   |
   +-- release shared lock / commit transaction
   |
   +-- local unlock
```

Nhưng phải trả lời câu hỏi: **business work thực sự nằm trong khoảng nào?**

Nếu code:

```java
if (localLock.tryLock()) {
    try {
        CompletableFuture.runAsync(this::doPayment);
    } finally {
        localLock.unlock();
    }
}
```

đây là **phản ví dụ**. Local lock được nhả sau khi submit, không phải sau khi payment hoàn tất. Nếu shared lease cũng bị nhả lúc đó, hai instances có thể chạy payment trùng nhau. Đừng cập nhật trạng thái `SUCCEEDED` khi chỉ mới submit.

Nếu muốn bảo vệ một future thực, thiết kế owner/lease/pipeline để thời điểm lease kết thúc gắn với **kết quả nghiệp vụ**, có timeouts, cancellation, renewal/fencing và durable state. Hoặc đẩy từng work item vào queue có idempotency.

## 17. Testing: unit test một JVM không đủ

### 17.1. Ba cấp kiểm thử

| Cấp | Mục tiêu | Công cụ gợi ý |
| --- | --- | --- |
| Unit test cùng JVM | Race giữa threads, local guard, finally-unlock | JUnit + executor + latch |
| Integration test 2 process dùng chung store | Cả hai tranh cùng business key/lock | Hai JVM/containers + PostgreSQL/Redis test instance |
| Failure injection | Crash, lease expiry, network partition, stale owner | Fault injection, kill process, pause, timeouts và kiểm tra DB |

`ProcessLocalLockDemo.java` kiểm tra ranh giới JVM bằng hai subprocess. `ClusterLockApplication` là cấu hình thực để thử hai Spring processes dùng chung database. **Không thể suy ra từ unit test một JVM rằng toàn cluster an toàn.**

### 17.2. Kiểm thử scenario “thắng khóa nhưng chưa commit”

Cho node A vào transaction, lấy advisory lock rồi **chặn bằng latch/test hook trước commit**. Node B thử cùng key: nhận false và không cập nhật DB. Sau khi A commit, B thử lại trong cùng slot: unique constraint khiến B nhận `ALREADY_DONE`. Điều đó kiểm tra cả hai lớp **mutual exclusion** và **dedup per slot**.

Trong test cần resource chung thật và cơ chế điều phối đáng tin, không dựa vào `Thread.sleep(100)` rồi kết luận chắc hai process overlap.

### 17.3. Kiểm thử exception và rollback

Sau khi insert claim nhưng trước update business state, ép một RuntimeException để transaction rollback. Sau đó kiểm tra:

- Claim của slot đó **không được commit**.
- Business counter **không tăng**.
- Transaction-level advisory lock được giải phóng.
- Một retry đúng key có thể thực hiện lại thành công.

Nếu có external side effect trước exception, DB rollback **không chứng minh** external effect chưa xảy ra; cần mock/provider idempotency/reconciliation để test riêng.

### 17.4. Kiểm thử crash và lease expiry

Với store có TTL, mô phỏng A lấy lease rồi bị pause vượt TTL; B lấy lease mới; A resume. Kiểm tra đích ghi có token/version guard và A không thể commit state sai sau B. Đây là bài test quan trọng hơn “hai worker tranh lock bình thường”.

Với PostgreSQL transaction advisory lock, nếu client connection chết làm transaction kết thúc, lock được giải phóng theo DB semantics. Tuy nhiên, nếu service bên ngoài đã nhận request trước khi connection chết, hậu quả có thể tiếp tục dù DB lock không còn.

### 17.5. Đo những kết quả nào?

- Không có hai claim/row cùng unique business key.
- Không có hai workers đang ở protected critical section khi store còn giữ lock hợp lệ.
- Mỗi logical slot có trạng thái outcome theo thiết kế.
- Một owner cũ không ghi đè resource sau khi mất quyền.
- Work còn pending được recover sau crash nếu đó là SLO.
- Không có duplicate external side effect, hoặc có reconciliation khi không thể loại trừ hoàn toàn.

## 18. Monitoring: đo duplicate và skipped lock đúng cách

Một dashboard nên có:

| Metric | Ý nghĩa |
| --- | --- |
| `job_tick_total` | Số tick scheduler đã kích hoạt theo instance |
| `lock_attempt_total` | Số lần thử shared lock |
| `lock_acquired_total` | Số lần lấy lock thành công |
| `lock_contended_total` | Lượt phải skip do owner khác giữ |
| `job_claim_conflict_total` | Lượt cùng business key đã được claim |
| `job_success_total`/`job_failure_total` | Outcome thực tế |
| `job_last_success_at` | Thời điểm outcome thành công được xác nhận |
| `lease_renewal_failure_total` | Tín hiệu có thể mất quyền trong lease-based design |
| `stale_owner_write_rejected_total` | Fencing/owner version ngăn ghi cũ |
| `job_pending_age` | Mức độ backlog chưa được xử lý |

**Không pager chỉ vì follower skipped lock** nếu bỏ qua là hành vi đúng. Cần alert khi **không có owner thành công trong thời gian SLO**, khi lock held quá lâu, claim backlog tăng, hoặc một stale owner vẫn cố ghi.

Với PostgreSQL ví dụ, `recordedByThisInstance` chỉ là counter cục bộ. `durableCountInDatabase` và `job_run_claims` mới biểu thị dữ liệu chung. Một replica restart sẽ reset counters local, không làm mất claims đã commit.

### 18.1. Correlation và instance identity

Log nên có `jobName`, `slot/jobId`, `instanceId`, `attemptId`, `lockKey` hoặc hash an toàn, `lockOutcome`, `duration`, `businessOutcome`. Không đưa secret/token Redis lock thô vào logs. Cần phân biệt **job identity** và **attempt identity** để không ghép nhầm attempts từ hai node thành một lần hoàn thành.

### 18.2. Clock và cluster

Nếu dùng leases theo TTL, clock skew và đổi giờ có thể tác động. Dùng clock của shared store nếu API hỗ trợ hoặc thiết kế có tolerance; với cron lịch nghiệp vụ, cấu hình `zone` rõ ràng. Đồng bộ NTP giúp giảm lệch nhưng không thay thế transaction/unique constraint/fencing.

## 19. Những sai lầm dễ mắc

**Lỗi 1.** Cho rằng `synchronized` trên Spring singleton bean là distributed mutex. Singleton chỉ nằm trong container/JVM tương ứng.

**Lỗi 2.** Dùng `static synchronized` và nghĩ `static` chia sẻ giữa máy. Mỗi JVM có `Class` object của riêng nó.

**Lỗi 3.** Dùng `ReentrantLock`/`AtomicBoolean` rồi scale pods, không bổ sung coordination shared.

**Lỗi 4.** Cho rằng `@Scheduled(fixedDelay)` ngăn trùng toàn cluster. Nó chỉ áp dụng scheduling trong từng context.

**Lỗi 5.** Đặt scheduler pool size 1 rồi nghĩ chỉ một worker toàn deployment. Mỗi replica vẫn có pool riêng.

**Lỗi 6.** Tin cấu hình replicas=1 tuyệt đối không có hai processes khi rolling update/failover.

**Lỗi 7.** Dùng distributed lock nhưng mọi node không trỏ cùng store/namespace hoặc dùng lock key khác nhau.

**Lỗi 8.** Chọn key khóa theo instance ID thay vì logical job/resource ID.

**Lỗi 9.** Chỉ khóa khi bắt đầu rồi nhả trước khi async future thật hoàn tất.

**Lỗi 10.** Dùng advisory transaction lock nhưng công việc chạy ngoài transaction hoặc connection khác.

**Lỗi 11.** Dùng Redis `DEL` vô điều kiện khi unlock: có thể xóa lock của owner mới sau expiry.

**Lỗi 12.** Giả định lease không bao giờ hết khi owner còn sống. GC pause, network partition và timeout vẫn xảy ra.

**Lỗi 13.** Dùng TTL ngắn hơn thời gian chạy tối đa, tạo hai concurrent owners.

**Lỗi 14.** Coi distributed lock là exactly-once delivery hoặc exactly-once side effect.

**Lỗi 15.** Chỉ dùng mutex, bỏ qua unique business key: hai nodes xử lý nối tiếp vẫn có thể duplicate.

**Lỗi 16.** Dùng unique key không đúng granularity, ví dụ khóa theo ngày trong khi business event xảy ra nhiều lần/ngày hợp lệ.

**Lỗi 17.** Đánh dấu job success trước khi transaction commit hoặc remote provider xác nhận.

**Lỗi 18.** Dùng `@Transactional` nhưng không có row lock/claim/unique key phù hợp.

**Lỗi 19.** Để `@Transactional` self-invocation bypass proxy rồi tưởng advisory lock chạy trong transaction.

**Lỗi 20.** Giữ DB transaction lâu trong lúc chờ remote HTTP/payment.

**Lỗi 21.** Không xử lý stale owner khi lease chuyển sang node mới.

**Lỗi 22.** Dùng fencing token nhưng protected resource không kiểm tra token.

**Lỗi 23.** Không định nghĩa retry/recovery sau crash: skip lock vô hạn làm mất job logic.

**Lỗi 24.** Chỉ test hai threads cùng JVM, không chạy thử hai process dùng shared store.

**Lỗi 25.** Gắn alert vào mọi lock skip dù skip là behavior bình thường; bỏ sót alert cho no-success/backlog.

## 20. Checklist chọn giải pháp

- [ ] Xác định business invariant: **không overlap**, **một lần mỗi slot**, **at least once**, hay **effectively once**.
- [ ] Xác định scope: một JVM, mỗi account, một job toàn cluster, một tenant hoặc một event.
- [ ] Biết chắc mỗi replica có scheduler/context riêng; `@Scheduled` không tự phối hợp cluster.
- [ ] Chỉ sử dụng local `synchronized`/`ReentrantLock` cho state thực sự trong JVM.
- [ ] Nếu cần shared exclusion, chọn DB advisory lock, Redis/ShedLock, leader lease hoặc job store tùy guarantee.
- [ ] Mọi instance dùng **cùng coordination store/namespace/lock key** cho cùng logical resource.
- [ ] Với DB transaction lock, acquisition và protected DB writes nằm trong **cùng transaction/connection**.
- [ ] Có unique/idempotency business key nếu một effect không được thực hiện lại trong cùng logical window.
- [ ] Có timeout/lease expiry, renewal/fencing policy và phản ứng khi mất ownership.
- [ ] Nếu có external side effect, phối hợp idempotency key, outbox hoặc reconciliation thay vì chỉ lock.
- [ ] Không nhầm skip lock với business success; có durable outcome và metrics.
- [ ] Nếu requirement cần replay sau crash, dùng durable job store/queue, không chỉ in-memory scheduler.
- [ ] Integration test với **hai JVMs/pods** và shared store, plus failure injection.
- [ ] Kiểm tra rolling updates, failover, stale owners, dropped responses, clock skew và lock expiry.
- [ ] Giám sát contended/skipped, last success, backlog, duplicate prevention và lease failures.

## 21. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `synchronized` bảo vệ tốt trong một JVM nhưng không bảo vệ được hai Spring Boot replicas?

**Đáp án:** Vì synchronized lấy monitor của một Java object trong một JVM. Hai replicas có hai heaps/monitors riêng, nên cả hai cùng có thể lấy monitor cục bộ.

**Câu 2.** Dùng `private static final ReentrantLock LOCK` có làm nó global trên cluster không?

**Đáp án:** Không. `static` không chia sẻ state xuyên tiến trình. Mỗi JVM có Class/static fields riêng, thậm chí classloader có thể tạo bản sao trong một JVM.

**Câu 3.** `@Scheduled(fixedDelay=5000)` trên ba pods có chắc chỉ một pod xử lý không?

**Đáp án:** Không. Mỗi ApplicationContext đăng ký schedule riêng; fixed delay không phải distributed lock.

**Câu 4.** Nếu muốn chỉ một worker **đang xử lý** cùng key tại một thời điểm, nên dùng gì?

**Đáp án:** Dùng cơ chế shared coordination đúng phạm vi: DB lock/atomic claim, distributed lease/lock, leader election hoặc scheduler/job store phù hợp; mọi instances phải cùng dùng key và store.

**Câu 5.** Distributed lock có ngăn A xử lý xong, nhả lock, rồi B chạy lại cùng job slot không?

**Đáp án:** Không tự động. Đó là hai lần chạy nối tiếp; cần durable unique/idempotency key theo logical occurrence để ngăn duplicate.

**Câu 6.** PostgreSQL `pg_try_advisory_xact_lock` có đặc điểm gì?

**Đáp án:** Thử lấy advisory exclusive lock cho một transaction, trả true/false, và tự giải phóng khi transaction kết thúc. Code phải giữ transaction thật trong toàn phạm vi cần bảo vệ.

**Câu 7.** Vì sao ví dụ Spring Boot còn dùng `PRIMARY KEY(job_name, slot)`?

**Đáp án:** Để không có hai lần claim cùng business window, kể cả sau khi lock đã được nhả và replica khác lấy được lock.

**Câu 8.** Dùng `@SchedulerLock` của ShedLock thì mọi lịch đều được lưu và replay sau crash?

**Đáp án:** Không. ShedLock là distributed locking integration, không phải durable scheduler/queue; lượt không lấy lock có thể bị skip.

**Câu 9.** Một Redis lock 30 giây hết hạn trong lúc A bị GC pause, B lấy lock mới. A tỉnh dậy có còn được phép ghi an toàn không?

**Đáp án:** Không được giả định. A có thể là stale owner; cần fencing/owner checks tại resource và idempotency.

**Câu 10.** Một fencing token được in trong log nhưng database/external API không kiểm tra token có giúp chặn stale owner không?

**Đáp án:** Không. Protected resource phải enforce token/version order hoặc preconditions phù hợp.

**Câu 11.** `@Transactional` trên method scheduled có tự tạo distributed mutex cho toàn job không?

**Đáp án:** Không. Transaction boundary chỉ cho hiệu quả kiểm soát tương ứng với DB operations/locks/constraints được cấu hình; hai transactions vẫn có thể cùng đọc và gây side effect.

**Câu 12.** Dùng unit test với hai threads cùng JVM có đủ để khẳng định an toàn trên hai Kubernetes pods?

**Đáp án:** Không. Cần test hai processes dùng shared store, vì các object monitors là độc lập giữa JVMs.

**Câu 13.** Nếu một job chỉ submit `CompletableFuture` rồi nhả shared lock, điều gì có thể sai?

**Đáp án:** Lock đã được nhả trước khi công việc thật kết thúc; replica khác có thể bắt đầu cùng business operation. Cần ownership/lifecycle của actual work hoặc queue/claim.

**Câu 14.** Nên chọn global lock hay per-account claim cho hàng triệu accounts độc lập?

**Đáp án:** Nếu nghiệp vụ cho phép song song giữa accounts, per-account/row claim thường tốt hơn về throughput; global lock dễ thành bottleneck. Vẫn phải bảo vệ uniqueness theo từng business key.

**Câu 15.** Tại sao distributed job vẫn cần idempotency khi đã có lease/leader election?

**Đáp án:** Vì crash, retry, timeout, lease expiry, stale owner hoặc unknown remote outcomes có thể tạo duplicate attempts. Idempotency kiểm soát effect theo logical operation.

## Kết luận

**`synchronized`, `ReentrantLock`, `AtomicBoolean`, `Semaphore` và Spring singleton chỉ điều phối phạm vi JVM/context, không tự trở thành khóa trên nhiều máy.** Khi Spring Boot được triển khai nhiều replicas, từng process có scheduler, heap và lock riêng; mỗi `@Scheduled` job có thể chạy gần như cùng lúc.

Muốn bảo vệ nghiệp vụ phân tán, hãy:

1. Xác định **resource và invariant** chính xác: không overlap hay không duplicate effect.
2. Sử dụng **shared coordination** khi cần, ví dụ PostgreSQL advisory transaction lock, atomic DB claim, Redis/ShedLock hoặc leader Lease.
3. Bổ sung **durable unique business key/idempotency** nếu cùng một logical action không được thực hiện nhiều lần.
4. Tính đến **lease expiry, stale owner, fencing, failure recovery** và side effect bên ngoài.
5. **Test ít nhất hai JVMs** cùng shared store, không chỉ hai threads trong một JVM.

**Khóa cục bộ vẫn đúng, nhưng nó chỉ trả lời câu hỏi cục bộ. Sự đúng đắn toàn cluster phải được thực thi tại một ranh giới chia sẻ phù hợp với dữ liệu và nghiệp vụ.**

## Tài liệu tham khảo

1. Java SE 17 Language Specification — `synchronized`, monitors và object/class lock: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
2. Java SE 17 — `ReentrantLock`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html
3. Spring Framework Reference — `@Scheduled`, `@EnableScheduling`, scheduling lifecycle: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
4. PostgreSQL 17 — Advisory locking functions: https://www.postgresql.org/docs/17/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS
5. PostgreSQL 17 — Explicit locking, advisory lock considerations: https://www.postgresql.org/docs/17/explicit-locking.html#ADVISORY-LOCKS
6. Spring Framework Reference — Programmatic Transaction Management / `TransactionTemplate`: https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html
7. ShedLock — shared LockProvider, `@SchedulerLock` và giới hạn về lease: https://github.com/lukas-krecan/ShedLock
8. Redis — Distributed Locks, owner tokens và consistency: https://redis.io/docs/latest/develop/use/patterns/distributed-locks/
9. Kubernetes — Lease objects và leader election: https://kubernetes.io/docs/concepts/architecture/leases/
10. Spring Boot Reference — JDBC/SQL database và transaction support: https://docs.spring.io/spring-boot/reference/data/sql.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="distributed-limits-003">Chọn một nơi kích hoạt hoặc cơ chế phối hợp có phạm vi phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn một nơi kích hoạt hoặc cơ chế phối hợp có phạm vi phù hợp

## 1. Tại sao cần chọn đúng nơi kích hoạt trong Spring chạy phân tán?

Một phương thức Spring gắn `@Scheduled` được đăng ký bởi scheduling infrastructure của **ApplicationContext nơi bean tồn tại**. Khi triển khai nhiều pod, mỗi pod thường có ApplicationContext riêng. Nếu cùng class được tải trên ba pod, **cả ba có thể tự kích hoạt cùng một mốc cron**.

```java
@Component
public class DailyClosingJob {
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void closeDailyPeriod() {
        closingService.closeDuePeriod();
    }
}
```

Đây là code tích hợp cần bean `closingService`, imports `@Component`/`@Scheduled` và `@EnableScheduling` hoặc infrastructure scheduling tương đương. Câu lệnh này không có thuộc tính ngầm kiểu “execute on exactly one pod”.

```text
                         02:00 UTC
                            |
             +--------------+--------------+
             |              |              |
          Spring A       Spring B       Spring C
          @Scheduled     @Scheduled     @Scheduled
             |              |              |
             v              v              v
          close()         close()         close()
             |              |              |
             +----- có thể tạo ba tác động trùng ----+
```

Nếu công việc là dọn một cache **cục bộ của từng pod**, cả ba cùng chạy có thể hoàn toàn đúng. Nếu công việc là phát hóa đơn cho toàn công ty, ba lần chạy là rủi ro. **Không có một cơ chế phối hợp duy nhất phù hợp cho mọi loại job.**

Hai hướng giải quyết chính:

1. **Chọn một nơi sở hữu trigger**: chỉ một thành phần có trách nhiệm phát sự kiện đúng mốc lịch, còn workers xử lý những sự kiện đó.
2. **Cho nhiều nơi phát tick nhưng phối hợp claim/quyền xử lý**: tất cả có thể nhìn thấy cron, nhưng chỉ một chủ thể được nhận một logical execution theo phạm vi được chỉ định.

Điểm quan trọng hơn cả lựa chọn công cụ là định nghĩa **phạm vi**: một lần trong JVM, một lần trên cụm, một lần theo tenant, theo partition, theo business window hay một side effect duy nhất toàn hệ thống.

**Mục tiêu học tập:** phân biệt các loại “một lần”; chọn đúng phạm vi phối hợp; hiểu leader, ShedLock, Quartz JDBC clustering và durable job queue; sử dụng claim/lease/fencing/idempotency; chạy một ví dụ Java 17 chứng minh khóa local không giải quyết cạnh tranh nhiều node; lập kiểm thử crash, restart, failover và bảo vệ tài nguyên downstream.

## 2. Bốn hợp đồng “chạy một lần” khác nhau

| Hợp đồng | Điều thật sự được bảo vệ | Ví dụ |
| --- | --- | --- |
| Một lịch đăng ký trong JVM | Một `ScheduledFuture`/một owner local | Dọn cache của chính pod |
| Tối đa một execution **đồng thời** trên cluster | Không có hai workers giữ quyền chạy cùng lúc, khi lease còn hiệu lực | Reconcile account |
| Một lần claim cho một logical window | Một `(jobId, dueAt)` chỉ có một owner hợp lệ tại thời điểm claim | Báo cáo ngày 1/11 |
| Một business side effect duy nhất | Không gửi hóa đơn/chuyển tiền hai lần ngay cả khi retry | Phát hành invoice |

Các hợp đồng không tương đương:

- Một `synchronized` hoặc `AtomicBoolean` local chỉ bảo vệ các threads truy cập **cùng một object** trong JVM.
- Một distributed lock có thể ngăn overlap **trong lúc lease còn hạn**, nhưng sau khi khóa được nhả một instance khác vẫn có thể xử lý lại cùng business window.
- Một leader duy nhất tại từng thời điểm không bảo đảm hệ thống sẽ không có **leader cũ vẫn đang xử lý** khi lease hết hạn.
- Một unique constraint bảo vệ hàng dữ liệu trùng nhưng không tự bảo đảm worker sẽ hoàn tất nếu nó crash.
- Một `ScheduledFuture.cancel(false)` chỉ hủy lịch của scheduler sở hữu handle ấy, không tự ngắt remote HTTP hoặc hủy lịch trên pod khác.

Với tác động nghiệp vụ quan trọng, cần xác định **idempotency key** và nơi commit dữ liệu độc lập với cách phát tick.

## 3. Xác định phạm vi phối hợp trước khi chọn lock

| Phạm vi | Mục tiêu | Cơ chế phù hợp |
| --- | --- | --- |
| Thread/JVM | Không cho hai lời gọi cùng object chạy chồng | `AtomicBoolean`, `Semaphore`, lock |
| Một Spring context | Đăng ký một lịch trên một bean | Một scheduler owner/registry local |
| Một host | Nhiều processes cùng sửa file local | OS/file locking với lifecycle rõ |
| Một Kubernetes cluster | Một job cho nhiều pods | Một scheduler owner, lease hoặc shared claim |
| Một tenant | Mỗi tenant chạy độc lập, không được nhân đôi riêng tenant | Key theo `tenantId + jobId + window` |
| Một shard/partition | Một owner cho mỗi partition, nhiều partitions chạy song song | Partition ownership/consumer group |
| Nhiều clusters/regions | Không chạy trùng khi active-active | Coordination thật sự chung hoặc phân vùng ownership |
| Tại nơi tác động nghiệp vụ | Mỗi hóa đơn chỉ phát hành một lần | Unique business key, transaction, idempotency |

**Khóa quá hẹp** không ngăn pod khác chạy cùng việc. **Khóa quá rộng** lại chặn các công việc đáng lẽ được chạy song song.

Ví dụ:

```text
SAI nếu nhiều tenants độc lập:
  lockName = "daily-report"

HỢP LÝ hơn khi mỗi tenant có lịch riêng:
  lockName = "daily-report/<tenantId>"

Nếu cần phân biệt mỗi business window:
  executionKey = (tenantId, reportDate, reportType)
```

Không mặc định bỏ thêm `reportDate` vào *lock name* khi muốn tránh cùng job chạy đồng thời qua nhiều kỳ: hãy tách **lock key bảo vệ overlap** khỏi **execution key bảo vệ trùng của một kỳ**. Chọn key theo chính xác hợp đồng nghiệp vụ và retention.

Khi chạy hai vùng active-active, một lock Redis độc lập ở từng region **không** bảo đảm một quyền chạy toàn cầu. Nếu dùng ownership theo vùng, cần có quy tắc chuyển quyền để vùng cũ không tiếp tục ghi sau failover.

## 4. Tách ba tầng: trigger, claim, business execution

```text
Cron/Deadline/Configuration
           |
           v
    TẦNG TRIGGER
  phát "job due" hoặc
  tạo durable job record
           |
           v
     TẦNG CLAIM
  chọn một owner hợp lệ
  cho job/window
       /       \
    winner    loser
      |         |
      v         +--> skip, wait, retry hoặc queue
 TẦNG BUSINESS
      |
      v
Transaction / HTTP / email
      |
      v
  SUCCEEDED / FAILED / RETRY
```

Một trigger chỉ đưa ra **ý định** công việc tới hạn. Một claim chỉ nói **ai đang có quyền** theo coordinator. Một business commit mới xác định tác động được chấp nhận.

**Một trigger owner vẫn có thể phát trùng** do restart, retry sau timeout, nhiều bản cấu hình hoặc split brain. **Một shared lock vẫn có thể mất việc** khi tick bị skip và không được lưu. Do đó cơ chế dùng để giải quyết “không chạy đồng thời” chưa chắc giải quyết “không bỏ lỡ một kỳ”.

Phải biết công việc nào được phép bỏ tick:

- Refresh cache: bỏ một lần thường có thể chấp nhận, kỳ sau thử lại.
- Gửi một bản báo cáo bắt buộc: không thể im lặng bỏ kỳ ngày hôm qua.
- Thanh toán/gửi invoice: cần khôi phục và idempotency ở nghiệp vụ, không chỉ lịch.

## 5. Bảng quyết định: một chủ trigger hay nhiều pod phối hợp?

| Nhu cầu | Cách tiếp cận thường hợp lý | Điểm phải quản lý |
| --- | --- | --- |
| Cleanup local trên mọi pod | `@Scheduled` tại tất cả pods | Không cần distributed lock |
| Một job chung, bỏ tick được | Một scheduler pod hoặc `@Scheduled` + ShedLock | Owner chết, lock expiry, skip |
| Job chung cần HA/failover | Active-passive leader hoặc shared lease | Split brain, token, lost tick |
| Cron động, nhiều lịch, lưu qua restart | Quartz JDBC clustering/durable scheduler | Job store, misfire, instance IDs |
| Mỗi người dùng có một deadline | Durable job table/queue + consumers | Claim, retry, idempotency, retention |
| Một job theo từng tenant | Partitioned keys/leases | Key granularity, quota per tenant |
| Side effect không được nhân đôi | Unique business key + transaction/outbox/idempotency | Crash sau external API |
| Hai regions có quyền chạy cùng job | Ownership chung hoặc globally consistent coordinator | Consistency, latency, failure partition |

Không chọn giải pháp chỉ theo “ít dòng code”. Nếu mất một tick gây thiệt hại, single-owner scheduler chỉ sống trong RAM có thể chưa đủ. Ngược lại, dùng full Quartz cluster cho một phép dọn temp directory local có thể tạo độ phức tạp không cần thiết.

## 6. Phương án A: một nơi sở hữu lịch, nhiều nơi xử lý việc

Mô hình tách trách nhiệm:

```text
           Scheduling owner / service
                    |
               cron reaches due
                    |
                    v
             Durable job record
                hoặc queue
                    |
          +---------+---------+
          |         |         |
       Worker A  Worker B  Worker C
          |         |         |
     claim & execute jobs
```

**Lợi ích:**

- Tập trung việc diễn giải cron, múi giờ, thay đổi cấu hình và các mốc missed.
- Workers không phải đăng ký tất cả các cron giống nhau.
- Với queue/DB bền vững, workloads có thể được buffer, retry, quan sát.
- Dễ tách công suất worker khỏi scheduler owner.

**Giới hạn:**

- Nếu chỉ có một owner trong RAM, nó chết lúc đến hạn thì **có thể lỡ tick**.
- Nếu failover tạo hai owners tạm thời, **có thể phát trùng**.
- Publish vào queue có thể timeout sau khi broker đã nhận message; retry có thể tạo duplicate events.
- “Có một scheduler deployment” không tự bảo đảm mọi business record được xử lý đúng một lần.

Với job quan trọng, trigger nên **upsert một execution ID xác định** trong DB/queue, không chỉ gửi một `UUID.randomUUID()` mới mỗi attempt.

### Cấu hình vai trò trong Spring Boot

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "jobs",
        name = "triggers.enabled",
        havingValue = "true")
public class ReportTrigger {
    private final JobPublisher publisher;

    public ReportTrigger(JobPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void tick() {
        publisher.publishDueWindow();
    }
}
```

**Đây là snippet tích hợp**, cần có `JobPublisher`, Spring Boot dependency, bean scanning và `@EnableScheduling`/scheduling infrastructure. Triển khai scheduler role với:

```properties
jobs.triggers.enabled=true
```

và worker role:

```properties
jobs.triggers.enabled=false
```

**Quan trọng:** `@ConditionalOnProperty` chỉ quyết định có tạo bean trên **instance này**. Nó **không phải** leader election. Hai pods được cấu hình `true` thì có hai trigger owners; mọi pod `false` thì không có ai phát lịch.

### Kubernetes CronJob có phải một chủ trigger không?

Kubernetes CronJob cho phép khai báo một lịch tại controller cluster. Đây có thể là một nơi trigger phù hợp, nhưng không nên coi nó là lời hứa exactly-once của side effect. Controller có thể tạo trễ, bỏ lỡ hoặc tạo nhiều Job trong một số điều kiện sự cố. `concurrencyPolicy: Forbid` áp dụng tới các Jobs thuộc **cùng CronJob**, không phải global business lock giữa nhiều Kubernetes clusters hoặc những API manual retry khác.

## 7. Phương án B: mọi pod phát tick nhưng chia sẻ cơ chế nhận quyền

Các pod đều có `@Scheduled`, nhưng trước khi xử lý đều thử claim ở **một shared store**:

```text
A tick ----\
B tick -----+--> atomic claim("daily-report@window")
C tick ----/             |
                  +------+------+
                  |             |
                winner        losers
                  |             |
               execute         skip
```

Điều này có lợi khi scheduler owner duy nhất không đủ HA: nếu một pod hỏng, pod khác vẫn có thể gặp mốc và nhận quyền.

Nhưng shared claim phải bảo vệ đúng **hợp đồng**:

- **Distributed lock/lease**: thường bảo vệ việc không có hai executions đồng thời **khi lease còn hiệu lực**.
- **Unique claim theo business window**: bảo vệ một record/tick khỏi được nhận hai lần ở shared database tại thời điểm đó.
- **Durable queue**: giữ công việc cho tới khi xử lý/retry, tùy delivery semantics.
- **Idempotency tại business boundary**: ngăn tác động trùng bất kể trigger tạo lại hay worker retry.

Một `ConcurrentHashMap` static trong một pod không phải shared store. Một `synchronized` trên singleton Spring bean cũng không áp dụng sang pod khác.

## 8. ShedLock: khóa cho các @Scheduled có thể bỏ qua tick

ShedLock là thư viện bên ngoài Spring core dùng shared storage như JDBC/Redis để phối hợp những phương thức được gắn annotation. Theo README của dự án:

- ShedLock là **lock**, không phải distributed scheduler hoàn chỉnh.
- Nếu node A đang giữ lock của một job, node B thường **bỏ qua** lần chạy, không tự xếp hàng để chạy bù.
- `lockAtMostFor` là giới hạn fallback để khóa hết hạn khi node giữ lock chết.
- Nếu job chạy lâu hơn `lockAtMostFor`, node khác có thể nhận lock khi node cũ vẫn đang chạy: nguy cơ overlap.
- `lockAtLeastFor` có thể giảm các lần chạy sát nhau với task rất ngắn và clock skew, nhưng không thay business idempotency.

Ví dụ **snippet tích hợp** (phải cài đúng phiên bản dependencies, cấu hình `LockProvider`/shared store và `@EnableSchedulerLock` trước):

```java
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

@Scheduled(cron = "0 */5 * * * *", zone = "UTC")
@SchedulerLock(
        name = "shared-daily-cache-refresh",
        lockAtMostFor = "PT10M",
        lockAtLeastFor = "PT15S")
public void refresh() {
    cacheService.refreshWithTimeout();
}
```

Code nằm trong một Spring-managed bean có `cacheService`; bản thân snippet không chạy độc lập. Chỉ thêm annotation mà chưa có lock provider **không đủ để có distributed coordination**.

### Khi nào nên chọn ShedLock?

Phù hợp với công việc idempotent, ngắn, có tick kế tiếp, **được phép bỏ qua** khi node khác đang xử lý: refresh cache, đồng bộ dữ liệu có thể tiếp tục ở kỳ kế tiếp.

Không đủ nếu mỗi kỳ phải được xử lý và ghi nhận cho tới khi thành công. Khi đó cần durable job state/queue và policy catch-up.

**Checklist khóa:** mọi pod có chung store? lock name đúng scope? timeout dài hơn thời gian job tối đa? đồng hồ có đồng bộ? có metric skipped? có hành vi khi lease expired và old worker chưa dừng? mọi code path có đi qua wrapper khóa?

## 9. Quartz JDBC clustering: một firing được một node trong cụm nhận

Quartz hỗ trợ một hệ scheduler có state job/trigger lưu trong database. Trong chế độ **JDBC JobStore clustering**, các scheduler instances dùng cùng DB để lựa chọn node nhận từng trigger firing và hỗ trợ failover/misfire theo cấu hình.

```text
               Shared Quartz JDBC JobStore
                           |
             Quartz trigger + job tables
                /          |          \
           Quartz A    Quartz B    Quartz C
                \          |          /
                    acquire trigger
                           |
                      one winner
                           |
                        run job
```

Theo tài liệu Quartz, mỗi firing của một trigger trong cluster thường được một node nhận, nhưng không có nghĩa **external API** sẽ không bao giờ nhận duplicate trong trường hợp crash/recovery.

Để cấu hình đúng cần:

- JDBC JobStore phù hợp, không dùng in-memory `RAMJobStore` rồi mong chia sẻ lịch.
- Các scheduler nodes cùng instanceName/job store, mỗi node có **instanceId riêng** (thường AUTO).
- Bật clustering và dùng schema/datasource/transaction config đúng.
- Kiểm soát clock skew, check-in interval, trigger misfire, recovery và shutdown.
- Không cho scheduler non-clustered dùng cùng tables với cluster đang hoạt động.

Ví dụ cấu hình ý tưởng của Quartz:

```properties
org.quartz.scheduler.instanceName=clustered-reports
org.quartz.scheduler.instanceId=AUTO
org.quartz.jobStore.isClustered=true
org.quartz.jobStore.tablePrefix=QRTZ_
```

Đây **không phải file cấu hình hoàn chỉnh**: còn cần JobStore class, datasource/schema, transaction policy và các key tích hợp Spring Boot (ví dụ `spring.quartz.job-store-type=jdbc`). Không tự đặt schema initialization kiểu drop/recreate trong production khi nâng cấp deployment.

**Phù hợp:** nhiều cron/jobs động, yêu cầu lưu qua restart, có misfire/recovery và muốn scheduler engine quản lý trigger state. **Không cần thiết:** các job local đơn giản vốn được phép bỏ tick.

## 10. Durable job table: mỗi logical execution có danh tính và trạng thái

Với yêu cầu “đã tới kỳ thì cuối cùng phải xử lý hoặc ghi nhận FAILED”, một bảng job bền vững và cơ chế claim atomic thường rõ ràng hơn lock theo tick.

Lưu từng logical execution:

```sql
CREATE TABLE scheduled_job_runs (
    job_id        text        NOT NULL,
    due_at        timestamptz NOT NULL,
    status        text        NOT NULL DEFAULT 'PENDING',
    lease_owner   text,
    lease_until   timestamptz,
    generation    bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (job_id, due_at)
);
```

Các columns đóng vai trò:

- `job_id` là logical ID, có thể gồm namespace tenant/partition.
- `due_at` là mốc business time được chuẩn hóa và **ổn định giữa retries**.
- PK `(job_id, due_at)` ngăn tạo hai records cho cùng một logical window.
- `status` thể hiện PENDING/RUNNING/SUCCEEDED/FAILED theo state machine do ứng dụng định nghĩa.
- `lease_owner`, `lease_until` và `generation` phục vụ claim/renewal/fencing.

Khi nhiều trigger cùng tới hạn:

```sql
INSERT INTO scheduled_job_runs (job_id, due_at)
VALUES (:job_id, :due_at)
ON CONFLICT (job_id, due_at) DO NOTHING;
```

Đây là PostgreSQL SQL với named parameters cần bind qua ứng dụng. Unique constraint giúp dedupe trong **cùng database có giao dịch**, không phải phép màu chống tác động trùng của HTTP/email.

### Atomic conditional claim

Một mẫu PostgreSQL cho một row cụ thể:

```sql
WITH db_clock AS (
    SELECT clock_timestamp() AS now_at
)
UPDATE scheduled_job_runs AS j
SET status      = 'RUNNING',
    lease_owner = :node_id,
    lease_until = db_clock.now_at + INTERVAL '2 minutes',
    generation  = j.generation + 1
FROM db_clock
WHERE j.job_id = :job_id
  AND j.due_at = :due_at
  AND j.due_at <= db_clock.now_at
  AND (
      j.status = 'PENDING'
      OR (
          j.status = 'RUNNING'
          AND j.lease_until < db_clock.now_at
      )
  )
RETURNING j.generation;
```

Chỉ transaction nào thực hiện `UPDATE` hợp lệ mới nhận row mới/generation. Với workers cạnh tranh cùng row, row locking và điều kiện WHERE giúp chỉ một owner claim tại thời điểm đó. Commit claim **trước khi thực hiện blocking remote call**, không giữ transaction/row lock suốt vài phút chờ API.

Một worker hoàn tất phải kiểm tra **owner và generation**:

```sql
UPDATE scheduled_job_runs
SET status = 'SUCCEEDED',
    lease_owner = NULL,
    lease_until = NULL
WHERE job_id = :job_id
  AND due_at = :due_at
  AND status = 'RUNNING'
  AND lease_owner = :node_id
  AND generation = :generation;
```

Nếu affected rows bằng 0, worker đã mất quyền hoặc state đã đổi; không được âm thầm tuyên bố thành công.

**Đây chỉ là mẫu claim, chưa phải engine hoàn chỉnh.** Phải bổ sung tìm due jobs, indexes, retry budget, failed/dead-letter state, heartbeat/renewal, orphan recovery, queuing, transaction isolation, audit và capacity. Nếu công việc chạy quá hai phút mà lease không được gia hạn, owner mới có thể nhận lại trong lúc owner cũ vẫn chạy.

### SKIP LOCKED khi có nhiều consumers

PostgreSQL hỗ trợ `FOR UPDATE SKIP LOCKED` cho bảng queue-like. Nó tránh chờ các rows đang bị khóa, nhưng tài liệu cảnh báo kết quả có thể nhìn một tập rows không hoàn toàn nhất quán, không phù hợp cho mọi query nghiệp vụ. Dùng khi thiết kế dequeue nhiều workers và hiểu rõ transaction boundaries; không coi SKIP LOCKED là distributed lock toàn cầu qua các databases độc lập.

## 11. Java 17 runnable: ba node giả lập và một điểm claim chung

Ví dụ sau không dùng Spring/DB, giúp kiểm tra **logic khác biệt giữa local và shared coordination**. Ba “node” được mô phỏng bằng ba tasks trên cùng một JVM để có thể chủ động đồng bộ điểm bắt đầu:

- **LOCAL:** mỗi task có một `AtomicBoolean(false)` riêng. Cả ba đều giành quyền ở lock của chính mình.
- **SHARED:** mọi task dùng chung một `ConcurrentHashMap.newKeySet()` cho cùng key. Một task thắng, hai task skip.

**Lưu ý rất quan trọng:** set trong ví dụ chỉ là **mô hình shared store trong một JVM**. Triển khai thực tế trên nhiều pods phải thay bằng PostgreSQL atomic claim, distributed lease/store hoặc durable queue. Không copy static map này để làm distributed lock.

### File SharedClaimDemo.java

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SharedClaimDemo {
    private static final int NODES = 3;
    private static final String WINDOW =
            "report@2026-11-02T02:00:00Z";

    private record Summary(
            int triggers, int executions, int skipped) {}

    private static Summary runScenario(boolean shared)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(NODES);
        CountDownLatch ready = new CountDownLatch(NODES);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> claims = ConcurrentHashMap.newKeySet();
        AtomicInteger businessCalls = new AtomicInteger();
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < NODES; i++) {
                // Mỗi node có local lock riêng, không chia sẻ.
                AtomicBoolean local = new AtomicBoolean(false);

                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Start timeout");
                    }

                    boolean won = shared
                            ? claims.add(WINDOW)
                            : local.compareAndSet(false, true);
                    if (won) {
                        businessCalls.incrementAndGet();
                    }
                    return won;
                }));
            }

            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Workers not ready");
            }
            start.countDown();

            int winners = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(5, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            if (winners != businessCalls.get()) {
                throw new IllegalStateException("Count mismatch");
            }

            return new Summary(
                    NODES, businessCalls.get(), NODES - winners);
        } finally {
            start.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Pool did not stop");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Summary local = runScenario(false);
        Summary shared = runScenario(true);

        System.out.println("localTriggers=" + local.triggers());
        System.out.println("localExecutions=" + local.executions());
        System.out.println("localSkipped=" + local.skipped());
        System.out.println("sharedTriggers=" + shared.triggers());
        System.out.println("sharedExecutions=" + shared.executions());
        System.out.println("sharedSkipped=" + shared.skipped());
    }
}
```

Biên dịch/chạy JDK 17+:

```bash
javac --release 17 SharedClaimDemo.java
java SharedClaimDemo
```

**Standard output xác định trên đường chạy thành công:**

```text
localTriggers=3
localExecutions=3
localSkipped=0
sharedTriggers=3
sharedExecutions=1
sharedSkipped=2
```

### Tại sao kết quả không phụ thuộc thứ tự threads?

`ready` đảm bảo cả ba tasks đã khởi động và đang chờ trước khi main mở `start`. Không dùng `Thread.sleep()` để đoán thứ tự. Trong LOCAL mode, ba `AtomicBoolean` thuộc ba objects riêng nên cả ba `compareAndSet(false,true)` thắng. Trong SHARED mode, thao tác `claims.add(WINDOW)` atomic trên cùng concurrent set: một lời gọi trả true, hai lời gọi trả false. Main đợi mọi `Future.get(5s)` trước khi in.

`finally` mở gate và đóng executor ngay cả khi timeout. Nếu môi trường quá tải khiến tác vụ không hoàn tất trong năm giây, ví dụ sẽ báo exception thay vì in kết quả như thể đã thành công.

**Giới hạn thực nghiệm:** không có network, DB, time-based lease, retry hay cluster thật. Chương trình chỉ kiểm chứng ý tưởng “phối hợp phải nhìn một trạng thái chung” và phân biệt local exclusion với shared atomic claim. Không suy từ output ra exactly-once cho API đối tác.

## 12. Leader election, lease và nguy cơ split brain

Một chủ trigger duy nhất có thể được chọn bằng leader election. Nhưng leader election thường cấp **lease có thời hạn**, không khóa cứng vĩnh viễn.

```text
t0: Pod A lấy lease, generation=17
t1: A bắt đầu gửi báo cáo qua HTTP
t2: A mất kết nối với coordinator, HTTP vẫn chạy
t3: lease A hết hạn
t4: Pod B được chọn, generation=18
t5: B cũng bắt đầu xử lý cùng window
t6: A khôi phục và nhận HTTP response thành công
```

Ở t5, coordinator chỉ có **một owner hợp lệ** (B), nhưng ngoài hệ thống vẫn có thể có **hai business calls**. Đây là *split-brain-like stale worker* trong xử lý phân tán.

### Fencing token

Mỗi lần lease/claim tăng token:

```text
currentGeneration(job)=18

A writes with generation 17 -> REJECT_STALE
B writes with generation 18 -> ACCEPT
```

Token phải được kiểm tra **tại nơi nhận ghi** hoặc trong transaction có điều kiện. Chỉ lưu token trong log hoặc biến local không ngăn được một worker lỗi thời gọi API.

Nếu external partner không hỗ trợ fencing, cần idempotency key (nếu partner hỗ trợ), outbox/compensation và reconciliation. Đặc biệt không coi `Future.cancel(true)` là một bảo đảm remote HTTP/JDBC đã rollback.

### Lease duration và heartbeat

- Lease ngắn giúp failover nhanh nhưng nếu task lâu hơn lease sẽ tăng nguy cơ overlap.
- Lease dài giảm overlap trong phần lớn trường hợp nhưng có thể làm recovery chậm.
- Với task dài, thiết kế heartbeat/renew lease có kiểm tra owner và generation.
- Nếu renewal thất bại, worker không nên commit dữ liệu như thể vẫn có quyền.
- Dùng clock của coordinator/shared DB cho TTL check khi có thể; đừng dựa hoàn toàn vào clock của từng pod.
- Luôn có deadline tối đa, monitoring job stuck và chính sách xử lý owner cũ.

## 13. Idempotency key quan trọng hơn một khóa chỉ có thời hạn

Hai pods có thể phát cùng logical tick nhưng đến ở hai mili-giây khác nhau. Nếu execution ID được tạo từ `Instant.now()` hoặc `UUID.randomUUID()` của mỗi attempt, chúng không đụng cùng unique key.

Ví dụ identity ổn định:

```text
jobId         = daily-invoice
tenantId      = tenant-42
businessDate  = 2026-11-02
timezone      = Asia/Ho_Chi_Minh

Business key = (jobId, tenantId, businessDate)
```

Cần thống nhất nếu một ngày địa phương có DST gap/overlap, hoặc nếu scheduled fire time UTC khác business date. Retry một kỳ cũ phải dùng **cùng business key**, không sinh một key mới chỉ vì worker mới chạy sau restart.

### Unique DB constraint khác distributed lock

- Unique constraint có thể ngăn hai records trùng trong **cùng transactional database**.
- Distributed lock thường ngăn **concurrent execution** trong thời gian giữ khóa.
- Retry sau một crash có thể cần **reclaim job** dù business record vẫn chưa SUCCEEDED.
- Một unique row đã được tạo rồi worker chết vẫn cần recovery; nếu coi “row tồn tại” là “done”, công việc sẽ mất.
- Với HTTP/payment/email, phía ngoài DB có thể đã thành công dù local transaction chưa commit. Cần idempotency tại downstream, transactional outbox hoặc đối soát.

Vì vậy hãy tách **execution key**, **lease key**, **business idempotency key**. Chúng có thể giống nhau trong một bài toán đơn giản nhưng không phải luôn cùng phạm vi.

## 14. Mỗi tenant/partition cần một chủ hay một khóa riêng?

Nếu có 1.000 tenants, mỗi tenant có bản báo cáo riêng, một global lock `daily-report` sẽ serialize cả 1.000 jobs. Một tenant rất chậm có thể chặn 999 tenants khác.

Mô hình tốt hơn có thể là:

```text
logical ownership:
    tenant-A / daily-report
    tenant-B / daily-report
    tenant-C / daily-report

business execution identity:
    (tenantId, reportDate, reportType)
```

Nhưng đừng tùy ý tạo hàng triệu scheduler instances/threads cho mỗi tenant. Dùng một số scheduler/worker pools chia sẻ, **keys per tenant** ở coordinator và queue có admission/backpressure.

Nếu dùng Kafka/consumer groups hoặc partitioned queue, partition assignment có thể đảm bảo một consumer trong group nhận một partition tại một thời điểm, nhưng rebalance/retry/offset commit vẫn có thể gây **at-least-once processing**. Business idempotency vẫn cần với side effects quan trọng.

### Phạm vi nhiều regions

Một tenant có thể được gán sở hữu cho region A. Failover sang region B cần tăng ownership generation hoặc đảm bảo A ngừng ghi. Dữ liệu state phục vụ coordination phải nhất quán theo phạm vi mà công việc cạnh tranh. Hai dữ liệu registry độc lập không trở thành một coordinator chỉ vì dùng cùng tên lock.

## 15. SKIP, WAIT, QUEUE và CATCH-UP là bốn hợp đồng khác nhau

| Policy | Khi một job/window đang được xử lý | Khi phù hợp |
| --- | --- | --- |
| **SKIP** | Bỏ tick mới, không lưu chờ | Cache refresh, health checks |
| **WAIT** | Chờ lock có timeout | Cần chạy gần mốc, chấp nhận đợi ngắn |
| **QUEUE** | Lưu pending execution cho worker xử lý sau | Báo cáo bắt buộc, orders |
| **CATCH-UP** | Tạo lại windows đã lỡ theo lịch, xử lý có giới hạn | Reconciliation và closing |
| **RETRY** | Thử lại execution thất bại với backoff/budget | Lỗi tạm thời của downstream |

Spring `@Scheduled` không tự biến các missed windows thành durable pending jobs. ShedLock khi thua khóa thường skip. Quartz có misfire/recovery policies nhưng vẫn phải được cấu hình theo hợp đồng. Durable table/queue có thể lưu tất cả windows, đổi lại phải quản lý retry, retention, dedupe, dead-letter và quotas.

**Không nhầm tiếp tục ở tick ngày hôm sau với retry kỳ ngày hôm trước.** Một công việc báo cáo ngày 2 không tự sửa tình trạng báo cáo ngày 1 bị bỏ lỡ chỉ nhờ cron tiếp tục chạy.

## 16. Khi cấu hình cron thay đổi, đừng tạo thêm owner hoặc registration

Một lỗi độc lập với distributed lock: listener reload config gọi `taskScheduler.schedule(task,newTrigger)` nhiều lần mà không cancel handle cũ. Trên một pod đã có thể phát trùng, rồi nhân số lượng với replicas.

Quy trình an toàn hơn trong một JVM:

1. Có logical job ID và registry sở hữu `ScheduledFuture<?>` hiện hành.
2. Validate cron/zone trước khi thay đổi.
3. So sánh **effective spec** (cron, zone, enabled, semantics/version), không thay lịch khi config giống hệt.
4. Serialize updates theo job ID để hai config events không tạo hai active owners.
5. Đăng ký candidate với generation mới theo một chiến lược rõ; nếu không đăng ký được, báo lỗi mà không âm thầm làm mất lịch cũ.
6. Publish generation mới và yêu cầu `cancel(false)` đối với handle cũ.
7. Callback stale kiểm tra generation trước khi xử lý. Điều này không rollback execution đã bắt đầu.
8. Nếu job cần một lần toàn cluster, còn phải có **shared claim**; registry local không đủ.

Thêm một điều: không giữ cùng job vừa có `@Scheduled` annotation tĩnh vừa được manual schedule trong service khác, trừ khi chủ đích có hai triggers độc lập.

## 17. Những tình huống dễ làm mất việc hoặc xử lý trùng

**Scheduler owner crash đúng mốc:** nếu không lưu due time/catch-up, tick bị mất. Cần durable schedule state khi không được phép mất kỳ.

**Hai pods đều bật role scheduler:** property role không tự có leader election; mỗi pod phát một tick.

**Lock hết hạn giữa việc:** owner mới claim và worker cũ vẫn gọi external API. Cần fencing/idempotency.

**Timeout sau publish queue:** broker có thể đã nhận message, caller lại retry publish. Cần stable message/execution key.

**DB claim thành công, worker chết trước business:** có row RUNNING nhưng chưa thực hiện; cần lease expiry/recovery.

**External business thành công, worker chết trước SUCCEEDED:** retry có thể gây side effect trùng. Cần downstream idempotency hoặc reconciliation.

**Clock skew:** hai pods tính window/time khác nhau. Quy ước dùng UTC instants/shared DB time và lưu zone/business calendar khi cần.

**Region A không nhìn thấy lock của region B:** hai vùng chọn hai winners. Cần shared coordinator/ownership.

**Lock key quá rộng:** job tenant A làm kẹt tenant B không liên quan. Key theo logical scope.

**Lock key quá hẹp:** retry tạo UUID mới, unique constraint không phát hiện same job. Key theo business identity ổn định.

**Autoscaling tăng tất cả @Scheduled replicas:** duplicate attempts tăng theo pod count nếu không phối hợp.

**Một đường manual admin bypass claim:** scheduler có lock nhưng admin API gọi trực tiếp business method. Bảo vệ cuối cùng phải nằm ở boundary chung.

**Giới hạn resource downstream:** một shared scheduler không tự tăng DB/HTTP capacity; vẫn cần connection pool, bulkhead, rate limiter và timeout.

## 18. Quan sát: cần nhìn cả trigger, claim và business commit

| Metric/Log | Câu hỏi |
| --- | --- |
| `tickCount{pod,jobType}` | Bao nhiêu pods đang tự kích hoạt? |
| `claimSucceeded/claimSkipped` | Một window có bao nhiêu winners/losers? |
| `leaseOwner/generation` | Owner có còn quyền không? |
| `leaseRenewalFailures` | Có worker mất quyền đang chạy không? |
| `executionStarted/Completed/Failed` | Công việc thật đã đi tới đâu? |
| `lastSuccessfulWindow` | Có bỏ lỡ kỳ bắt buộc không? |
| `pending/retry/deadLetter` | Backlog có được khôi phục không? |
| `duplicateBusinessKeyRejected` | Nơi commit đã bắt được trùng chưa? |
| `scheduledVsActualStartLag` | Lịch bị trễ do queue/owner/GC? |
| `external429/503/timeout` | Có retry storm hay quá tải partner? |

Không chỉ giám sát `ScheduledFuture.isDone()` trên một pod. Future không có thông tin về những workers khác trong cluster hoặc external side effects.

Log nên có `jobId`, `window`, `nodeId`, `leaseGeneration`, `claimResult` và `executionId`, nhưng tránh đưa các ID có cardinality khổng lồ vào mọi metrics tags. Dùng shared job status store để đối soát những kỳ chưa hoàn tất.

## 19. Kế hoạch kiểm thử race, crash và failover

Các phép thử cần kiểm soát thứ tự bằng latch/barrier/transaction, không dựa vào `Thread.sleep(100)` để đoán:

1. **Local-only lock:** hai JVM riêng cùng giữ lock local và cùng chạy; xác nhận không có distributed guarantee.
2. **Shared claim:** ba clients vào một PostgreSQL row đúng lúc; chỉ một transaction claim ở cùng thời điểm.
3. **Crash sau claim:** worker A dừng trước business, worker B reclaim sau lease expiry; job không bị mất.
4. **Crash sau HTTP success:** worker B retry cùng business key; partner idempotency chống gửi lại.
5. **Lease hết hạn khi A còn chạy:** B lấy generation mới, A không commit local DB state khi mất quyền.
6. **Scale 1→5 pods:** số cron ticks tăng nhưng số successful business windows không nhân lên.
7. **Coordinator outage:** ứng dụng fail-open hay fail-closed? Test đúng rủi ro nghiệp vụ.
8. **Clock skew/DST:** các nodes tính cùng key cho cùng một ngày nghiệp vụ.
9. **Config reload:** không còn hai registrations trong một pod và không tạo hai cron owners do config drift.
10. **Owner failover:** scheduler mới hồi phục missed windows theo hợp đồng; không chỉ bật cron cho tương lai.
11. **Partition ownership:** tenant A không chặn tenant B, nhưng hai workers của tenant A không commit trùng.
12. **Retry storm:** backoff, max attempts và queue limits bảo vệ downstream.
13. **Shutdown:** lease/heartbeat/worker cleanup đúng, không cần hứa interrupt sẽ rollback HTTP.
14. **Job dài:** test sát ngưỡng lock expiry; không chọn `lockAtMostFor` ngắn hơn worst-case task duration mà không có renewal.

Kết quả test nên ghi cả *claim count*, *execution attempts*, *successful business commits* và *lost/missed windows*. Một log “one winner” chưa chứng minh dữ liệu đúng sau crash.

## 20. Checklist thiết kế và vận hành

1. **Logical job ID** có ổn định trên mọi pod và qua restart?
2. **Phạm vi** là JVM, cluster, tenant, partition hay multi-region?
3. **“Một lần”** là trigger, concurrent execution, claim/window hay business effect?
4. Công việc có **được skip một tick** không?
5. Nếu chỉ một trigger owner, **ai tiếp quản** khi nó chết và missed ticks được xử lý ra sao?
6. Nếu nhiều pods tick, **shared store** có thực sự được tất cả pods/regions dùng chung không?
7. Có **unique execution key** theo window không? Có bao gồm tenant khi cần?
8. Có **transactional claim** hoặc lock/lease được atomic trong shared store không?
9. Lease hết hạn nhưng old worker còn chạy thì **fencing** ở đâu?
10. External side effect có **idempotency key** hoặc reconciliation không?
11. Có retry budget, backoff, DLQ/FAILED state và policy catch-up không?
12. Cấu hình cron/zone thay đổi có được cập nhật **idempotent** không?
13. Có tránh cùng job vừa `@Scheduled` vừa manual schedule không?
14. Tài nguyên downstream có hạn mức connection/concurrency/rate riêng không?
15. Có metrics last-success-window, pending, lease failures và duplicate attempts?
16. Có integration test nhiều processes thật dùng cùng database/coordinator?
17. Có runbook phục hồi jobs stuck, kiểm soát leader và rollback config?
18. Có biết giới hạn guarantee của framework/library đã chọn, không suy diễn exactly-once?
19. Có kiểm tra chi phí coordination khi tăng replicas/partitions?
20. Có phân biệt “scheduler khỏe” với “business outcome đã thành công” trong dashboard không?

### Bảng chọn nhanh

| Nhu cầu | Lựa chọn thực tế thường phù hợp |
| --- | --- |
| Mỗi pod dọn state riêng | `@Scheduled` local |
| Một job chung, skip tick được | Scheduler owner hoặc multi-pod + ShedLock |
| Cần trigger HA nhưng ít jobs | Leader lease + durable event ID |
| Nhiều cron động, cần job store | Quartz JDBC cluster |
| Nhiều one-shot jobs cần khôi phục | Durable job table/queue + atomic claim |
| Mỗi tenant/job window không trùng | Lock/claim key có tenant + business window |
| External side effect không được nhân đôi | Business idempotency/transactional outbox/fencing |

## 21. Tài liệu tham khảo và tổng kết

1. [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Scheduled`, `TaskScheduler`, các triggers độc lập và trường hợp nhiều scheduled bean instances.
2. [Spring Framework 6.2 – TaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/TaskScheduler.html): API scheduling, trigger và `ScheduledFuture`.
3. [Spring Framework 6.2 – ThreadPoolTaskScheduler](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html): pool/lifecycle cục bộ và cancellation.
4. [ShedLock – README](https://github.com/lukas-krecan/ShedLock): lock store, skip behavior, `lockAtMostFor` và `lockAtLeastFor`; ShedLock không phải distributed scheduler hoàn chỉnh.
5. [Quartz – Configuration/Clustering](https://www.quartz-scheduler.org/documentation/2.3.1-SNAPSHOT/configuration.html): cluster JDBC JobStore, trigger acquisition và failover.
6. [Quartz – Lesson 11](https://www.quartz-scheduler.org/documentation/quartz-2.2.2/tutorials/tutorial-lesson-11.html): cluster coordination và recovery.
7. [PostgreSQL – INSERT / ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html): unique constraint/deduplication.
8. [PostgreSQL – UPDATE / RETURNING](https://www.postgresql.org/docs/current/sql-update.html): conditional claim/fencing.
9. [PostgreSQL – SELECT / SKIP LOCKED](https://www.postgresql.org/docs/current/sql-select.html): queue-like multi-consumer rows.
10. [Java SE 17 – ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): shared-set simulation trong một JVM.
11. [Java SE 17 – CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): bài test đồng thời không dựa vào sleep.

**Kết luận:** chọn **một nơi phát lịch** khi bạn cần owner rõ và có chính sách failover; chọn **phối hợp qua shared claim/lease** khi nhiều pods đều có thể trigger và cần loại trừ thực thi đồng thời. Nếu công việc không được mất qua restart, phải có **durable job state**. Nếu tác động không được nhân đôi, khóa hoặc leader election chưa đủ — cần **business identity ổn định, idempotency/fencing ở nơi commit**. Luôn đặt phạm vi đúng theo job, tenant, partition và business window thay vì khóa mọi thứ toàn cụm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="distributed-limits-004">Tính lặp an toàn bằng mã công việc và dấu mốc xử lý</a>

<details>
<summary>Xem chi tiết</summary>

# Tính lặp an toàn bằng mã công việc và dấu mốc xử lý

## 1. Vấn đề: cùng một công việc có thể đến nhiều lần

Trong hệ thống Spring chạy trên nhiều JVM/pod, cùng một yêu cầu nghiệp vụ có thể được thực thi **hơn một lần**, dù người gửi chỉ muốn thực hiện một lần. Những nguyên nhân phổ biến:

- Consumer nhận lại message vì database commit xong nhưng chưa kịp ack hoặc commit offset.
- HTTP client timeout rồi retry, dù server đã thực hiện thay đổi.
- Scheduler ở hai replica cùng kích hoạt một job theo lịch.
- Worker crash sau side effect nhưng trước khi ghi trạng thái hoàn tất.
- Message broker redelivery khi worker xử lý lâu hơn visibility timeout hoặc lease hết hạn.
- Người dùng bấm hai lần và mỗi request được dispatch cho một replica.
- Network trả lỗi sau khi upstream đã xử lý thành công, khiến kết quả là **UNKNOWN**.
- Một job cũ tỉnh lại sau khi lease hết hạn và worker mới đã nhận quyền xử lý.

Ví dụ một lệnh cộng tiền vào ví:

```text
jobId = "topup-2026-0007"
tenant = "acme"
walletId = "W-17"
amountMinor = 2500

Lần A: cộng 2500, COMMIT DB, trước khi ACK thì worker chết.
Lần B: broker phát lại cùng message.
Nếu chỉ viết: UPDATE wallets SET balance = balance + 2500
thì số dư bị cộng hai lần.
```

Giải pháp đúng không phải “hy vọng broker chỉ phát một lần”. Ta cần một **định danh công việc ổn định** và **dấu mốc xử lý có tính nguyên tử** để nhiều lần giao cùng một yêu cầu chỉ tạo ra **một tác dụng nghiệp vụ hợp lệ** trong phạm vi đã định nghĩa.

Trong module **Spring Framework – concurrency**, bài này thuộc nhóm **Giới hạn phân tán**: các giới hạn về retry, phân phối, crash, xử lý trùng, ownership và nhất quán khi code Java/Spring chạy trên nhiều process.

### Mục tiêu học tập

Sau khi học, bạn có thể:

1. Phân biệt `jobId`/`operationId`, `messageId`, `attemptId`, `correlationId`, `tenantId` và idempotency key.
2. Thiết kế một **processing marker** bền vững với unique constraint, trạng thái, phiên bản và kết quả.
3. Chặn race kiểu “SELECT chưa có → cả hai worker cùng xử lý”.
4. Dùng database transaction bao trùm **claim marker + business mutation + success marker** trong **cùng database**.
5. Giải thích vì sao lưu dấu `DONE` **trước** side effect hoặc lưu **sau** side effect trong transaction khác đều có cửa sổ lỗi.
6. Xử lý retry, rollback, lease expiry, fencing token, duplicate và key/payload conflict.
7. Đặt transaction ở Spring-managed service đúng proxy boundary; không dựa vào `@Async` để tự chuyển transaction.
8. Dùng outbox/inbox và downstream idempotency cho hiệu ứng ngoài database.
9. Chạy ví dụ Java 17+ và một project Spring Boot + PostgreSQL kiểm chứng replay không cộng hai lần.
10. Xây test concurrency nhiều replica, crash/retry, retention, logging và metrics.

**Thông điệp cốt lõi:** *at-least-once delivery + một định danh ổn định + cơ chế xử lý lặp an toàn* thường thực tế hơn một lời hứa “chạy đúng một lần” mơ hồ. Phải xác định rõ **cái gì** chỉ xảy ra một lần và **ở ranh giới lưu trữ nào** bảo đảm đó được thực thi.

## 2. Định nghĩa tính lặp an toàn (idempotency)

Một thao tác được gọi là **idempotent** khi áp dụng lại cùng một yêu cầu không làm thay đổi kết quả nghiệp vụ đã xác định sau lần áp dụng thành công đầu tiên.

Ví dụ:

```text
setStatus(orderId=42, status="APPROVED")
    có thể idempotent theo trạng thái:
    gọi lại vẫn là APPROVED.

incrementBalance(walletId=W, amount=100)
    không idempotent mặc định:
    gọi hai lần có thể cộng 200.

incrementBalance(walletId=W, amount=100, operationId=OP-9)
    có thể trở thành idempotent theo operationId
    nếu chỉ một mutation OP-9 được chấp nhận.
```

Phân biệt hai mức:

- **Idempotent về trạng thái cuối:** áp dụng lại giữ trạng thái cuối không đổi.
- **Idempotent về tác dụng phụ:** không tạo thêm giao dịch, email, webhook, thanh toán, điểm thưởng hay audit business event ngoài mong muốn.

Một câu lệnh `SET status='APPROVED'` có thể giữ trạng thái order, nhưng nếu mỗi lần gọi lại phát một email, thao tác **chưa idempotent theo hiệu ứng email**.

### 2.1. “Exactly-once execution” và “exactly-once effect” không giống nhau

| Khái niệm | Nghĩa | Có thể thực tế kỳ vọng điều gì? |
| --- | --- | --- |
| At-most-once delivery | Có thể không giao, nhưng không lặp theo cơ chế giao đó | Có nguy cơ mất việc |
| At-least-once delivery | Có thể giao lặp; retry cho tới khi đạt điều kiện | Consumer cần idempotency |
| Exactly-once processing trong một transaction domain | Một thay đổi được ghi nhận đúng một lần trong phạm vi transaction đó | Có thể đạt bằng unique constraint/transaction |
| Exactly-once external effect toàn hệ | Email, payment, DB và broker đều xảy ra đúng một lần bất chấp crash | Không tự có; cần protocol/idempotency/reconciliation cho từng biên |

Ví dụ Kafka transactional producer có thể cung cấp exactly-once semantics cho phạm vi **consume → process → produce** trong Kafka theo cấu hình phù hợp, nhưng **không tự đưa mọi database transaction và email bên ngoài vào cùng một commit nguyên tử**.

### 2.2. Replay là hành vi bình thường

Nếu consumer đã xử lý xong nhưng chết trước ack, redelivery là một cách broker bảo vệ công việc khỏi bị mất. Vì vậy duplicate không nhất thiết là lỗi broker. Một ứng dụng vững chắc phải chấp nhận rằng duplicate/retry có thể xảy ra, rồi thiết kế **khả năng nhận biết và xử lý lại an toàn**.

## 3. Mã công việc: định danh nghiệp vụ phải giữ nguyên qua retry

Định danh là nền tảng của deduplication. Nhưng **không phải mọi ID đều cùng một ý nghĩa**.

| Trường | Mục đích | Có được đổi giữa những lần retry cùng job? |
| --- | --- | --- |
| `jobId` / `operationId` | Xác định **một ý định nghiệp vụ** cần áp dụng một lần | **Không** |
| `idempotencyKey` | Key do client/producer cung cấp cho retry cùng operation | **Không** |
| `messageId` | Xác định một bản message transport | Có thể đổi nếu cùng business job được phát lại dưới message khác |
| `attemptId` | Xác định một lần thử xử lý | **Có**, mỗi attempt cần ID riêng |
| `correlationId` | Liên kết logs/traces giữa các dịch vụ | Thường giữ xuyên workflow; không phải dedupe key mặc định |
| `tenantId` | Scope sở hữu business operation | Không được trộn tenants |
| `aggregateId` | Định danh order/wallet/đối tượng nghiệp vụ | Nhiều job khác nhau có thể tác động cùng aggregate |
| `generation` / `fencingToken` | Phân biệt owner cũ và owner mới | Tăng khi claim/reclaim để từ chối kết quả cũ |

**Ví dụ sai:** consumer tạo `UUID.randomUUID()` **mỗi lần nhận lại message** rồi dùng UUID đó làm dedupe key. Lần retry có key mới nên hệ thống xử lý lặp như một operation mới.

**Ví dụ đúng:** API tạo key cho lần yêu cầu đầu hoặc client cung cấp key; key được giữ trong message/outbox và mọi retry dùng lại đúng key đó. Mỗi lần thử có thể có `attemptId` mới nhưng cùng `operationId`.

### 3.1. Key không nhất thiết là một UUID

Một key có thể được sinh từ business identity, ví dụ:

```text
tenantId = acme
operationType = SEND_INVOICE
invoiceId = INV-507
invoiceVersion = 12

dedupe scope = (tenantId, operationType, invoiceId, invoiceVersion)
```

Điều kiện là key phải biểu diễn **đúng một ý định**, không nhầm hai sự kiện khác nhau. Nếu cùng invoice có thể gửi lại chủ động theo yêu cầu mới, cần thêm version hoặc một request operation ID riêng; dùng duy nhất `invoiceId` có thể chặn nhầm yêu cầu mới hợp lệ.

### 3.2. Tenant scope và key reuse

Unique key nên áp dụng theo đúng phạm vi, ví dụ `PRIMARY KEY (tenant_id, operation_id)`, hoặc `(consumer_name, tenant_id, event_id)` cho inbox của nhiều consumer độc lập.

Nếu key chỉ unique toàn cầu nhưng do khách hàng tự đặt, có thể gây false conflict hoặc cho khách hàng này can thiệp key của khách hàng khác. Nếu chỉ unique theo từng tenant nhưng job tác động một tài nguyên dùng chung, cần thiết kế scope rộng hơn. Hãy ghi rõ **ai sở hữu namespace của key**.

### 3.3. Cùng key nhưng payload khác là lỗi hợp đồng

Client retry cùng `operationId` với amount mới không được trả “đã xong” một cách im lặng. Nên lưu một **request fingerprint** hoặc các trường bất biến dùng để so sánh:

```text
OP-9 + wallet=W1 + amount=100   → xử lý lần đầu
OP-9 + wallet=W1 + amount=100   → replay, trả kết quả cũ
OP-9 + wallet=W1 + amount=999   → CONFLICT, không áp dụng
```

Fingerprint phải lấy từ **canonical input** có schema ổn định. Hash JSON thô không canonical có thể khác chỉ do thứ tự fields hoặc whitespace. Với dữ liệu nhạy cảm, không log toàn bộ payload; có thể dùng hash cryptographic sau khi canonicalize và tránh nhận key không được xác thực.

## 4. Processing marker: dấu mốc có ý nghĩa gì?

**Dấu mốc xử lý** (inbox marker / processed-job record) là bản ghi bền vững cho biết một operation đã được hệ thống biết đến, đang được sở hữu hoặc đã hoàn thành. Dấu mốc có thể nằm trong database chung với business state, trong job store hoặc trong một hệ thống idempotency chuyên dụng.

Các fields thường dùng:

```text
tenant_id           scope sở hữu
operation_id        key ổn định qua retries
request_fingerprint hash/định danh payload bất biến
state               READY/RUNNING/SUCCEEDED/FAILED...
attempt             số lần thử
lease_owner         worker hiện có quyền xử lý
lease_until         thời hạn quyền sở hữu
generation          fencing token/revision
result_payload      kết quả để replay an toàn
created_at          thời điểm tạo
updated_at          thời điểm cập nhật
completed_at        thời điểm hoàn tất
last_error          lỗi hữu ích cho vận hành
```

Không nhất thiết mọi hệ cần tất cả fields. Với một mutation DB cực ngắn và cùng transaction, marker chỉ có `operation_id`, fingerprint, result và thời gian commit có thể đủ. Với job dài qua nhiều process và lease, cần state/generation/ownership rõ ràng.

### 4.1. State machine mẫu

```text
                     +----------------+
new operation -----> | READY / PENDING|
                     +----------------+
                              |
                         atomic claim
                              |
                              v
                      +---------------+
                      |    RUNNING    |---- heartbeat/extend lease
                      +---------------+
                         |          |
                  commit success    retryable error / lease expires
                         |          |
                         v          v
                  +-----------+  +------------------+
                  | SUCCEEDED |  | RETRYABLE / READY|
                  +-----------+  +------------------+
                                         |
                                      new claim

Other terminal outcome: FAILED_TERMINAL / CANCELLED
phải theo policy nghiệp vụ, không coi là SUCCEEDED.
```

**Lưu ý:** một trạng thái `RUNNING` không phải bằng chứng rằng job **đã hoàn tất**. Không từ chối vĩnh viễn mọi bản retry chỉ vì có marker. Nếu owner crash, phải có cách reclaim hợp lệ hoặc recovery từ job store.

### 4.2. Dấu mốc “đã nhìn thấy” khác “đã áp dụng thành công”

- `RECEIVED`: đã nhận/saved, có thể chưa bắt đầu.
- `RUNNING`: có owner, có thể chưa commit.
- `SUCCEEDED`: effect xác định đã commit theo contract.
- `FAILED_RETRYABLE`: có thể retry.
- `FAILED_TERMINAL`: không tự retry, cần policy/điều tra.
- `UNKNOWN`: không biết external effect đã thực hiện hay chưa; cần reconcile trước khi thử lại mù quáng.

Đặt tên state rõ ràng hơn việc có một cột boolean `processed=true/false`, nhất là khi hệ thống có nhiều side effects.

## 5. Hai phản mẫu cần tránh

### 5.1. Check-then-act bằng SELECT không nguyên tử

```java
// PHẢN MẪU: hai worker có thể cùng thấy chưa tồn tại.
if (!markerRepository.existsById(operationId)) {
    creditWallet(walletId, amount);
    markerRepository.save(new Marker(operationId));
}
```

Timeline race:

```text
Worker A                    Worker B
SELECT marker: absent
                            SELECT marker: absent
credit wallet +100
                            credit wallet +100
INSERT marker
                            INSERT marker
                             (có thể conflict quá muộn)
```

Ngay cả khi bảng marker có unique constraint, nếu business effect đã chạy **ngoài transaction chung** trước khi insert, unique violation cuối cùng không hoàn tác được email/payments hay mutation đã commit trong transaction khác.

**Giải pháp:** DB phải đóng vai trò **trọng tài nguyên tử** (unique insert/conditional update/lock) trước khi một worker được quyền áp dụng effect, đồng thời bao trùm marker + effect bằng một transaction khi effect nằm cùng DB.

### 5.2. Ghi DONE sai thời điểm

**Marker commit trước effect:**

```text
INSERT processed_jobs(status=DONE)
COMMIT
-- crash
UPDATE business state chưa xảy ra

Retry thấy DONE → skip; business effect bị mất.
```

**Effect commit trước marker:**

```text
UPDATE business state
COMMIT
-- crash
INSERT processed_jobs(status=DONE) chưa xảy ra

Retry không thấy marker → áp dụng effect lần nữa.
```

Nếu marker và business mutation **cùng nằm trong một database**, đưa chúng vào **cùng một transaction**. Với external systems, không thể mặc nhiên gom vào cùng transaction DB; cần giải pháp ở phần outbox/idempotent downstream.

## 6. Pattern A — cùng database, transaction nguyên tử là lựa chọn đơn giản nhất

Giả sử việc duy nhất là **cộng tiền vào một wallet trong PostgreSQL**. Ta có thể thiết kế:

1. Kiểm tra input, xác định `(tenant_id, operation_id)` và fingerprint ổn định.
2. Mở transaction.
3. Chèn marker với unique constraint bằng `INSERT ... ON CONFLICT DO NOTHING`.
4. Nếu **chèn được 1 dòng**: worker này có quyền áp dụng mutation trong transaction đó; update wallet, lưu kết quả, commit.
5. Nếu **chèn 0 dòng**: một operation đó đã có marker. Kiểm tra fingerprint; nếu giống, replay kết quả đã lưu; nếu khác, trả conflict. Không update wallet.
6. Nếu exception trước commit: rollback **cả marker và mutation**, cho phép lần thử sau thực hiện lại.
7. Sau commit mới ack transport khi contract yêu cầu.

### 6.1. PostgreSQL schema mẫu

```sql
CREATE TABLE wallets (
    tenant_id      VARCHAR(80) NOT NULL,
    wallet_id      VARCHAR(80) NOT NULL,
    balance_minor  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, wallet_id)
);

CREATE TABLE processed_jobs (
    tenant_id          VARCHAR(80) NOT NULL,
    operation_id       VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(256) NOT NULL,
    result_text        TEXT,
    completed_at       TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, operation_id)
);
```

Đây là **PostgreSQL DDL**. Dấu mốc dùng cùng database với ví để transaction có thể commit/rollback cùng nhau.

### 6.2. Claim atomically

```sql
INSERT INTO processed_jobs (
    tenant_id, operation_id, request_fingerprint
)
VALUES (:tenant_id, :operation_id, :fingerprint)
ON CONFLICT (tenant_id, operation_id) DO NOTHING;
```

Trong `JdbcTemplate.update(...)`, số hàng tác động là **1** khi insert thành công, **0** khi trùng key và `DO NOTHING`. PostgreSQL unique constraint làm trọng tài dưới concurrent insert, thay cho check-then-act ở application.

**Điểm tinh tế:** nếu worker A vừa insert marker **chưa commit**, worker B insert cùng key có thể phải đợi A commit/rollback theo cơ chế lock/index của database. Nếu A rollback, B có thể insert; nếu A commit, B nhận conflict. Không suy từ một SELECT snapshot trước đó mà biết chắc ai sở hữu marker.

### 6.3. Cùng transaction bao trùm effect và result

```sql
BEGIN;

INSERT INTO processed_jobs (
    tenant_id, operation_id, request_fingerprint
)
VALUES ('acme', 'OP-9', 'W1|100')
ON CONFLICT (tenant_id, operation_id) DO NOTHING;

-- Chỉ nếu insert thực sự tạo 1 marker:
UPDATE wallets
SET balance_minor = balance_minor + 100
WHERE tenant_id = 'acme' AND wallet_id = 'W1';

UPDATE processed_jobs
SET result_text = 'creditedMinor=100',
    completed_at = CURRENT_TIMESTAMP
WHERE tenant_id = 'acme' AND operation_id = 'OP-9';

COMMIT;
```

Các câu trên là **pseudocode transaction điều phối ở application**, không phải một script SQL đủ an toàn nếu copy chạy nguyên khối, vì SQL thuần trong đoạn chưa kiểm tra affected-row count và chưa có nhánh replay. **Code Spring hoàn chỉnh ở phần 8** thực hiện nhánh điều kiện đó.

### 6.4. Định nghĩa chính xác bảo đảm

Trong phạm vi **wallet + marker trên cùng PostgreSQL database**, pattern bảo đảm một operation key hợp lệ không cộng tiền hai lần do các worker/replica cùng tranh chấp, miễn là:

- Mọi đường cập nhật tương ứng đều đi qua cùng key/unique constraint/transaction.
- Schema và fingerprint thực thi đúng contract.
- Không có side effect khác nằm ngoài transaction mà lại được coi là “exactly-once”.
- Transaction manager thật sự bao trùm các lệnh trên.
- Isolation/locking và việc xử lý serialization/deadlock retry được thiết kế phù hợp.

Nó **không** bảo đảm hệ thống Kafka, email và payment gateway cũng exactly-once chỉ vì số dư trong bảng wallet chính xác.

## 7. Pattern B — một job dài, cần claim/lease/generation

Với việc kéo dài nhiều phút, giữ một DB transaction mở từ đầu tới cuối thường không phù hợp. Cần **claim ngắn**, **lease**, **heartbeat**, **reclaim** và **fencing**.

Ví dụ hai worker cạnh tranh:

```text
T0: Worker A claim OP-9, generation=7, lease_until=T+30s.
T1: A bị pause/GC lâu; không heartbeat.
T2: Lease của A hết hạn.
T3: Worker B reclaim OP-9, generation=8.
T4: A tỉnh lại và muốn ghi kết quả cũ.
T5: Chỉ B/generation=8 còn được commit effect hợp lệ.
```

Không đủ nếu chỉ dựa vào `lease_until`. Worker A có thể tiếp tục chạy **sau khi lease hết hạn**. Vì vậy cần một **fencing token tăng đơn điệu**, được kiểm tra tại **nơi áp dụng effect**.

### 7.1. Schema job state mở rộng

```sql
CREATE TABLE work_items (
    tenant_id       VARCHAR(80) NOT NULL,
    operation_id    VARCHAR(128) NOT NULL,
    request_hash    VARCHAR(128) NOT NULL,
    status          VARCHAR(24) NOT NULL,
    attempt         INTEGER NOT NULL DEFAULT 0,
    generation      BIGINT NOT NULL DEFAULT 0,
    lease_owner     VARCHAR(128),
    lease_until     TIMESTAMPTZ,
    result_text     TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, operation_id),
    CONSTRAINT work_status_ck CHECK (
        status IN ('READY', 'RUNNING', 'SUCCEEDED',
                   'FAILED_RETRYABLE', 'FAILED_TERMINAL')
    )
);
```

Cột `generation` nên được cập nhật **nguyên tử trong DB** khi owner mới claim; không lấy timestamp client làm fencing token vì clock các máy có thể lệch.

### 7.2. Claim bằng conditional UPDATE

```sql
UPDATE work_items
SET status       = 'RUNNING',
    attempt      = attempt + 1,
    generation   = generation + 1,
    lease_owner  = :worker_id,
    lease_until  = CURRENT_TIMESTAMP + INTERVAL '30 seconds',
    updated_at   = CURRENT_TIMESTAMP
WHERE tenant_id = :tenant_id
  AND operation_id = :operation_id
  AND (
      status IN ('READY', 'FAILED_RETRYABLE')
      OR (status = 'RUNNING' AND lease_until < CURRENT_TIMESTAMP)
  )
RETURNING generation;
```

Đây là **PostgreSQL SQL fragment** với named parameters do application bind, không phải SQL trực tiếp với dấu hai chấm trong psql. `UPDATE ... WHERE ... RETURNING` nguyên tử; nếu không có hàng được cập nhật, worker không nhận được claim hợp lệ. Nên dùng thời gian database thay vì so thời gian trên mỗi JVM.

### 7.3. Heartbeat phải kiểm tra owner + generation

```sql
UPDATE work_items
SET lease_until = CURRENT_TIMESTAMP + INTERVAL '30 seconds',
    updated_at  = CURRENT_TIMESTAMP
WHERE tenant_id = :tenant_id
  AND operation_id = :operation_id
  AND status = 'RUNNING'
  AND lease_owner = :worker_id
  AND generation = :generation
  AND lease_until > CURRENT_TIMESTAMP;
```

Nếu affected rows = 0, worker phải giả định đã mất quyền và **dừng áp dụng tác dụng phụ mới**. Điều này không tự undo những tác dụng đã commit.

### 7.4. Kết thúc bằng conditional state transition

```sql
UPDATE work_items
SET status      = 'SUCCEEDED',
    result_text = :result,
    lease_owner = NULL,
    lease_until = NULL,
    updated_at  = CURRENT_TIMESTAMP
WHERE tenant_id = :tenant_id
  AND operation_id = :operation_id
  AND status = 'RUNNING'
  AND lease_owner = :worker_id
  AND generation = :generation;
```

Affected rows = 1 có nghĩa một update phù hợp owner/version đã được ghi; 0 có nghĩa stale/mất quyền/trạng thái khác. Nhưng nếu business effect đã thực hiện **ở database khác** trước lệnh này, affected rows = 0 **không làm nó biến mất**.

**Điều kiện an toàn mạnh:** đối với thay đổi cùng database, ghép **fence check + business write + terminal marker** trong một transaction/conditional operation. Với downstream bên ngoài, downstream phải hỗ trợ idempotency/fencing hoặc bạn phải chấp nhận trạng thái `UNKNOWN` và reconcile.

### 7.5. Không giữ lock DB xuyên suốt I/O dài

Một job có thể chờ remote API 2 phút. Đừng mở transaction với row lock 2 phút chỉ để “chặn người khác”. Hãy claim/commit nhanh, làm công việc độc lập, heartbeat có giới hạn và chỉ apply effect ở commit boundary có kiểm tra token phù hợp.

Nếu đòi hỏi strict single-owner cho một thiết bị hay payment call, phải xem downstream có hỗ trợ fencing/operation ID không. Lease chỉ giúp bầu owner, không đơn phương ngăn owner cũ gây external side effect.

## 8. Ví dụ hoàn chỉnh Spring Boot Java 17 + PostgreSQL: cùng key chỉ cộng tiền một lần

Phần này là **project chạy được** bằng Spring Boot **3.3.5**, JDK **17+**, Maven và PostgreSQL 16. Nó sử dụng Spring `@Transactional`, `JdbcTemplate` và `INSERT ... ON CONFLICT DO NOTHING`.

Project minh họa nguyên tắc:

- Một `operationId` ổn định qua hai lần gọi.
- Một unique marker với fingerprint.
- Cộng 25 đơn vị tiền nhỏ (minor units) **một lần**.
- Retry trả lại cùng result.
- Dùng cùng key nhưng đổi amount sẽ bị từ chối.
- Marker và wallet mutation nằm trong **một PostgreSQL transaction**.
- Dùng `UUID.randomUUID()` chỉ để tạo **một operation mới cho mỗi lần chạy demo**, không dùng để thay key giữa retry của cùng operation.

### 8.1. Cấu trúc project

```text
idempotent-credit-demo/
├── pom.xml
└── src/main/
    ├── java/demo/
    │   ├── CreditDemoApplication.java
    │   └── CreditService.java
    └── resources/
        ├── application.properties
        └── schema.sql
```

### 8.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>demo</groupId>
    <artifactId>idempotent-credit-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
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

### 8.3. Tệp src/main/resources/application.properties

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/demo
spring.datasource.username=demo
spring.datasource.password=demo

# Với PostgreSQL, yêu cầu Boot chạy schema.sql.
spring.sql.init.mode=always

# Demo không cần HTTP server.
spring.main.web-application-type=none
```

Đây là cấu hình **local demo**; không lưu password production trong repository. Với môi trường thật dùng secret manager/env, migrations (Flyway/Liquibase) và pool configuration phù hợp.

### 8.4. Tệp src/main/resources/schema.sql

```sql
CREATE TABLE IF NOT EXISTS wallets (
    tenant_id      VARCHAR(80) NOT NULL,
    wallet_id      VARCHAR(80) NOT NULL,
    balance_minor  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, wallet_id)
);

CREATE TABLE IF NOT EXISTS processed_jobs (
    tenant_id           VARCHAR(80) NOT NULL,
    operation_id        VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(256) NOT NULL,
    result_text         TEXT,
    completed_at        TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, operation_id)
);

INSERT INTO wallets(tenant_id, wallet_id, balance_minor)
VALUES ('tenant-A', 'wallet-1', 0)
ON CONFLICT (tenant_id, wallet_id) DO NOTHING;
```

`spring.sql.init.mode=always` cho phép Boot khởi tạo schema khi chạy. Dùng `IF NOT EXISTS` trong demo để chạy lại với DB cũ; ở production, dùng migrations có kiểm soát thay vì âm thầm bỏ qua schema drift.

### 8.5. Tệp src/main/java/demo/CreditService.java

```java
package demo;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditService {
    private final JdbcTemplate jdbc;

    public CreditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public String credit(
            String tenantId,
            String operationId,
            String walletId,
            long amountMinor) {

        if (amountMinor <= 0 ||
                !walletId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("invalid request");
        }

        String fingerprint = walletId + "|" + amountMinor;

        int inserted = jdbc.update("""
            INSERT INTO processed_jobs
                (tenant_id, operation_id, request_fingerprint)
            VALUES (?, ?, ?)
            ON CONFLICT (tenant_id, operation_id) DO NOTHING
            """, tenantId, operationId, fingerprint);

        if (inserted == 0) {
            Map<String, Object> existing = jdbc.queryForMap("""
                SELECT request_fingerprint, result_text
                FROM processed_jobs
                WHERE tenant_id = ? AND operation_id = ?
                """, tenantId, operationId);

            String oldFingerprint =
                    (String) existing.get("request_fingerprint");
            if (!fingerprint.equals(oldFingerprint)) {
                throw new IllegalArgumentException(
                        "same key, different payload");
            }

            String result = (String) existing.get("result_text");
            if (result == null) {
                throw new IllegalStateException(
                        "marker has no committed result");
            }
            return result;
        }

        int updated = jdbc.update("""
            UPDATE wallets
            SET balance_minor = balance_minor + ?
            WHERE tenant_id = ? AND wallet_id = ?
            """, amountMinor, tenantId, walletId);

        if (updated != 1) {
            throw new IllegalArgumentException("wallet not found");
        }

        String result = "creditedMinor=" + amountMinor;

        jdbc.update("""
            UPDATE processed_jobs
            SET result_text = ?, completed_at = CURRENT_TIMESTAMP
            WHERE tenant_id = ? AND operation_id = ?
            """, result, tenantId, operationId);

        return result;
    }
}
```

**Vì sao có tính nguyên tử?**

- `credit(...)` là **public method của Spring-managed service** với `@Transactional`; bean khác gọi nó qua proxy.
- `JdbcTemplate` dùng cùng transaction-bound database connection trong Spring Boot JDBC.
- Insert marker, update wallet, update result nằm trong **một transaction**. Không có transaction commit riêng giữa các câu.
- Nếu fail trong các bước chưa commit, Spring rollback với runtime exception, cả marker lẫn update wallet đều không có hiệu lực.
- Nếu insert trùng key và fingerprint đúng, không update wallet; trả kết quả đã lưu từ lần thành công.
- Nếu insert trùng key nhưng fingerprint khác, ném exception để người gọi xử lý conflict, không thực thi lại.
- Unique index của PK là bảo vệ thực tế giữa **nhiều JVM** cùng trỏ một PostgreSQL database.
- Không gửi email/HTTP/payment bên trong transaction và coi chúng cũng rollback được.

**Giới hạn fingerprint trong demo:** để đơn giản, `walletId` bị giới hạn ký tự không chứa `|` và amount là `long`. Production cần canonical representation có schema/version hoặc hash cryptographic ổn định, áp dụng input validation đầy đủ và không tạo collision bằng các phép nối chuỗi mơ hồ.

### 8.6. Tệp src/main/java/demo/CreditDemoApplication.java

```java
package demo;

import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class CreditDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditDemoApplication.class, args);
    }

    @Bean
    CommandLineRunner run(CreditService service, JdbcTemplate jdbc) {
        return args -> {
            String tenant = "tenant-A";
            String wallet = "wallet-1";
            String jobId = UUID.randomUUID().toString();

            Long before = jdbc.queryForObject(
                    "SELECT balance_minor FROM wallets " +
                    "WHERE tenant_id=? AND wallet_id=?",
                    Long.class, tenant, wallet);

            String first =
                    service.credit(tenant, jobId, wallet, 25);
            String replay =
                    service.credit(tenant, jobId, wallet, 25);

            boolean conflictDetected = false;
            try {
                service.credit(tenant, jobId, wallet, 26);
            } catch (IllegalArgumentException expected) {
                conflictDetected = true;
            }

            Long after = jdbc.queryForObject(
                    "SELECT balance_minor FROM wallets " +
                    "WHERE tenant_id=? AND wallet_id=?",
                    Long.class, tenant, wallet);

            Long markerCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM processed_jobs " +
                    "WHERE tenant_id=? AND operation_id=?",
                    Long.class, tenant, jobId);

            System.out.println("same-result=" +
                    first.equals(replay));
            System.out.println("credited-once=" +
                    (after - before == 25));
            System.out.println("conflict-detected=" +
                    conflictDetected);
            System.out.println("marker-count=" + markerCount);
        };
    }
}
```

Ở đây `jobId` mới được tạo **một lần cho một demo operation**; cả lần đầu, retry và thử đổi amount đều dùng lại **cùng** `jobId`. Mỗi lần chạy demo từ đầu tạo **operation khác**, nên wallet có thể tăng tiếp 25 theo mỗi lần chạy. Kết quả kiểm tra dùng chênh lệch số dư chứ không giả định database luôn trống.

### 8.7. Khởi chạy PostgreSQL và ứng dụng

Ví dụ dùng Docker có sẵn, không trùng container/port:

```bash
docker run --rm -d --name idempotent-credit-db \
  -e POSTGRES_USER=demo \
  -e POSTGRES_PASSWORD=demo \
  -e POSTGRES_DB=demo \
  -p 5432:5432 postgres:16

docker exec idempotent-credit-db pg_isready -U demo -d demo
```

Đợi lệnh `pg_isready` báo server nhận kết nối trước khi chạy Maven. Trong thư mục project:

```bash
mvn -q spring-boot:run
```

Các dòng do `CommandLineRunner` in khi không có tác nhân khác cùng sửa ví trong lúc demo:

```text
same-result=true
credited-once=true
conflict-detected=true
marker-count=1
```

Spring Boot có thể in thêm startup logs. Nếu port 5432 đã được sử dụng, cấu hình port khác đồng bộ với `spring.datasource.url`, không chạy chồng lên PostgreSQL production.

Để dừng database demo:

```bash
docker stop idempotent-credit-db
```

Vì container được tạo với `--rm` và không mount volume, dữ liệu demo sẽ bị mất sau khi container bị xóa. Đây chỉ là ví dụ local; **production marker phải được lưu bền vững**.

### 8.8. Tại sao không dùng một transaction riêng để insert marker?

Nếu có `@Transactional(REQUIRES_NEW)` chỉ để commit marker trước, rồi business mutation chạy transaction khác, có crash window như phần 5. Khi đó marker có thể tồn tại mà mutation không xảy ra. Với mutation nội bộ cùng DB, cách đúng thường là **marker + mutation + result trong cùng transaction**.

### 8.9. Nếu hai replicas gọi cùng jobId đồng thời?

Cả hai có thể bắt đầu cùng lúc. Nhưng **unique constraint** trên `(tenant_id, operation_id)` quyết định một winner; loser không cộng ví. Test concurrency thực nên dùng hai connections và nhiều threads/process, không chỉ gọi tuần tự như demo.

### 8.10. Một giới hạn quan trọng của ví dụ

Demo xử lý **transaction DB ngắn**; nó không làm payment gateway hay email exactly-once. Với job dài có lease/retry hoặc external effects, cần pattern phức tạp hơn. Không mở một DB transaction dài chỉ để giữ quyền xử lý trong lúc chờ mạng.

## 9. Ví dụ Java 17+ thuần: hai thread nhận cùng job nhưng chỉ một handler được chạy

Để nhìn rõ tranh chấp trong một JVM, có thể dùng `ConcurrentHashMap.computeIfAbsent` như một phép quyết định nguyên tử theo key. Đây là **mô hình học tập trong memory**, không thay thế database unique constraint ở nhiều pods.

Tệp `ConcurrentDuplicateDemo.java`:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentDuplicateDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, String> results =
                new ConcurrentHashMap<>();
        AtomicInteger effects = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        Callable<String> command = () -> {
            start.await();
            return results.computeIfAbsent(
                    "tenant-A:job-17",
                    key -> {
                        effects.incrementAndGet();
                        return "creditedMinor=25";
                    });
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(command);
            Future<String> b = pool.submit(command);

            start.countDown();

            String first = a.get(5, TimeUnit.SECONDS);
            String second = b.get(5, TimeUnit.SECONDS);

            System.out.println("same-result=" +
                    first.equals(second));
            System.out.println("effects=" + effects.get());
            System.out.println("markers=" + results.size());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac ConcurrentDuplicateDemo.java
java ConcurrentDuplicateDemo
```

Output:

```text
same-result=true
effects=1
markers=1
```

**Giải thích:** `computeIfAbsent` trên một `ConcurrentHashMap` cung cấp phép tính mapping nguyên tử cho key trong context JVM đó; hai threads cùng key nhìn cùng value sau khi mapping thành công. Nhưng có ba giới hạn:

1. JVM restart thì map/marker mất.
2. Pod A và pod B có **hai map khác nhau**, không có arbitration liên tiến trình.
3. `effects.incrementAndGet()` mô phỏng một effect local; nếu mapping function gọi payment API rồi crash trước khi map lưu thành công, đây **không phải** transaction và không rollback external effect.

Do đó ví dụ dùng để hiểu **race trên một key**, không phải mẫu production để đặt side effect bên trong `computeIfAbsent`.

## 10. Ví dụ Java 17+: marker và effect phải commit cùng nhau

Java thuần không có transaction database, nhưng ta có thể mô phỏng một critical section để hiểu rằng **một attempt lỗi trước commit không được để lại marker DONE**.

Tệp `MarkerRollbackModelDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class MarkerRollbackModelDemo {
    private static final Set<String> completed = new HashSet<>();
    private static int totalMinor;

    private static synchronized boolean apply(
            String operationId, int amount, boolean failBeforeCommit) {

        if (completed.contains(operationId)) {
            return false;
        }
        if (failBeforeCommit) {
            throw new IllegalStateException("simulated failure");
        }

        // Chỉ là mô hình một critical section trong JVM.
        totalMinor += amount;
        completed.add(operationId);
        return true;
    }

    public static void main(String[] args) {
        try {
            apply("OP-1", 50, true);
        } catch (IllegalStateException expected) {
            System.out.println("first-attempt-failed=true");
        }

        System.out.println("marked-after-failure=" +
                completed.contains("OP-1"));
        System.out.println("retry-applied=" +
                apply("OP-1", 50, false));
        System.out.println("duplicate-applied=" +
                apply("OP-1", 50, false));
        System.out.println("total-minor=" + totalMinor);
    }
}
```

Chạy:

```bash
javac MarkerRollbackModelDemo.java
java MarkerRollbackModelDemo
```

Output:

```text
first-attempt-failed=true
marked-after-failure=false
retry-applied=true
duplicate-applied=false
total-minor=50
```

**Cảnh báo:** `synchronized` không phải database transaction. Nếu chương trình bị crash giữa cập nhật total và marker, trạng thái memory biến mất hoặc thiếu bền vững; nếu effect ra external service thì không thể rollback qua lock Java. Ví dụ chỉ minh họa **logic của commit cùng nhau**, còn pattern dùng thật phải có cùng DB transaction như phần 8.

## 11. Ví dụ Java 17+: fencing token chặn worker đã mất quyền

Lease ngăn worker mới nhận job quá sớm, nhưng **không ngăn worker cũ tỉnh lại**. Cần một version/generation làm fencing token và kiểm tra nó tại điểm áp dụng effect.

Tệp `FencingGenerationDemo.java`:

```java
import java.util.concurrent.atomic.AtomicReference;

public class FencingGenerationDemo {
    record State(long generation, String owner, boolean completed) {}

    static long claim(
            AtomicReference<State> store, String newOwner) {
        while (true) {
            State current = store.get();
            State next = new State(
                    current.generation() + 1,
                    newOwner,
                    false);

            if (store.compareAndSet(current, next)) {
                return next.generation();
            }
        }
    }

    static boolean complete(
            AtomicReference<State> store,
            String owner,
            long generation) {

        while (true) {
            State current = store.get();

            if (current.completed() ||
                    current.generation() != generation ||
                    !current.owner().equals(owner)) {
                return false;
            }

            State next = new State(
                    current.generation(),
                    current.owner(),
                    true);

            if (store.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    public static void main(String[] args) {
        AtomicReference<State> store =
                new AtomicReference<>(new State(0, "-", false));

        long oldGeneration = claim(store, "worker-A");
        long newGeneration = claim(store, "worker-B");

        System.out.println("old-generation=" + oldGeneration);
        System.out.println("new-generation=" + newGeneration);
        System.out.println("stale-completion=" +
                complete(store, "worker-A", oldGeneration));
        System.out.println("current-completion=" +
                complete(store, "worker-B", newGeneration));
        System.out.println("completed=" + store.get().completed());
    }
}
```

```bash
javac FencingGenerationDemo.java
java FencingGenerationDemo
```

Output:

```text
old-generation=1
new-generation=2
stale-completion=false
current-completion=true
completed=true
```

Trong demo, `AtomicReference.compareAndSet` thực hiện chuyển state nguyên tử **trong một JVM**. Trong production có nhiều pods, cần generation bền vững và atomic compare-and-update tại **database hoặc downstream**. Nếu worker A đã gửi email **trước khi** gọi `complete(...)`, việc bị từ chối completion không thu hồi email. Đó là lý do fencing phải gắn với nơi effect được ghi, hoặc có downstream idempotency.

## 12. API idempotency: cùng key phải cho cùng phản hồi

Một HTTP endpoint nhận lệnh tạo/chi tiền có thể hỗ trợ:

```text
POST /wallets/W1/credits
Idempotency-Key: OP-001
Content-Type: application/json

{"amountMinor":100}
```

Khi server xử lý thành công lần đầu, nó lưu kết quả theo key/payload. Các retry cùng key và cùng payload trả lại **cùng kết quả nghiệp vụ**, không tạo một giao dịch thứ hai.

### 12.1. Thiết kế response

| Tình huống | Hành vi khuyến nghị |
| --- | --- |
| Key mới, payload hợp lệ | Claim và xử lý, lưu kết quả |
| Key cũ, payload giống, đã SUCCEEDED | Replay kết quả cũ; không thêm effect |
| Key cũ, payload khác | Conflict; không áp dụng |
| Key cũ, đang RUNNING | Trả pending/in-progress hoặc chờ theo policy |
| Key cũ, retryable failure | Cho retry nếu state/lease hợp lệ |
| Key cũ, permanent failure | Replay/return error theo hợp đồng API |
| Key đã hết retention | Cần policy rõ: key có được reuse hay sẽ tạo effect mới? |
| Không có key cho mutation cần idempotency | Từ chối hoặc sinh business operation ID một lần và trao lại cho client |

Không có một mã HTTP duy nhất bắt buộc cho mọi API. `409 Conflict` hợp lý cho cùng key khác payload; `202 Accepted` có thể hợp lý khi job đang xử lý; `200/201` hoặc response lưu lại có thể hợp lý khi replay. Quan trọng là contract ổn định và client hiểu nó.

### 12.2. Client phải reuse key khi timeout

**Sai:** mỗi lần retry POST lại tạo một `UUID.randomUUID()` mới. **Đúng:** tạo key khi người dùng phát sinh một business intent, lưu key trong trạng thái request, retry với đúng key đó đến khi có terminal response hoặc có workflow reconciliation.

Nếu có một hành động mới hợp lệ, tạo key mới. Một key không nên được tái sử dụng cho hai intent khác nhau.

### 12.3. Lưu response để replay an toàn

Marker có thể lưu response code/body hợp lệ, canonical result hoặc ID của resource đã tạo. Với tác vụ bất đồng bộ, trả `jobId`/status URI để client polling; không nhất thiết lưu toàn bộ response HTTP nhạy cảm trong marker.

Nếu response chứa token/PII, cần retention và encryption/redaction; không lưu indiscriminately.

## 13. Lỗi “mark as processed” khi job còn đang chạy

Một biến thể phổ biến:

```java
// PHẢN MẪU:
if (markerStore.putIfAbsent(jobId, true) == null) {
    runLongJob();
}
```

Nếu `runLongJob()` lỗi hoặc JVM chết, marker đã thành true và mọi retry bị skip vĩnh viễn. Đặc biệt nguy hiểm khi map được lưu bền vững nhưng không có state machine/recovery.

Với job dài:

1. Tạo `READY` marker (có key và fingerprint).
2. Claim nguyên tử thành `RUNNING` với lease/generation.
3. Thực hiện work có timeout, heartbeat và cancellation check.
4. Chỉ chuyển `SUCCEEDED` khi effect có thể xác minh đã commit.
5. Nếu fail retryable, giải phóng lease/đặt backoff; nếu fail terminal, đánh dấu rõ.
6. Nếu worker chết, lease hết hạn cho phép reclaim đúng policy.
7. Stale owner phải bị chặn khi commit hoặc gửi downstream effect.

`READY` cũng có thể được tạo cùng business transaction ở outbox hoặc API nhận việc. Không cần giữ transaction mở trong toàn bộ quá trình xử lý.

### 13.1. Status không phải boolean thành công

Một field `processed=true` thường không đủ trả lời:

- Đã nhận nhưng chưa bắt đầu?
- Đã chạy xong nhưng chưa commit?
- Đã commit DB nhưng chưa gửi email?
- Đã gửi request ra payment gateway nhưng chưa nhận response?
- Có thể retry hay cần đối soát?
- Worker mới có được reclaim không?

State machine phải phản ánh những khác biệt này nếu chúng quan trọng.

## 14. Inbox, outbox và tác dụng phụ bên ngoài

### 14.1. Inbox: nhớ rằng event đã được áp dụng

Ở phía consumer, có thể dùng bảng `inbox` với khóa duy nhất:

```sql
CREATE TABLE consumer_inbox (
    consumer_name VARCHAR(80) NOT NULL,
    tenant_id     VARCHAR(80) NOT NULL,
    event_id      VARCHAR(128) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, tenant_id, event_id)
);
```

Một consumer phải xử lý idempotently **trong phạm vi của chính nó**. Nếu hai consumers khác nhau cùng cần xử lý một event cho hai mục đích khác nhau, không nên dùng một marker global khiến consumer thứ hai bị skip. Vì vậy `consumer_name` là một phần của key.

Nếu effect là update DB cùng datasource, insert inbox marker và mutation trong **cùng transaction**. Khi retry trùng, unique conflict trở thành no-op. Khi transaction rollback, marker cũng rollback để lần retry hợp lệ có thể chạy.

### 14.2. Outbox: không đánh mất event sau commit

Một producer thay đổi aggregate và cần publish event có thể ghi:

```text
BEGIN TRANSACTION:
   UPDATE order
   INSERT outbox(event_id, operation_id, payload, state=PENDING)
COMMIT

Outbox relay:
   Đọc event chưa phát
   Publish tới broker
   Ghi nhận delivery/retry
```

Đây là **pseudocode kiến trúc**. Vì order mutation + outbox row cùng database transaction, hệ thống không rơi vào tình trạng “order commit rồi không có event record để khôi phục”. Nhưng relay có thể publish rồi chết trước khi đánh dấu đã gửi → broker nhận lại event → consumer cần inbox/idempotency.

### 14.3. Không có transaction nguyên tử giữa DB và email

Giả sử:

```text
1. INSERT processed_jobs(DONE) vào PostgreSQL
2. COMMIT
3. Gửi email
```

Crash giữa 2 và 3 khiến email không gửi nhưng marker DONE. Đảo thứ tự:

```text
1. Gửi email
2. Crash
3. Chưa INSERT processed_jobs(DONE)
```

Retry có thể gửi lần nữa. PostgreSQL transaction không rollback email đã rời hệ thống.

Cách thiết kế tốt hơn:

- Lưu outbox event/notification intent trong cùng transaction business.
- Relay gửi với một **downstream idempotency key** nếu provider hỗ trợ.
- Lưu trạng thái gửi/ack và phân biệt `UNKNOWN`.
- Có retry policy, quota, dead-letter và reconciliation.
- Đừng tuyên bố “exactly-once email” nếu provider không có idempotency contract đủ mạnh.

### 14.4. Payment gateway và response UNKNOWN

Một request payment có thể được provider xử lý nhưng response mất. Retry **cùng operation key** nếu API provider có hỗ trợ idempotency; nếu không, query trạng thái giao dịch theo provider reference và reconcile trước khi gửi lệnh mới.

Một local DB marker không thể chứng minh provider chưa charge; chờ/timeout ở JVM chỉ là quan sát của phía client.

### 14.5. Kafka exactly-once không tự bao phủ side effect ngoài Kafka

Spring for Apache Kafka có hỗ trợ transactional processing và exactly-once semantics cho **consume → process → produce** trong phạm vi Kafka transactions khi cấu hình phù hợp. Tuy nhiên, phần “read/process” vẫn có thể lặp khi retry, và một external database, email, HTTP API không tự trở thành participant trong Kafka transaction.

Vì vậy dù có Kafka EOS, một consumer cập nhật database ngoài Kafka vẫn nên thiết kế inbox marker/unique operation ID hoặc cơ chế idempotency tại destination phù hợp.

## 15. Thứ tự commit và ACK quyết định điều gì?

Một consumer at-least-once thường cần:

```text
receive message
       |
validate idempotency key / payload
       |
BEGIN DB transaction
       |
atomic marker claim + business mutation
       |
COMMIT
       |
ACK broker / commit offset
```

Khi ACK/commit offset **sau** database commit, crash ở khoảng giữa có thể gây **redelivery**, nhưng marker giúp dedup an toàn. Nếu ACK **trước** database commit, crash có thể làm mất công việc vì broker coi message đã xử lý xong.

### 15.1. Ba trường hợp crash

| Vị trí crash | DB marker + effect | Broker ack | Hệ quả |
| --- | --- | --- | --- |
| Trước DB commit | Rollback/not committed | Chưa ack | Replay và xử lý lại |
| Sau DB commit, trước ack | Đã commit | Chưa ack | Replay, marker ngăn apply lần hai |
| Sau ack | Đã commit | Đã ack | Không cần replay bình thường |

**Cảnh báo:** bảng mô tả cùng-DB effect. Với external effect không có transaction chung, trạng thái có thể là UNKNOWN và cần chính sách riêng.

### 15.2. Offset không phải business operation ID

Một event Kafka có topic/partition/offset. Offset giúp xác định **một record tại log location đó**, nhưng:

- Cùng business operation có thể được publish thành **nhiều records**.
- Repartition hoặc republish sang topic khác làm offset thay đổi.
- Client HTTP có thể tạo cùng business operation không liên quan Kafka.
- Một record có thể yêu cầu nhiều consumer logic khác nhau.

Dùng `operationId` mang ý nghĩa nghiệp vụ và `eventId` ổn định nếu cần, không coi Kafka offset luôn là global idempotency key cho mọi workflow.

## 16. Spring @Transactional, proxy và @Async: ranh giới phải rõ

### 16.1. Đừng tin annotation khi self-invocation

`@Transactional` trong chế độ Spring proxy mặc định có hiệu lực khi gọi **qua proxy**. Nếu trong cùng class:

```java
public void receive(Message message) {
    processTransactionally(message); // this.processTransactionally()
}

@Transactional
public void processTransactionally(Message message) {
    // ...
}
```

Đây là **fragment phản mẫu**: self-invocation có thể không được transaction interceptor bắt, khiến marker và mutation không được bao trong transaction như bạn tưởng.

**Cách đúng thường là** tách một `@Service` transactional method và gọi qua bean được inject, như `CreditDemoApplication` gọi `CreditService` ở phần 8.

### 16.2. @Async không mang transaction của caller sang worker

Nếu controller/messaging listener có một transaction rồi gọi `@Async` worker, context transaction imperative thường gắn với thread caller và **không tự đi theo** worker thread. Worker có thể chạy trước commit/rollback của caller.

Pattern:

```java
@Service
public class JobWorker {
    private final ApplyOperationService applyService;

    public JobWorker(ApplyOperationService applyService) {
        this.applyService = applyService;
    }

    @Async("jobExecutor")
    public CompletableFuture<Void> handle(JobMessage job) {
        applyService.applyIdempotently(job);
        return CompletableFuture.completedFuture(null);
    }
}
```

Đây là **fragment**: `ApplyOperationService` là Spring bean khác có `@Transactional` cho phần marker + mutation; `JobMessage` là immutable DTO/record. Không truyền managed JPA entity/`EntityManager` qua thread. Executor, retry/error policy và dependencies cần khai báo trong project.

### 16.3. Rollback rules

Theo Spring transaction defaults, unchecked `RuntimeException` và `Error` thường dẫn tới rollback; checked exception mặc định có thể không, nếu không khai báo `rollbackFor` hoặc cấu hình rollback behavior tương ứng phiên bản.

Nếu “job thất bại” được biểu diễn bằng **return false** mà không ném exception/đánh dấu rollback, transaction có thể **commit marker** dù nghiệp vụ chưa hoàn tất. Định nghĩa rõ failure semantics: throw exception phù hợp, `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` khi có lý do, hoặc explicit state transition trong một transaction hợp lệ.

### 16.4. @TransactionalEventListener AFTER_COMMIT

Nếu công việc chỉ hợp lệ sau khi transaction nguồn commit, có thể dùng `@TransactionalEventListener(phase = AFTER_COMMIT)` để **dispatch** sau commit. Tuy nhiên in-memory listener không bền vững qua JVM crash. Job không được mất cần **transactional outbox** hoặc lưu job record cùng transaction nguồn.

### 16.5. Không để transaction quá dài

Không giữ DB transaction mở trong khi:

- Gọi API external có thể timeout nhiều phút.
- Chờ `Thread.sleep` hoặc retry/backoff.
- Đợi lease hết hạn.
- Xử lý số lượng lớn item không có checkpoint.

Transaction dài giữ connection/locks, tăng contention và làm khả năng recovery khó. Với job dài, chia thành **claim transaction**, **business chunk transactions** và **finalization transaction** có fencing/operation ID đúng nghĩa.

## 17. Key retention và vòng đời dấu mốc

Idempotency có **horizon**, không tự kéo dài vô hạn nếu bạn xóa marker.

### 17.1. Xóa marker quá sớm có thể tạo double effect

```text
T0: OP-1 đã áp dụng, marker được lưu.
T1: Marker bị xóa sau TTL ngắn.
T2: Một delayed retry của OP-1 đến.
T3: Hệ thống tưởng key mới và áp dụng lại.
```

TTL phải lớn hơn khoảng thời gian hệ thống có thể thực tế phát retry/redelivery, và phải xét khả năng client retry thủ công sau nhiều ngày. Với thanh toán/ledger quan trọng, có thể cần giữ dấu vết lâu hơn TTL của cache.

### 17.2. Tách hot cache khỏi durable evidence

Redis `SETNX`/TTL có thể hữu ích làm tầng giảm tải hoặc lock tạm, nhưng:

- Redis key TTL hết hạn là mất thông tin.
- Redis không tự commit atomically cùng SQL business mutation.
- Failover/network partition có thể làm ownership phức tạp.
- Local in-memory map càng không tồn tại qua process restart.

Với effects trong DB, dùng **unique constraint/transaction** ở authoritative store. Cache có thể tăng tốc kiểm tra nhưng không nên là **duy nhất nguồn sự thật** khi yêu cầu bền vững cao.

### 17.3. Retention theo nghiệp vụ

| Loại operation | Gợi ý retention |
| --- | --- |
| Cache refresh | Có thể ngắn; lặp không gây hậu quả lớn |
| Gửi notification không được spam | Dựa trên retry window và yêu cầu nghiệp vụ |
| Payment request | Dài hơn, có audit/reconciliation theo quy định |
| Ledger balance mutation | Nên giữ durable operation record/audit phù hợp |
| Inbox event với broker retention dài | Đủ bảo vệ delayed redelivery/replay |
| Batch run có version | Có thể dùng business run ID và generation để tách các lần hợp lệ |

Không áp đặt “24 giờ” cho mọi hệ thống. TTL của provider bên ngoài (nếu có) và TTL của marker nội bộ phải được đối chiếu.

## 18. Tính lặp an toàn với công việc có nhiều bước

Một job có thể gồm:

1. Cập nhật order.
2. Tính điểm thưởng.
3. Gửi email.
4. Gọi shipping API.

Không thể chỉ có một marker `DONE` chung mà không xác định các bước đã áp dụng:

```text
operationId=OP-77
order_update      SUCCEEDED
loyalty_points    SUCCEEDED
email_send        UNKNOWN
shipping_request  PENDING
```

Cần chọn mô hình:

- **Một DB transaction:** khi tất cả effects là các bảng trong cùng DB và có thể commit chung.
- **Saga/workflow state:** khi có nhiều dịch vụ và từng step cần retry/compensation.
- **Outbox per effect:** mỗi bước tạo một command/event có `operationId + stepId` riêng.
- **Idempotent consumer:** mỗi destination ghi marker theo đúng scope.
- **Reconciliation:** khi provider trả outcome không rõ.

**Step identity quan trọng:** một job có thể cần gửi hai loại email hợp lệ; nếu cả hai dùng cùng `operationId` và inbox scope không có `stepId`, marker của email đầu có thể chặn email thứ hai.

### 18.1. Idempotency và compensation khác nhau

Idempotency ngăn **cùng một effect lặp vô ý**. Compensation là một hành động nghiệp vụ mới để đảo/khắc phục effect cũ (ví dụ hoàn tiền). Compensation cũng cần **operationId riêng** và idempotency; không đổi cùng key của request gốc để mô phỏng rollback.

### 18.2. Kết quả “đã xử lý” có thể không còn là state mới nhất

Một retry của `OP-1` nên nhận lại kết quả của `OP-1`, không nhất thiết là trạng thái aggregate hiện tại đã được thay đổi bởi `OP-2` sau đó. Nếu client muốn “balance hiện tại”, cung cấp một query riêng. Đừng trộn idempotent command replay với fresh read để tạo response không nhất quán.

## 19. Bảo vệ tác dụng phụ cũ bằng version và fencing

Ngay cả khi một operation chỉ có một owner hợp lệ tại một thời điểm, worker cũ có thể giữ kết quả từ version cũ và ghi đè trạng thái mới. Ví dụ:

```text
A xử lý order revision 7.
B xử lý order revision 8 và commit mới hơn.
A hoàn tất muộn, muốn ghi status từ revision 7.
```

Nếu business invariant là latest-wins theo version, dùng conditional update:

```sql
UPDATE order_views
SET status = :incoming_status,
    source_version = :incoming_version
WHERE tenant_id = :tenant_id
  AND order_id = :order_id
  AND source_version < :incoming_version;
```

Affected rows = 1 khi version đến là mới hơn trạng thái đã lưu; 0 khi cũ/không phù hợp. Đây là **SQL fragment**, cần xử lý hàng chưa tồn tại, transaction và upsert race.

**Phân biệt:**

- **Idempotency key** chống **lặp cùng operation**.
- **Version/fencing token** chống **owner/event cũ ghi đè kết quả mới**.
- **Lease** phân phối quyền làm việc trong một khoảng thời gian.
- **Unique constraint** làm trọng tài cho các claim trùng.
- **Outbox/inbox** giúp recover delivery và nhận diện effect đã áp dụng.
- **Transaction** tạo atomicity bên trong một resource domain.

Một hệ thống có thể cần tất cả, nhưng không được coi chúng là các khái niệm đồng nghĩa.

## 20. Kiểm thử cạnh tranh và crash/retry

### 20.1. Unit test identity và fingerprint

- Cùng `tenantId + operationId` và payload canonical giống → replay.
- Cùng key nhưng amount/wallet khác → conflict.
- Hai tenants dùng cùng operationId → không chặn nhầm nếu scope là tenant.
- Hai attempts của cùng business operation → giữ same key, attemptId khác.
- Hai business operations hợp lệ trên cùng wallet → key khác, cả hai được áp dụng.

### 20.2. Integration test hai connections/threads

Với PostgreSQL thực:

1. Bắt đầu hai transaction độc lập với cùng `operationId`.
2. Dùng `CountDownLatch` điều phối để cả hai cạnh tranh insert marker.
3. Kiểm tra unique index/`ON CONFLICT DO NOTHING` phân xử.
4. Cho winner commit marker + wallet update.
5. Kiểm tra loser không update wallet.
6. Assert số dư tăng **một lần**, marker count là 1.
7. Lặp lại với winner rollback; loser hoặc retry khác có quyền xử lý.
8. Dùng test DB có isolation level tương đương production.

Không chỉ dùng `ConcurrentHashMap` unit test để tuyên bố hệ thống nhiều replica đúng.

### 20.3. Crash injection matrix

| Điểm tiêm crash | Kỳ vọng ở pattern cùng DB |
| --- | --- |
| Trước insert marker | Không effect, retry có quyền claim |
| Sau insert marker nhưng trước update | Transaction rollback, marker không còn |
| Sau update nhưng trước commit | Transaction rollback cả hai |
| Sau commit nhưng trước broker ack | Replay thấy marker, không cộng lần hai |
| Trong lúc duplicate insert đang chờ unique lock | Chỉ một transaction commit một marker/effect |
| Trong lúc query replay result | Kết quả đã commit phải đọc được |
| Sau gửi HTTP external trước lưu ack | Outcome UNKNOWN; reconcile/idempotency downstream |

### 20.4. Test lease expiry

- A claim generation=1.
- A bị giữ bằng latch cho tới khi lease expire.
- B claim generation=2 và apply effect.
- A release, cố commit với generation=1.
- Conditional update phải trả 0 cho A.
- Đảm bảo A không còn external effect nào bypass kiểm tra fence.

Nếu kiểm tra fence **sau khi** A gọi external API, test phải chỉ ra lỗ hổng thay vì đánh dấu pass.

### 20.5. Test @Transactional proxy và rollback

Gọi service qua Spring-managed bean, không qua `this`. Ném unchecked exception giữa marker insert và wallet update/commit, assert rollback cả hai. Test checked exception riêng với `rollbackFor` nếu ứng dụng có path đó.

### 20.6. Test expiry/replay window

Giả lập old event sau khi marker TTL hết hạn. Kiểm tra policy: operation được block bằng audit durable, hay có thể tái áp dụng? Đừng xóa marker production khi còn delayed retries và không có guard khác.

### 20.7. Test multi-replica và network partition

- Hai pods cùng nhận một operationId.
- Một pod mất kết nối tới DB rồi tái kết nối.
- Lease cũ hết hạn, owner mới claim.
- External API response timeout nhưng thực sự đã xử lý.
- Bản ghi retry đến sai thứ tự so với event mới.
- Một consumer group khác nhận cùng event với xử lý độc lập.
- Rebalance/rolling update gây redelivery giữa commit và ack.

Mục tiêu là chứng minh **invariant nghiệp vụ**, không chỉ “hai threads không ném exception”.

## 21. Metrics và logging cho idempotency

Theo dõi các tín hiệu:

| Metric / log | Ý nghĩa |
| --- | --- |
| `operations_claimed_total` | Key mới được nhận |
| `duplicate_requests_total` | Request trùng key/payload |
| `payload_conflicts_total` | Cùng key nhưng payload khác |
| `marker_insert_conflicts_total` | Cạnh tranh unique key |
| `operations_succeeded_total` | Business operation hoàn thành |
| `operations_failed_retryable_total` | Công việc có thể retry |
| `operations_failed_terminal_total` | Không tự retry |
| `leases_expired_total` | Owner mất lease |
| `fencing_rejections_total` | Stale worker bị từ chối |
| `unknown_external_effect_total` | Kết quả ngoài DB chưa rõ |
| `outbox_pending_age_seconds` | Sự kiện chờ publish quá lâu |
| `inbox_retention_age` | Dấu mốc sắp vượt retention window |

Log theo `operationId`, `attemptId`, `tenantId`, `generation`, `state` và trace context nhưng **tránh đưa operationId duy nhất thành label metric** vì tạo cardinality quá cao. Không log số thẻ, token, email/PII hoặc payload tài chính nguyên văn.

### 21.1. Cần phân biệt ba tỷ lệ

1. **Duplicate rate:** có bao nhiêu requests là replay?
2. **Conflict rate:** có bao nhiêu requests reuse key với payload khác?
3. **Recovery rate:** bao nhiêu attempts bị crash/reclaim rồi cuối cùng thành công?

Duplicate cao có thể bình thường khi broker redelivery do ack policy; conflict cao thường là vấn đề API/client; lease expirations cao có thể là worker timeout, pause hoặc quá tải.

## 22. Troubleshooting

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| Một job cộng tiền hai lần | Check-then-act, thiếu unique constraint | Dùng unique key + atomic transaction |
| Có marker DONE nhưng effect không xảy ra | Commit marker trước effect | Commit cùng DB transaction hoặc outbox state |
| Effect đã xảy ra nhưng marker không có | Commit effect trước marker | Cùng transaction; external effect cần reconciliation |
| Duplicate request bị xử lý lại sau restart | Marker chỉ trong memory | Dùng durable database/job store |
| Hai pods cùng xử lý | Local lock/ConcurrentHashMap mỗi pod riêng | DB atomic claim/lease/fencing |
| Retry bị skip mãi dù lần đầu thất bại | Marker dùng boolean thay state machine | READY/RUNNING/FAILED_RETRYABLE/lease recovery |
| Cùng key nhưng amount khác vẫn được chấp nhận | Không so fingerprint | Canonical payload fingerprint + conflict |
| Key cũ đến sau TTL và bị xử lý mới | Retention ngắn | Xem retry horizon, audit/durable dedupe |
| Worker cũ ghi đè worker mới | Chỉ dùng lease, thiếu fencing | Generation/conditional commit/downstream fence |
| Kafka báo EOS nhưng DB vẫn có duplicate | EOS không bao trùm external DB | Inbox/unique operation ID |
| ACK thành công nhưng DB chưa có effect | ACK trước DB commit | Commit DB trước ACK theo contract |
| Email gửi hai lần sau timeout | External side effect không idempotent | Provider idempotency/reconciliation |
| @Transactional không rollback | Self-invocation hoặc exception rules | Gọi qua proxy, rollback config/test |
| @Async job chạy trước commit | Worker có transaction riêng | AFTER_COMMIT/outbox nếu phụ thuộc commit |
| Markers/unique index quá lớn | Retention và indexing không có kế hoạch | Partition/archive theo policy |
| DB lock contention tăng | Nhiều requests cùng hot key | Throttle hot key, đo conflict/backoff |
| Replay trả dữ liệu khác lần đầu | Trả fresh state thay vì stored result | Lưu kết quả operation hoặc resource ID |
| Retry trên lỗi vĩnh viễn vô hạn | Thiếu state terminal/DLQ | Phân loại lỗi, bounded retry |

## 23. Bài tập thực hành

### Bài 1 — Phân biệt các loại ID

Một message có `messageId=M-50`, `operationId=OP-9`, `attemptId=A-1`. Broker redelivery thành `attemptId=A-2`; producer lại publish cùng business operation với `messageId=M-51`. Xác định key nào nên dùng để đảm bảo lệnh cộng tiền OP-9 chỉ được áp dụng một lần, và key nào phục vụ tracing/retry.

### Bài 2 — Chạy Java concurrency demo

Chạy `ConcurrentDuplicateDemo.java` bằng Java 17+, xác nhận:

```text
same-result=true
effects=1
markers=1
```

Sau đó thay `ConcurrentHashMap` bằng `HashMap` không đồng bộ trong một chương trình test riêng, giải thích vì sao check-then-act có race. Không dùng map Java này để thay DB transaction trong production.

### Bài 3 — Rollback marker và effect

Chạy `MarkerRollbackModelDemo.java`, xác nhận attempt lỗi không để lại marker, retry hợp lệ áp dụng một lần. Sau đó implement trên PostgreSQL/Spring để thấy rằng DB transaction, không phải `synchronized`, mới bảo vệ across replicas và crash.

### Bài 4 — Thực hành Spring Boot + PostgreSQL

Khởi chạy project `idempotent-credit-demo`. Gọi cùng key/payload hai lần, thử amount khác với key đó. Kiểm tra số dư, một marker và response được replay. Sau đó tạo hai clients đồng thời tranh cùng key; assert database chỉ cộng một lần.

### Bài 5 — Reclaim và fencing

Chạy `FencingGenerationDemo.java`, quan sát worker A bị từ chối khi B đã claim generation mới. Áp dụng conditional SQL UPDATE theo generation và chứng minh A không được ghi đè B. Thêm heartbeat và test lease expiration.

### Bài 6 — Crash sau commit trước ACK

Giả lập consumer: update DB + marker commit, sau đó process chết trước broker ACK. Cho broker phát lại cùng message. Kiểm chứng marker ngăn second effect nhưng consumer vẫn có thể ACK replay hợp lệ.

### Bài 7 — Outbox + inbox

Thiết kế một order transaction cùng outbox event. Relay có thể publish trùng do crash; consumer dùng inbox marker theo `consumer_name + event_id` và cập nhật DB trong cùng transaction. Mô tả trường hợp email provider không có idempotency API: đâu là trạng thái UNKNOWN và cách đối soát.

### Bài 8 — Retention

Một client có thể retry lệnh trong 7 ngày, còn message broker giữ và replay event tối đa 14 ngày. Đề xuất TTL/archival cho marker sao cho không xử lý lặp do xóa quá sớm. Giải thích khi nào audit ledger lâu dài phù hợp hơn TTL cache.

### Bài 9 — Multi-step workflow

Một job làm 3 bước: ghi order, thưởng điểm, gửi webhook. Thiết kế operation IDs cho từng step, terminal states và retry. Đảm bảo bước thưởng điểm không chạy lại khi webhook lỗi rồi retry.

### Bài 10 — Phân biệt idempotency và exactly-once

Viết ba câu khẳng định có điều kiện: (1) wallet update cùng PostgreSQL marker; (2) Kafka transactional read/process/write; (3) email gửi qua external provider. Với mỗi câu, nêu ranh giới exactly-once effect hoặc yêu cầu idempotency/reconciliation còn thiếu.

## 24. Checklist trước production

- [ ] Tôi biết duplicate/redelivery là tình huống thường gặp trong hệ phân tán.
- [ ] Tôi có stable operationId/jobId giữ nguyên qua retry.
- [ ] Tôi không dùng attemptId hoặc UUID mới ở mỗi retry làm idempotency key.
- [ ] Tôi có tenant/consumer/step scope phù hợp cho key.
- [ ] Tôi lưu hoặc so sánh fingerprint để chặn same key/different payload.
- [ ] Tôi không dùng `SELECT exists` + side effect + insert marker như một check-then-act không nguyên tử.
- [ ] Tôi có unique constraint hoặc conditional atomic claim tại authoritative store.
- [ ] Với effect cùng DB, marker + effect + result commit/rollback trong một transaction.
- [ ] Tôi không commit marker DONE trước một effect chưa xảy ra.
- [ ] Tôi không coi RUNNING/RECEIVED là SUCCEEDED.
- [ ] Tôi có recovery/lease/backoff nếu job dài và worker chết.
- [ ] Tôi có generation/fencing để chặn stale owner tại commit boundary.
- [ ] Tôi hiểu lease hết hạn không tự ngăn worker cũ gửi external effect.
- [ ] Tôi ACK broker sau khi effect theo contract đã commit, hoặc dùng transaction protocol được hỗ trợ.
- [ ] Tôi có inbox/outbox khi workflow cần durability qua restart.
- [ ] Tôi không tuyên bố Kafka EOS bao phủ database/email không thuộc Kafka transaction.
- [ ] Tôi biết external effect UNKNOWN cần reconciliation/provider idempotency.
- [ ] Tôi kiểm tra `@Transactional` proxy/rollback và `@Async` boundaries.
- [ ] Tôi có retention horizon không ngắn hơn replay/retry window có thể xảy ra.
- [ ] Tôi có metrics duplicate, conflict, reclaim, fencing và unknown outcomes.
- [ ] Tôi test race hai transactions, crash injection, retry và nhiều replicas thật.
- [ ] Tôi bảo vệ privacy và không log idempotency payload nhạy cảm.

**Kết luận:** trong môi trường nhiều replicas, retry và crash là bình thường. **Mã công việc ổn định** giúp nhận ra cùng một ý định; **dấu mốc xử lý bền vững** cho biết đã tiếp nhận, đang chạy hay đã áp dụng thành công; **unique constraint/conditional write và transaction** biến việc nhận diện thành một bảo đảm thực sự trong phạm vi database. Khi công việc vượt khỏi một transaction domain, hãy thêm **outbox/inbox, lease, fencing, downstream idempotency, timeout, retry và reconciliation**. Đừng hứa exactly-once cho toàn hệ nếu chưa chứng minh được từng ranh giới effect.

## 25. Tài liệu chính thức

- PostgreSQL — [INSERT / ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html): unique constraints, `ON CONFLICT DO NOTHING` và affected-row count; trọng tài khi concurrent insert.
- PostgreSQL — [UPDATE / RETURNING](https://www.postgresql.org/docs/current/sql-update.html): conditional UPDATE, row counts và `RETURNING` cho claim/fencing.
- Spring Framework — [@Transactional declarative transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): transaction proxy, isolation/propagation, rollback behavior và self-invocation.
- Spring Framework — [Transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html): `@TransactionalEventListener` và AFTER_COMMIT.
- Spring for Apache Kafka — [Exactly Once Semantics](https://docs.spring.io/spring-kafka/reference/kafka/exactly-once.html): phạm vi Kafka transactional read/process/write, và việc read/process có thể thực thi ít nhất một lần.
- Spring Framework — [Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@Async`/`@Scheduled` không thay thế idempotency hoặc durability.
- Java SE 17 — [ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): `computeIfAbsent` nguyên tử trong một map.
- Java SE 17 — [AtomicReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): `compareAndSet` để mô phỏng generation fencing trong JVM.

**Lưu ý:** ví dụ Spring Boot 3.3.5/PostgreSQL 16 ở đây là một môi trường học tập xác định, không phải khẳng định đó là bản mới nhất. Các đoạn ghi rõ **fragment**, **pseudocode** hoặc **phản mẫu** không phải chương trình tự chạy độc lập. Đưa vào production phải kiểm tra schema migrations, transaction manager thật, security, quota, provider API contracts và deployment topology.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="distributed-limits-005">Không coi nhận tác vụ trong bộ nhớ là đã lưu công việc bền vững</a>

<details>
<summary>Xem chi tiết</summary>

# Không coi nhận tác vụ trong bộ nhớ là đã lưu công việc bền vững

## 1. Bài toán cốt lõi: “Đã nhận” không đồng nghĩa “Đã lưu”

Trong một ứng dụng Spring Framework, lệnh `executor.execute(task)`, `executor.submit(task)`, lời gọi qua `@Async` hoặc đăng ký bằng `TaskScheduler` có thể trả về bình thường. Điều đó chỉ nói rằng **hạ tầng thực thi tại thời điểm đó chấp nhận lời gọi theo hợp đồng của nó**. Nó **không chứng minh** yêu cầu công việc đã được ghi vào một kho bền vững có thể phục hồi sau khi JVM chết, container bị thay, máy mất điện hoặc một deployment mới khởi động.

Ví dụ điển hình:

```java
@PostMapping("/reports")
public ResponseEntity<Void> requestReport() {
    taskExecutor.execute(() -> generateReport());
    return ResponseEntity.accepted().build();
}
```

Đây là **mảnh Spring MVC minh họa**, các import và `generateReport()` phải được ứng dụng cung cấp. Nếu `taskExecutor` là `ThreadPoolTaskExecutor` thông thường, task có thể nằm trong **queue của JVM**, chưa bắt đầu. HTTP 202 ở đây có nghĩa do application tự gán; nó **chưa có một bằng chứng bền vững nào** cho việc công việc sẽ còn tồn tại sau restart.

```text
Client               Spring API              In-memory executor
  | POST /reports        |                            |
  |--------------------->|                            |
  |                      | execute(task)               |
  |                      |--------------------------->|
  |                      |                      task ở heap queue
  |<----- 202 Accepted --|                            |
  |                      |                            |
  |                      X  JVM/process bị kill        X
  |                                                   |
  |                          task chưa chạy: bị mất   |
```

**Nguyên tắc học tập:** Phải phân biệt ít nhất ba mốc:

1. **Accepted in memory:** executor/scheduler đã nhận một `Runnable` vào vùng nhớ hoặc bắt đầu giao thread.
2. **Durably recorded:** một hệ thống lưu trữ được cấu hình bền vững đã xác nhận ghi nhận **mô tả công việc** sau commit/ack tương ứng.
3. **Business effect completed:** tác dụng phụ của công việc đã được thực hiện và ghi nhận theo đúng hợp đồng nghiệp vụ.

Ba mốc này không tự suy ra nhau. Một job có thể đã được lưu bền vững nhưng chưa chạy; có thể đã chạy nhưng chưa ACK; và có thể đã có side effect ngoài hệ thống nhưng trạng thái nội bộ chưa cập nhật.

### Mục tiêu của bài

Sau bài này, bạn có thể:

- Giải thích vì sao `execute`/`submit`/`@Async`/`@Scheduled`/`ScheduledFuture` không phải cơ chế lưu job bền vững.
- Nêu rõ ý nghĩa của HTTP 202, `Future`, executor queue và broker ACK.
- Phân tích các cửa sổ lỗi giữa DB commit, dispatch, thực thi và acknowledgement.
- Thiết kế **durable inbox/job table**, worker poll, claim/lease, retry, backoff và idempotency.
- Nhận diện và xử lý **dual-write problem** bằng transactional outbox khi cần gửi broker.
- So sánh memory executor, hàng đợi thông điệp, Spring Batch `JobRepository` và Quartz JDBC JobStore.
- Chạy demo Java 17 chứng minh queue đã nhận task vẫn có thể mất khi executor dừng đột ngột.
- Chạy demo Spring Framework + H2 qua **nhiều lần khởi động JVM** để thấy công việc đã commit vẫn còn cho worker xử lý.

## 2. Thế nào là bền vững? Phải nói rõ nguồn và ranh giới bảo đảm

Một công việc **durable** khi metadata đủ để xác định, tiếp tục hoặc thực thi lại công việc được lưu trong một hệ thống có cam kết bền vững **phù hợp với failure model**. Không có một nút “durable=true” bảo vệ mọi dạng sự cố.

Ví dụ dữ liệu tối thiểu cần lưu:

```json
{
  "jobId": "report-731",
  "type": "GENERATE_REPORT",
  "tenantId": "tenant-7",
  "payloadRef": "reports/request-731",
  "status": "PENDING",
  "attempts": 0,
  "createdAt": "2026-10-29T10:00:00Z"
}
```

Đây là **JSON mô hình dữ liệu**, không phải cấu hình Spring có thể tự xử lý nếu thiếu worker. `jobId` là định danh nghiệp vụ ổn định; `payloadRef` dẫn đến dữ liệu đã lưu ở nơi được quản lý; `status`/`attempts` hỗ trợ retry và vận hành.

### 2.1. Đâu là “durability boundary”?

| Hành động | Sự kiện đã chứng minh | Điều chưa được chứng minh |
|---|---|---|
| `new Runnable()` | Tạo object trong heap | Có lịch hoặc có persistence |
| `TaskExecutor.execute(task)` trả về | Executor nhận/giao task theo implementation | Task sống qua restart |
| `ExecutorService.submit(task)` trả `Future` | Có handle của submission | Công việc đã commit ra disk |
| `@Async` trả `CompletableFuture` | Có handle thực thi bất đồng bộ theo proxy | Task có bản ghi durable |
| `TaskScheduler.schedule(...)` trả `ScheduledFuture` | Lịch được đăng ký với scheduler | Lịch vẫn tồn tại sau JVM crash |
| `@Scheduled` được đăng ký | Trigger có thể gọi method trong context | Mọi lượt lỡ khi tắt máy được chạy bù |
| `INSERT job` commit ở DB bền vững | Metadata job đã được DB xác nhận commit | Worker đã chạy hoặc hiệu ứng ngoài đã xong |
| Broker xác nhận publish | Broker đã nhận theo hợp đồng confirm/acks/config | Consumer đã xử lý nghiệp vụ |
| Worker ghi `DONE` commit | Trạng thái job đã ghi | Side effect ngoài DB đã được thực hiện nếu không có cơ chế đồng bộ |

Mức durability thực tế phụ thuộc vào DB storage, replication, backup, fsync và broker settings. H2 in-memory là database có transaction nhưng **không bền vững qua restart**; một DB file test có thể sống qua JVM restart nhưng không mặc nhiên chịu được mất ổ cứng hoặc cả vùng triển khai.

### 2.2. Metadata công việc không phải object Runnable

Không nên cố ghi `Runnable` hoặc một lambda Java tùy ý vào database để “đảm bảo job survive”. Lambda có thể giữ reference đến Spring bean, socket, `ThreadLocal`, connection và lớp runtime, không phải một hợp đồng tái tạo ổn định.

Nên lưu **mô tả công việc** dạng dữ liệu: `jobType`, `jobId`, `payload`/`payloadRef`, schema version, thời hạn, tenant, retry policy. Worker khởi tạo lại logic từ mã ứng dụng đã triển khai và dữ liệu đã lưu.

## 3. TaskExecutor chỉ đảm nhận thực thi, không phải durable job store

Spring `TaskExecutor` trừu tượng hóa việc thực thi tác vụ, không đặt yêu cầu lưu tác vụ vào disk. `ThreadPoolTaskExecutor` thường dựa vào `ThreadPoolExecutor` của Java: worker threads và queue **ở trong bộ nhớ tiến trình**.

```java
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(100);
executor.setThreadNamePrefix("report-");
executor.initialize();

executor.execute(() -> {
    System.out.println("Generate report");
});
```

Đây là **mảnh Java dùng Spring**, không được trình bày như ứng dụng hoàn chỉnh: trong production, để Spring sở hữu bean lifecycle, quản lý shutdown và bảo đảm không tạo executor ngoài kiểm soát.

Khi queue chưa đầy, `execute(...)` có thể thành công trước khi worker bắt đầu. Nếu container bị kill, task chờ trong queue của JVM không có nơi nào để ứng dụng mới tìm lại.

### 3.1. QueueCapacity khác durability

`setQueueCapacity(100)` là **giới hạn số tác vụ chờ trong queue in-memory** của executor theo cấu hình phù hợp, không phải “lưu tối đa 100 job bền vững”. Ngay cả queue có hàng triệu chỗ trống, nó vẫn chỉ chứa các object nằm trong heap.

Một queue lớn còn có rủi ro giữ payload/closure và gây memory pressure. Giới hạn queue là công cụ **backpressure/tài nguyên**, không phải commit log.

### 3.2. Lệnh trả thành công còn phụ thuộc rejection policy

Tùy executor:

- `AbortPolicy`/Spring adapter có thể ném `TaskRejectedException` khi không nhận thêm task.
- `CallerRunsPolicy` có thể chạy task **trên caller thread**, tức có tác dụng ngay trước khi `execute` trả.
- `DiscardPolicy` hoặc `DiscardOldestPolicy` có thể loại bỏ công việc theo chính sách của Java executor, đặc biệt nguy hiểm nếu caller vẫn trả HTTP 202.
- Khi executor đang shutdown, submission mới có thể bị từ chối.

Điều này càng cho thấy một boolean “hàm execute không throw” không nên là **bằng chứng job durable**. Cần thiết kế response theo trạng thái **kho nhận công việc** đã commit, không chỉ theo phản ứng của pool.

## 4. @Async, CompletableFuture và ThreadLocal không tạo persistence

Một `@Async` method:

```java
@Service
public class ReportService {
    @Async("reportExecutor")
    public CompletableFuture<String> generate(String requestId) {
        return CompletableFuture.completedFuture(
                "generated-" + requestId);
    }
}
```

Đây là **Spring service minh họa**, cần import và bật `@EnableAsync`; lời gọi phải đi qua Spring proxy. `CompletableFuture` trả ra có thể cho caller biết task async hoàn tất hoặc lỗi, nhưng nó vẫn là **handle in-memory**.

Nếu `requestId` chưa từng được ghi vào một store bền vững, ứng dụng mới sau restart không thể dựa vào `CompletableFuture` của process cũ để phục hồi.

Tương tự:

```java
CompletableFuture.runAsync(() -> sendEmail());
```

Mảnh code này thường dispatch lên common ForkJoinPool nếu không truyền executor. Nó **không** tự sinh message bền vững, không tự retry khi JVM chết và không có ACK của hệ thống lưu công việc.

**Ngoại lệ có chủ đích:** Một method `@Async` *có thể* gọi repository để commit job vào DB; khi ấy durability đến từ **repository commit**, không từ chính annotation `@Async`.

## 5. @Scheduled và ScheduledFuture cũng không phải job persistence

`TaskScheduler` có thể cung cấp handle:

```java
ScheduledFuture<?> handle = scheduler.schedule(
        this::performReport,
        Instant.now().plus(Duration.ofHours(1))
);
```

Đây là mảnh code trong Spring bean có scheduler và `performReport`; cần import `Instant`, `Duration`, `ScheduledFuture`. Handle cho phép hủy/kiểm tra trạng thái trong JVM, nhưng không phải row bền vững trong DB.

Khi ứng dụng bị tắt và khởi động lại:

- Một đăng ký được tạo bằng API `schedule(...)` trên scheduler in-memory **không tự khôi phục**.
- `@Scheduled` có thể được đăng ký **lại từ annotation** sau khi context khởi động; điều đó không có nghĩa các lần chạy đã lỡ khi downtime tự được replay.
- Nếu trong downtime có ba trigger cron mà không có store/logic bù, không thể suy rằng Spring tự phát ba job bù.
- Một `ScheduledFuture<?>` của process cũ không thể lưu sang JVM mới rồi gọi `cancel(...)`.
- `fixedDelay` tính theo lượt chạy trong một scheduler; nó không biến các công việc đã nhận vào queue thành job bền vững.

**Khác biệt:** Một scheduler phát tín hiệu theo lịch, còn durable job store giữ **công việc đã được yêu cầu**. Trong hệ thống tin cậy, ta có thể dùng `@Scheduled(fixedDelay=...)` làm **poller** đọc từ durable table. Khi pod restart, poller mới lại đọc công việc chưa hoàn tất.

## 6. Failure timeline: vì sao HTTP 202 có thể gây hiểu nhầm

Giả sử controller trả 202 ngay sau `executor.execute()`:

```text
T0  POST /reports (jobId=report-731)
T1  In-memory executor nhận Runnable
T2  HTTP 202 Accepted gửi về client
T3  JVM bị kill trước khi Runnable được chạy
T4  Pod mới khởi động; queue cũ không còn
T5  Client không retry vì tin báo cáo đã được nhận
=> Công việc bị mất
```

Một HTTP status code **không quyết định** khả năng phục hồi; cần một hợp đồng phía server bảo đảm sự tồn tại của một bản ghi tương ứng.

Nếu nghiệp vụ chấp nhận mất việc, ví dụ refresh cache hoặc cập nhật metric không quan trọng, in-memory execution có thể hoàn toàn hợp lý. Vấn đề chỉ xuất hiện khi hệ thống **tuyên bố đã nhận cam kết** trong khi không thực sự có lớp bền vững hỗ trợ.

### 6.1. 202 Accepted nên cam kết điều gì?

Một API có thể định nghĩa:

- `202 Accepted`: yêu cầu hợp lệ **đã commit vào durable job store**, công việc sẽ được xử lý theo chính sách retry/SLA đã công bố.
- `Location: /jobs/report-731`: endpoint cho client xem trạng thái.
- `jobId`/idempotency key ổn định: client có thể retry cùng yêu cầu nếu response mất.
- Nếu database chưa commit hoặc không thể ghi: trả lỗi thích hợp; **không** báo “accepted durable”.

Không nên hiểu 202 là “đã xử lý thành công”. Thao tác xử lý có thể tiếp tục thất bại vĩnh viễn hoặc quá hạn, vì vậy API phải có status, outcome và cách vận hành.

## 7. Demo Java 17 độc lập: executor đã nhận nhưng task có thể chưa chạy

Đây là một **chương trình Java SE 17 biên dịch và chạy độc lập**. Để tái hiện việc công việc đang nằm trong heap queue và bị mất khi executor bị dừng, ví dụ cố ý dùng `shutdownNow()` trong lúc một worker đang bận. Đây là **giả lập dừng đột ngột**, không phải một JVM crash thật; nó đủ để chứng minh submission không phải durability.

**Tệp `InMemoryAcceptanceDemo.java`:**

```java
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InMemoryAcceptanceDemo {

    public static void main(String[] args)
            throws InterruptedException {

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean businessEffect = new AtomicBoolean(false);

        try {
            // Occupy the single worker deterministically.
            executor.execute(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });

            if (!firstStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Worker did not start");
            }

            // Accepted into the executor's IN-MEMORY queue.
            executor.execute(() -> businessEffect.set(true));

            System.out.println("Executor accepted task: true");

            // Stop the executor and drain unstarted queued tasks.
            List<Runnable> neverStarted = executor.shutdownNow();

            releaseFirst.countDown();
            boolean stopped = executor.awaitTermination(
                    5, TimeUnit.SECONDS);

            System.out.println("Unstarted tasks returned: "
                    + neverStarted.size());
            System.out.println("Business effect executed: "
                    + businessEffect.get());
            System.out.println("Executor terminated: " + stopped);

        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }
}
```

Chạy:

```bash
javac InMemoryAcceptanceDemo.java
java InMemoryAcceptanceDemo
```

Đầu ra của chương trình trong điều kiện máy xử lý kịp timeout 5 giây:

```text
Executor accepted task: true
Unstarted tasks returned: 1
Business effect executed: false
Executor terminated: true
```

Giải thích chính xác:

1. Một worker duy nhất gọi `firstStarted.countDown()` rồi chờ `releaseFirst`.
2. Main chỉ submit task thứ hai **sau khi xác nhận worker thứ nhất đang chiếm thread**. Task thứ hai vì thế ở queue, chưa thể chạy.
3. `execute(...)` của task thứ hai trả về bình thường: ta in “accepted”.
4. `shutdownNow()` đặt executor ở trạng thái dừng, cố gắng interrupt worker và trả danh sách queued tasks **chưa bắt đầu**. Với điều kiện race được kiểm soát, danh sách có một Runnable.
5. `businessEffect` vẫn `false` vì Runnable thứ hai không được thực thi.
6. `releaseFirst.countDown()` trong `finally` tránh giữ worker vĩnh viễn; `awaitTermination` cho main biết executor đã ngừng.

**Không suy diễn quá mức:** `shutdownNow()` không giống mất điện; trong crash thật, không có cơ hội đọc `neverStarted` để khôi phục. Và `shutdownNow()` không bảo đảm dừng ngay mọi tác vụ đang chạy, nhất là task không phản hồi interruption. Ví dụ chỉ cho thấy **acceptance vào in-memory executor khác xa durable enqueue**.

## 8. Mẫu đúng cơ bản: commit công việc rồi mới báo đã nhận

Một API cần thực sự giữ yêu cầu qua restart có thể dùng một **durable job table**:

```text
POST /jobs
   |
   | validate + authorize + stable request key
   v
BEGIN TRANSACTION
   INSERT durable_jobs(job_id, payload, state=PENDING)
COMMIT
   |
   +--> HTTP 202 + jobId
   |
   +--> Worker poll từ DB và xử lý
            |
            +--> SUCCESS / RETRY / FAILURE
```

**Nguồn chân lý** là trạng thái đã commit trong DB, không phải object `Runnable` đang nằm ở queue.

Mẫu controller định hướng:

```java
@PostMapping("/jobs")
public ResponseEntity<JobAccepted> create(
        @RequestBody JobRequest request) {

    String jobId = jobCommandService.persistRequest(request);

    return ResponseEntity
            .accepted()
            .location(URI.create("/jobs/" + jobId))
            .body(new JobAccepted(jobId, "PENDING"));
}
```

Đây là **mảnh Spring MVC**, chưa đủ runnable: `JobRequest`, `JobAccepted`, `jobCommandService`, validation và authentication là các thành phần ứng dụng. Method `persistRequest(...)` cần hoàn tất transaction commit **trước khi controller trả 202**. Trong Spring AOP thông thường, lời gọi qua proxy vào một `@Transactional` service trả về khi transaction interceptor đã hoàn tất commit.

**Đừng gọi `executor.execute(...)` thay cho `persistRequest(...)` rồi gán status `PENDING`:** tên trạng thái không biến Runnable thành dữ liệu bền vững.

### 8.1. Khóa idempotency ở giai đoạn nhận yêu cầu

Client có thể gửi lại vì bị mất HTTP response sau DB commit. Hãy dùng `jobId`/`requestId` ổn định theo yêu cầu logic và unique constraint để tránh tạo hai công việc:

```sql
CREATE TABLE durable_jobs (
    job_id         VARCHAR(128) PRIMARY KEY,
    job_type       VARCHAR(80) NOT NULL,
    payload_ref    VARCHAR(512) NOT NULL,
    payload_hash   VARCHAR(128) NOT NULL,
    state          VARCHAR(20) NOT NULL,
    attempts       INT NOT NULL DEFAULT 0,
    available_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Đây là ví dụ SQL **PostgreSQL**, không phải schema H2 của demo mục 11. Nếu cùng key đã tồn tại, phải kiểm tra payload hash/ownership để phân biệt **cùng yêu cầu gửi lại** với **khóa bị tái sử dụng cho nội dung khác**. Không bao giờ đổi sang random ID mới trong retry chỉ để né unique conflict.

## 9. Mô hình trạng thái cho công việc bền vững

Một durable store cần ít nhất trạng thái sau:

```text
PENDING ------ claim --------> RUNNING
   ^                             |
   |                             +--> SUCCEEDED / DONE
   |                             |
   |                             +--> RETRY_WAIT (transient failure)
   |                                          |
   +----------- retry due -------------------+
                                 |
                                 +--> FAILED_PERMANENT / DEAD_LETTER

RUNNING + lease expired
   -> eligible for reclaim under strict policy
```

Ý nghĩa:

| Trạng thái | Nội dung | Có đủ để khẳng định business effect? |
|---|---|---|
| `PENDING` | Yêu cầu đã lưu nhưng chưa claim thành công | Không |
| `RUNNING` | Worker đã claim/đang xử lý; có thể crash | Không |
| `RETRY_WAIT` | Tạm lỗi, chờ lần thử tiếp theo | Không |
| `SUCCEEDED`/`DONE` | Workflow đã ghi nhận hoàn tất | Chỉ trong phạm vi effect/commit đã định nghĩa |
| `FAILED_PERMANENT` | Không retry tự động nữa | Không; cần xử lý nghiệp vụ |
| `UNKNOWN`/`RECONCILIATION` | External outcome chưa rõ | Không được tự gán failed/succeeded |

Một status field đơn giản chưa đủ để chống worker cũ chạy sau khi lease hết hạn. Production nên lưu `claimed_by`, `lease_until`, `attempt_count`, `next_retry_at`, `version` hoặc `fencing_token` theo mô hình sở hữu công việc.

### 9.1. Claim và completion khác nhau

Ghi `RUNNING` là dấu hiệu nhận quyền thử xử lý, **không phải đã hoàn tất**. Nếu đánh dấu `DONE` ngay khi đưa task vào in-memory executor, worker chết trước khi thực thi thì job vĩnh viễn bị bỏ qua. Cần chuyển sang `DONE` **sau khi công việc thực sự hoàn tất theo hợp đồng**, với atomicity/idempotency phù hợp.

### 9.2. Tránh “RUNNING vĩnh viễn” khi worker chết

Worker có thể crash sau khi claim. Nếu `RUNNING` không có lease/timeout hoặc cơ chế reconcile, job không bao giờ được claim lại.

Một giải pháp có `lease_until`: job đang RUNNING với lease quá hạn có thể trở lại eligible. Tuy nhiên, **lease quá hạn không tự dừng worker cũ**; cần fencing/generation và idempotent effects để xử lý việc hai worker tạm thời cùng chạy.

## 10. Ghi công việc và dispatch: dual-write problem

Giả sử ứng dụng cần vừa lưu job vào DB vừa đẩy một message vào broker:

```text
DB: INSERT job=PENDING
Broker: publish JobRequested
```

Đây là **hai hệ thống độc lập**. Không thể giả định hai lệnh ở hai nơi sẽ cùng thành công vì đặt cạnh nhau trong một method Java.

### 10.1. Ghi DB trước, publish sau

```text
BEGIN TX -> INSERT job -> COMMIT thành công
                           |
                           X process crash trước broker.publish()
=> DB có job nhưng broker chưa có thông báo
```

Nếu chỉ có consumer nghe broker và không có poller/outbox relay, job có thể nằm im vô hạn.

### 10.2. Publish trước, ghi DB sau

```text
Broker xác nhận message
             |
             X process crash trước DB commit
=> Consumer có thể xử lý message khi job metadata chưa tồn tại
```

Dù producer dùng publisher confirms/acks, điều đó **không** làm một DB transaction riêng được commit cùng lúc.

### 10.3. @Transactional không bao bọc được mọi nơi

`@Transactional` có thể làm transaction của một database. Một lời gọi HTTP, SMTP hoặc broker publish thông thường **không tự rollback** khi local DB rollback. Đừng nhầm “method annotated transaction” với giao dịch phân tán tự động.

Một số nền tảng hỗ trợ transactional producer/coordinated transaction trong phạm vi cụ thể, nhưng không có lý do chung để coi HTTP, JDBC, Kafka/RabbitMQ và side effect khác nằm trong **một atomic commit toàn cục**.

## 11. Transactional outbox: cách bảo đảm không bỏ quên việc gửi thông báo

Khi database là nguồn chân lý, có thể lưu **business change/job request và outbox event trong cùng một DB transaction**:

```text
HTTP request
    |
    v
BEGIN TRANSACTION
  INSERT durable_jobs(jobId, PENDING)
  INSERT outbox(eventId, JobRequested, payload)
COMMIT
    |
    +--> 202 Accepted: durable job đã được ghi
    |
    +--> Outbox relay poll event chưa publish
              |
              +--> Broker publish (có confirm/ack phù hợp)
              |
              +--> Đánh dấu đã gửi / offset / checkpoint
              |
              +--> Consumer dedupe và xử lý
```

Ví dụ schema PostgreSQL:

```sql
CREATE TABLE outbox_events (
    event_id       VARCHAR(128) PRIMARY KEY,
    job_id         VARCHAR(128) NOT NULL,
    event_type     VARCHAR(64) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);
```

Vì job row và outbox row commit chung, không có cửa sổ “DB có yêu cầu nhưng không còn dấu vết cần publish”. Nếu relay chết trước publish, nó đọc lại outbox chưa hoàn tất sau restart.

**Nhưng outbox không tự bảo đảm exactly-once delivery.** Relay có thể publish thành công, sau đó crash **trước khi đánh dấu published**. Khi restart, nó publish lại cùng event. Consumer cần idempotency key/unique marker để bỏ qua duplicate. AWS cũng nêu rõ yêu cầu xử lý duplicate trong transactional outbox pattern.

### 11.1. Poller từ job table có thể là giải pháp đủ

Nếu worker xử lý trực tiếp từ cùng database, không nhất thiết phải thêm broker + outbox. Một poller đọc `PENDING` rows rồi claim atomic có thể đủ. Khi cần truyền sự kiện sang hệ thống khác, outbox giúp xử lý ranh giới publish.

Đừng thêm tầng messaging chỉ để “trông phân tán” khi một durable DB queue đáp ứng yêu cầu throughput, isolation và vận hành; nhưng cũng đừng dùng DB queue không index/không quota cho workload quá lớn rồi phớt lờ backpressure.

## 12. Demo Spring JDBC + H2 file database: commit trước, xử lý sau restart

Ví dụ này dùng **Spring Framework 6.2.7, Java 17, Spring JDBC, Spring TX và H2 file database**. Nó có thể chạy **nhiều lần như các JVM riêng biệt** để thấy một job đã commit tồn tại khi ứng dụng tắt và mở lại.

Mục tiêu:

1. `enqueue` ghi một job với `state=PENDING` vào **H2 file DB** rồi thoát; không đẩy job vào executor.
2. `status` chạy trong một JVM khác vẫn nhìn thấy `PENDING`.
3. `fail` mô phỏng lỗi sau khi claim, trước khi commit; Spring rollback và job còn `PENDING`.
4. `run` trong lần khởi động khác thực thi xử lý trong DB và ghi `DONE`.
5. Chạy `run` lần nữa không nhân đôi kết quả nhờ trạng thái + unique key.

**Lưu ý:** H2 file là môi trường demo, không phải cam kết HA, replication, backup hay khả năng chịu mất ổ cứng. Các lệnh dưới đây chạy **tuần tự** trong cùng thư mục; không dùng H2 embedded file demo để chứng minh đa node đồng thời.

### 12.1. Cấu trúc thư mục

```text
durable-job-demo/
├── pom.xml
└── src/main/java/lesson/DurableJobDemo.java
```

### 12.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lesson</groupId>
    <artifactId>durable-job-demo</artifactId>
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

### 12.3. src/main/java/lesson/DurableJobDemo.java

```java
package lesson;

import javax.sql.DataSource;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

public class DurableJobDemo {

    private static final String ID = "report-731";

    public static void main(String[] args) {
        String command = args.length == 0 ? "status" : args[0];

        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Config.class)) {

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            initializeTables(jdbc);

            JobService service = context.getBean(JobService.class);

            switch (command) {
                case "enqueue" -> {
                    boolean fresh;
                    try {
                        service.enqueue(ID, "report-content");
                        fresh = true;
                    } catch (DuplicateKeyException duplicate) {
                        fresh = false;
                    }
                    System.out.println("New request committed: " + fresh);
                    System.out.println("Jobs in store: "
                            + service.jobCount());
                }

                case "status" -> {
                    System.out.println("State: " + service.state(ID));
                    System.out.println("Outputs: "
                            + service.outputCount());
                }

                case "fail" -> {
                    boolean failed = false;
                    try {
                        service.runOnce(ID, true);
                    } catch (IllegalStateException expected) {
                        failed = true;
                    }
                    System.out.println("Failure simulated: " + failed);
                    System.out.println("State: " + service.state(ID));
                    System.out.println("Outputs: "
                            + service.outputCount());
                }

                case "run" -> {
                    System.out.println("Applied this time: "
                            + service.runOnce(ID, false));
                    System.out.println("State: " + service.state(ID));
                    System.out.println("Outputs: "
                            + service.outputCount());
                }

                default -> throw new IllegalArgumentException(
                        "Expected enqueue, status, fail or run");
            }
        }
    }

    private static void initializeTables(JdbcTemplate jdbc) {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS durable_jobs (
                job_id VARCHAR(128) PRIMARY KEY,
                payload VARCHAR(255) NOT NULL,
                state VARCHAR(20) NOT NULL,
                attempts INT NOT NULL
            )
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS job_outputs (
                job_id VARCHAR(128) PRIMARY KEY,
                result VARCHAR(255) NOT NULL
            )
            """);
    }

    @Configuration
    @EnableTransactionManagement
    public static class Config {

        @Bean
        public DataSource dataSource() {
            DriverManagerDataSource ds =
                    new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:file:./durable-job-example");
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
        public JobService jobService(JdbcTemplate jdbc) {
            return new JobService(jdbc);
        }
    }

    public static class JobService {
        private final JdbcTemplate jdbc;

        public JobService(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        public void enqueue(String id, String payload) {
            jdbc.update(
                    "INSERT INTO durable_jobs "
                  + "(job_id, payload, state, attempts) "
                  + "VALUES (?, ?, 'PENDING', 0)",
                    id, payload);
        }

        @Transactional
        public boolean runOnce(String id, boolean failAfterClaim) {
            int claimed = jdbc.update(
                    "UPDATE durable_jobs "
                  + "SET state = 'RUNNING', attempts = attempts + 1 "
                  + "WHERE job_id = ? AND state = 'PENDING'",
                    id);

            if (claimed == 0) {
                return false; // Không có job PENDING.
            }

            if (failAfterClaim) {
                throw new IllegalStateException(
                        "Simulated failure before commit");
            }

            String payload = jdbc.queryForObject(
                    "SELECT payload FROM durable_jobs "
                  + "WHERE job_id = ?",
                    String.class, id);

            jdbc.update(
                    "INSERT INTO job_outputs(job_id, result) "
                  + "VALUES (?, ?)",
                    id, "result:" + payload);

            jdbc.update(
                    "UPDATE durable_jobs SET state = 'DONE' "
                  + "WHERE job_id = ?",
                    id);

            return true;
        }

        public String state(String id) {
            return jdbc.queryForObject(
                    "SELECT state FROM durable_jobs "
                  + "WHERE job_id = ?",
                    String.class, id);
        }

        public Integer jobCount() {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM durable_jobs",
                    Integer.class);
        }

        public Integer outputCount() {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM job_outputs",
                    Integer.class);
        }
    }
}
```

### 12.4. Chạy lần lượt trong **cùng thư mục**

Bắt đầu với một thư mục mới chưa có file `durable-job-example.mv.db`. Nếu bạn từng chạy demo, hãy dùng **thư mục mới sạch** thay vì xóa dữ liệu cần giữ.

**Lần 1: API giả lập lưu yêu cầu rồi thoát JVM.**

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.DurableJobDemo -Dexec.args=enqueue
```

Stdout của chương trình:

```text
New request committed: true
Jobs in store: 1
```

**Lần 2: một JVM mới xem trạng thái trước khi xử lý.**

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.DurableJobDemo -Dexec.args=status
```

```text
State: PENDING
Outputs: 0
```

**Lần 3: cố ý gây lỗi trong transaction.**

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.DurableJobDemo -Dexec.args=fail
```

```text
Failure simulated: true
State: PENDING
Outputs: 0
```

**Lần 4: một JVM mới xử lý thành công.**

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.DurableJobDemo -Dexec.args=run
```

```text
Applied this time: true
State: DONE
Outputs: 1
```

**Lần 5: chạy lại không nhân đôi output.**

```bash
mvn -q compile exec:java -Dexec.mainClass=lesson.DurableJobDemo -Dexec.args=run
```

```text
Applied this time: false
State: DONE
Outputs: 1
```

### 12.5. Giải thích hợp đồng

- `enqueue(...)` có `@Transactional`, được gọi qua bean `JobService` lấy từ Spring context; commit hoàn tất trước khi main in `New request committed`.
- `H2 file database` lưu row ra file, nên một JVM mới có thể xem lại `PENDING`. Đây không phải chỉ là một queue trong heap của executor.
- `runOnce(...)` dùng một conditional `UPDATE ... WHERE state='PENDING'` để nhận công việc, sau đó insert output và chuyển `DONE` **trong cùng một DB transaction**.
- Khi `failAfterClaim=true` ném `IllegalStateException`, Spring rollback cả thay đổi `RUNNING/attempts`; công việc vẫn `PENDING`. Trong demo, attempt chỉ đếm **lượt commit**; production muốn audit cả failed attempts cần bảng/log giao dịch riêng.
- `job_outputs.job_id` có primary key, cung cấp thêm ràng buộc không nhân đôi output theo job.
- Lần `run` thứ hai không tìm thấy `PENDING`, trả false mà không insert output mới.
- Không có external HTTP/email nào trong transaction. Nếu output là tác dụng phụ ở hệ thống khác, cần xử lý ranh giới riêng.

### 12.6. Giới hạn thiết kế demo

Đừng nhầm kết quả này là durable queue production:

1. H2 embedded file dùng để **minh họa tính lưu qua các JVM tuần tự**, không phải HA database chịu mất máy hoặc nhiều pod đồng thời.
2. `runOnce` giữ trạng thái `RUNNING` chưa commit trong transaction trong lúc xử lý; với logic nhanh ở local DB có thể chấp nhận cho demo. Production không giữ một DB transaction dài trong lúc gọi external API.
3. Demo không có `lease_until`, timeout, cron poller, retry budget, dead-letter, quota, payload hash hoặc multi-tenant authorization.
4. Câu lệnh duplicate `INSERT` sẽ ném `DuplicateKeyException`; caller ngoài transaction phải phân biệt **cùng yêu cầu retry** và **cùng khóa nhưng payload khác**.
5. Nếu một job hoàn tất side effect bên ngoài nhưng crash trước `DONE`, có thể được retry; cần idempotency tại nơi sinh side effect.
6. Data durability thật còn phụ thuộc file system và cấu hình DB. Cơ chế commit của H2 file trong một laptop không thay thế yêu cầu backup, replication, monitoring và disaster recovery.

## 13. Đánh thức worker sau commit: optimization, không phải nguồn chân lý

Nhiều ứng dụng muốn xử lý ngay, không chờ chu kỳ poll DB. Có thể commit yêu cầu trước, rồi **thử dispatch** một “tín hiệu đánh thức” tới executor.

```text
BEGIN TX
  INSERT durable_jobs(PENDING)
COMMIT
       |
       +-- thử executor.execute(() -> worker.process(jobId))
       |          |
       |          +-- thành công: latency tốt hơn
       |          +-- thất bại/JVM crash: job vẫn ở DB
       |
       +-- poller định kỳ quét PENDING làm fallback
```

Điểm mấu chốt: **dispatch không được là đường duy nhất để job được phát hiện**. Nếu sau commit nhưng trước dispatch process chết, poller phải tìm lại job.

Một mẫu Spring:

```java
@Transactional
public void accept(JobCommand command) {
    jdbc.update(
            "INSERT INTO durable_jobs (job_id, state) "
          + "VALUES (?, 'PENDING')",
            command.id());

    TransactionSynchronizationManager
        .registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    executor.execute(
                            () -> worker.process(command.id()));
                } catch (TaskRejectedException rejected) {
                    logWakeupFailure(command.id(), rejected);
                    // Không xóa job PENDING ở DB.
                }
            }
        });
}
```

Đây là **mảnh kiến trúc**, cần schema phù hợp, import `TransactionSynchronizationManager`, `TransactionSynchronization` và `TaskRejectedException`, các biến `jdbc`/`executor`/`worker`/`command` cùng một Spring bean có transaction thật. Nó không chạy độc lập.

**Cảnh báo quan trọng:** `afterCommit()` là callback **trong process**, không phải durable message. Nếu JVM chết giữa commit và callback, wakeup mất. Vì thế cần poller/outbox relay fallback; callback chỉ giúp giảm độ trễ trung bình.

Nếu callback ném lỗi sau commit, request HTTP có thể thấy exception **mặc dù job đã được lưu**. Cần thiết kế error handling, stable idempotency key và endpoint xem status để caller có thể retry an toàn. Không giả định callback sau commit có thể rollback transaction đã commit.

## 14. @Scheduled poller: nhiệm vụ là truy tìm công việc đã ghi bền vững

Một Spring component có thể poll định kỳ:

```java
@Component
public class PendingJobPoller {
    private final JobWorker worker;

    public PendingJobPoller(JobWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelay = 5000)
    public void scan() {
        worker.claimAndRunBatch(50);
    }
}
```

Đây là **Spring fragment** cần `@EnableScheduling` và các bean nghiệp vụ. `@Scheduled` không lưu job; nó chỉ là đồng hồ đánh thức. **Durable jobs ở database** mới là nguồn để khôi phục sau restart.

Khi một pod bị thay:

```text
Pod cũ: poller đang ngủ / task đang chờ heap
             X
Pod mới: Spring tạo lại poller từ configuration
             |
             v
         SELECT PENDING / overdue RETRY_WAIT / expired leases
             |
             v
         Claim rồi xử lý theo chính sách
```

Không khẳng định mọi việc được khôi phục ngay tức thì: độ trễ phụ thuộc poll interval, DB capacity, lease expiry, retry backoff và worker pool. Hệ thống phải có SLA và quan sát backlog.

## 15. Claim atomic ở nhiều worker: không được SELECT rồi xử lý ngay

Hai pod cùng `SELECT ... WHERE state='PENDING'` có thể nhận cùng một job. Nếu mỗi pod đều chạy mà không claim atomic, side effect có thể được lặp.

Một hướng cho PostgreSQL:

```sql
-- Chỉ là minh họa PostgreSQL, trong một transaction NGẮN.
WITH chosen AS (
    SELECT job_id
    FROM durable_jobs
    WHERE state = 'PENDING'
      AND available_at <= now()
    ORDER BY available_at, created_at, job_id
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
UPDATE durable_jobs AS j
SET state = 'RUNNING',
    attempts = j.attempts + 1,
    claimed_by = 'worker-7',
    lease_until = now() + interval '30 seconds',
    version = j.version + 1
FROM chosen
WHERE j.job_id = chosen.job_id
RETURNING j.job_id, j.payload_ref, j.version;
```

Đây là **mẫu SQL hướng dẫn**, cần bổ sung các cột `claimed_by`, `lease_until`, `version` vào schema sản phẩm. Truy vấn `FOR UPDATE SKIP LOCKED` là tính năng của PostgreSQL và các DB tương thích; không áp nguyên xi cho mọi engine.

Quy trình:

1. Transaction claim chọn một record đủ điều kiện và cập nhật ownership/version.
2. **Commit claim nhanh**, không giữ row lock trong khi gọi HTTP chậm.
3. Thực thi logic với deadline và idempotency key.
4. Ghi kết quả bằng conditional update kiểm tra `job_id + version/owner + state`.
5. Nếu worker chết, lease quá hạn cho phép recovery theo chính sách.

### 15.1. Lease quá hạn không làm worker cũ biến mất

Nếu worker A chậm và lease hết hạn, worker B có thể claim lại. A vẫn có thể chạy trên CPU hoặc tiếp tục ghi bên ngoài.

Để bảo vệ:

- **Fencing token** tăng đơn điệu; nơi ghi effect phải từ chối owner có token cũ nếu khả thi.
- **Idempotency key** ở tác dụng phụ, ví dụ `job_id`/event ID ổn định.
- Conditional DB update `WHERE version=? AND state='RUNNING'` để worker cũ không ghi `DONE` đè state mới.
- Timeout/cancellation, gia hạn lease nếu process vẫn khỏe.
- Không coi distributed lock là bằng chứng exactly-once effects.

Đừng tách `SELECT` và `UPDATE` mà không kiểm tra kết quả cập nhật; có thể đọc trạng thái cũ rồi cả hai worker đều tin mình sở hữu job.

## 16. Khi job chạy lại: idempotency là yêu cầu chứ không phải tiện ích

Một durable queue với retry thường chọn **at-least-once attempts**: công việc chưa xác nhận thành công sẽ được thử lại. Điều đó có thể tạo duplicate attempts.

```text
Worker A: gọi payment API thành công
Worker A: mất kết nối trước khi ghi DONE
Recovery: job trở lại PENDING
Worker B: xử lý lại
=> Nếu không idempotent, payment bị thu hai lần
```

Do đó cần idempotency key ổn định của **công việc logic**, không phải random ID mới cho từng retry.

Ví dụ:

```text
jobId = "tenant-7/payment-intent-731"
externalIdempotencyKey = "tenant-7/payment-intent-731"
```

Nếu payment gateway hỗ trợ idempotency keys, dùng lại cùng key khi retry và kiểm tra retention/payload mismatch theo nhà cung cấp. Nếu không hỗ trợ, cần query status/reconciliation/compensation; local marker không thể biến HTTP call thành transaction chung.

### 16.1. Unique constraint cho effect ở cùng database

```sql
CREATE TABLE job_effect_markers (
    job_id       VARCHAR(128) PRIMARY KEY,
    payload_hash VARCHAR(128) NOT NULL,
    applied_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Trong **cùng DB transaction**, insert marker và cập nhật business state. Nếu worker crash trước commit, cả hai rollback. Nếu commit xong nhưng ACK mất, retry thấy unique key và không làm lại effect. Cùng key payload khác phải bị từ chối, không tự coi là duplicate hợp lệ.

**Không** được insert marker rồi commit trước, còn business update làm ở transaction sau: crash giữa hai transaction sẽ làm mất việc. **Không** làm business update rồi marker commit độc lập: crash có thể gây effect lặp.

## 17. Broker nhận message: đừng nhầm send(), confirm và consumer ACK

Sử dụng RabbitMQ/Kafka có thể cung cấp lưu công việc bền vững **nếu cấu hình đúng và có đủ cơ chế xác nhận**. Nhưng gọi producer API không tự chứng minh dữ liệu đã persisted.

Ví dụ phân biệt:

| Bước | Điều đã xảy ra | Còn cần |
|---|---|---|
| `rabbitTemplate.convertAndSend(...)` trả về | Client library đã thực hiện publish call theo API | Publisher confirms/returns và queue settings |
| Producer Kafka `send(...)` trả future | Gửi async đã được khởi tạo | Chờ thành công theo `acks`/replication config |
| Broker publisher confirm/producer ack | Broker xác nhận theo hợp đồng triển khai | Consumer xử lý, dedupe và ACK riêng |
| Consumer nhận message | Payload đã được giao tới worker | Xử lý nghiệp vụ thành công trước ACK |
| Consumer ACK sau commit | Broker được phép coi delivery đã xử lý | Có thể redelivery trước ACK khi crash |

**RabbitMQ:** Publisher confirms khác với consumer acknowledgements. Với persistent message vào durable queue, confirm có ý nghĩa theo độ bền được RabbitMQ mô tả; với quorum queue, confirm liên quan việc quorum replicas chấp nhận. Tuy nhiên **publisher confirm không phải bằng chứng consumer đã hoàn tất công việc**. Nếu message không route đến queue nào, confirm và mandatory return là hai tín hiệu khác nhau; cần kiểm tra cả routing nếu ứng dụng yêu cầu.

**Kafka:** Ý nghĩa của producer ack/future thành công phụ thuộc `acks`, replication/min.insync.replicas, broker configuration, retention và failure model. Một successful send không đồng nghĩa mọi consumer downstream đã xử lý effect.

### 17.1. Consumer ACK phải đặt đúng chỗ

```text
Broker giao message
      |
      v
Consumer xử lý / commit business transaction
      |
      +-- success -> ACK
      |
      +-- transient failure -> NACK/requeue/retry/DLQ theo policy
```

Nếu ACK **trước** khi business transaction hoàn tất, consumer crash có thể khiến message mất nhưng effect chưa làm. Nếu effect commit rồi crash **trước ACK**, broker có thể redeliver; phải có idempotency để không làm lại effect.

Đây là hai cửa sổ khác nhau; **không thể loại bỏ chỉ bằng một cờ `autoAck=true`**.

## 18. Spring Batch JobRepository: metadata bền vững cũng có giới hạn

Spring Batch có `JobRepository` để quản lý `JobInstance`, `JobExecution`, `StepExecution` và execution context, phục vụ restart/recovery theo thiết kế batch.

Tuy nhiên cần phân biệt:

- Một **JDBC-backed JobRepository** với database bền vững có thể lưu metadata các lần chạy qua restart.
- `ResourcelessJobRepository` của các bản Spring Batch mới không ghi metadata bền vững; nó phù hợp một số trường hợp không cần restart.
- `JobRepository` không tự persist một arbitrary `Runnable` được submit vào `ThreadPoolTaskExecutor`.
- Job/step code phải hỗ trợ **restartability**, checkpoint và idempotent writer; có metadata không có nghĩa mọi external effect không bị trùng.
- `JobLauncher`/`JobOperator` và policy identity của JobParameters quyết định cách vận hành; không suy rằng tên job giống nhau luôn là cùng một instance.

Nếu bài toán là ETL/batch dài cần checkpoint/restart, Spring Batch có thể phù hợp hơn tự viết scheduler/queue. Nhưng vẫn phải chọn backend, transaction manager và restart semantics rõ ràng.

### 18.1. JobRepository không đồng nghĩa một distributed lock phổ quát

Có nhiều Spring Batch instances/pods dùng cùng database thì vẫn cần cấu hình và phối hợp thích hợp cho job identity, locking, partitioning và worker ownership. Không dùng metadata `STARTED` như bằng chứng external API đã làm việc xong.

## 19. Quartz JobStore: RAMJobStore và JDBC JobStore

Quartz cung cấp scheduler với tùy chọn lưu job/trigger:

| Quartz JobStore | Lưu metadata | Sống qua JVM restart |
|---|---|---|
| `RAMJobStore` | RAM | Không |
| `JDBCJobStore` (như JobStoreTX/CMT) | Database | Có, theo cấu hình và database |
 
Quartz RAMJobStore nhanh và đơn giản nhưng mất scheduling information khi process dừng. JDBC JobStore giữ job details và triggers trong DB; có thể hỗ trợ cluster/misfire/recovery **khi được cấu hình đầy đủ**, không phải tự động cho mọi loại job và mọi side effect.

**Hai mức khác nhau:**

1. **Durability của lịch**: trigger sau restart vẫn còn để quyết định khi nào chạy.
2. **Durability của công việc nghiệp vụ**: bản ghi cần làm còn tồn tại, retries được quản lý và effects có idempotency.

Một Quartz JDBC JobStore không tự biến việc gọi một external email API thành exactly-once delivery. Cần chọn đúng `JobDetail` durability, trigger/misfire/recovery policy và ứng dụng xử lý duplicate.

## 20. HTTP response, commit timeout và “không rõ kết quả”

Có một tình huống ngược với mất queue in-memory:

```text
Client gửi POST jobId=report-731
Server INSERT + COMMIT thành công vào DB
Server crash trước HTTP response
Client nhận timeout
Client retry cùng request
```

Nếu client retry bằng **jobId mới**, server sẽ tạo hai công việc. Cần stable idempotency key để client có thể hỏi hoặc gửi lại cùng ID. Database unique constraint phát hiện duplicate, server trả status hiện tại thay vì tạo lại.

Một response `202` sau commit không biến hệ thống thành exactly-once: nó mới là **durable acceptance** trong phạm vi DB. Việc thực thi vẫn cần worker/retry và có thể thất bại.

### 20.1. Response contract nên cụ thể

Ví dụ:

```json
{
  "jobId": "report-731",
  "state": "PENDING",
  "acceptedDurably": true,
  "statusUrl": "/jobs/report-731"
}
```

Đây là **mô hình response**, không phải một lời chứng minh thực tế nếu code không commit DB trước khi trả. Cũng cần status phân biệt `PENDING`/`RUNNING`/`RETRY_WAIT`/`DONE`/`FAILED` và retention policy.

Nếu yêu cầu vi phạm unique key với payload khác, trả conflict; nếu DB không thể commit, báo lỗi thay vì đánh dấu accepted. Quy tắc status code cụ thể do API contract chọn, nhưng ý nghĩa **không được quảng bá hơn những gì hệ thống đã bảo đảm**.

## 21. Backpressure: durable không đồng nghĩa nhận vô hạn job

Một DB queue hay broker có thể chịu lưu lâu hơn heap queue, nhưng vẫn có giới hạn:

- Disk/quota, WAL volume, transaction log.
- Queue depth, consumer lag và retry backlog.
- DB indexes, hot partitions và contention.
- Payload size, retention, cleanup và archival.
- SLA thời gian chờ của từng tenant.
- Rate limit trên downstream.
- Số worker, max in-flight và timeout.

Nếu worker xử lý 100 jobs/phút nhưng API nhận 500 jobs/phút liên tục, backlog vẫn tăng ~400 jobs/phút. Durability **ngăn mất việc khi process restart**, không tự cấp thêm throughput.

Trong production, nên có:

1. Admission control/quota theo tenant/job type.
2. Limit pending backlog, payload size và retry count.
3. `available_at`/`next_retry_at`, backoff + jitter.
4. Dead-letter hoặc manual review cho lỗi không thể tự sửa.
5. Timeout và retention phù hợp nghiệp vụ.
6. Monitoring age of oldest pending job và expected completion deadline.
7. Capacity planning cho DB/broker và replication.
8. Policy rõ khi không còn đủ dung lượng để nhận việc mới: **từ chối thay vì nói đã nhận**.

### 21.1. Sự khác nhau giữa limit worker và limit backlog

`ThreadPoolTaskExecutor.setMaxPoolSize(20)` giới hạn một dạng concurrency trong JVM, không giới hạn số durable jobs đã chờ trong database. Một backlog bền vững có thể chứa hàng triệu job trong khi pool chỉ xử lý 20 song song.

Đó là hành vi mong muốn **nếu** quota/storage/latency SLA được thiết kế. Đừng tự động nạp hàng triệu rows từ DB vào in-memory executor queue; như vậy lại tạo một tầng backlog dễ mất và tốn heap.

## 22. Shutdown, deploy và crash recovery

### 22.1. Graceful shutdown chỉ là lớp bảo vệ bổ sung

Khi container nhận SIGTERM, Spring có thể cố ngừng nhận request và chờ một số task hoàn tất theo timeout/policy. Điều đó **không bảo đảm** mọi pending in-memory task sẽ sống qua SIGKILL, OOM, node crash hoặc mất nguồn.

Một hệ thống chỉ dựa vào “chúng tôi có graceful shutdown 30 giây” để coi `TaskExecutor` durable là thiết kế sai. Graceful shutdown giảm khả năng mất việc trong triển khai bình thường; durable store chịu trách nhiệm phục hồi khi process không kịp đóng.

### 22.2. Khôi phục dựa vào dữ liệu, không phải handle cũ

Khi pod mới khởi động:

- Đăng ký lại scheduler/poller theo code.
- Kết nối tới durable database/broker.
- Tìm `PENDING`, `RETRY_WAIT` đã đến hạn và `RUNNING` lease expired.
- Claim theo ownership/transaction phù hợp.
- Xử lý với idempotency key ổn định.
- Cập nhật trạng thái hoặc retry; log/alert nếu quá hạn.

Không deserialize `Future<?>` hoặc `ScheduledFuture<?>` cũ, cũng không xây cơ chế phụ thuộc vào địa chỉ memory của worker cũ.

### 22.3. Một job đã lưu không có nghĩa sẵn sàng phục hồi ngay

Nếu DB chưa khôi phục, broker không đạt quorum hoặc credentials/config sai, job dù bền vững vẫn chưa thể xử lý. “Durable” và “Available” là hai đặc tính khác nhau. Cần readiness checks, alerting, backup/restore tests và runbook.

## 23. Observability: tách nhận, lưu, dispatch và hoàn tất

Để tránh dashboard hiển thị “100% accepted” trong khi công việc bị mất, đo các mốc riêng:

| Metric/log | Ý nghĩa |
|---|---|
| `job.request_received` | Có request đến |
| `job.persist_commit_success` | Durable store commit thành công |
| `job.persist_commit_failure` | Không thể nhận cam kết |
| `job.executor_dispatch_success` | Wakeup in-memory gửi được |
| `job.executor_rejected` | Dispatcher bị từ chối |
| `job.pending_count` | Công việc đã lưu còn chờ |
| `job.oldest_pending_age` | Công việc lâu nhất chưa xử lý |
| `job.claimed_count` | Worker đã claim |
| `job.running_lease_expired` | Claim không hoàn tất trước hạn |
| `job.completed_count` | Công việc được ghi nhận xong |
| `job.duplicate_attempts` | Số lần thử trùng theo key |
| `job.retry_count` | Số lần thử lại |
| `job.failed_permanent` | Lỗi cuối không retry |
| `outbox.unpublished_count` | Outbox chưa publish/confirm |
| `broker.publish_confirm_latency` | Thời gian xác nhận publish |
| `worker.ack_latency` | Độ trễ từ thực thi tới xác nhận xử lý |

Cần alert khi **persisted pending age** vượt SLA, không chỉ khi executor queue size tăng. Một executor queue bằng 0 có thể đồng nghĩa *không có backlog*, hoặc *toàn bộ công việc đã mất sau restart* nếu không có store.

### 23.1. Đánh dấu business outcome có nguồn chứng minh

Nếu có payment/email/outbound API, log `DONE` cần gắn một căn cứ theo contract: local DB transaction committed, downstream idempotency response, delivered event state hoặc reconciliation result. Không log “delivered” ngay khi gọi `executor.execute(...)`.

Tránh metrics cardinality cao: `jobId`/`requestId` duy nhất nên ở structured logs/traces/status store; tags metrics nên dùng job type, outcome class và nhóm tenant được kiểm soát.

## 24. Ma trận failure injection

| Thời điểm lỗi | Thiết kế chỉ in-memory | Thiết kế có durable job store |
|---|---|---|
| Trước nhận request | Chưa có task | Chưa có job |
| Sau `execute()` nhận, trước task chạy | Có thể mất task khi crash | Nếu chưa commit DB, chưa được báo durable accepted |
| Sau DB commit, trước dispatch | Không có DB row để tìm lại | Poller tìm lại `PENDING` |
| Sau dispatch, trước worker bắt đầu | Queue có thể mất | Poller/claim lại từ DB |
| Trong worker trước business commit | Task có thể biến mất | Retry sau rollback/lease policy |
| Sau business commit, trước `DONE` | Có thể retry và trùng effect | Cần atomic commit/idempotency |
| Sau outbox publish, trước mark published | Không có replay state | Relay có thể publish lại; consumer dedupe |
| Sau DB commit, trước HTTP 202 | Client timeout khó biết đã nhận | Stable key cho retry/status |
| Một pod tắt, pod khác còn | Queue pod tắt mất | Pod khác có thể claim nếu shared durable store |
| DB/broker tạm unavailable | Executor vẫn có thể “nhận” nhưng không durable | Không tuyên bố durable accepted nếu chưa có confirm hợp lệ |

### 24.1. Cách kiểm thử thực chất

Không chỉ test method `execute()` trả bình thường. Test:

1. Block một worker bằng `CountDownLatch`, submit task vào queue rồi dừng executor: xác nhận task được nhận nhưng chưa làm.
2. Commit một job row, tắt hẳn JVM, mở JVM khác: xác nhận đọc lại `PENDING`.
3. Gây lỗi sau claim nhưng trước commit: xác nhận rollback và retry.
4. Gây crash sau commit nhưng trước dispatch: poller tìm lại.
5. Gây duplicate request cùng key: chỉ một job row được tạo.
6. Gây mất ACK sau effect commit: retry không nhân side effect.
7. Cho lease hết hạn khi worker cũ chậm: verify fencing hoặc idempotent effect.
8. Tắt broker khi tạo job: xác minh outbox vẫn giữ event, relay retry.
9. Đầy disk/quota hoặc DB down: API không trả “durable accepted”.
10. Rolling deployment nhiều pod: không mất backlog, metrics và status nhất quán.

Các ví dụ mục 7 và 12 kiểm chứng **hai lớp riêng**: lệnh executor accepted không bền vững, và DB file persistence qua nhiều JVM. Production còn phải test failover thật với DB/broker cấu hình của mình.

## 25. Những sai lầm thường gặp

| Sai lầm | Vì sao sai | Cách sửa |
|---|---|---|
| Trả `202` ngay sau `execute()` | Chỉ xác nhận memory handoff | Commit job vào durable store trước |
| Dùng `Future` làm job ID bền vững | Future thuộc JVM cũ | Dùng job ID nghiệp vụ ổn định |
| Tăng `queueCapacity` để chống mất job | Queue vẫn ở heap | Durable queue/DB |
| Dựa vào `@Scheduled` để tự phát lại mọi lượt đã lỡ | Annotation không lưu occurrence history | Lưu logical jobs/checkpoint/recovery |
| `@Async` trả nhanh nên cho là công việc đã lưu | Trả nhanh không có nghĩa persisted | Ghi DB hoặc broker confirm |
| Sau DB commit, dispatch thất bại thì xóa job | Xóa nguồn chân lý | Giữ `PENDING` cho poller retry |
| Chỉ có `afterCommit` callback, không có poller | Crash làm mất wakeup callback | Poller/outbox relay fallback |
| Đánh dấu `DONE` lúc enqueue | Chưa có effect | DONE sau khi xử lý đúng contract |
| SELECT pending rồi xử lý không claim | Nhiều pod đọc cùng job | Atomic claim + ownership |
| Cho rằng lease hết hạn đã kill worker cũ | Worker cũ có thể vẫn chạy | Fencing + idempotency |
| Xử lý ngoại bộ trong transaction rồi tin rollback | HTTP/email không rollback cùng DB | Idempotency/outbox/reconcile |
| Cho rằng broker `send()` trả là persisted | Có thể chỉ đưa vào client buffer | Xác nhận publisher/producer theo broker config |
| Publisher confirm đồng nghĩa consumer done | Hai ACK ở hai ranh giới khác nhau | Consumer ACK sau xử lý |
| Chọn Quartz `RAMJobStore` và tưởng persistent | RAM mất khi process kết thúc | JDBC JobStore với cấu hình phù hợp |
| Chọn Spring Batch resourceless repository cần restart | Không có persisted batch metadata | JDBC-backed JobRepository |
| Chấp nhận vô hạn durable jobs | Backlog/disk/SLA có giới hạn | Admission, quota, alerting |
| Retry với job ID mới sau timeout | Có thể sinh hai công việc logic | Stable idempotency key |

## 26. Bài tập thực hành

### Bài 1 — Phân biệt ba mốc

Một controller nhận `jobId=7`, gọi `executor.submit(...)` và lập tức trả 202. Có thể kết luận job đã bền vững không?

**Đáp án định hướng:** Không. Submission/Future chỉ là state của executor trong process. Cần durable commit/confirm theo hợp đồng API.

### Bài 2 — Nâng queue capacity

Đổi `ThreadPoolTaskExecutor.queueCapacity` từ 100 lên 100.000. Có giảm xác suất mất job khi container bị `SIGKILL` không?

**Đáp án định hướng:** Không tạo persistence. Nó chỉ thay số task có thể chờ ở heap; thậm chí tăng lượng công việc có thể mất và memory pressure.

### Bài 3 — Crash trước dispatch

API đã commit job row `PENDING` vào DB nhưng JVM chết trước khi gọi executor. Thiết kế nào cho phép khôi phục?

**Đáp án định hướng:** Poller/worker từ durable DB hoặc outbox relay phát lại wakeup; không dựa vào callback in-memory duy nhất.

### Bài 4 — H2 qua nhiều JVM

Chạy lần lượt lệnh `enqueue`, `status`, `fail`, `run` và `run` của `DurableJobDemo`. Vì sao `status` ở process mới vẫn thấy `PENDING`?

**Đáp án định hướng:** DB dùng file-backed H2 và yêu cầu đã commit; metadata không nằm trong heap JVM cũ.

### Bài 5 — Rollback

Sau mode `fail` của demo, trạng thái vẫn `PENDING`. Vì sao?

**Đáp án định hướng:** `runOnce` có `@Transactional` qua Spring proxy. `IllegalStateException` làm rollback cập nhật `RUNNING` và attempts; chưa có committed effect.

### Bài 6 — Submit rồi crash

`InMemoryAcceptanceDemo` in `Executor accepted task: true` và `Business effect executed: false`. Tại sao hai dòng không mâu thuẫn?

**Đáp án định hướng:** Task đã vào queue của executor nhưng chưa bắt đầu; `shutdownNow()` trả những tasks còn chờ.

### Bài 7 — Broker ACK

RabbitMQ publisher confirm thành công. Có chắc một consumer đã gửi email không?

**Đáp án định hướng:** Không. Publisher confirm là broker-side acceptance theo cấu hình, consumer processing ACK là bước khác. Cần quan sát worker/effect và dedupe.

### Bài 8 — Quartz store

Một Quartz scheduler dùng `RAMJobStore`, hẹn job sau hai giờ rồi pod bị thay sau một giờ. Job vẫn còn không?

**Đáp án định hướng:** RAMJobStore mất scheduling data khi process kết thúc. Với yêu cầu persistent triggers cần JDBC JobStore và policy misfire/recovery hợp lệ.

### Bài 9 — Backlog

API nhận 500 jobs/phút, worker xử lý 100 jobs/phút. Dù DB lưu bền vững, điều gì xảy ra?

**Đáp án định hướng:** Backlog tăng khoảng 400 jobs/phút khi tốc độ ổn định, gây age/latency/disk tăng; cần capacity planning và admission policy.

### Bài 10 — Atomic claim

Hai pod cùng thấy record `PENDING` rồi đều chạy. `SELECT` trước khi gọi worker có đủ không?

**Đáp án định hướng:** Không. Dùng claim atomic, row lock/CAS, version/lease; side effect vẫn cần idempotency.

### Bài 11 — Lease expiry

Worker A nhận job với lease 30 giây, API ngoài chậm 45 giây; Worker B claim lại sau 30 giây. Có thể có hai worker đồng thời không?

**Đáp án định hướng:** Có. Lease không ngắt A. Cần fencing và idempotency ở nơi ghi effect.

### Bài 12 — Outbox

DB transaction commit thành công, outbox relay đã publish rồi crash trước mark published. Relay có thể gửi lặp không?

**Đáp án định hướng:** Có. Outbox ngăn mất event khi DB commit, không tự bảo đảm một lần delivery; consumer cần dedupe theo event ID.

### Bài 13 — 202 khi mất response

Server đã commit `job-7`, crash trước khi gửi response. Client timeout rồi retry. Làm sao tránh tạo job mới?

**Đáp án định hướng:** Client gửi lại cùng stable request/job ID, server dùng unique constraint và trả trạng thái đã lưu sau kiểm tra payload.

### Bài 14 — Batch metadata

Một ứng dụng Spring Batch dùng `ResourcelessJobRepository` rồi yêu cầu resume sau restart. Có mâu thuẫn gì?

**Đáp án định hướng:** Repository đó không lưu batch metadata bền vững để restart. Chọn repository backed by persistent DB và thiết kế steps restartable.

### Bài 15 — API ngoài

Worker đã gọi payment thành công, DB cập nhật `DONE` thất bại. Lượt sau làm gì để tránh charge hai lần?

**Đáp án định hướng:** Retry cùng idempotency key ở gateway có hỗ trợ, query/reconcile status hoặc compensation theo contract; local transaction không tự undo payment.

## 27. Checklist triển khai production

1. Tuyên bố rõ **độ bền mong muốn** của yêu cầu công việc (JVM restart, node loss, region failure...).
2. Không lấy `TaskExecutor.execute/submit`, `@Async`, `Future` hoặc `TaskScheduler.schedule` làm dấu hiệu “đã lưu”.
3. Job có ID/operation/payload reference/schema version bền vững, được validation/authorization?
4. HTTP 202 chỉ được trả sau **durable commit/confirm** đúng contract?
5. Client có stable idempotency key khi retry do timeout/mất HTTP response?
6. Nếu dùng broker, có publisher confirms/producer acknowledgements và routing/durability settings phù hợp?
7. Consumer ACK chỉ sau khi effect được xử lý đúng; retry có idempotency?
8. Nếu dùng DB jobs, có unique key và atomic claim, không chỉ SELECT?
9. Có lease timeout, heartbeat/recovery và fencing cho worker cũ?
10. `DONE` chỉ được commit sau outcome nghiệp vụ trong ranh giới đã định nghĩa?
11. Có retry/backoff/jitter, max attempts, DLQ/manual intervention?
12. Có poller tìm `PENDING` sau restart/commit-before-dispatch crash?
13. Nếu dùng callback `afterCommit` để wakeup, đã coi đó là tối ưu latency, không phải durable dispatch?
14. Nếu liên hệ DB + broker, đã xử lý dual-write bằng outbox hoặc cơ chế tương đương?
15. Nếu dùng Quartz, `JobStore` thật sự là JDBC khi cần persistent schedule?
16. Nếu dùng Spring Batch, repository và steps thật sự hỗ trợ restart cần thiết?
17. Có quota, retention, backlog capacity và chính sách từ chối khi không thể lưu job?
18. Có metrics cho **persisted accepted** tách biệt **executor accepted**, **worker started** và **business completed**?
19. Có failure tests kill process, rolling deploy, retry duplicate, broker outage, DB outage và lease expiry?
20. Có runbook khôi phục trạng thái mơ hồ/unknown và audit side effects quan trọng?

## 28. Kết luận

- **In-memory acceptance ≠ durable persistence.** Một task vào queue của `ThreadPoolTaskExecutor` vẫn có thể mất khi JVM tắt.
- **`@Async`, `CompletableFuture`, `Future`, `ScheduledFuture` và `@Scheduled` không tự ghi công việc bền vững.** Chúng tổ chức execution/scheduling ở runtime.
- **Commit vào durable job store hoặc broker confirmation theo hợp đồng** mới là mốc có thể dùng để nói “đã nhận bền vững”; điều đó vẫn **không** chứng minh business effect đã hoàn tất.
- **Tách nguồn chân lý và wakeup:** DB/broker giữ công việc; executor giúp xử lý nhanh. Nếu wakeup trong memory mất, worker poll/recovery vẫn tìm lại job.
- **Đối với DB + broker**, transactional outbox giải quyết phần dual-write, nhưng consumer vẫn cần xử lý trùng và retry.
- **Nhiều pod cần atomic claim/lease/fencing**, và tác dụng phụ phải idempotent; scheduler hoặc distributed lock đơn lẻ không đủ.
- **Durability phải được kiểm chứng bằng tắt/mở process và fault injection**, không chỉ thấy `execute()` không ném lỗi.

### Nguồn tài liệu

- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Java SE 17 API — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java SE 17 API — ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [Spring Framework API — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework Reference — Transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Spring Batch Reference — JobRepository](https://docs.spring.io/spring-batch/reference/job/configuring-repository.html)
- [Quartz Java Scheduler — Job Stores (RAM vs JDBC)](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/tutorials/tutorial-lesson-09.html)
- [RabbitMQ — Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms)
- [RabbitMQ — Reliability Guide](https://www.rabbitmq.com/docs/reliability)
- [AWS Prescriptive Guidance — Transactional Outbox Pattern](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html)
- [PostgreSQL Documentation — SELECT / FOR UPDATE SKIP LOCKED](https://www.postgresql.org/docs/current/sql-select.html)
- [Spring JDBC — JdbcTemplate API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/core/JdbcTemplate.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="distributed-limits-006">Xử lý mất tiến trình, khởi động lại và công việc bị bỏ dở</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý mất tiến trình, khởi động lại và công việc bị bỏ dở

Trong **Spring Framework - concurrency**, một tác vụ được gửi vào **ThreadPoolTaskExecutor**, **@Async**, **CompletableFuture** hoặc được kích hoạt qua **@Scheduled/TaskScheduler** có thể đang chạy bình thường thì JVM bị kill, pod bị thay thế, node mất điện hoặc process crash. Lúc ấy, **Future đang nằm trong bộ nhớ, queue của executor và trạng thái ThreadLocal đều biến mất cùng tiến trình**. Chỉ thêm retry trong method hoặc bật graceful shutdown không đủ để khôi phục công việc nếu chúng chưa được lưu vào một nơi bền vững.

Bài Knowledge này thuộc nhóm **Giới hạn phân tán**, với mục tiêu **xử lý mất tiến trình, khởi động lại và công việc bị bỏ dở**. Nội dung phân biệt dừng êm với chết đột ngột; mô hình lưu trạng thái job và các lần thử; atomic claim, lease, heartbeat, fencing token; nhận diện tác vụ bị bỏ dở; tái chạy an toàn nhờ idempotency; Spring Batch và Quartz khi phù hợp; cũng như quan sát và kiểm thử bằng Java 17+.

**Luận điểm cốt lõi:** Nếu một job có giá trị nghiệp vụ quan trọng, câu hỏi không chỉ là “làm sao chạy bất đồng bộ?”, mà còn là **“nếu tiến trình chết ở bất kỳ dòng nào, hệ thống biết công việc nào chưa hoàn tất, ai được nhận lại và làm sao không lặp side effect?”**.

## 1. Phân biệt ba loại vòng đời

Một ứng dụng có thể có ba vòng đời khác nhau:

1. **Vòng đời tiến trình:** JVM/pod đang chạy, nhận tín hiệu dừng, đóng ApplicationContext hoặc chết bất ngờ.
2. **Vòng đời executor/scheduler:** nhận task, xếp hàng, worker bắt đầu chạy, hoàn tất hoặc bị hủy.
3. **Vòng đời business job:** yêu cầu được ghi nhận bền vững, đang xử lý, thành công, cần retry, thất bại cuối cùng hoặc cần con người can thiệp.

Ba vòng đời **không tự đồng bộ với nhau**.

~~~text
HTTP request / cron trigger
          |
          v
  TaskExecutor.submit(...)
          |
          +-- In-memory queue --> worker thread
                                     |
                                     +-- read DB
                                     +-- call remote API
                                     +-- commit side effect
                                     +-- return Future result

JVM crash ở bất kỳ chỗ nào:
  queue RAM mất
  worker thread mất
  Future state mất
  callback cleanup có thể không chạy
  nhưng remote side effect có thể ĐÃ thành công
~~~

**Future.isDone()** hoặc kết quả của **CompletableFuture** chỉ mô tả trạng thái được quan sát trong runtime hiện tại. Một Future của JVM đã chết không phải hồ sơ công việc đủ để JVM mới khôi phục tác vụ. Nếu không có durable job identifier và durable status, chương trình mới thậm chí không biết tác vụ cũ từng tồn tại.

## 2. Graceful shutdown khác crash hoặc mất node

| Tình huống | Context có cơ hội dừng đúng lifecycle? | Các task trong RAM có tự sống qua restart? |
| --- | --- | --- |
| ApplicationContext.close() chủ động | Có, nếu gọi đúng và resource hoạt động | **Không** |
| SIGTERM trong container với thời hạn dừng | Có thể, tùy signal handler, timeout và process | **Không** |
| Rolling deployment, pod được drain đúng | Có thể ngừng nhận việc, drain/cancel theo policy | **Không** |
| SIGKILL / kill -9 | **Không**; không bảo đảm finally, @PreDestroy, destroy callbacks | **Không** |
| OOM kill, kernel crash, mất điện | Không bảo đảm callback | **Không** |
| Network partition nhưng process vẫn sống | Process có thể **vẫn chạy**, dù hệ thống khác xem nó đã chết | Task có thể tiếp tục gây side effect |
| Restart sau deploy/config change | JVM mới và mọi thread mới | Chỉ dữ liệu đã lưu bền vững được tìm lại |

**ThreadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown(true)** và **setAwaitTerminationSeconds(...)** hữu ích cho dừng bình thường. Nhưng nếu deadline kết thúc trước khi công việc xong hoặc process bị SIGKILL, chúng không thể ghi lại mọi Future còn trong queue. Chúng cũng không biến một local queue thành durable queue.

Kubernetes terminationGracePeriodSeconds, readiness drain và preStop hooks là **cơ hội** cho graceful shutdown, không phải cơ chế lưu job. Khi node chết hoặc bị cưỡng bức kết thúc, không có cam kết cleanup sẽ chạy.

### Một ví dụ sống còn

Một worker gửi email rồi cập nhật DB:

~~~text
(1) Gửi email -> provider chấp nhận
(2) JVM crash
(3) Chưa ghi job = SUCCEEDED vào DB
(4) Process mới retry job
(5) Email có nguy cơ gửi lần thứ hai
~~~

Retry đơn thuần giải quyết nguy cơ **mất việc**, nhưng tạo nguy cơ **lặp việc**. Thiết kế cần cả **phục hồi** lẫn **idempotency** hoặc cơ chế đối soát outcome.

## 3. Những trạng thái một job cần có

Một mô hình tối thiểu:

~~~text
PENDING ----claim----> RUNNING ----complete----> SUCCEEDED
    ^                    |
    |                    +----fail retryable----> RETRY_WAIT
    |                    |                           |
    |                    |                       due retry
    |                    |                           |
    |                    +----lease expired---------+
    |                    |
    |                    +----permanent failure----> DEAD
    |
    +----recover/requeue có kiểm tra
~~~

Đây là **mô hình nghiệp vụ**, không phải enum do Spring tự cung cấp. Có thể thêm **CANCEL_REQUESTED**, **CANCELLED**, **PAUSED**, **UNKNOWN_OUTCOME** hoặc **NEEDS_RECONCILIATION** tùy domain.

| Trạng thái | Ý nghĩa | Độ bền cần có |
| --- | --- | --- |
| **PENDING** | Đã ghi nhận yêu cầu, chưa có owner | Durable |
| **RUNNING** | Có worker giữ quyền xử lý; cần owner/lease/token | Durable |
| **RETRY_WAIT** | Lỗi có thể retry, chưa tới mốc tiếp theo | Durable |
| **SUCCEEDED** | Business completion đã được xác nhận theo policy | Durable |
| **DEAD** | Không retry tự động nữa; đã hết hạn hoặc lỗi không sửa được | Durable |
| **CANCEL_REQUESTED** | Có yêu cầu hủy, worker cần hợp tác | Durable nếu cancellation sống qua restart |
| **UNKNOWN_OUTCOME** | Side effect có thể đã thành công nhưng chưa có acknowledgement | Cần đối soát, không retry mù |

Điểm quyết định: **RUNNING không phải bằng chứng worker còn sống.** Nếu tiến trình chết, database có thể tiếp tục lưu RUNNING vô hạn. Cần quy tắc phát hiện job bị bỏ dở.

### ID của job khác ID của attempt

- **jobId** là danh tính logic của công việc, ví dụ xuất báo cáo cho ngày 2026-10-29.
- **attemptId/generation/leaseToken** đại diện cho **lần một worker nhận quyền thực thi**.
- **idempotencyKey** xác định business effect phải chỉ được commit một lần, có thể trùng jobId hoặc do domain tạo.
- **ownerId** là process/worker nắm lease; nên có phiên bản instance độc nhất mỗi lần khởi động.
- **correlationId** liên kết log và request, không tự thay thế jobId.

**Retry attempt mới không được tự tạo một business job mới** nếu yêu cầu là tiếp tục đúng công việc cũ. Đó là lý do phải giữ jobId ổn định qua retry/restart.

## 4. Ranh giới quan trọng nhất: chấp nhận job trước hay sau khi lưu?

Anti-pattern trong controller:

~~~java
@PostMapping("/exports")
public ResponseEntity<Void> export() {
    executor.execute(() -> exportReport());
    return ResponseEntity.accepted().build();
}
~~~

Đây là **đoạn Spring MVC minh họa** không độc lập. Nếu submit thành công nhưng JVM chết trước khi worker chạy, caller đã nhận **202 Accepted** nhưng không có hồ sơ nào để process mới khôi phục. Nếu submit bị từ chối và exception bị nuốt, trạng thái trả về còn sai hơn.

Một thiết kế đáng tin hơn:

~~~text
HTTP request
    |
    v
Xác thực, validate, tạo immutable job command
    |
    v
DB transaction:
    INSERT work_item(jobId, payload, state=PENDING, idempotencyKey)
    COMMIT
    |
    v
Trả 202 Accepted + jobId
    |
    v
Worker poll/claim từ DB rồi thực thi
~~~

Nếu process chết **sau commit nhưng trước HTTP response**, client có thể gửi lại; **unique idempotency key** giúp lấy lại jobId cũ thay vì tạo hai jobs.

Nếu process chết **trước commit**, job chưa được nhận bền vững; server không nên tuyên bố đã tiếp nhận thành công. Nếu API response bị mất sau commit, client phải dùng idempotency key hoặc status endpoint để phân biệt.

### Ví dụ transaction hợp lệ

Trong một Spring service, ghi job vào DB bằng @Transactional ở **request thread**, rồi để worker đọc từ DB sau commit. Không cần đặt task vào executor bên trong cùng transaction và kỳ vọng task tự chờ commit; worker có thể chạy trước khi transaction commit, không nhìn thấy job vừa ghi.

**Pattern lưu trước, poll sau** có ưu điểm là không có khoảng “đã gửi việc trong RAM nhưng chưa được ghi” sau khi API xác nhận. Nếu dùng event/queue, hãy chọn một giao thức commit/ack tương đương, thường kết hợp **outbox**.

## 5. Crash có thể rơi vào những vị trí nào?

Một job đi qua nhiều ranh giới; mỗi ranh giới cần policy riêng.

| Điểm crash | DB thường nhìn thấy | Nguy cơ | Cách phục hồi |
| --- | --- | --- | --- |
| Trước khi insert job | Không có job | Caller không rõ request có tới không | Idempotent request/submission; không nhận thành công trước commit |
| Sau khi insert PENDING, trước dispatch | PENDING | Task không chạy nếu chỉ dựa vào dispatch một lần | Poller/queue consumer tìm PENDING rồi claim |
| Sau claim RUNNING, trước khi body chạy | RUNNING có owner/lease | Job mắc kẹt nếu không có recovery | Lease timeout + reclaim/requeue |
| Giữa xử lý, trước side effect | RUNNING | Công việc dở dang | Retry từ checkpoint hoặc chạy lại an toàn |
| Sau side effect, trước ack SUCCEEDED | RUNNING | **Nguy cơ double effect** khi retry | Idempotency key/uniqueness/outbox/đối soát |
| Sau ack SUCCEEDED, trước response | SUCCEEDED | Caller có thể tưởng thất bại | Status lookup theo jobId/idempotencyKey |
| Đang ghi nhiều hệ thống khác nhau | Có thể partial success | Không có transaction xuyên hệ thống | Saga/compensation, checkpoint, reconciliation |
| Khi executor đang shutdown | Có PENDING/RUNNING hoặc accepted jobs | Bị reject hoặc dừng giữa chừng | Stop producers; durable state; recovery sau restart |

**Mỗi dòng trong bảng là một failure injection test đáng làm**. Chỉ thử crash trước khi worker bắt đầu là chưa đủ; trường hợp khó nhất thường là **side effect đã xảy ra nhưng acknowledgement chưa được lưu**.

## 6. Ba mức đảm bảo thường gặp

Cần nói rõ **đảm bảo nào** được cung cấp, ở **ranh giới nào**:

**At-most-once:** có thể bỏ việc khi mất process nhưng không cố retry; giảm nguy cơ trùng, đổi lại có thể mất job.

**At-least-once:** job có thể được giao lại nếu chưa xác nhận hoàn tất; giảm nguy cơ mất việc, **có thể xử lý nhiều lần**.

**Exactly-once business effect:** hiệu ứng nghiệp vụ cuối cùng chỉ xuất hiện một lần trong phạm vi một hệ thống được xác định. Nó thường được xây từ **at-least-once delivery + idempotency/unique constraints/atomic transactions**. Không nên hứa exactly-once cho chuỗi gồm database, HTTP, email và payment gateway chỉ vì dùng một executor hay một message broker.

Ví dụ: job gửi một thông báo với key **invoice-123-reminder**. Nếu email provider hỗ trợ idempotency key có hợp đồng rõ, dùng cùng key qua retries. Nếu không, có thể cần lưu outbox, deduplicate ở phía nhận hoặc chấp nhận vùng **UNKNOWN_OUTCOME** cần reconciliation.

**“Xử lý đúng một lần” phải nêu rõ một lần cái gì:** một lần nhận message, một lần chạy Java method, một lần INSERT, một lần chuyển tiền, hay một lần người dùng nhận email. Các câu đó không tương đương.

## 7. Lease giúp nhận diện worker đã bỏ dở

Một trường **lease_until** định nghĩa đến thời điểm nào một worker được xem là có quyền nhận/tiếp tục job:

~~~text
t=00   worker A claim job J, lease_until = t+30s
t=05   A bắt đầu gọi dependency
t=06   A crash, không thể heartbeat
t=10   B thử claim J -> bị từ chối, lease A còn hạn
t=31   B thấy lease đã hết -> có thể reclaim J
t=32   B chạy attempt mới; token/generation tăng
~~~

**Lease không phải lock vĩnh viễn**; nếu A không còn cập nhật heartbeat trong khoảng hợp lệ, job trở thành eligible for recovery.

### Các field thường cần

~~~text
job_id            stable business/job identity
state             PENDING | RUNNING | RETRY_WAIT | SUCCEEDED | DEAD
payload           immutable command / validated input
owner_id          worker instance identity
lease_until       timestamp do DB time tính
lease_token       monotonically increasing generation
attempts          số lần claim/attempt
next_attempt_at   thời điểm được phép retry
created_at        created timestamp
updated_at        last state change
last_error_code   optional sanitized error information
idempotency_key   stable business-effect dedupe identity
version           row version / optimistic concurrency nếu cần
~~~

Một thiết kế có thể thêm **deadline_at**, **heartbeat_at**, **checkpoint**, **max_attempts** và **priority**.

**Không chỉ dùng một cột boolean isRunning.** Nếu worker chết, isRunning=true không cho biết có thể giải phóng khi nào. Thêm owner và thời hạn lease tạo cơ sở rõ cho recovery, nhưng phải tránh reclaim quá sớm khi worker còn sống.

## 8. Atomic claim: chỉ một worker thắng ở mỗi lượt

Khi có nhiều pods cùng poll, thao tác:

~~~text
SELECT job WHERE state=PENDING
if found:
    UPDATE job SET state=RUNNING
~~~

là **không an toàn nếu SELECT và UPDATE không được bảo vệ bằng transaction/điều kiện**. Hai workers có thể đọc cùng PENDING trước khi cả hai cập nhật.

Một mô hình đúng phải đảm bảo **atomic claim**:

- Dùng **database transaction + row-level lock** hoặc **conditional update/CAS**.
- Tại thời điểm claim, kiểm tra state, retry time và lease hết hạn.
- Ghi owner, lease deadline và **tăng generation/token** atomically.
- Chỉ trả job payload cho worker nếu claim thực sự thành công.
- Release DB transaction ngắn; **không giữ row lock trong toàn thời gian gọi HTTP/DB kéo dài**.

### PostgreSQL: ví dụ atomic claim bằng SKIP LOCKED

Đây là **SQL minh họa dành cho PostgreSQL**, không phải SQL portable trên mọi database. Bảng work_item phải có các cột tương ứng:

~~~sql
WITH candidate AS (
    SELECT job_id
    FROM work_item
    WHERE attempts < :max_attempts
      AND (
          (state = 'PENDING' AND next_attempt_at <= now())
          OR
          (state = 'RUNNING' AND lease_until <= now())
      )
    ORDER BY created_at, job_id
    LIMIT 1
    FOR UPDATE SKIP LOCKED
)
UPDATE work_item AS j
SET state = 'RUNNING',
    owner_id = :worker_id,
    lease_token = j.lease_token + 1,
    lease_until = now() + INTERVAL '60 seconds',
    attempts = j.attempts + 1,
    updated_at = now()
FROM candidate
WHERE j.job_id = candidate.job_id
RETURNING j.job_id, j.payload, j.lease_token,
          j.lease_until, j.attempts;
~~~

**Giải thích:**

1. **FOR UPDATE** khóa row ứng viên trong transaction đang claim.
2. **SKIP LOCKED** bỏ qua những rows đang bị transaction khác giữ lock, hữu ích cho nhiều consumers của một queue-like table.
3. **UPDATE ... FROM candidate ... RETURNING** thực hiện chuyển trạng thái và trả claim trong cùng câu lệnh/transaction.
4. **lease_token tăng** giúp phân biệt attempt mới với attempt cũ.
5. **now()** lấy thời gian theo database trong transaction; worker không tự áp dụng local clock khác nhau khi quyết định lease đã hết hạn.
6. Điều kiện **attempts < max_attempts** là minh họa giới hạn retry; production phải xử lý các jobs đã hết attempts sang DEAD hoặc manual review.

**Điểm hạn chế:** SKIP LOCKED tạo một góc nhìn không nhất quán cho mục đích truy vấn tổng quát, nhưng phù hợp cho tranh chấp ở một queue-like table. Nó không bảo đảm fairness tuyệt đối; không có cột ORDER BY nào tự đảm bảo job lâu năm không bị starvation khi ưu tiên/locks thay đổi. Với lease expiration, cần cân nhắc dead worker và những workers đang chậm hoặc bị pause.

**Không bắt transaction mở tới lúc business job hoàn thành.** Hãy commit claim nhanh, rồi xử lý job ngoài transaction claim; các thay đổi hiệu ứng nghiệp vụ có transaction riêng theo yêu cầu.

## 9. Heartbeat: giữ lease trong công việc dài

Nếu một job chạy vài phút nhưng lease ban đầu chỉ 60 giây, worker sống phải có cơ chế **renew lease** trước khi hết hạn.

Một SQL ví dụ:

~~~sql
UPDATE work_item
SET lease_until = now() + INTERVAL '60 seconds',
    updated_at = now()
WHERE job_id = :job_id
  AND state = 'RUNNING'
  AND owner_id = :worker_id
  AND lease_token = :lease_token
  AND lease_until > now();
~~~

**Chỉ khi update count = 1** thì worker biết lease của mình vẫn còn hợp lệ tại thời điểm statement. Nếu update count = 0, job có thể bị hoàn tất/hủy, token đã thay đổi hoặc lease hết hạn. Worker phải **ngừng các side effects tiếp theo hoặc chuyển sang trạng thái an toàn**; không cố ghi SUCCEEDED bằng token cũ.

### Không renew kiểu mù

Nếu heartbeat chạy:

~~~sql
UPDATE work_item
SET lease_until = now() + INTERVAL '60 seconds'
WHERE job_id = :job_id;
~~~

thì một worker A đã mất quyền vẫn có thể gia hạn row do worker B mới nhận, phá cơ chế ownership. Vì vậy heartbeat phải mang **owner + generation/token + state + điều kiện thời gian**.

### Lease duration và heartbeat interval

- Lease quá ngắn: pause GC, mạng chậm hoặc DB hiccup có thể khiến worker còn sống mất lease, từ đó tạo hai attempts song song.
- Lease quá dài: job từ worker đã chết phải chờ lâu trước khi được reclaim.
- Heartbeat quá dày: tăng tải DB; heartbeat quá thưa: dễ timeout sai.
- Nên có **quy tắc backoff** khi DB lỗi, giới hạn số lần fail trước khi worker tự ngừng side effect.
- Dùng **thời gian từ một nguồn nhất quán**, thường là database clock cho các điều kiện SQL; không tin tuyệt đối đồng hồ ở các pods đồng bộ đến từng mili giây.

Một heartbeat **không chứng minh business progress**, nó chỉ cho biết owner vẫn có thể liên hệ cơ chế coordination. Với job dài, cân nhắc thêm **progress checkpoint** riêng.

## 10. Fencing token: chống worker cũ quay lại

Lease hết hạn không có nghĩa process cũ thật sự đã chết. Có thể nó chỉ bị pause, chập chờn mạng hoặc mất kết nối DB nhưng vẫn gọi được một dịch vụ khác:

~~~text
A claim J, token=41
A bị GC pause / network partition
Lease của A hết hạn
B claim J, token=42
B bắt đầu xử lý
A tỉnh dậy và cố ghi kết quả bằng token=41
~~~

Nếu chỉ xem **ownerId trong RAM** hoặc tin “A đã chết”, A có thể ghi đè dữ liệu mới của B.

**Fencing token** là số phiên bản tăng dần mỗi lần claim. Khi cập nhật dữ liệu do job quản lý, tài nguyên nhận kết quả phải **từ chối token cũ**:

~~~sql
UPDATE work_item
SET state = 'SUCCEEDED',
    owner_id = NULL,
    lease_until = NULL,
    updated_at = now()
WHERE job_id = :job_id
  AND state = 'RUNNING'
  AND owner_id = :worker_id
  AND lease_token = :lease_token
  AND lease_until > now();
~~~

Nếu update count = 0, worker không còn đủ quyền **ack job**; cần log/metric stale attempt.

**Quan trọng:** câu UPDATE bảo vệ **row work_item**, không tự bảo vệ một API bên ngoài đã chấp nhận request từ A. Nếu A gửi tiền hoặc ghi file trước khi kiểm tra token, side effect vẫn có thể xảy ra. Khi fencing thực sự cần, **nơi nhận side effect** phải hỗ trợ kiểm token/serial version hoặc **idempotency key/unique constraint**. Fencing ở job table không tự ngăn mọi external action.

## 11. Idempotency: chạy lại nhưng không nhân đôi tác động nghiệp vụ

**Idempotent handler** có nghĩa việc nhận lại cùng logical command/attempt không khiến side effect bị áp dụng hai lần theo phạm vi đã định.

Một giao thức DB-only dễ kiểm soát:

~~~text
BEGIN TRANSACTION

1) Kiểm tra job J vẫn thuộc owner/token hiện hành
2) INSERT business_effect(job_id UNIQUE, result...)
   ON CONFLICT(job_id) DO NOTHING
3) Update business data nếu cần theo unique constraint/version
4) UPDATE work_item SET state=SUCCEEDED
   WHERE job_id=J AND owner/token hiện hành

COMMIT
~~~

Khi cả **business effect và ack** thuộc **cùng database transaction**, crash ở trước commit làm cả hai rollback; crash sau commit có thể được phát hiện từ durable state.

Nếu bước 2 và 4 nằm trong **hai transactions riêng**, có thể xảy ra:

~~~text
effect committed
    |
JVM crash
    |
ack chưa committed
    |
job reclaimed
    |
effect bị thử lần nữa
~~~

Đó là lúc **unique business key / UPSERT / idempotent operation** phải bảo đảm không nhân đôi hiệu ứng.

### Dùng unique key nào?

Không phải lúc nào jobId cũng là idempotency key đúng:

- Một job xuất báo cáo theo kỳ có thể dùng **reportType + tenantId + businessDate + version**.
- Một lệnh gửi email có thể dùng **notificationId** của sự kiện nguồn.
- Một payment có thể dùng **paymentIntentId** mà gateway hiểu và chấp nhận.
- Một batch chuyển dữ liệu có thể dùng **sourceRecordId + transformationVersion**.

**Đừng gán UUID mới mỗi attempt** cho key cần deduplicate giữa retries. Một key mới mỗi retry thường biến việc trùng thành nhiều tác vụ hợp lệ về mặt kỹ thuật, phá mục tiêu exactly-once effect.

## 12. Java 17 chạy được: mô phỏng crash, lease recovery và stale attempt

Chương trình dưới đây là **một mô hình in-memory có đồng hồ cố định**. Nó không tự persist xuyên JVM, nhưng mô phỏng đầy đủ **trạng thái mà một durable job store phải giữ** và cho đầu ra xác định, không dùng Thread.sleep.

Tình huống:

1. Worker A claim job, token 1.
2. A ghi hiệu ứng nghiệp vụ mô phỏng, rồi “crash” trước khi ack hoàn tất.
3. B không được claim khi lease A còn hạn.
4. Sau khi lease hết hạn, B claim lại với token 2.
5. Ack của A cũ bị từ chối.
6. B thấy hiệu ứng đã ghi theo jobId, không ghi trùng, rồi ack SUCCEEDED.
7. Worker C không thể nhận lại một job đã thành công.

Lưu thành **LeaseRecoveryDemo.java**:

~~~java
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class LeaseRecoveryDemo {
    enum State { PENDING, RUNNING, SUCCEEDED }

    record Claim(String jobId, String owner, long token) {}

    static final class Job {
        final String id;
        State state = State.PENDING;
        String owner;
        Instant leaseUntil;
        long token = 0;
        int attempts = 0;
        boolean effectApplied = false;

        Job(String id) {
            this.id = id;
        }
    }

    static final class Store {
        private final Map<String, Job> jobs = new HashMap<>();

        synchronized void submit(String id) {
            if (jobs.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate job");
            }
            jobs.put(id, new Job(id));
        }

        private Job get(String id) {
            Job job = jobs.get(id);
            if (job == null) {
                throw new IllegalArgumentException("Unknown job");
            }
            return job;
        }

        synchronized Claim claim(
                String id, String owner, Instant now,
                Duration leaseDuration) {

            Job job = get(id);

            if (job.state == State.SUCCEEDED) {
                return null;
            }

            if (job.state == State.RUNNING
                    && now.isBefore(job.leaseUntil)) {
                return null;
            }

            job.state = State.RUNNING;
            job.owner = owner;
            job.token++;
            job.attempts++;
            job.leaseUntil = now.plus(leaseDuration);

            return new Claim(id, owner, job.token);
        }

        private boolean owns(
                Job job, Claim claim, Instant now) {

            return job.state == State.RUNNING
                    && job.token == claim.token()
                    && claim.owner().equals(job.owner)
                    && now.isBefore(job.leaseUntil);
        }

        synchronized boolean applyEffectIfAbsent(
                Claim claim, Instant now) {

            Job job = get(claim.jobId());
            if (!owns(job, claim, now)) {
                return false;
            }

            if (job.effectApplied) {
                return false;
            }

            job.effectApplied = true;
            return true;
        }

        synchronized boolean acknowledge(
                Claim claim, Instant now) {

            Job job = get(claim.jobId());

            if (!owns(job, claim, now)
                    || !job.effectApplied) {
                return false;
            }

            job.state = State.SUCCEEDED;
            job.owner = null;
            job.leaseUntil = null;
            return true;
        }

        synchronized String summary(String id) {
            Job job = get(id);
            return job.state + ", attempts=" + job.attempts
                    + ", effectApplied=" + job.effectApplied;
        }
    }

    public static void main(String[] args) {
        Store store = new Store();
        String id = "report-2026-10-29";
        Instant t0 = Instant.parse("2026-10-29T00:00:00Z");
        Duration lease = Duration.ofSeconds(30);

        store.submit(id);

        Claim a = store.claim(id, "worker-A", t0, lease);

        System.out.println("A nhận job: " + (a != null));
        System.out.println("A ghi hiệu ứng: "
                + store.applyEffectIfAbsent(
                        a, t0.plusSeconds(5)));

        // Giả lập A chết trước khi acknowledge().
        Claim beforeExpiry = store.claim(
                id, "worker-B", t0.plusSeconds(10), lease);

        System.out.println("B nhận khi lease còn hạn: "
                + (beforeExpiry != null));

        Claim b = store.claim(
                id, "worker-B", t0.plusSeconds(31), lease);

        System.out.println("B nhận sau lease hết hạn: "
                + (b != null));
        System.out.println("Token A / B: "
                + a.token() + " / " + b.token());

        // Giả lập A sống lại: token cũ không còn hợp lệ.
        System.out.println("Ack từ A đã cũ: "
                + store.acknowledge(a, t0.plusSeconds(32)));

        System.out.println("B tạo hiệu ứng lần thứ hai: "
                + store.applyEffectIfAbsent(
                        b, t0.plusSeconds(32)));

        System.out.println("B ack thành công: "
                + store.acknowledge(
                        b, t0.plusSeconds(33)));

        Claim c = store.claim(
                id, "worker-C", t0.plusSeconds(40), lease);

        System.out.println("C nhận job đã hoàn tất: "
                + (c != null));
        System.out.println("Trạng thái cuối: "
                + store.summary(id));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 LeaseRecoveryDemo.java
java LeaseRecoveryDemo
~~~

**Stdout xác định:**

~~~text
A nhận job: true
A ghi hiệu ứng: true
B nhận khi lease còn hạn: false
B nhận sau lease hết hạn: true
Token A / B: 1 / 2
Ack từ A đã cũ: false
B tạo hiệu ứng lần thứ hai: false
B ack thành công: true
C nhận job đã hoàn tất: false
Trạng thái cuối: SUCCEEDED, attempts=2, effectApplied=true
~~~

### Giải thích từng điểm

- **Store.claim** là thao tác synchronized: trong mô hình một process, không có hai lời claim cùng lúc đều thắng trên state đó.
- **Lease** cho phép B nhận lại job khi thời điểm được truyền vào vượt deadline của A.
- **Token tăng từ 1 lên 2**, nên A không thể ack với token 1 dù process cũ tỉnh lại.
- **effectApplied** mô phỏng một hiệu ứng có unique key jobId. Khi B retry, nó không tạo hiệu ứng thứ hai.
- Vì ack của B hợp lệ, state sang SUCCEEDED và C không thể claim lại.
- **Không có thời gian thực, không có race scheduler**, do các thời điểm Instant được mô phỏng rõ.

**Giới hạn bắt buộc phải hiểu:** HashMap trong ví dụ nằm **trong RAM** và sẽ mất khi JVM thực sự chết. Nó chỉ **diễn giải thuật toán**. Ở production, Store phải được thay bằng **database/queue bền vững**, atomic claim phải do database/coordination mechanism bảo đảm, và effectApplied phải được bảo vệ bởi **unique business key hoặc idempotency ở hệ thống nhận side effect**. synchronized của Java không tạo khóa xuyên nhiều pods.

## 13. Java 17 chạy hai lần: checkpoint file tồn tại qua khởi động lại

Để tự tay thấy **dữ liệu bền vững** khác **biến trong RAM**, ví dụ thứ hai dùng một file local đơn giản. Nó không xây queue production, nhưng cho phép chạy chương trình thành **nhiều tiến trình Java độc lập**.

Lưu **FileRestartDemo.java**:

~~~java
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileRestartDemo {
    private static final Path STATE =
            Path.of("demo-job-state.txt");

    private static String read() throws Exception {
        return Files.readString(
                STATE, StandardCharsets.UTF_8).trim();
    }

    private static void write(String state) throws Exception {
        Files.writeString(
                STATE, state + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Cần reset|claim|recover|finish");
        }

        String action = args[0];

        switch (action) {
            case "reset" -> {
                write("PENDING");
                System.out.println("reset: PENDING");
            }
            case "claim" -> {
                String old = read();
                if ("PENDING".equals(old)) {
                    write("RUNNING");
                    System.out.println("claim: RUNNING");
                } else {
                    System.out.println("claim: " + old);
                }
            }
            case "recover" -> {
                String old = read();
                if ("RUNNING".equals(old)) {
                    write("PENDING");
                    System.out.println("recover: PENDING");
                } else {
                    System.out.println("recover: " + old);
                }
            }
            case "finish" -> {
                String old = read();
                if ("RUNNING".equals(old)) {
                    write("SUCCEEDED");
                    System.out.println("finish: SUCCEEDED");
                } else {
                    System.out.println("finish: " + old);
                }
            }
            default -> throw new IllegalArgumentException(
                    "Unknown action: " + action);
        }
    }
}
~~~

Biên dịch rồi chạy **từng lệnh như các lần khởi động JVM riêng**, từ cùng một thư mục:

~~~bash
javac -encoding UTF-8 FileRestartDemo.java
java FileRestartDemo reset
java FileRestartDemo claim
# Giả sử process claim đã chết mà chưa finish.
java FileRestartDemo recover
java FileRestartDemo claim
java FileRestartDemo finish
java FileRestartDemo recover
~~~

**Stdout theo thứ tự:**

~~~text
reset: PENDING
claim: RUNNING
recover: PENDING
claim: RUNNING
finish: SUCCEEDED
recover: SUCCEEDED
~~~

Lệnh **recover** đầu tiên đóng vai trò operator/quy trình recovery được chạy **sau khi đã xác nhận owner cũ không còn hợp lệ**. Lệnh cuối chứng minh SUCCEEDED không bị đưa lại vào queue.

**Tại sao ví dụ này không phải durable queue production?**

- **Files.writeString + TRUNCATE_EXISTING không atomic và không bảo đảm fsync** khi mất điện.
- Không có lock liên tiến trình, nên hai processes có thể đọc/ghi đè đồng thời.
- Không có lease/generation, nên recovery mù có thể lấy job từ một worker **vẫn đang sống**.
- Một filesystem local của pod/container có thể bị xóa khi pod bị thay thế; **file nằm trên disk không tự đồng nghĩa storage bền vững qua node loss**.
- Không có business effect/idempotency, schema migration hoặc retry policy.

Bài học chỉ là **state persist qua nhiều lần chạy**. Với production, dùng DB/broker/job store có transaction và availability đúng yêu cầu, không chuyển nguyên ví dụ file thành queue đa tiến trình.

## 14. Spring @Async, TaskExecutor và CompletableFuture không tự phục hồi

Một đoạn:

~~~java
@Async("jobExecutor")
public CompletableFuture<Void> generateReport(String reportId) {
    createReport(reportId);
    return CompletableFuture.completedFuture(null);
}
~~~

Đây là **method minh họa trong Spring-managed bean**. Nó có thể rất hữu ích cho responsiveness của request, nhưng nếu JVM bị kill:

- Không có worker nào của JVM mới tự nhận lại method call đó.
- Caller không thể truy vấn Future cũ nếu chỉ giữ reference trong RAM.
- Queue của ThreadPoolTaskExecutor không tự được serialize vào DB.
- CompletionException, cancellation flag và ThreadLocal context của tiến trình cũ không tồn tại ở JVM mới.
- Gọi **@Async** qua Spring proxy không thay đổi những sự thật trên.

**Nguyên tắc:** @Async là cách **chuyển việc trong process**; durable job orchestration là một bài toán khác. Có thể kết hợp hai tầng: request persist job → poller claim durable job → @Async/TaskExecutor dùng thread pool để xử lý attempt đang được sở hữu.

### @Transactional không xuyên thread

Nếu controller mở transaction và gọi @Async, transaction của caller **không tự truyền sang worker**. Nếu worker cần transaction, mở một transaction mới ở service method được worker gọi qua proxy, hoặc dùng TransactionTemplate/JdbcTemplate theo contract.

Không mở một transaction kéo dài suốt thời gian job gọi HTTP hàng phút chỉ để “giữ lock”. Claim cần transaction ngắn; effect transaction được thiết kế riêng, và remote calls cần timeout/compensation.

## 15. @Scheduled/TaskScheduler chỉ giúp tìm việc, không phải job store

Một cron:

~~~java
@Scheduled(fixedDelay = 1_000)
public void scanPendingJobs() {
    // Tìm những job đã lưu bền vững và đến hạn.
}
~~~

Đây là **method minh họa**. Mỗi pod có thể chạy callback riêng, nhưng nếu **claim là atomic**, nhiều pollers có thể cùng cạnh tranh mà không nhận trùng một attempt hợp lệ.

**Điều không nên làm:**

~~~java
@Scheduled(cron = "0 0 0 * * *")
public void dailyReport() {
    executor.execute(this::generateDailyReport);
}
~~~

Nếu chỉ dùng in-memory executor, process chết có thể làm job mất. Khi có nhiều pods, cùng cron có thể sinh nhiều reports. Bản thân cron không có trạng thái job, idempotency hay recovery.

**Thiết kế tốt hơn:** cron tạo hoặc phát hiện **durable job record** theo key **report-type + business-date + tenant**, DB unique constraint ngăn tạo trùng; workers claim theo lease; sau restart, poller tìm các PENDING/retryable/expired jobs còn trong DB.

### FixedDelay không chờ worker job đã delegate

Nếu scheduled callback chỉ gọi executor.execute(...) rồi return, fixedDelay được đo sau **callback**, không sau business job. Điều này có thể tạo nhiều jobs cùng loại đang in-flight. Muốn không overlap, dùng **single-flight/job key/claim policy** riêng, không dựa duy nhất vào fixedDelay.

## 16. Spring service design: producer, claimer và processor tách rời

Dưới đây là **các đoạn Spring minh họa tích hợp**, không phải một dự án tự biên dịch vì cần repository/DAO, schema và dependency injection của ứng dụng. Mục tiêu là thấy rõ ranh giới chịu trách nhiệm.

**A. Job submission service**:

~~~java
@Service
public class JobSubmissionService {
    private final WorkItemRepository jobs;

    public JobSubmissionService(WorkItemRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public String requestReport(ReportCommand command) {
        // command đã validate và có stable idempotency key.
        // Database UNIQUE(idempotency_key) bảo vệ retry.
        return jobs.insertOrFindPending(command);
    }
}
~~~

**B. Poller chỉ điều phối và giao việc**:

~~~java
@Component
public class RecoveryAwarePoller {
    private final JobClaimService claimService;
    private final JobProcessor processor;
    private final TaskExecutor executor;

    public RecoveryAwarePoller(
            JobClaimService claimService,
            JobProcessor processor,
            @Qualifier("jobExecutor") TaskExecutor executor) {

        this.claimService = claimService;
        this.processor = processor;
        this.executor = executor;
    }

    @Scheduled(fixedDelay = 1_000)
    public void tick() {
        // Giới hạn số claim mỗi tick để không lấp queue vô hạn.
        for (int i = 0; i < 4; i++) {
            Claim attempt = claimService.tryClaimNext();
            if (attempt == null) {
                return;
            }

            try {
                executor.execute(
                        () -> processor.process(attempt));
            } catch (TaskRejectedException rejected) {
                // Release có điều kiện token hoặc cho lease timeout.
                claimService.releaseIfStillOwned(attempt);
                return;
            }
        }
    }
}
~~~

**C. Processor sở hữu một attempt**:

~~~java
@Service
public class JobProcessor {
    private final JobClaimService claims;
    private final ReportEffectService effects;

    public JobProcessor(
            JobClaimService claims,
            ReportEffectService effects) {
        this.claims = claims;
        this.effects = effects;
    }

    public void process(Claim attempt) {
        try {
            // Kiểm tra attempt chưa hết quyền trước side effect.
            if (!claims.isLeaseValid(attempt)) {
                return;
            }

            effects.applyIdempotently(attempt);
            claims.completeIfOwner(attempt);
        } catch (TransientJobException retryable) {
            claims.scheduleRetryIfOwner(attempt, retryable);
        } catch (PermanentJobException fatal) {
            claims.markDeadIfOwner(attempt, fatal);
        }
    }
}
~~~

**Các type Claim, WorkItemRepository, ReportCommand, các exception và service domain** ở đây là abstractions của ứng dụng; phải được định nghĩa riêng. Chúng không phải class có sẵn trong Spring. Việc phân tách cho thấy:

1. **JobSubmissionService** làm durable write trước khi xác nhận.
2. **JobClaimService** được gọi qua một bean khác để transaction claim ngắn và commit đúng.
3. **RecoveryAwarePoller** không làm business I/O trong scheduler thread; nó chỉ gửi số lượng attempt hữu hạn.
4. **JobProcessor** kiểm tra ownership và kết quả; failure policy nằm ở DB state, không chỉ trong logs.
5. Nếu executor reject sau claim, release phải **có điều kiện owner/token**, không ghi PENDING mù làm mất claim của worker khác.

### Cảnh báo về khoảng claim → worker start

Poller có thể claim J, nhưng J nằm trong queue 40 giây trước khi worker rảnh. Nếu lease chỉ 30 giây, pod khác có thể reclaim khi task đầu **còn chưa bắt đầu**.

Cách xử lý:
- Queue hữu hạn và đo queue wait.
- Lease đủ cho thời gian chờ/độ trễ hợp lý, có renewal hoặc claim tại worker boundary.
- Khi worker bắt đầu phải kiểm tra token/lease hiện hành.
- Nếu đã hết hạn thì từ chối thực thi, không “đánh cắp lại” quyền từ attempt mới.
- Không chờ tới lúc gọi external side effect mới phát hiện lease hết hạn.

Đây là lý do **ThreadPoolTaskExecutor queueCapacity** và **lease duration** là hai cấu hình liên quan nhau, dù một thuộc runtime thread pool và một thuộc durable coordination.

## 17. Tại sao “requeue RUNNING quá lâu” không đủ?

Một sweeper đơn giản:

~~~sql
UPDATE work_item
SET state = 'PENDING'
WHERE state = 'RUNNING';
~~~

là **sai và nguy hiểm** khi ứng dụng còn workers hoạt động. Nó có thể giải phóng mọi jobs đúng lúc chúng đang xử lý.

Ngay cả:

~~~sql
UPDATE work_item
SET state = 'PENDING'
WHERE state = 'RUNNING'
  AND updated_at < now() - INTERVAL '5 minutes';
~~~

cũng chưa đủ nếu updated_at chỉ ghi lúc claim và job hợp lệ chạy 30 phút.

**Tiêu chí tốt hơn** thường dựa vào:
- **lease_until <= database now()**.
- Worker có **heartbeat không được gia hạn trong cửa sổ**.
- Có **owner/lease_token** để tránh stale ack.
- Có **deadline**, **attempt cap**, **next_attempt_at**.
- Có policy **UNKNOWN_OUTCOME** nếu side effect không thể xác minh.
- Có biện pháp chống tranh chấp khi network partition hoặc worker tạm pause.

Sweeper có thể:
1. Đưa expired jobs sang PENDING/RETRY_WAIT có điều kiện.
2. Hoặc cho phép **claim trực tiếp một RUNNING đã hết lease** trong SQL atomic claim.
3. Ghi event/metric recovery thay vì im lặng overwrite.
4. Chuyển quá số attempts sang DEAD để triage.

**Một quy trình recovery không được tự tái ghi trạng thái SUCCEEDED thành PENDING.** Đó là đường dẫn dễ gây double processing nhất.

## 18. Retry policy: không phải lỗi nào cũng nên chạy lại

Một phân loại hữu ích:

| Loại lỗi | Ví dụ | Hành động |
| --- | --- | --- |
| **Transient** | 503, connection reset, lock contention có thể giải quyết | Retry hữu hạn với backoff + jitter |
| **Timeout có outcome chưa rõ** | Gateway đã nhận request nhưng client timeout | Tra cứu trạng thái/idempotency, không gửi lại mù |
| **Permanent validation** | Payload sai schema, id/permission không hợp lệ | DEAD hoặc NEEDS_REVIEW; không retry vô hạn |
| **Business conflict** | Version mismatch, tài nguyên đã bị xóa | Đánh giá lại command và policy |
| **Worker crash** | Lease hết hạn, không có ack | Reclaim theo token và idempotency |
| **Cancellation hợp lệ** | Người dùng hủy job trước side effect | Chuyển CANCELLED nếu state transition hợp lệ |
| **Poison message** | Cùng payload luôn gây bug/deserialization failure | Quarantine/DLQ, alert và sửa code |

Tính toán retry:
- **next_attempt_at** phải ghi vào job store, không chỉ Thread.sleep trong worker.
- **attempts** tăng atomically khi claim/attempt theo định nghĩa của bạn.
- **backoff + jitter** giảm hiệu ứng nhiều pods retry cùng lúc.
- **maxAttempts** và **deadline** tránh giữ job vô hạn.
- Dùng **lastErrorCode** hoặc thông tin lỗi đã loại secrets để phân tích; không lưu stack trace chứa PII/token bừa bãi.
- Có cơ chế replay bằng thao tác được audit sau khi sửa lỗi, với cùng jobId/idempotency key nếu đó là cùng một business effect.

**Không retry một payment/email trừ khi đã đánh giá khả năng duplicate side effect.** Retry là công cụ truyền lại công việc, không phải chứng nhận trạng thái của hệ thống ngoài.

## 19. Checkpoint để tiếp tục chứ không nhất thiết chạy lại từ đầu

Với job dài, chạy lại toàn bộ có thể tốn thời gian hoặc không an toàn.

Ví dụ batch xử lý 1.000.000 records:

~~~text
job J:
    checkpoint = record #250,000
    last_committed_chunk = chunk 250
    status = RUNNING

Process chết.
Process mới đọc checkpoint và bắt đầu từ chunk 251.
~~~

Để checkpoint đáng tin:

1. Mỗi chunk commit với **business side effects và checkpoint tương ứng** trong cùng transaction khi có thể.
2. Nếu external side effect không thể atomic cùng checkpoint, dùng idempotent operation per record/chunk.
3. Mỗi chunk có **stable chunkId**, không dùng ngẫu nhiên mới mỗi retry.
4. Job input phải ổn định hoặc có snapshot/version, tránh dữ liệu nguồn đổi khiến position index 250.000 trỏ record khác.
5. Checkpoint phải phù hợp với loại reader/writer và trạng thái business; không chỉ là số dòng đã lặp trong RAM.
6. Có cơ chế rollback/restart khi schema/model thay đổi giữa các versions của ứng dụng.

**Spring Batch** là một lựa chọn khi workflow là batch nhiều steps/chunks, cần metadata, checkpoint và restart semantics. Một @Async method tự viết không có những cơ chế này chỉ vì nó nằm trong Spring application.

## 20. Spring Batch: JobRepository và trạng thái STARTED sau crash

Spring Batch có **JobRepository** để lưu metadata của **JobInstance, JobExecution, StepExecution**, và có cơ chế restart cho những jobs được thiết kế có thể restart.

Điều quan trọng khi **JVM chết bất ngờ**: một JobExecution có thể **vẫn nằm ở trạng thái STARTED trong repository**, dù process thực tế đã chết. Repository không tự có bằng chứng tuyệt đối rằng worker không còn sống.

Theo tài liệu Spring Batch, recovery cần **xác minh execution cũ**, sửa/trị trạng thái theo quy trình được hỗ trợ, rồi mới restart khi dữ liệu checkpoint và business state còn hợp lệ. Ở các phiên bản hiện đại, tham khảo **JobOperator** và các API recovery phù hợp với version triển khai; **không tự SQL UPDATE hàng loạt từ STARTED sang FAILED** mà bỏ qua quy tắc metadata/transaction.

### Phân biệt restart và job mới

- **JobInstance** được định danh từ job name và identifying JobParameters.
- **JobExecution** đại diện cho một attempt thực thi JobInstance đó.
- **Restart** thường dựa trên existing JobInstance và trạng thái/các checkpoints cho phép.
- **Run mới** với identifying parameters khác có thể tạo **JobInstance mới**, không phải tiếp tục instance cũ.
- Các statuses như **FAILED**, **STOPPED**, **COMPLETED**, **ABANDONED** có ý nghĩa khác nhau; không thể tùy tiện coi tất cả là retryable.

**Không đưa mỗi lần retry một timestamp identifying parameter mới** nếu yêu cầu là restart đúng JobInstance cũ. Cách đó có thể làm Spring Batch hiểu rằng bạn muốn một instance mới và chạy lại toàn bộ công việc.

Spring Batch hỗ trợ restart metadata nhưng **không tự khiến external side effects idempotent**. Một ItemWriter gọi payment gateway vẫn cần business idempotency và checkpoint contract.

### Chọn repository có persistence

Các lựa chọn repository in-memory hoặc resourceless chỉ phù hợp trường hợp không cần restartability. Nếu yêu cầu **khôi phục sau process death**, chọn một persistent JobRepository và cấu hình transaction/data source phù hợp; test behavior bằng việc kill tiến trình thật.

## 21. Quartz JDBC JobStore và clustered recovery

Nếu công việc chủ yếu là **lập lịch có trạng thái**, Quartz có **JDBC JobStore** và tùy chọn clustering, khác với **RAMJobStore**.

Với clustered Quartz/JDBC:
- Nhiều nodes dùng cùng persistent job store.
- Scheduler instances cạnh tranh quyền nhận các trigger đến hạn thông qua coordination ở database.
- Khi một node mất kết nối/không check in, nodes khác có thể phát hiện những executions bị bỏ dở.
- Những jobs được cấu hình **requestsRecovery** có thể được Quartz chạy lại sau failover theo hợp đồng.
- **Misfire instructions** quyết định cách xử lý những trigger quá hạn trong điều kiện áp dụng.
- Không phải mọi job cũ đều mặc nhiên được replay; cần đọc chính sách recovery cho JobDetail/Trigger.

**Đừng nhầm** hai câu hỏi:
1. “Lịch cron nào bị bỏ lỡ vì application không chạy?”
2. “Một job đã được kích hoạt, đang thực hiện side effect, rồi process chết?”

Quartz có cơ chế cho cả trigger/misfire và execution recovery khi được cấu hình, nhưng **business effect vẫn phải idempotent**. Nếu job đã gửi yêu cầu tới một external service rồi crash, Quartz không thể tự biết bên ngoài đã xử lý hay chưa.

**Không xem RAMJobStore là durable** và không dùng mỗi **@Scheduled** như một thay thế tự động của clustered JDBC JobStore.

## 22. Transactional outbox cho công việc liên quan nhiều hệ thống

Một vấn đề phổ biến: cập nhật DB và gửi message tới broker trong cùng một request:

~~~text
UPDATE order SET state='PAID'
publish("order-paid")
~~~

Nếu process chết sau khi DB commit nhưng trước khi publish, message có thể mất. Nếu publish trước commit rồi DB rollback, downstream có thể xử lý một event không tương ứng trạng thái committed.

**Transactional outbox**:

~~~text
DB transaction:
    UPDATE orders...
    INSERT INTO outbox(event_id, event_type, payload, status=PENDING)
COMMIT

Separate relay:
    SELECT pending outbox events
    -> publish message
    -> record acknowledgement / mark published
~~~

Vì order update và outbox insert cùng DB transaction, process chết không để lại trạng thái “DB đã commit nhưng chưa từng ghi event cần gửi”. Tuy nhiên, relay có thể publish rồi crash trước khi đánh dấu PUBLISHED, dẫn đến **message gửi lại**. Do đó consumer cần **inbox/deduplication** hoặc idempotent handler.

**Không nhầm outbox với distributed two-phase commit.** Nó đổi một giao dịch phân tán khó kiểm soát thành **durable intent + at-least-once delivery**, với idempotency để bảo vệ tác động cuối cùng.

Một API có thể dùng outbox/job table để bảo đảm công việc đã được ghi trước khi trả 202, thay vì gửi Fire-and-forget Runnable vào RAM.

## 23. Phát hiện tiến trình chết và nguy cơ split-brain

Không có detector phân tán nào luôn phân biệt hoàn hảo:
- Process đã chết thật.
- Process còn sống nhưng GC pause rất lâu.
- Node mất mạng tạm thời.
- Database bị tách mạng.
- CPU quá tải không kịp heartbeat.

Nếu B reclaim job vì lease A hết hạn, A có thể vẫn chạy. Đây là **tình huống split ownership** cần fencing và idempotency.

### Vì sao heartbeat đơn thuần chưa đủ?

~~~text
A lease token=7, gửi payment request chậm 45 giây
A bị mất kết nối DB nên heartbeat thất bại
Lease hết hạn sau 30 giây
B nhận token=8 và gửi lại payment
Cả A và B có thể thành công ở gateway nếu gateway không dedupe
~~~

Dù job table chỉ cho B ack SUCCEEDED, **payment gateway vẫn có thể nhận hai yêu cầu**. Giải pháp phải ở ranh giới thực hiện payment: payment idempotency key ổn định, gateway API hỗ trợ key, hoặc workflow reconciliation.

**Không hứa “lease đảm bảo exactly-once”.** Lease chỉ hỗ trợ xác định **ai đang có quyền theo store tại thời điểm kiểm tra**; các hành động bên ngoài phải được thiết kế an toàn riêng.

## 24. Cấu hình thời gian: chọn lease TTL và deadline

Có ba loại thời gian khác nhau:

| Loại | Ví dụ | Mục đích |
| --- | --- | --- |
| **lease duration** | 60 giây | Khi owner không heartbeat, job đủ điều kiện được reclaim |
| **heartbeat interval** | 10–20 giây | Cập nhật lease khi job còn đang xử lý |
| **job deadline** | 15 phút | Sau thời điểm đó, job không còn hợp lệ hoặc phải escalation |
| **RPC timeout** | 5 giây | Một downstream call không được chặn vô hạn |
| **retry backoff** | 2s, 4s, 8s + jitter | Không gây retry storm |
| **orchestrator grace** | 30 giây | Thời gian process được phép shutdown bình thường |
| **stale scan interval** | 5 giây | Bao lâu poller phát hiện expired leases |
| **reconciliation timeout** | Theo payment/email provider | Khi nào outcome không rõ cần kiểm tra ngoài |

**Các con số chỉ là ví dụ, không phải cấu hình mặc định được khuyến nghị.** Với job dài, heartbeat phải đều, lease đủ dài để chịu transient faults, và worker phải phản ứng khi mất quyền.

Nếu bạn đặt lease TTL = 5 giây nhưng một DB query hợp lệ có thể chặn 20 giây và không heartbeat, sẽ có **false recovery**. Nếu đặt TTL = 24 giờ cho job 5 giây, crash thật khiến recovery quá chậm.

### Dùng deadline trong command

Một immutable job command có thể gồm:

~~~java
public record ExportCommand(
        String jobId,
        String tenantId,
        java.time.Instant deadlineAt,
        String idempotencyKey,
        long configVersion) {
}
~~~

Đây là **một record Java 17 minh họa**, chưa phải application hoàn chỉnh. Worker kiểm tra deadline trước khi gọi side effect; nếu job hết thời hạn và policy không cho chạy muộn, chuyển trạng thái phù hợp thay vì âm thầm xử lý dữ liệu lỗi thời.

## 25. Một sơ đồ phục hồi sau restart

~~~text
STARTUP PROCESS MỚI
      |
      v
Read persisted desired job state
      |
      +-- SUCCEEDED / DEAD
      |       |
      |       +--> Không tự requeue
      |
      +-- PENDING / RETRY_WAIT đến hạn
      |       |
      |       +--> Atomic claim (nếu capacity cho phép)
      |
      +-- RUNNING
              |
              +-- lease còn hạn
              |       |
              |       +--> Tạm chờ / theo dõi owner
              |
              +-- lease đã hết
                      |
                      +--> Atomic reclaim
                             |
                             +--> generation/token mới
                             +--> kiểm tra checkpoint + deadline
                             +--> đối soát external effect nếu cần
                             +--> thực thi idempotent
                             +--> conditional ack / retry / DEAD
~~~

**Không đặt recovery chỉ trong @PostConstruct rồi quên nó.** Trong hệ thống đang chạy, một worker có thể chết bất kỳ lúc nào. Nên có một **poller/sweeper liên tục hoặc queue consumer có visibility timeout**. Startup scan có thể hữu ích để rút ngắn thời gian khôi phục, nhưng nó không thay thế monitoring và periodic reclamation.

Nếu một process mới đọc được một job RUNNING có lease chưa hết, **không nên lập tức reset PENDING** chỉ vì chính process vừa khởi động. Có thể owner đó thuộc pod khác đang làm việc.

## 26. Graceful shutdown trong hệ thống có durable jobs

Mặc dù crash recovery là bắt buộc, vẫn nên giảm công việc bị bỏ dở khi dừng bình thường:

1. **Ngừng nhận API submissions mới** hoặc cho phép API tiếp tục ghi durable jobs nhưng ngừng dispatch.
2. **Ngừng tạo scheduled triggers mới** hoặc tạm dừng scheduler.
3. **Ngừng claim jobs mới** từ store.
4. **Cho running attempts hoàn tất** trong thời hạn, tiếp tục heartbeat khi còn owner hợp lệ.
5. Nếu không kịp, **đánh dấu release/cancel/retry theo token hiện hành** hoặc để lease hết hạn để process khác reclaim.
6. **Đảm bảo các dependencies còn hoạt động** đến khi workers hoàn tất hoặc không còn được phép side effect.
7. **Đóng executor/scheduler** và ApplicationContext theo lifecycle.
8. Ghi rõ jobs còn PENDING/RUNNING, stale lease count và số attempts chưa có terminal outcome.

Đối với ThreadPoolTaskExecutor:
- **waitForTasksToCompleteOnShutdown(true)** là lựa chọn liên quan xử lý running/queued tasks, không tự định nghĩa durable retry.
- **awaitTerminationSeconds(...)** giới hạn thời gian chờ của shutdown path; không ép một HTTP call cứng đầu phải dừng.
- **strictEarlyShutdown/acceptTasksAfterContextClose** ảnh hưởng submissions muộn tùy phiên bản Spring.
- **@Async** producer có thể bị TaskRejectedException khi executor bắt đầu đóng.

**Không chỉ tắt scheduler trước nhưng quên worker pool**; công việc đã delegate có lifecycle riêng. Ngược lại, dừng worker trước trong khi scheduler còn phát task có thể tạo rejection storm.

## 27. Cancellation không đồng nghĩa rollback

Nếu một người dùng hủy job khi worker đang chạy:
- Ghi **CANCEL_REQUESTED** ở durable state.
- Worker đọc cancellation token/flag tại checkpoints thích hợp.
- Ngừng những bước chưa bắt đầu nếu policy cho phép.
- Xử lý transaction đang mở theo hợp đồng của DB.
- Không kỳ vọng **Future.cancel(true)** hoặc thread interruption tự hoàn tác external side effects.
- Nếu external operation không thể hủy, ghi **UNKNOWN_OUTCOME/NEEDS_RECONCILIATION** thay vì khẳng định CANCELLED ngay.

Một attempt bị process kill có thể không kịp ghi trạng thái CANCELLED; **lease expiration + reconciliation** vẫn là cần thiết.

Trong Spring/Java:
- **Thread.interrupt** thường là yêu cầu hợp tác.
- **FutureTask.cancel(true)** có thể đề nghị interrupt worker.
- **CompletableFuture.cancel(true)** không tự interrupt computation bên dưới theo contract JDK.
- Các cơ chế này không thay thế persisted job state.

## 28. Quan sát và cảnh báo: không chỉ nhìn thread pool

Một dashboard để trả lời “job nào bị bỏ dở sau deploy?” nên theo dõi ít nhất:

**Tiếp nhận**
- New job count, duplicate submission suppressed count.
- PENDING age / oldest pending age.
- API 202 response count và durable insert failures.

**Thực thi**
- Claimed attempts theo worker/pod, active leases, heartbeat age.
- Worker queue wait, executor active count, rejection count.
- Processing duration, downstream timeouts, job deadline miss.
- Số attempts/job, số retryable errors, số DEAD/poison jobs.

**Phục hồi**
- **Expired RUNNING leases** theo tuổi và tenant.
- Reclaim count, **stale-owner ack rejected** count.
- Recovery latency = lúc owner bị xem mất → lúc attempt mới bắt đầu.
- Số jobs bị đối soát vì UNKNOWN_OUTCOME.
- Jobs bị replay sau restart và kết quả cuối cùng.
- Fencing token conflicts / split ownership observations.

**Hiệu ứng nghiệp vụ**
- Số logical jobs SUCCEEDED.
- Số unique committed effects.
- Duplicate effect attempts bị chặn nhờ idempotency.
- Side effects pending reconciliation ở hệ thống ngoài.

Một metric **executor.active=0** trong process mới không có nghĩa mọi job đã hoàn tất. Nó chỉ nói hiện tại process mới không có worker đang bận. Cần nhìn **durable job store** để biết backlog và abandoned attempts.

### Log tối thiểu có ích

~~~text
jobId
logicalBusinessKey
attemptId / leaseToken
ownerId / podId / processStartId
stateBefore -> stateAfter
leaseUntil
checkpoint / progress
idempotencyKey (bản an toàn để log)
errorCode + retryable?
correlationId / traceId
elapsedTime
~~~

Không log payload chứa PII/credentials, tokens hoặc full exceptions có secrets. Có thể hash/redact idempotencyKey nếu nó chứa thông tin nhạy cảm.

## 29. Failure injection: những test bắt buộc

Nếu chưa test crash ở nhiều ranh giới, thiết kế recovery mới là giả định.

| Kiểm thử | Cách thực hiện | Kỳ vọng |
| --- | --- | --- |
| Chết trước khi lưu PENDING | Bỏ commit/kill trước commit | Không có job accepted |
| Chết sau commit trước dispatch | Kill tiến trình ngay sau commit | Job PENDING còn trong DB, process khác claim được |
| Chết sau claim trước body | Claim RUNNING rồi kill worker | Lease hết hạn, reclaim được |
| Chết giữa business transaction | Kill trước DB commit | Rollback/checkpoint chính xác, retry an toàn |
| Chết sau effect trước ack | Commit effect rồi kill trước update SUCCEEDED | Retry không nhân đôi effect |
| Mất heartbeat nhưng worker còn sống | Tạm chặn mạng/GC pause mô phỏng | Attempt cũ không ack hoặc side effect được fenced |
| Hai pods claim cùng lúc | Barrier/latch để tranh chấp DB | Chỉ một claim hợp lệ mỗi generation |
| Executor từ chối task | Làm pool/queue bão hòa | Claim được release có điều kiện hoặc lease hết rồi recover |
| Retry liên tục lỗi permanent | Job luôn ném lỗi cố định | Về DEAD/DLQ, không lặp vô hạn |
| Restart khi batch có checkpoint | Kill giữa chunk | Không bỏ hoặc nhân đôi những records đã commit |
| Dừng êm đang có jobs | Gửi SIGTERM, đóng context | Không nhận job mới, drain trong grace hoặc recover |
| Kill cưỡng bức | SIGKILL/pod delete force/kill -9 | Không phụ thuộc finally; durable jobs vẫn recover |
| Broker duplicate delivery | Giao cùng message hai lần | Idempotency giữ một business effect |
| Lease timeout giả | Giảm TTL và tạo job chậm | Phát hiện duplicate attempts, fencing bảo vệ side effect |

**Đừng chỉ dùng unit test trên HashMap** để chứng minh recovery trên nhiều processes. Unit test kiểm tra state machine; integration test cần **database thật**, transactions, constraints và ít nhất hai workers/processes khi kiểm tra distributed ownership.

### Test race claim

Dùng **CountDownLatch** hoặc barrier để hai workers cùng cố claim một job. Sau khi cả hai complete transaction, assert tổng số successful claims **bằng một** trong cùng generation. Khi lease hết hạn và worker khác reclaim, assert token tăng và update bằng token cũ bị reject.

### Test crash thật

Một integration environment có thể khởi chạy worker như **process con**, chờ một marker đã persist rồi cưỡng bức terminate process. Sau đó khởi động worker mới dùng cùng DB và assert:

1. Job cũ còn durable.
2. Lease hết hạn/recovery diễn ra theo policy.
3. Chỉ một unique business effect tồn tại.
4. Owner cũ không thể ack sau reclaim.
5. Trạng thái cuối và attempts/checkpoint nhất quán.

Tránh dùng chỉ **Thread.interrupt** để mô phỏng process crash: nó vẫn cho Java finally chạy và các thread khác cùng JVM tiếp tục, nên không có những mất mát giống SIGKILL.

## 30. Khi nào dùng framework nào?

| Bài toán | Công cụ/hướng tiếp cận |
| --- | --- |
| Task ngắn, có thể mất nếu app restart | Spring TaskExecutor/@Async có thể đủ |
| API cần 202 + không mất job | Persist job trước, poller/worker đọc durable job store |
| Đơn hàng/message/event xuyên dịch vụ | Broker durable + ack/retry + idempotent consumer/outbox |
| Batch nhiều steps/chunks, cần checkpoint và restart | **Spring Batch + persistent JobRepository** |
| Lịch persistent, clustered triggers và recovery | **Quartz JDBC JobStore** hoặc scheduler chuyên dụng |
| Workflow dài nhiều service, compensation, timeout | Workflow engine/saga và durable state |
| Chỉ một job global ở nhiều pods | Leader/lease/lock có fencing và idempotent effect |
| Tác vụ I/O bất đồng bộ bên trong một process | TaskExecutor, CompletableFuture, reactive Publisher (không tự bền vững) |

Không nhất thiết phải tự xây database work queue nếu tổ chức đã có broker/workflow engine phù hợp. Ngược lại, không chọn một hệ thống lớn chỉ để chạy một best-effort cache refresh có thể bỏ qua. **Mức đảm bảo phải xuất phát từ hậu quả của việc mất hoặc lặp side effect.**

## 31. Những nhầm lẫn phổ biến

**“@Async đã trả CompletableFuture nên restart sẽ tiếp tục công việc.”** Sai. Future trong RAM mất khi JVM chết.

**“ThreadPoolTaskExecutor có queueCapacity=1000 nghĩa là 1000 tasks được lưu bền vững.”** Sai. Queue của pool là in-memory queue theo implementation phổ biến.

**“@Scheduled sẽ chạy bù mọi lần cron bị lỡ khi app khởi động lại.”** Không được bảo đảm. @Scheduled không có persistent misfire/replay log mặc định.

**“Graceful shutdown bảo đảm finally luôn chạy.”** Sai nếu process bị kill cưỡng bức hoặc node chết.

**“Lease hết hạn chứng minh worker cũ đã chết.”** Sai. Nó có thể còn sống nhưng bị pause/network partition.

**“Tăng lease token đủ ngăn mọi external side effect trùng.”** Sai. Hệ thống nhận side effect phải kiểm token hoặc idempotency.

**“Có synchronized/AtomicBoolean là đủ trong nhiều pods.”** Sai. Chúng chỉ đồng bộ trong một JVM/process.

**“Dùng retry vô hạn là đảm bảo không mất job.”** Sai. Nó có thể tạo duplicate effects, retry storm, poison job và không giúp khi job chưa durable.

**“DB row RUNNING nghĩa là job vẫn đang chạy.”** Sai. Nó có thể là stale state từ process đã chết.

**“Reset mọi RUNNING về PENDING lúc startup là an toàn.”** Sai với nhiều pods hoặc workers còn sống.

**“Timeout nghĩa là remote request chắc chắn chưa thực hiện.”** Sai. Outcome có thể UNKNOWN và cần reconciliation.

**“202 Accepted sau executor.execute nghĩa là hệ thống đã nhận bền vững.”** Sai nếu chưa commit job state.

**“Gọi shutdown() là mọi task đã xong.”** Sai. isShutdown và isTerminated là hai trạng thái khác nhau.

**“Spring Batch tự biết chính xác một job STARTED đã chết hay chưa.”** Không mặc nhiên. Cần recovery procedures phù hợp và xác minh state.

**“Quartz clustered phục hồi nghĩa là email/payment sẽ chỉ gửi một lần.”** Sai. Recovery/re-execution có thể lặp business effect.

**“Outbox khiến broker không bao giờ giao message trùng.”** Sai. Outbox relay có thể publish rồi crash trước ack; consumer vẫn cần dedupe.

**“Kiểm thử bằng Thread.sleep và interrupt là đủ mô phỏng mất tiến trình.”** Không. Cần crash tests và persisted state.

## 32. Câu hỏi ôn tập có đáp án

**Câu 1.** Một HTTP controller chỉ gọi executor.submit rồi trả 202. JVM chết trước khi worker bắt đầu. Công việc có tự phục hồi không?

**Đáp án:** Không. Task/Future trong RAM không tự phục hồi. Nếu cần bền vững, ghi durable job record trước khi xác nhận accepted.

**Câu 2.** Job đã được mark RUNNING, process chết, trạng thái trong DB có tự đổi sang PENDING không?

**Đáp án:** Không mặc nhiên. Cần lease/heartbeat timeout và cơ chế reclaim/requeue được thiết kế.

**Câu 3.** Tại sao lease hết hạn không chứng minh worker cũ đã chết?

**Đáp án:** Worker có thể chỉ bị GC pause hoặc network partition. Cần fencing/idempotency để phòng attempt cũ chạy lại.

**Câu 4.** Hai pods cùng tìm một PENDING job bằng SELECT rồi UPDATE rời rạc. Có thể claim trùng không?

**Đáp án:** Có. Claim phải atomic bằng transaction/conditional update/row-level locking.

**Câu 5.** Worker A token=7, sau lease expiry worker B token=8. Ack của A được chấp nhận không?

**Đáp án:** Không nếu SQL ack kiểm tra owner, generation/token, state và hạn lease. Nếu không có fencing, stale ack có thể ghi đè.

**Câu 6.** Worker gửi email xong rồi crash trước ghi SUCCEEDED. Retry có gây email trùng không?

**Đáp án:** Có thể. Cần idempotency của recipient/provider, dedupe, đối soát hoặc chấp nhận unknown outcome theo policy.

**Câu 7.** Một job có unique business key trong DB và effect + ack cùng một transaction. Điều này có ích gì?

**Đáp án:** Crash trước commit rollback cả hai; crash sau commit có durable state; uniqueness hạn chế duplicate DB effects. Nó không tự bao gồm external HTTP side effects.

**Câu 8.** Dùng SKIP LOCKED để claim jobs từ queue-like table có cần transaction không?

**Đáp án:** Có. SELECT lock và UPDATE claim cần thuộc cùng transaction/statement an toàn. Không giữ transaction quá lâu trong business I/O.

**Câu 9.** Heartbeat chỉ UPDATE theo jobId, không kiểm token. Rủi ro?

**Đáp án:** Worker cũ có thể gia hạn lease của attempt mới, phá ownership.

**Câu 10.** Nếu lease ban đầu được claim trước khi task nằm chờ 90 giây trong executor queue, nhưng TTL chỉ 30 giây, chuyện gì xảy ra?

**Đáp án:** Job có thể bị reclaim trước khi worker bắt đầu. Cần thiết kế claim/start boundary, queue wait, TTL và token validation.

**Câu 11.** Vì sao không nên restart Spring Batch JobInstance với identifying parameter timestamp mới nếu mục tiêu là tiếp tục checkpoint cũ?

**Đáp án:** Parameter mới có thể tạo JobInstance mới thay vì restart execution cũ, dẫn tới chạy lại không theo checkpoint dự định.

**Câu 12.** Quartz JDBC JobStore có đảm bảo side effect exactly-once khi job yêu cầu recovery không?

**Đáp án:** Không. Job có thể được re-execute sau crash; business handler vẫn cần idempotency.

**Câu 13.** Nếu retry luôn ném lỗi validation vĩnh viễn, nên làm gì?

**Đáp án:** Chuyển DEAD/DLQ/NEEDS_REVIEW theo policy, alert và cho phép replay được audit sau khi sửa lỗi; không retry vô hạn.

**Câu 14.** Nếu application SIGKILL, @PreDestroy có chắc chạy không?

**Đáp án:** Không. Persistence/recovery không được phụ thuộc vào callback shutdown.

**Câu 15.** Worker đã commit payment nhưng ACK bị mất. Nên gửi payment mới ngay không?

**Đáp án:** Không gửi mù. Dùng stable idempotency key và query/reconcile outcome theo contract provider.

**Câu 16.** Một scheduled cleanup chạy trên ba pods với cùng ngày dữ liệu. Làm sao giảm double processing?

**Đáp án:** Stable job key + unique insertion, atomic claim, lease/fencing và idempotent effect. Nếu chỉ chạy một leader, dùng coordinator/lock phù hợp.

**Câu 17.** Java FileRestartDemo có đủ cho multi-pod production không?

**Đáp án:** Không. File local không có transaction/fsync/locking/distributed recovery; nó chỉ minh họa state còn lại qua nhiều JVM chạy lần lượt.

**Câu 18.** Một worker còn sống nhưng mất quyền lease. Nó nên làm gì khi phát hiện renew count = 0?

**Đáp án:** Dừng hoặc không khởi động side effects tiếp theo theo policy, không ack bằng token cũ, ghi stale-attempt metric và đối soát những effects đã gửi.

## 33. Checklist triển khai

- [ ] Xác định việc nào **có thể bỏ** và việc nào **phải khôi phục**.
- [ ] Không coi ThreadPoolTaskExecutor/@Async/@Scheduled queue trong RAM là durable.
- [ ] Chỉ trả **202 Accepted** cho job quan trọng sau khi durable job intent đã commit.
- [ ] Có **jobId**, **idempotencyKey** ổn định và tránh duplicate submit.
- [ ] Có job store hoặc broker bền vững, schema version và transaction phù hợp.
- [ ] Có trạng thái PENDING/RUNNING/RETRY_WAIT/SUCCEEDED/DEAD và policy chuyển.
- [ ] Claim phải atomic giữa các pods, không SELECT rồi UPDATE rời rạc.
- [ ] Lưu ownerId, leaseUntil, leaseToken/generation và attempts.
- [ ] Có heartbeat/renew conditional khi job dài.
- [ ] Reclaim RUNNING chỉ sau điều kiện lease/owner hợp lệ, không reset tất cả khi startup.
- [ ] Reject stale ACK, stale heartbeat và stale data mutation qua token/fencing.
- [ ] Bảo vệ business effect bằng uniqueness/idempotency/transaction/outbox.
- [ ] Xem timeout external là **UNKNOWN_OUTCOME** khi không biết đã xử lý hay chưa.
- [ ] Có nextAttemptAt, backoff+jitter, maxAttempts, deadline và dead-letter/quarantine.
- [ ] Có checkpoint cho batch dài, commit với business effect theo contract.
- [ ] Giới hạn worker queue và tính cả queue wait trong lease/deadline.
- [ ] Khi executor reject sau claim, release có token hoặc để lease hết hạn.
- [ ] Ngừng producers/scheduler trước worker shutdown khi có thể.
- [ ] Không dựa vào @PreDestroy/finally để phục hồi sau SIGKILL.
- [ ] Có shutdown grace và downstream timeouts, không chờ vô hạn.
- [ ] Nếu dùng Spring Batch, cấu hình persistent JobRepository và restart semantics.
- [ ] Nếu dùng Quartz, hiểu JDBC JobStore, cluster/recovery và misfire policy.
- [ ] Với multiple pods, dùng shared durable store và phối hợp claim.
- [ ] Có metric stale leases, reclaim count, stale token rejection và pending job age.
- [ ] Test kill sau claim, sau effect trước ack, sau ack trước response.
- [ ] Test network partition/GC pause: worker cũ không được ghi kết quả sai.
- [ ] Đối soát external effects và có audit trail cho manual recovery.
- [ ] Xác định chính xác nơi bảo đảm **at-least-once** và nơi có **idempotent business effect**.

## Kết luận

**Mất tiến trình không chỉ làm task đang chạy bị gián đoạn; nó xóa mọi state chỉ nằm trong JVM, gồm Future, executor queue, threads và ThreadLocal.** Vì vậy, nếu công việc quan trọng phải tiếp tục sau restart, ứng dụng Spring cần tách **việc lập lịch/chuyển luồng** khỏi **durable job ownership và kết quả nghiệp vụ**.

Mô hình có thể tin cậy thường gồm: **ghi job trước khi xác nhận → atomic claim với lease và generation → heartbeat/timeout → thực thi có idempotency/checkpoint → conditional acknowledgement → retry hoặc recovery có kiểm soát**. Graceful shutdown giảm sự cố khi deploy, nhưng **SIGKILL, mất node và network partition** vẫn đòi hỏi cơ chế phục hồi. Khi side effect liên quan hệ thống ngoài, luôn tính đến trạng thái **đã thành công nhưng chưa ack**, thay vì hứa exactly-once một cách tuyệt đối.

Spring TaskExecutor, @Async và TaskScheduler vẫn rất hữu ích để thực thi từng attempt; **Spring Batch, Quartz JDBC JobStore, broker bền vững hoặc workflow engine** là những lựa chọn khi cần metadata, checkpoint, distributed coordination và restartability ở cấp cao hơn. Chọn công cụ dựa trên hậu quả của việc **mất hoặc lặp** công việc, không chỉ dựa trên việc một method có thể chạy bất đồng bộ.

**Tài liệu tham khảo chính thức:**

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): TaskExecutor, ThreadPoolTaskExecutor, TaskScheduler, @Async, @Scheduled.
- [Spring Framework — ThreadPoolTaskExecutor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html): queue, rejection, shutdown/lifecycle.
- [Spring Batch — Advanced Metadata Usage](https://docs.spring.io/spring-batch/reference/job/advanced-meta-data.html): restart/recover JobExecution sau JVM dừng đột ngột.
- [Spring Batch — JobRepository](https://docs.spring.io/spring-batch/reference/job/configuring-repository.html): persistent job metadata và các lựa chọn repository.
- [Spring Batch — Configuring a Job](https://docs.spring.io/spring-batch/reference/job/configuring.html): JobInstance, JobExecution và restartability.
- [Quartz — Configuration Reference: JDBC JobStore Clustering](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/configuration/ConfigJDBCJobStoreClustering.html): clustered recovery/requestsRecovery và scheduling persistence.
- [PostgreSQL — SELECT locking clauses](https://www.postgresql.org/docs/current/sql-select.html): FOR UPDATE, NOWAIT, SKIP LOCKED trong queue-like tables.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): shutdown và semantics của in-process executors.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="distributed-limits-007">Phân biệt điều phối tác vụ cục bộ với hàng đợi, workflow hoặc lịch phân tán</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt điều phối tác vụ cục bộ với hàng đợi, workflow hoặc lịch phân tán

Trong một ứng dụng Spring Framework, `@Async`, `TaskExecutor`, `CompletableFuture`, `@Scheduled` và `TaskScheduler` giải quyết rất tốt **việc chạy công việc ngoài luồng gọi hoặc vào một thời điểm trong một ứng dụng đang sống**. Tuy nhiên, khi hệ thống có nhiều JVM/pod/replica, cần chịu được crash và khởi động lại, phải xử lý hàng triệu thông điệp hoặc cần duy trì một quy trình kéo dài qua nhiều ngày, câu hỏi không còn là “dùng bao nhiêu thread” mà là **“Ai giữ trạng thái công việc, ai có quyền thực thi, ai đảm bảo không mất việc, và ai chịu trách nhiệm khôi phục sau sự cố?”**

Một local thread pool không phải message broker. Một phương thức `@Scheduled` trong ba pods không phải một cron job toàn cụm. Một hàng đợi thông điệp không tự động là workflow orchestration engine. Một clustered scheduler giúp phối hợp **lịch kích hoạt**, nhưng chưa bảo đảm side effect nghiệp vụ xảy ra đúng một lần. Các giới hạn này cần được nhận diện trước khi chọn công nghệ.

Bài học thuộc nhóm **Giới hạn phân tán** của module **Spring Framework - concurrency**. Mục tiêu là phân biệt bốn lớp kiến trúc: **điều phối cục bộ**, **hàng đợi công việc**, **workflow bền vững** và **lịch phân tán**; từ đó xây hợp đồng rõ về độ bền, trùng lặp, retry, thứ tự, transaction, timeout, quan sát và khôi phục.

**Thông điệp chính:** chọn công cụ theo *bảo đảm nghiệp vụ* chứ không theo từ khóa “async”, “distributed”, “exactly once” hay “cluster”. Hãy tách ba câu hỏi: **khi nào phát sinh công việc?**, **công việc được lưu và giao cho ai?**, **trạng thái nhiều bước và kết quả cuối được phục hồi thế nào?** Những câu hỏi này có thể đòi hỏi **kết hợp** scheduler + queue + workflow thay vì tìm một API duy nhất.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích ranh giới một JVM/ApplicationContext của `TaskExecutor`, `ThreadPoolTaskExecutor`, `TaskScheduler`, `@Async` và `@Scheduled`.
- Phân biệt chạy **đồng thời** với **phân tán**, chạy **ở nền** với **lưu bền**, và chạy **một lần mỗi JVM** với **một lần cho toàn business window**.
- Biết vì sao pool queue trong RAM và `ScheduledFuture<?>` không phải job store.
- Mô tả producer, broker, consumer, ack/nack, redelivery, visibility/lease, dead-letter và consumer group.
- Hiểu các mức at-most-once, at-least-once và giới hạn của lời hứa exactly-once khi có external side effects.
- Nhận diện **transactional outbox**, **inbox/idempotent consumer** và các khoảng trống crash giữa database, broker, API ngoài.
- Phân biệt workflow orchestration có **durable execution history**, retry/timeouts/signals/timers với một chuỗi `CompletableFuture` trong RAM.
- Hiểu lịch phân tán qua Quartz JDBC clustered JobStore, Kubernetes CronJob, leader election/lease hoặc database claim.
- Biết ranh giới của Spring Batch `JobRepository`: hỗ trợ metadata/restart phù hợp, nhưng không tự làm `@Scheduled` thành global exactly-once.
- Thiết kế idempotency key, lease, fencing token và đối chiếu trạng thái khi process chết/đổi owner.
- Áp dụng bảng quyết định theo nhu cầu: việc ngắn trong cùng request, async xử lý độc lập, hàng đợi, long-running workflow hay job theo lịch toàn cụm.
- Tự kiểm thử crash/restart, timeout, duplicate delivery, hai consumers, mất ack, node failover và thực thi trùng.

## 2. Bốn công cụ giải quyết bốn vấn đề khác nhau

| Phương án | Câu hỏi chính được giải quyết | Trạng thái điển hình | Phạm vi điều phối |
| --- | --- | --- | --- |
| **Local executor / @Async** | Chạy task ở thread nào, có chờ không, pool lớn bao nhiêu? | RAM trong process | Một JVM/ApplicationContext |
| **Message queue / event log** | Làm sao lưu và chuyển một đơn vị việc cho consumer, phân phối tải, giao lại khi lỗi? | Broker/external store nếu cấu hình độ bền đúng | Nhiều process, nhiều máy |
| **Durable workflow engine** | Quy trình nhiều bước đang ở đâu, đã làm gì, đang chờ gì, khôi phục sau crash ra sao? | Workflow history/state bên ngoài worker | Nhiều worker và thời gian dài |
| **Distributed scheduler** | Ai phát một lần kích hoạt theo lịch giữa nhiều node? Xử lý lịch bị lỡ thế nào? | Có thể có persisted trigger/lease tùy giải pháp | Nhiều instance/nodes |

Các phương án **không loại trừ nhau**. Ví dụ mỗi ngày lúc 02:00, một clustered scheduler có thể **phát một message**; broker phân phối message cho worker; worker **khởi động workflow** nhiều bước; trong từng worker, `TaskExecutor` vẫn giúp xử lý các công việc cục bộ.

~~~text
Distributed / durable schedule
            |
            | fire một business window
            v
     Durable queue / broker
            |
            | claim / consume / ack
            v
        Worker process
            |
            +-- local TaskExecutor for bounded concurrency
            |
            +-- workflow engine for multi-step recovery
            |
            v
    DB / external side effects / outcome
~~~

**Quan trọng:** sơ đồ là một kiến trúc mẫu, không phải tuyên bố mọi ứng dụng phải dùng cả bốn tầng. Mỗi tầng bổ sung complexity, hạ tầng và điểm failure. Nếu việc chỉ là làm sạch cache trong một instance, local scheduler có thể là lựa chọn đúng nhất.

## 3. Ranh giới của điều phối cục bộ trong Spring

### 3.1 TaskExecutor và @Async làm việc trong ApplicationContext

`TaskExecutor` là abstraction thực thi công việc của Spring. `ThreadPoolTaskExecutor` thường quản lý một pool cục bộ với các thread/queue theo cấu hình. `@Async` cho phép gọi method thông qua Spring proxy rồi dispatch vào executor, nhưng **không tự tạo một durable job**.

Ví dụ Spring:

~~~java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Service
public class ThumbnailService {

    @Async("thumbnailExecutor")
    public CompletableFuture<String> render(String imageId) {
        String location = generateThumbnail(imageId);
        return CompletableFuture.completedFuture(location);
    }

    private String generateThumbnail(String imageId) {
        // Đây là logic minh họa, thay bằng image processing thực tế.
        return "thumbnails/" + imageId + ".webp";
    }
}
~~~

Đây là **Spring class minh họa**, cần executor bean `thumbnailExecutor` và `@EnableAsync` ở cấu hình. Nếu một request gọi `render` qua Spring proxy, công việc được giao vào pool của **JVM hiện tại**. Khi JVM bị dừng sau lúc đã nhận task nhưng trước lúc thực hiện xong, việc chưa hoàn thành trong queue RAM có thể **mất**. `CompletableFuture` trả cho caller không phải một record bền vững có thể tra cứu từ một JVM mới.

Lưu ý `@Async` qua Spring proxy: nếu tự gọi `this.render(...)` từ cùng object trong proxy mode, interception có thể không xảy ra. Nhiều ứng dụng đã gặp lỗi “tưởng async mà thực ra chạy inline”; đây là lỗi local concurrency, chưa phải một bài toán distributed.

### 3.2 @Scheduled và TaskScheduler cũng cục bộ theo context

`TaskScheduler` được dùng cho việc lên lịch. `@Scheduled` đăng ký periodic task **khi Spring context khởi tạo** nếu `@EnableScheduling` được bật. Mỗi application instance thường tự có registration riêng.

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredCacheCleanup {

    @Scheduled(fixedDelayString = "${cleanup.delay:PT1M}")
    public void clean() {
        // Dọn local cache, không cần điều phối nhiều JVM.
    }
}
~~~

Đây là **Spring component minh họa**. Nếu chạy ba pods, thường có **ba schedules**, không có “một schedule chung” chỉ vì cron hoặc annotation giống nhau.

### 3.3 Thay ThreadPoolTaskExecutor bằng pool lớn hơn không làm task bền

Một pool 100 threads vẫn ở trong process. `queueCapacity=10000` có thể chứa nhiều tác vụ chờ, nhưng nếu queue là local RAM, process chết thì các entry không tự chuyển sang replica khác. Việc đổi `AbortPolicy` sang `CallerRunsPolicy` là thay chính sách backpressure/latency trong process; nó **không tạo replication**.

### 3.4 Thread context không phải distributed context

Transaction JDBC, `ThreadLocal`, MDC, SecurityContext của request thường không tự truyền sang một thread khác, càng không tự truyền sang pod khác. Các thông tin cần dùng giữa process phải được diễn đạt thành **payload/metadata rõ ràng** như job ID, tenant ID, traceparent/correlation ID, version, deadline; transaction của worker được thiết lập riêng.

## 4. Ví dụ hai pods và một @Scheduled: tại sao chạy hai lần?

Giả sử ứng dụng có:

~~~java
@Scheduled(cron = "0 0 2 * * *")
public void generateDailyInvoiceSummary() {
    invoiceService.generateSummary();
}
~~~

Đây là fragment trong Spring bean; cron Spring có **sáu trường**, gồm giây. Nếu hệ thống deploy hai replicas A/B, mỗi replica có thể tự đăng ký:

~~~text
02:00:00
    |
    +--> Pod A: @Scheduled -> generateSummary()
    |
    +--> Pod B: @Scheduled -> generateSummary()
~~~

Nếu cả hai cùng xử lý ngày 2026-10-29 mà không có idempotency/coordination, có thể tạo hai báo cáo, hai email hoặc hai giao dịch.

**Sai kết luận:** “Cấu hình scheduler beanName giống nhau nên Spring tự chia việc giữa pods.”

**Đúng:** cùng một bean name chỉ hữu ích **bên trong context**; nó không tạo distributed leader election. Muốn chỉ một chủ sở hữu cho business window, cần thêm distributed lock/lease, unique constraint, Quartz cluster, Kubernetes CronJob hoặc một service orchestration khác.

Nhưng ngay cả khi chỉ **một** pod được chọn để chạy, nếu pod đó chết sau khi gọi external API mà trước khi cập nhật trạng thái, một pod khác vẫn có thể xử lý lại. **Chống chạy trùng khi kích hoạt** và **chống trùng side effect khi retry** là hai nhiệm vụ khác nhau.

## 5. Ma trận bảo đảm: không đánh đồng durability, delivery và kết quả

| Khả năng | Local executor | Durable queue/broker | Durable workflow | Clustered scheduler |
| --- | --- | --- | --- | --- |
| Chạy ngoài request thread | Có | Có, qua worker | Có, qua worker/activity | Có, trên node được chọn |
| Phân phối sang JVM khác | Không tự có | Có | Có | Có thể phân phối lượt trigger |
| Giữ việc chưa xử lý qua JVM crash | Không tự có | Có nếu broker/queue cấu hình durable đúng | Có nếu history/store bền vững đúng | Trigger có thể bền nếu persistent JobStore |
| Retry công việc | Tự viết/handler local | Có thể redeliver/retry theo broker/consumer policy | Có policy riêng cho activities/workflows | Misfire/recovery theo scheduler policy |
| Chờ tín hiệu bên ngoài nhiều ngày | Không phù hợp nếu chỉ RAM | Cần tự xây state/correlation | Phù hợp | Không phải chức năng chính |
| Chỉ một fire time trong cluster | Không | Không phải chức năng chính | Có thể qua orchestration/schedule feature | Có cơ chế nếu cấu hình cluster đúng |
| Exactly-once external effect | Không | Không tự có | Không tự có | Không tự có |
| Workflow history từng bước | Không | Message/offset không tương đương workflow history | Có, theo engine | Job trigger metadata không tương đương |
| Cần quản trị hạ tầng ngoài process | Ít | Có | Có | Có nếu clustered/persistent |

“Có” trong bảng là **khả năng khi được cấu hình và vận hành phù hợp**, không phải lời hứa tự động của mọi product. Một queue non-durable, broker mất quorum hoặc publisher không nhận publisher confirm có thể vẫn mất việc. Một workflow activity gọi API không idempotent có thể tạo duplicate dù workflow history bền vững.

## 6. Mức bảo đảm delivery: at-most-once, at-least-once, exactly-once

### At-most-once

Một đơn vị công việc được thử tối đa một lần theo hợp đồng đang xét. Nếu consumer/process chết trước khi hoàn thành, việc có thể **bị mất**. Ví dụ đơn giản: ghi “đã xử lý” hoặc ack **trước khi** thực thi external side effect; sau crash broker không redeliver dù side effect chưa diễn ra.

### At-least-once

Hệ thống có thể giao lại nếu chưa có ack/commit thành công. Điều này giúp tránh mất việc do crash **nếu nguồn dữ liệu có độ bền**, nhưng cùng message có thể được xử lý **nhiều lần**.

Ví dụ:

~~~text
Broker: message M chưa ack
  -> Worker A nhận M
  -> Worker A gọi payment provider: provider đã charge
  -> Worker A crash trước khi ack
  -> Broker redeliver M
  -> Worker B nhận M
  -> Worker B gọi provider lần nữa nếu không idempotent
~~~

Lời hứa at-least-once là về **delivery attempt**, không phải exactly-once của charge.

### Exactly-once

Khái niệm “exactly once” phải luôn nêu **phạm vi**: exactly-once theo record trong stream-processing transaction, theo unique row trong database, theo command ID ở API cung cấp idempotency, hay theo end-to-end business effect. Một tính năng broker quảng bá “exactly-once semantics” **không tự bao trùm** database ngoài, email, HTTP API hoặc hai systems độc lập.

Thực tế, thường thiết kế **at-least-once delivery + idempotent consumer** để đạt “một tác động nghiệp vụ cho một idempotency key” dưới các giả định rõ ràng. Cần test cả khi network response mất sau khi side effect đã thành công.

## 7. Hàng đợi công việc: producer, broker, consumer, ack

Để tách request nhận việc và worker xử lý, có thể sử dụng RabbitMQ, Apache Kafka, Amazon SQS hoặc dịch vụ queue tương đương. Cơ chế cụ thể **khác nhau** giữa các hệ thống; không nên áp dụng cùng một bảng thuật ngữ cho mọi broker.

~~~text
HTTP / command / event producer
          |
          | publish task(messageId, type, payloadRef)
          v
 Durable broker / topic / queue
          |
          +---- Consumer A (worker)
          |
          +---- Consumer B (worker)
          |
          +---- Consumer C (worker)
                   |
                   v
              Processing
                   |
          +--------+---------+
          |                  |
       success              failure
          |                  |
      ack/offset          retry/requeue/DLQ
      tiến triển          theo chính sách
~~~

### Producer và delivery confirmation

Gọi API `convertAndSend` hoặc `KafkaTemplate.send` không đồng nghĩa message chắc chắn đã được lưu bền. Tùy broker, producer cần cơ chế confirmation/ack, retry và cấu hình resource durable/quorum/replication.

**Producer không được công bố “đã giao việc bền”** chỉ từ việc đã enqueue vào một queue trong RAM hoặc nhận về một `CompletableFuture` chưa hoàn tất. Nếu request cần trả job ID, xác định chính xác mốc nào bảo đảm job tồn tại trong store/broker.

### Consumer acknowledgement

- **RabbitMQ:** thông điệp có thể được acknowledge sau khi listener xử lý, hoặc quản lý ack/nack thủ công theo container configuration. Nack/requeue/dead-letter phụ thuộc policy.
- **Kafka:** consumer sử dụng offset/consumer group. Commit offset đánh dấu tiến độ đọc, không tự atomic với một external database hoặc HTTP call.
- **SQS-like queues:** message có visibility timeout; nếu worker chưa delete message trong hạn, nó có thể xuất hiện cho consumer khác. Cần gia hạn visibility/lease phù hợp task dài.

**Không ack trước success** nếu hệ thống yêu cầu at-least-once. Nhưng ngay cả khi ack sau success, vẫn có **cửa sổ duplicate** khi side effect đã thành công còn ack chưa được broker ghi nhận.

### Backpressure

Local thread queue, broker backlog và workflow task queue có ý nghĩa khác. Với message queue, đo consumer lag/depth, max concurrency, prefetch, rate limit, age of oldest message, retry budget và DLQ. Không chỉ tăng số consumer vô hạn: downstream có thể bị quá tải, DB connection pool có thể cạn, và duplicate/retry storms có thể tăng.

## 8. Ví dụ Spring AMQP: consumer không phải @Async local

Một fragment dùng Spring AMQP:

~~~java
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InvoiceEmailConsumer {
    private final InvoiceEmailService service;

    public InvoiceEmailConsumer(InvoiceEmailService service) {
        this.service = service;
    }

    @RabbitListener(queues = "invoice-email-requests")
    public void consume(InvoiceEmailMessage message) {
        service.sendIdempotently(
                message.messageId(), message.invoiceId());
    }
}
~~~

Đây là **Spring integration example**, không phải class Java 17 có thể chạy độc lập. `InvoiceEmailMessage` là DTO ứng dụng; `InvoiceEmailService` phải có logic idempotent và transaction phù hợp. Cần Spring AMQP connection factory, queue/binding, conversion/serialization, listener container và retry/ack policy đúng.

**Lưu ý:** chỉ gắn `@RabbitListener` không tự làm queue durable, không bảo đảm publish thành công, không tự cấu hình DLQ, và không tự bảo đảm mail chỉ gửi một lần. Với listener đồng bộ trong chế độ ack tự động thường dùng, container có thể ack sau khi method trả bình thường và xử lý lỗi theo cấu hình; cần test đường lỗi, chuyển DLQ, số lần retry và requeue thực tế.

Nếu listener bắt exception rồi **im lặng return**, container có thể hiểu là processing success và ack; vì thế phải lưu trạng thái retryable failure hoặc ném exception để error/retry policy phù hợp hoạt động. Không dùng catch-and-ignore như một cách “làm queue ổn định”.

## 9. Khi nào queue phù hợp, khi nào chưa đủ?

Queue phù hợp khi:

- Mỗi message mô tả một đơn vị việc tương đối độc lập, có thể nhận/ack riêng.
- Cần buffer lưu lượng giữa producer và consumers.
- Cần thay đổi số worker độc lập với API layer.
- Task có thể retry, duplicate có thể được kiểm soát bằng idempotency.
- Ưu tiên throughput, load leveling và tách deploy.

Queue **chưa đủ một mình** khi:

- Quy trình phải đợi **phê duyệt từ con người** sau vài ngày.
- Có nhiều bước với branching, timeout và compensation, cần nhìn “đang ở bước 7/12”.
- Cần biết một payment đã authorize, một shipment đã book, rồi khi fail phải refund/cancel phần trước.
- Cần timer bền vững và giao tiếp signal/callback vào một execution có identity.
- Cần audit toàn bộ tiến trình qua restart và replay/recovery.

Bạn có thể tự xây workflow bằng queue + DB state machine. Điều đó hợp lệ nếu bài toán đơn giản và đội ngũ đủ khả năng kiểm soát state transitions, retries, signals, leases, migrations. Nhưng một queue không tự sinh **durable orchestration history** cho bạn.

## 10. Transactional outbox: chống mất việc ở ranh giới DB và broker

Một lỗi kinh điển là “dual write”:

~~~text
BEGIN TRANSACTION
  INSERT order
COMMIT
publish OrderCreated event to broker
~~~

Nếu process crash **sau commit nhưng trước publish**, order có trong DB nhưng không có event. Đảo thứ tự publish trước commit lại có thể tạo event về một order sau đó rollback.

**Transactional outbox** ghi dữ liệu nghiệp vụ và outbox record **trong cùng DB transaction**:

~~~sql
-- SQL mô hình, tên bảng/cột là ví dụ:
BEGIN;

INSERT INTO orders (order_id, status)
VALUES ('ord-42', 'CREATED');

INSERT INTO outbox_events
    (event_id, aggregate_id, event_type, payload, status)
VALUES
    ('evt-42', 'ord-42', 'OrderCreated', '...', 'NEW');

COMMIT;
~~~

Đây là **SQL mô hình**, không phải DDL/transaction script portable cho mọi database. Worker/CDC đọc outbox, publish broker, rồi cập nhật trạng thái. Nếu process crash sau publish nhưng trước khi đánh dấu `SENT`, event có thể **được publish lại**; vì vậy outbox giải quyết **atomic record creation** ở DB gốc, không tự bảo đảm receiver side effect exactly once.

### Flow

~~~text
TX business + outbox
       |
       | COMMIT atomically within ONE database
       v
Outbox row NEW
       |
       v
Relay / CDC / worker
       |
       +--- send to broker
       |
       +--- mark sent / advance offset
                   |
           crash here? duplicate publish possible
                   |
                   v
       Consumer idempotency
~~~

**Điểm học tập:** mỗi boundary có hợp đồng riêng: DB transaction, outbox relay, broker delivery, consumer DB write, external API. Không có một annotation Spring duy nhất tự tạo atomic transaction bao tất cả các boundary này.

## 11. Inbox/idempotent consumer: xử lý message giao lại

Một consumer nên nhận một `eventId` hoặc `jobId` ổn định từ producer. Trong cùng database transaction với business update, có thể dùng unique constraint để ngăn cùng event được áp dụng hai lần:

~~~sql
-- DDL ý tưởng (cú pháp có thể khác giữa các DB):
CREATE TABLE processed_messages (
    consumer_name VARCHAR(100) NOT NULL,
    message_id VARCHAR(150) NOT NULL,
    PRIMARY KEY (consumer_name, message_id)
);
~~~

Pseudo-flow:

~~~text
BEGIN DB TRANSACTION
  INSERT processed_messages(consumer_name, message_id)
  IF unique conflict: already processed -> COMMIT and ACK
  ELSE:
      apply business update
      COMMIT
ACK message after commit
~~~

Đây là **thuật toán minh họa**, cần transaction isolation, xử lý unique conflict và ack semantics phù hợp driver/broker. Với một SQL engine, duplicate-key error có thể khiến transaction hiện tại phải rollback; production code cần thực hiện insert-on-conflict/transaction strategy đúng database, **không blindly catch exception rồi commit**.

### External API phá vỡ tính atomic

Nếu consumer gọi payment provider hoặc gửi email, việc lưu `processed_messages` trong DB không atomic với external API. Có thể xảy ra:

- API thành công nhưng response mất.
- Worker chết trước khi ghi processed marker.
- Message được redeliver, consumer không thấy marker và gọi API lần nữa.

Với thanh toán, dùng **idempotency key được provider hỗ trợ**, lưu operation ID, đối chiếu trạng thái provider trước retry, và có reconciliation. Với email, nếu provider không có idempotent send API, đôi khi phải chấp nhận at-least-once/possible duplicate hoặc xây dedup ở lớp khác. Không hứa exactly-once nếu external destination không hỗ trợ.

## 12. Workflow: quy trình dài và trạng thái nhiều bước

Một workflow không chỉ là “nhiều tasks trong một list”. Nó cần một **execution identity** và state đủ để sau crash vẫn biết:

1. Những bước nào đã hoàn thành.
2. Những activities nào còn chờ hoặc phải retry.
3. Deadline/timeout của từng bước.
4. Signal nào đã đến từ con người hoặc hệ thống ngoài.
5. Compensation nào phải chạy khi bước sau thất bại.
6. Sự kiện và phiên bản code nào đã quyết định nhánh xử lý.

Ví dụ một quy trình đơn hàng:

~~~text
OrderWorkflow(orderId)
    |
    +--> Validate inventory
    |
    +--> Reserve stock -------------+
    |                               |
    +--> Authorize payment          |
    |                               |
    +--> Wait for fraud approval <--+-- external signal / timeout
    |
    +--> Create shipment
    |
    +--> Capture payment
    |
    +--> COMPLETE
          OR
         compensate successful earlier steps on failure
~~~

Nếu chỉ viết:

~~~java
CompletableFuture.runAsync(this::reserve)
    .thenRunAsync(this::authorize)
    .thenRunAsync(this::ship);
~~~

đây là **mã ý tưởng không đầy đủ** và còn có thể dùng executor chung không kiểm soát. `CompletableFuture` lưu chain ở bộ nhớ process; JVM crash không cung cấp workflow history, external signal handling hay recovery. Không dùng chuỗi future trong RAM làm workflow engine cho một tiến trình kéo dài nhiều ngày.

### Durable workflow engine làm thêm điều gì?

Các hệ thống như **Temporal** duy trì **event history** bên ngoài worker, hỗ trợ replay/recovery từ những sự kiện đã được ghi. Workflow code cần đáp ứng **tính quyết định khi replay**; I/O ngoài được biểu diễn qua **activities** hoặc các primitive phù hợp. Activity có thể được retry, vì vậy side effect của activity vẫn cần idempotent hoặc một quy tắc reconciliation.

**Đừng hiểu “workflow được replay” là mọi email/API call đều được replay tùy tiện.** Với engine dựa event history, các kết quả activity đã ghi thường được dùng lại khi replay workflow logic, còn activity chưa hoàn tất có thể được retry/được giao cho worker khác. Chi tiết retry, timeout, cancellation và versioning phải theo đúng engine.

### Saga và compensation

Workflow phân tán thường không thể rollback một ACID transaction xuyên nhiều services. Thay vào đó, có thể thiết kế **saga**:

- Sau khi reserve stock, nếu payment fail thì release stock.
- Sau khi charge thành công nhưng shipment fail, có thể refund hoặc đưa đơn vào manual review.
- Compensation **không phải SQL rollback tuyệt đối**: refund có thể lỗi, email đã gửi không thể “thu hồi”.
- Cả forward action và compensation cần idempotency keys, retry và audit.

Nếu quy trình chỉ có hai bước đồng bộ trong một transaction DB duy nhất, dùng workflow engine có thể quá phức tạp. Chọn theo thực tế **state sống ngoài process bao lâu** và failure recovery cần sâu đến đâu.

## 13. So sánh queue và workflow qua một tình huống

Nhiệm vụ: gửi báo cáo sau khi nhập dữ liệu.

**Queue-centric:** producer gửi `GenerateReport(reportId)`, worker lấy message và tạo PDF một lần, ghi kết quả vào DB, ack. Nếu fail, broker redeliver theo policy. Phù hợp khi mỗi message là một đơn vị việc tương đối tự đủ.

**Workflow-centric:** khi có dữ liệu, hệ thống phải chờ xác minh dữ liệu, yêu cầu phê duyệt của hai phòng ban, timeout sau 48 giờ, xử lý nhánh từ chối, tạo báo cáo, gửi, chờ xác nhận, có escalation. Một workflow có execution ID và event history phù hợp hơn một hàng đợi đơn giản.

**Kết hợp:** workflow engine giao activities cho worker thông qua task queues nội bộ; hoặc workflow gửi message sang broker cho một loại công việc chuyên trách. Tránh coi hai tầng là “cùng loại queue” chỉ vì đều có worker.

| Tiêu chí | Queue task | Workflow execution |
| --- | --- | --- |
| Đơn vị danh tính | Message/job ID | Workflow ID/run ID + activity IDs |
| Mô hình chính | Receive -> process -> ack/retry | State transitions, timers, signals, branching |
| Chờ con người nhiều ngày | Phải tự xây state/correlation | Use case thường phù hợp |
| Retry | Theo message/consumer policy | Theo activity/workflow policy |
| Quan sát từng bước | Phải tự ghi | Workflow history / visibility features |
| Durability | Phụ thuộc broker và cấu hình | Phụ thuộc workflow store/service |
| Duplicate external effect | Vẫn phải chống | Vẫn phải chống |

Không dùng bảng này để suy rằng mọi workflow engine hỗ trợ cùng một API hay có chi phí tương đương.

## 14. Lịch phân tán: ai được quyền kích hoạt?

Một scheduler **không nhất thiết làm công việc nghiệp vụ**. Nó có thể chỉ chịu trách nhiệm phát lệnh `StartDailyReport(windowId)` vào một queue hoặc durable job store. Trong hệ thống nhiều instances, cần phân biệt:

- **Registration:** những ai đang có lịch?
- **Fire time:** mốc UTC/timezone nào được xem là một lượt?
- **Acquisition:** node nào được phép claim lượt đó?
- **Dispatch:** node nào sẽ chạy business work?
- **Completion:** bên nào ghi kết quả/ack?
- **Misfire/recovery:** hệ thống xử lý những lượt bỏ lỡ khi down như thế nào?

Một distributed scheduler tốt hơn local `@Scheduled` ở **phối hợp trigger** nếu đã được cấu hình đúng. Nhưng nó không tự chịu trách nhiệm về việc charge/email external xảy ra đúng một lần.

### “Một trigger” khác “một business effect”

Một clustered scheduler có thể bảo đảm mỗi trigger acquisition bình thường chỉ được một node lấy tại một thời điểm. Nếu node đó chết sau khi thực hiện effect nhưng trước khi ghi completion, cơ chế recovery có thể làm một node khác chạy lại. Để ngăn **double charge/double shipment**, business action vẫn phải idempotent.

## 15. Quartz clustered scheduler với JDBC JobStore

Spring tích hợp với **Quartz Scheduler**. Spring Boot có `spring-boot-starter-quartz` và có thể cấu hình JDBC JobStore. Quartz **không tự bật clustering chỉ vì thư viện được thêm vào classpath**.

Ý tưởng cấu hình Spring Boot, cần các dependency, schema và DataSource phù hợp:

~~~yaml
spring:
  quartz:
    job-store-type: jdbc
    properties:
      org.quartz.scheduler.instanceName: ReportingCluster
      org.quartz.scheduler.instanceId: AUTO
      org.quartz.jobStore.isClustered: true
      org.quartz.jobStore.clusterCheckinInterval: 20000
~~~

Đây là **đoạn YAML minh họa**, cần đối chiếu đầy đủ cấu hình Quartz JDBC của phiên bản đang dùng, bao gồm driver delegate, datasource và bảng Quartz. Clustering Quartz với JDBC JobStore cần các nodes cùng dùng **một bộ bảng JobStore** tương thích; mỗi instance có ID riêng, có cơ chế check-in/failover và acquisition của trigger.

**Cảnh báo vận hành:** không nên bật `spring.quartz.jdbc.initialize-schema=always` một cách vô ý trong production có dữ liệu: tài liệu Spring Boot cảnh báo script khởi tạo mặc định có thể **drop các bảng Quartz**, xóa triggers đã lưu. Schema cần được quản lý bằng migration có kiểm soát.

### Quartz giải quyết gì?

- Lưu trigger/job metadata trong JDBC store nếu cấu hình persistent.
- Phối hợp acquisition giữa các scheduler instances qua JobStore.
- Có chính sách misfire/recovery cho các loại trigger.
- Có cơ chế job identity, calendar, pausing/resuming theo APIs Quartz.

### Quartz không tự giải quyết gì?

- External effect exactly-once.
- Atomic commit giữa DB nghiệp vụ và HTTP endpoint bất kỳ.
- State machine nhiều bước có human approval như workflow engine.
- Bất kỳ replica Spring `@Scheduled` nào không dùng Quartz đều được đồng bộ theo Quartz cluster.
- Tất cả failure policy không cần cấu hình và test.

Một task Quartz có thể đóng vai trò **kích hoạt hàng đợi** thay vì xử lý batch nặng ngay trong scheduler thread. Với công việc dài hoặc cần backpressure, tách trigger và processing sẽ rõ SLA hơn.

## 16. Kubernetes CronJob là một bộ tạo Job theo lịch, không phải exact-once engine

Kubernetes CronJob chạy theo cron **năm trường** thông thường, khác Spring `@Scheduled(cron = ...)` vốn có trường **giây**. Ví dụ:

~~~yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: daily-report-trigger
spec:
  schedule: "0 2 * * *"
  timeZone: "Asia/Ho_Chi_Minh"
  concurrencyPolicy: Forbid
  startingDeadlineSeconds: 300
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure
          containers:
            - name: trigger
              image: example/report-trigger:1.0
              args: ["--window=today"]
~~~

Đây là **manifest minh họa**, tên image chỉ để biểu diễn cấu trúc, không phải image có thể pull. Cần Kubernetes version hỗ trợ `spec.timeZone`, image/permissions/secrets thực tế, công việc nhận business window và cơ chế idempotency. Không copy nguyên `--window=today` cho nghiệp vụ cần replay theo ngày đã định: business window phải gắn **scheduled timestamp** được điều phối, không lệ thuộc giờ thực tế pod bắt đầu.

### Ba chế độ concurrency

| `concurrencyPolicy` | Ý nghĩa đối với Jobs của cùng CronJob |
| --- | --- |
| `Allow` (mặc định) | Cho phép các Jobs từ cùng CronJob chạy đồng thời |
| `Forbid` | Không start một Job mới khi Job trước còn active; có thể bỏ một lượt dự kiến |
| `Replace` | Thay Job đang chạy bằng Job mới, có hệ quả cancellation |

`Forbid` **không** bảo đảm toàn cluster chỉ có một business effect mãi mãi. Nó chỉ kiểm soát overlap ở **phạm vi CronJob object đó**; hai CronJob objects khác nhau vẫn có thể chạy cùng logic. Job có thể thất bại, Pod có thể retry, và controller có thể tạo **không có Job hoặc hai Jobs** trong một số tình huống. Tài liệu Kubernetes khuyến nghị Jobs phải **idempotent**.

### Misfire / suspended schedule

`startingDeadlineSeconds` đặt giới hạn độ trễ để khởi tạo một Job lỡ lịch; `suspend=true` ngăn tạo Jobs mới cho các lượt tiếp theo. Các lượt đã khởi động trước đó không tự bị hủy chỉ vì suspend. Quy định catch-up/missed schedules có chi tiết controller cần đọc đúng phiên bản Kubernetes; không dùng CronJob như “ledger mọi business date phải xử lý”.

### Khi nào CronJob phù hợp?

Phù hợp để kích hoạt **Kubernetes Jobs** theo lịch vận hành, đặc biệt khi mỗi lượt chạy là một workload độc lập có thể restart. Nếu ứng dụng muốn cập nhật một bảng dữ liệu duy nhất toàn công ty, vẫn cần unique business window hoặc idempotent output để đối phó retry/duplicate.

## 17. Distributed lock/lease: giải pháp nhẹ nhưng có ranh giới

Một cách giữ local `@Scheduled` nhưng chỉ một node thực sự xử lý là sử dụng **distributed lease** trong database hoặc coordination store.

~~~text
Pod A @Scheduled ----+
                     +--> acquire lease("daily-report", windowId)
Pod B @Scheduled ----+            |
                                  v
                         one owner selected
                                  |
                             execute job
                                  |
                         release / expire lease
~~~

Để làm đúng, phải xác định:

- **Identity của job/window**: ví dụ `daily-report/2026-10-29`.
- **Atomic claim**: chỉ một transaction được phép chiếm lease khi điều kiện thỏa.
- **TTL/expiration**: nếu owner chết, lease có thể được chủ sở hữu khác lấy.
- **Heartbeat/renewal**: job dài cần gia hạn đúng.
- **Clock semantics**: dùng đồng hồ đáng tin (thường dựa vào DB time) để tránh clock skew.
- **Fencing token**: token tăng đơn điệu khi đổi owner, dùng để từ chối write của worker cũ nếu lease đã hết hạn.
- **State/recovery**: job hoàn thành, thất bại, bị mất lease hoặc pending được lưu ở đâu.

### Split-brain do lease hết hạn

Giả sử:

~~~text
00:00 A lấy lease với token 41
00:01 A bị pause lâu (GC hoặc mạng chậm)
00:02 lease hết hạn
00:03 B lấy lease mới với token 42
00:04 A hồi phục, tưởng vẫn sở hữu và tiếp tục ghi dữ liệu
00:04 B cũng đang xử lý
~~~

Chỉ dùng một lock key với TTL **không tự ngăn hai worker cũ/mới gây side effect đồng thời**. Với storage hỗ trợ, fencing token trong mỗi write giúp từ chối token cũ. Nếu external API không hiểu fencing, dùng idempotency key, status check hoặc thêm một service chịu trách nhiệm độc quyền.

### SQL claim chỉ là ý tưởng

~~~sql
-- PSEUDOCODE: cú pháp atomic UPDATE/RETURNING,
-- đồng hồ DB và isolation phụ thuộc hệ quản trị.
UPDATE job_leases
SET owner_id = :owner,
    lease_until = :db_now_plus_ttl,
    fencing_token = fencing_token + 1
WHERE job_key = :job_key
  AND lease_until < :db_now
RETURNING fencing_token;
~~~

Đây là **SQL phác thảo**, không phải một statement portable để chạy không chỉnh sửa. Cần xử lý trường hợp job key chưa tồn tại, affected-row count, transaction isolation và chỉ tiếp tục khi claim thực sự thành công.

### Khi distributed lock là đủ?

- Job tương đối đơn giản và có thể tính lại/idempotent.
- Không cần durable step history phức tạp.
- Chấp nhận tự xây heartbeat/recovery/observability.
- Hệ thống đã có một DB/coordination store đáng tin.
- SLA cho missfire/catch-up được ghi rõ.

Nếu bắt đầu tự viết cả scheduler ledger, retry queue, cron calculator, lease, timeout, web UI, workflow state và compensation, hãy cân nhắc một platform chuyên dụng thay vì tiếp tục mở rộng một annotation.

## 18. Spring Batch: processing framework, không tự thay thế distributed scheduler

Spring Batch cung cấp mô hình `Job`, `Step`, `ItemReader`/`ItemProcessor`/`ItemWriter`, transaction chunk và `JobRepository` lưu metadata `JobExecution`/`StepExecution`. Khi dùng **persistent JobRepository** và cấu hình phù hợp, nó giúp quản lý trạng thái/restart một batch execution.

Tuy nhiên:

- `JobRepository` không tự tạo một **global clock trigger** hay distributed leader cho `@Scheduled` ở mọi replica.
- Spring Batch có thể chạy trong một JVM hoặc dùng remote partitioning/chunking, nhưng cần cấu hình kiến trúc tương ứng.
- Job restartability phụ thuộc ItemReader/Writer, transaction semantics và các flags `saveState` thích hợp; không mặc nhiên mọi external API side effect đều replay-safe.
- Cấu hình **resourceless JobRepository** không lưu metadata bền và không phù hợp yêu cầu restart từ trạng thái đã lưu.

Ví dụ use case: mỗi đêm xử lý 30 triệu records theo chunk và checkpoint; Quartz/K8s/other scheduler có thể **launch** Spring Batch Job; job có persistent `JobRepository` để restart; queue/partitioning có thể phân tải nếu cần. **Batch engine** và **lịch kích hoạt** là hai tầng trách nhiệm khác.

## 19. Ví dụ Java 17+ chạy độc lập: local tasks, hai replicas và giao lại thông điệp

Chương trình sau mô phỏng ba ranh giới mà không cần Spring, database hay message broker thật:

1. **Local queue:** một công việc nằm trong object RAM; khi tạo process/state mới, pending queue không tự khôi phục.
2. **Hai local schedulers:** hai instances độc lập đều kích hoạt cùng một logical tick.
3. **Mô hình broker có redelivery:** worker thực hiện side effect rồi “crash” trước ack; message được giao lại, và **idempotency set chung** chặn tác động lặp.

**Giới hạn rõ:** `BrokerModel` và `processed` vẫn ở RAM của **một chương trình Java**. Demo chỉ mô phỏng semantics; nó **không tạo độ bền thật**, không có network, disk, quorum hay transaction. Trong production, những thứ này phải được thay bằng broker/DB/idempotency store bền vững.

Tệp **CoordinationBoundaryDemo.java**:

~~~java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoordinationBoundaryDemo {

    static final class LocalDispatcher {
        private final Deque<String> pending = new ArrayDeque<>();

        void submit(String jobId) {
            pending.addLast(jobId);
        }

        int pendingCount() {
            return pending.size();
        }
    }

    static final class BrokerModel {
        private final Deque<String> ready = new ArrayDeque<>();
        private String inFlight;

        void publish(String messageId) {
            ready.addLast(messageId);
        }

        String deliver() {
            if (inFlight != null) {
                throw new IllegalStateException("already delivering");
            }
            inFlight = ready.removeFirst();
            return inFlight;
        }

        void simulateWorkerCrashBeforeAck() {
            if (inFlight == null) {
                throw new IllegalStateException("nothing in flight");
            }
            ready.addFirst(inFlight);
            inFlight = null;
        }

        void ack() {
            if (inFlight == null) {
                throw new IllegalStateException("no delivery to ack");
            }
            inFlight = null;
        }

        int pendingCount() {
            return ready.size() + (inFlight == null ? 0 : 1);
        }
    }

    public static void main(String[] args) throws Exception {
        LocalDispatcher processA = new LocalDispatcher();
        processA.submit("job-17");
        System.out.println(
                "localBeforeRestart=" + processA.pendingCount());

        // Mô phỏng một process mới: state cũ không tồn tại ở đây.
        LocalDispatcher restarted = new LocalDispatcher();
        System.out.println(
                "localAfterRestart=" + restarted.pendingCount());

        ScheduledExecutorService nodeA =
                Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService nodeB =
                Executors.newSingleThreadScheduledExecutor();
        AtomicInteger fired = new AtomicInteger();
        CountDownLatch both = new CountDownLatch(2);

        try {
            Runnable sameLogicalTick = () -> {
                fired.incrementAndGet();
                both.countDown();
            };
            nodeA.schedule(sameLogicalTick, 0, TimeUnit.MILLISECONDS);
            nodeB.schedule(sameLogicalTick, 0, TimeUnit.MILLISECONDS);

            if (!both.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Missing scheduler tick");
            }
            System.out.println("twoNodesOneTick=" + fired.get());
        } finally {
            nodeA.shutdownNow();
            nodeB.shutdownNow();
            nodeA.awaitTermination(5, TimeUnit.SECONDS);
            nodeB.awaitTermination(5, TimeUnit.SECONDS);
        }

        BrokerModel broker = new BrokerModel();
        Set<String> processed = new HashSet<>();

        broker.publish("evt-42");
        String firstDelivery = broker.deliver();
        boolean firstEffectApplied = processed.add(firstDelivery);

        // Side effect đã xảy ra, nhưng worker chết trước ACK.
        broker.simulateWorkerCrashBeforeAck();
        String secondDelivery = broker.deliver();

        boolean redelivered =
                firstDelivery.equals(secondDelivery);
        boolean duplicateEffectApplied =
                processed.add(secondDelivery);

        broker.ack();

        System.out.println(
                "firstEffectApplied=" + firstEffectApplied);
        System.out.println("redelivered=" + redelivered);
        System.out.println(
                "duplicateSuppressed=" + !duplicateEffectApplied);
        System.out.println(
                "brokerPendingAfterAck=" + broker.pendingCount());
    }
}
~~~

Lệnh chạy:

~~~bash
javac CoordinationBoundaryDemo.java
java CoordinationBoundaryDemo
~~~

Stdout kỳ vọng, không phụ thuộc lịch CPU vì hai jobs được đồng bộ qua `CountDownLatch`:

~~~text
localBeforeRestart=1
localAfterRestart=0
twoNodesOneTick=2
firstEffectApplied=true
redelivered=true
duplicateSuppressed=true
brokerPendingAfterAck=0
~~~

### Đọc kết quả đúng cách

- `localBeforeRestart=1`: một object local có một job RAM.
- `localAfterRestart=0`: object đại diện process mới không có state RAM của process cũ; chương trình **không thật sự kill/restart JVM**, vì vậy đây là **mô hình**.
- `twoNodesOneTick=2`: hai local executors độc lập đều chạy một Runnable cho cùng logical tick; không có coordination tự động.
- `firstEffectApplied=true`: side effect được mô phỏng bằng thêm ID vào `Set`.
- `redelivered=true`: broker model gửi cùng event sau crash-before-ack.
- `duplicateSuppressed=true`: idempotency set chung ngăn apply event lần hai trong mô hình.
- `brokerPendingAfterAck=0`: lần redelivery đã được ack.

**Không suy rộng demo thành production correctness:** `HashSet` không an toàn cho nhiều workers thật, không bền qua JVM crash và không atomic với payment API. Production cần **unique constraints/transactional inbox** hoặc provider idempotency, cùng broker configuration đảm bảo delivery đúng hợp đồng.

## 20. Đọc Java demo để tách ba trách nhiệm

### A. In-memory queue chỉ giải quyết admission vào process

`LocalDispatcher.submit` đẩy vào `ArrayDeque`. Không có disk flush, replication, checkpoint, job state hay nhận từ pod khác. Một `ThreadPoolTaskExecutor` có queue tương tự ở khía cạnh **state trong process**, dù implementation thực tế dùng những queue/worker khác.

Nếu muốn caller biết việc đang pending sau restart, cần một **job ID trong external durable store**. Không giải quyết bằng cách tăng heap hoặc giữ thêm Java `Future`.

### B. Hai schedulers cục bộ không tự cùng biết logical window

Hai `ScheduledExecutorService` tạo độc lập. Chúng cùng schedule task ở delay 0 nên mỗi instance thực thi. Spring `@Scheduled` trong hai pods có ranh giới tương tự. Nếu việc là **local cache refresh trên từng pod**, hai lượt có thể đúng. Nếu việc là **gửi một hóa đơn cho khách hàng**, hai lượt có thể là bug.

Điều quyết định không phải “có 2 executions” mà là **business contract của cùng một key/window**.

### C. Giao lại không phải lỗi của broker

Khi worker side effect thành công nhưng chưa ack, broker không thể biết external API đã hoàn tất. Redelivery là một phần của thiết kế at-least-once. Không nên cố “tắt redelivery” để hết duplicate; làm vậy có thể mất việc trong failure window đối diện. Hãy chấp nhận delivery lặp và thiết kế **idempotency**.

## 21. Chọn công cụ theo tình huống cụ thể

### Tình huống 1: resize ảnh ngay sau upload, không cần phục hồi

Nếu response có thể chờ hoặc việc mất có thể dễ tạo lại từ nguồn, `@Async`/`ThreadPoolTaskExecutor` trong cùng service có thể đủ. Cần bound pool/queue, timeout, xử lý overload, shutdown. Nếu việc mất sau crash là không chấp nhận được, hãy lưu một durable job record và đưa worker qua queue.

### Tình huống 2: gửi email hoặc tạo thumbnail phải hoàn thành sau khi request trả về

Nếu cần bảo đảm không mất khi API process chết, dùng **durable queue hoặc DB job table**; API nhận request trả một `jobId` sau khi đã lưu bền, worker xử lý, cập nhật status. `@Async` đơn thuần không đáp ứng durability requirement. Với email, cần hiểu duplicate delivery và khả năng dedup của provider.

### Tình huống 3: nạp dữ liệu hằng đêm cho một cluster

Một distributed scheduler/Quartz JDBC/Kubernetes CronJob có thể tạo một **run cho business date**. Worker (có thể dùng Spring Batch) thực hiện ETL, lưu progress/checkpoint, job ID và idempotency theo window. Không dùng mỗi `@Scheduled` ở tất cả replicas nếu chỉ muốn một run logic.

### Tình huống 4: workflow onboarding gồm nhiều bước và human approval

Workflow engine hoặc DB state machine bền vững là lựa chọn phù hợp. Queue có thể phân phối từng activity, nhưng trạng thái “đã xác minh hồ sơ, đang đợi ký, phải timeout sau 72 giờ” cần nơi giữ bền và signal correlation rõ.

### Tình huống 5: mỗi pod tự refresh cache

Local `@Scheduled` trên mọi pod có thể đúng: mỗi pod có state cache riêng. Thêm distributed lock có thể gây sai vì chỉ một pod refresh, các pod khác vẫn stale. **Đừng áp “one leader” cho mọi scheduled task** mà không phân biệt mục tiêu là cluster-wide hay per-instance.

### Tình huống 6: rất nhiều events nhỏ, cần consumer scale

Message broker với consumer group, partition/concurrency phù hợp và at-least-once + idempotency thường có lợi. Nếu cần strict ordering theo customer, partition key/order constraints và consumer concurrency phải được thiết kế; tăng số worker có thể phá thứ tự nếu không bảo vệ.

### Tình huống 7: một tác vụ chạy vào 08:00 ngày hẹn sau sáu tháng

Đừng giữ `ScheduledFuture` trong JVM sáu tháng và nghĩ nó bền vững. Dùng persistent job schedule, workflow durable timer hoặc DB job table dựa trên due time. Khi server down tại 08:00, định nghĩa **misfire policy**: chạy bù trong bao lâu, bỏ qua khi quá hạn, hay escalation.

## 22. Bảng lựa chọn nhanh: câu hỏi nào dẫn tới công cụ nào?

| Yêu cầu | Cấu trúc nên bắt đầu xem |
| --- | --- |
| Chỉ cần tránh block request trong cùng JVM | `@Async` + bounded local executor |
| Chạy định kỳ trên **mỗi instance** | Local `@Scheduled` |
| Sự kiện xảy ra thì nhiều workers có thể xử lý | Durable queue/broker + consumer |
| API trả về ngay nhưng việc phải không mất khi crash | Durable job store + queue/worker |
| Chờ tín hiệu bên ngoài trong vài ngày, nhiều bước phụ thuộc | Durable workflow engine |
| Kích hoạt theo cron **toàn cluster** | Quartz JDBC cluster hoặc Kubernetes CronJob/coordination phù hợp |
| Cần ETL chunk/checkpoint/restart | Spring Batch persistent `JobRepository` |
| Chỉ một worker giữ lock cho một window | DB/distributed lease; bổ sung fencing/idempotency |
| Tác vụ dài, muốn retry/supervise | Queue/job store hoặc workflow, tùy số bước và state |
| Exactly-once external charge/email | Idempotency/reconciliation theo destination; không chỉ scheduler |
| Deadline/misfire qua downtime | Persistent scheduler/timer + business window ledger |
| Nhiều jobs chạy trùng do autoscaling | Distributed ownership hoặc idempotent windows |
| Cần đơn giản nhất, task có thể làm lại nếu mất | Local executor/scheduler có giám sát |

Một quyết định kiến trúc đúng thường là **sự kết hợp tối thiểu**: ví dụ *Kubernetes CronJob + DB job table* hoặc *Spring @Scheduled poller + durable outbox*, không nhất thiết triển khai một workflow engine lớn chỉ vì job “đang ở nền”.

## 23. Transaction ranh giới và phương án hybrid

### Local executor và transaction

Trong Spring dùng JDBC/JPA, transaction thường gắn với thread. `@Async` worker không tự kế thừa transaction của thread request. Nếu trước commit đã dispatch một task đọc row mới, worker có thể không thấy row hoặc thấy trạng thái không cuối cùng.

Một phương án là `@TransactionalEventListener(phase=AFTER_COMMIT)` để kích hoạt sau commit, nhưng **callback trong JVM vẫn có thể mất nếu process crash**. Nếu không được mất, dùng transactional outbox/DB job table.

### Queue + outbox + consumer

~~~text
Business request
  |
  +-- DB TX writes business row + outbox row
  |
  +-- COMMIT
  |
  +-- relay publishes outbox event
  |
  +-- broker stores message
  |
  +-- consumer claims and processes idempotently
  |
  +-- ACK / commit offset after chosen success boundary
~~~

### Scheduler + queue

~~~text
Quartz/K8s/lease chooses business window
  |
  +-- creates durable Run(windowId)
  |
  +-- sends RunRequested message
  |
  +-- worker executes idempotently
  |
  +-- stores outcome; retries if needed
~~~

Scheduler không cần giữ thread trong suốt một ETL kéo dài vài giờ. Trách nhiệm của nó có thể dừng ở **phát một work item bền vững**. Worker có thể scale theo queue backlog.

### Workflow + activities + queue

Workflow giữ execution history và timers; activities thực hiện I/O, có retry/backoff. Đừng để workflow code trực tiếp gọi network theo cách không phù hợp replay/determinism contract của engine. External effects nên ở activity layer với idempotency key.

## 24. Các điểm crash cần thiết kế trước khi chọn công nghệ

| Crash tại thời điểm | Nếu chỉ local | Với durable queue/workflow/scheduler |
| --- | --- | --- |
| Sau khi nhận HTTP, trước khi ghi job | Có thể mất yêu cầu nếu đã trả success | Ghi bền/ack producer đúng boundary trước trả success |
| Sau DB commit, trước broker publish | Event có thể mất | Transactional outbox và relay |
| Sau broker publish, trước producer nhận ack | Producer có thể gửi lại | Event ID ổn định, idempotent consumer |
| Sau consumer nhận, trước side effect | Local worker mất | Broker redelivery/lease/retry |
| Sau side effect, trước ack | Khó phát hiện | Duplicate delivery có thể xảy ra; cần idempotency |
| Sau step 3/10 của quy trình dài | CompletableFuture chain RAM mất | Workflow history/DB state machine phục hồi |
| Node scheduler chết gần fire time | Local tick có thể mất | Persistent trigger/misfire/failover theo policy |
| Lease holder bị pause quá TTL | Có thể có owner thứ hai | Fencing token/idempotency để bảo vệ effect |
| CronJob tạo 2 Jobs cho một tick | Local uniqueness không bảo vệ | Business-window unique constraint/dedup |
| Khi worker đang chạy thì deploy phiên bản mới | Future cũ/ThreadLocal mất | Worker shutdown protocol, leases, replay/versioning |

**Bài kiểm tra kiến trúc:** lấy một viên gạch khỏi hệ thống (kill pod, mất mạng, mất ACK, làm chậm DB) và hỏi một business item cụ thể còn được tìm thấy ở đâu. Nếu câu trả lời là “trong Java heap của pod vừa chết”, thiết kế **không có durability** ở ranh giới đó.

## 25. Run identity, business window và idempotency key

Một job có thể được triggered nhiều lần vì retry, crash, operator bấm Run hoặc controller catch-up. Cần phân biệt:

- **Job definition ID:** loại công việc, ví dụ `daily-revenue-report`.
- **Business window ID:** kỳ dữ liệu cần xử lý, ví dụ `2026-10-29@Asia/Ho_Chi_Minh`.
- **Run ID:** một lần attempt/execution cụ thể, ví dụ một UUID.
- **Message ID:** danh tính của message được publish; một logical event nên giữ cùng stable ID qua retry.
- **Idempotency key:** định danh tác động nghiệp vụ chỉ được phép xảy ra một lần trong phạm vi được định nghĩa.
- **Owner/lease ID:** worker hiện có quyền claim; có thể thay khi failover.
- **Fencing token:** số phiên bản tăng khi owner mới được cấp quyền.

Không dùng **run ID** mới mỗi lần retry làm **idempotency key** nếu điều đó khiến cùng business command bị áp dụng lần thứ hai. Ví dụ charge cho order `ord-42` nên có key ổn định đại diện charge logical operation, dù attempt #1 và #2 có run IDs khác nhau.

### Unique constraint theo business window

~~~sql
-- PSEUDOCODE/DDL minh họa:
CREATE TABLE report_runs (
    report_type VARCHAR(80) NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL,
    PRIMARY KEY (report_type, business_date)
);
~~~

Nếu có cùng một báo cáo ngày đó từ hai schedulers, database có thể từ chối insert trùng. Nhưng unique row chỉ chống tạo **hai business-run records**, không tự chống **hai workers thực thi song song** nếu cả hai đọc cùng record và không có atomic claim. Muốn owner duy nhất, thêm state transition/lease/fencing và test transaction isolation.

### Fencing khi ghi kết quả

Một worker token 41 đã hết lease có thể vẫn hoàn tất HTTP call sau worker token 42. Nếu storage downstream không kiểm tra token mới nhất, bạn có thể ghi kết quả lỗi thời đè lên bản mới. Thiết kế write condition như “chỉ cập nhật nếu fencing token vẫn bằng token hiện tại” hoặc dùng provider idempotency/version control khi được hỗ trợ.

## 26. Thứ tự thông điệp, partition và scale

“Có queue” không bảo đảm toàn cục FIFO theo mọi consumer và mọi kiểu lỗi. Thứ tự phụ thuộc:

- Partitioning key, số partitions/queues.
- Số consumer/worker.
- Prefetch và batching.
- Retry/requeue/dead-letter.
- Việc một item chạy chậm trong khi item sau hoàn thành trước.
- Cách consumer commit/ack offsets.

Nếu cần thứ tự **theo khách hàng**, có thể partition theo `customerId` rồi xử lý tuần tự trong partition/key, kết hợp với sequence/version check. Nếu cần thứ tự tuyệt đối toàn hệ thống, throughput có thể giảm mạnh và kiến trúc phải cân nhắc. Idempotency **không đồng nghĩa** ordering.

Workflow engine có thể giữ thứ tự các activities trong một workflow execution nhưng nhiều workflows độc lập không mặc nhiên có thứ tự toàn cục. Distributed scheduler cũng không giải quyết ordering của mọi item sau khi gửi vào broker.

## 27. Timeout, retry và deadline theo từng tầng

Một lỗi thiết kế thường gặp là dùng đúng **một timeout** cho toàn hệ thống. Hãy phân biệt:

| Timeout / policy | Ý nghĩa |
| --- | --- |
| HTTP request timeout | Client có chờ response nữa không? |
| Enqueue/publish timeout | Producer có biết broker nhận việc không? |
| Broker visibility/ack timeout | Message đã claim bao lâu trước redelivery? |
| Worker execution timeout | Một lần attempt được phép chạy bao lâu? |
| Activity timeout | Một bước workflow có deadline nào? |
| Workflow overall deadline | Toàn quy trình được phép sống bao lâu? |
| Lease TTL + renewal | Owner được giữ claim đến lúc nào? |
| Retry backoff | Khi nào thử lại sau lỗi? |
| Cron misfire grace | Lượt lịch trễ bao lâu vẫn đáng tạo? |
| Business SLA | Khi nào cần escalation/reconciliation? |

Ví dụ payment API timeout tại 5 giây **không chứng minh provider chưa charge**. Retry phải dùng cùng idempotency key hoặc truy vấn lại operation status. Worker mất lease trong lúc API chậm cũng không tự hủy request cũ; cần giới hạn side effect ở nơi chấp nhận command.

### Retry budget

Nếu broker redelivery 5 lần, workflow activity retry 4 lần, HTTP client retry 3 lần và provider tự retry nữa, tổng số network attempts có thể tăng nhanh ngoài dự kiến. Thiết kế **retry ownership** theo một hoặc hai tầng rõ, có deadline tổng, error classification và jitter/bulkhead để tránh retry storm.

## 28. Observability: metric khác nhau theo tầng

| Tầng | Số đo cần quan sát | Tại sao |
| --- | --- | --- |
| Local executor | Active threads, queue depth, rejection, task latency | Tắc nghẽn bên trong JVM |
| Scheduler | Scheduled fire, actual start, misfire/skip, active registrations | Lịch có hoạt động như mong đợi? |
| Broker | Pending depth, age of oldest, consumer lag, redelivery, DLQ | Việc được nhận và giao đi không? |
| Worker | Claimed, in-flight, success/failure, attempts, duration | Công việc thực thi có thành công? |
| Workflow | Running/stuck executions, failed activities, waiting timers/signals | Quy trình đang ở bước nào? |
| DB/job store | PENDING/CLAIMED/FAILED/SUCCEEDED, lease expiry | Nguồn sự thật của job state |
| External API | Idempotency hit, ambiguous timeout, reconciliation count | Có trùng side effect không? |
| Business | Last successful business date, missed windows, SLA breach | Mục tiêu thực tế đã đạt chưa? |

**Đừng chỉ dựa vào scheduler heartbeat.** Một `@Scheduled` có thể đều đặn trigger và dispatch message, nhưng broker luôn reject; một broker có thể luôn giao messages nhưng consumers fail; workflow có thể luôn alive nhưng treo signal chờ quá hạn.

Gắn `correlationId`, `jobId`, `businessWindow`, `attempt` và `nodeId` vào log/tracing. Tránh metric label theo UUID không giới hạn gây cardinality quá lớn. Khi xử lý cross-process, truyền tracing context bằng envelope/protocol tương ứng; không kỳ vọng MDC Java ThreadLocal tự vượt mạng.

## 29. Kiểm thử đúng cấp độ: không chỉ unit test một Runnable

### Local unit test

- Verify pool executor chạy task và Future báo lỗi.
- Verify `@Async` được gọi qua proxy khi dùng Spring integration test, không chỉ new class.
- Verify cancellation/cooperative interruption và shutdown.
- Verify queue full/rejection khác lỗi từ task body.

### Broker integration test

- Publish khi worker chưa chạy; message còn không?
- Worker chết trước ack; có redelivery không?
- Worker thành công nhưng ack response mất; duplicate thế nào?
- Đưa malformed message vào DLQ theo policy, không tạo poison-message loop.
- Consumer scale từ 1 lên N, ordering và idempotency còn đúng?
- Producer publish thành công nhưng API timeout, retry có tạo message ID mới ngoài ý muốn không?
- Broker restart/quorum failover có khôi phục pending messages theo cấu hình?

### Workflow integration test

- Worker crash giữa activity 3 và 4; history replay đến đâu?
- External signal tới trước/sau worker restart; có được match đúng execution không?
- Activity đã side effect nhưng chưa ghi completion, có duplicate không?
- Timer/deadline qua downtime và timezone xử lý ra sao?
- Thay version workflow code sau khi history cũ tồn tại; deterministic replay/migration thế nào?
- Compensation thất bại rồi retry; trạng thái nào là MANUAL_REVIEW?

### Distributed scheduler test

- Hai nodes cùng cố acquire một trigger/run; có duplicate run không?
- Scheduler leader chết ngay trước fire; có missfire/recovery đúng yêu cầu không?
- Hai Quartz instances dùng đúng một JDBC JobStore và instance IDs khác nhau?
- `CronJob concurrencyPolicy=Forbid` skip khi job cũ dài; business muốn skip hay catch-up?
- Lease owner bị pause vượt TTL, fencing token có chặn stale owner không?
- Deploy 3 replicas và scale down/up; job state có ổn định không?

### End-to-end business tests

- Test mỗi business key chỉ có **một side effect**, kể cả khi delivery lặp.
- Test provider timeout **sau khi** đã xử lý operation.
- Test một phần batch commit thành công rồi crash, restart xử lý đúng phần còn lại.
- Test SLA và trạng thái dashboard/alerts khi tất cả workers đều fail.

Không dùng `Thread.sleep` ngẫu nhiên để chứng minh tính phân tán; cần fault injection, latch/timeouts cho local, testcontainers hoặc cluster môi trường kiểm thử và quan sát **persistent state**.

## 30. Các lỗi phổ biến cần tránh

1. **Tin rằng `@Async` tạo job bền vững.** Nó chỉ dispatch task tới executor theo cấu hình.
2. **Tin rằng `CompletableFuture` là workflow history.** Future nằm trong tiến trình và không tự phục hồi sau crash.
3. **Tin rằng `@Scheduled` chỉ chạy một lần trên toàn Kubernetes deployment.** Mỗi context có thể đăng ký lịch riêng.
4. **Đổi `ThreadPoolTaskExecutor` thành pool 100 threads để giải quyết distributed reliability.** Tăng threads không tạo durability.
5. **Đặt queueCapacity rất lớn rồi gọi nó là message queue.** RAM queue không có broker ack/redelivery bền vững.
6. **Gọi `convertAndSend` rồi trả thành công khi chưa kiểm chứng publisher/broker confirmation** theo hợp đồng producer.
7. **Ack message trước external side effect** dù nghiệp vụ không cho phép mất.
8. **Ack sau side effect nhưng không chống duplicate**, khiến crash-before-ack tạo double charge.
9. **Cho rằng RabbitMQ/Kafka có một bộ mặc định giống nhau về ack, ordering, DLQ.** Cần đọc đúng semantics và cấu hình.
10. **Bật retry nhiều tầng mà không có budget**, gây request storm.
11. **Dùng message ID ngẫu nhiên mỗi lần retry business command**, phá tính ổn định của idempotency key.
12. **Dùng unique run row rồi nghĩ không thể có hai workers chạy song song**, trong khi claim không atomic.
13. **Dùng lock TTL mà không xét GC pause/clock skew**, gây owner chồng lấn.
14. **Không dùng fencing/idempotency khi stale worker hồi phục**, ghi đè state mới.
15. **Tưởng Quartz trên classpath tự clustered.** Cần JDBC JobStore và config cluster cụ thể.
16. **Tưởng Quartz cluster đảm bảo exactly-once email/payment.** Trigger acquisition khác side effect.
17. **Tưởng Kubernetes CronJob tạo đúng một Job cho mọi tick trong mọi sự cố.** Docs nêu có thể tạo không hoặc nhiều.
18. **Cho rằng `concurrencyPolicy=Forbid` đủ để chống duplicate toàn hệ thống** khi có nhiều CronJob objects và retries.
19. **Trộn cron Spring sáu trường với CronJob Kubernetes năm trường.** Schedule có thể sai.
20. **Bật khởi tạo schema Quartz với lệnh drop tables trong production**, mất triggers lịch sử.
21. **Cho rằng Spring Batch JobRepository tự phân tán toàn bộ job scheduler.** Nó quản lý metadata/execution của batch, không thay global trigger.
22. **Dùng resourceless JobRepository rồi kỳ vọng restart từ state đã lưu**.
23. **Dùng queue cho quy trình có human approval nhiều ngày nhưng không lưu state machine**, khó khôi phục.
24. **Dùng workflow engine rồi nghĩ activities không cần idempotency**; activity có thể được retry.
25. **Gọi API ngoài trực tiếp trong workflow replay path trái contract**, gây nondeterminism/duplicate.
26. **Gộp businessWindow và runId làm một**, khiến retry tạo hai tác động cho cùng kỳ.
27. **Dùng ThreadLocal/MDC giữa JVMs**, thiếu correlation ở envelope.
28. **Không quản lý retryable vs permanent errors**, poison messages quay vòng vô hạn.
29. **Không có DLQ/manual remediation**, công việc lỗi bị bỏ quên.
30. **Tính thành công từ ACK message** dù database transaction hoặc external action chưa đạt outcome.
31. **Tính thành công từ log 'scheduled triggered'** dù worker fail.
32. **Tin rằng mất ACK nghĩa business side effect chưa xảy ra**, retry không kiểm tra lại.
33. **Không có business reconciliation**, không phát hiện missing hoặc duplicate operations.
34. **Khởi tạo hàng đợi in-memory rồi viết “durable” trong tên class**, đánh lừa mức bảo đảm.
35. **Không kiểm tra shutdown của worker**, task bị cắt giữa transaction và completion.
36. **Không giám sát oldest pending age**, backlog có thể tăng dù throughput metrics cao.
37. **Cho rằng at-least-once nghĩa exactly-once**, bỏ qua idempotency.
38. **Cho rằng một distributed scheduler cũng là distributed queue**, giao business job trực tiếp cho trigger thread mà không có backpressure.
39. **Dùng global lock cho mỗi-pod cache refresh**, chỉ một cache cập nhật còn những pod khác stale.
40. **Chọn workflow engine quá sớm cho tác vụ vài mili-giây và có thể làm lại**, tăng complexity không cần thiết.

## 31. Checklist lựa chọn trước khi triển khai

Trước khi đưa job vào production, trả lời ít nhất những câu hỏi sau:

1. **Work scope:** việc thuộc một HTTP request, một JVM, một tenant, một business window hay toàn cluster?
2. **Durability:** nếu process chết ngay bây giờ, một unfinished job được tìm lại ở đâu?
3. **Trigger:** theo event, theo giờ, theo manual command hay theo bước trước?
4. **Delivery:** at-most-once hay at-least-once? Nếu duplicate, hệ thống làm gì?
5. **Idempotency:** key ổn định nằm ở đâu và được kiểm tra atomically tại effect boundary không?
6. **Ack:** khi nào producer/consumer được phép tuyên bố thành công?
7. **Transaction:** DB và external message có dual-write gap không? Có cần outbox?
8. **Concurrency:** số workers, queue depth, prefetch, per-key serialization và downstream quotas?
9. **Ownership:** nếu nhiều replicas, ai được claim job và chống stale owner thế nào?
10. **Retries:** transient/permanent errors, backoff, max attempts và DLQ?
11. **Deadline/misfire:** nếu job tới muộn hoặc missed window, chạy bù hay skip?
12. **Long-running state:** có timers/signals/compensations cần durable workflow không?
13. **Observability:** status/attempts/last-success/backlog được lưu và hiển thị ở đâu?
14. **Deployment:** đang chạy khi rollout thì cancel/drain/recover thế nào?
15. **Testing:** đã mô phỏng crash sau side effect trước ack chưa? Hai owners cùng xử lý chưa?
16. **Operations:** ai có quyền pause/resume/retry/manual reconciliation, có audit trail không?
17. **Security:** payload, authorization, trace và secrets được truyền an toàn qua queue/workflow không?
18. **Cost/complexity:** có thể đạt yêu cầu bằng DB job table + poller đơn giản thay vì dựng thêm hệ thống không?

### Một nguyên tắc kiến trúc dễ nhớ

- Dùng **local executor** khi chỉ cần *thực thi song song trong process*.
- Dùng **durable queue** khi cần *nhận việc và phân tải giữa processes với khả năng giao lại*.
- Dùng **workflow** khi cần *giữ trạng thái của một quy trình nhiều bước, chờ đợi và phục hồi qua downtime*.
- Dùng **distributed scheduler** khi cần *phối hợp thời điểm kích hoạt giữa nhiều nodes, có thể xử lý misfire và persistent triggers*.
- Dùng **transactional outbox/idempotency** để bảo vệ *biên giới giữa những hệ thống có thể crash hoặc gửi trùng*.

## 32. Câu hỏi tự kiểm tra

1. `@Async` với `ThreadPoolTaskExecutor` có thể chuyển task từ pod A sang pod B khi A chết không? Vì sao?
2. Một `@Scheduled` method được deploy trên bốn replicas thường có bao nhiêu independent registrations?
3. Sự khác biệt giữa **“tick chạy một lần toàn cụm”** và **“external side effect xảy ra đúng một lần”** là gì?
4. Khi nào bạn nên dùng queue thay vì chỉ tăng `queueCapacity` trong ThreadPoolTaskExecutor?
5. ACK trước xử lý và ACK sau xử lý tạo hai loại failure window khác nhau thế nào?
6. Vì sao at-least-once delivery luôn buộc ứng dụng xét duplicate?
7. “Exactly once” cần nêu phạm vi nào trước khi chấp nhận một claim về broker?
8. Transactional outbox giải quyết trường hợp crash ở đâu trong flow DB -> broker?
9. Vì sao outbox không tự loại duplicate từ consumer?
10. Khi consumer gọi payment API xong nhưng mất response, idempotency key nên được chọn thế nào?
11. Một queue có tự quản lý “đã reserve stock, đang đợi approval, cần refund nếu timeout” không?
12. Durable workflow history và Java `CompletableFuture` chain khác nhau ở đâu?
13. Vì sao activity trong workflow vẫn cần idempotency?
14. Quartz cluster cần store loại nào để phối hợp nodes? Chỉ đặt `spring.quartz.job-store-type=jdbc` đã đủ cluster chưa?
15. Kubernetes `concurrencyPolicy=Forbid` áp dụng trên phạm vi nào? Nó có thể skip mốc lịch không?
16. Một CronJob có bảo đảm đúng một Kubernetes Job cho mọi scheduled time không?
17. Spring Batch `JobRepository` giải quyết phần nào và không tự giải quyết phần nào?
18. Distributed lease hết hạn khi worker A đang GC pause dài tạo rủi ro gì?
19. Fencing token khác idempotency key ra sao?
20. Vì sao một unique `report_runs(report_type, business_date)` chưa chắc ngăn hai workers xử lý cùng row?
21. Khi nào local `@Scheduled` trên mọi pod là lựa chọn đúng thay vì bug?
22. Nếu cần chạy đúng một báo cáo cho mỗi business date sau downtime, cần lưu gì?
23. Test nào chứng minh không mất việc sau worker crash-before-ack?
24. Nếu một job bị retry ở broker và HTTP client, ai sở hữu retry budget?
25. Làm sao phân biệt scheduler alive với business processing healthy?
26. Nếu một workflow code thay đổi khi execution cũ đang sống, phải quan tâm tới tính chất nào khi replay?
27. Tại sao không nên gọi `Future.isDone()` là “business success”?
28. Phương án tối thiểu nào đủ cho một poller queue có thể làm lại mà không cần workflow engine?

## 33. Tài liệu chính thức và đọc thêm

- [Spring Framework Reference — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework — @Async và AsyncTaskExecutor](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async)
- [Spring Framework — @Scheduled và TaskScheduler](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-scheduled)
- [Spring Boot Reference — Quartz Scheduler](https://docs.spring.io/spring-boot/reference/io/quartz.html)
- [Quartz Scheduler — Configuration: JDBC JobStore Clustering](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/configuration/ConfigJDBCJobStoreClustering.html)
- [Quartz Scheduler — JDBC JobStore Configuration](https://www.quartz-scheduler.org/documentation/quartz-2.5.x/configuration/ConfigJobStoreTX.html)
- [Kubernetes Documentation — CronJob](https://kubernetes.io/docs/concepts/workloads/controllers/cron-jobs/)
- [Spring AMQP Reference — Asynchronous Consumer](https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-consumer.html)
- [Spring AMQP — @RabbitListener Return Types, Ack and Failure](https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-returns.html)
- [Spring Batch — Domain Language and JobRepository](https://docs.spring.io/spring-batch/reference/domain.html)
- [Spring Batch — Configuring a JobRepository](https://docs.spring.io/spring-batch/reference/job/configuring-repository.html)
- [Temporal Documentation — Workflows and Durable Execution](https://docs.temporal.io/workflows)
- [Temporal Documentation — Event History](https://docs.temporal.io/encyclopedia/event-history)
- [Temporal Documentation — Workflow Tasks](https://docs.temporal.io/tasks)
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Java SE 17 — ScheduledExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html)

**Ghi nhớ:** **Spring TaskExecutor/TaskScheduler giải quyết concurrency và thời điểm thực thi trong một ApplicationContext**, không tự tạo dịch vụ phân tán. **Hàng đợi** giữ và giao việc giữa workers nhưng cần ack/retry/idempotency. **Workflow** giữ lịch sử/quy trình nhiều bước và phục hồi qua crash nhưng activities vẫn có thể được retry. **Lịch phân tán** phối hợp fire times giữa nodes nhưng không đồng nghĩa external side effect exactly-once. Khi yêu cầu không mất việc hoặc không trùng effect, phải thiết kế **persistent state, ownership, transaction boundaries, idempotency và reconciliation** theo từng ranh giới; đừng dùng một annotation thay cho hợp đồng hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="distributed-limits-008">Ghi rõ kỳ vọng về chạy trùng, bỏ lỡ và thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi rõ kỳ vọng về chạy trùng, bỏ lỡ và thứ tự

> **Chủ đề:** Spring Framework – concurrency  
> **Nhóm kiến thức:** Giới hạn phân tán  
> **Trọng tâm:** mô tả một **hợp đồng thực thi có thể kiểm chứng** cho hệ thống nhiều JVM/pod: *một việc có thể chạy trùng không? một kỳ có thể bị bỏ lỡ không? các việc có cần giữ thứ tự không?*  
> **Nguyên tắc:** `@Scheduled`, `@Async`, `TaskScheduler` và executor trong một JVM không tự bảo đảm **exactly-once**, không mất việc qua crash hay thứ tự xử lý **toàn cụm**. Cam kết đúng phải gắn với **đơn vị việc nghiệp vụ**, phạm vi áp dụng, cơ chế lưu trạng thái, cách xử lý lỗi và phép kiểm thử.

Bài sử dụng **Java 17**, **Spring Framework 6.2.x**, ví dụ PostgreSQL để minh họa durable job store, và Kafka như một ví dụ có thứ tự theo partition. Bản Java 17 chạy độc lập và bản Spring Context Maven đều có input/output xác định. Một demo in-memory chỉ chứng minh logic của *một tiến trình*; khi triển khai nhiều pod, phải dùng state store/coordination chung.

## 1. Mục tiêu học tập

Sau bài này, bạn sẽ:

1. Phân biệt **trigger**, **invocation/attempt**, **logical job**, **business effect** và **acknowledgement**.
2. Ghi rõ duplicate policy: *at-most-once*, *at-least-once*, *effectively-once* và phạm vi giới hạn của “exactly-once”.
3. Ghi rõ missed-work policy: *skip*, *coalesce latest*, *retry/backfill mọi kỳ*, và giới hạn deadline/retention.
4. Ghi rõ ordering policy: **không bảo đảm**, theo **mỗi key/partition**, theo **version**, hoặc **total order** với chi phí cụ thể.
5. Biết vì sao cùng `@Scheduled` ở nhiều replica có thể chạy nhiều lần và vì sao local `synchronized`, `AtomicBoolean` không giải quyết toàn cụm.
6. Thiết kế `jobKey` ổn định, unique constraint, transaction, inbox/outbox, claim/lease/fencing và reconciliation.
7. Biết lúc nào queue/event broker bảo đảm delivery nhưng không bảo đảm **side effect bên ngoài** chính xác một lần.
8. Tách thứ tự *gửi*, *nhận*, *bắt đầu*, *commit*, *hiển thị*; chọn loại thứ tự nghiệp vụ thực sự cần.
9. Xác định các cửa sổ lỗi crash, timeout, leader change, retry, pod restart, clock skew và cách phục hồi.
10. Viết contract, metrics/SLO và test matrix để team có thể đánh giá yêu cầu thay vì dựa vào tên API.

## 2. Ba câu hỏi phải trả lời trước khi viết code

Một tính năng “chạy báo cáo hằng ngày” nghe đơn giản nhưng cần hợp đồng rõ ràng:

**A. Chạy trùng:** Nếu hai pod đều thấy 09:00, có được phép cùng tạo báo cáo? Nếu network timeout sau khi báo cáo đã được lưu, lần retry có được tạo bản thứ hai không?

**B. Bỏ lỡ:** Nếu không có pod nào chạy lúc 09:00 vì deploy hoặc mất điện, hệ thống có cần tạo báo cáo đó khi phục hồi? Nếu 10 ngày offline, xử lý đủ 10 kỳ hay chỉ tính kỳ gần nhất?

**C. Thứ tự:** Nếu kỳ 03 chạy xong trước kỳ 02, dữ liệu có sai không? Nếu trạng thái `SHIPPED` tới trước `PAID`, phải đợi, từ chối hay xử lý theo version mới nhất?

Không có câu trả lời mặc định đúng cho tất cả bài toán. **Cache refresh** thường chịu được skip tick và cho phép coalesce; **billing** thường phải đủ từng kỳ và không được tạo side effect trùng; **event sourcing** có thể cần thứ tự chặt trong từng aggregate.

### 2.1. Định nghĩa đơn vị công việc

Một **trigger firing** là sự kiện scheduler quyết định gọi method. Một **attempt** là một lượt thử xử lý có ID riêng. Một **logical job** là công việc nghiệp vụ duy nhất, có khóa bền như:

~~~text
jobKey = (tenantId, jobType, businessPeriod)
example = (tenant-7, generate-invoice, 2027-02)
~~~

Nếu cùng job được retry ba lần, đó là **ba attempts của một jobKey**, không phải ba hóa đơn khác nhau.

`jobId` ngẫu nhiên mỗi lần submit **không** đủ làm idempotency key; hai pod có thể tạo hai UUID khác nhau cho cùng hóa đơn. Khóa phải đến từ identity nghiệp vụ đã chuẩn hóa.

### 2.2. Bốn mốc trạng thái

~~~text
PLANNED -> CLAIMED -> EFFECT_COMMITTED -> ACKNOWLEDGED
    \        |               |                 /
     \       +-- crash window +-- retry may occur
      +-- skipped/outage -> reconciliation
~~~

- **PLANNED:** một logical period/event được xác định.
- **CLAIMED:** worker có quyền *thử* xử lý theo token/lease.
- **EFFECT_COMMITTED:** dữ liệu hoặc side effect thật đã được commit.
- **ACKNOWLEDGED:** client/broker/scheduler nhận được xác nhận.

Không nên coi `ACKNOWLEDGED` là cùng một transaction với external payment/email API nếu không có cơ chế phối hợp. Crash **sau commit nhưng trước ack** là nguồn duplicate phổ biến.

## 3. Viết contract dưới dạng bảng, không bằng lời mơ hồ

Một mẫu có thể mang vào RFC, issue hoặc runbook:

| Trường hợp | Câu trả lời phải ghi |
| --- | --- |
| Logical key | `tenant + jobType + businessPeriod` |
| Trigger | `@Scheduled(cron=...)` chỉ đánh thức scanner |
| Duplicate attempts | **Được phép**, đặc biệt khi retry/failover |
| Duplicate business effects | **Không được phép** theo cùng `jobKey` |
| Missed periods | **Phải backfill đủ** trong retention 90 ngày |
| Ordering scope | **Theo tenant + chuỗi kỳ**, không yêu cầu giữa tenants |
| Max lag | 15 phút sau thời điểm dự kiến trong điều kiện bình thường |
| Replay | Quét các kỳ chưa `SUCCEEDED` và claim lại có kiểm soát |
| Deadline/expiry | Hết 90 ngày thì báo manual recovery, không âm thầm bỏ |
| Failure after external call | Dùng provider idempotency key và reconcile |
| Multi-instance owner | Durable DB claim + fencing token |
| Observability | metrics missing/duplicate/out-of-order + job audit log |
| Test | concurrent pods, crash windows, delayed/reordered messages |

Từ “**không trùng**” phải nói rõ **cái gì không trùng**: số lần method được gọi, số lần DB `INSERT`, hay số tiền bị trừ? Một hệ thống có thể gọi method 5 lần nhưng chỉ commit một business effect — đó thường là giải pháp đúng hơn việc cố chứng minh không bao giờ có retry.

## 4. Delivery semantics: các thuật ngữ cần dùng chính xác

| Mô hình | Duplicate delivery/attempt | Có thể mất việc? | Ghi chú |
| --- | --- | --- | --- |
| **At-most-once** | Hạn chế phát lại | **Có thể** | Thường chấp nhận mất khi crash trước commit |
| **At-least-once** | **Có thể** giao lại | Cố gắng không mất trong phạm vi durable store/retention | Consumer phải idempotent |
| **Effectively-once** | Có thể retry nhiều lần | Phụ thuộc durable workflow | Side effect nghiệp vụ được dedup/commit một lần theo key |
| **Exactly-once trong phạm vi hẹp** | Được định nghĩa theo transaction/broker boundary | Phụ thuộc hạ tầng | Không tự mở rộng sang mọi DB/API/email |
| **Best-effort periodic trigger** | Có thể nhiều pod cùng fire | Có thể skip khi down | Mặc định cần kỳ vọng khi dùng Spring `@Scheduled` |

### 4.1. Exactly-once là kết quả, không phải tên annotation

`@Scheduled`, `@Async`, `@Transactional`, `synchronized` hay một distributed lock đơn lẻ **không** biến mọi HTTP/DB side effect thành exactly-once end-to-end.

Một lần thanh toán có thể được provider xử lý nhưng client timeout trước khi nhận response. Retry tạo request thứ hai nếu không có idempotency key. Dù Java Future báo cancelled, provider vẫn có thể đã commit. “Exactly-once” cần một **ranh giới** rõ, ví dụ *một row duy nhất theo primary key trong PostgreSQL*; không có nghĩa “mỗi dòng code chỉ chạy đúng một lần”.

### 4.2. Chạy trùng ở đâu?

- **Trigger duplicate:** nhiều pod cùng chạy scanner lúc 09:00.
- **Submission duplicate:** cùng logical job được enqueue hai lần vì timeout.
- **Processing duplicate:** consumer retry sau crash/rebalance.
- **Side-effect duplicate:** hai lần ghi sổ/trừ tiền/gửi email.
- **Notification duplicate:** người dùng nhận nhiều push dù database state chỉ có một bản ghi.

Đừng chỉ đếm `@Scheduled invocation` rồi suy ra hóa đơn bị trùng. Dùng unique business key và đo **effect commits**.

### 4.3. Tại sao at-least-once + idempotency thường thực tế?

Nếu một worker có thể chết giữa chừng, muốn “không mất” thì thường phải **thử lại**. Thử lại đồng nghĩa việc cũ có thể đã được thực hiện mà ack chưa kịp ghi. Vì vậy, cho phép duplicate attempts nhưng **ngăn duplicate effects** bằng transaction/unique constraint/provider key là thiết kế có thể kiểm chứng.

At-least-once vẫn phụ thuộc dữ liệu đầu vào thật sự được lưu bền và retention chưa hết. Không được dùng nó để cam kết “sẽ không mất kể cả khi toàn bộ broker/disk bị mất”.

## 5. Spring @Scheduled: phạm vi là JVM/bean, không phải cluster

Một method:

~~~java
@Component
public class BillingTrigger {

    private final BillingReconciler reconciler;

    public BillingTrigger(BillingReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Scheduled(cron = "0 0 9 * * *",
               zone = "Asia/Ho_Chi_Minh")
    public void tick() {
        reconciler.findAndEnqueueMissingPeriods();
    }
}
~~~

Đây là **Spring application snippet**, cần `@EnableScheduling`, imports `@Component`/`@Scheduled` và service `BillingReconciler` do ứng dụng định nghĩa.

Với **ba replicas**, thường có ba Spring application contexts và ba registrations. Mỗi pod có thể gọi `tick()`. Cùng `zone` chỉ làm chúng tính mốc thời gian cùng quy tắc, **không** chọn một pod làm owner.

### 5.1. Local lock không phải distributed lock

~~~java
private final AtomicBoolean running = new AtomicBoolean();

public void tick() {
    if (!running.compareAndSet(false, true)) {
        return;
    }
    try {
        reconcile();
    } finally {
        running.set(false);
    }
}
~~~

Snippet này chỉ tránh hai lời gọi đồng thời **trong một bean instance/JVM**. Pod A và B mỗi pod có `running=false` riêng, nên cả hai có thể cùng vào. Nếu nhiệm vụ có side effect, một local guard vẫn nên đi kèm durable business key hoặc distributed claim.

### 5.2. Một method có nhiều registration

Spring `@Scheduled` là repeatable annotation. Hai declarations trên cùng method tạo **hai schedules độc lập**, có thể overlap; tạo hai bean instances cũng có thể có nhiều registrations. Khi debug duplicate, kiểm tra cả annotation, bean scope, deployment replicas và mọi paths submit job chứ không chỉ số pod.

### 5.3. fixedRate và fixedDelay không xử lý cluster duplication

Một periodic task trên `ScheduledThreadPoolExecutor` thông thường không overlap **với chính nó trong cùng registration**. Nhưng đây là phạm vi *một periodic series*, không phải toàn bộ cluster. Hai independent schedules hoặc hai pods vẫn có thể cùng chạy.

### 5.4. Đổi deployment count không được đổi correctness

Nếu tính năng đúng khi chỉ có một pod nhưng sai khi scale lên năm pod, hệ thống đang dựa vào scheduling local cho một invariant global. Hãy biến invariant thành dữ liệu được áp đặt bởi shared DB/queue/coordination.

## 6. Chọn chính sách chạy trùng theo công việc

### 6.1. Cache refresh

- Duplicate attempts: chấp nhận được.
- Duplicate effects: thường không sao nếu update cùng state, nhưng cần version để ngăn **stale overwrite**.
- Missed tick: có thể bỏ qua nhiều tick và làm một lần refresh mới nhất.
- Ordering: không cần mọi tick theo thứ tự, nhưng update version cũ không được ghi đè version mới.

### 6.2. Hóa đơn hằng tháng

- Duplicate attempts: cho phép để phục hồi lỗi.
- Duplicate invoice issuance: **không**; unique key `tenant + billingMonth`.
- Missed month: không được âm thầm bỏ, phải backfill/reconcile.
- Ordering: có thể yêu cầu tháng trước được đóng trước tháng sau **trong cùng tenant**; tenants khác chạy song song.
- Side effects với payment provider: dùng provider idempotency key, đối soát unknown outcomes.

### 6.3. Email xác nhận

- Duplicate attempts: có thể do retry.
- Duplicate user-visible emails: nên tránh bằng email intent ID và provider idempotency/delivery tracking nếu có; không phải mọi email provider hỗ trợ exactly-once.
- Missed confirmation: retry trong một SLA, dead-letter khi hết hạn.
- Ordering: chỉ quan trọng với một conversation/thread hoặc trạng thái account nhất định.

### 6.4. Event cập nhật tồn kho

- Duplicate event delivery: chấp nhận ở transport layer.
- Duplicate stock adjustment: **không**, phải dedup theo event ID.
- Missing event: không tự bỏ, phát hiện gap và replay/snapshot.
- Ordering: có thể yêu cầu theo `sku/location` hoặc aggregate, không cần total order toàn hệ thống.

## 7. Bỏ lỡ một tick khác bỏ lỡ một công việc nghiệp vụ

Giả sử job cron chạy mỗi phút, app downtime 8 phút. Với scheduler in-memory, có thể không có 8 invocations tương ứng sau restart. **Điều này không nhất thiết khiến dữ liệu thiếu**, nếu scanner kế tiếp tìm được toàn bộ business records chưa xử lý.

~~~text
09:00 tick -> scan pending rows
09:01 pod down
09:02..09:08 no tick
09:09 pod up -> scan all due and not completed rows
                 |
                 +-- 09:02 task: claim/recover
                 +-- 09:03 task: claim/recover
                 +-- ...
~~~

**Đơn vị đúng để backfill là business period hoặc durable event**, không phải counter số lần `@Scheduled` đã gọi.

### 7.1. Các kiểu missed-work policy

| Policy | Nghĩa | Dùng khi |
| --- | --- | --- |
| **Skip** | Bỏ những kỳ trễ | Heartbeat/sampling được phép mất mẫu |
| **Coalesce latest** | Gộp nhiều mốc thành một lần cập nhật mới nhất | Cache/materialized view có thể đọc lại từ source of truth |
| **Replay all** | Xử lý từng business key bị thiếu | Billing, ledger, mỗi cửa sổ thống kê bắt buộc |
| **Replay within retention** | Bù trong khoảng cho phép; quá hạn cần thao tác riêng | Dữ liệu có TTL, event log hữu hạn |
| **Compensate/manual** | Không có thể replay an toàn; tạo ticket/đối soát | Thanh toán/email external ambiguity |
| **Fail closed** | Không xử lý kỳ mới nếu kỳ trước thiếu | Chuỗi sổ cái bắt buộc liên tiếp |

### 7.2. Cần định nghĩa “bỏ lỡ” tại tầng nào

- Không nhận **cron trigger** đúng giờ?
- Task được enqueue nhưng **chưa bắt đầu**?
- Task bắt đầu rồi **thất bại**?
- Task commit DB xong nhưng **ack mất**?
- Task đã complete ở worker nhưng **downstream chưa xác nhận**?

Những lỗi này có hướng xử lý khác nhau. “Missed” nên được định nghĩa bằng **expected business keys minus completed business keys**, có xét trạng thái running/lease/retry, thay vì chỉ dựa vào log scheduler.

### 7.3. Năm/tháng và múi giờ nghiệp vụ

~~~text
jobKey = tenant-7 / monthly-billing / 2027-02
scheduleZone = Asia/Ho_Chi_Minh
dueAt = 2027-03-01T02:00:00Z  (09:00 HCM 01/03)
~~~

Đây là ví dụ policy billing **ngày đầu tháng kế tiếp**. Nếu task tháng 02 chạy trễ sang tháng 04, key vẫn là `2027-02`. Không đổi thành tháng hiện tại chỉ vì `LocalDate.now()`. Lưu `YearMonth`, `ZoneId` và version lịch để không mất/trùng kỳ qua thay đổi timezone.

### 7.4. Khi coalesce phải nói rõ mất thông tin nào

Nếu cứ một phút refresh cache, bỏ 8 tick rồi refresh một lần vẫn có thể đúng vì trạng thái hiện tại chứa toàn bộ nguồn cần dùng. Với 8 giao dịch thanh toán độc lập, coalesce thành một giao dịch là sai. Phải phân biệt “poll frequency” với “event identity”.

## 8. Thứ tự: phải ghi phạm vi và điểm quan sát

Một lời hứa “sự kiện được xử lý đúng thứ tự” không có nghĩa nếu thiếu **key, mốc xác định và hành động cần tuần tự**.

Có ít nhất năm khái niệm:

1. **Order of creation:** business commands/events được tạo theo thứ tự nào?
2. **Order of publication:** messages được append vào log/queue theo thứ tự nào?
3. **Order of delivery:** consumer nhận lần lượt thế nào?
4. **Order of execution:** worker bắt đầu/kết thúc trước sau thế nào?
5. **Order of business commit:** state cuối được xác nhận theo thứ tự nào?

Một message có thể được **nhận trước** nhưng xử lý chậm, khiến message sau **commit trước** khi chạy nhiều workers. Vì thế, chỉ thấy queue trả message theo thứ tự không đủ kết luận business state được cập nhật theo thứ tự.

### 8.1. Các loại ordering contract

| Contract | Phạm vi | Giá phải trả |
| --- | --- | --- |
| **No order guarantee** | Toàn hệ thống | Throughput cao, consumer cần idempotency/commutativity |
| **Latest-version-wins** | Theo entity/key | Cần version/compare-and-set, có thể bỏ bản cũ |
| **Per-key strict order** | Ví dụ một account/order/tenant | Per-key serialization, gap handling, có head-of-line blocking |
| **Per-partition order** | Log partition | Các keys chia partition; không có global total order |
| **Causal order** | Theo quan hệ “happened-before”/phụ thuộc | Cần correlation/dependency version |
| **Global total order** | Mọi sự kiện trên cả hệ thống | Bottleneck/sequencer, giảm parallelism và thêm availability tradeoff |

### 8.2. Per-key thường hợp lý hơn global

Nếu account A và B độc lập, A có chuỗi `A#1,A#2`, B có `B#1,B#2`. Bạn có thể yêu cầu **A#1 trước A#2** và **B#1 trước B#2**, nhưng không cần chọn A#2 hay B#1 xảy ra trước. Cách này cho phép song song giữa keys trong khi giữ tính nhất quán từng aggregate.

### 8.3. Out-of-order không phải duplicate

~~~text
expected next for account-7 = sequence 3

received sequence 4 -> OUT_OF_ORDER / GAP (chưa áp dụng)
received sequence 3 -> APPLY 3, rồi APPLY 4
received sequence 3 again -> DUPLICATE / STALE
~~~

- `seq > expected`: thiếu ít nhất một sự kiện hoặc delivery reorder.
- `seq == expected`: có thể áp dụng nếu business invariant thỏa.
- `seq < expected`: đã xử lý/cũ; dedup hoặc phát hiện conflict nếu payload khác.
- Nếu cùng `(key,seq)` có hai payload khác nhau, đó là **vi phạm dữ liệu**, không được im lặng chọn tùy ý.

### 8.4. Total order không miễn phí

Một global sequencer cho mọi tài khoản làm giảm song song, có thể bị partition/network failure. Nếu yêu cầu chỉ có `per-account`, đừng thiết kế một lock toàn cluster chỉ để đảm bảo thứ tự không cần thiết.

## 9. Java 17 chạy được: duplicate, gap và per-key ordering

Chương trình sau có **in-memory processor**, kiểm chứng chính xác ba hành vi:

- Sự kiện `#2` tới trước `#1` thì bị **buffer**, không áp dụng sai thứ tự.
- Khi `#1` tới, processor xử lý **#1 rồi #2**.
- Event đã áp dụng tới lại bị coi là **duplicate/stale**, không tạo effect lần nữa.
- Tương tự `#4` tới trước `#3`, đợi rồi xử lý đúng thứ tự.

Đây là một **mô hình local để hiểu contract**, không phải distributed consumer hay durable inbox; pending buffer và next sequence đều nằm trong RAM.

Tạo file **`DeliveryContractDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DeliveryContractDemo {

    record Event(String key, int sequence,
                 String eventId, String payload) { }

    static final class Processor {
        private final Map<String, Integer> next = new HashMap<>();
        private final Map<String, TreeMap<Integer, Event>> pending =
                new HashMap<>();
        private final List<String> applied = new ArrayList<>();

        void receive(Event event) {
            int expected = next.getOrDefault(event.key(), 1);

            if (event.sequence() < expected) {
                System.out.println("duplicate/stale "
                        + event.key() + "#" + event.sequence());
                return;
            }

            TreeMap<Integer, Event> buffer =
                    pending.computeIfAbsent(
                            event.key(), ignored -> new TreeMap<>());

            Event old = buffer.putIfAbsent(
                    event.sequence(), event);

            if (old != null) {
                if (!old.equals(event)) {
                    throw new IllegalStateException(
                            "conflicting event for same sequence");
                }
                System.out.println("duplicate buffered "
                        + event.key() + "#" + event.sequence());
                return;
            }

            if (event.sequence() > expected) {
                System.out.println("gap " + event.key()
                        + " expected=" + expected
                        + " got=" + event.sequence());
            }

            while (true) {
                Event nextEvent = buffer.remove(expected);
                if (nextEvent == null) {
                    break;
                }

                String effect = nextEvent.key() + "#"
                        + nextEvent.sequence() + "="
                        + nextEvent.payload();

                applied.add(effect);
                System.out.println("apply " + effect);
                expected++;
            }
            next.put(event.key(), expected);
        }

        int effectCount() {
            return applied.size();
        }
    }

    public static void main(String[] args) {
        Processor p = new Processor();

        p.receive(new Event("account-7", 2, "evt-2", "paid"));
        p.receive(new Event("account-7", 1, "evt-1", "created"));
        p.receive(new Event("account-7", 1, "evt-1", "created"));
        p.receive(new Event("account-7", 4, "evt-4", "shipped"));
        p.receive(new Event("account-7", 3, "evt-3", "packed"));
        p.receive(new Event("account-7", 4, "evt-4", "shipped"));

        System.out.println("effects = " + p.effectCount());
    }
}
~~~

Biên dịch và chạy với Java 17+:

~~~bash
javac DeliveryContractDemo.java
java DeliveryContractDemo
~~~

**Stdout chính xác:**

~~~text
gap account-7 expected=1 got=2
apply account-7#1=created
apply account-7#2=paid
duplicate/stale account-7#1
gap account-7 expected=3 got=4
apply account-7#3=packed
apply account-7#4=shipped
duplicate/stale account-7#4
effects = 4
~~~

### 9.1. Đọc output đúng cách

- **Gap 1→2:** đến `#2` trước, chưa áp dụng state `paid` khi chưa có `created`.
- **Apply #1, #2:** khi `#1` đến, có thể drain buffer đúng thứ tự.
- **Duplicate #1:** consumer xử lý lại message cũ nhưng không tăng effect counter.
- **Gap 3→4:** `#4` bị đợi cho tới khi `#3` xuất hiện.
- **Effects = 4:** bốn business events độc nhất được áp dụng đúng sequence.

Không có thread hoặc `sleep` nên output deterministic. Ví dụ **không** kiểm tra bền vững qua crash, các consumers cạnh tranh hoặc ranh giới database commit; các chủ đề đó được xử lý ở phần schema/transaction phía sau.

### 9.2. Vì sao production không chỉ dùng TreeMap in-memory?

Nếu process chết lúc đã nhận `#4` nhưng chưa có `#3`, TreeMap sẽ mất `#4`. Nếu còn message trong durable broker thì có thể replay theo offset/retention; nếu không, phải lưu **pending event** vào DB/inbox. Nhiều consumers phải phối hợp theo key, và `nextSequence` phải được cập nhật cùng transaction với business state.

### 9.3. Event ID khác sequence

`eventId` dùng để dedup một event được phát lại. `sequence` dùng để xác định **thứ tự trong một aggregate/key**. Hai message có cùng `eventId` nhưng khác sequence hoặc cùng sequence nhưng khác payload cần bị đánh dấu conflict theo data contract. Trong demo, guard theo sequence đủ để minh họa thứ tự; production nên có cả unique `eventId` và version/hash validation.

## 10. Kiểm chứng riêng vấn đề missed business periods

Giả sử billing hằng ngày theo HCM; các kỳ cần có từ 01/01 đến 04/01, nhưng chỉ 01/01 và 03/01 đã `SUCCEEDED`.

~~~text
Expected business keys: 2027-01-01, 02, 03, 04
Succeeded keys:         2027-01-01,     03
Missing/retry required:            02,     04
~~~

Không suy “pod có tick ngày 04/01” nghĩa kỳ 04 đã thành công. Tìm expected keys từ lịch nghiệp vụ và đối chiếu **trạng thái đã commit**. Một đơn vị có thể đang `RUNNING` với lease còn hiệu lực, không được claim lại như pending ngay lập tức.

### 10.1. Policy rõ ràng cho case billing

- Mỗi tenant/ngày có một `jobKey`.
- Scanner tạo tất cả kỳ còn thiếu trong retention.
- Consumer chỉ claim jobs đến hạn.
- Nếu có business ordering dependency, không xử lý ngày N+1 trước ngày N.
- Job lỗi được retry với backoff/cap và trạng thái cuối.
- Nếu vượt retention, tạo alert/manual recovery, không âm thầm đánh dấu thành công.
- Nếu provider trả outcome không rõ, tra cứu remote status bằng idempotency key trước khi retry.

### 10.2. Cache refresh lại khác

Với cache chỉ phản ánh dữ liệu mới nhất, bạn có thể không cần tạo tám records riêng cho tám mốc đã qua; chỉ cần một `refresh latest` và timestamp/version. Đây là **coalesce** hợp lệ nhưng phải ghi explicit trong contract, tránh tình trạng team vận hành nhầm là dữ liệu bị mất.

## 11. Durable SQL key: chống chạy trùng ở tầng lưu trữ

Giả sử PostgreSQL, một bảng giữ từng logical occurrence:

~~~sql
CREATE TABLE job_occurrence (
    tenant_id       TEXT NOT NULL,
    job_type        TEXT NOT NULL,
    business_period DATE NOT NULL,
    state           TEXT NOT NULL
                    CHECK (state IN (
                        'PENDING', 'RUNNING', 'SUCCEEDED',
                        'FAILED', 'RETRYABLE', 'UNKNOWN'
                    )),
    attempt         INTEGER NOT NULL DEFAULT 0,
    owner_token     BIGINT NOT NULL DEFAULT 0,
    lease_until     TIMESTAMPTZ,
    last_error      TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, job_type, business_period)
);
~~~

Cấu trúc này đặt **một row cho mỗi logical job key**. Primary key không cấm nhiều attempts; nó cấm tạo hai rows mô tả cùng `tenant/jobType/period`.

### 11.1. Tạo job idempotently

~~~sql
INSERT INTO job_occurrence
    (tenant_id, job_type, business_period, state)
VALUES
    ('tenant-7', 'daily-report', DATE '2027-01-02', 'PENDING')
ON CONFLICT (tenant_id, job_type, business_period)
DO NOTHING;
~~~

Hai pods có thể cùng thực hiện `INSERT`; DB chỉ cho tồn tại một row. Đây là guarantee **trên database commit**. Nó không tự xử lý việc hai workers đã bắt đầu side effect trước khi tạo row, hoặc external API trả response không rõ.

### 11.2. Claim bằng một atomic update

~~~sql
UPDATE job_occurrence
SET state = 'RUNNING',
    attempt = attempt + 1,
    owner_token = owner_token + 1,
    lease_until = now() + INTERVAL '30 seconds',
    updated_at = now()
WHERE tenant_id = 'tenant-7'
  AND job_type = 'daily-report'
  AND business_period = DATE '2027-01-02'
  AND (
        state IN ('PENDING', 'RETRYABLE')
        OR (state = 'RUNNING' AND lease_until < now())
      )
RETURNING attempt, owner_token, lease_until;
~~~

Nếu hai transactions cạnh tranh trên cùng row, PostgreSQL sẽ thực hiện row locking và kiểm tra điều kiện theo transaction/isolation rules. Worker chỉ làm việc khi statement trả được row và commit claim thành công. **Không** thực thi side effect chỉ vì một SELECT trước đó thấy `PENDING`.

**Lưu ý:** schema + atomic claim mới là nền tảng. Trong production cần index/monitor, retry policy, statement timeout, chuyển trạng thái phù hợp, permission và giữ transaction ngắn. Đừng giữ row lock DB xuyên một HTTP call nhiều phút.

### 11.3. Lease hết hạn không có nghĩa worker cũ đã chết

Pod A claim với `owner_token=8`, bị pause JVM 45 giây; lease 30 giây hết hạn. Pod B claim lại với `owner_token=9`. Pod A có thể thức dậy và **vẫn thực hiện side effect**, dù không còn quyền. Đây là tình huống *split brain* do stale owner.

Chỉ dùng TTL lease chưa đủ để ngăn duplicate effects. Cần **fencing token được thực thi ở nơi ghi dữ liệu**, hoặc idempotency/claim kiểm soát sát transaction.

### 11.4. Fencing ở DB có thể chống stale writes

Một business state có cột `last_fence`, và writer chỉ được cập nhật khi token mới hợp lệ:

~~~sql
UPDATE aggregate_state
SET payload = :new_payload,
    last_fence = :my_token
WHERE aggregate_id = :aggregate_id
  AND last_fence < :my_token;
~~~

Đây là **mẫu SQL khái niệm** (named parameters do tầng Java/JDBC bind), không phải truy vấn copy-paste đầy đủ. Nó chỉ có ý nghĩa khi `my_token` **đơn điệu tăng theo owner generation của đúng resource**, được cấp từ một nguồn đáng tin, và transaction write/check bảo đảm atomic. Đối với ghi ra external payment provider không hỗ trợ fencing, vẫn cần idempotency key/reconciliation.

### 11.5. Đừng nhầm một lock với exactly-once

- **Leader election:** chọn leader trong một thời điểm.
- **Lease:** giới hạn khoảng thời gian quyền có hiệu lực.
- **Fencing token:** khiến tài nguyên từ chối stale owners.
- **Idempotency key:** khiến cùng logical operation không sinh hiệu ứng mới.
- **Unique constraint + transaction:** ngăn một dạng duplicate tại DB.
- **Reconciliation:** xử lý trường hợp trạng thái local và external chưa nhất quán.

Một hệ thống cần tổ hợp thích hợp; **không có một primitive đơn lẻ** thay tất cả.

## 12. SKIP LOCKED: tăng throughput nhưng không tự giữ thứ tự

PostgreSQL hỗ trợ `FOR UPDATE SKIP LOCKED` cho nhiều consumers đọc các rows kiểu queue:

~~~sql
SELECT tenant_id, job_type, business_period
FROM job_occurrence
WHERE state = 'PENDING'
ORDER BY business_period, tenant_id
FOR UPDATE SKIP LOCKED
LIMIT 20;
~~~

Đây là **minh họa trong một transaction**, không phải toàn bộ claim pipeline. `SKIP LOCKED` giúp worker khác không phải chờ row đang lock; vì bị skip, view có thể không nhất quán theo thứ tự. Nếu worker A lock kỳ `#3`, worker B có thể lấy kỳ `#4` trước.

**Do đó:** dùng `SKIP LOCKED` để tăng concurrency cần kết hợp per-key ordering guard khi nghiệp vụ yêu cầu sequence: ví dụ chỉ cho claim kỳ N+1 khi kỳ N `SUCCEEDED`, hoặc serialize theo aggregate trong transaction. `ORDER BY` trên query **không** đủ để đảm bảo commit order của các workers độc lập.

### 12.1. Concurrent claim và business transaction

Một workflow an toàn hơn thường tách:

1. Atomic claim job, commit claim nhanh.
2. Worker xử lý trong giới hạn timeout và lưu result theo key.
3. Khi commit effect nội bộ, xác minh token/version/unique key.
4. Khi external outcome không rõ, dùng remote idempotency + reconcile.
5. Đánh dấu `SUCCEEDED` chỉ khi hiệu ứng nghiệp vụ đã được xác nhận theo contract.

Nếu worker chết giữa bước 2 và 4, recovery worker có thể chạy lại. Đây là chỗ duplicate attempts được dự kiến trước, không phải tình huống “đáng lẽ không bao giờ xảy ra”.

## 13. Inbox và Outbox để nối transaction và message delivery

### 13.1. Transactional outbox: giải bài toán DB commit vs publish

Nếu một service cập nhật đơn hàng **và** cần phát event, không nên làm:

~~~text
DB UPDATE committed
process crashes
Kafka publish never happens
~~~

Thay bằng:

~~~text
One DB transaction:
  UPDATE orders
  INSERT outbox(eventId, aggregateId, sequence, payload, PENDING)
COMMIT

Separate relay:
  read pending outbox
  publish to broker
  mark published
~~~

Nếu relay publish thành công rồi crash trước khi `mark published`, event có thể được phát lại. Vì vậy, outbox giúp tránh **mất event do khoảng trống giữa DB commit và publish**, nhưng **không cam kết consumer sẽ không bao giờ thấy duplicate**. Consumer cần inbox/dedup theo `eventId` hoặc `(aggregateId, sequence)`.

### 13.2. Transactional inbox ở consumer

Khi nhận event:

~~~text
BEGIN
  INSERT inbox(event_id) ON CONFLICT DO NOTHING
  if already present: COMMIT; ACK
  else:
      verify expected aggregate sequence
      apply business state
      update last_applied_sequence
COMMIT
ACK broker message
~~~

Đây là **sơ đồ thuật toán**, không phải SQL đã giải quyết mọi race. `INSERT inbox`, business update và sequence check phải nằm trong **một transaction**; nếu gap thì không được đánh dấu event đã xử lý thành công rồi ACK theo kiểu khiến nó mất vĩnh viễn. Có thể persist `PENDING_GAP` ở inbox rồi resume sau, hoặc tạm không ACK theo policy của broker/consumer.

### 13.3. Khi inbox và business effect ở hai hệ thống

Nếu inbox ở PostgreSQL nhưng effect là gửi email/charge provider, hai operations không thuộc một DB transaction duy nhất. Dùng provider idempotency key và trạng thái trung gian `UNKNOWN` để reconcile; chỉ dùng `ON CONFLICT DO NOTHING` tại inbox không bảo đảm external effect exactly-once.

### 13.4. Giữ version trong event

~~~text
eventId = evt-2027-00031
aggregateId = account-7
sequence = 13
schemaVersion = 2
occurredAt = 2027-01-03T02:00:00Z
payload = ...
~~~

`occurredAt` là metadata thời điểm, **không** thay sequence; timestamp từ nhiều machines có thể trùng/lệch và không phản ánh commit order. Sequence nên được cấp cùng transaction với aggregate state hoặc từ cơ chế sequencer đáng tin.

## 14. Kafka: thứ tự trong partition, không phải toàn topic/cluster

Kafka lưu records trong một partition theo thứ tự offset. Trong một consumer group, mỗi partition được gán cho một consumer tại một thời điểm theo protocol, nhưng rebalances và failures có thể dẫn đến redelivery và in-flight công việc cũ cần phối hợp.

**Không** suy:

- Mọi partitions trong topic có một total order.
- Cùng message không bao giờ được giao lại.
- Hai events có cùng business key luôn vào cùng partition nếu producer không dùng partitioning/key nhất quán.
- Thứ tự append vào partition tự tạo thứ tự **external DB commits** khi worker bên trong consumer dispatch song song.
- Kafka “exactly-once” tự áp dụng cho một payment provider độc lập.

### 14.1. Per-key routing

Nếu order events phải giữ thứ tự theo `orderId`, producer thường dùng cùng `orderId` làm message key để partitioner đưa các events của một order về cùng partition. Cần theo dõi thay đổi partition count/partitioner và đảm bảo producer semantics phù hợp; đừng chỉ dùng timestamp để sắp xếp.

### 14.2. Ack/offset commit quyết định duplicate hay missing

~~~text
Process then commit offset:
    crash after DB effect, before offset -> REDelivery -> duplicate attempt

Commit offset then process:
    crash after offset, before DB effect -> potential LOST effect
~~~

At-least-once consumer thường **process then acknowledge**. Để tránh duplicate business effect, consumer phải idempotent. Với processing Kafka→Kafka, transactions và committed offset có thể đem lại guarantee trong phạm vi hỗ trợ; với Kafka→external SQL/API, cần phối hợp output state/offset hoặc dedup ở nơi nhận.

### 14.3. Consumer parallelism có thể phá order

Một consumer poll offsets `10,11,12` rồi submit ba tasks vào executor. Nếu 12 chạy nhanh hơn 10, effect của 12 có thể commit trước 10. Khi yêu cầu strict per-key order, cần key-aware lanes, sequential processing hoặc version/CAS/buffer để đảm bảo **commit order**, không chỉ poll order.

### 14.4. Retry không được đẩy record mới qua record cũ tùy tiện

Nếu event `#5` fail và consumer tiếp tục áp dụng `#6`, hệ thống có thể sai nếu hai events phụ thuộc nhau. Policy có thể là block partition/key, persist gap và defer #6, hoặc bù bằng snapshot. Cần nêu rõ dead-letter handling: một event nằm ở DLQ **không** làm chuỗi nghiệp vụ tự hoàn thành đúng thứ tự.

## 15. Spring @Async và executor không tự giữ order

~~~java
@Async("jobExecutor")
public CompletableFuture<Void> handle(EventDto event) {
    apply(event);
    return CompletableFuture.completedFuture(null);
}
~~~

Đây là **Spring snippet**, cần `@EnableAsync`, bean executor và định nghĩa `EventDto`/`apply`. Một pool có nhiều workers không có guarantee tasks **bắt đầu, hoàn thành hoặc commit** theo thứ tự submit. Future A được submit trước Future B nhưng B có thể xong trước.

Nếu cần per-key order:

- Một lane/actor theo aggregate key.
- Hàng đợi FIFO cho từng key với một worker active.
- Hoặc database version/sequence CAS và pending-gap store.
- Khi scale ngang, assignment/lease phải tránh hai nodes cùng commit một aggregate.
- Backpressure nếu một key “nóng” có backlog dài.

### 15.1. CompletableFuture chained stages cũng có phạm vi

`thenApply`/`thenCompose` có thể diễn tả phụ thuộc trong **một chain**, nhưng không tự làm hai chains độc lập chạy theo thứ tự. `thenApplyAsync` còn có thể đổi executor. Nếu nghiệp vụ đòi key order qua process restart, một chain in-memory chưa đủ.

### 15.2. @Transactional trong mỗi worker không đồng nghĩa global serialization

Hai `@Transactional` methods có thể chạy đồng thời, commit order khác order nhận. Cần optimistic version, row-level lock, unique key hoặc cơ chế serialize theo aggregate với isolation phù hợp. Một transaction riêng lẻ atomic không giải quyết mọi cross-transaction ordering requirement.

## 16. Spring Context Maven chạy được: dedup cùng logical job key

Ví dụ sau minh họa **hai lệnh từ hai “node” giả lập** cùng muốn xử lý một kỳ. Cả hai dùng chung một `InMemoryStore` để kết quả **deterministic** và dễ kiểm tra. Đây **không** phải giải pháp distributed: trong Kubernetes mỗi pod có heap riêng, nên phải thay store bằng PostgreSQL/Redis/coordination có semantics rõ.

### 16.1. Cấu trúc

~~~text
spring-delivery-contract/
├── pom.xml
└── src/main/java/demo/SpringDeliveryContractDemo.java
~~~

**Tệp `pom.xml`:**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-delivery-contract</artifactId>
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

**Tệp `src/main/java/demo/SpringDeliveryContractDemo.java`:**

~~~java
package demo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class SpringDeliveryContractDemo {

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2027-01-03T02:00:00Z"),
                    ZoneOffset.UTC);
        }

        @Bean
        InMemoryStore store() {
            return new InMemoryStore();
        }

        @Bean
        Reconciler reconciler(Clock fixedClock, InMemoryStore store) {
            return new Reconciler(fixedClock, store);
        }
    }

    static class InMemoryStore {
        private final Set<String> accepted =
                ConcurrentHashMap.newKeySet();

        boolean acceptOnce(String jobKey) {
            return accepted.add(jobKey);
        }

        int size() {
            return accepted.size();
        }
    }

    static class Reconciler {
        private final Clock clock;
        private final InMemoryStore store;

        Reconciler(Clock clock, InMemoryStore store) {
            this.clock = clock;
            this.store = store;
        }

        void tick(String node) {
            LocalDate today = clock.instant()
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .toLocalDate();

            LocalDate period = today.minusDays(1);
            String jobKey = "billing|tenant-A|" + period;

            System.out.println(node + " accepted="
                    + store.acceptOnce(jobKey)
                    + " key=" + jobKey);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            Reconciler reconciler =
                    context.getBean(Reconciler.class);
            InMemoryStore store =
                    context.getBean(InMemoryStore.class);

            reconciler.tick("pod-A");
            reconciler.tick("pod-B");

            System.out.println("logical keys = " + store.size());
        }
    }
}
~~~

Chạy trên Java 17 + Maven:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.SpringDeliveryContractDemo
~~~

**Stdout do class in khi chạy thành công:**

~~~text
pod-A accepted=true key=billing|tenant-A|2027-01-02
pod-B accepted=false key=billing|tenant-A|2027-01-02
logical keys = 1
~~~

### 16.2. Điều ví dụ chứng minh

- `Clock.fixed` giúp tính kỳ nghiệp vụ theo HCM rõ ràng, không phụ thuộc timezone laptop.
- Cả hai lời gọi dùng **cùng job key**, nên fake store từ chối lần hai.
- `ConcurrentHashMap.newKeySet().add` là atomic trong một JVM, thuận tiện để kiểm tra logic keying.
- Chương trình gọi `tick` bằng tay, **không tự schedule**; mục tiêu là kiểm tra key/dedup, không mô phỏng hai Kubernetes pods.
- Kết quả hoàn toàn deterministic vì hai calls được thực hiện theo thứ tự trong `main`.

**Giới hạn rất quan trọng:** `InMemoryStore` không an toàn trên cluster và không bền sau restart. Ngoài ra, nó đánh dấu “accepted” **trước khi chạy business effect**; nếu process crash ngay sau khi add mà chưa xử lý, job có thể bị bỏ lỡ. Production phải dùng một **state machine trong DB** với `PENDING/RUNNING/SUCCEEDED`, transaction, claim/retry và reconciliation. Không dùng fake store này như một implementation production.

### 16.3. Cách kết nối với @Scheduled production

~~~java
@Component
class DailyBillingTrigger {
    private final DurableReconciler reconciler;

    DailyBillingTrigger(DurableReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Scheduled(cron = "0 0 9 * * *",
               zone = "Asia/Ho_Chi_Minh")
    public void trigger() {
        reconciler.ensureAllMissingPeriodsHaveJobs();
    }
}
~~~

Đây là **Spring application snippet**, cần `@EnableScheduling`, service `DurableReconciler`, imports và DB job store. Một hoặc nhiều pods đều được phép gọi scanner: business keys và claims trong shared DB sẽ quyết định **ai được thực thi effect**, không phải số lần method kích hoạt.

## 17. Crash windows: viết vào test plan trước khi release

| Cửa sổ lỗi | Rủi ro | Cách xử lý kỳ vọng |
| --- | --- | --- |
| Trước khi lưu job intent | **Missed job** | Recompute expected periods và enqueue |
| Sau lưu job, trước ACK HTTP | **Duplicate submission** | Idempotent `jobKey`/unique constraint |
| Sau claim, trước start | `RUNNING` nhưng chưa có effect | Lease expiry và retry |
| Sau effect DB, trước ACK broker | **Duplicate delivery** | Inbox/eventId+effect trong một transaction |
| Sau external API commit, trước lưu local success | **Unknown outcome / duplicate retry** | Provider idempotency key + reconcile |
| Lease hết hạn khi worker cũ vẫn chạy | **Split brain** | Fencing token tại resource + idempotency |
| Consumer nhận `#4` trước `#3` | **Out-of-order/gap** | Buffer/replay #3; không apply #4 sai |
| DLQ chứa `#3`, live topic có `#4` | **Per-key block** | Chính sách unblock/quarantine/manual |
| Pod down ngay mốc cron | **Missing trigger** | Durable period scan khi khởi động |
| Retry storm khi downstream 503 | Overload/reorder | Backoff+jitter+budget, bounded workers |
| Clock skew giữa pods | Cùng lịch nhưng phase/lease lệch | Reliable time source, tolerate skew, store-issued tokens |
| Hard SIGKILL không gọi shutdown hook | Pending work mất khỏi RAM | Durable job store, no reliance on graceful shutdown only |
| DB commit của #2 trước #1 | Business order sai | Row lock/version or per-key serial lane |
| Duplicate message có payload khác | Data conflict | Reject/quarantine, kiểm tra hash/version |

### 17.1. Failure injection quan trọng hơn happy-path

Nếu hệ thống chỉ được test với hai pods chạy khỏe và một event đến đúng thứ tự, ba guarantee vẫn chưa được chứng minh. Cần tiêm lỗi ở **ranh giới transaction/ack** và **thời điểm lease hết hạn**, vì chính các cửa sổ này tạo duplicate/missed/out-of-order trong thực tế.

### 17.2. Không chỉ kiểm “không có duplicate trong log”

Một log line có thể bị gửi hai lần, một event có thể được xử lý hai lần nhưng chỉ một row được commit, hoặc một payment có thể được trừ hai lần mà Java log chỉ có một record. Assertions phải nhắm đúng **observable business effect**: số hóa đơn unique, tổng tiền/ledger state, số email thực sự được provider chấp nhận, last sequence và số kỳ còn thiếu.

## 18. Thiết kế tests theo ba chiều

### 18.1. Duplicate tests

- Gọi cùng `jobKey` hai lần trên một instance.
- Gọi đồng thời từ hai simulated pods với **shared transactional DB**.
- Crash sau DB commit nhưng trước ACK, replay cùng event.
- Thử lease expiry/failover khi worker cũ vẫn active.
- Gửi hai payload khác nhau có cùng idempotency key: phải reject/conflict hoặc policy rõ.
- Assert **effect count = 1** trong phạm vi key; attempt count có thể > 1.

### 18.2. Missed tests

- Tắt scheduler qua một hoặc nhiều business periods.
- Restart và xác nhận scanner tạo đủ keys trong retention.
- Khi một kỳ đã `SUCCEEDED`, scanner không tạo lại effect.
- Khi một kỳ `RUNNING` với lease chưa hết, không claim trùng.
- Khi lease hết và worker cũ mất, job được retry theo policy.
- Khi quá retention, job được đánh dấu manual/alert, **không âm thầm skip** nếu contract yêu cầu đủ.

### 18.3. Ordering tests

- Giao `#2` trước `#1` cho cùng key: #2 không commit trước #1.
- Giao `#2` hai lần: dedup.
- Một event bị thiếu: phát hiện gap và retry/replay rõ.
- Hai keys A/B: có thể xử lý song song nếu không phụ thuộc.
- Gửi event cũ sau event mới: version/CAS bảo vệ stale overwrite.
- Consumer rebalance/restart ở giữa một key sequence: last-applied-sequence bền.
- Retry của #3 có thể xảy ra sau #4: vẫn không phá commit order nếu contract strict.
- Test riêng **order of commit**, không chỉ order of delivery.

### 18.4. Không dùng Thread.sleep làm bằng chứng concurrency

Dùng `CountDownLatch`, `CyclicBarrier`, futures có timeout, transaction hooks và fault injection. Nếu cần tái hiện race, tạo hai callers tranh atomic claim trong database và kiểm tra **một claim thành công**. Số sleep milliseconds không chứng minh race hoặc loại trừ race.

## 19. SLO và observability theo đúng điều đã hứa

| Metric | Ý nghĩa |
| --- | --- |
| `job_attempts_total` | Tổng số lần thử, bao gồm retry |
| `job_effect_commits_total` | Số effect được xác nhận commit |
| `job_duplicate_attempts_total` | Số attempts cho key đã tồn tại |
| `job_idempotency_conflicts_total` | Same key nhưng payload/version khác |
| `job_missing_periods` | Số business periods đến hạn chưa SUCCEEDED |
| `job_oldest_missing_age_seconds` | Kỳ chưa hoàn thành lâu nhất |
| `job_out_of_order_total` | Số events được nhận khi chưa đúng sequence |
| `job_sequence_gap_age_seconds` | Thời gian thiếu một sequence |
| `job_retry_total` | Số retry theo nguyên nhân |
| `job_unknown_outcome_total` | Số effects external chưa xác minh |
| `job_lease_expired_total` | Số claim chuyển owner do lease |
| `job_fenced_stale_writer_total` | Số stale writes bị chặn |
| `job_end_to_end_lag_seconds` | Độ trễ từ dueAt tới business completion |

Nên alert theo **outcome**, không chỉ tick heartbeat. Ví dụ “scanner chạy đúng giờ” có thể xanh trong khi 50 kỳ chưa commit. Với time series monitoring, tránh label là mỗi `jobId` duy nhất (cardinality lớn); IDs chi tiết vào log/tracing/DB, metric labels chỉ dùng phân nhóm có kiểm soát.

### 19.1. Log/audit fields

~~~text
jobKey / eventId / attemptId
tenantId / aggregateId / businessPeriod
scheduledAt / receivedAt / startedAt / committedAt
sequence / lastAppliedSequence / partition / offset
workerId / leaseOwner / fencingToken
status / errorCategory / retryAfter
schemaVersion / scheduleVersion
~~~

`attemptId` giúp điều tra retry; `jobKey` giúp dedup; `sequence` giúp order; `scheduledAt` giúp xác định missed. Ba thứ này **khác nhau**, không nên nén vào một UUID rồi mất semantics.

## 20. Quyết định ngay từ thiết kế: chấp nhận/không chấp nhận cái gì?

Một contract cho hệ thống **daily billing**:

~~~text
Duplicate attempts: ALLOWED
Duplicate invoice effects: FORBIDDEN per tenant + month
Missing business period: FORBIDDEN silently
Recovery: automatic backfill up to 90 days
Beyond retention: manual queue + alert
Ordering: strict per tenant per month (only if prior month closed)
Concurrency: independent tenants may run in parallel
External payment outcome unknown: reconcile before retry
Sources of truth: PostgreSQL job state + ledger + provider status
Hard crashes: may repeat attempts; must not duplicate effect
Maximum ordinary lag: 15 minutes; alert after threshold
~~~

Một contract cho **cache refresh**:

~~~text
Duplicate refresh attempts: ALLOWED
Missing ticks: ALLOWED; COALESCE to latest refresh
Ordering: latest version wins; stale results discarded
Recovery: recompute cache from source of truth
Exactly-once: NOT REQUIRED
Maximum staleness: 2 minutes under normal availability
~~~

**Không** dùng một contract “exactly once, no miss, total order” cho mọi feature mà không hiểu chi phí: nó có thể gây global bottleneck hoặc bất khả thi khi dependencies external không phối hợp transaction. Hãy chọn guarantee tối thiểu **đủ cho nghiệp vụ**, rồi đo đúng guarantee đó.

## 21. Checklist tài liệu và code review

- [ ] Đã định nghĩa logical `jobKey`/`eventId` và phân biệt với attempt ID.
- [ ] Ghi rõ duplicate attempts có được phép không.
- [ ] Ghi rõ duplicate **business effect** có được phép không và unique scope.
- [ ] Có phương án idempotency theo business key ở DB/provider.
- [ ] Không tuyên bố exactly-once vượt khỏi transaction boundary thực.
- [ ] Đã chọn missed policy: skip/coalesce/replay/expire/manual.
- [ ] Có cách tính expected business periods độc lập số cron invocations.
- [ ] Có durable job/event store nếu việc không được mất qua restart.
- [ ] Có scanner/reconciler để tìm `due but not succeeded`.
- [ ] Có retention window, retry budget và escalation khi quá hạn.
- [ ] Có ordering scope: none, per-key, per-partition, per-tenant hay global.
- [ ] Đã chọn order of **commit** cần thiết, không chỉ queue receive order.
- [ ] Có `sequence/version` đáng tin và xử lý gap/stale/duplicate payload.
- [ ] Nếu dùng Kafka, producer key/partition routing có chủ đích.
- [ ] Nếu dùng executor nhiều workers, không mặc định hoàn tất theo submit order.
- [ ] Nếu dùng `SKIP LOCKED`, kiểm tra nguy cơ claim kỳ sau trước kỳ trước.
- [ ] Nếu dùng lease, xử lý old worker sống lại và fencing.
- [ ] Đã thử concurrent claims từ ít nhất hai clients với DB chung.
- [ ] Đã thử crash sau commit trước ack và timeout external ambiguous.
- [ ] Đã thử restart khi cron bị bỏ lỡ nhiều kỳ.
- [ ] Đã thử out-of-order events, missing sequence và DLQ.
- [ ] Đã test consistency giữa transaction/inbox/outbox/effect.
- [ ] Có shutdown/drain nhưng không chỉ dựa shutdown hook khi SIGKILL.
- [ ] Có alert theo missing backlog, oldest pending age và sequence gap.
- [ ] Có runbook/manual reconciliation cho trạng thái UNKNOWN.
- [ ] Không lưu raw credentials/PII trong log hoặc metric labels.
- [ ] Theo dõi retention của broker/job store đủ để backfill theo SLA.
- [ ] Mọi cam kết đều có test/metric và người sở hữu vận hành.

## 22. Những nhầm lẫn thường gặp

**1. “Một @Scheduled chỉ chạy một lần mỗi tick trong cluster.”** Sai. Mỗi application context/pod có thể đăng ký lịch riêng.

**2. “Có synchronized thì không hai pods cùng chạy.”** Sai. Java monitor là trong một JVM.

**3. “Có Redis lock là exactly-once.”** Sai. Lease expiry, stale workers, mất ACK và external effects vẫn tồn tại.

**4. “TTL lock hết hạn thì owner cũ chắc chắn đã dừng.”** Sai. Nó có thể bị pause rồi sống lại; cần fencing/idempotency.

**5. “At-least-once nghĩa là không bao giờ chạy trùng.”** Sai. Retry là hành vi dự kiến.

**6. “At-most-once nghĩa là không thể mất event.”** Sai. Có thể mất khi crash trước processing.

**7. “Không có duplicate message nghĩa là không có duplicate invoice.”** Không chắc; nhiều application paths vẫn có thể tạo effect trùng.

**8. “Cron chạy đúng giờ thì không mất kỳ nghiệp vụ.”** Sai. Job có thể lỗi hoặc chưa commit.

**9. “Sau restart Spring sẽ tự replay mọi cron tick đã lỡ.”** Sai đối với scheduling in-memory thông thường.

**10. “Một request timeout nghĩa là remote payment chắc chắn chưa được xử lý.”** Sai; có thể đã commit trước timeout.

**11. “Future cancelled chứng minh external rollback.”** Sai. Cancellation là local state/interrupt request.

**12. “Unique constraint trên jobs đủ bảo đảm external API không trừ tiền hai lần.”** Không. Cần provider idempotency/reconciliation.

**13. “ORDER BY trong SQL + SKIP LOCKED bảo đảm workers commit theo thứ tự.”** Sai. Các rows có thể bị skip và hoàn tất khác thứ tự.

**14. “Kafka giữ thứ tự toàn topic dù nhiều partitions.”** Sai. Ordering là theo partition, không có total order mặc định.

**15. “Consumer poll theo offset tức DB writes cũng tuần tự.”** Không nếu dispatch asynchronous hoặc transactions commit khác thứ tự.

**16. “Message key luôn đảm bảo per-key order dù thay partitioner/partition count.”** Cần xác minh routing, rebalances và producer semantics cụ thể.

**17. “Timestamp là sequence ID đáng tin giữa các máy.”** Không. Clock skew và concurrent events làm thứ tự mơ hồ.

**18. “Bỏ một tick cache giống bỏ một hóa đơn.”** Sai. Policy phụ thuộc khả năng tái dựng từ source of truth.

**19. “Hai retries cùng jobId ngẫu nhiên sẽ được dedup.”** Sai nếu mỗi attempt tạo UUID mới; cần stable business key.

**20. “Kafka transactions nghĩa external PostgreSQL/payment được exactly-once tự động.”** Sai. Guarantee có phạm vi.

**21. “Replayed message có cùng payload thì luôn vô hại.”** Sai nếu effect là cộng/trừ thay vì set idempotent.

**22. “At-least-once transport tự giữ correctness khi consumer đổi schema.”** Sai. Cần versioning/compatibility và handling conflict.

**23. “Một gap được gửi DLQ nghĩa là chuỗi có thể bỏ qua và vẫn strict order.”** Không; cần policy cho missing sequence.

**24. “Ghi idempotency row trước khi làm effect là đủ.”** Có thể tạo lost effect nếu crash sau mark nhưng trước commit; cần transaction/state machine.

**25. “Đã có một leader thì không cần kiểm DB constraints.”** Sai khi failover/split brain và bugs.

**26. “Tăng số workers luôn làm job backlog giảm mà không ảnh hưởng ordering.”** Không nếu per-key strict order, DB pressure hoặc downstream quota.

**27. “Exactly-once delivery cũng đồng nghĩa exactly-once business outcome.”** Không; cần chỉ rõ hệ thống/ranh giới.

**28. “Duplicate metric bằng 0 nghĩa hệ thống hoàn hảo.”** Có thể ít test/retries; phải kiểm effect invariants và miss/lag.

**29. “Mọi tenant phải tuần tự theo một khóa toàn hệ thống.”** Thường không cần; gây bottleneck.

**30. “Chỉ cần test happy path và restart sạch.”** Không đủ; cần crash windows và fault injection.

## 23. Bài tập tự kiểm tra

### Bài 1 — Hai pod cùng 09:00

Hai pods có cùng `@Scheduled(cron="0 0 9 * * *")`. Có thể cả hai cùng chạy không?

**Đáp án:** có. `@Scheduled` là scheduling trong từng Spring context; nếu yêu cầu một business effect, cần shared key/claim/idempotency.

### Bài 2 — Duplicate do mất ACK

Worker đã commit DB, chết trước khi commit offset. Consumer mới nhận lại message. Phải làm gì?

**Đáp án:** xử lý idempotently bằng `eventId`/inbox và transaction chung với business state; không coi replay là một business event mới.

### Bài 3 — Bỏ lỡ cron qua deploy

Ứng dụng down 20 phút, cần tạo báo cáo cho từng phút. Có thể chỉ dựa vào `@Scheduled` memory không?

**Đáp án:** không. Phải có durable expected windows và scanner/replay/backfill; cron chỉ kích hoạt việc kiểm tra.

### Bài 4 — Cache latest

Cache refresh mỗi phút, downtime 10 phút. Phải tạo 10 refresh jobs không?

**Đáp án:** không nhất thiết. Nếu business chỉ cần latest state, cho phép coalesce thành một refresh, nhưng policy phải được ghi rõ.

### Bài 5 — Out-of-order

Nhận `account#4` trước `account#3`, yêu cầu strictly sequential account state. Có apply #4 ngay không?

**Đáp án:** không. Buffer/quarantine/replay event #3 rồi xử lý theo sequence, hoặc có policy tái dựng state; không commit sai thứ tự.

### Bài 6 — Lease hết hạn

Pod A bị GC pause 45 giây, lease 30 giây; pod B claim lại. A có thể ghi đè B không?

**Đáp án:** có thể nếu thiếu fencing/version check tại resource. Lease TTL một mình không ngăn stale worker.

### Bài 7 — Kafka partitions

Topic có 8 partitions. Có tổng thứ tự của mọi message trong topic không?

**Đáp án:** không. Mỗi partition có ordering theo offset; nếu business cần per-key, phải đảm bảo routing và xử lý per-key thích hợp.

### Bài 8 — Commit offset trước DB

Consumer commit offset rồi process DB; crash giữa hai bước. Có thể mất business effect không?

**Đáp án:** có, vì broker tưởng message đã được xử lý. Thường process + commit effect trước ack, với dedup cho replay.

### Bài 9 — Claim một lần

Hai transactions cùng `INSERT ... ON CONFLICT DO NOTHING` với cùng primary key; có thể có hai rows không?

**Đáp án:** không theo unique constraint khi commits hợp lệ. Nhưng hai attempts hoặc external effects vẫn có thể xảy ra nếu application xử lý ngoài cơ chế claim.

### Bài 10 — Thứ tự submit và hoàn thành

Hai tasks A rồi B được submit vào 8-thread executor. Có chắc A commit trước B không?

**Đáp án:** không. Submission order không phải completion/commit order khi xử lý song song.

### Bài 11 — Định nghĩa SLA

Sản phẩm ghi “không bao giờ bỏ sót” nhưng không có retention/backfill/outage behavior. Có kiểm thử được không?

**Đáp án:** chưa. Cần định nghĩa expected keys, persistence, max lag, recovery window, expiry/manual action và failure cases.

### Bài 12 — Exactly once ở payment

Ứng dụng thấy timeout sau POST payment. Có thể retry bằng idempotency key mới không?

**Đáp án:** nguy hiểm. Nên tra trạng thái hoặc retry với **cùng logical idempotency key** theo API provider; không tự tạo charge thứ hai.

## 24. Kết luận

**Để giới hạn phân tán rõ ràng, hãy ghi ba hợp đồng độc lập: chạy trùng, bỏ lỡ và thứ tự.** Một invocation trùng không nhất thiết là effect trùng; một cron tick mất không nhất thiết là kỳ nghiệp vụ mất; một queue giao theo thứ tự không có nghĩa mọi DB commits theo thứ tự. Mỗi hợp đồng cần nêu chính xác **logical key, phạm vi, failure policy, persistence, cách test và chỉ số đo**.

Trong Spring, `@Scheduled`/`@Async` là công cụ **kích hoạt/điều phối task cục bộ**, không phải exactly-once distributed scheduler. Hệ thống chịu lỗi thường chấp nhận **at-least-once attempts**, tạo **effectively-once business effects** bằng unique key/transaction/idempotency, phát hiện **missed periods** bằng durable reconciliation, và chỉ áp **ordering per key** ở nơi nghiệp vụ yêu cầu.

Không dựa riêng vào distributed lock, lease, scheduler, Kafka offset hay graceful shutdown để hứa mọi thứ. Muốn tuyên bố một guarantee, hãy chỉ rõ **ranh giới của nó và thử crash/timeout/rebalance/split brain ngay trong test**.

## Tài liệu tham khảo chính thức

- Spring Framework — Task Execution and Scheduling, `@Scheduled` và repeatable registrations: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework 6.2 — `@Scheduled` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html
- Spring Framework 6.2 — `ThreadPoolTaskScheduler`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskScheduler.html
- Spring Framework 6.2 — `CronTrigger` lenient/fixed: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html
- Java SE 17 — `ExecutorService`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- Apache Kafka 4.0 — Design, order per partition và message delivery semantics: https://kafka.apache.org/40/design/design/
- Apache Kafka 4.0 — Producer idempotence configurations: https://kafka.apache.org/40/configuration/producer-configs/
- Quartz Scheduler — JDBC clustering/failover/misfires: https://www.quartz-scheduler.org/documentation/2.3.1-SNAPSHOT/configuration.html
- PostgreSQL — `INSERT ... ON CONFLICT`: https://www.postgresql.org/docs/current/sql-insert.html
- PostgreSQL — `SELECT ... FOR UPDATE SKIP LOCKED`: https://www.postgresql.org/docs/current/sql-select.html
- PostgreSQL — Transaction Isolation: https://www.postgresql.org/docs/current/transaction-iso.html


</details>

- [Quay lại đầu trang](#back-to-top)
